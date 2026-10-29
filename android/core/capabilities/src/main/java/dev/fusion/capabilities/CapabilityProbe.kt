package dev.fusion.capabilities

import android.app.ActivityManager
import android.app.role.RoleManager
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.display.DisplayManager
import android.media.MediaCodecList
import android.os.Build

/** Null is unknown, never a fabricated zero/false. Flags do not prove usable throughput. */
data class CapabilityReport(
    val sdk: Int, val fingerprint: String, val soc: String,
    val ramGb: Double?, val deviceClass: String, val presentationDisplays: Int?,
    val videoEncoders: List<String>?, val npuStatus: String,
    val homeRoleHeld: Boolean?, val freeformDeclared: Boolean?,
    val displayOutSupported: Boolean? = null,
    val desktopSessionActive: Boolean? = null,
    val errors: List<String> = emptyList(),
)
object CapabilityProbe {
    fun inspect(context: Context): CapabilityReport {
        val failures = mutableListOf<String>()
        fun <T> probe(name: String, block: () -> T): T? = try { block() } catch (e: Exception) {
            failures += "$name unavailable (${e.javaClass.simpleName})"; null
        }
        val am = context.getSystemService(ActivityManager::class.java)
        val bytes = probe("Memory") { ActivityManager.MemoryInfo().also(am::getMemoryInfo).totalMem }
        val lowRam = probe("Low-RAM flag") { am.isLowRamDevice }
        val displays = probe("Displays") { context.getSystemService(DisplayManager::class.java).getDisplays(DisplayManager.DISPLAY_CATEGORY_PRESENTATION).size }
        val encoders = probe("Codec enumeration") {
            MediaCodecList(MediaCodecList.ALL_CODECS).codecInfos.filter { it.isEncoder && it.isHardwareAccelerated }
                .flatMap { it.supportedTypes.filter { mime -> mime.startsWith("video/") } }.distinct().sorted()
        }
        return CapabilityReport(
            Build.VERSION.SDK_INT, Build.FINGERPRINT, "${Build.SOC_MANUFACTURER} / ${Build.SOC_MODEL}",
            bytes?.takeIf { it > 0 }?.div(1024.0 * 1024 * 1024),
            if (lowRam == true) "Android low-RAM device (Compat suggested, no hard block)" else "Unclassified; no device benchmark has been run",
            displays, encoders,
            // [U] verify on API 34/36/37: NPU/session/physical DP-out are not inferred from SoC or displays.
            "Unknown; no NPU inference backend admitted",
            probe("Home role") { context.getSystemService(RoleManager::class.java).isRoleHeld(RoleManager.ROLE_HOME) },
            probe("Freeform declaration") { context.packageManager.hasSystemFeature(PackageManager.FEATURE_FREEFORM_WINDOW_MANAGEMENT) },
            errors = failures.toList(),
        )
    }

    /** R51 host-deterministic Wasm 2.0 128-bit SIMD (0xFD / v128) module audit hook. */
    fun inspectWasmSimdModule(wasmBytes: ByteArray): WasmSimdModuleVerifier.VerificationReport =
        WasmSimdModuleVerifier.verify(wasmBytes)
}
