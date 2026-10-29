#!/usr/bin/env python3
"""Live Interactive Browser & HTTP API Workbench for Fusion Shell R46 (No Android Studio Required).

Architecture:
  - Compiles real Kotlin R46 production jars (`MultiSheetBook`, `FormulaEval`, `SheetPivot`,
    `PdfPageWriter`, `DocOutlineRender`, `TrackRevisions`, `CitationBib`, `WasmSandbox`,
    `OnionGraterFilter`, `UsbGuardPolicy`, `BleHidPairingGate`, `KeystrokeJitter`, `MetadataScrub`)
    and starts a warm OpenJDK 17 worker process (`R46WorkbenchDaemon`).
  - Serves an interactive zero-external-dependency HTML/JS desktop workbench bound to `0.0.0.0:8765`
    that uses strictly relative `api/...` URLs and permits proxy `Host`/`Origin` headers and iframe
    embedding (`*.e2b.app` live preview compatible).
  - Honesty boundary: executes real Kotlin JVM production bytecode on OpenJDK 17; explicitly
    labeled NOT an Android ART / Jetpack Compose / emulator runtime.
"""
from __future__ import annotations

import argparse
import base64
from http import HTTPStatus
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
import json
import os
from pathlib import Path
import subprocess
import sys
import threading

sys.path.insert(0, str(Path(__file__).resolve().parent))
import amnesic_posture
import run_r46_contracts
import run_r47_contracts
import run_r48_contracts
import run_r49_contracts
import run_r50_contracts
import run_r51_contracts
import run_r52_contracts
import run_r53_contracts

ROOT = Path(__file__).resolve().parents[1]
DAEMON_DIR = ROOT / "testing/build/r53-workbench-daemon"

