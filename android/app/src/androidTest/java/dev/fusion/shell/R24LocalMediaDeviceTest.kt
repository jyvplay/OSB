// SPDX-License-Identifier: Apache-2.0
package dev.fusion.shell

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.SystemClock
import android.widget.Button
import android.widget.TextView
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import dev.fusion.media.LocalPlaybackActivity
import dev.fusion.media.PlaybackEpoch
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.sin

/** Actual PCM document, ContentProvider FD, Android decoder/player clock and platform UI input. */
class R24LocalMediaDeviceTest {
    @get:Rule val disposable = DisposableEmulatorRule()
    private val context get() = ApplicationProvider.getApplicationContext<Context>()
    private val device get() = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
    private fun tone(): Uri {
        val rate = 16000; val samples = rate * 4; val bytes = ByteBuffer.allocate(44 + samples * 2).order(ByteOrder.LITTLE_ENDIAN)
        bytes.put("RIFF".toByteArray()); bytes.putInt(36 + samples * 2); bytes.put("WAVEfmt ".toByteArray()); bytes.putInt(16)
        bytes.putShort(1); bytes.putShort(1); bytes.putInt(rate); bytes.putInt(rate * 2); bytes.putShort(2); bytes.putShort(16)
        bytes.put("data".toByteArray()); bytes.putInt(samples * 2)
        repeat(samples) { bytes.putShort((sin(2 * Math.PI * 440 * it / rate) * 600).toInt().toShort()) }
        File(context.cacheDir, "r24c-tone.wav").writeBytes(bytes.array())
        return Uri.parse("content://${context.packageName}.media-fixture/tone.wav")
    }
    private fun await(scenario: ActivityScenario<LocalPlaybackActivity>, predicate: (LocalPlaybackActivity.Observation) -> Boolean): LocalPlaybackActivity.Observation {
        val end = SystemClock.elapsedRealtime() + 10000; var observed: LocalPlaybackActivity.Observation? = null
        while (SystemClock.elapsedRealtime() < end) {
            scenario.onActivity { observed = it.observation() }
            if (predicate(observed!!)) return observed!!
            SystemClock.sleep(50)
        }
        throw AssertionError("Actual native player predicate not met: $observed")
    }
    private fun pressPlayControl(scenario: ActivityScenario<LocalPlaybackActivity>) {
        val bounds = android.graphics.Rect()
        scenario.onActivity { a ->
            val button = a.findViewById<Button>(dev.fusion.media.R.id.player_play)
            assertTrue("Player must own real input focus", a.hasWindowFocus())
            assertTrue(button.isEnabled && button.isShown)
            val location = IntArray(2); button.getLocationOnScreen(location)
            bounds.set(location[0], location[1], location[0] + button.width, location[1] + button.height)
            val visible = android.graphics.Rect(); a.window.decorView.getWindowVisibleDisplayFrame(visible)
            assertTrue("Actual play target must be visible: $bounds / $visible", visible.contains(bounds))
        }
        // Native view buttons may expose ALL-CAPS transformed text. Use actual screen geometry, not a text guess.
        // Do not await a globally idle hierarchy while a legitimate playback clock updates every 250ms.
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val down = SystemClock.uptimeMillis()
        for ((action, time) in listOf(android.view.MotionEvent.ACTION_DOWN to down, android.view.MotionEvent.ACTION_UP to down + 40)) {
            val event = android.view.MotionEvent.obtain(down, time, action, bounds.exactCenterX(), bounds.exactCenterY(), 0)
                .apply { source = android.view.InputDevice.SOURCE_TOUCHSCREEN }
            try { assertTrue(instrumentation.uiAutomation.injectInputEvent(event, true)) } finally { event.recycle() }
        }
    }
    @Test fun actualAudioReadyIsNotAutoplayThenPlayPauseSeekAndBackgroundRelease() {
        val uri = tone()
        try {
            ActivityScenario.launch<LocalPlaybackActivity>(Intent(context, LocalPlaybackActivity::class.java).setData(uri)).use { scenario ->
                val ready = await(scenario) { it.phase == PlaybackEpoch.Phase.READY }
                assertTrue(ready.durationMs!! in 3900..4100); assertEquals(0, ready.positionMs)
                pressPlayControl(scenario)
                await(scenario) { it.phase == PlaybackEpoch.Phase.PLAYING && (it.positionMs ?: 0) > 250 }
                pressPlayControl(scenario)
                val paused = await(scenario) { it.phase == PlaybackEpoch.Phase.PAUSED }
                SystemClock.sleep(250)
                val again = await(scenario) { it.phase == PlaybackEpoch.Phase.PAUSED }
                assertTrue(kotlin.math.abs(again.positionMs!! - paused.positionMs!!) <= 80)
                // Native SeekBar keyboard interaction, not a direct MediaPlayer call.
                scenario.onActivity { a -> a.findViewById<android.widget.SeekBar>(dev.fusion.media.R.id.player_seek).requestFocus() }
                InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_DPAD_RIGHT)
                await(scenario) { it.phase == PlaybackEpoch.Phase.PAUSED && (it.positionMs ?: 0) > paused.positionMs!! }
                pressPlayControl(scenario)
                await(scenario) { it.phase == PlaybackEpoch.Phase.PLAYING }
                device.pressHome()
                await(scenario) { it.phase == PlaybackEpoch.Phase.CLOSED && !it.hasPlayer }
            }
        } finally { File(context.cacheDir, "r24c-tone.wav").delete() }
    }
    @Test fun networkUrlIsNotTreatedAsAPlayableLocalDocument() {
        ActivityScenario.launch<LocalPlaybackActivity>(Intent(context, LocalPlaybackActivity::class.java).setData(Uri.parse("https://www.youtube.com/watch?v=abcdefghijk"))).use { scenario ->
            scenario.onActivity { a ->
                assertFalse(a.observation().hasPlayer)
                assertFalse(a.findViewById<Button>(dev.fusion.media.R.id.player_play).isEnabled)
                assertTrue(a.findViewById<TextView>(dev.fusion.media.R.id.player_status).text.contains("Refused"))
                val info = a.packageManager.getActivityInfo(android.content.ComponentName(a, LocalPlaybackActivity::class.java), 0)
                assertFalse(info.exported)
            }
        }
    }
}
