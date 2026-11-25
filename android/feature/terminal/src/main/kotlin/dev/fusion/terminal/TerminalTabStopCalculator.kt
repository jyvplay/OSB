// SPDX-License-Identifier: Apache-2.0
package dev.fusion.terminal

/**
 * Bounded terminal next-tab-stop calculator.
 */
class TerminalTabStopCalculator private constructor() {
    companion object {
        fun nextTabStop(col: Int, tabWidth: Int = 8): Int {
            require(col >= 0 && tabWidth in 1..32) { "invalid tab stop args" }
            return ((col / tabWidth) + 1) * tabWidth
        }
    }
}
