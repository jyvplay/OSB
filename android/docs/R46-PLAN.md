# Round 46 (`R46`) Formal Research & Engineering Protocol (`A–J`)

Date: `2026-10-26` (America/New_York)  
Branch: `arena/64550eba-osb`  
Target Root: `/home/user/OSB/android` (`50` Gradle modules: `47` product + `:benchmark`, `:baselineprofile`, `:testing`)

---

## RUNTIME HONESTY

1. **Tools, Compilers, Libraries & Daemons Actually Available and Executed**:
   - **Kotlin Compiler**: `kotlinc-jvm 2.2.21` (`JRE 17.0.9+9`, `-Werror -jvm-target 17`).
   - **JVM & Graphics Runtime**: OpenJDK `17.0.9+9` (`-Djava.awt.headless=true` Java2D `BufferedImage` + `FontMetrics` rasterizer + live warm `R46WorkbenchDaemon` over pipes).
   - **Python & Differential Reference Libraries**: Python `3.11.2`, `openpyxl 3.1.5`, `XlsxWriter 3.2.5`, plus stdlib `tarfile` (POSIX.1-2001 PAX), `quopri`/`email.header`, `zlib.crc32`, `hmac`/`hashlib`, `zipfile`, `xml.etree.ElementTree`, and `http.server.ThreadingHTTPServer`.
   - **OS Telemetry**: Unprivileged Linux container `/proc/swaps`, `/sys/kernel/iommu_groups`, `RLIMIT_CORE`, `/sys/bus/usb/devices` (`amnesic_posture.py`).
   - **Outbound Research Access**: Strictly `github.com`, `codeload.github.com`, `api.github.com`, `registry.npmjs.org`, `pypi.org`, `files.pythonhosted.org`, plus verified `web_search` (`docs/research/r46-primary.md`).
2. **Tools Explicitly NOT Available in This Sandbox (Zero Fabrication)**:
   - **NO Android SDK (`platforms/android-36`, `android-37`), `aapt2`, `d8`/`r8`, AGP merged-manifest runner, Android Studio Rabbit 1 IDE, Android Emulator, ADB physical device, TalkBack accessibility service, or physical USB/BLE radio interfaces.**
   - **NO formal theorem prover** (`Lean 4`, `Coq`, `Isabelle`, `Z3`, `TLA+` `tlc`); algebraic properties (CRDT semilattice laws, WAL crash-truncation safety, GS1 check-digit detection, Wasm Fibonacci equivalence) are verified via bounded exhaustive/deterministic property sweeps on OpenJDK 17, never claimed as unbounded machine-checked proofs.
3. **5th Consecutive Inter-Turn `.cache/` Snapshot Reset Disclosure**:
   - `/home/user/.cache` is excluded from Arena inter-turn snapshots. At the start of `R46`, `/home/user/.cache/fusion-host-r23` had reset for the **5th consecutive turn** and was deterministically re-bootstrapped via `python3 tools/bootstrap_host_tools.py install`.
4. **Strict Separation of Testable Subsets vs Full-Spec Class A Acceptance**:
   - **Substantial Host-Testable Subsets (`docs/module-testability.json`)**: `50 / 50 = 100.00%` (`34 / 50 = 68.00%` deepened in `R46`, `47 / 47 = 100.00%` product modules covered across `R45 + R46`).
   - **Full-Spec Class A Module Completion (`docs/module-audit.json`)**: **`0 / 50 = 0.00%`** (`50 / 50 = 100.00%` unfinished pending real Android SDK/AGP/device/TalkBack/16KB ELF/hardware gates).

---

## A. FORMAL MODEL

