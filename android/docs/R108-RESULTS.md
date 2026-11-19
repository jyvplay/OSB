# R108 — Text functions: `MID`, `FIND`, `SEARCH`, `EXACT`, `SUBSTITUTE`, `REPT`, `VALUE`

Every conditional format in the first two thousand workbooks of any audit finds is a *label test*:

```
SEARCH("draft",B1)>0        MID(D1,4,2)="Q3"        EXACT(E1,"paid")        SUBSTITUTE(F1,"-","")="20240101"
```

R104 could already read `LEFT`, `RIGHT`, `LEN`, `LOWER`, `UPPER` and `TRIM`, and R105–R107 could ask a range how many
cells satisfied a criterion. But the six shapes above — find a substring, cut a slice at a position, compare case
sensitively, rewrite a string, repeat one, and turn digits into a number — were each refused *by name*:

```
unsupported: expression 'SEARCH("draft",B1)>0' is not a function this reader evaluates
```

R108 adds the seven text functions behind those formulas. One product file changed
(`XlsxConditionalFormatVerdict.kt`, **+162/−13**): the arity table, seven evaluator branches, a whole-number argument
guard, a substitution walk, and a split of the existing wildcard matcher into a token list plus a matcher that
`SEARCH` can also ask for a *prefix*. Everything else in this round is evidence about it.

## 0. Runtime honesty

**Actually available and used this round:** the pinned host toolchain installed by `python3
tools/bootstrap_host_tools.py install` — kotlinc **2.2.21** on **OpenJDK 17.0.9**, both fetched through npm and
trusted only as transport — plus CPython **3.11** with `unittest`, `zipfile`, `xml.etree`, `re`, `hashlib`, and
**openpyxl 3.1.5** installed into `$HOME/.cache/fusion-r76-readers` by pip (`python-docx 1.2.0` alongside it, unused
this round). **Not available, therefore never claimed:** a Gradle distribution, the Android SDK, an emulator, a
device, Excel, LibreOffice, any spreadsheet renderer, any browser, any independent human reviewer, any theorem
prover, any network service. **Visual inspections this round: 0.** Statements about Excel's behaviour are
documentation-derived and labelled as such wherever they matter (§F.1).

## A. Formal model

**Admissible objects.** The R107 grammar plus seven names, each with its own arity:

```
MID(text, start, count)                 3 arguments, one-based, whole indices
FIND(needle, text [, start])            2..3 arguments, case-sensitive, no wildcards
SEARCH(needle, text [, start])          2..3 arguments, case-insensitive, `*` `?` `~` wildcards
EXACT(left, right)                      2 arguments, the grammar's only case-sensitive equality
SUBSTITUTE(text, old, new [, instance]) 3..4 arguments, case-sensitive, all occurrences or one
REPT(text, count)                       2 arguments, bounded by Excel's cell-text ceiling
VALUE(text)                             1 argument, this reader's own number grammar
```

Text arguments are coerced by the model's existing `text()`: a number in Excel's general form, a boolean as
`TRUE`/`FALSE`, a blank as the empty string, an error refused with its code. A missing `start` is 1; a missing
`instance` means every occurrence.

**Rules, stated exactly as implemented.**

| Function | Rule | Result |
|---|---|---|
| `MID` | `start < 1`, non-whole, or a negative count | `#VALUE!` |
| `MID` | `start > length` (one past the end included) | empty text |
| `MID` | otherwise | `text[start-1 .. start-1+count)`, clipped at the end |
| `FIND` | `start ∉ 1..length+1` | `#VALUE!` |
| `FIND` | empty needle | `start` |
| `FIND` | needle absent at/after `start` | `#VALUE!` |
| `SEARCH` | `start ∉ 1..length+1` | `#VALUE!` |
| `SEARCH` | empty pattern | `start` |
| `SEARCH` | smallest position `p ≥ start` whose *prefix* matches the pattern | `p` |
| `SEARCH` | no such position | `#VALUE!` |
| `SEARCH` | `length × tokens > 1 000 000` | refused (a stated scan bound, not an error value) |
| `EXACT` | always | the two rendered texts are equal, case included |
| `SUBSTITUTE` | empty `old` | the text unchanged |
| `SUBSTITUTE` | `instance < 1` or non-whole | `#VALUE!` |
| `SUBSTITUTE` | `instance` past the last occurrence | the text unchanged |
| `SUBSTITUTE`/`REPT` | result longer than 32767 | `#VALUE!` (never a truncation) |
| `REPT` | `count < 0` or non-whole | `#VALUE!` |
| `VALUE` | `^[+-]?(\d+(\.\d*)?|\.\d+)([eE][+-]?\d+)?$` after trimming | that number |
| `VALUE` | anything else, including empty text, a boolean and `0x10` | `#VALUE!` |

