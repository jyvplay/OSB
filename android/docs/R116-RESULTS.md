# R116 — the trigonometry the formula engine called but could not offer

A formula engine that can evaluate `SEC(x)` but not `COS(x)` is not missing a feature; it is missing the feature its own
implementation is written on top of. That is the state `FormulaEval.kt` was in:

```kotlin
"SEC"  -> { val c = kotlin.math.cos(x); ... 1.0 / c }
"CSC"  -> { val s = kotlin.math.sin(x); ... 1.0 / s }
"COT"  -> { val t = kotlin.math.tan(x); ... 1.0 / t }
"SECH" -> 1.0 / kotlin.math.cosh(x)
"ACOT" -> (Math.PI / 2.0) - kotlin.math.atan(x)
```

Eight kernels were reachable — `SEC`, `CSC`, `COT`, `SECH`, `CSCH`, `COTH`, `ACOT`, `ACOTH` — and every primitive they
call was not: `SIN`, `COS`, `TAN`, `ASIN`, `ACOS`, `ATAN`, `ATAN2`, `SINH`, `COSH`, `TANH`, `ASINH`, `ACOSH`, `ATANH`.
`SQRTPI` existed; `PI()` did not. A measured check across every Kotlin source in the repository (`grep` for `"SIN"`,
`"COS"`, `"TAN"`, `"ATAN2"`, `"PI"` over all `*.kt` outside `build/`) returned **zero** occurrences, so no other dispatch
site covered them.

R116 adds the fourteen names to `FormulaEval.kt`: **49 lines inserted, 1 changed**, one dispatch branch and one helper.
No build file, no Android source, no test assertion and no existing function is modified. The reciprocal kernels and the
`ACOT` definition are pinned byte-exact by rule R116-7 precisely because they are this round's *callers*, not its
subject.

## A. Formal model

**Admissible objects.** Formula cells evaluated by two entry points of the same engine: `evaluateFormulaAsGrid(text, env)`
(a single expression) and `evaluateSheet(Map<String, CellInput>)` (the dependency DAG the sheet editor uses). Arguments
arrive as `CellValue`, i.e. `Num`, `Text`, `Bool`, `Empty` or `Err`.

**Information/access model.** The evaluator sees only the cell values in `env` and the parsed AST. It has no clock, no
locale, no host function table beyond `kotlin.math` (JVM fdlibm) and `java.lang.Math`.

**Resource counted.** Nothing is bounded by this round: these are total functions on doubles with declared refusals. The
counted quantities are *correctness* quantities — argument count, domain membership, returned error literal, and the
numeric difference from an independent implementation.

**Success and failure, quantifier order.** For the numeric cases: **for every** probe `(f, x)` in the corpus, the value
`E(f(x))` the engine returns satisfies `|E − R| ≤ 1e-12 · max(1, |R|)`, where `R` is CPython's `math` value. For the
refusals: **for every** declared out-of-domain argument, the engine returns exactly the declared literal
(`#NUM!`, `#DIV/0!`, `#VALUE!`) and never a finite approximation, and **for every** error argument the error is
propagated rather than coerced to zero. Domain *refusal* is a stronger claim than "≈ NaN": the engine must not leak a
non-finite double, which `CellValue.Num`'s `init` block would reject anyway.

**Parameters, boundary conditions, units, tolerances.** Radians, `Math.PI` as the only π. Domains: `ASIN`/`ACOS`
`[-1, 1]`; `ACOSH` `[1, ∞)`; `ATANH` `(-1, 1)`; `ATAN2(0, 0)` refused. Tolerance `1e-12` relative with an absolute
floor of `1e-12` for values near zero; the last-ULP behaviour of `tan(π/2)` (≈1.633e16 in double) is deliberately not
asserted beyond the corpus value. Overflow (`COSH(1000)`, `SINH(±1000)`) is `#NUM!`.

**Adjacent problems that must not be substituted.** Excel's *display* of these values (number formats), Excel's
own last-ULP results, degree-based trigonometry, complex trigonometry (`IMSIN`/`IMCOS` already exist separately),
statistical distribution functions that call `ln`/`exp` internally, the calculator UI's own keypad, and every
Class A question about rendering these results in a real workbook.

## B. Outcome space

