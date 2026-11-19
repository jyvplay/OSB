# R107 — Plural criteria: `COUNTIFS`, `SUMIFS`, `AVERAGEIFS`

R106 could ask one question of a range. The conditional formats a real workbook actually writes ask two or three at
once:

```
COUNTIFS(A1:A9,">5",B1:B9,"draft")          SUMIFS(C1:C9,A1:A9,">5",B1:B9,"draft")          AVERAGEIFS(C1:C9,A1:A9,">5")
```

Every one of those was still refused by name, because `COUNTIFS`, `SUMIFS` and `AVERAGEIFS` were not functions in the
grammar. R107 adds them: one or more range/criterion pairs, all of which must hold **at the same position**, with the
range to accumulate first for the last two.

One product file changed (`XlsxConditionalFormatVerdict.kt`, **+106/−1**). Everything else here is evidence about it.

## 0. Runtime honesty

**Actually available and used this round:** a pinned host toolchain installed by `python3 tools/bootstrap_host_tools.py
install` — kotlinc **2.2.21** on **OpenJDK 17.0.9** (both fetched through npm/PyPI, transport-trusted only) — plus
CPython **3.11** with `unittest`, `zipfile`, `xml.etree`, `re`, `hashlib`, and **openpyxl 3.1.5** installed into
`$HOME/.cache/fusion-r76-readers` by pip. **Not available, therefore never claimed:** a Gradle distribution, the
Android SDK, an emulator, a device, Excel, LibreOffice, any spreadsheet renderer, any browser, any independent agent,
any theorem prover, any network service. No execution in this round was parallel, and no search over theorem space
was performed. Statements about Excel's behaviour are documentation-derived and labelled as such.

## A. Formal model

**Admissible objects.** The R106 grammar plus three names and one alignment rule:

```
COUNTIFS   ( range , criterion , range , criterion , … )                 2..8 arguments, every argument a pair
SUMIFS     ( range , range , criterion [, range , criterion , … ] )      3..8 arguments, accumulated range first
AVERAGEIFS ( range , range , criterion [, range , criterion , … ] )      3..8 arguments, accumulated range first
```

`criterion` is R106's grammar unchanged: a number, a boolean, or text beginning with one of `<= >= <> = < >`, whose
rest is a number when written as one and otherwise a case-insensitive wildcard pattern with `~` escapes; an empty
rest means empty text (`""` = blanks, `"<>"` = non-blanks); 255 characters maximum.

**Information model.** Unchanged: the rule's `sqref`, the formula text, and a sparse grid of the five values this
model knows — blank, number, text, boolean, error. No used-range, no date type, no locale, no collator, no formula
text of other cells.

**Resource counted.** Parsing stays `O(tokens)`; each criterion adds `O(characters)` bounded at 255. Evaluation is
`O(positions × pairs)` grid reads, where `positions ≤ MAX_RANGE_CELLS = 100000` and `pairs ≤ 4`. Ranges are described
by `rangeBounds()` and read through `cellAt()`, so no range is materialized: the plural path allocates one bounds
array and one criterion per pair, nothing per cell.

**Success, with explicit quantifier order.** For every block, every token of its `sqref`, every cell of that token,
every `expression` rule in it: `Yes` if the formula evaluates to a true boolean or a non-zero number, `No` if
false/zero/blank, `Unsupported(reason)` otherwise. Inside a plural call:
`∃ position ∈ range₁ : ∀ pair p ∈ 1..n : criterion_p holds at position` — the count is over *positions*, and the
conjunction is *per position*, never over positions and criteria independently (that would be the "set semantics"
shortcut §D.3).

**Parameter regime, units, tolerances.** Grid `1..1048576 × 1..16384`; area bound 100000 cells per range; criterion
bound 255 characters; pairs bounded by the grammar's existing `MAX_CALL_ARGS = 8` (four pairs for `COUNTIFS`, three
for `SUMIFS`/`AVERAGEIFS`; Excel allows 127). Number equality is exact double equality; text equality folds ASCII
case only; there are no rounding tolerances in the model.

