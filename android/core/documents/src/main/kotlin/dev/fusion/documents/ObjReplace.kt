package dev.fusion.documents

/**
 * U+FFFC OBJECT REPLACEMENT CHARACTER handling for the writer surface.
 *
 * Unicode semantics: U+FFFC stands in for an inline object (image,
 * attachment) whose content lives elsewhere; text processing must treat
 * it as one opaque character, and plain-text export must not leak it.
 *
 * Invariant enforced by tests: strip(text).length + positions(text).size
 * == text.length — objects and visible text partition the string.
 *
 * Declared narrowings: the placeholder character only — attachment
 * payloads, layout and rendering live elsewhere; indices are Kotlin char
 * indices (U+FFFC is BMP so char==codepoint here).
 */
object ObjReplace {
    const val OBJ = '\uFFFC'
    const val SCOPE = "U+FFFC placeholder bookkeeping only; attachment payloads and rendering live elsewhere."

    fun insert(text: String, at: Int): String {
        require(at in 0..text.length) { "insert position $at outside 0..${text.length}" }
        return text.substring(0, at) + OBJ + text.substring(at)
    }

    fun positions(text: String): List<Int> =
        text.indices.filter { text[it] == OBJ }

    fun strip(text: String): String = text.filter { it != OBJ }

    fun remove(text: String, at: Int): String {
        require(at in text.indices && text[at] == OBJ) { "no object placeholder at $at" }
        return text.substring(0, at) + text.substring(at + 1)
    }

    /** Visible-text segments between objects; size is always objects+1. */
    fun segments(text: String): List<String> {
        val out = ArrayList<String>()
        val cur = StringBuilder()
        for (c in text) {
            if (c == OBJ) { out.add(cur.toString()); cur.setLength(0) } else cur.append(c)
        }
        out.add(cur.toString())
        return out
    }
}
