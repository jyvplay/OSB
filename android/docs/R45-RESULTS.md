# R45 Verification Results & Honesty Ledger

**Date:** `2026-10-26` (UTC)  
**Branch:** `arena/64550eba-osb`  
**Toolchain Executed:** `kotlinc-jvm 2.2.21` + OpenJDK `17.0.9+9` (`-Djava.awt.headless=true`) + Python `3.11` + `openpyxl 3.1.5`

---

## 1. Summary of Executed Verification Gates

| Gate | Command | Scope & Honesty Boundary | Result |
|---|---|---|---|
| **R45 Host JVM Contracts** | `python3 tools/run_r45_contracts.py` | 26 isolated module jars (`-Werror`) + `R45Checks.kt` (`33` new kernels + `1` touched `DashEducate.kt`) | **PASS (`508` assertions)** |
| **Bidirectional Differential Oracle** | `python3 tools/check_r45_reference.py --verify` | `FormulaXlsx` <-> Python `openpyxl` (`data_only=False` + `data_only=True` + foreign `.xlsx` ingest), `DocxPackage` zero-epoch XML, RFC 5869 HKDF (6 vectors), RFC 6962 Merkle (`0..10`), `FinMath`, `SpringStep`, `UtmGrid`, `RleBitset` | **PASS** |
| **Java2D Workbench Visual & WCAG Audit** | `python3 tools/render_r45_visual_preview.py` | Live R45 kernel outputs rendered to `1440x940` PNG; `75` `FontMetrics.stringWidth` non-overlap checks; WCAG AA contrast `>= 5.6:1` (min floor `4.5:1`). Host Java2D only, NOT Android device rendering | **PASS (`143,729` B PNG)** |
| **Amnesic & Hardware Host Posture** | `python3 tools/amnesic_posture.py` | Live Linux `/proc/swaps`, `RLIMIT_CORE`, `suid_dumpable`, `/sys/kernel/iommu_groups`, `/proc/mounts`, `/sys/bus/usb` | **PASS (`swap=0B`, `core_disabled=True`, `strict_amnesic=False` honestly reported)** |
| **Touch-Target Static Gate** | `python3 tools/check_r45_touch_targets.py` | Enforces `TileLabelFit.MIN_TOUCH_TARGET_DP >= 48`, default dock slot `56dp`, and `0` sub-48dp layout XML attributes | **PASS** |
| **17-Manifest Scope & Air-Gap Gate** | `python3 tools/check_r45_manifest_scope.py` | Checks all `17` manifests: `app/src/main` exact scope, `pro`-only SMS isolation, `:privacy` zero-`INTERNET` + `HIDE_OVERLAY_WINDOWS`, global spyware permission ban | **PASS** |
| **3x Idempotent Testability Census** | `python3 tools/r45_census_deepen.py` (x3) | `26` deepened modules + `1` `TOUCHED` (`DashEducate.kt`); identical SHA-256 across 3 consecutive runs | **PASS (`50/50 = 100.0%`)** |
| **Pilot & Subset SourceSet Regressions** | `python3 tools/run_jvm_contracts.py` & `run_subset_contracts.py` | Full 13-module pilot contracts (`183,246` assertions) + 6-module subset contracts (`2,394` assertions) | **PASS** |
| **CI Bridge Backport Regressions** | `run_r34_contracts.py`, `run_r35_contracts.py`, `run_r36_contracts.py`, `run_r40_contracts.py`, `run_r44_contracts.py` | Verifies backported `r34` (`166`), `r35` (`129`), `r36` (`176`), touched `r40`, and `r44` (`378`) suites | **PASS** |
| **Repository Python Test Discovery** | `python3 -m unittest discover -s tools -p 'test_*.py'` | All `49` test modules (`610` tests, including `52` in `test_r45_tools.py`) | **PASS (`610 / 610`)** |
| **Project Structure & `0/50` Audit** | `python3 tools/check_project.py` | 231 requirements, 36 obligations, 495 main `.kt` files, wrapper SHA-256, `0/50` full-spec completion | **PASS (`0/50` complete)** |

---

## 2. Disclosed Repairs Executed During R45 (§I Zero-Trust Log)

1. **`ProseEducate` URL Trailing Punctuation Boundary (`R45Checks.kt`):** Separated internal URL dot/dash preservation (`https://example.com/a--b/v1...v2`) from trailing prose sentence punctuation (`...` -> `…`) and pinned both cases with dedicated assertions.
2. **`PieceTable.History` No-Op Suppression (`R45Checks.kt`):** Verified `History.edit` returns `this` without polluting `undoStack` on zero-effect `replace(x, x, "")` calls and added an explicit adversarial check before the 24-step differential fuzz loop.
3. **`run_jvm_contracts.py` Missing `format_archive.jar` Classpath (`tools/run_jvm_contracts.py`):** Fixed `format/ooxml` compilation classpath in legacy `run_jvm_contracts.py` to match `format/ooxml/build.gradle.kts` (`implementation(project(":format:archive"))`); re-ran all `183,246` pilot assertions + `2,394` subset assertions.
4. **`R45VisualRenderer` Horizontal Text Collisions (`tools/render_r45_visual_preview.py`):** Visual inspection of the initial render caught 4 text-vs-badge/column overlaps; added `drawChecked` (`FontMetrics.stringWidth` hard bounds enforcement across all `75` drawn strings), caught one remaining 13px overhang (`[c1-a1]`), fixed coordinates/copy, and visually verified the clean re-render (`docs/verification/r45-host-workbench/r45-workbench-preview.png`).

---

## 3. What Remains Unverified in This Sandbox

- Android SDK `platforms;android-37` / AGP merged-manifest / Compose compilation (`./gradlew assembleDebug lintDebug`).
- Physical arm64 device execution (4K & 16K page kernels), TalkBack/Switch Access traversal, thermal throttling, LibreOfficeKit native `.so` rendering, and live USB/DMA hardware attach tests.
- Consequently, `docs/module-audit.json` remains **`0 / 50` (`0.00%`)** complete.

## Lab receipt

```text
R45 host lab PASS (openjdk version "17.0.9" 2023-10-17 / kotlinc-jvm 2.2.21 (JRE 17.0.9+9)).
Harness: tools/r45_lab.py host
- R45 host contracts (33 kernels + 1 touched): PASS
- R45 differential reference oracle: PASS
- R44 contracts (regression): PASS
- R44 reference (regression): PASS
- R40 contracts (DashEducate regression): PASS
- R36 contracts (CI bridge regression): PASS
- R35 contracts (CI bridge regression): PASS
- R34 contracts (CI bridge regression): PASS
- android syntax gate: PASS
- layout-bounds self-test: PASS
- page-matrix self-test: PASS
- beauty/contrast reference: PASS
- R45 touch-target static audit: PASS
- R45 17-manifest scope gate: PASS
- amnesic host procfs/sysfs posture audit: PASS
- Java2D workbench visual preview + WCAG audit: PASS
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
- python test_record_sdk_platform: PASS 2/2
- python test_release_gate: PASS 5/5
- python test_requirements: PASS 5/5
Hashes (sha256, consumers+artifacts):
  845462a61b51507ed18e40cdc8bad62830ff4a8142648032fe5be29f881d6573  testing/src/r45/kotlin/dev/fusion/testing/R45Checks.kt
  90586f7660f81b08039e50b5f513bdcc9ca2f4cf1f0ae27978359fb4e9cb1bb8  docs/verification/r45-host-workbench/r45-workbench-preview.png
```
