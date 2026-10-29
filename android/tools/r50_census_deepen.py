#!/usr/bin/env python3
"""Idempotently deepen ALL 50/50 module rows in docs/module-testability.json with 80 R50 kernels + 14 TOUCHED files.

Never modifies docs/module-audit.json (full-spec Class A acceptance remains strictly 0/50 = 0.00%).
Preserves exact R47, R48, and R49 wiring (`run_r49_contracts.py` stays in `command`) while wiring
`run_r50_contracts.py` across all 50/50 modules.
"""
from __future__ import annotations

import hashlib
import json
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "tools"))
import module_testability as census

R50_KERNELS: list[tuple[str, str, str]] = [
    (':app', 'app/src/main/java/dev/fusion/shell/AppInventoryLifecycleAdvisor.kt', 'AppInventoryLifecycleAdvisor'),
    (':app', 'app/src/main/java/dev/fusion/shell/WorkspaceContextProfileEngine.kt', 'WorkspaceContextProfileEngine'),
    (':baselineprofile', 'baselineprofile/src/main/kotlin/dev/fusion/baselineprofile/ArtStartupProfileRuleSynthesizer.kt', 'ArtStartupProfileRuleSynthesizer'),
    (':benchmark', 'benchmark/src/main/java/dev/fusion/benchmark/R50LatencyPercentileBudgetGate.kt', 'R50LatencyPercentileBudgetGate'),
    (':cast:airplay', 'cast/airplay/src/main/kotlin/dev/fusion/airplay/CastSinkSrpPinVerifier.kt', 'CastSinkSrpPinVerifier'),
    (':cast:castv2', 'cast/castv2/src/main/kotlin/dev/fusion/castv2/PresentationSpeakerNotesDualView.kt', 'PresentationSpeakerNotesDualView'),
    (':cast:dlna', 'cast/dlna/src/main/java/dev/fusion/cast/dlna/DlnaDidlLitePlaylistQueue.kt', 'DlnaDidlLitePlaylistQueue'),
    (':core:capabilities', 'core/capabilities/src/main/java/dev/fusion/capabilities/WebAppWindowManifestHost.kt', 'WebAppWindowManifestHost'),
    (':core:commands', 'core/commands/src/main/kotlin/dev/fusion/commands/ShortcutPresetConflictDetector.kt', 'ShortcutPresetConflictDetector'),
    (':core:designsystem', 'core/designsystem/src/main/java/dev/fusion/designsystem/OsdQuickPanelTileTokens.kt', 'OsdQuickPanelTileTokens'),
    (':core:documents', 'core/documents/src/main/java/dev/fusion/documents/WriterTrackChangesDiffMerger.kt', 'WriterTrackChangesDiffMerger'),
    (':core:documents', 'core/documents/src/main/kotlin/dev/fusion/documents/MasterSlideTransitionDeck.kt', 'MasterSlideTransitionDeck'),
    (':core:documents', 'core/documents/src/main/kotlin/dev/fusion/documents/OpenTypeKerningTableLayout.kt', 'OpenTypeKerningTableLayout'),
    (':core:files', 'core/files/src/main/java/dev/fusion/files/CrossWindowClipUriGrantor.kt', 'CrossWindowClipUriGrantor'),
    (':core:files', 'core/files/src/main/java/dev/fusion/files/RemoteMountReconnectPlanner.kt', 'RemoteMountReconnectPlanner'),
    (':core:hw', 'core/hw/src/main/java/dev/fusion/hw/GamepadPointerModeMapper.kt', 'GamepadPointerModeMapper'),
    (':core:hw', 'core/hw/src/main/java/dev/fusion/hw/HardwareKeyboardImeSwitcher.kt', 'HardwareKeyboardImeSwitcher'),
    (':core:hw', 'core/hw/src/main/java/dev/fusion/hw/TempestDisplayJitterFilter.kt', 'TempestDisplayJitterFilter'),
    (':core:index', 'core/index/src/main/kotlin/dev/fusion/index/OptInWebAppSearchFederation.kt', 'OptInWebAppSearchFederation'),
    (':core:model', 'core/model/src/main/kotlin/dev/fusion/model/ContactsVcard4PickerModel.kt', 'ContactsVcard4PickerModel'),
    (':core:perf', 'core/perf/src/main/java/dev/fusion/perf/MeteredBatteryLowRamScheduler.kt', 'MeteredBatteryLowRamScheduler'),
    (':core:security', 'core/security/src/main/java/dev/fusion/security/ImeIncognitoClipboardFirewall.kt', 'ImeIncognitoClipboardFirewall'),
    (':core:security', 'core/security/src/main/java/dev/fusion/security/NfcRelayDistanceBoundingGate.kt', 'NfcRelayDistanceBoundingGate'),
    (':core:security', 'core/security/src/main/java/dev/fusion/security/UsbBadHidKeystrokeRateGuard.kt', 'UsbBadHidKeystrokeRateGuard'),
    (':core:settings', 'core/settings/src/main/java/dev/fusion/settings/DefaultAppsRoleGuideEngine.kt', 'DefaultAppsRoleGuideEngine'),
    (':core:storage', 'core/storage/src/main/java/dev/fusion/storage/ImapEnvelopeTriageStore.kt', 'ImapEnvelopeTriageStore'),
    (':core:windowing', 'core/windowing/src/main/kotlin/dev/fusion/windowing/ExternalAppBrokerDensityPin.kt', 'ExternalAppBrokerDensityPin'),
    (':core:windowing', 'core/windowing/src/main/kotlin/dev/fusion/windowing/KeyToTouchOverlayMapper.kt', 'KeyToTouchOverlayMapper'),
    (':feature:accessibility', 'feature/accessibility/src/main/java/dev/fusion/a11y/ColorBlindnessDaltonizeMatrix.kt', 'ColorBlindnessDaltonizeMatrix'),
    (':feature:accessibility', 'feature/accessibility/src/main/java/dev/fusion/a11y/EasySimpleModeLayoutScaler.kt', 'EasySimpleModeLayoutScaler'),
    (':feature:accessibility', 'feature/accessibility/src/main/java/dev/fusion/a11y/FocusRingDwellClickController.kt', 'FocusRingDwellClickController'),
    (':feature:alerts', 'feature/alerts/src/main/java/dev/fusion/alerts/FocusModeContextEvaluator.kt', 'FocusModeContextEvaluator'),
    (':feature:automation', 'feature/automation/src/main/java/dev/fusion/automation/WorkflowJsonImportExportSandbox.kt', 'WorkflowJsonImportExportSandbox'),
    (':feature:backup', 'feature/backup/src/main/java/dev/fusion/backup/ResticCdcChunkDeduplicator.kt', 'ResticCdcChunkDeduplicator'),
    (':feature:beam', 'feature/beam/src/main/java/dev/fusion/beam/BeamChunkedResumeReedSolomon.kt', 'BeamChunkedResumeReedSolomon'),
    (':feature:browser', 'feature/browser/src/main/java/dev/fusion/browser/BrowserProfileDirectoryIsolator.kt', 'BrowserProfileDirectoryIsolator'),
    (':feature:browser', 'feature/browser/src/main/java/dev/fusion/browser/BrowserTabShortcutBatchBroker.kt', 'BrowserTabShortcutBatchBroker'),
    (':feature:browser', 'feature/browser/src/main/java/dev/fusion/browser/DnsOverHttpsWireQueryCodec.kt', 'DnsOverHttpsWireQueryCodec'),
    (':feature:browser', 'feature/browser/src/main/java/dev/fusion/browser/EasyListTrackerFilterEngine.kt', 'EasyListTrackerFilterEngine'),
    (':feature:calc', 'feature/calc/src/main/java/dev/fusion/calc/UnitMatrixSolverWorkbench.kt', 'UnitMatrixSolverWorkbench'),
    (':feature:capture', 'feature/capture/src/main/java/dev/fusion/capture/ScrollingCaptureSeamStitcher.kt', 'ScrollingCaptureSeamStitcher'),
    (':feature:continuity', 'feature/continuity/src/main/java/dev/fusion/continuity/RemoteDesktopRfbTileCodec.kt', 'RemoteDesktopRfbTileCodec'),
    (':feature:continuity', 'feature/continuity/src/main/java/dev/fusion/continuity/SharedFolderDeltaSyncPlanner.kt', 'SharedFolderDeltaSyncPlanner'),
    (':feature:editor', 'feature/editor/src/main/java/dev/fusion/editor/OutlineTocCrossRefEngine.kt', 'OutlineTocCrossRefEngine'),
    (':feature:files', 'feature/files/src/main/java/dev/fusion/filesui/DualPaneContextMenuModel.kt', 'DualPaneContextMenuModel'),
    (':feature:find', 'feature/find/src/main/java/dev/fusion/find/CommandPaletteFuzzyActionRanker.kt', 'CommandPaletteFuzzyActionRanker'),
    (':feature:launcher', 'feature/launcher/src/main/java/dev/fusion/launcher/PinnedWebShortcutShelfModel.kt', 'PinnedWebShortcutShelfModel'),
    (':feature:lens', 'feature/lens/src/main/java/dev/fusion/lens/Cube3dLutColorGrader.kt', 'Cube3dLutColorGrader'),
    (':feature:lens', 'feature/lens/src/main/java/dev/fusion/lens/FoldPosturePresenterStudio.kt', 'FoldPosturePresenterStudio'),
    (':feature:lens', 'feature/lens/src/main/java/dev/fusion/lens/Fsr1EasuRcasUpscaler.kt', 'Fsr1EasuRcasUpscaler'),
    (':feature:lens', 'feature/lens/src/main/java/dev/fusion/lens/NlmsAcousticEchoGate.kt', 'NlmsAcousticEchoGate'),
    (':feature:lens', 'feature/lens/src/main/java/dev/fusion/lens/PerspectiveDeskewBinarizer.kt', 'PerspectiveDeskewBinarizer'),
    (':feature:lens', 'feature/lens/src/main/java/dev/fusion/lens/VirtualWebcamMjpegStream.kt', 'VirtualWebcamMjpegStream'),
    (':feature:maps', 'feature/maps/src/main/java/dev/fusion/maps/OfflinePmtilesHilbertDirectory.kt', 'OfflinePmtilesHilbertDirectory'),
    (':feature:media', 'feature/media/src/main/java/dev/fusion/media/Mp4KeyframeTrimPlanner.kt', 'Mp4KeyframeTrimPlanner'),
    (':feature:media', 'feature/media/src/main/java/dev/fusion/media/VoiceRecorderWavWaveformCodec.kt', 'VoiceRecorderWavWaveformCodec'),
    (':feature:messages', 'feature/messages/src/main/java/dev/fusion/messages/MatrixMegolmGroupRatchet.kt', 'MatrixMegolmGroupRatchet'),
    (':feature:messages', 'feature/messages/src/main/java/dev/fusion/messages/UnifiedPushSmsFallbackRouter.kt', 'UnifiedPushSmsFallbackRouter'),
    (':feature:messages', 'feature/messages/src/main/java/dev/fusion/messages/XmppOmemoStanzaSeal.kt', 'XmppOmemoStanzaSeal'),
    (':feature:notes', 'feature/notes/src/main/java/dev/fusion/notes/MermaidFlowchartSvgLayout.kt', 'MermaidFlowchartSvgLayout'),
    (':feature:settings', 'feature/settings/src/main/java/dev/fusion/settingsui/KeyboardShortcutProfileEditor.kt', 'KeyboardShortcutProfileEditor'),
    (':feature:terminal', 'feature/terminal/src/main/java/dev/fusion/terminal/SshTransportKexFrameCodec.kt', 'SshTransportKexFrameCodec'),
    (':feature:together', 'feature/together/src/main/java/dev/fusion/together/TogetherCoWatchSyncClock.kt', 'TogetherCoWatchSyncClock'),
    (':feature:vault', 'feature/vault/src/main/java/dev/fusion/vault/ShamirSecretShareSplitter.kt', 'ShamirSecretShareSplitter'),
    (':format:archive', 'format/archive/src/main/kotlin/dev/fusion/archive/Mat2RecursiveMetadataScrubber.kt', 'Mat2RecursiveMetadataScrubber'),
    (':format:image', 'format/image/src/main/java/dev/fusion/format/image/OklchPaletteContrastPicker.kt', 'OklchPaletteContrastPicker'),
    (':format:lok', 'format/lok/src/main/java/dev/fusion/lok/ThumbnailMetadataIndexer.kt', 'ThumbnailMetadataIndexer'),
    (':format:native', 'format/native/src/main/kotlin/dev/fusion/native/NewFileTemplateCatalog.kt', 'NewFileTemplateCatalog'),
    (':format:ooxml', 'format/ooxml/src/main/kotlin/dev/fusion/ooxml/ConditionalDataBarScaleEngine.kt', 'ConditionalDataBarScaleEngine'),
    (':format:ooxml', 'format/ooxml/src/main/kotlin/dev/fusion/ooxml/OoxmlSparklineSvgRenderer.kt', 'OoxmlSparklineSvgRenderer'),
    (':format:ooxml', 'format/ooxml/src/main/kotlin/dev/fusion/ooxml/PivotCacheXmlExporter.kt', 'PivotCacheXmlExporter'),
    (':format:pdf', 'format/pdf/src/main/java/dev/fusion/format/pdf/PdfIncrementalSaveRedactor.kt', 'PdfIncrementalSaveRedactor'),
    (':format:pdf', 'format/pdf/src/main/java/dev/fusion/format/pdf/PdfPageBookmarkSplitMerger.kt', 'PdfPageBookmarkSplitMerger'),
    (':format:psd', 'format/psd/src/main/java/dev/fusion/psd/PsdBlendAdjustmentCompositor.kt', 'PsdBlendAdjustmentCompositor'),
    (':privacy', 'privacy/src/main/java/dev/fusion/privacy/BluetoothBluffsSessionGuard.kt', 'BluetoothBluffsSessionGuard'),
    (':privacy', 'privacy/src/main/java/dev/fusion/privacy/ClearUrlTrackingParamStripper.kt', 'ClearUrlTrackingParamStripper'),
    (':privacy', 'privacy/src/main/java/dev/fusion/privacy/PrivacyPostureDashboardEngine.kt', 'PrivacyPostureDashboardEngine'),
    (':privacy', 'privacy/src/main/java/dev/fusion/privacy/TorPluggableTransportConfig.kt', 'TorPluggableTransportConfig'),
    (':privacy', 'privacy/src/main/java/dev/fusion/privacy/WifiProbeSequenceScrambler.kt', 'WifiProbeSequenceScrambler'),
    (':testing', 'testing/src/main/kotlin/dev/fusion/testing/R50CrossKernelTranscriptVerifier.kt', 'R50CrossKernelTranscriptVerifier'),
]

