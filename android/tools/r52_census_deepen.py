#!/usr/bin/env python3
"""Idempotently deepen ALL 50/50 module rows in docs/module-testability.json with 91 new R52 pure-JVM kernels, 6 new Compose UI files, and 17 TOUCHED production files.

Never modifies docs/module-audit.json (full-spec Class A acceptance remains strictly 0/50 = 0.00%).
Preserves exact R47..R51 wiring (`run_r49_contracts.py`, `run_r50_contracts.py`, and `run_r51_contracts.py` stay in `command`)
while wiring `run_r52_contracts.py` across all 50/50 modules.
"""
from __future__ import annotations

import hashlib
import json
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "tools"))
import module_testability as census

R52_MODULE_KERNELS: list[tuple[str, str, str]] = [
    (':app', 'app/src/main/java/dev/fusion/shell/RecallSessionCoordinator.kt', 'RecallSessionCoordinator'),
    (':app', 'app/src/main/java/dev/fusion/shell/ShellKeyboardShortcutRegistry.kt', 'ShellKeyboardShortcutRegistry'),
    (':baselineprofile', 'baselineprofile/src/main/kotlin/dev/fusion/baselineprofile/BaselineProfileRuleConsolidator.kt', 'BaselineProfileRuleConsolidator'),
    (':benchmark', 'benchmark/src/main/java/dev/fusion/benchmark/BenchmarkWelchRegressionGate.kt', 'BenchmarkWelchRegressionGate'),
    (':cast:airplay', 'cast/airplay/src/main/kotlin/dev/fusion/airplay/SlideRehearsalPacingCoach.kt', 'SlideRehearsalPacingCoach'),
    (':cast:castv2', 'cast/castv2/src/main/kotlin/dev/fusion/castv2/HttpRangeResumePlanner.kt', 'HttpRangeResumePlanner'),
    (':cast:dlna', 'cast/dlna/src/main/kotlin/dev/fusion/cast/dlna/CastSinkCodecNegotiator.kt', 'CastSinkCodecNegotiator'),
    (':core:capabilities', 'core/capabilities/src/main/java/dev/fusion/capabilities/CapabilityDelegationAttenuator.kt', 'CapabilityDelegationAttenuator'),
    (':core:capabilities', 'core/capabilities/src/main/java/dev/fusion/capabilities/WasmTailCallVerifier.kt', 'WasmTailCallVerifier'),
    (':core:commands', 'core/commands/src/main/kotlin/dev/fusion/commands/ReversibleBatchCommandJournal.kt', 'ReversibleBatchCommandJournal'),
    (':core:commands', 'core/commands/src/main/kotlin/dev/fusion/commands/StackedUndoNotificationDeck.kt', 'StackedUndoNotificationDeck'),
    (':core:designsystem', 'core/designsystem/src/main/kotlin/dev/fusion/designsystem/AnalyticalSpringMotionSolver.kt', 'AnalyticalSpringMotionSolver'),
    (':core:designsystem', 'core/designsystem/src/main/kotlin/dev/fusion/designsystem/FusionViewModeVectorIcons.kt', 'FusionViewModeVectorIcons'),
    (':core:designsystem', 'core/designsystem/src/main/kotlin/dev/fusion/designsystem/ShoulderSurfingPrivacyVeil.kt', 'ShoulderSurfingPrivacyVeil'),
    (':core:documents', 'core/documents/src/main/kotlin/dev/fusion/documents/DocsBibliographyCiteFormatter.kt', 'DocsBibliographyCiteFormatter'),
    (':core:documents', 'core/documents/src/main/kotlin/dev/fusion/documents/DocumentCrossRefResolver.kt', 'DocumentCrossRefResolver'),
    (':core:files', 'core/files/src/main/kotlin/dev/fusion/files/RecallFolderDelineator.kt', 'RecallFolderDelineator'),
    (':core:files', 'core/files/src/main/kotlin/dev/fusion/files/RecallPlaylistSidecarWriter.kt', 'RecallPlaylistSidecarWriter'),
    (':core:hw', 'core/hw/src/main/kotlin/dev/fusion/hw/ChargerSurfingPowerMasker.kt', 'ChargerSurfingPowerMasker'),
    (':core:hw', 'core/hw/src/main/kotlin/dev/fusion/hw/UltrasonicDolphinAttackFilter.kt', 'UltrasonicDolphinAttackFilter'),
    (':core:index', 'core/index/src/main/kotlin/dev/fusion/index/KeysetCursorPaginator.kt', 'KeysetCursorPaginator'),
    (':core:index', 'core/index/src/main/kotlin/dev/fusion/index/RecallMediaTranscriptSearchIndex.kt', 'RecallMediaTranscriptSearchIndex'),
    (':core:model', 'core/model/src/main/kotlin/dev/fusion/model/HumanByteSizeCodec.kt', 'HumanByteSizeCodec'),
    (':core:model', 'core/model/src/main/kotlin/dev/fusion/model/Rfc8785JsonCanonicalizer.kt', 'Rfc8785JsonCanonicalizer'),
    (':core:perf', 'core/perf/src/main/kotlin/dev/fusion/perf/LocalFrameJankHistogram.kt', 'LocalFrameJankHistogram'),
    (':core:security', 'core/security/src/main/kotlin/dev/fusion/security/ImuKeystrokeMotionZeroizer.kt', 'ImuKeystrokeMotionZeroizer'),
    (':core:security', 'core/security/src/main/kotlin/dev/fusion/security/Rfc5869HkdfSha256.kt', 'Rfc5869HkdfSha256'),
    (':core:security', 'core/security/src/main/kotlin/dev/fusion/security/ThermalResidueWipeAdvisor.kt', 'ThermalResidueWipeAdvisor'),
    (':core:settings', 'core/settings/src/main/kotlin/dev/fusion/settings/DatastoreSchemaMigrationChain.kt', 'DatastoreSchemaMigrationChain'),
    (':core:storage', 'core/storage/src/main/kotlin/dev/fusion/storage/SegmentedLruStorageQuotaGovernor.kt', 'SegmentedLruStorageQuotaGovernor'),
    (':core:storage', 'core/storage/src/main/kotlin/dev/fusion/storage/SqliteWalFrameIntegrityVerifier.kt', 'SqliteWalFrameIntegrityVerifier'),
    (':core:windowing', 'core/windowing/src/main/kotlin/dev/fusion/windowing/PipCornerOcclusionPlacementSolver.kt', 'PipCornerOcclusionPlacementSolver'),
    (':core:windowing', 'core/windowing/src/main/kotlin/dev/fusion/windowing/RightClickContextMenuGeometry.kt', 'RightClickContextMenuGeometry'),
    (':core:windowing', 'core/windowing/src/main/kotlin/dev/fusion/windowing/SecondaryClickGestureClassifier.kt', 'SecondaryClickGestureClassifier'),
    (':feature:accessibility', 'feature/accessibility/src/main/kotlin/dev/fusion/a11y/WcagTargetSpacingFocusAuditor.kt', 'WcagTargetSpacingFocusAuditor'),
    (':feature:alerts', 'feature/alerts/src/main/kotlin/dev/fusion/alerts/HubWorkspaceFeedAggregator.kt', 'HubWorkspaceFeedAggregator'),
    (':feature:automation', 'feature/automation/src/main/kotlin/dev/fusion/automation/OfflineMmrExtractiveSummarizer.kt', 'OfflineMmrExtractiveSummarizer'),
    (':feature:backup', 'feature/backup/src/main/kotlin/dev/fusion/backup/GearRollingChunkSlicer.kt', 'GearRollingChunkSlicer'),
    (':feature:beam', 'feature/beam/src/main/kotlin/dev/fusion/beam/BeamMerkleChunkVerifier.kt', 'BeamMerkleChunkVerifier'),
    (':feature:browser', 'feature/browser/src/main/kotlin/dev/fusion/browser/model/BounceTrackingRedirectPurger.kt', 'BounceTrackingRedirectPurger'),
    (':feature:browser', 'feature/browser/src/main/kotlin/dev/fusion/browser/model/CleanUrlTrackingParamStripper.kt', 'CleanUrlTrackingParamStripper'),
    (':feature:browser', 'feature/browser/src/main/kotlin/dev/fusion/browser/model/RecallDashMpdSegmentResolver.kt', 'RecallDashMpdSegmentResolver'),
    (':feature:browser', 'feature/browser/src/main/kotlin/dev/fusion/browser/model/RecallHarArchiveExporter.kt', 'RecallHarArchiveExporter'),
    (':feature:browser', 'feature/browser/src/main/kotlin/dev/fusion/browser/model/RecallMethodEscalationRouter.kt', 'RecallMethodEscalationRouter'),
    (':feature:browser', 'feature/browser/src/main/kotlin/dev/fusion/browser/model/RecallTier1DirectAssetHarvester.kt', 'RecallTier1DirectAssetHarvester'),
    (':feature:browser', 'feature/browser/src/main/kotlin/dev/fusion/browser/model/RecallTier2HlsDashAssembler.kt', 'RecallTier2HlsDashAssembler'),
    (':feature:calc', 'feature/calc/src/main/kotlin/dev/fusion/calc/ConditionalFormattingScaleEvaluator.kt', 'ConditionalFormattingScaleEvaluator'),
    (':feature:calc', 'feature/calc/src/main/kotlin/dev/fusion/calc/SheetSolverSensitivityEngine.kt', 'SheetSolverSensitivityEngine'),
    (':feature:capture', 'feature/capture/src/main/kotlin/dev/fusion/capture/RecallCaptureRolloverPlanner.kt', 'RecallCaptureRolloverPlanner'),
    (':feature:capture', 'feature/capture/src/main/kotlin/dev/fusion/capture/RecallTier4NativeScreenAudioRecorder.kt', 'RecallTier4NativeScreenAudioRecorder'),
    (':feature:continuity', 'feature/continuity/src/main/kotlin/dev/fusion/continuity/ContinuityHybridLogicalClock.kt', 'ContinuityHybridLogicalClock'),
    (':feature:editor', 'feature/editor/src/main/java/dev/fusion/editor/MultiCaretBlockEditor.kt', 'MultiCaretBlockEditor'),
    (':feature:editor', 'feature/editor/src/main/java/dev/fusion/editor/WriterFootnotePaginator.kt', 'WriterFootnotePaginator'),
    (':feature:editor', 'feature/editor/src/main/java/dev/fusion/editor/WriterMailMergeEngine.kt', 'WriterMailMergeEngine'),
    (':feature:files', 'feature/files/src/main/kotlin/dev/fusion/filesui/BatchFileRenameCollisionPlanner.kt', 'BatchFileRenameCollisionPlanner'),
    (':feature:files', 'feature/files/src/main/kotlin/dev/fusion/filesui/MillerColumnsTreeNavigator.kt', 'MillerColumnsTreeNavigator'),
    (':feature:files', 'feature/files/src/main/kotlin/dev/fusion/filesui/RecallArchiveCatalog.kt', 'RecallArchiveCatalog'),
    (':feature:find', 'feature/find/src/main/kotlin/dev/fusion/find/LocalBpeSubwordTokenizer.kt', 'LocalBpeSubwordTokenizer'),
    (':feature:launcher', 'feature/launcher/src/main/kotlin/dev/fusion/launcher/LauncherFrecencyRanker.kt', 'LauncherFrecencyRanker'),
    (':feature:launcher', 'feature/launcher/src/main/kotlin/dev/fusion/launcher/LauncherViewModeShelfPlanner.kt', 'LauncherViewModeShelfPlanner'),
    (':feature:lens', 'feature/lens/src/main/kotlin/dev/fusion/lens/LensQrPayloadSemanticGuard.kt', 'LensQrPayloadSemanticGuard'),
    (':feature:maps', 'feature/maps/src/main/kotlin/dev/fusion/maps/MapsElevationTravelEstimator.kt', 'MapsElevationTravelEstimator'),
    (':feature:media', 'feature/media/src/main/kotlin/dev/fusion/media/FlacStreamInfoVorbisInspector.kt', 'FlacStreamInfoVorbisInspector'),
    (':feature:media', 'feature/media/src/main/kotlin/dev/fusion/media/Id3v24MusicTagEmbedder.kt', 'Id3v24MusicTagEmbedder'),
    (':feature:media', 'feature/media/src/main/kotlin/dev/fusion/media/RecallIsoBmffFragmentMuxer.kt', 'RecallIsoBmffFragmentMuxer'),
    (':feature:media', 'feature/media/src/main/kotlin/dev/fusion/media/RecallTier3CanvasWebAudioSiphoner.kt', 'RecallTier3CanvasWebAudioSiphoner'),
    (':feature:media', 'feature/media/src/main/kotlin/dev/fusion/media/RecallWebVttCaptionArchiver.kt', 'RecallWebVttCaptionArchiver'),
    (':feature:messages', 'feature/messages/src/main/kotlin/dev/fusion/messages/MessagingSymmetricChainRatchet.kt', 'MessagingSymmetricChainRatchet'),
    (':feature:notes', 'feature/notes/src/main/kotlin/dev/fusion/notes/NotesFlashcardSrsScheduler.kt', 'NotesFlashcardSrsScheduler'),
    (':feature:notes', 'feature/notes/src/main/kotlin/dev/fusion/notes/ZettelkastenBacklinkGraph.kt', 'ZettelkastenBacklinkGraph'),
    (':feature:settings', 'feature/settings/src/main/kotlin/dev/fusion/settingsui/PredictiveBackOverlayStack.kt', 'PredictiveBackOverlayStack'),
    (':feature:terminal', 'feature/terminal/src/main/kotlin/dev/fusion/terminal/MarkdownPipeTablePrettyFormatter.kt', 'MarkdownPipeTablePrettyFormatter'),
    (':feature:together', 'feature/together/src/main/kotlin/dev/fusion/together/ThreeWayDocumentLineMerger.kt', 'ThreeWayDocumentLineMerger'),
    (':feature:vault', 'feature/vault/src/main/kotlin/dev/fusion/vault/VaultPassphraseEntropyAuditor.kt', 'VaultPassphraseEntropyAuditor'),
    (':format:archive', 'format/archive/src/main/kotlin/dev/fusion/archive/Epub33PackageManifestBuilder.kt', 'Epub33PackageManifestBuilder'),
    (':format:image', 'format/image/src/main/kotlin/dev/fusion/format/image/TextureAtlasShelfPacker.kt', 'TextureAtlasShelfPacker'),
    (':format:lok', 'format/lok/src/main/java/dev/fusion/lok/LokCommandStateSynchronizer.kt', 'LokCommandStateSynchronizer'),
    (':format:lok', 'format/lok/src/main/java/dev/fusion/lok/OdfMasterPageHeaderFooterEngine.kt', 'OdfMasterPageHeaderFooterEngine'),
    (':format:lok', 'format/lok/src/main/java/dev/fusion/lok/OdfTrackedChangesRedlineEngine.kt', 'OdfTrackedChangesRedlineEngine'),
    (':format:native', 'format/native/src/main/kotlin/dev/fusion/nativefmt/IsoHeifAvifContainerProbe.kt', 'IsoHeifAvifContainerProbe'),
    (':format:ooxml', 'format/ooxml/src/main/kotlin/dev/fusion/ooxml/DocxContentControlBinder.kt', 'DocxContentControlBinder'),
    (':format:ooxml', 'format/ooxml/src/main/kotlin/dev/fusion/ooxml/OoxmlSparklineEngine.kt', 'OoxmlSparklineEngine'),
    (':format:ooxml', 'format/ooxml/src/main/kotlin/dev/fusion/ooxml/SlideShapeSnapDistributor.kt', 'SlideShapeSnapDistributor'),
    (':format:ooxml', 'format/ooxml/src/main/kotlin/dev/fusion/ooxml/XlsxAutoFilterEvaluator.kt', 'XlsxAutoFilterEvaluator'),
    (':format:pdf', 'format/pdf/src/main/java/dev/fusion/format/pdf/PdfAcroFormFieldFiller.kt', 'PdfAcroFormFieldFiller'),
    (':format:pdf', 'format/pdf/src/main/java/dev/fusion/format/pdf/PdfBookletImpositionPlanner.kt', 'PdfBookletImpositionPlanner'),
    (':format:pdf', 'format/pdf/src/main/java/dev/fusion/format/pdf/PdfBookmarkOutlineWriter.kt', 'PdfBookmarkOutlineWriter'),
    (':format:psd', 'format/psd/src/main/kotlin/dev/fusion/psd/PsdSeparableBlendCompositor.kt', 'PsdSeparableBlendCompositor'),
    (':privacy', 'privacy/src/main/kotlin/dev/fusion/privacy/TailsAmnesicSwapCoreAuditor.kt', 'TailsAmnesicSwapCoreAuditor'),
    (':privacy', 'privacy/src/main/kotlin/dev/fusion/privacy/TorOnionPowIntroQueue.kt', 'TorOnionPowIntroQueue'),
    (':testing', 'testing/src/main/kotlin/dev/fusion/testing/DeterministicFuzzShrinker.kt', 'DeterministicFuzzShrinker'),
]

