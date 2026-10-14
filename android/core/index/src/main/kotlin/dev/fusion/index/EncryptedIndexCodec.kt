package dev.fusion.index

import java.io.*
import java.nio.ByteBuffer
import java.nio.CharBuffer
import java.nio.charset.CodingErrorAction
import java.util.Collections
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Encrypted whole-snapshot persistence, NOT encrypted computation/search over ciphertext. */
object EncryptedIndexCodec {
    const val MAX_DOCS = 512
    const val MAX_UNITS = 1024 * 1024 // UTF-16 units in all fields; not heap bytes.
    const val MAX_PLAIN_BYTES = 4 * 1024 * 1024
    const val MAX_FILE_BYTES = MAX_PLAIN_BYTES + 8 + 12 + 16
    private const val MAGIC = 0x46534958 // FSIX
    private const val VERSION = 1

    fun validateScope(scope: String) {
        require(scope.matches(Regex("[a-zA-Z0-9_-]{1,80}"))) { "Invalid index storage scope" }
    }

    fun admitted(documents: List<Doc>): List<Doc> {
        require(documents.size <= MAX_DOCS) { "Encrypted index has a 512-document bound" }
        val owned = ArrayList(documents)
        val validation = LocalIndex()
        val ids = HashSet<String>()
        var units = 0L
        for (document in owned) {
            require(ids.add(document.id)) { "Duplicate index document ID" }
            validation.upsert(document) // Existing per-field/query semantics stay authoritative.
            units += document.id.length.toLong() + document.title.length + document.body.length + document.source.length
            require(units <= MAX_UNITS) { "Encrypted index payload exceeds one Mi UTF-16 units" }
            for (text in listOf(document.id, document.title, document.body, document.source)) strictBytes(text)
        }
        return Collections.unmodifiableList(owned)
    }

    private fun strictBytes(text: String): ByteArray {
        val buffer = Charsets.UTF_8.newEncoder().onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT).encode(CharBuffer.wrap(text))
        return ByteArray(buffer.remaining()).also(buffer::get)
    }
    private fun writeText(out: DataOutputStream, text: String) {
        val bytes = strictBytes(text)
        out.writeInt(bytes.size); out.write(bytes)
    }
    private fun readText(input: DataInputStream, maxUnits: Int): String {
        val bytes = input.readInt()
        require(bytes in 0..maxUnits * 3 && bytes <= input.available()) { "Invalid UTF-8 field length" }
        val data = ByteArray(bytes).also(input::readFully)
        val text = Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(data)).toString()
        require(text.length <= maxUnits)
        return text
    }
    private fun encode(documents: List<Doc>): ByteArray {
        val owned = admitted(documents)
        val buffer = ByteArrayOutputStream()
        DataOutputStream(buffer).use { out ->
            out.writeInt(VERSION); out.writeInt(owned.size)
            for (doc in owned) {
                writeText(out, doc.id); writeText(out, doc.title); writeText(out, doc.body); writeText(out, doc.source)
            }
        }
        return buffer.toByteArray().also { require(it.size <= MAX_PLAIN_BYTES) }
    }
    private fun decode(bytes: ByteArray): List<Doc> {
        require(bytes.size <= MAX_PLAIN_BYTES)
        return DataInputStream(ByteArrayInputStream(bytes)).use { input ->
            require(input.readInt() == VERSION) { "Unsupported index payload version" }
            val count = input.readInt(); require(count in 0..MAX_DOCS)
            var units = 0L
            val documents = ArrayList<Doc>()
            repeat(count) {
                val doc = Doc(readText(input,256), readText(input,512), readText(input,LocalIndex.MAX_BODY), readText(input,1024))
                units += doc.id.length.toLong() + doc.title.length + doc.body.length + doc.source.length
                require(units <= MAX_UNITS) { "Decrypted index exceeds payload budget" }
                documents.add(doc)
            }
            require(input.read() == -1) { "Trailing decrypted index data" }
            admitted(documents)
        }
    }
    private fun header() = ByteBuffer.allocate(8).putInt(MAGIC).putInt(VERSION).array()
    private fun aad(cipher: Cipher, scope: String) {
        validateScope(scope)
        cipher.updateAAD(header())
        cipher.updateAAD(scope.toByteArray(Charsets.US_ASCII))
    }
    private fun validateKey(key: SecretKey) {
        require(key.algorithm.equals("AES", ignoreCase = true)) { "AES key required" }
        // Keystore keys do not expose bytes; their 256-bit policy must be enforced by the adapter.
        val encoded = key.encoded
        // Key.getEncoded() does not promise ownership of its returned array. Never mutate it.
        if (encoded != null) require(encoded.size == 32) { "256-bit key required" }
    }
    fun seal(documents: List<Doc>, key: SecretKey, scope: String): ByteArray {
        validateScope(scope); validateKey(key)
        val plain = encode(documents)
        try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            // Provider-generated IV: compatible with randomized-encryption-required Android Keystore.
            cipher.init(Cipher.ENCRYPT_MODE, key)
            require(cipher.iv.size == 12) { "Provider must produce a 96-bit GCM nonce" }
            aad(cipher, scope)
            val encrypted = cipher.doFinal(plain)
            require(encrypted.size == plain.size + 16) { "Provider must use a 128-bit GCM tag" }
            return header() + cipher.iv + encrypted
        } finally { plain.fill(0) }
    }
    fun open(container: ByteArray, key: SecretKey, scope: String): List<Doc> {
        require(container.size in 36..MAX_FILE_BYTES) { "Invalid encrypted index size" }
        require(container.copyOfRange(0,8).contentEquals(header())) { "Unsupported encrypted index format" }
        validateScope(scope); validateKey(key)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128,container.copyOfRange(8,20)))
        aad(cipher, scope)
        // No parsing/search publication until the authentication tag has been verified.
        val plain = cipher.doFinal(container,20,container.size-20)
        try { return decode(plain) } finally { plain.fill(0) }
    }
}
