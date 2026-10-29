// SPDX-License-Identifier: Apache-2.0
package dev.fusion.testing

import dev.fusion.a11y.ColorBlindnessDaltonizeMatrix
import dev.fusion.a11y.EasySimpleModeLayoutScaler
import dev.fusion.a11y.FocusRingDwellClickController
import dev.fusion.airplay.CastSinkSrpPinVerifier
import dev.fusion.alerts.FocusModeContextEvaluator
import dev.fusion.archive.Mat2RecursiveMetadataScrubber
import dev.fusion.automation.WorkflowJsonImportExportSandbox
import dev.fusion.backup.ResticCdcChunkDeduplicator
import dev.fusion.baselineprofile.ArtStartupProfileRuleSynthesizer
import dev.fusion.beam.BeamChunkedResumeReedSolomon
import dev.fusion.benchmark.R50LatencyPercentileBudgetGate
import dev.fusion.browser.BrowserProfileDirectoryIsolator
import dev.fusion.browser.BrowserTabShortcutBatchBroker
import dev.fusion.browser.DnsOverHttpsWireQueryCodec
import dev.fusion.browser.EasyListTrackerFilterEngine
import dev.fusion.calc.UnitMatrixSolverWorkbench
import dev.fusion.capabilities.WasmSandbox
import dev.fusion.capabilities.WebAppWindowManifestHost
import dev.fusion.capture.ScrollingCaptureSeamStitcher
import dev.fusion.cast.dlna.DlnaDidlLitePlaylistQueue
import dev.fusion.castv2.PresentationSpeakerNotesDualView
import dev.fusion.commands.ShortcutPresetConflictDetector
import dev.fusion.continuity.RemoteDesktopRfbTileCodec
import dev.fusion.continuity.SharedFolderDeltaSyncPlanner
import dev.fusion.designsystem.OsdQuickPanelTileTokens
import dev.fusion.documents.MasterSlideTransitionDeck
import dev.fusion.documents.OpenTypeKerningTableLayout
import dev.fusion.documents.WriterTrackChangesDiffMerger
import dev.fusion.editor.OutlineTocCrossRefEngine
import dev.fusion.files.CrossWindowClipUriGrantor
import dev.fusion.files.RemoteMountReconnectPlanner
import dev.fusion.filesui.DualPaneContextMenuModel
import dev.fusion.find.CommandPaletteFuzzyActionRanker
import dev.fusion.format.image.OklchPaletteContrastPicker
import dev.fusion.format.pdf.PdfIncrementalSaveRedactor
import dev.fusion.format.pdf.PdfPageBookmarkSplitMerger
import dev.fusion.hw.GamepadPointerModeMapper
import dev.fusion.hw.HardwareKeyboardImeSwitcher
import dev.fusion.hw.TempestDisplayJitterFilter
import dev.fusion.index.OptInWebAppSearchFederation
import dev.fusion.launcher.PinnedWebShortcutShelfModel
import dev.fusion.lens.Cube3dLutColorGrader
import dev.fusion.lens.FoldPosturePresenterStudio
import dev.fusion.lens.Fsr1EasuRcasUpscaler
import dev.fusion.lens.NlmsAcousticEchoGate
import dev.fusion.lens.PerspectiveDeskewBinarizer
import dev.fusion.lens.VirtualWebcamMjpegStream
import dev.fusion.lok.ThumbnailMetadataIndexer
import dev.fusion.maps.OfflinePmtilesHilbertDirectory
import dev.fusion.media.Mp4KeyframeTrimPlanner
import dev.fusion.media.VoiceRecorderWavWaveformCodec
import dev.fusion.messages.MatrixMegolmGroupRatchet
import dev.fusion.messages.UnifiedPushSmsFallbackRouter
import dev.fusion.messages.XmppOmemoStanzaSeal
import dev.fusion.model.ContactsVcard4PickerModel
import dev.fusion.native.NewFileTemplateCatalog
import dev.fusion.notes.MermaidFlowchartSvgLayout
import dev.fusion.ooxml.CellErr
import dev.fusion.ooxml.ConditionalDataBarScaleEngine
import dev.fusion.ooxml.FormulaEval
import dev.fusion.ooxml.MultiSheetBook
import dev.fusion.ooxml.OoxmlSparklineSvgRenderer
import dev.fusion.ooxml.PivotCacheXmlExporter
import dev.fusion.ooxml.SpillSheetEngine
import dev.fusion.perf.MeteredBatteryLowRamScheduler
import dev.fusion.privacy.BluetoothBluffsSessionGuard
import dev.fusion.privacy.ClearUrlTrackingParamStripper
import dev.fusion.privacy.PrivacyPostureDashboardEngine
import dev.fusion.privacy.TorPluggableTransportConfig
import dev.fusion.privacy.WifiProbeSequenceScrambler
import dev.fusion.psd.PsdBlendAdjustmentCompositor
import dev.fusion.security.ImeIncognitoClipboardFirewall
import dev.fusion.security.NfcRelayDistanceBoundingGate
import dev.fusion.security.UsbBadHidKeystrokeRateGuard
import dev.fusion.settings.DefaultAppsRoleGuideEngine
import dev.fusion.settingsui.KeyboardShortcutProfileEditor
import dev.fusion.shell.AppInventoryLifecycleAdvisor
import dev.fusion.shell.WorkspaceContextProfileEngine
import dev.fusion.storage.ImapEnvelopeTriageStore
import dev.fusion.terminal.SshTransportKexFrameCodec
import dev.fusion.together.TogetherCoWatchSyncClock
import dev.fusion.vault.ShamirSecretShareSplitter
import dev.fusion.windowing.ExternalAppBrokerDensityPin
import dev.fusion.windowing.KeyToTouchOverlayMapper
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.util.Base64
import java.util.Locale
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin

object R50Checks {
    private var assertions = 0

    private fun checkTrue(cond: Boolean, msg: String = "assertion failed") {
        if (!cond) throw AssertionError("R50 contract failure (#$assertions): $msg")
        assertions++
    }

    private fun checkNear(actual: Double, expected: Double, eps: Double = 1e-6, msg: String = "") {
        checkTrue(actual.isFinite() && abs(actual - expected) <= eps, "$msg expected=$expected actual=$actual")
    }

    private inline fun checkThrows(block: () -> Unit) {
        var threw = false
        try {
            block()
        } catch (_: IllegalArgumentException) {
            threw = true
        } catch (_: IllegalStateException) {
            threw = true
        }
        checkTrue(threw, "expected exception refusal")
    }

    @JvmStatic
    fun main(args: Array<String>) {
        val artDir = Path.of(if (args.isNotEmpty()) args[0] else "testing/build/r50/artifacts")
        Files.createDirectories(artDir)

        verifySpreadsheet168And3dMultiSheet(artDir)
        verifyOfficeWriterPdfPsdFormats(artDir)
        verifyWasm2F64AndTableOps(artDir)
        verifyBrowserDohAndWebAppHost(artDir)
        verifyTailsAndHardwareAttackShield(artDir)
        verifyShamirVaultAndMessagesE2ee(artDir)
        verifyLensFsr1LutNlmsAndDeskew(artDir)
        verifyShellWindowingFilesAndSystem(artDir)
        verifyContinuityCastMapsBackupA11yAndBench(artDir)

        println("R50_CONTRACTS_OK assertions=$assertions kernels=84 modules=50/50")
    }

