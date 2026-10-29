package dev.fusion.cast.dlna

/**
 * Clean-room UPnP AV ContentDirectory `DIDL-Lite` XML Metadata Builder & XXE-Refusing Parser
 * with `DLNA.ORG_PN` / `DLNA.ORG_OP=01` byte-seek `protocolInfo` synthesizer (Apache-2.0).
 */
object DidlLiteXmlMetadataBuilder {
    const val SCOPE =
        "UPnP AV DIDL-Lite XML builder/parser + DLNA.ORG_OP/FLAGS protocolInfo synthesizer with strict DOCTYPE/XXE refusal"

    data class MediaItemSpec(
        val id: String,
        val parentId: String = "0",
        val title: String,
        val creator: String = "Fusion Shell",
        val mimeType: String,
        val streamUrl: String,
        val byteSize: Long,
        val durationHhMmSs: String,
    )

    fun buildDidlLite(item: MediaItemSpec): String {
        require(item.streamUrl.startsWith("http://") || item.streamUrl.startsWith("https://")) {
            "DLNA streamUrl must be http(s)"
        }
        val dlnaProfile = when (item.mimeType.lowercase()) {
            "audio/flac" -> "DLNA.ORG_PN=FLAC"
            "audio/mpeg" -> "DLNA.ORG_PN=MP3"
            "video/mp4" -> "DLNA.ORG_PN=AVC_MP4_MP_SD_AAC_MULT5"
            else -> "DLNA.ORG_CI=0"
        }
        val protocolInfo = "http-get:*:${item.mimeType}:$dlnaProfile;DLNA.ORG_OP=01;DLNA.ORG_FLAGS=01700000000000000000000000000000"
        val upnpClass = if (item.mimeType.startsWith("video/")) "object.item.videoItem" else "object.item.audioItem.musicTrack"
        return buildString {
            append("""<DIDL-Lite xmlns="urn:schemas-upnp-org:metadata-1-0/DIDL-Lite/" xmlns:dc="http://purl.org/dc/elements/1.1/" xmlns:upnp="urn:schemas-upnp-org:metadata-1-0/upnp/">""")
            append("""<item id="${esc(item.id)}" parentID="${esc(item.parentId)}" restricted="1">""")
            append("""<dc:title>${esc(item.title)}</dc:title>""")
            append("""<dc:creator>${esc(item.creator)}</dc:creator>""")
            append("""<upnp:class>$upnpClass</upnp:class>""")
            append("""<res protocolInfo="${esc(protocolInfo)}" size="${item.byteSize}" duration="${esc(item.durationHhMmSs)}">${esc(item.streamUrl)}</res>""")
            append("</item></DIDL-Lite>")
        }
    }

    fun parseDidlLite(xml: String): MediaItemSpec {
        require(!xml.contains("<!DOCTYPE", ignoreCase = true) && !xml.contains("<!ENTITY", ignoreCase = true)) {
            "Refusing DIDL-Lite XML containing DOCTYPE or ENTITY declarations (XXE defense)"
        }
        fun tag(name: String): String =
            unesc(Regex("<$name>([\\s\\S]*?)</$name>").find(xml)?.groupValues?.get(1).orEmpty())
        val itemAttrs = Regex("<item\\s+([^>]+)>").find(xml)?.groupValues?.get(1)
            ?: throw IllegalArgumentException("Missing <item> in DIDL-Lite")
        val resMatch = Regex("<res\\s+([^>]+)>([\\s\\S]*?)</res>").find(xml)
            ?: throw IllegalArgumentException("Missing <res> in DIDL-Lite")
        val resAttrs = resMatch.groupValues[1]
        val proto = unesc(Regex("""protocolInfo="([^"]+)"""").find(resAttrs)?.groupValues?.get(1).orEmpty())
        val mime = proto.split(':').getOrNull(2).orEmpty()
        return MediaItemSpec(
            id = unesc(Regex("""id="([^"]+)"""").find(itemAttrs)?.groupValues?.get(1).orEmpty()),
            parentId = unesc(Regex("""parentID="([^"]+)"""").find(itemAttrs)?.groupValues?.get(1) ?: "0"),
            title = tag("dc:title"),
            creator = tag("dc:creator"),
            mimeType = mime,
            streamUrl = unesc(resMatch.groupValues[2].trim()),
            byteSize = Regex("""size="([0-9]+)"""").find(resAttrs)?.groupValues?.get(1)?.toLongOrNull() ?: 0L,
            durationHhMmSs = unesc(Regex("""duration="([^"]+)"""").find(resAttrs)?.groupValues?.get(1).orEmpty()),
        )
    }

    private fun esc(s: String): String = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")
    private fun unesc(s: String): String = s.replace("&quot;", "\"").replace("&gt;", ">").replace("&lt;", "<").replace("&amp;", "&")
}