TOUCHED: dict[str, tuple[str, str]] = {
    "format/ooxml/src/main/kotlin/dev/fusion/ooxml/FormulaEval.kt": (
        ":format:ooxml",
        "R50 expands FormulaEval from 130 to 168 pure functions with matrix algebra (MDETERM, MINVERSE, MMULT, MUNIT), FREQUENCY/TREND/GROWTH/RANK/PERCENTRANK, BIT*/DEC2*/HEX2*/BIN2*/OCT2*, CONVERT, ERF/ERFC/GAMMALN, GCD/LCM, WORKDAY, and DOLLARDE/DOLLARFR.",
    ),
    "format/ooxml/src/main/kotlin/dev/fusion/ooxml/SpillSheetEngine.kt": (
        ":format:ooxml",
        "R50 expands SpillSheetEngine from 23 to 29 spilled array operations adding MMULT, MINVERSE, MUNIT, FREQUENCY, TREND, and GROWTH.",
    ),
    "format/ooxml/src/main/kotlin/dev/fusion/ooxml/MultiSheetBook.kt": (
        ":format:ooxml",
        "R50 adds MultiSheetBook.evaluate3d and write3d for 3D cross-sheet span formulas (SUM/AVERAGE/MIN/MAX/COUNT/PRODUCT(Start:End!Range)) while preserving single-sheet evaluate() failure contracts.",
    ),
    "core/capabilities/src/main/java/dev/fusion/capabilities/WasmSandbox.kt": (
        ":core:capabilities",
        "R50 upgrades WasmSandbox with full IEEE-754 f64 (0x7C) load/store/arithmetic/comparisons/conversions + invokeF64 and Wasm 2.0 table.get/set/size/grow/fill/copy + passive elem.drop.",
    ),
    "feature/lens/src/main/java/dev/fusion/lens/LensScreen.kt": (
        ":feature:lens",
        "R50 wires FoldPosturePresenterStudio studio posture + focal-length optic badge into LensScreen (authored-only Compose UI, syntax PARSE OK).",
    ),
    "feature/messages/src/main/java/dev/fusion/messages/MessagesScreen.kt": (
        ":feature:messages",
        "R50 wires MatrixMegolmGroupRatchet forward-secret group E2EE scope posture into MessagesScreen (authored-only Compose UI, syntax PARSE OK).",
    ),
    "feature/continuity/src/main/java/dev/fusion/continuity/ContinuityScreen.kt": (
        ":feature:continuity",
        "R50 wires RemoteDesktopRfbTileCodec RFB 003.008 tile budget summary into ContinuityScreen (authored-only Compose UI, syntax PARSE OK).",
    ),
    "feature/together/src/main/java/dev/fusion/together/TogetherSession.kt": (
        ":feature:together",
        "R50 wires TogetherCoWatchSyncClock deterministic co-watch clock summary into TogetherSession (authored-only Compose UI, syntax PARSE OK).",
    ),
    "feature/beam/src/main/java/dev/fusion/beam/CastSendPanel.kt": (
        ":feature:beam",
        "R50 wires BeamChunkedResumeReedSolomon GF(2^8) parity chunk summary into CastSendPanel (authored-only Compose UI, syntax PARSE OK).",
    ),
    "feature/automation/src/main/java/dev/fusion/automation/TaskPanel.kt": (
        ":feature:automation",
        "R50 wires WorkflowJsonImportExportSandbox dry-run status into TaskPanel (authored-only Compose UI, syntax PARSE OK).",
    ),
    "feature/accessibility/src/main/java/dev/fusion/a11y/Adaptation.kt": (
        ":feature:accessibility",
        "R50 wires ColorBlindnessDaltonizeMatrix, FocusRingDwellClickController, and EasySimpleModeLayoutScaler into Adaptation (authored-only Compose UI, syntax PARSE OK).",
    ),
    "cast/dlna/src/main/java/dev/fusion/cast/dlna/DlnaScreen.kt": (
        ":cast:dlna",
        "R50 wires DlnaDidlLitePlaylistQueue DIDL-Lite XML escape verification into DlnaScreen (authored-only Compose UI, syntax PARSE OK).",
    ),
    "format/psd/src/main/java/dev/fusion/psd/PsdScreen.kt": (
        ":format:psd",
        "R50 wires PsdBlendAdjustmentCompositor pure-JVM layer blend preview into PsdScreen (authored-only Compose UI, syntax PARSE OK).",
    ),
    "privacy/src/main/java/dev/fusion/privacy/PrivacyActivity.kt": (
        ":privacy",
        "R50 wires ClearUrlTrackingParamStripper offline tracking-key inspection into PrivacyActivity while preserving GuardedPrivacyActivity amnesic view tree (syntax PARSE OK).",
    ),
}