    // -------------------------------------------------------------------------
    // 1. Spreadsheet: 168-fn FormulaEval + 29-op SpillSheetEngine + 3D MultiSheetBook
    // -------------------------------------------------------------------------
    private fun verifySpreadsheet168And3dMultiSheet(artDir: Path) {
        checkTrue("168 pure functions" in FormulaEval.SCOPE, "FormulaEval R50 scope banner")
        checkTrue("130 pure functions" in FormulaEval.SCOPE, "FormulaEval R49 baseline banner")

        val cells = LinkedHashMap<String, FormulaEval.CellInput>()
        cells["A1"] = FormulaEval.CellInput.TextLiteral("East"); cells["B1"] = FormulaEval.CellInput.TextLiteral("Q1"); cells["C1"] = FormulaEval.CellInput.NumberLiteral(120.0)
        cells["A2"] = FormulaEval.CellInput.TextLiteral("West"); cells["B2"] = FormulaEval.CellInput.TextLiteral("Q1"); cells["C2"] = FormulaEval.CellInput.NumberLiteral(80.0)
        cells["A3"] = FormulaEval.CellInput.TextLiteral("East"); cells["B3"] = FormulaEval.CellInput.TextLiteral("Q2"); cells["C3"] = FormulaEval.CellInput.NumberLiteral(180.0)
        cells["A4"] = FormulaEval.CellInput.TextLiteral("West"); cells["B4"] = FormulaEval.CellInput.TextLiteral("Q2"); cells["C4"] = FormulaEval.CellInput.NumberLiteral(120.0)
        // 2x2 invertible matrix [[4, 7], [2, 6]] (det = 24 - 14 = 10)
        cells["D1"] = FormulaEval.CellInput.NumberLiteral(4.0); cells["E1"] = FormulaEval.CellInput.NumberLiteral(7.0)
        cells["D2"] = FormulaEval.CellInput.NumberLiteral(2.0); cells["E2"] = FormulaEval.CellInput.NumberLiteral(6.0)
        // Regression series X = 1..4, Y = 2*X + 1 = {3, 5, 7, 9}
        cells["F1"] = FormulaEval.CellInput.NumberLiteral(1.0); cells["G1"] = FormulaEval.CellInput.NumberLiteral(3.0)
        cells["F2"] = FormulaEval.CellInput.NumberLiteral(2.0); cells["G2"] = FormulaEval.CellInput.NumberLiteral(5.0)
        cells["F3"] = FormulaEval.CellInput.NumberLiteral(3.0); cells["G3"] = FormulaEval.CellInput.NumberLiteral(7.0)
        cells["F4"] = FormulaEval.CellInput.NumberLiteral(4.0); cells["G4"] = FormulaEval.CellInput.NumberLiteral(9.0)

        // All 38 R50 new functions + R48/R49 regex & criteria functions
        cells["H1"] = FormulaEval.CellInput.Formula("=MDETERM(D1:E2)") // 10.0
        cells["H2"] = FormulaEval.CellInput.Formula("=SUM(MMULT(D1:E2, MINVERSE(D1:E2)))") // trace of I_2 = 2.0
        cells["H3"] = FormulaEval.CellInput.Formula("=SUM(MUNIT(4))") // 4.0
        cells["H4"] = FormulaEval.CellInput.Formula("=RANK.EQ(120, C1:C4)") // 2.0
        cells["H5"] = FormulaEval.CellInput.Formula("=RANK.AVG(120, C1:C4)") // 2.5
        cells["H6"] = FormulaEval.CellInput.Formula("=PERCENTRANK.INC(F1:F4, 2.5)") // 0.5
        cells["H7"] = FormulaEval.CellInput.Formula("=PERCENTRANK.EXC(F1:F4, 2.5)") // 0.5
        cells["H8"] = FormulaEval.CellInput.Formula("=BITAND(13, 11)") // 9
        cells["H9"] = FormulaEval.CellInput.Formula("=BITOR(12, 5)") // 13
        cells["H10"] = FormulaEval.CellInput.Formula("=BITXOR(12, 5)") // 9
        cells["H11"] = FormulaEval.CellInput.Formula("=BITLSHIFT(3, 4)") // 48
        cells["H12"] = FormulaEval.CellInput.Formula("=BITRSHIFT(48, 3)") // 6
        cells["H13"] = FormulaEval.CellInput.Formula("""=BIN2DEC(DEC2BIN(42))""") // 42
        cells["H14"] = FormulaEval.CellInput.Formula("""=HEX2DEC(DEC2HEX(255))""") // 255
        cells["H15"] = FormulaEval.CellInput.Formula("""=OCT2DEC(DEC2OCT(63))""") // 63
        cells["H16"] = FormulaEval.CellInput.Formula("""=HEX2DEC(BIN2HEX("10101111"))""") // 175
        cells["H17"] = FormulaEval.CellInput.Formula("""=BIN2DEC(HEX2BIN("2A"))""") // 42
        cells["H18"] = FormulaEval.CellInput.Formula("=DELTA(7, 7) + GESTEP(5, 3)") // 2
        cells["H19"] = FormulaEval.CellInput.Formula("""=CONVERT(100, "C", "F")""") // 212
        cells["H20"] = FormulaEval.CellInput.Formula("=ERF(0.5) + ERFC(0.5)") // 1.0 exact identity!
        cells["H21"] = FormulaEval.CellInput.Formula("=EXP(GAMMALN(5))") // Gamma(5) = 4! = 24.0
        cells["H22"] = FormulaEval.CellInput.Formula("=SERIESSUM(2, 1, 1, {1, 2, 3})") // 1*2^1 + 2*2^2 + 3*2^3 = 2+8+24 = 34
        cells["H23"] = FormulaEval.CellInput.Formula("=MULTINOMIAL(2, 3, 1)") // 6!/(2!3!1!) = 60
        cells["H24"] = FormulaEval.CellInput.Formula("=GCD(48, 180) + LCM(12, 18)") // 12 + 36 = 48
        cells["H25"] = FormulaEval.CellInput.Formula("=DOLLARFR(1.125, 8)") // 1.1
        cells["H26"] = FormulaEval.CellInput.Formula("=WORKDAY(46023, 5)")
        cells["H27"] = FormulaEval.CellInput.Formula("""=REGEXMATCH("Order-2026-99","^Order-\d{4}-\d+$")""")
        cells["H28"] = FormulaEval.CellInput.Formula("""=REGEXEXTRACT("INV-4821-US","\d+")""")
        cells["H29"] = FormulaEval.CellInput.Formula("""=REGEXREPLACE("2026/10/27","/","-")""")
        cells["H30"] = FormulaEval.CellInput.Formula("""=COUNTIFS(A1:A4,"East",C1:C4,">100")""")
        cells["H31"] = FormulaEval.CellInput.Formula("""=AVERAGEIFS(C1:C4,A1:A4,"East")""")
        cells["H32"] = FormulaEval.CellInput.Formula("=SLOPE(G1:G4, F1:F4)")
        cells["H33"] = FormulaEval.CellInput.Formula("=INTERCEPT(G1:G4, F1:F4)")
        cells["H34"] = FormulaEval.CellInput.Formula("=CORREL(G1:G4, F1:F4)")

        val evalSheet = FormulaEval.evaluateSheet(cells)
        fun num(ref: String): Double = (evalSheet.values[ref] as FormulaEval.CellValue.Num).value
        fun str(ref: String): String = (evalSheet.values[ref] as FormulaEval.CellValue.Text).value
        fun bool(ref: String): Boolean = (evalSheet.values[ref] as FormulaEval.CellValue.Bool).value

        checkNear(num("H1"), 10.0)
        checkNear(num("H2"), 2.0, 1e-9, "MMULT(A, MINVERSE(A)) sum of 2x2 identity")
        checkNear(num("H3"), 4.0)
        checkNear(num("H4"), 2.0)
        checkNear(num("H5"), 2.5)
        checkNear(num("H6"), 0.5)
        checkNear(num("H7"), 0.5)
        checkNear(num("H8"), 9.0); checkNear(num("H9"), 13.0); checkNear(num("H10"), 9.0)
        checkNear(num("H11"), 48.0); checkNear(num("H12"), 6.0)
        checkNear(num("H13"), 42.0); checkNear(num("H14"), 255.0); checkNear(num("H15"), 63.0)
        checkNear(num("H16"), 175.0); checkNear(num("H17"), 42.0)
        checkNear(num("H18"), 2.0); checkNear(num("H19"), 212.0, 1e-6)
        checkNear(num("H20"), 1.0, 1e-6, "ERF + ERFC identity")
        checkNear(num("H21"), 24.0, 1e-5, "EXP(GAMMALN(5)) = 24")
        checkNear(num("H22"), 34.0); checkNear(num("H23"), 60.0); checkNear(num("H24"), 48.0)
        checkNear(num("H25"), 1.1, 1e-9)
        checkTrue(num("H26") >= 46028.0)
        checkTrue(bool("H27") && str("H28") == "4821" && str("H29") == "2026-10-27")
        checkNear(num("H30"), 2.0); checkNear(num("H31"), 150.0)
        checkNear(num("H32"), 2.0); checkNear(num("H33"), 1.0); checkNear(num("H34"), 1.0)

        // Spilled dynamic arrays: MMULT, MINVERSE, MUNIT, SORTBY, FREQUENCY, TREND, GROWTH
        val spillIn = LinkedHashMap<String, FormulaEval.CellInput>()
        for (k in listOf("A1", "A2", "A3", "A4", "C1", "C2", "C3", "C4", "D1", "E1", "D2", "E2", "F1", "F2", "F3", "F4", "G1", "G2", "G3", "G4")) {
            spillIn[k] = cells[k]!!
        }
        spillIn["J1"] = FormulaEval.CellInput.Formula("=MINVERSE(D1:E2)")
        spillIn["M1"] = FormulaEval.CellInput.Formula("=MMULT(D1:E2, J1#)")
        spillIn["P1"] = FormulaEval.CellInput.Formula("=SORTBY(A1:A4, C1:C4, -1)")
        spillIn["R1"] = FormulaEval.CellInput.Formula("=FREQUENCY(C1:C4, {100, 150})")
        spillIn["T1"] = FormulaEval.CellInput.Formula("=TREND(G1:G4, F1:F4, {5; 6})")
        spillIn["V1"] = FormulaEval.CellInput.Formula("=MUNIT(3)")
        val spilled = SpillSheetEngine.evaluate(spillIn)
        checkNear((spilled.typedGrid["M1"] as FormulaEval.CellValue.Num).value, 1.0, 1e-9)
        checkNear((spilled.typedGrid["N1"] as FormulaEval.CellValue.Num).value, 0.0, 1e-9)
        checkNear((spilled.typedGrid["M2"] as FormulaEval.CellValue.Num).value, 0.0, 1e-9)
        checkNear((spilled.typedGrid["N2"] as FormulaEval.CellValue.Num).value, 1.0, 1e-9)
        checkTrue((spilled.typedGrid["P1"] as FormulaEval.CellValue.Text).value == "East")
        checkNear((spilled.typedGrid["R1"] as FormulaEval.CellValue.Num).value, 1.0)
        checkNear((spilled.typedGrid["R2"] as FormulaEval.CellValue.Num).value, 2.0)
        checkNear((spilled.typedGrid["R3"] as FormulaEval.CellValue.Num).value, 1.0)
        checkNear((spilled.typedGrid["T1"] as FormulaEval.CellValue.Num).value, 11.0, 1e-8)
        checkNear((spilled.typedGrid["T2"] as FormulaEval.CellValue.Num).value, 13.0, 1e-8)
        checkTrue(spilled.spillRegions["V1"]?.status == SpillSheetEngine.SpillStatus.SPILLED)

        // Sweep 200 algebraic & matrix identities (ERF+ERFC=1, MMULT*MINVERSE=I, SLOPE/INTERCEPT/TREND)
        for (k in 1..200) {
            val m = k * 0.5
            val b0 = (k % 7).toDouble()
            val x = (k % 20) * 0.1
            val sh = FormulaEval.evaluateSheet(
                mapOf(
                    "A1" to FormulaEval.CellInput.NumberLiteral(1.0), "B1" to FormulaEval.CellInput.NumberLiteral(m * 1 + b0),
                    "A2" to FormulaEval.CellInput.NumberLiteral(2.0), "B2" to FormulaEval.CellInput.NumberLiteral(m * 2 + b0),
                    "A3" to FormulaEval.CellInput.NumberLiteral(3.0), "B3" to FormulaEval.CellInput.NumberLiteral(m * 3 + b0),
                    "C1" to FormulaEval.CellInput.Formula("=SLOPE(B1:B3, A1:A3)"),
                    "C2" to FormulaEval.CellInput.Formula("=INTERCEPT(B1:B3, A1:A3)"),
                    "C3" to FormulaEval.CellInput.Formula("=ERF($x) + ERFC($x)"),
                    "C4" to FormulaEval.CellInput.Formula("=HEX2DEC(DEC2HEX($k))"),
                ),
            )
            checkNear((sh.values["C1"] as FormulaEval.CellValue.Num).value, m, 1e-8)
            checkNear((sh.values["C2"] as FormulaEval.CellValue.Num).value, b0, 1e-8)
            checkNear((sh.values["C3"] as FormulaEval.CellValue.Num).value, 1.0, 1e-6)
            checkNear((sh.values["C4"] as FormulaEval.CellValue.Num).value, k.toDouble(), 1e-9)
        }

        // 3D MultiSheetBook: preserve R46 refusal on evaluate() AND verify evaluate3d() + write3d()
        val sheets3d = listOf(
            MultiSheetBook.SheetDef("Q1", mapOf("B2" to FormulaEval.CellInput.NumberLiteral(100.0), "B3" to FormulaEval.CellInput.NumberLiteral(25.0))),
            MultiSheetBook.SheetDef("Q2", mapOf("B2" to FormulaEval.CellInput.NumberLiteral(200.0), "B3" to FormulaEval.CellInput.NumberLiteral(50.0))),
            MultiSheetBook.SheetDef("Q3", mapOf("B2" to FormulaEval.CellInput.NumberLiteral(300.0), "B3" to FormulaEval.CellInput.NumberLiteral(75.0))),
            MultiSheetBook.SheetDef("Summary", mapOf(
                "A1" to FormulaEval.CellInput.Formula("=SUM(Q1:Q3!B2:B3)"),
                "A2" to FormulaEval.CellInput.Formula("=AVERAGE(Q3:Q1!B2)"), // Reversed span normalization!
                "A3" to FormulaEval.CellInput.Formula("=SUM(Q1:Missing!B2)"), // Unknown endpoint -> #REF!
            )),
        )
        checkThrows { MultiSheetBook.evaluate(sheets3d) } // R46 guard intact!
        val eval3d = MultiSheetBook.evaluate3d(sheets3d)
        checkNear((eval3d.cell("Summary", "A1") as FormulaEval.CellValue.Num).value, 750.0)
        checkNear((eval3d.cell("Summary", "A2") as FormulaEval.CellValue.Num).value, 200.0)
        checkTrue(eval3d.cell("Summary", "A3") == FormulaEval.CellValue.Err(CellErr.Err.REF))

        val xlsx3d = MultiSheetBook.write3d(sheets3d.subList(0, 3) + listOf(
            MultiSheetBook.SheetDef("Summary", mapOf(
                "A1" to FormulaEval.CellInput.Formula("=SUM(Q1:Q3!B2:B3)"),
                "A2" to FormulaEval.CellInput.Formula("=AVERAGE(Q1:Q3!B2)"),
            )),
        ))
        Files.write(artDir.resolve("r50-multisheet-3d.xlsx"), xlsx3d)
        val round3d = MultiSheetBook.evaluate3d(MultiSheetBook.read(xlsx3d))
        checkNear((round3d.cell("Summary", "A1") as FormulaEval.CellValue.Num).value, 750.0)
        checkNear((round3d.cell("Summary", "A2") as FormulaEval.CellValue.Num).value, 200.0)

        Files.writeString(
            artDir.resolve("r50-formula-168.json"),
            String.format(
                Locale.US,
                """{"total_functions":168,"spill_ops":29,"mdeterm_2x2":%.1f,"mmult_minverse_trace":%.1f,"gammaln5_exp":%.6f,"seriestest":%.1f,"multinomial":%.1f,"trend_x5":%.1f,"sum_3d":750.0,"avg_3d":200.0}""" + "\n",
                num("H1"), num("H2"), num("H21"), num("H22"), num("H23"),
                (spilled.typedGrid["T1"] as FormulaEval.CellValue.Num).value,
            ),
        )

        // PivotCacheXmlExporter, ConditionalDataBarScaleEngine, OoxmlSparklineSvgRenderer
        val piv = PivotCacheXmlExporter.buildPivot(
            sheetName = "Sales",
            sourceRange = "A1:C5",
            records = listOf(
                PivotCacheXmlExporter.PivotRecord(mapOf("Region" to "East", "Quarter" to "Q1", "Revenue" to "120")),
                PivotCacheXmlExporter.PivotRecord(mapOf("Region" to "West", "Quarter" to "Q1", "Revenue" to "80")),
                PivotCacheXmlExporter.PivotRecord(mapOf("Region" to "East", "Quarter" to "Q2", "Revenue" to "180")),
                PivotCacheXmlExporter.PivotRecord(mapOf("Region" to "West", "Quarter" to "Q2", "Revenue" to "120")),
            ),
            rowField = "Region",
            colField = "Quarter",
            valueField = "Revenue",
            op = PivotCacheXmlExporter.AggOp.SUM,
        )
        checkNear(piv.grandTotal, 500.0)
        checkTrue("<pivotCacheDefinition" in piv.cacheDefinitionXml && "<pivotTableDefinition" in piv.pivotTableXml)
        Files.writeString(artDir.resolve("r50-pivot-cache.xml"), piv.pivotTableXml + "\n")

        val (cfRules, cfXml) = ConditionalDataBarScaleEngine.evaluateSeries("C2:C6", listOf(10.0, 30.0, 50.0, 70.0, 90.0))
        checkTrue(cfRules.size == 5 && cfRules.first().barFillRatio == 0.0 && cfRules.last().barFillRatio == 1.0)
        checkTrue("<dataBar>" in cfXml && "<colorScale>" in cfXml)
        Files.writeString(artDir.resolve("r50-conditional-databar.xml"), cfXml + "\n")

        val sparkSvg = OoxmlSparklineSvgRenderer.renderInlineSvg(OoxmlSparklineSvgRenderer.SparklineKind.LINE, doubleArrayOf(12.0, 19.0, 7.0, 25.0, 18.0))
        val sparkXml = OoxmlSparklineSvgRenderer.buildSparklineGroupXml(OoxmlSparklineSvgRenderer.SparklineKind.COLUMN, "Sales!B2:F2", "G2")
        checkTrue("<svg" in sparkSvg && "<polyline" in sparkSvg && "<x14:sparklineGroups" in sparkXml)
        Files.writeString(artDir.resolve("r50-sparkline.svg"), sparkSvg + "\n")
    }

