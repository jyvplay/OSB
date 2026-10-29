// SPDX-License-Identifier: Apache-2.0
package dev.fusion.browser.model

import java.net.URI
import java.util.Base64

/**
 * **Recall Method 1 (Easy / Fast / Direct):** Automatic DOM subresource, `srcset`, inline SVG,
 * `data:` URI, and RFC 9110 HTTP `206 Partial Content` byte-range harvester for images, videos,
 * and music viewed in the browser.
 */
object RecallTier1DirectAssetHarvester {
    const val SCOPE =
        "Recall Method 1 direct browser asset harvester: HTML/CSS img/srcset/picture/video/audio/source/svg/data-URI extractor, RFC 9110 HTTP 206 Partial Content range assembler with gap rejection, and binary magic-byte container classifier."

    enum class DetectedMediaKind { IMAGE, VIDEO, MUSIC, UNKNOWN }

    data class DiscoveredMediaCandidate(
        val resolvedUrl: String,
        val mediaKind: DetectedMediaKind,
        val sourceTag: String,
        val inlineBytes: ByteArray? = null,
        val widthDescriptor: Int = 0,
    )

    data class HttpRangeChunk(
        val contentRangeHeader: String, // e.g. "bytes 0-1023/2048"
        val payload: ByteArray,
    )

    sealed interface RangeAssemblyResult {
        data class Complete(
            val bytes: ByteArray,
            val detectedMime: String,
            val canonicalExtension: String,
            val mediaKind: DetectedMediaKind,
        ) : RangeAssemblyResult

        data class IncompleteOrInvalid(val reason: String) : RangeAssemblyResult
    }

    fun discoverViewedMedia(pageUrl: String, html: String): List<DiscoveredMediaCandidate> {
        val baseUri = runCatching { URI(pageUrl) }.getOrNull()
        val out = LinkedHashMap<String, DiscoveredMediaCandidate>()

        fun resolveUrl(raw: String): String? {
            val t = raw.trim().removeSurrounding("\"").removeSurrounding("'")
            if (t.isBlank() || t.startsWith("javascript:", ignoreCase = true) || t.startsWith("vbscript:", ignoreCase = true)) {
                return null
            }
            if (t.startsWith("data:", ignoreCase = true)) return t
            return runCatching {
                if (baseUri != null) baseUri.resolve(t).toString() else t
            }.getOrNull()
        }

        // 1. Best candidate from srcset="..."
        for (m in Regex("""\bsrcset\s*=\s*["']([^"']+)["']""", RegexOption.IGNORE_CASE).findAll(html)) {
            var bestUrl: String? = null
            var bestScore = -1
            for (cand in m.groupValues[1].split(',')) {
                val parts = cand.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
                if (parts.isEmpty()) continue
                val u = resolveUrl(parts[0]) ?: continue
                val desc = parts.getOrNull(1)?.lowercase() ?: "1x"
                val score = when {
                    desc.endsWith("w") -> desc.removeSuffix("w").toIntOrNull() ?: 100
                    desc.endsWith("x") -> ((desc.removeSuffix("x").toDoubleOrNull() ?: 1.0) * 1000.0).toInt()
                    else -> 100
                }
                if (score > bestScore) {
                    bestScore = score
                    bestUrl = u
                }
            }
            if (bestUrl != null) {
                out[bestUrl] = DiscoveredMediaCandidate(bestUrl, DetectedMediaKind.IMAGE, "srcset", decodeDataUriIfPresent(bestUrl), bestScore)
            }
        }

        // 2. Direct <img src>, <video src>, <audio src>, <source src>
        val tagPattern = Regex("""<(img|video|audio|source)\b([^>]*)>""", RegexOption.IGNORE_CASE)
        for (m in tagPattern.findAll(html)) {
            val tag = m.groupValues[1].lowercase()
            val attrs = m.groupValues[2]
            val src = Regex("""\bsrc\s*=\s*["']([^"']+)["']""", RegexOption.IGNORE_CASE).find(attrs)?.groupValues?.get(1)
            val typeAttr = Regex("""\btype\s*=\s*["']([^"']+)["']""", RegexOption.IGNORE_CASE).find(attrs)?.groupValues?.get(1)?.lowercase() ?: ""
            if (src != null) {
                val resolved = resolveUrl(src)
                if (resolved != null && resolved !in out) {
                    val kind = when {
                        tag == "img" -> DetectedMediaKind.IMAGE
                        tag == "video" -> DetectedMediaKind.VIDEO
                        tag == "audio" -> DetectedMediaKind.MUSIC
                        typeAttr.startsWith("audio/") -> DetectedMediaKind.MUSIC
                        typeAttr.startsWith("video/") -> DetectedMediaKind.VIDEO
                        typeAttr.startsWith("image/") -> DetectedMediaKind.IMAGE
                        else -> inferKindFromUrl(resolved)
                    }
                    out[resolved] = DiscoveredMediaCandidate(resolved, kind, tag, decodeDataUriIfPresent(resolved))
                }
            }
            if (tag == "video") {
                val poster = Regex("""\bposter\s*=\s*["']([^"']+)["']""", RegexOption.IGNORE_CASE).find(attrs)?.groupValues?.get(1)
                if (poster != null) {
                    val resolvedPoster = resolveUrl(poster)
                    if (resolvedPoster != null && resolvedPoster !in out) {
                        out[resolvedPoster] = DiscoveredMediaCandidate(resolvedPoster, DetectedMediaKind.IMAGE, "video-poster", decodeDataUriIfPresent(resolvedPoster))
                    }
                }
            }
        }

        // 3. CSS background-image: url(...)
        for (m in Regex("""background(?:-image)?\s*:[^;>]*url\(\s*['"]?([^'")]+)['"]?\s*\)""", RegexOption.IGNORE_CASE).findAll(html)) {
            val resolved = resolveUrl(m.groupValues[1]) ?: continue
            if (resolved !in out) {
                out[resolved] = DiscoveredMediaCandidate(resolved, DetectedMediaKind.IMAGE, "css-background", decodeDataUriIfPresent(resolved))
            }
        }

        // 4. Inline <svg ...>...</svg>
        var svgIdx = 0
        for (m in Regex("""<svg\b[\s\S]*?</svg>""", RegexOption.IGNORE_CASE).findAll(html)) {
            val rawSvg = m.value
            if (!rawSvg.contains("<script", ignoreCase = true)) {
                svgIdx++
                val pseudoUrl = "${pageUrl.substringBefore('#')}#inline-svg-$svgIdx.svg"
                out[pseudoUrl] = DiscoveredMediaCandidate(pseudoUrl, DetectedMediaKind.IMAGE, "inline-svg", rawSvg.toByteArray(Charsets.UTF_8))
            }
        }

        return out.values.toList()
    }

