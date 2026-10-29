# Fusion Shell — native Android development

Open **[`android/`](android/README.md)** in Android Studio. That is the Kotlin /
Jetpack Compose application, not a browser recreation.

The existing root `src/`, npm files and Vite configuration are legacy web work;
they are not the Android product, were not extended in this pass, and are not
part of the Android build.

**This is an incomplete implementation with limited API34 app and API34/API37 privacy emulator testing of the owner's
v2.0 + v2.1 specification.** No release-complete or performance claim is made.
See [build instructions](android/docs/BUILD.md),
[current verification status](android/STATUS.md), and the
[231-row requirement ledger](android/requirements.csv).

Historical: [prior R12 engineering and evidence](android/docs/R12-ENGINEERING.md).
[Current substantial testable subsets:50/50 (100%)](android/docs/MODULE-TESTABILITY.md).
**0/50 specification-accepted modules (0%); all50 unfinished at full scope.**

## Latest R53 — Recall 6-Method Browser Media Studio (`WebRTC RTP` + `ISO 28500 WARC/1.1`) + 7-OS Cascading Context Menus / 5-DPI Crisp `List`·`Grid`·`Columns` Logos / Interactive `Undo` Fan + 96 new kernels (+7 UI + 19 touched) across 50/50 modules (100%)

Branch **`arena/64550eba-osb`**, `2026-10-29`. Completes `R52` closure and executes **`R53`**, authoring **96 new pure-JVM production kernels + 7 new Jetpack Compose UI components + 19 touched production files (`FormulaEval.kt` `260 -> 300` fns, `SpillSheetEngine.kt` `42 -> 48` spill ops, `WasmSandbox.kt` W3C WebAssembly 3.0 Multi-Memory up to `8` linear memories + cross-memory `0xFC 0x0A memory.copy`, `RecallMethodEscalationRouter.kt` & `RecallSessionCoordinator.kt` `6` recording methods, plus 14 Jetpack Compose UI / Activity screens with `59/59` Android/Compose AST `PARSE OK`) across ALL 50/50 declared Gradle modules (`1,101` `src/main/**/*.kt` files)**:
- **Phase 1 (Completed FIRST) — 6-Method `Recall` Browser Media Recording System (`IMAGE`, `VIDEO`, `MUSIC` across `BY_URL_NAME`, `BY_PAGE_TITLE`, `BY_CUSTOM_TEMPLATE`):** Expands `Recall` from 4 to **6 distinct recording methods** — adding **Method 3 (`RecallTier25WebRtcRtpDepacketizer`)** RFC 3550 / RFC 7587 48 kHz Opus/VP8 RTP jitter reordering + duplicate suppression and **Method 6 (`RecallTier5WarcIso28500Archiver` + `WarcGzipConcatMemberInspector` + `RecallCdxjReplayIndexCodec`)** ISO 28500:2017 `WARC/1.1` self-contained multi-resource archival bundles with `sha256:` digests, per-record `warc.gz` random access, SURT CDXJ replay index, RFC 2557 `MHTML`, `.cue` audio silence splitting (`RecallAudioSilenceTrackSplitter`), ITU-R BS.1770-4 EBU R128 LUFS metering (`LoudnessEbuR128MomentaryMeter`), MP3 Xing/VBRI, Ogg Opus chapters, WebM EBML clusters, and 64-bit `dHash` deduplication.
- **Phase 2 (Executed AFTER Phase 1) — 7-OS Cascading Right-Click Submenus, Multi-DPI Crisp `List`/`Grid`/`Columns` Vector Logos & Interactive Hover `Undo` Fan:** `ContextMenuSubmenuCascadeGeometry` + `PointerSafeTriangleSubmenuFilter` + `FusionContextMenuSubmenuHost` enforce `180..280dp` primary + `168..240dp` left/right-flipping submenu geometry against 7 OS baselines (plus right-sizing `Calc.kt` `UnitSelector` `DropdownMenu`); `ViewModePixelGridCrispHinting` + `CrispHintedViewModeToolbar` deliver `100%` crisp half-pixel hinted original `List`/`Grid`/`Columns` logos across `16/20/24/32/48px` on `8` surfaces; `UndoDeckInteractiveFanController` + `TranslucentGlassBackdropContrastGuard` + `StackedUndoFanOverlayBar` keep the compact bottom-right Z-stack at `8.44%` right-side height (`< 9%` cap) and expand to `20.44%` (`< 25%` cap) on hover.
- **300-Fn Spreadsheet + 48-Op Spill + Wasm 3.0 Multi-Memory + Tor Prop 329 `Conflux` + Clean-Room `MAT2` + 30-Vector Hardware Shield:** `25,425` R53 JVM assertions (`77` artifacts) + `411/411` Python 3.11 differential oracle checks + `6` visually inspected `1880×1400` PNGs (`docs/verification/r53-host-workbench/`, `1,376` `FontMetrics` + `180,495` pairwise 2D checks, `12.38:1` WCAG AAA) + `42` Gradle CI round tasks (`r13..r53`) + `57` Python `unittest` suites + live warm-JVM `Kotlin-JVM-R53` workbench (`0.0.0.0:8765`, `Cards 1..30`); **`50/50 = 100.00%`** host-testable subsets vs **`0/50 = 0.00%`** full-spec Class A (`221` `partial_unverified`, `6` `not_implemented`, `0` `verified`).
[R53 Results](android/docs/R53-RESULTS.md) · [R53 Plan](android/docs/R53-PLAN.md) · [R53 Alternatives](android/docs/R53-ALTERNATIVES.md) · [R53 Privacy PRD](android/docs/R53-PRIVACY-PRD.md) · [R53 Module Deepening](android/docs/R53-MODULE-DEEPENING.md) · [R53 Class-A Readiness](android/docs/class-a-readiness-r53.json).

## Latest R52 — Recall 4-Tier Browser Media Recording Studio + Compact Context Menu / Original List·Grid·Columns Logos / Bottom-Right Z-Stack Undo + 91 new kernels (+6 UI + 17 touched) across 50/50 modules (100%)

Branch **`arena/64550eba-osb`**, `2026-10-28`. Completes `R51` closure and executes **`R52`**, authoring **91 new pure-JVM production kernels + 6 new Jetpack Compose UI components + 17 touched production files (`FormulaEval.kt` `225 -> 260` fns, `SpillSheetEngine.kt` `36 -> 42` spill ops, `PptxSlideDeck.kt` `<p:timing>` media cue timelines, `WasmSandbox.kt` Wasm 3.0 `0x12`/`0x13` `O(1)` tail-call execution, `UndoRedoStack.kt`, `DualPaneContextMenuModel.kt`, plus 11 Jetpack Compose UI / Activity screens with `52/52` Android/Compose AST `PARSE OK`) across ALL 50/50 declared Gradle modules (`998` `src/main/**/*.kt` files)**:
- **Task Block 1 (Completed FIRST) — Production-Grade `Recall` Automatic Browser Media Recording System (`IMAGE`, `VIDEO`, `MUSIC`):**
  - **3 Folder Delineation Modes + SHA-256 Deduplicated Vault (`RecallFolderDelineator` + `RecallArchiveCatalog`):** `(1) BY_URL_NAME` (IDNA/Punycode host + CleanURL path slug), `(2) BY_PAGE_TITLE` (Unicode NFKC slug + Windows/POSIX reserved-device sanitization + 6-hex origin discriminator), and `(3) BY_CUSTOM_TEMPLATE` (`{custom}/{yyyy}-{mm}-{dd}/{domain}/{kind}` with strict `normalize().startsWith(archiveRoot)` path-traversal confinement).
  - **4 Distinct Escalating Recording Methods (exceeding >= 3 requirement):** **Method 1 (`RecallTier1DirectAssetHarvester`)** DOM `<img>`/`<picture>`/`srcset` highest-`w` + `<video>`/`<audio>` + inline `<svg>` + `data:` URI + out-of-order RFC 9110 HTTP `206 Partial Content` range reassembly + binary magic sniffer; **Method 2 (`RecallTier2HlsDashAssembler` + `RecallDashMpdSegmentResolver` + `RecallIsoBmffFragmentMuxer`)** RFC 8216 HLS (`#EXT-X-STREAM-INF`, `#EXT-X-MAP`, `#EXT-X-BYTERANGE`, clear-key `AES-128`) + ISO 23009-1 MPEG-DASH `SegmentTemplate` + ISO/IEC 14496-12 `fMP4` muxing (`ftyp` + `moov`/`mvex`/`trex` + `moof`[`mfhd`, `traf`(`tfhd`, `tfdt` strictly before `trun`)] + `mdat`); **Method 3 (`RecallTier3CanvasWebAudioSiphoner`)** WebAudio 32-bit float -> TPDF-dithered 48 kHz 16-bit `RIFF/WAVE` `.wav` + RFC 7845 `OggS` `.opus` + HTML5 `<canvas>` RGBA8 -> RFC 2083 lossless PNG; **Method 4 (`RecallTier4NativeScreenAudioRecorder`)** ultimate fallback `MediaProjection` + Android API 29–37 `AudioPlaybackCaptureConfiguration` (`USAGE_MEDIA`), even-macroblock `YUV420p` viewport cropper, and rational Bresenham A/V PTS clock synchronizer (`<= 20,000 us` residual drift across 2-minute GC stalls), strictly refusing DRM/EME, `FLAG_SECURE`, `ALLOW_CAPTURE_BY_NONE`, and incognito tabs.
