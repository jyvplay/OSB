// SPDX-License-Identifier: Apache-2.0
package dev.fusion.ooxml

import java.util.Locale

/**
 * Print layout for a worksheet: `<printOptions>` (§18.3.1.70, `CT_PrintOptions` §18.3.2.24),
 * `<pageMargins>` (§18.3.1.62, `CT_PageMargins` §18.3.2.20) and `<pageSetup>` (§18.3.1.63,
 * `CT_PageSetup` §18.3.2.21).
 *
 * Modelled: the four centring/headings/gridline switches, all six margins (**all six are required** by the
 * schema, so the defaults are written rather than omitted), orientation, paper size and print scale.
 *
 * Not modelled, and not claimed: `fitToWidth`/`fitToHeight` (they need `<sheetPr><pageSetUpPr fitToPage="1"/>`,
 * which changes `sheetPr` and is therefore out of this model's scope), `headerFooter`, `rowBreaks`/`colBreaks`,
 * `firstPageNumber`, DPI, `cellComments`, `errors` and the printer's own state. A print *record* also says
 * nothing about what a printer or a PDF exporter will do with the page.
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
    ) {
        init {
            require(orientation != null || paperSize != null || scale != null) {
                "An empty <pageSetup> element is not written"
            }
            paperSize?.let {
                require(it in MIN_PAPER_SIZE..MAX_PAPER_SIZE) { "paperSize $it outside 1..118 (ST_PaperSize)" }
            }
            scale?.let { require(it in MIN_SCALE..MAX_SCALE) { "scale $it outside 10..400" } }
        }
    }

    /** Whatever combination of the three elements a sheet declares; `null` means "write nothing". */
    data class Spec(
        val options: Options? = null,
        val margins: Margins? = null,
        val setup: Setup? = null,
    )

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
            setup.orientation?.let { append(""" orientation="${it.token}"""") }
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
        if (optionsNode == null && marginsNode == null && setupNode == null) return null

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
                Setup(
                    orientation = node.attr("orientation")?.let { Orientation.of(it) },
                    paperSize = node.attr("paperSize")?.toIntOrNull()?.takeIf { it in MIN_PAPER_SIZE..MAX_PAPER_SIZE },
                    scale = node.attr("scale")?.toIntOrNull()?.takeIf { it in MIN_SCALE..MAX_SCALE },
                )
            }.getOrNull()?.takeIf { it.orientation != null || it.paperSize != null || it.scale != null }
        }
        if (options == null && margins == null && setup == null) return null
        return Spec(options = options, margins = margins, setup = setup)
    }

    private fun isOn(value: String?): Boolean = value == "1" || value.equals("true", ignoreCase = true)
}
