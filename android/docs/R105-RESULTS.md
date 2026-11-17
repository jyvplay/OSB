# R105 — Ranges and the seven aggregates a conditional format actually uses

R104 gave an `expression` rule a real grammar, but every operand was a single value. So the three most common
conditional formats in existence were still refused:

```
SUM(A1:A3)>10        AVERAGE(B1:B5)<2        COUNT(A1:A9)=9
```

Not "answered badly" — refused, because `SUM` was not a function and `A1:A3` was not a form. A reader that cannot
read `SUM(A1:A3)>10` is a reader for a language nobody writes. R105 adds `from:to` ranges and `SUM`, `COUNT`,
`COUNTA`, `AVERAGE`, `MIN`, `MAX`, `MEDIAN`, with Excel's range-versus-scalar rules and the R103 shift rule applied
to **both** endpoints.

One product file changed again (`XlsxConditionalFormatVerdict.kt`, **+145/−2**). Everything else
here is evidence about it.

## A. Formal model

**Admissible objects.** The R104 grammar plus:

```
primary := ... | cell-ref (':' cell-ref)?
```

and seven more function names, each taking 1..8 arguments, each of which may be a range. A range is bounded on both
sides by a cell reference that carries its own `$` locks. Whole-column (`A:A`), whole-row (`1:1`), 3-D (`Sheet1:Sheet3!A1`)
and defined-name ranges are **not** in the grammar.

**Information model.** Unchanged: the rule's `sqref`, the formula text and a sparse grid of the four values this
model knows (blank, number, text, boolean) plus error values. No workbook, no sheet name, no recalculation engine,
no locale.

**Resource counted.** Parsing stays `O(tokens)`. Evaluation is now `O(cells read)`, and one rule may read at most
`MAX_RANGE_CELLS = 100000` cells — the same bound the component already applies to its colour scales, data bars and
icon sets. A range above it is refused, never truncated. Per cell the cost is one grid read.

**Success, with quantifier order.** For every block, every token of its `sqref`, every cell of that token, and every
`expression` rule in it: the verdict is `Yes` if the formula evaluates to a true boolean or a non-zero number, `No` if
false/zero/blank, and `Unsupported(reason)` otherwise. A range contributes the cells its two shifted endpoints name.

**Regime, units, tolerances.** Grid `1..1048576 × 1..16384`; area bound 100000 cells; `MEDIAN` of an even count is the
mean of the two middle values (exact halves are exact in binary floating point for the values tested); no rounding
tolerance anywhere, because there are no tolerances in the model — an aggregate either sums exactly representable
values or the test uses `ROUND` deliberately.

**Adjacent problems that must not be substituted.** (i) *Array formulas* — `A1:A3>0` outside an aggregate is refused,
not spilled; Excel's implicit intersection is not modelled. (ii) *Cell-formula evaluation* — `FormulaEval` has its own
range semantics for a different caller and is untouched. (iii) *Cross-sheet references* — still out. (iv) *Painting* —
a verdict is still not a rendered cell. (v) *`SUMIF`/`COUNTIF` criteria strings* — not implemented, refused by name
because the function is unknown, and listed in §K.

## B. Outcome space

Three outcomes per rule, as before: `Yes`, `No`, `Unsupported(reason)`. New in R105 is a *nested* outcome: an
aggregate can itself produce an error value (`#DIV/0!` from `AVERAGE` of no numbers, `#NUM!` from `MEDIAN` of no
numbers). Those propagate exactly like a reader-computed error: as a refusal carrying the code, never as "did not
fire". Measured over the round's 71 cases: **50 fire or do not fire, 21 are refused** (21/71 = 29.6%).

- **H+ (achievable):** all seven aggregates over bounded rectangular ranges, with Excel's ignore-text/bool/blank rule
  inside a range, are decidable from cached values alone. Executed: yes, 50 decided cases.
- **H− (impossible under these constraints):** a formula whose value depends on something the grid does not carry —
  a cell's *formula* text, another sheet, a defined name, or a locale-dependent collation — cannot be decided here.
  Refused, not guessed.
- **H∂ (boundary):** the 100000-cell cap and the grid edge are the two boundaries where the answer changes from a
  number to a refusal. Both are pinned by cases that sit exactly on each side.

## C. Frontier — what this closed, and what was verified

Imported result, restated with its hypotheses: Excel's aggregate functions **ignore text, logical values and blanks
when the argument is a range reference**, count them when the value is supplied *directly*, propagate an error value
from any cell they read, and answer `#DIV/0!` (`AVERAGE` of nothing) or `#NUM!` (`MEDIAN` of nothing). The hypothesis
is "for a range argument, the reference is read cell by cell, not as one value".

