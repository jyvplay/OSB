// SPDX-License-Identifier: Apache-2.0
package dev.fusion.testing

import dev.fusion.ooxml.SheetRange
import dev.fusion.ooxml.SerialDate
import dev.fusion.ooxml.XlsxConditionalFormatVerdict
import dev.fusion.ooxml.XlsxConditionalFormatVerdict.Calendar
import dev.fusion.ooxml.XlsxConditionalFormatVerdict.Outcome
import dev.fusion.ooxml.XlsxConditionalFormatVerdict.Value
import dev.fusion.ooxml.XlsxConditionalFormatVerdict.WeekStart
import dev.fusion.ooxml.XlsxConditionalFormatting
import dev.fusion.ooxml.XlsxConditionalFormatting.Block
import dev.fusion.ooxml.XlsxConditionalFormatting.Format
import dev.fusion.ooxml.XlsxConditionalFormatting.IconSet
import dev.fusion.ooxml.XlsxConditionalFormatting.Kind
import dev.fusion.ooxml.XlsxConditionalFormatting.Operator
import dev.fusion.ooxml.XlsxConditionalFormatting.Rule
import dev.fusion.ooxml.XlsxConditionalFormatting.Threshold
import dev.fusion.ooxml.XlsxConditionalFormatting.ThresholdKind
import dev.fusion.ooxml.XlsxConditionalFormatting.TimePeriod
import dev.fusion.ooxml.XlsxStyledBook
import java.io.File

/**
 * R101 spreadsheet contracts: the last six `cfRule` kinds — `containsBlanks`, `notContainsBlanks`,
 * `containsErrors`, `notContainsErrors`, `timePeriod` and `iconSet` — which together with R96's five and R100's
 * seven complete the eighteen kinds the base schema defines.
 *
 * Four things are asserted, each by a mechanism that could fail independently:
 *
 * 1. **The file.** A sheet carrying one rule of every kind (`AllKinds`) is written and its exact attributes are
 *    read back out of the ZIP: `timePeriod="today"`, a self-closing `containsBlanks`, an `<iconSet>` child with
 *    `iconCount - 1` boundaries and only the flags that differ from the schema defaults.
 * 2. **The reader.** `XlsxStyledBook.read` must reproduce all nineteen rules field for field, and a hand-built
 *    *foreign* package must survive where it is legal (Excel's one-`cfvo`-per-icon form, `percent="0"`,
 *    `showValue="0"`, `reverse="1"`) and be refused where it is ambiguous (a leading boundary that is not the
 *    range minimum, an `x14` set name, an unknown `timePeriod` token).
 * 3. **The model.** Sixteen shapes that look like a rule but are not one are refused with a message.
 * 4. **The evaluator.** Blank/error predicates over six value shapes, icon indices over three sets including a
 *    five-icon set and a reversed set, and ten `timePeriod` presets against a supplied reference date — plus the
 *    two cases where the answer *must* be withheld: no calendar at all, and an out-of-range serial.
 *
 * Host JVM only. No Excel, LibreOffice, renderer or device ran: an icon index is a statement about which icon a
 * value earns, never about the pixels that draw it.
 */
object R101Checks {
    private var assertions = 0
    private var refusals = 0

    private inline fun check(ok: Boolean, message: () -> String) {
        assertions++
        require(ok) { "FAILED: " + message() }
    }

    private fun text(bytes: ByteArray): String = String(bytes, Charsets.ISO_8859_1)

    private fun partOrNull(bytes: ByteArray, entry: String): String? {
        var found: String? = null
        java.util.zip.ZipInputStream(java.io.ByteArrayInputStream(bytes)).use { input ->
            while (found == null) {
                val current = input.nextEntry ?: break
                val payload = input.readBytes()
                if (current.name == entry) found = text(payload)
            }
        }
        return found
    }

    private fun part(bytes: ByteArray, entry: String): String =
        partOrNull(bytes, entry) ?: error("missing OPC part $entry")

    private fun rejects(name: String, block: () -> Unit) {
        assertions++
        refusals++
        val threw = try {
            block()
            false
        } catch (expected: IllegalArgumentException) {
            check(expected.message?.isNotBlank() == true) { "$name refused without a message" }
            true
        }
        require(threw) { "FAILED: $name was accepted" }
    }

    // ---------------------------------------------------------------- formats

    private val RED = Format(fillRgb = "FFC7CE")
    private val GREEN = Format(fillRgb = "C6EFCE")
    private val BOLD = Format(bold = true)
    private val ITALIC = Format(italic = true)
    private val UNDERLINE = Format(underline = true)
    private val STRIKE = Format(strike = true)

    private fun yes(value: Boolean?): String = when (value) {
        null -> "-"
        true -> "1"
        false -> "0"
    }

    private fun token(format: Format?): String {
        if (format == null) return "-"
        val parts = ArrayList<String>()
        if (format.bold) parts.add("bold")
        if (format.italic) parts.add("italic")
        if (format.strike) parts.add("strike")
        if (format.underline) parts.add("underline")
        format.fontRgb?.let { parts.add("font=$it") }
        format.fillRgb?.let { parts.add("fill=$it") }
        return parts.joinToString(";")
    }

    private fun thresholds(rule: Rule): String =
        rule.thresholds.joinToString(";") { threshold ->
            threshold.value?.let { "${threshold.kind.token}:${it}" } ?: threshold.kind.token
        }.ifEmpty { "-" }

    /** Every field of a rule, in one string, so the round trip can be compared without trusting equals(). */
    private fun description(rule: Rule): String = listOf(
        rule.priority.toString(),
        rule.kind.token,
        rule.text ?: "-",
        yes(rule.aboveAverage),
        yes(rule.equalAverage),
        rule.stdDev?.toString() ?: "-",
        rule.operator?.token ?: "-",
        rule.rank?.toString() ?: "-",
        rule.formulas.joinToString(",").ifEmpty { "-" },
        rule.timePeriod?.token ?: "-",
        rule.iconSet?.token ?: "-",
        yes(rule.showValue),
        yes(rule.percent),
        yes(rule.reverse),
        thresholds(rule),
        rule.colors.joinToString(",").ifEmpty { "-" },
        token(rule.format),
    ).joinToString("|")

