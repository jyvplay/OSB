// SPDX-License-Identifier: Apache-2.0
package dev.fusion.browser

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import dev.fusion.browser.model.RecallMethodEscalationRouter
import dev.fusion.browser.model.RecallTier6MseSourceBufferTap

/**
 * R54 **Recall** seven-method studio sheet.
 *
 * Surfaces all seven recording methods (T1, T2, T2.5, T3, T4, T5 WARC, T6 MSE/FLAC) with a live routing
 * preview: toggling a signal flag re-runs `RecallMethodEscalationRouter.selectExtendedMethod` and shows the
 * chosen method, its refusal reason, or the DRM/incognito refusal — so a user sees *why* a capture will or
 * will not happen before starting it.
 */
@Composable
fun RecallSevenMethodStudioSheet(
    pageUrl: String,
    pageTitle: String,
    modifier: Modifier = Modifier,
) {
    var mseTap by remember { mutableStateOf(true) }
    var webRtc by remember { mutableStateOf(false) }
    var warc by remember { mutableStateOf(true) }
    var drm by remember { mutableStateOf(false) }

    val decision = RecallMethodEscalationRouter.selectExtendedMethod(
        RecallMethodEscalationRouter.ViewedResourceSignal(
            url = pageUrl,
            contentType = if (webRtc) "application/sdp" else "video/mp4",
            drmEncryptedEme = drm,
            isWebRtcRtpStream = webRtc,
            warcBundleRequested = warc,
            mseSourceBufferTapAvailable = mseTap,
        ),
    )
    val label = when (decision) {
        is RecallMethodEscalationRouter.ExtendedRoutingDecision.Selected ->
            "Method ${decision.method.methodNumber} \u00b7 ${decision.method.tierAlias} \u2014 ${decision.method.complexityLabel}"
        is RecallMethodEscalationRouter.ExtendedRoutingDecision.SkippedOrRefused ->
            "Refused (${decision.code}): ${decision.reason}"
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .testTag("recall-seven-method-sheet")
            .semantics { contentDescription = "Recall seven method routing" },
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(
                text = "Recall studio \u2014 ${pageTitle.ifBlank { pageUrl }}",
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.semantics { heading() },
            )
            Text(text = label, style = MaterialTheme.typography.bodyMedium)
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
                Text(text = "MSE SourceBuffer tap (T6)", style = MaterialTheme.typography.bodySmall)
                Switch(checked = mseTap, onCheckedChange = { mseTap = it }, modifier = Modifier.testTag("toggle-mse"))
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(text = "WebRTC RTP (T2.5)", style = MaterialTheme.typography.bodySmall)
                Switch(checked = webRtc, onCheckedChange = { webRtc = it }, modifier = Modifier.testTag("toggle-webrtc"))
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(text = "WARC bundle (T5)", style = MaterialTheme.typography.bodySmall)
                Switch(checked = warc, onCheckedChange = { warc = it }, modifier = Modifier.testTag("toggle-warc"))
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(text = "DRM protected (never captured)", style = MaterialTheme.typography.bodySmall)
                Switch(checked = drm, onCheckedChange = { drm = it }, modifier = Modifier.testTag("toggle-drm"))
            }
            Text(
                text = "Container ${RecallTier6MseSourceBufferTap.containerOf("video/mp4")} \u00b7 " +
                    "kind ${RecallTier6MseSourceBufferTap.mediaKindOf("video/mp4")} \u00b7 " +
                    "methods ${RecallMethodEscalationRouter.ExtendedRecordingMethod.entries.size}",
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}
