# R115 — the device job's "exactly 37 named cases" decision, made locally

The managed-device job `R54 office authoring` selects instrumented tests with one argument:

```
-Pandroid.testInstrumentationRunnerArguments.class=…R54OfficeDeviceTest,…CopyContractDeviceTest,
   …NativeVisualEvidenceDeviceTest,…R58SheetDeviceTest,…R59SheetDeviceTest,…R24MapAndServiceDeviceTest,
   …ShellSmokeTest#explicitLocalPlanOpensActualCalculatorAndPreservesModuleGates
```

Six whole classes plus **one** method filter. `tools/r64_office_case_gate.py` then requires exactly 37 named cases in
the device XML. Between the Kotlin sources and that assertion sit three silent failure modes, all of which currently
cost an 18-minute emulator round trip to discover:

* a case is renamed, deleted, or moved to another class, so the runner emits 36 cases;
* the workflow's selection drifts — a class added, or the single `#method` filter dropped, which would run the other
  **18** `ShellSmokeTest` methods that the gate never expected;
* two captures write the same file name, so one piece of visual evidence overwrites the other *before* anyone looks.

`tools/check_r115_office_cases.py` decides all three from the same two sources the device job uses.

| Rule | Contract |
|---|---|
| R115-1 expected present | every expected case exists as an `@Test fun` in the class it names |
| R115-2 unique methods | an office test class declares no duplicate method name |
| R115-3 selection equals | the selected cases are exactly the expected cases — no additions, no losses |
| R115-4 classes resolve | every instrumented class has a source file |
| R115-5 capture unique | no two dialog captures write the same file name |

The expected list is **imported** from `r64_office_case_gate.EXPECTED`, not copied: the gate and the device assertion
cannot disagree because there is only one list. Result on this tree:

```
R115_OFFICE_CASES_OK expected=37 selected=37 classes=7 captures=14
```

## 1. The defect this round found in itself: comment stripping deletes code

The first run of the gate reported two false `R115-1` failures. Two methods — `CopyContractDeviceTest.selectedResult
RetainsItsOriginalIdentity` and `R24MapAndServiceDeviceTest.serviceReviewRejectsOversizeText…` — exist and are
selected, yet the gate said they were missing. The cause was the shared stripper's order of operations: it removed
`/* … */` first, and `CopyContractDeviceTest` contains

```kotlin
"content://provider/document/*"
```

So the "comment" opened inside a string literal and swallowed everything up to the next `*/` — four hundred lines of
real tests. A gate that fails this way fails *loudly*, but the mirrored shape fails silently: a `"//"` inside a string
would delete the rest of its line, and a test the workflow relies on would vanish from the inventory with no message.

`tools/kotlin_code_only.py` replaces all three private strippers (R112, R113, R115) with one left-to-right scanner that
recognises raw strings, quoted strings with escapes, character literals, line comments and block comments in a single
pass, and blanks removed spans **character for character** so line and column numbers in the view are the file's own.
It offers two views of the same bytes:

* `code_only(text)` — comments *and* literals blanked; used to find annotations and declarations, so a `@Test` inside
  a KDoc block or a string is not a case;
* `strip_comments(text)` — comments blanked, literals intact; used to read a call site's arguments.

Both keep every newline. `check_r112_visual_contracts.py` (−9/+2 lines) and `check_r113_include_closure.py` (−8/+2)
now import it instead of carrying a private copy; both stayed green, so the rewiring is behaviour-preserving.

## 2. Hardening from the red-team passes

Five adversarial passes over the new gate, each asking "what makes this say green while the device job says red?":

1. **A commented-out selection.** `SELECTION` matched `class=` anywhere, including a remark. A disabled line would have
   doubled the selection. Fixed: `workflow_code()` blanks full-line YAML comments and trailing ` # …` remarks before
   scanning. Control: a commented `class=…A` line selects nothing and the class count stays 2.
2. **A commented-out capture.** `captures()` read raw text, so a disabled `captureDialogDisplay(...)` could claim a file
   name. Fixed with the two views above (comments blanked in both, literals kept only for reading). Control: a `//` and
   a `/* */` call are invisible; only the live call's `r58-live` is collected.
3. **Parentheses inside a string.** Depth counting over raw text unbalances on `"t(1)"`. Fixed by counting brackets in
   the literal-blanked view and slicing arguments out of the literal-preserving one. Control pairs a named call with
   `tag = "t(1)"` with a positional call of the same name and requires exactly one `R115-5`.
4. **The capture name is not the first argument.** The signature is
   `captureDialogDisplay(compose, activity, name, tag, additionalTags)`; "first string literal" was only accidentally
   right. Now the third positional argument, or `name = …` when passed by name. Cross-checked against R112's
   independent parser: both list the same **14** names on the real tree, which is now a control.
5. **Annotation arguments and quoted comment markers.** `@Test(timeout = 5000)` was not recognised; `"a//b"` and
   `"…document/*"` had to survive. Both are controls.

Control file: `tools/test_r115_office_cases.py`, **20 tests**: eight one-rule mutants, six adversarial fixtures above,
three scanner controls, two repository controls — one of which copies the *real* workflow and removes the single
`#method` filter and requires exactly **18** `R115-3` overruns with `selected=55`.

## 3. What this does and does not prove

It proves the inventory the device job will enumerate, without a device: names, classes, the selection argument's
meaning, and the uniqueness of the evidence file names. It does **not** compile Kotlin, and it does not render a
document — no JDK, Gradle, Android SDK, emulator, Excel or LibreOffice exists in this sandbox, and CI artifact blobs are
unreachable from here. A gate passing means "the evidence code kept its shape"; the render itself is still only
verified by the managed-device job.

## 4. Verification

| Check | Result |
|---|---|
| `python3 tools/check_r115_office_cases.py` | `R115_OFFICE_CASES_OK expected=37 selected=37 classes=7 captures=14` |
| `python3 tools/check_r113_include_closure.py` | `R113_INCLUDE_CLOSURE_OK sets=102 with_include=43 modelled=43 unmodelled_templates=0` |
| `python3 tools/check_r112_visual_contracts.py` | `R112_VISUAL_CONTRACTS_OK rules=9 test_files=57` |
| `PYTHONPATH=tools python3 -m unittest tools.test_r115_office_cases` | 20/20 controls |
| `PYTHONPATH=tools python3 -m unittest discover -s tools -p 'test_*.py'` | `Ran 2366 tests … OK (skipped=446)`, rc 0 |
| `python3 tools/r115_census_deepen.py` | 50/50 modules (100.00%) testable, `not_established: 0`; Class A **0/50 (0.00%)** |

Regressions versus `541acd5` (R114): none. The diff is three new Python files, one new control file, two gates rewired
to a shared scanner, and the census re-pin; no product source, no build file, no test assertion changed.

## 5. Open items, unchanged

* **Class A stays 0/50.** Testable is not accepted: acceptance needs rendered evidence from a device run, which this
  sandbox cannot produce and must not fake.
* **`Probe died`** on `BrowserProfileDeviceTest.sameOriginCookiesAndDomStorageRemainInTheirOwnProcessProfiles`
  (R113 head) is still unproven as flake — a rerun was refused by GitHub, so there is one clean sample and one failure.
