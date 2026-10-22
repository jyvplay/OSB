# R34 lab results

Working branch `arena/350b91e4-osb`. Contract: [R34-PLAN](R34-PLAN.md).

## Host execution (this sandbox, 2026-10-22)

- `python3 tools/run_r34_contracts.py --tools ~/.cache/fusion-host-r23`:
  **R34 PASS: 166 assertions across 10 kernels (pure-logic round, zero
  android imports).** Separate per-kernel kotlinc compiles (10 jars, one
  kernel each) + consumer run on the 2 real fixtures in
  `tools/fixtures/r34/`. Toolchain: kotlinc-jvm 2.2.21, JRE 17.0.9.
- `python3 tools/check_r34_reference.py`: PASS (stats 4 via the statistics
  module, mime 16 via stdlib mimetypes, outline 4 via regex re-scan,
  brackets 4 via a stack scan, soap 4 via ElementTree, pipes 10 via
  split-then-repair, jpeg 5 segments at 1×1, zlib 31-byte stdlib
  roundtrip, vtodo 6, pseudonym 2 via JVM=hmac interop with crash token
  `c6e8480c4fc241780116719dd6cb3fa0`). The reference implementations are
  deliberately distinct from the kernels' algorithms; the
  tar.gz convention split (stdlib layers type+encoding, kernel returns the
  last-extension label) is documented, not hidden.
- `python3 tools/test_r34_tools.py`: 12/12 PASS (incl. the JPEG/zlib
  fixture guards, the statistics cross-check, and the kept
  package-collision guard).
- `python3 tools/check_r34_touch_targets.py`: detector self-test PASS; 223
  Button-family sites scanned, 175 static findings across 39 files (delta +0
  vs the R28 baseline — R34 authors no UI; queue is pre-existing).
  Strict: ConsentDialog clean (2 sites). STATIC ONLY — not rendered proof.
- `python3 tools/check_r26_android_syntax.py`: 8/8 PARSE OK, 0 syntax errors
  (resolution-only diagnostics expected without android.jar; R34 adds no
  Android files).
- `python3 tools/check_r34_manifest_scope.py`: PASS — 2 activities, 1
  receiver, 0 services/providers, INTERNET + READ_MEDIA_IMAGES only (static
  XML; merged truth needs AGP).
- Repair log: (1) Pre-run review caught SoapFault's prefixed-close search
  stopping at `</faultcode>` -> regex open/close pair. (2) JpegSeg returned
  an empty walk for bare SOI -> done-flag refusal + dedicated truncation
  attack. (3) Parallel same-file edits proved lossy a fourth time (only
  last-per-file survived) -> re-applied atomically in a single call with
  grep verification. (4) Consumer-only slips (outline tab test, bracket
  interleave index) fixed in tests; kernels correct. (5) Reference-only
  slips (mimetypes wav alias + tar.gz encoding layer, ElementTree
  namespace-XPath quirk) fixed in Python.

## Authored Android (NONE this turn)

R34 authors no Android components, no manifest entries, no permissions, no
stubs, and no authored-support store. Every R34 production file is a
host-JVM kernel with zero android imports (hygiene-gated). Future wiring
(stats display, share sheets, notes outline, editor highlight, renderer
errors, shell runner, image/archive decode, task sync, analytics tokens)
is documented as unresolved interfaces in R34-PLAN, never claimed.

## Scope

Host JVM + independent Python recomputation + static scans only. NOT Android,
device, service, anonymity, or acceptance evidence. Census: subset 50/50
(100%), R34-deepened 10/50 (20%), cumulative distinct R26–R34 45/50 (90%,
unchanged — all seconds), full 0/50.

## Visual inspection (honest substitute, no emulator in sandbox)

No emulator/SDK exists here, so no screenshot was taken and none is claimed.
What holds instead: (1) R34 authors no UI, so the static touch-target audit
is unchanged (223 sites, +0 delta); (2) JpegSeg and ZlibHead are
format-adjacent MATH only (markers/headers) with no decode/render claim, and
OutlineParse renders text structure, not pixels; (3) layout-bounds +
page-matrix self-tests (detectors verified; page coverage still honestly
0.0 — no device exports). CI device frames exist as artifacts but blob
download is blocked in this sandbox, so no CI frame was visually inspected
either. All limits stated, not hidden.

## CI ledger (exact candidates, nothing inherited)

Pending: filled after CI runs on the R34 commit, per precedent. The R24
audio-focus case (1 fail in 4 observations on R33 candidates) and the
first-seen R24TaskInput IME case on `2aea522` are both watch items this
round.

## Lab receipt

R34 host lab PASS (openjdk version "17.0.9" 2023-10-17 / kotlinc-jvm 2.2.21 (JRE 17.0.9+9)).
Harness: tools/r34_lab.py host --tools /home/user/.cache/fusion-host-r23
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
- python test_record_sdk_platform: PASS 2/2
- python test_release_gate: PASS 5/5
- python test_requirements: PASS 5/5
Hashes (sha256, fixtures+consumers):
  4cce53a807d173485a27dcdf50bfc097c99dba532e1d2baa446e58b0112dacd0  tools/fixtures/r34/tiny.jpg
  3a9ddf791238cc01346a88ee58965acd51dc3bc14938b4df2dc1a19344e4709b  tools/fixtures/r34/zlib.bin
  82d6e6f372d75bbc7a191b4b3f04aea13ffed48e024e8d39153b8ee420ead743  testing/src/r34/kotlin/dev/fusion/testing/R34Checks.kt
