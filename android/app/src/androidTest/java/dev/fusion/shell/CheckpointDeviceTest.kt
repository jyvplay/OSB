package dev.fusion.shell

import android.content.Context
import android.util.AtomicFile
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.fusion.storage.CheckedAtomicFile
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.*
import java.util.UUID

/** Actual Android AtomicFile, no substitutes. Added for R3; NOT executed in the sandbox. */
@RunWith(AndroidJUnit4::class)
class CheckpointDeviceTest {
    @Test fun readbackAndEncoderFailureKeepPriorCheckpoint() {
        val context=ApplicationProvider.getApplicationContext<Context>()
        val file=AtomicFile(File(context.cacheDir,"checkpoint-test-${UUID.randomUUID()}"))
        val encode: (String,OutputStream)->Unit = { value,stream -> DataOutputStream(stream).apply { writeUTF(value); flush() } }
        val decode: (InputStream)->String = { DataInputStream(it).readUTF() }
        try {
            CheckedAtomicFile.write(file,"first",encode,decode)
            assertEquals("first",file.openRead().use(decode))
            try {
                CheckedAtomicFile.write(file,"second",{ _,stream -> stream.write(1); throw IOException("deliberate encoder interruption") },decode)
                fail("Interrupted encoder must not report a checkpoint")
            } catch(_: IOException) { /* Expected; inspect real previous bytes next. */ }
            assertEquals("first",file.openRead().use(decode))
            try {
                CheckedAtomicFile.write(file,"expected",{ _,stream -> encode("different",stream) },decode)
                fail("Readback mismatch must not report the intended value")
            } catch(_: IOException) { /* A mismatch is failure, not a durability proof. */ }
            assertEquals("different",file.openRead().use(decode))
        } finally { file.delete() }
    }
}
