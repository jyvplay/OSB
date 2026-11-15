# R88 — Writer paragraph borders (`w:pBdr`) and sheet-view gridline suppression, with rendering evidence

Branch `arena/fffb0d65-osb`, branched from `ea519fd` (= `main` after PR #20 was merged at
2026-11-15T07:46:13Z). This round opens a **new** PR, as instructed, distinct from the merged #20.
Every number below came from a command run in this session; §H lists them with their outputs and §I lists
what was *not* re-run.

## 0. The restoration request, answered honestly

The instruction for this turn was "use npm and other script to restore all of our uncommitted changes on
this pr". There was nothing to restore, and the honest answer is a proof rather than a script:

```
$ git status --porcelain          # (no output: the tree was clean)
$ git log --oneline -1            # ea519fd Merge pull request #20 …
$ git rev-parse origin/main       # ea519fd2501c34ce551deeea827fdd21d5ab22f5
$ git stash list                  # (no output: no stash exists)
```

R87's 16-file delta (+1900/−) is committed as `6dfb1f3` and `d937230` **inside merged PR #20**. Nothing was
lost, nothing was rebased and no commit was re-created. What actually needed restoring was the *toolchain*,
which is disposable and was rebuilt from pinned sources this turn (§H0). `npm ci --prefix tools/r87node`
installed 221 packages; `bootstrap_host_tools.py install` restored Kotlin 2.x + a cache JRE; python-docx
1.2.0, openpyxl 3.1.5, `ssf` 0.2.2 and formualizer 0.13.0 were installed into the cache directories the
runners expect. Only then were the artifacts regenerated.

## A. Formal model

**A.1 Writer — paragraph borders.** `Block.Heading` and `Block.Paragraph` gain `borders: ParagraphBorders?`.

```kotlin
enum class BorderStyle(val token: String) { SINGLE, DOUBLE, DASHED, DOTTED, THICK, NIL }   // ST_Border subset
data class Border(style, sizeEighths: Int = 4, colorRgb: String? = null, spacePoints: Int = 1)
data class ParagraphBorders(top, left, bottom, right, between, bar)   // at least one non-null
```

Admissibility is enforced in the constructors, not in the writer: `sizeEighths ∈ 2..96` (Word's UI offers
0.25–12 pt, i.e. 2–96 eighths of a point), `spacePoints ∈ 0..31` (CT_Border `w:space`), `colorRgb` matches
`^[0-9A-Fa-f]{6}$` or is `null` (written as `w:color="auto"`), and a `ParagraphBorders` with six nulls is
refused. The emitted element is

```xml
<w:pBdr><w:top w:val="single" w:sz="8" w:space="1" w:color="1F4E79"/>…</w:pBdr>
```

with edges in **CT_PBdr order** (top, left, bottom, right, between, bar) and each edge carrying all four
attributes. Position is CT_PPrBase order: after `w:widowControl`, before `w:shd`.

**A.2 Writer — import leniency.** A foreign package is not allowed to throw at the model boundary. Each edge
is mapped independently: an unknown `w:val`, a `w:sz` outside 2..96, a `w:space` outside 0..31 or a colour
that is not 6-hex drops **that edge only**; `w:val="none"` reads as `NIL` (the canonical "no line" token);
two edges with the same name resolve to the first; a box whose every edge is dropped becomes `null` rather
than an empty element. Nothing is clamped and nothing is guessed — the two behaviours that would silently
change a document.

**A.3 Spreadsheet — gridlines.** `StyledSheet.showGridLines: Boolean?` is tri-state. `null` writes nothing
and leaves Excel's default (gridlines shown); `false`/`true` write `showGridLines="0"`/`"1"` inside the
single `sheetView` element, alongside `zoomScale` and the frozen `pane`. On read, `0`/`false` → `false`,
`1`/`true` → `true`, absence → `null`.

**A.4 Access model and counted resource.** The counted resource is the byte stream of `word/document.xml`
and `xl/worksheets/sheetN.xml`, plus the model values that produced it. Both features are *additive*:
with the new field `null` the bytes are those R87 produced, which is what the three byte-identical
baselines in §I measure. There is no tolerance parameter: an edge is either the declared edge or absent.

**A.5 Quantifier order.** "For every accepted block, the emitted edge set equals the declared edge set, in
schema order, and the reader returns the declared edge set" — a per-block universal, tested on 7 paragraphs
and 14 edges, not an existential over a sample.

**A.6 Regime.** Host JVM (Kotlin 2.x, JRE 17 cache runtime) and Node 22 for the two third-party lanes.
No Word, Excel, LibreOffice, pandoc, Android SDK, emulator or browser exists in this environment, so the
regime of every claim here stops at *package structure and third-party reader/renderer agreement*.

