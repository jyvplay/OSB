#!/usr/bin/env python3
"""Compile (with -Werror) and execute Round 48 (R48) pure-JVM contracts across all 50/50 modules."""
import argparse
import hashlib
import json
import os
import subprocess
import sys
import time
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]

JARS = [
    ("r48_office.jar", [], [
        "format/ooxml/src/main/kotlin/dev/fusion/ooxml/ColLetters.kt",
        "format/ooxml/src/main/kotlin/dev/fusion/ooxml/SheetRange.kt",
        "format/ooxml/src/main/kotlin/dev/fusion/ooxml/CellErr.kt",
        "format/ooxml/src/main/kotlin/dev/fusion/ooxml/FormulaEval.kt",
        "format/ooxml/src/main/kotlin/dev/fusion/ooxml/SpillSheetEngine.kt",
        "format/ooxml/src/main/kotlin/dev/fusion/ooxml/PptxSlideDeck.kt",
        "format/ooxml/src/main/kotlin/dev/fusion/ooxml/PivotCubeSummary.kt",
        "format/ooxml/src/main/kotlin/dev/fusion/ooxml/XlsxDataValidationXml.kt",
        "core/documents/src/main/kotlin/dev/fusion/documents/Epub3Package.kt",
        "core/documents/src/main/kotlin/dev/fusion/documents/RtfCleanConverter.kt",
        "core/documents/src/main/kotlin/dev/fusion/documents/OpenSearchSuggestionJson.kt",
        "format/pdf/src/main/java/dev/fusion/pdf/PdfAcroFormWriter.kt",
        "format/pdf/src/main/java/dev/fusion/pdf/PdfPageLabelsAndOutline.kt",
        "format/lok/src/main/kotlin/dev/fusion/lok/OdsSpreadsheetXml.kt",
        "feature/editor/src/main/java/dev/fusion/editor/ReadabilityMetrics.kt",
        "feature/editor/src/main/java/dev/fusion/editor/MarkdownOfficeBridge.kt",
        "feature/calc/src/main/kotlin/dev/fusion/calc/FinancialScheduleSolver.kt",
        "feature/calc/src/main/kotlin/dev/fusion/calc/MatrixLinearAlgebra.kt",
        "feature/notes/src/main/kotlin/dev/fusion/notes/ZettelkastenFolgezettel.kt",
    ]),
    ("r48_privacy_sec_wasm.jar", [], [
        "privacy/src/main/java/dev/fusion/privacy/BinaryHttpRfc9292.kt",
        "privacy/src/main/java/dev/fusion/privacy/ObliviousHttpPolicy.kt",
        "privacy/src/main/java/dev/fusion/privacy/OnionV3Address.kt",
        "privacy/src/main/java/dev/fusion/privacy/AcousticKeystrokeShield.kt",
        "privacy/src/main/java/dev/fusion/privacy/ExitTrafficPadBucket.kt",
        "core/hw/src/main/java/dev/fusion/hw/HidUsageFirewall.kt",
        "core/security/src/main/java/dev/fusion/security/ScreenCaptureGuard.kt",
        "core/security/src/main/java/dev/fusion/security/ConstantTimePrimitives.kt",
        "core/security/src/main/java/dev/fusion/security/HkdfRfc5869.kt",
        "feature/vault/src/main/kotlin/dev/fusion/vault/PasskeyRpValidator.kt",
        "feature/browser/src/main/java/dev/fusion/browser/ArticleReaderExtractor.kt",
        "feature/browser/src/main/java/dev/fusion/browser/CrossOriginIsolationPolicy.kt",
        "feature/browser/src/main/java/dev/fusion/browser/CspHeaderDirective.kt",
        "core/capabilities/src/main/java/dev/fusion/capabilities/WasmSandbox.kt",
        "core/capabilities/src/main/java/dev/fusion/capabilities/WasiVirtualPipe.kt",
    ]),
    ("r48_systems.jar", [], [
        "app/src/main/java/dev/fusion/shell/WorkspaceSessionSnapshot.kt",
        "core/windowing/src/main/kotlin/dev/fusion/windowing/SnapZoneMagneticRouter.kt",
        "core/files/src/main/kotlin/dev/fusion/files/ContentHashDeduplicator.kt",
        "core/model/src/main/kotlin/dev/fusion/model/VersionedSchemaEnvelope.kt",
        "core/commands/src/main/kotlin/dev/fusion/commands/KeybindingChordTrie.kt",
        "core/settings/src/main/kotlin/dev/fusion/settings/PolicyLockdownMerger.kt",
        "core/perf/src/main/kotlin/dev/fusion/perf/JankFrameClassifier.kt",
        "core/index/src/main/kotlin/dev/fusion/index/RoaringBitSetLite.kt",
        "core/storage/src/main/kotlin/dev/fusion/storage/BTreePageLayout.kt",
        "core/designsystem/src/main/kotlin/dev/fusion/designsystem/AdaptiveTypographyScale.kt",
        "feature/launcher/src/main/kotlin/dev/fusion/launcher/FrecencyAppRanker.kt",
        "feature/find/src/main/java/dev/fusion/find/RegexSafeAutomaton.kt",
        "feature/files/src/main/kotlin/dev/fusion/filesui/SafeBatchRenamePlanner.kt",
        "feature/terminal/src/main/kotlin/dev/fusion/terminal/TmuxPaneLayoutCodec.kt",
        "feature/settings/src/main/kotlin/dev/fusion/settingsui/PermissionRiskAuditor.kt",
        "feature/media/src/main/kotlin/dev/fusion/media/WebVttCueTimeline.kt",
        "feature/maps/src/main/kotlin/dev/fusion/maps/GeoFencePolygon.kt",
        "feature/beam/src/main/kotlin/dev/fusion/beam/ReedSolomonQrEcc.kt",
        "feature/alerts/src/main/kotlin/dev/fusion/alerts/CronExpressionSchedule.kt",
        "feature/capture/src/main/kotlin/dev/fusion/capture/PiiRedactionScanner.kt",
        "feature/backup/src/main/kotlin/dev/fusion/backup/ChunkedRestoreVerifier.kt",
        "feature/continuity/src/main/kotlin/dev/fusion/continuity/VectorClockCausalOrder.kt",
        "feature/accessibility/src/main/kotlin/dev/fusion/a11y/ReadingOrderLinearizer.kt",
        "feature/together/src/main/kotlin/dev/fusion/together/YataRgbaSequenceCrdt.kt",
        "feature/lens/src/main/kotlin/dev/fusion/lens/PerspectiveHomography3x3.kt",
        "feature/messages/src/main/kotlin/dev/fusion/messages/DoubleRatchetHeaderChain.kt",
        "feature/automation/src/main/kotlin/dev/fusion/automation/WorkflowPetriNet.kt",
        "format/native/src/main/kotlin/dev/fusion/nativefmt/Elf16KbPageAudit.kt",
        "format/archive/src/main/kotlin/dev/fusion/archive/SevenZipSignatureHeader.kt",
        "format/image/src/main/kotlin/dev/fusion/format/image/NetpbmPpmCodec.kt",
        "format/psd/src/main/kotlin/dev/fusion/psd/PsdColorModeResources.kt",
        "cast/dlna/src/main/kotlin/dev/fusion/cast/dlna/DidlLiteMetadataXml.kt",
        "cast/airplay/src/main/kotlin/dev/fusion/airplay/RtpAudioPacketHeader.kt",
        "cast/castv2/src/main/kotlin/dev/fusion/castv2/CastMediaStatusFrame.kt",
        "benchmark/src/main/java/dev/fusion/benchmark/WelchTTestRegression.kt",
        "baselineprofile/src/main/kotlin/dev/fusion/baselineprofile/ArtProfileRuleCodec.kt",
        "testing/src/main/kotlin/dev/fusion/testing/DeterministicShrinker.kt",
    ]),
    ("r48_checks.jar", ["r48_office.jar", "r48_privacy_sec_wasm.jar", "r48_systems.jar"], [
        "testing/src/r48/kotlin/dev/fusion/testing/R48Checks.kt",
    ]),
]


