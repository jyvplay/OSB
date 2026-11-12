# R76 — Number-format language rebuilt against an independent implementation, Writer section layout

Host-JVM round on branch `arena/15d3f8f5-osb` (base `9965ba1`). Nothing in this document was produced by Android, an emulator, Word, LibreOffice or Excel. Every number below comes from a command executed in this round; gates that did not run are listed in section J rather than implied.

**Runtime honesty.** Used: pinned Kotlin 2.2.21 (`kotlin-compiler` from the npm registry) and pinned JRE 17 from `tools/bootstrap_host_tools.py`, CPython 3.11 `zipfile`/`zlib`, `python-docx` 1.2.0 (second reader for Writer), `ssf` 0.2.2 + `babel` 2.9.1 (Apache-2.0 Python port of SheetJS — independent reference for number formats), `lxml`. Not available and therefore not claimed: Android SDK/AGP/Gradle, emulator or device, Word, LibreOffice, Excel, any theorem prover, any reviewer other than this one. No external agent reviewed the result; no benchmark is cited as if it were our measurement.

## 0. What changed in production code

| Artifact | Role |
|---|---|
| `format/ooxml/.../NumberFormatRender.kt` | Rebuilt format-section engine: date-only sections truncate to the day, time sections round **once** at second granularity, elapsed `[h]`/`[m]`/`[s]` brackets, `mm:ss` and `[h]:mm:ss`, `h:mm:ss.00` fractional seconds, `mmmmm` month initial, `[$-409]`/`[$\u20ac-407]` locale/currency annotations with the symbol kept and the annotation dropped, trailing-comma thousands scaling, scientific notation normalised before rounding, AM/PM 12-hour boundary. Unsupported shapes are **refused by name**, not approximated. |
| `format/ooxml/.../DocxPackage.kt` | `PageSetup(widthTwips, heightTwips, orientation, margins)`, `Layout(page, header, footer)`, `appendSectPr`/`headerFooterXml` emission (`w:pgSz`, `w:pgMar`, `w:headerReference`, `w:footerReference`, `w:pgNumType`), `readLayout` round trip, running header/footer parts with `{PAGE}`/`{NUMPAGES}` as real `w:fldSimple` fields, and a document written without a `Layout` keeps the byte-identical `<w:sectPr/>`. |
| `testing/src/r76sheet/.../R76SheetFormatChecks.kt` | 790 host assertions over 247 observations, 8 declared refusals, `testing/build/r76formats/artifacts/formats.tsv`. |
| `testing/src/r76writer/.../R76WriterLayoutChecks.kt` | 27 host assertions, four real DOCX packages (`legacy`, `a4-footer`, `letter-landscape`, `mixed-layout`) plus `layout.tsv`. |
| `tools/check_r76_sheet_formats.py` | Independent re-derivation (own date/serial arithmetic, own rounding model) plus replay through `ssf`; refuses to exit 0 without `--require-reference`. |
| `tools/check_r76_writer_layout.py` | python-docx 1.2 **and** raw OPC inspection (Overrides, relationship resolution, field instructions, exact header/footer text); refuses to exit 0 without `--require-python-docx`. |
| `tools/test_r76_sheet_formats.py`, `tools/test_r76_writer_layout.py` | 15 and 13 negative controls that attack the verifiers themselves. |
| `tools/run_r76_sheet_formats.py`, `tools/run_r76_writer_layout.py` | Compile → run → verify in one command; artifact directories are per-round. |
| `.github/workflows/android-r54-office.yml`, `testing/build.gradle.kts` | `:testing:r76SheetFormatContracts`, `:testing:r76WriterLayoutContracts`, both verifiers, both control suites (not yet observed in CI). |

Two R76 readback defects were found **by running the probe rather than by reading the code**, and both are fixed: `readLayout`'s `sectPr` lookup read regex `groupValues[1]` (the attribute *name*, always `"r:id"`) so header/footer always read back as `null`; and `fieldText()` replaced `w:fldSimple` with a bare `{PAGE}` outside any `w:t`, so text extraction silently dropped the token (`"Page  of "`). Section H records the attacks that now pin both.

