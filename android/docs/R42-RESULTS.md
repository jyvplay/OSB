# R42 lab results

Round shape: SECOND consecutive full sandbox reset, recovered by the
standing protocol (fetch → hard reset to `24224c7` → byte-clean →
toolchain reinstall → R41 361/361 baseline green BEFORE any edit).
Twenty-two new executed kernels across twenty-one modules; the CI
bridge extended to six rounds (r37 backport + r42 task).

## Host execution (this sandbox, 2026-10-24)

- `python3 tools/run_r42_contracts.py` → **R42 PASS: 362
  production-kernel assertions across 22 kernels** (21 compile groups;
  the ooxml group carries its declared intra-module deps). 362 > 361;
  22 > 21 kernels; 21 > 20 modules.
- `python3 tools/check_r42_reference.py` → PASS, 22 mirrors —
  HANDWRITTEN recomputations this round (no new held-out stdlib family
  fit these surfaces; the mirror header declares the downgrade), with
  the Damm table re-entered independently on both sides.
- `python3 -m unittest tools.test_r42_tools` → 36/36 (R41: 33).
- Static gates: touch delta +0; manifest scope unchanged.
- Census: deepen idempotent; `--check` green. 50/50 testable (100%),
  **21/50 deepened (42%)**, 45/50 distinct (90%), 0/50 accepted (0%).
  Inventory regenerated before the lab.

## Repairs

One PRE-EXECUTION purity repair (Rfc2231 mutable charset state —
caught in self-review before first compile, refactored, guarded by a
new tool-test grep) and one wrong test vector (NiceStep — kernel
right). Zero kernel repairs post-execution; second consecutive round
where every kernel passed its full contract on first run.

## Lab-first-run disclosure

The first lab run FAILED on the touch/manifest gates — not a code
problem but a harness-clone omission (the r42 lab was sed-cloned to
call `check_r42_*` gates that had not been created yet). Both gates
were cloned, run green standalone, the FAIL receipt stripped, and the
lab re-run from zero. Recorded because a receipt that silently
swallowed missing gates would be the exact dishonesty this process
exists to prevent.

## The CI bridge (status at commit)

`r37Contracts` (backport) + `r42Contracts` joined the JVM lane. Once
green: R37–R42, **114 kernels / 2027 assertions** under the real
Gradle toolchain per push. Authored-not-verified until the lane runs;
the ledger records what CI did.

## Pinned facts (spot list)

- ISO 32000-1's own date example parses field-exact and round-trips.
- Damm: BOTH published guarantees executed as sweeps (60 single
  mutations + all adjacent swaps on `3141594`), table re-entered
  independently in Kotlin and Python.
- HSTS: unknown directives TOLERATED (spec-right), duplicates refused.
- $A$1 never moves under (9,9) fill; $B2 pins its column only.
- NMEA 4807.038,N ⇄ 48.1173 both directions.
- Histo conservation holds under a 1000-sample reorder.
- `Sec-GPC: 0` refuses — consent is never coerced in either direction.

## Authored Android (bridge files only)

All 22 kernels are new pure-logic files; zero manifest/UI/service
changes. `testing/build.gradle.kts` + `android.yml` extend the
contract-task bridge (`:testing:r37Contracts` … `:testing:r42Contracts`
also run from Android Studio Rabbit 1's Gradle panel).

## Scope

Host JVM + host Python + static analysis + CI annotations. No device,
emulator, rendering, live-network or wild-corpus evidence. 0/50
accepted; nothing here is A-grade and no such claim is made
(R42-ALTERNATIVES answers the Class-A question directly).

## Visual inspection (honest substitute)

Beauty/contrast reference green in the lab battery; layout-bounds and
page-matrix self-tests green; touch delta +0. Rendered review remains
a named downgrade.

## CI ledger (exact candidates, nothing inherited)

Round commit `948f47d5389dae812e8f607faf2102a513018f24`; all 10
triggered workflows triaged by exact run id. **Zero R42 regressions in
any JVM lane — the six-round bridge is VERIFIED: the JVM job running
`:testing:r37Contracts` through `:testing:r42Contracts` succeeded, so
R37–R42's 114 kernels / 2027 assertions executed under the real Gradle
toolchain in CI.**

| Run id | Workflow | Conclusion | Triage |
|---|---|---|---|
| 40134781529 | Android native | failure | **JVM job SUCCESS — traceability + flavors + JVM tests + ALL SIX contract tasks**; device job: exactly the 2 standing `R25PngCopyDeviceTest` signatures (NotesProcessDeath did NOT recur — watch stays 1/2) |
| 40134781577 | R25 actual PNG metadata copy | failure | same 2 standing PNG signatures |
| 40134781468 | R24 desktop media atlas | failure | standing no-tests install lane |
| 40134781463 | Tasks focused diagnostic | success | second consecutive pass — focus-repro watch now 2 passes after its all-fail history |
| 40134781490 | R25 local video | success | MP4 watch → 1/5 |
| 40134781480 | R21 | success | — |
| 40134781493 | R24 API37 privacy | success | IME → 3/14 |
| 40134781482 | offline privacy companion | success | companion recovers; IME-family discard case did not recur |
| 40134781467 | R24 appearance/input/PDF | success | — |
| 40134781494 | R23/R22 | success | Xlsx → 2/7 |

Standing watches after this round: 2 PNG picker signatures (every
obs), atlas no-tests lane (every obs), IME family 3/14, Xlsx 2/7,
screen-off 1/6, R25 MP4 1/5, audio-focus 1/10, R17 focus-repro
intermittent (2 recent passes), NotesProcessDeath 1/2. `rerun-failed-
jobs` remains 403; rates accumulate across observations.


## Lab receipt

R42 host lab PASS (openjdk version "17.0.9" 2023-10-17 / kotlinc-jvm 2.2.21 (JRE 17.0.9+9)).
Harness: tools/r42_lab.py host
- host contracts: PASS
- reference recomputation: PASS
- R41 contracts (regression): PASS
- R41 reference (regression): PASS
- R40 contracts (regression): PASS
- R40 reference (regression): PASS
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
- python test_r41_tools: PASS 33/33
- python test_r42_tools: PASS 36/36
- python test_record_sdk_platform: PASS 2/2
- python test_release_gate: PASS 5/5
- python test_requirements: PASS 5/5
Hashes (sha256, fixtures+consumers):
  556748d077d7dadf00f67592ff9d34545176418654eaea1bfd8a3500d0d6fc52  testing/src/r42/kotlin/dev/fusion/testing/R42Checks.kt
