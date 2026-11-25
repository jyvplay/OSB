// SPDX-License-Identifier: Apache-2.0
package dev.fusion.files

/**
 * Bounded conflict-free rename suffix generator (`name (1).ext`).
 */
class FilesConflictRenameSuffix private constructor() {
    companion object {
        const val MAX_ATTEMPTS = 10_000

        fun resolve(baseName: String, ext: String, existing: Set<String>): String {
            val initial = if (ext.isEmpty()) baseName else "$baseName.$ext"
            if (initial !in existing) return initial
            for (i in 1..MAX_ATTEMPTS) {
                val candidate = if (ext.isEmpty()) "$baseName ($i)" else "$baseName ($i).$ext"
                if (candidate !in existing) return candidate
            }
            throw IllegalStateException("too many collisions")
        }
    }
}
