# R101 — Closing the `cfRule` kind set, and what a spreadsheet cannot tell you

R96 modelled five rule kinds and said so. R100 added seven. R101 adds the last six —
`containsBlanks`, `notContainsBlanks`, `containsErrors`, `notContainsErrors`, `timePeriod` and `iconSet` — which is
**every kind the base schema defines, 18 of 18**. Two product files changed (`XlsxConditionalFormatting.kt`,
`XlsxConditionalFormatVerdict.kt`); everything else here is evidence about those two files.

The most useful thing this round produced is not a feature. It is a measured **incompatibility**: openpyxl 3.1.5
cannot read a workbook containing seven schema-legal `timePeriod` values. That finding shapes the artifact split
below, and it is the reason the round's honesty section is longer than its feature section.

## A. What was added

**Model (`XlsxConditionalFormatting`).**

| Addition | Shape | Refused when |
|---|---|---|
| `Kind.CONTAINS_BLANKS` / `NOT_CONTAINS_BLANKS` / `CONTAINS_ERRORS` / `NOT_CONTAINS_ERRORS` | attributes only, self-closing; optional dxf | a formula, cfvo, rank, percent or bottom flag |
| `Kind.TIME_PERIOD` + `TimePeriod` (17 presets) | `type="timePeriod" timePeriod="today"`; optional dxf | the preset missing; a formula, rank, cfvo or rank flag |
| `Kind.ICON_SET` + `IconSet` (17 sets) | `<iconSet iconSet="3TrafficLights1"><cfvo/><cfvo/></iconSet>`, `showValue`/`percent`/`reverse` written only when non-default; optional dxf | the set missing; a boundary count other than `icons - 1`; a `min`/`max` boundary; colours; a rank or bottom flag |
| `Rule.timePeriod`, `.iconSet`, `.showValue`, `.reverse` | each legal on exactly one kind | any other kind carrying it |
| `MAX_TEXT` unchanged; no new numeric bound | `iconCount` derives the boundary count from the set name | — |

Two decisions worth naming:

* **`percent` is one field with two homes.** `CT_CfRule/@percent` (top10, default false) and `CT_IconSet/@percent`
  (iconSet, default true) are the same attribute name on different elements; the writer puts it on whichever
  element the kind owns, and the reader takes the child's value when both are present.
* **The icon set's name is always written.** It is the rule's subject, not a modifier, so omitting it would
  describe an anonymous icon set. `showValue`, `percent` and `reverse` follow the usual "write no default" rule.

**Reader normalization.** Excel writes one `<cfvo>` per icon with the first pinned at the range minimum; this model
stores only the boundaries that decide something (`iconCount - 1`) and drops a leading boundary **only** when it is
`min`, or `percent`/`percentile` at `0` — the three cases where it is provably the range minimum. A leading
boundary that is not the minimum (an absolute `num` of 2 on a 1..9 range, say) would move every icon by one if
dropped, so that rule is **refused**, not normalized. This is the only place where the reader deliberately declines
to read a well-formed file, and §D gives the test that pins it.

**Evaluator (`XlsxConditionalFormatVerdict`).** Four blank/error predicates, `timePeriod` against a caller-supplied
`Calendar`, and `iconSet` indices. Two of these carry real semantics rather than arithmetic:

* **`containsBlanks` follows `LEN(TRIM(cell))=0`**, so a cell holding only spaces is blank and a cell holding only
  a tab is not. **`notContainsBlanks` is not its complement on an error cell**: both formulas take the cell as an
  argument, both error, and an erroring conditional format applies nothing. So an error cell fires *neither*, and
  neither is left undecided.
* **`timePeriod` needs a calendar.** Without one the rule is `Undecided`, never "did not fire". Weeks follow an
  explicit `weekStartsOn` because no attribute in the file states the first day of week; the same cell is in
  "this week" under a Sunday-first week and in "last week" under a Monday-first one, and the probe asserts both.

## B. Formal model

**Admissible objects.** A rule is `(kind, priority, operator?, text?, rank?, percent?, bottom?, aboveAverage?,
equalAverage?, stdDev?, timePeriod?, iconSet?, showValue?, reverse?, cfvo[], colours[], format?)` with a
disjointness predicate: each optional field belongs to exactly one kind (or to the two kinds that spell the same
XML attribute). Construction is the single gate: an illegal combination cannot reach the writer, and 18 such
combinations are asserted to throw with a message.

