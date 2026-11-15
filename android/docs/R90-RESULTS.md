# R90 — Writer run decorations (`w:vertAlign`, `w:highlight`) and SpreadsheetML font decorations (`<u/>`, `<strike/>`)

Branch `arena/92b53607-osb`, branched from `92a1578` (the R89 merge). Production footprint: **+59 / −5
lines in two files** (`DocxPackage.kt` +34, `XlsxStyledBook.kt` +30/−5). Everything else is probe,
checker, control and census material. No existing behaviour was removed or reordered.

## 0. The restoration request — the tree was provably empty this time too

`git status --porcelain` → empty; `git log -1` → `92a1578`; `git rev-parse origin/main` → `92a1578…`;
`git stash list` → empty. There was no uncommitted work to restore, and no stash to pop. The toolchain was
rebuilt from the pinned manifests (`tools/bootstrap_host_tools.py install`, `npm ci --prefix tools/r87node`,
`python-docx==1.2.0`, `openpyxl==3.1.5`, `ssf==0.2.2`, `babel==2.9.1`, `formualizer`), because "restore the
changes" also means "make the thing that produced them runnable again".

## A. Formal model

**A.1 Writer.** `Run` gains two optional fields, both trailing so every prior call site is untouched:

* `verticalAlign: VerticalAlign?` — the three-value ST_VerticalAlignRun set (`baseline`, `superscript`,
  `subscript`), emitted as `<w:vertAlign w:val="…"/>`; `baseline` is normalised to `null` and therefore
  never emitted, because it is the default the reader already assumes.
* `highlight: HighlightColor?` — the **closed** 16-token ST_HighlightColor set (`black`, `blue`, `cyan`,
  `green`, `magenta`, `red`, `yellow`, `white`, `darkBlue`, `darkCyan`, `darkGreen`, `darkMagenta`,
  `darkRed`, `darkYellow`, `darkGray`, `lightGray`). It is an enum, not a string, so a caller cannot write a
  token Word does not define.

Emission order inside `w:rPr` is fixed and asserted:
`<w:b/> <w:i/> <w:color/> <w:sz/> <w:highlight/> <w:u/> <w:strike/> <w:vertAlign/> <w:rFonts/>`. A run with
no decorations still emits **no** `w:rPr` at all (structural control, not an assertion about appearance).

**A.2 Writer import.** An unknown `w:highlight` token reads back as `null` (not a guessed nearest colour); a
`w:vertAlign` value of `baseline` reads back as `null`; an absent element reads back as `null`. The reader
accepts unknown decorations it does not model without refusing the package.

**A.3 Spreadsheet.** `CellStyle` gains `underline: Boolean` and `strike: Boolean`; they enter `FontKey`, so
two cells that differ only in an underline get different `<font>` records and the shared `<fonts>` table
still interns equal fonts. Emission order in `CT_Font` is `b<i<strike<u<sz<color`. On import, `<u/>` with
*any* `val` (`single`, `double`, `singleAccounting`, …) reads as underlined — the style is not modelled and
is not claimed; `<strike/>` likewise.

**A.4 Counted resource.** The bytes of `word/document.xml` and `xl/styles.xml` plus the per-cell font
references (`<c r=… s=…>` through `cellXfs/fontId`). Nothing else is counted, and nothing above the OPC
layer is measured.

**A.5 Quantifier order.** "For every run in every package written by the probe, the `w:rPr` children equal
the declared decoration set, in the declared order" — the intent tables are the declaration, the packages
are the observation, and the checker refuses a package that the table does not declare.

**A.6 Regime, units, tolerances.** Exact: enums are closed sets, tokens are compared case-sensitively at
the XML layer and lower-cased only where the third-party renderer demonstrably lower-cases them. No numeric
tolerance exists in this slice because no measurement is involved.

**A.7 Adjacent non-substitutes.** Paragraph shading (`w:shd`, R87), paragraph borders (`w:pBdr`, R88), run
colour/size (`w:color`/`w:sz`, R89), cell fills (`<fill>`), cell borders, and `w:vertAlign` in *tables* are
different features. Conflating `w:highlight` with `w:shd` would be wrong: `w:shd` shades the run's box,
`w:highlight` is the reviewer marker Word's toolbar produces, and the two render differently.

