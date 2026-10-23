# R37 module deepening — absolute values and percentages

Working branch `arena/350b91e4-osb`. Evaluation date 2026-10-23. Full contract:
[R37-PLAN](R37-PLAN.md) · [Results](R37-RESULTS.md) · [Privacy PRD](R37-PRIVACY-PRD.md).

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
| Modules deepened by R37 with NEW executed kernels | 14/50 | 28.00% |
| Modules deepened by R26–R37 combined (distinct, executed kernels) | 45/50 | 90.00% |
| Fully specification-accepted modules | 0/50 | 0.00% |
| Unfinished at full scope | 50/50 | 100.00% |

Counting note (this round's only metric change): "distinct" counts modules whose
census row names a kernel **executed on the host JVM**. `:format:native`'s R26
line describes isolated-job lifecycle contracts and TSV import, not an executed
kernel, so it is excluded — that is why 46 rows contain a "deepens" sentence but
45 count. R36 said 45 with the same exclusion; R37 re-derived it from the JSON
rather than copying the number.

R37 deepens: :feature:notes (SlugHead), :feature:files (SizeBucket),
:feature:continuity (TaskTally), :format:archive (XzHead), :feature:editor
(PasteWrap), :cast:airplay (RtspReason), :privacy (UtmStrip), :core:security
(HibpRange), :feature:maps (PolyLine), :feature:media (Mp4Box),
:feature:terminal (OscLink), :feature:find (RankFuse), :core:commands
(ExitCode), :feature:lens (FovMath). All fourteen are second-or-later kernels
(survey: no first-deepen surface survived the ownership cut), so the distinct
count holds at 45/50 while the *number of executed assertions per module*
rises.

## Absolute execution counts

| Kernel | New assertions | Fixture |
|---|---|---|
| SlugHead | 18 | — |
| SizeBucket | 22 | — |
| TaskTally | 14 | — (VTODO text built in the consumer) |
| XzHead | 17 | `tools/fixtures/r37/xz.bin` |
| PasteWrap | 20 | — |
| RtspReason | 31 | — |
| UtmStrip | 19 | — |
| HibpRange | 16 | — |
| PolyLine | 20 | canonical 27-char example |
| Mp4Box | 16 | `tools/fixtures/r37/mp4.bin` |
| OscLink | 20 | — |
| RankFuse | 18 | — |
| ExitCode | 25 | — |
| FovMath | 18 | — |
| **Total** | **274** | 2 binary + 2 transcript fixtures |

274 static call sites = 274 executed assertions (no loop- or
branch-amplified assertion), which is the strongest honest statement this round
can make about coverage: every assertion written is one assertion run.

## What is still NOT deepened

:app, :benchmark, :baselineprofile and :testing carry no kernel and are cut on
ownership grounds rather than skipped (:app has now been examined nine times;
12/12 files import android/androidx). Full scope remains 0/50 accepted — the
honest figure for a bounded-subset project with device and engine gates open.
