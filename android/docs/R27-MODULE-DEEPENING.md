# R27 module deepening — absolute values and percentages

Working branch `arena/350b91e4-osb`. Evaluation date 2026-10-21. Full contract:
[R27-PLAN](R27-PLAN.md) · [Results](R27-RESULTS.md).

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
| Modules deepened by R27 with NEW executed kernels | 8/50 | 16.00% |
| Fully specification-accepted modules | 0/50 | 0.00% |
| Unfinished at full scope | 50/50 | 100.00% |

The 100% testable figure is a lower bound on bounded subsets, never delivery
or acceptance. The 16% deepening figure counts modules that gained at least
one new host-executed kernel + consumer check this turn: :feature:launcher
(TabletopArrange), :feature:media (PodcastFeed, StreamPolicy), :feature:maps
(Gazetteer), :feature:browser (ContentBlocker), :core:documents (MarkdownDoc),
:core:security (FirewallPolicy, UsbInventory), :core:designsystem (TypeScale),
:feature:automation (CronSpec). Authored-only Android edits (BeautyDock tag
alignment, StreamPlayerActivity launch contract, MediaHub stream-player
button) deepen no row until they compile and run under CI/Studio with exact
receipts; the BeautyDock tags are aligned but the dock is still unwired.

## Prior turns (unchanged)

R26 deepened 6/50 (12%) with 157 host assertions; R27 adds 106 host
assertions over 10 kernels. Cumulative host-assertion counts are evidence
volume, not completion: full acceptance remains 0/50.
