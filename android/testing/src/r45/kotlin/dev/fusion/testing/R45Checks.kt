// SPDX-License-Identifier: Apache-2.0
package dev.fusion.testing

import dev.fusion.a11y.KeyChord
import dev.fusion.airplay.TxtRecord
import dev.fusion.automation.DagTopo
import dev.fusion.browser.model.CspHeader
import dev.fusion.browser.model.PslGate
import dev.fusion.calc.FinMath
import dev.fusion.cast.dlna.SsdpHeader
import dev.fusion.castv2.CastTxtProps
import dev.fusion.continuity.ClipMime
import dev.fusion.designsystem.SpringStep
import dev.fusion.documents.DashEducate
import dev.fusion.documents.PieceTable
import dev.fusion.documents.ProseEducate
import dev.fusion.editor.FindReplace
import dev.fusion.files.MerkleTree
import dev.fusion.hw.HidReportAudit
import dev.fusion.index.Bm25Rank
import dev.fusion.lok.OdtExtract
import dev.fusion.maps.UtmGrid
import dev.fusion.native.RleBitset
import dev.fusion.notes.TocTree
import dev.fusion.ooxml.CellErr
import dev.fusion.ooxml.DocxNumbering
import dev.fusion.ooxml.DocxPackage
import dev.fusion.ooxml.FormulaEval
import dev.fusion.ooxml.FormulaXlsx
import dev.fusion.privacy.OverlayTouchGuard
import dev.fusion.privacy.TailsCadence
import dev.fusion.psd.PsdLayerRecords
import dev.fusion.security.CtSctPolicy
import dev.fusion.security.DmaGuardAudit
import dev.fusion.settingsui.EnvOverride
import dev.fusion.shell.TileLabelFit
import dev.fusion.vault.HkdfExpand
import dev.fusion.windowing.WorkAreaFit
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.charset.StandardCharsets
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.math.abs

/** R45 executed contracts across 33 new kernels + DashEducate.educateOutside. Host JVM only; NOT Android/device evidence. */
object R45Checks {
    private var count = 0

    private fun ok(cond: Boolean, msg: String) {
        if (!cond) throw AssertionError(msg)
        count++
    }

    private fun refuses(msg: String, block: () -> Unit) {
        try {
            block()
        } catch (_: IllegalArgumentException) {
            count++
            return
        }
        throw AssertionError("expected refusal: $msg")
    }

    private fun near(a: Double, b: Double, eps: Double = 1e-9): Boolean = abs(a - b) <= eps

    private fun hexBytes(hex: String): ByteArray {
        val clean = hex.replace(" ", "").replace("\n", "")
        val out = ByteArray(clean.length / 2)
        for (i in out.indices) {
            out[i] = clean.substring(i * 2, i * 2 + 2).toInt(16).toByte()
        }
        return out
    }

    private fun toHex(bytes: ByteArray): String = buildString(bytes.size * 2) {
        val h = "0123456789abcdef"
        for (b in bytes) {
            val v = b.toInt() and 0xFF
            append(h[v ushr 4]); append(h[v and 0x0F])
        }
    }

    private fun formulaEval() {
        val sheet = FormulaEval.evaluateSheet(
            mapOf(
                "A1" to FormulaEval.CellInput.NumberLiteral(120.0),
                "A2" to FormulaEval.CellInput.NumberLiteral(80.0),
                "A3" to FormulaEval.CellInput.NumberLiteral(50.0),
                "A4" to FormulaEval.CellInput.TextLiteral("note"),
                "B1" to FormulaEval.CellInput.Formula("=SUM(\$A\$1:\$A\$4)"),
                "B2" to FormulaEval.CellInput.Formula("=AVERAGE(A1:A4)"),
                "B3" to FormulaEval.CellInput.Formula("=MIN(A1:A3) & \"..\" & MAX(A1:A3)"),
                "B4" to FormulaEval.CellInput.Formula("=COUNT(A1:A4)"),
                "B5" to FormulaEval.CellInput.Formula("=COUNTA(A1:A4)"),
                "C1" to FormulaEval.CellInput.Formula("=IF(B1>=250, ROUND(B1*12.5%, 2), 0)"),
                "C2" to FormulaEval.CellInput.Formula("=IF(A2=80, 42, 1/0)"), // lazy IF avoids untaken #DIV/0!
                "C3" to FormulaEval.CellInput.Formula("=IFERROR(10/0, 999)"),
                "C4" to FormulaEval.CellInput.Formula("=INDEX(A1:A4, MATCH(\"NOTE\", A1:A4, 0))"),
                "C5" to FormulaEval.CellInput.Formula("=MOD(-7, 3)"),
                "C6" to FormulaEval.CellInput.Formula("=INT(-2.3)"),
                "C7" to FormulaEval.CellInput.Formula("=AND(1=1, NOT(FALSE), OR(FALSE, A1>100))"),
                "C8" to FormulaEval.CellInput.Formula("=LEN(CONCAT(\"ab\", 12))"),
                "D1" to FormulaEval.CellInput.Formula("=1/0"),
                "D2" to FormulaEval.CellInput.Formula("=SUM(A1, D1)"),
                "D3" to FormulaEval.CellInput.Formula("=NO_SUCH_FN(1)"),
                "D4" to FormulaEval.CellInput.Formula("=MATCH(\"missing\", A1:A4, 0)"),
                "D5" to FormulaEval.CellInput.Formula("=INDEX(A1:A3, 9)"),
            ),
        )
        ok(sheet.values["B1"] == FormulaEval.CellValue.Num(250.0), "SUM ignores text in range")
        ok(near((sheet.values["B2"] as FormulaEval.CellValue.Num).value, 250.0 / 3.0, 1e-12), "AVERAGE numeric count")
        ok(sheet.values["B3"] == FormulaEval.CellValue.Text("50..120"), "MIN/MAX + & concat")
        ok(sheet.values["B4"] == FormulaEval.CellValue.Num(3.0), "COUNT numeric only")
        ok(sheet.values["B5"] == FormulaEval.CellValue.Num(4.0), "COUNTA non-empty")
        ok(sheet.values["C1"] == FormulaEval.CellValue.Num(31.25), "IF + ROUND + %")
        ok(sheet.values["C2"] == FormulaEval.CellValue.Num(42.0), "IF lazy branch skips 1/0")
        ok(sheet.values["C3"] == FormulaEval.CellValue.Num(999.0), "IFERROR catches #DIV/0!")
        ok(sheet.values["C4"] == FormulaEval.CellValue.Text("note"), "INDEX + case-insensitive MATCH")
        ok(sheet.values["C5"] == FormulaEval.CellValue.Num(2.0), "Excel floor MOD(-7,3)==2")
        ok(sheet.values["C6"] == FormulaEval.CellValue.Num(-3.0), "Excel INT(-2.3)==-3")
        ok(sheet.values["C7"] == FormulaEval.CellValue.Bool(true), "AND/OR/NOT")
        ok(sheet.values["C8"] == FormulaEval.CellValue.Num(4.0), "LEN(CONCAT)")
        ok(sheet.values["D1"] == FormulaEval.CellValue.Err(CellErr.Err.DIV0), "1/0 -> #DIV/0!")
        ok(sheet.values["D2"] == FormulaEval.CellValue.Err(CellErr.Err.DIV0), "SUM propagates #DIV/0!")
        ok(sheet.values["D3"] == FormulaEval.CellValue.Err(CellErr.Err.NAME), "unknown fn -> #NAME?")
        ok(sheet.values["D4"] == FormulaEval.CellValue.Err(CellErr.Err.NA), "MATCH miss -> #N/A")
        ok(sheet.values["D5"] == FormulaEval.CellValue.Err(CellErr.Err.REF), "INDEX OOB -> #REF!")

        val dirty = FormulaEval.dirtyDependents(sheet.dependencies, setOf("A1"))
        ok("B1" in dirty && "C1" in dirty && dirty.indexOf("B1") < dirty.indexOf("C1"), "topological dirty closure")
        ok("C5" !in dirty, "unrelated cell excluded from dirty closure")

        refuses("direct self cycle") {
            FormulaEval.evaluateSheet(mapOf("A1" to FormulaEval.CellInput.Formula("=A1+1")))
        }
        refuses("3-node cycle") {
            FormulaEval.evaluateSheet(
                mapOf(
                    "A1" to FormulaEval.CellInput.Formula("=C1+1"),
                    "B1" to FormulaEval.CellInput.Formula("=A1+1"),
                    "C1" to FormulaEval.CellInput.Formula("=B1+1"),
                ),
            )
        }
        refuses("volatile NOW refused") {
            FormulaEval.evaluateSheet(mapOf("A1" to FormulaEval.CellInput.Formula("=NOW()")))
        }
        refuses("3D sheet ref refused") {
            FormulaEval.evaluateSheet(mapOf("A1" to FormulaEval.CellInput.Formula("=Sheet2!A1")))
        }
    }

    private fun formulaXlsx() {
        val cells = linkedMapOf(
            "A1" to FormulaEval.CellInput.TextLiteral("Revenue"),
            "B1" to FormulaEval.CellInput.NumberLiteral(1500.5),
            "B2" to FormulaEval.CellInput.NumberLiteral(499.5),
            "B3" to FormulaEval.CellInput.Formula("=SUM(B1:B2)"),
            "B4" to FormulaEval.CellInput.Formula("=IF(B3=2000, \"ON_TARGET\", \"OFF\")"),
            "B5" to FormulaEval.CellInput.Formula("=B3>1000"),
            "B6" to FormulaEval.CellInput.Formula("=1/0"),
            "C1" to FormulaEval.CellInput.BoolLiteral(false),
        )
        val bytes1 = FormulaXlsx.write("Budget2026", cells)
        val bytes2 = FormulaXlsx.write("Budget2026", cells)
        ok(bytes1.contentEquals(bytes2), "deterministic zero-epoch .xlsx bytes")

        val loaded = FormulaXlsx.read(bytes1)
        ok(loaded.sheetName == "Budget2026", "sheet name round-trip")
        ok(loaded.cachedValues["B3"] == FormulaEval.CellValue.Num(2000.0), "cached numeric formula <v>")
        ok(loaded.cachedValues["B4"] == FormulaEval.CellValue.Text("ON_TARGET"), "cached str formula <v>")
        ok(loaded.cachedValues["B5"] == FormulaEval.CellValue.Bool(true), "cached bool formula <v>")
        ok(loaded.cachedValues["B6"] == FormulaEval.CellValue.Err(CellErr.Err.DIV0), "cached error formula <v>")
        ok(loaded.cachedValues["A1"] == FormulaEval.CellValue.Text("Revenue"), "inlineStr literal round-trip")
        ok(loaded.cachedValues["C1"] == FormulaEval.CellValue.Bool(false), "bool literal round-trip")
        ok((loaded.inputs["B3"] as FormulaEval.CellInput.Formula).body == "SUM(B1:B2)", "formula expression preserved")
        ok(loaded.recalculate().values == loaded.cachedValues, "recalculate matches cached values")

        refuses("illegal sheet name char") { FormulaXlsx.write("Bad*Name", cells) }
        refuses("sheet name > 31 chars") { FormulaXlsx.write("X".repeat(32), cells) }
        refuses("macro xlsm vbaProject.bin refused") {
            val baos = ByteArrayOutputStream()
            ZipOutputStream(baos).use { z ->
                z.putNextEntry(ZipEntry("xl/vbaProject.bin"))
                z.write(byteArrayOf(1, 2, 3))
                z.closeEntry()
            }
            FormulaXlsx.read(baos.toByteArray())
        }
    }

