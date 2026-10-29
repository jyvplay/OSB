// SPDX-License-Identifier: Apache-2.0
package dev.fusion.baselineprofile

/**
 * Clean-room Android **ART `baseline-prof.txt` (`HSPL` Human-Readable Profile Rule) Parser,
 * Validator, Flag Merger, and Canonical Deterministic Sorter**.
 *
 * Parses and normalizes ART profile rules:
 * - Class rules: `Ldev/fusion/shell/MainActivity;`
 * - Method rules: `[H][S][P]Ldev/fusion/ooxml/FormulaEval;->evaluateSheet(Ljava/util/Map;)...`
 * Merges duplicate method entries by OR-ing their `H` (Hot), `S` (Startup), and `P` (Post-startup)
 * execution flags and emits deterministic lexicographically sorted `baseline-prof.txt` output!
 */
object ArtProfileRuleCodec {
    const val SCOPE =
        "Clean-room Android ART baseline-prof.txt HSPL rule parser, JNI descriptor validator, duplicate-flag union merger, and deterministic sorter."

    sealed interface ProfileRule {
        val classDescriptor: String

        data class ClassRule(override val classDescriptor: String) : ProfileRule
        data class MethodRule(
            val hot: Boolean,
            val startup: Boolean,
            val postStartup: Boolean,
            override val classDescriptor: String,
            val methodName: String,
            val signature: String,
        ) : ProfileRule {
            init {
                require(hot || startup || postStartup) { "ART method rule requires at least one of H, S, P flags" }
            }
        }
    }

    fun parseLine(rawLine: String): ProfileRule? {
        val trimmed = rawLine.substringBefore('#').trim()
        if (trimmed.isEmpty()) return null
        val arrowIdx = trimmed.indexOf("->")
        if (arrowIdx < 0) {
            validateClassDescriptor(trimmed)
            return ProfileRule.ClassRule(trimmed)
        }
        val lIdx = trimmed.indexOf('L')
        require(lIdx in 0 until arrowIdx) { "missing class 'L...;' descriptor in ART method rule '$trimmed'" }
        val flagsPart = trimmed.substring(0, lIdx)
        require(flagsPart.isNotEmpty() && flagsPart.all { it in "HSP" }) {
            "invalid ART method flags '$flagsPart' (expected subset of HSP)"
        }
        val cls = trimmed.substring(lIdx, arrowIdx)
        validateClassDescriptor(cls)
        val rhs = trimmed.substring(arrowIdx + 2)
        val paren = rhs.indexOf('(')
        require(paren > 0 && ')' in rhs) { "invalid JNI method signature in '$trimmed'" }
        val mName = rhs.substring(0, paren)
        val sig = rhs.substring(paren)
        return ProfileRule.MethodRule(
            hot = 'H' in flagsPart,
            startup = 'S' in flagsPart,
            postStartup = 'P' in flagsPart,
            classDescriptor = cls,
            methodName = mName,
            signature = sig,
        )
    }

    fun canonicalizeProfile(text: String): List<String> {
        val classes = sortedSetOf<String>()
        val methods = LinkedHashMap<Triple<String, String, String>, Triple<Boolean, Boolean, Boolean>>()

        for (line in text.lines()) {
            when (val r = parseLine(line)) {
                null -> {}
                is ProfileRule.ClassRule -> classes.add(r.classDescriptor)
                is ProfileRule.MethodRule -> {
                    val key = Triple(r.classDescriptor, r.methodName, r.signature)
                    val prev = methods[key]
                    methods[key] = if (prev == null) {
                        Triple(r.hot, r.startup, r.postStartup)
                    } else {
                        Triple(prev.first || r.hot, prev.second || r.startup, prev.third || r.postStartup)
                    }
                }
            }
        }
        val formattedMethods = methods.entries.map { (k, f) ->
            val flags = buildString {
                if (f.first) append('H')
                if (f.second) append('S')
                if (f.third) append('P')
            }
            "$flags${k.first}->${k.second}${k.third}"
        }.sorted()
        return (classes.toList() + formattedMethods).sorted()
    }

    private fun validateClassDescriptor(desc: String) {
        require(desc.startsWith("L") && desc.endsWith(";") && desc.length >= 3 && ' ' !in desc) {
            "invalid JNI class descriptor '$desc'"
        }
    }
}
