# R74 — Writer hyperlinks and page breaks, plus re-verification of the R73 formula slice

Host-JVM round. No Android build, no emulator, no Word/LibreOffice, no visual capture was run in R74. Every number below comes from a command that was actually executed in this round; anything unexecuted is listed in section J instead of being implied.

## 0. What changed in production code

| Artifact | Role |
|---|---|
| `format/ooxml/src/main/kotlin/dev/fusion/ooxml/DocxPackage.kt` | `Run.href: String?`, `Block.PageBreak`, `MAX_HYPERLINKS = 512`, `MAX_HREF_CHARS = 2048`, `validateHref` (http/https/mailto only, no control characters), hyperlink `<Relationship>` emission in `document.xml.rels`, `<w:hyperlink r:id="rIdN">` runs, reader support for `w:hyperlink` (target resolved through the part relationships, at most 512), `w:br w:type="page"` → `Block.PageBreak`, and `xmlns:r` declared **only** when a link exists |
| `format/ooxml/src/main/kotlin/dev/fusion/ooxml/StructuredWriterDocx.kt` | `PAGE_BREAK_MARKER = "[[pagebreak]]"` (a line that is exactly the marker becomes a page break), inline `[label](http://…|https://…|mailto:…)` link segmentation with `[[pagebreak]]`/unsafe-scheme literals preserved verbatim |
| `format/ooxml/src/main/kotlin/dev/fusion/ooxml/FormulaEval.kt` | R73 payload restored on top of the remote head: `FORMULATEXT`, `ISFORMULA`, `AGGREGATE` 12–19, `TEXT()` renderer consumption (see `R73-RESULTS.md`) |
| `testing/src/r74writer/kotlin/dev/fusion/testing/R74WriterLinkChecks.kt` | 18 host assertions producing `links.docx` and `blocks.docx` |
| `tools/check_r74_writer_links.py` | independent verifier: re-reads both packages with `python-docx` 1.2.0 and with raw OPC XML, 16 checks |
| `tools/test_r74_writer_links.py` | 7 negative-control tests that build tampered packages by hand |
| `tools/run_r74_writer_links.py` | one command: `kotlinc -Werror` compile, JVM run, verifier |
| `.github/workflows/android-r54-office.yml` | office lane runs the contract task, the verifier and the negative controls (not yet observed green — see J) |

## A. Formal model

- **Objects.** A finite text document `T` over Unicode scalar values, split into `k` lines. The writer maps `T` to a byte string `Z` that is an OPC package: `[Content_Types].xml`, `_rels/.rels`, `word/document.xml`, `word/_rels/document.xml.rels`, `word/styles.xml`, `word/numbering.xml`. The reader maps `Z` back to a block list `B`.
- **Access model.** Production code is pure Kotlin over an in-memory byte array; the only ambient authority is the caller's choice to persist the result. Links are *declarative*: the writer never dereferences a target, never resolves DNS, never stores credentials. `TargetMode="External"` means Word (or any consumer) is the only component that can fetch anything.
- **Counted resource.** Two budgets, not one: `|links(Z)| ≤ 512` and `|href| ≤ 2048` characters, plus the pre-existing `MAX_PART_BYTES` per part. A link-free document must remain **byte-identical** to the pinned legacy writer — that is a budget of zero on the *representation* of existing documents, not on the new feature.
- **Quantifier order.** For every line `l` of `T`: either `l` is the marker and contributes exactly one page break, or every `[label](target)` in `l` with `target ∈ {http,https,mailto}` and `|target| ≤ 2048` contributes exactly one hyperlink relationship, and every other bracket text is preserved verbatim. The verified claim is `∀` over a **finite** corpus (two hand-built documents: one 8-line source text and one 4-block block-API document, giving 18 assertions and 16 independent reader checks), never over "all documents".
- **Parameter regime.** Line lengths ≤ 4 KiB, ≤ 128 lines per corpus document, ASCII + `α😀`, no fields, no headers/footers, no comments, no track changes, no `w:sectPr` variations.
- **Adjacent-but-different problems, explicitly out of scope:** bookmark-based intra-document links (`w:anchor`), footnotes/endnotes, images/`w:drawing` with `r:embed`, `w:fldSimple` HYPERLINK field codes, altChunk, custom XML parts, and macro-enabled packages. This round adds *external* hyperlinks only; an intra-document anchor needs a `w:bookmarkStart`, which is a different object with a different validity rule.

