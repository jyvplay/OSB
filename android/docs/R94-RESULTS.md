# R94 — Merged cells, cell hyperlinks and print layout

**Scope of this pass.** Three worksheet features that were missing from `XlsxStyledBook`, each taken to the
point where a host-only verifier can say something true about it: `<mergeCells>` (§18.3.1.55 /
`CT_MergeCells` §18.3.2.11), `<hyperlinks>` plus the **worksheet relationship part** (§18.3.1.48 /
`CT_Hyperlink` §18.3.2.13, OPC Part 2 §9.3), and print layout — `<printOptions>` (§18.3.1.70),
`<pageMargins>` (§18.3.1.62) and `<pageSetup>` (§18.3.1.63).

**Class A applications: still 0.** No Excel, LibreOffice, browser, printer or Android device ran. What follows
is agreement between this writer, this reader, a raw OPC walker and openpyxl 3.1.5. None of them is Excel, and
nothing here observes a cell being rendered as one, a link being clicked or a page being printed.

## A. What was added

| Piece | File | Lines |
|---|---|---|
| Merged-cell geometry (`Range`, disjointness proof, `toXml`, `parse`) | `XlsxMerges.kt` (new) | 123 |
| Hyperlinks + worksheet relationships (`Link`, URI/location validation, `toXml`, `toRels`, `parse`, `parseRels`) | `XlsxHyperlinks.kt` (new) | 187 |
| Print layout (`Margins`, `Options`, `Setup`, `Spec`, `toXml`, `parse`) | `XlsxPrintSetup.kt` (new) | 180 |
| Integration: three trailing defaulted fields, one `requireDisjoint` call, four emission lines, the relationship part, the rels read | `XlsxStyledBook.kt` (**+28/-1 lines**) | 28 |
| Probe + artifacts | `testing/src/r94/.../R94Checks.kt` | 439 |
| Runner | `tools/run_r94_checks.py` | 78 |
| Raw OPC + openpyxl lane (merges, links, rels, content types) | `tools/check_r94_sheet_structure.py` | 392 |
| Raw OPC + openpyxl lane (print) | `tools/check_r94_sheet_print.py` | 224 |
| Negative controls | `tools/test_r94_sheet_structure.py`, `tools/test_r94_sheet_print.py` | 264 + 167 |

Nothing else in the tree changed: `XlsxProtection.kt`, `XlsxValidation.kt`, `XlsxAutoFilter.kt`,
`XlsxDataValidationXml.kt` and `BoundedXml.kt` are byte-identical to R93.

## B. The contract, stated so it can be wrong

**Placement.** `CT_Worksheet` is a sequence. This pass inserts three positions into it: `mergeCells` *after*
`autoFilter` and *before* `dataValidations`; `hyperlinks` after `dataValidations`; and then `printOptions`,
`pageMargins`, `pageSetup`. openpyxl's own serializer was run and emits the same relative order
(`mergeCells` → `hyperlinks` → `printOptions` → `pageMargins` → `pageSetup`), and the R94 probe asserts the
six-record order on a single sheet that carries every record this writer can emit.

**Merge geometry.** A merged range must be upper-case `A1:B2`, cover at least two cells, lie inside the
1 048 576 × 16 384 grid, and be **pairwise disjoint** — Excel repairs overlapping merges on open, so a model
that can hold them cannot be written back unchanged. The disjointness proof is O(n²) over at most 4 096 ranges
(a model bound, not a schema bound) and runs in `StyledSheet`'s initialiser, so an unwritable sheet cannot even
be constructed. On read, a range outside the grid, a one-cell range, or one that overlaps something already
read is dropped; lower case is normalised, as it is everywhere else in this writer.

