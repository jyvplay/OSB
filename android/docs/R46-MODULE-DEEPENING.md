# R46 Per-Module Testability vs Full-Spec Completion Report (`50` Modules)

Date: `2026-10-26`

## 1. Headline Quantitative Summary (Absolute Counts & Exact Percentages)

| Metric | R45 Baseline | R46 Result | Delta |
| :--- | :--- | :--- | :--- |
| **Total Gradle Modules (`settings.gradle.kts`)** | `50` (`47` product + `3` infra) | **`50`** (`47` product + `3` infra) | `0` (`100.00%` accounted) |
| **Modules with Verified Host-Testable Subsets (`module-testability.json`)** | `50 / 50` (`100.00%`) | **`50 / 50` (`100.00%`)** | Maintained `100.00%` |
| **Full-Spec Class A Completed Modules (`module-audit.json`)** | `0 / 50` (`0.00%`) | **`0 / 50` (`0.00%`)** | **`0` status inflation** (`50/50 = 100.00%` unfinished) |
| **Modules Deepened in Current Round** | `26 / 50` (`52.00%`) | **`34 / 50` (`68.00%`)** | **`+8` modules (`+16.00` pp)** |
| **Product Modules Deepened Across `R45 + R46` Combined** | `26 / 47` (`55.32%`) | **`47 / 47` (`100.00%`)** | **`+21` product modules (`+44.68` pp)** |
| **New Pure-JVM Production Kernels Added in Round** | `33` | **`39`** | **`+6` kernels (`+18.18%`)** |
| **Existing Production Files Repaired/Expanded (`TOUCHED`)** | `1` (`DashEducate.kt`) | **`3`** (`DesktopChrome.kt`, `TabletopDesktop.kt`, `FormulaEval.kt`) | **`+2` touched files (`3x`)** |
| **Total `src/main/**/*.kt` Source Files** | `495` | **`534`** | **`+39` files (`+7.88%`)** |
| **Round Host-JVM Production Assertions (`R46Checks` vs `R45Checks`)** | `508` | **`885`** | **`+377` assertions (`+74.21%`)** |
| **Round Hygiene & Honesty `unittest` Checks (`test_r46_tools`)** | `52` | **`66`** | **`+14` checks (`+26.92%`)** |
| **Contiguous Gradle CI Contract Tasks (`:testing:r30..r46Contracts`)** | `12` (`r34..r45`) | **`17` (`r30..r46`)** | **`+5` CI tasks (`+41.67%`)** |

---

## 2. Priority Subsystem Completeness & Remaining Android Device Blockers

| Priority Subsystem | Host-Executable Completeness Achieved (No Android Studio Needed) | Exact Gate Blocking `complete` in `docs/module-audit.json` |
| :--- | :--- | :--- |
| **Spreadsheet (`:format:ooxml`, `:format:lok`, `:feature:calc`)** | `MultiSheetBook` cross-sheet `.xlsx` read/write + global DAG cycle detection, `FormulaEval` (`35` ECMA-376 functions), `SheetPivot` group-by cubes, `CondFormat` color-scales/data-bars, `OdsSheetExtract` ODF 1.3 `.ods` bounded reader/writer, `FinMath` TVM/NPV/IRR; verified against `openpyxl 3.1.5` & live `:8765` UI. | Android Compose 2D virtualized grid scrolling, IME formula-bar input, clipboard intent wiring, and TalkBack cell-coordinate announcements on physical API 36/37 device. |
| **Writer & PDF (`:core:documents`, `:feature:editor`, `:format:pdf`, `:format:ooxml`)** | `PieceTable` undo/redo, `DocxPackage` + `DocxNumbering`, `OdtExtract`, `DocOutlineRender` CommonMark <-> HTML5, `TrackRevisions` (`w:ins`/`w:del` + author scrub), `CitationBib` IEEE/APA BibTeX, `PdfPageWriter` ISO 32000-1 `%PDF-1.7` paginated export; live `:8765` preview + `.pdf` download. | On-device Compose rich-text cursor/selection handles, Android `PrintDocumentAdapter` integration, embedded TrueType subsetting for CJK/RTL scripts, and physical keyboard shortcut testing. |
| **Sandbox (`:core:capabilities`, `:core:perf`, `:core:commands`)** | `WasmSandbox` pure-JVM Wasm 1.0 binary decoder + linear memory bounds + deterministic fuel trap (zero JNI / 16KB ELF safe), `QuotaBudget` token buckets, `PipeChain` shell-free pipelines. | Android isolated-process `<service android:isolatedProcess="true">` Binder bridge and OS `seccomp-bpf` / cgroup memory pressure telemetry on device. |
| **Privacy & Anti-Keylogger (`:privacy`, `:core:hw`, `:core:security`, `:app`)** | `OnionGraterFilter` Tor ControlPort `510` filter, `MetadataScrub` OOXML/ODF/PDF scrubber, `PngMetadataStrip`, `MacRandomizer`, `UsbGuardPolicy`, `BleHidPairingGate` (`CVE-2023-45866`), `KeystrokeJitter`, `TailsCadence`, `OverlayTouchGuard`, `DmaGuardAudit`, `HidReportAudit`. | Android `UsbManager` / Linux `usbguard` sysfs bind hook, `BluetoothHidHost` pairing interceptor, root/OS kernel memory wipe on hardware token pull, and AGP merged-manifest verification. |
| **Browser (`:feature:browser`, `:core:security`)** | `CspHeader`, `PslGate` (eTLD+1 + WebAuthn RP ID), `CtSctPolicy` (RFC 9162 + CA distrust), `PermissionsPolicyHeader` (iframe origin inheritance). | Real WebEngine (`GeckoView` / `Servo` / `Android WebView`) process embedding, network stack cookie jar partitioning, and 16 KB native `.so` alignment audit. |

