# R31 lab results

Working branch `arena/350b91e4-osb`. Contract: [R31-PLAN](R31-PLAN.md).

## Host execution (this sandbox, 2026-10-22)

- `python3 tools/run_r31_contracts.py --tools ~/.cache/fusion-host-r23`:
  **R31 PASS: 152 assertions across 10 kernels (pure-logic round, zero
  android imports).** Separate per-kernel kotlinc compiles (10 jars, the hw
  group explicitly carrying its Models.kt dependency) + consumer run on the
  4 real fixtures in `tools/fixtures/r31/`. Toolchain: kotlinc-jvm 2.2.21,
  JRE 17.0.9.
- `python3 tools/check_r31_reference.py`: PASS (scrub 5 tags, gate 7
  vectors, query 7 incl. AND-binds-tighter RPN shape, offsets 4 starts,
  vault digest `241ff8801b95...`, pdf startxref 182, psd 2 blocks, codec 3,
  phase 400ms, robots 2 groups). The query evaluator is a deliberately
  distinct shunting-yard implementation, not a port of the kernel's
  recursive descent.
- `python3 tools/test_r31_tools.py`: 9/9 PASS (incl. vault sorting-is-
  load-bearing check and the scrub fixture self-trip guard).
- `python3 tools/check_r31_touch_targets.py`: detector self-test PASS; 223
  Button-family sites scanned, 175 static findings across 39 files (delta +0
  vs the R28 baseline — R31 authors no UI; queue is pre-existing).
  Strict: ConsentDialog clean (2 sites). STATIC ONLY — not rendered proof.
- `python3 tools/check_r26_android_syntax.py`: 8/8 PARSE OK, 0 syntax errors
  (resolution-only diagnostics expected without android.jar; R31 adds no
  Android files).
- `python3 tools/check_r31_manifest_scope.py`: PASS — 2 activities, 1
  receiver, 0 services/providers, INTERNET + READ_MEDIA_IMAGES only (static
  XML; merged truth needs AGP).
- Repair log: (1) PdfTrailer object/data-class name clash ->
  PdfTrailerInfo (parallel same-file edits proved lossy again — applied
  serially with grep verification). (2) Runner SPDX assert tripped on
  pre-existing Models.kt -> SPDX stays in tool tests per R30 precedent
  (runner keeps the android-import gate). (3) Scrub fixture "bearer header"
  self-tripped the bearer grammar -> reworded to "auth header" (kernel
  behavior correct per its grammar; tool test now guards the fixture).
  (4) Checks arithmetic: toOffset clamp target is 10 (end of text), OOB
  probes at 11 (end positions are valid). Pre-execution review fixes:
  SearchQuery Unicode tokenizer, VaultManifest hex masking, PsdResources
  overflow-safe bound, TextOffsets dead-code removal, exact-UA and
  hyphen-negation KDoc honesty.

## Authored Android (NONE this turn)

R31 authors no Android components, no manifest entries, no permissions, no
stubs, and no authored-support store. Every R31 production file is a
host-JVM kernel with zero android imports (hygiene-gated). Future wiring
(redacted-report share, LocalIndex queries, editor integration, fetcher
policy, probe mapping) is documented as unresolved interfaces in R31-PLAN,
never claimed.

## Scope

Host JVM + independent Python recomputation + static scans only. NOT Android,
device, service, anonymity, or acceptance evidence. Census: subset 50/50
(100%), R31-deepened 10/50 (20%), cumulative distinct R26–R31 42/50 (84%),
full 0/50.

## Visual inspection (honest substitute, no emulator in sandbox)

No emulator/SDK exists here, so no screenshot was taken and none is claimed.
What holds instead: (1) R31 authors no UI, so the static touch-target audit
is unchanged (223 sites, +0 delta); (2) TextOffsets is editor-adjacent MATH
only (offsets/columns/words) with UTF-16 honesty documented and no
rendering claim; (3) layout-bounds + page-matrix self-tests (detectors
verified; page coverage still honestly 0.0 — no device exports). CI device
frames exist as artifacts but blob download is blocked in this sandbox, so
no CI frame was visually inspected either. All limits stated, not hidden.

## CI ledger (exact candidates, nothing inherited)

6 workflows triggered on the R31 commit (path-filtered set differs from
R30's: R21 + API37 privacy instead of PNG/R24-boundaries — consistent with
a kernels+docs-only delta). Triage is by annotations API (logs unavailable;
claim only what annotations show).

| Candidate | Workflow / run | Outcome | Cause |
|---|---|---|---|
| 9f1731b | R21 39744975134, API37 privacy 39744975220, privacy 39744975139, R23/R22 39744975249 | 4 SUCCESS | — |
| 9f1731b | native 39744975119: "Traceability, assemble both flavors, JVM tests" | SUCCESS | **Compile receipt**: all 10 R31 kernels assemble both flavors; JVM tests pass. |
| 9f1731b | native 39744975119 device job | FAIL | Only the same 2 pre-existing R25PngCopyDeviceTest cases; the full suite otherwise passes. |
| 9f1731b | atlas 39744975203 | FAIL | Pre-existing infra: root job exits 127, frame jobs fail downstream on missing artifacts; the Servo 0.7.0 AAR inspection job succeeds. Same shape as base/R28–R30. |

Net: all R31 code compiles both flavors with green JVM tests. Every device
failure is a pre-existing shape (2 PNG cases, atlas exit-127 + downstream
artifact misses); no transient infra outage and no new failure this round —
no failure names an R31 file, and R31 touches no device-covered path (no
Android code authored at all). Zero confirmed R31 regressions. Runs on the
ledger commit itself will be observed per precedent and folded next round.

Ledger amendment (observed from the R32 turn): the `7d30a44` ("R31: record
9f1731b CI ledger") runs show identical pre-existing shapes — native
39747906385 compiles both flavors with green JVM tests while its device job
fails only the same 2 pre-existing R25PngCopyDeviceTest cases, and atlas
39747906469 fails the pre-existing root exit-127. R31 closure stands; zero
confirmed R31 regressions.

## Lab receipt

R31 host lab PASS (openjdk version "17.0.9" 2023-10-17 / kotlinc-jvm 2.2.21 (JRE 17.0.9+9)).
Harness: tools/r31_lab.py host --tools /home/user/.cache/fusion-host-r23
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
- python test_record_sdk_platform: PASS 2/2
- python test_release_gate: PASS 5/5
- python test_requirements: PASS 5/5
Hashes (sha256, fixtures+consumers):
  4e1582cd1e616ff07db4814b918daa141c8af76793f029a4542b220ab67f3781  tools/fixtures/r31/min.pdf
  b4071e52c5e20d359cc9e283d1fcbadb6e6dd8e2170675da49a4fe39e3b746ef  tools/fixtures/r31/psd_res.bin
  bd5113ed466921c4afd3cc201597b5aee6fff1c3b0683a6c5558f92b6f18d15d  tools/fixtures/r31/robots.txt
  d238395d89d33aaa4cef9499440787d38fca903373d19ee30430faa0567cf2d4  tools/fixtures/r31/scrub_sample.txt
  19bd70df12985fec974e4aa36f959623b316f4fe6e13868259e3f4739f97c130  testing/src/r31/kotlin/dev/fusion/testing/R31Checks.kt
