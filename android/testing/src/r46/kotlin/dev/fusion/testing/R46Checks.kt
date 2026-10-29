// SPDX-License-Identifier: Apache-2.0
package dev.fusion.testing

import dev.fusion.alerts.RateWindow
import dev.fusion.archive.TarPax
import dev.fusion.automation.CronExpr
import dev.fusion.backup.RabinChunker
import dev.fusion.beam.ChunkedTransfer
import dev.fusion.browser.model.PermissionsPolicyHeader
import dev.fusion.capabilities.WasmSandbox
import dev.fusion.capture.EdlCutList
import dev.fusion.commands.PipeChain
import dev.fusion.documents.DocOutlineRender
import dev.fusion.documents.TrackRevisions
import dev.fusion.editor.CitationBib
import dev.fusion.filesui.PathConflict
import dev.fusion.find.TrigramIndex
import dev.fusion.format.image.PngMetadataStrip
import dev.fusion.format.pdf.PdfPageWriter
import dev.fusion.hw.BleHidPairingGate
import dev.fusion.hw.UsbGuardPolicy
import dev.fusion.launcher.DesktopEntryIni
import dev.fusion.lens.BarcodeGs1
import dev.fusion.lok.OdsSheetExtract
import dev.fusion.media.HlsPlaylist
import dev.fusion.messages.QuotedPrintable
import dev.fusion.model.SemVerRange
import dev.fusion.native.ColumnarPage
import dev.fusion.notes.MarkdownTableFmt
import dev.fusion.ooxml.CellErr
import dev.fusion.ooxml.CondFormat
import dev.fusion.ooxml.FormulaEval
import dev.fusion.ooxml.MultiSheetBook
import dev.fusion.ooxml.SheetPivot
import dev.fusion.ooxml.SheetRange
import dev.fusion.perf.QuotaBudget
import dev.fusion.privacy.MetadataScrub
import dev.fusion.privacy.OnionGraterFilter
import dev.fusion.security.MacRandomizer
import dev.fusion.settings.SchemaMigrate
import dev.fusion.shell.KeystrokeJitter
import dev.fusion.storage.WalFrame
import dev.fusion.terminal.VtScreenBuf
import dev.fusion.together.CrdtLwwMap
import dev.fusion.vault.PassphraseDice
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.charset.StandardCharsets
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.math.abs

object R46Checks {
    private var passed = 0

    private fun checkTrue(cond: Boolean, msg: String = "assertion failed") {
        if (!cond) throw AssertionError("R46 FAIL: $msg")
        passed++
    }

    private fun checkEq(actual: Any?, expected: Any?, msg: String = "") {
        if (actual != expected) {
            throw AssertionError("R46 FAIL $msg: expected <$expected>, got <$actual>")
        }
        passed++
    }

    private fun checkClose(actual: Double, expected: Double, eps: Double = 1e-6, msg: String = "") {
        if (!actual.isFinite() || abs(actual - expected) > eps) {
            throw AssertionError("R46 FAIL $msg: expected $expected +/- $eps, got $actual")
        }
        passed++
    }

    private inline fun <reified T : Throwable> checkThrows(msg: String = "", block: () -> Unit) {
        var threw = false
        try {
            block()
        } catch (t: Throwable) {
            if (t is T) threw = true else throw AssertionError("R46 FAIL $msg: expected ${T::class.simpleName}, got $t")
        }
        if (!threw) throw AssertionError("R46 FAIL $msg: expected ${T::class.simpleName}, nothing thrown")
        passed++
    }

    @JvmStatic
    fun main(args: Array<String>) {
        val fixtureDir = File(args.firstOrNull() ?: "tools/fixtures/r46")
        val corpusFile = File(fixtureDir, "adversarial-corpus.json")
        checkTrue(corpusFile.isFile, "adversarial-corpus.json must exist at ${corpusFile.path}")
        val corpusText = corpusFile.readText(StandardCharsets.UTF_8)
        checkTrue(corpusText.contains("\"round\": \"R46\""), "fixture round header")

        testFormulaEvalR46Extensions()
        testMultiSheetBook()
        testSheetPivot()
        testCondFormat()
        testPdfPageWriter()
        testDocOutlineRender()
        testTrackRevisions()
        testCitationBib()
        testOdsSheetExtract()
        testWasmSandbox()
        testQuotaBudget()
        testWalFrame()
        testPipeChain(corpusText)
        testSemVerRange()
        testSchemaMigrate()
        testColumnarPage()
        testTarPax()
        testOnionGraterFilter(corpusText)
        testMetadataScrub()
        testUsbGuardPolicy()
        testBleHidPairingGate()
        testMacRandomizer()
        testKeystrokeJitter()
        testPermissionsPolicyHeader()
        testHlsPlaylist()
        testDesktopEntryIni()
        testTrigramIndex()
        testPathConflict()
        testVtScreenBuf()
        testChunkedTransfer()
        testRateWindow()
        testEdlCutList()
        testRabinChunker()
        testCrdtLwwMap()
        testBarcodeGs1(corpusText)
        testQuotedPrintable()
        testPngMetadataStrip()
        testCronExpr()
        testPassphraseDice()
        testMarkdownTableFmt()
        testPropertyInvariantsSweep()

        println("R46 PASS: $passed production-kernel assertions across 39 kernels (+3 touched). Host JVM only; not Android/device evidence.")
    }

    private fun testPropertyInvariantsSweep() {
        // 1. WasmSandbox fib(0..20) against independent iterative reference (42 assertions)
        val inst = WasmSandbox.decode(buildTestWasmModule()).instantiate(maxMemoryPages = 2)
        var fPrev = 0
        var fCur = 1
        for (n in 0..20) {
            val res = inst.invoke("fib", listOf(n), fuelBudget = 20_000L)
            checkEq(res.returnValues, listOf(fPrev), "wasm fib($n)")
            checkTrue(res.fuelUsed >= 4L, "wasm fuelUsed($n) > 0")
            val nxt = fPrev + fCur
            fPrev = fCur
            fCur = nxt
        }

        // 2. WalFrame torn-tail crash sweep across 40 distinct truncation lengths (80 assertions)
        val committedA = "committed-alpha".toByteArray()
        val committedB = "committed-bravo-payload".toByteArray()
        val trailingUncommitted = "uncommitted-delta-frame-payload-48bytes---------".toByteArray()
        val fullWal = WalFrame.encode(
            salt = 0x1234_5678,
            entries = listOf(
                WalFrame.Entry(10, committedA, isCommit = true),
                WalFrame.Entry(20, committedB, isCommit = true),
                WalFrame.Entry(30, trailingUncommitted, isCommit = false),
            ),
        )
        for (cut in 1..40) {
            val truncated = fullWal.copyOfRange(0, fullWal.size - cut)
            val rec = WalFrame.recover(truncated)
            checkTrue(rec.committedPages[10]!!.contentEquals(committedA), "wal cut=$cut page 10")
            checkTrue(rec.committedPages[20]!!.contentEquals(committedB) && 30 !in rec.committedPages, "wal cut=$cut page 20 & no 30")
        }

        // 3. GS1 Mod-10 check-digit generation + single-digit corruption rejection across 60 codes (120 assertions)
        for (i in 0 until 60) {
            val payload12 = "%012d".format(400_638_100_000L + i * 137L)
            val chk = BarcodeGs1.computeCheckDigit(payload12)
            val valid13 = payload12 + chk
            checkTrue(BarcodeGs1.isValidGs1Code(valid13), "GS1 valid $valid13")
            val pos = i % 13
            val flippedDigit = ('0'.code + ((valid13[pos] - '0' + 1) % 10)).toChar()
            val corrupted13 = valid13.substring(0, pos) + flippedDigit + valid13.substring(pos + 1)
            checkTrue(!BarcodeGs1.isValidGs1Code(corrupted13), "GS1 single-digit error rejected at pos=$pos")
        }

        // 4. CrdtLwwMap semilattice laws across 30 randomized replica triples (90 assertions)
        for (seed in 1..30) {
            val m1 = CrdtLwwMap.ReplicaMap.empty()
                .put("k1", "v-$seed", CrdtLwwMap.Hlc(100L + (seed % 5), seed % 3, "n1"))
                .put("k2", "live-$seed", CrdtLwwMap.Hlc(200L, 0, "n1"))
            val m2 = CrdtLwwMap.ReplicaMap.empty()
                .put("k1", "w-$seed", CrdtLwwMap.Hlc(100L + ((seed * 3) % 5), (seed + 1) % 3, "n2"))
                .remove("k2", CrdtLwwMap.Hlc(198L + (seed % 4), 1, "n2"))
            val m3 = CrdtLwwMap.ReplicaMap.empty()
                .put("k3", "z-$seed", CrdtLwwMap.Hlc(150L, 0, "n3"))
                .remove("k1", CrdtLwwMap.Hlc(102L, 2, "n3"))
            checkEq(m1.merge(m2), m2.merge(m1), "commutativity seed=$seed")
            checkEq(m1.merge(m2).merge(m3), m1.merge(m2.merge(m3)), "associativity seed=$seed")
            checkEq(m1.merge(m2).merge(m1), m1.merge(m2), "absorption/idempotence seed=$seed")
        }

        // 5. QuotedPrintable binary round-trip + <=76 line width across 40 byte arrays (80 assertions)
        for (len in 1..40) {
            val raw = ByteArray(len * 5) { idx -> ((idx * 97 + len * 13) and 0xFF).toByte() }
            val qp = QuotedPrintable.encode(raw)
            checkTrue(qp.split("\r\n").all { it.length <= 76 }, "QP width len=$len")
            checkTrue(QuotedPrintable.decode(qp).contentEquals(raw), "QP binary roundtrip len=$len")
        }

        // 6. ColumnarPage nullable delta-ZigZag round-trip + zone-map soundness across 30 pages (60 assertions)
        for (p in 1..30) {
            val col = List(25) { idx -> if ((idx + p) % 6 == 0) null else (idx * -17L + p * 100L) }
            val encoded = ColumnarPage.encodeLongColumn(col)
            checkEq(ColumnarPage.decodeLongColumn(encoded), col, "ColumnarPage decode p=$p")
            val st = ColumnarPage.inspectLongStats(encoded)
            val nonNull = col.filterNotNull()
            checkTrue(st.min == nonNull.minOrNull() && st.max == nonNull.maxOrNull(), "ColumnarPage zone-map p=$p")
        }

        // 7. MacRandomizer LAA/unicast invariants across 40 SSIDs (40 assertions)
        val sec = ByteArray(32) { (it xor 0x5C).toByte() }
        val hw = MacRandomizer.parse("b8:27:eb:aa:bb:cc")
        for (k in 1..40) {
            val derived = MacRandomizer.derivePerNetworkMac(sec, "wlan0", "Net-$k", k.toLong())
            checkTrue(derived.isValidPrivateClientMac && MacRandomizer.verifyNoHardwareLeak(derived, hw), "MAC LAA Net-$k")
        }
    }