**A.7 Adjacent non-substitutes.** Character (run) shading, table borders (`w:tblBorders`/`w:tcBorders`),
cell borders in SpreadsheetML (`<border>` in `xl/styles.xml`), tab stops, freeze panes, `showSheetTabs`,
and gridline *colour* are all different objects and are untouched. Rendering a border into our own HTML
preview would be a substitute for a renderer witness; that is exactly what §C forbids.

## B. Outcome space against the pre-declared threshold (`docs/R72-PLAN.md` §B)

**H+ (the feature removes a real defect).** Before R88 a caller could not ask for a paragraph box or a
heading rule at all: `DocxPackage` wrote no `w:pBdr`, and every paragraph came out borderless. After R88 a
declared box survives a full write→read cycle (7/7 paragraphs, 14/14 edges), is seen by a second parser
(python-docx's lxml tree: 7/7 paragraphs, 5/5 boxes), and is turned into the matching CSS by a third-party
renderer (12/12 sides). On the spreadsheet side a sheet that must hide its gridlines previously had no way
to say so; now `showGridLines="0"` is written, read by openpyxl (6/6 sheets) and by exceljs (6/6 sheets).

**H− (what this does not do).** It does not make our Writer or Spreadsheet a Class A Office application.
There is still no page layout, no font metrics, no pagination, no cell-grid layout, no formula evaluation
inside the writer, no UI, no device execution and no Word/Excel rendering anywhere in this repository. A
paragraph border is one CT_PBdr element; Word's own border drawing (line joins at corners, `w:space`
interaction with line height, `between`/`bar` semantics in tables) is not modelled and cannot be verified
here.

**H∂ (the boundary conditions that must hold).** Exact limits are accepted and limit+1 refused
(`sz` 2 and 96 accepted, 1 and 97 refused; `space` 0 and 31 accepted, −1 and 32 refused); an empty box is
refused; an out-of-window foreign value drops that edge instead of clamping it; `document.xml` is required;
`showGridLines` must live in the single `sheetView` element, and `zoomScale` stays inside 10..400.

## C. Frontier, restated with the hypotheses it imports

The frontier is *not* "more XML elements per round". It is: **can a declared document object be proven to
survive to an application-level observer?** R88 buys three things on that frontier and imports three
hypotheses while doing so:

1. *Third-party structure* — docx-preview's CSS is a faithful function of `w:pBdr`. Imported hypothesis: a
   renderer's CSS is evidence about the renderer, not about Word. It is not pagination.
2. *Tri-parser agreement* — three independent parsers (ours, lxml/python-docx, docx-preview's own XML→CSS
   path) see the same six-edge model. Imported hypothesis: agreement on structure implies agreement on
   meaning, which is only true for the subset all three implement.
3. *Additivity* — a new optional field cannot disturb existing output. Imported hypothesis: the baselines
   cover every path a null field can take (they cover 128 + 128 + 64 exact cases, not all inputs).

The next frontier object (video upscale / mosaic removal, carried from R85) is *outside* this repository's
surface: there is no player and no renderer here, so no number can be produced for it this round without
pretending. That is stated rather than simulated.

## D. Negative space — 15 shapes that look like a solution and are not