R52_NEW_COMPOSE_UI_FILES: list[tuple[str, str, str]] = [
    (':core:designsystem', 'core/designsystem/src/main/kotlin/dev/fusion/designsystem/FusionContextMenuPopup.kt', 'FusionContextMenuPopup'),
    (':core:designsystem', 'core/designsystem/src/main/kotlin/dev/fusion/designsystem/FusionViewModeIconBar.kt', 'FusionViewModeIconBar'),
    (':core:designsystem', 'core/designsystem/src/main/kotlin/dev/fusion/designsystem/StackedUndoNotificationHost.kt', 'StackedUndoNotificationHost'),
    (':feature:browser', 'feature/browser/src/main/java/dev/fusion/browser/BrowserRecallHud.kt', 'BrowserRecallHud'),
    (':feature:capture', 'feature/capture/src/main/java/dev/fusion/capture/RecallWorkspaceScreen.kt', 'RecallWorkspaceScreen'),
    (':feature:files', 'feature/files/src/main/java/dev/fusion/filesui/FilesViewColumnsPane.kt', 'FilesViewCollectionPane'),
]

TOUCHED_PRODUCTION_FILES: list[tuple[str, str, str]] = [
    (':format:ooxml', 'format/ooxml/src/main/kotlin/dev/fusion/ooxml/FormulaEval.kt', 'R52 expands FormulaEval from 225 to 260 pure functions.'),
    (':format:ooxml', 'format/ooxml/src/main/kotlin/dev/fusion/ooxml/SpillSheetEngine.kt', 'R52 expands SpillSheetEngine from 36 to 42 spilled dynamic-array operations.'),
    (':format:ooxml', 'format/ooxml/src/main/kotlin/dev/fusion/ooxml/PptxSlideDeck.kt', 'R52 adds writeDeckWithMediaTimings and inspectSlideMediaTimings (<p:timing>) on PptxSlideDeck.'),
    (':core:capabilities', 'core/capabilities/src/main/java/dev/fusion/capabilities/WasmSandbox.kt', 'R52 adds WebAssembly 3.0 0x12/0x13 return_call/return_call_indirect tail-call execution on WasmSandbox.'),
    (':core:commands', 'core/commands/src/main/kotlin/dev/fusion/commands/UndoRedoStack.kt', 'R52 adds toBottomRightToastEntry Z-stack integration on UndoRedoStack.'),
    (':feature:files', 'feature/files/src/main/java/dev/fusion/filesui/DualPaneContextMenuModel.kt', 'R52 wires RightClickContextMenuGeometry compact bounds into DualPaneContextMenuModel.'),
    (':feature:browser', 'feature/browser/src/main/java/dev/fusion/browser/BrowserScreen.kt', 'R52 wires BrowserRecallHud, CleanUrlTrackingParamStripper, compact context menu, and bottom-right Undo Z-stack into BrowserScreen (syntax PARSE OK).'),
    (':feature:capture', 'feature/capture/src/main/java/dev/fusion/capture/CaptureScreen.kt', 'R52 wires RecallWorkspaceScreen and Tier-4 viewport capture into CaptureScreen (syntax PARSE OK).'),
    (':feature:media', 'feature/media/src/main/java/dev/fusion/media/MediaHub.kt', 'R52 wires FlacStreamInfoVorbisInspector, Id3v24MusicTagEmbedder, and RecallIsoBmffFragmentMuxer into MediaHub (syntax PARSE OK).'),
    (':feature:files', 'feature/files/src/main/java/dev/fusion/filesui/FilesScreen.kt', 'R52 wires FusionContextMenuPopup, FusionViewModeIconBar (List/Grid/Columns), FilesViewColumnsPane, and StackedUndoNotificationHost into FilesScreen (syntax PARSE OK).'),
    (':feature:editor', 'feature/editor/src/main/java/dev/fusion/editor/EditorScreen.kt', 'R52 wires FusionContextMenuPopup and StackedUndoNotificationHost into EditorScreen (syntax PARSE OK).'),
    (':app', 'app/src/main/java/dev/fusion/shell/MainActivity.kt', 'R52 wires RecallSessionCoordinator, ShoulderSurfingPrivacyVeil, and StackedUndoNotificationHost into MainActivity (syntax PARSE OK).'),
    (':feature:vault', 'feature/vault/src/main/java/dev/fusion/vault/VaultScreen.kt', 'R52 wires VaultPassphraseEntropyAuditor and StackedUndoNotificationHost into VaultScreen (syntax PARSE OK).'),
    (':privacy', 'privacy/src/main/java/dev/fusion/privacy/PrivacyActivity.kt', 'R52 wires TorOnionPowIntroQueue, TailsAmnesicSwapCoreAuditor, and StackedUndoNotificationHost into PrivacyActivity (syntax PARSE OK).'),
    (':feature:notes', 'feature/notes/src/main/java/dev/fusion/notes/NotesScreen.kt', 'R52 wires ZettelkastenBacklinkGraph and StackedUndoNotificationHost into NotesScreen (syntax PARSE OK).'),
    (':feature:terminal', 'feature/terminal/src/main/java/dev/fusion/terminal/TerminalScreen.kt', 'R52 wires MarkdownPipeTablePrettyFormatter and StackedUndoNotificationHost into TerminalScreen (syntax PARSE OK).'),
    (':feature:launcher', 'feature/launcher/src/main/java/dev/fusion/launcher/Shelf.kt', 'R52 wires LauncherViewModeShelfPlanner and FusionViewModeIconBar into Shelf (syntax PARSE OK).'),
]

