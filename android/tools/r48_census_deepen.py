#!/usr/bin/env python3
"""Idempotently deepen ALL 50/50 module rows in docs/module-testability.json with 66 R48 kernels + 7 TOUCHED files.

Never modifies docs/module-audit.json (full-spec Class A acceptance remains strictly 0/50 = 0.00%).
Preserves exact R47 wiring on the 48 R47 modules while wiring run_r48_contracts.py across all 50/50 modules.
"""
from __future__ import annotations

import hashlib
import json
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "tools"))
import module_testability as census

R48_KERNELS: list[tuple[str, str, str]] = [
    (":format:ooxml", "format/ooxml/src/main/kotlin/dev/fusion/ooxml/SpillSheetEngine.kt", "SpillSheetEngine"),
    (":format:ooxml", "format/ooxml/src/main/kotlin/dev/fusion/ooxml/PptxSlideDeck.kt", "PptxSlideDeck"),
    (":format:ooxml", "format/ooxml/src/main/kotlin/dev/fusion/ooxml/PivotCubeSummary.kt", "PivotCubeSummary"),
    (":format:ooxml", "format/ooxml/src/main/kotlin/dev/fusion/ooxml/XlsxDataValidationXml.kt", "XlsxDataValidationXml"),
    (":core:documents", "core/documents/src/main/kotlin/dev/fusion/documents/Epub3Package.kt", "Epub3Package"),
    (":core:documents", "core/documents/src/main/kotlin/dev/fusion/documents/RtfCleanConverter.kt", "RtfCleanConverter"),
    (":core:documents", "core/documents/src/main/kotlin/dev/fusion/documents/OpenSearchSuggestionJson.kt", "OpenSearchSuggestionJson"),
    (":format:pdf", "format/pdf/src/main/java/dev/fusion/pdf/PdfAcroFormWriter.kt", "PdfAcroFormWriter"),
    (":format:pdf", "format/pdf/src/main/java/dev/fusion/pdf/PdfPageLabelsAndOutline.kt", "PdfPageLabelsAndOutline"),
    (":format:lok", "format/lok/src/main/kotlin/dev/fusion/lok/OdsSpreadsheetXml.kt", "OdsSpreadsheetXml"),
    (":feature:editor", "feature/editor/src/main/java/dev/fusion/editor/ReadabilityMetrics.kt", "ReadabilityMetrics"),
    (":feature:editor", "feature/editor/src/main/java/dev/fusion/editor/MarkdownOfficeBridge.kt", "MarkdownOfficeBridge"),
    (":feature:calc", "feature/calc/src/main/kotlin/dev/fusion/calc/FinancialScheduleSolver.kt", "FinancialScheduleSolver"),
    (":feature:calc", "feature/calc/src/main/kotlin/dev/fusion/calc/MatrixLinearAlgebra.kt", "MatrixLinearAlgebra"),
    (":feature:notes", "feature/notes/src/main/kotlin/dev/fusion/notes/ZettelkastenFolgezettel.kt", "ZettelkastenFolgezettel"),
    (":privacy", "privacy/src/main/java/dev/fusion/privacy/BinaryHttpRfc9292.kt", "BinaryHttpRfc9292"),
    (":privacy", "privacy/src/main/java/dev/fusion/privacy/ObliviousHttpPolicy.kt", "ObliviousHttpPolicy"),
    (":privacy", "privacy/src/main/java/dev/fusion/privacy/OnionV3Address.kt", "OnionV3Address"),
    (":privacy", "privacy/src/main/java/dev/fusion/privacy/AcousticKeystrokeShield.kt", "AcousticKeystrokeShield"),
    (":privacy", "privacy/src/main/java/dev/fusion/privacy/ExitTrafficPadBucket.kt", "ExitTrafficPadBucket"),
    (":core:hw", "core/hw/src/main/java/dev/fusion/hw/HidUsageFirewall.kt", "HidUsageFirewall"),
    (":core:security", "core/security/src/main/java/dev/fusion/security/ScreenCaptureGuard.kt", "ScreenCaptureGuard"),
    (":core:security", "core/security/src/main/java/dev/fusion/security/ConstantTimePrimitives.kt", "ConstantTimePrimitives"),
    (":core:security", "core/security/src/main/java/dev/fusion/security/HkdfRfc5869.kt", "HkdfRfc5869"),
    (":feature:vault", "feature/vault/src/main/kotlin/dev/fusion/vault/PasskeyRpValidator.kt", "PasskeyRpValidator"),
    (":feature:browser", "feature/browser/src/main/java/dev/fusion/browser/ArticleReaderExtractor.kt", "ArticleReaderExtractor"),
    (":feature:browser", "feature/browser/src/main/java/dev/fusion/browser/CrossOriginIsolationPolicy.kt", "CrossOriginIsolationPolicy"),
    (":feature:browser", "feature/browser/src/main/java/dev/fusion/browser/CspHeaderDirective.kt", "CspHeaderDirective"),
    (":core:capabilities", "core/capabilities/src/main/java/dev/fusion/capabilities/WasiVirtualPipe.kt", "WasiVirtualPipe"),
    (":app", "app/src/main/java/dev/fusion/shell/WorkspaceSessionSnapshot.kt", "WorkspaceSessionSnapshot"),
    (":core:windowing", "core/windowing/src/main/kotlin/dev/fusion/windowing/SnapZoneMagneticRouter.kt", "SnapZoneMagneticRouter"),
    (":core:files", "core/files/src/main/kotlin/dev/fusion/files/ContentHashDeduplicator.kt", "ContentHashDeduplicator"),
    (":core:model", "core/model/src/main/kotlin/dev/fusion/model/VersionedSchemaEnvelope.kt", "VersionedSchemaEnvelope"),
    (":core:commands", "core/commands/src/main/kotlin/dev/fusion/commands/KeybindingChordTrie.kt", "KeybindingChordTrie"),
    (":core:settings", "core/settings/src/main/kotlin/dev/fusion/settings/PolicyLockdownMerger.kt", "PolicyLockdownMerger"),
    (":core:perf", "core/perf/src/main/kotlin/dev/fusion/perf/JankFrameClassifier.kt", "JankFrameClassifier"),
    (":core:index", "core/index/src/main/kotlin/dev/fusion/index/RoaringBitSetLite.kt", "RoaringBitSetLite"),
    (":core:storage", "core/storage/src/main/kotlin/dev/fusion/storage/BTreePageLayout.kt", "BTreePageLayout"),
    (":core:designsystem", "core/designsystem/src/main/kotlin/dev/fusion/designsystem/AdaptiveTypographyScale.kt", "AdaptiveTypographyScale"),
    (":feature:launcher", "feature/launcher/src/main/kotlin/dev/fusion/launcher/FrecencyAppRanker.kt", "FrecencyAppRanker"),
    (":feature:find", "feature/find/src/main/java/dev/fusion/find/RegexSafeAutomaton.kt", "RegexSafeAutomaton"),
    (":feature:files", "feature/files/src/main/kotlin/dev/fusion/filesui/SafeBatchRenamePlanner.kt", "SafeBatchRenamePlanner"),
    (":feature:terminal", "feature/terminal/src/main/kotlin/dev/fusion/terminal/TmuxPaneLayoutCodec.kt", "TmuxPaneLayoutCodec"),
    (":feature:settings", "feature/settings/src/main/kotlin/dev/fusion/settingsui/PermissionRiskAuditor.kt", "PermissionRiskAuditor"),
    (":feature:media", "feature/media/src/main/kotlin/dev/fusion/media/WebVttCueTimeline.kt", "WebVttCueTimeline"),
    (":feature:maps", "feature/maps/src/main/kotlin/dev/fusion/maps/GeoFencePolygon.kt", "GeoFencePolygon"),
    (":feature:beam", "feature/beam/src/main/kotlin/dev/fusion/beam/ReedSolomonQrEcc.kt", "ReedSolomonQrEcc"),
    (":feature:alerts", "feature/alerts/src/main/kotlin/dev/fusion/alerts/CronExpressionSchedule.kt", "CronExpressionSchedule"),
    (":feature:capture", "feature/capture/src/main/kotlin/dev/fusion/capture/PiiRedactionScanner.kt", "PiiRedactionScanner"),
    (":feature:backup", "feature/backup/src/main/kotlin/dev/fusion/backup/ChunkedRestoreVerifier.kt", "ChunkedRestoreVerifier"),
    (":feature:continuity", "feature/continuity/src/main/kotlin/dev/fusion/continuity/VectorClockCausalOrder.kt", "VectorClockCausalOrder"),
    (":feature:accessibility", "feature/accessibility/src/main/kotlin/dev/fusion/a11y/ReadingOrderLinearizer.kt", "ReadingOrderLinearizer"),
    (":feature:together", "feature/together/src/main/kotlin/dev/fusion/together/YataRgbaSequenceCrdt.kt", "YataRgbaSequenceCrdt"),
    (":feature:lens", "feature/lens/src/main/kotlin/dev/fusion/lens/PerspectiveHomography3x3.kt", "PerspectiveHomography3x3"),
    (":feature:messages", "feature/messages/src/main/kotlin/dev/fusion/messages/DoubleRatchetHeaderChain.kt", "DoubleRatchetHeaderChain"),
    (":feature:automation", "feature/automation/src/main/kotlin/dev/fusion/automation/WorkflowPetriNet.kt", "WorkflowPetriNet"),
    (":format:native", "format/native/src/main/kotlin/dev/fusion/nativefmt/Elf16KbPageAudit.kt", "Elf16KbPageAudit"),
    (":format:archive", "format/archive/src/main/kotlin/dev/fusion/archive/SevenZipSignatureHeader.kt", "SevenZipSignatureHeader"),
    (":format:image", "format/image/src/main/kotlin/dev/fusion/format/image/NetpbmPpmCodec.kt", "NetpbmPpmCodec"),
    (":format:psd", "format/psd/src/main/kotlin/dev/fusion/psd/PsdColorModeResources.kt", "PsdColorModeResources"),
    (":cast:dlna", "cast/dlna/src/main/kotlin/dev/fusion/cast/dlna/DidlLiteMetadataXml.kt", "DidlLiteMetadataXml"),
    (":cast:airplay", "cast/airplay/src/main/kotlin/dev/fusion/airplay/RtpAudioPacketHeader.kt", "RtpAudioPacketHeader"),
    (":cast:castv2", "cast/castv2/src/main/kotlin/dev/fusion/castv2/CastMediaStatusFrame.kt", "CastMediaStatusFrame"),
    (":benchmark", "benchmark/src/main/java/dev/fusion/benchmark/WelchTTestRegression.kt", "WelchTTestRegression"),
    (":baselineprofile", "baselineprofile/src/main/kotlin/dev/fusion/baselineprofile/ArtProfileRuleCodec.kt", "ArtProfileRuleCodec"),
    (":testing", "testing/src/main/kotlin/dev/fusion/testing/DeterministicShrinker.kt", "DeterministicShrinker"),
]

