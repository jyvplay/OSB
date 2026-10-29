// SPDX-License-Identifier: Apache-2.0
package dev.fusion.browser.model

/**
 * Deterministic 4-tier escalation router for **Recall** automatic browser media recording.
 *
 * Selects the optimal recording strategy across:
 * 1. `TIER_1_DIRECT_RANGE` — fast direct HTTP `200`/`206`, `srcset`, inline SVG, or `data:` capture;
 * 2. `TIER_2_HLS_DASH_FMP4` — adaptive `.m3u8` HLS / `.mpd` MPEG-DASH / `fMP4` segment assembly;
 * 3. `TIER_3_CANVAS_WEBAUDIO` — `blob:` MediaSource, `<canvas>` frame-strip & WebAudio PCM/Opus siphon;
 * 4. `TIER_4_NATIVE_VIEWPORT_AV` — ultimate native viewport-cropped `MediaProjection` + `AudioPlaybackCapture` screenrecorder + audio siphoner for difficult non-DRM sites & weird formats.
 */
object RecallMethodEscalationRouter {
    const val SCOPE =
        "Recall 4-tier automatic recording escalation router (TIER_1_DIRECT_RANGE -> TIER_2_HLS_DASH_FMP4 -> TIER_3_CANVAS_WEBAUDIO -> TIER_4_NATIVE_VIEWPORT_AV) with amnesic incognito bypass and strict DRM/FLAG_SECURE refusal."

    enum class RecordingTier(val rank: Int, val complexityLabel: String) {
        TIER_1_DIRECT_RANGE(1, "Easy / Fast Direct & HTTP-206 Byte-Range"),
        TIER_2_HLS_DASH_FMP4(2, "Moderate Adaptive HLS/DASH & fMP4 Muxer"),
        TIER_3_CANVAS_WEBAUDIO(3, "Complex Canvas RGBA & WebAudio PCM/Opus Siphon"),
        TIER_4_NATIVE_VIEWPORT_AV(4, "Ultimate Native Viewport Screenrecorder + Audio Siphoner"),
        TIER_25_WEBRTC_RTP_DEPACKETIZER(5, "Real-Time WebRTC RFC 3550/7587 RTP Opus/VP8 De-packetizer"),
        TIER_5_WARC_ISO28500_BUNDLE(6, "Archival ISO 28500:2017 WARC/1.1 Multi-Resource Replay Bundle"),
    }

    enum class ExtendedRecordingMethod(val methodNumber: Int, val tierAlias: String, val complexityLabel: String) {
        METHOD_1_DIRECT_RANGE(1, "T1", "Direct DOM / srcset / HTTP-206 Byte-Range"),
        METHOD_2_HLS_DASH_FMP4(2, "T2", "RFC 8216 HLS & ISO 23009-1 DASH -> fMP4"),
        METHOD_3_WEBRTC_RTP(3, "T2.5", "RFC 3550 / RFC 7587 WebRTC RTP Opus/VP8 De-packetizer"),
        METHOD_4_CANVAS_WEBAUDIO(4, "T3", "WebAudio PCM/Opus & Canvas Lossless Siphon"),
        METHOD_5_NATIVE_VIEWPORT_AV(5, "T4", "Native MediaProjection + AudioPlaybackCapture Loopback"),
        METHOD_6_WARC_ISO28500(6, "T5", "ISO 28500:2017 WARC/1.1 Multi-Resource Replay Bundle"),
        METHOD_7_MSE_SOURCEBUFFER_FLAC_TAP(7, "T6", "W3C MSE SourceBuffer Init+Media Segment & RFC 9639 FLAC Lossless Tap"),
    }

    sealed interface ExtendedRoutingDecision {
        data class Selected(val method: ExtendedRecordingMethod, val rationale: String) : ExtendedRoutingDecision
        data class SkippedOrRefused(val code: String, val reason: String) : ExtendedRoutingDecision
    }

    fun selectExtendedMethod(sig: ViewedResourceSignal): ExtendedRoutingDecision {
        if (sig.incognitoTab && !sig.explicitIncognitoOverride) {
            return ExtendedRoutingDecision.SkippedOrRefused("SKIP_AMNESIC_INCOGNITO", "Incognito tabs default to zero disk archival")
        }
        if (sig.drmEncryptedEme) {
            return ExtendedRoutingDecision.SkippedOrRefused("REFUSED_DRM_EME", "DRM/EME streams are never circumvented")
        }
        if (sig.flagSecureSurface) {
            return ExtendedRoutingDecision.SkippedOrRefused("REFUSED_FLAG_SECURE", "Window carries FLAG_SECURE protection")
        }
        if (sig.mseSourceBufferTapAvailable) {
            return ExtendedRoutingDecision.Selected(
                ExtendedRecordingMethod.METHOD_7_MSE_SOURCEBUFFER_FLAC_TAP,
                "W3C Media Source Extensions SourceBuffer init+media segment & RFC 9639 FLAC lossless tap",
            )
        }
        return when (val base = selectTier(sig)) {
            is RoutingDecision.SkippedOrRefused -> ExtendedRoutingDecision.SkippedOrRefused(base.code, base.reason)
            is RoutingDecision.Selected -> {
                val mapped = when (base.tier) {
                    RecordingTier.TIER_1_DIRECT_RANGE -> ExtendedRecordingMethod.METHOD_1_DIRECT_RANGE
                    RecordingTier.TIER_2_HLS_DASH_FMP4 -> ExtendedRecordingMethod.METHOD_2_HLS_DASH_FMP4
                    RecordingTier.TIER_25_WEBRTC_RTP_DEPACKETIZER -> ExtendedRecordingMethod.METHOD_3_WEBRTC_RTP
                    RecordingTier.TIER_3_CANVAS_WEBAUDIO -> ExtendedRecordingMethod.METHOD_4_CANVAS_WEBAUDIO
                    RecordingTier.TIER_4_NATIVE_VIEWPORT_AV -> ExtendedRecordingMethod.METHOD_5_NATIVE_VIEWPORT_AV
                    RecordingTier.TIER_5_WARC_ISO28500_BUNDLE -> ExtendedRecordingMethod.METHOD_6_WARC_ISO28500
                }
                ExtendedRoutingDecision.Selected(mapped, base.rationale)
            }
        }
    }