    // ---------------------------------------------------------------- the eighteen kinds

    /** One rule per kind, priorities 1..19 (iconSet appears twice: with and without its optional flags). */
    private val ALL_KINDS = listOf(
        Block(
            "A1:A9",
            listOf(
                Rule(
                    kind = Kind.CELL_IS, priority = 1, operator = Operator.GREATER_THAN,
                    formulas = listOf("5"), format = RED,
                ),
                Rule(kind = Kind.EXPRESSION, priority = 2, formulas = listOf("MOD(ROW(),2)=0"), format = BOLD),
                Rule(kind = Kind.TOP10, priority = 3, rank = 3, percent = false, bottom = false, format = GREEN),
                Rule(
                    kind = Kind.COLOR_SCALE, priority = 4,
                    thresholds = listOf(Threshold(ThresholdKind.MIN), Threshold(ThresholdKind.MAX)),
                    colors = listOf("F8696B", "63BE7B"),
                ),
                Rule(
                    kind = Kind.DATA_BAR, priority = 5,
                    thresholds = listOf(Threshold(ThresholdKind.NUM, 0.0), Threshold(ThresholdKind.NUM, 100.0)),
                    colors = listOf("638EC6"),
                ),
                Rule(kind = Kind.TEXT_CONTAINS, priority = 6, text = "ap*e", format = RED),
                Rule(kind = Kind.TEXT_NOT_CONTAINS, priority = 7, text = "a*a", format = BOLD),
                Rule(kind = Kind.TEXT_BEGINS_WITH, priority = 8, text = "Ap", format = GREEN),
                Rule(kind = Kind.TEXT_ENDS_WITH, priority = 9, text = "e", format = ITALIC),
                Rule(kind = Kind.ABOVE_AVERAGE, priority = 10, stdDev = 1, format = RED),
                Rule(kind = Kind.DUPLICATE, priority = 11, format = UNDERLINE),
                Rule(kind = Kind.UNIQUE, priority = 12, format = STRIKE),
                Rule(kind = Kind.CONTAINS_BLANKS, priority = 13, format = BOLD),
                Rule(kind = Kind.NOT_CONTAINS_BLANKS, priority = 14, format = ITALIC),
                Rule(kind = Kind.CONTAINS_ERRORS, priority = 15, format = RED),
                Rule(kind = Kind.NOT_CONTAINS_ERRORS, priority = 16, format = GREEN),
                Rule(kind = Kind.TIME_PERIOD, priority = 17, timePeriod = TimePeriod.TODAY, format = RED),
                // No dxf: an icon set paints its own icons, and the format is optional on this kind.
                Rule(
                    kind = Kind.ICON_SET, priority = 18, iconSet = IconSet.THREE_TRAFFIC_LIGHTS_1,
                    thresholds = listOf(
                        Threshold(ThresholdKind.PERCENT, 33.0),
                        Threshold(ThresholdKind.PERCENT, 67.0),
                    ),
                ),
                // Every flag that differs from the default, plus a dxf, on a four-icon set with absolute numbers.
                Rule(
                    kind = Kind.ICON_SET, priority = 19, iconSet = IconSet.FOUR_ARROWS,
                    showValue = false, percent = false, reverse = true, format = GREEN,
                    thresholds = listOf(
                        Threshold(ThresholdKind.NUM, 3.0),
                        Threshold(ThresholdKind.NUM, 6.0),
                        Threshold(ThresholdKind.NUM, 9.0),
                    ),
                ),
                // Five icons need four boundaries, which is the schema's maximum for <cfvo>.
                Rule(
                    kind = Kind.ICON_SET, priority = 20, iconSet = IconSet.FIVE_QUARTERS,
                    thresholds = listOf(
                        Threshold(ThresholdKind.PERCENT, 20.0),
                        Threshold(ThresholdKind.PERCENT, 40.0),
                        Threshold(ThresholdKind.PERCENT, 60.0),
                        Threshold(ThresholdKind.PERCENT, 80.0),
                    ),
                ),
            ),
        ),
    )

    // ---------------------------------------------------------------- verdicts

    private val SERIAL_1900 = SerialDate.toSerial1900(2024, 3, 15)

    private val MONDAY = Calendar.ofDate(2024, 3, 15)
    private val SUNDAY = Calendar.ofDate(2024, 3, 15, weekStartsOn = WeekStart.SUNDAY)

    private fun serial(year: Int, month: Int, day: Int): Double = SerialDate.toSerial1900(year, month, day).toDouble()

    private val VALUES: Map<SheetRange.Cell, Value> = buildMap {
        fun put(ref: String, value: Value) {
            put(SheetRange.parseCell(ref), value)
        }
        // A1 blank, A2 spaces, A3 text, A4 number, A5 error, A6 boolean.
        put("A2", Value.Txt("   "))
        put("A3", Value.Txt("abc"))
        put("A4", Value.Num(5.0))
        put("A5", Value.Err("#DIV/0!"))
        put("A6", Value.Bool(true))
        // B, C, D: 1..9 for three icon sets.
        for (row in 1..9) {
            put("B$row", Value.Num(row.toDouble()))
            put("C$row", Value.Num(row.toDouble()))
            put("D$row", Value.Num(row.toDouble()))
        }
        // E: the ten dates the timePeriod presets are asked about.
        put("E1", Value.Num(serial(2024, 3, 15)))   // today
        put("E2", Value.Num(serial(2024, 3, 14)))   // yesterday
        put("E3", Value.Num(serial(2024, 3, 16)))   // tomorrow
        put("E4", Value.Num(serial(2024, 3, 10)))   // five days ago, a Sunday
        put("E5", Value.Num(serial(2024, 2, 15)))   // last month
        put("E6", Value.Num(serial(2023, 5, 1)))    // last year
        put("E7", Value.Num(serial(2024, 1, 15)))   // this year, first quarter
        put("E8", Value.Num(serial(2024, 3, 11)))   // the Monday of today's week
        put("E9", Value.Num(serial(2024, 3, 4)))    // the Monday of the previous week
        // F: a five-icon set over a full 0..100 span.
        for ((index, value) in listOf(0.0, 10.0, 25.0, 50.0, 75.0, 100.0).withIndex()) {
            put("F${index + 1}", Value.Num(value))
        }
        // G: the Sunday that separates a Monday-first week from a Sunday-first one.
        put("G1", Value.Num(serial(2024, 3, 10)))
    }

