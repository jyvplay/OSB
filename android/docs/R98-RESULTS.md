# R98 — Fit to page, page numbering, and the print records

**Scope of this pass.** The four holes R94/R95/R96/R97 left in the print contract, closed together because they
are one contract: `pageSetup@fitToWidth`/`@fitToHeight` are meaningless without `<sheetPr><pageSetUpPr
fitToPage="1"/>`; `firstPageNumber`/`useFirstPageNumber` are one pair; and the print area and repeating titles are
not sheet XML at all but **sheet-scoped workbook names** (`_xlnm.Print_Area`, `_xlnm.Print_Titles`), which the
model could not express because `DefinedName` had no scope. Each of those is now modelled, written, read back, and
verified against an independent reader.

**Class A applications: still 0.** No Excel, LibreOffice, browser, printer or PDF exporter ran. This pass closes
file-level gaps a reader can check; it does not show a page being laid out.

## A. What was added

| Piece | File | Change |
|---|---|---|
| `Setup` gains `fitToWidth`, `fitToHeight`, `firstPageNumber`, `useFirstPageNumber`, with bounds and the "a flag without a number prints page 1" refusal | `XlsxPrintSetup.kt` | +~55 lines |
| `Titles(rows, cols)` with `FIRST:LAST` validation on both axes | `XlsxPrintSetup.kt` | new type |
| `Spec` gains `fitToPage`, `area`, `titles`, the inert-pair refusal, and `withoutRecords()` | `XlsxPrintSetup.kt` | new fields |
| `sheetPrXml`, `quoteSheet`, `records`, `areaOf`, `titlesOf` | `XlsxPrintSetup.kt` | new functions |
| `DefinedName.localSheetId`; print records emitted from the sheets; duplicate-name-in-scope refusal; records folded back on read; out-of-scope and uninterpretable records repaired away | `XlsxStyledBook.kt` | +77/−? |
| Probe (7 sheets, 4 packages, 2 intent tables) | `testing/src/r98/.../R98Checks.kt` | new, ~450 lines |
| Runner | `tools/run_r98_checks.py` | new |
| Two independent lanes | `tools/check_r98_sheet_print.py`, `tools/check_r98_print_records.py` | new, 300 + 250 lines |
| Negative controls | `tools/test_r98_sheet_print.py`, `tools/test_r98_print_records.py` | 22 + 19 tests |

Everything else is wiring: one Gradle source set/task, two lane lines and two unittest names in the workflow, the
census pin. `XlsxStyledBook.kt`'s other emission paths, `XlsxBreaks.kt`, `XlsxHeaderFooter.kt`, `XlsxProtection.kt`,
`XlsxValidation.kt`, `XlsxAutoFilter.kt`, `XlsxMerges.kt`, `XlsxHyperlinks.kt`, `XlsxConditionalFormatting.kt`
and `XlsxConditionalFormatVerdict.kt` are untouched.

## B. Formal model

**Admissible objects.** A *print spec* per sheet: `options` (four switches), `margins` (six required reals),
`setup` (orientation, paper, scale, fit counts, first page), `fitToPage`, `area` (an A1 range on that sheet),
`titles` (`FIRST:LAST` rows and/or `FIRST:LAST` columns). A *workbook* is sheets plus defined names, each name
optionally scoped by a zero-based sheet index.

**Information/access model.** The model sees XML attributes and element order, and nothing else. It has no
pagination engine, no paper geometry, no font metrics, no printer. Consequences that are consequences, not
choices: `fitToWidth = 0` means "however many the other axis implies", so it is a **declared** zero, not a
derived count; `autoPageBreaks` remains unmodelled because honouring it means laying out a page.

**Resource counted.** 91 probe assertions; 41 negative controls; 7 sheets across 4 packages; 6 sheet-scoped
records; 19 construction refusals; 2 independent lanes; 7 openpyxl reader comparisons per lane.

**Success and failure, with quantifier order.** Success is: *for every* declared sheet, the package's `sheetPr`,
`pageSetup` and records equal the intent **and** openpyxl re-derives the same values from the file **and**
*for every* refusal case the model refuses with a message. Failure is asymmetric: one disagreeing attribute, one
record whose scope does not index its own sheet reference, or one accepted inert pair fails the pass.

**Parameter regime, units, tolerances.** Page counts and page numbers are integers in `0..32767`
(`firstPageNumber ≥ 1`); margins are inches in `0..100` with at most four decimals and compared at `1e-9`;
`paperSize` in `1..118`; `scale` in `10..400`; areas inside the 1048576 × 16384 grid; title rows/columns ordered
`FIRST ≤ LAST`; scopes in `0..sheets-1`. Units are the file's units — inches, points, page counts — never a
device's.

