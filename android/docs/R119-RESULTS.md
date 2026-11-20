# R119 — the legacy statistical aliases and FORECAST the engine could not name

`FormulaEval.kt` could evaluate `VAR.S`, `VAR.P`, `STDEV.S`, `STDEV.P`, `MODE.SNGL`, `COVARIANCE.P`, `COVARIANCE.S`,
`SLOPE` and `INTERCEPT` — and could not evaluate `VAR`, `VARP`, `STDEVP`, `MODE`, `COVARIANCE`, `FORECAST` or
`FORECAST.LINEAR`, the legacy names Excel's own function reference still documents as the primary spellings for
most of them. `STDEV` (legacy sample) existed, which is how the gap was found: the sample-variance family was
asymmetric, with `STDEV` present and `VAR` absent.

A measured check across `FormulaEval.kt` (`grep` for each quoted name) returned **zero** dispatch occurrences for
all seven names, so no other site covered them. Every one of them returned `#NAME?` from a formula.

R119 adds the seven names: **+7 lines of dispatch and one new helper `evalForecast` in `FormulaEval.kt`** (five
aliases delegate to the modern kernels they rename; `FORECAST`/`FORECAST.LINEAR` evaluate the least-squares
prediction) and **+1 line in `SpillSheetEngine.kt`** (`FORECAST.LINEAR` in the OOXML export prefix list). No build
file, no Android source, no existing function is modified. The percentile, `MODE.SNGL`, `STDEV`/`VAR` and
`A`-family kernels are pinned byte-exact by rule R119-7, because they are the kernels this round extends, not
its subject. The delegation shape was chosen *because* of that pin: extending the existing branch lines would
have changed the pinned text, so the aliases are new branches that call the kernels by their modern names.

## A. Formal model

**Admissible objects.** Formula cells evaluated by two entry points of the same engine: `evaluateFormulaAsGrid(text, env)`
(a single expression) and `evaluateSheet(Map<String, CellInput>)` (the dependency DAG the sheet editor uses). The
arguments arrive as `CellValue` — `Num`, `Text`, `Bool`, `Empty` or `Err` — each tagged by whether it came from a
range reference, which decides whether text and logicals are coerced or ignored.

**Information/access model.** The evaluator sees only the cell values in `env` and the parsed AST. It has no clock, no
locale, no host function table beyond `kotlin.math` and the standard library.

**Resource counted.** Nothing is bounded by this round: these are total functions on finite lists with declared
refusals. The counted quantities are *correctness* quantities — the sample/population divisor, the pairing rules,
the returned error literal, and the numeric difference from an independent implementation.

**Success and failure, quantifier order.** For the numeric cases: **for every** probe in the corpus, the value
`E` the engine returns satisfies `|E − R| ≤ 1e-12 · max(1, |R|)`, where `R` is the documented definition recomputed
independently. For the refusals: **for every** declared out-of-domain input, the engine returns exactly the declared
literal (`#DIV/0!`, `#N/A`, `#VALUE!`) and never a number; **for every** error element in a paired array, the error
is propagated rather than skipped. The alias identities are exact: **for every** input, `VAR` returns what `VAR.S`
returns, because it *is* `VAR.S` — the delegation makes the identity structural, not approximate.

**Parameters, boundary conditions, units, tolerances.** Variances are in squared units of the data; standard
deviations in data units; covariance in product units; `FORECAST` in units of `known_y`. Sample divisors are `n−1`,
population divisors `n`. `FORECAST(x, known_y, known_x)` requires exactly three arguments, equal-length arrays, at
least two numeric pairs, and a non-constant `known_x`. Tolerance `1e-12` relative with an absolute floor of `1e-12`.

**Adjacent problems that must not be substituted.** Excel's *display* of these values (number formats), Excel's own
last-ULP results, `MODE.MULT` (array-returning, a different contract), `FORECAST.ETS*` (time-series, different
function), `T.DIST`/`T.INV`/`CHISQ.INV`/`F.DIST`/`F.INV`/`BETA.INV`/`GAMMA.INV`/`BESSELK`/`BESSELY` (still absent,
named in §J), the calculator UI's own keypad, and every Class A question about rendering these results in a real
workbook.

## B. Outcome space

* **H+** — the seven names become reachable, keep the documented divisors and pairing rules, and agree with an
  independent implementation on 212 probes. **This is what happened**, modulo the CI evidence limits in §H.
* **H−** — impossible to *pass* this contract by adding names alone: a name that dispatches but is not implemented,
  or an alias delegating to the wrong kernel (sample for population — the two differ only in the divisor, so a
  copy-paste passes every legacy test), fails R119-1 or the corpus. There is no shortcut that satisfies it.
