// SPDX-License-Identifier: Apache-2.0
package dev.fusion.browser

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
import dev.fusion.browser.model.RecallMethodEscalationRouter

/**
 * **Recall 6-Method Automatic Media Recording & Archival Matrix Sheet** inside `:feature:browser`,
 * exposing live routing across all **6 Recall tiers**:
 * - `T1`: Direct / HTTP-206 Range Harvest (`RecallTier1DirectAssetHarvester`)
 * - `T2`: HLS `m3u8` / DASH `MPD` -> ISO-BMFF `fMP4` (`RecallTier2HlsDashAssembler`)
 * - `T2.5`: WebRTC RFC 3550/7587 RTP Opus/VP8 Jitter-Buffer De-packetizer (`RecallTier25WebRtcRtpDepacketizer`)
 * - `T3`: DOM `<canvas>` + WebAudio PCM/WAV Siphoner (`RecallTier3CanvasWebAudioSiphoner`)
 * - `T4`: Native `MediaProjection` + `AudioPlaybackCapture` (`RecallTier4NativeScreenAudioRecorder`)
 * - `T5`: ISO 28500:2017 `WARC/1.1` + CDXJ Self-Contained Web Archive (`RecallTier5WarcIso28500Archiver`)
 */
@Composable
fun RecallSixMethodMatrixSheet(
    currentUrl: String,
    pageTitle: String,
    incognitoActive: Boolean,
    modifier: Modifier = Modifier,
) {
    var folderMode by remember { mutableStateOf("BY_URL_NAME") }
    var wantsWarcBundle by remember { mutableStateOf(false) }
    var webRtcStreamDetected by remember { mutableStateOf(true) }

    val decision = remember(incognitoActive, wantsWarcBundle, webRtcStreamDetected, currentUrl) {
        RecallMethodEscalationRouter.selectTier(
            RecallMethodEscalationRouter.ViewedResourceSignal(
                url = currentUrl,
                contentType = if (webRtcStreamDetected) "application/sdp" else "video/mp4",
                incognitoTab = incognitoActive,
                isWebRtcRtpStream = webRtcStreamDetected && !wantsWarcBundle,
                warcBundleRequested = wantsWarcBundle,
            )
        )
    }
    val decisionSummary = when (decision) {
        is RecallMethodEscalationRouter.RoutingDecision.Selected ->
            "${decision.tier.name} — ${decision.rationale}"
        is RecallMethodEscalationRouter.RoutingDecision.SkippedOrRefused ->
            "REFUSED (${decision.code}) — ${decision.reason}"
    }

    Surface(
        tonalElevation = 3.dp,
        shape = MaterialTheme.shapes.medium,
        modifier = modifier.fillMaxWidth().testTag("recall-six-method-matrix-sheet"),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = "Recall 6-Method Auto-Archiver (Image · Video · Music · WARC/1.1)",
                style = MaterialTheme.typography.titleSmall,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("BY_URL_NAME" to "URL Folder", "BY_PAGE_TITLE" to "Page Title", "BY_CUSTOM_FOLDER" to "Custom Vault").forEach { (id, label) ->
                    FilterChip(
                        selected = folderMode == id,
                        onClick = { folderMode = id },
                        label = { Text(label) },
                        modifier = Modifier.testTag("recall-delineation-$id"),
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = webRtcStreamDetected,
                    onClick = { webRtcStreamDetected = !webRtcStreamDetected },
                    label = { Text("T2.5 WebRTC RTP") },
                )
                FilterChip(
                    selected = wantsWarcBundle,
                    onClick = { wantsWarcBundle = !wantsWarcBundle },
                    label = { Text("T5 ISO WARC/1.1") },
                )
            }
            Text(
                text = "Active Route: $decisionSummary ($folderMode · ${pageTitle.ifBlank { currentUrl }})",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
