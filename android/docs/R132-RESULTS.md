# R132 — Complex Trigonometric/Hyperbolic Functions (IMCSC / IMSEC / IMSECH / IMSINH / IMTAN)

## A. Scope

Five complex functions added to `:format:ooxml`'s `FormulaEval` engine:

| Function | Meaning | Arity |
|---|---|---|
| `IMCSC(inumber)` | Cosecant of a complex number: 1/sin(z) | 1 |
| `IMSEC(inumber)` | Secant of a complex number: 1/cos(z) | 1 |
| `IMSECH(inumber)` | Hyperbolic secant of a complex number: 1/cosh(z) | 1 |
| `IMSINH(inumber)` | Hyperbolic sine of a complex number: sinh(z) | 1 |
| `IMTAN(inumber)` | Tangent of a complex number: tan(z) | 1 |

All accept complex numbers in `x+yi` or `x+yj` format and return complex numbers in the same format.
Return `#VALUE!` on wrong arity, `#NUM!` on non-complex input, `#DIV/0!` on singular results (e.g. IMCSC(0)).
No existing function was modified, removed, or degraded.

## B. Evidence

| Artifact | Path |
|---|---|
| Product impl | `android/format/ooxml/src/main/kotlin/dev/fusion/ooxml/FormulaEval.kt` (dispatch + complex branch + SCOPE note) |
| Export list | `android/format/ooxml/src/main/kotlin/dev/fusion/ooxml/SpillSheetEngine.kt` (`_xlfn.` prefix) |
| Oracle generator | `android/tools/gen_r132_complex_corpus.py` (pure stdlib + cmath) |
| Corpus | `android/format/ooxml/src/test/resources/r132/complex_corpus.tsv` (48 data rows) |
| Gate | `android/tools/check_r132_complex.py` |
| Kotlin test | `android/format/ooxml/src/test/kotlin/dev/fusion/ooxml/R132ComplexTest.kt` (11 @Tests, TOL=1e-12) |
| Census deepen | `android/tools/r132_census_deepen.py` |
| This doc | `android/docs/R132-RESULTS.md` |

## C. Gate result

`R132_CMPLX_OK names=5 rows=48 tests=7 tolerance=1e-12`

All 7 rules pass:

- R132-1 registered — all 5 names dispatched + exported
- R132-2 covered — 48 rows, all 5 functions >= 3 non-refusal + >= 2 refusals
- R132-3 well-formed — all rows 3 columns
- R132-4 coherent — corpus byte-identical to generator output
- R132-5 reproducible — generator output deterministic
- R132-6 tested — 11 @Test methods, TOL=1e-12
- R132-7 no regression — R121-R131 gates all still pass

## D. Verification boundary

- **Host-verifiable**: Python corpus generator, gate, and census deepen all run locally and pass.
- **CI-only**: Kotlin compilation and `:format:ooxml:test` execution require Android SDK / Gradle / JDK, which are not present in this sandbox. The Kotlin test follows the exact R130 pattern and is syntactically consistent with existing tests.
- **No regressions**: R121-R131 gates all pass unchanged on this tree.

## E. Module census

| Metric | Value |
|---|---|
| Total modules | 50 |
| Testable (substantial subset) | 50/50 (100.00%) |
| Class A (full acceptance) | 0/50 (0.00%) |
| R132 module | `:format:ooxml` — testable, not yet Class A (full acceptance requires Android/CI receipts) |

## F. Files changed

- `android/format/ooxml/src/main/kotlin/dev/fusion/ooxml/FormulaEval.kt` (+R132 dispatch + complex impl + SCOPE)
- `android/format/ooxml/src/main/kotlin/dev/fusion/ooxml/SpillSheetEngine.kt` (+_xlfn. exports)
- `android/tools/gen_r132_complex_corpus.py` (new)
- `android/tools/check_r132_complex.py` (new)
- `android/tools/r132_census_deepen.py` (new)
- `android/format/ooxml/src/test/kotlin/dev/fusion/ooxml/R132ComplexTest.kt` (new)
- `android/format/ooxml/src/test/resources/r132/complex_corpus.tsv` (new)
- `android/docs/module-testability.json` (census pins updated)
- `android/docs/R132-RESULTS.md` (new)
