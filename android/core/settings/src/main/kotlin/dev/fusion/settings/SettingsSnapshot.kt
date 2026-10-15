package dev.fusion.settings

import java.nio.ByteBuffer
import java.nio.CharBuffer
import java.nio.charset.CodingErrorAction
import java.util.Base64
import java.util.Locale

data class SettingDef(val id: String, val title: String, val aliases: List<String> = emptyList(), val default: String = "")

/** Bounded registry and portable snapshot, NOT preferences persistence or settings application. */
class SettingsCatalog(definitions: List<SettingDef>) {
    private val definitions = definitions.also { list ->
        require(list.size in 1..64)
        list.forEach { require(it.aliases.size <= 16) }
    }.map { it.copy(aliases = it.aliases.toList()) }
    init {
        require(definitions.size in 1..64)
        require(definitions.map { it.id }.distinct().size == definitions.size)
        definitions.forEach {
            require(it.id.matches(Regex("[a-z][a-z0-9_.-]{0,63}")))
            require(it.title.length in 1..256 && it.aliases.size <= 16 && it.aliases.all { a -> a.length <= 256 })
            bytes(it.default)
        }
    }
    fun all(): List<SettingDef> = definitions.map { it.copy(aliases = it.aliases.toList()) }
    fun search(query: String): List<SettingDef> {
        require(query.length <= 256)
        val q = query.trim().lowercase(Locale.ROOT)
        return all().filter { d -> q.isEmpty() || (listOf(d.id, d.title) + d.aliases).any { it.lowercase(Locale.ROOT).contains(q) } }
    }
    fun encode(values: Map<String, String>): ByteArray {
        require(values.size <= definitions.size)
        val ids = definitions.map { it.id }.toSet()
        val text = buildString {
            append("FSET1\n")
            for ((id, value) in values.toSortedMap()) {
                require(id in ids) { "Unknown setting: $id" }
                append(id).append('=').append(Base64.getEncoder().encodeToString(bytes(value))).append('\n')
            }
        }
        return text.toByteArray(Charsets.US_ASCII).also { require(it.size <= MAX_FRAME) }
    }
    fun decode(frame: ByteArray): Map<String, String> {
        require(frame.size <= MAX_FRAME)
        val text = strict(frame)
        require(text.startsWith("FSET1\n") && text.endsWith('\n'))
        val result = linkedMapOf<String, String>()
        val body = text.substring(6)
        if (body.isNotEmpty()) for (line in body.dropLast(1).split('\n')) {
            val split = line.indexOf('='); require(split > 0)
            val id = line.substring(0, split)
            require(!result.containsKey(id) && result.size < definitions.size)
            val payload = line.substring(split + 1)
            require(payload.length <= 5464)
            val decoded = Base64.getDecoder().decode(payload)
            require(decoded.size <= MAX_VALUE)
            result[id] = strict(decoded)
        }
        // Canonical encoding rejects unknown keys, padding aliases, reordered records and extra lines.
        require(encode(result).contentEquals(frame)) { "Noncanonical settings snapshot" }
        return result.toMap()
    }
    companion object {
        const val MAX_VALUE = 4096
        const val MAX_FRAME = 65536
        private fun strict(b: ByteArray): String = Charsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(b)).toString()
        private fun bytes(s: String): ByteArray {
            require(s.length <= MAX_VALUE)
            val b = Charsets.UTF_8.newEncoder().onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT).encode(CharBuffer.wrap(s))
            require(b.remaining() <= MAX_VALUE)
            return ByteArray(b.remaining()).also { b.get(it) }
        }
    }
}