**Information model.** Unchanged: the rule's `sqref`, the formula text, and a sparse grid of the five values this
model knows — blank, number, text, boolean, error. No used-range, no date type, no locale, no collator, no number
format, no formula text of other cells.

**Resource counted.** Parsing stays `O(tokens)`. Each text function is `O(length)` except `SEARCH`, which is
`O(positions × tokens)` with a *stated* ceiling of 1 000 000 token-position steps; above it the reader refuses with a
reason naming the length and the token count instead of running quadratic work on a formula from a file it did not
write. `REPT` and `SUBSTITUTE` are bounded by 32767 characters of output, and `REPT("",1000000000)` is answered
without allocating, because the empty text has no length to multiply.

**Success, with explicit quantifier order.** For every block, every token of its `sqref`, every cell of that token,
every `expression` rule in it: `Yes` if the formula evaluates to a true boolean or a non-zero number, `No` if
false/zero/blank, `Unsupported(reason)` otherwise. Inside `SEARCH`: `∃ p ∈ start..length : pattern covers a prefix of
text[p..]`, and the answer is the **least** such `p` — not the longest match, not the first pattern-consistent end.

**Parameter regime, units, tolerances.** Text is UTF-16 code units, as Kotlin's `String` counts them, so an index
counts the same units the file's `<t>` holds; case folding is `lowercaseChar` per character, which is ASCII-correct
and length-preserving (a fold that changed the length would move every position `SEARCH` reports, so it is done
character by character rather than by `String.lowercase()`). Grid `1..1048576 × 1..16384`. Number equality is exact
double equality; there are no rounding tolerances in the text path.

**Adjacent problems that must not be substituted.** (i) *Cell-formula evaluation* (`FormulaEval`) — a different
caller, untouched. (ii) *Painting* — a verdict is not a rendered cell. (iii) *Collation* — ordering two different
texts stays refused. (iv) *Regular expressions* — `SEARCH` reads Excel's `*`/`?`/`~`, not a regex dialect, because a
regex engine in a document reader is both a resource risk and a second semantics. (v) *Locale-aware `VALUE`* —
currency, percent and date parsing are refused rather than guessed. (vi) *Whole-column ranges, 3-D references,
implicit intersection* — still out, as in R105–R107.

## B. Outcome space

Three outcomes per rule, unchanged. Measured over this round's **92** cases: **63 decided, 29 refused**
(29/92 = 31.5%).

- **H+ (achievable):** every text formula whose arguments are literals, references or other bounded expressions is
  decidable from the grid alone; a text function's result does not depend on anything the grid does not carry.
  Executed: 63 decided cases.
- **H− (impossible under these constraints):** a text formula whose meaning depends on a locale (currency, percent,
  collation), on a regex dialect Excel does not use, or on a text longer than the model's ceiling cannot be decided
  here. Refused, with a reason naming the argument or the code.
- **H∂ (boundary):** five boundaries, each pinned from both sides: index `0` and `1`; `start = length+1` and
  `length+2`; count `0` and `-1`; result length `32767` and `32768`; `SEARCH` scan work below and above 1 000 000.

## C. Frontier — what this closed, and what was verified

Imported result, restated with its hypotheses: Excel's text functions are **one-based**, `MID` answers empty text
rather than an error past the end, `FIND` is case-sensitive and does not read wildcards while `SEARCH` folds case and
does, `EXACT` is the case-sensitive equality, `SUBSTITUTE` replaces every occurrence or the *n*th, and a cell's text
is limited to 32767 characters. The hypothesis is "text is a sequence of characters whose positions are counted from
one, and the two search functions differ exactly in case folding and wildcard reading".

That is what R108 implements and measures. `FIND`-versus-`SEARCH` is pinned by a case where the *same* needle gives
two different answers (`FIND("L","alpha")` is `#VALUE!` while `SEARCH("L","alpha")` is 2), and the wildcard reading is
pinned by `FIND("*","a*b")` = 2 against `SEARCH("a~*","a*b")` = 1. Mutation 3 (the matcher requires the pattern to
cover the whole remaining text) is caught by `SEARCH("p","alpha",3)` and `SEARCH("?p","alpha")`.