## B. Outcome space against the pre-declared threshold (`docs/R72-PLAN.md` §B)

**H+.** Before R90 a caller could not mark text as super/subscript or as reviewer-highlighted, and a cell's
underline/strike state could not be expressed at all — the font record had no `<u>` or `<strike>` child and
`FontKey` could not distinguish such cells. Now 4 superscript/subscript runs, 5 highlighted runs across
5 packages and 5 decorated cells across 3 workbooks are produced and independently read back by four
different implementations (raw OPC, python-docx, openpyxl, exceljs) plus one renderer (docx-preview).

**H−.** This does not make either application Class A. Python-docx and openpyxl are *readers*, not the
applications; they confirm bytes, not that Word/Excel would lay out or paint them. docx-preview reproduces
`background-color` but **drops run-level `vertAlign`** (see H1), so superscript/subscript rendering is
verified at the XML and object-model level only. There is still no Word, Excel, LibreOffice, Android device
or browser in the loop.

**H∂.** The closed sets are enforced at the exact boundary: the 16th token is accepted, a 17th is
unrepresentable in the API; `baseline` is accepted on import and normalised away on export; an unknown
`w:highlight` value on import yields `null` rather than a nearest-neighbour guess.

## C. Frontier, with the hypotheses it imports

The frontier moved by two *attributes*, not by a rendering capability. Imported hypotheses, each falsifiable
and none of them established here: (i) Word's `w:highlight` is a 16-value palette whose colour keywords are
stable across versions — imported from ISO/IEC 29500-1 §17.18.40 and python-docx's `WD_COLOR_INDEX`; (ii) a
`w:vertAlign` of `superscript` raises the run without changing its size — imported from §17.3.2.42, *not*
observed; (iii) `<u/>` without `val` means a single underline and any `val` means "underlined at all" for
the purposes of a boolean model — a deliberate, stated loss of the `val` distinction. Each hypothesis is
listed because a Class A claim later would have to discharge it.

## D. Negative space — shapes that look like a solution and are not

1. Writing `w:highlight` as a free-form string (Word silently ignores tokens outside the closed set).
2. Reusing `w:shd` as "highlight" (different visual object; already shipped in R87 and asserted distinct).
3. Emitting `<w:vertAlign w:val="baseline"/>` for unstyled runs (noise that breaks the no-`rPr` control).
4. Encoding superscript as a raised `position` plus a smaller `sz` (that is the OOXML *fallback* pair, not
   the feature; it also silently changes the font size the caller asked for).
