#!/usr/bin/env python3
"""Idempotently deepen ALL 50/50 module rows in docs/module-testability.json with 75 R49 kernels + 11 TOUCHED files.

Never modifies docs/module-audit.json (full-spec Class A acceptance remains strictly 0/50 = 0.00%).
Preserves exact R47 wiring on the 48 R47 modules and R48 wiring on all 50 R48 modules while wiring
run_r49_contracts.py across all 50/50 modules.
"""
from __future__ import annotations

import hashlib
import json
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "tools"))
import module_testability as census

R49_KERNELS: list[tuple[str, str, str]] = [
    # Batch A (9): Writer, Publishing, ODF Tracked Changes, Notes & Slides
    (":core:documents", "core/documents/src/main/kotlin/dev/fusion/documents/KnuthPlassLineBreaker.kt", "KnuthPlassLineBreaker"),
    (":core:documents", "core/documents/src/main/kotlin/dev/fusion/documents/LiangHyphenationEngine.kt", "LiangHyphenationEngine"),
    (":core:documents", "core/documents/src/main/kotlin/dev/fusion/documents/MultiColumnPagePaginator.kt", "MultiColumnPagePaginator"),
    (":core:documents", "core/documents/src/main/kotlin/dev/fusion/documents/OdfTrackedChangesCodec.kt", "OdfTrackedChangesCodec"),
    (":feature:editor", "feature/editor/src/main/java/dev/fusion/editor/MailMergeTemplateEngine.kt", "MailMergeTemplateEngine"),
    (":feature:editor", "feature/editor/src/main/java/dev/fusion/editor/DocumentCompareDiff.kt", "DocumentCompareDiff"),
    (":feature:editor", "feature/editor/src/main/java/dev/fusion/editor/BibliographyCitationEngine.kt", "BibliographyCitationEngine"),
    (":feature:notes", "feature/notes/src/main/java/dev/fusion/notes/StickyNoteBoardLayout.kt", "StickyNoteBoardLayout"),
    (":feature:notes", "feature/notes/src/main/java/dev/fusion/notes/MarkdownSlideExporter.kt", "MarkdownSlideExporter"),
    # Batch B (14): Spreadsheet Charts/Sparklines/Solver/VBA, Formats Registry/AE-2/Watchdog/LOK/HEIC/AI, PDF Forms/Search/Sig
    (":format:ooxml", "format/ooxml/src/main/kotlin/dev/fusion/ooxml/DrawingMlChartBuilder.kt", "DrawingMlChartBuilder"),
    (":format:ooxml", "format/ooxml/src/main/kotlin/dev/fusion/ooxml/SheetSparklineRenderer.kt", "SheetSparklineRenderer"),
    (":format:ooxml", "format/ooxml/src/main/kotlin/dev/fusion/ooxml/SheetSolverSimplex.kt", "SheetSolverSimplex"),
    (":format:ooxml", "format/ooxml/src/main/kotlin/dev/fusion/ooxml/OoxmlMacroVbaInspector.kt", "OoxmlMacroVbaInspector"),
    (":format:native", "format/native/src/main/kotlin/dev/fusion/native/FormatsRegistryCatalog.kt", "FormatsRegistryCatalog"),
    (":format:native", "format/native/src/main/kotlin/dev/fusion/native/ExportFidelityAdvisor.kt", "ExportFidelityAdvisor"),
    (":format:archive", "format/archive/src/main/kotlin/dev/fusion/archive/ZipAesExtraFieldCodec.kt", "ZipAesExtraFieldCodec"),
    (":format:archive", "format/archive/src/main/kotlin/dev/fusion/archive/FormatSandboxWatchdog.kt", "FormatSandboxWatchdog"),
    (":format:lok", "format/lok/src/main/java/dev/fusion/lok/LokTileRenderCache.kt", "LokTileRenderCache"),
    (":format:image", "format/image/src/main/java/dev/fusion/format/image/HeicIsomBoxInspector.kt", "HeicIsomBoxInspector"),
    (":format:psd", "format/psd/src/main/java/dev/fusion/psd/AiPdfCompatibilityBridge.kt", "AiPdfCompatibilityBridge"),
    (":format:pdf", "format/pdf/src/main/java/dev/fusion/format/pdf/PdfFormAcroFieldInspector.kt", "PdfFormAcroFieldInspector"),
    (":format:pdf", "format/pdf/src/main/java/dev/fusion/format/pdf/PdfPageTextSearchHighlighter.kt", "PdfPageTextSearchHighlighter"),
    (":format:pdf", "format/pdf/src/main/java/dev/fusion/format/pdf/PdfPkcs7SignatureVerifier.kt", "PdfPkcs7SignatureVerifier"),
    # Batch C (16): Browser WebRTC/SplitTabs/Perms/Find, WASI/AppFunctions, RFC 9849 ECH, ChoiceJacking/Pixnapping/Tails CA/VPN/DVFS/AppLock, USB/Mouse, Tink
    (":feature:browser", "feature/browser/src/main/java/dev/fusion/browser/WebRtcIceCandidateScrubber.kt", "WebRtcIceCandidateScrubber"),
    (":feature:browser", "feature/browser/src/main/java/dev/fusion/browser/BrowserSplitTabWorkspace.kt", "BrowserSplitTabWorkspace"),
    (":feature:browser", "feature/browser/src/main/java/dev/fusion/browser/SitePermissionVault.kt", "SitePermissionVault"),
    (":feature:browser", "feature/browser/src/main/java/dev/fusion/browser/FindInPageMatchEngine.kt", "FindInPageMatchEngine"),
    (":core:capabilities", "core/capabilities/src/main/java/dev/fusion/capabilities/WasiSocketCapabilityGate.kt", "WasiSocketCapabilityGate"),
    (":core:capabilities", "core/capabilities/src/main/java/dev/fusion/capabilities/AppFunctionsSchemaRegistry.kt", "AppFunctionsSchemaRegistry"),
    (":privacy", "privacy/src/main/java/dev/fusion/privacy/TlsEchConfigCodec.kt", "TlsEchConfigCodec"),
    (":privacy", "privacy/src/main/java/dev/fusion/privacy/ChoiceJackingUsbGuard.kt", "ChoiceJackingUsbGuard"),
    (":privacy", "privacy/src/main/java/dev/fusion/privacy/PixnappingSurfaceShield.kt", "PixnappingSurfaceShield"),
    (":privacy", "privacy/src/main/java/dev/fusion/privacy/TailsCaTrustPinBlocklist.kt", "TailsCaTrustPinBlocklist"),
    (":core:security", "core/security/src/main/java/dev/fusion/security/VpnAppFirewallPolicy.kt", "VpnAppFirewallPolicy"),
    (":core:security", "core/security/src/main/java/dev/fusion/security/PowerDvfsTimingMitigator.kt", "PowerDvfsTimingMitigator"),
    (":core:security", "core/security/src/main/java/dev/fusion/security/ModuleAppLockGate.kt", "ModuleAppLockGate"),
    (":core:hw", "core/hw/src/main/java/dev/fusion/hw/ExternalMouseButtonMapper.kt", "ExternalMouseButtonMapper"),
    (":core:hw", "core/hw/src/main/java/dev/fusion/hw/UsbDataSignalingController.kt", "UsbDataSignalingController"),
    (":feature:vault", "feature/vault/src/main/java/dev/fusion/vault/TinkAeadKeysetEnvelope.kt", "TinkAeadKeysetEnvelope"),
    # Batch D (17): Shell, Windowing, Files, Billing, Commands, Settings, Perf, Index, Storage, DesignSystem, Launcher, Find, FilesUI, SettingsUI, Terminal, Calc
    (":app", "app/src/main/java/dev/fusion/shell/BlackTopSessionCoordinator.kt", "BlackTopSessionCoordinator"),
    (":core:windowing", "core/windowing/src/main/kotlin/dev/fusion/windowing/MultiDisplayWindowBroker.kt", "MultiDisplayWindowBroker"),
    (":core:files", "core/files/src/main/java/dev/fusion/files/JournaledFileOpsPlanner.kt", "JournaledFileOpsPlanner"),
    (":core:files", "core/files/src/main/java/dev/fusion/files/ArchiveDocumentsProviderTree.kt", "ArchiveDocumentsProviderTree"),
    (":core:model", "core/model/src/main/kotlin/dev/fusion/model/BillingEntitlementLedger.kt", "BillingEntitlementLedger"),
    (":core:commands", "core/commands/src/main/kotlin/dev/fusion/commands/ThirdPartyIntentCommandGate.kt", "ThirdPartyIntentCommandGate"),
    (":core:settings", "core/settings/src/main/java/dev/fusion/settings/ManagedRestrictionsSchema.kt", "ManagedRestrictionsSchema"),
    (":core:perf", "core/perf/src/main/java/dev/fusion/perf/AdpfThermalRefreshPolicy.kt", "AdpfThermalRefreshPolicy"),
    (":core:index", "core/index/src/main/kotlin/dev/fusion/index/IndexChurnQuarantineMonitor.kt", "IndexChurnQuarantineMonitor"),
    (":core:storage", "core/storage/src/main/java/dev/fusion/storage/ConfigExportBundleCodec.kt", "ConfigExportBundleCodec"),
    (":core:designsystem", "core/designsystem/src/main/java/dev/fusion/designsystem/FocusRingPointerTokens.kt", "FocusRingPointerTokens"),
    (":feature:launcher", "feature/launcher/src/main/java/dev/fusion/launcher/WidgetHostIsolationSupervisor.kt", "WidgetHostIsolationSupervisor"),
    (":feature:find", "feature/find/src/main/java/dev/fusion/find/PaletteInlineAnswerEngine.kt", "PaletteInlineAnswerEngine"),
    (":feature:files", "feature/files/src/main/java/dev/fusion/filesui/DuplicateFileClusterer.kt", "DuplicateFileClusterer"),
    (":feature:settings", "feature/settings/src/main/java/dev/fusion/settingsui/OemSettingsDeepLinkCatalog.kt", "OemSettingsDeepLinkCatalog"),
    (":feature:terminal", "feature/terminal/src/main/java/dev/fusion/terminal/VtTrueColorSafePasteEngine.kt", "VtTrueColorSafePasteEngine"),
    (":feature:calc", "feature/calc/src/main/java/dev/fusion/calc/BaseRadixBitfieldInspector.kt", "BaseRadixBitfieldInspector"),
    # Batch E (19): Media, Maps, Beam, Alerts, Capture, Backup, Continuity, A11y, Together, Lens (x2), Messages, Automation, Cast (x3), Benchmark, BaselineProfile, Testing
    (":feature:media", "feature/media/src/main/java/dev/fusion/media/AudioFocusVolumeMatrix.kt", "AudioFocusVolumeMatrix"),
    (":feature:maps", "feature/maps/src/main/java/dev/fusion/maps/OfflineRouteTurnSynthesizer.kt", "OfflineRouteTurnSynthesizer"),
    (":feature:beam", "feature/beam/src/main/java/dev/fusion/beam/CastPrivacyExclusionGate.kt", "CastPrivacyExclusionGate"),
    (":feature:alerts", "feature/alerts/src/main/java/dev/fusion/alerts/NotificationDiagnosticLedger.kt", "NotificationDiagnosticLedger"),
    (":feature:capture", "feature/capture/src/main/java/dev/fusion/capture/CaptureBitrateBudgetPlanner.kt", "CaptureBitrateBudgetPlanner"),
    (":feature:backup", "feature/backup/src/main/java/dev/fusion/backup/BackupHealthDrillScheduler.kt", "BackupHealthDrillScheduler"),
    (":feature:continuity", "feature/continuity/src/main/java/dev/fusion/continuity/KdeConnectPacketCodec.kt", "KdeConnectPacketCodec"),
    (":feature:accessibility", "feature/accessibility/src/main/java/dev/fusion/a11y/WebVttCaptionCueTimeline.kt", "WebVttCaptionCueTimeline"),
    (":feature:together", "feature/together/src/main/java/dev/fusion/together/SharedClockSyncCoordinator.kt", "SharedClockSyncCoordinator"),
    (":feature:lens", "feature/lens/src/main/java/dev/fusion/lens/LensThermalQualityLadder.kt", "LensThermalQualityLadder"),
    (":feature:lens", "feature/lens/src/main/java/dev/fusion/lens/VirtualRelightNormalMap.kt", "VirtualRelightNormalMap"),
    (":feature:messages", "feature/messages/src/main/java/dev/fusion/messages/RcsHandoffUriBuilder.kt", "RcsHandoffUriBuilder"),
    (":feature:automation", "feature/automation/src/main/java/dev/fusion/automation/CalendarIcsRecurrenceEngine.kt", "CalendarIcsRecurrenceEngine"),
    (":cast:dlna", "cast/dlna/src/main/java/dev/fusion/cast/dlna/SsdpDiscoveryResponseParser.kt", "SsdpDiscoveryResponseParser"),
    (":cast:airplay", "cast/airplay/src/main/kotlin/dev/fusion/airplay/RaopRtpSyncPacketizer.kt", "RaopRtpSyncPacketizer"),
    (":cast:castv2", "cast/castv2/src/main/kotlin/dev/fusion/castv2/CastWebReceiverSessionModel.kt", "CastWebReceiverSessionModel"),
    (":benchmark", "benchmark/src/main/java/dev/fusion/benchmark/StartupFrameP99Analyzer.kt", "StartupFrameP99Analyzer"),
    (":baselineprofile", "baselineprofile/src/main/kotlin/dev/fusion/baselineprofile/BaselineProfileRuleSynthesizer.kt", "BaselineProfileRuleSynthesizer"),
    (":testing", "testing/src/main/kotlin/dev/fusion/testing/AdversarialMutationCorpus.kt", "AdversarialMutationCorpus"),
]

