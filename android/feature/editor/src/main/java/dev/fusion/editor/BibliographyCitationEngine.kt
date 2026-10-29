// SPDX-License-Identifier: Apache-2.0
package dev.fusion.editor

/**
 * Clean-room BibTeX / CSL-lite academic citation & bibliography formatter for Writer.
 *
 * Supports:
 * - Clean-room `@article`, `@inproceedings`, `@book`, `@techreport`, and `@misc` BibTeX parsing.
 * - Inline citation keys `[@knuth1981]` or `[@draschbacher2025; @wang2025]`.
 * - Two canonical styles:
 *   1. `IEEE` numbered appearance order (`[1]`, `[2, 3]`) + numbered bibliography.
 *   2. `APA7` author-year (`(Knuth & Plass, 1981)`) + alphabetical hanging-indent bibliography.
 */
object BibliographyCitationEngine {
    enum class Style { IEEE, APA7 }

    data class BibEntry(
        val citeKey: String,
        val entryType: String,
        val authors: List<Pair<String, String>>, // Pair(family, given)
        val title: String,
        val venue: String,
        val year: String,
        val volume: String? = null,
        val pages: String? = null,
        val doiOrUrl: String? = null,
    )

    data class FormattedManuscript(
        val bodyWithCitations: String,
        val bibliographyEntries: List<String>,
        val missingCiteKeys: Set<String>,
    )

    fun parseBibTeX(bibtex: String): Map<String, BibEntry> {
        val entries = LinkedHashMap<String, BibEntry>()
        val rx = Regex("""@([A-Za-z]+)\s*\{\s*([^,\s]+)\s*,(.*?)\n\}""", setOf(RegexOption.DOT_MATCHES_ALL))
        val normalized = bibtex.trim() + "\n"
        for (m in rx.findAll(normalized.replace(Regex("""\}\s*(?=@|\z)"""), "\n}"))) {
            val kind = m.groupValues[1].lowercase()
            val key = m.groupValues[2].trim()
            val body = m.groupValues[3]
            val fields = LinkedHashMap<String, String>()
            val fieldRx = Regex("""([A-Za-z0-9_-]+)\s*=\s*(?:\{([^{}]*)\}|"([^"]*)")""")
            for (fm in fieldRx.findAll(body)) {
                val fName = fm.groupValues[1].lowercase()
                val fVal = (if (fm.groupValues[2].isNotEmpty()) fm.groupValues[2] else fm.groupValues[3])
                    .replace(Regex("\\s+"), " ").trim()
                fields[fName] = fVal
            }
            val rawAuthors = fields["author"]?.split(Regex("\\s+and\\s+", RegexOption.IGNORE_CASE)).orEmpty()
                .map { it.trim() }.filter { it.isNotEmpty() }
            val parsedAuthors = rawAuthors.map { raw ->
                if (',' in raw) {
                    val p = raw.split(',', limit = 2)
                    p[0].trim() to p[1].trim()
                } else {
                    val toks = raw.split(' ')
                    toks.last() to toks.dropLast(1).joinToString(" ")
                }
            }
            entries[key] = BibEntry(
                citeKey = key,
                entryType = kind,
                authors = parsedAuthors,
                title = fields["title"] ?: "Untitled",
                venue = fields["journal"] ?: fields["booktitle"] ?: fields["publisher"] ?: "",
                year = fields["year"] ?: "n.d.",
                volume = fields["volume"],
                pages = fields["pages"],
                doiOrUrl = fields["doi"] ?: fields["url"],
            )
        }
        return entries
    }

    fun formatManuscript(body: String, catalog: Map<String, BibEntry>, style: Style): FormattedManuscript {
        val citedOrder = LinkedHashSet<String>()
        val missing = LinkedHashSet<String>()
        val citeRx = Regex("""\[(@[A-Za-z0-9_:.-]+(?:\s*;\s*@[A-Za-z0-9_:.-]+)*)\]""")

        // Pass 1: discover cited keys in appearance order
        for (m in citeRx.findAll(body)) {
            val keys = m.groupValues[1].split(';').map { it.trim().removePrefix("@") }
            for (k in keys) {
                if (k in catalog) citedOrder.add(k) else missing.add(k)
            }
        }

        val ieeeIndex = citedOrder.withIndex().associate { (i, k) -> k to (i + 1) }

        val renderedBody = citeRx.replace(body) { m ->
            val keys = m.groupValues[1].split(';').map { it.trim().removePrefix("@") }
            when (style) {
                Style.IEEE -> {
                    val labels = keys.map { k -> ieeeIndex[k]?.toString() ?: "?$k" }
                    "[${labels.joinToString(", ")}]"
                }
                Style.APA7 -> {
                    val parts = keys.map { k ->
                        val e = catalog[k]
                        if (e == null) "?$k"
                        else "${apaInlineAuthor(e.authors)}, ${e.year}"
                    }
                    "(${parts.joinToString("; ")})"
                }
            }
        }

        val bibList = when (style) {
            Style.IEEE -> citedOrder.map { k ->
                val e = catalog.getValue(k)
                val num = ieeeIndex.getValue(k)
                val auth = e.authors.joinToString(", ") { (fam, giv) ->
                    val initials = giv.split(' ').filter { it.isNotEmpty() }.joinToString(" ") { "${it[0]}." }
                    if (initials.isEmpty()) fam else "$initials $fam"
                }
                buildString {
                    append("[$num] $auth, \"${e.title},\"")
                    if (e.venue.isNotEmpty()) append(" ${e.venue},")
                    if (!e.volume.isNullOrEmpty()) append(" vol. ${e.volume},")
                    if (!e.pages.isNullOrEmpty()) append(" pp. ${e.pages},")
                    append(" ${e.year}.")
                }
            }
            Style.APA7 -> citedOrder.map { catalog.getValue(it) }
                .sortedWith(compareBy({ it.authors.firstOrNull()?.first?.lowercase() ?: "" }, { it.year }))
                .map { e ->
                    val auth = e.authors.joinToString(", ") { (fam, giv) ->
                        val initials = giv.split(' ').filter { it.isNotEmpty() }.joinToString(" ") { "${it[0]}." }
                        if (initials.isEmpty()) fam else "$fam, $initials"
                    }
                    buildString {
                        append("$auth (${e.year}). ${e.title}.")
                        if (e.venue.isNotEmpty()) append(" ${e.venue}.")
                        if (!e.doiOrUrl.isNullOrEmpty()) append(" ${e.doiOrUrl}")
                    }
                }
        }
        return FormattedManuscript(renderedBody, bibList, missing)
    }

    private fun apaInlineAuthor(authors: List<Pair<String, String>>): String = when (authors.size) {
        0 -> "Anonymous"
        1 -> authors[0].first
        2 -> "${authors[0].first} & ${authors[1].first}"
        else -> "${authors[0].first} et al."
    }
}
