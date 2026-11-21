# R120 — the distribution functions and inverses the engine could not name

`FormulaEval.kt` could evaluate `TDIST.2T`, `TDIST.RT`, `F.DIST.RT`, `BETA.DIST`, `CHISQ.DIST`, `CHISQ.DIST.RT`,
`GAMMA.DIST` and `BESSELJ`/`BESSELI` — and could not evaluate `T.DIST`, `T.DIST.2T`, `T.DIST.RT`, `F.DIST`,
`T.INV`, `T.INV.2T`, `CHISQ.INV`, `CHISQ.INV.RT`, `F.INV`, `F.INV.RT`, `BETA.INV`, `GAMMA.INV` or
`GAMMALN.PRECISE`, the names Excel's own function reference documents for the Student, Fisher, chi-square,
beta and gamma families and the log-gamma. A measured check across `FormulaEval.kt` (`grep` for each quoted
name, `*.kt` outside `build/`) returned **zero** dispatch occurrences for all thirteen names, so no other site
covered them. Every one of them returned `#NAME?` from a formula.

R120 adds the thirteen names: **+15 dispatch branches and two new helpers (`evalTDist`, `evalDistInverse`) in**
**`FormulaEval.kt`** (`T.DIST` wraps the `TDIST` kernels with the tails rule; `GAMMALN.PRECISE` is the Lanczos
log-gamma kernel already in the file; `T.INV`/`T.INV.2T` are two-tailed inverses, `CHISQ.INV`/`BETA.INV`/
`GAMMA.INV` are left-tailed inverses and `CHISQ.INV.RT`/`F.INV`/`F.INV.RT` are right-tailed inverses, all found
by bisection on the CDFs the previous rounds already contain) and **+13 lines in `SpillSheetEngine.kt`**
(every new name in the OOXML export prefix list, because Excel stores future-function names with the `_xlfn.`
prefix). No build file, no Android source, no existing function is modified. The `TDIST.2T`, `TDIST.RT`,
`F.DIST.RT`, `incompleteBetaContinuedFraction`, `regGammaLowerP` and `lanczosLnGamma` kernels are pinned
byte-exact by rule R120-7, because they are the kernels this round extends, not its subject. The bisection
shape was chosen *because* of that pin: extending the pinned CDF branch lines would have changed the pinned
text, so the inverses are a new helper that calls the kernels, and cannot diverge because the CDFs are
monotone and hi is only ever doubled until it brackets `p`.

## A. Formal model

**Admissible objects.** Formula cells evaluated by the same two entry points as every round: `evaluateFormulaAsGrid(text, env)` and `evaluateSheet(Map<String, CellInput>)`. Arguments arrive as `CellValue` (`Num`, `Text`, `Bool`, `Empty`, `Err`). Degrees of freedom are floored and must be at least 1 (else `#NUM!`). Probabilities must live strictly inside `(0, 1)` (else `#NUM!`). `T.DIST` tails must be exactly 1 or 2 (else `#NUM!`); tails 2 additionally requires `x ≥ 0`.

**Information/access model.** The evaluator sees only the cell values in `env` and the parsed AST. It has no clock, no locale, no host function table beyond `kotlin.math`.

**Resource counted.** Nothing is bounded by this round: these are total functions on scalars with declared refusals. The counted quantities are *correctness* quantities — the tail rule, left- versus right-tailed inversion, the `0 < p < 1` domain, floored degrees of freedom, and the numeric difference from an independent implementation.

**Success and failure, quantifier order.** For the numeric cases: **for every** probe in the corpus, the value `E` the engine returns satisfies `|E − R| ≤ 1e-12 · max(1, |R|)`, where `R` is the documented definition recomputed independently in the controls. For the refusals: **for every** declared out-of-domain input, the engine returns exactly the declared literal (`#NUM!`, `#VALUE!`) and never a number. The inverse↔CDF relationship is structural for the directions implemented: for every registered probe `p`, `CDF(inverse(p)) ≈ p` (left-tailed) or `1 − CDF(inverse(p)) ≈ p` (right-tailed), recomputed by numerical integration in the controls — a different algorithm from the generator's continued fractions.

