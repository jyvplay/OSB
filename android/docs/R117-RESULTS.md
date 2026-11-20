# R117 — the percentile variants and the `A` aggregates the engine could not name

`FormulaEval.kt` could evaluate `PERCENTILE` and `QUARTILE` — the inclusive method, `pos = k·(n−1)` — and could not
evaluate `PERCENTILE.EXC`, the exclusive method Excel documents as the default for new workbooks. It could evaluate
`AVERAGE`, `MIN`, `MAX`, `VAR.S`, `VAR.P`, `STDEV.S`, `STDEV.P` — and could not evaluate any of their `A`-suffixed
siblings, the family whose entire reason to exist is that it *counts* text and logicals where the others ignore them.

A measured check across every Kotlin source in the repository (`grep` for `"PERCENTILE.INC"`, `"PERCENTILE.EXC"`,
`"QUARTILE.INC"`, `"QUARTILE.EXC"`, `"AVERAGEA"`, `"MAXA"`, `"MINA"`, `"VARA"`, `"VARPA"`, `"STDEVA"`, `"STDEVPA"` over
all `*.kt` outside `build/`) returned **zero** occurrences for all eleven names, so no other dispatch site covered
them. `PERCENTRANK.INC`/`PERCENTRANK.EXC` and `MODE.SNGL` did exist, which is how the gap was found: the percentile
family was half-present.

R117 adds the thirteen names: **+49/−1 lines in `FormulaEval.kt`** (four names on the existing percentile dispatch
branch, one new helper `evalAggregateA`) and **+1 line in `SpillSheetEngine.kt`** (the four dotted names in the OOXML
export prefix list). No build file, no Android source, no existing function is modified. The legacy inclusive kernel,
`MODE.SNGL` and the `STDEV`/`VAR` kernels are pinned byte-exact by rule R117-7, because they are the kernels this
round extends, not its subject.

## A. Formal model

**Admissible objects.** Formula cells evaluated by two entry points of the same engine: `evaluateFormulaAsGrid(text, env)`
(a single expression) and `evaluateSheet(Map<String, CellInput>)` (the dependency DAG the sheet editor uses). The
first argument of every function in this round is a *range* (or a list of scalar arguments); its elements arrive as
`CellValue` — `Num`, `Text`, `Bool`, `Empty` or `Err` — each tagged by whether it came from a range reference.

**Information/access model.** The evaluator sees only the cell values in `env` and the parsed AST. It has no clock, no
locale, no host function table beyond `kotlin.math` and the standard library.

**Resource counted.** Nothing is bounded by this round: these are total functions on finite lists with declared
refusals. The counted quantities are *correctness* quantities — the rank formula, the counting rules, the returned
error literal, and the numeric difference from an independent implementation.

**Success and failure, quantifier order.** For the numeric cases: **for every** probe `(f, data, k)` in the corpus,
the value `E(f(data, k))` the engine returns satisfies `|E − R| ≤ 1e-12 · max(1, |R|)`, where `R` is the documented
definition recomputed independently. For the refusals: **for every** declared out-of-domain rank, the engine returns
exactly the declared literal (`#NUM!`, `#DIV/0!`, `#VALUE!`) and never an extrapolated number; **for every** error
element in the data, the error is propagated rather than skipped. The exclusive rank's refusal is a stronger claim
than "approximately the minimum": the engine must not extrapolate below the first value, which `statistics.quantiles`
*does* — that divergence is the point of the refusal rows.

**Parameters, boundary conditions, units, tolerances.** Ranks are unitless fractions in `[0, 1]` (percentile) or
integers in `0..4` (quartile). Inclusive rank `pos = k·(n−1)`; exclusive rank `pos = k·(n+1)−1`, refused when `pos`
falls outside `[0, n−1]`. A single element under the inclusive method returns that element; under the exclusive
method only `k = 0.5` lands on it. Tolerance `1e-12` relative with an absolute floor of `1e-12`.

**Adjacent problems that must not be substituted.** Excel's *display* of these values (number formats), Excel's own
last-ULP results, `PERCENTRANK` (already present, different function), `RANK`/`RANK.EQ`/`RANK.AVG` (already present),
`MODE.MULT` (array-returning, a different contract), `TRIMMEAN`/`MEDIAN`/`LARGE`/`SMALL` (already present), the
calculator UI's own keypad, and every Class A question about rendering these results in a real workbook.

