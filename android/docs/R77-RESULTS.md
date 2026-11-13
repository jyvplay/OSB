# R77 — Conditional number formats, Writer section controls, and the restore of the branch state

Host-JVM round on branch `arena/15d3f8f5-osb`. Nothing below was executed by Android, an emulator, Word, LibreOffice or Excel. Where a gate did not run, section J says so instead of implying it did.

**Runtime honesty.** Used: pinned Kotlin 2.2.21 + JRE 17 from `tools/bootstrap_host_tools.py` (re-installed this round after the sandbox cache was wiped), CPython 3.11 `zipfile`/`zlib`/`lxml`, `python-docx` 1.2.0, `ssf` 0.2.2 + `babel` 2.9.1 (Apache-2.0 Python port of SheetJS), `formualizer` 0.13.0 at an earlier round, Node 22 with `npm ci` + `vite build` + `tsc --noEmit`. Not available: Android SDK/AGP/Gradle, emulator or device, Word, LibreOffice, Excel, a theorem prover, a second human reviewer. No parallelism, no external agent, no benchmark cited as our measurement.

## 0. Restoring the branch before doing anything

The working tree arrived holding the full R75/R76 content while `HEAD` sat on the merge base `9965ba1`. The restore was mechanical and is recorded here rather than described: `git add -A` staged the tree, `git diff --cached FETCH_HEAD` was **empty** (byte-identical to the remote tip `471d452`), then `git reset --hard FETCH_HEAD` moved `HEAD` back to the branch tip. Nothing was lost, nothing was guessed, and `git status` is clean before and after this round's work. The web side was then rebuilt from the repository's own scripts — `npm ci` (exit 0) and `npm run build` → `dist/index.html` 225.28 kB (gzip 70.00 kB) from 1920 modules in 3.03 s — and `npx tsc --noEmit` exits 0.

## 0b. What changed in production code

| Artifact | Role |
|---|---|
| `format/ooxml/.../NumberFormatRender.kt` | Conditional sections: `Condition`/`parseCondition` read the leading bracket run of a section (at most one numeric condition per section), `chooseSection` evaluates conditions in source order and falls back to the trailing unconditional section, `classicSection` keeps the R76 sign rules intact, and `stripAnnotations` now *removes* condition annotations instead of refusing the format. Refusals: two conditions in one section, a condition after an unconditional section, more than two conditions, scientific-notation or empty thresholds, and "no condition matched while every section carries one". |
| `format/ooxml/.../DocxPackage.kt` | `Layout.columns`, `columnSpaceTwips`, `pageNumberStart`, `sectionType` (`nextPage`/`continuous`/`evenPage`/`oddPage`), emitted in CT_SectPr order (`w:type` before `w:pgSz`, `w:pgNumType` before `w:cols`) and only when they differ from Word's defaults, plus a full `readLayout` round trip for each. Invariants: 1–45 columns, spacing bound, restart in 1…1 000 000, and a continuous section cannot restart page numbering. |
| `testing/src/r77sheet/.../R77SheetConditionChecks.kt` | 207 host assertions over 60 observations (10 conditional formats, 2 unconditional controls, 8 refusals). |
| `testing/src/r76sheet/.../R76SheetFormatChecks.kt` | Migrated: bracket conditions are no longer refused here, because R77 implements them and owns their corpus. 786 assertions, 245 observations. |
| `testing/src/r76writer/.../R76WriterLayoutChecks.kt` | Extended to 44 assertions and 6 packages: `columns-restart-odd` (3 columns, 360 twip gutter, restart at 5, odd-page section) and `continuous-columns` (2 columns, default gutter, continuous). |
| `tools/check_r77_sheet_conditions.py` | Independent re-derivation of the conditional grammar, a control differential against `ssf` on the unconditional rows, and a re-executed demonstration that `ssf` ignores bracket conditions. Refuses to exit 0 without `--require-reference`. |
| `tools/check_r76_writer_layout.py` | Now verifies `w:type`, `w:cols`, `w:pgNumType`, CT_SectPr child order, and python-docx's own `section.start_type`. |
| `tools/test_r77_sheet_conditions.py`, `tools/test_r76_writer_layout.py` | 15 and 16 negative controls. |
| `docs/verification/r77-condition-divergences.json` | Declared semantics with sources, four pinned `ssf` observations, eight refused shapes, and the residual risk. |
| `.github/workflows/android-r54-office.yml`, `testing/build.gradle.kts` | `:testing:r77SheetConditionContracts`, the R77 verifier and its control suite. **Observed in CI** (run `43147966121`, job `office`): step 9 `Compile Android and run actual JVM contracts` and step 14 `Independent document readers and formula comparison` both succeed, so the new Gradle task, the verifier and all 15 controls executed green on a clean machine; the job fails only at steps 19 and 21, the device-only gates that failed identically before this round. |

