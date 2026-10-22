# R35 module deepening — absolute values and percentages

Working branch `arena/350b91e4-osb`. Evaluation date 2026-10-22. Full contract:
[R35-PLAN](R35-PLAN.md) · [Results](R35-RESULTS.md) · [Privacy PRD](R35-PRIVACY-PRD.md).

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
| Modules deepened by R35 with NEW executed kernels | 10/50 | 20.00% |
| Modules deepened by R26–R35 combined (distinct) | 45/50 | 90.00% |
| Fully specification-accepted modules | 0/50 | 0.00% |
| Unfinished at full scope | 50/50 | 100.00% |

R35 deepens: :feature:lens (EvShift), :feature:vault (XorSplit),
:core:documents (WordDiff), :feature:files (DupGroup), :core:index
(SnippetCut), :feature:media (Id3Head), :feature:maps (TileCover),
:feature:continuity (VCardParse), :privacy (FieldMask), :feature:editor
(IndentDetect). All ten are second (or later) kernels — no first-deepen
surface survived the survey, so the combined distinct count holds at 45/50
(90.00%). Three deferred seconds were cut as already-owned (SrtCue owned
by SubRip verbatim; BackoffPlan owned-adjacent by LockoutPolicy's
doubling-backoff math; UnitConvert owned by calc's Units.definition), and
:app was cut a seventh time (12/12 files import android/androidx; all
honest surfaces Android-inline or speculative). Remaining un-deepened: :app
only, plus the :benchmark, :baselineprofile, :testing harnesses, which take
no product kernels.

## Prior turns (unchanged)

R26 deepened 6/50 (12%, 157 assertions); R27 deepened 8/50 (16%, 106
assertions); R28 deepened 10/50 (20%, 112 assertions); R29 deepened 10/50
(20%, 128 assertions incl. authored-support); R30 deepened 10/50 (20%, 98
assertions); R31 deepened 10/50 (20%, 152 assertions); R32 deepened 10/50
(20%, 157 assertions); R33 deepened 10/50 (20%, 142 assertions); R34
deepened 10/50 (20%, 166 assertions); R35 adds 129 host assertions over 10
pure-logic kernels. Cumulative counts are evidence volume, not completion:
full acceptance remains 0/50.
