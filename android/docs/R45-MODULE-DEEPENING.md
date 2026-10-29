# R45 Module Deepening & Per-Module Testability vs Full-Spec Completion Census

**Date:** `2026-10-26`  
**Canonical Machine Sources:** `docs/module-testability.json` (`50/50`) and `docs/module-audit.json` (`0/50`)

---

## 1. Executive Quantitative Summary (Absolute Values & Percentages)

| Metric | Absolute Count | Percentage (`N / 50`) | Product-Module Share (`N / 47`) | Delta vs R44 |
|---|---:|---:|---:|---|
| **Included Gradle Modules (`settings.gradle.kts`)** | `50 / 50` | `100.00%` | `47` product + `3` harness | `0` |
| **Full-Spec Class A Accepted (`docs/module-audit.json`)** | **`0 / 50`** | **`0.00%`** | **`0.00%`** | `0` (honestly held at `0/50`) |
| **Unfinished at Full Original v2/v2.1 Scope** | `50 / 50` | `100.00%` | `100.00%` | `0` |
| **Substantial Runnable Testable Subset (`docs/module-testability.json`)** | **`50 / 50`** | **`100.00%`** | **`100.00%` (`47/47`)** | Maintained `100.00%` |
| **Unestablished Testable Subsets** | `0 / 50` | `0.00%` | `0.00%` | Maintained `0.00%` |
| **Modules Owning `kotlinc -Werror` Host-JVM Executable Kernels** | **`47 / 50`** | **`94.00%`** | **`100.00%` (`47/47`)** | **`+3` modules (`88.00% -> 94.00%`; closes `:core:hw`, `:core:designsystem`, `:feature:settings`)** |
| **Harness-Only Structural/Python-Tested Modules** | `3 / 50` | `6.00%` | N/A (`:benchmark`, `:baselineprofile`, `:testing`) | `0` |
| **Modules Deepened in R45** | **`26 / 50`** | **`52.00%`** | **`55.32%` (`26/47`)** | **`+3` modules vs R44 (`23/50 = 46.00%`)** |
| **New + Touched Production Kernels in R45** | **`33` new + `1` touched** | — | — | **`+9` new (`+37.5%`) + `1` touched vs R44 (`24`)** |
| **R45 Host-JVM Contract Assertions (`R45Checks.kt`)** | **`508`** | — | — | **`+130` (`+34.39%`) vs R44 (`378`)** |
| **R45 Tool Hygiene `unittest` Checks (`test_r45_tools.py`)** | **`52`** | — | — | **`+17` vs R44 (`35`), `+14` vs R43 (`38`)** |
| **Total Repository Python `unittest` Checks** | **`610 / 610`** | **`100.00%` pass** | — | **`+52` new tests** |
| **Contiguous Gradle CI Contract Tasks (`:testing:r34..r45Contracts`)** | **`12` tasks** | — | — | **`+4` tasks (`r34`, `r35`, `r36` backported + `r45` added)** |

---

## 2. All 26 Modules Deepened in R45 (`26 / 50 = 52.00%`)

