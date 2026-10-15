package dev.fusion.files

import java.io.InputStream
import java.security.MessageDigest

data class ContentReceipt(val bytes: Long, val sha256: String) {
    init { require(bytes in 0..ContentDigest.MAX_BYTES && sha256.matches(Regex("[0-9a-f]{64}"))) }
}
/** Consumed-content evidence, NOT file identity, snapshot consistency, permission or mutation authority.
 * Caller owns stream. A byte cap cannot interrupt a blocking provider or prevent concurrent mutation.
 */
object ContentDigest {
    const val MAX_BYTES = 64L * 1024 * 1024
    fun scan(input: InputStream, limit: Long = MAX_BYTES): ContentReceipt {
        require(limit in 0..MAX_BYTES)
        val digest=MessageDigest.getInstance("SHA-256");val buffer=ByteArray(8192);var count=0L
        while(true) {
            val n=input.read(buffer,0,minOf(buffer.size.toLong(),limit-count+1).toInt())
            if(n<0) break
            require(n>0) { "Stream made no progress" }
            count+=n;require(count<=limit) { "Content exceeds byte budget" }
            digest.update(buffer,0,n)
        }
        val hex="0123456789abcdef"
        val hash=buildString { for(b in digest.digest()) { val n=b.toInt() and 255;append(hex[n ushr 4]);append(hex[n and 15]) } }
        return ContentReceipt(count,hash)
    }
    fun matches(input: InputStream, expected: ContentReceipt): Boolean = scan(input)==expected
}
