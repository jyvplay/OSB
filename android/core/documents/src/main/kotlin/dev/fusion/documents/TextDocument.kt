package dev.fusion.documents

import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.CharBuffer
import java.nio.charset.CodingErrorAction
import java.security.MessageDigest

/** PR-01 / FF-15: editing a session never changes its source. No lossy decoding. */
data class TextDocument(
    val id: String,
    val name: String,
    val sourceUri: String?,
    val content: String,
    val savedDigest: String,
    val encoding: TextEncoding = TextEncoding.UTF8,
) {
    val dirty: Boolean get() = TextBytes.digest(content) != savedDigest
    fun saved(snapshot: String): TextDocument = copy(savedDigest = TextBytes.digest(snapshot))
}

object TextBytes {
    const val MAX_BYTES = 2 * 1024 * 1024

    /** Allocation-free strict UTF-16 validation and UTF-8 size; no replacement characters. */
    fun encodedSize(text: String): Int {
        require(text.length <= MAX_BYTES) { "Text exceeds the 2 MiB editor limit" }
        var size = 0
        var i = 0
        while (i < text.length) {
            val c = text[i++]
            size += when {
                c.code < 0x80 -> 1
                c.code < 0x800 -> 2
                Character.isHighSurrogate(c) -> {
                    require(i < text.length && Character.isLowSurrogate(text[i])) { "Unpaired UTF-16 surrogate" }
                    i++; 4
                }
                Character.isLowSurrogate(c) -> throw IllegalArgumentException("Unpaired UTF-16 surrogate")
                else -> 3
            }
            require(size <= MAX_BYTES) { "UTF-8 text exceeds the 2 MiB editor limit" }
        }
        return size
    }

    fun encode(text: String): ByteArray {
        require(text.length <= MAX_BYTES) { "Text exceeds the 2 MiB editor limit" }
        val buffer = Charsets.UTF_8.newEncoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT).encode(CharBuffer.wrap(text))
        require(buffer.remaining() <= MAX_BYTES) { "UTF-8 text exceeds the 2 MiB editor limit" }
        return ByteArray(buffer.remaining()).also { buffer.get(it) }
    }

    fun decode(bytes: ByteArray): String {
        require(bytes.size <= MAX_BYTES) { "Text exceeds the 2 MiB editor limit" }
        return Charsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString()
    }

    fun read(input: InputStream): ByteArray {
        val bytes = input.readNBytes(MAX_BYTES + 1)
        require(bytes.size <= MAX_BYTES) { "Text exceeds the 2 MiB editor limit" }
        return bytes
    }

    fun digest(text: String): String = digest(encode(text))
    fun digest(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256")
        .digest(bytes).joinToString("") { "%02x".format(it) }
}
