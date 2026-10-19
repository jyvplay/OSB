package dev.fusion.privacy

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.database.ContentObserver
import android.hardware.display.DisplayManager
import android.hardware.input.InputManager
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import android.view.ViewTreeObserver
import java.io.Closeable

/** Test-only observation of actual callbacks. Never fabricates an event, rearms, retries or permits a failed assertion. */
internal class PrivacySignalTrace(private val activity: Activity) : Closeable {
    private val events = mutableListOf<String>()
    private fun record(event: String) { synchronized(events) { if (events.size < 64) events.add("${SystemClock.elapsedRealtime()}:$event") } }
    fun snapshot(): String = synchronized(events) { events.joinToString(";") }
    fun mark(label: String) = record("test-phase:$label")
    private val handler = Handler(Looper.getMainLooper())
    private val input = activity.getSystemService(InputManager::class.java)
    private val display = activity.getSystemService(DisplayManager::class.java)
    private val inputs = object : InputManager.InputDeviceListener {
        override fun onInputDeviceAdded(id: Int) = record("actual-input-added:$id")
        override fun onInputDeviceRemoved(id: Int) = record("actual-input-removed:$id")
        override fun onInputDeviceChanged(id: Int) = record("actual-input-changed:$id")
    }
    private val displays = object : DisplayManager.DisplayListener {
        override fun onDisplayAdded(id: Int) = record("actual-display-added:$id")
        override fun onDisplayRemoved(id: Int) = record("actual-display-removed:$id")
        override fun onDisplayChanged(id: Int) = record("actual-display-changed:$id")
    }
    private val configuration = object : ContentObserver(handler) {
        override fun onChange(selfChange: Boolean) = record("actual-ime-or-accessibility-notification")
    }
    private val focus = ViewTreeObserver.OnWindowFocusChangeListener { record("actual-window-focus:$it") }
    private val screen = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) { record("actual-screen-event:${intent.action}") }
    }
    init {
        check(Looper.myLooper() == Looper.getMainLooper())
        input.registerInputDeviceListener(inputs, handler)
        display.registerDisplayListener(displays, handler)
        for (setting in listOf(Settings.Secure.DEFAULT_INPUT_METHOD, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES, Settings.Secure.ACCESSIBILITY_ENABLED))
            activity.contentResolver.registerContentObserver(Settings.Secure.getUriFor(setting), false, configuration)
        activity.window.decorView.viewTreeObserver.addOnWindowFocusChangeListener(focus)
        activity.registerReceiver(screen, IntentFilter().apply { addAction(Intent.ACTION_SCREEN_OFF); addAction(Intent.ACTION_SCREEN_ON) }, Context.RECEIVER_NOT_EXPORTED)
        mark("observer-attached")
    }
    override fun close() {
        input.unregisterInputDeviceListener(inputs); display.unregisterDisplayListener(displays)
        activity.contentResolver.unregisterContentObserver(configuration)
        activity.window.decorView.viewTreeObserver.takeIf { it.isAlive }?.removeOnWindowFocusChangeListener(focus)
        activity.unregisterReceiver(screen)
        val instrumentation = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation()
        val folder = androidx.test.platform.app.InstrumentationRegistry.getArguments().getString("additionalTestOutputDir")?.let { java.io.File(it) }
            ?: java.io.File(instrumentation.targetContext.getExternalFilesDir(null), "r24c-privacy-readiness")
        check(folder.exists() || folder.mkdirs())
        val copy = synchronized(events) { events.toList() }
        val report = org.json.JSONObject().put("origin", "android-instrumentation").put("api", android.os.Build.VERSION.SDK_INT)
            .put("events", org.json.JSONArray(copy)).put("event_limit_reached", copy.size == 64)
            .put("scope", "Actual test-only event observations; not retrospective cause, hardware authenticity, or an excuse to pass missing UI")
        java.io.File(folder, "r24c-privacy-reveal-events.json").writeText(report.toString())
    }
}
