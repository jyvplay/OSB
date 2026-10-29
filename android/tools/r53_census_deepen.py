#!/usr/bin/env python3
"""Idempotently deepen ALL 50/50 module rows in docs/module-testability.json with 96 new R53 pure-JVM kernels, 7 new Compose UI files, and 19 TOUCHED production files.

Never modifies docs/module-audit.json (full-spec Class A acceptance remains strictly 0/50 = 0.00%).
Preserves exact R47..R52 wiring (`run_r49_contracts.py`..`run_r52_contracts.py` stay in `command`)
while wiring `run_r53_contracts.py` across all 50/50 modules.
"""
from __future__ import annotations

import hashlib
import json
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "tools"))
import module_testability as census

R53_MODULE_KERNELS: list[tuple[str, str, str]] = [
    (':app', 'app/src/main/java/dev/fusion/shell/RecallAutoArchivalPipelineOrchestrator.kt', 'RecallAutoArchivalPipelineOrchestrator'),
    (':app', 'app/src/main/java/dev/fusion/shell/ShellMultiMonitorDpiScaleRouter.kt', 'ShellMultiMonitorDpiScaleRouter'),
    (':baselineprofile', 'baselineprofile/src/main/kotlin/dev/fusion/baselineprofile/StartupDexLayoutHotnessScorer.kt', 'StartupDexLayoutHotnessScorer'),
    (':benchmark', 'benchmark/src/main/java/dev/fusion/benchmark/MannWhitneyNonParametricGate.kt', 'MannWhitneyNonParametricGate'),
    (':cast:airplay', 'cast/airplay/src/main/kotlin/dev/fusion/airplay/RaopRtpTimingSyncCalculator.kt', 'RaopRtpTimingSyncCalculator'),
    (':cast:castv2', 'cast/castv2/src/main/kotlin/dev/fusion/castv2/CastV2FramingHeaderCodec.kt', 'CastV2FramingHeaderCodec'),
    (':cast:dlna', 'cast/dlna/src/main/kotlin/dev/fusion/cast/dlna/UpnpSoapActionEnvelopeSanitizer.kt', 'UpnpSoapActionEnvelopeSanitizer'),
    (':core:capabilities', 'core/capabilities/src/main/java/dev/fusion/capabilities/SeccompBpfSyscallAllowlistPlanner.kt', 'SeccompBpfSyscallAllowlistPlanner'),
    (':core:capabilities', 'core/capabilities/src/main/java/dev/fusion/capabilities/WasmMultiMemoryIsolationVerifier.kt', 'WasmMultiMemoryIsolationVerifier'),
    (':core:commands', 'core/commands/src/main/kotlin/dev/fusion/commands/ContextMenuMnemonicKeyNavigator.kt', 'ContextMenuMnemonicKeyNavigator'),
    (':core:commands', 'core/commands/src/main/kotlin/dev/fusion/commands/UndoDeckInteractiveFanController.kt', 'UndoDeckInteractiveFanController'),
    (':core:designsystem', 'core/designsystem/src/main/kotlin/dev/fusion/designsystem/TranslucentGlassBackdropContrastGuard.kt', 'TranslucentGlassBackdropContrastGuard'),
    (':core:designsystem', 'core/designsystem/src/main/kotlin/dev/fusion/designsystem/ViewModePixelGridCrispHinting.kt', 'ViewModePixelGridCrispHinting'),
    (':core:documents', 'core/documents/src/main/kotlin/dev/fusion/documents/WriterSectionColumnsPageLayout.kt', 'WriterSectionColumnsPageLayout'),
    (':core:documents', 'core/documents/src/main/kotlin/dev/fusion/documents/WriterTableOfFiguresIndexBuilder.kt', 'WriterTableOfFiguresIndexBuilder'),
    (':core:files', 'core/files/src/main/kotlin/dev/fusion/files/RecallCdxjReplayIndexCodec.kt', 'RecallCdxjReplayIndexCodec'),
    (':core:files', 'core/files/src/main/kotlin/dev/fusion/files/RecallVaultQuotaLifecyclePruner.kt', 'RecallVaultQuotaLifecyclePruner'),
    (':core:hw', 'core/hw/src/main/kotlin/dev/fusion/hw/AmbientLightKeystrokeShadowGuard.kt', 'AmbientLightKeystrokeShadowGuard'),
    (':core:hw', 'core/hw/src/main/kotlin/dev/fusion/hw/BleHogpKeystrokeFirewall.kt', 'BleHogpKeystrokeFirewall'),
    (':core:hw', 'core/hw/src/main/kotlin/dev/fusion/hw/DramRowhammerRefreshMonitor.kt', 'DramRowhammerRefreshMonitor'),
    (':core:hw', 'core/hw/src/main/kotlin/dev/fusion/hw/TempestSubpixelEmsecDitherer.kt', 'TempestSubpixelEmsecDitherer'),
    (':core:hw', 'core/hw/src/main/kotlin/dev/fusion/hw/VerifiedBootVbmetaDigestAuditor.kt', 'VerifiedBootVbmetaDigestAuditor'),
    (':core:index', 'core/index/src/main/kotlin/dev/fusion/index/PositionalPhraseInvertedIndex.kt', 'PositionalPhraseInvertedIndex'),
    (':core:index', 'core/index/src/main/kotlin/dev/fusion/index/RecallPerceptualDHashDeduplicator.kt', 'RecallPerceptualDHashDeduplicator'),
    (':core:model', 'core/model/src/main/kotlin/dev/fusion/model/DeterministicUlidGenerator.kt', 'DeterministicUlidGenerator'),
    (':core:perf', 'core/perf/src/main/kotlin/dev/fusion/perf/RingBufferLockFreeTelemetry.kt', 'RingBufferLockFreeTelemetry'),
    (':core:security', 'core/security/src/main/kotlin/dev/fusion/security/ChaCha20Rfc8439StreamBlock.kt', 'ChaCha20Rfc8439StreamBlock'),
    (':core:security', 'core/security/src/main/kotlin/dev/fusion/security/Slip39MnemonicShareChecksumCodec.kt', 'Slip39MnemonicShareChecksumCodec'),
    (':core:settings', 'core/settings/src/main/kotlin/dev/fusion/settings/RecallUserPreferencesSchema.kt', 'RecallUserPreferencesSchema'),
    (':core:storage', 'core/storage/src/main/kotlin/dev/fusion/storage/ContentAddressedBlobCasStore.kt', 'ContentAddressedBlobCasStore'),
    (':core:windowing', 'core/windowing/src/main/kotlin/dev/fusion/windowing/ContextMenuSubmenuCascadeGeometry.kt', 'ContextMenuSubmenuCascadeGeometry'),
    (':core:windowing', 'core/windowing/src/main/kotlin/dev/fusion/windowing/DesktopWorkAreaInsetPartitioner.kt', 'DesktopWorkAreaInsetPartitioner'),
    (':core:windowing', 'core/windowing/src/main/kotlin/dev/fusion/windowing/PointerSafeTriangleSubmenuFilter.kt', 'PointerSafeTriangleSubmenuFilter'),
    (':feature:accessibility', 'feature/accessibility/src/main/kotlin/dev/fusion/a11y/ReducedTransparencyAndMotionAdapter.kt', 'ReducedTransparencyAndMotionAdapter'),
    (':feature:alerts', 'feature/alerts/src/main/kotlin/dev/fusion/alerts/SensitiveOtpNotificationRedactor.kt', 'SensitiveOtpNotificationRedactor'),
    (':feature:automation', 'feature/automation/src/main/kotlin/dev/fusion/automation/CronNextFireScheduleEvaluator.kt', 'CronNextFireScheduleEvaluator'),
    (':feature:backup', 'feature/backup/src/main/kotlin/dev/fusion/backup/RabinKarpDeltaPatchGenerator.kt', 'RabinKarpDeltaPatchGenerator'),
    (':feature:beam', 'feature/beam/src/main/kotlin/dev/fusion/beam/MdnsServiceTxtPrivacySanitizer.kt', 'MdnsServiceTxtPrivacySanitizer'),
    (':feature:browser', 'feature/browser/src/main/kotlin/dev/fusion/browser/model/BrowserLinkMediaContextMenuPlanner.kt', 'BrowserLinkMediaContextMenuPlanner'),
    (':feature:browser', 'feature/browser/src/main/kotlin/dev/fusion/browser/model/BrowserStorageAccessPartitionGuard.kt', 'BrowserStorageAccessPartitionGuard'),
    (':feature:browser', 'feature/browser/src/main/kotlin/dev/fusion/browser/model/ClearSiteDataHeaderExecutor.kt', 'ClearSiteDataHeaderExecutor'),
    (':feature:browser', 'feature/browser/src/main/kotlin/dev/fusion/browser/model/RecallMhtmlMultipartSnapshotter.kt', 'RecallMhtmlMultipartSnapshotter'),
    (':feature:browser', 'feature/browser/src/main/kotlin/dev/fusion/browser/model/RecallSrcsetPictureDensitySelector.kt', 'RecallSrcsetPictureDensitySelector'),
    (':feature:browser', 'feature/browser/src/main/kotlin/dev/fusion/browser/model/RecallTier25WebRtcRtpDepacketizer.kt', 'RecallTier25WebRtcRtpDepacketizer'),
    (':feature:browser', 'feature/browser/src/main/kotlin/dev/fusion/browser/model/RecallTier5WarcIso28500Archiver.kt', 'RecallTier5WarcIso28500Archiver'),
    (':feature:calc', 'feature/calc/src/main/kotlin/dev/fusion/calc/MonteCarloSensitivityTableRunner.kt', 'MonteCarloSensitivityTableRunner'),
    (':feature:calc', 'feature/calc/src/main/kotlin/dev/fusion/calc/SpreadsheetGoalSeekBrentSolver.kt', 'SpreadsheetGoalSeekBrentSolver'),
    (':feature:capture', 'feature/capture/src/main/kotlin/dev/fusion/capture/AudioCapturePolicyClassifier.kt', 'AudioCapturePolicyClassifier'),
    (':feature:capture', 'feature/capture/src/main/kotlin/dev/fusion/capture/CaptureSegmentConcatManifestBuilder.kt', 'CaptureSegmentConcatManifestBuilder'),
    (':feature:capture', 'feature/capture/src/main/kotlin/dev/fusion/capture/RecallViewportCursorAnnotationCompositor.kt', 'RecallViewportCursorAnnotationCompositor'),
    (':feature:continuity', 'feature/continuity/src/main/kotlin/dev/fusion/continuity/ClipboardAutoExpireSanitizer.kt', 'ClipboardAutoExpireSanitizer'),
    (':feature:editor', 'feature/editor/src/main/java/dev/fusion/editor/EditorSelectionContextMenuPlanner.kt', 'EditorSelectionContextMenuPlanner'),
    (':feature:editor', 'feature/editor/src/main/java/dev/fusion/editor/SyntaxHighlightLexicalScanner.kt', 'SyntaxHighlightLexicalScanner'),
    (':feature:editor', 'feature/editor/src/main/java/dev/fusion/editor/WriterTrackChangesAcceptRejectEngine.kt', 'WriterTrackChangesAcceptRejectEngine'),
    (':feature:files', 'feature/files/src/main/kotlin/dev/fusion/filesui/ContextMenuSelectionVerbResolver.kt', 'ContextMenuSelectionVerbResolver'),
    (':feature:files', 'feature/files/src/main/kotlin/dev/fusion/filesui/DualPaneViewModeSyncStore.kt', 'DualPaneViewModeSyncStore'),
    (':feature:find', 'feature/find/src/main/kotlin/dev/fusion/find/TrigramFuzzyCommandMatcher.kt', 'TrigramFuzzyCommandMatcher'),
    (':feature:launcher', 'feature/launcher/src/main/kotlin/dev/fusion/launcher/ShelfAppCategoryColumnsModel.kt', 'ShelfAppCategoryColumnsModel'),
    (':feature:lens', 'feature/lens/src/main/kotlin/dev/fusion/lens/DocumentSkewQuadRectifier.kt', 'DocumentSkewQuadRectifier'),
    (':feature:maps', 'feature/maps/src/main/kotlin/dev/fusion/maps/DouglasPeuckerTrackSimplifier.kt', 'DouglasPeuckerTrackSimplifier'),
    (':feature:media', 'feature/media/src/main/kotlin/dev/fusion/media/LoudnessEbuR128MomentaryMeter.kt', 'LoudnessEbuR128MomentaryMeter'),
    (':feature:media', 'feature/media/src/main/kotlin/dev/fusion/media/MatroskaWebmEbmlClusterProbe.kt', 'MatroskaWebmEbmlClusterProbe'),
    (':feature:media', 'feature/media/src/main/kotlin/dev/fusion/media/MediaCollectionViewModeGrouper.kt', 'MediaCollectionViewModeGrouper'),
    (':feature:media', 'feature/media/src/main/kotlin/dev/fusion/media/Mp3MpegFrameDurationXingScanner.kt', 'Mp3MpegFrameDurationXingScanner'),
    (':feature:media', 'feature/media/src/main/kotlin/dev/fusion/media/OggVorbisCommentChapterEmbedder.kt', 'OggVorbisCommentChapterEmbedder'),
    (':feature:media', 'feature/media/src/main/kotlin/dev/fusion/media/RecallAudioSilenceTrackSplitter.kt', 'RecallAudioSilenceTrackSplitter'),
    (':feature:messages', 'feature/messages/src/main/kotlin/dev/fusion/messages/SealedCertificateBucketEnvelope.kt', 'SealedCertificateBucketEnvelope'),
    (':feature:notes', 'feature/notes/src/main/kotlin/dev/fusion/notes/MarkdownFootnoteAndCalloutParser.kt', 'MarkdownFootnoteAndCalloutParser'),
    (':feature:notes', 'feature/notes/src/main/kotlin/dev/fusion/notes/NotesCanvasInfiniteGraphLayout.kt', 'NotesCanvasInfiniteGraphLayout'),
    (':feature:notes', 'feature/notes/src/main/kotlin/dev/fusion/notes/NotesCollectionViewModeSorter.kt', 'NotesCollectionViewModeSorter'),
    (':feature:settings', 'feature/settings/src/main/kotlin/dev/fusion/settingsui/UiErgonomicsPreferencesValidator.kt', 'UiErgonomicsPreferencesValidator'),
    (':feature:terminal', 'feature/terminal/src/main/kotlin/dev/fusion/terminal/AnsiSgrTruecolorScrollbackBuffer.kt', 'AnsiSgrTruecolorScrollbackBuffer'),
    (':feature:terminal', 'feature/terminal/src/main/kotlin/dev/fusion/terminal/TerminalOsc52ClipboardSanitizer.kt', 'TerminalOsc52ClipboardSanitizer'),
    (':feature:together', 'feature/together/src/main/kotlin/dev/fusion/together/CausalTreeAwarenessPresenceRoster.kt', 'CausalTreeAwarenessPresenceRoster'),
    (':feature:vault', 'feature/vault/src/main/kotlin/dev/fusion/vault/VaultEmergencyDuressDecoyPlanner.kt', 'VaultEmergencyDuressDecoyPlanner'),
    (':feature:vault', 'feature/vault/src/main/kotlin/dev/fusion/vault/VaultItemViewModeOrganizer.kt', 'VaultItemViewModeOrganizer'),
    (':format:archive', 'format/archive/src/main/kotlin/dev/fusion/archive/EpubCfiFragmentLocator.kt', 'EpubCfiFragmentLocator'),
    (':format:archive', 'format/archive/src/main/kotlin/dev/fusion/archive/WarcGzipConcatMemberInspector.kt', 'WarcGzipConcatMemberInspector'),
    (':format:image', 'format/image/src/main/kotlin/dev/fusion/format/image/JpegExifAppMarkerSanitizer.kt', 'JpegExifAppMarkerSanitizer'),
    (':format:image', 'format/image/src/main/kotlin/dev/fusion/format/image/PngChunkSanitizerAndCrcCodec.kt', 'PngChunkSanitizerAndCrcCodec'),
    (':format:lok', 'format/lok/src/main/java/dev/fusion/lok/LokTileInvalidationCoalescer.kt', 'LokTileInvalidationCoalescer'),
    (':format:lok', 'format/lok/src/main/java/dev/fusion/lok/OdfPresentationOdpBuilder.kt', 'OdfPresentationOdpBuilder'),
    (':format:lok', 'format/lok/src/main/java/dev/fusion/lok/OdfSpreadsheetFormulaTranslator.kt', 'OdfSpreadsheetFormulaTranslator'),
    (':format:native', 'format/native/src/main/kotlin/dev/fusion/nativefmt/Gltf2BinaryGlbChunkInspector.kt', 'Gltf2BinaryGlbChunkInspector'),
    (':format:ooxml', 'format/ooxml/src/main/kotlin/dev/fusion/ooxml/DocxEquationOmmlBuilder.kt', 'DocxEquationOmmlBuilder'),
    (':format:ooxml', 'format/ooxml/src/main/kotlin/dev/fusion/ooxml/DocxTableLayoutGridEngine.kt', 'DocxTableLayoutGridEngine'),
    (':format:ooxml', 'format/ooxml/src/main/kotlin/dev/fusion/ooxml/PptxMasterThemeColorSchemeWriter.kt', 'PptxMasterThemeColorSchemeWriter'),
    (':format:ooxml', 'format/ooxml/src/main/kotlin/dev/fusion/ooxml/XlsxPivotCacheRecordsWriter.kt', 'XlsxPivotCacheRecordsWriter'),
    (':format:pdf', 'format/pdf/src/main/java/dev/fusion/format/pdf/PdfDigitalSignatureByteRangePlanner.kt', 'PdfDigitalSignatureByteRangePlanner'),
    (':format:pdf', 'format/pdf/src/main/java/dev/fusion/format/pdf/PdfOptionalContentLayersWriter.kt', 'PdfOptionalContentLayersWriter'),
    (':format:pdf', 'format/pdf/src/main/java/dev/fusion/format/pdf/PdfTextColumnReflowExtractor.kt', 'PdfTextColumnReflowExtractor'),
    (':format:psd', 'format/psd/src/main/kotlin/dev/fusion/psd/PsdImageResourceIccExtractor.kt', 'PsdImageResourceIccExtractor'),
    (':privacy', 'privacy/src/main/kotlin/dev/fusion/privacy/OnionServiceClientAuthDescriptorCodec.kt', 'OnionServiceClientAuthDescriptorCodec'),
    (':privacy', 'privacy/src/main/kotlin/dev/fusion/privacy/TailsMetadataAnonymizer.kt', 'TailsMetadataAnonymizer'),
    (':privacy', 'privacy/src/main/kotlin/dev/fusion/privacy/TorConfluxMultipathScheduler.kt', 'TorConfluxMultipathScheduler'),
    (':testing', 'testing/src/main/kotlin/dev/fusion/testing/DifferentialBytecodeMutationOracle.kt', 'DifferentialBytecodeMutationOracle'),
]

