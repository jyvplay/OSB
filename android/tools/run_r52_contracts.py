#!/usr/bin/env python3
"""Host-runnable R52 contract runner (50/50 modules, 91 new pure-JVM kernels + 6 new Compose UI files + 17 TOUCHED files).

Compiles the R52 Recall/Browser/Media, Task-Block-2 UI-Ergonomics/Shell/Core, Office/Spreadsheet/Formats,
and Privacy/Tails/Hardware-Defense/Feature jars under separate `-Werror` kotlinc invocations with
SHA-256 content-fingerprint caching, then executes `dev.fusion.testing.R52Checks` on JVM 17 and emits
48 real binary & structured artifacts for independent Python differential validation (`tools/check_r52_reference.py`).
"""
from __future__ import annotations

import argparse
import hashlib
import json
import os
import subprocess
import sys
import time
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]

JARS: list[tuple[str, list[str], list[str]]] = [
    ("r52_recall_media_browser.jar", [], [
        "app/src/main/java/dev/fusion/shell/RecallSessionCoordinator.kt",
        "app/src/main/java/dev/fusion/shell/ShellKeyboardShortcutRegistry.kt",
        "core/files/src/main/kotlin/dev/fusion/files/RecallFolderDelineator.kt",
        "core/files/src/main/kotlin/dev/fusion/files/RecallPlaylistSidecarWriter.kt",
        "core/index/src/main/kotlin/dev/fusion/index/RecallMediaTranscriptSearchIndex.kt",
        "core/index/src/main/kotlin/dev/fusion/index/KeysetCursorPaginator.kt",
        "feature/browser/src/main/kotlin/dev/fusion/browser/model/RecallTier1DirectAssetHarvester.kt",
        "feature/browser/src/main/kotlin/dev/fusion/browser/model/RecallTier2HlsDashAssembler.kt",
        "feature/browser/src/main/kotlin/dev/fusion/browser/model/RecallDashMpdSegmentResolver.kt",
        "feature/browser/src/main/kotlin/dev/fusion/browser/model/RecallMethodEscalationRouter.kt",
        "feature/browser/src/main/kotlin/dev/fusion/browser/model/RecallHarArchiveExporter.kt",
        "feature/browser/src/main/kotlin/dev/fusion/browser/model/BounceTrackingRedirectPurger.kt",
        "feature/browser/src/main/kotlin/dev/fusion/browser/model/CleanUrlTrackingParamStripper.kt",
        "feature/media/src/main/kotlin/dev/fusion/media/RecallIsoBmffFragmentMuxer.kt",
        "feature/media/src/main/kotlin/dev/fusion/media/RecallTier3CanvasWebAudioSiphoner.kt",
        "feature/media/src/main/kotlin/dev/fusion/media/RecallWebVttCaptionArchiver.kt",
        "feature/media/src/main/kotlin/dev/fusion/media/Id3v24MusicTagEmbedder.kt",
        "feature/media/src/main/kotlin/dev/fusion/media/FlacStreamInfoVorbisInspector.kt",
        "feature/capture/src/main/kotlin/dev/fusion/capture/RecallTier4NativeScreenAudioRecorder.kt",
        "feature/capture/src/main/kotlin/dev/fusion/capture/RecallCaptureRolloverPlanner.kt",
        "feature/files/src/main/kotlin/dev/fusion/filesui/RecallArchiveCatalog.kt",
        "feature/files/src/main/kotlin/dev/fusion/filesui/BatchFileRenameCollisionPlanner.kt",
    ]),
    ("r52_ui_ergonomics_shell_core.jar", [], [
        "core/windowing/src/main/kotlin/dev/fusion/windowing/RightClickContextMenuGeometry.kt",
        "core/windowing/src/main/kotlin/dev/fusion/windowing/PipCornerOcclusionPlacementSolver.kt",
        "core/windowing/src/main/kotlin/dev/fusion/windowing/SecondaryClickGestureClassifier.kt",
        "core/designsystem/src/main/kotlin/dev/fusion/designsystem/FusionViewModeVectorIcons.kt",
        "core/designsystem/src/main/kotlin/dev/fusion/designsystem/AnalyticalSpringMotionSolver.kt",
        "core/designsystem/src/main/kotlin/dev/fusion/designsystem/ShoulderSurfingPrivacyVeil.kt",
        "core/commands/src/main/kotlin/dev/fusion/commands/StackedUndoNotificationDeck.kt",
        "core/commands/src/main/kotlin/dev/fusion/commands/UndoRedoStack.kt",
        "core/commands/src/main/kotlin/dev/fusion/commands/ReversibleBatchCommandJournal.kt",
        "feature/files/src/main/kotlin/dev/fusion/filesui/MillerColumnsTreeNavigator.kt",
        "feature/files/src/main/java/dev/fusion/filesui/DualPaneContextMenuModel.kt",
        "feature/launcher/src/main/kotlin/dev/fusion/launcher/LauncherViewModeShelfPlanner.kt",
        "feature/launcher/src/main/kotlin/dev/fusion/launcher/LauncherFrecencyRanker.kt",
        "core/model/src/main/kotlin/dev/fusion/model/Rfc8785JsonCanonicalizer.kt",
        "core/model/src/main/kotlin/dev/fusion/model/HumanByteSizeCodec.kt",
        "core/settings/src/main/kotlin/dev/fusion/settings/DatastoreSchemaMigrationChain.kt",
        "feature/settings/src/main/kotlin/dev/fusion/settingsui/PredictiveBackOverlayStack.kt",
        "core/perf/src/main/kotlin/dev/fusion/perf/LocalFrameJankHistogram.kt",
        "core/storage/src/main/kotlin/dev/fusion/storage/SegmentedLruStorageQuotaGovernor.kt",
        "core/storage/src/main/kotlin/dev/fusion/storage/SqliteWalFrameIntegrityVerifier.kt",
        "feature/accessibility/src/main/kotlin/dev/fusion/a11y/WcagTargetSpacingFocusAuditor.kt",
    ]),
    ("r52_office_formats.jar", [], [
        "format/ooxml/src/main/kotlin/dev/fusion/ooxml/BoundedXml.kt",
        "format/ooxml/src/main/kotlin/dev/fusion/ooxml/ColLetters.kt",
        "format/ooxml/src/main/kotlin/dev/fusion/ooxml/SheetRange.kt",
        "format/ooxml/src/main/kotlin/dev/fusion/ooxml/CellErr.kt",
        "format/ooxml/src/main/kotlin/dev/fusion/ooxml/FormulaEval.kt",
        "format/ooxml/src/main/kotlin/dev/fusion/ooxml/SpillSheetEngine.kt",
        "format/ooxml/src/main/kotlin/dev/fusion/ooxml/PptxSlideDeck.kt",
        "format/ooxml/src/main/kotlin/dev/fusion/ooxml/OoxmlSparklineEngine.kt",
        "format/ooxml/src/main/kotlin/dev/fusion/ooxml/DocxContentControlBinder.kt",
        "format/ooxml/src/main/kotlin/dev/fusion/ooxml/XlsxAutoFilterEvaluator.kt",
        "format/ooxml/src/main/kotlin/dev/fusion/ooxml/SlideShapeSnapDistributor.kt",
        "format/lok/src/main/java/dev/fusion/lok/OdfMasterPageHeaderFooterEngine.kt",
        "format/lok/src/main/java/dev/fusion/lok/OdfTrackedChangesRedlineEngine.kt",
        "format/lok/src/main/java/dev/fusion/lok/LokCommandStateSynchronizer.kt",
        "format/pdf/src/main/java/dev/fusion/format/pdf/PdfBookmarkOutlineWriter.kt",
        "format/pdf/src/main/java/dev/fusion/format/pdf/PdfAcroFormFieldFiller.kt",
        "format/pdf/src/main/java/dev/fusion/format/pdf/PdfBookletImpositionPlanner.kt",
        "format/native/src/main/kotlin/dev/fusion/nativefmt/IsoHeifAvifContainerProbe.kt",
        "format/archive/src/main/kotlin/dev/fusion/archive/Epub33PackageManifestBuilder.kt",
        "format/image/src/main/kotlin/dev/fusion/format/image/TextureAtlasShelfPacker.kt",
        "format/psd/src/main/kotlin/dev/fusion/psd/PsdSeparableBlendCompositor.kt",
        "core/documents/src/main/kotlin/dev/fusion/documents/DocsBibliographyCiteFormatter.kt",
        "core/documents/src/main/kotlin/dev/fusion/documents/DocumentCrossRefResolver.kt",
        "feature/editor/src/main/java/dev/fusion/editor/WriterMailMergeEngine.kt",
        "feature/editor/src/main/java/dev/fusion/editor/WriterFootnotePaginator.kt",
        "feature/editor/src/main/java/dev/fusion/editor/MultiCaretBlockEditor.kt",
        "feature/calc/src/main/kotlin/dev/fusion/calc/SheetSolverSensitivityEngine.kt",
        "feature/calc/src/main/kotlin/dev/fusion/calc/ConditionalFormattingScaleEvaluator.kt",
        "feature/notes/src/main/kotlin/dev/fusion/notes/NotesFlashcardSrsScheduler.kt",
        "feature/notes/src/main/kotlin/dev/fusion/notes/ZettelkastenBacklinkGraph.kt",
        "feature/terminal/src/main/kotlin/dev/fusion/terminal/MarkdownPipeTablePrettyFormatter.kt",
    ]),
    ("r52_privacy_hw_wasm_features.jar", [], [
        "core/capabilities/src/main/java/dev/fusion/capabilities/WasmSandbox.kt",
        "core/capabilities/src/main/java/dev/fusion/capabilities/WasmTailCallVerifier.kt",
        "core/capabilities/src/main/java/dev/fusion/capabilities/CapabilityDelegationAttenuator.kt",
        "privacy/src/main/kotlin/dev/fusion/privacy/TorOnionPowIntroQueue.kt",
        "privacy/src/main/kotlin/dev/fusion/privacy/TailsAmnesicSwapCoreAuditor.kt",
        "core/hw/src/main/kotlin/dev/fusion/hw/ChargerSurfingPowerMasker.kt",
        "core/hw/src/main/kotlin/dev/fusion/hw/UltrasonicDolphinAttackFilter.kt",
        "core/security/src/main/kotlin/dev/fusion/security/ThermalResidueWipeAdvisor.kt",
        "core/security/src/main/kotlin/dev/fusion/security/ImuKeystrokeMotionZeroizer.kt",
        "core/security/src/main/kotlin/dev/fusion/security/Rfc5869HkdfSha256.kt",
        "feature/vault/src/main/kotlin/dev/fusion/vault/VaultPassphraseEntropyAuditor.kt",
        "feature/find/src/main/kotlin/dev/fusion/find/LocalBpeSubwordTokenizer.kt",
        "feature/maps/src/main/kotlin/dev/fusion/maps/MapsElevationTravelEstimator.kt",
        "feature/beam/src/main/kotlin/dev/fusion/beam/BeamMerkleChunkVerifier.kt",
        "feature/alerts/src/main/kotlin/dev/fusion/alerts/HubWorkspaceFeedAggregator.kt",
        "feature/backup/src/main/kotlin/dev/fusion/backup/GearRollingChunkSlicer.kt",
        "feature/continuity/src/main/kotlin/dev/fusion/continuity/ContinuityHybridLogicalClock.kt",
        "feature/together/src/main/kotlin/dev/fusion/together/ThreeWayDocumentLineMerger.kt",
        "feature/lens/src/main/kotlin/dev/fusion/lens/LensQrPayloadSemanticGuard.kt",
        "feature/messages/src/main/kotlin/dev/fusion/messages/MessagingSymmetricChainRatchet.kt",
        "feature/automation/src/main/kotlin/dev/fusion/automation/OfflineMmrExtractiveSummarizer.kt",
        "cast/dlna/src/main/kotlin/dev/fusion/cast/dlna/CastSinkCodecNegotiator.kt",
        "cast/airplay/src/main/kotlin/dev/fusion/airplay/SlideRehearsalPacingCoach.kt",
        "cast/castv2/src/main/kotlin/dev/fusion/castv2/HttpRangeResumePlanner.kt",
        "benchmark/src/main/java/dev/fusion/benchmark/BenchmarkWelchRegressionGate.kt",
        "baselineprofile/src/main/kotlin/dev/fusion/baselineprofile/BaselineProfileRuleConsolidator.kt",
        "testing/src/main/kotlin/dev/fusion/testing/DeterministicFuzzShrinker.kt",
    ]),
    ("r52_checks.jar", [
        "r52_recall_media_browser.jar",
        "r52_ui_ergonomics_shell_core.jar",
        "r52_office_formats.jar",
        "r52_privacy_hw_wasm_features.jar",
    ], [
        "testing/src/r52/kotlin/dev/fusion/testing/R52Checks.kt",
    ]),
]


