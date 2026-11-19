# R110 — The module boundary: a compile error that seventeen green rounds could not see

`main` has been red since before R92, and every round since then has reported it as an environmental footnote:

```
> Task :testing:compileR93AutoFilterKotlin FAILED
e: testing/src/r93/kotlin/dev/fusion/testing/R93Checks.kt:131:31
   smart cast to 'Iterable<TypeVariable(T)>' is impossible,
   because 'values' is a public API property declared in different module.
```

That was not an environmental footnote. It was a real defect in a probe file, it aborted `:testing:assemble` before a
single JVM test executed in CI, and **the reason seventeen rounds of host checks missed it is a property of how this
repository verifies itself**: every host runner compiles the product sources and the probe **in one kotlinc
invocation**, and Kotlin permits that smart cast inside one compilation unit. Gradle gives each probe source set its
own compilation unit with `:format:ooxml` as a dependency, and refuses the same code.

R110 repairs the four sites and, more importantly, builds the gate that would have caught them:
`tools/check_r110_probe_boundaries.py` compiles the product once, then compiles each reproducible probe source set
**separately against that jar**, the way Gradle does.

The probe file changed by **+13/−9**; **no product file changed at all**. This is a repair-and-instrument round, not
a feature round, and §J says so in as many words.

## 0. Runtime honesty

**Actually available and used:** the pinned host toolchain from `python3 tools/bootstrap_host_tools.py install` —
**kotlinc 2.2.21** on **OpenJDK 17.0.9** (npm/PyPI transport, trusted as transport only) — CPython **3.11** with
`unittest`, `re`, `hashlib`, `subprocess`, and an `openpyxl 3.1.5` + `python-docx 1.2.0` reader cache installed by pip.
**Not available, therefore never claimed:** a Gradle distribution, the Android SDK, an emulator, a device, Excel,
LibreOffice, any renderer, any browser, any independent agent, any theorem prover. **No CI run was observed from this
sandbox** — the PR's CI is the external verifier and §F.1 states that I cannot see it. **Visual inspections: 0**, as
in every round of this programme.

## A. Formal model

**Admissible objects.** A `testing` source set is a quadruple

```
S = ( name, {srcDir_1 … srcDir_n}, include-filter?, dependencies ⊆ {…} )
```

read from `testing/build.gradle.kts`. Its **Gradle compilation unit** is the set of Kotlin files that Gradle's
`kotlin` extension resolves from those directories under the `include` filter, compiled against the jars of the
declared project dependencies. This round compiles exactly those units whose declaration this sandbox can reproduce:

```
reproducible(S)  ⟺  ¬extendsFrom(S) ∧ ¬loop(S) ∧ ¬include(S) ∧ |srcDirs(S)| = 1
                    ∧ deps(S) = [":format:ooxml"] ∧ srcDir exists
```

Measured: **56 of 102** source sets satisfy it, holding **60 Kotlin files**. The other 46 are excluded with a named
reason (§`--list`, and §H).

**Information/access model.** The build file's text, the probe sources, the product sources, and kotlinc's
diagnostics. No Gradle model, no dependency graph resolution beyond the literal `project("…")` arguments, no Android
SDK, no device.

**Resource counted.** Compiler invocations: one product build plus **one kotlinc process per selected source set**
(56). Wall clock ≈ 4 min 40 s for the gate on this host, sequential; no parallelism was used or claimed.

**Success, with explicit quantifier order.**

```
success ⟺ ∀ S ∈ selected : kotlinc(product.jar, files(S)) emits no `error:` diagnostic
failure ⟺ ∃ S ∈ selected : at least one `error:` diagnostic
```

The quantifier is over *source sets*, not over files and not over error messages: one failing set fails the round even
if every other set and every runtime probe passes.

