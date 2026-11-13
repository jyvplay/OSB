# R81 — Writer table geometry and spreadsheet row heights (host-verified)

Branch `arena/95f1a43c-osb`, forked from merged `main` at `dfbc5b41a5a25bf2d9a7b4207180419e8578e71b` (the squash of PR #16).
Every number below was produced by a command in this session; nothing is projected, simulated or copied from a prior run.

## A. Formal model

**Admissible objects.** Two constructors in `:format:ooxml` and the packages they emit.

*Writer.* `DocxPackage.Block.Table(rows, columnWidthsTwips, repeatHeaderRow)` inside
`DocxPackage.Document`, serialised by `DocxPackage.write` into a real `.docx` (OPC ZIP, WordprocessingML
`word/document.xml`). `columnWidthsTwips` is `null` or a list of `Int` twips, one per column;
`repeatHeaderRow` is a `Boolean`. Reading is `DocxPackage.read`.

*Spreadsheet.* `XlsxStyledBook.StyledSheet(name, cells, freezeRows, freezeCols, columnWidths, rowHeights)`
inside `WorkbookSpec`, serialised by `XlsxStyledBook.write` into a real `.xlsx`; `rowHeights` is a
`Map<Int, Double>` from 1-based row number to height in points. Reading is `XlsxStyledBook.read`.

**Information/access model.** The writer is a pure function of caller-supplied in-memory data (no file,
URL or provider is opened). The verifiers see only the emitted bytes; no private writer state is exported
to them, and the intent table is written by the probe before the verifier runs, so a disagreement is a
real disagreement about bytes.

**Resource counted.** Structural slots that render: emitted twips width per grid column and their sum,
the presence/position of `w:tblLayout`, the presence/position of `w:trPr/w:tblHeader`, and emitted
`ht`/`customHeight` attributes per worksheet row. Not counted: rendered pixels, pagination, Word/Excel
interpretation, recalculation, memory or time.

**Success and failure, with quantifier order.**
For the writer, **for every** document such that `columnWidthsTwips == null` and `repeatHeaderRow == false`,
the emitted bytes of the table are exactly the pre-R81 bytes (legacy equality), **and for every** document
with declared widths, every declared width is in `240..31680`, the width count equals the column count,
`w:tblW = Σ widths ≤ 31680`, `w:tblW` precedes `w:tblLayout type="fixed"`, each `w:gridCol@w:w` equals its
declared width, and — **when the package declares page geometry** — `Σ widths ≤ pageWidth − left − right`.
**And for every** table with `repeatHeaderRow`, exactly the first row carries `w:trPr/w:tblHeader`, and the
`w:trPr` is that row's first child; **for every** table without it, no row carries `w:tblHeader`.
**For every** width list of wrong length, width outside the window, or total above the ceiling, construction
fails with `IllegalArgumentException` before any byte is produced.

For the spreadsheet, **for every** sheet, the set of emitted `w:row` numbers equals the set of rows that
hold cells **united with** the declared height-only rows; **for every** declared `(row, height)`,
`ht` is present, parses as that height, satisfies `0 < ht ≤ 409`, and is accompanied by `customHeight="1"`;
**for every** undeclared row, neither attribute appears; rows are strictly ascending and unique; and
**for every** height of 0, negative, above 409, or row index outside `1..1048576`, construction fails.

**Parameters, boundaries, units, tolerances.** Widths in twips (`1 pt = 20 twips`, `1 in = 1440 twips`);
window `240..31680` twips (0.17 in .. 22 in) per column and `≤ 31680` twips total — 31680 is this writer's
existing page ceiling (`MAX_PAGE_TWIPS`), reused as an app policy, not a number ECMA mandates.
Heights in points, `0 < h ≤ 409` — Microsoft's documented Excel row-height maximum ([specifications and limits](https://support.microsoft.com/en-us/excel/excel-specifications-and-limits)).
Float comparisons use `1e-9` absolute tolerance; every integer width is exact.

**Adjacent problems that must not be substituted.** Word/Excel rendering and pagination; "the header
really repeats on page 2"; OOXML schema validation of the whole package; table borders, shading, styles,
cell merging, vertical cell alignment; column-width *character* units; `w:tcW` per cell; `sheetFormatPr`
default row height; `trHeight` in Word (a different element with `hRule`); Excel recalculation; Android UI
or provider behaviour; Class A acceptance of Writer or Spreadsheet.

## B. Outcome space

* **H+ (achievable here).** Additive, default-preserving geometry to both writers, verified by two
  independent readers plus raw OPC rules, with the legacy byte stream provably unchanged.
  *Observed: supported.*
* **H− (not achievable here).** That these elements *render* as intended — a repeated header on page 2,
  a 409 pt row, a vertically centred body — cannot be established in this environment. There is no Word,
  no Excel, no LibreOffice, no display and no device. *Observed: true, and stated as a limit, not a pass.*
* **H∂ (boundary-conditional).** The claim holds exactly inside the declared windows and fails outside
  them by construction: `240`/`31680`/`409.0`/`1048576` accepted, one unit beyond refused; a table that fits
  the page passes, the same table with wider margins fails containment.

**Commit threshold declared before editing:** (1) R62's 128-case byte baseline and the R60 baseline hash
unchanged; (2) `kotlinc -Werror` execution of both probes; (3) python-docx **and** openpyxl reading the real
artifacts, not a shared oracle; (4) negative controls that reject each reverted guard; (5) full host suite
with zero failures/errors; (6) census + source audit reproducible; (7) this document naming what was not run.
All seven were met before the commit.

## C. Frontier (restated, with its hypotheses)

* `w:tbl` is a sequence: `w:tblPr` (optional), `w:tblGrid` (required), then `w:tr`+ — ECMA-376 §17.4.38 as
  summarised by the [OOXML reference for `w:tbl`](https://ooxml.org/wordml/w-tbl/). Hypothesis imported:
  the summary is faithful to the schema; I did not run a schema validator.
* `w:tblPr` is a *sequence*, not a set, and `w:tblW` sits after `w:bidiVisual` while `w:tblLayout` sits after
  `w:tblBorders` — reported independently in a [PHPWord PR repairing that order](https://github.com/PHPOffice/PHPWord/pull/2965)
  and in a [generator PR moving `w:tblLayout` after `w:tblBorders`](https://github.com/07Rinat07/GEOLOG_GASRATIO_Pixler/pull/551).
  Hypothesis imported: `tblW → tblLayout` is the correct relative order when no style/bidi/borders exist.
* `w:tblHeader` is an on/off toggle under `w:trPr` that repeats the row on each page, is applied only to
  rows contiguous from the table's first row, and is *ignored* for a non-contiguous row —
  [ECMA-376 §17.4.49 summary](https://ooxml.org/wordml/w-tblheader/), [Microsoft `TableHeader`](https://learn.microsoft.com/en-us/dotnet/api/documentformat.openxml.wordprocessing.tableheader?view=openxml-3.0.1),
  and the older [WordprocessingML row-properties table](http://officeopenxml.com/WPtableRowProperties.php).
  Hypothesis imported: "ignored" is a renderer rule; the *bytes* remain well-formed, which is all this round asserts.
* python-docx 1.2 exposes column width and table autofit but **no** header-row API — a
  [public answer to that question](https://stackoverflow.com/questions/74139113/how-to-add-the-column-headers-on-every-page-on-python-docx)
  adds `w:tblHeader` through raw `OxmlElement`, consistent with what I measured.
* openpyxl's `RowDimension.ht` is a `Float` alias of `height`, and its `customHeight` property is
  **derived** (`self.ht is not None`), not read from the attribute — [openpyxl dimensions source](https://openpyxl.readthedocs.io/en/stable/_modules/openpyxl/worksheet/dimensions.html).
  Hypothesis imported: the attribute itself must therefore be checked in raw OPC, which this round does.

**Open interface.** Whether the emitted geometry *renders* as Word and Excel would — that is the exact gap
this host cannot cross, and it is why no Class A claim is made.

## D. Negative space (18 shapes that look like a solution and are not)

| # | Shortcut | Why it fails the contract | Detector in this repo |
|---|---|---|---|
| 1 | Emit `w:tblW` only, leave `w:gridCol` bare | Word falls back to auto layout; python-docx reads `None` per column | `test_missing_table_width_is_rejected` + python-docx width equality in `check_r81_writer_tables.py` |
| 2 | Emit `w:gridCol@w:w` only, no `w:tblW` | Total width is undefined; `w:tblPr` child list changes | `tblPr children … != [tblW, tblLayout]` control |
| 3 | `w:tblLayout type="autofit"` with widths | Word ignores declared widths under autofit | `test_auto_layout_on_a_sized_table_is_rejected` |
| 4 | Percent widths (`w:type="pct"`) | Different unit and different semantics | verifier requires `type="dxa"` |
| 5 | Per-cell `w:tcW` instead of the grid | Redundant and can contradict the grid | verifier forbids `w:tcW` |
| 6 | Mark **every** row `w:tblHeader` | Word ignores non-contiguous rows; visually wrong | `only row 0 may repeat` |
| 7 | Mark a *later* row `w:tblHeader` | The toggle is ignored unless contiguous from row 0 | `test_header_marker_on_a_later_row_is_rejected` |
| 8 | Put `w:trPr` after the first `w:tc` | CT_Tr requires `trPr` first | `w:trPr must be the first child of the header row` |
| 9 | Reuse `w:tblStyle` to imply widths | No `styles.xml` exists; a style reference changes the part set | `empty w:tblPr` control |
| 10 | Change the legacy auto table to "just add widths" | Breaks R62's 128 byte-identical baseline | R62 run below |
| 11 | Skip a height-only row (no cells) | The height vanishes silently | `row set` control; openpyxl `max_row` unchanged |
| 12 | Emit `ht` without `customHeight="1"` | Consumers may treat the height as non-custom | `without customHeight` control |
| 13 | Emit heights only for rows that hold cells | Same loss as #11 for the trailing rows | same detector |
| 14 | Let a height invent a row | `max_row` grows; cells appear empty but present | openpyxl `max_row` equality + `occupied cells` count |
| 15 | Treat openpyxl's derived `customHeight` as proof of the attribute | It is `ht is not None`, so it is *not* evidence | raw-OPC attribute check; §F says so explicitly |
| 16 | Count a Python expectation recomputed from the code under test | Circular | intent table is written by the Kotlin probe; controls tamper with it |
| 17 | Call `kotlinc` success an Android build | The Android app was not compiled here | §H: CI step 9 is the only Android executor |
| 18 | Call a merge a verification | A merge moves commits, it proves nothing | §J |

**Modal shortcut, expanded: #1 + #3 together** ("declare widths, but leave the layout auto"). It is the
tempting minimal patch because `w:tblW` looks authoritative and autofit is the Word default. It fails because
ECMA-376's fixed layout is what makes `w:tblGrid` binding; under `autofit` Word re-computes column widths from
content and the declared numbers become decorative. python-docx independently reports the difference
(`table.autofit` is `False` only for `w:tblLayout type="fixed"`), so the detector is not a private opinion:
`test_auto_layout_on_a_sized_table_is_rejected` rewrites only that attribute and the verifier refuses.

## E. Mechanism portfolio (nine distinct, preserved early)

| # | Mechanism | Central construction | Artifact | Proved portion | Unresolved interface | Cheapest falsifier | Gap |
|---|---|---|---|---|---|---|---|
| M1 | **Chosen:** additive fields, default-preserving emission | `?List<Int>` + `Boolean` default; two code paths in one `is Block.Table` branch | `DocxPackage.kt`, 52 changed lines | legacy bytes, geometry, refusal window, containment | rendering | R62 byte baseline | local |
| M2 | Table style (`w:tblStyle` = "Table Grid") | would need `word/styles.xml` + rels | not implemented | nothing | new part changes the package shape | verifier forbids non-empty `tblPr` beyond the two elements | **equivalent to a different feature** (styles), not to geometry |
| M3 | Per-cell `w:tcW` | widths repeated in every cell | rejected in design | nothing | grid/width contradiction | forbidden `w:tcW` | local, redundant |
| M4 | Percent widths | `w:type="pct"` relative to text column | rejected in design | nothing | page width becomes an input | `type="dxa"` requirement | local but changes semantics |
| M5 | Autofit + widths | emit widths, leave layout auto | rejected by control #3 | nothing | Word ignores the numbers | control | local, silently wrong |
| M6 | No change (status quo) | bare `w:gridCol` | measured | it *is* the baseline | no geometry at all | python-docx `None` | none |
| M7 | Sheet: `sheetFormatPr/@defaultRowHeight` | one global default | rejected in design | nothing | per-row intent lost | row-set equality | different feature |
| M8 | Sheet: heights only for rows with cells | simpler loop | rejected by control | nothing | trailing height-only rows vanish | `row set` | local, lossy |
| M9 | **Chosen:** union of cell rows and height rows | `(byRow.keys + sheet.rowHeights.keys).toSortedSet()` | `XlsxStyledBook.kt`, 22 changed lines | set equality, attributes, bounds | Excel rendering | `row set` control | local |

Each mechanism reports its own artifact location, so an abandoned branch leaves no code behind: M2–M5,
M7–M8 exist only as rejected designs and as detectors in the verifier.

## F. Artifacts and what they are not

* `testing/build/r81writer/artifacts/` — 9 real `.docx` files and `tables.tsv` (10 tables).
  `auto-table`, `sized-table`, `repeat-header`, `sized-repeat`, `wide-table`, `boundary-width` (exactly
  31 680 twips), `two-tables`, `layout-table` (A4 section), `single-cell`.
* `testing/build/r81sheet/artifacts/` — 6 real `.xlsx` files and `rows.tsv` (7 sheets).
  `plain`, `heights` (including a height-only row 3), `max-height` (409.0), `fractional` (13.5 and 0.75),
  `styled` (heights + fill/bold/number format/formula + freeze + widths + defined name), `multi-sheet`.
* Tools: `run_r81_writer_tables.py`, `check_r81_writer_tables.py`, `test_r81_writer_tables.py` (15 controls);
  `run_r81_sheet_rows.py`, `check_r81_sheet_rows.py`, `test_r81_sheet_rows.py` (13 controls).
* **Not artifacts of this round:** any rendered page, any Excel screenshot, any Android frame. None exist
  for `95f1a43c`.

## G. Second-order adversary, per candidate

**A(writer table).** Derived from the chosen structure: the emission is one `if/else` on `widths == null`,
so the attacker targets the *default* branch and the *transition* between branches.
1. *Default-branch drift* — sneak a `w:tblLayout` into the auto path. Caught by R62 (128 byte-identical) and
   by the raw-OPC rule "a table without declared widths must carry an empty `w:tblPr`".
2. *Half-specified table* — widths present, grid partially bare. Caught by per-column equality and by the
   lenient-import probe that rebuilds a CRC-valid package with the `w:w` attributes stripped.
3. *Order inversion* — `w:tblLayout` before `w:tblW`. Caught by the child-name list, not by presence tests.
4. *Header ambiguity* — `w:tblHeader` on rows 0 and 2. Caught by "only row 0 may repeat".
5. *Overflow* — geometry internally consistent but wider than the text column. Caught by the containment rule
   and by `test_table_wider_than_the_text_column_is_rejected`, which widens margins instead of the table so
   that containment is the *only* failing rule.
6. *Round-trip asymmetry* — reader accepts what the writer cannot produce. Caught by import leniency semantics:
   partial grids import as `null`, never as a contradiction, and never as an exception.
7. *Boundary off-by-one* — 239/240/31 680/31 681. Four separate assertions plus two refusals.

**A(spreadsheet rows).** Derived from the chosen structure: the emission loop is a union of two key sets,
so the attacker targets the union and the attribute pair.
1. *Union collapse* — a height-only row dropped. Caught by `row set` equality and by openpyxl `max_row`.
2. *Fabricated cells* — a height row that becomes a data row. Caught by `occupied cells` and `max_row`.
3. *Attribute split* — `ht` without `customHeight`. Caught in raw OPC, deliberately **not** by openpyxl,
   whose `customHeight` is derived from `ht` (a second-order finding: an openpyxl-based check would have
   been vacuous).
4. *Ordering* — a height row emitted after a larger row number. Caught by strict ascending order and
   by the reordering control.
5. *Duplicate row* — two `w:row` with the same `r`. Caught by uniqueness before per-row checks.
6. *Float formatting* — `9.75` vs `9.7500000001`. Caught by 1e-9 tolerance in both readers; exactness is not
   claimed beyond that.
7. *Bounds* — 0.0, −3.0, 409.01, row 0, row 1 048 577 refused; 409.0 at row 1 048 576 accepted.

**Adversarial passes run (9, more than the required 5).** (i) baseline-byte attack; (ii) half-specified grid;
(iii) order inversion; (iv) header contiguity; (v) containment/overflow; (vi) intent tampering; (vii) row-set
union; (viii) derived-property trap (openpyxl `customHeight`); (ix) boundary off-by-one. Passes (vi)–(ix)
changed the code or the claims: (vi) produced the `write_intent`-style requirement that tampered intent must
fail loudly; (viii) forced the `customHeight` claim down to raw OPC; (ix) is why both `240` and `31 440+240`
are asserted as *accepted* rather than only the refusal side.

## H. Verification — what actually ran

One agent, one Linux sandbox: bash, Python 3.11, Node/npm, a host-only Kotlin 2.2.21 compiler shipped
through npm, an old JRE 17, python-docx 1.2.0, openpyxl 3.1.5, `ssf` 0.2.2, formualizer 0.13.0, and
ImageMagick. **Absent:** Android SDK, emulator, Studio, device, Word, Excel, LibreOffice, pandoc, any
theorem prover, any display, and any second agent. Concurrency is process-level only.

Restore first: the branch was cut from the merged `main`; `git add -A` produced an empty diff (there was no
uncommitted work to recover, and this document says so instead of inventing a recovery), `npm ci`,
`npm run build` (225.28 kB, gzip 70.00 kB) and `npx tsc --noEmit` all exited 0.

| Gate | Result |
|---|---|
| `run_r81_writer_tables.py` | `R81_WRITER_TABLE_OK assertions=44 documents=9`; verifier: **9 packages / 10 tables** with python-docx + raw OPC |
| `test_r81_writer_tables.py` | **15/15** controls |
| `run_r81_sheet_rows.py` | `R81_SHEET_ROW_OK assertions=46 workbooks=6`; verifier: **6 workbooks / 7 sheets** with openpyxl + raw OPC |
| `test_r81_sheet_rows.py` | **13/13** controls |
| R60 Writer contracts | 675 assertions, 256 DOCX |
| R60 baseline | 128 cases, `ab4840b2…` (unchanged) |
| R62 baseline | **128 byte-identical**, `e89d1a2…` (unchanged) |
| R74 links | 20 independent checks |
| R75 images | ImageMagick decoded the embedded media (PNG 4×3, 8×2) |
| R76 Writer layout | 74 assertions, 9 packages |
| R76 sheet formats | 239 rows re-derived, 231 identical through `ssf`, 8 adjudicated |
| R77 conditions | 207 assertions, 60 observations, 7/7 `ssf` controls, 4/4 pinned |
| Full `tools` suite | **1775 tests OK (22 skipped)** |
| Census | **50/50 = 100.00 %** substantial testable subset, **0/50 = 0.00 %** fully accepted |
| Source audit | 231 requirements, 36 obligations, all native packages owned |
- CI on `8a68396` (run `43363690711`): step 9 *Compile Android and run actual JVM contracts* **success**
  (this is the only Gradle/Android compile that ever ran the new `:testing:r81WriterTableContracts` and
  `:testing:r81SheetRowContracts` tasks), step 14 *Independent document readers and formula comparison*
  **success**, step 19 *Run real API34 UI and provider checks* **failure**, step 21 *Require all 37 named
  Office cases without duplicate or skip* **failure**. The two failures are the same device-only steps that
  have failed on every round in this environment; they are observations, not passes.

**Downgraded claims, explicitly.** (1) `w:tblHeader` has no independent-reader check — python-docx has no
API for it, so it is structural only. (2) openpyxl's `customHeight` is derived from `ht`, so the
`customHeight="1"` attribute is raw-OPC evidence only. (3) Height-only rows are invisible to openpyxl's cell
model; the row-set rule is raw OPC. (4) Nothing here is Word, Excel, Android or rendering evidence.

**Visual inspection: not performed, and not substituted.** There is no renderer or display in this
environment, so no new frame was produced. The repository's only real captures (`r24c-silver.png` and the CI
artifacts) belong to earlier revisions and cannot be regenerated for this branch; this document records that
gap rather than calling a screenshot a pass. The one geometric property a picture would have shown —
a table overflowing its text column — is asserted numerically instead, and its control widens the margins so
that only containment fails.

## I. Regressions versus the best prior codebase (R80, `dfbc5b4`)

**None observed, and several are structurally excluded.** R62's 128-case byte-identical baseline and R60's
pinned hash are unchanged, which is the direct evidence that the new fields' defaults emit exactly the old
bytes. R76 Writer layout, R74 links, R75 images, R76 formats, R77 conditions all reproduce. The discovery
suite reports **1775** tests, 28 of which are new this round (15 writer + 13 sheet controls, measured as
`Ran 28 tests`), so the pre-round count on this branch was 1747; the writer lane gained 44 assertions and
the sheet lane 46. Census stays 50/50 — 0/50. Production change is 74 added lines
across two files, with no removal.

Two defects of *this* round were found and fixed before any gate was called green: the styled-sheet probe
declared cells in non-sheet order and therefore failed its own round-trip equality (fixed by declaring sheet
order, and the reason is written in the code), and the sheet verifier reported a shuffled sheet as a stray
height instead of an ordering violation (fixed by checking ordering and uniqueness before per-row attributes).

## J. Class A, merge, stopping, and the next test

**Class A is not claimed for Writer, Spreadsheet or anything else.** The required observer — a real word
processor, a real spreadsheet engine that recalculates, and a device — does not exist here. This round adds
two *structural* capabilities to each app and one containment invariant; it does not convert either app into
a Class A feature, and the user's question ("can both go Class A this turn?") is answered **no**, with the
reason named rather than papered over.

**Merge.** Per instruction, the PR for this branch is merged at the end of the turn. That is a repository
operation, not verification: the CI job's two device-only steps are expected to stay red, exactly as on every
prior round, and the merge changes nothing about what was or was not measured.

**Stopping rule reached honestly.** The budget was spent on: two production features, two probes, six tools,
28 controls, and a full regression sweep. The next increment toward Class A cannot be produced by more
host-side XML work — it needs a renderer.

**Next highest-information test** (outside this sandbox): open `testing/build/r81writer/artifacts/sized-repeat.docx`
in Word and scroll past page 1 — does the `Stage / Owner` header repeat at the top of page 2 with columns at
2 400 and 4 800 twips (1.67 in and 3.33 in)? Then open
`testing/build/r81sheet/artifacts/heights.xlsx` in Excel — is row 1 exactly 24 pt, row 3 exactly 9.75 pt and
visibly empty, and does row 2 keep its default height? Those four observations are precisely what no reader
available here can answer.
