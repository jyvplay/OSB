# R82 — Writer paragraph alignment and spreadsheet cell alignment (host-verified)

Branch `arena/95f1a43c-osb`, continuing from R81 `8a68396` on the merged `main` line. Every number
below came from a command in this session; nothing is projected or copied from a prior round.

## A. Formal model

**Admissible objects.** Two constructors in `:format:ooxml` and the packages they emit.

*Writer.* `DocxPackage.Block.Paragraph(runs, alignment)` and `DocxPackage.Block.Heading(level, runs,
alignment)` with `alignment: DocxPackage.Align?` in `{null, LEFT, CENTER, RIGHT, JUSTIFY}`, serialised by
`DocxPackage.write` into a real `.docx`; `alignment` is `null` unless the caller sets it. Reading is
`DocxPackage.read`.

*Spreadsheet.* `XlsxStyledBook.CellStyle(bold, italic, fontRgb, fillRgb, thinBorder, numberFormat,
horizontal, vertical)` with `horizontal: HAlign?` in `{null, LEFT, CENTER, RIGHT}` and `vertical: VAlign?`
in `{null, TOP, CENTER, BOTTOM}`, consumed by `XlsxStyledBook.write` into `xl/styles.xml` +
`xl/worksheets/sheetN.xml` of a real `.xlsx`. Reading is `XlsxStyledBook.read`.

**Information/access model.** Both writers are pure functions of caller-supplied in-memory data; the
verifiers see only emitted bytes and the intent table the probe wrote first. No private writer state is
exported to the verifier, so agreement is evidence about bytes, not about shared code.

**Resource counted.** Emitted property elements that change rendering: `w:jc` (its presence, its `w:val`
token, its position inside `w:pPr`) and, per workbook, the `cellXfs` entries with their `<alignment>`
children, `applyAlignment` flags and cell `s` references — including *sharing* between cells. Not counted:
pixels, pagination, Excel/Word interpretation, recalculation, memory, time.

**Success and failure, with quantifier order.**
For the writer: **for every** document whose headings and paragraphs declare no alignment, no `w:jc` is
emitted and the bytes are exactly the pre-R82 bytes (proved by the R62 baseline); **for every** declared
alignment, `w:jc` appears exactly once inside that paragraph's `w:pPr`, carries the mapped token
(`LEFT→left`, `CENTER→center`, `RIGHT→right`, `JUSTIFY→both`), is the last property child emitted, and the
prefix before it (`w:pStyle`, then optionally `w:numPr`) remains in CT_PPr sequence order; **and for every**
list item, quote, code block, page break or table cell, no `w:jc` is emitted at all. Round-trip:
`read(write(doc)).blocks == doc.blocks` for every fixture.

For the spreadsheet: **for every** workbook, the number of `cellXfs` entries equals one plus the number of
*distinct non-default style signatures declared in the intent* (never computed by the implementation);
**for every** cell with alignment, its `s` entry contains `<alignment>` with exactly the declared
`horizontal`/`vertical` attributes and `applyAlignment="1"`; **for every** cell without alignment, its
entry contains no `<alignment>`; **for every** pair of cells with identical declared signatures, both
resolve to the *same* entry; **for every** pair of distinct signatures, to different entries; and index 0
is the default style.

**Parameters, boundaries, units, tolerances.** Tokens are exact strings, not tolerances: the writer's set
is `{left, center, right, both}` (CT_Jc's five schema values include `distribute`, which this writer does
not emit and which the reader maps to *no* alignment rather than to a guess); the sheet's horizontal set is
`{left, center, right}` and vertical `{top, center, bottom}` — the tokens ECMA-376 §18.8.1 defines for
cell alignment. Import is lenient in both directions (`start`/`end`/`centerContinuous`/`justify` map onto
the same enums), so a foreign file is never rejected because of an alignment value this writer does not
produce. No numeric tolerance is involved anywhere in this round.

**Adjacent problems that must not be substituted.** Word/Excel rendering; paragraph indentation, spacing,
bidi, or `distribute`; character-level alignment; `w:lvl/w:jc` (numbering-label alignment — explicitly out
of scope); table row/column alignment (`w:trPr/w:jc`); cell `wrapText`, `indent`, `textRotation`,
`shrinkToFit`; merged-cell alignment (it cannot be overridden per cell in Excel); conditional formats;
Android UI or provider behaviour; Class A acceptance of either app.

