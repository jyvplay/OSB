// SPDX-License-Identifier: Apache-2.0
package dev.fusion.browser.model

/**
 * Pure-JVM **W3C HTTP Archive (`HAR 1.2`) Media Provenance Sidecar Exporter** for **Recall**,
 * serializing captured Image, Video, and Music asset timings, clean URLs, MIME types, SHA-256
 * hashes, and recording tier (`T1..T4`) into portable `HAR 1.2` JSON (`"version": "1.2"`) while
 * strictly redacting `Cookie`, `Set-Cookie`, and `Authorization` headers!
 */
object RecallHarArchiveExporter {
    const val SCOPE =
        "Cookie/Auth-redacted W3C HAR 1.2 JSON provenance manifest exporter for Recall archived browser media sessions."

    private val REDACTED_HEADERS = setOf("cookie", "set-cookie", "authorization", "proxy-authorization")

    data class HarMediaCaptureEntry(
        val startedIsoUtc: String,
        val cleanUrl: String,
        val status: Int,
        val mimeType: String,
        val bodyBytes: Long,
        val durationMs: Long,
        val tierRank: Int,
        val sha256Hex: String,
        val responseHeaders: Map<String, String> = emptyMap(),
    )

    fun exportHar12Json(pageTitle: String, entries: List<HarMediaCaptureEntry>): String {
        val sb = StringBuilder()
        sb.append("{\"log\":{\"version\":\"1.2\",\"creator\":{\"name\":\"FusionRecall\",\"version\":\"R52\"},")
        sb.append("\"pages\":[{\"id\":\"page_1\",\"title\":\"").append(esc(pageTitle)).append("\"}],")
        sb.append("\"entries\":[")
        entries.forEachIndexed { idx, e ->
            if (idx > 0) sb.append(',')
            sb.append("{\"pageref\":\"page_1\",\"startedDateTime\":\"").append(esc(e.startedIsoUtc)).append("\",")
            sb.append("\"time\":").append(e.durationMs.coerceAtLeast(0L)).append(',')
            sb.append("\"request\":{\"method\":\"GET\",\"url\":\"").append(esc(e.cleanUrl)).append("\",\"headers\":[]},")
            sb.append("\"response\":{\"status\":").append(e.status).append(",\"headers\":[")
            var firstHdr = true
            for ((k, v) in e.responseHeaders) {
                if (!firstHdr) sb.append(',')
                firstHdr = false
                val safeVal = if (k.lowercase() in REDACTED_HEADERS) "[REDACTED]" else v
                sb.append("{\"name\":\"").append(esc(k)).append("\",\"value\":\"").append(esc(safeVal)).append("\"}")
            }
            sb.append("],\"content\":{\"size\":").append(e.bodyBytes).append(",\"mimeType\":\"").append(esc(e.mimeType))
                .append("\",\"comment\":\"RecallTier=").append(e.tierRank).append(";sha256=").append(esc(e.sha256Hex)).append("\"}}}")
        }
        sb.append("]}}")
        return sb.toString()
    }

    private fun esc(s: String): String =
        s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "")
}