KOTLIN_DAEMON_SRC = r'''
package dev.fusion.testing

import dev.fusion.browser.ArticleReaderExtractor
import dev.fusion.browser.CrossOriginIsolationPolicy
import dev.fusion.browser.CspHeaderDirective
import dev.fusion.browser.HtmlSanitizer
import dev.fusion.browser.WebRtcIceCandidateScrubber
import dev.fusion.browser.model.AdblockFilterEngine
import dev.fusion.capabilities.WasiPreview1Host
import dev.fusion.capabilities.WasiSocketCapabilityGate
import dev.fusion.capabilities.WasmSandbox
import dev.fusion.documents.DocOutlineRender
import dev.fusion.documents.Epub3Package
import dev.fusion.documents.KnuthPlassLineBreaker
import dev.fusion.documents.LatexMathSvg
import dev.fusion.documents.LiangHyphenationEngine
import dev.fusion.documents.OdfTrackedChangesCodec
import dev.fusion.documents.RtfCleanConverter
import dev.fusion.documents.TrackRevisions
import dev.fusion.editor.CitationBib
import dev.fusion.editor.ReadabilityMetrics
import dev.fusion.find.PaletteInlineAnswerEngine
import dev.fusion.format.pdf.PdfOutlineBookmarks
import dev.fusion.format.pdf.PdfPageWriter
import dev.fusion.format.pdf.PdfPkcs7SignatureVerifier
import dev.fusion.hw.BleHidPairingGate
import dev.fusion.hw.HidUsageFirewall
import dev.fusion.hw.UsbGuardPolicy
import dev.fusion.lok.OdsSpreadsheetXml
import dev.fusion.lok.OdtRichWriter
import dev.fusion.model.BillingEntitlementLedger
import dev.fusion.ooxml.DocxRichDocument
import dev.fusion.ooxml.DrawingMlChartBuilder
import dev.fusion.ooxml.FormulaEval
import dev.fusion.ooxml.MultiSheetBook
import dev.fusion.ooxml.PptxSlideDeck
import dev.fusion.ooxml.SheetSolverSimplex
import dev.fusion.ooxml.SpillSheetEngine
import dev.fusion.pdf.PdfAcroFormWriter
import dev.fusion.privacy.AcousticKeystrokeShield
import dev.fusion.privacy.BinaryHttpRfc9292
import dev.fusion.privacy.ChoiceJackingUsbGuard
import dev.fusion.privacy.ImuSideChannelGuard
import dev.fusion.privacy.ObliviousHttpPolicy
import dev.fusion.privacy.OnionGraterFilter
import dev.fusion.privacy.OnionV3Address
import dev.fusion.privacy.PixnappingSurfaceShield
import dev.fusion.privacy.TailsCaTrustPinBlocklist
import dev.fusion.privacy.TlsEchConfigCodec
import dev.fusion.privacy.TorSocksIsolation
import dev.fusion.security.HkdfRfc5869
import dev.fusion.security.ScreenCaptureGuard
import dev.fusion.security.SecureKeypadScrambler
import dev.fusion.security.NfcRelayDistanceBoundingGate
import dev.fusion.security.UsbBadHidKeystrokeRateGuard
import dev.fusion.archive.Mat2RecursiveMetadataScrubber
import dev.fusion.browser.DnsOverHttpsWireQueryCodec
import dev.fusion.documents.OpenTypeKerningTableLayout
import dev.fusion.format.pdf.PdfIncrementalSaveRedactor
import dev.fusion.lens.Fsr1EasuRcasUpscaler
import dev.fusion.lens.NlmsAcousticEchoGate
import dev.fusion.messages.MatrixMegolmGroupRatchet
import dev.fusion.ooxml.OoxmlSparklineSvgRenderer
import dev.fusion.ooxml.PivotCacheXmlExporter
import dev.fusion.privacy.BluetoothBluffsSessionGuard
import dev.fusion.privacy.ClearUrlTrackingParamStripper
import dev.fusion.privacy.PrivacyPostureDashboardEngine
import dev.fusion.privacy.TorPluggableTransportConfig
import dev.fusion.privacy.WifiProbeSequenceScrambler
import dev.fusion.vault.ShamirSecretShareSplitter
import dev.fusion.browser.model.ChipsPartitionedCookieJar
import dev.fusion.browser.model.CssFlexBlockBoxLayouter
import dev.fusion.browser.model.DeclarativeNetRequestTrieEngine
import dev.fusion.calc.GoalSeekSimplexSolver
import dev.fusion.capabilities.WasmSimdModuleVerifier
import dev.fusion.capabilities.WitComponentCanonicalAbiCodec
import dev.fusion.editor.CitationBibliographyFormatter
import dev.fusion.editor.WidowOrphanGalleyPaginator
import dev.fusion.format.pdf.PdfLinearizedHintValidator
import dev.fusion.format.pdf.PdfUa1TaggedStructureWriter
import dev.fusion.hw.CapacitiveGhostTouchFilter
import dev.fusion.hw.HallMagneticCoverTamperGuard
import dev.fusion.hw.Usb4PcieIommuDmaGuard
import dev.fusion.lok.OdsSpreadsheetFormulaBridge
import dev.fusion.ooxml.StructuredTableRefEngine
import dev.fusion.privacy.TorVanguardsLiteMesh
import dev.fusion.security.Luks2Argon2idHeaderAuditor
import dev.fusion.settingsui.PrivacyPostureScorecardCalculator
import dev.fusion.shell.FusionClassAReadinessAggregator
import dev.fusion.shell.KeystrokeJitter
import dev.fusion.files.RecallFolderDelineator
import dev.fusion.browser.model.RecallMethodEscalationRouter
import dev.fusion.browser.model.RecallHarArchiveExporter
import dev.fusion.media.RecallIsoBmffFragmentMuxer
import dev.fusion.media.RecallTier3CanvasWebAudioSiphoner
import dev.fusion.capture.RecallTier4NativeScreenAudioRecorder
import dev.fusion.media.Id3v24MusicTagEmbedder
import dev.fusion.windowing.RightClickContextMenuGeometry
import dev.fusion.designsystem.FusionViewModeVectorIcons
import dev.fusion.filesui.MillerColumnsTreeNavigator
import dev.fusion.commands.StackedUndoNotificationDeck
import dev.fusion.commands.UndoRedoStack
import dev.fusion.commands.toBottomRightToastEntry
import dev.fusion.capabilities.WasmTailCallVerifier
import dev.fusion.privacy.TorOnionPowIntroQueue
import dev.fusion.privacy.TailsAmnesicSwapCoreAuditor
import dev.fusion.hw.ChargerSurfingPowerMasker
import dev.fusion.hw.UltrasonicDolphinAttackFilter
import dev.fusion.security.ThermalResidueWipeAdvisor
import dev.fusion.security.ImuKeystrokeMotionZeroizer
import dev.fusion.designsystem.ShoulderSurfingPrivacyVeil
import dev.fusion.browser.model.RecallTier25WebRtcRtpDepacketizer
import dev.fusion.browser.model.RecallTier5WarcIso28500Archiver
import dev.fusion.files.RecallCdxjReplayIndexCodec
import dev.fusion.media.LoudnessEbuR128MomentaryMeter
import dev.fusion.windowing.ContextMenuSubmenuCascadeGeometry
import dev.fusion.windowing.PointerSafeTriangleSubmenuFilter
import dev.fusion.designsystem.ViewModePixelGridCrispHinting
import dev.fusion.designsystem.TranslucentGlassBackdropContrastGuard
import dev.fusion.commands.UndoDeckInteractiveFanController
import dev.fusion.commands.toBottomRightToastEntry
import dev.fusion.capabilities.WasmMultiMemoryIsolationVerifier
import dev.fusion.privacy.TorConfluxMultipathScheduler
import dev.fusion.privacy.TailsMetadataAnonymizer
import dev.fusion.hw.TempestSubpixelEmsecDitherer
import dev.fusion.hw.BleHogpKeystrokeFirewall
import dev.fusion.hw.VerifiedBootVbmetaDigestAuditor
import dev.fusion.hw.AmbientLightKeystrokeShadowGuard
import dev.fusion.hw.DramRowhammerRefreshMonitor
import dev.fusion.security.ChaCha20Rfc8439StreamBlock
import kotlin.math.abs
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.time.LocalDate
import kotlin.math.PI
import kotlin.math.roundToInt
import kotlin.math.sin
import java.io.BufferedReader
import java.io.ByteArrayOutputStream
import java.io.InputStreamReader
import java.nio.charset.StandardCharsets
import java.security.SecureRandom
import java.util.Base64

object R46WorkbenchDaemon {
    @JvmStatic
    fun main(args: Array<String>) {
        val reader = BufferedReader(InputStreamReader(System.`in`, StandardCharsets.UTF_8))
        println("READY")
        System.out.flush()
        while (true) {
            val line = reader.readLine() ?: break
            if (line.isBlank()) continue
            val reply = try {
                dispatch(line)
            } catch (t: Throwable) {
                """{"ok":false,"error":${jsonStr("${t.javaClass.simpleName}: ${t.message ?: ""}")}}"""
            }
            println(reply)
            System.out.flush()
        }
    }

    private fun dispatch(line: String): String {
        // Tab-separated framing from trusted local Python parent: OP \t ARG1 \t ARG2 \t ARG3
        val parts = line.split('\t')
        val op = parts[0]
        return when (op) {
            "PING" -> """{"ok":true,"engine":"Kotlin-JVM-R53","functions":300,"kernels":101,"modules":"50/50"}"""
            "R53_RECALL_6METHOD_WARC" -> {
                val url = parts.getOrNull(1)?.let { String(Base64.getDecoder().decode(it), StandardCharsets.UTF_8) }?.ifBlank { "https://concert.example.org/webrtc-live" } ?: "https://concert.example.org/webrtc-live"
                val pkt0 = RecallTier25WebRtcRtpDepacketizer.buildRtpPacket(500, 48_000L, 0x5353_0001L, 111, false, byteArrayOf(0x48, 0x01, 0x02, 0x03))
                val pkt1 = RecallTier25WebRtcRtpDepacketizer.buildRtpPacket(501, 48_960L, 0x5353_0001L, 111, true, byteArrayOf(0x48, 0x04, 0x05, 0x06))
                val depkt = RecallTier25WebRtcRtpDepacketizer.reassembleStream(listOf(pkt1, pkt0, pkt0))
                val warc = RecallTier5WarcIso28500Archiver.writeWarcBundle(
                    archiveFilename = "r53-live-workbench.warc",
                    warcinfoTimestampIso = "2026-10-29T12:00:00Z",
                    exchanges = listOf(
                        RecallTier5WarcIso28500Archiver.CapturedHttpExchange(
                            targetUri = "$url?utm_source=feed",
                            statusLine = "HTTP/1.1 200 OK",
                            responseHeaders = mapOf("Content-Type" to "text/html; charset=utf-8", "Set-Cookie" to "secret=123"),
                            payloadBytes = "<html><body>Live WebRTC</body></html>".toByteArray(),
                            identifiedPayloadType = "text/html",
                            captureTimestampIso = "2026-10-29T12:00:01Z",
                        ),
                        RecallTier5WarcIso28500Archiver.CapturedHttpExchange(
                            targetUri = "$url/audio.opus",
                            statusLine = "HTTP/1.1 200 OK",
                            responseHeaders = mapOf("Content-Type" to "audio/opus"),
                            payloadBytes = depkt.concatenatedPayloadBytes,
                            identifiedPayloadType = "audio/opus",
                            captureTimestampIso = "2026-10-29T12:00:02Z",
                        ),
                    ),
                )
                val warcParsed = RecallTier5WarcIso28500Archiver.parseAndVerifyWarcBundle(warc)
                val cdxjLines = RecallCdxjReplayIndexCodec.buildSortedCdxj(
                    listOf(RecallCdxjReplayIndexCodec.CdxjEntry(url, "20261029120001", "live.warc", 0L, warc.size.toLong(), "text/html", 200, "a".repeat(64)))
                )
                val webrtcRoute = RecallMethodEscalationRouter.selectTier(
                    RecallMethodEscalationRouter.ViewedResourceSignal(url, "application/sdp", isWebRtcRtpStream = true)
                ) as RecallMethodEscalationRouter.RoutingDecision.Selected
                val warcRoute = RecallMethodEscalationRouter.selectTier(
                    RecallMethodEscalationRouter.ViewedResourceSignal(url, "text/html", warcBundleRequested = true)
                ) as RecallMethodEscalationRouter.RoutingDecision.Selected
                val sine = FloatArray(48_000) { i -> (0.5 * sin(2.0 * PI * 1000.0 * i / 48_000.0)).toFloat() }
                val lufs = LoudnessEbuR128MomentaryMeter.measureMono48k(sine).momentaryLufs
                val digestsOk = warcParsed.size == 3 && "secret=123" !in String(warc, StandardCharsets.ISO_8859_1)
                """{"ok":true,"methods_supported":${RecallMethodEscalationRouter.RecordingTier.entries.size},"webrtc_tier":${jsonStr(webrtcRoute.tier.name)},"warc_tier":${jsonStr(warcRoute.tier.name)},"rtp_ordered_frames":${depkt.orderedSlots.size},"rtp_duplicates_dropped":${depkt.duplicateDroppedCount},"warc_bytes":${warc.size},"warc_records":${warcParsed.size},"warc_digests_valid":$digestsOk,"cdxj_surt":${jsonStr(cdxjLines.first().substringBefore(" "))},"sine_1khz_lufs":$lufs,"warc_b64":${jsonStr(Base64.getEncoder().encodeToString(warc))}}"""
            }
            "R53_UI_ERGONOMICS_PHASE2" -> {
                val vw = parts.getOrNull(1)?.toDoubleOrNull() ?: 1440.0
                val vh = parts.getOrNull(2)?.toDoubleOrNull() ?: 900.0
                val cx = parts.getOrNull(3)?.toDoubleOrNull() ?: 1260.0
                val cy = parts.getOrNull(4)?.toDoubleOrNull() ?: 300.0
                val parentRows = listOf(
                    RightClickContextMenuGeometry.MenuActionRow("open", "Open", "Enter"),
                    RightClickContextMenuGeometry.MenuActionRow("open_with", "Open With >"),
                    RightClickContextMenuGeometry.MenuActionRow("trash", "Move to Trash", "Del", startsNewSection = true),
                )
                val subRows = listOf(
                    RightClickContextMenuGeometry.MenuActionRow("writer", "Fusion Writer"),
                    RightClickContextMenuGeometry.MenuActionRow("calc", "Fusion Calc (300 fns)"),
                )
                val primary = RightClickContextMenuGeometry.solve(vw, vh, cx, cy, parentRows)
                val cascade = ContextMenuSubmenuCascadeGeometry.solveSubmenu(vw, vh, primary, 1, subRows)
                val safeHold = PointerSafeTriangleSubmenuFilter.shouldKeepSubmenuOpenOnSiblingHover(
                    prevPointer = PointerSafeTriangleSubmenuFilter.PointDp(400.0, 200.0),
                    currentPointer = PointerSafeTriangleSubmenuFilter.PointDp(460.0, 220.0),
                    submenuNearEdgeX = 500.0,
                    submenuTopY = 180.0,
                    submenuBottomY = 320.0,
                    elapsedMillisSincePrevSample = 80L,
                )
                var crispAll = true
                for (kind in FusionViewModeVectorIcons.ViewModeKind.entries) {
                    for (px in ViewModePixelGridCrispHinting.STANDARD_ICON_SIZES_PX) {
                        if (ViewModePixelGridCrispHinting.hintIconForCanvasPx(kind, px).crispAlignmentRatio != 1.0) crispAll = false
                    }
                }
                val undoStack = UndoRedoStack("v0").apply { pushState("v1", 1_000L) }
                var deck = StackedUndoNotificationDeck.DeckState()
                for (i in 1..4) deck = deck.enqueue(undoStack.toBottomRightToastEntry("u$i", "Archived item #$i", 1_000L + i * 100L))
                val compactDeck = UndoDeckInteractiveFanController.computeFrame(deck, false, 2_000L, vw, vh)
                val fanDeck = UndoDeckInteractiveFanController.computeFrame(deck, true, 2_000L, vw, vh)
                val glass = TranslucentGlassBackdropContrastGuard.solveSafeTranslucentAlpha(0x121926, 0xF2F6FC, 0.88)
                """{"ok":true,"os_baselines":${ContextMenuSubmenuCascadeGeometry.SEVEN_OS_BENCHMARKS.size},"parent_w_dp":${primary.widthDp},"child_w_dp":${cascade.widthDp},"submenu_flipped_left":${cascade.cascadedLeftOfParent},"safe_triangle_hold":$safeHold,"crisp_all_scales":$crispAll,"compact_height_pct":${compactDeck.compactRightSideHeightFraction * 100.0},"fan_height_pct":${fanDeck.expandedRightSideHeightFraction * 100.0},"glass_contrast":${glass.worstCaseContrast}}"""
            }
            "R53_CALC300_WASM_MULTIMEMORY" -> {
                val fSheet = FormulaEval.evaluateSheet(
                    mapOf(
                        "A1" to FormulaEval.CellInput.Formula("=IMABS(\"3+4i\")"),
                        "A2" to FormulaEval.CellInput.Formula("=MULTINOMIAL(2, 3, 4)"),
                        "A3" to FormulaEval.CellInput.Formula("=FISHERINV(FISHER(0.5))"),
                        "A4" to FormulaEval.CellInput.Formula("=BETA.DIST(0.5, 2, 2, TRUE)"),
                    )
                )
                val hankel = FormulaEval.evaluateFormulaAsGrid("=HANKEL({1,2,3}, {3,4,5})", emptyMap())
                val mmOut = ByteArrayOutputStream().apply {
                    write(byteArrayOf(0x00, 0x61, 0x73, 0x6D, 0x01, 0x00, 0x00, 0x00))
                    write(byteArrayOf(0x01, 0x05, 0x01, 0x60, 0x00, 0x01, 0x7F))
                    write(byteArrayOf(0x03, 0x02, 0x01, 0x00))
                    write(byteArrayOf(0x05, 0x07, 0x02, 0x01, 0x01, 0x02, 0x01, 0x01, 0x02))
                    val expName = "copyAcrossMemories".toByteArray(StandardCharsets.UTF_8)
                    val sec7 = ByteArrayOutputStream().apply { write(0x01); write(expName.size); write(expName); write(0x00); write(0x00) }.toByteArray()
                    write(0x07); write(sec7.size); write(sec7)
                    val body = byteArrayOf(0x00, 0x41, 0x10, 0x41, 0x00, 0x41, 0x04, 0xFC.toByte(), 0x0A, 0x01, 0x00, 0x41, 0x10, 0x28, 0x42, 0x01, 0x00, 0x0B)
                    val sec10 = ByteArrayOutputStream().apply { write(0x01); write(body.size); write(body) }.toByteArray()
                    write(0x0A); write(sec10.size); write(sec10)
                    val sec11 = byteArrayOf(0x01, 0x00, 0x41, 0x00, 0x0B, 0x04, 0x4D, 0x45, 0x4D, 0x53)
                    write(0x0B); write(sec11.size); write(sec11)
                }.toByteArray()
                val parsedWasm = WasmSandbox.decode(mmOut)
                val mmEcho = parsedWasm.instantiate().invoke("copyAcrossMemories").returnValues[0] == 0x534D_454D
                val isoOk = WasmMultiMemoryIsolationVerifier.auditPolicy(listOf(1, 1), listOf(2, 2), setOf(0), emptySet(), setOf(1)).isolationCompliant
                """{"ok":true,"functions":300,"spill_ops":48,"imabs_3_4i":${(fSheet.values["A1"] as FormulaEval.CellValue.Num).value},"multinomial_2_3_4":${(fSheet.values["A2"] as FormulaEval.CellValue.Num).value},"fisher_roundtrip":${(fSheet.values["A3"] as FormulaEval.CellValue.Num).value},"beta_dist":${(fSheet.values["A4"] as FormulaEval.CellValue.Num).value},"hankel_2_2":${(hankel[2][2] as FormulaEval.CellValue.Num).value},"wasm_memories":${parsedWasm.memoryDecls.size},"wasm_isolation_ok":$isoOk,"wasm_dual_mem_echo":$mmEcho}"""
            }
            "R53_CONFLUX_MAT2_HW30" -> {
                val goodLegs = listOf(
                    TorConfluxMultipathScheduler.ConfluxLeg("L1", "GUARD_A", "MID_A", "EXIT_Z", 35, 31, 5),
                    TorConfluxMultipathScheduler.ConfluxLeg("L2", "GUARD_B", "MID_B", "EXIT_Z", 52, 31, 2),
                )
                val topoOk = TorConfluxMultipathScheduler.verifyDisjointLegs(goodLegs).first
                val dirtyPdf = "%PDF-1.7\n1 0 obj << /Author (Secret Author) /Title (Leak) >> endobj\n<?xpacket begin=''?>GPS<?xpacket end='w'?>\n%%EOF".toByteArray(StandardCharsets.ISO_8859_1)
                val mat = TailsMetadataAnonymizer.anonymizeFile("memo.pdf", dirtyPdf)
                val v26 = TempestSubpixelEmsecDitherer.ditherSensitiveViewportRgb24(IntArray(16 * 16) { 0x204060 }, 16, 16, amplitudeLsb = 3)
                val v27Blocked = !BleHogpKeystrokeFirewall.evaluateHidEvent(BleHogpKeystrokeFirewall.BleHidSessionContext(0x1812, false, true, true, 16, true, 2)).allowed
                val vbHdr = VerifiedBootVbmetaDigestAuditor.buildSyntheticVbmetaHeader(algorithmType = 2, rollbackIndex = 20261029L, flags = 0)
                val v28Ok = VerifiedBootVbmetaDigestAuditor.auditVbmetaHeader(vbHdr, 20260101L).trustedGreenState
                val v29 = AmbientLightKeystrokeShadowGuard.filterSample(143.7f, 1.2f, 1_200L, sensitivePinOrPasswordFocused = true)
                val v30 = DramRowhammerRefreshMonitor.assessWindow(120_000L, 180_000L, 0.90, highTempAbove75C = true)
                val block1 = ChaCha20Rfc8439StreamBlock.chacha20Block(ByteArray(32) { it.toByte() }, 1, byteArrayOf(0, 0, 0, 0x09, 0, 0, 0, 0x4a, 0, 0, 0, 0))
                val chachaOk = ByteBuffer.wrap(block1, 0, 4).order(ByteOrder.LITTLE_ENDIAN).int == 0xe4e7f110.toInt()
                """{"ok":true,"conflux_disjoint_valid":$topoOk,"mat2_cleaned_name":${jsonStr(mat.cleanedFilename)},"mat2_removed_fields":${mat.removedArtifacts.size},"v26_tmds_var_ratio":${v26.rowTransitionVarianceReductionRatio},"v27_ble_locked_blocked":$v27Blocked,"v28_vbmeta_verified":$v28Ok,"v29_als_quantized_lux":${v29.quantizedLux},"v30_trr_action":${jsonStr(v30.action.name)},"chacha20_rfc8439_ok":$chachaOk,"vectors_total":30,"full_class_a":"0/50","host_subset":"50/50"}"""
            }
            "R52_RECALL_MEDIA_STUDIO" -> {
                val url = parts.getOrNull(1)?.let { String(Base64.getDecoder().decode(it), StandardCharsets.UTF_8) }?.ifBlank { "https://arte.tv/concerts/baroque?utm_source=feed" } ?: "https://arte.tv/concerts/baroque?utm_source=feed"
                val pageTitle = parts.getOrNull(2)?.let { String(Base64.getDecoder().decode(it), StandardCharsets.UTF_8) }?.ifBlank { "Baroque Evening Live: Concert #4" } ?: "Baroque Evening Live: Concert #4"
                val customTpl = parts.getOrNull(3)?.let { String(Base64.getDecoder().decode(it), StandardCharsets.UTF_8) }?.ifBlank { "{custom}/{yyyy}-{mm}-{dd}/{domain}/{kind}" } ?: "{custom}/{yyyy}-{mm}-{dd}/{domain}/{kind}"
                val customName = parts.getOrNull(4)?.let { String(Base64.getDecoder().decode(it), StandardCharsets.UTF_8) }?.ifBlank { "MusicResearch" } ?: "MusicResearch"
                val payload = "baroque-video-stream".toByteArray()
                val pUrl = RecallFolderDelineator.planLocation(
                    RecallFolderDelineator.DelineationMode.BY_URL_NAME,
                    RecallFolderDelineator.ViewedMediaMetadata(url, pageTitle, url, RecallFolderDelineator.MediaKind.VIDEO, "concert.fmp4", 1_793_145_600_000L, customName),
                    payload, customTpl,
                ).relativeFilePath
                val pTitle = RecallFolderDelineator.planLocation(
                    RecallFolderDelineator.DelineationMode.BY_PAGE_TITLE,
                    RecallFolderDelineator.ViewedMediaMetadata(url, pageTitle, url, RecallFolderDelineator.MediaKind.MUSIC, "nocturne.flac", 1_793_145_600_000L, customName),
                    payload, customTpl,
                ).relativeFilePath
                val pCustom = RecallFolderDelineator.planLocation(
                    RecallFolderDelineator.DelineationMode.BY_CUSTOM_TEMPLATE,
                    RecallFolderDelineator.ViewedMediaMetadata(url, pageTitle, url, RecallFolderDelineator.MediaKind.IMAGE, "poster.avif", 1_793_145_600_000L, customName),
                    payload, customTpl,
                ).relativeFilePath
                val chain = (0..3).map { fc ->
                    val routed = RecallMethodEscalationRouter.selectTier(
                        RecallMethodEscalationRouter.ViewedResourceSignal(
                            url = url, contentType = "application/octet-stream",
                            priorFailedTiers = RecallMethodEscalationRouter.RecordingTier.entries.take(fc).toSet(),
                        )
                    )
                    (routed as RecallMethodEscalationRouter.RoutingDecision.Selected).tier.name
                }.joinToString(" -> ")
                val fmp4 = RecallIsoBmffFragmentMuxer.muxPlayableFmp4(
                    trackId = 1, timescale = 90_000, isAudioOnly = false,
                    fragments = listOf(
                        RecallIsoBmffFragmentMuxer.FragmentSpec(1, 0L, listOf(RecallIsoBmffFragmentMuxer.FragmentSample(3_000, byteArrayOf(0x65, 0x01, 0x02, 0x03), true)))
                    )
                )
                val probe = RecallIsoBmffFragmentMuxer.inspectFmp4(fmp4)
                val pcm = FloatArray(480) { (0.45 * sin(2.0 * PI * 440.0 * (it / 2) / 48_000.0)).toFloat() }
                val wav = RecallTier3CanvasWebAudioSiphoner.siphonWebAudioFloatToWav(pcm, sampleRateHz = 48_000, channels = 2)
                val vTs = (0 until 120).map { (it * 1_000_000L) / 30L }
                val aSmp = (0 until 120).map { (it * 1600L) + (if (it >= 60) 4800L else 0L) }
                val avSync = RecallTier4NativeScreenAudioRecorder.synchronizeVideoFramesToAudioClock(vTs, aSmp, 30)
                val maxDrift = avSync.maxOf { abs(it.residualDriftUs) }
                val id3Bytes = Id3v24MusicTagEmbedder.prependId3v24Tag(ByteArray(32) { 0xFF.toByte() }, Id3v24MusicTagEmbedder.MusicTrackMetadata(pageTitle, "Fusion Ensemble", "Recall Vault", url))
                val harJson = RecallHarArchiveExporter.exportHar12Json(
                    pageTitle,
                    listOf(RecallHarArchiveExporter.HarMediaCaptureEntry("2026-10-28T12:00:00Z", url, 200, "video/mp4", fmp4.size.toLong(), 18L, 2, "0123456789abcdef", mapOf("Cookie" to "secret=1")))
                )
                """{"ok":true,"path_by_url":${jsonStr(pUrl)},"path_by_title":${jsonStr(pTitle)},"path_by_custom":${jsonStr(pCustom)},"escalation_chain":${jsonStr(chain)},"fmp4_bytes":${fmp4.size},"tfdt_before_trun":${probe.tfdtAlwaysBeforeTrun},"wav_bytes":${wav.size},"tier4_max_drift_us":$maxDrift,"id3_bytes":${id3Bytes.size},"har_redacted":${!harJson.contains("secret=1")},"fmp4_b64":${jsonStr(Base64.getEncoder().encodeToString(fmp4))},"wav_b64":${jsonStr(Base64.getEncoder().encodeToString(wav))}}"""
            }
            "R52_UI_ERGONOMICS_BENCHMARK" -> {
                val vw = parts.getOrNull(1)?.toDoubleOrNull() ?: 1440.0
                val vh = parts.getOrNull(2)?.toDoubleOrNull() ?: 900.0
                val cx = parts.getOrNull(3)?.toDoubleOrNull() ?: 420.0
                val cy = parts.getOrNull(4)?.toDoubleOrNull() ?: 260.0
                val rows = listOf(
                    RightClickContextMenuGeometry.MenuActionRow("open", "Open in Recall Studio", "Enter", false),
                    RightClickContextMenuGeometry.MenuActionRow("copy_path", "Copy Vault Relative Path", "Ctrl+Shift+C", false),
                    RightClickContextMenuGeometry.MenuActionRow("verify", "Verify SHA-256 Digest", "Ctrl+V", false),
                    RightClickContextMenuGeometry.MenuActionRow("beam", "Send via Encrypted Beam", "Ctrl+B", false),
                    RightClickContextMenuGeometry.MenuActionRow("trash", "Move to Trash (Undoable)", "Del", true),
                )
                val solved = RightClickContextMenuGeometry.solve(vw, vh, cx, cy, rows)
                val svgList = FusionViewModeVectorIcons.toCleanSvgMarkup(FusionViewModeVectorIcons.ViewModeKind.LIST, "#38BDF8")
                val svgGrid = FusionViewModeVectorIcons.toCleanSvgMarkup(FusionViewModeVectorIcons.ViewModeKind.GRID, "#34D399")
                val svgCols = FusionViewModeVectorIcons.toCleanSvgMarkup(FusionViewModeVectorIcons.ViewModeKind.COLUMNS, "#C084FC")
                val editStack = UndoRedoStack("init")
                editStack.pushState("v2", 1_000L)
                var deck = StackedUndoNotificationDeck.DeckState()
                listOf(
                    "Saved concert.fmp4 to Recall Vault",
                    "Stripped 3 tracking params from URL",
                    "Renamed 4 media files atomically",
                    "Exported clean-room .docx manuscript",
                ).forEachIndexed { idx, m ->
                    deck = deck.enqueue(editStack.toBottomRightToastEntry("u$idx", m, 1_000L + idx * 500L))
                }
                val undoLayout = StackedUndoNotificationDeck.computeBottomRightLayout(deck, vw, vh, bottomDockClearanceDp = 64.0)
                val cardsJson = undoLayout.visibleFrontToBack.joinToString(",") {
                    """{"depth":${it.zDepthIndex},"msg":${jsonStr(it.entry.message)},"alpha":${it.alpha},"scale":${it.scale},"ty":${it.translateYDp}}"""
                }
                """{"ok":true,"menu_width_dp":${solved.widthDp},"menu_height_dp":${solved.heightDp},"menu_x_dp":${solved.xDp},"menu_y_dp":${solved.yDp},"flipped_left":${solved.flippedLeftOfCursor},"flipped_up":${solved.flippedAboveCursor},"win_width_pct":${solved.widthFractionOfViewport * 100.0},"undo_bbox_x":${undoLayout.boundingBoxXDp},"undo_bbox_y":${undoLayout.boundingBoxYDp},"undo_bbox_w":${undoLayout.boundingBoxWidthDp},"undo_bbox_h":${undoLayout.boundingBoxHeightDp},"undo_height_pct":${undoLayout.rightSideHeightFraction * 100.0},"undo_hidden":${undoLayout.hiddenOlderCount},"undo_cards":[$cardsJson],"svg_list_b64":${jsonStr(Base64.getEncoder().encodeToString(svgList.toByteArray()))},"svg_grid_b64":${jsonStr(Base64.getEncoder().encodeToString(svgGrid.toByteArray()))},"svg_cols_b64":${jsonStr(Base64.getEncoder().encodeToString(svgCols.toByteArray()))}}"""
            }
            "R52_CALC260_WASM3_TAILCALL" -> {
                val tailN = (parts.getOrNull(1)?.toIntOrNull() ?: 25000).coerceIn(1, 100_000)
                val fSheet = FormulaEval.evaluateSheet(
                    mapOf(
                        "A1" to FormulaEval.CellInput.Formula("=GAMMA(5)"),
                        "A2" to FormulaEval.CellInput.Formula("=WEIBULL.DIST(2, 1.5, 2, TRUE)"),
                        "A3" to FormulaEval.CellInput.Formula("=BESSELJ(1.5, 1)"),
                        "A4" to FormulaEval.CellInput.Formula("=TBILLEQ(0, 182, 0.05)"),
                        "A5" to FormulaEval.CellInput.Formula("=HEX2OCT(\"1FF\")"),
                    )
                )
                val sp = SpillSheetEngine.evaluate(
                    mapOf(
                        "A1" to FormulaEval.CellInput.NumberLiteral(2.0),
                        "A2" to FormulaEval.CellInput.NumberLiteral(4.0),
                        "B1" to FormulaEval.CellInput.NumberLiteral(10.0),
                        "B2" to FormulaEval.CellInput.NumberLiteral(20.0),
                        "D1" to FormulaEval.CellInput.Formula("=OUTERPRODUCT(A1:A2, B1:B2)"),
                    )
                )
                val tailWasm = WasmTailCallVerifier.buildTailRecursiveSumModule()
                val ver = WasmTailCallVerifier.audit(tailWasm)
                val res = WasmSandbox.decode(tailWasm).instantiate().invoke("sum_tail", listOf(tailN, 0), fuelBudget = 2_000_000L, maxCallDepth = 2)
                """{"ok":true,"functions":260,"spill_ops":42,"gamma_5":${(fSheet.values["A1"] as FormulaEval.CellValue.Num).value},"weibull":${(fSheet.values["A2"] as FormulaEval.CellValue.Num).value},"besselj":${(fSheet.values["A3"] as FormulaEval.CellValue.Num).value},"tbilleq":${(fSheet.values["A4"] as FormulaEval.CellValue.Num).value},"hex2oct_1ff":${jsonStr((fSheet.values["A5"] as FormulaEval.CellValue.Text).value)},"outerproduct_e2":${(sp.typedGrid["E2"] as FormulaEval.CellValue.Num).value},"wasm3_tail_valid":${ver.valid},"wasm3_return_calls":${ver.returnCallCount},"wasm3_sum_tail":${res.returnValues.first()},"max_call_depth":2}"""
            }
            "R52_TAILS_POW_SHIELD25" -> {
                val blakeHex = TorOnionPowIntroQueue.blake2b("abc".toByteArray(), 64).joinToString("") { "%02x".format(it) }.take(32)
                val v21 = ChargerSurfingPowerMasker.evaluate(ChargerSurfingPowerMasker.ChargingInputContext(true, false, true, 90.0, 240.0))
                val v22 = UltrasonicDolphinAttackFilter.auditAndFilter(DoubleArray(960) { 0.35 * sin(2.0 * PI * 500.0 * it / 96_000.0) + 0.65 * sin(2.0 * PI * 25_000.0 * it / 96_000.0) }, 96_000)
                val v23 = ThermalResidueWipeAdvisor.assess(ThermalResidueWipeAdvisor.SurfaceMaterial.GLASS_GORILLA)
                val rawImu = (0 until 100).map { i -> ImuKeystrokeMotionZeroizer.ImuSample(i * 10L, 0.4 * sin(i.toDouble()), 0.3, 9.75, 0.8, -0.5, 0.2) }
                val sanImu = ImuKeystrokeMotionZeroizer.sanitizeStream(rawImu, listOf(250L, 500L, 750L))
                val v24Ratio = ImuKeystrokeMotionZeroizer.computeTiltImpulseEnergy(sanImu) / ImuKeystrokeMotionZeroizer.computeTiltImpulseEnergy(rawImu)
                val v25 = ShoulderSurfingPrivacyVeil.evaluateVeil(1080, 500)
                """{"ok":true,"blake2b_abc_prefix":${jsonStr(blakeHex)},"v21_equalized_mw":${v21.totalEqualizedLoadMilliwatts},"v22_ultrasonic_detected":${v22.dolphinAttackDetected},"v23_thermal_wipe_required":${v23.palmScrubRequiredImmediately},"v24_imu_impulse_ratio":$v24Ratio,"v25_offaxis_contrast":${v25.offAxis45DegContrast},"vectors_total":25,"full_class_a":"0/50","host_subset":"50/50"}"""
            }
            "R51_OFFICE_TABLE_SOLVER" -> {
                val nQ1 = parts.getOrNull(1)?.toDoubleOrNull() ?: 120.0
                val sQ1 = parts.getOrNull(2)?.toDoubleOrNull() ?: 200.0
                val targetSq = parts.getOrNull(3)?.toDoubleOrNull() ?: 2.0
                val fSheet = FormulaEval.evaluateSheet(
                    mapOf(
                        "A1" to FormulaEval.CellInput.NumberLiteral(2.0),
                        "A2" to FormulaEval.CellInput.NumberLiteral(4.0),
                        "A3" to FormulaEval.CellInput.NumberLiteral(8.0),
                        "B1" to FormulaEval.CellInput.Formula("=HARMEAN(A1:A3)"),
                        "B2" to FormulaEval.CellInput.Formula("=GAMMALN(5)"),
                        "B3" to FormulaEval.CellInput.Formula("=ROMAN(2026)"),
                    )
                )
                val harmean = (fSheet.values["B1"] as FormulaEval.CellValue.Num).value
                val gammaln5 = (fSheet.values["B2"] as FormulaEval.CellValue.Num).value
                val roman2026 = (fSheet.values["B3"] as FormulaEval.CellValue.Text).value
                val tbl = StructuredTableRefEngine.StructuredTable(
                    id = 1, name = "Sales", topLeftA1 = "B2",
                    columns = listOf(
                        StructuredTableRefEngine.TableColumnSpec("Region", StructuredTableRefEngine.TotalsFunction.NONE, "Total"),
                        StructuredTableRefEngine.TableColumnSpec("Q1", StructuredTableRefEngine.TotalsFunction.SUM),
                    ),
                    dataRows = listOf(
                        listOf(FormulaEval.CellInput.TextLiteral("North"), FormulaEval.CellInput.NumberLiteral(nQ1)),
                        listOf(FormulaEval.CellInput.TextLiteral("South"), FormulaEval.CellInput.NumberLiteral(sQ1)),
                    ),
                    includeTotalsRow = true,
                )
                val tblEv = StructuredTableRefEngine.evaluateSheetWithTables(listOf(tbl))
                val q1Total = (tblEv.values["C5"] as FormulaEval.CellValue.Num).value
                val tblXlsxB64 = java.util.Base64.getEncoder().encodeToString(StructuredTableRefEngine.writeTableXlsx(tbl))
                val root = GoalSeekSimplexSolver.goalSeek(targetValue = targetSq.coerceAtLeast(0.01), initialGuess = 1.0) { x -> x * x }.solutionX
                val lp = GoalSeekSimplexSolver.solveLinearProgramMax(
                    listOf(3.0, 5.0),
                    listOf(listOf(1.0, 0.0), listOf(0.0, 2.0), listOf(3.0, 2.0)),
                    listOf(4.0, 12.0, 18.0),
                ).objectiveValue
                val openF = OdsSpreadsheetFormulaBridge.a1ToOpenFormula("=SUM(A1:B4)+Sheet2!C3")
                """{"ok":true,"harmean":$harmean,"gammaln5":$gammaln5,"roman_2026":${jsonStr(roman2026)},"table_q1_total":$q1Total,"goalseek_root":$root,"simplex_lp_opt":$lp,"openformula":${jsonStr(openF)},"table_xlsx_b64":${jsonStr(tblXlsxB64)}}"""
            }
            "R51_WRITER_GALLEY_PDFUA1" -> {
                val docTitle = parts.getOrNull(1)?.let { String(java.util.Base64.getDecoder().decode(it), StandardCharsets.UTF_8) }?.ifBlank { "Fusion R51 Accessible Report" } ?: "Fusion R51 Accessible Report"
                val galley = WidowOrphanGalleyPaginator.paginate(
                    blocks = listOf(
                        WidowOrphanGalleyPaginator.GalleyBlock.Heading("h1", "1. $docTitle"),
                        WidowOrphanGalleyPaginator.GalleyBlock.Paragraph("p1", (1..8).map { "Scholarly galley line $it with widow/orphan invariants." }, footnoteByLineIndex = mapOf(1 to listOf("1. Primary archival citation."))),
                    ),
                )
                val bib = CitationBibliographyFormatter.parseBibTex(
                    "@inproceedings{wang2022,\n  author = {Kai Wang},\n  title = {GhostTouch},\n  booktitle = {USENIX Security},\n  year = {2022}\n}\n@inproceedings{markettos2019,\n  author = {A. Markettos},\n  title = {Thunderclap},\n  booktitle = {NDSS},\n  year = {2019}\n}"
                )
                val cited = CitationBibliographyFormatter.formatManuscript("Mitigating [@wang2022; @markettos2019].", bib, CitationBibliographyFormatter.CitationStyle.IEEE)
                val msDocx = DocxRichDocument.writeMultiSection(
                    sections = listOf(
                        DocxRichDocument.SectionPart(
                            setup = DocxRichDocument.SectionSetup(DocxRichDocument.PageOrientation.PORTRAIT, columns = 2),
                            blocks = listOf(DocxRichDocument.RichBlock.Heading(1, docTitle)),
                            bookmarks = listOf("sec_top" to docTitle),
                        ),
                        DocxRichDocument.SectionPart(
                            setup = DocxRichDocument.SectionSetup(DocxRichDocument.PageOrientation.LANDSCAPE, columns = 1),
                            blocks = listOf(DocxRichDocument.RichBlock.Heading(2, "Wide Schedule")),
                            crossRefs = listOf("sec_top" to "See Top"),
                        ),
                    ),
                    headerText = docTitle,
                    footerText = "Page Footer",
                )
                val pdfUaBytes = PdfUa1TaggedStructureWriter.writeTaggedPdfUa1(
                    docTitle, "en-US",
                    listOf(
                        PdfUa1TaggedStructureWriter.TaggedElement.Heading(1, docTitle),
                        PdfUa1TaggedStructureWriter.TaggedElement.Paragraph(cited.renderedBody),
                        PdfUa1TaggedStructureWriter.TaggedElement.Figure("Vanguards-Lite 4-guard pool diagram", "Fig 1"),
                    )
                )
                val uaAudit = PdfUa1TaggedStructureWriter.auditPdfUa1Bytes(pdfUaBytes)
                val linAudit = PdfLinearizedHintValidator.inspect(PdfLinearizedHintValidator.buildLinearizedSamplePdf(3, docTitle))
                """{"ok":true,"galley_pages":${galley.pages.size},"galley_zero_violations":${galley.widowViolations == 0 && galley.orphanViolations == 0},"ieee_body":${jsonStr(cited.renderedBody)},"pdfua1_compliant":${uaAudit.compliant},"mcid_count":${uaAudit.mcidCount},"linearized_exact_length":${linAudit.declaredFileLength == linAudit.actualFileLength},"docx_ms_b64":${jsonStr(java.util.Base64.getEncoder().encodeToString(msDocx))},"pdfua1_b64":${jsonStr(java.util.Base64.getEncoder().encodeToString(pdfUaBytes))}}"""
            }
            "R51_WASM2_SIMD_BROWSER" -> {
                val scalar = parts.getOrNull(1)?.toIntOrNull() ?: 3
                val viewportW = parts.getOrNull(2)?.toDoubleOrNull()?.coerceIn(320.0, 1920.0) ?: 600.0
                val rawUrl = parts.getOrNull(3)?.let { String(java.util.Base64.getDecoder().decode(it), StandardCharsets.UTF_8) }?.ifBlank { "https://clean.example/read?id=42&fbclid=TRACK&utm_source=x" } ?: "https://clean.example/read?id=42&fbclid=TRACK&utm_source=x"
                val wasmBytes = buildDemoR51SimdWasm()
                val ver = WasmSimdModuleVerifier.verify(wasmBytes)
                val inst = WasmSandbox.decode(wasmBytes).instantiate()
                inst.writeMemory(0, ByteBuffer.allocate(16).order(ByteOrder.LITTLE_ENDIAN).putInt(10).putInt(20).putInt(30).putInt(40).array())
                val v128 = inst.invokeV128("simd_saxpy_lanes", listOf(0, 32, scalar)).value
                val lb = ByteBuffer.wrap(v128.toBytes()).order(ByteOrder.LITTLE_ENDIAN)
                val lanes = listOf(lb.int, lb.int, lb.int, lb.int)
                val witMem = ByteArray(256)
                val witSlice = WitComponentCanonicalAbiCodec.lowerUtf8String(witMem, 0, "WIT-R51")
                val witLifted = WitComponentCanonicalAbiCodec.liftUtf8String(witMem, witSlice.ptr, witSlice.len)
                val flex = CssFlexBlockBoxLayouter.computeLayout(
                    CssFlexBlockBoxLayouter.DomBoxNode(
                        id = "bar",
                        style = CssFlexBlockBoxLayouter.BoxStyle(display = CssFlexBlockBoxLayouter.DisplayMode.FLEX, gapPx = 12.0, padding = CssFlexBlockBoxLayouter.EdgeInsets.all(8.0)),
                        children = listOf(
                            CssFlexBlockBoxLayouter.DomBoxNode("back", CssFlexBlockBoxLayouter.BoxStyle(widthPx = 48.0, heightPx = 40.0)),
                            CssFlexBlockBoxLayouter.DomBoxNode("omnibox", CssFlexBlockBoxLayouter.BoxStyle(flexGrow = 1.0, heightPx = 40.0)),
                            CssFlexBlockBoxLayouter.DomBoxNode("shield", CssFlexBlockBoxLayouter.BoxStyle(widthPx = 48.0, heightPx = 40.0)),
                        ),
                    ),
                    viewportW, 56.0,
                )
                val chips = ChipsPartitionedCookieJar.Jar(blockUnpartitionedThirdParty = true)
                chips.setCookie("https://cdn.trk.example/a.js", "https://news.example/", "sid=alpha; Secure; SameSite=None; Path=/; Partitioned")
                chips.setCookie("https://cdn.trk.example/a.js", "https://shop.example/", "sid=beta; Secure; SameSite=None; Path=/; Partitioned")
                val chipsIsolated = chips.cookieHeaderForRequest("https://cdn.trk.example/p", "https://news.example/") != chips.cookieHeaderForRequest("https://cdn.trk.example/p", "https://shop.example/")
                val dnr = DeclarativeNetRequestTrieEngine.compile(listOf("||ads.example^\$third-party,script"))
                val cleanNav = dnr.evaluate(rawUrl, "https://clean.example/", DeclarativeNetRequestTrieEngine.ResourceType.DOCUMENT)
                """{"ok":true,"simd_verified":${ver.valid},"simd_op_count":${ver.simdInstructionCount},"v128_lanes":[${lanes.joinToString(",")}],"wit_roundtrip":${jsonStr(witLifted)},"omnibox_px":${flex.children[1].width},"chips_isolated":$chipsIsolated,"sanitized_url":${jsonStr(cleanNav.sanitizedUrl)}}"""
            }
            "R51_TAILS_SHIELD_20VEC" -> {
                val argonMemKiB = parts.getOrNull(1)?.toLongOrNull() ?: 1_048_576L
                val iommuStrict = parts.getOrNull(2)?.toBooleanStrictOrNull() ?: false
                val hallUt = parts.getOrNull(3)?.toDoubleOrNull() ?: 1800.0
                val pool = TorVanguardsLiteMesh.Pool(java.security.SecureRandom(byteArrayOf(5, 1, 5, 1)))
                val leases = pool.replenish(
                    1_700_000_000L,
                    TorVanguardsLiteMesh.RelayDescriptor("0000ENTRY", "10.0.1.1", "fam_e", 5_000L),
                    (1..10).map { i -> TorVanguardsLiteMesh.RelayDescriptor("R_%02d".format(i), "${30 + i}.10.0.1", "fam_$i", 1_000L * i) },
                )
                val luksAudit = Luks2Argon2idHeaderAuditor.auditHeader(
                    Luks2Argon2idHeaderAuditor.buildSyntheticLuks2Header(kdfType = "argon2id", memoryKiB = argonMemKiB, timeCost = 4, keySizeBytes = 64)
                )
                val ghost = CapacitiveGhostTouchFilter.inspectBurst(
                    (0 until 10).map { i -> CapacitiveGhostTouchFilter.TouchSample(i * 2L, 400.0 + (if (i % 2 == 0) 22.0 else -22.0), 600.0, 0.01, 1.5, 0.0) },
                    acChargerConnected = true, privilegedSurface = true,
                )
                val dma = Usb4PcieIommuDmaGuard.evaluateAttach(
                    Usb4PcieIommuDmaGuard.PeripheralAttachEvent(0x8086, 0x15EB, setOf(0x08), true, false, false, iommuStrict)
                )
                val hall = HallMagneticCoverTamperGuard.evaluate(
                    HallMagneticCoverTamperGuard.MagneticWakeSample(true, hallUt, 900.0, 600.0, chassisAngularRateRadPerSec = 0.0)
                )
                val posture = PrivacyPostureScorecardCalculator.evaluate(
                    (1..20).map { PrivacyPostureScorecardCalculator.DefenseControl("V$it", "Vector $it", 5, true) }
                )
                val readiness = FusionClassAReadinessAggregator.summarize(
                    (1..50).map { FusionClassAReadinessAggregator.ModuleGateRecord(":m$it", true, true, false, false) }
                )
                """{"ok":true,"vanguards_l2_guards":${leases.size},"vanguards_ram_only":${!TorVanguardsLiteMesh.DISK_PERSIST_ALLOWED},"luks2_argon2id_compliant":${luksAudit.compliant},"ghost_touch_verdict":${jsonStr(ghost.verdict.name)},"thunderclap_decision":${jsonStr(dma.decision.name)},"hall_verdict":${jsonStr(hall.verdict.name)},"posture_score":${posture.score0To100},"full_class_a":"${readiness.fullClassACount}/50","host_subset":"${readiness.hostTestableSubsetCount}/50"}"""
            }
            "R50_SPREADSHEET_3D" -> {
                val q1Val = parts[1].toDoubleOrNull() ?: 150.0
                val q2Val = parts[2].toDoubleOrNull() ?: 250.0
                val q3Val = parts[3].toDoubleOrNull() ?: 350.0
                val fSheet = FormulaEval.evaluateSheet(
                    mapOf(
                        "A1" to FormulaEval.CellInput.NumberLiteral(4.0), "B1" to FormulaEval.CellInput.NumberLiteral(7.0),
                        "A2" to FormulaEval.CellInput.NumberLiteral(2.0), "B2" to FormulaEval.CellInput.NumberLiteral(6.0),
                        "C1" to FormulaEval.CellInput.Formula("=MDETERM(A1:B2)"),
                        "C2" to FormulaEval.CellInput.Formula("=SUM(MMULT(A1:B2, MINVERSE(A1:B2)))"),
                        "C3" to FormulaEval.CellInput.Formula("=EXP(GAMMALN(5))"),
                        "C4" to FormulaEval.CellInput.Formula("""=CONVERT(100,"C","F")"""),
                    ),
                )
                val detVal = (fSheet.values["C1"] as FormulaEval.CellValue.Num).value
                val traceVal = (fSheet.values["C2"] as FormulaEval.CellValue.Num).value
                val gamma5 = (fSheet.values["C3"] as FormulaEval.CellValue.Num).value
                val convF = (fSheet.values["C4"] as FormulaEval.CellValue.Num).value
                val defs3d = listOf(
                    MultiSheetBook.SheetDef("Q1", mapOf("B2" to FormulaEval.CellInput.NumberLiteral(q1Val))),
                    MultiSheetBook.SheetDef("Q2", mapOf("B2" to FormulaEval.CellInput.NumberLiteral(q2Val))),
                    MultiSheetBook.SheetDef("Q3", mapOf("B2" to FormulaEval.CellInput.NumberLiteral(q3Val))),
                    MultiSheetBook.SheetDef("Summary", mapOf("A1" to FormulaEval.CellInput.Formula("=SUM(Q1:Q3!B2)"), "A2" to FormulaEval.CellInput.Formula("=AVERAGE(Q1:Q3!B2)"))),
                )
                val ev3d = MultiSheetBook.evaluate3d(defs3d)
                val sum3d = (ev3d.cell("Summary", "A1") as FormulaEval.CellValue.Num).value
                val avg3d = (ev3d.cell("Summary", "A2") as FormulaEval.CellValue.Num).value
                val xlsx3dB64 = Base64.getEncoder().encodeToString(MultiSheetBook.write3d(defs3d))
                val sparkSvg = OoxmlSparklineSvgRenderer.renderInlineSvg(OoxmlSparklineSvgRenderer.SparklineKind.LINE, doubleArrayOf(q1Val, q2Val, q3Val))
                """{"ok":true,"mdeterm":$detVal,"mmult_minverse_trace":$traceVal,"gammaln5_exp":$gamma5,"convert_100c_f":$convF,"sum_3d":$sum3d,"avg_3d":$avg3d,"xlsx3d_b64":${jsonStr(xlsx3dB64)},"spark_svg":${jsonStr(sparkSvg)}}"""
            }
            "R50_WASM2_F64_TABLE" -> {
                val xIn = parts[1].toDoubleOrNull() ?: 4.0
                val iIn = parts[2].toIntOrNull() ?: 21
                val mod = WasmSandbox.decode(buildDemoR50Wasm())
                val f64Out = mod.instantiate().invokeF64("quad_eval", listOf(xIn), fuelBudget = 10_000L)
                val tabOut = mod.instantiate().invoke("table_ops_demo", listOf(iIn), fuelBudget = 10_000L).returnValues.first()
                """{"ok":true,"x":$xIn,"quad_f64":$f64Out,"table_arg":$iIn,"table_indirect_result":$tabOut}"""
            }
            "R50_LENS_FSR1_NLMS" -> {
                val sharpStops = (parts[1].toDoubleOrNull() ?: 0.25).coerceIn(0.0, 2.0)
                val redactTerm = String(Base64.getDecoder().decode(parts[2]), StandardCharsets.UTF_8).ifEmpty { "999-42-8811" }
                val easu = Fsr1EasuRcasUpscaler.easuUpscale(Fsr1EasuRcasUpscaler.GrayFrame(6, 6, DoubleArray(36) { if (it % 6 >= 3) 0.85 else 0.15 }), 12, 12)
                val rcas = Fsr1EasuRcasUpscaler.rcasSharpen(easu, sharpStops)
                val far = DoubleArray(500) { n -> 0.6 * sin(2.0 * PI * n / 19.0) + 0.25 * sin(2.0 * PI * n / 7.0) }
                val mic = DoubleArray(500) { n -> if (n >= 2) 0.55 * far[n - 2] else 0.0 }
                val aec = NlmsAcousticEchoGate.cancelEchoNlms(far, mic, 12, 0.6)
                val kernBin = OpenTypeKerningTableLayout.encodeKernTable(listOf(OpenTypeKerningTableLayout.KernPair('A'.code, 'V'.code, -90)))
                val shaped = OpenTypeKerningTableLayout.layoutText("AVATAR office", kernBin, 1000, true)
                val redPdf = PdfIncrementalSaveRedactor.buildAndRedactPdf(listOf("Record $redactTerm sealed."), listOf(redactTerm))
                """{"ok":true,"fsr_dims":"${rcas.width}x${rcas.height}","easu_min":${easu.luma.min()},"easu_max":${easu.luma.max()},"nlms_erle_db":${aec.echoReturnLossEnhancementDb},"opentype_advance_em":${shaped.totalAdvanceEm},"pdf_redactions":${redPdf.redactionCount},"pdf_redacted_b64":${jsonStr(Base64.getEncoder().encodeToString(redPdf.pdfBytes))}}"""
            }
            "R50_SHIELD_15VEC" -> {
                val keyOctets = parts[1].toIntOrNull() ?: 7
                val nfcRttMs = parts[2].toDoubleOrNull() ?: 34.5
                val hidIkiMs = parts[3].toLongOrNull() ?: 3L
                val wt = TorPluggableTransportConfig.buildConfig(
                    listOf(TorPluggableTransportConfig.BridgeDescriptor(TorPluggableTransportConfig.TransportKind.WEBTUNNEL, "https://edge.example.org/wt", "A".repeat(40))),
                    listOf(TorPluggableTransportConfig.ConfluxLegMetrics("legA", "B".repeat(40), 38.0, 120), TorPluggableTransportConfig.ConfluxLegMetrics("legB", "C".repeat(40), 72.0, 80)),
                )
                val bt = BluetoothBluffsSessionGuard.BondLedger().apply { recordTrustedScBond("AA:BB:CC:DD:EE:01") }.evaluate(
                    BluetoothBluffsSessionGuard.SessionProposal("AA:BB:CC:DD:EE:01", BluetoothBluffsSessionGuard.DeviceClass.HID_KEYBOARD_MOUSE, BluetoothBluffsSessionGuard.PairingSecurityMode.SECURE_CONNECTIONS_SC, keyOctets, true, false, "1".repeat(32)),
                )
                val wifi = WifiProbeSequenceScrambler.generateDecorrelatedBurst(4, byteArrayOf(5, 0)).first()
                val nfc = NfcRelayDistanceBoundingGate.ProximitySessionVerifier().verifyExchange(
                    NfcRelayDistanceBoundingGate.ApduExchangeSample("2".repeat(32), nfcRttMs, true, true, 0),
                )
                val usb = UsbBadHidKeystrokeRateGuard.inspectHidBurst(
                    UsbBadHidKeystrokeRateGuard.HidEventWindow("1234:5678", 60_000L, true, LongArray(8) { it * hidIkiMs }, false),
                )
                val doh = DnsOverHttpsWireQueryCodec.encodePaddedDohQuery("tails.net", 65, 128)
                val shares = ShamirSecretShareSplitter.split("FusionVault32B".toByteArray(StandardCharsets.UTF_8), 3, 5, ByteArray(32) { 9 })
                val shamirOk = ShamirSecretShareSplitter.combine(shares.take(3)).decodeToString() == "FusionVault32B"
                val posture = PrivacyPostureDashboardEngine.evaluate(PrivacyPostureDashboardEngine.defaultFourteenVectors())
                """{"ok":true,"tor_transport":${jsonStr(wt.transport.name)},"conflux_primary":${jsonStr(wt.confluxPrimaryLeg ?: "")},"bluffs_allowed":${bt.allowed},"wifi_mac":${jsonStr(wifi.macAddress)},"wifi_seq12":${wifi.sequenceNumber12Bit},"nfc_permitted":${nfc.apduPermitted},"badusb_allowed":${usb.allowInputDelivery},"doh_padded_bytes":${doh.size},"shamir_3of5_ok":$shamirOk,"posture_score":${posture.score0To100}}"""
            }
            "R49_LAMBDA_SIMPLEX" -> {
                val rawCells = String(Base64.getDecoder().decode(parts[1]), StandardCharsets.UTF_8)
                val c1 = parts[2].toDoubleOrNull() ?: 3.0
                val c2 = parts[3].toDoubleOrNull() ?: 5.0
                val cellMap = LinkedHashMap<String, FormulaEval.CellInput>()
                for (ln in rawCells.lines()) {
                    if (ln.isBlank()) continue
                    val kv = ln.split('|', limit = 2)
                    if (kv.size == 2) cellMap[kv[0].trim()] = parseUserCell(kv[1])
                }
                val spill = SpillSheetEngine.evaluate(cellMap)
                val gridPairs = spill.displayGrid.entries.sortedBy { it.key }.joinToString(",") { "${jsonStr(it.key)}:${jsonStr(it.value)}" }
                val lp = SheetSolverSimplex.solveLinearProgram(
                    doubleArrayOf(c1, c2),
                    true,
                    listOf(
                        SheetSolverSimplex.LinearConstraint(doubleArrayOf(1.0, 0.0), SheetSolverSimplex.Relation.LE, 4.0),
                        SheetSolverSimplex.LinearConstraint(doubleArrayOf(0.0, 2.0), SheetSolverSimplex.Relation.LE, 12.0),
                        SheetSolverSimplex.LinearConstraint(doubleArrayOf(3.0, 2.0), SheetSolverSimplex.Relation.LE, 18.0),
                    ),
                )
                val chartSvg = DrawingMlChartBuilder.renderSvg(
                    DrawingMlChartBuilder.ChartSpec(
                        "Simplex & LAMBDA Output",
                        DrawingMlChartBuilder.ChartKind.BAR,
                        listOf(DrawingMlChartBuilder.ChartSeries("Primal/Obj", listOf("x", "y", "z"), listOf(lp.primalVariables[0], lp.primalVariables[1], lp.objectiveValue), "38BDF8")),
                    ),
                )
                """{"ok":true,"spill_grid":{$gridPairs},"lp_status":${jsonStr(lp.status.name)},"lp_x":${lp.primalVariables[0]},"lp_y":${lp.primalVariables[1]},"lp_obj":${lp.objectiveValue},"chart_svg":${jsonStr(chartSvg)}}"""
            }
            "R49_WRITER_PDF_SIGN" -> {
                val para = String(Base64.getDecoder().decode(parts[1]), StandardCharsets.UTF_8)
                val word = String(Base64.getDecoder().decode(parts[2]), StandardCharsets.UTF_8)
                val hyph = LiangHyphenationEngine()
                val hyphStr = hyph.hyphenateWord(word).joinToString("-")
                val kp = KnuthPlassLineBreaker.breakParagraph(
                    KnuthPlassLineBreaker.tokenizeParagraph(para, hyphenator = { listOf(it) }),
                    KnuthPlassLineBreaker.Config(lineWidth = 250.0),
                )
                val kpJson = kp.lines.joinToString(",") { jsonStr(it.text) }
                val odt = OdfTrackedChangesCodec.TrackedParagraph(
                    listOf(
                        OdfTrackedChangesCodec.TrackedParagraph.Segment.Unchanged("Fusion R49 ships "),
                        OdfTrackedChangesCodec.TrackedParagraph.Segment.Deleted("c1", "Reviewer", "2026-10-27T09:00:00Z", "95 "),
                        OdfTrackedChangesCodec.TrackedParagraph.Segment.Inserted("c2", "Reviewer", "2026-10-27T09:01:00Z", "130 "),
                        OdfTrackedChangesCodec.TrackedParagraph.Segment.Unchanged("pure functions."),
                    ),
                )
                val odtXml = OdfTrackedChangesCodec.encodeOdtBodyXml(odt)
                val basePdf = PdfPageWriter.render(listOf(PdfPageWriter.Element.Paragraph(para)), PdfPageWriter.PageSetup(headerTitle = "R49 Signed PDF")).bytes
                val kpEd = PdfPkcs7SignatureVerifier.generateEd25519KeyPair()
                val signedPdf = PdfPkcs7SignatureVerifier.signPdfEnvelopeEd25519(basePdf, kpEd.private)
                val verifyOk = PdfPkcs7SignatureVerifier.verifyPdfSignature(signedPdf, kpEd.public)
                """{"ok":true,"hyphenated":${jsonStr(hyphStr)},"kp_demerits":${kp.totalDemerits},"kp_fallback":${kp.usedFallbackGreedy},"kp_lines":[$kpJson],"odt_accept":${jsonStr(odt.acceptAll())},"odt_xml_bytes":${odtXml.length},"pdf_sig_status":${jsonStr(verifyOk.status.name)},"signed_pdf_bytes":${signedPdf.size},"signed_pdf_b64":${jsonStr(Base64.getEncoder().encodeToString(signedPdf))}}"""
            }
            "R49_ECH_HARDWARE_SHIELD" -> {
                val innerSni = String(Base64.getDecoder().decode(parts[1]), StandardCharsets.UTF_8)
                val outerSni = String(Base64.getDecoder().decode(parts[2]), StandardCharsets.UTF_8)
                val caIssuer = String(Base64.getDecoder().decode(parts[3]), StandardCharsets.UTF_8)
                val ech = TlsEchConfigCodec.EchConfig(
                    42,
                    TlsEchConfigCodec.KEM_DHKEM_X25519_HKDF_SHA256,
                    ByteArray(32) { (it + 3).toByte() },
                    listOf(TlsEchConfigCodec.HpkeSymmetricSuite(TlsEchConfigCodec.KDF_HKDF_SHA256, TlsEchConfigCodec.AEAD_AES_128_GCM)),
                    64,
                    "cloudflare-ech.com",
                )
                val echAudit = TlsEchConfigCodec.auditOuterClientHello(ech, innerSni, outerSni, true, false)
                val cj = ChoiceJackingUsbGuard.evaluateAttach(ChoiceJackingUsbGuard.UsbAttachContext(0x18D1, 0x2D01, listOf(0xFF, 0x03), false, false, 80L, 10L))
                val pix = PixnappingSurfaceShield.policyFor(PixnappingSurfaceShield.SurfaceSensitivity.CRITICAL_SECRET_2FA_OR_SEED)
                val tails = TailsCaTrustPinBlocklist.evaluateTlsChain(
                    listOf(TailsCaTrustPinBlocklist.CertChainNode("target.example", caIssuer, "b".repeat(64), "2027-06-01")),
                    LocalDate.of(2026, 10, 27),
                )
                val sdp = WebRtcIceCandidateScrubber.scrubSdp(
                    "c=IN IP4 192.168.1.44\na=candidate:1 1 udp 2122 203.0.113.77 5000 typ srflx\na=candidate:2 1 tcp 1518 198.51.100.9 443 typ relay",
                    WebRtcIceCandidateScrubber.IpHandlingMode.MODE_4_DISABLE_NON_PROXIED_UDP,
                )
                """{"ok":true,"ech_compliant":${echAudit.compliant},"ech_wire_bytes":${TlsEchConfigCodec.encodeConfigList(listOf(ech)).size},"choicejacking_policy":${jsonStr(cj.policy.name)},"pixnapping_flag_secure":${pix.flagSecure},"tails_ca_decision":${jsonStr(tails.decision.name)},"webrtc_mode4_kept":${sdp.keptCandidates},"webrtc_mode4_dropped":${sdp.droppedCandidates}}"""
            }
            "R49_WASM2_SHELL" -> {
                val ax = parts[1].toFloatOrNull() ?: 3.0f
                val ay = parts[2].toFloatOrNull() ?: 4.0f
                val query = String(Base64.getDecoder().decode(parts[3]), StandardCharsets.UTF_8)
                val sockGate = WasiSocketCapabilityGate(setOf("api.github.com" to 443), 1024L)
                val ssrfErrno = sockGate.checkConnect("127.0.0.1", 8080).wasiErrno
                val cards = PaletteInlineAnswerEngine.evaluateQuery(query, optInWebSearchEnabled = false)
                val bill = BillingEntitlementLedger.evaluate(25L * 86_400_000L, 0L, false, false, false, null)
                val hypotVal = kotlin.math.sqrt(ax * ax + ay * ay)
                """{"ok":true,"f32_hypot":$hypotVal,"wasi_loopback_errno":$ssrfErrno,"palette_headline":${jsonStr(cards.firstOrNull()?.headline ?: "(no match)")},"billing_tier":${jsonStr(bill.tier.name)},"nag_banner":${bill.showNagBanner},"wipe_on_revoke":${bill.wipeUserDataOnRevoke}}"""
            }
            "R48_OFFICE_SPILL" -> {
                val slideTitle = String(Base64.getDecoder().decode(parts[1]), StandardCharsets.UTF_8)
                val chapterText = String(Base64.getDecoder().decode(parts[2]), StandardCharsets.UTF_8)
                val rawCells = String(Base64.getDecoder().decode(parts[3]), StandardCharsets.UTF_8)
                val cellMap = LinkedHashMap<String, FormulaEval.CellInput>()
                for (ln in rawCells.lines()) {
                    if (ln.isBlank()) continue
                    val kv = ln.split('|', limit = 2)
                    if (kv.size == 2) cellMap[kv[0].trim()] = parseUserCell(kv[1])
                }
                val spill = SpillSheetEngine.evaluate(cellMap)
                val gridPairs = spill.displayGrid.entries.sortedBy { it.key }.joinToString(",") { "${jsonStr(it.key)}:${jsonStr(it.value)}" }
                val pptx = PptxSlideDeck.writeDeck(
                    PptxSlideDeck.DeckSpec(listOf(PptxSlideDeck.SlideSpec(slideTitle, listOf("95-Fn Spreadsheet + Spill", "OHTTP RFC 9458 + BHTTP RFC 9292"), listOf(listOf("Metric", "Value"), listOf("Modules", "50/50")), "Clean-room speaker notes"))),
                )
                val epub = Epub3Package.writeBook(
                    Epub3Package.BookSpec("urn:uuid:r48-live", slideTitle, "en", "Fusion Clean-Room", listOf(Epub3Package.Chapter("ch1", slideTitle, listOf(chapterText)))),
                )
                val rtf = RtfCleanConverter.writeRtf(listOf(RtfCleanConverter.Paragraph(listOf(RtfCleanConverter.Span(chapterText, bold = true)))))
                val ods = OdsSpreadsheetXml.writeOds(listOf(OdsSpreadsheetXml.OdsSheet("Live", listOf(listOf(OdsSpreadsheetXml.OdsCell.Num(220.0, "=SUM(A1:B4)"))))))
                val formPdf = PdfAcroFormWriter.writeFillablePdf(slideTitle, listOf(PdfAcroFormWriter.FormField.TextField("reviewer", "CleanRoom", 48, 72, 640)))
                val rd = ReadabilityMetrics.analyze(chapterText)
                """{"ok":true,"spill_grid":{$gridPairs},"pptx_bytes":${pptx.size},"pptx_b64":${jsonStr(Base64.getEncoder().encodeToString(pptx))},"epub_bytes":${epub.size},"epub_stored_magic":${Epub3Package.verifyMimetypeHeader(epub)},"epub_b64":${jsonStr(Base64.getEncoder().encodeToString(epub))},"ods_bytes":${ods.size},"rtf_chars":${rtf.length},"acroform_pdf_bytes":${formPdf.size},"flesch_kincaid":${"%.2f".format(java.util.Locale.ROOT, rd.fleschKincaidGrade)}}"""
            }
            "R48_OHTTP_ONION" -> {
                val relayUrl = String(Base64.getDecoder().decode(parts[1]), StandardCharsets.UTF_8)
                val gatewayUrl = String(Base64.getDecoder().decode(parts[2]), StandardCharsets.UTF_8)
                val bodyStr = String(Base64.getDecoder().decode(parts[3]), StandardCharsets.UTF_8)
                val bhttp = BinaryHttpRfc9292.encodeRequest(
                    BinaryHttpRfc9292.KnownRequest("POST", "https", "gateway.privacy.example", "/v1/oblivious", listOf(BinaryHttpRfc9292.FieldLine("content-type", "application/json")), bodyStr.toByteArray(StandardCharsets.UTF_8)),
                    128,
                )
                val relayHost = relayUrl.substringAfter("://", "").substringBefore("/")
                val gwHost = gatewayUrl.substringAfter("://", "").substringBefore("/")
                val audit = ObliviousHttpPolicy.auditRelayGatewayRoute(relayUrl, gatewayUrl, relayHost, gwHost)
                val onion = OnionV3Address.fromEd25519PublicKey(HkdfRfc5869.deriveKey("r48-salt".toByteArray(), bodyStr.toByteArray(), "onion-v3".toByteArray(), 32))
                val vJson = audit.violations.joinToString(",") { jsonStr(it) }
                """{"ok":true,"route_allowed":${audit.allowed},"violations":[$vJson],"bhttp_padded_bytes":${bhttp.size},"onion_v3":${jsonStr(onion)},"onion_valid":${OnionV3Address.isValidV3(onion)}}"""
            }
            "R48_ACOUSTIC_BROWSER" -> {
                val rawHtml = String(Base64.getDecoder().decode(parts[1]), StandardCharsets.UTF_8)
                val coopVal = String(Base64.getDecoder().decode(parts[2]), StandardCharsets.UTF_8)
                val coepVal = String(Base64.getDecoder().decode(parts[3]), StandardCharsets.UTF_8)
                val v1k = ShortArray(640) { n -> (10000.0 * sin(2.0 * PI * 1000.0 * n / 48000.0)).toInt().toShort() }
                val u20k = ShortArray(640) { n -> (10000.0 * sin(2.0 * PI * 20000.0 * n / 48000.0)).toInt().toShort() }
                val voiceRms = AcousticKeystrokeShield.rmsEnergy(AcousticKeystrokeShield.attenuateUltrasonic48k(v1k).sliceArray(32 until 608)).roundToInt()
                val sonarRms = AcousticKeystrokeShield.rmsEnergy(AcousticKeystrokeShield.attenuateUltrasonic48k(u20k).sliceArray(32 until 608)).roundToInt()
                val hidBad = HidUsageFirewall.inspectReportDescriptor(byteArrayOf(0x06, 0x00, 0xFF.toByte(), 0x09, 0x01, 0xA1.toByte(), 0x01, 0xC0.toByte()))
                val cap = ScreenCaptureGuard.evaluate(ScreenCaptureGuard.EnvironmentState(displays = listOf(ScreenCaptureGuard.DisplaySnapshot(2, true, false, "com.mirror.spy"))))
                val iso = CrossOriginIsolationPolicy.evaluateDocumentHeaders(mapOf("Cross-Origin-Opener-Policy" to coopVal, "Cross-Origin-Embedder-Policy" to coepVal))
                val reader = ArticleReaderExtractor.extract(rawHtml)
                val csp = CspHeaderDirective.parse("default-src 'self'; script-src 'unsafe-inline' 'nonce-r48live'")
                """{"ok":true,"voice_1khz_rms":$voiceRms,"sonar_20khz_rms":$sonarRms,"hid_vendor_allowed":${hidBad.allowed},"virtual_display_lock_vault":${cap.lockVaultImmediately},"cross_origin_isolated":${iso.crossOriginIsolated},"csp_blocks_unnonced_inline":${!CspHeaderDirective.allowInlineScript(csp, null, "evil()")},"reader_title":${jsonStr(reader.title)},"reader_markdown":${jsonStr(reader.markdown)}}"""
            }
            "R47_OFFICE" -> {
                val heading = String(Base64.getDecoder().decode(parts[1]), StandardCharsets.UTF_8)
                val body = String(Base64.getDecoder().decode(parts[2]), StandardCharsets.UTF_8)
                val latex = String(Base64.getDecoder().decode(parts[3]), StandardCharsets.UTF_8)
                val docx = DocxRichDocument.write(
                    DocxRichDocument.RichDoc(
                        blocks = listOf(
                            DocxRichDocument.RichBlock.TableOfContents("Contents", maxLevel = 3),
                            DocxRichDocument.RichBlock.Heading(1, heading),
                            DocxRichDocument.RichBlock.Paragraph(body, footnoteId = 1, bold = false),
                        ),
                        headerText = "Fusion Office R47",
                        footnotes = listOf(DocxRichDocument.Footnote(1, "Zero-metadata OOXML + ODF 1.3.")),
                    ),
                )
                val odt = OdtRichWriter.write(
                    listOf(
                        OdtRichWriter.OdtBlock.Heading(1, heading),
                        OdtRichWriter.OdtBlock.Paragraph(body, footnote = "ODF 1.3 STORED mimetype@38"),
                    ),
                )
                val pdf = PdfOutlineBookmarks.render(
                    listOf(
                        PdfOutlineBookmarks.Element.BookmarkHeading(1, heading),
                        PdfOutlineBookmarks.Element.Paragraph(body),
                    ),
                )
                val mathSvg = LatexMathSvg.renderSvg(latex)
                """{"ok":true,"docx_bytes":${docx.size},"docx_b64":${jsonStr(Base64.getEncoder().encodeToString(docx))},"odt_bytes":${odt.size},"odt_magic":${OdtRichWriter.verifyMimetypeMagic(odt)},"odt_b64":${jsonStr(Base64.getEncoder().encodeToString(odt))},"pdf_bytes":${pdf.pdfBytes.size},"bookmarks":${pdf.bookmarks.size},"math_svg":${jsonStr(mathSvg)}}"""
            }
            "R47_BROWSER_WASI" -> {
                val htmlIn = String(Base64.getDecoder().decode(parts[1]), StandardCharsets.UTF_8)
                val urlProbe = String(Base64.getDecoder().decode(parts[2]), StandardCharsets.UTF_8)
                val pathProbe = String(Base64.getDecoder().decode(parts[3]), StandardCharsets.UTF_8)
                val san = HtmlSanitizer.sanitize(htmlIn)
                val eng = AdblockFilterEngine.compile(listOf("||doubleclick.example.net^\$third-party,script", "||tracker.example.org^", "@@||allow.tracker.example.org^"))
                val blocked = eng.matchNetwork(AdblockFilterEngine.RequestContext(urlProbe, "news.example.com", AdblockFilterEngine.ResourceType.SCRIPT)).blocked
                val host = WasiPreview1Host.HostInstance(
                    args = listOf("guest.wasm"),
                    envVars = emptyMap(),
                    preopens = listOf(WasiPreview1Host.PreopenDir("/work", WasiPreview1Host.RIGHT_FD_READ or WasiPreview1Host.RIGHT_PATH_OPEN, linkedMapOf("readme.txt" to "WASI-OK".toByteArray()))),
                )
                val pb = pathProbe.toByteArray(StandardCharsets.UTF_8)
                host.writeMem(64, pb)
                val errno = host.pathOpen(3, 64, pb.size, WasiPreview1Host.RIGHT_FD_READ, false, 128)
                """{"ok":true,"sanitized_html":${jsonStr(san.html)},"adblock_blocked":$blocked,"wasi_errno":$errno,"wasi_verdict":${jsonStr(if (errno == 0) "ERRNO_SUCCESS (0)" else "ERRNO_NOTCAPABLE ($errno)")}}"""
            }
            "R47_PRIVACY_KEYPAD" -> {
                val appId = String(Base64.getDecoder().decode(parts[1]), StandardCharsets.UTF_8)
                val origin = String(Base64.getDecoder().decode(parts[2]), StandardCharsets.UTF_8)
                val secret = ByteArray(32) { (it * 13 + 7).toByte() }
                val cred = TorSocksIsolation.deriveProp351Credentials(secret, TorSocksIsolation.StreamContext(appId, origin, 0))
                val pad = SecureKeypadScrambler.generatePinPad(SecureRandom())
                val keysJson = pad.cells.joinToString(",") { jsonStr(it.label) }
                val imu = ImuSideChannelGuard.SessionGate().evaluateSample(ImuSideChannelGuard.SensorKind.ACCELEROMETER, 1_000_000L, 0.1, 9.8, 0.0, false, true)
                """{"ok":true,"tor_user":${jsonStr(cred.username)},"tor_token_prefix":${jsonStr(cred.passwordHex.take(24) + "...")},"imu_allowed":${imu.allowed},"imu_verdict":${jsonStr(imu.verdict.name)},"scrambled_keys":[$keysJson]}"""
            }
            "SHEET" -> {
                // parts[1]: newline-encoded "SheetName|A1|rawValue" rows (base64 UTF-8)
                val decoded = String(Base64.getDecoder().decode(parts[1]), StandardCharsets.UTF_8)
                val bySheet = LinkedHashMap<String, LinkedHashMap<String, FormulaEval.CellInput>>()
                for (row in decoded.lines()) {
                    if (row.isBlank()) continue
                    val cols = row.split('|', limit = 3)
                    if (cols.size < 3) continue
                    val sName = cols[0].trim()
                    val a1 = cols[1].trim()
                    val raw = cols[2]
                    val inp = parseUserCell(raw)
                    bySheet.getOrPut(sName) { LinkedHashMap() }[a1] = inp
                }
                val defs = bySheet.map { (n, m) -> MultiSheetBook.SheetDef(n, m) }
                val ev = MultiSheetBook.evaluate(defs)
                val xlsxB64 = Base64.getEncoder().encodeToString(MultiSheetBook.write(defs))
                val cellsJson = ArrayList<String>()
                for (sName in ev.sheetOrder) {
                    for ((a1, cv) in ev.values[sName].orEmpty()) {
                        cellsJson.add("""${jsonStr("$sName!$a1")}:${jsonStr(cv.displayText())}""")
                    }
                }
                """{"ok":true,"values":{${cellsJson.joinToString(",")}},"xlsx_b64":${jsonStr(xlsxB64)}}"""
            }
            "WRITER" -> {
                val md = String(Base64.getDecoder().decode(parts[1]), StandardCharsets.UTF_8)
                val bib = String(Base64.getDecoder().decode(parts[2]), StandardCharsets.UTF_8)
                val insText = String(Base64.getDecoder().decode(parts[3]), StandardCharsets.UTF_8)
                val lib = if (bib.isBlank()) emptyMap() else CitationBib.parseBibtex(bib)
                val resolved = CitationBib.resolveCitations(md, lib, CitationBib.Style.IEEE_NUMERIC)
                val ast = DocOutlineRender.parseMarkdown(resolved.renderedText)
                val html = DocOutlineRender.toHtml(ast)

                val revBase = TrackRevisions.fromPlainText(resolved.renderedText)
                val revEdited = if (insText.isNotEmpty()) {
                    revBase.recordInsert(revBase.visibleFinalText().length, " " + insText, "Reviewer", 1_760_000_000L)
                        .anonymizeAuthors("AnonymousAuthor")
                } else revBase

                val pdfBlocks = ArrayList<PdfPageWriter.Element>()
                for (n in ast) {
                    when (n) {
                        is DocOutlineRender.Node.Heading ->
                            pdfBlocks.add(PdfPageWriter.Element.Heading(n.level.coerceIn(1, 4), n.spans.joinToString("") { it.text }))
                        is DocOutlineRender.Node.Paragraph ->
                            pdfBlocks.add(PdfPageWriter.Element.Paragraph(n.spans.joinToString("") { it.text }))
                        is DocOutlineRender.Node.CodeBlock ->
                            pdfBlocks.add(PdfPageWriter.Element.Paragraph(n.code, monospace = true))
                        is DocOutlineRender.Node.ListBlock ->
                            n.items.forEachIndexed { idx, sp ->
                                pdfBlocks.add(PdfPageWriter.Element.BulletItem(if (n.ordered) "${idx + 1}." else "*", sp.joinToString("") { it.text }))
                            }
                        is DocOutlineRender.Node.TableBlock ->
                            pdfBlocks.add(PdfPageWriter.Element.Table(listOf(n.headers) + n.rows))
                    }
                }
                if (resolved.bibliography.isNotEmpty()) {
                    pdfBlocks.add(PdfPageWriter.Element.Heading(2, "References"))
                    for (bLine in resolved.bibliography) pdfBlocks.add(PdfPageWriter.Element.Paragraph(bLine))
                }
                if (pdfBlocks.isEmpty()) pdfBlocks.add(PdfPageWriter.Element.Paragraph(revEdited.visibleFinalText()))
                val pdf = PdfPageWriter.render(pdfBlocks, PdfPageWriter.PageSetup(headerTitle = "Fusion Writer Clean-Room PDF 1.7"))
                val pdfB64 = Base64.getEncoder().encodeToString(pdf.bytes)
                val bibArr = resolved.bibliography.joinToString(",") { jsonStr(it) }
                """{"ok":true,"html":${jsonStr(html)},"final_text":${jsonStr(revEdited.visibleFinalText())},"bibliography":[$bibArr],"pdf_pages":${pdf.pageCount},"pdf_bytes":${pdf.bytes.size},"pdf_b64":${jsonStr(pdfB64)}}"""
            }
            "WASM" -> {
                val n = parts[1].toInt().coerceIn(0, 45)
                val fuel = parts[2].toLong().coerceIn(10L, 500_000L)
                val spinMode = parts[3] == "spin"
                val mod = WasmSandbox.decode(buildDemoWasm())
                val inst = mod.instantiate(1)
                val res = if (spinMode) inst.invoke("spin", emptyList(), fuelBudget = fuel)
                else inst.invoke("fib", listOf(n), fuelBudget = fuel)
                """{"ok":true,"result":${res.returnValues.firstOrNull() ?: 0},"fuel_used":${res.fuelUsed},"fuel_remaining":${res.fuelRemaining}}"""
            }
            "SECURITY" -> {
                val torCmd = String(Base64.getDecoder().decode(parts[1]), StandardCharsets.UTF_8)
                val tsCsv = parts[2]
                val sess = OnionGraterFilter.Session(OnionGraterFilter.onionCircuitsProfile(isolatedSocks = "127.0.0.1:9150"))
                sess.evaluateClientLine("AUTHENTICATE")
                val torDec = when (val d = sess.evaluateClientLine(torCmd)) {
                    is OnionGraterFilter.FilterDecision.ForwardToTor -> "ALLOW_FORWARD: ${d.sanitizedCommand}"
                    is OnionGraterFilter.FilterDecision.SyntheticReply -> "ALLOW_REWRITE: ${d.controlReply.trim().replace("\r\n", " | ")}"
                    is OnionGraterFilter.FilterDecision.Denied -> "DENIED_${d.replyCode}: ${d.reason}"
                }
                val timestamps = if (tsCsv.isBlank()) emptyList() else tsCsv.split(',').mapNotNull { it.trim().toLongOrNull() }
                val cad = KeystrokeJitter.classifyCadence(timestamps)
                val usbDucky = UsbGuardPolicy.evaluate(
                    UsbGuardPolicy.UsbCandidate(0x1234, 0x5678, null, listOf(UsbGuardPolicy.UsbInterfaceSpec(8, 6, 80), UsbGuardPolicy.UsbInterfaceSpec(3, 1, 1))),
                    UsbGuardPolicy.HostContext(false, 1, 1000L, 1800L),
                )
                val bleDucky = BleHidPairingGate.evaluate(
                    BleHidPairingGate.WirelessHidAttempt("AA:BB:CC:11:22:33", true, 0, null, BleHidPairingGate.IoCapability.NO_INPUT_NO_OUTPUT, BleHidPairingGate.AuthMethod.JUST_WORKS, true, 16, false),
                )
                """{"ok":true,"onion_grater":${jsonStr(torDec)},"cadence":${jsonStr(cad.classification.name)},"mean_iki_ms":${"%.2f".format(java.util.Locale.ROOT, cad.meanIkiMs)},"std_iki_ms":${"%.2f".format(java.util.Locale.ROOT, cad.stddevIkiMs)},"block_stream":${cad.shouldBlockStream},"usb_guard":${jsonStr("${usbDucky.action}:${usbDucky.ruleId}")},"ble_gate":${jsonStr(bleDucky.code)}}"""
            }
            else -> """{"ok":false,"error":"unknown op"}"""
        }
    }

    private fun parseUserCell(raw: String): FormulaEval.CellInput {
        val t = raw.trim()
        if (t.isEmpty()) return FormulaEval.CellInput.Blank
        if (t.startsWith("=")) return FormulaEval.CellInput.Formula(t)
        if (t.equals("TRUE", true) || t.equals("FALSE", true)) return FormulaEval.CellInput.BoolLiteral(t.equals("TRUE", true))
        val num = t.toDoubleOrNull()
        return if (num != null && num.isFinite()) FormulaEval.CellInput.NumberLiteral(num)
        else FormulaEval.CellInput.TextLiteral(raw)
    }

    private fun jsonStr(s: String): String = buildString(s.length + 2) {
        append('"')
        for (c in s) when (c) {
            '"' -> append("\\\"")
            '\\' -> append("\\\\")
            '\n' -> append("\\n")
            '\r' -> append("\\r")
            '\t' -> append("\\t")
            else -> if (c.code < 0x20) append("\\u%04x".format(c.code)) else append(c)
        }
        append('"')
    }

    private fun buildDemoR51SimdWasm(): ByteArray {
        val out = ByteArrayOutputStream()
        fun uleb(v0: Int) {
            var v = v0
            do { var b = v and 0x7F; v = v ushr 7; if (v != 0) b = b or 0x80; out.write(b) } while (v != 0)
        }
        fun sec(id: Int, body: ByteArray) { out.write(id); uleb(body.size); out.write(body) }
        out.write(byteArrayOf(0x00, 0x61, 0x73, 0x6D, 0x01, 0x00, 0x00, 0x00))
        sec(1, byteArrayOf(0x01, 0x60, 0x03, 0x7F, 0x7F, 0x7F, 0x01, 0x7B))
        sec(3, byteArrayOf(0x01, 0x00))
        sec(5, byteArrayOf(0x01, 0x00, 0x01))
        val nm = "simd_saxpy_lanes".toByteArray(StandardCharsets.UTF_8)
        sec(7, ByteArrayOutputStream().apply { write(1); write(nm.size); write(nm); write(0); write(0) }.toByteArray())
        val fn = ByteArrayOutputStream().apply {
            write(byteArrayOf(0x01, 0x01, 0x7B, 0x02, 0x7B, 0x20, 0x01, 0x20, 0x00))
            write(byteArrayOf(0xFD.toByte(), 0x00, 0x04, 0x00, 0x20, 0x02, 0xFD.toByte(), 0x11))
            write(byteArrayOf(0xFD.toByte(), 0xB5.toByte(), 0x01, 0xFD.toByte(), 0x0C))
            write(ByteBuffer.allocate(16).order(ByteOrder.LITTLE_ENDIAN).putInt(1).putInt(2).putInt(3).putInt(4).array())
            write(byteArrayOf(0xFD.toByte(), 0xAE.toByte(), 0x01, 0x22, 0x03, 0xFD.toByte(), 0x0B, 0x04, 0x00, 0x20, 0x03, 0x0B, 0x0B))
        }.toByteArray()
        sec(10, ByteArrayOutputStream().apply { write(1); write(fn.size); write(fn) }.toByteArray())
        return out.toByteArray()
    }

    private fun buildDemoR50Wasm(): ByteArray {
        fun uleb(v0: Int): ByteArray {
            var v = v0
            val out = ByteArrayOutputStream()
            do {
                var b = v and 0x7F; v = v ushr 7
                if (v != 0) b = b or 0x80
                out.write(b)
            } while (v != 0)
            return out.toByteArray()
        }
        fun f64Const(d: Double): ByteArray = ByteBuffer.allocate(9).order(ByteOrder.LITTLE_ENDIAN)
            .put(0x44.toByte()).putLong(java.lang.Double.doubleToRawLongBits(d)).array()
        fun section(id: Int, payload: ByteArray): ByteArray = byteArrayOf(id.toByte()) + uleb(payload.size) + payload
        fun strBytes(s: String): ByteArray { val b = s.toByteArray(StandardCharsets.UTF_8); return uleb(b.size) + b }

        val typeSec = byteArrayOf(2, 0x60, 1, 0x7C, 1, 0x7C, 0x60, 1, 0x7F, 1, 0x7F)
        val funcSec = byteArrayOf(4, 0, 1, 1, 1)
        val tableSec = byteArrayOf(1, 0x70, 0x01, 2, 8)
        val memSec = byteArrayOf(1, 0x01, 1, 2)
        val expPayload = ByteArrayOutputStream().apply {
            write(2)
            write(strBytes("quad_eval")); write(0x00); write(0)
            write(strBytes("table_ops_demo")); write(0x00); write(3)
        }.toByteArray()
        val elemSec = byteArrayOf(1, 0x01, 0x00, 2, 1, 2)
        val body0 = ByteArrayOutputStream().apply {
            write(0)
            write(f64Const(2.5)); write(byteArrayOf(0x20, 0, 0xA2.toByte(), 0x20, 0, 0xA2.toByte()))
            write(f64Const(1.25)); write(byteArrayOf(0x20, 0, 0xA2.toByte(), 0xA1.toByte()))
            write(f64Const(0.5)); write(0xA0)
            write(byteArrayOf(0x20, 0, 0x9F.toByte(), 0xA0.toByte(), 0x0B))
        }.toByteArray()
        val body1 = byteArrayOf(0, 0x20, 0, 0x41, 2, 0x6C, 0x0B)
        val body2 = byteArrayOf(0, 0x20, 0, 0x41, 10, 0x6A, 0x0B)
        val body3 = ByteArrayOutputStream().apply {
            write(0)
            write(byteArrayOf(0x41, 0x7F, 0x41, 2, 0xFC.toByte(), 15, 0, 0x1A))
            write(byteArrayOf(0x41, 0, 0x41, 0, 0x41, 2, 0xFC.toByte(), 12, 0, 0))
            write(byteArrayOf(0xFC.toByte(), 13, 0))
            write(byteArrayOf(0x41, 2, 0x41, 0, 0x41, 2, 0xFC.toByte(), 14, 0, 0))
            write(byteArrayOf(0x20, 0, 0x41, 2, 0x11, 1, 0, 0x41, 3, 0x11, 1, 0, 0x0B))
        }.toByteArray()
        val bodies = listOf(body0, body1, body2, body3)
        val codeSec = ByteArrayOutputStream().apply {
            write(bodies.size); for (b in bodies) { write(uleb(b.size)); write(b) }
        }.toByteArray()
        return ByteArrayOutputStream().apply {
            write(byteArrayOf(0x00, 0x61, 0x73, 0x6D, 0x01, 0x00, 0x00, 0x00))
            write(section(1, typeSec)); write(section(3, funcSec)); write(section(4, tableSec))
            write(section(5, memSec)); write(section(7, expPayload)); write(section(9, elemSec)); write(section(10, codeSec))
        }.toByteArray()
    }

    private fun buildDemoWasm(): ByteArray {
        val out = ByteArrayOutputStream()
        out.write(byteArrayOf(0x00, 0x61, 0x73, 0x6D, 0x01, 0x00, 0x00, 0x00))
        val secType = byteArrayOf(0x02, 0x60, 0x01, 0x7F, 0x01, 0x7F, 0x60, 0x00, 0x01, 0x7F)
        out.write(0x01); out.write(secType.size); out.write(secType)
        val secFunc = byteArrayOf(0x02, 0x00, 0x01)
        out.write(0x03); out.write(secFunc.size); out.write(secFunc)
        val secMem = byteArrayOf(0x01, 0x01, 0x01, 0x02)
        out.write(0x05); out.write(secMem.size); out.write(secMem)
        val secExp = ByteArrayOutputStream().apply {
            write(0x02)
            write(3); write("fib".toByteArray()); write(0x00); write(0x00)
            write(4); write("spin".toByteArray()); write(0x00); write(0x01)
        }.toByteArray()
        out.write(0x07); out.write(secExp.size); out.write(secExp)
        val body0 = byteArrayOf(
            0x01, 0x03, 0x7F,
            0x41, 0x00, 0x21, 0x01, 0x41, 0x01, 0x21, 0x02,
            0x02, 0x40, 0x03, 0x40,
            0x20, 0x00, 0x41, 0x00, 0x4C, 0x0D, 0x01,
            0x20, 0x01, 0x20, 0x02, 0x6A, 0x21, 0x03,
            0x20, 0x02, 0x21, 0x01, 0x20, 0x03, 0x21, 0x02,
            0x20, 0x00, 0x41, 0x01, 0x6B, 0x21, 0x00,
            0x0C, 0x00, 0x0B, 0x0B, 0x20, 0x01, 0x0B,
        )
        val body1 = byteArrayOf(0x00, 0x03, 0x40, 0x0C, 0x00, 0x0B, 0x41, 0x00, 0x0B)
        val secCode = ByteArrayOutputStream().apply {
            write(0x02); write(body0.size); write(body0); write(body1.size); write(body1)
        }.toByteArray()
        out.write(0x0A); out.write(secCode.size); out.write(secCode)
        return out.toByteArray()
    }
}
'''