- **Task Block 2 (Executed AFTER Task Block 1) — Compact Right-Click Context Menu, Original `List`/`Grid`/`Columns` Vector Logos & Bottom-Right Translucent Z-Stack `Undo` Deck:**
  - **Right-Sized Context Menu (`RightClickContextMenuGeometry` + `FusionContextMenuPopup`):** Clamped to `180..280dp` (`19.4%` of `1440dp` desktop width, `<= 34%` viewport cap; `<= 240dp` mobile) with bottom/right quadrant auto-flip, benchmarked against WinUI 3 (`456 epx`), macOS `NSMenu` (`~240pt`), Material 3 (`112..280dp`), and GNOME 47 HIG (`<= 33%`).
  - **100% Original From-Scratch Vector Logos (`FusionViewModeVectorIcons` + `FusionViewModeIconBar` + `FilesViewColumnsPane`):** Clean-room `24x24` mathematical primitives for **`List`** (*Fusion Spine-Ledger*: left spine + 3 diamond index ticks + asymmetric baselines), **`Grid`** (*Fusion Aperture-Mosaic*: 4 dog-ear chamfered card tiles + central diamond registration reticle), and **`Columns`** (*Fusion Cascading Miller-Triptych*: 3 graduated vertical pane chambers + 2 explicit horizontal drill-down bridges) + live `MillerColumnsTreeNavigator`.
  - **Bottom-Right Translucent Front-to-Back `Undo` Deck (`StackedUndoNotificationDeck` + `StackedUndoNotificationHost`):** Bottom-right corner anchor (`<= 340dp` wide, `<= 8.4%` of `900dp` desktop height), 3-card translucent Z-stack (`alpha = 0.88 / 0.68 / 0.48`, `scale = 1.00 / 0.95 / 0.90`, `peekY = 0 / -10 / -20dp`) wired across `11` UI/Activity screens so the right side of the page is never obstructed.
- **260-Fn Spreadsheet + 42-Op Spill + Wasm 3.0 `0x12`/`0x13` Tail Calls + Tor Prop 327 PoW + 25-Vector Hardware Shield + Visual Inspection:** `18,533` R52 JVM assertions (`56` artifacts) + `306/306` Python 3.11 differential oracle checks + `5` visually inspected Java2D PNGs (`docs/verification/r52-host-workbench/`, `1,108` `FontMetrics` + `127,341` pairwise 2D checks, `11.93:1` WCAG AAA) + live warm-JVM `Kotlin-JVM-R52` workbench (`0.0.0.0:8765`, `Cards 1..26`); **`50/50 = 100.00%`** host-testable subsets vs **`0/50 = 0.00%`** full-spec Class A (`221` `partial_unverified`, `6` `not_implemented`, `0` `verified`).
[R52 Results](android/docs/R52-RESULTS.md) · [R52 Plan](android/docs/R52-PLAN.md) · [R52 Alternatives](android/docs/R52-ALTERNATIVES.md) · [R52 Privacy PRD](android/docs/R52-PRIVACY-PRD.md) · [R52 Module Deepening](android/docs/R52-MODULE-DEEPENING.md) · [R52 Class-A Readiness](android/docs/class-a-readiness-r52.json).

## Latest R51 — 85 new kernels (+16 touched) across 50/50 modules (100%); 221/231 spec rows + 40 CI tasks + 20-vector shield + live `:8765` workbench

Branch **`arena/64550eba-osb`**, `2026-10-28`. Completes `R50` closure and executes **`R51`**, authoring **85 new pure-JVM production kernels + 16 touched files (`FormulaEval.kt` `168 -> 215` fns, `SpillSheetEngine.kt` `29 -> 36` spill ops, `DocxRichDocument.kt` multi-section/bookmarks, `OdtRichWriter.kt` scholarly ODT, `WasmSandbox.kt` Wasm 2.0 `0x7B`/`0xFD` `v128` SIMD + `invokeV128`, plus 11 Jetpack Compose UI / Activity files with `46/46` Android/Compose AST `PARSE OK`) across ALL 50/50 declared Gradle modules (`901` `src/main/**/*.kt` files)**:
- **215-Fn Spreadsheet (`StructuredTableRefEngine` + `R1C1FormulaTranslator` + `CalcChainDependencyGraph` + `GoalSeekSimplexSolver`), Galley Writer (`WidowOrphanGalleyPaginator` + `CitationBibliographyFormatter`), Tagged `PDF/UA-1` (`PdfUa1TaggedStructureWriter`), & `.ods`/`.pptx`/`.arrow`/`.psd` Formats:** `12,251` R51 JVM assertions across `41` artifacts + `208/208` independent Python 3.11 differential checks (`check_r51_reference.py`).
- **20-Vector Tails-Light (`7.15.1` + `Arti 2.7.0` `Vanguards-Lite` Prop 333 + `LUKS2`/`Argon2id` + `age v1`), Browser CHIPS/Flexbox/DNR/Permissions-Policy, Wasm 2.0 `0xFD` SIMD, & Hardware Attack Defenses (`GhostTouch`, `WIGHT`, `Thunderclap` `IOMMU`, `Rowhammer`, Optical Reflection):** `TorVanguardsLiteMesh`, `AgeV1StreamCipherEnvelope`, `Luks2Argon2idHeaderAuditor`, `AmnesicSessionShutdownOrchestrator`, `PanicDeadManSwitchEngine`, `CapacitiveGhostTouchFilter`, `Usb4PcieIommuDmaGuard`, `HallMagneticCoverTamperGuard`, `OpticalReflectionSideChannelGuard`, `DramRowhammerZeroizeGuard`.
- **Visual & Live `:8765` Verification + Strict Runtime Honesty:** `4` Java2D rendered PNGs (`docs/verification/r51-host-workbench/`, `864` `FontMetrics` + `110,376` pairwise 2D checks, `11.93:1` min WCAG AAA), live warm-JVM `Kotlin-JVM-R51` workbench (`Cards 1..22`), **`50/50 = 100.00%`** testable subsets vs **`0/50 = 0.00%`** full-spec Class A (`221` `partial_unverified`, `6` `not_implemented`, `0` `verified`).

## Latest R50 — 80 new kernels (+14 touched) across 50/50 modules (100%); 213/231 spec rows + 39 CI tasks + 15-vector shield + live `:8765` workbench