We model the R46 workspace upgrade as a typed deterministic transition system
$$\mathcal{M}_{46} = (S, A, T, I, \mathcal{A}_{46}, V)$$
where:
- **State $S$**: Product of (i) multi-worksheet cell graph $G_{\text{book}} = (V_{\text{sheet}\times\text{cell}}, E_{\text{ref}})$, (ii) structured document & revision sequence $(\Sigma_{\text{AST}}, \Delta_{\text{ins/del}}, B_{\text{bib}})$ mapped onto paginated ISO 32000-1 PDF coordinate frames, (iii) pure-JVM Wasm 1.0 machine state $(\text{stack}, \text{locals}, M_{64\text{KiB}}, f_{\text{fuel}} \in \mathbb{N})$, (iv) Tor ControlPort session filter state $\sigma_{\text{og}} \in \{\text{Unauth}, \text{Filtered}(\pi_{\text{app}})\}$, (v) wired/wireless HID authorization & inter-keystroke cadence state $(\mathcal{D}_{\text{USB}}, \mathcal{D}_{\text{BLE}}, \tau_{1..k})$, and (vi) crash-consistent storage/CRDT state $(W_{\text{WAL}}, M_{\text{HLC}})$.
- **Invariants $I$**:
  - $I_{\text{purity}}$: Zero `android.*`/`androidx.*` or `java.io.File` dependencies inside any pure-JVM kernel; deterministic zero-epoch (`1980-01-01T00:00:00Z` / `mtime=0`) archive output (`MultiSheetBook`, `OdsSheetExtract`, `TarPax`).
  - $I_{\text{termination}}$: Every untrusted computation is bounded by explicit input caps (`MAX_BYTES`, `MAX_COL_REPEAT = 256`, `MAX_EXPANDED_CELLS = 100_000`) or monotonic Wasm fuel $f_{t+1} = f_t - 1$ trapping at $f = 0$ (`TrapKind.OUT_OF_FUEL`).
  - $I_{\text{privacy}}$: Unlisted Tor ControlPort commands deterministically yield `510 Command filtered`; `MetadataScrub` and `PngMetadataStrip` strip author/timestamp/EXIF/XMP metadata without corrupting container CRCs or PDF `xref` byte offsets; unauthenticated `JustWorks` HID (`CVE-2023-45866`) and composite `08:* + 03:01:*` BadUSB devices are rejected pre-bind; superhuman (`mean < 22 ms`) or crystal-clocked (`stddev < 1.5 ms`) keystroke bursts set `shouldBlockStream = true`.
  - $I_{\text{honesty}}$: `module-audit.json` `complete == 0` (`0.00%`); every SHA-256 pin in `module-testability.json` matches workspace bytes.

---

## B. OUTCOME SPACE (`H+`, `H-`, `H∂`)

| Partition | Definition | R46 Concrete Elements |
| :--- | :--- | :--- |
| **$H^+$ (Verified on Host)** | Clean-room permissive Kotlin 2.2.21 bytecode compiled with `-Werror` and verified by `R46Checks` + Python differential oracles + live warm-JVM HTTP workbench. | `39` new kernels + `3` `TOUCHED` repairs across `34/50` (`68.00%`) modules; `885` `R46Checks` assertions; `25` cross-engine `check_r46_reference.py` checks (`openpyxl`, PDF `xref`, `tarfile` PAX, `quopri`, PNG CRC-32, HMAC, Wasm `fib(20)`); `17` contiguous Gradle CI tasks (`r30Contracts..r46Contracts`); live `0.0.0.0:8765` workbench. |
| **$H^-$ (Refused / Unsound)** | Copyleft contamination, JNI native `.so` 16KB traps, silent truncation, tautological assertions, or unverified completion claims. | `AGPL-3.0` MuPDF/PyMuPDF, `GPLv2+CE` GraalWasm, `LGPL` LibreOfficeKit/OpenHTMLtoPDF; JNI Wasm runtimes without 16 KB `p_align` verification; `localhost` browser fetches breaking `*.e2b.app` preview; claiming `complete` in `module-audit.json`. |
| **$H^\partial$ (Boundary / Deferred to Android Studio Rabbit 1 & Hardware)** | Android framework bindings, Compose UI pixel rendering on ART, Binder/USB/Bluetooth OS enforcement, TalkBack AX, and APK packaging. | Full `:app:assembleDebug` under Android SDK 36/37, Compose `DesktopTopBar`/`TabletopDesktop` on-device 360dp screenshot capture, Linux `usbguard-daemon`/Android `UsbManager` & `BluetoothHidHost` enforcement, and 16 KB `zipalign`/`readelf` APK gate. |

