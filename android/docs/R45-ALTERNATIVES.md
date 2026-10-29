# R45 Permissive Open-Source Architecture Alternatives & Class-A Path

**Date:** `2026-10-26`  
**License Policy:** Strictly permissive or file-level weak-copyleft where process/library boundaries permit all use cases (**MIT, Apache-2.0, BSD-2-Clause, BSD-3-Clause, ISC, Zlib, MPL-2.0**); **zero GPL/LGPL/AGPL** dependencies; all Fusion Shell kernels written clean-room from normative specifications.

---

## 1. Major Browser Engine — Overcoming Monolithic Bloat & Origin Leaks

### Current Blocker Analysis
Full Blink/Chromium source builds exceed 35 GB of build artifacts and require continuous multi-hundred-CVE security backports, while naive `android.webkit.WebView` embedding shares process/cookie state unless strictly isolated.

### Permissive Open-Source Architectural Options

| Option | Upstream Reference & License | Strengths | Honest Limitations (2026-10) | Role in Fusion Shell |
|---|---|---|---|---|
| **B1. Hardened Isolated System WebView + Clean-Room Policy Gate (Default Lightweight Path)** | Android `WebView` (`BSD-3` Chromium) in dedicated `android:isolatedProcess="true"` / `WebViewCompat.setDataDirectorySuffix` + Fusion `UrlPolicy`, `HostCanon`, `CspHeader`, `PslGate`, `CtSctPolicy` | **0 MB** APK size overhead; OS-managed Blink security updates; deterministic host-verified URL/CSP/PSL/CT pre-flight gates | Depends on host OS WebView version; cannot customize Blink internals (e.g., native Tor SOCKS proxy per-tab without `ProxyController`) | **Primary lightweight mode** (`<15 MB` shell budget); R45 adds `CspHeader`, `PslGate`, and `CtSctPolicy` |
| **B2. Mozilla GeckoView / Android Components** | Mozilla `GeckoView` (`MPL-2.0`) | True independent engine; first-party isolation (FPI), Total Cookie Protection, full WebExtension API, strong Tor Uplift heritage | Adds ~45–65 MB per ABI (`arm64-v8a`); requires native AAR intake + 16 KB ELF segment alignment audit | **Primary full-browser flavor (`pro` / Privacy Browser)** when independent rendering & anti-fingerprinting are required |
| **B3. Servo 0.6.0 Embedded WebView** | Servo Project (`MPL-2.0`, v0.6.0 Oct 2026) | Memory-safe Rust parallel layout (Stylo + WebRender), CSS Grid enabled by default in 0.6.0, clean new Embedding `WebView` API (proxies, custom certs, WebCrypto ML-KEM/ML-DSA) | ~110 MB `aarch64` APK footprint; upstream explicitly notes multi-year gap vs full desktop web compatibility | **Experimental Sandboxed Reader / Local HTML App View** (`:feature:browser` spike S14) |

---

## 2. Office Engine (Writer & Spreadsheet) — Honest Path Toward Class A

### Current Blocker Analysis
LibreOfficeKit (`LOK`) provides unmatched legacy layout fidelity but ships ~90–140 MB of C++ `.so` binaries, requires JNI tile rendering (`S18`–`S21`), and cannot run in pure host-JVM sandboxes without native `.so` artifacts. Meanwhile, AGPL spreadsheet engines (`HyperFormula`) and GPL suites (`Gnumeric`) violate our permissive-only constraint.

### Two-Tier Permissive Hybrid Architecture (Implemented Kernels + Native Escalation)

| Tier | Engine / Reference Architecture | License | Capabilities | R45 Concrete Delivery |
|---|---|---|---|---|
| **Tier 1: Pure-Kotlin Zero-JNI Instant Engine (< 500 KB, Host & Device)** | Clean-room from **ECMA-376 (OOXML)** + **OASIS ODF 1.3**, inspired by permissive designs of **IronCalc** (`MIT/Apache-2.0`), **Formualizer** (`MIT/Apache-2.0`), **Mammoth** (`BSD-2`), **ProseMirror/Lexical** (`MIT`) | **100% Clean-Room First-Party** | Opens, edits, recalculates, and saves `.xlsx`, `.docx`, `.odt`, `.csv`, and `.md` in milliseconds in memory; deterministic undo/redo; zero native crash surface; works identically on laptop CLI (`fusion_workbench_cli.py`) and Android | **`FormulaEval`**, **`FormulaXlsx`** (verified bidirectionally with `openpyxl`), **`DocxPackage`**, **`DocxNumbering`**, **`OdtExtract`**, **`PieceTable`**, **`ProseEducate`**, **`FindReplace`** |
| **Tier 2: Permissive WASM / Rust Calc & Layout Coprocessor (Optional Medium Tier)** | **IronCalc** (`MIT/Apache-2.0`, ~300 Excel functions) or **Univer** (`Apache-2.0`) / **PDFium** (`BSD-3-Clause`) | `MIT` / `Apache-2.0` / `BSD-3` | 300+ Excel financial/statistical formulas in ~400 KB WASM/JNI without C++ memory unsafety; crisp PDF export via PDFium | `FinMath` (`pmt`/`pv`/`fv`/`npv`/`irr`) bridges financial parity today; WASM coprocessor slots cleanly behind `FormulaEval` interface |
| **Tier 3: Isolated LibreOfficeKit 26.8 Tile Engine (Heavy Complex Fidelity)** | Collabora / LibreOffice `26.8` (`MPL-2.0`) in separate `:office_lok` process | `MPL-2.0` | Tracked changes, complex floating drawings, legacy `.doc`/`.xls`/`.ppt`, SmartArt pagination | Kept strictly optional/on-demand in isolated UID so crash or CVE in C++ parser never compromises `:app` or `:privacy` |