    data class ViewedResourceSignal(
        val url: String,
        val contentType: String,
        val incognitoTab: Boolean = false,
        val explicitIncognitoOverride: Boolean = false,
        val drmEncryptedEme: Boolean = false,
        val flagSecureSurface: Boolean = false,
        val isBlobOrMediaSource: Boolean = false,
        val hasActiveWebAudioOrCanvasPlayer: Boolean = false,
        val obfuscatedCustomFormat: Boolean = false,
        val priorFailedTiers: Set<RecordingTier> = emptySet(),
        val isWebRtcRtpStream: Boolean = false,
        val warcBundleRequested: Boolean = false,
        val mseSourceBufferTapAvailable: Boolean = false,
    )

    sealed interface RoutingDecision {
        data class Selected(val tier: RecordingTier, val rationale: String) : RoutingDecision
        data class SkippedOrRefused(val code: String, val reason: String) : RoutingDecision
    }

    fun selectTier(sig: ViewedResourceSignal): RoutingDecision {
        if (sig.incognitoTab && !sig.explicitIncognitoOverride) {
            return RoutingDecision.SkippedOrRefused(
                "SKIP_AMNESIC_INCOGNITO",
                "Incognito/amnesic tabs default to zero disk archival unless explicitly armed by the user",
            )
        }
        if (sig.drmEncryptedEme) {
            return RoutingDecision.SkippedOrRefused(
                "REFUSED_DRM_EME",
                "Widevine/PlayReady/FairPlay/SAMPLE-AES DRM streams are never circumvented",
            )
        }
        if (sig.flagSecureSurface) {
            return RoutingDecision.SkippedOrRefused(
                "REFUSED_FLAG_SECURE",
                "Window carries FLAG_SECURE protection",
            )
        }

        val lowerUrl = sig.url.substringBefore('?').lowercase()
        val ct = sig.contentType.lowercase()

        val orderedCandidates = buildList {
            val lowerScheme = sig.url.lowercase()
            val looksWebRtc = sig.isWebRtcRtpStream || lowerScheme.startsWith("webrtc:") || lowerScheme.startsWith("rtp:") || "application/sdp" in ct
            val looksAdaptive = lowerUrl.endsWith(".m3u8") || lowerUrl.endsWith(".mpd") ||
                "mpegurl" in ct || "dash+xml" in ct
            val looksBlobOrProcedural = sig.isBlobOrMediaSource || sig.url.startsWith("blob:", ignoreCase = true) ||
                sig.hasActiveWebAudioOrCanvasPlayer

            if (sig.warcBundleRequested) {
                add(RecordingTier.TIER_5_WARC_ISO28500_BUNDLE to "ISO 28500:2017 WARC/1.1 multi-resource page + media archival bundle")
            }
            if (looksWebRtc) {
                add(RecordingTier.TIER_25_WEBRTC_RTP_DEPACKETIZER to "RFC 3550 / RFC 7587 WebRTC RTP jitter-buffer de-packetizer")
            }
            if (!looksWebRtc && !looksAdaptive && !looksBlobOrProcedural && !sig.obfuscatedCustomFormat) {
                add(RecordingTier.TIER_1_DIRECT_RANGE to "Direct static/range-addressable image, video, or audio asset")
            }
            if (!looksWebRtc && (looksAdaptive || !sig.obfuscatedCustomFormat)) {
                add(RecordingTier.TIER_2_HLS_DASH_FMP4 to "Segmented RFC 8216 HLS / ISO 23009-1 DASH / fMP4 stream")
            }
            if (looksBlobOrProcedural || looksWebRtc || !sig.obfuscatedCustomFormat) {
                add(RecordingTier.TIER_3_CANVAS_WEBAUDIO to "Browser MSE/Canvas/WebAudio live PCM & frame-strip siphon")
            }
            add(RecordingTier.TIER_4_NATIVE_VIEWPORT_AV to "Ultimate native viewport MediaProjection + AudioPlaybackCapture fallback for difficult sites/formats")
        }

        val chosen = orderedCandidates.firstOrNull { (tier, _) -> tier !in sig.priorFailedTiers }
            ?: return RoutingDecision.SkippedOrRefused("ALL_TIERS_EXHAUSTED", "All recording tiers failed on ${sig.url}")
        return RoutingDecision.Selected(chosen.first, chosen.second)
    }
}
