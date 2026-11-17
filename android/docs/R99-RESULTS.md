# R99 — Worksheet outlining

**Scope of this pass.** `<sheetPr><outlinePr/></sheetPr>` and the `outlineLevel`/`collapsed` attributes on
`<row>`/`<col>`: the third child of `CT_SheetPr`, the one that made R98's ordering work a hypothesis about a
sequence it never populated. R98 wrote `tabColor` and `pageSetUpPr`; with `outlinePr` present, the sequence
`tabColor → outlinePr → pageSetUpPr` is now exercised by a real file, and that exact inversion is the one two
independent producers were repairing in 2026.

**Class A applications: still 0.** No Excel, LibreOffice, browser or Android target ran. Nothing was grouped,
expanded or drawn; the `+`/`-` buttons a UI would show are not modelled and not claimed.

## A. What was added

| Piece | File | Change |
|---|---|---|
| `Settings(summaryBelow, summaryRight, applyStyles)` with `isDefault`, and `Levels(rows, cols, collapsedRows, collapsedCols)` with level/grid refusals and the dangling-marker refusal | `XlsxOutline.kt` (new) | 190 lines |
| `outlinePrXml`, `rowAttributes`, `colAttributes`, `parseSettings`, `parseLevels` (span expansion with a cap) | `XlsxOutline.kt` | (same file) |
| `<sheetPr>` composition moved into the sheet writer: `tabColor`, `outlinePr`, `pageSetUpPr` fragments placed in one place | `XlsxStyledBook.kt` | +12/−3 |
| `StyledSheet.outline`, `StyledSheet.outlineSettings`; `<cols>`/`<row>` emission of grouping; parse of both | `XlsxStyledBook.kt` | +24/−8 |
| `sheetPrXml` → `pageSetUpPrXml` (a fragment; the sequence owner places it) | `XlsxPrintSetup.kt` | −9/+8 |
| Probe (6 sheets, foreign fixture, 2 intent tables) | `testing/src/r99/.../R99Checks.kt` | new, 400 lines |
| Runner + one independent lane | `tools/run_r99_checks.py`, `tools/check_r99_outline.py` | new, 75 + 300 lines |
| Negative controls | `tools/test_r99_outline.py` | 21 tests |

Wiring: one Gradle source set/task, one lane line and one unittest name in the workflow, one R47 kernel source
(the isolated jar must compile), the census pin. No other module was touched.

## B. Formal model

**Admissible objects.** Per sheet: an optional `Settings` (`summaryBelow`, `summaryRight`, `applyStyles`) and an
optional `Levels` — a map from row index to level `1..7`, the same for columns, plus two sets of `collapsed`
markers. Rows are one-based in `1..1048576`, columns in `1..16384`.

**Information/access model.** The model reads and writes XML attributes. It has no UI, no notion of a click, no
idea which rows a renderer would hide. Consequences, not choices: a level of `0` is spelled by *omitting* the
attribute, so a declared `0` is a modelling error rather than a value; `collapsed` marks the **summary** row or
column (the one beside the group), never the group's members; and hiding is expressed by `hidden="1"` on the
members, which this model already owned before R99 and deliberately did not couple to grouping.

**Resource counted.** 68 probe assertions; 21 negative controls; 6 sheets; 13 touched rows; 4 touched columns;
10 construction refusals; one third-party reader (openpyxl 3.1.5) over 15 row/column level lines.

**Success and failure, with quantifier order.** Success is: *for every* sheet, `<sheetPr>` carries `outlinePr`
exactly when non-default settings are declared **and** *for every* row/column line in `levels.tsv` the element
carries exactly the declared `outlineLevel`/`collapsed`/`hidden` **and** *for every* grouped element in the file
that line exists in the table (completeness in both directions) **and** openpyxl re-derives the same values.
Failure is asymmetric: one extra attribute, one missing line, or one span instead of one element per column fails
the pass.

**Parameter regime and units.** `outlineLevel ∈ 1..7` ("eight levels" counting the ungrouped 0); `summaryBelow` /
`summaryRight` default `true`, `applyStyles` default `false` — the schema defaults, never written; one `<col>`
element per column in this writer; the reader expands a foreign `<col min max outlineLevel>` span up to
`MAX_COL_SPAN = 1024` columns and reads a wider span as its first column.

