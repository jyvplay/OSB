# R97 — Conditional-format verdicts

**Scope of this pass.** The half of conditional formatting that R96 deliberately left out: given a sheet's
`<conditionalFormatting>` blocks and the values a reader has for the cells, **which rules fire**, in what order,
what the merged painting is, and which applicable rules this model refuses to decide. The file layer is unchanged
apart from one numeric-formatting fix; everything new is the decision procedure.

**Class A applications: still 0.** No Excel, LibreOffice, browser or Android target ran. R97 produces *verdicts*,
not pixels, and its agreement is between two implementations written by the same author plus invariants that hold
for any correct interpolation. That is weaker evidence than R96's file-level round trip, and it is labelled as
such throughout: a verdict is a claim about which rule fires, never about how a cell looks.

## A. What was added

| Piece | File | Lines |
|---|---|---|
| Verdict engine (`Value`, `Grid`, `Visual`, `Fired`, `Unresolved`, `Outcome`, `Decoration`, operand grammar, `order`/`equal` type rule, `top10`, colour scale, data bar, `percentile`, `mix`) | `XlsxConditionalFormatVerdict.kt` (new) | 586 |
| Numeric formatting fix (`BigDecimal(double)` → `BigDecimal.valueOf`) | `XlsxConditionalFormatting.kt` (**+3/−1**) | 4 |
| Probe + artifacts (`r97.xlsx`, `rules.tsv`, `values.tsv`, `verdicts.tsv`, `dxfs.tsv`) | `testing/src/r97/.../R97Checks.kt` | 571 |
| Runner | `tools/run_r97_checks.py` | 82 |
| Independent re-implementation (whole decision procedure, in Python) | `tools/check_r97_verdicts.py` | 696 |
| Arithmetic lane (interpolation, endpoints, containment, bar fractions) | `tools/check_r97_scale.py` | 175 |
| Negative controls | `tools/test_r97_verdicts.py`, `tools/test_r97_scale.py` | 237 + 155 |

Everything else is wiring: one source path in the R47 kernel (the isolated jar must compile), one Gradle task, two
checker lines and two unittest names in the CI workflow, and the census pin. `XlsxStyledBook.kt`,
`XlsxConditionalFormatting.kt`'s structure, `XlsxValidation.kt`, `XlsxProtection.kt`, `XlsxAutoFilter.kt`,
`XlsxMerges.kt`, `XlsxHyperlinks.kt`, `XlsxPrintSetup.kt`, `XlsxHeaderFooter.kt`, `XlsxBreaks.kt` and
`CondFormat.kt` are otherwise byte-identical to R96.

## B. Contract, stated so it can be wrong

