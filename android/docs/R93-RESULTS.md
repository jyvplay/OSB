# R93 — AutoFilter: the record, its predicate, and the rows it actually hides

**Scope of this pass.** One worksheet feature, taken as far as a host-only verifier can take it: the
`<autoFilter>` record (`CT_AutoFilter` §18.3.1.2, `CT_FilterColumn` §18.3.2.7, `CT_Filters` §18.3.2.29,
`CT_CustomFilters` §18.3.2.24), the row predicate each column filter describes, and the step that turns that
predicate into the `<row hidden="1">` flags which are the only thing that hides anything.

**Class A applications: still 0.** No Excel, LibreOffice, browser or Android device ran. What follows is
agreement between four readers of the same bytes (this writer's own reader, a raw OPC walker, openpyxl 3.1.5 and
a from-scratch Python re-implementation of the predicate). None of them is Excel.

## A. What was added

| Piece | File | Lines |
|---|---|---|
| `XlsxAutoFilter`: `Operator`, `Custom`, `Column`, `AutoFilter`, `toXml`, `parse`, `matches`, `evaluate` | `format/ooxml/src/main/kotlin/dev/fusion/ooxml/XlsxAutoFilter.kt` (new) | 322 |
| Integration: `StyledSheet.autoFilter`, one emission line, one reader line | `XlsxStyledBook.kt` (**+5 lines, nothing else touched**) | 5 |
| Probe + artifacts | `testing/src/r93/.../R93Checks.kt` | 481 |
| Runner | `tools/run_r93_checks.py` | 78 |
| Raw OPC + openpyxl verifier | `tools/check_r93_sheet_autofilter.py` | 395 |
| Second implementation of the predicate and of the row set | `tools/check_r93_filter_semantics.py` | 326 |
| Negative controls | `tools/test_r93_autofilter.py` | 349 |

The production surface is one new file plus five lines in the existing writer/reader. Nothing else in the tree
was edited: `XlsxProtection.kt`, `XlsxValidation.kt` and `BoundedXml.kt` are byte-identical to R92.

## B. The contract, stated so it can be wrong