    private fun docxNumbering() {
        val xml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <w:numbering xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main">
              <w:abstractNum w:abstractNumId="10">
                <w:lvl w:ilvl="0"><w:start w:val="1"/><w:numFmt w:val="upperRoman"/><w:lvlText w:val="%1."/></w:lvl>
                <w:lvl w:ilvl="1"><w:start w:val="1"/><w:numFmt w:val="decimal"/><w:lvlText w:val="%1.%2)"/><w:lvlRestart w:val="1"/></w:lvl>
                <w:lvl w:ilvl="2"><w:start w:val="1"/><w:numFmt w:val="lowerLetter"/><w:lvlText w:val="(%3)"/></w:lvl>
              </w:abstractNum>
              <w:abstractNum w:abstractNumId="20">
                <w:lvl w:ilvl="0"><w:start w:val="1"/><w:numFmt w:val="bullet"/><w:lvlText w:val="*"/></w:lvl>
              </w:abstractNum>
              <w:num w:numId="1"><w:abstractNumId w:val="10"/></w:num>
              <w:num w:numId="2">
                <w:abstractNumId w:val="10"/>
                <w:lvlOverride w:ilvl="0"><w:startOverride w:val="4"/></w:lvlOverride>
              </w:num>
              <w:num w:numId="3"><w:abstractNumId w:val="20"/></w:num>
            </w:numbering>
        """.trimIndent()
        val defs = DocxNumbering.parseXml(xml)
        val rendered = DocxNumbering.renderSequence(
            defs,
            listOf(
                DocxNumbering.ParagraphRef(1, 0, "Part One"),
                DocxNumbering.ParagraphRef(1, 1, "Sec A"),
                DocxNumbering.ParagraphRef(1, 1, "Sec B"),
                DocxNumbering.ParagraphRef(1, 2, "Sub a"),
                DocxNumbering.ParagraphRef(1, 0, "Part Two"),
                DocxNumbering.ParagraphRef(1, 1, "Sec Reset"),
                DocxNumbering.ParagraphRef(2, 0, "Override IV"),
                DocxNumbering.ParagraphRef(3, 0, "Bullet"),
                DocxNumbering.ParagraphRef(null, 0, "Plain"),
            ),
        )
        ok(rendered.map { it.marker } == listOf("I.", "I.1)", "I.2)", "(a)", "II.", "II.1)", "IV.", "*", ""), "hierarchical numbering + restart + startOverride")
        ok(DocxNumbering.formatCounter(27, DocxNumbering.NumFormat.LOWER_LETTER) == "aa", "27th lowerLetter == aa")
        ok(DocxNumbering.formatCounter(1999, DocxNumbering.NumFormat.UPPER_ROMAN) == "MCMXCIX", "roman 1999")
        ok(DocxNumbering.formatCounter(7, DocxNumbering.NumFormat.DECIMAL_ZERO) == "07", "decimalZero 07")

        refuses("roman 0") { DocxNumbering.formatCounter(0, DocxNumbering.NumFormat.UPPER_ROMAN) }
        refuses("roman 4000") { DocxNumbering.formatCounter(4000, DocxNumbering.NumFormat.UPPER_ROMAN) }
        refuses("unknown numId") {
            DocxNumbering.renderSequence(defs, listOf(DocxNumbering.ParagraphRef(99, 0, "x")))
        }
        refuses("ilvl 9") {
            DocxNumbering.renderSequence(defs, listOf(DocxNumbering.ParagraphRef(1, 9, "x")))
        }
    }

    private fun docxPackage() {
        val doc = DocxPackage.Document(
            title = "Spec",
            blocks = listOf(
                DocxPackage.Block.Heading(1, listOf(DocxPackage.Run("Fusion Writer"))),
                DocxPackage.Block.Paragraph(
                    listOf(
                        DocxPackage.Run("Clean "),
                        DocxPackage.Run("bold", bold = true),
                        DocxPackage.Run(" and "),
                        DocxPackage.Run("code()", code = true),
                        DocxPackage.Run(" text.", italic = true, underline = true, strike = true),
                    ),
                ),
                DocxPackage.Block.ListItem(ordered = false, level = 0, runs = listOf(DocxPackage.Run("Bullet 1"))),
                DocxPackage.Block.ListItem(ordered = true, level = 1, runs = listOf(DocxPackage.Run("Step 1.1"))),
                DocxPackage.Block.BlockQuote(listOf(DocxPackage.Run("Zero trust on repair."))),
                DocxPackage.Block.CodeBlock(listOf("val x = 1", "println(x)")),
                DocxPackage.Block.Table(listOf(listOf("ColA", "ColB"), listOf("10", "20"))),
            ),
        )
        val zipA = DocxPackage.write(doc)
        val zipB = DocxPackage.write(doc)
        ok(zipA.contentEquals(zipB), "deterministic .docx archive")
        val roundTrip = DocxPackage.read(zipA)
        ok(roundTrip.blocks == doc.blocks, "full structural .docx round-trip across 6 block types + 5 run styles")

        refuses("heading level 7") { DocxPackage.Block.Heading(7, listOf(DocxPackage.Run("x"))) }
        refuses("ragged table") { DocxPackage.Block.Table(listOf(listOf("a", "b"), listOf("only-one"))) }
        refuses("NUL char in run") { DocxPackage.Run("bad\u0000char") }
        refuses("docm vbaProject.bin refused") {
            val baos = ByteArrayOutputStream()
            ZipOutputStream(baos).use { z ->
                z.putNextEntry(ZipEntry("word/vbaProject.bin"))
                z.write(byteArrayOf(0xCA.toByte(), 0xFE.toByte()))
                z.closeEntry()
            }
            DocxPackage.read(baos.toByteArray())
        }
    }

    private fun pieceTable() {
        var hist = PieceTable.createHistory("Hello world")
        hist = hist.edit { it.insert(5, ", brave") }
        ok(hist.current.materialize() == "Hello, brave world", "insert middle")
        hist = hist.edit { it.replace(7, 12, "clean") }
        ok(hist.current.materialize() == "Hello, clean world", "replace span")
        hist = hist.edit { it.insert(hist.current.length, "\nLine two") }
        ok(hist.current.slice(7, 18) == "clean world", "cross-piece slice")
        ok(hist.current.lineColAt(0) == PieceTable.LineCol(1, 1), "origin lineCol")
        ok(hist.current.lineColAt(hist.current.length) == PieceTable.LineCol(2, 9), "line 2 col")

        val snapAfter3 = hist.current.materialize()
        val u1 = hist.undo()
        ok(u1.current.materialize() == "Hello, clean world", "undo 1")
        val u2 = u1.undo()
        ok(u2.current.materialize() == "Hello, brave world", "undo 2")
        val u3 = u2.undo()
        ok(u3.current.materialize() == "Hello world", "undo 3 back to original")
        ok(!u3.canUndo && u3.canRedo, "undo exhausted, redo available")
        val rAll = u3.redo().redo().redo()
        ok(rAll.current.materialize() == snapAfter3, "3x redo exact state")

        refuses("empty undo") { PieceTable.createHistory("a").undo() }
        refuses("empty redo") { PieceTable.createHistory("a").redo() }
        refuses("bad slice") { PieceTable.create("abc").slice(2, 1) }
        refuses("oob insert") { PieceTable.create("abc").insert(4, "x") }
    }

    private fun proseEducate() {
        // Direct DashEducate.educateOutside check (TOUCHED R44 §J enhancement)
        val rawDash = "Wait -- see `cmd --dry-run` and 1--9..."
        val codeIdx = rawDash.indexOf("`cmd --dry-run`")
        val outsideOnly = DashEducate.educateOutside(rawDash, listOf(codeIdx until (codeIdx + "`cmd --dry-run`".length)))
        ok("`cmd --dry-run`" in outsideOnly, "DashEducate.educateOutside preserves --dry-run")
        ok("Wait \u2013 see" in outsideOnly && "1\u20139\u2026" in outsideOnly, "educateOutside educates prose slices")
        refuses("DashEducate oob excluded range") { DashEducate.educateOutside("abc", listOf(0..5)) }

        // Full ProseEducate composition (fences + multi-backticks + URLs + SmartQuote + DashEducate)
        val md = """
            She said "run ``echo "a--b" ...`` on https://example.com/a--b/v1...v2/path now" --- okay...
            ```sh
            curl --silent "https://raw.example/x---y"
            ```
        """.trimIndent()
        val res = ProseEducate.educate(md)
        ok("``echo \"a--b\" ...``" in res.text, "double-backtick inline code untouched")
        ok("https://example.com/a--b/v1...v2/path" in res.text, "URL internal dashes and dots untouched")
        ok("curl --silent \"https://raw.example/x---y\"" in res.text, "fenced code block untouched")
        ok("\u201crun " in res.text && "now\u201d \u2014 okay\u2026" in res.text, "prose curly quotes + em-dash + ellipsis")
        ok(res.protectedSpans.size == 3, "detected inline code, URL, and fenced block")
        ok(res.unbalancedDoubleQuotes == 0, "balanced prose quotes")

        // Adversarial patch test: trailing prose ellipsis/period after URL strips punctuation from URL span while keeping URL body untouched
        val trailingPunct = ProseEducate.educate("See https://example.com/a--b... right now.")
        ok("https://example.com/a--b\u2026 right now." in trailingPunct.text, "URL body a--b preserved while trailing prose ... educates")

        val unbal = ProseEducate.educate("He said \"unclosed `\"ignored\"`")
        ok(unbal.unbalancedDoubleQuotes == 1, "unbalanced prose quote detected without counting code quotes")
    }

    private fun findReplace() {
        val src = "foo Foo FOO foobar `foo` end"
        val codeStart = src.indexOf("`foo`")
        val res = FindReplace.replaceAll(
            text = src,
            query = "foo",
            replacement = "bar",
            options = FindReplace.Options(
                caseSensitive = false,
                wholeWord = true,
                preserveCase = true,
                excludedRanges = listOf(codeStart..(codeStart + 4)),
            ),
        )
        ok(res.output == "bar Bar BAR foobar `foo` end", "whole-word + preserveCase + excluded span")
        ok(res.replacedCount == 3, "3 whole-word non-excluded replacements")

        val nonOverlap = FindReplace.findAll("aaaa", "aa")
        ok(nonOverlap.map { it.start } == listOf(0, 2), "non-overlapping literal scan")

        refuses("empty query") { FindReplace.findAll("abc", "") }
        refuses("oob excluded range") {
            FindReplace.findAll("abc", "a", FindReplace.Options(excludedRanges = listOf(0..9)))
        }
    }

    private fun odtExtract() {
        val contentXml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <office:document-content xmlns:office="urn:oasis:names:tc:opendocument:xmlns:office:1.0"
                xmlns:text="urn:oasis:names:tc:opendocument:xmlns:text:1.0"
                xmlns:table="urn:oasis:names:tc:opendocument:xmlns:table:1.0">
              <office:body><office:text>
                <text:h text:outline-level="1">ODF Spec</text:h>
                <text:p>Hello<text:s text:c="3"/>world</text:p>
                <text:list><text:list-item><text:p>Item A</text:p></text:list-item></text:list>
                <table:table table:name="Grid">
                  <table:table-row>
                    <table:table-cell><text:p>K</text:p></table:table-cell>
                    <table:table-cell table:number-columns-repeated="2"><text:p>V</text:p></table:table-cell>
                  </table:table-row>
                </table:table>
              </office:text></office:body>
            </office:document-content>
        """.trimIndent()

        val baos = ByteArrayOutputStream()
        ZipOutputStream(baos).use { z ->
            z.putNextEntry(ZipEntry("mimetype"))
            z.write("application/vnd.oasis.opendocument.text".toByteArray(StandardCharsets.UTF_8))
            z.closeEntry()
            z.putNextEntry(ZipEntry("content.xml"))
            z.write(contentXml.toByteArray(StandardCharsets.UTF_8))
            z.closeEntry()
        }
        val doc = OdtExtract.readOdtZip(baos.toByteArray())
        ok(doc.blocks[0] == OdtExtract.Block.Heading(1, "ODF Spec"), "ODT heading")
        ok(doc.blocks[1] == OdtExtract.Block.Paragraph("Hello   world"), "ODT text:s space run")
        ok(doc.blocks[2] == OdtExtract.Block.ListItem(1, "Item A"), "ODT list item")
        ok(doc.blocks[3] == OdtExtract.Block.Table("Grid", listOf(listOf("K", "V", "V"))), "ODT repeated cell")

        refuses("ODT million-column bomb refused") {
            OdtExtract.parseContentXml(
                """<doc><table:table><table:table-row><table:table-cell table:number-columns-repeated="1048576"><text:p>x</text:p></table:table-cell></table:table-row></table:table></doc>""",
            )
        }
        refuses("ODT space bomb refused") {
            OdtExtract.parseContentXml("""<doc><text:p><text:s text:c="9999"/></text:p></doc>""")
        }
        refuses("ODT DOCTYPE XXE refused") {
            OdtExtract.parseContentXml("""<!DOCTYPE foo><doc/>""")
        }
    }