---

## 3. Sandbox Architecture — Defense-in-Depth Without Root

Android prohibits arbitrary unprivileged `unshare(CLONE_NEWUSER)` container namespaces inside standard apps, so pretending to run Docker/Bubblewrap inside an unprivileged APK is dishonest. Instead, Fusion Shell combines **four real, non-root mechanisms**:

1. **Android Isolated Process Sandboxes (`android:isolatedProcess="true"` + `Context.BIND_EXTERNAL_SERVICE`):**
   - Each untrusted parser/renderer (`WebView`, `LOK`, `PDF/PSD/Archive` previewer) runs in an ephemeral Linux UID with **zero filesystem permissions, zero network sockets, and zero Binder service discovery**, receiving only a sealed `ParcelFileDescriptor` + shared-memory output surface.
2. **In-Memory Zero-IO Format Kernels (Implemented across R26–R45):**
   - Every R45 parser (`DocxPackage`, `FormulaXlsx`, `OdtExtract`, `PsdLayerRecords`, `BoundedXml`, `StrictZip`) operates strictly on bounded `ByteArray` slices (`java.io.File` banned at compile/test gates), enforcing explicit byte, entry, depth, and cell-repeat caps before allocation.
3. **Capability-Based Wasmtime / Chicory Bytecode Sandbox (`Apache-2.0`):**
   - For user automation plugins (`:feature:automation`) and third-party formula extensions, pure-JVM **Chicory** (`Apache-2.0`, zero native `.so`!) or **Wasmtime** (`Apache-2.0`) provides deterministic fuel-metered WebAssembly execution with zero ambient WASI host capabilities unless explicitly granted.
4. **SAF Capability Descriptors + RFC 6962 `MerkleTree` Audit (`:core:files`):**
   - Read-only `mutationsAllowed = false` default in Files, explicit `CopyDestinationActivity` (`exported="false"`) user-mediated save broker, and `MerkleTree` inclusion verification on multi-part archives.

---

## 4. Productivity Suite — Composable Local-First Workbench

| Capability | Monolithic Cloud Trap | Fusion Permissive Local-First Design | R45 Verifiable Kernel |
|---|---|---|---|
| **Notes & Knowledge Graph** | Cloud-synced proprietary DB | Plain UTF-8 Markdown + wiki-links (`NoteLinks`) + heading outline (`TocTree`) + encrypted local index | `TocTree.kt` + `Bm25Rank.kt` |
| **Writer & Prose Editor** | Heavy web-view DOM editor | Immutable `PieceTable` (`MAX_HISTORY=256`) + code/URL-safe `ProseEducate` + regex `FindReplace` + `.docx`/`.odt` | `PieceTable.kt`, `ProseEducate.kt`, `FindReplace.kt`, `DocxPackage.kt`, `OdtExtract.kt` |
| **Spreadsheet & Finance** | Cloud sheet or AGPL library | `FormulaEval` reactive DAG + `FormulaXlsx` + `FinMath` TVM/IRR + `DecimalSheet` | `FormulaEval.kt`, `FormulaXlsx.kt`, `FinMath.kt` |
| **Task & Build Automation** | Unbounded shell scripts | Deterministic Kahn parallel-wave DAG (`DagTopo`) with cycle witnesses + accessible `KeyChord` bindings | `DagTopo.kt`, `KeyChord.kt` |
| **Vault & Continuity** | Opaque cloud keychain | RFC 5869 `HkdfExpand` domain-separated subkeys + strict `ClipMime` sanitizer stripping `text/html` scripts | `HkdfExpand.kt`, `ClipMime.kt` |