R53_NEW_COMPOSE_UI_FILES: list[tuple[str, str, str]] = [
    (':core:designsystem', 'core/designsystem/src/main/kotlin/dev/fusion/designsystem/FusionContextMenuSubmenuHost.kt', 'FusionContextMenuSubmenuHost'),
    (':core:designsystem', 'core/designsystem/src/main/kotlin/dev/fusion/designsystem/StackedUndoFanOverlayBar.kt', 'StackedUndoFanOverlayBar'),
    (':core:designsystem', 'core/designsystem/src/main/kotlin/dev/fusion/designsystem/CrispHintedViewModeToolbar.kt', 'CrispHintedViewModeToolbar'),
    (':feature:browser', 'feature/browser/src/main/java/dev/fusion/browser/RecallSixMethodMatrixSheet.kt', 'RecallSixMethodMatrixSheet'),
    (':feature:media', 'feature/media/src/main/java/dev/fusion/media/MediaLibraryMultiViewPanel.kt', 'MediaLibraryMultiViewPanel'),
    (':feature:files', 'feature/files/src/main/java/dev/fusion/filesui/FilesDualPaneInspectorBar.kt', 'FilesDualPaneInspectorBar'),
    (':feature:vault', 'feature/vault/src/main/java/dev/fusion/vault/VaultColumnsSecretBrowser.kt', 'VaultColumnsSecretBrowser'),
]

