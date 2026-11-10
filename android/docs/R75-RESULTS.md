# R75 — Writer inline pictures and spreadsheet fraction formats

Host-JVM round on branch `arena/15d3f8f5-osb` (base `9965ba1`). Nothing here was run on Android, in Word, in LibreOffice or in Excel. Every number below comes from a command executed in this round; unexecuted gates are listed in section J instead of being implied.

**Runtime honesty.** Available and used: pinned Kotlin 2.2.21 compiler from the npm `kotlin-compiler` registry package and a pinned JRE17 (`tools/bootstrap_host_tools.py`), `python-docx` 1.2.0, `openpyxl` 3.1.5, `formualizer` 0.13.0, `lxml`, GNU `zipfile`/`zlib` from CPython 3.11, and **ImageMagick 6.9** as a third, non-Python decoder of the embedded media. Not available: Android SDK/AGP/Gradle, an emulator or device, Word, LibreOffice, Excel, any theorem prover, any network service under test. No parallelism was used, no external agent reviewed this, and no result is a citation of someone else's benchmark.

## 0. What changed in production code

| Artifact | Role |
|---|---|
| `format/ooxml/.../DocxPackage.kt` | `Image` (content-addressed raster: PNG/JPEG detected by magic bytes, IHDR/SOF dimension measurement from the header, 512 KiB and 4096 px bounds), `ImageFormat`, `Block.Picture(image, alt, widthEmu, heightEmu)` with a 40 M EMU extent bound, binary package parts, one `word/media/imageN.<ext>` per **distinct** image, `…/relationships/image` rels after the hyperlink ids, `wp:inline` DrawingML emission, reader support for `w:drawing` (one picture per paragraph, `wp:anchor` refused, part/relationship/extension/magic cross-checks), and namespace declarations emitted only when used |
| `format/ooxml/.../StructuredWriterDocx.kt` | `write(…, images: Map<String, ByteArray>)`: a line that is exactly `![alt](key)` becomes a picture when the caller supplies that key; the writer performs **no** file, URL or provider access |
| `format/ooxml/.../NumberFormatRender.kt` | Fraction formats: mixed (`# ?/?`, `0 ??/??`), improper (`?/?`), fixed denominator (`# ?/16`, `?/8`), 1–4 placeholder digits, `%`, literals; nearest fraction under the placeholder-derived bound with ties to the smaller denominator and a carry on `p == q`; grouping, decimals, scientific notation inside a fraction remain refused |
| `testing/src/r75writer/kotlin/…/R75WriterImageChecks.kt` | 39 host assertions, four real DOCX packages (index, mixed, links+images, escaped alt/explicit extents) |
| `testing/src/r75writer/kotlin/…/R75SheetFractionChecks.kt` | 282 host assertions, 136 observations |
| `tools/check_r75_writer_images.py` | generic OPC/DrawingML consistency validator + corpus expectations (8 independent checks); python-docx used as a second reader, never the only one |
| `tools/test_r75_writer_images.py` | 13 negative controls that build packages by hand |
| `tools/check_r75_sheet_fractions.py`, `tools/test_r75_sheet_fractions.py`, `tools/run_r75_*.py` | exhaustive oracle, 8 oracle controls, one-command runners |
| `.github/workflows/android-r54-office.yml`, `testing/build.gradle.kts` | `:testing:r75WriterImageContracts`, `:testing:r75SheetFractionContracts`, both verifiers and both control suites (not yet observed in CI) |

## A. Formal model

**Objects.** A Writer document is a finite `Document(blocks)` where `blocks` now ranges over `Heading | Paragraph | ListItem | BlockQuote | CodeBlock | Table | PageBreak | Picture`. `Picture` carries an `Image(bytes, format, widthPx, heightPx)` whose bytes came from the caller. A package is a ZIP of parts with `[Content_Types].xml`, `_rels/.rels`, `word/document.xml`, `word/_rels/document.xml.rels`, styles, numbering, and `word/media/*`.

**Access model.** The writer is a pure function: no filesystem, no network, no ContentResolver, no ambient authority, no image decoding beyond the header. A caller that wants pictures supplies bytes it already holds; a caller that supplies none gets byte-identical legacy output. Reading a foreign package touches only bounded ZIP parts and bounded XML, and refuses macros, floating anchors, escaping targets and mismatched magic.

