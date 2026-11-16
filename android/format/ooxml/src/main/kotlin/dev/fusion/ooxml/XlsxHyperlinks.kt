// SPDX-License-Identifier: Apache-2.0
package dev.fusion.ooxml

/**
 * Cell hyperlinks: the worksheet `<hyperlinks><hyperlink/></hyperlinks>` list (ECMA-376 Part 1 §18.3.1.48,
 * `CT_Hyperlink` §18.3.2.13) and the worksheet relationship part that external targets live in
 * (`xl/worksheets/_rels/sheetN.xml.rels`, OPC Part 2 §9.3).
 *
 * Two kinds, and the file states them differently:
 *
 * * **external** — `r:id` pointing at a `<Relationship Type="…/hyperlink" TargetMode="External">` in the
 *   worksheet's own relationship part; the `Target` holds the URI;
 * * **internal** — a `location` attribute such as `Sheet2!A1`, resolved inside the workbook and **not** a
 *   relationship. Excel writes no rels for these, so neither does this writer.
 *
 * Modelled: `ref` (one cell or a rectangle), exactly one of `target` / `location`, and the optional `tooltip`
 * and `display` strings. Not modelled, and not claimed: the `extLst` child; relative targets (a path to another
 * document such as `other.xlsx#Sheet1`), which need a base URI this model does not carry; and mail-client or
 * protocol handler behaviour after the click, which is the application's business, not the file's.
 */
object XlsxHyperlinks {

    /** Self-imposed model bounds, not schema bounds: they bound one attribute's size and the output. */
    const val MAX_TARGET = 2048
    const val MAX_TEXT = 255

    const val REL_NS = "http://schemas.openxmlformats.org/officeDocument/2006/relationships"
    const val PKG_REL_NS = "http://schemas.openxmlformats.org/package/2006/relationships"
    const val HYPERLINK_REL = "$REL_NS/hyperlink"

    data class Link(
        val ref: String,
        /** An absolute URI, written as an external relationship; `null` for an internal jump. */
        val target: String? = null,
        /** An in-workbook destination such as `Sheet2!A1`; `null` for an external link. */
        val location: String? = null,
        val tooltip: String? = null,
        val display: String? = null,
    ) {
        init {
            require(REF.matches(ref)) { "A hyperlink ref must be an upper-case A1 or A1:B2 reference: $ref" }
            require((target != null) != (location != null)) {
                "Exactly one of target/location must be present: Excel writes r:id or location, never both"
            }
            target?.let { require(isAbsoluteUri(it)) { "A hyperlink target must be an absolute URI: $it" } }
            location?.let { require(isLocation(it)) { "A hyperlink location must be Sheet!A1 or A1: $it" } }
            tooltip?.let { require(isText(it)) { "A hyperlink tooltip must be 1..$MAX_TEXT printable characters" } }
            display?.let { require(isText(it)) { "A hyperlink display must be 1..$MAX_TEXT printable characters" } }
        }
    }

    private val REF = Regex("^[A-Z]{1,3}[1-9][0-9]{0,6}(:[A-Z]{1,3}[1-9][0-9]{0,6})?$")
    private val SCHEME = Regex("^[A-Za-z][A-Za-z0-9+.-]{0,31}$")
    private val RANGE = Regex("^\\$?[A-Z]{1,3}\\$?[1-9][0-9]{0,6}(:\\$?[A-Z]{1,3}\\$?[1-9][0-9]{0,6})?$")
    private val SHEET_NAME = Regex("^[A-Za-z0-9_. ]{1,31}$")

    private fun isPrintable(value: String): Boolean = value.none { it.code < 0x20 || it.code == 0x7F }

    private fun isAbsoluteUri(value: String): Boolean {
        if (value.isEmpty() || value.length > MAX_TARGET || !isPrintable(value) || value.any { it == ' ' }) {
            return false
        }
        // The scheme is checked before java.net.URI so a single-letter "C:..." drive path is not read as a URI,
        // and URI parsing itself refuses malformed percent escapes, control characters and stray brackets.
        val colon = value.indexOf(':')
        if (colon <= 0 || !SCHEME.matches(value.substring(0, colon))) return false
        val uri = runCatching { java.net.URI(value) }.getOrNull() ?: return false
        return uri.isAbsolute && !uri.scheme.isNullOrEmpty()
    }

