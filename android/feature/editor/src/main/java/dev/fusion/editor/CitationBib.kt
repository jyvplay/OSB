// SPDX-License-Identifier: Apache-2.0
package dev.fusion.editor

/**
 * Academic & technical Writer BibTeX citation parser, inline `[@key, p. 12]` resolver,
 * and deterministic IEEE `[1]` / APA `(Author, Year)` bibliography formatter.
 */
object CitationBib {
    const val SCOPE =
        "BibTeX subset (@article/@book/@inproceedings/@techreport/@misc) parser, Pandoc-style [@key, locator] citation resolver, and IEEE/APA bibliography formatter; full LaTeX macro expansion and CSL XML locales out of scope."

    const val MAX_BIB_CHARS = 262_144
    private val ALLOWED_TYPES = setOf("article", "book", "inproceedings", "techreport", "misc")
    private val CITE_GROUP_RE = Regex("""\[((?:\s*@[A-Za-z0-9_.:-]+(?:\s*,\s*[^;\]]+)?\s*;?)+)]""")
    private val CITE_ITEM_RE = Regex("""@([A-Za-z0-9_.:-]+)(?:\s*,\s*(.+))?""")

    enum class Style { IEEE_NUMERIC, APA_AUTHOR_YEAR }

    data class BibEntry(
        val type: String,
        val key: String,
        val fields: Map<String, String>,
    ) {
        fun primarySurname(): String {
            val raw = fields["author"] ?: fields["editor"] ?: "Anonymous"
            val firstAuthor = raw.split(Regex("\\s+and\\s+", RegexOption.IGNORE_CASE)).first().trim()
            return if (',' in firstAuthor) firstAuthor.substringBefore(',').trim()
            else firstAuthor.split(Regex("\\s+")).last()
        }

        fun yearOrNd(): String = fields["year"]?.trim()?.ifEmpty { "n.d." } ?: "n.d."
        fun title(): String = fields["title"]?.trim().orEmpty()
    }

    data class ResolvedProse(
        val renderedText: String,
        val bibliography: List<String>,
        val citedKeysInOrder: List<String>,
        val missingKeys: Set<String>,
    )

    fun parseBibtex(src: String): Map<String, BibEntry> {
        require(src.length <= MAX_BIB_CHARS) { "BibTeX input exceeds $MAX_BIB_CHARS chars" }
        val entries = LinkedHashMap<String, BibEntry>()
        var i = 0
        while (i < src.length) {
            val at = src.indexOf('@', i)
            if (at < 0) break
            var j = at + 1
            while (j < src.length && src[j].isLetter()) j++
            val rawType = src.substring(at + 1, j).lowercase()
            while (j < src.length && src[j].isWhitespace()) j++
            require(j < src.length && src[j] == '{') { "expected '{' after @$rawType at offset $at" }
            if (rawType == "comment") {
                i = skipBalancedBraces(src, j)
                continue
            }
            require(rawType in ALLOWED_TYPES) { "unsupported BibTeX entry type '@$rawType'" }
            val bodyEnd = skipBalancedBraces(src, j)
            val inner = src.substring(j + 1, bodyEnd - 1)
            val comma = inner.indexOf(',')
            require(comma > 0) { "missing citation key comma in @$rawType" }
            val key = inner.substring(0, comma).trim()
            require(key.matches(Regex("[A-Za-z0-9_.:-]+"))) { "invalid BibTeX key '$key'" }
            require(key !in entries) { "duplicate BibTeX key '$key'" }
            val fields = parseFields(inner.substring(comma + 1))
            require(!fields["title"].isNullOrBlank()) { "BibTeX entry '$key' missing required title" }
            entries[key] = BibEntry(rawType, key, fields)
            i = bodyEnd
        }
        return entries
    }

