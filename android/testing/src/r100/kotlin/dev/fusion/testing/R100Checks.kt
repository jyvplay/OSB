// SPDX-License-Identifier: Apache-2.0
package dev.fusion.testing

import dev.fusion.ooxml.SheetRange
import dev.fusion.ooxml.XlsxConditionalFormatVerdict
import dev.fusion.ooxml.XlsxConditionalFormatVerdict.Outcome
import dev.fusion.ooxml.XlsxConditionalFormatVerdict.Value
import dev.fusion.ooxml.XlsxConditionalFormatting
import dev.fusion.ooxml.XlsxConditionalFormatting.Block
import dev.fusion.ooxml.XlsxConditionalFormatting.Format
import dev.fusion.ooxml.XlsxConditionalFormatting.Kind
import dev.fusion.ooxml.XlsxConditionalFormatting.Operator
import dev.fusion.ooxml.XlsxConditionalFormatting.Rule
import dev.fusion.ooxml.XlsxConditionalFormatting.Threshold
import dev.fusion.ooxml.XlsxConditionalFormatting.ThresholdKind
import dev.fusion.ooxml.XlsxStyledBook
import java.io.File

/**
 * R100 spreadsheet contracts: the seven conditional-format rule kinds that state a *predicate* rather than a
 * numeric comparison — `containsText`, `notContainsText`, `beginsWith`, `endsWith`, `aboveAverage`,
 * `duplicateValues`, `uniqueValues` — together with the `text`/`aboveAverage`/`equalAverage`/`stdDev` attributes
 * they own, and the verdicts they produce.
 *
 * Three witnesses, none of them an application:
 *
 * 1. the probe writes real packages and asserts the exact `<cfRule>` attributes it emitted (`text=` and the
 *    `operator` token differ from the type for `notContainsText`; a text rule carries no `<formula>`);
 * 2. `XlsxStyledBook.read` round-trips the same rules through an independent parser, and a hand-built "foreign"
 *    package exercises what a *another* producer writes: a redundant `<formula>` beside `containsText`, a rule
 *    with no `dxfId`, an unparsable `aboveAverage`, a blank `text` and an unmodelled `containsBlanks`;
 * 3. `tools/check_r100_conditional.py` re-derives both the XML facts (raw OPC + openpyxl 3.1.5) and every
 *    verdict in Python from `rules.tsv`/`values.tsv`.
 *
 * Host JVM only. No Excel, LibreOffice, renderer or device was involved: an assertion here is a statement about
 * this writer and this evaluator, never about how a spreadsheet paints a cell.
 */
object R100Checks {
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

    /** The token this probe and the Python lane both use for a format, so the two can be compared as strings. */
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

