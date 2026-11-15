# R86 — Writer tab stops + page/keep flags, and workbook view options (host-verified)

Branch `arena/0afee65e-osb`, continuing from R84 `01ee6ce` on the merged `main` line. Every number below came
from a command run in this session. This round found **three** defects in its own work after a lane had
already reported OK — a tautological checker assertion, a serialization churn that rewrote an unrelated
document, and a regression lane that was red for a reason none of the previous rounds had recorded. They are
in §G with the attacks that exposed them. Nothing here is Word, Excel, Android or device evidence.

## A. Formal model

**Admissible objects.** Two constructors in `:format:ooxml` and the packages they emit.

*Writer.* `DocxPackage.Block.Heading` and `Block.Paragraph` gain three fields, all defaulted so that every
pre-R86 call site is unchanged:

* `tabStops: List<TabStop>` where `TabStop(positionTwips: Int, align: TabAlign, leader: TabLeader? = null)`,
  `TabAlign ∈ {LEFT, CENTER, RIGHT, DECIMAL, BAR, CLEAR}` and
  `TabLeader ∈ {NONE, DOT, HYPHEN, UNDERSCORE, HEAVY, MIDDLE_DOT}`;
* `pageBreakBefore: Boolean?` and `widowControl: Boolean?` — **tri-states**, for the reason R84 established
  for the keep flags: an omitted `w:pageBreakBefore`/`w:widowControl` inherits from the style hierarchy and
  does not mean "off", so `null` writes nothing, `true` writes the bare element and `false` writes
  `w:val="0"`.

`TabStop` is normalised at construction: `positionTwips ∈ 0..31680` (0 ≤ pos ≤ 22 in), stops strictly
ascending after a stable dedupe of equal positions (first wins), at most 64 of them, and every emitted
`w:tab` carries an explicit `w:val` (§C explains why this is stricter than the schema).

*Spreadsheet.* `StyledSheet` gains `tabRgb: String?` (exactly six hex digits; written as `FF<hex>` inside
`<sheetPr><tabColor>`, which must precede `<sheetViews>` in CT_Worksheet) and `zoomScale: Int?`
(10..400, written on the single `<sheetView>` that also carries a frozen pane, not on a second one).
`WorkbookSpec` gains `showSheetTabs: Boolean?` and `firstSheet: Int?`; `firstSheet` must name a **visible**
sheet, because a hidden leftmost tab is not a tab and the workbook is repaired on open (§C says how far that
claim is verified). Reading is `XlsxStyledBook.read`; on import a `firstSheet` that no longer points at a
visible sheet, a `tabRgb` that is not a full 6-hex `rgb` (theme and indexed colours are left unset rather
than guessed) and a `zoomScale` outside 10..400 are dropped rather than repaired.

One inherited asymmetry is recorded rather than fixed: a paragraph whose only content is a page-break run is
still imported as `Block.PageBreak`, which carries no fields, so tab stops or page/keep flags on such a
paragraph are dropped on import. The model cannot express them, and widening `Block.PageBreak` is an R84-era
shape that this round does not touch.

**Information/access model.** Both writers remain pure functions of caller-supplied data; the verifiers never
import Kotlin. Each probe writes the real OPC package *and* a TSV intent table derived from the fields it
declared (`tabs.tsv`, `views.tsv`). The checkers read the TSV, then inspect the emitted bytes twice: with
**python-docx 1.2.0 / openpyxl 3.1.5** (an independent implementation that natively models `tab_stops`,
`page_break_before`, `widow_control`, `sheet_view.zoomScale`, `worksheet.sheet_properties.tabColor` and
`wb.views`) and with **raw ZIP + XML**. Neither the intent table nor the probe can satisfy the raw-XML
assertions, which are literal substrings checked against the emitted part.

