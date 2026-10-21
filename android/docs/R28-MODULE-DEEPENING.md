# R28 module deepening — absolute values and percentages

Working branch `arena/350b91e4-osb`. Evaluation date 2026-10-21. Full contract:
[R28-PLAN](R28-PLAN.md) · [Results](R28-RESULTS.md) · [Privacy PRD](R28-PRIVACY-PRD.md).

## Testable-subset standard (unchanged definition)

At least one concrete nontrivial operational transformation, persistence
operation, protocol parser/serializer, runtime integration, or executable
harness with real source and an existing reproducible behavioral check. Folder
presence, compilation, value types, gate/denial-only code, simulated peers,
labels and authored-only UI do not qualify.

## Census

| Measure | Absolute | Percentage |
|---|---|---|
| Included modules | 50 | 100% (denominator; no module created or split) |
| Modules meeting the testable-subset standard | 50/50 | 100.00% |
| Modules not established by the standard | 0/50 | 0.00% |
| Modules deepened by R28 with NEW executed kernels | 10/50 | 20.00% |
| Fully specification-accepted modules | 0/50 | 0.00% |
| Unfinished at full scope | 50/50 | 100.00% |

R28 deepens: :core:files (TrashLedger), :core:windowing (SnapZones),
:feature:find (FrecencyLedger), :feature:maps (TileMath), :feature:media
(ChapterMarks), :core:security (ConsentLedger), :feature:browser (CookieJar),
:feature:automation (Recurrence), :feature:notes (FrontMatter),
:feature:terminal (Scrollback). Authored-only Android (UsbWatchReceiver,
ConsentDialog) deepens no row until CI compiles and a device run receipts it.

## Prior turns (unchanged)

R26 deepened 6/50 (12%, 157 assertions); R27 deepened 8/50 (16%, 106
assertions); R28 adds 112 host assertions over 10 kernels. Cumulative counts
are evidence volume, not completion: full acceptance remains 0/50.
