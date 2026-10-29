# Round 47 (`R47`) Formal Research & Engineering Protocol (`A–J`)

Date: `2026-10-26` (America/New_York)  
Branch: `arena/64550eba-osb`  
Target Root: `/home/user/OSB/android` (`50` Gradle modules: `47` product + `:benchmark`, `:baselineprofile`, `:testing`)

---

## RUNTIME HONESTY

1. **Tools, Compilers, Libraries & Daemons Actually Available and Executed**:
   - **Kotlin Compiler**: `kotlinc-jvm 2.2.21` (`JRE 17.0.9+9`, `-Werror -jvm-target 17`), compiling `6` SHA-256-cached domain jars (`r47_{office,runtime,privacy_sec,product_a,product_b,product_c}.jar`) + `r47_checks.jar`.
   - **JVM & Graphics Runtime**: OpenJDK `17.0.9+9` (`-Djava.awt.headless=true` Java2D `BufferedImage` + `FontMetrics` rasterizer + live warm `R47` Kotlin JVM Workbench daemon bound to `0.0.0.0:8765`).
   - **Python & Differential Reference Libraries**: Python `3.11.2`, `openpyxl 3.1.5`, `XlsxWriter 3.2.5`, plus stdlib `zipfile`, `xml.etree.ElementTree`, `wave`, `struct`, `hashlib`, `hmac`, `math`, and `http.server.ThreadingHTTPServer`.
   - **OS Telemetry**: Unprivileged Linux container `/proc/swaps`, `/sys/kernel/iommu_groups`, `RLIMIT_CORE`, `/sys/bus/usb/devices` (`amnesic_posture.py`).
   - **Outbound Research Access**: Strictly `github.com`, `codeload.github.com`, `api.github.com`, `registry.npmjs.org`, `pypi.org`, `files.pythonhosted.org`, plus verified `2026-10-26` `web_search` (`docs/research/r47-primary.md`).
2. **Tools Explicitly NOT Available in This Sandbox (Zero Fabrication)**:
   - **NO Android SDK (`platforms/android-36`, `android-37`), `aapt2`, `d8`/`r8`, AGP merged-manifest runner, Android Studio Rabbit 1 IDE, Android Emulator, ADB physical device, TalkBack accessibility service, physical IMU/USB/BLE hardware, or Linux `CLONE_NEWNET` capability (`CAP_SYS_ADMIN`).**
   - **NO formal theorem prover** (`Lean 4`, `Coq`, `Isabelle`, `Z3`, `TLA+` `tlc`); algebraic properties (GF(256) Shamir/Reed-Solomon reconstruction, SMPTE 29.97 drop-frame bijection, RGA CRDT commutativity, BSP non-overlap) are verified via bounded exhaustive/property sweeps on OpenJDK 17 and cross-checked in Python, never claimed as unbounded machine-checked proofs.
3. **6th Consecutive Inter-Turn `.cache/` Snapshot Reset Disclosure**:
   - `/home/user/.cache` is excluded from Arena inter-turn snapshots. At the start of `R47`, `/home/user/.cache/fusion-host-r23` had reset for the **6th consecutive turn** and was deterministically re-bootstrapped via `python3 tools/bootstrap_host_tools.py install`.
4. **Strict Separation of Testable Subsets vs Full-Spec Class A Acceptance**:
   - **Substantial Host-Testable Subsets (`docs/module-testability.json`)**: `50 / 50 = 100.00%` (`48 / 50 = 96.00%` deepened in-round during `R47` — `47/47 = 100.00%` of product modules + `:benchmark`).
   - **Full-Spec Class A Module Completion (`docs/module-audit.json`)**: **`0 / 50 = 0.00%`** (`50 / 50 = 100.00%` unfinished pending real Android SDK 37 / `aapt2` / `d8` / device UI / TalkBack / 16 KB ELF APK / physical hardware gates).

---

## A. FORMAL MODEL

