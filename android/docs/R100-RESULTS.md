# R100 — The conditional-format predicates: text, average, duplicates

R100 finishes the second half of the conditional-format feature that R96 documented as *unmodelled*. R96 could
state five rule kinds (*cellIs, expression, top10, colorScale, dataBar*); R97 could decide them; everything else
Excel writes — the four text predicates, `aboveAverage`, `duplicateValues`, `uniqueValues` — was refused and
recorded as a gap. This round models seven more kinds, both halves: the file contract and the evaluator.

Two files changed (`XlsxConditionalFormatting.kt`, `XlsxConditionalFormatVerdict.kt`); nothing else in the product
was touched. Everything else in this document is evidence about those two files.

## A. What was added

**Model (`XlsxConditionalFormatting`).**

| Addition | Shape | Refused when |
|---|---|---|
| `Kind.TEXT_CONTAINS` / `TEXT_NOT_CONTAINS` / `TEXT_BEGINS_WITH` / `TEXT_ENDS_WITH` | `type=`, `text=`, and an `operator` that is **not** the type for the negative form (`notContains`) | text missing, blank, or over 255 characters; a formula or an `operator` supplied by the caller; rank, cfvo or percent flags |
| `Kind.ABOVE_AVERAGE` | `aboveAverage`, `equalAverage`, `stdDev` — each written **only** when it differs from the schema default | a formula, cfvo, rank or percent flag; `stdDev` outside `0..100` |
| `Kind.DUPLICATE` / `Kind.UNIQUE` | attributes only, self-closing | any child element, formula, cfvo, rank or percent flag |
| `text` / `aboveAverage` / `equalAverage` / `stdDev` fields on `Rule` | `text` only on the four text kinds, the average flags only on `aboveAverage` | a kind that cannot carry the field |
| `Kind.selfPainted` | `colorScale`/`dataBar` must not carry a dxf | a dxf on either |
| `MAX_TEXT = 255`, `MAX_STD_DEV = 100` | new bounds | outside |

The dxf rule is deliberately asymmetric and is the one place R100 could have broken a working feature: `cellIs`,
`expression` and `top10` *require* a dxf (unchanged), a scale or bar must not have one (unchanged), and the seven
new kinds accept either — "fires but paints nothing" is a legitimate foreign file, and refusing it would drop the
rule from the model rather than record it.

**Evaluator (`XlsxConditionalFormatVerdict`).** `containsText` / `notContainsText` (Excel's SEARCH grammar:
`?`, `*`, `~` escape), `beginsWith` / `endsWith`, `aboveAverage` (population sigma band, `equalAverage` ties,
`aboveAverage="0"` inverts), `duplicateValues` / `uniqueValues` (value keys, case-insensitive text). A cell with
an error value makes a text or average rule *undecided*, and a duplicate rule *decided-not-fired* — an error is
equal to nothing, not unknown.

**Removed from the KDoc's list of what is not modelled:** the seven kinds and their attributes. **Still not
modelled, still stated:** `iconSet`, `timePeriod`, `containsBlanks`/`notContainsBlanks`,
`containsErrors`/`notContainsErrors`, `<extLst>`, dxf fonts/borders/alignment/number formats, and the painting
itself.

## B. Formal model

A rule is now a tuple `(kind, priority, operator?, text?, rank?, aboveAverage?, equalAverage?, stdDev?, cfvo[],
colours[], format?)`, and the well-formedness predicate is a disjointness constraint: `text` lives only on the
four text kinds, the three average flags only on `aboveAverage`, cfvo/colours only on the two self-painted kinds,
a dxf mandatory on the three legacy visual kinds and forbidden on the two self-painted ones. Every illegal
combination is refused at construction with a message, not silently dropped at write time — the probe asserts
18 such refusals.

The evaluator is a partial function `Rule × Grid × Cell ⇀ Decision`, `Decision ∈ {Yes, No, Unsupported}`; `Yes`
carries `Visual`s, `Unsupported` carries a reason. The sheet-level outcome keeps R97's asymmetry: nothing decided
plus something undecided yields `Undecided`, never "no rule fired".

## C. Frontier, with hypotheses

Every line below is an *import hypothesis* — a reading this implementation commits to because Excel's documented
behaviour is ambiguous or unobservable here. They are listed so that they can be attacked, and four of them are
pinned by assertions in `tools/check_r100_conditional.py`.

1. **SEARCH is unanchored.** `containsText` matches anywhere in the cell's text; `*ap*e*` is the shape this
   implementation reduces it to. An anchored reading would fail the lane on `pineapple`.
2. **A blank cell's text is `""`.** `SEARCH` on a blank errors, so `containsText` is false and
   `notContainsText` is true for a blank — Excel's own written formula `ISERROR(SEARCH(...))` agrees, but no
   application was run.
