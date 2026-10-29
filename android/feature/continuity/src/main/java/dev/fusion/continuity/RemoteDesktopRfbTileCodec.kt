// SPDX-License-Identifier: Apache-2.0
package dev.fusion.continuity

import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Clean-room `RFC 6143` RFB 3.8 (`VNC`) Dirty-Tile Framebuffer Update & Input Event Wire Codec
 * (`CT-03`: Remote Desktop / VNC / RDP client surface).
 */
object RemoteDesktopRfbTileCodec {
    const val SCOPE =
        "Pure-JVM RFC 6143 RFB 3.8 version handshake, Raw/CopyRect FramebufferUpdate dirty-tile codec, and KeyEvent/PointerEvent wire framer (CT-03)."

    const val RFB_3_8_BANNER = "RFB 003.008\n"
    const val ENCODING_RAW = 0
    const val ENCODING_COPYRECT = 1

    data class CopyRectTile(val dstX: Int, val dstY: Int, val w: Int, val h: Int, val srcX: Int, val srcY: Int)

    fun encodeCopyRectUpdate(tiles: List<CopyRectTile>): ByteArray {
        require(tiles.isNotEmpty() && tiles.size <= 1024) { "invalid tile count" }
        val buf = ByteBuffer.allocate(4 + tiles.size * 16).order(ByteOrder.BIG_ENDIAN)
        buf.put(0.toByte()) // message-type = 0 (FramebufferUpdate)
        buf.put(0.toByte()) // padding
        buf.putShort(tiles.size.toShort())
        for (t in tiles) {
            require(t.w in 1..8192 && t.h in 1..8192 && t.dstX >= 0 && t.dstY >= 0 && t.srcX >= 0 && t.srcY >= 0) {
                "invalid CopyRect geometry $t"
            }
            buf.putShort(t.dstX.toShort())
            buf.putShort(t.dstY.toShort())
            buf.putShort(t.w.toShort())
            buf.putShort(t.h.toShort())
            buf.putInt(ENCODING_COPYRECT)
            buf.putShort(t.srcX.toShort())
            buf.putShort(t.srcY.toShort())
        }
        return buf.array()
    }

    fun decodeCopyRectUpdate(packet: ByteArray): List<CopyRectTile> {
        require(packet.size >= 4 && packet[0] == 0.toByte()) { "expected RFB FramebufferUpdate type 0" }
        val buf = ByteBuffer.wrap(packet).order(ByteOrder.BIG_ENDIAN)
        buf.position(2)
        val count = buf.short.toInt() and 0xFFFF
        require(packet.size == 4 + count * 16) { "RFB CopyRect packet length mismatch" }
        return List(count) {
            val dx = buf.short.toInt() and 0xFFFF
            val dy = buf.short.toInt() and 0xFFFF
            val w = buf.short.toInt() and 0xFFFF
            val h = buf.short.toInt() and 0xFFFF
            val enc = buf.int
            require(enc == ENCODING_COPYRECT) { "unexpected RFB encoding $enc" }
            val sx = buf.short.toInt() and 0xFFFF
            val sy = buf.short.toInt() and 0xFFFF
            CopyRectTile(dx, dy, w, h, sx, sy)
        }
    }

    fun encodePointerEvent(buttonMask: Int, x: Int, y: Int): ByteArray {
        require(buttonMask in 0..255 && x in 0..32767 && y in 0..32767) { "invalid RFB PointerEvent" }
        return ByteBuffer.allocate(6).order(ByteOrder.BIG_ENDIAN)
            .put(5.toByte())
            .put(buttonMask.toByte())
            .putShort(x.toShort())
            .putShort(y.toShort())
            .array()
    }
}
