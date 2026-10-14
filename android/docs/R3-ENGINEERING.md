# R3 — native calculator implementation, verification and completion audit

Date: 2026-10-13. **This is a bounded native implementation advance, not delivery of the full suite or a cleared pilot.** No TypeScript, web UI, mocked Android runtime, receiver simulation or fabricated device measurement was added.

## Result first

- Replaced the two-operand calculator with production Kotlin decimal expressions, approximate scientific functions, 64-bit programmer operations, dimension-checked units and bounded local history. Native Compose/ViewModel integration is source-present; it has **not** been Android-compiled.
- Added explicit file-descriptor sync and decoded-value readback to calculator, editor and desktop checkpoints. The Android adapter has **not** executed here. Successful readback is not a proof of survival through sudden power loss.
- Latest executed run: **153,216 existing + 2,056 calculator = 155,272 JVM assertions passed**, using Kotlin 2.2.21 and Temurin 17.0.9+9. These are repeated assertions, not that many independent tests. Three existing CastV2 unchecked-cast warnings remain. No Cast transport was exercised.
- **15 Python tests passed**, including six new audit-validator tests. Structural checks still cover 48 included modules, 231 feature rows and 30 unique blocked spikes. PR-09 remains `partial_unverified`; no P1 or spike was promoted.
- **0/48 included modules specification-complete (0%); 48/48 still need completion/acceptance (100%).** See [all 48 rows and architecture reconciliation](MODULE-COMPLETION.md). This is an acceptance count, not an estimate that zero implementation work exists.

## A — Runtime/tool honesty

Actually used: local Git/source inspection; `gh run list` (returned `[]`); GitHub Contents API for pinned upstream source excerpts; genuine Kotlin compiler/JVM execution; Python tests/structural checks; filesystem readback; supplementary syntax parsing. Tool/source caches are outside the app and are not product dependencies.

Not available/executed: full JDK with javac, Android SDK/adb, Gradle distribution/build, Android Studio, AVD, physical device, remote receiver, legal counsel, theorem prover or independent agent. The earlier wrapper launch failed fetching Gradle before an Android build could start. No GitHub workflow was dispatched, no APK was generated, and no claim of trying the app in Rabbit 1 is made. Writing a workflow or Android test does not execute it.

## B — Admissible objects, bounds and quantifiers

**Product success** requires the whole assigned v2.0/v2.1 scope, not merely PR-09: every mandatory module/requirement and applicable spike must have reviewed implementation, build, license, security, accessibility, interoperability and reference-device evidence. Folder names, a compilation pass and a count of assertions do not imply that universal claim.

**This slice's admissible request** is immutable mode/angle/output-radix/known-unit context plus an expression of at most 512 UTF-16 characters. Grammar is ASCII numeric literals/names, conventional operators, parentheses, scientific function calls, plus explicitly supported π/×/÷/−. There is no eval, reflection, symbolic simplification, implicit multiplication, remote calculation or imported executable expression.

Bounds and semantics:

- At most 256 tokens, 128 characters per literal, 32 parser frames including the root (31 nested parentheses can be accepted; 32 are rejected). The lexer advances on every iteration; parser recursion is depth-limited and consumes tokens. This is a finite-work construction, **not** a measured Android latency bound or a mechanically checked proof.
- Decimal arithmetic uses `MathContext(34, HALF_EVEN)`, not arbitrary exact reals. Nonzero results must have adjusted decimal exponent −1000…1000; literal exponents and integer powers are bounded to ±1000. Power is right-associative; unary minus has lower precedence than power. `%` means signed remainder, not an implicit percentage. `0^0`, division by zero and fractional decimal powers are rejected.
- Scientific arithmetic is approximate finite binary64; angle context is explicit. The fixed oracle/identity corpus uses absolute-or-relative tolerance `1e-12 × max(1, |expected|)`. This is **not** a worst-case accuracy theorem for arbitrary composed expressions. Nonfinite domains, explicit literal/product/division/power/exp underflow checks, factorial outside integer 0…170, and tangent near a cosine singularity are rejected. `percent(x)` is explicit. Repeated `!!` is rejected rather than masquerading as mathematical double factorial. Other ill-conditioning/rounding limits remain those of binary64.
- Programmer mode uses a 64-bit two's-complement word. Arithmetic wraps modulo 2^64. `^` is XOR, not power. Literal magnitudes must fit 64 bits; default input is decimal, with explicit `0x/0b/0o` prefixes. `>>` is arithmetic and `>>>` logical. Counts outside 0…63 are rejected, not silently masked. Output radix changes do not reinterpret input.
- Unit definitions are fixed and dimension-checked: length, mass, absolute temperature, fixed duration, data, speed, area and volume. Affine rational numerators/denominators are combined before the **single final decimal34 rounding step**. Below-absolute-zero values fail. International foot/mile and avoirdupois pound are explicit; no survey foot, ambiguous gallon, currency or calendar conversions. Standalone unit amounts are limited to 34 significant digits and the same exponent bound.
- History holds the newest 100 results, immutable request context and an unfinished draft. Snapshot payload ≤256 KiB, version/magic/SHA-256/exact EOF; duplicate IDs and invalid contexts are rejected. A checksum detects accidental corruption, **not malicious authentication**. Restore never evaluates a stored expression. Stored results are labeled recorded, not recomputed.
- Android persistence is private `no_backup` storage, one mutex-protected writer, 500 ms coalesced checkpoint delay and a best-effort onStop flush. Dirty/failed state remains explicit. Unreadable data requires explicit retain-and-reset; clear is confirmed in UI and is not secure erasure of retained files/backups. The Android implementation is unverified; hard-kill/power-loss durability is not inferred from source.

Adjacent non-solutions excluded: a JavaScript calculator, a system-intent calculator, exact-real/symbolic claims, 32-bit arithmetic advertised as 64-bit, SI/binary prefix conflation, history replay with changed context, and an app-wide completion percentage inferred from this one feature.

## C — Hypotheses and evidence thresholds

- **H+ (full native suite is complete and usable): not established.** Required evidence includes successful both-flavor Android builds/install/lint, assigned feature tests, actual process/provider failures, accessibility and physical/legal gates. None may be replaced by this JVM suite.
- **H− (the requested suite is impossible): not established.** Missing tools, time and evidence are not an impossibility theorem. Specific prohibited routes, such as systemwide third-party native virtual camera or an unlicensed FairPlay path, are not pursued.
- **Boundary result supported now:** production calculator kernels compile and satisfy the executed bounded corpus; native integration exists in source; completion remains 0/48 under the stated gate. This is the strongest current claim.

Stop rule: do not change any module to complete, any requirement to verified, any performance budget above E0, or any spike to passed until its independent evidence exists and matches the implementation. Repaired candidates receive a fresh full run, not inherited trust.

## D — Imported knowledge and its hypotheses

Pinned URLs, Git blobs, SHA-256 and fetched line excerpts are recorded in [`research/r3-frontier.json`](research/r3-frontier.json). No upstream code was copied into the app as a new dependency.

