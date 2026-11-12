# R78 — Writer different-first-page sections, minimal-diff round

Host-JVM round on branch `arena/15d3f8f5-osb`. Nothing below was executed by Android, an emulator, Word, LibreOffice or Excel. The mandate this round was explicitly *minimal*: add one capability, change as few lines as possible, remove nothing, and prove no regression against R77.

**Runtime honesty.** Used: pinned Kotlin 2.2.21 + JRE 17 (`tools/bootstrap_host_tools.py install`, re-run because the sandbox cache is excluded from snapshots), CPython 3.11 `zipfile`/`lxml`, `python-docx` 1.2.0, `ssf` 0.2.2 + `babel` 2.9.1, `openpyxl` 3.1.5, ImageMagick 6.9 (via the R75 lane), Node 22 (`npm ci`, `vite build`, `tsc --noEmit`). Not available: Android SDK/AGP/Gradle, a device, Word, LibreOffice, Excel, a theorem prover, a second reviewer. No parallelism, no external agent, no borrowed code.

## 0. Restore (asked for first)

`HEAD` arrived on the merge base `9965ba1` with the whole R75–R77 tree in the working directory. Restore was verified, not assumed: `git add -A` then `git diff --cached FETCH_HEAD` printed **nothing** (the tree was byte-identical to the branch tip `2d8bb36`), then `git reset --hard FETCH_HEAD`. The web side was rebuilt from the repository's own scripts: `npm ci` exit 0, `npm run build` → `dist/index.html` 225.28 kB (gzip 70.00 kB), `npx tsc --noEmit` exit 0.

## 0b. What changed (and what deliberately did not)

| Artifact | Change |
|---|---|
| `format/ooxml/.../DocxPackage.kt` | `Layout` gains `firstPageHeader` and `firstPageFooter`. When either is present the writer emits `w:headerReference`/`w:footerReference` of `w:type="first"`, the parts `word/header2.xml` / `word/footer2.xml`, their two relationships and two content-type overrides, and `<w:titlePg/>` after `w:cols` (CT_SectPr order). `readLayout` now resolves a reference **by `w:type`** instead of taking the first matching tag, and returns both first-page texts. Everything else is untouched: every new parameter defaults to `null`, so a layout that does not use it produces identical bytes. |
| `testing/src/r76writer/.../R76WriterLayoutChecks.kt` | +1 package (`first-page`), +11 assertions (55 total, 7 packages), +3 refusals (blank first-page header, unknown field token, oversized first-page header). |
| `tools/check_r76_writer_layout.py` | Intent table 16 → 18 fields; references verified per type (at most one of each); `w:titlePg` present **iff** a first-page part is declared and absent otherwise; `w:titlePg` added to the CT_SectPr order check; python-docx cross-checks `different_first_page_header_footer`, both containers, and the literal text of each (python-docx cannot see `w:fldSimple` text, so the raw OPC check owns the field tokens). |
| `tools/test_r76_writer_layout.py` | +4 controls (title-page element removed, first reference downgraded to `default`, changed first-page text, missing first-page part) → 20/20. |

No production file outside `DocxPackage.kt` was edited; `NumberFormatRender.kt`, the sheet lanes, the R73/R74/R75 lanes and the web app were not touched.

## A. Formal model

**Objects.** A Writer section is `Layout(pageSetup, header, footer, columns, columnSpaceTwips, pageNumberStart, sectionType, firstPageHeader, firstPageFooter)`; a package is a ZIP of `[Content_Types].xml`, `_rels/.rels`, `word/document.xml`, its rels, styles, numbering, and up to four header/footer parts.

**Access model.** The writer remains a pure function of `(blocks, Layout)`: no file, URL, provider or clock. `readLayout` reads only bounded parts of a package this writer produced.

**Counted resources.** ≤ 1024 characters per header/footer text (four texts now), ≤ 6 relationships from `document.xml` for headers/footers, existing page/column bounds unchanged.

**Success, with quantifier order.** For **every** one of the seven packages in the corpus: the raw OPC walk and python-docx agree with the emitted bytes on geometry, orientation, margins, section type, columns, page-number restart, and each declared header/footer text; `w:titlePg` is present exactly when a first-page text is declared; every reference of either type resolves to a part with the right content type; the sectPr children follow CT_SectPr order. For **every** package in the R60 and R62 legacy corpora (128 each), the bytes are unchanged from the pinned sources.