That hypothesis is precisely what R105 implements and measures. Before R105 those shapes were refused; after, they are
decided, with the exception listed in §F.2 (scalar classification for `COUNT`/`COUNTA`) and §F.1 (no spreadsheet ran).

**Coverage now.** Functions the grammar reads: **23** (16 from R104 + 7 new). Formula cases across the two rounds:
**171** (100 + 71). Refusal shapes pinned: **48** (27 + 21).

## D. Negative space — nineteen shapes that look like solutions but do not satisfy the contract

| # | Shape | Why it fails | The test that detects it |
|---|---|---|---|
| 1 | Read the range as its top-left cell | `SUM(A1:A3)` answers 1 | `sum of a range` = 6 |
| 2 | Read only the first row of the range | `A1:A3` gives 1 | mutation 1 (§H) |
| 3 | Count text in `COUNT` | `COUNT(A1:A8)` gives 7 | `count of numbers in a range` = 5 |
| 4 | Count booleans in `COUNT` inside a range | same case, 6 | same case |
| 5 | Treat blank as 0 in `COUNT` | 9 | same case |
| 6 | `AVERAGE` of no numbers = 0 | silently paints | `average of no numbers` refuses with `#DIV/0!` |
| 7 | `MEDIAN` of no numbers = 0 | same | `median of no numbers` refuses with `#NUM!` |
| 8 | Even median takes the upper middle | `MEDIAN(1,2,3,4)` = 3 | `median of an even count` = 2.5; mutation 3 |
| 9 | Ignore an error inside a range | `SUM(A1:C1)` = 1 | `an error inside a range` refuses with `#N/A in SUM at C1` |
| 10 | Propagate an error without its cell | undiagnosable | same case asserts `at C1` |
| 11 | Clamp a range that leaves the grid | reads different cells | `a range that shifts off the grid` |
| 12 | Expand a whole-column range to the used area | invents a bound Excel does not have | `a whole-column range` refuses |
| 13 | Drop the area bound | 250000 reads, silently | `a range above the reader's bound`; mutation 4 |
| 14 | Do not shift the range at all | every cell reads `A1:A3` | `a shifted range one row down` |
| 15 | Shift only the first endpoint | the range grows as the scan descends | `a shifted range two rows down` = 3 |
| 16 | Normalize a reversed range in one coordinate only | `SUM(A3:A1)` = 0 | `a reversed range is normalized` = 6 |
| 17 | Treat a range as a value (implicit intersection) | `A1:A3>0` answered about one cell | `a range in a comparison` refuses |
| 18 | Coerce a text scalar in `SUM` | `SUM(A4)` answers 0 | `a text scalar inside an aggregate` refuses |
| 19 | Accept `SUM()` as 0 | invents an arity Excel rejects | `an aggregate without arguments` refuses |

The modal shortcut is #1/#14: "a range is a reference, so read the reference". It fails because a range denotes a
*set* of cells and the aggregate is defined over the set; the cases `sum of a range` and `a shifted range two rows
down` (which expects 3, not 6) cannot both pass under that shortcut.

## E. Mechanism portfolio — six distinct mechanisms

| # | Mechanism | Artifact | Proved portion | Unresolved interface | Cheapest falsification | Gap local? |
|---|---|---|---|---|---|---|
| 1 | Hand-written expectation table, written before the evaluator ran | `R105Checks.kt` `CASES` (71 rows) | outcomes per formula/cell | none for the table itself | change one expected value | local |
| 2 | Independent Python tokenizer/parser/evaluator with its own range engine | `tools/check_r105_ranges.py` | 71/71 cases agree with Kotlin **and** the table | shares no code with the product | edit one Python aggregate branch | local |
| 3 | Controls on that reference implementation | `tools/test_r105_ranges.py` (20 tests) | the lane is not self-agreeing | none | break `median` in Python | local |
| 4 | Source mutation | 4 mutants, §H | each defect class is detectable | mutants are author-chosen | add a 5th mutant | local |
| 5 | File round-trip: raw `<formula>` XML + openpyxl 3.1.5 | `r105.xlsx`, 12 rules | formula text survives verbatim, `$` included | openpyxl does not evaluate formulas | corrupt a rule's text | local |
| 6 | Boundary probes | 100000-cell cap, grid edge, reversed corners, 800-cell walk | the two boundaries behave as documented | the exact Excel cap is *not* claimed to be 100000 | widen the cap | local |