TOUCHED: dict[str, tuple[str, str]] = {
    "format/ooxml/src/main/kotlin/dev/fusion/ooxml/FormulaEval.kt": (
        ":format:ooxml",
        "R49 expands FormulaEval from 95 to 130 pure functions with lexical LAMBDA closures, ISOMITTED, MAP/REDUCE/SCAN/BYROW/BYCOL/MAKEARRAY, and VSTACK/HSTACK/TAKE/DROP/TOCOL/TOROW/CHOOSECOLS/CHOOSEROWS/WRAPROWS/WRAPCOLS.",
    ),
    "format/ooxml/src/main/kotlin/dev/fusion/ooxml/SpillSheetEngine.kt": (
        ":format:ooxml",
        "R49 expands SpillSheetEngine to 23 spilled dynamic-array & higher-order LAMBDA operations delegating 2D array evaluation to FormulaEval.",
    ),
    "core/capabilities/src/main/java/dev/fusion/capabilities/WasmSandbox.kt": (
        ":core:capabilities",
        "R49 upgrades WasmSandbox with Wasm 2.0 Table/Global/Element/Data/DataCount sections, type-checked call_indirect, memory.init/data.drop, and IEEE-754 f32 + saturating truncations.",
    ),
    "feature/calc/src/main/java/dev/fusion/calc/Calc.kt": (
        ":feature:calc",
        "R49 wires BaseRadixBitfieldInspector (64-bit + IEEE-754 binary64) and FinancialScheduleSolver preview into CalcScreen (authored-only Compose UI, syntax PARSE OK).",
    ),
    "feature/files/src/main/java/dev/fusion/filesui/FilesScreen.kt": (
        ":feature:files",
        "R49 wires DuplicateFileClusterer storage summary and Forge-peek headline into FilesScreen preserving entries=emptyList() order invariant (authored-only Compose UI, syntax PARSE OK).",
    ),
    "feature/find/src/main/java/dev/fusion/find/Palette.kt": (
        ":feature:find",
        "R49 wires PaletteInlineAnswerEngine local BigDecimal math/unit/radix answer card into CommandPalette (authored-only Compose UI, syntax PARSE OK).",
    ),
    "feature/settings/src/main/java/dev/fusion/settingsui/SettingsScreen.kt": (
        ":feature:settings",
        "R49 wires OemSettingsDeepLinkCatalog and PermissionRiskAuditor posture badge into SettingsScreen (authored-only Compose UI, syntax PARSE OK).",
    ),
    "feature/backup/src/main/java/dev/fusion/backup/BackupScreen.kt": (
        ":feature:backup",
        "R49 wires BackupHealthDrillScheduler 30-day restore-drill status and new-device migration checklist into BackupScreen (authored-only Compose UI, syntax PARSE OK).",
    ),
    "feature/capture/src/main/java/dev/fusion/capture/CaptureScreen.kt": (
        ":feature:capture",
        "R49 wires CaptureBitrateBudgetPlanner live 720p30 AVC storage-rate summary into CaptureScreen (authored-only Compose UI, syntax PARSE OK).",
    ),
    "format/pdf/src/main/java/dev/fusion/format/pdf/PdfScreen.kt": (
        ":format:pdf",
        "R49 wires PdfFormAcroFieldInspector and PdfPkcs7SignatureVerifier stream inspection summary into PdfScreen (authored-only Compose UI, syntax PARSE OK).",
    ),
    "feature/alerts/src/main/java/dev/fusion/alerts/AlertsScreen.kt": (
        ":feature:alerts",
        "R49 wires NotificationDiagnosticLedger disposition summary into AlertsScreen (authored-only Compose UI, syntax PARSE OK).",
    ),
}

