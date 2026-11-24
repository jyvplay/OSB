// SPDX-License-Identifier: Apache-2.0
package dev.fusion.files

/**
 * Bounded file extension sanitizer. Strips leading dots, lowercases ASCII,
 * and rejects path separators, NUL bytes, and overly long extensions.
 */
class FileExtensionSanitizer private constructor() {
    companion object {
        const val MAX_EXT_LEN = 32
        private val EXT_RE = Regex("^[a-z0-9][a-z0-9._-]{0,31}$")

        fun sanitize(rawExt: String): String {
            val trimmed = rawExt.trim().trimStart('.').lowercase()
            require(trimmed.isNotEmpty() && trimmed.length <= MAX_EXT_LEN) {
                "extension length out of bounds"
            }
            require(!trimmed.contains('/') && !trimmed.contains('\\') && !trimmed.contains('\u0000')) {
                "extension contains forbidden characters"
            }
            require(EXT_RE.matches(trimmed)) { "invalid extension: $rawExt" }
            return trimmed
        }
    }
}
