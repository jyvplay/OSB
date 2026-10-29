#!/usr/bin/env python3
"""Idempotently deepen ALL 50/50 module rows in docs/module-testability.json with 85 R51 kernels + 16 TOUCHED files.

Never modifies docs/module-audit.json (full-spec Class A acceptance remains strictly 0/50 = 0.00%).
Preserves exact R47, R48, R49, and R50 wiring (`run_r49_contracts.py` and `run_r50_contracts.py` stay in `command`)
while wiring `run_r51_contracts.py` across all 50/50 modules.
"""
from __future__ import annotations

import hashlib
import json
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "tools"))
import module_testability as census

R51_MODULE_KERNELS: list[tuple[str, str, str]] = [
    (':app', 'app/src/main/java/dev/fusion/shell/FusionClassAReadinessAggregator.kt', 'FusionClassAReadinessAggregator'),
    (':app', 'app/src/main/java/dev/fusion/shell/WorkspaceDeepLinkIntentSanitizer.kt', 'WorkspaceDeepLinkIntentSanitizer'),
    (':baselineprofile', 'baselineprofile/src/main/kotlin/dev/fusion/baselineprofile/ArtStartupMethodRuleOptimizer.kt', 'ArtStartupMethodRuleOptimizer'),
    (':benchmark', 'benchmark/src/main/java/dev/fusion/benchmark/SimdSpreadsheetThroughputHarness.kt', 'SimdSpreadsheetThroughputHarness'),
    (':cast:airplay', 'cast/airplay/src/main/kotlin/dev/fusion/airplay/AirPlayFairPlayStubRefusalGate.kt', 'AirPlayFairPlayStubRefusalGate'),
    (':cast:airplay', 'cast/airplay/src/main/kotlin/dev/fusion/airplay/RaopAlacAudioPacketizer.kt', 'RaopAlacAudioPacketizer'),
    (':cast:castv2', 'cast/castv2/src/main/kotlin/dev/fusion/castv2/CastV2MediaQueueController.kt', 'CastV2MediaQueueController'),
    (':cast:castv2', 'cast/castv2/src/main/kotlin/dev/fusion/castv2/CastVirtualDisplayBoundsScaler.kt', 'CastVirtualDisplayBoundsScaler'),
    (':cast:dlna', 'cast/dlna/src/main/kotlin/dev/fusion/cast/dlna/DidlLiteXmlMetadataBuilder.kt', 'DidlLiteXmlMetadataBuilder'),
    (':core:capabilities', 'core/capabilities/src/main/java/dev/fusion/capabilities/WasmSimdModuleVerifier.kt', 'WasmSimdModuleVerifier'),
    (':core:capabilities', 'core/capabilities/src/main/java/dev/fusion/capabilities/WitComponentCanonicalAbiCodec.kt', 'WitComponentCanonicalAbiCodec'),
    (':core:commands', 'core/commands/src/main/kotlin/dev/fusion/commands/FuzzyChordCommandPaletteEngine.kt', 'FuzzyChordCommandPaletteEngine'),
    (':core:designsystem', 'core/designsystem/src/main/kotlin/dev/fusion/designsystem/ApcaContrastTypographyScale.kt', 'ApcaContrastTypographyScale'),
    (':core:documents', 'core/documents/src/main/kotlin/dev/fusion/documents/OpenTypeKerningLineJustifier.kt', 'OpenTypeKerningLineJustifier'),
    (':core:documents', 'core/documents/src/main/kotlin/dev/fusion/documents/SpreadsheetNumberMaskFormatter.kt', 'SpreadsheetNumberMaskFormatter'),
    (':core:files', 'core/files/src/main/kotlin/dev/fusion/files/AtomicSafeWriteJournal.kt', 'AtomicSafeWriteJournal'),
    (':core:files', 'core/files/src/main/kotlin/dev/fusion/files/ExtendedMimeMagicSniffer.kt', 'ExtendedMimeMagicSniffer'),
    (':core:hw', 'core/hw/src/main/kotlin/dev/fusion/hw/CapacitiveGhostTouchFilter.kt', 'CapacitiveGhostTouchFilter'),
    (':core:hw', 'core/hw/src/main/kotlin/dev/fusion/hw/HallMagneticCoverTamperGuard.kt', 'HallMagneticCoverTamperGuard'),
    (':core:hw', 'core/hw/src/main/kotlin/dev/fusion/hw/Usb4PcieIommuDmaGuard.kt', 'Usb4PcieIommuDmaGuard'),
    (':core:index', 'core/index/src/main/kotlin/dev/fusion/index/Bm25FtsPositionalIndex.kt', 'Bm25FtsPositionalIndex'),
    (':core:index', 'core/index/src/main/kotlin/dev/fusion/index/TrigramFuzzySpellSuggester.kt', 'TrigramFuzzySpellSuggester'),
    (':core:model', 'core/model/src/main/kotlin/dev/fusion/model/VectorClockCausalRevision.kt', 'VectorClockCausalRevision'),
    (':core:perf', 'core/perf/src/main/kotlin/dev/fusion/perf/MemoryPressureTrimBudgeter.kt', 'MemoryPressureTrimBudgeter'),
    (':core:perf', 'core/perf/src/main/kotlin/dev/fusion/perf/StartupColdWarmRegressionGate.kt', 'StartupColdWarmRegressionGate'),
    (':core:security', 'core/security/src/main/kotlin/dev/fusion/security/AgeV1StreamCipherEnvelope.kt', 'AgeV1StreamCipherEnvelope'),
    (':core:security', 'core/security/src/main/kotlin/dev/fusion/security/DramRowhammerZeroizeGuard.kt', 'DramRowhammerZeroizeGuard'),
    (':core:security', 'core/security/src/main/kotlin/dev/fusion/security/Luks2Argon2idHeaderAuditor.kt', 'Luks2Argon2idHeaderAuditor'),
    (':core:security', 'core/security/src/main/kotlin/dev/fusion/security/OpticalReflectionSideChannelGuard.kt', 'OpticalReflectionSideChannelGuard'),
    (':core:settings', 'core/settings/src/main/kotlin/dev/fusion/settings/TransactionalSettingsMigrationEngine.kt', 'TransactionalSettingsMigrationEngine'),
    (':core:storage', 'core/storage/src/main/kotlin/dev/fusion/storage/LsmWriteAheadCompactionSim.kt', 'LsmWriteAheadCompactionSim'),
    (':core:windowing', 'core/windowing/src/main/kotlin/dev/fusion/windowing/FocusZOrderModalStack.kt', 'FocusZOrderModalStack'),
    (':core:windowing', 'core/windowing/src/main/kotlin/dev/fusion/windowing/SnapTileMagneticLayoutSolver.kt', 'SnapTileMagneticLayoutSolver'),
    (':feature:accessibility', 'feature/accessibility/src/main/kotlin/dev/fusion/a11y/WcagTouchTargetSemanticAuditor.kt', 'WcagTouchTargetSemanticAuditor'),
    (':feature:alerts', 'feature/alerts/src/main/kotlin/dev/fusion/alerts/DndPriorityScheduleEvaluator.kt', 'DndPriorityScheduleEvaluator'),
    (':feature:automation', 'feature/automation/src/main/kotlin/dev/fusion/automation/CronDagWorkflowScheduler.kt', 'CronDagWorkflowScheduler'),
    (':feature:backup', 'feature/backup/src/main/kotlin/dev/fusion/backup/RestorableChunkDedupManifest.kt', 'RestorableChunkDedupManifest'),
    (':feature:beam', 'feature/beam/src/main/kotlin/dev/fusion/beam/ReedSolomonQrChunkFountain.kt', 'ReedSolomonQrChunkFountain'),
    (':feature:browser', 'feature/browser/src/main/kotlin/dev/fusion/browser/model/ChipsPartitionedCookieJar.kt', 'ChipsPartitionedCookieJar'),
    (':feature:browser', 'feature/browser/src/main/kotlin/dev/fusion/browser/model/CssFlexBlockBoxLayouter.kt', 'CssFlexBlockBoxLayouter'),
    (':feature:browser', 'feature/browser/src/main/kotlin/dev/fusion/browser/model/DeclarativeNetRequestTrieEngine.kt', 'DeclarativeNetRequestTrieEngine'),
    (':feature:browser', 'feature/browser/src/main/kotlin/dev/fusion/browser/model/PermissionsPolicyHeaderEvaluator.kt', 'PermissionsPolicyHeaderEvaluator'),
    (':feature:calc', 'feature/calc/src/main/kotlin/dev/fusion/calc/GoalSeekSimplexSolver.kt', 'GoalSeekSimplexSolver'),
    (':feature:calc', 'feature/calc/src/main/kotlin/dev/fusion/calc/PivotSlicerCrossFilterEngine.kt', 'PivotSlicerCrossFilterEngine'),
    (':feature:capture', 'feature/capture/src/main/kotlin/dev/fusion/capture/ScrollingStitchSeamMatcher.kt', 'ScrollingStitchSeamMatcher'),
    (':feature:continuity', 'feature/continuity/src/main/kotlin/dev/fusion/continuity/AudioOutputHandoffCoordinator.kt', 'AudioOutputHandoffCoordinator'),
    (':feature:continuity', 'feature/continuity/src/main/kotlin/dev/fusion/continuity/CrossDeviceClipboardSyncEngine.kt', 'CrossDeviceClipboardSyncEngine'),
    (':feature:editor', 'feature/editor/src/main/java/dev/fusion/editor/CitationBibliographyFormatter.kt', 'CitationBibliographyFormatter'),
    (':feature:editor', 'feature/editor/src/main/java/dev/fusion/editor/WidowOrphanGalleyPaginator.kt', 'WidowOrphanGalleyPaginator'),
    (':feature:files', 'feature/files/src/main/kotlin/dev/fusion/filesui/BatchRenamePatternPreviewEngine.kt', 'BatchRenamePatternPreviewEngine'),
    (':feature:find', 'feature/find/src/main/kotlin/dev/fusion/find/BooleanQueryAstEvaluator.kt', 'BooleanQueryAstEvaluator'),
    (':feature:launcher', 'feature/launcher/src/main/kotlin/dev/fusion/launcher/PinnedWorkspaceDockLayoutEngine.kt', 'PinnedWorkspaceDockLayoutEngine'),
    (':feature:lens', 'feature/lens/src/main/kotlin/dev/fusion/lens/BarcodeSymbologyDecoder.kt', 'BarcodeSymbologyDecoder'),
    (':feature:lens', 'feature/lens/src/main/kotlin/dev/fusion/lens/DocumentDewarpPerspectiveEngine.kt', 'DocumentDewarpPerspectiveEngine'),
    (':feature:maps', 'feature/maps/src/main/kotlin/dev/fusion/maps/RTreeSpatialGeofenceIndex.kt', 'RTreeSpatialGeofenceIndex'),
    (':feature:media', 'feature/media/src/main/kotlin/dev/fusion/media/EbuR128LoudnessNormalizer.kt', 'EbuR128LoudnessNormalizer'),
    (':feature:media', 'feature/media/src/main/kotlin/dev/fusion/media/WebVttCueTimelineEngine.kt', 'WebVttCueTimelineEngine'),
    (':feature:messages', 'feature/messages/src/main/kotlin/dev/fusion/messages/DoubleRatchetSessionEngine.kt', 'DoubleRatchetSessionEngine'),
    (':feature:messages', 'feature/messages/src/main/kotlin/dev/fusion/messages/SealedSenderPaddingEnvelope.kt', 'SealedSenderPaddingEnvelope'),
    (':feature:notes', 'feature/notes/src/main/kotlin/dev/fusion/notes/MarkdownTableFormatterEngine.kt', 'MarkdownTableFormatterEngine'),
    (':feature:notes', 'feature/notes/src/main/kotlin/dev/fusion/notes/SpacedRepetitionFsrsScheduler.kt', 'SpacedRepetitionFsrsScheduler'),
    (':feature:settings', 'feature/settings/src/main/kotlin/dev/fusion/settingsui/PrivacyPostureScorecardCalculator.kt', 'PrivacyPostureScorecardCalculator'),
    (':feature:terminal', 'feature/terminal/src/main/kotlin/dev/fusion/terminal/ShellPipelineTokenizer.kt', 'ShellPipelineTokenizer'),
    (':feature:terminal', 'feature/terminal/src/main/kotlin/dev/fusion/terminal/Vt100AlternateScreenScrollback.kt', 'Vt100AlternateScreenScrollback'),
    (':feature:together', 'feature/together/src/main/kotlin/dev/fusion/together/YjsRgaTextCrdtEngine.kt', 'YjsRgaTextCrdtEngine'),
    (':feature:vault', 'feature/vault/src/main/kotlin/dev/fusion/vault/DuressDecoyVaultRouter.kt', 'DuressDecoyVaultRouter'),
    (':feature:vault', 'feature/vault/src/main/kotlin/dev/fusion/vault/ShamirSecretVaultSplitter.kt', 'ShamirSecretVaultSplitter'),
    (':format:archive', 'format/archive/src/main/kotlin/dev/fusion/archive/ArchiveBombQuotaInspector.kt', 'ArchiveBombQuotaInspector'),
    (':format:archive', 'format/archive/src/main/kotlin/dev/fusion/archive/DeterministicReproducibleTarGzWriter.kt', 'DeterministicReproducibleTarGzWriter'),
    (':format:image', 'format/image/src/main/kotlin/dev/fusion/format/image/ExifGpsPrivacyScrubber.kt', 'ExifGpsPrivacyScrubber'),
    (':format:image', 'format/image/src/main/kotlin/dev/fusion/format/image/OklabPerceptualPaletteQuantizer.kt', 'OklabPerceptualPaletteQuantizer'),
    (':format:lok', 'format/lok/src/main/java/dev/fusion/lok/OdsSpreadsheetFormulaBridge.kt', 'OdsSpreadsheetFormulaBridge'),
    (':format:native', 'format/native/src/main/kotlin/dev/fusion/nativefmt/ColumnarArrowIpcBatchCodec.kt', 'ColumnarArrowIpcBatchCodec'),
    (':format:ooxml', 'format/ooxml/src/main/kotlin/dev/fusion/ooxml/CalcChainDependencyGraph.kt', 'CalcChainDependencyGraph'),
    (':format:ooxml', 'format/ooxml/src/main/kotlin/dev/fusion/ooxml/PptxMasterLayoutAnimationEngine.kt', 'PptxMasterLayoutAnimationEngine'),
    (':format:ooxml', 'format/ooxml/src/main/kotlin/dev/fusion/ooxml/R1C1FormulaTranslator.kt', 'R1C1FormulaTranslator'),
    (':format:ooxml', 'format/ooxml/src/main/kotlin/dev/fusion/ooxml/StructuredTableRefEngine.kt', 'StructuredTableRefEngine'),
    (':format:pdf', 'format/pdf/src/main/java/dev/fusion/format/pdf/PdfLinearizedHintValidator.kt', 'PdfLinearizedHintValidator'),
    (':format:pdf', 'format/pdf/src/main/java/dev/fusion/format/pdf/PdfUa1TaggedStructureWriter.kt', 'PdfUa1TaggedStructureWriter'),
    (':format:psd', 'format/psd/src/main/kotlin/dev/fusion/psd/PsdSmartPlacedLayerInspector.kt', 'PsdSmartPlacedLayerInspector'),
    (':privacy', 'privacy/src/main/kotlin/dev/fusion/privacy/AmnesicSessionShutdownOrchestrator.kt', 'AmnesicSessionShutdownOrchestrator'),
    (':privacy', 'privacy/src/main/kotlin/dev/fusion/privacy/MetadataCleanRoomExportPipeline.kt', 'MetadataCleanRoomExportPipeline'),
    (':privacy', 'privacy/src/main/kotlin/dev/fusion/privacy/PanicDeadManSwitchEngine.kt', 'PanicDeadManSwitchEngine'),
    (':privacy', 'privacy/src/main/kotlin/dev/fusion/privacy/TorVanguardsLiteMesh.kt', 'TorVanguardsLiteMesh'),
    (':testing', 'testing/src/main/kotlin/dev/fusion/testing/DeterministicPropertyShrinker.kt', 'DeterministicPropertyShrinker'),
]