**Schema placement.** `CT_Worksheet` is a *sequence*, and in it `sheetProtection` precedes `autoFilter`, which
precedes `dataValidations`. The record is therefore emitted after protection and before validations, never
before `sheetData`. Three independent sources put `autoFilter` after `sheetProtection`: the [unioffice
`CT_Worksheet` struct](https://apidocs.unidoc.io/unioffice/v2.12.0/github.com/unidoc/unioffice/v2/schema/schemas.microsoft.com/office/excel/),
[a Dart writer's explicit ordinal table for §18.3.1.99](https://github.com/almasumdev/excel_plus/blob/main/lib/src/utils/worksheet_order.dart),
and openpyxl's own serializer, which was run here and emitted `sheetProtection` → `autoFilter` → `mergeCells` →
`pageMargins`. My working note from the start of this turn said the opposite; the note was wrong and the code
was corrected against the evidence, not the note.

**Per column.** `colId` is zero-based against the start of `ref`, must be inside the range's width, and never
repeats. Exactly one of `<filters>` / `<customFilters>` is written. Attribute order follows the schema
(`colId`, `hiddenButton`, `showButton`). Defaults are **not** written: `hiddenButton` defaults to false,
`showButton` to true, and `<customFilters and>` defaults to false, so the AND join alone carries `and="1"`.
That last rule was changed during this turn (see G2).

**What the bytes mean.** `<autoFilter>` is a record. It hides nothing by itself. `evaluate(filter, cells)`
computes the row set: the range's header row is never hidden; a data row is hidden when some column's predicate
is *decidedly false*; a row whose only undecided column does not already exclude it is reported as undecided and
left visible. The caller passes that set to the writer's existing `hiddenRows`, which emits `<row hidden="1">`.

**Where the model refuses to answer** (`matches` returns `null`): blanks (Excel's blanks handling is not
modelled), an ordering comparison against text (needs a collation), a numeric cell against a non-numeric operand,
booleans. And where the parser declines to guess: a `filterColumn` carrying `top10`, `dynamicFilter`,
`colorFilter`, `iconFilter`, a `<dateGroupItem>`, or a `<filters>` element with attributes such as `blank` or
`calendarType` is dropped instead of being read as "exactly these values".

Text comparison is case-insensitive with `*`/`?` wildcards, as Excel's is. `~` escaping is **not** modelled, so
a literal `*` cannot be expressed; a 10 000-item filter list is not capped; `<sortState>` (which nests inside
`<autoFilter>`) is not written, because a sort *record* without reordered rows would be a lie about the data.

## C. Frontier, with the hypotheses it imports

Retrieved and restated: `CT_AutoFilter` is `ref` + `filterColumn*` + `sortState?`; `CT_FilterColumn` is `colId`
(required) plus `hiddenButton`/`showButton`; `CT_Filters` is a list of `<filter val>` plus optional `blank` and
`calendarType`; `CT_CustomFilters` is one or two `CT_CustomFilter` (`operator` ∈ {equal, notEqual, greaterThan,
greaterThanOrEqual, lessThan, lessThanOrEqual, default equal}) joined by an `and` attribute defaulting to false.

Imported hypotheses, none discharged here: (i) Excel hides a row exactly when its cell's value fails the filter
under the same rules; (ii) a text comparison in Excel's filter engine is case-insensitive for the languages this
model claims; (iii) `<row hidden="1">` inside a filtered range is what the grid renders as a filtered-out row
rather than a user-hidden one. The open interface is the application, again.

## D. Negative space — 14 shapes that look like a solution and are not

1. Writing `<autoFilter>` and claiming the sheet is filtered — the record hides nothing; the row flags do.
2. Writing the row flags from the filter without asking what the undecided cells mean.
3. Treating a blank cell as "does not match" and hiding rows Excel would leave visible.
4. Comparing text with `greaterThan` lexicographically and calling it a filter.
5. Writing both `<filters>` and `<customFilters>` in one `filterColumn` (Excel reports the sheet as damaged).
6. Writing `and="0"` (legal, but the default; a reader cannot tell it from an absent attribute).
7. Writing `hiddenButton="0"` / `showButton="1"` (defaults; noise that makes the byte lane meaningless).
8. Counting `colId` from one, or from column A instead of the range start.
9. Treating `<filters blank="1">` as the value list and silently dropping the blanks selection.
10. Reading `<dateGroupItem>` as a value.
11. Emitting `autoFilter` before `<sheetData>` (sequence violation; the sheet is reported as damaged).
12. Emitting `autoFilter` at the very end of the worksheet, after `pageMargins`.
13. Claiming round-trip safety without reading the file back and re-writing it.
14. Claiming the filter works because openpyxl can parse the record.

## E. Mechanism portfolio

| # | Mechanism | Artifact | Proved portion | Unresolved interface | Cheapest falsifier | Gap |
|---|---|---|---|---|---|---|
| 1 | Raw OPC walk + full `CT_Worksheet` sequence check | `check_r93_sheet_autofilter.py` | placement, `ref`, per-column `colId`/attribute order/default omission, criterion kind, values and criteria in order, `and` only when true | bytes only | add `hiddenButton="0"` | local |
| 2 | openpyxl `AutoFilter`/`FilterColumn`/`Filters`/`CustomFilters`/`row_dimensions` | same file, second half | 6 sheets, 8 columns, the join flag, both button flags, hidden rows | openpyxl ≠ Excel | flip one value | local |
| 3 | Second implementation of the predicate | `check_r93_filter_semantics.py` | 259 verdicts over 7 rules, recomputed from the criterion | same prose read twice | change one operand | local |
| 4 | Second implementation of the row set | same file, `verify_applied` | 4 hidden + 2 undecided rows re-derived from the typed table | same | change one cell | local |
| 5 | Round-trip + idempotency | `R93Checks.kt` §4, §6b | record equality, row-flag equality, and a byte-identical worksheet part after read→write | self-consistency | drop an attribute | local |
| 6 | Refusal surface | `R93Checks.kt` §5 | 11 illegal column/filter shapes refused with a message (both kinds, neither kind, three criteria, AND on one criterion, empty list, empty operand, negative/duplicate/out-of-range `colId`, lower-case `ref`, row 0) | none | remove one `require` | local |

Branch 3 and 4 exist because openpyxl is one implementation and can share a misreading of the prose with this
one. They are **not** an oracle for Excel's behaviour.

## F. Artifact requirement

`testing/build/r93/artifacts/`: `af-plain.xlsx` (no filter), `af-range.xlsx` (a filter with no columns), `af.xlsx`
(three sheets: four columns of criteria, an OR pair, and a sheet carrying protection + filter + validations),
`af-applied.xlsx` (a real table plus the rows the filter hides), and the three intent tables `autofilters.tsv`
(9 fields per column), `applied_filters.tsv` (6 fields, including the typed table) and `filter_verdicts.tsv`
(7 fields per decision). The probe writes the tables before any verifier runs; every verifier refuses a folder
whose workbook set disagrees with the declared set.

## G. Second-order adversary — what attacked this work, and what it caught

All seven were found by running the code, not by reading it.

**G1. A rule I invented.** The first version of the checker required that `<autoFilter>` not be the last child of
`<worksheet>`. That is not a schema rule; it failed on a legitimate minimal sheet. Replaced by the actual
`CT_Worksheet` sequence check, which then immediately justified itself by catching an out-of-place
`<mergeCells>` control.

**G2. Noise dressed as explicitness.** The writer emitted `and="0"` for an OR pair. Legal, and it round-trips, but
it writes a default, which is exactly what this repository's byte lanes are designed to catch elsewhere
(`hiddenButton`/`showButton`). The writer now emits `and="1"` only for a real AND join, and a control asserts
that re-adding `and="0"` is rejected.

**G3. A silent misread on the way in.** `<filters blank="1">` (show-blanks), `<filters calendarType="…">` and
`<dateGroupItem>` children were being read as an ordinary value list, i.e. a file that shows blanks would have
been read as a file that only shows `["x"]`. The parser now drops such a column instead of reinterpreting it, and
a control injects `blank="1"` to prove the byte lane rejects it too.

**G4. `str.splitlines()` is not `split('\n')`.** The applied-filter intent carries a typed table with `\x1e`
between records; Python's `splitlines()` treats `\x1e` as a line break, so the table arrived as a second "line"
with one field. Found because the verifier failed on its own artifact — and it is a real hazard for any TSV
reader in this tree that uses control characters as separators.

**G5. One bug per implementation, and they disagreed.** `check_r93_filter_semantics.py` keyed `autofilters.tsv`
by `(file, sheet)` in a dict, which collapsed a multi-column sheet to its last column: the recomputation then
produced hidden `{4, 8}` where the Kotlin side said `{3, 4, 6, 8}`. The disagreement is what surfaced it; a
single implementation would have "agreed with itself". Fixed to a list, with the row-set test as the guard.

**G6. The probe's own data bug.** The first applied table stored `"2"` and `"9"` as *text*, so the ordering
criteria were legitimately undecided and only two rows were hidden. The probe's assertion (`hidden == {3,4,6,8}`)
failed, which is the undecided semantics working as designed rather than a bug in the writer; the table is now
typed (numbers as numbers, `"ten"` as text) so the undecided rows are the two that are genuinely undecidable.

**G7. A pre-existing overlap, disclosed rather than merged.** `XlsxAutoFilterEvaluator` (R52) already emits an
`<autoFilter>` string from an untyped criteria map and filters plain-string rows. It is a separate, stringly
API with no connection to `StyledSheet`; the typed model in `XlsxAutoFilter.kt` is now the authoritative one. The
R52 contract test still runs and still passes. Merging them would have touched a component outside this turn's
task, which the working rules forbid; the duplication is recorded here instead of hidden.

## H. Verification — what ran

**H1. Probe and three lanes** (`python3 tools/run_r93_checks.py`):
`R93_OFFICE_OK assertions=72 workbooks=4 filters=3 verdicts=259 hidden=4 undecided=2`; then
*5 filtered sheet(s), 8 filterColumn record(s) verified, 4 row(s) hidden by their filter and 2 left visible as
undecided; openpyxl agreed on 6 sheet(s) and 8 column(s)*; then *259 verdicts over 7 rules, 170 decided and 89
undecided, identical in Kotlin and Python; 1 applied filter recomputed to 4 hidden and 2 undecided rows*.

**H2. Negative controls — 45 tests, all green** (`tools/test_r93_autofilter.py`). They cover: a declared filter
missing, an undeclared filter present, a changed `ref`, a dropped column, swapped and extra values, a removed /
flipped / gratuitous `and`, an explicit default button flag, a dropped declared button flag, an unmodelled
`top10` child, both criterion kinds at once, an unmodelled `<filters blank="1">`, the filter moved before
`<sheetData>`, a `CT_Worksheet` sequence violation, a duplicated and an out-of-range `colId`, dropped intent rows,
a wrong `colId`, a lying button flag, a wrong field count, a hidden row shown, an undecided row hidden, the header
row hidden, a row hidden with no reason, three kinds of applied-intent lie, a missing and a truncated applied
table, plus corpus tampering (flipped verdicts, changed operand, changed join, malformed row, missing file) and
direct unit tests of the glob and number-spelling rules.

**H3. Regression sweep — 37 runner invocations re-executed serially.** 35 exited 0 on the first attempt:
R92, R91, R90, R89, R88, R87 shade, R86 Writer tabs, R86 sheet views, R84 Writer indent, R84 sheet visibility,
R83 Writer spacing, R83 hidden rows, R82 Writer align, R82 sheet align, R81 Writer tables (44 assertions, 9
documents), R81 sheet rows (46, 6 workbooks), R76 Writer layout (74, 9 documents), R76 sheet formats (786
assertions, 245 rows, 6 refused; 239 rows replayed through the independent `ssf` implementation with 231
identical and 8 adjudicated differences), R75 fractions (282), R72 ooxml import-budget comparators, R56
(24 451), R57 (548), R58 (6 428), R59 (1 580 + 400 and 200 DOCX), R60 (675 + 256 DOCX, 6 588 + 64 XLSX), R61
(1 831), R62 (1 434 + 1 288), R63 (1 522 + 2 246), R64 (1 093 + 814 + 613).

Two failed, both for environmental reasons that were then repaired and re-run to exit 0 — recorded rather than
quietly dropped:

* `run_r72_core_zip.py` compiles the **pinned baseline** `aacae239` from `git show`. That commit was **not in this
  clone** (shallow), so its guard skipped every baseline file and the pinned side compiled a probe with no
  sources. `git fetch --depth=200 origin main` restored the commit; the lane then exited 0 with its per-fixture
  comparison unchanged. Nothing in this pass caused the absence, but nothing in this pass would have detected it
  either if the lane had been silently skipped.
* `run_r60_xlsx_differential.py` requires `run_r22_contracts.py` to have produced its build output in the same
  tree; after running R22 first (37 consumer assertions), the differential exited 0.

**H4. Full host discovery.** `python3 -m unittest discover -s tools -p 'test_*.py'` → **`Ran 2342 tests … OK`**,
zero skips (R92: 2297; +45 = exactly the new R93 controls). The R73 lane still prints its one adjudicated
cross-implementation disagreement (`ROUND(2.675, 2)` versus formualizer) and still passes, unchanged by this
pass.

**H5. Census and audit.** `module_testability.py --check` → **50/50 modules (100.00 %) meet the bounded
testability standard; 0/50 (0 %) are accepted** (unchanged by this pass, which adds no module).
`native_source_audit.py --check` → **231 requirement records / 36 cross-cutting obligations**, reproducing after
the new source file entered the inventory.

**H6. Not run / not claimable.** No Android build, emulator, device or provider gate. No Excel, LibreOffice,
browser or Word — `soffice`, `libreoffice` and a headless browser are absent from this host, so there is **no
visual evidence** of a filtered grid, no screenshot of a dropdown, and no observation that a row disappeared.
Everything above is byte-level and model-level agreement.

**H7. Per-module testability, in absolute numbers.** The only module whose content changed is `:format:ooxml`:
63 production files and 225 check artifacts in the census row, of which this pass adds 1 production file and 5
check artifacts, all hash-pinned. Measured against the repository's own standard, `:format:ooxml` was and remains
one of the 50/50 (100.00 %) modules with a substantial executable testable subset — and 0 modules are fully
specification-accepted, because acceptance needs an application-level witness that no host verifier can supply.

## I. Regressions versus the best prior codebase (R92, `2dd8d1d`)

No behavioural regression found by the thirty-seven re-run lanes, the 45 new controls or the full discovery run.
Two risks worth stating rather than burying:

* `StyledSheet` gained a trailing defaulted field (`autoFilter`). Every construction site in the tree passes
  named arguments or relies on defaults; the whole module compiles with `-Werror`, and the R92 probe still
  asserts that an unfiltered sheet's part ends with `</sheetData></worksheet>` — i.e. the unfiltered bytes are
  unchanged.
* The `CT_Worksheet` sequence check in the new verifier now inspects *all* children of every sheet in the R93
  artifacts. It found no violation in R93's own output, and the R92/R91/R90 lanes still pass, but the check is
  stricter than anything that ran before: a future element placed out of order will now fail a lane instead of
  being silently tolerated. That is a widening of *detection*, not of behaviour.

## J. Class A, the PR, and the next test

**Class A: still 0 applications.** `af-applied.xlsx` contains a filter record, a predicate-consistent set of
`<row hidden="1">` flags and no dishonest undecided row; openpyxl reproduces both. Whether Excel renders those
rows as filtered out — and whether its own filter engine would have hidden exactly those rows — is unobserved.

**The PR.** This turn's instruction was to merge, so the branch is pushed and PR #23 is merged into `main` at the
END of the turn, after this evidence was written.

**Next test that would move the frontier.** Open `af-applied.xlsx` in Excel, click the filter arrow on the
`colour` column: does the list show `red` checked and `blue`/`green` unchecked; are rows 3, 4, 6 and 8 hidden
while 2, 5 and 7 (the undecided ones) remain visible; does clearing the filter restore all eight rows without
touching the cell values. That single observation converts record agreement into behaviour.

**Carried, unimplemented:** device-accelerated video upscale and "remove mosaic" — unchanged, and still refused
for the same information-theoretic reason.