## B. Outcome space

* **H+ (achievable here).** Both alignments can be added additively, verified by an independent reader
  that genuinely exposes the property, with legacy bytes unchanged. *Observed: supported* — and stronger
  than R81, because **both** python-docx (`Paragraph.alignment`) and openpyxl (`cell.alignment`) read the
  property natively; nothing here rests on raw XML alone.
* **H− (not achievable here).** Rendering: whether a centred heading is centred, whether `both` looks
  justified, whether a top-aligned cell paints at the top. No Word, Excel, LibreOffice, display or device
  exists in this environment. *Observed: true; stated as a limit, not a pass.*
* **H∂ (boundary-conditional).** The claim is exact only for the four/six emitted tokens; other CT_Jc and
  ST_HorizontalAlignment values are import-only and deliberately lose information (mapped to `null`), which
  is a documented asymmetry between what can be read and what can be written.

**Commit threshold declared before editing:** (1) R62's 128-case byte baseline and the R60 pinned hash
unchanged; (2) `kotlinc -Werror` execution of both probes; (3) python-docx **and** openpyxl reading the real
artifacts; (4) negative controls rejecting every reverted guard; (5) full host suite with zero
failures/errors; (6) census and source audit reproducible; (7) this document naming what was not run.

## C. Frontier (restated, with its hypotheses)

