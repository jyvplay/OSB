# R45 Engineering Contract & A–J Protocol

**Date:** `2026-10-26` (UTC)  
**Branch:** `arena/64550eba-osb`  
**Baseline Commit:** `a8043fedd1ec0e4badaf45732b380c22f0112083` (`R44: 24 host-JVM kernels across 23 modules`)

---

## RUNTIME HONESTY

- **Executed in this sandbox (`/home/user/OSB`):**
  - Linux container (`2` vCPU cores, `3.8 GiB` RAM, `0 B` swap), Python `3.11`, `kotlinc-jvm 2.2.21`, OpenJDK `17.0.9+9` (`-Djava.awt.headless=true` Java2D `BufferedImage` + `FontMetrics` verified), `openpyxl 3.1.5`, `XlsxWriter 3.2.5`, `git`, `gh`, and outbound HTTPS to `github.com`/`pypi.org`/`npmjs.org` plus live `web_search`.
- **NOT available / NOT claimed in this sandbox:**
  - Android SDK `platforms;android-37`, Android NDK / CMake / Ninja, Android Emulator / `adb` / physical arm64 hardware (4K/16K page kernels), LibreOfficeKit `.so` native runtime, Chromium/GeckoView/Servo runtime engines, TalkBack/Switch Access on-device services, and formal theorem provers (Lean/Coq/Z3).
- **Downgrade rule enforced:**
  - Every R45 claim is scoped strictly to host-JVM bytecode execution, differential Python/`openpyxl`/`zipfile`/RFC verification, static XML/AST/symbol gates, and headless Java2D kernel-output visual layout/contrast verification. Full-spec Android module completion in `docs/module-audit.json` remains **`0/50` (`0.00%`)**.

---

## A. FORMAL MODEL

Let $M = \{m_1, \dots, m_{50}\}$ be the 50 Gradle modules in `settings.gradle.kts`, partitioned into $M_{\text{prod}}$ ($|M_{\text{prod}}| = 47$ product modules) and $M_{\text{harness}} = \{\text{:benchmark}, \text{:baselineprofile}, \text{:testing}\}$ ($|M_{\text{harness}}| = 3$).

1. **Two Orthogonal Ledgers (Never Conflated):**
   - **Full-Spec Acceptance** $A: M \to \{0, 1\}$ (`docs/module-audit.json`): $A(m) = 1$ iff all six mandatory gates pass (AGP/Lint both flavors, whole assigned v2/v2.1 scope, S1–S30 device spikes, security/privacy/AX review, API 37 + 16 KB page performance, and license/SBOM closure). Today $\sum_{m \in M} A(m) = 0/50 = 0.00\%$.
   - **Substantial Runnable Testable Subset** $T: M \to \{0, 1\}$ (`docs/module-testability.json`): $T(m) = 1$ iff module $m$ owns SHA-256-pinned production files compiled and executed by a deterministic command with zero mocks. Today $\sum_{m \in M} T(m) = 50/50 = 100.00\%$.
2. **State & Invariant Domains:**
   - **Spreadsheet (`FormulaEval`, `FormulaXlsx`):** Finite cell map $S: \text{CellRef} \to \text{CellInput}$ over A1 coordinates ($1 \le \text{col} \le 16384$, $1 \le \text{row} \le 1048576$, $|S| \le 16384$). Dependency graph $G = (V, E)$ evaluated in topological order; any strongly connected cycle yields deterministic `CellErr.Kind.CYCLE` (`#CYCLE!`) on every participating/downstream cell without recursion overflow.
   - **Writer (`PieceTable`, `ProseEducate`, `DocxNumbering`, `DocxPackage`, `OdtExtract`):** Immutable piece sequence $P = \langle (src_i, off_i, len_i) \rangle$ over `(original, added)` buffers with bounded history $|H| \le 256$; protected span set $\mathcal{I} = \{[a_k, b_k)\}$ (fenced code, inline code, URLs) satisfies $\forall [a,b) \in \mathcal{I},\; \text{educate}(x)[a..b) = x[a..b)$.
   - **Browser & Network (`CspHeader`, `PslGate`, `CtSctPolicy`, `SsdpHeader`):** Fail-closed policy lattices where unlisted TLDs (`UNLISTED_TLD`), distrusted CAs (`REJECT_CA_DISTRUSTED`), single-operator SCTs, and non-RFC1918 SSDP `LOCATION` URLs reject deterministically.
   - **Tails-Mod Amnesia & Anti-Keylogger (`TailsCadence`, `HidReportAudit`, `DmaGuardAudit`, `OverlayTouchGuard`):** Product lattice over release freshness ($\Delta t \le 14\text{d}$ standard, immediate block on emergency advisories), bounded shutdown ($T_{\text{wipe}} \le 60\,000\text{ ms}$), USB HID descriptor capability vs claimed device role, DMA IOMMU/DMAR posture, and obscured-touch / cross-UID overlay flags.

