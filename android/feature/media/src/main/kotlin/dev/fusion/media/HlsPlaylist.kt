// SPDX-License-Identifier: Apache-2.0
package dev.fusion.media

/**
 * Clean-room RFC 8216 HTTP Live Streaming (`#EXTM3U`) Master & Media Playlist Parser + ABR Selector.
 *
 * Parses `#EXT-X-STREAM-INF` multi-bitrate variants and `#EXTINF` segment timelines while refusing
 * `file://`, `javascript:`, or path-traversal (`..`) segment URIs.
 */
object HlsPlaylist {
    const val SCOPE =
        "RFC 8216 #EXTM3U Master & Media playlist parser + bandwidth/resolution ABR variant selector and safe URI validator; byte-range fMP4 init segments out of scope."

    const val MAX_PLAYLIST_CHARS = 262_144

    data class Variant(
        val bandwidthBps: Long,
        val width: Int?,
        val height: Int?,
        val codecs: String?,
        val uri: String,
    )

    data class Segment(
        val sequenceNumber: Long,
        val durationSec: Double,
        val title: String,
        val uri: String,
        val encryptedAes128Uri: String? = null,
    )

    sealed interface Playlist {
        data class Master(val variants: List<Variant>) : Playlist {
            /** Selects highest bitrate variant fitting `<= maxBandwidthBps` (or lowest fallback if all exceed). */
            fun selectVariant(maxBandwidthBps: Long, maxHeightPx: Int = Int.MAX_VALUE): Variant {
                require(maxBandwidthBps > 0L && maxHeightPx > 0) { "invalid ABR constraints" }
                val eligible = variants.filter {
                    it.bandwidthBps <= maxBandwidthBps && (it.height == null || it.height <= maxHeightPx)
                }
                return eligible.maxByOrNull { it.bandwidthBps }
                    ?: variants.minBy { it.bandwidthBps }
            }
        }

        data class Media(
            val targetDurationSec: Int,
            val mediaSequence: Long,
            val isEndList: Boolean,
            val segments: List<Segment>,
        ) : Playlist {
            val totalDurationSec: Double get() = segments.sumOf { it.durationSec }
        }
    }

    fun parse(m3u8Text: String): Playlist {
        require(m3u8Text.length <= MAX_PLAYLIST_CHARS) { "M3U8 exceeds $MAX_PLAYLIST_CHARS chars" }
        val lines = m3u8Text.replace("\r\n", "\n").split('\n').map { it.trim() }.filter { it.isNotEmpty() }
        require(lines.isNotEmpty() && lines.first() == "#EXTM3U") { "playlist must begin with #EXTM3U" }

        val isMaster = lines.any { it.startsWith("#EXT-X-STREAM-INF:") }
        return if (isMaster) parseMaster(lines) else parseMedia(lines)
    }

    private fun parseMaster(lines: List<String>): Playlist.Master {
        val variants = ArrayList<Variant>()
        var i = 1
        while (i < lines.size) {
            val ln = lines[i]
            if (ln.startsWith("#EXT-X-STREAM-INF:")) {
                val attrs = parseAttrList(ln.removePrefix("#EXT-X-STREAM-INF:"))
                val bw = attrs["BANDWIDTH"]?.toLongOrNull()
                    ?: throw IllegalArgumentException("missing BANDWIDTH in #EXT-X-STREAM-INF")
                require(bw > 0L) { "BANDWIDTH must be > 0" }
                val res = attrs["RESOLUTION"]?.split('x')
                val w = res?.getOrNull(0)?.toIntOrNull()
                val h = res?.getOrNull(1)?.toIntOrNull()
                i++
                while (i < lines.size && lines[i].startsWith("#")) i++
                require(i < lines.size) { "missing URI after #EXT-X-STREAM-INF" }
                val uri = validateSafeUri(lines[i])
                variants.add(Variant(bw, w, h, attrs["CODECS"], uri))
            }
            i++
        }
        require(variants.isNotEmpty()) { "master playlist contains 0 variants" }
        return Playlist.Master(variants)
    }