    private val VERDICT_BLOCKS = listOf(
        Block(
            "A1:A6",
            listOf(
                Rule(kind = Kind.CONTAINS_BLANKS, priority = 1, format = RED),
                Rule(kind = Kind.NOT_CONTAINS_BLANKS, priority = 2, format = BOLD),
                Rule(kind = Kind.CONTAINS_ERRORS, priority = 3, format = GREEN),
                Rule(kind = Kind.NOT_CONTAINS_ERRORS, priority = 4, format = ITALIC),
            ),
        ),
        Block(
            "B1:B9",
            listOf(
                Rule(
                    kind = Kind.ICON_SET, priority = 5, iconSet = IconSet.THREE_TRAFFIC_LIGHTS_1,
                    thresholds = listOf(
                        Threshold(ThresholdKind.PERCENT, 33.0),
                        Threshold(ThresholdKind.PERCENT, 67.0),
                    ),
                ),
            ),
        ),
        Block(
            "C1:C9",
            listOf(
                Rule(
                    kind = Kind.ICON_SET, priority = 6, iconSet = IconSet.FOUR_ARROWS, percent = false,
                    thresholds = listOf(
                        Threshold(ThresholdKind.NUM, 3.0),
                        Threshold(ThresholdKind.NUM, 6.0),
                        Threshold(ThresholdKind.NUM, 8.0),
                    ),
                ),
            ),
        ),
        Block(
            "D1:D9",
            listOf(
                Rule(
                    kind = Kind.ICON_SET, priority = 7, iconSet = IconSet.THREE_ARROWS, percent = false,
                    reverse = true, showValue = false,
                    thresholds = listOf(
                        Threshold(ThresholdKind.NUM, 2.0),
                        Threshold(ThresholdKind.NUM, 5.0),
                    ),
                ),
            ),
        ),
        Block(
            "E1:E9",
            listOf(
                Rule(kind = Kind.TIME_PERIOD, priority = 8, timePeriod = TimePeriod.TODAY, format = RED),
                Rule(kind = Kind.TIME_PERIOD, priority = 9, timePeriod = TimePeriod.YESTERDAY, format = RED),
                Rule(kind = Kind.TIME_PERIOD, priority = 10, timePeriod = TimePeriod.TOMORROW, format = RED),
                Rule(kind = Kind.TIME_PERIOD, priority = 11, timePeriod = TimePeriod.LAST_7_DAYS, format = RED),
                Rule(kind = Kind.TIME_PERIOD, priority = 12, timePeriod = TimePeriod.THIS_MONTH, format = RED),
                Rule(kind = Kind.TIME_PERIOD, priority = 13, timePeriod = TimePeriod.LAST_MONTH, format = RED),
                Rule(kind = Kind.TIME_PERIOD, priority = 14, timePeriod = TimePeriod.THIS_YEAR, format = RED),
                Rule(
                    kind = Kind.TIME_PERIOD, priority = 15,
                    timePeriod = TimePeriod.FIRST_QUARTER, format = RED,
                ),
                Rule(kind = Kind.TIME_PERIOD, priority = 16, timePeriod = TimePeriod.THIS_WEEK, format = RED),
                Rule(kind = Kind.TIME_PERIOD, priority = 17, timePeriod = TimePeriod.LAST_WEEK, format = RED),
            ),
        ),
        Block(
            "F1:F6",
            listOf(
                Rule(
                    kind = Kind.ICON_SET, priority = 18, iconSet = IconSet.FIVE_QUARTERS,
                    thresholds = listOf(
                        Threshold(ThresholdKind.PERCENT, 20.0),
                        Threshold(ThresholdKind.PERCENT, 40.0),
                        Threshold(ThresholdKind.PERCENT, 60.0),
                        Threshold(ThresholdKind.PERCENT, 80.0),
                    ),
                ),
            ),
        ),
        Block(
            "G1",
            listOf(Rule(kind = Kind.TIME_PERIOD, priority = 19, timePeriod = TimePeriod.THIS_WEEK, format = RED)),
        ),
    )

    private class MapGrid(private val values: Map<SheetRange.Cell, Value>) : XlsxConditionalFormatVerdict.Grid {
        override fun value(cell: SheetRange.Cell): Value = values[cell] ?: Value.Blank
    }

    /** `outcome|fired|stopped|unresolved|bold;italic;strike;underline;font;fill|set:index`. */
    private fun encode(outcome: Outcome): String = when (outcome) {
        is Outcome.Undecided -> "UNDECIDED|-|0|${outcome.reason}|-|-"
        is Outcome.Decided -> {
            val decoration = XlsxConditionalFormatVerdict.decoration(outcome)
            val paint = listOf(
                if (decoration.bold) "1" else "0",
                if (decoration.italic) "1" else "0",
                if (decoration.strike) "1" else "0",
                if (decoration.underline) "1" else "0",
                decoration.fontRgb ?: "-",
                decoration.fillRgb ?: "-",
            ).joinToString(";")
            val icon = decoration.iconIndex?.let { "${decoration.iconSet}:$it" } ?: "-"
            val fired = outcome.fired.joinToString(",") { it.priority.toString() }.ifEmpty { "-" }
            val unresolved = outcome.unresolved.joinToString(",") { it.priority.toString() }.ifEmpty { "-" }
            "DECIDED|$fired|${if (outcome.stopped) 1 else 0}|$unresolved|$paint|$icon"
        }
    }