Two production defects were found by *running* gates rather than reading code, and both are fixed: `Layout`'s first draft allowed `CONTINUOUS` + page-number restart (contradictory, and the probe refused to build the document), and the verifier's `w:type` message evaluated `section_type.get(...)` eagerly, so a *passing* check crashed on a `None` element — a gate that cannot pass is as bad as one that cannot fail.

## A. Formal model

**Objects.** A format is a finite string of up to four `;`-separated sections, each a sequence of literals, quoted literals, escapes, `_x`/`*x` skips, bracket annotations (colour, locale/currency, elapsed `[h]`/`[m]`/`[s]`, numeric condition) and placeholders. A value is an IEEE-754 double or a string. A Writer section is a `Layout(pageSetup, header, footer, columns, columnSpaceTwips, pageNumberStart, sectionType)`.

**Access model.** Both renderers are pure functions of `(value, format)`. The Writer layout is a pure function of `(blocks, Layout)`; no file, URL, provider or ambient authority is touched, and `readLayout` reads only the bounded parts of a package it just wrote.

**Counted resources.** At most 2 conditions per format; a condition threshold is a decimal literal; per-section output ≤ `MAX_OUTPUT_CHARS`; Writer columns ≤ 45; column spacing and page geometry in twips inside the existing bounds; page-number restart ∈ 1…1 000 000.

**Success, with quantifier order.** For **every** observation in the 60-row conditional corpus, the production string equals the independent Python re-derivation; for **every** row of the 245-row unconditional corpus, the production string equals the re-derivation and, for the replayed subset, `ssf` except where a divergence is declared; for **every** package in the six-package Writer corpus, python-docx and a raw OPC walk agree with the emitted bytes on geometry, orientation, margins, header/footer text, field names, section type, column count/spacing and page-number restart.

**Parameters / regime / tolerances.** Values `-1234.567 … 2000000`; twips are integers with 1440 per inch; page numbering starts at 1; condition thresholds are parsed as `Double` from a decimal literal only (no exponent, no locale separator). No tolerance is applied to string comparison anywhere: equality is exact.

**Adjacent but different problems (explicitly not solved).** Excel's `[>100][Red]` interaction with the *fourth* (text) section, per-locale decimal separators inside conditions, condition thresholds written as fractions or dates, `*` fill and `_(` width as Excel measures them, Word's actual pagination, first-page-different headers (`w:titlePg`), multi-section documents (this writer still emits exactly one section), line numbering, and every rendering question (fonts, hyphenation, justification, what Word paints).

## B. Outcome space and the declared threshold

Threshold declared before the verification runs, and enforced by the tools: **every conditional observation re-derived (100 %), every unconditional control row matching `ssf` exactly, every pinned `ssf` limitation observation reproducing, every Writer field checked by two independent readers, and all controls green.**

- **H+** — the conditional grammar is small enough to implement exactly, and two independent implementations (Kotlin production, Python re-derivation) agree on all 60 observations.
- **H−** — this cannot be *verified* as Excel behaviour: the only outside implementation available, `ssf`, ignores bracket conditions outright, so no independent authority checks the selection rule. Class A for the spreadsheet is therefore not achievable in this environment, regardless of how many rows we add.
- **H∂** — the boundary is the sign rule and the "no section matches" corner. `#,##0;#,##0` hides the sign of negatives (three independent Excel references), so a conditional section must not invent one; and a fully-conditioned format that matches nothing has no documented rendering, so it is refused rather than guessed.

Observed: 60/60 re-derived, 7/7 control rows identical to `ssf`, 4/4 pinned observations reproduced, 44 Writer assertions across 6 packages, 16/16 Writer controls, 15/15 condition controls. Threshold met; the impossibility in H− is recorded, not papered over.

## C. Frontier