The delta between mechanisms 1 and 2 is what makes the evidence *independent*: mechanism 1 encodes what I believe,
mechanism 2 re-derives it from the text with a separately written parser, and mechanism 4 proves that a wrong
implementation cannot pass both.

## F. Artifact requirement, and the three honest holes

Artifacts (regenerated by `python3 tools/run_r105_checks.py`, not checked in):
`testing/build/r105/artifacts/{r105.xlsx, cases.tsv, rules.tsv, values.tsv, summary.txt}`.

1. **No spreadsheet application ran.** Every aggregate rule is documentation-derived (ECMA-376 and Excel's function
   documentation) plus this repository's own prior reference behaviour. Where Excel's answer is disputed — e.g.
   `COUNT(TRUE)` counts a directly-supplied boolean but not one found in a range — this build makes a documented
   choice and pins it with a test instead of pretending to know.
2. **Scalar classification is narrower than Excel for `COUNT`.** `COUNT(TRUE)` returns 0 here (a boolean is not a
   number); Excel counts a directly supplied boolean. This is a *documented narrowing with a pinned case*, not an
   oversight, and it is the only place the aggregate semantics knowingly differ from Excel's documented behaviour.
3. **`100000` is this reader's bound, not Excel's.** A formula summing more than 100000 cells is refused here and
   would be computed by Excel. The refusal names the area, so the boundary is visible rather than mysterious.

## G. Second-order adversary — seven passes

1. **Set semantics vs reference semantics.** Every case that would pass under "read the top-left cell" was written
   first (§D.1/§D.14), and the shift cases make the difference numeric (6 vs 5 vs 3).
2. **Type mixture inside a range.** One range contains a number, a text, a blank, a boolean, a zero, a negative and a
   numeric-looking text (`" 4 "`); `SUM`, `COUNT`, `COUNTA`, `MIN`, `MAX`, `MEDIAN` are each asserted on it.
3. **Errors.** An error at the range's third cell is asserted for both a range read (`SUM(A1:C1)`) and a scalar read
   (`COUNT(C1)`), with different messages, because they take different code paths.
4. **Empty decision cases.** `AVERAGE`/`MEDIAN` over a range with no numbers, and over a blank scalar.
5. **Boundaries.** Exactly the cap (a 40000-cell range is read; a 250000-cell range is refused), the grid edge after
   a shift, a reversed corner, and a single-cell range.
6. **Composition.** Aggregates inside `AND`, inside arithmetic, inside `ROUND`, an aggregate of an aggregate, and two
   aggregates compared with each other.
7. **The lane's own integrity.** The Python lane compares reasons as well as shapes, so a message drift fails; its
   controls were themselves found wrong twice during authoring (an error cell inside a large range changed a sum, and
   a lock test at the anchor assumed an offset) — both controls were corrected, not the product.

## H. Verification — what actually ran

| Lane | Command | Result |
|---|---|---|
| R105 probe + checker | `python3 tools/run_r105_checks.py` | `R105_RANGES_OK assertions=147 cases=71 decided=50 refused=21 aggregates=7 rules=12`; then `71 case(s) re-evaluated in Python, 12 rule(s) raw, 12 formula(s) through openpyxl` |
| R105 controls | `python3 -m unittest test_r105_ranges` | `Ran 20 tests … OK` |
| R104 (re-run after the support edits) | `python3 tools/run_r104_checks.py` | `R104_GRAMMAR_OK assertions=220 cases=100 decided=73 refused=27 functions=16 rules=14`; `Ran 33 tests … OK` |
| R92–R102, R104 | their runners | all green, **every count identical to R104** (38, 72, 98, 72, 60, 70, 91, 68, 132, 125, 113, 220) |
| R103 | `python3 tools/run_r103_checks.py` | green after one support edit, counts unchanged (`113`, `106 cells`, `31 sampled`, `4 undecided`) |
| R47 kernels | `python3 tools/run_r47_contracts.py` | `R47 PASS: 2393 assertions across 61 kernels` |
| whole Python suite | `python3 -m unittest discover -s tools -p 'test_*.py'` | `Ran 2487 tests … OK (skipped=268)` |
| census | `python3 tools/module_testability.py --check` | `50/50 (100.00%)`, `not_established: 0` |
| structural + Studio gate | `python3 tools/check_project.py`, `tools/check_r104_studio.py` | green (AGP 9.1.1, Gradle 9.3.1, JDK 17, compileSdk 37) |

**Source mutations executed** (each recompiled, run, and reverted to a byte-identical file):

