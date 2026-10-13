package dev.fusion.capture

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.MediaRecorder
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.view.WindowManager
import kotlinx.coroutines.flow.MutableStateFlow
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Observable recorder state. Only this service mutates it. */
object RecorderState {
    val recording = MutableStateFlow(false)
    val message = MutableStateFlow("Idle. Screen is not being captured.")
    val lastFile = MutableStateFlow<String?>(null)
}

/**
 * Real MediaProjection screen recorder (video only, no audio).
 *
 * Order enforced for Android 14+:
 *   1. startForeground(mediaProjection type)  2. getMediaProjection(consent)
 *   3. registerCallback(...)                  4. createVirtualDisplay(...)
 * Skipping step 3 makes step 4 throw IllegalStateException on API 34+.
 * Each consent token is single-use: a new recording needs a new prompt.
 */
class ScreenRecordService : Service() {

    private var projection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var recorder: MediaRecorder? = null
    private var outputFile: File? = null
    private val handler = Handler(Looper.getMainLooper())

    private val projectionCallback = object : MediaProjection.Callback() {
        override fun onStop() {
            // User revoked capture from the system UI, or the session ended.
            teardown("Capture stopped by the system or by you.")
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                val code = intent.getIntExtra(EXTRA_RESULT_CODE, Int.MIN_VALUE)
                val data: Intent? =
                    if (Build.VERSION.SDK_INT >= 33) intent.getParcelableExtra(EXTRA_RESULT_DATA, Intent::class.java)
                    else @Suppress("DEPRECATION") intent.getParcelableExtra(EXTRA_RESULT_DATA)
                if (code == Int.MIN_VALUE || data == null) {
                    RecorderState.message.value = "Missing capture consent. Nothing started."
                    stopSelf(); return START_NOT_STICKY
                }
                startRecording(code, data)
            }
            ACTION_STOP -> teardown("Recording stopped.")
            else -> stopSelf()
        }
        return START_NOT_STICKY
    }

    private fun startRecording(resultCode: Int, data: Intent) {
        if (RecorderState.recording.value) return
        try {
            // 1. Foreground first, otherwise getMediaProjection throws on Android 14+.
            goForeground()

            val bounds = (getSystemService(Context.WINDOW_SERVICE) as WindowManager)
                .currentWindowMetrics.bounds
            val width = (bounds.width().coerceAtMost(1920) / 2) * 2
            val height = (bounds.height().coerceAtMost(1920) / 2) * 2
            val dpi = resources.configuration.densityDpi

            val dir = getExternalFilesDir(Environment.DIRECTORY_MOVIES)
                ?: filesDir // app-scoped; no storage permission required
            dir.mkdirs()
            val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
            val file = File(dir, "fusion-capture-$stamp.mp4")
            outputFile = file

            recorder = MediaRecorder(this).apply {
                setVideoSource(MediaRecorder.VideoSource.SURFACE)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setVideoEncoder(MediaRecorder.VideoEncoder.H264)
                setVideoSize(width, height)
                setVideoFrameRate(30)
                setVideoEncodingBitRate(8_000_000)
                setOutputFile(file.absolutePath)
                prepare()
            }

            // 2. Consent -> projection.
            val mgr = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            val mp = mgr.getMediaProjection(resultCode, data)
            if (mp == null) {
                teardown("System refused the capture token.")
                return
            }
            projection = mp

            // 3. Callback BEFORE display (hard requirement on API 34+).
            mp.registerCallback(projectionCallback, handler)

            // 4. Mirror into the recorder surface.
            virtualDisplay = mp.createVirtualDisplay(
                "FusionCapture", width, height, dpi,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                recorder!!.surface, null, handler
            )

            recorder!!.start()
            RecorderState.recording.value = true
            RecorderState.message.value = "Recording ${width}x$height @30fps (video only) -> ${file.name}"
        } catch (e: Exception) {
            teardown("Recording failed: ${e.javaClass.simpleName}: ${e.message}")
        }
    }

    private fun goForeground() {
        val mgr = getSystemService(NotificationManager::class.java)
        if (mgr.getNotificationChannel(CHANNEL) == null) {
            mgr.createNotificationChannel(
                NotificationChannel(CHANNEL, "Screen capture", NotificationManager.IMPORTANCE_LOW)
            )
        }
        val n: Notification = Notification.Builder(this, CHANNEL)
            .setContentTitle("Fusion is recording your screen")
            .setContentText("Video only. Stop from the Capture window.")
            .setSmallIcon(android.R.drawable.presence_video_online)
            .setOngoing(true)
            .build()
        startForeground(NOTIFICATION_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)
    }

    private fun teardown(reason: String) {
        val wasRecording = RecorderState.recording.value
        runCatching { if (wasRecording) recorder?.stop() }
            .onFailure { RecorderState.message.value = "Encoder produced no usable frames; file discarded." }
        runCatching { recorder?.reset() }
        runCatching { recorder?.release() }
        recorder = null
        runCatching { virtualDisplay?.release() }
        virtualDisplay = null
        runCatching { projection?.unregisterCallback(projectionCallback) }
        runCatching { projection?.stop() }
        projection = null

        RecorderState.recording.value = false
        val f = outputFile
        if (wasRecording && f != null && f.exists() && f.length() > 0) {
            RecorderState.lastFile.value = f.absolutePath
            RecorderState.message.value = "$reason Saved ${f.length() / 1024} KB to ${f.absolutePath}"
        } else {
            if (f != null && f.exists() && f.length() == 0L) f.delete()
            RecorderState.message.value = reason
        }
        outputFile = null
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        super.onDestroy()
        if (RecorderState.recording.value) teardown("Service destroyed.")
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val ACTION_START = "dev.fusion.capture.START"
        const val ACTION_STOP = "dev.fusion.capture.STOP"
        const val EXTRA_RESULT_CODE = "resultCode"
        const val EXTRA_RESULT_DATA = "resultData"
        private const val CHANNEL = "fusion_capture"
        private const val NOTIFICATION_ID = 4711

        fun consentIntent(context: Context): Intent =
            (context.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager)
                .createScreenCaptureIntent()

        fun start(context: Context, resultCode: Int, data: Intent) {
            val i = Intent(context, ScreenRecordService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_RESULT_CODE, resultCode)
                putExtra(EXTRA_RESULT_DATA, data)
            }
            context.startForegroundService(i)
        }

        fun stop(context: Context) {
            val i = Intent(context, ScreenRecordService::class.java).apply { action = ACTION_STOP }
            context.startService(i)
        }
    }
}