**Adjacent problems that must not be substituted.** (i) *Cell-formula evaluation* (`FormulaEval`) — a different
caller with different range semantics, untouched. (ii) *Painting* — a verdict is not a rendered cell. (iii)
*Collation* — ordering two different texts stays refused. (iv) *Whole-column ranges* — still outside the grammar,
because resolving `A:A` needs a used-range the model does not carry. (v) *Cross-sheet references* — still out.
(vi) *Locale and date criteria* — still out.

## B. Outcome space

Three outcomes per rule, unchanged. Measured over this round's **60** cases: **41 decided, 19 refused** (19/60 =
31.7%).

- **H+ (achievable):** every conjunction of bounded rectangular ranges with R106's criterion grammar is decidable
  from cached values; the alignment is a property of positions, not of values. Executed: 41 decided cases.
- **H− (impossible under these constraints):** any conjunction whose meaning depends on something the grid does not
  carry — an ordering of two different texts, a whole-column bound, a used-range, a locale — cannot be decided here.
  Refused, with the reason naming the range, the pair, or the code.
- **H∂ (boundary):** four boundaries where an answer becomes a refusal, each pinned from both sides: the argument
  count at 8 (the parser's ceiling) and the parity rule (an unpaired argument), the 255-character criterion bound
  (R106, re-exercised here), the 100000-cell area bound (shared with R105), and the shape equality of every range
  against the first.

## C. Frontier — what this closed, and what was verified

Imported result, restated with its hypotheses: Excel's plural criteria functions take **ranges of identical size**,
match **cell by cell at the same index**, and *every* pair must be true; the accumulated range in `SUMIFS`/
`AVERAGEIFS` is the first argument and need not be adjacent to the criteria ranges; `SUMIFS` adds numbers only;
`AVERAGEIFS` with no matching number is `#DIV/0!`. The hypothesis is "alignment is positional, and the conjunction is
inside the position loop".

That is exactly what R107 implements and measures: the alignment is pinned by `cell_at`/`cellAt` walking each range's
own bounds (mutation 2 replaces it with the first range and is caught), and the conjunction is pinned by pairs whose
per-position truth values differ (`C>2` ∩ `K="high"` = 2, while the value-set shortcut would give 6).

**Coverage now.** Functions the grammar reads: **29** (16 + 7 aggregates + 3 singular criteria + 3 plural criteria).
Formula cases across four rounds: **295** (100 + 71 + 64 + 60). Refusal shapes pinned: **80** (27 + 21 + 13 + 19).
Written conditional rules round-tripped: **50** (14 + 12 + 12 + 12).

## D. Negative space — twenty shapes that look like solutions but do not satisfy the contract

| # | Shape | Why it fails | The test that detects it |
|---|---|---|---|
| 1 | Treat the pairs as independent counts and multiply | `C>2` (3 cells) × `K="high"` (3 cells) = 9 | `two pairs on two ranges` = 2; mutation 1 (disjunction) |
| 2 | Conjoin over the *union* of the ranges instead of per position | a cell that satisfies pair 1 anywhere and pair 2 elsewhere counts | same case; `a low band` = 1 |
| 3 | Set semantics: match values rather than positions | `K`'s "high" is a property of rows, not of values | same case |
| 4 | Align every pair by the first range's coordinates | `K1:K5` read where `C1:C5` is | mutation 2 (`two pairs on two ranges` gives 0) |
| 5 | Align by absolute cell address instead of by index | two ranges that start in different columns stop aligning | `a range position is free but its shape is not` |
| 6 | Compare widths only, not heights | `C1:C5` against `K1:K4` accepted (both width 1) | `a shape mismatch in the second pair`; mutation 3 |
| 7 | Require the ranges to *start* at the same cell | `SUMIFS(B1:B5,C1:C5,…)` refused (a practical formula) | `sumifs with one pair` = 120 |
| 8 | Accept an odd argument count and drop the last criterion | `COUNTIFS(x,">2",K)` counted as one pair | `an odd argument count`; mutation 7 |
| 9 | Require an even argument count for `SUMIFS` too | 3 arguments refused | `sumifs with one pair` |
| 10 | Read the criterion ranges lazily and skip later pairs after a failure | an error in pair 2 hidden when pair 1 failed | `an error is found even when an earlier pair never matches`; mutation 4 |
| 11 | Treat an error under *any* criterion as "no match" | silently paints over `#N/A` | `an error under a second criterion` refuses |
| 12 | Read the accumulated range for every position | the `#N/A` at `H1` leaks into a sum whose criterion never matched that row | `an error in the sum range on an unmatched row is not read` = 0 |
| 13 | Skip the accumulated range's shape check | reads the wrong cells or throws | `a sum range of the wrong shape` |
| 14 | Name the wrong reference in the mismatch message | undiagnosable | the message asserts `the sum range is 1x4` / `the average range is 1x4` / `the first criteria range is 1x5` |
| 15 | Count a matched boolean as 1 in `SUMIFS` | `SUMIFS(A1:A8,A1:A8,"<>")` = 3 instead of 2 | `a matched text and boolean add nothing to a sum`; mutation 5 |
| 16 | Count a matched text as 0 but still divide by it in `AVERAGEIFS` | averages over 7 positions instead of 5 numbers | `an average over text and booleans` = 0.4 |
| 17 | Answer 0 for an `AVERAGEIFS` with no matching number | silently paints | `averageifs with no matches` refuses `#DIV/0!`; mutation 6 |
| 18 | Let `COUNTIFS` accept a non-range pair (a scalar to match position 0) | reads the wrong cells | `a first pair that is not a range`, `a second pair that is not a range` |
| 19 | Use the first range's anchor for every pair's shift | `$A$1:$A$4` locked and `C1:C4` free stop being independent | `a locked and a free range together` = 3 |
| 20 | Fold the plural names into the singular dispatcher by prefix | `COUNTIFSS(...)` decides as `COUNTIF` | `a plural name is not a singular one` refuses |

## E. Mechanism portfolio — six mechanism-distinct approaches

| # | Mechanism | Central construction | Concrete artifact | Proved portion | Exact unresolved interface | Cheapest falsification | Gap local? |
|---|---|---|---|---|---|---|---|
| 1 | Hand-written expectation table, written before the evaluator ran | `formula -> answer at a cell` | `R107Checks.kt` `CASES` (60 rows) | outcomes per formula/cell | none for the table itself | change one expected value | local |
| 2 | Independent Python tokenizer/parser/evaluator | own recursive-descent parser, own criterion parser, own **DP wildcard matcher**, own pair walk | `tools/check_r107_plural_criteria.py` | 60/60 cases agree with Kotlin **and** the table, reasons included | shares no code with the product | edit one branch of the Python pair walk | local |
| 3 | Controls on that reference implementation | direct calls to `decide`, `cell_at` | `tools/test_r107_plural_criteria.py` (24 tests) | the lane is not self-agreeing | none | break the position alignment in Python | local |
| 4 | Source mutation | 7 mutants, §H | each defect class is detectable | mutants are author-chosen | add an 8th mutant | local |
| 5 | File round-trip | raw `<formula>` XML + openpyxl 3.1.5 | `r107.xlsx`, 12 rules | plural formula text survives verbatim, quotes and `~` included | openpyxl does not evaluate formulas | corrupt a rule's text | local |
| 6 | Cross-round differential | R106's 64 singular cases re-derived unchanged by the same product | `R106_CRITERIA_OK …` | adding the plural path changed no singular answer | the singular path could still be wrong the same way in both rounds | break the shared `criterion()` parser | equivalent to the original problem (shared code) |

Mechanisms 1 and 2 are independent because they were written from the specification, not from each other; mechanism
6 is *not* independent of the singular path by construction and is listed with that weakness stated rather than
hidden.

## F. Artifact requirement, and the five honest holes

Artifacts (regenerated by `python3 tools/run_r107_checks.py`, not checked in):
`testing/build/r107/artifacts/{r107.xlsx, cases.tsv, rules.tsv, values.tsv, summary.txt}`.

1. **No spreadsheet application ran.** The "every pair must hold", the shape requirement, the `#DIV/0!` and the
   numbers-only accumulation are Excel's *documented* behaviour; none was observed here.
2. **No collator.** Ordering two different texts inside any pair is refused, not locale-guessed (carried from R106).
3. **The pair ceiling is the grammar's, not Excel's.** `MAX_CALL_ARGS = 8` allows four pairs for `COUNTIFS` and three
   for `SUMIFS`/`AVERAGEIFS`; Excel allows 127. Raising the ceiling would change every function's arity bound, so it
   was not done as part of this task.
4. **A shape mismatch is a refusal, not `#VALUE!`.** Excel returns an error value, which the model would then have to
   propagate; this reader refuses with a reason that names the range. Both end in "no format painted", but the
   mechanism differs and is stated.
5. **Whole-column and multi-area ranges are still out**, so `COUNTIFS(A:A,">5")` — a legal Excel formula — is still
   refused, and the refusal says "neither a function, a literal nor a cell reference" for the bare `A:A`.

## G. Second-order adversary — eight passes

1. **Set semantics vs positional semantics.** Every case that would pass under "match values, count combinations" is
   present, plus `two pairs on one range` where the same range appears twice, plus a case where the two ranges'
   satisfying sets differ in size (3 and 3, intersection 2).
2. **Alignment under shift.** Three shift cases: both ranges free (alignment preserved), one locked (alignment
   broken deliberately, answer changes 3 vs 2), and the anchor case. A wrong per-range anchor cannot pass all three.
3. **Error determinism.** A position is read by *all* pairs before the conjunction is decided, so the mutation that
   restores short-circuiting is caught by a case where the first criterion never matches.
4. **Empty decision cases.** Contradictory pairs (`""` ∧ `"<>"`), a pair that matches nothing, and `AVERAGEIFS` with
   no matching number.
5. **Boundaries.** Argument count 3 (unpaired) and 9 (parser ceiling); criterion 255/256 (R106 lane); range shape 1x4
   against 1x5; a single-cell range against a five-row one.
6. **Composition.** A plural call inside `AND`, in arithmetic, as a number inside `SUM`, and as a *range* where a
   range is required (refused).
7. **Cross-round compatibility.** The whole R106 table (64 cases, 39 controls) re-derives unchanged; the shared
   `criterion()` parser is exercised by both rounds.
8. **The lane's own integrity.** Two of my hand-written expectations were arithmetically wrong on first run (a
   four-pair conjunction where I counted two rows instead of one, and a shift case where I forgot that `2 > 1`). In
   both, the product was right and the *table* was repaired; the repaired table was then re-derived by an
   independently written Python walk, which agreed on all 60.

