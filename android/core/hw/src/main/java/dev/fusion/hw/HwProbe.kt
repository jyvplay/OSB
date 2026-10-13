package dev.fusion.hw

import android.content.Context
import android.content.pm.PackageManager
import android.media.MediaCodecList
import dev.fusion.model.CodecInfo

data class HwReport(
    val encoders: List<CodecInfo>,
    val decoders: List<CodecInfo>,
    val lowLatencyAudio: Boolean,
    val preferredCastCodec: String
)

/** Real MediaCodec + PackageManager probe. Never infers NPU from SoC name. */
object HwProbe {
    fun inspect(context: Context): HwReport {
        val enc = mutableListOf<CodecInfo>()
        val dec = mutableListOf<CodecInfo>()
        try {
            for (info in MediaCodecList(MediaCodecList.ALL_CODECS).codecInfos) {
                for (type in info.supportedTypes) {
                    val l = type.lowercase()
                    if (!l.startsWith("video/")) continue
                    val e = CodecInfo(type, info.isEncoder, info.isHardwareAccelerated)
                    if (info.isEncoder) enc.add(e) else dec.add(e)
                }
            }
        } catch (_: Exception) { }
        val pm = context.packageManager
        val lowLatency = try { pm.hasSystemFeature(PackageManager.FEATURE_AUDIO_LOW_LATENCY) } catch (_: Exception) { false }
        val hevc = enc.any { it.mime.equals("video/hevc", true) && it.hardwareAccelerated }
        return HwReport(
            enc.distinctBy { it.mime }, dec.distinctBy { it.mime },
            lowLatency, if (hevc) "HEVC" else "AVC"
        )
    }
}
