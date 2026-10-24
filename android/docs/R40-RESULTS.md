# R40 lab results

Round shape: toolchain persisted (no reset); R39 baseline re-run green
(344/344 + 19 mirrors) BEFORE any edit; twenty new executed kernels
across nineteen modules; and the CI-execution bridge — the R39-declared
next-highest-information test — authored and pushed this round.

## Host execution (this sandbox, 2026-10-23)

- `python3 tools/run_r40_contracts.py` → **R40 PASS: 360 production-kernel
  assertions across 20 kernels.** Each kernel compiled ALONE with
  `-include-runtime` (20 jars), consumer against all 20, one JVM run.
  360 > R39's 344; 20 kernels > 19; 19 modules deepened > 16.
- `python3 tools/check_r40_reference.py` → PASS, 20 independent mirrors;
  six held-out stdlib families (html, email.header, zlib.crc32,
  plistlib, ipaddress, urllib.parse).
- `python3 tools/test_r40_tools.py` → 38/38 (R39: 30), including a new
  no-tautology gate over the mirrors and the Gradle/workflow wiring
  checks.
- Static gates: touch targets delta +0 (R40 authors no UI); manifest
  scope unchanged (2 activities, 1 receiver, 0 services/providers).
- Census: deepen ×2 byte-identical; `--check` green. 50/50 testable
  (100%), 19/50 deepened this round (38%), 45/50 distinct (90%),
  0/50 accepted (0%). Source inventory regenerated before the lab.

## In-round repairs (full detail in R40-PLAN §I — four kernel bugs
found by first execution, each patched + attacked + re-run from zero)

Rfc2047 dead-refusal regex; Percentile FP rank drift (p=55 → rank 56);
SnapFraction empty-range validation bypass; OtpauthUri decode-before-
split colon smuggling. Plus two wrong consumer vectors fixed (kernels
were right) and two mirror tautologies deleted with a gate added.

## Receipt-honesty repair (applies to R39 too, disclosed here)

The first R40 lab run exposed that the lab executes tool suites via
`python3 -m unittest`, and the R39/R40 tool-test files were plain
scripts — so the lab recorded them as "PASS 0/0" (zero tests
collected). That means R39's receipt line `test_r39_tools: PASS 0/0`
contributed NOTHING to its 43-suite battery; the 30 checks had executed
only in the manual run (which was genuine and logged). Both files are
now real unittest cases (one test per check — Ran 30 / Ran 38), the
census re-pinned (including a documented cross-round allowance so the
two deepen scripts can both re-pin a joint harness edit), and the lab
re-run from a stripped receipt. R39's round totals stand; its
lab-receipt suite line was hollow — recorded here, not papered over.

## The CI bridge (authored this round; verification recorded below)

`testing/build.gradle.kts` gained `r39Contracts` and `r40Contracts`
JavaExec tasks (source sets over the 39 kernel files + consumers), and
the `android.yml` JVM lane now invokes both. These two files are the
round's ONLY non-kernel, non-doc edits. Until the lane runs, this wiring
is authored-not-verified; the ledger below states what CI did.

## Pinned facts produced this round (spot list)

- ICAO TD3 specimen reproduced digit-for-digit:
  `L898902C36UTO7408122F1204159ZE184226B<<<<<10`.
- 7z StartHeaderCRC gate catches any bit flip in the 20-byte span while
  tolerating the minor-version byte OUTSIDE the span — both executed.
- `&#128;`: kernel refuses (declared C1 narrowing); Python's
  `html.unescape` maps it to `€` — both sides executed and recorded.
- FNV-1a published vectors recomputed independently (32: 0x811C9DC5 /
  0xE40C292C / 0xBF9CF968; 64: foobar 0x85944171F73967E8).
- Bech32: BIP-173 `A12UEL5L` residue 1; tampered `A12UEL5M` fails both
  constants; local vectors `osb1qpzry9x8gf2y2numr` (bech32m) /
  `osb1qpzry9x8gf23krs7p` (bech32) verified by handwritten Python
  polymod AND the Kotlin kernel.
- Snap tiling: 9 widths × 6 column counts sum exactly; thirds of 1000 =
  [333, 333, 334] under the floor rule.
- Excel error literal #N/A parses, `#n/a` refuses; `#SPILL!` refused by
  name.

## Authored Android (NONE this turn beyond the build/workflow bridge)