**Adjacent problems that must not be substituted.** (i) *Hiding* (`hidden="1"`, already modelled) — a collapsed
group without hidden members looks grouped and behaves as if it were not; (ii) *auto-outlining* — Excel's
"auto outline" derives groups from subtotal formulas, a different feature; (iii) *pivot-table collapse state*;
(iv) *drawing* the outline buttons and the level strip, which is a renderer's job; (v) *removing* an outline,
which per Microsoft's own documentation can leave rows hidden — a state this model can express (hidden without
grouping) and does not attempt to tidy.

## C. Frontier, with hypotheses

1. **`CT_SheetPr` is a sequence: `tabColor`, `outlinePr`, `pageSetUpPr`.** rust_xlsxwriter #184 (2026-08) shows
   Excel replacing the sheet part when `pageSetUpPr` precedes `tabColor`; exceljs's fix for exceljs#1348 fixed the
   `outlinePr`/`pageSetUpPr` inversion specifically. R99 is the first pass in this repository that *populates* all
   three, and the probe asserts the exact string
   `<sheetPr><tabColor rgb="FFCC00FF"/><outlinePr summaryBelow="0"/><pageSetUpPr fitToPage="1"/></sheetPr>`.
2. **Excel supports eight outline levels**, outer summaries at the lower numbers and inner detail at the higher
   ones (Microsoft 365 documentation for `Range.group`/`showOutlineLevels`, repeated by several 2026 references).
   Hypothesis restated: the file spells those eight levels as `outlineLevel` 0..7 with 0 meaning *not grouped*, so
   `1..7` is the modelled range and 8 is refused rather than clamped.
3. **`collapsed` is a state of the summary row/column, and grouping is not hiding.** Microsoft's own note — that
   removing an outline while its detail is hidden can leave rows hidden — is only coherent if the two are separate
   bits. R99 keeps them separate and asserts both, in both directions.
4. **A column group is written as one `<col>` element**, which openpyxl's dimension map keys on `min`. This writer
   never emits a span (one element per column), and the reader expands foreign spans so no grouped column is lost.
5. **openpyxl keeps `None` for an omitted boolean and hands back an `Outline` object for every sheet**, even one
   with no `<sheetPr>` (measured in this pass: `Grouped → (False, None, None)`, `FitOnly → (True, True, None)`).
   Consequence: *presence* can only be checked from raw XML; the reader lane checks values only. This corrected a
   first-draft claim that openpyxl could witness absence.

**Open interface.** Whether any consumer requires the span spelling of `<col>` (some writers emit it, this one
does not), and whether `outlineLevel` 8 is honoured by any engine. Both are one file and one click away in a real
application; neither is settled here.

## D. Negative space — 16 shapes that look like grouping

1. Writing `outlineLevel="0"` — not a level, a mis-spelling of absence; refused.
2. Writing `outlineLevel="8"` — past the ceiling; refused, not clamped.
3. A `<row>` element that carries no group but exists only to hold a level — the attribute needs an element, but a
   level without cells is a row the sheet never had; the writer emits touched rows, and the checker requires the
   table to describe every grouped element.
4. Putting `collapsed="1"` on the group's members instead of the summary row — looks collapsed, highlights the
   wrong control.
5. Assuming grouping hides: a grouped row without `hidden="1"` is visible; the probe asserts a grouped row is
   **not** hidden.
6. Assuming hiding collapses: `hidden="1"` with no group is a manual hide, legal and already modelled.
7. Writing a `<col min="2" max="3" outlineLevel="1">` span — a reader keyed on `min` (this one, and openpyxl's
   dimension map) sees one grouped column where the author meant two.
8. `outlinePr` before `tabColor`, or after `pageSetUpPr` — the R98 hazard, now actually reachable.
9. Writing `<outlinePr/>` with no attributes for the schema defaults — pure noise, and it makes "declared" and
   "absent" indistinguishable; `isDefault` short-circuits it.
10. Writing `summaryBelow="1"` explicitly — same failure from the other side; only deviations are written.
11. `collapsed="1"` with no grouped row or column anywhere — a dangling flag; refused.
12. Expanding a hostile `min="1" max="16384"` span to the whole grid — memory grows with a hostile file; the
    reader caps at 1024 and reads the first column beyond that.
