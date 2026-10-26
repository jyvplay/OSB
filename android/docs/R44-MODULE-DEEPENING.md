# R44 module deepening census

- **50/50 modules (100%)** hold substantial testable subsets.
- **23/50 modules (46%)** received NEW executed kernels this round
  (ties the R43 record; R42: 21). All verified deepens-again BEFORE
  writing.
- **45/50 modules (90%)** carry at least one distinct executed kernel
  (the five without remain `:app`, `:format:native`, `:benchmark`,
  `:baselineprofile`, `:testing`).
- **0/50 specification-accepted. No A-grade claim.**

## R44 kernels (sum 378 assertions (post-repair), runtime-verified)
| Module | Kernel | | Module | Kernel |
|---|---|---|---|---|
| :format:ooxml | SheetHide | | :core:hw | DisplayInch |
| :core:documents | QuoteNest | | :feature:backup | BackupName |
| :feature:editor | GoToLine | | :feature:launcher | AlphaIndex |
| :format:pdf | ObjNum | | :format:image | ExifOrient |
| :core:security | SriHash | | :feature:browser | AcceptLang |
| :format:archive | DebVersion | | :feature:notes | TagExtract |
| :feature:lens | Issn + UpcE | | :feature:alerts | Severity |
| :feature:terminal | CwdTilde | | :feature:files | HiddenDot |
| :core:index | Levenshtein | | :feature:together | SessionCode |
| :core:settings | PrefKeyLint | | :feature:media | SampleBytes |
| :core:commands | HistDedup | | :feature:messages | SubjectNorm |
| :privacy | GpsRational | | | |

Totals: 24 kernels / 23 modules / 378 assertions (R43: 23/23/372).
Writer+spreadsheet: SheetHide + QuoteNest + GoToLine = 3 kernels.

## CI bridge
`r44Contracts` joins the lane; once green, R37–R44 = **161 kernels /
2777 assertions** under Gradle per push.

## Ownership cuts this round (grep-verified; do not retry)
- BboxMerge — `feature/maps/TileCover.kt` owns bbox work.
- Timebase — `feature/capture/Timecode.kt` owns drop-frame.
- FontScaleBucket — designsystem metrics own scale handling.
- PdfVersionHeader — `PdfTrailer.kt` owns the %PDF header.
- DefinedName — adjacent to LiteralXlsxImport's refusals; cut as murky.
- FrameBudget - CUT POST-PUSH: redeclared PerformancePolicy.kt's class;
  CI caught what host isolation could not; replaced by Severity.
  Grep the literal kernel name, always.
- JournalSeq — `core/windowing/DesktopCommandStream.kt` owns stream
  sequencing; cut to avoid surface confusion.

## Census mechanics
Deepen ×3 byte-identical; `--check` green; TOUCHED empty; sibling
allowance extended to r43's tool-test file; inventory regenerated
BEFORE the lab.
