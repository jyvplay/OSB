// SPDX-License-Identifier: Apache-2.0
package dev.fusion.shell

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Rect
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.InputDevice
import android.view.MotionEvent
import android.view.PixelCopy
import android.view.SurfaceView
import android.widget.Button
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import dev.fusion.media.LocalPlaybackActivity
import dev.fusion.media.PlaybackEpoch
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Actual original MP4 input, platform decoder and changing Surface pixels. Not provider-site or audible-fidelity evidence. */
class R25LocalVideoDeviceTest {
    @get:Rule val disposable=DisposableEmulatorRule()
    private val instrumentation get()=InstrumentationRegistry.getInstrumentation()
    private val context get()=ApplicationProvider.getApplicationContext<Context>()
    private fun observation(s:ActivityScenario<LocalPlaybackActivity>):LocalPlaybackActivity.Observation {
        lateinit var result:LocalPlaybackActivity.Observation;s.onActivity{result=it.observation()};return result
    }
    private fun await(s:ActivityScenario<LocalPlaybackActivity>,test:(LocalPlaybackActivity.Observation)->Boolean):LocalPlaybackActivity.Observation {
        val end=SystemClock.elapsedRealtime()+10000;var result=observation(s)
        while(SystemClock.elapsedRealtime()<end) {result=observation(s);if(test(result))return result;SystemClock.sleep(50)}
        throw AssertionError("Actual video state deadline: $result")
    }
    private fun play(s:ActivityScenario<LocalPlaybackActivity>) {
        val bounds=Rect()
        s.onActivity {a->val v=a.findViewById<Button>(dev.fusion.media.R.id.player_play);val xy=IntArray(2);v.getLocationOnScreen(xy)
            bounds.set(xy[0],xy[1],xy[0]+v.width,xy[1]+v.height);val visible=Rect();a.window.decorView.getWindowVisibleDisplayFrame(visible)
            assertTrue(a.hasWindowFocus() && v.isEnabled && v.isShown && visible.contains(bounds))}
        val start=SystemClock.uptimeMillis()
        for((action,time) in listOf(MotionEvent.ACTION_DOWN to start,MotionEvent.ACTION_UP to start+40)) {
            val event=MotionEvent.obtain(start,time,action,bounds.exactCenterX(),bounds.exactCenterY(),0).apply {source=InputDevice.SOURCE_TOUCHSCREEN}
            try {assertTrue(instrumentation.uiAutomation.injectInputEvent(event,true))} finally {event.recycle()}
        }
    }
    private fun frame(s:ActivityScenario<LocalPlaybackActivity>):Bitmap? {
        val result=Bitmap.createBitmap(64,48,Bitmap.Config.ARGB_8888);val done=CountDownLatch(1);var status=PixelCopy.ERROR_UNKNOWN
        s.onActivity {a->val surface=a.findViewById<SurfaceView>(dev.fusion.media.R.id.player_surface)
            assertTrue(surface.isShown && surface.holder.surface.isValid && surface.width>0 && surface.height>0)
            PixelCopy.request(surface,result,{value->status=value;done.countDown()},Handler(Looper.getMainLooper()))}
        if(!done.await(3,TimeUnit.SECONDS))throw AssertionError("Actual video Surface copy did not complete; no frame inferred")
        if(status==PixelCopy.ERROR_SOURCE_NO_DATA) {result.recycle();return null}
        if(status!=PixelCopy.SUCCESS) {result.recycle();throw AssertionError("Actual video Surface copy failed: $status")}
        return result
    }
    @Test fun actualMp4DecoderProducesOrderedChangingSurfacePixelsAndReleasesOnHome() {
        val input=instrumentation.context.assets.open("r25-color-frames.mp4").use{it.readBytes()}
        val digest=MessageDigest.getInstance("SHA-256").digest(input).joinToString(""){"%02x".format(it.toInt() and 255)}
        assertEquals("970274fa5f8f5f653bc6e2a57f3d3731d5c228edbae7102fb3dad799e8ba5c4d",digest)
        val file=File(context.cacheDir,"r24c-frames.mp4");file.writeBytes(input)
        val folder=InstrumentationRegistry.getArguments().getString("additionalTestOutputDir")?.let(::File) ?: File(context.getExternalFilesDir(null),"r25-video-observations")
        check(folder.exists() || folder.mkdirs())
        try {
            val intent=Intent(context,LocalPlaybackActivity::class.java).setData(Uri.parse("content://${context.packageName}.media-fixture/frames.mp4"))
            ActivityScenario.launch<LocalPlaybackActivity>(intent).use {s->
                val ready=await(s){it.phase==PlaybackEpoch.Phase.READY}
                assertTrue(ready.durationMs!! in 5900..6100);assertEquals(0,ready.positionMs)
                SystemClock.sleep(250);assertEquals("No autoplay",0,observation(s).positionMs)
                play(s);await(s){it.phase==PlaybackEpoch.Phase.PLAYING && (it.positionMs?:0)>0}
                val seen=mutableListOf<String>();val samples=JSONArray();var lastPosition=-1;val deadline=SystemClock.elapsedRealtime()+7500
                var last="No actual frame"
                while(SystemClock.elapsedRealtime()<deadline && seen.size<3) {
                    val image=frame(s)
                    if(image!=null) try {
                        var red=0L;var green=0L;var blue=0L;var count=0
                        for(y in 12 until 36) for(x in 16 until 48) {val c=image.getPixel(x,y);red+=Color.red(c);green+=Color.green(c);blue+=Color.blue(c);count++}
                        val r=(red/count).toInt();val g=(green/count).toInt();val b=(blue/count).toInt();val actual=observation(s)
                        val color=when {r>180 && g<80 && b<80->"red";g>80 && r<60 && b<60->"green";b>180 && r<80 && g<80->"blue";else->"other"}
                        last="color=$color RGB=$r/$g/$b state=$actual"
                        val expected=listOf("red","green","blue")[seen.size]
                        if(color==expected) {
                            assertEquals(PlaybackEpoch.Phase.PLAYING,actual.phase)
                            val position=requireNotNull(actual.positionMs)
                            assertTrue("Decoded-frame progression must accompany the real player clock",position>lastPosition)
                            if(color=="green")assertTrue(position>=1500)
                            if(color=="blue")assertTrue(position>=3500)
                            lastPosition=position
                            seen.add(color)
                            samples.put(JSONObject().put("color",color).put("position_ms",actual.positionMs).put("mean_rgb",JSONArray(listOf(r,g,b))))
                            File(folder,"r25-decoded-$color.png").outputStream().use{assertTrue(image.compress(Bitmap.CompressFormat.PNG,100,it))}
                        }
                    } finally {image.recycle()}
                    if(seen.size<3)SystemClock.sleep(80)
                }
                assertEquals("Actual changing decoded pixels not observed: $last",listOf("red","green","blue"),seen)
                UiDevice.getInstance(instrumentation).pressHome()
                await(s){it.phase==PlaybackEpoch.Phase.CLOSED && !it.hasPlayer}
                val report=JSONObject().put("origin","android-instrumentation").put("api",android.os.Build.VERSION.SDK_INT)
                    .put("input_sha256",digest).put("samples",samples).put("surface_copy","PixelCopy(SurfaceView), actual decoded buffers")
                    .put("scope","Controlled silent local AVC fixture only; no audible quality, arbitrary codecs/DRM, provider-site streaming, hardware decoder or Studio GUI acceptance")
                File(folder,"r25-video-evidence.json").writeText(report.toString(2))
            }
        } finally {file.delete()}
    }
}
