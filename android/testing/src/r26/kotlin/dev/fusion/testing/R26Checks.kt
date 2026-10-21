// SPDX-License-Identifier: Apache-2.0
package dev.fusion.testing

import dev.fusion.designsystem.BeautyStandard
import dev.fusion.designsystem.DesktopMetrics
import dev.fusion.designsystem.ProfessionalPalettes
import dev.fusion.fileops.FileMoveJournal
import dev.fusion.fileops.JournalStage
import dev.fusion.ime.HardenedPinBuffer
import dev.fusion.ime.HidBurstClassifier
import dev.fusion.ime.HidEventSample
import dev.fusion.ime.HidThreatVerdict
import dev.fusion.ime.ScrambledKeypadLayout
import dev.fusion.ime.TimingQuantizer
import dev.fusion.launcher.TabletopProjection
import dev.fusion.maps.GeoJsonGpxCodec
import dev.fusion.maps.GeoPoint
import dev.fusion.maps.MapPin
import dev.fusion.maps.OfflineGazetteer
import dev.fusion.media.EmbeddedStreamContract
import dev.fusion.media.MediaService
import dev.fusion.media.ServiceLink
import dev.fusion.model.PilotAccess
import dev.fusion.model.PilotPolicy
import dev.fusion.office.OfficeSessionController
import dev.fusion.photos.PhotoMetadataCleaner
import dev.fusion.poi.CellValue
import dev.fusion.poi.CleanRoomPoiEngine
import dev.fusion.poi.CleanWorkbook
import dev.fusion.poi.DocxDocument
import dev.fusion.poi.DocxParagraph
import dev.fusion.poi.SheetCell
import dev.fusion.poi.SlideDeck
import dev.fusion.poi.WorkbookSheet
import dev.fusion.quickpanel.QuickPanelSnapshot
import dev.fusion.quickpanel.StudioPreset
import dev.fusion.security.AmnesicSessionVault
import dev.fusion.security.MetadataScrubber
import dev.fusion.security.TorTransportPolicy
import dev.fusion.settings.Appearance
import dev.fusion.settings.ProfessionalPalette
import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.security.SecureRandom
import kotlin.math.abs

object R26Checks {
    private var assertions = 0

    private fun ok(condition: Boolean, detail: String = "Assertion failed") {
        if (!condition) throw AssertionError(detail)
        assertions++
    }

    private fun refuses(block: () -> Unit) {
        val failed = try {
            block()
            false
        } catch (_: Throwable) {
            true
        }
        ok(failed, "Expected failure but call succeeded")
    }

    @JvmStatic
    fun main(args: Array<String>) {
        // Optional interop mode `--interop <dir>`: exchange real DOCX/XLSX/PPTX/JPEG/PDF files with external Python tools.
        if (args.size == 2 && args[0] == "--interop") {
            runInteropExchange(Path.of(args[1]))
            println("R26 INTEROP EXCHANGE PASS: $assertions assertions")
            return
        }
        checkFileMoveJournal()
        checkCleanRoomPoiAndOffice()
        checkPhotosJpegCleaner()
        checkHardenedImeAndTiming()
        checkAmnesicVaultTorAndPdfScrubber()
        checkBeautyPalettesDockAndTabletop()
        checkEmbeddedStreamsAndOfflineAtlas()
        checkGateHonesty()
        println("R26 PASS: $assertions production-kernel assertions across 6 new modules + security + beauty + media + maps.")
    }

