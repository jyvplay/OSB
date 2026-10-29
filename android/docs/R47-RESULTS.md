# Round 47 (`R47`) Verification Results

Date: `2026-10-26` (America/New_York)  
Branch: `arena/64550eba-osb`

---

## Executive Summary

- **Production Code Authored & Verified (`kotlinc -Werror -jvm-target 17` + Android AST `PARSE OK`)**:
  - **`61` new pure-JVM production kernels** + **`5` `TOUCHED` production files** (`MainActivity.kt`, `AdaptiveDock.kt`, `UsbWatchReceiver.kt`, `MapScreen.kt`, `FormulaEval.kt`) across **`48 / 50 = 96.00%`** Gradle modules (`47/47 = 100.00%` of product modules + `:benchmark`).
  - Total repository Kotlin production sources: **`595`** `src/main/**/*.kt` files (`0` stem or top-level symbol collisions).
- **Contract, Property & Adversarial Suite (`tools/run_r47_contracts.py`)**:
  - **`2,393` assertions PASS** across all `61` new kernels + `61`-function `FormulaEval` (`2.70x` R46's `885`).
- **Independent Cross-Language Differential Oracle (`tools/check_r47_reference.py`)**:
  - **`40 / 40` checks PASS** across `.docx`, `.xlsx`, `.pdf` (`28` exact `xref` byte offsets), `.odt` (`STORED` `mimetype@38`), `.svg`, `.wav` (`1-LSB` 440 Hz sine), `.idx` (Git packfile v2 SHA-1), `.ar` (BSD `#1/36`), `.icc` (ICC v4 `acsp`), `PMT`/`PV`/`FV`/`NPV`, Tor Proposal 351 `<torS0X>0`, SMPTE 29.97 DF, GF(256) Shamir Lagrange, and ICAO 9303 MRZ.
- **Visual & 360dp Anti-Clipping Audit (`tools/render_r47_visual_preview.py` + direct `read_file` visual inspection)**:
  - **`111` `FontMetrics` non-overlap checks PASS** (including an exact `360px` compact phone viewport frame), minimum WCAG 2.1 contrast **`5.68:1`** (`>= 4.5:1`), exported to `docs/verification/r47-host-workbench/r47-workbench-preview.png` (`1560 x 1020`).
- **Live Interactive Browser & CLI Workbench (`tools/fusion_workbench_server.py` + `tools/fusion_workbench_cli.py`)**:
  - Live HTTP workbench bound to `0.0.0.0:8765` verified with synthetic `Host: 8765-sandbox.e2b.app` proxy headers and strictly relative `api/...` URLs.
- **Contiguous Gradle & GitHub Actions CI Bridge**:
  - Extended `testing/build.gradle.kts` and `.github/workflows/android.yml` to **`22` contiguous contract tasks (`r26Contracts..r47Contracts`)**, forming an unbroken **`r13..r47`** CI verification chain alongside `android-r25-png.yml`.
- **Runtime Honesty & Class-A Verdict**:
  - **Testable Subsets (`docs/module-testability.json`)**: `50 / 50 = 100.00%`.
  - **Full-Spec Class A Completion (`docs/module-audit.json`)**: **`0 / 50 = 0.00%`** (Android SDK 37, `aapt2`, `d8`, device UI, TalkBack, 16 KB ELF APK, and hardware IMU/USB/BLE gates cannot run in this Linux sandbox).

## Lab receipt

```text
R47 host lab PASS (openjdk version "17.0.9" 2023-10-17 / kotlinc-jvm 2.2.21 (JRE 17.0.9+9)) | 31 gates + 51 unittest suites (758 checks).
Harness: tools/r47_lab.py host --jobs 3
- R47 host contracts (61 kernels + 5 touched across 48/50 modules, 2,393 assertions): PASS
- R46 host contracts (regression, 885 assertions): PASS
- R45 host contracts (regression, 508 assertions): PASS
- R26 contracts (newly bridged CI lane, 157 assertions): PASS
- Java2D R47 workbench visual preview + WCAG/FontMetrics/360dp audit: PASS
- R47 differential reference oracle (40 cross-language checks): PASS
- R46 differential reference oracle (regression, 25 checks): PASS
- R45 differential reference oracle (regression): PASS
- R44 contracts (regression): PASS
- R44 reference (regression): PASS
- R40 contracts (DashEducate regression): PASS
- R36 contracts (CI bridge regression): PASS
- R35 contracts (CI bridge regression): PASS
- R34 contracts (CI bridge regression): PASS
- R33 contracts (CI bridge regression): PASS
- R32 contracts (CI bridge regression): PASS
- R31 contracts (CI bridge regression): PASS
- R30 contracts (CI bridge regression): PASS
- R29 contracts (newly bridged CI lane, 128 assertions): PASS
- R28 contracts (newly bridged CI lane, 112 assertions): PASS
- R27 contracts (newly bridged CI lane, 106 assertions): PASS
- android syntax gate (12 Compose/Android files): PASS
- layout-bounds self-test: PASS
- page-matrix self-test: PASS
- beauty/contrast reference: PASS
- touch-target static audit (48dp floor): PASS
- 17-manifest air-gap & permission scope gate: PASS
- amnesic host procfs/sysfs posture audit: PASS
- warm OpenJDK 17 R47 workbench daemon self-test: PASS
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
- python test_r47_tools: PASS 82/82
- python test_record_sdk_platform: PASS 2/2
- python test_release_gate: PASS 5/5
- python test_requirements: PASS 5/5
Hashes (sha256, consumers+artifacts):
  36ee7462d197786cc2702e1212824cc6fa97341c3aafdaae6940e418b6a871aa  testing/src/r47/kotlin/dev/fusion/testing/R47Checks.kt
  028e695b90582e22b0c7e379300fe3b65d5490b09c6af680c47293e487c3bafb  docs/verification/r47-host-workbench/r47-workbench-preview.png
  02518449a1c8467453fcc1bae2f95dc1971fb01c83af889a61f28de6cd1418c7  docs/verification/r47-host-workbench/r47-visual-audit.json
```
