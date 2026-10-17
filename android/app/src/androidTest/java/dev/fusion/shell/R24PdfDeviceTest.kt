package dev.fusion.shell

import android.content.ComponentName
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.graphics.Color
import android.graphics.Paint
import android.os.*
import android.provider.MediaStore
import android.system.OsConstants
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.*
import dev.fusion.format.pdf.sandbox.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.UUID
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern

/** Genuine Android PdfDocument producer/native isolated PdfRenderer/picker, not a fake PDF engine. */
class R24PdfDeviceTest {
    private val compose=createAndroidComposeRule<MainActivity>()
    @get:Rule val rules:RuleChain=RuleChain.outerRule(DisposableEmulatorRule()).around(compose)
    private val context get()=InstrumentationRegistry.getInstrumentation().targetContext
    private fun pdf():ByteArray {
        val doc=android.graphics.pdf.PdfDocument()
        try {
            for(i in 0..1) {
                val page=doc.startPage(android.graphics.pdf.PdfDocument.PageInfo.Builder(240,160,i+1).create())
                page.canvas.drawColor(Color.WHITE)
                page.canvas.drawRect(24f,24f,96f,96f,Paint().apply {color=if(i==0) Color.rgb(237,19,180) else Color.rgb(19,180,237)})
                doc.finishPage(page)
            }
            return ByteArrayOutputStream().also {doc.writeTo(it)}.toByteArray()
        } finally {doc.close()}
    }
    @Test fun genuinePdfRendersInDistinctUidWithReadonlyCapabilityAndBoundedRawPixels() {
        val bytes=pdf()
        for(index in 0..1) {
            FormatLease(context).use {lease->
                val result=lease.previewOwnedPdf(bytes,index)
                assertNotEquals(android.os.Process.myUid(),result.uid);assertFalse(result.internetGranted)
                assertEquals("READ_ONLY_FILE",result.inputTransport);assertTrue(result.sourceInode>0)
                val page=requireNotNull(result.pdf)
                assertEquals(2,page.pages);assertEquals(index,page.index);assertEquals(240,page.width);assertEquals(160,page.height)
                val expected=if(index==0) Color.rgb(237,19,180) else Color.rgb(19,180,237)
                assertEquals(expected,page.pixels()[40*page.width+40])
                val changed=page.pixels();changed.fill(0);assertEquals(expected,page.pixels()[40*page.width+40])
                assertTrue(runCatching {lease.previewOwnedPdf(bytes,0)}.isFailure)
            }
        }
        assertTrue(context.cacheDir.listFiles()?.none {it.name.startsWith("fusion-owned-pdf-")}==true)
    }
    private fun wire(memory:SharedMemory?,file:ParcelFileDescriptor?,page:Int):Bundle {
        val thread=HandlerThread("r24-pdf-wire").apply {start()}
        val connected=CompletableFuture<Messenger>();val result=CompletableFuture<Bundle>();val id=UUID.randomUUID().toString()
        val reply=Messenger(object:Handler(thread.looper) {override fun handleMessage(message:Message) {
            if(message.data.getString("id")==id) {
                assertNotEquals(android.os.Process.myUid(),message.sendingUid)
                result.complete(Bundle(message.data))
            }
        }})
        val connection=object:ServiceConnection {
            override fun onServiceConnected(name:ComponentName,binder:IBinder) {connected.complete(Messenger(binder))}
            override fun onServiceDisconnected(name:ComponentName) {result.completeExceptionally(IllegalStateException("Disconnected"))}
        }
        var bound=false
        try {
            bound=context.bindIsolatedService(Intent(context,FormatWorkerService::class.java),Context.BIND_AUTO_CREATE,"r24pdf"+id.replace("-",""),context.mainExecutor,connection)
            check(bound)
            connected.get(12,TimeUnit.SECONDS).send(Message.obtain(null,FormatWorkerService.PDF_PREVIEW).apply {
                data=Bundle().apply {putString("id",id);putParcelable("input",file);putParcelable("memory",memory);putInt("pdfPage",page)
                    if(memory!=null) putInt("memoryBytes",memory.size)};replyTo=reply
            })
            return result.get(8,TimeUnit.SECONDS)
        } finally {if(bound) context.unbindService(connection);thread.quitSafely()}
    }
    @Test fun malformedOversizeWrongPageAndMemoryCarrierRefuseThenFreshPdfLeaseRecovers() {
        for(bytes in listOf(byteArrayOf(),"not a PDF".toByteArray(),ByteArray(PdfPreviewLimits.MAX_BYTES+1)))
            assertTrue(runCatching {FormatLease(context).use {it.previewOwnedPdf(bytes,0)}}.isFailure)
        val valid=pdf()
        assertTrue(runCatching {FormatLease(context).use {it.previewOwnedPdf(valid,2)}}.isFailure)
        SharedMemory.create("r24-real-pdf-wire",valid.size).use {memory->
            val writer=memory.mapReadWrite();try {writer.put(valid)} finally {SharedMemory.unmap(writer)}
            assertTrue(memory.setProtect(OsConstants.PROT_READ))
            assertFalse(wire(memory,null,0).getBoolean("ok")) // PDF needs a seekable readonly FD, never silently falls back from memory.
        }
        val file=File.createTempFile("r24-pdf-wire-",".pdf",context.cacheDir).apply {writeBytes(valid)}
        try {
            ParcelFileDescriptor.open(file,ParcelFileDescriptor.MODE_READ_WRITE).use {assertFalse(wire(null,it,0).getBoolean("ok"))}
            ParcelFileDescriptor.open(file,ParcelFileDescriptor.MODE_READ_ONLY).use {assertFalse(wire(null,it,-1).getBoolean("ok"))}
            assertArrayEquals(valid,file.readBytes())
        } finally {file.delete()}
        assertEquals(2,FormatLease(context).use {requireNotNull(it.previewOwnedPdf(valid,0).pdf).pages})
    }
    private fun displayedColor(rgb:Int):Int {
        val bitmap=requireNotNull(InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot())
        try {var n=0;for(y in 0 until bitmap.height step 4) for(x in 0 until bitmap.width step 4) if(bitmap.getPixel(x,y) and 0xffffff==rgb) n++;return n}
        finally {bitmap.recycle()}
    }
    @Test fun actualPickerIsolatedPreviewNextPageAndRecreationNeverWriteOriginalPdf() {
        val resolver=context.contentResolver;val name="r24-source-${UUID.randomUUID()}.pdf";val bytes=pdf()
        val uri=requireNotNull(resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI,ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME,name);put(MediaStore.MediaColumns.MIME_TYPE,"application/pdf")
            put(MediaStore.MediaColumns.RELATIVE_PATH,android.os.Environment.DIRECTORY_DOWNLOADS);put(MediaStore.MediaColumns.IS_PENDING,1)
        }))
        try {
            resolver.openOutputStream(uri,"wt")!!.use {it.write(bytes)};resolver.update(uri,ContentValues().apply {put(MediaStore.MediaColumns.IS_PENDING,0)},null,null)
            compose.waitUntil(10000) {compose.onAllNodes(hasTestTag("shelf-pdf-preview") and isEnabled()).fetchSemanticsNodes().isNotEmpty()}
            compose.onNodeWithTag("shelf-pdf-preview").performScrollTo().performClick()
            compose.onNodeWithText("Enable for this session with test data").performScrollTo().performClick()
            compose.onNodeWithTag("pdf-open").performScrollTo().performClick()
            val device=UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
            assertTrue(device.wait(Until.hasObject(By.pkg(Pattern.compile(".*documentsui"))),10000))
            requireNotNull(device.wait(Until.findObject(By.text(name)),10000)).click()
            assertTrue(device.wait(Until.gone(By.pkg(Pattern.compile(".*documentsui"))),10000))
            compose.waitUntil(15000) {compose.onAllNodesWithContentDescription("PDF page 1").fetchSemanticsNodes().isNotEmpty()}
            compose.waitUntil(10000) {displayedColor(0xed13b4)>100}
            compose.onNodeWithTag("pdf-next").performScrollTo().performClick()
            compose.waitUntil(15000) {compose.onAllNodesWithContentDescription("PDF page 2").fetchSemanticsNodes().isNotEmpty()}
            // A changed label is not a new page: prove the independent cyan marker reached actual display pixels.
            compose.waitUntil(10000) {displayedColor(0x13b4ed)>100}
            resolver.openInputStream(uri)!!.use {assertArrayEquals(bytes,it.readBytes())}
            compose.activityRule.scenario.recreate()
            // Session consent must not survive recreation, nor can the picker URI implicitly authorize parsing.
            compose.onNodeWithText("Experimental — explicit consent required").assertExists()
            resolver.openInputStream(uri)!!.use {assertArrayEquals(bytes,it.readBytes())}
        } finally {resolver.delete(uri,null,null)}
    }
}
