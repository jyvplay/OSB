package dev.fusion.hw

import android.content.Context
import android.content.pm.PackageManager
import android.media.MediaCodecList
import dev.fusion.model.CodecInfo

data class HwReport(
    val encoders: List<CodecInfo>,
    val decoders: List<CodecInfo>,
    val lowLatencyAudio: Boolean?,
    val preferredCastCodec: String?,
    val errors: List<String> = emptyList()
)

/** Real MediaCodec + PackageManager probe. Never infers NPU from SoC name. */
object HwProbe {
    fun inspect(context: Context): HwReport {
        val enc = mutableListOf<CodecInfo>()
        val dec = mutableListOf<CodecInfo>()
        val errors = mutableListOf<String>()
        try {
            for (info in MediaCodecList(MediaCodecList.ALL_CODECS).codecInfos) {
                for (type in info.supportedTypes) {
                    val l = type.lowercase()
                    if (!l.startsWith("video/")) continue
                    val e = CodecInfo(type, info.isEncoder, info.isHardwareAccelerated)
                    if (info.isEncoder) enc.add(e) else dec.add(e)
                }
            }
        } catch (e: Exception) { errors += "Codec probe incomplete (${e.javaClass.simpleName})" }
        val pm = context.packageManager
        val lowLatency = try { pm.hasSystemFeature(PackageManager.FEATURE_AUDIO_LOW_LATENCY) } catch (e: Exception) { errors += "Audio probe unavailable (${e.javaClass.simpleName})"; null }
        val hevc = enc.any { it.mime.equals("video/hevc", true) && it.hardwareAccelerated }
        return HwReport(
            enc.distinctBy { it.mime to it.hardwareAccelerated }, dec.distinctBy { it.mime to it.hardwareAccelerated },
            lowLatency, when { hevc -> "HEVC"; enc.any { it.mime.equals("video/avc", true) && it.hardwareAccelerated } -> "AVC"; else -> null }, errors.toList()
        )
    }
}
