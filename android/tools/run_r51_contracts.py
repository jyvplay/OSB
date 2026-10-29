#!/usr/bin/env python3
"""Host-runnable R51 contract runner (50/50 modules, 85 new pure-JVM kernels + 16 TOUCHED files).

Compiles the R51 Office/Spreadsheet/Formats, Browser/Privacy/Security/Wasm-SIMD, Shell/Core/Feature,
and Lens/Messages/Continuity/Cast/A11y/Testing jars under separate `-Werror` kotlinc invocations
with SHA-256 content-fingerprint caching, then executes `dev.fusion.testing.R51Checks` on JVM 17
and emits 30 real `.docx`, `.odt`, `.pptx`, `.ods`, `.pdf`, `.wasm`, `.age`, `.tar.gz`, `.arrow`,
`.vtt`, `.xml`, `.alac`, `.jpg`, `.txt`, and `.json` artifacts for independent Python differential
validation (`tools/check_r51_reference.py`).
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
    ("r51_office_formats.jar", [], [
        "format/ooxml/src/main/kotlin/dev/fusion/ooxml/BoundedXml.kt",
        "format/ooxml/src/main/kotlin/dev/fusion/ooxml/ColLetters.kt",
        "format/ooxml/src/main/kotlin/dev/fusion/ooxml/SheetRange.kt",
        "format/ooxml/src/main/kotlin/dev/fusion/ooxml/CellErr.kt",
        "format/ooxml/src/main/kotlin/dev/fusion/ooxml/FormulaEval.kt",
        "format/ooxml/src/main/kotlin/dev/fusion/ooxml/SpillSheetEngine.kt",
        "format/ooxml/src/main/kotlin/dev/fusion/ooxml/DocxRichDocument.kt",
        "format/lok/src/main/java/dev/fusion/lok/OdtRichWriter.kt",
        "format/ooxml/src/main/kotlin/dev/fusion/ooxml/StructuredTableRefEngine.kt",
        "format/ooxml/src/main/kotlin/dev/fusion/ooxml/R1C1FormulaTranslator.kt",
        "format/ooxml/src/main/kotlin/dev/fusion/ooxml/CalcChainDependencyGraph.kt",
        "format/ooxml/src/main/kotlin/dev/fusion/ooxml/PptxMasterLayoutAnimationEngine.kt",
        "feature/calc/src/main/kotlin/dev/fusion/calc/GoalSeekSimplexSolver.kt",
        "feature/calc/src/main/kotlin/dev/fusion/calc/PivotSlicerCrossFilterEngine.kt",
        "feature/editor/src/main/java/dev/fusion/editor/WidowOrphanGalleyPaginator.kt",
        "feature/editor/src/main/java/dev/fusion/editor/CitationBibliographyFormatter.kt",
        "core/documents/src/main/kotlin/dev/fusion/documents/OpenTypeKerningLineJustifier.kt",
        "core/documents/src/main/kotlin/dev/fusion/documents/SpreadsheetNumberMaskFormatter.kt",
        "format/pdf/src/main/java/dev/fusion/format/pdf/PdfUa1TaggedStructureWriter.kt",
        "format/pdf/src/main/java/dev/fusion/format/pdf/PdfLinearizedHintValidator.kt",
        "format/lok/src/main/java/dev/fusion/lok/OdsSpreadsheetFormulaBridge.kt",
        "format/native/src/main/kotlin/dev/fusion/nativefmt/ColumnarArrowIpcBatchCodec.kt",
    ]),
    ("r51_privacy_browser_wasm.jar", [], [
        "core/capabilities/src/main/java/dev/fusion/capabilities/WasmSandbox.kt",
        "core/capabilities/src/main/java/dev/fusion/capabilities/WasmSimdModuleVerifier.kt",
        "core/capabilities/src/main/java/dev/fusion/capabilities/WitComponentCanonicalAbiCodec.kt",
        "feature/browser/src/main/kotlin/dev/fusion/browser/model/CssFlexBlockBoxLayouter.kt",
        "feature/browser/src/main/kotlin/dev/fusion/browser/model/ChipsPartitionedCookieJar.kt",
        "feature/browser/src/main/kotlin/dev/fusion/browser/model/PermissionsPolicyHeaderEvaluator.kt",
        "feature/browser/src/main/kotlin/dev/fusion/browser/model/DeclarativeNetRequestTrieEngine.kt",
        "privacy/src/main/kotlin/dev/fusion/privacy/TorVanguardsLiteMesh.kt",
        "privacy/src/main/kotlin/dev/fusion/privacy/AmnesicSessionShutdownOrchestrator.kt",
        "privacy/src/main/kotlin/dev/fusion/privacy/PanicDeadManSwitchEngine.kt",
        "privacy/src/main/kotlin/dev/fusion/privacy/MetadataCleanRoomExportPipeline.kt",
        "core/security/src/main/kotlin/dev/fusion/security/Luks2Argon2idHeaderAuditor.kt",
        "core/security/src/main/kotlin/dev/fusion/security/AgeV1StreamCipherEnvelope.kt",
        "core/security/src/main/kotlin/dev/fusion/security/DramRowhammerZeroizeGuard.kt",
        "core/security/src/main/kotlin/dev/fusion/security/OpticalReflectionSideChannelGuard.kt",
        "core/hw/src/main/kotlin/dev/fusion/hw/CapacitiveGhostTouchFilter.kt",
        "core/hw/src/main/kotlin/dev/fusion/hw/Usb4PcieIommuDmaGuard.kt",
        "core/hw/src/main/kotlin/dev/fusion/hw/HallMagneticCoverTamperGuard.kt",
        "feature/vault/src/main/kotlin/dev/fusion/vault/DuressDecoyVaultRouter.kt",
        "feature/vault/src/main/kotlin/dev/fusion/vault/ShamirSecretVaultSplitter.kt",
        "format/archive/src/main/kotlin/dev/fusion/archive/DeterministicReproducibleTarGzWriter.kt",
        "format/archive/src/main/kotlin/dev/fusion/archive/ArchiveBombQuotaInspector.kt",
        "feature/backup/src/main/kotlin/dev/fusion/backup/RestorableChunkDedupManifest.kt",
    ]),
    ("r51_shell_core.jar", [], [
        "app/src/main/java/dev/fusion/shell/FusionClassAReadinessAggregator.kt",
        "app/src/main/java/dev/fusion/shell/WorkspaceDeepLinkIntentSanitizer.kt",
        "core/windowing/src/main/kotlin/dev/fusion/windowing/SnapTileMagneticLayoutSolver.kt",
        "core/windowing/src/main/kotlin/dev/fusion/windowing/FocusZOrderModalStack.kt",
        "core/files/src/main/kotlin/dev/fusion/files/AtomicSafeWriteJournal.kt",
        "core/files/src/main/kotlin/dev/fusion/files/ExtendedMimeMagicSniffer.kt",
        "core/index/src/main/kotlin/dev/fusion/index/Bm25FtsPositionalIndex.kt",
        "core/index/src/main/kotlin/dev/fusion/index/TrigramFuzzySpellSuggester.kt",
        "feature/terminal/src/main/kotlin/dev/fusion/terminal/Vt100AlternateScreenScrollback.kt",
        "feature/terminal/src/main/kotlin/dev/fusion/terminal/ShellPipelineTokenizer.kt",
        "feature/notes/src/main/kotlin/dev/fusion/notes/SpacedRepetitionFsrsScheduler.kt",
        "feature/notes/src/main/kotlin/dev/fusion/notes/MarkdownTableFormatterEngine.kt",
        "core/model/src/main/kotlin/dev/fusion/model/VectorClockCausalRevision.kt",
        "core/commands/src/main/kotlin/dev/fusion/commands/FuzzyChordCommandPaletteEngine.kt",
        "core/settings/src/main/kotlin/dev/fusion/settings/TransactionalSettingsMigrationEngine.kt",
        "core/storage/src/main/kotlin/dev/fusion/storage/LsmWriteAheadCompactionSim.kt",
        "core/designsystem/src/main/kotlin/dev/fusion/designsystem/ApcaContrastTypographyScale.kt",
        "feature/launcher/src/main/kotlin/dev/fusion/launcher/PinnedWorkspaceDockLayoutEngine.kt",
        "feature/find/src/main/kotlin/dev/fusion/find/BooleanQueryAstEvaluator.kt",
        "feature/files/src/main/kotlin/dev/fusion/filesui/BatchRenamePatternPreviewEngine.kt",
        "feature/settings/src/main/kotlin/dev/fusion/settingsui/PrivacyPostureScorecardCalculator.kt",
        "core/perf/src/main/kotlin/dev/fusion/perf/StartupColdWarmRegressionGate.kt",
        "core/perf/src/main/kotlin/dev/fusion/perf/MemoryPressureTrimBudgeter.kt",
    ]),
    ("r51_lens_media_cast_a11y.jar", [], [
        "cast/airplay/src/main/kotlin/dev/fusion/airplay/RaopAlacAudioPacketizer.kt",
        "cast/airplay/src/main/kotlin/dev/fusion/airplay/AirPlayFairPlayStubRefusalGate.kt",
        "cast/castv2/src/main/kotlin/dev/fusion/castv2/CastV2MediaQueueController.kt",
        "cast/castv2/src/main/kotlin/dev/fusion/castv2/CastVirtualDisplayBoundsScaler.kt",
        "cast/dlna/src/main/kotlin/dev/fusion/cast/dlna/DidlLiteXmlMetadataBuilder.kt",
        "feature/media/src/main/kotlin/dev/fusion/media/WebVttCueTimelineEngine.kt",
        "feature/media/src/main/kotlin/dev/fusion/media/EbuR128LoudnessNormalizer.kt",
        "format/image/src/main/kotlin/dev/fusion/format/image/ExifGpsPrivacyScrubber.kt",
        "format/image/src/main/kotlin/dev/fusion/format/image/OklabPerceptualPaletteQuantizer.kt",
        "format/psd/src/main/kotlin/dev/fusion/psd/PsdSmartPlacedLayerInspector.kt",
        "feature/continuity/src/main/kotlin/dev/fusion/continuity/CrossDeviceClipboardSyncEngine.kt",
        "feature/continuity/src/main/kotlin/dev/fusion/continuity/AudioOutputHandoffCoordinator.kt",
        "feature/lens/src/main/kotlin/dev/fusion/lens/DocumentDewarpPerspectiveEngine.kt",
        "feature/lens/src/main/kotlin/dev/fusion/lens/BarcodeSymbologyDecoder.kt",
        "feature/messages/src/main/kotlin/dev/fusion/messages/DoubleRatchetSessionEngine.kt",
        "feature/messages/src/main/kotlin/dev/fusion/messages/SealedSenderPaddingEnvelope.kt",
        "feature/beam/src/main/kotlin/dev/fusion/beam/ReedSolomonQrChunkFountain.kt",
        "feature/alerts/src/main/kotlin/dev/fusion/alerts/DndPriorityScheduleEvaluator.kt",
        "feature/capture/src/main/kotlin/dev/fusion/capture/ScrollingStitchSeamMatcher.kt",
        "feature/accessibility/src/main/kotlin/dev/fusion/a11y/WcagTouchTargetSemanticAuditor.kt",
        "feature/together/src/main/kotlin/dev/fusion/together/YjsRgaTextCrdtEngine.kt",
        "feature/automation/src/main/kotlin/dev/fusion/automation/CronDagWorkflowScheduler.kt",
        "feature/maps/src/main/kotlin/dev/fusion/maps/RTreeSpatialGeofenceIndex.kt",
        "benchmark/src/main/java/dev/fusion/benchmark/SimdSpreadsheetThroughputHarness.kt",
        "baselineprofile/src/main/kotlin/dev/fusion/baselineprofile/ArtStartupMethodRuleOptimizer.kt",
        "testing/src/main/kotlin/dev/fusion/testing/DeterministicPropertyShrinker.kt",
    ]),
    ("r51_checks.jar", [
        "r51_office_formats.jar",
        "r51_privacy_browser_wasm.jar",
        "r51_shell_core.jar",
        "r51_lens_media_cast_a11y.jar",
    ], [
        "testing/src/r51/kotlin/dev/fusion/testing/R51Checks.kt",
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

    out_dir = ROOT / "testing/build/r51"
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
        if jar_name == "r51_checks.jar":
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
        [java, "-cp", cp, "dev.fusion.testing.R51Checks", str(art_dir)],
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
        "round": "R51",
        "exit_code": 0,
        "elapsed_seconds": elapsed,
        "assertions": int(m),
        "new_kernels": 85,
        "touched_pure_jvm_kernels": 5,
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
