# R103 — What a conditional-format formula's references mean, and the bug that hid it

Every conditional-format rule that carries a formula — `expression`, and the operands of `cellIs` — addresses cells
by A1 reference. Excel reads those references **relative to the cell being evaluated**, anchored at the top-left of
the range token that covers it, with `$` locking one coordinate. Until R103 this evaluator read the *literal*
reference every time. A rule `A1>5` on `A1:A9` therefore asked the same question of `A1` for all nine cells, and
`$`-locked references were not even parseable.

That is the shape of the defect: not a missing feature, but a predicate that answered about one cell while
claiming to answer about nine. One product file changed (`XlsxConditionalFormatVerdict.kt`); everything else here is
evidence about it.

## A. Formal model

**Admissible objects.** A rule is `(kind, priority, sqref-token, formula)`; a formula is either `ISBLANK(ref)`,
`MOD(ROW(),k)=r`, a `cellIs` operator with one or two operands, or `operand op operand`, where an operand is a
number, a quoted string, `TRUE`/`FALSE`, or a reference token `[$]COL[$]ROW`.

**Information model.** The evaluator sees a block's `sqref` (one or more space-separated range tokens), the
formula text, and a sparse grid of values. It does not see the workbook, the sheet name, or a recalculation engine.

**Resource counted.** One verdict costs `O(rules × |sqref tokens|)` reference resolutions for formula kinds —
constant work per cell, unlike the range-relative kinds, which pay `O(cells)` per reference.

**Success, with quantifier order.** For every block `B`, every token `t ∈ B.sqref`, every cell `c ∈ t`, and every
rule `r ∈ B.rules`: the reference `ref` written in `r`'s formula denotes the cell
`(ref.col + [¬col$](c.col − t.top.col), ref.row + [¬row$](c.row − t.top.row))`. Failure is the negation. The
implementation must also refuse, not approximate, when that address leaves the grid.

**Regime and boundaries.** Rows `1..1048576`, columns `1..16384` (the SpreadsheetML grid); `$` before the column
locks the column, before the row locks the row, both lock both; no unit conversion, no tolerance. A shift outside
the grid makes the rule *undecided*, never "did not fire".

**Adjacent problems that must not be substituted.** (i) *Formula evaluation in cells* — `FormulaEval` has its own
reference semantics for a different caller. (ii) *Defined names* — a named range is not a shifted reference.
(iii) *Cross-sheet references* — `Sheet2!A1` is out of grammar and stays out (§B). (iv) *Whole-column ranges*
(`A:A`) — out of grammar. (v) *Excel's recalculation order* — irrelevant: this model reads cached values.

## B. Outcome space

**H+ (achievable, achieved).** The shift is arithmetic on two integers plus a parse of two optional `$` markers.
The probe asserts it over ten rules and 106 cells; three source mutations (no shift, no lock, block anchor) were
executed and each was caught (§H).

**H− (impossible here).** Two claims are out of reach:

1. **What Excel does with a multi-token `sqref`.** The UI never writes one for a relative formula — it splits the
   range into separate rules. A file can carry `sqref="G1:G9 H1:H9"` with one rule, and nothing on this host says
   whether Excel anchors such a rule per token or at the first token. This implementation anchors **per token**
   (the token that covers the cell), because that is the reading under which each range behaves as the rule
   written for that range. It is a hypothesis, and the fixture's `H5` cell is exactly the discriminating case.
2. **Whether Excel refuses or clamps a shift off the grid.** We refuse (`Undecided`). Excel may produce `#REF!` and
   apply nothing. Both are defensible; ours is stated.

**H∂ (boundary).** Row 1048576. A rule `A1048576>0` on `M1:M3` reads `A1048576` at `M1` and would need
`A1048577` at `M2`. The verdict is therefore *decided* for one cell and *undecided* for the next two. That
asymmetry is asserted, and it is why "undecided" is per cell rather than per rule.

## C. Frontier

| Question | Best available answer | Status here |
|---|---|---|
| What is a reference relative to? | the cell being evaluated, anchored at the applied range's top-left | implemented, asserted over 106 cells |
| What does `$` lock? | the coordinate it precedes; `$A1` locks the column, `A$1` the row | both single locks and the double lock asserted |
| What is the grid bound? | 1048576 × 16384 (XFD1048576) | asserted at the row edge; column edge follows the same code |
| Is `A$1` legal as a `cellIs` operand? | yes — `ST_CfRule` formulas are ordinary formula text | implemented through a shared operand parser |
| What about `Sheet2!A1`? | legal Excel, outside this grammar | refused as undecided, reason names the token |
| What did Excel write for `ISBLANK`? | `ISBLANK($B$4)` when the user clicks B4 from another cell | the R97 fixture now writes the locked form (§I) |

The open interface is the multi-token anchor (§B.1): every other row above is decided by the schema or by the
probe, and that one needs Excel or LibreOffice to observe.

## D. Negative space — 12 shapes that look like fixes but are not

