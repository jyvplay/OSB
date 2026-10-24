# R41 module deepening census

Standard restated: a module holds a *substantial testable subset* when it
contains enough real logic that a meaningful executed test exists for it.
Full numbers after R41:

- **50/50 modules (100%)** hold substantial testable subsets.
- **20/50 modules (40%)** received NEW executed kernels this round
  (R40: 19, R39: 16, R38: 15). All twenty verified as deepens-again rows
  against the census `production` lists BEFORE any sentence was written.
- **45/50 modules (90%)** carry at least one distinct executed kernel
  (the five without remain `:app`, `:format:native`, `:benchmark`,
  `:baselineprofile`, `:testing`).
- **0/50 modules (0%)** specification-accepted. No A-grade claim.

## R41 kernels and executed assertion counts (sum 361, runtime-verified)

| Module | Kernel | Assertions | Surface |
|---|---|---|---|
| :format:ooxml | SheetRange | 25 | spreadsheet |
| :format:ooxml | R1c1Ref | 20 | spreadsheet |
| :format:pdf | PdfPageGeometry | 13 | office |
| :core:commands | ShlexJoin | 18 | terminal/display |
| :feature:automation | DurationIso | 21 | schedules |
| :feature:maps | DmsFormat | 19 | maps |
| :core:model | ColorHex | 19 | theming |
| :feature:accessibility | ReadEase | 15 | a11y |
| :feature:lens | Ean13 | 14 | lens |
| :feature:browser | DataUri | 17 | browser |
| :core:index | Trigram | 20 | search |
| :feature:together | TurnOrder | 18 | collaboration |
| :core:perf | Ewma | 12 | perf |
| :core:settings | KeyMigrate | 13 | settings |
| :cast:castv2 | CastNs | 16 | cast |
| :core:documents | LineWidth | 24 | writer |
| :core:storage | ShardPath | 14 | storage |
| :feature:find | SpanMerge | 17 | productivity |
| :feature:beam | PiecePlan | 18 | beam |
| :feature:editor | TabStops | 14 | writer |
| :feature:launcher | GridPos | 14 | launcher |

Totals: 21 kernels / 20 modules / 361 assertions (R40: 20 / 19 / 360).
Writer+spreadsheet this round: 5 kernels / 96 assertions (SheetRange,
R1c1Ref, LineWidth, TabStops + the geometry composition serving office).

## CI-executed contracts (bridge status)

`r41Contracts` (new) and `r38Contracts` (BACKPORT — the R40-named next
test) joined `r39Contracts`/`r40Contracts` in the JVM lane. When this
round's push runs green, every kernel from R38 through R41 — 77 kernels,
1391 assertions — executes under the real Gradle toolchain in CI, not
only under host kotlinc. The ledger in R41-RESULTS records the first run.

## Ownership cuts (grep-verified before writing; do not retry)

- PsdHead — `format/psd/PsdInfo.kt` owns the 8BPS header (PSB refused
  there as "not supported in this build").
- ProtocolInfo — `cast/dlna/DidlLite.kt` owns res@protocolInfo.
- Base32Rfc — `feature/vault/TotpCode.kt` decodes base32 internally.
- SigFigs — Calculator's 34-significant-digit display contract is
  adjacent enough to confuse; cut as murky rather than argued.
- ConstTimeEq — JDK MessageDigest.isEqual exists; reimplementation
  without a distinct surface is rename-shaped; cut.
- AspectFit — owned by `feature/lens/ViewportTransform.kt` (R40 cut,
  reconfirmed).

## Census mechanics

`tools/r41_census_deepen.py` run twice → byte-identical; `--check`
green; TOUCHED empty (all 21 kernels are new files); sibling-round
allowance extended to r40's tool-test file (same documented pattern as
R40's fix). Source inventory regenerated BEFORE the lab run.