TOUCHED_PRODUCTION_FILES: list[tuple[str, str, str]] = [
    (':format:ooxml', 'format/ooxml/src/main/kotlin/dev/fusion/ooxml/FormulaEval.kt', 'R51 expands FormulaEval from 168 to 215 functions.'),
    (':format:ooxml', 'format/ooxml/src/main/kotlin/dev/fusion/ooxml/SpillSheetEngine.kt', 'R51 expands SpillSheetEngine from 29 to 36 spilled dynamic-array operations.'),
    (':format:ooxml', 'format/ooxml/src/main/kotlin/dev/fusion/ooxml/DocxRichDocument.kt', 'R51 adds writeMultiSection and inspectSectionsAndBookmarks on DocxRichDocument.'),
    (':format:lok', 'format/lok/src/main/java/dev/fusion/lok/OdtRichWriter.kt', 'R51 adds writeScholarlyOdt (TOC + tracked changes + bibliography) on OdtRichWriter.'),
    (':core:capabilities', 'core/capabilities/src/main/java/dev/fusion/capabilities/WasmSandbox.kt', 'R51 adds WebAssembly 2.0 0x7B/0xFD v128 SIMD and invokeV128 on WasmSandbox.'),
    (':feature:editor', 'feature/editor/src/main/java/dev/fusion/editor/EditorTools.kt', 'R51 wires WidowOrphanGalleyPaginator and CitationBibliographyFormatter into EditorTools (syntax PARSE OK).'),
    (':feature:calc', 'feature/calc/src/main/java/dev/fusion/calc/CalculatorViewModel.kt', 'R51 wires GoalSeekSimplexSolver Brent/Simplex preview hooks into CalculatorViewModel (syntax PARSE OK).'),
    (':app', 'app/src/main/java/dev/fusion/shell/ExternalDesktopScreen.kt', 'R51 wires SnapTileMagneticLayoutSolver and FusionClassAReadinessAggregator into ExternalDesktopScreen (syntax PARSE OK).'),
    (':feature:settings', 'feature/settings/src/main/java/dev/fusion/settingsui/AppearanceControls.kt', 'R51 wires PrivacyPostureScorecardCalculator 20-vector posture summary into AppearanceControls (syntax PARSE OK).'),
    (':feature:media', 'feature/media/src/main/java/dev/fusion/media/SubtitlePicker.kt', 'R51 wires WebVttCueTimelineEngine lane preview into SubtitlePicker (syntax PARSE OK).'),
    (':feature:launcher', 'feature/launcher/src/main/java/dev/fusion/launcher/Shelf.kt', 'R51 wires PinnedWorkspaceDockLayoutEngine 48dp slot-overflow planner into Shelf (syntax PARSE OK).'),
    (':core:hw', 'core/hw/src/main/java/dev/fusion/hw/HwProbe.kt', 'R51 wires Usb4PcieIommuDmaGuard Thunderclap pre-unlock DMA policy into HwProbe (syntax PARSE OK).'),
    (':core:capabilities', 'core/capabilities/src/main/java/dev/fusion/capabilities/CapabilityProbe.kt', 'R51 wires WasmSimdModuleVerifier 0xFD v128 audit hook into CapabilityProbe (syntax PARSE OK).'),
    (':core:designsystem', 'core/designsystem/src/main/java/dev/fusion/designsystem/Theme.kt', 'R51 wires ApcaContrastTypographyScale WCAG 2.2 + Lc palette check into Theme (syntax PARSE OK).'),
    (':feature:continuity', 'feature/continuity/src/main/java/dev/fusion/continuity/Peripherals.kt', 'R51 wires AudioOutputHandoffCoordinator unplug pause planner into Peripherals (syntax PARSE OK).'),
    (':privacy', 'privacy/src/main/java/dev/fusion/privacy/ExposureActivity.kt', 'R51 wires TorVanguardsLiteMesh and PanicDeadManSwitchEngine into ExposureActivity (syntax PARSE OK).'),
]

