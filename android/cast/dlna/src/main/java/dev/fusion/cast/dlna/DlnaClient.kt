package dev.fusion.cast.dlna

import android.content.Context
import android.net.wifi.WifiManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.URL

data class Renderer(
    val friendlyName: String,
    val location: String,
    val controlUrl: String
)

/**
 * Real DLNA/UPnP control point.
 *
 * Scope that is actually implemented:
 *   - SSDP M-SEARCH discovery of MediaRenderer devices
 *   - fetching + parsing the device description
 *   - AVTransport SetAVTransportURI + Play for a URL the renderer can reach
 *
 * The renderer fetches the media itself, so the URL must be reachable from the
 * TV. Local phone files can be served by the Beam module's LocalMediaServer at
 * http://<phone-ip>:8080/<path-under-your-granted-folder>.
 *
 * Not implemented: no transcoding, no playlist queue, no volume control, and
 * no RenderingControl service.
 */
class DlnaClient(private val context: Context) {

    suspend fun discover(timeoutMs: Int = 3000): Result<List<Renderer>> = withContext(Dispatchers.IO) {
        runCatching {
            val wifi = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            val lock = wifi.createMulticastLock("fusion-ssdp").apply {
                setReferenceCounted(true); acquire()
            }
            try {
                val locations = linkedSetOf<String>()
                DatagramSocket().use { socket ->
                    socket.soTimeout = 600
                    socket.broadcast = true
                    val query = buildString {
                        append("M-SEARCH * HTTP/1.1\r\n")
                        append("HOST: $SSDP_HOST:$SSDP_PORT\r\n")
                        append("MAN: \"ssdp:discover\"\r\n")
                        append("MX: 2\r\n")
                        append("ST: $ST_RENDERER\r\n")
                        append("\r\n")
                    }.toByteArray()
                    val group = InetAddress.getByName(SSDP_HOST)
                    // Repeat: UDP discovery packets are routinely dropped.
                    repeat(3) {
                        socket.send(DatagramPacket(query, query.size, InetSocketAddress(group, SSDP_PORT)))
                    }
                    val deadline = System.currentTimeMillis() + timeoutMs
                    val buffer = ByteArray(4096)
                    while (System.currentTimeMillis() < deadline) {
                        val packet = DatagramPacket(buffer, buffer.size)
                        try {
                            socket.receive(packet)
                        } catch (_: Exception) {
                            continue // soTimeout tick; keep polling until the deadline
                        }
                        val response = String(packet.data, 0, packet.length, Charsets.UTF_8)
                        UpnpParsing.header(response, "LOCATION")?.let(locations::add)
                    }
                }
                locations.mapNotNull { describe(it) }
            } finally {
                runCatching { lock.release() }
            }
        }
    }

    private fun describe(location: String): Renderer? = runCatching {
        val connection = (URL(location).openConnection() as HttpURLConnection).apply {
            connectTimeout = 2500; readTimeout = 2500; requestMethod = "GET"
        }
        val xml = try {
            connection.inputStream.use { it.readBytes().toString(Charsets.UTF_8) }
        } finally {
            connection.disconnect()
        }
        val path = UpnpParsing.avTransportControlPath(xml) ?: return@runCatching null
        Renderer(
            friendlyName = UpnpParsing.tagText(xml, "friendlyName") ?: URL(location).host,
            location = location,
            controlUrl = UpnpParsing.resolve(location, path)
        )
    }.getOrNull()

    suspend fun playUrl(renderer: Renderer, mediaUrl: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            require(mediaUrl.startsWith("http://") || mediaUrl.startsWith("https://")) {
                "The renderer must fetch the media itself, so enter an http(s) URL"
            }
            soap(
                renderer, "SetAVTransportURI",
                "<InstanceID>0</InstanceID>" +
                    "<CurrentURI>${UpnpParsing.xmlEscape(mediaUrl)}</CurrentURI>" +
                    "<CurrentURIMetaData></CurrentURIMetaData>"
            )
            soap(renderer, "Play", "<InstanceID>0</InstanceID><Speed>1</Speed>")
        }
    }

    suspend fun stop(renderer: Renderer): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching { soap(renderer, "Stop", "<InstanceID>0</InstanceID>") }
    }

    private fun soap(renderer: Renderer, action: String, body: String) {
        val payload = UpnpParsing.soapEnvelope(action, body).toByteArray(Charsets.UTF_8)
        val connection = (URL(renderer.controlUrl).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            doOutput = true
            connectTimeout = 3000
            readTimeout = 4000
            setRequestProperty("Content-Type", "text/xml; charset=\"utf-8\"")
            setRequestProperty("SOAPAction", "\"$AV_TRANSPORT#$action\"")
            setFixedLengthStreamingMode(payload.size)
        }
        try {
            connection.outputStream.use { out -> out.write(payload) }
            val code = connection.responseCode
            if (code !in 200..299) {
                val detail = runCatching {
                    connection.errorStream?.readBytes()?.toString(Charsets.UTF_8).orEmpty()
                }.getOrDefault("")
                error("$action failed with HTTP $code ${detail.take(180)}")
            }
        } finally {
            connection.disconnect()
        }
    }

    companion object {
        private const val SSDP_HOST = "239.255.255.250"
        private const val SSDP_PORT = 1900
        private const val AV_TRANSPORT = "urn:schemas-upnp-org:service:AVTransport:1"
        private const val ST_RENDERER = "urn:schemas-upnp-org:device:MediaRenderer:1"
    }
}
