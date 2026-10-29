package dev.fusion.commands

/**
 * Clean-room CamelCase / Word-Boundary Subsequence Fuzzy Command Palette Scorer & Two-Stroke
 * Key-Chord (`Ctrl+K -> Ctrl+S`) State Machine (Apache-2.0).
 */
object FuzzyChordCommandPaletteEngine {
    const val SCOPE =
        "Word-boundary/camelCase subsequence fuzzy command ranker + multi-stroke key-chord prefix state machine"

    data class CommandEntry(val id: String, val title: String, val chordSequence: List<String> = emptyList())
    data class RankedCommand(val command: CommandEntry, val score: Int, val matchedIndices: List<Int>)

    enum class ChordResolution { UNBOUND, WAITING_FOR_NEXT_STROKE, DISPATCHED }
    data class ChordStepResult(val status: ChordResolution, val commandId: String? = null)

    fun rankCommands(query: String, commands: List<CommandEntry>, limit: Int = 10): List<RankedCommand> {
        val q = query.trim().lowercase()
        if (q.isEmpty()) return commands.take(limit).map { RankedCommand(it, 0, emptyList()) }
        return commands.mapNotNull { scoreSubsequence(q, it) }
            .sortedWith(compareByDescending<RankedCommand> { it.score }.thenBy { it.command.title })
            .take(limit)
    }

    class ChordStateMachine(bindings: List<CommandEntry>) {
        private val chordMap = bindings.filter { it.chordSequence.isNotEmpty() }.associateBy { it.chordSequence }
        private val buffer = ArrayList<String>()

        fun onKeyStroke(normalizedStroke: String): ChordStepResult {
            buffer.add(normalizedStroke.trim())
            val exact = chordMap[buffer.toList()]
            if (exact != null) {
                buffer.clear()
                return ChordStepResult(ChordResolution.DISPATCHED, exact.id)
            }
            val hasPrefix = chordMap.keys.any { seq -> seq.size > buffer.size && seq.subList(0, buffer.size) == buffer }
            if (hasPrefix) return ChordStepResult(ChordResolution.WAITING_FOR_NEXT_STROKE, null)
            buffer.clear()
            return ChordStepResult(ChordResolution.UNBOUND, null)
        }
    }

    private fun scoreSubsequence(qLower: String, cmd: CommandEntry): RankedCommand? {
        val t = cmd.title
        val tLower = t.lowercase()
        val idxs = ArrayList<Int>(qLower.length)
        var ti = 0
        for (qc in qLower) {
            while (ti < tLower.length && tLower[ti] != qc) ti++
            if (ti == tLower.length) return null
            idxs.add(ti)
            ti++
        }
        var score = 100 - (idxs.last() - idxs.first())
        for ((k, pos) in idxs.withIndex()) {
            val isBoundary = pos == 0 || !t[pos - 1].isLetterOrDigit() || (t[pos].isUpperCase() && t[pos - 1].isLowerCase())
            if (isBoundary) score += 25
            if (k > 0 && idxs[k - 1] + 1 == pos) score += 18
        }
        return RankedCommand(cmd, score, idxs)
    }
}
