# R52 Formal A–J Engineering Contract — `Recall` 4-Tier Media Archiver, 50/50 Deepening, & Ergonomic Chrome

## RUNTIME HONESTY
- **Available & Executed in Sandbox (`H+`):** Linux x86_64 host, OpenJDK 17 (`17.0.13`), `kotlinc 2.2.21` (`-Werror`), Python `3.11` (`openpyxl`, `zlib`, `zipfile`, `hashlib`, `struct`, `xml.etree`), Java2D headless `BufferedImage` + `FontMetrics` rasterizer, `read_file` direct visual image inspection, and `start_process` live `:8765` warm-JVM workbench preview.
- **Unavailable in Sandbox (`H-`):** Android SDK Platform 37 (`android.jar`, `aapt2`, `d8`, `R8`), physical/emulated Android ART runtime, `WebView`/`GeckoView` network stack, `MediaProjection` / `AudioPlaybackCapture` / `SurfaceFlinger` `FLAG_SECURE` HALs, TalkBack `AccessibilityService`, and `16 KB` ELF `.apk` install execution.
- **Mandatory Ledger Invariant:**
  - **Host-verified testable-subset modules:** `50 / 50` (`100.00%`)
  - **Full-specification Class A accepted modules:** `0 / 50` (`0.00%` — zero status inflation; `settings.gradle.kts` remains strictly `50` modules).

## A. FORMAL MODEL
Let the 50-module workspace state be $W = (M_{1..50}, K_{R52}, U_{R52}, R_{\text{Recall}}, C_{\text{UI}})$ where:
1. $|M| = 50$, $|K_{R52}| = 90 > 85$ new pure-JVM `.kt` kernels (`zero` `android.*` imports, globally unique stems), $|U_{R52}| = 20 > 16$ touched files (`6` pure-JVM engines + `14` Android/Compose UI files).
2. **`Recall` Media Archiver** $R_{\text{Recall}} = (\mathcal{D}, \{T_1, T_2, T_3, T_4\}, \mathcal{M}_{\text{SHA256}}, \mathcal{P}_{\text{DRM}})$ maps each viewed media observation $e \in \{\text{IMAGE}, \text{VIDEO}, \text{MUSIC}\}$ under folder-delineation mode $d \in \{\text{BY\_URL\_NAME}, \text{BY\_PAGE\_TITLE}, \text{BY\_CUSTOM\_TEMPLATE}\}$ to a canonical traversal-free archive path $p = \mathcal{D}(d, e) \subset \text{root}_{\text{Recall}}$ via tier selector $\tau(e) \in \{T_1, T_2, T_3, T_4\}$, subject to refusal predicate $\mathcal{P}_{\text{DRM}}(e) = \text{REFUSE}$ whenever EME/Widevine/FairPlay/`SAMPLE-AES`/`ALLOW_CAPTURE_BY_NONE`/`FLAG_SECURE` is present.
3. **Ergonomic Chrome** $C_{\text{UI}} = (G_{\text{ctx}}, V_{\text{icons}}, S_{\text{undo}})$ enforces:
   - Right-click context menu width $w_{\text{ctx}} \in [180\text{dp}, \min(280\text{dp}, \lfloor 0.34 \cdot W_{\text{vp}} \rfloor)]$ (with compact-phone clamp $[168\text{dp}, \min(240\text{dp}, W_{\text{vp}} - 24\text{dp})]$ when $W_{\text{vp}} < 540\text{dp}$, always $< 0.67 \cdot W_{\text{vp}}$ on `360dp` phone and $\le 0.153 \cdot W_{\text{vp}}$ on `1840px` desktop — NEVER full-width!).
   - Original from-scratch vector logos $V_{\text{icons}} = \{\text{List}, \text{Grid}, \text{Columns}\}$ with distinct geometric primitives and zero borrowed paths.
   - Bottom-right front-to-back translucent notification stack $S_{\text{undo}}$ anchored at $(W_{\text{vp}} - 16\text{dp} - w_{\text{card}}, H_{\text{vp}} - \text{dockH} - 16\text{dp} - h_{\text{card}})$ with $w_{\text{card}} \le \min(340\text{dp}, W_{\text{vp}} - 24\text{dp})$, depth $k \in \{0, 1, 2\}$, $\alpha_k = 0.88 - 0.20k$, $\text{scale}_k = 1.0 - 0.05k$, $\Delta y_k = -10k\text{ dp}$.

