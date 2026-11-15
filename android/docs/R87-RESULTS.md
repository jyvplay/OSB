# R87 — Writer paragraph shading, the tab-character path, and the first third-party rendering evidence

Branch `arena/0afee65e-osb`, continuing from R86 `a190ea4` (this round is added to the same open PR #20 and
merged at the end of the turn, as instructed). Every number below came from a command run in this session.
This round produced one production field pair, two new verification lanes that use programs written by other
people, and **three defects in its own tooling** caught before the commit — §G lists each with the attack
that found it.

**Session restoration first.** At the start of this turn the working tree held the R86 delta while the local
branch ref pointed at `01ee6ce`, so the R86 commits existed only on the remote. `git fetch` +
`git diff FETCH_HEAD` proved the tree was byte-identical to `a190ea4`, the branch was re-pointed there, and
the tree became clean. No file was lost and no commit was re-created: the local ref was restored to the
commit that already existed. Everything after that is new work.

## A. Formal model

**Admissible objects.** Two groups of changes in `:format:ooxml`, both opt-in.

*Writer — paragraph shading.* `Block.Heading` and `Block.Paragraph` gain `shadingRgb: String?`. A non-null
value must match `^[0-9A-Fa-f]{6}$` (six hex digits, case-insensitive) and is emitted as

```xml
<w:shd w:val="clear" w:color="auto" w:fill="ABCDEF"/>
```

placed between `w:widowControl` and `w:tabs` in CT_PPr order. Only the **solid** `clear` pattern is
modelled: a percentage or pattern shade would be a colour the caller never asked for, so a foreign fill whose
`w:val` is not `clear` is dropped on import rather than flattened, exactly as a themed fill is.

*Writer — the tab character.* No API change: a `\t` inside `Run.text` already became `<w:tab/>` and a
`\n` became `<w:br/>`, and the reader already rejoined both. R87 is the first round that **exercises and
verifies** that path, because R86 had only ever verified tab *stops*, which are definitions with no effect
until a tab character reaches them.

**Information/access model.** The writers remain pure functions of caller data. Verification now has four
independent witnesses instead of two: our own probe, **python-docx 1.2** (a structure reader),
**docx-preview 0.4.1 under jsdom** (an HTML *renderer*: it turns the package into DOM with CSS), and
**exceljs 4.4.0** (a second spreadsheet reader, for the R86 workbook slice). The Node tools are pinned by
`tools/r87node/package.json` + `package-lock.json` and installed with `npm ci`; nothing from that directory
ships in the APK. The probe still writes both the packages and a TSV intent table, and the checkers compare
the *rendered* output and the *bytes* against it independently.

**Resource counted.** For the writer: the presence, attributes and position of `w:shd` per paragraph; the
number and placement of `<w:tab/>` run children; and, through the renderer, the CSS `background-color`,
`margin-inline*`, `text-indent`, `text-align` and heading classes it derives. Not counted: how many pages
Word produces, where a line breaks, what a tab stop actually does to the caret, or any pixel.

**Success and failure, quantifier order explicit.** **For every** paragraph declaring no fill, no `w:shd`
exists (R60/R62 byte baselines make this an equality, not a claim). **For every** paragraph declaring a fill,
exactly one `w:shd` exists with `val="clear"`, `color="auto"` and `fill` equal to the declared value
case-insensitively, positioned after `w:widowControl`/`w:numPr`/`w:pBdr` and before `w:tabs`/`w:spacing`/
`w:ind`/`w:jc`. **For every** run whose text contains `k` tab characters, exactly `k` `<w:tab/>` children
exist and no `<w:tab>` inside `w:tabs` is counted as one. **For every** rendered paragraph, the renderer's
text equals the declared text with tabs normalised to spaces, its `background-color` equals the declared fill,
its heading class matches the block kind, and its tab-span count equals the declared tab count. **For every**
R84 paragraph, `margin-inline(-start)` and `text-indent` equal the declared twips ÷ 20 in points. **For
every** workbook, exceljs reports the declared sheet state, `tabColor.argb` = `FF` + declared RGB, and
`firstSheet`.