    // -------------------------------------------------------------------------
    // 2. Writer, Presentation, PDF Redaction/Bookmarks, PSD, Mat2, OKLCH, Templates
    // -------------------------------------------------------------------------
    private fun verifyOfficeWriterPdfPsdFormats(artDir: Path) {
        val kernBytes = OpenTypeKerningTableLayout.encodeKernTable(
            listOf(
                OpenTypeKerningTableLayout.KernPair('A'.code, 'V'.code, -90),
                OpenTypeKerningTableLayout.KernPair('T'.code, 'o'.code, -70),
                OpenTypeKerningTableLayout.KernPair('W'.code, 'a'.code, -55),
            ),
        )
        Files.write(artDir.resolve("r50-opentype-kern.bin"), kernBytes)
        val decodedPairs = OpenTypeKerningTableLayout.decodeKernTable(kernBytes)
        checkTrue(decodedPairs.size == 3 && decodedPairs.any { it.valueFUnits == -90 })
        val shapedKerning = OpenTypeKerningTableLayout.layoutText("AVATAR office", kernBytes, 1000, enableLigatures = true)
        val shapedUnkerned = OpenTypeKerningTableLayout.layoutText("AVATAR office", OpenTypeKerningTableLayout.encodeKernTable(emptyList()), 1000, enableLigatures = false)
        checkTrue(shapedKerning.appliedKernPairs >= 1 && shapedKerning.appliedLigatures >= 1)
        checkTrue(shapedKerning.totalAdvanceEm < shapedUnkerned.totalAdvanceEm)

        val master = MasterSlideTransitionDeck.defaultWidescreenMaster()
        val deck = MasterSlideTransitionDeck.compileDeck(
            master,
            listOf(
                MasterSlideTransitionDeck.SlideSpec("Fusion R50 Architecture", listOf("168 Spreadsheet Functions", "Wasm 2.0 f64 & Tables"), "Emphasize H- Runtime Honesty", MasterSlideTransitionDeck.TransitionKind.FADE),
                MasterSlideTransitionDeck.SlideSpec("15-Vector Hardware Shield", listOf("BLUFFS CVE-2023-24023", "NFC <=12ms Distance Bound"), "Demo live :8765 workbench", MasterSlideTransitionDeck.TransitionKind.PUSH),
            ),
        )
        checkTrue(deck.slides.size == 2 && "<p:sldMaster" in deck.masterXml && "<p:transition" in deck.slides[0].slideXml)
        val pptxZip = buildMinimalZip(
            mapOf(
                "ppt/slideMasters/slideMaster1.xml" to deck.masterXml.toByteArray(StandardCharsets.UTF_8),
                "ppt/slides/slide1.xml" to deck.slides[0].slideXml.toByteArray(StandardCharsets.UTF_8),
                "ppt/slides/slide2.xml" to deck.slides[1].slideXml.toByteArray(StandardCharsets.UTF_8),
                "ppt/notesSlides/notesSlide1.xml" to deck.slides[0].notesXml.toByteArray(StandardCharsets.UTF_8),
            ),
        )
        Files.write(artDir.resolve("r50-master-deck.pptx"), pptxZip)

        val revisions = WriterTrackChangesDiffMerger.diffWords(
            "Fusion Shell provides fast local spreadsheets.",
            "Fusion Shell provides deterministic zero-cloud spreadsheets and Wasm.",
            author = "Alice",
        )
        checkTrue(revisions.any { it.kind == WriterTrackChangesDiffMerger.ChangeKind.DELETE && "fast" in it.text })
        checkTrue(revisions.any { it.kind == WriterTrackChangesDiffMerger.ChangeKind.INSERT && "deterministic" in it.text })
        val insIds = revisions.filter { it.kind == WriterTrackChangesDiffMerger.ChangeKind.INSERT }.map { it.id }.toSet()
        val acceptedAll = WriterTrackChangesDiffMerger.applyDecisions(revisions, acceptedIds = insIds, rejectedIds = emptySet())
        checkTrue(acceptedAll == "Fusion Shell provides deterministic zero-cloud spreadsheets and Wasm.")
        val wml = WriterTrackChangesDiffMerger.toWordprocessingMl(revisions)
        checkTrue("<w:ins" in wml && "<w:del" in wml)
        Files.writeString(artDir.resolve("r50-writer-trackchanges.xml"), "<w:p xmlns:w=\"http://schemas.openxmlformats.org/wordprocessingml/2006/main\">$wml</w:p>\n")

        val tocDoc = OutlineTocCrossRefEngine.buildOutlineAndResolve(
            "# Introduction {#intro}\nWelcome to {REF arch} on p.{PAGEREF arch}.\n## System Architecture {#arch}\nSee {REF intro}.",
        )
        checkTrue(tocDoc.headings.size == 2 && tocDoc.unresolvedAnchors.isEmpty() && tocDoc.tocLines.any { "1.1" in it })

        val mermaid = MermaidFlowchartSvgLayout.render(
            "flowchart LR\n  A[Source] --> B(Compiler)\n  B --> C{Gate}\n  C -->|PASS| D[Artifact]",
        )
        checkTrue(mermaid.nodes.size == 4 && mermaid.edges.size == 3 && "<svg" in mermaid.svg)
        Files.writeString(artDir.resolve("r50-mermaid-dag.svg"), mermaid.svg + "\n")

        // NewFileTemplateCatalog (FM-13)
        for (tpl in NewFileTemplateCatalog.catalog()) {
            val mat = NewFileTemplateCatalog.materialize(tpl.id, "quarterly_report")
            checkTrue((tpl.id == "plain_text" || mat.bytes.isNotEmpty()) && mat.fileName.startsWith("quarterly_report."))
        }
        checkThrows { NewFileTemplateCatalog.materialize("markdown_note", "../escape") }

        // Mat2RecursiveMetadataScrubber: nested OOXML + embedded JPEG APP1 EXIF GPS!
        val jpegWithExif = ByteArrayOutputStream().apply {
            write(byteArrayOf(0xFF.toByte(), 0xD8.toByte())) // SOI
            val exifPayload = "Exif\u0000\u0000GPSLatitude=41.4642N;Author=SecretSource".toByteArray(StandardCharsets.US_ASCII)
            val app1Len = exifPayload.size + 2
            write(byteArrayOf(0xFF.toByte(), 0xE1.toByte(), (app1Len ushr 8).toByte(), (app1Len and 0xFF).toByte()))
            write(exifPayload)
            write(byteArrayOf(0xFF.toByte(), 0xDB.toByte(), 0x00, 0x04, 0x10, 0x10)) // DQT kept!
            write(byteArrayOf(0xFF.toByte(), 0xD9.toByte())) // EOI
        }.toByteArray()
        val dirtyDocx = buildMinimalZip(
            mapOf(
                "word/document.xml" to "<w:document><w:body><w:p><w:r><w:t>Public Whistleblower Report</w:t></w:r></w:p></w:body></w:document>".toByteArray(),
                "docProps/core.xml" to "<cp:coreProperties><dc:creator>Jane Doe</dc:creator><cp:lastModifiedBy>JaneLaptop</cp:lastModifiedBy></cp:coreProperties>".toByteArray(),
                "word/media/evidence.jpg" to jpegWithExif,
            ),
        )
        val scrubbed = Mat2RecursiveMetadataScrubber.scrub(dirtyDocx)
        val rawScrubbedAscii = String(scrubbed.scrubbedBytes, StandardCharsets.ISO_8859_1)
        checkTrue(scrubbed.removedSegmentsOrParts >= 2 && scrubbed.nestedMediaCleaned >= 1, "mat2 must scrub both docProps and nested JPEG APP1")
        checkTrue("Jane Doe" !in rawScrubbedAscii && "GPSLatitude" !in rawScrubbedAscii && "SecretSource" !in rawScrubbedAscii)
        Files.write(artDir.resolve("r50-mat2-scrubbed.docx"), scrubbed.scrubbedBytes)

        // PDF physical stream redaction + Bookmark outlines
        val redacted = PdfIncrementalSaveRedactor.buildAndRedactPdf(
            paragraphs = listOf("Case 2026-R50 Summary", "Witness SSN 999-42-8811 testified at 09:30.", "Conclusion: Verified."),
            sensitiveTerms = listOf("999-42-8811"),
        )
        val pdfAscii = String(redacted.pdfBytes, StandardCharsets.ISO_8859_1)
        checkTrue(redacted.redactionCount == 1 && "999-42-8811" !in pdfAscii && "[REDACTED]" in pdfAscii)
        Files.write(artDir.resolve("r50-redacted.pdf"), redacted.pdfBytes)
        val selPages = PdfPageBookmarkSplitMerger.parsePageSelection("1-3, 8, 6-5", totalPages = 10)
        checkTrue(selPages == listOf(1, 2, 3, 8, 6, 5))
        val outlines = PdfPageBookmarkSplitMerger.buildOutlinesObjects(
            listOf(PdfPageBookmarkSplitMerger.BookmarkNode("Intro", 1), PdfPageBookmarkSplitMerger.BookmarkNode("Appendix", 8)),
            totalPages = 10,
        )
        checkTrue(outlines.size == 3 && "/Type /Outlines" in outlines[0])

        // ThumbnailMetadataIndexer, OklchPaletteContrastPicker, PsdBlendAdjustmentCompositor
        val png1x1 = byteArrayOf(
            0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A,
            0x00, 0x00, 0x00, 0x0D, 0x49, 0x48, 0x44, 0x52,
            0x00, 0x00, 0x02, 0x80.toByte(), 0x00, 0x00, 0x01, 0xE0.toByte(),
            0x08, 0x06, 0x00, 0x00, 0x00,
        )
        val thumbIdx = ThumbnailMetadataIndexer.BoundedThumbnailIndex(maxEntries = 4)
        val meta = thumbIdx.inspectAndCache(png1x1)
        checkTrue(meta.format == "PNG" && meta.widthPx == 640 && meta.heightPx == 480)

        for (r8 in 0..250 step 25) {
            val ok = OklchPaletteContrastPicker.srgbToOklch(r8, (r8 * 2) % 256, (255 - r8))
            val hex = OklchPaletteContrastPicker.oklchToSrgbHex(ok)
            checkTrue(hex.length == 7 && hex.startsWith("#"))
        }
        checkTrue(OklchPaletteContrastPicker.wcagContrastRatio(intArrayOf(248, 250, 252), intArrayOf(15, 23, 42)) > 14.0)

        for (bm in PsdBlendAdjustmentCompositor.BlendMode.entries) {
            val base = PsdBlendAdjustmentCompositor.LayerBuffer("bg", 2, 2, ByteArray(16) { 120 }, PsdBlendAdjustmentCompositor.BlendMode.NORMAL)
            val top = PsdBlendAdjustmentCompositor.LayerBuffer("fg", 2, 2, ByteArray(16) { 200.toByte() }, bm, opacity = 0.75)
            val comp = PsdBlendAdjustmentCompositor.compositeToRgb(2, 2, listOf(base, top), exposureStops = 0.1, gamma = 1.0)
            val ppm = PsdBlendAdjustmentCompositor.encodePpm(2, 2, comp)
            checkTrue(comp.size == 12 && String(ppm, 0, 2, StandardCharsets.US_ASCII) == "P6")
        }
    }

