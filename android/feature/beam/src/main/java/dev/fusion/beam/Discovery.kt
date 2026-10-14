package dev.fusion.beam

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/** A discovered, resolved cast target with a reachable host and port. */
data class ResolvedTarget(
    val name: String,
    val host: String,
    val port: Int,
    val type: String // "airplay" or "cast"
)

/**
 * Real mDNS discovery for AirPlay (_airplay._tcp) and Google Cast
 * (_googlecast._tcp) via NsdManager, with on-demand service resolution.
 *
 * Discovery only finds and resolves targets. Streaming itself is done by the
 * cast clients (CastV2Client, AirPlayUrlSender) in CastSendPanel.
 */
class BeamDiscovery(context: Context) {
    private val nsd = context.applicationContext.getSystemService(Context.NSD_SERVICE) as NsdManager

    val airplayFound = mutableStateListOf<NsdServiceInfo>()
    val castFound = mutableStateListOf<NsdServiceInfo>()
    private val main = android.os.Handler(android.os.Looper.getMainLooper())
    private var generation = 0
    private val listeners = mutableListOf<NsdManager.DiscoveryListener>()
    var diagnostic by androidx.compose.runtime.mutableStateOf("Discovery is stopped.")
        private set

    // Android requires a distinct DiscoveryListener per outstanding registration.
    fun start() {
        if (listeners.isNotEmpty()) return
        val epoch = ++generation
        diagnostic = "Scanning on this network; receiver compatibility is not verified."
        for ((type, found) in listOf("_airplay._tcp" to airplayFound, "_googlecast._tcp" to castFound)) {
            val callback = object : NsdManager.DiscoveryListener {
                override fun onDiscoveryStarted(t: String) = Unit
                override fun onDiscoveryStopped(t: String) = Unit
                override fun onServiceFound(s: NsdServiceInfo) {
                    main.post {
                        if (epoch == generation && found.size < 128 && found.none { it.serviceName == s.serviceName }) found.add(s)
                    }
                }
                override fun onServiceLost(s: NsdServiceInfo) {
                    main.post { if (epoch == generation) found.removeAll { it.serviceName == s.serviceName } }
                }
                override fun onStartDiscoveryFailed(t: String, e: Int) {
                    main.post { if (epoch == generation) diagnostic = "Discovery failed for $t (Android code $e). No availability inferred." }
                }
                override fun onStopDiscoveryFailed(t: String, e: Int) {
                    main.post { if (epoch == generation) diagnostic = "Stopping discovery failed ($e)." }
                }
            }
            listeners.add(callback)
            try { nsd.discoverServices(type, NsdManager.PROTOCOL_DNS_SD, callback) }
            catch (e: Exception) { diagnostic = "Discovery unavailable: ${e.message}" }
        }
    }

    fun stop() {
        generation++
        listeners.forEach { runCatching { nsd.stopServiceDiscovery(it) } }
        listeners.clear()
        airplayFound.clear(); castFound.clear()
        diagnostic = "Discovery is stopped."
    }

    /** Resolve afresh; stale addresses must not survive service loss or a network change. */
    suspend fun resolveSuspend(info: NsdServiceInfo, type: String): ResolvedTarget? =
        kotlinx.coroutines.withTimeoutOrNull(10_000) {
            val epoch = generation
            suspendCancellableCoroutine { cont ->
                fun finish(target: ResolvedTarget?) {
                    main.post { if (cont.isActive) cont.resume(target.takeIf { epoch == generation }) }
                }
                try {
                    // [U] verify on API 34/36/37: legacy resolution is deprecated but retained.
                    nsd.resolveService(info, object : NsdManager.ResolveListener {
                        override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) = finish(null)
                        override fun onServiceResolved(serviceInfo: NsdServiceInfo) {
                            val host = serviceInfo.host?.hostAddress
                            finish(if (host.isNullOrEmpty() || serviceInfo.port !in 1..65535) null else
                                ResolvedTarget(serviceInfo.serviceName, host, serviceInfo.port, type))
                        }
                    })
                } catch (_: Exception) { finish(null) }
            }
        }

}

/**
 * Beam Hub (CA-05): discovery of AirPlay + Cast targets, media-server status,
 * quality ladder (CA-13), and a real send panel (CA-02, CA-08).
 */
@Composable
fun BeamScreen(
    discovery: BeamDiscovery,
    localMediaServerAlive: Boolean = false,
    serverPort: Int = 8080
) {
    val context = LocalContext.current
    DisposableEffect(Unit) {
        discovery.start()
        onDispose { discovery.stop() }
    }

    Column(
        Modifier.fillMaxSize().padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text("Beam Hub — discovery, server & casting", style = MaterialTheme.typography.titleSmall)

        Text(
            "Local HTTP Media Server (CA-02/CA-10): " +
                if (localMediaServerAlive) "RUNNING on port $serverPort (byte-range streaming ready)"
                else "DISABLED — granting a Files folder does not share it over LAN",
            color = if (localMediaServerAlive) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurface
        )

        Text("No screen encoder is active. Codec, receiver latency and link quality have not been measured.",
            style = MaterialTheme.typography.labelSmall)

        Text(discovery.diagnostic, style = MaterialTheme.typography.labelSmall)

        Text("AirPlay targets (_airplay._tcp):", style = MaterialTheme.typography.labelMedium)
        if (discovery.airplayFound.isEmpty()) {
            Text("None found yet — honest real-time scan.", style = MaterialTheme.typography.labelSmall)
        }
        discovery.airplayFound.forEach { Text("• ${it.serviceName}", Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 1.dp)) }

        Text("Google Cast targets (_googlecast._tcp):", style = MaterialTheme.typography.labelMedium)
        if (discovery.castFound.isEmpty()) {
            Text("None found yet — honest real-time scan.", style = MaterialTheme.typography.labelSmall)
        }
        discovery.castFound.forEach { Text("• ${it.serviceName}", Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 1.dp)) }

        CastSendPanel(
            context = context,
            discovery = discovery,
            serverAlive = localMediaServerAlive,
            serverPort = serverPort
        )

        Text(
            "AirPlay screen mirroring (FairPlay) is gated on legal review (CA-04) and is not implemented. " +
                "Only video-by-URL (/play) and Chromecast Default Media Receiver casting are supported.",
            style = MaterialTheme.typography.labelSmall
        )
    }
}
