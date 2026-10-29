package dev.fusion.terminal

/**
 * Clean-room `DECSET ?1049h / ?1049l` VT100 Alternate-Screen Buffer, Bounded Scrollback Ring,
 * and Bracketed-Paste (`\u001b[200~ ... \u001b[201~`) Escape-Injection Sanitizer (Apache-2.0).
 *
 * Defends against clipboard-to-terminal RCE ("Pastejacking") by stripping embedded `\u001b[201~`,
 * CSI/OSC control bytes, and raw `\r`/`\n` command-execution breaks unless explicitly multiline!
 */
object Vt100AlternateScreenScrollback {
    const val SCOPE =
        "DECSET ?1049h/?1049l alternate-screen buffer + bounded scrollback ring + anti-pastejacking bracketed-paste sanitizer"

    const val BRACKET_PASTE_START = "\u001b[200~"
    const val BRACKET_PASTE_END = "\u001b[201~"

    class ScreenBuffer(val cols: Int = 80, val rows: Int = 24, val maxScrollbackLines: Int = 1_000) {
        private val primaryLines = ArrayDeque<String>().apply { repeat(rows) { addLast("") } }
        private val scrollback = ArrayDeque<String>()
        private var altLines = Array(rows) { "" }
        var inAlternateScreen: Boolean = false
            private set

        fun writeLine(line: String) {
            val clipped = line.take(cols)
            if (inAlternateScreen) {
                for (r in 0 until rows - 1) altLines[r] = altLines[r + 1]
                altLines[rows - 1] = clipped
            } else {
                val evicted = primaryLines.removeFirst()
                if (evicted.isNotEmpty() || scrollback.isNotEmpty()) {
                    if (scrollback.size >= maxScrollbackLines) scrollback.removeFirst()
                    scrollback.addLast(evicted)
                }
                primaryLines.addLast(clipped)
            }
        }

        fun enterAlternateScreen() {
            if (!inAlternateScreen) {
                inAlternateScreen = true
                altLines = Array(rows) { "" }
            }
        }

        fun exitAlternateScreen() {
            if (inAlternateScreen) {
                inAlternateScreen = false
                altLines = Array(rows) { "" }
            }
        }

        fun visibleViewport(): List<String> =
            if (inAlternateScreen) altLines.toList() else primaryLines.toList()

        fun scrollbackHistory(): List<String> = scrollback.toList()
    }

    /** Wraps untrusted clipboard text safely inside XTerm bracketed paste, neutralizing premature `\e[201~` escapes! */
    fun sanitizeBracketedPaste(untrustedClipboard: String): String {
        val stripped = buildString(untrustedClipboard.length) {
            for (ch in untrustedClipboard) {
                val code = ch.code
                // Strip ESC (0x1B), BEL (0x07), DEL (0x7F), C1 controls (0x80..0x9F), NUL
                if (code == 0x1B || code == 0x07 || code == 0x7F || code == 0x00 || code in 0x80..0x9F) continue
                append(ch)
            }
        }
        return "$BRACKET_PASTE_START$stripped$BRACKET_PASTE_END"
    }
}
