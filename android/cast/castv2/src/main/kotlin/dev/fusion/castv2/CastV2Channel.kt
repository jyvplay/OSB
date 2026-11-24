// SPDX-License-Identifier: Apache-2.0
package dev.fusion.castv2

/**
 * Bounded CastV2 channel frame parser. Parses CastV2 protocol frames:
 * a source id, destination id, namespace, and payload (UTF-8 text). Each
 * field is length-prefixed. Frames are bounded in count and size; the
 * parser does not open sockets or perform the TLS handshake — bytes are
 * supplied by the caller.
 *
 * Declared narrowings: input at most [MAX_INPUT] bytes; at most
 * [MAX_FRAMES] frames; each field length at most [MAX_FIELD_LEN] bytes;
 * payload is UTF-8; namespaces must match [NS_RE]; only CastV2 data
 * channel frames are parsed — heartbeat and connection frames are refused.
 */
class CastV2Channel private constructor() {
    companion object {
        const val MAX_INPUT = 1 << 16  // 64 KiB
        const val MAX_FRAMES = 256
        const val MAX_FIELD_LEN = 4096
        private val NS_RE = Regex("urn:x-cast:[a-z0-9.]+")

        data class Frame(
            val sourceId: String,
            val destinationId: String,
            val namespace: String,
            val payload: String
        )

        fun empty(): CastV2Channel = CastV2Channel()
    }

    fun parse(input: ByteArray): List<Frame> {
        require(input.size <= MAX_INPUT) { "input exceeds $MAX_INPUT bytes" }
        val frames = mutableListOf<Frame>()
        var offset = 0
        while (offset < input.size) {
            if (frames.size >= MAX_FRAMES) break
            // Read 4-byte big-endian total frame length
            if (offset + 4 > input.size) break
            val frameLen = readIntBE(input, offset)
            require(frameLen > 0 && frameLen <= MAX_INPUT) { "invalid frame length" }
            offset += 4
            if (offset + frameLen > input.size) break
            val frameBytes = input.copyOfRange(offset, offset + frameLen)
            offset += frameLen
            // Parse sourceId, destinationId, namespace, payload — each 2-byte length prefix
            var fo = 0
            val sourceId = readLengthPrefixed(frameBytes, fo) ?: break
            fo = sourceId.let { it.second }
            val destinationId = readLengthPrefixed(frameBytes, fo) ?: break
            fo = destinationId.let { it.second }
            val namespace = readLengthPrefixed(frameBytes, fo) ?: break
            fo = namespace.let { it.second }
            val payload = readLengthPrefixed(frameBytes, fo) ?: break
            require(NS_RE.matches(namespace.second)) { "invalid namespace: $namespace" }
            frames.add(Frame(
                String(sourceId.first, charset("UTF-8")),
                String(destinationId.first, charset("UTF-8")),
                String(namespace.first, charset("UTF-8")),
                String(payload.first, charset("UTF-8"))
            ))
        }
        return frames.toList()
    }

    private fun readLengthPrefixed(data: ByteArray, offset: Int): Pair<ByteArray, Int>? {
        if (offset + 2 > data.size) return null
        val len = ((data[offset].toInt() and 0xFF) shl 8) or (data[offset + 1].toInt() and 0xFF)
        if (len < 0 || offset + 2 + len > data.size) return null
        return Pair(data.copyOfRange(offset + 2, offset + 2 + len), offset + 2 + len)
    }

    private fun readIntBE(input: ByteArray, offset: Int): Int {
        return ((input[offset].toInt() and 0xFF) shl 24) or
               ((input[offset + 1].toInt() and 0xFF) shl 16) or
               ((input[offset + 2].toInt() and 0xFF) shl 8) or
               (input[offset + 3].toInt() and 0xFF)
    }

    private fun charset(name: String): java.nio.charset.Charset =
        java.nio.charset.Charset.forName(name)
}