**Parameters, boundaries, units, tolerances.** A lower-case fill is accepted and upper-cased on the wire, so `read(write(block))` returns the
canonical spelling rather than the caller's; the probe asserts both halves of that. Fills are 6 hex digits; `ABCDE`, `ABCDEFG`, `#ABCDEF`,
`GGGGGG` and the empty string are refused, lower-case is accepted and normalised to upper-case on the wire.
Twips convert at 20 per point; the render lane compares with a 0.01 pt tolerance, which is the print
precision of the CSS it produces (every fixture value is an exact multiple of 20 twips, so the tolerance is
never exercised). Text comparison collapses whitespace runs. Tab positions and leaders keep R86's 0..31680,
ascending, ≤ 64 rules, unchanged.

**Adjacent problems that must not be substituted.** Writer: character shading (`w:rPr/w:shd`, a different
element in a different parent, not implemented), `w:pBdr` borders, `w:highlight`, theme fill references
(`w:themeFill`), percentage/pattern shades, tab *stops* (R86) versus tab *characters* (R87), and pagination.
Spreadsheet: `showSheetTabs` (exceljs does not model it, so it stays openpyxl-only and is counted as skipped,
not as covered), active-tab navigation, repair prompts, recalculation.

## B. Outcome space

* **H+ (achievable here).** A solid paragraph fill, and the tab-character path, are both fully expressible
  and independently observable: python-docx agrees on text and structure, and docx-preview — a program that
  has never seen our writer — reproduces the fill as `background-color: rgb(…)`, the indents as points, and
  the tab as a tab span. *Observed: supported*, across 30 packages.
* **H− (not achievable here).** Pagination, font metrics, tab-stop arithmetic, tab-leader rendering,
  Excel's tab strip, recalculation and repair. docx-preview has no pagination engine and no tab-stop
  implementation (a tab renders as a span, and no leader dots appear); no Office application exists here.
  *Observed: true; stated as a limit, not worked around.*
* **H∂ (boundary-conditional).** Three boundaries moved the answer. (1) Pattern fills: a `w:val="pct20"`
  shade is not the colour in `w:fill`, so it is dropped, not flattened. (2) `showSheetTabs`: exceljs cannot
  read it, so the third-reader lane covers four of the five R86 workbook attributes and says so. (3) A
  `w:tab` under `w:tabs` is a *stop*, not a character — a checker that counted every `w:tab` element would
  both pass broken files and fail good ones, which is why the byte checker counts only run children and a
  control exercises exactly that confusion.

**Gate set, fixed before this commit:** (1) R60 128-case, R62 128-byte-identical and R61 64/64 baselines
unchanged; (2) `kotlinc -Werror` execution of the R87 probe; (3) raw OPC + python-docx agreement; (4)
docx-preview and exceljs reproducing the declared values for **every** artifact in the three writer lanes and
the workbook lane; (5) 43 negative controls (23 byte-level, 20 renderer/reader-level) refusing every broken
artifact; (6) full host suite with zero failures/errors; (7) census and source audit reproducible; (8) this
document naming what was not run. All eight were met before the commit; the defects in §G were found and
repaired before it.

## C. Frontier, restated with the hypotheses it imports

* `w:shd` is a CT_PPrBase child that follows `w:pBdr` and precedes `w:tabs` in `CT_PPrBase`, and the same
  `CT_Shd` type appears in `w:rPr` for character shading. The solid pattern token is `clear`; `w:color` is
  the pattern colour and `w:fill` the background. **Hypotheses imported**: that ordering (taken from the
  sequence R84 already encoded and re-used here), that `clear` + `auto` + a 6-hex `fill` is the
  interoperable solid shade, and that a non-`clear` pattern cannot be represented by a fill alone. Section
  numbers are quoted from knowledge, exactly as in R84/R86, and are not re-derived from a fetched schema.
