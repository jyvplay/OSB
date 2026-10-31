# R57 — device continuation and Office depth

**2026-10-31. In progress; no module is being declared Class A.** Continue the unfinished R56 actual-device tasks before claiming their outcomes. The owner asks for a merge of [PR #12](https://github.com/jyvplay/OSB/pull/12) at the end of the turn; a merge is not proof of full-spec acceptance.

## Runtime honesty and evidence thresholds

Available: one coding agent, this repository, Python 3 and a host Kotlin/JDK toolchain when bootstrap succeeds, `gh`/GitHub API, web search, and GitHub-hosted Android API 34 managed emulators. Separately installed host-only `formualizer`, `openpyxl` and `python-docx` are external comparators, not app dependencies. No local Android SDK/emulator, Android Studio Rabbit 1 GUI, physical device, Excel, LibreOffice, independent coding agents, theorem prover, independent expert panel, or privileged all-provider experiment is available. An emulator is Android execution but not physical-hardware acceptance. Compiler-only parsing is not Android type checking.

**Pre-commit threshold for the remaining picker-test repair:** observed actual failed cases and selector/tree evidence, an explicit real-picker/real-readback contract, bounded diagnostics without a direct-result injection, adversarial reporting/geometry tests, affected Python/source/census checks, and Kotlin syntax parsing. Android compilation and real device behavior must be checked after pushing; neither is inferred from a host pass. **For a new production Writer or spreadsheet change:** executable affected-behavior/boundary contracts, independent reader or reference comparison where applicable, old affected regressions, source/census checks, and exact remaining Android/fidelity limits. Do not commit merely because a candidate looks plausible.

The reviewed census remains **50/50 included modules (100%)** with at least one bounded substantial executable subset, versus **0/50 (0%)** full original specifications accepted. The first figure is not module completeness and is not independently expert-audited. Since all 50 already count by this narrow measure, deeper capabilities do not increase its numerator without changing the standard.

## A. Formal problem and non-equivalences

For each executed picker case on a disposable API 34 emulator, a successful case requires a real ACTION_OPEN_DOCUMENT selection, return to the actual app, bounded processing and readback, and unchanged original bytes. The test may navigate the system picker, but may not replace its URI/result with an injected callback. ∀ passing *executed cases* these postconditions must hold; no finite subset proves ∀ OEMs, providers, device sizes, schedules or contents. Tests operate on unique disposable seeded PNG files, not user files. Foreground/selector readiness is bounded in seconds; frame geometry is measured in dp/physical pixels as recorded. Import/export fidelity, durable persistence, semantic visual quality, provider atomicity, cloud sync and security isolation are distinct predicates.

For an optional structured Writer export, any candidate must leave the existing plain export untouched; it must document exactly which textual syntax becomes which WordprocessingML blocks/runs, bound UTF-8 input/output and parser work, refuse invalid XML, and either preserve noninterpreted text or explicitly refuse ambiguous/unsupported syntax rather than silently claim fidelity. A successful DOCX package is not equivalent to arbitrary DOCX import, Word/LibreOffice rendering, rich editing, pagination, collaboration or complete Class A.

## B. Outcome space

- **H+**: specific documented input and picker cases are achievable and independently inspectable under the stated limits.
- **H−**: no proof of impossibility of the full app is offered; finite observed executions cannot establish the universal acceptance claim, and mandatory physical-device/security/fidelity gates remain unrun.
- **H∂**: picker initial roots, accessibility-tree shape, lifecycle timing, `ROUND` binary/decimal representation and unsupported Writer syntax are material boundaries.

## C. Frontier and actual counterexamples

The prior [R56 report](R56-RESULTS.md) contains real Office/PNG/XLSX outcomes and the independent ROUND disagreements. On `67a6a57`, the full [API 34 native run 41011266513](https://github.com/jyvplay/OSB/actions/runs/41011266513) reported **152 named passes / 4 named failures**. Historical child-focus failure was reproduced and the candidate focus case passed; the actual ROUND editor case passed. Two PNG tests failed because DocumentsUI was in **Downloads** rather than the seeded Pictures location, not because selection or readback passed. A subtitle assertion expected an obsolete exact label, and one calculator setup failed deleting an atomic temporary file; causes and repairs must be individually assessed, not automatically labelled flaky.

The dedicated [`67a6a57` PNG run 41011266677](https://github.com/jyvplay/OSB/actions/runs/41011266677) independently failed both picker cases: the matched preview icon was visible, but the accessibility tree had no named `item_root`. Its actual hierarchy, dimensions, and the next candidate are recorded in R56. The new candidate must also handle the full-suite starting-root counterexample. Android's [DocumentsUI change](https://android.googlesource.com/platform/packages/apps/DocumentsUI/+/070e14547db2e03590e295ac25b76cfb9f45fc78%5E!/) explicitly describes falling back to a last-accessed picker stack in certain initial-location cases; the observed Downloads screen is direct evidence for this test, not a universal provider rule. The [Storage Access Framework guide](https://developer.android.com/guide/topics/providers/document-provider) describes separate document-provider roots and real user selection.

For Writer interoperability, the [Open XML run documentation](https://github.com/OfficeDev/office-content/blob/main/en-us/OpenXMLCon/articles/1fbc6d30-bfe4-4b2b-8fd8-0c5a400d1e03.md) distinguishes paragraph, run, run properties and text. This is a format reference, not evidence that an implementation renders correctly in every office suite. The code here must remain original and permissibly licensed; external host tools are not bundled.

## D. Negative space / shortcut attacks

The [R56 catalogue](R56-RESULTS.md#d-negative-space-catalogue) lists 21 distinct misleading outcomes, including picker disappearance versus a URI result, a screenshot versus a test pass, readback versus provider durability, a decimal reference versus Excel, and narrow subset counts versus full acceptance. Additional R57-specific failures: (22) a seeded image exists but is not in the currently remembered picker root; (23) a preview icon's text names the file but clicking it opens a viewer; (24) an accessibility layout copied from one Android branch need not match the emulator tree; (25) a DOCX whose XML parses can silently lose whitespace or formatting; (26) a heading style reference is not proof the referenced style exists; (27) concatenating an unchanged draft and an unrelated formatted copy is not a rich editor; (28) a setup deletion error does not establish an application failure or safe cleanup; (29) an exact old UI label assertion is not equivalent to the intended semantic count; (30) an independently read cached formula is not an independently recalculated result.

## E. Mechanism portfolio

| Mechanism and concrete artifact | Established portion | Gap / cheapest falsification | Gap kind |
|---|---|---|---|
| Actual picker + result/readback, `R25PngCopyDeviceTest` | Isolated decode passed; real picker failures observed | Navigate actual roots and require real byte/readback assertions on a fresh emulator | Local device |
| Accessibility ancestry and bounded tap, same test | Actual `preview_icon` and grid ancestor rectangles observed | Changed ancestry/tap and remembered root need Android execution | Local device |
| Bounded observation reporter, `report_r25_picker_selection.py` | Host rejects fake fixture/geometry and missing observations | Data is not JUnit outcome; compare separate case results | Reporting |
| Semantic UI assertion, `SubtitleUiDeviceTest` | Actual mismatch shows prefix plus added lane count | Must exercise zero and one cue, not just delete the obsolete suffix | Local test |
| Atomic fixture reset, `DisposableEmulatorRule` | Actual failure in `.new` cleanup | Distinguish disappearance race, continuing writer, and actual deletion failure; preserve fail-closed semantics | Shared lifecycle |
| Structured WordprocessingML export, `DocxPackage` / candidate | Existing typed runs/blocks and plain exporter execute | Explicit conversion semantics and independent python-docx/XML inspection required | Local product; full Writer remains |
| Decimal evaluator, `FormulaEval` / `R56Checks` | ROUND bounded repair plus host/reference and two actual 11/11 Office runs | 16–17-digit and overflow disagreement with formualizer; need real Excel oracle | Core compatibility |
| Native frames and held-out suite, R56 evidence | Actual frames and revision-specific API 34 outcomes | Physical devices, IME/AX matrix and user acceptance absent | Global |

## F. Artifacts

Reproduce the prior kernel and comparator from `android/` using `python3 tools/run_r56_contracts.py` and `python3 tools/check_r56_reference.py --require-reference` after installing the specified host-only comparators. Device tests require a disposable API 34 emulator and explicit opt-in, as in [BUILD.md](BUILD.md). Any new R57 source, tests, real screenshots and CI IDs will be added here; generated `testing/build/` files are ignored build artifacts.

## G. Adversary

Attack picker initial roots, title/preview ambiguity, missing root, truncated/multiple matches, touch coordinates inside the hotspot, lost foreground, cancellation, alias, source mutation and empty/malformed derivatives. Attack Writer bounds, malformed XML, unpaired surrogates, blank lines, tabs, formatting delimiter boundaries, unsupported syntax, nonrectangular tables, too many nodes, generated package structure, and cancellation/recovery. A test harness fix does not inherit any prior pass.

## H. Actual verification

R56's genuine host and Android outcomes and inspected frames remain revision-specific; see linked report. On the R57 pre-push workspace, the revised ROUND suite passed **24,451 JVM assertions**; Python decimal matched **5,614/5,614** grid and **16,384/16,384** finite-bit cases. Formualizer's **9,404 exact / 6,979 within relative tolerance / 1 other** finite-bit results and four nonfinite explicit-edge differences remain divergences, not Excel verdicts; openpyxl/XML performed 12 cache/package checks, not recalculation. The picker observation reporter passed **9 adversarial Python tests**, including an invalid navigation route and tap in the preview. Full Python discovery ran **1,514 tests**, project/source/census checks passed, and the Kotlin frontend parsed **68** selected Android files with unresolved Android dependencies expected. No local SDK or `/dev/kvm` was found. The new picker-root route, subtitle assertion and cleanup guard have not yet been Android type-checked or run. No full product, Excel, physical-device or accessibility acceptance is inferred.

## I. Repair / regression

Rerun the relevant unit, independent reference, ownership/census, compile and actual emulator gates after each patch. Add at least one attack aimed specifically at the repair; retain failures rather than retrying them into a pass.

## J. Exact remaining gaps

Finish the PNG picker and full-suite counterexamples, then implement and verify bounded improvements without removing the existing working paths. Arbitrary Office import/fidelity, complete Writer and spreadsheet editing, provider adversaries, physical devices, accessibility, performance/security, release acceptance and other original requirements remain open.
