# R127 — DOLLAR / FIXED / TIMEVALUE / COLUMN / ROW / COLUMNS / ROWS

## A. Scope

Seven text, time, and reference functions added to `:format:ooxml`'s `FormulaEval` engine:

| Function | Meaning | Arity |
|---|---|---|
| `DOLLAR(number, [decimals])` | Currency text with `$` prefix and fixed decimals | 1–2 |
| `FIXED(number, [decimals], [no_commas])` | Fixed-decimal text, optional comma suppression | 1–3 |
| `TIMEVALUE(time_text)` | Fraction of a day from `"HH:MM:SS"` / `"HH:MM"` | 1 |
| `COLUMN([ref])` | 1-based column index of a cell reference | 1 |
| `ROW([ref])` | 1-based row index of a cell reference | 1 |
| `COLUMNS(array)` | Number of columns in a reference or array | 1 |
| `ROWS(array)` | Number of rows in a reference or array | 1 |

All seven return `#VALUE!` on wrong arity or non-coercible arguments, and `#NUM!` on out-of-range time components. No existing function was modified, removed, or degraded.

## B. Evidence

| Artifact | Path |
|---|---|
| Product impl | `android/format/ooxml/src/main/kotlin/dev/fusion/ooxml/FormulaEval.kt` (dispatch + `formatWithCommas` helper + SCOPE note) |
| Export list | `android/format/ooxml/src/main/kotlin/dev/fusion/ooxml/SpillSheetEngine.kt` (`_xlfn.` prefix) |
| Oracle generator | `android/tools/gen_r127_textref_corpus.py` (pure stdlib) |
| Corpus | `android/format/ooxml/src/test/resources/r127/textref_corpus.tsv` (51 data rows) |
| Gate | `android/tools/check_r127_textref.py` |
| Kotlin test | `android/format/ooxml/src/test/kotlin/dev/fusion/ooxml/R127TextRefTest.kt` (15 @Tests, TOL=1e-12) |
| Census deepen | `android/tools/r127_census_deepen.py` |
| This doc | `android/docs/R127-RESULTS.md` |

## C. Gate result

`R127_TEXT_OK names=7 rows=51 tests=7 tolerance=1e-12`

All 7 rules pass:
- R127-1 registered — all 7 names dispatched + exported
- R127-2 covered — 51 rows, every function ≥ 3 numeric + ≥ 2 refusals
- R127-3 well-formed — all rows 3 columns, numeric or refusal (DOLLAR/FIXED text accepted)
- R127-4 coherent — corpus byte-identical to generator output
- R127-5 reproducible — generator output deterministic
- R127-6 tested — 15 @Test methods, TOL=1e-12
- R127-7 no regression — R121-R126 gates all still pass

## D. Verification boundary

- **Host-verifiable**: Python corpus generator, gate, and census deepen all run locally and pass.
- **CI-only**: Kotlin compilation and `:format:ooxml:test` execution require Android SDK / Gradle / JDK, which are not present in this sandbox. The Kotlin test follows the exact R126 pattern and is syntactically consistent with existing tests.
- **No regressions**: R121-R126 gates all pass unchanged on this tree.

## E. Module census

| Metric | Value |
|---|---|
| Total modules | 50 |
| Testable (substantial subset) | 50/50 (100.00%) |
| Class A (full acceptance) | 0/50 (0.00%) |
| R127 module | `:format:ooxml` — testable, not yet Class A (full acceptance requires Android/CI receipts) |

## F. Files changed

- `android/format/ooxml/src/main/kotlin/dev/fusion/ooxml/FormulaEval.kt` (+R127 dispatch + helper + SCOPE)
- `android/format/ooxml/src/main/kotlin/dev/fusion/ooxml/SpillSheetEngine.kt` (+_xlfn. exports)
- `android/tools/gen_r127_textref_corpus.py` (new)
- `android/tools/check_r127_textref.py` (new)
- `android/tools/r127_census_deepen.py` (new)
- `android/format/ooxml/src/test/kotlin/dev/fusion/ooxml/R127TextRefTest.kt` (new)
- `android/format/ooxml/src/test/resources/r127/textref_corpus.tsv` (new)
- `android/docs/module-testability.json` (census pins updated)
- `android/docs/R127-RESULTS.md` (new)
