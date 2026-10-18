package dev.fusion.privacy

import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.KeyEvent
import android.view.View
import android.view.WindowInsets
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import dev.fusion.security.VolatileDigits
import java.security.SecureRandom
import java.util.Collections

/** No keyboard or InputConnection. A decimal scratch tool, NOT authentication or a password manager.
 * Accessibility tools/platform/pointer observers are still trusted. Nothing is sent, saved or exported.
 */
class TouchCodeActivity : GuardedPrivacyActivity() {
    private val handler = Handler(Looper.getMainLooper())
    private var digits: VolatileDigits? = null
    private var generation: Any = Any()
    private var output: TextView? = null

    override fun dispatchKeyEvent(event: KeyEvent): Boolean = if (event.isSystem) super.dispatchKeyEvent(event) else true
    override fun dispatchKeyShortcutEvent(event: KeyEvent): Boolean = true

    override fun discardWorkspace(reason: String) {
        generation = Any(); handler.removeCallbacksAndMessages(null)
        digits?.clear(); digits = null; output?.text = ""; output = null
        showDiscarded(reason)
    }

    override fun rebuildWorkspace() {
        discardWorkspace("Code discarded.")
        val buffer = VolatileDigits(); digits = buffer
        val token = generation
        val column = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; protect(this) }
        val pad = (16 * resources.displayMetrics.density).toInt(); column.setPadding(pad, pad, pad, pad)
        fun label(text: String) = TextView(this).apply { this.text = text; textSize = 17f; protect(this); column.addView(this) }
        label("Touch-only code pad — digits only").textSize = 23f
        label("No Android keyboard, hardware text keys, clipboard, import, export or saved code. This bypasses a KEYBOARD-ONLY logger only when you click/tap instead of typing. It does not defeat a hostile laptop/OS, accessibility tool, camera or screen/pointer observer. Not an authentication service.")
        label("Observed input/IME/accessibility/display changes also discard and require an explicit empty restart. No passive/preexisting keylogger detection. 64 digits maximum. Discard on background, focus loss, obscured touch, recreation or 60 seconds without an accepted code change. Reveal hides on a 5-second UI timer, not a hard real-time bound. Clearing is NOT a forensic-erasure guarantee. Back exits. Use the ordinary scratchpad for keyboard/Switch Access workflows with their different trust boundary.")
        val value = label("").apply { id = R.id.touch_code_output; textSize = 23f; setTextIsSelectable(false) }
        output = value
        val status = label("Code empty.").apply { id = R.id.touch_code_status; accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE }
        var revealed = false
        fun current() = token === generation && acceptsInteraction()
        fun render() {
            if (token !== generation) return
            val now = SystemClock.elapsedRealtime(); val size = buffer.size(now)
            value.text = (if (revealed) buffer.reveal(now) else "•".repeat(size)) + " ($size/64)"
        }
        val expiry = object : Runnable {
            override fun run() {
                if (token !== generation) return
                if (buffer.expire(SystemClock.elapsedRealtime())) {
                    revealed = false; status.text = "Code discarded after 60 seconds without a code change."; render()
                } else {
                    val delay = buffer.remainingMs(SystemClock.elapsedRealtime())
                    if (delay > 0) handler.postDelayed(this, delay)
                }
            }
        }
        fun schedule() {
            handler.removeCallbacks(expiry)
            val delay = buffer.remainingMs(SystemClock.elapsedRealtime())
            if (delay > 0) handler.postDelayed(expiry, delay)
        }
        fun button(text: String, id: Int, parent: LinearLayout = column, weight: Boolean = false, action: () -> Unit): Button {
            val button = Button(this).apply {
                this.id = id; this.text = text; minHeight = (48 * resources.displayMetrics.density).toInt(); protect(this)
                isSoundEffectsEnabled = false; isHapticFeedbackEnabled = false
                setOnClickListener { if (current()) action() }
            }
            parent.addView(button, LinearLayout.LayoutParams(if (weight) 0 else -1, -2, if (weight) 1f else 0f))
            return button
        }
        val keys = ('0'..'9').toMutableList(); Collections.shuffle(keys, SecureRandom())
        val ids = intArrayOf(R.id.touch_key_0, R.id.touch_key_1, R.id.touch_key_2, R.id.touch_key_3, R.id.touch_key_4,
            R.id.touch_key_5, R.id.touch_key_6, R.id.touch_key_7, R.id.touch_key_8, R.id.touch_key_9)
        for (rowKeys in keys.chunked(3)) {
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; protect(this) }; column.addView(row)
            for (key in rowKeys) button(key.toString(), ids[key - '0'], row, true) {
                revealed = false
                status.text = if (buffer.append(key, SystemClock.elapsedRealtime())) "Digit entered; code hidden." else "Input refused: maximum 64 digits."
                render(); schedule()
            }
        }
        button("Delete last digit", R.id.touch_delete) {
            revealed = false; buffer.backspace(SystemClock.elapsedRealtime()); status.text = "Code hidden."; render(); schedule()
        }
        button("Reveal (5-second timer)", R.id.touch_reveal) {
            revealed = true; status.text = "Visible to people/cameras and trusted accessibility tools. Not anonymous."; render()
            handler.postDelayed({ if (token === generation) { revealed = false; render() } }, 5_000)
        }
        button("Discard code and reshuffle keys", R.id.touch_clear) { rebuildWorkspace() }
        button("Return to scratchpad (discards code)", R.id.touch_back) { finish() }
        val scroll = ScrollView(this).apply { protect(this); addView(column) }
        scroll.setOnApplyWindowInsetsListener { v, insets ->
            val safe = insets.getInsets(WindowInsets.Type.systemBars()); v.setPadding(safe.left, safe.top, safe.right, safe.bottom); insets
        }
        setContentView(scroll); column.isFocusableInTouchMode = true; column.requestFocus(); render()
    }
}