## A. Formal model

**Objects.** A format section is a finite string over {literals, quoted literals, escapes, `_x`/`*x` fill skips, `[colour]`, `[condition]`, `[locale]`, `[h]`/`[m]`/`[s]`, placeholders `0 # ?`, `.` `,` `%` `E+`/`E-`, date tokens `y m d h s A/P AM/PM @`}. A value is either an IEEE-754 double (number/time/date) or a string (`@`).

**Access model.** Both renderers are pure functions of `(value, formatText)`; no locale database, no clock, no filesystem, no network. The Writer layout is a pure function of `(blocks, Layout, headerText, footerText)`; header/footer text is bounded plain text, never a field the caller cannot see.

**Counted resources.** Writer: ≤ 64 images (unchanged from R75), header/footer ≤ 1024 characters each, margins and page size in twips bounded to `0 … 31 680`, page numbers only through `{PAGE}`/`{NUMPAGES}`. Formats: ≤ 4 sections, ≤ 3 fractional-second digits, elapsed brackets only `[h]`/`[m]`/`[s]`, date serial window `0 … 2 958 465`.

**Success, with quantifier order.** For **every** one of the 247 corpus observations, the candidate's rendered string equals (i) the string produced by our independent Python re-derivation, which computes each row from the serial with `excel_date`, `round_half_up`, `quantize` and `grouped` and stores no expected outputs, and (ii) the string produced by `ssf` **unless** the observation appears in `docs/verification/r76-format-divergences.json` with a reason and a source. For **every** package in the Writer corpus, python-docx's section API and a raw OPC walk agree on page geometry, orientation, margins, header/footer text and field names.

**Parameters / regime / tolerances.** Time rounding is `floor(x*86400 + 0.5)` seconds applied once per observation (`ssf` uses a `1e-7` epsilon on the day fraction; both agree on the corpus except at exact-midnight carry, section G). Dates use the Excel 1900 clock with the fictitious 1900-02-29 as serial 60. Twips are integers; 1 inch = 1440 twips, 1 point = 20 twips. Corpus values span `-1234.5 … 1234.5` plus datetimes 1899-12-31 … 2000-01-01; formats span 2–20 characters.

**Adjacent but explicitly not solved.** Excel's `[>100]`-style conditional sections, per-locale digit shapes, currency rendering for currencies whose symbol is not in the annotation, `*` fill and `_(` alignment width as Excel measures them, the 1904 date system in the renderer, Word's actual pagination, widow control, section breaks beyond the final body section, columns, line numbering, first-page-different headers, and everything about rendering: fonts, hyphenation, justification, or how Word paints a page.

## B. Hypotheses and the pre-declared threshold

Declared in `docs/verification/r76-format-divergences.json` (`scope`) before the final verification run, and enforced by the checker: **a difference between candidate and reference counts as resolved only if it appears in that file with a reason *and* a citable source, and the file may not contain an entry that no longer corresponds to an observed difference.** The Writer gate is stricter than a similarity check: every assertion must be re-derivable from the emitted bytes.

- **H+** — the format language is large but finite in practice, and a table-free implementation that rounds once at second granularity, keeps literals inside the section, and refuses ambiguous shapes agrees with an independent port on essentially all realistic formats.
- **H−** — agreement with a Python port is **not** evidence about Excel or LibreOffice, which we cannot run; and no amount of host evidence makes an untrusted `.docx` safe to open, or makes our page geometry equal Word's pagination. `ssf` is itself a re-implementation, so two implementations can share an ancestor error.
- **H∂** — the boundary is the rounding/carry junction and the section-literal grammar. Exactly at midnight, at `59.999…` seconds, at serial 60, and at `"pre "@` the implementations disagree, and those are the observations that decide whether the model is right.