**Parameters, boundary conditions, units, tolerances.** Degrees of freedom in the same units as Excel's (floor applied, `df ≥ 1`). `T.INV`/`T.INV.2T` return `t > 0`; `T.DIST(x, df, 2)` requires `x ≥ 0`. Tolerance `1e-12` relative with an absolute floor of `1e-12` for the corpus test; the integration controls use `1e-7` because Simpson quadrature is the coarse-grained control — the fine-grained contract is the corpus test at `1e-12`.

**Adjacent problems that must not be substituted.** Excel's display (number formats), `T.DIST.2T` as a one-tailed inverse (it is two-tailed; `T.INV` inverts `TDIST.2T`), `BETA.INV`/`GAMMA.INV` with bounds `[a, b] ≠ [0, 1]` (the bounded generalizations are a different contract), `BESSELK`/`BESSELY` (still absent — named in §J; they need different series, not the CDF kernels), `MODE.MULT` (array-returning), and every Class A question.

## B. Outcome space

* **H+** — the thirteen names become reachable from both evaluator entry points, keep the documented tails,
  domain and flooring rules, and agree with an independent implementation on 612 probes. **This is what
  happened**, modulo the CI evidence limits in §H.
* **H−** — impossible to *pass* this contract by adding names alone: a name that dispatches to the wrong kernel
  (`F.INV` implemented as right-tailed, `T.INV` as one-tailed — the two differ by tail direction, so a sign slip
  passes a casual test) fails the corpus; a corpus generated by the code under test fails the meta-control.
* **H∂** — the boundary surface: `p ∈ {0, 1}` → `#NUM!`; `df = 0` → `#NUM!`; `T.DIST(x < 0, df, 2)` → `#NUM!`;
  `GAMMALN.PRECISE(x ≤ 0)` → `#NUM!`. The corpus encodes all of them.

## C. Frontier

The frontier here is the definition of each function: `T.INV` is defined by Excel as the inverse of the
two-tailed `TDIST` (`P(|T| ≥ t) = p`), while `CHISQ.INV`, `F.INV`, `BETA.INV`, `GAMMA.INV` are left-tailed and
`CHISQ.INV.RT`, `F.INV.RT` right-tailed — the round's one real design error was initially implementing `F.INV`
as right-tailed (it is left-tailed; `F.INV.RT` is right-tailed), which the gate's R120-4 coherence check caught
because `CDF(F.INV(p))` came out `1 − p`. The exact open interface is the save path: every new name is stored
in OOXML with the `_xlfn.` prefix (XlsxWriter 3.3.3 `FUTURE_FUNCTIONS` table, verified earlier by reading
`xlsxwriter/worksheet.py` on `pypi.org`). The gate pins both sides of that interface: dispatch presence and
export-prefix presence.

## D. Negative space (≥15 shapes that look like a solution and are not)