We model the R47 multi-subsystem upgrade as a typed deterministic transition system
$$\mathcal{M}_{47} = (S, A, T, I, \mathcal{A}_{47}, V)$$
where:
- **State $S$**: Product of:
  1. Rich Office state $(\mathcal{D}_{\text{DOCX}}, \mathcal{D}_{\text{ODT}}, \mathcal{B}_{\text{XLSX}}, \mathcal{P}_{\text{PDF1.7}}, \mathcal{G}_{\text{Wiki}})$ with 61-function DAG evaluation and zero-metadata archive streams;
  2. Browser & WASI Capability Sandbox state $(\text{DOM}_{\text{san}}, \mathcal{F}_{\text{EasyList}}, \text{FD}_{3..k} \mapsto (\text{mount}, \text{rights}))$;
  3. Tails-Light Anonymity & Multi-Layer Anti-Keylogger state $(\sigma_{\text{Prop351}}, \sigma_{\text{IMU}}, \pi_{\text{PIN}}, \sigma_{\text{Clip}}, \sigma_{\text{IME}}, \sigma_{\text{RAM}})$;
  4. Replicated & Erasure-Coded Substrate state $(\text{RGA}_{\text{seq}}, \text{GF}(2^8)_{\text{RS/Shamir}}, \text{BSP}_{\text{rect}}, \text{DAG}_{\text{tasks}})$.
- **Invariants $I$**:
  - $I_{\text{purity}}$: Zero `android.*`/`androidx.*` imports inside all `61` pure-JVM R47 kernels; zero `docProps/core.xml`, `meta.xml`, `/Producer`, or wall-clock `mtime` metadata leaks across `.docx`, `.xlsx`, `.odt`, and `.pdf`.
  - $I_{\text{capability}}$: `WasiPreview1Host.pathOpen(dirFd, relPath)` resolves strictly inside `preopen[dirFd]` after segment normalization; any `..` escape, absolute path, NUL byte, or missing `RIGHT_PATH_OPEN` bit deterministically returns `ERRNO_NOTCAPABLE (76)`.
  - $I_{\text{anonymity}}$: Every Tor SOCKS5 connection derives `username = "<torS0X>0"` (Tor Proposal 351) and `password = HMAC-SHA256(secret, appId || 0x00 || eTLD+1/origin || 0x00 || epoch)` so distinct apps or distinct origins never share a Tor circuit; any `<torS0X>` username with suffix $\neq \text{'0'}$ is rejected.
  - $I_{\text{sidechannel}}$: Whenever text or secret entry is focused, `ImuSideChannelGuard` enforces a `0 Hz` sensor blackout across accelerometer, gyroscope, and magnetometer (defeating `Glitch in Time` 200+200 Hz phase reconstruction and `AlphaLogger` 10 Hz fusion); untrusted network-capable IMEs force CSPRNG-scrambled `SecureKeypadScrambler` (`>= 48dp` keys, constant-scan touch resolution).
  - $I_{\text{honesty}}$: `docs/module-audit.json` retains `complete = 0` (`0/50 = 0.00%`); every SHA-256 pin in `docs/module-testability.json` and `docs/verification/r11-source-inventory.json` matches exact workspace bytes.

---

## B. OUTCOME SPACE (`H+` / `H-` / `H∂`) — DIRECT ANSWER ON CLASS-A WRITER, SPREADSHEET & CORE FEATURES

- **`H-` (Honest Negative Verdict on Full Class-A Promotion In-Sandbox)**:
  - **Can Writer, Spreadsheet, PDF, Browser, Wasm Sandbox, Tails-Light Anonymity, or Anti-Keylogger be marked Class A (`complete`) inside `docs/module-audit.json` in this turn? NO (`0 / 50 = 0.00%`).**
  - Promoting any module to Class A requires passing external verifiers absent from this Linux container:
    1. `:feature:editor` / `:format:ooxml` / `:format:lok` (**Writer**): Android SDK 37 + `aapt2`/`d8` APK compilation, Compose rich-text IME composition/cursor/selection tests on ART, TalkBack accessibility traversal, and round-trip interop against desktop Word/LibreOffice binaries.
    2. `:feature:calc` / `:format:ooxml` (**Spreadsheet**): Compose virtualized 2D grid scrolling (`>= 60 fps` frame timing via `:benchmark` Macrobenchmark on physical hardware), touch cell-handle drag gestures, and TalkBack grid row/column announcements.
    3. `:format:pdf` (**PDF**): Android `PdfRenderer` / bitmap raster diff on device + print-spooler integration.
    4. `:feature:browser` (**Browser**): Android `WebView` / GeckoView isolated-process sandboxing, ServiceWorker/CSP enforcement on device, and network-namespace leak verification.
    5. `:privacy` / `:core:security` / `:core:hw` (**Tails-Light & Anti-Keylogger**): Real `WindowManager.LayoutParams.FLAG_SECURE` screenshot refusal test on SurfaceFlinger, real `SensorManager` listener gating on hardware IMU, real `ClipDescription.EXTRA_IS_SENSITIVE` OS overlay masking, and kernel `CLONE_NEWNET` / `VpnService` packet-capture leak proof.
