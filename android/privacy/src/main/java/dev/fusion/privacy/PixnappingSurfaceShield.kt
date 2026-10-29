// SPDX-License-Identifier: Apache-2.0
package dev.fusion.privacy

import java.security.SecureRandom

/**
 * Clean-room defense against **Pixnapping** (`CVE-2025-48561`, *Wang, Paccagnella et al., CMU /
 * UC Berkeley / UW, ACM CCS Oct 2025*) cross-window blur + `GPU.zip` pixel-theft attacks.
 *
 * Enforces four concentric protections on sensitive surfaces (Vault, 2FA OTP, Recovery Seed,
 * Scrambled PIN Pad, Incognito Browser):
 * 1. Mandatory window flags: `FLAG_SECURE = true`, `hideOverlayWindows = true`, AND
 *    `crossWindowBlurEnabled = false` (breaks the window-blur compositing primitive!).
 * 2. Task-stack isolation: rejects untrusted cross-UID `Intent` launches that attempt to embed a
 *    sensitive Activity inside an attacker's translucent task stack (`LAUNCH_SINGLE_TASK_ISOLATED`).
 * 3. Sub-pixel glyph coordinate dither (`±1 px`) + zero-mean high-frequency background entropy
 *    checkerboard (`±deltaLuma`) to decorrelate `GPU.zip` cacheline compression timing from text!
 */
object PixnappingSurfaceShield {
    enum class SurfaceSensitivity { PUBLIC_UI, PRIVATE_CONTENT, CRITICAL_SECRET_2FA_OR_SEED }

    data class WindowHardeningSpec(
        val flagSecure: Boolean,
        val hideOverlayWindows: Boolean,
        val crossWindowBlurEnabled: Boolean,
        val isolateFromExternalTaskStack: Boolean,
        val glyphJitterRangePx: Int,
        val backgroundNoiseAmplitude: Int,
    )

    private val csprng = SecureRandom()

    fun policyFor(sensitivity: SurfaceSensitivity): WindowHardeningSpec = when (sensitivity) {
        SurfaceSensitivity.PUBLIC_UI -> WindowHardeningSpec(
            flagSecure = false,
            hideOverlayWindows = false,
            crossWindowBlurEnabled = false, // Disabled globally to deny blur side-channel amplification
            isolateFromExternalTaskStack = false,
            glyphJitterRangePx = 0,
            backgroundNoiseAmplitude = 0,
        )
        SurfaceSensitivity.PRIVATE_CONTENT -> WindowHardeningSpec(
            flagSecure = true,
            hideOverlayWindows = true,
            crossWindowBlurEnabled = false,
            isolateFromExternalTaskStack = true,
            glyphJitterRangePx = 0,
            backgroundNoiseAmplitude = 4,
        )
        SurfaceSensitivity.CRITICAL_SECRET_2FA_OR_SEED -> WindowHardeningSpec(
            flagSecure = true,
            hideOverlayWindows = true,
            crossWindowBlurEnabled = false,
            isolateFromExternalTaskStack = true,
            glyphJitterRangePx = 1,
            backgroundNoiseAmplitude = 9,
        )
    }

    fun allowIncomingLaunchIntent(
        sensitivity: SurfaceSensitivity,
        callerUid: Int,
        ownUid: Int,
        launchedInForeignTask: Boolean,
    ): Boolean {
        if (sensitivity == SurfaceSensitivity.PUBLIC_UI) return true
        if (callerUid != ownUid) return false
        if (launchedInForeignTask) return false
        return true
    }

    /**
     * Generates a zero-mean high-spatial-frequency background luma perturbation tile (`width * height`)
     * that defeats `GPU.zip` constant-color cacheline run-length compression without reducing WCAG text contrast.
     */
    fun generateAntiCompressionLumaTile(
        width: Int = 16,
        height: Int = 16,
        baseLuma: Int = 20,
        amplitude: Int = 9,
    ): IntArray {
        require(width in 4..128 && height in 4..128 && width % 2 == 0) { "even tile dimensions 4..128 required" }
        require(amplitude in 0..24 && baseLuma - amplitude >= 0 && baseLuma + amplitude <= 255) {
            "baseLuma ± amplitude must stay within 0..255"
        }
        val out = IntArray(width * height)
        val rowNoise = ByteArray(width / 2)
        for (y in 0 until height) {
            csprng.nextBytes(rowNoise)
            for (pair in 0 until width / 2) {
                val mag = if (amplitude == 0) 0 else ((rowNoise[pair].toInt() and 0xFF) % (amplitude + 1))
                val sign = if ((pair + y) % 2 == 0) 1 else -1
                // Paired +delta / -delta guarantees exact zero mean per scanline!
                out[y * width + pair * 2] = baseLuma + sign * mag
                out[y * width + pair * 2 + 1] = baseLuma - sign * mag
            }
        }
        return out
    }

    fun sampleSubpixelGlyphOffset(jitterRangePx: Int = 1): Pair<Int, Int> {
        require(jitterRangePx in 0..3) { "jitterRangePx outside 0..3" }
        if (jitterRangePx == 0) return 0 to 0
        val span = 2 * jitterRangePx + 1
        val dx = csprng.nextInt(span) - jitterRangePx
        val dy = csprng.nextInt(span) - jitterRangePx
        return dx to dy
    }
}
