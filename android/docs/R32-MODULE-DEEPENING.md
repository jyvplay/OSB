# R32 module deepening — absolute values and percentages

Working branch `arena/350b91e4-osb`. Evaluation date 2026-10-22. Full contract:
[R32-PLAN](R32-PLAN.md) · [Results](R32-RESULTS.md) · [Privacy PRD](R32-PRIVACY-PRD.md).

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
| Modules deepened by R32 with NEW executed kernels | 10/50 | 20.00% |
| Modules deepened by R26–R32 combined (distinct) | 45/50 | 90.00% |
| Fully specification-accepted modules | 0/50 | 0.00% |
| Unfinished at full scope | 50/50 | 100.00% |

R32 deepens: :feature:calc (FractionApprox), :format:lok (OfficeCapability),
:cast:castv2 (CastMediaMeta), :core:commands (ArgSplit), :core:security
(PinRules), :privacy (RetentionSweep), :feature:continuity (ICalDuration),
:feature:maps (GeoUri), :feature:vault (LockoutPolicy), :feature:media
(CueSheetParser). Three are new to the R26–R31 set (:feature:calc,
:format:lok, :cast:castv2 — the lok slot surveyed weakest three rounds
before yielding its honest surface); seven gain a second executed kernel, so
the combined distinct count rises from 42/50 to 45/50 (90.00%).

R32 authors no Android code, so no other row moves: the census delta is
exactly the 10 kernel rows above. Remaining un-deepened product modules:
:app only (cut a fourth round — all honest surfaces Android-inline or
speculative), plus the :benchmark, :baselineprofile, :testing harnesses,
which take no product kernels.

## Prior turns (unchanged)

R26 deepened 6/50 (12%, 157 assertions); R27 deepened 8/50 (16%, 106
assertions); R28 deepened 10/50 (20%, 112 assertions); R29 deepened 10/50
(20%, 128 assertions incl. authored-support); R30 deepened 10/50 (20%, 98
assertions); R31 deepened 10/50 (20%, 152 assertions); R32 adds 157 host
assertions over 10 pure-logic kernels. Cumulative counts are evidence
volume, not completion: full acceptance remains 0/50.