**Resource counted.** Layout/navigation-bearing property content: the `<w:tabs>` container and each `<w:tab>`
element (attribute set, order, count), `w:pageBreakBefore`/`w:widowControl` presence and `w:val`, and their
position among the `w:pPr` children; per sheet the `<sheetPr>`/`<tabColor>` element, the `zoomScale`
attribute and the single-`sheetView` invariant; per workbook `<bookViews><workbookView>` with
`showSheetTabs`/`firstSheet`/`activeTab`. Not counted: where Word actually moves the insertion point, whether
a page break lands where declared, what Excel paints in the tab strip, or any repair prompt.

**Success and failure, quantifier order explicit.**
**For every** document whose blocks declare no tab stop and no page/keep flag, the bytes are exactly the
pre-R86 bytes (proved by the R60/R62 128-case and R61 64/64 baselines, §H). **For every** declared stop list,
the paragraph's `w:pPr` contains exactly one `<w:tabs>` whose children are exactly the declared stops in
ascending order, each with an explicit `w:val`, its `w:pos` and a `w:leader` **iff** one was declared.
**For every** declared `pageBreakBefore`/`widowControl`, the element is present with `w:val="0"` exactly when
the value is `false`. **For every** paragraph, `w:pPr` children appear in CT_PPr order — here
`pStyle, pageBreakBefore, widowControl, tabs, spacing`. **For every** list item, quote, code block and table
cell, none of these elements appears. Round trip: `read(write(doc)).blocks == doc.blocks` for every fixture.
**For every** workbook, `<bookViews>` exists **iff** something needs it (a hidden sheet, `showSheetTabs`, or
`firstSheet`); `activeTab` names the first visible sheet **iff** a sheet is hidden; `<tabColor rgb="FF…">` is
the only child of a written `<sheetPr>`; a sheet with zoom and a frozen pane has **one** `<sheetView>` carrying
both attributes. Round trip: `read(write(book)).sheets` and the two workbook-level option fields survive.

**Parameters, boundaries, units, tolerances.** Twips again: 1440 = 1 in, 20 = 1 pt; `w:pos` is accepted in
0..31680 and refused outside (`−1` and `31681` are both rejected on the writer, and out-of-range intent rows
are rejected by the checker). Stop count: 64 accepted, 65 refused. Zoom: 10 and 400 accepted, 9 and 401
refused. Colour: `ABCDEF`/`abcdef` accepted (upper-cased on write), `ABCDE`, `#ABCDEF`, `GGGGGG` refused on
write; a foreign `theme=`/`indexed=` colour is dropped on read. No floating-point tolerance is used anywhere:
every comparison in this round is exact bytes, exact integers or exact strings.

**Adjacent problems that must not be substituted.** Writer: default tab interval ((0.5 in) implicit stops, not
elements), `w:tab` *characters* inside runs (`<w:r><w:tab/></w:r>` — a different element with the same name),
tab stops inherited from `w:styles`, `w:suppressAutoHyphens`, `w:keepLines`, sections/page setup, actual
pagination. Sheet: active-tab *navigation* semantics, `tabColor` with theme/indexed references, multi-window
views (`workbookView` in `<customWorkbookViews>`), `pane` state beyond the split, Android UI or provider
behaviour. None of these were implemented, and none stands in for the others.

## B. Outcome space

* **H+ (achievable here).** Tab stops, `pageBreakBefore`, `widowControl`, `tabColor`, `zoomScale`,
  `showSheetTabs`, `firstSheet` are all modelled natively by both independent readers, and the legacy bytes
  are unchanged. *Observed: supported.* The strongest datum is python-docx refusing a `w:tab` without
  `w:val`: our writer's stricter emission is required by a real third-party consumer, not a self-imposed
  style rule.
* **H− (not achievable here).** Whether Word puts the insertion point at 720 twips, whether a declared page
  break lands at the top of a page, whether widow/orphan control actually suppresses a lone line, what Excel
  paints for `FFABCDEF`, whether a zoomed sheet scrolls identically, and whether a hidden-tab workbook
  triggers repair. *Observed: true; stated as a limit.* No word processor, spreadsheet application or device
  exists in this environment.