    private fun rleBitset() {
        val raw = listOf(10, 1, 2, 3, 12, 11, 2, 100, 101)
        val bs = RleBitset.ofInts(raw)
        ok(bs.runs == listOf(RleBitset.Run(1, 3), RleBitset.Run(10, 3), RleBitset.Run(100, 2)), "coalesced canonical runs")
        ok(bs.cardinality == 8L, "cardinality deduplicated")
        ok(bs.contains(11) && !bs.contains(9) && !bs.contains(-1), "O(log R) membership")

        val wire = RleBitset.encode(bs)
        val decoded = RleBitset.decode(wire)
        ok(decoded == bs && decoded.toIntList() == listOf(1, 2, 3, 10, 11, 12, 100, 101), "LEB128 codec bijection")

        val other = RleBitset.ofInts(listOf(3, 4, 10, 50))
        ok(RleBitset.intersect(bs, other).toIntList() == listOf(3, 10), "set intersection")
        ok(RleBitset.difference(bs, other).toIntList() == listOf(1, 2, 11, 12, 100, 101), "set difference")
        ok(RleBitset.union(bs, other).contains(50) && RleBitset.union(bs, other).runs.first() == RleBitset.Run(1, 4), "union coalesces 1..3 + 3..4 -> 1..4")

        val hugeRun = RleBitset.ofRuns(listOf(RleBitset.Run(0, 2_000_000)))
        ok(hugeRun.cardinality == 2_000_000L, "O(1) cardinality on 2M-bit run")
        refuses("RLE materialization bomb refused") { hugeRun.toIntList() }
        refuses("bad magic") { RleBitset.decode(byteArrayOf(0, 0, 1, 0)) }
        refuses("trailing garbage") { RleBitset.decode(wire + byteArrayOf(0)) }
        refuses("overlong varint refused") { RleBitset.decode(byteArrayOf(0x52, 0x42, 1, 0x80.toByte(), 0x00)) }
    }

    private fun finMath() {
        // 0% APR exact analytical limit
        ok(near(FinMath.pmt(0.0, 12.0, 1200.0), -100.0, 1e-12), "0% rate PMT exact")
        ok(near(FinMath.pv(0.0, 12.0, -100.0), 1200.0, 1e-12), "0% rate PV exact")
        ok(near(FinMath.fv(0.0, 12.0, -100.0, 0.0), 1200.0, 1e-12), "0% rate FV exact")

        // Standard 6% APR (0.5%/mo) 360-month $200,000 mortgage
        val monthly = FinMath.pmt(0.005, 360.0, 200_000.0)
        ok(near(monthly, -1199.1010503055139, 1e-7), "30yr mortgage PMT")
        ok(near(FinMath.pv(0.005, 360.0, monthly), 200_000.0, 1e-6), "PV(PMT) inverse identity")
        ok(near(FinMath.fv(0.005, 360.0, monthly, 200_000.0), 0.0, 1e-6), "FV after 360 payments == 0")

        // Annuity due (type = 1) vs ordinary (type = 0): PMT(type=0) == PMT(type=1) * (1 + r)
        val pmtDue = FinMath.pmt(0.01, 24.0, 10_000.0, 0.0, type = 1)
        val pmtOrd = FinMath.pmt(0.01, 24.0, 10_000.0, 0.0, type = 0)
        ok(near(pmtOrd, pmtDue * 1.01, 1e-9), "annuity-due scaling law")

        ok(near(FinMath.npv(0.10, listOf(110.0, 121.0)), 200.0, 1e-9), "NPV exact discount")
        ok(near(FinMath.cagr(100.0, 200.0, 10.0), Math.pow(2.0, 0.1) - 1.0, 1e-12), "CAGR 10yr doubling")

        refuses("rate <= -100%") { FinMath.pmt(-1.0, 10.0, 100.0) }
        refuses("nper <= 0") { FinMath.pmt(0.05, 0.0, 100.0) }
        refuses("bad type 2") { FinMath.pmt(0.05, 10.0, 100.0, 0.0, type = 2) }
    }

    private fun tocTree() {
        val toc = TocTree.build(
            listOf(
                TocTree.HeadingInput(1, "Intro", line = 1),
                TocTree.HeadingInput(2, "Setup", line = 5),
                TocTree.HeadingInput(2, "Setup", line = 12),
                TocTree.HeadingInput(4, "Deep Jump", line = 20),
                TocTree.HeadingInput(1, "Appendix", line = 30),
            ),
        )
        ok(toc.entries.map { it.number } == listOf("1", "1.1", "1.2", "1.2.0.1", "2"), "legal section numbers preserve visible gap")
        ok(toc.entries.map { it.anchor } == listOf("intro", "setup", "setup-2", "deep-jump", "appendix"), "deduplicated anchors")
        ok(toc.skippedGaps == listOf(TocTree.SkippedLevelGap(3, 2, 4, 20)), "H2 -> H4 gap audited")

        refuses("level 0") { TocTree.build(listOf(TocTree.HeadingInput(0, "Bad"))) }
        refuses("blank heading") { TocTree.build(listOf(TocTree.HeadingInput(1, "   "))) }
    }

    private fun cspHeader() {
        val origin = CspHeader.parseOrigin("https://app.example.com")
        val p = CspHeader.parse(
            "default-src 'self'; img-src 'self' https://*.cdn.example.com data:; " +
                "script-src 'self' 'unsafe-inline' 'nonce-R45Secret' 'strict-dynamic'; " +
                "object-src 'none'; script-src *",
        )
        ok(p.duplicateDirectives == listOf("script-src"), "CSP duplicate directive logged & ignored")
        ok(CspHeader.allowsUrl(p, "connect-src", origin, "https://app.example.com/api"), "connect-src falls back to default-src 'self'")
        ok(!CspHeader.allowsUrl(p, "connect-src", origin, "https://evil.example.org/api"), "cross-origin connect blocked")
        ok(CspHeader.allowsUrl(p, "frame-ancestors", origin, "https://other.org"), "frame-ancestors NEVER falls back to default-src")
        ok(CspHeader.allowsUrl(p, "img-src", origin, "https://static.cdn.example.com/a.png"), "wildcard subdomain allowed")
        ok(!CspHeader.allowsUrl(p, "img-src", origin, "https://cdn.example.com/a.png"), "*.cdn does NOT match bare cdn")
        ok(CspHeader.allowsUrl(p, "img-src", origin, "data:image/png;base64,AA=="), "data: allowed on img-src")
        ok(!CspHeader.allowsUrl(p, "object-src", origin, "https://app.example.com/x.swf"), "'none' rejects all")
        // 'strict-dynamic' suppresses 'self' and 'unsafe-inline', allowing ONLY valid nonce!
        ok(!CspHeader.allowsUrl(p, "script-src", origin, "https://app.example.com/app.js"), "'strict-dynamic' ignores 'self' URL without nonce")
        ok(CspHeader.allowsUrl(p, "script-src", origin, "https://thirdparty.org/lib.js", nonce = "R45Secret"), "'strict-dynamic' admits matching nonce")
        ok(!CspHeader.allowsInline(p, "script-src"), "'unsafe-inline' suppressed by nonce + strict-dynamic")
        ok(CspHeader.allowsInline(p, "script-src", nonce = "R45Secret"), "matching inline nonce allowed")
        ok(!CspHeader.allowsUrl(p, "img-src", origin, "javascript:alert(1)"), "javascript: scheme unconditionally blocked")

        refuses("CRLF header injection refused") { CspHeader.parse("default-src 'self'\r\nX-Evil: 1") }
    }

    private fun pslGate() {
        ok(PslGate.classify("sub.login.co.uk") == PslGate.Decision.Registrable("login.co.uk", "co.uk"), "co.uk multi-level registrable")
        ok(PslGate.classify("co.uk") == PslGate.Decision.PublicSuffixOnly("co.uk"), "bare co.uk is public suffix")
        ok(PslGate.classify("uk") == PslGate.Decision.PublicSuffixOnly("uk"), "bare uk is public suffix")
        // Wildcard + !exception priority (`*.kawasaki.jp` vs `!city.kawasaki.jp`, `*.ck` vs `!www.ck`)
        ok(PslGate.classify("ward.kawasaki.jp") == PslGate.Decision.PublicSuffixOnly("ward.kawasaki.jp"), "wildcard *.kawasaki.jp is public suffix")
        ok(PslGate.classify("portal.ward.kawasaki.jp") == PslGate.Decision.Registrable("portal.ward.kawasaki.jp", "ward.kawasaki.jp"), "under wildcard suffix")
        ok(PslGate.classify("city.kawasaki.jp") == PslGate.Decision.Registrable("city.kawasaki.jp", "kawasaki.jp"), "!city.kawasaki.jp exception wins over *.kawasaki.jp")
        ok(PslGate.classify("www.ck") == PslGate.Decision.Registrable("www.ck", "ck"), "!www.ck exception wins over *.ck")
        ok(PslGate.classify("foo.ck") == PslGate.Decision.PublicSuffixOnly("foo.ck"), "foo.ck matched by *.ck")
        ok(PslGate.classify("host.corp.internal") == PslGate.Decision.UnlistedTld("internal"), "unlisted TLD fail-closed")

        // WebAuthn RP ID composition
        ok(PslGate.isValidRpIdForHost("auth.login.co.uk", "login.co.uk"), "registrable parent RP ID valid")
        ok(!PslGate.isValidRpIdForHost("auth.login.co.uk", "co.uk"), "co.uk supercookie/RP-ID attack blocked")
        ok(!PslGate.isValidRpIdForHost("a.corp.internal", "corp.internal"), "unlisted TLD RP ID blocked")

        refuses("trailing dot") { PslGate.classify("example.com.") }
        refuses("IPv4 literal") { PslGate.classify("192.168.1.1") }
    }

