// SPDX-License-Identifier: Apache-2.0
package dev.fusion.vault

import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * RFC 6238 TOTP (SHA-1, T0 = 0, 30s step, 6..8 digits) over a
 * caller-supplied instant — no clock reads, so determinism is structural.
 * Secrets are uppercase base32 (padding tolerated at the end only),
 * decoding to 10..64 bytes. Verification scans ±window steps with a
 * plain string compare (timing side-channels outside this kernel).
 * Secret storage and the authenticator UI are outside this kernel.
 */
object TotpCode {
    const val STEP = 30L
    const val MAX_WINDOW = 10
    private const val ALPHA = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567"

    fun code(secretBase32: String, instantSeconds: Long, digits: Int = 6): String {
        require(digits in 6..8) { "digits $digits outside 6..8" }
        require(instantSeconds >= 0) { "negative instant" }
        val key = decode(secretBase32)
        val counter = instantSeconds / STEP
        val msg = ByteArray(8)
        for (i in 0..7) msg[i] = (counter shr (56 - 8 * i)).toByte()
        val mac = Mac.getInstance("HmacSHA1")
        mac.init(SecretKeySpec(key, "HmacSHA1"))
        val h = mac.doFinal(msg)
        val off = h[h.size - 1].toInt() and 0x0F
        val v = ((h[off].toInt() and 0x7F) shl 24) or ((h[off + 1].toInt() and 0xFF) shl 16) or
            ((h[off + 2].toInt() and 0xFF) shl 8) or (h[off + 3].toInt() and 0xFF)
        val mod = when (digits) { 6 -> 1_000_000; 7 -> 10_000_000; else -> 100_000_000 }
        return (v % mod).toString().padStart(digits, '0')
    }

    /** True when [code] matches any step in instant ± window. */
    fun verify(secretBase32: String, code: String, instantSeconds: Long, digits: Int = 6, window: Int = 1): Boolean {
        require(digits in 6..8) { "digits $digits outside 6..8" }
        require(window in 0..MAX_WINDOW) { "window $window outside 0..$MAX_WINDOW" }
        require(code.length == digits && code.all { it.isDigit() }) { "bad code shape" }
        val step = instantSeconds / STEP
        for (d in -window..window) {
            val t = (step + d) * STEP
            if (t < 0) continue
            if (code(secretBase32, t, digits) == code) return true
        }
        return false
    }

    private fun decode(s: String): ByteArray {
        require(s.isNotEmpty() && s.length <= 128) { "secret length ${s.length} outside 1..128" }
        val pad = s.length - s.trimEnd('=').length
        require(pad <= 6) { "over-padded secret" }
        val core = s.dropLast(pad)
        require(core.isNotEmpty() && core.all { it in ALPHA }) { "bad base32" }
        require(setOf(0, 2, 4, 5, 7).contains(core.length % 8)) { "bad base32 length" }
        val bits = StringBuilder(core.length * 5)
        for (c in core) bits.append(ALPHA.indexOf(c).toString(2).padStart(5, '0'))
        val bytes = ByteArray(bits.length / 8) { i -> bits.substring(i * 8, i * 8 + 8).toInt(2).toByte() }
        require(bytes.size in 10..64) { "decoded ${bytes.size} bytes outside 10..64" }
        return bytes
    }
}
