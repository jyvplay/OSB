# R106 — Criteria functions: `COUNTIF`, `SUMIF`, `AVERAGEIF`

R105 taught the `expression` grammar to read ranges and to aggregate them. It could sum a range but not **select**
cells from one, so the two shapes a real workbook uses most after `SUM` were still refused by name:

```
COUNTIF(A1:A8,">5")                 SUMIF(B1:B9,"draft")>100          AVERAGEIF(C1:C9,"<>",B1:B9)
```

R106 adds those three functions and, with them, a second grammar that is *not* the formula grammar: a **criterion**.
A criterion is a number, a boolean, a comparison written as text (`">5"`, `"<=0"`, `"<>draft"`, `"="`, `"<>"`), or a
case-insensitive wildcard pattern (`"a*"`, `"?p*"`, `"~*"` for a literal star). Getting that grammar wrong is how a
reader ends up counting blanks as non-blanks or silently ignoring text.

One product file changed (`XlsxConditionalFormatVerdict.kt`, **+262/−7**). Everything else here is evidence about it.

## A. Formal model

**Admissible objects.** The R105 grammar plus three function names and one value grammar:

```
COUNTIF   ( range , criterion )                     exactly 2 arguments
SUMIF     ( range , criterion [ , range ] )         2 or 3 arguments
AVERAGEIF ( range , criterion [ , range ] )         2 or 3 arguments

criterion := number | boolean | text
text      := operator rest?
operator  := "<=" | ">=" | "<>" | "=" | "<" | ">"      matched longest first
rest      := a number *written* as one  |  a wildcard pattern
wildcard  := any characters, where "*" is a run, "?" is one character and "~" escapes the next one
```

The first argument must be a `RangeRef`. The third, when present, must be a `RangeRef` of the **same width × height**
as the criteria range; its position is free. `COUNTIFS`/`SUMIFS`/`AVERAGEIFS` (plural), 3-D ranges, whole-column
ranges, multi-area ranges and array criteria are **not** in the grammar.

**Information model.** Unchanged: the rule's `sqref`, the formula text and a sparse grid of the values this model
knows — blank, number, text, boolean, error. No date type, no locale, no collator, no formula text of other cells.

