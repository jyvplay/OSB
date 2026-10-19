package dev.fusion.privacy

import android.app.Activity
import android.os.Bundle
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import dev.fusion.security.TouchAdmission

/** Platform mitigation, not protection from a privileged OS/host or trusted accessibility tool. */
abstract class GuardedPrivacyActivity : Activity() {
    private val touches = TouchAdmission()
    private val inputRearm = dev.fusion.security.InputRearm()
    private var inputManager: android.hardware.input.InputManager? = null
    private var displayManager: android.hardware.display.DisplayManager? = null
    private var postureReason = "Input environment changed. Restart explicitly with an empty workspace. This is not keylogger detection."
    private var observerRegistered = false
    private val inputListener = object : android.hardware.input.InputManager.InputDeviceListener {
        override fun onInputDeviceAdded(deviceId: Int) = environmentChanged("An input device was added")
        override fun onInputDeviceRemoved(deviceId: Int) = environmentChanged("An input device was removed")
        override fun onInputDeviceChanged(deviceId: Int) = environmentChanged("Input-device properties changed")
    }
    private val displayListener = object : android.hardware.display.DisplayManager.DisplayListener {
        override fun onDisplayAdded(displayId: Int) = environmentChanged("A display was added")
        override fun onDisplayRemoved(displayId: Int) = environmentChanged("A display was removed")
        override fun onDisplayChanged(displayId: Int) {
            if (displayId != android.view.Display.DEFAULT_DISPLAY) environmentChanged("An additional display changed")
        }
    }
    private val configurationObserver = object : android.database.ContentObserver(android.os.Handler(android.os.Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean) = environmentChanged("Keyboard or accessibility configuration changed")
    }
    private fun environmentChanged(reason: String) {
        inputRearm.changed(); touches.reset()
        postureReason = "$reason. Workspace discarded; review the change before starting empty. No hardware/keylogger attestation or forensic-erasure guarantee."
        discardWorkspace(postureReason)
    }
    private fun restoreOrHold() {
        if (inputRearm.armed) rebuildWorkspace() else showDiscarded(postureReason)
    }
    private var resumed = false
    private var screenOn=false
    private var receiverRegistered=false
    private fun interactive()=getSystemService(android.os.PowerManager::class.java)?.isInteractive==true
    private val displayState=object:android.content.BroadcastReceiver() {
        override fun onReceive(context:android.content.Context,intent:android.content.Intent) {
            if(intent.action !in setOf(android.content.Intent.ACTION_SCREEN_OFF,android.content.Intent.ACTION_SCREEN_ON)) return
            touches.reset();screenOn=interactive()
            if(intent.action==android.content.Intent.ACTION_SCREEN_OFF) screenOn=false
            if(!screenOn) discardWorkspace("Workspace discarded on screen off. No forensic-erasure guarantee.")
            else if(resumed && screenOn && hasWindowFocus()) restoreOrHold()
        }
    }
    protected abstract fun rebuildWorkspace()
    protected abstract fun discardWorkspace(reason: String)
    private fun windowReady(): Boolean = resumed && screenOn && hasWindowFocus()
    protected fun acceptsInteraction(): Boolean = windowReady() && inputRearm.armed

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(null)
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        window.setHideOverlayWindows(true)
        setRecentsScreenshotEnabled(false)
        screenOn=interactive()
        registerReceiver(displayState,android.content.IntentFilter().apply {
            addAction(android.content.Intent.ACTION_SCREEN_OFF);addAction(android.content.Intent.ACTION_SCREEN_ON)
        },android.content.Context.RECEIVER_NOT_EXPORTED)
        receiverRegistered=true
        val handler = android.os.Handler(android.os.Looper.getMainLooper())
        inputManager = getSystemService(android.hardware.input.InputManager::class.java)
        displayManager = getSystemService(android.hardware.display.DisplayManager::class.java)
        try {
            requireNotNull(inputManager).registerInputDeviceListener(inputListener, handler)
            requireNotNull(displayManager).registerDisplayListener(displayListener, handler)
            for (setting in listOf(android.provider.Settings.Secure.DEFAULT_INPUT_METHOD,
                android.provider.Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES, android.provider.Settings.Secure.ACCESSIBILITY_ENABLED)) {
                contentResolver.registerContentObserver(android.provider.Settings.Secure.getUriFor(setting), false, configurationObserver)
                observerRegistered = true
            }
        } catch (_: RuntimeException) {
            environmentChanged("Some input-change observations are unavailable")
        }
    }

    override fun onResume() {
        super.onResume();resumed=true;screenOn=interactive();touches.reset()
        if(screenOn) restoreOrHold() else discardWorkspace("Display is not interactive; workspace discarded.")
    }
    override fun onDestroy() {
        if(receiverRegistered) {unregisterReceiver(displayState);receiverRegistered=false}
        inputManager?.unregisterInputDeviceListener(inputListener)
        displayManager?.unregisterDisplayListener(displayListener)
        if (observerRegistered) contentResolver.unregisterContentObserver(configurationObserver)
        super.onDestroy()
    }
    override fun onPause() {
        resumed = false; touches.reset(); discardWorkspace("Workspace discarded on background.")
        super.onPause()
    }
    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        touches.reset()
        if (!hasFocus) discardWorkspace("Workspace discarded on focus loss.")
        else if (resumed && screenOn) restoreOrHold()
    }
    override fun onSaveInstanceState(outState: Bundle) { super.onSaveInstanceState(outState); outState.clear() }

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        // While latched, the only live UI is the explicit empty-restart screen.
        if (!windowReady()) { touches.reset(); return true }
        val phase = when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> TouchAdmission.Phase.DOWN
            MotionEvent.ACTION_MOVE, MotionEvent.ACTION_POINTER_DOWN, MotionEvent.ACTION_POINTER_UP -> TouchAdmission.Phase.CONTINUE
            MotionEvent.ACTION_UP -> TouchAdmission.Phase.UP
            MotionEvent.ACTION_CANCEL -> TouchAdmission.Phase.CANCEL
            else -> TouchAdmission.Phase.OTHER
        }
        val obscured = event.flags and MotionEvent.FLAG_WINDOW_IS_OBSCURED != 0
        val partial = event.flags and MotionEvent.FLAG_WINDOW_IS_PARTIALLY_OBSCURED != 0
        if (touches.admit(phase, obscured, partial)) return super.dispatchTouchEvent(event)
        // Cancel an already pressed child BEFORE replacing it. Never deliver the rejected UP.
        val cancel = MotionEvent.obtain(event)
        try { cancel.action = MotionEvent.ACTION_CANCEL; super.dispatchTouchEvent(cancel) }
        finally { cancel.recycle() }
        if (obscured || partial) discardWorkspace("Obscured input refused; workspace discarded. Remove the overlay and reopen this view.")
        return true
    }

    protected fun showDiscarded(reason: String) {
        val observedEpoch = inputRearm.epoch
        val column = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; protect(this) }
        column.addView(TextView(this).apply { text = reason; textSize = 18f; protect(this) })
        column.addView(Button(this).apply {
            id = R.id.restart_workspace; text = "Start an empty workspace"; protect(this)
            setOnClickListener { if (windowReady() && inputRearm.restart(observedEpoch)) rebuildWorkspace() }
        })
        setContentView(column)
    }

    protected fun protect(view: View) {
        view.isSaveEnabled = false; view.isSaveFromParentEnabled = false
        view.filterTouchesWhenObscured = true
        view.importantForAutofill = View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS
        view.importantForContentCapture = View.IMPORTANT_FOR_CONTENT_CAPTURE_NO_EXCLUDE_DESCENDANTS
        // Does NOT exclude services that identify as legitimate accessibility tools.
        view.setAccessibilityDataSensitive(View.ACCESSIBILITY_DATA_SENSITIVE_YES)
    }
}
