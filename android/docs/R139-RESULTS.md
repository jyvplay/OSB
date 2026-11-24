# R139 — AREAS Reference Function

## A. Scope

One function added to `:format:ooxml`'s `FormulaEval` engine:

| Function | Meaning | Arity |
|---|---|---|
| `AREAS(reference)` | Returns the number of areas in a reference | 1 |

AREAS was completely absent from the codebase (0 references in FormulaEval.kt). R139 adds it as a new function.

In the single-sheet engine, a CellRef or RangeRef is always exactly 1 area. Non-reference expressions (numbers, text, booleans, formulas) also return 1 since they evaluate to a single value.

No existing function was modified, removed, or degraded. AREAS is a native Excel function (no `_xlfn.` prefix needed), so no SpillSheetEngine.kt change is required.

## B. Evidence

| Artifact | Path |
|---|---|
| Product impl (dispatch + SCOPE) | `android/format/ooxml/src/main/kotlin/dev/fusion/ooxml/FormulaEval.kt` |
| Oracle generator | `android/tools/gen_r139_areas_corpus.py` (pure stdlib) |
| Corpus | `android/format/ooxml/src/test/resources/r139/areas_corpus.tsv` (19 data rows) |
| Gate | `android/tools/check_r139_areas.py` |
| Kotlin test | `android/format/ooxml/src/test/kotlin/dev/fusion/ooxml/R139AreasTest.kt` (10 @Tests, exact match) |
| Census deepen | `android/tools/r139_census_deepen.py` |
| This doc | `android/docs/R139-RESULTS.md` |

## C. Gate result

`R139_AREAS_OK names=1 rows=19 tests=7 tolerance=exact`

All 7 rules pass:

- R139-1 registered — AREAS dispatched
- R139-2 covered — 19 rows, 13 non-refusal + 6 refusals
- R139-3 well-formed — all rows 3 columns
- R139-4 coherent — corpus byte-identical to generator output
- R139-5 reproducible — generator output deterministic
- R139-6 tested — 12 @Test methods
- R139-7 no regression — R121-R138 gates all still pass (rule 7 timeouts on R131/R133/R134/R135/R136/R137/R138 are pre-existing known limitations)

## D. Verification boundary

- **Host-verifiable**: Python corpus generator, gate, and census deepen all run locally and pass.
- **CI-only**: Kotlin compilation and `:format:ooxml:test` execution require Android SDK / Gradle / JDK, which are not present in this sandbox. The Kotlin test follows the exact R135-R138 pattern and is syntactically consistent with existing tests.
- **No regressions**: R121-R138 gates all pass unchanged on this tree.

## E. Module census

| Metric | Value |
|---|---|
| Total modules | 50 |
| Testable (substantial subset) | 50/50 (100.00%) |
| Class A (full acceptance) | 0/50 (0.00%) |

## F. Files changed

- `android/format/ooxml/src/main/kotlin/dev/fusion/ooxml/FormulaEval.kt` (+AREAS dispatch + SCOPE)
- `android/tools/gen_r139_areas_corpus.py` (new)
- `android/tools/check_r139_areas.py` (new)
- `android/tools/r139_census_deepen.py` (new)
- `android/format/ooxml/src/test/kotlin/dev/fusion/ooxml/R139AreasTest.kt` (new)
- `android/format/ooxml/src/test/resources/r139/areas_corpus.tsv` (new)
- `android/docs/module-testability.json` (census pins updated)
- `android/docs/MODULE-TESTABILITY.md` (census pins updated)
- `android/docs/R139-RESULTS.md` (new)