1. **Shift only the row** — passes any single-column fixture, fails `E2` (needs the column offset too).
2. **Shift only the column** — symmetric failure.
3. **Shift everything, ignore `$`** — fires on `C2`, where the fully locked rule must not fire.
4. **Honour `$` but anchor at the block's first token** — reads `H5` where Excel reads `G5`; the probe fails at `H5`.
5. **Anchor at the sheet origin `A1`** — right for `A1:A9`, wrong for anything else; the `C5:C7` case fails.
6. **Clamp an off-grid shift to the last row** — silently reads a different cell than Excel; `M2` must be undecided.
7. **Treat an off-grid shift as "did not fire"** — the same mistake inverted: it makes an unknown answer into a
   decided one, and `M2` would return `-` instead of `UNDECIDED`.
8. **Parse `$` but strip it into the same key** — indistinguishable from (3) on any locked fixture.
9. **Reject `$` as an unsupported operand** — this is what the code did before R103: honest, but it makes every
   rule Excel writes from its own dialog undecided, which is a silent feature loss rather than an error.
10. **Shift relative to the cell instead of the anchor** — `A1` would then always read the cell being evaluated, so
    `ISBLANK(J1)` on `J1:J3` fires everywhere; the probe's `J2`/`J3` cases fail.
11. **Read a cross-sheet reference by dropping the sheet prefix** — compares a different sheet's `A1` and reports a
    verdict; the probe requires `Undecided` naming the token.
12. **Return the anchor's answer for the whole block (the pre-R103 code)** — the modal shortcut, and the actual
    defect: it agrees with the correct answer on every single-cell block, which is why 70 R97 assertions and 113
    R101 assertions never saw it.

**The test that detects the modal shortcut** is `A1:A9` with `A1>5` over `[1,9,9,9,9,9,9,9,9]`: the correct answer
fires on `A2..A9`, the pre-R103 answer fires on nothing. The probe asserts `A2`, `A3` and `A9` fire, and mutation
`noshift` fails at `E2` inside the same run.

## E. Mechanism portfolio

| # | Mechanism | Artifact | Proved portion | Unresolved interface | Cheapest falsification |
|---|---|---|---|---|---|
| 1 | Raw OPC formula text | `rules.tsv` × `sheet1.xml` | the formulas, `$` markers included, survive the writer | nothing about Excel | `test_a_dropped_absolute_marker_is_caught` |
| 2 | Public reader round trip | 10 rules, 10 blocks | formula text and operator field-for-field | a shared writer/reader bug | change a formula in the table |
| 3 | openpyxl 3.1.5 | same workbook | an unrelated parser reads the same formulas | openpyxl does not evaluate | `test_the_reader_lane_agrees_when_openpyxl_is_present` |
| 4 | Python oracle | `rules.tsv` + `values.tsv` → `verdicts.tsv` | the shift rule, re-implemented (locks, anchor, grid edge, undecided-if-nothing-fired) | Excel's multi-token anchor | `test_a_collapsed_anchor_is_caught` |
| 5 | Source mutation | three compiled mutants | that the probe is not vacuous | — | see §H |
| 6 | Boundary case | `M1:M3` vs row 1048576 | decided/undecided split within one rule | Excel's `#REF!` behaviour | `test_a_boundary_rule_moved_off_the_grid_edge_is_caught` |
| 7 | Grammar refusals | five non-reference tokens | `A0`, `A1:A2`, `"text"&A1`, `SUM(...)`, `A1048577` all stay undecided | full formula grammar | change one token's expectation |

## F. Artifact requirement, and the three honest holes

`tools/run_r103_checks.py` writes `r103.xlsx` (ten rules, one sheet) plus `rules.tsv`, `values.tsv`,
`verdicts.tsv` (106 cells) and `summary.txt`. Current output:

```
R103_SHIFT_OK assertions=113 refusals=3 rules=10 blocks=10 cells=106 sampled=31 undecided=4
R103 check: 10 rule(s) raw, 10 formula(s) through openpyxl, 106 verdict(s) recomputed from the shift rule
```

The holes:

1. **The multi-token anchor is a hypothesis** (§B.1). It is implemented, documented, and pinned by a
   discriminating fixture; it is not observed.
2. **Off-grid shifts are refused, not modelled as `#REF!`** (§B.2). A caller that wants Excel's behaviour would
   have to decide what "#REF! in a conditional format" means; this model declines to guess.
3. **The grammar is still narrow.** `AND`/`OR`, function calls, whole-column ranges and cross-sheet references are
   all outside it, and each is *refused as undecided* rather than evaluated approximately. That is unchanged by
   R103, and it means a real workbook's rules are often undecided — which the outcome type makes visible.

## G. Second-order adversary — seven passes

1. **Attack the anchor.** Make `anchorFor` return the block's first token: caught at `H5` (§H).
2. **Attack the lock.** Ignore `$` in `shifted`: caught at `C2`.
3. **Attack the shift.** Drop the column offset: caught at `E2`.
4. **Attack the direction.** Swap `cell.col - anchor.col` for `anchor.col - cell.col`: every relative case
   inverts; `A2` (which must fire) stops firing.