| Mutation | Detected by | Message |
|---|---|---|
| a range reads only its first row | the probe's range lane | `sum of a range: 'SUM(A1:A3)=6' … gave FALSE` |
| text inside a range treated as an error | the mixed-range lane | `sum of a range with text … gave UNDECIDED` |
| even median returns the upper middle | the median lane | `median of an even count: 'MEDIAN(1,2,3,4)=2.5' … gave FALSE` |
| the area bound is not enforced | the bound lane | `a range above the reader's bound … gave UNDECIDED` |

**Substantial enough code to test — absolute and percentage.** Against the census standard (50 modules): substantial
testable subset **50/50 = 100.00%**; specification-accepted modules **0/50 = 0.00%**. This feature: aggregates with an
executable witness **7/7 = 100%**; range shapes (single, multi-row, 2-D, reversed, locked-row, locked-column,
off-grid, above-bound, as-value) **9/9 = 100%**; formula cases **71** of which **21/71 = 29.6%** are refusals by design;
independent re-derivations **3** (Python lane, 20 controls, 4 source mutants); written rules round-tripped **12/12**
through raw XML and openpyxl.

**Tools that exist here and were used:** pinned kotlinc 2.2.21 + OpenJDK 17, openpyxl 3.1.5, CPython 3.11
`unittest`/`zipfile`/`xml.etree`/`decimal`, npm-installed Node modules for the R87 lanes.
**Not available, therefore not claimed:** Excel, LibreOffice, any spreadsheet renderer, Android SDK, Gradle
distribution, emulator, device, browser. **Visual inspections this round: 0.**

## I. Regressions and support edits versus R104 (`4b1e679`)

**Product behaviour that changed — the point of the round.** `SUM`, `COUNT`, `COUNTA`, `AVERAGE`, `MIN`, `MAX`,
`MEDIAN` and `A1:A3`-style ranges are now readable. Consequences that a caller can observe:

1. A formula naming one of those functions now decides instead of refusing. That is the intent.
2. A bare range used as a value now refuses with **`a range is not a single value`** instead of a colon lexer error.
   The outcome class (`Undecided`) is unchanged; only the reason text moved, and it now says what is wrong.
3. `SUM()` with no arguments is refused by arity (`takes 1 to 8 argument(s), not 0`).

No existing decision changed value. R92–R104 reproduce their counts exactly, R47's 2393 assertions are unchanged, and
the Python suite grew from 2467 to 2487 tests (20 new controls) with no test removed.

**Support edits, each re-pinned in the census with its reason:**

1. **`R104Checks.kt` (2 examples).** The unknown-function case used `SUM(A1,A2)` — `SUM` is real now, so it names
   `FOO(A1,A2)`. The range case expected the old `':'` reason; it now expects the range refusal. Both keep their
   property. All 220 assertions and 100 cases are unchanged.
2. **`check_r104_grammar.py` (+8)** and **`test_r104_grammar.py` (2 lines).** The R104 lane learns that `:` starts a
   range and refuses it as a value, so its 100 cases still re-derive. It stays the R104-era grammar by design; the
   range grammar is R105's lane.
3. **`R103Checks.kt` (1 list entry).** `SUM(A1:A3)>0` in its "looks like a reference but is not" list became a real
   formula; the entry is now `FOO(A1:A3)>0`. The assertion and all 113 counts are unchanged.
4. **`testing/build.gradle.kts` (+14), `.github/workflows/android-r54-office.yml` (+3), `tools/r105_census_deepen.py`
   (new), `docs/module-testability.json` / `MODULE-TESTABILITY.md` (regenerated), `docs/verification/r11-source-inventory.json`
   (regenerated).**

## J. Class A, and the next test

**Class A applications: still 0.** Testability is 50/50; acceptance is 0/50, and this round does not move that
number — it moves what the product can *decide*.

Next tests, in order of information per minute:

1. **Open `r105.xlsx` in Excel and LibreOffice** and compare the painted cells with `cases.tsv`. One look settles the
   seven aggregate rules and the `COUNT(TRUE)` divergence (§F.2).
2. **A workbook with `SUM(A1:A3)` over a 100001-cell range**, to learn whether my cap is defensible in practice or
   whether real workbooks need it lifted.
3. **`SUMIF`/`COUNTIF`**: the next refused-by-name shapes, and the ones a real sheet uses most after `SUM`. Criteria
   strings need their own grammar, which is why they are not in this round.
4. **Build the app on a laptop with Rabbit 1** and record the first real sync; the Studio gate still asserts
   configuration only.
