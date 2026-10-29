// SPDX-License-Identifier: Apache-2.0
package dev.fusion.beam

/**
 * Clean-room RFC 9110 §14 HTTP single-part `Range` & `Content-Range` resumable LAN file-transfer codec (`ContentRangeHttp`).
 */
object ContentRangeHttp {
    const val SCOPE =
        "RFC 9110 §14 HTTP byte-range parser & 206 Partial Content / 416 Range Not Satisfiable negotiater (bytes=start-end, open start-, suffix -N); multipart/byteranges out of scope."

    sealed interface Resolution {
        data class Partial206(val startInclusive: Long, val endInclusive: Long, val totalLength: Long) : Resolution {
            val contentLength: Long get() = endInclusive - startInclusive + 1L
            val contentRangeHeader: String get() = "bytes $startInclusive-$endInclusive/$totalLength"
        }

        data class Unsatisfiable416(val totalLength: Long) : Resolution {
            val contentRangeHeader: String get() = "bytes */$totalLength"
        }
    }

    fun resolveByteRange(rangeHeader: String, totalLength: Long): Resolution {
        require(totalLength >= 0L) { "Negative resource length: $totalLength" }
        val trimmed = rangeHeader.trim()
        require(trimmed.startsWith("bytes=")) { "Unsupported Range unit (expected bytes=): $rangeHeader" }
        val spec = trimmed.substring(6).trim()
        require(!spec.contains(',')) { "Multipart byte ranges refused; issue single resumable Range" }
        val dash = spec.indexOf('-')
        require(dash >= 0) { "Malformed Range spec: $rangeHeader" }
        if (totalLength == 0L) return Resolution.Unsatisfiable416(0L)

        val lhs = spec.substring(0, dash).trim()
        val rhs = spec.substring(dash + 1).trim()
        return if (lhs.isEmpty()) {
            // Suffix range: bytes=-N (last N bytes)
            val suffixLen = rhs.toLongOrNull() ?: throw IllegalArgumentException("Invalid suffix length: $rhs")
            if (suffixLen <= 0L) Resolution.Unsatisfiable416(totalLength)
            else {
                val start = (totalLength - suffixLen).coerceAtLeast(0L)
                Resolution.Partial206(start, totalLength - 1L, totalLength)
            }
        } else {
            val start = lhs.toLongOrNull() ?: throw IllegalArgumentException("Invalid range start: $lhs")
            val end = if (rhs.isEmpty()) totalLength - 1L else {
                rhs.toLongOrNull() ?: throw IllegalArgumentException("Invalid range end: $rhs")
            }
            if (start < 0L || start >= totalLength || end < start) {
                Resolution.Unsatisfiable416(totalLength)
            } else {
                Resolution.Partial206(start, end.coerceAtMost(totalLength - 1L), totalLength)
            }
        }
    }
}
