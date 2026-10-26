# R30 module deepening — absolute values and percentages

Working branch `arena/350b91e4-osb`. Evaluation date 2026-10-22. Full contract:
[R30-PLAN](R30-PLAN.md) · [Results](R30-RESULTS.md) · [Privacy PRD](R30-PRIVACY-PRD.md).

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
| Modules deepened by R30 with NEW executed kernels | 10/50 | 20.00% |
| Modules deepened by R26–R30 combined (distinct) | 33/50 | 66.00% |
| Fully specification-accepted modules | 0/50 | 0.00% |
| Unfinished at full scope | 50/50 | 100.00% |

R30 deepens: :cast:airplay (RtspMessage), :feature:capture (Timecode),
:feature:continuity (VEventParse), :feature:together (LamportClock),
:format:image (GifInfo), :feature:settings (PatchScope), :format:archive
(TarHeader), :feature:lens (ZoomLadder), :feature:messages (SmsSegments),
:core:model (DensitySelect). Nine are new to the R26–R29 set;
:feature:settings gains a second executed kernel (LocaleMatch R29,
PatchScope R30), so the combined distinct count rises from 24/50 to
33/50 (66.00%).

R30 authors no Android code and no authored-support store, so no other row
moves: the census delta is exactly the 10 kernel rows above.

## Prior turns (unchanged)

R26 deepened 6/50 (12%, 157 assertions); R27 deepened 8/50 (16%, 106
assertions); R28 deepened 10/50 (20%, 112 assertions); R29 deepened 10/50
(20%, 128 assertions incl. authored-support); R30 adds 98 host assertions
over 10 pure-logic kernels. Cumulative counts are evidence volume, not
completion: full acceptance remains 0/50.
