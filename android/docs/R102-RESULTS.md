# R102 — What a differential format says, and what "first rule wins" cannot settle

Every painting `cfRule` points at a `<dxf>` in `styles.xml`. R96 modelled that element as "bold, italic, strike,
underline, a font colour and a solid fill" — exactly what the evaluator needed to answer *which cells fire*. R102
completes the element short of a gradient: font family and size, a number format, patterned fills, borders with up
to five edges, alignment, and protection — and carries all of it to the caller, merged field by field across the
rules that fired.

Three product files changed: `XlsxConditionalFormatting.kt` (+411), `XlsxConditionalFormatVerdict.kt` (+47) and
four added lines in `XlsxStyledBook.kt`, which owns the styles table. Everything else is evidence about them.

The most interesting thing this round produced is a **negative** result: the reader was wrong about two attributes
in a way no earlier lane could see, and the repair exposed a limitation that cannot be repaired inside this model.

## A. What was added

| Section | `<dxf>` child | Model | Refused when |
|---|---|---|---|
| font | `<b/> <i/> <strike/> <u/> <sz val/> <color rgb/> <name val/>` | flags + `fontSize` (1..409) + `fontName` (1..31 chars) | a size outside `1..409`, a non-finite size, an empty/oversized name, a control character |
| number | `<numFmt numFmtId formatCode/>` | `numberFormat` (1..255 chars), id registered in `<numFmts>` by the caller | an empty code, a code with a control character |
| fill | `<patternFill patternType>` + `fgColor`/`bgColor` | `fillRgb`, `fillPattern` (18 `ST_PatternType` values), `fillBgRgb` | a background without a pattern, a solid pattern without a foreground |
| alignment | `<alignment horizontal vertical textRotation wrapText indent shrinkToFit readingOrder/>` | `Alignment` | an empty alignment, rotation outside `0..180`, indent outside `0..250`, reading order outside `0..2` |
| border | `<border diagonalUp diagonalDown>` + five edges | `Border`/`Edge`, 13 `ST_BorderStyle` values | no edges at all, a diagonal flag without an edge, an edge without a flag, a colour that is not 6 hex digits |
| protection | `<protection locked hidden/>` | `Protection` | an empty protection |

Two boundaries are deliberate and stated because they refuse files Excel accepts:

* **`textRotation="255"`** is vertical text, not a rotation. This model holds an angle, so the value is refused
  rather than clamped to 180 — a dxf that stacks text and a dxf that rotates it are different instructions.
* **`style="none"`** on a border edge is not a token here; an edge that draws nothing is *absent*. Reading `none`
  as a style would put a five-edge border in the model where Excel draws nothing.

**The merge.** `Decoration` gained one field, `detail: Visual.Detail?`, and `Visual.Detail` carries the eight
fields above. The merge is field-wise first-wins, so a rule that fired earlier keeps every field it set and an
omitted field does not override anything. §B says why that is a hypothesis rather than a fact.

## B. Outcome space: H+, H−, H∂

**H+ (achievable).** `CT_Dxf` is a finite, schema-described element with no arithmetic; a complete model is a
matter of enumeration and of writing each section in the sequence order. This round's artifact confirms it for
every section except the gradient: six formats written, six read back field for field, by two independent parsers.

**H− (impossible under the stated constraints).** Two claims are out of reach here:

1. **"This dxf makes the cell look like X."** No renderer exists on this host. The model states what the file says.
2. **The *observable* meaning of a partially specified dxf.** Excel stores a *differential*: `<dxf><font><b/></font>`
   means "make this bold and change nothing else". Whether Excel applies that at property granularity or at
   *section* granularity — font name from rule 1, font size from rule 2, or rule 1's whole font — is not stated
   anywhere this project can read, and cannot be settled without Excel or LibreOffice.

**H∂ (boundary).** The merge granularity. R102 implements **field-wise** first-wins, because a field is the
smallest unit this model can state independently, and because the alternative (first whole section wins) would
silently discard a size that a lower-priority rule legitimately set. The boundary case is exactly the merge lane:
`B1` where rule 1 sets the family and a number format, rule 2 the size, a border and an alignment, rule 3 tries to
take the family and the number format back. Field-wise keeps all of rule 1 and rule 2; section-wise would keep
rule 1's font *and nothing of rule 2's border*. `detail.tsv` records which answer this implementation gives; a
renderer comparing the two is the next test.

## C. Frontier

