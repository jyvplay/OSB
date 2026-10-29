# R54 — Office correctness repairs (2026-10-29)

## Scope and honest outcome

The prior conversation itself is not available; checked-in R53 documents and the
231-row requirements ledger are the baseline. PR #9 is merged at the branch base;
no unfinished prior working-tree changes existed. This round repairs three real
production kernels rather than adding nominal modules. It does **not** complete
the original specification, increase the already-100% subset-testability count,
or establish Class A Writer/Calc. No new Android UI integration is claimed.

| Standard | Count | Percentage | Evidence scope |
|---|---:|---:|---|
| Existing substantial testable module subsets | 50/50 | 100% | Inherited inventory; R53 contracts rerun |
| Full-spec accepted modules | 0/50 | 0% | Unchanged; native/end-to-end acceptance missing |
| Production modules repaired this round | 2/50 | 4% | feature:editor, format:ooxml |
| Newly testable modules | 0/50 | 0% | Both already had testable subsets |
| Writer and spreadsheet fully Class A | 0/2 | 0% | Kernels are not full applications |

## A. Contract

Admissible merge objects are bounded UTF-16 templates with fields, properly nested
`#if/#else` and `#each` blocks, and string-keyed record maps. Each requires a list
of string-keyed maps; fields use exact keys (dots are literal, not object traversal).
Data is emitted literally, never reinterpreted as template syntax. Missing fields
and missing lists are diagnostic; untaken branches do not generate diagnostics.
Unknown conditions retain the prior false behavior. Malformed syntax throws.
Limits: 2 Mi UTF-16 units/template or rendered document, nesting 32, 100,000 node
visits/document, 10,000 list items or batch records, 32 Mi UTF-16 units/batch.
Caller-owned maps must not mutate during a call. No network or filesystem access.

For every admitted template and record, rendering either returns the selected
branches with literal substitution and unresolved-key set, or explicitly refuses
an exceeded budget. This is the intended universal contract, not a formal proof;
630 finite regression assertions provide bounded evidence, not exhaustive proof.

Pagination uses finite positive point heights, unique line indices, page height
at least 120pt, nonnegative separator, at most 100,000 lines. Every indivisible
line plus notes must fit; unsupported splitting fails before pagination. For every
accepted input, lines must appear exactly once in order, pages must not exceed
the supplied height. Comparisons use strict Double arithmetic, no epsilon.

ROUND uses decimal `BigDecimal.valueOf` and HALF_UP, truncates fractional digits
toward zero, and no longer clamps to ±12. Extremely positive precision leaves a
finite Double unchanged; extremely negative precision returns zero. Overflow
returns NUM. This is a declared decimal policy, **not** a proof of bit-for-bit
Excel/LibreOffice compatibility across all floating-point inputs.

Resources counted: template units, output units, visits, records, line count,
and executed assertions—not application throughput or memory benchmarks.
Do not substitute parser compilation, function count, or screenshots for acceptance.

## B/C. Outcomes and frontier

H+: bounded host regression evidence for these repairs. H−: malformed/oversized
inputs are deliberately unsupported, not silently transformed. H∂: Office import,
formatting preservation, UI routing, accessibility, device lifecycle and native
rendering remain required for app acceptance. No impossibility theorem or
best-known research frontier is asserted. No external research was performed in
this round; the blockers addressed were local algorithmic defects, not unavailable
third-party engines. No imported theorem or copied external implementation.

## D. Negative space / shortcut detector

Not solutions: (1) regex nesting, (2) reparsing record data, (3) unresolved markers
in inactive branches, (4) malformed blocks left in output, (5) unlimited nesting,
(6) unlimited empty loop expansion, (7) unlimited batches, (8) coercing wrong list
types to empty, (9) NaN heights, (10) negative notes, (11) duplicate line indices,
(12) silent oversized-line overflow, (13) binary multiply tie rounding,
(14) silently clamped precision, (15) kernel tests called UI acceptance,
(16) host images called device screenshots, (17) prior CI called current CI.
The modal regex shortcut fails because a lazy match pairs the first closing tag
rather than the structurally matching tag. The nested-if and per-item-if tests
in R54Checks detect this. Literal `{{secret}}` record data detects reparsing leaks.

## E–G. Six distinct mechanisms and attacks

| Mechanism / artifact | Established portion | Candidate-specific attack / cheapest test | Remaining gap |
|---|---|---|---|
| Recursive block parser / WriterMailMergeEngine | Nested block regressions pass | mismatched closing tag; reject | finite tests, not grammar proof; local |
| AST/data separation / same kernel | injected field stays literal | substitute `{{secret}}`; exact output | caller mutation/custom toString cost; local |
| Expansion budgets / same kernel | oversized output rejected | three million-char fields; reject | no total heap bound for hostile caller objects; local |
| Page preflight / WriterFootnotePaginator | invalid and oversized geometry rejected | NaN, duplicate, >page line; reject | splitting oversized notes unsupported; local |
| Ordered greedy packing / same paginator | 200 line-count cases preserve order and cap | alternating note-bearing lines | measured glyph shaping/widows not implemented; app-level |
| Decimal scaling / FormulaEval | signed ties and >12 precision repaired | ±1.005, negative precision, extreme digits | independent full Office numerical corpus; app-level |

These are distinct repair mechanisms reviewed sequentially by one assistant,
not six independent agents or six completed alternative applications.

## H/I. Execution and repair gates

Actually executed on this branch:
- pinned bootstrap: Kotlin compiler 2.2.21 and disposable OpenJDK JRE 17.0.9;
  no Android SDK, Gradle build, emulator, physical hardware or theorem prover.
- `python3 android/tools/run_r54_contracts.py`: **630 assertions PASS**,
  production Kotlin compiled with `-Werror` and executed on JVM.
- `run_r53_contracts.py`: **25,425 assertions PASS**.
- `check_r53_reference.py`: **411 checks / 77 artifacts PASS**.
- `render_r53_visual_preview.py`: **1,376 boxes / 180,495 pairs PASS**.
  Visually inspected regenerated `r53-workbench-overview.png`: dense but visibly
  separated panels and readable headings; tiny ledger text needs zoom. Its R53
  labels/claims are historical; it is not a native screenshot or new R54 UI test.

R54 tests are authored by the same assistant, not independently held out.
R53 Python reference checks supply a separate implementation for existing R53
artifacts, not an independent oracle for all new mail-merge semantics.
No simulations, peers, external database, expert review, or parallel agents used.
New patch attacks include malformed nesting, record template injection, expansion
bombs, invalid geometry and high-magnitude precision. Existing R53 gates were
rerun after patches. CI workflow now runs the R54 standalone suite; remote result
must be read from the PR, not inferred from its existence.

## J. Remaining gap and next test

Commit threshold: new production code compiles warning-free, all R54 checks pass,
R53 contract and reference gates pass, no acceptance inflation. Met locally.
The highest-information next test is a real Android Studio/device workflow that
opens, edits, saves, closes and reopens representative Writer and spreadsheet
files, comparing content/formula/formatting preservation against independently
created files. These kernels alone do not supply that workflow.

Laptop: open `android/` in Android Studio; use the pinned SDK/toolchain in
`docs/BUILD.md` and run the existing app configuration. This round does not verify
Rabbit 1 compatibility or produce an APK. Host-only reproduction:

```sh
python3 android/tools/bootstrap_host_tools.py install
python3 android/tools/run_r54_contracts.py
```

No dependency added to the Android app. Modified/new source follows existing
Apache-2.0 SPDX convention; no third-party source copied. This is not a legal
audit of the inherited dependency stack.