TOUCHED_PRODUCTION_FILES: list[tuple[str, str, str]] = [
    (':format:ooxml', 'format/ooxml/src/main/kotlin/dev/fusion/ooxml/FormulaEval.kt', 'R53 expands FormulaEval from 260 to 300 pure functions (COMPLEX/IM*, COUP*/DURATION, BETA.DIST/TDIST/FISHER/MULTINOMIAL, HANKEL/TOEPLITZ/VANDERMONDE/KRONECKER/CONVOLVE1D/PARETOGRID).'),
    (':format:ooxml', 'format/ooxml/src/main/kotlin/dev/fusion/ooxml/SpillSheetEngine.kt', 'R53 expands SpillSheetEngine from 42 to 48 spilled dynamic-array operations while preserving RANDARRAY refusal.'),
    (':core:capabilities', 'core/capabilities/src/main/java/dev/fusion/capabilities/WasmSandbox.kt', 'R53 adds WebAssembly 3.0 Multi-Memory (up to 8 linear memories, cross-memory 0xFC 0x0A memory.copy, memidx-indexed load/store/size/grow).'),
    (':feature:browser', 'feature/browser/src/main/kotlin/dev/fusion/browser/model/RecallMethodEscalationRouter.kt', 'R53 expands RecallMethodEscalationRouter from 4 to 6 methods (adding TIER_25_WEBRTC_RTP_DEPACKETIZER and TIER_5_WARC_ISO28500_BUNDLE).'),
    (':app', 'app/src/main/java/dev/fusion/shell/RecallSessionCoordinator.kt', 'R53 expands RecallSessionCoordinator to track tiers 1..6 including WebRTC RTP and ISO 28500 WARC/1.1.'),
    (':feature:browser', 'feature/browser/src/main/java/dev/fusion/browser/BrowserRecallHud.kt', 'R53 upgrades BrowserRecallHud to 6-method badge & RecallSixMethodMatrixSheet trigger (syntax PARSE OK).'),
    (':feature:browser', 'feature/browser/src/main/java/dev/fusion/browser/BrowserScreen.kt', 'R53 wires RecallSixMethodMatrixSheet and BrowserLinkMediaContextMenuPlanner into BrowserScreen (syntax PARSE OK).'),
    (':feature:capture', 'feature/capture/src/main/java/dev/fusion/capture/RecallWorkspaceScreen.kt', 'R53 wires CrispHintedViewModeToolbar and 6-method status strip into RecallWorkspaceScreen (syntax PARSE OK).'),
    (':feature:capture', 'feature/capture/src/main/java/dev/fusion/capture/CaptureScreen.kt', 'R53 wires AudioCapturePolicyClassifier and CaptureSegmentConcatManifestBuilder into CaptureScreen (syntax PARSE OK).'),
    (':feature:media', 'feature/media/src/main/java/dev/fusion/media/MediaHub.kt', 'R53 wires LoudnessEbuR128MomentaryMeter and MediaLibraryMultiViewPanel into MediaHub (syntax PARSE OK).'),
    (':feature:files', 'feature/files/src/main/java/dev/fusion/filesui/FilesScreen.kt', 'R53 wires FilesDualPaneInspectorBar and StackedUndoFanOverlayBar into FilesScreen (syntax PARSE OK).'),
    (':feature:editor', 'feature/editor/src/main/java/dev/fusion/editor/EditorScreen.kt', 'R53 wires WriterTrackChangesAcceptRejectEngine and EditorSelectionContextMenuPlanner into EditorScreen (syntax PARSE OK).'),
    (':feature:vault', 'feature/vault/src/main/java/dev/fusion/vault/VaultScreen.kt', 'R53 wires Slip39MnemonicShareChecksumCodec and VaultColumnsSecretBrowser into VaultScreen (syntax PARSE OK).'),
    (':feature:notes', 'feature/notes/src/main/java/dev/fusion/notes/NotesScreen.kt', 'R53 wires MarkdownFootnoteAndCalloutParser and CrispHintedViewModeToolbar into NotesScreen (syntax PARSE OK).'),
    (':feature:launcher', 'feature/launcher/src/main/java/dev/fusion/launcher/Shelf.kt', 'R53 wires ShelfAppCategoryColumnsModel into Shelf (syntax PARSE OK).'),
    (':feature:terminal', 'feature/terminal/src/main/java/dev/fusion/terminal/TerminalScreen.kt', 'R53 wires TerminalOsc52ClipboardSanitizer query-block telemetry into TerminalScreen (syntax PARSE OK).'),
    (':privacy', 'privacy/src/main/java/dev/fusion/privacy/PrivacyActivity.kt', 'R53 wires TorConfluxMultipathScheduler Prop 329 disjoint-leg audit into PrivacyActivity (syntax PARSE OK).'),
    (':app', 'app/src/main/java/dev/fusion/shell/MainActivity.kt', 'R53 wires CrispHintedViewModeToolbar into MainActivity while preserving single-line DropdownMenu(expanded = actionsOpen (syntax PARSE OK).'),
    (':feature:calc', 'feature/calc/src/main/java/dev/fusion/calc/Calc.kt', 'R53 right-sizes Calc UnitSelector DropdownMenu to widthIn(180.dp, 280.dp) and wires SpreadsheetGoalSeekBrentSolver (syntax PARSE OK).'),
]

