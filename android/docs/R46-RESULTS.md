# R46 Verification Results

Date: `2026-10-26`  
Scope: **Host OpenJDK `17.0.9+9` + `kotlinc 2.2.21` + Python `3.11.2` (`openpyxl 3.1.5`) + Headless Java2D Visual Audit + Live `:8765` Warm-JVM Browser Workbench.**  
Non-Scope (Runtime Honesty): **NOT Android SDK / AGP / emulator / physical device / TalkBack / hardware USB-BLE radio evidence.** Full-spec module completion in `docs/module-audit.json` remains strictly **`0 / 50` (`0.00%`)**; substantial host-testable subsets in `docs/module-testability.json` are **`50 / 50` (`100.00%`)**.

---

## 1. R46 vs R45 Verified Progression

- **New pure-JVM production kernels**: `39` (`+18.18%` vs R45 `33`), `534` total `src/main/**/*.kt` files (`0` cross-file symbol collisions).
- **Touched production repairs**: `3` (`DesktopChrome.kt` 360dp clipping + divider fix, `TabletopDesktop.kt` 2-line `TileLabelFit` pins, `FormulaEval.kt` `19 -> 35` functions; `check_r26_android_syntax.py` `PARSE OK` across all `10` Compose files).
- **Modules deepened in R46**: `34 / 50` (`68.00%` vs R45 `26 / 50 = 52.00%`); **`47 / 47` (`100.00%`) product modules deepened across `R45 + R46`**.
- **R46 host-JVM contract assertions (`R46Checks.kt`)**: **`885` PASS** (`+74.21%` vs R45 `508`), including property sweeps over Wasm `fib(0..20)`, 40-offset `WalFrame` crash truncations, 60 GS1 barcode check-digit/error vectors, 30 3-replica `CrdtLwwMap` semilattice joins, 40 `QuotedPrintable` binary round-trips, 30 `ColumnarPage` sweeps, and 40 `MacRandomizer` LAA vectors.
- **Cross-engine differential reference oracle (`check_r46_reference.py --verify`)**: **`25 / 25` PASS** (`openpyxl` 3-sheet `.xlsx` `data_only=False` & `data_only=True`, ISO 32000-1 PDF 1.7 `xref` byte-offset verification, Python `tarfile` POSIX PAX round-trip, `quopri`/`email.header`, `zlib.crc32` PNG strip, `hmac` SHA-256 MAC, Wasm `fib(20)=6765`).
- **Visual inspection & headless Java2D audit (`r46-workbench-preview.png`)**: Inspected `art-studio-graphite.png`, `business-office-blue.png`, `r45-workbench-preview.png`, and `r46-workbench-preview.png` (`1520x980`, `77` `FontMetrics` non-overlap checks PASS, min WCAG AA contrast **`5.68:1`**).
- **Contiguous Gradle CI contract bridge**: **`17` tasks (`r30Contracts..r46Contracts`)** wired in `android/testing/build.gradle.kts` and `.github/workflows/android.yml`.
- **Live No-Android-Studio Browser Workbench**: Running on `0.0.0.0:8765` (`tools/fusion_workbench_server.py`) backed by warm OpenJDK 17 `R46WorkbenchDaemon`; all 6 HTTP endpoints (`GET /`, `GET /api/status`, `POST /api/sheet`, `POST /api/writer`, `POST /api/wasm`, `POST /api/security`) verified end-to-end.

## Lab receipt

