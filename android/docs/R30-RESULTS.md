# R30 lab results

Working branch `arena/350b91e4-osb`. Contract: [R30-PLAN](R30-PLAN.md).

## Host execution (this sandbox, 2026-10-22)

- `python3 tools/run_r30_contracts.py --tools ~/.cache/fusion-host-r23`:
  **R30 PASS: 98 assertions across 10 kernels (pure-logic round, zero
  android imports).** Separate per-kernel kotlinc compiles (10 jars, the
  model group explicitly carrying its Models.kt dependency) + consumer run
  on the 4 real fixtures in `tools/fixtures/r30/`. Toolchain: kotlinc-jvm
  2.2.21, JRE 17.0.9.
- `python3 tools/check_r30_reference.py`: PASS (RTSP 135B body, timecode 4
  vectors, VEVENT 6 props, Lamport 5 events, GIF 2 frames, scope 3+1, tar 2
  entries, zoom 2, SMS 3 classes, density 5 tiers).
- `python3 tools/test_r30_tools.py`: 10/10 PASS (incl. GSM alphabet audit:
  127 unique basic + 9 extension codepoints, overlap none).
- `python3 tools/check_r30_touch_targets.py`: detector self-test PASS; 223
  Button-family sites scanned, 175 static findings across 39 files (delta +0
  vs the R28 baseline — R30 authors no UI; queue is pre-existing).
  Strict: ConsentDialog clean (2 sites). STATIC ONLY — not rendered proof.
- `python3 tools/check_r26_android_syntax.py`: 8/8 PARSE OK, 0 syntax errors
  (resolution-only diagnostics expected without android.jar; R30 adds no
  Android files).
- `python3 tools/check_r30_manifest_scope.py`: PASS — 2 activities, 1
  receiver, 0 services/providers, INTERNET + READ_MEDIA_IMAGES only (static
  XML; merged truth needs AGP).
- Repair log: (1) TarHeader ustar version offset was 262/263, off by one —
  the real fixture refused honestly; corrected to 263/264 with a new
  bad-version-digit attack, full suite re-run to green. (2) The model group
  failed to compile until the runner explicitly carried Models.kt
  (DensityProfile's home; zero-import file). (3) Fixture Content-Length
  corrected 129 -> 135 real body bytes. (4) event.ics backslash hygiene
  re-verified at byte level (valid `\\` pair; decoded summary pinned at
  exactly 125 chars = 35-char prefix + 90 x-run). (5) Pre-execution review
  fixes: RtspMessage bare-LF body walk + serialize grammar check,
  VEventParse dead-require removal. (6) Checks tightened to grammar-valid
  inputs and exact golden vectors throughout.

## Authored Android (NONE this turn)

R30 authors no Android components, no manifest entries, no permissions, no
stubs, and no authored-support store. Every R30 production file is a
host-JVM kernel with zero android imports (hygiene-gated). Future wiring
(settings apply path, camera zoom, backup tar writer, calendar import) is
documented as unresolved interfaces in R30-PLAN, never claimed.

## Scope

Host JVM + independent Python recomputation + static scans only. NOT Android,
device, service, anonymity, or acceptance evidence. Census: subset 50/50
(100%), R30-deepened 10/50 (20%), cumulative distinct R26–R30 33/50 (66%),
full 0/50.

## Visual inspection (honest substitute, no emulator in sandbox)

No emulator/SDK exists here, so no screenshot was taken and none is claimed.
What holds instead: (1) R30 authors no UI, so the static touch-target audit
is unchanged (223 sites, +0 delta); (2) ZoomLadder and DensitySelect are
display-adjacent MATH only (zoom ratios, tier cuts) with no layout/render
claim; (3) layout-bounds + page-matrix self-tests (detectors verified; page
coverage still honestly 0.0 — no device exports). CI device frames exist as
artifacts but blob download is blocked in this sandbox, so no CI frame was
visually inspected either. All limits stated, not hidden.

## CI ledger (exact candidates, nothing inherited)

Only 6 workflows triggered on the R30 commit (path filters: kernels + docs
only, no app/UI paths) — fewer than R29's 10, as expected. Triage is by
annotations API (logs unavailable; claim only what annotations show).

| Candidate | Workflow / run | Outcome | Cause |
|---|---|---|---|
| 2fb468c | privacy 39736049334, R23/R22 39736049445, R24 boundaries 39736049209 | 3 SUCCESS | — |
| 2fb468c | native 39736049355: "Traceability, assemble both flavors, JVM tests" | SUCCESS | **Compile receipt**: all 10 R30 kernels assemble both flavors; JVM tests pass. |
| 2fb468c | native 39736049355 device job | FAIL | Only the same 2 pre-existing R25PngCopyDeviceTest cases (`recreation...`, `actualPngPicker...`); the full suite otherwise passes. |
| 2fb468c | PNG 39736049227 | FAIL | Pre-existing: the same 2 R25PngCopyDeviceTest cases; the frame-transport jobs fail downstream on missing artifacts (no frames uploaded). |
| 2fb468c | atlas 39736049520 | FAIL | Pre-existing infra: root job exits 127, frame jobs fail downstream on missing artifacts; the Servo 0.7.0 AAR inspection job succeeds. Same shape as base/R28/R29. |

Net: all R30 code compiles both flavors with green JVM tests. Every device
failure is a pre-existing shape (2 PNG cases, atlas exit-127 + downstream
artifact misses); no transient infra outage and no new failure this round —
no failure names an R30 file, and R30 touches no device-covered path (no
Android code authored at all). Zero confirmed R30 regressions. Runs on the
ledger commit itself will be observed per precedent and folded next round.

## Lab receipt

R30 host lab PASS (openjdk version "17.0.9" 2023-10-17 / kotlinc-jvm 2.2.21 (JRE 17.0.9+9)).
Harness: tools/r30_lab.py host --tools /home/user/.cache/fusion-host-r23
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
- python test_record_sdk_platform: PASS 2/2
- python test_release_gate: PASS 5/5
- python test_requirements: PASS 5/5
Hashes (sha256, fixtures+consumers):
  637e654eb782f318db7293a92c86db03bebe80ee57e5c9966b2a4e6f0580c870  tools/fixtures/r30/archive.tar
  11b910dd424bde10201b619ea5a5ab6919fd4bde202d45f8ab83b75806ceab9f  tools/fixtures/r30/event.ics
  8362e4133274b0d078132e8ab26244dc2a8bbe2d1affc11fd461ea425fe6441b  tools/fixtures/r30/frames2.gif
  80a32f78ec406aa26203ba9e85117fafe2edf46fc61d18d604b963ac5890aaea  tools/fixtures/r30/rtsp_announce.txt
  670b1c6090efe783070a618565cb060bcfd195232a6ac0b55b14d62e11e2b5fa  testing/src/r30/kotlin/dev/fusion/testing/R30Checks.kt
