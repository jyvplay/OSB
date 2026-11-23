# R131 — Financial Functions (AMORDEGRC / AMORLINC / DB / ISPMT / PRICE / PRICEMAT / YIELD / YIELDMAT)

## A. Scope

Eight financial functions added to `:format:ooxml`'s `FormulaEval` engine:

| Function | Meaning | Arity |
|---|---|---|
| `AMORDEGRC(cost, date_purchased, first_period, salvage, period, rate, [basis])` | French declining-balance amortization with coefficient | 6–7 |
| `AMORLINC(cost, date_purchased, first_period, salvage, period, rate, [basis])` | French linear amortization | 6–7 |
| `DB(cost, salvage, life, period, [month])` | Fixed-declining balance depreciation | 4–5 |
| `ISPMT(rate, per, nper, pv)` | Interest payment for a given period | 4 |
| `PRICE(settlement, maturity, rate, yld, redemption, frequency, [basis])` | Bond price per 100 face value | 6–7 |
| `PRICEMAT(settlement, maturity, issue, rate, yld, [basis])` | Bond price at maturity | 5–6 |
| `YIELD(settlement, maturity, rate, pr, redemption, frequency, [basis])` | Bond yield | 6–7 |
| `YIELDMAT(settlement, maturity, issue, rate, pr, [basis])` | Bond yield at maturity | 5–6 |

Day-count basis: 0=US(NASD)30/360, 1=actual/actual, 2=actual/360, 3=actual/365, 4=European 30/360.
All return `#VALUE!` on wrong arity, `#NUM!` on invalid inputs (negative cost/salvage, life<=0, period<1 or >life, settle>=maturity, invalid frequency/basis, rate<=0 for AMOR*). No existing function was modified, removed, or degraded.

## B. Evidence

| Artifact | Path |
|---|---|
| Product impl | `android/format/ooxml/src/main/kotlin/dev/fusion/ooxml/FormulaEval.kt` (dispatch + `evalR131FinancialFunction` + SCOPE note) |
| Export list | `android/format/ooxml/src/main/kotlin/dev/fusion/ooxml/SpillSheetEngine.kt` (`_xlfn.` prefix) |
| Oracle generator | `android/tools/gen_r131_financial_corpus.py` (pure stdlib) |
| Corpus | `android/format/ooxml/src/test/resources/r131/financial_corpus.tsv` (32 data rows) |
| Gate | `android/tools/check_r131_financial.py` |
| Kotlin test | `android/format/ooxml/src/test/kotlin/dev/fusion/ooxml/R131FinancialTest.kt` (13 @Tests, TOL=1e-12) |
| Census deepen | `android/tools/r131_census_deepen.py` |
| This doc | `android/docs/R131-RESULTS.md` |

## C. Gate result

`R131_FIN_OK names=8 rows=32 tests=7 tolerance=1e-12`

All 7 rules pass:

- R131-1 registered — all 8 names dispatched + exported
- R131-2 covered — 32 rows, all 8 functions >= 3 non-refusal + >= 2 refusals
- R131-3 well-formed — all rows 3 columns
- R131-4 coherent — corpus byte-identical to generator output
- R131-5 reproducible — generator output deterministic
- R131-6 tested — 13 @Test methods, TOL=1e-12
- R131-7 no regression — R121-R130 gates all still pass

## D. Verification boundary

- **Host-verifiable**: Python corpus generator, gate, and census deepen all run locally and pass.
- **CI-only**: Kotlin compilation and `:format:ooxml:test` execution require Android SDK / Gradle / JDK, which are not present in this sandbox. The Kotlin test follows the exact R130 pattern and is syntactically consistent with existing tests.
- **No regressions**: R121-R130 gates all pass unchanged on this tree.

## E. Module census

| Metric | Value |
|---|---|
| Total modules | 50 |
| Testable (substantial subset) | 50/50 (100.00%) |
| Class A (full acceptance) | 0/50 (0.00%) |
| R131 module | `:format:ooxml` — testable, not yet Class A (full acceptance requires Android/CI receipts) |

## F. Files changed

- `android/format/ooxml/src/main/kotlin/dev/fusion/ooxml/FormulaEval.kt` (+R131 dispatch + `evalR131FinancialFunction` + SCOPE)
- `android/format/ooxml/src/main/kotlin/dev/fusion/ooxml/SpillSheetEngine.kt` (+_xlfn. exports)
- `android/tools/gen_r131_financial_corpus.py` (new)
- `android/tools/check_r131_financial.py` (new)
- `android/tools/r131_census_deepen.py` (new)
- `android/format/ooxml/src/test/kotlin/dev/fusion/ooxml/R131FinancialTest.kt` (new)
- `android/format/ooxml/src/test/resources/r131/financial_corpus.tsv` (new)
- `android/docs/module-testability.json` (census pins updated)
- `android/docs/R131-RESULTS.md` (new)