**Counted resources.** ≤ 64 distinct images per document; ≤ 512 KiB per image; ≤ 4096 px per edge; ≤ 40,000,000 EMU per extent; ≤ 1024 characters of alt text; ≤ 256 ZIP entries per package on read (the R75 checker additionally rejects any package with more than 64 parts); ≤ 1 MiB per part; existing `MAX_HYPERLINKS = 512`. For fractions the counted quantity is the denominator bound encoded by the format itself (`?` → 9, `??` → 99, `???` → 999, `????` → 9999, or the literal digits).

**Success, with quantifier order.** For every package `P` this writer emits (`∀` over the three corpus packages and the 12 hand-built tamperings): every `a:blip` resolves to exactly one declared image relationship; every relationship target stays inside the package; every media part's extension agrees with its magic bytes; every `wp:extent` equals the header dimensions × 9525 EMU; every distinct image appears exactly once as a part; and the read-back block list equals the written block list. For fractions: for every observation `(x, fmt)` in the 136-row corpus, the rendered string equals the independently computed nearest fraction under the placeholder bound, with the padding implied by `0`/`#`/`?`.

**Parameters / regime / tolerances.** Values are IEEE doubles rendered through `BigDecimal`; the fraction search is exact rational arithmetic over `q ≤ 9999`, with a `1e-15` absolute tie tolerance that only ever picks the **smaller** denominator. Corpus values span `0 … 1234.5`; formats span 2–10 characters. Nothing here is a claim about arbitrary spreadsheets.

**Adjacent but different problems (explicitly not solved).** Floating/anchored pictures (`wp:anchor`), text wrapping, cropping, rotation, transparency, SVG/EMF/WMF, image compression choice, header/footer images, alt-text accessibility semantics beyond storage, and any layout/rendering question. For fractions: decimal rounding, accounting formats, locale-specific separators, currency symbols, elapsed-time formats, and Excel's exact behaviour for pathological formats such as `# ?/?` applied to negative values in a two-section format.

## B. Outcome space and declared threshold

- **H+** — caller-supplied raster bytes, header-measured dimensions and per-target dedupe yield packages that an independent OOXML reader resolves exactly, while every image-free document stays byte-identical. For fractions, nearest-rational-with-bound is implementable exactly and reproducibly.
- **H−** — this cannot prove Microsoft Word will *display* the images correctly, cannot make an untrusted `.docx` safe to render, and cannot prove Excel parity for the whole format language. A fraction format language is open-ended; we implement three shapes, not "fractions" in general.
- **H∂** — the boundary is the denominator bound and the padding width: `?/8` is literal (a zero value renders `0/8`, never `0/1`), `# ??/??` pads the numerator (`" 1/16"`), and a carry is required exactly when the rounded numerator equals the denominator. Several "obvious" invariants fail at that boundary — see D and H.

**Threshold declared before committing** (unchanged from `R72-PLAN.md` §B): pinned baseline reproduces the old behaviour; real `kotlinc -Werror` execution of new corpora plus retained lanes; independent Python inspection of real archives; negative controls that reject reverted guards; host discovery with zero failures; census/audit green; documentation naming the unrun gates. Committed only after all of those passed.

## C. Frontier and open interface

Known and restated with their hypotheses: ECMA-376 §20.4.2.8 defines `wp:inline` inside `w:drawing`, §20.1.8.55 defines `a:blip/@r:embed`, and OPC (ISO/IEC 29500-2) requires every relationship target to be a part in the same package — all of which the checker enforces mechanically. Excel's documented fraction semantics ("nearest fraction with at most the indicated denominator") is a *definition*, which is why the oracle can be exhaustive rather than differential. The open interface is precisely: **storage + header consistency is verified here; layout, rendering and recalculation are not.** No theorem in this round closes that gap, and no available tool in this sandbox can.

## D. Negative space — 18 shapes that look like solutions

