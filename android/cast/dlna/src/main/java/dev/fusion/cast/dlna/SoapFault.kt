// SPDX-License-Identifier: Apache-2.0
package dev.fusion.cast.dlna

/**
 * SOAP 1.1 fault parser for UPnP control errors.
 *
 * Sits next to [UpnpParsing] (device-description helpers): when a renderer
 * rejects an AVTransport action it answers with a SOAP `<Fault>`, and nothing
 * extracts the machine-readable failure from it. This reads `faultcode`,
 * `faultstring`, and the `<detail><UPnPError>` pair (`errorCode` numeric,
 * `errorDescription` text) using the same bounded substring search [UpnpParsing]
 * uses — namespace prefixes on `Fault` are tolerated, but the `detail` block
 * must contain exactly the UPnPError shape, otherwise there is no actionable
 * code to report. Missing fault parts are refused: a half-read error is worse
 * than an explicit parse failure.
 *
 * No Android imports here so this stays unit-testable on the JVM.
 */
object SoapFault {

    /** A parsed SOAP fault with its UPnP error detail. */
    data class Fault(
        val code: String,
        val message: String,
        val upnpCode: Int,
        val upnpDescription: String,
    )

    /**
     * Parses a SOAP fault envelope. Throws [IllegalArgumentException] when any
     * required part is missing or the UPnP error code is not numeric.
     */
    fun parse(xml: String): Fault {
        val faultBlock = block(xml, "Fault") ?: throw IllegalArgumentException("no Fault")
        val code = tagText(faultBlock, "faultcode") ?: throw IllegalArgumentException("no faultcode")
        val message = tagText(faultBlock, "faultstring") ?: throw IllegalArgumentException("no faultstring")
        val detail = block(faultBlock, "detail") ?: throw IllegalArgumentException("no detail")
        val upnp = block(detail, "UPnPError") ?: throw IllegalArgumentException("no UPnPError")
        val num = tagText(upnp, "errorCode") ?: throw IllegalArgumentException("no errorCode")
        val desc = tagText(upnp, "errorDescription") ?: throw IllegalArgumentException("no errorDescription")
        return Fault(code, message, num.toIntOrNull() ?: throw IllegalArgumentException("bad errorCode"), desc)
    }

    private fun tagText(xml: String, tag: String): String? {
        val open = xml.indexOf("<$tag>", ignoreCase = true)
        if (open < 0) return null
        val from = open + tag.length + 2
        val close = xml.indexOf("</$tag>", startIndex = from, ignoreCase = true)
        if (close < 0) return null
        return xml.substring(from, close).trim().takeIf { it.isNotEmpty() }
    }

    private fun block(xml: String, tag: String): String? {
        if (tag == "Fault") {
            // Fault tolerates a namespace prefix on both open and close tags.
            val openM = Regex(
                "<([A-Za-z0-9_-]+:)?Fault(\\s[^>]*)?>", RegexOption.IGNORE_CASE
            ).find(xml) ?: return null
            val closeM = Regex(
                "</([A-Za-z0-9_-]+:)?Fault\\s*>", RegexOption.IGNORE_CASE
            ).find(xml, openM.range.last + 1) ?: return null
            return xml.substring(openM.range.first, closeM.range.last + 1)
        }
        val open = xml.indexOf("<$tag", ignoreCase = true)
        if (open < 0) return null
        val openEnd = xml.indexOf('>', open)
        if (openEnd < 0) return null
        val closeTag = "</$tag>"
        val close = xml.indexOf(closeTag, startIndex = openEnd, ignoreCase = true)
        if (close < 0) return null
        return xml.substring(open, close + closeTag.length)
    }
}