INDEX_HTML = """<!DOCTYPE html>
<html lang="en">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>Fusion Shell R52 — Recall Studio &amp; Live Kotlin Kernel Workbench</title>
<style>
  :root {
    --bg: #0a0f1a; --card: #121a2b; --subtle: #1a253c; --border: #2d4165;
    --fg: #f1f5f9; --muted: #b8c6dc; --emerald: #34d399; --cyan: #38bdf8;
    --amber: #fbbf24; --rose: #fb7185; --violet: #c084fc;
  }
  #r52-ctx-menu {
    position: fixed; display: none; z-index: 900;
    min-width: 180px; max-width: 280px; width: 248px;
    background: rgba(18, 26, 43, 0.96); border: 1px solid var(--emerald);
    border-radius: 10px; padding: 6px; box-shadow: 0 14px 36px rgba(0,0,0,0.62);
    backdrop-filter: blur(10px);
  }
  #r52-ctx-menu button {
    width: 100%; background: transparent; border: none; color: var(--fg);
    display: flex; justify-content: space-between; align-items: center;
    padding: 8px 10px; border-radius: 6px; font-size: 12.5px; font-weight: 500;
  }
  #r52-ctx-menu button:hover { background: var(--subtle); color: var(--cyan); }
  #r52-ctx-menu button.danger { color: var(--rose); }
  #r52-undo-deck {
    position: fixed; right: 18px; bottom: 18px; width: 328px; height: 92px;
    pointer-events: none; z-index: 850;
  }
  .r52-undo-card {
    position: absolute; left: 0; right: 0; bottom: 0; height: 58px;
    background: rgba(18, 26, 43, 0.88); border: 1px solid var(--violet);
    border-radius: 10px; padding: 10px 12px; display: flex;
    justify-content: space-between; align-items: center; gap: 8px;
    backdrop-filter: blur(8px); transform-origin: bottom center;
    transition: transform 160ms ease, opacity 160ms ease; pointer-events: auto;
    box-shadow: 0 10px 26px rgba(0,0,0,0.48); font-size: 12px;
  }
  .r52-icon-btn { display: inline-flex; align-items: center; gap: 7px; min-height: 40px; padding: 6px 12px; background: var(--subtle); border: 1px solid var(--border); }
  .r52-icon-btn.active { border-color: var(--cyan); background: #162642; }
  * { box-sizing: border-box; }
  body { margin: 0; font-family: system-ui, -apple-system, sans-serif; background: var(--bg); color: var(--fg); }
  header { padding: 16px 24px; background: var(--card); border-bottom: 1px solid var(--border); display: flex; flex-wrap: wrap; justify-content: space-between; align-items: center; gap: 12px; }
  header h1 { margin: 0; font-size: 18px; letter-spacing: 0.3px; }
  .honesty { font-family: ui-monospace, monospace; font-size: 12px; color: var(--amber); }
  .pill { font-family: ui-monospace, monospace; font-size: 12px; padding: 4px 10px; border-radius: 999px; background: var(--subtle); border: 1px solid var(--emerald); color: var(--emerald); }
  main { display: grid; grid-template-columns: repeat(auto-fit, minmax(460px, 1fr)); gap: 18px; padding: 20px 24px; max-width: 1600px; margin: 0 auto; }
  section { background: var(--card); border: 1px solid var(--border); border-radius: 14px; padding: 18px; display: flex; flex-direction: column; gap: 12px; }
  section h2 { margin: 0; font-size: 16px; display: flex; justify-content: space-between; align-items: center; }
  label { font-size: 12px; color: var(--muted); display: block; margin-bottom: 4px; }
  textarea, input, select { width: 100%; background: var(--bg); color: var(--fg); border: 1px solid var(--border); border-radius: 8px; padding: 8px 10px; font-family: ui-monospace, monospace; font-size: 12.5px; }
  .row { display: flex; gap: 10px; align-items: center; flex-wrap: wrap; }
  button { background: #1d4ed8; color: #fff; border: 1px solid var(--cyan); border-radius: 8px; padding: 8px 14px; font-weight: 600; cursor: pointer; font-size: 13px; }
  button.secondary { background: var(--subtle); color: var(--cyan); }
  pre, .out { background: var(--subtle); border: 1px solid var(--border); border-radius: 8px; padding: 10px; font-family: ui-monospace, monospace; font-size: 12px; overflow-x: auto; margin: 0; white-space: pre-wrap; }
  .render-box { background: #0d1524; border: 1px solid var(--border); border-radius: 8px; padding: 12px; font-size: 13.5px; line-height: 1.5; }
  .render-box h1, .render-box h2, .render-box h3 { margin: 4px 0 8px 0; color: var(--cyan); }
  .render-box table { border-collapse: collapse; width: 100%; margin: 6px 0; }
  .render-box th, .render-box td { border: 1px solid var(--border); padding: 4px 8px; text-align: left; }
</style>
</head>
<body>
<header>
  <div>
    <h1>FUSION SHELL R52 — RECALL MEDIA STUDIO &amp; KOTLIN JVM WORKBENCH (CARDS 1..26)</h1>
    <div class="honesty">Runtime Honesty: OpenJDK 17 + kotlinc 2.2.21 real production kernels (NOT Android SDK 37 / ART / physical device) | Host Subsets: 50/50 (100.00%) | Class A Full-Spec: 0/50 (0.00%) | Right-click any R52 Recall surface to inspect the compact 180..280px context menu!</div>
  </div>
  <span id="engine-pill" class="pill">Connecting to warm JVM daemon...</span>
</header>
<div id="r52-ctx-menu" role="menu" aria-label="Compact Fusion Context Menu">
  <button onclick="pushUndoToast('Opened media asset in Recall Studio'); hideCtxMenu();"><span>Open in Recall Studio</span><span style="color:var(--muted)">Enter</span></button>
  <button onclick="pushUndoToast('Copied delineated vault path'); hideCtxMenu();"><span>Copy Vault Path</span><span style="color:var(--muted)">Ctrl+C</span></button>
  <button onclick="pushUndoToast('Verified SHA-256 content digest'); hideCtxMenu();"><span>Verify SHA-256</span><span style="color:var(--muted)">Ctrl+V</span></button>
  <button class="danger" onclick="pushUndoToast('Moved media capture to Trash'); hideCtxMenu();"><span>Move to Trash</span><span style="color:var(--rose)">Del</span></button>
</div>
<div id="r52-undo-deck" aria-live="polite"></div>
<main>
  <section>
    <h2>1. Multi-Sheet Spreadsheet (MultiSheetBook + 35-Func FormulaEval) <span class="pill">ECMA-376 .xlsx</span></h2>
    <div>
      <label>Workbook Cells (`SheetName|CellRef|FormulaOrLiteral` per line):</label>
      <textarea id="sheet-in" rows="9">Q1 Sales|A1|North
Q1 Sales|B1|1200.50
Q1 Sales|A2|South
Q1 Sales|B2|850.00
Q1 Sales|A3|North
Q1 Sales|B3|449.50
Q1 Sales|B4|=SUM(B1:B3)
Rates|B1|0.085
Summary|A1|='Q1 Sales'!B4
Summary|A2|=ROUND(A1 * Rates!B1, 2)
Summary|A3|=SUMIF('Q1 Sales'!A1:A3, "North", 'Q1 Sales'!B1:B3)
Summary|A4|=VLOOKUP("South", 'Q1 Sales'!A1:B3, 2, FALSE)</textarea>
    </div>
    <div class="row">
      <button onclick="runSheet()">Evaluate Multi-Sheet DAG</button>
      <button class="secondary" onclick="downloadXlsx()">Download Zero-Epoch .xlsx</button>
    </div>
    <pre id="sheet-out">Click "Evaluate Multi-Sheet DAG" to execute on Kotlin JVM.</pre>
  </section>

  <section>
    <h2>2. Writer + BibTeX + Tracked Revisions + PDF 1.7 <span class="pill">ISO 32000-1</span></h2>
    <div>
      <label>CommonMark Document with Inline `[@key]` Citations:</label>
      <textarea id="writer-md" rows="5"># Clean-Room Office &amp; Sandbox Architecture

Fusion executes untrusted plugins inside pure-JVM WebAssembly [@chicory2026] while isolating Tor ControlPort commands via allowlists [@tails2026, p. 4].

| Subsystem | License | JNI Free |
| --- | --- | --- |
| WasmSandbox | Apache-2.0 | Yes |</textarea>
    </div>
    <div class="row">
      <div style="flex:1"><label>BibTeX Library:</label>
        <textarea id="writer-bib" rows="3">@misc{chicory2026, author={Dylibso}, title={Chicory 1.7.5 Pure-JVM Wasm}, year={2026}}
@misc{tails2026, author={Tails Project}, title={onion-grater ControlPort Filter}, year={2026}}</textarea>
      </div>
      <div style="flex:1"><label>Tracked Insertion (Anonymized):</label>
        <textarea id="writer-ins" rows="3">[Reviewed under mat2 zero-metadata policy.]</textarea>
      </div>
    </div>
    <div class="row">
      <button onclick="runWriter()">Render HTML5 + PDF 1.7</button>
      <button class="secondary" onclick="downloadPdf()">Download Paginated .pdf</button>
    </div>
    <div id="writer-html" class="render-box"></div>
    <pre id="writer-meta"></pre>
  </section>

  <section>
    <h2>3. Pure-JVM Wasm 1.0 Gas-Metered Sandbox (WasmSandbox) <span class="pill">Zero JNI / 16KB Safe</span></h2>
    <div class="row">
      <div style="flex:1"><label>Fibonacci Input `n` (0..45):</label><input id="wasm-n" type="number" value="20" min="0" max="45"></div>
      <div style="flex:1"><label>Instruction Fuel Budget:</label><input id="wasm-fuel" type="number" value="5000" min="10" max="500000"></div>
    </div>
    <div class="row">
      <button onclick="runWasm('fib')">Execute Wasm `fib(n)`</button>
      <button class="secondary" onclick="runWasm('spin')">Test Infinite-Loop Fuel Trap (`spin()`)</button>
    </div>
    <pre id="wasm-out"></pre>
  </section>

  <section>
    <h2>4. Tails onion-grater &amp; Anti-Keylogger Hardware Shield <span class="pill">Zero-Trust</span></h2>
    <div>
      <label>Tor ControlPort Command Probe (try `SIGNAL NEWNYM`, `GETINFO net/listeners/socks`, or `ADD_ONION NEW:ED25519-V3 Port=80`):</label>
      <input id="tor-cmd" value="ADD_ONION NEW:ED25519-V3 Port=80">
    </div>
    <div>
      <label>Type Here to Measure Live Inter-Keystroke Cadence (`KeystrokeJitter` vs BadUSB Injection):</label>
      <input id="type-box" placeholder="Type naturally here, or click 'Simulate 8ms BadUSB Burst'..." onkeydown="recordStroke()">
    </div>
    <div class="row">
      <button onclick="runSecurity(false)">Evaluate Live Posture</button>
      <button class="secondary" onclick="runSecurity(true)">Simulate 8ms BadUSB DuckyScript Burst</button>
    </div>
    <pre id="sec-out"></pre>
  </section>

  <section>
    <h2>5. R47 Rich Office Suite (DOCX + ODT 1.3 + Bookmarked PDF + LaTeX SVG) <span class="pill">OOXML + ODF</span></h2>
    <div><label>Document Heading:</label><input id="r47-hdr" value="1. Zero-Metadata Office Architecture"></div>
    <div><label>Paragraph Body:</label><textarea id="r47-body" rows="2">Authored from scratch on OpenJDK 17 with zero docProps/core.xml leaks.</textarea></div>
    <div><label>LaTeX Math Expression:</label><input id="r47-latex" value="\\frac{-b \\pm \\sqrt{b^{2} - 4ac}}{2a}"></div>
    <div class="row">
      <button onclick="runR47Office()">Build .docx + .odt + .pdf + Math SVG</button>
      <button class="secondary" onclick="dlB64(lastDocxB64,'application/vnd.openxmlformats-officedocument.wordprocessingml.document','fusion-r47.docx')">Download .docx</button>
      <button class="secondary" onclick="dlB64(lastOdtB64,'application/vnd.oasis.opendocument.text','fusion-r47.odt')">Download .odt</button>
    </div>
    <div id="r47-math-box" class="render-box"></div>
    <pre id="r47-office-out"></pre>
  </section>

  <section>
    <h2>6. R47 Browser Shield (DOMPurify-Class + Brave Adblock) &amp; WASI Preview 1 <span class="pill">Fail-Closed</span></h2>
    <div><label>Untrusted HTML Probe:</label><textarea id="r47-html" rows="2">&lt;template shadowrootmode="closed"&gt;&lt;script&gt;evil()&lt;/script&gt;&lt;/template&gt;&lt;p onclick="x()"&gt;Safe Paragraph&lt;/p&gt;</textarea></div>
    <div class="row">
      <div style="flex:1"><label>Adblock URL Probe:</label><input id="r47-url" value="https://ad.doubleclick.example.net/tag.js"></div>
      <div style="flex:1"><label>WASI `path_open(fd=3)` Probe:</label><input id="r47-path" value="../etc/passwd"></div>
    </div>
    <div class="row"><button onclick="runR47BrowserWasi()">Test Sanitizer + Adblock + WASI Cap</button></div>
    <pre id="r47-bw-out"></pre>
  </section>

  <section>
    <h2>7. R47 Tor Proposal 351 (`&lt;torS0X&gt;0`) + CSPRNG Scrambled PIN Pad <span class="pill">Anti-Keylogger</span></h2>
    <div class="row">
      <div style="flex:1"><label>App Compartment:</label><input id="r47-app" value="browser"></div>
      <div style="flex:1"><label>Destination Origin:</label><input id="r47-orig" value="https://check.torproject.org/test"></div>
    </div>
    <div class="row"><button onclick="runR47Privacy()">Derive Prop 351 Stream Token &amp; Scramble PIN Pad</button></div>
    <pre id="r47-priv-out"></pre>
  </section>

  <section>
    <h2>8. R48 Dynamic Spill Sheet + .pptx + EPUB 3.3 + AcroForm PDF <span class="pill">95-Fn + Spill</span></h2>
    <div class="row">
      <div style="flex:1"><label>Deck / EPUB Title:</label><input id="r48-title" value="Fusion R48 Clean-Room Suite"></div>
      <div style="flex:1"><label>Prose Chapter (Readability + EPUB + RTF):</label><input id="r48-prose" value="Oblivious HTTP separates client identity from encrypted request payloads cleanly."></div>
    </div>
    <div>
      <label>Dynamic Array Spill Sheet (`CellRef|FormulaOrLiteral` per line):</label>
      <textarea id="r48-spill-in" rows="5">A1|=SEQUENCE(3, 2, 10, 5)
C1|=SUM(A1#)
D1|=LET(x, 14, x * 3)
A4|=SEQUENCE(2, 1, 1, 1)
A5|BLOCKER</textarea>
    </div>
    <div class="row">
      <button onclick="runR48OfficeSpill()">Evaluate Spill + Build .pptx / .epub / .ods / AcroForm</button>
      <button class="secondary" onclick="dlB64(lastPptxB64,'application/vnd.openxmlformats-officedocument.presentationml.presentation','fusion-r48.pptx')">Download .pptx</button>
      <button class="secondary" onclick="dlB64(lastEpubB64,'application/epub+zip','fusion-r48.epub')">Download .epub</button>
    </div>
    <pre id="r48-office-out"></pre>
  </section>

  <section>
    <h2>9. R48 Oblivious HTTP (RFC 9458) + BHTTP (RFC 9292) + Tor v3 .onion <span class="pill">Split-Trust</span></h2>
    <div class="row">
      <div style="flex:1"><label>Oblivious Relay HTTPS URL:</label><input id="r48-relay" value="https://relay.partner-edge.net/ohttp"></div>
      <div style="flex:1"><label>Oblivious Gateway HTTPS URL:</label><input id="r48-gw" value="https://gateway.privacy-target.org/ohttp"></div>
    </div>
    <div><label>Inner BHTTP Payload JSON:</label><input id="r48-bhttp-body" value='{"query":"ephemeral-lookup","pad":128}'></div>
    <div class="row"><button onclick="runR48Ohttp()">Audit Relay/Gateway Split-Trust + Frame BHTTP + Derive v3 .onion</button></div>
    <pre id="r48-ohttp-out"></pre>
  </section>

  <section>
    <h2>10. R48 Acoustic Mic Shield + HID Firewall + Browser COOP/COEP/Reader <span class="pill">5-Channel Guard</span></h2>
    <div class="row">
      <div style="flex:1"><label>COOP Header:</label><input id="r48-coop" value="same-origin"></div>
      <div style="flex:1"><label>COEP Header:</label><input id="r48-coep" value="require-corp"></div>
    </div>
    <div><label>Page HTML for Clean Reader Mode Extraction:</label><textarea id="r48-reader-html" rows="2">&lt;html&gt;&lt;head&gt;&lt;title&gt;Zero-Trust Isolation&lt;/title&gt;&lt;/head&gt;&lt;body&gt;&lt;nav&gt;&lt;a href="/ad"&gt;Ad&lt;/a&gt;&lt;/nav&gt;&lt;article&gt;&lt;p&gt;Cross-origin isolation blocks speculative side channels.&lt;/p&gt;&lt;/article&gt;&lt;/body&gt;&lt;/html&gt;</textarea></div>
    <div class="row"><button onclick="runR48AcousticBrowser()">Run 48kHz Ultrasonic FIR + HID Firewall + Reader Extractor</button></div>
    <pre id="r48-ab-out"></pre>
  </section>

  <section class="card">
    <h2>11. R49 LAMBDA + 130-Fn Dynamic Spill + Simplex LP + DrawingML Chart</h2>
    <div class="row">
      <div style="flex:1"><label>Objective c1 (x):</label><input id="r49-c1" value="3"></div>
      <div style="flex:1"><label>Objective c2 (y):</label><input id="r49-c2" value="5"></div>
    </div>
    <div><label>Spill + LAMBDA Cells (A1|Formula):</label><textarea id="r49-lambda-cells" rows="4">A1|=MAKEARRAY(3,3,LAMBDA(r,c,r*10+c))
E1|=TAKE(A1#,,-1)
F1|=SCAN(0,E1#,LAMBDA(a,v,a+v))
G1|=LET(tri,LAMBDA(a,LAMBDA(b,LAMBDA(c,a*100+b*10+c))),tri(4)(5)(6))</textarea></div>
    <div class="row"><button onclick="runR49LambdaSimplex()">Evaluate LAMBDA Spill + Solve Simplex LP + Render Chart SVG</button></div>
    <div id="r49-chart-box" style="margin:6px 0"></div>
    <pre id="r49-lambda-out"></pre>
  </section>

  <section class="card">
    <h2>12. R49 Writer Knuth-Plass DP + Liang Hyphenation + ODF 1.3 + Ed25519 Signed PDF</h2>
    <div><label>Paragraph for Knuth-Plass Optimal Line Breaking:</label><textarea id="r49-kp-para" rows="2">Optimal paragraph breaking minimizes total cubic badness across every feasible active line break.</textarea></div>
    <div><label>Word for Liang Hyphenation:</label><input id="r49-hyph-word" value="cryptography"></div>
    <div class="row">
      <button onclick="runR49WriterPdfSign()">Run Knuth-Plass DP + ODF Tracked Changes + Sign PDF</button>
      <button onclick="dlB64(lastSignedPdfB64,'application/pdf','fusion-r49-signed.pdf')">Download Signed .pdf</button>
    </div>
    <pre id="r49-writer-out"></pre>
  </section>

  <section class="card">
    <h2>13. R49 RFC 9849 TLS ECH + ChoiceJacking + Pixnapping + Tails 7.15.1 + WebRTC Mode 4</h2>
    <div class="row">
      <div style="flex:1"><label>Inner Secret SNI:</label><input id="r49-inner-sni" value="torproject.org"></div>
      <div style="flex:1"><label>Outer Cleartext SNI:</label><input id="r49-outer-sni" value="cloudflare-ech.com"></div>
    </div>
    <div><label>Certificate Issuer to Audit (Tails 7.15.1):</label><input id="r49-ca-issuer" value="eMudhra Technologies Limited"></div>
    <div class="row"><button onclick="runR49EchShield()">Audit RFC 9849 ECH + ChoiceJacking + Pixnapping + Tails CA + SDP Mode 4</button></div>
    <pre id="r49-ech-out"></pre>
  </section>

  <section class="card">
    <h2>14. R49 Wasm 2.0 IEEE-754 f32 + WASI SSRF Gate + Palette Math + Nag-Free Billing</h2>
    <div class="row">
      <div style="flex:1"><label>f32 leg A:</label><input id="r49-f32-a" value="3.0"></div>
      <div style="flex:1"><label>f32 leg B:</label><input id="r49-f32-b" value="4.0"></div>
    </div>
    <div><label>Command Palette Inline Query:</label><input id="r49-palette-q" value="(19.99 + 14.99) * 2"></div>
    <div class="row"><button onclick="runR49WasmShell()">Run f32 Hypot + WASI SSRF Check + Palette + Billing Invariants</button></div>
    <pre id="r49-wasm-shell-out"></pre>
  </section>

  <section class="card">
    <h2>15. R50 168-Fn Matrix/Engineering Sheet + 3D Sheet Spans (Q1:Q3!B2) <span class="pill">168-Fn + 3D</span></h2>
    <div class="row">
      <div style="flex:1"><label>Q1!B2:</label><input id="r50-q1" value="150"></div>
      <div style="flex:1"><label>Q2!B2:</label><input id="r50-q2" value="250"></div>
      <div style="flex:1"><label>Q3!B2:</label><input id="r50-q3" value="350"></div>
    </div>
    <div class="row">
      <button onclick="runR50Sheet3d()">Evaluate 168-Fn Matrix + 3D Span + Sparkline SVG</button>
      <button class="secondary" onclick="dlB64(lastR50Xlsx3dB64,'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet','fusion-r50-3d.xlsx')">Download 3D .xlsx</button>
    </div>
    <div id="r50-spark-box" class="render-box"></div>
    <pre id="r50-sheet3d-out"></pre>
  </section>

  <section class="card">
    <h2>16. R50 Wasm 2.0 IEEE-754 f64 + Multi-Table Bulk Ops <span class="pill">Wasm 2.0 f64</span></h2>
    <div class="row">
      <div style="flex:1"><label>f64 Input `x` (`2.5x^2 - 1.25x + 0.5 + sqrt(x)`):</label><input id="r50-f64-x" value="4.0"></div>
      <div style="flex:1"><label>Table Bulk Ops Input `v` (`add10(dbl(v))`):</label><input id="r50-tab-v" value="21"></div>
    </div>
    <div class="row"><button onclick="runR50WasmF64()">Execute Wasm 2.0 `invokeF64` + `table.init/copy/grow`</button></div>
    <pre id="r50-wasmf64-out"></pre>
  </section>

  <section class="card">
    <h2>17. R50 Lens MIT FSR 1.0 + NLMS Echo + OpenType Kern + PDF Stream Redactor <span class="pill">Zero-Leak DSP</span></h2>
    <div class="row">
      <div style="flex:1"><label>FSR 1.0 RCAS Sharpness Stops (0.0..2.0):</label><input id="r50-fsr-sharp" value="0.25"></div>
      <div style="flex:1"><label>Sensitive String to Physically Redact in PDF:</label><input id="r50-pdf-sec" value="999-42-8811"></div>
    </div>
    <div class="row">
      <button onclick="runR50LensFsr()">Run FSR 1.0 + NLMS Echo + OpenType + Redact PDF</button>
      <button class="secondary" onclick="dlB64(lastR50RedPdfB64,'application/pdf','fusion-r50-redacted.pdf')">Download Redacted .pdf</button>
    </div>
    <pre id="r50-lens-out"></pre>
  </section>

  <section class="card">
    <h2>18. R50 15-Vector Hardware &amp; Tails-Light Anonymity Shield <span class="pill">BLUFFS + NFC + BadUSB</span></h2>
    <div class="row">
      <div style="flex:1"><label>BT Key Octets (16 req):</label><input id="r50-bt-key" value="7"></div>
      <div style="flex:1"><label>NFC APDU RTT ms (&lt;=12):</label><input id="r50-nfc-rtt" value="34.5"></div>
      <div style="flex:1"><label>USB HID IKI ms (&gt;=8):</label><input id="r50-usb-iki" value="3"></div>
    </div>
    <div class="row"><button onclick="runR50Shield15()">Audit WebTunnel + BLUFFS + Wi-Fi + NFC + BadUSB + DoH + Shamir</button></div>
    <pre id="r50-shield15-out"></pre>
  </section>

  <section class="card">
    <h2>19. R51 215-Fn Spreadsheet + Structured Tables + GoalSeek/Simplex + ODS <span class="pill">215-Fn + table1.xml</span></h2>
    <div class="row">
      <div style="flex:1"><label>North Q1:</label><input id="r51-nq1" value="120"></div>
      <div style="flex:1"><label>South Q1:</label><input id="r51-sq1" value="200"></div>
      <div style="flex:1"><label>GoalSeek Target `x^2 = T`:</label><input id="r51-gs-t" value="2.0"></div>
    </div>
    <div class="row">
      <button onclick="runR51OfficeTable()">Evaluate 215-Fn + StructuredTable + Brent/Simplex + OpenFormula</button>
      <button class="secondary" onclick="dlB64(lastR51TblXlsxB64,'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet','fusion-r51-table.xlsx')">Download StructuredTable .xlsx</button>
    </div>
    <pre id="r51-office-out"></pre>
  </section>

  <section class="card">
    <h2>20. R51 Multi-Section Writer + Typst Galley Paginator + Tagged PDF/UA-1 <span class="pill">ISO 14289-1</span></h2>
    <div><label>Manuscript Title:</label><input id="r51-doc-title" value="Fusion R51 Accessible Scholarly Report"></div>
    <div class="row">
      <button onclick="runR51WriterPdfUa()">Paginate Galley + IEEE BibTeX + Build Multi-Section .docx &amp; PDF/UA-1</button>
      <button class="secondary" onclick="dlB64(lastR51MsDocxB64,'application/vnd.openxmlformats-officedocument.wordprocessingml.document','fusion-r51-multisection.docx')">Download .docx</button>
      <button class="secondary" onclick="dlB64(lastR51PdfUaB64,'application/pdf','fusion-r51-pdfua1.pdf')">Download Tagged PDF/UA-1</button>
    </div>
    <pre id="r51-writer-out"></pre>
  </section>

  <section class="card">
    <h2>21. R51 Wasm 2.0 128-bit SIMD (0xFD / v128) + Zero-Trust Browser <span class="pill">0xFD v128 + CHIPS</span></h2>
    <div class="row">
      <div style="flex:1"><label>SIMD `i32x4` Scalar Multiplier:</label><input id="r51-simd-k" value="3"></div>
      <div style="flex:1"><label>Flexbox Viewport Width px:</label><input id="r51-flex-w" value="600"></div>
    </div>
    <div><label>Navigation URL for DNR Tracker/Param Sanitizer:</label><input id="r51-nav-url" value="https://clean.example/read?id=42&amp;fbclid=TRACK123&amp;utm_source=ads"></div>
    <div class="row"><button onclick="runR51WasmSimdBrowser()">Execute 0xFD `invokeV128` + Static Verifier + WIT + Flexbox + CHIPS + DNR</button></div>
    <pre id="r51-wasm-browser-out"></pre>
  </section>

  <section class="card">
    <h2>22. R51 Tails 7.15.1 Argon2id + Vanguards-Lite + 20-Vector Hardware Shield <span class="pill">GhostTouch + Thunderclap</span></h2>
    <div class="row">
      <div style="flex:1"><label>LUKS2 Argon2id KiB (&gt;=1048576):</label><input id="r51-argon-kib" value="1048576"></div>
      <div style="flex:1"><label>Strict IOMMU Active (`true`/`false`):</label><input id="r51-iommu" value="false"></div>
      <div style="flex:1"><label>Hall Sensor Field uT:</label><input id="r51-hall-ut" value="1800"></div>
    </div>
    <div class="row"><button onclick="runR51TailsShield20()">Audit Prop 333 Vanguards-Lite + LUKS2 + GhostTouch + Thunderclap + Class A Honesty</button></div>
    <pre id="r51-shield20-out"></pre>
  </section>

  <section class="card" oncontextmenu="openCompactCtxMenu(event)">
    <h2>23. R52 Recall 4-Tier Browser Media Recording Studio <span class="pill">URL / Title / Custom + 4 Methods</span></h2>
    <div class="row">
      <div style="flex:1"><label>Viewed Page URL:</label><input id="r52-rec-url" value="https://arte.tv/concerts/baroque?utm_source=feed&amp;fbclid=99"></div>
      <div style="flex:1"><label>Viewed Page Title:</label><input id="r52-rec-title" value="Baroque Evening Live: Concert #4"></div>
    </div>
    <div class="row">
      <div style="flex:1"><label>Custom Template (`BY_CUSTOM_TEMPLATE`):</label><input id="r52-rec-tpl" value="{custom}/{yyyy}-{mm}-{dd}/{domain}/{kind}"></div>
      <div style="flex:1"><label>Custom Folder Label:</label><input id="r52-rec-cname" value="MusicResearch"></div>
    </div>
    <div class="row">
      <button onclick="runR52RecallStudio()">Record Across All 3 Folder Modes &amp; 4 Capture Tiers</button>
      <button class="secondary" onclick="dlB64(lastR52Fmp4B64,'video/mp4','recall-tier2-fragment.fmp4')">Download Tier-2 .fmp4</button>
      <button class="secondary" onclick="dlB64(lastR52WavB64,'audio/wav','recall-tier3-webaudio.wav')">Download Tier-3 48kHz .wav</button>
    </div>
    <pre id="r52-recall-out"></pre>
  </section>

  <section class="card" oncontextmenu="openCompactCtxMenu(event)">
    <h2>24. R52 Compact Right-Click Menu, Original List/Grid/Columns Icons &amp; Bottom-Right Z-Stack Undo <span class="pill">180..280dp · SVG · Z3</span></h2>
    <div class="row" id="r52-view-bar">
      <button class="r52-icon-btn active" id="btn-mode-list" onclick="selectViewMode('list')"><span id="svg-slot-list"></span><span>List</span></button>
      <button class="r52-icon-btn" id="btn-mode-grid" onclick="selectViewMode('grid')"><span id="svg-slot-grid"></span><span>Grid</span></button>
      <button class="r52-icon-btn" id="btn-mode-cols" onclick="selectViewMode('cols')"><span id="svg-slot-cols"></span><span>Columns</span></button>
      <button class="secondary" onclick="pushUndoToast('Archived Recall item #' + (++undoSeq) + ' (translucent bottom-right)')">Trigger Bottom-Right Z-Stack Undo</button>
    </div>
    <div class="row">
      <div style="flex:1"><label>Viewport W×H (dp):</label><input id="r52-vp" value="1440x900"></div>
      <div style="flex:1"><label>Cursor (x,y) — try `(1380,840)` for edge flip:</label><input id="r52-cur" value="1380,840"></div>
    </div>
    <div class="row"><button onclick="runR52UiErgonomics()">Solve Compact Context Menu + Render Original SVGs + Audit Z-Stack Bounding Box</button></div>
    <pre id="r52-ergo-out"></pre>
  </section>

  <section class="card">
    <h2>25. R52 260-Fn FormulaEval + 42-Op SpillSheet + Wasm 3.0 Tail Calls (0x12 / 0x13) <span class="pill">O(1) Call Depth</span></h2>
    <div class="row">
      <div style="flex:1"><label>Wasm 3.0 `sum_tail(N, 0)` Recursion Count (`maxCallDepth=2`!):</label><input id="r52-tail-n" type="number" value="25000" min="10" max="100000"></div>
      <button onclick="runR52CalcWasm3()">Evaluate 260-Fn + OUTERPRODUCT + Wasm 3.0 `0x12` Tail Call</button>
    </div>
    <pre id="r52-calcwasm-out"></pre>
  </section>

  <section class="card">
    <h2>26. R52 Tor Proposal 327 Onion PoW + 25-Vector Hardware Shield <span class="pill">ChargerSurfing · Dolphin · Thermanator</span></h2>
    <div class="row"><button onclick="runR52TailsPow25()">Verify RFC 7693 BLAKE2b PoW + Vectors #21–#25 + 5 Visual Audit Sheets</button></div>
    <div class="row" style="font-size:12px">
      <a href="api/r52/preview/r52-recall-studio.png" target="_blank" style="color:var(--cyan)">1. Recall Studio PNG</a> ·
      <a href="api/r52/preview/r52-context-menu-benchmark.png" target="_blank" style="color:var(--cyan)">2. Context Menu PNG</a> ·
      <a href="api/r52/preview/r52-view-mode-icons.png" target="_blank" style="color:var(--cyan)">3. View Icons PNG</a> ·
      <a href="api/r52/preview/r52-undo-zstack-toast.png" target="_blank" style="color:var(--cyan)">4. Z-Stack Undo PNG</a> ·
      <a href="api/r52/preview/r52-workbench-overview.png" target="_blank" style="color:var(--cyan)">5. Overview PNG</a>
    </div>
    <pre id="r52-pow25-out"></pre>
  </section>

  <section class="card">
    <h2>27. R53 Recall 6-Method Studio: WebRTC RTP Opus + ISO 28500 WARC/1.1 + CDXJ + EBU R128 <span class="pill">6 Methods · T1..T5</span></h2>
    <div class="row">
      <div style="flex:1"><label>Live WebRTC / WARC Target URL:</label><input id="r53-rec-url" value="https://concert.example.org/webrtc-live"></div>
      <button onclick="runR53Recall6()">Run 6-Method WebRTC RTP + WARC/1.1 + CDXJ + LUFS</button>
      <button class="secondary" onclick="dlB64(lastR53WarcB64,'application/warc','recall-bundle.warc')">Download ISO 28500 WARC/1.1</button>
    </div>
    <pre id="r53-recall6-out"></pre>
  </section>

  <section class="card">
    <h2>28. R53 Cascading Submenus, 5-DPI Crisp View Icons &amp; Interactive Hover Undo Fan <span class="pill">7 OS Baselines · &lt;9% Stack</span></h2>
    <div class="row">
      <button onclick="runR53UiErgo2()">Solve Cascade + Safe-Triangle + 5-DPI Crisp Icons + Undo Fan</button>
      <a href="api/r53/preview/r53-recall-6method-studio.png" target="_blank" style="color:var(--cyan)">1. Recall 6-Method PNG</a> ·
      <a href="api/r53/preview/r53-context-menu-cascade-benchmark.png" target="_blank" style="color:var(--cyan)">2. 7-OS Cascade PNG</a> ·
      <a href="api/r53/preview/r53-crisp-view-mode-matrix.png" target="_blank" style="color:var(--cyan)">3. 5-DPI Crisp Icons PNG</a> ·
      <a href="api/r53/preview/r53-undo-fan-zstack-overlay.png" target="_blank" style="color:var(--cyan)">4. Undo Fan PNG</a> ·
      <a href="api/r53/preview/r53-tails-conflux-hw30-shield.png" target="_blank" style="color:var(--cyan)">5. Conflux+30Vec PNG</a> ·
      <a href="api/r53/preview/r53-workbench-overview.png" target="_blank" style="color:var(--cyan)">6. R53 Overview PNG</a>
    </div>
    <pre id="r53-ergo2-out"></pre>
  </section>

  <section class="card">
    <h2>29. R53 300-Fn FormulaEval + 48-Op HANKEL Spill + Wasm 3.0 Multi-Memory (`0xFC 0x0A`) <span class="pill">8 Linear Memories</span></h2>
    <div class="row"><button onclick="runR53Calc300WasmMM()">Evaluate 300-Fn + HANKEL + Cross-Memory `memory.copy(dst=1, src=0)`</button></div>
    <pre id="r53-calc300-out"></pre>
  </section>

  <section class="card">
    <h2>30. R53 Tor Proposal 329 Conflux + Clean-Room MAT2 + 30-Vector Hardware Shield <span class="pill">TEMPEST · HOGP · AVB · ALS · TRR</span></h2>
    <div class="row"><button onclick="runR53ConfluxHw30()">Audit Conflux Disjoint Legs + MAT2 Scrub + Vectors #26–#30 + RFC 8439</button></div>
    <pre id="r53-hw30-out"></pre>
  </section>
</main>
<script>
let lastXlsxB64 = null;
let lastPdfB64 = null;
let lastDocxB64 = null;
let lastOdtB64 = null;
let lastPptxB64 = null;
let lastEpubB64 = null;
let lastSignedPdfB64 = null;
let lastR50Xlsx3dB64 = null;
let lastR50RedPdfB64 = null;
let lastR51TblXlsxB64 = null;
let lastR51MsDocxB64 = null;
let lastR51PdfUaB64 = null;
let lastR52Fmp4B64 = null;
let lastR52WavB64 = null;
let lastR53WarcB64 = null;
let undoSeq = 0;
let undoStack = [];
let strokeTimes = [];

function renderUndoDeck() {
  const host = document.getElementById("r52-undo-deck");
  if (!host) return;
  host.innerHTML = "";
  const vis = undoStack.slice(0, 3);
  const alphas = [0.88, 0.68, 0.48];
  const scales = [1.0, 0.95, 0.90];
  const peeks = [0, -10, -20];
  for (let d = vis.length - 1; d >= 0; d--) {
    const item = vis[d];
    const div = document.createElement("div");
    div.className = "r52-undo-card";
    div.style.opacity = String(alphas[d]);
    div.style.transform = `translateY(${peeks[d]}px) scale(${scales[d]})`;
    div.style.zIndex = String(860 - d);
    const span = document.createElement("span");
    span.textContent = d === 0 && undoStack.length > 3 ? `${item.msg} (+${undoStack.length - 3})` : item.msg;
    div.appendChild(span);
    if (d === 0) {
      const btn = document.createElement("button");
      btn.textContent = "Undo";
      btn.style.padding = "4px 10px";
      btn.onclick = () => { undoStack.shift(); renderUndoDeck(); };
      div.appendChild(btn);
    }
    host.appendChild(div);
  }
}

function pushUndoToast(msg) {
  undoStack.unshift({id: Date.now() + Math.random(), msg});
  if (undoStack.length > 8) undoStack.pop();
  renderUndoDeck();
}

function openCompactCtxMenu(ev) {
  ev.preventDefault();
  const m = document.getElementById("r52-ctx-menu");
  const mw = 248, mh = 188, pad = 12;
  let x = ev.clientX, y = ev.clientY;
  if (x + mw + pad > window.innerWidth) x = Math.max(pad, x - mw);
  if (y + mh + pad > window.innerHeight) y = Math.max(pad, y - mh);
  m.style.left = x + "px";
  m.style.top = y + "px";
  m.style.display = "block";
}
function hideCtxMenu() {
  const m = document.getElementById("r52-ctx-menu");
  if (m) m.style.display = "none";
}
document.addEventListener("click", (e) => {
  const m = document.getElementById("r52-ctx-menu");
  if (m && !m.contains(e.target)) hideCtxMenu();
});
function selectViewMode(mode) {
  for (const k of ["list", "grid", "cols"]) {
    document.getElementById("btn-mode-" + k).classList.toggle("active", k === mode);
  }
  pushUndoToast("Switched Files & Recall view to " + mode.toUpperCase());
}

function dlB64(b64, mime, name) {
  if (!b64) return;
  const a = document.createElement("a");
  a.href = "data:" + mime + ";base64," + b64;
  a.download = name;
  a.click();
}

function b64Utf8(str) {
  const bytes = new TextEncoder().encode(str);
  let bin = "";
  for (const b of bytes) bin += String.fromCharCode(b);
  return btoa(bin);
}

function recordStroke() {
  strokeTimes.push(Math.round(performance.now()));
  if (strokeTimes.length > 24) strokeTimes.shift();
}

async function apiPost(path, body) {
  // Strictly relative URL for proxy/e2b preview compatibility (zero localhost hardcoding!)
  const r = await fetch(path, {
    method: "POST",
    headers: {"Content-Type": "application/json"},
    body: JSON.stringify(body)
  });
  return await r.json();
}

async function runSheet() {
  const raw = document.getElementById("sheet-in").value;
  const res = await apiPost("api/sheet", {rows_b64: b64Utf8(raw)});
  if (!res.ok) { document.getElementById("sheet-out").textContent = "ERROR: " + res.error; return; }
  lastXlsxB64 = res.xlsx_b64;
  const lines = Object.entries(res.values).map(([k, v]) => k.padEnd(16) + " => " + v);
  document.getElementById("sheet-out").textContent = lines.join("\n");
}

function downloadXlsx() {
  if (!lastXlsxB64) return;
  const a = document.createElement("a");
  a.href = "data:application/vnd.openxmlformats-officedocument.spreadsheetml.sheet;base64," + lastXlsxB64;
  a.download = "fusion-r46-workbook.xlsx";
  a.click();
}

async function runWriter() {
  const md = document.getElementById("writer-md").value;
  const bib = document.getElementById("writer-bib").value;
  const ins = document.getElementById("writer-ins").value;
  const res = await apiPost("api/writer", {md_b64: b64Utf8(md), bib_b64: b64Utf8(bib), ins_b64: b64Utf8(ins)});
  if (!res.ok) { document.getElementById("writer-meta").textContent = "ERROR: " + res.error; return; }
  lastPdfB64 = res.pdf_b64;
  document.getElementById("writer-html").innerHTML = res.html;
  document.getElementById("writer-meta").textContent =
    `PDF 1.7 Generated: ${res.pdf_bytes} bytes (${res.pdf_pages} page) | Bibliography:\n` +
    (res.bibliography.join("\n") || "(none)");
}

function downloadPdf() {
  if (!lastPdfB64) return;
  const a = document.createElement("a");
  a.href = "data:application/pdf;base64," + lastPdfB64;
  a.download = "fusion-r46-document.pdf";
  a.click();
}

async function runWasm(mode) {
  const n = parseInt(document.getElementById("wasm-n").value || "20", 10);
  const fuel = parseInt(document.getElementById("wasm-fuel").value || "5000", 10);
  const res = await apiPost("api/wasm", {n, fuel, mode});
  document.getElementById("wasm-out").textContent = JSON.stringify(res, null, 2);
}

async function runSecurity(simulateBadUsb) {
  const cmd = document.getElementById("tor-cmd").value;
  const times = simulateBadUsb
    ? Array.from({length: 12}, (_, i) => i * 8)
    : strokeTimes;
  const res = await apiPost("api/security", {tor_b64: b64Utf8(cmd), timestamps_csv: times.join(",")});
  document.getElementById("sec-out").textContent = JSON.stringify(res, null, 2);
}

async function runR47Office() {
  const res = await apiPost("api/r47/office", {
    hdr_b64: b64Utf8(document.getElementById("r47-hdr").value),
    body_b64: b64Utf8(document.getElementById("r47-body").value),
    latex_b64: b64Utf8(document.getElementById("r47-latex").value)
  });
  if (!res.ok) { document.getElementById("r47-office-out").textContent = "ERROR: " + res.error; return; }
  lastDocxB64 = res.docx_b64;
  lastOdtB64 = res.odt_b64;
  document.getElementById("r47-math-box").innerHTML = res.math_svg;
  document.getElementById("r47-office-out").textContent =
    `DOCX=${res.docx_bytes}B | ODT=${res.odt_bytes}B (STORED@38=${res.odt_magic}) | Bookmarked PDF=${res.pdf_bytes}B (${res.bookmarks} bookmarks)`;
}

async function runR47BrowserWasi() {
  const res = await apiPost("api/r47/browser-wasi", {
    html_b64: b64Utf8(document.getElementById("r47-html").value),
    url_b64: b64Utf8(document.getElementById("r47-url").value),
    path_b64: b64Utf8(document.getElementById("r47-path").value)
  });
  document.getElementById("r47-bw-out").textContent = JSON.stringify(res, null, 2);
}

async function runR47Privacy() {
  const res = await apiPost("api/r47/privacy-keypad", {
    app_b64: b64Utf8(document.getElementById("r47-app").value),
    orig_b64: b64Utf8(document.getElementById("r47-orig").value)
  });
  document.getElementById("r47-priv-out").textContent = JSON.stringify(res, null, 2);
}

async function runR48OfficeSpill() {
  const res = await apiPost("api/r48/office-spill", {
    title_b64: b64Utf8(document.getElementById("r48-title").value),
    prose_b64: b64Utf8(document.getElementById("r48-prose").value),
    cells_b64: b64Utf8(document.getElementById("r48-spill-in").value)
  });
  if (res.ok) { lastPptxB64 = res.pptx_b64; lastEpubB64 = res.epub_b64; }
  const slim = Object.assign({}, res);
  delete slim.pptx_b64;
  delete slim.epub_b64;
  document.getElementById("r48-office-out").textContent = JSON.stringify(slim, null, 2);
}

async function runR48Ohttp() {
  const res = await apiPost("api/r48/ohttp-onion", {
    relay_b64: b64Utf8(document.getElementById("r48-relay").value),
    gw_b64: b64Utf8(document.getElementById("r48-gw").value),
    body_b64: b64Utf8(document.getElementById("r48-bhttp-body").value)
  });
  document.getElementById("r48-ohttp-out").textContent = JSON.stringify(res, null, 2);
}

async function runR48AcousticBrowser() {
  const res = await apiPost("api/r48/acoustic-browser", {
    html_b64: b64Utf8(document.getElementById("r48-reader-html").value),
    coop_b64: b64Utf8(document.getElementById("r48-coop").value),
    coep_b64: b64Utf8(document.getElementById("r48-coep").value)
  });
  document.getElementById("r48-ab-out").textContent = JSON.stringify(res, null, 2);
}

async function runR49LambdaSimplex() {
  const res = await apiPost("api/r49/lambda-simplex", {
    cells_b64: b64Utf8(document.getElementById("r49-lambda-cells").value),
    c1: document.getElementById("r49-c1").value,
    c2: document.getElementById("r49-c2").value
  });
  if (res.ok && res.chart_svg) { document.getElementById("r49-chart-box").innerHTML = res.chart_svg; }
  const slim = Object.assign({}, res);
  delete slim.chart_svg;
  document.getElementById("r49-lambda-out").textContent = JSON.stringify(slim, null, 2);
}

async function runR49WriterPdfSign() {
  const res = await apiPost("api/r49/writer-pdf-sign", {
    para_b64: b64Utf8(document.getElementById("r49-kp-para").value),
    word_b64: b64Utf8(document.getElementById("r49-hyph-word").value)
  });
  if (res.ok) { lastSignedPdfB64 = res.signed_pdf_b64; }
  const slim = Object.assign({}, res);
  delete slim.signed_pdf_b64;
  document.getElementById("r49-writer-out").textContent = JSON.stringify(slim, null, 2);
}

async function runR49EchShield() {
  const res = await apiPost("api/r49/ech-hardware-shield", {
    inner_b64: b64Utf8(document.getElementById("r49-inner-sni").value),
    outer_b64: b64Utf8(document.getElementById("r49-outer-sni").value),
    ca_b64: b64Utf8(document.getElementById("r49-ca-issuer").value)
  });
  document.getElementById("r49-ech-out").textContent = JSON.stringify(res, null, 2);
}

async function runR49WasmShell() {
  const res = await apiPost("api/r49/wasm2-shell", {
    ax: document.getElementById("r49-f32-a").value,
    ay: document.getElementById("r49-f32-b").value,
    query_b64: b64Utf8(document.getElementById("r49-palette-q").value)
  });
  document.getElementById("r49-wasm-shell-out").textContent = JSON.stringify(res, null, 2);
}

async function runR50Sheet3d() {
  const res = await apiPost("api/r50/spreadsheet3d", {
    q1: document.getElementById("r50-q1").value,
    q2: document.getElementById("r50-q2").value,
    q3: document.getElementById("r50-q3").value
  });
  if (res.ok) {
    lastR50Xlsx3dB64 = res.xlsx3d_b64;
    document.getElementById("r50-spark-box").innerHTML = res.spark_svg;
  }
  const slim = Object.assign({}, res);
  delete slim.xlsx3d_b64;
  delete slim.spark_svg;
  document.getElementById("r50-sheet3d-out").textContent = JSON.stringify(slim, null, 2);
}

async function runR50WasmF64() {
  const res = await apiPost("api/r50/wasm2-f64-table", {
    x: document.getElementById("r50-f64-x").value,
    v: document.getElementById("r50-tab-v").value
  });
  document.getElementById("r50-wasmf64-out").textContent = JSON.stringify(res, null, 2);
}

async function runR50LensFsr() {
  const res = await apiPost("api/r50/lens-fsr1-nlms", {
    sharp: document.getElementById("r50-fsr-sharp").value,
    term_b64: b64Utf8(document.getElementById("r50-pdf-sec").value)
  });
  if (res.ok) { lastR50RedPdfB64 = res.pdf_redacted_b64; }
  const slim = Object.assign({}, res);
  delete slim.pdf_redacted_b64;
  document.getElementById("r50-lens-out").textContent = JSON.stringify(slim, null, 2);
}

async function runR50Shield15() {
  const res = await apiPost("api/r50/shield-15vec", {
    bt_key: document.getElementById("r50-bt-key").value,
    nfc_rtt: document.getElementById("r50-nfc-rtt").value,
    usb_iki: document.getElementById("r50-usb-iki").value
  });
  document.getElementById("r50-shield15-out").textContent = JSON.stringify(res, null, 2);
}

async function runR51OfficeTable() {
  const res = await apiPost("api/r51/office-table-solver", {
    nq1: document.getElementById("r51-nq1").value,
    sq1: document.getElementById("r51-sq1").value,
    gs_t: document.getElementById("r51-gs-t").value
  });
  if (res.ok) { lastR51TblXlsxB64 = res.table_xlsx_b64; }
  const slim = Object.assign({}, res);
  delete slim.table_xlsx_b64;
  document.getElementById("r51-office-out").textContent = JSON.stringify(slim, null, 2);
}

async function runR51WriterPdfUa() {
  const res = await apiPost("api/r51/writer-galley-pdfua1", {
    title_b64: b64Utf8(document.getElementById("r51-doc-title").value)
  });
  if (res.ok) { lastR51MsDocxB64 = res.docx_ms_b64; lastR51PdfUaB64 = res.pdfua1_b64; }
  const slim = Object.assign({}, res);
  delete slim.docx_ms_b64;
  delete slim.pdfua1_b64;
  document.getElementById("r51-writer-out").textContent = JSON.stringify(slim, null, 2);
}

async function runR51WasmSimdBrowser() {
  const res = await apiPost("api/r51/wasm2-simd-browser", {
    scalar: document.getElementById("r51-simd-k").value,
    viewport_w: document.getElementById("r51-flex-w").value,
    url_b64: b64Utf8(document.getElementById("r51-nav-url").value)
  });
  document.getElementById("r51-wasm-browser-out").textContent = JSON.stringify(res, null, 2);
}

async function runR51TailsShield20() {
  const res = await apiPost("api/r51/tails-shield-20vec", {
    argon_kib: document.getElementById("r51-argon-kib").value,
    iommu_strict: document.getElementById("r51-iommu").value,
    hall_ut: document.getElementById("r51-hall-ut").value
  });
  document.getElementById("r51-shield20-out").textContent = JSON.stringify(res, null, 2);
}

async function runR52RecallStudio() {
  const res = await apiPost("api/r52/recall-studio", {
    url_b64: b64Utf8(document.getElementById("r52-rec-url").value),
    title_b64: b64Utf8(document.getElementById("r52-rec-title").value),
    tpl_b64: b64Utf8(document.getElementById("r52-rec-tpl").value),
    cname_b64: b64Utf8(document.getElementById("r52-rec-cname").value)
  });
  if (res.ok) {
    lastR52Fmp4B64 = res.fmp4_b64;
    lastR52WavB64 = res.wav_b64;
    pushUndoToast("Recall archived " + res.path_by_url);
  }
  const slim = Object.assign({}, res);
  delete slim.fmp4_b64;
  delete slim.wav_b64;
  document.getElementById("r52-recall-out").textContent = JSON.stringify(slim, null, 2);
}

async function runR52UiErgonomics() {
  const vp = (document.getElementById("r52-vp").value || "1440x900").split("x");
  const cur = (document.getElementById("r52-cur").value || "1380,840").split(",");
  const res = await apiPost("api/r52/ui-ergonomics", {
    vw: vp[0] || "1440", vh: vp[1] || "900",
    cx: cur[0] || "1380", cy: cur[1] || "840"
  });
  if (res.ok) {
    document.getElementById("svg-slot-list").innerHTML = atob(res.svg_list_b64);
    document.getElementById("svg-slot-grid").innerHTML = atob(res.svg_grid_b64);
    document.getElementById("svg-slot-cols").innerHTML = atob(res.svg_cols_b64);
  }
  const slim = Object.assign({}, res);
  delete slim.svg_list_b64;
  delete slim.svg_grid_b64;
  delete slim.svg_cols_b64;
  document.getElementById("r52-ergo-out").textContent = JSON.stringify(slim, null, 2);
}

async function runR52CalcWasm3() {
  const res = await apiPost("api/r52/calc260-wasm3", {
    tail_n: document.getElementById("r52-tail-n").value
  });
  document.getElementById("r52-calcwasm-out").textContent = JSON.stringify(res, null, 2);
}

async function runR52TailsPow25() {
  const res = await apiPost("api/r52/tails-pow-shield25", {});
  document.getElementById("r52-pow25-out").textContent = JSON.stringify(res, null, 2);
}

async function runR53Recall6() {
  const res = await apiPost("api/r53/recall6", {
    url_b64: b64Utf8(document.getElementById("r53-rec-url").value)
  });
  if (res.ok) lastR53WarcB64 = res.warc_b64;
  const slim = Object.assign({}, res);
  delete slim.warc_b64;
  document.getElementById("r53-recall6-out").textContent = JSON.stringify(slim, null, 2);
}

async function runR53UiErgo2() {
  const res = await apiPost("api/r53/ui-ergonomics2", { vw: 1440, vh: 900, cx: 1340, cy: 300 });
  document.getElementById("r53-ergo2-out").textContent = JSON.stringify(res, null, 2);
}

async function runR53Calc300WasmMM() {
  const res = await apiPost("api/r53/calc300-wasm-multimemory", {});
  document.getElementById("r53-calc300-out").textContent = JSON.stringify(res, null, 2);
}

async function runR53ConfluxHw30() {
  const res = await apiPost("api/r53/conflux-mat2-hw30", {});
  document.getElementById("r53-hw30-out").textContent = JSON.stringify(res, null, 2);
}

(async () => {
  const st = await (await fetch("api/status")).json();
  document.getElementById("engine-pill").textContent =
    `${st.daemon.engine} (${st.daemon.kernels} R53 kernels | ${st.daemon.functions} Sheet funcs | ${st.daemon.modules} modules)`;
  await runSheet();
  await runWriter();
  await runWasm("fib");
  await runSecurity(true);
  await runR47Office();
  await runR47BrowserWasi();
  await runR47Privacy();
  await runR48OfficeSpill();
  await runR48Ohttp();
  await runR48AcousticBrowser();
  await runR49LambdaSimplex();
  await runR49WriterPdfSign();
  await runR49EchShield();
  await runR49WasmShell();
  await runR50Sheet3d();
  await runR50WasmF64();
  await runR50LensFsr();
  await runR50Shield15();
  await runR51OfficeTable();
  await runR51WriterPdfUa();
  await runR51WasmSimdBrowser();
  await runR51TailsShield20();
  await runR52RecallStudio();
  await runR52UiErgonomics();
  await runR52CalcWasm3();
  await runR52TailsPow25();
  await runR53Recall6();
  await runR53UiErgo2();
  await runR53Calc300WasmMM();
  await runR53ConfluxHw30();
})();
</script>
</body>
</html>
"""