| # | Looks like | Why it is not | Detector that would catch it |
|---|---|---|---|
| 1 | Writing `w:pBdr` with all four edges whenever `borders != null` | Silently invents edges the caller did not declare | `check_r88_writer_borders.py` compares the declared edge set; `test_missing_edge_is_rejected` |
| 2 | Clamping `w:sz="200"` to 96 on import | Changes a foreign document without telling anyone | probe `a 200-eighth edge is dropped rather than clamped`; `test_changed_size_is_rejected` |
| 3 | Treating `w:sz` as points | 8 would become 8 pt instead of 1 pt | probe `sz="2" w:space="0"` / renderer `0.25pt` assertion |
| 4 | Assuming CSS has a `thick` border style | CSS has no `thick`; width carries thickness | `styles.docx` renders `3pt solid` for `thick` and the checker expects exactly that |
| 5 | Assuming docx-preview renders `w:between`/`w:bar` | It does not; a checker that demanded them would "fail" a correct writer | counted as `r88_unrendered=2` and reported, not asserted |
| 6 | Using a python-docx border API | It has none (`ParagraphFormat.border` does not exist) | the checker reads python-docx's **lxml tree** and says so |
| 7 | Emitting an empty `<w:pBdr/>` for "no borders" | An empty element is a change with no meaning | model refuses; `test_empty_pbdr_is_rejected` |
| 8 | Writing `w:pBdr` after `w:shd` | Violates CT_PPrBase sequence; Word repairs the part | `test_pbdr_after_shd_is_rejected`; probe order assertion |
| 9 | Writing `showGridLines="false"` | Legal attribute value, but Excel's own writer uses `0`/`1` | probe asserts the wire form is `0`/`1`; reader accepts both spellings |
| 10 | Writing `showGridLines="1"` for `null` | Turns "leave the default" into "force on" — a real edit | `grid-default` must contain no `sheetViews`; `test_invented_flag_is_rejected` |
| 11 | Confusing gridlines with gridline *colour* or `showSheetTabs` | Different attributes, different elements | separate lanes; `showSheetTabs` stays with openpyxl because exceljs does not model it |
| 12 | Claiming exceljs covers `showSheetTabs` | It does not report it at all | checker prints the skipped count every run |
| 13 | Rendering the border in our own preview and calling it rendering evidence | Our preview is our code: no independent observer | only docx-preview output is used as CSS evidence |
| 14 | Treating the probe's 60 assertions as Word evidence | They run our writer and our reader | every lane prints "not Word rendering" in its own output |
| 15 | Dropping a package from the intent table to make a lane pass | A verifier that only walks its table never notices | **found as a real defect this round** (§G3); both checkers now compare the folder listing against the table |
| 16 | Reporting a lane as green because it skipped | Skips read as passes in CI summaries | runners print *which* lane was skipped and why; the full suite reports `skipped=3` explicitly |
| 17 | Re-running the R87 tools with the new flags and claiming R87 unchanged | The flags touch shared code | R87's own 43 controls re-run green in §H |

## E. Mechanism portfolio — six branches, each with a falsifier

1. **New pPr child** (chosen): `w:pBdr` with per-edge attributes. *Falsifier*: a foreign package whose
   `w:pBdr` is dropped or mutated by our reader — covered by four rezip attacks in the probe.
2. **Table/cell borders instead** (`w:tcBorders`): would need a table model change. *Falsifier*: our writer's
   table path shares `w:tblPr`; a border field there would alter R64 table bytes — baselines would fail.
3. **Character borders** (`w:rPr/w:bdr`): legal but invisible to docx-preview (probe showed run-level
   `w:shd` is ignored), so no honest second witness. *Falsifier*: run-level `w:shd` probe returned `{}`.
4. **Theme colours** (`w:themeColor`): rich, but our model has no theme table, so import would have to guess.
   *Falsifier*: `w:color="theme"` reads back `null` (probe).
5. **Sheet-view gridline colour** (`<sheetView>` + `<selection>`/`colorId`): Excel writes `colorId`, a
   palette index we do not model. *Falsifier*: openpyxl exposes `sheet_view.showGridLines` only.
6. **Workbook-level default** (`bookViews/workbookView`): applies to windows, not worksheets. *Falsifier*:
   `grid-default` must have no `sheetViews`, and a workbook-level write would change `workbook.xml`.

## F. Artifact requirement

Real packages, not fixtures-in-JSON: 5 `.docx` (`plain`, `box`, `styles`, `boundary`, `combined`) and
3 `.xlsx` (`grid-default`, `grid-off`, `grid-mixed`) in `testing/build/r88/artifacts`, plus the two intent
tables `borders.tsv` and `grid.tsv` (5 packages / 7 paragraphs / 14 edges and 3 workbooks / 6 sheets). Sizes and part lists are read by both checkers; nothing is asserted from a
string that was not first written to disk and re-opened.

## G. Second-order adversary, per candidate — and the defects it found in my own tooling

The adversary here is not "a malformed file" but *a verifier that passes for the wrong reason*. Five
attacks on my own work this round, each found and fixed before the commit:

1. **Silent attribute defaulting.** `parseBorders` first read attributes with `node.attr("sz")`, which looks
   in the empty namespace; OOXML attributes are `w:` prefixed. Every edge silently read back `sz=4`,
   `space=1`, colour `null` — the probe's round-trip equality caught it (`box block round trip: … sizeEighths=4`).
   Fixed by using the module's `wAttr` helper.
2. **A control that tampered nothing.** `test_out_of_order_edges_are_rejected` swapped two strings that were
   not adjacent, so it edited nothing and the verifier "passed". Fixed with placeholders; a control must
   prove it changed the bytes.
3. **Intent-table-only iteration.** Both byte checkers walked the TSV and never compared it with the folder.
   Deleting a row (or a workbook) made the lane pass. Found by `test_dropped_intent_row_is_rejected` and
   `test_dropped_workbook_is_rejected`; both checkers now require `set(table) == set(files on disk)`.
