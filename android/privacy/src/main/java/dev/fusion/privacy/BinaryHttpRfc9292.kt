// SPDX-License-Identifier: Apache-2.0
package dev.fusion.privacy

import java.io.ByteArrayOutputStream

/**
 * Clean-room IETF **`RFC 9292` Binary Representation of HTTP Messages (`BHTTP`)** encoder & decoder.
 *
 * Implements known-length request (`Framing Indicator = 0x00`) and known-length response
 * (`Framing Indicator = 0x01`) messages using IETF `RFC 9000 §16` QUIC variable-length integer
 * (`varint`) encoding, header/trailer field-line validation (lowercase token names per RFC 9110),
 * and deterministic trailing zero-byte padding (`RFC 9292 §3.8`) for `RFC 9458` Oblivious HTTP
 * payload size-hiding!
 */
object BinaryHttpRfc9292 {
    const val SCOPE =
        "Clean-room IETF RFC 9292 Binary HTTP (BHTTP) known-length request (0x00) and response (0x01) codec with RFC 9000 QUIC varint framing and trailing zero-byte padding."

    const val FRAMING_KNOWN_REQUEST = 0L
    const val FRAMING_KNOWN_RESPONSE = 1L
    const val MAX_MESSAGE_BYTES = 4 * 1024 * 1024

    data class FieldLine(val name: String, val value: String) {
        init {
            require(name.isNotEmpty() && name.all { it in 'a'..'z' || it in '0'..'9' || it in "-_." }) {
                "BHTTP header field name must be lowercase HTTP token: '$name'"
            }
            require('\r' !in value && '\n' !in value) { "CR/LF forbidden in BHTTP header value" }
        }
    }

    data class KnownRequest(
        val method: String,
        val scheme: String,
        val authority: String,
        val path: String,
        val headers: List<FieldLine> = emptyList(),
        val body: ByteArray = ByteArray(0),
        val trailers: List<FieldLine> = emptyList(),
    )

    data class KnownResponse(
        val statusCode: Int,
        val informationalResponses: List< Pair<Int, List<FieldLine>> > = emptyList(),
        val headers: List<FieldLine> = emptyList(),
        val body: ByteArray = ByteArray(0),
        val trailers: List<FieldLine> = emptyList(),
    ) {
        init {
            require(statusCode in 200..599) { "final status code $statusCode outside 200..599" }
            informationalResponses.forEach { (sc, _) ->
                require(sc in 100..199) { "informational status $sc outside 100..199" }
            }
        }
    }

    /** Encodes an `RFC 9000 §16` variable-length integer (`0..2^62 - 1`) in `1, 2, 4, or 8` bytes. */
    fun encodeVarint(v: Long): ByteArray {
        require(v in 0L..0x3FFF_FFFF_FFFF_FFFFL) { "QUIC varint $v out of 62-bit unsigned range" }
        return when {
            v <= 63L -> byteArrayOf(v.toByte())
            v <= 16_383L -> byteArrayOf(
                (0x40 or ((v ushr 8).toInt() and 0x3F)).toByte(),
                (v and 0xFF).toByte(),
            )
            v <= 1_073_741_823L -> byteArrayOf(
                (0x80 or ((v ushr 24).toInt() and 0x3F)).toByte(),
                ((v ushr 16) and 0xFF).toByte(),
                ((v ushr 8) and 0xFF).toByte(),
                (v and 0xFF).toByte(),
            )
            else -> byteArrayOf(
                (0xC0 or ((v ushr 56).toInt() and 0x3F)).toByte(),
                ((v ushr 48) and 0xFF).toByte(),
                ((v ushr 40) and 0xFF).toByte(),
                ((v ushr 32) and 0xFF).toByte(),
                ((v ushr 24) and 0xFF).toByte(),
                ((v ushr 16) and 0xFF).toByte(),
                ((v ushr 8) and 0xFF).toByte(),
                (v and 0xFF).toByte(),
            )
        }
    }

