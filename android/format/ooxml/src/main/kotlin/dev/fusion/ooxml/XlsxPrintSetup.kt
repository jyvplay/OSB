// SPDX-License-Identifier: Apache-2.0
package dev.fusion.ooxml

import java.util.Locale

/**
 * Print layout for a worksheet: `<printOptions>` (§18.3.1.70, `CT_PrintOptions` §18.3.2.24),
 * `<pageMargins>` (§18.3.1.62, `CT_PageMargins` §18.3.2.20) and `<pageSetup>` (§18.3.1.63,
 * `CT_PageSetup` §18.3.2.21).
 *
 * Modelled: the four centring/headings/gridline switches, all six margins (**all six are required** by the
 * schema, so the defaults are written rather than omitted), orientation, paper size, print scale, fit-to-page
 * (`pageSetup@fitToWidth/@fitToHeight` together with the `<sheetPr><pageSetUpPr fitToPage="1"/>` flag that
 * gives them their meaning), first page numbering, and the two sheet-scoped print *records*
 * (`_xlnm.Print_Area`, `_xlnm.Print_Titles`) expressed here as a range and a rows/columns pair.
 *
 * Not modelled, and not claimed: `headerFooter`, `rowBreaks`/`colBreaks`, DPI, `cellComments`, `errors`,
 * `crop`/`picture`, the printer's own state, and anything a printer or PDF exporter subsequently decides.
 * A print record says what the workbook asks for, never what comes out of a device.
 */
object XlsxPrintSetup {

    /** CT_PageSetup `orientation` (ST_Orientation); `default` is not written because it is the schema default. */
    enum class Orientation(val token: String) {
        PORTRAIT("portrait"),
        LANDSCAPE("landscape"),
        ;

        companion object {
            fun of(token: String): Orientation? = values().firstOrNull { it.token == token }
        }
    }

    /** Self-imposed model bounds, not schema bounds. */
    const val MAX_MARGIN_INCHES = 100.0
    const val MIN_SCALE = 10
    const val MAX_SCALE = 400
    const val MIN_PAPER_SIZE = 1
    const val MAX_PAPER_SIZE = 118

    /** ST_UnsignedInt bounds this model accepts for page counts and page numbers; 0 means "automatic". */
    const val MAX_PAGES = 32_767

    private fun isQuarterOfAnInch(value: Double): Boolean {
        if (value.isNaN() || value.isInfinite() || value < 0.0 || value > MAX_MARGIN_INCHES) return false
        val scaled = value * 10_000.0
        return kotlin.math.abs(scaled - kotlin.math.round(scaled)) < 1e-6
    }

    /** CT_PageMargins: every attribute is required, so the defaults below are what Excel itself writes. */
    data class Margins(
        val left: Double = 0.75,
        val right: Double = 0.75,
        val top: Double = 1.0,
        val bottom: Double = 1.0,
        val header: Double = 0.5,
        val footer: Double = 0.5,
    ) {
        init {
            for ((name, value) in listOf(
                "left" to left, "right" to right, "top" to top,
                "bottom" to bottom, "header" to header, "footer" to footer,
            )) {
                require(isQuarterOfAnInch(value)) {
                    "Margin $name must be 0..$MAX_MARGIN_INCHES inches with at most 4 decimals: $value"
                }
            }
        }
    }

    /** CT_PrintOptions; only the switches set to `true` are written, because `false` is the schema default. */
    data class Options(
        val horizontalCentered: Boolean = false,
        val verticalCentered: Boolean = false,
        val headings: Boolean = false,
        val gridLines: Boolean = false,
    ) {
        init {
            require(horizontalCentered || verticalCentered || headings || gridLines) {
                "An empty <printOptions> element is not written"
            }
        }
    }

