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
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import dev.fusion.perf.ThermalState
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
    private val resolved = mutableMapOf<String, ResolvedTarget>()
    private var listening = false

    private val listener = object : NsdManager.DiscoveryListener {
        override fun onDiscoveryStarted(t: String) {}
        override fun onDiscoveryStopped(t: String) {}
        override fun onServiceFound(s: NsdServiceInfo) {
            when (s.serviceType) {
                "_airplay._tcp." -> if (airplayFound.none { it.serviceName == s.serviceName }) airplayFound.add(s)
                "_googlecast._tcp." -> if (castFound.none { it.serviceName == s.serviceName }) castFound.add(s)
            }
        }
        override fun onServiceLost(s: NsdServiceInfo) {
            airplayFound.removeAll { it.serviceName == s.serviceName }
            castFound.removeAll { it.serviceName == s.serviceName }
        }
        override fun onStartDiscoveryFailed(t: String, e: Int) {}
        override fun onStopDiscoveryFailed(t: String, e: Int) {}
    }

    fun start() {
        if (listening) return
        listening = true
        try { nsd.discoverServices("_airplay._tcp", NsdManager.PROTOCOL_DNS_SD, listener) } catch (_: Exception) {}
        try { nsd.discoverServices("_googlecast._tcp", NsdManager.PROTOCOL_DNS_SD, listener) } catch (_: Exception) {}
    }

    fun stop() {
        if (!listening) return
        try { nsd.stopServiceDiscovery(listener) } catch (_: Exception) {}
        listening = false
    }

    /** Resolve a discovered service to a host/port. Cached after first success. */
    suspend fun resolveSuspend(info: NsdServiceInfo, type: String): ResolvedTarget? =
        suspendCancellableCoroutine { cont ->
            val key = type + ":" + info.serviceName
            resolved[key]?.let { cont.resume(it); return@suspendCancellableCoroutine }
            try {
                nsd.resolveService(info, object : NsdManager.ResolveListener {
                    override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                        cont.resume(null)
                    }
                    override fun onServiceResolved(serviceInfo: NsdServiceInfo) {
                        val target = ResolvedTarget(
                            name = serviceInfo.serviceName,
                            host = serviceInfo.host?.hostAddress ?: "",
                            port = serviceInfo.port,
                            type = type
                        )
                        if (target.host.isNotEmpty()) resolved[key] = target
                        cont.resume(target.takeIf { it.host.isNotEmpty() })
                    }
                })
            } catch (_: Exception) {
                cont.resume(null)
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
    serverPort: Int = 8080,
    currentThermal: ThermalState = ThermalState.COOL,
    linkSpeedMbps: Int = 100
) {
    val context = LocalContext.current
    DisposableEffect(Unit) {
        discovery.start()
        onDispose { discovery.stop() }
    }

    val ladder = remember(currentThermal, linkSpeedMbps) {
        QualityGovernor.selectQuality(
            linkSpeedMbps = linkSpeedMbps,
            thermalState = currentThermal,
            receiverSupportsHevc = true
        )
    }

    Column(
        Modifier.fillMaxSize().padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text("Beam Hub — discovery, server & casting", style = MaterialTheme.typography.titleSmall)

        Text(
            "Local HTTP Media Server (CA-02/CA-10): " +
                if (localMediaServerAlive) "RUNNING on port $serverPort (byte-range streaming ready)"
                else "OFF (no SAF folder granted)",
            color = if (localMediaServerAlive) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurface
        )

        Text(
            "Quality Governor (CA-13): ${ladder.resolutionLabel} @ ${ladder.fps}fps · ${ladder.codec}",
            style = MaterialTheme.typography.labelMedium
        )
        Text(ladder.reason, style = MaterialTheme.typography.labelSmall)

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