R50_CHECKS = [
    "testing/src/r50/kotlin/dev/fusion/testing/R50Checks.kt",
    "tools/test_r50_tools.py",
]
CMD = "python3 tools/run_r50_contracts.py"


def sha(rel: str) -> str:
    return hashlib.sha256((ROOT / rel).read_bytes()).hexdigest()


def main() -> int:
    assert len(R50_KERNELS) == 80, f"Expected 80 R50 kernels, got {len(R50_KERNELS)}"
    assert len(TOUCHED) == 14, f"Expected 14 R50 TOUCHED files, got {len(TOUCHED)}"
    deepen: dict[str, list[tuple[str, str]]] = {}
    for mod, path, name in R50_KERNELS:
        assert (ROOT / path).is_file(), f"Missing R50 kernel file: {path}"
        deepen.setdefault(mod, []).append((path, f"R50 adds {name} pure-JVM kernel."))
    for t_path in TOUCHED:
        assert (ROOT / t_path).is_file(), f"Missing R50 TOUCHED file: {t_path}"

    jp = ROOT / "docs/module-testability.json"
    data = json.loads(jp.read_text(encoding="utf-8"))

    also_allowed_repin = {
        "tools/check_r26_android_syntax.py",
        "tools/fusion_workbench_server.py",
        "tools/fusion_workbench_cli.py",
        "format/ooxml/src/main/kotlin/dev/fusion/ooxml/BoundedXml.kt",
    }
    owned = {f for items in deepen.values() for f, _ in items} | set(R50_CHECKS) | set(TOUCHED) | also_allowed_repin
    stale: list[tuple[str, str]] = []
    for r in data["rows"]:
        for p, pinned in r["sha256"].items():
            if p not in owned and sha(p) != pinned:
                stale.append((r["module"], p))
    if stale:
        raise SystemExit(f"refusing to silently re-pin modified files outside R50 DEEPEN/TOUCHED: {stale}")

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
        for chk in R50_CHECKS:
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
    print(f"R50 census deepen OK ({len(seen_mods)}/50 = 100.00% modules deepened, {len(touched_applied)} touched):", json.dumps(census.counts(reloaded)))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
