package dev.fusion.format.pdf.sandbox

import android.os.SharedMemory
import android.system.ErrnoException
import android.system.OsConstants

/** Fresh parent-owned region. No writable map/handle is shared before restriction.
 * Not hostile-parent proof, no-swap memory, secure erase or computation authentication.
 * Prerequisite actual Android experiment recorded in r23-memory-platform-decision.md BEFORE this code.
 */
internal object OwnedFormatMemory {
    const val MAX_BYTES = 2 * 1024 * 1024

    fun create(bytes: ByteArray, limit: Int): SharedMemory {
        require(limit in 1..MAX_BYTES && bytes.size <= limit)
        val memory = SharedMemory.create("fusion-owned-format", maxOf(1, bytes.size))
        try {
            val writer = memory.mapReadWrite()
            try { if (bytes.isEmpty()) writer.put(0, 0.toByte()) else writer.put(bytes) }
            finally { SharedMemory.unmap(writer) } // setProtect alone would NOT revoke this writer.
            check(memory.setProtect(OsConstants.PROT_READ)) { "Could not restrict owned memory" }
            return memory
        } catch (error: Throwable) { memory.close(); throw error }
    }

    /** Does not prove that a malicious sender retained no old mappings. Parent ownership is required. */
    fun read(memory: SharedMemory, payloadBytes: Int, limit: Int): ByteArray {
        require(limit in 1..MAX_BYTES && payloadBytes in 0..limit) { "Memory payload budget exceeded" }
        require(memory.size == maxOf(1, payloadBytes)) { "Memory allocation/length mismatch" }
        require(!memory.setProtect(OsConstants.PROT_READ or OsConstants.PROT_WRITE)) { "Writable future permissions refused" }
        var writer: java.nio.ByteBuffer? = null
        var denied = false
        try { writer = memory.mapReadWrite() }
        catch (error: ErrnoException) {
            // ENOMEM/EINVAL/etc are not evidence of a readonly capability.
            require(error.errno == OsConstants.EACCES || error.errno == OsConstants.EPERM) { "Unexpected write-map failure" }
            denied = true
        } finally { writer?.let { SharedMemory.unmap(it) } }
        require(denied) { "Read-only shared memory required" }
        val reader = memory.mapReadOnly()
        try {
            if (payloadBytes == 0) require(reader.get(0) == 0.toByte()) { "Nonzero empty padding" }
            return ByteArray(payloadBytes).also { reader.get(it) }
        } finally { SharedMemory.unmap(reader) }
    }
}