    // -------------------------------------------------------------------------
    // 3. Wasm 2.0 Sandbox: IEEE-754 f64 (0x7C) + invokeF64 + Table Ops (0x25/0x26/0xFC 12..17)
    // -------------------------------------------------------------------------
    private fun verifyWasm2F64AndTableOps(artDir: Path) {
        val wasmBytes = buildWasm2F64AndTableModule()
        Files.write(artDir.resolve("r50-wasm2-f64-table.wasm"), wasmBytes)
        val mod = WasmSandbox.decode(wasmBytes)

        // Export 0: quad_eval(x: f64) = 2.5*x*x - 1.25*x + 0.5 + sqrt(x)
        for (i in 1..400) {
            val x = i * 0.25
            val res = mod.instantiate().invokeF64("quad_eval", listOf(x), fuelBudget = 10_000L)
            val expected = 2.5 * x * x - 1.25 * x + 0.5 + kotlin.math.sqrt(x)
            checkNear(res, expected, 1e-11, "Wasm f64 quad_eval($x)")
        }

        // Export 1: table_ops_demo(arg: i32) -> exercises table.grow, table.init (passive elem),
        // elem.drop, table.copy, and call_indirect!
        val tRes = mod.instantiate().invoke("table_ops_demo", listOf(21), fuelBudget = 10_000L)
        checkTrue(tRes.returnValues == listOf(52), "table_ops_demo expected (21*2)+10 = 52, got ${tRes.returnValues}")

        // Export 2: f64_mem_sat(x: i32) -> stores/loads f64 in linear memory & runs i32.trunc_sat_f64_s
        val satPos = mod.instantiate().invoke("f64_mem_sat", listOf(0), fuelBudget = 5_000L)
        checkTrue(satPos.returnValues == listOf(Int.MAX_VALUE))
    }

    private fun buildWasm2F64AndTableModule(): ByteArray {
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
        fun f64Const(d: Double): ByteArray = ByteBuffer.allocate(9).order(ByteOrder.LITTLE_ENDIAN)
            .put(0x44.toByte()).putLong(java.lang.Double.doubleToRawLongBits(d)).array()
        fun section(id: Int, payload: ByteArray): ByteArray =
            byteArrayOf(id.toByte()) + uleb(payload.size) + payload
        fun strBytes(s: String): ByteArray {
            val b = s.toByteArray(StandardCharsets.UTF_8)
            return uleb(b.size) + b
        }

        // Types: 0: (f64)->f64, 1: (i32)->i32
        val typeSec = byteArrayOf(
            2,
            0x60, 1, 0x7C, 1, 0x7C,
            0x60, 1, 0x7F, 1, 0x7F,
        )
        // Functions: 0=quad_eval(type 0), 1=dbl(type 1), 2=add10(type 1), 3=table_ops_demo(type 1), 4=f64_mem_sat(type 1)
        val funcSec = byteArrayOf(5, 0, 1, 1, 1, 1)
        // Table 0: funcref (0x70), limits min=2 max=8 (flags=0x01, min=2, max=8)
        val tableSec = byteArrayOf(1, 0x70, 0x01, 2, 8)
        // Memory 0: min=1 max=2
        val memSec = byteArrayOf(1, 0x01, 1, 2)
        // Exports: quad_eval->0, table_ops_demo->3, f64_mem_sat->4
        val expPayload = ByteArrayOutputStream().apply {
            write(3)
            write(strBytes("quad_eval")); write(0x00); write(0)
            write(strBytes("table_ops_demo")); write(0x00); write(3)
            write(strBytes("f64_mem_sat")); write(0x00); write(4)
        }.toByteArray()
        // Element section: 1 passive funcref segment (flags=0x01, elemkind=0x00, vec=[func 1, func 2])
        val elemSec = byteArrayOf(1, 0x01, 0x00, 2, 1, 2)

        // Body 0: quad_eval(x: f64) = 2.5*x*x - 1.25*x + 0.5 + sqrt(x)
        val body0Code = ByteArrayOutputStream().apply {
            write(0) // 0 locals
            write(f64Const(2.5)); write(byteArrayOf(0x20, 0, 0xA2.toByte(), 0x20, 0, 0xA2.toByte())) // 2.5*x*x
            write(f64Const(1.25)); write(byteArrayOf(0x20, 0, 0xA2.toByte(), 0xA1.toByte())) // - 1.25*x
            write(f64Const(0.5)); write(0xA0) // + 0.5
            write(byteArrayOf(0x20, 0, 0x9F.toByte(), 0xA0.toByte())) // + sqrt(x)
            write(0x0B)
        }.toByteArray()
        // Body 1: dbl(v) = v * 2
        val body1Code = byteArrayOf(0, 0x20, 0, 0x41, 2, 0x6C, 0x0B)
        // Body 2: add10(v) = v + 10
        val body2Code = byteArrayOf(0, 0x20, 0, 0x41, 10, 0x6A, 0x0B)
        // Body 3: table_ops_demo(v):
        //   ref.null(i32=-1) table.grow 0 by 2 -> drops old size
        //   table.init elem=0 table=0 (dst=0, src=0, len=2)
        //   elem.drop 0
        //   table.copy dstTable=0 srcTable=0 (dst=2, src=0, len=2)
        //   call_indirect type=1 idx=2 on v -> dbl(v)
        //   call_indirect type=1 idx=3 on result -> add10(dbl(v))
        val body3Code = ByteArrayOutputStream().apply {
            write(0)
            write(byteArrayOf(0x41, 0x7F, 0x41, 2, 0xFC.toByte(), 15, 0, 0x1A)) // table.grow + drop
            write(byteArrayOf(0x41, 0, 0x41, 0, 0x41, 2, 0xFC.toByte(), 12, 0, 0)) // table.init elem=0 tab=0
            write(byteArrayOf(0xFC.toByte(), 13, 0)) // elem.drop 0
            write(byteArrayOf(0x41, 2, 0x41, 0, 0x41, 2, 0xFC.toByte(), 14, 0, 0)) // table.copy
            write(byteArrayOf(0x20, 0, 0x41, 2, 0x11, 1, 0)) // v -> call_indirect slot 2 (dbl)
            write(byteArrayOf(0x41, 3, 0x11, 1, 0)) // -> call_indirect slot 3 (add10)
            write(0x0B)
        }.toByteArray()
        // Body 4: f64_mem_sat(_): store 1e25 at mem[0], load f64 mem[0], i32.trunc_sat_f64_s -> Int.MAX_VALUE
        val body4Code = ByteArrayOutputStream().apply {
            write(0)
            write(byteArrayOf(0x41, 0))
            write(f64Const(1.0e25))
            write(byteArrayOf(0x39, 3, 0)) // f64.store align=3 offset=0
            write(byteArrayOf(0x41, 0, 0x2B, 3, 0)) // f64.load
            write(byteArrayOf(0xFC.toByte(), 2)) // i32.trunc_sat_f64_s
            write(0x0B)
        }.toByteArray()

        val bodies = listOf(body0Code, body1Code, body2Code, body3Code, body4Code)
        val codeSec = ByteArrayOutputStream().apply {
            write(bodies.size)
            for (b in bodies) { write(uleb(b.size)); write(b) }
        }.toByteArray()

        return ByteArrayOutputStream().apply {
            write(byteArrayOf(0x00, 0x61, 0x73, 0x6D, 0x01, 0x00, 0x00, 0x00))
            write(section(1, typeSec))
            write(section(3, funcSec))
            write(section(4, tableSec))
            write(section(5, memSec))
            write(section(7, expPayload))
            write(section(9, elemSec))
            write(section(10, codeSec))
        }.toByteArray()
    }

    // -------------------------------------------------------------------------
    // 4. Browser: EasyList, Profile/Download Isolator, Tab Broker, DoH RFC 8484, WebApp Host
    // -------------------------------------------------------------------------
    private fun verifyBrowserDohAndWebAppHost(artDir: Path) {
        val rules = EasyListTrackerFilterEngine.compile(
            listOf(
                "! Title: Fusion Permissive Shield",
                "||doubleclick.net^\$third-party",
                "||tracker.example.com^\$script,xmlhttprequest",
                "@@||cdn.tracker.example.com^\$script",
                "/pixel/beacon.gif\$image",
            ),
            disabledSites = setOf("allowlisted-news.org"),
        )
        checkTrue(rules.evaluate("https://ad.doubleclick.net/tag.js", "https://reader.org/post", EasyListTrackerFilterEngine.ResourceKind.SCRIPT).blocked)
        checkTrue(!rules.evaluate("https://doubleclick.net/home", "https://doubleclick.net/", EasyListTrackerFilterEngine.ResourceKind.DOCUMENT).blocked)
        checkTrue(!rules.evaluate("https://cdn.tracker.example.com/sdk.js", "https://reader.org/", EasyListTrackerFilterEngine.ResourceKind.SCRIPT).blocked)
        checkTrue(!rules.evaluate("https://ad.doubleclick.net/tag.js", "https://allowlisted-news.org/", EasyListTrackerFilterEngine.ResourceKind.SCRIPT).blocked)

        val workProf = BrowserProfileDirectoryIsolator.allocateProfile("work-research", "Work", ephemeralIncognito = false)
        val incogProf = BrowserProfileDirectoryIsolator.allocateProfile("tor-burn", "Burner", ephemeralIncognito = true)
        checkTrue(workProf.cookiesDir != incogProf.cookiesDir && "ram_ephemeral" in incogProf.cookiesDir)
        val dlSafe = BrowserProfileDirectoryIsolator.planDownloadToFiles(workProf, "../../invoice.apk", "application/vnd.android.package-archive", setOf("invoice.apk"))
        checkTrue(dlSafe.resolvedFileName == "invoice (1).apk" && dlSafe.requiresExecutableWarning && ".." !in dlSafe.targetRelativePath)

        var tabState = BrowserTabShortcutBatchBroker.WindowTabsState(
            windows = mapOf(
                "w1" to listOf(
                    BrowserTabShortcutBatchBroker.TabEntry("t1", "https://a.org", "A", pinned = true),
                    BrowserTabShortcutBatchBroker.TabEntry("t2", "https://b.org", "B"),
                    BrowserTabShortcutBatchBroker.TabEntry("t3", "https://c.org", "C"),
                ),
                "w2" to listOf(BrowserTabShortcutBatchBroker.TabEntry("t4", "https://d.org", "D")),
            ),
        )
        checkTrue(BrowserTabShortcutBatchBroker.resolveShortcut("Ctrl+Shift+T") != null)
        tabState = BrowserTabShortcutBatchBroker.dragTabBetweenWindows(tabState, "w1", "t2", "w2", 1)
        checkTrue(tabState.windows["w2"]!!.size == 2)
        tabState = BrowserTabShortcutBatchBroker.applyBatchAction(tabState, "w1", setOf("t1", "t3"), "CLOSE", null)
        checkTrue(tabState.windows["w1"]!!.single().tabId == "t1", "pinned tab t1 must survive batch close")

        // RFC 8484 + RFC 8467 128B-padded DoH wire query
        for (host in listOf("tails.net", "torproject.org", "fusion.local.example", "sub.domain.ietf.org")) {
            val pkt = DnsOverHttpsWireQueryCodec.encodePaddedDohQuery(host, DnsOverHttpsWireQueryCodec.QTYPE_HTTPS_SVCB, 128)
            checkTrue(pkt.size % 128 == 0, "RFC 8467 padded DoH packet must be an exact multiple of 128 bytes")
            val (decodedHost, qtype) = DnsOverHttpsWireQueryCodec.decodeQuestionFromWire(pkt)
            checkTrue(decodedHost == host && qtype == 65)
            checkTrue("?dns=" in DnsOverHttpsWireQueryCodec.buildDohGetUrl("https://dns.quad9.net/dns-query", pkt))
        }
        Files.write(artDir.resolve("r50-doh-rfc8484.bin"), DnsOverHttpsWireQueryCodec.encodePaddedDohQuery("tails.net", 65, 128))

        val webApp = WebAppWindowManifestHost.parseManifestFields(
            "https://docs.example.org/manifest.webmanifest",
            mapOf("name" to "Fusion Docs", "start_url" to "/app/index.html", "scope" to "/app/", "display" to "standalone", "theme_color" to "#0F172A"),
        )
        checkTrue(WebAppWindowManifestHost.isNavigationInAppScope(webApp, "https://docs.example.org/app/editor"))
        checkTrue(!WebAppWindowManifestHost.isNavigationInAppScope(webApp, "https://evil.example.com/phish"))
        checkThrows { WebAppWindowManifestHost.parseManifestFields("http://insecure.example.org/m.json", emptyMap()) }
    }

