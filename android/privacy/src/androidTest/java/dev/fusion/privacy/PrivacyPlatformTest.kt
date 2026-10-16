package dev.fusion.privacy

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Process
import android.system.Os
import android.system.OsConstants
import android.system.ErrnoException
import android.view.WindowManager
import android.widget.TextView
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import org.junit.Assert.*
import org.junit.Test
import java.io.FileDescriptor

class PrivacyPlatformTest {
    @Test fun noInternetPermissionDeniesInetSocketsButUnixPairTransfersRealBytes() {
        val context=ApplicationProvider.getApplicationContext<android.content.Context>()
        assertEquals("dev.fusion.privacy",context.packageName)
        assertEquals(PackageManager.PERMISSION_DENIED,context.checkPermission(Manifest.permission.INTERNET,Process.myPid(),Process.myUid()))
        for(domain in listOf(OsConstants.AF_INET,OsConstants.AF_INET6)) {
            try { val fd=Os.socket(domain,OsConstants.SOCK_STREAM,0);Os.close(fd);fail("Unexpected Internet socket") }
            catch(e:ErrnoException) {assertTrue("Wrong errno: ${e.errno}",e.errno==OsConstants.EACCES || e.errno==OsConstants.EPERM)}
        }
        val a=FileDescriptor();val b=FileDescriptor()
        Os.socketpair(OsConstants.AF_UNIX,OsConstants.SOCK_STREAM,0,a,b)
        try {val bytes="unix IPC is still possible".toByteArray();assertEquals(bytes.size,Os.write(a,bytes,0,bytes.size));val out=ByteArray(bytes.size);assertEquals(bytes.size,Os.read(b,out,0,out.size));assertArrayEquals(bytes,out)}
        finally {Os.close(a);Os.close(b)}
    }
    private val instrumentation get()=InstrumentationRegistry.getInstrumentation()
    private data class Marker(val view:TextView,val nonce:String)
    private fun assertCurrent(activity:android.app.Activity,marker:Marker,unsecured:Boolean) {
        assertTrue("Capture lost window focus",activity.hasWindowFocus())
        assertEquals(!unsecured,activity.window.attributes.flags and WindowManager.LayoutParams.FLAG_SECURE!=0)
        assertSame("Lifecycle replaced the staged marker",marker.view,activity.findViewById<TextView>(marker.view.id))
        assertTrue("Marker not attached/shown",marker.view.isAttachedToWindow && marker.view.isShown)
        assertTrue("Marker has no actual laid-out area",marker.view.width>0 && marker.view.height>0)
        assertEquals(marker.nonce,marker.view.text.toString())
    }
    private fun installCommittedMarker(scenario:ActivityScenario<android.app.Activity>,unsecured:Boolean):Marker {
        PrivacyDeviceReady.focus(scenario,true)
        val committed=java.util.concurrent.CountDownLatch(1)
        val nonce="R23 actual frame "+java.util.UUID.randomUUID()
        lateinit var marker:Marker
        scenario.onActivity { activity ->
            assertEquals(!unsecured,activity.window.attributes.flags and WindowManager.LayoutParams.FLAG_SECURE!=0)
            val view=TextView(activity).apply {id=android.view.View.generateViewId();text=nonce;setBackgroundColor(Color.rgb(237,19,180))}
            activity.setContentView(view);marker=Marker(view,nonce)
            assertTrue("Hardware frame verifier unavailable; do not infer a blank-screen pass",view.isHardwareAccelerated)
            view.viewTreeObserver.registerFrameCommitCallback {committed.countDown()}
            view.invalidate()
        }
        assertTrue("No actual renderer frame commit",committed.await(10,java.util.concurrent.TimeUnit.SECONDS))
        instrumentation.waitForIdleSync()
        assertNotNull("Fresh nonce marker absent from real UI",UiDevice.getInstance(instrumentation).wait(
            androidx.test.uiautomator.Until.findObject(androidx.test.uiautomator.By.text(nonce)),10000))
        scenario.onActivity {assertCurrent(it,marker,unsecured)}
        return marker
    }
    private fun captureFrame(scenario:ActivityScenario<android.app.Activity>,unsecured:Boolean):Int {
        val marker=installCommittedMarker(scenario,unsecured)
        val image=requireNotNull(instrumentation.uiAutomation.takeScreenshot())
        val count=try {
            var n=0
            for(y in 0 until image.height step 8) for(x in 0 until image.width step 8)
                if(image.getPixel(x,y)==Color.rgb(237,19,180)) n++
            n
        } finally {image.recycle()}
        // Zero pixels are not accepted if lifecycle/focus removed the test content during capture.
        scenario.onActivity {assertCurrent(it,marker,unsecured)}
        return count
    }
    private fun capture(unsecured:Boolean):Int {
        val context=ApplicationProvider.getApplicationContext<android.content.Context>()
        val intent=android.content.Intent(context,if(unsecured) ScreenshotControlActivity::class.java else PrivacyActivity::class.java)
            .putExtra("unsecuredPositiveControl",true) // Production must continue to ignore this attempted bypass.
        return ActivityScenario.launch<android.app.Activity>(intent).use {captureFrame(it,unsecured)}
    }
    @Test fun secureWindowExcludesActualScreenshotMarkerWithVisiblePositiveControl() {
        // Secure from first publication, not a late flag or an empty/replaced marker pretending to be protected.
        assertEquals("Initially secure pixels leaked",0,capture(false))
        assertTrue("Missing separately launched unprotected positive control",capture(true)>1000)
        assertEquals("Reopened secure window leaked after unprotected control",0,capture(false))
    }
    @Test fun realFocusableDialogInvalidatesMarkerBeforeFreshCommittedSecureCapture() {
        assertTrue("Missing actual unprotected control before focus attack",capture(true)>1000)
        val context=ApplicationProvider.getApplicationContext<android.content.Context>()
        ActivityScenario.launch<android.app.Activity>(android.content.Intent(context,PrivacyActivity::class.java)).use {scenario ->
            val retired=installCommittedMarker(scenario,false)
            var dialog:android.app.Dialog?=null
            try {
                scenario.onActivity {activity ->dialog=android.app.Dialog(activity).apply {
                    setContentView(TextView(activity).apply {text="R23 real focus control"});show()
                }}
                PrivacyDeviceReady.focus(scenario,false)
                scenario.onActivity {activity ->
                    assertFalse("Focus loss retained injected marker",retired.view.isAttachedToWindow)
                    assertNull(activity.findViewById<TextView>(retired.view.id))
                    requireNotNull(dialog).dismiss()
                }
                PrivacyDeviceReady.focus(scenario,true)
                scenario.onActivity {assertNull("Focus return restored stale marker",it.findViewById<TextView>(retired.view.id))}
                assertEquals("Fresh committed secure frame leaked after actual focus cycle",0,captureFrame(scenario,false))
            } finally {scenario.onActivity {dialog?.dismiss()}}
        }
        assertTrue("Missing actual unprotected control after focus attack",capture(true)>1000)
    }
}
