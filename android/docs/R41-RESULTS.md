# R41 lab results

Round shape: FULL SANDBOX RESET recovery first (fetch → hard reset to
the branch tip → byte-clean at `de1af47` → toolchain reinstall via
`bootstrap_host_tools.py install` → R40 360/360 + R39 344/344 baselines
green BEFORE any edit). Then twenty-one new executed kernels across
twenty modules, and the CI bridge extended BACKWARDS (`r38Contracts`)
and FORWARDS (`r41Contracts`).

## Host execution (this sandbox, 2026-10-24)

- `python3 tools/run_r41_contracts.py` → **R41 PASS: 361
  production-kernel assertions across 21 kernels.** 20 compile groups
  (the ooxml and pdf groups carry their declared intra-module
  dependencies — ColLetters, CropBox, PdfRotate — nothing else), one
  JVM run. 361 > R40's 360; 21 kernels > 20; 20 modules > 19.
- `python3 tools/check_r41_reference.py` → PASS, 21 independent
  mirrors; five held-out stdlib families (shlex, unicodedata,
  timedelta, base64/urllib, str.expandtabs).
- `python3 -m unittest tools.test_r41_tools` → 33/33 (unittest-native
  from birth — the R40 harness lesson applied at authoring time).
- Static gates: touch delta +0; manifest scope unchanged.
- Census: deepen ×2 byte-identical; `--check` green. 50/50 testable
  (100%), **20/50 deepened this round (40%)**, 45/50 distinct (90%),
  0/50 accepted (0%). Source inventory regenerated before the lab.

## Zero kernel repairs this round

All 21 kernels passed their full contracts on first execution — the
first zero-repair round since R38. Pre-execution tightenings (not
repairs) are logged in R41-PLAN §I; the tautology gate grew to catch
`!= None`.

## The CI bridge (status at commit)

`r38Contracts` (backport) and `r41Contracts` joined the JVM lane. Once
green, R38–R41's 77 kernels / 1391 assertions all execute under the
real Gradle toolchain in CI. Authored-not-verified until the lane runs;
the ledger below records what CI did.

## Pinned facts produced this round (spot list)

- Addressing layer complete: whole-grid count 16384×1048576 executed;
  `B2:A1` normalizes; R1C1 `R[2]C[-1]` resolves and inverts; no
  wrapping at any edge.
- shlex agreement including the `''` token (`''"'"''"'"''`), `$HOME`,
  and join vectors.
- Width pairs executed: ｱ=1 vs ア=2; Ａ=2 vs A=1; é-combining = 1;
  CJK Ext-B astral = 2 — each agreeing with unicodedata.
- DMS carry: -89.9999999 → `90°00′00″S`, never 60″.
- P1M refuses toSeconds (months have no fixed length); PT90M = 5400s.
- EWMA: corrected estimate equals a constant input at EVERY step;
  uncorrected bias equals (1-α)^t exactly (α=0.3, t=1..20 executed).
- EAN/UPC/ISBN-13 share one executed algebra (three published-family
  vectors); sizes-sum-to-total piece property over all 62 pieces of
  the 1MB plan.

## Authored Android (bridge files only)

All 21 kernels are new pure-logic files; zero manifest/UI/service
changes. `testing/build.gradle.kts` + `.github/workflows/android.yml`
extend the contract-task bridge (also runnable from Android Studio's
Gradle panel: `:testing:r38Contracts` … `:testing:r41Contracts`).

## Scope

Host JVM + host Python + static analysis + CI annotations. No device,
emulator, rendering, live-network or wild-corpus evidence. 0/50
accepted; nothing here is A-grade and no such claim is made (the
Class-A question is answered in R41-ALTERNATIVES).

## Visual inspection (honest substitute, no renderer in sandbox)

Beauty/contrast reference green in the lab battery; layout-bounds and
page-matrix self-tests green; touch delta +0. LineWidth/TabStops output
is verified column-exact against held-out verifiers — rendered review
remains a named downgrade.

## CI ledger (exact candidates, nothing inherited)

Round commit `d27fdc2dcd965f820c60e7723c4b59cb84dcebfb`; all 8
triggered workflows triaged by exact run id via the annotations API.
**Zero R41 regressions in any JVM lane — and the bridge is VERIFIED
end-to-end: the JVM job running `:testing:r38Contracts` through
`:testing:r41Contracts` succeeded, so R38–R41's 77 kernels / 1391
assertions now execute under the real Gradle toolchain in CI.**

| Run id | Workflow | Conclusion | Triage |
|---|---|---|---|
| 40120890152 | Android native | failure | **JVM job SUCCESS — traceability + both flavors + JVM tests + r38/r39/r40/r41 contract tasks (1391 assertions under Gradle)**; device job: 2 standing `R25PngCopyDeviceTest` signatures + ONE NEW case, `NotesProcessDeathDeviceTest.acknowledgedDraftCheckpointSurvivesActualWriterProcessDeath` (first observation; R41 touches no notes/writer device surface — new watch at 1/1, no code suspect) |
| 40120890073 | R25 actual PNG metadata copy | failure | same 2 standing PNG signatures |
| 40120890262 | R24 continuation desktop media atlas | failure | standing no-tests install lane |
| 40120890092 | Android offline privacy companion | failure | `PrivacyWorkspaceTest` IME-discard case — the IME watch family moves to 3 fails / 13 observations |
| 40120890116 | Tasks focused diagnostic | **success** | notable: the R17 focus-repro lane PASSED for the first time in its observation history — watch flipped to intermittent-repro |
| 40120890077 | R21 boundary checks | success | — |
| 40120890076 | R23 memory and R22 boundary checks | success | Xlsx watch improves to 2/6 |
| 40120890085 | R24 appearance, input and PDF boundaries | success | — |

Not triggered (path filters): R25 media/web lanes, API37 experiment.

Standing watch table after this round: 2 PNG picker signatures (every
obs), atlas no-tests lane (every obs), IME family 3/13 (worst mover),
Xlsx 2/6, screen-off 1/5 (lane failed via the IME case, counted there),
R25 MP4 1/4 (not triggered), audio-focus 1/10, R17 focus repro now
INTERMITTENT (passed once), NEW NotesProcessDeath 1/1. `rerun-failed-
jobs` remains 403; rates accumulate across observations.

## Lab receipt

R41 host lab PASS (openjdk version "17.0.9" 2023-10-17 / kotlinc-jvm 2.2.21 (JRE 17.0.9+9)).
Harness: tools/r41_lab.py host
- host contracts: PASS
- reference recomputation: PASS
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
- python test_record_sdk_platform: PASS 2/2
- python test_release_gate: PASS 5/5
- python test_requirements: PASS 5/5
Hashes (sha256, fixtures+consumers):
  58f6dd2620f698400e5e09bded04438b982b5d85aa45f8b6f9141838cff39051  testing/src/r41/kotlin/dev/fusion/testing/R41Checks.kt
