# R91 — SpreadsheetML data validation (`CT_DataValidation`) and the predicate it describes

Branch `arena/92b53607-osb` on top of R90 (`d2f4bc6`). Production footprint: **one new file**
(`XlsxValidation.kt`, 331 lines) plus **three edits / six lines** in `XlsxStyledBook.kt` — a new trailing
`StyledSheet` field, one emission line after `</sheetData>`, one parse argument. No existing line was
reordered, renamed or removed.

## 0. The restoration request — the tree *was* rewound this time

Unlike R90, this turn really did have something to restore, and it was not in the files: the sandbox came back
with the local branch at `92a1578` and the whole R90 change set **uncommitted in the working tree**, while the
remote branch and PR #22 already held the R90 commit. Recovery, in order:

1. `git fetch origin arena/92b53607-osb` → `d2f4bc6` (the R90 commit I pushed last turn);
2. `git add -A` and compare trees: staged tree `e3bc260b…` **equals** `FETCH_HEAD^{tree}` `e3bc260b…` — the
   working tree was byte-identical to the pushed commit, so nothing was lost and nothing needed rewriting;
3. `git reset --mixed FETCH_HEAD` re-pointed the local branch at the commit while leaving every file in place,
   then `git branch --set-upstream-to=origin/arena/92b53607-osb`.

`git status` is clean at `d2f4bc6` before R91 begins. The toolchain also had to be rebuilt (`bootstrap_host_tools
install`, `npm ci --prefix tools/r87node`, `openpyxl==3.1.5`, `python-docx==1.2.0`, `ssf==0.2.2`,
`babel==2.9.1`, `formualizer`) because the host caches do not survive the rebuild.

## A. Formal model

**A.1 Admissible objects.** A *rule* is `Rule(sqref, type, formula1, operator, formula2, allowBlank,
hideDropDown, showErrorMessage, errorStyle, errorTitle, error, showInputMessage, promptTitle, prompt)`:

* `type` ∈ {`whole`, `decimal`, `list`, `date`, `time`, `textLength`, `custom`} — the CT_DataValidation set
  minus `none`, which is the absence of a rule and therefore not representable;
* `operator` ∈ the eight ST_DataValidationOperator tokens; `between`/`notBetween` take two operands, the other
  six take one;
* `sqref` is `A1` or `A1:B2`, space-separated, **upper case only** (the writer normalises before emitting, so
  a lower-case input is refused rather than silently rewritten);
* `hideDropDown` is the *intended* meaning of the OOXML attribute `showDropDown`, which the specification
  inverts.

**A.2 Access model.** The writer sees the whole sheet; the reader sees only the package bytes; a *witness*
(openpyxl) sees only the package bytes through its own object model; the oracle sees only the rule parameters
and the value, never the Kotlin source.

**A.3 Counted resource.** The bytes of `xl/worksheets/sheetN.xml` (the `<dataValidations>` element and its
`count` attribute) and, for the predicate, **one decision per (rule, value) pair** — no time or memory claim.

**A.4 Success / failure, with quantifier order.** Success is: *for every* rule in the intent table, *there
exists* no attribute whose value differs from the declaration, and *for every* (rule, value) pair in the
corpus, the JVM's verdict and the Python oracle's verdict are the same token. Failure is a single
counterexample, printed with its file, sheet, index and line number.

**A.5 Parameter regime, units, tolerances.** Exact equality everywhere; no tolerance exists. Dates and times
are compared as **serial numbers in the 1900 system** (`43831` = 2020-01-01, `44196` = 2020-12-31), because
that is what this writer stores. `textLength` counts **UTF-16 code units**, which is what Excel's `LEN`
returns, so an astral character counts twice.

**A.6 Boundary conditions.** `whole` refuses a non-integral value *before* comparing (Excel refuses `3.5` for
a whole-number rule); `list` matches case-insensitively and also matches a numeric cell against the literal's
spelling; `between` is inclusive on both ends; an empty operand, a half-quoted list literal, a row-0 `sqref`,
and an operator on `list`/`custom` are all refused at construction.

