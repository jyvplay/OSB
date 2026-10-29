// SPDX-License-Identifier: Apache-2.0
package dev.fusion.cast.dlna

import java.net.URI

/**
 * Clean-room UPnP / DLNA SSDP `HTTP/1.1 200 OK` & `NOTIFY * HTTP/1.1` Discovery Response Parser
 * with LAN-Only `LOCATION` SSRF Guard.
 *
 * Refuses `LOCATION` descriptor URLs pointing to loopback (`127.0.0.1`), cloud metadata
 * (`169.254.169.254`), or public WAN hosts so a spoofed SSDP UDP datagram can never trick the
 * device into fetching arbitrary internal/external HTTP endpoints!
 */
object SsdpDiscoveryResponseParser {
    data class SsdpDeviceAnnouncement(
        val usn: String,
        val stOrNt: String,
        val locationUrl: String,
        val serverHeader: String,
        val cacheMaxAgeSeconds: Int,
    )

    fun parseDatagram(rawDatagram: String): SsdpDeviceAnnouncement {
        val lines = rawDatagram.replace("\r\n", "\n").lines()
        require(lines.isNotEmpty()) { "empty SSDP datagram" }
        val status = lines[0].trim().uppercase()
        require(status.startsWith("HTTP/1.1 200") || status.startsWith("NOTIFY * HTTP/1.1")) {
            "unexpected SSDP start line '${lines[0]}'"
        }

        val headers = LinkedHashMap<String, String>()
        for (i in 1 until lines.size) {
            val line = lines[i].trim()
            if (line.isEmpty()) break
            val colon = line.indexOf(':')
            if (colon > 0) {
                headers[line.substring(0, colon).trim().lowercase()] = line.substring(colon + 1).trim()
            }
        }

        val usn = headers["usn"] ?: throw IllegalArgumentException("SSDP packet missing USN header")
        val st = headers["st"] ?: headers["nt"] ?: throw IllegalArgumentException("SSDP packet missing ST/NT header")
        val loc = headers["location"] ?: throw IllegalArgumentException("SSDP packet missing LOCATION header")
        validateSafeLanLocation(loc)

        val maxAge = Regex("""max-age\s*=\s*(\d+)""", RegexOption.IGNORE_CASE)
            .find(headers["cache-control"].orEmpty())?.groupValues?.get(1)?.toIntOrNull() ?: 1800

        return SsdpDeviceAnnouncement(
            usn = usn,
            stOrNt = st,
            locationUrl = loc,
            serverHeader = headers["server"] ?: "",
            cacheMaxAgeSeconds = maxAge.coerceIn(60, 86_400),
        )
    }

    fun validateSafeLanLocation(locationUrl: String) {
        val uri = URI(locationUrl)
        val scheme = uri.scheme?.lowercase()
        require(scheme == "http" || scheme == "https") { "SSDP LOCATION must use http/https" }
        val host = uri.host?.lowercase() ?: throw IllegalArgumentException("SSDP LOCATION missing host")
        val parts = host.split('.')
        require(parts.size == 4 && parts.all { it.toIntOrNull() in 0..255 }) {
            "SSDP LOCATION must be an explicit RFC 1918 LAN IPv4 literal, got '$host'"
        }
        val a = parts[0].toInt()
        val b = parts[1].toInt()
        val isRfc1918Lan = a == 10 || (a == 172 && b in 16..31) || (a == 192 && b == 168)
        require(isRfc1918Lan) {
            "SSRF guard: SSDP LOCATION '$host' rejected (outside RFC 1918 private LAN)"
        }
    }
}