**Formal model.** A verdict is computed from a sheet's blocks, a value function `Cell → {blank, num, text, bool,
error}` and a target cell. A rule `r` (kind, priority, stopIfTrue, operator, formulas, rank, percent, bottom,
thresholds, colours, format) is *applicable* when its block's `sqref` contains the cell. Applicable rules are
visited in ascending `priority`; each yields **Yes(visuals)**, **No**, or **Unsupported(reason)**. `Yes` appends a
`Fired` and a `stopIfTrue == true` rule ends the scan. The outcome is

* `Decided(fired, stopped, unresolved)` when at least one rule fired;
* `Undecided(priority, reason)` when nothing fired but an applicable rule could not be decided;
* `Decided([], false, [])` when nothing applies or every applicable rule was false.

**Information/access model.** The evaluator sees only: the blocks, the cell's value, and the values inside the
rule's own ranges. It has no formula engine, no locale collator, and no engine to ask. Consequences that are
consequences and not choices: ordering *text* is refused (collation is locale data), an `error` cell has no order,
and a boolean never orders against a number.

**Resource counted.** Rule decisions: 70 probe assertions, 45 negative controls, 33 cell verdicts over 6 sheets
covering 15 rules, and 46 rule hits plus 7 refusals recomputed independently.

**Success and failure, with quantifier order.** Success is: *for every* decision branch there exists a case that
takes it (probe, §H), **and** *for every* row of `verdicts.tsv`, *for every* field except the prose reason, the
Python recomputation equals the Kotlin output (lanes), **and** *for every* declared rule there is at least one row
in which it fired or was refused (lane 1's coverage check). Failure is asymmetric: one disagreement, or one rule
that no row exercises, fails the pass.

**Parameter regime, units, tolerances.** Priorities and ranks are integers; bar fractions are doubles compared at
`1e-12`; colours are six hex digits with half-up rounding; `percent` thresholds are 0..100 over
`[min, max]`; `percentile` uses `pos = p/100·(n−1)` with linear interpolation; a scan larger than
`MAX_RANGE_CELLS = 100_000` is refused, not trimmed. Blank cells: `0` against a number, `""` against text.

**Adjacent problems that must not be substituted.** (i) *Painting* — gradient direction, bar borders, negative
fill, icon rendering; (ii) *formula evaluation* — `FormulaEval` exists in this module and is **not** used: an
`expression` rule is decided only for `ISBLANK(ref)`, `MOD(ROW(),k)=r` and `a <op> b`, and everything else is
refused; (iii) `CondFormat.kt`'s vocabulary (`GT`, `TextContains`, `TopN`, `ColorScale2`, `DataBar`) is a different
model and remains unwired; (iv) whole-column scans, which this model bounds; (v) `extLst` data-bar extensions.

## C. Frontier, with the hypotheses it imports

Every claim below is **imported from documentation and unverified on an engine**; each is written so a single
LibreOffice run could falsify it.

1. **Priority order decides conflicts; lower wins.** Sheet-wide uniqueness is enforced by the R96 model, so the
   order is total here. *Unverified:* whether Excel applies several non-stopping rules additively in priority
   order rather than only the first — this model records every firing rule and merges properties first-wins.
2. **`stopIfTrue` ends evaluation of later rules only when the rule fired.** Trivial here; untested on an engine.
3. **A number never equals a text and sorts before every text** (`="5"=5` is FALSE, `="5">5` is TRUE). Imported
   from Excel's comparison semantics; the probe pins both directions on a text `"10"` beside a numeric `10`.
4. **Blank reads as `0` against a number and as `""` against text.** Imported; the probe's blank A5 satisfies
   neither the `>5` nor the `between` rule and sits at the low end of a scale.
5. **`top10` includes ties.** Imported; bottom-2 over `[5,5,3,9]` hits three cells.
6. **`percent` ranks take `ceil(n·p/100)` values (and ties).** Imported; 50 % of four values hits `D1`, `D2`, `D4`.
7. **`percent` thresholds interpolate linearly between the range minimum and maximum; `percentile` uses the
   order-statistic interpolation above.** Imported from the schema's wording plus common practice; the probe's
   percentile scale makes A6 exactly the middle colour because `p50([1,2,3,4,6,7,8,9,10]) = 6`.
8. **Colour interpolation is per-channel sRGB, half-up.** Imported; the observed `FF3900`, `FF7100`, `FFAA00`,
   `3333FF`, `6666FF`, `9999FF`, `BFBFBF`, `808080`, `404040` are all reproduced by the Python lane.
9. **A data bar is `(v − low) / (high − low)` clamped to `0..1`.** Imported; blanks carry no bar.

## D. Negative space — 16 shapes that look like a verdict and are not

1. "Rule list is empty, therefore nothing fires" — computed for a cell outside every block, but *wrong* when an
   applicable rule is undecidable; the modal shortcut. Detected by `Faults!E1`, which must be `undecided`, and by
   the control that flips a decided row to undecided.
2. Treating `Unsupported` as `No` — the same bug with a different spelling; detected by the 7 refusals the probe
   asserts and by lane 1's recomputation of the unresolved column.
3. Comparing the prose `reason` across implementations — pins a dialect, not a contract; lane 1 compares only
   presence.
4. Comparing `applicable` as a block count — the probe writes a *rule* count; the control that changes it fails.
5. Counting `stop = '0'` as "stop" (a truthiness bug in the Python rewrite) — caught live by lane 1 during
   development and kept as a control (`stop` flag dropped).
6. Ordering text by code point — a locale could order it differently; refused.
7. Coercing numeric-looking text to a number — makes `"10"` equal `10`; refused, with `Mixed!F1/F2` pinning it.
8. Treating an error cell as blank — refused.
9. Comparing booleans with numbers — refused.
10. Clamping a `top10` rank to `1..n` instead of extending through ties — changes which cells are painted.
11. Truncating a `percent` rank instead of rounding up — one fewer cell.
12. Reading `min`/`max` thresholds from the *workbook* instead of the rule's own ranges.
13. Silent truncation of an over-large range (skip → "no values" → "no rule fires"); now a refusal.
14. Emitting a bar for a blank cell (fraction 0) — the probe requires *no* visual.
15. Emitting a scale for a text cell — refused, not defaulted to the first colour.
16. Merging decorations by *last* writer instead of first — the probe asserts `A2` is bold *and* underlined, and the
    control flips the bold bit.

## E. Mechanism portfolio

| # | Mechanism | Central construction | Artifact | Proved portion | Unresolved interface | Cheapest falsification | Gap |
|---|---|---|---|---|---|---|---|
| 1 | Direct interpreter over the R96 model (chosen) | visit applicable rules by priority; each kind decides | `XlsxConditionalFormatVerdict.kt` | all five kinds on 33 cells; 46 hits, 7 refusals | Excel's exact ordering/painting | one LibreOffice macro printing `StopIfTrue`/priority effects | local |
| 2 | Compile each rule to a formula string and hand it to `FormulaEval` | `cellIs` → `A1>5`, `between` → `AND(A1>=2,A1<=4)`, `MOD(ROW(),2)=0` verbatim | none written | none | `FormulaEval` coverage, `ROW()` context | evaluate the compiled formulas on the same grid and compare to lane 1 | **equivalent** to the original problem for `expression` |
| 3 | Translate rules into `CondFormat`'s vocabulary and reuse its engine | `cellIs(GT)` → `CondFormat.CellIs(GT,…)`; `top10` → `TopN` | none written | mapping is mechanical for 3 of 5 kinds | `CondFormat` has no threshold resolution, no scale arithmetic, and its `TopN` lacks tie semantics | translate one sheet and diff verdicts | local for 3 kinds, equivalent for 2 |
| 4 | Truth-table / decision-diagram encoding of the operand grammar | enumerate type pairs (blank/num/text/bool/error) × operator | the `order`/`equal` table in the engine | complete: 25 type pairs, 6 operators | same as 1 | exhaustively evaluate the 150 combinations and check total ordering axioms | local |
| 5 | Interval/abstract interpretation for range rules | treat `top10`/scales as order statistics over an interval domain | this pass, implicitly (`numbersIn`) | bounds and clamps | exact tie behaviour needs the concrete multiset | feed duplicated values at the cut-off | local |
| 6 | Oracle capture: drive Excel/LibreOffice headless and record verdicts | macro writes per-cell fills to a CSV; compare | **unavailable on this host** | 0 | the whole painting question | install LibreOffice, run the same 33 cells | equivalent to the original problem |

Mechanism 1 is the one that exists; 2, 3 and 6 are the honest alternatives with their exact gaps, and 4 and 5 are
the parts of 1 that are actually exhaustive.

## F. Artifact requirement

`testing/build/r97/artifacts/` holds `r97.xlsx` (six sheets — `Numbers`, `Percentile`, `Text`, `Rank`, `Faults`,
`Mixed`), `rules.tsv` (17 fields × 15 rules), `values.tsv` (4 fields × 33 cells, blanks explicit), `verdicts.tsv`
(18 fields × 33 rows) and `dxfs.tsv`. A reader can reconstruct every verdict from `rules.tsv` and `values.tsv`
alone, which is exactly what the Python lane does.

## G. Second-order adversary — seven passes, and what each caught

1. **"The verdict is about a file the reader never saw."** `verdicts.tsv` could describe a rule set the package
   does not carry. Lane 1 reopens `r97.xlsx` with openpyxl and compares blocks and priorities; it caught that
   openpyxl **merges blocks sharing a sqref** (its list is keyed by range), so the comparison is per-sqref, not
   per-block — the same class of asymmetry R96 found with `MultiCellRange`.
2. **"Undecidable means false."** The first draft returned `Undecided` for the whole cell whenever any rule was
   refused, which erased a known painting. The outcome now carries the refused rules *beside* the fired ones; only
   a cell with nothing fired is `Undecided`. This is the modal shortcut in §D.1 and it was in this repository for
   twenty minutes.
3. **"Numbers and text are interchangeable."** The first draft coerced numeric-looking text; `="5"=5` would have
   returned TRUE. Replaced with Excel's type rule, pinned by `Mixed!F1/F2`, and the change is visible in the
   refusals count (9 → 7: two comparisons became decided instead of refused).
4. **"Stop means truthy."** The Python rewrite treated the raw TSV cell `'-'` as true and stopped after the first
   scale; lane 1 caught it on `Numbers!A1` (one hit instead of two). Kept as a control.
5. **"A block of three rules is one decision."** The `applicable` column counted blocks in Kotlin and rules in
   Python; the disagreement surfaced immediately and the column is now rules, with a coverage check that no rule
   escapes the table.
6. **"Too big to scan is empty."** A whole-column rule was skipped by the size cap, which made a scale silently
   unresolvable *and* a `top10` silently false. Both now refuse with the cap named; the probe pins the reason.
7. **"The arithmetic is plausible, so it is right."** Lane 2 recomputes every scale and bar from *its own* rule,
   then checks containment (the colour lies inside its stop pair's box) and exact endpoints. A single hex digit
   off — `FF3901` instead of `FF3900`, or `FFFF00` substituted at a mid-point — fails.

Eighth pass, unassisted: **"what would a spreadsheet do that this cannot say?"** Paint, order text, evaluate
formulas, render icons, draw gradients. §J.

## H. Verification — what ran

Host JVM plus Python 3.11; openpyxl 3.1.5. No Gradle, no Android SDK, no Excel, no LibreOffice, no browser.

```
python3 tools/run_r97_checks.py
R97_CF_VERDICT_OK assertions=70 sheets=6 values=33 verdicts=33 rules=15 dxfs=6
R97 verdicts: 33 cell verdict(s) over 6 sheet(s), 46 rule hit(s), 7 unresolved rule evaluation(s),
    2 cell(s) with no verdict at all; recomputed independently in Python;
    openpyxl confirmed 15 rule(s) in 6 sheet(s)
