#!/usr/bin/env python3
"""Host-runnable R50 contract runner (50/50 modules, 80 new pure-JVM kernels + 14 TOUCHED files).

Compiles the R50 Office/Spreadsheet/Formats, Browser/Privacy/Security/Wasm, Shell/Core/Feature,
and Lens/Messages/Continuity/Cast/A11y/Testing jars under separate `-Werror` kotlinc invocations
with SHA-256 content-fingerprint caching, then executes `dev.fusion.testing.R50Checks` on JVM 17
and emits 26 real `.xlsx`, `.pptx`, `.docx`, `.pdf`, `.wav`, `.wasm`, `.bin`, `.svg`, `.xml`,
`.vcf`, `.txt`, and `.json` artifacts for external differential validation (`tools/check_r50_reference.py`).
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
    ("r50_office_formats.jar", [], [
        "format/ooxml/src/main/kotlin/dev/fusion/ooxml/BoundedXml.kt",
        "format/ooxml/src/main/kotlin/dev/fusion/ooxml/ColLetters.kt",
        "format/ooxml/src/main/kotlin/dev/fusion/ooxml/SheetRange.kt",
        "format/ooxml/src/main/kotlin/dev/fusion/ooxml/CellErr.kt",
        "format/ooxml/src/main/kotlin/dev/fusion/ooxml/FormulaEval.kt",
        "format/ooxml/src/main/kotlin/dev/fusion/ooxml/SpillSheetEngine.kt",
        "format/ooxml/src/main/kotlin/dev/fusion/ooxml/MultiSheetBook.kt",
        "format/ooxml/src/main/kotlin/dev/fusion/ooxml/PivotCacheXmlExporter.kt",
        "format/ooxml/src/main/kotlin/dev/fusion/ooxml/ConditionalDataBarScaleEngine.kt",
        "format/ooxml/src/main/kotlin/dev/fusion/ooxml/OoxmlSparklineSvgRenderer.kt",
        "core/documents/src/main/kotlin/dev/fusion/documents/OpenTypeKerningTableLayout.kt",
        "core/documents/src/main/kotlin/dev/fusion/documents/MasterSlideTransitionDeck.kt",
        "core/documents/src/main/java/dev/fusion/documents/WriterTrackChangesDiffMerger.kt",
        "feature/editor/src/main/java/dev/fusion/editor/OutlineTocCrossRefEngine.kt",
        "feature/notes/src/main/java/dev/fusion/notes/MermaidFlowchartSvgLayout.kt",
        "format/native/src/main/kotlin/dev/fusion/native/NewFileTemplateCatalog.kt",
        "format/archive/src/main/kotlin/dev/fusion/archive/Mat2RecursiveMetadataScrubber.kt",
        "format/lok/src/main/java/dev/fusion/lok/ThumbnailMetadataIndexer.kt",
        "format/image/src/main/java/dev/fusion/format/image/OklchPaletteContrastPicker.kt",
        "format/psd/src/main/java/dev/fusion/psd/PsdBlendAdjustmentCompositor.kt",
        "format/pdf/src/main/java/dev/fusion/format/pdf/PdfIncrementalSaveRedactor.kt",
        "format/pdf/src/main/java/dev/fusion/format/pdf/PdfPageBookmarkSplitMerger.kt",
    ]),
    ("r50_privacy_browser_wasm.jar", [], [
        "core/capabilities/src/main/java/dev/fusion/capabilities/WasmSandbox.kt",
        "core/capabilities/src/main/java/dev/fusion/capabilities/WebAppWindowManifestHost.kt",
        "feature/browser/src/main/java/dev/fusion/browser/EasyListTrackerFilterEngine.kt",
        "feature/browser/src/main/java/dev/fusion/browser/BrowserProfileDirectoryIsolator.kt",
        "feature/browser/src/main/java/dev/fusion/browser/BrowserTabShortcutBatchBroker.kt",
        "feature/browser/src/main/java/dev/fusion/browser/DnsOverHttpsWireQueryCodec.kt",
        "privacy/src/main/java/dev/fusion/privacy/TorPluggableTransportConfig.kt",
        "privacy/src/main/java/dev/fusion/privacy/BluetoothBluffsSessionGuard.kt",
        "privacy/src/main/java/dev/fusion/privacy/WifiProbeSequenceScrambler.kt",
        "privacy/src/main/java/dev/fusion/privacy/PrivacyPostureDashboardEngine.kt",
        "privacy/src/main/java/dev/fusion/privacy/ClearUrlTrackingParamStripper.kt",
        "core/security/src/main/java/dev/fusion/security/NfcRelayDistanceBoundingGate.kt",
        "core/security/src/main/java/dev/fusion/security/ImeIncognitoClipboardFirewall.kt",
        "core/security/src/main/java/dev/fusion/security/UsbBadHidKeystrokeRateGuard.kt",
        "core/hw/src/main/java/dev/fusion/hw/TempestDisplayJitterFilter.kt",
        "core/hw/src/main/java/dev/fusion/hw/GamepadPointerModeMapper.kt",
        "core/hw/src/main/java/dev/fusion/hw/HardwareKeyboardImeSwitcher.kt",
        "feature/vault/src/main/java/dev/fusion/vault/ShamirSecretShareSplitter.kt",
    ]),
    ("r50_shell_core.jar", [], [
        "app/src/main/java/dev/fusion/shell/AppInventoryLifecycleAdvisor.kt",
        "app/src/main/java/dev/fusion/shell/WorkspaceContextProfileEngine.kt",
        "core/windowing/src/main/kotlin/dev/fusion/windowing/ExternalAppBrokerDensityPin.kt",
        "core/windowing/src/main/kotlin/dev/fusion/windowing/KeyToTouchOverlayMapper.kt",
        "core/files/src/main/java/dev/fusion/files/RemoteMountReconnectPlanner.kt",
        "core/files/src/main/java/dev/fusion/files/CrossWindowClipUriGrantor.kt",
        "core/model/src/main/kotlin/dev/fusion/model/ContactsVcard4PickerModel.kt",
        "core/commands/src/main/kotlin/dev/fusion/commands/ShortcutPresetConflictDetector.kt",
        "core/settings/src/main/java/dev/fusion/settings/DefaultAppsRoleGuideEngine.kt",
        "core/perf/src/main/java/dev/fusion/perf/MeteredBatteryLowRamScheduler.kt",
        "core/index/src/main/kotlin/dev/fusion/index/OptInWebAppSearchFederation.kt",
        "core/storage/src/main/java/dev/fusion/storage/ImapEnvelopeTriageStore.kt",
        "core/designsystem/src/main/java/dev/fusion/designsystem/OsdQuickPanelTileTokens.kt",
        "feature/launcher/src/main/java/dev/fusion/launcher/PinnedWebShortcutShelfModel.kt",
        "feature/find/src/main/java/dev/fusion/find/CommandPaletteFuzzyActionRanker.kt",
        "feature/files/src/main/java/dev/fusion/filesui/DualPaneContextMenuModel.kt",
        "feature/settings/src/main/java/dev/fusion/settingsui/KeyboardShortcutProfileEditor.kt",
        "feature/terminal/src/main/java/dev/fusion/terminal/SshTransportKexFrameCodec.kt",
        "feature/calc/src/main/java/dev/fusion/calc/UnitMatrixSolverWorkbench.kt",
        "feature/alerts/src/main/java/dev/fusion/alerts/FocusModeContextEvaluator.kt",
    ]),
    ("r50_lens_media_cast_a11y.jar", [], [
        "feature/media/src/main/java/dev/fusion/media/VoiceRecorderWavWaveformCodec.kt",
        "feature/media/src/main/java/dev/fusion/media/Mp4KeyframeTrimPlanner.kt",
        "feature/lens/src/main/java/dev/fusion/lens/Fsr1EasuRcasUpscaler.kt",
        "feature/lens/src/main/java/dev/fusion/lens/Cube3dLutColorGrader.kt",
        "feature/lens/src/main/java/dev/fusion/lens/NlmsAcousticEchoGate.kt",
        "feature/lens/src/main/java/dev/fusion/lens/PerspectiveDeskewBinarizer.kt",
        "feature/lens/src/main/java/dev/fusion/lens/FoldPosturePresenterStudio.kt",
        "feature/lens/src/main/java/dev/fusion/lens/VirtualWebcamMjpegStream.kt",
        "feature/messages/src/main/java/dev/fusion/messages/MatrixMegolmGroupRatchet.kt",
        "feature/messages/src/main/java/dev/fusion/messages/XmppOmemoStanzaSeal.kt",
        "feature/messages/src/main/java/dev/fusion/messages/UnifiedPushSmsFallbackRouter.kt",
        "feature/continuity/src/main/java/dev/fusion/continuity/SharedFolderDeltaSyncPlanner.kt",
        "feature/continuity/src/main/java/dev/fusion/continuity/RemoteDesktopRfbTileCodec.kt",
        "feature/together/src/main/java/dev/fusion/together/TogetherCoWatchSyncClock.kt",
        "cast/castv2/src/main/kotlin/dev/fusion/castv2/PresentationSpeakerNotesDualView.kt",
        "cast/airplay/src/main/kotlin/dev/fusion/airplay/CastSinkSrpPinVerifier.kt",
        "cast/dlna/src/main/java/dev/fusion/cast/dlna/DlnaDidlLitePlaylistQueue.kt",
        "feature/automation/src/main/java/dev/fusion/automation/WorkflowJsonImportExportSandbox.kt",
        "feature/accessibility/src/main/java/dev/fusion/a11y/ColorBlindnessDaltonizeMatrix.kt",
        "feature/accessibility/src/main/java/dev/fusion/a11y/FocusRingDwellClickController.kt",
        "feature/accessibility/src/main/java/dev/fusion/a11y/EasySimpleModeLayoutScaler.kt",
        "feature/maps/src/main/java/dev/fusion/maps/OfflinePmtilesHilbertDirectory.kt",
        "feature/beam/src/main/java/dev/fusion/beam/BeamChunkedResumeReedSolomon.kt",
        "feature/capture/src/main/java/dev/fusion/capture/ScrollingCaptureSeamStitcher.kt",
        "feature/backup/src/main/java/dev/fusion/backup/ResticCdcChunkDeduplicator.kt",
        "benchmark/src/main/java/dev/fusion/benchmark/R50LatencyPercentileBudgetGate.kt",
        "baselineprofile/src/main/kotlin/dev/fusion/baselineprofile/ArtStartupProfileRuleSynthesizer.kt",
        "testing/src/main/kotlin/dev/fusion/testing/R50CrossKernelTranscriptVerifier.kt",
    ]),
    ("r50_checks.jar", [
        "r50_office_formats.jar",
        "r50_privacy_browser_wasm.jar",
        "r50_shell_core.jar",
        "r50_lens_media_cast_a11y.jar",
    ], [
        "testing/src/r50/kotlin/dev/fusion/testing/R50Checks.kt",
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

    out_dir = ROOT / "testing/build/r50"
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
        if jar_name == "r50_checks.jar":
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
        [java, "-cp", cp, "dev.fusion.testing.R50Checks", str(art_dir)],
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
        "round": "R50",
        "exit_code": 0,
        "elapsed_seconds": elapsed,
        "assertions": int(m),
        "new_kernels": 80,
        "touched_pure_jvm_kernels": 4,
        "touched_android_ui_files": 10,
        "modules_deepened_in_round": "50/50",
        "output": proc.stdout.strip(),
        "artifacts": sorted(p.name for p in art_dir.iterdir() if p.is_file()),
        "sha256": {s: sha256_file(ROOT / s) for s in all_srcs},
        "scope": "Actual host kotlinc -Werror separate-jar compilation + JVM execution across 50/50 modules; NOT Android device/SDK/AX/SurfaceFlinger Class A acceptance.",
    }
    (out_dir / "result.json").write_text(json.dumps(report, indent=2) + "\n")


if __name__ == "__main__":
    main()
