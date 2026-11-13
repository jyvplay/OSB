# R80 — Writer line numbering and vertical alignment, and the merge of PR #16

Host-JVM round on branch `arena/15d3f8f5-osb`. No Android device, emulator, Word, LibreOffice or Excel took part. Minimal-diff mandate: two `sectPr` properties, no new parts, nothing removed.

**Runtime honesty.** Used: pinned Kotlin 2.2.21 + JRE 17 (`tools/bootstrap_host_tools.py install`, re-run because sandbox caches are excluded from snapshots), CPython 3.11 `zipfile`/`lxml`, `python-docx` 1.2.0, `ssf` 0.2.2 + `babel` 2.9.1, `openpyxl` 3.1.5, ImageMagick 6.9 (R75 lane), Node 22 (`npm ci`, `vite build`, `tsc --noEmit`). Not available: Android SDK/AGP/Gradle, device, Word, LibreOffice, Excel, theorem prover, second reviewer. No parallelism, no external agent, no borrowed code.

## 0. Restore (asked for first)

`HEAD` arrived on the merge base `9965ba1` with the R75–R79 tree uncommitted. Verified, not assumed: `git add -A` → `git diff --cached FETCH_HEAD` was **empty** (tree byte-identical to the branch tip `b617d91`) → `git reset --hard FETCH_HEAD`. Web side rebuilt from the repo's own scripts: `npm ci` exit 0, `npm run build` → `dist/index.html` 225.28 kB (gzip 70.00 kB), `npx tsc --noEmit` exit 0. Toolchain and reader caches were reinstalled before any gate ran.

## 0b. What changed

| Artifact | Change |
|---|---|
| `format/ooxml/.../DocxPackage.kt` | `Layout.lineNumbering: LineNumbering?(countBy, start, restart)` emits `w:lnNumType` between `w:pgMar` and `w:pgNumType`, omitting the default `newPage` restart; `Layout.verticalAlignment: VerticalAlignment` (`top`/`center`/`both`/`bottom`) emits `w:vAlign` after `w:cols`, omitting the default `top`. Both are read back; bounds are `countBy ∈ 1..1000`, `start ∈ 1..1 000 000`; no part and no relationship is added, so layouts that use neither keep identical bytes. |
| `testing/src/r76writer/.../R76WriterLayoutChecks.kt` | +1 package (`line-numbers`: countBy 5, start 3, restart `continuous`, vertical alignment `center`), +11 assertions (74 total, 9 packages), +3 refusals (countBy 0, countBy 1001, start 0). Also asserts the default form `<w:lnNumType w:countBy="1" w:start="1"/>` carries no restart attribute. |
| `tools/check_r76_writer_layout.py` | Intent table 20 → 24 fields; `w:lnNumType` required iff declared, with exact `@countBy`/`@start`, `@restart` present only for non-default values; `w:vAlign` required iff not `top`; both inserted into the CT_SectPr order check. |
| `tools/test_r76_writer_layout.py` | +4 controls (line numbering removed, `@countBy` changed, explicit default restart, vertical alignment removed) → **28/28**. |

No other production file was edited.

## A. Formal model

**Objects.** A Writer section is `Layout(pageSetup, header, footer, columns, columnSpaceTwips, pageNumberStart, sectionType, firstPageHeader, firstPageFooter, evenPageHeader, evenPageFooter, lineNumbering, verticalAlignment)`.

**Access model.** Pure function of `(blocks, Layout)`; no filesystem, URL, provider or clock. `readLayout` reads only bounded parts of a package this writer produced.

**Counted resources.** Line-number `countBy ≤ 1000`, `start ≤ 1 000 000`; the sectPr gains at most two child elements; nothing else is counted differently from R79.

**Success, with quantifier order.** For **every** package in the nine-package corpus: `w:lnNumType` is absent exactly when no line numbering is declared, and otherwise carries exactly the declared `countBy`/`start` plus `restart` only when it is not `newPage`; `w:vAlign` is absent exactly when the alignment is `top`; both sit in CT_SectPr order (`pgMar → lnNumType → pgNumType → cols → vAlign → titlePg`); `readLayout` returns the same layout the probe asked for. For **every** package in the R60/R62 legacy corpora (128 each) the bytes are unchanged.

**Parameters / regime / tolerances.** Word counts lines 1, 2, … per page; restart ∈ {`newPage`, `continuous`, `newSection`}; no tolerance anywhere: string and byte equality are exact.

**Adjacent but different problems.** Word's actual line-number placement and margin behaviour, `w:docGrid` line pitch, `w:formProt`, vertical alignment inside table cells (`w:vAlign` in `tcPr` is a different element), and any rendering question. None is claimed.

## B. Outcome space and threshold

Threshold declared before the run: **nine packages verified, 28 controls green, both legacy baselines byte-identical, full suite green.** Met.

- **H+** — both properties are single `sectPr` children with enumerated values; adding them cannot perturb any existing package because they are emitted only on request and their defaults are omitted.
- **H−** — **no independent reader checks these two elements.** python-docx exposes neither `lnNumType` nor `vAlign`. The verification is therefore our own OPC walk plus CT_SectPr order, which is weaker than R78/R79 where python-docx corroborated. That is stated here rather than hidden.
- **H∂** — the boundary is the default-omission rule: emitting `w:restart="newPage"` or `w:vAlign w:val="top"` is legal XML but redundant, and a writer that emits defaults grows every package that uses them. The verifier tests omission, and a control injects the explicit default to prove it is rejected.

## C. Frontier

