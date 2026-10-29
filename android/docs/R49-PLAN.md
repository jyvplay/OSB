# Round 49 (`R49`) Engineering Plan — Runtime Honesty, Formal Model, Outcome Space & Frontier

## RUNTIME HONESTY STATEMENT

- **Exact executed host toolchain**:
  - OpenJDK `17.0.9+9` (`jdk4py 17.0.9.2`), Kotlin JVM compiler `kotlinc 2.2.21` (`kotlin-stdlib` + `org.jetbrains.kotlin.compiler.embeddable` PSI/AST parser), Python `3.11`, `openpyxl 3.1.5`, `XlsxWriter 3.2.5`, headless Java2D (`BufferedImage`, `Graphics2D`, `FontMetrics`), and live HTTP/CLI workbench (`0.0.0.0:8765`).
- **Exact unavailable external systems (never simulated, mocked, or claimed)**:
  - Android SDK Platform `37`, `aapt2`, `d8`/`r8`, Android Emulator / physical Pixel-Samsung-tablet hardware, SurfaceFlinger compositor (`FLAG_SECURE` / cross-window blur hardware pipeline), physical USB Type-C PD / `UsbManager` / `AOAP` bus, hardware Keystore StrongBox TEE, TalkBack / Switch Access accessibility services, and Google Play Billing servers.
- **Strict claim separation (`H+` vs `H-`)**:
  - `docs/module-audit.json` remains strictly at **`0 / 50 = 0.00%` full-spec Class A accepted**.
  - `docs/module-testability.json` advances to **`50 / 50 = 100.00%` host-executed R49 testable subsets** (`736` `src/main/**/*.kt` files: `75` new pure-JVM production kernels + `11` `TOUCHED` production upgrades including `8` newly wired Compose screens -> `25 / 25` Android/Compose AST `PARSE OK`).
  - `docs/feature-register.json` (`requirements.csv` / `FULL-SPEC-REVIEW.md`) advances every owner requirement backed by real, compiled, contract-verified production kernels from `not_implemented` to **`partial_unverified`** (`0 / 231` `verified`, `3` `excluded`, `1` `superseded`), recording exact production routes, `host:R49Checks` test IDs, and exact remaining Android SDK / physical device / AX acceptance gates.

---

## Direct Answer to the Class-A Promotion Challenge

> *"Can we go hard and turn both the writer and spreadsheet apps to Class A features this turn? And other core features into class A?"*

1. **Why `0 / 50` Full-Spec Class A Status Remains Unchanged (`H-`)**:
   - Under `tools/release_gate.py` and `tools/module_audit.py`, promoting any module (`:core:documents`, `:feature:editor`, `:format:ooxml`, `:feature:browser`, `:privacy`, etc.) to `status = "complete"` (Class A accepted) requires passing **all 7 common acceptance gates**: Android API 37 full `:app` APK compilation (`aapt2`/`d8`), 16 KB ELF page alignment check, physical multi-display / external-keyboard / stylus device execution, TalkBack / accessibility device verification (`AX-01..08`), `FLAG_SECURE` / SurfaceFlinger security verification, spike sign-offs (`S1..S30`), and signed release-artifact digests. None of those physical Android SDK/device gates exist in this Linux container, so marking any module `"complete"` or any requirement `"verified"` is forbidden and fails `release_gate.py`.