    /** CT_PageSetup, restricted to the attributes this model owns. */
    data class Setup(
        val orientation: Orientation? = null,
        val paperSize: Int? = null,
        val scale: Int? = null,
        /** Pages wide when fitting to page; 0 asks Excel to derive the count from the other axis. */
        val fitToWidth: Int? = null,
        /** Pages tall when fitting to page; 0 asks Excel to derive the count from the other axis. */
        val fitToHeight: Int? = null,
        /** The number printed on the first page; only meaningful with [useFirstPageNumber]. */
        val firstPageNumber: Int? = null,
        /** CT_PageSetup `useFirstPageNumber`: when false (the schema default) Excel numbers from 1. */
        val useFirstPageNumber: Boolean = false,
    ) {
        init {
            require(orientation != null || paperSize != null || scale != null ||
                fitToWidth != null || fitToHeight != null || firstPageNumber != null) {
                "An empty <pageSetup> element is not written"
            }
            paperSize?.let {
                require(it in MIN_PAPER_SIZE..MAX_PAPER_SIZE) { "paperSize $it outside 1..118 (ST_PaperSize)" }
            }
            scale?.let { require(it in MIN_SCALE..MAX_SCALE) { "scale $it outside 10..400" } }
            fitToWidth?.let { require(it in 0..MAX_PAGES) { "fitToWidth $it outside 0..$MAX_PAGES" } }
            fitToHeight?.let { require(it in 0..MAX_PAGES) { "fitToHeight $it outside 0..$MAX_PAGES" } }
            firstPageNumber?.let {
                require(it in 1..MAX_PAGES) { "firstPageNumber $it outside 1..$MAX_PAGES" }
            }
            require(!useFirstPageNumber || firstPageNumber != null) {
                "useFirstPageNumber needs a firstPageNumber: the flag alone prints page 1"
            }
        }
    }

    /**
     * The `_xlnm.Print_Titles` record: repeating rows (`1:3`), repeating columns (`A:B`), or both. Excel keeps
     * them in one defined name, rows first, so this model keeps them together rather than as two names.
     */
    data class Titles(val rows: String? = null, val cols: String? = null) {
        init {
            require(rows != null || cols != null) { "Print titles without rows or columns repeat nothing" }
            rows?.let { text ->
                val m = requireNotNull(ROW_TITLES.matchEntire(text)) {
                    "Print title rows must be FIRST:LAST (e.g. 1:3), got '$text'"
                }
                require(m.groupValues[1].toInt() <= m.groupValues[2].toInt()) {
                    "Print title rows are reversed: '$text'"
                }
            }
            cols?.let { text ->
                val m = requireNotNull(COL_TITLES.matchEntire(text)) {
                    "Print title columns must be FIRST:LAST (e.g. A:B), got '$text'"
                }
                require(ColLetters.fromLetters(m.groupValues[1]) <= ColLetters.fromLetters(m.groupValues[2])) {
                    "Print title columns are reversed: '$text'"
                }
            }
        }
    }

    /**
     * Whatever combination of elements a sheet declares. `null` means "write nothing", and a sheet whose only
     * declaration is an area or titles still writes a `Spec`, because those records are workbook-level names.
     */
    data class Spec(
        val options: Options? = null,
        val margins: Margins? = null,
        val setup: Setup? = null,
        /** `<sheetPr><pageSetUpPr fitToPage="1"/>`: the flag that makes fitToWidth/fitToHeight mean anything. */
        val fitToPage: Boolean = false,
        /** Printed area on this sheet, in A1 form (`A1:C10`); written as the `_xlnm.Print_Area` record. */
        val area: String? = null,
        /** Repeating rows/columns; written as the `_xlnm.Print_Titles` record. */
        val titles: Titles? = null,
    ) {
        init {
            require(setup?.fitToWidth == null && setup?.fitToHeight == null || fitToPage) {
                "fitToWidth/fitToHeight are inert without fitToPage: declare fitToPage = true or drop them"
            }
            area?.let { text -> SheetRange.parse(text) }
        }

        /** Same page layout, neither print record: the named ranges are dropped, the page setup survives. */
        fun withoutRecords(): Spec = copy(area = null, titles = null)
    }

    private val ROW_TITLES = Regex("^([1-9][0-9]{0,6}):([1-9][0-9]{0,6})$")
    private val COL_TITLES = Regex("^([A-Z]{1,3}):([A-Z]{1,3})$")
    private val SHEET_NAME = Regex("^[A-Za-z0-9_.]+$")

    /**
     * A sheet name as defined-name syntax spells it: bare when it is a plain word, otherwise single-quoted with
     * any embedded quote doubled (ECMA-376 §18.17.2). Excel writes `Sheet1!$A$1` but `'My Sheet'!$A$1`.
     */
    fun quoteSheet(name: String): String =
        if (SHEET_NAME.matches(name) && name.firstOrNull()?.isDigit() != true) name
        else "'" + name.replace("'", "''") + "'"

    /**
     * `A1:C10` → `$A$1:$C$10`, the absolute spelling a print record uses. A one-cell area is spelled `$A$1`,
     * not `$A$1:$A$1`, which is what Excel writes and what every reader round-trips back to `A1`.
     */
    private fun absolute(range: String): String {
        val rangeRef = SheetRange.parse(range)
        fun cell(c: SheetRange.Cell) = "\$" + ColLetters.toLetters(c.col) + "\$" + c.row
        return if (rangeRef.top == rangeRef.bottom) cell(rangeRef.top)
        else cell(rangeRef.top) + ":" + cell(rangeRef.bottom)
    }