**Parameter regime, boundaries, units, tolerances.** kotlinc 2.2.21, JDK 17.0.9, `-jvm-target 17` (matching the
project's `jvmToolchain(17)`); the gate does **not** promote warnings to errors, because Gradle's Kotlin task does not
either — so the gate is *weaker than the host runners on warnings* (`-Werror`) and *stronger on module boundaries*.
That asymmetry is deliberate and is the whole point: the two checks are not substitutes.

**Adjacent but different problems, not to be substituted for this one.**

1. **Gradle execution** — configuration, task graph, dependency resolution, incremental compile. Absent here.
2. **Android compilation** — 46 source sets need SDK-backed modules; compiling them here would be a fiction.
3. **Runtime behaviour of the probes** — a probe that compiles can still assert the wrong thing; R92–R108's runners
   are that evidence, not this gate.
4. **The product's own compile** — already covered by every round's runner; this round did not change it.
5. **Rendering / visual truth** — untouched, still 0 inspections.

## B. Outcome space

- **H+ (achievable):** every reproducible probe source set compiles against the product jar as an independent unit,
  and the four smart casts in R93 are removable without changing a single assertion. **Executed: 56/56 sets, 60
  files, 0 diagnostics, 0 smart casts**; the R93 probe still prints `R93_OFFICE_OK assertions=72 …`.
- **H− (impossible under these constraints):** the 46 source sets that Gradle assembles from several directories,
  `include` globs or extra modules **cannot** be compiled here — there is no Android SDK and no Gradle, and a
  hand-rolled classpath would constitute a different compilation unit. Refused, counted, and named rather than
  estimated. Likewise: no claim is made that CI now passes, because CI cannot be observed from this sandbox.
- **H∂ (boundary):** the property that decides every one of these cases is *cross-module null-check-then-use of a
  `val` property*. One line either side of the boundary changes the answer: the same code compiles inside one unit
  (control `test_the_same_shape_compiles_inside_one_unit`) and fails across two
  (`test_the_pre_repair_shape_fails_across_modules`).

## C. Frontier

**Imported result, with its hypotheses.** Kotlin's smart-cast rules: a smart cast is not applied to a **public API
property declared in a different module**, because the compiler cannot promise that a later write (or a custom getter)
will not change the value; the same property is fine when read into a local `val` first. Hypotheses under which this
applies: the property is a member of a class compiled in another module; it is used directly in the condition and in
the body; the compiler is the current 2.x front end (verified: kotlinc 2.2.21 rejects it, and the project's Gradle
Kotlin plugin rejects it too — CI's message is byte-for-byte the one reproduced here).

**The exact open interface between "imported result" and "our evidence".** The imported result tells us *what* Kotlin
refuses; it does not tell us *which of our source sets are compiled as separate units*. That is a property of
`testing/build.gradle.kts`, and it is precisely what was unmodelled: the host runners assume one unit; Gradle creates
102. R110 closes the interface by deriving the unit from the build file instead of assuming it.

**Frontier position.** 56/102 source sets are now checked as Gradle compiles them; 46 are not checkable here and are
listed with reasons. Before this round the ratio was **0/102** — no host check compiled any probe as its own unit.

## D. Negative space — eighteen shapes that look like a repair or a verification, and do not satisfy the contract

| # | Shape | Why it fails | The test that detects it |
|---|---|---|---|
| 1 | Compile the probe together with the product (what all seventeen runners do) | Kotlin allows the smart cast inside one unit; the Gradle error is invisible | `test_the_same_shape_compiles_inside_one_unit` **and** `test_the_pre_repair_shape_fails_across_modules`, both green |
| 2 | Replace the property read with `!!` | compiles, but throws at runtime where the original would fall into the `else` branch | rejected by inspection; `R93Checks` has no `!!` | 
| 3 | Replace it with `as List<String>` | unchecked cast: a wrong-typed value becomes a `ClassCastException` instead of a branch | `check = 0` for `as ` in the repaired sites |
| 4 | Add a compiler flag or `@Suppress` to silence the diagnostic | hides the whole class of defect, in every file | the gate recompiles without suppressions, so a `@Suppress` would leave the error |
| 5 | Move the probe's files into the product source set | changes the dependency structure to dodge a compile error | build-file review; `git diff` is probe-only |
| 6 | Add the product's *sources* to the probe source set | turns two units into one — the same fallacy as #1, now permanent | the gate selects on declaration, and `srcDir` count/singleton rule |
| 7 | Fix only the first reported error | CI reports one file at a time; `assemble` would fail again on the next | the gate prints all four, and both mutants are separate |
| 8 | Fix R93, declare the tree clean, never look at the other 55 sets | the defect class is not local to R93 a priori | the gate compiles all 56 and reports per set |
| 9 | A gate that selects zero source sets | passes trivially | controls pin `len(selected) ≥ 56` and name four sets |
| 10 | A gate that compiles a *different* set than Gradle (e.g. ignoring `include` filters) | the verdict is about the tool, not the build | `r54` is excluded for its `include(...)`, and the exclusion is asserted |
| 11 | A gate that reports "no errors" when kotlinc failed to start | exit code is 127, `error:` count is 0 | `diagnostics()` is only trusted when the process ran; a missing compiler is a `Fail`, not a pass |
| 12 | Treat the 46 excluded sets as verified | they are the ones with the least coverage | the summary line prints `excluded=46`, and `--list` names each reason |
| 13 | Revert the probe to its pre-fix text "to keep the evidence stable" | reinstates the CI break | mutation 1 restores that text and the gate fails |
| 14 | Point the gate at `/tmp` jars and forget the JDK | `java: command not found` (exit 127), silently read as "compiled" | `verify` requires `KOTLINC` to exist and the build uses `JAVA_HOME`; the control harness passes `JAVA_HOME` explicitly |
| 15 | Count assertions instead of compiling | 72 assertions passed for seventeen rounds while nothing compiled | the round's headline number is `sets=56`, not `assertions` |
| 16 | Assume Kotlin 2.2 fixed cross-module smart casts | it did not; the diagnostic is current, quoted from a live compile | `test_the_pre_repair_shape_fails_across_modules` runs that exact construct |
| 17 | Declare CI fixed from a local compile | CI compiles 102 units with Gradle's plugin versions, and 46 of them are not reproducible here | §F.1: no CI claim is made |
| 18 | Treat a green host sweep as a green build | R92–R108 all green **while** `:testing:assemble` failed | this round's existence is the counter-example; the gate is the detector |

## E. Mechanism portfolio — six mechanism-distinct approaches

| # | Mechanism | Central construction | Concrete artifact | Proved portion | Exact unresolved interface | Cheapest falsification | Gap local? |
|---|---|---|---|---|---|---|---|
| 1 | Self-contained two-compilation reproduction of the Kotlin rule | holder compiled to a jar, caller compiled against it | `test_r110_probe_boundaries.RuleReproductionTest` (3 tests) | the rule, independent of this repository | the project's Gradle Kotlin plugin version could differ in *other* diagnostics | change `AFTER` back to `BEFORE` | local |
| 2 | Boundary gate derived from the build file | parse `sourceSets.creating` blocks → select reproducible ones → compile each against the product jar | `tools/check_r110_probe_boundaries.py`, `R110_BOUNDARIES_OK sets=56 files=60` | 56/56 compile, 0 smart casts | the 46 excluded sets | revert one repaired site | local |
| 3 | Controls on the gate itself | synthetic build files for all five exclusion reasons; diagnostic parsing; real-tree assertions | `tools/test_r110_probe_boundaries.py` (15 tests) | the gate cannot pass by selecting nothing | `verify` end-to-end only on sets the jar can build | make `selectable` return `{}` | local |
| 4 | Source mutation | put each repaired site back, one at a time | `tools/r110_mutants.py` → **2/2 caught** | both sites are guarded by the gate | mutants are author-chosen | add a third site | local |
| 5 | Cross-round differential | seventeen runners, product+probe in one unit, before and after | `R92…R108` logs | the repair changed no assertion anywhere | shares the compile-unit assumption by construction | change an assertion in R93 | equivalent to the original problem (same unit model) |
| 6 | External verifier | Gradle's own `:testing:assemble` in CI | the PR's CI run | the only check that compiles all 102 units | **not observable from this sandbox** | merge and watch CI | global — and stated as open |

Mechanisms 1–4 are independent of the code they check (a compiler, a parser of the build file, and a mutation of the
repair itself). Mechanism 5 is *not* independent by construction — it inherits the very compile-unit assumption that
caused the defect — and is listed with that weakness rather than counted as support for the fix.

## F. Artifact requirement, and the six honest holes

Artifacts (regenerated by `python3 tools/run_r110_checks.py`, not checked in):
`testing/build/r110/{product.jar, mutants.tsv}` and `testing/build/r93/artifacts/*`.

1. **No CI run was observed.** The PR's `office` / `Traceability, assemble both flavors, JVM tests` jobs are the real
   verifier for the repair and I cannot see them from here. What is verified is that the exact construct CI rejected
   now compiles in a separate unit against a jar of the product.
2. **46 of 102 source sets remain unchecked here** — 39 use `include(...)` filters over several directories, 4 use a
   `"../$it/…"` loop template, 1 (`contract`) inherits the module's whole implementation configuration, 1
   (`r60Writer`) has a second dependency, 1 has two source directories. Two of them (`r54`, `contract`) are *known*
   not to compile against the JVM graph alone, because their sources reach `../feature/editor`, `../core/files`,
   `../app` and others.
3. **Compiler versions differ from CI.** Host kotlinc 2.2.21 vs the Gradle Kotlin plugin the project pins; both
   reject the same construct, but the *set* of diagnostics is not proven identical.
4. **No product behaviour changed**, so nothing here is evidence about the app. This round's claim is about a compile
   unit, and a compile is not an execution.
5. **Warnings are not promoted** in the gate (`-Werror` is used only by the round runners), matching Gradle's task
   defaults. A warning-only regression would be caught by the runners, not by this gate.
6. **Class A is unchanged at 0/50** and visual inspections remain 0. Nothing in this round moves acceptance.

## G. Second-order adversary — eight passes aimed at this patch

1. **The patch could be cosmetic.** Two `val` bindings could leave the property read elsewhere. The gate compiles the
   whole file; the four original diagnostics came from exactly two sites, and both mutants reproduce them one at a
   time.
2. **The patch could change semantics.** `val values = column.values` is a single read of a `val` property; the `if`,
   the `when`, the joined payload and the `else` branch are byte-identical in behaviour. Evidence: `R93_OFFICE_OK
   assertions=72 workbooks=4 filters=3 verdicts=259 hidden=4 undecided=2`, unchanged from R93's baseline.
3. **The gate could be satisfied by selecting nothing.** Controls pin the population (`≥ 56`, four named sets) and
   the sum `selected + excluded == total`.
4. **The gate could select the wrong unit.** Five exclusion reasons are each exercised by a synthetic build file, and
   `r54` (the real set that pulls `../feature/editor` with an `include` filter) must be *excluded*.
5. **The gate could pass on a silently dead compiler.** `verify` requires `KOTLINC` to exist and sets `JAVA_HOME`;
   the failure mode `java: command not found` was hit during development and is what motivated the check.
6. **The repair could be right but the diagnosis wrong.** The diagnosis is "cross-module smart cast"; the control
   proves the same shape fails only across modules and compiles inside one — that is the diagnosis, executed.
7. **The rest of the tree could hide the same class.** All 56 reproducible sets were compiled, not just R93; 55 were
   already clean and R93 was the only failure. The 46 unreachable sets are named, so the gap is bounded rather than
   hidden.
8. **The lane's own integrity.** Five mistakes of mine were found and fixed in this round (§I), each by a failing
   control or a failing gate run, and each fix was followed by a full re-run of the gate, the controls, the mutants,
   the seventeen round runners, R47, the Python suite, the census and the structural gates.

## H. Verification — what actually ran

| Lane | Command | Result |
|---|---|---|
| R110 runner (end to end) | `python3 tools/run_r110_checks.py` | `R110_PRODUCT_JAR sources=94`; `R110_BOUNDARIES_OK sets=56 files=60 smart_casts=0 excluded=46`; `Ran 15 tests … OK`; `R93_OFFICE_OK assertions=72 workbooks=4 filters=3 verdicts=259 hidden=4 undecided=2` |
| boundary gate alone | `python3 tools/check_r110_probe_boundaries.py --jar …` | 56 `ok` lines, `R110_BOUNDARIES_OK …`; population and reasons via `--list`: **56 compilable, 46 excluded** (39 `include`, 4 `$it`, 1 `extendsFrom`, 1 second dependency, 1 two directories) |
| R110 controls | `python3 -m unittest test_r110_probe_boundaries` | `Ran 15 tests … OK` |
| R110 mutants | `python3 tools/r110_mutants.py` | **2/2 caught**, each with `smart cast to 'Iterable<TypeVariable(T)>' is impossible, because 'values' is a public API property declared in different module.`; probe sha256 `1ca5669f901e235e…` restored |
| R92–R108 | their runners | all green, **every count identical to R109's base**: 38, 72, 98, 72, 60, 70, 91, 68, 132, 125, 113, 113, 220, 147, 132, 134, 176 |
| R47 | `python3 tools/check_r47_reference.py` | `2393 production-kernel assertions across 61 new kernels`; oracle `40/40` |
| whole Python suite | `python3 -m unittest discover -s tools` | `Ran 2597 tests … OK (skipped=268)` (2582 at R108, +15 controls) |
| census | `python3 tools/module_testability.py --check` | `50/50 = 100.00%`, `not_established: 0`, with R93Checks.kt re-pinned to `1ca5669f…` |
| structural + Studio gate + audit | `check_project.py`, `check_r104_studio.py`, `native_source_audit.py` | green — AGP 9.1.1, Gradle 9.3.1, JDK 17+, compileSdk 37 (Rabbit 1); 231 requirements, 36 cross-cutting obligations |

**The before/after, measured with the same tool.** The four diagnostics the gate now forbids were produced by
compiling `testing/src/r93/kotlin` as its own unit against the product jar:

| Site | Line | Diagnostic |
|---|---|---|
| `writeIntent`, values | 131 | `smart cast to 'Iterable<TypeVariable(T)>' is impossible, because 'values' …` |
| `writeIntent`, custom | 137 | `smart cast to 'List<XlsxAutoFilter.Custom>' is impossible, because 'custom' …` |
| rounding fixture, values | 186 | `smart cast to 'Iterable<TypeVariable(T)>' is impossible, because 'values' …` |
| rounding fixture, custom | 191 | `smart cast to 'List<XlsxAutoFilter.Custom>' is impossible, because 'custom' …` |

After the repair: the same compile, zero diagnostics, and `R110_BOUNDARIES_OK sets=56 … smart_casts=0`.

**Absolute and percentage, per the standing ask.** Against the census standard (50 modules): substantial testable
subset **50/50 = 100.00%**; specification-accepted (Class A) **0/50 = 0.00%**. For this round's own subject, the
measurement that matters is not a module count but a compile-unit count: **56/102 source sets = 54.9%** are compiled
here the way Gradle compiles them (0/102 = 0.0% before this round), and **56/56 = 100%** of those compile clean.
Coverage of the *defect class*: 4/4 known diagnostics eliminated, 2/2 mutants detected, 15 controls green.

## I. Repair — including five repairs of my own work in this round

The probe repair is `+13/−9` in one file: two locals per site, no other edit. The rest of this section is what went
wrong in *my* tooling while building the gate, because a repaired candidate inherits no trust and neither does a new
checker.

1. **The gate resolved `srcDir` against the repository root** instead of `testing/`, so the first run failed with
   `src/r100/kotlin holds no Kotlin source`. Fixed by introducing `TESTING`; the controls now assert every selected
   directory exists.
2. **The selection rule was too permissive at first**: I selected on "dependency is exactly `:format:ooxml`" and then
   discovered that `r54`'s block also carries a second `srcDir` and an `include(...)` filter — compiling that would
   have been a *different* unit. The rule was tightened to five explicit exclusions, each with a synthetic control.
3. **A control built invalid Kotlin**: merging two packages into one file by string concatenation left `import
   holder.Holder` inside `package together`. Replaced with a hand-written one-unit source.
4. **The synthetic selection control assumed fictional directories exist.** `selectable` gained a `root` parameter so
   synthetic build files can be tested against an empty tree.
5. **The mutation harness looked for `error:` in the gate's output**, but the gate prints diagnostics without
   kotlinc's prefix, so both mutants were recorded as "caught" with an empty message. The harvest now matches
   `smart cast` and records the diagnostic itself.
6. **The census script's block splice mangled a comment** (`…and the m# The one file…`) and left a dead print loop.
   Both repaired, then re-run; the census pins R93Checks.kt at the new hash and the aggregate check passes.

Each repair was followed by re-running the gate, the 15 controls, the 2 mutants and — after the last one — the entire
sweep in §H, rather than only the failing case.

**New attack aimed at the patch (per §I):** the mutation harness does not merely assert that the gate is green; it
*reintroduces* each repaired site and requires the gate to fail with the CI diagnostic. Both sites are caught
independently, which is the executable statement that the repair is what closes the error and not some incidental
change around it.

## J. Regressions versus R108 (`9a014d6`, the base of this round)

**Product:** none. No file under `format/`, `core/`, `feature/`, `app/` or `docs/` (other than the generated census)
changed. Nothing about the reader, the writer, the grammar or the verdict model moved.

**Evidence:** one probe file, `testing/src/r93/kotlin/dev/fusion/testing/R93Checks.kt`, changed by **+13/−9**. The
compiled shape changed; the assertions did not — `R93_OFFICE_OK assertions=72 workbooks=4 filters=3 verdicts=259
hidden=4 undecided=2` before and after. Its artifacts (`autofilters.tsv`, and the rest) are regenerated by the runner
and are byte-identical, which is the strongest available statement that the two `val` bindings are a pure refactor.

**Support edits, none of which re-pins another round's evidence:**

1. `tools/check_r110_probe_boundaries.py` (new, 232 lines) — the gate.
2. `tools/run_r110_checks.py` (new) — product jar → gate → controls → R93 probe.
3. `tools/test_r110_probe_boundaries.py` (new, 15 controls).
4. `tools/r110_census_deepen.py` (new) — re-pins only `R93Checks.kt` and adds R110's three tools to the
   `:format:ooxml` row; it refuses to run if any other pinned file has changed.
5. `docs/module-testability.json` / `MODULE-TESTABILITY.md` — regenerated by their generators.

**No CI-file change was made, deliberately.** Wiring a kotlinc-based Python lane into CI would fail there (CI has no
pinned host kotlinc) and would duplicate what Gradle already does correctly; the CI `assemble` job *is* the check for
all 102 units, and it was already failing. R110's contribution is that the failure is now reproducible, bounded and
gated locally before pushing.

**Restoration note.** This turn began on a clean tree at `9a014d6` with `git status --porcelain` **empty** and
`HEAD` equal to the merge commit of PR #31. There was nothing to restore: the "uncommitted changes" instruction was
satisfied by proof of identity rather than by a restore that did not happen — no file in the tree differed from
`main`, and the R108 artefacts are tracked in the merge commit.

## K. Class A, and the next test

**Class A applications: still 0/50.** Testability 50/50 = 100.00%. Visual inspections 0. This round repaired a
breakage and converted an invisible verifier gap into a gate; it did not move acceptance, and it adds no user-facing
feature. Saying otherwise would be the plausible essay this programme keeps refusing to write.

Next tests, in order of information per minute:

1. **Watch this PR's CI.** `:testing:assemble` will now pass or fail at the *next* source set after R93 — the 46
   sets this sandbox cannot compile. That single run answers a question no host check can.
2. **If `assemble` fails after R93**, the next fix is the same class or the same kind of declaration problem; the gate
   can be extended to that set only by reproducing its declaration (multi-`srcDir` + `include` globbing), which is
   cheap to add once a real failure names it.
3. **Extend the gate to multi-`srcDir` + `include` sets** in a dedicated round — 45 blocks, of which the `"../$it/…"`
   loop templates (4) need the loop's variable list parsed to be honest.
4. **The next feature increment** remains whole-column ranges (`SEARCH("x",B:B)>0`), which needs a used-range the
   model still does not carry.
5. **First visual inspection** still requires a machine with Excel or LibreOffice; nothing in this sandbox can supply
   one.
