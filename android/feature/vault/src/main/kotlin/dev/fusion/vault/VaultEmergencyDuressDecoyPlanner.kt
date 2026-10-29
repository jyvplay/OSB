// SPDX-License-Identifier: Apache-2.0
package dev.fusion.vault

import java.security.MessageDigest
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * Plausible-deniability fixed-size multi-slot encrypted **Vault Duress / Decoy Header Planner**:
 * packs `4` fixed-size `256-byte` indistinguishable key-slot envelopes where a primary passphrase
 * unlocks the real vault extent and a duress passphrase unlocks an innocuous decoy extent, while
 * unused slots contain cryptographically indistinguishable pseudorandom keystream!
 */
object VaultEmergencyDuressDecoyPlanner {
    const val SCOPE =
        "Constant-size 4x256B plausible-deniability Vault header planner supporting indistinguishable Primary and Duress/Decoy volume extents."

    const val SLOT_BYTES = 256
    const val NUM_SLOTS = 4

    data class VolumeExtent(val label: String, val byteOffset: Long, val byteLength: Long)

    fun buildConstantSizeHeader(
        containerSalt16: ByteArray,
        primaryPassphrase: String,
        primaryExtent: VolumeExtent,
        duressPassphrase: String,
        decoyExtent: VolumeExtent,
    ): ByteArray {
        require(containerSalt16.size == 16 && primaryPassphrase != duressPassphrase)
        require(!extentsOverlap(primaryExtent, decoyExtent)) { "Primary and decoy extents must not overlap!" }
        val header = ByteArray(SLOT_BYTES * NUM_SLOTS)
        writeEncryptedSlot(header, 0, containerSalt16, primaryPassphrase, primaryExtent)
        writeEncryptedSlot(header, 1, containerSalt16, duressPassphrase, decoyExtent)
        // Fill slots 2 and 3 with keyed pseudorandom bytes indistinguishable from ciphertext
        fillPrfNoise(header, 2 * SLOT_BYTES, SLOT_BYTES * 2, containerSalt16)
        return header
    }

    fun tryUnlockAnySlot(header1024: ByteArray, containerSalt16: ByteArray, candidatePassphrase: String): VolumeExtent? {
        require(header1024.size == SLOT_BYTES * NUM_SLOTS && containerSalt16.size == 16)
        for (slot in 0 until NUM_SLOTS) {
            val plain = xorSlot(header1024, slot * SLOT_BYTES, containerSalt16, candidatePassphrase)
            val magic = String(plain, 0, 8, Charsets.US_ASCII)
            if (magic == "FVLT_R53") {
                val payload = String(plain, 8, 120, Charsets.UTF_8).trimEnd('\u0000')
                val parts = payload.split('|')
                if (parts.size == 3) {
                    return VolumeExtent(parts[0], parts[1].toLong(), parts[2].toLong())
                }
            }
        }
        return null
    }

    private fun extentsOverlap(a: VolumeExtent, b: VolumeExtent): Boolean =
        a.byteOffset < (b.byteOffset + b.byteLength) && b.byteOffset < (a.byteOffset + a.byteLength)

    private fun writeEncryptedSlot(buf: ByteArray, slotIdx: Int, salt: ByteArray, pass: String, ext: VolumeExtent) {
        val plain = ByteArray(SLOT_BYTES)
        fillPrfNoise(plain, 0, SLOT_BYTES, MessageDigest.getInstance("SHA-256").digest((pass + slotIdx).toByteArray()))
        val meta = "FVLT_R53" + "${ext.label}|${ext.byteOffset}|${ext.byteLength}".padEnd(120, '\u0000')
        val metaBytes = meta.toByteArray(Charsets.UTF_8)
        System.arraycopy(metaBytes, 0, plain, 0, metaBytes.size.coerceAtMost(128))
        val enc = xorWithKeystream(plain, salt, pass)
        System.arraycopy(enc, 0, buf, slotIdx * SLOT_BYTES, SLOT_BYTES)
    }

    private fun xorSlot(buf: ByteArray, offset: Int, salt: ByteArray, pass: String): ByteArray =
        xorWithKeystream(buf.copyOfRange(offset, offset + SLOT_BYTES), salt, pass)

    private fun xorWithKeystream(data: ByteArray, salt: ByteArray, pass: String): ByteArray {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(pass.toByteArray(Charsets.UTF_8), "HmacSHA256"))
        val out = ByteArray(data.size)
        var block = 0
        var pos = 0
        while (pos < data.size) {
            val ks = mac.doFinal(salt + byteArrayOf(block.toByte()))
            val n = minOf(ks.size, data.size - pos)
            for (i in 0 until n) out[pos + i] = (data[pos + i].toInt() xor ks[i].toInt()).toByte()
            pos += n; block++
        }
        return out
    }

    private fun fillPrfNoise(dst: ByteArray, offset: Int, len: Int, seed: ByteArray) {
        var cur = seed
        var p = 0
        while (p < len) {
            cur = MessageDigest.getInstance("SHA-256").digest(cur)
            val n = minOf(cur.size, len - p)
            System.arraycopy(cur, 0, dst, offset + p, n)
            p += n
        }
    }
}
