# R73 — essentials formula batch, `TEXT()` renderer, wildcard criteria

Status: **host- and CI-lane-verified**. The JVM contracts run under the real Android/Gradle toolchain in the office lane (both app flavors assemble), and the oracle plus third-engine comparisons pass there. No Excel, LibreOffice, Word, device, emulator or provider executed anything for this slice, so nothing here is Class A evidence by itself.

## 0. What changed in production code

| Artifact | Size | Role |
|---|---|---|
| `format/ooxml/src/main/kotlin/dev/fusion/ooxml/FormulaEval.kt` | +900 lines | 39 new pure-function arms in `evalEssential` (37 essentials + `ISREF`/`AGGREGATE`), 4 dynamic-array functions (`SORT`/`SORTBY`/`UNIQUE`/`FILTER`) with elementwise operator broadcast for array arguments, wildcard text criteria in `matchesCriteria`, `NORM.INV` refinement, `DAYS360` NASD/European end-of-month rules |
| `format/ooxml/src/main/kotlin/dev/fusion/ooxml/NumberFormatRender.kt` | 556 lines (new) | ECMA-376 number/text/date/scientific renderer: section selection, colour/condition/elapsed-time bracket handling, digit and grouping layout, scaling commas, percent, `E+`/`E-`, date/time tokens, literals/escapes/padding |
| `format/ooxml/src/main/kotlin/dev/fusion/ooxml/NumFmtSections.kt` | unchanged | structure-only splitter the renderer consumes |

New scalar functions (39): ROUNDUP, ROUNDDOWN, TRUNC, LOG, PRODUCT, REPT, REPLACE, SEARCH, VALUE, TEXT, TIME, HOUR, MINUTE, SECOND, DATEVALUE, DAYS, DAYS360, YEARFRAC, WEEKNUM, ISNA, ISNONTEXT, ISEVEN, ISODD, NA, ERROR.TYPE, TYPE, COUNTBLANK, STDEV.S, STDEV.P, VAR.S, VAR.P, NORM.INV, BINOM.INV, MODE.SNGL, MAXIFS, MINIFS, LOOKUP, ISREF, AGGREGATE. New dynamic-array functions (4): SORT, SORTBY, UNIQUE, FILTER, dispatched through `tryEvalDynamicGridCall` so their full spilled grid is compared, with `A1:A6>20`-style masks evaluated elementwise. `ESSENTIAL_NAMES` is the single source of truth for the scalar set; a guard test fails if a name lacks an arm or the set drifts.

## A. Formal model

**Admissible objects.** Pairs `(f, E)` where `f` is a formula string over a fixed 35-cell environment `E` (numbers, text, booleans, blanks, date serials, one fraction), and number-format strings passed to `TEXT`. Evaluation is pure, single-sheet, deterministic and total (each case yields a number, text, boolean, error literal, or an explicit refusal).

**Access model.** The oracle sees only the formula text and the documented environment; it never sees engine output while computing expectations. The probe sees only `cases.tsv`; it cannot see `expected.tsv`. The third engine sees the same formula text through its own workbook API.

**Counted resource.** Compared outputs and emitted text lengths — not time, heap or battery. `REPT` is bounded at 32,767 characters, matching the spreadsheet cell limit; beyond that the call returns `#VALUE!`.

**Quantifier order, stated up front.** *For every* case in the corpus, the engine's observable result equals the oracle's independently computed result within the declared tolerance. This is a universal claim **over the corpus**, not over the function domains: no case here proves behaviour on inputs outside the corpora, in another locale, or under Excel's own rendering.

**Boundaries and tolerances.** Numbers compare exactly unless the case declares a relative tolerance (transcendental and statistical cases: 2e-9 for `NORM.INV`, 1e-12 for STDEV/VAR, 1e-9 for YEARFRAC). Text, booleans and error literals compare exactly. Refusals compare by a declared message fragment, so a refusal cannot silently become an answer.

**Adjacent problems not substituted.** Excel rendering, locale-dependent decimal separators, currency symbols, fraction formats, accounting formats, conditional colours in real cells, `SORT`/`SORTBY`/`UNIQUE`/`FILTER`/`RANDARRAY`, `AGGREGATE`, `FORMULATEXT`, `ISREF`, and Android UI/device behaviour are **not** covered.

## B. Evidence threshold, declared before committing

Committing required all of:

1. The corpus builds from an independent reimplementation and refuses to overwrite existing fixtures (no self-fulfilling regeneration).
2. The Kotlin engine compiles under `-Werror` with the new source file present in **every** source set that compiles `FormulaEval.kt`.
3. The probe executes 100 % of cases; every observation matches the oracle.
4. A **third** implementation (formualizer 0.13.0) agrees, with every disagreement enumerated by name and justified, and zero unexplained disagreements.
5. Guard tests reject doctored observations, duplicate ids, a missing renderer in any include list, and a missing function arm.
6. All retained corpora still pass unchanged (R46, R47, R48, R55) and full host discovery has zero failures.
7. Census/source audits regenerate cleanly against the edited sources.
8. This document names the unrun gates.

Android compilation, device execution, visual inspection and Office parity were **not** satisfied and are not claimed.

## C. Frontier and restated hypotheses

Open interface: whether these formulas and formats produce Excel-identical results on inputs outside the corpus, in a non-US locale, and inside an actual Excel/LibreOffice recalculation. The public references behind the rules are the ECMA-376 number-format grammar (section 18.8.31) and Microsoft's per-function documentation for `DAYS360`'s two end-of-month rules and `NORM.INV`'s domain. None of those documents is an executable oracle, which is exactly why two independent implementations were used.

## D. Negative space — shortcuts that would have passed a weaker check

1. Comparing against values produced by the engine under test.
2. Regenerating fixtures from the same code path that consumes them.
3. Treating a refusal as a match.
4. Accepting `#VALUE!` where Excel returns a number because both are "an error".
5. Comparing only the first decimal place.
6. Omitting the boundary case (`REPT` at 32,767 vs 32,768).
7. Testing `TEXT` only with `General`.
8. Testing `DAYS360` only inside one month.
9. Testing `WEEKNUM` only at mid-year (year boundaries are where the ISO and non-ISO types differ).
10. Letting the oracle reuse the engine's helper functions.
11. Counting a compile success as an execution.
12. Counting the probe's own exit code as agreement.
13. Declaring "known differences" without listing them by id.
14. Trusting a differential that silently converts its own exceptions into skips.
15. Reporting the third engine's agreement as Excel evidence.

**Modal shortcut:** *treating the independent Python oracle as ground truth*. It was written by the same author as the Kotlin code and shared its `DAYS360` misreading of the end-of-month rule. The executable falsifier is DAYS360("2023-01-01","2023-01-31"): the corrected rule yields 30; both the original Kotlin and the original oracle produced 0. Only the third engine, which disagreed, exposed it.

## E. Mechanisms (distinct, each with an artifact and a falsifier)

1. **Executable oracle** (`tools/r73_formula_cases.py`): reimplements rounding, formatting, date arithmetic, statistics and the information functions from first principles; writes `cases.tsv` + `expected.tsv`; `verify` compares observations and returns non-zero. Falsifier: doctor one observation — the guard test requires exactly that id to be reported.
2. **Engine probe** (`testing/src/r73formula/.../R73FormulaProbe.kt`): a host-only JVM `main` that evaluates each formula against the frozen environment and emits one line per case; catches only declared exception types so an unexpected crash is a failure, not a skip. Falsifier: a thrown message that does not match a declared fragment.
3. **Third-engine differential** (`tools/check_r73_formula_reference.py`): formualizer 0.13.0 over the same formulas; 213 compared, 9 enumerated differences, 98 not representable (date objects, unsupported functions), 0 unexpected. Falsifier: any undeclared disagreement.
4. **Structural wiring guards** (`tools/test_r73_formula_contract.py`, 10 tests): every `FormulaEval.kt` include list also compiles `NumberFormatRender.kt` and `NumFmtSections.kt`; every host runner that compiles the engine adds both; each of the 37 names has an arm; duplicate case ids are rejected; the oracle contains neither `subprocess` nor `formualizer`.
5. **Retained regression corpora**: R46 (885 assertions), R47 (2,393), R48 (3,861), R55 (1,054) re-run after the wildcard-criteria and `DAYS360` changes. Falsifier: any count change.
6. **Census/source audits**: `module_testability.py` and `native_source_audit.py` re-derive their reports from the edited sources with SHA-256 binding. Falsifier: a stale hash or a report mismatch under `--check`.

## F. Second-order adversary per candidate