1. [OpenJDK 17.0.9 BigDecimal](https://github.com/openjdk/jdk17u/blob/jdk-17.0.9+9/src/java.base/share/classes/java/math/BigDecimal.java): arbitrary precision representation does **not** make finite-MathContext operations exact. The import supports the declared rounding model, conditional on these APIs and inputs. Desktop JDK behavior does not itself verify Android's implementation.
2. [AOSP Android 14 AtomicFile](https://github.com/aosp-mirror/platform_frameworks_base/blob/android-14.0.0_r1/core/java/android/util/AtomicFile.java): caller must provide locking; `finishWrite()` can log sync/rename failures rather than propagate them. Consequently “it returned” is insufficient evidence of persisted state. Added explicit `fd.sync()` and decoded readback. This source observation is not a universal API36/37/OEM or storage-durability proof.
3. [SciPy 1.14.1 constants implementation](https://github.com/scipy/scipy/blob/v1.14.1/scipy/constants/_constants.py): numerical definitions distinguish international inch/foot/mile and Celsius offset. This is primary source for that implementation, **not a substitute for a metrology standards/legal audit**. Conversion identities and exact rational examples are separately tested; no SciPy library is bundled.

These imports are API/numerical facts, not an imported theorem that Android desktop, casting or the complete product is achievable within the present environment.

## E — Deceptive solutions and the modal shortcut

The modal shortcut is **promoting source presence or a local arithmetic/test pass into product completeness**. The audit, unchanged feature statuses and runtime admission gates explicitly prevent that promotion.

| Deceptive shape | Rejection/detection |
|---|---|
| 1. A folder equals a complete module | All 48 rows retain specific implementation/acceptance gaps |
| 2. Play/pro flavors count as two finished apps/modules | One `:app` in the denominator |
| 3. A test file proves tests ran | Separate executed JVM evidence from unrun Android tests |
| 4. A huge assertion count proves broad feature coverage | Report repeated invariants and exact tested kernels |
| 5. Kotlin/JVM compilation proves Android compilation | Android sources explicitly excluded from standalone run |
| 6. Successful parse means mathematically correct precedence | `-2^2`, `2^3^2`, unary and boundary oracles |
| 7. BigDecimal means exact arithmetic | Decimal34 half-even declaration and 1/3 oracle |
| 8. Binary64 is a financial or exact-real solver | Scientific approximation label and domain/underflow attacks |
| 9. Decimal and programmer `^` are interchangeable | Separate parsers and mode-bound history |
| 10. Java's masked shifts satisfy the requested count | Reject −1/64, verify logical versus arithmetic shifts |
| 11. Temperature is only multiplication | Rational affine transform; 0 F→C exposed an actual defect |
| 12. kB equals KiB | Independent 1000/1024 assertions |
| 13. Every unit can convert into every other | Dimension and absolute-zero checks |
| 14. History text can be silently re-evaluated after restore | Preserve full context/value; recall requires Calculate |
| 15. Checksum means encryption/authentication | Explicit plaintext/checksum limitations; rechecksummed future-version test |
| 16. AtomicFile returning means durable success | Explicit sync + decoded readback; no power-loss claim |
| 17. History clear securely erases all old bytes | Explicit retained-snapshot/backup warning |
| 18. Catch every Throwable and show a result | Only domain/argument/arithmetic exceptions become calculator failures |
| 19. Enter anywhere should submit the expression | Handler moved onto input; focused buttons keep their own activation |
| 20. CI configuration or a source citation is external validation | No CI/device/legal pass is claimed |
| 21. Blocked/disabled functionality is implemented functionality | Safety gating and completion remain separate |
| 22. API36, emulator or laptop validates API37/16K/ARM64 | Each remains an explicit separate gate |

## F/G — Distinct mechanisms, artifacts, adversaries and exact gaps

These are complementary mechanisms, **not six independent agents or six independently verified full-product implementations**.

| Mechanism | Real artifact / demonstrated portion | Candidate-specific attack and falsifier | Exact gap |
|---|---|---|---|
| Decimal precedence parser + rounded decimal arithmetic | `Calculator.kt`; fixed exact examples + 400 independent BigInteger formula oracles | Wrong unary/right associativity, division by zero, huge exponent, 256-token/32-frame boundary; any mismatch falsifies corpus claim | Not arbitrary exact real algebra; Android runtime untested |
| Scientific function/domain evaluator | `ScientificParser`; constants, trig identities, factorial and explicit approximation | NaN/infinity, singular tangent, inverse domain, `!!`, underflow; error or tolerance mismatch falsifies claim | No worst-case numerical stability guarantee, plot/complex/symbolic system |
| Bit-vector arithmetic | `Programmer.kt`; 400 arbitrary-width multiply/mod-2^64 oracles | Sign-bit overflow, MIN/-1 semantics, oversized literal, arithmetic/logical shift and count masking | No arbitrary-width word mode; Android input/format UI pending |
| Rational affine conversion | `Units.kt`; exact definitions, dimension/temperature guards, all same-dimension roundtrips | Close-offset cancellation and 0 F→C single-round oracle, then 101 held-out Fahrenheit cases | Not currency, calendar or unit-parser algebra; precision/domain explicit |
| Bounded versioned history codec | `CalculatorHistory.kt`; actual file roundtrip, mixed mode/angle/radix/units, malformed envelopes | Truncation, trailing bytes, changed checksum, rechecksummed future version, duplicate IDs, incomplete draft | SHA-256 is not authentication; JVM codec/file I/O does not test Android checkpoint adapter |
| Explicit sync + value readback | `CheckedAtomicFile.kt`; AOSP failure analysis, real Android test authored | Encoder interruption and intentionally wrong readback; any reported success on mismatch falsifies adapter intent | **Uncompiled/unrun Android source**; kill/fsync/rename/OEM matrix pending |
| Activity-owned native state + context-bound recall | `CalculatorViewModel.kt`, `Calc.kt`, `MainActivity.kt` | Rotate, minimize, close/reopen, mode-change/recall, focus Enter on Clear/Recall, failed checkpoint, 200% text | **Uncompiled/unrun Android source**; no AX or process-death pass |
| Fail-closed completion accounting | `module_audit.py`, `module-audit.json`, generated report | Remove/duplicate a module, invent alias, promote source-only row, stale generated report | Validator tests establish accounting consistency, not truth of future human acceptance records |

## H/I — External checks, repair history and renewed attacks

Actual machine checks are distinct from self-review, but the tests were authored in this session. There is **no independent reviewer, second implementation team or theorem-prover certificate**. The BigInteger/high-precision oracles use a different formula/representation than the expression parser, not an independent runtime vendor. Android/device validation remains the necessary external check.

Observed sequence:

1. Initial kernel compilation failed on a missing closing parenthesis in the unit registry. Fixed it, then recompiled successfully; no initial failure was called a pass.
2. Review identified ambiguous `!!`, explicit underflow paths, and a keyboard handler at the wrong scope. Repaired them. Production JVM tests were run after kernel changes; keyboard behavior remains unrun Android source.
3. The first 1,146-assertion calculator corpus passed. A **new held-out test `0 F → C = -17.77777777777777777777777777777778` then failed in the real runner**. The initial implementation divided before subtracting the offset, losing significant digits through cancellation. The earlier pass did not protect it from this attack.
4. Repaired the rational affine numerator so offset subtraction happens before the only division/rounding. Re-ran the entire existing suite and added 101 Fahrenheit values against a separately structured 100-digit formula rounded once; all passed. Also attacked oversized standalone amounts/IDs.
5. Added fresh exact boundary cases, all mode/angle/radix histories with nondefault units, a valid-checksum future schema, and 200 malformed expressions across all four modes, evaluated twice for determinism. Final calculator run: **2,056 assertions passed**; existing suite: **153,216 passed**.
6. Added actual Android `CheckpointDeviceTest` and expanded `ShellSmokeTest` for expression/result/recreation. These remain **unrun**, including their Gradle/Android type checking. No repair is credited with Android acceptance.

Evidence: [`verification/r3-jvm-contracts.txt`](verification/r3-jvm-contracts.txt) includes executed output and compiled-source hashes. Earlier R2 evidence is historical; it is not a current-source certificate after later changes.

## J — Exact frontier and next high-information test

**Strongest verified artifact:** bounded production Kotlin kernels plus executable contracts and a reproducible, fail-closed module audit. **Residual gap:** Android integration has not even reached a compiler, much less installation, lifecycle/accessibility/device acceptance or complete product scope.

The highest-information next step is a real full-JDK/Android-SDK worker, not another source-only assertion total:

```sh
# In android/, full JDK17 + SDK36 installed; network access to official dependencies required.
python3 tools/check_project.py
python3 -m unittest discover -s tools -p 'test_*.py' -v
./gradlew :app:assembleProDebug :app:assemblePlayDebug
./gradlew :app:lintProDebug :app:lintPlayDebug
./gradlew :testing:pilotContracts :testing:calculatorContracts
./gradlew :app:pixel6Api34ProDebugAndroidTest
```

Then install/run the calculator in the first-party shell: decimal/scientific/programmer/conversion examples; all keyboard controls and focused Enter; TalkBack/Switch/Voice and 200% reflow; rotation/multiwindow; kill/relaunch after checkpoint; corrupt-copy recovery retaining old bytes; clear-history after successful and failed disk writes. An AVD can investigate API/lifecycle behavior but cannot pass physical storage durability, API37/16K/ARM64, receiver, thermal or reference-device budgets. Use [PILOT.md](PILOT.md) and [BUILD.md](BUILD.md); no current artifact should be represented as ready for paid distribution.
