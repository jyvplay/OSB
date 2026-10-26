package dev.fusion.notes

/**
 * Inline hash-tag extraction for notes (the third namespace after
 * NoteLinks' wiki links and FrontMatter's YAML keys): a tag is '#'
 * followed by a letter then letters/digits/_/-, terminated by
 * non-tag characters; '#' mid-word (a#b) and numeric '#123' are NOT
 * tags (issue references are a different feature, named).
 *
 * Declared narrowings: ASCII tag alphabet (locale tags are an ICU
 * decision, same stance as AlphaIndex); case-insensitive identity
 * with first-seen casing kept for display.
 */
object TagExtract {
    const val SCOPE = "#letter-led ASCII tags; #123 and a#b are not tags; case-insensitive identity."
    private val TAG = Regex("(?<![\\w#])#([A-Za-z][A-Za-z0-9_-]*)")

    fun tags(text: String): List<String> {
        val seen = LinkedHashMap<String, String>()
        for (m in TAG.findAll(text)) {
            val t = m.groupValues[1]
            seen.putIfAbsent(t.lowercase(), t)
        }
        return seen.values.toList()
    }

    fun hasTag(text: String, tag: String): Boolean {
        require(tag.isNotEmpty() && tag[0].isLetter()) { "tag must start with a letter" }
        return tags(text).any { it.equals(tag, ignoreCase = true) }
    }

    fun strip(text: String): String = TAG.replace(text, "").replace(Regex("  +"), " ").trim()
}