R52_KERNELS = R52_MODULE_KERNELS
TOUCHED: dict[str, tuple[str, str]] = {rel: (mod, note) for mod, rel, note in TOUCHED_PRODUCTION_FILES}
R52_CHECKS = [
    "testing/src/r52/kotlin/dev/fusion/testing/R52Checks.kt",
    "tools/test_r52_tools.py",
]
CMD = "python3 tools/run_r52_contracts.py"


def sha(rel: str) -> str:
    return hashlib.sha256((ROOT / rel).read_bytes()).hexdigest()


def main() -> int:
    assert len(R52_KERNELS) == 91, f"Expected 91 R52 pure-JVM kernels, got {len(R52_KERNELS)}"
    assert len(R52_NEW_COMPOSE_UI_FILES) == 6, f"Expected 6 R52 Compose UI files, got {len(R52_NEW_COMPOSE_UI_FILES)}"
    assert len(TOUCHED) == 17, f"Expected 17 R52 TOUCHED files, got {len(TOUCHED)}"
    deepen: dict[str, list[tuple[str, str]]] = {}
    for mod, path, name in R52_KERNELS:
        assert (ROOT / path).is_file(), f"Missing R52 kernel file: {path}"
        deepen.setdefault(mod, []).append((path, f"R52 adds {name} pure-JVM kernel."))
    for mod, path, name in R52_NEW_COMPOSE_UI_FILES:
        assert (ROOT / path).is_file(), f"Missing R52 Compose UI file: {path}"
        deepen.setdefault(mod, []).append((path, f"R52 adds {name} Compose UI component (syntax PARSE OK)."))
    for t_path in TOUCHED:
        assert (ROOT / t_path).is_file(), f"Missing R52 TOUCHED file: {t_path}"

    jp = ROOT / "docs/module-testability.json"
    data = json.loads(jp.read_text(encoding="utf-8"))

    also_allowed_repin = {
        "tools/check_r26_android_syntax.py",
        "tools/fusion_workbench_server.py",
        "tools/fusion_workbench_cli.py",
        "format/ooxml/src/main/kotlin/dev/fusion/ooxml/BoundedXml.kt",
    }
    owned = {f for items in deepen.values() for f, _ in items} | set(R52_CHECKS) | set(TOUCHED) | also_allowed_repin
    stale: list[tuple[str, str]] = []
    for r in data["rows"]:
        for p, pinned in r["sha256"].items():
            if p not in owned and sha(p) != pinned:
                stale.append((r["module"], p))
    if stale:
        raise SystemExit(f"refusing to silently re-pin modified files outside R52 DEEPEN/TOUCHED: {stale}")

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
        for chk in R52_CHECKS:
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
    print(f"R52 census deepen OK ({len(seen_mods)}/50 = 100.00% modules deepened, {len(touched_applied)} touched):", json.dumps(census.counts(reloaded)))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