4. **Wrong assumption about an XML shape.** The `w:shd` in `combined.docx` is self-closing; a control that
   searched for `</w:shd>` raised `ValueError` instead of tampering. Fixed by anchoring on `/>`.
5. **Parallel sweep poisoning.** Running the 42 office lanes four at a time clobbered shared build
   directories: `ClassNotFoundException: R60SheetChecks`, `unresolved reference 'PlainWriterDocx'`, and eight
   false failures. Re-ran serially: all pass. The parallelism was mine, not the code's.

Plus two environment defects, both pre-existing and both honest blockers rather than code faults: the
sandbox clone arrived **shallow** again (baseline commits `ab4840b2`, `e89d1a21`, `86b39d90` unreachable
until `git fetch --unshallow`), and the system Python is externally managed (`pip install formualizer`
needs `--target`, which is why R73's differential needed a `PYTHONPATH` entry).

## H. Verification — what actually ran

**H0. Toolchain rebuild.** `bootstrap_host_tools.py install` (Kotlin + cache JRE), `npm ci --prefix
tools/r87node` → *added 221 packages in 3s*, `pip install --target …/fusion-r76-readers python-docx==1.2.0
openpyxl==3.1.5`, `ssf==0.2.2`+`babel==2.9.1`, `formualizer==0.13.0`.

**H1. R88 probe + four independent lanes** (`python3 tools/run_r88_checks.py`):

```
R88_OFFICE_OK assertions=60 documents=5 workbooks=3
R88 independent ZIP/XML checks: 5 packages, 7 body paragraphs (5 bordered, 14 edges) verified;
  python-docx (lxml tree) agreed on 7 paragraphs and 5 boxes
R88 independent ZIP/XML checks: 3 workbooks, 6 sheets (3 with gridlines suppressed) verified;
  openpyxl agreed on 6 sheets in 3 workbooks
R87/R88 docx-preview@0.4.1 rendered 5 packages: 7 R88 paragraphs (5 bordered, 12 sides CSS-verified,
  2 between/bar edges that docx-preview does not render)
R88 gridline lane: 6 sheets in 3 workbooks cross-checked through exceljs, 3 with gridlines suppressed
```

**H2. Negative controls — 42 tests, all green.** `test_r88_borders` (15), `test_r88_gridlines` (12),
`test_r88_render_lanes` (15). Each tampers one byte, one CSS property or one reader field and requires a
refusal; the undisturbed case is asserted first so a verifier that rejects everything cannot pass.

**H3. Regression sweep — 42 office lanes, all green** (R59–R88 runners, four at a time then re-run
serially where the parallelism itself poisoned the lane):

```
run_r59_writer        400 assertions / 200 DOCX      run_r60_writer       675 / 256
run_r60_writer_baseline 128 cases, ab4840b2…         run_r60_sheet        6588 assertions / 64 XLSX
run_r60_xlsx_differential PASS (host timing only)     run_r61_sheet       1831 / 512 cases
run_r61_sheet_baseline  64/64 byte-identical, 86b39d90…   run_r62_writer  1434 / 256
run_r62_writer_baseline 128 byte-identical, e89d1a21… run_r62_range       1288 / 512
run_r63_writer_baseline PASS  run_r63_range_baseline PASS  run_r63_formula_fill 1522 / 512
run_r63_writer_restart 2246 / 192   run_r64_writer_baseline PASS  run_r64_writer_tables 814 / 192
run_r64_sheet_copy 1093 / 512       run_r64_sheet_csv 613 / 256
run_r72_writer_guard PASS  run_r72_office_zip 1703 predicates  run_r72_ooxml_zip PASS
run_r72_core_zip PASS      run_r73_formula 236 compared / 16 declared differences / 0 unexpected
run_r74_writer_links 20    run_r75_writer_images PASS  run_r75_sheet_fractions 136
run_r76_writer_layout 9 packages  run_r76_sheet_formats PASS  run_r77_sheet_conditions 60/20
run_r81_writer_tables 9/10 run_r81_sheet_rows 6/7        run_r82_writer_align 8/20
run_r82_sheet_align 6/17   run_r83_writer_spacing 8/14    run_r83_sheet_hidden 6/6
run_r84_writer_indent 12/19 run_r84_sheet_visibility 6/13 run_r86_writer_tabs 11/21
run_r86_sheet_views 7/15   run_r87_writer_shade 35 assertions  run_r87_render_lanes 19 packages
run_r88_checks PASS
```

**H4. Full host discovery.** `python3 -m unittest discover -s tools -p 'test_*.py'` →
**`Ran 2128 tests … OK (skipped=3)`** (R87: 1996 with 11 skipped; the 42 new controls are part of the delta
and the reduced skip count is the artifacts being present this time).

**H5. Census and audit.** `module_testability.py --check` → **50/50 modules (100.00 %) meet the bounded
substantial-testable standard; 0/50 (0 %) are specification-accepted**. `native_source_audit.py --check`
→ 231 requirements, 36 cross-cutting obligations, unchanged counts. `:format:ooxml` evidence paths: 215
checks + production paths.

### CI observation (appended after the push; this is the observation, not a local claim)

Commit `34edfbf` on `arena/fffb0d65-osb`, run `43697712661` (R54 office authoring), job `office`:

| Step | Result |
|---|---|
| 9 — `gradle … :testing:r88OfficeContracts` (plus every R54–R87 contract) | **success** |
| 14 — `npm ci --prefix tools/r87node` | **success** |
| 15 — independent readers: the two R88 checkers, the `--r88` render and exceljs lanes, and `test_r88_borders test_r88_gridlines test_r88_render_lanes` | **success** |
| 20 — "Run real API34 UI and provider checks" | failure |
| 22 — "Require all 37 named Office cases without duplicate or skip" | failure |

Steps 20/22 are the Android emulator gates and fail identically on `main` (they were red in R87 on `d937230`
as well); the job log text is not downloadable in this sandbox (blob storage is off the allowlist), so the
*content* of those failures is not quoted here. The `Android native` run on the same commit
(`43697712616`) completed **success**. The Gradle compile of the new source set is therefore observed, not
assumed.

**H6. What could not run.** CI's steps 20/22 ("Run real API34 UI and provider checks", "Require all 37 named
Office cases") fail on `main` too; there is no Android SDK, emulator, device, Word, Excel, LibreOffice or
browser in this sandbox, so no pixel of a Word or Excel rendering exists anywhere in this repository. The
`-Werror` Kotlin compile ran locally; the Gradle task `:testing:r88OfficeContracts` is wired and will be
compiled by CI, but its CI outcome is not observable from here in advance.

## I. Regressions versus the best prior codebase (R87, `d937230`)

**No regression observed in any lane that could run.** The three exact-byte baselines are unchanged:
R60 writer 128 cases (`ab4840b2…`), R62 writer 128 byte-identical (`e89d1a21…`), R61 sheet 64/64
(`86b39d90…`). The R87 tools still produce their old results with the new optional flags — R87's own 43
controls pass, `run_r87_writer_shade` still prints `assertions=35 documents=7`, `run_r87_render_lanes`
still renders 19 packages.

Deliberate diffs in shared tooling, all additive: `check_r87_docx_render.py` and
`check_r87_sheet_exceljs.py` gained an optional `--r88` lane and their success line now reads `R87/R88 …`;
`check_r87_docx_render.py` also moved to a `lanes` dict internally so an unknown lane cannot KeyError.
Nothing was removed, no behaviour of an existing flag changed, and the R87 controls prove it.

Not re-run, and not claimed: `:testing:r54Contracts` … `:testing:r87WriterShadeContracts` were not invoked
through Gradle this turn (local runs used the pinned kotlinc directly), and no Android, emulator, device,
provider or visual gate was executed.

## J. Class A, merge, stopping, and the next test

**Class A: still 0 applications.** Class A requires an application-level observer — Word opening the file,
Excel repairing or showing it, a device rendering it, a browser displaying it. None of those exists here.
What R88 adds is a *third* independent structure witness for two new fields, not an application.

**Merge.** Per this round's instruction the work is pushed to `arena/fffb0d65-osb` and a **new PR** is opened
at the end of the turn; #20 is already merged and is not touched.

**Stopping.** Further XML attributes are now cheap and low-information: the marginal value of a nineteenth
border style is near zero. The highest-information next test is the one this environment cannot run —
opening a real package in a real Word/Excel (or an instrumented LibreOffice headless instance) and comparing
the rendered page against the declared model. Until an application-level observer exists, the honest move is
to stop adding XML surface and say so, rather than to add a fourth lane that re-reads the same bytes.

**Carried, still unimplemented, and why.** Video upscale in the video player and browser via device hardware
acceleration: no player, no renderer, no device in this repository — nothing to modify and nothing to
measure. "Remove mosaic" is refused as information-theoretically impossible: the information was discarded
before the frame reached us. A shader-only WebGL2/WebCodecs path would preserve privacy and the lightweight
constraint, but a learned super-resolution model needs weights that break it, and neither path can be
verified without a rendering surface.