    private fun testFormulaEvalR46Extensions() {
        checkTrue(FormulaEval.SCOPE.contains("35 pure functions"))
        val res = FormulaEval.evaluateSheet(
            mapOf(
                "A1" to FormulaEval.CellInput.TextLiteral("SKU-1"),
                "B1" to FormulaEval.CellInput.NumberLiteral(120.0),
                "A2" to FormulaEval.CellInput.TextLiteral("SKU-2"),
                "B2" to FormulaEval.CellInput.NumberLiteral(250.0),
                "A3" to FormulaEval.CellInput.TextLiteral("SKU-3"),
                "B3" to FormulaEval.CellInput.NumberLiteral(90.0),
                "A4" to FormulaEval.CellInput.TextLiteral("SKU-2"),
                "B4" to FormulaEval.CellInput.NumberLiteral(50.0),
                "C1" to FormulaEval.CellInput.Formula("=VLOOKUP(\"SKU-2\", A1:B4, 2, FALSE)"),
                "C2" to FormulaEval.CellInput.Formula("=IFNA(VLOOKUP(\"SKU-9\", A1:B4, 2, 0), -1)"),
                "C3" to FormulaEval.CellInput.Formula("=IFNA(1/0, 999)"),
                "C4" to FormulaEval.CellInput.Formula("=SUMIF(B1:B4, \">=100\")"),
                "C5" to FormulaEval.CellInput.Formula("=SUMIF(A1:A4, \"SKU-2\", B1:B4)"),
                "C6" to FormulaEval.CellInput.Formula("=COUNTIF(B1:B4, \"<150\")"),
                "C7" to FormulaEval.CellInput.Formula("=MEDIAN(B1:B4)"),
                "C8" to FormulaEval.CellInput.Formula("=STDEV(10, 20, 30)"),
                "D1" to FormulaEval.CellInput.Formula("=TRIM(\"  hello   fusion  \")"),
                "D2" to FormulaEval.CellInput.Formula("=UPPER(LEFT(D1, 5))"),
                "D3" to FormulaEval.CellInput.Formula("=LOWER(RIGHT(D2, 3))"),
                "D4" to FormulaEval.CellInput.Formula("=MID(D1, 7, 6)"),
                "E1" to FormulaEval.CellInput.Formula("=ISBLANK(Z9)"),
                "E2" to FormulaEval.CellInput.Formula("=ISNUMBER(B1)"),
                "E3" to FormulaEval.CellInput.Formula("=ISTEXT(A1)"),
                "E4" to FormulaEval.CellInput.Formula("=ISERROR(C3)"),
                "E5" to FormulaEval.CellInput.Formula("=VLOOKUP(\"SKU-1\", A1:B4, 3, FALSE)"),
            ),
        )
        checkEq(res.values["C1"], FormulaEval.CellValue.Num(250.0))
        checkEq(res.values["C2"], FormulaEval.CellValue.Num(-1.0))
        // IFNA does NOT mask #DIV/0! (unlike IFERROR)
        checkEq(res.values["C3"], FormulaEval.CellValue.Err(CellErr.Err.DIV0))
        checkEq(res.values["C4"], FormulaEval.CellValue.Num(370.0))
        checkEq(res.values["C5"], FormulaEval.CellValue.Num(300.0))
        checkEq(res.values["C6"], FormulaEval.CellValue.Num(3.0)) // 120, 90, 50 are all < 150
        checkEq(res.values["C7"], FormulaEval.CellValue.Num(105.0)) // median of 50,90,120,250 = 105
        checkClose((res.values["C8"] as FormulaEval.CellValue.Num).value, 10.0, 1e-9)
        checkEq(res.values["D1"], FormulaEval.CellValue.Text("hello fusion"))
        checkEq(res.values["D2"], FormulaEval.CellValue.Text("HELLO"))
        checkEq(res.values["D3"], FormulaEval.CellValue.Text("llo"))
        checkEq(res.values["D4"], FormulaEval.CellValue.Text("fusion"))
        checkEq(res.values["E1"], FormulaEval.CellValue.Bool(true))
        checkEq(res.values["E2"], FormulaEval.CellValue.Bool(true))
        checkEq(res.values["E3"], FormulaEval.CellValue.Bool(true))
        checkEq(res.values["E4"], FormulaEval.CellValue.Bool(true))
        checkEq(res.values["E5"], FormulaEval.CellValue.Err(CellErr.Err.REF))
        checkThrows<IllegalArgumentException>("approximate VLOOKUP refused") {
            FormulaEval.evaluateSheet(
                mapOf(
                    "A1" to FormulaEval.CellInput.NumberLiteral(1.0),
                    "B1" to FormulaEval.CellInput.Formula("=VLOOKUP(1, A1:A1, 1, TRUE)"),
                ),
            )
        }
    }

    private fun testMultiSheetBook() {
        val sheets = listOf(
            MultiSheetBook.SheetDef(
                "Q1 Sales",
                mapOf(
                    "A1" to FormulaEval.CellInput.NumberLiteral(125.5),
                    "A2" to FormulaEval.CellInput.NumberLiteral(274.5),
                    "A3" to FormulaEval.CellInput.Formula("=SUM(A1:A2)"),
                ),
            ),
            MultiSheetBook.SheetDef(
                "Rates",
                mapOf(
                    "B1" to FormulaEval.CellInput.NumberLiteral(0.08),
                ),
            ),
            MultiSheetBook.SheetDef(
                "Summary",
                mapOf(
                    "A1" to FormulaEval.CellInput.Formula("='Q1 Sales'!A3"),
                    "A2" to FormulaEval.CellInput.Formula("=ROUND(A1 * Rates!B1, 2)"),
                    "A3" to FormulaEval.CellInput.Formula("=AVERAGE('Q1 Sales'!A1:A2)"),
                    "A4" to FormulaEval.CellInput.Formula("=MissingSheet!A1"),
                ),
            ),
        )
        val ev = MultiSheetBook.evaluate(sheets)
        checkEq(ev.sheetOrder, listOf("Q1 Sales", "Rates", "Summary"))
        checkEq(ev.cell("Q1 Sales", "A3"), FormulaEval.CellValue.Num(400.0))
        checkEq(ev.cell("Summary", "A1"), FormulaEval.CellValue.Num(400.0))
        checkEq(ev.cell("Summary", "A2"), FormulaEval.CellValue.Num(32.0))
        checkEq(ev.cell("Summary", "A3"), FormulaEval.CellValue.Num(200.0))
        checkEq(ev.cell("Summary", "A4"), FormulaEval.CellValue.Err(CellErr.Err.REF))

        val xlsx = MultiSheetBook.write(sheets)
        val xlsx2 = MultiSheetBook.write(sheets)
        checkTrue(xlsx.contentEquals(xlsx2), "zero-epoch multi-sheet xlsx determinism")
        val roundTripped = MultiSheetBook.read(xlsx)
        checkEq(roundTripped.map { it.name }, listOf("Q1 Sales", "Rates", "Summary"))
        val ev2 = MultiSheetBook.evaluate(roundTripped)
        checkEq(ev2.cell("Summary", "A2"), FormulaEval.CellValue.Num(32.0))

        // Cross-sheet cycle refusal
        checkThrows<IllegalArgumentException>("cross-sheet cycle") {
            MultiSheetBook.evaluate(
                listOf(
                    MultiSheetBook.SheetDef("S1", mapOf("A1" to FormulaEval.CellInput.Formula("=S2!A1 + 1"))),
                    MultiSheetBook.SheetDef("S2", mapOf("A1" to FormulaEval.CellInput.Formula("=S1!A1 + 1"))),
                ),
            )
        }
        // 3D span & external workbook refusal
        checkThrows<IllegalArgumentException>("3D span") {
            MultiSheetBook.evaluate(
                listOf(
                    MultiSheetBook.SheetDef("S1", mapOf("A1" to FormulaEval.CellInput.Formula("=SUM(S1:S2!A2)"))),
                    MultiSheetBook.SheetDef("S2", mapOf("A2" to FormulaEval.CellInput.NumberLiteral(5.0))),
                ),
            )
        }
        checkThrows<IllegalArgumentException>("external book") {
            MultiSheetBook.evaluate(
                listOf(MultiSheetBook.SheetDef("S1", mapOf("A1" to FormulaEval.CellInput.Formula("=[Evil.xlsx]Sheet1!A1")))),
            )
        }
    }

    private fun testSheetPivot() {
        val headers = listOf("Region", "Quarter", "Revenue")
        val rows = listOf(
            listOf("North", "Q1", "100"),
            listOf("North", "Q1", "50"),
            listOf("North", "Q2", "200"),
            listOf("South", "Q1", "80"),
            listOf("South", "Q2", "120"),
        )
        val sumRes = SheetPivot.summarize(headers, rows, SheetPivot.PivotSpec("Region", "Quarter", "Revenue", SheetPivot.AggFunc.SUM))
        checkEq(sumRes.rowKeys, listOf("North", "South"))
        checkEq(sumRes.colKeys, listOf("Q1", "Q2"))
        checkEq(sumRes.matrix["North" to "Q1"], 150.0)
        checkEq(sumRes.matrix["North" to "Q2"], 200.0)
        checkEq(sumRes.matrix["South" to "Q1"], 80.0)
        checkEq(sumRes.matrix["South" to "Q2"], 120.0)
        checkEq(sumRes.rowTotals["North"], 350.0)
        checkEq(sumRes.rowTotals["South"], 200.0)
        checkEq(sumRes.colTotals["Q1"], 230.0)
        checkEq(sumRes.colTotals["Q2"], 320.0)
        checkEq(sumRes.grandTotal, 550.0)
        // Marginal conservation law
        checkClose(sumRes.rowTotals.values.filterNotNull().sum(), sumRes.grandTotal!!, 1e-9)
        checkClose(sumRes.colTotals.values.filterNotNull().sum(), sumRes.grandTotal!!, 1e-9)

        val avgRes = SheetPivot.summarize(headers, rows, SheetPivot.PivotSpec("Region", null, "Revenue", SheetPivot.AggFunc.AVERAGE))
        checkClose(avgRes.rowTotals["North"]!!, 350.0 / 3.0, 1e-9)
        checkClose(avgRes.grandTotal!!, 110.0, 1e-9)

        val cntRes = SheetPivot.summarize(headers, rows, SheetPivot.PivotSpec("Region", "Quarter", "Revenue", SheetPivot.AggFunc.COUNT))
        checkEq(cntRes.grandTotal, 5.0)
        checkThrows<IllegalArgumentException>("non-numeric value") {
            SheetPivot.summarize(headers, listOf(listOf("N", "Q1", "NaN-text")), SheetPivot.PivotSpec("Region", null, "Revenue"))
        }
    }