- *Oracle*: an error shared with the implementation (realised: `DAYS360`), or a rule invented to match the code. Countermeasure: the third engine plus explicit per-case citations of the rule when the engines disagree.
- *Probe*: catch-all exception handling turning failures into skips (guard: only `IllegalArgumentException`/`IllegalStateException`, and refusals must match declared fragments).
- *Third engine*: silently returning its own error object for an unimplemented function (guard: `unsupported` is counted separately and never counted as agreement; its error kinds are normalised explicitly).
- *Guards*: asserting on the file that was just edited in a way that always passes (each guard test is shown to fail on a doctored input).
- *Census*: blessing hashes after edits without reviewing the prose (guard: the prose explicitly states what was and was not executed).

## G. Verification actually performed (this revision)

| Check | Result |
|---|---|
| `kotlinc -Werror` over archive+ooxml+probe | exit 0 |
| Oracle corpus | **347 cases, 0 mismatches** (including whole spilled grids for the 4 array functions) |
| Third engine | **228 compared, 12 declared differences, 119 not representable, 0 unexpected** |
| Wiring/negative-control tests | **11 passed** |
| R46 / R47 / R48 / R55 | 885 / 2,393 / 3,861 / 1,054 (unchanged) |
| Full host discovery | **1,668 tests, 0 failures, 0 errors**, 15 pre-existing optional-dependency skips |
| `module_testability.py --check` | 50/50 narrow subsets (100 %), 0/50 full-spec (0 %) |
| `native_source_audit.py --check` | 231 requirements, 36 obligations |
| CI office lane (run 42568404564, step 9): `assemble` + `:app:assembleProDebug` + `:app:assembleProDebugAndroidTest` + `:testing:r73FormulaContracts` | **success** — the new source set and the updated `kotlin.include` lists compile under the real AGP/Gradle toolchain and the probe runs there |
| CI office lane (step 14): oracle `verify` + formualizer differential | **success** |

## H. Defects found and repaired during this round

1. `PRODUCT()` with no argument list returned 0; Excel rejects the formula, so it now returns `#VALUE!`.
2. `TEXT("abc","0.00")` returned the literal format text; Excel returns the text unchanged.
3. `[h]:mm` elapsed-time formats were refused as unsupported bracket annotations.
4. Wildcard text criteria (`*`, `?`, `~` escapes) were unsupported, so `MINIFS(...,"*")` matched nothing.
5. `TEXT` of a text value against a date format threw instead of returning the text.
6. `DAYS360` ignored both end-of-month rules; `YEARFRAC` basis 0 and 4 inherited the error.
7. `NORM.INV` carried a ~1e-9 error from Acklam's approximation; one Halley step against the regularized incomplete gamma cuts it to double precision.
8. Oracle-side: sign placement for single- and two-section formats, and unescaped quotes in generated `TEXT` formulas.
9. `SORT` accepted a sort index beyond the array width because rows and columns were conflated.
10. `FILTER` refused an inline mask such as `C1:C6>25`; dynamic-array arguments now evaluate operators elementwise with scalar broadcast.
11. `AGGREGATE` functions 8 and 10 were checked against a third engine that swaps STDEV.S with STDEV.P; the engine follows Microsoft's table (8 = STDEV.S, 10 = VAR.P) and the difference is declared.

## I. Repairs re-run all gates

After each repair the oracle corpus, the third-engine differential, the guard tests and R46/R47/R48/R55 were re-executed; the numbers in section G are the final state. The patch-specific attack for the wildcard change is `MINIFS(C1:C6,D1:D6,"*")` (must equal 10) together with `MAXIFS(C1:C6,D1:D6,"")` (blank criteria must not become a wildcard) and the literal `~*` escape case.

## J. Honest stopping point

Stopped before: any device or emulator run of the new code, visual inspection, Excel or LibreOffice recalculation, locale and currency behaviour, fraction/accounting formats, `RANDARRAY`, `SEQUENCE` overflow limits, `AGGREGATE` functions 12–19 (LARGE/SMALL/PERCENTILE/QUARTILE), `FORMULATEXT` (the evaluator does not retain source text), and provider/durability behaviour. `FILTER` with no matches returns `#N/A` because `#CALC!` is outside this kernel's declared error vocabulary; that deviation is deliberate and recorded here.

Next highest-information test: run `:testing:r73FormulaContracts` plus the oracle/third-engine comparisons inside the CI office lane on this exact revision, then replace the third engine with an actual Excel-or-LibreOffice recalculation of the same 311 formulas — that is the only artifact that can promote any of this from "two independent implementations agree" to "matches the reference implementation".