R53_KERNELS = R53_MODULE_KERNELS
TOUCHED: dict[str, tuple[str, str]] = {rel: (mod, note) for mod, rel, note in TOUCHED_PRODUCTION_FILES}
R53_CHECKS = [
    "testing/src/r53/kotlin/dev/fusion/testing/R53Checks.kt",
    "tools/test_r53_tools.py",
]
CMD = "python3 tools/run_r53_contracts.py"


def sha(rel: str) -> str:
    return hashlib.sha256((ROOT / rel).read_bytes()).hexdigest()


def main() -> int:
    assert len(R53_KERNELS) == 96, f"Expected 96 R53 pure-JVM kernels, got {len(R53_KERNELS)}"
    assert len(R53_NEW_COMPOSE_UI_FILES) == 7, f"Expected 7 R53 Compose UI files, got {len(R53_NEW_COMPOSE_UI_FILES)}"
    assert len(TOUCHED) == 19, f"Expected 19 R53 TOUCHED files, got {len(TOUCHED)}"
    deepen: dict[str, list[tuple[str, str]]] = {}
    for mod, path, name in R53_KERNELS:
        assert (ROOT / path).is_file(), f"Missing R53 kernel file: {path}"
        deepen.setdefault(mod, []).append((path, f"R53 adds {name} pure-JVM kernel."))
    for mod, path, name in R53_NEW_COMPOSE_UI_FILES:
        assert (ROOT / path).is_file(), f"Missing R53 Compose UI file: {path}"
        deepen.setdefault(mod, []).append((path, f"R53 adds {name} Compose UI component (syntax PARSE OK)."))
    for t_path in TOUCHED:
        assert (ROOT / t_path).is_file(), f"Missing R53 TOUCHED file: {t_path}"

    jp = ROOT / "docs/module-testability.json"
    data = json.loads(jp.read_text(encoding="utf-8"))

    also_allowed_repin = {
        "app/src/main/java/dev/fusion/shell/UsbWatchReceiver.kt",
        "tools/check_r26_android_syntax.py",
        "tools/fusion_workbench_server.py",
        "tools/fusion_workbench_cli.py",
        "tools/test_r47_tools.py",
        "tools/test_r48_tools.py",
        "tools/test_r49_tools.py",
        "tools/test_r50_tools.py",
        "tools/test_r51_tools.py",
        "tools/test_r52_tools.py",
        "feature/messages/src/main/kotlin/dev/fusion/messages/SealedSenderPaddingEnvelope.kt",
    }
    owned = {f for items in deepen.values() for f, _ in items} | set(R53_CHECKS) | set(TOUCHED) | also_allowed_repin
    stale: list[tuple[str, str]] = []
    for r in data["rows"]:
        for p, pinned in r["sha256"].items():
            if p not in owned and sha(p) != pinned:
                stale.append((r["module"], p))
    if stale:
        raise SystemExit(f"refusing to silently re-pin modified files outside R53 DEEPEN/TOUCHED: {stale}")

    seen_mods: set[str] = set()
    touched_applied: set[str] = set()
    for r in data["rows"]:
        mod = r["module"]
        for t_path, (t_mod, t_note) in TOUCHED.items():
            if mod == t_mod or t_path in r["production"] or t_path in r["sha256"]:
                if mod == t_mod and t_path not in r["production"]:
                    r["production"].append(t_path)
                touched_applied.add(t_path)
                if t_note not in r["behavior"]:
                    r["behavior"] = r["behavior"].rstrip() + " " + t_note
                r["sha256"][t_path] = sha(t_path)

        items = deepen.get(mod, [])
        if not items:
            continue
        for prod, note in items:
            if prod not in r["production"]:
                r["production"].append(prod)
            if note not in r["behavior"]:
                r["behavior"] = r["behavior"].rstrip() + " " + note
        for chk in R53_CHECKS:
            if chk not in r["checks"]:
                r["checks"].append(chk)
        if CMD not in r["command"]:
            r["command"] = (r["command"] + "; " + CMD) if r["command"] else CMD
        for p in r["production"] + r["checks"]:
            if (ROOT / p).is_file():
                r["sha256"][p] = sha(p)
        for p in list(r["sha256"].keys()):
            if p in also_allowed_repin and (ROOT / p).is_file():
                r["sha256"][p] = sha(p)
        seen_mods.add(mod)

    assert len(seen_mods) == 50, f"Expected 50/50 deepened modules, got {len(seen_mods)}"
    assert touched_applied == set(TOUCHED), f"Missing touched: {set(TOUCHED) - touched_applied}"

    census.validate(data)
    jp.write_text(json.dumps(data, indent=2) + "\n", encoding="utf-8")
    (ROOT / "docs/MODULE-TESTABILITY.md").write_text(census.render(data), encoding="utf-8")
    reloaded = json.loads(jp.read_text(encoding="utf-8"))
    census.validate(reloaded)
    assert census.render(reloaded) == (ROOT / "docs/MODULE-TESTABILITY.md").read_text(encoding="utf-8")
    print(f"R53 census deepen OK ({len(seen_mods)}/50 = 100.00% modules deepened, {len(touched_applied)} touched):", json.dumps(census.counts(reloaded)))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