* `w:tab` is the run-level tab character (a leaf element inside `w:r`), while `w:tab` inside `w:tabs` is a
  tab *stop*: same element name, different parents, different meaning. ECMA-376 keeps them apart;
  [Microsoft's `TabStop` metadata](https://learn.microsoft.com/en-us/dotnet/api/documentformat.openxml.wordprocessing.tabstop)
  shows the stop's parent as *"tabs (§17.3.1.38)"*, and the [ooxml.org tab-stop page](https://ooxml.org/wordml/w-tabs/)
  describes the containing element as "a container for one or more tab stop definitions". R86's checker and
  R87's checker both rely on that distinction.
* **Executed locally, and this is the new part**: `docx-preview@0.4.1` renders `<w:shd w:val="clear"
  w:color="auto" w:fill="ABCDEF"/>` as `background-color: rgb(171, 205, 239)` and `<w:tab/>` as
  `<span> </span>`; it renders `w:ind w:left="720" w:right="360" w:hanging="240"` as
  `margin-inline: 36pt 18pt; text-indent: -12pt`. Those are three independent confirmations that our
  twips-to-layout arithmetic and our fill encoding are what a renderer expects.
* **Executed locally**: `exceljs@4.4.0` reports `tabColor.argb = 'FFABCDEF'`, `zoomScale = 200`,
  `firstSheet = 2` and `state = 'hidden'` for the R86 workbooks. It reports **no** `showSheetTabs`, which is
  why that attribute is not claimed here.
* **Open interface.** Pagination, font metrics, tab-stop layout, tab leaders and Excel repair behaviour.
  docx-preview is an HTML renderer, not Word; exceljs parses, it does not calculate. This is exactly why no
  Class A claim is made.

## D. Negative space — 15 shapes that look like a solution and are not

| # | Shortcut | Why it fails the contract | Detector (real, run) |
|---|---|---|---|
| 1 | Emit `w:shd` after `w:tabs` | CT_PPr is a sequence | `test_fill_after_the_tabs_is_rejected` |
| 2 | Emit `w:shd` after `w:jc` | same, at the other end | `test_fill_after_the_alignment_is_rejected` |
| 3 | Emit `w:shd` before `w:widowControl` | same sequence, wrong side | `test_fill_before_widow_control_is_rejected` |
| 4 | Treat any `w:shd` as a solid fill | `pct20` is a pattern, not the fill colour | `test_pattern_fill_is_rejected` |
| 5 | Write `w:color="FF0000"` "for completeness" | `w:color` is the pattern colour; `auto` is the honest default | `test_foreground_colour_is_rejected` |
| 6 | Normalise fills case-sensitively | two spellings of one colour would compare unequal | `test_changed_fill_is_rejected` (upper-case wire form) |
| 7 | Shade table paragraphs "for uniformity" | cell content is a different layout context; R87 writes no fill there | `test_fill_inside_a_table_cell_is_rejected` |
| 8 | Invent a fill on a paragraph that declared none | adds visual state nobody asked for; breaks the unchanged-bytes gate | `test_invented_fill_on_a_plain_paragraph_is_rejected` |
| 9 | Count every `w:tab` element as a tab character | stops are definitions; the count would be wrong in both directions | `test_tab_stop_cannot_stand_in_for_a_tab_character` |
| 10 | Drop the `<w:tab/>` and let the stop "handle it" | a stop with no character moves nothing | `test_removed_tab_character_is_rejected` |
| 11 | Add a second `<w:tab/>` "to be safe" | one declared tab, two rendered | `test_extra_tab_character_is_rejected` |
| 12 | Emit `w:br` where `\t` was meant | soft break is not a tab; the text is unchanged but the line moves | `test_soft_break_injected_into_text_is_rejected` |
| 13 | Let the renderer lane pass an empty document | a renderer that produced nothing must not be counted as agreeing | `test_missing_rendered_file_is_rejected`, `test_extra_paragraph_is_rejected` |
| 14 | Take the renderer's background colour on faith | the colour must be the declared one, converted exactly | `test_changed_background_is_rejected`, `test_removed_background_is_rejected` |
| 15 | Trust a reader dump that lost a field | `null` is a legitimate value and must be distinguished from "not checked" | `test_dropped_colour_is_rejected`, `test_dropped_indent_style_is_rejected` |

Three further byte-level refusals cover the tab stops this round carries alongside the fills:
`test_removed_tab_stop_is_rejected`, `test_changed_tab_leader_is_rejected` and
`test_descending_tab_stops_are_rejected`. Two more refusals are structural rather than field-level:
`test_undeclared_package_is_rejected` (a package with no intent row) and `test_truncated_intent_row_is_rejected` (a row that lost a field). The render lane
adds `test_lost_heading_class_is_rejected`, `test_missing_tab_span_is_rejected` and
`test_invented_tab_span_is_rejected`; the exceljs lane adds `test_changed_zoom_is_rejected`,
`test_changed_first_sheet_is_rejected`, `test_changed_sheet_state_is_rejected` and
`test_missing_workbook_is_rejected`.

## E. Mechanism portfolio — six branches, each with a falsifier

1. **Fill as `w:shd` in `pPr` vs character-level `w:rPr/w:shd`.** Chosen: paragraph-level, because the model
   has no per-run background and inventing one for every run would change every existing document.
   Falsifier: the byte baseline would move for plain documents if it leaked; it does not.
2. **Always `val="clear" color="auto"` vs omit the attributes.** Chosen: always, because omitting `w:val`
   means `clear` by default but a reader that sees `w:fill` alone may treat the shade as themed. Falsifier:
   `test_pattern_fill_is_rejected` and `test_foreground_colour_is_rejected`.
3. **Drop non-solid fills on import vs flatten them to `w:fill`.** Chosen: drop. Falsifier: the rezip control
   that patches `w:val="pct20"` must produce `shadingRgb == null` (probe assertion), and the byte checker
   independently refuses a `pct20` package whose TSV declares a colour.
4. **Tab characters through `Run.text` vs a new `Run` flag.** Chosen: `Run.text`, because the writer already
   split `\t`/`\n` and the reader already rejoined them, so the whole feature is an existing contract that
   had never been verified. Falsifier: the round-trip equality in every R87 fixture would fail if the split
   and the rejoin disagreed.
5. **Python-only verification vs a real renderer.** Chosen: both, because python-docx proves structure and
   docx-preview proves *what a renderer does with it*. Falsifier: the render lane is run against the R84 and
   R86 corpora too, so it cannot be tuned to R87's fixtures alone.
6. **Trust the third-party lane vs cross-check it against our intent tables.** Chosen: cross-check. A
   renderer dump that disagrees with the TSV is a failure in either direction — that is what every control in
   §D exercises.

## F. Artifact requirement

Real files only. `testing/build/r87writer/artifacts/` holds 7 `.docx` packages plus `shade.tsv`;
`testing/build/r84writer/artifacts/` 12 packages + `indent.tsv`; `testing/build/r86writer/artifacts/`
11 packages + `tabs.tsv`; `testing/build/r86sheet/artifacts/` 7 workbooks + `views.tsv`. The Node lanes read
those directories in place; the controls copy a package, break exactly one thing, and require a refusal.
Reproduce with `python3 tools/run_r87_writer_shade.py` and `python3 tools/run_r87_render_lanes.py
--install`.

## G. Second-order adversary, per candidate

* **A renderer that renders nothing.** Attack: docx-preview could silently produce an empty document and a
  naive checker would compare zero paragraphs to zero rows. Outcome: the checker compares paragraph counts to
  the intent table per file and fails on a dropped file (`test_missing_rendered_file_is_rejected`), and the
  rendered count is printed (12 / 19 / 21) so a silent zero would be visible.
* **A renderer dump edited to match the checker.** Attack: the controls feed tampered dumps through the real
  checker. Outcome: 20 controls, all refused — the lane cannot be satisfied by writing the answer into the
  JSON.
* **The DOM has no CSS cascade.** Attack: docx-preview writes `margin-inline-start` in one case and
  `margin-inline: L R` in another, and a checker that only parsed the shorthand silently saw *nothing* for
  eight of the twelve R84 packages. Outcome: this actually happened in this session — the first render run
  failed with `indent-left paragraph 0: left indent declared 720 twips but the renderer produced none` —
  and the parser now accepts both forms plus `margin-inline` with a single value. The failure is why the R84
  lane asserts 11 indents rather than 3.
* **Raw-XML tab counting.** Attack: count `w:tab` elements with `iter()`, which includes tab *stops*.
  Outcome: caught by my own byte checker on `boundary.docx` (`text '\tBoundary' != declared 'Boundary'`) and
  fixed to iterate run children only; a control now guards it.
* **A third reader that quietly drops fields.** Attack: exceljs reports `null` for an absent colour, which is
  indistinguishable from "not read". Outcome: the checker requires `null` **iff** the intent table says `-`,
  so a dropped field fails (`test_dropped_colour_is_rejected`).
* **A lockfile that drifts.** Attack: `npm ci` on a stale lock installs nothing and the lane would skip.
  Outcome: both runners refuse to run without `node_modules` and the checker names the install command;
  the exact pins are printed by the lanes (`docx-preview@0.4.1`, `exceljs@4.4.0`).

## H. Verification — what actually ran

| Lane | Command | Result |
|---|---|---|
| R87 Writer probe | `tools/run_r87_writer_shade.py` | `R87_WRITER_SHADE_OK assertions=35 documents=7` |
| R87 byte checker | `tools/check_r87_writer_shade.py … --require-python-docx` | 7 packages / 12 paragraphs (6 shaded) / 2 tab characters |
| R87 render lane | `tools/run_r87_render_lanes.py` | `docx-preview@0.4.1 rendered 30 packages`: 12 R87 paragraphs (6 with a fill), 19 R84 paragraphs (11 with a declared indent), 21 R86 paragraphs |
| R87 reader lane | `tools/check_r87_sheet_exceljs.py` | `exceljs@4.4.0 read 7 workbooks and 15 sheets`: 4 colours, 3 zooms, 7 `firstSheet`; 7 `showSheetTabs` values explicitly *not* covered |
| Negative controls + full host suite | `python3 -m unittest discover -s tools -p 'test_*.py'` | **1996 tests — OK (skipped=11)**, with every artifact directory present. (An earlier run during the parallel sweep reported 155 skips, all of them "artifacts absent" skips for lanes whose runners were mid-regeneration; the 11 remaining skips are the same pre-existing artifact gaps the R86 round reported.) |
| Census | `tools/module_testability.py --check` | 50/50 = 100.00 % substantial-testable; 0/50 = 0.00 % accepted |
| Source audit | `tools/native_source_audit.py --check` | 231 requirements / 36 obligations |

### CI observation (appended after the push; this is the observation, not a local claim)

Workflow `R54 office authoring`, run 43687817844, job 132731892474, head `6dfb1f3` (PR #20):

* step 9 **Compile Android and run actual JVM contracts — success**. The invocation now carries
  `:testing:r87WriterShadeContracts`, so the new `r87Writer` source set compiled on the runner and the probe
  executed there.
* step 14 **Install the pinned R87 Node renderer and workbook reader — success**: `npm ci --prefix
  tools/r87node` installed the lockfile on the runner.
* step 15 **Independent document readers and formula comparison — success**. That step runs
  `check_r87_writer_shade.py`, `check_r87_docx_render.py` (R84 + R86 + R87 corpora), `check_r87_sheet_exceljs.py`
  and the two new control modules, so **docx-preview and exceljs ran on CI, not only on this host**.
* steps 20 `Run real API34 UI and provider checks` and 22 `Require all 37 named Office cases without
  duplicate or skip` **failed** — the same two steps that fail on `main` at `01ee6ce` and `ffcd4de`, i.e. the
  pre-existing managed-emulator failure, not a consequence of this change. The job log text is not
  downloadable from this sandbox (GitHub redirects it to blob storage, outside the egress allowlist), so this
  comparison is by step conclusion, not by reading the failure messages.

**Not run, and why:** Gradle (no Android SDK here; CI compiles the new `r87Writer` source set), Word, Excel,
LibreOffice, any device or emulator. docx-preview is a browser HTML renderer with no pagination engine, and
exceljs parses workbooks without calculating anything. No pixel comparison of a rendered page was made: the
render lane compares DOM/CSS text, not images.

## I. Regressions versus the best prior codebase (R86, `a190ea4`)

**None observed.** Thirty-three lanes were re-run after the source change and every one exits 0.

| Lane | Result |
|---|---|
| R60 writer | 675 assertions / 256 files |
| R60 baseline | **128 cases byte-identical**, pin `ab4840b2…` |
| R62 writer | 1434 assertions / 256 files |
| R62 baseline | **128 byte-identical**, pin `e89d1a21…` |
| R61 sheet | 1831 assertions / 512 cases (259 accepted / 253 refused) |
| R61 baseline | **64/64 byte-identical**, pin `86b39d90…` |
| R84 sheet visibility | 6 workbooks / 13 sheets (openpyxl + raw OPC) |
| R86 writer tabs | 11 packages / 21 paragraphs (python-docx + raw OPC) |
| R86 sheet views | 7 workbooks / 15 sheets (openpyxl + raw OPC) |
| R60 writer reference | 5377 predicates / 256 files |
| R62 writer reference | 8198 predicates / 256 files |
| R60 sheet reference | 1089 predicates / 64 files |
| R45 contracts | 508 production-kernel assertions / 33 kernels |
| R47 contracts | 2393 assertions / 61 new kernels across 48/50 modules |
| R74 writer links | 20 checks (python-docx + raw OPC) |
| R75 writer images | ImageMagick decoded the embedded media (PNG 4x3, PNG 8x2) |
| R75 sheet fractions | 136 observations against an exhaustive independent oracle |
| R76 writer layout | 9 packages / 8 with page geometry |
| R76 sheet formats | 239 rows re-derived; 231 identical to ssf, 8 adjudicated differences |
| R77 sheet conditions | 60 observations / 20 formats, 8 declared refusals |
| R81 writer tables | 9 packages / 10 tables |
| R81 sheet rows | 6 workbooks / 7 sheets |
| R82 writer align | 8 packages / 20 paragraphs |
| R82 sheet align | 6 workbooks / 17 cells |
| R83 writer spacing | 8 packages / 14 paragraphs |
| R83 sheet hidden | 6 workbooks / 6 sheets |

Two of those lanes (R76 sheet formats, R77 conditions) first failed in this session with "re-run with
`PYTHONPATH=$HOME/.cache/fusion-r76-ssf`": the pinned `ssf 0.2.2` reference package was not installed in the
fresh sandbox. After `pip install --target $HOME/.cache/fusion-r76-ssf ssf==0.2.2 babel==2.9.1` both pass
unchanged. That is an environment repair, not a code change, and it is recorded rather than hidden.

The source delta against R86 is **+32 lines in `DocxPackage.kt`, +12 in `testing/build.gradle.kts` and +9 in
the Office workflow, with no deletions**: one defaulted field on each of the two paragraph block types, one
emission helper, one reader branch, two call sites and one extra disjunct in the paragraph `properties`
guard. Nothing else in either writer was touched, and the three byte baselines are what proves it: for every
one of those 320 pinned cases the emitted package is byte-identical to R86's.

The render lane doubles as a cross-round regression test: it renders the **R84** and **R86** corpora through
docx-preview as well (19 and 21 paragraphs), so an R87 change that broke an earlier feature would fail in a
third program, not only in our own checkers.

## J. Class A, merge, stopping, and the next test

**Class A: still not claimed.** The bar is an application-level observer — Word opening the file without
repair, Excel painting the tab strip, a device rendering pagination. R87 is the first round with a *renderer*
in the loop, which moves the evidence from "two parsers agree" to "a renderer draws what we declared", but
docx-preview is an HTML approximation: it has no pagination, no font metrics and no tab-stop arithmetic.
Calling that Class A would be exactly the overclaim the rules forbid.

**Percentages.** 50/50 modules (100.00 %) carry a substantial executable testable subset; **0/50 (0.00 %)**
are fully acceptance-tested. The module count is unchanged because `:format:ooxml` was already counted; its
evidence grew to 270 paths, and the new lanes verify R84's and R86's features through a third program as well
as their own.

**Merge.** The PR (#20) is merged at the end of this turn, as the R87 instruction requires.

**Next highest-information test.** In order: (1) add a `w:rPr/w:shd` character-shade field and a
`w:pBdr` paragraph border, both of which docx-preview already renders, so the render lane keeps growing in
width rather than only depth; (2) run LibreOffice headless (`--convert-to pdf`) on the same artifacts, which
would be the first *paginating* observer; (3) open the workbooks with a real spreadsheet application to test
the `firstSheet`/repair hypothesis R86 left open. None of the three can run in this environment, and none
will be simulated.
