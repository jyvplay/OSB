// SPDX-License-Identifier: Apache-2.0
package dev.fusion.find

/**
 * Pure-JVM **Byte-Pair Encoding (`BPE`) Subword Merge Table & Context-Window Token Budgeter**
 * for `:core:ai`, running 100% locally in memory with deterministic greedy/priority rank merges.
 */
object LocalBpeSubwordTokenizer {
    const val SCOPE =
        "Deterministic local Byte-Pair Encoding (BPE) subword merge tokenizer and sliding context-window budgeter."

    class MergeVocabulary(mergePairsInPriorityOrder: List<Pair<String, String>>) {
        private val rankMap: Map<Pair<String, String>, Int> =
            mergePairsInPriorityOrder.withIndex().associate { (idx, pair) -> pair to idx }

        fun encodeWord(word: String): List<String> {
            if (word.isEmpty()) return emptyList()
            var symbols = word.map { it.toString() }.toMutableList()
            while (symbols.size >= 2) {
                var bestPos = -1
                var bestRank = Int.MAX_VALUE
                for (i in 0 until symbols.size - 1) {
                    val r = rankMap[symbols[i] to symbols[i + 1]] ?: continue
                    if (r < bestRank) {
                        bestRank = r
                        bestPos = i
                    }
                }
                if (bestPos < 0) break
                val merged = symbols[bestPos] + symbols[bestPos + 1]
                symbols[bestPos] = merged
                symbols.removeAt(bestPos + 1)
            }
            return symbols
        }

        fun encodeText(text: String, maxTokens: Int = 4096): List<String> {
            require(maxTokens >= 1)
            val out = ArrayList<String>()
            for (part in Regex("\\S+").findAll(text)) {
                for (tok in encodeWord(part.value)) {
                    out.add(tok)
                    if (out.size >= maxTokens) return out
                }
            }
            return out
        }
    }
}