---

## B. OUTCOME SPACE

| Outcome Tier | Definition | R45 Target | Achieved |
|---|---|---|---|
| **O0 (Regressive)** | Breaks any prior R13–R44 contract, inflates `0/50` audit, or introduces Android/file IO into pure kernels | Forbidden | Avoided |
| **O1 (Incremental Parity)** | Matches R44 (~24 kernels, 23 modules, 378 assertions, 35 tool tests) without closing R44 §J follow-ups | Insufficient | Exceeded |
| **O2 (R45 Target)** | Closes all R44 §J follow-ups (`TOUCHED` `DashEducate`, `r34..r45` contiguous CI bridge, Writer/Sheet/PSL/Amnesia), `>=30` kernels, `>=25` modules, `>=500` assertions, `>=50` tool tests, bidirectional `openpyxl` oracle, honest visual preview | Required | **Achieved (`33+1` kernels, `26` modules, `508` assertions, `52` R45 tool tests, `610` total Python tests)** |
| **O3 (Class A Device Closure)** | Full SDK 37 APK + arm64 16K device + LOK/Gecko native spikes | Blocked by sandbox toolchain | Documented honestly (`0/50`) |

---

## C. FRONTIER

1. **Closing R44 §J Open Follow-Ups First:**
   - Exercised `TOUCHED` census flow (`DashEducate.educateOutside`) + `ProseEducate` so writer typography no longer corrupts URLs or code spans.
   - Backported `r34Contracts`, `r35Contracts`, `r36Contracts` and added `r45Contracts` in `testing/build.gradle.kts` and `.github/workflows/android.yml` (`12` contiguous rounds `r34..r45`).
   - Built real `.docx` (`DocxPackage` + `DocxNumbering`), formula `.xlsx` (`FormulaEval` + `FormulaXlsx`), ODF `.odt` (`OdtExtract`), and `PslGate` kernels.
