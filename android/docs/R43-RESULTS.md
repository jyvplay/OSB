# R43 lab results

Round shape: THIRD consecutive sandbox reset, recovered by the
standing protocol (fetch → hard reset to `24753ef` → byte-clean →
toolchain reinstall → R42 362/362 baseline green BEFORE any edit).
Twenty-three new executed kernels across twenty-three modules — the
widest module spread of any round.

## Host execution (this sandbox, 2026-10-24)
- `python3 tools/run_r43_contracts.py` → **R43 PASS: 372
  production-kernel assertions across 23 kernels** (23 compile groups;
  ooxml/model/find/vault groups carry declared intra-module deps).
  372 > 362; 23 > 22 kernels; 23 > 21 modules.
- `python3 tools/check_r43_reference.py` → PASS, 23 handwritten
  mirrors (downgrade stated in-file).
- `python3 -m unittest tools.test_r43_tools` → 38/38 (R42: 36).
- Static gates: touch delta +0; manifest scope unchanged.
- Census: deepen ×3 byte-identical; `--check` green. 50/50 testable
  (100%), **23/50 deepened (46%)**, 45/50 distinct (90%), 0/50
  accepted (0%). Inventory regenerated before the lab.

## Repairs (PLAN §I in full)
ONE genuine kernel hole found by first execution: TrackerTier's URL
regex ended in `.*`, accepting embedded whitespace — repaired to
`\S*`, the attack kept, contracts re-run from zero. Three wrong test
vectors fixed where the kernels were right (rows-only pane class,
snippet tie-start, Apdex 0.75), each message now carrying the
corrected reasoning. One consumer compile fix; one mirror tautology
replaced before first run.

## The CI bridge
`r43Contracts` joins the JVM lane. Once green: R37–R43, **137 kernels
/ 2399 assertions** under the real Gradle toolchain per push.
Authored-not-verified until the lane runs; the ledger records it.

## Pinned facts (spot list)
- The dangerous RP-ID case is executed POSITIVELY: "example.org"
  matches rpId "org" under the dot rule — exactly why pslChecked=false
  rides in the result type.
- Frozen panes classify the whole truth table incl. the empty-left
  region of a rows-only freeze; the cut twips formula stays cut.
- Referrer Policy: full 8-token table incl. http→https NOT being a
  downgrade.
- QR 17+4v bijection executed 1..40; Apdex 4T-inclusive edge executed;
  caret's three zero-tiers executed against the in-repo SemVer.
- 51×1-groups age payload pads clean to 32 bytes; bech32m variant for
  age refused.

## Authored Android (bridge files only)
All 23 kernels are new pure-logic files (plus the one-line TrackerTier
repair inside an R43 file); zero manifest/UI/service changes.
`testing/build.gradle.kts` + `android.yml` register
`:testing:r43Contracts` (also runnable from Android Studio Rabbit 1's
Gradle panel).

## Scope
Host JVM + host Python + static analysis + CI annotations. No device,
emulator, rendering, live-network or wild-corpus evidence. 0/50
accepted; nothing here is A-grade and no such claim is made
(R43-ALTERNATIVES answers the Class-A question directly).

## Visual inspection (honest substitute)
Beauty/contrast reference green in the lab battery; layout-bounds and
page-matrix self-tests green; touch delta +0. Rendered review remains
a named downgrade.

## CI ledger (exact candidates, nothing inherited)
Filled after the round push. Of special interest: first run of
r43Contracts. Standing watches carried in: 2 PNG picker signatures
(every obs), atlas no-tests lane (every obs), IME family 3/14, Xlsx
2/7, screen-off 1/6, R25 MP4 1/5, audio-focus 1/10, R17 focus-repro
intermittent (2 recent passes), NotesProcessDeath 1/2.

## Lab receipt

R43 host lab PASS (openjdk version "17.0.9" 2023-10-17 / kotlinc-jvm 2.2.21 (JRE 17.0.9+9)).
Harness: tools/r43_lab.py host
- host contracts: PASS
- reference recomputation: PASS
- R42 contracts (regression): PASS
- R42 reference (regression): PASS
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
- python test_r43_tools: PASS 38/38
- python test_record_sdk_platform: PASS 2/2
- python test_release_gate: PASS 5/5
- python test_requirements: PASS 5/5
Hashes (sha256, fixtures+consumers):
  acd1d6f84e12f7c69f9be2b968486da6b4d5220524e43609afa2cea5d7614015  testing/src/r43/kotlin/dev/fusion/testing/R43Checks.kt