All 20 kernels are new pure-logic files; zero manifest/UI/service
changes. A laptop Android Studio Rabbit 1 build is behaviorally
identical except the new kernels compile in and two new Gradle contract
tasks exist (`:testing:r39Contracts`, `:testing:r40Contracts` — also
runnable from Studio's Gradle panel on your laptop).

## Scope

Host JVM + host Python + static analysis + CI annotations. No device,
emulator, rendering, live-bus, or wild-corpus evidence. 0/50 accepted;
nothing here is A-grade and no such claim is made (the Class-A question
is answered directly in R40-ALTERNATIVES).

## Visual inspection (honest substitute, no renderer in sandbox)

Beauty/contrast reference green in the lab battery (palette pairs ≥
4.5:1, phi-gap floor ≥ 0.80); layout-bounds and page-matrix self-tests
green; touch delta +0. DashEducate/FootnoteMark outputs are verified
character-exact, not typographically rendered — rendered review remains
a named downgrade.

## CI ledger (exact candidates, nothing inherited)

Round commit `c57a8e39d27a0f3735935b17d0fd75862843be36`; all 7 triggered
workflows triaged by exact run id via the annotations API. **Zero R40
regressions — and the CI bridge is VERIFIED: the JVM job that now runs
`:testing:r39Contracts` + `:testing:r40Contracts` succeeded, so all 39
R39+R40 kernels have executed under the real Gradle toolchain in CI.**

| Run id | Workflow | Conclusion | Triage |
|---|---|---|---|
| 40055219493 | Android native | failure | **JVM job SUCCESS — traceability + both flavors + JVM tests + FIRST RUN of r39Contracts/r40Contracts (344+360 assertions under Gradle)**; managed-device job failed with exactly the 2 standing `R25PngCopyDeviceTest` signatures |
| 40055219377 | R23 memory and R22 boundary checks | failure | standing `R22XlsxUiDeviceTest` picker signature — watch degrades to 2 fails / 5 observations |
| 40055219540 | R24 continuation desktop media atlas | failure | standing install-lane state: "No executed device test cases found", exit-code family unchanged |
| 40055219415 | R21 boundary checks | success | — |
| 40055219392 | Android offline privacy companion | success | screen-off watch → 1/5 |
| 40055219379 | R24 actual API37 privacy experiment | success | IME watch → 2/12 |
| 40055219452 | R24 appearance, input and PDF boundaries | success | triggered this round (gradle/testing paths); green |

Not triggered (path filters, recorded as non-triggers, not missing
evidence): Tasks focused diagnostic, R25 PNG/media/web lanes.

Standing watch table after this round: 2 PNG picker signatures (every
observation), atlas no-tests lane (every observation), Xlsx 2/5 (worst
mover this round), IME 2/12, screen-off 1/5, R25 MP4 1/4 (lane not
triggered), audio-focus 1/10. `rerun-failed-jobs` remains 403; flake
rates accumulate across observations instead.



## Lab receipt

R40 host lab PASS (openjdk version "17.0.9" 2023-10-17 / kotlinc-jvm 2.2.21 (JRE 17.0.9+9)).
Harness: tools/r40_lab.py host
- host contracts: PASS
- reference recomputation: PASS
- R39 contracts (regression): PASS
- R39 reference (regression): PASS
- R38 contracts (regression): PASS
- R38 reference (regression): PASS
- R37 contracts (regression): PASS
- R37 reference (regression): PASS
- R34 contracts (repair regression): PASS
- R34 reference (repair regression): PASS
- android syntax gate: PASS
- layout-bounds self-test: PASS
- page-matrix self-test: PASS
- beauty/contrast reference: PASS
- touch-target static audit: PASS
- manifest-scope gate: PASS
- HID topology parser (fixture): PASS
- project structure: PASS
- python test_bounded_process: PASS 3/3
- python test_ci_annotations: PASS 8/8
- python test_collect_native_evidence: PASS 6/6
- python test_device_results: PASS 6/6
- python test_engine_intake: PASS 2/2
- python test_gradle_distribution: PASS 3/3
- python test_module_audit: PASS 6/6
- python test_module_testability: PASS 7/7
- python test_native_lab: PASS 6/6
- python test_native_source_audit: PASS 13/13
- python test_pilot_reporting: PASS 4/4
- python test_png_capture: PASS 7/7
- python test_probe_android_sdk: PASS 4/4
- python test_r17_evidence: PASS 7/7
- python test_r18_evidence: PASS 8/8
- python test_r19_evidence: PASS 5/5
- python test_r20_evidence: PASS 8/8
- python test_r21_evidence: PASS 9/9
- python test_r22_evidence: PASS 9/9
- python test_r23_evidence: PASS 10/10
- python test_r23_input_tools: PASS 5/5
- python test_r23_lab: PASS 4/4
- python test_r24_evidence: PASS 4/4
- python test_r24_runner: PASS 2/2
- python test_r24c_tools: PASS 21/21
- python test_r25_tools: PASS 8/8
- python test_r26_tools: PASS 9/9
- python test_r27_tools: PASS 11/11
- python test_r28_tools: PASS 10/10
- python test_r29_tools: PASS 10/10
- python test_r30_tools: PASS 10/10
- python test_r31_tools: PASS 9/9
- python test_r32_tools: PASS 12/12
- python test_r33_tools: PASS 13/13
- python test_r34_tools: PASS 12/12
- python test_r35_tools: PASS 11/11
- python test_r36_tools: PASS 13/13
- python test_r37_tools: PASS 19/19
- python test_r38_tools: PASS 24/24
- python test_r39_tools: PASS 30/30
- python test_r40_tools: PASS 38/38
- python test_record_sdk_platform: PASS 2/2
- python test_release_gate: PASS 5/5
- python test_requirements: PASS 5/5
Hashes (sha256, fixtures+consumers):
  da128e29c7fb1b8a0cad6a4fe922530aebe8422f20e4aca1c61ec1c90a3986ac  tools/fixtures/r40/sevenz.bin
  05aff162ffe644d97a4477b9ef0f34f80c7ed46e886126827c6abbd794413d2c  tools/fixtures/r40/bplist.bin
  b787d911e5016f6b3f8f4a0dea850dea3f4048ad095804ac272fad6d860430fb  testing/src/r40/kotlin/dev/fusion/testing/R40Checks.kt
