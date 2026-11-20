# R114 — two gates closed over the cases they had only reported as unmodelled

R114 changes no product source, no build file and no test assertion. It extends the two static gates added in R112 and
R113 so that they decide three more classes of failure locally instead of after a CI queue, and it re-pins them.

## 1. `check_r113_include_closure.py`: 43 of 43 include-filtered sets are now modelled

`testing/build.gradle.kts` reaches four directories through a loop template rather than a literal path:

```kotlin
listOf("core/files", "feature/terminal", …).forEach {
    kotlin.srcDir("../$it/src/main/kotlin")
}
```

Three of the four sets that use this idiom (`r13`, `r14`, `r15`; `subsets` has no include filter) were reported as
*unmodelled* by R113, which meant the gate said nothing about them. The parser now expands each template to its real
directories by reading the `listOf(...)` that encloses the `.forEach`, and only a template with no enclosing list stays
unmodelled — and then it is reported as unmodelled rather than accused of having no source directory, which is a
different claim the gate must not make.

| Quantity | R113 | R114 |
|---|---|---|
| source sets parsed | 102 | 102 |
| sets with an `include(...)` filter | 43 | 43 |
| **modelled** | 40 | **43** |
| unmodelled templates | 3 | **0** |
| violations | 0 | 0 |

Second extension: a **capitalised top-level `fun`** is now treated as a provided name. Lowercase functions are
deliberately excluded — the reference scan collects capitalised identifiers only, so it can never match them, and
widening the scan would turn local variables and labels into dependencies. An extension function still declares its
*name*, not its receiver: `fun Map<String, Any?>.str(…)` provides `str`, never `Map` (that false positive was found and
pinned in R113 and is still controlled here).

## 2. `check_r112_visual_contracts.py`: two runtime `require`s decided before the boot

`captureDialogDisplay` enforces two invariants at run time:

```kotlin
require(additionalTags.size <= 8 && (listOf(tag) + additionalTags).distinct().size == additionalTags.size + 1)
require(NativeCaptureNames.dialog(name))
```

A capture call that breaks either one throws **after** the managed emulator has booted — the same 18-minute round trip
R112 was about. Two new rules read every call site statically:

| Rule | Contract |
|---|---|
| R112-8 | at most eight extra tags, and no tag repeated (including the primary) |
| R112-9 | the capture name matches the allowlist regex parsed out of `NativeCaptureNames` |

The gate now enforces **9** rules across 57 androidTest files, and the suite has **19** controls: three of them
mutate the synthetic call site into nine extra tags, a repeated tag, and an off-list name (`cap-review`), each firing
exactly its own rule.

## 3. What I got wrong while building this, in order

1. The first expansion pass left `template = true` for every set that had ever contained `$it`, so the summary still
   read `modelled=40, unmodelled_templates=3` while the code looked correct. Fixed by tracking which template strings
   were actually resolved.
2. The second pass dropped unresolved `$it` entries, which made an unexpandable template look like a set with no
   source directory and fired R113-4 on the control tree. Fixed by keeping unresolved templates and skipping them as
   unmodelled.
3. An assertion of mine (`assertEqual(1, summary['templates'])`) encoded the *bug* rather than the intent; the control
   caught it, not me.
4. The census has per-round ownership: `r112_census_deepen.py` refused to run because R113's files had changed, and
   `r113_census_deepen.py` would in turn refuse because R112's gate changed. The resolution is the established
   pattern, not a loophole: a new `tools/r114_census_deepen.py` that owns exactly the four files this round edited.

## 4. Verification

| Check | Result |
|---|---|
| `python3 tools/check_r113_include_closure.py` | `R113_INCLUDE_CLOSURE_OK sets=102 with_include=43 modelled=43 unmodelled_templates=0` |
| `python3 tools/check_r112_visual_contracts.py` | `R112_VISUAL_CONTRACTS_OK rules=9 test_files=57` |
| `PYTHONPATH=tools python3 -m unittest tools.test_r113_include_closure` | 15/15 controls |
| `PYTHONPATH=tools python3 -m unittest tools.test_r112_visual_contracts` | 19/19 controls |
| `PYTHONPATH=tools python3 -m unittest discover -s tools -p 'test_*.py'` | `Ran 2346 tests … OK (skipped=446)`, rc 0 |
| census | 50/50 modules (100.00%) testable, `not_established: 0`; Class A 0/50 (0.00%) |

## 5. Limits, stated rather than implied

* Both gates decide **names and call-site literals**. They do not type-check Kotlin and they cannot see a dynamically
  built tag list; a call site that computes its tags would be invisible to R112-8/R112-9, which is why the allowlist
  rule also exists in the test that runs on the device.
* Nothing here compiles Kotlin: no JDK, Gradle, Android SDK or emulator exists in this sandbox, and CI remains the
  compiler and the only renderer.
* **The R113 blocker is untouched**: `BrowserProfileDeviceTest.sameOriginCookiesAndDomStorageRemainInTheirOwnProcessProfiles`
  died with `Probe died` on the R113 head while passing on the parent commit, and GitHub refused both rerun attempts.
  I did not change that test: a probe-lifecycle "fix" I cannot execute would be a guess that could hide the real
  cause. It stays the top open item, with the missing artifact being one clean second sample.