**Resource counted.** Parsing stays `O(tokens)`; the criterion adds `O(characters)` and is bounded at **255
characters** (Excel's own documented limit for a criterion) before a cell is read. Evaluation is `O(cells read)` with
one grid read per cell, and the same `MAX_RANGE_CELLS = 100000` bound R105 applied to aggregates now lives in one
`rangeBounds()` that both readers share — so a criteria range and a sum range refuse an oversized area identically.

**Success, with quantifier order.** For every block, every token of its `sqref`, every cell of that token, and every
`expression` rule in it: the verdict is `Yes` if the formula evaluates to a true boolean or a non-zero number, `No` if
false/zero/blank, and `Unsupported(reason)` otherwise. A criterion is evaluated once per formula, in order:
parse the criterion, read the criteria range, check the second range's shape, then match cells pairwise.

**Regime, units, tolerances.** Grid `1..1048576 × 1..16384`; area bound 100000 cells; criterion bound 255 characters;
equality on numbers is exact double equality; text equality folds ASCII case and nothing else. There is no collation
anywhere, which is why ordering two *different* texts is refused rather than guessed (§F.2).

**Adjacent problems that must not be substituted.** (i) *Cell-formula evaluation* — `FormulaEval` has its own range
semantics for another caller and is untouched. (ii) *Painting* — a verdict is not a rendered cell. (iii) *Locale* —
`COUNTIF(range,"<m")` against text is refused; Excel would use the workbook locale's collation. (iv) *Cross-sheet
references* — still out. (v) *Plural criteria functions* — still refused by name.

## B. Outcome space

Three outcomes per rule, unchanged: `Yes`, `No`, `Unsupported(reason)`. Measured over the round's **64** cases:
**51 fire or do not fire, 13 are refused** (13/64 = 20.3%).

- **H+ (achievable):** every criterion shape over a bounded rectangular range, with Excel's blank, error and
  numeric-text rules, is decidable from cached values alone. Executed: 51 decided cases.
- **H− (impossible under these constraints):** ordering two different texts needs a collator the model does not
  carry, and a criterion that depends on another sheet or a defined name is outside the grammar. Refused, not guessed.
- **H∂ (boundary):** the 255-character criterion bound, the 100000-cell area bound and the grid edge are the three
  places where an answer becomes a refusal. Each is pinned by cases on both sides of the edge, and the length bound
  is pinned at 255 (accepted) and 256 (refused) rather than "somewhere above 255".

## C. Frontier — what this closed, and what was verified

Imported result, restated with its hypotheses: a criterion is compared **per cell**, not evaluated as a formula; a
blank cell satisfies only the empty `=` criterion (`""`), which makes `"<>"` the non-blank count; text comparison is
case-insensitive and supports `*`/`?`/`~`; a numeric criterion also matches text that spells that number; an error in
the scanned range or in the sum range propagates as an error rather than being skipped. The hypothesis is "the
criterion is data, and matching is by value, not by evaluation".

Before R106 those shapes were refused because the function names were unknown; after, they are decided, with the
documented deviations in §F.2–§F.4.

**Coverage now.** Functions the grammar reads: **26** (23 from R104+R105 + 3 new). Formula cases across the three
rounds: **235** (100 + 71 + 64). Refusal shapes pinned: **61** (27 + 21 + 13). Written conditional rules
round-tripped: 14 + 12 + 12 = **38**.

## D. Negative space — twenty shapes that look like solutions but do not satisfy the contract

| # | Shape | Why it fails | The test that detects it |
|---|---|---|---|
| 1 | Treat the criterion as a formula and evaluate it | `">5"` has no formula meaning; the call is refused | `greater than` = 2 decided cases |
| 2 | Compare text case-sensitively | `draft` misses `Draft` | `text equality is case-insensitive` = 2 |
| 3 | Collapse `*` to a literal | `a*` matches nothing | `star wildcard`; mutation 1 |
| 4 | Treat `?` as a run instead of one character | `a~?b` would match `a*b` as well | `escaped question is literal` = 1 |
| 5 | Ignore `~` | `a~*b` matches both `a*b` and `a?b` | `escaped star is literal` = 1 |
| 6 | Make `<>` behave like `=` | `COUNTIF(A1:A8,"<>3")` answers 1 | `not equal counts text and booleans`; mutation 2 |
| 7 | Let a blank satisfy `<>` | `"<>"` counts 8 | `non-blank count` = 7; mutation 3 |
| 8 | Treat an empty-text cell as blank in both directions | `""` would count 2 | `empty text is not blank` = 1 |
| 9 | Drop numeric-text folding | `COUNTIF(A9:A9,4)` = 0 | `numeric text matches a number criterion`; mutation 4 |
| 10 | Fold booleans into numbers under `=` | `COUNTIF(A1:A8,1)` would count `TRUE` | `boolean criterion` = 1, `count of one number` = 1 |
| 11 | Order two different texts with a locale guess | `"<m"` would silently depend on the machine | `ordering two different texts` refuses |
| 12 | Ignore an error under a criterion | paints a cell over `#N/A` | `an error under a criterion` refuses |
| 13 | Ignore an error in the sum range | sums the rest silently | `an error in the sum range` refuses |
| 14 | Compare the two ranges by **position** instead of shape | a real `SUMIF(C1:C5,">2",B1:B5)` is refused | `sumif with a sum range`; the bug this round actually had |
| 15 | Skip the shape check on the second range | reads the wrong cells or throws | `a sum range of the wrong shape` |
| 16 | Count matched text/booleans in the average's denominator | `AVERAGEIF` of 5 numbers and 2 texts divides by 7 | `a matched text and boolean add nothing` = 2 |
| 17 | Return 0 for an average of no matches | silently paints | `averageif with no matches` refuses with `#DIV/0!` |
| 18 | Read a 300-character criterion | scans a pattern Excel would reject | `a criterion one character above the length bound`; mutation 5 |
| 19 | Read a cell criterion as its formula text | `J1` holding `>2` becomes text `J1` | `criterion from a cell` = 3 |
| 20 | Fold `COUNTIF`'s arity into `SUMIF`'s | `COUNTIF(a,1,b)` accepted | `countif rejects a third argument` |

## E. Evidence — six mechanisms, each falsifiable on its own

| # | Mechanism | Artifact | Proved portion | Unresolved interface | Cheapest falsification | Gap local? |
|---|---|---|---|---|---|---|
| 1 | Hand-written expectation table, written before the evaluator ran | `R106Checks.kt` `CASES` (64 rows) | outcomes per formula/cell | none for the table itself | change one expected value | local |
| 2 | Independent Python tokenizer/parser/evaluator with its own criterion parser **and a dynamic-programming wildcard matcher** (the product uses a backtracking scan) | `tools/check_r106_criteria.py` | 64/64 cases agree with Kotlin **and** the table, reasons included | shares no code with the product | edit one Python matching branch | local |
| 3 | Controls on that reference implementation | `tools/test_r106_criteria.py` (39 tests) | the lane is not self-agreeing | none | break the `?` branch in Python | local |
| 4 | Source mutation | 5 mutants, §H | each criterion defect class is detectable | mutants are author-chosen | add a 6th mutant | local |
| 5 | File round-trip: raw `<formula>` XML + openpyxl 3.1.5 | `r106.xlsx`, 12 rules | criterion text survives verbatim, quotes and `~` included | openpyxl does not evaluate formulas | corrupt a rule's text | local |
| 6 | Boundary probes | 255/256-character criterion, 100000-cell cap, grid edge, shift and lock | each boundary behaves as documented | the exact Excel behaviour at 255 is documentation-derived | widen the bound | local |

The delta between mechanisms 1 and 2 is what makes the evidence *independent*: mechanism 1 encodes what I believe,
mechanism 2 re-derives it from the text with a separately written parser and a **different wildcard algorithm**, and
mechanism 4 proves that a wrong implementation cannot pass both.

## F. Artifact requirement, and the four honest holes

Artifacts (regenerated by `python3 tools/run_r106_checks.py`, not checked in):
`testing/build/r106/artifacts/{r106.xlsx, cases.tsv, rules.tsv, values.tsv, summary.txt}`.

1. **No spreadsheet application ran.** Every criterion rule is documentation-derived (ECMA-376 and Excel's function
   documentation) plus this repository's own prior behaviour. The `#DIV/0!` for an empty average, the blank rule and
   the 255-character limit are Excel's documented behaviour; none of them was observed here.
2. **No collator, so text ordering is refused.** Excel orders two different texts by the workbook locale. This model
   has no locale, and inventing an order would make the same workbook decide differently on another machine, so
   `COUNTIF(range,"<m")` over text is refused with `cannot order two different texts under a '<' or '>' criterion`.
   Equal texts (case-insensitively) *are* orderable (`>=` is true, `>` is false), because that needs no collation.
3. **`~` escapes any following character here.** Excel documents `~` for `*`, `?` and `~`. This reader treats
   `~x` as a literal `x` for every `x`, and a trailing `~` as a literal tilde; both spellings are pinned by tests.
4. **`SUMIFS`/`COUNTIFS`/`AVERAGEIFS` are not modelled**, and a criterion over an unbounded area is still refused at
   100000 cells. The 100000 cap is this reader's bound, not Excel's.

## G. Second-order adversary — seven passes

1. **Set semantics vs value semantics.** Every shape that would pass under "the criterion is just a string compare"
   is present twice: once with matching case, once with different case and a wildcard.
2. **Type mixture.** The scanned range holds numbers, a negative, a zero, text, numeric-looking text (`" 4 "`), an
   empty-text cell, a boolean, a blank and an error, and the criteria are asserted against each type.
3. **Errors.** An error under a criterion and an error in the sum range take different code paths and different
   messages; both are refused rather than skipped.
4. **Blanks.** `""`, `"="` and `"<>"` are three separate cases, and a blank plus an empty-text cell distinguish
   "no value" from "empty value".
5. **Boundaries.** The length bound at 255 and 256; the area bound (a 100000-cell read is inside, 100001 refused);
   the grid edge under a shift; a reversed range is not accepted as a criterion range.
6. **Composition.** A criteria result inside `AND`, in arithmetic, and compared with an aggregate; a criterion that is
   itself a cell reference; a criteria function nested in nothing that reads a range as a value.
7. **The lane's own integrity.** The Python lane compares reasons as well as shapes. Its controls were found wrong
   twice during authoring — the control grid was missing its `C` column (a `SUMIF` over `C1:C5` read blanks), and one
   control assumed `~` escapes only wildcards — and both controls were corrected, not the product. The probe itself
   found one real product bug before the lane existed: the second range was compared by whole bounds rather than
   shape, which refused every practical `SUMIF`.

## H. Verification — what actually ran

| Lane | Command | Result |
|---|---|---|
| R106 probe + checker | `python3 tools/run_r106_checks.py` | `R106_CRITERIA_OK assertions=132 cases=64 decided=51 refused=13 criteria=3 rules=12`; then `64 case(s) re-evaluated in Python, 12 rule(s) raw, 12 formula(s) through openpyxl` |
| R106 controls | `python3 -m unittest test_r106_criteria` | `Ran 39 tests … OK` |
| R105 (re-run after the support edits) | `python3 tools/run_r105_checks.py` | `R105_RANGES_OK assertions=147 cases=71 decided=50 refused=21 aggregates=7 rules=12`; `Ran 20 tests … OK` |
| R92–R104 | their runners | all green, **every count identical to R105** (38, 72, 98, 72, 60, 70, 91, 68, 132, 125, 113, 220) |
| R47 kernels | `python3 tools/run_r47_contracts.py` | `R47 PASS: 2393 production-kernel assertions across 61 new kernels` |
| whole Python suite | `PYTHONPATH=… python3 -m unittest discover -s tools -p 'test_*.py'` | `Ran 2526 tests … OK (skipped=268)` (2487 at R105, +39 new controls) |
| census | `python3 tools/module_testability.py --check` | `50/50 (100.00%)`, `not_established: 0` |
| structural + Studio gate | `python3 tools/check_project.py`, `tools/native_source_audit.py --check`, `tools/check_r104_studio.py` | green (AGP 9.1.1, Gradle 9.3.1, JDK 17, compileSdk 37) |

**Source mutations executed** (each recompiled with `-Werror`, run, and reverted to a byte-identical file; the
sha256 was asserted equal after every revert):

| Mutation | Detected by | Message |
|---|---|---|
| `*` is stored as a literal instead of a run | the wildcard lane | `star wildcard: 'COUNTIF(D1:D4,"a*")=2' at B1 gave FALSE, expected TRUE` |
| `<>` behaves like `=` | the non-equality lane | `not equal counts text and booleans: 'COUNTIF(A1:A8,"<>3")=6' … gave FALSE` |
| a blank also satisfies `<>` | the blank lane | `non-blank count: 'COUNTIF(A1:A8,"<>")=7' … gave FALSE` |
| numeric text no longer folds | the coercion lane | `numeric text matches a number criterion: 'COUNTIF(A9:A9,4)=1' … gave FALSE` |
| the 255-character bound is dropped | the bound lane | `a criterion one character above the length bound: 'COUNTIF(A1:A8,"aaa…")=0' … gave UNDECIDED` |

**Substantial enough code to test — absolute and percentage.** Against the census standard (50 modules): substantial
testable subset **50/50 = 100.00%**; specification-accepted modules **0/50 = 0.00%**. This feature: criteria
functions with an executable witness **3/3 = 100%**; criterion shapes exercised (numeric equality, each of the six
operators, blank, non-blank, empty text, boolean, numeric-text folding, case-insensitive text, `*`, `?`, `~` escape,
trailing `~`, cell criterion, wildcard under `<>`, error under a criterion, error in a sum range, shape mismatch,
non-range first argument, non-range third argument, arity 2/3/4, the 255 bound on both sides) **22/22 = 100%**;
formula cases **64** of which **13/64 = 20.3%** are refusals by design; independent re-derivations **3** (Python
lane, 39 controls, 5 source mutants); written rules round-tripped **12/12** through raw XML and openpyxl.

**Tools that exist here and were used:** pinned kotlinc 2.2.21 + OpenJDK 17, openpyxl 3.1.5, CPython 3.11
`unittest`/`zipfile`/`xml.etree`/`re`, npm-installed Node modules for the R87 lanes.
**Not available, therefore not claimed:** Excel, LibreOffice, any spreadsheet renderer, Android SDK, Gradle
distribution, emulator, device, browser. **Visual inspections this round: 0.**

## I. Regressions and support edits versus R105 (`c922f71`)

**Product behaviour that changed — the point of the round.** `COUNTIF`, `SUMIF` and `AVERAGEIF` are now readable, and
with them the criterion value grammar. Consequences a caller can observe:

1. A formula naming one of those three functions now decides instead of refusing with "not a function this reader
   evaluates". That is the intent.
2. `rangeCells` was refactored into `rangeBounds` + `rangeCells`; every range reader — aggregates and criteria
   functions alike — now takes its grid-edge and 100000-cell refusal from one place. The messages are unchanged and
   R105's 21 refusals re-derive byte-identically through the Python lane.
3. A criterion may be a cell reference, so `COUNTIF(C1:C5,J1)` now decides where R105 refused `COUNTIF` by name.

No existing decision changed value. R92–R105 reproduce their counts exactly, R47's 2393 assertions are unchanged, and
the Python suite grew from 2487 to 2526 tests (39 new controls) with no test removed.

**Support edits, none of which re-pins another round's evidence:**

1. **`testing/build.gradle.kts` (+15)** — the `r106Criteria` source set and the `r106CriteriaContracts` task.
2. **`.github/workflows/android-r54-office.yml` (+3/−2)** — the task in the Gradle line, the Python lane and the
   control module in the unittest line.
3. **`tools/r106_census_deepen.py` (new, 110 lines)** — re-pins only `XlsxConditionalFormatVerdict.kt` and appends
   R106's checks and commands to the `:format:ooxml` row; it refuses to run if any other pinned file has changed.
4. **`docs/module-testability.json` / `MODULE-TESTABILITY.md`** and **`docs/verification/r11-source-inventory.json`**
   — both **regenerated** by their generators, never hand-edited; the inventory diff is one hash line, the census
   diff is the `:format:ooxml` row.

No other round's product file, probe or lane was touched. The R104 grammar lane did **not** need a change this round:
the six comparison operators it already parses are the same six a criterion starts with, but criteria are parsed by
the criteria code, not by the formula parser.

## J. Class A, and the next test

**Class A applications: still 0.** Testability is 50/50; acceptance is 0/50, and this round does not move that
number — it moves what the product can *decide*.

Next tests, in order of information per minute:

1. **Open `r106.xlsx` in Excel and LibreOffice** and compare the painted cells with `cases.tsv`. One look settles the
   blank rule, the numeric-text folding and the `#DIV/0!` behaviour — the three places documentation and
   implementation could disagree.
2. **A real workbook with `COUNTIF` over text** to learn whether refusing `"<m"` costs a real user a format, or
   whether ordering text is rare enough that the refusal is the right trade.
3. **`SUMIFS`/`COUNTIFS`**: plural criteria with several ranges, the next refused-by-name shapes, and the ones a real
   sheet uses most after the singular forms.
4. **Build the app on a laptop with Rabbit 1** and record the first real sync; the Studio gate still asserts
   configuration only.
