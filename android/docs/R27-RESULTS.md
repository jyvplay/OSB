# R27 lab results

Working branch `arena/350b91e4-osb`. Contract: [R27-PLAN](R27-PLAN.md).

## Host execution (this sandbox, 2026-10-21)

- `python3 tools/run_r27_contracts.py --tools ~/.cache/fusion-host-r23`:
  **R27 PASS: 106 production-kernel assertions across 10 kernels.**
  Separate per-group kotlinc compiles (8 jars) + consumer run on the 7 real
  fixtures in `tools/fixtures/r27/`. Toolchain: kotlinc-jvm 2.2.21, JRE 17.0.9.
- `python3 tools/check_r27_reference.py`: PASS (podcast 2+1/2, blocklist 6/6
  decisions, cron 2 vectors, M3 15/15, gazetteer 48 places).
- `python3 tools/test_r27_tools.py`: 11/11 PASS.
- `python3 tools/check_r27_pagematrix.py` (self-test): PASS — 24 pages
  tracked, schema + detector verified, honest coverage 0.0 (no device exports).
- `python3 tools/check_r26_android_syntax.py`: 5/5 PARSE OK, 0 syntax errors
  (resolution-only diagnostics expected without android.jar).
- Repair log (check-side, production held): occupancy-cap null-path input,
  inverted block-decision assertion, ragged-table input (delimiter mismatch is
  paragraphs per GFM, not a table), phiSize(16,1) = 26.0 not 25.5. One
  production-adjacent clarification: MarkdownDoc comment wording.

## Authored Android edits (NOT executed)

BeautyDock tag alignment (unwired), StreamPlayerActivity launch contract,
MediaHub stream-player button. Parse-gated only; Studio/CI compile + device
execution required before any behavior claim.

## Scope

Host JVM + independent Python recomputation only. NOT Android, device,
service, anonymity, or acceptance evidence. Census: subset 50/50 (100%),
R27-deepened 8/50 (16%), full 0/50.

## CI repair (same turn, before merge)

Candidate 7ad4d86 failed 10/12 workflows at "Assemble both flavors" with a
real regression: `StreamPlayerActivity.kt:54:90 No value passed for parameter
'prefill'` (check-run annotation). Root cause: two same-file edits issued in
one parallel tool block raced — last writer won and dropped the companion
hunk. The same race dropped hunks in AdaptiveDock.kt (4 of 6 tag-alignment
edits) and r27_lab.py (toolchain-version hunk). All hunks were re-applied
sequentially, verified by grep, and the parse gate re-passed 5/5. Lesson
recorded: never issue two edits to the same file in one parallel block.
Repair candidate follows; no failed receipt is inherited.

## CI ledger (exact candidates, nothing inherited)

| Candidate | Workflow / run | Outcome | Cause |
|---|---|---|---|
| 7ad4d86 | 10 workflows | 10 FAIL (1–3 min) | Real R27 regression: `prefill` compile break from the parallel-edit race (StreamPlayerActivity). Superseded, not inherited. |
| 6a2cd1d (repair 1) | WebView 39550306616, video 39550306565, privacy 39550306656, R21 39550306569, R24-boundaries 39550306570, UI-preconditions 39550306606, R23/R22 39550306555 | 7 SUCCESS | Privacy-companion green resolves the R26 open flake question (8b2289d failure was transient; zero privacy sources touched). |
| 6a2cd1d | atlas 39550306642 (1m10s) | FAIL | Pre-existing infra: exit 127, "No executed device test cases found" — same character as base-main failure recorded in R26 triage (1m43s). |
| 6a2cd1d | PNG 39550306577 (8m17s) | FAIL | Pre-existing device: 2 R25PngCopyDeviceTest cases "did not reach READY (phase=EMPTY)". R27 touches zero PNG code; fails on base main (7m19s). |
| 6a2cd1d | native 39550306568 | FAIL | Traceability step (my stale inventory — fixed in repair 2) + the same 2 pre-existing PNG device cases. |
| 97c1c1d (repair 2) | native 39552739806: "Traceability, assemble both flavors, JVM tests" | SUCCESS | **Compile receipt for all R27 code**: 10 kernels + 3 authored edits assemble on both flavors; JVM tests pass. |
| 97c1c1d | native 39552739806 device job | FAIL | Only the same 2 pre-existing PNG device cases; every other device case passes. |

Net: no R27 regression. Two failures I introduced (prefill break, stale
inventory) were both repaired in-turn with exact-candidate receipts; the
remaining failures (atlas infra, PNG device) reproduce on base main.

## Lab receipt

R27 host lab PASS (kotlinc-jvm 2.2.21, JRE Temurin-17.0.9+9; see host-tools.json versions).
Harness: tools/r27_lab.py host --tools /home/user/.cache/fusion-host-r23
- host contracts: PASS
- reference recomputation: PASS
- android syntax gate: PASS
- layout-bounds self-test: PASS
- page-matrix self-test: PASS
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
- python test_record_sdk_platform: PASS 2/2
- python test_release_gate: PASS 5/5
- python test_requirements: PASS 5/5
Hashes (sha256, fixtures+consumers):
  f7d854f1a5350fa120a8e7357cdc3aa1572055335b2e2e8fedfa11271b015164  tools/fixtures/r27/sample-podcast.xml
  90d2a4b47f1d543bda7c0d10e619a10dac0a2384350ed8e56df538ee253899ef  tools/fixtures/r27/sample-atom.xml
  c9ff19124f32d1069e12ef8f4841d7d14490a05aecfd46b4856b7466e1059a97  tools/fixtures/r27/variant.m3u8
  092427f75c64e7f896cb9b4f183834fff8f0a8f1abed87e324361f117f77486e  tools/fixtures/r27/blocklist.txt
  593a043a1f9cf0ca36d9e0958d69135531ef45b51cdf5face585b1f14c7d7508  tools/fixtures/r27/sample.md
  5fd8f61ea681ae84bd9c3686e499ae8ce836eb1c47ccbb872fa46b805f6a22b7  tools/fixtures/r27/inventory-a.txt
  73a4361b2c7f2751244f348899628ae65b7e399dffecc6e968196fdb20037acc  tools/fixtures/r27/inventory-b.txt
  e98a2e7e5069882d4c7d602befce965caa8523024689585268149f19fa96a408  testing/src/r27/kotlin/dev/fusion/testing/R27Checks.kt
