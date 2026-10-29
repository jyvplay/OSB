// SPDX-License-Identifier: Apache-2.0
package dev.fusion.documents

/**
 * Pure-JVM **Citation & Bibliography CSL-Style Formatter (`APA-7`, `IEEE`, `MLA-9`)** for `:feature:docs`,
 * resolving inline `[@citeKey]` references into numbered `[1]` or author-year `(Cronin et al., 2021)`
 * citations and deterministic Reference Lists.
 */
object DocsBibliographyCiteFormatter {
    const val SCOPE =
        "Docs inline [@citeKey] resolver and deterministic APA-7, IEEE, and MLA-9 bibliography section generator."

    enum class CitationStyle { IEEE, APA_7, MLA_9 }

    data class BibEntry(
        val key: String,
        val authors: List<String>, // e.g., ["Cronin, P.", "Gao, X.", "Wang, H."]
        val title: String,
        val venue: String,
        val year: Int,
        val urlOrDoi: String? = null,
    )

    data class FormattedDocumentCitations(
        val bodyWithInlineCitations: String,
        val bibliographyLines: List<String>,
        val missingCiteKeys: Set<String>,
    )

    fun formatDocument(
        markdownBody: String,
        entries: List<BibEntry>,
        style: CitationStyle = CitationStyle.IEEE,
    ): FormattedDocumentCitations {
        val byKey = entries.associateBy { it.key }
        val encounterOrder = LinkedHashSet<String>()
        val missing = LinkedHashSet<String>()
        val citeRegex = Regex("""\[@([A-Za-z0-9_:-]+)\]""")

        citeRegex.findAll(markdownBody).forEach { m ->
            val k = m.groupValues[1]
            if (k in byKey) encounterOrder.add(k) else missing.add(k)
        }

        val orderedKeys = when (style) {
            CitationStyle.IEEE -> encounterOrder.toList()
            CitationStyle.APA_7, CitationStyle.MLA_9 -> encounterOrder.sortedBy { byKey[it]!!.authors.firstOrNull().orEmpty() }
        }
        val ieeeRank = orderedKeys.withIndex().associate { (i, k) -> k to (i + 1) }

        val replaced = citeRegex.replace(markdownBody) { m ->
            val k = m.groupValues[1]
            val e = byKey[k] ?: return@replace "[?$k?]"
            when (style) {
                CitationStyle.IEEE -> "[${ieeeRank[k]}]"
                CitationStyle.APA_7 -> "(${shortAuthor(e.authors)}, ${e.year})"
                CitationStyle.MLA_9 -> "(${shortAuthor(e.authors)})"
            }
        }

        val bibLines = orderedKeys.mapIndexed { idx, k ->
            val e = byKey.getValue(k)
            val authStr = e.authors.joinToString(", ")
            val suffix = e.urlOrDoi?.let { " $it" }.orEmpty()
            when (style) {
                CitationStyle.IEEE -> "[${idx + 1}] $authStr, \"${e.title},\" ${e.venue}, ${e.year}.$suffix"
                CitationStyle.APA_7 -> "$authStr (${e.year}). ${e.title}. ${e.venue}.$suffix"
                CitationStyle.MLA_9 -> "$authStr. \"${e.title}.\" ${e.venue}, ${e.year}.$suffix"
            }
        }
        return FormattedDocumentCitations(replaced, bibLines, missing)
    }

    private fun shortAuthor(authors: List<String>): String {
        if (authors.isEmpty()) return "Anon."
        val surname = authors[0].substringBefore(',').trim()
        return when (authors.size) {
            1 -> surname
            2 -> "$surname & ${authors[1].substringBefore(',').trim()}"
            else -> "$surname et al."
        }
    }
}