| # | Module | R45 New / Touched Production File(s) | Ownership Cut / Non-Duplication Boundary | Full-Spec Status |
|---:|---|---|---|---|
| 1 | `:format:ooxml` | `FormulaEval.kt`, `FormulaXlsx.kt`, `DocxNumbering.kt`, `DocxPackage.kt` | Reuses `BoundedXml`, `ColLetters`, `SheetRange`, `CellErr`; leaves `Ooxml.kt` (`java.io.File`) untouched | `incomplete` (`0%` Class A) |
| 2 | `:core:documents` | `PieceTable.kt`, `ProseEducate.kt`, **`DashEducate.kt` (`TOUCHED`)** | Composes `SmartQuote` + `DashEducate.educateOutside` instead of re-implementing quote/dash rules | `incomplete` |
| 3 | `:feature:editor` | `FindReplace.kt` | Regex + `$0..$9` capture template search/replace complementary to literal session search | `incomplete` |
| 4 | `:format:lok` | `OdtExtract.kt` | Pure-JVM OASIS ODF 1.3 `.odt`/`.ods` bomb-capped reader; does NOT claim native `libmergedlo.so` | `incomplete` |
| 5 | `:format:native` | `RleBitset.kt` | Sorted interval run-length bitset with unsigned LEB128 framing | `incomplete` |
| 6 | `:feature:calc` | `FinMath.kt` | Closed-form TVM (`pmt`/`pv`/`fv`), `npv`, and Newton-secant `irr` | `incomplete` |
| 7 | `:feature:notes` | `TocTree.kt` | ATX heading outline + slug disambiguation; does NOT duplicate `NoteLinks` wiki-link graph | `incomplete` |
| 8 | `:feature:browser` | `CspHeader.kt`, `PslGate.kt` | W3C CSP3 + Mozilla PSL eTLD+1; leaves Punycode/IDNA tables to `Punycode`/`HostCanon` | `incomplete` |
| 9 | `:core:security` | `CtSctPolicy.kt`, `DmaGuardAudit.kt` | RFC 6962 multi-operator SCT + CA distrust + PCIe/Thunderbolt IOMMU/DMAR posture | `incomplete` |
| 10 | `:core:hw` | `HidReportAudit.kt` | **First pure-JVM kernel in `:core:hw`**: USB HID 1.11 descriptor bytecode + timing anomaly detector | `incomplete` |
| 11 | `:privacy` | `TailsCadence.kt`, `OverlayTouchGuard.kt` | Tails 7.15.1 cadence + 60s hard-deadline wipe + anti-tapjacking gate | `incomplete` |
| 12 | `:app` | `TileLabelFit.kt` | Word-boundary 2-line tile wrapper + >=48dp dock overflow planner fixing `r24c` visual defects | `incomplete` |
| 13 | `:core:designsystem` | `SpringStep.kt` | **First pure-JVM kernel in `:core:designsystem`**: exact analytical damped harmonic oscillator | `incomplete` |
| 14 | `:core:windowing` | `WorkAreaFit.kt` | Multi-cutout/IME/dock inset resolver + off-screen title-bar rescue clamp | `incomplete` |
| 15 | `:core:files` | `MerkleTree.kt` | RFC 6962 `0x00`/`0x01` domain-separated SHA-256 Merkle tree + inclusion proofs | `incomplete` |
| 16 | `:feature:settings` | `EnvOverride.kt` | **First pure-JVM kernel in `:feature:settings`**: 5-layer policy-locked precedence resolver | `incomplete` |
| 17 | `:format:psd` | `PsdLayerRecords.kt` | PSD Layer & Mask record header parser; leaves PackBits RLE pixel decode to `PsdCompositeEngine` | `incomplete` |
| 18 | `:cast:dlna` | `SsdpHeader.kt` | UPnP 2.0 SSDP parser enforcing RFC 1918 private LAN-only `LOCATION` URLs | `incomplete` |
| 19 | `:cast:castv2` | `CastTxtProps.kt` | `_googlecast._tcp` mDNS TXT property decoder; leaves protobuf varints to `Proto.kt` | `incomplete` |
| 20 | `:cast:airplay` | `TxtRecord.kt` | RFC 6763 §6 length-prefixed DNS-SD TXT wire codec | `incomplete` |
| 21 | `:feature:accessibility` | `KeyChord.kt` | Canonical modifier+key chord parser & conflict-refusing accessible shortcut registry | `incomplete` |
| 22 | `:feature:maps` | `UtmGrid.kt` | WGS84 Transverse Mercator UTM forward/inverse projector (incl. Norway 32V & Svalbard 31X..37X) | `incomplete` |
| 23 | `:feature:vault` | `HkdfExpand.kt` | RFC 5869 HKDF-SHA256 Extract-and-Expand verified against Appendix A.1–A.3 | `incomplete` |
| 24 | `:feature:continuity` | `ClipMime.kt` | RFC 2045 clipboard MIME negotiator stripping HTML/script types in strict mode | `incomplete` |
| 25 | `:feature:automation` | `DagTopo.kt` | Deterministic Kahn lexicographical topological wave planner + cycle witness | `incomplete` |
| 26 | `:core:index` | `Bm25Rank.kt` | Okapi BM25 probabilistic full-text scorer with deterministic `docId` tie-breaking | `incomplete` |

---

## 3. Per-Module Clarity Across All 50 Modules

- **`47 / 50` (`94.00%`) Product Modules (`100.00%` of the 47 product modules):** Every single product module in `settings.gradle.kts` now owns real, mock-free Kotlin production code compiled under `kotlinc` and executed on the host JVM (`docs/MODULE-TESTABILITY.md`).
  - The 21 product modules not modified in R45 retain their SHA-256-pinned R12–R44 runnable contracts (`:core:capabilities`, `:core:commands`, `:core:model`, `:core:perf`, `:core:settings`, `:core:storage`, `:feature:alerts`, `:feature:backup`, `:feature:beam`, `:feature:capture`, `:feature:files`, `:feature:find`, `:feature:ime`, `:feature:launcher`, `:feature:lens`, `:feature:media`, `:feature:meet`, `:feature:messages`, `:feature:office`, `:feature:photos`, `:feature:quickpanel`, `:feature:terminal`, `:feature:together`, `:format:archive`, `:format:image`, `:format:pdf`).
- **`3 / 50` (`6.00%`) Harness Modules (`:benchmark`, `:baselineprofile`, `:testing`):** Substantially testable via SHA-256-pinned structural, build-wiring, and contract-runner suites (`test_r45_tools.py`, `check_project.py`), while device macrobenchmark execution awaits physical arm64 hardware.

---

## 4. Honest Requirements to Promote Writer, Spreadsheet & Core Modules to Class A (`docs/module-audit.json`)

Why do Writer (`:feature:office`, `:feature:editor`, `:format:ooxml`, `:format:lok`) and Spreadsheet (`:format:ooxml`, `:feature:calc`) remain `incomplete` (`0/50`) despite R45's real `.docx`, `.odt`, and `openpyxl`-verified `.xlsx` engines?

1. **Android SDK 37 + Compose UI Wiring & Lint Closure:** Wire `PieceTable` + `ProseEducate` + `DocxPackage` and `FormulaEval` + `FormulaXlsx` into interactive Compose surfaces built under `./gradlew assemblePlayRelease assembleProRelease lintRelease` with zero warnings.
2. **Process-Isolated Parser Execution (`android:isolatedProcess="true"`):** Move untrusted `.docx`/`.xlsx`/`.odt` byte ingestion across a sealed `ParcelFileDescriptor` IPC boundary so parser OOMs/faults cannot terminate the window manager.
3. **Physical Device Spikes (S18–S21, API 34 & API 37, 4K & 16K Page Sizes):** Measure 60 fps scroll/edit latency, IME composition (`Japanese/CJK/RTL`), TalkBack cell/paragraph traversal, and 16 KB ELF alignment on physical arm64 hardware.
4. **SBOM & License Closure:** Pin transitive Maven checksums and generate cyclonedx/SPDX license closure across both `play` and `pro` flavors.