    private fun parseMedia(lines: List<String>): Playlist.Media {
        var targetDur = 10
        var mediaSeq = 0L
        var endList = false
        var activeKeyUri: String? = null
        val segments = ArrayList<Segment>()
        var pendingInf: Pair<Double, String>? = null

        for (ln in lines.drop(1)) {
            when {
                ln.startsWith("#EXT-X-TARGETDURATION:") -> {
                    targetDur = ln.removePrefix("#EXT-X-TARGETDURATION:").trim().toInt()
                    require(targetDur > 0) { "TARGETDURATION must be > 0" }
                }
                ln.startsWith("#EXT-X-MEDIA-SEQUENCE:") -> {
                    mediaSeq = ln.removePrefix("#EXT-X-MEDIA-SEQUENCE:").trim().toLong()
                    require(mediaSeq >= 0L) { "MEDIA-SEQUENCE must be >= 0" }
                }
                ln.startsWith("#EXT-X-KEY:") -> {
                    val attrs = parseAttrList(ln.removePrefix("#EXT-X-KEY:"))
                    val method = attrs["METHOD"] ?: throw IllegalArgumentException("missing METHOD in #EXT-X-KEY")
                    activeKeyUri = when (method) {
                        "NONE" -> null
                        "AES-128" -> validateSafeUri(attrs["URI"] ?: throw IllegalArgumentException("AES-128 missing URI"))
                        else -> throw IllegalArgumentException("unsupported HLS encryption METHOD '$method'")
                    }
                }
                ln.startsWith("#EXTINF:") -> {
                    val body = ln.removePrefix("#EXTINF:")
                    val comma = body.indexOf(',')
                    val durStr = if (comma >= 0) body.substring(0, comma) else body
                    val title = if (comma >= 0) body.substring(comma + 1).trim() else ""
                    val dur = durStr.trim().toDouble()
                    require(dur > 0.0 && dur.isFinite()) { "invalid #EXTINF duration $dur" }
                    pendingInf = dur to title
                }
                ln == "#EXT-X-ENDLIST" -> endList = true
                !ln.startsWith("#") -> {
                    val inf = pendingInf ?: throw IllegalArgumentException("segment URI '$ln' missing preceding #EXTINF")
                    pendingInf = null
                    val uri = validateSafeUri(ln)
                    segments.add(Segment(mediaSeq + segments.size, inf.first, inf.second, uri, activeKeyUri))
                }
            }
        }
        return Playlist.Media(targetDur, mediaSeq, endList, segments)
    }

    private fun validateSafeUri(uri: String): String {
        val u = uri.trim()
        require(u.isNotEmpty() && !u.contains("..")) { "unsafe traversal or empty HLS URI '$uri'" }
        val lower = u.lowercase()
        require(
            !lower.startsWith("file:") && !lower.startsWith("javascript:") && !lower.startsWith("data:"),
        ) { "forbidden scheme in HLS URI '$uri'" }
        return u
    }

    private fun parseAttrList(raw: String): Map<String, String> {
        val out = LinkedHashMap<String, String>()
        var i = 0
        while (i < raw.length) {
            while (i < raw.length && (raw[i] == ',' || raw[i].isWhitespace())) i++
            if (i >= raw.length) break
            val eq = raw.indexOf('=', i)
            require(eq > i) { "malformed HLS attribute list '$raw'" }
            val key = raw.substring(i, eq).trim()
            var p = eq + 1
            val value: String
            if (p < raw.length && raw[p] == '"') {
                val end = raw.indexOf('"', p + 1)
                require(end > p) { "unclosed quote in HLS attribute '$key'" }
                value = raw.substring(p + 1, end)
                p = end + 1
            } else {
                val end = raw.indexOf(',', p).let { if (it < 0) raw.length else it }
                value = raw.substring(p, end).trim()
                p = end
            }
            out[key] = value
            i = p
        }
        return out
    }
}
