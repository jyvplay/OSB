// SPDX-License-Identifier: Apache-2.0
package dev.fusion.files

/**
 * UTF Byte Order Mark (BOM) detection and stripping.
 *
 * Detects the five standard BOMs: UTF-8 (EF BB BF), UTF-16 LE (FF FE),
 * UTF-16 BE (FE FF), UTF-32 LE (FF FE 00 00), UTF-32 BE (00 00 FE FF).
 * Longer BOMs take precedence (UTF-32 before UTF-16 before UTF-8) so a
 * UTF-32 LE stream whose first four bytes begin FF FE is not misread as
 * UTF-16 LE. Returns the detected charset name and the remaining bytes
 * with the BOM removed; returns "UTF-8" (no BOM) and the input unchanged
 * when no BOM is present.
 *
 * Declared narrowings: only the five canonical BOMs are recognised; no
 * UTF-7, SCSU, BOCU-1 or other signature detection. Input is capped at
 * [MAX_INPUT] bytes; longer inputs are refused. The returned byte array
 * is a copy — the caller's buffer is never aliased.
 */
class BomDetector private constructor() {
    companion object {
        const val MAX_INPUT = 1 shl 20  // 1 MiB
        private val UTF8_BOM = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte())
        private val UTF16_LE_BOM = byteArrayOf(0xFF.toByte(), 0xFE.toByte())
        private val UTF16_BE_BOM = byteArrayOf(0xFE.toByte(), 0xFF.toByte())
        private val UTF32_LE_BOM = byteArrayOf(0xFF.toByte(), 0xFE.toByte(), 0x00.toByte(), 0x00.toByte())
        private val UTF32_BE_BOM = byteArrayOf(0x00.toByte(), 0x00.toByte(), 0xFE.toByte(), 0xFF.toByte())

        data class Result(val charset: String, val withoutBom: ByteArray)

        fun empty(): BomDetector = BomDetector()
    }

    fun detect(input: ByteArray): Result {
        require(input.size <= MAX_INPUT) { "input exceeds $MAX_INPUT bytes" }
        if (input.size >= 4) {
            val head = input.copyOfRange(0, 4)
            if (head.contentEquals(UTF32_LE_BOM)) {
                return Result("UTF-32LE", input.copyOfRange(4, input.size))
            }
            if (head.contentEquals(UTF32_BE_BOM)) {
                return Result("UTF-32BE", input.copyOfRange(4, input.size))
            }
        }
        if (input.size >= 2) {
            val head2 = input.copyOfRange(0, 2)
            if (head2.contentEquals(UTF16_LE_BOM)) {
                return Result("UTF-16LE", input.copyOfRange(2, input.size))
            }
            if (head2.contentEquals(UTF16_BE_BOM)) {
                return Result("UTF-16BE", input.copyOfRange(2, input.size))
            }
        }
        if (input.size >= 3) {
            val head3 = input.copyOfRange(0, 3)
            if (head3.contentEquals(UTF8_BOM)) {
                return Result("UTF-8", input.copyOfRange(3, input.size))
            }
        }
        return Result("UTF-8", input.copyOf())
    }

    fun hasBom(input: ByteArray): Boolean {
        require(input.size <= MAX_INPUT) { "input exceeds $MAX_INPUT bytes" }
        return when {
            input.size >= 4 && input.copyOfRange(0, 4).contentEquals(UTF32_LE_BOM) -> true
            input.size >= 4 && input.copyOfRange(0, 4).contentEquals(UTF32_BE_BOM) -> true
            input.size >= 2 && input.copyOfRange(0, 2).contentEquals(UTF16_LE_BOM) -> true
            input.size >= 2 && input.copyOfRange(0, 2).contentEquals(UTF16_BE_BOM) -> true
            input.size >= 3 && input.copyOfRange(0, 3).contentEquals(UTF8_BOM) -> true
            else -> false
        }
    }

    fun charsetName(input: ByteArray): String = detect(input).charset
}