TOUCHED: dict[str, tuple[str, str]] = {
    "format/ooxml/src/main/kotlin/dev/fusion/ooxml/FormulaEval.kt": (
        ":format:ooxml",
        "R48 expands FormulaEval from 61 to 95 pure functions plus inline array literals {1,2;3,4}, lexical LET, and _xlfn./_xlws./_xlpm. normalization.",
    ),
    "core/capabilities/src/main/java/dev/fusion/capabilities/WasmSandbox.kt": (
        ":core:capabilities",
        "R48 upgrades WasmSandbox with Wasm 2.0 bulk-memory (0xFC memory.copy, memory.fill), sign-extension ops, rotl/rotr, and structured-block 0xFC skip.",
    ),
    "feature/editor/src/main/java/dev/fusion/editor/EditorScreen.kt": (
        ":feature:editor",
        "R48 wires live ReadabilityMetrics + Epub3Package/RtfCleanConverter export readiness banner into EditorScreen (authored-only Compose UI, syntax PARSE OK).",
    ),
    "feature/browser/src/main/java/dev/fusion/browser/BrowserScreen.kt": (
        ":feature:browser",
        "R48 wires CrossOriginIsolationPolicy (COOP/COEP) + CspHeaderDirective + ArticleReaderExtractor badge into BrowserScreen (authored-only Compose UI, syntax PARSE OK).",
    ),
    "feature/vault/src/main/java/dev/fusion/vault/VaultScreen.kt": (
        ":feature:vault",
        "R48 wires ScreenCaptureGuard + PasskeyRpValidator WebAuthn L3 shield banner into VaultScreen (authored-only Compose UI, syntax PARSE OK).",
    ),
    "feature/notes/src/main/java/dev/fusion/notes/NotesScreen.kt": (
        ":feature:notes",
        "R48 wires ZettelkastenFolgezettel outline status strip into NotesScreen without touching store invariants (authored-only Compose UI, syntax PARSE OK).",
    ),
    "feature/terminal/src/main/java/dev/fusion/terminal/TerminalScreen.kt": (
        ":feature:terminal",
        "R48 wires TmuxPaneLayoutCodec 16-bit layout checksum badge into TerminalScreen (authored-only Compose UI, syntax PARSE OK).",
    ),
}