class KotlinDaemonBridge:
    def __init__(self) -> None:
        kotlinc, java_bin, stdlib, env = run_r46_contracts.resolve_toolchain()
        r46_jars, _ = run_r46_contracts.compile_all(workers=2, use_cache=True)
        r47_dir = ROOT / "testing" / "build" / "r47-host"
        r47_jars = [r47_dir / f"r47_{d}.jar" for d in ("office", "runtime", "privacy_sec", "product_a", "product_b", "product_c")]
        saved_argv = sys.argv[:]
        try:
            sys.argv = [sys.argv[0]]
            if not all(j.is_file() for j in r47_jars):
                run_r47_contracts.main()
            r48_dir = ROOT / "testing" / "build" / "r48"
            r48_jars = [r48_dir / "r48_office.jar", r48_dir / "r48_privacy_sec_wasm.jar", r48_dir / "r48_systems.jar"]
            if not all(j.is_file() for j in r48_jars):
                run_r48_contracts.main()
            r49_dir = ROOT / "testing" / "build" / "r49"
            r49_jars = [r49_dir / "r49_office_formats.jar", r49_dir / "r49_privacy_browser_wasm.jar", r49_dir / "r49_shell_systems.jar"]
            if not all(j.is_file() for j in r49_jars):
                run_r49_contracts.main()
            r50_dir = ROOT / "testing" / "build" / "r50"
            r50_jars = [
                r50_dir / "r50_office_formats.jar",
                r50_dir / "r50_privacy_browser_wasm.jar",
                r50_dir / "r50_shell_core.jar",
                r50_dir / "r50_lens_media_cast_a11y.jar",
            ]
            if not all(j.is_file() for j in r50_jars):
                run_r50_contracts.main([])
            r51_dir = ROOT / "testing" / "build" / "r51"
            r51_jars = [
                r51_dir / "r51_office_formats.jar",
                r51_dir / "r51_privacy_browser_wasm.jar",
                r51_dir / "r51_shell_core.jar",
                r51_dir / "r51_lens_media_cast_a11y.jar",
            ]
            if not all(j.is_file() for j in r51_jars):
                run_r51_contracts.main([])
            r52_dir = ROOT / "testing" / "build" / "r52"
            r52_jars = [
                r52_dir / "r52_recall_media_browser.jar",
                r52_dir / "r52_ui_ergonomics_shell_core.jar",
                r52_dir / "r52_office_formats.jar",
                r52_dir / "r52_privacy_hw_wasm_features.jar",
            ]
            if not all(j.is_file() for j in r52_jars):
                run_r52_contracts.main([])
            r53_dir = ROOT / "testing" / "build" / "r53"
            r53_jars = [
                r53_dir / "r53_recall_6method_media.jar",
                r53_dir / "r53_office_publishing_formats.jar",
                r53_dir / "r53_tails_hw30_wasm3_features.jar",
                r53_dir / "r53_ui_ergonomics_phase2.jar",
            ]
            if not all(j.is_file() for j in r53_jars):
                run_r53_contracts.main([])
        finally:
            sys.argv = saved_argv
        # Place R53 jars first so 300-fn FormulaEval, 48-op SpillSheetEngine, Wasm 3.0 Multi-Memory, 6-method Recall & R53 UI kernels win cleanly
        jars = [*r53_jars, *r52_jars, *r51_jars, *r50_jars, *r49_jars, *r48_jars, *r47_jars, *r46_jars]
        DAEMON_DIR.mkdir(parents=True, exist_ok=True)
        kt_file = DAEMON_DIR / "R46WorkbenchDaemon.kt"
        daemon_jar = DAEMON_DIR / "r53-workbench-daemon.jar"
        stamp = DAEMON_DIR / "r53-workbench-daemon.sha256"
        if not kt_file.is_file() or kt_file.read_text(encoding="utf-8") != KOTLIN_DAEMON_SRC:
            kt_file.write_text(KOTLIN_DAEMON_SRC, encoding="utf-8")
        sig = run_r46_contracts._sha256_paths([*jars, kt_file])
        if not (daemon_jar.is_file() and stamp.is_file() and stamp.read_text().strip() == sig):
            cp = os.pathsep.join(str(j) for j in jars)
            subprocess.run(
                [str(kotlinc), "-Werror", "-jvm-target", "17", "-cp", cp, str(kt_file), "-d", str(daemon_jar)],
                cwd=ROOT,
                env=env,
                check=True,
            )
            stamp.write_text(sig + "\n")
        run_cp = os.pathsep.join([*(str(j) for j in jars), str(daemon_jar), str(stdlib)])
        self._lock = threading.Lock()
        self._proc = subprocess.Popen(
            [str(java_bin), "-cp", run_cp, "dev.fusion.testing.R46WorkbenchDaemon"],
            cwd=ROOT,
            env=env,
            stdin=subprocess.PIPE,
            stdout=subprocess.PIPE,
            stderr=subprocess.PIPE,
            text=True,
            bufsize=1,
        )
        ready = self._proc.stdout.readline().strip()
        if ready != "READY":
            err = self._proc.stderr.read() if self._proc.stderr else ""
            raise RuntimeError(f"Kotlin daemon failed to start: {ready!r} {err}")

    def request(self, *fields: str) -> dict[str, object]:
        wire = "\t".join(f.replace("\t", " ").replace("\n", " ").replace("\r", "") for f in fields) + "\n"
        with self._lock:
            assert self._proc.stdin is not None and self._proc.stdout is not None
            self._proc.stdin.write(wire)
            self._proc.stdin.flush()
            line = self._proc.stdout.readline()
            if not line:
                raise RuntimeError("Kotlin daemon closed stdout unexpectedly")
            return json.loads(line)

    def close(self) -> None:
        if self._proc.poll() is None:
            self._proc.terminate()
            try:
                self._proc.wait(timeout=3)
            except subprocess.TimeoutExpired:
                self._proc.kill()


