# R41 alternatives — browser, Office engine, sandbox, productivity

## The recurring question: "writer and spreadsheet to Class A this turn?"

No — same honest shape, smaller distance. This round's movement:

- **Spreadsheet (:format:ooxml, THIRTEEN executed kernels now)** closes
  the ADDRESSING layer completely: ColLetters (R39) + SheetRange +
  R1c1Ref give cells, rectangular ranges, corner normalization,
  intersection/union, and both reference styles with no grid wrapping.
  Addressing was the last pure-logic layer under the grid; what remains
  to A is exactly the non-logic stack: styles wiring, formula breadth,
  rendered grid, device interaction — each named, none fakeable here.
- **Writer (:core:documents + :feature:editor, TWELVE executed
  kernels)** gains the column-geometry pair every monospace view needs:
  LineWidth (UAX #11 subset, held-out-verified) and TabStops (expansion
  that provably preserves geometry). The next step is wiring — R42's
  declared device-adjacent test (span-exclusion for the educators).

## Bridge economics (the structural answer to "overcome the gates")

The CI bridge pattern is compounding: with the r38 backport authored
this round, R38–R41's 77 kernels / 1391 assertions all execute under
Gradle in CI once the lane runs. The gate "host kotlinc is not the
shipping toolchain" is now structurally closed for every new round at
the cost of one source-set block — and closable backwards round by
round (R34-era checks are R42's candidate).

## Browser

DataUri completes the request-policy layer's last common scheme
(http(s)/about/blob policies existed; data: now parses with the RFC
default surfaced). The engine lane (Servo AAR) remains the rendering
gate; nothing in the policy layer blocks on it.

## Office engine

PdfPageGeometry turns three verified pieces (boxes, rotation,
inheritance) into the single display-surface answer a viewer shell
consumes — UserUnit refused rather than scaled keeps the honesty line.
DOCX-side: numbering families (Roman/letters/footnotes) complete;
next is the numbering.xml projector that composes them.

## Sandbox

ShlexJoin gives every "copy this command" UI a display-quoting path
that cannot be confused with execution (nothing here executes — the
inverse parser ArgSplit already refuses the dangerous shapes).
KeyMigrate makes settings schema evolution refuse data loss. ShardPath
bounds content-store fan-out deterministically.

## Productivity

DurationIso (schedules that refuse calendar lies), DmsFormat (maps),
Trigram (search candidates), SpanMerge (highlight rendering), TurnOrder
(together sessions), Ewma (perf smoothing with executed bias math),
Ean13 (lens scanning), GridPos (launcher paging), CastNs (cast intake),
ColorHex (theming), ReadEase (a11y writing feedback).

## Spares surveyed for R42 (re-grep before writing)

r34-era Gradle backport; editor span-exclusion wiring (TOUCHED-path
census flow); numbering.xml projector; `PdfDest` (named destinations);
`HarEntry` summary (browser devtools-lite); `NmeaLat` (the declared-out
GPS shape, maps); `UnitPrice` (shopping-list math, notes); LOK bridge
arg validation (format/lok has only two files — check surface first).
