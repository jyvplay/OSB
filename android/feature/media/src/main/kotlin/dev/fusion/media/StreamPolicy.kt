// SPDX-License-Identifier: Apache-2.0
package dev.fusion.media

import java.util.Collections
import java.util.Locale

/**
 * Explicit rendition choice for HLS variant playlists. Parses EXT-X-STREAM-INF entries
 * (BANDWIDTH required, RESOLUTION/CODECS optional) and requires the USER to pick one —
 * no auto-select function exists by design, so no silent quality/bandwidth decision is
 * made on anyone's behalf. Encrypted variants (EXT-X-KEY) and DRM-bearing CODECS hints
 * are refused with a reason, never downgraded silently. Parser + choice model only;
 * segment fetching/ABR belongs to the player with its own disclosure.
 */
object StreamPolicy {
    const val MAX_BYTES = 256 * 1024
    const val MAX_VARIANTS = 64

    data class Variant(val uri: String, val bandwidthBps: Long, val width: Int?, val height: Int?,
                       val codecs: String?) {
        init {
            require(uri.length in 1..2048 && bandwidthBps in 1..1_000_000_000L)
            require((width == null) == (height == null) && (width == null || (width in 16..7680 && height!! in 16..4320)))
            require(codecs == null || codecs.length <= 256)
        }
        val label: String get() {
            val res = if (width != null) "${width}x$height · " else ""
            val mbps = bandwidthBps / 1_000_000.0
            val rate = if (mbps >= 1.0) String.format(Locale.ROOT, "%.1f Mbps", mbps)
            else String.format(Locale.ROOT, "%d kbps", bandwidthBps / 1000)
            return "$res$rate"
        }
    }

    private fun attr(line: String, name: String): String? {
        val key = "$name="
        val start = line.indexOf(key)
        if (start < 0) return null
        var i = start + key.length
        if (i < line.length && line[i] == '"') {
            val end = line.indexOf('"', i + 1)
            if (end < 0) return null
            return line.substring(i + 1, end)
        }
        val end = line.indexOf(',', i).takeIf { it >= 0 } ?: line.length
        return line.substring(i, end)
    }

    fun parseVariants(text: String): List<Variant> {
        require(text.length <= MAX_BYTES) { "Variant playlist exceeds $MAX_BYTES bytes" }
        val lines = text.lines().map { it.trim() }.filter { it.isNotEmpty() }
        require(lines.isNotEmpty() && lines[0] == "#EXTM3U") { "Not an extended M3U playlist" }
        val variants = mutableListOf<Variant>()
        var i = 1
        while (i < lines.size) {
            val line = lines[i]
            require(line.length <= 2048) { "Line ${i + 1} too long" }
            if (line.startsWith("#EXT-X-KEY"))
                throw IllegalArgumentException("Encrypted variants (EXT-X-KEY) are not handled (line ${i + 1})")
            if (!line.startsWith("#EXT-X-STREAM-INF")) { i++; continue }
            val bandwidth = attr(line, "BANDWIDTH")?.toLongOrNull()
            require(bandwidth != null && bandwidth in 1..1_000_000_000L) { "Variant needs numeric BANDWIDTH (line ${i + 1})" }
            attr(line, "RESOLUTION")?.let {
                require(it.matches(Regex("\\d{2,4}x\\d{2,4}"))) { "Bad RESOLUTION (line ${i + 1})" }
            }
            val codecs = attr(line, "CODECS")?.take(256)
            if (codecs != null) require("drm" !in codecs.lowercase(Locale.ROOT)) { "DRM-bearing variant refused (line ${i + 1})" }
            require(i + 1 < lines.size && !lines[i + 1].startsWith("#")) { "Variant URI missing (line ${i + 1})" }
            val uri = lines[i + 1]
            require(uri.all { it.code in 0x21..0x7e } && '\\' !in uri && ".." !in uri) { "Bad variant URI (line ${i + 2})" }
            require(variants.size < MAX_VARIANTS) { "More than $MAX_VARIANTS variants" }
            val res = attr(line, "RESOLUTION")?.split('x')?.map { it.toInt() }
            variants += Variant(uri, bandwidth, res?.get(0), res?.get(1), codecs)
            i += 2
        }
        require(variants.isNotEmpty()) { "No variants offered" }
        return Collections.unmodifiableList(variants)
    }

    /** The ONLY way to pick a rendition: explicit caller index. No auto-selection exists. */
    fun choose(variants: List<Variant>, index: Int): Variant {
        require(variants.size in 1..MAX_VARIANTS && index in variants.indices) { "Choose an offered variant 0..${variants.size - 1}" }
        return variants[index]
    }
}
