# R40 module deepening census

Standard restated: a module holds a *substantial testable subset* when it
contains enough real logic that a meaningful executed test exists for it.
Full numbers after R40:

- **50/50 modules (100%)** hold substantial testable subsets.
- **19/50 modules (38%)** received NEW executed kernels this round
  (R39: 16/50, R38: 15/50). All nineteen verified as deepens-again rows
  against the census `production` lists BEFORE any sentence was written.
- **45/50 modules (90%)** carry at least one distinct executed kernel
  (unchanged; the five without remain `:app`, `:format:native`,
  `:benchmark`, `:baselineprofile`, `:testing`).
- **0/50 modules (0%)** are specification-accepted. No A-grade claim.

## R40 kernels and executed assertion counts (sum 360, runtime-verified)

| Module | Kernel | Assertions | Surface |
|---|---|---|---|
| :format:ooxml | NumFmtSections | 27 | spreadsheet |
| :format:ooxml | CellErr | 18 | spreadsheet |
| :core:documents | DashEducate | 18 | writer |
| :core:documents | ObjReplace | 15 | writer |
| :feature:editor | FootnoteMark | 20 | writer |
| :format:pdf | PdfRotate | 18 | office |
| :feature:browser | HtmlEntity | 23 | browser |
| :feature:messages | Rfc2047 | 17 | messages |
| :format:archive | SevenZHead | 13 | formats |
| :core:index | Fnv1a | 14 | search |
| :feature:lens | Mrz | 17 | lens |
| :core:files | SafeName | 23 | files |
| :core:perf | Percentile | 15 | perf |
| :feature:notes | TaskCheckbox | 18 | productivity |
| :core:windowing | SnapFraction | 14 | desktop |
| :cast:airplay | PlistHead | 14 | cast |
| :feature:launcher | Monogram | 14 | launcher |
| :feature:vault | Bech32 | 19 | vault |
| :core:security | OtpauthUri | 21 | security |
| :privacy | IpRedact | 22 | privacy |

Totals: 20 kernels / 19 modules / 360 assertions (R39: 19 / 16 / 344).
Writer+spreadsheet continuation: 5 kernels / 98 assertions across
ooxml+documents+editor, on top of R39's 6/116.

## NEW this round: CI-executed contracts (the quality jump)

`:testing:r39Contracts` and `:testing:r40Contracts` are registered
Gradle JavaExec tasks (source sets pulling the 39 kernel files + the two
consumers) and appended to the JVM lane of `android.yml`. When that lane
runs green, all 39 R39+R40 kernels will have executed under the REAL
Gradle/AGP toolchain in CI — not only under host kotlinc. Status at
commit time: authored; the ledger in R40-RESULTS records the first run.

## Ownership cuts (grep-verified before writing; do not retry)

- ZlibHead — `format/archive/ZlibHead.kt` already exists (prior round).
- Id3Head — `feature/media/Id3Head.kt` already exists.
- AspectFit — owned by `feature/lens/ViewportTransform.kt` + MediaHub.
- JpegMarkers — `format/image/JpegSeg.kt` owns JPEG segment walking.
- PaneSplit — cut for UNVERIFIABLE PRIMARY: the circulating xSplit-twips
  formula contradicts its own published example (2040 vs 2115 from the
  same code); shipping either number would be a guess. Revisit only with
  a real Excel-written fixture to measure against.

## Census mechanics

`tools/r40_census_deepen.py` run twice → byte-identical; `--check`
green; TOUCHED empty (all 20 kernels are new files). Source inventory
regenerated BEFORE the lab run. The per-round pre-write gate (verify
"first kernel" claims against the census row) ran for all 19 modules —
every row already had executed kernels, so every sentence is
deepens-again.
