package dev.fusion.editor

/**
 * Clean-room CSL-inspired Scholarly Citation & Bibliography Formatter (Apache-2.0).
 *
 * Parses clean-room BibTeX entries (`@article`, `@inproceedings`, `@book`, `@techreport`),
 * formats inline citations in `IEEE` (`[1]`, `[1]–[3]`), `APA_7` (`(Wang et al., 2022)`),
 * and `CHICAGO_NOTES` (`ibid.` collapsing on consecutive repeat citations), and emits a
 * deterministically ordered reference list.
 */
object CitationBibliographyFormatter {
    const val SCOPE =
        "BibTeX parser + IEEE numeric range / APA-7 author-date / Chicago ibid. citation & bibliography processor"

    enum class CitationStyle { IEEE, APA_7, CHICAGO_NOTES }

    data class AuthorName(val family: String, val given: String) {
        fun apaForm(): String {
            val initials = given.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
                .joinToString(" ") { "${it.first().uppercaseChar()}." }
            return if (initials.isEmpty()) family else "$family, $initials"
        }

        fun ieeeForm(): String {
            val initials = given.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
                .joinToString(" ") { "${it.first().uppercaseChar()}." }
            return if (initials.isEmpty()) family else "$initials $family"
        }
    }

    data class BibEntry(
        val key: String,
        val entryType: String,
        val authors: List<AuthorName>,
        val title: String,
        val venue: String,
        val year: Int,
        val pagesOrUrl: String = "",
    )

    data class FormattedManuscript(
        val renderedBody: String,
        val bibliography: List<String>,
        val citationOrderKeys: List<String>,
    )

    private val ENTRY_RE = Regex("@([A-Za-z]+)\\s*\\{\\s*([^,\\s]+)\\s*,([\\s\\S]*?)\\n\\}", RegexOption.MULTILINE)
    private val FIELD_RE = Regex("([A-Za-z_][A-Za-z0-9_]*)\\s*=\\s*\\{([^}]*)\\}")
    private val CITE_TAG_RE = Regex("\\[@([A-Za-z0-9_.:-]+(?:\\s*;\\s*@[A-Za-z0-9_.:-]+)*)\\]")

    fun parseBibTex(source: String): Map<String, BibEntry> {
        val normalized = source.trim() + "\n"
        val map = LinkedHashMap<String, BibEntry>()
        for (m in ENTRY_RE.findAll(normalized)) {
            val type = m.groupValues[1].lowercase()
            val key = m.groupValues[2].trim()
            val fields = FIELD_RE.findAll(m.groupValues[3]).associate {
                it.groupValues[1].lowercase() to it.groupValues[2].trim()
            }
            val rawAuthors = fields["author"] ?: "Anonymous"
            val authors = rawAuthors.split(Regex("\\s+and\\s+")).map { token ->
                val t = token.trim()
                if (',' in t) {
                    val p = t.split(',', limit = 2)
                    AuthorName(p[0].trim(), p[1].trim())
                } else {
                    val words = t.split(Regex("\\s+"))
                    if (words.size == 1) AuthorName(words[0], "")
                    else AuthorName(words.last(), words.dropLast(1).joinToString(" "))
                }
            }
            val venue = fields["booktitle"] ?: fields["journal"] ?: fields["publisher"] ?: ""
            map[key] = BibEntry(
                key = key,
                entryType = type,
                authors = authors,
                title = fields["title"] ?: key,
                venue = venue,
                year = fields["year"]?.toIntOrNull() ?: 0,
                pagesOrUrl = fields["pages"] ?: fields["url"] ?: "",
            )
        }
        return map
    }

    /** Replaces `[@key1; @key2]` markers in [markdown] and builds the matching bibliography. */
    fun formatManuscript(
        markdown: String,
        entries: Map<String, BibEntry>,
        style: CitationStyle,
    ): FormattedManuscript {
        val appearanceOrder = LinkedHashSet<String>()
        var prevSingleKey: String? = null
        var chicagoNoteNum = 0
        val chicagoNotes = mutableListOf<String>()

        val rendered = CITE_TAG_RE.replace(markdown) { match ->
            val keys = match.groupValues[1].split(';').map { it.trim().removePrefix("@") }
            for (k in keys) {
                require(k in entries) { "Missing BibTeX entry for '@$k'" }
                appearanceOrder.add(k)
            }
            when (style) {
                CitationStyle.IEEE -> {
                    val orderList = appearanceOrder.toList()
                    val nums = keys.map { orderList.indexOf(it) + 1 }.sorted()
                    formatIeeeBracketRange(nums)
                }
                CitationStyle.APA_7 -> {
                    "(" + keys.joinToString("; ") { k ->
                        val e = entries.getValue(k)
                        val lead = when (e.authors.size) {
                            0 -> "Anonymous"
                            1 -> e.authors[0].family
                            2 -> "${e.authors[0].family} & ${e.authors[1].family}"
                            else -> "${e.authors[0].family} et al."
                        }
                        "$lead, ${e.year}"
                    } + ")"
                }
                CitationStyle.CHICAGO_NOTES -> {
                    val parts = keys.map { k ->
                        chicagoNoteNum++
                        val e = entries.getValue(k)
                        val noteText = if (keys.size == 1 && k == prevSingleKey) {
                            "$chicagoNoteNum. Ibid."
                        } else {
                            "$chicagoNoteNum. ${e.authors.joinToString(", ") { "${it.given} ${it.family}".trim() }}, \"${e.title},\" ${e.venue} (${e.year})."
                        }
                        chicagoNotes.add(noteText)
                        "^$chicagoNoteNum"
                    }
                    prevSingleKey = if (keys.size == 1) keys[0] else null
                    parts.joinToString(",")
                }
            }
        }

        val bibliography = when (style) {
            CitationStyle.IEEE -> appearanceOrder.mapIndexed { idx, k ->
                val e = entries.getValue(k)
                val auth = if (e.authors.size > 3) "${e.authors[0].ieeeForm()} et al." else e.authors.joinToString(", ") { it.ieeeForm() }
                val tail = if (e.pagesOrUrl.isBlank()) "${e.year}." else "${e.year}, pp. ${e.pagesOrUrl}."
                "[${idx + 1}] $auth, \"${e.title},\" ${e.venue}, $tail"
            }
            CitationStyle.APA_7 -> appearanceOrder
                .map { entries.getValue(it) }
                .sortedWith(compareBy({ it.authors.firstOrNull()?.family ?: "" }, { it.year }, { it.title }))
                .map { e ->
                    val auth = e.authors.joinToString(", ") { it.apaForm() }
                    "$auth (${e.year}). ${e.title}. ${e.venue}."
                }
            CitationStyle.CHICAGO_NOTES -> chicagoNotes
        }
        return FormattedManuscript(rendered, bibliography, appearanceOrder.toList())
    }

    private fun formatIeeeBracketRange(sortedNums: List<Int>): String {
        if (sortedNums.isEmpty()) return "[]"
        if (sortedNums.size >= 3 && sortedNums.zipWithNext().all { (a, b) -> b == a + 1 }) {
            return "[${sortedNums.first()}]–[${sortedNums.last()}]"
        }
        return sortedNums.joinToString(", ") { "[$it]" }
    }
}