5. Mapping `w:vertAlign` onto paragraph spacing (visually similar for a single line, wrong for every other).
6. Guessing python-docx colour names (`cyan` → `TURQUOISE`, `green` → `BRIGHT_GREEN`) by string equality.
7. Reading `<u val="double"/>` as "not underlined" because it is not `single`.
8. Putting `<u/>` in the `cellXf`/`xf` instead of the font record (Excel ignores it).
9. Adding `<u/>` after `<sz>` (order violation the schema tolerates but Word's round-trip does not keep).
10. Treating a bold-only cell as "decorated" so the decoration count looks bigger.
11. Counting docx-preview's silence about `vertAlign` as agreement.
12. Reporting the exceljs lane as "Excel opened the file".
13. Defaulting an unknown highlight token to `yellow` (a plausible-looking lie).
14. Using `enum.entries` where the pinned compiler rejects it (a build break, not a feature).
15. Deriving the sheet checker by scripted string replacement without asserting the anchors (it silently
    wrote nothing — see G2).
16. Claiming CSS-level sup/sub because the CSS dump contains a `verticalAlign` key somewhere else.

## E. Mechanism portfolio — six branches, each with a falsifier

| Branch | What it does | Falsifier it survives / fails |
|---|---|---|
| Raw OPC | parses `word/document.xml` / `xl/styles.xml` itself | fails if a decoration is declared but absent, present but undeclared, or out of the declared child order |
| python-docx 1.2.0 | `run.font.superscript/subscript/highlight_color` | fails on any token whose `WD_COLOR_INDEX` name is unmapped — the map is deliberately partial and refuses rather than guesses |
| openpyxl 3.1.5 | `cell.font.u` / `cell.font.strike` | fails if `<u val="double"/>` were read as not-underlined |
| exceljs 4.4.0 | `font.underline` (boolean *or* `{style}`) / `font.strike` | fails if the writer emitted a shape exceljs coerces differently |
| docx-preview 0.4.1 | HTML/CSS `background-color` for highlights | **fails for `vertAlign`** — and the checker reports the 4 dropped values instead of hiding them |
| Negative controls | 37 tampering tests | fails if any lane accepts a mutated package or a mutated intent row |

## F. Artifact requirement

Every claim above names a file on disk that a reader can re-open: `testing/build/r90/artifacts/`
(`plain.docx`, `align.docx`, `highlight.docx`, `combined.docx`, `mixed.docx`, `deco-default.xlsx`,
`deco.xlsx`, `deco-mixed.xlsx`, `decorations.tsv`, `decorations_xlsx.tsv`). The intent tables are written by
the probe *before* any verifier runs, and the verifiers refuse a folder whose file set disagrees with them.

## G. Second-order adversary — what attacked this work and what it caught

**G1.** docx-preview lower-cases the ST token before writing `background-color` (`darkBlue` → `darkblue`).
The first version of the render lane asserted equality and failed. Fixed by asserting the lower-cased token
and stating the consequence in the output: only `yellow`, `white`, `green` and `cyan` are also CSS colour
keywords, so `darkblue` would not paint in a browser. The checker now proves *what the renderer does*, which
is weaker and true, instead of what I assumed.

**G2.** The sheet checker was first "derived" from the R89 checker by twelve scripted replacements; one
anchor did not exist, the assertion fired, and **no file was written**. The failure was only visible because
the next command said `No such file`. Rewritten as an authored file with its own structure — the derived
version would also have inherited R89's font-size semantics it does not need.

**G3.** The census validator rejected the R90 probe listed under `:format:ooxml` (production artifacts must
live under the module's own folder) and then rejected the edit because six source hashes were stale. Both
refusals were correct; the probe moved to `:testing` and the hash map was refreshed for exactly the six files
that changed.

**G4.** The R72 pinned-vs-candidate comparators silently compiled an *empty* pinned source set because the
clone was shallow and `git cat-file -e` failed for every baseline path. `git fetch --unshallow origin` fixed
it; without that unshallow, four lanes would have reported PASS while comparing nothing.

**G5.** Two runners refused to overwrite existing evidence (`Refusing to overwrite existing evidence`), which
is the intended guard; the directories were removed and the lanes re-run rather than the guard bypassed.

## H. Verification — what ran

**H1. R90 probe and five lanes** (`python3 tools/run_r90_checks.py`):

* `R90_OFFICE_OK assertions=63 documents=5 workbooks=3`
* raw OPC + python-docx: *5 packages, 10 runs (4 aligned, 5 highlighted); python-docx agreed on 10 runs,
  4 alignment values and 5 highlight values*
* raw OPC + openpyxl: *3 workbooks, 8 cells (5 with a declared decoration); openpyxl agreed on 8 cells*
* docx-preview: *5 packages, 10 runs — 5 highlights rendered as `background-color`, 4 `vertAlign` values
  that docx-preview 0.4.1 drops*
* exceljs: *8 cells in 3 workbooks, 5 with a declared underline/strike*

**H2. Negative controls — 37 tests, all green**: `test_r90_decorations` (13), `test_r90_sheet_decorations`
(9), `test_r90_lanes` (15). They cover: dropped/changed/extra decoration, reordered `CT_Font` children,
invented intent rows, dropped intent rows, a tampered python-docx/openpyxl table, a cross-package swap, a
missing `document.xml`, a missing `styles.xml`, a wrong field count, a renamed workbook, a renderer that
returns the wrong CSS value, and a renderer that *does* support `vertAlign` (which must then be checked, not
skipped). Three of them skip when python-docx/openpyxl are absent on the host; CI installs both.

**H3. Regression sweep — 41 previously-recorded runner scripts, re-executed serially.** 26 office,
integration and render lanes (R72 core/office/ooxml ZIP readers, R72 Writer guard, R73 371-formula oracle,
R74 links, R75 sheet fractions 282 assertions/136 rows, R75 images, R76 Writer layout 74/9, R76 sheet
formats 786/245, R77 conditions 207/60, R81 Writer tables 44/9, R81 sheet rows, R82 Writer align 25/8,
R82 sheet align 17 cells, R83 Writer spacing 30/8, R83 hidden rows, R84 Writer indent, R84 sheet
visibility, R86 Writer tabs 11 packages/21 paragraphs, R86 sheet views, R87 shade, R87 render lanes,
R88 checks, R89 checks, R90 checks) and 15 R5x/R6x corpus generators (R56 24 451 assertions, R57 548,
R58 6 428, R59 1 580 + 200 DOCX, R60 675 + 256 DOCX / 6 588 + 64 XLSX, R61 1 831, R62 1 288 + 1 434,
R63 1 522 + 2 246, R64 1 093 + 814 + 613). Every lane exited 0; the four lanes that needed a `PATH`
toolchain or an unshallow are named in G4/G5 rather than quietly dropped.

**H4. Full host discovery.** `python3 -m unittest discover -s tools -p 'test_*.py'` →
**`Ran 2228 tests … OK`**, zero skips. (R89: 2165 with 3 skips. The increase contains the 37 new R90
controls plus corpus-dependent tests in the R56–R64 families that only execute when their generated artifacts
are present.)

**H5. Census and audit.** `module_testability.py --check` → **50/50 modules (100.00 %) meet the bounded
testability standard; 0/50 accepted**; `native_source_audit.py --check` → **231 recorded requirements, 36
cross-cutting obligations**, unchanged and reproducing.

**H6. Not run / not claimable.** No Android build, emulator, device, provider or file-picker gate ran. No
Word, Excel or LibreOffice opened any artifact. No browser screenshot exists for R90: `soffice`, `libreoffice`
and a headless Chrome are all absent from this host, so the only visual-adjacent evidence is docx-preview's
CSS dump — which is exactly why `vertAlign` is reported as dropped rather than as rendered.

## I. Regressions versus the best prior codebase (R89, `92a1578`)

None found. The two production files only gained fields, enum values, emission branches guarded by
`null`/`false`, and reader branches; no existing branch was reordered or deleted (`git diff` is +59/−5, and
five of those deletions are the `FontKey`/`rPr` line rewrites that keep the previous output byte-identical
when the new fields are absent). The evidence for "no regression" is H3: every prior lane that reads these
two files still passes, including the R89 font-size/colour lane, the R88 border lane, and the R76/R81/R82/
R83/R84/R86 Writer lanes.

Residual risk, stated rather than hidden: both changes only *append* defaulted constructor parameters, so
positional and named construction sites are source-compatible; the only shapes that could break are
destructuring declarations and exhaustive `when`s over new enum values. A tree-wide `grep` finds **zero**
destructuring declarations of `Run`/`CellStyle` (`val (text, …` / `component1()`), every `CellStyle(`
construction site in the tree uses named arguments, and the three `when (run)` sites match unrelated
`IntRange`/number types. The whole `format/archive` + `format/ooxml` source set still compiles with
`-Werror` at the pinned compiler, which is what the probe and every lane below were built from.

## J. Class A, the PR, and the next test

**Class A: still 0 applications.** Five independent implementations now agree on 10 runs and 8 cells, but
agreement among readers is not layout, pagination, painting or repair. Word-class acceptance needs an
application to open the file, and none did.

**The PR.** This turn's instruction was to open a new PR, so the branch is pushed and a PR is opened against
`main`; nothing is merged and nothing is closed.

**Next test that would move the frontier.** Run one R90 package through a real Word/Excel round-trip on a
Windows host (or LibreOffice plus a PDF diff) and compare the produced PDF's text runs against the intent
table — that is the only measurement that could turn these reader agreements into a rendering claim.
**Carried, unimplemented:** device-accelerated video upscale and "remove mosaic" — unchanged: the repo has
no player or renderer surface, and removing a mosaic is information-theoretically impossible, so no code was
written to pretend otherwise.
