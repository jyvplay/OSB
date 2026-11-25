// SPDX-License-Identifier: Apache-2.0
package dev.fusion.cast.dlna

/**
 * Production-grade DLNA DIDL-Lite metadata extractor & SOAPAction validator.
 */
class DlnaDidlMetadataParser private constructor() {
    companion object {
        data class DidlItem(val title: String, val upnpClass: String, val resUri: String)

        fun extractFirstItem(didlXml: String): DidlItem? {
            val title = Regex("<dc:title>([^<]+)</dc:title>").find(didlXml)?.groupValues?.get(1)?.trim() ?: return null
            val cls = Regex("<upnp:class>([^<]+)</upnp:class>").find(didlXml)?.groupValues?.get(1)?.trim() ?: ""
            val uri = Regex("<res[^>]*>([^<]+)</res>").find(didlXml)?.groupValues?.get(1)?.trim() ?: ""
            return DidlItem(title, cls, uri)
        }

        fun isValidSoapAction(header: String): Boolean {
            val clean = header.trim().removeSurrounding("\"")
            return clean.startsWith("urn:schemas-upnp-org:service:") && '#' in clean
        }
    }
}
