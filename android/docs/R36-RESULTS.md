# R36 lab results

Working branch `arena/350b91e4-osb`. Contract: [R36-PLAN](R36-PLAN.md).

Round context: the sandbox was re-provisioned between R35 and R36. Recovery
was verified, not assumed: origin re-fetched at `408c39f`, branch
re-pointed (mixed reset; nothing tracked discarded), worktree byte-identical
(empty `git status`), toolchain re-bootstrapped to identical versions
(kotlinc-jvm 2.2.21, JRE 17.0.9), and R35 contracts re-passed 129/129 on the
fresh toolchain before R36 work began.

## Host execution (this sandbox, 2026-10-22)

- `python3 tools/run_r36_contracts.py --tools ~/.cache/fusion-host-r23`:
  **R36 PASS: 176 assertions across 12 kernels (pure-logic round, zero
  android imports).** Separate per-kernel kotlinc compiles (12 jars) +
  consumer run on the real fixtures in `tools/fixtures/r36/`. Toolchain:
  kotlinc-jvm 2.2.21, JRE 17.0.9.
- `python3 tools/check_r36_reference.py`: PASS (rpn 4 via float mirror,
  names 5 via gap-fill loop, wrap 3 via independent walk, redir 2 via
  token walk, cast 11 via dict-table mirror, scale 7 via slippy
  recomputation, wav 44-byte struct re-parse, tiff 16-byte struct
  re-parse, kanon 3 via Counter, window 6 via comprehension counts, dof 4
  via formula mirror, shannon 4 via math.log2). The reference
  implementations are deliberately distinct from the kernels' algorithms.
- `python3 tools/test_r36_tools.py`: 13/13 PASS (incl. the WAV/TIFF
  fixture guards, the slippy/hyperfocal/entropy cross-checks, and the kept
  package-collision guard).
- `python3 tools/check_r36_touch_targets.py`: detector self-test PASS; 223
  Button-family sites scanned, 175 static findings across 39 files (delta +0
  vs the R28 baseline — R36 authors no UI; queue is pre-existing).
  Strict: ConsentDialog clean (2 sites). STATIC ONLY — not rendered proof.
- `python3 tools/check_r26_android_syntax.py`: 8/8 PARSE OK, 0 syntax errors
  (resolution-only diagnostics expected without android.jar; R36 adds no
  Android files).
- `python3 tools/check_r36_manifest_scope.py`: PASS — 2 activities, 1
  receiver, 0 services/providers, INTERNET + READ_MEDIA_IMAGES only (static
  XML; merged truth needs AGP).
- Repair log: pre-run review rebuilt the stereo WAV bytes with explicit LE
  helpers and re-derived the b3 pin for largest-fitting semantics (both
  test-only; kernels correct). Five post-failure test-side repairs, zero
  kernel repairs: numbered-input expectation corrected to base reuse (the
  kernel strips ` (N)`), b4/b5 pins corrected (arithmetic slip + fit
  semantics), the hyperfocal pin restored to the +f form after a mid-stream
  misread, the Python reference mirror aligned (fit loop, +f, suffix
  strip), and one lost update re-applied after parallel same-file edits
  raced.

## Authored Android (NONE this turn)

R36 authors no Android components, no manifest entries, no permissions, no
stubs, and no authored-support store. Every R36 production file is a
host-JVM kernel with zero android imports (hygiene-gated). Future wiring
(calc UI, files copy flow, editor display, shell runner, Cast transport,
map overlay, sample decode, IFD walk, export gating, auth wiring, lens UI,
strength meter) is documented as unresolved interfaces in R36-PLAN, never
claimed.

## Scope

Host JVM + independent Python recomputation + static scans only. NOT Android,
device, service, anonymity, or acceptance evidence. Census: subset 50/50
(100%), R36-deepened 12/50 (24%), cumulative distinct R26–R36 45/50 (90%,
unchanged — all seconds), full 0/50.

## Visual inspection (honest substitute, no emulator in sandbox)

No emulator/SDK exists here, so no screenshot was taken and none is claimed.
What holds instead: (1) R36 authors no UI, so the static touch-target audit
is unchanged (223 sites, +0 delta); (2) WavHead, TiffHead, ScaleBar, and
DofNear are format-adjacent MATH only (headers/ground-resolution/focus
limits) with no decode/render/fetch claim; (3) layout-bounds +
page-matrix self-tests (detectors verified; page coverage still honestly
0.0 — no device exports). CI device frames exist as artifacts but blob
download is blocked in this sandbox, so no CI frame was visually inspected
either. All limits stated, not hidden.

## CI ledger (exact candidates, nothing inherited)

Pending: filled after CI runs on the R36 commit, per precedent. The
R24TaskInput IME case (2 fails in 5 observations) stays the watch item;
R24 audio-focus (1 in 8) keeps resolving toward noise.

## Lab receipt

R36 host lab PASS (openjdk version "17.0.9" 2023-10-17 / kotlinc-jvm 2.2.21 (JRE 17.0.9+9)).
Harness: tools/r36_lab.py host --tools /home/user/.cache/fusion-host-r23
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
- python test_r32_tools: PASS 12/12
- python test_r33_tools: PASS 13/13
- python test_r34_tools: PASS 12/12
- python test_r35_tools: PASS 11/11
- python test_r36_tools: PASS 13/13
- python test_record_sdk_platform: PASS 2/2
- python test_release_gate: PASS 5/5
- python test_requirements: PASS 5/5
Hashes (sha256, fixtures+consumers):
  4f8734c5e13ac599e168cf247a51c1dd0758537ce00bf16d7fed1a3d14d07041  tools/fixtures/r36/wav.bin
  b44eca9fba2125c9c0ad0ff4b24870a4134db745600e8365953a15ea9ae0d9df  tools/fixtures/r36/tiff.bin
  5878b667de680a14d76503a6947a064b4b92d3410086f357c099bee19134885e  testing/src/r36/kotlin/dev/fusion/testing/R36Checks.kt
