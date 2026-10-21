package dev.fusion.cast.dlna

import java.net.URL

/**
 * Pure parsing helpers for SSDP + UPnP device descriptions.
 * No Android imports here so these stay unit-testable on the JVM.
 */
object UpnpParsing {

    /** Case-insensitive header lookup from an SSDP response. */
    fun header(response: String, name: String): String? =
        response.lineSequence()
            .map { it.trim() }
            .firstOrNull { it.startsWith("$name:", ignoreCase = true) }
            ?.substringAfter(':')
            ?.trim()
            ?.takeIf { it.isNotEmpty() }

    fun tagText(xml: String, tag: String): String? {
        val open = xml.indexOf("<$tag>", ignoreCase = true, startIndex = 0)
        if (open < 0) return null
        val from = open + tag.length + 2
        val close = xml.indexOf("</$tag>", startIndex = from, ignoreCase = true)
        if (close < 0) return null
        return xml.substring(from, close).trim().takeIf { it.isNotEmpty() }
    }

    /**
     * Finds the controlURL of the AVTransport service inside a device description.
     * Returns null when the device has no AVTransport (e.g. a renderer-less server).
     */
    fun avTransportControlPath(xml: String): String? {
        var cursor = 0
        while (true) {
            val service = xml.indexOf("<service", cursor, ignoreCase = true)
            if (service < 0) return null
            val end = xml.indexOf("</service>", service, ignoreCase = true)
                .let { if (it < 0) xml.length else it }
            val block = xml.substring(service, end)
            if (block.contains("AVTransport", ignoreCase = true)) {
                tagText(block, "controlURL")?.let { return it }
            }
            cursor = end + 1
        }
    }

    /** Resolves a possibly-relative controlURL against the device LOCATION. */
    fun resolve(location: String, path: String): String =
        runCatching { URL(URL(location), path).toString() }.getOrDefault(path)

    fun soapEnvelope(action: String, body: String): String =
        """<?xml version="1.0" encoding="utf-8"?>""" +
            """<s:Envelope xmlns:s="http://schemas.xmlsoap.org/soap/envelope/" """ +
            """s:encodingStyle="http://schemas.xmlsoap.org/soap/encoding/"><s:Body>""" +
            """<u:$action xmlns:u="urn:schemas-upnp-org:service:AVTransport:1">$body""" +
            """</u:$action></s:Body></s:Envelope>"""

    fun xmlEscape(value: String): String =
        value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
            .replace("\"", "&quot;").replace("'", "&apos;")

    /** Single-pass inverse of [xmlEscape]; &amp; resolves last. Unknown entities pass through untouched. */
    fun unescapeXml(value: String): String =
        value.replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"")
            .replace("&apos;", "'").replace("&amp;", "&")
}