## B. Threshold declared before committing

Reused from `R72-PLAN.md` §B, unweakened: (1) a pinned baseline reproduces the old behaviour and the candidate does not; (2) real `kotlinc -Werror` execution of the new corpus plus retained R59–R72 suites; (3) independent Python inspection (`python-docx` + raw OPC) of the real archives, not a shared oracle; (4) negative-control tests that reject reverted guards; (5) host discovery with zero failures; (6) syntax-only parse, census and source audit; (7) documentation naming the unrun Android gates. Android compilation/device/visual gates are not claimed.

Hypotheses:

- **H+** — emitting hyperlink relationships only for allowed schemes, and declaring `xmlns:r` only when links exist, yields a package that an independent OOXML reader resolves to exactly the intended targets *and* leaves every link-free document byte-identical.
- **H−** — this cannot make an untrusted `.docx` safe to process, cannot stop a consumer from following a link the user clicks, and says nothing about rendering (Word may still refuse a scheme at open time).
- **H∂** — "preserved as literal text" and "rejected" are the *same bytes* for a refused scheme; the distinguishing observable is the presence/absence of a `TargetMode="External"` relationship, not the visible string. The verifier must assert element count and relationship count separately, because three link runs may share two relationships (dedupe by target).

## C. Frontier and restated hypotheses, open interface

Frontier restated: Writer now produces **bold/italic/strike/headings/lists/tables/hyperlinks/page breaks** and re-reads them; it does not produce fields, bookmarks, images, headers/footers, sections or comments. Open interface `DocxPackage.write(blocks, numberedLines..., hrefIds)` is unchanged for callers; `Run.href` is an additive default-`null` field, and `Block.PageBreak` is an additive sealed subclass — both source-compatible for existing `when` expressions only if the caller's `when` is not exhaustive over `Block` (this is the one breaking change, and it fires at compile time, not at runtime).

## D. Negative space — solution-shaped non-solutions

1. Emit `<w:hyperlink>` runs without any `document.xml.rels` entries (Word shows plain text; a reader sees a dangling `r:id`).
2. Emit the relationship but keep `TargetMode` internal (path lookup instead of external).
3. Allow every scheme and rely on the consumer to refuse (`javascript:` becomes live in browsers that embed the DOCX).
4. Strip control characters silently instead of refusing the target.
5. Percent-encode the whole target to "sanitise" it (changes identity; `?x=1&y=2` becomes a different URL).
6. Dedupe links by *label* instead of by target.
7. Dedupe by target but reuse `rId` per *run* (duplicate relationship ids → corrupt rels part).
8. Truncate over-long targets to 2048 instead of refusing.
9. Emit `xmlns:r` unconditionally "for safety" (breaks byte-identity of every legacy document — this is the shortcut that actually happened; see H1).
10. Treat any `[...](...)` as a link regardless of the scheme, then escape the angle brackets.
11. Split on the first `](` and ignore an unclosed `[` (loses the literal text).
12. Render the page break as `\n\n` (paragraph spacing, not a break).
13. Render the page break as `<w:br/>` without `w:type="page"` (line break, not a page break).
14. Swallow the marker line's text when it appears *inside* a line rather than alone.
15. Reset the hyperlink counter per paragraph instead of per document.
16. Verify with `python-docx`'s `paragraph.text` alone, which flattens hyperlink runs and cannot prove a relationship exists.

