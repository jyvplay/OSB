# R79 — Writer even-page headers and footers, minimal-diff round

Host-JVM round on branch `arena/15d3f8f5-osb`. No Android device, emulator, Word, LibreOffice or Excel took part. The mandate was minimal: one capability, the smallest possible diff, nothing removed, and a byte-level proof that nothing else moved.

**Runtime honesty.** Used: pinned Kotlin 2.2.21 + JRE 17 (`tools/bootstrap_host_tools.py install`, re-run because sandbox caches are excluded from snapshots), CPython 3.11 `zipfile`/`lxml`, `python-docx` 1.2.0, `ssf` 0.2.2 + `babel` 2.9.1, `openpyxl` 3.1.5, ImageMagick 6.9 (through the R75 lane), Node 22 (`npm ci`, `vite build`, `tsc --noEmit`). Not available: Android SDK/AGP/Gradle, device, Word, LibreOffice, Excel, theorem prover, second reviewer. No parallelism, no external agent, no borrowed code.

## 0. Restore (asked for first)

`HEAD` arrived on the merge base `9965ba1` with the R75–R78 tree uncommitted. Restore was verified, not assumed: `git add -A`, then `git diff --cached FETCH_HEAD` printed **nothing** (tree byte-identical to the branch tip `fe8dc2f`), then `git reset --hard FETCH_HEAD`. Web side rebuilt from the repo's own scripts: `npm ci` exit 0, `npm run build` → `dist/index.html` 225.28 kB (gzip 70.00 kB), `npx tsc --noEmit` exit 0.

## 0b. What changed

| Artifact | Change |
|---|---|
| `format/ooxml/.../DocxPackage.kt` | `Layout` gains `evenPageHeader`/`evenPageFooter`. When either is set the writer emits `w:headerReference`/`w:footerReference` of `w:type="even"`, the parts `word/header3.xml` / `word/footer3.xml`, their relationships and content-type overrides, **and** `word/settings.xml` containing `<w:evenAndOddHeaders/>` with its own relationship (`rIdSet1`) and content type — because Word ignores even/odd parts without that setting. `readLayout` also returns both even-page texts. All new parameters default to `null`; a layout that does not use them produces identical bytes (no settings part is written). |
| `testing/src/r76writer/.../R76WriterLayoutChecks.kt` | +1 package (`even-odd`), +8 assertions (63 total, 8 packages), +2 refusals (blank even-page footer, unknown field token in an even-page header). |
| `tools/check_r76_writer_layout.py` | Intent table 18 → 20 fields; references verified per type including `even` (at most one of each); the settings part must exist **iff** an even-page part does, must be declared in `[Content_Types].xml`, must be reached by exactly one relationship of type `settings`, and must contain `w:evenAndOddHeaders`; python-docx now also checks `even_page_header` and `even_page_footer`. |
| `tools/test_r76_writer_layout.py` | +4 controls (settings part removed, `w:evenAndOddHeaders` removed, even reference downgraded to `default`, changed even-page text) → **24/24**. |

No other production file was edited. `NumberFormatRender.kt`, the R76/R77 sheet lanes, the R73 formula lane and the web app were untouched.

## A. Formal model

**Objects.** A Writer section is `Layout(pageSetup, header, footer, columns, columnSpaceTwips, pageNumberStart, sectionType, firstPageHeader, firstPageFooter, evenPageHeader, evenPageFooter)`. A package is a ZIP of `[Content_Types].xml`, `_rels/.rels`, `word/document.xml` + rels, styles, numbering, an optional settings part, and up to six header/footer parts.

**Access model.** The writer stays a pure function of `(blocks, Layout)`; no filesystem, URL, provider, clock or locale table. `readLayout` reads only bounded parts of a package this writer produced.

**Counted resources.** ≤ 1024 characters per text (six now); ≤ 8 relations from `document.xml` for headers/footers/settings; existing page and column bounds unchanged.

**Success, with quantifier order.** For **every** package in the eight-package corpus: the raw OPC walk and python-docx agree with the emitted bytes on geometry, orientation, margins, section type, columns, page-number restart, and each declared header/footer text; each reference type (`default`, `first`, `even`) exists at most once and resolves to a part with the right content type; `w:titlePg` is present iff a first-page text is declared; `word/settings.xml` with `w:evenAndOddHeaders` is present iff an even-page text is declared; sectPr children follow CT_SectPr order. For **every** package in the R60 and R62 legacy corpora (128 each) the bytes are unchanged.

**Parameters / regime / tolerances.** Twips as before; string equality exact; byte equality exact. No tolerance is applied anywhere.

**Adjacent but different problems.** Word's actual even/odd page rendering, mirror margins (`w:mirrorMargins`), distinct odd-only headers (there is no `odd` reference type — odd pages use `default`), multi-section documents, and any pagination question. None is claimed.

## B. Outcome space and threshold

Threshold declared before the run: **all eight packages verified by two readers, 24 controls green, both legacy baselines byte-identical, whole suite green.** Met.

- **H+** — the change is additive and local: a reference type, two parts, and the one document setting that makes them meaningful. Existing bytes cannot move because the emitter branches on presence and the new parameters default to `null`.
- **H−** — this cannot show that Word or LibreOffice *uses* the even header on page 2, nor that page 1 renders as expected. No page-layout engine exists here. Class A for Writer remains unreachable for that reason alone.
- **H∂** — the boundary is the settings part: emitting even-page parts without `w:evenAndOddHeaders` is schema-valid XML that a word processor ignores, so the verifier tests the **iff** (parts ⇔ setting) rather than mere presence, and the probe asserts the converse on a first-page-only document.

## C. Frontier

R79 moves "even-page header/footer" from absent to emitted and verified by two independent readers, with the enabling setting. Still open: mirror margins, multi-section documents, line numbering, and every rendering question. The open interface is unchanged: an application that lays out pages.

