// SPDX-License-Identifier: Apache-2.0
package dev.fusion.ooxml

/**
 * Bounded Excel worksheet name validator.
 */
class OoxmlSheetNameValidator private constructor() {
    companion object {
        private val FORBIDDEN = setOf('\\', '/', '?', '*', '[', ']', ':')

        fun isValid(name: String): Boolean {
            if (name.isEmpty() || name.length > 31) return false
            if (name.startsWith('\'') || name.endsWith('\'')) return false
            return name.none { it in FORBIDDEN }
        }
    }
}