- **`H+` (Maximum Host-Executable Engine Completeness Achieved in R47)**:
  - Within pure-JVM + headless Java2D + live `:8765` Web Workbench + Android AST (`PARSE OK`), R47 pushes every core subsystem to its maximum clean-room host-verifiable ceiling across **`61` new kernels + `5` `TOUCHED` production files in `48/50` (`96.00%`) Gradle modules**:
    - **Writer**: `DocxRichDocument` (headings, styled runs, tables, headers/footers, footnotes, comments, tracked `w:ins`/`w:del`, TOC, CVE-2017-0199 external-template refusal) + `OdtRichWriter` (OASIS ODF 1.3 Part 2 §2.2.1 `STORED` `mimetype` at byte 38 + XXE-hardened DOM reader) + `LatexMathSvg` (pure-Kotlin TeX math AST -> SVG & MathML) + `DocumentDiffMerge` (Myers $O(ND)$ diff + 3-way merge).
    - **Spreadsheet**: `FormulaEval` expanded `35 -> 61` pure functions (`XLOOKUP` with `IronCalc` PR `#10` 1-cell ref vs `#VALUE!` length-mismatch parity, `XMATCH`, `HLOOKUP`, `CHOOSE`, `SUMIFS`, `COUNTIFS`, `AVERAGEIF`, `AVERAGEIFS`, `IFS`, `SWITCH`, `XOR`, `TEXTJOIN`, `SUBSTITUTE`, `FIND`, `SEARCH`, `PROPER`, `EXACT`, `REPT`, `VALUE`, `NPV`, `PMT`, `FV`, `PV`, `MOD`, `POWER`, `LOG10`, `LN`, `EXP`, `CEILING`, `FLOOR`, `SIGN`, `TRUNC`) + `XlsxStyledBook` (`xl/styles.xml` fonts/fills/borders/`numFmts`/`cellXfs`, frozen panes, merged cells, `autoFilter`, defined names) + `SvgChartPlot` (bar/line/pie vector SVG).
    - **PDF**: `PdfOutlineBookmarks` (hierarchical `/Outlines` bookmark tree with negative `/Count` collapsed nodes, `/XYZ` destinations, clickable `/Link` `/URI` annotations, and 100% verified `xref` byte offsets).
    - **Browser & WASI**: `HtmlSanitizer` (DOMPurify 3.4.17-class string tokenizer defeating `<template shadowrootmode="closed">` & `<foreignObject>` XSS) + `AdblockFilterEngine` (Brave `adblock-rust`-style EasyList network + cosmetic rules) + `WasiPreview1Host` (capability-based `path_open`/`fd_read`/`fd_write`/`args_get`/`clock_time_get` refusing `../` escapes with `ERRNO_NOTCAPABLE = 76`).
    - **Tails-Light & Anti-Keylogger**: `TorSocksIsolation` (Tor Proposal 351 `<torS0X>0` SOCKS5 stream isolation), `ImuSideChannelGuard` (0 Hz blackout during text/secret focus), `SecureKeypadScrambler` (CSPRNG Fisher-Yates PIN/hex pad, `>= 48dp`, constant-scan touch hit-testing), `ClipboardSensitiveGuard` (`EXTRA_IS_SENSITIVE` + 30s TTL auto-wipe), `ImeTrustGate`, `RamWipeSchedule`, `ScreenReaderRedactor`, and `SecureSurfacePolicy`.
