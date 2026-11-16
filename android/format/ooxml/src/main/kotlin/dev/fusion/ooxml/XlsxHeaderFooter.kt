// SPDX-License-Identifier: Apache-2.0
package dev.fusion.ooxml

/**
 * Page headers and footers: `<headerFooter>` (ECMA-376 Part 1 §18.3.1.46, `CT_HeaderFooter` §18.3.2.12)
 * with its six text slots and four switches.
 *
 * Modelled: every slot in the schema's order, the `&L` / `&C` / `&R` section markers, the field and
 * emphasis codes this writer can validate, and the three flags Excel stores as attributes. A slot's text is
 * *code text*: it is stored exactly as Excel stores it (`&LPage &P of &N`), not as rendered output, so this
 * model says nothing about what a printer draws.
 *
 * Not modelled, and not claimed: `&K` (colour), `&G` (picture) and `&Z` (theme colour). Each of those names
 * something this writer does not produce — a validated colour index, a drawing part, or a theme colour — and
 * accepting the code unvalidated would be a promise the file cannot keep. They are refused with a message
 * rather than emitted blind. `_x000D_` escaping, control characters and right-to-left runs are likewise out.
 */
object XlsxHeaderFooter {

    /** ECMA-376 `ST_Xstring` cap for header/footer text; the schema type is unbounded, Excel is not. */
    const val MAX_TEXT = 255

    /** The six slots, in `CT_HeaderFooter` sequence order — the order they must be written in. */
    enum class Slot(val token: String) {
        ODD_HEADER("oddHeader"),
        ODD_FOOTER("oddFooter"),
        EVEN_HEADER("evenHeader"),
        EVEN_FOOTER("evenFooter"),
        FIRST_HEADER("firstHeader"),
        FIRST_FOOTER("firstFooter"),
        ;

        val isEven: Boolean get() = this == EVEN_HEADER || this == EVEN_FOOTER
        val isFirst: Boolean get() = this == FIRST_HEADER || this == FIRST_FOOTER

        companion object {
            fun of(token: String): Slot? = values().firstOrNull { it.token == token }
        }
    }

    /** One slot's text. Refused here, at construction, so no unwritable slot can reach the writer. */
    data class Text(val slot: Slot, val text: String) {
        init {
            require(text.isNotEmpty()) { "A ${slot.token} may not be empty" }
            require(text.length <= MAX_TEXT) {
                "A ${slot.token} may hold at most $MAX_TEXT characters, got ${text.length}"
            }
            // A header/footer slot opens with a section marker. Text that starts with anything else is not
            // assigned to a section by any reader we can test against — openpyxl drops the whole element with
            // "Cannot parse header or footer so it will be ignored" — so it is refused rather than written.
            require(isSectionMarker(text)) { "A ${slot.token} must open with &L, &C or &R: $text" }
            requireCodes(slot, text)
        }
    }

    /**
     * A sheet's header/footer block. `null` flags are "not declared": `scaleWithDoc` and `alignWithMargins`
     * both default to `true` in the schema, so `true` is not written and `false` is written as `0`.
     */
    data class Spec(
        val texts: List<Text> = emptyList(),
        val differentOddEven: Boolean = false,
        val differentFirst: Boolean = false,
        val scaleWithDoc: Boolean? = null,
        val alignWithMargins: Boolean? = null,
    ) {
        init {
            require(texts.isNotEmpty() || differentOddEven || differentFirst) {
                "An empty <headerFooter> element is not written"
            }
            require(texts.distinctBy { it.slot }.size == texts.size) {
                "A header/footer slot may appear only once: ${texts.map { it.slot.token }}"
            }
            require(texts.size <= Slot.values().size) { "At most ${Slot.values().size} header/footer slots" }
            // Self-imposed model rule, not a schema rule: Excel accepts these flags with no even/first text
            // and then renders blank pages. This writer refuses a flag that cannot change a single page,
            // because the file would be stating an intent the sheet has no content for.
            if (differentOddEven) {
                require(texts.any { it.slot.isEven }) {
                    "differentOddEven needs at least one evenHeader or evenFooter"
                }
            }
            if (differentFirst) {
                require(texts.any { it.slot.isFirst }) {
                    "differentFirst needs at least one firstHeader or firstFooter"
                }
            }
        }

        /** The slots actually declared, in schema order — what the writer emits and the reader returns. */
        val ordered: List<Text> get() = texts.sortedBy { it.slot.ordinal }
    }