## H. Verification — what actually ran

| Lane | Command | Result |
|---|---|---|
| R107 probe + checker | `python3 tools/run_r107_checks.py` | `R107_PLURAL_OK assertions=134 cases=60 decided=41 refused=19 criteria=3 rules=12`; then `60 case(s) re-evaluated in Python, 12 rule(s) raw, 12 formula(s) through openpyxl` |
| R107 controls | `python3 -m unittest test_r107_plural_criteria` | `Ran 24 tests … OK` |
| R106 (re-run after this round) | `python3 tools/run_r106_checks.py` | `R106_CRITERIA_OK assertions=132 cases=64 decided=51 refused=13 criteria=3 rules=12`; `Ran 39 tests … OK` |
| R92–R105 | their runners | all green, **every count identical to R106** (38, 72, 98, 72, 60, 70, 91, 68, 132, 125, 113, 220, 147) |
| R47 kernels | `python3 tools/run_r47_contracts.py` | `R47 PASS: 2393 production-kernel assertions across 61 new kernels` |
| whole Python suite | `PYTHONPATH=… python3 -m unittest discover -s tools -p 'test_*.py'` | `Ran 2550 tests … OK (skipped=268)` (2526 at R106, +24 controls) |
| census | `python3 tools/module_testability.py --check` | `50/50 (100.00%)`, `not_established: 0` |
| structural + Studio gate | `python3 tools/check_project.py`, `tools/native_source_audit.py --check`, `tools/check_r104_studio.py` | green (AGP 9.1.1, Gradle 9.3.1, JDK 17, compileSdk 37) |