2. **Product-Module Coverage Frontier:**
   - R45 deepens `26/50` (`52.00%`) modules in a single round (vs R44's `23/50` = `46.00%`) and lifts cumulative R26–R45 pure-JVM kernel coverage to **`47/50` (`94.00%` of all modules; `47/47 = 100.00%` of product modules)** by bringing `:core:hw`, `:core:designsystem`, and `:feature:settings` into host-JVM contract coverage.

---

## D. NEGATIVE SPACE (>= 16 Failure Shapes & Modal Shortcut Failure Test)

| # | Failure Shape | Modal Shortcut Failure | R45 Refusal / Countermeasure |
|---|---|---|---|
| 1 | Formula cycle (`A1=B1+1, B1=A1+1`) | Infinite recursion / `StackOverflowError` | Tarjan/3-color topological sort marks `#CYCLE!` deterministically |
| 2 | `.xlsx` formula without cached `<v>` | Blank/`None` in viewers using `data_only=True` | `FormulaXlsx.write` evaluates DAG first and writes both `<f>` and `<v>` |
| 3 | `.docx` / `.xlsx` macro smuggling | Accepting `.docm`/`.xlsm` `vbaProject.bin` renamed to `.docx`/`.xlsx` | Both `DocxPackage.read` and `FormulaXlsx.read` refuse any `vbaProject.bin` entry |
| 4 | ODF `.odt` million-cell repeat bomb | `table:number-columns-repeated="1048576"` OOMs parser | `OdtExtract` enforces `MAX_COL_REPEAT=256`, `MAX_ROW_REPEAT=64`, `MAX_CELLS=16384` |
| 5 | Smart-quote / em-dash corrupting code & URLs | Turning `--verify` in `` `cmd --verify` `` or `https://x/a--b` into `—` | `DashEducate.educateOutside` + `ProseEducate` exclude fenced/inline code & URLs |
| 6 | PieceTable edit splitting UTF-16 surrogate pair | Mid-emoji insert produces lone surrogates | `PieceTable` validates/refuses unpaired surrogate split indices |
| 7 | Regex zero-length infinite loop (`a*`) | `replaceAll` hangs at index 0 | `FindReplace` advances cursor by >=1 code unit after zero-length matches |
| 8 | Merkle second-preimage leaf/node confusion | `SHA256(L \|\| R)` allows 64-byte leaf to impersonate internal node | `MerkleTree` enforces RFC 6962 `0x00` leaf prefix and `0x01` internal-node prefix |
| 9 | Public-suffix supercookie (`Domain=.co.uk`) | Naive 2-label split allows `.co.uk` cookie sharing across all UK sites | `PslGate` classifies `co.uk` as `PUBLIC_SUFFIX` and unlisted TLDs as `UNLISTED_TLD` |
| 10 | Single-operator SCT quorum bypass | Accepting 2 SCTs issued by 2 logs run by the same operator | `CtSctPolicy` requires `>=2` distinct `operatorId`s AND checks distrusted CA list first |
| 11 | Forged/compromised CA intermediate (CVE-2026-113470) | Trusting valid SCTs or chain without emergency CA distrust check | `CtSctPolicy` short-circuits to `REJECT_CA_DISTRUSTED` on distrusted SPKI/cert hashes |
| 12 | Composite USB mouse+keyboard implant (O.MG / Rubber Ducky) | Allowing keyboard HID reports from a device enumerated as a mouse/charger | `HidReportAudit` parses HID descriptor `0x05,0x01,0x09,0x06` + flags superhuman `<5ms` cadence |
| 13 | Pre-boot / passthrough PCIe/Thunderbolt DMA | Checking only `/sys/kernel/iommu_groups` while `iommu.passthrough=1` | `DmaGuardAudit` requires `IommuMode.STRICT` + `preBootDmaProtection` + locked-port block |
| 14 | Tapjacking / cross-UID screen overlay during PIN entry | Trusting touch events when `FLAG_WINDOW_IS_OBSCURED` is set | `OverlayTouchGuard` blocks secret entry on obscured/partially-obscured/cross-UID overlays |
| 15 | Hung service stalling amnesic shutdown wipe | Waiting indefinitely on graceful stop before RAM wipe | `TailsCadence.planShutdown` forces `DEADLINE_FORCED_WIPE_POWEROFF` at `60_000 ms` |
| 16 | SSDP `LOCATION` WAN SSRF / cleartext exfiltration | Fetching `LOCATION: http://169.254.169.254/...` or public WAN IP | `SsdpHeader` restricts `LOCATION` strictly to RFC 1918 private IPv4 HTTP endpoints |
| 17 | Mid-word tile truncation & 390dp 6th-dock clipping (`r24c`) | Fixed 6-column dock + character-slice ellipsis (`"Settings an..."`) | `TileLabelFit` wraps on word boundaries and collapses 6th slot into `[More +2]` >= 56dp |
| 18 | Cross-file Kotlin package/symbol collision | Duplicate helper object names across modules breaking union sourceSets | `test_r45_tools` scans all `495` `src/main/**/*.kt` files for FQN uniqueness |

---

## E. MECHANISM PORTFOLIO (8 Distinct Mechanisms)

1. **Topological Reactive Spreadsheet Engine + Dual-Mode ECMA-376 `.xlsx` (`FormulaEval`, `FormulaXlsx`):** Recursive-descent expression parser, Tarjan cycle detector, dirty-closure propagator, and deterministic zero-epoch `.xlsx` reader/writer verified bidirectionally against Python `openpyxl`.
2. **Clean-Room WordprocessingML & ODF Writer Pipeline (`DocxNumbering`, `DocxPackage`, `OdtExtract`, `PieceTable`, `ProseEducate`, `FindReplace`, `TocTree`):** Append-buffer piece table, span-protecting typography educator, multi-level Roman/alpha/ordinal numbering, macro-refusing `.docx` OPC round-tripper, and bomb-capped `.odt` extractor.
3. **Browser Origin, CSP & CT Security Lattice (`CspHeader`, `PslGate`, `CtSctPolicy`):** W3C CSP3 directive/nonce/strict-dynamic evaluator, Mozilla PSL exact/wildcard/exception classifier, and RFC 6962 multi-operator SCT + emergency CA distrust evaluator.
4. **Tails-Mod Amnesic Lifecycle & Local Hardware Anti-Keylogger Suite (`TailsCadence`, `HidReportAudit`, `DmaGuardAudit`, `OverlayTouchGuard`, `amnesic_posture.py`):** Tails `7.15.1` cadence + 60s hard-deadline shutdown state machine, USB HID 1.11 report descriptor bytecode parser + keystroke timing detector, DMA/IOMMU/DMAR verifier, anti-tapjacking gate, and live Linux procfs/sysfs posture CLI.
5. **Responsive Shell & Analytical Physics Geometry (`TileLabelFit`, `SpringStep`, `WorkAreaFit`):** Word-boundary 2-line tile fitter, >=48dp dock overflow planner, analytical damped harmonic oscillator ($\zeta < 1$, $\zeta = 1$, $\zeta > 1$), and multi-inset window rescue clamp.
6. **Cryptographic Integrity & Storage Framing (`MerkleTree`, `HkdfExpand`, `RleBitset`):** RFC 6962 `0x00`/`0x01` SHA-256 Merkle inclusion proofs, RFC 5869 HKDF-SHA256, and unsigned LEB128 interval bitsets.
7. **LAN Discovery, Cast & Continuity Protocols (`SsdpHeader`, `CastTxtProps`, `TxtRecord`, `ClipMime`):** SSRF-hardened SSDP parser, Cast mDNS TXT property decoder, RFC 6763 DNS-SD TXT wire codec, and safe clipboard MIME negotiator.
8. **Planning, Search, Geodesy & Accessibility (`DagTopo`, `Bm25Rank`, `UtmGrid`, `FinMath`, `KeyChord`, `EnvOverride`):** Kahn lexicographical wave planner, Okapi BM25 scorer, WGS84 UTM projector (Norway/Svalbard), TVM/IRR financial solver, accessible chord registry, and policy-locked settings resolver.

---

## F. ARTIFACT REQUIREMENT

- **Production Kotlin Kernels (`33` new + `1` touched across `26` modules):** All under `android/**/src/main/**/*.kt` with `const val SCOPE`, zero `import android`/`androidx`, zero `java.io.File`, compiled under `kotlinc -Werror`.
- **Host Contract Consumer:** `android/testing/src/r45/kotlin/dev/fusion/testing/R45Checks.kt` (`508` assertions).
- **Gradle & CI Wiring:** `android/testing/build.gradle.kts` and `.github/workflows/android.yml` (`r34Contracts..r45Contracts`).
- **Verification & Workbench Tooling:** `run_r45_contracts.py`, `check_r45_reference.py`, `check_r45_touch_targets.py`, `check_r45_manifest_scope.py`, `amnesic_posture.py`, `render_r45_visual_preview.py`, `fusion_workbench_cli.py`, `r45_census_deepen.py`, `r45_lab.py`, `test_r45_tools.py`.
- **Inspectable Visual Artifact:** `android/docs/verification/r45-host-workbench/r45-workbench-preview.png` + `r45-visual-audit.json`.

---

## G. SECOND-ORDER ADVERSARY

1. **The "Silent Formula Drift" Adversary:** Emitting `<f>SUM(B1:B2)</f>` without `<v>` passes naive XML schema checks yet yields `None` in read-onlyspreadsheet consumers (`openpyxl(..., data_only=True)`). Countered by bidirectional `openpyxl` dual-mode differential verification in `check_r45_reference.py`.
2. **The "Composite Mouse Keylogger" Adversary:** Hardware implants (O.MG Cable, stealth inline receivers) enumerate with a benign mouse product string while appending a second HID Top-Level Collection (`Usage Page 0x01, Usage 0x06`). Countered by `HidReportAudit` bytecode parsing + timing entropy checks.
3. **The "Visual Self-Certification" Adversary:** Generating a preview image without checking pixel text widths allows labels to collide with badges/columns. Countered by `R45VisualRenderer.drawChecked` (`75` `FontMetrics.stringWidth` bounds assertions) + direct human/agent visual inspection of the rendered PNG.
4. **The "Flavor Permission Bleed" Adversary:** Allowing sideload `pro` SMS permissions or `INTERNET` to leak into `app/src/main` or air-gapped `:privacy`. Countered by `check_r45_manifest_scope.py` across all 17 `AndroidManifest.xml` files.

---

## H. VERIFICATION

1. `python3 tools/run_r45_contracts.py` -> `508` assertions across `33` kernels (`+1` touched) in `26` isolated `-Werror` module jars.
2. `python3 tools/check_r45_reference.py --verify` -> Bidirectional `openpyxl` (`data_only=False` + `data_only=True` + foreign `.xlsx` ingest), `.docx` zero-epoch XML, RFC 5869 HKDF, RFC 6962 Merkle, and analytical physics/geodesy/TVM.
3. `python3 tools/render_r45_visual_preview.py` -> `1440x940` PNG, `75` `FontMetrics` non-overlap checks, WCAG AA contrast `>= 5.6:1`.
4. `python3 tools/r45_census_deepen.py` (3x) -> Byte-identical SHA-256 idempotence (`50/50 = 100.0%` testable subsets).
5. `python3 tools/run_jvm_contracts.py` (`183,246` assertions) & `python3 tools/run_subset_contracts.py` (`2,394` assertions) -> Zero regression on pilot/subset sourceSets.
6. `python3 -m unittest discover -s tools -p 'test_*.py'` -> `610/610` Python tests green (`52` in `test_r45_tools`).
7. `python3 tools/r45_lab.py host` -> End-to-end receipt stamped in `docs/R45-RESULTS.md`.

---

## I. REPAIR (Zero Inherited Trust — Real Failures Caught & Patched in R45)

1. **Repair 1 — `ProseEducate` Trailing Sentence Punctuation vs Internal URL Dots/Dashes:**
   - *Observed failure:* Initial union `R45Checks` run asserted `"https://example.com/a--b..."` stayed entirely inside the URL span, whereas `ProseEducate.scanUrlEnd` intentionally strips trailing sentence-ending `.` (`.,;:!?`) so prose `"Visit https://example.com."` doesn't swallow the period.
   - *Repair & adversarial regression:* Verified `scanUrlEnd` preserves internal `a--b/v1...v2` paths while educating trailing prose `...` -> `…`, and added dual assertions in `R45Checks.proseEducate` testing both internal `https://example.com/a--b/v1...v2` preservation and trailing punctuation boundary stripping.
2. **Repair 2 — `PieceTable.History` No-Op Edit Invariant:**
   - *Observed failure:* 24-step differential fuzz loop generated `replace(lo, lo, "")` at step `i=0` (`lo=hi=0, ins=""`), where `History.edit` intentionally suppresses pushing a duplicate snapshot onto `undoStack`.
   - *Repair & adversarial regression:* Added explicit `R45Checks` adversarial assertion that `hist.edit { it.replace(2, 2, "") } === hist && !noOp.canUndo` (proving no-op edits never pollute the 256-entry undo budget) before testing 24 mutating edits + full undo/redo walk.
3. **Repair 3 — `run_jvm_contracts.py` Missing `format_archive.jar` Classpath Dependency:**
   - *Observed failure:* Running legacy `run_jvm_contracts.py` failed compiling `format/ooxml/LiteralXlsxImport.kt` (`unresolved reference 'StrictZip'`), because `format/ooxml/build.gradle.kts` depends on `:format:archive` (`format_archive.jar`), which `run_jvm_contracts.py` omitted from `-classpath`.
   - *Repair & re-verification:* Passed `-classpath format_archive.jar` when compiling `format/ooxml` in `run_jvm_contracts.py`; re-ran full `run_jvm_contracts.py` (`183,246` assertions PASS) and `run_subset_contracts.py` (`2,394` assertions PASS).
4. **Repair 4 — Java2D Visual Preview Text-vs-Badge & Column Collisions:**
   - *Observed failure:* Visual inspection of the first rendered `r45-workbench-preview.png` caught 4 real horizontal text overlaps (Card 2 & Card 3 titles touching right-aligned badges, Row 6 formula touching `Evaluated <v>`, and Card 3 subtitle touching inner box border).
   - *Repair & adversarial gate:* Adjusted column coordinates/titles AND added `drawChecked` (`FontMetrics.stringWidth` hard bounds check) across all `75` drawn strings, which immediately caught one remaining 13px inner-padding overhang (`[c1-a1]: 691 > 678`), fixed it, re-rendered from zero, and visually re-verified zero collisions.

---

## J. STOPPING & NEXT FRONTIER (R46+)

Stopping criteria for R45 are met only when `r45_lab.py host`, `check_project.py`, and all `610` Python `unittest` checks exit `0` with `0/50` full-spec non-inflation preserved.

**Queued for R46+ (Honest Remaining Blockers):**
1. Provision an Android SDK 37 + NDK host/CI image to execute `./gradlew testDebugUnitTest lintDebug assemblePlayRelease assembleProRelease` and verify merged manifests via AGP.
2. Wire `TileLabelFit` and `WorkAreaFit` directly into Compose shellables (`DesktopTile`, `DockBar`) once Compose compiler/runtime jars are available in CI, and capture fresh on-device screenshots to retire `r24c` visual debt on Android hardware.
3. Execute physical arm64 (4K + 16K page) spikes S1–S30 for isolated `WebView`/Gecko/Servo processes, LibreOfficeKit `.so` bridge, and USB hardware attach quarantine.