R49_CHECKS = [
    "testing/src/r49/kotlin/dev/fusion/testing/R49Checks.kt",
    "tools/test_r49_tools.py",
]
CMD = "python3 tools/run_r49_contracts.py"


def sha(rel: str) -> str:
    return hashlib.sha256((ROOT / rel).read_bytes()).hexdigest()


def main() -> int:
    assert len(R49_KERNELS) == 75, f"Expected 75 R49 kernels, got {len(R49_KERNELS)}"
    assert len(TOUCHED) == 11, f"Expected 11 R49 TOUCHED files, got {len(TOUCHED)}"
    deepen: dict[str, list[tuple[str, str]]] = {}
    for mod, path, name in R49_KERNELS:
        assert (ROOT / path).is_file(), f"Missing R49 kernel file: {path}"
        deepen.setdefault(mod, []).append((path, f"R49 adds {name} pure-JVM kernel."))

    jp = ROOT / "docs/module-testability.json"
    data = json.loads(jp.read_text(encoding="utf-8"))

    also_allowed_repin = {"tools/check_r26_android_syntax.py"}
    owned = {f for items in deepen.values() for f, _ in items} | set(R49_CHECKS) | set(TOUCHED) | also_allowed_repin
    stale: list[tuple[str, str]] = []
    for r in data["rows"]:
        for p, pinned in r["sha256"].items():
            if p not in owned and sha(p) != pinned:
                stale.append((r["module"], p))
    if stale:
        raise SystemExit(f"refusing to silently re-pin modified files outside R49 DEEPEN/TOUCHED: {stale}")

    seen_mods: set[str] = set()
    touched_applied: set[str] = set()
    for r in data["rows"]:
        mod = r["module"]
        for t_path, (t_mod, t_note) in TOUCHED.items():
            if mod == t_mod or t_path in r["production"] or t_path in r["sha256"]:
                if mod == t_mod and t_path not in r["production"]:
                    # Only pure-JVM files belong in production[] if row is host_testable, whereas Compose UI screens
                    # live in authored_only or mixed rows; check existing convention:
                    if not t_path.endswith("Screen.kt") and not t_path.endswith("Calc.kt") and not t_path.endswith("Palette.kt"):
                        r["production"].append(t_path)
                    elif t_path not in r["production"]:
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
        for chk in R49_CHECKS:
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
    print(f"R49 census deepen OK ({len(seen_mods)}/50 = 100.00% modules deepened, {len(touched_applied)} touched):", json.dumps(census.counts(reloaded)))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