1. **Conditional formats** — from "refused by name" to "implemented, re-derived in full, reference limitation demonstrated". Still not Excel-verified.
2. **Writer section controls** — columns, restart, section type added with two-reader verification; multi-section documents, `titlePg`, and line numbering remain.
3. **Everything else** — unchanged from R76: `RANDARRAY`, locale digits, `[>…]` combined with text sections, device execution, image picking, web runtime.

The open interface between achievement and impossibility is exactly: *an outside implementation that evaluates bracket conditions*. Excel is one; LibreOffice another; neither exists here, and `ssf` provably does not (`[>100]0.00` at 50 returns `50.00`).

## D. Fifteen solution-shaped non-solutions and their detectors

| # | Non-solution | Detector |
|---|---|---|
| 1 | Evaluate conditions in *numeric* order rather than source order | `test_r77_sheet_conditions` corpus row `[>100]0.00;-0.00` at 100 (must take the fallback) |
| 2 | Treat the *last* section as the answer when nothing matches | same row plus the refusal control `[>100]0.00;[<0]0.00` |
| 3 | Auto-sign negative values inside conditional sections | row `[Red][<0]-0.00;[Green][>=0]+0.00` and `[>=1000]#,##0;[<0]0.00;0.0` at −3.5 → `3.50` |
| 4 | Let a colour annotation reach the text (`[Red]` printed as `Red`) | rows for `[Red][<100]0;[Blue][>=100]0` |
| 5 | Keep the locale annotation visible (`[$-409]1,234`) | existing R76 divergence entry + `[$\u20ac-407][>1000]…` rows |
| 6 | Accept scientific thresholds (`[>1e3]`) by calling `toDouble` | control `test_scientific_threshold_is_rejected` |
| 7 | Accept `[>=]` as "greater than zero" | corpus refusal `[>=]0.0;0` |
| 8 | Let a condition follow an unconditional section (dead condition) | control `test_condition_after_unconditional_section_is_rejected` |
| 9 | AND two conditions inside one section silently | control `test_two_conditions_in_one_section_are_rejected` |
| 10 | Treat `[h]` as a condition | R76 elapsed-time rows still pass through `stripAnnotations` unchanged |
| 11 | Emit `w:cols` even for one column (changes every existing package) | legacy baselines: 128 byte-identical packages each for R60 and R62 |
| 12 | Emit `w:type w:val="nextPage"` (legal but not the default) | control `test_section_type_on_a_next_page_section_is_rejected` + readback equality |
| 13 | Emit `w:pgNumType` before `w:pgMar` (schema-invalid order) | verifier's CT_SectPr order check on every package |
| 14 | Trust python-docx alone for section type | raw OPC check of `w:type` in `check_package` *and* the python-docx `start_type` cross-check |
| 15 | Let the verifier silently pass when `ssf` is missing | `--require-reference` + control `test_absent_reference_downgrades_when_allowed_and_fails_when_required` |

## E. Six mechanism-distinct branches

1. **Prefix-condition grammar with a trailing fallback** (chosen): smallest state, every refusal is nameable.
2. Full universal condition language (any comparison anywhere, AND/OR): rejected — Excel defines at most two conditions and the interaction with the text section is undocumented here.
3. Transpile the format to a small regex and match: rejected — a regex cannot express ordinal selection with a fallback, and it hides which section was chosen.
4. Vendor a conditions-aware engine: rejected — contradicts "original implementation" and makes the differential vacuous.
5. Evaluate conditions in the *verifier only* and let production keep refusing: rejected — that is a test of a feature nobody ships.
6. Represent sections as a decision table generated at parse time: rejected as equivalent to (1) with more state to keep consistent (and `data class` equality on `Condition` already gives determinism).

For the Writer, the branches were: (a) new parts for a first-page header, (b) attribute-only controls inside the existing `sectPr` (chosen), (c) separate `w:sectPr` per section (needs a multi-section document model, out of scope this round).

## F. Second-order adversary

- **Against conditions**: a corpus-fitting implementation. Defences: the re-derivation computes from the grammar, the controls mutate the corpus, and the same production path is exercised by the R76 unconditional corpus (245 rows) and the R73 `TEXT` lane. Residual risk: both implementations are mine, so a shared misreading of the sign rule would agree with itself — mitigated only by the three cited Excel references, which is why `verified_against_excel` is `false` in the record.
- **Against the Writer lane**: a verifier that accepts whatever XML is present. Defences: exact values from an intent table written by the *producer* probe, double reading (raw OPC + python-docx), refuse-by-default on unexpected packages, and controls that change one number each (`w:num="3"→"4"`, deleting `w:pgNumType`, inserting `w:type`).
- **Against the harness**: missing dependencies. Both verifiers fail closed (`--require-reference`, `--require-python-docx`), and the controls prove it.

