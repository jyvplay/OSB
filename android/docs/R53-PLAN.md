# Round 53 (`R53`) Architecture & Execution Plan — Strict Monotonic Expansion + Final PR Merge Readiness

## 1. Execution Order Mandate
1. **Phase 1 (Complete & Verify FIRST)**:
   - Expand **`Recall`** Browser Media Recording System from 4 methods to **6 distinct, production-grade methods** (`T1` Direct/HTTP-206, `T2` HLS/DASH->fMP4, **`T2.5` WebRTC RFC 3550/7587 RTP Opus/VP8 Jitter-Buffer De-packetizer**, `T3` Canvas/WebAudio WAV/Opus, `T4` Native `MediaProjection`+`AudioPlaybackCapture`, **`T5` ISO 28500 `WARC/1.1` Web Archive Bundle**) + **`RecallAudioSilenceTrackSplitter`** across all 3 folder delineation modes (`BY_URL_NAME`, `BY_PAGE_TITLE`, `BY_CUSTOM_TEMPLATE`), preserving strict DRM/`FLAG_SECURE`/`ALLOW_CAPTURE_BY_NONE`/Incognito refusal.
   - Deepen **all `50/50 = 100.00%` Gradle modules** with **`96` new pure-JVM kernels** (`> 91` in R52):
     - `FormulaEval.kt`: `260 -> 300` pure functions (`+40` complex-number, fixed-income coupon/duration, and statistical functions).
     - `SpillSheetEngine.kt`: `42 -> 48` spilled dynamic-array operations (`HANKEL`, `TOEPLITZ`, `VANDERMONDE`, `KRONECKER`, `CONVOLVE1D`, `PARETOGRID`).
     - `WasmSandbox.kt`: WebAssembly 3.0 `0xFC 0x08..0x0B` bulk-memory operations (`memory.init`, `data.drop`, `memory.copy` overlap-safe, `memory.fill`) + passive data segments.
     - Writer/Office/PDF: `<w:tblGrid>` table layout solver, `<m:oMathPara>` OMML equation builder, `.odp` presentation package writer, PDF `/ByteRange` signature planner, 2-column XY-cut PDF text reflow.
     - Tails-Light & Hardware Shield `#26`–`#30`: Tor Proposal 329 `Conflux` multipath scheduler/reassembler, clean-room `Apache-2.0` `TailsMetadataAnonymizer` (PNG/JPEG/MP4/MP3/PDF/OOXML), `TempestSubpixelEmsecDitherer`, `BleHogpKeystrokeFirewall`, `VerifiedBootVbmetaDigestAuditor`, `AmbientLightKeystrokeShadowGuard`, `DramRowhammerRefreshMonitor`.
2. **Phase 2 (Execute Strictly AFTER Phase 1)**:
   - Upgrade **Right-Click Context Menu**: add cascading submenu geometry (`ContextMenuSubmenuCascadeGeometry` + `FusionContextMenuSubmenuHost.kt`), mnemonic key jump, and 7-OS width benchmark (`180..280 dp`, never full-width).
   - Upgrade **Original `List` / `Grid` / `Columns` Vector Logos**: add multi-DPI pixel-grid crisp hinting (`ViewModePixelGridCrispHinting` across `16/20/24/32/48 px`) and wire across `MediaHub.kt`, `VaultScreen.kt`, `NotesScreen.kt`, `FilesScreen.kt`, `RecallWorkspaceScreen.kt`, `MainActivity.kt`, and `Shelf.kt` + `FilesGalleryInspectorBar.kt`.
   - Upgrade **Bottom-Right Translucent Stacked `Undo` Notifications**: add interactive fan-out expansion (`UndoDeckInteractiveFanController` + `StackedUndoFanOverlayBar.kt`), TTL countdown fraction, and batch `Undo Latest` / `Dismiss Expired` while keeping compact bottom-right height `< 9%` of desktop viewport.
   - Render and visually inspect **6 `1880×1400` Java2D PNG sheets** (`docs/verification/r53-host-workbench/`).
3. **Phase 3 (Verification + Git Commit + Push + PR + Merge into `main`)**:
   - Verify `run_r53_contracts.py` (`> 24,000` assertions, `66` artifacts), `check_r53_reference.py` (`> 380` oracle checks), `check_r26_android_syntax.py` (`59/59 PARSE OK`), `check_project.py`, all `57` Python `unittest` suites (cold & warm), push `arena/64550eba-osb`, open PR to `main`, verify CI, and merge into `main` via `gh pr merge <number> --merge`.