**Parameters / regime / tolerances.** Twips as before; no tolerance anywhere: string equality is exact, byte equality is exact.

**Adjacent but different problems.** Word's actual title-page rendering, odd/even-page headers (`w:evenAndOddHeaders`, two more reference types), multi-section documents, first-page page-number suppression (`w:pgNumType` on a first page), and any layout question — none of these is claimed.

## B. Outcome space and threshold

Threshold declared before the run: **all seven packages verified by two readers, all 20 controls green, both legacy baselines byte-identical, and the whole `tools` suite green.**

- **H+** — adding a reference type and a part is a local, additive change; existing bytes cannot move because every new field defaults to `null` and the emitter branches on presence.
- **H−** — this cannot show that Word *renders* a title page differently, or that a page-number restart interacts correctly with `titlePg` in Word's pagination. No writer or reader of rendered pages exists here.
- **H∂** — the boundary is `w:titlePg`: emitting it without a first-page part (or omitting it while declaring one) is schema-legal but semantically wrong, and python-docx exposes exactly that distinction, so the verifier checks the *iff* rather than the presence.

## C. Frontier

R78 moves "different first page" from absent to emitted-and-verified-by-two-readers. Still open: even/odd headers, multi-section documents, line numbering, and all rendering. The open interface is unchanged and is not closable in this environment: an outside application that lays out pages.

## D. Fifteen solution-shaped non-solutions

| # | Non-solution | Detector |
|---|---|---|
| 1 | Emit `w:titlePg` unconditionally | verifier `titlePg present without a first-page part` on `a4-footer`, `letter-landscape`, `columns-restart-odd`, `continuous-columns`, `mixed-layout` |
| 2 | Emit the first-page part but forget `w:titlePg` | control `test_title_page_element_removed_is_rejected` |
| 3 | Emit `w:headerReference` twice with `w:type="default"` | control `test_first_reference_downgraded_to_default_is_rejected` (`2 default references`) |
| 4 | Reuse `header1.xml` for both default and first | part-name reuse would leave `header2.xml` absent; control `test_missing_first_page_part_is_rejected` |
| 5 | Skip the content-type override for `header2.xml` | `no content-type override for word/header2.xml` |
| 6 | Skip the relationship and inline the text | `first_header (type first) reference does not resolve` |
| 7 | Read the first reference of the tag and call it the header (the R76 behaviour) | round-trip equality: `readLayout(bytes) == layout` fails because `firstPageHeader` would be `null` |
| 8 | Return the default text as the first-page text | same round-trip equality |
| 9 | Put `w:titlePg` before `w:cols` | CT_SectPr order check |
| 10 | Trust python-docx alone | raw OPC walk is a separate reader; the text check compares against the *declared* intent, not against what python-docx saw |
| 11 | Trust the raw walk alone | python-docx `different_first_page_header_footer` and both containers must agree |
| 12 | Let `{PAGE}` disappear from python-docx and call the text "equal" | the raw check reconstructs the exact text including tokens; python-docx is compared against the token-stripped literal, explicitly |
| 13 | Accept a blank first-page header and emit an empty part | probe refusals `blank first-page header`, `unknown field token`, `oversized` |
| 14 | Change the writer for layouts that do not use the feature | R60 and R62 baselines: 128 byte-identical packages each |
| 15 | Add the parts but leave them unreferenced (dead parts) | `unexpected packages without a declared intent` and the per-type reference checks |

## E. Six mechanism-distinct branches

1. **Additive fields on `Layout` + type-filtered reference lookup** (chosen; smallest diff, no new concepts).
2. A separate `SectionLayout` type composed of `Layout`: rejected, it would force every call site to change.
3. A flag `titlePage: Boolean` plus one text: rejected, Word allows a first-page header without a first-page footer, so one text is not enough.
4. Emit first-page parts as a *variant of the default part* with a shared writer helper: equivalent to (1) but hides which relationship is which; rejected.
5. Store the first-page texts in `PageSetup`: rejected, they are content, not geometry.
6. Multi-section document model (list of `Layout`s): the correct long-term shape, out of scope for a minimal-diff round; recorded as the next structural step.