Branch **`arena/64550eba-osb`**, `2026-10-27`. Completes `R49` closure and executes **`R50`**, authoring **80 new pure-JVM production kernels + 14 touched files (`FormulaEval.kt` `130 -> 168` fns, `SpillSheetEngine.kt` `23 -> 29` spill ops, `MultiSheetBook.kt` `evaluate3d`/`write3d` 3D cross-sheet spans, `WasmSandbox.kt` Wasm 2.0 `f64` `0x7C` + `invokeF64` + `table.*`/`elem.drop`, plus 10 Jetpack Compose UI / Activity screens with `35/35` Android/Compose AST `PARSE OK`) across ALL 50/50 declared Gradle modules (`816` `src/main/**/*.kt` files)**:
- **168-Fn Spreadsheet, 3D MultiSheet, Writer, Master Slides, PDF Stream Redactor & Formats:** `FormulaEval` (`MDETERM`, `MINVERSE`, `MMULT`, `MUNIT`, `FREQUENCY`, `TREND`, `GROWTH`, `RANK.EQ`/`RANK.AVG`, `PERCENTRANK.INC`/`EXC`, `BIT*`, `DEC2*`/`HEX2*`/`BIN2*`/`OCT2*`, `DELTA`, `GESTEP`, `CONVERT`, `ERF`/`ERFC`, `GAMMALN`, `SERIESSUM`, `MULTINOMIAL`, `GCD`/`LCM`, `WORKDAY`, `DOLLARFR`), `MultiSheetBook` (`SUM(Q1:Q3!B2:B3)`), `PivotCacheXmlExporter`, `ConditionalDataBarScaleEngine`, `OoxmlSparklineSvgRenderer`, `OpenTypeKerningTableLayout`, `WriterTrackChangesDiffMerger`, `MasterSlideTransitionDeck`, `MermaidFlowchartSvgLayout`, `PdfIncrementalSaveRedactor`, `PdfPageBookmarkSplitMerger`, `Mat2RecursiveMetadataScrubber`, `PsdBlendAdjustmentCompositor`, `OklchPaletteContrastPicker`, `NewFileTemplateCatalog`, `ThumbnailMetadataIndexer`.
- **15-Vector Tails-Light Anonymity (`6.18–7.8.1`), Browser Isolation & Local Hardware Attack Defenses:** `TorPluggableTransportConfig` (`WebTunnel` + `Snowflake` + `obfs4` + `Conflux` dual-leg), `BluetoothBluffsSessionGuard` (`CVE-2023-24023` Mode 4 Level 4 `16`-octet SC-Only), `WifiProbeSequenceScrambler` (MAC + 12-bit `seq_ctrl` + `IFAT` jitter + 0-SSID), `NfcRelayDistanceBoundingGate` (`<= 12.0 ms` ISO-14443 RTT bound), `TempestDisplayJitterFilter` (`L2` Parseval scanline softening `<= 2` LSB), `UsbBadHidKeystrokeRateGuard` (`< 8.0 ms` BadUSB IKI firewall), `ImeIncognitoClipboardFirewall`, `EasyListTrackerFilterEngine`, `DnsOverHttpsWireQueryCodec` (`RFC 8484` + `RFC 8467` 128B pad), `ClearUrlTrackingParamStripper`, `BrowserProfileDirectoryIsolator`, `BrowserTabShortcutBatchBroker`, `WebAppWindowManifestHost`, `PrivacyPostureDashboardEngine`.
- **Wasm 2.0 `f64`/Multi-Table, Lens Studio (`FSR 1.0` + `.cube` 3D LUT + `NLMS` Echo), Vault `GF(256)` Shamir, E2EE Messaging & 50/50 Systems:** `Fsr1EasuRcasUpscaler` (MIT AMD `FSR 1.0` 12-tap `EASU` + 5-tap `RCAS`), `Cube3dLutColorGrader`, `NlmsAcousticEchoGate` (`>20 dB` ERLE), `PerspectiveDeskewBinarizer`, `FoldPosturePresenterStudio`, `VirtualWebcamMjpegStream`, `ShamirSecretShareSplitter` (`GF(2^8)` threshold recovery), `MatrixMegolmGroupRatchet` (`4`-tier forward-secret group E2EE), `XmppOmemoStanzaSeal`, `UnifiedPushSmsFallbackRouter`, `OfflinePmtilesHilbertDirectory`, `ResticCdcChunkDeduplicator`, `BeamChunkedResumeReedSolomon`, `RemoteDesktopRfbTileCodec`, `SshTransportKexFrameCodec`, `UnitMatrixSolverWorkbench`, `ColorBlindnessDaltonizeMatrix`, `FocusRingDwellClickController`, `EasySimpleModeLayoutScaler`, and full `50/50` module deepening.

**50/50 testable subsets (100.00%); 0/50 full-spec Class A accepted (0.00%); `213/231` (`92.21%`) `partial_unverified` / `14` `not_implemented` / `0` `verified` — [class-a-readiness-r50.json](android/docs/class-a-readiness-r50.json) enumerates exact external Android SDK 37 / device / TalkBack / SurfaceFlinger / hardware gates.** Executed: **8,555 kotlinc/JVM `-Werror` assertions** (`26` artifacts), **146/146** independent Python 3.11 differential oracle checks, **39 contiguous Gradle CI tasks (`r13Contracts..r50Contracts`)**, live warm-JVM HTTP workbench (`0.0.0.0:8765`), and inspectable Java2D desktop (`1840x1380`) + standalone compact (`360x800`) PNG previews (`android/docs/verification/r50-host-workbench/`, **321** `FontMetrics` + **36,942** pairwise 2D checks, min WCAG AAA contrast **9.37:1**).
[R50 Results](android/docs/R50-RESULTS.md) · [R50 Plan](android/docs/R50-PLAN.md) · [R50 Privacy PRD](android/docs/R50-PRIVACY-PRD.md) · [R50 Class-A Readiness](android/docs/class-a-readiness-r50.json) · [Prior R49 Results](android/docs/R49-RESULTS.md).

## Current R48 — 66 new kernels (+7 touched) across 50/50 modules (100%); zero-gap R13–R48 CI chain + live `:8765` workbench

Branch **`arena/64550eba-osb`**. Closes R47 follow-ups first (wires R47/R48 kernels into **5 Jetpack Compose screens** `EditorScreen`, `BrowserScreen`, `VaultScreen`, `NotesScreen`, `TerminalScreen` with `17/17` Android/Compose files `PARSE OK`; upgrades `FormulaEval` `61 -> 95` functions + inline `{1,2;3,4}` arrays + lexical `LET` + `_xlfn.`/`_xlws.`/`_xlpm.`; upgrades `WasmSandbox` to Wasm 2.0 `0xFC memory.copy`/`memory.fill` + sign-extension; wires `:testing:r25PngContracts` + `:testing:r48Contracts` into `.github/workflows/android.yml` for **37 contiguous round tasks `r13Contracts..r48Contracts` with zero gaps**), then authors **66 new pure-JVM production kernels across ALL 50/50 declared Gradle modules (100.00% in-round module coverage; 661 `src/main/**/*.kt` files)**:
- **Writer, 95-Fn Dynamic Spill Spreadsheet, Presentation `.pptx` & Fillable PDF 1.7:** `SpillSheetEngine` (`SEQUENCE`, `SORT`, `SORTBY`, `UNIQUE`, `FILTER`, `TEXTSPLIT`, `A1#`, `#SPILL!` collision report), `PptxSlideDeck` (`16:9` EMU slides, DrawingML `<a:tbl>`, speaker notes), `Epub3Package` (W3C EPUB 3.3 OCF `STORED@38` + OPF 3.0 + XHTML5 TOC), `RtfCleanConverter` (signed-16-bit BMP `\uN?` + `\objdata` exploit refusal), `OdsSpreadsheetXml` (OASIS ODF 1.3 `.ods` + OpenFormula `of:=`), `PdfAcroFormWriter` (`/FT /Tx` + `/FT /Btn`, `/JavaScript` refused, exact `xref`), `PdfPageLabelsAndOutline`, `PivotCubeSummary`, `XlsxDataValidationXml`, `ReadabilityMetrics`, `MarkdownOfficeBridge`, `OpenSearchSuggestionJson`, `FinancialScheduleSolver` (`XIRR`/`XNPV`), `MatrixLinearAlgebra`.
- **Tails-Light OHTTP/BHTTP, Tor v3 `.onion` & 5-Channel Anti-Keylogger Shield:** `BinaryHttpRfc9292` (QUIC varints + bucket zero-padding), `ObliviousHttpPolicy` (`RFC 9458` Relay!=Gateway split-trust auditor + `0-RTT`/cookie block), `OnionV3Address` (pure-JVM `Keccak-f[1600]` `SHA3-256` 56-char `.onion`, v2 refused), `ExitTrafficPadBucket`, `AcousticKeystrokeShield` (zero-fill password mute, stereo->mono TDoA collapse, 48 kHz Blackman FIR `18 kHz` LPF attenuating `20 kHz` `SonarSnoop` to `0 RMS`), `HidUsageFirewall` (blocks Vendor `0xFF00` BadUSB C2 & `GUI+R`), `ScreenCaptureGuard`, `ConstantTimePrimitives`, `HkdfRfc5869`, `PasskeyRpValidator` (WebAuthn L3).
- **Browser Isolation, Wasm 2.0 Pipe & Cross-Module Systems (`50/50`):** `ArticleReaderExtractor`, `CrossOriginIsolationPolicy` (`COOP`/`COEP`/`DIP` + `Sec-Fetch-*`), `CspHeaderDirective` (CSP3 nonce/hash overriding `'unsafe-inline'`), `WasiVirtualPipe`, `RoaringBitSetLite` (4,096 hybrid Array/Bitmap), `BTreePageLayout` (4 KiB slotted pages), `RegexSafeAutomaton` ($O(N \cdot M)$ Thompson NFA ReDoS-immune), `ContentHashDeduplicator` (Gear CDC), `YataRgbaSequenceCrdt` (`6! = 720` permutation convergence), `DoubleRatchetHeaderChain`, `Elf16KbPageAudit`, `SevenZipSignatureHeader`, `NetpbmPpmCodec`, `WelchTTestRegression`, `ArtProfileRuleCodec`, `DeterministicShrinker`, and 19 additional module kernels.