* **H∂ (boundary-conditional).** Three boundaries moved the answer this round. (1) `w:val` on `w:tab`:
  optional in CT_TabStop, mandatory in practice — the schema and the consumer disagree, and the consumer
  wins. (2) `w:tab` `w:val="clear"`/`"bar"`: modelled, because `ST_TabJc` allows them, but `clear` stops have
  no observable effect in the emitted file beyond their presence. (3) `w:widowControl` **defaults to on when
  never specified**, so "off" must be written explicitly; `w:pageBreakBefore` defaults to not-applied and
  *supersedes* `w:keepNext`.

**Gate set, fixed before this commit** (R84's set carried forward, with a R86-specific control gate):
(1) R60 128-case, R62 128-byte-identical and R61 64/64 baselines unchanged; (2) `kotlinc -Werror` execution of
both probes; (3) python-docx **and** openpyxl reading the real artifacts, plus raw OPC; (4) 57 negative
controls, each written against a real artifact, refusing every reverted guard; (5) full host suite with zero
failures/errors; (6) census and source audit reproducible; (7) this document naming what was not run. All
seven were met before the commit; the defects in §G were found and repaired before it.

## C. Frontier, restated with the hypotheses it imports

* `w:tabs` is the container (§17.3.1.38) and `w:tab` is `CT_TabStop` (§A.1); the Microsoft SDK's own metadata
  records the parent as *"tabs (§17.3.1.38)"* — [TabStop class](https://learn.microsoft.com/en-us/dotnet/api/documentformat.openxml.wordprocessing.tabstop).
  Hypotheses imported: a tab stop is *measured from the leading edge of the paragraph*, is *"always measured
  relative to the leading edge"*, and is a sibling of the other `w:pPr` children, so its position in the
  sequence is part of the contract.
* Attribute table used for the writer, from [ooxml.org `w:tabs`](https://ooxml.org/wordml/w-tabs/): `w:pos`
  (twips), `w:val` (`left`, `center`, `right`, `decimal`, …), `w:leader` (`none` default, `dot`, `hyphen`,
  `underscore`, `heavy`, plus the `middleDot` variant that appears in `ST_TabTblLeader`). Two of that page's
  claims are **not** relied on and are recorded as unverified: its section number (§17.3.1.34 — Microsoft's
  metadata says §17.3.1.38 for `tabs`) and its statement that "up to 63 stops can be defined per paragraph".
  Our cap of 64 is a defensive bound of our own, not a claim about Word's limit.
* `w:pageBreakBefore`: `CT_OnOff`, parent `w:pPr`; the paragraph starts a new page and the property
  **supersedes any use of `keepNext`**; omitted ⇒ style hierarchy, never specified ⇒ not applied
  ([pageBreakBefore](https://learn.microsoft.com/en-us/dotnet/api/documentformat.openxml.wordprocessing.pagebreakbefore)).
* `w:widowControl`: `CT_OnOff`, parent `w:pPr`; prevents a lone first/last line; omitted ⇒ style hierarchy,
  **never specified ⇒ on by default** — which is why `false` is written as `w:val="0"`
  ([widowControl](https://learn.microsoft.com/en-us/dotnet/api/documentformat.openxml.wordprocessing.widowcontrol)).
* **Executed locally**: python-docx 1.2.0 raises `InvalidXmlError` for `<w:tab w:pos="720"/>` without
  `w:val`, while its `TabStops` reads `left/center/right/decimal/bar/clear` and maps `leader=None` and
  `leader="none"` to the same `SPACES` value. That is a third-party confirmation of both the mandatory-`val`
  behaviour and the decision to omit `w:leader` rather than write `w:leader="none"`.
* Spreadsheet: `CT_BookView` carries `showSheetTabs` (default true), `firstSheet` (default 0, the leftmost
  tab) and `activeTab`; `CT_SheetView` carries `zoomScale` as a percentage. openpyxl round-trips
  `wb.views[0].showSheetTabs/firstSheet` and `ws.sheet_view.zoomScale`, and `ws.sheet_properties.tabColor`
  reports the `FF`-prefixed ARGB string. **Hypothesis imported and not verified**: that Excel repairs a
  workbook whose `firstSheet` names a hidden sheet. No spreadsheet application ran here; the refusal is a
  defensive rule, not an observed repair. Section numbers for `CT_BookView`/`CT_SheetView` are quoted from
  knowledge, not from a fetched page, exactly as in R84.

**Open interface.** Everything in H−. This is why no Class A claim is made (§J).

## D. Negative space — 18 shapes that look like a solution and are not

| # | Shortcut | Why it fails the contract | Detector (real, run) |
|---|---|---|---|
| 1 | `<w:tab>` written directly under `w:pPr`, without `<w:tabs>` | `w:tab` is only valid inside `w:tabs`; ordering tests miss it, schema does not | `test_non_tab_child_is_rejected` |
| 2 | Omit `w:val` for `LEFT` because "left is the default" | python-docx refuses the file outright | `test_missing_val_is_rejected`, `test_python_docx_refuses_a_tab_without_val` |
| 3 | Write `w:leader="none"` "for completeness" | same meaning as omitting; would make every paragraph differ from a Word-authored one | `test_undeclared_tab_attribute_is_rejected` |
| 4 | Emit stops in declaration order without sorting | Word requires ascending positions; a descending list is not a tab set | `test_descending_intent_is_rejected`, `test_out_of_range_intent_is_rejected` |
| 5 | Accept any `w:pos` and rely on Word to clamp | a negative or >22 in stop is not a legal twips value here | control #4 plus the constructor `require` |
| 6 | Insert `<w:tabs>` after `<w:spacing>` | CT_PPr is a sequence; Word drops or repairs the property | `test_tabs_after_the_spacing_are_rejected` |
| 7 | Insert `w:pageBreakBefore` after `w:widowControl` | same sequence rule, different pair | `test_page_break_after_widow_is_rejected` |
| 8 | Write `w:widowControl` bare to mean "off" | bare means **on**; disabling needs `w:val="0"` | `test_widow_off_written_bare_is_rejected` |
| 9 | Write `w:val="0"` for `true` on `pageBreakBefore` | inverts the document's meaning while looking "explicit" | `test_explicit_on_written_as_zero_is_rejected` |
| 10 | Emit `w:pageBreakBefore` on every heading "to be safe" | adds page breaks nobody asked for; the unchanged-bytes gate fails | `test_invented_page_break_is_rejected` |
| 11 | Support tabs on list items / table cells "for uniformity" | list level indentation belongs to `w:numbering`; cell paragraphs are a different layout context | `test_tabs_on_a_list_item_are_rejected`, `test_tabs_inside_a_table_cell_are_rejected` |
| 12 | Put `<sheetPr>` after `<sheetViews>` | CT_Worksheet sequence; Excel repairs | `test_sheet_pr_out_of_place_is_rejected` |
| 13 | Write `rgb="ABCDEF"` without the alpha byte | `ST_UnsignedIntHex` here is 8 hex digits; 6-digit colour is a different attribute form | `test_colour_without_the_alpha_prefix_is_rejected` |
| 14 | Guess a colour for `theme=`/`indexed=` tabs on import | invents a value the file did not state | `test_changed_colour_is_rejected` plus the reader's `takeIf` |
| 15 | Emit a second `<sheetViews>` for zoom | one sheet has one view set here; the frozen pane would be lost | `test_removed_sheet_views_are_rejected`, `test_removed_pane_is_rejected` |
| 16 | Write `<bookViews>` unconditionally | changes every legacy workbook's bytes; R61's 64/64 baseline fails | `test_invented_book_views_are_rejected` |
| 17 | Invent `activeTab` when no sheet is hidden | contradicts R84's rule and adds navigation state nobody asked for | `test_invented_active_tab_is_rejected` |
| 18 | Accept a hidden `firstSheet` and "let Excel fix it" | writes a workbook that our own reader drops on import | `test_wrong_first_sheet_is_rejected`, `test_invented_first_sheet_is_rejected` |

Two further refusals live in the *checkers* rather than the writers, because they are properties of the
artifact: `test_undeclared_package_is_rejected` (a package with no TSV row) and
`test_truncated_intent_row_is_rejected` (a TSV row missing a field), in both lanes.

## E. Mechanism portfolio — six branches, each with a falsifier

1. **Container vs bare element.** `<w:tabs>` + N `<w:tab>` children (chosen) versus `<w:tab>` under `w:pPr`.
   Falsified by a raw-XML control that strips the container (branch 1 of §D).
2. **Always-`w:val` vs omit-when-left.** Chosen: always. Falsified by a real consumer: python-docx raises on
   the omit-when-left form (`test_python_docx_refuses_a_tab_without_val`).
3. **Leader omitted vs `w:leader="none"`.** Chosen: omit. Falsifier: the leader sweep must emit exactly five
   attributes for five leaders and none for `NONE`; any `"none"` attribute fails
   `test_undeclared_tab_attribute_is_rejected`.
4. **One `<sheetView>` vs two.** Chosen: one element carrying `zoomScale` **and** `<pane>`. Falsifier: the
   `zoom` fixture with `freezeRows = 1` and `zoomScale = 75` — a second element would leave the pane
   unreachable and openpyxl would report `zoomScale` on a different view (`test_removed_pane_is_rejected`).
5. **`<bookViews>` on demand vs always.** Chosen: on demand. Falsifier: R61's 64/64 byte baseline and the
   R84 visibility lane, both regenerated after the change (§H).
6. **Constructor-refused `firstSheet` vs reader-dropped `firstSheet`.** Chosen: both, asymmetrically — the
   writer refuses a hidden `firstSheet` (a file we would not accept back), the reader drops it (the sheets
   are still readable). Falsifier: `test_wrong_first_sheet_is_rejected` for the write side; the
   `first-sheet` round-trip fixture for the read side.

Each branch was re-run after the final source edit; the numbers in §H are post-repair.

## F. Artifact requirement

Real files, not mocks: `testing/build/r86writer/artifacts/` holds 11 `.docx` packages plus `tabs.tsv`
(`default`, `heading-tabs`, `tabs-basic`, `tabs-aligns`, `tabs-leaders`, `layout-tabs`, `break-before`,
`widow`, `mixed`, `ordered`, `boundary`); `testing/build/r86sheet/artifacts/` holds 7 `.xlsx` packages plus a
10-field `views.tsv`. Every assertion in §D and §H is made against those bytes or against a **copy** of them
that a control has deliberately broken. Reproduce with `python3 tools/run_r86_writer_tabs.py` and
`python3 tools/run_r86_sheet_views.py` (compile → probe → verify, ~40 s each on this host).

## G. Second-order adversary, per candidate

* **The probe is the only witness.** Attack: make the intent table agree with the writer by construction, so
  the checker proves nothing. Mitigation: the probe asserts literal XML substrings (not model values) before
  writing the TSV, and the checkers re-derive every row from the bytes with python-docx/openpyxl. Residual
  risk: both sides share one model; a *consistently* wrong model (e.g. wrong unit) would pass. The only
  external anchor is the reader's own field values, which is why the readers are run unconditionally, not
  optionally.
* **A checker that cannot fail.** Attack found in this round: `require(children and …)` — a truthiness test
  that could never be false, kept as "the first child must be `sheetPr`". Repaired to
  `require(children, …)` and the real ordering assertion is made separately, against the raw part. This is
  the class of bug that makes a green lane meaningless, and it was in the *checker*, not the code.
* **A checker that passes for the wrong reason.** Attack: point the R86 checker at another round's artifacts.
  Result: exit 1 with `missing intent table …/tabs.tsv` and `…/views.tsv` — it cannot pass by accident.
* **Post-green tampering.** Three attacks on copies of the *real* artifacts, run after every lane was green:
  deleting one `<w:tab>` ⇒ `tabs-basic.docx: paragraph 0 has 0 w:tab, declared 1`; changing one leader on the
  wire ⇒ `tabs-leaders.docx: paragraph 0 w:tab w:leader 'heavy' != declared 'dot'`; changing `zoomScale`
  150→125 ⇒ `zoom.xlsx: Wide: zoomScale '125' != 150`. All three exit non-zero.
* **Regression lane that lies.** R60's baseline was red at the start of this session and would have been read
  as "R86 broke the writer". Cause: the baseline runner needs `run_r60_writer.py` to have produced
  `doc-000-default.docx` first; run in order, all 128 cases are byte-identical to the pin. Recorded so the
  next round does not repeat the diagnosis.
* **Census churn.** My first census refresh rewrote ~983 unrelated lines because rebuilding the hash map
  changed key order. Repaired by refreshing values in place and preserving insertion order: the diff is now
  21 insertions / 5 deletions, and the MD is byte-identical except for the new sentence.

## H. Verification — what actually ran

| Lane | Command | Result |
|---|---|---|
| Writer probe | `tools/run_r86_writer_tabs.py` | `R86_WRITER_TABS_OK assertions=56 documents=11` |
| Writer checker | `tools/check_r86_writer_tabs.py … --require-python-docx` | `11 packages and 21 body paragraphs verified; no Word pagination or rendering` |
| Sheet probe | `tools/run_r86_sheet_views.py` | `R86_SHEET_VIEWS_OK assertions=42 workbooks=7` |
| Sheet checker | `tools/check_r86_sheet_views.py … --require-openpyxl` | `7 workbooks and 15 sheets verified; no Excel tab strip, navigation or repair evidence` |
| Negative controls | `python3 -m unittest discover -s tools -p 'test_*.py'` | **Ran 1945 tests — OK (skipped=12)** |
| Census | `tools/module_testability.py --check` | `50/50 = 100.00 %` substantial-testable, `0/50 = 0.00 %` fully accepted |
| Source audit | `tools/native_source_audit.py --check` | 231 recorded requirements, 36 cross-cutting obligations |

Regression sweep, every lane re-run **after** the source edits (all exit 0):

`r84_writer_indent`, `r84_sheet_visibility`, `r83_writer_spacing`, `r83_sheet_hidden`, `r82_sheet_align`,
`r82_writer_align` (8 packages / 20 paragraphs), `r81_writer_tables` (9 / 10 tables), `r81_sheet_rows`
(6 workbooks / 7 sheets), `r76_writer_layout` (9 packages), `r76_sheet_formats`, `r74_writer_links`
(20 checks), `r75_writer_images` (ImageMagick decoded the media), `r75_sheet_fractions` (136 observations),
`r77_sheet_conditions` (60 observations / 20 formats), `r60_writer` (675 assertions / 256 files),
`r60_writer_baseline` (**128 cases byte-identical**, pin `ab4840b2…`), `r60_sheet` (6588 assertions /
64 files), `r61_sheet_baseline` (**64/64**, pin `86b39d90…`), `r62_writer` (1434 assertions / 256 files),
`r62_writer_baseline` (**128 byte-identical**, pin `e89d1a21…`), `r45_contracts` (508 assertions / 33
kernels), `r47_contracts` (2393 assertions / 61 kernels), plus the independent reference checkers
(`5377/256`, `8198/256`, `1089/64` predicates).

### CI observation (appended after the push; this is the observation, not a local claim)

Workflow `R54 office authoring`, run 43680476301 on head `04ece7d` (PR #20):

* step 9 **Compile Android and run actual JVM contracts — success**. That invocation now carries
  `:testing:r86WriterTabContracts :testing:r86SheetViewContracts` alongside the other forty tasks, so both new
  Gradle source sets compiled on the runner and both probes executed there.
* step 14 **Independent document readers and formula comparison — success**. The two R86 checkers and the two
  R86 control modules are inside that step's command list, so they ran green on the runner as well.
* steps 19 `Run real API34 UI and provider checks` and 21 `Require all 37 named Office cases without duplicate
  or skip` **failed**. The same two steps fail on `main` at `01ee6ce` (run 43650752625, job 132618449551) and
  at `ffcd4de` (run 43638894847), so this is a pre-existing managed-emulator failure, not a consequence of
  this change. The job's log text could not be downloaded — GitHub redirects it to blob storage, which is
  outside this sandbox's egress allowlist — so the comparison above is by step conclusion, not by reading the
  failure messages.

**Not run, and why:** Gradle (no Android SDK in this environment — CI compiles the two new source sets),
Word/Excel/LibreOffice (not installed), any device or emulator, any visual inspection of the emitted files.
The evidence is therefore structural and reader-based, never rendered.

## I. Regressions versus the best prior codebase (R84, `01ee6ce`)

**None observed.** All 23 lanes above are green and the three byte baselines are unchanged, which is the
strongest available statement: for documents that declare none of the new fields, the packages are
bit-identical to R84's. Three lanes were initially red and each had an environmental cause that predates this
round: the repository was a shallow clone (`git show … exit 128` — fixed once with `git fetch --unshallow`),
`run_r82_writer_align.py` spawns its verifier with a sanitised environment and needs
`PYTHONPATH=$HOME/.cache/fusion-r76-readers:…`, and `run_r60_writer_baseline.py` needs `run_r60_writer.py`
to have run first. No production code was changed to make any of them pass.

## J. Class A, merge, stopping, and the next test

**Class A: not claimed, for either application.** The requirement is an *application-level observer* — Word
opening the file without repair, Excel painting the tab colour, a device rendering pagination. None exists
here. What this round establishes is narrower and honest: the bytes match the specification as read by two
independent implementations, and every legacy byte is unchanged.

**Percentages (the same standard as previous rounds).** 50/50 modules (100.00 %) carry a substantial
executable testable subset; **0/50 (0.00 %)** are fully acceptance-tested. The census total is unchanged by
this round because `:format:ooxml` was already counted; what changed is the depth of that row's evidence
(8 new paths, 258 total) and one new behavior sentence.

**Merge.** Per the R86 instruction this round opens a PR and does **not** merge.

**Stopping point and next highest-information test.** The highest-information next step is *not* another
property: it is a real consumer. Concretely, in order of information per unit of effort: (1) open the 11
packages with LibreOffice headless (`--convert-to pdf`) and diff the page geometry against the declared
stops — the first check that would observe pagination rather than bytes; (2) `dotnet` + DocumentFormat.OpenXml
`OpenXmlValidator` on the same files — a *schema* validator, which would settle the `w:tab`/`w:val` and
CT_PPr-order questions against a third implementation that is not python-docx; (3) an Excel-authored fixture
with `firstSheet`/`tabColor` round-tripped through `XlsxStyledBook.read/write`, the only way to test the
repair hypothesis in §C. None of the three can run in this environment, and none will be simulated.

### The R85 video-enhancement request — not built, and why

The standing request (add hardware-accelerated upscaling/frame-rate/colour enhancement to the video player
and browser, and "remove mosaic") was **not** implemented this round, and the honest reason is not time:

* There is no video player or browser renderer in this repository to attach a pipeline to. The
  `feature/media` module has no player surface, no codec path and no SurfaceFlinger/GLES integration; a shader
  module with no caller is exactly the "toy module" the working rules forbid.
* "Hardware acceleration" cannot be claimed without a device: no GPU, no GLES driver, no Android SDK here.
  Anything I wrote would be unexecuted shader text.
* "Remove mosaic" is an information-theoretic impossibility, not a tuning problem: destroyed detail cannot be
  recovered, and a plausible-looking result would be invention, which is precisely what the rules prohibit.
  The achievable part — perceptual upscale (Lanczos/bicubic + contrast-adaptive sharpening + debanding +
  chroma reconstruction), a bounded frame-blend, and tone/colour mapping — is real but *perceptual only*, and
  it needs the player that does not exist.
* What I will not do: emit a shader, a `VideoEnhanceMath` kernel nobody calls, or a claim of "enhanced video"
  without a frame that was actually enhanced.