Threshold set before the run: **≥ 95 % of observations re-derived by our own oracle, ≥ 95 % of the replayed observations identical to `ssf`, every remainder declared with a source, and all negative controls green.** Observed: 241/247 re-derived (97.6 %), 231/239 replayed identical (96.7 %), 8 declared and adjudicated, 8 declared refusals, 28/28 controls green. Threshold met, and the two hypotheses that failed are recorded rather than absorbed.

## C. Frontier after this round

Restated from R75, with R76 movement:

1. **Number/date/time rendering** — moved from "a subset of formats, unverified against anything" to "247 observations, 96.7 % agreement with an independent port, every difference named". Still **not** Excel parity.
2. **Writer page setup and headers/footers** — moved from absent to "page geometry, orientation, margins, header/footer text, PAGE fields and a readback that the probe itself exercises". Still **not** Word rendering.
3. **Writer columns, line numbering, first-page-different, multi-section documents** — untouched; the next structural gap.
4. **Spreadsheet `RANDARRAY`, locale/currency formats, `[>…]` conditions** — untouched, refused by name.
5. **Android image picking / device execution** — untouched; no device in this environment.
6. **Web build (repo-root `package.json` → Vite)** — run in this round: `npm ci` then `npm run build` transformed 1920 modules and emitted `dist/index.html` (225.28 kB, gzip 70.00 kB) in 4.40 s; `npx tsc --noEmit` exits 0. This is a *build* gate only, not office evidence, and the bundle was not opened in a browser here.

## D. Fifteen solution-shaped non-solutions (and the detector for each)

Each row is something a plausible implementation would have done, that R76 rejects on purpose, and the concrete gate that pins the rejection. "Control" names are real `unittest` methods in `tools/test_r76_*.py`; "verifier" names are functions in `tools/check_r76_*.py`.

| # | Non-solution | Detector |
|---|---|---|
| 1 | Round at each token (`h`, `m`, `s`) instead of once, so `59.6 s` becomes `60` | `check_r76_sheet_formats._rederive` rounds once from the serial; a doubly-rounded candidate mismatches rows in `formats.tsv`, and `test_doctored_value_is_rejected` proves a wrong row cannot pass |
| 2 | Truncate a date-only format at the time of day instead of the day, so `yyyy-mm-dd` slips by one near midnight | `_rederive` truncates to the day; `test_wrong_serial_mapping_is_rejected` |
| 3 | Treat `[h]` as `h` (no elapsed accumulation), so `26:00` renders `2:00` | `_rederive` accumulates total elapsed units; `test_doctored_value_is_rejected` |
| 4 | Apply `,` scaling after rounding rather than before, so `0.0,,` of 12 250 000 differs | `_numeric` scales then formats; `test_doctored_value_is_rejected` |
| 5 | Leave `[$-409]` / `[$\u20ac-407]` in the output text, or lose the currency symbol | `test_mangled_euro_symbol_is_rejected` requires the `€` reconstructed from the annotation |
| 6 | Guess a meaning for an ambiguous `00:00` / `0:00` instead of refusing | declared refusals in `r76-format-divergences.json` + `test_unexpected_refusal_is_rejected` and `test_refusal_that_renders_is_rejected` |
| 7 | Accept `ss.0000` and silently drop a fractional-second digit | same refusal pair (`fraction-precision`) |
| 8 | Use the 1904 window, or proleptic Gregorian without the fictitious 1900-02-29 | `excel_date` uses the 1900 clock with serial 60; `test_wrong_serial_mapping_is_rejected` |
| 9 | Emit a `w:headerReference` that points at no relationship, yet still print the header text | verifier `check_package` (`missing reference`) + control `missing_reference_with_a_part_present_is_rejected` |
| 10 | Emit `w:pgSz` with `w:orient="landscape"` but portrait dimensions | control `swapped_orientation_is_rejected` |
| 11 | Reuse one relationship id for two different targets | control `duplicate_relationship_id_is_rejected` |
| 12 | Emit `{PAGE}` as literal text instead of a `w:fldSimple` field | verifier reconstructs field text from `w:t` only; control `unknown_field_instruction_is_rejected` |
| 13 | Drop the legacy empty `<w:sectPr/>` when no `Layout` is supplied (silently changing pinned documents) | verifier `verify` (`the legacy document no longer carries an empty sectPr`), control `legacy_package_gaining_a_header_is_rejected`, plus `run_r60_writer_baseline.py` and `run_r62_writer_baseline.py` (128 byte-identical packages each) |
| 14 | Match header/footer text by prefix or substring, so `"Quarterly report"` passes for `"Quarterly"` | the verifier compares reconstructed text with `!=` — no prefix match anywhere; this is a code property rather than a control, and is why controls 9/15 are constructed to differ only in text |
| 15 | Resolve a relationship target with `split('/')[-1]`, so `media/image1.png` is looked up as `word/image1.png` | target resolution is `posixpath.normpath(join('word', target))`; control `missing_footer_part_is_rejected` |

