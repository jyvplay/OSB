// SPDX-License-Identifier: Apache-2.0
package dev.fusion.castv2

/**
 * Pure-JVM **RFC 9110 §14 HTTP `Range` / `Content-Range` Multipart & Resumable Download Planner**
 * for `:core:network` and Recall's Tier-1/Tier-2 segmented media downloader.
 */
object HttpRangeResumePlanner {
    const val SCOPE =
        "IETF RFC 9110 §14 HTTP Range / Content-Range header builder, 206 Partial Content validator, and ETag/If-Range resume verifier."

    data class ContentRangeHeader(
        val firstBytePos: Long,
        val lastBytePos: Long,
        val completeLength: Long?, // null if '*'
    )

    fun buildRangeHeader(startOffset: Long, maxChunkBytes: Long): String {
        require(startOffset >= 0L && maxChunkBytes >= 1L)
        val end = startOffset + maxChunkBytes - 1L
        return "bytes=$startOffset-$end"
    }

    fun parseContentRange(rawHeader: String): ContentRangeHeader {
        val m = Regex("""^bytes\s+(\d+)-(\d+)/(\d+|\*)$""", RegexOption.IGNORE_CASE).matchEntire(rawHeader.trim())
            ?: throw IllegalArgumentException("Invalid RFC 9110 Content-Range header: '$rawHeader'")
        val start = m.groupValues[1].toLong()
        val end = m.groupValues[2].toLong()
        val total = m.groupValues[3].let { if (it == "*") null else it.toLong() }
        require(start <= end && (total == null || end < total)) { "Out-of-bounds Content-Range: '$rawHeader'" }
        return ContentRangeHeader(start, end, total)
    }

    fun canSafelyAppendPartialResponse(
        statusCode: Int,
        expectedOffset: Long,
        originalEtag: String?,
        responseEtag: String?,
        contentRangeRaw: String?,
    ): Boolean {
        if (statusCode != 206 || contentRangeRaw == null) return false
        if (originalEtag != null && responseEtag != null && originalEtag != responseEtag) return false
        val parsed = runCatching { parseContentRange(contentRangeRaw) }.getOrNull() ?: return false
        return parsed.firstBytePos == expectedOffset
    }
}
