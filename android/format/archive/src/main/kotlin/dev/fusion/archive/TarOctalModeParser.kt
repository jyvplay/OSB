// SPDX-License-Identifier: Apache-2.0
package dev.fusion.archive

/**
 * Bounded POSIX tar octal field parser.
 */
class TarOctalModeParser private constructor() {
    companion object {
        const val MAX_FIELD_LEN = 12

        fun parseOctal(field: String): Long {
            require(field.length <= MAX_FIELD_LEN) { "field too long" }
            val trimmed = field.trim { it == ' ' || it == '\u0000' }
            if (trimmed.isEmpty()) return 0L
            var acc = 0L
            for (ch in trimmed) {
                require(ch in '0'..'7') { "invalid octal digit: $ch" }
                acc = (acc shl 3) + (ch - '0')
            }
            return acc
        }
    }
}