R97 scales: 14 colour-scale visual(s) and 4 data-bar visual(s) over 30 cell(s), 3 range-relative rule(s),
    recomputed and bounded

PYTHONPATH=tools python3 -m unittest test_r97_verdicts test_r97_scale
Ran 45 tests ... OK

python3 tools/run_r47_contracts.py
R47 PASS: 2393 production-kernel assertions across 61 new kernels (+5 touched across 48/50 modules).

python3 tools/module_testability.py --check   → 50/50 (100.00 %), 0 accepted
python3 tools/native_source_audit.py --check  → 231 requirements, 36 obligations
Full Python suite (R88–R97 lanes regenerated first): Ran 806 tests ... OK (skipped=257)
```

The 257 skips are suites whose corpora come from Gradle tasks that cannot run on this host. CI is red at the Kotlin
compile step on every commit including `main`, so there is no CI number to quote either. The probe's 70 assertions
cover: priority order and the `stopIfTrue` cut on five rows; inclusive `between` at both bounds; `MOD(ROW(),2)=0`
on the surviving even rows; the blank cell's behaviour in four rules; scale endpoints, a mid-stop interpolation
(`FFAA00`), the blank's low-end colour and a percentile stop exactly on the order statistic; bar fractions `0.1`
and `1.0` and the absence of a bar on a blank; case-insensitive text equality; `ISBLANK` as an absolute reference;
an ordering over text refused; a rule set whose only rule is refused producing no verdict; bottom-2 with ties;
`percent` 50 over four values; an error cell and a boolean refused; Excel's number/text rule in both directions;
an over-large range refused; property merging (`bold` *and* `underline`, no fill from a rule that did not fire); a
cell outside every block; and the percentile/mix arithmetic.

## I. Regressions versus the best prior codebase (R96, `5a15f42`)

* **None known.** R96's probe (`assertions=60`) and both its lanes ran unchanged in this pass; R92/R93 were
  regenerated and their lanes are green; R47 still reports 2393 assertions over the same 61 kernels.
* `XlsxConditionalFormatting.kt` changed by +3/−1: `BigDecimal(double)` → `BigDecimal.valueOf(double)` in the
  `cfvo` number formatter. For every value the model emitted before (integers and exact binary fractions) the text
  is identical; for a value like `0.1` the old code wrote the binary expansion. That is a strict improvement, not a
  behaviour change, and the file's rules, bounds and parse paths are untouched.
* Everything else is additive: one new source file, one probe, one runner, two lanes, two control suites, one
  Gradle task, one R47 kernel path, workflow lines, census pin.
* Test count: 806 `OK` (257 skipped) against R96's 761 `OK` (257 skipped) — exactly the 45 new controls, no test
  lost. The R88–R97 lane sweep was re-run end to end: all ten `run_r*_checks.py` runners end `rc=0`, after three
  (R88–R90) first failed on a missing `python-docx` in this fresh sandbox — the dependency the workflow pins —
  and passed once it was installed. The exceljs renderer lane still skips because `testing/build/r86sheet` comes
  from a Gradle task; the docx-preview lane runs (`npm ci --prefix tools/r87node`, 221 packages).

## J. Class A, the PR, and the next test

**Class A applications: 0.** A conditional format is valuable only when it paints, and no engine ran. What exists
now is: a file model that says the right thing (R96), and a decision procedure that agrees with an independent
re-implementation on 33 cells and refuses 7 rule evaluations it cannot do honestly (R97). What is missing is
everything a user sees: `iconSet` and the text predicates, `extLst` data bars with gradients and axes, text
collation, formula evaluation for arbitrary `expression` rules, and an engine to look at.

**Census.** 50/50 modules (100.00 %) meet the bounded testability standard; **0/50 (0.00 %) are specification
accepted**. `:format:ooxml` now counts 70 production files and 249 check artifacts under the re-pinned hashes.

**Next test that could fail.** Install LibreOffice headless, open `r97.xlsx`, and ask each sheet for the fill
colour and font flags of every cell in `verdicts.tsv` — a macro writing a CSV is enough. That single run would
falsify or confirm §C.1–§C.9 at once, and it is the only test that can move this component past "two
implementations agree".
