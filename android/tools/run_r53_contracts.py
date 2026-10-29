#!/usr/bin/env python3
"""Host-runnable R53 contract runner (50/50 modules, 96 new pure-JVM kernels + 7 new Compose UI files + 19 TOUCHED files).

Compiles the R53 Phase-1 Recall-6-Method/Media/Core, Office/Publishing/Formats, Tails/HW-30/Wasm-3/Features,
and Phase-2 UI-Ergonomics jars under separate `-Werror` kotlinc invocations with SHA-256 content-fingerprint
caching, then executes `dev.fusion.testing.R53Checks` on JVM 17 and emits 66 real binary & structured
artifacts for independent Python differential validation (`tools/check_r53_reference.py`).
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
    ("r53_recall_6method_media.jar", [], [
        "app/src/main/java/dev/fusion/shell/RecallSessionCoordinator.kt",
        "app/src/main/java/dev/fusion/shell/RecallAutoArchivalPipelineOrchestrator.kt",
        "app/src/main/java/dev/fusion/shell/ShellMultiMonitorDpiScaleRouter.kt",
        "feature/browser/src/main/kotlin/dev/fusion/browser/model/CleanUrlTrackingParamStripper.kt",
        "feature/browser/src/main/kotlin/dev/fusion/browser/model/RecallMethodEscalationRouter.kt",
        "feature/browser/src/main/kotlin/dev/fusion/browser/model/RecallTier25WebRtcRtpDepacketizer.kt",
        "feature/browser/src/main/kotlin/dev/fusion/browser/model/RecallTier5WarcIso28500Archiver.kt",
        "feature/browser/src/main/kotlin/dev/fusion/browser/model/RecallMhtmlMultipartSnapshotter.kt",
        "feature/browser/src/main/kotlin/dev/fusion/browser/model/RecallSrcsetPictureDensitySelector.kt",
        "feature/browser/src/main/kotlin/dev/fusion/browser/model/BrowserStorageAccessPartitionGuard.kt",
        "feature/browser/src/main/kotlin/dev/fusion/browser/model/ClearSiteDataHeaderExecutor.kt",
        "feature/media/src/main/kotlin/dev/fusion/media/RecallAudioSilenceTrackSplitter.kt",
        "feature/media/src/main/kotlin/dev/fusion/media/Mp3MpegFrameDurationXingScanner.kt",
        "feature/media/src/main/kotlin/dev/fusion/media/OggVorbisCommentChapterEmbedder.kt",
        "feature/media/src/main/kotlin/dev/fusion/media/MatroskaWebmEbmlClusterProbe.kt",
        "feature/media/src/main/kotlin/dev/fusion/media/LoudnessEbuR128MomentaryMeter.kt",
        "feature/capture/src/main/kotlin/dev/fusion/capture/RecallViewportCursorAnnotationCompositor.kt",
        "feature/capture/src/main/kotlin/dev/fusion/capture/CaptureSegmentConcatManifestBuilder.kt",
        "feature/capture/src/main/kotlin/dev/fusion/capture/AudioCapturePolicyClassifier.kt",
        "core/files/src/main/kotlin/dev/fusion/files/RecallFolderDelineator.kt",
        "core/files/src/main/kotlin/dev/fusion/files/RecallVaultQuotaLifecyclePruner.kt",
        "core/files/src/main/kotlin/dev/fusion/files/RecallCdxjReplayIndexCodec.kt",
        "core/index/src/main/kotlin/dev/fusion/index/RecallPerceptualDHashDeduplicator.kt",
        "core/index/src/main/kotlin/dev/fusion/index/PositionalPhraseInvertedIndex.kt",
        "core/storage/src/main/kotlin/dev/fusion/storage/ContentAddressedBlobCasStore.kt",
        "core/perf/src/main/kotlin/dev/fusion/perf/RingBufferLockFreeTelemetry.kt",
        "core/model/src/main/kotlin/dev/fusion/model/Rfc8785JsonCanonicalizer.kt",
        "core/model/src/main/kotlin/dev/fusion/model/DeterministicUlidGenerator.kt",
        "core/settings/src/main/kotlin/dev/fusion/settings/RecallUserPreferencesSchema.kt",
    ]),
    ("r53_office_publishing_formats.jar", [], [
        "format/ooxml/src/main/kotlin/dev/fusion/ooxml/BoundedXml.kt",
        "format/ooxml/src/main/kotlin/dev/fusion/ooxml/ColLetters.kt",
        "format/ooxml/src/main/kotlin/dev/fusion/ooxml/SheetRange.kt",
        "format/ooxml/src/main/kotlin/dev/fusion/ooxml/CellErr.kt",
        "format/ooxml/src/main/kotlin/dev/fusion/ooxml/FormulaEval.kt",
        "format/ooxml/src/main/kotlin/dev/fusion/ooxml/SpillSheetEngine.kt",
        "format/ooxml/src/main/kotlin/dev/fusion/ooxml/DocxTableLayoutGridEngine.kt",
        "format/ooxml/src/main/kotlin/dev/fusion/ooxml/DocxEquationOmmlBuilder.kt",
        "format/ooxml/src/main/kotlin/dev/fusion/ooxml/XlsxPivotCacheRecordsWriter.kt",
        "format/ooxml/src/main/kotlin/dev/fusion/ooxml/PptxMasterThemeColorSchemeWriter.kt",
        "format/lok/src/main/java/dev/fusion/lok/OdfPresentationOdpBuilder.kt",
        "format/lok/src/main/java/dev/fusion/lok/OdfSpreadsheetFormulaTranslator.kt",
        "format/lok/src/main/java/dev/fusion/lok/LokTileInvalidationCoalescer.kt",
        "format/pdf/src/main/java/dev/fusion/format/pdf/PdfDigitalSignatureByteRangePlanner.kt",
        "format/pdf/src/main/java/dev/fusion/format/pdf/PdfTextColumnReflowExtractor.kt",
        "format/pdf/src/main/java/dev/fusion/format/pdf/PdfOptionalContentLayersWriter.kt",
        "format/image/src/main/kotlin/dev/fusion/format/image/PngChunkSanitizerAndCrcCodec.kt",
        "format/image/src/main/kotlin/dev/fusion/format/image/JpegExifAppMarkerSanitizer.kt",
        "format/archive/src/main/kotlin/dev/fusion/archive/WarcGzipConcatMemberInspector.kt",
        "format/archive/src/main/kotlin/dev/fusion/archive/EpubCfiFragmentLocator.kt",
        "format/native/src/main/kotlin/dev/fusion/nativefmt/Gltf2BinaryGlbChunkInspector.kt",
        "format/psd/src/main/kotlin/dev/fusion/psd/PsdImageResourceIccExtractor.kt",
        "core/documents/src/main/kotlin/dev/fusion/documents/WriterSectionColumnsPageLayout.kt",
        "core/documents/src/main/kotlin/dev/fusion/documents/WriterTableOfFiguresIndexBuilder.kt",
        "feature/editor/src/main/java/dev/fusion/editor/WriterTrackChangesAcceptRejectEngine.kt",
        "feature/editor/src/main/java/dev/fusion/editor/SyntaxHighlightLexicalScanner.kt",
        "feature/calc/src/main/kotlin/dev/fusion/calc/SpreadsheetGoalSeekBrentSolver.kt",
        "feature/calc/src/main/kotlin/dev/fusion/calc/MonteCarloSensitivityTableRunner.kt",
        "feature/notes/src/main/kotlin/dev/fusion/notes/NotesCanvasInfiniteGraphLayout.kt",
        "feature/notes/src/main/kotlin/dev/fusion/notes/MarkdownFootnoteAndCalloutParser.kt",
        "feature/terminal/src/main/kotlin/dev/fusion/terminal/AnsiSgrTruecolorScrollbackBuffer.kt",
        "feature/terminal/src/main/kotlin/dev/fusion/terminal/TerminalOsc52ClipboardSanitizer.kt",
    ]),
    ("r53_tails_hw30_wasm3_features.jar", [], [
        "core/capabilities/src/main/java/dev/fusion/capabilities/WasmSandbox.kt",
        "core/capabilities/src/main/java/dev/fusion/capabilities/WasmMultiMemoryIsolationVerifier.kt",
        "core/capabilities/src/main/java/dev/fusion/capabilities/SeccompBpfSyscallAllowlistPlanner.kt",
        "privacy/src/main/kotlin/dev/fusion/privacy/TorConfluxMultipathScheduler.kt",
        "privacy/src/main/kotlin/dev/fusion/privacy/TailsMetadataAnonymizer.kt",
        "privacy/src/main/kotlin/dev/fusion/privacy/OnionServiceClientAuthDescriptorCodec.kt",
        "core/hw/src/main/kotlin/dev/fusion/hw/TempestSubpixelEmsecDitherer.kt",
        "core/hw/src/main/kotlin/dev/fusion/hw/BleHogpKeystrokeFirewall.kt",
        "core/hw/src/main/kotlin/dev/fusion/hw/VerifiedBootVbmetaDigestAuditor.kt",
        "core/hw/src/main/kotlin/dev/fusion/hw/AmbientLightKeystrokeShadowGuard.kt",
        "core/hw/src/main/kotlin/dev/fusion/hw/DramRowhammerRefreshMonitor.kt",
        "core/security/src/main/kotlin/dev/fusion/security/Slip39MnemonicShareChecksumCodec.kt",
        "core/security/src/main/kotlin/dev/fusion/security/ChaCha20Rfc8439StreamBlock.kt",
        "feature/vault/src/main/kotlin/dev/fusion/vault/VaultEmergencyDuressDecoyPlanner.kt",
        "feature/find/src/main/kotlin/dev/fusion/find/TrigramFuzzyCommandMatcher.kt",
        "feature/maps/src/main/kotlin/dev/fusion/maps/DouglasPeuckerTrackSimplifier.kt",
        "feature/beam/src/main/kotlin/dev/fusion/beam/MdnsServiceTxtPrivacySanitizer.kt",
        "feature/alerts/src/main/kotlin/dev/fusion/alerts/SensitiveOtpNotificationRedactor.kt",
        "feature/backup/src/main/kotlin/dev/fusion/backup/RabinKarpDeltaPatchGenerator.kt",
        "feature/continuity/src/main/kotlin/dev/fusion/continuity/ClipboardAutoExpireSanitizer.kt",
        "feature/together/src/main/kotlin/dev/fusion/together/CausalTreeAwarenessPresenceRoster.kt",
        "feature/lens/src/main/kotlin/dev/fusion/lens/DocumentSkewQuadRectifier.kt",
        "feature/messages/src/main/kotlin/dev/fusion/messages/SealedCertificateBucketEnvelope.kt",
        "feature/automation/src/main/kotlin/dev/fusion/automation/CronNextFireScheduleEvaluator.kt",
        "cast/dlna/src/main/kotlin/dev/fusion/cast/dlna/UpnpSoapActionEnvelopeSanitizer.kt",
        "cast/airplay/src/main/kotlin/dev/fusion/airplay/RaopRtpTimingSyncCalculator.kt",
        "cast/castv2/src/main/kotlin/dev/fusion/castv2/CastV2FramingHeaderCodec.kt",
        "benchmark/src/main/java/dev/fusion/benchmark/MannWhitneyNonParametricGate.kt",
        "baselineprofile/src/main/kotlin/dev/fusion/baselineprofile/StartupDexLayoutHotnessScorer.kt",
        "testing/src/main/kotlin/dev/fusion/testing/DeterministicFuzzShrinker.kt",
        "testing/src/main/kotlin/dev/fusion/testing/DifferentialBytecodeMutationOracle.kt",
    ]),
    ("r53_ui_ergonomics_phase2.jar", [], [
        "core/windowing/src/main/kotlin/dev/fusion/windowing/RightClickContextMenuGeometry.kt",
        "core/windowing/src/main/kotlin/dev/fusion/windowing/ContextMenuSubmenuCascadeGeometry.kt",
        "core/windowing/src/main/kotlin/dev/fusion/windowing/PointerSafeTriangleSubmenuFilter.kt",
        "core/windowing/src/main/kotlin/dev/fusion/windowing/DesktopWorkAreaInsetPartitioner.kt",
        "core/designsystem/src/main/kotlin/dev/fusion/designsystem/FusionViewModeVectorIcons.kt",
        "core/designsystem/src/main/kotlin/dev/fusion/designsystem/ViewModePixelGridCrispHinting.kt",
        "core/designsystem/src/main/kotlin/dev/fusion/designsystem/TranslucentGlassBackdropContrastGuard.kt",
        "core/commands/src/main/kotlin/dev/fusion/commands/UndoRedoStack.kt",
        "core/commands/src/main/kotlin/dev/fusion/commands/StackedUndoNotificationDeck.kt",
        "core/commands/src/main/kotlin/dev/fusion/commands/UndoDeckInteractiveFanController.kt",
        "core/commands/src/main/kotlin/dev/fusion/commands/ContextMenuMnemonicKeyNavigator.kt",
        "feature/files/src/main/java/dev/fusion/filesui/DualPaneContextMenuModel.kt",
        "feature/files/src/main/kotlin/dev/fusion/filesui/MillerColumnsTreeNavigator.kt",
        "feature/files/src/main/kotlin/dev/fusion/filesui/DualPaneViewModeSyncStore.kt",
        "feature/files/src/main/kotlin/dev/fusion/filesui/ContextMenuSelectionVerbResolver.kt",
        "feature/launcher/src/main/kotlin/dev/fusion/launcher/ShelfAppCategoryColumnsModel.kt",
        "feature/settings/src/main/kotlin/dev/fusion/settingsui/UiErgonomicsPreferencesValidator.kt",
        "feature/accessibility/src/main/kotlin/dev/fusion/a11y/ReducedTransparencyAndMotionAdapter.kt",
        "feature/browser/src/main/kotlin/dev/fusion/browser/model/BrowserLinkMediaContextMenuPlanner.kt",
        "feature/media/src/main/kotlin/dev/fusion/media/MediaCollectionViewModeGrouper.kt",
        "feature/vault/src/main/kotlin/dev/fusion/vault/VaultItemViewModeOrganizer.kt",
        "feature/notes/src/main/kotlin/dev/fusion/notes/NotesCollectionViewModeSorter.kt",
        "feature/editor/src/main/java/dev/fusion/editor/EditorSelectionContextMenuPlanner.kt",
    ]),
    ("r53_checks.jar", [
        "r53_recall_6method_media.jar",
        "r53_office_publishing_formats.jar",
        "r53_tails_hw30_wasm3_features.jar",
        "r53_ui_ergonomics_phase2.jar",
    ], [
        "testing/src/r53/kotlin/dev/fusion/testing/R53Checks.kt",
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

    out_dir = ROOT / "testing/build/r53"
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
        if jar_name == "r53_checks.jar":
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
        [java, "-cp", cp, "dev.fusion.testing.R53Checks", str(art_dir)],
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
        "round": "R53",
        "exit_code": 0,
        "elapsed_seconds": elapsed,
        "assertions": int(m),
        "new_pure_jvm_kernels": 96,
        "new_compose_ui_files": 7,
        "touched_pure_jvm_kernels": 5,
        "touched_android_ui_files": 14,
        "modules_deepened_in_round": "50/50",
        "output": proc.stdout.strip(),
        "artifacts": sorted(p.name for p in art_dir.iterdir() if p.is_file()),
        "sha256": {s: sha256_file(ROOT / s) for s in all_srcs},
        "scope": "Actual host kotlinc -Werror separate-jar compilation + JVM execution across 50/50 modules; NOT Android device/SDK/AX/SurfaceFlinger Class A acceptance.",
    }
    (out_dir / "result.json").write_text(json.dumps(report, indent=2) + "\n")


if __name__ == "__main__":
    main()