**Adjacent problems that must not be substituted.** (i) *Pagination*: how many pages a given `fitToWidth` produces
depends on fonts, margins, column widths and the engine's own scaling search; (ii) *painting on paper*: what a
printer or PDF exporter emits; (iii) `printOptions`/margins, which R94 already owns; (iv) header/footer, R95;
(v) row/column **breaks** (`XlsxBreaks`, R95) — a break is a request, not a page count; (vi) `definedNames` as a
general feature (formulas, hidden names, `publishToServer`), of which this pass models only the two print records
and the existing plain name.

## C. Frontier, and the exact open interface

Three imported results, each restated with its hypotheses and its source:

1. **`CT_SheetPr`'s children are ordered `tabColor`, `outlinePr`, `pageSetUpPr`, and Excel repairs the file when
   they are not.** Two independent producers hit this in the last three months: rust_xlsxwriter issue #184
   ("invalid worksheet XML when combining tab color with fit-to-pages"; Excel's recovery log replaces the sheet
   part) and exceljs's fix for exceljs#1348, which reported exactly the `outlinePr`/`pageSetUpPr` inversion. This
   pass writes `tabColor` then `pageSetUpPr` and asserts the order in both the probe and lane 1.
2. **`fitToWidth`/`fitToHeight` without `pageSetUpPr@fitToPage` do not take effect in Excel.** umya-spreadsheet
   PR #379 ("Sheets set to *fit to N x M pages* lost that setting… Excel/LibreOffice went back to the plain scale
   percentage") describes the failure mode precisely. **Open interface:** the same report's sibling probe on
   bhs.crg asks whether *LibreOffice* honours the counts without the flag (it records 1C writing that shape). This
   model does not bet either way: it **refuses** the pair rather than writing an attribute one engine may ignore.
3. **`localSheetId` is the zero-based position of the sheet in `workbook.xml`; `_xlnm.Print_Titles` repeats rows
   and columns in one name.** Verified locally rather than taken on trust: openpyxl 3.1.5, asked to set an area,
   title rows and title columns on `'Fit Two'`, writes
   `'_xlnm.Print_Titles' localSheetId="0" → 'Fit Two'!$1:$3,'Fit Two'!$A:$B` — rows first, each part absolutely
   addressed and quoted because of the space. That is the spelling this writer emits, and the reason lane 2
   asserts rows-before-columns. (docxy PR #896 describes its own struct as "columns then rows"; the two readings
   disagree, and only a real Excel run could settle which producer is right. openpyxl's output is what this
   repository can check, and it is what the lanes check.)

**Open interface between them:** whether an engine honours an inert count, and whether any engine reads
`_xlnm.Print_Titles` in the opposite order. Both are one macro away, and neither is settled here.

## D. Negative space — 16 shapes that look like a print layout

1. Writing `fitToWidth` and calling the job done — the attribute Excel ignores (item C.2).
2. Writing `fitToWidth="0"` as "one page" — `0` means *automatic*, and the probe pins `0` through the reader.
3. Writing `scale` **and** fit counts as if they composed — Excel applies fit and ignores scale.
4. Emitting `<pageSetUpPr>` before `<tabColor>` — repairs the workbook in Excel (C.1).
5. Emitting an empty `<sheetPr/>` for a sheet that declares nothing — a byte change with no meaning.
6. Writing `firstPageNumber` without `useFirstPageNumber` — the number is inert; the probe refuses the
   *flag-without-number* shape and writes the pair.
7. Quoting every sheet name (always legal, but not what Excel writes) or quoting none (breaks on spaces).
   `quoteSheet` is the rule; `'O''Brien'` is in the probe.
8. Writing a print area as `Sheet1!$A:$A` — a whole column is not an area this model accepts, and `SheetRange`
   refuses it.
9. Writing relative corners (`A1:C10`) in the record — Excel stores absolute; `$` is required.
10. Dropping the sheet prefix (`$A$1:$C$10`) — legal in some contexts, but a *sheet-scoped* name is what carries
    the meaning, and the prefix is how a reader associates it.
11. Reusing one name for two sheets (no `localSheetId`) — makes the second sheet silently shadow the first.
12. Keeping a record after the caller cleared the area — `withoutRecords()` exists so clearing is expressible;
    the probe asserts the name disappears and the `pageSetup` survives.
