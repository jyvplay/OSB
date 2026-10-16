package dev.fusion.privacy

import android.os.Parcel
import android.os.SharedMemory
import android.system.ErrnoException
import android.system.OsConstants
import org.junit.Assert.*
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ReadOnlyBufferException

/** A real Android substrate experiment, NOT a new production FormatLease transport or hostile-child test. */
class R23MemoryBoundaryTest {
    private fun newWriteMapRefused(memory: SharedMemory) {
        var unexpected: ByteBuffer? = null
        try {
            unexpected = memory.mapReadWrite()
            fail("New writable mapping unexpectedly admitted")
        } catch (error: ErrnoException) {
            assertTrue("Unexpected errno ${error.errno}", error.errno == OsConstants.EACCES || error.errno == OsConstants.EPERM)
        } finally { unexpected?.let { SharedMemory.unmap(it) } }
    }

    @Test fun existingWritableMapSurvivesRestrictionButUnmappedReadOnlyParcelRefusesNewWrites() {
        SharedMemory.create("fusion-public-r23-counterexample", 4096).use { memory ->
            val writer = memory.mapReadWrite()
            try {
                writer.put(0, 1.toByte()); assertTrue(memory.setProtect(OsConstants.PROT_READ))
                // Actual documented counterexample: protection changes do not revoke existing mmap writers.
                writer.put(0, 9.toByte())
                val reader = memory.mapReadOnly()
                try { assertEquals(9.toByte(), reader.get(0)) } finally { SharedMemory.unmap(reader) }
                newWriteMapRefused(memory)
            } finally { SharedMemory.unmap(writer) }
        }
        SharedMemory.create("fusion-public-r23-readonly", 2 * 1024 * 1024).use { memory ->
            val payload = ByteArray(memory.size) { (it % 251).toByte() }
            val writer = memory.mapReadWrite()
            try { writer.put(payload) } finally { SharedMemory.unmap(writer) }
            assertTrue(memory.setProtect(OsConstants.PROT_READ))
            assertFalse(memory.setProtect(OsConstants.PROT_READ or OsConstants.PROT_WRITE))
            val parcel = Parcel.obtain()
            try {
                memory.writeToParcel(parcel, 0); parcel.setDataPosition(0)
                SharedMemory.CREATOR.createFromParcel(parcel).use { recipient ->
                    assertEquals(payload.size, recipient.size); newWriteMapRefused(recipient)
                    assertFalse(recipient.setProtect(OsConstants.PROT_READ or OsConstants.PROT_WRITE))
                    val reader = recipient.mapReadOnly()
                    try {
                        val actual = ByteArray(payload.size); reader.get(actual); assertArrayEquals(payload, actual)
                        try { reader.put(0, 7.toByte()); fail("Read-only ByteBuffer accepted write") }
                        catch (_: ReadOnlyBufferException) { /* Actual JVM buffer rejection. */ }
                    } finally { SharedMemory.unmap(reader) }
                }
            } finally { parcel.recycle() }
        }
    }
}