    private fun ctSctPolicy() {
        val logGoogle = "11".repeat(32)
        val logCloudflare = "22".repeat(32)
        val logGoogle2 = "33".repeat(32)
        val revokedEmudhra = "de".repeat(32)
        val goodRoot = "aa".repeat(32)
        val goodLeaf = "bb".repeat(32)
        val now = 1_793_000_000_000L

        fun buildSct(logHex: String, ts: Long, sigBytes: Int = 64): ByteArray {
            val bb = ByteBuffer.allocate(1 + 32 + 8 + 2 + 1 + 1 + 2 + sigBytes).order(ByteOrder.BIG_ENDIAN)
            bb.put(0) // v1
            bb.put(hexBytes(logHex))
            bb.putLong(ts)
            bb.putShort(0) // ext len
            bb.put(4) // SHA256
            bb.put(3) // ECDSA
            bb.putShort(sigBytes.toShort())
            bb.put(ByteArray(sigBytes) { 0x5A })
            return bb.array()
        }

        val logs = listOf(
            CtSctPolicy.TrustedLog(logGoogle, "Google"),
            CtSctPolicy.TrustedLog(logGoogle2, "Google"),
            CtSctPolicy.TrustedLog(logCloudflare, "Cloudflare"),
        )
        val sctG1 = buildSct(logGoogle, now - 60_000L)
        val sctG2 = buildSct(logGoogle2, now - 50_000L)
        val sctCf = buildSct(logCloudflare, now - 40_000L)

        val pass = CtSctPolicy.evaluate(
            chainSpkiSha256Hex = listOf(goodLeaf, goodRoot),
            sctPayloads = listOf(sctG1, sctCf),
            trustedLogs = logs,
            revokedCaSpkiSha256Hex = setOf(revokedEmudhra),
            nowEpochMs = now,
            certLifetimeDays = 90,
        )
        ok(pass.verdict == CtSctPolicy.Verdict.COMPLIANT && pass.distinctOperators.size == 2, "2 operators compliant for 90d cert")

        val singleOp = CtSctPolicy.evaluate(
            chainSpkiSha256Hex = listOf(goodLeaf, goodRoot),
            sctPayloads = listOf(sctG1, sctG2),
            trustedLogs = logs,
            revokedCaSpkiSha256Hex = setOf(revokedEmudhra),
            nowEpochMs = now,
            certLifetimeDays = 90,
        )
        ok(singleOp.verdict == CtSctPolicy.Verdict.REJECT_SINGLE_OPERATOR, "2 logs from 1 operator rejected")

        val longCertNeeds3 = CtSctPolicy.evaluate(
            chainSpkiSha256Hex = listOf(goodLeaf, goodRoot),
            sctPayloads = listOf(sctG1, sctCf),
            trustedLogs = logs,
            revokedCaSpkiSha256Hex = setOf(revokedEmudhra),
            nowEpochMs = now,
            certLifetimeDays = 200,
        )
        ok(longCertNeeds3.verdict == CtSctPolicy.Verdict.REJECT_INSUFFICIENT_SCTS, ">180d cert requires 3 SCTs")

        val distrusted = CtSctPolicy.evaluate(
            chainSpkiSha256Hex = listOf(goodLeaf, revokedEmudhra),
            sctPayloads = listOf(sctG1, sctG2, sctCf),
            trustedLogs = logs,
            revokedCaSpkiSha256Hex = setOf(revokedEmudhra),
            nowEpochMs = now,
            certLifetimeDays = 90,
        )
        ok(distrusted.verdict == CtSctPolicy.Verdict.REJECT_CA_DISTRUSTED, "CVE-2026-113470 emergency CA SPKI distrust overrides SCTs")

        refuses("SCT v2 unsupported") { CtSctPolicy.parseSct(sctG1.clone().also { it[0] = 1 }) }
        refuses("SCT trailing byte refused") { CtSctPolicy.parseSct(sctG1 + byteArrayOf(0x00)) }
    }

    private fun dmaGuardAudit() {
        val hard = DmaGuardAudit.evaluate(
            DmaGuardAudit.Signals(
                iommuMode = DmaGuardAudit.IommuMode.STRICT,
                preBootDmaProtection = true,
                externalPciePolicy = DmaGuardAudit.ExternalPciePolicy.DENY_ALL,
                usbLockPolicy = DmaGuardAudit.UsbLockPolicy.DISABLED_WHILE_LOCKED,
            ),
        )
        ok(hard.verdict == DmaGuardAudit.Verdict.HARDENED && hard.safeForAmnesicRamSecrets, "strict IOMMU + pre-boot DMAR + locked USB hardened")

        val passthrough = DmaGuardAudit.evaluate(
            DmaGuardAudit.Signals(
                iommuMode = DmaGuardAudit.IommuMode.PASSTHROUGH,
                preBootDmaProtection = true,
                externalPciePolicy = DmaGuardAudit.ExternalPciePolicy.AUTHORIZE_WHEN_UNLOCKED,
                usbLockPolicy = DmaGuardAudit.UsbLockPolicy.DISABLED_WHILE_LOCKED,
            ),
        )
        ok(passthrough.verdict == DmaGuardAudit.Verdict.EXPOSED && !passthrough.safeForAmnesicRamSecrets, "iommu=pt exposed to DMA")

        val uefiGap = DmaGuardAudit.evaluate(
            DmaGuardAudit.Signals(
                iommuMode = DmaGuardAudit.IommuMode.STRICT,
                preBootDmaProtection = false,
                externalPciePolicy = DmaGuardAudit.ExternalPciePolicy.DENY_ALL,
                usbLockPolicy = DmaGuardAudit.UsbLockPolicy.ENABLED_ALWAYS,
            ),
        )
        ok(uefiGap.verdict == DmaGuardAudit.Verdict.DEGRADED && uefiGap.findings.size == 2, "missing DMAR opt-in + unlocked USB degraded")
    }

    private fun hidReportAudit() {
        // Standard USB HID 1.11 Appendix B.1 Boot Keyboard descriptor (63 bytes subset)
        val cleanKbdDesc = byteArrayOf(
            0x05, 0x01, // Usage Page (Generic Desktop)
            0x09, 0x06, // Usage (Keyboard)
            0xA1.toByte(), 0x01, // Collection (Application)
            0x05, 0x07, //   Usage Page (Key Codes)
            0x75, 0x01, //   Report Size (1)
            0x95.toByte(), 0x08, // Report Count (8)
            0x81.toByte(), 0x02, // Input (Data, Variable, Absolute)
            0x95.toByte(), 0x06, // Report Count (6)
            0x75, 0x08, //   Report Size (8)
            0x81.toByte(), 0x00, // Input (Data, Array)
            0xC0.toByte(), // End Collection
        )
        val cleanAudit = HidReportAudit.auditDevice(
            claimedRole = HidReportAudit.ClaimedRole.KEYBOARD,
            descriptorBytes = cleanKbdDesc,
            externalUntrustedWiredBus = false,
        )
        ok(!cleanAudit.quarantineDevice && cleanAudit.flags.isEmpty(), "internal keyboard clean")
        ok(cleanAudit.descriptor.inputBits == 56 && cleanAudit.descriptor.exposesKeyboardInput, "parsed 56 input bits")

        // O.MG-style stealth implant: claims MOUSE role, exposes Mouse + Keyboard + 0xFF00 Vendor C2 Output
        val stealthDesc = byteArrayOf(
            0x05, 0x01, 0x09, 0x02, 0xA1.toByte(), 0x01, // Mouse App Collection
            0x05, 0x09, 0x75, 0x08, 0x95.toByte(), 0x03, 0x81.toByte(), 0x02,
            0xC0.toByte(),
            0x05, 0x01, 0x09, 0x06, 0xA1.toByte(), 0x01, // Hidden Keyboard App Collection!
            0x05, 0x07, 0x75, 0x08, 0x95.toByte(), 0x06, 0x81.toByte(), 0x00,
            0xC0.toByte(),
            0x06, 0x00, 0xFF.toByte(), 0x09, 0x01, 0xA1.toByte(), 0x01, // Vendor 0xFF00 C2!
            0x75, 0x08, 0x95.toByte(), 0x40, 0x91.toByte(), 0x02, // 64-byte Output report
            0xC0.toByte(),
        )
        // Plus rapid GUI+R + <8ms burst boot reports
        val burstReports = (0 until 10).map { idx ->
            val mod = if (idx == 0) 0x08.toByte() else 0x00.toByte() // LeftGUI on frame 0
            val key = if (idx == 0) 0x15.toByte() else (0x04 + idx).toByte() // 'r' = 0x15 on frame 0
            HidReportAudit.BootKeyReport(
                timestampMs = 100L + idx * 3L,
                bytes = byteArrayOf(mod, 0, key, 0, 0, 0, 0, 0),
            )
        }
        val implantAudit = HidReportAudit.auditDevice(
            claimedRole = HidReportAudit.ClaimedRole.MOUSE,
            descriptorBytes = stealthDesc,
            bootReports = burstReports,
            enumerationTimestampMs = 0L,
        )
        ok(implantAudit.quarantineDevice, "O.MG/BadUSB composite mouse+kbd+vendor+burst quarantined")
        ok(HidReportAudit.RiskFlag.UNDECLARED_KEYBOARD_CAPABILITY in implantAudit.flags, "undeclared keyboard flagged")
        ok(HidReportAudit.RiskFlag.COMPOSITE_MOUSE_AND_KEYBOARD in implantAudit.flags, "composite mouse+kbd flagged")
        ok(HidReportAudit.RiskFlag.VENDOR_DEFINED_C2_CHANNEL in implantAudit.flags, "vendor 0xFF00 C2 flagged")
        ok(HidReportAudit.RiskFlag.GUI_COMMAND_INJECTION_CHORD in implantAudit.flags, "GUI+R chord flagged")
        ok(HidReportAudit.RiskFlag.SUPERHUMAN_KEYSTROKE_CADENCE in implantAudit.flags, "3ms burst cadence flagged")

        refuses("HID 0xFE long item refused") { HidReportAudit.parseDescriptor(byteArrayOf(0xFE.toByte(), 0, 0)) }
        refuses("unclosed HID collection refused") { HidReportAudit.parseDescriptor(byteArrayOf(0x05, 0x01, 0x09, 0x06, 0xA1.toByte(), 0x01)) }
    }

