# R33 lab results

Working branch `arena/350b91e4-osb`. Contract: [R33-PLAN](R33-PLAN.md).

## Host execution (this sandbox, 2026-10-22)

- `python3 tools/run_r33_contracts.py --tools ~/.cache/fusion-host-r23`:
  **R33 PASS: 142 assertions across 10 kernels (pure-logic round, zero
  android imports).** Separate per-kernel kotlinc compiles (10 jars, the
  documents group explicitly carrying TextEncoding + TextDocument +
  LineDiff) + consumer run on the 3 real fixtures in `tools/fixtures/r33/`.
  Toolchain: kotlinc-jvm 2.2.21, JRE 17.0.9.
- `python3 tools/check_r33_reference.py`: PASS (e164 12, ansi 10, chunk 4,
  glob 19 via a memoized recursive matcher, fuzzy 6, clock 4, hunks via
  difflib change-shape agreement, bmp 2×2, gzip 2 fixtures gunzip, totp 6
  RFC vectors via hmac over raw key bytes). The reference implementations
  are deliberately distinct from the kernels' algorithms.
- `python3 tools/test_r33_tools.py`: 13/13 PASS (incl. the gzip gunzip
  guards, the RFC-vector cross-check, and the kept package-collision
  guard).
- `python3 tools/check_r33_touch_targets.py`: detector self-test PASS; 223
  Button-family sites scanned, 175 static findings across 39 files (delta +0
  vs the R28 baseline — R33 authors no UI; queue is pre-existing).
  Strict: ConsentDialog clean (2 sites). STATIC ONLY — not rendered proof.
- `python3 tools/check_r26_android_syntax.py`: 8/8 PARSE OK, 0 syntax errors
  (resolution-only diagnostics expected without android.jar; R33 adds no
  Android files).
- `python3 tools/check_r33_manifest_scope.py`: PASS — 2 activities, 1
  receiver, 0 services/providers, INTERNET + READ_MEDIA_IMAGES only (static
  XML; merged truth needs AGP).
- Repair log: (1) documents group missed the TextBytes→TextEncoding closure
  -> group carries TextEncoding + TextDocument + LineDiff (verified no
  further Text* refs). (2) Clock pins compared Int maps against Long maps
  -> L-suffixes (test-only; kernel correct). (3) Parallel same-file edits
  proved lossy a third time (only last-per-file survived) -> re-applied
  serially with grep verification. (4) Reference/test-only slips (TOTP
  slice precedence, glob mirror parity) fixed in Python. Pre-execution
  review fixes: SGR overflow-param null, glob `^`-negation removal, glob
  `]`-literal add + dead empty-class check removal, hunk line-ending
  strip, TOTP 7-digit and fuzzy folded-pin corrections.

## Authored Android (NONE this turn)

R33 authors no Android components, no manifest entries, no permissions, no
stubs, and no authored-support store. Every R33 production file is a
host-JVM kernel with zero android imports (hygiene-gated). Future wiring
(dialer/SMS, terminal render, beam transport, file search, palette ranking,
conflict resolution, diff display, image/archive decode, authenticator UI)
is documented as unresolved interfaces in R33-PLAN, never claimed.

## Scope

Host JVM + independent Python recomputation + static scans only. NOT Android,
device, service, anonymity, or acceptance evidence. Census: subset 50/50
(100%), R33-deepened 10/50 (20%), cumulative distinct R26–R33 45/50 (90%,
unchanged — all seconds), full 0/50.

## Visual inspection (honest substitute, no emulator in sandbox)

No emulator/SDK exists here, so no screenshot was taken and none is claimed.
What holds instead: (1) R33 authors no UI, so the static touch-target audit
is unchanged (223 sites, +0 delta); (2) BmpInspect and AnsiSgr are
format-adjacent MATH only (headers/styles) with no decode/render claim, and
DiffHunks renders text, not pixels; (3) layout-bounds + page-matrix
self-tests (detectors verified; page coverage still honestly 0.0 — no
device exports). CI device frames exist as artifacts but blob download is
blocked in this sandbox, so no CI frame was visually inspected either. All
limits stated, not hidden.