    fun decodeDataUriIfPresent(uri: String): ByteArray? {
        if (!uri.startsWith("data:", ignoreCase = true)) return null
        val comma = uri.indexOf(',')
        if (comma < 5) return null
        val header = uri.substring(5, comma).lowercase()
        val body = uri.substring(comma + 1)
        return if (header.endsWith(";base64")) {
            runCatching { Base64.getDecoder().decode(body.trim()) }.getOrNull()
        } else {
            java.net.URLDecoder.decode(body, Charsets.UTF_8).toByteArray(Charsets.UTF_8)
        }
    }

    fun assembleHttp206Ranges(chunks: List<HttpRangeChunk>): RangeAssemblyResult {
        if (chunks.isEmpty()) return RangeAssemblyResult.IncompleteOrInvalid("EMPTY_CHUNK_LIST")
        val rangeRegex = Regex("""^bytes\s+(\d+)-(\d+)/(\d+)$""", RegexOption.IGNORE_CASE)
        var declaredTotal = -1
        val parsed = ArrayList<Triple<Int, Int, ByteArray>>(chunks.size)
        for (c in chunks) {
            val m = rangeRegex.matchEntire(c.contentRangeHeader.trim())
                ?: return RangeAssemblyResult.IncompleteOrInvalid("MALFORMED_CONTENT_RANGE:${c.contentRangeHeader}")
            val start = m.groupValues[1].toIntOrNull() ?: return RangeAssemblyResult.IncompleteOrInvalid("RANGE_START_OVERFLOW")
            val end = m.groupValues[2].toIntOrNull() ?: return RangeAssemblyResult.IncompleteOrInvalid("RANGE_END_OVERFLOW")
            val total = m.groupValues[3].toIntOrNull() ?: return RangeAssemblyResult.IncompleteOrInvalid("RANGE_TOTAL_OVERFLOW")
            if (total <= 0 || total > 256 * 1024 * 1024 || start > end || end >= total) {
                return RangeAssemblyResult.IncompleteOrInvalid("INVALID_BOUNDS:$start-$end/$total")
            }
            if (c.payload.size != (end - start + 1)) {
                return RangeAssemblyResult.IncompleteOrInvalid("PAYLOAD_LENGTH_MISMATCH:expected=${end - start + 1},actual=${c.payload.size}")
            }
            if (declaredTotal == -1) declaredTotal = total
            else if (declaredTotal != total) return RangeAssemblyResult.IncompleteOrInvalid("INCONSISTENT_TOTAL:$declaredTotal!=$total")
            parsed.add(Triple(start, end, c.payload))
        }

        parsed.sortBy { it.first }
        if (parsed.first().first != 0) {
            return RangeAssemblyResult.IncompleteOrInvalid("MISSING_LEADING_BYTES:starts_at=${parsed.first().first}")
        }
        val out = ByteArray(declaredTotal)
        var coveredThrough = -1
        for ((start, end, buf) in parsed) {
            if (start > coveredThrough + 1) {
                return RangeAssemblyResult.IncompleteOrInvalid("MISSING_BYTE_RANGE_GAP:${coveredThrough + 1}..${start - 1}")
            }
            // Verify overlapping bytes match identically rather than silently corrupting
            if (start <= coveredThrough) {
                val overlapEnd = minOf(end, coveredThrough)
                for (pos in start..overlapEnd) {
                    if (out[pos] != buf[pos - start]) {
                        return RangeAssemblyResult.IncompleteOrInvalid("CONFLICTING_OVERLAP_AT_BYTE:$pos")
                    }
                }
            }
            System.arraycopy(buf, 0, out, start, buf.size)
            if (end > coveredThrough) coveredThrough = end
        }
        if (coveredThrough != declaredTotal - 1) {
            return RangeAssemblyResult.IncompleteOrInvalid("TRUNCATED_TAIL:covered=${coveredThrough + 1}/$declaredTotal")
        }
        val (mime, ext, kind) = sniffContainerMagic(out)
        return RangeAssemblyResult.Complete(out, mime, ext, kind)
    }

