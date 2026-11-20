# R112 — two red builds, one repaired lane, one contract made honest

R112 finishes the two open items from R111 and nothing else. It changes **no feature behaviour**: one inert
`testTag`, one shared test helper made stricter, one test's stated contract corrected, one Python `setUp` guarded,
plus the round's gate and its census re-pin.

## 1. `source-python-tests` (pre-existing red since `dbf8585`): reproduced, then closed

`android.yml` runs `python3 -m unittest discover -s tools -p 'test_*.py'` **before** any Gradle step, so
`testing/build/r92/artifacts/` does not exist yet. `test_r92_password_hash.CorpusTampering.setUp` read
`password_hashes.tsv` unconditionally, so nine tests errored in `setUp`; two are skipped by their own guards, which
is why CI reported exactly **`errors=7`**. Reproduced locally with the artifact directory absent (identical
summary line, identical traceback, line 70).

Repair: two lines that mirror the guard already used by the same file's `HashArithmetic.test_corpus_matches_a_third_implementation`.

| Lane | Before | After |
|---|---|---|
| `test_r92_password_hash` alone, artifacts absent | `FAILED (errors=7, skipped=3)` | `OK (skipped=10)` |
| `unittest discover -s tools -p 'test_*.py'` (the CI step) | `FAILED (failures=1, errors=1, …)` mid-repair; `errors=7` at `dbf8585` | `Ran 2322 tests … OK (skipped=446)`, rc 0 |

The seven tests are not lost: `android-r54-office.yml` invokes `test_r92_password_hash` by name in the job that has
the artifacts, and `check_r92_password_hash.py` runs against them there. **Unverified by me:** which step in that
workflow generates the R92 artifacts; I read the checker invocation, not the producer.

## 2. The 38 px disclosure clip: why the old assertion could not be satisfied

Measured in the failed Office run: `writer-structured-disclosure` laid out **424.0 px**, visible **386.0 px**. The
node is the first child of `Column(Modifier.heightIn(max=248.dp).verticalScroll(...))` in
`feature/editor/.../EditorScreen.kt`; the test reached it after `performScrollTo()` on `writer-numbered-option`.

With the disclosure at content offset 0, a viewport `V`, row height `h` and scroll offset `o`:

- "disclosure fully visible" ⇒ `o = 0`; measured visible 386 ⇒ **`o = 38`**, so the clip was scroll-induced.
- "numbered notice fully visible" ⇒ `424 + h − o ≤ V`; minimal scrolling gives **`V = 386 + h`**.
- At `o = 0` the row then overhangs by `424 + h − V = 38`, **invariant in `h`**.

So "both notices fully visible together" was **unsatisfiable in that dialog on that window** — by 38 px, whatever the
row height. Raising the 248 dp bound is predicted to be a no-op, because `V = 386 + h` is already below the bound;
moving the disclosure out of the scroll column is worse, because it removes 424 px from the viewport. The condition
is Compose's minimal-scroll behaviour, which is why the repair below is a contract statement, not a layout change.

Repair, three lines in one helper, following the idiom `revealAndClick` already used:

```kotlin
val node = compose.onNodeWithTag(tag, useUnmergedTree = true)
if (hasAnyAncestor(hasScrollAction()).matches(node.fetchSemanticsNode())) node.performScrollTo()
node.assertIsDisplayed()
```

`assertFullTextVisible` now means **"can be made wholly visible in its own viewport"**: a scroll offset is no longer
misreported as a layout defect, and a notice genuinely taller than its viewport still fails. The pinned
`writer-structured-safety` summary has no scroll ancestor, so it is judged exactly as before — that promise is
untouched, and so is the *negative* control: `NativeVisualEvidenceDeviceTest.textFitCheckerAcceptsVisibleTextAndRejectsClippedScrollableText`
clips a `Text` through **its own** `verticalScroll` viewport, so the ancestor guard does not fire there and a
deliberately clipped disclosure still fails the check. The R54 comment now states that contract instead of the unsatisfiable joint claim, and the capture list
records `writer-structured-scroll`, so the next run **measures `V`** instead of inferring it.

CI prediction this repair is falsifiable against: the failing Office case should stop reporting a 38 px clip. If the
new capture shows `V ≥ 556 px`, then the layout, not the test, was at fault and the dialog needs a taller text slot.

## 3. A second, pre-existing red surfaced the moment the lane moved

`Check source contracts and generated traceability` then reached the Gradle steps for the first time in this history
and `:testing:compileR47Kotlin` failed on nine unresolved `Xlsx*` symbols in `XlsxStyledBook.kt`. Cause: the r47
source set compiles the whole ooxml directory behind a `kotlin.include(...)` allowlist that named only
`XlsxStyledBook.kt`, while later rounds made that file reference ten sibling records. Nothing in R112 touched
`format/ooxml` or the build file, and the R110 boundary gate deliberately excludes every set with an `include(...)`
filter — r47 included — so no local gate could have seen it.