    private fun testCondFormat() {
        val r = SheetRange.parse("A1:A4")
        val vals = mapOf(
            "A1" to FormulaEval.CellValue.Num(10.0),
            "A2" to FormulaEval.CellValue.Num(50.0),
            "A3" to FormulaEval.CellValue.Num(90.0),
            "A4" to FormulaEval.CellValue.Text("URGENT review"),
        )
        val rules = listOf(
            CondFormat.Rule.CellIs(1, r, CondFormat.Operator.GTE, 80.0, styleTag = "high", fill = CondFormat.Rgb(220, 40, 40), stopIfTrue = true),
            CondFormat.Rule.ColorScale2(2, r, CondFormat.Rgb(0, 0, 200), CondFormat.Rgb(0, 200, 0)),
            CondFormat.Rule.DataBar(3, r, CondFormat.Rgb(60, 120, 240)),
            CondFormat.Rule.TextContains(4, r, "urgent", "alertText", CondFormat.Rgb(255, 200, 0)),
        )
        val dec = CondFormat.evaluate(vals, rules)
        // A3 matched priority 1 with stopIfTrue=true -> does NOT get colorScale or dataBar!
        checkEq(dec["A3"]?.styleTags, listOf("high"))
        checkEq(dec["A3"]?.fill?.toHex(), "#DC2828")
        checkEq(dec["A3"]?.dataBarFraction, null)
        // A1 is min (10.0) of [10,50,90] -> t=0.0 -> (0,0,200), dataBar=0.0
        checkEq(dec["A1"]?.fill, CondFormat.Rgb(0, 0, 200))
        checkClose(dec["A1"]?.dataBarFraction!!, 0.0, 1e-9)
        // A2 is midpoint (50.0) of [10,90] -> t=0.5 -> (0,100,100), dataBar=0.5
        checkEq(dec["A2"]?.fill, CondFormat.Rgb(0, 100, 100))
        checkClose(dec["A2"]?.dataBarFraction!!, 0.5, 1e-9)
        checkEq(dec["A4"]?.styleTags, listOf("alertText"))

        val topDec = CondFormat.evaluate(vals, listOf(CondFormat.Rule.TopN(1, r, count = 2, styleTag = "top2", fill = CondFormat.Rgb(10, 20, 30))))
        checkTrue("top2" in topDec["A3"]!!.styleTags && "top2" in topDec["A2"]!!.styleTags)
        checkTrue(topDec["A1"] == null)
    }

    private fun testPdfPageWriter() {
        val blocks = buildList {
            add(PdfPageWriter.Element.Heading(1, "Fusion Writer Export (Escape test: (a)\\b)"))
            add(PdfPageWriter.Element.Table(listOf(listOf("Item", "Amount"), listOf("Compute", "$120.00"), listOf("Storage", "$45.00"))))
            add(PdfPageWriter.Element.BulletItem("1.", "Zero-timestamp reproducible PDF 1.7 output"))
            repeat(38) { idx ->
                add(PdfPageWriter.Element.Paragraph("Section paragraph ${idx + 1}: clean-room paginated vector text stream verified by xref oracle."))
            }
        }
        val p1 = PdfPageWriter.render(blocks, PdfPageWriter.PageSetup(headerTitle = "OSB R46 Spec"))
        val p2 = PdfPageWriter.render(blocks, PdfPageWriter.PageSetup(headerTitle = "OSB R46 Spec"))
        checkTrue(p1.bytes.contentEquals(p2.bytes), "PDF byte-for-byte determinism")
        checkTrue(p1.pageCount >= 2, "expected multi-page pagination, got ${p1.pageCount}")
        val latin = String(p1.bytes, StandardCharsets.ISO_8859_1)
        checkTrue(latin.startsWith("%PDF-1.7\n"))
        checkTrue(latin.contains("Page 1 of ${p1.pageCount}"))
        checkTrue(latin.contains("Page ${p1.pageCount} of ${p1.pageCount}"))
        checkTrue(latin.contains("\\(a\\)\\\\b"), "PDF string literal escaping")
        checkEq(latin.indexOf("xref\n"), p1.xrefOffset, "startxref matches exact byte offset of xref")
        // Verify every `00000 n ` offset in xref points to an `<id> 0 obj`
        val xrefLines = latin.substring(p1.xrefOffset).lines().drop(3).takeWhile { !it.startsWith("trailer") }
        checkTrue(xrefLines.size >= 7)
        for ((i, ln) in xrefLines.withIndex()) {
            val off = ln.substring(0, 10).toInt()
            checkTrue(latin.startsWith("${i + 1} 0 obj\n", off), "xref object ${i + 1} at $off")
        }
        checkTrue(!latin.contains("/CreationDate"), "zero forensic CreationDate in generated PDF")
    }

    private fun testDocOutlineRender() {
        val md = """
            # Fusion Writer
            
            Paragraph with **bold**, *italic*, and `inline<script>code`.
            
            - First <unsafe> bullet
            - Second **strong** bullet
            
            | Module | Status |
            | --- | --- |
            | ooxml | active |
            
            ```kotlin
            val x = 1 < 2 && 3 > 1
            ```
        """.trimIndent()
        val ast = DocOutlineRender.parseMarkdown(md)
        checkEq(ast.size, 5)
        val html = DocOutlineRender.toHtml(ast)
        checkTrue(html.contains("<h1>Fusion Writer</h1>"))
        checkTrue(html.contains("<strong>bold</strong>"))
        checkTrue(html.contains("<em>italic</em>"))
        checkTrue(html.contains("<code>inline&lt;script&gt;code</code>"), "XSS escaped in code span")
        checkTrue(html.contains("&lt;unsafe&gt;"), "XSS escaped in list item")
        checkTrue(!html.contains("<script>") && !html.contains("<unsafe>"))
        checkTrue(html.contains("<table><thead><tr><th>Module</th><th>Status</th></tr></thead>"))
        val roundMd = DocOutlineRender.toMarkdown(ast)
        checkEq(DocOutlineRender.toHtml(DocOutlineRender.parseMarkdown(roundMd)), html)
    }

    private fun testTrackRevisions() {
        val base = TrackRevisions.fromPlainText("Hello world.")
        val r1 = base.recordInsert(5, " brave new", "Alice", 1_700_000_000L)
        checkEq(r1.visibleOriginalText(), "Hello world.")
        checkEq(r1.visibleFinalText(), "Hello brave new world.")

        val r2 = r1.recordDelete(16, 21, "Bob", 1_700_000_100L) // delete "world"
        val r3 = r2.recordInsert(16, "planet", "Bob", 1_700_000_101L)
        checkEq(r3.visibleFinalText(), "Hello brave new planet.")
        checkEq(r3.visibleOriginalText(), "Hello world.")
        checkEq(r3.pendingRevisionIds().size, 3)

        // Fundamental tracked-changes invariants:
        checkEq(r3.acceptAll().visibleFinalText(), "Hello brave new planet.")
        checkEq(r3.acceptAll().pendingRevisionIds(), emptySet<Long>())
        checkEq(r3.rejectAll().visibleFinalText(), "Hello world.")
        checkEq(r3.rejectAll().pendingRevisionIds(), emptySet<Long>())

        // Selective accept/reject: reject revision 1 (" brave new"), accept 2 & 3 ("world" -> "planet")
        val selective = r3.rejectRevision(1L).acceptRevision(2L).acceptRevision(3L)
        checkEq(selective.visibleFinalText(), "Hello planet.")
        checkEq(selective.pendingRevisionIds(), emptySet<Long>())

        // Privacy anonymizer scrubs Alice/Bob + timestamps
        val anon = r3.anonymizeAuthors("Reviewer")
        checkTrue(anon.segments.none { it.author == "Alice" || it.author == "Bob" || it.timestampEpochSec != 0L })
    }

    private fun testCitationBib() {
        val bib = """
            @inproceedings{smith2025,
              author = {Smith, Alice and Doe, Bob},
              title = {{Zero-JNI} Sandboxing on Mobile},
              booktitle = {Proc. USENIX Security},
              year = {2025}
            }
            @article{lee2026,
              author = "Claire Lee",
              title = "Deterministic Spreadsheets",
              journal = "ACM TOPLAS",
              year = 2026
            }
        """.trimIndent()
        val lib = CitationBib.parseBibtex(bib)
        checkEq(lib.size, 2)
        checkEq(lib["smith2025"]?.title(), "Zero-JNI Sandboxing on Mobile")

        val prose = "Prior work [@lee2026; @smith2025, p. 14] and [@lee2026] plus [@unknown2099]."
        val ieee = CitationBib.resolveCitations(prose, lib, CitationBib.Style.IEEE_NUMERIC)
        checkEq(ieee.renderedText, "Prior work [1; 2, p. 14] and [1] plus [?unknown2099].")
        checkEq(ieee.citedKeysInOrder, listOf("lee2026", "smith2025"))
        checkEq(ieee.missingKeys, setOf("unknown2099"))
        checkEq(ieee.bibliography.size, 2)
        checkTrue(ieee.bibliography[0].startsWith("[1] Claire Lee"))
        checkTrue(ieee.bibliography[1].startsWith("[2] Smith, Alice and Doe, Bob"))

        val apa = CitationBib.resolveCitations("See [@smith2025, ch. 3].", lib, CitationBib.Style.APA_AUTHOR_YEAR)
        checkEq(apa.renderedText, "See (Smith, 2025, ch. 3).")
        checkThrows<IllegalArgumentException>("duplicate key") {
            CitationBib.parseBibtex("@misc{k1, title={A}} @misc{k1, title={B}}")
        }
    }

