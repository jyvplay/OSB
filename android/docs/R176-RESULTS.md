# R176 results — partial system delivery, not Class A

2026-11-27; baseline `d3e9bfb`, PR [#69](https://github.com/jyvplay/OSB/pull/69).
[Formal model, literature boundary and A–J portfolio](R176-PLAN.md).

## What changed at the user/system boundary

Shell Settings no longer directly shares live probe text as a diagnostic. It opens
an internal native Activity with explicit fixed-field opt-in, bounded optional note,
exact redacted-text review, full digest display and fresh approval checkbox.
Approval produces an opaque session-bound single-use capability. Only after this
step may the system Create Document picker return a ZIP destination. A single
worker writes/closes it and reopens the same selected URI for bounded full-byte
readback. Success clears the staged review and warns that integrity is not
anonymity/cloud durability. Discard and failure handling require fresh approval.

A separate Open Document route verifies an existing bounded diagnostic ZIP without
extracting it or displaying its values. It displays only count/size/digest/warning.
No INTERNET or other permission was added. Internal Activity has no intent filter,
is not exported, is excluded from recents, and retains FLAG_SECURE. The privacy
scratchpad's no-export boundary and no-INTERNET manifest are unchanged. The shell
itself already has INTERNET: user-selected providers may also use a network; this
is not an offline-provider guarantee.

Shared diagnostics now live in `core:security`. The privacy companion preserves
its historical API using thin facades. The actual existing CLI and R146 runner
compile/use the same engine, not a divergent new diagnostic implementation.
R176 adds a strict bounded ZIP reader alongside the reviewed serializer. The ZIP
subset isn't a general ZIP importer or authenticity/signature mechanism.

Rotation retention and provider-thread code exist; **native lifecycle/UI behavior
has not been executed here**. Six new Activity/MediaStore instrumentation tests
are authored but uncompiled/unrun, and do not automate DocumentsUI. An Android
emulator test that was never run is not a successful device test.

## Production repair breadth — 38 files / 25 module owners

Repairs target actual observed compiler/behavior failures, not new claimed complete
features. The runtime source-union gate compiles **120 production files + 1 probe**
together with Kotlin 2.2.21/JVM17 and `-Werror`. It includes every production Kotlin
source in model/windowing/index/commands and the 21 shift-repaired sources.

| Owner(s) | Implemented / repaired | Direct executed evidence / exact limit |
|---|---|---|
| app, feature/settings | Native review→approval→SAF save/readback + existing archive verification route | API35 Activity compile; structural route/manifest controls. Compose/Gradle/device unrun |
| core/security, privacy | Shared diagnostic state/session/workflow/inspector; compatibility facades; constant-time bound expression | 84 real file/thread assertions, 27 independent ZIPs; R146 314-vector rerun. No anonymity proof |
| core/model | Eliminate duplicate placeholder SemVer; canonical parser/precedence + release-only range API compatibility | Full source union; R43 372 assertions; 1039 independent ranges, huge numeric prerelease test |
| core/settings | Separate colliding catalog wrapper from canonical scope definitions; preserve both public APIs | Both catalog APIs compiled and exercised in R176; source gates |
| core/windowing | Register actual display areas; choose fitting areas; clamp dimensions before positions; refuse impossible minima; Long distance arithmetic | 262 independent geometry cases incl multi-display, oversize, Int extremes |
| core/index | Repair Horspool constant syntax | 404 binary searches vs Python bytes.find on actual Kotlin |
| feature/calc | Preserve enum conversion and table conversion APIs while eliminating duplicate UnitConverter; repair DecimalMath Java API calls and pre-arithmetic input limits | 169 independent decimal34 half-even results; conversion assertions; not arbitrary-precision transcendental math |
| core/capabilities | Kotlin shift syntax, Char ranges and invalid extension repair | Host compile and actual supplied-input smoke; not device discovery |
| core/documents, core/files | Valid shift bounds in line index, word count, normalizer, BOM detector | Host compile + supplied-input smoke; no provider integration |
| cast/castv2, cast/dlna | Valid shift syntax; namespace String/type repair | Host compile and supplied-input refusal checks. Existing nonstandard framing is NOT Cast protobuf interoperability |
| feature/lens | Valid shift bounds, Byte/Double type fixes, output pixel cap before OCR preprocessing allocation | Host compile and supplied small image smoke; not OCR recognition/pipeline acceptance |
| feature/terminal, format/image | Valid shifts/escape/Byte conversions in ANSI and PNG parser | Host compile and supplied-input smoke; not terminal/device rendering or full PNG decoding |
| feature/notes | Correct optional backlink regex group use / warning | Actual `[[id|label]]` smoke and R146 regression; not full Notes system |
| feature/editor, backup, continuity, automation | Valid bound constant syntax while retaining existing operations | Actual edit/undo/redo, manifest, current-time clipboard and macro smoke |
| feature/beam, capture | Valid shift syntax | Runtime reflected constants + host compile; no radio/capture-device claim |

24 repaired bound constants in 21 sources are inventoried with paths/class/values
in `tools/r176_build_surface.json`; each is reflected from actual JVM classes.
The new scanner rejects recurrence of C-style numeric `const val ... << ...` in
production Kotlin. Source compilation is much stronger than the old text-only
checks, but it is not the complete Android dependency graph.

## Tests actually executed

| Command / gate | Observed outcome | Does NOT prove |
|---|---|---|
| `python3 tools/check_project.py --keep` | PASS, all prior pins retained; exact new nonexported Activity allowed; permissions unchanged | APK/manifest merge/runtime |
| `python3 -m unittest discover -s tools -v` before repairs | 3406 tests; 11 failures, 3 errors, 443 skips | Candidate result |
| Same full suite after repairs | **3575 tests; 0 failures/errors; 443 skips** | Skipped execution or device behavior |
| `python3 tools/run_r176_checks.py --android-jar <jar> --artifacts <dir>` | **15 tests PASS**; source-union compile; 84 workflow assertions, 32 build-smoke assertions +24 constants; 1039 range /169 decimal /262 geometry /404 search /27 ZIP cases | Full product or statistical universality |
| R176 native API compile | PASS `-jvm-target 17 -Werror`; actual API35 stubs SHA recorded in PLAN | SDK37/AGP/resources/Compose/APK/device |
| `python3 tools/run_r43_contracts.py` | PASS 372 assertions /23 historical kernels | Android execution |
| `python3 tools/run_r146_checks.py` | PASS 314 cases /22 lanes; all four W1–W4 malicious controls caught | Class A |
| R119, R43, R45, R46 focused source suites | PASS: 25, 38, 52, 66 tests respectively before final full-suite rerun | Runtime proof |
| Android instrumentation/provider/UI tests | **AUTHORED, NOT COMPILED OR RUN locally** | Any native acceptance |

The full suite deliberately prints an R73 incorrect-oracle `MISMATCH` from a
negative control whose unittest expects detection. It does not represent a hidden
production failure. R146 likewise prints DEFECT lines for deliberately mutated
implementations and passes only when those controls are detected. Never infer a
PASS from a shell pipeline's final `tail`; durable gates check subprocess return
codes directly.

Baselines and final logs are kept under `docs/verification/r176/`. Host logs can
contain sanitized absolute temporary paths; app exports do not. CI now runs the
R176 runner in the existing host job; local success does not predict unobserved
remote execution. Final PR/merge status belongs in the turn report/GitHub history,
not a prematurely written source assertion.

## Visual inspection — real output, explicitly host-only

`docs/verification/r176/diagnostic-protocol.png` is a real Java2D rendering of a
real 43-byte redacted review and real successfully saved/readback ZIP, not an
Android screenshot or design mock. It is generated by the Kotlin workflow itself
and shows REVIEW→SAVED phases, full digest and bytes. The image was opened and
visually inspected: text was readable and within its boundaries; no host-frame
clipping observed. The matching `frame-review.zip` contains the actual artifacts.
This verifies the **host protocol frame only**. Native font scaling, touch targets,
scrolling, TalkBack order, picker overlay/lifecycle and device screenshots remain
unverified. FLAG_SECURE was not disabled to manufacture a visual acceptance claim.

## Current actual testability and original-spec completeness

The SHA-bound per-module register is `MODULE-TESTABILITY.md` /
`module-testability.json`; per-feature routes and source evidence are in
`feature-register.json` and `requirements.csv`. These are navigational ledgers,
not test results or declarations of completion.

* **50/50 modules (100%) have substantial testable subsets**; 0/50 lack any
  established subset. This does not mean all features in every module are runnable.
* **0/50 modules (0%) are verified full Class A; 50/50 (100%) remain unfinished**.
* Original 231-row census remains: **221 partial/unverified, 6 not implemented,
  3 excluded, 1 superseded, 0 verified complete**. No denominator was reduced.
* Original complete-app goal, Rabbit 1 runnability and all-module A-grade goal are
  **not achieved**. Evidence quantity rose (169 new collected tests; actual shared
  compilation and system boundary work) but is not a certification of breadth.

## Highest-value remaining acceptance gates

1. Android Studio Rabbit 1/JDK17/SDK37 full build, lint, merged manifest, APK install.
   If CI reveals more compile defects, fix actual callers/source closure, not an
   oracle copy. The local API35 compile is only a diagnostic boundary check.
2. Real Android API/device matrix: native Activity tests plus DocumentsUI selection,
   cancellation, persisted grants, local/cloud provider corruption/failure/latency,
   background/rotation, process-death while picker is open. Provider blocking cannot
   be universally prevented by an app-side executor.
3. Native visual/AX review at multiple font scales/densities with secure window
   retained. No host-frame substitution.
4. Complete each original feature's actual system flow and remaining module tests;
   privacy, permission/policy, performance and final A-grade gates still apply.
5. General codec/protocol interoperability, robust malformed-packet coverage,
   comprehensive image/terminal correctness and real media/network/file-provider
   integration exceed these supplied-input smoke checks.

No prior working feature was deliberately removed; broad compatibility tests and
source pins pass. A green host suite cannot establish that every existing Android
feature survived until the app is built and run. This is the strongest verified
partial result, with explicit remaining gaps rather than a full-success claim.

## Remote follow-up after first push

Both push/PR `host-contracts` checks passed on `8ce57c6`, including the new R176
runner. Android assembly reached the repaired model/windowing/index compilation
but failed at `core/commands/CommandRouter.kt:29`: two parameters named `s`. Device
annotations explicitly reported no executed cases. This is an actual source
failure, not attributed to the emulator. R176 repairs that declaration and adds
all 25 production commands sources to the compile union (120 production +1 probe)
plus six real routing/name/refusal assertions. Focused rerun passes. Remote results
for the subsequent commit must not be inferred from the first push's checks.
