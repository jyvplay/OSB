// SPDX-License-Identifier: Apache-2.0
package dev.fusion.model

import java.io.ByteArrayOutputStream

/**
 * Forward/Backward-Compatible **Canonical TLV Binary Schema Envelope**.
 *
 * Follows the IETF / RFC critical-bit pattern (`tag bit 0 == 1` => **critical** field that older
 * readers MUST understand or fail closed; `tag bit 0 == 0` => **optional/ignorable** extension
 * field that older readers safely skip while recording skipped tag IDs!).
 */
object VersionedSchemaEnvelope {
    const val SCOPE =
        "Canonical TLV binary schema envelope with monotonic tag ordering and critical (odd tag) vs optional (even tag) forward-compatibility enforcement."

    data class Field(val tag: Int, val value: ByteArray) {
        init {
            require(tag in 1..65_535) { "TLV tag $tag outside 1..65535" }
            require(value.size <= 1_048_576) { "TLV field payload exceeds 1 MiB" }
        }
        val isCritical: Boolean get() = (tag and 1) == 1
    }

    data class DecodedEnvelope(
        val schemaVersion: Int,
        val understoodFields: Map<Int, ByteArray>,
        val skippedOptionalTags: List<Int>,
    )

    fun encode(schemaVersion: Int, fields: List<Field>): ByteArray {
        require(schemaVersion in 1..65_535) { "schemaVersion outside 1..65535" }
        require(fields.map { it.tag }.distinct().size == fields.size) { "duplicate TLV tags forbidden in canonical mode" }
        val sorted = fields.sortedBy { it.tag }
        val out = ByteArrayOutputStream()
        writeU16(out, schemaVersion)
        writeU16(out, sorted.size)
        for (f in sorted) {
            writeU16(out, f.tag)
            writeU32(out, f.value.size)
            out.write(f.value)
        }
        return out.toByteArray()
    }

    fun decode(bytes: ByteArray, knownTags: Set<Int>): DecodedEnvelope {
        require(bytes.size >= 4) { "truncated TLV envelope header" }
        val ver = readU16(bytes, 0)
        val count = readU16(bytes, 2)
        var pos = 4
        var prevTag = 0
        val understood = LinkedHashMap<Int, ByteArray>()
        val skipped = ArrayList<Int>()

        repeat(count) {
            require(pos + 6 <= bytes.size) { "truncated TLV field header at $pos" }
            val tag = readU16(bytes, pos); pos += 2
            require(tag > prevTag) { "non-canonical unsorted or duplicate TLV tag $tag after $prevTag" }
            prevTag = tag
            val len = readU32(bytes, pos); pos += 4
            require(len in 0..(bytes.size - pos)) { "truncated TLV field payload for tag $tag" }
            val slice = bytes.copyOfRange(pos, pos + len)
            pos += len
            if (tag in knownTags) {
                understood[tag] = slice
            } else if ((tag and 1) == 1) {
                throw IllegalArgumentException("unknown CRITICAL TLV tag $tag refused (fail-closed forward guard)")
            } else {
                skipped.add(tag)
            }
        }
        require(pos == bytes.size) { "trailing garbage bytes in TLV envelope" }
        return DecodedEnvelope(ver, understood, skipped)
    }

    private fun writeU16(out: ByteArrayOutputStream, v: Int) {
        out.write((v ushr 8) and 0xFF); out.write(v and 0xFF)
    }

    private fun writeU32(out: ByteArrayOutputStream, v: Int) {
        out.write((v ushr 24) and 0xFF); out.write((v ushr 16) and 0xFF)
        out.write((v ushr 8) and 0xFF); out.write(v and 0xFF)
    }

    private fun readU16(b: ByteArray, o: Int): Int =
        ((b[o].toInt() and 0xFF) shl 8) or (b[o + 1].toInt() and 0xFF)

    private fun readU32(b: ByteArray, o: Int): Int =
        ((b[o].toInt() and 0xFF) shl 24) or ((b[o + 1].toInt() and 0xFF) shl 16) or
            ((b[o + 2].toInt() and 0xFF) shl 8) or (b[o + 3].toInt() and 0xFF)
}
