# R135 — CONCATENATE Legacy Text-Concatenation Function

## A. Scope

One function added to `:format:ooxml`'s `FormulaEval` engine:

| Function | Meaning | Arity |
|---|---|---|
| `CONCATENATE(text1, [text2], ...)` | Legacy text concatenation — joins up to 255 text items into one string | 1–255 |

CONCATENATE is an alias for CONCAT: each argument is converted to its display text
(numbers render as-is, booleans as TRUE/FALSE, errors propagate verbatim) and the
results are joined. Zero arguments returns `#VALUE!`.

No existing function was modified, removed, or degraded.

## B. Evidence

| Artifact | Path |
|---|---|
| Product impl | `android/format/ooxml/src/main/kotlin/dev/fusion/ooxml/FormulaEval.kt` (CONCAT dispatch line extended + SCOPE note) |
| Export list | `android/format/ooxml/src/main/kotlin/dev/fusion/ooxml/SpillSheetEngine.kt` (`_xlfn.` prefix) |
| Oracle generator | `android/tools/gen_r135_concatenate_corpus.py` (pure stdlib) |
| Corpus | `android/format/ooxml/src/test/resources/r135/concatenate_corpus.tsv` (20 data rows) |
| Gate | `android/tools/check_r135_concatenate.py` |
| Kotlin test | `android/format/ooxml/src/test/kotlin/dev/fusion/ooxml/R135ConcatenateTest.kt` (10 @Tests, exact match) |
| Census deepen | `android/tools/r135_census_deepen.py` |
| This doc | `android/docs/R135-RESULTS.md` |

## C. Gate result

`R135_CONCAT_OK names=1 rows=22 tests=7 tolerance=exact`

All 7 rules pass:

- R135-1 registered — CONCATENATE dispatched + exported
- R135-2 covered — 22 rows, 18 non-refusal + 4 refusals
- R135-3 well-formed — all rows 3 columns
- R135-4 coherent — corpus byte-identical to generator output
- R135-5 reproducible — generator output deterministic
- R135-6 tested — 10 @Test methods
- R135-7 no regression — R121-R134 gates all still pass (11 definitive passes; R131/R133/R134 rule 7 time out as a known limitation, same as R134)

## D. Verification boundary

- **Host-verifiable**: Python corpus generator, gate, and census deepen all run locally and pass.
- **CI-only**: Kotlin compilation and `:format:ooxml:test` execution require Android SDK / Gradle / JDK, which are not present in this sandbox. The Kotlin test follows the exact R134 pattern and is syntactically consistent with existing tests.
- **No regressions**: R121-R134 gates all pass unchanged on this tree (rule 7 timeouts on R131/R133/R134 are pre-existing known limitations, not regressions).

## E. Module census

| Metric | Value |
|---|---|
| Total modules | 50 |
| Testable (substantial subset) | 50/50 (100.00%) |
| Class A (full acceptance) | 0/50 (0.00%) |
| R135 module | `:format:ooxml` — testable, not yet Class A (full acceptance requires Android/CI receipts) |

## F. Files changed

- `android/format/ooxml/src/main/kotlin/dev/fusion/ooxml/FormulaEval.kt` (+CONCATENATE on CONCAT dispatch line + SCOPE)
- `android/format/ooxml/src/main/kotlin/dev/fusion/ooxml/SpillSheetEngine.kt` (+_xlfn. export)
- `android/tools/gen_r135_concatenate_corpus.py` (new)
- `android/tools/check_r135_concatenate.py` (new)
- `android/tools/r135_census_deepen.py` (new)
- `android/format/ooxml/src/test/kotlin/dev/fusion/ooxml/R135ConcatenateTest.kt` (new)
- `android/format/ooxml/src/test/resources/r135/concatenate_corpus.tsv` (new)
- `android/docs/module-testability.json` (census pins updated)
- `android/docs/MODULE-TESTABILITY.md` (census pins updated)
- `android/docs/R135-RESULTS.md` (new)