**Detector for the modal shortcut (#9, unconditional namespace).** `tools/run_r60_writer_baseline.py`, `run_r62_writer_baseline.py`, `run_r63_writer_baseline.py`, `run_r63_range_baseline.py`, `run_r64_writer_baseline.py` and `run_r72_writer_guard.py` compare current bytes against pinned legacy writers; #9 turns all of them red with `Old/default DOCX bytes differ at 0` / `Legacy Writer corpus bytes changed: doc-000.docx`. That detector is not hypothetical: it fired, and the fix was to declare `xmlns:r` only when `hrefIds` is non-empty.

## E. Mechanisms (distinct, each with an artifact and a falsifier)

1. **Relationship emission.** `documentRelsXml(hrefIds)` now writes one `<Relationship Id="rIdN" Type="…/hyperlink" Target="…" TargetMode="External"/>` per distinct target. Falsifier: `blocks.docx` must show 3 hyperlink elements against **2** relationships (two runs share `https://example.org/plain`).
2. **Run grouping.** `appendRuns` emits `<w:hyperlink r:id>` for grouped href runs, `<w:r>` otherwise; falsifier: a paragraph mixing plain text and a link must keep both, in order.
3. **Scheme gate.** `validateHref` accepts only `http`, `https`, `mailto`, no control characters, ≤ 2048 chars; falsifier: `[bad](javascript:alert(1))` must appear as literal text *and* produce zero relationships.
4. **Boundary discipline.** `MAX_HYPERLINKS = 512` is enforced on write (require) and on read (stop collecting); falsifier: the reader maps at most 512 ids.
5. **Page-break block.** A marker-only line becomes `Block.PageBreak` → `<w:br w:type="page"/>` inside its own run; falsifier: `<w:br w:type="page"/>` must precede the paragraph containing "After the break." and must not be `<w:br/>`.
6. **Marker literalness.** `[[pagebreak]]` inside a sentence, and an unterminated `[label](https://example.org`, stay literal; falsifier: the escaped `\[label](https://example.org)` case.
7. **Link-free byte identity.** Conditional `xmlns:r`; falsifier: six pinned legacy baselines (section G).

## F. Second-order adversary per candidate

1. **Relationship emission** — a malicious target containing `"` or `>`: the attribute is escaped with `&quot;`/`&gt;`, so the rels part stays well-formed; a target containing a newline is refused by `validateHref`, so no attribute injection.
2. **Run grouping** — a `w:hyperlink` nested inside another `w:hyperlink` cannot be produced because the writer only ever nests from a flat segment list; a reader that meets a nested one keeps the outer id.
3. **Scheme gate** — case folding: `HTTPS://` passes (intended), `JaVaScRiPt:` is refused because the comparison is case-insensitive on an allowlist, not a denylist.
4. **Budget** — 512 links × 2048 chars is ≈ 1.05 MiB of rels before escaping, i.e. just above the 1 MiB `MAX_PART_BYTES` reader budget. A maximal document can therefore be *written* but not *re-read* by this reader; the writer does not pre-check the rels size. That is a real limitation, recorded rather than papered over.
5. **Page break ordering** — a break at document start yields a leading `<w:br w:type="page"/>` with an empty paragraph; a break at end yields a trailing one. Both are well-formed and neither adds text.
6. **Marker literalness** — an attacker who wants a break cannot get one without occupying a whole line; an attacker who wants text preserved cannot be tricked into a link because the target must start with an allowed scheme.

## G. Verification actually performed (final state of this revision)

| Check | Result |
|---|---|
| `kotlinc -Werror` over archive+ooxml+probe | exit 0 |
| `R74WriterLinkChecks` (host JVM) | **`R74_WRITER_LINKS_OK assertions=18`**, two DOCX packages |
| `check_r74_writer_links.py` (python-docx 1.2.0 + raw OPC XML) | **16 independent checks passed** |
| `test_r74_writer_links.py` negative controls | **7 tests, OK** (missing target, missing page break, live `javascript:` target, lost literal text, duplicate relationship, macro part, untampered pass) |
| R73 oracle re-run | **371 formulas / 371 actual / 371 matched**, 0 mismatches |
| R73 third engine (formualizer 0.13.0) | **236 compared, 16 declared differences, 135 not representable, 0 unexpected** |
| R73 wiring guards | **11 tests, OK** (after syncing `ESSENTIALS` with the production surface: 39 → 41 names) |
| Legacy Writer lanes | R59 400 assertions/200 DOCX; R60 675/256; R62 1,434/256; R63 2,246/192; R64 tables 814/192 |
| Pinned legacy byte baselines | R60 **128**; R62 **128**; R63 **257**; R63-range **370**; R64 **193** artifacts byte-identical |
| R61 sheet baseline | 64 grids / 128 cases byte-identical |
| R72 Writer guard | passed |
| R45 / R46 / R47 / R48 / R55 | 508 / 885 / 2,393 / 3,861 (68 kernels, 50/50 modules) / 1,054 |
| Census | **50/50 narrow testable subsets (100.00 %)**, **0/50 full-spec accepted (0.00 %)** |
| Source audit | 231 requirements, 36 obligations |
| Full host discovery (`python3 -m unittest discover -s tools -t tools -p 'test_*.py'`) | **1,773 tests, OK**, 5 pre-existing optional-dependency skips |
| `module_testability.py --check`, `native_source_audit.py --check`, `test_module_testability` (7), `test_native_source_audit` (13) | green after regenerating both reports |

## H. Defects found and repaired during this round

1. **`documentRelsXml(hrefIds)` ignored its argument.** The parameter existed and was threaded through, but the function body never emitted a `<Relationship>`; every hyperlink therefore had a dangling `r:id`. Repaired by emitting one relationship per distinct target. Caught by the contract check after the writer had already produced "valid-looking" XML.
2. **Page break read-back always failed.** The writer emitted `<w:t xml:space="preserve">\n</w:t>`, so the run was non-empty (`text.isEmpty()` false) and the paragraph was not classified as a break; the condition is now `isBlank()`.
3. **Unconditional `xmlns:r` (regression introduced by this round and repaired in it).** The first version declared the relationship namespace on every document, which changed the bytes of all link-free documents and broke six pinned legacy baselines. Repaired by interpolating the declaration only when links exist. This is recorded because it is exactly the kind of "harmless" edit that a weaker gate would have shipped.
4. **`ESSENTIALS` guard drifted from production.** `test_r73_formula_contract.py` still listed 39 essentials while `FormulaEval.ESSENTIAL_NAMES` had grown to 41 (`FORMULATEXT`, `ISFORMULA`); the guard failed honestly and was synced rather than deleted.
5. **Stale census/audit hashes** after three production files changed; both reports were regenerated and their `--check` modes now pass.
6. **Baseline runners died on a shallow clone.** `git fetch --unshallow` restored history, and the runners that pin files absent at their base commit now skip those paths via `git cat-file -e` instead of crashing.

## I. Repairs re-run all gates

After each repair the full set in section G was re-executed. The patch-specific attack for the namespace change is the byte-identity baseline on a link-free document (`doc-000.docx`, the R60/R62/R63/R64 corpus head), which must be identical to the pinned writer *and* still compile the new reader; the patch-specific attack for the relationship change is `blocks.docx`, whose three link runs must resolve to two relationships, and the negative-control "duplicate relationship" case, which must be rejected.

## J. Honest stopping point

Not run, not implied: any Gradle/AGP build of the new source set, any Android or emulator execution, any Word/LibreOffice open, any visual inspection, any `.docx` produced by a second word processor. The CI office lane wiring added in this round has **not** been observed green: the last observed office run on `a362ae6` (`42569424797`) **failed** at steps 19 and 21. Writer still lacks fields, bookmarks/anchors, images, headers/footers, sections, comments and track changes. The formula kernel still lacks `RANDARRAY`, spill limits for `SEQUENCE`, locale/currency/fraction formats, and any Excel or LibreOffice recalculation.

Next highest-information test: open `links.docx` and `blocks.docx` in an actual Word or LibreOffice, and check that the three links are clickable, the refused `javascript:` target is not, and the page break paginates — that is the only artifact that can promote this from "two independent readers agree on the package" to "a real word processor renders it".