**A.7 Adjacent non-substitutes.** Conditional formatting (`R77`), sheet protection (`<sheetProtection>`),
number formats (`R76`), cell borders, the "circle invalid data" tracker, dropdown *rendering*, and input-message
*popups* are different features. In particular: a data-validation rule **does not** reject a value written by
the writer (Excel only validates *user entry*), so nothing here claims that an invalid value was refused.

## B. Outcome space against the pre-declared threshold (`docs/R72-PLAN.md` §B)

**H+.** Before R91 the writer could not express any data-validation rule: a spreadsheet built by this code
could not offer a dropdown, bound a quantity, or show an error message. Now 7 rules over 3 workbooks are
written, read back through this code, and independently read by openpyxl as the same 7 records — including the
inverted dropdown flag — and 228 (rule, value) decisions are reproduced by a second implementation.

**H−.** This does not make the spreadsheet application Class A. No Excel, LibreOffice, Android or browser
opened these files: whether Excel *shows* the dropdown, *blocks* the entry, or *repairs* the sheet is unknown
here. The predicate models what Excel **should** decide for decidable rules; it cannot prove Excel agrees.

**H∂.** The answer changes at the decidability boundary: `custom`, range-referenced lists, `DATE()` operands
and boolean cells return `undecided` (122 of 228 rows). That is the honest third value — neither "accept" nor
"reject" — and the oracle treats a missing `undecided` row as a failure so the refusal path cannot rot.

## C. Frontier, restated with the hypotheses it imports

Best-known practice, retrieved rather than assumed: ECMA-376 Part 1 §18.3.1.32 defines the record and its
attribute defaults (`allowBlank`, `showDropDown`, `showErrorMessage`, `showInputMessage` all default to
`false`; `errorStyle` defaults to `stop`); §18.3.1.32 note and Excel's own writer establish the **inverted**
`showDropDown`; openpyxl 3.1.5 and LibreOffice both model the record as a single element with two operand
children. The exact open interface between "record verified" and "application behaviour verified" is *the
application*: no result here crosses it. Imported hypotheses, each falsifiable: (i) `textLength` counts UTF-16
units (matches `LEN`); (ii) `list` membership is case-insensitive in Excel; (iii) serial dates are what a
`date` rule compares when the cell holds a number. None is discharged by anything in this repository.

## D. Negative space — 16 shapes that look like a solution and are not

1. Emitting `<dataValidation>` **before** `<sheetData>` (schema order violation; Excel repairs the file).
2. Writing `showDropDown="1"` when the caller asked to *show* the dropdown (the classic inversion bug).
3. Writing an `operator` attribute on `list`/`custom` (ignored by Excel, misleading to a reader).
4. Writing `formula2` for `between` as an empty element (Excel reads an empty operand, not "no operand").
5. Leaving `count` stale after adding a rule (Excel tolerates it; a verifier should not).
6. Escaping only `&` and leaving `<` in an error message (truncates the document).
7. Double-escaping (`&amp;amp;`), which round-trips to the wrong text.
8. Storing `sqref` lower-case (`a1:b2`) and letting Excel uppercase it — the model would disagree with the bytes.
9. Treating `custom` as "accept everything" instead of "undecided".
10. Treating an unevaluable rule as `false` (a false *rejection* is as wrong as a false acceptance).
11. Comparing a text cell to a numeric rule and answering `false` (Excel never validates text against `whole` as
    a number; the honest answer is undecided).
12. Counting `LEN` in code points, which under-counts astral characters by one.
13. Matching list items case-sensitively (`GREEN` rejected for a list containing `green`).
14. Reporting "no validations" for a sheet because the reader only looked at `<sheetData>`.
15. Claiming the dropdown *renders* because the record parses.
16. Claiming an invalid value was *rejected* — the writer never validates, and Excel validates only user entry.

## E. Mechanism portfolio — six mechanism-distinct approaches