    /** `1:3` → `$1:$3`, `A:B` → `$A:$B`. */
    private fun absoluteTitles(text: String): String = "\$" + text.replace(":", ":\$")

    /**
     * The defined names a sheet's [Spec] contributes, as `(name, reference)` pairs. The caller attaches the
     * `localSheetId`; this type does not own workbook indexing.
     */
    fun records(sheetName: String, spec: Spec): List<Pair<String, String>> {
        val prefix = quoteSheet(sheetName) + "!"
        val out = mutableListOf<Pair<String, String>>()
        spec.area?.let { out.add("_xlnm.Print_Area" to prefix + absolute(it)) }
        spec.titles?.let { titles ->
            val parts = listOfNotNull(
                titles.rows?.let { prefix + absoluteTitles(it) },
                titles.cols?.let { prefix + absoluteTitles(it) },
            )
            out.add("_xlnm.Print_Titles" to parts.joinToString(","))
        }
        return out
    }

    /**
     * `<sheetPr>` for a sheet: `tabColor` first, then `pageSetUpPr` (CT_SheetPr's own sequence). `null` when
     * the sheet declares neither, so a sheet without a tab colour or a fit flag keeps its prior bytes.
     */
    fun sheetPrXml(tabRgb: String?, spec: Spec?): String? {
        if (tabRgb == null && spec?.fitToPage != true) return null
        return buildString {
            append("<sheetPr>")
            tabRgb?.let { append("""<tabColor rgb="FF${it.uppercase()}"/>""") }
            if (spec?.fitToPage == true) append("""<pageSetUpPr fitToPage="1"/>""")
            append("</sheetPr>")
        }
    }

    /** `%.4f` without trailing zeros, and never in exponent notation, so the bytes stay decimal. */
    private fun inches(value: Double): String =
        String.format(Locale.ROOT, "%.4f", value).trimEnd('0').trimEnd('.')

