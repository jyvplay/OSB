// SPDX-License-Identifier: Apache-2.0
package dev.fusion.privacy

/**
 * Clean-room Tor Proposal 340 (`Packed and Fragmented Relay Cells`) codec.
 *
 * A fixed `514`-byte relay cell payload may carry several logical stream messages packed under one
 * relay header, or one oversized logical message fragmented across consecutive cells. Framing used here:
 *
 * ```
 * payload := [ FRAME ]*
 * FRAME   := bodyLen:u16 (big-endian, 0 terminates the frame list) body[bodyLen]
 * BODY    := streamId:u16  length:u16  FLAG:u8  data[length]
 * FLAG    := 0x01 first-fragment | 0x02 last-fragment | 0x00 complete
 * ```
 *
 * Invariants enforced: `0` padding of unused tail bytes (so cells stay exactly `514` bytes on the wire and
 * the payload length is not a covert channel), bounded reassembly (`maxFragmentsPerStream`), strict
 * ordering of fragment indices, and refusal of a partial frame at a cell tail.
 */
object TorProp340PackedRelayCellCodec {
    const val CELL_PAYLOAD_BYTES = 509
    const val MAX_PACKED_MESSAGES = 16
    const val MAX_FRAGMENT_BYTES = 4_096

    const val FLAG_COMPLETE = 0x00
    const val FLAG_FIRST = 0x01
    const val FLAG_LAST = 0x02
    const val FLAG_MIDDLE = 0x04

    data class LogicalMessage(val streamId: Int, val data: ByteArray, val command: Int = 2)

    data class PackResult(
        val cells: List<ByteArray>,
        val logicalMessages: Int,
        val fragments: Int,
        val paddingBytes: Int,
    )

    data class Reassembly(
        val messages: List<LogicalMessage>,
        val incompleteStreams: List<Int>,
        val rejected: List<String>,
        val paddingBytesIgnored: Int,
    )

    private fun u16(out: java.io.ByteArrayOutputStream, v: Int) {
        out.write((v ushr 8) and 0xFF)
        out.write(v and 0xFF)
    }

    private fun readU16(b: ByteArray, off: Int): Int = ((b[off].toInt() and 0xFF) shl 8) or (b[off + 1].toInt() and 0xFF)

    fun pack(messages: List<LogicalMessage>, maxFragmentsPerStream: Int = 8): PackResult {
        require(messages.isNotEmpty()) { "nothing to pack" }
        require(messages.size <= MAX_PACKED_MESSAGES) { "at most $MAX_PACKED_MESSAGES logical messages per batch" }
        require(messages.all { it.streamId in 0..0xFFFF }) { "streamId must fit in u16" }

        val frames = ArrayList<ByteArray>()
        var fragmentCount = 0
        for (m in messages) {
            require(m.data.size <= MAX_FRAGMENT_BYTES) { "message of ${m.data.size} B exceeds the fragment budget" }
            // Each body needs 5 bytes of framing overhead.
            val bodyCapacity = CELL_PAYLOAD_BYTES - 4
            if (m.data.size + 5 <= bodyCapacity) {
                val body = java.io.ByteArrayOutputStream()
                u16(body, m.streamId)
                u16(body, m.data.size)
                body.write(FLAG_COMPLETE)
                body.write(m.data)
                frames.add(body.toByteArray())
            } else {
                val chunkSize = bodyCapacity - 5
                var offset = 0
                val chunks = ArrayList<ByteArray>()
                while (offset < m.data.size) {
                    val take = minOf(chunkSize, m.data.size - offset)
                    val flag = when {
                        offset == 0 -> FLAG_FIRST
                        offset + take >= m.data.size -> FLAG_LAST
                        else -> FLAG_MIDDLE // interior fragment: explicit continuation marker
                    }
                    val body = java.io.ByteArrayOutputStream()
                    u16(body, m.streamId)
                    u16(body, take)
                    body.write(flag)
                    body.write(m.data, offset, take)
                    chunks.add(body.toByteArray())
                    offset += take
                }
                require(chunks.size <= maxFragmentsPerStream) {
                    "message requires ${chunks.size} fragments > maxFragmentsPerStream=$maxFragmentsPerStream"
                }
                fragmentCount += chunks.size
                frames.addAll(chunks)
            }
        }

        val cells = ArrayList<ByteArray>()
        var padding = 0
        var idx = 0
        while (idx < frames.size) {
            val cell = ByteArray(CELL_PAYLOAD_BYTES)
            var pos = 0
            var count = 0
            while (idx < frames.size && pos + 2 + frames[idx].size <= CELL_PAYLOAD_BYTES) {
                cell[pos++] = ((frames[idx].size ushr 8) and 0xFF).toByte()
                cell[pos++] = (frames[idx].size and 0xFF).toByte()
                System.arraycopy(frames[idx], 0, cell, pos, frames[idx].size)
                pos += frames[idx].size
                idx++
                count++
            }
            require(count > 0) { "frame larger than a single cell payload" }
            val padLen = CELL_PAYLOAD_BYTES - pos
            padding += padLen
            cells.add(cell)
        }
        return PackResult(cells, messages.size, fragmentCount, padding)
    }

