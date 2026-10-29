// SPDX-License-Identifier: Apache-2.0
package dev.fusion.documents

/**
 * Clean-room Franklin M. Liang (1983, Stanford PhD thesis *Word Hy-phen-a-tion by Com-put-er*)
 * finite-state pattern hyphenation engine.
 *
 * Uses odd/even inter-character priority levels (`.hy3phen`, `4tion`, `al1g2o`, etc.):
 * odd priorities permit a hyphen break; even priorities inhibit a hyphen break; maximum priority
 * wins at each inter-glyph slot, respecting `leftHyphenMin >= 2` and `rightHyphenMin >= 3`.
 */
class LiangHyphenationEngine(
    patterns: List<String> = DEFAULT_ENGLISH_PATTERNS,
    exceptions: Map<String, List<String>> = DEFAULT_EXCEPTIONS,
    val leftHyphenMin: Int = 2,
    val rightHyphenMin: Int = 3,
) {
    private class TrieNode {
        val children = HashMap<Char, TrieNode>()
        var levels: IntArray? = null
    }

    private val root = TrieNode()
    private val normalizedExceptions: Map<String, List<String>>

    init {
        require(leftHyphenMin >= 1 && rightHyphenMin >= 1) { "hyphen margins must be >= 1" }
        for (raw in patterns) insertPattern(raw)
        normalizedExceptions = exceptions.entries.associate { (k, v) -> k.lowercase() to v }
    }

    private fun insertPattern(pattern: String) {
        if (pattern.isBlank()) return
        val letters = ArrayList<Char>()
        val digits = ArrayList<Int>()
        var pending = 0
        for (ch in pattern.trim().lowercase()) {
            if (ch in '0'..'9') {
                pending = ch - '0'
            } else {
                digits.add(pending)
                pending = 0
                letters.add(ch)
            }
        }
        digits.add(pending)
        var node = root
        for (ch in letters) {
            node = node.children.getOrPut(ch) { TrieNode() }
        }
        node.levels = digits.toIntArray()
    }

    fun hyphenateWord(word: String): List<String> {
        if (word.length < leftHyphenMin + rightHyphenMin) return listOf(word)
        var lead = 0
        while (lead < word.length && !word[lead].isLetter()) lead++
        var trail = word.length
        while (trail > lead && !word[trail - 1].isLetter()) trail--
        if (trail - lead < leftHyphenMin + rightHyphenMin) return listOf(word)

        val core = word.substring(lead, trail)
        if (core.any { !it.isLetter() }) return listOf(word)

        val lower = core.lowercase()
        normalizedExceptions[lower]?.let { exc ->
            // Re-project original casing + punctuation onto exception syllables
            val out = ArrayList<String>(exc.size)
            var offset = 0
            for ((i, part) in exc.withIndex()) {
                val slice = core.substring(offset, offset + part.length)
                offset += part.length
                val prefix = if (i == 0) word.substring(0, lead) else ""
                val suffix = if (i == exc.lastIndex) word.substring(trail) else ""
                out.add(prefix + slice + suffix)
            }
            return out
        }

        val framed = ".$lower."
        val work = IntArray(framed.length + 1)
        for (start in framed.indices) {
            var node: TrieNode? = root
            var pos = start
            while (pos < framed.length && node != null) {
                node = node.children[framed[pos]]
                val lv = node?.levels
                if (lv != null) {
                    for (j in lv.indices) {
                        if (lv[j] > work[start + j]) work[start + j] = lv[j]
                    }
                }
                pos++
            }
        }

        // Inter-character slot `k` (1..core.length-1) corresponds to `work[k + 1]` in `.c0 c1 ... c_{m-1}.`
        val syllables = ArrayList<String>()
        var chunkStart = 0
        for (k in 1 until core.length) {
            if (k >= leftHyphenMin && core.length - k >= rightHyphenMin) {
                val priority = work[k + 1]
                if (priority % 2 == 1) {
                    syllables.add(core.substring(chunkStart, k))
                    chunkStart = k
                }
            }
        }
        syllables.add(core.substring(chunkStart))
        if (syllables.size == 1) return listOf(word)
        syllables[0] = word.substring(0, lead) + syllables[0]
        syllables[syllables.lastIndex] = syllables.last() + word.substring(trail)
        return syllables
    }

    companion object {
        val DEFAULT_EXCEPTIONS: Map<String, List<String>> = mapOf(
            "algorithm" to listOf("al", "go", "rithm"),
            "algorithms" to listOf("al", "go", "rithms"),
            "anonymous" to listOf("an", "on", "y", "mous"),
            "cryptography" to listOf("cryp", "tog", "ra", "phy"),
            "hyphenation" to listOf("hy", "phen", "a", "tion"),
            "paragraph" to listOf("para", "graph"),
            "spreadsheet" to listOf("spread", "sheet"),
            "table" to listOf("ta", "ble"),
            "tables" to listOf("ta", "bles"),
        )

        val DEFAULT_ENGLISH_PATTERNS: List<String> = listOf(
            ".ach4", ".ad4der", ".af1t", ".al3t", ".am5at", ".an1t", ".anti5", ".ar1c",
            ".as3s", ".at3t", ".au3t", ".av3e", ".be5ra", ".bi1o", ".ca4t", ".co3m",
            ".con3", ".cryp3", ".de3c", ".de3v", ".di3s", ".do3c", ".dy3n", ".el3e",
            ".en3c", ".ex3p", ".fi3n", ".fo3r", ".fu3s", ".ge3n", ".hy3ph", ".im3p",
            ".in1t", ".is3o", ".ka3l", ".ke4y", ".la3m", ".li3n", ".ma3t", ".me3m",
            ".mi3c", ".mo3d", ".mu3l", ".ne3t", ".no3t", ".ob3l", ".op3t", ".pa3g",
            ".pa3r", ".pe3r", ".pi3x", ".po3l", ".pr3i", ".pro3", ".qu3a", ".re3d",
            ".re3l", ".sa3n", ".se3c", ".sh3e", ".si3g", ".sp3r", ".st3a", ".su3b",
            ".sy3n", ".ta3i", ".te3l", ".tr3a", ".un1", ".va3l", ".ve3r", ".vi3s",
            ".wa3s", ".wi3n", "1ba", "1be", "1bi", "1bo", "1bu", "1ca", "1ce", "1ci",
            "1co", "1cu", "1da", "1de", "1di", "1do", "1du", "1fa", "1fe", "1fi",
            "1fo", "1fu", "1ga", "1ge", "1gi", "1go", "1gu", "1la", "1le", "1li",
            "1lo", "1lu", "1ma", "1me", "1mi", "1mo", "1mu", "1na", "1ne", "1ni",
            "1no", "1nu", "1pa", "1pe", "1pi", "1po", "1pu", "1ra", "1re", "1ri",
            "1ro", "1ru", "1sa", "1se", "1si", "1so", "1su", "1ta", "1te", "1ti",
            "1to", "1tu", "1va", "1ve", "1vi", "1vo", "4tion.", "3ment.", "3ness.",
            "3able.", "3ible.", "3ing.", "4ally.", "2st", "2nt", "2nd", "2rg", "2ct",
        )
    }
}
