package dev.fusion.documents

/**
 * Case cycling for the writer's change-case shortcut: UPPER → lower →
 * Title → UPPER, with [detect] classifying the current state so the
 * cycle continues from wherever the text is.
 *
 * Title rule (stated contract, deliberately simple): the first LETTER
 * of each whitespace-separated token uppercases, the rest lowercase;
 * no small-word list (AP/Chicago styles are dictionary work — a
 * documented narrowing, same policy as SmartQuote's elisions).
 *
 * Declared narrowings: locale-neutral Kotlin case mapping (no Turkish-i
 * handling — declared, not hidden); strings with NO letters refuse
 * classification rather than returning an arbitrary state.
 */
object CaseCycle {
    const val SCOPE = "UPPER->lower->Title cycle, simple title rule, locale-neutral; no-letter input refuses."

    enum class State { UPPER, LOWER, TITLE, MIXED }

    fun title(text: String): String = text.split(" ").joinToString(" ") { tok ->
        val idx = tok.indexOfFirst { it.isLetter() }
        if (idx < 0) tok
        else tok.substring(0, idx) + tok[idx].uppercaseChar() + tok.substring(idx + 1).lowercase()
    }

    fun detect(text: String): State {
        require(text.any { it.isLetter() }) { "no letters to classify" }
        val letters = text.filter { it.isLetter() }
        return when {
            letters.all { it.isUpperCase() } -> State.UPPER
            letters.all { it.isLowerCase() } -> State.LOWER
            text == title(text) -> State.TITLE
            else -> State.MIXED
        }
    }

    /** The shortcut: next state in the cycle (MIXED enters at UPPER). */
    fun next(text: String): String = when (detect(text)) {
        State.UPPER -> text.lowercase()
        State.LOWER -> title(text)
        State.TITLE, State.MIXED -> text.uppercase()
    }
}