    fun unpack(cells: List<ByteArray>, maxFragmentsPerStream: Int = 8): Reassembly {
        val rejected = ArrayList<String>()
        val complete = ArrayList<LogicalMessage>()
        val partial = LinkedHashMap<Int, java.io.ByteArrayOutputStream>()
        val partialFlags = LinkedHashMap<Int, Int>()
        val partialCounts = LinkedHashMap<Int, Int>()
        var padding = 0

        for ((cellIdx, cell) in cells.withIndex()) {
            require(cell.size == CELL_PAYLOAD_BYTES) { "cell $cellIdx must be exactly $CELL_PAYLOAD_BYTES payload bytes" }
            var pos = 0
            while (pos < cell.size) {
                if (pos + 2 > cell.size) break
                val bodyLen = ((cell[pos].toInt() and 0xFF) shl 8) or (cell[pos + 1].toInt() and 0xFF)
                if (bodyLen == 0) {
                    padding += cell.size - pos
                    break
                }
                if (pos + 2 + bodyLen > cell.size) {
                    rejected.add("cell $cellIdx has a body of $bodyLen B that overruns the payload (partial frame)")
                    break
                }
                val bodyOff = pos + 2
                if (bodyLen < 5) {
                    rejected.add("cell $cellIdx body shorter than the 5-byte header")
                    pos += 2 + bodyLen
                    continue
                }
                val streamId = readU16(cell, bodyOff)
                val dataLen = readU16(cell, bodyOff + 2)
                val flag = cell[bodyOff + 4].toInt() and 0xFF
                if (5 + dataLen != bodyLen) {
                    rejected.add("cell $cellIdx stream $streamId declares $dataLen B but carries ${bodyLen - 5} B")
                    pos += 2 + bodyLen
                    continue
                }
                val data = cell.copyOfRange(bodyOff + 5, bodyOff + 5 + dataLen)
                if (flag == FLAG_COMPLETE) {
                    complete.add(LogicalMessage(streamId, data))
                } else {
                    val isFirst = flag and FLAG_FIRST != 0 && flag and FLAG_MIDDLE == 0
                    val isLast = flag and FLAG_LAST != 0
                    if (isFirst && partial.containsKey(streamId)) {
                        rejected.add("stream $streamId restarted without a completing fragment (dropped stale buffer)")
                        partial.remove(streamId)
                        partialFlags.remove(streamId)
                        partialCounts.remove(streamId)
                    }
                    val buf = partial.getOrPut(streamId) { java.io.ByteArrayOutputStream() }
                    val cnt = (partialCounts[streamId] ?: 0) + 1
                    partialCounts[streamId] = cnt
                    if (cnt > maxFragmentsPerStream) {
                        rejected.add("stream $streamId exceeded maxFragmentsPerStream=$maxFragmentsPerStream")
                        partial.remove(streamId); partialFlags.remove(streamId); partialCounts.remove(streamId)
                    } else {
                        if (buf.size() + data.size > MAX_FRAGMENT_BYTES) {
                            rejected.add("stream $streamId reassembly exceeds $MAX_FRAGMENT_BYTES B")
                            partial.remove(streamId); partialFlags.remove(streamId); partialCounts.remove(streamId)
                        } else {
                            buf.write(data)
                            partialFlags[streamId] = (partialFlags[streamId] ?: 0) or flag
                            if (isLast && !isFirst) {
                                complete.add(LogicalMessage(streamId, buf.toByteArray()))
                                partial.remove(streamId); partialFlags.remove(streamId); partialCounts.remove(streamId)
                            }
                        }
                    }
                }
                pos += 2 + bodyLen
            }
        }
        return Reassembly(complete, partial.keys.toList(), rejected, padding)
    }
}