1. Emit `<w:drawing>` without a `word/media` part (Word shows a red X, a reader sees a dangling relationship).
2. Emit media but no `[Content_Types].xml` default (part is untyped; strict consumers reject).
3. Declare `image/png` for JPEG bytes and trust the caller.
4. Take width/height from the caller instead of the header.
5. Store the same image once per *occurrence* (bloats the file, breaks "distinct image" counting).
6. Deduplicate by alt text or by key rather than by content.
7. Put media targets at absolute paths (`/word/media/…`) or with `..`.
8. Use the hyperlink relationship type for images (consumers silently ignore).
9. Reuse `rId3` for both a hyperlink and an image.
10. Emit `wp:anchor` and call it "inline" (out of contract, refused by this reader).
11. Percent-encode or base64 the media inside `document.xml` (not DrawingML at all).
12. Accept any extension (`.svg`, `.gif`) and pass it through.
13. Trust `wp:extent` and skip the header check (the checker's exact failing case).
14. Trim alt text to avoid escaping (loses content, breaks round trip).
15. Verify with python-docx only — it reads `inline_shapes` but tolerates several of the tamperings above.

Fractions:
16. Render `p/q` by rounding the fraction with `round()` (banker's rounding: `56.5 → 56`), which disagrees with Excel and silently picks the wrong numerator — **this actually happened in the oracle, not in the renderer**.
17. Reduce a fixed denominator (`?/8` showing `0/1`), which is a different format language.
18. Ignore padding and always emit minimal digits, so columns misalign and `# ??/??` round-trips as a different string.

**Detector for the modal shortcut.** #13 ("trust the extent") is the shortcut a self-review would take; `tools/test_r75_writer_images.py::test_extent_contradicting_the_pixels_is_rejected` fails if the checker stops re-deriving dimensions, and `test_jpeg_bytes_behind_a_png_name_are_rejected` covers #3/#12. #16's detector is `test_oracle_disagrees_with_bankers_rounding`, plus the exhaustive oracle itself.

## E. Mechanism portfolio (six distinct approaches, each with an artifact)

| # | Mechanism | Artifact | Proved portion | Unresolved interface | Cheapest falsifier |
|---|---|---|---|---|---|
| 1 | Header-measured media + `wp:inline` DrawingML | `DocxPackage.appendPicture` | part/rel/extent agreement over 3 packages | whether Word lays it out | drop one media part → checker fails |
| 2 | Content-addressed media dedupe | `LinkedHashMap<Image, String>` keyed by content equality | identical bytes → one part, one relationship | equality cost on huge images (bounded at 64 × 512 KiB) | duplicate an image → part count must stay 2 |
| 3 | Structural OPC validator | `inspect_package` | every tampering class in D | semantic rendering | 13 negative controls |
| 4 | Third-decoder confirmation | ImageMagick `identify` on extracted media | the bytes are decodable images of the stated size | what Word's decoder does | change one byte of magic → identify fails |
| 5 | Exhaustive nearest-rational oracle | `check_r75_sheet_fractions.py` | best `p/q` under the bound, by definition | Excel's exact padding quirks | doctor a rendered string → oracle fails |
| 6 | Semantic re-derivation | `semantic_check` in the oracle | rendered text re-parses to floor + p/q | sign handling in multi-section formats | flip a sign → oracle fails |

Each was developed independently: the validator (3) and the oracle (5) share no code with the Kotlin writer/renderer, and (4) is a decoder neither of them uses.

## F. Artifact requirement

Every branch returned an executable artifact: three real `.docx` packages, a 136-row observation table, a PNG contact sheet decoded from the actual media (`docs/verification/r75-writer-images-decoded.png`), and two suites of negative controls. No branch is a status report.

## G. Second-order adversary per candidate

1. **DrawingML emission** — attacker controls alt text (`"` closing an attribute, `<`, `&`): escaped by `escapeXml`, and `descr` is read back verbatim in the round trip; attacker controls bytes: magic and header checks refuse unknown containers.
2. **Dedupe** — attacker supplies 64 distinct 1×1 images and one identical pair; the bound is enforced on *distinct* images before allocation, and the pair collapses to one part.
3. **OPC validator** — attacker crafts a package with an `image` relationship whose target escapes the package, a duplicate part name, or a JPEG behind a `.png` name: all three are rejected, in that order.
4. **Third decoder** — attacker truncates the PNG after IHDR: ImageMagick refuses to decode, and the writer's own `measure()` would have accepted the header; that asymmetry is why the decode step exists.
5. **Oracle** — attacker (or a buggy implementation) reports a *plausible* wrong fraction, e.g. `1/3` instead of `1/4`, or a reduced fixed denominator: exact comparison and the exhaustive property both reject it.
6. **Semantic re-derivation** — attacker strips padding or moves digits: exact comparison rejects, and if the exact check were removed the semantic check would still reject a wrong `p/q`.

## H. Defects found and repaired during this round

1. **`wp:extent` read through the wrong namespace.** The first JVM run threw `wp:extent cx` because `cx`/`cy` are *unqualified* attributes; the reader now accepts both spellings. Caught by the contract check on its first execution, not by review.
2. **A wrong assertion of my own.** I asserted that an IHDR width contradicting the compressed body would be refused. It is not: dimensions come from the header, exactly as `Word` does its own decode. The assertion was replaced by two that pin the real contract, and the limit is now documented in the class and in this file.
3. **Banker's rounding in the independent oracle.** `round(56.5) == 56` in Python; the renderer uses half-away-from-zero and produced `57/8` for `7.0625` with `?/8`. The **renderer was right** and the oracle was wrong; the oracle now uses `half_up` and has a regression test naming the difference.
4. **Oracle parser bugs found by its own runs**: the fixed denominator `16` was split into `1` + suffix `6` (rendering `0` as `06`), and padded bodies such as `0/ 1` were unparseable. Both fixed before the corpus matched.
5. **Two over-eager refusal assertions in the check**: `???/???` and `????/????` are perfectly legal formats; my *test* was wrong, not the code. Replaced with a 5-placeholder refusal, which is genuinely out of range.
6. **The validator rejected a legal document.** `inspect_package` first required `wp:extent` to equal the pixel dimensions, which would have refused any deliberately scaled picture — a feature this round adds. The rule is now `wp:extent == a:ext` plus range checks, the scaled case is a positive control, and the negative control was retargeted at a real inconsistency (extent disagreeing with the shape extent).
7. **Base image artifacts in the fresh sandbox**: the pinned kotlinc/JRE cache and the python readers were absent, so `tools/bootstrap_host_tools.py install` (npm) plus a pinned `pip install --target` were required before anything could compile. Recorded because "restore the toolchain" is part of reproducing this round.

## I. Repairs re-run all gates, plus a patch-specific attack

After every repair, the whole set was re-executed: both R75 runners (39 and 282 assertions), both control suites (13 + 8 tests), the retained Writer lanes (R57 548/35, R59 400, R60 675, R62 1,434, R63 2,246, R64 814), the pinned byte baselines (R60 128, R62 128, R63 257, R63-range 370, R64 193 artifacts byte-identical; R72 guard passed), the R73 formula lane (**371/371 matched**, formualizer **236 compared / 16 declared / 135 not representable / 0 unexpected**), and full host discovery (**1,741 tests, OK**, 9 artifact-dependent skips).

Patch-specific attacks: for the namespace fix, the round trip of a picture with an explicit non-default extent (`1 4/8`-style caller override) must still equal the written blocks — covered by `Block.Picture(widthEmu, heightEmu)` round trip; for the oracle rounding fix, `test_oracle_disagrees_with_bankers_rounding` fails if anyone reverts to `round()`; for the dedupe bound, the 65-distinct-image write must still be refused.

## J. Honest stopping point

Not run and not implied: any Android build of the new source sets, any device/emulator execution, any Word or LibreOffice open, any Excel recalculation, any visual inspection of a *rendered page*. The only visual artifact is `docs/verification/r75-writer-images-decoded.png`, which is a contact sheet of the **media parts** decoded by ImageMagick — it proves the bytes are real, correctly sized images, and nothing about layout.

Writer still lacks: floating pictures/wrapping, cropping/rotation, SVG/EMF, header/footer images, fields, bookmarks/anchors, comments, track changes, sections. Sheet still lacks: locale/currency/accounting formats, elapsed-time rounding, `RANDARRAY`, spill limits, and any recalculation evidence.

Module census unchanged in kind: **50/50 modules (100.00 %) meet the bounded substantial-testable-subset standard; 0/50 (0.00 %) are fully specification-accepted.** Writer and Sheet are **not Class A**: two of the four mandatory gates for that (real Word/LibreOffice/Excel execution and a device run) cannot be satisfied in this sandbox.

## K. Observed CI (this commit)

Run `42776312124` on `47f4914`: 27 checks passed, 1 failed, 1 skipped. The "Compile Android and run actual JVM contracts" step — which now also executes `:testing:r75WriterImageContracts`, `:testing:r75SheetFractionContracts`, `check_r75_writer_images.py --require-python-docx`, `check_r75_sheet_fractions.py` and both control suites — **passed in CI**, and the traceability/JVM job passed in 9m36s. The two failures are the long-standing device-only gates, step 19 (real API34 UI and provider checks) and step 21 (all 37 named Office cases), unchanged from run `42767212348` on `bad9618`. Those require a working managed emulator/device, which is exactly the gate this round cannot close; nothing in the R75 slices is implicated by them.

Next highest-information test: open `images.docx`, `mixed.docx` and `links-and-images.docx` in Word or LibreOffice and check that both pictures display at their stated sizes with readable alt text, then recalculate `TEXT(1.25,"# ?/?")` and `TEXT(0.0625,"# ??/??")` in Excel or LibreOffice and compare with the oracle's `1 1/4` and `" 1/16"`.