| Question | Best available answer | Status |
|---|---|---|
| What may a `<dxf>` contain? | `CT_Dxf`: `font`, `numFmt`, `fill`, `alignment`, `border`, `protection`, `extLst`, in that order | six of seven modelled; `extLst` out |
| How many border styles? | 13 `ST_BorderStyle` values besides `none` | 13 modelled, count asserted |
| How many pattern types? | 18 `ST_PatternType` values besides `none` | 18 modelled, count asserted |
| How many horizontal/vertical alignments? | 8 / 5 | 8 / 5 modelled, counts asserted |
| Does a dxf's `numFmt` need an id? | `CT_NumFmt` requires `numFmtId`; Excel declares custom ids in `<numFmts>` | both written: the id is registered, the code is inline |
| What does openpyxl do with an id-only `<numFmt>`? | resolves nothing — its `DifferentialStyle.numFmt.formatCode` is the attribute only | our reader resolves through `<numFmts>`; asserted in the foreign lane |

The open interface between "best known" and "achievable here" is **the renderer**: every remaining question in this
table is a question about what Excel *does* with the bytes, and no tool on this host answers it.

## D. Negative space — 25 refused shapes and 4 dropped ones

Refused at construction, each asserted with its message: an empty format; a 32-character font name; an empty font
name; a font name with a control character; a size of 0; 0.5; 410.24; `NaN`; an empty number format; one with a
newline; one of 256 characters; a background colour without a pattern; a solid pattern without a foreground; a
border with no edges; a diagonal flag without an edge; an edge without a flag; a five-digit border colour; an empty
alignment; rotation 255; rotation 181; indent −1; indent 251; reading order 3; an empty protection; a font colour of
five hex digits.

Dropped on read, each asserted through `r102-foreign.xlsx`: a gradient-only dxf (→ `null`, and the rule that
pointed at it is then refused); `textRotation="255"` (→ `null`); an unknown border style on one edge (that edge is
dropped, the dxf survives with its other edge); an id-only `numFmt` whose id is not in `<numFmts>` (→ no code, so
the dxf is `null` if nothing else is set).

**The modal shortcut this round** is *"trust the flag's presence"* — the reader this project shipped until today
did exactly that:

```kotlin
bold = font?.children?.any { it.name.local == "b" } == true,
underline = font?.children?.any { it.name.local == "u" } == true,
```

so a dxf that says `<b val="0"/>` (clear bold) was read as **bold**, and `<u val="none"/>` (no underline) as
**underlined**. Both are legal spellings — `ST_OnOff` allows `val="0"` and `ST_UnderlineValues` has `none` — and
both were invisible to R96, R97, R100 and R101 because every fixture they held wrote the flags as bare `<b/>`.
The attribute form is not hypothetical: openpyxl writes `<b val="1"/>` for the flags it does emit (verified by
dumping its `styles.xml` on this host), which is what drew attention to how this reader parsed them. The test that
detects it is the foreign lane's first dxf — `<b val="1"/><i val="0"/><u val="none"/>` must read as
`bold, not italic, not underlined` — and its assertion `!format.italic` fails against the old reader.

A second shortcut is *"read the id, ignore the code"*: `numFmtId="164"` alone. Our writer emits both, so a bug that
used only the id would still pass on our own files; the foreign lane's dxf 1 carries `numFmtId` **without** a code,
which only resolves through `<numFmts>`, and the control `test_a_resolved_id_only_numfmt_is_caught` breaks the
fixture so that the lane would pass vacuously if it stopped checking.

## E. Mechanism portfolio

| # | Mechanism | Artifact | Proved portion | Unresolved interface | Cheapest falsification |
|---|---|---|---|---|---|
| 1 | Raw OPC re-encoding (`xml.etree`) | `xl/styles.xml` × `formats.tsv` | every field, its absence, and the section order | nothing about Excel | `test_a_shuffled_section_order_is_caught` |
| 2 | Public `XlsxStyledBook.read` round trip | 6 formats + 1 merge | field-for-field identity, `Format.equals` | a shared bug in writer+reader | drop `fontSize` from `describe` |
| 3 | openpyxl 3.1.5 `_differential_styles` | same 6 formats | an unrelated dxf model agrees | fields openpyxl defaults away | `test_a_changed_alignment_is_caught` |
| 4 | `<numFmts>` id resolution | `styles.xml` + foreign dxf 1 | a code reachable only by id | Excel's choice when id and code disagree | `test_a_dropped_numfmt_registration_is_caught` |
| 5 | Python re-derivation of the merge | `merge.tsv` → `detail.tsv` | the first-wins rule, re-implemented | Excel's granularity (§B) | `test_a_reordered_merge_is_caught` |
| 6 | Foreign-package refusals | `r102-foreign.xlsx` | that each refusal has a reason | — | `test_a_dropped_gradient_fill_is_caught` |
| 7 | Enumeration cardinalities | `BorderStyle`/`PatternType`/`Horizontal`/`Vertical` | 13/18/8/5 against the schema's counts | a wrong count in the schema itself | change a count constant in the probe |