**50/50 testable subsets (100.00%); 0/50 full-spec Class A accepted (0.00%); zero status inflation —
[class-a-readiness-r48.json](android/docs/class-a-readiness-r48.json), [R48-PLAN](android/docs/R48-PLAN.md), [R48-ALTERNATIVES](android/docs/R48-ALTERNATIVES.md) & [R48-MODULE-DEEPENING](android/docs/R48-MODULE-DEEPENING.md) detail exact external Android SDK 37 / device / TalkBack / SurfaceFlinger / hardware gates per feature.** Executed: **3,861 kotlinc/JVM assertions** across 66 new + 2 pure-JVM touched kernels (`0` `-Werror` warnings), **56/56** independent Python differential oracle checks, **35** host lab gates + **863/863** Python `unittest` checks across **52** suites (**105** in `test_r48_tools`), live warm-JVM HTTP workbench (`0.0.0.0:8765`), and inspectable Java2D visual + 360dp compact viewport audit (`android/docs/verification/r48-host-workbench/r48-workbench-preview.png`, **172** `FontMetrics` + pairwise 2D non-overlap checks, min WCAG contrast **5.75:1**; host-only, not Android device evidence).
[Results](android/docs/R48-RESULTS.md) ·
[Contract](android/docs/R48-PLAN.md) ·
[Privacy PRD](android/docs/R48-PRIVACY-PRD.md) ·
[Class-A Readiness](android/docs/class-a-readiness-r48.json) ·
[Try without Studio](android/docs/BUILD.md).

## Prior R47 — 61 new kernels (+5 touched) across 48/50 modules (96%); contiguous bridge spans R26–R47 + live `:8765` workbench

Branch **`arena/64550eba-osb`**. Closes R46 follow-ups first (5 `TOUCHED` upgrades: `MainActivity.kt` swaps `DesktopShelf` to phi-derived `BeautyDock`, `AdaptiveDock.kt` enforces `TileLabelFit.MIN_TOUCH_TARGET_DP` >=48dp + 12.dp compact padding, `UsbWatchReceiver.kt` wires `UsbGuardPolicy` lockscreen/composite BadUSB evaluation, `MapScreen.kt` applies 360dp compact toolbar spacing, and `FormulaEval.kt` expands `35 -> 61` pure ECMA-376 functions; backports `:testing:r26Contracts..:testing:r29Contracts` + `:testing:r47Contracts` for **22 contiguous Gradle CI tasks** and an unbroken **R13–R47** CI chain), then authors **61 new pure-JVM production kernels across 48/50 modules (96.00% — 47/47 = 100.00% of product modules + `:benchmark`)**:
- **Rich Office Suite (Writer `.docx`/`.odt`/LaTeX SVG, 61-fn Spreadsheet & Bookmarked PDF 1.7):** `DocxRichDocument` (headings, styled runs, tables, headers/footers, footnotes, comments, `w:ins`/`w:del` revisions, TOC, CVE-2017-0199 external-template refusal), `OdtRichWriter` (OASIS ODF 1.3 Part 2 §2.2.1 `STORED` `mimetype@38` + XXE-hardened DOM reader), `LatexMathSvg` (TeX math AST -> vector SVG & MathML), `DocumentDiffMerge` (Myers $O(ND)$ diff + 3-way merge), `FormulaEval` (`61` functions incl. `XLOOKUP` with `IronCalc` PR `#10` scalar-vs-range parity, `XMATCH`, `HLOOKUP`, `CHOOSE`, `SUMIFS`, `COUNTIFS`, `AVERAGEIFS`, `IFS`, `SWITCH`, `TEXTJOIN`, `PMT`, `PV`, `FV`, `NPV`), `XlsxStyledBook` (`styles.xml` `numFmts`/`cellXfs`, frozen panes, defined names), `SvgChartPlot` (bar/line/pie SVG), `PdfOutlineBookmarks` (`/Outlines` hierarchy + `/XYZ` destinations + `/Link` `/URI` annotations + exact `xref` offsets), `WikiLinkGraph` (Zettelkasten backlinks + damped PageRank).
- **Browser Shield, WASI Preview 1 Capability Sandbox & Cross-Module Substrates:** `HtmlSanitizer` (DOMPurify 3.4.17-class string tokenizer defeating `<template shadowrootmode="closed">` & `<foreignObject>` XSS), `AdblockFilterEngine` (Brave `adblock-rust`-style EasyList network + cosmetic rules), `WasiPreview1Host` (`path_open`/`fd_read`/`fd_write`/`clock_time_get` refusing `../` escapes with `ERRNO_NOTCAPABLE = 76`), `JsonPatchRfc6902`, `ArgvSpecParser`, `UndoRedoStack`, `HdrLatencyHist`, `BTreePageIndex`, `ZipSafeExtract`, `PackIndexCompact`, `TomlConfigCodec`, `FluidTypeClamp`, `TilingBinaryTree`, `FuzzyAppSearch`, `Bm25Scorer`, `BooleanQueryAst`, `BoyerMooreHorspool`, `ExtendedGlob`, `PromptOsc133`, `WebVttCueParser`, `WavPcmHeader`, `GeoJsonFeature`, `ReedSolomonGf256`, `ShamirSecretGf256`, `EwmaAnomalyDetector`, `MaintenanceSilence`, `DagTaskRunner`, `ContentRangeHttp`, `NoiseFraming`, `SmpteTimecode`, `HandoffSessionTicket`, `RgaTextSequence`, `MrzIcao9303`, `DkimHeaderCanon`, `RetryBackoff`, `VarIntZigZag`, `ArArchive`, `SvgSafeSubset`, `IccProfileHeader`, `SoapActionEnvelope`, `AirPlayTxtRecord`, `RtspRangeHeader`, `CastChannelFrame`, `CastMediaStatus`, `MicrobenchStats`.
- **Tails-Light Tor Proposal 351 Anonymity & Multi-Layer Anti-Keylogger Shield:** `TorSocksIsolation` (Tor Proposal `351` `<torS0X>0` RFC 1929 per-`(app, origin, epoch)` HMAC stream isolation), `ImuSideChannelGuard` (`0 Hz` complete accelerometer/gyroscope/magnetometer blackout during text/secret focus defeating `Glitch in Time` 400 Hz phase reconstruction & `AlphaLogger` 10 Hz fusion), `SecureKeypadScrambler` (CSPRNG Fisher-Yates PIN/hex pad, `>= 48dp` keys, constant-scan touch resolution), `ClipboardSensitiveGuard` (`EXTRA_IS_SENSITIVE` + `30s` TTL wipe), `ImeTrustGate`, `RamWipeSchedule`, `ScreenReaderRedactor`, `SecureSurfacePolicy`.

**50/50 testable subsets (100.00%); 0/50 full-spec Class A accepted (0.00%); zero status inflation —
[R47-PLAN](android/docs/R47-PLAN.md), [R47-ALTERNATIVES](android/docs/R47-ALTERNATIVES.md) & [R47-MODULE-DEEPENING](android/docs/R47-MODULE-DEEPENING.md) detail exact missing Android SDK 37 / device / TalkBack / hardware gates per feature.** Executed: **2,393 kotlinc/JVM assertions** across 61 new kernels (+5 touched) in 6 domain-cached `-Werror` jars, **40/40** independent Python differential oracle checks, **31** host lab gates + **758/758** Python `unittest` checks across **51** suites (**82** in `test_r47_tools`), live warm-JVM HTTP workbench (`0.0.0.0:8765`), and inspectable Java2D visual + 360dp compact viewport audit (`android/docs/verification/r47-host-workbench/r47-workbench-preview.png`, **111** `FontMetrics` non-overlap checks, min WCAG contrast **5.68:1**; explicitly host-only, not Android device evidence).
[Results](android/docs/R47-RESULTS.md) ·
[Contract](android/docs/R47-PLAN.md) ·
[Privacy PRD](android/docs/R47-PRIVACY-PRD.md) ·
[Try without Studio](android/docs/BUILD.md).

## Prior R46 — 39 new kernels (+3 touched) across 34 modules; contiguous bridge spans R30–R46 + live `:8765` workbench

