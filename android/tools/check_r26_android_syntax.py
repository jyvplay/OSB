#!/usr/bin/env python3
"""Parse-gate for R26 authored Android sources using the real kotlinc frontend.

Without android.jar/Compose, resolution errors are EXPECTED. This gate fails only on
syntax-class diagnostics (which precede resolution), proving the files parse. It is NOT
a type check and NOT a compile claim: full compilation requires Studio/CI with the SDK.
"""
import json
import os
import re
import subprocess
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
FILES = [
    'app/src/main/java/dev/fusion/shell/AdaptiveDock.kt',
    'feature/media/src/main/java/dev/fusion/media/MediaHub.kt',  # R27: stream-player button
    'app/src/main/java/dev/fusion/shell/UsbWatchReceiver.kt',  # R28: USB watch receiver (app-only; keeps :privacy scope exact)
    'app/src/main/java/dev/fusion/shell/ConsentDialog.kt',  # R28: consent dialog
    'app/src/main/java/dev/fusion/shell/UsbAllowlist.kt',  # R29: USB allowlist store
    'feature/media/src/main/java/dev/fusion/media/StreamPlayerActivity.kt',
    'feature/maps/src/main/java/dev/fusion/maps/VectorAtlasBridge.kt',
    'feature/browser/src/main/java/dev/fusion/browser/OrbotHandoff.kt',
    'app/src/main/java/dev/fusion/shell/TabletopDesktop.kt',  # R46: TileLabelFit 2-line desktop pin wiring
    'app/src/main/java/dev/fusion/shell/DesktopChrome.kt',  # R46: DesktopTopBar compact width + divider anti-clipping fix
    'app/src/main/java/dev/fusion/shell/MainActivity.kt',  # R47: BeautyDock wiring repairing r24c 6th-dock clipping
    'feature/maps/src/main/java/dev/fusion/maps/MapScreen.kt',  # R47: Offline atlas toolbar padding + divider anti-clipping fix
    'feature/editor/src/main/java/dev/fusion/editor/EditorScreen.kt',  # R48: ReadabilityMetrics + LaTeX/citation live Writer inspector
    'feature/browser/src/main/java/dev/fusion/browser/BrowserScreen.kt',  # R48: HtmlSanitizer + ArticleReaderExtractor + CrossOriginIsolationPolicy UI
    'feature/vault/src/main/java/dev/fusion/vault/VaultScreen.kt',  # R48: SecureKeypadScrambler CSPRNG PIN pad + ScreenCaptureGuard + ClipboardSensitiveGuard
    'feature/notes/src/main/java/dev/fusion/notes/NotesScreen.kt',  # R48: WikiLinkGraph live draft wikilink inspector
    'feature/terminal/src/main/java/dev/fusion/terminal/TerminalScreen.kt',  # R48: PromptOsc133 semantic prompt + OSC 52 exfil counter
    'feature/calc/src/main/java/dev/fusion/calc/Calc.kt',  # R49: BaseRadixBitfieldInspector + IEEE-754 + FinancialScheduleSolver UI
    'feature/files/src/main/java/dev/fusion/filesui/FilesScreen.kt',  # R49: DuplicateFileClusterer + Forge-peek headline + SizeBucket UI
    'feature/find/src/main/java/dev/fusion/find/Palette.kt',  # R49: PaletteInlineAnswerEngine live math/unit/radix answer card
    'feature/settings/src/main/java/dev/fusion/settingsui/SettingsScreen.kt',  # R49: OemSettingsDeepLinkCatalog + PermissionRiskAuditor posture UI
    'feature/backup/src/main/java/dev/fusion/backup/BackupScreen.kt',  # R49: BackupHealthDrillScheduler + new-device checklist UI
    'feature/capture/src/main/java/dev/fusion/capture/CaptureScreen.kt',  # R49: CaptureBitrateBudgetPlanner live AVC budget summary
    'format/pdf/src/main/java/dev/fusion/format/pdf/PdfScreen.kt',  # R49: PdfFormAcroFieldInspector + PdfPkcs7SignatureVerifier summary
    'feature/alerts/src/main/java/dev/fusion/alerts/AlertsScreen.kt',  # R49: NotificationDiagnosticLedger ingest disposition summary
    'feature/lens/src/main/java/dev/fusion/lens/LensScreen.kt',  # R50: FoldPosturePresenterStudio + VirtualWebcamMjpegStream thermal cap UI
    'feature/messages/src/main/java/dev/fusion/messages/MessagesScreen.kt',  # R50: UnifiedPushSmsFallbackRouter PDU segmenter + MatrixMegolmGroupRatchet UI
    'feature/continuity/src/main/java/dev/fusion/continuity/ContinuityScreen.kt',  # R50: RemoteDesktopRfbTileCodec + SharedFolderDeltaSyncPlanner UI
    'feature/together/src/main/java/dev/fusion/together/TogetherSession.kt',  # R50: TogetherCoWatchSyncClock PLL baseline UI
    'feature/beam/src/main/java/dev/fusion/beam/CastSendPanel.kt',  # R50: BeamChunkedResumeReedSolomon LAN parity shard preview UI
    'feature/automation/src/main/java/dev/fusion/automation/TaskPanel.kt',  # R50: WorkflowJsonImportExportSandbox zero-shell pack preview UI
    'feature/accessibility/src/main/java/dev/fusion/a11y/Adaptation.kt',  # R50: FocusRingDwellClickController + EasySimpleModeLayoutScaler + Daltonize UI
    'cast/dlna/src/main/java/dev/fusion/cast/dlna/DlnaScreen.kt',  # R50: DlnaDidlLitePlaylistQueue XXE-safe DIDL-Lite preview UI
    'format/psd/src/main/java/dev/fusion/psd/PsdScreen.kt',  # R50: PsdBlendAdjustmentCompositor blend-mode readiness summary UI
    'privacy/src/main/java/dev/fusion/privacy/PrivacyActivity.kt',  # R50: ClearUrlTrackingParamStripper offline tracking-param counter UI
    'feature/editor/src/main/java/dev/fusion/editor/EditorTools.kt',  # R51: WidowOrphanGalleyPaginator + CitationBibliographyFormatter live Writer tools UI
    'feature/calc/src/main/java/dev/fusion/calc/CalculatorViewModel.kt',  # R51: GoalSeekSimplexSolver Brent root-finder & Simplex LP preview hooks
    'app/src/main/java/dev/fusion/shell/ExternalDesktopScreen.kt',  # R51: SnapTileMagneticLayoutSolver + FusionClassAReadinessAggregator external display banner
    'feature/settings/src/main/java/dev/fusion/settingsui/AppearanceControls.kt',  # R51: PrivacyPostureScorecardCalculator 20-vector shield summary badge
    'feature/media/src/main/java/dev/fusion/media/SubtitlePicker.kt',  # R51: WebVttCueTimelineEngine collision-free WebVTT lane preview UI
    'feature/launcher/src/main/java/dev/fusion/launcher/Shelf.kt',  # R51: PinnedWorkspaceDockLayoutEngine 48dp slot-overflow planner UI
    'core/hw/src/main/java/dev/fusion/hw/HwProbe.kt',  # R51: Usb4PcieIommuDmaGuard Thunderclap pre-unlock DMA policy hook
    'core/capabilities/src/main/java/dev/fusion/capabilities/CapabilityProbe.kt',  # R51: WasmSimdModuleVerifier 0xFD v128 module audit hook
    'core/designsystem/src/main/java/dev/fusion/designsystem/Theme.kt',  # R51: ApcaContrastTypographyScale WCAG 2.2 + perceptual Lc palette check
    'feature/continuity/src/main/java/dev/fusion/continuity/Peripherals.kt',  # R51: AudioOutputHandoffCoordinator privacy-first unplug pause planner
    'privacy/src/main/java/dev/fusion/privacy/ExposureActivity.kt',  # R51: TorVanguardsLiteMesh + PanicDeadManSwitchEngine GuardedPrivacyActivity view
    'feature/browser/src/main/java/dev/fusion/browser/BrowserRecallHud.kt',  # R52: Recall automatic media archiver HUD (URL / Page Title / Custom + 4 tiers)
    'feature/capture/src/main/java/dev/fusion/capture/RecallWorkspaceScreen.kt',  # R52: Recall Tier-4 viewport screen + AudioPlaybackCapture workspace panel
    'core/designsystem/src/main/kotlin/dev/fusion/designsystem/FusionContextMenuPopup.kt',  # R52: Right-sized 180..280dp (<=34% vw) context menu popup
    'core/designsystem/src/main/kotlin/dev/fusion/designsystem/FusionViewModeIconBar.kt',  # R52: Original bespoke Canvas vector glyphs for List / Grid / Columns
    'feature/files/src/main/java/dev/fusion/filesui/FilesViewColumnsPane.kt',  # R52: List / Grid / Miller-Columns multi-mode directory pane
    'core/designsystem/src/main/kotlin/dev/fusion/designsystem/StackedUndoNotificationHost.kt',  # R52: Bottom-right translucent Z-stack Undo notification host
    'core/designsystem/src/main/kotlin/dev/fusion/designsystem/FusionContextMenuSubmenuHost.kt',  # R53: Right-sized 180..280dp + 168..240dp cascading submenu host
    'core/designsystem/src/main/kotlin/dev/fusion/designsystem/StackedUndoFanOverlayBar.kt',  # R53: Interactive compact Z-stack (<9% h) <-> hover-fanned translucent Undo bar
    'core/designsystem/src/main/kotlin/dev/fusion/designsystem/CrispHintedViewModeToolbar.kt',  # R53: Pixel-grid crisp-hinted original List / Grid / Columns logo toolbar
    'feature/browser/src/main/java/dev/fusion/browser/RecallSixMethodMatrixSheet.kt',  # R53: Recall 6-Method (T1..T5 incl. WebRTC RTP & ISO WARC/1.1) live routing sheet
    'feature/media/src/main/java/dev/fusion/media/MediaLibraryMultiViewPanel.kt',  # R53: Media Hub List / Grid / Columns multi-view panel
    'feature/files/src/main/java/dev/fusion/filesui/FilesDualPaneInspectorBar.kt',  # R53: Dual-pane synced List/Grid/Columns + cascading context-menu bar
    'feature/vault/src/main/java/dev/fusion/vault/VaultColumnsSecretBrowser.kt',  # R53: Encrypted Vault List / Grid / Columns secret browser
    'feature/calc/src/main/java/dev/fusion/calc/SheetScreen.kt',  # R54: bounded sheet authoring UI
    'feature/calc/src/main/java/dev/fusion/calc/SheetViewModel.kt',  # R54: private draft/recalculation owner
    'feature/editor/src/main/java/dev/fusion/editor/CopyDestinationActivity.kt',  # R54: allowlisted office copy MIME types
    'app/src/main/java/dev/fusion/shell/OfficeExportViewModel.kt',  # R54: frozen output-only office copies
    'app/src/androidTest/java/dev/fusion/shell/R54OfficeDeviceTest.kt',  # R54: authored device/provider/UI tests
    'app/src/androidTest/java/dev/fusion/shell/R25PngCopyDeviceTest.kt',  # R55: real picker-return diagnostic
]
SYNTAX = re.compile(r'error: (expecting|syntax|mismatched input|unclosed|unresolved reference \S*$)')

