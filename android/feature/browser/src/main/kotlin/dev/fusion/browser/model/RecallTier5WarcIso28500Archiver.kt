// SPDX-License-Identifier: Apache-2.0
package dev.fusion.browser.model

import java.io.ByteArrayOutputStream
import java.security.MessageDigest

/**
 * **Recall Method 6 (`TIER_5_WARC_ISO28500_BUNDLE`)** — Clean-room ISO 28500:2017 (`WARC/1.1`)
 * archival container writer & verifier for multi-resource pages and media streams.
 *
 * Emits standard `WARC/1.1` records (`warcinfo`, `response`, `resource`, `metadata`, `revisit`)
 * separated by mandatory `CRLF CRLF` framing, computes `WARC-Block-Digest: sha256:<hex>` and
 * `WARC-Payload-Digest: sha256:<hex>`, redacts sensitive HTTP session headers (`Cookie`,
 * `Set-Cookie`, `Authorization`, `Proxy-Authorization`), and supports deduplicated `revisit`
 * records when identical media hashes reappear across browsed pages.
 */
object RecallTier5WarcIso28500Archiver {
    const val SCOPE =
        "Recall Method 6 ISO 28500:2017 WARC/1.1 archival writer and parser (warcinfo, response, resource, metadata, revisit) with SHA-256 block/payload digests and HTTP auth/cookie redaction."

    private const val CRLF = "\r\n"
    private val SENSITIVE_HTTP_HEADERS = setOf("cookie", "set-cookie", "authorization", "proxy-authorization")

    data class CapturedHttpExchange(
        val targetUri: String,
        val statusLine: String = "HTTP/1.1 200 OK",
        val responseHeaders: Map<String, String>,
        val payloadBytes: ByteArray,
        val identifiedPayloadType: String,
        val captureTimestampIso: String = "2026-10-29T12:00:00Z",
    )

    data class ParsedWarcRecord(
        val version: String,
        val warcType: String,
        val recordId: String,
        val targetUri: String?,
        val warcDate: String,
        val contentType: String,
        val contentLength: Int,
        val blockDigest: String?,
        val payloadDigest: String?,
        val blockBytes: ByteArray,
    )

