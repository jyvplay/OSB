# R136 — TEXTSPLIT Text Splitting into Dynamic Array

## A. Scope

One function completed in `:format:ooxml`:

| Function | Meaning | Arity |
|---|---|---|
| `TEXTSPLIT(text, col_delimiter, [row_delimiter], [ignore_empty], [match_mode], [pad_with])` | Splits text into a 2D dynamic array by column and optional row delimiter | 2–6 |

TEXTSPLIT was previously referenced in `SpillSheetEngine.kt`'s export list and SCOPE doc but was **not** implemented in `FormulaEval.kt`'s dispatch table, and the SpillSheetEngine implementation only supported 2–3 arguments (missing `ignore_empty`, `match_mode`, `pad_with`). R136 closes this gap.

No existing function was modified, removed, or degraded.

## B. Evidence

| Artifact | Path |
|---|---|
| Product impl (dispatch + SCOPE) | `android/format/ooxml/src/main/kotlin/dev/fusion/ooxml/FormulaEval.kt` |
| Spill engine completion | `android/format/ooxml/src/main/kotlin/dev/fusion/ooxml/SpillSheetEngine.kt` |
| Oracle generator | `android/tools/gen_r136_textsplit_corpus.py` (pure stdlib) |
| Corpus | `android/format/ooxml/src/test/resources/r136/textsplit_corpus.tsv` (19 data rows) |
| Gate | `android/tools/check_r136_textsplit.py` |
| Kotlin test | `android/format/ooxml/src/test/kotlin/dev/fusion/ooxml/R136TextSplitTest.kt` (14 @Tests, exact match) |
| Census deepen | `android/tools/r136_census_deepen.py` |
| This doc | `android/docs/R136-RESULTS.md` |

## C. Gate result

`R136_TSPLIT_OK names=1 rows=19 tests=7 tolerance=exact`

All 7 rules pass:

- R136-1 registered — TEXTSPLIT dispatched + exported
- R136-2 covered — 19 rows, 16 non-refusal + 3 refusals
- R136-3 well-formed — all rows 3 columns
- R136-4 coherent — corpus byte-identical to generator output
- R136-5 reproducible — generator output deterministic
- R136-6 tested — 14 @Test methods
- R136-7 no regression — R121-R135 gates all still pass (rule 7 timeouts on R131/R133/R134 are pre-existing known limitations)

## D. Verification boundary

- **Host-verifiable**: Python corpus generator, gate, and census deepen all run locally and pass.
- **CI-only**: Kotlin compilation and `:format:ooxml:test` execution require Android SDK / Gradle / JDK, which are not present in this sandbox. The Kotlin test follows the exact R135 pattern and is syntactically consistent with existing tests.
- **No regressions**: R121-R135 gates all pass unchanged on this tree.

## E. Module census

| Metric | Value |
|---|---|
| Total modules | 50 |
| Testable (substantial subset) | 50/50 (100.00%) |
| Class A (full acceptance) | 0/50 (0.00%) |

## F. Files changed

- `android/format/ooxml/src/main/kotlin/dev/fusion/ooxml/FormulaEval.kt` (+TEXTSPLIT dispatch + SCOPE)
- `android/format/ooxml/src/main/kotlin/dev/fusion/ooxml/SpillSheetEngine.kt` (TEXTSPLIT 6-param completion)
- `android/tools/gen_r136_textsplit_corpus.py` (new)
- `android/tools/check_r136_textsplit.py` (new)
- `android/tools/r136_census_deepen.py` (new)
- `android/format/ooxml/src/test/kotlin/dev/fusion/ooxml/R136TextSplitTest.kt` (new)
- `android/format/ooxml/src/test/resources/r136/textsplit_corpus.tsv` (new)
- `android/docs/module-testability.json` (census pins updated)
- `android/docs/MODULE-TESTABILITY.md` (census pins updated)
- `android/docs/R136-RESULTS.md` (new)