---

## C. PARETO FRONTIER

Candidates were evaluated across 6 non-dominated axes: **(1) License Permissiveness**, **(2) Zero-JNI / 16KB Page Safety**, **(3) Host-JVM Standalone Executability**, **(4) Memory/APK Footprint**, **(5) Spec Interoperability**, and **(6) Privacy/Anti-Forensic Posture**:
- **Sandbox Frontier**: Pure-JVM Wasm (`Chicory 1.7.5` / `Endive` architecture, `Apache-2.0`, 0 JNI `.so` bytes) strictly dominates JNI Wasmtime/Wasmer (native `.so` 16KB ELF risk) and GraalWasm (`GPLv2+CE` license conflict).
- **Document/PDF Frontier**: Clean-room `DocOutlineRender` + `TrackRevisions` + `CitationBib` + `PdfPageWriter` (`Apache-2.0`/`MIT`/`BSD-3` design lineage: `PDFBox 3.0.8`, `rdocx`, `office2pdf`) strictly dominates Chromium headless printing (~150 MB binary) and MuPDF (`AGPL-3.0`).
- **Spreadsheet Frontier**: Clean-room `MultiSheetBook` + 35-function `FormulaEval` + `SheetPivot` + `CondFormat` + `OdsSheetExtract` (`IronCalc`/`Formualizer` `MIT`/`Apache-2.0` model, verified against `openpyxl 3.1.5`) strictly dominates bundling LibreOffice Calc (`MPL/LGPL`, >100 MB JNI).

---

## D. NEGATIVE SPACE (`18` Failure Shapes + Modal Shortcut Failure Test)