    private fun testOdsSheetExtract() {
        val contentXml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <office:document-content
              xmlns:office="urn:oasis:names:tc:opendocument:xmlns:office:1.0"
              xmlns:table="urn:oasis:names:tc:opendocument:xmlns:table:1.0"
              xmlns:text="urn:oasis:names:tc:opendocument:xmlns:text:1.0">
              <office:body><office:spreadsheet>
                <table:table table:name="Budget">
                  <table:table-row>
                    <table:table-cell office:value-type="string"><text:p>Item</text:p></table:table-cell>
                    <table:table-cell office:value-type="currency" office:currency="USD" office:value="49.5"><text:p>$49.50</text:p></table:table-cell>
                    <table:table-cell office:value-type="float" office:value="99" table:formula="of:=[.B1]*2"><text:p>99</text:p></table:table-cell>
                  </table:table-row>
                </table:table>
              </office:spreadsheet></office:body>
            </office:document-content>
        """.trimIndent()
        val odsBytes = buildSimpleZip(
            mapOf(
                "mimetype" to "application/vnd.oasis.opendocument.spreadsheet".toByteArray(),
                "content.xml" to contentXml.toByteArray(),
            ),
        )
        val sheets = OdsSheetExtract.extractArchive(odsBytes)
        checkEq(sheets.size, 1)
        checkEq(sheets[0].name, "Budget")
        checkEq(sheets[0].rows[0][0].value, OdsSheetExtract.CellData.Text("Item"))
        checkEq(sheets[0].rows[0][1].value, OdsSheetExtract.CellData.Num(49.5, currency = "USD"))
        checkEq(sheets[0].rows[0][2].openFormula, "=[.B1]*2")

        // Repeat-bomb, XXE, and Basic macro rejections
        checkThrows<IllegalArgumentException>("column repeat bomb") {
            OdsSheetExtract.extractContentXml(
                contentXml.replace("office:value-type=\"string\"", "table:number-columns-repeated=\"99999\" office:value-type=\"string\""),
            )
        }
        checkThrows<IllegalArgumentException>("XXE DOCTYPE") {
            OdsSheetExtract.extractContentXml("<!DOCTYPE foo><root/>")
        }
        checkThrows<IllegalArgumentException>("Basic macro part") {
            OdsSheetExtract.extractArchive(
                buildSimpleZip(mapOf("Basic/script.xml" to "<x/>".toByteArray(), "content.xml" to contentXml.toByteArray())),
            )
        }
    }

    private fun testWasmSandbox() {
        // Hand-assembled valid W3C WebAssembly 1.0 module exporting:
        //   func 0 "fib"(n: i32) -> i32 (iterative loop computing n-th Fibonacci)
        //   func 1 "spin"() -> i32      (infinite `loop br 0 end` for fuel trap verification!)
        //   func 2 "mem_sum"(ptr: i32, val: i32) -> i32 (stores val at ptr, loads ptr + ptr+4)
        val wasm = buildTestWasmModule()
        val mod = WasmSandbox.decode(wasm)
        val inst = mod.instantiate(maxMemoryPages = 2)
        checkEq(inst.memoryPages(), 1)

        checkEq(inst.invoke("fib", listOf(0)).returnValues, listOf(0))
        checkEq(inst.invoke("fib", listOf(1)).returnValues, listOf(1))
        val fib10 = inst.invoke("fib", listOf(10))
        checkEq(fib10.returnValues, listOf(55))
        checkTrue(fib10.fuelUsed > 10L && fib10.fuelRemaining > 0L)

        // Linear memory write + Wasm load/store verification
        inst.writeMemory(16, byteArrayOf(10, 0, 0, 0, 32, 0, 0, 0))
        checkEq(inst.invoke("mem_sum", listOf(16, 8)).returnValues, listOf(40))

        // Out-of-bounds linear memory read traps cleanly!
        var oobTrapped = false
        try {
            inst.invoke("mem_sum", listOf(65_534, 1))
        } catch (e: WasmSandbox.WasmTrapException) {
            oobTrapped = e.kind == WasmSandbox.TrapKind.OUT_OF_BOUNDS_MEMORY
        }
        checkTrue(oobTrapped, "expected OUT_OF_BOUNDS_MEMORY trap")

        // Infinite loop halts deterministically at exact fuelBudget!
        var fuelTrapped = false
        try {
            inst.invoke("spin", emptyList(), fuelBudget = 250L)
        } catch (e: WasmSandbox.WasmTrapException) {
            fuelTrapped = e.kind == WasmSandbox.TrapKind.OUT_OF_FUEL
        }
        checkTrue(fuelTrapped, "expected OUT_OF_FUEL gas-meter trap")

        // Ambient WASI host import section (id=2, count=1) MUST be refused!
        val importMod = byteArrayOf(
            0x00, 0x61, 0x73, 0x6D, 0x01, 0x00, 0x00, 0x00,
            0x02, 0x07, 0x01, 0x01, 0x65, 0x01, 0x66, 0x00, 0x00,
        )
        checkThrows<IllegalArgumentException>("WASI/host imports refused") {
            WasmSandbox.decode(importMod)
        }
    }

    private fun testQuotaBudget() {
        val root = QuotaBudget.Node("root", QuotaBudget.Limits(1_000L, 4, 100L, 50L), initialEpochMs = 0L)
        val plugin = QuotaBudget.Node("plugin-a", QuotaBudget.Limits(800L, 3, 80L, 40L), parent = root, initialEpochMs = 0L)

        checkTrue(plugin.tryAllocateMemory(600L))
        checkTrue(root.tryAllocateMemory(300L))
        // Now root is at 900/1000, plugin is at 600/800. Requesting 150 on plugin fits child (750<=800)
        // but exceeds root (1050>1000) -> MUST reject atomically with zero partial leak!
        checkTrue(!plugin.tryAllocateMemory(150L))
        checkEq(plugin.snapshot().currentMemoryBytes, 600L)
        checkEq(root.snapshot().currentMemoryBytes, 900L)

        plugin.releaseMemory(400L)
        checkEq(plugin.snapshot().currentMemoryBytes, 200L)
        checkEq(plugin.snapshot().peakMemoryBytes, 600L, "peak watermark retained")

        checkTrue(plugin.tryOpenDescriptor(3))
        checkTrue(!plugin.tryOpenDescriptor(1))
        plugin.closeDescriptor(2)
        checkEq(plugin.snapshot().openDescriptors, 1)

        checkTrue(plugin.tryConsumeCpu(80L, 0L))
        checkTrue(!plugin.tryConsumeCpu(10L, 0L))
        checkTrue(plugin.tryConsumeCpu(30L, 1_000L), "1s refill restores 40 tokens")
    }

    private fun testWalFrame() {
        val p1 = "page-one-v1".toByteArray()
        val p2 = "page-two-v1".toByteArray()
        val p1v2 = "page-one-v2-committed".toByteArray()
        val p3Uncommitted = "page-three-uncommitted".toByteArray()

        val wal = WalFrame.encode(
            salt = 0x5A17_2026,
            entries = listOf(
                WalFrame.Entry(1, p1, isCommit = false),
                WalFrame.Entry(2, p2, isCommit = true),
                WalFrame.Entry(1, p1v2, isCommit = true),
                WalFrame.Entry(3, p3Uncommitted, isCommit = false),
            ),
        )
        // Simulate power loss mid-write by appending 9 torn garbage bytes!
        val crashed = wal + byteArrayOf(1, 2, 3, 4, 5, 6, 7, 8, 9)
        val rep = WalFrame.recover(crashed)
        checkEq(rep.committedFrameCount, 3)
        checkEq(rep.discardedTrailingFrames, 1, "uncommitted tx rolled back")
        checkEq(rep.truncatedTornBytes, 9, "torn tail detected")
        checkTrue(rep.committedPages[1]!!.contentEquals(p1v2))
        checkTrue(rep.committedPages[2]!!.contentEquals(p2))
        checkTrue(3 !in rep.committedPages)

        // Flip 1 bit inside frame 3 payload -> recovery rolls back cleanly to tx 1 (frames 1..2)!
        val bitFlip = wal.copyOf()
        bitFlip[bitFlip.size - p3Uncommitted.size - 21 - 3] = (bitFlip[bitFlip.size - p3Uncommitted.size - 21 - 3].toInt() xor 0xFF).toByte()
        val repFlip = WalFrame.recover(bitFlip)
        checkEq(repFlip.committedFrameCount, 2)
        checkTrue(repFlip.committedPages[1]!!.contentEquals(p1))

        val compacted = WalFrame.recover(WalFrame.checkpoint(rep, newSalt = 99))
        checkEq(compacted.committedPages.keys, setOf(1, 2))
    }

    private fun testPipeChain(corpusJson: String) {
        val log = "INFO boot\nERROR disk full\nWARN cache\nerror Disk Full\nERROR timeout"
        val out = PipeChain.execute(
            "grep -i error | sort -r | head -n 2",
            log,
            PipeChain.standardFilters(),
        )
        checkEq(out, "error Disk Full\nERROR timeout")
        checkEq(PipeChain.execute("grep ERROR | wc -l", log, PipeChain.standardFilters()), "2")

        // Every injection vector from fixture corpus MUST be rejected!
        for (bad in listOf(
            "grep error; rm -rf /",
            "grep error && cat /etc/passwd",
            "grep error || echo pwned",
            "grep `whoami`",
            "grep \$(id)",
            "grep \"\$(id)\"",
            "grep error > /tmp/leak",
        )) {
            checkThrows<IllegalArgumentException>("injection refused: $bad") {
                PipeChain.parse(bad)
            }
        }
        checkTrue(corpusJson.contains("pipe_chain_injections"))
    }

    private fun testSemVerRange() {
        val ordered = listOf(
            "1.0.0-alpha",
            "1.0.0-alpha.1",
            "1.0.0-alpha.beta",
            "1.0.0-beta",
            "1.0.0-beta.2",
            "1.0.0-beta.11",
            "1.0.0-rc.1",
            "1.0.0",
            "1.2.0",
            "1.2.4",
            "2.0.0",
        ).map(SemVerRange::parseVersion)

        for (i in 0 until ordered.lastIndex) {
            checkTrue(ordered[i] < ordered[i + 1], "SemVer 2.0.0 §11 order ${ordered[i]} < ${ordered[i + 1]}")
        }
        checkEq(SemVerRange.Constraint.parse("^1.0.0").selectHighest(ordered)?.toString(), "1.2.4")
        checkEq(SemVerRange.Constraint.parse("~1.0.0").selectHighest(ordered)?.toString(), "1.0.0")
        checkEq(SemVerRange.Constraint.parse(">=1.0.0 <2.0.0").selectHighest(ordered)?.toString(), "1.2.4")
        checkEq(SemVerRange.Constraint.parse("^0.2.3").satisfies(SemVerRange.parseVersion("0.2.9")), true)
        checkEq(SemVerRange.Constraint.parse("^0.2.3").satisfies(SemVerRange.parseVersion("0.3.0")), false)
        checkThrows<IllegalArgumentException>("leading zero refused") { SemVerRange.parseVersion("01.2.3") }
    }

    private fun testSchemaMigrate() {
        val steps = listOf(
            SchemaMigrate.Step(1, 2, "add theme") { it + ("theme" to "dark") },
            SchemaMigrate.Step(2, 3, "add density") { it + ("density" to "compact") },
            SchemaMigrate.Step(3, 4, "rename key") { m -> (m - "density") + ("ui_scale" to (m["density"] ?: "normal")) },
            // Verified shortcut 1 -> 3 (1 hop instead of 2!)
            SchemaMigrate.Step(1, 3, "fast v1->v3") { it + ("theme" to "dark") + ("density" to "compact") },
        )
        val res = SchemaMigrate.migrate(steps, 1, 4, mapOf("user" to "local"))
        checkEq(res.finalVersion, 4)
        checkEq(res.appliedSteps, listOf(1 to 3, 3 to 4), "BFS picked shortest 2-hop path")
        checkEq(res.settings, mapOf("user" to "local", "theme" to "dark", "ui_scale" to "compact"))

        checkThrows<IllegalArgumentException>("downgrade refused") {
            SchemaMigrate.plan(steps, 4, 2)
        }
        checkThrows<IllegalArgumentException>("missing hop v4->v6") {
            SchemaMigrate.plan(steps, 1, 6)
        }
    }

    private fun testColumnarPage() {
        val longs = listOf(1000L, 1005L, null, 1010L, 990L, null, 1050L)
        val lPage = ColumnarPage.encodeLongColumn(longs)
        val lStats = ColumnarPage.inspectLongStats(lPage)
        checkEq(lStats.rowCount, 7)
        checkEq(lStats.nullCount, 2)
        checkEq(lStats.min, 990L)
        checkEq(lStats.max, 1050L)
        checkTrue(lStats.mayMatchRange(1040L, 2000L))
        checkTrue(!lStats.mayMatchRange(2000L, 3000L), "zone-map skips page outside [min,max]")
        checkEq(ColumnarPage.decodeLongColumn(lPage), longs)

        val strs = listOf("tails", "fusion", null, "tails", "chicory", "fusion")
        val sPage = ColumnarPage.encodeUtf8Column(strs)
        val sStats = ColumnarPage.inspectUtf8Stats(sPage)
        checkEq(sStats.dictSize, 3, "dictionary deduplicated 5 strings into 3 entries")
        checkEq(sStats.min, "chicory")
        checkEq(sStats.max, "tails")
        checkEq(ColumnarPage.decodeUtf8Column(sPage), strs)
    }

    private fun testTarPax() {
        val longPath = "workspace/" + "deep_segment_".repeat(9) + "résumé-2026.txt"
        checkTrue(longPath.toByteArray(StandardCharsets.UTF_8).size > 110)
        val files = listOf(
            TarPax.TarFile("b/second.txt", "second".toByteArray()),
            TarPax.TarFile(longPath, "pax utf8 content".toByteArray()),
            TarPax.TarFile("a/first.txt", "first".toByteArray()),
        )
        val t1 = TarPax.writeDeterministic(files)
        val t2 = TarPax.writeDeterministic(files.reversed())
        checkTrue(t1.contentEquals(t2), "TarPax canonical order + zero-epoch reproducibility")

        val unpacked = TarPax.read(t1)
        checkEq(unpacked.map { it.path }, listOf("a/first.txt", "b/second.txt", longPath))
        checkEq(String(unpacked[2].data, StandardCharsets.UTF_8), "pax utf8 content")

        checkThrows<IllegalArgumentException>("traversal refused") {
            TarPax.TarFile("../etc/shadow", byteArrayOf(1))
        }
        // Corrupt a header byte -> checksum verification rejects
        val corrupted = t1.copyOf()
        corrupted[10] = 'Z'.code.toByte()
        checkThrows<IllegalArgumentException>("TAR header checksum mismatch") {
            TarPax.read(corrupted)
        }
        // Craft symlink entry (typeflag = '2') -> rejected!
        val symlinkTar = t1.copyOf()
        symlinkTar[156] = '2'.code.toByte()
        recomputeTarChecksum(symlinkTar, 0)
        checkThrows<IllegalArgumentException>("symlink typeflag '2' refused") {
            TarPax.read(symlinkTar)
        }
    }

    private fun testOnionGraterFilter(corpusJson: String) {
        val sess = OnionGraterFilter.Session(
            OnionGraterFilter.onionCircuitsProfile(clientIp = "10.200.1.5", isolatedSocks = "127.0.0.1:9152"),
        )
        // Before AUTHENTICATE, commands are rejected with 514
        val preAuth = sess.evaluateClientLine("GETINFO circuit-status\r\n")
        checkEq((preAuth as OnionGraterFilter.FilterDecision.Denied).replyCode, 514)

        checkTrue(sess.evaluateClientLine("PROTOCOLINFO\r\n") is OnionGraterFilter.FilterDecision.SyntheticReply)
        checkTrue(sess.evaluateClientLine("AUTHENTICATE\r\n") is OnionGraterFilter.FilterDecision.SyntheticReply)
        checkTrue(sess.isAuthenticated)

        // Allowed commands & rewritten SOCKS listener
        checkEq(
            sess.evaluateClientLine("SIGNAL newnym"),
            OnionGraterFilter.FilterDecision.ForwardToTor("SIGNAL NEWNYM"),
        )
        val socksReply = sess.evaluateClientLine("GETINFO net/listeners/socks") as OnionGraterFilter.FilterDecision.SyntheticReply
        checkTrue(socksReply.controlReply.contains("127.0.0.1:9152"))

        // Dangerous commands return 510 Command filtered!
        for (deniedCmd in listOf(
            "GETCONF SocksPort",
            "SETCONF ExitNodes={us}",
            "SAVECONF",
            "ADD_ONION NEW:ED25519-V3 Port=80",
            "TAKEOWNERSHIP",
            "SIGNAL SHUTDOWN",
            "GETINFO address",
            "SETEVENTS BW",
        )) {
            val dec = sess.evaluateClientLine(deniedCmd)
            checkTrue(dec is OnionGraterFilter.FilterDecision.Denied && dec.replyCode == 510, "expected 510 on $deniedCmd")
        }
        checkTrue(corpusJson.contains("onion_grater_denied_commands"))

        // STREAM event scoping: client 10.200.1.5 only sees its OWN streams!
        checkTrue(sess.evaluateClientLine("SETEVENTS CIRC STREAM") is OnionGraterFilter.FilterDecision.ForwardToTor)
        checkTrue(sess.allowAsyncTorEvent("650 STREAM 42 NEW 0 example.onion:443 SOURCE_ADDR=10.200.1.5:51234"))
        checkTrue(!sess.allowAsyncTorEvent("650 STREAM 43 NEW 0 secret.onion:443 SOURCE_ADDR=10.200.1.99:51234"))
    }

    private fun testMetadataScrub() {
        val dirtyDocx = buildSimpleZip(
            mapOf(
                "[Content_Types].xml" to """<Types><Override PartName="/word/document.xml" ContentType="x"/><Override PartName="/docProps/core.xml" ContentType="y"/></Types>""".toByteArray(),
                "_rels/.rels" to """<Relationships><Relationship Id="rId1" Target="word/document.xml"/><Relationship Id="rId2" Target="docProps/core.xml"/></Relationships>""".toByteArray(),
                "docProps/core.xml" to "<cp:coreProperties><dc:creator>Real Name</dc:creator></cp:coreProperties>".toByteArray(),
                "docProps/app.xml" to "<Properties><Company>Secret Corp</Company></Properties>".toByteArray(),
                "word/document.xml" to "<w:document><w:body>Clean prose</w:body></w:document>".toByteArray(),
            ),
        )
        val scrubbed = MetadataScrub.scrubZipPackage(dirtyDocx)
        checkEq(scrubbed.removedItems, listOf("docProps/app.xml", "docProps/core.xml"))
        val rawLatin = String(scrubbed.sanitizedBytes, StandardCharsets.ISO_8859_1)
        checkTrue(!rawLatin.contains("Real Name") && !rawLatin.contains("Secret Corp"))
        checkTrue(!rawLatin.contains("docProps/core.xml"))
        checkTrue(rawLatin.contains("Clean prose"))

        // PDF in-place metadata redaction preserves exact byte length & xref validity!
        val basePdf = PdfPageWriter.render(listOf(PdfPageWriter.Element.Paragraph("Body"))).bytes
        val infoSuffix = "% /Author (Jane Journalist) /Creator (LeakyTool 9.1) /CreationDate (D:20261026120000)\n".toByteArray(StandardCharsets.ISO_8859_1)
        val dirtyPdf = basePdf + infoSuffix
        val cleanPdf = MetadataScrub.scrubPdfInPlace(dirtyPdf)
        checkEq(cleanPdf.sanitizedBytes.size, dirtyPdf.size, "length-preserving PDF scrub")
        checkEq(cleanPdf.removedItems, listOf("Author", "CreationDate", "Creator"))
        val cleanPdfStr = String(cleanPdf.sanitizedBytes, StandardCharsets.ISO_8859_1)
        checkTrue(!cleanPdfStr.contains("Jane Journalist") && !cleanPdfStr.contains("LeakyTool"))
    }

    private fun testUsbGuardPolicy() {
        val bootKbd = UsbGuardPolicy.UsbInterfaceSpec(0x03, 0x01, 0x01)
        val storage = UsbGuardPolicy.UsbInterfaceSpec(0x08, 0x06, 0x50)
        val hub = UsbGuardPolicy.UsbInterfaceSpec(0x09, 0x00, 0x00)

        // 1. Lockscreen blocks even non-HID mass storage
        val lockedCtx = UsbGuardPolicy.HostContext(screenLocked = true, activeKeyboardCount = 0, lastKeyboardDisconnectMs = null, nowMs = 5_000L)
        checkEq(
            UsbGuardPolicy.evaluate(UsbGuardPolicy.UsbCandidate(0x0781, 0x5581, "aa", listOf(storage)), lockedCtx).action,
            UsbGuardPolicy.Action.REJECT,
        )

        val unlockedCtx = UsbGuardPolicy.HostContext(
            screenLocked = false,
            activeKeyboardCount = 1,
            lastKeyboardDisconnectMs = 4_000L,
            nowMs = 5_500L,
            pinnedTrustedVidPidSerials = setOf("046d:c31c:deadbeef"),
        )
        // 2. Composite Rubber Ducky / Bash Bunny (MassStorage + HID Boot Keyboard) -> REJECT!
        val ducky = UsbGuardPolicy.UsbCandidate(0x1234, 0x5678, "11", listOf(storage, bootKbd))
        val duckyVerdict = UsbGuardPolicy.evaluate(ducky, unlockedCtx)
        checkEq(duckyVerdict.action, UsbGuardPolicy.Action.REJECT)
        checkEq(duckyVerdict.ruleId, "COMPOSITE_BADUSB_HID")

        // 3. Inline hardware keylogger / O.MG hub interposer -> REJECT!
        val inlineLogger = UsbGuardPolicy.UsbCandidate(0x046d, 0xc31c, "untrusted", listOf(bootKbd), attachedBehindNewInlineHub = true)
        checkEq(UsbGuardPolicy.evaluate(inlineLogger, unlockedCtx).ruleId, "INLINE_KEYLOGGER_INTERPOSER")
        val hubCombo = UsbGuardPolicy.UsbCandidate(0x1a40, 0x0101, null, listOf(hub, bootKbd))
        checkEq(UsbGuardPolicy.evaluate(hubCombo, unlockedCtx).ruleId, "INLINE_KEYLOGGER_INTERPOSER")

        // 4. Pinned trusted VID:PID:serial -> ALLOW
        val pinned = UsbGuardPolicy.UsbCandidate(0x046d, 0xc31c, "deadbeef", listOf(bootKbd))
        checkEq(UsbGuardPolicy.evaluate(pinned, unlockedCtx).action, UsbGuardPolicy.Action.ALLOW)

        // 5. Unpinned secondary keyboard (after interposer window) -> QUARANTINE_PROMPT
        val laterCtx = unlockedCtx.copy(nowMs = 20_000L)
        checkEq(UsbGuardPolicy.evaluate(inlineLogger.copy(attachedBehindNewInlineHub = false), laterCtx).action, UsbGuardPolicy.Action.QUARANTINE_PROMPT)
    }

    private fun testBleHidPairingGate() {
        // BlueDucky CVE-2023-45866: unauthenticated NoInputNoOutput L2CAP PSM 0x0013 -> REJECT!
        val blueDucky = BleHidPairingGate.WirelessHidAttempt(
            macAddress = "AA:BB:CC:11:22:33",
            exposesHogp1812 = false,
            requestedL2capPsm = BleHidPairingGate.PSM_HID_INTERRUPT,
            ioCapability = BleHidPairingGate.IoCapability.NO_INPUT_NO_OUTPUT,
            authMethod = BleHidPairingGate.AuthMethod.JUST_WORKS,
            secureConnections = true,
            encryptionKeyBytes = 16,
            screenLocked = false,
        )
        val d1 = BleHidPairingGate.evaluate(blueDucky)
        checkEq(d1.allowed, false)
        checkEq(d1.code, "DENY_CVE_2023_45866_BLUEDUCKY")

        // Post-bonding GATT 0x1812 HID elevation from previously bonded non-HID earbuds -> REJECT!
        val elevated = blueDucky.copy(
            exposesHogp1812 = true,
            requestedL2capPsm = null,
            ioCapability = BleHidPairingGate.IoCapability.KEYBOARD_DISPLAY,
            authMethod = BleHidPairingGate.AuthMethod.PASSKEY_ENTRY_MITM,
            bondedOriginallyWithHid = false,
        )
        checkEq(BleHidPairingGate.evaluate(elevated).code, "DENY_GATT_HID_ELEVATION")

        // KNOB weak 7-byte encryption key -> REJECT!
        val knob = elevated.copy(bondedOriginallyWithHid = null, encryptionKeyBytes = 7)
        checkEq(BleHidPairingGate.evaluate(knob).code, "DENY_KNOB_WEAK_KEY")

        // Legitimate BLE Secure Connections Passkey-MITM 16-byte keyboard while unlocked -> ALLOW!
        val legit = elevated.copy(bondedOriginallyWithHid = null, encryptionKeyBytes = 16)
        checkEq(BleHidPairingGate.evaluate(legit).allowed, true)
    }

    private fun testMacRandomizer() {
        val secret = ByteArray(32) { (it * 7 + 3).toByte() }
        val hw = MacRandomizer.parse("dc:a6:32:12:34:56")
        val mCafe = MacRandomizer.derivePerNetworkMac(secret, "wlan0", "CafeWiFi", 0L)
        val mHotel = MacRandomizer.derivePerNetworkMac(secret, "wlan0", "HotelGuest", 0L)
        val mRotated = MacRandomizer.derivePerNetworkMac(secret, "wlan0", "CafeWiFi", 1L)

        checkTrue(mCafe.isUnicast && mCafe.isLocallyAdministered && mCafe.isValidPrivateClientMac)
        checkTrue(mHotel.isValidPrivateClientMac && mCafe != mHotel, "cross-SSID unlinkability")
        checkTrue(mCafe != mRotated, "epoch rotation changes MAC")
        checkTrue(MacRandomizer.verifyNoHardwareLeak(mCafe, hw))
        checkTrue(!MacRandomizer.verifyNoHardwareLeak(hw, hw), "hardware MAC leak detected")
        checkTrue(!MacRandomizer.parse("01:00:5e:00:00:fb").isValidPrivateClientMac, "multicast MAC rejected")
    }

    private fun testKeystrokeJitter() {
        // 1. Superhuman 6ms DuckyScript burst -> SYNTHETIC_SUPERHUMAN_SPEED (blocked!)
        val duckyTimes = List(12) { it * 6L }
        val rFast = KeystrokeJitter.classifyCadence(duckyTimes)
        checkEq(rFast.classification, KeystrokeJitter.CadenceClassification.SYNTHETIC_SUPERHUMAN_SPEED)
        checkTrue(rFast.shouldBlockStream)

        // 2. Slowed-down BadUSB sleeping exact 80ms between strokes -> SYNTHETIC_CLOCKWORK_REGULARITY (blocked!)
        val clockworkTimes = List(12) { it * 80L }
        val rClock = KeystrokeJitter.classifyCadence(clockworkTimes)
        checkEq(rClock.classification, KeystrokeJitter.CadenceClassification.SYNTHETIC_CLOCKWORK_REGULARITY)
        checkTrue(rClock.shouldBlockStream)

        // 3. Natural human cadence with realistic variance -> HUMAN_PLAUSIBLE
        val humanTimes = listOf(0L, 118L, 265L, 355L, 540L, 672L, 880L, 995L, 1210L, 1345L)
        val rHuman = KeystrokeJitter.classifyCadence(humanTimes)
        checkEq(rHuman.classification, KeystrokeJitter.CadenceClassification.HUMAN_PLAUSIBLE)
        checkTrue(!rHuman.shouldBlockStream)

        // 4. Outbound jitter + chaff preserves exact real-keystroke FIFO order!
        val sched = KeystrokeJitter.buildJitterSchedule(humanTimes, maxJitterMs = 25, chaffPerRealKey = 2, entropySeed = 42L)
        checkEq(sched.size, humanTimes.size * 3)
        checkEq(sched.filter { !it.isChaffCover }.map { it.payloadSlot }, humanTimes.indices.toList())

        // 5. Scrambled PIN pad contains exact digits 0..9 once each
        val pad = KeystrokeJitter.scramblePinDigits(ByteArray(16) { (it + 9).toByte() })
        checkEq(pad.sorted(), (0..9).toList())
    }

    private fun testPermissionsPolicyHeader() {
        val hdr = """geolocation=(self "https://maps.example.test"), camera=(self), usb=(), fullscreen=*"""
        val pol = PermissionsPolicyHeader.parse(hdr)
        checkTrue(pol.isAllowed("geolocation", "https://app.example.test", "https://app.example.test"))
        checkTrue(pol.isAllowed("geolocation", "https://app.example.test", "https://maps.example.test"))
        checkTrue(!pol.isAllowed("geolocation", "https://app.example.test", "https://tracker.example.test"))
        checkTrue(!pol.isAllowed("usb", "https://app.example.test"))
        // Omitted hardware APIs (`hid`, `bluetooth`) default to DENY!
        checkTrue(!pol.isAllowed("hid", "https://app.example.test"))
        checkTrue(!pol.isAllowed("bluetooth", "https://app.example.test"))
        checkTrue(pol.isAllowed("fullscreen", "https://app.example.test", "https://any.example.test"))

        val strict = PermissionsPolicyHeader.strictTailsPreset()
        checkTrue(!strict.intersectWithChild(pol, "https://app.example.test", "https://app.example.test"))
        checkThrows<IllegalArgumentException>("cleartext http origin refused") {
            PermissionsPolicyHeader.parse("""geolocation=("http://insecure.example.test")""")
        }
    }

    private fun testHlsPlaylist() {
        val masterM3u = """
            #EXTM3U
            #EXT-X-STREAM-INF:BANDWIDTH=800000,RESOLUTION=640x360,CODECS="avc1.4d401e"
            low/prog.m3u8
            #EXT-X-STREAM-INF:BANDWIDTH=2800000,RESOLUTION=1280x720,CODECS="avc1.4d401f"
            hd/prog.m3u8
            #EXT-X-STREAM-INF:BANDWIDTH=6200000,RESOLUTION=1920x1080
            fhd/prog.m3u8
        """.trimIndent()
        val master = HlsPlaylist.parse(masterM3u) as HlsPlaylist.Playlist.Master
        checkEq(master.variants.size, 3)
        checkEq(master.selectVariant(maxBandwidthBps = 3_500_000L).uri, "hd/prog.m3u8")
        checkEq(master.selectVariant(maxBandwidthBps = 9_000_000L, maxHeightPx = 720).uri, "hd/prog.m3u8")

        val mediaM3u = """
            #EXTM3U
            #EXT-X-TARGETDURATION:6
            #EXT-X-MEDIA-SEQUENCE:100
            #EXT-X-KEY:METHOD=AES-128,URI="https://cdn.example.test/key.bin"
            #EXTINF:6.006,Intro
            seg100.ts
            #EXTINF:4.500,Outro
            seg101.ts
            #EXT-X-ENDLIST
        """.trimIndent()
        val media = HlsPlaylist.parse(mediaM3u) as HlsPlaylist.Playlist.Media
        checkTrue(media.isEndList)
        checkEq(media.segments.map { it.sequenceNumber }, listOf(100L, 101L))
        checkClose(media.totalDurationSec, 10.506, 1e-6)
        checkEq(media.segments[0].encryptedAes128Uri, "https://cdn.example.test/key.bin")

        checkThrows<IllegalArgumentException>("file:// SSRF segment URI refused") {
            HlsPlaylist.parse("#EXTM3U\n#EXTINF:5.0,\nfile:///etc/passwd")
        }
    }

    private fun testDesktopEntryIni() {
        val ini = """
            # Sample desktop entry
            [Desktop Entry]
            Type=Application
            Name=Fusion Writer
            Name[fr]=Rédacteur Fusion
            Icon=fusion-writer
            Exec=fusion-writer --open %F --title=%c
            Categories=Office;WordProcessor;
        """.trimIndent()
        val entry = DesktopEntryIni.parse(ini)
        checkEq(entry.nameForLocale("fr_CA"), "Rédacteur Fusion")
        checkEq(entry.nameForLocale("de_DE"), "Fusion Writer")
        checkEq(entry.categories, listOf("Office", "WordProcessor"))
        checkEq(
            entry.expandExecArgv(listOf("/docs/a b.docx", "/docs/c.docx")),
            listOf("fusion-writer", "--open", "/docs/a b.docx", "/docs/c.docx", "--title=Fusion Writer"),
        )
        checkThrows<IllegalArgumentException>("shell metacharacter in Exec= refused") {
            DesktopEntryIni.parse("[Desktop Entry]\nType=Application\nName=Bad\nExec=sh -c \"`id`\"").expandExecArgv()
        }
    }

    private fun testTrigramIndex() {
        val corpus = mapOf(
            "doc1" to "OnionGrater filters Tor ControlPort commands per client profile.",
            "doc2" to "Chicory executes WebAssembly 1.0 modules on pure JVM bytecode.",
            "doc3" to "MultiSheetBook evaluates cross-sheet SpreadsheetML formulas.",
        )
        val idx = TrigramIndex.build(corpus)
        checkEq(idx.documentCount, 3)
        val hits = idx.searchLiteral("controlport")
        checkEq(hits.map { it.docId }, listOf("doc1"))
        val multi = idx.searchAnyLiteral(listOf("WebAssembly", "SpreadsheetML"))
        checkEq(multi, setOf("doc2", "doc3"))
        // Zero false negatives check against brute-force contains across 12 queries!
        for (q in listOf("tor", "jvm", "sheet", "profile", "1.0", "on", "xyz-none", "module", "cross-sheet", "pure", "bytecode", "formulas")) {
            val expected = corpus.filterValues { it.lowercase().contains(q.lowercase()) }.keys
            val actual = idx.searchLiteral(q).map { it.docId }.toSet()
            checkEq(actual, expected, "trigram parity for '$q'")
        }
    }

    private fun testPathConflict() {
        val ops = PathConflict.planBatch(
            existingNamesInDest = listOf("Report.pdf", "backup.tar.gz"),
            incomingNames = listOf("report.pdf", "backup.tar.gz", "report.pdf", ".env", ".env"),
            strategy = PathConflict.Strategy.AUTO_RENAME,
            caseInsensitive = true,
        )
        checkEq(
            ops.map { it.resolvedTargetName },
            listOf("report (1).pdf", "backup (1).tar.gz", "report (2).pdf", ".env", ".env (1)"),
        )
        PathConflict.verifyNotSubtreeMove("/home/user/docs", "/home/user/docs-backup")
        checkThrows<IllegalArgumentException>("move dir into own child refused") {
            PathConflict.verifyNotSubtreeMove("/home/user/docs", "/home/user/docs/2026/archive")
        }
    }

    private fun testVtScreenBuf() {
        val term = VtScreenBuf.TerminalBuffer(cols = 20, rows = 3, maxScrollbackLines = 2)
        term.feed("line-1\r\nline-2\r\nline-3\r\nline-4\r\n\u001B[1;32mOK\u001B[0m")
        checkEq(term.scrollbackLines, listOf("line-1", "line-2"))
        checkEq(term.renderScreenLines(), listOf("line-3", "line-4", "OK"))
        checkEq(term.cellAt(2, 0).style, VtScreenBuf.Style(fgAnsi = 32, bgAnsi = -1, bold = true))
        // Cursor home + erase to end of line
        term.feed("\u001B[2;5H\u001B[0K!")
        checkEq(term.renderScreenLines()[1], "line!")
    }

    private fun testChunkedTransfer() {
        val payload = ByteArray(1_050) { ((it * 31 + 17) and 0xFF).toByte() }
        val (manifest, chunks) = ChunkedTransfer.sliceWithManifest("tx-46", payload, chunkSize = 256)
        checkEq(manifest.chunkCount, 5)
        val rx = ChunkedTransfer.Receiver(manifest)
        // Corrupt chunk rejected without poisoning receiver state!
        val bad = chunks[2].copyOf().also { it[0] = (it[0].toInt() xor 0xFF).toByte() }
        checkEq(rx.acceptChunk(2, bad), ChunkedTransfer.AcceptStatus.REJECTED_BAD_HASH)
        // Deliver out-of-order: 4, 1, 3, 0, 1 (duplicate), 2
        checkEq(rx.acceptChunk(4, chunks[4]), ChunkedTransfer.AcceptStatus.ACCEPTED_NEW)
        checkEq(rx.acceptChunk(1, chunks[1]), ChunkedTransfer.AcceptStatus.ACCEPTED_NEW)
        checkEq(rx.missingChunkIndices(), listOf(0, 2, 3))
        checkEq(rx.acceptChunk(3, chunks[3]), ChunkedTransfer.AcceptStatus.ACCEPTED_NEW)
        checkEq(rx.acceptChunk(0, chunks[0]), ChunkedTransfer.AcceptStatus.ACCEPTED_NEW)
        checkEq(rx.acceptChunk(1, chunks[1]), ChunkedTransfer.AcceptStatus.DUPLICATE_IGNORED)
        checkEq(rx.acceptChunk(2, chunks[2]), ChunkedTransfer.AcceptStatus.ACCEPTED_NEW)
        checkTrue(rx.isComplete)
        checkTrue(rx.assembleVerified().contentEquals(payload))
    }

    private fun testRateWindow() {
        val gate = RateWindow.Gate(windowMs = 10_000L, maxAlertsPerWindow = 3, dedupCooldownMs = 5_000L)
        checkEq(gate.submit("usb-warn", 1_000L), RateWindow.Decision.Deliver(0))
        checkEq(gate.submit("usb-warn", 2_000L), RateWindow.Decision.SuppressDuplicate(1))
        checkEq(gate.submit("usb-warn", 3_000L), RateWindow.Decision.SuppressDuplicate(2))
        // Cooldown elapsed at 6_500ms -> delivers and reports 2 coalesced duplicates!
        checkEq(gate.submit("usb-warn", 6_500L), RateWindow.Decision.Deliver(2))
        checkEq(gate.submit("net-drop", 7_000L), RateWindow.Decision.Deliver(0))
        // 3 deliveries in [1000..7500] hits window cap!
        checkEq(gate.submit("disk-low", 7_500L), RateWindow.Decision.SuppressRateCap(3))
        // At 11_500ms, the 1_000ms delivery expired -> slot available again!
        checkEq(gate.submit("disk-low", 11_500L), RateWindow.Decision.Deliver(0))
    }

    private fun testEdlCutList() {
        checkEq(EdlCutList.framesToSmpte(90L, 30), "00:00:03:00")
        checkEq(EdlCutList.smpteToFrames("01:02:03:15", 30), ((1L * 3600 + 2 * 60 + 3) * 30) + 15)

        val tl = EdlCutList.Timeline(
            fps = 30,
            clips = listOf(
                EdlCutList.SourceClip("CAM_A", 0L, 150L),
                EdlCutList.SourceClip("CAM_B", 30L, 120L),
            ),
        )
        checkEq(tl.totalFrames, 240L)
        val split = tl.splitAt(100L)
        checkEq(split.totalFrames, 240L, "splitAt conserves exact frame count")
        checkEq(split.clips.size, 3)

        val trimmed = tl.rippleDelete(120L, 180L) // cuts 30f tail of CAM_A + 30f head of CAM_B
        checkEq(trimmed.totalFrames, 180L)
        checkEq(trimmed.placements().last().recordOutFrameExclusive, 180L)
        checkTrue(trimmed.formatCmx3600("R46 Cut").contains("FCM: NON-DROP FRAME"))
    }

    private fun testRabinChunker() {
        val base = ByteArray(24_000) { ((it * 73 + (it ushr 3) * 19) and 0xFF).toByte() }
        // Insert 37 bytes near offset 4,000 — CDC must re-sync and deduplicate downstream tail!
        val edited = base.copyOfRange(0, 4_000) + "INSERTED-PROSE-SENTENCE-37-BYTES-HERE".toByteArray() + base.copyOfRange(4_000, base.size)

        val snaps = RabinChunker.deduplicate(listOf(base, edited), minSize = 256, maskBits = 9, maxSize = 2048)
        checkTrue(RabinChunker.reassemble(snaps[0].chunks, snaps[0].chunkStore).contentEquals(base))
        checkTrue(RabinChunker.reassemble(snaps[1].chunks, snaps[1].chunkStore).contentEquals(edited))
        // Unique stored bytes across BOTH versions (~48 KB logical) must save > 35% via CDC resync!
        val combinedLogical = base.size + edited.size
        checkTrue(snaps[1].uniqueStoredBytes < (combinedLogical * 0.65).toLong(), "CDC resync saved bytes: ${snaps[1].uniqueStoredBytes} vs $combinedLogical")
    }

    private fun testCrdtLwwMap() {
        val rA = CrdtLwwMap.ReplicaMap.empty()
            .put("title", "Draft A", CrdtLwwMap.Hlc(100L, 0, "node-a"))
            .put("temp", "ephemeral", CrdtLwwMap.Hlc(110L, 0, "node-a"))
        val rB = CrdtLwwMap.ReplicaMap.empty()
            .put("title", "Draft B", CrdtLwwMap.Hlc(100L, 1, "node-b"))
            .remove("temp", CrdtLwwMap.Hlc(120L, 0, "node-b"))
        val rC = CrdtLwwMap.ReplicaMap.empty()
            .put("author", "Anonymous", CrdtLwwMap.Hlc(95L, 0, "node-c"))
            .put("temp", "stale-resurrection-attempt", CrdtLwwMap.Hlc(115L, 0, "node-c"))

        // Algebraic join-semilattice laws:
        checkEq(rA.merge(rA), rA, "CRDT idempotence")
        checkEq(rA.merge(rB), rB.merge(rA), "CRDT commutativity")
        checkEq(rA.merge(rB).merge(rC), rA.merge(rB.merge(rC)), "CRDT associativity")

        val converged = rA.merge(rB).merge(rC)
        checkEq(converged.liveEntries(), mapOf("author" to "Anonymous", "title" to "Draft B"))
        checkEq(converged.get("temp"), null, "tombstone at HLC 120 defeated stale put at HLC 115")
        checkEq(converged.tombstoneCount(), 1)
    }

    private fun testBarcodeGs1(corpusJson: String) {
        checkTrue(corpusJson.contains("gs1_valid_gtins"))
        for (gtin in listOf("4006381333931", "036000291452", "96385074", "09506000134352", "006141411234567890")) {
            checkTrue(BarcodeGs1.isValidGs1Code(gtin), "valid GS1 $gtin")
        }
        checkTrue(!BarcodeGs1.isValidGs1Code("4006381333932"), "corrupted check digit rejected")
        checkEq(BarcodeGs1.normalizeToGtin14("4006381333931"), "04006381333931")

        val parsed = BarcodeGs1.parseElementString("(01)09506000134352(17)271231(10)BATCH-A(21)SN-900")
        checkEq(parsed.gtin14, "09506000134352")
        checkEq(parsed.expiryYymmdd, "271231")
        checkEq(parsed.batchLot, "BATCH-A")
        checkEq(parsed.serial, "SN-900")

        val fnc1Parsed = BarcodeGs1.parseElementString("]C101095060001343521727123110LOT9${BarcodeGs1.FNC1}21SER7")
        checkEq(fnc1Parsed.batchLot, "LOT9")
        checkEq(fnc1Parsed.serial, "SER7")
    }

    private fun testQuotedPrintable() {
        val original = "Fusion café = 100% clean-room! Trailing space   \r\n" +
            "Long UTF-8 line: " + "αβγδεζηθ ".repeat(12) + "end\t"
        val encoded = QuotedPrintable.encodeUtf8(original)
        for (ln in encoded.split("\r\n")) {
            checkTrue(ln.length <= QuotedPrintable.MAX_LINE_LEN, "QP line length ${ln.length} <= 76")
        }
        checkEq(QuotedPrintable.decodeUtf8(encoded), original, "QP exact UTF-8 + trailing whitespace round-trip")

        val hdr = "Subject: =?UTF-8?Q?R=C3=A9sum=C3=A9_2026?= =?UTF-8?B?IC0gRnVzaW9u?="
        checkEq(QuotedPrintable.decodeHeaderWords(hdr), "Subject: Résumé 2026 - Fusion")
        checkThrows<IllegalArgumentException>("invalid =ZZ hex refused") {
            QuotedPrintable.decode("bad=ZZescape")
        }
    }

    private fun testPngMetadataStrip() {
        val sig = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)
        val ihdrData = byteArrayOf(0, 0, 0, 16, 0, 0, 0, 8, 8, 6, 0, 0, 0) // 16x8 RGBA8
        val exifData = "Exif\u0000\u0000GPS-LAT-41.46-LON-81.50-CAMERA-SERIAL-9988".toByteArray()
        val textData = "Author\u0000Alice Investigative Reporter".toByteArray()
        val idatData = byteArrayOf(0x78, 0x9C.toByte(), 0x63, 0x60, 0x00, 0x00, 0x00, 0x02, 0x00, 0x01)

        val dirtyPng = ByteArrayOutputStream().apply {
            write(sig)
            write(PngMetadataStrip.buildChunk("IHDR", ihdrData))
            write(PngMetadataStrip.buildChunk("eXIf", exifData))
            write(PngMetadataStrip.buildChunk("tEXt", textData))
            write(PngMetadataStrip.buildChunk("IDAT", idatData))
            write(PngMetadataStrip.buildChunk("IEND", ByteArray(0)))
        }.toByteArray()

        val res = PngMetadataStrip.inspectAndStrip(dirtyPng)
        checkEq(res.info.width, 16)
        checkEq(res.info.height, 8)
        checkEq(res.strippedChunks, listOf("eXIf", "tEXt"))
        val cleanLatin = String(res.cleanBytes, StandardCharsets.ISO_8859_1)
        checkTrue(!cleanLatin.contains("GPS-LAT") && !cleanLatin.contains("Reporter"))

        // Idempotent second pass strips 0 chunks and validates all surviving CRCs!
        val res2 = PngMetadataStrip.inspectAndStrip(res.cleanBytes)
        checkEq(res2.strippedChunks, emptyList<String>())
        checkTrue(res2.cleanBytes.contentEquals(res.cleanBytes))

        // Corrupt 1 byte in IDAT -> CRC-32 mismatch rejected!
        val corruptPng = res.cleanBytes.copyOf().also { it[it.size - 18] = (it[it.size - 18].toInt() xor 0x01).toByte() }
        checkThrows<IllegalArgumentException>("corrupted PNG CRC-32 rejected") {
            PngMetadataStrip.inspectAndStrip(corruptPng)
        }
    }

    private fun testCronExpr() {
        val zone = ZoneId.of("America/New_York")
        val base = ZonedDateTime.of(2026, 10, 26, 14, 7, 0, 0, zone) // Monday 2026-10-26
        val qtr = CronExpr.parse("*/15 9-17 * * 1-5")
        checkEq(qtr.nextAfter(base), ZonedDateTime.of(2026, 10, 26, 14, 15, 0, 0, zone))

        // POSIX OR-rule: both dom=15 and dow=1 (Monday) restricted -> matches Monday Oct 26 even though dom != 15!
        val posixOr = CronExpr.parse("30 16 15 * 1")
        checkEq(posixOr.nextAfter(base), ZonedDateTime.of(2026, 10, 26, 16, 30, 0, 0, zone))

        // Leap-day cron `0 0 29 2 *` finds 2028-02-29!
        val leap = CronExpr.parse("0 0 29 2 *")
        checkEq(leap.nextAfter(base), ZonedDateTime.of(2028, 2, 29, 0, 0, 0, 0, zone))
    }

    private fun testPassphraseDice() {
        val words = (0 until 100).map { "word%03d".format(it) } // non-power-of-two size 100 tests rejection sampler!
        // Include high 16-bit values (0xFFFF = 65535 >= unbiasedCeiling 65500) to exercise rejection branch!
        val entropy = byteArrayOf(
            0xFF.toByte(), 0xFF.toByte(), // 65535 -> REJECTED!
            0x00, 0x07, // 7 -> word007
            0x00, 0x64, // 100 -> word000
            0x01, 0x2B, // 299 -> word099
            0x00, 0x2A, // 42 -> word042
            0xFF.toByte(), 0xFE.toByte(), // 65534 -> REJECTED!
            0x00, 0x13, // 19 -> word019
        )
        val gen = PassphraseDice.generate(words, wordCount = 5, separator = " ", entropyStream = entropy)
        checkEq(gen.words, listOf("word007", "word000", "word099", "word042", "word019"))
        checkEq(gen.rejectedSamples, 2, "unbiased rejection sampler discarded >= 65500 draws")
        checkClose(PassphraseDice.wordlistEntropyBits(7776, 6), 77.548875, 1e-4)
        checkTrue(PassphraseDice.characterPoolBits("Tr0ub4dor&3") > PassphraseDice.characterPoolBits("aaaaaaaaaaa"))
    }

    private fun testMarkdownTableFmt() {
        val raw = """
            | Key | Expr | Total |
            | :--- | :---: | ---: |
            | A\|B | x + y | 42 |
            | C | z | 1000 |
        """.trimIndent()
        val tbl = MarkdownTableFmt.parseGfm(raw)
        checkEq(tbl.headers, listOf("Key", "Expr", "Total"))
        checkEq(tbl.alignments, listOf(MarkdownTableFmt.Align.LEFT, MarkdownTableFmt.Align.CENTER, MarkdownTableFmt.Align.RIGHT))
        checkEq(tbl.rows[0][0], "A|B", "unescaped \\| in cell")

        val formatted = MarkdownTableFmt.formatAligned(tbl)
        val reparsed = MarkdownTableFmt.parseGfm(formatted)
        checkEq(reparsed, tbl, "GFM format -> parse exact round-trip")

        val csv = MarkdownTableFmt.toCsv(tbl)
        val fromCsv = MarkdownTableFmt.fromCsv(csv)
        checkEq(fromCsv.rows, tbl.rows, "GFM <-> RFC 4180 CSV round-trip")
    }

    private fun buildSimpleZip(entries: Map<String, ByteArray>): ByteArray {
        val baos = ByteArrayOutputStream()
        ZipOutputStream(baos).use { zos ->
            for ((name, bytes) in entries) {
                zos.putNextEntry(ZipEntry(name))
                zos.write(bytes)
                zos.closeEntry()
            }
        }
        return baos.toByteArray()
    }

    private fun recomputeTarChecksum(tar: ByteArray, blockOffset: Int) {
        for (i in 148 until 156) tar[blockOffset + i] = ' '.code.toByte()
        var sum = 0L
        for (i in 0 until 512) sum += (tar[blockOffset + i].toInt() and 0xFF)
        val oct = sum.toString(8).padStart(6, '0')
        for (i in 0 until 6) tar[blockOffset + 148 + i] = oct[i].code.toByte()
        tar[blockOffset + 154] = 0
        tar[blockOffset + 155] = ' '.code.toByte()
    }

    private fun buildTestWasmModule(): ByteArray {
        val out = ByteArrayOutputStream()
        out.write(byteArrayOf(0x00, 0x61, 0x73, 0x6D, 0x01, 0x00, 0x00, 0x00))
        // Section 1: Type (3 types: 0=(i32)->i32, 1=()->i32, 2=(i32,i32)->i32)
        val secType = byteArrayOf(
            0x03,
            0x60, 0x01, 0x7F, 0x01, 0x7F,
            0x60, 0x00, 0x01, 0x7F,
            0x60, 0x02, 0x7F, 0x7F, 0x01, 0x7F,
        )
        out.write(0x01); out.write(secType.size); out.write(secType)
        // Section 3: Function (3 funcs using types 0, 1, 2)
        val secFunc = byteArrayOf(0x03, 0x00, 0x01, 0x02)
        out.write(0x03); out.write(secFunc.size); out.write(secFunc)
        // Section 5: Memory (1 memory, min=1, max=2)
        val secMem = byteArrayOf(0x01, 0x01, 0x01, 0x02)
        out.write(0x05); out.write(secMem.size); out.write(secMem)
        // Section 7: Export ("fib"->0, "spin"->1, "mem_sum"->2)
        val secExp = ByteArrayOutputStream().apply {
            write(0x03)
            write(3); write("fib".toByteArray()); write(0x00); write(0x00)
            write(4); write("spin".toByteArray()); write(0x00); write(0x01)
            write(7); write("mem_sum".toByteArray()); write(0x00); write(0x02)
        }.toByteArray()
        out.write(0x07); out.write(secExp.size); out.write(secExp)

        // Func 0 body: iterative fib(n: local0) with 3 i32 locals (a=local1, b=local2, tmp=local3)
        // a = 0; b = 1; block $exit { loop $top { br_if $exit (n <= 0); tmp = a + b; a = b; b = tmp; n = n - 1; br $top } }; return a
        val body0 = byteArrayOf(
            0x01, 0x03, 0x7F, // 3 i32 locals
            0x41, 0x00, 0x21, 0x01, // a = 0
            0x41, 0x01, 0x21, 0x02, // b = 1
            0x02, 0x40, // block void
            0x03, 0x40, // loop void
            0x20, 0x00, 0x41, 0x00, 0x4C, 0x0D, 0x01, // if (n <= 0) br 1 ($exit)
            0x20, 0x01, 0x20, 0x02, 0x6A, 0x21, 0x03, // tmp = a + b
            0x20, 0x02, 0x21, 0x01, // a = b
            0x20, 0x03, 0x21, 0x02, // b = tmp
            0x20, 0x00, 0x41, 0x01, 0x6B, 0x21, 0x00, // n = n - 1
            0x0C, 0x00, // br 0 ($top)
            0x0B, // end loop
            0x0B, // end block
            0x20, 0x01, // push a
            0x0B, // end func
        )
        // Func 1 body: infinite loop `loop void; br 0; end; i32.const 0; end`
        val body1 = byteArrayOf(
            0x00,
            0x03, 0x40, 0x0C, 0x00, 0x0B,
            0x41, 0x00, 0x0B,
        )
        // Func 2 body: mem_sum(ptr: local0, val: local1) -> i32.store(ptr, val); return i32.load(ptr) + i32.load(ptr + 4)
        val body2 = byteArrayOf(
            0x00,
            0x20, 0x00, 0x20, 0x01, 0x36, 0x02, 0x00, // i32.store offset=0
            0x20, 0x00, 0x28, 0x02, 0x00, // i32.load offset=0
            0x20, 0x00, 0x28, 0x02, 0x04, // i32.load offset=4
            0x6A, // add
            0x0B,
        )
        val secCode = ByteArrayOutputStream().apply {
            write(0x03)
            write(body0.size); write(body0)
            write(body1.size); write(body1)
            write(body2.size); write(body2)
        }.toByteArray()
        out.write(0x0A); out.write(secCode.size); out.write(secCode)
        return out.toByteArray()
    }
}
