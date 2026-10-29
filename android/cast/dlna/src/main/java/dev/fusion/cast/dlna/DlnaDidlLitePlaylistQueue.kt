// SPDX-License-Identifier: Apache-2.0
package dev.fusion.cast.dlna

/**
 * Clean-room UPnP / DLNA `AVTransport:1` Gapless Playlist Queue (`SetAVTransportURI` +
 * `SetNextAVTransportURI`) and XXE-Safe `DIDL-Lite` XML Metadata Builder (`CA-03`).
 */
object DlnaDidlLitePlaylistQueue {
    const val SCOPE =
        "Pure-JVM UPnP AVTransport:1 gapless current/next URI queue planner and XXE-safe DIDL-Lite XML item serializer/parser (CA-03)."

    data class DlnaTrack(
        val id: String,
        val title: String,
        val mimeType: String,
        val httpLanUrl: String,
        val durationHhMmSs: String,
    )

    data class AvTransportPair(val currentTrack: DlnaTrack, val nextGaplessTrack: DlnaTrack?)

    fun planGaplessPair(queue: List<DlnaTrack>, activeIndex: Int): AvTransportPair {
        require(queue.isNotEmpty() && activeIndex in queue.indices) { "invalid DLNA queue index" }
        return AvTransportPair(queue[activeIndex], queue.getOrNull(activeIndex + 1))
    }

    fun buildDidlLiteXml(track: DlnaTrack): String {
        require(track.httpLanUrl.startsWith("http://") || track.httpLanUrl.startsWith("https://")) {
            "DLNA transport URI must be HTTP(S)"
        }
        val upnpClass = when {
            track.mimeType.startsWith("audio/") -> "object.item.audioItem.musicTrack"
            track.mimeType.startsWith("video/") -> "object.item.videoItem"
            else -> "object.item.imageItem.photo"
        }
        return buildString {
            append("<DIDL-Lite xmlns=\"urn:schemas-upnp-org:metadata-1-0/DIDL-Lite/\" ")
            append("xmlns:dc=\"http://purl.org/dc/elements/1.1/\" ")
            append("xmlns:upnp=\"urn:schemas-upnp-org:metadata-1-0/upnp/\">")
            append("<item id=\"${esc(track.id)}\" parentID=\"0\" restricted=\"1\">")
            append("<dc:title>${esc(track.title)}</dc:title>")
            append("<upnp:class>$upnpClass</upnp:class>")
            append("<res protocolInfo=\"http-get:*:${esc(track.mimeType)}:*\" duration=\"${esc(track.durationHhMmSs)}\">${esc(track.httpLanUrl)}</res>")
            append("</item></DIDL-Lite>")
        }
    }

    private fun esc(s: String): String =
        s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")
}