5. **Attack the grammar.** Accept `$` everywhere including inside a quoted string: `"$A$1"` would become a
   reference; the string path is checked before the reference path, so this fails closed.
6. **Attack the tables.** Alter a recorded verdict, a value, a fired list or the `UNDECIDED` marker: all four are
   controls (`test_a_changed_value_is_caught` and friends).
7. **Attack the oracle itself.** Replace a locked reference with an unlocked one in both the table *and* the
   workbook: the oracle then re-derives different verdicts than were recorded, which is the check that the oracle
   is doing work rather than echoing.

## H. Verification — what actually ran

| Lane | Command | Result |
|---|---|---|
| R103 probe + 3 lanes | `python3 tools/run_r103_checks.py` | `R103_SHIFT_OK assertions=113 refusals=3 rules=10 blocks=10 cells=106 sampled=31 undecided=4`, then `10 raw, 10 openpyxl, 106 recomputed` |
| R103 controls | `PYTHONPATH=tools python3 -m unittest tools.test_r103_shift` | `Ran 19 tests … OK` |
| R92, R94–R102 | their runners | green; every count identical to R102 **except R97**, see §I |
| R47 kernels | `python3 tools/run_r47_contracts.py` | `R47 PASS: 2393 assertions across 61 kernels` |
| whole Python suite | `python3 -m unittest <all tools/test_r*.py>` | `Ran 2288 tests … OK (skipped=269)` |
| census | `python3 tools/module_testability.py --check` | `50/50 (100.00%)`, `not_established: 0` |

**Source mutations executed** (each recompiled and run, then reverted):

| Mutation | Detected by | Message |
|---|---|---|
| column offset dropped | the probe's evaluator lane | `E2 gave 3, expected 3,4` |
| `$` ignored | same | `C2 gave 2, expected -` |
| block's first token as anchor | same | `H5 gave -, expected 5` |

**Substantial enough code to test — absolute and percentage.** Against the census standard (50 modules):
substantial testable subset **50/50 = 100.00%**; specification-accepted modules **0/50 = 0.00%**. This feature:
reference shapes with an executable witness **9/9 = 100%** (relative, both locks, `$col`+rel, rel+`$row`, locked
`ISBLANK`, relative `ISBLANK`, `cellIs` operand, cross-sheet refusal, grid-edge refusal); assertions **113**;
negative controls **19**; independent re-derivations **2** (Python oracle, three source mutants).

**Tools that exist here and were used:** pinned kotlinc 2.2.21 + OpenJDK 17, openpyxl 3.1.5, CPython 3.11
`unittest`/`zipfile`/`xml.etree`, npm-installed node modules for the R87 lanes.
**Not available, therefore not claimed:** Excel, LibreOffice, ExcelJS, any browser, emulator, Android device,
theorem prover, SMT solver, spreadsheet renderer.

## I. Regressions and support edits versus R102 (`9c3922d`)

**Product behaviour that changed — the point of the round.** Unlocked references now shift; `$` is now parseable.
Any caller that relied on the literal reading was relying on the defect. In particular R97's text fixture used
`ISBLANK(B4)` on `B1:B5` and asserted that a blank test fired at `B1`, `B2` and `B3` — under Excel's rule that
unlocked reference tests `B5`, `B6`, `B7`, so the assertion only passed because the reference never moved. The
fixture's own comment called it "the absolute blank test", which is what it now is.

Two evidence-side edits, both minimal:

1. **`R97Checks.kt` (test probe, +4/−1).** `ISBLANK(B4)` → `ISBLANK($B$4)`, with a comment stating why. All 70
   assertions and all 33 verdicts are unchanged after the edit.
2. **`tools/check_r97_verdicts.py` (R97's oracle, +48/−12).** Its Python re-derivation resolved references
   absolutely and could not parse `$`; it now implements the R103 rule (locks, per-token anchor, off-grid refusal).
   Without this the oracle would have kept verifying the pre-R103 semantics — the failure mode this project treats
   as the real risk.

Also corrected: the `XlsxConditionalFormatVerdict` header still described R102's completed detail fields as "not
here". The list now names what is genuinely absent (`vertAlign`/shadow/outline, gradients, cross-sheet).

No other product behaviour changed: R92, R94–R96 and R98–R102 reproduce their counts exactly, and R47's 2393
assertions are unchanged.

## J. Class A, and the next test

**Class A applications: still 0.** The census percentage measures executable testability, not acceptance. What R103
changes is that the conditional-format *predicate* now answers about the cell it is asked about, which is a
correctness property rather than a feature count.

Next tests, in order of information per minute:

1. **LibreOffice headless render** of `r103.xlsx` with the multi-token block, compared against `verdicts.tsv` —
   settles §B.1, the one hypothesis this round could not test.
2. **Excel opening the `M1:M3` rule**, to learn whether an off-grid shift shows `#REF!` and whether the cell is
   painted or left alone.
3. **A workbook whose rule uses `A$1` against a tall range**, opened in Excel, confirming the row-locked reading
   that this round implements for `E1`/`E5`.