### Enumerated Failure Shapes (`NS-01` .. `NS-18`)
1. **`NS-01` Cross-Sheet Cycle Smuggling**: `Sheet1!A1 = Sheet2!B1` and `Sheet2!B1 = Sheet1!A1` bypassing single-sheet cycle checks -> caught by global `(sheet, cell)` topological sort in `MultiSheetBook`.
2. **`NS-02` Quoted Sheet Name Apostrophe Escape (`'Q1 ''26'!A1`)**: Naive `split('!')` corrupting sheet names containing spaces, `!`, or escaped `''` -> parsed by quote-aware state machine in `MultiSheetBook`.
3. **`NS-03` PDF `xref` Byte-Offset Drift**: Multi-byte characters or CRLF normalization shifting object offsets so PDF readers enter repair mode -> `PdfPageWriter` records exact UTF-8/ASCII byte offsets in `ByteArrayOutputStream` and `check_r46_reference.py` independently verifies every `0000000000 00000 n` offset.
4. **`NS-04` PDF Metadata Leakage**: Emitting `/Producer`, `/Author`, or `CreationDate` in generated or shared PDFs -> `PdfPageWriter` omits `/Info` timestamps by default and `MetadataScrub.scrubPdfBytes` overwrites `/Author`/`/Creator`/`/Producer`/`/CreationDate` values in-place with spaces so `xref` offsets remain 100% valid!
5. **`NS-05` ODF `.ods` `number-columns-repeated="1048576"` Billion-Cell Bomb**: `OdsSheetExtract` caps column repeats at `256` and row repeats at `64`.
6. **`NS-06` Wasm Infinite Loop (`loop br 0 end`)**: Halting host UI thread -> `WasmSandbox` decrements monotonic instruction fuel on every opcode and traps deterministically with `TrapKind.OUT_OF_FUEL`.
7. **`NS-07` Wasm Linear Memory Out-of-Bounds / Negative Offset**: `i32.load` / `i32.store` crossing 64 KiB page limit -> `WasmSandbox` traps with `TrapKind.OUT_OF_BOUNDS_MEMORY`.
8. **`NS-08` WAL Torn Tail Frame on Power Loss**: Crash mid-frame or uncommitted transaction frames -> `WalFrame.recover` verifies salt + rolling CRC-32 chain and discards uncommitted/torn trailing bytes across 40 tested truncation offsets.
9. **`NS-09` Tar PAX Path Traversal (`../../etc/shadow`) & Symlink Escape**: `TarPax` rejects `..` segments, leading `/`, NUL bytes, and non-regular link types (`'1'`, `'2'`).
10. **`NS-10` Tor ControlPort Deanonymization (`GETINFO address`, `ADD_ONION`, `GETCONF`)**: `OnionGraterFilter` denies unlisted commands with `510 Command filtered`, rewrites `GETINFO net/listeners/socks` to the isolated SOCKS port, and suppresses foreign-UID `650 STREAM` events.
11. **`NS-11` Composite USB Flash Drive + Hidden Boot Keyboard (`08:* + 03:01:01`)**: `UsbGuardPolicy` rejects composite Mass Storage + HID devices and lockscreen hotplug keyboards pre-authorization.
12. **`NS-12` Inline Hardware USB Keylogger Implant (`09:* Hub + 03:01:01 HID` within `<2500ms`)**: `UsbGuardPolicy` blocks composite Hub+HID interposer topologies.
13. **`NS-13` Authorized-Keyboard Post-Bind DuckyScript Injection**: Even if a USB keyboard is authorized (where `USBGuard` is blind), `KeystrokeJitter.classifyCadence` detects `<22 ms` superhuman speed and `<1.5 ms` crystal-oscillator regularity (`shouldBlockStream = true`).
14. **`NS-14` `BlueDucky` `CVE-2023-45866` Unauthenticated Bluetooth/BLE HID Injection**: `BleHidPairingGate` denies `JustWorks` / `NoInputNoOutput` L2CAP `0x0011`/`0x0013` and BLE HOGP `0x1812` connections, requiring MITM-authenticated numeric comparison/passkey + 16-byte link encryption.
15. **`NS-15` Terminal `OSC 52` Clipboard Exfiltration (`\u001b]52;c;...`)**: `VtScreenBuf` consumes and drops all OSC sequences without mutating screen cells or host clipboards.
16. **`NS-16` Desktop `.desktop` `Exec=` Command Injection (`Exec=app; curl evil`)**: `DesktopEntryIni` tokenizes field codes (`%f`, `%u`, `%F`, `%U`) into discrete `argv` arrays and throws on unquoted shell metacharacters (`; | & $ > < \``).
17. **`NS-17` Compact 360dp Top-Bar Text Clipping (`"Pilo"` & `"Work Control panel"`)**: Discovered via direct visual inspection of `docs/verification/r24-native-previews/*.png`; fixed in `DesktopChrome.kt` (`BoxWithConstraints` compact title width cap + `"WS N"` badge + `1.dp` divider) and `TabletopDesktop.kt` (`TileLabelFit.fitLabel` 2-line pin labels).
18. **`NS-18` Silent Census Drift on `TOUCHED` Production Files**: Editing `DesktopChrome.kt`, `TabletopDesktop.kt`, and `FormulaEval.kt` without re-pinning SHA-256 -> `r46_census_deepen.py` explicitly tracks all 3 in `TOUCHED` and refuses any unlisted drift.

### Modal Shortcut Failure Test
- *Shortcut 1*: "Mock Spreadsheet/PDF/Wasm outputs in Python for the browser workbench." -> **FAILS** user's zero-mock rule; instead `fusion_workbench_server.py` spawns `R46WorkbenchDaemon` on OpenJDK 17 over compiled R46 `.jar`s.
- *Shortcut 2*: "Hardcode `http://localhost:8765/api/...` in workbench JS." -> **FAILS** in Arena's `https://8765-{sandboxId}.e2b.app` browser preview because the user's browser is not the sandbox; `fusion_workbench_server.py` binds `0.0.0.0:8765` and uses strictly relative `fetch("api/...")` URLs.
- *Shortcut 3*: "Only add `r46Contracts` to Gradle and leave `r30..r33` unbridged." -> **FAILS** historical continuity; we bridged `r30Contracts..r33Contracts` + `r46Contracts` (`17` contiguous tasks `r30Contracts..r46Contracts`).