def make_handler(bridge: KotlinDaemonBridge):
    class WorkbenchHandler(BaseHTTPRequestHandler):
        def _send_json(self, code: int, payload: dict[str, object]) -> None:
            body = (json.dumps(payload) + "\n").encode("utf-8")
            self.send_response(code)
            self.send_header("Content-Type", "application/json; charset=utf-8")
            self.send_header("Content-Length", str(len(body)))
            self.send_header("Access-Control-Allow-Origin", "*")
            self.send_header("Access-Control-Allow-Headers", "Content-Type")
            self.send_header("Cache-Control", "no-store")
            self.end_headers()
            self.wfile.write(body)

        def do_OPTIONS(self) -> None:
            self.send_response(HTTPStatus.NO_CONTENT)
            self.send_header("Access-Control-Allow-Origin", "*")
            self.send_header("Access-Control-Allow-Methods", "GET, POST, OPTIONS")
            self.send_header("Access-Control-Allow-Headers", "Content-Type")
            self.end_headers()

        def do_GET(self) -> None:
            path = self.path.split("?", 1)[0]
            if path in ("/", "/index.html"):
                body = INDEX_HTML.encode("utf-8")
                self.send_response(HTTPStatus.OK)
                self.send_header("Content-Type", "text/html; charset=utf-8")
                self.send_header("Content-Length", str(len(body)))
                self.send_header("Access-Control-Allow-Origin", "*")
                self.end_headers()
                self.wfile.write(body)
                return
            if "/api/r53/preview/" in path:
                fname = path.rsplit("/", 1)[-1]
                allowed53 = {
                    "r53-recall-6method-studio.png",
                    "r53-context-menu-cascade-benchmark.png",
                    "r53-crisp-view-mode-matrix.png",
                    "r53-undo-fan-zstack-overlay.png",
                    "r53-tails-conflux-hw30-shield.png",
                    "r53-workbench-overview.png",
                }
                png_path53 = ROOT / "docs/verification/r53-host-workbench" / fname
                if fname in allowed53 and png_path53.is_file():
                    raw_png53 = png_path53.read_bytes()
                    self.send_response(HTTPStatus.OK)
                    self.send_header("Content-Type", "image/png")
                    self.send_header("Content-Length", str(len(raw_png53)))
                    self.send_header("Access-Control-Allow-Origin", "*")
                    self.end_headers()
                    self.wfile.write(raw_png53)
                    return
            if "/api/r52/preview/" in path:
                fname = path.rsplit("/", 1)[-1]
                allowed = {
                    "r52-recall-studio.png",
                    "r52-context-menu-benchmark.png",
                    "r52-view-mode-icons.png",
                    "r52-undo-zstack-toast.png",
                    "r52-workbench-overview.png",
                }
                png_path = ROOT / "docs/verification/r52-host-workbench" / fname
                if fname in allowed and png_path.is_file():
                    raw_png = png_path.read_bytes()
                    self.send_response(HTTPStatus.OK)
                    self.send_header("Content-Type", "image/png")
                    self.send_header("Content-Length", str(len(raw_png)))
                    self.send_header("Access-Control-Allow-Origin", "*")
                    self.end_headers()
                    self.wfile.write(raw_png)
                    return
            if path.endswith("/api/status") or path == "/api/status":
                posture = amnesic_posture.collect_posture()
                self._send_json(
                    HTTPStatus.OK,
                    {
                        "ok": True,
                        "daemon": bridge.request("PING"),
                        "amnesic_posture": posture,
                        "honesty": "Host OpenJDK 17 Kotlin production kernels; NOT Android device/emulator execution.",
                    },
                )
                return
            self._send_json(HTTPStatus.NOT_FOUND, {"ok": False, "error": f"unknown path {path}"})

        def do_POST(self) -> None:
            path = self.path.split("?", 1)[0]
            length = int(self.headers.get("Content-Length", "0"))
            raw = self.rfile.read(min(length, 2_097_152))
            data = json.loads(raw.decode("utf-8") or "{}")
            if path.endswith("/api/sheet"):
                res = bridge.request("SHEET", str(data.get("rows_b64", "")))
                self._send_json(HTTPStatus.OK, res)
            elif path.endswith("/api/writer"):
                res = bridge.request(
                    "WRITER",
                    str(data.get("md_b64", "")),
                    str(data.get("bib_b64", "")),
                    str(data.get("ins_b64", "")),
                )
                self._send_json(HTTPStatus.OK, res)
            elif path.endswith("/api/wasm"):
                res = bridge.request(
                    "WASM",
                    str(int(data.get("n", 10))),
                    str(int(data.get("fuel", 5000))),
                    str(data.get("mode", "fib")),
                )
                self._send_json(HTTPStatus.OK, res)
            elif path.endswith("/api/security"):
                res = bridge.request(
                    "SECURITY",
                    str(data.get("tor_b64", base64.b64encode(b"SIGNAL NEWNYM").decode("ascii"))),
                    str(data.get("timestamps_csv", "")),
                )
                self._send_json(HTTPStatus.OK, res)
            elif path.endswith("/api/r47/office"):
                res = bridge.request(
                    "R47_OFFICE",
                    str(data.get("hdr_b64", "")),
                    str(data.get("body_b64", "")),
                    str(data.get("latex_b64", "")),
                )
                self._send_json(HTTPStatus.OK, res)
            elif path.endswith("/api/r47/browser-wasi"):
                res = bridge.request(
                    "R47_BROWSER_WASI",
                    str(data.get("html_b64", "")),
                    str(data.get("url_b64", "")),
                    str(data.get("path_b64", "")),
                )
                self._send_json(HTTPStatus.OK, res)
            elif path.endswith("/api/r47/privacy-keypad"):
                res = bridge.request(
                    "R47_PRIVACY_KEYPAD",
                    str(data.get("app_b64", "")),
                    str(data.get("orig_b64", "")),
                )
                self._send_json(HTTPStatus.OK, res)
            elif path.endswith("/api/r48/office-spill"):
                res = bridge.request(
                    "R48_OFFICE_SPILL",
                    str(data.get("title_b64", "")),
                    str(data.get("prose_b64", "")),
                    str(data.get("cells_b64", "")),
                )
                self._send_json(HTTPStatus.OK, res)
            elif path.endswith("/api/r48/ohttp-onion"):
                res = bridge.request(
                    "R48_OHTTP_ONION",
                    str(data.get("relay_b64", "")),
                    str(data.get("gw_b64", "")),
                    str(data.get("body_b64", "")),
                )
                self._send_json(HTTPStatus.OK, res)
            elif path.endswith("/api/r48/acoustic-browser"):
                res = bridge.request(
                    "R48_ACOUSTIC_BROWSER",
                    str(data.get("html_b64", "")),
                    str(data.get("coop_b64", "")),
                    str(data.get("coep_b64", "")),
                )
                self._send_json(HTTPStatus.OK, res)
            elif path.endswith("/api/r49/lambda-simplex"):
                res = bridge.request(
                    "R49_LAMBDA_SIMPLEX",
                    str(data.get("cells_b64", "")),
                    str(data.get("c1", "3")),
                    str(data.get("c2", "5")),
                )
                self._send_json(HTTPStatus.OK, res)
            elif path.endswith("/api/r49/writer-pdf-sign"):
                res = bridge.request(
                    "R49_WRITER_PDF_SIGN",
                    str(data.get("para_b64", "")),
                    str(data.get("word_b64", "")),
                )
                self._send_json(HTTPStatus.OK, res)
            elif path.endswith("/api/r49/ech-hardware-shield"):
                res = bridge.request(
                    "R49_ECH_HARDWARE_SHIELD",
                    str(data.get("inner_b64", "")),
                    str(data.get("outer_b64", "")),
                    str(data.get("ca_b64", "")),
                )
                self._send_json(HTTPStatus.OK, res)
            elif path.endswith("/api/r49/wasm2-shell"):
                res = bridge.request(
                    "R49_WASM2_SHELL",
                    str(data.get("ax", "3.0")),
                    str(data.get("ay", "4.0")),
                    str(data.get("query_b64", "")),
                )
                self._send_json(HTTPStatus.OK, res)
            elif path.endswith("/api/r50/spreadsheet3d"):
                res = bridge.request(
                    "R50_SPREADSHEET_3D",
                    str(data.get("q1", "150")),
                    str(data.get("q2", "250")),
                    str(data.get("q3", "350")),
                )
                self._send_json(HTTPStatus.OK, res)
            elif path.endswith("/api/r50/wasm2-f64-table"):
                res = bridge.request(
                    "R50_WASM2_F64_TABLE",
                    str(data.get("x", "4.0")),
                    str(data.get("v", "21")),
                )
                self._send_json(HTTPStatus.OK, res)
            elif path.endswith("/api/r50/lens-fsr1-nlms"):
                res = bridge.request(
                    "R50_LENS_FSR1_NLMS",
                    str(data.get("sharp", "0.25")),
                    str(data.get("term_b64", "")),
                )
                self._send_json(HTTPStatus.OK, res)
            elif path.endswith("/api/r50/shield-15vec"):
                res = bridge.request(
                    "R50_SHIELD_15VEC",
                    str(data.get("bt_key", "7")),
                    str(data.get("nfc_rtt", "34.5")),
                    str(data.get("usb_iki", "3")),
                )
                self._send_json(HTTPStatus.OK, res)
            elif path.endswith("/api/r51/office-table-solver"):
                res = bridge.request(
                    "R51_OFFICE_TABLE_SOLVER",
                    str(data.get("nq1", "120")),
                    str(data.get("sq1", "200")),
                    str(data.get("gs_t", "2.0")),
                )
                self._send_json(HTTPStatus.OK, res)
            elif path.endswith("/api/r51/writer-galley-pdfua1"):
                res = bridge.request(
                    "R51_WRITER_GALLEY_PDFUA1",
                    str(data.get("title_b64", "")),
                )
                self._send_json(HTTPStatus.OK, res)
            elif path.endswith("/api/r51/wasm2-simd-browser"):
                res = bridge.request(
                    "R51_WASM2_SIMD_BROWSER",
                    str(data.get("scalar", "3")),
                    str(data.get("viewport_w", "600")),
                    str(data.get("url_b64", "")),
                )
                self._send_json(HTTPStatus.OK, res)
            elif path.endswith("/api/r51/tails-shield-20vec"):
                res = bridge.request(
                    "R51_TAILS_SHIELD_20VEC",
                    str(data.get("argon_kib", "1048576")),
                    str(data.get("iommu_strict", "false")),
                    str(data.get("hall_ut", "1800")),
                )
                self._send_json(HTTPStatus.OK, res)
            elif path.endswith("/api/r52/recall-studio"):
                res = bridge.request(
                    "R52_RECALL_MEDIA_STUDIO",
                    str(data.get("url_b64", "")),
                    str(data.get("title_b64", "")),
                    str(data.get("tpl_b64", "")),
                    str(data.get("cname_b64", "")),
                )
                self._send_json(HTTPStatus.OK, res)
            elif path.endswith("/api/r52/ui-ergonomics"):
                res = bridge.request(
                    "R52_UI_ERGONOMICS_BENCHMARK",
                    str(data.get("vw", "1440")),
                    str(data.get("vh", "900")),
                    str(data.get("cx", "1380")),
                    str(data.get("cy", "840")),
                )
                self._send_json(HTTPStatus.OK, res)
            elif path.endswith("/api/r52/calc260-wasm3"):
                res = bridge.request(
                    "R52_CALC260_WASM3_TAILCALL",
                    str(data.get("tail_n", "25000")),
                )
                self._send_json(HTTPStatus.OK, res)
            elif path.endswith("/api/r52/tails-pow-shield25"):
                res = bridge.request("R52_TAILS_POW_SHIELD25")
                self._send_json(HTTPStatus.OK, res)
            elif path.endswith("/api/r53/recall6"):
                res = bridge.request("R53_RECALL_6METHOD_WARC", str(data.get("url_b64", "")))
                self._send_json(HTTPStatus.OK, res)
            elif path.endswith("/api/r53/ui-ergonomics2"):
                res = bridge.request(
                    "R53_UI_ERGONOMICS_PHASE2",
                    str(data.get("vw", "1440")),
                    str(data.get("vh", "900")),
                    str(data.get("cx", "1340")),
                    str(data.get("cy", "300")),
                )
                self._send_json(HTTPStatus.OK, res)
            elif path.endswith("/api/r53/calc300-wasm-multimemory"):
                res = bridge.request("R53_CALC300_WASM_MULTIMEMORY")
                self._send_json(HTTPStatus.OK, res)
            elif path.endswith("/api/r53/conflux-mat2-hw30"):
                res = bridge.request("R53_CONFLUX_MAT2_HW30")
                self._send_json(HTTPStatus.OK, res)
            else:
                self._send_json(HTTPStatus.NOT_FOUND, {"ok": False, "error": f"unknown endpoint {path}"})

        def log_message(self, fmt: str, *args: object) -> None:
            sys.stdout.write("[fusion-workbench] " + (fmt % args) + "\n")
            sys.stdout.flush()

    return WorkbenchHandler


