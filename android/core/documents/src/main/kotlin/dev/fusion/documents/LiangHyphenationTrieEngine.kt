// SPDX-License-Identifier: Apache-2.0
package dev.fusion.documents

/**
 * Clean-room Frank Liang (1983) TeX inter-letter priority pattern trie hyphenator.
 *
 * Evaluates embedded permissive English (`en-US`/`en-GB`) + custom `.pat` pattern rules over
 * `.word.` sentinels: odd inter-letter weights permit a break, even weights forbid a break,
 * explicit soft hyphens (`\u00AD`) override automatic patterns, and short (`< minWordLength`),
 * numeric, or ligature-splitting (`ff`, `fi`, `fl`, `ffi`, `ffl`) positions are protected.
 */
object LiangHyphenationTrieEngine {
    private val DEFAULT_EN_PATTERNS = listOf(
        ".hy3p", "hy3ph", "he2n", "hen5a", "na4t", "ti4o", "io2n",
        ".al3g", "go3r", "ri2t", "ith4m", ".ty3p", "po3g", "gr4a", "ra3ph",
        ".pa3r", "ra3g", "ag4r", ".do3c", "cu3m", "me2n", "nt4a", "at3i",
        ".sp3r", "re4a", "ad3s", "sh4e", "4tion", "4ment", "4ness", "4able",
        "3form", "3proc", "3comp", "3struc", "1ba", "1be", "1bi", "1bo", "1bu",
        "1ca", "1ce", "1ci", "1co", "1cu", "1da", "1de", "1di", "1do", "1du",
        "1fa", "1fe", "1fi", "1fo", "1fu", "1la", "1le", "1li", "1lo", "1lu",
        "1ma", "1me", "1mi", "1mo", "1mu", "1na", "1ne", "1ni", "1no", "1nu",
        "1pa", "1pe", "1pi", "1po", "1pu", "1ra", "1re", "1ri", "1ro", "1ru",
        "1sa", "1se", "1si", "1so", "1su", "1ta", "1te", "1ti", "1to", "1tu",
        "2bb", "2cc", "2dd", "2ff", "2gg", "2ll", "2mm", "2nn", "2pp", "2rr", "2ss", "2tt",
        "b2l", "c2k", "c2h", "g2h", "p2h", "s2h", "t2h", "w2h",
    )

    private val PROTECTED_LIGATURES = setOf("ff", "fi", "fl")

    class TrieNode {
        val children = HashMap<Char, TrieNode>()
        var weights: IntArray? = null
    }

    class PatternDictionary(patterns: Iterable<String>, val exceptions: Map<String, List<Int>> = emptyMap()) {
        val root = TrieNode()

        init {
            for (raw in patterns) insertPattern(raw.trim())
        }

        private fun insertPattern(pat: String) {
            if (pat.isEmpty()) return
            val letters = StringBuilder()
            val w = IntArray(pat.length + 1)
            var letterIdx = 0
            for (ch in pat) {
                if (ch in '0'..'9') {
                    w[letterIdx] = ch - '0'
                } else {
                    letters.append(ch.lowercaseChar())
                    letterIdx++
                }
            }
            var node = root
            for (i in 0 until letters.length) {
                node = node.children.getOrPut(letters[i]) { TrieNode() }
            }
            val trimmed = w.copyOf(letters.length + 1)
            val prev = node.weights
            if (prev == null) {
                node.weights = trimmed
            } else {
                val merged = IntArray(maxOf(prev.size, trimmed.size))
                for (i in merged.indices) {
                    merged[i] = maxOf(prev.getOrElse(i) { 0 }, trimmed.getOrElse(i) { 0 })
                }
                node.weights = merged
            }
        }
    }

    val DEFAULT_ENGLISH = PatternDictionary(
        patterns = DEFAULT_EN_PATTERNS,
        exceptions = mapOf(
            "spreadsheet" to listOf(6),
            "cryptography" to listOf(4, 7),
            "typesetting" to listOf(4, 7),
            "pagination" to listOf(3, 6),
        ),
    )

    fun hyphenateWord(
        word: String,
        dict: PatternDictionary = DEFAULT_ENGLISH,
        minWordLength: Int = 6,
        leftHyphenMin: Int = 2,
        rightHyphenMin: Int = 3,
    ): List<Int> {
        if ('\u00AD' in word) {
            // Explicit soft hyphen overrides automatic Liang pattern breaks (folio#28)
            val cleanPositions = ArrayList<Int>()
            var visibleCount = 0
            for (ch in word) {
                if (ch == '\u00AD') {
                    if (visibleCount > 0) cleanPositions.add(visibleCount)
                } else {
                    visibleCount++
                }
            }
            return cleanPositions.filter { it < visibleCount }.distinct()
        }

        if (word.length < minWordLength || word.any { !it.isLetter() }) return emptyList()
        val lower = word.lowercase()
        dict.exceptions[lower]?.let { exc ->
            return exc.filter { it >= leftHyphenMin && it <= word.length - rightHyphenMin }
        }

        val padded = ".$lower."
        val scores = IntArray(padded.length + 1)
        for (start in padded.indices) {
            var node: TrieNode? = dict.root
            for (end in start until padded.length) {
                node = node?.children?.get(padded[end]) ?: break
                val w = node.weights
                if (w != null) {
                    for (k in w.indices) {
                        val pos = start + k
                        if (pos < scores.size && w[k] > scores[pos]) {
                            scores[pos] = w[k]
                        }
                    }
                }
            }
        }

        val result = ArrayList<Int>()
        for (charSplit in leftHyphenMin..(word.length - rightHyphenMin)) {
            // Inter-letter position `charSplit` in `word` maps to `charSplit + 1` in `.word.`
            val weight = scores[charSplit + 1]
            if (weight % 2 == 1) {
                val pair = lower.substring(charSplit - 1, charSplit + 1)
                if (pair !in PROTECTED_LIGATURES) {
                    result.add(charSplit)
                }
            }
        }
        return result
    }

    fun stripSoftHyphens(word: String): String = word.replace("\u00AD", "")
}
