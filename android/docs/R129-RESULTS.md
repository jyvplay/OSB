# R129 — SHEET / SHEETS Sheet Reference Functions

## A. Scope

Two sheet reference functions added to `:format:ooxml`'s `FormulaEval` engine:

| Function | Meaning | Arity |
|---|---|---|
| `SHEET([value])` | Returns the sheet number of the referenced cell. Single-sheet engine always returns 1. Any argument is accepted. | 0–1 |
| `SHEETS([ref])` | Returns the number of sheets in the reference. Single-sheet engine always returns 1. Any reference is accepted. | 0–1 |

Both return `#VALUE!` on wrong arity (>1 argument). No existing function was modified, removed, or degraded.

## B. Evidence

| Artifact | Path |
|---|---|
| Product impl | `android/format/ooxml/src/main/kotlin/dev/fusion/ooxml/FormulaEval.kt` (dispatch + SCOPE note) |
| Export list | `android/format/ooxml/src/main/kotlin/dev/fusion/ooxml/SpillSheetEngine.kt` (`_xlfn.` prefix) |
| Oracle generator | `android/tools/gen_r129_sheet_corpus.py` (pure stdlib) |
| Corpus | `android/format/ooxml/src/test/resources/r129/sheet_corpus.tsv` (11 data rows) |
| Gate | `android/tools/check_r129_sheet.py` |
| Kotlin test | `android/format/ooxml/src/test/kotlin/dev/fusion/ooxml/R129SheetTest.kt` (10 @Tests, TOL=1e-12) |
| Census deepen | `android/tools/r129_census_deepen.py` |
| This doc | `android/docs/R129-RESULTS.md` |

## C. Gate result

`R129_SHEET_OK names=2 rows=11 tests=7 tolerance=1e-12`

All 7 rules pass:

- R129-1 registered — SHEET and SHEETS dispatched + exported
- R129-2 covered — 11 rows, both functions >= 3 non-refusal + >= 2 refusals
- R129-3 well-formed — all rows 3 columns
- R129-4 coherent — corpus byte-identical to generator output
- R129-5 reproducible — generator output deterministic
- R129-6 tested — 10 @Test methods, TOL=1e-12
- R129-7 no regression — R121-R128 gates all still pass

## D. Verification boundary

- **Host-verifiable**: Python corpus generator, gate, and census deepen all run locally and pass.
- **CI-only**: Kotlin compilation and `:format:ooxml:test` execution require Android SDK / Gradle / JDK, which are not present in this sandbox. The Kotlin test follows the exact R128 pattern and is syntactically consistent with existing tests.
- **No regressions**: R121-R128 gates all pass unchanged on this tree.

## E. Module census

| Metric | Value |
|---|---|
| Total modules | 50 |
| Testable (substantial subset) | 50/50 (100.00%) |
| Class A (full acceptance) | 0/50 (0.00%) |
| R129 module | `:format:ooxml` — testable, not yet Class A (full acceptance requires Android/CI receipts) |

## F. Files changed

- `android/format/ooxml/src/main/kotlin/dev/fusion/ooxml/FormulaEval.kt` (+R129 dispatch + SCOPE)
- `android/format/ooxml/src/main/kotlin/dev/fusion/ooxml/SpillSheetEngine.kt` (+_xlfn. exports)
- `android/tools/gen_r129_sheet_corpus.py` (new)
- `android/tools/check_r129_sheet.py` (new)
- `android/tools/r129_census_deepen.py` (new)
- `android/format/ooxml/src/test/kotlin/dev/fusion/ooxml/R129SheetTest.kt` (new)
- `android/format/ooxml/src/test/resources/r129/sheet_corpus.tsv` (new)
- `android/docs/module-testability.json` (census pins updated)
- `android/docs/R129-RESULTS.md` (new)
