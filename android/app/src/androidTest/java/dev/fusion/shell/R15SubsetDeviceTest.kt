package dev.fusion.shell

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Matrix
import android.net.Uri
import android.os.Process
import android.os.SystemClock
import androidx.test.core.app.ApplicationProvider
import dev.fusion.lens.*
import dev.fusion.capture.*
import dev.fusion.messages.*
import dev.fusion.vault.*
import dev.fusion.model.*
import dev.fusion.ooxml.*
import dev.fusion.format.pdf.sandbox.FormatLease
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.security.MessageDigest
import java.security.SecureRandom

class R15SubsetDeviceTest {
    @get:Rule val disposable=DisposableEmulatorRule()
    private val context get()=ApplicationProvider.getApplicationContext<Context>()
    private fun refused(block:()->Unit) { var failed=false;try { block() } catch(_:Exception) { failed=true };assertTrue("Expected refusal",failed) }
    @Test fun lensCoordinatesAgreeWithActualAndroidBitmapQuarterTurnsAndMirror() {
        val input=Bitmap.createBitmap(2,3,Bitmap.Config.ARGB_8888)
        try {
            for(x in 0..1) for(y in 0..2) input.setPixel(x,y,Color.rgb(x*70,y*60,20))
            for(turn in 0..3) for(mirror in listOf(false,true)) {
                val matrix=Matrix().apply { postRotate(turn*90f);if(mirror) postScale(-1f,1f) }
                val rotated=Bitmap.createBitmap(input,0,0,2,3,matrix,false)
                try {
                    val transform=ViewportTransform(2,3,rotated.width,rotated.height,turn,mirror)
                    for(x in 0..1) for(y in 0..2) {
                        val p=transform.forward(LensPoint(x+0.5,y+0.5))
                        assertEquals(input.getPixel(x,y),rotated.getPixel(p.x.toInt(),p.y.toInt()))
                    }
                } finally { if(rotated!==input) rotated.recycle() }
            }
        } finally { input.recycle() }
    }
    @Test fun recorderAccountingUsesActualElapsedClockWithoutClaimingMediaDuration() {
        val timeline=RecordingTimeline();timeline.start(SystemClock.elapsedRealtime())
        timeline.pause(SystemClock.elapsedRealtime());timeline.resume(SystemClock.elapsedRealtime())
        val ended=timeline.stop(SystemClock.elapsedRealtime())
        assertEquals(ended.elapsedMillis,ended.activeMillis+ended.pausedMillis);assertTrue(ended.stopped)
        assertEquals(ended,timeline.read(SystemClock.elapsedRealtime()))
    }
    @Test fun transcriptWritesAndReadsRealFileWithoutAccessingSystemMessages() {
        val file=File.createTempFile("r15-message-",".fmt",context.cacheDir)
        val rows=listOf(TranscriptLine("own-test","作者","hello\n🙂",100))
        try { file.writeBytes(TranscriptArchive.encode(rows));assertEquals(rows,TranscriptArchive.decode(file.readBytes())) }
        finally { file.delete() }
    }
    @Test fun vaultEnvelopeAuthenticatesActualCiphertextFileAndContext() {
        val file=File.createTempFile("r15-vault-",".fve",context.cacheDir)
        val key=ByteArray(32).also(SecureRandom()::nextBytes);val plain="test-only plaintext".toByteArray();val contextBytes="context".toByteArray()
        try {
            file.writeBytes(VaultEnvelope.seal(plain,key,contextBytes))
            assertArrayEquals(plain,VaultEnvelope.open(file.readBytes(),key,contextBytes))
            refused { VaultEnvelope.open(file.readBytes(),key) }
            val broken=file.readBytes().also { it[it.lastIndex]=(it.last().toInt() xor 1).toByte() }
            refused { VaultEnvelope.open(broken,key,contextBytes) }
        } finally { file.delete() }
    }
    @Test fun canonicalLaunchLinkAgreesWithAndroidUriWithoutDispatchingAnIntent() {
        val target=LaunchTarget("editor",2);val text=LaunchLinks.encode(target);val uri=Uri.parse(text)
        assertEquals("fusion",uri.scheme);assertEquals("open",uri.authority)
        assertEquals("2",uri.getQueryParameter("workspace"));assertEquals(target,LaunchLinks.decode(uri.toString()))
        refused { LaunchLinks.decode(text+"&workspace=3") }
    }
    @Test fun originalSheetParserActuallyExecutesInDifferentUidWithReadOnlyFileCapability() {
        val bytes=SheetFrame.encode(mapOf("A1" to "2","B1" to "=A1*3+1"))
        val file=File.createTempFile("r15-sheet-",".fsc",context.cacheDir)
        try {
            file.writeBytes(bytes)
            val result=FormatLease(context).use { it.evaluateSheet(file) }
            assertNotEquals(Process.myUid(),result.uid);assertNotEquals(Process.myPid(),result.pid)
            assertFalse(result.internetGranted);assertEquals(bytes.size,result.bytes)
            assertEquals(mapOf("A1" to "2","B1" to "7"),result.sheet)
            assertEquals(MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) },result.digest)
            assertArrayEquals(bytes,file.readBytes())
        } finally { file.delete() }
    }
    @Test fun isolatedSheetCycleAndConsumedInputBudgetAreRefused() {
        val file=File.createTempFile("r15-sheet-bad-",".fsc",context.cacheDir)
        try {
            file.writeBytes(SheetFrame.encode(mapOf("A1" to "=A1")))
            refused { FormatLease(context).use { it.evaluateSheet(file) } }
            java.io.RandomAccessFile(file,"rw").use { it.setLength(SheetFrame.MAX_INPUT+1L) }
            refused { FormatLease(context).use { it.evaluateSheet(file) } }
            refused { SheetFrame.readResult("FSR1\nZZ9999\t1\n".toByteArray()) }
        } finally { file.delete() }
    }
}
