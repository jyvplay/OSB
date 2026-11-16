# R92 — Worksheet protection (`<sheetProtection>`, the legacy 16-bit hash) and per-cell `<protection>`

Branch `arena/a870d292-osb` on top of R91 (`6b7202d`). Production footprint: **one new file**
(`XlsxProtection.kt`, 180 lines), **six edits / 27 lines** in `XlsxStyledBook.kt`, and **one constant plus its
justification** in `BoundedXml.kt` (the attribute ceiling, explained in G1). No existing behaviour was
reordered, renamed or removed.

## 0. The restoration request

`git status --porcelain` → empty, `git log -1` → `6b7202d` (the R91 merge), `git stash list` → empty. There was
nothing uncommitted to restore this time. The toolchain still had to be rebuilt (the host caches do not survive
a rebuild): `tools/bootstrap_host_tools.py install`, `npm ci --prefix tools/r87node` (221 packages),
`openpyxl==3.1.5`, `python-docx==1.2.0`, `ssf==0.2.2`, `babel==2.9.1`, `formualizer`.

## A. Formal model

**A.1 Admissible objects.** `SheetProtection(passwordHash, blocked)` where `blocked ⊆ Action` over the fifteen
CT_SheetProtection booleans, and `passwordHash` is 1–8 upper-case hex digits or `null`. Per cell,
`CellStyle.locked: Boolean?` and `CellStyle.hiddenFormula: Boolean?`, where `null` means "the schema default"
(locked, not hidden) and is therefore **not written**.

**A.2 Information / access model.** The document model never holds the plaintext: `ofPassword(p)` stores
`hash(p)` and discards `p`. A verifier sees only package bytes; openpyxl sees the same bytes through its own
object model; the hash oracle sees only `(password, hash)` pairs.

**A.3 Counted resource.** The attributes of `<sheetProtection>` (17 with a password, 16 without), the
`<protection>` child of each `xf` that needs one, and one hash value per password.

**A.4 Success / failure with quantifier order.** For *every* declared sheet, no attribute of the written record
differs from the declaration, all fifteen flags are present, and the record sits between `</sheetData>` and
`</worksheet>`; for *every* declared password, the independent implementations agree on the 16-bit value. One
counterexample fails the lane, with file, sheet and line.

**A.5 Regime, units, tolerances.** Exact. The hash is a 16-bit value spelled as four upper-case hex digits; the
password regime is **≤ 15 characters** and **BMP only**, both refused outside rather than approximated.

**A.6 Boundary conditions.** Protection is inactive unless `sheet="1"`. The schema's defaults are **not
uniform**: eleven flags default to blocked, four (objects, scenarios, selecting locked cells, selecting unlocked
cells) default to allowed — so the writer emits all fifteen explicitly and the reader fills in the schema
default for any that a foreign file omits. A cell that declares nothing writes nothing and reads back as `null`.

**A.7 Adjacent non-substitutes.** Conditional formatting (R77), data validation (R91), workbook-level
protection (`<workbookProtection>`, not modelled), the strong-hash attributes (`algorithmName`, `hashValue`,
`saltValue`, `spinCount`, not modelled — they are for password *editing* protection), file encryption
(ECMA-376 Part 4, not modelled), and actual enforcement, which is the application's.

## B. Outcome space

**H+.** Before R92 the writer could not protect a sheet at all: no `<sheetProtection>`, no cell `<protection>`,
no hash. Now 2 protected sheets (one with a password and a deliberately mixed policy, one with an empty blocked
set), 5 cell-protection records and 311 password hashes are produced; openpyxl reads back the same fifteen
flags, the same password string and the same per-cell `locked`/`hidden` values, and its independently written
hash function agrees on all 311 passwords.

**H−.** Nothing here protects anything. No Excel, LibreOffice, Android or browser opened these files, so
"the cell was refused" is unobserved and unclaimed. The hash is a 16-bit non-cryptographic checksum, trivially
searchable; treating it as a security boundary would be wrong, and the code says so where the function is
defined.