    private fun isLocation(value: String): Boolean {
        if (value.isEmpty() || value.length > MAX_TARGET || !isPrintable(value)) return false
        val bang = value.lastIndexOf('!')
        if (bang < 0) return RANGE.matches(value)
        val raw = value.substring(0, bang)
        val name = if (raw.length >= 2 && raw.startsWith("'") && raw.endsWith("'")) {
            raw.substring(1, raw.length - 1)
        } else {
            raw
        }
        if (!SHEET_NAME.matches(name) || name.contains('!')) return false
        return RANGE.matches(value.substring(bang + 1))
    }

    private fun isText(value: String): Boolean =
        value.isNotEmpty() && value.length <= MAX_TEXT && value.trim() == value && isPrintable(value)

    private fun esc(s: String): String = buildString(s.length) {
        for (ch in s) {
            when (ch) {
                '&' -> append("&amp;")
                '<' -> append("&lt;")
                '>' -> append("&gt;")
                '"' -> append("&quot;")
                else -> append(ch)
            }
        }
    }

    /**
     * One relationship per **distinct** external target, in first-use order: several cells may point at the
     * same URI and OPC only needs the relationship once. Internal links get no relationship at all.
     */
    fun relationshipIds(links: List<Link>): Map<String, String> {
        val ids = LinkedHashMap<String, String>()
        for (link in links) {
            val target = link.target ?: continue
            if (target !in ids) ids[target] = "rHl${ids.size + 1}"
        }
        return ids
    }

    fun toXml(links: List<Link>, ids: Map<String, String>): String {
        require(links.isNotEmpty()) { "An empty <hyperlinks> element is not written" }
        val external = links.any { it.target != null }
        return buildString {
            append("<hyperlinks")
            if (external) append(""" xmlns:r="$REL_NS"""")
            append(">")
            for (link in links) {
                append("""<hyperlink ref="${esc(link.ref)}"""")
                link.target?.let { target ->
                    val id = ids[target] ?: error("Missing relationship id for $target")
                    append(""" r:id="${esc(id)}"""")
                }
                link.location?.let { append(""" location="${esc(it)}"""") }
                link.tooltip?.let { append(""" tooltip="${esc(it)}"""") }
                link.display?.let { append(""" display="${esc(it)}"""") }
                append("/>")
            }
            append("</hyperlinks>")
        }
    }

    /** The worksheet relationship part: one external relationship per distinct target. */
    fun toRels(ids: Map<String, String>): ByteArray = buildString {
        append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
        append("""<Relationships xmlns="$PKG_REL_NS">""")
        for ((target, id) in ids) {
            append("""<Relationship Id="${esc(id)}" Type="$HYPERLINK_REL" Target="${esc(target)}"""")
            append(""" TargetMode="External"/>""")
        }
        append("</Relationships>")
    }.toByteArray(Charsets.UTF_8)

    /**
     * Reads the worksheet relationship part. Only `Type="…/hyperlink"` relationships are returned, so a part
     * that also carries a drawing or a comment relationship is not misread as a link target.
     */
    fun parseRels(bytes: ByteArray): Map<String, String> {
        val targets = LinkedHashMap<String, String>()
        val root = BoundedXml.parse(bytes)
        for (child in root.children.filter { it.name.local == "Relationship" }) {
            if (child.attr("Type") != HYPERLINK_REL) continue
            val id = child.attr("Id") ?: continue
            val target = child.attr("Target") ?: continue
            targets[id] = target
        }
        return targets
    }

    /**
     * Reads `<hyperlinks>`. A link whose `r:id` has no hyperlink relationship in the sheet's rels part, or whose
     * shape this model refuses, is dropped rather than rewritten with a guessed destination.
     */
    fun parse(root: BoundedXml.Node, targets: Map<String, String>): List<Link> {
        val node = root.children.firstOrNull { it.name.local == "hyperlinks" } ?: return emptyList()
        val links = ArrayList<Link>()
        for (child in node.children.filter { it.name.local == "hyperlink" }) {
            val ref = child.attr("ref")?.trim()?.uppercase() ?: continue
            val id = child.attr("id", REL_NS)
            val external = id?.let { targets[it] }
            val location = child.attr("location")
            val link = runCatching {
                Link(
                    ref = ref,
                    target = external,
                    location = if (external == null) location else null,
                    tooltip = child.attr("tooltip"),
                    display = child.attr("display"),
                )
            }.getOrNull() ?: continue
            links.add(link)
        }
        return links
    }
}