**Source mutations executed** (each recompiled with `-Werror`, run, and reverted to a byte-identical file; the
sha256 of the product file was asserted equal after every revert):

| Mutation | Detected by | Message |
|---|---|---|
| the conjunction becomes a disjunction | the two-pair lane | `two pairs on one range: 'COUNTIFS(C1:C5,">1",C1:C5,"<4")=2' at B1 gave FALSE, expected TRUE` |
| every pair reads the first range | the two-range lane | `two pairs on two ranges: 'COUNTIFS(C1:C5,">2",K1:K5,"high")=2' … gave FALSE` |
| the shape check compares widths only | the shape lane | `a shape mismatch in the second pair: … gave FALSE, expected UNDECIDED` |
| an error is only seen where an earlier pair held | the error lane | `an error under a second criterion: … gave TRUE, expected UNDECIDED` |
| a matched boolean counts as one | the accumulation lane | `a matched text and boolean add nothing to a sum: 'SUMIFS(A1:A8,A1:A8,"<>")=2' … gave FALSE` |
| an average of no matches answers zero | the `#DIV/0!` lane | `averageifs with no matches: … gave FALSE, expected UNDECIDED` |
| the unpaired-argument check is dropped | the parity lane | `an odd argument count: 'COUNTIFS(C1:C5,">2",K1:K5)=0' … gave FALSE, expected UNDECIDED` |

