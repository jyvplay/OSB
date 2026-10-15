package dev.fusion.shell

import android.content.Context
import android.graphics.Bitmap
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.fusion.settings.*
import dev.fusion.media.*
import dev.fusion.launcher.*
import dev.fusion.backup.*
import dev.fusion.format.image.*
import dev.fusion.native.TextFormats
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class SubsetDeviceTest {
    @get:Rule val disposable = DisposableEmulatorRule()
    @Test fun realSoftwareBitmapCropRotateAndFlipDoNotMutateSource() {
        val p = intArrayOf(0xff000001.toInt(), 0xff000002.toInt(), 0xff000003.toInt(), 0xff000004.toInt(), 0xff000005.toInt(), 0xff000006.toInt())
        val source = Bitmap.createBitmap(p, 3, 2, Bitmap.Config.ARGB_8888)
        val rotated = ImageOps.rotate(source, 1); val cropped = ImageOps.crop(source, 1, 0, 3, 2); val flipped = ImageOps.flip(source, true)
        fun samples(b: Bitmap) = IntArray(b.width * b.height).also { b.getPixels(it, 0, b.width, 0, 0, b.width, b.height) }
        try {
            assertEquals(2, rotated.width); assertEquals(3, rotated.height)
            assertArrayEquals(intArrayOf(p[3],p[0],p[4],p[1],p[5],p[2]), samples(rotated))
            assertArrayEquals(intArrayOf(p[1],p[2],p[4],p[5]), samples(cropped))
            assertArrayEquals(intArrayOf(p[2],p[1],p[0],p[5],p[4],p[3]), samples(flipped))
            assertArrayEquals(p, samples(source))
        } finally { source.recycle(); rotated.recycle(); cropped.recycle(); flipped.recycle() }
    }
    @Test fun settingsExportReadsRealPreferencesAndInspectionDoesNotApply() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val id = "r12_" + UUID.randomUUID().toString().replace("-", "")
        val defs = listOf(SettingDef(id, "Probe", listOf("FILE"), "default"))
        val graph = SettingsGraph(context, defs)
        try {
            assertTrue(graph.set(id, "α🙂"))
            val snapshot = graph.exportSnapshot()
            assertEquals("α🙂", SettingsGraph(context, defs).inspectSnapshot(snapshot)[id])
            assertTrue(graph.set(id, "changed")); graph.inspectSnapshot(snapshot)
            assertEquals("changed", graph.get(id))
            assertFalse(graph.set(id, "\uD800")); assertEquals("changed", graph.get(id))
        } finally { context.getSharedPreferences("fusion_settings", Context.MODE_PRIVATE).edit().remove(id).commit() }
    }
    @Test fun strictSubtitleStreamOverlapAndHalfOpenEndpointsOnArt() {
        val s = SubRip.read("1\r\n00:00:00,000 --> 00:00:00,002\r\nα🙂\r\n\r\n2\r\n00:00:00,001 --> 00:00:00,003\r\nliteral <b>\r\n".byteInputStream())
        assertEquals(2, s.at(1).size); assertEquals(1, s.at(2).size); assertEquals(0, s.at(3).size)
        assertEquals(s.cues(), SubRip.parse(s.encode()).cues())
    }
    @Test fun backupContentRoundTripsRealFileWithoutRestoringOrChangingSource() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val file = File(context.cacheDir, "r12-content-" + UUID.randomUUID())
        val source = "original α🙂".toByteArray()
        try {
            file.writeBytes(source)
            val encoded = BackupContent.encode(mapOf("note.md" to file.readBytes()))
            assertArrayEquals(source, BackupContent.decode(encoded)["note.md"])
            encoded[12] = 0
            try { BackupContent.decode(encoded); fail("Corruption accepted") } catch (_: IllegalArgumentException) { }
            assertArrayEquals(source, file.readBytes())
        } finally { file.delete() }
    }
    @Test fun missingCatalogTargetsDoNotDestroyStoredPins() {
        val p = PinnedCatalog(listOf("a", "b"))
        assertEquals(listOf("b"), p.shelf(listOf(LaunchTarget("b", "B"))).map { it.key })
        assertEquals(listOf("a", "b"), p.shelf(listOf(LaunchTarget("b", "B"), LaunchTarget("a", "A"))).map { it.key })
        assertEquals(listOf("b", "a"), p.move("b", 0).pins())
    }
    @Test fun malformedUtf8IsRefusedOnArt() {
        assertFalse(TextFormats.isProbablyUtf8(byteArrayOf(0xff.toByte())))
        assertTrue(TextFormats.isProbablyUtf8("α🙂".toByteArray()))
    }
}
