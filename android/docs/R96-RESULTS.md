# R96 — Conditional formatting and the differential-format table

**Scope of this pass.** The record pair that lets a worksheet say "paint this cell differently *when some
condition holds*": `<conditionalFormatting>` (ECMA-376 Part 1 §18.3.1.18) with its `<cfRule>` children (§18.3.1.10), and the
workbook-level `<dxfs>` table (§18.8.3) whose entries `<dxf>` (§18.8.3.2) are what `cfRule@dxfId` resolves
against. Only element sections are cited; the complex types (`CT_ConditionalFormatting`, `CT_CfRule`, `CT_Dxfs`,
`CT_Dxf`) are named rather than numbered, because an invented subsection number is worse than none. R95 finished the
print tail; R96 goes back up the `CT_Worksheet` sequence and fills the position between `mergeCells` and
`dataValidations`, which was still unwritten.

**Class A applications: still 0.** No Excel, LibreOffice, browser or Android target ran. What follows is
agreement between this writer, this reader, a raw OPC walker and openpyxl 3.1.5. None of those is a spreadsheet
application, and nothing here observes a cell being painted, a data bar being drawn or a rule actually firing.
Conditional formatting is *the* feature whose value is entirely in the painting; the evidence below covers the
file, never the paint.

## A. What was added

| Piece | File | Lines |
|---|---|---|
| Conditional-format model (`Kind`, `Operator`, `ThresholdKind`, `Threshold`, `Format`, `Rule`, `Block`, `requireDistinctPriorities`, `dxfToXml`, `parseDxfs`, `parse`, `toXml`) | `XlsxConditionalFormatting.kt` (new) | 459 |
| Integration: one sheet field, one invariant, block emission, `dxfs` interning, `<dxfs>` emission, read-back wiring | `XlsxStyledBook.kt` (**+38/−3**) | 41 |
| Probe + artifacts | `testing/src/r96/.../R96Checks.kt` | 540 |
| Runner | `tools/run_r96_checks.py` | 76 |
| Raw OPC + openpyxl lane (`conditional_formatting`) | `tools/check_r96_sheet_conditional.py` | 334 |
| Raw OPC + openpyxl lane (`_differential_styles`) | `tools/check_r96_sheet_dxf.py` | 228 |
| Negative controls | `tools/test_r96_sheet_conditional.py`, `tools/test_r96_sheet_dxf.py` | 312 + 214 |

`XlsxValidation.kt`, `XlsxProtection.kt`, `XlsxAutoFilter.kt`, `XlsxMerges.kt`, `XlsxHyperlinks.kt`,
`XlsxPrintSetup.kt`, `XlsxHeaderFooter.kt`, `XlsxBreaks.kt` and `BoundedXml.kt` are byte-identical to R95.
`git diff --stat` on this branch lists exactly the files above, the two census documents the pin refreshes, one
kernel source line in `tools/run_r47_contracts.py`, one Gradle task in `testing/build.gradle.kts`, and the CI
workflow lines named in §H.

The pre-existing `CondFormat.kt` and `ConditionalDataBarScaleEngine.kt` are untouched. They *evaluate* a rule
against a value; this pass models the *file*. The two vocabularies do not meet yet — §C says why that is an
honest gap rather than a hidden one.

## B. The contract, stated so it can be wrong

**Formal model.** A sheet carries a sequence of *blocks*; a block is `Block(sqref, rules)` where `sqref` is a
non-empty, de-duplicated, ascending list of A1 ranges and `rules` is 1..64 rules. A rule is one of five kinds
with a priority in `1..1_000_000`, unique **per sheet**, plus:

* `cellIs` — an `Operator` (`lessThan … between`, `notBetween`, 11 tokens) and **exactly** one formula, or two
  for `between`/`notBetween`; a differential format is mandatory.
* `expression` — one formula; a differential format is mandatory.
* `top10` — `rank` in `1..1000`, `percent`/`bottom` booleans; a differential format is mandatory.
* `colorScale` — 2 or 3 `Threshold`s (`min`/`max` take no value; `num`/`percent`/`percentile` take one;
  percent/percentile in `0..100`) with one fewer colour than thresholds+1, i.e. exactly as many colours as
  thresholds; **no** differential format.
* `dataBar` — the same threshold rule, exactly one colour, **no** differential format.

