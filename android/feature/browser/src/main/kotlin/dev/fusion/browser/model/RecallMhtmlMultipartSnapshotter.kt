// SPDX-License-Identifier: Apache-2.0
package dev.fusion.browser.model

import java.util.Base64

/**
 * Clean-room IETF RFC 2557 `multipart/related` MHTML single-file snapshot encoder & unpacker
 * for **Recall**, bundling sanitized HTML together with viewed images, stylesheets, and audio
 * clips addressed via `Content-Location`.
 */
object RecallMhtmlMultipartSnapshotter {
    const val SCOPE =
        "RFC 2557 multipart/related MHTML single-file page + embedded media snapshot writer and extractor with tracking-stripped Content-Location URIs."

    data class MhtmlPart(
        val contentLocation: String,
        val contentType: String,
        val bodyBytes: ByteArray,
    )

    fun packSnapshot(pageTitle: String, rootUrl: String, parts: List<MhtmlPart>): ByteArray {
        require(parts.isNotEmpty()) { "MHTML snapshot requires at least root part" }
        val boundary = "----=_FusionRecallBoundary_R53_9A4E2F1B"
        val cleanRoot = CleanUrlTrackingParamStripper.sanitize(rootUrl).sanitizedUrl
        val safeTitle = pageTitle.replace("\r", " ").replace("\n", " ").trim().ifEmpty { "Fusion Snapshot" }
        val sb = StringBuilder()
        sb.append("From: <Saved by Fusion Recall>\r\n")
        sb.append("Snapshot-Content-Location: ").append(cleanRoot).append("\r\n")
        sb.append("Subject: ").append(safeTitle).append("\r\n")
        sb.append("MIME-Version: 1.0\r\n")
        sb.append("Content-Type: multipart/related; type=\"text/html\"; boundary=\"").append(boundary).append("\"\r\n\r\n")

        for (p in parts) {
            val loc = CleanUrlTrackingParamStripper.sanitize(p.contentLocation).sanitizedUrl
            sb.append("--").append(boundary).append("\r\n")
            sb.append("Content-Type: ").append(p.contentType.replace("\r", "").replace("\n", "")).append("\r\n")
            sb.append("Content-Transfer-Encoding: base64\r\n")
            sb.append("Content-Location: ").append(loc).append("\r\n\r\n")
            val b64 = Base64.getMimeEncoder(76, "\r\n".toByteArray()).encodeToString(p.bodyBytes)
            sb.append(b64).append("\r\n")
        }
        sb.append("--").append(boundary).append("--\r\n")
        return sb.toString().toByteArray(Charsets.UTF_8)
    }

    fun unpackSnapshot(mhtmlBytes: ByteArray): List<MhtmlPart> {
        val txt = String(mhtmlBytes, Charsets.UTF_8)
        val bMatch = Regex("boundary=\"([^\"]+)\"").find(txt)
            ?: throw IllegalArgumentException("Missing multipart/related boundary in MHTML")
        val boundary = bMatch.groupValues[1]
        val rawSegments = txt.split("--$boundary")
        val out = ArrayList<MhtmlPart>()
        for (seg in rawSegments.drop(1)) {
            val trimmed = seg.trim()
            if (trimmed == "--" || trimmed.isEmpty()) break
            val splitIdx = seg.indexOf("\r\n\r\n")
            require(splitIdx > 0) { "Malformed MIME part header in MHTML" }
            val hdr = seg.substring(0, splitIdx)
            val bodyB64 = seg.substring(splitIdx + 4).trim()
            var ct = "application/octet-stream"
            var loc = ""
            for (ln in hdr.split("\r\n")) {
                val c = ln.indexOf(':')
                if (c > 0) {
                    val k = ln.substring(0, c).trim().lowercase()
                    val v = ln.substring(c + 1).trim()
                    if (k == "content-type") ct = v
                    if (k == "content-location") loc = v
                }
            }
            require(loc.isNotEmpty()) { "Missing Content-Location in MHTML part" }
            out.add(MhtmlPart(loc, ct, Base64.getMimeDecoder().decode(bodyB64)))
        }
        require(out.isNotEmpty()) { "No MIME parts unpacked from MHTML" }
        return out
    }
}
