// SPDX-License-Identifier: Apache-2.0
package dev.fusion.cast.dlna

/** One DIDL-Lite item: identity, UPnP class, title, and first resource. */
data class DidlItem(
    val id: String,
    val parentId: String,
    val restricted: Boolean,
    val cls: String,
    val title: String,
    val protocolInfo: String,
    val mime: String,
    val url: String,
    val size: Long?,
    val durationSec: Double?
)

/**
 * DIDL-Lite item parser for Browse responses. Handles the <item> subset the
 * shell renders (identity, class, title, first <res>); <container> elements
 * are counted but not returned. Text runs through the module's shared XML
 * unescaper so entity handling stays in one place. protocolInfo must be the
 * four-field "http-get:*:mime:*" form; duration is strict H:MM:SS(.frac)?
 */
object DidlLite {
    const val MAX_XML = 65536
    const val MAX_ITEMS = 256

    fun validDuration(s: String): Double? {
        val p = s.split(":")
        if (p.size != 3) return null
        val h = p[0].toIntOrNull() ?: return null
        val m = p[1].toIntOrNull() ?: return null
        val sec = p[2].toDoubleOrNull() ?: return null
        if (h < 0 || m !in 0..59 || sec < 0 || sec >= 60) return null
        return h * 3600.0 + m * 60.0 + sec
    }

    fun parse(xml: String): Pair<List<DidlItem>, Int> {
        require(xml.length <= MAX_XML)
        val items = ArrayList<DidlItem>()
        var containers = 0
        var pos = 0
        while (true) {
            val it = xml.indexOf("<item", pos)
            val ct = xml.indexOf("<container", pos)
            if (it < 0 && ct < 0) break
            if (ct >= 0 && (it < 0 || ct < it)) {
                containers++
                pos = ct + 10
                continue
            }
            require(it >= 0)
            val openEnd = xml.indexOf('>', it)
            require(openEnd > it)
            val open = xml.substring(it, openEnd + 1)
            val close = xml.indexOf("</item>", openEnd)
            require(close > openEnd)
            val body = xml.substring(openEnd + 1, close)
            items.add(parseItem(open, body))
            require(items.size <= MAX_ITEMS)
            pos = close + 7
        }
        return items.toList() to containers
    }

    private fun attr(tag: String, name: String): String? {
        val key = "$name=\""
        val i = tag.indexOf(key)
        if (i < 0) return null
        val j = tag.indexOf('"', i + key.length)
        if (j < 0) return null
        return UpnpParsing.unescapeXml(tag.substring(i + key.length, j))
    }

    private fun text(tag: String, body: String): String? {
        val open = body.indexOf("<$tag")
        if (open < 0) return null
        val end = body.indexOf('>', open)
        if (end < 0) return null
        val closeTag = "</${tag.substringBefore(' ')}>"
        val close = body.indexOf(closeTag, end)
        if (close < 0) return null
        return UpnpParsing.unescapeXml(body.substring(end + 1, close))
    }

    private fun parseItem(open: String, body: String): DidlItem {
        val id = attr(open, "id") ?: throw IllegalArgumentException("item without id")
        require(id.isNotEmpty() && id.length <= 256)
        val parent = attr(open, "parentID") ?: throw IllegalArgumentException("item without parentID")
        val restricted = when (attr(open, "restricted")) {
            "1", "true" -> true
            "0", "false", null -> false
            else -> throw IllegalArgumentException("bad restricted")
        }
        val cls = text("upnp:class", body) ?: throw IllegalArgumentException("item without class")
        require(cls.isNotEmpty() && cls.length <= 256)
        val title = text("dc:title", body) ?: throw IllegalArgumentException("item without title")
        require(title.isNotEmpty() && title.length <= 1024)
        val resOpen = body.indexOf("<res")
        require(resOpen >= 0) { "item without res" }
        val resEnd = body.indexOf('>', resOpen)
        require(resEnd > resOpen)
        val resTag = body.substring(resOpen, resEnd + 1)
        val proto = attr(resTag, "protocolInfo") ?: throw IllegalArgumentException("res without protocolInfo")
        val fields = proto.split(":")
        require(fields.size == 4 && fields[0] == "http-get" && fields[1] == "*" && fields[3] == "*")
        require(fields[2].isNotEmpty() && fields[2].length <= 127)
        val size = attr(resTag, "size")?.let {
            val v = it.toLongOrNull() ?: throw IllegalArgumentException("bad size")
            require(v >= 0); v
        }
        val dur = attr(resTag, "duration")?.let {
            validDuration(it) ?: throw IllegalArgumentException("bad duration")
        }
        val resClose = body.indexOf("</res>", resEnd)
        require(resClose > resEnd)
        val url = UpnpParsing.unescapeXml(body.substring(resEnd + 1, resClose))
        require(url.isNotEmpty() && url.length <= 2048)
        return DidlItem(id, parent, restricted, cls, title, proto, fields[2], url, size, dur)
    }
}
