package dev.fusion.backup

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.security.MessageDigest

/** New FBC1 content layer, NOT legacy ZIP/FSB1, encryption, filesystem restore or a migration.
 * Uncompressed to eliminate decompression amplification. Checksum detects damage, not forgery.
 * Decode validates the entire frame before returning any entries; it never writes a filesystem.
 */
object BackupContent {
    const val MAX_FILES = 64
    const val MAX_FILE_BYTES = 256 * 1024
    const val MAX_TOTAL_BYTES = 8 * 1024 * 1024
    const val MAX_FRAME = MAX_TOTAL_BYTES + MAX_FILES * 136 + 40
    private const val MAGIC = 0x46424331
    private fun validName(s: String) {
        // Deliberately portable ASCII basename subset; no traversal, hidden files or case aliases.
        require(s.matches(Regex("[A-Za-z0-9][A-Za-z0-9_-]{0,63}\\.md")))
        val stem = s.removeSuffix(".md").uppercase(java.util.Locale.ROOT)
        require(stem !in setOf("CON", "PRN", "AUX", "NUL") && !stem.matches(Regex("(?:COM|LPT)[1-9]")))
    }
    private fun digest(b: ByteArray) = MessageDigest.getInstance("SHA-256").digest(b)
    fun encode(entries: Map<String, ByteArray>): ByteArray {
        require(entries.size <= MAX_FILES)
        var total = 0L
        val names = HashSet<String>()
        entries.forEach { (name, bytes) ->
            validName(name); require(names.add(name.lowercase(java.util.Locale.ROOT)))
            require(bytes.size <= MAX_FILE_BYTES); total += bytes.size
        }
        require(total <= MAX_TOTAL_BYTES)
        val raw = ByteArrayOutputStream()
        DataOutputStream(raw).use { out ->
            out.writeInt(MAGIC); out.writeInt(entries.size)
            for ((name, bytes) in entries.toSortedMap()) {
                val n = name.toByteArray(Charsets.US_ASCII)
                out.writeInt(n.size); out.write(n); out.writeInt(bytes.size); out.write(bytes)
            }
        }
        val body = raw.toByteArray()
        return body + digest(body)
    }
    fun decode(frame: ByteArray): Map<String, ByteArray> {
        require(frame.size in 40..MAX_FRAME)
        // Own a snapshot: no returned buffer aliases the caller's frame.
        val body = frame.copyOfRange(0, frame.size - 32)
        require(MessageDigest.isEqual(digest(body), frame.copyOfRange(frame.size - 32, frame.size)))
        val input = DataInputStream(ByteArrayInputStream(body))
        require(input.readInt() == MAGIC)
        val count = input.readInt(); require(count in 0..MAX_FILES)
        val result = linkedMapOf<String, ByteArray>(); var total = 0L; var previous = ""
        val names = HashSet<String>()
        repeat(count) {
            val n = input.readInt(); require(n in 4..67 && n <= input.available())
            val nameBytes = ByteArray(n).also(input::readFully)
            require(nameBytes.all { it.toInt() in 0..127 })
            val name = String(nameBytes, Charsets.US_ASCII); validName(name)
            require(name > previous && names.add(name.lowercase(java.util.Locale.ROOT)))
            previous = name
            val size = input.readInt(); require(size in 0..MAX_FILE_BYTES && size <= input.available())
            total += size; require(total <= MAX_TOTAL_BYTES)
            result[name] = ByteArray(size).also(input::readFully)
        }
        require(input.available() == 0) { "Trailing content" }
        return result.toMap()
    }
}