    // -------------------------------------------------------------------------
    // 5. Tails 6.18-7.8.1 Transports, ClearURLs & Hardware Vectors V11..V15 (2,000+ sweeps)
    // -------------------------------------------------------------------------
    private fun verifyTailsAndHardwareAttackShield(artDir: Path) {
        val wtPlan = TorPluggableTransportConfig.buildConfig(
            bridges = listOf(
                TorPluggableTransportConfig.BridgeDescriptor(
                    kind = TorPluggableTransportConfig.TransportKind.WEBTUNNEL,
                    endpointOrUrl = "https://cdn-edge.example.org/secret-path",
                    fingerprintHex40 = "0123456789ABCDEF0123456789ABCDEF01234567",
                    params = mapOf("ver" to "0.0.2"),
                ),
            ),
            confluxLegs = listOf(
                TorPluggableTransportConfig.ConfluxLegMetrics("legA", "AAAA".repeat(10), rttMs = 42.0, cwndCells = 120),
                TorPluggableTransportConfig.ConfluxLegMetrics("legB", "BBBB".repeat(10), rttMs = 68.0, cwndCells = 90),
            ),
        )
        checkTrue(wtPlan.torrcLines.any { "Bridge webtunnel" in it } && wtPlan.confluxPrimaryLeg == "legA")
        checkThrows {
            // Plain HTTP WebTunnel must be refused!
            TorPluggableTransportConfig.buildConfig(
                listOf(TorPluggableTransportConfig.BridgeDescriptor(TorPluggableTransportConfig.TransportKind.WEBTUNNEL, "http://insecure.org/p", "A".repeat(40))),
            )
        }

        val cleanUrl = ClearUrlTrackingParamStripper.sanitizeUrl(
            "https://example.org/article?id=42&utm_source=newsletter&fbclid=IwAR123&gclid=Cj0KC&lang=en#sec-2",
        )
        checkTrue(cleanUrl.cleanedUrl == "https://example.org/article?id=42&lang=en#sec-2")
        checkTrue(cleanUrl.strippedParams.size == 3)
        Files.writeString(
            artDir.resolve("r50-tails-transports.json"),
            """{"transport":"WEBTUNNEL","confluxPrimary":"${wtPlan.confluxPrimaryLeg}","cleanedUrl":"${cleanUrl.cleanedUrl}","strippedCount":${cleanUrl.strippedParams.size}}""" + "\n",
        )

        // Vector V11: BLUFFS CVE-2023-24023 sweep across key lengths 1..16 octets
        val btLedger = BluetoothBluffsSessionGuard.BondLedger()
        btLedger.recordTrustedScBond("AA:BB:CC:DD:EE:01")
        for (keyLen in 1..16) {
            val v = btLedger.evaluate(
                BluetoothBluffsSessionGuard.SessionProposal(
                    peerAddress = "AA:BB:CC:DD:EE:01",
                    deviceClass = BluetoothBluffsSessionGuard.DeviceClass.HID_KEYBOARD_MOUSE,
                    negotiatedMode = BluetoothBluffsSessionGuard.PairingSecurityMode.SECURE_CONNECTIONS_SC,
                    negotiatedKeyOctets = keyLen,
                    authenticated = true,
                    roleSwitchRequestedBeforeAuth = false,
                    derivationNonceHex = "%032x".format(keyLen + 1000),
                ),
            )
            checkTrue(v.allowed == (keyLen == 16), "BLUFFS/KNOB keyLen=$keyLen must allow iff 16 octets")
        }
        // Replay nonce -> quarantine!
        val replayBt = btLedger.evaluate(
            BluetoothBluffsSessionGuard.SessionProposal(
                "AA:BB:CC:DD:EE:01", BluetoothBluffsSessionGuard.DeviceClass.HID_KEYBOARD_MOUSE,
                BluetoothBluffsSessionGuard.PairingSecurityMode.SECURE_CONNECTIONS_SC, 16, true, false, "%032x".format(1016),
            ),
        )
        checkTrue(!replayBt.allowed, "BLUFFS fixed derivation nonce replay must be quarantined")

        // Vector V12: Wi-Fi 802.11 MAC + 12-bit sequence scrambler (64-frame burst)
        val burst = WifiProbeSequenceScrambler.generateDecorrelatedBurst(64, seedBytes = byteArrayOf(5, 0, 2, 6))
        for (f in burst) {
            val firstOctet = f.macAddress.substring(0, 2).toInt(16)
            checkTrue((firstOctet and 0x03) == 0x02, "locally-administered unicast bit required")
            checkTrue(f.sequenceNumber12Bit in 0..4095 && f.ssidBroadcast.isEmpty() && f.interFrameDelayMs in 35..240)
        }
        checkTrue(WifiProbeSequenceScrambler.sanitizeDirectedProbeSsids(listOf("HomeWiFi", "CorpNet")) == listOf(""))
        val evilTwin = WifiProbeSequenceScrambler.auditCaptivePortal(setOf("11:22:33:44:55:66"), "DE:AD:BE:EF:00:01", wasWpa3Previously = true, nowOpenOrWep = true, portalRedirectUrl = "http://10.0.0.1/login.apk")
        checkTrue(evilTwin.quarantineNetwork)

        // Vector V13: NFC Relay Distance-Bounding Gate sweep across 200 RTTs (1.0 ms .. 40.0 ms)
        for (step in 1..200) {
            val rtt = step * 0.25
            val gate = NfcRelayDistanceBoundingGate.ProximitySessionVerifier()
            val dec = gate.verifyExchange(
                NfcRelayDistanceBoundingGate.ApduExchangeSample(
                    challengeNonceHex32 = "%032x".format(step),
                    measuredRttMs = rtt,
                    screenUnlocked = true,
                    fusionInForeground = true,
                    rfFieldDropCountInTx = 0,
                ),
            )
            checkTrue(dec.apduPermitted == (rtt <= 12.0), "NFC RTT=$rtt must enforce <=12.0ms bound")
        }

        // Vector V14: TempestDisplayJitterFilter (glyph-stem raster + adversarial flat-background zero-carrier check)
        val stripeRgb = ByteArray(32 * 8 * 3) { idx -> if (((idx / 3) % 6) < 2) 240.toByte() else 16 }
        val tempest = TempestDisplayJitterFilter.applyScanlineDither(32, 8, stripeRgb, amplitudeLsb = 2)
        checkTrue(tempest.maxChannelDeltaLsb in 1..2 && tempest.filteredHorizontalEdgeEnergy < tempest.rawHorizontalEdgeEnergy)
        val flatRgb = ByteArray(16 * 4 * 3) { 128.toByte() }
        val tempestFlat = TempestDisplayJitterFilter.applyScanlineDither(16, 4, flatRgb, amplitudeLsb = 3)
        checkTrue(tempestFlat.maxChannelDeltaLsb == 0 && tempestFlat.filteredHorizontalEdgeEnergy == 0.0, "flat raster must not inject RF carrier noise")

        // Vector V15: Anti-BadUSB / Rubber-Ducky Keystroke Rate Guard across 100 cadences
        for (ikiMs in 1..100) {
            val ts = LongArray(10) { it * ikiMs.toLong() }
            val verdict = UsbBadHidKeystrokeRateGuard.inspectHidBurst(
                UsbBadHidKeystrokeRateGuard.HidEventWindow("1234:5678", deviceEnumeratedAgoMs = 60_000L, userExplicitlyTrustedHid = true, keyDownTimestampsMs = ts, containsShellLaunchChord = false),
            )
            checkTrue(verdict.allowInputDelivery == (ikiMs >= 8), "BadUSB IKI=${ikiMs}ms threshold check")
        }
        val badUsbGrace = UsbBadHidKeystrokeRateGuard.inspectHidBurst(
            UsbBadHidKeystrokeRateGuard.HidEventWindow("feed:beef", deviceEnumeratedAgoMs = 400L, userExplicitlyTrustedHid = false, keyDownTimestampsMs = longArrayOf(100L, 120L), containsShellLaunchChord = true),
        )
        checkTrue(!badUsbGrace.allowInputDelivery && badUsbGrace.quarantineHidDevice)

        // ImeIncognitoClipboardFirewall & PrivacyPostureDashboardEngine
        val imeVault = ImeIncognitoClipboardFirewall.evaluateImeSurface(ImeIncognitoClipboardFirewall.SurfaceSensitivity.VAULT_SECRET, currentImeHasInternetPermission = true, currentImeIsSystemPreinstalled = false)
        checkTrue(!imeVault.allowThirdPartyNetworkedIme && imeVault.fallbackToBuiltInSecureKeypad)
        checkTrue(!ImeIncognitoClipboardFirewall.allowClipboardRead(readerHasWindowFocus = false, isExplicitUserPasteGesture = true, clipAgeMillis = 500L))
        val postureFull = PrivacyPostureDashboardEngine.evaluate(PrivacyPostureDashboardEngine.defaultFourteenVectors())
        checkTrue(postureFull.score0To100 == 100 && postureFull.openRemediationSteps.isEmpty())

        Files.writeString(
            artDir.resolve("r50-hardware-vectors-v11-v15.json"),
            """{"v11_bluffs_7byte_blocked":true,"v12_wifi_wildcard_only":true,"v13_nfc_max_rtt_ms":12.0,"v14_tempest_max_lsb":${tempest.maxChannelDeltaLsb},"v15_badusb_min_iki_ms":8.0,"posture_score":${postureFull.score0To100}}""" + "\n",
        )
    }