**Substantial enough code to test — absolute and percentage.** Against the census standard (50 modules): substantial
testable subset **50/50 = 100.00%**; specification-accepted modules **0/50 = 0.00%**. This feature: plural criteria
functions with an executable witness **3/3 = 100%**; plural shapes exercised (one pair, two pairs, three pairs, four
pairs, contradictory pairs, same-range twice, two ranges, a wildcard pair, case folding, blank pair, numeric-text
folding, boolean pair, `<>` pair, ordering pair, cell criterion, 2-D pair, reversed pair, shape mismatch, unpaired,
non-range pair 1 and 2, non-range accumulated range, error under a criterion, hidden error, error in accumulated
range read/unread, arity 1/2/9, shift both/locked/free) **29/29 = 100%**; formula cases **60**, of which
**19/60 = 31.7%** are refusals by design; independent re-derivations **3** (Python lane, 24 controls, 7 mutants);
written rules round-tripped **12/12** through raw XML and openpyxl.

**Tools that exist here and were used:** pinned kotlinc 2.2.21 + OpenJDK 17, openpyxl 3.1.5, CPython 3.11
`unittest`/`zipfile`/`xml.etree`/`re`/`hashlib`. **Not available, therefore not claimed:** Excel, LibreOffice, any
spreadsheet renderer, Android SDK, Gradle distribution, emulator, device, browser. **Visual inspections this round:
0.**

## I. Repair, and the attack on the repair

Two expectations were repaired (§G.8) and one product *behaviour* decision was made deliberately and then attacked:

