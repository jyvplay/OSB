# R83 — Writer paragraph spacing and spreadsheet hidden rows/columns (host-verified)

Branch `arena/b84b898d-osb`, continuing from R82 `2369f4b` on the merged `main` line. Every number below
came from a command run in this session; nothing is projected, copied from a prior round, or inferred from
a task name. Two feature increments, one of which (the `hidden="true"` import path) was found by attacking
my own reader *after* its first green run and is reported as such.

## A. Formal model

**Admissible objects.** Two constructors in `:format:ooxml` and the packages they emit.

*Writer.* `DocxPackage.Block.Heading(level, runs, alignment, spaceBeforeTwips, spaceAfterTwips)` and
`DocxPackage.Block.Paragraph(runs, alignment, spaceBeforeTwips, spaceAfterTwips)` with each spacing value in
`Int?` = `{null} ∪ 0..31680` twips, serialised by `DocxPackage.write` into a real `.docx` as one optional
`<w:spacing>` carrying only the declared attributes; `null` (the default for both) means no element at all.
Reading is `DocxPackage.read`, which maps `w:before`/`w:after` back through `toIntOrNull()`.

*Spreadsheet.* `XlsxStyledBook.StyledSheet(name, cells, freezeRows, freezeCols, columnWidths, rowHeights,
hiddenRows, hiddenColumns)` with `hiddenRows ⊆ 1..1 048 576` and `hiddenColumns ⊆ 1..16 384`,
serialised by `XlsxStyledBook.write` as `hidden="1"` on `<row>` and on `<col>`; neither set is inferred
from a cell (`hiddenRows` is a declaration, not a computed list of rows holding cells). Reading is
`XlsxStyledBook.read`.

**Information/access model.** Both writers are pure functions of caller-supplied in-memory data. The
verifiers never import Kotlin: they receive emitted bytes plus an intent table that the Kotlin probe wrote
from the *fields it declared*, never from the emitted XML or from the implementation's internals. The probe
is therefore not its own oracle; it is only the thing that supplied the package.

**Resource counted.** Emitted property content that can change layout: `w:spacing` (presence, `w:before`,
`w:after`, and its position among `w:pPr` children) and, per sheet, the `<row>` set, the `<col>` set with
their `hidden` flags, `width`/`customWidth` pairs and the occupied cells. Not counted: points of whitespace,
page breaks, scroll positions, recalculation, memory, time.

**Success and failure, with quantifier order.**
For the writer: **for every** document whose headings and paragraphs declare no spacing, no `w:spacing` is
emitted and the bytes are exactly the pre-R83 bytes (proved by the R62 and R60 byte baselines); **for every**
declared value, that paragraph's `w:pPr` contains exactly one `w:spacing`, its attributes are exactly the
declared subset of `w:before`/`w:after` with those integer values, it sits after `w:pStyle`/`w:numPr` and
before `w:jc` in CT_PPr sequence order, and the declared values read back; **and for every** list item,
quote, code block, page break or table-cell paragraph, no `w:spacing` is emitted. Round trip:
`read(write(doc)).blocks == doc.blocks` for every fixture, including the `null, null` case.

For the spreadsheet: **for every** sheet, its `<row>` set equals the declared cell rows **union** the hidden
rows (a hidden row with no cells is still emitted and still carries `hidden="1"`); **for every** row and
column, `hidden="1"` appears if and only if it is declared hidden; **for every** declared width the `<col>`
carries `width` + `customWidth="1"` and no other column carries a width; `<col>` entries are ascending,
unique, one column each; and a column may be both wide and hidden. Round trip: `read(write(book)).sheets`
equals `book.sheets`, including the hidden sets.

**Parameters, boundaries, units, tolerances.** Spacing is in twentieths of a point (`240` = 12 pt;
1440 twips = 1 inch), accepted in `0..31 680` — Word's own `ST_TblWidth`/`ST_TwipsMeasure` ceiling used
elsewhere in this file — and refused outside it with `IllegalArgumentException` (negative values included).
Hidden row indices are `1..1 048 576` and columns `1..16 384`, the sheet bounds Excel defines; `0` and
`bound+1` are refused. Widths compare as `double` with a `1e-9` tolerance in the verifier because XML
carries the shortest round-trip decimal (`18.0`). No other numeric tolerance is involved.

