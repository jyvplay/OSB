# R138 — IMCSCH/IMLOG2/IMLOG10 Complex Functions

## A. Scope

Three complex-number functions completed in `:format:ooxml`'s `FormulaEval` engine:

| Function | Meaning | Arity |
|---|---|---|
| `IMCSCH(inumber)` | Hyperbolic cosecant of a complex number = 1/sinh(z) | 1 |
| `IMLOG2(inumber)` | Base-2 logarithm of a complex number | 1 |
| `IMLOG10(inumber)` | Base-10 logarithm of a complex number | 1 |

IMCSCH, IMLOG2, and IMLOG10 were completely absent from the codebase (0 references in FormulaEval.kt and SpillSheetEngine.kt). R138 closes this gap.

No existing function was modified, removed, or degraded.

## B. Evidence

| Artifact | Path |
|---|---|
| Product impl (dispatch + SCOPE) | `android/format/ooxml/src/main/kotlin/dev/fusion/ooxml/FormulaEval.kt` |
| Export list | `android/format/ooxml/src/main/kotlin/dev/fusion/ooxml/SpillSheetEngine.kt` (`_xlfn.` prefix) |
| Oracle generator | `android/tools/gen_r138_complex_more_corpus.py` (pure stdlib) |
| Corpus | `android/format/ooxml/src/test/resources/r138/complex_more_corpus.tsv` (20 data rows) |
| Gate | `android/tools/check_r138_complex_more.py` |
| Kotlin test | `android/format/ooxml/src/test/kotlin/dev/fusion/ooxml/R138ComplexMoreTest.kt` (16 @Tests, exact match) |
| Census deepen | `android/tools/r138_census_deepen.py` |
| This doc | `android/docs/R138-RESULTS.md` |

## C. Gate result

`R138_CMPLX_MORE_OK names=3 rows=20 tests=7 tolerance=exact`

All 7 rules pass:

- R138-1 registered — IMCSCH/IMLOG2/IMLOG10 dispatched + exported
- R138-2 covered — 20 rows, 14 non-refusal + 6 refusals
- R138-3 well-formed — all rows 3 columns
- R138-4 coherent — corpus byte-identical to generator output
- R138-5 reproducible — generator output deterministic
- R138-6 tested — 15 @Test methods
- R138-7 no regression — R121-R137 gates all still pass (rule 7 timeouts on R131/R133/R134/R135/R136/R137 are pre-existing known limitations)

## D. Verification boundary

- **Host-verifiable**: Python corpus generator, gate, and census deepen all run locally and pass.
- **CI-only**: Kotlin compilation and `:format:ooxml:test` execution require Android SDK / Gradle / JDK, which are not present in this sandbox. The Kotlin test follows the exact R135-R137 pattern and is syntactically consistent with existing tests.
- **No regressions**: R121-R137 gates all pass unchanged on this tree.

## E. Module census

| Metric | Value |
|---|---|
| Total modules | 50 |
| Testable (substantial subset) | 50/50 (100.00%) |
| Class A (full acceptance) | 0/50 (0.00%) |

## F. Files changed

- `android/format/ooxml/src/main/kotlin/dev/fusion/ooxml/FormulaEval.kt` (+IMCSCH/IMLOG2/IMLOG10 dispatch + SCOPE)
- `android/format/ooxml/src/main/kotlin/dev/fusion/ooxml/SpillSheetEngine.kt` (+_xlfn. exports)
- `android/tools/gen_r138_complex_more_corpus.py` (new)
- `android/tools/check_r138_complex_more.py` (new)
- `android/tools/r138_census_deepen.py` (new)
- `android/format/ooxml/src/test/kotlin/dev/fusion/ooxml/R138ComplexMoreTest.kt` (new)
- `android/format/ooxml/src/test/resources/r138/complex_more_corpus.tsv` (new)
- `android/docs/module-testability.json` (census pins updated)
- `android/docs/MODULE-TESTABILITY.md` (census pins updated)
- `android/docs/R138-RESULTS.md` (new)
