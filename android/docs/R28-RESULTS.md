# R28 lab results

Working branch `arena/350b91e4-osb`. Contract: [R28-PLAN](R28-PLAN.md).

## Host execution (this sandbox, 2026-10-21)

- `python3 tools/run_r28_contracts.py --tools ~/.cache/fusion-host-r23`:
  **R28 PASS: 112 production-kernel assertions across 10 kernels.**
  Separate per-kernel kotlinc compiles (10 jars) + consumer run on the 4 real
  fixtures in `tools/fixtures/r28/`. Toolchain: kotlinc-jvm 2.2.21, JRE 17.0.9.
- `python3 tools/check_r28_reference.py`: PASS (chapters 3, cookies 4/4
  decisions, recurrence 3 vectors, tiles 2 vectors, trash 3, front-matter 4 keys).
- `python3 tools/test_r28_tools.py`: 10/10 PASS.
- `python3 tools/check_r28_touch_targets.py`: detector self-test PASS; 223
  Button-family sites scanned, 175 static findings across 39 files queued for
  manual review (pre-existing; calibration spot-checks confirm real missing
  48dp/testTag declarations, e.g. TabletopDesktop:45, EditorScreen:93-95,
  MapScreen:66 no-testTag). Strict: ConsentDialog clean (2 sites). STATIC
  ONLY — not rendered proof; rendered proof still needs device bounds exports.
- `python3 tools/check_r26_android_syntax.py`: 7/7 PARSE OK, 0 syntax errors
  (resolution-only diagnostics expected without android.jar).
- Repair log: ONE genuine production bug caught by check 33 — FrecencyLedger
  eviction scored against the pre-insert map and always evicted the new
  target. Fixed (post-insert snapshot scoring), re-attacked with the 65-target
  identity check, plus a negative-age clamp. One check-side fix (empty-list-item
  input). One self-caught authored bug before commit (`0xUSB1` invalid hex).

## Authored Android (NOT executed)

UsbWatchReceiver (detection-only USB attach/detach alerts + verdict text +
bounded record; manifest-registered, exported) and ConsentDialog (scope+TTL
confirm UI). Parse-gated only; Studio/CI compile + device execution required.

## Scope

Host JVM + independent Python recomputation + static scans only. NOT Android,
device, service, anonymity, or acceptance evidence. Census: subset 50/50
(100%), R28-deepened 10/50 (20%), full 0/50.

## Visual inspection (honest substitute, no emulator in sandbox)

No emulator/SDK exists here, so no screenshot was taken and none is claimed.
What ran instead: (1) the static touch-target audit above (223 sites), (2)
layout-bounds + page-matrix self-tests (detectors verified; page coverage
still honestly 0.0 — no device exports). CI device frames exist as artifacts
but blob download is blocked in this sandbox (EOF), so no CI frame was
visually inspected either. All three limits are stated, not hidden.

## CI ledger (exact candidates, nothing inherited)

| Candidate | Workflow / run | Outcome | Cause |
|---|---|---|---|
| 6e777fe | atlas 39558824039 (1m31s) | FAIL | Pre-existing infra (exit 127, no device cases) — same as base. |
| 6e777fe | UI preconditions 39558824076 | FAIL | Known-flaky `editorActions...TwoHundredPercentFont` Compose cross-thread crash (R20-documented shape); zero R28 code on that path. Flipped SUCCESS on re-run (f848e91) with no related change. |
| 6e777fe | PNG 39558824169 (8m21s) | FAIL | Pre-existing: same 2 R25PngCopyDeviceTest cases as base. |
| 6e777fe | privacy 39558824050 | FAIL | Emulator input-device flake ("Input-device properties changed", workspace discarded); manifest assertions passed. Reaction: moved the receiver OUT of :core:security's manifest anyway (scope hygiene). |
| f848e91 | privacy 39560303477 + API37 39560303482 | FAIL | REAL R28 REGRESSION (mine): `:privacy:lintDebug [NotificationPermission]` — UsbWatchReceiver.notify() in :core:security without merged POST_NOTIFICATIONS. |
| a20ebc6 (repair) | privacy 395615950xx + API37 | SUCCESS | Receiver moved to :app; :privacy scope exact; lint green. |
| a20ebc6 | native 39561595054: "Traceability, assemble both flavors, JVM tests" | SUCCESS | **Compile receipt**: all R28 code (10 kernels + receiver + dialog) assembles both flavors; JVM tests pass. |
| a20ebc6 | native device job | FAIL | Only the same 2 pre-existing PNG device cases; everything else passes. |
| a20ebc6 | WebView, video, R21, R23/R22, preconditions, R24-boundaries | 6 SUCCESS | — |

Net: 9/11 workflows green on the repair candidate; the 2 failures (atlas
infra, PNG device) reproduce on base main. Both R28 regressions I introduced
(security-manifest scope creep, privacy lint) were repaired in-turn with
exact-candidate receipts. Note: 6e777fe's `native` run was cancelled by
concurrency supersession when the scope-fix pushed — no receipt claimed from it.

## Lab receipt

R28 host lab PASS (openjdk version "17.0.9" 2023-10-17 / kotlinc-jvm 2.2.21 (JRE 17.0.9+9)).
Harness: tools/r28_lab.py host --tools /home/user/.cache/fusion-host-r23
- host contracts: PASS
- reference recomputation: PASS
- android syntax gate: PASS
- layout-bounds self-test: PASS
- page-matrix self-test: PASS
- touch-target static audit: PASS
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
- python test_record_sdk_platform: PASS 2/2
- python test_release_gate: PASS 5/5
- python test_requirements: PASS 5/5
Hashes (sha256, fixtures+consumers):
  eb65eef0d4430fb31a22a7648e60485ecdf655a2103a39e724b555d474416d4b  tools/fixtures/r28/chapters.xml
  2dad29dc084d03e84972340a3c1c8664ba9e6af76c8550eeab668871b2191e0f  tools/fixtures/r28/cookies.txt
  eec75ba6d0156e0e59a5353dea29264b7ba10c5038ad1dbb3819547f6962c63e  tools/fixtures/r28/note-fm.md
  ef3f6723f4cc34dec16ab0f8900d2def059f7c786f690588fb2b759c96bf9ae0  tools/fixtures/r28/trash.txt
  e0b9dac85ae9c68e9b393bf859436a0e3708879bf3dcd197cdf15b3449c36acb  testing/src/r28/kotlin/dev/fusion/testing/R28Checks.kt
