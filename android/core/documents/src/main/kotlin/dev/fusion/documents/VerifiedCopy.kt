package dev.fusion.documents

import java.io.InputStream
import java.io.OutputStream

/**
 * FM-18 fallback: an explicitly selected copy destination, never a claimed atomic SAF replacement.
 * A Create Document intent can return an existing item. The Android adapter rejects known aliases
 * and calls requireEmpty before output; this observation cannot establish freshness or exclusivity.
 * Provider metadata is not a durability guarantee. Success means byte-for-byte readback
 * after closing output; it does NOT claim atomic publish, remote sync, or physical fsync.
 */
object VerifiedCopy {
    class DestinationNotEmptyException : IllegalStateException("Destination contains data; choose a new file")
    /** Fail closed on nonempty OR unreadable targets. Not a compare-and-swap against concurrent writers. */
    fun requireEmpty(reopen: () -> InputStream) {
        reopen().use { if(it.read()!=-1) throw DestinationNotEmptyException() }
    }
    data class Binding(val requestId: String, val destinationUri: String)
    class Readback internal constructor(val digest: String, val bytes: Int, val binding: Binding?)
    fun write(bytes: ByteArray, output: () -> OutputStream, reopen: () -> InputStream, binding: Binding? = null): Readback {
        require(bytes.size <= TextBytes.MAX_BYTES)
        // Own the payload: a caller changing its byte array during I/O cannot change this write.
        val frozen=bytes.copyOf()
        val digest=TextBytes.digest(frozen)
        output().use { it.write(frozen); it.flush() }
        val actual = reopen().use(TextBytes::read)
        check(MessageDigestEqual.equal(frozen, actual) && TextBytes.digest(actual)==digest) {
            "Provider readback differs. Copy is unverified; the original and recovery draft are retained."
        }
        return Readback(digest,frozen.size,binding)
    }
}

private object MessageDigestEqual {
    fun equal(a: ByteArray, b: ByteArray) = java.security.MessageDigest.isEqual(a, b)
}
