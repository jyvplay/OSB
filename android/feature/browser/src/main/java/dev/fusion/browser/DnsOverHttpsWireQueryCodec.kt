// SPDX-License-Identifier: Apache-2.0
package dev.fusion.browser

import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.charset.StandardCharsets
import java.util.Base64

/**
 * Clean-room `RFC 8484` DNS-over-HTTPS (`DoH`) Binary Wire Query Codec (`A=1`, `AAAA=28`,
 * `HTTPS=65` SVCB/ECH) with `RFC 8467` EDNS(0) Option-12 `128`-Byte Block Padding to defeat
 * encrypted DNS packet-size traffic analysis!
 */
object DnsOverHttpsWireQueryCodec {
    const val SCOPE =
        "Pure-JVM RFC 8484 DNS-over-HTTPS wire-format query encoder/decoder supporting QTYPE HTTPS(65) ECH/SVCB and RFC 8467 EDNS(0) 128-byte block padding."

    const val QTYPE_A = 1
    const val QTYPE_AAAA = 28
    const val QTYPE_HTTPS_SVCB = 65
    private const val EDNS_OPT_PADDING = 12

    fun encodePaddedDohQuery(qname: String, qtype: Int = QTYPE_HTTPS_SVCB, blockPadBytes: Int = 128): ByteArray {
        val host = qname.trim().trimEnd('.').lowercase()
        require(host.isNotEmpty() && host.length <= 253 && qtype in setOf(QTYPE_A, QTYPE_AAAA, QTYPE_HTTPS_SVCB)) {
            "invalid DoH query ($qname, type=$qtype)"
        }
        require(blockPadBytes in setOf(64, 128, 256, 468)) { "invalid RFC 8467 block size" }

        val bos = ByteArrayOutputStream(blockPadBytes)
        // RFC 8484 §4.1 recommends ID = 0 for HTTP-cached DoH queries; flags = 0x0100 (RD=1), QDCOUNT=1, ARCOUNT=1 (EDNS OPT)
        val hdr = ByteBuffer.allocate(12).order(ByteOrder.BIG_ENDIAN)
            .putShort(0) // ID = 0
            .putShort(0x0100.toShort()) // RD=1
            .putShort(1) // QDCOUNT=1
            .putShort(0) // ANCOUNT=0
            .putShort(0) // NSCOUNT=0
            .putShort(1) // ARCOUNT=1 (OPT pseudo-RR)
        bos.write(hdr.array())

        for (label in host.split('.')) {
            val bytes = label.toByteArray(StandardCharsets.US_ASCII)
            require(bytes.size in 1..63) { "invalid DNS label '$label'" }
            bos.write(bytes.size)
            bos.write(bytes)
        }
        bos.write(0) // root label
        bos.write(ByteBuffer.allocate(4).order(ByteOrder.BIG_ENDIAN).putShort(qtype.toShort()).putShort(1).array())

        // EDNS(0) OPT header (11 bytes) + Option-12 Padding header (4 bytes)
        val basePlusOptHeader = bos.size() + 11 + 4
        val rem = basePlusOptHeader % blockPadBytes
        val padLen = if (rem == 0) 0 else (blockPadBytes - rem)

        val opt = ByteBuffer.allocate(11 + 4 + padLen).order(ByteOrder.BIG_ENDIAN)
        opt.put(0.toByte()) // root NAME
        opt.putShort(41.toShort()) // TYPE = OPT (41)
        opt.putShort(1232.toShort()) // UDP payload size = 1232 (DNS Flag Day 2020)
        opt.putInt(0) // extended RCODE / flags
        opt.putShort((4 + padLen).toShort()) // RDLENGTH
        opt.putShort(EDNS_OPT_PADDING.toShort()) // OPTION-CODE = 12 (Padding)
        opt.putShort(padLen.toShort()) // OPTION-LENGTH
        repeat(padLen) { opt.put(0.toByte()) } // RFC 7830 §3.1: MUST pad with 0x00 bytes
        bos.write(opt.array())
        return bos.toByteArray()
    }

    fun buildDohGetUrl(endpointHttpsUrl: String, wirePacket: ByteArray): String {
        require(endpointHttpsUrl.startsWith("https://")) { "DoH endpoint must use https://" }
        val dnsParam = Base64.getUrlEncoder().withoutPadding().encodeToString(wirePacket)
        return "$endpointHttpsUrl?dns=$dnsParam"
    }

    fun decodeQuestionFromWire(packet: ByteArray): Pair<String, Int> {
        require(packet.size >= 17) { "truncated DNS wire message" }
        var pos = 12
        val labels = ArrayList<String>()
        while (pos < packet.size) {
            val len = packet[pos].toInt() and 0xFF
            pos++
            if (len == 0) break
            require(len in 1..63 && pos + len <= packet.size) { "corrupt QNAME label length" }
            labels.add(String(packet, pos, len, StandardCharsets.US_ASCII))
            pos += len
        }
        require(labels.isNotEmpty() && pos + 4 <= packet.size) { "truncated DNS question section" }
        val qtype = ByteBuffer.wrap(packet, pos, 2).order(ByteOrder.BIG_ENDIAN).short.toInt() and 0xFFFF
        return labels.joinToString(".") to qtype
    }
}