R51_KERNELS = R51_MODULE_KERNELS
TOUCHED: dict[str, tuple[str, str]] = {rel: (mod, note) for mod, rel, note in TOUCHED_PRODUCTION_FILES}
R51_CHECKS = [
    "testing/src/r51/kotlin/dev/fusion/testing/R51Checks.kt",
    "tools/test_r51_tools.py",
]
CMD = "python3 tools/run_r51_contracts.py"


def sha(rel: str) -> str:
    return hashlib.sha256((ROOT / rel).read_bytes()).hexdigest()


def main() -> int:
    assert len(R51_KERNELS) == 85, f"Expected 85 R51 kernels, got {len(R51_KERNELS)}"
    assert len(TOUCHED) == 16, f"Expected 16 R51 TOUCHED files, got {len(TOUCHED)}"
    deepen: dict[str, list[tuple[str, str]]] = {}
    for mod, path, name in R51_KERNELS:
        assert (ROOT / path).is_file(), f"Missing R51 kernel file: {path}"
        deepen.setdefault(mod, []).append((path, f"R51 adds {name} pure-JVM kernel."))
    for t_path in TOUCHED:
        assert (ROOT / t_path).is_file(), f"Missing R51 TOUCHED file: {t_path}"

    jp = ROOT / "docs/module-testability.json"
    data = json.loads(jp.read_text(encoding="utf-8"))

    also_allowed_repin = {
        "tools/check_r26_android_syntax.py",
        "tools/fusion_workbench_server.py",
        "tools/fusion_workbench_cli.py",
        "format/ooxml/src/main/kotlin/dev/fusion/ooxml/BoundedXml.kt",
    }
    owned = {f for items in deepen.values() for f, _ in items} | set(R51_CHECKS) | set(TOUCHED) | also_allowed_repin
    stale: list[tuple[str, str]] = []
    for r in data["rows"]:
        for p, pinned in r["sha256"].items():
            if p not in owned and sha(p) != pinned:
                stale.append((r["module"], p))
    if stale:
        raise SystemExit(f"refusing to silently re-pin modified files outside R51 DEEPEN/TOUCHED: {stale}")

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
        for chk in R51_CHECKS:
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
    print(f"R51 census deepen OK ({len(seen_mods)}/50 = 100.00% modules deepened, {len(touched_applied)} touched):", json.dumps(census.counts(reloaded)))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
