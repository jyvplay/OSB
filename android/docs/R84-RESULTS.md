# R84 — Writer paragraph indentation + keep flags, and workbook sheet visibility (host-verified)

Branch `arena/4638a58d-osb`, continuing from R83 `ffcd4de` on the merged `main` line. Every number below came
from a command run in this session. This round found **four** defects in its own work, three of them in
production code, all after a lane had already reported OK; §G lists each one with the attack that exposed it.

## A. Formal model

**Admissible objects.** Two constructors in `:format:ooxml` and the packages they emit.

*Writer.* `DocxPackage.Block.Heading(level, runs, alignment, spaceBeforeTwips, spaceAfterTwips,
indentLeftTwips, indentRightTwips, firstLineTwips, keepWithNext, keepTogether)` and the same field set on
`Block.Paragraph`, with

* `indentLeftTwips`, `indentRightTwips`, `firstLineTwips ∈ {null} ∪ −31 680..31 680` twips, and
* `keepWithNext`, `keepTogether ∈ {null, true, false}` — a **tri-state**, because omitting `w:keepNext` does
  not mean "off": ISO/IEC 29500-1 §17.3.1.26 says an omitted element inherits from the style hierarchy, so
  `null` writes nothing, `true` writes the bare element and `false` writes `w:val="0"` to override an
  inherited on.

`firstLineTwips` is **one signed measurement**, not two: non-negative is emitted as `w:firstLine`, negative
as `w:hanging` with the magnitude, because CT_Ind makes those two attributes mutually exclusive. Reading is
`DocxPackage.read`.

*Spreadsheet.* `XlsxStyledBook.StyledSheet(..., visibility: Visibility)` with `Visibility ∈
{VISIBLE, HIDDEN, VERY_HIDDEN}` mapping onto CT_SheetState `visible`/`hidden`/`veryHidden`; `VISIBLE` is the
default and writes **no** `state` attribute, because that is the schema default. `WorkbookSpec` additionally
refuses a sheet list with no visible sheet. Reading is `XlsxStyledBook.read`.

**Information/access model.** Both writers are pure functions of caller-supplied in-memory data. The
verifiers never import Kotlin: they receive emitted bytes plus an intent table the probe wrote from the
*fields it declared*. Two of the checks are byte-level tamper tests on real packages (re-zip one OPC part,
re-read), so the import-path leniency claims are about bytes, not about a mock.

**Resource counted.** Emitted property content that can change layout or navigation: `w:ind` (presence,
`w:left`/`w:right`, and which of `w:firstLine`/`w:hanging` carries the measurement), the keep elements and
their `w:val`, their positions among `w:pPr` children; and, per workbook, the `<sheet>` elements with
`name`/`sheetId`/`r:id`/`state` plus the `<bookViews>` active-tab index. Not counted: how many lines actually
fit on a page, where Word breaks a kept pair, whether Excel paints the tab strip, or how a repair prompt
looks.

**Success and failure, with explicit quantifier order.**
For the writer: **for every** document whose blocks declare no indent and no keep flag, no `w:ind`,
`w:keepNext` or `w:keepLines` is emitted and the bytes are exactly the pre-R84 bytes (proved by the R60 and
R62 byte baselines); **for every** declared indent, that paragraph's `w:pPr` contains exactly one `w:ind`
whose attribute set is exactly the declared subset, with `firstLine`-or-`hanging` chosen by sign and never
both; **for every** declared keep value, the element is present with `w:val="0"` exactly when the value is
`false`; **for every** paragraph, the children of `w:pPr` appear in CT_PPr order (`keepNext`, `keepLines`,
`spacing`, `ind`, `jc` in the orders declared); **and for every** list item, quote, code block or table cell,
none of these elements appears. Round trip: `read(write(doc)).blocks == doc.blocks` for every fixture.

