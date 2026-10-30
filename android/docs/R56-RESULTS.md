# R56 — actual-device continuation and bounded Office deepening

**2026-10-30; in progress.** This is a continuation of [R55](R55-RESULTS.md), not an assertion that the full original application or any module is Class A. The owner now requests merging [PR #12](https://github.com/jyvplay/OSB/pull/12) at the end of the turn; that supersedes R55's earlier instruction to keep it open. A merge is not evidence of Class A acceptance.

## Runtime and evidence thresholds

Available: this workspace, local Python 3 and an installed host Kotlin/JDK toolchain; independent host-only `formualizer`, `openpyxl`, and `python-docx`; GitHub Actions with managed Android API 34 emulators, source repositories and web search. Android Studio Rabbit 1, a local Android SDK/emulator, physical Android hardware, Microsoft Excel, LibreOffice, independent human experts, a theorem prover, and independent coding agents were **not** available or used locally. GitHub-run instrumentation is real execution on an emulator, not physical-device evidence. No simulation is described as execution.

Before committing the **test-harness continuation**, require actual failing device evidence, conservative assertions that still require real picker results/readback, adversarial reporter checks, source/census checks, a Kotlin syntax gate, and explicit disclosure that Android compile/device verification of the changed tests comes **after** their commit. For any production change, additionally require executable changed-behavior contracts, independent comparison where feasible, affected historical regressions, and a clean source/diff check. A green finite test suite is not sufficient for full-spec acceptance.

The reviewed testability census is **50/50 included modules (100%)** with at least a bounded substantial executable subset; that is not complete implementation. The separate full-spec audit remains **0/50 modules accepted (0%)**, with **50/50 unfinished (100%)**. Because the subset count is already 50/50, further work can deepen features but cannot increase that count without changing its denominator or standard.

## A. Formal model and adjacent problems

The prior-run device tests operate only on disposable seeded documents in an API 34 managed emulator. A real picker test succeeds only if the app regains foreground, receives a provider result, performs the specified operation, verifies the output and checks the source unchanged. The result is quantified over the **executed cases and revision**, not over all users, OEM pickers, providers, devices or schedules. A test selector observation is evidence about the selected accessibility node, not evidence of a completed application operation. Bounds are 15 seconds for return/status waits and limited test fixture/annotation sizes; neither a timer nor a screenshot proves a provider guarantee.

The repaired scalar `ROUND` accepts a finite binary64 value and finite `num_digits`, truncates fractional digits toward zero, uses decimal half-away-from-zero on `BigDecimal.valueOf(value)` (the JVM's canonical decimal representation of that binary64 input), and does not silently clamp the requested scale. If the rounded result exceeds finite binary64, an explicit spreadsheet error is preferable to infinity; temporary memory and time must remain bounded. This is a proposed **bounded** contract, not a claim to match every Excel intermediate, binary precision rule, locale or recalculation. Source-file import, rich editing, concurrent provider safety, formatting display and financial decimal arithmetic are different problems and cannot be substituted for it.

## B. Outcome space

- **H+**: the specified finite picker flows and a bounded, independently compared rounding implementation can be exercised on their actual respective systems.
- **H−**: no result here proves full Writer/spreadsheet completeness, real Excel equivalence, all-provider atomicity, physical-device or accessibility acceptance. Inferring a universal claim from finitely many device configurations is logically invalid; that is not a proof that the product goal is impossible.
- **H∂**: Excel-like behavior depends on representation, decimal ties, under/overflow, fractional scale, picker implementation and lifecycle. Results may change at those boundaries.

## C. Frontier and observed counterexamples

Microsoft's [ROUND documentation](https://support.microsoft.com/en-us/excel/functions/round-function) describes positive, zero and negative digit positions, with examples such as `ROUND(-1.475,2)=-1.48`; [W3Schools](https://www.w3schools.com/excel/excel_round.php) explicitly describes half-away-from-zero. The [Apache POI discussion](https://lists.apache.org/thread/76tolnoj4yq2ot5w3990pvvbvqmhrsgx) cautions that spreadsheet implementations and Excel's 15-digit handling can differ. These descriptions do not substitute for executing Excel.

An actual host probe of the pre-R56 shared `FormulaEval` gave `ROUND(1.005,2)=1.0`, `ROUND(0.00000000000015,13)=0`, `ROUND(15000000000000,-13)=1.5e13` and `ROUND(1e308,2)=#NUM!`. Host-only `formualizer 0.13.0` returned `1.01`, `2e-13`, `2e13`, and `1e308` respectively. The old evaluator clamped scale to ±12 and multiplied binary floating-point; the R54 authoring route deliberately refused `ROUND`. These are real counterexamples in the **shared** evaluator, not evidence the refused authoring route previously returned wrong results. R56 replaces that scaling with bounded decimal rounding and admits `ROUND` to the editor. It returns the input for scales ≥400 and zero for scales ≤−400, since a finite binary64's canonical decimal representation has fewer than 400 positions; intermediate scales use `HALF_UP`, and a nonfinite result becomes `#NUM!`. The guard avoids allocations proportional to an arbitrary digit request.

The [API 34 Office PR run 41007836134](https://github.com/jyvplay/OSB/actions/runs/41007836134) passed 11/11 targeted cases on `1855617`, including the new blank-reference/COUNT UI assertion. The [push run 41007831123](https://github.com/jyvplay/OSB/actions/runs/41007831123) of the same revision failed 1/11 at the earlier type-menu interaction before that assertion. This is a real inconsistent result; no root cause or reliability fix is yet proved. On the later harness-only revision `818f737`, both the [push Office run 41010107423](https://github.com/jyvplay/OSB/actions/runs/41010107423) and [PR Office run 41010113230](https://github.com/jyvplay/OSB/actions/runs/41010113230) passed **11/11** cases. These two runs did not include R56's subsequent `ROUND` change.

The [PNG diagnostic 41007831095](https://github.com/jyvplay/OSB/actions/runs/41007831095) passed isolated processing but failed both picker cases with Google Photos' full-image viewer in the foreground, not the PNG activity. Android 14-lineage DocumentsUI [assigns the filename to a separate preview icon's accessibility description](https://github.com/LineageOS/android_packages_apps_DocumentsUI/blob/lineage-21.0/src/com/android/documentsui/dirlist/GridDocumentHolder.java) and [dispatches its hotspot to preview](https://github.com/LineageOS/android_packages_apps_DocumentsUI/blob/lineage-21.0/src/com/android/documentsui/dirlist/DirectoryFragment.java). The exact node matched in that older failed run was **not** recorded. In the subsequent [harness-only run 41010107410](https://github.com/jyvplay/OSB/actions/runs/41010107410), both picker cases reached the code branch for a matched `preview_icon` and failed closed because no title was exposed under its ancestors; the isolated PNG case passed. No selector JSON was emitted because those failures occurred before selection, and no image frame was captured. This confirms the new candidate matched a preview in that run, not that the old failure's exact node was the same. The next candidate finds a unique `item_root` containing the identified preview and performs one native tap inside the card but outside the preview rectangle, with bounded geometry metadata. Its Android behavior is not yet observed.

The [R22/R23 run 41007831125](https://github.com/jyvplay/OSB/actions/runs/41007831125) passed its memory/format cases but failed one real XLSX UI picker case waiting for DocumentsUI to disappear; its cause is not established. The diagnostic change observes foreground and clickable controls rather than treating an unrelated foreground window as a return. On the harness-only `818f737`, [run 41010107476](https://github.com/jyvplay/OSB/actions/runs/41010107476) passed **27/27** named API 34 cases, including all three R22 XLSX real picker/import/cancel/malformed cases. It does not identify the earlier failure's cause or prove reliable behavior on other runs.

The previously running [full API 34 native push run 41007831201](https://github.com/jyvplay/OSB/actions/runs/41007831201) on `1855617` finished **145 named passes / 11 named failures**. Its historical old-source child-focus counterexample was **reproduced**, and the candidate `ShellSmokeTest.activatingWindowKeepsChildFocus` **passed** on that revision. The suite nevertheless failed: one subtitle UI, the Writer system-picker entry, two 200%-font/IME cases, three Find/Notes input cases, one video decoder case, two PNG picker cases and one asleep-AVD Find touch. The Writer assertion failed before DocumentsUI was observed; the dedicated Office run passed the same end-to-end flow. A changed test will record bounded foreground/clickable/export-state diagnostics if this happens again; that diagnostic is not a fix. This discrepancy and the other full-suite failures remain real, not explained by the focus pass. The harness-only revision `818f737` was pushed only after that useful older suite completed; its new device results are separate. Its [full native push run 41010107436](https://github.com/jyvplay/OSB/actions/runs/41010107436) was still running when the subsequent candidate was prepared; the known-failing PNG harness is superseded, and a later push may cancel that run. No full-suite outcome is inferred for it. A dedicated [UI readiness run 41010107434](https://github.com/jyvplay/OSB/actions/runs/41010107434) then passed **6/6** API 34 cases, including the asleep-device Find, three input cases and two 200%-font/IME cases that had failed in the full suite. Different suite outcomes do not establish why the full run failed.

## D. Negative-space catalogue

1. A filename in an accessibility description may describe a **preview**, not the select action.
2. A system picker disappearing can mean an external viewer opened, not that the app got a URI.
3. A visible app control alone is not a complete provider result/readback.
4. A `READY` state is not proof the source bytes remained unchanged.
5. A successful readback is not provider atomicity or physical durability.
6. A disposable MediaStore callback injection is not a real system-picker test.
7. One passing device run does not erase a failing run of the same revision.
8. A visible toolbar screenshot does not show controls below the fold.
9. A screenshot or successful artifact transport is not a JUnit pass.
10. A parser-only Kotlin gate is not Android type checking or execution.
11. A rounded display string is not a correct typed numeric formula result.
12. Binary multiplication by ten can turn a decimal tie into a lower binary approximation.
13. Silently clamping requested digits can yield plausible but wrong results.
14. Naively building `10^digits` for arbitrary input can overflow or consume unbounded memory.
15. Returning zero for every large negative scale is wrong near finite binary64 overflow boundaries unless justified.
16. A decimal oracle is not proof of Excel's handling of a prior binary calculation.
17. openpyxl reading a cached value is not independent formula evaluation.
18. A reference-engine agreement does not prove either engine is Excel.
19. A copied/retried output is not a complete saved document unless actual bytes are checked.
20. A 50/50 bounded subset census is not 50/50 full-feature acceptance.

## E. Mechanism portfolio and falsification

| Mechanism / artifact | Proved portion | Exact gap and cheapest falsification | Gap kind |
|---|---|---|---|
| Real picker + provider readback: `R54OfficeDeviceTest.kt` | Writer flow passed 11/11 on a PR run | Other providers and the push-run menu failure: rerun on the changed revision without retries | Local device boundary |
| Foreground verification: `R25PngCopyDeviceTest.kt` | Previous run reached Photos instead of the app | Exact matched node was not recorded; publish fixture-only node metadata from a new run | Local diagnosis |
| Node-identity and geometry selection: same test | Actual API 34 run matched `preview_icon`; the previous title lookup failed closed | New unique-card/body-tap candidate, Android type-check and real selection/readback pending; unfamiliar picker trees fail closed | Local diagnosis |
| Picker-observation validation: `report_r25_picker_selection.py` | Host adversarial checks constrain origin, fixture, strategy and card/preview/tap geometry | Reported metadata does not prove app success; inspect separate JUnit results | Local reporting boundary |
| Decimal rounding: `FormulaEval.kt` | Pre-fix counterexamples repaired in host contracts, with explicit finite bounds | Android execution and broad Excel compatibility are still pending; compare ties and ±large scales | Core correctness |
| Independent formula/reference corpus: host-only formualizer and Python decimal | 5,614 exact typed matches against each, plus 28 edge matches | Neither is Microsoft Excel; formualizer emitted infinity for four edges while the candidate returns `#NUM!`/its `IFERROR` fallback or a finite unchanged value | Equivalent compatibility gap remains |
| Structured document verification: `python-docx`/ZIP/XML | R54 plain DOCX reader checks executed | No rich-editor/fidelity acceptance; add an explicitly scoped feature and independent structural checks | Core product gap |
| Actual API 34 + measured native frames | R55 captured finite genuine app windows | Other sizes, keyboard, OEMs and accessibility need distinct tests | Global acceptance gap |

## F. Artifacts and reproducibility

R55 artifacts and results are linked above. The test-harness candidates are `R25PngCopyDeviceTest.kt`, `R22XlsxUiDeviceTest.kt`, `R54OfficeDeviceTest.kt`, `report_r25_picker_selection.py` and its unit tests. The rounding repair is in `FormulaEval.kt` and `OfficeSheetDraft.kt`; reproducible actual-output artifacts come from `R56Checks.kt`, `run_r56_contracts.py`, `check_r56_reference.py` and `test_r56_reference.py`, and Gradle task `:testing:r56Contracts`. The Office Android case now additionally applies `=ROUND(1.005,2)` through the real editor and requests an actual frame; no new-device pass is implied by writing that assertion. Selector records are generated only for disposable fixture names; absence is reported as absence, never success. The old result and new candidate must not be conflated. Host test reproduction, from `android/`:

```sh
PYTHONPATH=tools python3 -m unittest test_r25_picker_selection test_r56_reference
python3 tools/run_r56_contracts.py
# Requires separately installed, host-only formualizer==0.13.0 and openpyxl==3.1.5:
python3 tools/check_r56_reference.py --require-reference
python3 tools/check_project.py
python3 tools/native_source_audit.py --check
python3 tools/module_testability.py --check
python3 tools/check_r26_android_syntax.py
```

## G. Candidate-specific attacks

For the picker reporter, malformed origin/schema, a wrong target, a nonfixture filename, symlink, over-budget input and a tap inside the preview or outside its card are rejected by host tests; these do not simulate or replace Android UI execution. The previous title-only attempt failed closed when its title was absent. The new candidate also fails closed if it cannot identify one card containing the matched preview. Rounding attacks include positive and negative ties, fractional scale, ±12/13, near-zero/subnormal values, large finite inputs, overflow, dependencies, recovery, immutable old drafts and new XLSX caches. Six comparator attacks reject malformed/missing/duplicated observations, mutated values, boolean coercion, NaN/infinity and a zero standing in for a subnormal. Existing spill, older spreadsheet and Writer regressions must still be rerun after the shared-engine repair.

## H. Executed verification

Two 360×800 application-surface frames from the successful `1855617` PR run were hash-checked and visually inspected. The [sheet frame](verification/r56-native/sheet-1855617-api34.png) shows the wrapped Export, Formula and Apply controls and B1 = 8, with only part of the grid header at the bottom edge; SHA-256 `41f841a7e8f8cf296f71f54bcb2320906639a2816a75f4f4096829f99c42dc8a`. The Writer frame is byte-identical to the [R55 Writer image](verification/r55-native/writer-api34.png), SHA-256 `1024212945c6e21b10b099dad1a41e931ac2d452a5d9a763ecf718ac67d256bf`; its export control and entered text are visible. Both were downsampled from 1080×2400, density 2.625 and font scale 1; their declared target bounds were at least 48 dp high. They do not establish other sizes, all states, subjective quality or accessibility acceptance.

The prior-run outcomes and four actual pre-fix rounding observations are recorded in C. The repaired evaluator and bounded authoring path passed **8,066 actual JVM assertions** and emitted **5,614 deterministic observed formulas**. Independent Python `decimal` and host-only `formualizer==0.13.0` each matched all 5,614 exact typed binary64 results; formualizer also matched **28 edge cases** and returned infinity for four edges: two overflow/`IFERROR` cases where the candidate instead uses `#NUM!` and its fallback, and two very-large-positive-scale cases where the candidate returns the unchanged largest finite double. This explicit policy difference is not an Excel execution. XML and `openpyxl` verified **12** XLSX formula/cache checks, not independent recalculation. **Six** adversarial comparator unit tests passed.

After the shared evaluator repair, the affected historical host suites were rerun: R45 **508** assertions plus independent checks; R48 **3,861/56**; R50 **8,555/146**; R53 **25,425/411**; R54 **662/21**; R55 **1,054/934** and the 913-formula comparison with the same three documented reference disagreements and one refusal. These do not imply Android acceptance. After these edits, **1,546 Python tests** passed, including seven adversarial selector-report tests and six rounding comparator tests; project, source ownership and census checks passed. The Kotlin frontend parsed **66** Android source/test files without syntax errors; unresolved Android dependencies were expected and this is not Android compilation. The changed Android tests have not yet run. No independent expert, physical device, actual Excel, Studio GUI, or whole-application acceptance is claimed.

## I. Repair and re-verification

A repaired candidate inherits no pass: run the relevant source/host regressions, independent references and real Android cases on the candidate revision; retain failures and update this section with exact run IDs and bounds. A test harness repair must not be presented as a production picker fix.

## J. Remaining gaps

Complete the real PNG and XLSX picker investigations, the flaky spreadsheet menu, the historical focus/candidate comparison and Android verification of the rounding repair. Full rich Writer, comprehensive spreadsheet fidelity, safe arbitrary Office imports, provider adversaries, physical devices, accessibility, security, performance and release gates remain open. Keep a PR available through verification and honor the latest explicit merge request only after reviewing actual outcomes.