---

## 3. All `34 / 50` (`68.00%`) Modules Deepened in `R46`

1. `:app` — `KeystrokeJitter.kt` + **TOUCHED** `DesktopChrome.kt` & `TabletopDesktop.kt`
2. `:core:capabilities` — `WasmSandbox.kt`
3. `:core:commands` — `PipeChain.kt`
4. `:core:documents` — `DocOutlineRender.kt`, `TrackRevisions.kt`
5. `:core:hw` — `UsbGuardPolicy.kt`, `BleHidPairingGate.kt`
6. `:core:model` — `SemVerRange.kt`
7. `:core:perf` — `QuotaBudget.kt`
8. `:core:security` — `MacRandomizer.kt`
9. `:core:settings` — `SchemaMigrate.kt`
10. `:core:storage` — `WalFrame.kt`
11. `:feature:alerts` — `RateWindow.kt`
12. `:feature:automation` — `CronExpr.kt`
13. `:feature:backup` — `RabinChunker.kt`
14. `:feature:beam` — `ChunkedTransfer.kt`
15. `:feature:browser` — `PermissionsPolicyHeader.kt`
16. `:feature:capture` — `EdlCutList.kt`
17. `:feature:editor` — `CitationBib.kt`
18. `:feature:files` — `PathConflict.kt`
19. `:feature:find` — `TrigramIndex.kt`
20. `:feature:launcher` — `DesktopEntryIni.kt`
21. `:feature:lens` — `BarcodeGs1.kt`
22. `:feature:media` — `HlsPlaylist.kt`
23. `:feature:messages` — `QuotedPrintable.kt`
24. `:feature:notes` — `MarkdownTableFmt.kt`
25. `:feature:terminal` — `VtScreenBuf.kt`
26. `:feature:together` — `CrdtLwwMap.kt`
27. `:feature:vault` — `PassphraseDice.kt`
28. `:format:archive` — `TarPax.kt`
29. `:format:image` — `PngMetadataStrip.kt`
30. `:format:lok` — `OdsSheetExtract.kt`
31. `:format:native` — `ColumnarPage.kt`
32. `:format:ooxml` — `MultiSheetBook.kt`, `SheetPivot.kt`, `CondFormat.kt` + **TOUCHED** `FormulaEval.kt`
33. `:format:pdf` — `PdfPageWriter.kt`
34. `:privacy` — `OnionGraterFilter.kt`, `MetadataScrub.kt`

*(The remaining `13` product modules — `:cast:airplay`, `:cast:castv2`, `:cast:dlna`, `:core:designsystem`, `:core:files`, `:core:index`, `:core:windowing`, `:feature:accessibility`, `:feature:calc`, `:feature:continuity`, `:feature:maps`, `:feature:settings`, `:format:psd` — were deepened in `R45`, giving **`47 / 47` (`100.00%`) product-module coverage across `R45 + R46`**, while `:benchmark`, `:baselineprofile`, and `:testing` retain verified host subsets across `R30..R46`.)*