    fun sniffContainerMagic(bytes: ByteArray): Triple<String, String, DetectedMediaKind> {
        fun startsWith(vararg sig: Int): Boolean =
            bytes.size >= sig.size && sig.indices.all { (bytes[it].toInt() and 0xFF) == sig[it] }

        return when {
            startsWith(0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A) -> Triple("image/png", "png", DetectedMediaKind.IMAGE)
            startsWith(0xFF, 0xD8, 0xFF) -> Triple("image/jpeg", "jpg", DetectedMediaKind.IMAGE)
            startsWith(0x47, 0x49, 0x46, 0x38) -> Triple("image/gif", "gif", DetectedMediaKind.IMAGE)
            bytes.size >= 12 && bytes.copyOfRange(0, 4).contentEquals("RIFF".toByteArray()) &&
                bytes.copyOfRange(8, 12).contentEquals("WEBP".toByteArray()) -> Triple("image/webp", "webp", DetectedMediaKind.IMAGE)
            bytes.size >= 12 && bytes.copyOfRange(0, 4).contentEquals("RIFF".toByteArray()) &&
                bytes.copyOfRange(8, 12).contentEquals("WAVE".toByteArray()) -> Triple("audio/wav", "wav", DetectedMediaKind.MUSIC)
            startsWith(0x66, 0x4C, 0x61, 0x43) -> Triple("audio/flac", "flac", DetectedMediaKind.MUSIC) // fLaC
            startsWith(0x49, 0x44, 0x33) || startsWith(0xFF, 0xFB) || startsWith(0xFF, 0xF3) -> Triple("audio/mpeg", "mp3", DetectedMediaKind.MUSIC)
            startsWith(0x4F, 0x67, 0x67, 0x53) -> Triple("audio/ogg", "ogg", DetectedMediaKind.MUSIC) // OggS
            startsWith(0x1A, 0x45, 0xDF, 0xA3) -> Triple("video/webm", "webm", DetectedMediaKind.VIDEO)
            bytes.size >= 12 && bytes.copyOfRange(4, 8).contentEquals("ftyp".toByteArray()) -> {
                val brand = bytes.copyOfRange(8, 12).toString(Charsets.US_ASCII)
                when {
                    brand == "avif" || brand == "avis" -> Triple("image/avif", "avif", DetectedMediaKind.IMAGE)
                    brand == "M4A " -> Triple("audio/mp4", "m4a", DetectedMediaKind.MUSIC)
                    else -> Triple("video/mp4", "mp4", DetectedMediaKind.VIDEO)
                }
            }
            bytes.take(256).toByteArray().toString(Charsets.UTF_8).trimStart().startsWith("<svg", ignoreCase = true) ->
                Triple("image/svg+xml", "svg", DetectedMediaKind.IMAGE)
            else -> Triple("application/octet-stream", "bin", DetectedMediaKind.UNKNOWN)
        }
    }

    private fun inferKindFromUrl(url: String): DetectedMediaKind {
        val path = url.substringBefore('?').substringBefore('#').lowercase()
        return when {
            path.endsWith(".png") || path.endsWith(".jpg") || path.endsWith(".jpeg") ||
                path.endsWith(".webp") || path.endsWith(".gif") || path.endsWith(".svg") || path.endsWith(".avif") -> DetectedMediaKind.IMAGE
            path.endsWith(".mp3") || path.endsWith(".flac") || path.endsWith(".wav") ||
                path.endsWith(".ogg") || path.endsWith(".opus") || path.endsWith(".m4a") -> DetectedMediaKind.MUSIC
            path.endsWith(".mp4") || path.endsWith(".webm") || path.endsWith(".mkv") || path.endsWith(".mov") -> DetectedMediaKind.VIDEO
            else -> DetectedMediaKind.UNKNOWN
        }
    }
}