## B. OUTCOME SPACE (`H+` / `H-` / `H∂`)
- **`H+` (Verified Host Subset):** Pure-JVM execution + Python 3.11 differential verification of all 4 `Recall` recording tiers (Tier 1 HTTP/206 + `srcset` + `data:` harvester, Tier 2 RFC 8216 HLS `#EXT-X-MAP`/`#EXT-X-BYTERANGE`/`AES-128` + MPEG-DASH MPD + ISO 14496-12 `ftyp`/`moov`/`moof`/`mdat` fMP4 assembler, Tier 3 `<canvas>` RGBA + WebAudio Float32 TPDF-dithered WAV/Ogg-Opus siphoner, Tier 4 native viewport-cropped `MediaProjection` + `AudioPlaybackCapture` planner & A/V PTS drift compensator), `FormulaEval` (`252` fns), `SpillSheetEngine` (`42` ops), `WasmSandbox` (`0x12`/`0x13` `O(1)` tail calls), `25`-vector Tails/hardware shield, right-sized context menu, original `List`/`Grid`/`Columns` vector logos, and bottom-right stacked translucent Undo notifications.
- **`H-` (Explicitly Refused / Unverified):** Zero circumvention of Widevine/PlayReady/FairPlay/`SAMPLE-AES` DRM, `ALLOW_CAPTURE_BY_NONE`, or `FLAG_SECURE`; `0/50` full-spec Class A modules claimed without Android SDK 37 + hardware HAL execution.
- **`H∂` (Boundary Conditions):** Malformed `..`/`%2e%2e`/NUL URLs or titles, `AES-128` `#EXT-X-MAP` missing mandatory `IV` per RFC 8216 §4.3.2.5, fMP4 `traf` with `tfdt` out-of-order after `trun`, A/V clock drift $> 20\text{ ms}$, `360x800` compact phone context menu near right/bottom viewport edge, and burst of `12` rapid Undo notifications.

## C. FRONTIER & TWO-STAGE EXECUTION ORDER
1. **Task Block 1 (Execute FIRST to 100% completion):**
   - `Recall` 4-tier automatic image/video/music recording & URL/Title/Custom folder-delineation system across `:core:files`, `:feature:files`, `:feature:browser`, `:feature:media`, `:feature:capture`, and `:app` + `50/50` module pure-JVM & engine deepening (`FormulaEval` `252` fns, `SpillSheetEngine` `42` ops, `WasmSandbox` `0x12`/`0x13` tail calls, Writer/ODF/PDF, Tails Prop-327 PoW, and vectors `#21–#25`).
2. **Task Block 2 (Execute strictly AFTER Task Block 1 is complete):**
   - Right-sized right-click context menu (`RightClickContextMenuGeometry` + `FusionContextMenuPopup` + `MainActivity`/`FilesScreen`/`:8765`), original from-scratch `List`/`Grid`/`Columns` vector logos (`FusionViewModeVectorIcons` + `FilesScreen` Miller Columns/Grid/List + `Recall` + `:8765`), and bottom-right translucent Z-stacked Undo/action notifications (`StackedUndoNotificationDeck` + `MainActivity`/`EditorScreen`/`:8765`), confirmed via Java2D + `read_file` visual inspections!

