package dev.fusion.capture

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Capture UI. The consent prompt is owned by the host Activity because
 * MediaProjection consent must come from an Activity result.
 */
@Composable
fun CaptureScreen(onRequestConsent: () -> Unit, onStop: () -> Unit) {
    val recording by RecorderState.recording.collectAsState()
    val message by RecorderState.message.collectAsState()
    val lastFile by RecorderState.lastFile.collectAsState()

    Column(Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Screen recording", style = MaterialTheme.typography.titleSmall)
        Text("Video only. No audio capture, no annotation, no OCR in this build.")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = onRequestConsent, enabled = !recording) {
                Text(if (recording) "Recording..." else "Start (asks consent)")
            }
            OutlinedButton(onClick = onStop, enabled = recording) { Text("Stop") }
        }
        Text(message)
        lastFile?.let {
            Text("Last file: $it", style = MaterialTheme.typography.labelSmall)
            Text(
                "Saved in app-specific storage, so no storage permission is used. " +
                    "It disappears if you uninstall the app.",
                style = MaterialTheme.typography.labelSmall
            )
        }
        Text(
            "Android grants one capture per consent, so Start always re-prompts.",
            style = MaterialTheme.typography.labelSmall
        )
    }
}