R48_CHECKS = [
    "testing/src/r48/kotlin/dev/fusion/testing/R48Checks.kt",
    "tools/test_r48_tools.py",
]
CMD = "python3 tools/run_r48_contracts.py"


def sha(rel: str) -> str:
    return hashlib.sha256((ROOT / rel).read_bytes()).hexdigest()


def main() -> int:
    assert len(R48_KERNELS) == 66
    deepen: dict[str, list[tuple[str, str]]] = {}
    for mod, path, name in R48_KERNELS:
        deepen.setdefault(mod, []).append((path, f"R48 adds {name} pure-JVM kernel."))

    jp = ROOT / "docs/module-testability.json"
    data = json.loads(jp.read_text(encoding="utf-8"))

    also_allowed_repin = {"tools/check_r26_android_syntax.py"}
    owned = {f for items in deepen.values() for f, _ in items} | set(R48_CHECKS) | set(TOUCHED) | also_allowed_repin
    stale: list[tuple[str, str]] = []
    for r in data["rows"]:
        for p, pinned in r["sha256"].items():
            if p not in owned and sha(p) != pinned:
                stale.append((r["module"], p))
    if stale:
        raise SystemExit(f"refusing to silently re-pin modified files outside R48 DEEPEN/TOUCHED: {stale}")

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
        for chk in R48_CHECKS:
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
    print(f"R48 census deepen OK ({len(seen_mods)}/50 = 100.00% modules deepened, {len(touched_applied)} touched):", json.dumps(census.counts(reloaded)))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