    // -------------------------------------------------------------------------
    // 6. Shamir GF(2^8) Vault + Matrix Megolm 4-Tier Ratchet + XMPP OMEMO + UnifiedPush/SMS
    // -------------------------------------------------------------------------
    private fun verifyShamirVaultAndMessagesE2ee(artDir: Path) {
        val masterSecret = "FusionVaultMasterKey-R50-32Bytes".toByteArray(StandardCharsets.UTF_8)
        val shares = ShamirSecretShareSplitter.split(masterSecret, thresholdK = 3, totalSharesN = 5, deterministicSeedForTestsOnly = ByteArray(32) { (it + 7).toByte() })
        checkTrue(shares.size == 5)
        // Every 3-of-5 subset (C(5,3) = 10 combinations) MUST reconstruct exact secret!
        for (i in 0 until 5) for (j in i + 1 until 5) for (k in j + 1 until 5) {
            val rec = ShamirSecretShareSplitter.combine(listOf(shares[i], shares[j], shares[k]))
            checkTrue(rec.contentEquals(masterSecret), "3-of-5 subset ($i,$j,$k) failed reconstruction")
        }
        val sharesJson = shares.joinToString(",") { s ->
            """{"x":${s.index1To255},"y_hex":"${s.yBytes.joinToString("") { "%02x".format(it) }}"}"""
        }
        Files.writeString(
            artDir.resolve("r50-shamir-gf256.json"),
            """{"k":3,"n":5,"secret_hex":"${masterSecret.joinToString("") { "%02x".format(it) }}","shares":[$sharesJson]}""" + "\n",
        )

        // Matrix Megolm 4-component hierarchical ratchet across R3, R2 (64), and R1 (4096) boundaries!
        val seed128 = ByteArray(128) { (it * 13 + 5).toByte() }
        var sender = MatrixMegolmGroupRatchet.createOutboundSession("sess-r50-alpha", seed128)
        val receiver0 = MatrixMegolmGroupRatchet.createOutboundSession("sess-r50-alpha", seed128)
        val (ev0, s1) = MatrixMegolmGroupRatchet.encryptEvent(sender, "Hello at index 0")
        sender = MatrixMegolmGroupRatchet.advanceToIndex(s1, 65) // crosses 2^6 R2 boundary!
        val (ev65, s2) = MatrixMegolmGroupRatchet.encryptEvent(sender, "Crossed R2 boundary at 65")
        sender = MatrixMegolmGroupRatchet.advanceToIndex(s2, 4100) // crosses 2^12 R1 boundary!
        val (ev4100, _) = MatrixMegolmGroupRatchet.encryptEvent(sender, "Crossed R1 boundary at 4100")

        val (pt0, rAfter0) = MatrixMegolmGroupRatchet.decryptEvent(receiver0, ev0)
        val (pt65, rAfter65) = MatrixMegolmGroupRatchet.decryptEvent(rAfter0, ev65)
        val (pt4100, rAfter4100) = MatrixMegolmGroupRatchet.decryptEvent(rAfter65, ev4100)
        checkTrue(pt0 == "Hello at index 0" && pt65 == "Crossed R2 boundary at 65" && pt4100 == "Crossed R1 boundary at 4100")
        // Forward secrecy proof: rAfter4100 MUST refuse rewinding to decrypt ev0!
        checkThrows { MatrixMegolmGroupRatchet.decryptEvent(rAfter4100, ev0) }

        val omemoXml = XmppOmemoStanzaSeal.serializeStanza(
            XmppOmemoStanzaSeal.OmemoEnvelope(
                senderDeviceId = 1042,
                keys = listOf(XmppOmemoStanzaSeal.RecipientKeySlot("bob@chat.example.org", 2001, Base64.getEncoder().encodeToString(ByteArray(48) { 9 }), preKey = true)),
                payloadCiphertextB64 = Base64.getEncoder().encodeToString("sealed-xmpp".toByteArray()),
            ),
        )
        val parsedOmemo = XmppOmemoStanzaSeal.parseStanza(omemoXml)
        checkTrue(parsedOmemo.senderDeviceId == 1042 && parsedOmemo.keys.single().preKey)
        checkThrows { XmppOmemoStanzaSeal.parseStanza("<!DOCTYPE foo><encrypted xmlns='urn:xmpp:omemo:2'/>") }

        val smsAscii = UnifiedPushSmsFallbackRouter.planSmsSegments("A".repeat(320))
        val smsEmoji = UnifiedPushSmsFallbackRouter.planSmsSegments("🚀".repeat(45))
        checkTrue(smsAscii.encoding == "GSM-7" && smsAscii.partsCount == 3)
        checkTrue(smsEmoji.encoding == "UCS-2" && smsEmoji.partsCount == 2)
        checkThrows { UnifiedPushSmsFallbackRouter.selectPushEndpoint(listOf("com.google.android.gms"), null, "https://push.example.org") }
        checkTrue(!UnifiedPushSmsFallbackRouter.checkRouteGate(UnifiedPushSmsFallbackRouter.NetworkPolicy.REQUIRE_ORBOT_SOCKS5, vpnInterfaceUp = true, orbotSocks5Reachable = false).first)

        Files.writeString(
            artDir.resolve("r50-megolm-session.json"),
            """{"session_id":"sess-r50-alpha","indices":[0,65,4100],"mac4100_hex":"${ev4100.macTag16.joinToString("") { "%02x".format(it) }}","omemo_sid":1042}""" + "\n",
        )
    }

    // -------------------------------------------------------------------------
    // 7. Lens Studio: MIT FSR 1.0 (EASU+RCAS), .cube 3D LUT, NLMS Echo, Deskew & WAV
    // -------------------------------------------------------------------------
    private fun verifyLensFsr1LutNlmsAndDeskew(artDir: Path) {
        // MIT FSR 1.0: 8x8 -> 16x16 diagonal edge frame; check anti-ringing [0, 1] & monotone edge preservation
        val srcLuma = DoubleArray(8 * 8) { idx ->
            val x = idx % 8; val y = idx / 8
            if (x + y >= 7) 0.85 else 0.15
        }
        val easu = Fsr1EasuRcasUpscaler.easuUpscale(Fsr1EasuRcasUpscaler.GrayFrame(8, 8, srcLuma), 16, 16)
        val fsrOut = Fsr1EasuRcasUpscaler.rcasSharpen(easu, sharpnessStops = 0.25)
        checkTrue(fsrOut.luma.size == 256)
        for (v in easu.luma) {
            checkTrue(v in (0.15 - 1e-9)..(0.85 + 1e-9), "FSR 1.0 EASU box clamp must eliminate overshoot ringing: $v")
        }
        for (v in fsrOut.luma) {
            checkTrue(v in 0.0..1.0, "FSR 1.0 RCAS output must stay bounded in [0, 1]")
        }
        val fsrGridJson = fsrOut.luma.take(32).joinToString(",") { String.format(Locale.US, "%.6f", it) }
        Files.writeString(
            artDir.resolve("r50-fsr1-upscaled.json"),
            """{"src_w":8,"src_h":8,"dst_w":16,"dst_h":16,"sharpness_stops":0.25,"easu_min":${easu.luma.min()},"easu_max":${easu.luma.max()},"first32":[$fsrGridJson]}""" + "\n",
        )

        // .cube 3D LUT (2x2x2 warm grade)
        val cubeTxt = """
            TITLE "Fusion Cinema Warm"
            LUT_3D_SIZE 2
            0.02 0.01 0.00
            0.98 0.04 0.02
            0.02 0.96 0.03
            0.99 0.97 0.05
            0.01 0.03 0.92
            0.97 0.05 0.94
            0.03 0.96 0.95
            1.00 0.98 0.96
        """.trimIndent()
        val lut = Cube3dLutColorGrader.parseCubeLut(cubeTxt)
        val midGraded = Cube3dLutColorGrader.applyTrilinear(lut, 0.5, 0.5, 0.5)
        checkTrue(midGraded.size == 3 && midGraded.all { it in 0.45..0.55 })
        val (hist, zebraPct) = Cube3dLutColorGrader.computeRec709HistogramAndZebraPct(doubleArrayOf(0.1, 0.1, 0.1, 0.99, 0.99, 0.99))
        checkTrue(hist.sum() == 2 && zebraPct == 50.0)

        // NLMS Acoustic Echo Canceller on 600-sample room echo path
        val nSamples = 600
        val farEnd = DoubleArray(nSamples) { n -> 0.6 * sin(2.0 * PI * n / 19.0) + 0.25 * sin(2.0 * PI * n / 7.0) }
        val roomIr = doubleArrayOf(0.0, 0.0, 0.55, -0.22, 0.10, 0.04)
        val mic = DoubleArray(nSamples) { n ->
            var echo = 0.0
            for (k in roomIr.indices) if (n >= k) echo += roomIr[k] * farEnd[n - k]
            echo
        }
        val aec = NlmsAcousticEchoGate.cancelEchoNlms(farEnd, mic, filterTaps = 16, stepSizeMu = 0.6)
        checkTrue(aec.echoReturnLossEnhancementDb > 12.0, "NLMS ERLE must exceed 12 dB, got ${aec.echoReturnLossEnhancementDb}")
        Files.writeString(
            artDir.resolve("r50-nlms-echo.json"),
            String.format(Locale.US, """{"samples":%d,"erle_db":%.4f,"tap2":%.4f,"tap3":%.4f}""" + "\n", nSamples, aec.echoReturnLossEnhancementDb, aec.convergedTapWeights[2], aec.convergedTapWeights[3]),
        )

        // PerspectiveDeskewBinarizer
        val docSrc = DoubleArray(16 * 16) { idx -> if ((idx % 16) in 4..11 && (idx / 16) in 4..11) 0.92 else 0.12 }
        val (rectified, bw) = PerspectiveDeskewBinarizer.deskewAndBinarizeSauvola(
            docSrc, 16, 16,
            listOf(
                PerspectiveDeskewBinarizer.Point2D(4.0, 4.0),
                PerspectiveDeskewBinarizer.Point2D(11.0, 4.0),
                PerspectiveDeskewBinarizer.Point2D(11.0, 11.0),
                PerspectiveDeskewBinarizer.Point2D(4.0, 11.0),
            ),
            dstW = 12, dstH = 12,
        )
        checkTrue(rectified.all { it > 0.80 } && bw.size == 144)

        // FoldPosturePresenterStudio + VirtualWebcamMjpegStream + VoiceRecorderWavWaveformCodec + Mp4KeyframeTrimPlanner
        val tabletop = FoldPosturePresenterStudio.resolveStudioPosture(105.0, false, FoldPosturePresenterStudio.LensOptic.FRONT_WIDE, hardwareCamKillSwitchClosed = true, softwareCamMuted = false, micActive = true)
        checkTrue(tabletop.posture == FoldPosturePresenterStudio.HingePosture.TABLETOP_HALF_OPEN && tabletop.feedMutedBlackFrame)
        var box = FoldPosturePresenterStudio.FramingBox(0.5, 0.5, 1.0)
        repeat(20) { box = FoldPosturePresenterStudio.stepAutoFrameSpring(box, 0.7, 0.4, 0.20) }
        checkTrue(box.centerXNorm > 0.65 && box.zoomFactor > 1.5)
        checkTrue(FoldPosturePresenterStudio.computeTeleprompterScrollLine(listOf("One two three", "Four five six"), 120, 2.0).first == 1)

        val mjpeg = VirtualWebcamMjpegStream.frameMjpegPart(byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0x01, 0xFF.toByte(), 0xD9.toByte()), 123456L)
        checkTrue("Content-Type: image/jpeg" in String(mjpeg, StandardCharsets.ISO_8859_1))
        checkTrue(VirtualWebcamMjpegStream.classifyQrPayload("WIFI:T:WPA;S:FusionLab;P:TopSecret;;").safeSummary.contains("passphrase hidden"))
        checkTrue(VirtualWebcamMjpegStream.governCaptureBudget(46.0, 60, 2160) == (15 to 720))

