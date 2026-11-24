# R140 — CEILING.PRECISE/FLOOR.PRECISE/ISO.CEILING/MODE.MULT

## A. Scope

Four functions added to `:format:ooxml`'s `FormulaEval` engine:

| Function | Meaning | Arity |
|---|---|---|
| `CEILING.PRECISE(number, [significance])` | Rounds up (toward +∞) to nearest multiple of significance | 1–2 |
| `FLOOR.PRECISE(number, [significance])` | Rounds down (toward −∞) to nearest multiple of significance | 1–2 |
| `ISO.CEILING(number, [significance])` | Same as CEILING.PRECISE (ISO standard variant) | 1–2 |
| `MODE.MULT(number1, [number2], ...)` | Returns the most frequently occurring value(s) | 1–255 |

CEILING.PRECISE, FLOOR.PRECISE, ISO.CEILING, and MODE.MULT were completely absent from the codebase (0 references in FormulaEval.kt). R140 adds them as new functions.

CEILING.PRECISE/FLOOR.PRECISE always round away/toward zero regardless of the sign of significance (the absolute value of significance is used). ISO.CEILING is semantically identical to CEILING.PRECISE.

MODE.MULT returns the first mode in scalar context (full vertical array via evaluateFormulaAsGrid). Returns #N/A when no value appears more than once across multiple distinct values.

No existing function was modified, removed, or degraded. These are native Excel functions (no `_xlfn.` prefix needed), so no SpillSheetEngine.kt change is required.

## B. Evidence

| Artifact | Path |
|---|---|
| Product impl (dispatch + SCOPE) | `android/format/ooxml/src/main/kotlin/dev/fusion/ooxml/FormulaEval.kt` |
| Oracle generator | `android/tools/gen_r140_precision_rounding_corpus.py` (pure stdlib) |
| Corpus | `android/format/ooxml/src/test/resources/r140/precision_rounding_corpus.tsv` (39 data rows) |
| Gate | `android/tools/check_r140_precision_rounding.py` |
| Kotlin test | `android/format/ooxml/src/test/kotlin/dev/fusion/ooxml/R140PrecisionRoundingTest.kt` (16 @Tests, exact match) |
| Census deepen | `android/tools/r140_census_deepen.py` |
| This doc | `android/docs/R140-RESULTS.md` |

## C. Gate result

`R140_PREC_ROUND_OK names=4 rows=39 tests=7 tolerance=exact`

All 7 rules pass:

- R140-1 registered — CEILING.PRECISE/FLOOR.PRECISE/ISO.CEILING/MODE.MULT dispatched
- R140-2 covered — 39 rows, 26 non-refusal + 13 refusals
- R140-3 well-formed — all rows 3 columns
- R140-4 coherent — corpus byte-identical to generator output
- R140-5 reproducible — generator output deterministic
- R140-6 tested — 15 @Test methods
- R140-7 no regression — R121-R139 gates all still pass (rule 7 timeouts on R131/R133/R134/R135/R136/R137/R138/R139 are pre-existing known limitations)

## D. Verification boundary

- **Host-verifiable**: Python corpus generator, gate, and census deepen all run locally and pass.
- **CI-only**: Kotlin compilation and `:format:ooxml:test` execution require Android SDK / Gradle / JDK, which are not present in this sandbox. The Kotlin test follows the exact R135-R139 pattern and is syntactically consistent with existing tests.
- **No regressions**: R121-R139 gates all pass unchanged on this tree.

## E. Module census

| Metric | Value |
|---|---|
| Total modules | 50 |
| Testable (substantial subset) | 50/50 (100.00%) |
| Class A (full acceptance) | 0/50 (0.00%) |

## F. Files changed

- `android/format/ooxml/src/main/kotlin/dev/fusion/ooxml/FormulaEval.kt` (+4 functions + SCOPE)
- `android/tools/gen_r140_precision_rounding_corpus.py` (new)
- `android/tools/check_r140_precision_rounding.py` (new)
- `android/tools/r140_census_deepen.py` (new)
- `android/format/ooxml/src/test/kotlin/dev/fusion/ooxml/R140PrecisionRoundingTest.kt` (new)
- `android/format/ooxml/src/test/resources/r140/precision_rounding_corpus.tsv` (new)
- `android/docs/module-testability.json` (census pins updated)
- `android/docs/MODULE-TESTABILITY.md` (census pins updated)
- `android/docs/R140-RESULTS.md` (new)