    private fun tailsCadence() {
        // 2026-10-23 (epochDay 20749) -> 2026-10-26 (epochDay 20752): 3 days old Tails 7.15.1
        val cur = TailsCadence.auditCadence("7.15.1", 20749L, 20752L, setOf("7.15"))
        ok(cur.status == TailsCadence.CadenceStatus.CURRENT && !cur.blockNetworkUntilUpdated, "7.15.1 3d old is CURRENT")

        val revoked = TailsCadence.auditCadence("7.15", 20735L, 20752L, setOf("7.15"))
        ok(revoked.status == TailsCadence.CadenceStatus.EMERGENCY_REVOKED && revoked.blockNetworkUntilUpdated, "7.15 pre-eMudhra emergency revoked")

        val freeze = TailsCadence.auditCadence("7.14.2", 20710L, 20752L)
        ok(freeze.status == TailsCadence.CadenceStatus.STALE_FREEZE_RISK && freeze.blockNetworkUntilUpdated, ">28d triggers freeze-attack block")

        val rollback = TailsCadence.auditCadence("7.15.1", 20749L, 20740L)
        ok(rollback.status == TailsCadence.CadenceStatus.CLOCK_ROLLBACK_ANOMALY && rollback.blockNetworkUntilUpdated, "clock rollback blocked")

        // 60s shutdown state machine
        val yank = TailsCadence.planShutdown(TailsCadence.ShutdownTrigger.USB_MEDIA_REMOVED, 200L, false)
        ok(yank.action == TailsCadence.ShutdownAction.IMMEDIATE_EMERGENCY_WIPE_POWEROFF && yank.remainingBudgetMs == 0L, "USB yank wipes immediately")
        val wait30 = TailsCadence.planShutdown(TailsCadence.ShutdownTrigger.GRACEFUL_EXIT, 30_000L, false)
        ok(wait30.action == TailsCadence.ShutdownAction.AWAIT_GRACEFUL_SERVICES && wait30.remainingBudgetMs == 30_000L, "30s remaining graceful budget")
        val deadline60 = TailsCadence.planShutdown(TailsCadence.ShutdownTrigger.GRACEFUL_EXIT, 60_000L, false)
        ok(deadline60.action == TailsCadence.ShutdownAction.DEADLINE_FORCED_WIPE_POWEROFF, "60s deadline forces wipe over hung service")

        // Amnesic low-footprint check
        val fpOk = TailsCadence.auditFootprint(TailsCadence.FootprintSignals(0L, true, 0, 0, 180, 256))
        ok(fpOk.amnesicCompliant && fpOk.lightweightCompliant && fpOk.violations.isEmpty(), "zero-swap zero-dump 180MiB compliant")
        val fpSwap = TailsCadence.auditFootprint(TailsCadence.FootprintSignals(4096L, false, 2, 1, 512, 256))
        ok(!fpSwap.amnesicCompliant && !fpSwap.lightweightCompliant && fpSwap.violations.size == 5, "swap+coredump+suid+rw+rss flagged")
    }

    private fun overlayTouchGuard() {
        val safe = OverlayTouchGuard.evaluate(
            OverlayTouchGuard.SurfaceContext(
                motionEventFlags = 0,
                hasCrossUidOverlay = false,
                hasFlagSecure = true,
                activeUntrustedVirtualDisplays = 0,
                enabledAccessibilityAgents = listOf(
                    OverlayTouchGuard.AccessibilityAgent("com.google.android.marvin.talkback", true, false),
                ),
                allowlistedAccessibilityPackages = setOf("com.google.android.marvin.talkback"),
            ),
        )
        ok(safe.allowSecretInput && safe.reasons.isEmpty(), "FLAG_SECURE + allowlisted TalkBack allowed")

        val ratHat = OverlayTouchGuard.evaluate(
            OverlayTouchGuard.SurfaceContext(
                motionEventFlags = OverlayTouchGuard.MOTION_FLAG_WINDOW_IS_PARTIALLY_OBSCURED,
                hasCrossUidOverlay = true,
                hasFlagSecure = false,
                activeUntrustedVirtualDisplays = 1,
                enabledAccessibilityAgents = listOf(
                    OverlayTouchGuard.AccessibilityAgent("org.evil.rathat.helper", true, true),
                ),
            ),
        )
        ok(!ratHat.allowSecretInput && ratHat.reasons.size == 6, "obscured+overlay+noSecure+vdisplay+RatHat blocked")
        ok(ratHat.flaggedAccessibilityPackages == listOf("org.evil.rathat.helper"), "RatHat package pinpointed")
    }

    private fun tileLabelFit() {
        // Exact visual-inspection repairs from r24c-tabletop.png / r24c-silver.png
        val tasks = TileLabelFit.fitLabel("Tasks and Automation", maxCharsPerLine = 12, maxLines = 2)
        ok(tasks.lines == listOf("Tasks and", "Automation") && !tasks.truncated, "no mid-word 'Tasks and ...' truncation")

        val settings = TileLabelFit.fitLabel("Settings and Privacy", maxCharsPerLine = 12, maxLines = 2)
        ok(settings.lines == listOf("Settings and", "Privacy") && !settings.truncated, "no mid-word 'Settings an...' truncation")

        val threeLine = TileLabelFit.fitLabel("Workspace Windowing and Desktop Modes", maxCharsPerLine = 12, maxLines = 2)
        ok(threeLine.lines.size == 2 && threeLine.truncated && threeLine.lines[1].endsWith(TileLabelFit.ELLIPSIS), "3-line overflow ellipsized cleanly")

        // 390dp viewport with 6 dock items (the exact r24c-silver.png 6th-slot A11y clip!)
        val dock390 = TileLabelFit.planDock(totalItems = 6, viewportWidthDp = 390, minSlotDp = 56, horizontalPaddingDp = 16, slotGapDp = 6)
        ok(dock390.hasOverflowButton && dock390.visibleItemCount == 4 && dock390.hiddenItemCount == 2 && dock390.renderedSlots == 5, "390dp reserves overflow slot instead of clipping 6th item")
        ok(dock390.slotWidthDp >= 56, "every rendered dock slot >= 56dp (>= 48dp touch floor)")

        val dockTablet = TileLabelFit.planDock(totalItems = 6, viewportWidthDp = 800, minSlotDp = 56)
        ok(!dockTablet.hasOverflowButton && dockTablet.visibleItemCount == 6, "800dp tablet fits all 6 dock items")

        refuses("sub-48dp touch target refused") { TileLabelFit.planDock(4, 390, minSlotDp = 40) }
    }

    private fun springStep() {
        for (zeta in listOf(0.5, 1.0, 1.8)) {
            val spec = SpringStep.Spec(stiffness = 300.0, dampingRatio = zeta)
            val oneShot = SpringStep.evaluate(spec, 0.0, 0.0, 100.0, 0.20)
            val half1 = SpringStep.evaluate(spec, 0.0, 0.0, 100.0, 0.08)
            val half2 = SpringStep.evaluate(spec, half1.value, half1.velocity, 100.0, 0.12)
            ok(near(oneShot.value, half2.value, 1e-6) && near(oneShot.velocity, half2.velocity, 1e-5), "semigroup time-split law zeta=$zeta")
            val settled = SpringStep.evaluate(spec, 0.0, 0.0, 100.0, 10.0)
            ok(settled.settled && settled.value == 100.0 && settled.velocity == 0.0, "converges at t=10s zeta=$zeta")
        }
        val reduced = SpringStep.evaluate(SpringStep.Spec(), 0.0, 50.0, 100.0, 0.016, reducedMotion = true)
        ok(reduced.settled && reduced.value == 100.0 && reduced.velocity == 0.0, "reducedMotion snaps immediately")

        refuses("zero damping perpetual oscillation refused") { SpringStep.Spec(stiffness = 100.0, dampingRatio = 0.0) }
        refuses("negative dt") { SpringStep.evaluate(SpringStep.Spec(), 0.0, 0.0, 1.0, -0.1) }
    }

    private fun workAreaFit() {
        val work = WorkAreaFit.usableWorkArea(
            1280, 800,
            listOf(
                WorkAreaFit.Insets(top = 28, bottom = 56), // status + taskbar
                WorkAreaFit.Insets(bottom = 280), // active IME overrides bottom
            ),
        )
        ok(work == WorkAreaFit.Rect(0, 28, 1280, 492), "max component-wise insets")

        val rescued = WorkAreaFit.clampWindow(
            desired = WorkAreaFit.Rect(-2000, -50, 9999, 80),
            work = work,
            minWidth = 240,
            minHeight = 160,
        )
        ok(rescued.width == 1280 && rescued.height == 160, "clamped width to work & height to minHeight")
        ok(rescued.y >= work.y && rescued.y + WorkAreaFit.DEFAULT_TITLEBAR_HEIGHT <= work.bottom, "titlebar vertically inside work area")
        ok(rescued.right >= work.x + WorkAreaFit.DEFAULT_MIN_GRAB_WIDTH, "grab handle horizontally reachable")

        val cascaded = WorkAreaFit.cascade(WorkAreaFit.Rect(0, 0, 500, 400), 400, 300, count = 6, stepPx = 32)
        ok(cascaded.size == 6 && cascaded.all { it.right <= 500 && it.bottom <= 400 }, "wrapped cascade stays inside work area")
        ok(cascaded[4].x == 0 && cascaded[4].y == 0, "cascade wraps cleanly after 4 steps")
    }

    private fun merkleTree() {
        // Empty tree == SHA-256("")
        ok(
            MerkleTree.toHex(MerkleTree.rootHash(emptyList())) ==
                "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
            "RFC 6962 empty MTH({}) == SHA-256('')",
        )
        // Non-power-of-two 7-leaf tree: verify EVERY leaf's inclusion proof AND reject tampered payload/index
        val leaves = (0 until 7).map { "chunk-$it".toByteArray(StandardCharsets.UTF_8) }
        val root = MerkleTree.rootHash(leaves)
        for (idx in leaves.indices) {
            val proof = MerkleTree.inclusionProof(leaves, idx)
            ok(proof.size <= 3, "O(log2 7) proof length <= 3")
            ok(MerkleTree.verifyInclusion(leaves[idx], idx, leaves.size, root, proof), "valid inclusion leaf $idx")
            ok(!MerkleTree.verifyInclusion("tampered".toByteArray(), idx, leaves.size, root, proof), "tampered payload rejected leaf $idx")
            if (idx + 1 < leaves.size) {
                ok(!MerkleTree.verifyInclusion(leaves[idx], idx + 1, leaves.size, root, proof), "wrong index rejected leaf $idx")
            }
        }
        // Second-preimage resistance: 1-leaf tree != 2-leaf internal node over raw 32B halves
        val h0 = MerkleTree.leafHash(leaves[0])
        val h1 = MerkleTree.leafHash(leaves[1])
        val twoRoot = MerkleTree.rootHash(listOf(leaves[0], leaves[1]))
        val forgedSingle = MerkleTree.rootHash(listOf(h0 + h1))
        ok(!twoRoot.contentEquals(forgedSingle), "0x00/0x01 domain separation defeats leaf/node confusion")
    }