13. Writing a record with a scope past the last sheet — unwritable and meaningless; repaired away on read.
14. Keeping an uninterpretable record (prefix naming another sheet) as a plain name — it would then collide with
    the sheet's own record on write; dropped, and the probe asserts the collision cannot happen.
15. Inventing `paperSize` for a sheet that declares none — the probe asserts no attribute appears that was not
    declared.
16. Treating a foreign `pageSetup fitToWidth` without the flag as meaningful — the reader drops the counts and
    keeps the attributes that do mean something (paper size, orientation).

**The modal shortcut** is #1 ("write the numbers; the flag is cosmetic"). It fails because the flag is exactly
what makes the numbers mean anything; the test that detects it is lane 1's first rule (`fit counts without
pageSetUpPr@fitToPage`) plus the reader comparison of `sheet_properties.pageSetUpPr.fitToPage`.

## E. Mechanism portfolio

| # | Mechanism | Central construction | Artifact | Proved portion | Unresolved interface | Cheapest falsification | Gap |
|---|---|---|---|---|---|---|---|
| 1 | Attribute-level model with paired invariants (chosen) | refuse inert pairs; derive records from the sheet spec | `XlsxPrintSetup`, `XlsxStyledBook`, probe, 2 lanes | all attributes and both records, 6 sheets | engine pagination | LibreOffice macro dumping `PageStyle` | local |
| 2 | Round-trip through a third-party reader | openpyxl `page_setup`, `pageSetUpPr`, `print_area`, `print_title_*` | lanes 1–2 | every declared value re-derived | openpyxl is one reader, not the engine | second reader (xlsx-js, exceljs) | local |
| 3 | Schema validation (XSD) of the produced parts | validate `CT_Worksheet` child order and `CT_DefinedName` attributes against the ECMA schema | not built (no XSD on this host) | element order asserted by hand instead | full schema coverage | install `xmllint` + the 5-part XSD set | local |
| 4 | Differential against a second writer | have openpyxl write the same layout and diff `workbook.xml`/sheet parts | partially used (C.3 experiment) | print-titles spelling, quoting | openpyxl's own conventions are not Excel's | diff 20 layouts | local |
| 5 | Page-layout simulation | model fonts, widths and the scaling search to predict page counts | **not attempted** | 0 | the whole problem | needs the engine's metric tables | equivalent |
| 6 | Engine oracle | one LibreOffice macro per workbook dumping page count, scale, area and repeated rows | **unavailable on this host** | 0 | pagination, repeat fidelity | install LibreOffice headless | equivalent |

1 and 2 exist and are exercised; 3 and 4 are partial and labelled; 5 and 6 are absent, and their absence is the
Class-A gap.

## F. Artifact requirement

`testing/build/r98/artifacts/` holds `r98.xlsx` (7 sheets: `Fit`, `Fit Two`, `Numbered`, `Records`, `Single`,
`Tabbed`, `Plain`), `r98-plain.xlsx` (no layout at all), `r98-foreign.xlsx` and `r98-foreign-fit.xlsx` (another producer's
shapes, with and without the flag), `print.tsv` (17 fields per sheet) and `names.tsv` (4 fields per defined name).
Every value in the tables is re-derivable from the package alone, which is what the two lanes do.

## G. Second-order adversary — seven passes

1. **"The verdict is about a file nobody wrote."** Lane 1 reads the package, not the table: it checks
   `sheetPr` children and order, `pageSetup` attribute order, the presence of `printOptions`/`pageMargins`, and
   openpyxl's own view. The invariant it caught during construction: the *inert-pair* rule was originally checked
   after the `sheetPr` presence rule, so a tampered table produced a vaguer message ("no sheetPr") than the real
   defect ("fit counts without the flag"). The check was reordered, and the control now pins the specific
   sentence.
2. **"Clearing is just `null`."** A `Spec` with records but no `pageSetup` is legal, so clearing had to be an
   operation on a live spec: `withoutRecords()` keeps the page setup and drops the two names; the probe asserts
   both halves.
3. **"A name is a name."** Without a scope, two sheets cannot both declare an area. `localSheetId` was added, and
   with it the duplicate-in-*scope* rule: the same spelling at two scopes is legal (probe), the same spelling at
   one scope is refused (probe).
4. **"Read whatever you find."** The reader initially kept an uninterpretable record as a plain name — which then
   collided on write with the record the model had folded in, making the parsed workbook unwritable. The rule is
   now: a print record is consumed or dropped, never half-kept; out-of-range scopes are repaired away like an
   unusable `firstSheet`. Both are asserted on the foreign fixture.
5. **"Quoting is cosmetic."** `quoteSheet` is a rule with three cases (plain word, spaced, embedded quote) and one
   edge (leading digit). Lane 2 asserts the prefix equals the *sheet's own name*, quoted by that rule, so an
   unquoted `Fit Two!` fails even though a reader might accept it.
6. **"Rows and columns are interchangeable."** They are not: the order is asserted, and the control that swaps
   them fails the lane.
7. **"The tables are two independent truths."** They are not: lane 2 cross-checks `names.tsv` against
   `print.tsv` sheet by sheet, so an area that exists in one and not the other fails. Two controls break exactly
   that.

Eighth pass, unassisted: **"what would a spreadsheet do that this cannot say?"** Paginate, honour `autoPageBreaks`,
scale, render a repeated header, print. §J.

## H. Verification — what ran

Host JVM (pinned kotlinc, `-Werror`) plus Python 3.11 and openpyxl 3.1.5. No Gradle, no Android SDK, no Excel, no
LibreOffice, no browser, no printer.

```
python3 tools/run_r98_checks.py
R98_PRINT_OK assertions=91 sheets=7 records=6 refusals=19
R98 fit to page: 3 sheet(s) with pageSetUpPr@fitToPage and its counts, 4 without; openpyxl agreed on 7 sheet(s)
R98 print records: 6 sheet-scoped record(s) over 7 sheet(s), scopes and quoting verified; openpyxl agreed on 7 sheet(s)