**Information model.** The evaluator sees a sparse `Grid` of four value shapes plus blank; it never sees formulas,
styles or the workbook's date system. `Calendar` is therefore *supplied*, not inferred: `today` is a whole-day
serial in the workbook's own system, `date1904` selects the system, `weekStartsOn` names the first day of week.

**Resource counted.** One verdict costs `O(rules × range cells)` for range-relative kinds (`top10`, `aboveAverage`,
duplicates, scales, bars, icon sets), bounded by `MAX_RANGE_CELLS = 100_000` per reference; blank/error/text/time
kinds are `O(1)` per cell.

**Success and failure, with quantifiers.** Success:
`∀ blocks, grid, cell: verdict is Decided ⇒ every fired rule's predicate holds under §C's hypotheses, and every
rule that could not be decided appears in `unresolved`.` Failure is the negation, plus the weaker but explicit
`∄ silent guess`: a rule whose predicate depends on an unknown (an error value for a text rule, a missing calendar
for a time rule) must appear as `Unsupported` with a reason.

**Regime, units, tolerances.** Priorities `1..1_000_000`; `stdDev` `0..100`; text `1..255` characters; icon sets
3/4/5 icons with 2/3/4 boundaries; period comparisons are exact day comparisons on whole-day serials (a fractional
serial is floored); no tolerance is used anywhere except the colour interpolation inherited from R97.

**Adjacent problems that must not be substituted.** (i) *Rendering an icon* — this model reports an index, never
pixels. (ii) *Excel's UI behaviour* — ten of the seventeen presets exist in the UI; the other seven are schema-only
here. (iii) *Calendar correctness for the 1900 phantom* — serial 60 names no real day. (iv) *The `x14` extensions*
— 2010 data bars and five-icon sets outside the base enumeration are out of scope and are dropped on read.

## C. Frontier

| Question | Best available answer | Status here |
|---|---|---|
| How many `cfRule` kinds does the base schema define? | 18 (`ST_CfType`) | all 18 modelled and written |
| How many `timePeriod` values does the schema allow? | 17 (`ST_TimePeriod`) | all 17 modelled, written, round-tripped |
| How many does Excel's UI offer? | 10 | all 10 covered by the verdict corpus |
| How many does openpyxl 3.1.5 parse? | **10** — measured, not assumed | asserted as a limitation (§H) |
| `<cfvo>` count on an icon set | 2..4 per `CT_IconSet`; Excel writes one per icon | model stores `icons-1`; reader normalizes Excel's form |
| Week boundaries | no attribute in the file states the first day of week | explicit `weekStartsOn`, both cases tested |

The open interface this exposes: **a workbook can be schema-valid and still unreadable by a mainstream library.**
The artifact split is the containment: `r101.xlsx` holds only `timePeriod="today"` so every openpyxl lane can read
it, and `r101-presets.xlsx` holds all seventeen so the raw lane can prove what the model writes — with the library
refusal asserted rather than avoided.

## D. Negative space — 18 refused shapes and 4 dropped ones

Refused at construction, each with a message and each asserted: a `timePeriod` rule without a preset; with a
formula; with a rank; a `timePeriod` field on a `cellIs` rule; on a `duplicateValues` rule; an `iconSet` rule
without a set; a three-icon set with one boundary; a four-icon set with two; a `min` boundary; a `max` boundary;
colours on an icon set; a rank on an icon set; a bottom flag on an icon set; icon-set flags on a `timePeriod` rule;
`showValue` on a `duplicateValues` rule; `reverse` on a blank rule; a rank on a blank rule; cfvo on an error rule.

Dropped on read, each asserted by the foreign-package lane: an `x14` set name (`5Boxes`); three boundaries for
three icons whose first is not the range minimum; an unknown `timePeriod` token (`fortnight`); a rank on
`containsBlanks`. The modal shortcut this round is **"drop the leading `<cfvo>` unconditionally"**: it makes every
Excel icon set readable and silently shifts every icon by one for the files where the leading boundary is not the
minimum. The test that detects it is `r101.xlsx` rule 4 of the foreign package: three boundaries `2,5,8` for
`3Arrows` must be *refused*, and the probe's `foreign.size == 5` fails if they are accepted.

## E. Mechanism portfolio

