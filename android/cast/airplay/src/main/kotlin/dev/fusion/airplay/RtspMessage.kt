// SPDX-License-Identifier: Apache-2.0
package dev.fusion.airplay

/** Parsed RTSP/1.0 request: method, URI, validated headers, exact body. */
data class RtspRequest(val method: String, val uri: String, val cseq: Int, val headers: Map<String, String>, val body: ByteArray) {
    override fun equals(other: Any?): Boolean =
        other is RtspRequest && method == other.method && uri == other.uri && cseq == other.cseq &&
            headers == other.headers && body.contentEquals(other.body)
    override fun hashCode(): Int = 31 * (method + uri + cseq + headers.toString()).hashCode() + body.contentHashCode()
}

/**
 * RTSP/1.0 request framing for the RAOP control channel. Parses the strict
 * subset the shell speaks (fixed method set, CRLF line endings, CSeq
 * required, Content-Length-gated body) and serializes the same shape back.
 * Deliberate refusals: bare-LF line endings (RTSP mandates CRLF), folded
 * header lines (obs-fold), unknown methods, and body-length mismatch. This
 * is framing only: no socket, no timing, no crypto.
 */
object RtspMessage {
    const val MAX_MESSAGE = 65536
    const val MAX_HEADERS = 64
    const val MAX_VALUE = 4096
    const val METHODS = "ANNOUNCE,SETUP,RECORD,PLAY,PAUSE,TEARDOWN,SET_PARAMETER,GET_PARAMETER,OPTIONS,DESCRIBE"
    private val METHOD_SET = METHODS.split(",").toSet()
    private val NAME = Regex("[A-Za-z0-9-]+")

    fun parse(bytes: ByteArray): RtspRequest {
        require(bytes.size in 1..MAX_MESSAGE)
        val text = String(bytes, Charsets.ISO_8859_1)
        for (i in text.indices) {
            if (text[i] == '\n') require(i > 0 && text[i - 1] == '\r') { "bare LF refused" }
        }
        require("\r\n" in text)
        val head = text.substringBefore("\r\n\r\n")
        require(head.length < text.length) { "missing header terminator" }
        val lines = head.split("\r\n")
        require(lines.size in 2..MAX_HEADERS + 1)
        val request = lines[0].split(" ")
        require(request.size == 3 && request[0] in METHOD_SET && request[2] == "RTSP/1.0")
        require(request[1].isNotEmpty() && request[1].length <= 2048 && ' ' !in request[1])
        val headers = LinkedHashMap<String, String>()
        for (line in lines.drop(1)) {
            val colon = line.indexOf(':')
            require(colon > 0)
            val name = line.substring(0, colon)
            require(NAME.matches(name) && name.length <= 64)
            val value = line.substring(colon + 1).trim()
            require(value.length <= MAX_VALUE && value.none { it.isISOControl() })
            require(!headers.containsKey(name)) { "duplicate header" }
            headers[name] = value
        }
        val cseq = headers["CSeq"]?.toIntOrNull()
        require(cseq != null && cseq in 0..999999)
        val declared = headers["Content-Length"]?.toIntOrNull() ?: 0
        require(declared >= 0)
        val bodyStart = head.length + 4
        val body = bytes.copyOfRange(bodyStart, bytes.size)
        require(body.size == declared) { "body length ${body.size} != declared $declared" }
        return RtspRequest(request[0], request[1], cseq, headers.toMap(), body)
    }

    fun serialize(method: String, uri: String, cseq: Int, headers: Map<String, String>, body: ByteArray): ByteArray {
        require(method in METHOD_SET)
        require(uri.isNotEmpty() && uri.length <= 2048 && ' ' !in uri && uri.none { it.isISOControl() })
        require(cseq in 0..999999)
        require(headers.size <= MAX_HEADERS)
        require(!headers.containsKey("CSeq") && !headers.containsKey("Content-Length"))
        for ((k, v) in headers) {
            require(NAME.matches(k) && k.length <= 64)
            require(v.length <= MAX_VALUE && v.all { it.code in 32..126 }) { "values are printable ASCII" }
        }
        require(body.size <= MAX_MESSAGE)
        val out = StringBuilder()
        out.append(method).append(' ').append(uri).append(" RTSP/1.0\r\n")
        out.append("CSeq: ").append(cseq).append("\r\n")
        for ((k, v) in headers) out.append(k).append(": ").append(v).append("\r\n")
        if (body.isNotEmpty()) out.append("Content-Length: ").append(body.size).append("\r\n")
        out.append("\r\n")
        val head = out.toString().toByteArray(Charsets.ISO_8859_1)
        require(head.size + body.size <= MAX_MESSAGE)
        return head + body
    }
}