    fun writeWarcBundle(
        archiveFilename: String,
        warcinfoTimestampIso: String,
        exchanges: List<CapturedHttpExchange>,
    ): ByteArray {
        require(archiveFilename.isNotBlank() && exchanges.isNotEmpty()) { "WARC bundle requires filename and >=1 exchange" }
        val out = ByteArrayOutputStream()
        val infoId = deterministicUrnUuid("warcinfo|$archiveFilename|$warcinfoTimestampIso")
        val infoBlock = (
            "software: FusionRecall/R53 (Clean-Room Apache-2.0)$CRLF" +
                "format: WARC File Format 1.1$CRLF" +
                "conformsTo: http://bibnum.bnf.fr/WARC/WARC_ISO_28500_version1-1_latestdraft.pdf$CRLF" +
                "privacy: Cookie/Set-Cookie/Authorization redacted$CRLF"
            ).toByteArray(Charsets.UTF_8)

        appendRecord(
            out = out,
            warcType = "warcinfo",
            recordId = infoId,
            warcDate = warcinfoTimestampIso,
            contentType = "application/warc-fields",
            extraHeaders = linkedMapOf(
                "WARC-Filename" to sanitizeHeaderValue(archiveFilename),
                "WARC-Block-Digest" to "sha256:${sha256Hex(infoBlock)}",
            ),
            block = infoBlock,
        )

        val seenPayloadHashes = LinkedHashMap<String, Pair<String, String>>() // sha256 -> (recordId, uri)
        for ((idx, ex) in exchanges.withIndex()) {
            val cleanUri = CleanUrlTrackingParamStripper.sanitize(ex.targetUri).sanitizedUrl
            val payloadHash = sha256Hex(ex.payloadBytes)
            val recId = deterministicUrnUuid("rec|$idx|$cleanUri|$payloadHash")
            val prior = seenPayloadHashes[payloadHash]

            val safeHeaders = ex.responseHeaders.entries
                .filter { it.key.lowercase() !in SENSITIVE_HTTP_HEADERS }
                .joinToString(CRLF) { "${sanitizeHeaderName(it.key)}: ${sanitizeHeaderValue(it.value)}" }

            if (prior != null) {
                val revisitHttpBlock = (
                    "${sanitizeHeaderValue(ex.statusLine)}$CRLF" +
                        (if (safeHeaders.isNotEmpty()) "$safeHeaders$CRLF" else "") +
                        "Content-Type: ${sanitizeHeaderValue(ex.identifiedPayloadType)}$CRLF$CRLF"
                    ).toByteArray(Charsets.UTF_8)
                appendRecord(
                    out = out,
                    warcType = "revisit",
                    recordId = recId,
                    warcDate = ex.captureTimestampIso,
                    contentType = "application/http;msgtype=response",
                    extraHeaders = linkedMapOf(
                        "WARC-Target-URI" to cleanUri,
                        "WARC-Warcinfo-ID" to infoId,
                        "WARC-Profile" to "http://netpreserve.org/warc/1.1/revisit/identical-payload-digest",
                        "WARC-Refers-To" to prior.first,
                        "WARC-Refers-To-Target-URI" to prior.second,
                        "WARC-Payload-Digest" to "sha256:$payloadHash",
                        "WARC-Block-Digest" to "sha256:${sha256Hex(revisitHttpBlock)}",
                    ),
                    block = revisitHttpBlock,
                )
            } else {
                seenPayloadHashes[payloadHash] = recId to cleanUri
                val httpHeadBytes = (
                    "${sanitizeHeaderValue(ex.statusLine)}$CRLF" +
                        (if (safeHeaders.isNotEmpty()) "$safeHeaders$CRLF" else "") +
                        "Content-Type: ${sanitizeHeaderValue(ex.identifiedPayloadType)}$CRLF" +
                        "Content-Length: ${ex.payloadBytes.size}$CRLF$CRLF"
                    ).toByteArray(Charsets.UTF_8)
                val fullBlock = ByteArray(httpHeadBytes.size + ex.payloadBytes.size)
                System.arraycopy(httpHeadBytes, 0, fullBlock, 0, httpHeadBytes.size)
                System.arraycopy(ex.payloadBytes, 0, fullBlock, httpHeadBytes.size, ex.payloadBytes.size)

                appendRecord(
                    out = out,
                    warcType = "response",
                    recordId = recId,
                    warcDate = ex.captureTimestampIso,
                    contentType = "application/http;msgtype=response",
                    extraHeaders = linkedMapOf(
                        "WARC-Target-URI" to cleanUri,
                        "WARC-Warcinfo-ID" to infoId,
                        "WARC-Identified-Payload-Type" to sanitizeHeaderValue(ex.identifiedPayloadType),
                        "WARC-Payload-Digest" to "sha256:$payloadHash",
                        "WARC-Block-Digest" to "sha256:${sha256Hex(fullBlock)}",
                    ),
                    block = fullBlock,
                )
            }
        }
        return out.toByteArray()
    }