**Coverage now.** Functions the grammar reads: **36** (16 + 7 aggregates + 3 singular criteria + 3 plural criteria +
7 text). Formula cases across five rounds: **387** (100 + 71 + 64 + 60 + 92). Refusal shapes pinned: **109**
(27 + 21 + 13 + 19 + 29). Written conditional rules round-tripped: **62** (14 + 12 + 12 + 12 + 12).

## D. Negative space — twenty shapes that look like solutions but do not satisfy the contract

| # | Shape | Why it fails | The test that detects it |
|---|---|---|---|
| 1 | Treat the `start` argument as a zero-based offset | `MID("alpha",2,3)` returns `lph` only if `start` is one-based | `mid of the middle`; mutation 1 |
| 2 | Return `#VALUE!` for a `start` past the end | Excel answers empty text; an error turns a decidable case into a refusal | `mid starting one past the end is empty` |
| 3 | Accept a negative count by truncating to empty | hides a broken formula | `mid negative width is an error` |
| 4 | Truncate a fractional index instead of refusing it | `MID("alpha",1.5,2)` would answer `lp` | `mid fractional start is an error`; mutation 8 |
| 5 | Give `FIND` `SEARCH`'s case folding | `FIND("L","alpha")` would answer 2 | `find a case-sensitive miss is an error` |
| 6 | Let `FIND` read `*`/`?` | `FIND("*","a*b")` would answer 1 | `find does not read a wildcard` = 2 |
| 7 | Make `SEARCH` case-sensitive | the whole point of having both | `search is case-insensitive` = 2 |
| 8 | Have `SEARCH` require the pattern to cover the rest of the text | `SEARCH("p","alpha",3)` would be `#VALUE!` | `search from a start at the match`; mutation 3 |
| 9 | Ignore `~`, so an escaped star stays a wildcard | `SEARCH("a~*","a*b")` would still be 1 for the wrong reason and `SEARCH("~?","a*b")` would answer 2 | `search an escaped star`, `search an escaped question mark misses` |
| 10 | Answer `0` for "not found" | `0` is a number a rule can compare, so a miss would silently decide | `search a missing pattern is an error`; `find a missing needle is an error` |
| 11 | Allow `start = length+2` (an off-by-one in the bound) | Excel refuses past `length+1` | `find a start past the end is an error` against `find an empty needle at a start answers that start` |
| 12 | Answer `0` or the length for an empty needle | Excel answers the start position | `find an empty needle answers the start`; `search an empty pattern answers the start` |
| 13 | Fold case in `EXACT` | `EXACT("alpha","Alpha")` would be `TRUE` | `exact is case-sensitive`; mutation 4 |
| 14 | Compare values rather than rendered text in `EXACT` | `EXACT(1,"1")` would be `FALSE` | `exact reads a number as its general text` |
| 15 | Make `SUBSTITUTE` case-insensitive | `SUBSTITUTE("alpha","A","X")` would change the text | `substitute is case-sensitive` |
| 16 | Insert the replacement for an empty `old` text | Excel returns the text unchanged | `substitute an empty old text changes nothing` |
| 17 | Replace every occurrence when an instance was asked for | `SUBSTITUTE("a-b-c","-","+",2)` would be `a+b+c` | `substitute one instance`; mutation 5 |
| 18 | Error when the instance is past the last occurrence | Excel leaves the text unchanged | `substitute an instance past the end changes nothing` |
| 19 | Truncate at 32767 instead of answering `#VALUE!` | a silent truncation compares unequal for no visible reason | `repeat one past the text bound is an error`; mutation 6 |
| 20 | Take whatever `toDouble` accepts, or read empty text as 0 | `VALUE("0x10")`, `VALUE("1,5")`, `VALUE("")` would decide | the five `value refuses …` cases; mutation 7 |
| 21 | Ignore the block anchor when shifting a reference | `EXACT(A1,"alpha")` at `K2` would read `A1` not `A2` | `a shifted text reference one row down` = `FALSE` |

(Twenty-one rows is one more than the heading promises; the extra row is the shift rule, which this round had to pin
again because a text function is a new place for a relative reference to appear.)

## E. Mechanism portfolio — six mechanism-distinct approaches