A `Format` (a `<dxf>`) holds optional bold/italic/strike/underline, an optional 6-hex font colour and an
optional 6-hex fill colour, and at least one of them. Formats are interned: two rules with the same format share
one `<dxf>` and one `dxfId`, which is what the schema is for.

**Information/access model.** The writer may consult the ECMA-376 text, openpyxl's *reader* and its own parser.
It may not consult Excel, the only object that can confirm a repair prompt. The reader must accept *foreign*
packages — the shapes openpyxl and Excel write — not only the shapes this writer writes.

**Resource counted.** Bytes emitted plus the number of records a reader can reconstruct: probe assertions (60),
controls (72), two independent lane checks over every declared rule and every declared dxf entry.

**Success and failure, with quantifier order.** Success is: *for every* admissibility condition *there exists* a
refusal (27 constructions in the probe, §H), **and** *for every* admissible rule the writer emits, *for both*
independent readers, the read-back equals the intent (per rule, per block, per dxf entry). Failure is
asymmetric: one accepted inadmissible rule, one dangling `dxfId`, or one reader disagreement fails the pass.

**Parameter regime, units, tolerances.** Priorities are plain integers; `rank` is an integer 1..1000;
`percent`/`percentile` thresholds are decimals in 0..100 and compared with a 1e-9 tolerance because Kotlin
writes `0.0` where the file says `0`; colours are 8 hex digits on disk (`FF` + six) and six in the intent
table; formulas are ≤8192 characters of text, stored **without** a leading `=`; `sqref` holds ≤64 tokens.
`stopIfTrue`/`percent`/`bottom` are tri-state in the model (`null` = absent = schema default `false`) and are
distinguished from an explicit `"0"`.

**Adjacent problems that must not be substituted.** (i) *Evaluation* — which row actually satisfies
`MOD(ROW(),2)=0` is `CondFormat`'s job and is not wired to this model; (ii) *painting* — a data bar's length, a
colour scale's interpolation, the gradient the schema allows via `extLst`; (iii) *icon sets* — `iconSet` needs a
styled-icon pipeline this writer has no renderer for, so the kind is refused rather than approximated; (iv)
`containsText`/`notContainsText`/`beginsWith`/`endsWith`/`duplicateValues`/`aboveAverage`/`timePeriod` — refused,
not silently mapped onto `expression`; (v) `pivot` on a block, `extLst` on a rule, `dxf` attributes other than
the six modelled (font/fill/border/numFmt/alignment/protection); (vi) the `dataBar` 2010 extensions (gradient,
axis, border, negative fill) which live in `extLst` and are not modelled.

## C. Frontier, with the hypotheses it imports

The unverified hypotheses, stated so they can be attacked:

1. **`dxfId` is an index into the workbook-level `<dxfs>` table, and an index that names no entry is a repair.**
   Read from the schema text; not observed on a real Excel. If false, the strongest structural check in §H is
   vacuous.
2. **Priorities are unique per sheet and lower wins.** Uniqueness is enforced as a model invariant, but *which*
   rule wins when two overlap is an engine decision this pass never observes. A rule that this writer refuses for
   a duplicate priority would be accepted by Excel with engine-defined behaviour.
3. **A `cellIs`/`expression`/`top10` rule without a format paints nothing and is therefore refused.** Excel
   accepts such a rule (it is legal XML, and openpyxl writes it — the probe contains exactly that shape). This is
   the one place where the writer is *stricter than the format*, chosen deliberately: a rule that cannot paint is
   a rule whose intent the model cannot state.
4. **A colour scale or data bar needs no differential format.** These kinds carry their formatting inline. A file
   that also gives them a `dxfId` is accepted by Excel; the reader drops the surplus rather than inventing a
   meaning for it.
5. **`<dxfs>` follows `</cellXfs>`** (and `</cellStyles>`) and precedes `<tableStyles>` in `CT_Stylesheet`; the
   block sits after `</sheetData>` and before `dataValidations`/`pageMargins` in `CT_Worksheet`. Both read from
   the schema; both are asserted byte-wise in §H. Excel's tolerance for a misplaced child is exactly the thing
   that cannot be tested here.
6. **A solid `dxf` fill is `<fgColor rgb>` + `<bgColor indexed="64"/>`.** This is the shape Excel writes and the
   shape this writer writes. openpyxl writes `<bgColor rgb>` instead — §G pass 2 shows what that cost.

## D. Negative space — 15 shapes that look like a solution and are not