For the spreadsheet: **for every** workbook, the `<sheet>` elements appear in declared order with the
declared names, one-based `sheetId`, `rSheet{index}` relationship and `state` present **iff** the sheet is
not visible, carrying exactly the declared token; **for every** workbook with at least one non-visible sheet,
`<bookViews><workbookView activeTab="k"/></bookViews>` is present with `k` = index of the first visible
sheet, and **for every** workbook with no non-visible sheet, no `<bookViews>` exists at all; **for every**
declared list, at least one sheet is visible, otherwise the constructor refuses. Round trip:
`read(write(book)).sheets` equals `book.sheets`, including `visibility`, and a foreign `state="visible"` reads
as `VISIBLE`.

**Parameters, boundaries, units, tolerances.** Twips: 1440 = 1 inch, 20 = 1 pt. Indents are accepted in
−31 680..31 680 (Word's own 22-inch measure used elsewhere in this file) and refused outside with
`IllegalArgumentException`; `±31 680` are accepted and `±31 681` refused, both directions, on both
`Block` types. Keep flags are `null`/`true`/`false` with no numeric parameter. Sheet tokens are exact strings.
No numeric tolerance is involved anywhere in this round.

**Adjacent problems that must not be substituted.** Writer: character-width indentation (`w:leftChars`,
`w:rightChars`, `w:firstLineChars`, `w:hangingChars`, which the schema lets *override* the twip attributes),
tab stops, list-level indentation inside `w:numbering`, `w:pageBreakBefore`, `w:widowControl`, styles-level
`w:pPr`, actual pagination. Sheet: row/column hiding (R83), grouping/`outlineLevel`, `zeroHeight`, print
areas, tab colours, `firstSheet`/`showSheetTabs` in bookViews, workbook protection, Android UI or provider
behaviour.

## B. Outcome space

* **H+ (achievable here).** Both features are additive, verified by an independent reader that natively
  exposes them (python-docx `left_indent`/`right_indent`/`first_line_indent` and its **tri-state**
  `keep_with_next`/`keep_together`; openpyxl `sheet_state`), with legacy bytes unchanged. *Observed:
  supported.* The strongest single datum is that python-docx distinguishes `None` from `False` for a keep
  flag, so the tri-state model is not self-referential.
* **H− (not achievable here).** Layout and navigation: whether a hanging indent wraps where Word wraps, how
  a chain of `keepNext` paragraphs is broken when the group exceeds a page, whether Excel shows or hides the
  tab strip, and whether a wrong `activeTab` produces a repair prompt. *Observed: true; stated as a limit.*
* **H∂ (boundary-conditional).** The answer changes at three boundaries this round touched: the sign of the
  first-line measurement (two different attributes), `null` versus `false` for a keep flag (inherit versus
  override), and "no visible sheet left" (refused, per Excel's own behaviour).

**Commit threshold declared before the production edit this round:** (1) R60's 128-case baseline, R62's 128
byte-identical baseline and R61's 64/64 XLSX baseline unchanged; (2) `kotlinc -Werror` execution of both
probes; (3) python-docx **and** openpyxl reading the real artifacts; (4) negative controls refusing every
reverted guard, each written against a real artifact; (5) full host suite with zero failures/errors; (6)
census and source audit reproducible; (7) this document naming what was not run. All seven were met before
the commit; defects found after the first green run (§G) were fixed and every affected gate was re-run
before the commit, and the numbers quoted here are the post-repair ones.

## C. Frontier (restated, with its hypotheses)

* `w:ind` is ECMA-376 Part 1 §17.3.1.12. `w:left`/`w:right` are `ST_SignedTwipsMeasure` (may be negative);
  `w:firstLine`/`w:hanging` are non-negative `ST_TwipsMeasure`; the two are **mutually exclusive**, and *"if
  both `w:firstLine` and `w:hanging` are present, the ECMA-376 standard specifies that `w:firstLine` takes
  precedence"*; `*Chars` variants *"take precedence over distance-based attributes"* —
  [`w:ind` reference](https://ooxml.org/wordml/w-ind/). Hypotheses imported: one signed field maps to two
  attributes; a both-present foreign file must be read with `firstLine` winning (my first implementation had
  this backwards, §G); character-width indents are out of scope and are therefore a known import gap.
* `w:keepNext` is `OnOffType` in `w:pPr` (§17.3.1.26) and its ISO text says it *"specifies that when
  rendering this document in a paginated view, the contents of this paragraph are at least partly rendered on
  the same page as the following paragraph whenever possible"*, that chained paragraphs are grouped, that a
  group exceeding a page starts a new page, and that **if the element is omitted its value comes from the
  style hierarchy** — [Microsoft `KeepNext` class](https://learn.microsoft.com/en-us/dotnet/api/documentformat.openxml.wordprocessing.keepnext).
  Hypotheses imported: omission ≠ false (hence the tri-state), and the element is a *request* whose success
  depends on pagination.
* CT_PPr is a **sequence** (`pStyle → keepNext → keepLines → … → numPr → … → spacing → ind → … → jc`), so the
  insertion point is part of the contract — reported independently by a
  [Euro-Office fix that lost `keepNext` when alignment moved](https://github.com/Euro-Office/core/pull/246)
  and a [writer that inserts CT_PPrBase children at their schema positions](https://github.com/UseJunior/safe-docx/pull/1294).
* CT_SheetState is `visible|hidden|veryHidden`, and a workbook must keep a visible sheet. **Executed
  locally**: `openpyxl`'s own writer refuses with `ValueError: The only worksheet of a workbook cannot be
  hidden`, and it emits `state="visible"` explicitly on every sheet — a third-party confirmation of both the
  refusal predicate and the fact that an explicit `visible` is legal on import (my reader accepts it). The
  `hidden` attribute on `x:row`/`x:col` used by R83 is a *different* attribute on a different element.

**Open interface.** Whether Word paginates these indents and keep-chains as declared, and whether Excel
opens a workbook whose active tab was moved to the first visible sheet without a repair prompt. No word
processor or spreadsheet application exists in this environment, which is exactly why Class A is not claimed.

## D. Negative space (18 shapes that look like a solution and are not)

| # | Shortcut | Why it fails the contract | Detector |
|---|---|---|---|
| 1 | Emit `w:ind` after `w:jc` | CT_PPr is a sequence | `test_indent_after_the_alignment_is_rejected` |
| 2 | Emit `w:ind` before `w:spacing` | Same rule, from the inside | `test_indent_before_the_spacing_is_rejected` |
| 3 | Always emit `w:ind` (`left="0"`) | Rewrites every legacy document | R62 128 byte-identical + R60 pinned hash |
| 4 | Emit `w:firstLine` and `w:hanging` together for one measurement | CT_Ind forbids both; readers disagree on which wins | write-side: `test_both_first_line_and_hanging_are_rejected`; read-side: probe asserts `firstLine` wins |
| 5 | Write a hanging indent as a negative `w:firstLine` | `ST_TwipsMeasure` is non-negative; Word shows no hanging | probe asserts `w:hanging="240"` and `w:firstLine` absent |
| 6 | Write `w:start`/`w:end` instead of `w:left`/`w:right` | Out of the transitional schema this writer targets | `test_alias_attribute_is_rejected` |
| 7 | Treat `firstLineTwips = 0` as "unset" | Explicit zero differs from inheriting a style indent | probe: `w:left="0" w:right="0" w:firstLine="0"` emitted *and* read back |
| 8 | Clip an out-of-range indent instead of refusing | Silently a different document | probe `fails(...)` at ±31 681 + intent-range rule |
| 9 | Emit `w:keepNext` for `false` by omitting it | Omitting *inherits* the style hierarchy — the opposite of an override | `test_explicit_off_written_as_a_bare_element_is_rejected` |
| 10 | Emit `w:keepNext w:val="0"` for `true` | Invertebrate on/off | `test_explicit_on_written_as_zero_is_rejected` |
| 11 | Put keep flags after `w:numPr` | Sequence order | raw-OPC `PROPERTY_ORDER` check |
| 12 | Put indents/keep flags on list items, quotes, code blocks | Outside the declared model | `test_keep_flag_on_a_list_item_is_rejected`, `only headings and paragraphs` |
| 13 | Put `w:ind` on table-cell paragraphs | Cell properties are not this feature | `test_indent_inside_a_table_cell_is_rejected` |
| 14 | Trust python-docx alone for ordering | It reports values, not child order | raw-OPC ordering assertions |
| 15 | Trust raw XML alone for tri-state | Would not show that a real reader sees `False` | python-docx `keep_with_next is False` |
| 16 | Write `state="visible"` on every sheet | Changes every legacy workbook's bytes | exact `<sheet …/>` string equality in the probe + `must not` rule |
| 17 | Hide sheets without `bookViews` | Default active tab is sheet 0; a hidden active tab is repaired on open | `test_missing_book_views_is_rejected` |
| 18 | Point `activeTab` at a hidden sheet | Same failure with the attribute present | `test_wrong_active_tab_is_rejected`, `test_hidden_first_sheet_with_its_own_active_tab_is_rejected` |

**Modal shortcut, expanded: #9.** The tempting implementation is `fun appendKeepFlags(keepNext: Boolean,
keepLines: Boolean) { if (keepNext) append("<w:keepNext/>") … }` — a boolean field, a bare element, one line.
It is wrong in the direction that is hardest to see: "not declared" and "declared off" become the same
output, so a paragraph that must *override* an inherited `keepNext` from its style silently inherits it
instead, and there is no way for a caller to express the override at all. It is detected three ways: the
model type is now `Boolean?` so `false` has to be written as `w:val="0"`, the verifier requires `w:val="0"`
exactly when the intent says `false`, and python-docx reports `False` (not `None`) only for that spelling. The
same shape appears on the sheet side as "hide a sheet by omitting it from `<sheets>`", which the name/order
and `sheetId` rules refuse.

## E. Mechanism portfolio (eight distinct)

| # | Mechanism | Construction | Artifact | Proved portion | Unresolved interface | Cheapest falsifier | Gap |
|---|---|---|---|---|---|---|---|
| M1 | **Chosen (writer):** signed first-line + optional left/right + tri-state keep flags on two block types | `appendIndent`/`appendKeepFlags` at their CT_PPr positions | `DocxPackage.kt` (+101/−5) | attributes, sign mapping, order, tri-state, refusals, round trip | pagination, keep-chain breaking | R60/R62 baselines + order/tri-state controls | local |
| M2 | Writer: character-width indents (`w:leftChars` …) | one more attribute family | not implemented | nothing | font-relative measurement, and it *overrides* twips | import-gap note in §A/§J | different feature, non-substitutable |
| M3 | Writer: indentation through a named paragraph style in `word/styles.xml` | new part + style records | rejected (would need a styles part) | nothing | style resolution across consumers | verifier forbids unknown `pPr` children | different feature |
| M4 | Writer: `w:contextualSpacing` to fake indentation | Reuses a spacing element | rejected in design | nothing | wrong semantic entirely | contract has no such element | different problem |
| M5 | **Chosen (sheet):** `state` on the `<sheet>` element + `bookViews` active tab | enum field, conditional emission, workbook-level invariant | `XlsxStyledBook.kt` (+38/−4) | states, ordering, ids, refusal, activeTab, round trip | tab strip, repair prompts | openpyxl `sheet_state` + raw XML + controls | local |
| M6 | Sheet: hide via `zeroHeight`/`width="0"` | renders close to hidden in some viewers | rejected | nothing | not visibility; loses width data | state-attribute rule | **different mechanism, lossy** |
| M7 | Sheet: `sheetId` gaps or omitting the sheet | looks like "not there" | rejected | nothing | corrupt workbook | name/order/sheetId equality | destroys structure |
| M8 | Sheet: `veryHidden` for everything then one visible | legal but a UX trap | allowed only with ≥1 visible; covered by fixture `very-hidden`/`first-hidden` | states, activeTab | Excel's own "unhide" UI | refusal predicate | local, accepted with documentation |

## F. Artifact requirement

* `testing/build/r84writer/artifacts/` — 12 real `.docx` + `indent.tsv` (19 body paragraphs): `default`
  (nothing declared), `indent-left` (720), `indent-right` (360), `hanging` (−240 → `w:hanging="240"`),
  `first-line` (480), `combined` (720/360/−240 + both keep flags), `heading-keep` (heading with both flags),
  `full` (keep + spacing + ind + centre alignment), `keep-off` (explicit `w:val="0"` on a paragraph and on a
  heading), `zero` (explicit zeros), `mixed` (heading with 0.25 in left indent and `keepNext`, a right-aligned body
  with 0.5 in left and 12 pt first line, plus list, quote, code and a 2×2400-twip table), `layout-indent` (indents + A4 section + header/footer + hyperlink).
* `testing/build/r84sheet/artifacts/` — 6 real `.xlsx` + `visibility.tsv` (13 sheets): `default` (all
  visible, no `state`, no `bookViews`), `hidden`, `very-hidden`, `mixed` (visible/hidden/veryHidden in one
  workbook), `first-hidden` (the first sheet hidden → `activeTab="1"`), `styled` (hidden sheet beside freeze
  panes, widths, hidden rows and a defined name).
* Tools: `run_r84_writer_indent.py`, `check_r84_writer_indent.py`, `test_r84_writer_indent.py` (**21**
  controls: 20 refusals + 1 acceptance); `run_r84_sheet_visibility.py`, `check_r84_sheet_visibility.py`,
  `test_r84_sheet_visibility.py` (**19** controls: 18 refusals + 1 acceptance).
* Reproducible experiment: the intersection of `keepWithNext = false` with python-docx's tri-state was
  measured by constructing a document with `w:keepNext w:val="0"` and observing `keep_with_next is False`
  while `keep_together is None`.
* **Not artifacts of this round:** any rendered page, screenshot, tab strip, Android frame or device log.

## G. Second-order adversary, per candidate

**A(writer indentation + keep flags).** Structure: one insertion point between an existing prefix and two
existing optional elements, one signed value that maps to two schema attributes, and two tri-state flags.
1. *Position drift* — after `jc`, or before `spacing`. Caught by the CT_PPr order list plus the explicit
   pairwise assertions.
2. *Sign collapse* — writing `w:firstLine="-240"` instead of `w:hanging="240"`. Caught by attribute equality
   per sign branch, and by python-docx reporting `-152400` EMU for the hanging case.
3. *Double measurement* — both attributes for one value. Caught on write by the verifier, and on read by a
   tamper test: a package patched to carry both must import the `w:firstLine` value (**ECMA-376 precedence**).
   *This attack found a real defect*: my first reader preferred `w:hanging`.
4. *Tri-state collapse* — `false` written as omission. Caught in both directions (§D #9/#10). *This attack
   produced a production change*: the fields became `Boolean?` after the ISO text showed omission inherits.
5. *Scope creep* — indents on list items or in table cells. Caught by the per-paragraph `kind` column.
6. *Refusal erosion* — clipping at ±31 680. Caught by `fails(...)` at ±31 681 and by the intent-range rule.
7. *Reader-only success* — a value python-docx reads but that is out of sequence. Caught because the raw and
   reader passes are independent and both required.
8. *Alias confusion* — a foreign file using `w:start`/`w:end`. Handled deliberately (accepted as `left`/
   `right`) and tested, rather than silently dropped.

**A(sheet visibility).** Structure: a workbook-level element (`<sheet>`) plus a workbook-level sibling
(`<bookViews>`) plus a cross-sheet invariant.
1. *Default drift* — writing `state="visible"`. Caught by exact `<sheet …/>` string equality on the legacy
   fixture and by the "no `bookViews`, no `state`" rules.
2. *Order/id drift* — reordering sheets, or `sheetId`/`r:id` mismatch. Caught by declaration-order equality,
   `sheetId == index + 1` and `r:id == rSheet{index+1}` (controls corrupt each).
3. *Attribute smuggling* — `tabColor`, `outlineLevel`. Caught by the undeclared-attribute rule.
4. *Invariant escape* — a workbook with no visible sheet. Refused in the constructor, and the probe asserts
   the refusal for one hidden sheet, one veryHidden sheet and two hidden sheets.
5. *Active-tab blindness* — hiding the first sheet and leaving the default active tab. Caught by requiring
   `bookViews` with `activeTab` = first visible index. *This attack produced a production change* (§G.6).
6. *Repair-prompt blindness (the real one)* — before that change, `hidden.xlsx` with the first sheet hidden
   loaded in openpyxl with `wb.active` = the **hidden** sheet, i.e. exactly the file Excel would repair. The
   fix emits `bookViews` only when something is hidden, so no legacy byte moved; the control then corrupts
   `activeTab` back to `0` and expects refusal.
7. *Data loss* — dropping a hidden sheet's cells. Caught by the per-sheet non-empty cell count, which the
   intent records and a control tampers with.

**Adversarial passes run (9).** (i) legacy-byte attack; (ii) insertion-order attack; (iii) sign/attribute
attack; (iv) both-attributes import attack; (v) tri-state collapse in both directions; (vi) scope attack on
non-paragraph blocks and table cells; (vii) boundary/refusal attack; (viii) workbook-structure attack
(order/ids/attributes); (ix) active-tab attack. Passes (iv), (v) and (ix) each changed production code after
a green run. A fourth, test-side defect (`String.replace(old, new, count)` — a Python idiom that Kotlin does
not have) was caught by `kotlinc` rather than by review, which is the honest sequence.

## H. Verification — what actually ran

One agent, one Linux sandbox, 2 cores: bash, Python 3.11, Node 22/npm, host-only Kotlin 2.2.21 via npm, an
old JRE 17, python-docx 1.2.0, openpyxl 3.1.5, `ssf` 0.2.2, ImageMagick. **Absent:** Android SDK, emulator,
Studio, device, Word, Excel, LibreOffice, pandoc, any theorem prover, any display, any second agent.

Restore first, reported as it was: `git status --porcelain` on this branch printed **zero lines** against
`ffcd4de`, so there was no uncommitted work to restore and none was invented. The clone was shallow
(`git rev-parse --is-shallow-repository` → `true`); `git fetch --unshallow` made it `false` before any
`git log`/`git diff` claim. `npm ci`, `npm run build` (dist/index.html 225.28 kB, gzip 70.00 kB),
`npx tsc --noEmit`, `bootstrap_host_tools.py install` and both reader caches exited 0 on their pins.

| Gate | Result |
|---|---|
| `run_r84_writer_indent.py` | `R84_WRITER_INDENT_OK assertions=51 documents=12`; verifier **12 packages / 19 body paragraphs** via python-docx + raw OPC |
| `test_r84_writer_indent.py` | **21/21** (20 refusals, 1 acceptance) |
| `run_r84_sheet_visibility.py` | `R84_SHEET_VISIBILITY_OK assertions=31 workbooks=6`; verifier **6 workbooks / 13 sheets** via openpyxl + raw OPC |
| `test_r84_sheet_visibility.py` | **19/19** (18 refusals, 1 acceptance) |
| R60 Writer baseline | 128 cases, `ab4840b2…` (unchanged); 5377 predicates / 256 DOCX |
| R62 Writer baseline | **128 byte-identical**, `e89d1a2…` (unchanged); 8198 predicates / 256 files |
| R61 sheet baseline | 64/64 byte-identical XLSX, 128 legacy overload cases (unchanged) |
| R60 sheet | 1089 predicates / 64 XLSX (unchanged) |
| R83 writer / sheet | 30 assertions / 8 packages; 40 assertions / 6 workbooks |
| R82 writer / sheet | 25 / 8 packages; 28 / 6 workbooks |
| R81 writer / sheet | 44 / 9 packages; 46 / 6 workbooks |
| R76 Writer layout; R76 sheet formats | 74 assertions / 9 packages; 239 rows → 231 identical, 8 adjudicated |
| R74 writer / R75 writer / R75 sheet fractions / R77 sheet conditions | 18 assertions; 39 assertions; 136 observations vs an exhaustive oracle; 207 assertions / 60 rows / 8 refusals over 20 formats |
| Prior control suites re-run against regenerated artifacts | **85 tests OK** (R81–R83 writer+sheet) |
| Full `tools` suite | **Ran 1886 tests, OK (skipped=12)** |
| Controls invocation | `PYTHONPATH=tools python3 -m unittest tools.test_r84_writer_indent tools.test_r84_sheet_visibility` → 40 tests OK (the sibling checker import needs `tools` on the path; `discover -s tools` supplies it implicitly) |
| R84's own contribution | 21 + 19 = **40** controls; 1886 − 40 = 1846, which is exactly R83's suite size (arithmetic, not a separate run) |
| Census | **50/50 = 100.00 %** substantial testable subset, **0/50 = 0.00 %** fully accepted |
| Source audit | 231 requirements, 36 obligations, all native packages owned |

**Android.** Nothing Android ran locally. `:testing:r84WriterIndentContracts` and
`:testing:r84SheetVisibilityContracts` are declared in `android/testing/build.gradle.kts` and added to the CI
task list; CI is the only Android executor here, it runs only after a push, and its result is therefore a
separate observation appended below rather than a local claim.

### CI observation (appended after the push; this is the observation, not a local claim)

PR #19, head `16ed908`, workflows `R54 office authoring` (runs `43647795187` push, `43647779593` PR) and
`Android native` (`43647795195`). **Android native: `success`** — traceability, both flavor assemblies and the
JVM tests, which include the two new contract source sets. **Office: `failure`**, with this step breakdown in
the `office` job: steps 1–18 `success` (step 9 is *Compile Android and run actual JVM contracts*, steps 10–17
are the pre-existing ZIP/reader/import-budget lanes), step 19 *Run real API34 UI and provider checks*
`failure`, step 21 *Require all 37 named Office cases without duplicate or skip* `failure`; all **17** per-frame
`Actual frame …` capture jobs `success`.

That failure predates this round and is not attributable to it: run `43638894847` on **`main` at `ffcd4de`**
and run `43638888176` on the R83 branch fail on **exactly the same two steps** (19 and 21). Nothing in R84
touches Android UI, providers or the emulator harness — the diff is one OOXML writer pair plus JVM-only test
source sets. The failure text could not be retrieved from this sandbox: `gh run view --log` returned
`EOF` from `results-receiver.actions.githubusercontent.com` (outside the permitted egress) and artifact
downloads are on the same host, so this section reports the step-level outcome and the pre-existing
signature, and **does not quote an error message it never saw**.

**Visual inspection: not performed, and not substituted.** There is no renderer, no display and no device in
this environment; ImageMagick cannot render DOCX or XLSX, and this repository's desktop page is a static
landing page, not an Office viewer, so building a viewer here would be a different component (and out of
scope for this round by instruction). What a look would have shown — whether 0.5 in of indent appears,
whether a kept heading travelled with its paragraph, whether the tab strip shows two of three sheets — is
exactly what python-docx, openpyxl and a byte walk cannot report.

## I. Regressions versus the best prior codebase (R83, `ffcd4de`)

**None observed.** The two unchanged-caller baselines are the direct evidence that an unset field emits
exactly the R83 bytes: R62's 128 cases are byte-identical with the same hash `e89d1a2…`, R60's 128 cases keep
`ab4840b2…`, and R61's 64/64 XLSX are byte-identical. Every prior lane re-run after the final production
state reproduces its R83 number (R83 30/8 and 40/6, R82 25/8 and 28/6, R81 44/9 and 46/6, R76 74/9 and
239/231/8, R74 18, R75 39 and 136, R77 207/60, R60 sheet 1089/64), and the prior **85** control tests pass
against the freshly regenerated artifacts.

Four defects of this round were found and fixed **before** the commit, and they are listed because the
sequence matters more than the final green: (1) a `String.replace(old, new, count)` call that Kotlin does not
have (compile error, test-side); (2) reading `w:hanging` in preference to `w:firstLine` when a foreign file
carries both, which ECMA-376 resolves the other way; (3) modelling keep flags as `Boolean` when ISO/IEC
29500-1 says an omitted element *inherits*, so `false` had no representation; (4) emitting `state` without
`bookViews`, which leaves the default active tab pointing at a hidden first sheet — a file Excel would
repair. Cost: **+139/−9 lines across two production files** (DocxPackage +101/−5, XlsxStyledBook +38/−4); the
deletions are the outdated CT_PPr order comment, the old guard that closed `w:pPr` on alignment/spacing
alone (now also on indents and keep flags), the two short reader constructors that now receive the indent
and keep arguments, and three sheet-writer lines whose emission moved into helpers that carry `state`,
the `visibility` parameter and the conditional `bookViews`. No
feature, branch or element from an earlier round was removed.

## J. Class A, merge, stopping, and the next test

**Class A is still not claimed for Writer, Spreadsheet or anything else.** This round adds two properties
whose entire value is a *consequence* in an application: an indent is a distance on a page, and a keep flag
is a decision a paginator makes, while a sheet state is a tab the user can or cannot click. python-docx and
openpyxl confirm what the file *declares*; they cannot paginate, wrap, hide a tab or show a repair prompt.
The answer to "can both go Class A this turn?" is **no**, with the missing observers named: a real word
processor, a real spreadsheet application, and hardware.

**Module accounting, in absolute numbers and percentages.** 50 of 50 modules (**100.00 %**) meet this
repository's bounded "substantial executable subset" standard; **0 of 50 (0.00 %)** meet the full
specification. Both unchanged, and that is the correct outcome: R84 added two more *verifiable features*
(paragraph indentation; sheet visibility including the active-tab invariant) plus three import-path
behaviours, not an acceptance. Feature-level testable state grew by two lanes (12 packages, 6 workbooks, 82
assertions, 40 controls), six Python tools and two Gradle source sets.

**Merge.** This turn's work is committed on `arena/4638a58d-osb` as **PR #19** and squash-merged at the end of
the turn, as instructed. The merge is a repository operation, not verification; in particular it does not
turn the office workflow's pre-existing step-19/21 failure (§H) into a pass, and it does not make anything
here Class A.

**Stopping rule reached honestly.** Two production features, two probes, six tools, 40 controls, two Gradle
source sets, a regression sweep of 18 re-run lanes (four of them byte/case baselines) plus three reference
oracles, the census/audit refresh, and nine adversarial passes is one round's budget. Further host-side XML
work would lower the marginal information: the remaining risks are pagination, style inheritance and tab-strip behaviour, none of which any reader available here can touch.

**Next highest-information test** (outside this sandbox): open
`testing/build/r84writer/artifacts/hanging.docx` in Word — is the first line outdented by 12 pt with the body
at 0.5 in, and does the `keep-off.docx` pair show that the explicit `w:val="0"` overrides an inherited keep?
Then open `testing/build/r84sheet/artifacts/first-hidden.xlsx` in Excel — does it open without a repair
prompt, is `Body` the active tab with `Cover` hidden, and does `very-hidden`'s `Deep` sheet still require the
VBA editor to reveal? Those files, opened by the applications that define the format, are exactly the
evidence no reader in this environment can produce.
