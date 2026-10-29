// SPDX-License-Identifier: Apache-2.0
package dev.fusion.a11y

/**
 * Clean-room canonical keyboard / Switch-Access shortcut chord parser, WCAG 2.2 SC 2.1.4
 * Character-Key-Shortcut gate, and binding conflict detector for `:feature:accessibility`.
 *
 * Enforces:
 * - Deterministic modifier canonicalization (`Ctrl` -> `Alt` -> `Shift` -> `Meta` -> `<Key>`),
 *   mapping synonyms (`Cmd`/`Super`/`Win` -> `Meta`, `Option` -> `Alt`, `Esc` -> `Escape`).
 * - Refuses modifier-only chords (`"Ctrl+Shift"`) and duplicate modifiers (`"Ctrl+Ctrl+K"`).
 * - **WCAG 2.2 Success Criterion 2.1.4 (Character Key Shortcuts, Level A)**: refuses global
 *   unmodified or `Shift`-only single printable character shortcuts (`"K"`, `"/"`, `"Shift+?"`)
 *   that hijack speech-input dictation and Switch-Access typing unless `scopedToNonTextComponent = true`.
 * - Detects normalized shortcut collisions across an action map (`findConflicts`).
 */
object KeyChord {
    const val SCOPE =
        "Canonical Ctrl+Alt+Shift+Meta+Key chord parser, WCAG 2.2 SC 2.1.4 single-character trap refusal, and shortcut conflict detector; platform KeyEvent scan-code hardware remapping out of scope."

    enum class Modifier(val label: String) {
        CTRL("Ctrl"),
        ALT("Alt"),
        SHIFT("Shift"),
        META("Meta"),
    }

    data class Chord(val modifiers: Set<Modifier>, val key: String) {
        fun canonicalString(): String = buildString {
            for (m in Modifier.entries) {
                if (m in modifiers) append(m.label).append('+')
            }
            append(key)
        }
    }

    data class Binding(
        val actionId: String,
        val rawChord: String,
        val scopedToNonTextComponent: Boolean = false,
    )

    data class Conflict(val canonicalChord: String, val actionIds: List<String>)

    private val NON_PRINTABLE_KEYS = mapOf(
        "esc" to "Escape", "escape" to "Escape",
        "tab" to "Tab", "enter" to "Enter", "return" to "Enter",
        "space" to "Space", "backspace" to "Backspace", "delete" to "Delete", "del" to "Delete",
        "up" to "ArrowUp", "arrowup" to "ArrowUp",
        "down" to "ArrowDown", "arrowdown" to "ArrowDown",
        "left" to "ArrowLeft", "arrowleft" to "ArrowLeft",
        "right" to "ArrowRight", "arrowright" to "ArrowRight",
        "home" to "Home", "end" to "End",
        "pageup" to "PageUp", "pgup" to "PageUp",
        "pagedown" to "PageDown", "pgdn" to "PageDown",
        "f1" to "F1", "f2" to "F2", "f3" to "F3", "f4" to "F4",
        "f5" to "F5", "f6" to "F6", "f7" to "F7", "f8" to "F8",
        "f9" to "F9", "f10" to "F10", "f11" to "F11", "f12" to "F12",
    )

    fun parse(raw: String, scopedToNonTextComponent: Boolean = false): Chord {
        val trim = raw.trim()
        require(trim.isNotEmpty() && trim.length <= 64) { "invalid key chord '$raw'" }

        // Handle trailing '+' key (e.g. "Ctrl++") cleanly before splitting on '+'
        val tokens = splitChordTokens(trim)
        require(tokens.isNotEmpty()) { "empty key chord '$raw'" }

        val mods = LinkedHashSet<Modifier>()
        var primaryKey: String? = null

        for ((idx, tok) in tokens.withIndex()) {
            val asMod = parseModifier(tok)
            if (asMod != null) {
                require(idx < tokens.lastIndex) { "chord '$raw' lacks a non-modifier primary key" }
                require(mods.add(asMod)) { "duplicate modifier '${asMod.label}' in '$raw'" }
            } else {
                require(idx == tokens.lastIndex && primaryKey == null) {
                    "unexpected multiple non-modifier keys in '$raw'"
                }
                primaryKey = normalizeKey(tok)
            }
        }
        val key = primaryKey ?: throw IllegalArgumentException("modifier-only chord '$raw' refused")

        // WCAG 2.2 SC 2.1.4 gate: printable character shortcuts (including Space) without Ctrl/Alt/Meta
        val hasSystemModifier = Modifier.CTRL in mods || Modifier.ALT in mods || Modifier.META in mods
        val isPrintableTrap = (key.length == 1 || key == "Space") && !hasSystemModifier
        require(!isPrintableTrap || scopedToNonTextComponent) {
            "WCAG 2.1.4 violation: character shortcut '${Chord(mods, key).canonicalString()}' requires Ctrl/Alt/Meta or non-text focus scope"
        }

        val orderedMods = Modifier.entries.filterTo(LinkedHashSet()) { it in mods }
        return Chord(orderedMods, key)
    }

    fun findConflicts(bindings: List<Binding>): List<Conflict> {
        val seenActionIds = HashSet<String>()
        val byChord = LinkedHashMap<String, MutableList<String>>()
        for (b in bindings) {
            require(b.actionId.isNotBlank()) { "actionId must not be blank" }
            require(seenActionIds.add(b.actionId)) { "duplicate actionId '${b.actionId}'" }
            val chord = parse(b.rawChord, b.scopedToNonTextComponent).canonicalString()
            byChord.getOrPut(chord) { ArrayList() }.add(b.actionId)
        }
        return byChord.entries
            .filter { it.value.size > 1 }
            .map { Conflict(it.key, it.value.toList()) }
    }

    private fun parseModifier(tok: String): Modifier? = when (tok.lowercase()) {
        "ctrl", "control" -> Modifier.CTRL
        "alt", "option", "opt" -> Modifier.ALT
        "shift" -> Modifier.SHIFT
        "meta", "cmd", "command", "super", "win" -> Modifier.META
        else -> null
    }

    private fun normalizeKey(tok: String): String {
        NON_PRINTABLE_KEYS[tok.lowercase()]?.let { return it }
        if (tok.length == 1) {
            val c = tok[0]
            require(c.code in 0x21..0x7E) { "unsupported control/whitespace key '$tok'" }
            return c.uppercaseChar().toString()
        }
        throw IllegalArgumentException("unrecognized primary key token '$tok'")
    }

    private fun splitChordTokens(s: String): List<String> {
        val out = ArrayList<String>()
        val cur = StringBuilder()
        var i = 0
        while (i < s.length) {
            val c = s[i]
            if (c == '+') {
                if (cur.isEmpty()) {
                    cur.append('+') // '+' is the key itself (e.g. after a separator '+' in "Ctrl++")
                } else {
                    out.add(cur.toString().trim())
                    cur.setLength(0)
                }
            } else {
                cur.append(c)
            }
            i++
        }
        require(cur.isNotEmpty()) { "trailing '+' without key in '$s'" }
        out.add(cur.toString().trim())
        require(out.all { it.isNotEmpty() }) { "empty token in chord '$s'" }
        return out
    }
}
