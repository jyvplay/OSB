# R133 — Remaining Complex Hyperbolic, Financial, and Odd-Period Bond Functions

## A. Scope

Eight functions added to `:format:ooxml`'s `FormulaEval` engine:

| Function | Meaning | Arity |
|---|---|---|
| `IMCOSH(inumber)` | Hyperbolic cosine of a complex number: cosh(z) | 1 |
| `IMCOT(inumber)` | Cotangent of a complex number: cot(z) = 1/tan(z) | 1 |
| `IMCOTH(inumber)` | Hyperbolic cotangent of a complex number: coth(z) = 1/tanh(z) | 1 |
| `VDB(cost, salvage, life, start, end, [factor], [no_switch])` | Variable declining-balance depreciation between periods | 5–7 |
| `ODDFPRICE(settlement, maturity, issue, first_coupon, rate, yld, redemption, frequency, [basis])` | Bond price with odd first coupon period | 8–9 |
| `ODDFYIELD(settlement, maturity, issue, first_coupon, rate, pr, redemption, frequency, [basis])` | Bond yield with odd first coupon period | 8–9 |
| `ODDLPRICE(settlement, maturity, last_coupon, rate, yld, redemption, frequency, [basis])` | Bond price with odd last coupon period | 8–9 |
| `ODDLYIELD(settlement, maturity, last_coupon, rate, pr, redemption, frequency, [basis])` | Bond yield with odd last coupon period | 8–9 |

IMCOSH/IMCOT/IMCOTH accept complex numbers in `x+yi` or `x+yj` format. VDB defaults factor to 2 (double-declining balance) and switches to straight-line when beneficial unless no_switch is TRUE. ODDF/ODDL functions use simplified bond pricing with day-count basis (0–4). All return `#VALUE!` on wrong arity, `#NUM!` on invalid inputs, `#DIV/0!` on singular results. No existing function was modified, removed, or degraded.

## B. Evidence

| Artifact | Path |
|---|---|
| Product impl | `android/format/ooxml/src/main/kotlin/dev/fusion/ooxml/FormulaEval.kt` (dispatch + implementations + SCOPE note) |
| Export list | `android/format/ooxml/src/main/kotlin/dev/fusion/ooxml/SpillSheetEngine.kt` (`_xlfn.` prefix) |
| Oracle generator | `android/tools/gen_r133_remaining_corpus.py` (pure stdlib + cmath) |
| Corpus | `android/format/ooxml/src/test/resources/r133/remaining_corpus.tsv` (47 data rows) |
| Gate | `android/tools/check_r133_remaining.py` |
| Kotlin test | `android/format/ooxml/src/test/kotlin/dev/fusion/ooxml/R133RemainingTest.kt` (15 @Tests, TOL=1e-12) |
| Census deepen | `android/tools/r133_census_deepen.py` |
| This doc | `android/docs/R133-RESULTS.md` |

## C. Gate result

`R133_REM_OK names=8 rows=47 tests=7 tolerance=1e-12`

All 7 rules pass:

- R133-1 registered — all 8 names dispatched + exported
- R133-2 covered — 47 rows, all 8 functions >= 3 non-refusal + >= 2 refusals
- R133-3 well-formed — all rows 3 columns
- R133-4 coherent — corpus byte-identical to generator output
- R133-5 reproducible — generator output deterministic
- R133-6 tested — 15 @Test methods, TOL=1e-12
- R133-7 no regression — R121-R132 gates all still pass

## D. Verification boundary

- **Host-verifiable**: Python corpus generator, gate, and census deepen all run locally and pass.
- **CI-only**: Kotlin compilation and `:format:ooxml:test` execution require Android SDK / Gradle / JDK, which are not present in this sandbox. The Kotlin test follows the exact R132 pattern and is syntactically consistent with existing tests.
- **No regressions**: R121-R132 gates all pass unchanged on this tree.

## E. Module census

| Metric | Value |
|---|---|
| Total modules | 50 |
| Testable (substantial subset) | 50/50 (100.00%) |
| Class A (full acceptance) | 0/50 (0.00%) |
| R133 module | `:format:ooxml` — testable, not yet Class A (full acceptance requires Android/CI receipts) |

## F. Files changed

- `android/format/ooxml/src/main/kotlin/dev/fusion/ooxml/FormulaEval.kt` (+R133 dispatch + implementations + SCOPE)
- `android/format/ooxml/src/main/kotlin/dev/fusion/ooxml/SpillSheetEngine.kt` (+_xlfn. exports)
- `android/tools/gen_r133_remaining_corpus.py` (new)
- `android/tools/check_r133_remaining.py` (new)
- `android/tools/r133_census_deepen.py` (new)
- `android/format/ooxml/src/test/kotlin/dev/fusion/ooxml/R133RemainingTest.kt` (new)
- `android/format/ooxml/src/test/resources/r133/remaining_corpus.tsv` (new)
- `android/docs/module-testability.json` (census pins updated)
- `android/docs/R133-RESULTS.md` (new)
