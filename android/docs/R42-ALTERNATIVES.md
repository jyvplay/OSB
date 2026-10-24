# R42 alternatives — browser, Office engine, sandbox, productivity

## "Writer and spreadsheet to Class A this turn?" — the honest ledger again

No. 0/50 accepted stands. Movement this round:

- **Spreadsheet (:format:ooxml, FIFTEEN executed kernels)**: with
  AbsRef the grid gains FILL semantics ($-pinned parts under shift —
  the behavior every formula copy depends on) and with MergedCells the
  layout layer gains its no-overlap law. The pure-logic grid stack is
  now: letters, widths, serial dates, number-format structure, error
  literals, ranges, R1C1, $-refs, merges. What remains to A is
  unchanged in KIND: styles wiring, formula evaluation breadth,
  rendered grid, device interaction — none of which host kernels can
  honestly claim.
- **Writer**: CaseCycle + CommentToggle extend the editing-command
  layer (both involution-tested). The next writer step remains the
  R43-declared device-adjacent wiring (span exclusion for the
  educators), which is an integration task, not a kernel.

## Bridge economics (gates, structurally)

With the r37 backport, the CI bridge covers SIX rounds: R37–R42, 114
kernels / 2027 assertions under Gradle per push. Remaining backports
(r34/r36-era) are one source-set block each — R43 candidates. The
"host kotlinc ≠ shipping toolchain" gate is now a closed pattern, not
an open risk.

## Browser

HstsParse adds the transport-security header with spec-mandated
tolerance done RIGHT (unknown directives tolerated, duplicates
refused) — a case where "stricter" would have been WRONG, documented
in PLAN §D. With DataUri (R41) and the policy lineage, header-layer
intake is near-complete; rendering stays gated on the engine lane.

## Office engine

PdfDate completes the document-metadata layer for the PDF shell
(trailer + names + boxes + rotation + geometry + dates). DOCX path
unchanged; numbering projector remains the next composition step.

## Sandbox

GpcHeader (consent signals that refuse coercion), PwPolicy (policy
without strength claims, no substring leaks), HexBytes (strict digest
intake), ArHead/BmpHead (refuse-before-walk for two more container
families), PairPin (typed-code error detection with the
never-security line in scope).

## Productivity

UnitPrice (notes), SnoozeSeq (alerts), NextRun (automation), NiceStep
(calc graphing), Histo (perf), CsiCursor (terminal), Rule3rds
(capture), NmeaLat (maps), CaseCycle/CommentToggle (writer commands),
Rfc2231 (messages attachments), GainDb (media).

## Spares surveyed for R43 (re-grep before writing)

r34/r36 Gradle backports; editor span-exclusion wiring (TOUCHED
census flow); numbering.xml projector; `SheetFreeze` (pane model
minus the cut twips formula — geometry only); `DebControl` (the
control-file fields inside the ar member); `IcsFold` (RFC 5545 line
folding — check VEventParse ownership first); `TermTabs` (terminal
tab-stop list ops vs TabStops' fixed grid — verify distinctness).
