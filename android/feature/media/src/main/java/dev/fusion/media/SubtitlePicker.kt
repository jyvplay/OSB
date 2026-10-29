package dev.fusion.media

import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Explicit picker, bounded read-only inspection. No permission request, retained grant, or player. */
@Composable
fun SubtitlePicker(context: Context) {
    var subtitle by remember { mutableStateOf<SubRip?>(null) }
    var status by remember { mutableStateOf("Strict UTF-8 SRT subset, ≤1 MiB. No playback or styling.") }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null && !busy) {
            busy = true
            scope.launch {
                try {
                    val parsed = withContext(Dispatchers.IO) {
                        context.contentResolver.openInputStream(uri).use { input ->
                            SubRip.read(requireNotNull(input) { "Provider did not open the file" })
                        }
                    }
                    subtitle = parsed; status = "${parsed.cues().size} cues loaded read-only. File was not changed."
                } catch (e: CancellationException) { throw e }
                catch (_: Exception) { status = "Import refused: unreadable, unsupported or over-limit SRT. Previous preview retained." }
                finally { busy = false }
            }
        }
    }
    Column(Modifier.fillMaxWidth().heightIn(max = 280.dp).verticalScroll(rememberScrollState())) {
        Button(onClick = { picker.launch(arrayOf("text/*", "application/x-subrip", "application/octet-stream")) }, enabled = !busy) {
            Text(if (busy) "Reading subtitles…" else "Inspect subtitle file…")
        }
        Text(status)
        subtitle?.let { SubtitleInspector(it) }
    }
}

@Composable
fun SubtitleInspector(subtitle: SubRip) {
    var position by remember(subtitle) { mutableStateOf("0") }
    Text("Inspect at a time — not a playing media clock")
    OutlinedTextField(position, { if (it.length <= 16 && it.all { c -> c in '0'..'9' }) position = it },
        modifier = Modifier.fillMaxWidth().testTag("subtitle-position"), label = { Text("Time in milliseconds") }, singleLine = true)
    val ms = position.toLongOrNull()
    if (ms == null) Text("Enter a nonnegative time")
    else {
        val active = subtitle.at(ms)
        val vttLanes = remember(active, ms) {
            val synth = buildString {
                append("WEBVTT\n\n")
                active.take(8).forEachIndexed { i, cue ->
                    append("${i + 1}\n00:00:00.000 --> 00:59:59.999\n${cue.text}\n\n")
                }
            }
            WebVttCueTimelineEngine.activeCuesAt(WebVttCueTimelineEngine.parseWebVtt(synth), 100L)
        }
        Text("${active.size} active cues; showing up to 8 (${vttLanes.size} collision-free WebVTT lane(s))", Modifier.testTag("subtitle-count"))
        active.take(8).forEach { Text(it.text) }
    }
}
