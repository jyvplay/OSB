package dev.fusion.format.pdf

/**
 * PDF indirect-object references (ISO 32000-1 §7.3.10: `objNum gen R`
 * with object number >= 1 and generation >= 0; generation 65535 is the
 * free-list ceiling and a REFERENCE carrying it is refused — only the
 * xref free list uses it).
 *
 * Declared narrowings: the reference TOKEN only — xref tables and
 * object streams live elsewhere (PdfTrailer owns the trailer); exactly
 * single spaces between the three tokens (writer canon), with the
 * loose-whitespace form refused so round-trips stay byte-stable.
 */
object ObjNum {
    const val SCOPE = "Indirect-reference token parse/format; single-space canon; xref/free-list out of scope."
    const val MAX_GENERATION = 65534

    data class Ref(val objNumber: Int, val generation: Int) {
        init {
            require(objNumber >= 1) { "object number " + objNumber + " must be >= 1" }
            require(generation in 0..MAX_GENERATION) { "generation " + generation + " outside 0..65534 (65535 is the free-list marker)" }
        }
        fun format(): String = "" + objNumber + " " + generation + " R"
    }

    private val SHAPE = Regex("(\\d{1,10}) (\\d{1,5}) R")

    fun parse(token: String): Ref {
        val m = SHAPE.matchEntire(token)
            ?: throw IllegalArgumentException("'" + token + "' is not a canonical 'n g R' reference")
        require(!m.groupValues[1].startsWith("0")) { "leading zero in object number" }
        require(m.groupValues[2] == "0" || !m.groupValues[2].startsWith("0")) { "leading zero in generation" }
        return Ref(m.groupValues[1].toInt(), m.groupValues[2].toInt())
    }

    fun isReference(token: String): Boolean = try { parse(token); true } catch (_: IllegalArgumentException) { false }
}
