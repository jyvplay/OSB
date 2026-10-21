// SPDX-License-Identifier: Apache-2.0
package dev.fusion.media

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp

/** Existing gallery/subtitle behavior remains available; new player and provider routes are explicit. */
@Composable
fun MediaScreen(context: Context, onView: (Uri, String) -> Unit) {
    var page by remember { mutableStateOf("player") }
    var status by remember { mutableStateOf("") }
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            runCatching { context.startActivity(Intent(context, LocalPlaybackActivity::class.java).setData(uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)) }
                .onFailure { status = "Player unavailable: ${it.javaClass.simpleName}" }
        } else status = "Selection cancelled; nothing played."
    }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("player" to "Player", "services" to "Online services", "library" to "Images & subtitles").forEach { (key, title) ->
                FilterChip(selected = page == key, onClick = { page = key }, label = { Text(title) }, modifier = Modifier.heightIn(min = 48.dp).testTag("media-tab-$key"))
            }
        }
        when (page) {
            "library" -> MediaLibraryScreen(context, onView)
            "services" -> ServiceLinksScreen(context)
            else -> Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Your media, locally", style = MaterialTheme.typography.headlineSmall)
                Text("Open selected audio or video in Fusion's native foreground player. Play/pause, seek and aspect-fit video use Android's actual decoder. Nothing autoplays. Leaving the player releases playback; reopen explicitly to continue.")
                Button(onClick = { pick.launch(arrayOf("audio/*", "video/*")) }, modifier = Modifier.heightIn(min = 48.dp).testTag("media-open-local")) { Text("Choose audio or video…") }
                // R27 AUTHORED, NOT EXECUTED: opens the direct-URL stream player (ExoPlayer, user-supplied
                // URLs only). Service page URLs are refused there; service links keep external handoff.
                OutlinedButton(onClick = {
                    runCatching { context.startActivity(Intent(context, StreamPlayerActivity::class.java)) }
                        .onFailure { status = "Stream player unavailable: ${it.javaClass.simpleName}" }
                }, modifier = Modifier.heightIn(min = 48.dp).testTag("media-open-stream")) { Text("Open direct-URL stream player…") }
                if (status.isNotEmpty()) Text(status, Modifier.testTag("media-status"))
                Text("Codec support depends on Android and the selected file. A document provider can itself use a network. This app does not extract YouTube/Spotify/SoundCloud streams or bypass DRM. No background service, microphone or broad media-library permission is needed for this picker route.", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun ServiceLinksScreen(context: Context) {
    var draft by remember { mutableStateOf("") }
    var candidate by remember { mutableStateOf<ServiceLink?>(null) }
    var status by remember { mutableStateOf("") }
    candidate?.let { link ->
        AlertDialog(onDismissRequest = { candidate = null }, title = { Text("Open ${link.service.title} externally?") }, text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(link.url)
                Text("This leaves Fusion for an installed app or browser. The service receives your connection and may associate accounts/device identifiers. Tracking parameters were removed, but the content ID remains. It is not anonymous.")
                Text("Service content, subscriptions, advertisements, DRM, regional availability and terms still apply. This is an official-player handoff, NOT built-in or universally free streaming. No provider SDK or player is bundled.")
            }
        }, confirmButton = { TextButton(onClick = {
            candidate = null
            runCatching { context.startActivity(Intent.createChooser(Intent(Intent.ACTION_VIEW, Uri.parse(link.url)), "Open official ${link.service.title} player")) }
                .onSuccess { status = "Handoff requested. Fusion cannot verify external playback." }
                .onFailure { status = "No compatible external player/browser available." }
        }, modifier = Modifier.testTag("service-confirm-open")) { Text("Open external player") } },
            dismissButton = { TextButton(onClick = { candidate = null }, modifier = Modifier.testTag("service-cancel")) { Text("Cancel") } })
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("YouTube · Spotify · SoundCloud", style = MaterialTheme.typography.titleLarge)
        Text("Use a public video, track, album, playlist, episode or SoundCloud set link. Validation is offline; no connection occurs until you confirm the handoff.")
        OutlinedTextField(draft, { if (it.length <= 2048) draft = it }, label = { Text("HTTPS content link") }, modifier = Modifier.fillMaxWidth().testTag("service-url"), maxLines = 4)
        Button(onClick = {
            try { candidate = ServiceLink.parse(draft.trim()); status = "Link validated locally." }
            catch (e: Exception) { status = "Link refused: ${e.message?.take(160)}. No service contacted." }
        }, modifier = Modifier.heightIn(min = 48.dp).testTag("service-preview")) { Text("Review link…") }
        Text(status, Modifier.testTag("service-status"))
        Text("Full embedded service playback remains unaccepted. Spotify's streaming/commercial restrictions and YouTube client-identification requirements cannot honestly be called open-source, anonymous and free for every use case.", style = MaterialTheme.typography.bodySmall)
    }
}