13. Treating an unknown `outlinePr` attribute as fatal — a foreign file would lose the sheet; unknown attributes
    are ignored and the known one kept (probe: `codeName` alongside `summaryBelow="0"`).
14. Reading an out-of-range `outlineLevel="9"` from a foreign file — ignored, never clamped to 7.
15. Distinguishing an explicit `Settings()` from an absent one — impossible in the file, so the model normalises
    both to absent, and the probe states the rule once.
16. Writing an empty `<pageSetUpPr/>` the way openpyxl does — legal, but a claim that the sheet fits to page when
    the flag is off; this writer omits it.

**The modal shortcut** is #1/#4 ("write the level, mark it collapsed"). It fails because the level without an
element is nowhere, and `collapsed` on the wrong element points the control at the detail instead of the summary.
The test that detects it: the probe's exact-string assertions on `<row r="5" collapsed="1">` versus
`<row r="2" outlineLevel="1">`, plus lane 1's per-line comparison of `collapsed` against the table.

## E. Mechanism portfolio

| # | Mechanism | Central construction | Artifact | Proved portion | Unresolved interface | Cheapest falsification | Gap |
|---|---|---|---|---|---|---|---|
| 1 | Attribute model + fragments placed by the sequence owner (chosen) | two fragment builders; one `<sheetPr>` assembly | `XlsxOutline`, `XlsxStyledBook`, probe, lane 1 | every attribute, 6 sheets, 15 lines | UI behaviour | openpyxl diff, done in this pass | local |
| 2 | Third-party reader differential | openpyxl `outlinePr`, `row_dimensions`, `column_dimensions` | lane 1 | all declared values | presence (openpyxl synthesises objects) | second reader | local |
| 3 | Differential *writer* | have openpyxl write the same logical outline and compare element/attribute shapes | **run once in this pass** (quoted below) | shapes match: `<row r="2" outlineLevel="1">`, `<row r="4" collapsed="1">`, one `<col>` per column, `outlinePr` before `pageSetUpPr` | openpyxl writes an empty `<pageSetUpPr/>`, which this model refuses to add | diff 20 outlines | local |
| 4 | Schema validation (XSD) | validate `CT_SheetPr`, `CT_Row`/`CT_Col` attributes | **absent** — no XSD on this host | element order asserted by hand | full schema coverage | install `xmllint` + the 5-part XSD set | local |
| 5 | Renderer simulation | draw the level strip and the `+`/`-` buttons | **not attempted** | 0 | the whole visible feature | needs a UI stack | equivalent |
| 6 | Engine oracle | LibreOffice macro calling `showOutlineLevels` and dumping visibility | **unavailable on this host** | 0 | what actually collapses | install LibreOffice headless | equivalent |

Mechanism 3's measured result, verbatim from openpyxl writing the same logical model into `/tmp/opx_outline.xlsx`:

```
<sheetPr><outlinePr summaryBelow="0"/><pageSetUpPr/></sheetPr>
<cols><col outlineLevel="1" width="13" customWidth="1" min="2" max="2"/> …</cols>
<row r="2" outlineLevel="1">…</row><row r="3" outlineLevel="1">…</row><row r="4" collapsed="1"></row>
```

Same child order, same one-element-per-column shape, same `collapsed` placement, same attribute names. The only
difference is the empty `<pageSetUpPr/>`, which item D.16 refuses.

## F. Artifact requirement

`testing/build/r99/artifacts/` holds `r99.xlsx` (6 sheets: `Grouped`, `Nested`, `Tabbed`, `FitOnly`, `Default`,
`Plain`), `r99-plain.xlsx` (nothing declared), `r99-foreign.xlsx` (two other-producer sheets: a grouped span with
an unknown attribute, and a 16384-wide span with an out-of-range level), `outline.tsv` (9 fields per sheet) and
`levels.tsv` (6 fields per touched row/column). Every declared level is re-derivable from the package alone.

## G. Second-order adversary — six passes

1. **"The file is the table."** Lane 1 checks both directions: every table line exists in the file, and every
   grouped element in the file exists in the table. The first draft was one-directional, and the control that
   deletes a table line caught it.
2. **"Absent and default are distinguishable."** They are not. The probe normalises `Settings()` to absent, and
   `outlinePrXml` refuses to write an element for defaults; a first draft compared the declared object directly
   and failed its own round trip.