| # | Mechanism | Central construction | Concrete artifact | Proved portion | Exact unresolved interface | Cheapest falsification | Gap local? |
|---|---|---|---|---|---|---|---|
| 1 | Hand-written expectation table, written before the evaluator ran | `formula -> answer at a cell` | `R108Checks.kt` `CASES` (92 rows) | outcomes per formula/cell | none for the table itself | change one expected value | local |
| 2 | Independent Python tokenizer/parser/evaluator | own number grammar, own DP matcher in whole-text **and** prefix mode, own substitution walk | `tools/check_r108_text_functions.py` | 92/92 cases agree with Kotlin **and** the table, refusal reasons included | shares no code with the product | edit one branch of the Python `text_function` | local |
| 3 | Controls on that reference implementation | direct calls to `wild_tokens`, `wild_match`, `search_position`, `substitute`, `whole_argument`, `decide` | `tools/test_r108_text_functions.py` (31 tests) | the lane is not self-agreeing | none | break the prefix mode in Python | local |
| 4 | Source mutation | 8 mutants, §H | each stated rule is detectable by the probe | mutants are author-chosen | add a 9th mutant | local |
| 5 | File round-trip | raw `<formula>` XML + openpyxl 3.1.5 | `r108.xlsx`, 12 rules incl. `$A$1` | text formula text survives verbatim, quotes and `~` included, and the locked markers stay | openpyxl does not evaluate formulas | corrupt a rule's text | local |
| 6 | Cross-round differential | R104/R106/R107's criterion lanes re-run against the same product | `R104_GRAMMAR_OK …`, `R106_CRITERIA_OK …`, `R107_PLURAL_OK …` | extracting the wildcard token list changed no criterion answer | those lanes share `wildcard()` with the new code | edit `wildTokens` and watch three rounds fail | equivalent to the original problem (shared code) |

Mechanisms 1 and 2 are independent because both were written from the specification rather than from each other (the
Python lane's matcher is a table over (token, position); the product's is a backtracking scan). Mechanism 6 is *not*
independent by construction and is listed with that weakness stated rather than hidden: a wrong `wildTokens` would
move three rounds together, which is exactly what mutation 3 demonstrated from the other direction.

## F. Artifact requirement, and the six honest holes

Artifacts (regenerated by `python3 tools/run_r108_checks.py`, not checked in):
`testing/build/r108/artifacts/{r108.xlsx, cases.tsv, rules.tsv, values.tsv, summary.txt, mutants.tsv}`.

1. **No spreadsheet application ran.** Every rule in §A is Excel's *documented* behaviour read against the published
   function descriptions; none was observed. `VALUE("")` → `#VALUE!` in particular is documentation-derived and is
   the one rule in the table I would check first if a copy of Excel were in front of me.
2. **No collator.** Ordering two different texts is still refused, so `SEARCH` can fold case but cannot be
   locale-aware, and `"i"` folds to `"i"` regardless of a Turkish locale.
3. **Character units are UTF-16 code units**, not Unicode code points: an astral character counts as two, which is
   what Excel counts and what Kotlin's indices count, but it means `MID` on an emoji can split a surrogate pair.
4. **`SEARCH`'s scan bound is the reader's, not Excel's.** Above 1 000 000 token-position steps the formula is
   refused with the length and token count in the reason. Excel has no such bound because it is not reading a file it
   did not write.
5. **`VALUE` reads only plain decimal and scientific text.** Currency, percent and dates are refused, so
   `VALUE("$5")` is `#VALUE!` here and 5 in Excel. Stated, not guessed.
6. **`REPT`'s 32767 ceiling is a cell-text ceiling, not a formula ceiling.** Excel truncates in some contexts; this
   reader answers `#VALUE!`, and the difference is visible only in the reason a user is shown, since both paint
   nothing.

## G. Second-order adversary — eight passes

1. **The two searches must not collapse into one.** A case where the same needle decides differently under `FIND` and
   `SEARCH` (`"L"` in `"alpha"`), a case where a wildcard is literal for `FIND` and a wildcard for `SEARCH`, and two
   `~`-escape cases. A single shared implementation that forgets to pass the flag cannot pass all four.
2. **Prefix versus whole-text matching.** `SEARCH("a*","alpha")` = 1, `SEARCH("*a","alpha")` = 1,
   `SEARCH("?p","alpha")` = 2 and `SEARCH("l*h","alpha")` = 2 are four different demands on the same matcher; the
   prefix mode is additionally pinned by `SEARCH("p","alpha",3)` = 3, which is `#VALUE!` under whole-text semantics.