    private fun envOverride() {
        val schema = listOf(
            EnvOverride.Spec.Bool("amnesic.strict", default = true),
            EnvOverride.Spec.IntRangeSpec("sheet.maxCols", default = 64, min = 1, max = 16384),
            EnvOverride.Spec.DurationMs("shutdown.timeout", default = 60_000L, minMs = 1_000L, maxMs = 120_000L),
            EnvOverride.Spec.EnumSpec("browser.mode", default = "ephemeral", allowed = setOf("ephemeral", "isolated")),
        )
        val raw = EnvOverride.parseLines("# comment\nsheet.maxCols = 256\nshutdown.timeout = 45s\nbrowser.mode = \"isolated\"")
        val resolved = EnvOverride.resolve(schema, raw)
        ok(resolved["amnesic.strict"] == EnvOverride.ResolvedEntry("amnesic.strict", EnvOverride.Value.B(true), EnvOverride.Source.DEFAULT), "default provenance")
        ok(resolved["sheet.maxCols"] == EnvOverride.ResolvedEntry("sheet.maxCols", EnvOverride.Value.I(256), EnvOverride.Source.OVERRIDE), "override int")
        ok(resolved["shutdown.timeout"] == EnvOverride.ResolvedEntry("shutdown.timeout", EnvOverride.Value.Ms(45_000L), EnvOverride.Source.OVERRIDE), "45s -> 45000ms")
        ok(resolved["browser.mode"] == EnvOverride.ResolvedEntry("browser.mode", EnvOverride.Value.E("isolated"), EnvOverride.Source.OVERRIDE), "quoted enum")

        refuses("Norway-problem boolean 'yes' refused") {
            EnvOverride.resolve(schema, mapOf("amnesic.strict" to "yes"))
        }
        refuses("unknown key refused") {
            EnvOverride.resolve(schema, mapOf("typo.key" to "true"))
        }
        refuses("\${VAR} interpolation refused") {
            EnvOverride.parseLines("browser.mode=\${HOME}")
        }
        refuses("duplicate key refused") {
            EnvOverride.parseLines("sheet.maxCols=10\nsheet.maxCols=20")
        }
    }

    private fun psdLayerRecords() {
        val baos = ByteArrayOutputStream()
        fun writeLayer(name: String, top: Int, left: Int, bottom: Int, right: Int, blend: String, opacity: Int, flags: Int) {
            val nameBytes = name.toByteArray(StandardCharsets.ISO_8859_1)
            val paddedNameLen = ((1 + nameBytes.size + 3) / 4) * 4
            val extraLen = 4 + 4 + paddedNameLen
            val bb = ByteBuffer.allocate(2 + 4 * 4 + 2 + 4 * 6 + 4 + 4 + 4 + 4 + extraLen).order(ByteOrder.BIG_ENDIAN)
            if (baos.size() == 0) bb.putShort((-2).toShort()) // -2 => 2 layers + merged alpha!
            bb.putInt(top); bb.putInt(left); bb.putInt(bottom); bb.putInt(right)
            bb.putShort(4) // RGBA
            for (cid in listOf(-1, 0, 1, 2)) {
                bb.putShort(cid.toShort())
                bb.putInt(128)
            }
            bb.put("8BIM".toByteArray(StandardCharsets.US_ASCII))
            bb.put(blend.toByteArray(StandardCharsets.US_ASCII))
            bb.put(opacity.toByte())
            bb.put(0) // clipping base
            bb.put(flags.toByte())
            bb.put(0) // filler
            bb.putInt(extraLen)
            bb.putInt(0) // mask len = 0
            bb.putInt(0) // blend ranges len = 0
            bb.put(nameBytes.size.toByte())
            bb.put(nameBytes)
            repeat(paddedNameLen - 1 - nameBytes.size) { bb.put(0) }
            val arr = bb.array()
            val used = bb.position()
            baos.write(arr, 0, used)
        }
        writeLayer("Background", 0, 0, 1080, 1920, "norm", 255, 0x01) // transparencyProtected=true, visible=true
        writeLayer("Ink Sketch", 10, 20, 510, 820, "mult", 180, 0x02) // visible=false (bit 1 set!)

        val table = PsdLayerRecords.parseLayerInfo(baos.toByteArray())
        ok(table.mergedAlphaFirstChannel && table.layers.size == 2, "negative layer count -> mergedAlpha + 2 layers")
        ok(table.layers[0].name == "Background" && table.layers[0].width == 1920 && table.layers[0].visible && table.layers[0].transparencyProtected, "layer 0 metadata")
        ok(table.layers[1].name == "Ink Sketch" && table.layers[1].blendMode == "mult" && !table.layers[1].visible && table.layers[1].opacity == 180, "layer 1 mult hidden")

        refuses("bad 8BIM signature refused") {
            val bad = baos.toByteArray()
            val idx = String(bad, StandardCharsets.ISO_8859_1).indexOf("8BIM")
            bad[idx] = 'X'.code.toByte()
            PsdLayerRecords.parseLayerInfo(bad)
        }
    }

    private fun ssdpHeader() {
        val okPkt = "HTTP/1.1 200 OK\r\n" +
            "CACHE-CONTROL: max-age=1800\r\n" +
            "LOCATION: http://192.168.1.44:8200/rootDesc.xml\r\n" +
            "ST: urn:schemas-upnp-org:device:MediaRenderer:1\r\n" +
            "USN: uuid:12345678-1234-1234-1234-123456789abc::urn:schemas-upnp-org:device:MediaRenderer:1\r\n\r\n"
        val parsed = SsdpHeader.parseAndValidate(okPkt)
        ok(parsed.kind == SsdpHeader.Kind.SEARCH_RESPONSE && parsed.hostIp == "192.168.1.44" && parsed.port == 8200 && parsed.maxAgeSeconds == 1800, "RFC 1918 SSDP 200 OK accepted")

        val ulaPkt = "NOTIFY * HTTP/1.1\r\n" +
            "NTS: ssdp:alive\r\n" +
            "LOCATION: http://[fd12:3456::1]:1400/desc.xml\r\n" +
            "NT: upnp:rootdevice\r\n" +
            "USN: uuid:abcd\r\n\r\n"
        ok(SsdpHeader.parseAndValidate(ulaPkt).hostIp == "fd12:3456::1", "RFC 4193 IPv6 ULA accepted")

        // CallStranger / SSRF / metadata / loopback / DNS-rebinding refusals
        refuses("AWS/GCP metadata 169.254.169.254 SSRF refused") {
            SsdpHeader.parseAndValidate(okPkt.replace("192.168.1.44:8200", "169.254.169.254"))
        }
        refuses("127.0.0.1 loopback SSRF refused") {
            SsdpHeader.parseAndValidate(okPkt.replace("192.168.1.44:8200", "127.0.0.1:8080"))
        }
        refuses("public WAN IP CallStranger refused") {
            SsdpHeader.parseAndValidate(okPkt.replace("192.168.1.44:8200", "203.0.113.5:80"))
        }
        refuses("DNS hostname rebinding refused") {
            SsdpHeader.parseAndValidate(okPkt.replace("192.168.1.44:8200", "rebind.attacker.example"))
        }
    }

    private fun castTxtProps() {
        val props = CastTxtProps.parseProps(
            mapOf(
                "id" to "a1b2c3d4-e5f6-0123-4567-89abcdef0123",
                "fn" to "Living Room TV",
                "md" to "Chromecast",
                "ca" to "37", // 1 (VIDEO_OUT) + 4 (AUDIO_OUT) + 32 (MULTIZONE)
                "st" to "1",
                "ve" to "05",
            ),
        )
        ok(props.deviceId == "a1b2c3d4e5f60123456789abcdef0123", "UUID hyphen normalization")
        ok(
            props.capabilities == setOf(
                CastTxtProps.Capability.VIDEO_OUT,
                CastTxtProps.Capability.AUDIO_OUT,
                CastTxtProps.Capability.MULTIZONE_GROUP,
            ),
            "Cast capability bitmask 37 decoded",
        )

        val pinA = "ab".repeat(32)
        val pinEvil = "cd".repeat(32)
        val first = CastTxtProps.verifyTofuPin(emptyMap(), props.deviceId, pinA)
        ok(first.outcome == CastTxtProps.TofuOutcome.FIRST_SEEN_PINNED && first.trusted, "TOFU first-seen pins cert")
        val second = CastTxtProps.verifyTofuPin(first.updatedPins, props.deviceId, pinA)
        ok(second.outcome == CastTxtProps.TofuOutcome.MATCHED_TRUSTED && second.trusted, "TOFU repeat match trusted")
        val mitm = CastTxtProps.verifyTofuPin(first.updatedPins, props.deviceId, pinEvil)
        ok(mitm.outcome == CastTxtProps.TofuOutcome.PIN_MISMATCH_MITM_REFUSED && !mitm.trusted, "TOFU SPKI swap refused")

        refuses("control char in friendly name") {
            CastTxtProps.parseProps(mapOf("id" to "0".repeat(32), "fn" to "Bad\nTV"))
        }
    }

    private fun txtRecord() {
        val entries = listOf(
            TxtRecord.Entry("txtvers", TxtRecord.AttrValue.ofUtf8("1")),
            TxtRecord.Entry("pw", TxtRecord.AttrValue.BooleanFlag),
            TxtRecord.Entry("note", TxtRecord.AttrValue.PresentEmpty),
            TxtRecord.Entry("model", TxtRecord.AttrValue.ofUtf8("AppleTV6,2")),
        )
        val wire = TxtRecord.encode(entries)
        val decoded = TxtRecord.decode(wire)
        ok((decoded["txtvers"] as TxtRecord.AttrValue.Bytes).utf8() == "1", "txtvers=1")
        ok(decoded["pw"] == TxtRecord.AttrValue.BooleanFlag, "flag without '=' distinct from empty")
        ok(decoded["note"] == TxtRecord.AttrValue.PresentEmpty, "key= empty value distinct from flag")
        ok((decoded["model"] as TxtRecord.AttrValue.Bytes).utf8() == "AppleTV6,2", "model UTF-8")
        ok(TxtRecord.decode(TxtRecord.encode(emptyList())).isEmpty(), "RFC 6763 0x00 empty RDATA")

        // RFC 6763 §6.4 first-key-wins on raw wire with duplicate case-variant key
        val dupWire = byteArrayOf(9) + "txtvers=1".toByteArray() + byteArrayOf(9) + "TXTVERS=9".toByteArray()
        ok((TxtRecord.decode(dupWire)["txtvers"] as TxtRecord.AttrValue.Bytes).utf8() == "1", "RFC 6763 first-key-wins")

        refuses("key containing '=' refused") { TxtRecord.Entry("a=b", TxtRecord.AttrValue.BooleanFlag) }
        refuses("truncated TXT length byte refused") { TxtRecord.decode(byteArrayOf(10, 65, 66)) }
    }