    fun resolveCitations(
        prose: String,
        library: Map<String, BibEntry>,
        style: Style = Style.IEEE_NUMERIC,
    ): ResolvedProse {
        val citedOrder = ArrayList<String>()
        val numberByKey = LinkedHashMap<String, Int>()
        val missing = LinkedHashSet<String>()

        val rendered = CITE_GROUP_RE.replace(prose) { groupMatch ->
            val parts = groupMatch.groupValues[1].split(';').map { it.trim() }.filter { it.isNotEmpty() }
            val formattedItems = parts.map { rawItem ->
                val m = CITE_ITEM_RE.matchEntire(rawItem) ?: return@replace groupMatch.value
                val key = m.groupValues[1]
                val locator = m.groupValues[2].trim()
                val entry = library[key]
                if (entry == null) {
                    missing.add(key)
                    "?$key"
                } else {
                    val num = numberByKey.getOrPut(key) {
                        citedOrder.add(key)
                        citedOrder.size
                    }
                    when (style) {
                        Style.IEEE_NUMERIC -> if (locator.isEmpty()) "$num" else "$num, $locator"
                        Style.APA_AUTHOR_YEAR -> {
                            val base = "${entry.primarySurname()}, ${entry.yearOrNd()}"
                            if (locator.isEmpty()) base else "$base, $locator"
                        }
                    }
                }
            }
            when (style) {
                Style.IEEE_NUMERIC -> "[${formattedItems.joinToString("; ")}]"
                Style.APA_AUTHOR_YEAR -> "(${formattedItems.joinToString("; ")})"
            }
        }

        val bibLines = when (style) {
            Style.IEEE_NUMERIC -> citedOrder.mapIndexed { idx, key ->
                val e = library.getValue(key)
                val venue = e.fields["journal"] ?: e.fields["booktitle"] ?: e.fields["publisher"] ?: ""
                val venueSuffix = if (venue.isNotBlank()) " $venue," else ""
                "[${idx + 1}] ${e.fields["author"] ?: "Anonymous"}, \"${e.title()},\"$venueSuffix ${e.yearOrNd()}."
            }
            Style.APA_AUTHOR_YEAR -> citedOrder
                .sortedWith(compareBy<String>({ library.getValue(it).primarySurname().lowercase() }, { library.getValue(it).yearOrNd() }))
                .map { key ->
                    val e = library.getValue(key)
                    val venue = e.fields["journal"] ?: e.fields["booktitle"] ?: e.fields["publisher"] ?: ""
                    val venuePart = if (venue.isNotBlank()) " $venue." else ""
                    "${e.primarySurname()} (${e.yearOrNd()}). ${e.title()}.$venuePart"
                }
        }
        return ResolvedProse(rendered, bibLines, citedOrder, missing)
    }

    private fun skipBalancedBraces(s: String, openIdx: Int): Int {
        var depth = 0
        var i = openIdx
        while (i < s.length) {
            when (s[i]) {
                '{' -> depth++
                '}' -> {
                    depth--
                    if (depth == 0) return i + 1
                }
            }
            i++
        }
        throw IllegalArgumentException("unclosed '{' starting at $openIdx")
    }

    private fun parseFields(src: String): Map<String, String> {
        val out = LinkedHashMap<String, String>()
        var i = 0
        while (i < src.length) {
            while (i < src.length && (src[i].isWhitespace() || src[i] == ',')) i++
            if (i >= src.length) break
            val nameStart = i
            while (i < src.length && (src[i].isLetterOrDigit() || src[i] in "_-")) i++
            val name = src.substring(nameStart, i).lowercase()
            require(name.isNotEmpty()) { "expected field name in BibTeX entry" }
            while (i < src.length && src[i].isWhitespace()) i++
            require(i < src.length && src[i] == '=') { "expected '=' after BibTeX field '$name'" }
            i++
            while (i < src.length && src[i].isWhitespace()) i++
            require(i < src.length) { "missing value for BibTeX field '$name'" }
            val rawVal: String
            when (src[i]) {
                '{' -> {
                    val end = skipBalancedBraces(src, i)
                    rawVal = src.substring(i + 1, end - 1)
                    i = end
                }
                '"' -> {
                    val sb = StringBuilder()
                    i++
                    while (i < src.length && src[i] != '"') {
                        if (src[i] == '\\' && i + 1 < src.length) {
                            sb.append(src[i + 1]); i += 2
                        } else {
                            sb.append(src[i]); i++
                        }
                    }
                    require(i < src.length && src[i] == '"') { "unclosed quote in field '$name'" }
                    i++
                    rawVal = sb.toString()
                }
                else -> {
                    val vStart = i
                    while (i < src.length && src[i] != ',' && !src[i].isWhitespace()) i++
                    rawVal = src.substring(vStart, i)
                }
            }
            out[name] = rawVal.replace("{", "").replace("}", "").trim().replace(Regex("\\s+"), " ")
        }
        return out
    }
}