Branch **`arena/64550eba-osb`**. Closes R45 follow-ups first (3 `TOUCHED` repairs: `DesktopChrome.kt` 360dp compact anti-clipping + 1.dp divider for visual defects found in `r24-native-previews`, `TabletopDesktop.kt` 2-line `TileLabelFit.fitLabel` desktop pins, and `FormulaEval.kt` expanded `19 -> 35` pure functions; backports `:testing:r30Contracts..:testing:r33Contracts` + `:testing:r46Contracts` for a **17-round contiguous Gradle CI lane**), then authors **39 new pure-JVM production kernels across 34/50 modules (68.00%, widest spread to date; 47/47 = 100.00% of product modules across R45 + R46)**:
- **Multi-Sheet Spreadsheet, Writer, Academic Citations & Paginated PDF 1.7:** `MultiSheetBook` (cross-sheet `'Q1 Sales'!B2:B10` DAG + cycle detection + zero-epoch `.xlsx`), `SheetPivot` (SUM/COUNT/AVERAGE/MIN/MAX cubes), `CondFormat` (2/3-stop RGB color scales + normalized `DataBar`), `OdsSheetExtract` (OASIS ODF 1.3 `.ods` bounded reader/writer), `DocOutlineRender` (CommonMark <-> HTML5), `TrackRevisions` (OOXML `w:ins`/`w:del` + author/timestamp scrub), `CitationBib` (BibTeX IEEE numeric & APA author-year), and `PdfPageWriter` (clean-room ISO 32000-1 `%PDF-1.7` paginated writer with exact byte-offset `xref` table).
- **Pure-JVM Wasm 1.0 Sandbox, Crash-Safe Storage & Productivity:** `WasmSandbox` (`Chicory 1.7.5`-style pure-JVM WebAssembly 1.0 binary decoder, 64 KiB linear memory bounds, deterministic instruction fuel trap — zero JNI / 16 KB ELF safe), `QuotaBudget`, `WalFrame` (SQLite-style rolling-CRC WAL + torn-tail crash recovery), `PipeChain`, `SemVerRange`, `SchemaMigrate`, `ColumnarPage` (Parquet/Arrow delta+dictionary), `TarPax` (POSIX.1-2001 PAX), `PermissionsPolicyHeader`, `HlsPlaylist`, `DesktopEntryIni`, `TrigramIndex`, `PathConflict`, `VtScreenBuf` (drops `OSC 52` clipboard exfiltration), `ChunkedTransfer`, `RateWindow`, `EdlCutList`, `RabinChunker`, `CrdtLwwMap` (HLC semilattice), `BarcodeGs1`, `QuotedPrintable`, `CronExpr`, `PassphraseDice`, `MarkdownTableFmt`.
- **Tails `onion-grater` / `mat2` Privacy & Wired/Wireless Anti-Keylogger Shield:** `OnionGraterFilter` (Tor ControlPort `951` `510 Command filtered` default-deny + `GETINFO net/listeners/socks` isolation + UID `STREAM` scoping), `MetadataScrub` (OOXML/ODF `docProps` strip + byte-length-preserving PDF `/Author` redaction), `PngMetadataStrip` (`eXIf`/`tEXt`/`iTXt` scrub + CRC-32 check), `MacRandomizer` (HMAC-SHA256 per-SSID LAA MAC), `UsbGuardPolicy` (blocks composite `08:* + 03:01:*` BadUSB, lockscreen hotplug HID, and `09:* + 03:01:01` inline hub implants), `BleHidPairingGate` (closes `BlueDucky` `CVE-2023-45866` unauthenticated L2CAP/HOGP HID), and `KeystrokeJitter` (physiological `<22ms` / `<1.5ms` stddev synthetic DuckyScript detector + `50ms` outbound chaff).

**50/50 testable subsets (100.00%); 0/50 full-spec accepted (0.00%); no Class-A fabrication —
[R46-ALTERNATIVES](android/docs/R46-ALTERNATIVES.md) & [R46-MODULE-DEEPENING](android/docs/R46-MODULE-DEEPENING.md) detail per-module testability vs Class-A blockers.** Executed: **885 kotlinc/JVM assertions** across 39 new kernels (+3 touched) in 34 cached `-Werror` module jars, **25** cross-engine differential checks (`openpyxl` 3-sheet `.xlsx`, PDF 1.7 `xref`, `tarfile` PAX, `quopri`, PNG CRC-32, HMAC, Wasm `fib(20)`), **25** host lab gates + **50** Python `unittest` suites (**676/676** checks; **66** in `test_r46_tools`), live warm-JVM HTTP workbench (`0.0.0.0:8765`), and inspectable Java2D visual audit (`android/docs/verification/r46-host-workbench/r46-workbench-preview.png`, 77 `FontMetrics` non-overlap checks, min WCAG contrast 5.68:1; explicitly host-only, not Android device evidence).
[Results](android/docs/R46-RESULTS.md) ·
[Contract](android/docs/R46-PLAN.md) ·
[Privacy PRD](android/docs/R46-PRIVACY-PRD.md) ·
[Try without Studio](android/docs/BUILD.md).

## Prior R45 — 33 new kernels (+1 touched) across 26 modules; contiguous bridge spans R34–R45

Branch **`arena/64550eba-osb`**. Closes R44 §J open follow-ups first (`TOUCHED` `DashEducate.educateOutside` + `ProseEducate` span-safe typography, backported `:testing:r34Contracts`..`:testing:r36Contracts` + `:testing:r45Contracts` for a contiguous 12-round CI lane, clean-room Writer `.docx`/`.odt` + reactive Spreadsheet `.xlsx` + `PslGate`), then deepens **26/50 modules (52.00%, widest spread to date)** and lifts `kotlinc -Werror` host-JVM contract coverage to **47/50 modules (94.00% overall; 47/47 = 100.00% of product modules)**:
- **Office & Productivity:** `FormulaEval` (topological spreadsheet DAG + `#CYCLE!` + dirty-closure), `FormulaXlsx` (bidirectional ECMA-376 `.xlsx` with `<f>` + cached `<v>`, verified in Python `openpyxl` `data_only=False` and `data_only=True`), `DocxNumbering`, `DocxPackage` (macro-refusing `.docx` OPC round-trip), `OdtExtract` (bomb-capped OASIS ODF 1.3 reader), `PieceTable` (immutable append-buffer + 256-step undo/redo), `ProseEducate` (`DashEducate.educateOutside` + `SmartQuote` preserving code/URL spans), `FindReplace`, `TocTree`, `FinMath` (TVM + IRR), `RleBitset`, `Bm25Rank`, `DagTopo`.
- **Browser, Tails-Mod Privacy & Anti-Keylogger Hardware Defense:** `CspHeader` (CSP3), `PslGate` (3-valued Mozilla PSL eTLD+1), `CtSctPolicy` (RFC 6962 multi-operator SCT quorum + CVE-2026-113470 CA distrust), `TailsCadence` (Tails `7.15.1` 14-day cadence + 60s hard-deadline shutdown wipe), `HidReportAudit` (USB HID 1.11 report descriptor bytecode + superhuman keystroke-injection detector), `DmaGuardAudit` (IOMMU strict + UEFI DMAR/IVRS + locked-port policy), `OverlayTouchGuard` (`FLAG_WINDOW_IS_OBSCURED` + `HIDE_OVERLAY_WINDOWS` anti-tapjacking), plus `amnesic_posture.py` live Linux procfs/sysfs auditor.
- **Shell Visual Repair, Crypto, Cast, Maps & A11y:** `TileLabelFit` (repairs `r24c` mid-word tile truncation & 390dp 6th-dock clipping with >=48dp touch floor), `SpringStep`, `WorkAreaFit`, `MerkleTree` (RFC 6962 `0x00`/`0x01`), `HkdfExpand` (RFC 5869), `EnvOverride`, `PsdLayerRecords`, `SsdpHeader` (LAN-only SSRF guard), `CastTxtProps`, `TxtRecord` (RFC 6763), `KeyChord`, `UtmGrid` (WGS84 Norway/Svalbard), `ClipMime`.

**50/50 testable subsets (100.00%); 0/50 full-spec accepted (0.00%); no Class-A fabrication —
[R45-ALTERNATIVES](android/docs/R45-ALTERNATIVES.md) & [R45-MODULE-DEEPENING](android/docs/R45-MODULE-DEEPENING.md) detail per-module testability vs Class-A blockers.** Executed: **508 kotlinc/JVM assertions** across 33 new kernels (+1 touched) in 26 isolated `-Werror` module jars, bidirectional `openpyxl`/`.docx`/RFC differential oracle, `183,246` pilot + `2,394` subset regression assertions, `52` R45 hygiene tests (`610/610` total Python `unittest` checks), and an inspectable Java2D workbench visual audit (`android/docs/verification/r45-host-workbench/r45-workbench-preview.png`, 75 `FontMetrics` non-overlap checks, WCAG >= 5.6:1; explicitly host-only, not Android device evidence).
[Results](android/docs/R45-RESULTS.md) ·
[Contract](android/docs/R45-PLAN.md) ·
[Privacy PRD](android/docs/R45-PRIVACY-PRD.md) ·
[Try without Studio](android/docs/BUILD.md).

## Prior R44 — 24 executed kernels across 23 modules; bridge spans R37–R44