* `w:jc` is CT_Jc, defined in ECMA-376 Part 1 §17.3.1.13 with values `left`, `center`, `right`, `both`,
  `distribute`, defaulting to left for LTR text — [OOXML reference for `w:jc`](https://ooxml.org/wordml/w-jc/).
  Hypothesis imported: `both` (not `justify`) is the schema token for justified text.
* `w:pPr` is a **sequence**, so a property written in the wrong place is at best repaired and at worst
  dropped; `keepNext` precedes `jc` (§17.3.1.26), and writers that insert alignment must respect
  `pStyle → numPr → spacing → ind → jc → rPr` — reported independently by a
  [Euro-Office fix that lost `keepNext` when alignment was set](https://github.com/Euro-Office/core/pull/246)
  and a [writer that inserts properties at their CT_PPrBase positions](https://github.com/UseJunior/safe-docx/pull/1294).
  Hypothesis imported: `pStyle` then `jc` is a legal prefix when neither `numPr` nor spacing is emitted.
* `xf` (a cell format) takes the children `alignment` (§18.8.1) then `protection` then `extLst`, and
  `applyAlignment` decides whether the alignment is applied —
  [ECMA-376 `xf`](https://webapp.docx4java.org/OnlineDemo/ecma376/SpreadsheetML/xf.html) and
  [Microsoft `CellFormat`](https://learn.microsoft.com/en-us/dotnet/api/documentformat.openxml.spreadsheet.cellformat?view=openxml-2.8.1).
  Hypothesis imported: a single `<alignment>` child with `horizontal`/`vertical` attributes is sufficient,
  and `applyAlignment="1"` is required for consumers to honour it.
* A real-world `styles.xml` containing `<alignment vertical="top"/>` inside `<cellXfs><xf …>` is
  [published verbatim in a public spreadsheet sample](https://github.com/ReneNyffenegger/about-Office-Open-XML/blob/master/SpreadsheetML/Merge-cells/xl/styles.xml),
  confirming the shape this writer emits.

**Open interface.** Whether Word and Excel *apply* these tokens as declared. That is precisely what no
reader here can answer, and it is why Class A is not claimed.

## D. Negative space (17 shapes that look like a solution and are not)

| # | Shortcut | Why it fails the contract | Detector |
|---|---|---|---|
| 1 | Emit `w:jc` before `w:pStyle` in a heading | CT_PPr is a sequence; consumers repair or drop it | `test_alignment_before_the_paragraph_style_is_rejected` |
| 2 | Use `justify` instead of `both` | Not a CT_Jc token | token equality against the declared table |
| 3 | Emit `w:jc` for *every* paragraph (defaults too) | Changes every legacy document's bytes | R62 128 byte-identical baseline |
| 4 | Emit `w:jc` on list items/quotes/code blocks | Outside the declared model; can change numbering alignment | `test_alignment_on_a_list_item_is_rejected` |
| 5 | Put `w:jc` on table-cell paragraphs | Cell paragraph properties are not this feature | `test_alignment_inside_a_table_cell_is_rejected` |
| 6 | Add an unrelated property (e.g. `w:sz`) into `w:pPr` | Unknown child in a sequence | `test_unknown_paragraph_property_is_rejected` |
| 7 | Trust python-docx alone for ordering | It exposes values, not child order | raw-OPC `PROPERTY_ORDER` check |
| 8 | Trust raw XML alone for the value | Would not show the token reaches a real reader | python-docx `alignment` comparison |
| 9 | Put `<alignment>` in `cellStyleXfs` | Cell formats live in `cellXfs` | xf lookup by the cell's `s` |
| 10 | Omit `applyAlignment="1"` | Consumers may skip the alignment | `test_missing_apply_alignment_is_rejected` |
| 11 | Attach alignment to the default xf 0 | Silently reformats every unstyled cell | `test_alignment_on_the_default_style_is_rejected` |
| 12 | Duplicate one xf per cell instead of sharing | Bloat plus contradictory sharing | `cellXfs` count derived from declared signatures |
| 13 | Give two identical styles two entries | Same failure from the other side | `test_two_identical_styles_on_different_entries_are_rejected` |
| 14 | Add `wrapText`/`indent`/`textRotation` "while we are here" | Attributes outside the declared contract | `undeclared attributes` rule |
| 15 | Drop `applyFont`/`applyFill` when alignment is added | Silent loss of an existing feature | `test_missing_bold_flag_is_rejected` + styled probe |
| 16 | Recompute the expected xf sharing from the registry | Circular: the expectation would come from the code under test | intent carries style *fields*; the verifier derives sharing |
| 17 | Call `kotlinc` or a merge a verification | Neither renders anything | §H, §J |

**Modal shortcut, expanded: #3 followed by #10.** The tempting one-line patch is "write `w:jc` on every
paragraph (default `left`) and add `<alignment>` to the default `xf`", which makes the feature visible in a
single screenshot-less diff and passes a naive value check. It fails because it rewrites every document that
was ever emitted (a legacy regression, caught by the R62 byte baseline) and because a default `xf` alignment
changes cells nobody asked about (caught by the xf-0 rule). The first half is detected by an existing,
pre-registered baseline; the second half by a control that injects `<alignment>` into `xf 0` and expects
refusal.

## E. Mechanism portfolio (eight distinct)

| # | Mechanism | Construction | Artifact | Proved portion | Unresolved interface | Cheapest falsifier | Gap |
|---|---|---|---|---|---|---|---|
| M1 | **Chosen (writer):** optional enum on two block types, emission branch per block | `alignment: Align? = null`; `w:jc` appended after the existing prefix | `DocxPackage.kt` (+23 lines) | token map, order, absence, round trip | rendering | R62 baseline + order control | local |
| M2 | Writer: paragraph style (`w:pStyle`) carrying alignment | would need `word/styles.xml` | not implemented | nothing | new part changes the package shape and every consumer | verifier forbids unknown `pPr` children | different feature (styles) |
| M3 | Writer: run-level alignment | Not a WordprocessingML concept — `w:jc` is paragraph-level | rejected in design | nothing | none | schema citation §17.3.1.13 | **not expressible** |
| M4 | Writer: `w:jc` on `w:lvl` (numbering label) | Reuses the element for a different target | rejected in design | nothing | changes list rendering, not paragraphs | verifier only accepts `pPr` children of body paragraphs | different problem |
| M5 | **Chosen (sheet):** alignment inside the interned xf | `horizontal`/`vertical` fields on `CellStyle`, carried into the registry's `Quad` | `XlsxStyledBook.kt` (+34 lines) | sharing, attributes, apply flags | Excel rendering | sharing + count controls | local |
| M6 | Sheet: alignment via a named cell style in `cellStyles` | separate indirection layer | rejected in design | nothing | needs style records this writer does not emit | verifier resolves only `cellXfs` | different feature |
| M7 | Sheet: per-cell `<alignment>` duplicated in every `xf` | trivially correct, bloated | rejected (measured): `shared` fixture asserts 3 entries, not 4 | bloat bound | none | `cellXfs` count control | local, superseded by M5 |
| M8 | Sheet: alignment in `row`/`col` dimensions | Applies to whole rows/columns, not cells | rejected in design | nothing | different semantics entirely | cell-level attribute rule | different problem |

## F. Artifacts and what they are not

* `testing/build/r82writer/artifacts/` — 8 real `.docx` files + `align.tsv` (20 body paragraphs):
  `default`, `heading-center`, `paragraph-right`, `paragraph-justify`, `paragraph-left`, `all-four`,
  `mixed` (heading + paragraph + list + quote + code + sized table + paragraph), `layout-align`
  (alignment + A4 section + header/footer + hyperlink).
* `testing/build/r82sheet/artifacts/` — 6 real `.xlsx` files + `align.tsv` (17 cells): `default`,
  `horizontal`, `vertical`, `combined`, `shared` (dedup), `styled` (alignment + bold + fill + border +
  number format).
* Tools: `run_r82_writer_align.py`, `check_r82_writer_align.py`, `test_r82_writer_align.py` (13 controls);
  `run_r82_sheet_align.py`, `check_r82_sheet_align.py`, `test_r82_sheet_align.py` (13 controls).
* **Not artifacts of this round:** any rendered page, any Excel screenshot, any Android frame.

## G. Second-order adversary, per candidate

**A(writer alignment).** Structure: one optional field, one insertion point after an existing prefix.
1. *Prefix blindness* — inserting `w:jc` before `w:pStyle`. Caught by the CT_PPr order check.
2. *Silent default drift* — emitting `left` always. Caught by R62 (128 byte-identical) and by the
   "no `w:jc` when undeclared" rule.
3. *Scope creep* — aligning list paragraphs. Caught because the intent records a kind per paragraph and the
   verifier refuses `w:jc` on anything but heading/paragraph.
4. *Token drift* — `justify` for `both`. Caught by exact token equality, and by python-docx mapping
   `both→JUSTIFY`.
5. *Reader-only success* — a value python-docx sees but that is not schema-ordered. Caught because raw OPC
   checks order and python-docx checks value; neither alone suffices.
6. *Import asymmetry* — a foreign `distribute` paragraph. Handled by mapping to `null` (lossy, documented),
   never to a guess, and never as an exception.
7. *Duplicate `w:jc`* — two `jc` children. Caught by sequence/unknown-child rules (a repeated name breaks
   the strict ordering list).

**A(sheet alignment).** Structure: alignment lives *inside* an interned entry, so the attacker targets the
interning and the entry.
1. *Interning collapse* — two different alignments sharing an xf. Caught by "two different declared styles
   share one xf", with the expected count derived from intent.
2. *Interning explosion* — identical alignments on distinct entries. Caught by the same-signature-same-`s`
   rule and by the `cellXfs` count.
3. *Attribute smuggling* — `wrapText` added alongside. Caught by the undeclared-attribute rule.
4. *Silent style loss* — building a new xf without `applyFont`/`applyFill`. Caught by per-flag rules and by
   the styled fixture (bold + fill + border + format + alignment on one cell).
5. *Default contamination* — alignment on `xf 0`. Caught by the xf-0 rule.
6. *Part confusion* — alignment written to `cellStyleXfs`. Caught because the verifier resolves the cell's
   `s` into `cellXfs` only.
7. *Reader leap* — openpyxl synthesises defaults. Mitigated by requiring `None` exactly where the intent
   declares none.

**Adversarial passes run (8).** (i) legacy-byte attack; (ii) order attack; (iii) scope attack on non-paragraph
blocks; (iv) token attack; (v) interning collapse; (vi) interning explosion; (vii) attribute/flag smuggling;
(viii) intent tampering. Passes (iii) and (vii) changed the code: the intent table had to record the *block
kind* and the *style signature fields* rather than an interned index, otherwise the verifier would have been
deriving its expectation from the implementation.

## H. Verification — what actually ran

One agent, one Linux sandbox: bash, Python 3.11, Node/npm, host-only Kotlin 2.2.21 via npm, an old JRE 17,
python-docx 1.2.0, openpyxl 3.1.5, `ssf` 0.2.2, formualizer 0.13.0, ImageMagick. **Absent:** Android SDK,
emulator, Studio, device, Word, Excel, LibreOffice, pandoc, any theorem prover, any display, any second
agent.

Restore first, and this round it was a real restoration: the local branch ref was stale at `dfbc5b4` with the
whole R81 change set sitting uncommitted, while `origin` already had it as `8a68396`. `git add -A` produced an
**empty** diff against `origin/arena/95f1a43c-osb`, so the working tree was byte-identical to the pushed
commit; `git reset --hard origin/arena/95f1a43c-osb` restored the ref and the tree is clean at `8a68396`.
`npm ci`, `npm run build` (225.28 kB, gzip 70.00 kB) and `npx tsc --noEmit` all exited 0, and the host
toolchain plus both reader caches were reinstalled from their pinned versions.

| Gate | Result |
|---|---|
| `run_r82_writer_align.py` | `R82_WRITER_ALIGN_OK assertions=25 documents=8`; verifier: **8 packages / 20 body paragraphs** via python-docx + raw OPC |
| `test_r82_writer_align.py` | **13/13** controls |
| `run_r82_sheet_align.py` | `R82_SHEET_ALIGN_OK assertions=28 workbooks=6`; verifier: **6 workbooks / 17 cells** via openpyxl + raw OPC |
| `test_r82_sheet_align.py` | **13/13** controls |
| R60 Writer contracts | 675 assertions, 256 DOCX |
| R60 baseline | 128 cases, `ab4840b2…` (unchanged) |
| R62 baseline | **128 byte-identical**, `e89d1a2…` (unchanged) |
| R74 links | 20 independent checks |
| R75 images | ImageMagick decoded the embedded media (PNG 4×3, 8×2) |
| R76 Writer layout | 74 assertions, 9 packages |
| R76 sheet formats | 239 rows re-derived, 231 identical through `ssf`, 8 adjudicated |
| R77 conditions | 60 observations, 7/7 `ssf` controls, 4/4 pinned |
| R81 writer tables | 44 assertions, 9 packages / 10 tables |
| R81 sheet rows | 46 assertions, 6 workbooks / 7 sheets |
| Full `tools` suite | **1801 tests OK (22 skipped)** |
| Census | **50/50 = 100.00 %** substantial testable subset, **0/50 = 0.00 %** fully accepted |
| Source audit | 231 requirements, 36 obligations, all native packages owned |

**Visual inspection: not performed, and not substituted.** No renderer, no display, no device. The
repository's only real captures belong to earlier revisions and cannot be regenerated for this commit, so
this document records the gap instead of calling a screenshot a pass. What a picture would have shown
(alignment actually applied) is outside every reader available here — python-docx and openpyxl report the
*declared* property, not the painted result, and this document does not blur that line.

## I. Regressions versus the best prior codebase (R81, `8a68396`)

**None observed.** R62's 128-case byte-identical baseline and R60's pinned hash are unchanged, which is the
direct evidence that an unset `alignment` emits exactly the R81 bytes; every prior lane reproduces its R81
numbers (R74 20, R75 decoded media, R76 74/9, R76 formats 239/231/8, R77 60 observations, R81 44/9 and
46/6). The suite grew 1775 → **1801** (+26: 13 writer + 13 sheet controls, measured as `Ran 26 tests`).
Census stays 50/50 — 0/50. Production change is 57 added lines across two files, with no deletion.

Two defects of this round were found and fixed before any gate was called green: the styled-sheet probe
declared cells out of sheet order and failed its own round-trip equality (fixed, with the reason in the
code), and one writer control was a near-duplicate of another (replaced by the unknown-`pPr`-child
control, which tests a distinct rule).

## J. Class A, merge, stopping, and the next test

**Class A is still not claimed for Writer, Spreadsheet or anything else.** This round is the strongest of
the recent rounds on *reader independence* — both properties are read by a third-party implementation —
but a third-party *reader* is not a renderer and not a device. The question "can both go Class A this
turn?" is answered **no**, with the missing observer named: a real word processor, a recalculation engine,
and hardware.

**Merge.** PR #17 is merged at the end of the turn on instruction. The merge is a repository operation, not
verification, and the CI job's two device-only steps remain red.

**Stopping rule reached honestly.** Two production features, two probes, six tools, 26 controls, four
`gradle` source sets and a full regression sweep is the budget for one round. The next increment cannot come
from more host-side XML work.

**Next highest-information test** (outside this sandbox): open
`testing/build/r82writer/artifacts/all-four.docx` in Word — are the four paragraphs left, centred, right and
justified as declared? Then open `testing/build/r82sheet/artifacts/styled.xlsx` in Excel — is `A1` bold,
filled `FFF2CC`, bordered and centred both ways, and is `B2` right-aligned with a `#,##0.00` format? Those
two files, opened by the two applications that define the format, are exactly the evidence no reader in
this environment can produce.
