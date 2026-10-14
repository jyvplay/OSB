package dev.fusion.capture

import android.Manifest
import android.app.*
import android.content.*
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.MediaMetadataRetriever
import android.media.MediaRecorder
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.*
import android.view.WindowManager
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import kotlin.math.min

object RecorderState {
    internal val active = MutableStateFlow(false)
    internal val finalizing = MutableStateFlow(false)
    internal val status = MutableStateFlow("Idle. Screen is not being captured.")
    internal val completed = MutableStateFlow<String?>(null)
    val recording = active.asStateFlow()
    val finishing = finalizing.asStateFlow()
    val message = status.asStateFlow()
    val lastFile = completed.asStateFlow()
}

/** MD-01 / SC-09 / CA-14: real local recorder; not a cast transport or verified HW encoder. */
class ScreenRecordService : Service() {
    private var projection: MediaProjection? = null
    private var display: VirtualDisplay? = null
    private var recorder: MediaRecorder? = null
    private var partial: File? = null
    private val main = Handler(Looper.getMainLooper())
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var receiverRegistered = false
    private var thermalRegistered = false
    private val lockReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) { finishCapture("Stopped when screen locked or turned off.") }
    }
    private val thermalListener = PowerManager.OnThermalStatusChangedListener { level ->
        if (level >= PowerManager.THERMAL_STATUS_SEVERE) finishCapture("Stopped for thermal safety (no adaptive encoder ladder in this pilot).")
    }
    private var projectionCallback: MediaProjection.Callback? = null

    override fun onCreate() {
        super.onCreate()
        registerReceiver(lockReceiver, IntentFilter(Intent.ACTION_SCREEN_OFF), Context.RECEIVER_NOT_EXPORTED)
        receiverRegistered = true
        // [U] verify on API 34/36/37 and OEMs: screen-off/thermal delivery while backgrounded.
        runCatching {
            getSystemService(PowerManager::class.java).addThermalStatusListener(mainExecutor, thermalListener)
            thermalRegistered = true
        }
    }
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                if (RecorderState.active.value || RecorderState.finalizing.value) return START_NOT_STICKY
                val code = intent.getIntExtra(EXTRA_RESULT_CODE, Int.MIN_VALUE)
                val data = intent.getParcelableExtra(EXTRA_RESULT_DATA, Intent::class.java)
                if (code != Activity.RESULT_OK || data == null) {
                    RecorderState.status.value = "No valid one-use capture consent. Nothing started."
                    stopSelf()
                } else begin(code, data)
            }
            ACTION_STOP -> finishCapture("Stopped by you.")
            else -> stopSelf()
        }
        return START_NOT_STICKY
    }
    private fun begin(code: Int, data: Intent) {
        RecorderState.completed.value = null
        try {
            check(!getSystemService(KeyguardManager::class.java).isKeyguardLocked) { "Unlock before capture" }
            check(getSystemService(PowerManager::class.java).currentThermalStatus < PowerManager.THERMAL_STATUS_SEVERE) { "Device too hot to record safely" }
            val notifications = getSystemService(NotificationManager::class.java)
            check(checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED && notifications.areNotificationsEnabled()) {
                "Visible capture notifications are required for this pilot. Enable notifications before recording."
            }
            if (notifications.getNotificationChannel(CHANNEL) == null) notifications.createNotificationChannel(
                NotificationChannel(CHANNEL, "Screen capture", NotificationManager.IMPORTANCE_LOW))
            check(notifications.getNotificationChannel(CHANNEL).importance != NotificationManager.IMPORTANCE_NONE) { "Capture notification channel is disabled" }
            val stop = PendingIntent.getService(this, 1, Intent(this, ScreenRecordService::class.java).setAction(ACTION_STOP),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            val notification = Notification.Builder(this, CHANNEL)
                .setContentTitle("Fusion is recording the selected screen/app")
                .setContentText("Local video only • AVC • hardware and latency unmeasured")
                .setSmallIcon(android.R.drawable.presence_video_online).setOngoing(true)
                .setVisibility(Notification.VISIBILITY_PUBLIC)
                .addAction(Notification.Action.Builder(null, "Stop recording", stop).build()).build()
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)

            val bounds = getSystemService(WindowManager::class.java).currentWindowMetrics.bounds
            val scale = min(1.0, 1920.0 / maxOf(bounds.width(), bounds.height()).coerceAtLeast(1))
            val width = ((bounds.width() * scale).toInt() / 2 * 2).coerceAtLeast(2)
            val height = ((bounds.height() * scale).toInt() / 2 * 2).coerceAtLeast(2)
            val dir = getExternalFilesDir(Environment.DIRECTORY_MOVIES) ?: filesDir
            check(dir.exists() || dir.mkdirs()) { "Cannot create recording directory" }
            partial = File(dir, "fusion-${UUID.randomUUID()}.mp4.partial")
            val created = MediaRecorder(this)
            recorder = created
            created.apply {
                setVideoSource(MediaRecorder.VideoSource.SURFACE)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setVideoEncoder(MediaRecorder.VideoEncoder.H264)
                setVideoSize(width, height); setVideoFrameRate(30); setVideoEncodingBitRate(8_000_000)
                setOutputFile(partial!!.absolutePath)
                setOnErrorListener { _, _, _ -> main.post { if (recorder === created) finishCapture("Encoder error.", discard = true) } }
                prepare()
            }
            val mp = getSystemService(MediaProjectionManager::class.java).getMediaProjection(code, data)
                ?: error("Android refused the token")
            projection = mp
            val callback = object : MediaProjection.Callback() {
                override fun onStop() { if (projection === mp) finishCapture("Capture revoked or stopped by Android.") }
            }
            projectionCallback = callback
            mp.registerCallback(callback, main)
            display = mp.createVirtualDisplay("FusionCapture", width, height, resources.configuration.densityDpi,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR, created.surface, null, main)
            created.start()
            RecorderState.active.value = true
            RecorderState.status.value = "Recording selected screen/app: ${width}×$height, AVC, 30 fps requested. No audio. Hardware encoder and latency not measured."
        } catch (e: Exception) {
            finishCapture("Capture start failed (${e.javaClass.simpleName}): ${e.message}", discard = true)
        }
    }

    private fun finishCapture(reason: String, discard: Boolean = false) {
        if (RecorderState.finalizing.value) return
        val started = RecorderState.active.value
        RecorderState.active.value = false
        RecorderState.finalizing.value = true
        RecorderState.status.value = "$reason Finalizing; no completed file claimed yet."
        val r = recorder; recorder = null
        val d = display; display = null
        val p = projection; projection = null
        val callback = projectionCallback; projectionCallback = null
        val file = partial; partial = null
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                var stopped = false
                try {
                    if (started) { r?.stop(); stopped = r != null }
                } catch (_: Exception) { stopped = false }
                finally {
                    runCatching { r?.release() }; runCatching { d?.release() }
                    runCatching { if (callback != null) p?.unregisterCallback(callback) }; runCatching { p?.stop() }
                }
                if (!started || !stopped || discard || file == null) {
                    val removed = file == null || !file.exists() || file.delete()
                    null to if (removed) "No completed recording; partial output removed." else "Unverified partial retained, not published."
                } else {
                    runCatching {
                        MediaMetadataRetriever().use { reader ->
                            reader.setDataSource(file.absolutePath)
                            check((reader.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L) > 0)
                            check((reader.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 0) > 0)
                            check((reader.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 0) > 0)
                        }
                        FileOutputStream(file, true).use { it.fd.sync() }
                        val committed = File(file.parentFile, file.name.removeSuffix(".partial"))
                        check(!committed.exists() && file.renameTo(committed)) { "Publish failed" }
                        committed.absolutePath to "MP4 finalized and metadata checked. Playback and A/V timing are not verified."
                    }.getOrElse { null to "Output could not be finalized/validated (${it.javaClass.simpleName}); unverified partial retained." }
                }
            }
            RecorderState.completed.value = result.first
            RecorderState.status.value = "$reason ${result.second}"
            RecorderState.finalizing.value = false
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }
    override fun onDestroy() {
        if (receiverRegistered) runCatching { unregisterReceiver(lockReceiver) }
        if (thermalRegistered) runCatching { getSystemService(PowerManager::class.java).removeThermalStatusListener(thermalListener) }
        if (recorder != null || projection != null || partial != null) finishCapture("Service ended.")
        // Let an already-running finalization release resources; cancellation must not skip release.
        super.onDestroy()
    }
    override fun onBind(intent: Intent?): IBinder? = null
    companion object {
        const val ACTION_START = "dev.fusion.capture.START"
        const val ACTION_STOP = "dev.fusion.capture.STOP"
        const val EXTRA_RESULT_CODE = "resultCode"
        const val EXTRA_RESULT_DATA = "resultData"
        private const val CHANNEL = "fusion_capture"
        private const val NOTIFICATION_ID = 4711
        fun consentIntent(context: Context) = context.getSystemService(MediaProjectionManager::class.java).createScreenCaptureIntent()
        fun start(context: Context, resultCode: Int, data: Intent) {
            context.startForegroundService(Intent(context, ScreenRecordService::class.java).apply {
                action = ACTION_START; putExtra(EXTRA_RESULT_CODE, resultCode); putExtra(EXTRA_RESULT_DATA, data)
            })
        }
        fun stop(context: Context) {
            if (RecorderState.active.value || RecorderState.finalizing.value) context.startService(
                Intent(context, ScreenRecordService::class.java).setAction(ACTION_STOP))
        }
    }
}