**H∂.** The hash has two hard boundaries that change the answer: **15 characters** (Excel's legacy window) and
**UTF-16 code units** (an astral character is two units and has no single-character representation), and both
are refused with a message rather than silently truncated or mis-hashed.

## C. Frontier, with the hypotheses it imports

Retrieved and restated: ECMA-376-1 §18.3.1.85 defines `CT_SheetProtection` and its per-attribute defaults
(`objects`, `scenarios`, `selectLockedCells`, `selectUnlockedCells` default false; the other eleven true);
§18.8.33/§18.4.12 place `<protection locked hidden>` inside `xf` with `applyProtection` controlling whether it
applies; the legacy password hash (rotate by position, xor, mix with length and `0xCE4B`) is the algorithm both
Excel and openpyxl implement. Imported hypotheses, none discharged here: (i) Excel's UI writes the same
fifteen flags with the same polarity; (ii) a `password` attribute of four upper-case hex digits is what Excel
compares when a user types the password; (iii) a cell is editable exactly when its `xf` says `locked="0"`.
The open interface is the application: enforcement cannot be reached from these bytes.

## D. Negative space — 16 shapes that look like a solution and are not

1. Writing `<sheetProtection/>` without `sheet="1"` (the record exists and does nothing).
2. Emitting only the flags that differ from *one* assumed default (four of the fifteen defaults are the
   opposite of the other eleven).
3. Storing plaintext passwords in the document model.
4. Hashing more than 15 characters instead of refusing (Excel's window is 15).
5. Hashing an astral character as one code unit (every other implementation hashes two or refuses).
6. Spelling the hash in lower case (Excel compares hex text; a reader may too).
7. Emitting `<protection>` in the `xf` without `applyProtection="1"` (some readers ignore it).
8. Putting `<protection>` before `<alignment>` (CT_Xf's sequence is alignment then protection).
9. Writing `<sheetProtection>` before `<sheetData>` (CT_Worksheet order violation).
10. Treating "no `<protection>` child" as `locked="0"` (the schema default is locked).
11. Writing a `<protection>` for every cell (bloat, and it changes nothing).
12. Claiming the sheet is protected because the record parses.
13. Claiming a wrong password would be rejected (nothing verifies a password here).
14. Treating the hash as a cryptographic digest ("16-bit" + "searchable" is the honest description).
15. Reading `sheet="0"` as protection (it means protection is switched off).
16. Raising the parser's attribute ceiling without keeping it a ceiling.

## E. Mechanism portfolio

| # | Mechanism | Artifact | Proved portion | Unresolved interface | Cheapest falsifier | Gap |
|---|---|---|---|---|---|---|
| 1 | Raw OPC attribute inspection | `check_r92_sheet_protection.py` | all 15 flags present/ordered/valued, `sheet="1"`, placement, password presence | bytes only | drop `sort="1"` | local |
| 2 | openpyxl `SheetProtection`/`Protection` | same file, second half | flags, password string, per-cell locked/hidden | openpyxl ≠ Excel | flip a flag in the table | local |
| 3 | openpyxl's independent hash | `check_r92_password_hash.py` | 311/311 agreement | Excel's own value | change one hash | local |
| 4 | A third implementation (control suite) | `test_r92_password_hash.py` | same 311, written from the description | same | mutate one corpus row | local |
| 5 | Round-trip through this code's reader | `R92Checks.kt` §4 | hash + 15 flags + per-cell values | self-consistency | drop an attribute | local |
| 6 | Parser-admissibility guard | `test_r92_password_hash.py` §3 | the 17-attribute record the feature writes is admitted; the ceiling is still a ceiling (≥21, ≤64) | none | revert the constant | local |

Branch 4 exists because branch 3 is only *one* other implementation: two implementations can share a
misreading of the prose. A third, written from the algorithm's description in this repository, makes a shared
misreading less likely — it does not remove the possibility, and nothing here substitutes for Excel.

## F. Artifact requirement

`testing/build/r92/artifacts/` (`prot.xlsx`, `prot-nopw.xlsx`, `prot-plain.xlsx`, `protections.tsv`,
`cell_protection.tsv`, `password_hashes.tsv`). The intent tables are written by the probe before any verifier
runs; the verifier refuses a folder whose workbook set disagrees with the table.

## G. Second-order adversary — what attacked this work and what it caught

**G1. A real defect in the shared parser.** `BoundedXml` refused any element with more than **16 attributes**.
A protection record with a password carries **17**, and one with the strong-hash attributes carries up to 21 —
so the reader would have refused a genuine Excel file as if it were an attack. Found by the probe crashing on
its own round-trip, not by inspection. Fixed by raising the ceiling to **24** with the schema maximum recorded
in a comment; `test_r92_password_hash` pins both facts (≥21, and still a bound ≤64), and both R72
pinned-vs-candidate comparators were re-run to show no behavioural drift on their corpora.

**G2. A false derivation, refuted by the corpus.** I derived that the legacy hash is always ≥ `0x8000` ("no term
can carry into bit 15") and wrote it as an assertion. The corpus produced `31BB` on the first run: a BMP code
unit shifted by 15 is below 2^30, not below 2^31, so bit 15 *can* be set by the `>>15` term. The claim was
withdrawn, replaced by the two facts that are true (the 16-bit mask, and that the corpus does not collapse), and
recorded here rather than deleted.

**G3. A surrogate-pair hole.** `require(code <= 0xFFFF)` accepts half of an astral character, because a lone
surrogate unit is ≤ 0xFFFF. The probe's own refusal test caught it; the check is now `none { it.isSurrogate() }`.

**G4. An ambiguous intent encoding.** "Unprotected" was inferred as `password == '-' && all flags 0`, which
silently conflated it with *protected but permitting everything* — exactly the `prot-nopw.xlsx` case. The table
now carries an explicit `protected` column, and the checker reads it instead of guessing.

**G5. A verifier that did not look at the whole folder.** Dropping the last intent row passed: the checker only
walked the rows it found. It now requires the declared workbook set and the folder's set to be equal, and the
negative control that caught this (`test_dropped_intent_row_is_rejected`) proves the fix.

**G6.** A shadowed variable (`protected` flag column vs `protected_sheets` counter) produced a `TypeError`
instead of a verification failure; renamed, with the failure path re-run.

## H. Verification — what ran

**H1. Probe and two lanes** (`python3 tools/run_r92_checks.py`):
`R92_OFFICE_OK assertions=38 workbooks=3 passwords=311 actions=15`; then *2 protected sheet(s) with all fifteen
action flags explicit, 5 cell protection record(s) verified; openpyxl agreed on 3 sheet(s) and 5 cell(s)*; then
*311 passwords (283 distinct hashes) recomputed with openpyxl's independent implementation and identical; 0
reference value(s) would have needed zero padding*.

**H2. Negative controls — 30 tests, all green**: `test_r92_sheet_protection` (17) and `test_r92_password_hash`
(13). They cover dropped/flipped/inactive flags, a misplaced element, a changed and an undeclared password, an
invented record on the unprotected control, dropped `<protection>`, missing `applyProtection`, a flipped cell
lock, invented cell protection, dropped and invented intent rows, a wrong field count, malformed XML, and a
tampered openpyxl comparison; plus corpus tampering (changed hash, lower-case hash, small corpus, missing
corpus) and the parser-admissibility bounds.

**H3. Regression sweep — 36 previously-recorded runners re-executed serially, every one exit 0.** 21 lanes
(R92, R91, R90, R89, R88, R87 shade, R86 Writer tabs, R86 sheet views, R84 Writer indent, R84 sheet visibility,
R83 Writer spacing, R83 hidden rows, R82 Writer align, R82 sheet align, R81 Writer tables, R81 sheet rows,
R76 Writer layout, R76 sheet formats, R75 fractions, and both R72 pinned-vs-candidate comparators — core
readers over 13 archives and ooxml import budgets over 30 archives, both compiling baseline `aacae239` against
the current tree) and 15 R5x/R6x corpus generators (R56 24 451 assertions, R57 548, R58 6 428, R59 1 580 + 200
DOCX, R60 675 + 256 DOCX and 6 588 + 64 XLSX, R61 1 831, R62 1 288 + 1 434, R63 1 522 + 2 246, R64 1 093 +
814 + 613).

**H4. Full host discovery.** `python3 -m unittest discover -s tools -p 'test_*.py'` →
**`Ran 2297 tests … OK`**, zero skips (R91: 2267; +30 = exactly the new R92 controls).

**H5. Census and audit.** `module_testability.py --check` → **50/50 modules (100.00 %) meet the bounded
testability standard; 0/50 accepted (0 %)**; `native_source_audit.py --check` → **231 requirements / 36
cross-cutting obligations**, reproducing.

**H6. Not run / not claimable.** No Android build, emulator, device, provider or picker gate. No Excel,
LibreOffice, browser or Word — and therefore **no visual inspection**: `soffice`, `libreoffice` and a headless
browser are absent from this host, so there is no screenshot of a protected sheet, a greyed-out Format menu or a
password prompt, and none is claimed.

## I. Regressions versus the best prior codebase (R91, `6b7202d`)

None found in behaviour; one **deliberate widening** of a shared bound, disclosed rather than buried: the parser
now admits up to 24 attributes per element instead of 16 (G1). That widens acceptance — a package this code
previously refused will now parse — so it is a change of *admissibility*, not of any parsed value, and the R72
comparators confirm no fixture in their corpora sits in the widened band.

The `xf` writer was rewritten from a two-branch form to a four-branch form (plain, alignment, protection, both).
For a style with neither alignment nor protection the output is byte-identical (`/>`), which is why the R76/R81/
R82/R83/R84/R86/R88 lanes and both R72 comparators still pass; the probe additionally asserts that an
unprotected workbook's sheet part still ends with `</sheetData></worksheet>` and that its styles part contains
no `<protection>`.

Residual risk: `CellStyle` and `StyledSheet` gained trailing defaulted fields. Every construction site in the
tree uses named arguments or defaults (the probe and all lanes compile with `-Werror`), and there is no
destructuring of either class.

## J. Class A, the PR, and the next test

**Class A: still 0 applications.** Four independent implementations of the hash agree and openpyxl reproduces
the record, and none of them is Excel. Whether a user is actually blocked from editing `A1` is unobserved.

**The PR.** This turn's instruction was to open a PR, so the branch is pushed and a PR is opened against
`main`; nothing is merged.

**Next test that would move the frontier.** Open `prot.xlsx` in Excel and record four observations: is the sheet
marked protected; is `A2` editable while `A1` is not; does the password `secret` unprotect it, and does a wrong
password fail; is `A3`'s formula hidden from the formula bar. That is the only measurement that turns a record
agreement into a behaviour claim.
**Carried, unimplemented:** device-accelerated video upscale and "remove mosaic" — unchanged and still refused
for the same information-theoretic reason.