- **`H∂` (Exact Boundary Characterization)**:
  - Host JVM + Python differential oracle (`40/40`) + Java2D `FontMetrics`/WCAG visual audit (`111` bounds checks, `>= 5.68:1` contrast) + Android syntax parser (`12/12 PARSE OK`) + GitHub Actions `r26Contracts..r47Contracts` (`22` contiguous tasks) are **green and machine-verified**; Android SDK/ART/device gates remain **explicitly unverified (`0/50`)** until executed on an Android SDK 37 + hardware runner.

---

## C. FRONTIER (NON-DOMINATED TRADEOFF SURFACE)

| Design Candidate | Permissive License Purity (`MIT`/`Apache-2.0`/`MPL-2.0`) | 16 KB ELF / Zero-JNI Safety | Offline / Zero-Metadata Privacy | Full Desktop Interop Fidelity | Verdict |
| --- | --- | --- | --- | --- | --- |
| **F1: Embed LibreOfficeKit JNI / GPL Ghostscript** | Fails (`MPL`+`LGPL`/copyleft native blobs) | High 4 KB page alignment risk (`>120 MB` `.so`) | Leaks generator/user metadata unless patched | High | Rejected (copyleft/JNI bloat) |
| **F2: Embed Chromium WebView for Office + Math + Sanitizer** | Mixed system blob | Depends on system WebView | Ambient network/DNS/telemetry risk; DOM shadowroot quirks | Medium | Rejected for pure core kernels |
| **F3 (Chosen R47 Frontier): Clean-Room Pure-Kotlin Kernels (`DocxRichDocument` + `OdtRichWriter` + `XlsxStyledBook` + `FormulaEval` 61-fn + `PdfOutlineBookmarks` + `HtmlSanitizer` + `WasiPreview1Host` + `TorSocksIsolation` + `ImuSideChannelGuard`)** | **100% Apache-2.0 from scratch** | **100% pure JVM bytecode (`0` `.so` files)** | **Zero `docProps`/`meta.xml`/`/Producer`, deterministic `1980` ZIP epoch, `0 Hz` IMU blackout** | Scoped to typed OOXML/ODF/PDF 1.7 subsets (explicit `SCOPE` constants) | **Selected Pareto-optimal host+Android core** |

---

## D. NEGATIVE SPACE (`16` FAILURE SHAPES + MODAL SHORTCUT TEST)

