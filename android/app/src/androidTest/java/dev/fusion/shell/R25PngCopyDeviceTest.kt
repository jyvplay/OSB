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
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.File
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
    private fun app() {
        // DocumentsUI disappearing is not sufficient: a third-party photo app can be
        // foreground instead of returning the result to our activity.
        if (!device.wait(Until.hasObject(By.res(context.packageName,"png_copy_choose")),15000)) {
            val actions=device.findObjects(By.clickable(true)).take(18).map {
                "${it.className} id=${it.resourceName} text=${it.text.orEmpty().take(60)} desc=${it.contentDescription.orEmpty().take(80)}"
            }
            throw AssertionError("Picker did not return to PNG activity; foreground=${device.currentPackageName}; actions=$actions")
        }
    }
    private fun seed(name:String,bytes:ByteArray):Uri {
        val uri=requireNotNull(context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI,ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME,name);put(MediaStore.MediaColumns.MIME_TYPE,"image/png")
            put(MediaStore.MediaColumns.RELATIVE_PATH,Environment.DIRECTORY_PICTURES);put(MediaStore.MediaColumns.IS_PENDING,1)
        }))
        context.contentResolver.openOutputStream(uri,"wt")!!.use{it.write(bytes)}
        context.contentResolver.update(uri,ContentValues().apply{put(MediaStore.MediaColumns.IS_PENDING,0)},null,null)
        return uri
    }
    private fun selectPickerRoot(label:String) {
        // This is a user-visible root navigation in the actual system picker, not a
        // substituted URI. Limit it to the disposable English-language emulator.
        check(label in setOf("Recent","Downloads"))
        val pkg=device.currentPackageName.orEmpty()
        check(pkg.endsWith("documentsui")) {"Unexpected picker foreground: $pkg"}
        requireNotNull(device.wait(Until.findObject(By.desc("Show roots")),10000)) {
            "DocumentsUI root drawer unavailable in $pkg"
        }.click()
        val roots=requireNotNull(device.wait(Until.findObject(By.res(pkg,"container_roots")),10000)) {
            "DocumentsUI root list unavailable in $pkg"
        }
        val matches=roots.findObjects(By.text(label))
        check(matches.size==1) {"Expected one $label root, got ${matches.size}; labels=${roots.findObjects(By.text(Pattern.compile(".+"))).map{it.text}}"}
        matches.single().click()
        check(device.wait(Until.hasObject(By.text(label)),10000)) {"DocumentsUI did not navigate to $label"}
    }
    private fun pick(name:String, forceDownloads:Boolean=false) {
        button("png_copy_choose").click();picker()
        // Exercise a remembered non-fixture root explicitly in the recreation case.
        if(forceDownloads) selectPickerRoot("Downloads")
        // A filename may also occur in the accessibility description of the separate
        // preview icon. Clicking that icon opens an external viewer, not the document.
        var file:androidx.test.uiautomator.UiObject2?=null
        var selector=""
        fun awaitFilename(timeoutMs:Long) {
            val end=SystemClock.elapsedRealtime()+timeoutMs
            while(file==null && SystemClock.elapsedRealtime()<end) {
                val textMatches=device.findObjects(By.text(name))
                check(textMatches.size<=1) {"Ambiguous exact picker titles for $name"}
                file=textMatches.singleOrNull()
                if(file!=null) selector="text" else {
                    val descriptions=device.findObjects(By.desc(Pattern.compile("(?s).*"+Pattern.quote(name)+".*")))
                    check(descriptions.size<=1) {"Ambiguous picker descriptions for $name"}
                    file=descriptions.singleOrNull()
                    if(file!=null) selector="description"
                }
                if(file==null)SystemClock.sleep(50)
            }
        }
        awaitFilename(2000)
        var navigation=if(forceDownloads) "downloads" else "initial"
        if(file==null) {
            // The full suite actually started in Downloads while our fixture was in
            // Pictures. Recent is the route observed to list this freshly seeded image.
            selectPickerRoot("Recent")
            navigation=if(forceDownloads) "downloads-to-recent" else "recent"
            awaitFilename(10000)
        }
        check(!forceDownloads || navigation=="downloads-to-recent") {"Fixture was unexpectedly visible in Downloads"}
        if(file==null) {
            val texts=device.findObjects(By.text(Pattern.compile(".+"))).take(15).map {it.text.take(100)}
            val descriptions=device.findObjects(By.desc(Pattern.compile(".+"))).take(20).map {it.contentDescription.take(130)}
            throw AssertionError("Image seed not identified in actual DocumentsUI: $name; foreground=${device.currentPackageName}; text=$texts; descriptions=$descriptions")
        }
        val matched=checkNotNull(file)
        var target=matched
        var strategy="filename-text"
        var tap:android.graphics.Point?=null
        var previewBounds:android.graphics.Rect?=null
        var itemBounds:android.graphics.Rect?=null
        var gridId:String?=null
        if(matched.resourceName.orEmpty().endsWith(":id/preview_icon")) {
            // The observed API 34 accessibility tree hides the title and has no item_root id.
            // Walk from this uniquely matched preview to its direct child of dir_list; tap that
            // item's lower body, outside the preview hotspot. This is a real native tap, never a
            // direct picker-result injection; a different/unrecognizable tree fails closed.
            val packageName=matched.resourceName.orEmpty().substringBefore(":id/")
            check(packageName.endsWith("documentsui")) {"Unexpected picker package $packageName"}
            val preview=matched.visibleBounds
            val lineage=ArrayList<String>()
            var child=matched
            var parent=child.parent
            var depth=0
            while(parent!=null && parent.resourceName!="$packageName:id/dir_list" && depth<8) {
                lineage.add("${parent.className} id=${parent.resourceName} bounds=${parent.visibleBounds}")
                child=parent;parent=child.parent;depth++
            }
            val grid=checkNotNull(parent) {"No DocumentsUI grid ancestor for $name; preview=$preview; ancestors=$lineage"}
            check(grid.resourceName=="$packageName:id/dir_list" && child!=matched) {"Unexpected DocumentsUI item ancestry for $name; ancestors=$lineage"}
            gridId=grid.resourceName
            target=child
            val item=target.visibleBounds
            check(item.width()>=48 && item.height()>=48 && item.contains(preview) && !preview.isEmpty) {"Invalid picker item: item=$item preview=$preview"}
            val point=android.graphics.Point(item.centerX(),item.bottom-maxOf(2,item.height()/10))
            check(item.contains(point.x,point.y) && !preview.contains(point.x,point.y)) {"Picker item body overlaps preview: item=$item preview=$preview tap=$point"}
            tap=point;previewBounds=preview;itemBounds=item
            strategy="preview-grid-item-body"
        } else check(selector=="text") {"Ambiguous filename description is not an admitted selection target: ${matched.resourceName}"}
        // Persist only disposable-fixture selector metadata, not user documents or image bytes.
        require(name.matches(Regex("r25-(src|rec)-[a-f0-9]{8}\\.png")))
        val instrumentation=InstrumentationRegistry.getInstrumentation()
        val folder=InstrumentationRegistry.getArguments().getString("additionalTestOutputDir")?.let(::File)
            ?: File(instrumentation.targetContext.getExternalFilesDir(null),"r25-native-captures")
        check(folder.exists() || folder.mkdirs())
        val observation=JSONObject()
            .put("schema",1).put("origin","android-instrumentation").put("api",android.os.Build.VERSION.SDK_INT)
            .put("fixture",name).put("foreground",device.currentPackageName.orEmpty().take(128))
            .put("selector",selector).put("navigation",navigation).put("matched_id",matched.resourceName.orEmpty().take(128))
            .put("matched_description",matched.contentDescription.orEmpty().take(160))
            .put("strategy",strategy).put("target_id",target.resourceName.orEmpty().take(128))
        fun bounds(rect:android.graphics.Rect)=JSONArray(listOf(rect.left,rect.top,rect.right,rect.bottom))
        if(tap!=null) observation.put("tap",JSONArray(listOf(tap.x,tap.y)))
            .put("item_bounds",bounds(requireNotNull(itemBounds))).put("preview_bounds",bounds(requireNotNull(previewBounds)))
            .put("grid_id",requireNotNull(gridId)).put("target_class",target.className.orEmpty().take(128))
        File(folder,"r25-picker-selection-${name.removeSuffix(".png")}.json").writeText(observation.toString())
        val point=tap
        if(point==null) target.click() else assertTrue("Native card-body tap was not injected",device.click(point.x,point.y))
        app()
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
                pick(name,forceDownloads=true);await(scenario,"READY");scenario.recreate()
                scenario.onActivity {assertEquals("EMPTY",it.observation().phase);assertFalse(it.findViewById<Button>(R.id.png_copy_save).isEnabled);assertNull(it.verifiedDocument())}
                assertArrayEquals(source,context.contentResolver.openInputStream(original)!!.use{it.readBytes()})
            }
        } finally {context.contentResolver.delete(original,null,null)}
    }
}