3. **Numbers coerce to their general-format text.** The number `12.5` satisfies `beginsWith "12"`; `1.0`
   renders as `1`, not `1.0`.
4. **`beginsWith` / `endsWith` are literal prefix/suffix compares**, case-insensitive, not wildcard matches.
   Excel writes them as `LEFT`/`RIGHT` formulae, which are literal.
5. **The average band is over the rule's own references**, numeric cells only (blanks and text are skipped, not
   coerced to zero), sigma is the *population* form (divide by `n`).
6. **Duplicates are counted by value across the whole reference**, text case-insensitively (COUNTIF's rule),
   numbers numerically, and text never collides with a number.
7. **Blanks and errors are never duplicated**; neither rule fires on them.
8. **Attribute absence means the schema default** (`aboveAverage` true, `equalAverage` false, `stdDev` 0), and a
   defaulted rule writes none of the three.

## D. Negative space — 18 shapes that look like a rule

Refused, each with a message, each asserted: text on `cellIs`; text on `duplicateValues`; a text rule with no
text; a text rule with blank text; a text rule over 255 characters; a text rule carrying a formula; carrying an
`operator`; carrying a rank; carrying cfvo; average flags on a `cellIs` rule; `aboveAverage` with a formula; with
cfvo; `stdDev = -1`; `stdDev = 101`; `duplicateValues` with a rank; `uniqueValues` with cfvo; a `cellIs` rule
without a dxf (pre-existing invariant, re-asserted because the new `selfPainted` branch touches the same code); a
repeated priority across blocks. On the read side, a *blank* `text` and an unmodelled `containsBlanks` are dropped
rather than guessed, and an `aboveAverage="maybe"` reads as absent rather than true.

## E. Mechanism portfolio

| Mechanism | Where | What it would catch |
|---|---|---|
| Kotlin probe, 132 assertions | `testing/src/r100/.../R100Checks.kt` | the exact attribute set the writer emits, defaults left unwritten, round trip |
| Raw OPC lane | `tools/check_r100_conditional.py` lane 1 | an attribute that is present when it should be absent (the failure mode R99 hit with `outlinePr`) |
| dxf resolution lane | lane 2 | a rule pointing at the wrong `dxfId`, or a dxf whose fill/bold/italic differs from the declaration |
| openpyxl 3.1.5 | lane 3 | a reader disagreeing about `type`/`operator`/`text`/`aboveAverage`/`equalAverage`/`stdDev` |
| Python re-derivation | lane 4 | a plausible-but-wrong evaluator (anchored wildcard, `n-1` sigma, case-sensitive duplicate key, text/number collision) |
| 20 negative controls | `tools/test_r100_conditional.py` | the lanes themselves going quiet |
| Foreign-package read | `r100-foreign.xlsx` | a real producer's shape: redundant formula, missing `dxfId`, unparsable boolean, blank text |

## F. Artifact requirement, and one honest hole

`tools/run_r100_checks.py` writes `r100.xlsx`, `r100-foreign.xlsx`, `kinds.tsv`, `rules.tsv`, `values.tsv`,
`verdicts.tsv`, `summary.txt` into `testing/build/r100/artifacts`. Current output:

```
R100_CF_OK assertions=132 refusals=18 rules=6 verdicts=31 kinds=14
R100 check: 14 raw kind rule(s), 13 raw verdict rule(s), 14 + 13 through openpyxl, 31 verdict(s) recomputed
```

**The hole.** The negative control for the *population-versus-sample* divisor does **not** exist, because it
cannot: for the values 1..8 the two divisors (2.2913 and 2.4495) both put 7 inside the band, so no verdict in the
corpus distinguishes them. What the control suite measures instead is that *dropping the band entirely* changes
verdicts (which it does). So hypothesis 5 above is **stated, implemented, and unverified** — the honest label is
"chosen, not proven". Fixing it needs a corpus whose sigma test straddles a cell value; that is a one-row change
to `values.tsv` and is the first thing the next round should do.

## G. Second-order adversary — six passes

1. **Ignore the type.** Treat `text` as free-form on every kind → the 18 refusals catch it; a `cellIs` with
   `text` would otherwise round-trip as a rule with an attribute Excel does not read.
2. **Echo what you read.** Keep the `<formula>` beside `containsText` → the round-trip lane compares
   `formulas` (empty), and the foreign lane asserts the formula was dropped, not re-emitted.
3. **Write the defaults.** Emit `aboveAverage="1" equalAverage="0" stdDev="0"` on every average rule → the raw
   lane looks for the *absence* of those attributes on priority 7; openpyxl also reports `None`.
4. **Anchor the wildcard.** `startsWith` instead of `contains` → `A6 "pineapple"` loses priority 1; both the
   probe's expected table and the Python lane fail.
5. **Guess the collation.** Order text with a locale collator → not attempted: this evaluator only decides
   equality and containment, both of which are case-folding questions, and the folding used is Unicode simple
   case mapping (host-independent). Ordering remains refused, as in R97.
6. **Drop the rule instead of refusing it.** Reading a foreign rule that fails validation → `parseRule` returns
   null and the block is skipped; the foreign lane asserts six surviving rules and that the blank-text and
   `containsBlanks` rules are not among them, so a silent widening of the model would be visible as seven.

## H. Verification — what actually ran

Everything below ran on this host, in this order, and all of it passed. Nothing is claimed that was not executed.

| Lane | Command | Result |
|---|---|---|
| R100 probe + 4 lanes | `python3 tools/run_r100_checks.py` | `R100_CF_OK assertions=132 refusals=18 rules=6 verdicts=31 kinds=14`, then `14/13 raw, 14+13 openpyxl, 31 verdicts recomputed` |
| R100 controls | `PYTHONPATH=tools python3 -m unittest tools.test_r100_conditional` | `Ran 20 tests … OK` |
| R96 (unchanged kinds) | `tools/run_r96_checks.py` | 8 rules / 6 blocks / 5 dxf references; openpyxl agreed |
| R97 (unchanged verdicts) | `tools/run_r97_checks.py` | 33 verdicts, 46 hits, 7 unresolved; Python recomputation identical |
| R94, R95, R98, R99 | their `run_rNN_checks.py` | all green, unchanged counts |
| R47 kernel contracts | `python3 tools/run_r47_contracts.py` | `2393 production-kernel assertions across 61 new kernels` |
| whole Python suite | `python3 -m unittest <all tools/test_r*.py>` | `Ran 2218 tests … OK (skipped=269)` |
| census | `python3 tools/module_testability.py --check` | `50/50 (100.00%)`, `not_established: 0` |

**What did not run, and cannot run here:** Excel, LibreOffice, any renderer, any Android device, Gradle, the CI
`office` job. There is no `soffice`, no Excel, no browser and no emulator on this host. So this document contains
**zero** visual inspections and **zero** statements about how a spreadsheet paints a cell — only about the bytes
this writer emits and the decisions this evaluator makes. `mergeable_state: unstable` on the PR is expected: the
CI `office`/`Traceability` jobs fail at the Kotlin compile step on every commit including `main`.

Per-feature testability, absolute and percentage terms, against the repository's own census standard
(`android/docs/module-testability.json`, 50 modules): substantial testable subset **50/50 = 100.00%**;
specification-accepted modules **0/50 = 0.00%**; this round raises the count of *rule kinds* with real tests from
5 to 12 (7 new, `+140%`), and the count of conditional-format *assertions* in the module by 132 probe + 20 control
+ 27 reader-level + 31 verdict rows.

## I. Regressions versus the best prior codebase (R99, `bf63d37`)

Checked by re-running the previous lanes rather than by reading the diff. No behavioural regression was found in
any of them. Three things did change and are worth naming:

1. **`Kind.needsFormat` narrowed and a new `selfPainted` was added.** The old `else` branch refused a dxf on
   anything that was not `cellIs`/`expression`/`top10`; that branch now also covers the new kinds and would have
   made every text rule with a fill unrepresentable. This is the only place where R100 had to *relax* a check,
   and the raw lane asserts the exact boundary (format required / optional / forbidden).
2. **`numbersIn` now delegates to `valuesIn`.** Same rows, same order, same `MAX_RANGE_CELLS` refusal — R97's
   `R97_CF_VERDICT_OK` counts (33 verdicts, 46 hits, 7 unresolved) are unchanged, which is the evidence.
3. **`text` is stripped on read for text kinds.** A file whose text rule carries a formula loses the formula.
   That is information loss, and it is deliberate: the formula is redundant under this model's reading, and
   remembering it would mean claiming a spelling this writer never derived. Stated here so it is not discovered
   later as a surprise.

Nothing was removed. The five R96 kinds, the R97 verdict grammar, the priority invariant, the dxf interning and
the `MAX_*` bounds are untouched and still exercised.

## J. Class A, the PR, and the next test

**Class A applications: still 0.** The census's 100% is a *testability* figure, not an acceptance figure, and the
gap between them is the whole honest story of this repository: every module has an executable subset and none has
a specification-accepted, application-verified implementation. R100 narrows the conditional-format gap in the
sense that matters — the kinds Excel actually writes are now modelled and decided — but a spreadsheet application
still cannot be said to show the right colours.

New PR opened at the end of this turn (R100 does not merge). The next test that could fail: **a LibreOffice
headless conversion of `r100.xlsx` to PDF, comparing the filled cells against `verdicts.tsv`.** Every prediction
in §C — the blank-cell reading, the number-to-text coercion, the duplicate key — would be falsified or confirmed
by that one run, and it is the cheapest available way to convert this round's hypotheses into findings. It needs
`soffice`, which this host does not have.