```text
R46 host lab PASS (openjdk version "17.0.9" 2023-10-17 / kotlinc-jvm 2.2.21 (JRE 17.0.9+9)) | 25 gates + 50 unittest suites (676 checks).
Harness: tools/r46_lab.py host
- R46 host contracts (39 kernels + 3 touched, 885 assertions): PASS
- R45 host contracts (regression, 508 assertions): PASS
- Java2D R46 workbench visual preview + WCAG/FontMetrics audit: PASS
- R46 differential reference oracle (25 cross-engine checks): PASS
- R45 differential reference oracle (regression): PASS
- R44 contracts (regression): PASS
- R44 reference (regression): PASS
- R40 contracts (DashEducate regression): PASS
- R36 contracts (CI bridge regression): PASS
- R35 contracts (CI bridge regression): PASS
- R34 contracts (CI bridge regression): PASS
- R33 contracts (newly bridged CI lane): PASS
- R32 contracts (newly bridged CI lane): PASS
- R31 contracts (newly bridged CI lane): PASS
- R30 contracts (newly bridged CI lane): PASS
- android syntax gate (10 Compose files incl. DesktopChrome & TabletopDesktop): PASS
- layout-bounds self-test: PASS
- page-matrix self-test: PASS
- beauty/contrast reference: PASS
- touch-target static audit (48dp floor): PASS
- 17-manifest air-gap & permission scope gate: PASS
- amnesic host procfs/sysfs posture audit: PASS
- warm OpenJDK 17 workbench daemon self-test: PASS
- HID topology parser (fixture): PASS
- project structure & SHA-256 census: PASS
- python test_bounded_process: PASS 3/3
- python test_ci_annotations: PASS 8/8
- python test_collect_native_evidence: PASS 6/6
- python test_device_results: PASS 6/6
- python test_engine_intake: PASS 2/2
- python test_gradle_distribution: PASS 3/3
- python test_module_audit: PASS 6/6
- python test_module_testability: PASS 7/7
- python test_native_lab: PASS 6/6
- python test_native_source_audit: PASS 13/13
- python test_pilot_reporting: PASS 4/4
- python test_png_capture: PASS 7/7
- python test_probe_android_sdk: PASS 4/4
- python test_r17_evidence: PASS 7/7
- python test_r18_evidence: PASS 8/8
- python test_r19_evidence: PASS 5/5
- python test_r20_evidence: PASS 8/8
- python test_r21_evidence: PASS 9/9
- python test_r22_evidence: PASS 9/9
- python test_r23_evidence: PASS 10/10
- python test_r23_input_tools: PASS 5/5
- python test_r23_lab: PASS 4/4
- python test_r24_evidence: PASS 4/4
- python test_r24_runner: PASS 2/2
- python test_r24c_tools: PASS 21/21
- python test_r25_tools: PASS 8/8
- python test_r26_tools: PASS 9/9
- python test_r27_tools: PASS 11/11
- python test_r28_tools: PASS 10/10
- python test_r29_tools: PASS 10/10
- python test_r30_tools: PASS 10/10
- python test_r31_tools: PASS 9/9
- python test_r32_tools: PASS 12/12
- python test_r33_tools: PASS 13/13
- python test_r34_tools: PASS 12/12
- python test_r35_tools: PASS 11/11
- python test_r36_tools: PASS 13/13
- python test_r37_tools: PASS 19/19
- python test_r38_tools: PASS 24/24
- python test_r39_tools: PASS 30/30
- python test_r40_tools: PASS 38/38
- python test_r41_tools: PASS 33/33
- python test_r42_tools: PASS 36/36
- python test_r43_tools: PASS 38/38
- python test_r44_tools: PASS 33/33
- python test_r45_tools: PASS 52/52
- python test_r46_tools: PASS 66/66
- python test_record_sdk_platform: PASS 2/2
- python test_release_gate: PASS 5/5
- python test_requirements: PASS 5/5
Hashes (sha256, consumers+artifacts):
  f8d3b91c7d83a303148ae18b9865a3d4372316c8cf7d543de9beeeac510ea89d  testing/src/r46/kotlin/dev/fusion/testing/R46Checks.kt
  275059e7b1cb6c8765b2f2b2feb865fba850858ebf164193f4545eecfb34de6f  docs/verification/r46-host-workbench/r46-workbench-preview.png
  71d0d02da7f8120e0627566db5653498845a0dc7f7221b492b3cb75ebe81d66e  docs/verification/r46-host-workbench/r46-visual-audit.json
```
