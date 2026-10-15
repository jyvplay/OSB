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
    @Test fun isolatedUidCannotReadParentPrivateFileOrAcquireInternetPermission() {
        val sentinel=File.createTempFile("r11-isolation-",".tmp",context.filesDir).apply { writeText("private-parent-only") }
        val bytes="actual opaque descriptor payload α\u0000".toByteArray()
        val file=input(bytes)
        try {
            val result=FormatLease(context).use { it.digest(file,sentinel.name) }
            assertNotEquals(Process.myUid(),result.uid);assertNotEquals(Process.myPid(),result.pid)
            assertEquals(bytes.size,result.bytes)
            assertEquals(MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) },result.digest)
            assertFalse("Worker unexpectedly holds INTERNET permission",result.internetGranted)
            assertEquals("Expected native EACCES, not missing-file refusal",OsConstants.EACCES,result.privateErrno)
            assertTrue("Private access denied flag must agree with actual errno",result.privateAccessDenied)
            assertEquals("private-parent-only",sentinel.readText());assertArrayEquals(bytes,file.readBytes())
        } finally { sentinel.delete();file.delete() }
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