## CI ledger (exact candidates, nothing inherited)

4 workflows triggered on the R33 commit (path-filtered set: native, atlas,
R25 PNG, R23/R22 — consistent with a kernels+docs-only delta). Triage is by
annotations API (logs unavailable; claim only what annotations show).

| Candidate | Workflow / run | Outcome | Cause |
|---|---|---|---|
| 5ece6a0 | R23/R22 39813286022 | SUCCESS | — |
| 5ece6a0 | native 39813285830: "Traceability, assemble both flavors, JVM tests" | SUCCESS | **Compile receipt**: all 10 R33 kernels assemble both flavors; JVM tests pass. |
| 5ece6a0 | native 39813285830 device job | FAIL | Only the same 2 pre-existing R25PngCopyDeviceTest cases; the R24 audio-focus suspect flake absent again (1 fail in 4 observations — stays a watch item, resolving toward noise). |
| 5ece6a0 | R25 PNG 39813285817 | FAIL | Same 2 pre-existing PNG cases in the focused job; frame/verify jobs fail downstream. Pre-existing shape. |
| 5ece6a0 | atlas 39813286163 | FAIL | Pre-existing infra: root job exits 127 at "Install real compilation and emulator components", frame jobs fail downstream, Servo 0.7.0 AAR inspection succeeds. |

Net: all R33 code compiles both flavors with green JVM tests. Every failure
is a pre-existing shape (2 PNG cases wherever PNG device tests run, atlas
exit-127 + downstream artifact misses); no transient infra outage and no new
failure this round — no failure names an R33 file, and R33 touches no
device-covered path (no Android code authored at all). Zero confirmed R33
regressions. The `056625b` (R32 ledger) observation was folded into
R32-RESULTS as an amendment inside the R33 commit — identical pre-existing
shapes, R32 closure stands. Runs on the ledger commit itself will be
observed per precedent and folded next round.

### Amendment: runs on the ledger commit 2aea522 (folded in R34)

| Candidate | Workflow / run | Outcome | Cause |
|---|---|---|---|
| 2aea522 | native 39817044602: compile + JVM | SUCCESS | Docs-only delta; nothing new to compile. |
| 2aea522 | native 39817044602 device job | FAIL | Pre-existing PNG recreation case PLUS first-seen `R24TaskInputDeviceTest.oneNativeImeVisibleNewNoteTouchRequestsDiscardWithoutErasingDraft` — names no R33 file; the other pre-existing PNG case PASSED this run (pre-existing PNG failures vary 1–2 per run). The IME case is a new watch item, suspect flake. |
| 2aea522 | atlas 39817044597 | FAIL | Pre-existing infra: root job fails at the same pre-compile install stage, this run with exit 1 (`sdk37-install`) rather than 127 — the atlas pre-compile failure is itself unstable run to run. Servo inspection succeeds. |

Net: zero R33 regressions confirmed; R33 closure stands. The R24TaskInput IME
case joins the R24 audio-focus case on the flake watch list for R34.

## Lab receipt

R33 host lab PASS (openjdk version "17.0.9" 2023-10-17 / kotlinc-jvm 2.2.21 (JRE 17.0.9+9)).
Harness: tools/r33_lab.py host --tools /home/user/.cache/fusion-host-r23
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
- python test_record_sdk_platform: PASS 2/2
- python test_release_gate: PASS 5/5
- python test_requirements: PASS 5/5
Hashes (sha256, fixtures+consumers):
  a7beb5056325b28509539b4f84f7444a1333692b32806406fe21d7d6f991ee8b  tools/fixtures/r33/tiny.bmp
  af7a6979986334b19f48252f6f33d2ec9250debf26de0d0f25c7552d51891971  tools/fixtures/r33/name.gz
  ae5a2369ed3db607bec6485ccb71f1b30c4af4a26a6905128bb23a07c6f43321  tools/fixtures/r33/hcrc.gz
  74af8ae67077ac1aecce15a80fd677038e5f2e30f759f21dd80490ee09bd124d  testing/src/r33/kotlin/dev/fusion/testing/R33Checks.kt