| # | Mechanism | Artifact | Proved portion | Unresolved interface | Cheapest falsification |
|---|---|---|---|---|---|
| 1 | Attribute-level raw OPC walk | `kinds.tsv` × `sheet1.xml` | every modelled attribute and its *absence* | nothing about Excel | `test_a_changed_time_period_in_the_table_is_caught` |
| 2 | Public `XlsxStyledBook.read` round trip | 20 rules, 17 presets | field-for-field identity | a shared bug in writer+reader | drop the new field from `description` |
| 3 | openpyxl 3.1.5 read | same 20 rules | independent parser agrees | openpyxl's own gaps | `test_a_changed_boundary_value_is_caught` |
| 4 | openpyxl refusal probe | `r101-presets.xlsx` | a library limitation, measured | whether Excel accepts those 7 | remove one preset from the file |
| 5 | Python re-derivation | 51 verdicts | the decision procedure | whether Excel agrees | `test_a_percent_boundary_resolved_as_a_percentile_is_caught` |
| 6 | `datetime` as date oracle | serial→date conversion | serial arithmetic, independently | Excel's phantom day 60 | `test_a_date_shifted_by_one_day_is_caught` |
| 7 | Model-level refusals | 18 assertions | the admissibility predicate | — | widen a `require` |

All seven ran. They are independent in the sense that each would survive the others' failure: 1 and 3 read the same
bytes through different code, 5 re-implements the evaluator rather than calling it, and 6 replaces the project's
date arithmetic with the standard library's.

## F. Artifact requirement, and the four honest holes

`tools/run_r101_checks.py` writes `r101.xlsx` (20 rules: one per kind plus three icon-set shapes), `r101-presets.xlsx`
(17 presets), `r101-foreign.xlsx`, `kinds.tsv`, `rules.tsv`, `values.tsv`, `calendar.tsv`, `verdicts.tsv`,
`summary.txt`. Current output:

```
R101_CF_OK assertions=125 refusals=18 kinds=20 presets=17 verdicts=51 foreign=5
R101 check: 20 raw rule(s), 17 preset(s) in enum order, 20 through openpyxl, 51 verdict(s) recomputed
```

The holes, stated rather than buried:

1. **No application ran.** No Excel, LibreOffice, renderer or device exists on this host. Nothing here says a cell
   was painted, an icon drawn, or a date highlighted.
