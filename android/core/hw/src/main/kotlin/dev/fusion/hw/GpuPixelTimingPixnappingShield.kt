// SPDX-License-Identifier: Apache-2.0
package dev.fusion.hw

/**
 * Vector `#33` — `Pixnapping`-class cross-window GPU compositing pixel-theft shield.
 *
 * `Pixnapping` (2024/2025) reconstructs another app's pixels by measuring *how long* the compositor
 * spends drawing a covering window: frame duration leaks the hidden layer's content. Mitigations must be
 * applied wherever a `FLAG_SECURE`, Vault, or Tails surface is simultaneously visible with an untrusted
 * pane:
 * 1. **quantise** render callbacks to a coarse vsync quantum so per-frame duration is bucketed;
 * 2. **isolate** the secure surface on an opaque layer with no cross-surface shader blend;
 * 3. **inject** a calibrated render-phase padding so draw time does not track content entropy;
 * 4. **refuse** animation/scroll primitives that amplify the timing signal while a secret is on screen.
 */
object GpuPixelTimingPixnappingShield {
    const val VSYNC_60HZ_NANOS = 16_666_667L

    enum class SurfaceKind { SECURE_VAULT, SECURE_TAILS, FLAG_SECURE_WINDOW, TRUSTED_DOCUMENT, UNTRUSTED_WEB, SYSTEM_OVERLAY }

    data class Layer(
        val id: String,
        val kind: SurfaceKind,
        val zOrder: Int,
        val opaque: Boolean,
        val widthPx: Int,
        val heightPx: Int,
        val alpha: Float = 1.0f,
    )

    data class CompositionPlan(
        val isolatedLayerIds: List<String>,
        val quantisedDrawNanos: Long,
        val injectedPaddingNanos: Long,
        val animationsSuppressed: Int,
        val crossSurfaceBlendAllowed: Boolean,
        val reasons: List<String>,
    )

    fun isSensitive(kind: SurfaceKind): Boolean =
        kind == SurfaceKind.SECURE_VAULT || kind == SurfaceKind.SECURE_TAILS || kind == SurfaceKind.FLAG_SECURE_WINDOW

    fun plan(layers: List<Layer>, requestedAnimationCount: Int, vsyncNanos: Long = VSYNC_60HZ_NANOS): CompositionPlan {
        require(vsyncNanos > 0) { "vsync quantum must be positive" }
        require(layers.map { it.id }.distinct().size == layers.size) { "duplicate layer ids" }
        val sensitive = layers.filter { isSensitive(it.kind) }
        if (sensitive.isEmpty()) {
            return CompositionPlan(
                isolatedLayerIds = emptyList(),
                quantisedDrawNanos = vsyncNanos,
                injectedPaddingNanos = 0,
                animationsSuppressed = 0,
                crossSurfaceBlendAllowed = true,
                reasons = listOf("no sensitive surface on screen"),
            )
        }

        val reasons = ArrayList<String>()
        val untrusted = layers.filter { it.kind == SurfaceKind.UNTRUSTED_WEB || it.kind == SurfaceKind.SYSTEM_OVERLAY }
        val isolated = ArrayList<String>()
        for (s in sensitive) {
            if (!s.opaque) reasons.add("sensitive layer ${s.id} forced opaque to block blend-based probing")
            isolated.add(s.id)
        }
        if (untrusted.isNotEmpty()) {
            reasons.add("${untrusted.size} untrusted layer(s) co-resident with a secret surface")
        }
        val animSuppressed = if (untrusted.isNotEmpty()) requestedAnimationCount else 0
        if (animSuppressed > 0) reasons.add("suppressed $animSuppressed animation/scroll primitives")

        // Render-time quantisation to 4 vsync quanta + calibrated padding to break the correlation.
        val quanta = 4L
        // One extra vsync of render-phase padding keeps the total on the vsync grid while decorrelating it.
        val padding = vsyncNanos
        val quantised = (quanta + 1) * vsyncNanos
        return CompositionPlan(
            isolatedLayerIds = isolated.sorted(),
            quantisedDrawNanos = quantised,
            injectedPaddingNanos = padding,
            animationsSuppressed = animSuppressed,
            crossSurfaceBlendAllowed = false,
            reasons = reasons.ifEmpty { listOf("secret surface present; quantised render isolation applied") },
        )
    }

    /** Tests whether two draw durations are distinguishable at the given measurement noise (nanoseconds). */
    fun distinguishable(drawA: Long, drawB: Long, noiseNanos: Long): Boolean {
        require(noiseNanos >= 0) { "noise must be non-negative" }
        return kotlin.math.abs(drawA - drawB) > 3 * noiseNanos
    }

    /** Quantises a raw measured draw duration into the shield's reporting bucket. */
    fun bucket(drawNanos: Long, vsyncNanos: Long = VSYNC_60HZ_NANOS): Long {
        require(vsyncNanos > 0) { "vsync quantum must be positive" }
        val q = 4 * vsyncNanos
        return ((drawNanos + q - 1) / q) * q
    }
}