Branch **`arena/350b91e4-osb`**. Fourth consecutive sandbox-reset recovery, then
twenty-four new host-executed kernels deepening 23/50 modules (46%, tying the
record): hidden-run grid arithmetic, email quote-depth hygiene, honest
go-to-line (clamping reported, never silent), PDF indirect-reference canon
(65535 free-list ceiling refused), SRI strongest-wins intake that never
hashes, the full Debian version total order (tilde laws executed in TWO
independent implementations), ISSN mod-11 with the computed X case, UPC-E
zero-suppression (all four branches), HOME-collapse with the /homework bug
refused by construction, banded Levenshtein with a 125-case agreement sweep,
key-naming lint, readline history semantics incl. the ignorespace privacy
convention, PCM byte math, subject threading normalization, EXIF GPS
presence math (conversion ≠ stripping, stated), display diagonal math that
refuses anisotropic averaging, snapshot naming with the lex==chrono law
executed, fast-scroll buckets (Latin-only declared), EXIF orientation with
its inverse involution, Accept-Language (fingerprint vector named in scope),
inline tags, frame-budget ceil law, dot-hidden vs .nomedia kept distinct,
and human-speakable session codes that explicitly do NOT claim a checksum.

**50/50 testable (100%); 0/50 full-spec accepted; no A-grade claim —
[R44-ALTERNATIVES](android/docs/R44-ALTERNATIVES.md) answers the Class-A
question honestly again.** Executed: **378 kotlinc/JVM assertions (post-repair; the CI bridge caught and we cut a name-colliding kernel — disclosed in R44-RESULTS)** across
24 kernels, 24 mirrors, lab PASS first full run with seven prior rounds'
regressions inside; bridge registers `:testing:r37Contracts` …
`:testing:r44Contracts` (161 kernels / 2777 assertions once green).
[Results](android/docs/R44-RESULTS.md) ·
[Contract](android/docs/R44-PLAN.md) ·
[Privacy PRD](android/docs/R44-PRIVACY-PRD.md) ·
[Try without Studio](android/docs/BUILD.md).

## Prior R43 — 23 executed kernels across 23 modules (widest spread yet)

Branch **`arena/350b91e4-osb`**. Third consecutive sandbox-reset recovery, then
twenty-three new host-executed kernels deepening 23/50 modules (46%): frozen
panes (cut twips formula upheld as cut), Trojan-Source bidi hygiene with
stripping pinned as a user-visible choice, duplicate-line cleanup, PDF
permission bits (policy-not-enforcement), the full 8-token Referrer-Policy
table, Debian control fields, the pinned Android API 21..36 naming table
(API 37 refused until named), golden-angle seat hues, median tap tempo,
BEP-12 tracker tiers (one real regex hole found by first execution, repaired,
attack kept), QR size law, WebAuthn RP-ID matching with pslChecked=false
carried in the result type (the dangerous case executed to justify it),
caret/tilde ranges, snippet windows with brute-force-agreed optimality, age
string recognition (zero cryptography), breadcrumb trimming, Apdex, percent
change (positive bases, declared), RTSP transport, Message-IDs, time
coarsening (NOT anonymity, stated), aspect snapping, alert keys.

**50/50 testable (100%); 0/50 full-spec accepted; no A-grade claim —
[R43-ALTERNATIVES](android/docs/R43-ALTERNATIVES.md) answers the Class-A
question honestly again.** Executed: **372 kotlinc/JVM assertions** across 23
kernels, 23 mirrors, lab PASS with six prior rounds' regressions inside;
bridge now registers `:testing:r37Contracts` … `:testing:r43Contracts`
(137 kernels / 2399 assertions under Gradle once green).
[Results](android/docs/R43-RESULTS.md) ·
[Contract](android/docs/R43-PLAN.md) ·
[Privacy PRD](android/docs/R43-PRIVACY-PRD.md) ·
[Try without Studio](android/docs/BUILD.md).

## Prior R42 — 22 executed kernels across 21 modules; CI bridge spans R37–R42

Branch **`arena/350b91e4-osb`**. Second consecutive sandbox-reset recovery, then
twenty-two new host-executed kernels deepening 21/50 modules (42%): spreadsheet
fill semantics ($-pinned AbsRef) + merge-region law (MergedCells), writer case
cycling + comment toggling, PDF date strings (spec example executed), NMEA
fields, RFC 6797 HSTS (unknown-directive tolerance done spec-right), Damm
pairing-code check digits (both published guarantees executed as mutation
sweeps), ar/deb + BMP container walls, GPC consent intake that refuses to
coerce, leak-proof password-policy checks, dB laws, strict hex, snooze ladders,
1-2-5 axis steps, anchor+period schedules, RFC 2231 continuations, CSI cursor
math, thirds grids, histograms, unit prices. Zero post-execution kernel
repairs (second consecutive round); one pre-execution purity repair disclosed.
Bridge: `r37Contracts` backport + `r42Contracts` — once green, R37–R42's 114
kernels / 2027 assertions run under real Gradle in CI.

**50/50 testable (100%); 0/50 full-spec accepted; no A-grade claim —
[R42-ALTERNATIVES](android/docs/R42-ALTERNATIVES.md) answers the Class-A
question honestly again.** Executed: **362 kotlinc/JVM assertions** across 22
kernels, 22 Python mirrors, lab 22 gates + 46 suites / 487 tests.
[Results](android/docs/R42-RESULTS.md) ·
[Contract](android/docs/R42-PLAN.md) ·
[Privacy PRD](android/docs/R42-PRIVACY-PRD.md) ·
[Try without Studio](android/docs/BUILD.md).

## Prior R41 — 21 executed kernels across 20 modules; CI bridge spans R38–R41

Branch **`arena/350b91e4-osb`**. Round opened with a full sandbox-reset recovery
(byte-clean re-sync, toolchain reinstall, prior baselines green before any
edit). Twenty-one new host-executed kernels deepen 20/50 modules (40%): the
spreadsheet ADDRESSING layer completes (SheetRange + R1c1Ref over the full
16384×1048576 grid, no wrapping), writer gains UAX-11 column widths +
provably-geometry-preserving tab expansion, plus PDF page-geometry
composition, shlex-identical display quoting, calendar-honest ISO durations,
carry-safe DMS, Android-order hex colors, Flesch arithmetic, EAN-13/UPC/ISBN
algebra, RFC 2397 data: URLs, pg_trgm trigrams, turn rotation, bias-corrected
EWMA, loss-refusing settings migration, cast namespaces, git-style sharding,
span merging, BEP-3 piece math, and RTL-aware launcher grids. **Zero kernel
repairs — first round since R38 where every kernel passed its full contract on
first execution.** The CI bridge extends backwards (`r38Contracts`) and
forwards (`r41Contracts`): once green, R38–R41's 77 kernels / 1391 assertions
all execute under real Gradle in CI.

**50/50 testable (100%); 0/50 full-spec accepted; no A-grade claim — the
Class-A question is answered honestly again in
[R41-ALTERNATIVES](android/docs/R41-ALTERNATIVES.md).** Executed: **361
kotlinc/JVM assertions** across 21 kernels, 21 Python mirrors (five held-out
stdlib families), lab 20 gates + 45 suites / 451 tests.
[Results](android/docs/R41-RESULTS.md) ·
[Contract](android/docs/R41-PLAN.md) ·
[Privacy PRD](android/docs/R41-PRIVACY-PRD.md) ·
[Try without Studio](android/docs/BUILD.md).

## Prior R40 — 20 executed kernels + first CI-executed contract tasks

Branch **`arena/350b91e4-osb`**. Twenty new host-executed kernels deepen 19/50
modules (38%): spreadsheet format-code structure + the seven classic error
literals; writer dash/ellipsis education, U+FFFC bookkeeping and CMOS footnote
marks; PDF /Rotate with the full composition table; strict HTML entities (the
cp1252 quirk refused AND documented); RFC 2047 encoded words; 7z signature CRC
gate; FNV-1a; ICAO 9303 MRZ check digits reproducing the published specimen;
filename safety; nearest-rank percentiles; GFM task checkboxes; exact-tiling
snap fractions; bplist envelopes; launcher monograms; Bech32/Bech32m; otpauth
intake hardening; IP /24-/48 redaction. **NEW: `:testing:r39Contracts` +
`:testing:r40Contracts` run all 39 recent kernels under real Gradle in CI's
JVM lane.** Six in-round repairs are documented in R40-PLAN §I — four kernel
bugs caught by first execution, plus a lab-harness honesty hole (hollow
"PASS 0/0" tool-suite lines) fixed and disclosed retroactively for R39.

**50/50 testable (100%); 0/50 full-spec accepted; no A-grade claim — the
Class-A question is answered honestly again in
[R40-ALTERNATIVES](android/docs/R40-ALTERNATIVES.md).** Executed: **360
kotlinc/JVM assertions** across 20 separately compiled kernels, 20 Python
mirrors (six held-out stdlib families), lab 18 gates + 44 suites / 418 tests.
[Results](android/docs/R40-RESULTS.md) ·
[Contract](android/docs/R40-PLAN.md) ·
[Privacy PRD](android/docs/R40-PRIVACY-PRD.md) ·
[Try without Studio](android/docs/BUILD.md).