2. **How We Go Maximum Hard on Every Host-Executable Class-A Pillar (`H+`)**:
   - **Writer & Desktop Publishing (`:core:documents`, `:feature:editor`, `:feature:notes`)**: Adds Knuth–Plass optimal dynamic-programming paragraph line-breaking (`KnuthPlassLineBreaker`), Franklin Liang TeX trie hyphenation (`LiangHyphenationEngine`), multi-column widow/orphan page pagination (`MultiColumnPagePaginator`), ODF 1.3 `<text:tracked-changes>` XML round-trip (`OdfTrackedChangesCodec`), Myers word-level redline compare (`DocumentCompareDiff`), tabular Mail Merge (`MailMergeTemplateEngine`), BibTeX/CSL citation & bibliography formatting (`BibliographyCitationEngine`), Sticky Notes canvas (`StickyNoteBoardLayout`), and Markdown-to-Slide deck export (`MarkdownSlideExporter`).
   - **Spreadsheet, Charts & Solver (`:format:ooxml`)**: Upgrades `FormulaEval` from **`95 -> 130` Excel functions** with first-class lexical **`LAMBDA`** closures and immediate `LAMBDA(x, x+1)(41)` invocation; upgrades `SpillSheetEngine` from **`6 -> 22` spilled dynamic array & higher-order `LAMBDA` operations** (`MAP`, `REDUCE`, `SCAN`, `BYROW`, `BYCOL`, `MAKEARRAY`, `VSTACK`, `HSTACK`, `TAKE`, `DROP` including omitted row/col counts per `rustsheet #1608`, `TOCOL`, `TOROW`, `CHOOSECOLS`, `CHOOSEROWS`, `WRAPROWS`, `WRAPCOLS`, `TRANSPOSE`); adds `ECMA-376` `xl/charts/chart1.xml` DrawingML chart XML + SVG plot generation (`DrawingMlChartBuilder`), `x14:sparklineGroups` in-cell sparklines (`SheetSparklineRenderer`), two-phase Simplex LP + Goal-Seek (`SheetSolverSimplex`), and `vbaProject.bin` macro quarantine (`OoxmlMacroVbaInspector`).
   - **Presentation, PDF & Format Boundary (`:format:pdf`, `:format:native`, `:format:archive`, `:format:lok`, `:format:image`, `:format:psd`)**: Adds AcroForm field inspection/fill planning (`PdfFormAcroFieldInspector`), PDF `BT..ET` text-search quad highlighting (`PdfPageTextSearchHighlighter`), detached `/ByteRange` signature shadow-attack verification (`PdfPkcs7SignatureVerifier`), `formats.yaml` engine/grade registry (`FormatsRegistryCatalog`), lossy Save-As fidelity advisor (`ExportFidelityAdvisor`), WinZip `AE-2` `0x9901` encrypted ZIP header/PBKDF2-HMAC-SHA1 verifier (`ZipAesExtraFieldCodec`), parser bomb watchdog (`FormatSandboxWatchdog`), LOKit 256px tile pyramid cache (`LokTileRenderCache`), HEIC ISOBMFF `ftyp`/`ispe`/`irot` parser (`HeicIsomBoxInspector`), and `.ai` PDF-compatibility detector (`AiPdfCompatibilityBridge`).
   - **Browser (`PR-07b..s`) & Wasm 2.0 / WASI (`:feature:browser`, `:core:capabilities`)**: Adds `RFC 8828` Mode 1–4 WebRTC ICE SDP candidate leak scrubber + `.local` mDNS masking (`WebRtcIceCandidateScrubber`), split-pane / tab-group / pinned / sleeping / session-restore workspace (`BrowserSplitTabWorkspace`), profile-isolated site permission matrix (`SitePermissionVault`), diacritic-optional Find-in-Page + shortcut dispatcher (`FindInPageMatchEngine`), WASI socket CIDR firewall (`WasiSocketCapabilityGate`), API 37 `AppFunctions` schema gate (`AppFunctionsSchemaRegistry`), and upgrades `WasmSandbox` with `Table` / `call_indirect`, `Global`, passive/active `Data` (`memory.init`/`data.drop`), `f32` ops, and `0xFC` `i32.trunc_sat_f32_s/u`.
   - **Tails-Light Anonymity & Local Hardware Anti-Keylogger Defenses (`:privacy`, `:core:security`, `:core:hw`, `:feature:vault`)**: Adds `RFC 9849` TLS Encrypted Client Hello (`TlsEchConfigCodec`), Tails `7.15.1` emergency compromised-CA SPKI blocklist + Secure Boot 2026 expiry auditor (`TailsCaTrustPinBlocklist`), USENIX Security 2025 `ChoiceJacking` charger/AOAP/HID->BLE guard (`ChoiceJackingUsbGuard` + `UsbDataSignalingController`), ACM CCS 2025 `Pixnapping` `CVE-2025-48561` blur/overlay/GPU.zip surface shield (`PixnappingSurfaceShield`), 25 ms epoch power/DVFS keystroke timing mitigator (`PowerDvfsTimingMitigator`), Pro local `VpnService` UID/DNS leak firewall (`VpnAppFirewallPolicy`), per-module app lock (`ModuleAppLockGate`), and Tink-compatible streaming AEAD keyset containers (`TinkAeadKeysetEnvelope`).

---

## A. FORMAL MODEL

Let $M = \{m_1, \dots, m_{50}\}$ be the 50 declared Gradle modules in `settings.gradle.kts`, $R = \{r_1, \dots, r_{231}\}$ be the 231 normalized owner requirements in `docs/feature-register.json`, and $K_{49} = K_{\text{new}} \cup K_{\text{touched}}$ with $|K_{\text{new}}| = 75$ and $|K_{\text{touched}}| = 11$ ($3$ pure-JVM core upgrades + $8$ Compose UI screens).

1. **100% Module Coverage & Dependency Soundness**:
   $$\forall m \in M,\; |K_{\text{new}}(m)| \ge 1,\quad \text{missing\_internal\_edges}(M) = \emptyset$$
   Every Compose screen wiring in $K_{\text{touched}}$ binds exclusively to pure-JVM kernels within its own module or already-declared Gradle `implementation(project(...))` edges, preserving `native_source_audit.py --check`.