    private fun keyChord() {
        val c1 = KeyChord.parse("shift + cmd + p")
        ok(c1.canonicalString() == "Shift+Meta+P", "modifier order canonicalized")
        val c2 = KeyChord.parse("alt+ctrl+f4")
        ok(c2.canonicalString() == "Ctrl+Alt+F4", "Ctrl+Alt+F4")
        val plusKey = KeyChord.parse("Ctrl++")
        ok(plusKey.canonicalString() == "Ctrl++", "zoom-in Ctrl++ parsed cleanly")
        val fKey = KeyChord.parse("F1")
        ok(fKey.canonicalString() == "F1", "non-printable F1 allowed without modifier")
        val scopedSingle = KeyChord.parse("k", scopedToNonTextComponent = true)
        ok(scopedSingle.canonicalString() == "K", "scoped non-text single key allowed")

        // WCAG 2.2 SC 2.1.4 Character Key Shortcuts refusals
        refuses("unmodified 'k' global shortcut violates WCAG 2.1.4") { KeyChord.parse("k") }
        refuses("Shift+? global shortcut violates WCAG 2.1.4") { KeyChord.parse("Shift+?") }
        refuses("Space global shortcut violates WCAG 2.1.4") { KeyChord.parse("Space") }
        refuses("modifier-only Ctrl+Shift refused") { KeyChord.parse("Ctrl+Shift") }

        val conflicts = KeyChord.findConflicts(
            listOf(
                KeyChord.Binding("open_palette", "Ctrl+Shift+P"),
                KeyChord.Binding("print_raw", "shift + control + p"),
                KeyChord.Binding("save_doc", "Ctrl+S"),
            ),
        )
        ok(conflicts == listOf(KeyChord.Conflict("Ctrl+Shift+P", listOf("open_palette", "print_raw"))), "normalized chord collision detected")
    }

    private fun utmGrid() {
        // Equator on central meridian of Zone 31 (lon = 3.0°E, lat = 0.0°N) -> Easting = 500,000.0m, Northing = 0.0m
        val origin31 = UtmGrid.fromLatLon(0.0, 3.0)
        ok(origin31.zone == 31 && origin31.band == 'N' && origin31.hemisphere == 'N', "31N at (0, 3E)")
        ok(near(origin31.eastingMeters, 500_000.0, 1e-6) && near(origin31.northingMeters, 0.0, 1e-6), "exact central-meridian origin")

        // Bergen, Norway (60.3913°N, 5.3221°E) -> would be Zone 31 by naive formula, MUST be 32V!
        val bergen = UtmGrid.fromLatLon(60.3913, 5.3221)
        ok(bergen.gridZoneDesignator() == "32V", "Southwest Norway 32V exception")

        // Longyearbyen, Svalbard (78.2232°N, 15.6267°E) -> naive formula gives 33, test 8.0°E -> 31X and 20.0°E -> 33X (naive 20°E is 34!)
        val svalbard20E = UtmGrid.fromLatLon(78.0, 20.0)
        ok(svalbard20E.gridZoneDesignator() == "33X", "Svalbard 20E -> 33X exception (not 34X)")

        // Sydney, Southern Hemisphere (-33.8568°S, 151.2153°E) -> Zone 56H, Northing ~ 6,252,271 m
        val sydney = UtmGrid.fromLatLon(-33.8568, 151.2153)
        ok(sydney.zone == 56 && sydney.band == 'H' && sydney.hemisphere == 'S', "Sydney 56H south")
        ok(sydney.eastingMeters in 334_000.0..336_000.0 && sydney.northingMeters in 6_251_000.0..6_254_000.0, "Sydney metric range")

        refuses("North Pole 85N UPS out of UTM scope") { UtmGrid.fromLatLon(85.0, 0.0) }
        refuses("South Pole -81S UPS out of UTM scope") { UtmGrid.fromLatLon(-81.0, 0.0) }
    }

    private fun hkdfExpand() {
        // Official RFC 5869 Appendix A.1 Test Case 1 (SHA-256)
        val ikm1 = hexBytes("0b".repeat(22))
        val salt1 = hexBytes("000102030405060708090a0b0c")
        val info1 = hexBytes("f0f1f2f3f4f5f6f7f8f9")
        val prk1 = HkdfExpand.extract(salt1, ikm1)
        val okm1 = HkdfExpand.expand(prk1, info1, 42)
        ok(toHex(prk1) == "077709362c2e32df0ddc3f0dc47bba6390b6c73bb50f9c3122ec844ad7c2b3e5", "RFC 5869 A.1 PRK")
        ok(
            toHex(okm1) == "3cb25f25faacd57a90434f64d0362f2a2d2d0a90cf1a5a4c5db02d56ecc4c5bf34007208d5b887185865",
            "RFC 5869 A.1 OKM (42 bytes)",
        )

        // Official RFC 5869 Appendix A.2 Test Case 2 (longer inputs/outputs, 82 bytes)
        val ikm2 = ByteArray(80) { it.toByte() }
        val salt2 = ByteArray(80) { (0x60 + it).toByte() }
        val info2 = ByteArray(80) { (0xB0 + it).toByte() }
        val okm2 = HkdfExpand.deriveKey(ikm2, salt2, info2, 82)
        ok(
            toHex(okm2) ==
                "b11e398dc80327a1c8e7f78c596a49344f012eda2d4efad8a050cc4c19afa97c59045a99cac78272" +
                "71cb41c65e590e09da3275600c2f09b8367793a9aca3db71cc30c58179ec3e87c14c01d5c1f3434f1d87",
            "RFC 5869 A.2 OKM (82 bytes)",
        )

        // Official RFC 5869 Appendix A.3 Test Case 3 (zero-length salt & zero-length info)
        val okm3 = HkdfExpand.deriveKey(ikm1, ByteArray(0), ByteArray(0), 42)
        ok(
            toHex(okm3) == "8da4e775a563c18f715f802a063c5a31b8a11f5c5ee1879ec3454e5f3c738d2d9d201395faa4b61a96c8",
            "RFC 5869 A.3 zero-salt zero-info OKM",
        )

        refuses("empty IKM refused") { HkdfExpand.extract(ByteArray(0), ByteArray(0)) }
        refuses("short PRK < 32B refused") { HkdfExpand.expand(ByteArray(16), ByteArray(0), 32) }
        refuses("OKM > 255*32 (8161) refused") { HkdfExpand.expand(prk1, ByteArray(0), 8161) }
    }

    private fun clipMime() {
        val htmlAndPlain = ClipMime.negotiate(
            ClipMime.Offer(
                offeredMimes = listOf("text/html; charset=utf-8", "text/plain"),
                plainText = "Safe summary",
                htmlText = "<img src=x onerror=alert(1)>Safe summary",
            ),
        )
        ok(htmlAndPlain.outcome == ClipMime.Outcome.DOWNGRADED_FROM_HTML && htmlAndPlain.selectedMime == "text/plain" && htmlAndPlain.payloadText == "Safe summary", "HTML downgraded to text/plain")

        val htmlOnly = ClipMime.negotiate(
            ClipMime.Offer(offeredMimes = listOf("text/html"), htmlText = "<script>evil()</script>"),
        )
        ok(htmlOnly.outcome == ClipMime.Outcome.REJECTED, "HTML-only without plain fallback rejected")

        val mixedUris = ClipMime.negotiate(
            ClipMime.Offer(
                offeredMimes = listOf("text/uri-list"),
                uriList = listOf(
                    "file:///home/user/.ssh/id_ed25519",
                    "content://com.android.contacts/data/1",
                    "javascript:alert(1)",
                    "https://tails.net/news/version_7.15.1/",
                ),
            ),
        )
        ok(mixedUris.outcome == ClipMime.Outcome.ACCEPTED && mixedUris.safeUris == listOf("https://tails.net/news/version_7.15.1/") && mixedUris.strippedUriCount == 3, "local file/content/js URIs stripped")
    }

    private fun dagTopo() {
        val res = DagTopo.plan(
            listOf(
                DagTopo.Task("package", 30L, setOf("compile_ui", "compile_core")),
                DagTopo.Task("compile_ui", 50L, setOf("gen_tokens")),
                DagTopo.Task("compile_core", 20L, emptySet()),
                DagTopo.Task("gen_tokens", 15L, emptySet()),
            ),
        )
        ok(res is DagTopo.PlanResult.Schedule, "acyclic DAG scheduled")
        val sched = res as DagTopo.PlanResult.Schedule
        ok(sched.order.map { it.id } == listOf("compile_core", "gen_tokens", "compile_ui", "package"), "lexicographically-smallest Kahn order")
        ok(sched.makespanMs == 95L && sched.criticalPath == listOf("gen_tokens", "compile_ui", "package"), "critical path 15+50+30 == 95ms")

        val cyc = DagTopo.plan(
            listOf(
                DagTopo.Task("init", 5L, emptySet()),
                DagTopo.Task("alpha", 10L, setOf("init", "gamma")),
                DagTopo.Task("beta", 10L, setOf("alpha")),
                DagTopo.Task("gamma", 10L, setOf("beta")),
            ),
        )
        ok(cyc is DagTopo.PlanResult.CycleDetected, "cycle refused cleanly")
        val witness = (cyc as DagTopo.PlanResult.CycleDetected).cycleWitness
        ok(witness.size == 4 && witness.first() == witness.last() && "init" !in witness, "closed cycle witness excludes acyclic 'init'")

        refuses("unknown dependency task refused") {
            DagTopo.plan(listOf(DagTopo.Task("a", 1L, setOf("missing"))))
        }
    }

    private fun bm25Rank() {
        val docs = listOf(
            Bm25Rank.DocTokens("doc-short", listOf("amnesic", "tails", "privacy")),
            Bm25Rank.DocTokens("doc-long", listOf("amnesic", "tails") + List(40) { "filler" }),
            Bm25Rank.DocTokens("doc-unrelated", listOf("spreadsheet", "xlsx", "formula")),
        )
        val ranked = Bm25Rank.rank(docs, listOf("Tails", "Amnesic"))
        ok(ranked.map { it.docId } == listOf("doc-short", "doc-long"), "shorter matching doc outranks padded doc; zero-match omitted")
        ok(ranked[0].score > ranked[1].score && ranked[1].score > 0.0, "strict positive BM25 monotonicity")

        // Ubiquitous term across 100% of docs still yields positive Lucene RSJ IDF (no negative inversion!)
        val ubi = Bm25Rank.rank(
            listOf(Bm25Rank.DocTokens("a", listOf("the", "the")), Bm25Rank.DocTokens("b", listOf("the"))),
            listOf("the"),
        )
        ok(ubi.size == 2 && ubi.all { it.score > 0.0 } && ubi[0].docId == "a", "ubiquitous term positive RSJ IDF + TF saturation")

        refuses("b > 1.0 refused") { Bm25Rank.Params(k1 = 1.2, b = 1.5) }
        refuses("duplicate docId refused") {
            Bm25Rank.rank(listOf(Bm25Rank.DocTokens("dup", listOf("a")), Bm25Rank.DocTokens("dup", listOf("b"))), listOf("a"))
        }
    }

