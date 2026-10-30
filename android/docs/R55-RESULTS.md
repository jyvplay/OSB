# R55 — spreadsheet blank-reference repair and native verification continuation

**Date:** 2026-10-30. This is a bounded correctness improvement and evidence report, **not Class A acceptance**. The [R54 report](R54-RESULTS.md) describes the current Writer/sheet/export scope and unresolved gates. The draft [PR #12](https://github.com/jyvplay/OSB/pull/12) was to remain open in R55; the later R56 request to merge after verification supersedes that instruction.

## Runtime honesty, census, and pre-commit threshold

The local sandbox has no Android SDK, emulator, Studio GUI, or physical device. Host JVM execution, independent host readers, and GitHub-hosted Android builds/device runs are separate evidence categories. For this change the declared threshold is: executable tests of the changed behavior; independent inspection where feasible; affected regression suites; source/CI checks; and explicit disclosure of Android/provider/visual gaps. Meeting that threshold does **not** confer Class A status.

The reviewed, hash-bound census remains **50/50 included modules (100%)** with at least a narrowly defined substantial executable subset. This does not mean that complete modules are implemented or that every behavior was freshly tested. The separate full-spec audit records **0/50 accepted (0%)** and **50/50 unfinished (100%)**. The census is a repository review, not an independent expert audit.

## A. Contract

For the existing scalar, single-sheet engine, an absent/blank **source** remains `Empty` while an expression is being evaluated. When the complete scalar formula result is stored as an output cell, an `Empty` result becomes numeric zero. Explicit `""` is a text result, not zero and not an absent cell. Formulas recalculate in dependency order, so downstream `COUNT`, `COUNTA`, and comparisons see the materialized value. The private spill-engine temporary scalar probe must keep the pre-existing intermediate-blank behavior; it is not an actual output cell.

Example: if A1 is absent, B1 `=A1` stores numeric 0, `COUNT(B1)` is 1, and `COUNTA(B1)` is 1; `COUNT(A1)` remains 0. A formula that explicitly returns `""` stays text and `COUNTA` counts it. Later source edits and clearing cause dependent recalculation, and old immutable drafts remain unchanged. The XLSX writer stores the same tested formula and typed cache. A cache is not proof of what any particular Office program will recalculate.

The rule is intentionally limited to scalar formula cells. It does not assert complete Excel/LibreOffice compatibility or fix all dynamic-array/legacy calculation semantics. The R54 authoring route continues to refuse unsupported or resource-risky formulas and arbitrary Office import.

## B. H+, H− and boundary

- **H+:** the real Kotlin JVM code passes the R55 contracts, including source-present/clear/recovery transitions and retained spill-subexpression behavior. An independent implementation and ZIP/XLSX reader examine finite sample artifacts. Details and exact outcomes are in H.
- **H−:** real Microsoft Excel or LibreOffice recalculation, broad spreadsheet compatibility, rich Writer, safe arbitrary Office import, production release, physical hardware, accessibility and device/provider atomicity are not established by these results.
- **Boundary:** sample agreement with another spreadsheet engine is not proof that either engine matches Excel. A rendered blank is ambiguous: a nonexistent cell, an explicit empty string, and a formula's numeric zero can produce different typed results and downstream calculations. Syntax-only Android parsing is not an Android compile.

## C. Research frontier

1. [LibreOffice's empty-cell documentation](https://help.libreoffice.org/latest/en-US/text/scalc/05/empty_cells.html) explicitly distinguishes its blank handling from Excel and states that Excel returns a number for a formula reference to an empty cell. The [ICAEW discussion](https://www.icaew.com/technical/technology/excel-community/excel-community-articles/2025/why-blank-cells-calculate-as-zero-and-does-it-matter) describes when a blank reaches the grid as zero. These are sources of the bounded contract, not local Excel execution.
2. [Formualizer](https://github.com/yousefmansy1/formualizer) is an independent spreadsheet engine. PyPI version **0.13.0** reports an MIT license in its package metadata and was used **on the host only**; the current GitHub repository reports Apache-2.0. It is not added to the Android app, and its outputs are not treated as authoritative when they disagree with documented semantics. [Apache POI's evaluator](https://poi.apache.org/components/spreadsheet/eval.html) and the MIT [xlsx-evaluate](https://github.com/devind-team/xlsx_evaluate) are other possible differential mechanisms, not engines evaluated or bundled here.
3. Microsoft's [AVERAGE](https://support.microsoft.com/en-us/excel/functions/average-function), [MIN](https://support.microsoft.com/en-us/office/min-function-61635d12-920f-4ce2-a70f-96f202dcc152), and [MAX](https://support.microsoft.com/en-us/excel/functions/max-function) descriptions say nonnumeric direct text causes an error. The chosen reference instead returns 2 for three such cases. Those **three disagreements** are reported separately, not counted as matches, and did not cause us to copy the reference's result blindly.
4. The Android runner log captured `qemu-system-x86_64` failing to load `libpulse.so.0` in [run 41006023385](https://github.com/jyvplay/OSB/actions/runs/41006023385). The host-only [Ubuntu libpulse0 package](https://packages.ubuntu.com/noble/libpulse0) and an [upstream missing-library report](https://github.com/actions/runner-images/issues/6253) informed the targeted workflow prerequisite. SDK package download success alone did not solve the host dependency.

## D. Misleading-result and negative-space catalogue

| # | Tempting but misleading result | Boundary or test |
|---|---|---|
| 1 | The grid looks blank, so the cell is absent | Source absence, empty text, and formula result have separate types. |
| 2 | A blank source and numeric zero are identical everywhere | Blank source is ignored by `COUNT`/`AVERAGE`, a formula zero is not. |
| 3 | A blank equals empty text, so the stored zero also equals empty text | Both cases are explicitly checked; stored numeric zero compares differently. |
| 4 | `COUNT` and `COUNTA` should agree | Explicit empty-string formula results count in `COUNTA`, not `COUNT`. |
| 5 | Every intermediate empty reference should immediately become zero | Materialization is at scalar output; the spill helper retains an intermediate blank. |
| 6 | A formula's displayed zero is a source cell containing zero | The genuinely absent source remains absent in the XLSX. |
| 7 | A previous immutable draft changes when the current source changes | Prior snapshots and repeated present/clear transitions are checked. |
| 8 | A checkpoint checksum means a backup or authenticated storage | Recovery round-trip is bounded integrity evidence only. |
| 9 | An XLSX contains `<f>`, therefore the cached value is right | The typed `<v>` and downstream caches are inspected separately. |
| 10 | A cached value proves actual Excel recalculation | openpyxl reads caches; it does not calculate formulas. |
| 11 | openpyxl `None` for an empty cached string means the original source was absent | The XML string type and formula are inspected; reader limitation is disclosed. |
| 12 | One reference implementation is always the authority | Three reference disagreements are retained and checked against documented direct-text rules. |
| 13 | A refused overflow case is a matching formula result | It is counted as one refusal, not a reference match. |
| 14 | A small floating-point comparison may treat any subnormal as zero | Comparator tests reject this and reject boolean/number type confusion. |
| 15 | Hundreds of matches imply full spreadsheet conformance | The corpus is finite, deliberately narrow, and does not include real Excel. |
| 16 | A successful SDK download guarantees an executable emulator | A separate version probe failed on a missing host library. |
| 17 | Retrying an SDK download installs missing Ubuntu libraries | Three retries failed until the host-package prerequisite was added. |
| 18 | `ldd` on the emulator launcher covers the child QEMU binary | The actual child loader error is captured; launcher linkage did not reveal it. |
| 19 | Installed host library proves emulator boot or passing tests | Actual boot and instrumentation are separate observed gates. |
| 20 | An empty report or successful screenshot-transport job means a device pass | No test result or frame is invented; inspect source run cases and frames. |
| 21 | A single actual screenshot approves all sizes, contrast, and accessibility | Only the captured device state and declared target measurements are inspected. |
| 22 | A prior revision's device run proves the current revision | Run IDs, commit heads and unrun follow-up tests are kept distinct. |
| 23 | Android-free parsing is a native build | Android compilation and on-device tests require actual SDK execution. |
| 24 | Substantial testable subsets mean 100% module completion | The census and the 0/50 full-acceptance audit are deliberately separate. |

## E. Mechanism-distinct approaches

1. **Typed empty/number/text/error values:** represent the distinction rather than patching display strings.
2. **Final-output materialization:** convert only a scalar formula's finished empty result; leave source absence distinct.
3. **Internal subexpression path:** preserve the legacy spill helper's intermediate values and exercise its existing defaults.
4. **Recalculation/recovery:** repeatedly change, clear, checkpoint and reopen an immutable draft; no provider behavior is inferred.
5. **Independent formula differential:** compare typed output with another real engine, retaining disagreements and refusals separately.
6. **Interchange inspection:** inspect real XML/ZIP and openpyxl reads independently of the Kotlin writer/reader round trip.
7. **Native/visual evidence:** compile and execute on a disposable API 34 emulator, with actual case reports and captured frames; this is distinct from host tests.
8. **Runner fault diagnosis:** identify the QEMU loader message and install/check its host library; installation itself is not a passing device test.

## F. Artifacts and reproduction

From `android/`, configure the documented JDK 17/Kotlin toolchain (or run the disposable host-only `python3 tools/bootstrap_host_tools.py install`) and run:

```sh
python3 tools/run_r55_contracts.py
python3 tools/check_r55_reference.py --require-reference
PYTHONPATH=tools python3 -m unittest test_r55_reference test_install_r54_sdk test_emulator_host_prereq
```

The reference command requires separately installed, host-only `openpyxl==3.1.5` and `formualizer==0.13.0`. `testing/build/r55/artifacts/blank-references.xlsx`, `blank-multisheet.xlsx`, and `formula-corpus.tsv` are generated build products; the TSV is observations, not an oracle. The corresponding Gradle task is `:testing:r55Contracts`. The Office workflow requires pinned independent tools; other workflows may report the differential as unavailable if they are not installed. The scoped Android UI test now also checks a blank reference and downstream `COUNT`, but is not a pass until executed on its own commit.

## G. Adversarial scope

Cases cover zero versus truly absent sources, explicit empty string, booleans, errors, direct-vs-referenced text, negative values, arithmetic underflow/overflow, formula dependencies, source clearing, recovery, old snapshots, and typed comparator mutations. The 913-formula comparison uses a fixed small source grid and relative tolerance `1e-12` with zero absolute tolerance. It is not a fuzzer, exhaustive proof, Excel process, hostile-provider experiment, or timing guarantee. Legacy `ROUND`, concatenation and many other operations remain outside the bounded R54 authoring route.

## H. Executed verification

- Before repair, an actual local JVM probe returned `CellValue.Empty` for `=A5`, `=IFERROR(A5,4)`, and `=IFERROR(1/0,A5)` with A5 absent; explicit `=IFERROR(1/0,"")` returned text. The repaired real JVM path passes **1,054 assertions**, including 101 repeated source-present/clear/recovery rounds, five legacy spill checks, and nine multi-sheet blank/result/count and roundtrip checks.
- The host-only formualizer 0.13.0 comparison ran **913 distinct formulas**: **909 typed matches**, **3 known reference disagreements**, **1 conservative refusal**, and **0 unexpected disagreements**. No disagreements/refusals are mislabeled as passes. The `AVERAGE(1e308,1e308)` candidate is refused without mutating the draft; this is not a correct returned spreadsheet error and remains a limitation.
- ZIP/XML and openpyxl 3.1.5 inspected the actual generated XLSX; the reference script performed **934 structural/corpus checks**, including the multi-sheet formula/caches and 913 well-formed observation rows. Four adversarial Python comparator tests passed. openpyxl did not recalculate formulas and interprets the empty-string cache as `None`; the XML `t="str"` check is separate.
- Post-change project/ownership/census/diff checks passed, as did **1,533 Python tests**. The Kotlin frontend parsed **65 Android-source/test files** without syntax errors; unresolved Android dependencies were expected, so this is not a type-check or Android build.
- [API 34 run 41006261319](https://github.com/jyvplay/OSB/actions/runs/41006261319), commit `f85ae06`, installed the host library, checked SDK components, compiled the app and test APK, ran the JVM contracts and independent readers, and finished **11/11 targeted device cases passed**: seven Office UI/provider cases and four copy-contract cases. The Writer case used the real system Create Document picker and observed byte-for-byte provider readback. The other provider cases used actual MediaStore destinations with direct injected results, not picker flows. The new R55 blank-reference change and added UI assertion were **not in that commit** and still need their own device run.
- A separate [API 34 tabletop/media/atlas regression](https://github.com/jyvplay/OSB/actions/runs/41006261731) at `f85ae06` passed **9/9** targeted tests, including local audio controls, offline map interactions, scene persistence, and targets exercised at 200% font; this is not whole-app or physical-device acceptance. Four transported 360×800 frames were hash-checked and visually inspected: [silver tabletop](verification/r55-native/r24c-silver.png), [blue tabletop](verification/r55-native/r24c-tabletop.png), [pile wall](verification/r55-native/r24c-wall.png), and [offline atlas](verification/r55-native/r24c-atlas.png). The primary tabletop/map controls and map are visible in these finite frames; the bottom dock abbreviates the Spreadsheet label (`Spreadsh…`). Frame metadata measured 48 dp or more for its declared targets only, at density 2.625 and font scale 1. The 200% behavior was tested separately, not shown in these frames. Hashes in that order: `4eca00c380af06eae70fd12d716d744b4147d2860bb33ee81c50d0869a5b8ea4`, `293fc8ab1fc5c9a9a8ad770b1c418846476b637cc9f74c07ab4a2b727d4bad94`, `f20400c77a2ca62c8b22ec0529fb5390621000454b85db1fd46cc58f8567dd69`, `db9d3df164be861d6c376577ed8edfddfce31972390f51e72f5c09206a114342`.
- The separately rerun [PNG regression 41006261360](https://github.com/jyvplay/OSB/actions/runs/41006261360) **failed overall**: isolated processing passed, but both system-picker cases failed before `READY` with Google Photos in the foreground. No PNG frame was captured. The cause is not yet established. Its test now checks that the PNG activity actually returned, rather than treating DocumentsUI disappearing as proof, and reports visible clickable controls on failure; that diagnostic change has not yet run.
- The two genuine API 34 application-surface frames from that successful run were visually inspected: [sheet](verification/r55-native/sheet-api34.png) and [Writer](verification/r55-native/writer-api34.png). The 360×800 previews were downsampled from 1080×2400, density 2.625, font scale 1. The sheet shows B1 = 8, a visible wrapped Export button (167.62×48 dp) and Apply (108.95×48 dp); the grid is below the fold in this captured scroll position. Writer shows entered text and a visible DOCX export target (171.05×48 dp). Sheet SHA-256 `5af4923358a529700a928b648f980a8312095d79774573661029547e83b6e82c`; Writer SHA-256 `1024212945c6e21b10b099dad1a41e931ac2d452a5d9a763ecf718ac67d256bf`. These finite captures do not establish layout at other sizes/font scales or accessibility acceptance.

## I. Repair and regression

The repair is in `FormulaEval.evaluateSheet`, with a separate internal temporary-evaluation path for `SpillSheetEngine`. The tests cover both formula output and the legacy intermediate path so a displayed fix does not silently change the spill helper's existing defaults. `MultiSheetBook`'s temporary probe represents an actual completed formula and is explicitly tested as such. Corpus and XLSX artifacts are generated from the changed implementation and checked by distinct host tools. After the repair, affected historical host suites passed: R45 **508** assertions plus independent references; R48 **3,861/56** assertions/reference checks; R50 **8,555/146**; R53 **25,425/411**; and R54 **662/21**. These are host results, not post-repair Android execution.

## J. Remaining work / stopping rule

R55 originally required keeping the PR open; see R56 for the later merge request and continuation. Finish the historical child-focus/candidate comparison and relevant regressions, and diagnose the failed PNG picker cases. The blank-reference UI assertion subsequently passed one API 34 PR run, while another run failed earlier in the type menu; both are recorded in R56. The prior Writer/sheet/provider rerun and the two finite frame inspections are recorded above, not generalized to new code or other devices. Separately pursue full Writer and spreadsheet editing/import/fidelity, Office-suite differential recalculation, broader provider/alias/race/process-death behavior, physical devices, multiple sizes and font scales, keyboard/IME, accessibility, security, performance, release and remaining modules. No test quantity or isolated fix upgrades those outstanding acceptance gates.