## E. Six mechanism-distinct branches considered

1. **Table-free grammar interpreter, refuse the rest** (chosen): predictable, auditable, and every refusal is visible in the corpus as a refusal count.
2. A port of a full format engine: rejected — vendoring an implementation contradicts "original implementation" and would make the differential vacuous.
3. Full `ssf`-equivalent including `[>…]` conditions: rejected this round — condition semantics interact with section selection in ways we cannot verify without Excel.
4. Date rendering off the JVM `java.time`: rejected as the primary path because Android's `java.time` on old API levels is desugared and the 1900 clock has no `java.time` representation; the arithmetic is done on `Long` serials instead.
5. Writer layout through `w:sectPr` in the final paragraph vs. a separate `w:sectPr` part: both are legal; chosen final-body `sectPr` because it is what `python-docx` and Word both read and it keeps one section model.
6. Headers as separate parts with own relationships (chosen) vs. inline text in the body: the separate-part route is the only one that survives a reader.

## F. Second-order adversary per candidate

For each surviving candidate, "what would make this pass while being wrong?"

- **Formats.** An adversary who knows the corpus could special-case the 247 pairs; the defences are (a) the independent re-derivation computes each row arithmetically from the serial (`excel_date`/`round_half_up`/`quantize`/`grouped`) and stores no expected output strings — though its switch is keyed on exactly the formats in the corpus, so a format the verifier does not know cannot be verified and is therefore not in the corpus, (b) the 15 controls mutate the corpus, (c) the same production code path is exercised by `R75SheetFractionChecks` (282 assertions) and the R73 `TEXT` function, so a table lookup would have to be consistent across three lanes with different inputs. Residual risk: a corpus that only contains formats we already thought of — declared, not solved.
- **Writer layout.** An adversary could write header text into the body and pass a naive text check; the verifier therefore reads `w:headerReference` → relationship → part → `w:t`, and cross-checks the extracted set of parts against the package's Overrides (`undeclared-footer` and `stray-package` controls: a part that is not declared in `[Content_Types].xml`, or a footer part with text that matches but no `w:footerReference`, both fail).
- **Both.** An adversary could weaken the verifier; that is why each verifier has a control suite that must fail on tampered input, and why both verifiers hard-fail when their dependency is missing (`--require-python-docx`, `--require-reference`).

## G. Independent external verification

- **Number formats**: `ssf` 0.2.2 (Apache-2.0, PyPI, a Python port of SheetJS `ssf.js`) replayed 239 of the observations; 231 matched. The eight differences are four formats in two rule classes and are recorded with reasons and sources in `docs/verification/r76-format-divergences.json`: (1) section literals around `@` — ECMA-376 §18.8.31 defines a section as literals interleaved with placeholders, `ssf` drops them; (2) AM/PM at an exact-midnight carry — `ssf` reports `12:00 PM`, we report `12:00 AM`, and midnight is the start of the AM half. **Neither is verified against Excel**, because Excel was not available; the file says so in `verified_against_excel: false` on every entry.
- **Writer layout**: `python-docx` 1.2.0 (MIT) as a second reader for the section API, plus a raw `zipfile`+`lxml` OPC walk that does not share code with either the writer or python-docx. The two readers must agree with each other and with the emitted bytes.
- **What this is not**: independent *authority*. Both references are re-implementations. Agreement is evidence of not being idiosyncratically wrong; it is not evidence of spreadsheet or word-processor parity.