**Hyperlinks.** Exactly one of `target` (external) / `location` (internal).
*External*: `r:id` → a relationship in `xl/worksheets/_rels/sheetN.xml.rels` with
`Type="…/hyperlink"`, `TargetMode="External"` and the URI as `Target`; one relationship per **distinct** URI, so
two cells pointing at the same URL share `rHl1`. The URI must be absolute with an RFC 3986 scheme; relative
targets (a path into another document) are refused, because they need a base this model does not carry.
*Internal*: a `location` attribute such as `A1`, `Sheet2!A1:B2` or `'Sheet 2'!A1` — no relationship at all, and
therefore **no relationship part** for an internal-only sheet.
`tooltip`/`display` are bounded, printable, trimmed strings and are XML-escaped on write (proved with a tooltip
containing `&` and `<`).

**Print layout.** `printOptions` writes only the switches set to true (false is the schema default).
`pageMargins` writes **all six** attributes, because all six are `use="required"` in `CT_PageMargins`; the
defaults are Excel's own (0.75, 0.75, 1, 1, 0.5, 0.5). Margins are decimal, bounded to four decimal places and
0..100 inches (model bounds), formatted with `%.4f` so no exponent notation can appear in the bytes. `pageSetup`
carries only `paperSize` (1..118, `ST_PaperSize`), `scale` (10..400) and `orientation`.

**Not modelled, and not claimed:** `extLst` on any of these; relative hyperlink targets; header/footer; row and
column page breaks; `fitToWidth`/`fitToHeight` (they require `<sheetPr><pageSetUpPr fitToPage="1"/>`, which
changes `sheetPr` and is deliberately out of this model); and every editor behaviour — Excel's rule that only
the top-left cell of a merged range is editable is not something a `.xlsx` file states.

## C. Frontier, with the hypotheses it imports

Retrieved and restated: `CT_MergeCells` is `count` + `mergeCell*`, each `ref` an `ST_Ref`; `CT_Hyperlink` is
`ref` (required), `r:id`, `location`, `tooltip`, `display`; the hyperlink relationship type is
`http://schemas.openxmlformats.org/officeDocument/2006/relationships/hyperlink` with
`TargetMode="External"` for a URI; `CT_PrintOptions` has four optional booleans; `CT_PageMargins` has six
required doubles; `CT_PageSetup` has the paper/scale/orientation attributes above.

Imported hypotheses, none discharged here: (i) Excel renders a merged rectangle as one cell and keeps the
top-left value; (ii) Excel resolves `r:id` through the worksheet's own rels part and treats `TargetMode="External"`
as "hand this URI to the OS"; (iii) Excel's print preview uses `pageMargins`/`pageSetup` as the page geometry.
The open interface is the application: a link's click, a merge's rendering and a page's layout are all outside
the file.

## D. Negative space — 15 shapes that look like a solution and are not

1. Writing `<mergeCells>` whose `count` disagrees with its children.
2. Writing overlapping merges (Excel drops one on open, so the file no longer says what the model says).
3. Writing a one-cell merge (`A1:A1`) as if it merged something.
4. Trusting `ref` from the intent table instead of re-parsing it (the checker recomputes every box).
5. Emitting `mergeCells` after `dataValidations` (sequence violation, invisible to a lenient reader).
6. Writing `<hyperlinks>` for a sheet whose links are all internal **and** a relationship part for it — an empty
   `<Relationships>` element claims there is something to relate.
7. Referencing `r:id` values that exist in no relationship part (the link renders as text).
8. Reusing one `r:id` for two *different* targets.
9. Emitting one relationship per *link* rather than per *target* (works, but doubles the part for no reason and
   makes the byte lane unable to state a rule).
10. Writing `location` and `r:id` on the same hyperlink (Excel treats the pair as ambiguous).
11. Writing a relative target (`other.xlsx#Sheet1`) and calling it an external link.
12. Passing a tooltip through unescaped, so `&`/`<` break the part.
13. Writing `<pageMargins>` with only the attributes that differ from the default (five of six are required).
14. Writing `scale="5E-1"`-style exponent notation in a margin.
15. Writing `<printOptions>` with every switch spelled out including the false ones.