3. **Off-by-one on every index.** `MID` at `1`, at `length`, at `length+1` and at `length+2`; `FIND`/`SEARCH` at `0`,
   `1`, `length+1` and `length+2`. The bound is `1..length+1` for the searches and `1..` for `MID`, deliberately
   different, and both sides are pinned.
4. **Growth and its ceiling.** `REPT` at 32767 (allowed, measured with `LEN`), at 32768 (refused), `REPT("ab",16384)`
   = 32768 (refused), `REPT("",1000)` (allowed, no allocation), `REPT("",10⁹)`-scale counts avoided in the table but
   guarded in code, and `SUBSTITUTE`'s growth past the ceiling (refused).
5. **The number grammar is the tokenizer's, not Python's.** `VALUE("1e2")` = 100, `VALUE(" 42 ")` = 42,
   `VALUE("0x10")`, `VALUE("1,5")`, `VALUE("abc")`, `VALUE("")`, `VALUE(TRUE)` all refused; the controls test the
   regex against twelve spellings on both sides.
6. **Composition.** A text call inside `AND`, a text call compared with a text call (`LEFT("alpha",2) =
   MID("alpha",1,2)`), `LEN(SUBSTITUTE(...))`, `FIND` inside `UPPER`, and a bare text result as a rule's value
   (refused as "is text, not a condition").
7. **Errors crossing the value model.** One case keeps `MID("alpha",0,2)="l"` deliberately: an error operand makes
   the *comparison* refuse with "cannot compare error #VALUE! with text", a different reason from the bare call's
   "evaluates to #VALUE!". Both are refusals; the reasons differ, and both are asserted.
8. **The lane's own integrity.** Four of my hand-written expectations were wrong on first run and the product was
   right each time (§I). The controls file also failed twice on first run — `wildcard('ALP','alpha')` is `FALSE`
   under whole-text semantics (a pattern of three characters cannot cover five) and `search_position('l','alpha',3)`
   is `None` (the only `l` is at 2). Both repaired; then the full lane, the mutants and the whole regression sweep
   were re-run rather than only the failing cases.

## H. Verification — what actually ran

| Lane | Command | Result |
|---|---|---|
| R108 probe + checker | `python3 tools/run_r108_checks.py` | `R108_TEXT_OK assertions=176 cases=92 decided=63 refused=29 functions=7 bound=32767 rules=12`; then `92 case(s) re-evaluated in Python, 12 rule(s) raw, 12 formula(s) through openpyxl` |
| R108 controls | `python3 -m unittest test_r108_text_functions` | `Ran 31 tests … OK` |
| R107 (re-run after this round) | `python3 tools/run_r107_checks.py` | `R107_PLURAL_OK assertions=134 cases=60 decided=41 refused=19 criteria=3 rules=12`; `Ran 24 tests … OK` |
| R92–R106 | their runners | all green, **every count identical to R107** (38, 72, 98, 72, 60, 70, 91, 68, 132, 125, 113, 113, 220, 147, 132) |
| R47 kernels | `python3 tools/check_r47_reference.py` | `R47 PASS: 2393 production-kernel assertions across 61 new kernels`; oracle `40/40` |
| whole Python suite | `PYTHONPATH=… python3 -m unittest discover -s tools` | `Ran 2582 tests … OK (skipped=268)` (2550 at R107, +31 controls, +1 discovery difference traced to no code change) |
| census | `python3 tools/module_testability.py --check` | `50/50 (100.00%)`, `not_established: 0` |
| structural + Studio gate | `python3 tools/check_project.py`, `tools/native_source_audit.py`, `tools/check_r104_studio.py` | green (AGP 9.1.1, Gradle 9.3.1, JDK 17, compileSdk 37); audit `231 recorded requirements, 36 cross-cutting obligations` |

**Source mutations executed** (each recompiled with `-Werror`, run, and reverted to a byte-identical file; the
sha256 of the product file was asserted equal after every revert, `37df0c4b89ce4a59…`):

