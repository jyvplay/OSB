package dev.fusion.model

/**
 * Shared value types. Pure Kotlin, no Android dependencies.
 * These are the only cross-module contracts; every probe result
 * is explicit about unknown states (DP-10).
 */

data class CodecInfo(
    val mime: String,
    val encoder: Boolean,
    val hardwareAccelerated: Boolean
)

data class DisplayInfo(
    val id: Int,
    val name: String,
    val width: Int,
    val height: Int,
    val refresh: Float,
    val presentation: Boolean
)

sealed interface CastTarget {
    val name: String
    val host: String
    data class AirPlay(override val name: String, override val host: String, val port: Int) : CastTarget
    data class Cast(override val name: String, override val host: String, val port: Int = 8009) : CastTarget
    data class Dlna(override val name: String, override val host: String, val controlUrl: String) : CastTarget
}

sealed interface CastSource {
    data class File(val uri: String) : CastSource
    data class Url(val url: String) : CastSource
    data object AppAudio : CastSource
    data object Screen : CastSource
    data class Window(val id: Long) : CastSource
}

data class CapabilitySnapshot(
    val sdk: Int,
    val fingerprint: String,
    val soc: String,
    val ramGb: Long,
    val presentationDisplays: Int,
    val encoders: List<CodecInfo>,
    val npu: String
)

enum class DensityProfile { COMPACT, BALANCED, TOUCH }
enum class ShortcutPreset { HYBRID, WINDOWS, MAC }