**Adjacent problems that must not be substituted.** Word: `w:line`/`w:lineRule` (line spacing),
`w:beforeLines`/`w:afterLines` (line-unit spacing), `w:beforeAutospacing`/`w:afterAutospacing`, style- and
theme-level spacing, *contextual* spacing (adjacent paragraphs do not add their spacing; the larger effective
value is used), and spacing across a page break. Sheet: `outlineLevel`/grouping, `collapsed`, row/column
`style`, `zeroHeight`, sheet visibility (`state="hidden"` on `<sheet>` — a different thing entirely),
`autoFilter` interaction, and the used-range/scroll consequences of hiding. Neither app's UI, provider
behaviour, or Class A acceptance.

## B. Outcome space

* **H+ (achievable here).** Both features can be added additively, verified by an independent reader that
  genuinely exposes the property, with legacy bytes unchanged. *Observed: supported* — python-docx exposes
  `paragraph_format.space_before`/`space_after` as `Length` objects and openpyxl exposes
  `row_dimensions[n].hidden` / `column_dimensions[c].hidden`; both read the property natively rather than via
  my code.
* **H− (not achievable here).** Layout: whether the spacing above a heading is *visually* 12 pt, whether it
  collapses against the neighbouring paragraph, whether the hidden row disappears from the grid, whether the
  used range shrinks. No Word, Excel, LibreOffice, renderer, display or device exists here. *Observed: true;
  stated as a limit, not a pass.* The reading of `w:spacing` I rely on makes this sharper: spacing is a
  **layout input**, and the source documents say adjacent paragraphs *do not add* their spacing but take the
  larger one, that `beforeLines`/`afterLines` **take precedence** over `before`/`after`, and that autospacing
  overrides both — three behaviours a byte-level check cannot observe.
* **H∂ (boundary-conditional).** Exact only for the emitted forms: `hidden="1"` (not `hidden="true"`), and
  `w:before`/`w:after` as integers. Foreign spellings are import-only, and the round is asymmetric: reading
  is now lenient for `hidden="true"`, while writing remains single-spelling.

**Commit threshold declared before the production edit this round:** (1) R62's 128-case byte baseline, the
R60 pinned hash and R61's 64/64 byte baseline unchanged; (2) `kotlinc -Werror` execution of both probes;
(3) python-docx **and** openpyxl reading the real artifacts; (4) negative controls rejecting every reverted
guard, each written against a real artifact; (5) full host suite with zero failures/errors; (6) census and
source audit reproducible; (7) this document naming what was not run. All seven were met before the commit;
the import-leniency defect in §G was found after (1)–(7) first passed and was fixed before this text was
final, which is why the sheet lane reports 40 assertions rather than the 37 of its first green run.

## C. Frontier (restated, with its hypotheses)