1. `iconSet` with three traffic lights — needs a renderer; refused by `Kind.of` rather than mapped to a scale.
2. `containsText` → `expression` + `SEARCH()` — a translation, not a model: it changes the file's stated type.
3. `aboveAverage` → `expression` with `AVERAGE()` — same objection, plus it silently changes the aggregate.
4. `duplicateValues` — there is no formula that means "duplicate" without a helper column.
5. `between` with one formula — the reader refuses rather than duplicating the operand.
6. `notBetween` with three formulas — extra operands have no schema position; refused, not truncated.
7. `top10` with `rank=0` or `rank=1001` — clamped would be a silent intent change; refused.
8. A percent threshold of `150` — the schema says 0..100; refused.
9. A `min` threshold with `val="0"` — Excel tolerates it, the schema does not define it; the reader ignores the
   surplus value instead of inventing a threshold kind.
10. Two rules with the same priority — accepted by Excel, refused here (see §C.2). A caller that builds such a
    sheet is refused at construction (`StyledSheet.init`); a *foreign* file that carries one is read with the first
    block that uses the priority kept and the later colliding block dropped, so one bad block does not cost the
    sheet. Renumbering the survivor would be inventing intent.
11. One `<dxf>` per rule even when the formats are equal — the interned table is the point; a per-rule copy
    would pass a naive reader and defeat the sharing the schema exists for.
12. A block written before `</sheetData>` — parses, reads back, and is out of `CT_Worksheet` order.
13. `pivot="1"` on a block — a real attribute this model does not hold; refused rather than dropped.
14. A `dataBar` with only one `cfvo` — the schema allows `2`; a single threshold cannot bound a bar.
15. A `dxf` carrying `<numFmt>` — legal, unmodelled, and refused, because a differential number format changes
    what the cell *shows* and this writer cannot round-trip it.

## E. Mechanism portfolio

| Mechanism | Where | What it buys |
|---|---|---|
| Per-sheet priority uniqueness | `StyledSheet.init` | A duplicate silently changes which rule wins; refused at construction, not at write time |
| Format interning | `StyleRegistry.internDxf` | Equal formats share one `<dxf>`; `dxfId` is stable across blocks and sheets |
| Pre-interning before serialisation | `XlsxStyledBook.write` | `styles.xml` is serialised before the sheets are built; without the pre-pass every `dxfId` dangles (see §G pass 1) |
| Tri-state flags | `Rule.stopIfTrue` / `percent` / `bottom` | Distinguishes "absent" (schema default) from an explicit `"0"` a foreign file may carry |
| Attribute-order assertion | lane 1, `attribute_sequence` | CT_CfRule sequence is checked on the raw text, where ElementTree cannot help |
| Cross-part integrity | lane 1, `dxfId < count` | The one structural failure that makes Excel offer a repair |
| Empty-table refusal | lane 2, probe | A workbook with no rules must not carry `<dxfs>` |
| Foreign-shape reading | `parseDxfs` accepting `fgColor` **or** `bgColor` | openpyxl's dxf fill is legal; dropping it also dropped its rule |

## F. Artifact requirement

`testing/build/r96/artifacts/` holds, per package, everything a reader can reconstruct:

* `r96.xlsx` — three sheets: `Grid` (5 rules in 4 blocks: a `cellIs` with a fill, an `expression` sharing the
  fill, a 2-stop colour scale, a data bar, a `top10 bottom` with the italic font), `Two` (3 rules in 2 blocks: a
  `between` with two formulas and an `expression` sharing the same format, plus a 3-stop scale with a `percent`
  middle), `Plain` (no rules);
* `r96-plain.xlsx` — one sheet, no rules at all, i.e. no `<dxfs>` in its `styles.xml`;
* `r96-foreign.xlsx` — five blocks written the way other tools write them: a `cellIs` with a `bgColor` fill, an
  `expression` with a `bgColor` fill, a `top10` with no format, an `iconSet` with three icons, and an `expression`
  that repeats the sheet-wide priority `1`; two are read, three are refused;
* `conditional.tsv` (18 fields per row) and `dxfs.tsv` (9 fields per row) — the intent, written before the
  packages are read back.

## G. Second-order adversary — six passes against this work, and what each caught