## B. Outcome space

* **H+** — the thirteen names become reachable, keep the documented rank formulas and counting rules, and agree with
  an independent implementation on 776 probes. **This is what happened**, modulo the CI evidence limits in §H.
* **H−** — impossible to *pass* this contract by adding names alone: a name that dispatches but is not implemented,
  or is implemented with the inclusive rank where the exclusive belongs, fails R117-1 or the corpus. There is no
  shortcut that satisfies it.
* **H∂** — the boundary cases are exactly the disagreement surface between a spreadsheet and a statistics library:
  the exclusive rank at the endpoints (Excel refuses, Python extrapolates); a single element under the exclusive
  method (only `k = 0.5` is in range); numeric-looking text under the `A` family (counted as 0, not parsed as a
  number); and sample-versus-population variance on one value (`#DIV/0!` vs `0`).

Commit threshold declared before pushing: (1) product source compiles; (2) the module's own JUnit test executes and
passes in CI; (3) an independent oracle recomputes the corpus; (4) the gate's controls each fire their named rule;
(5) the full Python discovery lane stays green; (6) the census re-pins only this round's files.

## C. Frontier

The governing documents are ISO/IEC 29500-1 (ECMA-376 18.17.7, the percentile rank) and the function reference it
inherits (the `A` family's counting rules, the exclusive rank's refusal). **This sandbox cannot fetch them**:
outbound access is limited to `github.com`, `codeload.github.com`, `api.github.com`, `registry.npmjs.org`,
`pypi.org`, `files.pythonhosted.org`, so no Microsoft or ISO page was retrieved this turn. What *was* retrieved is
the XlsxWriter 3.3.3 source (from `pypi.org`), whose `xlfn_functions` table confirms which names Excel stores with
the `_xlfn.` prefix: `PERCENTILE.INC`, `PERCENTILE.EXC`, `QUARTILE.INC`, `QUARTILE.EXC`, `MODE.SNGL`, `MODE.MULT` are
prefixed; `AVERAGEA`, `MAXA`, `MINA`, `VARA`, `VARPA`, `STDEVA`, `STDEVPA` are not in the table at all (stored bare).
The semantics implemented are therefore stated as *the standard's documented behaviour as understood*, and the
evidence for them is not a citation but the corpus plus the refusals the test asserts. That is a weaker foundation
than a fetched quote, and it is labelled as such rather than dressed up.

The open interface: whether these values are what **Excel** produces bit-for-bit. Nobody here can answer that; the
corpus answers a different and checkable question — whether two independent implementations of the documented
definitions agree.

## D. Negative space (≥15 shapes that look like a solution and are not)

1. Adding the names to a dispatch branch without an implementation — they would fall through to `#NAME?`. R117-1
   requires one branch per name.
2. Adding a name to the helper but not to the dispatch: the helper is unreachable, all corpus rows return `#NAME?` —
   caught by R117-1 *and* by every corpus row.
3. Implementing `PERCENTILE.EXC` with the inclusive rank `pos = k·(n−1)`: every legacy test passes, and the corpus
   rows at `k = 0.25` on four values differ by 0.5 (1.75 vs 1.25). The explicit rank test fails.
4. Extrapolating the exclusive rank below the first value (what `statistics.quantiles` does): the refusal rows fail.
5. Reusing `collectNumericAggregate` for the `A` family: it *ignores* text and logicals in ranges, which is exactly
   the behaviour the `A` family exists to differ from. `AVERAGEA(1,"x",3)` would return 2.0 instead of 4/3.
6. Parsing numeric-looking text as a number in the `A` family: `AVERAGEA("5")` would return 5 instead of 0.
7. Coercing an error element to 0 or skipping it: the propagation rows fail.
8. Returning 0 for `VARA` on one value instead of `#DIV/0!`: the refusal test fails.
9. Generating the corpus from the Kotlin implementation, or from a transcription of it: this is the modal shortcut.
   It makes the fixture a *transcript*, so a wrong implementation passes trivially. It fails because the expected
   column is recomputed in `tools/test_r117_percentile.py` through `statistics.quantiles` (a different
   implementation) and through a separate transcription of the `A` counting rules.
10. Loosening `TOL` until a wrong result fits: R117-6 refuses a tolerance above `1e-12`.
11. Editing one corpus value by hand: R117-5 compares the committed file to the generator's render, and R117-4
    re-derives the identities from the corpus itself, so a single altered row breaks at least two rules (proved by a
    control).