    private fun yes(value: Boolean?): String = when (value) {
        null -> "-"
        true -> "1"
        false -> "0"
    }

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
        token(rule.format),
    ).joinToString("|")

    // ---------------------------------------------------------------- Kinds sheet: one rule per kind

    private val TEXT_RULES = listOf(
        Rule(kind = Kind.TEXT_CONTAINS, priority = 1, text = "ap*e", format = RED),
        Rule(kind = Kind.TEXT_NOT_CONTAINS, priority = 2, text = "a*a", format = BOLD),
        Rule(kind = Kind.TEXT_BEGINS_WITH, priority = 3, text = "Ap", format = GREEN),
        Rule(kind = Kind.TEXT_ENDS_WITH, priority = 4, text = "e", format = ITALIC),
    )

    private val AVERAGE_RULES = listOf(
        Rule(kind = Kind.ABOVE_AVERAGE, priority = 5, stdDev = 1, format = RED),
        Rule(
            kind = Kind.ABOVE_AVERAGE,
            priority = 6,
            aboveAverage = false,
            equalAverage = true,
            format = BOLD,
        ),
        // No flags at all: the schema defaults (`aboveAverage` true, `equalAverage` false) stay unwritten.
        Rule(kind = Kind.ABOVE_AVERAGE, priority = 7, format = GREEN),
    )

    private val DUPLICATE_RULES = listOf(
        Rule(kind = Kind.DUPLICATE, priority = 8, format = UNDERLINE),
        Rule(kind = Kind.UNIQUE, priority = 9, format = STRIKE),
    )

    /** The five kinds R96 already modelled, on the same sheet: coexistence is the point. */
    private val LEGACY_RULES = listOf(
        Rule(
            kind = Kind.CELL_IS,
            priority = 10,
            operator = Operator.GREATER_THAN,
            formulas = listOf("5"),
            format = RED,
        ),
        Rule(kind = Kind.EXPRESSION, priority = 11, formulas = listOf("MOD(ROW(),2)=0"), format = BOLD),
        Rule(kind = Kind.TOP10, priority = 12, rank = 3, percent = false, bottom = false, format = GREEN),
        Rule(
            kind = Kind.COLOR_SCALE,
            priority = 13,
            thresholds = listOf(Threshold(ThresholdKind.MIN), Threshold(ThresholdKind.MAX)),
            colors = listOf("F8696B", "63BE7B"),
        ),
        Rule(
            kind = Kind.DATA_BAR,
            priority = 14,
            thresholds = listOf(Threshold(ThresholdKind.NUM, 0.0), Threshold(ThresholdKind.NUM, 100.0)),
            colors = listOf("638EC6"),
        ),
    )

    private val KIND_BLOCKS = listOf(
        Block("A1:A9", TEXT_RULES),
        Block("B1:B8", AVERAGE_RULES),
        Block("C1:C8", DUPLICATE_RULES),
        Block("D1:D8", LEGACY_RULES),
    )

    // ---------------------------------------------------------------- Verdicts sheet

    private val AP_TEXT = listOf("apple", "APPLE", "banana", "cherry", "", "pineapple")

    private val VALUES: Map<SheetRange.Cell, Value> = buildMap {
        AP_TEXT.forEachIndexed { index, value ->
            if (value.isNotEmpty()) put(SheetRange.parseCell("A${index + 1}"), Value.Txt(value))
        }
        // A5 stays blank. A7 is the *number* 12.5 and A8 the *text* "12.5": the coercion pair.
        put(SheetRange.parseCell("A7"), Value.Num(12.5))
        put(SheetRange.parseCell("A8"), Value.Txt("12.5"))
        // A9 repeats A7's *number*: numbers collide in a duplicate rule, and the text A8 does not join them.
        put(SheetRange.parseCell("A9"), Value.Num(12.5))
        (1..8).forEach { put(SheetRange.parseCell("B$it"), Value.Num(it.toDouble())) }
        listOf("x", "X", "y", "z", "x", "y", "x").forEachIndexed { index, value ->
            put(SheetRange.parseCell("C${index + 1}"), Value.Txt(value))
        }
        (1..6).forEach { put(SheetRange.parseCell("D$it"), Value.Num(5.0)) }
    }

    private val VERDICT_BLOCKS = listOf(
        Block(
            "A1:A9",
            listOf(
                Rule(kind = Kind.TEXT_CONTAINS, priority = 1, text = "ap*e", format = RED),
                Rule(kind = Kind.TEXT_NOT_CONTAINS, priority = 2, text = "a*a", format = BOLD),
                Rule(kind = Kind.TEXT_BEGINS_WITH, priority = 3, text = "Ap", format = GREEN),
                Rule(kind = Kind.TEXT_ENDS_WITH, priority = 4, text = "e", format = ITALIC),
                Rule(kind = Kind.DUPLICATE, priority = 5, format = UNDERLINE),
                Rule(kind = Kind.UNIQUE, priority = 6, format = STRIKE),
            ),
        ),
        Block(
            "B1:B8",
            listOf(
                Rule(kind = Kind.ABOVE_AVERAGE, priority = 7, format = RED),
                Rule(
                    kind = Kind.ABOVE_AVERAGE,
                    priority = 8,
                    aboveAverage = false,
                    equalAverage = true,
                    format = BOLD,
                ),
                Rule(
                    kind = Kind.ABOVE_AVERAGE,
                    priority = 9,
                    equalAverage = true,
                    stdDev = 1,
                    format = GREEN,
                ),
            ),
        ),
        Block(
            "C1:C8",
            listOf(
                Rule(kind = Kind.DUPLICATE, priority = 10, format = RED),
                Rule(kind = Kind.UNIQUE, priority = 11, format = BOLD),
            ),
        ),
        Block(
            "D1:D6",
            listOf(
                Rule(
                    kind = Kind.ABOVE_AVERAGE,
                    priority = 12,
                    equalAverage = true,
                    stdDev = 1,
                    format = GREEN,
                ),
                Rule(kind = Kind.ABOVE_AVERAGE, priority = 13, equalAverage = false, stdDev = 1, format = ITALIC),
            ),
        ),
    )

    private class MapGrid(private val values: Map<SheetRange.Cell, Value>) : XlsxConditionalFormatVerdict.Grid {
        override fun value(cell: SheetRange.Cell): Value = values[cell] ?: Value.Blank
    }

    private fun outcome(outcome: Outcome): String = when (outcome) {
        is Outcome.Undecided -> "UNDECIDED|-|0|${outcome.reason}|-"
        is Outcome.Decided -> {
            val decoration = XlsxConditionalFormatVerdict.decoration(outcome)
            val painted = listOf(
                if (decoration.bold) "1" else "0",
                if (decoration.italic) "1" else "0",
                if (decoration.strike) "1" else "0",
                if (decoration.underline) "1" else "0",
                decoration.fontRgb ?: "-",
                decoration.fillRgb ?: "-",
            ).joinToString(";")
            val fired = outcome.fired.joinToString(",") { it.priority.toString() }.ifEmpty { "-" }
            val unresolved = outcome.unresolved.joinToString(",") { it.priority.toString() }.ifEmpty { "-" }
            "DECIDED|$fired|${if (outcome.stopped) 1 else 0}|$unresolved|$painted"
        }
    }

    // ---------------------------------------------------------------- foreign package

    private const val MAIN = "http://schemas.openxmlformats.org/spreadsheetml/2006/main"
    private const val REL = "http://schemas.openxmlformats.org/officeDocument/2006/relationships"
    private const val PKG = "http://schemas.openxmlformats.org/package/2006/relationships"

    /** What another producer writes, including three shapes this model must survive and one it must refuse. */
    private fun foreignPackage(): ByteArray {
        val parts = linkedMapOf(
            "[Content_Types].xml" to
                """<?xml version="1.0" encoding="UTF-8"?><Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types"><Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/><Default Extension="xml" ContentType="application/xml"/><Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/><Override PartName="/xl/worksheets/sheet1.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/><Override PartName="/xl/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml"/></Types>""",
            "xl/workbook.xml" to
                """<?xml version="1.0" encoding="UTF-8"?><workbook xmlns="$MAIN" xmlns:r="$REL"><sheets><sheet name="Foreign" sheetId="1" r:id="rId1"/></sheets></workbook>""",
            "xl/_rels/workbook.xml.rels" to
                """<?xml version="1.0" encoding="UTF-8"?><Relationships xmlns="$PKG"><Relationship Id="rId1" Type="$REL/worksheet" Target="worksheets/sheet1.xml"/><Relationship Id="rId2" Type="$REL/styles" Target="styles.xml"/></Relationships>""",
            "xl/styles.xml" to
                """<?xml version="1.0" encoding="UTF-8"?><styleSheet xmlns="$MAIN"><fonts count="1"><font><sz val="11"/><name val="Calibri"/></font></fonts><fills count="1"><fill><patternFill patternType="none"/></fill></fills><borders count="1"><border/></borders><cellStyleXfs count="1"><xf numFmtId="0" fontId="0" fillId="0" borderId="0"/></cellStyleXfs><cellXfs count="1"><xf numFmtId="0" fontId="0" fillId="0" borderId="0" xfId="0"/></cellXfs><dxfs count="2"><dxf><font><b/><color rgb="00FF0000"/></font></dxf><dxf><fill><patternFill patternType="solid"><bgColor rgb="0000FF00"/></patternFill></fill></dxf></dxfs></styleSheet>""",
            "xl/worksheets/sheet1.xml" to
                """<?xml version="1.0" encoding="UTF-8"?><worksheet xmlns="$MAIN"><sheetData><row r="1"><c r="A1" t="inlineStr"><is><t>foreign</t></is></c></row></sheetData>""" +
                // Excel's own spelling: the type, the operator and the text, plus a formula this model treats as
                // redundant with all three and therefore does not keep.
                """<conditionalFormatting sqref="A1:A9"><cfRule type="containsText" priority="1" operator="containsText" dxfId="0" text="abc"><formula>NOT(ISERROR(SEARCH("abc",A1)))</formula></cfRule></conditionalFormatting>""" +
                // No dxfId: a rule that fires and paints nothing. Refusing it would drop the rule entirely.
                """<conditionalFormatting sqref="B1:B9"><cfRule type="notContainsText" priority="2" operator="notContains" text="a*a"/></conditionalFormatting>""" +
                """<conditionalFormatting sqref="C1:C9"><cfRule type="aboveAverage" priority="3" equalAverage="0" aboveAverage="1" stdDev="2" dxfId="1"/></conditionalFormatting>""" +
                // A value that is not ST_OnOff reads as absent rather than as true.
                """<conditionalFormatting sqref="D1:D9"><cfRule type="aboveAverage" priority="4" aboveAverage="maybe"/></conditionalFormatting>""" +
                """<conditionalFormatting sqref="E1:E9"><cfRule type="duplicateValues" priority="5" dxfId="0"/></conditionalFormatting>""" +
                """<conditionalFormatting sqref="F1:F9"><cfRule type="uniqueValues" priority="6"/></conditionalFormatting>""" +
                // Blank text is refused: SEARCH of the empty string matches every cell, so the rule would be a lie.
                """<conditionalFormatting sqref="G1:G9"><cfRule type="containsText" priority="7" operator="containsText" text="   "/></conditionalFormatting>""" +
                // Still unmodelled, and still dropped rather than guessed at.
                """<conditionalFormatting sqref="H1:H9"><cfRule type="containsBlanks" priority="8"/></conditionalFormatting>""" +
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
        val folder = File(args.firstOrNull() ?: "testing/build/r100/artifacts")
        folder.mkdirs()

        val kinds = XlsxStyledBook.StyledSheet(
            name = "Kinds",
            cells = (1..6).map {
                XlsxStyledBook.StyledCell("A$it", XlsxStyledBook.StyledVal.Txt("k$it"))
            },
            conditionalFormats = KIND_BLOCKS,
        )
        val verdicts = XlsxStyledBook.StyledSheet(
            name = "Verdicts",
            cells = VALUES.entries
                .sortedWith(compareBy({ it.key.row }, { it.key.col }))
                .map { (cell, value) ->
                    val payload = when (value) {
                        is Value.Num -> XlsxStyledBook.StyledVal.Num(value.value)
                        is Value.Txt -> XlsxStyledBook.StyledVal.Txt(value.text)
                        is Value.Bool -> XlsxStyledBook.StyledVal.Bool(value.flag)
                        Value.Blank -> error("a blank cell is written as no cell")
                        is Value.Err -> error("an error cell cannot be written as a value")
                    }
                    XlsxStyledBook.StyledCell(cell.toA1(), payload)
                },
            conditionalFormats = VERDICT_BLOCKS,
        )

        val bytes = XlsxStyledBook.write(XlsxStyledBook.WorkbookSpec(sheets = listOf(kinds, verdicts)))
        File(folder, "r100.xlsx").writeBytes(bytes)
        val sheet1 = part(bytes, "xl/worksheets/sheet1.xml")

        // --- 1. the attributes this writer emits -----------------------------------------------------------

        check("""type="containsText"""" in sheet1) { "the containsText rule is missing" }
        check("""operator="containsText""" in sheet1) { "containsText must state its own operator token" }
        check("""text="ap*e"""" in sheet1) { "the text attribute is missing" }
        // Excel spells the negative form with a different operator than its type; copying the type would be
        // wrong in a way a reader cannot see without evaluating the rule.
        check("""type="notContainsText"""" in sheet1) { "the notContainsText rule is missing" }
        check("""operator="notContains"""" in sheet1) { "notContainsText must write operator=notContains" }
        check("""operator="beginsWith"""" in sheet1) { "beginsWith must state its own operator token" }
        check("""operator="endsWith"""" in sheet1) { "endsWith must state its own operator token" }
        check("""type="aboveAverage"""" in sheet1) { "the aboveAverage rule is missing" }
        check("""stdDev="1"""" in sheet1) { "stdDev is missing" }
        check("""equalAverage="1"""" in sheet1) { "equalAverage is missing" }
        check("""aboveAverage="0"""" in sheet1) { "aboveAverage=0 is missing" }
        // A rule with no flags must write none of them: the schema defaults are the honest spelling of "default".
        val plainAverage = Regex("""<cfRule type="aboveAverage"[^>]*priority="7"[^>]*>""").find(sheet1)?.value
            ?: error("no aboveAverage rule with priority 7")
        check("aboveAverage=" !in plainAverage) { "a defaulted aboveAverage rule wrote aboveAverage: $plainAverage" }
        check("equalAverage=" !in plainAverage) { "a defaulted aboveAverage rule wrote equalAverage: $plainAverage" }
        check("stdDev=" !in plainAverage) { "a defaulted aboveAverage rule wrote stdDev: $plainAverage" }
        check(Regex("""<cfRule type="duplicateValues"[^>]*/>""").containsMatchIn(sheet1)) {
            "a duplicateValues rule carries nothing but attributes, so it must self-close"
        }
        check(Regex("""<cfRule type="uniqueValues"[^>]*/>""").containsMatchIn(sheet1)) {
            "a uniqueValues rule carries nothing but attributes, so it must self-close"
        }
        // Only the two legacy rules have a formula; a text rule that wrote one would be echoing a spelling it
        // never derived.
        check(Regex("<formula>").findAll(sheet1).count() == 2) {
            "expected exactly the two legacy formulae, got ${Regex("<formula>").findAll(sheet1).count()}"
        }

        // --- 2. round trip through the independent reader ---------------------------------------------------

        val read = XlsxStyledBook.read(bytes)
        val readKinds = read.sheets.first { it.name == "Kinds" }.conditionalFormats
        check(readKinds.size == KIND_BLOCKS.size) { "read ${readKinds.size} blocks, wrote ${KIND_BLOCKS.size}" }
        for ((index, block) in KIND_BLOCKS.withIndex()) {
            val back = readKinds[index]
            check(back.sqref == block.sqref) { "block $index sqref ${back.sqref} != ${block.sqref}" }
            check(back.rules.size == block.rules.size) {
                "block $index read ${back.rules.size} rules, wrote ${block.rules.size}"
            }
            for ((position, rule) in block.rules.withIndex()) {
                val round = back.rules[position]
                check(description(round) == description(rule)) {
                    "rule ${rule.priority} round-tripped as ${description(round)}, wrote ${description(rule)}"
                }
            }
        }

        // --- 3. a foreign package ---------------------------------------------------------------------------

        val foreignBytes = foreignPackage()
        File(folder, "r100-foreign.xlsx").writeBytes(foreignBytes)
        val foreign = XlsxStyledBook.read(foreignBytes).sheets.single().conditionalFormats
        check(foreign.size == 6) { "expected 6 surviving foreign blocks, got ${foreign.size}" }
        val flat = foreign.flatMap { it.rules }
        check(flat.size == 6) { "expected 6 surviving foreign rules, got ${flat.size}" }
        val byPriority = flat.associateBy { it.priority }
        check(byPriority.keys == setOf(1, 2, 3, 4, 5, 6)) { "surviving priorities: ${byPriority.keys}" }
        byPriority[1]?.let { rule ->
            check(rule.kind == Kind.TEXT_CONTAINS) { "priority 1 kind ${rule.kind}" }
            check(rule.text == "abc") { "priority 1 text ${rule.text}" }
            check(rule.formulas.isEmpty()) { "a text rule must not echo the formula it dropped: ${rule.formulas}" }
            check(token(rule.format) == "bold;font=FF0000") { "priority 1 format ${token(rule.format)}" }
        }
        byPriority[2]?.let { rule ->
            check(rule.kind == Kind.TEXT_NOT_CONTAINS) { "priority 2 kind ${rule.kind}" }
            check(rule.text == "a*a") { "priority 2 text ${rule.text}" }
            check(rule.format == null) { "a rule with no dxfId must read as no format" }
        }
        byPriority[3]?.let { rule ->
            check(rule.stdDev == 2 && rule.aboveAverage == true && rule.equalAverage == false) {
                "priority 3 flags ${yes(rule.aboveAverage)}/${yes(rule.equalAverage)}/${rule.stdDev}"
            }
        }
        // `aboveAverage="maybe"` is not ST_OnOff, so it is absent — not true, and not a reason to drop the rule.
        byPriority[4]?.let { rule ->
            check(rule.aboveAverage == null) { "an unparsable aboveAverage must read as absent" }
        }
        byPriority[5]?.let { rule -> check(rule.kind == Kind.DUPLICATE) { "priority 5 kind ${rule.kind}" } }
        byPriority[6]?.let { rule -> check(rule.kind == Kind.UNIQUE) { "priority 6 kind ${rule.kind}" } }
        check(flat.none { it.text?.isBlank() == true }) { "a blank text rule must be refused, not kept" }

        // --- 4. refusals ------------------------------------------------------------------------------------

        rejects("text on a cellIs rule") {
            Rule(kind = Kind.CELL_IS, priority = 1, operator = Operator.EQUAL, formulas = listOf("1"), text = "x")
        }
        rejects("text on a duplicateValues rule") {
            Rule(kind = Kind.DUPLICATE, priority = 1, text = "x")
        }
        rejects("a text rule without text") {
            Rule(kind = Kind.TEXT_CONTAINS, priority = 1)
        }
        rejects("a text rule with blank text") {
            Rule(kind = Kind.TEXT_CONTAINS, priority = 1, text = "   ")
        }
        rejects("a text rule longer than MAX_TEXT") {
            Rule(kind = Kind.TEXT_CONTAINS, priority = 1, text = "a".repeat(XlsxConditionalFormatting.MAX_TEXT + 1))
        }
        rejects("a text rule carrying a formula") {
            Rule(kind = Kind.TEXT_CONTAINS, priority = 1, text = "x", formulas = listOf("NOT(ISERROR(SEARCH(\"x\",A1)))"))
        }
        rejects("a text rule carrying an operator") {
            Rule(kind = Kind.TEXT_CONTAINS, priority = 1, text = "x", operator = Operator.EQUAL)
        }
        rejects("a text rule carrying a rank") {
            Rule(kind = Kind.TEXT_CONTAINS, priority = 1, text = "x", rank = 2)
        }
        rejects("a text rule carrying cfvo") {
            Rule(
                kind = Kind.TEXT_CONTAINS,
                priority = 1,
                text = "x",
                thresholds = listOf(Threshold(ThresholdKind.MIN), Threshold(ThresholdKind.MAX)),
            )
        }
        rejects("aboveAverage flags on a cellIs rule") {
            Rule(
                kind = Kind.CELL_IS,
                priority = 1,
                operator = Operator.EQUAL,
                formulas = listOf("1"),
                stdDev = 1,
            )
        }
        rejects("an aboveAverage rule carrying a formula") {
            Rule(kind = Kind.ABOVE_AVERAGE, priority = 1, formulas = listOf("A1>0"))
        }
        rejects("an aboveAverage rule carrying cfvo") {
            Rule(
                kind = Kind.ABOVE_AVERAGE,
                priority = 1,
                thresholds = listOf(Threshold(ThresholdKind.MIN), Threshold(ThresholdKind.MAX)),
            )
        }
        rejects("stdDev below zero") {
            Rule(kind = Kind.ABOVE_AVERAGE, priority = 1, stdDev = -1)
        }
        rejects("stdDev above MAX_STD_DEV") {
            Rule(kind = Kind.ABOVE_AVERAGE, priority = 1, stdDev = XlsxConditionalFormatting.MAX_STD_DEV + 1)
        }
        rejects("a duplicateValues rule carrying a rank") {
            Rule(kind = Kind.DUPLICATE, priority = 1, rank = 1)
        }
        rejects("a uniqueValues rule carrying cfvo") {
            Rule(
                kind = Kind.UNIQUE,
                priority = 1,
                thresholds = listOf(Threshold(ThresholdKind.MIN), Threshold(ThresholdKind.MAX)),
            )
        }
        // The pre-existing invariants must still hold with the new kinds in the picture.
        rejects("a text rule with no format is allowed, but a cellIs rule without one is not") {
            Rule(kind = Kind.CELL_IS, priority = 1, operator = Operator.EQUAL, formulas = listOf("1"))
        }
        rejects("a duplicate priority across blocks") {
            XlsxStyledBook.StyledSheet(
                name = "Dupe",
                cells = emptyList(),
                conditionalFormats = listOf(
                    Block("A1", listOf(Rule(kind = Kind.DUPLICATE, priority = 1))),
                    Block("A2", listOf(Rule(kind = Kind.UNIQUE, priority = 1))),
                ),
            )
        }

        // --- 5. verdicts ------------------------------------------------------------------------------------

        val grid = MapGrid(VALUES)
        // Every cell of every block, including the blanks (A5, C8) that carry no value at all: the verdict for a
        // blank is exactly as much a claim as the verdict for a filled cell.
        val scope = (1..9).map { "A$it" } + (1..8).map { "B$it" } + (1..8).map { "C$it" } + (1..6).map { "D$it" }
        val computed = LinkedHashMap<String, String>()
        for (ref in scope) {
            val cell = SheetRange.parseCell(ref)
            computed[ref] = outcome(XlsxConditionalFormatVerdict.verdict(VERDICT_BLOCKS, grid, cell))
        }

        fun verdict(ref: String) = computed[ref] ?: error("no verdict for $ref")
        fun fires(ref: String): List<Int> = verdict(ref).split('|')[1]
            .split(',').filter { it != "-" }.map { it.toInt() }

        // The whole table, so a semantic drift in any cell is a failure rather than a spot check.
        // Rows are: outcome|fired priorities|stopped|unresolved|bold;italic;strike;underline;fontRgb;fillRgb.
        val expected = linkedMapOf(
            // "apple" and "APPLE" differ only in case: contains, not-contains, begins, ends and duplicate all
            // fire, and the merged painting is bold+italic+underline over the first rule's fill.
            "A1" to "DECIDED|1,2,3,4,5|0|-|1;1;0;1;-;FFC7CE",
            "A2" to "DECIDED|1,2,3,4,5|0|-|1;1;0;1;-;FFC7CE",
            // "banana" is the one cell that matches a*a, so notContainsText refuses it; it is unique.
            "A3" to "DECIDED|6|0|-|0;0;1;0;-;-",
            "A4" to "DECIDED|2,6|0|-|1;0;1;0;-;-",
            // A blank cell errors inside SEARCH, so containsText is false and notContainsText is true; a blank
            // takes part in no duplicate comparison, so neither duplicate rule fires.
            "A5" to "DECIDED|2|0|-|1;0;0;0;-;-",
            "A6" to "DECIDED|1,2,4,6|0|-|1;1;1;0;-;FFC7CE",
            // A7/A9 are the number 12.5: a text predicate sees "12.5", and both copies are duplicates.
            "A7" to "DECIDED|2,5|0|-|1;0;0;1;-;-",
            "A9" to "DECIDED|2,5|0|-|1;0;0;1;-;-",
            // A8 is the *text* "12.5": the same text predicates, but a different duplicate key from A7/A9, so it
            // is unique rather than a duplicate.
            "A8" to "DECIDED|2,6|0|-|1;0;1;0;-;-",
            // 1..8, mean 4.5: strictly above on 5..8, ties-included below on 1..4, one sigma (2.2913) on 7..8.
            "B1" to "DECIDED|8|0|-|1;0;0;0;-;-",
            "B2" to "DECIDED|8|0|-|1;0;0;0;-;-",
            "B3" to "DECIDED|8|0|-|1;0;0;0;-;-",
            "B4" to "DECIDED|8|0|-|1;0;0;0;-;-",
            "B5" to "DECIDED|7|0|-|0;0;0;0;-;FFC7CE",
            "B6" to "DECIDED|7|0|-|0;0;0;0;-;FFC7CE",
            "B7" to "DECIDED|7,9|0|-|0;0;0;0;-;FFC7CE",
            "B8" to "DECIDED|7,9|0|-|0;0;0;0;-;FFC7CE",
            // x, X, y, z, x, y, x: x occurs three times, y twice, z once; case does not separate x from X.
            "C1" to "DECIDED|10|0|-|0;0;0;0;-;FFC7CE",
            "C2" to "DECIDED|10|0|-|0;0;0;0;-;FFC7CE",
            "C3" to "DECIDED|10|0|-|0;0;0;0;-;FFC7CE",
            "C4" to "DECIDED|11|0|-|1;0;0;0;-;-",
            "C5" to "DECIDED|10|0|-|0;0;0;0;-;FFC7CE",
            "C6" to "DECIDED|10|0|-|0;0;0;0;-;FFC7CE",
            "C7" to "DECIDED|10|0|-|0;0;0;0;-;FFC7CE",
            "C8" to "DECIDED|-|0|-|0;0;0;0;-;-",
            // Six 5s: the population sigma is zero, so the band collapses onto the mean and equalAverage alone
            // decides between the two rules.
            "D1" to "DECIDED|12|0|-|0;0;0;0;-;C6EFCE",
            "D2" to "DECIDED|12|0|-|0;0;0;0;-;C6EFCE",
            "D3" to "DECIDED|12|0|-|0;0;0;0;-;C6EFCE",
            "D4" to "DECIDED|12|0|-|0;0;0;0;-;C6EFCE",
            "D5" to "DECIDED|12|0|-|0;0;0;0;-;C6EFCE",
            "D6" to "DECIDED|12|0|-|0;0;0;0;-;C6EFCE",
        )
        for ((ref, want) in expected) {
            check(verdict(ref) == want) { "$ref got ${verdict(ref)}, expected $want" }
        }
        check(expected.size == computed.size) { "${expected.size} expectations for ${computed.size} verdicts" }
        check(fires("A1") == listOf(1, 2, 3, 4, 5)) { "A1 fires ${fires("A1")}" }
        check(fires("B7") == listOf(7, 9)) { "B7 fires ${fires("B7")}" }

        // A stopped scan: stopIfTrue on the first text rule must cut the rest, and say so.
        val stopping = listOf(
            Block(
                "A1:A2",
                listOf(
                    Rule(kind = Kind.TEXT_BEGINS_WITH, priority = 1, text = "Ap", stopIfTrue = true, format = RED),
                    Rule(kind = Kind.TEXT_ENDS_WITH, priority = 2, text = "e", format = BOLD),
                ),
            ),
        )
        val stoppedOutcome = XlsxConditionalFormatVerdict.verdict(stopping, grid, SheetRange.parseCell("A1"))
        check(stoppedOutcome is Outcome.Decided && stoppedOutcome.stopped) { "stopIfTrue did not stop: $stoppedOutcome" }
        check(stoppedOutcome is Outcome.Decided && stoppedOutcome.fired.map { it.priority } == listOf(1)) {
            "a stopped scan must keep only the rule that fired"
        }

        // An error cell has no text and no rank, so those rules are undecided rather than false.
        val errorGrid = MapGrid(mapOf(SheetRange.parseCell("A1") to Value.Err("#DIV/0!")))
        for (kind in listOf(Kind.TEXT_CONTAINS, Kind.TEXT_ENDS_WITH, Kind.ABOVE_AVERAGE)) {
            val block = listOf(
                Block("A1:A2", listOf(Rule(kind = kind, priority = 1, text = if (kind.isText) "a" else null, format = RED))),
            )
            val outcomeForError = XlsxConditionalFormatVerdict.verdict(block, errorGrid, SheetRange.parseCell("A1"))
            check(outcomeForError is Outcome.Undecided) { "$kind on an error cell decided: $outcomeForError" }
        }
        // A duplicate rule is different on purpose: an error participates in no comparison, so it is decided —
        // the cell is not one of several equal values, because it is not equal to anything.
        val dupOnError = listOf(
            Block("A1:A2", listOf(Rule(kind = Kind.DUPLICATE, priority = 1, format = RED))),
        )
        val dupOutcome = XlsxConditionalFormatVerdict.verdict(dupOnError, errorGrid, SheetRange.parseCell("A1"))
        check(dupOutcome is Outcome.Decided && dupOutcome.fired.isEmpty()) { "a duplicate rule on an error: $dupOutcome" }

        // A range larger than MAX_RANGE_CELLS is refused rather than scanned.        // A range larger than MAX_RANGE_CELLS is refused rather than scanned.
        val huge = listOf(Block("A1:A200000", listOf(Rule(kind = Kind.DUPLICATE, priority = 1, format = RED))))
        val hugeOutcome = XlsxConditionalFormatVerdict.verdict(huge, grid, SheetRange.parseCell("A1"))
        check(hugeOutcome is Outcome.Undecided) { "a range beyond MAX_RANGE_CELLS must be refused: $hugeOutcome" }

        // --- 6. intent tables for the independent lane ------------------------------------------------------

        val kindsRows = StringBuilder()
        for (block in KIND_BLOCKS) {
            for (rule in block.rules) {
                kindsRows.append(
                    listOf(
                        block.sqref,
                        rule.priority.toString(),
                        rule.kind.token,
                        rule.text ?: "-",
                        yes(rule.aboveAverage),
                        yes(rule.equalAverage),
                        rule.stdDev?.toString() ?: "-",
                        token(rule.format),
                    ).joinToString("\t"),
                ).append('\n')
            }
        }
        File(folder, "kinds.tsv").writeText(kindsRows.toString(), Charsets.UTF_8)

        val ruleRows = StringBuilder()
        for (block in VERDICT_BLOCKS) {
            for (rule in block.rules) {
                ruleRows.append(
                    listOf(
                        block.sqref,
                        rule.priority.toString(),
                        rule.kind.token,
                        rule.text ?: "-",
                        yes(rule.aboveAverage),
                        yes(rule.equalAverage),
                        rule.stdDev?.toString() ?: "-",
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

        val verdictRows = StringBuilder()
        for ((ref, encoded) in computed) verdictRows.append("$ref\t$encoded\n")
        File(folder, "verdicts.tsv").writeText(verdictRows.toString(), Charsets.UTF_8)

        val summary = "R100_CF_OK assertions=$assertions refusals=$refusals rules=${flat.size} " +
            "verdicts=${computed.size} kinds=${KIND_BLOCKS.sumOf { it.rules.size }}"
        File(folder, "summary.txt").writeText(summary + "\n", Charsets.UTF_8)
        println(summary)
    }
}