* **H+** — the fourteen names become reachable, keep spreadsheet argument order and documented domains, and agree with
  an independent libm implementation on 534 probes. **This is what happened**, modulo the CI evidence limits in §H.
* **H−** — impossible to *pass* this contract by adding names alone: a name that dispatches but is not implemented, or
  is implemented with `atan` where `asin` belongs, fails R116-1 or the corpus. There is no shortcut that satisfies it.
* **H∂** — the boundary cases are exactly the disagreement surface between a spreadsheet and a maths library:
  `ATAN2(x_num, y_num)` vs `atan2(y, x)`; `ACOT`'s range convention (already fixed in the pre-existing kernel and
  therefore pinned, not re-decided); `TANH` saturating to exactly ±1.0 only beyond |x| ≈ 20; and `ASIN`/`ACOS` at
  ±1 exactly, where `ACOS(1)` must be `0.0` rather than `-0.0` (`normalizeNegZero`).

Commit threshold declared before pushing: (1) product diff compiles; (2) the module's own JUnit test executes and passes
in CI; (3) an independent oracle generates the corpus and re-derives it; (4) the gate's controls each fire their named
rule; (5) the full Python discovery lane stays green; (6) the census re-pins only this round's files.

## C. Frontier

The governing documents are ISO/IEC 29500-1 and the function reference it inherits (documented domain refusals, the
`ATAN2(x_num, y_num)` order, `#DIV/0!` for `ATAN2(0,0)`). **This sandbox cannot fetch them**: outbound access is limited
to `github.com`, `codeload.github.com`, `api.github.com`, `registry.npmjs.org`, `pypi.org`,
`files.pythonhosted.org`, so no Microsoft or ISO page was retrieved this turn. The semantics implemented are therefore
stated as *the standard's documented behaviour as understood*, and the evidence for them is not a citation but the
corpus plus the refusals the test asserts. That is a weaker foundation than a fetched quote, and it is labelled as such
rather than dressed up.

The open interface: whether these values are what **Excel** produces bit-for-bit. Nobody here can answer that; the
corpus answers a different and checkable question — whether two independent libm implementations agree.

## D. Negative space (≥15 shapes that look like a solution and are not)

1. Adding the names to `ESSENTIAL_NAMES` without an implementation — they would fall through to `#NAME?` or, worse,
   reach `evalEssential` and be handled as something else. R116-1 requires one branch per name.
2. Adding a name to the helper but not to the dispatch branch: the helper is unreachable, all corpus rows return
   `#NAME?` — caught by R116-1 *and* by every corpus row.
3. Implementing `ATAN2` as `atan2(x, y)`: the corpus rows for the quadrants `(1,0)`, `(0,1)`, `(-1,0)`, `(0,-1)` differ
   by π/2 and the explicit order test fails.
4. Returning `NaN`/`Infinity` for out-of-domain arguments: `CellValue.Num` refuses non-finite values at construction,
   so the cell would throw instead of answering `#NUM!`; both the domain test and the overflow test fail.
5. Coercing an error argument to `0.0` (`evalScalarAsNum` would do that if the propagation loop were omitted): the
   propagation test fails.
6. Using `Double.NaN` for `ATAN2(0,0)` instead of `#DIV/0!`: the refusal test fails.
7. Accepting any arity (`args.firstOrNull()`-style): the six arity assertions fail.
8. Generating the corpus from the Kotlin implementation, or from a transcription of it: this is the modal shortcut. It
   makes the fixture a *transcript*, so a wrong implementation passes trivially. It fails because the expected column is
   produced by CPython's `math` in `tools/gen_r116_trig_corpus.py` and `tools/test_r116_trig.py` recomputes the entire
   numeric column independently through `math` again, function by function.
9. Loosening `TOL` until a wrong result fits: R116-6 refuses a tolerance above `1e-12`.
10. Editing one corpus value by hand: R116-5 compares the committed file to the generator's render, and R116-4 re-derives
    the identities from the corpus itself, so a single altered row breaks at least two rules (proved by a control).
11. Deleting corpus rows to remove an inconvenient function: R116-2 requires ≥20 rows per unary function and exactly one
    `PI` row.
