// SPDX-License-Identifier: Apache-2.0
package dev.fusion.lok

/**
 * R146 — ODF `office:meta` scrubber (owner:v2.0 privacy: share-time metadata
 * removal for ODT/ODS/ODP exports, alongside the existing MAT2/EXIF/PNG
 * scrubbers).
 *
 * Replaces the single `<office:meta>…</office:meta>` element with a version
 * that keeps only the allowlisted children (`dc:title`, `dc:language`,
 * `dc:subject`, `meta:keyword`) and drops every other child element together
 * with its content — creator, dates, editing cycles/duration, generator,
 * document statistics, user-defined fields, printed-by, and comments inside
 * the block. The rest of the document is passed through byte-for-byte: this
 * kernel never touches body text, styles, or other elements.
 *
 * The block content is read by a small quoted-attribute-aware tag scanner,
 * not a regular expression: comments inside the block are dropped, elements
 * must be balanced and unnested (ODF metadata schema is flat), and any
 * imbalance refuses the whole input so a partial scrub can never be
 * published.
 *
 * Declared narrowings: input <= [MAX_CHARS] UTF-16 units; exactly one
 * `<office:meta>` open and one `</office:meta>` close tag; metadata children
 * do not nest; removed/kept child tags match `[A-Za-z0-9_.:-]+`.
 */
object OdfMetadataScrubber {
    const val SCOPE =
        "Flat-scan office:meta child scrub with allowlist retention and balance refusal; byte-preserving outside the meta block."

    const val MAX_CHARS = 2_000_000

    private val ALLOWED = setOf("dc:title", "dc:language", "dc:subject", "meta:keyword")
    private const val OPEN = "<office:meta>"
    private const val CLOSE = "</office:meta>"

    fun scrub(input: String): String {
        require(input.length <= MAX_CHARS) { "input over $MAX_CHARS chars" }
        val openAt = input.indexOf(OPEN)
        require(openAt >= 0) { "missing <office:meta>" }
        require(input.indexOf(OPEN, openAt + OPEN.length) < 0) { "duplicate <office:meta>" }
        val innerStart = openAt + OPEN.length
        val closeAt = input.indexOf(CLOSE, innerStart)
        require(closeAt >= 0) { "missing </office:meta>" }
        require(input.indexOf(CLOSE, closeAt + CLOSE.length) < 0) { "duplicate </office:meta>" }
        val inner = input.substring(innerStart, closeAt)
        val scrubbed = scrubChildren(inner)
        return input.substring(0, innerStart) + scrubbed + input.substring(closeAt)
    }

    private fun scrubChildren(inner: String): String {
        val out = StringBuilder(inner.length)
        var i = 0
        while (i < inner.length) {
            val c = inner[i]
            if (c != '<') {
                out.append(c)
                i++
                continue
            }
            if (inner.startsWith("<!--", i)) {
                val end = inner.indexOf("-->", i + 4)
                require(end >= 0) { "unterminated comment in office:meta" }
                i = end + 3
                continue
            }
            require(!inner.startsWith("</", i)) { "stray close tag in office:meta" }
            val (tag, openEnd, selfClosing) = readOpenTag(inner, i)
            if (selfClosing) {
                if (tag in ALLOWED) out.append(inner, i, openEnd)
                i = openEnd
                continue
            }
            val closeSeq = "</$tag>"
            val closeAt = inner.indexOf(closeSeq, openEnd)
            require(closeAt >= 0) { "unterminated <$tag> in office:meta" }
            val blockEnd = closeAt + closeSeq.length
            // Balance check inside the block: no further opens of any tag
            // (ODF metadata children are flat) and no other closes.
            val innerBlock = inner.substring(openEnd, closeAt)
            require(!innerBlock.contains('<')) { "nested element inside <$tag>" }
            if (tag in ALLOWED) out.append(inner, i, blockEnd)
            i = blockEnd
        }
        return out.toString()
    }

    /** Reads `<name attr…>` from `at`; returns (name, indexAfter '>', selfClosing). */
    private fun readOpenTag(s: String, at: Int): Triple<String, Int, Boolean> {
        require(s[at] == '<') { "expected '<'" }
        var j = at + 1
        val nameStart = j
        while (j < s.length && (s[j].isLetterOrDigit() || s[j] == '_' || s[j] == ':' || s[j] == '.' || s[j] == '-')) {
            j++
        }
        require(j > nameStart) { "missing tag name" }
        val name = s.substring(nameStart, j)
        require(TAG_OK.matches(name)) { "illegal tag name" }
        var inQuote: Char? = null
        while (j < s.length) {
            val ch = s[j]
            if (inQuote != null) {
                if (ch == inQuote) inQuote = null
            } else if (ch == '"' || ch == '\'') {
                inQuote = ch
            } else if (ch == '>') {
                val selfClosing = j > at + 1 && s[j - 1] == '/'
                return Triple(name, j + 1, selfClosing)
            }
            j++
        }
        throw IllegalArgumentException("unterminated tag <$name")
    }

    private val TAG_OK = Regex("[A-Za-z0-9_.:-]+")
}