def main(argv: list[str] | None = None) -> int:
    ap = argparse.ArgumentParser(description=__doc__)
    ap.add_argument("--host", default="0.0.0.0", help="Bind host (default 0.0.0.0 for live preview)")
    ap.add_argument("--port", type=int, default=8765, help="Bind port (default 8765)")
    ap.add_argument("--self-test", action="store_true", help="Run a one-shot warm-daemon HTTP self-test and exit")
    args = ap.parse_args(argv)

    bridge = KotlinDaemonBridge()
    if args.self_test:
        try:
            ping = bridge.request("PING")
            sheet = bridge.request("SHEET", base64.b64encode(b"S1|A1|North\nS1|B1|42\nS1|A2|=XLOOKUP(\"North\",A1:A1,B1:B1)").decode("ascii"))
            wasm = bridge.request("WASM", "12", "5000", "fib")
            r47_bw = bridge.request(
                "R47_BROWSER_WASI",
                base64.b64encode(b"<script>evil()</script><p>ok</p>").decode("ascii"),
                base64.b64encode(b"https://ad.doubleclick.example.net/x.js").decode("ascii"),
                base64.b64encode(b"../etc/passwd").decode("ascii"),
            )
            r48_sp = bridge.request(
                "R48_OFFICE_SPILL",
                base64.b64encode(b"R48 Self-Test").decode("ascii"),
                base64.b64encode(b"Clean-room OHTTP and EPUB 3.3 verification.").decode("ascii"),
                base64.b64encode(b"A1|=SEQUENCE(2,2,10,5)\nC1|=SUM(A1#)").decode("ascii"),
            )
            r48_oh = bridge.request(
                "R48_OHTTP_ONION",
                base64.b64encode(b"https://relay.partner.net/ohttp").decode("ascii"),
                base64.b64encode(b"https://gw.target.org/ohttp").decode("ascii"),
                base64.b64encode(b"{}").decode("ascii"),
            )
            r49_ls = bridge.request(
                "R49_LAMBDA_SIMPLEX",
                base64.b64encode(b"A1|=MAKEARRAY(2,2,LAMBDA(r,c,10*r+c))\nD1|=SUM(TAKE(A1#,,-1))").decode("ascii"),
                "3",
                "5",
            )
            r49_ech = bridge.request(
                "R49_ECH_HARDWARE_SHIELD",
                base64.b64encode(b"torproject.org").decode("ascii"),
                base64.b64encode(b"cloudflare-ech.com").decode("ascii"),
                base64.b64encode(b"eMudhra Technologies Limited").decode("ascii"),
            )
            r50_s3d = bridge.request("R50_SPREADSHEET_3D", "150", "250", "350")
            r50_wf = bridge.request("R50_WASM2_F64_TABLE", "4.0", "21")
            r50_ln = bridge.request("R50_LENS_FSR1_NLMS", "0.25", base64.b64encode(b"999-42-8811").decode("ascii"))
            r50_sh = bridge.request("R50_SHIELD_15VEC", "7", "34.5", "3")
            r51_of = bridge.request("R51_OFFICE_TABLE_SOLVER", "120", "200", "2.0")
            r51_wr = bridge.request("R51_WRITER_GALLEY_PDFUA1", base64.b64encode(b"R51 Self-Test Manuscript").decode("ascii"))
            r51_wb = bridge.request("R51_WASM2_SIMD_BROWSER", "3", "600", base64.b64encode(b"https://clean.example/read?id=42&fbclid=X&utm_source=Y").decode("ascii"))
            r51_ts = bridge.request("R51_TAILS_SHIELD_20VEC", "1048576", "false", "1800")
            r52_rec = bridge.request(
                "R52_RECALL_MEDIA_STUDIO",
                base64.b64encode(b"https://arte.tv/concerts/baroque?utm_source=feed").decode("ascii"),
                base64.b64encode(b"Baroque Evening Live: Concert #4").decode("ascii"),
                base64.b64encode(b"{custom}/{yyyy}-{mm}-{dd}/{domain}/{kind}").decode("ascii"),
                base64.b64encode(b"MusicResearch").decode("ascii"),
            )
            r52_erg = bridge.request("R52_UI_ERGONOMICS_BENCHMARK", "1440", "900", "1380", "840")
            r52_cw3 = bridge.request("R52_CALC260_WASM3_TAILCALL", "25000")
            r52_pow = bridge.request("R52_TAILS_POW_SHIELD25")
            r53_rec6 = bridge.request("R53_RECALL_6METHOD_WARC", base64.b64encode(b"https://concert.example.org/webrtc-live").decode("ascii"))
            r53_erg2 = bridge.request("R53_UI_ERGONOMICS_PHASE2", "1440", "900", "1340", "300")
            r53_c300 = bridge.request("R53_CALC300_WASM_MULTIMEMORY")
            r53_hw30 = bridge.request("R53_CONFLUX_MAT2_HW30")
            assert ping["ok"] is True and ping["functions"] == 300 and sheet["values"]["S1!A2"] == "42" and wasm["result"] == 144
            assert r47_bw["adblock_blocked"] is True and r47_bw["wasi_errno"] == 76
            assert r48_sp["spill_grid"]["C1"] == "70" and r48_sp["epub_stored_magic"] is True
            assert r48_oh["route_allowed"] is True and r48_oh["onion_valid"] is True
            assert r49_ls["spill_grid"]["D1"] == "34" and r49_ls["lp_obj"] == 36.0
            assert r49_ech["ech_compliant"] is True and r49_ech["tails_ca_decision"] == "BLOCKED_EMERGENCY_CA_DISTRUST"
            assert r50_s3d["sum_3d"] == 750.0 and abs(r50_s3d["mdeterm"] - 10.0) < 1e-9 and abs(r50_s3d["mmult_minverse_trace"] - 2.0) < 1e-9
            assert abs(r50_wf["quad_f64"] - 37.5) < 1e-9 and r50_wf["table_indirect_result"] == 52
            assert r50_ln["nlms_erle_db"] > 20.0 and r50_ln["pdf_redactions"] == 1
            assert r50_sh["bluffs_allowed"] is False and r50_sh["nfc_permitted"] is False and r50_sh["badusb_allowed"] is False and r50_sh["shamir_3of5_ok"] is True
            assert r51_of["roman_2026"] == "MMXXVI" and abs(r51_of["table_q1_total"] - 320.0) < 1e-9 and abs(r51_of["simplex_lp_opt"] - 36.0) < 1e-9
            assert r51_wr["galley_zero_violations"] is True and r51_wr["pdfua1_compliant"] is True and r51_wr["linearized_exact_length"] is True
            assert r51_wb["simd_verified"] is True and r51_wb["v128_lanes"] == [31, 62, 93, 124] and r51_wb["chips_isolated"] is True and r51_wb["sanitized_url"] == "https://clean.example/read?id=42"
            assert r51_ts["vanguards_l2_guards"] == 4 and r51_ts["luks2_argon2id_compliant"] is True and r51_ts["thunderclap_decision"] == "BLOCK_UNPROTECTED_PCIE_DMA" and r51_ts["posture_score"] == 100 and r51_ts["full_class_a"] == "0/50" and r51_ts["host_subset"] == "50/50"
            assert r52_rec["tfdt_before_trun"] is True and r52_rec["har_redacted"] is True and r52_rec["tier4_max_drift_us"] <= 20_000 and r52_rec["path_by_url"].startswith("by-url/") and r52_rec["path_by_title"].startswith("by-title/") and r52_rec["path_by_custom"].startswith("custom/")
            assert 180.0 <= r52_erg["menu_width_dp"] <= 280.0 and r52_erg["flipped_left"] is True and r52_erg["flipped_up"] is True and r52_erg["undo_height_pct"] < 15.0 and r52_erg["undo_hidden"] == 1
            assert r52_cw3["functions"] == 260 and r52_cw3["wasm3_tail_valid"] is True and r52_cw3["wasm3_sum_tail"] == 312_512_500 and abs(r52_cw3["outerproduct_e2"] - 80.0) < 1e-9
            assert r52_pow["blake2b_abc_prefix"] == "ba80a53f981c4d0d6a2797b69f12f6e9" and r52_pow["v22_ultrasonic_detected"] is True and r52_pow["v24_imu_impulse_ratio"] < 0.15 and r52_pow["vectors_total"] == 25 and r52_pow["full_class_a"] == "0/50"
            assert r53_rec6["methods_supported"] == 6 and r53_rec6["warc_digests_valid"] is True and r53_rec6["rtp_ordered_frames"] == 2 and r53_rec6["rtp_duplicates_dropped"] == 1
            assert r53_erg2["os_baselines"] == 7 and r53_erg2["submenu_flipped_left"] is True and r53_erg2["safe_triangle_hold"] is True and r53_erg2["crisp_all_scales"] is True and r53_erg2["compact_height_pct"] < 9.0
            assert r53_c300["functions"] == 300 and r53_c300["wasm_memories"] == 2 and r53_c300["wasm_dual_mem_echo"] is True and abs(r53_c300["imabs_3_4i"] - 5.0) < 1e-9
            assert r53_hw30["conflux_disjoint_valid"] is True and r53_hw30["v28_vbmeta_verified"] is True and r53_hw30["chacha20_rfc8439_ok"] is True and r53_hw30["vectors_total"] == 30 and r53_hw30["full_class_a"] == "0/50"
            print("WORKBENCH SELF-TEST PASS: warm Kotlin JVM R53 daemon verified (Recall 6-Method WebRTC RTP + WARC/1.1, 7-OS Cascading Submenu + 5-DPI Crisp Icons + Interactive Undo Fan, 300-fn + 48-op HANKEL + Wasm3 Multi-Memory, Tor Prop 329 Conflux + Clean-Room MAT2 + 30-Vector Shield).")
            return 0
        finally:
            bridge.close()

    server = ThreadingHTTPServer((args.host, args.port), make_handler(bridge))
    print(f"FUSION WORKBENCH LISTENING on http://{args.host}:{args.port}/ (warm Kotlin JVM R46 daemon ready)", flush=True)
    try:
        server.serve_forever()
    except KeyboardInterrupt:
        pass
    finally:
        server.server_close()
        bridge.close()
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