* **H∂** — the boundary cases are exactly the disagreement surface between a spreadsheet and a statistics library:
  `MODE` on data with no repeat (Excel `#N/A`, a statistics library returns the first value); `FORECAST` on a
  constant `known_x` (Excel `#DIV/0!`, a statistics library raises); a single numeric pair (Excel `#DIV/0!`).
  The corpus encodes the spreadsheet in all three.

## C. Frontier

The frontier here is not a theorem but a naming history: Excel renamed `VAR`→`VAR.S`, `VARP`→`VAR.P`,
`STDEVP`→`STDEV.P`, `MODE`→`MODE.SNGL`, `COVARIANCE`→`COVARIANCE.P`, `FORECAST`→`FORECAST.LINEAR` when it
introduced the explicit sample/population split, and kept the legacy names as compatibility aliases. The imported
result is the alias table itself: each legacy name is *defined* as its modern counterpart, so delegation is not an
approximation of the semantics — it is the semantics. The exact open interface is the save path: `FORECAST.LINEAR`
is stored in OOXML with the `_xlfn.` prefix (XlsxWriter 3.3.3 `FUTURE_FUNCTIONS` table, verified this round by
downloading the wheel from `pypi.org` and reading `xlsxwriter/worksheet.py`), while the six legacy names are
stored bare. The gate pins both sides of that interface.

## D. Negative space (≥15 shapes that look like a solution and are not)

1. Dispatching `VAR` to the `VAR.P` kernel (sample/population swap; differs only in the divisor).
2. Implementing `FORECAST` as `SLOPE` alone, forgetting the intercept.
3. Implementing `FORECAST` as `INTERCEPT` alone.
4. Swapping `known_y` and `known_x` in `FORECAST` (the argument order is `x, known_y, known_x`).
5. Returning `0` for `VAR` on a single value instead of `#DIV/0!`.
6. Returning the first value for `MODE` on no-repeat data instead of `#N/A`.
7. Using the sample divisor `n−1` for `COVARIANCE` (it is population, `n`).
8. Generating the corpus from the Kotlin kernels (a transcript, not an oracle).
9. Hand-editing the corpus after generation (the fixture drifts from the definitions).
10. Loosening `TOL` to hide a divisor error (the divisor error is ~`1/n`, far above `1e-12` on small data).
11. Adding the names to the dispatch but not the corpus (unmeasured behaviour).
12. Adding the names to the corpus but not the dispatch (the test would fail — but only in CI, not locally).
13. Prefixing the legacy names with `_xlfn.` in the export list (Excel stores them bare; a prefixed file is
    unreadable by Excel as the legacy function).
14. Not prefixing `FORECAST.LINEAR` (Excel stores it with `_xlfn.`; a bare file is unreadable).
15. Treating numeric-looking text in a *range* as a number for `VAR` (ranges ignore text; only direct literals
    coerce — the distinction the `A` family exists to make).
16. Propagating an error cell inside a *range* for `MODE` (the kernel ignores it there; only a direct error
    argument propagates).
