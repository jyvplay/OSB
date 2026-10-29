// SPDX-License-Identifier: Apache-2.0
package dev.fusion.cast.dlna

/**
 * Clean-room UPnP AV **`DIDL-Lite` XML Media Item Serializer & XXE-Safe Parser**.
 */
object DidlLiteMetadataXml {
    const val SCOPE =
        "Clean-room UPnP AV DIDL-Lite XML item serializer, H:MM:SS.mmm duration formatter, and XXE-safe parser."

    data class MediaItem(
        val id: String,
        val title: String,
        val mimeType: String,
        val streamUrl: String,
        val durationMs: Long,
    ) {
        init {
            require(id.isNotBlank() && title.isNotBlank() && mimeType.contains('/')) { "invalid DIDL-Lite item fields" }
            require(streamUrl.startsWith("http://") || streamUrl.startsWith("https://")) { "streamUrl must be HTTP(S)" }
            require(durationMs >= 0L) { "durationMs must be >= 0" }
        }
    }

    fun formatDuration(durationMs: Long): String {
        val hrs = durationMs / 3_600_000L
        val mins = (durationMs % 3_600_000L) / 60_000L
        val secs = (durationMs % 60_000L) / 1_000L
        val ms = durationMs % 1_000L
        return "%d:%02d:%02d.%03d".format(hrs, mins, secs, ms)
    }

    fun toXml(item: MediaItem): String = buildString {
        append("""<DIDL-Lite xmlns="urn:schemas-upnp-org:metadata-1-0/DIDL-Lite/" xmlns:dc="http://purl.org/dc/elements/1.1/" xmlns:upnp="urn:schemas-upnp-org:metadata-1-0/upnp/">""")
        append("""<item id="${esc(item.id)}" parentID="0" restricted="1">""")
        append("""<dc:title>${esc(item.title)}</dc:title>""")
        val upnpClass = if (item.mimeType.startsWith("audio/")) "object.item.audioItem.musicTrack" else "object.item.videoItem"
        append("""<upnp:class>$upnpClass</upnp:class>""")
        append("""<res protocolInfo="http-get:*:${esc(item.mimeType)}:*" duration="${formatDuration(item.durationMs)}">${esc(item.streamUrl)}</res>""")
        append("""</item></DIDL-Lite>""")
    }

    fun parseSingleItem(xml: String): MediaItem {
        require(!xml.contains("<!DOCTYPE", ignoreCase = true) && !xml.contains("<!ENTITY", ignoreCase = true)) {
            "XXE DOCTYPE/ENTITY refused in DIDL-Lite XML"
        }
        val id = Regex("""<item\b[^>]*\bid="([^"]+)"""").find(xml)?.groupValues?.get(1)?.let(::unesc)
            ?: error("missing DIDL item id")
        val title = Regex("""<dc:title>([\s\S]*?)</dc:title>""").find(xml)?.groupValues?.get(1)?.let(::unesc)
            ?: error("missing dc:title")
        val proto = Regex("""protocolInfo="http-get:\*:([^:*]+):""").find(xml)?.groupValues?.get(1)?.let(::unesc)
            ?: error("missing protocolInfo mimeType")
        val durStr = Regex("""duration="(\d+:\d{2}:\d{2}\.\d{3})"""").find(xml)?.groupValues?.get(1) ?: "0:00:00.000"
        val url = Regex("""<res\b[^>]*>([\s\S]*?)</res>""").find(xml)?.groupValues?.get(1)?.trim()?.let(::unesc)
            ?: error("missing <res> URL")
        val dp = durStr.split(':', '.')
        val durMs = ((dp[0].toLong() * 60L + dp[1].toLong()) * 60L + dp[2].toLong()) * 1000L + dp[3].toLong()
        return MediaItem(id, title, proto, url, durMs)
    }

    private fun esc(s: String): String = s
        .replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")

    private fun unesc(s: String): String = s
        .replace("&quot;", "\"").replace("&gt;", ">").replace("&lt;", "<").replace("&amp;", "&")
}