Seven mechanisms ran. They are independent in the stronger sense: 1 and 3 read the same bytes through different
code, 5 re-implements the evaluator instead of calling it, and 7 would catch a model that is complete but
mislabelled.

## F. Artifact requirement, and the four honest holes

`tools/run_r102_checks.py` writes `r102.xlsx` (six formats, one per section group plus a kitchen sink),
`r102-foreign.xlsx` (five dxfs), `formats.tsv` (intent), `merge.tsv` (the three merge inputs), `detail.tsv` (the
merged outcome) and `summary.txt`. Current output:

```
R102_DXF_OK assertions=113 refusals=25 formats=6 sections=6 merged=3 foreign=4
R102 check: 6 dxf(s) raw, 6 through openpyxl, 3 merged detail(s) recomputed, 5 foreign dxf(s)
```

The holes, stated rather than buried:

1. **No application ran.** No Excel, LibreOffice, browser, emulator, device or Gradle on this host. Nothing here
   says a cell was painted, a border drawn or a number displayed differently.
2. **Negative flags collapse into "no opinion".** `Format(bold = false)` is indistinguishable from an absent
   `<b/>`, so a foreign `<b val="0"/>` reads as `false` — the probe's assertion is true either way. This is
   information loss, deliberately not hidden behind a tri-state that the rest of the model would then have to
   carry.
3. **Merge granularity is a hypothesis** (§B). Field-wise first-wins is implemented and recorded; Excel's
   behaviour is unknown here.
4. **`extLst`, gradient fills and vertical text are out.** A dxf carrying only them is `null`, which is a stated
   refusal, not a silent success.

## G. Second-order adversary — seven passes

1. **Write every default.** Emit `wrapText="0"`, `shrinkToFit="0"`, `textRotation="0"` on every alignment and
   `locked="1" hidden="0"` on every protection → the raw lane compares the absent attribute (`-`) with what was
   written; `test_a_flipped_protection_is_caught` shows the comparison bites.
2. **Shuffle the sections.** `CT_Dxf` is a sequence; a writer that emitted `protection` before `font` would still
   parse here and make Excel offer a repair. Lane 1 checks the *positions* of the child elements, and
   `test_a_shuffled_section_order_is_caught` moves the kitchen sink's font behind its protection.
3. **Skip the `<numFmts>` registration.** Then `numFmtId="164"` dangles; lane 1 resolves every dxf id against the
   declared table, and `test_a_dropped_numfmt_registration_is_caught` deletes the declaration.
4. **Reuse one id for two codes.** `numFmtId="164"` twice with different codes: the declaration lookup returns the
   wrong code, which `test_a_numfmt_id_collision_is_caught` demonstrates.
5. **Point a rule past the table.** `dxfId="9"` with six dxfs: the rule silently loses its format. Lane 1 asserts
   the set of ids a sheet uses equals the set of dxfs that exist.
6. **Invent a detail for a decoration-only format.** A `Format(bold = true)` must merge to `detail == null`; the
   control writes a detail row for it and the merge lane refuses it.
7. **Keep the refusals but remove the fixture that demonstrates them.** If the gradient fill vanished from
   `r102-foreign.xlsx`, the probe would still "refuse" it (nothing to read) and the lane would pass vacuously —
   so lane 4 asserts the gradient, the `255` rotation, the unknown style and the id-only `numFmt` are *present* in
   the fixture.

## H. Verification — what actually ran

