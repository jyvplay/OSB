// SPDX-License-Identifier: Apache-2.0
package dev.fusion.terminal

import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.charset.StandardCharsets

/**
 * Clean-room `RFC 4253` `SSH-2.0` Binary Packet & `SSH_MSG_KEXINIT` Modern-Crypto Negotiator +
 * USB-Serial Line-Discipline Validator (`TD-03`: SSH / SFTP / USB serial).
 */
object SshTransportKexFrameCodec {
    const val SCOPE =
        "Pure-JVM RFC 4253 SSH-2.0 binary packet framer, modern curve25519/chacha20-poly1305 KEX negotiator refusing legacy SHA-1/CBC suites, and USB-serial parameter validator (TD-03)."

    const val SSH_MSG_KEXINIT: Byte = 20

    private val MODERN_KEX = listOf("curve25519-sha256", "curve25519-sha256@libssh.org", "ecdh-sha2-nistp256")
    private val MODERN_CIPHERS = listOf("chacha20-poly1305@openssh.com", "aes256-gcm@openssh.com")
    private val FORBIDDEN_LEGACY = setOf("diffie-hellman-group1-sha1", "diffie-hellman-group14-sha1", "3des-cbc", "aes128-cbc", "ssh-dss", "ssh-rsa")

    data class NegotiatedAlgorithms(val kex: String, val hostKey: String, val cipher: String)
    data class UsbSerialConfig(val baudRate: Int, val dataBits: Int, val stopBits: Int, val parity: String)

    fun frameBinaryPacket(payload: ByteArray, blockSize: Int = 8): ByteArray {
        require(blockSize in setOf(8, 16) && payload.isNotEmpty() && payload.size <= 65_536) { "invalid SSH packet params" }
        val unpadded = 4 + 1 + payload.size
        var padLen = blockSize - (unpadded % blockSize)
        if (padLen < 4) padLen += blockSize
        val pktLen = 1 + payload.size + padLen
        val bos = ByteArrayOutputStream(4 + pktLen)
        bos.write(ByteBuffer.allocate(4).order(ByteOrder.BIG_ENDIAN).putInt(pktLen).array())
        bos.write(padLen)
        bos.write(payload)
        bos.write(ByteArray(padLen))
        return bos.toByteArray()
    }

    fun unframeBinaryPacket(frame: ByteArray): ByteArray {
        require(frame.size >= 12) { "truncated SSH binary packet" }
        val pktLen = ByteBuffer.wrap(frame, 0, 4).order(ByteOrder.BIG_ENDIAN).int
        require(pktLen >= 6 && 4 + pktLen == frame.size) { "SSH packet length mismatch ($pktLen)" }
        val padLen = frame[4].toInt() and 0xFF
        require(padLen >= 4 && 1 + padLen < pktLen) { "invalid SSH padding length $padLen" }
        val payloadLen = pktLen - 1 - padLen
        return frame.copyOfRange(5, 5 + payloadLen)
    }

    fun negotiateModernSuite(
        serverKexCsv: String,
        serverHostKeyCsv: String,
        serverCipherCsv: String,
    ): NegotiatedAlgorithms {
        val sKex = serverKexCsv.split(',').map { it.trim() }
        val sHk = serverHostKeyCsv.split(',').map { it.trim() }
        val sCiph = serverCipherCsv.split(',').map { it.trim() }
        require((sKex + sHk + sCiph).none { it in FORBIDDEN_LEGACY && sKex.size == 1 }) {
            "server offers only forbidden legacy SHA-1/CBC/DSA algorithms"
        }
        val kex = MODERN_KEX.firstOrNull { it in sKex && it !in FORBIDDEN_LEGACY }
            ?: throw IllegalArgumentException("no mutually trusted modern SSH KEX in '$serverKexCsv'")
        val hk = listOf("ssh-ed25519", "ecdsa-sha2-nistp256", "rsa-sha2-512").firstOrNull { it in sHk }
            ?: throw IllegalArgumentException("no mutually trusted modern host-key algorithm in '$serverHostKeyCsv'")
        val ciph = MODERN_CIPHERS.firstOrNull { it in sCiph }
            ?: throw IllegalArgumentException("no AEAD cipher in '$serverCipherCsv'")
        return NegotiatedAlgorithms(kex, hk, ciph)
    }

    fun validateUsbSerial(baudRate: Int, mode8N1: String = "8N1"): UsbSerialConfig {
        require(baudRate in setOf(9600, 19200, 38400, 57600, 115200, 230400, 460800, 921600)) {
            "unsupported baud rate $baudRate"
        }
        require(mode8N1.length == 3) { "expected 3-char frame mode like 8N1" }
        val db = mode8N1[0].digitToIntOrNull() ?: throw IllegalArgumentException("bad data bits")
        val par = mode8N1[1].uppercase()
        val sb = mode8N1[2].digitToIntOrNull() ?: throw IllegalArgumentException("bad stop bits")
        require(db in 7..8 && par in setOf("N", "E", "O") && sb in 1..2) { "invalid serial framing $mode8N1" }
        val banner = "SSH-2.0-FusionShell_R50".toByteArray(StandardCharsets.US_ASCII)
        check(banner.isNotEmpty())
        return UsbSerialConfig(baudRate, db, sb, par)
    }
}
