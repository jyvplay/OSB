// SPDX-License-Identifier: Apache-2.0
package dev.fusion.security

/**
 * Bounded Content-Security-Policy header builder.
 */
class SecurityHeaderCspBuilder private constructor() {
    companion object {
        fun build(directives: Map<String, List<String>>): String =
            directives.entries.joinToString("; ") { (k, vals) ->
                if (vals.isEmpty()) k else "$k ${vals.joinToString(" ")}"
            }
    }
}