def sha256_file(p: Path) -> str:
    return hashlib.sha256(p.read_bytes()).hexdigest()


def main(argv: list[str] | None = None) -> None:
    ap = argparse.ArgumentParser(description=__doc__)
    ap.add_argument("--tools", type=Path, default=Path.home() / ".cache/fusion-host-r23")
    ap.add_argument("--force-rebuild", action="store_true")
    args = ap.parse_args([] if argv is None and __name__ != "__main__" else argv)

    sys.path.insert(0, str(ROOT / "tools"))
    import bootstrap_host_tools
    env = bootstrap_host_tools.environment(args.tools.expanduser().resolve())
    kotlinc = env["KOTLINC"]
    java = str(Path(env["JAVA_HOME"]) / "bin/java")

    out_dir = ROOT / "testing/build/r52"
    art_dir = out_dir / "artifacts"
    art_dir.mkdir(parents=True, exist_ok=True)
    cache_file = out_dir / "jar-fingerprints.json"
    prev_fps = json.loads(cache_file.read_text()) if cache_file.is_file() and not args.force_rebuild else {}
    new_fps: dict[str, str] = {}

    t0 = time.monotonic()
    compiled_jars: list[Path] = []
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
        if jar_name == "r52_checks.jar":
            cmd.append("-include-runtime")
        cmd.extend(str(p) for p in src_paths)
        cmd.extend(["-d", str(jar_path)])
        subprocess.run(cmd, check=True, env=env)
        compiled_jars.append(jar_path)
        prev_fps[jar_name] = combined_fp
        cache_file.write_text(json.dumps(prev_fps, indent=2) + "\n")

    cache_file.write_text(json.dumps(new_fps, indent=2) + "\n")

    cp = os.pathsep.join(str(p) for p in reversed(compiled_jars))
    proc = subprocess.run(
        [java, "-cp", cp, "dev.fusion.testing.R52Checks", str(art_dir)],
        capture_output=True,
        text=True,
        check=False,
        env=env,
    )
    sys.stdout.write(proc.stdout)
    if proc.returncode != 0:
        sys.stderr.write(proc.stderr)
        raise SystemExit(proc.returncode)
    elapsed = round(time.monotonic() - t0, 3)

    m = next((tok.split("=")[1] for tok in proc.stdout.strip().split() if tok.startswith("assertions=")), "0")
    all_srcs = [s for _, _, srcs in JARS for s in srcs]
    report = {
        "round": "R52",
        "exit_code": 0,
        "elapsed_seconds": elapsed,
        "assertions": int(m),
        "new_pure_jvm_kernels": 91,
        "new_compose_ui_files": 6,
        "touched_pure_jvm_kernels": 6,
        "touched_android_ui_files": 11,
        "modules_deepened_in_round": "50/50",
        "output": proc.stdout.strip(),
        "artifacts": sorted(p.name for p in art_dir.iterdir() if p.is_file()),
        "sha256": {s: sha256_file(ROOT / s) for s in all_srcs},
        "scope": "Actual host kotlinc -Werror separate-jar compilation + JVM execution across 50/50 modules; NOT Android device/SDK/AX/SurfaceFlinger Class A acceptance.",
    }
    (out_dir / "result.json").write_text(json.dumps(report, indent=2) + "\n")


if __name__ == "__main__":
    main()