3. **"openpyxl can witness absence."** It cannot: it hands back an `Outline` object for every sheet. The lane was
   rewritten to check values only, with presence left to the raw layer, and the reason recorded in §C.5.
4. **"`None` means false."** A defaulted attribute read as `False` made openpyxl's `summaryRight` disagree on a
   sheet that never mentioned it. The lane now reads `None` as the schema default and says so.
5. **"A span is one column."** Excel and openpyxl write column groups as spans; this writer does not, so the
   reader had to expand them — with a cap, because a hostile span is a memory attack, not a group.
6. **"Grouping is hiding."** The probe asserts both directions: a grouped row is not hidden, a collapsed group's
   members are hidden, and neither implies the other.

Seventh pass, unassisted: **"what would a spreadsheet do that this cannot say?"** Draw the strip, expand a level,
recompute an auto outline, keep the state through a save from the application. §J.

## H. Verification — what ran

Host JVM (pinned kotlinc, `-Werror`) plus Python 3.11 and openpyxl 3.1.5. No Gradle, no Android SDK, no Excel,
no LibreOffice, no browser.

```
python3 tools/run_r99_checks.py
R99_OUTLINE_OK assertions=68 sheets=6 rows=13 cols=4 refusals=10
R99 outline: 3 sheet(s) with outlinePr, 4 sheet(s) carrying grouping, 15 row/column level(s) verified;
             openpyxl agreed on 6 sheet(s)

PYTHONPATH=tools python3 -m unittest tools.test_r99_outline
Ran 21 tests ... OK

python3 tools/run_r47_contracts.py
R47 PASS: 2393 production-kernel assertions across 61 new kernels (+5 touched across 48/50 modules).
```

The probe covers: a no-outline workbook that must be byte-identical after read→write; six sheets whose outline and
settings must survive read→write; the exact three-child `<sheetPr>`; a sheet with only settings; a sheet with only
the R98 fit flag; a sheet whose only settings are the defaults (no element); `Settings` defaults and the two
non-default spellings; row shapes for grouped, hidden-and-grouped and collapsed rows; the assertion that a grouped
row is not hidden; column shapes for grouped and collapsed columns; a nested level and the level ceiling; ten
refusals; a foreign span expanded to three columns with its summary markers; a hostile span capped to one column;
an out-of-range level ignored; an unknown attribute ignored; foreign semantics surviving re-write; and a column
that is wide, hidden and grouped at once.

## I. Regressions versus the best prior codebase (R98, `4350a51`)

* **None known.** R47 (2393 assertions), R84 ×2, R94 (98), R96 (60), R97 (70) and R98 (91) all ran in this pass
  with their prior numbers after the `<sheetPr>` refactor.
* `XlsxPrintSetup.sheetPrXml` no longer exists; `pageSetUpPrXml` returns the same child fragment it used to embed.
  The only caller was the sheet writer, and the emitted bytes for every R98 case are asserted unchanged
  (`FitOnly` sheet: `<sheetPr><pageSetUpPr fitToPage="1"/></sheetPr>`).
* `StyledSheet` gained two defaulted fields; every existing construction compiles and writes the same XML, which
  the no-outline byte-identity assertion and the R94/R96/R97/R98 lanes demonstrate rather than assert by argument.
* The reader now parses `outlinePr` and row/column grouping; for a package without them it produces `null` on both
  fields, so round trips are unchanged.

## J. Class A, the PR, and the next test

**Class A applications: 0.** Outlining exists to be clicked. Nothing here clicks, expands, collapses or draws.

**Census.** 50/50 modules (100.00 %) meet the bounded testability standard; **0/50 (0.00 %) are specification
accepted**. `:format:ooxml` counts 71 production files and 258 check artifacts under re-pinned hashes.

**Next test that could fail.** In LibreOffice headless, open `r99.xlsx`, ask the `Grouped` sheet for the visible
state of rows 2–5 with `showOutlineLevels(1)`, and compare with the file's `outlineLevel`/`collapsed`/`hidden`
triples. One run would confirm or refute C.2 and C.3 together — the level ceiling's meaning and the
summary-versus-member placement of `collapsed` — and it is the only test that can move this component past "the
file says what it says".