1. **"Serialise styles last."** The hypothesis was that `styles.xml` is built before the sheets, so a `dxf`
   interned during sheet building never reaches the table. The probe failed exactly there
   (`unexpected dxf count: null`) and the fix was *not* to reorder the parts — which would have changed the ZIP
   entry order of every workbook ever written — but to pre-intern **every rule's format** before
   `styleTable.toXml()`, keeping `internDxf` idempotent and the entry order intact. Had it shipped, every
   conditional format would have pointed at a `dxfId` that names nothing: a repair prompt with no visible cause.
2. **"Read a foreign fill."** The reader looked only for `patternFill/fgColor`, so openpyxl's
   `<patternFill><bgColor rgb="0000FF00"/></patternFill>` read as an empty format, which failed `Format`'s
   non-empty invariant, which dropped the rule. Two independent losses from one unstated assumption. Fixed by
   preferring `fgColor` and falling back to `bgColor`.
3. **"Don't repair what the file doesn't say."** The foreign package also carries a `top10` with no format, an
   `iconSet`, and a fifth block that repeats the sheet-wide priority `1` an earlier block already uses. A reader
   that "helpfully" attaches a default format, maps icons onto a colour scale, or renumbers the colliding block
   would pass a naive round-trip and lie about the file. All three are asserted *absent* from the read-back, and
   lane 2 asserts the rule-free workbook has no table. Before the dedupe was added, the colliding block reached
   `StyledSheet.init` and threw, i.e. one foreign quirk cost the whole sheet.
4. **"The reader is not the writer's mirror."** openpyxl's `MultiCellRange` is set-like and reorders `sqref`
   tokens (`E1:E9 D1:D4` reads back as `D1:D4 E1:E9`); its `DataBar.color` is a single `Color` while
   `ColorScale.color` is a list; its `_differential_styles` yields `None` fonts for fill-only entries. Three lane
   crashes, all in the *verifier*, all fixed by asserting the property that actually holds on each side rather
   than the one that holds on ours.
5. **"A verifier that crashes has not verified."** `require(ok, f'…{missing.attr}…')` evaluates its message
   eagerly, so a `None` produced a traceback instead of a reported failure — indistinguishable from a broken
   lane. Every such message was rewritten with safe accessors, and two malformed-intent paths (`block index "-"`,
   non-well-formed XML) now raise `Fail` with a message.
6. **"The harness itself."** The CI test step listed `test_r93_sheet_autofilter` and `test_r93_filter_semantics`,
   neither of which exists (the file is `test_r93_autofilter.py`); the step could never have passed since R93.
   Corrected. This is a harness bug found while adding the R96 names, not a feature.

Seventh pass, unassisted by tooling: **"what would a spreadsheet do that this cannot say?"** The answer is
everything in §B's adjacent list — evaluation, painting, icons — and it is why §J reports Class A at zero.

## H. Verification — what ran

Host JVM plus Python 3.11; openpyxl 3.1.5; no Gradle, no Android SDK, no Excel, no LibreOffice, no browser.

```
python3 tools/run_r96_checks.py
R96_CF_OK assertions=59 workbooks=2 sheets=3 rules=8 blocks=6 dxfs=3 foreign=1 refused=27
R96 conditional formatting: 8 rule(s) in 6 block(s) over 2 sheet(s), 2 rule-free sheet(s),
    5 resolved dxfId reference(s); openpyxl agreed on 8 rule(s) in 4 sheet(s)
R96 differential formats: 3 dxf entry(ies) in 1 workbook(s), 1 workbook(s) with no table;
    openpyxl agreed on 3 entry(ies) in 2 workbook(s)

PYTHONPATH=tools python3 -m unittest test_r96_sheet_conditional test_r96_sheet_dxf
Ran 72 tests ... OK

python3 tools/run_r47_contracts.py
R47 PASS: 2393 production-kernel assertions across 61 new kernels (+5 touched across 48/50 modules).

python3 tools/module_testability.py --check
{"not_established": 0, ..., "substantial_testable_subsets": 50, "testable_percent": 100.0, "total": 50}

python3 tools/native_source_audit.py --check
231 recorded requirements, 36 cross-cutting obligations and all native main-source package ownership checked.
```