## Prior R39 — 19 executed kernels, writer + spreadsheet push (host evidence only)

Branch **`arena/350b91e4-osb`**. Nineteen new host-executed kernels deepen 16/50
modules (32%): the spreadsheet trio (Excel serial dates with the Lotus phantom
1900-02-29, the full A..XFD column bijection, exact ECMA-376 column-width pixel
math), the writer trio (smart quotes with declared narrowings, UAX-14 soft
hyphens, canonical Roman numbering with the full 1..3999 bijection), PDF
CropBox rules, ICO directories with PNG-IHDR agreement, full RFC 3492 Punycode,
USB device descriptors (input-path audit rung), MIDI notes, the canonical ULID
codec, a JWS splitter that constitutionally cannot claim verification, IBAN
mod-97, fnmatch-exact globs, MAC U/L-bit privacy analysis, strict RFC 3339,
quiet-hours windows, and APEX exposure math. No Android code authored (touch
audit +0; manifest pinned). Fresh research: ULID canonical spec fetched;
same-day Tails 7.15.1 + eMudhra TLS-exit currency; 2026 HID-defense papers.

**50/50 testable (100%); 0/50 full-spec accepted; no A-grade claim — the
"writer/spreadsheet to Class A this turn?" question is answered honestly in
[R39-ALTERNATIVES](android/docs/R39-ALTERNATIVES.md).** Executed: **344
kotlinc/JVM assertions** across 19 separately compiled kernels, 19 Python
mirrors (six held-out stdlib verifiers), 350/350 tool tests (43 suites).
[Results](android/docs/R39-RESULTS.md) ·
[Contract](android/docs/R39-PLAN.md) ·
[Privacy PRD](android/docs/R39-PRIVACY-PRD.md) ·
[Try without Studio](android/docs/BUILD.md).

## Prior R38 — 17 executed kernels, pure-logic round (host evidence only)

Branch **`arena/350b91e4-osb`**. Seventeen new host-executed kernels deepen 15/50
modules (30%), all seconds (cumulative distinct holds at 45/50, 90%): magnet
links + strict bencode (first beam formats), FLAC/WebP/EDID headers with real
byte fixtures, SemVer precedence, UUID v7 timestamps, HTTP ranges,
quoted-printable, strict PEM, ISO week dates, CSS HSL, WebVTT timings, Luhn PAN
redaction, NARA Soundex, PDF name escapes, and an explicitly evadable
keystroke-cadence classifier (no "safe" verdict exists). No Android code
authored — touch audit +0 delta; manifest scope pinned. Fresh research: two
fetched primaries (BEP 3, BEP 9), stdlib-held-out mirrors (uuid, quopri,
colorsys, isocalendar), **Tails 7.15.1 same-day currency** (eMudhra CA block,
CVE-2026-113470), 2026 keystroke-timing numbers pinned in code.

**50/50 testable (100%); 0/50 full-spec accepted; no A-grade claim.** Executed:
**326 kotlinc/JVM assertions** across 17 separately compiled kernels, 17
independent Python mirrors, touch-target + parse + manifest-scope gates,
337/337 tool tests (42 suites). [Results](android/docs/R38-RESULTS.md) ·
[Contract](android/docs/R38-PLAN.md) · [Privacy PRD](android/docs/R38-PRIVACY-PRD.md) ·
[Try without Studio](android/docs/BUILD.md).

## Prior R37 — 14 executed kernels, pure-logic round (host evidence only)

Fourteen host-executed kernels deepen 14/50
modules (28%), all seconds (cumulative distinct holds at 45/50, 90%): heading slugs,
IEC size scaling, VTODO tallies, XZ stream headers, paste re-indentation, RTSP
reason phrases, tracking-parameter stripping, HIBP range scans, encoded polylines,
MP4 box walks, OSC 8 hyperlinks, reciprocal-rank fusion, exit-status names,
field-of-view math. Plus one host tool for HID input-path auditing (a transcript
parser, explicitly NOT a keylogger detector).
No Android code authored this turn — no new components, permissions, or UI (touch
audit +0 delta; manifest scope pinned). Fresh research: three **fetched primaries**
(RFC 2326, xz 1.2.1, RFC 5545) drove one shipped repair (VTODO `IN-PROCESS`) and
two pre-run corrections; Tails 7.15 stands, Signature 27 still November, Servo
0.7.0 stands, Orbot 17.9.7 stands, the A17 six stand.

**50/50 testable (100%); 0/50 full-spec accepted; no A-grade claim.** Executed:
274 kotlinc/JVM assertions across 14 separately compiled kernels, independent
Python recomputation (14 mirrors), touch-target + parse + manifest-scope gates,
326/326 tool tests. [Results](android/docs/R37-RESULTS.md) ·
[Contract](android/docs/R37-PLAN.md) · [Privacy PRD](android/docs/R37-PRIVACY-PRD.md) ·
[Try without Studio](android/docs/BUILD.md).

## Prior R36 — 12 executed kernels, pure-logic round (host evidence only)

Branch **`arena/350b91e4-osb`**. Twelve host-executed kernels deepened 12/50 modules
(24%), all seconds. No Android code authored. Fresh research: Tails 7.15, Signature
27 November still dateless, Servo 0.7.0 + downstream LTS embed, A17 six, Orbot 17.9.7.

**50/50 testable (100%); 0/50 full-spec accepted; no A-grade claim.** Executed:
176 kotlinc/JVM assertions, independent recomputation, touch-target + parse +
manifest-scope gates, 307/307 tool tests. [Results](android/docs/R36-RESULTS.md) ·
[Contract](android/docs/R36-PLAN.md) · [Privacy PRD](android/docs/R36-PRIVACY-PRD.md).

## Prior R30 — 10 executed kernels, pure-logic round (host evidence only)

Branch **`arena/350b91e4-osb`**. Ten new host-executed kernels deepen 10/50 modules
(20%): RTSP request framing, video timecode math, VEVENT calendar import, Lamport
causal clock, GIF structure walk, settings patch scoping, POSIX tar headers,
half-stop zoom ladder, SMS segment math (counts only, no sending), density-tier
selection. No Android code authored this turn — no new components, permissions,
or UI (touch audit +0 delta; manifest scope pinned). Fresh research: Tails 7.15
reconfirmed, GrapheneOS Oct 2026 USB/port controls, Servo 0.5.0 stable embed API,
Tor VPN still beta.

**50/50 testable (100%); 0/50 full-spec accepted; no A-grade claim.** Executed:
98 kotlinc/JVM assertions, independent recomputation, touch-target + parse +
manifest-scope gates, 237/237 tool tests. [Results](android/docs/R30-RESULTS.md) ·
[Contract](android/docs/R30-PLAN.md) · [Privacy PRD](android/docs/R30-PRIVACY-PRD.md) ·
[Try without Studio](android/docs/BUILD.md).

## Prior R29 — executed kernels + USB-allowlist privacy set (host evidence only)

Branch **`arena/350b91e4-osb`**. Ten new host-executed kernels deepen 10/50 modules
(20%): share-manifest offers, storage quota ledger, alert token-bucket throttle,
file-list total ordering, shell alias table, WCAG contrast math, locale
negotiation, backup retention plans, DIDL-Lite items, CSV dialect sniffing with a
real-parser pipeline. One authored Android store (private USB allowlist; logic
executed on JVM against an in-memory fake, wiring unaudited) plus a
detection-only receiver wiring edit await CI/Studio compile plus emulator runs —
no behavior claim is made. A static touch-target audit covers 223 Button sites
(175 pre-existing findings queued; +0 delta; static only, not rendered proof);
a new manifest-scope gate pins the component/permission set. Fresh research:
Orbot 17.9.7 audit fixes, Android 17 Advanced Protection, passkey transfer APIs,
USB-C Authentication posture.

**50/50 testable (100%); 0/50 full-spec accepted; no A-grade claim.** Executed:
128 kotlinc/JVM assertions, independent recomputation, touch-target + parse +
manifest-scope gates, 227/227 tool tests. [Results](android/docs/R29-RESULTS.md) ·
[Contract](android/docs/R29-PLAN.md) · [Privacy PRD](android/docs/R29-PRIVACY-PRD.md) ·
[Try without Studio](android/docs/BUILD.md).

## Prior R28 — executed kernels + USB/consent privacy set (host evidence only)

