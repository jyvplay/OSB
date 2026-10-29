# R46 Clean-Room Permissive Alternatives Matrix (Browser, Writer, Spreadsheet, PDF, Sandbox & Productivity)

Date: `2026-10-26`  
License Policy: **Strictly Permissive (`MIT`, `Apache-2.0`, `BSD-2/3`, file-scoped `MPL-2.0`) — Zero `GPL`/`AGPL`/`LGPL`, Zero Source Borrowing, 100% Clean-Room Kotlin From Scratch.**

---

## 1. Subsystem Blocker Bypass & Permissive Reference Architecture

| Subsystem | Upstream Blocker / Heavyweight Trap | Permissive Ecosystem Reference (`2026-10-26` Verified) | Fusion Clean-Room Kotlin Implementation (R45 + R46) | Host-Executable Without Android Studio? |
| :--- | :--- | :--- | :--- | :--- |
| **Spreadsheet (`.xlsx` / `.ods`)** | LibreOfficeKit JNI (`MPL/LGPL`, >100 MB native `.so`, 16 KB ELF alignment risk); Microsoft Excel proprietary | `IronCalc` (`MIT`/`Apache-2.0`), `Formualizer` (`MIT`/`Apache-2.0`), ECMA-376 Part 1, OASIS ODF 1.3 | `FormulaEval` (`35` pure functions), `FormulaXlsx`, **`MultiSheetBook`** (cross-sheet `'Q1 Sales'!B2:B10` DAG + zero-epoch `.xlsx`), **`SheetPivot`**, **`CondFormat`**, **`OdsSheetExtract`** | **YES** — CLI, `R46Checks`, `openpyxl 3.1.5` bidirectional oracle, and live `:8765` `.xlsx` download |
| **Writer & Academic Publishing (`.docx` / `.odt` / Markdown / BibTeX)** | Chromium WebView print-to-PDF (~150 MB), LibreOffice Writer JNI, Pandoc (`GPL-2.0+`) | `Mammoth 1.13.0` (`BSD-2`), `ProseMirror`/`Lexical` (`MIT`), `rdocx` (`MIT`/`Apache-2.0`), `office2pdf` (`Apache-2.0`) | `PieceTable`, `DocxPackage`, `DocxNumbering`, `ProseEducate`, `OdtExtract`, **`DocOutlineRender`** (CommonMark <-> HTML5), **`TrackRevisions`** (`w:ins`/`w:del` + author scrub), **`CitationBib`** (IEEE/APA BibTeX) | **YES** — Live HTML5 + tracked-revisions + bibliography resolution in `:8765` workbench |
| **Paginated PDF Export (`.pdf`)** | `MuPDF` / `PyMuPDF` (`AGPL-3.0`), `iText 7+` (`AGPL-3.0`), `OpenHTMLtoPDF` (`LGPL`), Android `PdfDocument` (requires Android framework) | `Apache PDFBox 3.0.8` (`Apache-2.0`), `PDFium` (`BSD-3`), `WeasyPrint 70` (`BSD-3`), ISO 32000-1:2008 | **`PdfPageWriter`** (`%PDF-1.7` greedy word-wrap, multi-page pagination, Type1 Helvetica/Courier, tables, headers/footers, zero-metadata trailer, exact byte-offset `xref`) | **YES** — Byte-verified by independent Python PDF `xref` parser + downloadable `.pdf` in `:8765` |
| **Plugin & Extension Sandbox** | `GraalWasm` (`GPLv2+CE` outside classpath exception), `Wasmtime`/`Wasmer` JNI (`.so` 16 KB ELF alignment blocker), `Linux namespaces` (blocked in unprivileged Android app UID) | `Chicory 1.7.5` / Bytecode Alliance `Endive` (`Apache-2.0`, 100% pure-JVM Wasm), W3C WebAssembly Core 1.0 | **`WasmSandbox`** (pure-JVM binary decoder, structured `block`/`loop`/`br_if` stack machine, 64 KiB bounds-checked linear memory, deterministic fuel meter) + **`QuotaBudget`** + **`PipeChain`** | **YES** — Executes real Wasm 1.0 bytecode (`fib(0..20)`) and traps infinite loops in `< 2 ms` |
| **Browser Security & Origin Isolation** | Bundling unpatched WebView without header/origin enforcement; GeckoView native build overhead | `Servo 0.6.0` (`MPL-2.0`), W3C CSP3, W3C Permissions-Policy, Mozilla PSL (`MPL-2.0`), RFC 9162 CT | `CspHeader`, `PslGate`, `CtSctPolicy`, **`PermissionsPolicyHeader`** (parent-to-iframe origin intersection), **`HlsPlaylist`** (RFC 8216 HTTPS/AES-128 ABR) | **YES** — Full policy & origin-inheritance evaluation on host JVM |
| **Local-First Search, Sync, Backup & Terminal** | SQLite FTS native extensions, `rsync` (`GPL-3.0`), native `libvte` (`LGPL`) | Russ Cox `codesearch` (`BSD-3`), FastCDC, Hybrid Logical Clocks, ECMA-48 | `Bm25Rank`, **`TrigramIndex`**, **`WalFrame`**, **`ColumnarPage`**, **`TarPax`**, **`RabinChunker`**, **`CrdtLwwMap`**, **`VtScreenBuf`** (with `OSC 52` exfiltration drop) | **YES** — Verified by property sweeps and POSIX `tarfile` differential oracle |

---

## 2. How to Run Without Android Studio

```bash
cd /home/user/OSB/android

# 1. Run all R45 + R46 Kotlin contracts, openpyxl/PDF/PAX differential oracles, amnesic audit & visual PNG render:
python3 tools/fusion_workbench_cli.py all

# 2. Launch the interactive browser workbench (Spreadsheet .xlsx + Writer PDF 1.7 + Wasm Sandbox + Anti-Keylogger):
python3 tools/fusion_workbench_cli.py serve --host 0.0.0.0 --port 8765

# 3. Run the full 20-gate + 50-suite R46 verification lab:
python3 tools/r46_lab.py host
```
