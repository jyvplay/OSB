// SPDX-License-Identifier: Apache-2.0
package dev.fusion.media

import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.dash.DashMediaSource
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.ui.PlayerView

/**
 * R26 AUTHORED, NOT EXECUTED IN THIS SANDBOX. Requires Android Studio/CI compile plus
 * disposable-emulator verification before any playback claim.
 *
 * Honest scope: local picked files plus user-pasted DIRECT http(s) media/playlist URLs
 * (progressive, HLS, DASH) played by the Apache-2.0 AndroidX Media3 ExoPlayer. This does
 * NOT extract streams from YouTube/Spotify/SoundCloud pages, bypass login/Premium/region/
 * DRM, or contact any service on its own. Service links keep validated external handoff.
 */
class StreamPlayerActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { MaterialTheme { Surface(Modifier.fillMaxSize()) { StreamPlayerScreen() } } }
    }
}

@Composable
private fun StreamPlayerScreen() {
    val context = LocalContext.current
    var urlText by remember { mutableStateOf("") }
    var status by remember { mutableStateOf("Pick a local file or paste a direct media URL. Nothing plays until you press Play.") }
    var currentLabel by remember { mutableStateOf("No media loaded") }
    val player = remember {
        ExoPlayer.Builder(context).build().apply { playWhenReady = false }
    }
    DisposableEffect(player) { onDispose { player.release() } }

    fun load(uri: Uri, label: String) {
        try {
            val dataSource = DefaultDataSource.Factory(context)
            val item = MediaItem.fromUri(uri)
            val path = uri.toString().lowercase().substringBefore('?')
            val source = when {
                path.endsWith(".m3u8") -> HlsMediaSource.Factory(dataSource).createMediaSource(item)
                path.endsWith(".mpd") -> DashMediaSource.Factory(dataSource).createMediaSource(item)
                else -> ProgressiveMediaSource.Factory(dataSource).createMediaSource(item)
            }
            player.setMediaSource(source)
            player.prepare()
            player.playWhenReady = false
            currentLabel = label
            status = "Loaded. Press Play."
        } catch (t: Exception) {
            status = "Could not load: ${t.message?.take(160)}"
        }
    }

    val picker = androidx.activity.compose.rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) load(uri, "Picked file") else status = "No file picked."
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).testTag("stream-player")) {
        Text("Stream player (direct URLs only)", style = MaterialTheme.typography.titleMedium)
        Text("Plays local files and direct progressive/HLS/DASH URLs you supply. It never extracts " +
            "streams from service pages and never bypasses login, Premium, region or DRM checks.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(currentLabel, style = MaterialTheme.typography.labelMedium, modifier = Modifier.testTag("stream-label"))
        AndroidView(factory = { PlayerView(it).apply { this.player = player } },
            modifier = Modifier.fillMaxWidth().heightIn(min = 240.dp).testTag("stream-view"))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { player.playWhenReady = true; status = "Playing." },
                modifier = Modifier.heightIn(min = 48.dp).testTag("stream-play")) { Text("Play") }
            OutlinedButton(onClick = { player.playWhenReady = false; status = "Paused." },
                modifier = Modifier.heightIn(min = 48.dp).testTag("stream-pause")) { Text("Pause") }
            OutlinedButton(onClick = { picker.launch("*/*") },
                modifier = Modifier.heightIn(min = 48.dp).testTag("stream-pick")) { Text("Pick file") }
        }
        OutlinedTextField(urlText, { if (it.length <= 2048) urlText = it }, label = { Text("Direct https media URL") },
            modifier = Modifier.fillMaxWidth().testTag("stream-url"))
        OutlinedButton(onClick = {
            val text = urlText.trim()
            if (text.startsWith("https://") && text.length <= 2048 && text.all { it.code in 0x21..0x7e }) {
                val lower = text.lowercase().substringBefore('?')
                if (lower.endsWith(".m3u8") || lower.endsWith(".mpd") || lower.endsWith(".mp3") ||
                    lower.endsWith(".mp4") || lower.endsWith(".m4a") || lower.endsWith(".ogg") || lower.endsWith(".wav")) {
                    load(Uri.parse(text), text.take(80))
                } else status = "Only direct .mp3/.mp4/.m4a/.ogg/.wav/.m3u8/.mpd URLs are accepted."
            } else status = "Paste a bounded ASCII https URL."
        }, modifier = Modifier.heightIn(min = 48.dp).testTag("stream-load")) { Text("Load URL") }
        Text(status, style = MaterialTheme.typography.bodySmall, modifier = Modifier.testTag("stream-status"))
    }
}