| Mutation | Detected by | Message |
|---|---|---|
| MID keeps the zero-based start | the probe’s own table | `mid of the middle: 'MID("alpha",2,3)="lph"' at B1 gave FALSE, expected TRUE (-)` |
| FIND searches from the character before the start | the probe’s own table | `find an empty needle answers the start: 'FIND("","alpha")=1' at B1 gave FALSE, expected TRUE (-)` |
| SEARCH returns only where the pattern covers the rest of the text | the probe’s own table | `search is case-insensitive: 'SEARCH("L","alpha")=2' at B1 gave UNDECIDED, expected TRUE (expression 'SEARCH("L","alpha")=2': cannot compare error #…` |
| EXACT folds case | the probe’s own table | `exact is case-sensitive: 'EXACT("alpha","Alpha")' at B1 gave TRUE, expected FALSE (-)` |
| SUBSTITUTE replaces every occurrence whatever instance was asked for | the probe’s own table | `substitute one instance: 'SUBSTITUTE("a-b-c","-","+",2)="a-b+c"' at B1 gave FALSE, expected TRUE (-)` |
| REPT allows twice the cell-text ceiling | the probe’s own table | `repeat one past the text bound is an error: 'REPT("a",32768)' refused with 'expression 'REPT("a",32768)' is text, not a condition', expected it to…` |
| VALUE reads an empty text as zero | the probe’s own table | `value refuses an empty text: 'VALUE("")' at B1 gave FALSE, expected UNDECIDED (-)` |
| a fractional index is rounded instead of refused | the probe’s own table | `mid fractional start is an error: 'MID("alpha",1.5,2)' refused with 'expression 'MID("alpha",1.5,2)' is text, not a condition', expected it to ment…` |

**Substantial enough code to test — absolute and percentage.** Against the census standard (50 modules): substantial
testable subset **50/50 = 100.00%**; specification-accepted (Class A) modules **0/50 = 0.00%**. This feature: the
seven text functions have an executable witness **7/7 = 100%**; the **92** cases split **88 direct calls** — `MID` 17,
`SEARCH` 15, `FIND` 13, `EXACT` 13, `VALUE` 11, `SUBSTITUTE` 11, `REPT` 8 — and **4 compositions** (two `LEN`, one
`LEFT`, one `AND`), covering index base, past-the-end, zero and negative counts, fractional indices, case folding on
both sides, `*` `?` `~` in three positions, empty needle at two starts, missing needle, binds at `length+1` and
`length+2`, instance 0/1/2/9, empty old text, the 32767/32768 text ceiling, seven spellings of a number, five shift
cases, and every arity bound from both sides — **92/92 = 100%** of the planned shapes. Of those, **29/92 = 31.5%** are
refusals by design; independent re-derivations **3** (Python lane, 31 controls, 8 mutants); written rules
round-tripped **12/12** through raw XML and openpyxl.

**Tools that exist here and were used:** pinned kotlinc 2.2.21 + OpenJDK 17.0.9, openpyxl 3.1.5, CPython 3.11
`unittest`/`zipfile`/`xml.etree`/`re`/`hashlib`. **Not available, therefore not claimed:** Excel, LibreOffice, any
spreadsheet renderer, Android SDK, Gradle distribution, emulator, device, browser. **Visual inspections this round:
0.**

## I. Repair, and the attack on the repair

Four expectations were mine and wrong; in every case the product was right and the *table* was repaired:

- `FIND("l","alpha")=3`. `alpha` is `a l p h a`, so the `l` is at **2**. Product right, table repaired (twice: the
  case row and the same string inside the written rules).
- `SEARCH("p","alpha",4)=4`. A `start` of 4 begins the scan at the second `h`/`a`, past the `p` at 3, so the answer
  is `#VALUE!` — and the case became `SEARCH("p","alpha",3)=3`, which is the sharper test anyway because it pins the
  *inclusive* start.
- `MID("alpha",0,2)="l"` expected the reason "evaluates to #VALUE!"; the actual reason is "cannot compare error
  #VALUE! with text". Rather than relax the assertion I kept that case (the comparison path deserves its own reason)
  and re-expressed the other twelve error cases as **bare calls**, whose reason is the one the model writes for an
  error value.
- The controls' `wildcard('ALP','alpha')` and `search_position('l','alpha',3)` were wrong for the same reason: a
  whole-text pattern of three characters cannot cover five, and there is no second `l`.

Two implementation repairs of my own, both structural rather than behavioural:

- `String.lowercase()` can change a string's length for a few code points, which would shift every position `SEARCH`
  reports. The fold is now per character (`CharArray(body.length) { body[it].lowercaseChar() }`).
- The Kotlin string `"EXACT(\$A\$1,\"alpha\")"` was first written with a doubled backslash and did not compile; the
  probe's own assertion caught it before anything ran.