    private fun algebraicPropertiesAndAdversarialSweeps() {
        // 1. PieceTable 24-step deterministic pseudo-random edit/undo/redo differential vs StringBuilder
        var seed = 0x5A17C0DEL
        fun nextRand(bound: Int): Int {
            seed = (seed * 6364136223846793005L + 1442695040888963407L)
            return ((seed ushr 33).toInt() and 0x7FFF_FFFF) % bound
        }
        var hist = PieceTable.createHistory("Initial document\nSecond line")
        val noOp = hist.edit { it.replace(3, 3, "") }
        ok(!noOp.canUndo && noOp === hist, "PieceTable no-op edit does not pollute undoStack")
        val refStack = ArrayList<String>().apply { add(hist.current.materialize()) }
        repeat(24) { step ->
            val curStr = refStack.last()
            val a = nextRand(curStr.length + 1)
            val b = nextRand(curStr.length + 1)
            val lo = minOf(a, b)
            val hi = maxOf(a, b)
            val ins = if (step % 3 == 0 && lo < hi) "" else "s$step\n"
            hist = hist.edit { it.replace(lo, hi, ins) }
            val expected = curStr.substring(0, lo) + ins + curStr.substring(hi)
            refStack.add(expected)
            ok(hist.current.materialize() == expected, "PieceTable differential step $step")
        }
        repeat(12) {
            hist = hist.undo()
            refStack.removeAt(refStack.lastIndex)
            ok(hist.current.materialize() == refStack.last(), "PieceTable undo differential")
        }
        repeat(12) {
            hist = hist.redo()
        }
        ok(!hist.canRedo, "PieceTable full redo stack drained")

        // 2. RleBitset Boolean-algebra laws across 10 pseudo-random sets
        repeat(10) { idx ->
            val setA = (0 until 30).map { nextRand(80) }.toSet()
            val setB = (0 until 30).map { nextRand(80) }.toSet()
            val bsA = RleBitset.ofInts(setA)
            val bsB = RleBitset.ofInts(setB)
            ok(RleBitset.decode(RleBitset.encode(bsA)) == bsA, "RleBitset wire bijection $idx")
            ok(RleBitset.union(bsA, bsB).toIntList() == (setA union setB).sorted(), "RleBitset union == Set.union $idx")
            ok(RleBitset.intersect(bsA, bsB).toIntList() == (setA intersect setB).sorted(), "RleBitset intersect == Set.intersect $idx")
            ok(RleBitset.difference(bsA, bsB).toIntList() == (setA subtract setB).sorted(), "RleBitset diff == Set.subtract $idx")
        }

        // 3. MerkleTree RFC 6962 inclusion proof across every tree size 1..13 (covers powers of 2, primes, odd tails)
        for (n in 1..13) {
            val leaves = (0 until n).map { i -> "n=$n,i=$i".toByteArray(StandardCharsets.UTF_8) }
            val root = MerkleTree.rootHash(leaves)
            for (k in listOf(0, n / 2, n - 1).distinct()) {
                val proof = MerkleTree.inclusionProof(leaves, k)
                ok(MerkleTree.verifyInclusion(leaves[k], k, n, root, proof), "Merkle verify size=$n idx=$k")
            }
        }

        // 4. FinMath PV <-> PMT <-> FV closed-loop identity across 12 (rate, nper, fv, type) combinations
        val rates = listOf(0.0, 0.0025, 0.01, 0.075)
        for (r in rates) {
            for (tp in listOf(0, 1)) {
                val p = FinMath.pmt(r, 48.0, 25_000.0, -5_000.0, tp)
                val pvBack = FinMath.pv(r, 48.0, p, -5_000.0, tp)
                val fvBack = FinMath.fv(r, 48.0, p, 25_000.0, tp)
                ok(near(pvBack, 25_000.0, 1e-6), "FinMath PV round-trip r=$r type=$tp")
                ok(near(fvBack, -5_000.0, 1e-6), "FinMath FV round-trip r=$r type=$tp")
            }
        }

        // 5. UtmGrid all 60 equatorial zones (-177°E .. +177°E step 6°) hit exact central meridian Easting = 500,000m
        for (z in 1..60) {
            val cmLon = (z - 1) * 6.0 - 180.0 + 3.0
            val c = UtmGrid.fromLatLon(0.0, cmLon)
            ok(c.zone == z && near(c.eastingMeters, 500_000.0, 1e-6), "UTM equatorial central meridian zone $z")
        }

        // 6. DagTopo input-order invariance: reversed/shuffled task lists produce identical schedule
        val baseTasks = listOf(
            DagTopo.Task("t4", 10L, setOf("t2", "t3")),
            DagTopo.Task("t2", 20L, setOf("t1")),
            DagTopo.Task("t3", 25L, setOf("t1")),
            DagTopo.Task("t1", 5L, emptySet()),
            DagTopo.Task("t0", 50L, emptySet()),
        )
        val sForward = DagTopo.plan(baseTasks)
        val sReverse = DagTopo.plan(baseTasks.reversed())
        ok(sForward == sReverse, "DagTopo lexicographic Kahn invariant under task list reversal")

        // 7. FormulaEval operator precedence, escaped quotes, ABS, and comparison operators
        val prec = FormulaEval.evaluateSheet(
            mapOf(
                "A1" to FormulaEval.CellInput.Formula("=2 + 3 * 2 ^ 3"), // 2 + 3*8 = 26
                "A2" to FormulaEval.CellInput.Formula("=ABS(-42.5)"),
                "A3" to FormulaEval.CellInput.Formula("=\"say \"\"hi\"\"\""),
                "A4" to FormulaEval.CellInput.Formula("=10 <> 20"),
                "A5" to FormulaEval.CellInput.Formula("=10 <= 10"),
            ),
        )
        ok(prec.values["A1"] == FormulaEval.CellValue.Num(26.0), "2 + 3 * 2 ^ 3 == 26")
        ok(prec.values["A2"] == FormulaEval.CellValue.Num(42.5), "ABS(-42.5) == 42.5")
        ok(prec.values["A3"] == FormulaEval.CellValue.Text("say \"hi\""), "doubled string quote escape")
        ok(prec.values["A4"] == FormulaEval.CellValue.Bool(true), "10 <> 20")
        ok(prec.values["A5"] == FormulaEval.CellValue.Bool(true), "10 <= 10")

        // 8. SsdpHeader RFC 1918 exact octet boundaries (172.16..31 accepted; 172.15 and 172.32 refused; 10.x accepted; byebye accepted)
        ok(SsdpHeader.isPrivateLanIpLiteral("10.0.0.1"), "10.0.0.1 private")
        ok(SsdpHeader.isPrivateLanIpLiteral("172.16.0.1"), "172.16.0.1 private")
        ok(SsdpHeader.isPrivateLanIpLiteral("172.31.255.254"), "172.31.255.254 private")
        ok(!SsdpHeader.isPrivateLanIpLiteral("172.15.255.254"), "172.15 public WAN")
        ok(!SsdpHeader.isPrivateLanIpLiteral("172.32.0.1"), "172.32 public WAN")
        ok(!SsdpHeader.isPrivateLanIpLiteral("192.168.01.1"), "octal/leading-zero IPv4 refused")
        val bye = SsdpHeader.parseAndValidate("NOTIFY * HTTP/1.1\r\nNTS: ssdp:byebye\r\nNT: upnp:rootdevice\r\nUSN: uuid:deadbeef\r\n\r\n")
        ok(bye.kind == SsdpHeader.Kind.NOTIFY_BYEBYE && bye.location == null, "ssdp:byebye without LOCATION accepted")

        // 9. TxtRecord exact 255-byte string boundary vs 256-byte refusal
        val v251 = "x".repeat(251) // "k=" (2) + 251 = 253 <= 255; "key=" (4) + 251 = 255 exact!
        val exact255 = TxtRecord.encode(listOf(TxtRecord.Entry("key", TxtRecord.AttrValue.ofUtf8(v251))))
        ok((TxtRecord.decode(exact255)["key"] as TxtRecord.AttrValue.Bytes).utf8() == v251, "exact 255-byte TXT entry round-trips")
        refuses("256-byte TXT entry refused") {
            TxtRecord.encode(listOf(TxtRecord.Entry("key", TxtRecord.AttrValue.ofUtf8("x".repeat(252)))))
        }

        // 10. DocxNumbering Roman 1..30 distinct & non-empty + PslGate caller RuleSet + TileLabelFit overlong single word
        val romans = (1..30).map { DocxNumbering.formatCounter(it, DocxNumbering.NumFormat.UPPER_ROMAN) }
        ok(romans.distinct().size == 30 && romans[3] == "IV" && romans[8] == "IX" && romans[28] == "XXIX", "Roman 1..30 distinct subtractive")
        val customPsl = PslGate.parseRuleList("test-v1", listOf("*.platform.test", "!www.platform.test"))
        ok(PslGate.classify("app.tenant.platform.test", customPsl) == PslGate.Decision.Registrable("app.tenant.platform.test", "tenant.platform.test"), "custom PSL wildcard")
        ok(PslGate.classify("www.platform.test", customPsl) == PslGate.Decision.Registrable("www.platform.test", "platform.test"), "custom PSL exception")
        val overlongWord = TileLabelFit.fitLabel("Supercalifragilistic", maxCharsPerLine = 8, maxLines = 2)
        ok(overlongWord.truncated && overlongWord.lines == listOf("Superca\u2026"), "overlong single word truncated cleanly at maxChars-1")
    }

    private fun scopeDeclarationsPresent() {
        val scopes = listOf(
            FormulaEval.SCOPE, FormulaXlsx.SCOPE, DocxNumbering.SCOPE, DocxPackage.SCOPE,
            PieceTable.SCOPE, ProseEducate.SCOPE, DashEducate.SCOPE, FindReplace.SCOPE,
            OdtExtract.SCOPE, RleBitset.SCOPE, FinMath.SCOPE, TocTree.SCOPE,
            CspHeader.SCOPE, PslGate.SCOPE, CtSctPolicy.SCOPE, DmaGuardAudit.SCOPE,
            HidReportAudit.SCOPE, TailsCadence.SCOPE, OverlayTouchGuard.SCOPE, TileLabelFit.SCOPE,
            SpringStep.SCOPE, WorkAreaFit.SCOPE, MerkleTree.SCOPE, EnvOverride.SCOPE,
            PsdLayerRecords.SCOPE, SsdpHeader.SCOPE, CastTxtProps.SCOPE, TxtRecord.SCOPE,
            KeyChord.SCOPE, UtmGrid.SCOPE, HkdfExpand.SCOPE, ClipMime.SCOPE,
            DagTopo.SCOPE, Bm25Rank.SCOPE,
        )
        ok(scopes.size == 34 && scopes.all { it.length >= 24 }, "all 33 new kernels + DashEducate declare non-empty SCOPE")
    }

    @JvmStatic
    fun main(args: Array<String>) {
        formulaEval(); formulaXlsx(); docxNumbering(); docxPackage()
        pieceTable(); proseEducate(); findReplace(); odtExtract()
        rleBitset(); finMath(); tocTree(); cspHeader()
        pslGate(); ctSctPolicy(); dmaGuardAudit(); hidReportAudit()
        tailsCadence(); overlayTouchGuard(); tileLabelFit(); springStep()
        workAreaFit(); merkleTree(); envOverride(); psdLayerRecords()
        ssdpHeader(); castTxtProps(); txtRecord(); keyChord()
        utmGrid(); hkdfExpand(); clipMime(); dagTopo(); bm25Rank()
        algebraicPropertiesAndAdversarialSweeps()
        scopeDeclarationsPresent()
        println("R45 PASS: $count production-kernel assertions across 33 kernels (+1 touched). Host JVM only; not Android/device evidence.")
    }
}
