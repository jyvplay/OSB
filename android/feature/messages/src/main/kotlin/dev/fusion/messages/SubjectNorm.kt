package dev.fusion.messages

/**
 * Subject normalization for threading (pairs MsgIdParse): strip
 * leading reply/forward markers (`Re:`, `RE:`, `Fwd:`, `FW:`, also
 * `Re[2]:` counted forms and repeats), collapse inner whitespace,
 * and report how many markers were removed so the UI can render
 * "Re(3):" honestly instead of guessing.
 *
 * Declared narrowings: the English marker set + bracket-count form
 * only (locale prefixes like `AW:`/`SV:` are data to add by decision,
 * refused-by-absence and named here); list tags `[name]` are KEPT
 * (stripping them merges distinct lists' threads — a real bug class).
 */
object SubjectNorm {
    const val SCOPE = "Re/Fwd marker strip + count; locale prefixes named-not-guessed; [list] tags kept."
    private val MARKER = Regex("^\\s*(re|fwd?|fw)(\\[(\\d+)\\])?:\\s*", RegexOption.IGNORE_CASE)

    data class Norm(val subject: String, val replyDepth: Int, val wasForward: Boolean)

    fun normalize(raw: String): Norm {
        var s = raw
        var depth = 0
        var fwd = false
        while (true) {
            val m = MARKER.find(s) ?: break
            val kind = m.groupValues[1].lowercase()
            val bracket = m.groupValues[3]
            depth += if (bracket.isNotEmpty()) {
                require(bracket.length <= 3) { "implausible marker count" }
                bracket.toInt()
            } else 1
            if (kind.startsWith("f")) fwd = true
            s = s.substring(m.range.last + 1)
        }
        val collapsed = s.trim().replace(Regex("\\s+"), " ")
        return Norm(collapsed, depth, fwd)
    }

    fun sameThread(a: String, b: String): Boolean =
        normalize(a).subject.equals(normalize(b).subject, ignoreCase = true)
}