    private fun runInteropExchange(dir: Path) {
        // 1. Read external openpyxl/XlsxWriter workbook written by Python and verify exact formula evaluation:
        val externalXlsx = Files.readAllBytes(dir.resolve("python-input.xlsx"))
        val wb = CleanRoomPoiEngine.readXlsx(externalXlsx)
        val evaluated = CleanRoomPoiEngine.evaluateWorkbook(wb).getValue("Metrics")
        ok(evaluated["B4"] == CellValue.Number(157.5), "External openpyxl workbook SUM mismatch: ${evaluated["B4"]}")
        ok(evaluated["B5"] == CellValue.Number(52.5), "External openpyxl workbook AVERAGE mismatch: ${evaluated["B5"]}")

        // 2. Write Kotlin-produced XLSX, DOCX, PPTX for independent Python openpyxl / zipfile / XML readback:
        val outWb = CleanWorkbook(
            listOf(
                WorkbookSheet(
                    "Audit",
                    mapOf(
                        "A1" to SheetCell("A1", CellValue.Number(42.0)),
                        "A2" to SheetCell("A2", CellValue.Number(58.0)),
                        "A3" to SheetCell("A3", CellValue.Blank, "SUM(A1:A2)"),
                        "B1" to SheetCell("B1", CellValue.Text("Clean-Room Apache-2.0"))
                    )
                )
            )
        )
        Files.write(dir.resolve("kotlin-output.xlsx"), CleanRoomPoiEngine.writeXlsx(outWb))
        Files.write(
            dir.resolve("kotlin-output.docx"),
            CleanRoomPoiEngine.writeDocx(
                DocxDocument(
                    paragraphs = listOf(
                        DocxParagraph("R26 Interop Heading", headingLevel = 1),
                        DocxParagraph("Verified independently in Python without shared code.")
                    ),
                    tables = listOf(listOf(listOf("Key", "State"), listOf("Macros", "Refused")))
                )
            )
        )
        Files.write(
            dir.resolve("kotlin-output.pptx"),
            CleanRoomPoiEngine.writePptx(
                SlideDeck(listOf(SlideDeck.SlidePage(1, "Interop Slide", listOf("Bullet 1", "Bullet 2"))))
            )
        )

        // 3. Clean a real Pillow-encoded JPEG containing EXIF GPS tags and write cleaned copy for Pillow pixel comparison:
        val dirtyJpeg = Files.readAllBytes(dir.resolve("pillow-dirty-exif.jpg"))
        val cleanJpeg = PhotoMetadataCleaner.inspectAndCleanJpeg(dirtyJpeg)
        ok(cleanJpeg.removedMarkers.any { it.startsWith("APP1") }, "Expected APP1 EXIF removal on real Pillow JPEG")
        Files.write(dir.resolve("kotlin-cleaned.jpg"), cleanJpeg.bytes)

        // 4. Scrub a real pypdf-generated PDF containing Author/Producer/XMP metadata and write cleaned PDF for pypdf check:
        val dirtyPdf = Files.readAllBytes(dir.resolve("pypdf-dirty.pdf"))
        val scrubbedPdf = MetadataScrubber.scrubPdfMetadata(dirtyPdf)
        ok(scrubbedPdf.scrubbedFields.containsAll(listOf("Author", "Producer")), "Expected Author/Producer scrub on real PDF")
        Files.write(dir.resolve("kotlin-cleaned.pdf"), scrubbedPdf.bytes)
    }

    private fun checkFileMoveJournal() {
        val dir = Files.createTempDirectory("r26-journal-")
        try {
            val journal = FileMoveJournal(dir)
            val data = ByteArray(4096) { (it * 31 + 7).toByte() }
            val a = dir.resolve("src.bin").also { Files.write(it, data) }
            val b = dir.resolve("sub/dst.bin")
            refuses {
                journal.moveVerified("crash_prepared", a, b) { st ->
                    if (st == JournalStage.PREPARED) error("boom")
                }
            }
            val r1 = journal.recover()
            ok(r1.rolledBack == listOf("crash_prepared") && Files.exists(a) && !Files.exists(b))
            ok(Files.readAllBytes(a).contentEquals(data))

            refuses {
                journal.moveVerified("crash_verified", a, b) { st ->
                    if (st == JournalStage.COPY_VERIFIED) error("boom2")
                }
            }
            val r2 = journal.recover()
            ok(r2.rolledForward == listOf("crash_verified") && !Files.exists(a) && Files.exists(b))
            ok(Files.readAllBytes(b).contentEquals(data))
            refuses { journal.saveVerifiedCopy(dir.resolve("../outside.bin"), data) }
        } finally {
            Files.walk(dir).sorted(Comparator.reverseOrder()).forEach { Files.deleteIfExists(it) }
        }
    }

