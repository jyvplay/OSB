package dev.fusion.ooxml

/**
 * SpreadsheetML cell error literals (ECMA-376 Part 1 §18.17.3 /
 * ST_CellType "e" values).
 *
 * The seven error literals a cell with t="e" may carry, with their
 * canonical spellings — including the two that end in characters other
 * than '!': #NAME? and #N/A.
 *
 * Declared narrowings: literal recognition and formatting only. Error
 * PROPAGATION through formula evaluation and the newer dynamic-array
 * errors (#SPILL!, #CALC!) are out of scope — the latter are refused by
 * name so a future extension is a decision, not an accident. Boolean
 * display literals TRUE/FALSE are classified here too because they share
 * the display-literal namespace; their 0/1 storage encoding is out of
 * scope.
 */
object CellErr {
    const val SCOPE = "The 7 classic error literals + TRUE/FALSE display tokens; no propagation, no #SPILL!/#CALC!."

    enum class Err(val literal: String) {
        NULL("#NULL!"), DIV0("#DIV/0!"), VALUE("#VALUE!"), REF("#REF!"),
        NAME("#NAME?"), NUM("#NUM!"), NA("#N/A")
    }

    private val BY_LITERAL = Err.values().associateBy { it.literal }
    private val NEWER = setOf("#SPILL!", "#CALC!", "#GETTING_DATA", "#FIELD!", "#BLOCKED!", "#UNKNOWN!")

    fun isError(text: String): Boolean = text in BY_LITERAL

    fun parse(text: String): Err {
        BY_LITERAL[text]?.let { return it }
        require(text !in NEWER) { "'$text' is a dynamic-array-era error, outside this kernel's declared scope" }
        throw IllegalArgumentException("'$text' is not a SpreadsheetML error literal")
    }

    fun format(e: Err): String = e.literal

    enum class Literal { ERROR, BOOLEAN_TRUE, BOOLEAN_FALSE, OTHER }

    /** Display-literal classification: exact, case-sensitive, per the XML display forms. */
    fun classify(text: String): Literal = when {
        text in BY_LITERAL -> Literal.ERROR
        text == "TRUE" -> Literal.BOOLEAN_TRUE
        text == "FALSE" -> Literal.BOOLEAN_FALSE
        else -> Literal.OTHER
    }
}
