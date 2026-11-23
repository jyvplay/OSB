# R128 — T / N Value Conversion Functions

## A. Scope

Two value conversion functions added to `:format:ooxml`'s `FormulaEval` engine:

| Function | Meaning | Arity |
|---|---|---|
| `T(value)` | Returns the text if value is text, otherwise empty string | 1 |
| `N(value)` | Returns the number if value is a number, 1 for TRUE, 0 for FALSE/empty/unparseable text, propagates errors | 1 |

Both return `#VALUE!` on wrong arity. No existing function was modified, removed, or degraded.

## B. Evidence

| Artifact | Path |
|---|---|
| Product impl | `android/format/ooxml/src/main/kotlin/dev/fusion/ooxml/FormulaEval.kt` (dispatch + SCOPE note) |
| Export list | `android/format/ooxml/src/main/kotlin/dev/fusion/ooxml/SpillSheetEngine.kt` (`_xlfn.` prefix) |
| Oracle generator | `android/tools/gen_r128_textnum_corpus.py` (pure stdlib) |
| Corpus | `android/format/ooxml/src/test/resources/r128/textnum_corpus.tsv` (20 data rows) |
| Gate | `android/tools/check_r128_textnum.py` |
| Kotlin test | `android/format/ooxml/src/test/kotlin/dev/fusion/ooxml/R128TextNumTest.kt` (11 @Tests, TOL=1e-12) |
| Census deepen | `android/tools/r128_census_deepen.py` |
| This doc | `android/docs/R128-RESULTS.md` |

## C. Gate result

`R128_TEXT_OK names=2 rows=20 tests=7 tolerance=1e-12`

All 7 rules pass:
- R128-1 registered — T and N dispatched + exported
- R128-2 covered — 20 rows, both functions >= 3 non-refusal + >= 2 refusals
- R128-3 well-formed — all rows 3 columns
- R128-4 coherent — corpus byte-identical to generator output
- R128-5 reproducible — generator output deterministic
- R128-6 tested — 11 @Test methods, TOL=1e-12
- R128-7 no regression — R121-R127 gates all still pass

## D. Verification boundary

- **Host-verifiable**: Python corpus generator, gate, and census deepen all run locally and pass.
- **CI-only**: Kotlin compilation and `:format:ooxml:test` execution require Android SDK / Gradle / JDK, which are not present in this sandbox. The Kotlin test follows the exact R127 pattern and is syntactically consistent with existing tests.
- **No regressions**: R121-R127 gates all pass unchanged on this tree.

## E. Module census

| Metric | Value |
|---|---|
| Total modules | 50 |
| Testable (substantial subset) | 50/50 (100.00%) |
| Class A (full acceptance) | 0/50 (0.00%) |
| R128 module | `:format:ooxml` — testable, not yet Class A (full acceptance requires Android/CI receipts) |

## F. Files changed

- `android/format/ooxml/src/main/kotlin/dev/fusion/ooxml/FormulaEval.kt` (+R128 dispatch + SCOPE)
- `android/format/ooxml/src/main/kotlin/dev/fusion/ooxml/SpillSheetEngine.kt` (+_xlfn. exports)
- `android/tools/gen_r128_textnum_corpus.py` (new)
- `android/tools/check_r128_textnum.py` (new)
- `android/tools/r128_census_deepen.py` (new)
- `android/format/ooxml/src/test/kotlin/dev/fusion/ooxml/R128TextNumTest.kt` (new)
- `android/format/ooxml/src/test/resources/r128/textnum_corpus.tsv` (new)
- `android/docs/module-testability.json` (census pins updated)
- `android/docs/R128-RESULTS.md` (new)
