// SPDX-License-Identifier: Apache-2.0
package dev.fusion.security

import java.security.SecureRandom

/**
 * Clean-room CSPRNG keypad layout scrambler & constant-time hit-tester (`SecureKeypadScrambler`).
 *
 * Defeats screen-coordinate / touch-offset / smudge / shoulder-surfing keyloggers during PIN and
 * hex-passphrase entry:
 * - Generates a fresh uniform Fisher-Yates permutation per prompt (`generatePinPad` for `0..9` + `BACKSPACE` + `OK`,
 *   `generateHexPad` for `0..9A..F`) from `java.security.SecureRandom` (`nextInt(bound)` rejection-sampled,
 *   never biased `% bound`!).
 * - Applies bounded random origin jitter (`0 .. maxOriginJitterDp`) to the grid origin while guaranteeing
 *   every key cell remains `>= 48.0 dp x 48.0 dp` (WCAG / Android Material 3 minimum touch target!).
 * - Evaluates `(touchXDp, touchYDp)` across ALL cells without early-exit branching (`resolveTouchConstantScan`)
 *   to avoid intra-process timing differences by grid index.
 */
object SecureKeypadScrambler {
    const val SCOPE =
        "CSPRNG Fisher-Yates scrambled PIN (3x4) & Hex (4x4) keypad layout generator (>=48dp AX cells, origin jitter, constant-scan hit-test); hardware TrustZone TUI framebuffer out of scope."

    const val MIN_CELL_DP = 48.0

    data class KeyCell(
        val row: Int,
        val col: Int,
        val label: String,
        val leftDp: Double,
        val topDp: Double,
        val rightDp: Double,
        val bottomDp: Double,
    ) {
        val widthDp: Double get() = rightDp - leftDp
        val heightDp: Double get() = bottomDp - topDp
    }

    data class PadLayout(
        val rows: Int,
        val cols: Int,
        val originShiftXDp: Double,
        val originShiftYDp: Double,
        val cells: List<KeyCell>,
    ) {
        /**
         * Scans all [cells] unconditionally before returning the matched label (if any).
         */
        fun resolveTouchConstantScan(xDp: Double, yDp: Double): String? {
            var matched: String? = null
            for (c in cells) {
                val inside = xDp >= c.leftDp && xDp <= c.rightDp && yDp >= c.topDp && yDp <= c.bottomDp
                if (inside && matched == null) {
                    matched = c.label
                }
            }
            return matched
        }
    }

    fun generatePinPad(
        rng: SecureRandom = SecureRandom(),
        cellWidthDp: Double = 64.0,
        cellHeightDp: Double = 56.0,
        gapDp: Double = 8.0,
        maxOriginJitterDp: Double = 16.0,
    ): PadLayout {
        val digits = MutableList(10) { it.toString() }
        fisherYatesShuffle(digits, rng)
        // Standard 4x3 grid: 9 scrambled digits on rows 0..2, then [BACKSPACE, 10th scrambled digit, OK]
        val labels = listOf(
            digits[0], digits[1], digits[2],
            digits[3], digits[4], digits[5],
            digits[6], digits[7], digits[8],
            "BACKSPACE", digits[9], "OK",
        )
        return buildGrid(4, 3, labels, rng, cellWidthDp, cellHeightDp, gapDp, maxOriginJitterDp)
    }

    fun generateHexPad(
        rng: SecureRandom = SecureRandom(),
        cellWidthDp: Double = 56.0,
        cellHeightDp: Double = 52.0,
        gapDp: Double = 6.0,
        maxOriginJitterDp: Double = 14.0,
    ): PadLayout {
        val symbols = "0123456789ABCDEF".map { it.toString() }.toMutableList()
        fisherYatesShuffle(symbols, rng)
        return buildGrid(4, 4, symbols, rng, cellWidthDp, cellHeightDp, gapDp, maxOriginJitterDp)
    }

    private fun buildGrid(
        rows: Int,
        cols: Int,
        labels: List<String>,
        rng: SecureRandom,
        cellWidthDp: Double,
        cellHeightDp: Double,
        gapDp: Double,
        maxOriginJitterDp: Double,
    ): PadLayout {
        require(cellWidthDp >= MIN_CELL_DP && cellHeightDp >= MIN_CELL_DP) {
            "Keypad touch target (${cellWidthDp}x${cellHeightDp}dp) violates 48dp AX minimum"
        }
        require(gapDp >= 2.0 && maxOriginJitterDp >= 0.0) { "Invalid gap/jitter geometry" }
        val jx = if (maxOriginJitterDp > 0.0) rng.nextDouble() * maxOriginJitterDp else 0.0
        val jy = if (maxOriginJitterDp > 0.0) rng.nextDouble() * maxOriginJitterDp else 0.0
        val cells = ArrayList<KeyCell>(rows * cols)
        for (r in 0 until rows) {
            for (c in 0 until cols) {
                val left = jx + c * (cellWidthDp + gapDp)
                val top = jy + r * (cellHeightDp + gapDp)
                cells.add(
                    KeyCell(
                        row = r,
                        col = c,
                        label = labels[r * cols + c],
                        leftDp = left,
                        topDp = top,
                        rightDp = left + cellWidthDp,
                        bottomDp = top + cellHeightDp,
                    ),
                )
            }
        }
        return PadLayout(rows, cols, jx, jy, cells)
    }

    private fun <T> fisherYatesShuffle(items: MutableList<T>, rng: SecureRandom) {
        for (i in items.lastIndex downTo 1) {
            val j = rng.nextInt(i + 1)
            val tmp = items[i]
            items[i] = items[j]
            items[j] = tmp
        }
    }
}
