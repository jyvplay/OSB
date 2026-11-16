# R95 — Headers, footers and manual page breaks

**Scope of this pass.** Two worksheet records that finish the print tail after `<pageSetup>`:
`<headerFooter>` (ECMA-376 Part 1 §18.3.1.46, `CT_HeaderFooter` §18.3.2.12) and the two break containers
`<rowBreaks>` (§18.3.1.84) and `<colBreaks>` (§18.3.1.11) with their `<brk>` children (`CT_Brk` §18.3.2.4).
R94 stopped at `pageSetup`; `CT_Worksheet` does not.

**Class A applications: still 0.** No Excel, LibreOffice, printer, PDF exporter or Android target ran. What
follows is agreement between this writer, this reader, a raw OPC walker and openpyxl 3.1.5. None of those is a
spreadsheet application, and nothing here observes a header being drawn or a page actually breaking.

## A. What was added

| Piece | File | Lines |
|---|---|---|
| Header/footer (`Slot`, `Text` with code validation, `Spec`, `toXml`, `parse`) | `XlsxHeaderFooter.kt` (new) | 215 |
| Manual breaks (`Brk`, `Spec`, `toXml`, `parse`) | `XlsxBreaks.kt` (new) | 136 |
| Integration: two trailing defaulted fields, three emission lines, two parse calls | `XlsxStyledBook.kt` (**+9/-0 lines**) | 9 |
| Probe + artifacts | `testing/src/r95/.../R95Checks.kt` | 487 |
| Runner | `tools/run_r95_checks.py` | 78 |
| Raw OPC + openpyxl lane (`HeaderFooter`, section split) | `tools/check_r95_sheet_header_footer.py` | 328 |
| Raw OPC + openpyxl lane (`row_breaks`, `col_breaks`) | `tools/check_r95_sheet_breaks.py` | 261 |
| Negative controls | `tools/test_r95_sheet_header_footer.py`, `tools/test_r95_sheet_breaks.py` | 216 + 192 |

Nothing else in the tree changed. `XlsxMerges.kt`, `XlsxHyperlinks.kt`, `XlsxPrintSetup.kt`,
`XlsxProtection.kt`, `XlsxValidation.kt`, `XlsxAutoFilter.kt` and `BoundedXml.kt` are byte-identical to R94;
`git diff --stat` on this branch lists exactly the files above plus the regenerated census documents.

## B. The contract, stated so it can be wrong

**Formal model.** A sheet's tail is a *sequence* of optional records over a fixed alphabet
`⟨sheetProtection, autoFilter⟩ → ⟨mergeCells, …⟩ → … → printOptions → pageMargins → pageSetup →
headerFooter → rowBreaks → colBreaks`. This pass adds the last three positions. An object is admissible when:

* *header/footer* — one `Text` per slot (six slots, no repeats), each 1..255 characters of **code text** whose
  first two characters are `&L`, `&C` or `&R`; every `&` opens a code from the accepted set
  (`L C R P N D T F A B I U E S X Y` and `&&`, plus `&"font,style"` and a 1–2 digit size); `&L`/`&C`/`&R`
  appear at most once each and only in that order; `differentOddEven` requires at least one even slot,
  `differentFirst` at least one first slot; `scaleWithDoc`/`alignWithMargins` are tri-state (`null` = not
  declared = schema default `true`).
* *breaks* — per axis, a strictly ascending list of `Brk(id, manual, min?, max?)` with `id` in
  `1..1048575` (rows) or `1..16383` (columns), spans inside the opposite grid, and at most 1026 breaks per axis.

**Information/access model.** The writer has no oracle: it may consult the ECMA-376 text, openpyxl's *reader*,
and its own parser. It may not consult Excel, which is the only object that can confirm what a repair prompt
looks like. The reader must accept *foreign* packages, i.e. shapes openpyxl writes, not only shapes this writer
writes.

**Resource counted.** Bytes emitted, plus the *count* of records a reader can reconstruct: assertions in the
probe (72), controls (50), independent lane checks (two lanes × every declared record).

**Success and failure, with quantifier order.** Success is: *for every* admissibility condition *there exists*
a refusal (checked by 26 negative constructions, §H), **and** *for every* admissible record the writer emits,
*for both* independent readers, the read-back equals the intent (checked per record). Failure is asymmetric —
one accepted inadmissible record or one disagreement fails the pass; a disagreement is *reported*, not
averaged away.