## H. Repaired candidates re-run through every gate, plus a patch-specific attack

Two production defects were found and repaired in this round. Each repair was re-run through the full lane, and each has an attack that would have caught the original defect:

| Repair | Gate re-run | Patch-specific attack |
|---|---|---|
| `readLayout` used `groupValues[1]` (attribute name) | `R76WriterLayoutChecks` 27/27, `check_r76_writer_layout.py` 4 packages, 13/13 controls, R60/R62 baselines 128 byte-identical each | `check_r76_writer_layout.py` control `missing-reference`: a package whose `sectPr` references a relationship id that does not exist must fail; a `groupValues[1]` readback returns `null` for *every* package, which the `a4-footer`/`letter-landscape` assertions now catch (they require non-null, exact text). |
| `fieldText()` emitted `{PAGE}` outside `w:t` | same lane | control `unknown-field`: a `w:fldSimple` with instruction `{DATE}` must fail; the text-extraction reconstruction requires the token to be inside `w:t`, so a bare `{PAGE}` yields `""` and the assertion `"Page 1 of 3"` fails. |

Both repairs were verified against the **legacy** path as well: `run_r60_writer.py` → 675 assertions / 256 DOCX files with the baseline `ab4840b…` hash matching exactly, and `run_r62_writer_baseline.py` → 128 byte-identical packages. The section-layout code did not perturb the documents that existed before R76.

## I. Visual inspection and census

- Visual: `android/docs/verification/r75-writer-images-decoded.png` remains the only decoded-image inspection (R75). R76 produced **no** rendered page and no screenshot — we have no Word, LibreOffice or viewer that renders DOCX here, and pretending a `layout.tsv` dump is a visual inspection would be exactly the kind of claim this round is meant to avoid. The R76 artifacts (`testing/build/r76writer/artifacts/*.docx`, `layout.tsv`) are inspectable by a human with Word; we mark that as **not done**.
- Census, regenerated this round from `tools/module_testability.py`: **50/50 modules (100.00 %) have a substantial testable subset; 0/50 (0.00 %) are fully accepted against the original spec.** R76 raised the *depth* of `:format:ooxml` (new behaviours, two new verifiers, 28 new controls), not the accepted count.
- Native source audit: 231 recorded requirements, 36 cross-cutting obligations, all native main-source package ownership checked — a reproducibility check, not a completeness proof.

## J. Honest stopping point and the next highest-information test

Gates that **did not** run and are therefore not claimed: Gradle/AGP build of the two new tasks, the CI workflow (wired, unobserved), any Android device or emulator, Word/LibreOffice/Excel, any network surface, the office-interop lanes that need a device.

The next highest-information test, in order:

0. **The clearest next observation** is (1) below, but note that neither the web bundle nor `dist/index.html` was rendered in a browser this round — the only claim is that the build and the type check succeed.
1. **Open `testing/build/r76writer/artifacts/letter-landscape.docx` and `a4-footer.docx` in Word or LibreOffice** and report the page size, orientation, margins and the header/footer text that the application shows. That single observation converts the largest remaining "believed correct" claim into evidence, and it is cheap for a human with a desktop.
2. **Open `testing/build/r76formats/artifacts/formats.tsv` observations in Excel** for the eight adjudicated differences — specifically `h:mm AM/PM` at `0.999999` and `"pre "@`. Two cells settle both.
3. Run the wired CI workflow and confirm `:testing:r76WriterLayoutContracts` and `:testing:r76SheetFormatContracts` execute there (currently only host-verified).

Until (1) and (2) exist, the correct statement is: **the Writer layout and the number-format language are verified against independent re-implementations on the host, and against nothing that renders or is Excel.**
