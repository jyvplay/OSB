package dev.fusion.cast.dlna

import android.content.Context
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import kotlinx.coroutines.launch

@Composable
fun DlnaScreen(
    context: Context,
    localMediaServerAlive: Boolean = false,
    serverPort: Int = 8080
) {
    val client = remember { DlnaClient(context) }
    val scope = rememberCoroutineScope()
    var renderers by remember { mutableStateOf<List<Renderer>>(emptyList()) }
    var selected by remember { mutableStateOf<Renderer?>(null) }
    var url by remember { mutableStateOf("") }
    var status by remember { mutableStateOf("Not scanned yet.") }
    var scanning by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("DLNA MediaRenderers on Local Wi-Fi (CA-10)", style = MaterialTheme.typography.titleSmall)

        Text(
            "Local Media Server: " +
                if (localMediaServerAlive) "ACTIVE on port $serverPort (Ready to cast phone documents)"
                else "DISABLED (Files grants do not share files; only existing URLs can be sent)",
            style = MaterialTheme.typography.labelSmall
        )

        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Button(enabled = !scanning, onClick = {
                scanning = true; status = "Scanning SSDP multicast (239.255.255.250:1900)..."
                scope.launch {
                    client.discover().fold(
                        onSuccess = {
                            renderers = it
                            status = if (it.isEmpty())
                                "No MediaRenderer answered SSDP M-SEARCH. Ensure smart TV is awake and on same subnet."
                            else "Discovered ${it.size} DLNA renderer(s)."
                        },
                        onFailure = { status = "Scan failed: ${it.message}" }
                    )
                    scanning = false
                }
            }) { Text("Scan Multicast") }

            OutlinedButton(enabled = selected != null, onClick = {
                val r = selected ?: return@OutlinedButton
                scope.launch {
                    client.stop(r).fold({ status = "Stop playback sent to ${r.friendlyName}." }, { status = "Stop failed: ${it.message}" })
                }
            }) { Text("Stop") }
        }

        LazyColumn(Modifier.weight(1f)) {
            items(renderers, key = { it.location }) { r ->
                Column(Modifier.fillMaxWidth().clickable { selected = r }.padding(6.dp)) {
                    Text((if (selected?.location == r.location) "▶ " else "  ") + r.friendlyName)
                    Text(r.controlUrl, style = MaterialTheme.typography.labelSmall)
                }
            }
        }

        OutlinedTextField(
            value = url, onValueChange = { url = it }, singleLine = true,
            label = { Text("Existing media http(s) URL") },
            modifier = Modifier.fillMaxWidth()
        )
        if (url.startsWith("http://") || url.startsWith("https://")) {
            val didlPreview = remember(url) {
                DlnaDidlLitePlaylistQueue.buildDidlLiteXml(
                    DlnaDidlLitePlaylistQueue.DlnaTrack("1", "Fusion Stream", "video/mp4", url.trim(), "00:05:00")
                )
            }
            Text("DIDL-Lite envelope ready (${didlPreview.length} bytes, XXE-safe)", style = MaterialTheme.typography.labelSmall)
        }

        Button(enabled = selected != null && url.isNotBlank(), onClick = {
            val r = selected ?: return@Button
            scope.launch {
                client.playUrl(r, url.trim()).fold(
                    { status = "AVTransport SetAVTransportURI + Play accepted by ${r.friendlyName}." },
                    { status = "Play error: ${it.message}" }
                )
            }
        }) { Text("Cast to Selected Renderer") }

        Text(status)
        Text(
            "Uses SSDP M-SEARCH + SOAP AVTransport. Compatible with UPnP/DLNA TVs, soundbars, and receivers.",
            style = MaterialTheme.typography.labelSmall
        )
    }
}