2. **The phantom day.** Serial 60 (Excel's 1900-02-29) has no civil date. The Python oracle returns "not a date"
   for it; this model's serial arithmetic assigns it Thursday, consistent with Excel's own `WEEKDAY` fiction
   (serial 59 and 60 cannot both be real). No corpus here exercises 1900 dates, so this is **stated and untested**.
3. **The quarter presets** have no Excel user-interface counterpart to compare against; their "quarter of the
   reference year" reading mirrors `thisMonth` and is a hypothesis, not an observation.
4. **`showValue` does not affect the verdict.** It decides whether the number is printed beside the icon. A test
   that could falsify my claim that it does not move the index would need an application.

## G. Second-order adversary — seven passes

1. **Write the defaulted flags.** Emit `showValue="1" percent="1" reverse="0"` on every icon set → the raw lane
   looks for absence on rule 18 and openpyxl reports `None`/`True` accordingly.
2. **Drop the icon-set name when it is the default.** A reader then cannot tell 3TrafficLights1 from an anonymous
   set; the raw lane requires the attribute on every icon set.
3. **Accept every `<cfvo>` count.** The four-icon rule with three boundaries and the three-icon rule with two would
   both pass; the lane counts `icons - 1` and the refusals pin the rest.
4. **Normalize unconditionally.** See §D: the foreign `2,5,8` rule must be refused, and the probe counts five
   surviving rules, not six.
5. **Invent today.** Use the host clock: then the same file yields different verdicts on different days and no
   reproducible artifact exists. `Calendar` makes the reference date an input; the lane asserts `UNDECIDED` when it
   is missing.
6. **Treat the negative predicates as complements.** On an error cell that flips both answers; the probe asserts
   `A5` fires only `containsErrors`, and the control that renames `containsErrors` to `containsBlanks` fails.
7. **Assume openpyxl reads everything.** It does not, and the lane asserts the exact `ValueError` rather than
   skipping the artifact — a silent skip would hide a real interoperability fact.

## H. Verification — what actually ran

| Lane | Command | Result |
|---|---|---|
| R101 probe + 4 lanes | `python3 tools/run_r101_checks.py` | `R101_CF_OK assertions=125 refusals=18 kinds=20 presets=17 verdicts=51 foreign=5`, then `20 raw, 17 presets, 20 openpyxl, 51 verdicts` |
| R101 controls | `PYTHONPATH=tools python3 -m unittest tools.test_r101_kinds` | `Ran 22 tests … OK` |
| R100 (unchanged surface) | `tools/run_r100_checks.py` | `assertions=132 refusals=18 verdicts=31`, identical to R100 |
| R96 | `tools/run_r96_checks.py` | 8 rules / 6 blocks / 5 dxf references |
| R97 | `tools/run_r97_checks.py` | 33 verdicts, 46 hits, 7 unresolved |
| R94, R95, R98, R99 | their runners | green, counts unchanged |
| R47 kernels | `python3 tools/run_r47_contracts.py` | `2393 assertions across 61 kernels` |
| whole Python suite | `python3 -m unittest <all tools/test_r*.py>` | `Ran 2240 tests … OK (skipped=269)` |
| census | `python3 tools/module_testability.py --check` | `50/50 (100.00%)`, `not_established: 0` |

Per-feature testability, absolute and percentage, against the repository's own census standard (50 modules):
substantial testable subset **50/50 = 100.00%**; specification-accepted modules **0/50 = 0.00%**. For this feature
specifically: `cfRule` kinds with an executable witness **18/18 = 100%** (was 12/18 = 66.7% at R100, 5/18 = 27.8% at
R96); `timePeriod` presets with a written-and-round-tripped witness **17/17 = 100%**, of which **10/17 = 58.8%**
can also be read by an independent library; icon-set sizes **3/3 = 100%**.

**Tools that actually exist here and were used:** pinned kotlinc 2.2.21 + OpenJDK 17 (host JVM), openpyxl 3.1.5,
python-docx 1.2.0, CPython 3.11 `unittest`/`datetime`/`math`, npm-installed node modules for the R87 lanes.
**Not available, therefore not claimed:** Excel, LibreOffice, ExcelJS, docx-preview, any browser, any emulator, any
Android device, any theorem prover, any SMT solver, any third-party spreadsheet renderer.

## I. Regressions versus the best prior codebase (R100, `1216966`)

Re-running the prior lanes found no behavioural regression: R96, R97, R100 produce identical counts, and the R100
probe needed **one fixture edit, not a product edit** (below). Three files outside the two product files changed,
each for a stated reason:

1. **`R100Checks.kt` (test probe).** Its foreign package used `type="containsBlanks"` as the example of an
   *unmodelled* kind. R101 models it, so the rule now survives and the count assertion failed — a true positive,
   not a defect. The example moved to `x14:iconSet`, which remains outside the base schema. R100's counts after the
   edit are unchanged (132 assertions, 31 verdicts).
2. **`tools/run_r47_contracts.py` (build wiring).** The verdict layer now references `SerialDate`, so the
   domain-isolated `r47_office` jar must carry that file; without it the isolated compile fails. This is the same
   class of change R91 recorded when `XlsxStyledBook` stopped being a lone file.
3. **`build.gradle.kts`, the office workflow, the census** — wiring and evidence registration for the new pass.

Product behaviour that changed and is worth naming: `iconSet` now decides "not a number ⇒ no icon" **before**
resolving the range's boundaries, so an all-blank range yields `Decided(no fire)` instead of `Undecided`. That is a
strict improvement (a blank cell's answer never depended on the boundaries) and it is asserted.

Nothing was removed. The fifteen previously modelled kinds, the R97 verdict grammar, the priority invariant, the
dxf interning, the `MAX_*` bounds and every refusal message are untouched.

## J. Class A, and the next test

**Class A applications: still 0.** The census's 100% measures executable testability, not acceptance. What R101
changes is narrower and real: the conditional-format *file model* is now complete over the base schema, so the gap
is no longer "kinds we cannot write" but "whether Excel agrees with what we wrote".

The next test that could fail, in order of information per minute:

1. **LibreOffice headless render** of `r101.xlsx` and `r101-presets.xlsx` to PDF, comparing which cells carry fills
   against `verdicts.tsv`. This would falsify or confirm the blank-cell reading, the icon indices and the preset
   semantics in one run. It needs `soffice`, which this host does not have.
2. **Excel opening `r101-presets.xlsx`** — the only way to learn whether the seven non-UI presets are honoured or
   silently ignored, which no open-source reader here can answer.
3. **A 1900-system corpus around serial 59–61**, to decide whether the phantom-day weekday claim in §F.2 is right.
