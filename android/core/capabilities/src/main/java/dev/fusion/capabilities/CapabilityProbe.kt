package dev.fusion.capabilities

import android.app.ActivityManager
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.display.DisplayManager
import android.media.MediaCodecList
import android.os.Build

data class CapabilityReport(
    val sdk: Int,
    val fingerprint: String,
    val soc: String,
    val ramGb: Long,
    val deviceClass: String,
    val presentationDisplays: Int,
    val videoEncoders: List<String>,
    val npuStatus: String
)

/** Reports only observable capabilities. Silicon names are not a substitute for performance tests. */
object CapabilityProbe {
    fun inspect(context: Context): CapabilityReport {
        val am = context.getSystemService(ActivityManager::class.java)
        val memory = ActivityManager.MemoryInfo()
        am.getMemoryInfo(memory)
        val ramGb = memory.totalMem / (1024L * 1024L * 1024L)
        val soc = Build.SOC_MODEL ?: Build.HARDWARE
        val displayManager = context.getSystemService(DisplayManager::class.java)
        val displays = displayManager.getDisplays(DisplayManager.DISPLAY_CATEGORY_PRESENTATION).size
        val encoders = MediaCodecList(MediaCodecList.ALL_CODECS).codecInfos
            .filter { it.isEncoder && it.isHardwareAccelerated }
            .flatMap { codec -> codec.supportedTypes.filter { it.startsWith("video/") } }
            .distinct().sorted()
        return CapabilityReport(
            sdk = Build.VERSION.SDK_INT,
            fingerprint = Build.FINGERPRINT,
            soc = soc,
            ramGb = ramGb,
            deviceClass = if (ramGb < 8) "Compat (no hardware block)" else "Unclassified: benchmark required",
            presentationDisplays = displays,
            videoEncoders = encoders,
            // [U] verify on API 37: NPU feature declaration; API 34/35 has no reliable universal probe.
            npuStatus = "Unknown (not inferred from SoC)"
        )
    }
}