    /** Decodes an `RFC 9000 §16` variable-length integer at `offset`, returning `(value, nextOffset)`. */
    fun decodeVarint(bytes: ByteArray, offset: Int): Pair<Long, Int> {
        require(offset in bytes.indices) { "varint offset $offset out of bounds (${bytes.size})" }
        val first = bytes[offset].toInt() and 0xFF
        val len = 1 shl (first ushr 6)
        require(offset + len <= bytes.size) { "truncated $len-byte QUIC varint at $offset" }
        var v = (first and 0x3F).toLong()
        for (i in 1 until len) {
            v = (v shl 8) or (bytes[offset + i].toLong() and 0xFFL)
        }
        return v to (offset + len)
    }

    fun encodeRequest(req: KnownRequest, padToMultipleOf: Int = 0): ByteArray {
        require(req.method.isNotEmpty() && req.scheme.isNotEmpty() && req.path.isNotEmpty()) {
            "BHTTP request method, scheme, and path must be non-empty"
        }
        val out = ByteArrayOutputStream()
        out.write(encodeVarint(FRAMING_KNOWN_REQUEST))
        writeLenBytes(out, req.method.toByteArray(Charsets.US_ASCII))
        writeLenBytes(out, req.scheme.toByteArray(Charsets.US_ASCII))
        writeLenBytes(out, req.authority.toByteArray(Charsets.US_ASCII))
        writeLenBytes(out, req.path.toByteArray(Charsets.US_ASCII))
        writeFieldSection(out, req.headers)
        writeLenBytes(out, req.body)
        writeFieldSection(out, req.trailers)
        applyZeroPadding(out, padToMultipleOf)
        return out.toByteArray()
    }

    fun decodeRequest(bytes: ByteArray): KnownRequest {
        require(bytes.isNotEmpty() && bytes.size <= MAX_MESSAGE_BYTES) {
            "BHTTP message size ${bytes.size} outside 1..$MAX_MESSAGE_BYTES"
        }
        var pos: Int
        val (framing, p0) = decodeVarint(bytes, 0); pos = p0
        require(framing == FRAMING_KNOWN_REQUEST) { "expected BHTTP Known-Length Request framing 0, got $framing" }
        val (methodB, p1) = readLenBytes(bytes, pos); pos = p1
        val (schemeB, p2) = readLenBytes(bytes, pos); pos = p2
        val (authB, p3) = readLenBytes(bytes, pos); pos = p3
        val (pathB, p4) = readLenBytes(bytes, pos); pos = p4
        val (headers, p5) = readFieldSection(bytes, pos); pos = p5
        val (body, p6) = if (pos < bytes.size) readLenBytes(bytes, pos) else ByteArray(0) to pos; pos = p6
        val (trailers, p7) = if (pos < bytes.size) readFieldSection(bytes, pos) else emptyList<FieldLine>() to pos; pos = p7
        verifyZeroPadding(bytes, pos)
        return KnownRequest(
            method = methodB.toString(Charsets.US_ASCII),
            scheme = schemeB.toString(Charsets.US_ASCII),
            authority = authB.toString(Charsets.US_ASCII),
            path = pathB.toString(Charsets.US_ASCII),
            headers = headers,
            body = body,
            trailers = trailers,
        )
    }

    fun encodeResponse(resp: KnownResponse, padToMultipleOf: Int = 0): ByteArray {
        val out = ByteArrayOutputStream()
        out.write(encodeVarint(FRAMING_KNOWN_RESPONSE))
        for ((infoCode, infoHeaders) in resp.informationalResponses) {
            out.write(encodeVarint(infoCode.toLong()))
            writeFieldSection(out, infoHeaders)
        }
        out.write(encodeVarint(resp.statusCode.toLong()))
        writeFieldSection(out, resp.headers)
        writeLenBytes(out, resp.body)
        writeFieldSection(out, resp.trailers)
        applyZeroPadding(out, padToMultipleOf)
        return out.toByteArray()
    }

