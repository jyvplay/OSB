// SPDX-License-Identifier: Apache-2.0
package dev.fusion.shell

import android.content.ContentValues
import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Environment
import android.os.SystemClock
import android.provider.MediaStore
import android.widget.Button
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import dev.fusion.format.image.PngMetadata
import dev.fusion.format.image.sandbox.PngLease
import dev.fusion.format.image.sandbox.PngWorkerService
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.util.UUID
import java.util.regex.Pattern
import java.util.zip.CRC32
import java.util.zip.Deflater

/** Controlled real PNG bytes, Android Binder/isolated UID/decoder/system pickers, not synthetic execution evidence. */
class R25PngCopyDeviceTest {
    @get:Rule val disposable=DisposableEmulatorRule()
    private val context get()=ApplicationProvider.getApplicationContext<Context>()
    private val device get()=UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
    private fun chunk(name:String,body:ByteArray):ByteArray {
        val type=name.toByteArray();val crc=CRC32().apply {update(type);update(body)}.value.toInt()
        return ByteBuffer.allocate(body.size+12).putInt(body.size).put(type).put(body).putInt(crc).array()
    }
    private fun fixture():ByteArray {
        val width=16;val height=12;val raw=ByteArray((width*4+1)*height)
        for(y in 0 until height) for(x in 0 until width) {
            val i=y*(width*4+1)+1+x*4;raw[i]=(x*15).toByte();raw[i+1]=(y*20).toByte();raw[i+2]=90;raw[i+3]=255.toByte()
        }
        val d=Deflater();val out=ByteArrayOutputStream();val b=ByteArray(1024)
        try {d.setInput(raw);d.finish();while(!d.finished()){val n=d.deflate(b);check(n>0);out.write(b,0,n)}} finally {d.end()}
        return byteArrayOf(137.toByte(),80,78,71,13,10,26,10)+
            chunk("IHDR",ByteBuffer.allocate(13).putInt(width).putInt(height).put(8).put(6).put(0).put(0).put(0).array())+
            chunk("tEXt","Author\u0000R25 private owner marker".toByteArray())+chunk("IDAT",out.toByteArray())+chunk("IEND",byteArrayOf())
    }
    private fun await(s:ActivityScenario<PngCopyActivity>,phase:String):PngCopyActivity.Observation {
        val end=SystemClock.elapsedRealtime()+15000;var result:PngCopyActivity.Observation?=null
        while(SystemClock.elapsedRealtime()<end) {s.onActivity{result=it.observation()};if(result?.phase==phase)return result!!;SystemClock.sleep(50)}
        throw AssertionError("Actual PNG workflow did not reach $phase: $result; foreground=${device.currentPackageName}")
    }
    private fun button(name:String) = requireNotNull(device.wait(Until.findObject(By.res(context.packageName,name)),10000))
    private fun picker()=assertTrue(device.wait(Until.hasObject(By.pkg(Pattern.compile(".*documentsui"))),10000))
    private fun app()=assertTrue(device.wait(Until.gone(By.pkg(Pattern.compile(".*documentsui"))),10000))
    private fun seed(name:String,bytes:ByteArray):Uri {
        val uri=requireNotNull(context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI,ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME,name);put(MediaStore.MediaColumns.MIME_TYPE,"image/png")
            put(MediaStore.MediaColumns.RELATIVE_PATH,Environment.DIRECTORY_PICTURES);put(MediaStore.MediaColumns.IS_PENDING,1)
        }))
        context.contentResolver.openOutputStream(uri,"wt")!!.use{it.write(bytes)}
        context.contentResolver.update(uri,ContentValues().apply{put(MediaStore.MediaColumns.IS_PENDING,0)},null,null)
        return uri
    }
    private fun pick(name:String) {
        button("png_copy_choose").click();picker()
        // Image grid tiles can expose their real filename as accessibility description, not visible TextView text.
        val end=SystemClock.elapsedRealtime()+10000
        var file:androidx.test.uiautomator.UiObject2?=null
        while(file==null && SystemClock.elapsedRealtime()<end) {
            file=device.findObject(By.text(name)) ?: device.findObject(By.desc(Pattern.compile("(?s).*"+Pattern.quote(name)+".*")))
            if(file==null)SystemClock.sleep(50)
        }
        if(file==null) {
            val texts=device.findObjects(By.text(Pattern.compile(".+"))).take(15).map {it.text.take(100)}
            val descriptions=device.findObjects(By.desc(Pattern.compile(".+"))).take(20).map {it.contentDescription.take(130)}
            throw AssertionError("Image seed not identified in actual DocumentsUI: $name; foreground=${device.currentPackageName}; text=$texts; descriptions=$descriptions")
        }
        file.click();app()
    }
    @Test fun isolatedCopyRejectsCorruptionThenPreservesActualDecodedPixels() {
        val input=fixture();val corrupt=input.copyOf();corrupt[corrupt.lastIndex]=(corrupt.last().toInt() xor 1).toByte()
        assertTrue(runCatching{PngLease(context).clean(corrupt)}.isFailure)
        val result=PngLease(context).clean(input)
        assertNotEquals(context.applicationInfo.uid,result.workerUid);assertNotEquals(android.os.Process.myPid(),result.workerPid)
        assertTrue(result.inetDenied);assertEquals(1,result.removed);assertTrue(PngMetadata.verifyDerivative(input,result.bytes()))
        val before=requireNotNull(BitmapFactory.decodeByteArray(input,0,input.size));val bytes=result.bytes()
        val after=requireNotNull(BitmapFactory.decodeByteArray(bytes,0,bytes.size))
        try {assertEquals(before.width,after.width);assertEquals(before.height,after.height)
            val a=IntArray(before.width*before.height);val b=IntArray(a.size)
            before.getPixels(a,0,before.width,0,0,before.width,before.height);after.getPixels(b,0,after.width,0,0,after.width,after.height)
            assertArrayEquals(a,b)
        } finally {before.recycle();after.recycle()}
        val service=context.packageManager.getServiceInfo(android.content.ComponentName(context,PngWorkerService::class.java),0)
        assertFalse(service.exported);assertTrue(service.flags and android.content.pm.ServiceInfo.FLAG_ISOLATED_PROCESS!=0)
    }
    @Test fun actualPngPickerNewCopyReadbackKeepsSourceAndRemovesMetadata() {
        val source=fixture();val name="r25-src-${UUID.randomUUID().toString().take(8)}.png";val original=seed(name,source)
        try {
            ActivityScenario.launch(PngCopyActivity::class.java).use {scenario->
                pick(name);val ready=await(scenario,"READY")
                assertTrue(ready.outputBytes<ready.inputBytes);assertTrue(ready.inetDenied==true)
                lateinit var activity:PngCopyActivity;scenario.onActivity {activity=it}
                NativeVisualEvidence.captureViews(activity,"r25-png-copy",listOf(R.id.png_copy_choose,R.id.png_copy_save,R.id.png_copy_discard,R.id.png_copy_close))
                button("png_copy_save").click();picker()
                val filename=requireNotNull(device.wait(Until.findObject(By.clazz("android.widget.EditText")),10000))
                filename.text="r25-clean-${UUID.randomUUID()}.png"
                requireNotNull(device.wait(Until.findObject(By.text(Pattern.compile("(?i)save"))),10000)).click();app();await(scenario,"VERIFIED")
                var output:Uri?=null;scenario.onActivity {output=it.verifiedDocument()}
                assertNotNull(output);assertNotEquals(original,output)
                val copy=context.contentResolver.openInputStream(output!!)!!.use{it.readBytes()}
                assertTrue(PngMetadata.verifyDerivative(source,copy));assertTrue(PngMetadata.clean(copy).report.removedChunkTypes.isEmpty())
                assertArrayEquals(source,context.contentResolver.openInputStream(original)!!.use{it.readBytes()})
                scenario.onActivity {a->
                    for(id in listOf(R.id.png_copy_choose,R.id.png_copy_save,R.id.png_copy_discard,R.id.png_copy_close)) {
                        val v=a.findViewById<Button>(id);assertTrue(v.height/a.resources.displayMetrics.density>=47.99f)
                    }
                }
            }
        } finally {context.contentResolver.delete(original,null,null)}
    }
    @Test fun recreationCannotExportOldPreparedCopyAndPickerCancelDoesNotRead() {
        val source=fixture();val name="r25-rec-${UUID.randomUUID().toString().take(8)}.png";val original=seed(name,source)
        try {
            ActivityScenario.launch(PngCopyActivity::class.java).use {scenario->
                button("png_copy_choose").click();picker();device.pressBack();app()
                scenario.onActivity {assertEquals("EMPTY",it.observation().phase);assertFalse(it.findViewById<Button>(R.id.png_copy_save).isEnabled)}
                pick(name);await(scenario,"READY");scenario.recreate()
                scenario.onActivity {assertEquals("EMPTY",it.observation().phase);assertFalse(it.findViewById<Button>(R.id.png_copy_save).isEnabled);assertNull(it.verifiedDocument())}
                assertArrayEquals(source,context.contentResolver.openInputStream(original)!!.use{it.readBytes()})
            }
        } finally {context.contentResolver.delete(original,null,null)}
    }
}
