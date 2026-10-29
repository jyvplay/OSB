// SPDX-License-Identifier: Apache-2.0
package dev.fusion.shell

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.zip.CRC32

/**
 * Deterministic Multi-Desktop Window/Dock Session Binary Snapshot Codec with IEEE 802.3 CRC-32
 * integrity verification and ephemeral-privacy zero-persistence guard.
 */
object WorkspaceSessionSnapshot {
    const val SCOPE =
        "Deterministic multi-desktop window/split/dock session binary snapshot codec with IEEE CRC-32 integrity & ephemeral-mode persistence refusal."

    private val MAGIC = byteArrayOf(0x46, 0x57, 0x53, 0x38) // "FWS8"
    const val SCHEMA_VERSION = 1

    data class WindowPlacement(
        val id: String,
        val workspaceIndex: Int,
        val xDp: Int,
        val yDp: Int,
        val widthDp: Int,
        val heightDp: Int,
        val focused: Boolean,
    ) {
        init {
            require(id.isNotBlank() && id.length <= 128) { "invalid window id" }
            require(workspaceIndex in 0..15) { "workspaceIndex outside 0..15" }
            require(widthDp >= 120 && heightDp >= 120) { "window dimensions below minimum 120dp" }
        }
    }

    data class SessionState(
        val activeWorkspace: Int,
        val windows: List<WindowPlacement>,
    )

    fun encode(state: SessionState, ephemeralPrivacyMode: Boolean = false): ByteArray {
        require(!ephemeralPrivacyMode) { "Tails-light ephemeral mode forbids persisting workspace session snapshots" }
        require(state.activeWorkspace in 0..15 && state.windows.size <= 64) { "invalid workspace session bounds" }
        val payloadBuf = ArrayList<ByteArray>()
        var bodyBytes = 8 // version(2) + activeWs(2) + count(4)
        for (w in state.windows) {
            val idBytes = w.id.toByteArray(Charsets.UTF_8)
            val rec = ByteBuffer.allocate(2 + idBytes.size + 21).order(ByteOrder.BIG_ENDIAN)
            rec.putShort(idBytes.size.toShort())
            rec.put(idBytes)
            rec.putInt(w.workspaceIndex)
            rec.putInt(w.xDp)
            rec.putInt(w.yDp)
            rec.putInt(w.widthDp)
            rec.putInt(w.heightDp)
            rec.put(if (w.focused) 1.toByte() else 0.toByte())
            val arr = rec.array()
            payloadBuf.add(arr)
            bodyBytes += arr.size
        }
        val body = ByteBuffer.allocate(bodyBytes).order(ByteOrder.BIG_ENDIAN)
        body.putShort(SCHEMA_VERSION.toShort())
        body.putShort(state.activeWorkspace.toShort())
        body.putInt(state.windows.size)
        payloadBuf.forEach { body.put(it) }
        val bodyArr = body.array()
        val crc = CRC32().apply { update(bodyArr) }.value.toInt()

        val full = ByteBuffer.allocate(4 + bodyArr.size + 4).order(ByteOrder.BIG_ENDIAN)
        full.put(MAGIC)
        full.put(bodyArr)
        full.putInt(crc)
        return full.array()
    }

    fun decode(bytes: ByteArray): SessionState {
        require(bytes.size >= 16) { "truncated WorkspaceSessionSnapshot" }
        for (i in 0..3) require(bytes[i] == MAGIC[i]) { "invalid WorkspaceSessionSnapshot magic" }
        val bodyArr = bytes.copyOfRange(4, bytes.size - 4)
        val expectedCrc = ByteBuffer.wrap(bytes, bytes.size - 4, 4).order(ByteOrder.BIG_ENDIAN).int
        val actualCrc = CRC32().apply { update(bodyArr) }.value.toInt()
        require(expectedCrc == actualCrc) { "WorkspaceSessionSnapshot CRC-32 mismatch (corrupt snapshot)" }

        val buf = ByteBuffer.wrap(bodyArr).order(ByteOrder.BIG_ENDIAN)
        val ver = buf.short.toInt()
        require(ver == SCHEMA_VERSION) { "unsupported WorkspaceSessionSnapshot version $ver" }
        val activeWs = buf.short.toInt()
        val count = buf.int
        require(count in 0..64) { "invalid window count $count" }
        val windows = List(count) {
            val idLen = buf.short.toInt() and 0xFFFF
            require(idLen <= buf.remaining()) { "truncated window id" }
            val idB = ByteArray(idLen)
            buf.get(idB)
            WindowPlacement(
                id = idB.toString(Charsets.UTF_8),
                workspaceIndex = buf.int,
                xDp = buf.int,
                yDp = buf.int,
                widthDp = buf.int,
                heightDp = buf.int,
                focused = buf.get().toInt() != 0,
            )
        }
        require(!buf.hasRemaining()) { "trailing bytes in WorkspaceSessionSnapshot" }
        return SessionState(activeWs, windows)
    }
}
