// SPDX-License-Identifier: Apache-2.0
package dev.fusion.image

/**
 * Bounded 8-byte PNG signature validator.
 */
class ImagePngSignatureChecker private constructor() {
    companion object {
        private val MAGIC = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)

        fun isPng(header: ByteArray): Boolean {
            if (header.size < 8) return false
            for (i in 0 until 8) {
                if (header[i] != MAGIC[i]) return false
            }
            return true
        }
    }
}
