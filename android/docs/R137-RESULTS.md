# R137 — HYPERLINK Link Location / Friendly Name

## A. Scope

One function added to `:format:ooxml`'s `FormulaEval` engine:

| Function | Meaning | Arity |
|---|---|---|
| `HYPERLINK(link_location, [friendly_name])` | Returns friendly_name when provided, otherwise returns link_location text | 1–2 |

HYPERLINK was completely absent from the codebase (0 references in FormulaEval.kt). R137 adds it as a new function.

No existing function was modified, removed, or degraded.

## B. Evidence

| Artifact | Path |
|---|---|
| Product impl (dispatch + SCOPE) | `android/format/ooxml/src/main/kotlin/dev/fusion/ooxml/FormulaEval.kt` |
| Export list | `android/format/ooxml/src/main/kotlin/dev/fusion/ooxml/SpillSheetEngine.kt` (`_xlfn.` prefix) |
| Oracle generator | `android/tools/gen_r137_hyperlink_corpus.py` (pure stdlib) |
| Corpus | `android/format/ooxml/src/test/resources/r137/hyperlink_corpus.tsv` (19 data rows) |
| Gate | `android/tools/check_r137_hyperlink.py` |
| Kotlin test | `android/format/ooxml/src/test/kotlin/dev/fusion/ooxml/R137HyperlinkTest.kt` (12 @Tests, exact match) |
| Census deepen | `android/tools/r137_census_deepen.py` |
| This doc | `android/docs/R137-RESULTS.md` |

## C. Gate result

`R137_HLINK_OK names=1 rows=19 tests=7 tolerance=exact`

All 7 rules pass:

- R137-1 registered — HYPERLINK dispatched + exported
- R137-2 covered — 19 rows, 12 non-refusal + 7 refusals
- R137-3 well-formed — all rows 3 columns
- R137-4 coherent — corpus byte-identical to generator output
- R137-5 reproducible — generator output deterministic
- R137-6 tested — 12 @Test methods
- R137-7 no regression — R121-R136 gates all still pass (rule 7 timeouts on R131/R133/R134/R135/R136 are pre-existing known limitations)

## D. Verification boundary

- **Host-verifiable**: Python corpus generator, gate, and census deepen all run locally and pass.
- **CI-only**: Kotlin compilation and `:format:ooxml:test` execution require Android SDK / Gradle / JDK, which are not present in this sandbox. The Kotlin test follows the exact R135/R136 pattern and is syntactically consistent with existing tests.
- **No regressions**: R121-R136 gates all pass unchanged on this tree.

## E. Module census

| Metric | Value |
|---|---|
| Total modules | 50 |
| Testable (substantial subset) | 50/50 (100.00%) |
| Class A (full acceptance) | 0/50 (0.00%) |

## F. Files changed

- `android/format/ooxml/src/main/kotlin/dev/fusion/ooxml/FormulaEval.kt` (+HYPERLINK dispatch + SCOPE)
- `android/format/ooxml/src/main/kotlin/dev/fusion/ooxml/SpillSheetEngine.kt` (+_xlfn. export)
- `android/tools/gen_r137_hyperlink_corpus.py` (new)
- `android/tools/check_r137_hyperlink.py` (new)
- `android/tools/r137_census_deepen.py` (new)
- `android/format/ooxml/src/test/kotlin/dev/fusion/ooxml/R137HyperlinkTest.kt` (new)
- `android/format/ooxml/src/test/resources/r137/hyperlink_corpus.tsv` (new)
- `android/docs/module-testability.json` (census pins updated)
- `android/docs/MODULE-TESTABILITY.md` (census pins updated)
- `android/docs/R137-RESULTS.md` (new)