## G. Independent external verification

- **Unconditional number formats**: `ssf` 0.2.2, replayed exactly; 7 control rows identical, 231 of 239 replayed rows identical in the R76 lane with 8 adjudicated differences.
- **Conditional number formats**: no independent implementation exists in this environment. `ssf`'s blindness is *demonstrated* by four pinned observations re-executed on every run (`[>100]0.00` at 50 → `50.00`; `[>100]0.00;-0.00` at −5 → `--5.00`; `[>=1000]#,##0;[<0]0.00;0.0` at −3.5 → `3.50`; `[Red][<100]0;0` at 150 → `150`). The conditional claim is therefore **specification-derived**, and the record says so.
- **Writer sections**: `python-docx` 1.2.0 and a raw `zipfile`+`lxml` OPC walk, which share no code with the writer.
- **Legacy parity**: `run_r60_writer.py` 675 assertions / 256 packages against the pinned `ab4840b…` source, and `run_r62_writer_baseline.py` 128 byte-identical packages.

## H. Repairs, re-run through every gate

| Repair | Gates re-run | New attack aimed at the patch |
|---|---|---|
| `Layout` allowed `CONTINUOUS` + page-number restart | Writer lane (44 assertions), 16 controls, both legacy baselines | `refuses("continuous section that restarts numbering")` in the probe |
| Verifier crashed on a *passing* `w:type` check (eager `None.get`) | Writer lane, 16 controls | `test_section_type_on_a_next_page_section_is_rejected` (inserts `w:type` into a `nextPage` package and requires the named failure) |
| R76 corpus carried two formats R77 now renders | R76 lane (786 assertions, 245 rows), R76 verifier, `r76-format-divergences.json` (`superseded` entry) | the removal is visible: the R76 lane's refusal count dropped 8 → 6 and the record names the supersession |

## I. Visual inspection and census

- Visual: **no new rendering evidence**. The Writer packages were not opened in Word or LibreOffice, and the web bundle was not opened in a browser; `layout.tsv` is an observation table, not an inspection. The only decoded-image artifact in the repository remains `docs/verification/r75-writer-images-decoded.png`.
- Census, regenerated from `tools/module_testability.py`: **50/50 modules (100.00 %) have a substantial testable subset; 0/50 (0.00 %) are fully accepted against the original spec.** R77 raised the depth of `:format:ooxml` again (conditions, Writer section controls, 18 new controls (15 condition controls + 3 Writer controls)) without moving the accepted count — and the reason is structural, not effort: acceptance needs Word/Excel/device evidence this environment cannot produce.
- Native source audit: 231 requirements / 36 obligations reproduced.
- Full `tools` suite: **1735 tests, 22 skipped, 0 failures** after regenerating the two artifacts this round invalidates (`module-testability.json` and the native source report).

## J. Can the Writer and Spreadsheet be Class A this turn?

No, and the honest reason is short: every remaining Class A criterion in `docs/FULL-SPEC-REVIEW.md` needs an observer we do not have — Word or LibreOffice opening the `.docx` (pagination, section breaks, column balance, header rendering), Excel opening the `.xlsx` (format application, recalculation), and an Android device executing the app. R77 adds two capabilities and their verification, and it adds the *demonstration* that the outside reference cannot check the harder one. Claiming Class A here would be exactly the false claim the spec forbids.

Gates that did not run: any Android device, Word/LibreOffice/Excel, any browser render. The new Gradle task, the R77 verifier and its 15 controls **did** run in CI (run `43147966121`, job `office`, steps 9 and 14 success).

**Next highest-information test**, in order: (1) open `testing/build/r76writer/artifacts/columns-restart-odd.docx` in Word/LibreOffice and report the column count, gutter, page-number start and section break; (2) type `[>100]0.00;-0.00` with 100 and −5, and `[Red][<0]-0.00;[Green][>=0]+0.00` with 0, into Excel and report the four cells. Two human observations would convert the largest "believed correct" claims of the last three rounds into evidence.