The probe's 60 assertions cover the five kinds, the two formula counts for `between`, the tri-state flags, the
2- and 3-stop scales, the `percent` threshold, dxf sharing, attribute order in the emitted text, the block
position between `</mergeCells>`/`<dataValidations>`, the `<dxfs count>` value and position, the rule-free
workbook, the foreign package (five blocks, two read and three refused for stated reasons), and 27 refusals. The 72 controls tamper one thing each — a removed block, a
swapped block, an altered `sqref`, a reordered token list, an invented block attribute, a block moved before
`</sheetData>`, a block injected into a rule-free sheet and into a rule-free workbook, a removed rule, a
reordered pair, a duplicate priority, a changed priority, a broken attribute order, a dropped attribute, a
flipped and an invented flag, a changed and a dropped operator, a changed and an invented `rank`, a changed type,
a changed formula, a dropped second formula, broken markup, a dangling/dropped/invented `dxfId`, a changed
`cfvo` kind and value, a changed/transparent/dropped colour, a scale injected into a `cellIs`, five intent-table
tampers; and, for the table: removal, injection into a rule-free workbook, a move ahead of `</cellXfs>`, a
changed and a dropped `count`, a removed/reordered/appended entry, `<fill>` before `<font>`, four flag tampers, a
changed/alpha-less/invented font colour, a changed fill colour, a dropped `patternType`, a dropped foreground, a
dropped and a re-indexed background, an unmodelled child, and four intent tampers.

Full Python suite (artifacts for R88–R96 regenerated first, see §I):

```
PYTHONPATH=tools python3 -m unittest test_r56_reference … test_r96_sheet_conditional test_r96_sheet_dxf
Ran 761 tests in 9.509s
OK (skipped=257)
```

The skips are the suites whose Gradle-produced artifacts (`test_r56_*` … `test_r87_*`) cannot be regenerated on
this host: there is no Gradle distribution and no Android SDK, and CI has failed at the Kotlin compile step on
every commit including `main` since before R91, so no CI number exists to quote either. That is a real hole in
this round's evidence, not a rounding error.

## I. Regressions versus the best prior codebase (R95, `5b8fb56`)

* **None known on the R94/R95 lanes.** Their runners and checkers run unchanged in §H's sweep and their
  artifacts still verify.
* `XlsxStyledBook.kt`: +38/−3, all of it conditional-format wiring. Workbooks without rules emit no
  `<conditionalFormatting>`, no `<dxfs>`, and the ZIP entry order is unchanged — pre-interning formats adds
  strings to a list, it does not move the `styles.xml` part.
* `tools/run_r47_contracts.py`: one source path added to the existing `XlsxStyledBook` kernel, so the 61-kernel
  count and the 2393 assertions are unchanged; without it the isolated jar would not compile.
* `testing/build.gradle.kts`: one new `r96TailContracts` task, in the shape of R95's.
* `.github/workflows/android-r54-office.yml`: `:testing:r96TailContracts` added to the assemble step, two checker
  lines, two unittest names, and the R93 module-name correction from §G pass 6.
* Census documents: SHA-256 re-pin for `:format:ooxml` (69 production + 243 check paths), same 50/50 modules.
* Full-suite count: 761 tests `OK` with 257 skipped, against R95's 2521 `OK`. The difference is the sandbox:
  only the R88–R96 Gradle-free artifacts could be regenerated here, and every suite whose corpus comes from a
  Gradle task skips. The lanes that *can* run (R88–R96, nine runners) were all re-run in this pass and returned
  `rc=0`; R94 (`98` assertions) and R95 (`72` assertions) are byte-for-byte the same numbers as last round.

## J. Class A, the PR, and the next test

**Class A applications: 0.** The conditional-format model can state five kinds honestly and refuse the rest; it
cannot evaluate a rule against a value (`CondFormat.kt` has a different vocabulary and is not wired), it cannot
paint one, and it cannot reproduce an icon set. The distance to "a user opens a file with conditional formatting,
the rule fires and the cell looks right" is: an evaluation bridge from `Rule` to `CondFormat`, `iconSet` and the
text predicates, `dataBar`'s `extLst` extensions, and an engine to look at. None of that is in this turn.

**Census.** 50/50 modules (100.00 %) meet the bounded testability standard; 0/50 (0.00 %) are specification
accepted. `:format:ooxml` now counts 69 production files and 243 check artifacts under the re-pinned hashes.

**Next test that could fail.** Give `CondFormat` a rule compiled from this model — `cellIs`, `expression` and
`top10` at least — and compare its verdict against a Python re-implementation on the same `conditional.tsv`
values, exactly as R91/R93 did for validation and filters. That is a *verdict* test, and it would move the
component from "the file says the right thing" to "the rule fires when it should".