Each of those is refused by a named control in `test_r94_sheet_structure.py` / `test_r94_sheet_print.py`; the
modal shortcut (#6, the empty relationship part) was a real defect in this pass's first writer draft — see G1.

## E. Mechanism portfolio

| # | Mechanism | Artifact | Proved portion | Unresolved interface | Cheapest falsifier | Gap |
|---|---|---|---|---|---|---|
| 1 | Raw OPC + full `CT_Worksheet` sequence | `check_r94_sheet_structure.py` | placement, `count`, refs in order, disjointness recomputed in Python, attribute order, `ref`→`r:id`/`location` exclusivity | bytes only | drop `count` | local |
| 2 | Relationship-part inspection | same file | part exists iff an external link exists, `Type`, `TargetMode`, unique `Id`s, one relationship per distinct target | OPC semantics beyond the part | change a `Target` | local |
| 3 | Content-type closure | same file | `rels` default + every worksheet override present, relationship parts under `_rels/` | none here | delete the `rels` default | local |
| 4 | openpyxl `merged_cells` / `cell.hyperlink` | same file, second half | 5 sheets, 4 merge ranges, 6 links with target/location/tooltip/display, and no undeclared link | openpyxl ≠ Excel | drop a link | local |
| 5 | Raw OPC print tail + openpyxl `page_margins`/`print_options`/`page_setup` | `check_r94_sheet_print.py` | order, all six margins, only-true switches, paper/scale/orientation, and openpyxl's defaults for a sheet that declares nothing | openpyxl ≠ Excel | change one margin | local |
| 6 | Foreign-bytes read | `R94Checks.kt` §3b | a hand-built package in openpyxl's own attribute shapes (`xmlns:r` on the hyperlink, `pageSetup` attributes in another order, relationship attributes Type/Target/TargetMode/Id) is read correctly | a second writer's shapes only | move an attribute | local |
| 7 | Round-trip + idempotency | `R94Checks.kt` §3 | merges, links, print equal after read; rewrite reproduces every sheet part and the rels part byte for byte | self-consistency | drop an attribute | local |

## F. Artifact requirement

`testing/build/r94/artifacts/`: `r94.xlsx` (four sheets — `Grid` with merges, mixed links and full print
layout; `Bare` with nothing; `Every` carrying protection + autoFilter + merge + validations + hyperlink +
print in one tail; `Internal` with internal links only), `r94-plain.xlsx`, and the intent tables `merges.tsv`
(3 fields), `hyperlinks.tsv` (7) and `print.tsv` (13). Both verifiers refuse a folder whose workbook set or
sheet set disagrees with the tables — that rule was added after a control proved a dropped row could otherwise
pass (G3).

## G. Second-order adversary — what attacked this work and what it caught

**G1. An empty relationship part (real defect, found by the probe).** The first writer draft emitted
`xl/worksheets/_rels/sheetN.xml.rels` for any sheet with hyperlinks, including a sheet whose links are all
internal. The result was a well-formed `<Relationships>` element with no children — a claim that something is
related when nothing is — and, in Excel's terms, a part that exists for no reason. The probe's `Internal` sheet
failed on it; the writer now emits the part only when `relationshipIds` is non-empty.

**G2. `Hyperlinks` and `Merges` were parsed without coverage.** My first two checkers walked only the rows the
intent table contained, so dropping a *sheet* row left that sheet unchecked while the file set still agreed —
the same class of hole R92 found in its own verifier. Both checkers now require the described (file, sheet) set
to equal the workbook's actual sheet set, and each has a control that deletes a row.

**G3. A missing element reached a `KeyError`, not a `Fail`.** `pageSetup` attributes could be declared in the
intent table while the element was absent from the sheet; the verifier crashed instead of refusing. Found by the
`test_page_setup_removed` control. Every element lookup in the print lane is now preceded by a presence check,
because "the verifier throws a Python exception" is not a verification result.

**G4. A control that silently did nothing.** After the tooltip gained `&`/`<` for the escaping test, the
attribute-order control still searched for the old literal, so its tamper was a no-op and the test "passed".
It failed on the next run — which is what controls are for — and the literal was updated. A control that does
not tamper is worse than no control: it reports a rejection it never asked for.

**G5. A rule I got wrong on the first attempt.** The link-lane check required `len(kinds) == 1` per sheet, which
rejects a sheet that legitimately mixes internal and external links (`Grid` does). Corrected to: a `none` row
must be alone, and a link sheet may mix both kinds.

**G6. Two independent parsers of the same geometry.** §D-4 and §E-1 exist because trusting the intent table's
`ref` strings would make the checker a mirror of the writer. The disjointness and box arithmetic are recomputed
in Python from the A1 text, so a writer bug in `XlsxMerges.box` shows up as a disagreement rather than as
agreement between two copies of the same arithmetic.

**G7. Read-direction blind spot.** Every earlier pass verified "my writer → someone else's reader". The foreign
package (§E-6) adds "someone else's bytes → my reader", with attribute orders this writer never emits. It passed
on the first run, which is evidence about the reader's design (attribute lookup by name, not position) rather
than a coincidence to celebrate.

## H. Verification — what ran

**H1. Probe and two lanes** (`python3 tools/run_r94_checks.py`):
`R94_OFFICE_OK assertions=98 workbooks=2 sheets=5 merges=4 links=6 relationships=2 print=2 foreign=1`; then
*5 sheet(s) with disjoint merged ranges, 6 hyperlink record(s) over 2 relationship part(s), 2 package(s) with
the rels content type and overrides; openpyxl agreed on 5 sheet(s) and 6 link(s)*; then *2 sheet(s) with
printOptions/pageMargins/pageSetup in order, 3 sheet(s) with no print record; openpyxl agreed on 5 sheet(s)*.

**H2. Negative controls — 53 tests, all green**: 32 structure controls and 21 print controls. They cover missing
and invented records, a wrong `count`, changed / reordered / single-cell / overlapping / out-of-sequence merges,
dropped and invented relationship parts, changed `Target`, dropped `TargetMode`, changed `Type`, duplicate `Id`,
one relationship too many, an internal link given an `r:id`, a changed `location`, an undeclared `tooltip`, a
broken attribute order, a package whose `rels` default or worksheet override was removed, and intent tampering
in both tables; plus every print element removed, a margin dropped / changed / written as `5E-1`, broken
attribute order, a false switch, an out-of-sequence tail, an invented print record, and four kinds of intent lie.

**H3. Regression sweep — 38 runner invocations** (37 from R93 plus R94's own lane) re-executed serially on a
freshly rebuilt sandbox. First pass: **35 rc 0, 3 non-zero** — and all three were *missing prerequisites*, not
product failures. `run_r76_sheet_formats` exited 2 because the pinned `ssf==0.2.2` reference cache was absent
from `$HOME/.cache`; the lane had already printed `R76_SHEET_FORMATS_OK assertions=786 rows=245 refused=6`.
`run_r72_core_zip` exited 1 because the pinned baseline commit `aacae239` was not in the shallow clone.
`run_r60_xlsx_differential` exited 1 with its own instruction, *"First run python3 tools/run_r22_contracts.py
with the host tool environment"*. After restoring the three prerequisites — `pip install --target
$HOME/.cache/fusion-r76-ssf ssf==0.2.2 babel==2.9.1`, `git fetch --depth=200 origin main`, and running R22
first — those four lanes returned **rc 0**: R22 *37 public-consumer assertions PASS*; R76 *239 rows replayed
through the independent ssf implementation: 231 identical, 8 adjudicated differences*; R60 differential
complete; R72 *13 XLSX archives … comparators passed*. **Final tally: 38/38 rc 0.** Two honest caveats: the
three first-pass failures are environment evidence about reproducibility (a new sandbox must re-pin `ssf`,
`openpyxl`, `formualizer` and the `aacae239` baseline before the sweep is meaningful), and the R60/R72 lanes
are re-ordered rather than independent of each other.

**H4. Full host discovery** — `python3 -m unittest discover -s tools -p 'test_*.py'` with `openpyxl`,
`ssf`/`babel`, `formualizer` and `tools/r87node/node_modules` present:
**`Ran 2395 tests … OK`** (R93: 2342; **+53** R94 controls). The 2395 is *executed* tests; the loader builds
2395 and runs all of them. In a bare sandbox the same command reports `Ran 2342 … FAILED (errors=1, skipped=3)`
— the delta is four classes whose `setUpClass`/`setUpModule` cannot construct their reference: `test_r64_formula_caches`
(no `formualizer`) and the three render-lane modules (no `tools/r87node/node_modules`). Those are gated
verifiers, not silent passes: they report the exact install command instead of a green tick.

**H5. Census and audit.** `module_testability.py --check` → **50/50 modules (100.00 %) meet the bounded
testability standard; 0/50 (0 %) accepted**; `native_source_audit.py --check` → **231 requirement records /
36 cross-cutting obligations**, reproducing with three new source files in the inventory.

**H6. Not run / not claimable.** No Android build, emulator, device or provider gate. No Excel, LibreOffice,
browser or printer — `soffice`, `libreoffice` and a headless browser are absent from this host, so there is **no
visual evidence** of a merged rectangle, a clickable link or a paginated preview, and none is claimed.

**H7. Per-module testability, in absolute numbers.** The only module whose content changed is `:format:ooxml`:
66 production files and 231 check artifacts in the census row (was 63/225 in R93), of which this pass adds 3
production files and 6 check artifacts, all hash-pinned. Measured against the repository's own standard,
`:format:ooxml` was and remains one of the 50/50 (100.00 %) modules with a substantial executable testable
subset — and 0 modules are fully specification-accepted, because acceptance needs an application-level witness
no host verifier can supply.

## I. Regressions versus the best prior codebase (R93, `c8ad031`)

No behavioural regression found by the 37 re-run lanes or by the full discovery run. Three risks stated rather
than buried:

* `StyledSheet` gained three trailing defaulted fields (`merges`, `hyperlinks`, `print`) and one extra
  initialiser call (`XlsxMerges.requireDisjoint`). Every construction site in the tree passes named arguments or
  relies on defaults, and the whole module compiles with `-Werror`. The R93 probe's assertion that an unfiltered,
  unmerged, unlinked sheet still ends with `</sheetData></worksheet>` is unchanged and still passes, so the
  no-feature bytes are identical.
* The package grew a part *conditionally*: `xl/worksheets/_rels/sheetN.xml.rels` appears only for sheets with at
  least one external link. A workbook without links has exactly the parts it had before (asserted in the probe
  and in both R93 and R94 lanes).
* `read()` now looks for a worksheet rels part per sheet. That is one more `entries[...]` lookup per sheet; a
  package with a malformed rels part is read as "no link targets" only when the part is absent, and a malformed
  part raises rather than silently dropping links.

## J. Class A, the PR, and the next test

**Class A: still 0 applications.** `r94.xlsx` contains a merge the grid must draw as one cell, two external
relationships and two internal jumps, and a print record with all six required margins; openpyxl reproduces all
of it. Whether Excel draws the rectangle as one cell, navigates on a click, or paginates the sheet as declared,
is unobserved.

**The PR.** This turn's instruction was to open a new PR, so the branch is pushed and a PR is opened against
`main`; nothing is merged.

**Next test that would move the frontier.** Open `r94.xlsx` in Excel and record four observations: is `C1:D1` a
single cell whose value survives a click on `D1`; does the `A1` tooltip appear with `& <go>` intact and does the
link open the URL; does `A2` jump to `B1` without a browser; does Print Preview show landscape A4 at 85 % with a
0.5-inch left margin. That single session converts record agreement into behaviour for three features at once.

**Carried, unimplemented:** device-accelerated video upscale and "remove mosaic" — unchanged, and still refused
for the same information-theoretic reason.