1. **Modal Shortcut Failure Test**: The modal shortcut to "make Writer & Spreadsheet Class A" would be (a) wrapping `Apache POI` / `docx4j` / `MathJax` binaries (violating offline sandbox build & from-scratch constraints) or (b) flipping `module-audit.json` `complete` counts > 0 based on JVM tests alone. Both fail: (a) introduces unverified transitive bloat and XML/ZIP attack surface, while (b) violates `RUNTIME HONESTY`. We reject both.
2. **16 Adversarial / Boundary Failure Shapes Explicitly Guarded in R47**:
   - `NS-01`: `XLOOKUP(val, A2, B1:B2)` 1-cell scalar ref vs multi-cell return range (`IronCalc` PR `#10`) -> must return `#VALUE!`, whereas `XLOOKUP(val, A2, B2)` treats both 1-cell refs as length-1 vectors.
   - `NS-02`: CVE-2017-0199 / Follina `TargetMode="External"` `attachedTemplate` relationship in `word/_rels/document.xml.rels` -> refused on read in `DocxRichDocument`.
   - `NS-03`: Macro-enabled `vbaProject.bin` inside `.docx`/`.xlsx` or `Basic/` inside `.odt` -> refused fail-closed.
   - `NS-04`: Nested `<text:p>` inside `<text:note-body>` in ODF 1.3 `content.xml` + `<!DOCTYPE ... ENTITY xxe>` -> solved via `disallow-doctype-decl` namespace-aware DOM walker (`OdtRichWriter`).
   - `NS-05`: TeX `\write18{sh}` / `\input{/etc/passwd}` / brace bomb `{^30` in `LatexMathSvg` -> refused before AST construction.
   - `NS-06`: Declarative closed Shadow DOM `<template shadowrootmode="closed"><script>...` & SVG `<foreignObject>` & entity-obfuscated `j&#x09;avascript:` in `HtmlSanitizer` -> stripped at string-tokenizer layer.
   - `NS-07`: Domain-suffix spoof (`notdoubleclick.example.net` matching `||doubleclick.example.net^`) in `AdblockFilterEngine` -> prevented by label-boundary check.
   - `NS-08`: WASI `path_open(fd=3, "../etc/passwd")` or NUL byte in guest memory -> returns `ERRNO_NOTCAPABLE (76)` in `WasiPreview1Host`.
   - `NS-09`: Zip-Slip (`../../`), absolute `/etc/shadow`, symlink Unix mode `0120000`, and `>100:1` compression bomb in `ZipSafeExtract` -> refused pre-extraction.
   - `NS-10`: Malformed Tor Proposal 351 username `<torS0X>1` (unsupported version byte) or cross-app circuit reuse -> rejected by `TorSocksIsolation`.
   - `NS-11`: `Glitch in Time` `200 Hz` accel + `200 Hz` gyro `2.5 ms` phase-offset `400 Hz` keystroke reconstruction & `AlphaLogger` `10 Hz` sensor fusion -> defeated by `0 Hz` total IMU blackout during text/secret focus (`ImuSideChannelGuard`).
   - `NS-12`: Fixed-coordinate screen-tap inference or timing side-channel on PIN entry -> defeated by CSPRNG Fisher-Yates keypad scrambling + constant-scan all-12-cell hit testing (`SecureKeypadScrambler`).
   - `NS-13`: Third-party network-capable IME active on secret field -> downgraded to `FORCE_BUILTIN_SCRAMBLED_PAD` (`ImeTrustGate`).
   - `NS-14`: TalkBack screen-reader vocalizing or exporting OTP/password text via `AccessibilityEvent` -> masked to `"Protected credential field, 12 characters"` (`ScreenReaderRedactor`).
   - `NS-15`: Tapjacking overlay (`MOTION_FLAG_WINDOW_IS_OBSCURED`) on Vault/PIN surface -> touch event rejected (`SecureSurfacePolicy`).
   - `NS-16`: Illegal skipped NTSC 29.97 drop-frame label `00:01:00;00` or `00:01:00;01` -> rejected on parse while `00:10:00;00` (10th minute) is preserved (`SmpteTimecode`).

---

## E. MECHANISM PORTFOLIO (`7` ORTHOGONAL MECHANISMS)

1. **Standards-Clean Zero-Metadata Binary/ZIP emitters** (`DocxRichDocument`, `XlsxStyledBook`, `OdtRichWriter`, `PdfOutlineBookmarks`, `WavPcmHeader`, `ArArchive`, `IccProfileHeader`, `PackIndexCompact`).
2. **Typed DAG & Vector Layout Engines** (`FormulaEval` 61-fn topological evaluator, `LatexMathSvg` box layout, `SvgChartPlot`, `FluidTypeClamp`, `TilingBinaryTree` BSP window manager, `DagTaskRunner` critical-path planner).
3. **Capability & Grammar Isolators** (`WasiPreview1Host` preopen FD rights table, `HtmlSanitizer` shadow-root-safe tokenizer, `SvgSafeSubset`, `SoapActionEnvelope` XXE-blocking parser, `ZipSafeExtract`).
4. **Cryptographic & Finite-Field Constructions** (`TorSocksIsolation` HMAC-SHA256 Proposal 351 isolator, `ShamirSecretGf256` Lagrange secret sharing over $\text{GF}(2^8)$, `ReedSolomonGf256` Vandermonde erasure coding, `NoiseFraming` AEAD nonce state machine, `HandoffSessionTicket` replay-window HMAC).
5. **Hardware & Sensor Side-Channel Guards** (`ImuSideChannelGuard` 0 Hz focus gate + quantization, `SecureKeypadScrambler`, `ClipboardSensitiveGuard`, `ImeTrustGate`, `ScreenReaderRedactor`, `SecureSurfacePolicy`, `RamWipeSchedule`).
6. **Convergent & Sublinear Information Retrieval Kernels** (`RgaTextSequence` causal CRDT, `DocumentDiffMerge` Myers 3-way merge, `Bm25Scorer` Okapi BM25, `BoyerMooreHorspool` bad-character search, `BTreePageIndex` split-page index, `WikiLinkGraph` damped PageRank).
7. **Statistical & Protocol Framing Verifiers** (`HdrLatencyHist` logarithmic tail latency, `MicrobenchStats` Tukey IQR + Welch $t$-test, `EwmaAnomalyDetector`, `CastChannelFrame` protobuf wire codec, `AirPlayTxtRecord` RFC 6763, `ContentRangeHttp` RFC 9110, `SmpteTimecode`).