    fun parseAndVerifyWarcBundle(bytes: ByteArray): List<ParsedWarcRecord> {
        val records = ArrayList<ParsedWarcRecord>()
        var cursor = 0
        while (cursor < bytes.size) {
            val headerEnd = findAsciiSequence(bytes, "\r\n\r\n".toByteArray(), cursor)
            require(headerEnd > cursor) { "Malformed WARC/1.1 header termination at byte $cursor" }
            val headerTxt = String(bytes, cursor, headerEnd - cursor, Charsets.UTF_8)
            val lines = headerTxt.split("\r\n")
            require(lines.firstOrNull() == "WARC/1.1") { "Expected WARC/1.1 version line, got '${lines.firstOrNull()}'" }
            val fields = LinkedHashMap<String, String>()
            for (ln in lines.drop(1)) {
                val colon = ln.indexOf(':')
                require(colon > 0) { "Malformed WARC header line '$ln'" }
                fields[ln.substring(0, colon).trim()] = ln.substring(colon + 1).trim()
            }
            val len = fields["Content-Length"]?.toIntOrNull()
                ?: throw IllegalArgumentException("Missing WARC Content-Length")
            val blockStart = headerEnd + 4
            val blockEnd = blockStart + len
            require(blockEnd + 4 <= bytes.size) { "Truncated WARC record block at $blockStart..$blockEnd" }
            require(
                bytes[blockEnd] == '\r'.code.toByte() &&
                    bytes[blockEnd + 1] == '\n'.code.toByte() &&
                    bytes[blockEnd + 2] == '\r'.code.toByte() &&
                    bytes[blockEnd + 3] == '\n'.code.toByte(),
            ) { "Missing mandatory CRLF CRLF trailer after WARC block at $blockEnd" }

            val block = bytes.copyOfRange(blockStart, blockEnd)
            val declaredBlockDigest = fields["WARC-Block-Digest"]
            if (declaredBlockDigest != null && declaredBlockDigest.startsWith("sha256:")) {
                val actual = "sha256:${sha256Hex(block)}"
                require(actual == declaredBlockDigest) { "WARC block digest mismatch ($actual != $declaredBlockDigest)" }
            }
            records.add(
                ParsedWarcRecord(
                    version = lines.first(),
                    warcType = fields.getValue("WARC-Type"),
                    recordId = fields.getValue("WARC-Record-ID"),
                    targetUri = fields["WARC-Target-URI"],
                    warcDate = fields.getValue("WARC-Date"),
                    contentType = fields.getValue("Content-Type"),
                    contentLength = len,
                    blockDigest = declaredBlockDigest,
                    payloadDigest = fields["WARC-Payload-Digest"],
                    blockBytes = block,
                ),
            )
            cursor = blockEnd + 4
        }
        require(records.isNotEmpty()) { "Empty WARC bundle" }
        return records
    }

    private fun appendRecord(
        out: ByteArrayOutputStream,
        warcType: String,
        recordId: String,
        warcDate: String,
        contentType: String,
        extraHeaders: Map<String, String>,
        block: ByteArray,
    ) {
        val sb = StringBuilder()
        sb.append("WARC/1.1").append(CRLF)
        sb.append("WARC-Type: ").append(warcType).append(CRLF)
        sb.append("WARC-Record-ID: ").append(recordId).append(CRLF)
        sb.append("WARC-Date: ").append(warcDate).append(CRLF)
        for ((k, v) in extraHeaders) sb.append(k).append(": ").append(v).append(CRLF)
        sb.append("Content-Type: ").append(contentType).append(CRLF)
        sb.append("Content-Length: ").append(block.size).append(CRLF)
        sb.append(CRLF)
        out.write(sb.toString().toByteArray(Charsets.UTF_8))
        out.write(block)
        out.write("\r\n\r\n".toByteArray(Charsets.UTF_8))
    }

    private fun findAsciiSequence(haystack: ByteArray, needle: ByteArray, start: Int): Int {
        val last = haystack.size - needle.size
        for (i in start..last) {
            var match = true
            for (j in needle.indices) {
                if (haystack[i + j] != needle[j]) { match = false; break }
            }
            if (match) return i
        }
        return -1
    }

    private fun sanitizeHeaderName(k: String): String = k.filter { it.code in 33..126 && it != ':' }
    private fun sanitizeHeaderValue(v: String): String = v.replace("\r", " ").replace("\n", " ").trim()

    private fun deterministicUrnUuid(seed: String): String {
        val d = MessageDigest.getInstance("SHA-256").digest(seed.toByteArray(Charsets.UTF_8))
        val b = d.copyOf(16)
        b[6] = ((b[6].toInt() and 0x0F) or 0x40).toByte()
        b[8] = ((b[8].toInt() and 0x3F) or 0x80).toByte()
        fun hex(from: Int, to: Int) = (from until to).joinToString("") { "%02x".format(b[it]) }
        return "<urn:uuid:${hex(0, 4)}-${hex(4, 6)}-${hex(6, 8)}-${hex(8, 10)}-${hex(10, 16)}>"
    }

    private fun sha256Hex(b: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(b).joinToString("") { "%02x".format(it) }
}
