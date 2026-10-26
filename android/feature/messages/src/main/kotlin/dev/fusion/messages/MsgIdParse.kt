package dev.fusion.messages

/**
 * RFC 5322 Message-ID shape validation (`<id-left@id-right>`): angle
 * brackets required, exactly one unquoted @, dot-atom charset on both
 * sides, no whitespace or controls. Threading (References/In-Reply-To
 * chains) consumes these; a malformed id breaks thread grouping
 * silently, hence the loud intake.
 *
 * Declared narrowings: dot-atom forms only — quoted-string id-left
 * and domain-literal id-right (both RFC-legal, both vanishingly rare
 * in practice) REFUSE by name so support is a decision.
 */
object MsgIdParse {
    const val SCOPE = "Dot-atom <left@right> shape only; quoted/literal RFC forms refused by name."
    private const val ATEXT = "!#$%&'*+-/=?^_`{|}~"

    data class MsgId(val left: String, val right: String) {
        fun format(): String = "<$left@$right>"
    }

    private fun dotAtom(s: String, what: String) {
        require(s.isNotEmpty()) { "$what empty" }
        for (part in s.split('.')) {
            require(part.isNotEmpty()) { "$what has an empty dot-atom segment" }
            require(part.all { it.isLetterOrDigit() && it.code < 128 || it in ATEXT }) { "$what char outside dot-atom" }
        }
    }

    fun parse(text: String): MsgId {
        require(text.startsWith("<") && text.endsWith(">")) { "Message-ID needs angle brackets" }
        val body = text.substring(1, text.length - 1)
        require(body.none { it.isWhitespace() || it.code < 0x20 }) { "whitespace/control inside Message-ID" }
        require(!body.startsWith("\"")) { "quoted-string id-left refused by declared narrowing" }
        val at = body.indexOf('@')
        require(at > 0 && body.indexOf('@', at + 1) < 0) { "exactly one @ required" }
        val right = body.substring(at + 1)
        require(!right.startsWith("[")) { "domain-literal id-right refused by declared narrowing" }
        dotAtom(body.substring(0, at), "id-left")
        dotAtom(right, "id-right")
        return MsgId(body.substring(0, at), right)
    }

    fun isValid(text: String): Boolean = try { parse(text); true } catch (_: IllegalArgumentException) { false }
}