## D. NEGATIVE SPACE (`>= 18` Failure Shapes + Modal Shortcut Test)
1. Path traversal in URL/page-title/custom Recall folder (`https://evil.test/../../etc/passwd` or `<title>CON/aux:..\secret</title>`).
2. Duplicate re-download bloat when viewing the same image/song/video across multiple tabs (solved via SHA-256 content-addressed dedup + hardlink/reference manifest).
3. Truncated HTTP `206 Partial Content` range assembly with overlapping or gapped byte intervals (`[0..1023]` + `[1025..2047]` missing byte `1024` — must refuse finalizing corrupt asset!).
4. RFC 8216 §4.3.2.5 violation: `#EXT-X-MAP` encrypted under `METHOD=AES-128` without an explicit `IV` attribute (must reject rather than silently using segment sequence `0`).
5. DRM circumvention attempt: HLS `#EXT-X-KEY:METHOD=SAMPLE-AES` / FairPlay `skd://` or DASH `cenc:pssh` Widevine UUID `edef8ba9-79d6-4ace-a3c8-27dcd51d21ed` (must hard-refuse with `DRM_PROTECTED_REFUSED`).
6. ISO 14496-12 §8.8.12.1 fMP4 box-order violation (`trun` emitted before `tfdt`, causing MSE `appendBuffer` error).
7. WebAudio Float32 clipping (`|sample| > 1.0` wrapping signed `int16` PCM instead of hard saturating `[-1.0, +1.0]` with TPDF dither).
8. Tier 4 native screenrecorder capturing odd pixel dimensions (`1079x607` crashing H.264 YUV420p `2x2` chroma subsampling) or violating `ALLOW_CAPTURE_BY_NONE` / `FLAG_SECURE`.
9. Tier 4 A/V desynchronization when browser frame drops occur during `30 min` capture (solved via PTS drift accumulator inserting repeat/drop cadence within `<= 20 ms`).
10. Full-width right-click context menu (`fillMaxWidth()` or unbound `DropdownMenu` `MaxWidth = Infinity` stretching across `1840px` desktop or `360dp` mobile).
11. Right-click near bottom-right viewport corner `(1790, 1340)` overflowing off-screen instead of flipping left/upward.
12. Borrowed/generic `List`/`Grid`/`Columns` icons indistinguishable at `16–24dp` or copied from Material/SF/Lucide icon sets.
13. Full-height or right-sidebar notification panel swallowing the right edge of the page during bulk file/Recall operations.
14. Opaque stacked Undo cards completely hiding canvas/table work underneath (`alpha = 1.0` instead of front-to-back translucent `0.88 / 0.68 / 0.48`).
15. Wasm `return_call` (`0x12`) implemented as naive recursive JVM call (`O(N)` stack frames overflowing on `100,000` tail calls — Modal Shortcut Test: REJECTED! Must reuse current frame in `O(1)` loop!).
16. `Charger-Surfing` localized PIN press ripple leaking digit coordinates over VBUS current.
17. `DolphinAttack` `25 kHz` ultrasonic energy slipping past unguarded speech pipeline.
18. Breaking `settings.gradle.kts` `50`-module invariant or stem uniqueness across `991` `.kt` files.

## E. MECHANISM PORTFOLIO (`>= 7` Distinct Engineered Subsystems)
1. **Recall 4-Tier Media Recording & Folder Delineation Pipeline** (`Tier 1` Direct/Range, `Tier 2` HLS/DASH/fMP4, `Tier 3` Canvas/WebAudio PCM/Opus, `Tier 4` Viewport `MediaProjection` + `AudioPlaybackCapture` with DRM/`FLAG_SECURE` refusal).
2. **252-Function Spreadsheet + 42-Op Dynamic Spill + Multi-Currency/XLSB/ODS Office Suite**.
3. **WebAssembly 3.0 `O(1)` Tail-Call (`0x12`/`0x13`) & Multi-Memory Sandbox**.
4. **25-Vector Tails Prop-327 Onion PoW + `Charger-Surfing` / `DolphinAttack` / `Thermanator` / IMU Keystroke Hardware Shield**.
5. **Incumbent-Benchmarked Right-Click Context Menu Solver (`RightClickContextMenuGeometry`)**.
6. **From-Scratch Parametric Vector Logo Engine (`FusionViewModeVectorIcons` for `List`, `Grid`, `Columns`)**.
7. **Bottom-Right Front-to-Back Translucent Z-Stacked Undo Deck (`StackedUndoNotificationDeck`)**.

## F. ARTIFACT REQUIREMENT (`48` Binary & Structured Artifacts)
`run_r52_contracts.py` emits `48` real binary/structured artifacts into `testing/build/r52/artifacts/` (playable `.mp4` fMP4 from HLS/DASH, decrypted `AES-128` HLS segment, Tier 3 `RIFF/WAVE` `.wav` + `OggS` `.opus`, Recall URL/Title/Custom archive tree & `manifest.recall.json`, Wasm `0x12`/`0x13` tail-call `.wasm`, `252`-fn `.xlsx`, custom `List`/`Grid`/`Columns` `.svg` sheets, context-menu benchmark `.json`, stacked-undo layout `.json`, Prop-327 PoW `.bin`, vectors `#21–#25` `.json`, and `5` tracked Java2D PNGs).

## G–J. SECOND-ORDER ADVERSARY, VERIFICATION, REPAIR & STOPPING
- Every repaired defect resets trust to zero, adds a targeted adversarial regression test, and re-runs `run_r52_contracts.py`, `check_r52_reference.py`, `render_r52_visual_preview.py` (+ `read_file` visual inspection of all 5 PNGs), `fusion_workbench_cli.py serve-self-test` + live `:8765` `start_process` preview, `r52_census_deepen.py`, and `r52_lab.py host --jobs 2` (`56` Python `unittest` suites).