## F. Artifacts

`testing/build/r76writer/artifacts/first-page.docx` (real package), `layout.tsv` (18 columns), the seven-package corpus, `tools/check_r76_writer_layout.py` output, the 20 controls, the two legacy baselines, and this document.

## G. Second-order adversary

The strongest attack is a verifier that accepts the bytes because the *producer* wrote both the package and the intent table. Defences: (a) python-docx is an independent reader and its container objects are consulted for existence, linkage and text; (b) the intent table is compared *exactly*, so an implementation that writes "Cover" where "Cover footer" was declared fails; (c) four controls mutate exactly one byte-string each, including the case that removes `w:titlePg` and the case that downgrades the reference type; (d) refuse-by-default on unexpected packages. Residual risk: a shared misreading of *where* `w:titlePg` belongs — mitigated only by the CT_SectPr order check, which is itself our reading of the schema.

## H. Verification

- `run_r76_writer_layout.py`: `R76_WRITER_LAYOUT_OK assertions=55 documents=7`; verifier reports **7 packages (6 with page geometry) verified with python-docx 1.2 and raw OPC inspection**.
- `test_r76_writer_layout.py`: **20/20** controls.
- `run_r60_writer.py` + baseline: 675 assertions, 256 packages, pinned hash `ab4840b…`, **128 byte-identical**.
- `run_r62_writer_baseline.py`: **128 byte-identical**.
- `run_r73_formula.py`: 371/371 matched; differential 0 unexpected.
- `run_r74_writer_links.py`: 20 independent checks; `run_r75_writer_images.py`: ImageMagick decoded the embedded media.
- `run_r76_sheet_formats.py`: 245 observations, 239 replayed, 231 identical, 8 adjudicated. `run_r77_sheet_conditions.py`: 60/60 re-derived, 7/7 controls match `ssf`, 4/4 pinned observations reproduce.
- CI run `43161901992`, job `office`: step 9 `Compile Android and run actual JVM contracts` and step 14 `Independent document readers and formula comparison` both **succeed** (the seven-package writer lane, the 20 controls and all R73–R77 lanes ran on a clean machine); the job fails only at steps 19 and 21, the device-only gates that fail identically on every round.
- Full `tools` suite: **1739 tests OK** (22 skipped). Census: **50/50 (100.00 %) substantial testable subset, 0/50 (0.00 %) fully accepted**.

## I. Repairs and regressions vs the previous turn

Two defects were introduced *by this round's own first draft* and fixed before any gate was declared green:

| Defect | Fix | Attack that pins it |
|---|---|---|
| The `appendSectPr` patch silently did not apply (the anchor did not match the file), so first-page parts were written but never referenced — the probe's round-trip check caught `firstPageHeader=null` | re-applied against the real text | `readLayout(bytes) == layout` plus `w:titlePg` presence |
| The new reference messages inserted "default" into the old strings, breaking two R76 controls for the *wrong* reason | default-type messages restored to the R76 wording, first-type messages labelled with the type | the two original controls plus `test_first_reference_downgraded_to_default_is_rejected` |

**Regressions vs the R77 codebase: none observed.** Enumerated: R60 baseline 128/128 identical, R62 baseline 128/128 identical, R76 writer lane 55 assertions green (was 44 — increased), writer controls 20/20 (was 16 — increased), R76 sheet lane unchanged at 245 rows/231 identical/8 adjudicated, R77 condition lane unchanged at 60/60, R73 371/371, R74 20 checks, R75 decoded media, full suite 1739 tests OK (was 1735 — increased by the four new controls), admission census unchanged at 50/50 — 0/50. No function, module, feature or semantic was removed, weakened or de-emphasised; the only production edit is additive with `null` defaults.

## J. Class A, stopping point, next test

**Not Class A, and not close.** The Writer still has no observer that renders; the Spreadsheet has no observer that recalculates; the app has no device. R78 adds one real capability and its verification, which is what a minimal-diff round can honestly deliver.

Gates not run: any device, Word/LibreOffice/Excel, any browser render.

**Next highest-information test:** open `testing/build/r76writer/artifacts/first-page.docx` in Word or LibreOffice and report what page 1 shows versus page 2 (different first page, header text, footer text, page number on the first page). That single observation decides the largest open question in the Writer lane.
