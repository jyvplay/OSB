// SPDX-License-Identifier: Apache-2.0
package dev.fusion.baselineprofile

/**
 * Clean-room Android Runtime (`ART`) `HSPL` / `PL` Baseline Profile Rule Synthesizer & Canonical
 * Deduplicator covering Fusion Shell R50 hot startup & interactive Office/Wasm/Browser/Lens paths.
 */
object ArtStartupProfileRuleSynthesizer {
    const val SCOPE =
        "Pure-JVM ART baseline-profile rule synthesizer emitting deduplicated HSPL/PL method & L-descriptor class rules."

    private val VALID_FLAGS = setOf("H", "S", "P", "HS", "HP", "SP", "HSP", "")

    fun synthesizeProfileText(classAndMethodSpecs: List<Triple<String, String, String?>>): String {
        require(classAndMethodSpecs.isNotEmpty()) { "baseline profile rule list cannot be empty" }
        val lines = LinkedHashSet<String>()
        for ((flags, binaryClassName, methodSigOrNull) in classAndMethodSpecs) {
            require(flags in VALID_FLAGS) { "invalid ART profile flags '$flags'" }
            require(binaryClassName.startsWith("dev/fusion/") && !binaryClassName.contains('.')) {
                "expected slash-delimited dev/fusion/* binary class name, got '$binaryClassName'"
            }
            val descriptor = "L$binaryClassName;"
            if (methodSigOrNull == null) {
                lines.add(descriptor)
            } else {
                require("->" !in methodSigOrNull && '(' in methodSigOrNull && ')' in methodSigOrNull) {
                    "invalid JVM method signature '$methodSigOrNull'"
                }
                lines.add("${flags}${descriptor}->$methodSigOrNull")
            }
        }
        return lines.sorted().joinToString("\n") + "\n"
    }
}