    private fun checkCleanRoomPoiAndOffice() {
        val office = OfficeSessionController()
        ok(office.state.evaluatedCells()["B1"] == CellValue.Number(250.0))
        office.updateCellInput("C1", "=IF(B1>200, ROUND(B1/7, 3), 0)")
        ok(office.state.evaluatedCells()["C1"] == CellValue.Number(35.714))
        ok(office.undo() && office.state.evaluatedCells()["C1"] == null)
        ok(office.redo() && office.state.evaluatedCells()["C1"] == CellValue.Number(35.714))

        val xlsx = office.state.exportXlsxBytes()
        val reimported = OfficeSessionController().apply { importOoxmlBytes("sheet.xlsx", xlsx) }
        ok(reimported.state.evaluatedCells()["C1"] == CellValue.Number(35.714))

        // Verify exact multi-sheet round-trip & cycle/div0 formulas from CleanRoomPoiEngineTest
        val multi = CleanWorkbook(
            listOf(
                WorkbookSheet("Inputs", mapOf("A1" to SheetCell("A1", CellValue.Number(12.5)), "A2" to SheetCell("A2", CellValue.Number(7.5)), "A3" to SheetCell("A3", CellValue.Number(30.0)))),
                WorkbookSheet("Summary", mapOf("B1" to SheetCell("B1", CellValue.Blank, "SUM(Inputs!A1:A3)"), "B2" to SheetCell("B2", CellValue.Blank, "IF(B1>=50, ROUND(B1/3, 2), 0)"), "C1" to SheetCell("C1", CellValue.Blank, "C2+1"), "C2" to SheetCell("C2", CellValue.Blank, "C1+1"), "D1" to SheetCell("D1", CellValue.Blank, "10/0")))
            )
        )
        val multiEval = CleanRoomPoiEngine.evaluateWorkbook(CleanRoomPoiEngine.readXlsx(CleanRoomPoiEngine.writeXlsx(multi))).getValue("Summary")
        ok(multiEval["B1"] == CellValue.Number(50.0), "Multi-sheet B1 failed: ${multiEval["B1"]}")
        ok(multiEval["B2"] == CellValue.Number(16.67), "Multi-sheet B2 failed: ${multiEval["B2"]}")
        ok(multiEval["C1"] == CellValue.Error("#CYCLE!"), "Multi-sheet C1 failed: ${multiEval["C1"]}")
        ok(multiEval["D1"] == CellValue.Error("#DIV/0!"), "Multi-sheet D1 failed: ${multiEval["D1"]}")
    }

    private fun checkPhotosJpegCleaner() {
        val out = ByteArrayOutputStream()
        fun seg(m: Int, body: ByteArray) {
            out.write(0xFF); out.write(m)
            val l = body.size + 2
            out.write((l shr 8) and 0xFF); out.write(l and 0xFF); out.write(body)
        }
        out.write(0xFF); out.write(0xD8)
        seg(0xE1, "Exif\u0000\u0000SecretLatLon".toByteArray())
        seg(0xFE, "AuthorComment".toByteArray())
        seg(0xDB, ByteArray(65) { 2 })
        seg(0xC0, byteArrayOf(8, 0, 8, 0, 8, 3, 1, 0x11, 0, 2, 0x11, 1, 3, 0x11, 1))
        seg(0xC4, ByteArray(29) { 1 })
        seg(0xDA, byteArrayOf(3, 1, 0, 2, 0x11, 3, 0x11, 0, 63, 0))
        out.write(byteArrayOf(0x12, 0xFF.toByte(), 0x00, 0x34, 0xFF.toByte(), 0xD9.toByte()))
        val cleaned = PhotoMetadataCleaner.inspectAndCleanJpeg(out.toByteArray())
        ok(cleaned.width == 8 && cleaned.height == 8)
        ok("SecretLatLon" !in String(cleaned.bytes, StandardCharsets.ISO_8859_1))
    }