    fun decodeResponse(bytes: ByteArray): KnownResponse {
        require(bytes.isNotEmpty() && bytes.size <= MAX_MESSAGE_BYTES) {
            "BHTTP message size ${bytes.size} outside 1..$MAX_MESSAGE_BYTES"
        }
        var pos: Int
        val (framing, p0) = decodeVarint(bytes, 0); pos = p0
        require(framing == FRAMING_KNOWN_RESPONSE) { "expected BHTTP Known-Length Response framing 1, got $framing" }
        val infoList = ArrayList<Pair<Int, List<FieldLine>>>()
        var status: Int
        while (true) {
            val (code, next) = decodeVarint(bytes, pos); pos = next
            require(code in 100L..599L) { "invalid HTTP status code $code in BHTTP response" }
            if (code in 100L..199L) {
                val (ih, pNext) = readFieldSection(bytes, pos); pos = pNext
                infoList.add(code.toInt() to ih)
            } else {
                status = code.toInt()
                break
            }
        }
        val (headers, p1) = readFieldSection(bytes, pos); pos = p1
        val (body, p2) = if (pos < bytes.size) readLenBytes(bytes, pos) else ByteArray(0) to pos; pos = p2
        val (trailers, p3) = if (pos < bytes.size) readFieldSection(bytes, pos) else emptyList<FieldLine>() to pos; pos = p3
        verifyZeroPadding(bytes, pos)
        return KnownResponse(status, infoList, headers, body, trailers)
    }

    private fun writeLenBytes(out: ByteArrayOutputStream, data: ByteArray) {
        out.write(encodeVarint(data.size.toLong()))
        out.write(data)
    }

    private fun readLenBytes(bytes: ByteArray, offset: Int): Pair<ByteArray, Int> {
        val (lenL, p) = decodeVarint(bytes, offset)
        require(lenL >= 0L && lenL <= MAX_MESSAGE_BYTES && p + lenL.toInt() <= bytes.size) {
            "truncated BHTTP length-prefixed slice ($lenL bytes at $p)"
        }
        val len = lenL.toInt()
        return bytes.copyOfRange(p, p + len) to (p + len)
    }

    private fun writeFieldSection(out: ByteArrayOutputStream, fields: List<FieldLine>) {
        val section = ByteArrayOutputStream()
        for (f in fields) {
            writeLenBytes(section, f.name.toByteArray(Charsets.US_ASCII))
            writeLenBytes(section, f.value.toByteArray(Charsets.UTF_8))
        }
        writeLenBytes(out, section.toByteArray())
    }

    private fun readFieldSection(bytes: ByteArray, offset: Int): Pair<List<FieldLine>, Int> {
        val (secBytes, next) = readLenBytes(bytes, offset)
        val out = ArrayList<FieldLine>()
        var sp = 0
        while (sp < secBytes.size) {
            val (nB, p1) = readLenBytes(secBytes, sp)
            val (vB, p2) = readLenBytes(secBytes, p1)
            out.add(FieldLine(nB.toString(Charsets.US_ASCII), vB.toString(Charsets.UTF_8)))
            sp = p2
        }
        return out to next
    }

    private fun applyZeroPadding(out: ByteArrayOutputStream, multiple: Int) {
        if (multiple > 1) {
            val rem = out.size() % multiple
            if (rem != 0) repeat(multiple - rem) { out.write(0) }
        }
    }

    private fun verifyZeroPadding(bytes: ByteArray, offset: Int) {
        for (i in offset until bytes.size) {
            require(bytes[i] == 0.toByte()) {
                "RFC 9292 §3.8 violation: non-zero padding byte 0x${(bytes[i].toInt() and 0xFF).toString(16)} at $i"
            }
        }
    }
}
