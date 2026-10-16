package dev.fusion.shell

import android.content.Context
import android.os.Process
import androidx.test.core.app.ApplicationProvider
import dev.fusion.format.pdf.sandbox.FormatLease
import dev.fusion.ooxml.OwnedSheetRequest
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class R18OwnedSheetDeviceTest {
    @get:Rule val disposable=DisposableEmulatorRule()
    private val context get()=ApplicationProvider.getApplicationContext<Context>()
    private fun names()=context.cacheDir.listFiles()!!.filter {it.name.startsWith("fusion-owned-sheet-")}.map {it.name}.toSet()
    @Test fun actualUnlinkedReadOnlyDescriptorExecutesInChildAndLeavesNoNamedInput() {
        val cells=mapOf("A1" to "2","B1" to "=A1*3");val expected=OwnedSheetRequest.create(cells);val before=names()
        val result=FormatLease(context).use {it.evaluateOwnedSheet(cells)}
        assertNotEquals(Process.myUid(),result.uid);assertNotEquals(Process.myPid(),result.pid)
        assertFalse(result.internetGranted);assertEquals(expected.bytes,result.bytes);assertEquals(expected.sha256,result.digest)
        assertEquals(mapOf("A1" to "2","B1" to "6"),result.sheet);assertEquals(before,names())
    }
    @Test fun refusedOwnedCycleCleansNamedInputAndClosesLease() {
        val before=names();val lease=FormatLease(context)
        lease.use {
            assertTrue(runCatching {it.evaluateOwnedSheet(mapOf("A1" to "=A1"))}.isFailure)
            assertTrue(runCatching {it.evaluateOwnedSheet(mapOf("A1" to "1"))}.isFailure)
        }
        assertEquals(before,names())
    }
}
