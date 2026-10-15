package dev.fusion.shell

import android.content.Context
import android.os.ParcelFileDescriptor
import android.os.Process
import android.os.SystemClock
import android.system.Os
import android.system.OsConstants
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.fusion.format.pdf.sandbox.FormatLease
import dev.fusion.format.pdf.sandbox.FormatWorkerService
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.security.MessageDigest

/** Actual Android Binder/isolated UID/FD/death tests. No fake service, provider, clock or parser. */
@RunWith(AndroidJUnit4::class)
class FormatIsolationDeviceTest {
    private val context get()=ApplicationProvider.getApplicationContext<Context>()
    private fun input(bytes: ByteArray)=File.createTempFile("isolation-input-",".bin",context.cacheDir).apply { writeBytes(bytes) }
    private fun refused(block:()->Unit) { var failed=false;try { block() } catch(_:Exception) { failed=true };assertTrue("Expected refusal",failed) }
    @Test fun privatePathUnavailableWhileExplicitCapabilityReadsSameFile() {
        val bytes="private parent-only capability payload α\u0000".toByteArray()
        val sentinel=File.createTempFile("r11-isolation-",".tmp",context.filesDir).apply { writeBytes(bytes) }
        try {
            // Positive controls prevent a wrong/nonexistent fixture path from becoming isolation evidence.
            assertTrue(sentinel.isFile)
            val before=Os.stat(sentinel.absolutePath)
            val result=FormatLease(context).use { it.digest(sentinel,sentinel.name) }
            assertNotEquals(Process.myUid(),result.uid);assertNotEquals(Process.myPid(),result.pid)
            assertEquals(sentinel.absolutePath,result.privateProbePath)
            assertEquals(before.st_dev,result.sourceDevice);assertEquals(before.st_ino,result.sourceInode)
            assertEquals(bytes.size,result.bytes)
            assertEquals(MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) },result.digest)
            assertFalse("Worker unexpectedly holds INTERNET permission",result.internetGranted)
            // API34 actually returned ENOENT, not EACCES. Name invisibility and permission denial
            // are distinct observations; neither is accepted without the controls above.
            assertTrue("Private pathname was opened or failed unexpectedly: errno=${result.privateErrno}",
                result.privateErrno==OsConstants.EACCES || result.privateErrno==OsConstants.ENOENT)
            assertEquals(result.privateErrno==OsConstants.EACCES,result.privateAccessDenied)
            val after=Os.stat(sentinel.absolutePath)
            assertEquals(before.st_dev,after.st_dev);assertEquals(before.st_ino,after.st_ino)
            assertArrayEquals(bytes,sentinel.readBytes())
        } finally { sentinel.delete() }
    }
    @Test fun distinctBindingsDoNotShareAnIsolatedProcess() {
        val file=input(byteArrayOf(1,2,3))
        try {
            FormatLease(context).use { first -> FormatLease(context).use { second ->
                val a=first.digest(file);val b=second.digest(file)
                assertNotEquals(a.uid,b.uid);assertNotEquals(a.pid,b.pid);assertEquals(a.digest,b.digest)
            } }
        } finally { file.delete() }
    }
    @Test fun idleWorkerIsKilledByActualWatchdog() {
        val start=SystemClock.elapsedRealtime()
        FormatLease(context).use { lease ->
            assertTrue("No Binder death within observation deadline",lease.awaitDeath(15000))
            assertTrue(SystemClock.elapsedRealtime()-start<20000)
        }
    }
    @Test fun writablePipeOffsetAndOverBudgetInputsAreRefused() {
        val file=input(byteArrayOf(1,2,3))
        try {
            FormatLease(context).use { lease -> ParcelFileDescriptor.open(file,ParcelFileDescriptor.MODE_READ_WRITE).use { fd ->
                refused { lease.digestDescriptor(fd) }
            } }
            FormatLease(context).use { lease -> ParcelFileDescriptor.open(file,ParcelFileDescriptor.MODE_READ_ONLY).use { fd ->
                Os.lseek(fd.fileDescriptor,1,OsConstants.SEEK_SET)
                refused { lease.digestDescriptor(fd) }
            } }
            val pipe=ParcelFileDescriptor.createPipe()
            try { FormatLease(context).use { lease -> refused { lease.digestDescriptor(pipe[0]) } } }
            finally { pipe.forEach { it.close() } }
            java.io.RandomAccessFile(file,"rw").use { it.setLength(FormatWorkerService.MAX_INPUT_BYTES+1L) }
            FormatLease(context).use { lease -> refused { lease.digest(file) } }
        } finally { file.delete() }
    }
    @Test fun emptyInputIsNotMistakenForFailureAndLeasesAreSingleUse() {
        val file=input(byteArrayOf())
        try { FormatLease(context).use { lease ->
            assertEquals(0,lease.digest(file).bytes)
            refused { lease.digest(file) }
        } } finally { file.delete() }
    }
}