17. Extending the pinned branch lines instead of adding delegation branches (breaks R117-7's byte-exact pin).

The modal shortcut is D.8: a fixture generated by the code under test. It fails because the corpus test would pass
against the same wrong numbers forever. The meta-control detects it: it mutates the generator's counting rules
(parse numeric-looking text as a number) and requires the render to move away from the committed corpus — if it
did not move, the fixture would be a transcript of one implementation.

## E. Mechanism portfolio (six distinct, kept independent until judged)

1. **Delegation to modern kernels** (chosen). Central lemma: each legacy name is *defined* as its modern
   counterpart, so `evalEssential("VAR.S", …)` is the semantics, not an approximation. Artifact: five dispatch
   branches. Proved: the alias identities are structural. Unresolved interface: none — the kernels already exist.
   Cheapest falsification: `VAR(1,2,3,4)` must equal `VAR.S(1,2,3,4)` = 1.6666666666666667. Gap: local (none).
2. **Extend the existing branch lines** (rejected). Add `VAR` etc. to the `STDEV.S` branch and `MODE` to the
   `MODE.SNGL` branch. Rejected because it changes the byte-exact text R117-7 pins; the delegation achieves the
   same semantics with additions only.
3. **Duplicate the variance kernel** for the legacy names (rejected). Two implementations of one formula is a
   divergence waiting to happen; delegation is strictly better and easier to debug.
4. **New `evalForecast` helper** (chosen for FORECAST). Central construction: least squares
   `slope = sxy/sxx`, `intercept = my − slope·mx`, result `intercept + slope·x`, with `SLOPE`/`INTERCEPT`'s
   pairing rules. Proved: agrees with `statistics.linear_regression` on every numeric row. Unresolved interface:
   the `x` scalar's coercion (direct literal vs cell) follows `evalScalarAsNum`, consistent with the engine.
   Cheapest falsification: `FORECAST(2.5, [2,4,6,8], [1,2,3,4])` must be 5.0. Gap: local.
5. **Reuse `evalLinearStats` for FORECAST** (rejected). It takes two arguments `(y, x)`; `FORECAST` takes three
   `(x, y, x)`. Forcing it would mean fabricating a wrapper that changes the pairing rules. A dedicated helper
   with the same rules is cleaner.
6. **Route through `evalEssential` by adding names to `ESSENTIAL_NAMES`** (rejected). `evalEssential` dispatches
   on the name; a new name would need a new branch there anyway, which is the same line count as delegating at
   the `evalCall` site — and the `evalCall` site keeps the pinned `evalEssential` text untouched.

## F. Artifacts

* Product: `FormulaEval.kt` (+7 dispatch lines, `evalForecast` +35 lines), `SpillSheetEngine.kt` (+1 export line).
* Oracle: `tools/gen_r119_legacy_stats_corpus.py` (deterministic, `render()` importable/pure), corpus
  `format/ooxml/src/test/resources/r119/legacy_stats_corpus.tsv` (212 rows, 50 error rows).
* Test: `LegacyStatsFunctionTest.kt` (17 `@Test`s, `TOL = 1e-12`, helpers `cell`/`number`/`shown` ≤3 params).
* Gate: `tools/check_r119_legacy_stats.py` (rules R119-1…7), controls `tools/test_r119_legacy_stats.py` (25).
* Census: `tools/r119_census_deepen.py` (re-run after any owned-file edit; foreign-drift guard).

## G. Second-order adversary

For the chosen candidate the strongest attack is not on the maths but on the *evidence*: make the oracle dependent on
the implementation (D.8), make the tolerance hide a divisor error (D.10), or make the fixture unreproducible (D.9).
All three are now controls. The second strongest is the alias-direction attack: `VAR` delegating to `VAR.P` (D.1)
passes every legacy test because no legacy test calls `VAR` — hence the corpus, which calls it 36 times against
an independent oracle. The third is reachability: a name can compile, dispatch and still be unreachable from a
workbook cell — hence the `evaluateSheet` case. The fourth is the save path: a name can evaluate correctly and
still be written to `.xlsx` with the wrong prefix — hence R119-1's prefix check in both directions (the dotted
name prefixed, the legacy names bare), backed by the XlsxWriter table.

What this adversary does **not** touch: a shared bug between the generator's least-squares formula and
`statistics.linear_regression` (different implementations, agreement measured on every numeric row before the
corpus was written), and Excel's own last-ULP values (out of scope, stated).

## H. Verification — what actually ran

| Claim | Evidence | Strength |
|---|---|---|
| Product source compiles in the real AGP/Kotlin toolchain | CI `Android native` on this commit (pending at write time; see §J) | strong when green (compiler) |
| The module's JUnit test compiles and the test task passes | same job, `Run all JVM unit tests` step, which includes `:format:ooxml:test` | strong for compile+pass; **the per-case JUnit names are not visible to me** (test-report artifact is on `*.blob.core.windows.net`, unreachable) |
| No regression in the contract source sets that compile `FormulaEval.kt` | same job, `Production Kotlin pilot contracts` step | strong when green |
| The Python lane and source checks are green in CI | same job, `Check source contracts and generated traceability` | strong when green |
| The corpus is an independent oracle | 117 numeric rows recomputed by the `statistics` module in the local control `test_every_value_is_the_statistics_library_value` (rows the statistics module cannot handle — single points, constant `known_x` — are skipped there and covered by the corpus test in CI); 12 `MODE` rows recomputed by a separate transcription | strong locally, and the same numbers are the ones CI's JUnit test reads |
| Gate + controls | `R119_LEGACY_STATS_OK names=7 rows=212 error_rows=50 tests=17 tolerance=1e-12`; `Ran 25 tests … OK` | strong locally |
| Prior rounds still green | `R117_PERCENTILE_OK`, `R116_TRIG_OK`, `Ran 24 tests … OK` (R117 controls) | strong locally |
| Full local lane | `Ran 2437 tests … OK (skipped=446)`, `check_project.py` OK, census `50/50 (100.00%)` | strong locally |

**Not verified, and not claimed:** that Excel produces these exact doubles; that the sheet UI renders them (no device,
no frames); that a workbook containing `=FORECAST(2, A1:A4, B1:B4)` round-trips through `.xlsx` with the `_xlfn.`
prefix and a cached value (the export list is pinned, but no file was written and read back here); and that the
histogram of ulp differences is zero — the tolerance is `1e-12`, not exact equality.