R80 completes the attribute-level section properties that need no new part (columns, restart, section type, title page, even/odd, line numbering, vertical alignment). Remaining structural gap: multi-section documents (a list of `Layout`s), and everything about pagination. The open interface is unchanged — an application that lays out pages.

## D. Fifteen solution-shaped non-solutions

| # | Non-solution | Detector |
|---|---|---|
| 1 | Emit `w:lnNumType` always (defaults everywhere) | `unexpected w:lnNumType` on the other eight packages |
| 2 | Emit `w:restart="newPage"` explicitly | control `test_explicit_default_restart_is_rejected` |
| 3 | Emit `w:vAlign w:val="top"` explicitly | `the default vertical alignment must be omitted` |
| 4 | Put `w:lnNumType` after `w:pgNumType` | CT_SectPr order check |
| 5 | Put `w:vAlign` before `w:cols` | CT_SectPr order check |
| 6 | Accept `countBy = 0` (Word would divide by zero) | probe refusal + `LineNumbering` `require` |
| 7 | Accept `countBy = 1001` | probe refusal |
| 8 | Accept `start = 0` | probe refusal |
| 9 | Ignore `restart` and always write `newPage` | round-trip equality (`continuous` would be lost) |
| 10 | Parse an unknown `restart` as `newPage` | `readLayout` throws `unknown line-number restart` |
| 11 | Parse an unknown `vAlign` as `top` | `readLayout` throws `unknown vertical alignment` |
| 12 | Map `center` to `both` | intent comparison of `@val` |
| 13 | Add a part or relationship for these properties | `unexpected packages without a declared intent`; package part counts unchanged |
| 14 | Change the bytes of packages that do not use them | R60 128 and R62 128 byte-identical baselines |
| 15 | Trust the producer's intent table without checking the XML | the verifier parses `document.xml` with `lxml` and compares each attribute |

## E. Six mechanism-distinct branches

1. **Nested value types on `Layout`, defaults omitted** (chosen: two elements, no new concepts, byte-identical when unused).
2. Encode both as booleans plus integers on `Layout` directly: rejected — `LineNumbering` as a nullable value makes "numbering off" and "numbering on with defaults" distinguishable.
3. Reuse `PageSetup` for line numbering: rejected — it is a text-flow property, not geometry.
4. Emit a `w:docGrid` to fake line pitch: rejected — a different element with different semantics.
5. Let the caller pass raw `sectPr` XML: rejected — removes every invariant this writer enforces.
6. Multi-section model: still the correct long-term shape, out of scope.

## F. Artifacts

`testing/build/r76writer/artifacts/line-numbers.docx`, `layout.tsv` (24 columns), the nine-package corpus, the verifier report, 28 controls, both legacy baselines, this document.

## G. Second-order adversary

The strongest attacker writes the XML and the intent table consistently but wrong. Defences: exact attribute comparison against the intent, four controls that each change one byte-string, defaults asserted *absent* rather than merely equal, and the byte-identical legacy baselines. Residual risk: since no independent reader parses these two elements, a shared misreading of the `w:lnNumType` attribute names would be invisible to this lane. Mitigated only by the ECMA-376 element name and by python-docx opening the package without error — not by an independent opinion.

## H. Verification

- `run_r76_writer_layout.py`: `R76_WRITER_LAYOUT_OK assertions=74 documents=9`; verifier: **9 packages (8 with page geometry) verified with python-docx 1.2 and raw OPC inspection**.
- `test_r76_writer_layout.py`: **28/28** controls.
- R60 baseline **128 byte-identical**; R62 baseline **128 byte-identical**; R74 writer links 20 checks; R75 media decoded by ImageMagick.
- R76 formats: 245 rows, 239 replayed, 231 identical, 8 adjudicated. R77 conditions: 60/60 re-derived, 7/7 `ssf`, 4/4 pinned.
- Full `tools` suite: **1747 tests OK** (22 skipped). Census: **50/50 (100.00 %) substantial testable subset, 0/50 (0.00 %) fully accepted**.

## I. Repairs and regressions vs the previous turn

One defect from this round's draft was fixed before any gate was called green: a control injected a *second* `w:restart` attribute (duplicate-attribute XML) and then asserted a message the mismatched-restart branch preempts; it now rewrites the existing attribute **and** updates the intent to the default, so it exercises the omission rule specifically.

**Regressions vs the R79 codebase: none observed.** R60 128/128, R62 128/128 identical; writer lane 63 → 74 assertions, all green; controls 24 → 28, all green; R74 20 checks; R75 decoded media; R76 formats unchanged (245/231/8); R77 conditions unchanged (60/60, 7/7, 4/4); suite 1743 → 1747; census 50/50 — 0/50. Nothing removed or weakened; the only production edit is additive with omitted defaults.

## J. Class A, merge, and the next test

**Class A is still not claimed** and cannot be claimed here: Writer needs a word processor, Spreadsheet an engine that recalculates, the app a device. This round also *lowers* the verification strength for its own two elements (no independent reader), and says so.

**PR #16 is merged at the end of this turn** on the user's instruction. The merge is a repository operation, not a verification: the CI job that gates it still fails at the two device-only steps, exactly as it has on every round, and the merge does not convert any host claim into rendered or device evidence.

**Next highest-information test:** open `testing/build/r76writer/artifacts/line-numbers.docx` in Word and report whether the line numbers appear in the margin at countBy 5 starting at 3, whether they continue across pages, and whether the body is vertically centred. Those three observations are exactly what no host reader in this environment can answer.
