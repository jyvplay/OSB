# R113 — the r47 lesson turned into a six-second local gate

R113 adds no product behaviour. It adds one static gate over the probe build configuration, its controls, the census
entries that name them, and this record. The round's question was narrow: *can the failure R112 only learned from CI
be decided locally, on every source set at once, without a Kotlin compiler?*

## 0. Worktree state restored by measurement, not by hope

The session's checkout arrived at `main` (`625837b`) carrying the R112 edits as **uncommitted** changes, while the
branch's pushed head was `849862d`. Restoration was verified before it was performed:

| Check | Result |
|---|---|
| `git fetch origin arena/08e63747-osb` | new remote-tracking ref at `849862d` |
| `git hash-object <file>` vs `849862d:<path>` for all 12 changed paths | **12/12 identical** |
| `git reset --soft 849862d` then `git reset 849862d` | branch ref and index moved to `849862d`; **worktree untouched** |
| `git status --porcelain` afterwards | empty |

No `--hard`, no `checkout -- .`, no `clean`: nothing was discarded, and the four R112 tool files are present. The
"restore the uncommitted changes" request is therefore satisfied *and measured* — the bytes were already the pushed
ones; only the local ref and index were behind.

## 1. The gate: `tools/check_r113_include_closure.py`

`android/testing/build.gradle.kts` declares 102 probe source sets; **43** of them filter their file list with
`kotlin.include(...)`, so Gradle compiles a subset of the directories they point at. A subset compiles as one module,
so a reference from an included file to a same-package top-level declaration in an *excluded* file is an unresolved
symbol. R112 hit that in CI (`:testing:compileR47Kotlin`, nine `Xlsx*` symbols) after an 18-minute round trip.

The gate parses the build file, resolves each set's `srcDir`s (relative to `testing/`, not the repository root),
splits every candidate file into included and excluded by Ant-style pattern match, and then:

* **R113-1 closure** — an included file may not reference a same-package top-level `object`/`class`/`interface`/
  `typealias`/`val`/`var` that only an excluded file provides.
* **R113-2 live pattern** — every include pattern must match at least one file (a dead pattern is drift).
* **R113-3 unique pattern** — no pattern listed twice.
* **R113-4 resolvable set** — an include-filtered set must have at least one source directory that exists.

Result on this repository: `R113_INCLUDE_CLOSURE_OK sets=102 with_include=43 modelled=40 unmodelled_templates=3`,
**0 violations**, in ~6 s. Three sets whose `srcDir` is a `"../$it/…"` template are reported as *not modelled* rather
than guessed at; top-level `fun` declarations are not modelled because a receiver (`fun Map<String, Any?>.str`) and a
name are the same text, and the gate says so in its own docstring.

## 2. Sensitivity: the real defect, reproduced locally

Two controls copy the **real** `testing/build.gradle.kts` and the **real** `format/ooxml` package into a temporary
root, remove one genuine r47 include entry, and require the gate to report it:

* removing `"**/XlsxValidation.kt"` → `R113-1: r47 compiles XlsxStyledBook.kt which uses XlsxValidation from
  XlsxValidation.kt, omitted by its include filter`;
* removing `"**/XlsxMerges.kt"` → the same class of report.

That is the CI failure that cost a full round trip, decided in about four seconds on a machine with no JDK, no Gradle,
no Android SDK and no Kotlin compiler. Twelve controls pass in total (`12/12`), including dead-pattern,
duplicate-pattern, template-set and clean-tree cases.

## 3. Three false-positive classes I produced, and the control that now pins each

A gate that fires on Kotlin scoping it does not model is worse than no gate. Each of these was found by disbelieving
the gate's own output and reading the code it accused:

1. **Extension receivers.** `cast/castv2/…/Json.kt` declares `fun Map<String, Any?>.str(...)`; a naive declaration
   regex read the receiver as a top-level `Map`, so r36 and r45 "used `Map` from `Json.kt`". Fixed by rejecting a
   captured name followed by `.` or `<`; pinned by `test_extension_receiver_is_not_a_declaration`.
2. **Nested shadowing.** `CronDagWorkflowScheduler.kt` declares its own nested `data class CronSpec(...)`, while
   `CronSpec.kt` declares a top-level `object CronSpec`; Kotlin resolves the nested one, so the probe compiles. Fixed
   by subtracting names the including file declares at *any* indentation; pinned by `test_nested_shadow_is_silent`.
3. **Wrong `srcDir` root.** The first run reported seventeen dead patterns in r53/r54 because I resolved `srcDir`
   against the repository root instead of `testing/`. Fixed in `files_for`; the counts above are the corrected ones.

## 4. What this does and does not decide

* **Decides**: name-resolution closure of every modelled include-filtered source set, dead patterns, duplicate
  patterns, and sets that compile nothing — without a toolchain.
* **Does not decide**: Kotlin type checking, smart-cast legality across compilation units (R110's domain), or the
  three `$it` template sets; and it is not a substitute for `:testing:compileR47Kotlin`, which remains CI's job.
* It runs inside the existing discovery lane (`test_r113_include_closure.py` matches `test_*.py`), so no workflow
  change is needed, and `r113_census_deepen.py` registers the pair on `:testing` and `:format:ooxml` (50/50 modules,
  100.00% testable; specification-accepted Class A unchanged at 0/50 = 0.00%).

## 5. Reproduce

```
cd android
python3 tools/check_r113_include_closure.py
PYTHONPATH=tools python3 -m unittest tools.test_r113_include_closure
python3 tools/check_r112_visual_contracts.py
PYTHONPATH=tools python3 -m unittest discover -s tools -p 'test_*.py'
```