| # | Mechanism | Artifact | Proved portion | Unresolved interface | Cheapest falsifier | Gap local? |
|---|---|---|---|---|---|---|
| 1 | Raw OPC attribute inspection | `check_r91_sheet_validations.py` | every attribute, its order, `count`, operand presence | nothing beyond bytes | drop a rule, expect refusal | local |
| 2 | openpyxl object model | same file, second half | record semantics through an independent model | openpyxl ≠ Excel | un-invert the flag | local |
| 3 | Differential decision oracle (Python, `Decimal`) | `check_r91_validation_semantics.py` | 228 decisions agree | Excel's own truth table | flip one verdict | local |
| 4 | JVM kernel (`XlsxValidation.accepts`) | `R91Checks.kt` | decidable semantics, `undecided` discipline | custom formulas need a formula engine | custom rule decided | **equivalent to** solving formula evaluation |
| 5 | Round-trip through this code's reader | `R91Checks.kt` §4 | write→read equality of all 14 fields | self-consistency only | mutate one field | local |
| 6 | Escaping inverse under an independent parser | `test_r91_escaping.py` | `& < > "` and UTF-8/astral survive | none | drop `&amp;` | local |

Approaches 1–3 and 5–6 are independent of each other (different parsers, different languages, different
models). Approach 4 is the only one whose gap is not local: deciding `custom` rules is *equivalent to*
implementing a spreadsheet formula evaluator over the sheet, which is the rest of the product.

## F. Artifact requirement

Every claim names a file a reader can re-open: `testing/build/r91/artifacts/` (`dv-plain.xlsx`, `dv.xlsx`,
`dv-escaping.xlsx`, `validations.tsv`, `validation_verdicts.tsv`). The intent table and the corpus are written
by the probe *before* any verifier runs; the verifier refuses a folder whose workbook set disagrees with the
table, and refuses a corpus that contains no `undecided` row.

## G. Second-order adversary — what attacked this work, and what it caught

**G1.** `esc()` escapes `"` in element text as well as in attributes. My first assertion expected
`<formula1>"a&amp;b…"</formula1>` and the actual bytes were `<formula1>&quot;a&amp;b…&quot;</formula1>`. Both
are valid XML; the code was right and the *assertion* was wrong. Fixed by asserting the actual behaviour and
documenting the deliberate choice: one escaper for both contexts, at the cost of a `&quot;` the schema would not
require. The negative control `test_quoting_survives_as_quotes` pins the count (4), so a change in either
direction is visible.

**G2.** `kotlinc -Werror` rejected a redundant `else` in an exhaustive `when`, and earlier an unsupported
`\u{1D11E}` escape. Both were real portability findings at the pinned compiler, both fixed in the probe rather
than by relaxing the flags.

**G3.** The first `test_dropped_rule_is_rejected` produced an XML `ParseError` that escaped the verifier as a
crash rather than a failure. The verifier now converts a malformed worksheet into `Fail`. This is the same class
of defect R90 found in the checker derivation: a failure path that is indistinguishable from a bug.

**G4.** The verdict corpus initially lacked the operator column, which made the Python oracle unable to
distinguish `between` from `greaterThanOrEqual`. Caught while designing the oracle, before any claim was made.
The corpus now carries 8 fields and the checker validates the field count.

**G5.** A stale census hash and a missing `formualizer` install broke the first full discovery run (2058 tests,
170 skips). Both were provisioned rather than papered over; the final run is 2267 tests with zero skips.

## H. Verification — what ran

**H1. R91 probe and two lanes** (`python3 tools/run_r91_checks.py`):
`R91_OFFICE_OK assertions=43 workbooks=3 rules=7 verdicts=228`; then *7 data-validation rules in 2 workbook(s)
verified, including the inverted showDropDown flag; openpyxl agreed on 7 rule(s)*; then *228 verdicts over 6
rules (106 decided, 122 refused as undecidable) recomputed in Python with Decimal arithmetic and identical to
the JVM*.

**H2. Negative controls — 39 tests, all green**: `test_r91_sheet_validations` (16), `test_r91_validation_semantics`
(16), `test_r91_escaping` (7). They cover: a dropped rule, a stale `count`, a changed type, reordered
attributes, a swapped dropdown polarity, an operator on a list, a missing second operand, a changed `sqref`, a
dropped error text, an invented rule in the unvalidated workbook, dropped/invented intent rows, a wrong field
count, a tampered openpyxl comparison, a malformed worksheet, flipped verdicts, an `undecidable` reported as
`true`, an empty corpus, a corpus with no refusal row, and a missing corpus. The oracle's own arithmetic
(`utf16_length`, `spreadsheet_spelling`, `literal`, `list_items`, complement pairs) has unit tests that need no
artifacts.