- **Repair.** `four pairs` expected 2 and the product answered 1; recomputation by hand showed the product right
  (`C>1 ∧ C>2 ∧ C<5 ∧ K="high"` leaves row 3 alone). `two ranges shift together` expected 1 and the product answered
  2; recomputation showed `2 > 1` at the first shifted row, so 2 was right. Both tables were fixed; no product line
  changed for either.
- **New attack aimed at the patch.** The patch reads every pair at every position instead of short-circuiting. The
  cost is `(pairs × positions)` reads where `pairs` reads suffice for a false position; that cost is accepted and
  pinned by mutants 4 (short-circuit) and by the "hidden error" case. A repaired candidate inherits no trust, so
  after each repair the full lane (probe, Python lane, controls, mutants, R92–R106, R47, discover, census, gates) was
  re-run rather than only the failing case.

## J. Regressions versus R106 (`7043b03`)

**Product behaviour that changed — the point of the round.** `COUNTIFS`, `SUMIFS` and `AVERAGEIFS` now decide instead
of being refused as unknown functions. Nothing else changed: the singular path is byte-for-byte the same code, and
its 64 cases plus 39 controls re-derive unchanged.

1. A formula naming one of the three plural functions now decides; the previous outcome was `Undecided` with "not a
   function this reader evaluates".
2. `MAX_CALL_ARGS` is untouched, so no existing function's arity bound moved.
3. `rangeBounds`/`rangeCells` are untouched; the plural path reads them, it does not modify them.

No existing decision changed value. R92–R106 reproduce their counts exactly; R47's 2393 assertions are unchanged; the
Python suite grew from 2526 to 2550 tests (24 new controls) with no test removed.

**Support edits, none of which re-pins another round's evidence:**

1. **`testing/build.gradle.kts` (+15)** — the `r107Plural` source set and the `r107PluralCriteriaContracts` task.
2. **`.github/workflows/android-r54-office.yml` (+3/−2)** — the task, the Python lane and the control module.
3. **`tools/r107_census_deepen.py` (new)** — re-pins only `XlsxConditionalFormatVerdict.kt` and appends R107's
   checks and commands to the `:format:ooxml` row; it refuses to run if any other pinned file has changed.
4. **`docs/module-testability.json` / `MODULE-TESTABILITY.md`** and **`docs/verification/r11-source-inventory.json`**
   — regenerated by their generators; the inventory diff is one hash line.

**Restoration note.** This turn began with the previous round's work present as an uncommitted tree while the local
branch still pointed at `c922f71`. `git write-tree` produced `62c84ec…`, identical to `7043b03^{tree}`, so the
"restore" was provably a no-op: the branch was re-pointed to `7043b03` and the tree became clean with **zero content
changes**. The toolchain (npm-pinned kotlinc, pip `openpyxl`/`python-docx`, `npm ci tools/r87node`) was reinstalled
from the checked-in lock files and the baseline re-verified cold (`R106_CRITERIA_OK …`, 39 controls) before any R107
line was written.

## K. Class A, and the next test

**Class A applications: still 0.** Testability is 50/50; acceptance is 0/50. This round moves what the product can
*decide*, not its acceptance.

Next tests, in order of information per minute:

1. **Open `r107.xlsx` in Excel and LibreOffice** and compare the painted cells with `cases.tsv`. One look settles the
   alignment claim, the numbers-only accumulation and the `#DIV/0!`.
2. **A workbook with a deliberate `#VALUE!` shape mismatch** to learn whether Excel's refusal and this reader's
   refusal differ in anything a user can see (both paint nothing; the question is the reason a user is shown).
3. **Whole-column ranges** (`COUNTIFS(A:A,">5")`): the next refused-by-name shape, and the one that needs a
   used-range concept this model deliberately does not have.
4. **Build the app on a laptop with Rabbit 1** and record the first real sync; the Studio gate still asserts
   configuration only.
