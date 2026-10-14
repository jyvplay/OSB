package dev.fusion.beam

import android.content.Context
import android.net.nsd.NsdServiceInfo
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.fusion.airplay.AirPlayUrlSender
import dev.fusion.castv2.CastV2Client
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.NetworkInterface

/**
 * Real send panel (CA-02 AirPlay /play, CA-08 Chromecast Default Media Receiver).
 *
 * Resolves a discovered target, builds a media URL (full URL, or a path under
 * the granted SAF tree served by LocalMediaServer), and drives the real
 * clients. Honest limits are surfaced in the status line.
 */
@Composable
fun CastSendPanel(
    context: Context,
    discovery: BeamDiscovery,
    serverAlive: Boolean,
    serverPort: Int
) {
    val scope = rememberCoroutineScope()
    var mediaInput by remember { mutableStateOf("") }
    var status by remember { mutableStateOf("Pick a target, enter a media URL or path, then send.") }
    var selectedAirplay by remember { mutableStateOf<NsdServiceInfo?>(null) }
    var selectedCast by remember { mutableStateOf<NsdServiceInfo?>(null) }
    var activeCastClient by remember { mutableStateOf<CastV2Client?>(null) }
    val phoneIp = remember { lanIpv4() ?: "unknown" }

    val mediaUrl: String = run {
        val t = mediaInput.trim()
        when {
            t.startsWith("http://") || t.startsWith("https://") -> t
            t.isNotBlank() && serverAlive -> "http://$phoneIp:$serverPort/${t.removePrefix("/")}"
            else -> ""
        }
    }

    Column(Modifier.fillMaxWidth().padding(top = 4.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("Send media", style = MaterialTheme.typography.labelMedium)
        OutlinedTextField(
            value = mediaInput,
            onValueChange = { mediaInput = it },
            singleLine = true,
            label = { Text(if (serverAlive) "Media URL or shared path" else "Media http(s) URL (local sharing disabled)") },
            modifier = Modifier.fillMaxWidth()
        )
        if (mediaUrl.isNotBlank()) {
            Text("Will cast: $mediaUrl", style = MaterialTheme.typography.labelSmall)
        } else if (mediaInput.isNotBlank()) {
            Text("Local file sharing is disabled. A Files folder grant does not publish files. Use an existing http(s) URL.",
                style = MaterialTheme.typography.labelSmall)
        }

        // AirPlay target picker + send
        if (discovery.airplayFound.isNotEmpty()) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                discovery.airplayFound.forEach { info ->
                    OutlinedButton(
                        onClick = { selectedAirplay = info }
                    ) {
                        Text((if (selectedAirplay?.serviceName == info.serviceName) "▶ " else "") + info.serviceName)
                    }
                }
            }
            Button(
                enabled = selectedAirplay != null && mediaUrl.isNotBlank(),
                onClick = {
                    val info = selectedAirplay ?: return@Button
                    scope.launch {
                        status = "Resolving ${info.serviceName}..."
                        val target = discovery.resolveSuspend(info, "airplay")
                        if (target == null) { status = "Could not resolve ${info.serviceName}."; return@launch }
                        status = "Sending /play to ${target.name} (${target.host}:${target.port})..."
                        val code = withContext(Dispatchers.IO) {
                            AirPlayUrlSender().play(target.host, target.port, mediaUrl)
                        }
                        status = if (code in 200..299) {
                            "AirPlay accepted (HTTP $code). The receiver is fetching the URL."
                        } else {
                            "AirPlay returned HTTP $code — tvOS may require pairing (pin), which is not implemented."
                        }
                    }
                }
            ) { Text("Play on AirPlay") }
        }

        // Cast target picker + send
        if (discovery.castFound.isNotEmpty()) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                discovery.castFound.forEach { info ->
                    OutlinedButton(
                        onClick = { selectedCast = info }
                    ) {
                        Text((if (selectedCast?.serviceName == info.serviceName) "▶ " else "") + info.serviceName)
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Button(
                    enabled = selectedCast != null && mediaUrl.isNotBlank() && activeCastClient == null,
                    onClick = {
                        val info = selectedCast ?: return@Button
                        scope.launch {
                            status = "Resolving ${info.serviceName}..."
                            val target = discovery.resolveSuspend(info, "cast")
                            if (target == null) { status = "Could not resolve ${info.serviceName}."; return@launch }
                            status = "Connecting to ${target.name} (${target.host}:${target.port})..."
                            try {
                                val client = withContext(Dispatchers.IO) {
                                    val c = CastV2Client(target.host, target.port)
                                    c.connect()
                                    c.launch()
                                    c.load(mediaUrl, mimeFor(mediaUrl), "Fusion")
                                    c
                                }
                                activeCastClient = client
                                status = "Casting to ${target.name}. Press Stop to end."
                            } catch (e: Exception) {
                                status = "Cast failed: ${e.message}"
                            }
                        }
                    }
                ) { Text("Play on Chromecast") }
                OutlinedButton(
                    enabled = activeCastClient != null,
                    onClick = {
                        activeCastClient?.close()
                        activeCastClient = null
                        status = "Cast session closed."
                    }
                ) { Text("Stop cast") }
            }
        }

        Text(status, style = MaterialTheme.typography.labelSmall)
    }
}

/** Best-effort LAN IPv4 of this phone for building LocalMediaServer URLs. */
private fun lanIpv4(): String? =
    runCatching {
        NetworkInterface.getNetworkInterfaces()?.toList()
            ?.asSequence()
            ?.flatMap { it.inetAddresses.asSequence() }
            ?.firstOrNull { addr ->
                !addr.isLoopbackAddress && addr.address.size == 4
            }?.hostAddress
    }.getOrNull()

private fun mimeFor(url: String): String {
    val path = url.substringBefore('?').substringBefore('#')
    return when {
        path.endsWith(".mp4", true) -> "video/mp4"
        path.endsWith(".webm", true) -> "video/webm"
        path.endsWith(".mkv", true) -> "video/x-matroska"
        path.endsWith(".m3u8", true) -> "application/x-mpegURL"
        path.endsWith(".mp3", true) -> "audio/mpeg"
        path.endsWith(".m4a", true) -> "audio/mp4"
        path.endsWith(".jpg", true) || path.endsWith(".jpeg", true) -> "image/jpeg"
        path.endsWith(".png", true) -> "image/png"
        else -> "video/mp4"
    }
}
