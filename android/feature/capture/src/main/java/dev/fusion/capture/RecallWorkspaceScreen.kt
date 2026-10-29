// SPDX-License-Identifier: Apache-2.0
package dev.fusion.capture

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp

/**
 * Dedicated **Recall** 4-Tier Media Archiver control panel inside `:feature:capture`,
 * exposing Tier-4 Native Viewport Screenrecorder + `AudioPlaybackCapture` diagnostics,
 * even-macroblock (`YUV420p`) browser crop bounds, and `<= 20 ms` A/V PTS drift telemetry.
 */
@Composable
fun RecallWorkspaceScreen(
    viewportWidthPx: Int = 1920,
    viewportHeightPx: Int = 1080,
) {
    var selectedFolderMode by remember { mutableStateOf("BY_URL_NAME") }
    var archiveViewMode by remember { mutableStateOf(dev.fusion.designsystem.FusionViewModeVectorIcons.ViewModeKind.GRID) }
    val crop = remember(viewportWidthPx, viewportHeightPx) {
        RecallTier4NativeScreenAudioRecorder.computeBrowserViewportCrop(
            RecallTier4NativeScreenAudioRecorder.ViewportCropSpec(
                displayWidthPx = viewportWidthPx,
                displayHeightPx = viewportHeightPx,
                topChromePx = 88,
                bottomChromePx = 64,
            ),
            targetFps = 30,
        )
    }
    val policyGate = remember {
        RecallTier4NativeScreenAudioRecorder.evaluateCapturePolicy(
            RecallTier4NativeScreenAudioRecorder.NativeCapturePolicyRequest(
                mediaProjectionUserConsented = true,
                windowFlagSecure = false,
                emeHardwareDrmActive = false,
                audioUsage = RecallTier4NativeScreenAudioRecorder.USAGE_MEDIA,
                appAllowedCapturePolicy = RecallTier4NativeScreenAudioRecorder.ALLOW_CAPTURE_BY_ALL,
                manifestAllowAudioPlaybackCapture = true,
                targetSdkVersion = 37,
                sameAndroidUserProfile = true,
            ),
        )
    }

    Surface(
        tonalElevation = 1.dp,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth().testTag("recall-workspace-panel"),
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Recall Automatic Media Archiver (4-Tier Escalation)", style = MaterialTheme.typography.titleMedium)
            Text(
                "Automatically archives viewed browser Images, Videos, and Music into folders delineated by URL Name, Page Title, or Custom Template:",
                style = MaterialTheme.typography.bodySmall,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("BY_URL_NAME" to "1. URL Name", "BY_PAGE_TITLE" to "2. Page Title", "BY_CUSTOM_TEMPLATE" to "3. Custom").forEach { (k, lbl) ->
                    FilterChip(
                        selected = selectedFolderMode == k,
                        onClick = { selectedFolderMode = k },
                        label = { Text(lbl) },
                    )
                }
            }
            dev.fusion.designsystem.CrispHintedViewModeToolbar(
                activeMode = archiveViewMode,
                onModeChanged = { archiveViewMode = it },
                surfaceLabel = "recall-vault",
            )
            Text("• Method 1 (Fast/Easy): Direct HTTP 200/206 Byte-Range, highest-density srcset, SVG & data-URI capture.", style = MaterialTheme.typography.bodySmall)
            Text("• Method 2 (Adaptive): RFC 8216 HLS .m3u8 & ISO 23009-1 MPEG-DASH .mpd + ISO 14496-12 fMP4 fragment muxer.", style = MaterialTheme.typography.bodySmall)
            Text("• Method 2.5 (Real-Time WebRTC): RFC 3550 / RFC 7587 RTP Opus 48 kHz + VP8 jitter-buffer de-packetizer.", style = MaterialTheme.typography.bodySmall)
            Text("• Method 3 (Procedural): Live <canvas> RGBA frame-strip + WebAudio 48 kHz IEEE-Float32 WAV/Opus siphon.", style = MaterialTheme.typography.bodySmall)
            Text(
                "• Method 4 (Ultimate Native Screen + Audio Siphoner): Viewport crop ${crop.widthEven}x${crop.heightEven} @ ${crop.suggestedBitrateBps / 1_000} kbps " +
                    "(<=20 ms A/V PTS sync; policy=${policyGate::class.simpleName}).",
                style = MaterialTheme.typography.bodySmall,
            )
            Text("• Method 5 (Archival Replay): ISO 28500:2017 WARC/1.1 + SURT CDXJ index + MHTML self-contained web bundle.", style = MaterialTheme.typography.bodySmall)
        }
    }
}