---

## E. MECHANISM PORTFOLIO (`8` Orthogonal Mechanisms)

1. **`M1` — Multi-Sheet Spreadsheet, Pivot & Conditional Formatting Engine** (`MultiSheetBook`, 35-func `FormulaEval`, `SheetPivot`, `CondFormat`, `OdsSheetExtract`).
2. **`M2` — Clean-Room Writer, Tracked Revisions, BibTeX & Paginated ISO 32000-1 PDF 1.7 Pipeline** (`DocOutlineRender`, `TrackRevisions`, `CitationBib`, `PdfPageWriter`).
3. **`M3` — Pure-JVM Fuel-Metered WebAssembly 1.0 Sandbox & Resource Quotas** (`WasmSandbox`, `QuotaBudget`, `PipeChain`).
4. **`M4` — Tails-Mod Tor ControlPort `onion-grater` Filter, `mat2` Metadata Scrubber & Per-SSID MAC Randomizer** (`OnionGraterFilter`, `MetadataScrub`, `PngMetadataStrip`, `MacRandomizer`).
5. **`M5` — Multi-Layer Wired & Wireless Anti-Keylogger Hardware Shield** (`UsbGuardPolicy`, `BleHidPairingGate`, `KeystrokeJitter`).
6. **`M6` — Crash-Consistent Storage, Columnar Analytics, POSIX PAX Archive & HLC-CRDT State Kernels** (`WalFrame`, `ColumnarPage`, `TarPax`, `CrdtLwwMap`, `RabinChunker`, `ChunkedTransfer`).
7. **`M7` — Visual Screenshot Audit & Compose Shell Anti-Clipping Repairs** (`DesktopChrome.kt`, `TabletopDesktop.kt`, `render_r46_visual_preview.py`).
8. **`M8` — 17-Round Contiguous Gradle CI Contract Lane & Live Warm-JVM Browser Workbench** (`testing/build.gradle.kts`, `.github/workflows/android.yml`, `fusion_workbench_server.py`, `fusion_workbench_cli.py`).

---

## F. ARTIFACT REQUIREMENT

All deliverables exist on disk and are deterministic, reproducible, and tracked:
- `39` new pure-JVM Kotlin kernels + `3` `TOUCHED` production files across `34` modules (`534` total `src/main/**/*.kt` files).
- `testing/src/r46/kotlin/dev/fusion/testing/R46Checks.kt` + `tools/fixtures/r46/adversarial-corpus.json` + `tools/run_r46_contracts.py` (`885` assertions).
- `tools/check_r46_reference.py` (`25` cross-engine differential checks).
- `tools/render_r46_visual_preview.py` + `docs/verification/r46-host-workbench/{r46-workbench-preview.png,r46-visual-audit.json}` (`1520x980`, `77` `FontMetrics` non-overlap checks, `5.68:1` min WCAG contrast).
- `tools/fusion_workbench_server.py` (running live on `0.0.0.0:8765`) + `tools/fusion_workbench_cli.py`.
- `tools/r46_census_deepen.py` + `tools/test_r46_tools.py` (`66` checks) + `tools/r46_lab.py`.

---

## G. SECOND-ORDER ADVERSARY