| Lane | Command | Result |
|---|---|---|
| R102 probe + 4 lanes | `python3 tools/run_r102_checks.py` | `R102_DXF_OK assertions=113 refusals=25 formats=6 sections=6 merged=3 foreign=4`, then `6 raw, 6 openpyxl, 3 merged, 5 foreign` |
| R102 controls | `PYTHONPATH=tools python3 -m unittest tools.test_r102_dxf` | `Ran 29 tests … OK` |
| R96 (prior dxf surface) | `tools/run_r96_checks.py` | `assertions=60 … dxfs=3`, unchanged |
| R97 (verdict surface) | `tools/run_r97_checks.py` | `assertions=70 … verdicts=33 … dxfs=6`, identical to R101 |
| R94, R95, R98, R99, R100, R101 | their runners | green, counts unchanged |
| R92 (password corpus) | `tools/run_r92_checks.py` | `assertions=38 … passwords=311` |
| R47 kernels | `python3 tools/run_r47_contracts.py` | `R47 PASS: 2393 assertions across 61 kernels` |
| whole Python suite | `python3 -m unittest <all tools/test_r*.py>` | `Ran 2269 tests … OK (skipped=269)` |
| census | `python3 tools/module_testability.py --check` | `50/50 (100.00%)`, `not_established: 0` |

**Substantial enough code to test — absolute and percentage.** Against the repository's own census standard (50
modules): substantial testable subset **50/50 = 100.00%**; specification-accepted modules **0/50 = 0.00%**. For
this feature: `<dxf>` sections with an executable witness **6/7 = 85.7%** (`extLst` out); border styles enumerated
and cardinality-asserted **13/13 = 100%**; pattern types **18/18 = 100%**; alignment enums **13/13 = 100%**
(8 horizontal + 5 vertical); model refusals **25**; independent read-back mechanisms **3** (ours, raw OPC,
openpyxl) plus a Python re-derivation of the merge.

**Source mutations actually executed** (each recompiled and run, then reverted):

| Mutation | Detected by | Message |
|---|---|---|
| writer drops `shrinkToFit` when it is `false` | the probe's file lane | `an explicit false must be written, not omitted` |
| reader treats any `<u>` as an underline | the probe's foreign lane | `u val=none was read as an underline` |

Both reverted; the counts above were re-measured afterwards, not carried over.

**Tools that exist here and were used:** pinned kotlinc 2.2.21 + OpenJDK 17 (host JVM), openpyxl 3.1.5, CPython
3.11 `unittest`/`zipfile`/`xml.etree`, npm-installed node modules for the R87 lanes.
**Not available, therefore not claimed:** Excel, LibreOffice, ExcelJS, docx-preview, any browser, any emulator, any
Android device, any theorem prover, any SMT solver, any spreadsheet renderer, any GPU.

## I. Regressions and support edits versus R101 (`90c8d51`)

No behavioural regression: R92, R94–R101 reproduce their counts exactly, including R97's 33 verdicts and R96's 3
dxfs, and R47's 2393 assertions are unchanged. Three edits outside the two main product files, each named with its
reason:

1. **`XlsxStyledBook.kt` (product, four added lines).** A dxf's number format needs an id in `<numFmts>`, exactly as a cell
   style's does; `internDxf` now registers the code and `toXml` passes the id. Without it a dxf would carry
   `numFmtId="164"` that the workbook never declares.
2. **`R97Checks.kt` (test probe, 4 lines).** Its `visual()` is an exhaustive `when` over `Visual`; the new
   `Visual.Detail` broke compilation. The branch is unreachable for R97's fixtures (asserted by unchanged counts)
   and exists instead of an `else`, so a sixth visual could never slip through silently.
3. **`build.gradle.kts`, the office workflow, the census** — wiring and evidence registration.

One **bug fixed** rather than introduced: `<b val="0"/>` and `<u val="none"/>` were read as bold and underlined.

## J. Class A, and the next test

**Class A applications: still 0.** The census's 100% measures executable testability, not acceptance. What R102
changes is that a conditional format's *paint payload* is now complete over `CT_Dxf` short of a gradient, so the
remaining gap is not "what can we write" but "whether Excel agrees with the merge we chose".

Next tests, in order of information per minute:

1. **LibreOffice headless render** of `r102.xlsx` compared against `detail.tsv`, with two rules that set different
   font properties — this settles §B's merge granularity, the single hypothesis this round could not test.
2. **Excel opening the kitchen-sink dxf** — the only way to learn whether a five-edge border with both diagonal
   flags round-trips through the UI's own "Format Cells" dialog without repair.
3. **A workbook whose dxf `numFmtId` and inline `formatCode` disagree**, opened in Excel, to learn which wins.