    // ---------------------------------------------------------------- foreign package

    private const val MAIN = "http://schemas.openxmlformats.org/spreadsheetml/2006/main"
    private const val REL = "http://schemas.openxmlformats.org/officeDocument/2006/relationships"
    private const val PKG = "http://schemas.openxmlformats.org/package/2006/relationships"

    private fun foreignPackage(): ByteArray {
        val parts = linkedMapOf(
            "[Content_Types].xml" to
                """<?xml version="1.0" encoding="UTF-8"?><Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types"><Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/><Default Extension="xml" ContentType="application/xml"/><Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/><Override PartName="/xl/worksheets/sheet1.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/><Override PartName="/xl/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml"/></Types>""",
            "xl/workbook.xml" to
                """<?xml version="1.0" encoding="UTF-8"?><workbook xmlns="$MAIN" xmlns:r="$REL"><sheets><sheet name="Foreign" sheetId="1" r:id="rId1"/></sheets></workbook>""",
            "xl/_rels/workbook.xml.rels" to
                """<?xml version="1.0" encoding="UTF-8"?><Relationships xmlns="$PKG"><Relationship Id="rId1" Type="$REL/worksheet" Target="worksheets/sheet1.xml"/><Relationship Id="rId2" Type="$REL/styles" Target="styles.xml"/></Relationships>""",
            "xl/styles.xml" to
                """<?xml version="1.0" encoding="UTF-8"?><styleSheet xmlns="$MAIN"><fonts count="1"><font><sz val="11"/><name val="Calibri"/></font></fonts><fills count="1"><fill><patternFill patternType="none"/></fill></fills><borders count="1"><border/></borders><cellStyleXfs count="1"><xf numFmtId="0" fontId="0" fillId="0" borderId="0"/></cellStyleXfs><cellXfs count="1"><xf numFmtId="0" fontId="0" fillId="0" borderId="0" xfId="0"/></cellXfs><dxfs count="1"><dxf><font><b/></font></dxf></dxfs></styleSheet>""",
            "xl/worksheets/sheet1.xml" to
                """<?xml version="1.0" encoding="UTF-8"?><worksheet xmlns="$MAIN"><sheetData><row r="1"><c r="A1" t="inlineStr"><is><t>foreign</t></is></c></row></sheetData>""" +
                // Excel's own one-cfvo-per-icon form: the leading boundary is pinned at the range minimum, so this
                // model stores the two boundaries that actually decide something.
                """<conditionalFormatting sqref="A1:A9"><cfRule type="iconSet" priority="1"><iconSet iconSet="3TrafficLights1"><cfvo type="percent" val="0"/><cfvo type="percent" val="33"/><cfvo type="percent" val="67"/></iconSet></cfRule></conditionalFormatting>""" +
                // Absolute boundaries with every flag set away from its default.
                """<conditionalFormatting sqref="B1:B9"><cfRule type="iconSet" priority="2"><iconSet iconSet="4Arrows" showValue="0" percent="0" reverse="1"><cfvo type="num" val="1"/><cfvo type="num" val="4"/><cfvo type="num" val="7"/></iconSet></cfRule></conditionalFormatting>""" +
                // An x14-only set name: not in the base enumeration, so it is refused rather than guessed at.
                """<conditionalFormatting sqref="C1:C9"><cfRule type="iconSet" priority="3"><iconSet iconSet="5Boxes"><cfvo type="percent" val="0"/><cfvo type="percent" val="20"/><cfvo type="percent" val="40"/><cfvo type="percent" val="60"/><cfvo type="percent" val="80"/></iconSet></cfRule></conditionalFormatting>""" +
                // Three boundaries for three icons whose first is *not* the range minimum: dropping it would move
                // every icon by one, so the rule is refused.
                """<conditionalFormatting sqref="D1:D9"><cfRule type="iconSet" priority="4"><iconSet iconSet="3Arrows"><cfvo type="num" val="2"/><cfvo type="num" val="5"/><cfvo type="num" val="8"/></iconSet></cfRule></conditionalFormatting>""" +
                """<conditionalFormatting sqref="E1:E9"><cfRule type="timePeriod" priority="5" timePeriod="today"/></conditionalFormatting>""" +
                // Not an ST_TimePeriod token.
                """<conditionalFormatting sqref="F1:F9"><cfRule type="timePeriod" priority="6" timePeriod="fortnight"/></conditionalFormatting>""" +
                """<conditionalFormatting sqref="G1:G9"><cfRule type="containsBlanks" priority="7"/></conditionalFormatting>""" +
                """<conditionalFormatting sqref="H1:H9"><cfRule type="notContainsErrors" priority="8" dxfId="0"/></conditionalFormatting>""" +
                // A rank on a kind that has no ranking.
                """<conditionalFormatting sqref="I1:I9"><cfRule type="containsBlanks" priority="9" rank="2"/></conditionalFormatting>""" +
                "</worksheet>",
        )
        val out = java.io.ByteArrayOutputStream()
        java.util.zip.ZipOutputStream(out).use { zip ->
            for ((name, xml) in parts) {
                zip.putNextEntry(java.util.zip.ZipEntry(name))
                zip.write(xml.toByteArray(Charsets.UTF_8))
                zip.closeEntry()
            }
        }
        return out.toByteArray()
    }

