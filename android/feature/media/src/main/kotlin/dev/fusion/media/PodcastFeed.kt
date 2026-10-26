// SPDX-License-Identifier: Apache-2.0
package dev.fusion.media

import java.util.Collections
import java.util.Locale

/**
 * Bounded RSS 2.0 / Atom 1.0 enclosure reader for open podcast and media feeds.
 * Parsing NEVER fetches: entries are validated direct media URLs the user may load into
 * the local/stream player explicitly. Enclosures with non-allowlisted MIME types are
 * skipped and COUNTED (never silent). Per-item title-or-description is required by the
 * feed specs; document order is preserved and no date normalization is claimed.
 */
object PodcastFeed {
    const val MAX_BYTES = 512 * 1024
    const val MAX_ITEMS = 256
    const val MAX_TEXT = 512
    val AUDIO_VIDEO = setOf("audio/mpeg", "audio/m4a", "audio/mp4", "audio/ogg", "audio/opus",
        "audio/wav", "audio/x-wav", "video/mp4", "video/m4v")

    data class Episode(val title: String, val mediaUrl: String, val mime: String, val lengthBytes: Long?)
    data class Feed(val format: String, val title: String, val episodes: List<Episode>, val skippedNonMedia: Int)

    private fun attr(tag: String, name: String): String? {
        val key = "$name=\""
        val start = tag.indexOf(key)
        if (start < 0) return null
        val end = tag.indexOf('"', start + key.length)
        if (end < 0 || end - start > 2048) return null
        return tag.substring(start + key.length, end)
    }

    private fun textOf(block: String, tag: String): String? {
        val open = "<$tag>"
        val start = block.indexOf(open)
        if (start < 0) return null
        val end = block.indexOf("</$tag>", start)
        if (end < 0 || end - start > MAX_TEXT + 64) return null
        return block.substring(start + open.length, end).trim().take(MAX_TEXT).takeIf { it.isNotEmpty() }
    }

    private fun cleanUrl(raw: String): String {
        val url = raw.trim()
        require(url.length in 1..2048 && url.all { it.code in 0x21..0x7e } && '\\' !in url) { "Bad enclosure URL" }
        val scheme = url.substringBefore(':').lowercase(Locale.ROOT)
        require(scheme == "http" || scheme == "https") { "Only http/https enclosure URLs" }
        require(".." !in url) { "Refused parent traversal" }
        return url
    }

    fun parse(text: String): Feed {
        require(text.length <= MAX_BYTES) { "Feed exceeds $MAX_BYTES bytes" }
        return if ("<rss" in text && "<channel" in text) parseRss(text) else parseAtom(text)
    }

    private fun blocks(text: String, tag: String): List<String> {
        val out = mutableListOf<String>()
        var cursor = 0
        while (true) {
            val open = text.indexOf("<$tag", cursor)
            if (open < 0) break
            val close = text.indexOf("</$tag>", open)
            require(close > open && close - open <= 65536) { "Bad <$tag> block" }
            out += text.substring(open, close)
            cursor = close + tag.length + 3
            require(out.size <= MAX_ITEMS + 4) { "More than $MAX_ITEMS feed blocks" }
        }
        return out
    }

    private fun parseRss(text: String): Feed {
        val channel = text.substringAfter("<channel>", "").substringBeforeLast("</channel>", "")
        require(channel.isNotEmpty()) { "RSS channel missing" }
        val title = textOf(channel, "title") ?: throw IllegalArgumentException("RSS channel title missing")
        val episodes = mutableListOf<Episode>()
        var skipped = 0
        for (item in blocks(channel, "item")) {
            require(episodes.size < MAX_ITEMS) { "More than $MAX_ITEMS items" }
            val encStart = item.indexOf("<enclosure")
            if (encStart < 0) { skipped++; continue }
            val encEnd = item.indexOf('>', encStart)
            require(encEnd > encStart && encEnd - encStart <= 2048) { "Bad enclosure tag" }
            val tag = item.substring(encStart, encEnd + 1)
            val mime = (attr(tag, "type") ?: "").lowercase(Locale.ROOT)
            if (mime !in AUDIO_VIDEO) { skipped++; continue }
            val url = cleanUrl(attr(tag, "url") ?: throw IllegalArgumentException("Enclosure URL missing"))
            val length = attr(tag, "length")?.toLongOrNull()?.takeIf { it >= 0 }
            val name = textOf(item, "title") ?: textOf(item, "description")?.take(MAX_TEXT)
                ?: throw IllegalArgumentException("Item needs title or description")
            episodes += Episode(name, url, mime, length)
        }
        require(episodes.isNotEmpty()) { "No playable episodes" }
        return Feed("rss", title, Collections.unmodifiableList(episodes), skipped)
    }

    private fun parseAtom(text: String): Feed {
        require("<feed" in text) { "Not an RSS or Atom feed" }
        val title = textOf(text, "title") ?: throw IllegalArgumentException("Atom feed title missing")
        val episodes = mutableListOf<Episode>()
        var skipped = 0
        for (entry in blocks(text, "entry")) {
            require(episodes.size < MAX_ITEMS) { "More than $MAX_ITEMS entries" }
            var found: Episode? = null
            var cursor = 0
            while (true) {
                val link = entry.indexOf("<link", cursor)
                if (link < 0) break
                val end = entry.indexOf('>', link)
                require(end > link && end - link <= 2048) { "Bad link tag" }
                val tag = entry.substring(link, end + 1)
                cursor = end + 1
                if ((attr(tag, "rel") ?: "alternate").lowercase(Locale.ROOT) != "enclosure") continue
                val mime = (attr(tag, "type") ?: "").lowercase(Locale.ROOT)
                if (mime !in AUDIO_VIDEO) { skipped++; continue }
                val href = attr(tag, "href") ?: throw IllegalArgumentException("Enclosure href missing")
                val length = attr(tag, "length")?.toLongOrNull()?.takeIf { it >= 0 }
                val name = textOf(entry, "title") ?: textOf(entry, "summary")?.take(MAX_TEXT)
                    ?: throw IllegalArgumentException("Entry needs title or summary")
                found = Episode(name, cleanUrl(href), mime, length)
                break
            }
            if (found == null) skipped++ else episodes += found
        }
        require(episodes.isNotEmpty()) { "No playable episodes" }
        return Feed("atom", title, Collections.unmodifiableList(episodes), skipped)
    }
}
