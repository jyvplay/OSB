package dev.fusion.privacy

import android.app.Activity
import android.view.ViewTreeObserver
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Real window callback readiness, not a fabricated lifecycle/focus event or a retrying test rule. */
internal object PrivacyDeviceReady {
    fun <T:Activity> focus(scenario:ActivityScenario<T>,expected:Boolean=true) {
        val ready=CountDownLatch(1)
        var observer:ViewTreeObserver?=null
        val listener=ViewTreeObserver.OnWindowFocusChangeListener {focused ->if(focused==expected) ready.countDown()}
        scenario.onActivity {activity ->
            observer=activity.window.decorView.viewTreeObserver
            observer!!.addOnWindowFocusChangeListener(listener)
            if(activity.hasWindowFocus()==expected) ready.countDown()
        }
        try {
            assertTrue("Actual window focus did not become $expected",ready.await(10,TimeUnit.SECONDS))
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()
            scenario.onActivity {assertEquals("Focus changed before action staging",expected,it.hasWindowFocus())}
        } finally {scenario.onActivity {observer?.takeIf {it.isAlive}?.removeOnWindowFocusChangeListener(listener)}}
    }
}