    @JvmStatic
    fun main(args: Array<String>) {
        val folder = File(args.firstOrNull() ?: "testing/build/r101/artifacts")
        folder.mkdirs()

        val allKinds = XlsxStyledBook.StyledSheet(
            name = "AllKinds",
            cells = (1..9).map {
                XlsxStyledBook.StyledCell("A$it", XlsxStyledBook.StyledVal.Txt("k$it"))
            },
            conditionalFormats = ALL_KINDS,
        )
        val bytes = XlsxStyledBook.write(XlsxStyledBook.WorkbookSpec(sheets = listOf(allKinds)))
        File(folder, "r101.xlsx").writeBytes(bytes)
        val sheet1 = part(bytes, "xl/worksheets/sheet1.xml")

        // Every `timePeriod` preset, in a workbook of its own. openpyxl 3.1.5's enumeration accepts only the ten
        // presets its own user interface offers and *raises* on the other seven, so keeping these rules out of
        // `r101.xlsx` is what lets the openpyxl lanes read the main artifact at all. The limitation is asserted
        // below rather than worked around silently.
        val presetRules = TimePeriod.values().mapIndexed { index, period ->
            Rule(kind = Kind.TIME_PERIOD, priority = index + 1, timePeriod = period, format = RED)
        }
        val presets = XlsxStyledBook.StyledSheet(
            name = "Presets",
            cells = listOf(XlsxStyledBook.StyledCell("A1", XlsxStyledBook.StyledVal.Txt("presets"))),
            conditionalFormats = listOf(Block("A1:A9", presetRules)),
        )
        val presetBytes = XlsxStyledBook.write(XlsxStyledBook.WorkbookSpec(sheets = listOf(presets)))
        File(folder, "r101-presets.xlsx").writeBytes(presetBytes)

        // --- 1. the file ------------------------------------------------------------------------------------

        check("""type="timePeriod"""" in sheet1) { "the timePeriod rule is missing" }
        check("""timePeriod="today"""" in sheet1) { "the timePeriod attribute is missing" }
        check(Regex("""<cfRule type="containsBlanks"[^>]*/>""").containsMatchIn(sheet1)) {
            "a containsBlanks rule carries attributes only, so it must self-close"
        }
        check(Regex("""<cfRule type="notContainsErrors"[^>]*/>""").containsMatchIn(sheet1)) {
            "a notContainsErrors rule carries attributes only, so it must self-close"
        }
        // The icon set's own child element, with the two boundaries and none of the defaulted flags.
        val plainIcons = Regex("""<iconSet iconSet="3TrafficLights1">(.*?)</iconSet>""").find(sheet1)?.groupValues?.get(1)
            ?: error("no iconSet child for 3TrafficLights1")
        check(Regex("""<cfvo""").findAll(plainIcons).count() == 2) {
            "a three-icon set writes two boundaries, wrote ${Regex("<cfvo").findAll(plainIcons).count()}"
        }
        check("""<cfvo type="percent" val="33"/>""" in plainIcons) { "the percent boundary is missing" }
        check("""<cfvo type="percent" val="67"/>""" in plainIcons) { "the percent boundary is missing" }
        // Every flag that differs from the default is written, in the child, and nothing else is.
        val flagged = Regex("""<iconSet iconSet="4Arrows"[^>]*>""").find(sheet1)?.value
            ?: error("no iconSet child for 4Arrows")
        check("""showValue="0"""" in flagged) { "showValue=0 was not written: $flagged" }
        check("""percent="0"""" in flagged) { "percent=0 was not written: $flagged" }
        check("""reverse="1"""" in flagged) { "reverse=1 was not written: $flagged" }
        // A five-icon set needs four boundaries, which is the schema's maximum.
        val five = Regex("""<iconSet iconSet="5Quarters"[^>]*>(.*?)</iconSet>""").find(sheet1)?.groupValues?.get(1)
            ?: error("no iconSet child for 5Quarters")
        check(Regex("<cfvo").findAll(five).count() == 4) { "a five-icon set writes four boundaries" }
        // The icon set that carries no format must not point at a dxf.
        val bareIcons = Regex("""<cfRule type="iconSet"[^>]*priority="18"[^>]*>""").find(sheet1)?.value
            ?: error("no iconSet rule with priority 18")
        check("dxfId" !in bareIcons) { "an iconSet rule with no format wrote a dxfId: $bareIcons" }

        // --- 2. the reader ----------------------------------------------------------------------------------

        val read = XlsxStyledBook.read(bytes)
        val readKinds = read.sheets.first { it.name == "AllKinds" }.conditionalFormats
        check(readKinds.size == 1) { "read ${readKinds.size} blocks, wrote 1" }
        check(readKinds[0].rules.size == ALL_KINDS[0].rules.size) {
            "read ${readKinds[0].rules.size} rules, wrote ${ALL_KINDS[0].rules.size}"
        }
        for ((index, rule) in ALL_KINDS[0].rules.withIndex()) {
            val round = readKinds[0].rules[index]
            check(description(round) == description(rule)) {
                "rule ${rule.priority} round-tripped as ${description(round)}, wrote ${description(rule)}"
            }
        }

        // All seventeen presets survive a round trip, including the seven openpyxl refuses to parse.
        val readPresets = XlsxStyledBook.read(presetBytes).sheets.single().conditionalFormats.single().rules
        check(readPresets.size == TimePeriod.values().size) {
            "read ${readPresets.size} preset rules, wrote ${TimePeriod.values().size}"
        }
        check(readPresets.map { it.timePeriod } == TimePeriod.values().toList()) {
            "preset order changed: ${readPresets.map { it.timePeriod }}"
        }

        // --- 3. a foreign package ---------------------------------------------------------------------------

        val foreignBytes = foreignPackage()
        File(folder, "r101-foreign.xlsx").writeBytes(foreignBytes)
        val foreign = XlsxStyledBook.read(foreignBytes).sheets.single().conditionalFormats
            .flatMap { it.rules }
        check(foreign.size == 5) { "expected 5 surviving foreign rules, got ${foreign.size}" }
        check(foreign.map { it.priority } == listOf(1, 2, 5, 7, 8)) {
            "surviving priorities: ${foreign.map { it.priority }}"
        }
        val byPriority = foreign.associateBy { it.priority }
        byPriority[1]?.let { rule ->
            check(rule.iconSet == IconSet.THREE_TRAFFIC_LIGHTS_1) { "priority 1 set ${rule.iconSet}" }
            check(rule.thresholds.size == 2) { "priority 1 kept ${rule.thresholds.size} boundaries" }
            check(rule.thresholds.first().value == 33.0) { "priority 1 first boundary ${rule.thresholds.first()}" }
            check(rule.showValue == null && rule.percent == null && rule.reverse == null) {
                "priority 1 wrote a defaulted flag"
            }
        }
        byPriority[2]?.let { rule ->
            check(rule.iconSet == IconSet.FOUR_ARROWS) { "priority 2 set ${rule.iconSet}" }
            check(rule.showValue == false && rule.percent == false && rule.reverse == true) {
                "priority 2 flags ${yes(rule.showValue)}/${yes(rule.percent)}/${yes(rule.reverse)}"
            }
            check(rule.thresholds.size == 3) { "priority 2 kept ${rule.thresholds.size} boundaries" }
        }
        byPriority[5]?.let { rule ->
            check(rule.kind == Kind.TIME_PERIOD && rule.timePeriod == TimePeriod.TODAY) {
                "priority 5 ${rule.kind}/${rule.timePeriod}"
            }
        }
        byPriority[7]?.let { rule -> check(rule.kind == Kind.CONTAINS_BLANKS) { "priority 7 ${rule.kind}" } }
        byPriority[8]?.let { rule ->
            check(rule.kind == Kind.NOT_CONTAINS_ERRORS) { "priority 8 ${rule.kind}" }
            check(token(rule.format) == "bold") { "priority 8 format ${token(rule.format)}" }
        }
        check(foreign.none { it.iconSet != null && it.iconSet!!.token == "5Boxes" }) {
            "an x14 icon set name must be refused"
        }

        // --- 4. refusals ------------------------------------------------------------------------------------

        rejects("a timePeriod rule without a period") {
            Rule(kind = Kind.TIME_PERIOD, priority = 1)
        }
        rejects("a timePeriod rule carrying a formula") {
            Rule(kind = Kind.TIME_PERIOD, priority = 1, timePeriod = TimePeriod.TODAY, formulas = listOf("A1"))
        }
        rejects("a timePeriod rule carrying a rank") {
            Rule(kind = Kind.TIME_PERIOD, priority = 1, timePeriod = TimePeriod.TODAY, rank = 1)
        }
        rejects("a timePeriod on a cellIs rule") {
            Rule(
                kind = Kind.CELL_IS, priority = 1, operator = Operator.EQUAL, formulas = listOf("1"),
                timePeriod = TimePeriod.TODAY,
            )
        }
        rejects("a time period on a duplicateValues rule") {
            Rule(kind = Kind.DUPLICATE, priority = 1, timePeriod = TimePeriod.TODAY)
        }
        rejects("an iconSet rule without a set") {
            Rule(
                kind = Kind.ICON_SET, priority = 1,
                thresholds = listOf(Threshold(ThresholdKind.PERCENT, 50.0)),
            )
        }
        rejects("a three-icon set with one boundary") {
            Rule(
                kind = Kind.ICON_SET, priority = 1, iconSet = IconSet.THREE_ARROWS,
                thresholds = listOf(Threshold(ThresholdKind.PERCENT, 50.0)),
            )
        }
        rejects("a four-icon set with two boundaries") {
            Rule(
                kind = Kind.ICON_SET, priority = 1, iconSet = IconSet.FOUR_ARROWS,
                thresholds = listOf(Threshold(ThresholdKind.PERCENT, 25.0), Threshold(ThresholdKind.PERCENT, 50.0)),
            )
        }
        rejects("an iconSet boundary of type min") {
            Rule(
                kind = Kind.ICON_SET, priority = 1, iconSet = IconSet.THREE_ARROWS,
                thresholds = listOf(Threshold(ThresholdKind.MIN), Threshold(ThresholdKind.PERCENT, 50.0)),
            )
        }
        rejects("an iconSet boundary of type max") {
            Rule(
                kind = Kind.ICON_SET, priority = 1, iconSet = IconSet.THREE_ARROWS,
                thresholds = listOf(Threshold(ThresholdKind.PERCENT, 50.0), Threshold(ThresholdKind.MAX)),
            )
        }
        rejects("an iconSet rule carrying colours") {
            Rule(
                kind = Kind.ICON_SET, priority = 1, iconSet = IconSet.THREE_ARROWS,
                thresholds = listOf(Threshold(ThresholdKind.PERCENT, 30.0), Threshold(ThresholdKind.PERCENT, 60.0)),
                colors = listOf("FF0000"),
            )
        }
        rejects("an iconSet rule carrying a rank") {
            Rule(
                kind = Kind.ICON_SET, priority = 1, iconSet = IconSet.THREE_ARROWS, rank = 2,
                thresholds = listOf(Threshold(ThresholdKind.PERCENT, 30.0), Threshold(ThresholdKind.PERCENT, 60.0)),
            )
        }
        rejects("an iconSet rule carrying a bottom flag") {
            Rule(
                kind = Kind.ICON_SET, priority = 1, iconSet = IconSet.THREE_ARROWS, bottom = true,
                thresholds = listOf(Threshold(ThresholdKind.PERCENT, 30.0), Threshold(ThresholdKind.PERCENT, 60.0)),
            )
        }
        rejects("iconSet flags on a timePeriod rule") {
            Rule(
                kind = Kind.TIME_PERIOD, priority = 1, timePeriod = TimePeriod.TODAY,
                iconSet = IconSet.THREE_ARROWS, showValue = false,
            )
        }
        rejects("a showValue flag on a duplicateValues rule") {
            Rule(kind = Kind.DUPLICATE, priority = 1, showValue = false)
        }
        rejects("a reverse flag on a blank rule") {
            Rule(kind = Kind.CONTAINS_BLANKS, priority = 1, reverse = true)
        }
        rejects("a blank rule carrying a rank") {
            Rule(kind = Kind.CONTAINS_BLANKS, priority = 1, rank = 1)
        }
        rejects("an error rule carrying cfvo") {
            Rule(
                kind = Kind.CONTAINS_ERRORS, priority = 1,
                thresholds = listOf(Threshold(ThresholdKind.MIN), Threshold(ThresholdKind.MAX)),
            )
        }

        // --- 5. verdicts ------------------------------------------------------------------------------------

        val grid = MapGrid(VALUES)
        val computed = LinkedHashMap<String, String>()   // key: "ref/calendar"
        fun compute(ref: String, calendar: String, supplied: Calendar?) {
            val cell = SheetRange.parseCell(ref)
            computed["$ref/$calendar"] = encode(
                XlsxConditionalFormatVerdict.verdict(VERDICT_BLOCKS, grid, cell, supplied),
            )
        }
        val scope = (1..6).map { "A$it" } + (1..9).map { "B$it" } + (1..9).map { "C$it" } +
            (1..9).map { "D$it" } + (1..9).map { "E$it" } + (1..6).map { "F$it" } + listOf("G1")
        for (ref in scope) compute(ref, "monday", MONDAY)
        compute("G1", "sunday", SUNDAY)
        compute("E1", "none", null)

        fun verdict(ref: String, calendar: String = "monday") =
            computed["$ref/$calendar"] ?: error("no verdict for $ref/$calendar")

        // Blank and error rules. A1 blank: blank fires, not-blank does not, not-error fires. A2 spaces: the same,
        // because TRIM removes spaces. A5 error: only containsErrors fires — the negative forms are not
        // complements there.
        check(verdict("A1") == "DECIDED|1,4|0|-|0;1;0;0;-;FFC7CE|-") { "A1 ${verdict("A1")}" }
        check(verdict("A2") == verdict("A1")) { "A2 ${verdict("A2")} != A1 ${verdict("A1")}" }
        check(verdict("A3") == "DECIDED|2,4|0|-|1;1;0;0;-;-|-") { "A3 ${verdict("A3")}" }
        check(verdict("A4") == verdict("A3")) { "A4 ${verdict("A4")} != A3 ${verdict("A3")}" }
        check(verdict("A5") == "DECIDED|3|0|-|0;0;0;0;-;C6EFCE|-") { "A5 ${verdict("A5")}" }
        check(verdict("A6") == verdict("A3")) { "A6 ${verdict("A6")} != A3 ${verdict("A3")}" }

        // Three icon sets over 1..9.
        fun icon(ref: String): String = verdict(ref).substringAfterLast('|')
        check(icon("B1") == "3TrafficLights1:0") { "B1 ${icon("B1")}" }
        check(icon("B4") == "3TrafficLights1:1") { "B4 ${icon("B4")}" }
        check(icon("B7") == "3TrafficLights1:2") { "B7 ${icon("B7")}" }
        check(icon("B9") == "3TrafficLights1:2") { "B9 ${icon("B9")}" }
        check(icon("C2") == "4Arrows:0") { "C2 ${icon("C2")}" }
        check(icon("C3") == "4Arrows:1") { "C3 ${icon("C3")}" }
        check(icon("C6") == "4Arrows:2") { "C6 ${icon("C6")}" }
        check(icon("C8") == "4Arrows:3") { "C8 ${icon("C8")}" }
        // Reversed: 1..9 against boundaries 2 and 5, then flipped.
        check(icon("D1") == "3Arrows:2") { "D1 ${icon("D1")}" }
        check(icon("D3") == "3Arrows:1") { "D3 ${icon("D3")}" }
        check(icon("D5") == "3Arrows:0") { "D5 ${icon("D5")}" }
        // Five icons over 0..100 with percent boundaries 20/40/60/80.
        check(icon("F1") == "5Quarters:0") { "F1 ${icon("F1")}" }
        check(icon("F3") == "5Quarters:1") { "F3 ${icon("F3")}" }
        check(icon("F4") == "5Quarters:2") { "F4 ${icon("F4")}" }
        check(icon("F5") == "5Quarters:3") { "F5 ${icon("F5")}" }
        check(icon("F6") == "5Quarters:4") { "F6 ${icon("F6")}" }

        // timePeriod against 2024-03-15 (a Friday).
        fun fires(ref: String, calendar: String = "monday"): List<Int> =
            verdict(ref, calendar).split('|')[1].split(',').filter { it != "-" }.map { it.toInt() }
        check(fires("E1") == listOf(8, 11, 12, 14, 15, 16)) { "E1 fires ${fires("E1")}" }
        check(fires("E2") == listOf(9, 11, 12, 14, 15, 16)) { "E2 fires ${fires("E2")}" }
        check(fires("E3") == listOf(10, 12, 14, 15, 16)) { "E3 fires ${fires("E3")}" }
        check(fires("E4") == listOf(11, 12, 14, 15, 17)) { "E4 fires ${fires("E4")}" }
        check(fires("E5") == listOf(13, 14, 15)) { "E5 fires ${fires("E5")}" }
        check(fires("E6") == emptyList<Int>()) { "E6 fires ${fires("E6")}" }
        check(fires("E7") == listOf(14, 15)) { "E7 fires ${fires("E7")}" }
        check(fires("E8") == listOf(11, 12, 14, 15, 16)) { "E8 fires ${fires("E8")}" }
        check(fires("E9") == listOf(12, 14, 15, 17)) { "E9 fires ${fires("E9")}" }
        // Same cell, different first day of week: 2024-03-10 is a Sunday, so under a Sunday-first week it is in
        // the current week and under a Monday-first week it is in the last one.
        check(fires("G1", "monday") == emptyList<Int>()) { "G1 Monday fires ${fires("G1")}" }
        check(fires("G1", "sunday") == listOf(19)) { "G1 Sunday fires ${fires("G1", "sunday")}" }
        // Without a calendar a timePeriod rule is undecided, never "did not fire".
        check(verdict("E1", "none").startsWith("UNDECIDED")) { "E1 without a calendar: ${verdict("E1", "none")}" }

        // A serial outside the modelled window is not a date, so the rule decides nothing.
        val outOfRange = MapGrid(mapOf(SheetRange.parseCell("E1") to Value.Num(4_000_000.0)))
        val outOfRangeOutcome = XlsxConditionalFormatVerdict.verdict(
            listOf(Block("E1", listOf(Rule(kind = Kind.TIME_PERIOD, priority = 1, timePeriod = TimePeriod.TODAY)))),
            outOfRange,
            SheetRange.parseCell("E1"),
            MONDAY,
        )
        check(outOfRangeOutcome is Outcome.Decided && outOfRangeOutcome.fired.isEmpty()) {
            "an out-of-range serial must decide nothing: $outOfRangeOutcome"
        }

        // A blank cell is never a date and earns no icon.
        val blankGrid = MapGrid(emptyMap())
        for (kind in listOf(Kind.TIME_PERIOD, Kind.ICON_SET)) {
            val rule = when (kind) {
                Kind.TIME_PERIOD -> Rule(kind = kind, priority = 1, timePeriod = TimePeriod.TODAY, format = RED)
                else -> Rule(
                    kind = kind, priority = 1, iconSet = IconSet.THREE_ARROWS,
                    thresholds = listOf(
                        Threshold(ThresholdKind.PERCENT, 30.0),
                        Threshold(ThresholdKind.PERCENT, 60.0),
                    ),
                )
            }
            val outcomeForBlank = XlsxConditionalFormatVerdict.verdict(
                listOf(Block("E1", listOf(rule))), blankGrid, SheetRange.parseCell("E1"), MONDAY,
            )
            check(outcomeForBlank is Outcome.Decided && outcomeForBlank.fired.isEmpty()) {
                "$kind on a blank cell decided to fire: $outcomeForBlank"
            }
        }

        // The 1904 date system shifts every serial by 1462 days; the same civil date must produce the same verdict.
        val system1904 = Calendar.ofDate(2024, 3, 15, date1904 = true)
        check(system1904.today == SERIAL_1900 - SerialDate.SYSTEM_DELTA) {
            "the 1904 serial for 2024-03-15 is ${system1904.today}"
        }
        val grid1904 = MapGrid(
            mapOf(SheetRange.parseCell("E1") to Value.Num(system1904.today.toDouble())),
        )
        val todayRule = listOf(
            Block("E1", listOf(Rule(kind = Kind.TIME_PERIOD, priority = 1, timePeriod = TimePeriod.TODAY, format = RED))),
        )
        val in1904 = XlsxConditionalFormatVerdict.verdict(todayRule, grid1904, SheetRange.parseCell("E1"), system1904)
        check(in1904 is Outcome.Decided && in1904.fired.size == 1) { "1904 system today did not fire: $in1904" }

        // --- 6. intent tables -------------------------------------------------------------------------------

        val kindRows = StringBuilder()
        for (block in ALL_KINDS) {
            for (rule in block.rules) {
                kindRows.append(
                    listOf(
                        block.sqref,
                        rule.priority.toString(),
                        rule.kind.token,
                        rule.timePeriod?.token ?: "-",
                        rule.iconSet?.token ?: "-",
                        yes(rule.showValue),
                        yes(rule.percent),
                        yes(rule.reverse),
                        thresholds(rule),
                        token(rule.format),
                    ).joinToString("\t"),
                ).append('\n')
            }
        }
        File(folder, "kinds.tsv").writeText(kindRows.toString(), Charsets.UTF_8)

        val ruleRows = StringBuilder()
        for (block in VERDICT_BLOCKS) {
            for (rule in block.rules) {
                ruleRows.append(
                    listOf(
                        block.sqref,
                        rule.priority.toString(),
                        rule.kind.token,
                        rule.timePeriod?.token ?: "-",
                        rule.iconSet?.token ?: "-",
                        yes(rule.reverse),
                        thresholds(rule),
                        token(rule.format),
                    ).joinToString("\t"),
                ).append('\n')
            }
        }
        File(folder, "rules.tsv").writeText(ruleRows.toString(), Charsets.UTF_8)

        val valueRows = StringBuilder()
        for ((cell, value) in VALUES.entries.sortedWith(compareBy({ it.key.row }, { it.key.col }))) {
            val encoded = when (value) {
                is Value.Num -> "n\t${value.value}"
                is Value.Txt -> "t\t${value.text}"
                is Value.Bool -> "b\t${if (value.flag) 1 else 0}"
                is Value.Err -> "e\t${value.code}"
                Value.Blank -> error("a blank cell is not in the grid")
            }
            valueRows.append("${cell.toA1()}\t$encoded\n")
        }
        File(folder, "values.tsv").writeText(valueRows.toString(), Charsets.UTF_8)

        File(folder, "calendar.tsv").writeText(
            "monday\t${MONDAY.today}\t${MONDAY.date1904}\t${MONDAY.weekStartsOn.token}\n" +
                "sunday\t${SUNDAY.today}\t${SUNDAY.date1904}\t${SUNDAY.weekStartsOn.token}\n",
            Charsets.UTF_8,
        )

        val verdictRows = StringBuilder()
        for ((key, encoded) in computed) {
            val (ref, calendar) = key.split('/')
            verdictRows.append("$ref\t$calendar\t$encoded\n")
        }
        File(folder, "verdicts.tsv").writeText(verdictRows.toString(), Charsets.UTF_8)

        val summary = "R101_CF_OK assertions=$assertions refusals=$refusals kinds=${ALL_KINDS[0].rules.size} " +
            "presets=${TimePeriod.values().size} verdicts=${computed.size} foreign=${foreign.size}"
        File(folder, "summary.txt").writeText(summary + "\n", Charsets.UTF_8)
        println(summary)
    }
}