**New attack aimed at the patch.** The wildcard matcher was split into `wildTokens` + `wildMatches` so that `SEARCH`
could ask for a *prefix* match without copying the text per position. That refactor is invisible to a case table that
only exercises criteria, so the whole R104/R106/R107 criterion lanes were re-run: all three reproduce their counts
(27, 13, 19 refusals) exactly. A repaired candidate inherits no trust, so after each repair the full lane — probe,
Python lane, controls, mutants, R92–R107, R47, discover, census, gates — was re-run rather than only the failing
case.

## J. Regressions versus R107 (`dbf8585`)

**Product behaviour that changed — the point of the round.** Seven names now decide instead of being refused as
unknown functions. Nothing else changed: `wildcard()` keeps its exact semantics (`wildTokens` builds the same token
list, `wildMatches(..., prefix = false)` runs the same backtracking scan), and the criterion, aggregate and plural
paths are untouched.

1. A formula naming `MID`, `FIND`, `SEARCH`, `EXACT`, `SUBSTITUTE`, `REPT` or `VALUE` now decides; the previous
   outcome was `Undecided` with "is not a function this reader evaluates".
2. `MAX_CALL_ARGS`, `MAX_DEPTH`, `MAX_TOKENS`, `MAX_RANGE_CELLS` and the criterion bound are untouched, so no existing
   function's bounds moved. Two new bounds were added (`MAX_TEXT_LENGTH = 32767`, `MAX_SEARCH_WORK = 1_000_000`) and
   they apply only to the new functions.
3. A wildcard criterion over a 400-character text still behaves exactly as before (the R106/R107 lanes exercise 255
   and 256), because the split kept the same token language and the same case folding.

No existing decision changed value. R92–R107 reproduce their counts exactly; R47's 2393 assertions are unchanged; the
Python suite grew from 2550 to 2582 tests with no test removed.

**Support edits, none of which re-pins another round's evidence:**

1. **`testing/build.gradle.kts` (+14)** — the `r108Text` source set and the `r108TextFunctionContracts` task.
2. **`.github/workflows/android-r54-office.yml` (+3/−2)** — the task, the Python lane and the control module.
3. **`tools/r108_census_deepen.py` (new)** — re-pins only `XlsxConditionalFormatVerdict.kt` and appends R108's checks
   and commands to the `:format:ooxml` row; it refuses to run if any other pinned file has changed.
4. **`docs/module-testability.json` / `MODULE-TESTABILITY.md`** and **`docs/verification/r11-source-inventory.json`**
   — regenerated by their generators; the inventory diff is one hash line.

**Restoration note.** This turn began from a clean tree at `dbf8585`, which *is* `origin/main` after PR #30 merged,
with `git status --porcelain` empty, `HEAD` identical to `origin/main` and all R107 files tracked. There was nothing
to restore: the "restore our uncommitted changes" instruction was satisfied by proving the tree already contains
every R107 line, not by claiming a restore that did not happen. The toolchain (npm-pinned kotlinc, pip
`openpyxl`/`python-docx`, `npm ci tools/r87node`) was reinstalled from the checked-in lock files and the baseline
re-verified cold (`R107_PLURAL_OK … assertions=134`, 24 controls) before any R108 line was written.

## K. Class A, and the next test

**Class A applications: still 0.** Testability is 50/50; acceptance is 0/50. This round moves what the product can
*decide*, not its acceptance, and it does not move the count of visually verified screens, which remains **0**.

Next tests, in order of information per minute:

1. **Open `r108.xlsx` in Excel and LibreOffice** and compare the painted cells with `cases.tsv`. One look settles the
   index base, the `FIND`/`SEARCH` split and the 32767 ceiling.
2. **A workbook whose rule is `SEARCH("draft",B1)>0` with `B1` holding `"Draft-2024"`** — the commonest real formula
   this round added — to confirm that case folding matches what a user sees.
3. **`VALUE` with a currency-formatted cell**, the one rule in §A most likely to be wrong in a locale-aware Excel:
   `VALUE("$5")` is `#VALUE!` here and 5 in Excel.
4. **Whole-column ranges** (`SEARCH("x",B:B)>0`): still refused, and still the next shape that needs a used-range this
   model does not carry.
5. **Build the app on a laptop with Rabbit 1** and record the first real sync; the Studio gate asserts configuration
   only.
