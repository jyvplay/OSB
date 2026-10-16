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
    @Test fun secureWindowExcludesActualScreenshotMarkerWithVisiblePositiveControl() {
        val instrumentation=InstrumentationRegistry.getInstrumentation()
        val context=ApplicationProvider.getApplicationContext<android.content.Context>()
        fun markers():Int {
            val image=requireNotNull(instrumentation.uiAutomation.takeScreenshot())
            try {var n=0;for(y in 0 until image.height step 8) for(x in 0 until image.width step 8) if(image.getPixel(x,y)==Color.rgb(237,19,180)) n++;return n}
            finally {image.recycle()}
        }
        fun capture(unsecured:Boolean):Int {
            val intent=android.content.Intent(context,if(unsecured) ScreenshotControlActivity::class.java else PrivacyActivity::class.java).putExtra("unsecuredPositiveControl",true)
            return ActivityScenario.launch<android.app.Activity>(intent).use {scenario ->
                if(!unsecured) scenario.onActivity {a ->a.setContentView(TextView(a).apply {text="R20 screenshot marker";setBackgroundColor(Color.rgb(237,19,180))})}
                assertTrue(UiDevice.getInstance(instrumentation).wait(androidx.test.uiautomator.Until.hasObject(androidx.test.uiautomator.By.text("R20 screenshot marker")),10000))
                UiDevice.getInstance(instrumentation).waitForIdle()
                scenario.onActivity {assertEquals(!unsecured,it.window.attributes.flags and WindowManager.LayoutParams.FLAG_SECURE!=0)}
                markers()
            }
        }
        // Patch attack: protected from first publication, not a late flag followed by an old-frame screenshot.
        assertEquals("Initially secure pixels leaked",0,capture(false))
        assertTrue("Missing separately launched unprotected positive control",capture(true)>1000)
        assertEquals("Reopened secure window leaked after unprotected control",0,capture(false))
    }
}
