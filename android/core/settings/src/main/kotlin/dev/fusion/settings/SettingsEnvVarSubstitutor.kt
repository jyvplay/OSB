// SPDX-License-Identifier: Apache-2.0
package dev.fusion.settings

/**
 * Bounded non-recursive `${VAR}` environment placeholder substitutor.
 */
class SettingsEnvVarSubstitutor private constructor() {
    companion object {
        private val VAR_RE = Regex("\\$\\{([A-Za-z_][A-Za-z0-9_]*)\\}")

        fun substitute(template: String, env: Map<String, String>): String =
            VAR_RE.replace(template) { m -> env[m.groupValues[1]] ?: m.value }
    }
}