    private fun checkHardenedImeAndTiming() {
        val rng = SecureRandom(byteArrayOf(1, 2, 3, 4))
        repeat(200) {
            val perm = ScrambledKeypadLayout.permute(ScrambledKeypadLayout.DIGITS, rng)
            ok(perm.size == 10 && perm.toSet().size == 10)
        }
        val pad = HardenedPinBuffer(maxLength = 6)
        var t = 500_000_000L
        for (digit in "8305") {
            t += 120_000_000L
            ok(pad.pressSlot(pad.keys.indexOf(digit), HidEventSample(t, "touch-0")) == HidThreatVerdict.CLEAN)
        }
        ok(String(pad.consumeAndZeroize()) == "8305" && pad.length == 0)

        // Attack: BadUSB <8ms burst wipes any partial PIN immediately, latches quarantine, and blocks follow-on strokes
        val attacked = HardenedPinBuffer(maxLength = 6)
        repeat(8) { idx ->
            attacked.pressSlot(0, HidEventSample(1_000_000L * idx, "badusb-ducky"))
        }
        ok(attacked.quarantined && attacked.length == 0 && attacked.verdict == HidThreatVerdict.QUARANTINED_SUPERHUMAN_CADENCE)
        refuses { attacked.consumeAndZeroize() }

        val sched = TimingQuantizer.paddedSchedule(listOf(5_000_000L, 6_000_000L, 7_000_000L), totalSlots = 8)
        ok(sched.size == 8 && sched.zipWithNext().all { (a, b) -> b.first - a.first == TimingQuantizer.DEFAULT_QUANTUM_NANOS })
        ok(HidBurstClassifier.classify(listOf(HidEventSample(10L, "kbd", obscuredWindow = true))) == HidThreatVerdict.QUARANTINED_OBSCURED_OVERLAY)
    }

    private fun checkAmnesicVaultTorAndPdfScrubber() {
        val vault = AmnesicSessionVault()
        val secret = "SUPER_SECRET_SESSION_KEY_2026".toByteArray(StandardCharsets.UTF_8)
        val e1 = vault.putSecret("tor-auth", secret, wipeCallerArray = true)
        ok(secret.all { it == 0.toByte() }, "Caller plaintext array must be zeroized in place")
        ok(!vault.containsRawSubsequence("SUPER_SECRET".toByteArray(StandardCharsets.UTF_8)))
        ok(String(vault.readSecret("tor-auth", e1), StandardCharsets.UTF_8) == "SUPER_SECRET_SESSION_KEY_2026")
        val e2 = vault.panicWipe()
        ok(e2 == e1 + 1 && vault.activeSlotCount == 0)
        refuses { vault.readSecret("tor-auth", e1) }
        refuses { vault.readSecret("tor-auth", e2) }

        // Tor transport policy: socks5h loopback required; socks5 local DNS refused; clearnet HTTP refused
        val validOnion = "http://" + "a".repeat(56) + ".onion/index.html"
        ok(!TorTransportPolicy.evaluate("https://example.org", null).admitted)
        ok(!TorTransportPolicy.evaluate("https://example.org", "socks5://127.0.0.1:9050").admitted)
        ok(!TorTransportPolicy.evaluate("http://example.org", "socks5h://127.0.0.1:9050").admitted)
        ok(TorTransportPolicy.evaluate("https://example.org", "socks5h://127.0.0.1:9050").admitted)
        ok(TorTransportPolicy.evaluate(validOnion, "socks5h://127.0.0.1:9050", strictOnionOnly = true).admitted)
        ok(!TorTransportPolicy.evaluate("https://example.org", "socks5h://127.0.0.1:9050", strictOnionOnly = true).admitted)

        // Trojan-source bidi + zero-width scrub
        val trojan = "safe\u202Eevil\u200Btext"
        val cleanText = MetadataScrubber.scrubText(trojan)
        ok(cleanText.cleanText == "safeeviltext" && cleanText.removedCodePoints == 2)
    }