---

## F. ARTIFACT REQUIREMENT

All deliverables exist as concrete, SHA-pinned repository files executed in this turn:
- `61` new pure-JVM production kernels + `5` `TOUCHED` Android/OOXML files (`595` `src/main/**/*.kt` files across `48/50` Gradle modules).
- `testing/src/r47/kotlin/dev/fusion/testing/R47Checks.kt` (`2,393` assertions) + `tools/run_r47_contracts.py` + `testing/build/r47-host/artifacts/*` (`10` binary/SVG outputs).
- `tools/check_r47_reference.py` (`40/40` independent Python differential oracle checks).
- `tools/render_r47_visual_preview.py` -> `docs/verification/r47-host-workbench/r47-workbench-preview.png` (`1560x1020`, `111` `FontMetrics` non-overlap checks incl. `360px` compact phone viewport, `5.68:1` min WCAG contrast).
- `tools/fusion_workbench_server.py` (live on `0.0.0.0:8765`) + `tools/fusion_workbench_cli.py`.
- `testing/build.gradle.kts` + `.github/workflows/android.yml` (`22` contiguous `r26Contracts..r47Contracts` CI tasks).

---

## G. SECOND-ORDER ADVERSARY

If an adaptive local adversary knows our R46 defenses (`200 Hz` Android cap, `KeystrokeJitter` USB cadence check, `OnionGraterFilter` ControlPort filter), how do they attack next?
1. **Cross-Sensor Phase Reconstruction (`Glitch in Time`, 2024) & Low-Rate Fusion (`AlphaLogger`, 10 Hz)**: An unprivileged app without `HIGH_SAMPLING_RATE_SENSORS` fuses `200 Hz` accel + `200 Hz` gyro (`2.5 ms` phase offset -> `400 Hz` effective) or `10 Hz` accel+gyro+mag (`90.2%` keystroke inference). **R47 Countermeasure**: `ImuSideChannelGuard` drops ALL motion sensor delivery to **`0 Hz`** whenever `textEntryFocused || secretFieldFocused` is active.
2. **Cross-App Tor Circuit Correlation**: Even with `OnionGraterFilter` blocking `GETINFO`, two apps sharing a default SOCKS5 listener share a Tor exit circuit, linking pseudonymous browsing with background package/feed fetches. **R47 Countermeasure**: `TorSocksIsolation` enforces Tor Proposal 351 (`<torS0X>0`) per-`(appId, origin, epoch)` HMAC stream credentials.
3. **Closed Declarative Shadow DOM XSS & Untrusted Cloud IME Capture**: Malicious HTML uses `<template shadowrootmode="closed">` to hide scripts from naive regex sanitizers, while a sideloaded cloud keyboard exfiltrates Vault passphrases before encryption. **R47 Countermeasure**: `HtmlSanitizer` strips `<template>`, `<foreignObject>`, and entity-obfuscated URIs at token level; `ImeTrustGate` forces `SecureKeypadScrambler` when an IME holds `INTERNET` permission on secret fields.

---

## H. VERIFICATION