## I. Repair: a candidate inherits no trust

Four failures occurred, all in my own evidence machinery, not the product:

1. The gate's coverage rule demanded ≥20 rows per name; `COVARIANCE` had 12 datasets. The gate was right and the
   corpus was thin — eight more paired datasets were added (212 rows total) rather than the threshold lowered.
2. Three controls failed on their first run: one renamed a helper to a name that still contained the checked
   substring (`evalForecast2` contains `evalForecast`); one shrank the suite by 3 from 17, leaving 14, which still
   passes the ≥14 rule; one replaced `#DIV/0!` with `#SPILL!`, which also removed the `FORECAST` refusal rows and
   so correctly fired R119-2 as well as R119-3. All three were control bugs. The gate caught badly-written
   controls, which is the gate working as intended.
3. The meta-control (`test_the_generator_and_the_oracle_are_independent_implementations`) did not move the render:
   the corpus had no numeric-looking text token, so parsing text as a number changed nothing. Two numeric-looking
   text datasets (`"5"`, `"10"`) were added, which is also the D.15 distinction made concrete.
4. `test_every_mode_value_is_a_most_frequent_value` checked only 6 rows (only six datasets have a repeat). Six
   more repeat datasets were added, bringing the numeric `MODE` rows to 12.

After each repair the whole local lane, the census and the gate were re-run on the repaired tree. Every numeric
assertion in the Kotlin test was re-derived from the documented semantics in Python before the first push
(`statistics.variance`/`pvariance`/`pstdev`, `linear_regression`, `covariance·(n−1)/n`) — all agree, so the CI
run is not a guess.

5. **CI failed on the first push** (`Android native` run 44834405157, `Check source contracts and generated
   traceability`): 13 Python-lane failures, all in the R119 controls, all with R119-5 (generator fidelity)
   firing in mutant trees — meaning `render()` differed from the committed corpus under CI's Python 3.12 while
   agreeing under the local 3.11. Root cause: **CPython 3.12 changed `sum()` for floats from naive left-to-right
   to Neumaier compensated summation** [1][2][3], so the generator's `sum()` calls produced last-ULP-different
   values under 3.12. The corpus was byte-identical (verified by `git show HEAD:…corpus` == `render()` under
   3.11); the generator was not version-stable. Fixed by replacing every float `sum()` in the generator with
   `naive_sum` (explicit left-to-right loops, matching Kotlin's `sum()` and identical on every Python version)
   and `** 2` with `x * x`. The corpus is unchanged under 3.11 (where `sum()` is already naive), which is the
   proof the fix is a no-op locally and a correction in CI. The gate and controls sum only ints and use
   `statistics` (exact arithmetic), so they needed no change. This is the same class of failure as R117's
   hand-arithmetic errors — my evidence machinery, not the product — and the reason the byte-exact R119-5 rule
   exists: it caught a real version instability that a tolerance-based check would have hidden.

[1] https://github.com/Jev-Engineering/cookbook/issues/274 — "CPython 3.12 changed sum() to use Neumaier
compensated summation for floats."
[2] https://github.com/buster14a/buster/pull/2111 — "In Python 3.12, built-in float sum() changed from naive
left-to-right addition to compensated (Neumaier) summation."
[3] https://github.com/buster14a/buster/issues/2110 — "From Python 3.12, sum() of floats uses compensated
(Neumaier) summation, while 3.11 and earlier add naively left to right."

## J. Stopping point and next highest-information test

Stopped at: seven names reachable from both evaluator entry points, 212 independent probes, 17 JUnit cases, 7 gate
rules and 25 controls, with the local lane green. Not stopped at "all features A": **Class A remains 0/50
(0.00%)**, `testable` remains 50/50 (100.00%) — those numbers are unchanged by this round and are not promoted by it.

The next highest-information test is a real workbook: type `=VAR(A1:A4)`, `=MODE(A1:A4)` and
`=FORECAST(2, A1:A4, B1:B4)` into the sheet editor on a device, save as `.xlsx`, and have an independent reader
confirm the cached values and the `_xlfn.FORECAST.LINEAR` prefix — which would also tell us whether `FormulaXlsx`
writes caches for the new names, the one adjacent path this round did not touch.

The next gap, measured the same way (repo-wide grep for each quoted name over all `*.kt` outside `build/`), is
the distribution-inverse family: `T.DIST`, `T.INV`, `CHISQ.INV`, `F.DIST`, `F.INV`, `BETA.INV`, `GAMMA.INV`,
`BESSELK`, `BESSELY` — all absent, all in XlsxWriter's `FUTURE_FUNCTIONS` table (so all need the `_xlfn.` prefix),
and all computable from the incomplete-beta and incomplete-gamma kernels the engine already has.