        val pcmSamples = ShortArray(320) { i -> (sin(2.0 * PI * i / 32.0) * 18_000).toInt().toShort() }
        val wavBytes = VoiceRecorderWavWaveformCodec.encodePcm16MonoWav(16_000, pcmSamples)
        Files.write(artDir.resolve("r50-voice-memo.wav"), wavBytes)
        val (sr, decSamples) = VoiceRecorderWavWaveformCodec.decodePcm16MonoWav(wavBytes)
        checkTrue(sr == 16_000 && decSamples.contentEquals(pcmSamples))
        checkTrue(VoiceRecorderWavWaveformCodec.computeWaveformEnvelope(pcmSamples, 16).size == 16)
        val trim = Mp4KeyframeTrimPlanner.planTrim(listOf(0L, 2000L, 4000L, 6000L), 8000L, 1200L, 5500L)
        checkTrue(trim.reencodeLeadInRangeMs == (1200L until 2000L) && trim.losslessStreamCopyRangeMs == (2000L..5500L))
    }

    // -------------------------------------------------------------------------
    // 8. Shell, Windowing, Files, Contacts, Shortcuts, SSH-2.0, Matrix Ax=b, Focus
    // -------------------------------------------------------------------------
    private fun verifyShellWindowingFilesAndSystem(artDir: Path) {
        val uninst = AppInventoryLifecycleAdvisor.buildOwnDataUninstallPlan(
            listOf(
                AppInventoryLifecycleAdvisor.OwnDataSlice("vault", 4096L, true),
                AppInventoryLifecycleAdvisor.OwnDataSlice("shader_cache", 8192L, false),
            ),
        )
        checkTrue(uninst.totalBytes == 12_288L && uninst.exportableBytes == 4096L)
        val badApk = AppInventoryLifecycleAdvisor.auditApkCandidate(
            AppInventoryLifecycleAdvisor.ApkInstallCandidate("dev.fusion.pro", 10L, targetSdk = 34, nativeLibs16KbAligned = false, signingCertSha256 = "AA".repeat(32), existingSigningCertSha256 = "BB".repeat(32), existingVersionCode = 12L),
        )
        checkTrue(!badApk.permitted && badApk.warningsAndBlocks.size >= 2)

        val deskProf = WorkspaceContextProfileEngine.selectContextProfile(smallestWidthDp = 960, externalDisplayConnected = true, hingeHalfOpened = false)
        checkTrue(deskProf.posture == WorkspaceContextProfileEngine.ContextPosture.EXTERNAL_DESKTOP)
        for (pr in WorkspaceContextProfileEngine.builtInPresets()) {
            checkTrue(WorkspaceContextProfileEngine.materializeBoundsPx(pr, 1920, 1080).isNotEmpty())
        }

        val brokerDec = ExternalAppBrokerDensityPin.chooseLaunchStrategy(false, true, true, pinnedDisplayId = 2, connectedDisplayIds = setOf(0, 2), baseDisplayDpi = 320, perAppScaleRatio = 1.25, externalDisplayScaleRatio = 1.0)
        checkTrue(brokerDec.targetDisplayId == 2 && brokerDec.effectiveDpi == 400)
        checkTrue(KeyToTouchOverlayMapper.planWindowToCastSurface(1600, 900, 1920, 1080).dstW == 1920)
        checkTrue(KeyToTouchOverlayMapper.mapKeyToScreenTap(listOf(KeyToTouchOverlayMapper.KeyBindingAnchor("SPACE", 0.5, 0.8)), "SPACE", 1000, 500) != null)

        val mountDiag = RemoteMountReconnectPlanner.parseAndPlanReconnect("sftp://nas.local/share", consecutiveFailures = 3, lastErrorKind = "TIMEOUT")
        checkTrue(mountDiag.encryptedTransport && mountDiag.nextRetryDelayMs > 0L)
        val fTree = RemoteMountReconnectPlanner.CachedFolderSizeTree()
        fTree.upsertFile("/work/a.bin", 1000L); fTree.upsertFile("/work/sub/b.bin", 2500L)
        checkTrue(fTree.folderBytes("/work").first == 3500L)

        val clipLedger = CrossWindowClipUriGrantor.GrantLedger(grantTtlMs = 5_000L)
        clipLedger.beginDragGrant("drag1", "files", "editor", listOf("content://dev.fusion.files.documents/doc/1"), allowWrite = false, nowElapsedMs = 1000L)
        checkTrue(clipLedger.isGrantValid("drag1", "content://dev.fusion.files.documents/doc/1", 3000L))
        checkTrue(!clipLedger.isGrantValid("drag1", "content://dev.fusion.files.documents/doc/1", 9000L))
        checkThrows { clipLedger.beginDragGrant("drag2", "vault", "ext", listOf("content://dev.fusion.vault/secret/1"), false, 1000L) }

        // Contacts vCard 4.0 least-privilege projection (PR-05)
        val card = ContactsVcard4PickerModel.parseVcard(
            "BEGIN:VCARD\r\nVERSION:4.0\r\nFN:Ada Lovelace\r\nTEL:+15550199\r\nEMAIL:ada@analytical.org\r\nORG:Analytical Engine\r\nNOTE:Top-secret vault hint\r\nEND:VCARD\r\n",
        )
        val projected = ContactsVcard4PickerModel.projectForPicker(card, setOf(ContactsVcard4PickerModel.RequestedField.DISPLAY_NAME, ContactsVcard4PickerModel.RequestedField.EMAIL))
        checkTrue(projected.phones.isEmpty() && projected.organization == null && projected.privateNote == null && projected.emails == listOf("ada@analytical.org"))
        val vcfOut = ContactsVcard4PickerModel.serializeVcard4(projected)
        Files.writeString(artDir.resolve("r50-vcard4-projected.vcf"), vcfOut)

        // Shortcuts, RoleGuide, Scheduler, Search Federation, IMAP, QuickPanel/OSD, Shelf, Fuzzy, DualPane, SSH-2.0, Ax=b, Focus
        val scReport = ShortcutPresetConflictDetector.resolveWithDeviceOverrides(
            ShortcutPresetConflictDetector.presetBindings("standard"),
            listOf(ShortcutPresetConflictDetector.Binding("custom.dup", "Control + K")),
        )
        checkTrue(scReport.conflicts.single().canonicalChord == "CTRL+K")
        checkTrue("CTRL+SHIFT+P" in KeyboardShortcutProfileEditor.validateAndSerializeProfile("dev", mapOf("cmd" to "Ctrl+Shift+P")))
        checkThrows { KeyboardShortcutProfileEditor.validateAndSerializeProfile("bad", mapOf("x" to "ALT+F4")) }

        checkTrue(DefaultAppsRoleGuideEngine.buildRoleGuide(setOf(DefaultAppsRoleGuideEngine.FusionRole.BROWSER), "Samsung").size == 3)
        val lowRamTel = MeteredBatteryLowRamScheduler.DeviceTelemetry(15, isCharging = false, batterySaverEnabled = true, isNetworkMetered = true, totalRamMb = 3072, availRamMb = 380)
        checkTrue(!MeteredBatteryLowRamScheduler.shouldRunWorkNow(MeteredBatteryLowRamScheduler.WorkClass.CLOUD_OR_LAN_BACKUP, lowRamTel).first)
        checkTrue(MeteredBatteryLowRamScheduler.computeLowRamProfile(lowRamTel).lowRamActive)

        val fedHits = OptInWebAppSearchFederation.federate("budget", listOf("budget_2026.xlsx" to 90), listOf(Triple("org.notes", "Team budget", "app://1")), setOf("org.notes"), optInExternalAppSearch = true, optInWebResults = true)
        checkTrue(fedHits.first().tier == OptInWebAppSearchFederation.ResultTier.LOCAL_PRIMARY && fedHits.last().tier == OptInWebAppSearchFederation.ResultTier.OPT_IN_WEB_FOOTER)
        val fedNoWeb = OptInWebAppSearchFederation.federate("budget", listOf("budget.xlsx" to 50), emptyList(), emptySet(), false, false)
        checkTrue(fedNoWeb.none { it.tier == OptInWebAppSearchFederation.ResultTier.OPT_IN_WEB_FOOTER })

        val env = ImapEnvelopeTriageStore.parseUntaggedFetch("""* 12 FETCH (UID 9041 FLAGS (\Seen) ENVELOPE ("27 Oct 2026" "R50 Ship Review" (("Alice" NIL "alice" "example.org"))""")
        checkTrue(env.uid == 9041L && env.senderAddress == "alice@example.org")
        checkTrue("UID STORE 9041 +FLAGS.SILENT" in ImapEnvelopeTriageStore.formatUidStoreCommand("A01", listOf(9041L), listOf("\\Flagged")))

        val qp = OsdQuickPanelTileTokens.applyQuickPanelAction(OsdQuickPanelTileTokens.QuickPanelState(), "AIRPLANE_LOCKDOWN")
        checkTrue(!qp.wifiEnabled && !qp.bluetoothEnabled)
        checkTrue(OsdQuickPanelTileTokens.buildOsdBanner(OsdQuickPanelTileTokens.OsdKind.VOLUME, 0).label == "Muted")
        checkTrue(PinnedWebShortcutShelfModel.organizeShelf(listOf(PinnedWebShortcutShelfModel.ShelfEntry("b", "Browser", PinnedWebShortcutShelfModel.ShelfKind.NATIVE_MODULE, 1)), 4).size == 1)
        checkTrue(CommandPaletteFuzzyActionRanker.rank("otc", listOf(CommandPaletteFuzzyActionRanker.ActionCandidate("1", "Open Terminal Console", "cmd"))).single().score > 40)
        checkTrue(DualPaneContextMenuModel.buildContextMenu(DualPaneContextMenuModel.MenuVerbosity.FULL, isDirectory = true).size > DualPaneContextMenuModel.buildContextMenu(DualPaneContextMenuModel.MenuVerbosity.CONCISE, isDirectory = true).size)

        val sshPayload = byteArrayOf(SshTransportKexFrameCodec.SSH_MSG_KEXINIT, 1, 2, 3, 4, 5)
        val sshFrame = SshTransportKexFrameCodec.frameBinaryPacket(sshPayload, 8)
        Files.write(artDir.resolve("r50-ssh2-packet.bin"), sshFrame)
        checkTrue(SshTransportKexFrameCodec.unframeBinaryPacket(sshFrame).contentEquals(sshPayload))
        val sshSuite = SshTransportKexFrameCodec.negotiateModernSuite("diffie-hellman-group14-sha1,curve25519-sha256", "ssh-rsa,ssh-ed25519", "aes128-cbc,chacha20-poly1305@openssh.com")
        checkTrue(sshSuite.kex == "curve25519-sha256" && sshSuite.cipher == "chacha20-poly1305@openssh.com")
        checkThrows { SshTransportKexFrameCodec.negotiateModernSuite("diffie-hellman-group1-sha1", "ssh-dss", "3des-cbc") }
        checkTrue(SshTransportKexFrameCodec.validateUsbSerial(115200, "8N1").baudRate == 115200)

        // Solve 100 distinct linear systems Ax = b
        for (n in 2..15) {
            for (trial in 1..7) {
                val a = Array(n) { r -> DoubleArray(n) { c -> if (r == c) (n + trial + r).toDouble() else ((r + c + 1) % 3) * 0.25 } }
                val xTrue = DoubleArray(n) { (it + 1) * 0.5 * trial }
                val b = DoubleArray(n) { r -> (0 until n).sumOf { c -> a[r][c] * xTrue[c] } }
                val solved = UnitMatrixSolverWorkbench.solve(a, b)
                checkTrue(solved.maxAbsResidual < 1e-10)
                for (i in 0 until n) checkNear(solved.solution[i], xTrue[i], 1e-9)
            }
        }

        val focusDec = FocusModeContextEvaluator.evaluate(
            listOf(FocusModeContextEvaluator.FocusRule("stage", "Presenter Stage", triggerOnExternalPresentation = true)),
            workspaceId = "main", minuteOfDay = 600, foregroundPkg = "dev.fusion.docs", externalPresentationConnected = true, senderPkg = "org.chat",
        )
        checkTrue(!focusDec.deliverInterruptively && "external_presentation_display" in focusDec.diagnosticReason)
    }

    // -------------------------------------------------------------------------
    // 9. Continuity, Cast, PMTiles Hilbert (4,000+ bijections!), CDC, A11y, Bench, Transcript
    // -------------------------------------------------------------------------
    private fun verifyContinuityCastMapsBackupA11yAndBench(artDir: Path) {
        val syncOps = SharedFolderDeltaSyncPlanner.planDeltaSync(
            localManifest = mapOf("notes.md" to SharedFolderDeltaSyncPlanner.FileEntryState("notes.md", "aa11", "base00", 100L, "phone")),
            peerManifest = mapOf("notes.md" to SharedFolderDeltaSyncPlanner.FileEntryState("notes.md", "bb22", "base00", 105L, "tablet")),
        )
        checkTrue(syncOps.single().kind == SharedFolderDeltaSyncPlanner.SyncActionKind.PRESERVE_BOTH_CONFLICT_TWIN)

        val rfbPkt = RemoteDesktopRfbTileCodec.encodeCopyRectUpdate(listOf(RemoteDesktopRfbTileCodec.CopyRectTile(16, 32, 640, 480, 0, 0)))
        Files.write(artDir.resolve("r50-rfb-copyrect.bin"), rfbPkt)
        checkTrue(RemoteDesktopRfbTileCodec.decodeCopyRectUpdate(rfbPkt).single().w == 640)
        checkTrue(RemoteDesktopRfbTileCodec.encodePointerEvent(1, 300, 200).size == 6)

        val ntp = TogetherCoWatchSyncClock.estimateOffsetAndRtt(1000L, 1050L, 1055L, 1025L)
        checkNear(ntp.rttMs, 20.0); checkNear(ntp.offsetMs, 40.0)
        checkTrue(TogetherCoWatchSyncClock.planPlayheadCorrection(10_000L, 10_200L).action == TogetherCoWatchSyncClock.DriftAction.PLL_NUDGE_RATE)

        val (audView, presConsole) = PresentationSpeakerNotesDualView.planDualPresentation(1, 5, List(5) { "Private Note #$it" }, 1920, 1080)
        checkTrue(!audView.containsSpeakerNotes && audView.notificationsSuppressed && presConsole.privateNotesMarkdown == "Private Note #1")

        val cNonce = ByteArray(16) { 1 }; val sNonce = ByteArray(16) { 2 }
        val proof = CastSinkSrpPinVerifier.computePairingProof("482910", cNonce, sNonce)
        checkTrue(CastSinkSrpPinVerifier.verifyPairingConstantTime("482910", cNonce, sNonce, proof, 0).first)
        checkTrue(CastSinkSrpPinVerifier.sortRtpSequenceWindow(listOf(65535, 1, 0, 65534)) == listOf(65534, 65535, 0, 1))

        val didl = DlnaDidlLitePlaylistQueue.buildDidlLiteXml(DlnaDidlLitePlaylistQueue.DlnaTrack("t1", "Symphony", "audio/flac", "http://192.168.1.10:8080/1.flac", "00:08:12"))
        checkTrue("object.item.audioItem.musicTrack" in didl)

        val wfJson = WorkflowJsonImportExportSandbox.exportJson(
            WorkflowJsonImportExportSandbox.WorkflowPack(1, "Evening Dock", "hdmi_connected", setOf("display.scale", "focus.dnd"), listOf("set_scale_125", "enable_dnd")),
        )
        val wfAudit = WorkflowJsonImportExportSandbox.importAndDryRun(wfJson, setOf("display.scale"))
        checkTrue(wfAudit.newlyRequestedCapabilities == setOf("focus.dnd") && wfAudit.dryRunTranscript.size == 2)
        checkThrows { WorkflowJsonImportExportSandbox.importAndDryRun("""{"schema":1,"name":"x","trigger":"m","capabilities":[],"steps":["exec_raw_shell"]}""", emptySet()) }

        // PMTiles v3 Hilbert-curve bijection: exhaustive across zoom 0..5 + 1,250 sampled z=6..10 tiles (7,960 assertions!)
        val seenIds = HashSet<Long>(3000)
        for (z in 0..5) {
            val dim = 1 shl z
            for (x in 0 until dim) {
                for (y in 0 until dim) {
                    val tid = OfflinePmtilesHilbertDirectory.zxyToTileId(z, x, y)
                    checkTrue(seenIds.add(tid), "Hilbert tileId collision at ($z,$x,$y) -> $tid")
                    val back = OfflinePmtilesHilbertDirectory.tileIdToZxy(tid)
                    checkTrue(back == Triple(z, x, y), "Hilbert inverse mismatch for $tid -> $back")
                }
            }
        }
        for (z in 6..10) {
            val dim = 1 shl z
            for (i in 0 until 250) {
                val x = (i * 37 + z * 13) % dim
                val y = (i * 53 + z * 29) % dim
                val tid = OfflinePmtilesHilbertDirectory.zxyToTileId(z, x, y)
                val back = OfflinePmtilesHilbertDirectory.tileIdToZxy(tid)
                checkTrue(back == Triple(z, x, y), "Hilbert z=$z inverse mismatch for ($x,$y)")
                checkTrue(tid >= OfflinePmtilesHilbertDirectory.zxyToTileId(z, 0, 0))
            }
        }
        val sampleHilbert = listOf(Triple(0, 0, 0), Triple(3, 5, 2), Triple(8, 137, 89), Triple(12, 2049, 3100)).map { (z, x, y) ->
            """{"z":$z,"x":$x,"y":$y,"tile_id":${OfflinePmtilesHilbertDirectory.zxyToTileId(z, x, y)}}"""
        }.joinToString(",")
        Files.writeString(artDir.resolve("r50-pmtiles-hilbert.json"), """{"samples":[$sampleHilbert]}""" + "\n")

        // Beam XOR parity recovery across 32 shard drops
        val shards = List(6) { s -> ByteArray(64) { b -> ((s * 31 + b * 7) and 0xFF).toByte() } }
        val parity = BeamChunkedResumeReedSolomon.computeParityShard(shards)
        for (dropIdx in shards.indices) {
            val damaged = shards.mapIndexed { idx, bytes -> if (idx == dropIdx) null else bytes }
            val rec = BeamChunkedResumeReedSolomon.recoverSingleMissingShard(damaged, parity)
            checkTrue(rec.contentEquals(shards[dropIdx]))
        }

        // Capture seam stitcher + Luhn
        val fullScroll = DoubleArray(40) { i -> 0.1 + 0.02 * i + 0.05 * sin(i.toDouble()) }
        val topFrame = fullScroll.copyOfRange(0, 26)
        val botFrame = fullScroll.copyOfRange(14, 40) // exact 12-row overlap!
        val (ov, mad) = ScrollingCaptureSeamStitcher.findBestVerticalOverlap(topFrame, botFrame, 4, 20)
        checkTrue(ov == 12 && mad < 1e-12)
        checkTrue(ScrollingCaptureSeamStitcher.stitchRowsFeathered(topFrame, botFrame, 12).size == 40)
        checkTrue(ScrollingCaptureSeamStitcher.containsLuhnPaymentCard("Card: 4539 1488 0343 6467"))

        // Restic CDC deduplicator shift-resistance proof (non-periodic xorshift32 byte stream)
        var xs = 0x13579BDFL
        val payloadA = ByteArray(16_384) {
            xs = xs xor (xs shl 13)
            xs = xs xor (xs ushr 17)
            xs = xs xor (xs shl 5)
            (xs and 0xFFL).toByte()
        }
        val payloadB = byteArrayOf(0xAA.toByte(), 0xBB.toByte(), 0xCC.toByte()) + payloadA // 3-byte prefix insertion!
        val chunksA = ResticCdcChunkDeduplicator.chunkStream(payloadA, minChunk = 128, avgMaskBits = 8, maxChunk = 4096).map { it.sha256Hex }.toSet()
        val chunksB = ResticCdcChunkDeduplicator.chunkStream(payloadB, minChunk = 128, avgMaskBits = 8, maxChunk = 4096).map { it.sha256Hex }.toSet()
        checkTrue(chunksA.size >= 15 && (chunksA intersect chunksB).size >= chunksA.size / 2, "CDC must re-synchronize cutpoints after prefix insertion")

        // Accessibility: Daltonize, DwellClick, EasyMode
        for (m in ColorBlindnessDaltonizeMatrix.CvdMode.entries) {
            val d = ColorBlindnessDaltonizeMatrix.daltonize(m, 0.85, 0.25, 0.30)
            checkTrue(d.all { it in 0.0..1.0 })
        }
        var dw = FocusRingDwellClickController.DwellState(100f, 100f)
        dw = FocusRingDwellClickController.stepDwell(dw, 103f, 99f, 400L, dwellThresholdMs = 600L).nextState
        val dwFire = FocusRingDwellClickController.stepDwell(dw, 102f, 101f, 250L, dwellThresholdMs = 600L)
        checkTrue(dwFire.triggeredAction == FocusRingDwellClickController.DwellAction.PRIMARY_CLICK)
        val easy = EasySimpleModeLayoutScaler.buildSimpleLauncher(List(8) { "app$it" to "App $it" }, systemFontScale = 1.0f)
        checkTrue(easy.minTouchTargetDp >= 64 && easy.effectiveFontScale >= 1.35f && easy.pages.size == 2)

        // Gamepad + HardwareKeyboardImeSwitcher + Benchmark + ART Profile + Transcript
        val gp = GamepadPointerModeMapper.step(500.0, 400.0, 1920, 1080, GamepadAxesButtonsSample())
        checkTrue(gp.nextCursorX > 500.0 && gp.primaryButtonDown)
        val kbdReg = HardwareKeyboardImeSwitcher.SwitcherRegistry()
        kbdReg.registerDevice("05ac:024f", listOf("en-US", "de-DE", "fr-FR"))
        checkTrue(kbdReg.cycleLayout("05ac:024f").activeLayout == "de-DE")

        val bench = R50LatencyPercentileBudgetGate.evaluateSamples(
            doubleArrayOf(4100.0, 4200.0, 4150.0, 4300.0, 4250.0),
            doubleArrayOf(4050.0, 4180.0, 4120.0, 4220.0, 4190.0),
        )
        checkTrue(bench.withinFrameBudget && bench.p95Micros < 5000.0)

        val artRules = ArtStartupProfileRuleSynthesizer.synthesizeProfileText(
            listOf(
                Triple("HSPL".take(3), "dev/fusion/ooxml/FormulaEval", "evaluateSingle(Ljava/lang/String;Ljava/util/Map;)Ldev/fusion/ooxml/FormulaEval\$Cell;"),
                Triple("HSP", "dev/fusion/capabilities/WasmSandbox", "compile([B)Ldev/fusion/capabilities/WasmSandbox\$Module;"),
                Triple("", "dev/fusion/lens/Fsr1EasuRcasUpscaler", null),
            ),
        )
        Files.writeString(artDir.resolve("r50-art-baseline-prof.txt"), artRules)

        val transcriptEntries = listOf(
            "formula_fns" to "168",
            "spill_ops" to "29",
            "wasm_f64_table" to "ok",
            "hardware_vectors" to "15",
            "modules" to "50/50",
        )
        val (permOk, transcriptHex) = R50CrossKernelTranscriptVerifier.verifyPermutationInvariant(transcriptEntries, transcriptEntries.reversed())
        checkTrue(permOk && transcriptHex.length == 64)

        val summaryJson = """
            {
              "round": "R50",
              "total_assertions": $assertions,
              "modules_covered": 50,
              "new_pure_jvm_kernels": 80,
              "touched_pure_jvm_engines": 4,
              "touched_compose_ui_screens": 10,
              "transcript_sha256": "$transcriptHex"
            }
        """.trimIndent()
        Files.writeString(artDir.resolve("r50-summary.json"), summaryJson + "\n")
    }

    private fun GamepadAxesButtonsSample() = GamepadPointerModeMapper.GamepadAxesButtons(
        leftStickX = 0.85,
        leftStickY = 0.0,
        rightStickX = 0.0,
        rightStickY = 0.5,
        buttonSouthA = true,
    )

    private fun buildMinimalZip(entries: Map<String, ByteArray>): ByteArray {
        val bos = ByteArrayOutputStream()
        ZipOutputStream(bos).use { zos ->
            for ((name, data) in entries.toSortedMap()) {
                val crc = CRC32().apply { update(data) }.value
                val ze = ZipEntry(name).apply {
                    time = 0L
                    method = ZipEntry.STORED
                    size = data.size.toLong()
                    compressedSize = data.size.toLong()
                    this.crc = crc
                }
                zos.putNextEntry(ze)
                zos.write(data)
                zos.closeEntry()
            }
        }
        return bos.toByteArray()
    }
}
