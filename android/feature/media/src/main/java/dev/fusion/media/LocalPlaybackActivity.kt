// SPDX-License-Identifier: Apache-2.0
package dev.fusion.media

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.View
import android.view.WindowInsets
import android.view.WindowManager
import android.widget.*
import java.util.Locale
import kotlin.math.min

/** Actual foreground-only platform playback. Nonexported; only a user-selected content URI is accepted.
 * Decoder/provider availability is external. This is not a DRM bypass, stream extractor or parser sandbox.
 */
class LocalPlaybackActivity : Activity() {
    private val main = Handler(Looper.getMainLooper())
    private val epoch = PlaybackEpoch()
    private var player: MediaPlayer? = null
    private var source: Uri? = null
    private var resumed = false
    private var surfaceReady = false
    private var video = false
    private var videoWidth = 0
    private var videoHeight = 0
    private lateinit var surface: SurfaceView
    private lateinit var frame: FrameLayout
    private lateinit var status: TextView
    private lateinit var time: TextView
    private lateinit var play: Button
    private lateinit var seek: SeekBar
    private lateinit var audio: AudioManager
    private lateinit var focus: AudioFocusRequest
    private var receiverRegistered = false
    private val noisy = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == AudioManager.ACTION_AUDIO_BECOMING_NOISY) pausePlayback("Paused: audio output changed.")
        }
    }
    data class Observation(val phase: PlaybackEpoch.Phase, val positionMs: Int?, val durationMs: Int?, val hasPlayer: Boolean)
    /** Read-only diagnostic: position/duration come from the real MediaPlayer, not a synthesized timer. */
    fun observation(): Observation {
        val queryable = epoch.phase in setOf(PlaybackEpoch.Phase.READY, PlaybackEpoch.Phase.PLAYING, PlaybackEpoch.Phase.PAUSED, PlaybackEpoch.Phase.ENDED)
        val current = if (queryable) player else null
        return Observation(epoch.phase, current?.let { runCatching { it.currentPosition }.getOrNull() },
            current?.let { runCatching { it.duration }.getOrNull() }, player != null)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(null)
        audio = getSystemService(AudioManager::class.java)
        val attributes = AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_MOVIE).build()
        focus = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN).setAudioAttributes(attributes)
            .setOnAudioFocusChangeListener({ change -> if (change != AudioManager.AUDIOFOCUS_GAIN) pausePlayback("Paused: audio focus lost. Press Play to resume.") }, main).build()
        val column = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(12), dp(8), dp(12), dp(8)) }
        val title = TextView(this).apply { text = "Local media player"; textSize = 22f }; column.addView(title)
        column.addView(TextView(this).apply { text = "Selected audio/video only · no autoplay or background playback"; textSize = 14f })
        status = TextView(this).apply { id = R.id.player_status; textSize = 16f; accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE }; column.addView(status)
        frame = FrameLayout(this).apply { setBackgroundColor(android.graphics.Color.BLACK) }
        surface = SurfaceView(this).apply { id = R.id.player_surface }
        frame.addView(surface, FrameLayout.LayoutParams(-1, -1, Gravity.CENTER))
        column.addView(frame, LinearLayout.LayoutParams(-1, 0, 1f))
        frame.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> fitVideo() }
        time = TextView(this).apply { id = R.id.player_time; text = "00:00 / --:--"; textSize = 16f }; column.addView(time)
        seek = SeekBar(this).apply { id = R.id.player_seek; minHeight = dp(48); isEnabled = false; contentDescription = "Playback position" }
        seek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onStartTrackingTouch(bar: SeekBar) {}
            override fun onStopTrackingTouch(bar: SeekBar) {}
            override fun onProgressChanged(bar: SeekBar, value: Int, fromUser: Boolean) {
                if (fromUser && epoch.phase in setOf(PlaybackEpoch.Phase.READY, PlaybackEpoch.Phase.PLAYING, PlaybackEpoch.Phase.PAUSED, PlaybackEpoch.Phase.ENDED)) {
                    runCatching { player?.seekTo(value.toLong(), MediaPlayer.SEEK_CLOSEST) }.onFailure { stopPlayback("Seek failed; reopen the document.") }
                }
            }
        }); column.addView(seek)
        val controls = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        fun control(label: String, id: Int, action: () -> Unit): Button = Button(this).apply {
            text = label; this.id = id; minHeight = dp(48); setOnClickListener { action() }
            controls.addView(this, LinearLayout.LayoutParams(0, -2, 1f))
        }
        play = control("Play", R.id.player_play) { if (epoch.phase == PlaybackEpoch.Phase.PLAYING) pausePlayback("Paused.") else startPlayback() }.apply { isEnabled = false }
        control("Reopen", R.id.player_reopen) { if (resumed) prepare() }
        control("Close", R.id.player_close) { finish() }
        column.addView(controls)
        column.setOnApplyWindowInsetsListener { view, insets ->
            val safe = insets.getInsets(WindowInsets.Type.systemBars()); view.setPadding(safe.left + dp(12), safe.top + dp(8), safe.right + dp(12), safe.bottom + dp(8)); insets
        }
        setContentView(column)
        source = intent.data?.takeIf { it.scheme == "content" && it.toString().length <= 4096 }
        if (source == null) { status.text = "Refused: choose a local content document from Media. No URL opened."; return }
        video = runCatching { contentResolver.getType(source!!)?.startsWith("video/") == true }.getOrDefault(false)
        surface.holder.addCallback(object : SurfaceHolder.Callback {
            override fun surfaceCreated(holder: SurfaceHolder) { surfaceReady = true; if (video && resumed) prepare() }
            override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) { fitVideo() }
            override fun surfaceDestroyed(holder: SurfaceHolder) { surfaceReady = false; if (video) stopPlayback("Video surface closed; reopen to prepare again.") }
        })
        if (!video) {
            surface.visibility = View.GONE
            frame.addView(TextView(this).apply { text = "AUDIO"; textSize = 28f; gravity = Gravity.CENTER; setTextColor(android.graphics.Color.WHITE) }, FrameLayout.LayoutParams(-1, -1))
        }
        registerReceiver(noisy, IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY), RECEIVER_NOT_EXPORTED); receiverRegistered = true
        status.text = "Preparing selected document…"
    }
    override fun onResume() {
        super.onResume(); resumed = true
        if (source != null && epoch.phase == PlaybackEpoch.Phase.EMPTY && (!video || surfaceReady)) prepare()
        main.removeCallbacks(tick); main.post(tick)
    }
    override fun onPause() { resumed = false; main.removeCallbacks(tick); stopPlayback("Stopped on background. Press Reopen to prepare the selected document again."); super.onPause() }
    override fun onDestroy() {
        stopPlayback("Player closed."); main.removeCallbacksAndMessages(null)
        if (receiverRegistered) unregisterReceiver(noisy)
        super.onDestroy()
    }
    override fun onSaveInstanceState(outState: Bundle) { super.onSaveInstanceState(outState); outState.clear() }
    private fun prepare() {
        if (!resumed || source == null || (video && !surfaceReady)) return
        stopPlayback("Preparing selected document…")
        val token = epoch.begin(); val instance = MediaPlayer(); player = instance
        fun current() = player === instance && epoch.generation == token && resumed
        instance.setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(if (video) AudioAttributes.CONTENT_TYPE_MOVIE else AudioAttributes.CONTENT_TYPE_MUSIC).build())
        if (video) instance.setDisplay(surface.holder)
        instance.setOnPreparedListener {
            if (!current() || !epoch.prepared(token)) return@setOnPreparedListener
            val duration = runCatching { it.duration }.getOrDefault(0)
            if (duration <= 0) { stopPlayback("Unknown/unsupported duration; this bounded local player requires seekable finite media."); return@setOnPreparedListener }
            seek.max = duration; seek.isEnabled = true; play.isEnabled = true; play.text = "Play"
            status.text = "Ready · press Play. Platform decoder; no playback inferred from readiness."
            renderTime()
        }
        instance.setOnCompletionListener {
            if (current() && epoch.ended(token)) {
                play.text = "Replay"; status.text = "Finished."; window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON); audio.abandonAudioFocusRequest(focus); renderTime()
            }
        }
        instance.setOnErrorListener { _, what, extra ->
            if (current()) { epoch.fail(token); stopPlayback("Platform decoder error $what/$extra. Nothing downloaded or extracted.") }; true
        }
        instance.setOnVideoSizeChangedListener { _, width, height -> if (current()) { videoWidth = width; videoHeight = height; fitVideo() } }
        try { instance.setDataSource(this, source!!); instance.prepareAsync() }
        catch (e: Exception) { stopPlayback("Cannot open selected media: ${e.javaClass.simpleName}") }
        main.postDelayed({ if (current() && epoch.phase == PlaybackEpoch.Phase.PREPARING) stopPlayback("Preparation exceeded 15 seconds; player released. Reopen explicitly to retry.") }, 15_000)
    }
    private fun startPlayback() {
        val instance = player ?: return
        if (!resumed || epoch.phase !in setOf(PlaybackEpoch.Phase.READY, PlaybackEpoch.Phase.PAUSED, PlaybackEpoch.Phase.ENDED)) return
        if (audio.requestAudioFocus(focus) != AudioManager.AUDIOFOCUS_REQUEST_GRANTED) { status.text = "Audio focus denied; nothing started."; return }
        try {
            if (epoch.phase == PlaybackEpoch.Phase.ENDED) instance.seekTo(0)
            instance.start(); check(epoch.play(epoch.generation)); play.text = "Pause"; status.text = "Playing selected document."
            if (video) window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } catch (e: Exception) { stopPlayback("Playback failed: ${e.javaClass.simpleName}") }
    }
    private fun pausePlayback(reason: String) {
        if (epoch.phase != PlaybackEpoch.Phase.PLAYING) return
        runCatching { player?.pause() }; epoch.pause(); play.text = "Play"; status.text = reason
        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON); audio.abandonAudioFocusRequest(focus); renderTime()
    }
    private fun stopPlayback(reason: String) {
        epoch.close(); val old = player; player = null
        old?.let { runCatching { it.setOnPreparedListener(null); it.setOnCompletionListener(null); it.setOnErrorListener(null); it.release() } }
        if (::audio.isInitialized && ::focus.isInitialized) audio.abandonAudioFocusRequest(focus)
        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        if (::play.isInitialized) { play.isEnabled = false; play.text = "Play"; seek.isEnabled = false; status.text = reason }
    }
    private fun renderTime() {
        val data = observation(); val position = data.positionMs ?: 0; val duration = data.durationMs ?: 0
        if (duration > 0) { if (!seek.isPressed) seek.progress = position; time.text = "${clock(position)} / ${clock(duration)}" }
    }
    private val tick = object : Runnable {
        override fun run() { if (resumed) { renderTime(); main.postDelayed(this, 250) } }
    }
    private fun fitVideo() {
        if (videoWidth <= 0 || videoHeight <= 0 || frame.width <= 0 || frame.height <= 0) return
        val scale = min(frame.width.toDouble() / videoWidth, frame.height.toDouble() / videoHeight)
        val width = (videoWidth * scale).toInt().coerceAtLeast(1); val height = (videoHeight * scale).toInt().coerceAtLeast(1)
        val current = surface.layoutParams
        if (current.width != width || current.height != height) surface.layoutParams = FrameLayout.LayoutParams(width, height, Gravity.CENTER)
    }
    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
    private fun clock(ms: Int) = String.format(Locale.ROOT, "%02d:%02d", ms / 60000, ms / 1000 % 60)
}