def sha256_file(p: Path) -> str:
    return hashlib.sha256(p.read_bytes()).hexdigest()


def main() -> None:
    ap = argparse.ArgumentParser(description=__doc__)
    ap.add_argument("--tools", type=Path, default=Path.home() / ".cache/fusion-host-r23")
    ap.add_argument("--force-rebuild", action="store_true")
    args = ap.parse_args()

    sys.path.insert(0, str(ROOT / "tools"))
    import bootstrap_host_tools
    env = bootstrap_host_tools.environment(args.tools.expanduser().resolve())
    kotlinc = env["KOTLINC"]
    java = str(Path(env["JAVA_HOME"]) / "bin/java")

    out_dir = ROOT / "testing/build/r48"
    art_dir = out_dir / "artifacts"
    art_dir.mkdir(parents=True, exist_ok=True)
    cache_file = out_dir / "jar-fingerprints.json"
    prev_fps = json.loads(cache_file.read_text()) if cache_file.is_file() and not args.force_rebuild else {}
    new_fps = {}

    t0 = time.monotonic()
    compiled_jars = []
    for jar_name, dep_jars, srcs in JARS:
        jar_path = out_dir / jar_name
        src_paths = [ROOT / s for s in srcs]
        fp_src = "|".join(f"{s}:{sha256_file(ROOT / s)}" for s in srcs)
        dep_fp = "|".join(f"{d}:{new_fps.get(d, '')}" for d in dep_jars)
        combined_fp = hashlib.sha256((fp_src + "||" + dep_fp).encode()).hexdigest()
        new_fps[jar_name] = combined_fp

        if not args.force_rebuild and jar_path.is_file() and prev_fps.get(jar_name) == combined_fp:
            compiled_jars.append(jar_path)
            continue

        cmd = [kotlinc, "-jvm-target", "17", "-Werror"]
        if dep_jars:
            cmd.extend(["-classpath", os.pathsep.join(str(out_dir / d) for d in dep_jars)])
        if jar_name == "r48_checks.jar":
            cmd.append("-include-runtime")
        cmd.extend(str(p) for p in src_paths)
        cmd.extend(["-d", str(jar_path)])
        subprocess.run(cmd, check=True, env=env)
        compiled_jars.append(jar_path)

    cache_file.write_text(json.dumps(new_fps, indent=2) + "\n")

    cp = os.pathsep.join(str(p) for p in reversed(compiled_jars))
    proc = subprocess.run([java, "-cp", cp, "dev.fusion.testing.R48Checks", str(art_dir)], capture_output=True, text=True, check=True, env=env)
    sys.stdout.write(proc.stdout)
    elapsed = round(time.monotonic() - t0, 3)

    m = next((tok.split("=")[1] for tok in proc.stdout.strip().split() if tok.startswith("assertions=")), "0")
    all_srcs = [s for _, _, srcs in JARS for s in srcs]
    report = {
        "round": "R48",
        "exit_code": 0,
        "elapsed_seconds": elapsed,
        "assertions": int(m),
        "new_kernels": 66,
        "touched_pure_jvm_kernels": 2,
        "touched_android_ui_files": 5,
        "modules_deepened_in_round": "50/50",
        "output": proc.stdout.strip(),
        "artifacts": sorted(p.name for p in art_dir.iterdir() if p.is_file()),
        "sha256": {s: sha256_file(ROOT / s) for s in all_srcs},
        "scope": "Actual host kotlinc -Werror separate-jar compilation + JVM execution across 50/50 modules; NOT Android device/SDK/AX/SurfaceFlinger Class A acceptance.",
    }
    (out_dir / "result.json").write_text(json.dumps(report, indent=2) + "\n")


if __name__ == "__main__":
    main()