A second-order adversary adapts to first-order defenses:
- **Against `USBGuard`**: Clones the `VID:PID` of an already-authorized Dell keyboard or inserts a composite hub+keyboard interposer. *Countermeasure*: `UsbGuardPolicy` rejects composite hub+HID (`09:* + 03:01:*`) and hotplug secondary boot keyboards, while `KeystrokeJitter` catches synthetic DuckyScript timing post-authorization.
- **Against PDF Metadata Stripping**: Relies on naive regex deletion shifting PDF byte lengths and breaking `xref` tables so recipients reject sanitized files. *Countermeasure*: `MetadataScrub.scrubPdfBytes` overwrites `/Author (...)` payload bytes in-place with `0x20` spaces, preserving exact `xref` byte offsets (`verified` in `R46Checks`).
- **Against Tor ControlPort Filtering**: Sends `GETINFO version address` multi-key batching on a single line to smuggle forbidden `address` alongside allowed `version`. *Countermeasure*: `OnionGraterFilter` validates *every* whitespace-separated `GETINFO` key against `allowedGetInfoKeys` and denies the entire line (`510`) if any key is unauthorized.

---

## H. VERIFICATION

Independent external verifiers executed in this sandbox:
1. `kotlinc 2.2.21 -Werror -jvm-target 17` across all `34` module `.jar`s + `R46Checks.kt` (`885` assertions PASS).
2. `check_r46_reference.py --verify` (`25` independent Python `openpyxl` / PDF `xref` / `tarfile` PAX / `quopri` / `zlib.crc32` / `hmac` / Wasm oracle checks PASS).
3. Legacy regression gates: `run_subset_contracts.py` (`2,394`), `run_jvm_contracts.py` (`183,185`), `run_r30_contracts.py` (`98`), `run_r31_contracts.py` (`152`), `run_r32_contracts.py` (`157`), `run_r33_contracts.py` (`142`), `run_r45_contracts.py` (`508`) — all `0` regressions.
4. `check_r26_android_syntax.py` (`PARSE OK` across all `10` Compose files including patched `DesktopChrome.kt` and `TabletopDesktop.kt`).
5. Visual inspection via `read_file` of `art-studio-graphite.png`, `business-office-blue.png`, `r45-workbench-preview.png`, and `r46-workbench-preview.png` (`77` `FontMetrics` checks, `5.68:1` min contrast).
6. Live HTTP E2E probe against `0.0.0.0:8765` (`GET /`, `GET /api/status`, `POST /api/sheet`, `POST /api/writer`, `POST /api/wasm`, `POST /api/security`) + `r46_lab.py host`.

---

## I. REPAIR LOG (Zero-Trust Re-Verification)

Every defect caught during R46 inherited zero trust and triggered full gate re-execution plus adversarial regression tests:
1. **`R46Checks` `COUNTIF` Criteria Fixture**: `B1:B4 = [120, 80, 200, 80]` has `3` values `<150` (`120, 80, 80`); verified against `openpyxl` and added negative/decimal criteria assertions.
2. **`R46VisualRenderer` `drawChecked` `FontMetrics` Collision**: Card 1 title initially ended at `x=592 > maxRightX=558` and pin caption ended at `x=719 > maxRightX=718` (`1 px` overflow!). Caught automatically by `drawChecked`, repaired with dynamic badge-width bounding (`bx - 12`), re-rendered (`77` checks PASS), and visually verified via `read_file`.
3. **`fusion_workbench_server.py` `/api/status` Method Name**: Live HTTP E2E testing caught `amnesic_posture.inspect_host_posture` -> repaired to `amnesic_posture.collect_posture()`, restarted background process, and verified HTTP `200` across all 6 endpoints with `Host: 8765-sandbox.e2b.app`.

---

## J. STOPPING CRITERIA

Stop `R46` only when:
- All `39` new kernels + `3` `TOUCHED` repairs compile with `-Werror` and pass `885` assertions (`> 508` in R45);
- `34/50` (`68.00%` `> 52.00%` in R45) modules are idempotently deepened (`3x` `cmp -s` byte-identical);
- `test_r46_tools.py` passes `66` checks (`> 52` in R45);
- `r46_lab.py host` passes every host gate and all `50` Python `unittest` suites (`676/676` checks);
- Live `0.0.0.0:8765` workbench server is running cleanly with zero preview warnings; and
- Full-spec Class A completion remains honestly reported as `0/50` (`0.00%`) alongside `50/50` (`100.00%`) testable subsets.
