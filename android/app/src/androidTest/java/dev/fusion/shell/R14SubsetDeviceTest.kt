package dev.fusion.shell

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import dev.fusion.filesui.*
import dev.fusion.designsystem.Contrast
import dev.fusion.a11y.*
import dev.fusion.alerts.*
import dev.fusion.settingsui.*
import dev.fusion.ooxml.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.util.UUID
import java.util.zip.ZipInputStream

class R14SubsetDeviceTest {
    @get:Rule val disposable=DisposableEmulatorRule()
    @Test fun actualFileMetadataIsNaturallyOrderedWithoutChangingBytes() {
        val root=File(ApplicationProvider.getApplicationContext<Context>().cacheDir,"r14-"+UUID.randomUUID());check(root.mkdir())
        try {
            File(root,"file10").writeText("ten");File(root,"file2").writeText("two")
            val rows=requireNotNull(root.listFiles()).map { ListingItem(it.path,it.name,it.isDirectory,it.length()) }
            assertEquals(listOf("file2","file10"),DirectoryQuery.select(rows).map { it.name })
            assertEquals("ten",File(root,"file10").readText());assertEquals("two",File(root,"file2").readText())
        } finally { root.deleteRecursively() }
    }
    @Test fun opaqueSrgbContrastSelectsSufficientForeground() {
        assertEquals(21.0,Contrast.ratio(0,0xffffff),1e-12)
        val foreground=requireNotNull(Contrast.foreground(0xffffff,0x65ddb4))
        assertTrue(Contrast.ratio(foreground,0x65ddb4)>=4.5)
        assertTrue(Contrast.ratio(requireNotNull(Contrast.foreground(0xE4E4E7,0x303A39)),0x303A39)>=4.5)
        assertNull(Contrast.foreground(0x777777,0x777777,7.0))
    }
    @Test fun geometricTraversalSkipsDisabledTargets() {
        val boxes=listOf(FocusBox("a",0.0,0.0,10.0,10.0),FocusBox("b",20.0,0.0,30.0,10.0,false),FocusBox("c",40.0,0.0,50.0,10.0))
        assertEquals("c",SpatialFocus.next(boxes,"a",FocusDirection.RIGHT))
    }
    @Test fun redactionCannotBePreventedByClockRollback() {
        val ledger=NoticeLedger();ledger.post(PublicNotice("id","public","",100),100)
        try { ledger.advance(99);fail("Rollback accepted") } catch(_:IllegalArgumentException) { }
        assertTrue(ledger.redact("id").retained.isEmpty())
    }
    @Test fun settingsConflictHasNoPartialEffect() {
        val current=mutableMapOf("a" to "old","b" to "conflict")
        val patch=SettingsPatch.plan(mapOf("a" to "old","b" to "base"),mapOf("a" to "new","b" to "new"))
        try { patch.apply(current);fail("Conflict ignored") } catch(_:PreferenceConflict) { }
        assertEquals("old",current["a"]);assertEquals("conflict",current["b"])
    }
    @Test fun originalArithmeticResultsEnterLiteralWorkbookWithoutFormulaExecution() {
        val value=DecimalSheet.evaluate(mapOf("A1" to "2","A2" to "=A1*3+1")).getValue("A2")
        val bytes=LiteralWorkbook.export("Values",listOf(listOf(LiteralCell.Number(value)))).bytes
        var sheet=""
        ZipInputStream(bytes.inputStream()).use { z -> while(true) { val entry=z.nextEntry?:break;val text=z.readBytes().toString(Charsets.UTF_8);if(entry.name=="xl/worksheets/sheet1.xml") sheet=text } }
        assertTrue(sheet.contains("<v>7</v>"));assertFalse(sheet.contains("<f>"))
        val fraction=DecimalSheet.evaluate(mapOf("A1" to "=1/3")).getValue("A1")
        try { LiteralWorkbook.export("Values",listOf(listOf(LiteralCell.Number(fraction))));fail("Export silently rounded unsupported precision") } catch(_:IllegalArgumentException) { }
    }
}
