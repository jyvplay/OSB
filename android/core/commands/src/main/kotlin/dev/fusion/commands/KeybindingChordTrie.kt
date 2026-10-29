// SPDX-License-Identifier: Apache-2.0
package dev.fusion.commands

/**
 * Multi-Stroke **Keybinding Chord Prefix Trie** (`Ctrl+K Ctrl+S`, `Alt+W 1`) with Shadowing Detector.
 *
 * Detects ambiguous bindings where a 1-stroke command (`Ctrl+K -> deleteToEnd`) shadows a 2-stroke
 * chord (`Ctrl+K Ctrl+S -> openShortcuts`), and steps stateful chord input cleanly (`MATCHED`,
 * `PENDING_NEXT_STROKE`, `UNBOUND_RESET`).
 */
object KeybindingChordTrie {
    const val SCOPE =
        "Multi-stroke keybinding chord prefix trie with prefix-shadowing conflict refusal and stateful stroke stepper."

    sealed interface StepOutcome {
        data class Matched(val commandId: String) : StepOutcome
        data class PendingPrefix(val completions: List<String>) : StepOutcome
        data object Unbound : StepOutcome
    }

    class Trie internal constructor(
        private val root: Node,
    ) {
        private val buffer = ArrayList<String>()

        fun reset() { buffer.clear() }

        fun feedStroke(rawStroke: String): StepOutcome {
            val stroke = normalizeStroke(rawStroke)
            buffer.add(stroke)
            var cur = root
            for (s in buffer) {
                val next = cur.children[s]
                if (next == null) {
                    buffer.clear()
                    return StepOutcome.Unbound
                }
                cur = next
            }
            val cmd = cur.commandId
            if (cmd != null) {
                buffer.clear()
                return StepOutcome.Matched(cmd)
            }
            return StepOutcome.PendingPrefix(collectCommands(cur))
        }

        private fun collectCommands(n: Node): List<String> {
            val out = ArrayList<String>()
            fun dfs(x: Node) {
                x.commandId?.let { out.add(it) }
                for ((_, ch) in x.children.entries.sortedBy { it.key }) dfs(ch)
            }
            dfs(n)
            return out
        }
    }

    internal class Node {
        val children = LinkedHashMap<String, Node>()
        var commandId: String? = null
    }

    fun compile(bindings: Map<String, String>): Trie {
        val root = Node()
        for ((rawChord, cmdId) in bindings) {
            require(cmdId.isNotBlank()) { "blank commandId for chord '$rawChord'" }
            val strokes = rawChord.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }.map(::normalizeStroke)
            require(strokes.size in 1..4) { "chord '$rawChord' stroke count outside 1..4" }
            var cur = root
            for (i in strokes.indices) {
                val s = strokes[i]
                val next = cur.children.getOrPut(s) { Node() }
                if (next.commandId != null) {
                    throw IllegalArgumentException("chord prefix conflict: '$rawChord' shadowed by terminal binding '${next.commandId}'")
                }
                if (i == strokes.lastIndex && next.children.isNotEmpty()) {
                    throw IllegalArgumentException("chord prefix conflict: '$rawChord' shadows longer multi-stroke chord")
                }
                cur = next
            }
            cur.commandId = cmdId
        }
        return Trie(root)
    }

    fun normalizeStroke(raw: String): String {
        val parts = raw.trim().split('+').map { it.trim().lowercase() }.filter { it.isNotEmpty() }
        require(parts.isNotEmpty()) { "empty key stroke" }
        val key = parts.last().uppercase()
        val mods = parts.dropLast(1).map {
            when (it) {
                "control", "ctl", "ctrl" -> "Ctrl"
                "shift", "shft" -> "Shift"
                "option", "opt", "alt" -> "Alt"
                "cmd", "super", "win", "meta" -> "Meta"
                else -> throw IllegalArgumentException("unknown modifier '$it' in '$raw'")
            }
        }.distinct().sortedBy { listOf("Ctrl", "Alt", "Shift", "Meta").indexOf(it) }
        return (mods + key).joinToString("+")
    }
}
