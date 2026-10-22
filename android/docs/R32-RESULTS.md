# R32 lab results

Working branch `arena/350b91e4-osb`. Contract: [R32-PLAN](R32-PLAN.md).

## Host execution (this sandbox, 2026-10-22)

- `python3 tools/run_r32_contracts.py --tools ~/.cache/fusion-host-r23`:
  **R32 PASS: 157 assertions across 10 kernels (pure-logic round, zero
  android imports).** Separate per-kernel kotlinc compiles (10 jars) +
  consumer run on the real `sheet.cue` fixture in `tools/fixtures/r32/`.
  Toolchain: kotlinc-jvm 2.2.21, JRE 17.0.9.
- `python3 tools/check_r32_reference.py`: PASS (fraction 8 via stdlib
  limit_denominator, office 11 formats, castmeta 15 via urllib verdicts,
  argsplit 20, pin 12, sweep 2+2, duration 19 via a regex grammar, geo 14,
  lockout 6, cue 3 tracks via a regex grammar). The reference readers are
  deliberately distinct implementations, not ports of the kernels' scans.
- `python3 tools/test_r32_tools.py`: 11/11 PASS (incl. the cue global-tags
  ordering guard and the limit-denominator agreement check).
- `python3 tools/check_r32_touch_targets.py`: detector self-test PASS; 223
  Button-family sites scanned, 175 static findings across 39 files (delta +0
  vs the R28 baseline — R32 authors no UI; queue is pre-existing).
  Strict: ConsentDialog clean (2 sites). STATIC ONLY — not rendered proof.
- `python3 tools/check_r26_android_syntax.py`: 8/8 PARSE OK, 0 syntax errors
  (resolution-only diagnostics expected without android.jar; R32 adds no
  Android files).
- `python3 tools/check_r32_manifest_scope.py`: PASS — 2 activities, 1
  receiver, 0 services/providers, INTERNET + READ_MEDIA_IMAGES only (static
  XML; merged truth needs AGP).
- Repair log: (1) CueSheet object/data-class name clash ->
  CueSheetParser (applied serially with grep verification). (2) semi()
  compared candidates against the CF remainder instead of the target ->
  captured target (caught by the pi-semiconvergent pin; the coarser 22/7
  pin had passed by accident). (3) Reference urlsplit scheme lowering ->
  raw-string case check in the mirror (kernel correct per its grammar).
  (4) Reference CUE tuple-index slip (mirror-only). (5) Tool-test TRACK
  regex missed indented fixture lines (test-only; kernel trims).
  Pre-execution review fixes: FractionApprox overflow ordering (q before
  p) + huge-quotient shortcut, semiconvergent-vs-previous-convergent
  finish, 32min-cap KDoc honesty, CueSheet global TITLE/PERFORMER
  tolerance.

## Authored Android (NONE this turn)

R32 authors no Android components, no manifest entries, no permissions, no
stubs, and no authored-support store. Every R32 production file is a
host-JVM kernel with zero android imports (hygiene-gated). Future wiring
(calc display, Office engines, cast transport, shell registry, credential
storage, deletion callers, scheduler, maps, vault unlock, chapters) is
documented as unresolved interfaces in R32-PLAN, never claimed.

## Scope

Host JVM + independent Python recomputation + static scans only. NOT Android,
device, service, anonymity, or acceptance evidence. Census: subset 50/50
(100%), R32-deepened 10/50 (20%), cumulative distinct R26–R32 45/50 (90%),
full 0/50.

## Visual inspection (honest substitute, no emulator in sandbox)

No emulator/SDK exists here, so no screenshot was taken and none is claimed.
What holds instead: (1) R32 authors no UI, so the static touch-target audit
is unchanged (223 sites, +0 delta); (2) CueSheetParser is media-adjacent
MATH only (cue times to CD frames) with no decode/render claim, and GeoUri
is string math with no positioning claim; (3) layout-bounds + page-matrix
self-tests (detectors verified; page coverage still honestly 0.0 — no
device exports). CI device frames exist as artifacts but blob download is
blocked in this sandbox, so no CI frame was visually inspected either. All
limits stated, not hidden.

## CI ledger (exact candidates, nothing inherited)

Ledger commits observe per precedent: the R32 commit's runs will be triaged
once (annotations API; logs unavailable) and a same-shape ledger amendment
folded next round. The `7d30a44` (R31 ledger) observation was folded into
R31-RESULTS as an amendment inside the R32 commit — identical pre-existing
shapes, R31 closure stands.

## Lab receipt

R32 host lab PASS (openjdk version "17.0.9" 2023-10-17 / kotlinc-jvm 2.2.21 (JRE 17.0.9+9)).
Harness: tools/r32_lab.py host --tools /home/user/.cache/fusion-host-r23
- host contracts: PASS
- reference recomputation: PASS
- android syntax gate: PASS
- layout-bounds self-test: PASS
- page-matrix self-test: PASS
- touch-target static audit: PASS
- manifest-scope gate: PASS
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
- python test_r32_tools: PASS 11/11
- python test_record_sdk_platform: PASS 2/2
- python test_release_gate: PASS 5/5
- python test_requirements: PASS 5/5
Hashes (sha256, fixtures+consumers):
  c4f3370737db40596eb4cae41962097f527e27f78e3697e1a1b5b4bde755cb48  tools/fixtures/r32/sheet.cue
  1b3c4e7d414bbce911854fc3c5c7301910082d4bfb1e1b5b343adf52ace176c7  testing/src/r32/kotlin/dev/fusion/testing/R32Checks.kt
