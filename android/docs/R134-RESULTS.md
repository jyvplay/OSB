# R134 — FORECAST.ETS Exponential Triple-Smoothing Forecasting Functions

## A. Scope

Four FORECAST.ETS functions added to `:format:ooxml`'s `FormulaEval` engine:

| Function | Meaning | Arity |
|---|---|---|
| `FORECAST.ETS(target, values, timeline, [seasonality], [data_completion], [aggregation])` | Point forecast at `target` using Holt-Winters triple exponential smoothing | 3–6 |
| `FORECAST.ETS.CONFINT(target, values, timeline, [confidence], [seasonality], [data_completion], [aggregation])` | Half-width of confidence interval for the forecast | 3–7 |
| `FORECAST.ETS.SEASONALITY(values, timeline, [data_completion], [aggregation])` | Detected seasonality period length via autocorrelation | 2–4 |
| `FORECAST.ETS.STAT(values, timeline, [seasonality], [data_completion], [aggregation], stat_type)` | ETS statistic: 1=alpha, 2=beta, 3=gamma, 4=MASE, 5=SMSE, 6=SMPE | 5–6 |

FORECAST.ETS uses additive seasonality with fixed smoothing parameters (alpha=0.3, beta=0.1, gamma=0.1).
When seasonality exceeds n/2, falls back to linear regression. FORECAST.ETS.CONFINT returns half-width
that grows with forecast distance using RMSE and inverse normal CDF. All return `#VALUE!` on wrong
arity, `#NUM!` on invalid inputs (confidence outside (0,1), stat_type outside 1-6, seasonality <= 0).
No existing function was modified, removed, or degraded.

## B. Evidence

| Artifact | Path |
|---|---|
| Product impl | `android/format/ooxml/src/main/kotlin/dev/fusion/ooxml/FormulaEval.kt` (dispatch + `evalForecastEts` + helpers + SCOPE note) |
| Export list | `android/format/ooxml/src/main/kotlin/dev/fusion/ooxml/SpillSheetEngine.kt` (`_xlfn.` prefix) |
| Oracle generator | `android/tools/gen_r134_forecast_ets_corpus.py` (pure stdlib) |
| Corpus | `android/format/ooxml/src/test/resources/r134/forecast_ets_corpus.tsv` (22 data rows) |
| Gate | `android/tools/check_r134_forecast_ets.py` |
| Kotlin test | `android/format/ooxml/src/test/kotlin/dev/fusion/ooxml/R134ForecastEtsTest.kt` (17 @Tests, TOL=1e-12) |
| Census deepen | `android/tools/r134_census_deepen.py` |
| This doc | `android/docs/R134-RESULTS.md` |

## C. Gate result

`R134_ETS_OK names=4 rows=22 tests=7 tolerance=1e-12`

All 7 rules pass:

- R134-1 registered — all 4 names dispatched + exported
- R134-2 covered — 22 rows, all 4 functions >= 3 non-refusal + >= 2 refusals
- R134-3 well-formed — all rows 3 columns
- R134-4 coherent — corpus byte-identical to generator output
- R134-5 reproducible — generator output deterministic
- R134-6 tested — 17 @Test methods, TOL=1e-12
- R134-7 no regression — R121-R133 gates all still pass

## D. Verification boundary

- **Host-verifiable**: Python corpus generator, gate, and census deepen all run locally and pass.
- **CI-only**: Kotlin compilation and `:format:ooxml:test` execution require Android SDK / Gradle / JDK, which are not present in this sandbox. The Kotlin test follows the exact R133 pattern and is syntactically consistent with existing tests.
- **No regressions**: R121-R133 gates all pass unchanged on this tree.

## E. Module census

| Metric | Value |
|---|---|
| Total modules | 50 |
| Testable (substantial subset) | 50/50 (100.00%) |
| Class A (full acceptance) | 0/50 (0.00%) |
| R134 module | `:format:ooxml` — testable, not yet Class A (full acceptance requires Android/CI receipts) |

## F. Files changed

- `android/format/ooxml/src/main/kotlin/dev/fusion/ooxml/FormulaEval.kt` (+R134 dispatch + `evalForecastEts` + SCOPE)
- `android/format/ooxml/src/main/kotlin/dev/fusion/ooxml/SpillSheetEngine.kt` (+_xlfn. exports)
- `android/tools/gen_r134_forecast_ets_corpus.py` (new)
- `android/tools/check_r134_forecast_ets.py` (new)
- `android/tools/r134_census_deepen.py` (new)
- `android/format/ooxml/src/test/kotlin/dev/fusion/ooxml/R134ForecastEtsTest.kt` (new)
- `android/format/ooxml/src/test/resources/r134/forecast_ets_corpus.tsv` (new)
- `android/docs/module-testability.json` (census pins updated)
- `android/docs/R134-RESULTS.md` (new)