**H3. Regression sweep — 38 previously-recorded runners re-executed serially, every one exit 0.** 23 lanes
(R91, R90, R89, R88, R87 shade, R86 Writer tabs, R86 sheet views, R84 Writer indent, R84 sheet visibility,
R83 Writer spacing, R83 hidden rows, R82 Writer align, R82 sheet align, R81 Writer tables, R81 sheet rows,
R77 conditions, R76 Writer layout, R76 sheet formats, R75 fractions, R74 links, R73 oracle, and the two R72
pinned-vs-candidate comparators — *core readers* over 13 archives and the *ooxml import budgets* over 30
archives, both of which compile baseline `aacae239` and the current tree and find no behavioural drift) and 15
R5x/R6x corpus generators (R56 24 451 assertions, R57 548, R58 6 428, R59 1 580 + 200 DOCX, R60 675 + 256 DOCX
and 6 588 + 64 XLSX, R61 1 831, R62 1 288 + 1 434, R63 1 522 + 2 246, R64 1 093 + 814 + 613).

**H4. Full host discovery.** `python3 -m unittest discover -s tools -p 'test_*.py'` →
**`Ran 2267 tests … OK`**, zero skips (R90: 2228; +39 = exactly the new R91 controls).

**H5. Census and audit.** `module_testability.py --check` → **50/50 modules (100.00 %) meet the bounded
testability standard; 0/50 accepted (0 %)**; `native_source_audit.py --check` → **231 requirements / 36
cross-cutting obligations**, reproducing.

**H6. Not run / not claimable.** No Android build, emulator, device, provider or picker gate. No Excel,
LibreOffice, browser or Word. **No visual inspection was possible** — `soffice`, `libreoffice` and a headless
browser are absent from this host — so no screenshot, dropdown or message box exists for R91; the decision
corpus is the strongest evidence available here and it is semantic, not visual.

## I. Regressions versus the best prior codebase (R90, `d2f4bc6`)

None found, and the mechanism that would have exposed one was run: `XlsxStyledBook` is the file every sheet lane
touches, so all seven sheet lanes (R76 formats, R77, R81, R82, R83, R84, R86) were regenerated from scratch
after the change, plus both R72 pinned-vs-candidate comparators, which compile the pre-change baseline and the
current tree and compare per-fixture outcomes. All green. The three edits are additive: a trailing defaulted
field (`validations = emptyList()`), a guarded emission (`if (sheet.validations.isNotEmpty())`), and a reader
call whose result is `emptyList()` for every sheet without the element — which is why `dv-plain.xlsx` is
byte-identical in structure to an R90 sheet (`</sheetData></worksheet>`), asserted in the probe and again in
`test_r91_escaping`.

Residual risk: `StyledSheet` gained a parameter; every construction site in the tree uses named arguments or
defaults (the probe's and the seven sheet lanes' constructions compile with `-Werror`), and there is no
destructuring of `StyledSheet` in the tree.

## J. Class A, the merge, and the next test

**Class A: still 0 applications.** Two independent record readers and a second decision implementation agree,
and none of them is Excel. The gap between "the bytes say a dropdown exists" and "a dropdown appeared" is not
crossed by anything in this turn.

**The merge.** The instruction was to merge at the end of the turn, so PR #22 — which now carries R90 and R91 —
is merged into `main` with `gh pr merge 22 --merge`; the branch is not rewritten and no history is squashed.

**Next test that would move the frontier.** Open `dv.xlsx` in Excel (or LibreOffice with the validity list
inspector) and record: does the dropdown appear on `D2:D10`; is `3.5` refused in `B2:B10`; is `GREEN` accepted
in the list; does the error message text survive with its `&` and `<>`. That is four observations and it is the
only measurement that can turn the record agreement into a behaviour claim.
**Carried, unimplemented:** device-accelerated video upscale and "remove mosaic" — unchanged and still refused
for the same information-theoretic reason; no code was written to pretend otherwise.