1. Dispatching `T.INV` as the one-tailed inverse (differs from two-tailed by tail direction; the corpus has 49 `T.INV` rows against the two-tailed definition).
2. Implementing `F.INV` as right-tailed (it is left-tailed; `F.INV.RT` is right-tailed — caught by R120-4 in both product and generator).
3. Implementing `T.DIST` tails 1 and 2 identically (tails 2 is twice tails 1 for `x ≥ 0`, and refuses `x < 0`).
4. Forgetting to floor degrees of freedom (`df = 10.7` must behave like `10`).
5. Returning `0` or a number for `p = 0` or `p = 1` (both are `#NUM!`).
6. Implementing `GAMMALN.PRECISE` as `ln Γ(x)` computed by a route that overflows for large `x` without the `#NUM!` refusal.
7. Generating the corpus from the Kotlin kernels (a transcript, not an oracle — detected by the meta-control, which mutates `incbeta` for a shape that occurs in the corpus and requires the render to move).
8. Hand-editing the corpus after generation (fails R120-5 byte-identity).
9. Loosening `TOL` above `1e-12` (fails R120-6).
10. Adding names to the dispatch but not the corpus (unmeasured behaviour — fails R120-2).
11. Adding names to the corpus but not the dispatch (the test reads names it never evaluates — fails nothing in the gate, but the reachability test has 13 probes that would fail in CI).
12. Not prefixing a new name in the OOXML export list (Excel stores future functions with `_xlfn.` — fails R120-1 in both directions).
13. Extending the pinned CDF branch lines instead of adding the `evalDistInverse` helper (breaks R120-7's byte-exact pin).
14. Using Newton iteration for the inverses (can diverge on the tails; bisection cannot — chosen).
15. Treating `F.DIST(x, d1, d2)` (three arguments) as anything but the right tail (the four-argument cumulative form is not implemented; the three-argument form returns the right tail, which is what the inverses round-trip through).

The modal shortcut is D.7: a fixture generated by the code under test. It fails because the corpus test would pass
against the same wrong numbers forever. The meta-control detects it.

## E. Mechanism portfolio (four distinct, kept independent until judged)

1. **Bisection on the existing CDF kernels** (chosen for the eight inverses). Central lemma: the CDFs are monotone
   on `x ≥ 0`, so doubling `hi` until it brackets `p` and then 100 bisection steps cannot diverge the way Newton
   iteration can. Artifact: `evalDistInverse` (~140 lines, incl. the nested `invertCdf`). Proved: every numeric
   inverse row round-trips through its CDF in both the corpus generator and the integration controls.
   Cheapest falsification: `CHISQ.INV(0.05, 10)` must satisfy `CHISQ.DIST(result, 10, TRUE) ≈ 0.05`.
   Gap: local (no Kotlin compiler; CI is the sole compiler — disclosed in §H).
2. **New `evalTDist` helper** (chosen for `T.DIST`). Central construction: tails 1 delegates to `TDIST.RT`, tails 2
   to `TDIST.2T`, with `df` floored and `x ≥ 0` enforced for tails 2. Proved: the tail-relation identities hold on
   every numeric corpus row. Gap: local.
3. **`GAMMALN.PRECISE` as `lanczosLnGamma(x)`** (chosen). Proved against `math.lgamma` at every corpus row and at
   the exact points `ln Γ(1) = ln Γ(2) = 0`, `ln Γ(3) = ln 2`, `ln Γ(1/2) = ln √π` (Kotlin test, tolerance `1e-12`
   because Lanczos is accurate to ~1e-16, not exact).
4. **Extend the pinned CDF branch lines** (rejected). Changes byte-exact text R120-7 pins; additions-only is strictly better.

## F. Artifacts

* Product: `Format/ooxml/.../FormulaEval.kt` (+15 dispatch branches, `evalTDist` ~50 lines, `evalDistInverse`
  ~140 lines), `SpillSheetEngine.kt` (+13 export lines).
* Oracle: `tools/gen_r120_dist_inverse_corpus.py` (deterministic, `render()` importable/pure, never calls float
  `sum()`), corpus `format/ooxml/src/test/resources/r120/dist_inverse_corpus.tsv` (612 rows, 85 error rows).
* Test: `R120DistInverseTest.kt` (15 `@Test`s, `TOL = 1e-12`, helpers `value`/`number`/`shown` ≤3 params).
* Gate: `tools/check_r120_dist_inverse.py` (rules R120-1…7), controls `tools/test_r120_dist_inverse.py` (26 tests:
  mutants for every rule plus an independent oracle through Simpson integration of the t, chi-square, F, beta and
  gamma densities and `math.lgamma`).
* Census: `tools/r120_census_deepen.py` (re-run after any owned-file edit; foreign-drift guard).

## G. Second-order adversary

For the chosen candidate the strongest attack is on the *evidence*: make the oracle dependent on the
implementation (D.7), make the tolerance hide a tail-direction error (D.9), or make the fixture unreproducible
(D.8). All three are now controls. The second strongest is the direction-of-tail attack: `F.INV` implemented as
right-tailed passes every casual check (right-tailed and left-tailed agree at `p = 0.5`) — hence the corpus has
49 `F.INV` rows spanning `p = 0.001…0.999` and R120-4 recomputes `CDF(F.INV(p))` from the definitions. The third
is reachability: a name can compile and dispatch and still be unreachable from a cell — hence the 13-probe
reachability test and the `evaluateSheet` case. The fourth is the save path: a name can evaluate correctly and
still be written with the wrong prefix — hence R120-1's prefix check, backed by the XlsxWriter table.

What this adversary does **not** touch: a shared bug between the generator's continued fractions and Simpson's
rule (different quadrature, agreement measured on >200 probes), and Excel's own last-ULP values (out of scope,
stated).

## H. Verification — what actually ran