    /** `<printOptions>`, `<pageMargins>`, `<pageSetup>` — CT_Worksheet's order, in one string. */
    fun toXml(spec: Spec): String = buildString {
        spec.options?.let { options ->
            append("<printOptions")
            if (options.horizontalCentered) append(""" horizontalCentered="1"""")
            if (options.verticalCentered) append(""" verticalCentered="1"""")
            if (options.headings) append(""" headings="1"""")
            if (options.gridLines) append(""" gridLines="1"""")
            append("/>")
        }
        spec.margins?.let { margins ->
            append("""<pageMargins left="${inches(margins.left)}" right="${inches(margins.right)}"""")
            append(""" top="${inches(margins.top)}" bottom="${inches(margins.bottom)}"""")
            append(""" header="${inches(margins.header)}" footer="${inches(margins.footer)}"/>""")
        }
        spec.setup?.let { setup ->
            append("<pageSetup")
            setup.paperSize?.let { append(""" paperSize="$it"""") }
            setup.scale?.let { append(""" scale="$it"""") }
            setup.firstPageNumber?.let { append(""" firstPageNumber="$it"""") }
            setup.fitToWidth?.let { append(""" fitToWidth="$it"""") }
            setup.fitToHeight?.let { append(""" fitToHeight="$it"""") }
            setup.orientation?.let { append(""" orientation="${it.token}"""") }
            if (setup.useFirstPageNumber) append(""" useFirstPageNumber="1"""")
            append("/>")
        }
    }

    /**
     * Reads the three elements. A value this model refuses (a scale of 5, a negative margin) is replaced by the
     * schema default rather than aborting the whole sheet: a print record is not worth refusing a document over.
     * `null` means the sheet declares no print layout at all.
     */
    fun parse(root: BoundedXml.Node): Spec? {
        val optionsNode = root.children.firstOrNull { it.name.local == "printOptions" }
        val marginsNode = root.children.firstOrNull { it.name.local == "pageMargins" }
        val setupNode = root.children.firstOrNull { it.name.local == "pageSetup" }
        // The fit flags live on <sheetPr>, not on <pageSetup>: read them first, because a pageSetup that asks
        // to fit to page while sheetPr leaves the flag off is inert, and this model refuses inert numbers.
        val fitToPage = isOn(
            root.children.firstOrNull { it.name.local == "sheetPr" }
                ?.children?.firstOrNull { it.name.local == "pageSetUpPr" }?.attr("fitToPage"),
        )
        if (optionsNode == null && marginsNode == null && setupNode == null && !fitToPage) return null

        val flags = listOf("horizontalCentered", "verticalCentered", "headings", "gridLines")
            .associateWith { isOn(optionsNode?.attr(it)) }
        val options = if (flags.values.any { it }) {
            Options(flags.getValue("horizontalCentered"), flags.getValue("verticalCentered"),
                flags.getValue("headings"), flags.getValue("gridLines"))
        } else {
            null
        }

        val defaults = Margins()
        val margins = marginsNode?.let { node ->
            runCatching {
                Margins(
                    left = node.attr("left")?.toDoubleOrNull() ?: defaults.left,
                    right = node.attr("right")?.toDoubleOrNull() ?: defaults.right,
                    top = node.attr("top")?.toDoubleOrNull() ?: defaults.top,
                    bottom = node.attr("bottom")?.toDoubleOrNull() ?: defaults.bottom,
                    header = node.attr("header")?.toDoubleOrNull() ?: defaults.header,
                    footer = node.attr("footer")?.toDoubleOrNull() ?: defaults.footer,
                )
            }.getOrElse { defaults }
        }

        val setup = setupNode?.let { node ->
            runCatching {
                val firstPage = node.attr("firstPageNumber")?.toIntOrNull()?.takeIf { it in 1..MAX_PAGES }
                Setup(
                    orientation = node.attr("orientation")?.let { Orientation.of(it) },
                    paperSize = node.attr("paperSize")?.toIntOrNull()?.takeIf { it in MIN_PAPER_SIZE..MAX_PAPER_SIZE },
                    scale = node.attr("scale")?.toIntOrNull()?.takeIf { it in MIN_SCALE..MAX_SCALE },
                    // Ignored by Excel unless pageSetUpPr asks for it, so a foreign file that sets only these
                    // is read with them dropped rather than modelled as if they did something.
                    fitToWidth = node.attr("fitToWidth")?.toIntOrNull()
                        ?.takeIf { it in 0..MAX_PAGES && fitToPage },
                    fitToHeight = node.attr("fitToHeight")?.toIntOrNull()
                        ?.takeIf { it in 0..MAX_PAGES && fitToPage },
                    firstPageNumber = firstPage,
                    useFirstPageNumber = isOn(node.attr("useFirstPageNumber")) && firstPage != null,
                )
            }.getOrNull()?.takeIf {
                it.orientation != null || it.paperSize != null || it.scale != null ||
                    it.fitToWidth != null || it.fitToHeight != null || it.firstPageNumber != null
            }
        }
        if (options == null && margins == null && setup == null && !fitToPage) return null
        return Spec(options = options, margins = margins, setup = setup, fitToPage = fitToPage)
    }

    /** `Sheet1!$A$1:$C$10` (or `'My Sheet'!$A$1`) → `A1:C10`; `null` when the prefix names another sheet. */
    fun areaOf(sheetName: String, reference: String): String? {
        val bare = stripSheet(sheetName, reference) ?: return null
        return runCatching { SheetRange.parse(bare.replace("$", "")).toA1() }.getOrNull()
    }

    /** The inverse of [records]' titles form: `Sheet1!$1:$3,Sheet1!$A:$B` → `Titles("1:3", "A:B")`. */
    fun titlesOf(sheetName: String, reference: String): Titles? {
        var rows: String? = null
        var cols: String? = null
        for (part in reference.split(',')) {
            val bare = stripSheet(sheetName, part.trim()) ?: return null
            val text = bare.replace("$", "")
            when {
                ROW_TITLES.matches(text) -> rows = text
                COL_TITLES.matches(text) -> cols = text
                else -> return null
            }
        }
        return runCatching { Titles(rows, cols) }.getOrNull()
    }

    /** Drops a leading `Sheet!`/`'Sheet'!`; refuses (null) when the prefix names a different sheet. */
    private fun stripSheet(sheetName: String, part: String): String? {
        val bang = part.lastIndexOf('!')
        if (bang < 0) return part
        val raw = part.substring(0, bang)
        val name = if (raw.length >= 2 && raw.startsWith("'") && raw.endsWith("'")) {
            raw.substring(1, raw.length - 1).replace("''", "'")
        } else {
            raw
        }
        if (!name.equals(sheetName, ignoreCase = true)) return null
        return part.substring(bang + 1)
    }

    private fun isOn(value: String?): Boolean = value == "1" || value.equals("true", ignoreCase = true)
}