* `w:spacing` in `w:pPr` is defined by ECMA-376 Part 1 §17.3.1.33 and carries `w:before`/`w:after` as
  `ST_TwipsMeasure` (non-negative twips), plus `w:beforeLines`/`w:afterLines`, `w:line` with `w:lineRule`,
  and `w:beforeAutospacing`/`w:afterAutospacing` —
  [OOXML reference for `w:spacing`](https://ooxml.org/wordml/w-spacing/). Hypotheses imported: (i) twips are
  the unit and `240` = 12 pt; (ii) `w:before` and `w:after` may be declared independently; (iii) the
  attribute is a *request*, not a result — the same page says adjacent paragraphs use the larger of their
  spacings rather than the sum, that the `…Lines` variants take precedence, and that autospacing overrides
  the explicit values.
* `w:pPr` is a **sequence** (`pStyle → numPr → spacing → ind → contextualSpacing → jc`), so a property
  written in the wrong place is at best repaired and at worst dropped — reported independently by a
  [Euro-Office fix that lost `keepNext` when alignment moved](https://github.com/Euro-Office/core/pull/246)
  and a [writer that inserts CT_PPrBase children at their schema positions](https://github.com/UseJunior/safe-docx/pull/1294).
  Hypothesis imported: `spacing` must precede `jc`, which R82 had already made the last property this writer
  emits; therefore the new element goes *between* the existing prefix and any alignment.
* `x:row` carries a `hidden` attribute (`Row.Hidden`, schema attribute `hidden`) alongside `ht`, `r`, `s`,
  `spans`, `outlineLevel` — [Microsoft `Row` class](https://learn.microsoft.com/en-us/dotnet/api/documentformat.openxml.spreadsheet.row).
  Hypothesis imported: a `<row>` element with `r` and `hidden` and **no children** is a legal way to hide a
  row that holds no cells, which the writer now relies on.
* `x:col` carries `hidden` (`Column.Hidden`, "Hidden Columns") alongside `min`, `max`, `width`,
  `customWidth`, `outlineLevel` —
  [Microsoft `Column` class](https://learn.microsoft.com/en-us/dotnet/api/documentformat.openxml.spreadsheet.column).
  Hypothesis imported: `hidden` is independent of `width`, so one `<col>` can be wide *and* hidden; and
  `min`/`max` are 1-based inclusive bounds, which this writer emits as a single-column range (`min == max`).

**What none of those pages settle.** Whether Word applies the declared spacing in a specific document (its
style chain, collapse rules and "compatibility" options decide), and whether Excel's used range, filters and
print area treat a hidden-but-populated row as present. Both are exactly what no reader here can answer.

## D. Negative space (17 shapes that look like a solution and are not)

| # | Shortcut | Why it fails the contract | Detector |
|---|---|---|---|
| 1 | Emit `w:spacing` after `w:jc` | CT_PPr is a sequence; consumers repair or drop it | `test_spacing_after_the_alignment_is_rejected` (`out of CT_PPr order`) |
| 2 | Emit `w:spacing` before `w:pStyle` | Same, from the other side | raw-OPC `PROPERTY_ORDER` prefix rule |
| 3 | Always emit `w:spacing` (e.g. `before="0"`) | Rewrites every legacy document's bytes | R62 128-case + R60 pinned-hash baselines |
| 4 | Emit both `w:before` and `w:after` when only one is declared | Invents an instruction the caller never gave | `test_invented_sibling_is_rejected` |
| 5 | Add `w:line="240" w:lineRule="auto"` "while we are here" | Out of contract, and changes line height | `test_invented_line_spacing_is_rejected` + attribute-subset rule |
| 6 | Emit `w:spacing` on list items / quotes / code blocks | Outside the declared model; can shift numbering | `test_spacing_on_a_list_item_is_rejected` |
| 7 | Emit `w:spacing` on table-cell paragraphs | Cell paragraph properties are not this feature | `test_spacing_inside_a_table_cell_is_rejected` |
| 8 | Treat `0` as "unset" and omit it | Caller asked for exactly zero, distinct from "inherit" | probe: explicit `w:before="0" w:after="0"` emitted *and* read back |
| 9 | Clip an out-of-range value instead of refusing | A silent 31680 is a different document than asked for | probe `fails(...)` on −1 / 31 681, plus intent-range rule |
| 10 | Trust python-docx alone for ordering | It reports the parsed spacing, not child order | raw-OPC ordering check |
| 11 | Trust raw XML alone for the value | Would not show the value reaches a real reader | python-docx `space_before/space_after.twips` |
| 12 | Emit `hidden="1"` only on rows that already have cells | Silently unhides an empty hidden row | `test_dropped_hidden_only_row_is_rejected` + `row set` rule |
| 13 | Create a `<row>` with a cell to "anchor" the hidden row | Invents data | `row set` equality against the declared cell rows |
| 14 | Put the hidden flag on `<sheet state="hidden">` | Hides the whole sheet, not a row | schema-level: flag only accepted on `<row>`/`<col>` |
| 15 | Drop `width` when a column is also hidden | Loses an existing feature (R81 widths) | `test_undeclared_column_width_is_rejected` + styled fixture |
| 16 | Emit one `<col min="1" max="3">` spanning a range that includes visible columns | Over-broad: hides columns nobody asked about | `min == max` rule + `<col>` set equality |
| 17 | Read only `hidden="1"` and treat `hidden="true"` as visible | Silent unhide on import of a foreign file | probe `hidden="true"` rezip case (this round's only defect found post-green) |

**Modal shortcut, expanded: #3 followed by #9.** The tempting patch is "write `<w:spacing w:before="240"
w:after="120"/>` into every paragraph's `w:pPr`". It passes a naive "is there spacing?" check, it is one
line, and it is wrong twice: it rewrites the bytes of every document ever produced (caught by an existing,
pre-registered baseline) and it turns "the caller said nothing" into "the caller asked for 12/6 pt", which
is unobservable in any reader that only reports numbers. The same shape appears on the sheet side as
"hide rows by deleting them" — the row set check catches it, and the `max_row` assertion catches the
subtler variant of hiding by omitting cells.

## E. Mechanism portfolio (eight distinct)

| # | Mechanism | Construction | Artifact | Proved portion | Unresolved interface | Cheapest falsifier | Gap |
|---|---|---|---|---|---|---|---|
| M1 | **Chosen (writer):** optional twips pair on two block types, one shared emitter | `spaceBeforeTwips/spaceAfterTwips` + `appendSpacing` called before any `jc` | `DocxPackage.kt` (+51/−7) | attributes, order, absence, zero, refusals, round trip | layout/collapse | R62 baseline + order control | local |
| M2 | Writer: paragraph-style spacing (`word/styles.xml` `w:pPr/w:spacing`) | new part + style records | not implemented | nothing | every consumer's style resolution | verifier forbids unknown `pPr` children | different feature (styles) |
| M3 | Writer: `w:spacing` inside `w:rPr` | Reuses the element for character tracking | rejected in design | nothing | different semantic (kerning), and schema-different attributes | element location rule (`pPr` only) | **not expressible as paragraph spacing** |
| M4 | Writer: `w:contextualSpacing` + spacing | Would look right in one draft, wrong in a chain | rejected | nothing | changes collapse semantics, not values | contract has no such element | different feature |
| M5 | **Chosen (sheet):** hidden sets on the sheet, unioned into `<cols>`/`<sheetData>` | `hiddenRows`/`hiddenColumns` + sorted union | `XlsxStyledBook.kt` (+32/−6) | row/col sets, flags, widths, absence, refusals, round trip, import leniency | grid/used-range | R81 rows lane + `row set` rule | local |
| M6 | Sheet: hide by `zeroHeight="1"` on rows / `width="0"` on columns | Visually similar in some viewers | rejected (measured): R81 asserts widths are preserved | nothing | not the same attribute; export/round-trip lose it | `hidden`-only attribute rule | **different mechanism, lossy** |
| M7 | Sheet: `<sheet state="hidden">` | Hides everything | rejected in design | nothing | wrong granularity | sheet-visibility not read by this lane | different problem |
| M8 | Sheet: drop the cells of hidden rows | Shrinks the used range | rejected (measured): `max_row` assertion in openpyxl | nothing | data loss | `occupied cells` count | **destroys content** |

## F. Artifacts and what they are not

* `testing/build/r83writer/artifacts/` — 8 real `.docx` + `spacing.tsv` (14 body paragraphs): `default`
  (no spacing anywhere), `heading-spacing` (240/120 on a heading), `paragraph-before` (120 only),
  `paragraph-after` (360 only), `zero` (explicit `0`/`0`), `spacing-align` (120/240 + centred),
  `mixed` (spaced heading + spaced right-aligned paragraph + list + quote + code + sized table),
  `layout-spacing` (200/200 + `0`/120 on an A4 section with header/footer and a hyperlink).
* `testing/build/r83sheet/artifacts/` — 6 real `.xlsx` + `hidden.tsv` (6 sheets): `default` (nothing
  hidden), `rows` (rows 2 and 5 hidden, 5 carrying no cells), `columns` (B and C hidden), `both` (row 1
  hidden; A wide, B wide *and* hidden), `height-hidden` (row 3 tall and hidden), `styled` (hidden column C
  with a width beside freeze panes, alignment and a defined name).
* Tools: `run_r83_writer_spacing.py`, `check_r83_writer_spacing.py`, `test_r83_writer_spacing.py` (15
  controls: 14 refusals + 1 acceptance); `run_r83_sheet_hidden.py`, `check_r83_sheet_hidden.py`,
  `test_r83_sheet_hidden.py` (16 controls: 14 refusals + 2 acceptance).
* **Not artifacts of this round:** any rendered page, any spreadsheet screenshot, any Android frame, any
  device log. The `testing/build` trees are ignored by git and are not evidence that survives a clone; the
  Kotlin probes and Python verifiers are.

## G. Second-order adversary, per candidate

**A(writer spacing).** Structure: one optional pair, one insertion point *between* an existing prefix and an
existing optional element — so the attacker's best lever is the insertion point and the "unset" case.
1. *Insertion drift* — `w:spacing` after `w:jc`. Caught by CT_PPr order, and by the `spacing` index < `jc`
   index rule even when the rest of the order is legal.
2. *Default drift* — emitting `0`/"no-op" spacing everywhere. Caught by the two byte baselines (R62 128,
   R60 pinned hash) and by the "no element when undeclared" rule.
3. *Sibling invention* — adding `w:after="0"` because "it is zero anyway". Detected: `w:after` present while
   the intent says none. This is a *semantic* difference (inherit vs zero), not cosmetic.
4. *Unit confusion* — writing points (`12`) instead of twips (`240`). Caught by exact integer equality
   against the intent, and independently by python-docx reporting `152400` EMU (= 12 pt) for `240`.
5. *Scope creep* — spacing on list paragraphs. Caught by the per-paragraph `kind` column.
6. *Refusal erosion* — clipping instead of refusing at the boundary. Caught by `fails(...)` at −1/31 681 and
   by the intent-range rule (a tampered intent is refused before any package is opened).
7. *Reader-only success* — a value python-docx reads but that is out of sequence. Caught because the raw
   pass and the reader pass are independent and both required.

**A(sheet hidden).** Structure: two sets that must be unioned with two other sources (`cells`, `rowHeights`)
before emission, and a read path that accepts a foreign spelling.
1. *Union collapse* — a hidden row that also has a height overwrites instead of merging. Caught by the
   `height-hidden` fixture (`ht="24.0" customHeight="1" hidden="1"`) and by openpyxl reporting both.
2. *Empty-row elision* — dropping hidden rows without cells. Caught by `row set` equality (row 5).
3. *Cell invention* — adding a placeholder cell to keep a row alive. Caught by openpyxl's non-empty cell
   count and `max_row`.
4. *Column range inflation* — one `<col>` covering more than one index. Caught by `min == max` and by the
   `<col>` set equality (duplicate/extra entries refused).
5. *Width loss* — dropping `width` on a hidden column. Caught by the `styled`/`both` fixtures and by the
   width-presence rule.
6. *Attribute smuggling* — `outlineLevel`, `collapsed`, `zeroHeight`. Caught by the undeclared-attribute rule.
7. *Reader leniency failure (real defect, found post-green)* — my reader only accepted `hidden="1"`, so a
   foreign `<row hidden="true">` imported as visible. Fixed in production (`isOn`), and the probe now
   re-zips a real package with `hidden="true"` and asserts the sets still import; that is the one change in
   this round that came from attacking my own first green result rather than from the plan.

**Adversarial passes run (8).** (i) legacy-byte attack; (ii) insertion-order attack; (iii) invented-sibling
attack; (iv) scope attack on non-paragraph blocks and table cells; (v) boundary/refusal attack (clip vs
refuse); (vi) union/empty-row attack; (vii) attribute/width smuggling attack; (viii) intent tampering on both
lanes. Passes (iv) and (viii) shaped the code before the first green run (the intent had to carry the block
*kind* and the hidden sets as declarations). Pass (vii) is what produced the `hidden="true"` finding, and it
was the only pass that changed production code after a lane had reported OK.

## H. Verification — what actually ran

One agent, one Linux sandbox, 2 cores: bash, Python 3.11, Node/npm, host-only Kotlin 2.2.21 via npm, an old
JRE 17, python-docx 1.2.0, openpyxl 3.1.5, `ssf` 0.2.2, ImageMagick. **Absent:** Android SDK, emulator,
Studio, device, Word, Excel, LibreOffice, pandoc, any theorem prover, any display, any second agent.

Restore first, and it is reported as it was: `git status --porcelain` on this branch returned **zero lines**
against `2369f4b`, so there was no uncommitted work to restore and none was invented. The clone had been
shallow (`git rev-parse --is-shallow-repository` → `true`); `git fetch --unshallow` made it `false`, so
`git log`/`git diff` against `origin` are now trustworthy. `npm ci`, `npm run build` (225.28 kB,
gzip 70.00 kB), `npx tsc --noEmit`, `bootstrap_host_tools.py install`, and both reader caches
(`fusion-r76-readers`, `fusion-r76-ssf`) exited 0 on their pinned versions.

| Gate | Result |
|---|---|
| `run_r83_writer_spacing.py` | `R83_WRITER_SPACING_OK assertions=30 documents=8`; verifier: **8 packages / 14 body paragraphs** via python-docx + raw OPC |
| `test_r83_writer_spacing.py` | **15/15** (14 refusals, 1 acceptance) |
| `run_r83_sheet_hidden.py` | `R83_SHEET_HIDDEN_OK assertions=40 workbooks=6`; verifier: **6 workbooks / 6 sheets** via openpyxl + raw OPC |
| `test_r83_sheet_hidden.py` | **16/16** (14 refusals, 2 acceptance) |
| R60 Writer baseline | 128 cases, `ab4840b2…` (unchanged), 5377 predicates / 256 DOCX |
| R61 sheet baseline | 64/64 byte-identical XLSX, 128 legacy overload cases (unchanged) |
| R62 Writer baseline | **128 byte-identical**, `e89d1a2…` (unchanged), 8198 predicates / 256 files |
| R60 sheet | 1089 predicates / 64 XLSX (unchanged) |
| R74 links | `R74_WRITER_LINKS_OK assertions=18` |
| R75 images | `R75_WRITER_IMAGES_OK assertions=39`; ImageMagick decoded the media |
| R76 Writer layout | `R76_WRITER_LAYOUT_OK assertions=74 documents=9` |
| R81 writer tables / sheet rows | `44 assertions / 9 packages`, `46 assertions / 6 workbooks` |
| R82 writer align / sheet align | `25 assertions / 8 packages`, `28 assertions / 6 workbooks`; R82 sheet controls **13/13** |
| Full `tools` suite | **Ran 1846 tests, OK (skipped=20)** |
| Same suite with the two R83 modules moved aside | **Ran 1815**, so R83 adds exactly **31** tests |
| Census | **50/50 = 100.00 %** substantial testable subset, **0/50 = 0.00 %** fully accepted |
| Source audit | 231 requirements, 36 obligations, all native packages owned |

Not like-for-like: R82's document records 1801 tests for its discovery run; today the identical command on
the unmodified test set reports 1815, and I did not reproduce 1801. The delta I can defend is the +31 from
R83's two modules, measured by the experiment in the table above. I am flagging this rather than presenting
a clean "+45" that would be arithmetically true and evidentially hollow.

**Android, honestly split.** Locally nothing Android ran: no SDK, no emulator, no device. The two source
sets `:testing:r83WriterSpacingContracts` and `:testing:r83SheetHiddenContracts` are declared in
`android/testing/build.gradle.kts` and were added to the CI task list, and that CI did run — **run
`43637322263` @ `505ea5f`**, ten minutes after the commit:

* step 9 *Compile Android and run actual JVM contracts* — **success**. This is the only Gradle/Android
  execution that has ever compiled the two new source sets, and it is the first evidence that they build
  and run under the real toolchain rather than only under the pinned host `kotlinc`.
* step 14 *Independent document readers and formula comparison (host-only, not bundled)* — **success**,
  which includes `check_r83_writer_spacing.py`, `check_r83_sheet_hidden.py` and all 31 new controls.
* steps 19 *Run real API34 UI and provider checks* and 21 *Require all 37 named Office cases without
  duplicate or skip* — **failure**, the same device-only steps that have failed on every prior round; this
  round does not touch them and does not claim them.

A caveat on ordering, because it is the honest one: `R83-RESULTS.md` was written **before** that run
started, said so, and this paragraph is the later observation appended after it. The document was not
retro-edited to look as though CI evidence existed when it was first written.

**Visual inspection: not performed, and not substituted.** There is no renderer, no display and no device;
ImageMagick is present but cannot render a DOCX or XLSX. The repository has no capture for this commit. What
a picture would have shown — 12 pt of air above a heading, a collapsed gap between two spaced paragraphs, a
hidden row missing from the grid — is precisely the part `space_before` and `row_dimensions.hidden` cannot
report.

## I. Regressions versus the best prior codebase (R82, `2369f4b`)

**None observed.** The two unchanged-caller baselines are the direct evidence that an unset spacing emits
exactly the R82 bytes: R62's 128 cases are byte-identical with the same hash `e89d1a2…`, R60's 128 cases keep
`ab4840b2…`, and R61's 64/64 XLSX are byte-identical. Every prior lane I re-ran reproduces its R82 number
after the production edit: R74 18, R75 39, R76 74/9, R81 44/9 and 46/6, R82 25/8 and 28/6, R60 sheet 1089/64.
The sheet lanes were re-run a **second** time after the `isOn` change, because that change is on the import
path they exercise; all still pass.

Artifacts of the round are new files; the production diff is **+83/−13 lines across two files**. The
deletions are the two comment lines the new sequence order replaced and the old `w:jc`-only emission path
for a paragraph, which the general `w:pPr` branch now covers and which R82's own lane re-proves at 25/8. No
feature is removed. One self-inflicted defect was found and fixed during
the round (a first-pass `init {}` split in `Block.Heading` that would have validated in two places, merged
into the existing `init`), and one real defect was found by attacking a green lane (`hidden="true"` import,
§G). `check_r61_sheet_reference.py` was invoked during the sweep and refused with `Missing/symlink artifact
root` because its own lane artifacts (`testing/build/r61sheet/artifacts`) are not produced by the runner I
ran; that is stated as a gap in the sweep, not as a pass — the R61 evidence I actually obtained is the 64/64
byte baseline above.

## J. Class A, merge, stopping, and the next test

**Class A is still not claimed for Writer, Spreadsheet or anything else, and the honest reason is now
sharper than in R82.** Spacing is the first property in this series whose *whole point* is a measured result
in a layout engine: the bytes say "12 pt before", and only a word processor can say whether 12 pt of air
appeared, whether it collapsed against the previous paragraph's 6 pt, and whether a page break moved it. The
same is true of a hidden row's effect on the grid and the used range. Readers verify declarations; Class A
needs an observer of consequences. The answer to "can both go Class A this turn?" is **no**, with the
missing observers named: a real word processor, a real spreadsheet application, and hardware.

**Module accounting, in absolute numbers and percentages.** 50 of 50 modules (**100.00 %**) meet this
repository's bounded "substantial executable subset" standard; **0 of 50 (0.00 %)** meet the full
specification. Both figures are unchanged by R83, and that is the correct outcome: this round added two
verifiable *features* (spacing on headings/paragraphs; hidden rows and columns, including the import path),
not an acceptance. Feature-level testable state grew by two lanes (8 packages, 6 workbooks, 62 assertions,
31 controls) and by six Python tools plus two Gradle source sets.

**Merge.** PR #17 was merged at the end of the previous turn (`2369f4b`). This turn's work is committed on
`arena/b84b898d-osb` and merged as a new pull request at the end of the turn, as instructed. The merge is a
repository operation, not verification.

**Stopping rule reached honestly.** Two production features, two probes, six tools, 31 controls, two Gradle
source sets, a regression sweep of thirteen prior lanes and two hash-bound reports is one round's budget. The
next increment in this environment would be more declaration-level XML — the marginal information is going
down, and the remaining risks (collapse semantics, style inheritance, used-range behaviour) cannot be
touched by any reader available here.

**Next highest-information test** (outside this sandbox): open
`testing/build/r83writer/artifacts/spacing-align.docx` in Word — is there 6 pt of air above the centred
paragraph and 12 pt below it, and does the gap collapse against the neighbouring paragraph rather than add
to it? Then open `testing/build/r83sheet/artifacts/both.xlsx` in Excel — is row 1 absent from the grid,
column B absent while still 12 characters wide in the format dialog, and does `Ctrl+End` still land on the
last populated cell? Those two files, opened by the applications that define the format, are exactly the
evidence no reader in this environment can produce.
