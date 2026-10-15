package dev.fusion.shell

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import dev.fusion.files.ContentDigest
import dev.fusion.terminal.LocalShell
import dev.fusion.automation.ActionPlan
import dev.fusion.continuity.*
import dev.fusion.ooxml.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.time.Instant
import java.util.UUID
import java.util.zip.ZipInputStream

class R13SubsetDeviceTest {
    @get:Rule val disposable=DisposableEmulatorRule()
    private val context get()=ApplicationProvider.getApplicationContext<Context>()
    @Test fun digestChecksActualFileAndLeavesSourceUnchanged() {
        val f=File(context.cacheDir,"r13-"+UUID.randomUUID());val bytes="α🙂".toByteArray()
        try { f.writeBytes(bytes);val receipt=f.inputStream().use { ContentDigest.scan(it) };assertEquals(bytes.size.toLong(),receipt.bytes)
            assertTrue(f.inputStream().use { ContentDigest.matches(it,receipt) });assertArrayEquals(bytes,f.readBytes())
            f.writeText("changed");assertFalse(f.inputStream().use { ContentDigest.matches(it,receipt) })
        } finally { f.delete() }
    }
    @Test fun localBuiltinHonorsQuotedArgumentsWithoutShellExpansion() {
        val shell=LocalShell(context)
        assertEquals(listOf("a b $"+"HOME"),shell.run("echo 'a b' '$"+"HOME'"))
        assertTrue(shell.run("echo 'unterminated").single().startsWith("Rejected"))
        assertTrue(shell.run("mem").single().startsWith("totalMb="))
    }
    @Test fun invalidPlanCannotStartAnyCallback() {
        var calls=0
        try { ActionPlan.parse("open calculator\nopen beam").execute { calls++;true };fail("Gate bypass") } catch(_:IllegalArgumentException) {}
        assertEquals(0,calls)
    }
    @Test fun foldedCalendarTextDoesNotInjectASecondEvent() {
        val t=Instant.parse("2026-10-15T12:00:00Z")
        val bytes=CalendarExport.encode(SharedCalendarEvent("test@fusion",t,t,t.plusSeconds(60),"🙂".repeat(60),"x\nBEGIN:VEVENT"))
        val text=String(bytes,Charsets.UTF_8)
        assertEquals(1,text.split("\r\n").count { it=="BEGIN:VEVENT" })
        assertEquals(1,text.split("\r\n").count { it=="END:VEVENT" })
        assertTrue(text.split("\r\n").all { it.toByteArray().size<=75 })
        for(control in listOf("\u007F","\u0085")) {
            try { CalendarExport.encode(SharedCalendarEvent("test@fusion",t,t,t.plusSeconds(60),control));fail("Unsupported control accepted") }
            catch(_:IllegalArgumentException) { }
        }
    }
    @Test fun originalWorkbookWritesLiteralFormulaLikeTextWithoutMacroParts() {
        val b=LiteralWorkbook.export("Sheet",listOf(listOf(LiteralCell.Text("=1+1"),LiteralCell.Bool(true))))
        val names=mutableListOf<String>();var worksheet=""
        ZipInputStream(b.bytes.inputStream()).use { z -> while(true) { val e=z.nextEntry?:break;names.add(e.name);val content=z.readBytes();if(e.name=="xl/worksheets/sheet1.xml") worksheet=content.toString(Charsets.UTF_8) } }
        assertEquals(5,names.size);assertFalse(worksheet.contains("<f>"));assertTrue(worksheet.contains("t=\"inlineStr\""));assertEquals(2,b.limitations.size)
    }
}
