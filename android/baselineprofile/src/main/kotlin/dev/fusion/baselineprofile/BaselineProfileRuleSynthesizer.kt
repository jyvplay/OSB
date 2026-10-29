// SPDX-License-Identifier: Apache-2.0
package dev.fusion.baselineprofile

/**
 * Clean-room Android ART `baseline-prof.txt` HRF (Human-Readable Format) Rule Synthesizer,
 * Flag Merger (`H` hot, `S` startup, `P` post-startup), and JVM Descriptor Validator.
 *
 * Merges duplicate method rules by unioning their `H`/`S`/`P` execution flags (`HS + SP -> HSP`)
 * and emits canonical sorted `baseline-prof.txt` lines ready for `profgen` / `:baselineprofile`.
 */
object BaselineProfileRuleSynthesizer {
    private val VALID_CLASS_DESCRIPTOR = Regex("""^L[A-Za-z0-9_/$]+;$|^L[A-Za-z0-9_/$*]+;?$""")

    data class ProfileRule(
        val hot: Boolean,
        val startup: Boolean,
        val postStartup: Boolean,
        val targetDescriptor: String, // e.g. "Ldev/fusion/shell/MainActivity;->onCreate(Landroid/os/Bundle;)V" or class "Ldev/fusion/ooxml/FormulaEval;"
    ) {
        val isMethodRule: Boolean get() = "->" in targetDescriptor

        init {
            val clsPart = targetDescriptor.substringBefore("->")
            require(VALID_CLASS_DESCRIPTOR.matches(clsPart)) {
                "invalid ART class descriptor '$clsPart' in '$targetDescriptor'"
            }
            if (isMethodRule) {
                require(hot || startup || postStartup) {
                    "ART method rule '$targetDescriptor' requires at least one of H, S, P flags"
                }
            }
        }

        fun formatHrfLine(): String {
            if (!isMethodRule) return targetDescriptor
            val flags = buildString {
                if (hot) append('H')
                if (startup) append('S')
                if (postStartup) append('P')
            }
            return "$flags$targetDescriptor"
        }
    }

    fun mergeAndFormatProfile(rules: List<ProfileRule>): String {
        val merged = LinkedHashMap<String, ProfileRule>()
        for (r in rules) {
            val prev = merged[r.targetDescriptor]
            merged[r.targetDescriptor] = if (prev == null) {
                r
            } else {
                ProfileRule(
                    hot = prev.hot || r.hot,
                    startup = prev.startup || r.startup,
                    postStartup = prev.postStartup || r.postStartup,
                    targetDescriptor = r.targetDescriptor,
                )
            }
        }
        return merged.values
            .sortedBy { it.targetDescriptor }
            .joinToString("\n") { it.formatHrfLine() } + "\n"
    }
}
