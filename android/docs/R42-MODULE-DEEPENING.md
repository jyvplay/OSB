# R42 module deepening census

Standard restated: a module holds a *substantial testable subset* when
it contains enough real logic that a meaningful executed test exists.
Full numbers after R42:

- **50/50 modules (100%)** hold substantial testable subsets.
- **21/50 modules (42%)** received NEW executed kernels this round
  (R41: 20, R40: 19). All twenty-one verified deepens-again BEFORE
  writing.
- **45/50 modules (90%)** carry at least one distinct executed kernel
  (the five without remain `:app`, `:format:native`, `:benchmark`,
  `:baselineprofile`, `:testing`).
- **0/50 specification-accepted. No A-grade claim.**

## R42 kernels and executed assertion counts (sum 362, runtime-verified)

| Module | Kernel | Assertions |
|---|---|---|
| :format:ooxml | AbsRef | 25 |
| :format:ooxml | MergedCells | 16 |
| :format:pdf | PdfDate | 24 |
| :feature:maps | NmeaLat | 21 |
| :feature:browser | HstsParse | 18 |
| :feature:continuity | PairPin | 17 |
| :format:archive | ArHead | 12 |
| :core:documents | CaseCycle | 19 |
| :feature:editor | CommentToggle | 16 |
| :feature:notes | UnitPrice | 16 |
| :feature:capture | Rule3rds | 14 |
| :core:perf | Histo | 14 |
| :feature:terminal | CsiCursor | 18 |
| :privacy | GpcHeader | 15 |
| :feature:vault | PwPolicy | 12 |
| :feature:media | GainDb | 15 |
| :core:storage | HexBytes | 15 |
| :feature:alerts | SnoozeSeq | 14 |
| :feature:calc | NiceStep | 15 |
| :feature:automation | NextRun | 16 |
| :feature:messages | Rfc2231 | 16 |
| :format:image | BmpHead | 14 |

Totals: 22 kernels / 21 modules / 362 assertions (R41: 21/20/361).
Writer+spreadsheet this round: AbsRef + MergedCells + CaseCycle +
CommentToggle = 4 kernels / 76 assertions (the formula-fill and merge
layers the grid needed after R41 completed addressing).

## CI bridge status

`r37Contracts` (backport) and `r42Contracts` joined the JVM lane. When
green, R37–R42's **114 kernels / 2027 assertions** execute under the
real Gradle toolchain in CI. Ledger records the first run.

## Ownership cuts (grep-verified; do not retry)

None new this round — all 22 surfaces came back free on the pre-write
sweep (the R41 spare bank plus fresh candidates); the standing banked
cuts from R38–R41 remain in force.

## Census mechanics

Deepen ×3 byte-identical; `--check` green; TOUCHED empty (all 22
kernels are new files); sibling allowance extended to r41's tool-test
file; source inventory regenerated BEFORE the lab.