12. Duplicating a row so a function looks better covered: R116-3 deduplicates `(function, x, y)`.
13. Asserting only that the cell is a number (never its value): the corpus test asserts the value against the oracle.
14. Testing only through `evaluateFormulaAsGrid` and calling that "the sheet works": a workbook cell goes through the
    dependency DAG, which is a different path; R116 adds a `evaluateSheet` case that also checks a downstream cell
    consumes the result and that the `A1 → B1` dependency edge is recorded.
15. Reusing the *reciprocals* as the implementation (`SIN(x) = 1/CSC(x)`): wrong at `x = 0` and less accurate; the
    corpus covers 0 and the symmetry of the full circle.
16. Passing a Kotlin arity error to CI and calling the local gate "green": this actually happened (§I) and is now rule
    R116-6's helper-arity check.
17. Treating `kotlinc` absence as "cannot verify": CI compiles and runs the module's tests; the claim is scoped to that.
18. Citing a previous revision's CI for this revision: the run ids in §H are this branch's, on this commit.

## E. Mechanism portfolio (six distinct, kept independent until judged)

| # | Mechanism | Artifact | Proved portion | Unresolved interface | Cheapest falsifier | Gap |
|---|---|---|---|---|---|---|
| 1 | One dispatch branch + one helper in `FormulaEval.kt` (**chosen**) | 49 inserted lines | Dispatch, domains, order, propagation | Excel bit-equality | R116-1 + corpus | local |
| 2 | Add the names to `ESSENTIAL_NAMES` and implement in `evalEssential` | — | Same semantics possible | A second dispatch convention for 14 names; `ESSENTIAL_NAMES` is an unordered `setOf`, so arity/PI special-casing still needs code | a reviewer diffing two conventions | rejected: larger diff, no benefit |
| 3 | New top-level kernel object (`TrigFunctions.kt`) called from the branch | — | Testable in isolation | A new file to include in 20+ Gradle source sets (the R112-7 include-closure trap), for arithmetic that is one line each | include-closure gate | rejected: risk without value |
| 4 | Rewrite `SEC`/`CSC`/`COT` in terms of the new functions | — | Removes duplicate `math` calls | Touches working kernels and changes their last-ULP results; R116-7 would have to be relaxed | reciprocal identity test | rejected: violates "change no working kernel" |
| 5 | Precomputed value table for common angles | — | Exact at the listed angles | A spreadsheet is a calculator, not a lookup table; any other argument returns nothing | corpus at non-tabulated angles | rejected: not correct |
| 6 | UI-level expansion (rewrite `=SIN(x)` into `=1/CSC(x)` in the editor) | — | Display could look right | Ties correctness to one UI path; the engine, importer and recalc would all disagree; wrong at 0 | engine-level corpus | rejected: fixes the symptom in the wrong layer |

Mechanism 1 is the smallest change that makes the capability real for every consumer (editor, importer, DAG, spill).

## F. Artifacts

* `format/ooxml/src/main/kotlin/dev/fusion/ooxml/FormulaEval.kt` — +49/−1: one dispatch branch, `evalR116TrigFunction`,
  and a `SCOPE` sentence appended (the historical "300 pure functions" banners are pinned by R52/R53 checks and are
  **not** rewritten; the append says what R116 adds).
* `format/ooxml/src/test/resources/r116/trig_corpus.tsv` — 534 rows + header, 10 of them refusal rows.
* `tools/gen_r116_trig_corpus.py` — deterministic generator; expected column from `math`.
* `format/ooxml/src/test/kotlin/dev/fusion/ooxml/TrigFunctionTest.kt` — 260 lines, 15 JUnit cases, 60 assertion sites,
  executed by the existing `:format:ooxml:test` CI step (no workflow edit).
* `tools/check_r116_trig.py` — 7 rules; `tools/test_r116_trig.py` — 22 controls.
* `tools/r116_census_deepen.py` — re-pins exactly this round's six files and refuses foreign drift.

## G. Second-order adversary

For the chosen candidate the strongest attack is not on the maths but on the *evidence*: make the oracle dependent on
the implementation (D.8), make the tolerance hide a defect (D.9), or make the fixture unreproducible (D.10). All three
are now controls. The second strongest is arity/type-level: Kotlin's overload resolution between a formula, one value
and two values is exactly what broke CI (§I). The third is reachability: a name can compile, dispatch and still be
unreachable from a workbook cell — hence the `evaluateSheet` case.

