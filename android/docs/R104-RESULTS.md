# R104 — What a conditional-format *expression* may say, and whether the app opens in Rabbit 1

Two questions this round, of different kinds.

The first is a product defect. Every conditional-format rule whose kind is `expression` carries one formula, and
until R104 that formula was read by two regular expressions (`ISBLANK(G4)`, `MOD(ROW(),2)=0`) plus a naive split at
the first comparison operator. Anything else — `AND(A1>5,B1<2)`, `LEN(A1)>3`, `LEFT(A1,3)="app"`,
`ROUND(A1,3)=1.5` — was answered *"undecided"*. That is honest, but it is also a refusal to do the job: a real
workbook is full of those formulas, and the product had no answer for them. Worse, the naive split gets precedence
wrong (`1+2*3=7` would compare `1+2` with `3=7`), so the two shapes it did accept were read by a method that
happens to work only because each has exactly one operator.

The second question is whether a person can open this repository in Android Studio Rabbit 1 and press Run. Nothing
in the sandbox can build it — there is no Android SDK, no Gradle distribution, no JDK on the system path — so the
honest answer is a **configuration gate plus a documented laptop path**, not a claim that it built.

## A. Formal model

**Admissible objects.** A formula is a string of at most 8192 characters (the model's own bound). Its grammar is

```
formula    := comparison
comparison := additive (op additive)?            op ∈ { < <= > >= = <> }, at most one
additive   := multiplicative (('+' | '-') multiplicative)*
multiplic. := unary (('*' | '/') unary)*
unary      := '-' unary | '+' unary | primary
primary    := number | "text" | TRUE | FALSE | cell-ref | name '(' args ')' | '(' formula ')'
args       := formula (',' formula)*             0..8 arguments
```

with `cell-ref` exactly `[$]COL[$]ROW` over the SpreadsheetML grid, `name` one of sixteen functions — `AND`, `OR`,
`NOT`, `ISBLANK`, `ISNUMBER`, `ISTEXT`, `LEN`, `TRIM`, `UPPER`, `LOWER`, `LEFT`, `RIGHT`, `ABS`, `ROUND`, `MOD`,
`ROW` — and nothing else.

**Information model.** The evaluator sees the rule's `sqref`, the formula text, and a sparse grid of the four values
this model knows: blank, number, text, boolean, plus error values read from the sheet. It sees no workbook, no sheet
name, no recalculation engine, no locale.

**Resource counted.** Parsing is `O(tokens)` with `tokens ≤ 512`; evaluation is `O(nodes)` with `nodes ≤ tokens`;
each reference costs one grid read. Nesting is capped at 16, arguments at 8, both enforced during the descent,
so no formula can make the reader recurse without bound.

**Success, with quantifier order.** For every block, every token of its `sqref`, every cell of that token, and every
`expression` rule in it: the verdict is `Yes` if the formula evaluates to a true boolean or a non-zero number,
`No` if it evaluates to false, zero or blank, and `Unsupported(reason)` otherwise — never a bare `No` for a formula
the reader could not read. References shift exactly as R103 defined, inside every function argument.

**Regime and boundaries.** Excel's own type rules are kept rather than invented: `"5"=5` is FALSE, `"5">5` is TRUE,
blank reads as zero against a number and as `""` against text, text comparison is case-insensitive, and *ordering*
two different texts is `Unsupported` because this host has no collator. `ROUND` keeps Excel's fifteen significant
decimal digits and then halves away from zero; `MOD` takes the divisor's sign. Division by zero and `MOD(x,0)`
produce `#DIV/0!`, which propagates as a refusal rather than a verdict.

**Adjacent problems that must not be substituted.** (i) *Cell-formula evaluation* — `FormulaEval` and
`FormulaXlsx` answer a different caller and are untouched. (ii) *Array semantics* — `A1:A3` is not a range
reference here, it is a refusal. (iii) *Cross-sheet references* — `Sheet2!A1` stays out. (iv) *Defined names* —
`foo` is a refusal, not a lookup. (v) *Painting* — a verdict is still not a rendered cell.

## B. Outcome space

Exactly three outcomes exist per rule, and the third is not a synonym for the second:

1. `Yes` — the formula evaluated to true. The rule's `dxf` is what a painter would apply.
2. `No` — the formula evaluated to false, zero or blank.
3. `Unsupported(reason)` — the formula is outside the grammar, an argument is the wrong type, a reference left the
   grid, or the result is text. `reason` names the formula and the failing position.

The measured split over the round's table: **58 fire, 15 do not, 27 are refused**.

## C. Frontier — what this actually closed

Before R104, of the sixteen functions above, **two** were readable (`ISBLANK`, `ROW`/`MOD` as one hand-written
shape). After R104, **16 of 16** are readable, with **100** formula cases and **14** written rules. Concretely, the
previous reader would have mis-answered or refused:

| Formula | Before R104 | R104 |
|---|---|---|
| `AND(A1>5,B1<2)` | undecided | decided |
| `1+2*3=7` | decided *wrong* — split at the last `=` compares `1+2` with `3=7` | TRUE |
| `LEN(A1)>3` | undecided | decided |
| `ROUND(2.675,2)=2.68` | undecided | TRUE (fifteen-digit step, §A) |
| `"5">5` | decided (accidentally right) | TRUE, by the documented type rule |
| `SUM(A1:A2)>0` | undecided, reason `outside the decided grammar` | undecided, reason `'SUM' is not a function this reader evaluates` |
| `1/0>0` | undecided | undecided, reason names `#DIV/0!` |

The last two rows are the point of the refusal design: the reader now says *why* it stopped, which is what turns an
undecided verdict into something a person can act on.

## D. Negative space — twelve shapes that look like fixes but are not

1. **Coerce numeric text into numbers.** `"5"=5` would become TRUE. Excel says FALSE, and this model documents the
   same rule in `order()`. It would also silently change every comparison against a text-formatted cell.
2. **Treat any text result as TRUE.** A rule whose formula is `A1` where `A1` holds `"apple"` is not a condition.
   Firing it would paint cells the spreadsheet would leave alone.
3. **Split at the first top-level operator.** That is the defect being replaced; it cannot express precedence.
4. **Evaluate ranges as arrays.** `A1:A3>0` would need Excel's array rules (implicit intersection, spilling). A
   one-cell approximation would answer about the wrong cell with full confidence.
5. **Answer `No` for an unknown function.** `FOO(A1)` is not "false", it is unread.
6. **Clamp an off-grid reference.** R103 already decided this: clamping reads a different cell silently.
7. **Drop unsupported arguments.** `AND(A1,Weird())` with the second argument skipped would answer about a
   weaker formula than the file contains.
8. **Use `Math.round` for `ROUND`.** Java rounds a negative half toward positive infinity: `ROUND(-2.5,0)` → -2.
   Excel says -3.
9. **Round the binary double without the decimal step.** `ROUND(2.675,2)` → 2.67, while Excel answers 2.68. The
   repository already records this divergence (`docs/R93-RESULTS.md`, `tools/r73_formula_cases.py`).
10. **Locale-dependent `UPPER`/`LOWER`.** Turkish dotless i would make the answer depend on the device.
11. **`MOD` with the host's `%`.** Kotlin's `%` takes the dividend's sign; Excel's `MOD` takes the divisor's.
12. **Cap nesting by catching `StackOverflowError`.** A crash caught is not a parse, and the recovery position is
    unknowable; the cap is enforced during the descent, at 16.

## E. Mechanism portfolio

* **Tokenizer → parser → evaluator**, in the product file, with every token either consumed or refused.
* **A hand-written expectation table** (`CASES`, 100 rows) whose expected answers were written before the evaluator
  was run. Three of them failed on the first run, and the failures were resolved in the *right* place each time: two
  were wrong expectations and were fixed in the table (a shift case that assumed a different anchor, and a
  `SUM(A1:A3)` case that refuses at `:` before it ever reaches the function name), and one — `ROUND(-2.5,0)=-3` —
  was a real evaluator defect, because Java's `Math.round` rounds a negative half toward positive infinity.
* **An independent Python implementation** (`tools/check_r104_grammar.py`) that re-tokenises, re-parses and
  re-evaluates all 100 cases and requires *three* answers to agree: the table's expectation, the Kotlin answer, and
  its own — including the refusal text.
* **Controls on that independent lane** (33 tests) so the second opinion is itself tested.
* **Four executed source mutations** (§H), each recompiled and rerun, each reverted.
* **The file lanes**: raw `<formula>` text in `sheet1.xml` and openpyxl 3.1.5's parse of the same 14 rules.

## F. Artifact requirement, and the three honest holes

Artifacts this round (regenerated by `python3 tools/run_r104_checks.py`, not checked in):
`testing/build/r104/artifacts/{r104.xlsx, cases.tsv, rules.tsv, values.tsv, summary.txt}`.

1. **No Excel or LibreOffice ran.** Every function's semantics here is derived from documentation and from this
   repository's own prior reference implementation (`r73_formula_cases.excel_round`), not from a spreadsheet
   application. Where Excel's answer is disputed or undocumented, the reader refuses rather than choosing.
2. **`LEN` counts UTF-16 units on the Kotlin side and code points in Python.** They agree on the whole BMP and
   disagree for characters outside it (emoji). The case table is ASCII, so this is untested, not settled.
3. **`formatNumber` (text rendering of a number, used by `LEN`, `LEFT`, `TRIM`, `UPPER`, `LOWER`) renders fifteen
   significant digits.** Excel's general format switches to scientific notation at different thresholds for some
   magnitudes; that boundary is not modelled and would show up as a text mismatch, not a wrong verdict.

## G. Second-order adversary — seven passes

1. **Precedence.** `1+2*3=7`, `2*3+4=10`, `(1+2)*3=9`, `8-3-2=3`, `8/4/2=1` — driven by removing the
   multiplicative level entirely (mutation 3) and watching the probe fail.
2. **Bounds.** 8 arguments accepted, 9 refused; nesting 16 accepted, 17 refused; 512 tokens accepted, more
   refused; 8192 characters refused at model construction.
3. **Malformed text.** Unterminated literal, doubled quotes inside a literal, a lone `%`, a lone `&`, a trailing
   operator, two numbers in a row, an empty argument list.
4. **Type confusion.** Text in arithmetic, text in `AND`, error in `LEN`, error in a comparison, boolean against a
   number, blank against text, number against text.
5. **Numeric edges.** `1e3`, `.5`, `3=3.0`, `-0` (as `0-0`), `ROUND` at ±15 digits, `MOD` with a negative divisor,
   division by zero, `ROUND(-2.5,0)`, `ROUND(2.675,2)`.
6. **Reference interaction.** Locked and unlocked references inside function arguments, at the anchor and three
   rows away, plus a locked reference that must not move.
7. **The independent lane's own integrity.** A wrong expectation in `cases.tsv` cannot pass: the Python lane
   compares against both the table and the Kotlin answer, so a bad table row fails on two counts.

## H. Verification — what actually ran

| Lane | Command | Result |
|---|---|---|
| R104 probe + checker | `python3 tools/run_r104_checks.py` | `R104_GRAMMAR_OK assertions=220 cases=100 decided=73 refused=27 functions=16 rules=14`; then `100 case(s) re-evaluated in Python, 14 rule(s) raw, 14 formula(s) through openpyxl` |
| R104 checker controls | `python3 -m unittest test_r104_grammar` | `Ran 33 tests … OK` |
| Studio gate controls | `python3 -m unittest test_r104_studio_gate` | `Ran 16 tests … OK` |
| Studio gate | `python3 tools/check_r104_studio.py` | AGP 9.1.1 inside Rabbit 1's 7.1–9.4, Gradle 9.3.1 ≥ the plugin's 9.3 floor, JDK 17, compileSdk 37 ≤ 37, 39 Android modules with namespaces, exported launcher |
| R92–R103 | their runners | all green, **every count identical to R103** (38, 72, 98, 72, 60, 70, 91, 68, 132, 125, 113, 113) |
| R47 kernels | `python3 tools/run_r47_contracts.py` | `R47 PASS: 2393 assertions across 61 kernels` |
| whole Python suite | `python3 -m unittest discover -s tools -p 'test_*.py'` | `Ran 2467 tests … OK (skipped=268)` |
| census | `python3 tools/module_testability.py --check` | `50/50 (100.00%)`, `not_established: 0` |
| structural | `python3 tools/check_project.py` | green, including the new Studio gate |

**Source mutations executed** (each recompiled, run, and reverted; the reverted file is byte-identical):

| Mutation | Detected by | Message |
|---|---|---|
| `HALF_UP` → `HALF_EVEN` | the probe's ROUND lane | `ROUND halves away from zero: 'ROUND(2.5,0)=3' … gave FALSE` |
| the fifteen-digit decimal step dropped | same | `ROUND of a decimal half: 'ROUND(2.675,2)=2.68' … gave FALSE` |
| numeric text coerced into a number | the type-rule lane | `text in arithmetic is refused: '"5"+0=5' … gave TRUE` |
| multiplicative level removed | the precedence lane | `parentheses: '(1+2)*3=9' … gave UNDECIDED` |

**Substantial enough code to test — absolute and percentage.** Against the census standard (50 modules):
substantial testable subset **50/50 = 100.00%**; specification-accepted modules **0/50 = 0.00%**. This feature:
functions with an executable witness **16/16 = 100%**; formula cases **100**; refusals **27/100 = 27%** of the table
by design; independent re-derivations **3** (Python lane, 49 controls across two files, 4 source mutants); written
rules round-tripped **14/14** through raw XML and openpyxl.

**Tools that exist here and were used:** pinned kotlinc 2.2.21 + OpenJDK 17, openpyxl 3.1.5, CPython 3.11
`unittest`/`zipfile`/`xml.etree`/`decimal`/`tomllib`.
**Not available, therefore not claimed:** Android SDK, Gradle distribution, system JDK, emulator, device, Excel,
LibreOffice, any browser, any spreadsheet renderer. **Visual inspections this round: 0.**

## I. The app, and "tried out on a laptop with Rabbit 1"

Rabbit 1 is Android Studio **2026.2.1**, and Google's compatibility table gives it AGP **7.1–9.4**
([about-agp](https://developer.android.com/build/releases/about-agp)). The same table gives each plugin's minimum
Gradle (9.1 → 9.3.1). AGP 9 needs JDK 17+.

What the repository already satisfies, now checked by `tools/check_r104_studio.py` on every `check_project.py` run:

| Fact | Value | Why it matters on a laptop |
|---|---|---|
| AGP | 9.1.1 | inside Rabbit 1's 7.1–9.4; a newer AGP would be refused by the IDE |
| Gradle wrapper | 9.3.1, pinned by SHA-256 | exactly equals AGP 9.1's floor; a stale wrapper stalls the first sync |
| Kotlin / jvmTarget | 2.2.21 / 17 | matches the JDK the IDE will be told to use |
| compileSdk | 37 | the newest API AGP 9.4 accepts |
| Android modules | 39 of 50, all with `namespace` and `compileSdk` | a missing namespace fails the build before any code compiles |
| Launcher | `dev.fusion.shell.MainActivity`, exported | the IDE's Run button needs it |
| Flavors | `play`, `pro` | the documented command is `:app:assembleProDebug` |
| `android.useAndroidX`, `android.builtInKotlin=false`, `android.newDsl=false` | declared | the AGP 9 Kotlin bridge is intentional, not accidental |

The gate refuses sixteen specific regressions (16 controls in `tools/test_r104_studio_gate.py`), including an AGP
outside Rabbit 1's range, a wrapper below the plugin floor, a module without a namespace, a launcher that is not
exported, and a missing checksum on the wrapper.

**What is still not verified, and cannot be here:** a Gradle sync, a compile, an install, a launch. The sandbox has
no Android SDK, the wrapper cannot download its distribution (`services.gradle.org` is unreachable), and there is no
system JDK. The laptop path is in `docs/BUILD.md`:

```sh
git clone <this repository> && cd OSB/android
# Android Studio Rabbit 1 → Open → android/ → accept the Gradle JDK 17 prompt
./gradlew :app:assembleProDebug     # first build downloads Gradle 9.3.1 and the AGP artifacts
./gradlew :app:installProDebug      # with a device or emulator attached
```

## J. Regressions and support edits versus R103 (`2353982`)

**Product behaviour that changed — the point of the round.** An `expression` rule now decides sixteen functions
instead of two, and refuses with a reason where it used to say "outside the decided grammar". The one deliberate
narrowing: `MOD(ROW(),2)=0` and `ISBLANK(...)` are now ordinary parses of the same grammar, so their *reason text*
changed even though their answers did not (R96–R103 reproduce their counts exactly).

`ROUND` adopts Excel's fifteen-digit step. That is a behaviour change relative to nothing (R104 introduced the
function) but it is a **divergence avoidable** only by copying `tools/r73_formula_cases.excel_round`'s rule, which is
what was done; the two now agree on `2.675`, `-2.675` and `1.005`.

Support edits, all minimal:

1. **`testing/build.gradle.kts` (+14).** `r104Grammar` source set and `:testing:r104GrammarContracts`.
2. **`.github/workflows/android-r54-office.yml` (+3).** the new task, the checker step, and two test modules in the
   unittest list.
3. **`tools/check_project.py` (+2).** runs the Studio gate, so the laptop contract is enforced without an SDK.
4. **`tools/r104_census_deepen.py` (new).** re-pins the one changed production file in the census and adds the
   round's checks; it refuses to run if any *other* pinned file has drifted.
5. **`docs/module-testability.json` / `docs/MODULE-TESTABILITY.md`.** regenerated through the script, not hand-edited.
6. **`docs/verification/r11-source-inventory.json`.** regenerated by `native_source_audit.py` (new source files).
7. **`docs/BUILD.md`.** an R104 section at the top with the laptop path.

No other product behaviour changed: R92–R103 reproduce their counts exactly and R47's 2393 assertions are unchanged.
The whole suite grew from 2288 to 2467 tests because two new test modules exist; nothing was removed and the skip
count moved by one.

## K. Class A, and the next test

**Class A applications: still 0.** The census percentage measures executable testability, not acceptance. What R104
changes is that a conditional format written the way real workbooks write them — `AND(...)`, `LEN(...)`,
`ROUND(...)` — now has an answer, and an answer the product can defend line by line.

Next tests, in order of information per minute:

1. **Open `r104.xlsx` in Excel and LibreOffice** and compare which cells are painted against `cases.tsv`. That
   settles the sixteen function semantics in one look, which no amount of local reasoning can.
2. **Build the app on a laptop** with Rabbit 1 and record the first real sync: the Studio gate asserts configuration
   compatibility, not a successful build.
3. **`LEN` outside the BMP.** One emoji in one cell tells us whether the code-point/UTF-16 difference matters.
4. **A workbook with `Sheet2!A1`** to learn what Excel paints when a conditional formula crosses sheets — currently
   a refusal, and the refusal may be the wrong answer.
