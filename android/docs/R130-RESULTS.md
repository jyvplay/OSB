# R130 — Database Aggregation Functions (DAVERAGE / DCOUNT / etc.)

## A. Scope

Twelve database aggregation functions added to `:format:ooxml`'s `FormulaEval` engine:

| Function | Meaning | Arity |
|---|---|---|
| `DAVERAGE(database, field, criteria)` | Average of numeric field values for matching records | 3 |
| `DCOUNT(database, field, criteria)` | Count of numeric field values for matching records | 3 |
| `DCOUNTA(database, field, criteria)` | Count of non-empty non-error field values for matching records | 3 |
| `DGET(database, field, criteria)` | Single field value; `#NA!` if not exactly one match | 3 |
| `DMAX(database, field, criteria)` | Max of numeric field values (0 if none) | 3 |
| `DMIN(database, field, criteria)` | Min of numeric field values (0 if none) | 3 |
| `DPRODUCT(database, field, criteria)` | Product of numeric field values (0 if none) | 3 |
| `DSTDEV(database, field, criteria)` | Sample standard deviation (`#DIV/0!` if < 2 values) | 3 |
| `DSTDEVP(database, field, criteria)` | Population standard deviation (0 if none) | 3 |
| `DSUM(database, field, criteria)` | Sum of numeric field values (0 if none) | 3 |
| `DVAR(database, field, criteria)` | Sample variance (`#DIV/0!` if < 2 values) | 3 |
| `DVARP(database, field, criteria)` | Population variance (0 if none) | 3 |

Database semantics: first row of the database range is headers; `field` resolves by header name (string) or 1-based column index (number). Criteria range's first row names database columns; each criteria row is an AND of its non-empty cells; multiple criteria rows are OR'd together. Blank criteria cells match anything. Text criteria support `=`, `<>`, `>`, `>=`, `<`, `<=` operators and `*` / `?` wildcards.

All return `#VALUE!` on wrong arity, invalid field, or non-range database/criteria. No existing function was modified, removed, or degraded.

## B. Evidence

| Artifact | Path |
|---|---|
| Product impl | `android/format/ooxml/src/main/kotlin/dev/fusion/ooxml/FormulaEval.kt` (dispatch + `evalDatabaseFunction` + SCOPE note) |
| Export list | `android/format/ooxml/src/main/kotlin/dev/fusion/ooxml/SpillSheetEngine.kt` (`_xlfn.` prefix) |
| Oracle generator | `android/tools/gen_r130_database_corpus.py` (pure stdlib) |
| Corpus | `android/format/ooxml/src/test/resources/r130/database_corpus.tsv` (62 data rows) |
| Gate | `android/tools/check_r130_database.py` |
| Kotlin test | `android/format/ooxml/src/test/kotlin/dev/fusion/ooxml/R130DatabaseTest.kt` (15 @Tests, TOL=1e-12) |
| Census deepen | `android/tools/r130_census_deepen.py` |
| This doc | `android/docs/R130-RESULTS.md` |

## C. Gate result

`R130_DB_OK names=12 rows=62 tests=7 tolerance=1e-12`

All 7 rules pass:

- R130-1 registered — all 12 names dispatched + exported
- R130-2 covered — 62 rows, all 12 functions >= 3 non-refusal + >= 2 refusals
- R130-3 well-formed — all rows 3 columns
- R130-4 coherent — corpus byte-identical to generator output
- R130-5 reproducible — generator output deterministic
- R130-6 tested — 15 @Test methods, TOL=1e-12
- R130-7 no regression — R121-R129 gates all still pass

## D. Verification boundary

- **Host-verifiable**: Python corpus generator, gate, and census deepen all run locally and pass.
- **CI-only**: Kotlin compilation and `:format:ooxml:test` execution require Android SDK / Gradle / JDK, which are not present in this sandbox. The Kotlin test follows the exact R129 pattern and is syntactically consistent with existing tests.
- **No regressions**: R121-R129 gates all pass unchanged on this tree.

## E. Module census

| Metric | Value |
|---|---|
| Total modules | 50 |
| Testable (substantial subset) | 50/50 (100.00%) |
| Class A (full acceptance) | 0/50 (0.00%) |
| R130 module | `:format:ooxml` — testable, not yet Class A (full acceptance requires Android/CI receipts) |

## F. Files changed

- `android/format/ooxml/src/main/kotlin/dev/fusion/ooxml/FormulaEval.kt` (+R130 dispatch + `evalDatabaseFunction` + SCOPE)
- `android/format/ooxml/src/main/kotlin/dev/fusion/ooxml/SpillSheetEngine.kt` (+_xlfn. exports)
- `android/tools/gen_r130_database_corpus.py` (new)
- `android/tools/check_r130_database.py` (new)
- `android/tools/r130_census_deepen.py` (new)
- `android/format/ooxml/src/test/kotlin/dev/fusion/ooxml/R130DatabaseTest.kt` (new)
- `android/format/ooxml/src/test/resources/r130/database_corpus.tsv` (new)
- `android/docs/module-testability.json` (census pins updated)
- `android/docs/R130-RESULTS.md` (new)