12. Deleting corpus rows to remove an inconvenient function: R117-2 requires ≥20 rows per name and ≥5 refusal rows
    for each exclusive variant.
13. Duplicating a row so a function looks better covered: R117-3 deduplicates `(function, data, k)`.
14. Asserting only that the cell is a number (never its value): the corpus test asserts the value against the oracle.
15. Testing only through `evaluateFormulaAsGrid` and calling that "the sheet works": a workbook cell goes through the
    dependency DAG, which is a different path; R117 adds an `evaluateSheet` case that also checks a downstream cell
    consumes the result.
16. Forgetting the OOXML export prefix: the value would be right in the editor and wrong in the saved file, because
    Excel stores the dotted names with `_xlfn.`. R117-1 requires the four names in the prefix list.
17. Passing a Kotlin arity error to CI and calling the local gate "green": this happened in R116 (§I of
    R116-RESULTS.md) and is now rule R117-6's helper-arity check.
18. Treating `kotlinc` absence as "cannot verify": CI compiles and runs the module's tests; the claim is scoped to
    that.
19. Citing a previous revision's CI for this revision: the run ids in §H are this branch's, on this commit.

## E. Mechanism portfolio (six distinct, kept independent until judged)

| # | Mechanism | Artifact | Proved portion | Unresolved interface | Cheapest falsifier | Gap |
|---|---|---|---|---|---|---|
| 1 | Extend the existing percentile branch + one new helper (**chosen**) | +49/−1 lines | Rank formulas, counting rules, refusals, propagation | Excel bit-equality | R117-1 + corpus | local |
| 2 | A new top-level kernel object (`PercentileFunctions.kt`) called from the branch | — | Testable in isolation | A new file to include in 20+ Gradle source sets (the R112-7 include-closure trap), for arithmetic that is one line each | include-closure gate | rejected: risk without value |
| 3 | Implement the `A` family by calling `collectNumericAggregate` and post-adjusting | — | Reuses existing code | The post-adjustment must re-walk the raw values anyway, because the aggregate discards the text/logical distinction the `A` family needs | `AVERAGEA(1,"x",3)` | rejected: the reuse destroys the information |
| 4 | Implement `PERCENTILE.EXC` by delegating to `PERCENTILE.INC` with a transformed rank | — | One kernel | No affine transform of `k` maps `k·(n+1)−1` onto `k·(n−1)` for all `n`; the refusal boundary also differs | corpus at two different `n` | rejected: mathematically impossible |
| 5 | Precomputed value table for common datasets | — | Exact at the listed datasets | A spreadsheet is a calculator, not a lookup table; any other dataset returns nothing | corpus at a non-tabulated dataset | rejected: not correct |
| 6 | UI-level expansion (rewrite `=PERCENTILE.EXC(...)` into an equivalent expression in the editor) | — | Display could look right | Ties correctness to one UI path; the engine, importer and recalc would all disagree | engine-level corpus | rejected: fixes the symptom in the wrong layer |

Mechanism 1 is the smallest change that makes the capability real for every consumer (editor, importer, DAG, spill).

## F. Artifacts

* `format/ooxml/src/main/kotlin/dev/fusion/ooxml/FormulaEval.kt` — +49/−1: four names on the percentile branch,
  `evalAggregateA`, and the exclusive-rank refusal in `evalPercentileOrQuartile`.
* `format/ooxml/src/main/kotlin/dev/fusion/ooxml/SpillSheetEngine.kt` — +1: the four dotted names in the OOXML
  export prefix list.
* `format/ooxml/src/test/resources/r117/percentile_corpus.tsv` — 776 rows + header, 156 of them refusal rows.
* `tools/gen_r117_percentile_corpus.py` — deterministic generator; expected column from the documented definitions.
* `format/ooxml/src/test/kotlin/dev/fusion/ooxml/PercentileFunctionTest.kt` — 15 JUnit cases, executed by the
  existing `:format:ooxml:test` CI step (no workflow edit).
* `tools/check_r117_percentile.py` — 7 rules; `tools/test_r117_percentile.py` — 23 controls.
* `tools/r117_census_deepen.py` — re-pins exactly this round's seven files and refuses foreign drift.

## G. Second-order adversary