## D. Fifteen solution-shaped non-solutions

| # | Non-solution | Detector |
|---|---|---|
| 1 | Emit `w:type="even"` references without `word/settings.xml` | control `test_missing_settings_part_is_rejected`; verifier `even-page parts declared but word/settings.xml is absent` |
| 2 | Emit `settings.xml` without `w:evenAndOddHeaders` | control `test_settings_without_even_and_odd_is_rejected` |
| 3 | Emit `settings.xml` unconditionally (grows every package) | `unexpected word/settings.xml without an even-page part` + both legacy baselines |
| 4 | Reuse `header1.xml`/`header2.xml` for the even header | part-name collision; round-trip equality and the per-type reference checks |
| 5 | Downgrade the even reference to `w:type="default"` | control `test_even_reference_downgraded_to_default_is_rejected` (`2 default references`) |
| 6 | Swap the texts of default and even headers | `even_header text … != declared` |
| 7 | Skip the content-type override for `header3.xml` / `settings.xml` | `no content-type override for …` |
| 8 | Skip the settings relationship and rely on the part existing | `0 settings relationships, expected 1` |
| 9 | Point the settings relationship at `word/document.xml` | `settings relationship targets …` |
| 10 | Return the default header as the even header | round-trip equality (`readLayout(bytes) == layout`) |
| 11 | Read the first reference of the tag and call it even (pre-R78 behaviour) | same round-trip equality |
| 12 | Trust python-docx alone | raw OPC walk is a separate reader; both must agree with the declared intent |
| 13 | Trust the raw walk alone | python-docx `even_page_header`/`even_page_footer` must show the literal text |
| 14 | Accept a blank or unknown-token even header | probe refusals `blank even-page footer`, `unknown field token in an even-page header` |
| 15 | Add a settings part but leave it unreferenced | relationship check plus `unexpected packages without a declared intent` |

## E. Six mechanism-distinct branches

1. **Additive `Layout` fields + settings part only when needed** (chosen: smallest diff, no new concepts, byte-identical when unused).
2. Always write `settings.xml` (simpler helper, but changes every package): rejected by the legacy baselines.
3. Encode even/odd in `PageSetup`: rejected — it is content, not geometry.
4. A separate `SectionExtras` type: rejected — forces call-site churn for one field pair.
5. Multi-section document model: the correct long-term shape, out of scope for a minimal-diff round.
6. Put `w:evenAndOddHeaders` in a `w:docSettings`-like custom part: rejected — Word would not read it.

## F. Artifacts

`testing/build/r76writer/artifacts/even-odd.docx` (real package with `header1/3`, `footer1/3`, `settings.xml`), `layout.tsv` (20 columns), the eight-package corpus, the verifier report, the 24 controls, both legacy baselines, and this document.

## G. Second-order adversary

The producer writes both the package and the intent table, so the strongest attack is a self-consistent lie. Defences: python-docx is an independent reader and must show the same literal text; the intent table is compared exactly; four controls each mutate one byte-string (dropping the settings part, dropping the setting element, changing one reference type, changing one character of text); refuse-by-default on unexpected packages. Residual risk: our reading of *whether* Word requires `w:evenAndOddHeaders` in `settings.xml` — that is documentation- and practice-derived (python-docx exposes `even_page_header` only when the section carries an `even` reference, while Word additionally needs the setting), and it stays a declared, not observed, claim.

## H. Verification

- `run_r76_writer_layout.py`: `R76_WRITER_LAYOUT_OK assertions=63 documents=8`; verifier: **8 packages (7 with page geometry) verified with python-docx 1.2 and raw OPC inspection**.
- `test_r76_writer_layout.py`: **24/24** controls.
- R60 baseline: 675 assertions, 256 packages, pinned `ab4840b…`, **128 byte-identical**. R62 baseline: **128 byte-identical**.
- R74 writer links: 20 checks. R75 writer images: ImageMagick decoded the embedded media.
- R76 formats: 245 rows, 239 replayed, 231 identical, 8 adjudicated. R77 conditions: 60/60 re-derived, 7/7 `ssf` controls, 4/4 pinned.
- Full `tools` suite: **1743 tests OK** (22 skipped). Census: **50/50 (100.00 %) substantial testable subset, 0/50 (0.00 %) fully accepted**.

## I. Repairs and regressions vs the previous turn

One defect was introduced by this round's first draft and fixed before any gate was declared green: the missing-settings control asserted a message that never fires, because the relationship check reports `targets missing part` *before* the settings existence check. The control now asserts the message the verifier actually emits, which is the stronger failure.

**Regressions vs the R78 codebase: none observed.** R60 128/128 identical; R62 128/128 identical; writer lane 55 → 63 assertions, all green; writer controls 20 → 24, all green; R76 formats unchanged (245 rows, 231 identical, 8 adjudicated); R77 conditions unchanged (60/60, 7/7, 4/4); R74 20 checks; R75 decoded media; suite 1739 → 1743 tests; census unchanged at 50/50 — 0/50. Nothing was removed, weakened or de-emphasised; the only production edit is additive with `null` defaults.

## J. Class A, stopping point, next test

**Still not Class A.** Writer needs a word processor to open the package; Spreadsheet needs Excel to recalculate; the app needs a device. R79 adds one real capability with two-reader verification, which is what a minimal-diff round can honestly deliver.

Gates not run: CI on this commit at the time of writing (below), any device, Word/LibreOffice/Excel, any browser render.

**Next highest-information test:** open `testing/build/r76writer/artifacts/even-odd.docx` in Word or LibreOffice and report page 1, 2 and 3 headers and footers. That one observation decides whether the even/odd wiring is honoured, which no host reader can answer.
