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

/** Existing gallery/subtitle behavior remains available; new player, open-stream and official-embed routes are explicit. */
@Composable
fun MediaScreen(context: Context, onView: (Uri, String) -> Unit) {
    var page by remember { mutableStateOf("player") }
    var status by remember { mutableStateOf("") }
    var directStreamInput by remember { mutableStateOf("") }
    var directStreamStatus by remember { mutableStateOf("No open stream validated.") }
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            runCatching { context.startActivity(Intent(context, LocalPlaybackActivity::class.java).setData(uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)) }
                .onFailure { status = "Player unavailable: ${it.javaClass.simpleName}" }
        } else status = "Selection cancelled; nothing played."
    }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(
                "player" to "Player",
                "services" to "Online services",
                "stream" to "Open stream",
                "library" to "Images & subtitles"
            ).forEach { (key, title) ->
                FilterChip(selected = page == key, onClick = { page = key }, label = { Text(title) }, modifier = Modifier.heightIn(min = 48.dp).testTag("media-tab-$key"))
            }
        }
        when (page) {
            "library" -> MediaLibraryScreen(context, onView)
            "services" -> ServiceLinksScreen(context)
            "stream" -> Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Direct open-licensed HTTPS audio & video", style = MaterialTheme.typography.titleLarge)
                Text("Validate Creative Commons, public-domain or self-hosted HTTPS .mp4 / .webm / .mp3 / .ogg / .opus / .wav / .flac streams without proprietary SDKs.")
                OutlinedTextField(
                    directStreamInput,
                    { if (it.length <= 2048) directStreamInput = it },
                    label = { Text("HTTPS media file URL") },
                    modifier = Modifier.fillMaxWidth().testTag("direct-stream-url")
                )
                Button(
                    onClick = {
                        runCatching { EmbeddedStreamContract.parseDirectOpenStream(directStreamInput.trim()) }
                            .onSuccess { desc -> directStreamStatus = "Validated ${desc.mediaKind} stream on ${desc.host}" }
                            .onFailure { err -> directStreamStatus = "Stream refused: ${err.message}" }
                    },
                    modifier = Modifier.heightIn(min = 48.dp).testTag("direct-stream-check")
                ) { Text("Validate stream") }
                Text(directStreamStatus, Modifier.testTag("direct-stream-status"))
            }
            else -> Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Your media, locally", style = MaterialTheme.typography.headlineSmall)
                Text("Open selected audio or video in Fusion's native foreground player. Play/pause, seek and aspect-fit video use Android's actual decoder. Nothing autoplays. Leaving the player releases playback; reopen explicitly to continue.")
                Button(onClick = { pick.launch(arrayOf("audio/*", "video/*")) }, modifier = Modifier.heightIn(min = 48.dp).testTag("media-open-local")) { Text("Choose audio or video…") }
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
    var activeEmbed by remember { mutableStateOf<EmbeddedStreamContract.EmbedDescriptor?>(null) }
    var status by remember { mutableStateOf("") }
    candidate?.let { link ->
        AlertDialog(onDismissRequest = { candidate = null }, title = { Text("Open ${link.service.title} externally?") }, text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(link.url)
                Text("This leaves Fusion for an installed app or browser, or opens the official web embed inside a strict origin-allowlisted container below. The service receives your connection and may associate accounts/device identifiers. Tracking parameters were removed, but the content ID remains. It is not anonymous.")
                Text("Service content, subscriptions, advertisements, DRM, regional availability and terms still apply; NOT built-in or universally free streaming. No proprietary SDK is bundled.")
            }
        }, confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                TextButton(onClick = {
                    activeEmbed = EmbeddedStreamContract.toOfficialEmbed(link)
                    candidate = null
                    status = "Official ${link.service.title} embed sandbox started."
                }, modifier = Modifier.heightIn(min = 48.dp).testTag("service-confirm-embed")) { Text("Load sandboxed embed") }
                TextButton(onClick = {
                    candidate = null
                    runCatching { context.startActivity(Intent.createChooser(Intent(Intent.ACTION_VIEW, Uri.parse(link.url)), "Open official ${link.service.title} player")) }
                        .onSuccess { status = "Handoff requested. Fusion cannot verify external playback." }
                        .onFailure { status = "No compatible external player/browser available." }
                }, modifier = Modifier.heightIn(min = 48.dp).testTag("service-confirm-open")) { Text("Open external player") }
            }
        },
            dismissButton = { TextButton(onClick = { candidate = null }, modifier = Modifier.heightIn(min = 48.dp).testTag("service-cancel")) { Text("Cancel") } })
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("YouTube · Spotify · SoundCloud", style = MaterialTheme.typography.titleLarge)
        Text("Use a public video, track, album, playlist, episode or SoundCloud set link. Validation is offline; zero network traffic occurs until you confirm external handoff or official embed playback.")
        OutlinedTextField(draft, { if (it.length <= 2048) draft = it }, label = { Text("HTTPS content link") }, modifier = Modifier.fillMaxWidth().testTag("service-url"), maxLines = 4)
        Button(onClick = {
            try { candidate = ServiceLink.parse(draft.trim()); status = "Link validated locally." }
            catch (e: Exception) { status = "Link refused: ${e.message?.take(160)}. No service contacted." }
        }, modifier = Modifier.heightIn(min = 48.dp).testTag("service-preview")) { Text("Review link…") }
        activeEmbed?.let { desc ->
            ConsentedEmbedPlayerCard(desc, onClose = {
                activeEmbed = null
                status = "Sandboxed embed closed and cookies/WebStorage purged."
            })
        }
        Text(status, Modifier.testTag("service-status"))
        Text("Official embeds enforce >=200×200dp visible viewport (YouTube IFrame Terms), SoundCloud attribution, and Spotify preview/session rules without bundling closed-source SDKs.", style = MaterialTheme.typography.bodySmall)
    }
}