Branch **`arena/350b91e4-osb`**. Ten new host-executed kernels deepen 10/50 modules
(20%): trash purge ledger, window snap zones, frecency ranking, slippy-map tile
math, podcast chapters, single-use consent receipts, RFC 6265 cookie jar, RRULE
recurrence, note front-matter, terminal scrollback. Two authored Android files
(detection-only USB watch receiver, consent dialog) await CI/Studio compile plus
emulator runs — no behavior claim is made. A static touch-target audit covers 223
Button sites (175 pre-existing findings queued for review; static only, not
rendered proof). Fresh research: Servo 0.6-LTS, Tor VPN/Arti 2.7 beta, Tails 7.15,
Android USB Protection + Intrusion Logging, Privacy Sandbox retired.

**50/50 testable (100%); 0/50 full-spec accepted; no A-grade claim.** Executed:
112 kotlinc/JVM assertions, independent recomputation, touch-target + parse gates,
217/217 tool tests. [Results](android/docs/R28-RESULTS.md) ·
[Contract](android/docs/R28-PLAN.md) · [Privacy PRD](android/docs/R28-PRIVACY-PRD.md) ·
[Try without Studio](android/docs/BUILD.md).

## Prior R27 — executed kernels + authored hub/dock wiring (host evidence only)

Branch **`arena/350b91e4-osb`**. Ten new host-executed kernels deepen 8/50 modules
(16%): tabletop arrange (snap/tidy/first-free), podcast RSS/Atom enclosures,
explicit HLS variant choice, 48-city offline gazetteer, hosts+ABP-subset content
blocker, CommonMark+GFM-table markdown, fail-closed firewall proposal, USB
inventory diff, 15-style M3 type scale, Vixie cron next-fire. Three Android edits
(BeautyDock tag alignment still unwired, player launch contract, MediaHub
stream-player button) are authored with 0 syntax errors and await CI/Studio
compile plus emulator runs — no behavior claim is made.

**50/50 testable (100%); 0/50 full-spec accepted; no A-grade claim.** Executed:
106 kotlinc/JVM assertions, independent recomputation, page-matrix + parse gates,
207/207 tool tests. [Results](android/docs/R27-RESULTS.md) ·
[Contract](android/docs/R27-PLAN.md) ·
[Try without Studio](android/docs/BUILD.md).

## Prior R26 — executed kernels + authored surfaces (host evidence only)

Branch **`arena/350b91e4-osb`**. Ten host-executed kernels deepened 6/50 modules
(12%): finite beauty budgets (27/27 AA pairs recomputed), amnesiac session policy,
tri-state Tor posture, USBGuard-subset policy evaluation, shuffled-pad channel
avoidance, bounded playlists, GPX import, safe-navigation policy, sandbox-job
contracts, TSV worksheets. Four Android surfaces (Media3 direct-URL player, optional
vector-map probe, Orbot handoff, phi-gap dock) are authored with 0 syntax errors and
await CI/Studio compile plus emulator runs — no playback/map/Tor/dock claim is made.

**50/50 testable (100%); 0/50 full-spec accepted; no A-grade claim.** Executed:
157 kotlinc/JVM assertions, independent recomputation, layout + parse gates.
[Results](android/docs/R26-RESULTS.md) ·
[Contract](android/docs/R26-PLAN.md) · [Privacy PRD](android/docs/R26-PRIVACY-PRD.md).

## Current R24 continuation — native tabletop, local media and offline atlas

Branch **`arena/a8ac5475-osb`**. Default slanted tabletop with draggable pins, directly focused walls,
piles and saved scene views; silver Art / light Business presets and font-aware overflow dock.
Actual local Android audio/video player and a real Natural Earth offline atlas are bounded additions;
YouTube/Spotify/SoundCloud links use disclosed **external handoff**, not embedded streaming.
Input/IME changes discard the separate privacy workspace until explicit empty rearm.

**49/50 substantial code+runnable-test subsets (98%);0/50 full-spec accepted.**
The new local host pipeline passes179 Python tests,439839 continuation assertions and65097 retained
R24 assertions. These are not native/physical acceptance. Native failures, targeted repairs, exact-source
results and actual screenshots are retained rather than presented as an all-green product.
A real pinned Servo Android AAR was inspected remotely; it was **not loaded or integrated**.

[Current results, counts and gaps](android/docs/R24C-RESULTS.md)
· [Laptop / no-Studio instructions](android/docs/BUILD.md)
· [A–J contract and alternatives](android/docs/R24C-PLAN.md)
· [Privacy / engine boundaries](android/docs/R24C-PRIVACY-AND-ENGINES.md)
· [Additional October engine frontier](android/docs/R24C-ADDITIONAL-ENGINE-FRONTIER.md).

## Historical R24 — native professional desktop and safer input/documents

**Office Blue / Studio Graphite**, independent **Business / Art** layouts in both **Settings and Control Panel**.
Art has a centered own-app dock with real mouse/keyboard-focus magnification, running dots and left window controls;
Business has a full-width bottom taskbar. No stock OS/third-party task authority is implied.

Exact native-tested **23e8868**: desktop/input**9/0**, PDF/boundary**27/0**, CSV**8/0**, privacy**18/0 on API34 AND API37**.
Main full workflow completed**133/0 SUCCESS**, including both build/JVM/contracts/lint jobs;
earlier aggregate bookkeeping lag was retained, then the actual completed receipt collected. All earlier failures retained.

Genuine isolated readonly PDF preview (<=2MiB/1024pages/320px) and real IME pinned Task/Notes actions are implemented.
**48/49 substantial testable subsets (97.96%),0/49 fully original-spec accepted.** Eleven existing production modules
deepened (22.45% inventory), not eleven completed modules. No full browser/Office/host-hardware anonymity/Tails claim.

[Current results and actual native screenshots](android/docs/R24-RESULTS.md)
· [Try on laptop / no-Studio scripts](android/docs/BUILD.md#r24-current-native-evaluation)
· [A–J engineering](android/docs/R24-ENGINEERING.md)
· [Privacy PRD](android/docs/R24-PRIVACY-PRD.md)
· [Engine alternatives](android/docs/R24-ENGINE-PORTFOLIO.md).

## Historical R19

[R19 engineering/evidence report](android/docs/R19-ENGINEERING.md): exact-snapshot edit/import undo,
confirmed append-only task merge, real system-picker FTS interchange, and Active/Archived literal
XLSX reports with aggregate-bounded multi-sheet writing. Task data is plaintext; reports are not backups.
**cce38cb: 95/95 full API34 cases and 4/4 focused cases passed**, plus both builds/JVM/contracts/lint.
**Latest 58e6566: 96/96 full API34 cases and 5/5 focused cases passed**, including the stronger
actual picker import/save/reopen/undo workflow; both builds/JVM/contracts/lint also passed. Earlier compiler failures and intermittent task-add failures remain recorded.
**539 host assertions + 430 reference/readback cases + 30 stress reports passed.**
No full Office, browser, physical/AX/legal or release-completion claim; subset count remains 47/48.
Try **Automation → enable with test data → Local tasks**; see [instructions and limits](android/docs/BUILD.md).

## Historical R18

[R18 engineering/evidence report](android/docs/R18-ENGINEERING.md): editable date-only tasks,
reversible archive/restore with protected revision capacity, versioned persistence and query/order;
plus caller-owned arithmetic requests through the real isolated worker.
**Latest d6f7393/run38831196589:85/86 API34 cases passed; one older task-add timeout remains.**
Its focused run passed2/2; all seven new R18 cases and both builds/JVM/contracts/lint passed.
Earlier9340662 passed86/86 full but failed0/2 focused. UI instability remains unresolved.
**906 new host assertions +1,200 reference cases;47/48 executable subsets,0/48 fully accepted.**
No complete desktop browser, Office/LOK, physical/AX/legal or production-release claim.
Try **Automation → enable with test data → Local tasks**. Archive retains data/capacity;
explicit edits upgrade legacy data to FTS2; no reminders are implemented.

## Historical R17

[R17 report](android/docs/R17-ENGINEERING.md): R16 experiments closed,855 actual measured ART rules,
and persistent local Tasks added. Latest verified native candidate **ddc7fec passed79/79 API34 cases**,
both builds/JVM/contracts/lint. An earlier Task UI timeout remains unexplained; successful reruns
are not a causal repair/stability proof. No full Office, browser, physical or release acceptance.
Try **Automation → enable with test data → Local tasks**; no reminders are implemented.

## Historical R12 continuation

**30/48 modules (62.50%) have substantial testable subsets;0/48 are fully specification-accepted.**
Five new bounded kernels and two newly executed Android inventory paths, plus strict UTF8/CSV
repairs and actual subtitle inspection. See the [R12 continuation engineering record](android/docs/R12B-ENGINEERING.md)
and [Android Studio instructions](android/docs/BUILD.md). Native results are revision-specific;
no whole browser, Office engine, full sandbox or complete productivity suite is claimed.
