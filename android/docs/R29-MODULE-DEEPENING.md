# R29 module deepening — absolute values and percentages

Working branch `arena/350b91e4-osb`. Evaluation date 2026-10-21. Full contract:
[R29-PLAN](R29-PLAN.md) · [Results](R29-RESULTS.md) · [Privacy PRD](R29-PRIVACY-PRD.md).

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
| Modules deepened by R29 with NEW executed kernels | 10/50 | 20.00% |
| Modules deepened by R26–R29 combined (distinct) | 24/50 | 48.00% |
| Fully specification-accepted modules | 0/50 | 0.00% |
| Unfinished at full scope | 50/50 | 100.00% |

R29 deepens: :feature:beam (ShareManifest), :core:storage (QuotaLedger),
:feature:alerts (AlertThrottle), :feature:files (FileSort), :core:commands
(AliasTable), :feature:accessibility (ContrastRatio), :core:settings
(LocaleMatch), :feature:backup (RetentionPlan), :cast:dlna (DidlLite),
:format:ooxml (CsvDialect). None overlaps R26–R28 modules, so the combined
distinct count rises from 14/50 to 24/50 (48.00%).

The :app census row gained UsbAllowlist store-logic evidence (executed
against an in-memory fake) but is NOT counted as deepened: the Android
wiring is unaudited, and authored surfaces deepen no row until CI compiles
and a device run receipts them.

## Prior turns (unchanged)

R26 deepened 6/50 (12%, 157 assertions); R27 deepened 8/50 (16%, 106
assertions); R28 deepened 10/50 (20%, 112 assertions); R29 adds 128 host
assertions (111 over 10 kernels + 17 authored-support). Cumulative counts
are evidence volume, not completion: full acceptance remains 0/50.