def main():
    import argparse
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument('--tools', type=Path, default=Path.home() / '.cache/fusion-host-r23')
    a = p.parse_args()
    sys.path.insert(0, str(ROOT / 'tools'))
    import bootstrap_host_tools
    env = bootstrap_host_tools.environment(a.tools.expanduser().resolve())
    out = ROOT / 'testing/build/r26'
    out.mkdir(parents=True, exist_ok=True)
    report = {}
    failed = False
    # Single-JVM batch parse check (10x faster than 17 cold JVM starts); falls back to per-file isolation if any syntax diagnostic appears.
    batch_proc = subprocess.run(
        [env['KOTLINC'], '-jvm-target', '17', '-d', str(out / 'parse-check.jar'),
         *[str(ROOT / rel) for rel in FILES]],
        capture_output=True, text=True, env=env,
    )
    batch_lines = (batch_proc.stdout + batch_proc.stderr).splitlines()
    for rel in FILES:
        rel_errors = [l for l in batch_lines if rel in l and 'error:' in l]
        syntax = [l for l in rel_errors if SYNTAX.search(l) and 'unresolved reference' not in l]
        report[rel] = {'diagnostics': len(rel_errors), 'syntax_errors': syntax,
                       'all_resolution_class': not syntax and len(rel_errors) > 0}
        if syntax:
            failed = True
            print(f'SYNTAX FAIL {rel}:')
            print('\n'.join(syntax))
        else:
            print(f'PARSE OK {rel}: {len(rel_errors)} resolution-only diagnostics (expected without android.jar).')
    report['scope'] = 'kotlinc parse only; NOT a type check, NOT compilation, NOT execution.'
    (out / 'android-syntax.json').write_text(json.dumps(report, indent=2) + '\n')
    if failed:
        raise SystemExit(1)

if __name__ == '__main__':
    main()
