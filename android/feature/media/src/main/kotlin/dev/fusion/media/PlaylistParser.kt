// SPDX-License-Identifier: Apache-2.0
package dev.fusion.media

import java.util.Collections
import java.util.Locale

/**
 * Bounded local playlist parsing (M3U/M3U8/PLS). Parsing NEVER fetches, extracts, decrypts
 * or contacts a service: entries are validated URI/address strings for the local player or
 * for explicit user-confirmed handoff. Encrypted HLS segments (EXT-X-KEY) and nested remote
 * playlists beyond one level are refused with an explicit reason, not silently skipped.
 *
 * Commercial services (YouTube/Spotify/SoundCloud) keep their own entitlements: this parser
 * does not turn a webpage URL into a playable stream. Service links use validated external
 * handoff ([HandoffDisclosure]), never embedded extraction.
 */
object PlaylistParser {
    const val MAX_BYTES = 256 * 1024
    const val MAX_ENTRIES = 512
    const val MAX_LINE = 2048

    data class Entry(val uri: String, val title: String?, val durationSeconds: Double?) {
        init {
            require(uri.length in 1..MAX_LINE)
            val seconds = durationSeconds
            require(seconds == null || (seconds.isFinite() && seconds >= -1.0))
        }
    }
    data class Playlist(val format: String, val entries: List<Entry>) {
        val uris: List<String> get() = entries.map { it.uri }
    }

    private fun cleanUri(raw: String, line: Int): String {
        val uri = raw.trim()
        require(uri.length in 1..MAX_LINE && uri.all { it.code in 0x21..0x7e } && '\\' !in uri) { "Bad URI at line $line" }
        val scheme = uri.substringBefore(':').lowercase(Locale.ROOT)
        require(scheme in setOf("http", "https", "file") || !uri.contains(':') || uri.startsWith('/')) { "Refused scheme at line $line" }
        require(!uri.contains("..")) { "Refused parent traversal at line $line" }
        return uri
    }

    fun parseM3U(text: String): Playlist {
        require(text.length <= MAX_BYTES) { "Playlist exceeds $MAX_BYTES bytes" }
        val lines = text.lines()
        require(lines.size <= MAX_ENTRIES * 3 + 8) { "Too many lines" }
        val entries = mutableListOf<Entry>()
        var pendingTitle: String? = null
        var pendingDuration: Double? = null
        var extended = false
        lines.forEachIndexed { n, raw ->
            val lineNo = n + 1
            val line = raw.trim()
            if (line.isEmpty()) return@forEachIndexed
            require(line.length <= MAX_LINE) { "Line $lineNo exceeds $MAX_LINE chars" }
            if (line.startsWith("#EXTM3U")) { extended = true; return@forEachIndexed }
            if (line.startsWith("#EXT-X-KEY")) throw IllegalArgumentException("Encrypted HLS (EXT-X-KEY) is not handled at line $lineNo")
            if (line.startsWith("#EXT-X-STREAM-INF") || line.startsWith("#EXT-X-SESSION-DATA"))
                throw IllegalArgumentException("Master/variant playlists need explicit variant choice; refused at line $lineNo")
            if (line.startsWith("#EXTINF:")) {
                val body = line.removePrefix("#EXTINF:")
                val seconds = body.substringBefore(',').toDoubleOrNull()
                require(seconds != null && seconds.isFinite() && seconds >= -1.0) { "Bad EXTINF duration at line $lineNo" }
                pendingDuration = seconds
                pendingTitle = body.substringAfter(',', "").takeIf { it.isNotEmpty() }?.take(256)
                return@forEachIndexed
            }
            if (line.startsWith("#")) return@forEachIndexed
            require(entries.size < MAX_ENTRIES) { "More than $MAX_ENTRIES entries" }
            entries += Entry(cleanUri(line, lineNo), pendingTitle, pendingDuration)
            pendingTitle = null; pendingDuration = null
        }
        require(entries.isNotEmpty()) { "No playable entries" }
        return Playlist(if (extended) "m3u8" else "m3u", Collections.unmodifiableList(entries))
    }

    fun parsePLS(text: String): Playlist {
        require(text.length <= MAX_BYTES) { "Playlist exceeds $MAX_BYTES bytes" }
        val map = mutableMapOf<String, String>()
        var section = false
        text.lines().forEachIndexed { n, raw ->
            val lineNo = n + 1
            val line = raw.trim()
            if (line.isEmpty() || line.startsWith(";")) return@forEachIndexed
            require(line.length <= MAX_LINE) { "Line $lineNo exceeds $MAX_LINE chars" }
            if (line.equals("[playlist]", true)) { section = true; return@forEachIndexed }
            require(section && '=' in line) { "Bad PLS line $lineNo" }
            val key = line.substringBefore('=').trim()
            require(key.length in 1..32 && key.matches(Regex("[A-Za-z0-9]+"))) { "Bad PLS key at line $lineNo" }
            map[key.lowercase(Locale.ROOT)] = line.substringAfter('=').trim().take(MAX_LINE)
        }
        require(section) { "Missing [playlist] section" }
        val count = map["numberofentries"]?.toIntOrNull()
        require(count != null && count in 1..MAX_ENTRIES) { "Bad NumberOfEntries" }
        val entries = (1..count).map { i ->
            val uri = requireNotNull(map["file$i"]) { "Missing File$i" }
            val title = map["title$i"]?.take(256)
            val seconds = map["length$i"]?.toDoubleOrNull()
            if (map.containsKey("length$i")) require(seconds != null && seconds.isFinite() && seconds >= -1.0) { "Bad Length$i" }
            Entry(cleanUri(uri, i), title, seconds)
        }
        return Playlist("pls", Collections.unmodifiableList(entries))
    }
}

/** Explicit, disclosed external handoff for commercial service links. Never embedded playback. */
object HandoffDisclosure {
    fun text(link: ServiceLink): String = buildString {
        append("Open in an external app: ${link.service.title} — ${link.url}. ")
        append("Fusion does not embed ${link.service.title} playback and does not bypass login, Premium, ")
        append("region or DRM requirements. The external app's terms and privacy policy apply; ")
        append("continuing may identify you to ${link.service.title}. ")
        append("Local formats only inside Fusion: picked audio/video files and M3U/M3U8/PLS playlists.")
    }
}
