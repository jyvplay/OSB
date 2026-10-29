// SPDX-License-Identifier: Apache-2.0
package dev.fusion.cast.dlna

import java.net.URI

/**
 * UPnP AVTransport 1.0 **SOAP Action Envelope Builder & SSRF / XXE / CallStranger (`CVE-2020-12695`)
 * Callback URL Firewall** ensuring DLNA cast control only addresses RFC 1918 / link-local LAN sinks
 * and never emits `<!DOCTYPE` or external entity declarations!
 */
object UpnpSoapActionEnvelopeSanitizer {
    const val SCOPE =
        "UPnP AVTransport:1 SOAP XML envelope builder with CVE-2020-12695 CallStranger LAN-only callback firewall and XXE rejection."

    fun isSafePrivateLanEndpoint(url: String): Boolean {
        val uri = runCatching { URI(url.trim()) }.getOrNull() ?: return false
        if (uri.scheme != "http" && uri.scheme != "https") return false
        val host = uri.host ?: return false
        val octets = host.split('.').map { it.toIntOrNull() ?: return false }
        if (octets.size != 4 || octets.any { it !in 0..255 }) return false
        return when {
            octets[0] == 10 -> true
            octets[0] == 172 && octets[1] in 16..31 -> true
            octets[0] == 192 && octets[1] == 168 -> true
            octets[0] == 169 && octets[1] == 254 -> true
            else -> false // Blocks loopback 127.x SSRF AND public WAN CallStranger exfiltration!
        }
    }

    fun buildSetAvTransportUriSoap(instanceId: Int, mediaUri: String): String {
        require(instanceId >= 0 && isSafePrivateLanEndpoint(mediaUri)) {
            "Rejected non-LAN or unsafe media URI '$mediaUri' (SSRF / CallStranger guard)"
        }
        require(!mediaUri.contains("<!DOCTYPE", ignoreCase = true) && !mediaUri.contains("<!ENTITY", ignoreCase = true))
        val safeUri = mediaUri.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")
        return "<?xml version=\"1.0\" encoding=\"utf-8\"?>" +
            "<s:Envelope xmlns:s=\"http://schemas.xmlsoap.org/soap/envelope/\" s:encodingStyle=\"http://schemas.xmlsoap.org/soap/encoding/\">" +
            "<s:Body><u:SetAVTransportURI xmlns:u=\"urn:schemas-upnp-org:service:AVTransport:1\">" +
            "<InstanceID>$instanceId</InstanceID><CurrentURI>$safeUri</CurrentURI><CurrentURIMetaData/>" +
            "</u:SetAVTransportURI></s:Body></s:Envelope>"
    }
}