Repair: ten explicit `"**/Xlsx<Sibling>.kt"` entries next to `"**/XlsxStyledBook.kt"`. I first tried the single glob
`"**/Xlsx*.kt"`; it is cleaner, but `tools/test_r47_tools.py` pins each of the 61 kernels by literal filename and
rejected it in CI (`Ran 2247 tests … FAILED (errors=1)`). The explicit list keeps that check meaningful, and rule
R112-7 below makes the closure checkable instead of silent.

## 4. New gate: `tools/check_r112_visual_contracts.py`

Seven source contracts, run by `test_r112_visual_contracts.py` inside the existing discovery lane (no workflow edit):

| Rule | Property |
|---|---|
| R112-1 | the text-fit judge reveals a scrolled node **before** it asserts |
| R112-2 | no androidTest file computes its own fit verdict from clipped bounds |
| R112-3 | the pinned safety summary stays outside the scrolling details column |
| R112-4 | every tag a dialog capture names is defined by product code (a typo would otherwise surface after an 18-minute emulator boot) |
| R112-5 | the details column keeps a bounded height |
| R112-6 | the deliberate-clip control survives, and its clip comes from the node itself, not an ancestor the reveal would scroll away |
| R112-7 | every ooxml sibling an included r47 source actually uses is matched by that set's `include(...)` list (comments and string literals are ignored, so a KDoc mention is not a dependency) |

Verification: `R112_VISUAL_CONTRACTS_OK rules=7 test_files=57`; **16/16 controls pass** — two against this
repository, a clean synthetic tree, one benign-edit control, and eleven mutants that each fire exactly their own rule
and no other: removed reveal, reveal after the judgement, self-matching scroll instead of an ancestor scroll, private
fit judgment, safety inside the scroll, misspelled capture tag, unbounded column, removed clip control, a clip caused
by an ancestor rather than the node's own viewport, an omitted sibling the probe uses, and a real (non-comment)
reference to an omitted sibling. A comment-only mention is checked *not* to fire, which is what keeps the rule from
turning KDoc prose into a false dependency.

## 5. Census and audit

`tools/r112_census_deepen.py` re-pins exactly the three pinned files this round edited and refuses if anything else
drifted; it added the gate and its commands to `:app` and `:feature:editor`. `tools/native_source_audit.py` was
regenerated and re-checks. Unchanged counts: **50/50 modules (100.00%) meet the bounded testability standard**,
`not_established: 0`, and **0/50 (0.00%)** are specification-accepted Class A — a testable subset is not delivery.

## 6. CI evidence actually observed this turn

Both workflows were green on `6a05d80`, which is the state this document describes:

| Run | Job | Result | Steps that used to fail |
|---|---|---|---|
| [44641350727](https://github.com/jyvplay/OSB/actions/runs/44641350727) `Android native` | Traceability, assemble both flavors, JVM tests | **success** | `Check source contracts and generated traceability` (the discovery lane), `Production Kotlin pilot contracts` (`:testing:compileR47Kotlin`), `Run all JVM unit tests`, `Assemble both flavors`, lint — every step green |
| [44641350589](https://github.com/jyvplay/OSB/actions/runs/44641350589) `R54 office authoring` | office | **success** | `Run real API34 UI and provider checks`, `Require all 37 named Office cases without duplicate or skip` |

The Office run is the first green one after R110's compile repair, and it includes the R112 judge change, so the
disclosure case that reported a 38 px clip no longer does. Iteration that CI itself caught: the single glob
`"**/Xlsx*.kt"` was rejected by `tools/test_r47_tools.py` (`Ran 2247 tests … FAILED (errors=1)`), which is why the
include list is explicit; and the Office run at `71d5346` was cancelled by the next push, so no verdict exists there.

What this does **not** prove: I could not download `r54-office-evidence`, because the artifact redirects to a storage
host this sandbox cannot reach. So **I have not inspected any frame**, and I have not read the new
`writer-structured-scroll` measurement. "37/37 named cases" is the workflow's own gate as reported by the run, not my
visual inspection; the viewport height `V` from §2 is therefore still unmeasured by me.

## 7. What was not done, and what would falsify this

- **No visual inspection.** No emulator, device, Android SDK, Gradle or Excel in this sandbox; no Kotlin compile, so
  the three Kotlin edits are reviewed by hand and by the new static gate only. Their real verifier is the Office job.
- **No claim that the Office job is green**: it will tell us. The gate and this document deliberately claim source
  shape and a lane result, not rendering.
- **Regressions versus `625837b`: none known.** No product behaviour, copy, bound or semantic changed; the only
  product edit is an inert tag. The helper is strictly more capable (it adds reach, not tolerance).
- Next highest-information tests: ① the Office run's `r60-writer-numbered.json`, read for
  `writer-structured-scroll.visible_height_dp` — this resolves the `V` question; ② the 37 named Office cases;
  ③ the `source-python-tests` step, expected green; ④ if `V < 556 px` is confirmed, do **not** enlarge the dialog —
  the per-notice contract is then the correct one.