    /**
     * `L`, `C` and `R` open the left/centre/right section and may each appear once, in that order; `P`, `N`,
     * `D`, `T`, `F`, `A` are fields; `B`, `I`, `U`, `E`, `S`, `X`, `Y` are emphasis. A literal ampersand is
     * `&&`, and `&"font,style"` and a 1–2 digit font size are accepted. Everything else is refused.
     */
    private const val LETTER_CODES = "LCRPNDTFABIUESXY"
    private const val SECTION_CODES = "LCR"

    private fun isSectionMarker(text: String): Boolean =
        text.length >= 2 && text[0] == '&' && text[1].uppercaseChar() in SECTION_CODES

    private fun requireCodes(slot: Slot, text: String) {
        var i = 0
        var seen = 0
        var lastSection = -1
        while (i < text.length) {
            if (text[i] != '&') {
                i++
                continue
            }
            require(i + 1 < text.length) { "A ${slot.token} may not end with a bare '&': $text" }
            val code = text[i + 1].uppercaseChar()
            when {
                code == '&' -> i += 2
                code in SECTION_CODES -> {
                    val index = SECTION_CODES.indexOf(code)
                    require(seen and (1 shl index) == 0) { "Section &$code appears twice in ${slot.token}: $text" }
                    require(index > lastSection) {
                        "Sections must appear as &L, then &C, then &R in ${slot.token}: $text"
                    }
                    seen = seen or (1 shl index)
                    lastSection = index
                    i += 2
                }
                code == '"' -> {
                    val close = text.indexOf('"', i + 2)
                    require(close > i + 1) { "An &\"font,style\" code must be closed in ${slot.token}: $text" }
                    i = close + 1
                }
                code.isDigit() -> {
                    var j = i + 1
                    var digits = 0
                    while (j < text.length && text[j].isDigit() && digits < 2) {
                        j++
                        digits++
                    }
                    i = j
                }
                code in LETTER_CODES -> i += 2
                code == 'K' -> error("&K colour is not modelled and is refused, not emitted unchecked: $text")
                code == 'G' -> error("&G pictures are not modelled and are refused, not emitted unchecked: $text")
                code == 'Z' -> error("&Z theme colours are not modelled and are refused, not emitted unchecked: $text")
                else -> error("Unknown header/footer code &${text[i + 1]} in ${slot.token}: $text")
            }
        }
    }

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

    /** `<headerFooter …>` in `CT_HeaderFooter` attribute-then-child order; callers pass a validated [Spec]. */
    fun toXml(spec: Spec): String = buildString {
        append("<headerFooter")
        if (spec.differentOddEven) append(""" differentOddEven="1"""")
        if (spec.differentFirst) append(""" differentFirst="1"""")
        spec.scaleWithDoc?.let { append(""" scaleWithDoc="${if (it) 1 else 0}"""") }
        spec.alignWithMargins?.let { append(""" alignWithMargins="${if (it) 1 else 0}"""") }
        append(">")
        for (text in spec.ordered) append("<${text.slot.token}>${esc(text.text)}</${text.slot.token}>")
        append("</headerFooter>")
    }

    /**
     * Reads `<headerFooter>`. A slot whose text this model refuses (too long, an unmodelled code, a repeated
     * section) is dropped rather than repaired; a flag whose content did not survive is dropped with it. If
     * nothing usable remains, the sheet is read as having no header/footer at all.
     */
    fun parse(root: BoundedXml.Node): Spec? {
        val node = root.children.firstOrNull { it.name.local == "headerFooter" } ?: return null
        val texts = ArrayList<Text>()
        for (slot in Slot.values()) {
            if (texts.size >= Slot.values().size) break
            val child = node.children.firstOrNull { it.name.local == slot.token } ?: continue
            val raw = child.text.orEmpty()
            if (raw.isEmpty()) continue
            texts.add(runCatching { Text(slot, raw) }.getOrNull() ?: continue)
        }
        val differentOddEven = isOn(node.attr("differentOddEven"))
        val differentFirst = isOn(node.attr("differentFirst"))
        val hasEven = texts.any { it.slot.isEven }
        val hasFirst = texts.any { it.slot.isFirst }
        val scaleWithDoc = when (node.attr("scaleWithDoc")?.lowercase()) {
            null -> null
            "1", "true" -> true
            else -> false
        }
        val alignWithMargins = when (node.attr("alignWithMargins")?.lowercase()) {
            null -> null
            "1", "true" -> true
            else -> false
        }
        if (texts.isEmpty() && !(differentOddEven && hasEven) && !(differentFirst && hasFirst)) return null
        return Spec(
            texts = texts,
            differentOddEven = differentOddEven && hasEven,
            differentFirst = differentFirst && hasFirst,
            scaleWithDoc = scaleWithDoc,
            alignWithMargins = alignWithMargins,
        )
    }

    private fun isOn(value: String?): Boolean = value == "1" || value.equals("true", ignoreCase = true)
}
