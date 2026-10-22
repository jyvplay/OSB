# R36 module deepening — absolute values and percentages

Working branch `arena/350b91e4-osb`. Evaluation date 2026-10-22. Full contract:
[R36-PLAN](R36-PLAN.md) · [Results](R36-RESULTS.md) · [Privacy PRD](R36-PRIVACY-PRD.md).

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
| Modules deepened by R36 with NEW executed kernels | 12/50 | 24.00% |
| Modules deepened by R26–R36 combined (distinct) | 45/50 | 90.00% |
| Fully specification-accepted modules | 0/50 | 0.00% |
| Unfinished at full scope | 50/50 | 100.00% |

R36 deepens: :feature:calc (RpnEval), :feature:files (NameClash),
:feature:editor (WrapCol), :core:commands (RedirPlan), :cast:castv2
(CastState), :feature:maps (ScaleBar), :feature:media (WavHead),
:format:image (TiffHead), :privacy (KAnon), :core:security (AttemptWindow),
:feature:lens (DofNear), :feature:vault (ShannonBits). All twelve are
second (or later) kernels — no first-deepen surface survived the survey,
so the combined distinct count holds at 45/50 (90.00%). One deferred
second was cut as already-owned (DlnaDuration owned by DidlLite's
strict duration grammar verbatim), and :app was cut an eighth time (12/12
files import android/androidx; all honest surfaces Android-inline or
speculative). Remaining un-deepened: :app only, plus the :benchmark,
:baselineprofile, :testing harnesses, which take no product kernels.

## Prior turns (unchanged)

R26 deepened 6/50 (12%, 157 assertions); R27 deepened 8/50 (16%, 106
assertions); R28 deepened 10/50 (20%, 112 assertions); R29 deepened 10/50
(20%, 128 assertions incl. authored-support); R30 deepened 10/50 (20%, 98
assertions); R31 deepened 10/50 (20%, 152 assertions); R32 deepened 10/50
(20%, 157 assertions); R33 deepened 10/50 (20%, 142 assertions); R34
deepened 10/50 (20%, 166 assertions); R35 deepened 10/50 (20%, 129
assertions); R36 adds 176 host assertions over 12 pure-logic kernels.
Cumulative counts are evidence volume, not completion:
full acceptance remains 0/50.