    private fun checkBeautyPalettesDockAndTabletop() {
        ok(TabletopProjection.TABLE_AREA > BeautyStandard.MIN_FLOOR_SCENE_SHARE)
        for (p in ProfessionalPalette.entries) {
            val audit = BeautyStandard.auditPalette(ProfessionalPalettes.colors(p))
            ok(audit.passesWcag22Aa, "WCAG 2.2 AA failed for $p: $audit")
            ok(audit.passesApcaLabels, "APCA Lc failed for $p: $audit")
        }
        val d = DesktopMetrics.dock(1280.0, 1.0, false, 20)
        ok(d.slot >= 72 && d.viewport < 1280.0 * 0.60)
        val qp = QuickPanelSnapshot(Appearance(), 3, 2, 0).applyPreset(StudioPreset.ART_SILVER).triggerAmnesicPurge()
        ok(qp.appearance.palette == ProfessionalPalette.STUDIO_SILVER && qp.activeConsentCount == 0)
    }

    private fun checkEmbeddedStreamsAndOfflineAtlas() {
        val yt = EmbeddedStreamContract.toOfficialEmbed(ServiceLink.parse("https://youtu.be/abcdefghijk?si=track"))
        ok(yt.embedUrl.startsWith("https://www.youtube-nocookie.com/embed/abcdefghijk"))
        ok(EmbeddedStreamContract.isHostAllowedForEmbed(yt, "www.youtube-nocookie.com"))
        ok(!EmbeddedStreamContract.isHostAllowedForEmbed(yt, "evil-tracker.example"))
        val sp = EmbeddedStreamContract.toOfficialEmbed(ServiceLink.parse("https://open.spotify.com/track/0123456789ABCDEFGHIJKL"))
        ok(sp.embedUrl == "https://open.spotify.com/embed/track/0123456789ABCDEFGHIJKL")
        val sc = EmbeddedStreamContract.toOfficialEmbed(ServiceLink.parse("https://soundcloud.com/artist/track"))
        ok(sc.embedUrl.startsWith("https://w.soundcloud.com/player/?url="))
        val direct = EmbeddedStreamContract.parseDirectOpenStream("https://archive.example.org/pub/lecture.opus")
        ok(direct.mediaKind == "audio")
        refuses { EmbeddedStreamContract.parseDirectOpenStream("http://archive.example.org/pub/lecture.mp3") }

        val raleigh = OfflineGazetteer.search("Raleigh").first()
        val london = OfflineGazetteer.search("London").first()
        val distKm = OfflineGazetteer.routeMeters(listOf(raleigh.point, london.point)) / 1000.0
        ok(distKm in 6150.0..6350.0, "Unexpected Raleigh->London great-circle km: $distKm")
        val bearing = OfflineGazetteer.forwardBearingDegrees(raleigh.point, london.point)
        ok(bearing in 45.0..60.0, "Unexpected NE initial azimuth Raleigh->London: $bearing")
        val poly = OfflineGazetteer.sphericalPolygonSquareKm(
            listOf(GeoPoint(0.0, 0.0), GeoPoint(0.0, 1.0), GeoPoint(1.0, 1.0), GeoPoint(1.0, 0.0))
        )
        ok(abs(poly - 12_364.0) < 150.0, "1x1 deg equatorial square area mismatch: $poly")
        val pins = listOf(MapPin(1, "Raleigh", raleigh.point), MapPin(2, "London", london.point))
        val gpx = GeoJsonGpxCodec.exportGpx(pins)
        val roundTrip = GeoJsonGpxCodec.importGpxWaypoints(gpx)
        ok(roundTrip.size == 2 && roundTrip[0].first == "Raleigh")
    }

    private fun checkGateHonesty() {
        // Negative-space modal shortcut detector: full-spec unverified gates MUST stay BLOCKED.
        for (blockedId in listOf("browser", "docs", "pdf", "vault", "beam", "backup", "together")) {
            ok(PilotPolicy.module(blockedId).access == PilotAccess.BLOCKED, "Gate shortcut violation on $blockedId")
            ok(!PilotPolicy.mayRun(blockedId, setOf(blockedId, "browser-workbench", "office", "pdf-preview")))
        }
        // Bounded clean-room alternatives require explicit per-session consent:
        for (consentedId in listOf("office", "photos", "browser-workbench", "media", "maps")) {
            ok(PilotPolicy.module(consentedId).access == PilotAccess.CONSENT_REQUIRED)
            ok(!PilotPolicy.mayRun(consentedId, emptySet()))
            ok(PilotPolicy.mayRun(consentedId, setOf(consentedId)))
        }
    }
}