2. **Bounded Spreadsheet `LAMBDA` & Spill Termination**:
   Every `CellValue.Lambda(params, bodyAst, lexicalEnv)` closure carries an immutable lexical environment and executes under strict call-depth ceiling $D_{\max} = 32$ and spill-cell budget $S_{\max} = 4{,}096$, guaranteeing deterministic termination without Turing-divergent infinite recursion (`#NUM!` / `IllegalArgumentException` on recursion budget overflow).
3. **Traceability Soundness (`requirements.py` & `release_gate.py`)**:
   $$\forall r \in R:\; \text{status}(r) \neq \text{verified} \;\land\; \text{ClassA}(M) = 0/50$$
   Transitioning $r: \text{not\_implemented} \to \text{partial\_unverified}$ requires an existing, compiled production kernel in $r$'s owner candidate module(s) exercised by an automated host contract check, while explicitly preserving all unpassed `gates` (`S1..S30`) and Android device obligations.

---

## B. OUTCOME SPACE (`H+`, `H-`, `H∂`)

- **`H+` (Strictly Proven & Host-Executed in `R49`)**:
  - `75` new pure-JVM production kernels + `11` `TOUCHED` files across `50 / 50 = 100.00%` modules (`736` `src/main/**/*.kt` files total).
  - `25 / 25` Android/Compose UI files verified `PARSE OK` by `kotlinc` PSI/AST (`check_r26_android_syntax.py`, up from `17` in `R48`).
  - `FormulaEval` at `130` functions + `LAMBDA` closures; `SpillSheetEngine` at `22` dynamic array & higher-order `LAMBDA` operations; `openpyxl` + `XlsxWriter` independent cross-check.
  - `> 5,200` `R49Checks.kt` assertions, `>= 72` Python 3.11 differential oracle checks (`check_r49_reference.py`), `>= 210` Java2D `FontMetrics` + pairwise 2D non-overlap checks (`render_r49_visual_preview.py`), `38` contiguous CI round tasks (`r13Contracts..r49Contracts`), live `:8765` `/api/r49/*` endpoints + CLI subcommands, and `38` host lab gates + `53` `unittest` suites (`>= 980` checks).
- **`H-` (Explicitly Refused / Unclaimed)**:
  - `0 / 50` full-spec Class A modules; `0 / 231` `verified` owner requirements; `0 / 30` passed device spikes; zero claims of kernel-level Linux `netfilter`, Android `UsbManager` HAL execution, SurfaceFlinger `FLAG_SECURE` hardware compositing, FairPlay (`CA-04`), or separate GPL receiver APKs (`CA-06`, `CA-07`).
- **`H∂` (Exact Boundary Manifest — `docs/class-a-readiness-r49.json`)**:
  - Machine-readable per-pillar and per-requirement boundary mapping every pure-JVM kernel and wired Compose UI screen to the exact Android API 37 / device / TalkBack / hardware test needed on an Android Studio runner to cross from `partial_unverified` to `verified`.

---

## C. FRONTIER (Strict Dominance Over `R48`)

| Dimension | `R47` | `R48` | **`R49` Target** |
| --- | ---: | ---: | ---: |
| New pure-JVM production kernels | `61` | `66` | **`75`** |
| `TOUCHED` production files | `5` | `7` | **`11`** (`3` JVM + `8` Compose screens) |
| Total `src/main/**/*.kt` files | `595` | `661` | **`736`** |
| Modules deepened in-round | `48 / 50` | `50 / 50` | **`50 / 50` (`100.00%`)** |
| Android/Compose AST `PARSE OK` files | `12` | `17` | **`25`** |
| `FormulaEval` functions + `SpillSheetEngine` ops | `61` + `0` | `95` + `6` | **`130` + `22` (incl. `LAMBDA`/`MAP`/`REDUCE`/`SCAN`/`BYROW`/`BYCOL`/`MAKEARRAY`)** |
| Owner `requirements.csv` `partial_unverified` rows | `59` | `59` | **`>= 140` (`0` `verified`, `0/50` Class A)** |
| Round contract assertions (`R49Checks.kt`) | `2,393` | `3,861` | **`>= 5,200`** |
| Python 3.11 differential oracle checks | `40` | `56` | **`>= 72`** |
| Java2D `FontMetrics` + 2D non-overlap checks | `111` | `172` | **`>= 210`** |
| Contiguous Gradle & CI round tasks | `22` | `37` | **`38` (`r13..r49`)** |
| Host lab gates + `unittest` suites | `31` + `51` (`758`) | `35` + `52` (`863`) | **`38` + `53` (`>= 980`)** |
