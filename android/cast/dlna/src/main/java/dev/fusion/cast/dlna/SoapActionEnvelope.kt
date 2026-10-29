// SPDX-License-Identifier: Apache-2.0
package dev.fusion.cast.dlna

/**
 * Clean-room UPnP AVTransport:1 SOAP 1.1 action envelope builder & response parser (`SoapActionEnvelope`).
 *
 * Powers `:cast:dlna` LAN media control (`SetAVTransportURI`, `Play`, `Pause`, `Seek`, `Stop`, `GetPositionInfo`)
 * while strictly refusing `<!DOCTYPE`/`<!ENTITY` XXE injection (`UPnP SOAP XXE` defense) and enforcing
 * LAN-only HTTP/HTTPS transport URIs!
 */
object SoapActionEnvelope {
    const val SCOPE =
        "UPnP AVTransport:1 SOAP 1.1 envelope builder + XXE-refusing response/Fault parser (SetAVTransportURI, Play, Pause, Seek, Stop); SSDP multicast socket binding out of scope."

    const val AV_TRANSPORT_URN = "urn:schemas-upnp-org:service:AVTransport:1"
    const val SOAP_NS = "http://schemas.xmlsoap.org/soap/envelope/"

    data class SoapRequest(
        val soapActionHeader: String,
        val xmlBody: String,
    )

    sealed interface SoapReply {
        data class Success(val actionName: String, val args: Map<String, String>) : SoapReply
        data class Fault(val faultCode: String, val faultString: String, val upnpErrorCode: Int?) : SoapReply
    }

    fun buildSetAvTransportUri(instanceId: Int = 0, currentUri: String, currentUriMetaData: String = ""): SoapRequest {
        require(instanceId >= 0) { "Negative InstanceID" }
        require(currentUri.startsWith("http://") || currentUri.startsWith("https://")) {
            "DLNA CurrentURI must use http:// or https://: $currentUri"
        }
        return buildAction(
            "SetAVTransportURI",
            linkedMapOf(
                "InstanceID" to instanceId.toString(),
                "CurrentURI" to currentUri,
                "CurrentURIMetaData" to currentUriMetaData,
            ),
        )
    }

    fun buildPlay(instanceId: Int = 0, speed: String = "1"): SoapRequest =
        buildAction("Play", linkedMapOf("InstanceID" to instanceId.toString(), "Speed" to speed))

    fun buildPause(instanceId: Int = 0): SoapRequest =
        buildAction("Pause", linkedMapOf("InstanceID" to instanceId.toString()))

    fun buildStop(instanceId: Int = 0): SoapRequest =
        buildAction("Stop", linkedMapOf("InstanceID" to instanceId.toString()))

    fun buildSeekRelTime(instanceId: Int = 0, targetHhMmSs: String): SoapRequest {
        require(Regex("^\\d{2}:[0-5]\\d:[0-5]\\d$").matches(targetHhMmSs)) {
            "Invalid UPnP REL_TIME target (expected HH:MM:SS): $targetHhMmSs"
        }
        return buildAction(
            "Seek",
            linkedMapOf("InstanceID" to instanceId.toString(), "Unit" to "REL_TIME", "Target" to targetHhMmSs),
        )
    }

    fun buildAction(actionName: String, params: Map<String, String>): SoapRequest {
        require(actionName.matches(Regex("^[A-Za-z][A-Za-z0-9_]{0,63}$"))) { "Invalid SOAP action: $actionName" }
        val xml = buildString {
            append("""<?xml version="1.0" encoding="utf-8"?>""")
            append("""<s:Envelope xmlns:s="$SOAP_NS" s:encodingStyle="http://schemas.xmlsoap.org/soap/encoding/">""")
            append("<s:Body>")
            append("""<u:$actionName xmlns:u="$AV_TRANSPORT_URN">""")
            for ((k, v) in params) {
                require(k.matches(Regex("^[A-Za-z][A-Za-z0-9_]{0,63}$"))) { "Invalid SOAP param: $k" }
                append("<$k>${esc(v)}</$k>")
            }
            append("</u:$actionName></s:Body></s:Envelope>")
        }
        return SoapRequest(soapActionHeader = "\"$AV_TRANSPORT_URN#$actionName\"", xmlBody = xml)
    }

    fun parseReply(rawXml: String): SoapReply {
        require(rawXml.length <= 64 * 1024) { "SOAP response exceeds 64 KiB cap" }
        require(!rawXml.contains("<!DOCTYPE", ignoreCase = true) && !rawXml.contains("<!ENTITY", ignoreCase = true)) {
            "Refusing UPnP SOAP response with <!DOCTYPE or <!ENTITY (XXE defense)"
        }
        if (rawXml.contains(":Fault>") || rawXml.contains("<Fault>")) {
            val fc = extractTagText(rawXml, "faultcode") ?: "s:Client"
            val fs = extractTagText(rawXml, "faultstring") ?: "UPnP Error"
            val errCode = extractTagText(rawXml, "errorCode")?.toIntOrNull()
            return SoapReply.Fault(fc, unesc(fs), errCode)
        }
        val respRe = Regex("""<(?:[A-Za-z0-9_]+:)?([A-Za-z0-9_]+)Response\b[^>]*>([\s\S]*?)</(?:[A-Za-z0-9_]+:)?\1Response>""")
        val m = respRe.find(rawXml) ?: throw IllegalArgumentException("Missing SOAP *Response body")
        val action = m.groupValues[1]
        val inner = m.groupValues[2]
        val argRe = Regex("""<([A-Za-z0-9_]+)>([\s\S]*?)</\1>""")
        val args = linkedMapOf<String, String>()
        for (am in argRe.findAll(inner)) {
            args[am.groupValues[1]] = unesc(am.groupValues[2])
        }
        return SoapReply.Success(action, args)
    }

    private fun extractTagText(xml: String, localName: String): String? =
        Regex("""<(?:[A-Za-z0-9_]+:)?$localName>([\s\S]*?)</(?:[A-Za-z0-9_]+:)?$localName>""")
            .find(xml)?.groupValues?.get(1)

    private fun esc(s: String): String = s
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")

    private fun unesc(s: String): String = s
        .replace("&quot;", "\"")
        .replace("&gt;", ">")
        .replace("&lt;", "<")
        .replace("&amp;", "&")
}
