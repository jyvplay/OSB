# R35 lab results

Working branch `arena/350b91e4-osb`. Contract: [R35-PLAN](R35-PLAN.md).

Round context: the sandbox was re-provisioned between R34 and R35. Recovery
was verified, not assumed: origin re-fetched at `0ab824b`, branch
re-pointed, worktree byte-identical (empty `git status`), toolchain
re-bootstrapped to identical versions (kotlinc-jvm 2.2.21, JRE 17.0.9),
and R34 contracts re-passed 166/166 on the fresh toolchain before R35 work
began.

## Host execution (this sandbox, 2026-10-22)

- `python3 tools/run_r35_contracts.py --tools ~/.cache/fusion-host-r23`:
  **R35 PASS: 129 assertions across 10 kernels (pure-logic round, zero
  android imports).** Separate per-kernel kotlinc compiles (10 jars; the
  maps group explicitly carries TileMath + TileCover) + consumer run on
  the real fixture in `tools/fixtures/r35/`. Toolchain: kotlinc-jvm 2.2.21,
  JRE 17.0.9.
- `python3 tools/check_r35_reference.py`: PASS (ev 6 via math.log2, xor 3
  via bytes recompute, words 3 via difflib opcode agreement, dups 2 via
  groupby, snips 5 via independent slicing, id3 100-byte tag walk, tiles 5
  via slippy recomputation, vcard 8-line re-parse, fields 2 via
  comprehension, indent 4 via math.gcd). The reference implementations are
  deliberately distinct from the kernels' algorithms.
- `python3 tools/test_r35_tools.py`: 11/11 PASS (incl. the ID3 fixture
  guard, the difflib/slippy/gcd cross-checks, and the kept
  package-collision guard with `sealed interface` added to the pattern).
- `python3 tools/check_r35_touch_targets.py`: detector self-test PASS; 223
  Button-family sites scanned, 175 static findings across 39 files (delta +0
  vs the R28 baseline — R35 authors no UI; queue is pre-existing).
  Strict: ConsentDialog clean (2 sites). STATIC ONLY — not rendered proof.
- `python3 tools/check_r26_android_syntax.py`: 8/8 PARSE OK, 0 syntax errors
  (resolution-only diagnostics expected without android.jar; R35 adds no
  Android files).
- `python3 tools/check_r35_manifest_scope.py`: PASS — 2 activities, 1
  receiver, 0 services/providers, INTERNET + READ_MEDIA_IMAGES only (static
  XML; merged truth needs AGP).
- Repair log: pre-run hand-trace corrected two snippet pins
  (leading-space middle window, `ta theta` tail) and gave the empty-TEL
  test its missing END:VCARD (all test-only; kernels correct). One
  reference-only SyntaxError (unbalanced `sorted` paren) fixed in Python.
  No kernel repairs — first-run green.

## Authored Android (NONE this turn)

R35 authors no Android components, no manifest entries, no permissions, no
stubs, and no authored-support store. Every R35 production file is a
host-JVM kernel with zero android imports (hygiene-gated). Future wiring
(camera display, escrow custody, diff display, cleanup UI, results list,
tag parsing, pack download, contact sync, pipeline projection, editor
config) is documented as unresolved interfaces in R35-PLAN, never claimed.

## Scope

Host JVM + independent Python recomputation + static scans only. NOT Android,
device, service, anonymity, or acceptance evidence. Census: subset 50/50
(100%), R35-deepened 10/50 (20%), cumulative distinct R26–R35 45/50 (90%,
unchanged — all seconds), full 0/50.

## Visual inspection (honest substitute, no emulator in sandbox)

No emulator/SDK exists here, so no screenshot was taken and none is claimed.
What holds instead: (1) R35 authors no UI, so the static touch-target audit
is unchanged (223 sites, +0 delta); (2) Id3Head and TileCover are
format-adjacent MATH only (headers/coverage) with no decode/render/fetch
claim, and SnippetCut renders text windows, not pixels; (3) layout-bounds +
page-matrix self-tests (detectors verified; page coverage still honestly
0.0 — no device exports). CI device frames exist as artifacts but blob
download is blocked in this sandbox, so no CI frame was visually inspected
either. All limits stated, not hidden.

## CI ledger (exact candidates, nothing inherited)

5 workflows triggered on the R35 commit (path-filtered set differs this
round: native, atlas, R25 local video, R24 privacy experiment, offline
privacy companion — no R23/R22, no R25 PNG job). Triage is by annotations
API (logs unavailable; claim only what annotations show).

| Candidate | Workflow / run | Outcome | Cause |
|---|---|---|---|
| 5c8e82b | R25 local video 39843176017 | SUCCESS | — |
| 5c8e82b | R24 privacy experiment 39843175940 | SUCCESS | — |
| 5c8e82b | offline privacy companion 39843176112 | SUCCESS | — |
| 5c8e82b | native 39843175934: "Traceability, assemble both flavors, JVM tests" | SUCCESS | **Compile receipt**: all 10 R35 kernels assemble both flavors; JVM tests pass. |
| 5c8e82b | native 39843175934 device job | FAIL | Noisy lab run: 8 "Failure detail" notices vs 2 on `7dc6dc3`. The 2 pre-existing PNG cases plus 6 flake-cluster cases: 2 R24TaskInput IME native-timeouts (`oneActualImeVisibleAddInArt/Business…WritesExactlyOneRevision`, "R24 native timeout: actual Find dialog"), the IME watch case recurring (`oneNativeImeVisible…`, now 2 fails in 4 observations — fail/pass/fail/pass pattern over device-identical candidates), an asleep-AVD prep failure, 2 two-hundred-percent-font IME/smoke cases, and 1 MP4-decoder case that PASSED in the dedicated R25-video workflow on the same commit (same test, different result across jobs = flakiness proof). R24 audio-focus absent (now 1 in 7). |
| 5c8e82b | atlas 39843176034 | FAIL | Pre-existing infra: root job exits 127 at the install stage, Servo 0.7.0 AAR inspection succeeds, frame jobs fail downstream. |

Net: all R35 code compiles both flavors with green JVM tests. The device
job's extra failures are lab noise, not regressions: R35 authors no
Android code and touches no device-covered path (every failing test is a
dev.fusion.shell device test over R24/R25-era app code, unchanged since
long before R35); the timeout/AVD-prep shapes, the font-scale sensitivity,
and the cross-job MP4 flip all point at emulator flakiness. No failure
names an R35 file. Zero confirmed R35 regressions. The `0ab824b` (R34
ledger) observation was folded into R34-RESULTS as an amendment inside the
R35 commit — 2 pre-existing PNG cases, atlas 127, R34 closure stands. Runs
on the ledger commit itself will be observed per precedent and folded next
round.

## Lab receipt

R35 host lab PASS (openjdk version "17.0.9" 2023-10-17 / kotlinc-jvm 2.2.21 (JRE 17.0.9+9)).
Harness: tools/r35_lab.py host --tools /home/user/.cache/fusion-host-r23
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
- python test_record_sdk_platform: PASS 2/2
- python test_release_gate: PASS 5/5
- python test_requirements: PASS 5/5
Hashes (sha256, fixtures+consumers):
  d46bc271ffca55b0b57f4f15711f06668adcf2758a4719ff772f6bd301731870  tools/fixtures/r35/id3.bin
  a00cf57870825b012f97bd0845930fef580e9426b4f46241942a05e9fb1119c9  testing/src/r35/kotlin/dev/fusion/testing/R35Checks.kt
