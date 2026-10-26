# R26 results — executed kernels, authored UI, no A-grade claim

Working branch `arena/350b91e4-osb`. Native app is `android/`, not the root web
prototype. Evaluation date 2026-10-21. [Contract/detectors/alternatives](R26-PLAN.md) ·
[Privacy PRD](R26-PRIVACY-PRD.md) · [Deepening census](R26-MODULE-DEEPENING.md) ·
[Build/laptop instructions](BUILD.md) · [Fresh sources](research/r26-primary.md).

## Completion by the requested testable-subset standard

**50/50 included modules meet the bounded testability standard: 100.00%. 0/50 not
established: 0.00%. Fully specification-accepted: 0/50 (0%). Unfinished at full scope:
50/50 (100%).** No module was created or split to move the denominator.

**R26 deepened 6/50 modules (12.00%)** with 10 new host-executed kernels:
`:core:designsystem` (1), `:core:security` (4), `:feature:media` (1),
`:feature:maps` (1), `:feature:browser` (1), `:format:native` (2).
Per-module pairings and retained limits: [MODULE-TESTABILITY](MODULE-TESTABILITY.md)
(sha-bound), [deepening table](R26-MODULE-DEEPENING.md).

## Actually executed this turn (host)

| Gate | Result |
|---|---|
| `run_r26_contracts` — 6 kotlinc module compiles + JVM consumer on real fixtures | **157/157 assertions PASS**, 40.899s, 20 files sha-bound (`testing/build/r26/result.json`) |
| `check_r26_beauty_reference` — independent WCAG recomputation from shipped constants | PASS: 3 palettes × 9 pairs all ≥ 4.5, floor 0.8316 ≥ 0.80, phi gaps {64:15, 72:16, 88:16} |
| `check_r26_layout_bounds` — 48dp/inside-viewport schema + detectors | PASS on fixed sample (1 small-target + 1 clipped-target detected, 2 clean) |
| `check_r26_android_syntax` — kotlinc parse of 4 authored Android files | PARSE OK × 4; 135/118/40/49 resolution-only diagnostics, **0 syntax errors** |
| `test_r26_tools` — pure-Python interop/hygiene | 9/9 PASS |
| Full `tools/test_*.py` suite incl. census/audit/requirements | **196/196 PASS** |
| `check_project.py` (register, audit, catalog, wrapper, manifests, privacy contracts, censuses) | PASS |

These are host/structural results. They are not Android compilation, device execution,
service integration, anonymity, hardware, or acceptance evidence.

## First native receipt (compile-level only)

At **8b2289d**, [Android native / 39545667085](https://github.com/jyvplay/OSB/actions/runs/39545667085)
job "Traceability, assemble both flavors, JVM tests" completed **success**: both app
flavors assembled (all four authored surfaces compiled, Media3 1.11.0 dependency
closure resolved, manifest merges accepted) and JVM tests passed. This is a compile +
JVM signal only — no device, playback, map, Tor, or dock behavior is established by
it.

## Native comparison vs base (no R26 regression)

Sibling workflows at 8b2289d vs the same workflows on base `main` (39195171xxx,
without R26 changes). Log blobs were unreachable from this sandbox, so the comparison
is workflow-level conclusion + duration signature, not log diagnosis:

| Workflow | Base main | R26 8b2289d | Reading |
|---|---|---|---|
| R25 WebView capability boundary | success | success | unchanged green |
| R25 local video decoding | success | success | unchanged green |
| R21 boundary checks | success | success | unchanged green |
| R23 memory + R22 boundary | success | success | unchanged green |
| R25 PNG metadata copy | **failure** (7m19s) | **failure** (7m46s) | pre-existing (matches recorded R25 picker failures) |
| R25 UI preconditions | **failure** (7m34s) | **failure** (6m32s) | pre-existing on base |
| R24 continuation desktop/media/atlas | **failure** (1m43s) | **failure** (1m50s) | pre-existing fast failure on base |
| Android native (full) | **failure** (23m31s) | build/JVM **success**, device checks running | partial so far; full verdict pending |
| R24 appearance/input/PDF, API37 privacy | success | running | verdict pending |

No workflow that was green on base turned red with R26. The three failures reproduce
without R26 changes; per the contract they are retained as open investigations, not
re-diagnosed or claimed here. A docs-only follow-up (ad80750) re-queued CI; its
receipts will supersede these when collected.

## Authored for Studio/CI, NOT executed here

| Surface | Scope | Pending gates |
|---|---|---|
| `BeautyDock` (:app) — drop-in dock from the verified dock-plan kernel | Same ShelfApp list/magnification/48dp targets; phi gaps; ≤8 preferred slots + overflow label | CI compile; swap DesktopShelf call-site; disposable-emulator suite; real bounds export |
| `StreamPlayerActivity` (:feature:media) — Media3 1.11.0 direct-URL/local player | Local picks + direct .mp3/.mp4/.m4a/.ogg/.wav/.m3u8/.mpd only; no service extraction | Media3 dependency closure in CI; compile; emulator playback incl. HLS/DASH fixtures |
| `VectorAtlasBridge` (:feature:maps) — presence-gated MapsForge probe + panel | Reflection only; zero new deps; disclosed absence | CI compile; emulator probe on builds with/without MapsForge |
| `OrbotHandoff` (:feature:browser) — Orbot presence check + consent handoff | PackageManager check + launch intent + disclosure; manifest `<queries>` added | CI manifest merge; emulator check; real Orbot bootstrap observed separately |

New catalog entries: 4 Apache-2.0 Media3 1.11.0 artifacts (exoplayer, hls, dash, ui).
No other APK dependency added. APK size/footprint unmeasured here.

## Retained R26 repair cycles (no trust inherited)

1. kotlinc rejected nullable `durationSeconds` smart-cast across `||` in PlaylistParser
   Entry init → restructured guard; full pipeline re-run green.
2. JVM check demanded "forensic" in the wipe receipt; production text only implied it →
   disclosure strengthened to disclaim forensic erasure explicitly; re-run green.
3. USB stateful-rule check string carried a `)` instead of `}` → production correctly
   refused; check fixed AND a dedicated unbalanced-brace refusal check added; green.
4. First syntax-gate loop ran without JAVA_HOME and silently proved nothing → rerun with
   the pinned JRE, every diagnostic classified; gate script now sets up its own env.
5. Python palette regex also matched the data-class declaration (4 vs 3) → anchored to
   hex constructors; green.

## Tools actually used

Python 3.11.2, kotlinc-jvm 2.2.21 + JRE 17.0.9 (disposable host cache, reinstall
verified), git, fresh web retrieval. No Android SDK, full JDK, Studio GUI run,
emulator/device, service entitlement, Tor deployment, engine load, theorem prover,
independent agent, or physical measurement was used or is claimed.

## Exact remaining gap + next highest-information tests

1. Push this branch and collect the exact CI receipts: both-flavor builds, lint, JVM/
   contract jobs, and the touched device suites — the authored surfaces have zero
   compile evidence until then.
2. Run the disposable-emulator suites for media/maps/browser/app regressions; record
   named pass/fail, never a re-rolled green.
3. Export real Compose layout bounds from the emulator and run them through
   `check_r26_layout_bounds.py`; do the same for BeautyDock after wiring.
4. Measure the Media3 APK delta on device; revisit the footprint note with numbers.
5. Keep every H− boundary: no Tails equivalence, no anonymity badge, no logger
   detection, no service-streaming claim, no A-grade label.