1. **Kotlin `-Werror` Contract & Property Suite (`tools/run_r47_contracts.py`)**: `2,393` assertions PASS across all `61` new kernels + `FormulaEval` 61-fn.
2. **Independent Cross-Language Differential Oracle (`tools/check_r47_reference.py`)**: `40/40` checks PASS (`zipfile`, `xml.etree`, exact PDF `xref` byte offsets, ODF 1.3 byte-38 `STORED` magic, Python `wave` 1-LSB 440 Hz sine check, Git `.idx` SHA-1 trailer, BSD `#1/36` `.ar`, ICC v4 `acsp` UTF-16BE, closed-form `PMT`/`PV`/`FV`/`NPV`, GF(256) Lagrange, SMPTE 29.97 DF, ICAO 9303 MRZ).
3. **Android/Compose AST Gate (`tools/check_r26_android_syntax.py`)**: `12/12 PARSE OK` including `MainActivity.kt`, `AdaptiveDock.kt`, `UsbWatchReceiver.kt`, and `MapScreen.kt`.
4. **Java2D Visual & 360dp Anti-Clipping Audit (`tools/render_r47_visual_preview.py` + `read_file` visual inspection)**: `111` `FontMetrics` non-overlap checks PASS, min WCAG contrast `5.68:1` (`>= 4.5:1`), visually verified.
5. **Prior Round Regression Gates**: `r26..r29` (`157 + 106 + 112 + 128`), `r45` (`508`), `r46` (`885`), live `:8765` HTTP + `*.e2b.app` proxy check, and full `unittest` suite (`r47_lab.py host`).

---

## I. REPAIR (ZERO-TRUST PATCH RECORD)

Two genuine implementation defects were caught by our own verification gates during R47 and repaired from zero trust with dedicated adversarial regression tests:
1. **`OdtRichWriter.kt` Nested `<text:p>` Footnote Truncation (`Chomsky Type-3 vs Type-2`)**:
   - *Symptom*: `R47Checks.testOdtRichWriterMagicAndRoundTrip` threw `NullPointerException` on `p.footnote!!` because ODF 1.3 `<text:note><text:note-body><text:p>...</text:p></text:note-body></text:note>` embeds an inner `</text:p>` inside the outer `<text:p>`, causing non-greedy regex `<text:p>(.*?)</text:p>` to terminate prematurely.
   - *Repair*: Replaced regex extraction with a hardened `DocumentBuilderFactory` (`disallow-doctype-decl = true`, `FEATURE_SECURE_PROCESSING = true`, zero entity expansion) namespace-aware DOM walker.
   - *Adversarial Regression Added*: Verified both nested footnote round-trip AND explicit refusal of a poisoned `.odt` containing `<!DOCTYPE lol [<!ENTITY xxe SYSTEM "file:///etc/passwd">]>` inside `content.xml`.
2. **`WikiLinkGraph.kt` Mid-Line Multi-Backtick Code Span Leak**:
   - *Symptom*: `R47Checks.testWikiLinkGraphAndPageRank` failed on `idx.brokenLinks.single()` because `stripCodeRegions` only toggled fences when ```` ``` ```` appeared at column 0 of a line, leaking `[[Ignored Code Link]]` inside a mid-line CommonMark ```` ```\n...\n``` ```` span.
   - *Repair*: Updated `stripCodeRegions` to strip multi-line ```` ```...``` ```` regions first, then `~~~` line fences, then inline `` `...` `` spans.
   - *Adversarial Regression Added*: Extended `tor-design` fixture with simultaneous mid-line ```` ```...``` ````, inline `` `[[InlineIgnored]]` ``, and `~~~` tilde-fenced `[[TildeFenceIgnored]]` fake links.

---

## J. STOPPING

R47 stops only when all `61` new kernels + `5` `TOUCHED` files compile under `-Werror`, `2,393` R47 contract assertions + `40` differential oracle checks + `111` visual bounds checks + `12` Android syntax parses + live `:8765` `*.e2b.app` workbench checks + `>= 80` `test_r47_tools.py` unit tests + `r47_lab.py host` + `native_source_audit.py --check` pass cleanly, while preserving `0/50 = 0.00%` honest Class-A status in `docs/module-audit.json`.