For the chosen candidate the strongest attack is not on the maths but on the *evidence*: make the oracle dependent on
the implementation (D.9), make the tolerance hide a defect (D.10), or make the fixture unreproducible (D.11). All
three are now controls. The second strongest is arity/type-level: Kotlin's overload resolution between a formula, one
value and two values is exactly what broke CI in R116. The third is reachability: a name can compile, dispatch and
still be unreachable from a workbook cell — hence the `evaluateSheet` case. The fourth is the save path: a name can
evaluate correctly and still be written to `.xlsx` without the `_xlfn.` prefix Excel expects — hence R117-1's prefix
check, backed by the XlsxWriter table.

What this adversary does **not** touch: a shared bug between the generator's rank formula and `statistics.quantiles`
(they are different implementations, and the agreement was measured at 1.6e-15 over 140 probes before the corpus was
written), and Excel's own last-ULP values (out of scope, stated).

## H. Verification — what actually ran

| Claim | Evidence | Strength |
|---|---|---|
| Product source compiles in the real AGP/Kotlin toolchain | CI `Android native` on this commit: `Traceability, assemble both flavors, JVM tests` — both APKs assembled, JVM unit tests passed | strong (compiler) |
| The module's JUnit test compiles and the test task passes | same job, `Run all JVM unit tests` step, which includes `:format:ooxml:test` | strong for compile+pass; **the per-case JUnit names are not visible to me** (test-report artifact is on `*.blob.core.windows.net`, unreachable) |
| No regression in the contract source sets that compile `FormulaEval.kt` | same job, `Production Kotlin pilot contracts` step success | strong |
| The Python lane and source checks are green in CI | same job, `Check source contracts and generated traceability` success | strong |
| The corpus is an independent oracle | 432 numeric percentile rows recomputed by `statistics.quantiles` in the local control `test_every_percentile_value_is_the_statistics_library_value` (the 34 single-element rows are skipped there because `statistics.quantiles` requires ≥2 points; they are covered by the corpus test in CI); 154 `A`-family rows recomputed by a separate transcription | strong locally, and the same numbers are the ones CI's JUnit test reads |
| Gate + controls | `R117_PERCENTILE_OK names=13 rows=776 error_rows=156 tests=15 tolerance=1e-12`; `Ran 23 tests … OK` | strong locally |
| Full local lane | `Ran 2411 tests … OK (skipped=446)`, `check_project.py` OK, census `50/50 (100.00%)` | strong locally |

**Not verified, and not claimed:** that Excel produces these exact doubles; that the sheet UI renders them (no device,
no frames); that a workbook containing `=PERCENTILE.EXC(A1:A4, 0.25)` round-trips through `.xlsx` with the `_xlfn.`
prefix and a cached value (the export list is pinned, but no file was written and read back here); and that the
histogram of ulp differences is zero — the tolerance is `1e-12`, not exact equality.

## I. Repair: a candidate inherits no trust

No CI failure occurred on this round's first push. Two *local* failures did, and both were in my own evidence
machinery, not the product:

1. The gate's coverage rule demanded ≥20 rows per name; the `A` family had 16 datasets. The gate was right and the
   corpus was thin — eight more mixed-type datasets were added (776 rows total) rather than the threshold lowered.
2. Two controls failed on their first run: one replaced `#NUM!` with `#SPILL!`, which also removed the exclusive
   refusal rows and so correctly fired R117-2 as well as R117-3; the other renamed a helper to a name that still
   contained the checked substring. Both were control bugs. The gate caught a badly-written control, which is the
   gate working as intended.

After both repairs the whole local lane, the census and the CI job were re-run on the repaired commit.

## J. Stopping point and next highest-information test

Stopped at: thirteen names reachable from both evaluator entry points, 776 independent probes, 15 JUnit cases, 7 gate
rules and 23 controls, with CI compiling and passing. Not stopped at "all features A": **Class A remains 0/50
(0.00%)**, `testable` remains 50/50 (100.00%) — those numbers are unchanged by this round and are not promoted by it.

The next highest-information test is a real workbook: type `=PERCENTILE.EXC(A1:A4, 0.25)` and `=AVERAGEA(A1:A4)`
into the sheet editor on a device, save as `.xlsx`, and have an independent reader confirm the cached values and the
`_xlfn.` prefix — which would also tell us whether `FormulaXlsx` writes caches for the new names, the one adjacent
path this round did not touch.