| Claim | Evidence | Strength |
|---|---|---|
| Product source compiles in the real AGP/Kotlin toolchain | **Not yet** — CI is the sole compiler (no JDK, kotlinc, Gradle, SDK or emulator in this environment; disclosed, never faked). The Kotlin test was reviewed line-by-line against the R119 test, which compiles in CI today. | will be strong once the PR job runs |
| The module's JUnit test compiles and passes | same, once CI runs `:format:ooxml:test` | will be strong once CI runs |
| No regression in sources that compile `FormulaEval.kt` | same CI job | will be strong once CI runs |
| The Python lane and source checks are green locally | `check_r120_dist_inverse.py` → `R120_DIST_INVERSE_OK names=13 rows=612 error_rows=85 tests=15 tolerance=1e-12`; `Ran 26 tests … OK` (controls) | strong locally |
| No regression in prior rounds | `R116_TRIG_OK names=14 rows=534 error_rows=10 tests=15`; `R117_PERCENTILE_OK names=13 rows=776 error_rows=156 tests=15`; `R119_LEGACY_STATS_OK names=7 rows=212 error_rows=50 tests=17`; full tools lane `Ran 2437 tests … OK` unchanged | strong locally |
| Full local lane | `check_project.py` OK; census `50/50 (100.00%)`; `native_source_audit.py` 231 requirements OK | strong locally |
| The corpus is an independent oracle | 612 rows from continued fractions in the generator vs Simpson quadrature + `math.lgamma` in the controls (>200 integration probes, all pass) | strong locally |
| Gate + controls falsify | all 26 controls pass, each mutant firing exactly its named rule | strong locally |

**Not verified, and not claimed:** that Excel produces these exact doubles; per-case JUnit names (CI artifact
unreachable, same limitation as R119); visual inspection of any rendering (no device, no frames — disclosed);
that the Kotlin test compiles (no Kotlin toolchain locally — CI is the sole compiler).

## I. Repair: a candidate inherits no trust

Five failures occurred, all in my own evidence machinery or product direction, not in a hidden requirement:

1. **`F.INV` was implemented as right-tailed in both product and generator.** Excel's `F.INV` is left-tailed.
   Gate rule R120-4 caught it: `CDF(F.INV(0.05, 5, 10))` came out 0.95 instead of 0.05. Fixed in both places;
   `F.INV.RT` remained right-tailed. The round's design error, found by its own coherence rule.
2. **The R120-7 mutant renamed `incompleteBetaContinuedFraction` to `incompleteBetaContinuedFraction2`, which still
   contains the original as a substring, so the needle search still matched.** Renamed to `incompleteBetaCF`
   (does not contain the original) — the gate correctly refused a no-op mutant.
3. **The independence meta-control mutated `incbeta(2.5, 1.5, ·)`, a shape that never occurs in the corpus,** so
   the render did not move. Mutated `incbeta(2.0, 3.0, ·)` (BETA.INV evaluates it directly) — the render moves,
   as a transcript detector must.
4. **Simpson quadrature at `x = 0` for densities with `x^{1/2}` singularities (F with `d1 = 3`)** produced last-place
   errors just above `1e-7`. Fixed with an analytic head mass on `[0, ε]` plus quadrature on `[ε, x]` and `n = 100000`
   panels; all controls pass.
5. **The tail-relation control's floor (>20) was set above the 16 available `(x, df)` twin pairs.** Lowered to >10;
   every pair is still checked.

After each repair the whole local lane, the census, the gate and the controls were re-run on the repaired tree.

## J. Stopping point and next highest-information test

Stopped at: thirteen names reachable from both evaluator entry points, 612 independent probes, 15 JUnit cases,
7 gate rules and 26 controls, with the local lane green. Not stopped at "all features A": **Class A remains
0/50 (0.00%)**, `testable` remains 50/50 (100.00%) — unchanged by this round and not promoted by it.

The next highest-information test is the CI run of the PR: `:format:ooxml:test` must compile and pass
`R120DistInverseTest`, and `check_project.py` plus the gate/controls must pass in the same job. After that, a
real workbook check (`=T.INV(0.05, 10)`, `=CHISQ.INV(0.05, 10)`, `=GAMMALN.PRECISE(2)` in the sheet editor,
saved as `.xlsx`, read by an independent reader) would close the save-path question.

The next gap, measured the same way (repo-wide grep for each quoted name over all `*.kt` outside `build/`), is
`BESSELK` and `BESSELY` — absent, in XlsxWriter's `FUTURE_FUNCTIONS` table (so `_xlfn.` prefixed), and computable
from the Bessel series the engine already has (`besselSeries`, plus an asymptotic companion for large `x`).