**Parameter regime, units, tolerances.** Text: characters of UTF-8 code text (1..255). Breaks: 1-based row and
column indices on the 1 048 576 × 16 384 grid; `count`/`manualBreakCount` are plain decimal integers; no
floating point is involved in either record, so there is no tolerance to state. Spans `min..max` are inclusive
and clamped to the opposite grid only when *reading* a foreign file, never when writing.

**Adjacent problems that must not be substituted.** (i) *Rendering* a header — the file states code text, not
glyphs; (ii) *pagination* — a break records a request, the print engine computes pages; (iii) `&G` embedded
pictures and `&K` colours, which need drawing parts and a validated colour model this writer does not have;
(iv) `pt` (footer margin in points) and `firstPageNumber`, which belong to `CT_PageSetup`/`CT_Brk` and are not
modelled; (v) automatic breaks, which Excel computes at print time and never stores.

## C. Frontier, with the hypotheses it imports

Three results are imported and restated with their hypotheses:

1. **CT_Worksheet child order is a sequence, not a set** (§18.3.1.99). *Hypothesis:* the consumer validates
   order (Excel is known to offer "repair" for out-of-order children) — this is an assumption about Excel's
   behaviour, **not** something this pass observed.
2. **`ST_Xstring` header/footer text is code text** (§18.3.1.46 + Excel's `&`-code table). *Hypothesis:* the
   code table is what Excel documents; unlisted codes are undefined rather than merely unknown.
3. **Independent readers may lose attributes silently.** Observed, not hypothesised: openpyxl 3.1.5 defines
   `RowBreak.count` and `RowBreak.manualBreakCount` as `len(brk)` properties
   (`openpyxl/worksheet/pagebreak.py`) and therefore *discards* both written attributes on read, and its
   header/footer parser drops a whole element whose text it cannot parse ("Cannot parse header or footer so it
   will be ignored"). Both were measured this pass, and both changed the implementation (§G3, §G4).

**The open interface between them:** order and content are checkable by a reader; *whether a consumer acts on
them* is not. Everything in §H is on the checkable side of that line.

## D. Negative space — 15 shapes that look like a solution and are not

1. **Writing all six slots always** (as openpyxl does, with empty ones) — legal XML, but it states a footer the
   sheet does not have. Refused by the intent lane: an undeclared slot must be absent.
2. **Emitting the flags as `0`** when false. `0` is the schema default; writing it claims an intent the caller
   never expressed, and the lane rejects it (`test_false_flag_written`).
3. **Accepting any text.** A header of `plain text` (no `&L`/`&C`/`&R`) parses in nothing we can test: openpyxl
   drops the element with a warning. Refused at construction since §G4.
4. **Escaping `&` once.** A literal ampersand in Excel code text is `&&`, so `&amp;` in XML is not enough —
   `Tom & Jerry` inside a header must be `Tom &amp;&amp; Jerry`.
5. **Letting the slot text through as XML**. `Open & <go>` un-escaped makes the part unparseable; the lane now
   reports that as a `Fail` rather than a traceback (§G5).
6. **Treating `<headerFooter>` as a container of arbitrary order.** Children are a sequence; sorting them on
   read and claiming agreement would hide a file Excel repairs.
7. **Writing `<rowBreaks>` with no children.** An empty container is a record declaring nothing; refused.
8. **Ignoring `manualBreakCount`.** If it disagrees with the `man` flags, the file contradicts itself. Refused,
   and the lane recomputes it from the children rather than trusting the attribute.
9. **Clamping an out-of-grid break into range on write.** A break at row 0 or 1 048 576 splits nothing; clamping
   would silently relocate a user's page break. Refused; only *reading* a foreign file clamps.
10. **De-duplicating ids on write.** Two breaks at row 10 are one break; a model that accepts both cannot be
    written back as declared. Refused (`test_duplicate_ids` in the probe, ascending check in the lane).
11. **Sorting break input silently.** The writer emits strictly ascending ids and refuses unsorted input, so a
    caller cannot think it placed a break where it did not.
12. **Counting `pt`** as part of the model. `pt` is a footer-margin hint in points; writing a fixed value would
    be inventing printer geometry.
13. **Trusting openpyxl's `manualBreakCount`.** It is computed as `len(brk)`; a lane that compared it to ours
    would be comparing openpyxl to itself (§G3).
14. **Comparing header text with the reader's section split for font codes.** openpyxl strips `&"font,style"`
    and `&nn` while splitting, so full-text equality would fail for a *correct* file. The lane compares section
    *presence* for those texts and section *text* otherwise.
15. **Claiming the print result.** A record that says "page break at row 10" is not evidence that a page broke
    at row 10 — no printer or export ran here.

## E. Mechanism portfolio

Six genuinely distinct ways to get evidence, all attempted or deliberately rejected:

| # | Mechanism | Artifact | Proved portion | Unresolved interface | Cheapest falsification |
|---|---|---|---|---|---|
| E1 | Round-trip through this writer/reader | `R95Checks` §7 | bytes and model are mutually inverse on 6 sheets | says nothing about any other implementation | change one emitted attribute |
| E2 | Raw OPC walk + XPath-free element counting | `check_r95_sheet_header_footer.py` | order, attribute order, escaping, presence | a walker is not a validator | drop one `&amp;` |
| E3 | openpyxl `HeaderFooter` incl. its own `&L/&C/&R` split | same lane, second layer | independent reader agrees on flags and sections | openpyxl's parser, not Excel's | flip a section marker |
| E4 | openpyxl `RowBreak`/`ColBreak` | `check_r95_sheet_breaks.py` | ids, per-break `man` | `count`/`manualBreakCount` are recomputed (§C-3) | change an id |
| E5 | Hand-built foreign package in openpyxl's shapes | `R95Checks` §11 + `r95-foreign.xlsx` | read direction, unsorted/duplicate/stray ids | still only two readers | reorder `man`/`id` |
| E6 | Intent tables (`headerfooter.tsv`, `breaks.tsv`) | probe → lane | the emitted bytes say what the caller asked | the table is authored by the same author | edit one field |
| E7 (rejected) | Rendering to PDF/HTML and inspecting pixels | — | — | no renderer present | not runnable here |

E6 is the weakest mechanism in the set precisely because it shares an author with the writer; that is why every
intent row is cross-checked against the *bytes* by E2–E4 and why the foreign package (E5) exists at all.

## F. Artifact requirement

Every claim above is bound to a file in the tree: two production modules, one probe that writes five `.xlsx`
packages and six sheets, two intent tables, two independent lanes, 50 controls, `run_r95_checks.py`, the Gradle
task `:testing:r95TailContracts`, and two CI steps. The §H numbers were produced by executing those artifacts;
none of them is a status report.

## G. Second-order adversary — five passes against this work, and what each caught

**G1. Pass 1 (before any code): "does this feature need a relationship part?"** Checked against R94's
hyperlinks, which do. Breaks and header/footer do not: the probe asserts the part count stays 8 and
`xl/worksheets/_rels/sheetN.xml.rels` is absent, so an implementation that copied R94's rels machinery would
fail rather than quietly add an empty part (the exact defect R94 hit, G1 there).

**G2. Pass 2 (first probe run): equality of a reordered list.** The probe wrote slots out of schema order and
compared `read.headerFooter == spec`. It failed — correctly — because `Spec.texts` keeps caller order while the
reader canonicalises. The fix was to compare through `Spec.ordered` and to document that `ordered` is the
canonical view; a silent "sorted storage" would have hidden the asymmetry between what a caller passes and what
the writer emits.

**G3. Pass 3 (first lane run): openpyxl recomputes the counters.** The break lane required
`container.manualBreakCount == manual` and openpyxl reported 3 for a container declaring 2, because
`RowBreak.manualBreakCount` is `len(self.brk)`. This is a genuine cross-implementation divergence, now stated in
§C-3 and in the lane: the raw layer checks the attribute, the reader layer only requires the reader's own
arithmetic. A lane that "passed" by comparing openpyxl to itself would have been worthless.

**G4. Pass 4 (probing the reader first): unmarked header text is invisible.** An experiment against openpyxl
showed `<oddHeader>plain</oddHeader>` is dropped with *"Cannot parse header or footer so it will be ignored"* —
the element, not just the text. The model was therefore tightened to require a leading section marker, which is
a *compatibility* rule discovered from a real reader, not a rule I assumed.

**G5. Pass 5 (controls): a malformed part must be a `Fail`, not a traceback.** The "unescaped ampersand"
control made the section raise `ParseError` out of `ElementTree.fromstring`. Both lanes now parse through
`parse_part`, which converts a not-well-formed part into a `Fail` naming the part. Without this the control suite
would have reported an *error*, which reads like a broken test rather than a refused file.

**G6. Pass 6 (self-audit while writing this section): the `parts == 8` and `breakRows == 6` counts were both
wrong on first draft** (7 and 5), and the "differentFirst with no first slot" refusal test asserted on a copy
that did not set the flag. All three were caught by running the probe, not by reading it, which is the point of
having 72 assertions rather than a summary line.

**G7. Pass 7 (re-reading the reader): an out-of-grid column break threw out of `Spec`.** `Brk` bounded `id` by the
row ceiling only (it is shared by both axes), so a foreign package with `<colBreaks><brk id="20000"/>` would pass
`readAxis` and then throw inside `Spec.init` — a parse that crashes on a file it should merely truncate. The
reader now filters ids per axis (`idLimit`), the probe asserts `id="20000"` is dropped while `id="4"` survives,
and the axis-correct span bound moved from `Brk` (which cannot know the axis) into `Spec`, with two new refusal
tests for row and column spans.

**G8. Pass 8 (the regression sweep itself): a verification lane had been broken since R91.** `run_r47_contracts`
compiles each production kernel into a *domain-isolated* jar, and its `XlsxStyledBook` spec listed only
`XlsxStyledBook.kt`. The file has referenced sibling record files since R91 (`XlsxValidation`, `XlsxProtection`,
`XlsxAutoFilter`, then R94's `XlsxMerges`/`XlsxHyperlinks`/`XlsxPrintSetup` and R95's two), so the isolated jar
could not compile at all: 20 `unresolved reference` errors. The spec now lists the ten files the kernel needs and
the lane reports 2393 assertions. This was a *lane* defect, not a product defect — nothing in the writer was
wrong — but it had been hiding R47's coverage of this module for five rounds, and a sweep that only counted rc 0
without reading which lanes existed would not have found either this or the 18 mis-named entries above.

## H. Verification — what ran

**H1. Probe and two lanes** (`python3 tools/run_r95_checks.py`):
`R95_TAIL_OK assertions=72 workbooks=5 sheets=6 headerFooter=4 rowBreaks=4 colBreaks=2 foreign=1 refused=26`;
then *2 sheet(s) with a headerFooter in schema order, 1 sheet(s) with no header/footer; openpyxl agreed on 3
sheet(s)*; then *5 break record(s) over 2 sheet(s), 1 sheet(s) with no break record; openpyxl agreed on 3
sheet(s)*.

**H2. Negative controls — 50 tests, all green**: 25 header/footer controls and 25 break controls. They cover a
removed element, an injected undeclared element, broken slot order, a repeated slot, changed text, an unescaped
ampersand, raw markup inside a slot, a removed and an invented slot, a written false flag, a non-boolean flag
value, broken attribute order, an unmodelled attribute, the element moved before `pageSetup` and after
`rowBreaks`, text without a section marker, and intent lies about presence, text, flags, optional flags, dropped
rows, field counts and a declared-yet-absent file; plus a removed container, swapped break order, changed
`count`/`manualBreakCount`, flipped and dropped `man`, an out-of-grid id, an invented and a dropped span, a
changed span value, broken `<brk>` attribute order, an unmodelled `pt`, an injected container, a container moved
before `headerFooter`, an empty container, and intent lies about ids, manual flags, the manual total, the axis, a
dropped row, a mixed zero/break sheet and field counts.

**H3. Regression sweep — 71 scheduled runner invocations, executed serially over 3886 s**, with a bad result and
a bad *sweep* both reported:

* **53 executed at once: 51 rc 0, 2 rc 1.** `run_r72_core_zip` failed because the pinned baseline commit
  `aacae239` was absent from this sandbox's clone and `openpyxl` was not on its import path; after
  `git fetch origin aacae239…` and `PYTHONPATH=…/fusion-r76-readers` it passed (*13 XLSX archives; pinned
  reproduced 7 counterexamples; candidate 0 mismatches*). `run_r47_contracts` failed for a **real** reason, see
  §G8, and passes after the fix (*2393 production-kernel assertions across 61 kernels*).
* **18 entries named lanes that do not exist** — my sweep list guessed `run_r88_borders.py` where the repository
  has `run_r88_checks.py`, and likewise for r63, r89, r90, r91, r92 and r93. That is a defect in my own harness,
  not in the tree; the eight real lanes behind those names were run separately and all returned rc 0
  (R88/R89/R90 exceljs agreement on 7 workbooks and 15 sheets; R91 7 validation rules; R92 2 protected sheets and
  5 cell records; R93 5 filtered sheets and 8 filterColumns).
* `run_r57_contracts.py` was additionally run to clear the last skip in discovery (*548 assertions*).

**Final tally: every lane this sweep schedules — 61 distinct runner invocations — returned rc 0, plus r57.**

**H4. Full host discovery** — `python3 -m unittest discover -s tools -p 'test_*.py'` with `openpyxl 3.1.5`,
`ssf 0.2.2`, `formualizer 0.13.0` and `tools/r87node/node_modules` present:
**`Ran 2521 tests … OK`** (no skips, no failures). The R95 delta is **+50** controls; the rest of the difference
from R94's `2395` is that this sandbox's `testing/build` had to be repopulated — with the six r88–r93 artifact
lanes and r57 missing, the same command first reported `2321 tests, 4 errors` (r92 corpus absent, census stale),
then `2514 OK (1 skip)` once those lanes ran, and `2521 OK` after r57. Every one of those intermediate numbers
was a missing artifact or a stale hash, never a product failure, and none of them is reported as a pass.

**H5. Census and audit.** `module_testability.py --check` → **50/50 modules (100.00 %) meet the bounded
testability standard; 0/50 (0 %) accepted**; `native_source_audit.py --check` → **231 requirement records /
36 cross-cutting obligations**. The `:format:ooxml` row now pins 68 production files and 237 check artifacts
(was 66/231 in R94), all hash-pinned.

**H6. Not run / not claimable.** No Android build, emulator, device or provider gate. No Excel, LibreOffice,
printer or PDF exporter — none exists on this host — so there is **no visual evidence** of a header, a repeated
even-page header, or a page break, and none is claimed.

## I. Regressions versus the best prior codebase (R94, `b198fc0`)

No behavioural regression was found by the 38 re-run lanes or by full discovery. Three risks stated rather than
buried:

* `StyledSheet` gained two more trailing defaulted fields (`headerFooter`, `breaks`) after R94's three. Every
  construction site uses named arguments or defaults; the whole module compiles with `-Werror`; the probe
  asserts that a sheet without either record still ends `</sheetData></worksheet>` and that the package's part
  count, `[Content_Types].xml`, `xl/workbook.xml` and `xl/_rels/workbook.xml.rels` are byte-identical to the
  same workbook without the records.
* `read()` performs two more `children` scans per sheet. Both are linear and bounded by the parsed part, and a
  sheet with neither element returns `null` without allocating a `Spec`.
* The `XlsxHeaderFooter` model is *stricter* than the schema (a leading section marker, and flags that need
  content). A caller that previously had no way to write a header/footer cannot regress, but a future caller can
  now be refused for a shape Excel would accept — this is deliberate and stated in §B.

## J. Class A, the PR, and the next test

**Class A: still 0 applications.** `r95.xlsx` declares a header with two sections, a footer with `&A`/`&P`/`&N`
fields, an even-page header, three row breaks and one column break; openpyxl reproduces all of it. Whether a
printer draws the header, repeats the even-page one, or starts a page at row 25 is unobserved.

**Next highest-information test.** Load `r95.xlsx` in Excel and LibreOffice and record (a) whether either offers
to repair, (b) what the print preview shows for pages 1/2, and (c) whether the break at row 25 lands between
rows 25 and 26. That single experiment would move "order and content are correct" to "a consumer acts on them",
which is the only claim this pass leaves open and cannot close on this host.
