// SPDX-License-Identifier: Apache-2.0
package dev.fusion.airplay

/**
 * RTSP status-line parsing plus the RFC 2326 reason-phrase table.
 *
 * Sits next to [RtspMessage] (which frames REQUESTS only — method, URI,
 * CSeq): nothing handles server status lines. [parse] validates the
 * `RTSP/1.0 <3-digit> <reason>` shape and echoes the wire reason;
 * [phrase] returns the recommended phrase for the 44 codes of the
 * RFC 2326 §7.1.1 `Status-Code` grammar, including 304 (present in the
 * grammar, absent from the §7.1.1 usage table) and the RTSP-only
 * 250/451–462/551 range no HTTP table carries. Unknown codes are
 * refused by [phrase] (the caller keeps the echoed wire reason for
 * those); transport and session state are outside.
 *
 * Spelling note: the RFC disagrees with itself. The §7.1.1 grammar says
 * "Request Time-out" (408), "Request-URI Too Large" (414), "Parameter
 * Not Understood" (451), "Conference Not Found" (452), "Header Field
 * Not Valid for Resource" (456), "Gateway Time-out" (504), "RTSP
 * Version not supported" (505) and "Option not supported" (551), while
 * the Table 1 usage list prints "Request Timeout", "Request-URI Too
 * Long", "Invalid parameter", "Illegal Conference Identifier", "Header
 * Field Not Valid", "Gateway Timeout", "RTSP Version Not Supported" and
 * "Option not support". This table follows the grammar verbatim — it is
 * the normative ABNF and the only list that contains 304 — and callers
 * that prefer the peer's wording should display the echoed wire reason.
 *
 * Plain Kotlin, no Android dependency, JVM unit-testable.
 */
object RtspReason {
    /** A parsed RTSP status line. */
    data class Status(val code: Int, val reason: String)

    /** RFC 2326 §7.1.1 reason phrases, grammar spelling, 44 entries. */
    private val PHRASES = linkedMapOf(
        100 to "Continue", 200 to "OK", 201 to "Created", 250 to "Low on Storage Space",
        300 to "Multiple Choices", 301 to "Moved Permanently", 302 to "Moved Temporarily",
        303 to "See Other", 304 to "Not Modified", 305 to "Use Proxy",
        400 to "Bad Request", 401 to "Unauthorized", 402 to "Payment Required",
        403 to "Forbidden", 404 to "Not Found", 405 to "Method Not Allowed",
        406 to "Not Acceptable", 407 to "Proxy Authentication Required",
        408 to "Request Time-out", 410 to "Gone", 411 to "Length Required",
        412 to "Precondition Failed", 413 to "Request Entity Too Large",
        414 to "Request-URI Too Large", 415 to "Unsupported Media Type",
        451 to "Parameter Not Understood", 452 to "Conference Not Found",
        453 to "Not Enough Bandwidth", 454 to "Session Not Found",
        455 to "Method Not Valid in This State", 456 to "Header Field Not Valid for Resource",
        457 to "Invalid Range", 458 to "Parameter Is Read-Only",
        459 to "Aggregate operation not allowed", 460 to "Only aggregate operation allowed",
        461 to "Unsupported transport", 462 to "Destination unreachable",
        500 to "Internal Server Error", 501 to "Not Implemented", 502 to "Bad Gateway",
        503 to "Service Unavailable", 504 to "Gateway Time-out",
        505 to "RTSP Version not supported", 551 to "Option not supported",
    )

    /** The 44 code points this table names, in ascending order. */
    val CODES: Set<Int> = PHRASES.keys.toSortedSet()

    /**
     * Parses one RTSP status line.
     *
     * @throws IllegalArgumentException on a bad version, a non-3-digit
     * or out-of-range code, or a missing/over-long reason.
     */
    fun parse(line: String): Status {
        require(line.length <= 512) { "line over cap" }
        val parts = line.split(" ", limit = 3)
        require(parts.size == 3) { "bad shape" }
        require(parts[0] == "RTSP/1.0") { "bad version" }
        require(parts[1].length == 3 && parts[1].all { it.isDigit() }) { "bad code" }
        val code = parts[1].toInt()
        require(code in 100..699) { "code out of range" }
        require(parts[2].isNotEmpty() && parts[2].length <= 128) { "bad reason" }
        require('\n' !in parts[2] && '\r' !in parts[2]) { "bad reason" }
        return Status(code, parts[2])
    }

    /**
     * Recommended RFC 2326 phrase for [code].
     *
     * @throws IllegalArgumentException on a code the table does not name.
     */
    fun phrase(code: Int): String {
        require(code in PHRASES) { "unknown code" }
        return PHRASES.getValue(code)
    }
}