What this adversary does **not** touch: a shared `kotlin.math` bug (both sides of the corpus would move together only if
CPython and the JVM shared an implementation, which they do not), and Excel's own last-ULP values (out of scope, stated).

## H. Verification — what actually ran

| Claim | Evidence | Strength |
|---|---|---|
| Product source compiles in the real AGP/Kotlin toolchain | CI `Android native` run 44701676497, `Traceability, assemble both flavors, JVM tests`: **success**, 9m; `Assemble both flavors` assembled both APKs (46,545,480 / 46,545,540 bytes) | strong (compiler) |
| The module's JUnit test compiles and the test task passes | same job, `Run all JVM unit tests` step **success**, which includes `:format:ooxml:test`; the *previous* run on this branch proved that step consumes this exact file (`:format:ooxml:compileTestKotlin FAILED … TrigFunctionTest.kt:173`) | strong for compile+pass; **the per-case JUnit names are not visible to me** (test-report artifact is on `*.blob.core.windows.net`, unreachable) |
| No regression in the **9** contract source sets that compile `FormulaEval.kt` | same job, `Production Kotlin pilot contracts` step (44 tasks) success | strong |
| The device suite still passes with the engine extended | CI `R54 office authoring` run 44701676448 on `9653151`: job `office` **success**, including `Require all 37 named Office cases without duplicate or skip` (the sheet-editing, grid, copy and recalculation cases run on a real API34 emulator) | strong for "no device-path regression"; it does not exercise `=SIN(…)` in a workbook |
| The Python lane and source checks are green in CI | same job, `Check source contracts and generated traceability` success | strong |
| The corpus is an independent oracle | 534 values recomputed by `math` in the local control `test_every_expected_value_is_the_platform_libm_value`; generator output compared byte-for-byte | strong locally, and the same numbers are the ones CI's JUnit test reads |
| Gate + controls | `R116_TRIG_OK names=14 rows=534 error_rows=10 tests=15 tolerance=1e-12`; `Ran 22 tests … OK` | strong locally |
| Full local lane | `Ran 2388 tests … OK (skipped=446)`, `check_project.py` OK, census `50/50 (100.00%)` | strong locally |

**Not verified, and not claimed:** that Excel produces these exact doubles; that the sheet UI renders them (no device,
no frames); that a workbook containing `=SIN(1)` round-trips through `.xlsx` unchanged (that path is `FormulaXlsx` and was
not touched, but neither was it exercised for these functions); and that the histogram of ulp differences is zero — the
tolerance is `1e-12`, not exact equality, and I did not measure per-function ulp maxima.

## I. Repair: two CI failures, both mine, both now gate rules

1. **`Census review stale: tools/test_r116_trig.py`** (run on `ebe150c`). I edited an owned file after the deepen script
   had hashed it. The pin was right and I was wrong; re-running `tools/r116_census_deepen.py` re-pinned only R116's
   files and refused nothing else. This is the second time this pattern has cost a CI cycle in this history, and the
   durable fix is procedural, not code: re-run the deepen script as the last step before pushing.
2. **`:format:ooxml:compileTestKotlin FAILED — Too many arguments for 'fun shown'`** (run on `1a144e0`). A four-argument
   call in the `ATAN2` arity case. The product source compiled; only my test helper call was wrong. Fixed, and the gate
   now counts the top-level arguments of every `cell(`/`number(`/`shown(` call in the Kotlin test and refuses more than
   three — with a control that re-introduces the exact defect and requires the rule to fire.

A repaired candidate inherits no trust: after both repairs the whole local lane, the census and the CI job were re-run
on the repaired commit, and the new attack (helper arity) was added to the controls rather than only to the test.

## J. Stopping point and next highest-information test

Stopped at: fourteen names reachable from both evaluator entry points, 534 independent probes, 15 JUnit cases, 7 gate
rules and 22 controls, with CI compiling and passing. Not stopped at "all features A": **Class A remains 0/50
(0.00%)**, `testable` remains 50/50 (100.00%) — those numbers are unchanged by this round and are not promoted by it.

The next highest-information test is a real workbook: type `=ATAN2(1,0)` and `=COSH(709)` into the sheet editor on a
device, save as `.xlsx`, and have an independent reader confirm the cached values — which would also tell us whether
`FormulaXlsx` writes caches for the new names, the one adjacent path this round did not touch.
