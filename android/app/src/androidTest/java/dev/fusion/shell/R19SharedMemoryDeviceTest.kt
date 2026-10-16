package dev.fusion.shell

import android.os.SharedMemory
import android.system.ErrnoException
import android.system.OsConstants
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** Actual platform mappings, not a mock memory object or a compromised-worker simulation. */
class R19SharedMemoryDeviceTest {
    @get:Rule val disposable=DisposableEmulatorRule()
    @Test fun protectionRestrictsFutureMapsButDoesNotRevokeExistingWriter() {
        // [U] verify on API 34/36/37: this is an API34 spike, not a transport implementation.
        SharedMemory.create("fusion-r19-alias",4096).use { memory ->
            val writer=memory.mapReadWrite()
            try {
                writer.put(0,11.toByte())
                assertTrue(memory.setProtect(OsConstants.PROT_READ))
                val reader=memory.mapReadOnly()
                try {
                    assertEquals(11.toByte(),reader.get(0))
                    writer.put(0,29.toByte())
                    assertEquals(29.toByte(),reader.get(0)) // Genuine surviving-writer counterexample.
                    val error=runCatching { memory.mapReadWrite().also { SharedMemory.unmap(it) } }.exceptionOrNull()
                    assertTrue(error is ErrnoException)
                    assertFalse(memory.setProtect(OsConstants.PROT_READ or OsConstants.PROT_WRITE))
                } finally { SharedMemory.unmap(reader) }
            } finally { SharedMemory.unmap(writer) }
            val finalReader=memory.mapReadOnly()
            try {assertEquals(29.toByte(),finalReader.get(0))} finally {SharedMemory.unmap(finalReader)}
        }
    }
}
