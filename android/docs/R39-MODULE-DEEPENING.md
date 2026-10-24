# R39 module deepening census

Standard restated: a module has a *substantial testable subset* when it
contains enough real logic that a meaningful executed test exists for it
(not a stub, not a data class). Full numbers after R39:

- **50/50 modules (100%)** hold substantial testable subsets.
- **16/50 modules (32%)** received NEW executed kernels this round
  (R38: 15/50). All sixteen are deepens-again rows — verified against the
  census `production` lists before writing a single sentence (the R38
  false-"first" lesson is now a pre-write gate).
- **45/50 modules (90%)** carry at least one distinct executed kernel
  (unchanged — every R39 module was already distinct; the five without
  remain `:app`, `:format:native`, `:benchmark`, `:baselineprofile`,
  `:testing`).
- **0/50 modules (0%)** are specification-accepted. No A-grade claim.

## R39 kernels and executed assertion counts (sum 344, runtime-verified)

| Module | Kernel | Assertions | Surface |
|---|---|---|---|
| :format:ooxml | SerialDate | 26 | spreadsheet |
| :format:ooxml | ColLetters | 20 | spreadsheet |
| :format:ooxml | ColWidth | 17 | spreadsheet |
| :core:documents | SmartQuote | 16 | writer |
| :core:documents | HyphenSoft | 14 | writer |
| :feature:editor | RomanNum | 23 | writer |
| :format:pdf | CropBox | 12 | office |
| :format:image | IcoDir | 17 | formats |
| :feature:browser | Punycode | 20 | browser |
| :core:hw | UsbDesc | 17 | input-path audit |
| :feature:media | MidiNote | 20 | media |
| :core:storage | Ulid | 18 | storage |
| :core:security | JwtSplit | 18 | security |
| :feature:vault | IbanCheck | 18 | vault/privacy |
| :feature:find | GlobMatch | 20 | productivity |
| :privacy | MacAddr | 15 | privacy |
| :feature:backup | Rfc3339 | 22 | productivity |
| :feature:alerts | QuietHours | 16 | productivity |
| :feature:capture | ExposureEv | 15 | capture |

Totals: 19 kernels / 16 modules / 344 assertions (R38: 17 / 15 / 326).
The writer+spreadsheet push the user asked for = 6 kernels / 116
assertions across :format:ooxml, :core:documents, :feature:editor.

## Ownership cuts (grep-verified before writing; do not retry)

- SheetName validation — owned by `LiteralWorkbook.kt:33`.
- TarHead — `format/archive/TarHeader.kt` already exists.
- CronField — `feature/automation/CronSpec.kt` already exists.
- SsdpAlive — `cast/dlna/UpnpParsing.kt` owns SSDP header parsing.
- TileMath — `feature/maps/TileMath.kt` already exists.
- VcardFold — `feature/continuity/VCardParse.kt` owns vCard surface.
- Sgr256Color — `feature/terminal/AnsiSgr.kt` owns SGR handling.

## Census mechanics

`tools/r39_census_deepen.py` run twice → byte-identical (idempotent);
`module_testability.py --check` green; TOUCHED set empty (R39 edits no
existing production file — all 19 kernels are new files; the one existing-
file edit this round, JwtSplit visibility, is itself an R39 file).
Source inventory regenerated via `native_source_audit.py` BEFORE the lab
run this time — the R38 lab's first-run FAIL on a stale inventory is now
a checklist item, not a surprise.