PYTHONPATH=tools python3 -m unittest tools.test_r98_sheet_print tools.test_r98_print_records
Ran 41 tests ... OK

python3 tools/run_r47_contracts.py
R47 PASS: 2393 production-kernel assertions across 61 new kernels (+5 touched across 48/50 modules).

Regression lanes re-run in this pass, all rc=0:
  R84 writer indent 51 / R84 sheet visibility 31 / R88 / R89 / R90 / R91 / R93 / R95 (all rc=0)
  R94 98 / R96 60 / R97 70 / R98 91
  census 50/50 (100.00 %), not_established 0, 0 modules accepted
```

The probe covers: a no-layout workbook that must be byte-identical after read→write; seven sheets whose full spec
must survive read→write and a second round trip; `tabColor`+`pageSetUpPr` co-existing in one ordered `sheetPr`; a
sheet with neither that must carry no `sheetPr`; `firstPageNumber`/`useFirstPageNumber` as a pair; the records'
scopes, quoting and rows-before-columns spelling; a one-cell area spelled `$B$2`; user names before records;
four quoting cases; clearing both halves; 19 refusals; two names at two scopes kept apart; a foreign package whose inert counts are dropped and
whose uninterpretable records are repaired; and the same package with the flag on, whose counts must survive.

## I. Regressions versus the best prior codebase (R97, `671985e`)

* **None known.** R47 (2393 assertions), R84 ×2, R94, R96 and R97 all ran in this pass with their prior numbers.
* `XlsxStyledBook.kt` changed in four ways that are all additive on the write path: `sheetPr` now goes through
  `XlsxPrintSetup.sheetPrXml`, which emits the *identical* `<sheetPr><tabColor …/></sheetPr>` when `fitToPage` is
  false; `definedNames` is emitted from `definedNames + printNames(sheets)` and is empty exactly when it was
  before; `DefinedName` gained a defaulted `localSheetId`, so existing call sites compile and emit unchanged
  bytes; and `read` gained the repair pass, which only affects packages that carry scoped print records.
* The R84/R94/R96/R97 lanes and the byte-stability probe (`r98-plain.xlsx`, `R94`'s plain sheet) are the evidence
  for "unchanged", not the argument above.
* New refusals are new *constraints*, not behaviour changes: a workbook that used to be writable with an inert
  fit pair is now refused. Nothing in the repository constructed such a pair.

## J. Class A, the PR, and the next test

**Class A applications: 0.** A print layout only matters when a page comes out, and nothing here paginates. What
exists is a file model that states the layout correctly, refuses the shapes that would lie about it, and agrees
with an independent reader on every declared value.

**Census.** 50/50 modules (100.00 %) meet the bounded testability standard; **0/50 (0.00 %) are specification
accepted**. `:format:ooxml` counts 70 production files and 255 check artifacts under re-pinned hashes.

**Next test that could fail.** Install LibreOffice headless and, for each sheet in `r98.xlsx`, dump the page count,
the scale factor, the printed area and the repeated rows of the first printed page. That single run would settle
the two open interfaces in §C at once — whether the counts are honoured with the flag, and whether the titles are
read in the spelling openpyxl writes — and it is the only test that can move this component past "two readers
agree".
