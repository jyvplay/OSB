# R29 lab results

Working branch `arena/350b91e4-osb`. Contract: [R29-PLAN](R29-PLAN.md).

## Host execution (this sandbox, 2026-10-21)

- `python3 tools/run_r29_contracts.py --tools ~/.cache/fusion-host-r23`:
  **R29 PASS: 128 assertions across 10 kernels + UsbAllowlist
  authored-support (in-memory fake prefs).** Separate per-kernel kotlinc
  compiles (11 jars: 10 kernels + usb group) + consumer run on the 6 real
  fixtures in `tools/fixtures/r29/`. Toolchain: kotlinc-jvm 2.2.21, JRE 17.0.9.
- `python3 tools/check_r29_reference.py`: PASS (shares 5/1076384256B, quota
  2 scopes, throttle 2.0 tokens, filesort 5 rows, alias 1 chain + 1 cycle,
  contrast 3 pairs, locale 3, retention 3 kept, DIDL 3 items, CSV 3 dialects).
- `python3 tools/test_r29_tools.py`: 10/10 PASS.
- `python3 tools/check_r29_touch_targets.py`: detector self-test PASS; 223
  Button-family sites scanned, 175 static findings across 39 files (delta +0
  vs the R28 baseline — R29 authors no Compose UI; queue is pre-existing).
  Strict: ConsentDialog clean (2 sites). STATIC ONLY — not rendered proof.
- `python3 tools/check_r26_android_syntax.py`: 8/8 PARSE OK, 0 syntax errors
  (resolution-only diagnostics expected without android.jar; R29 adds
  UsbAllowlist.kt).
- `python3 tools/check_r29_manifest_scope.py`: PASS — 2 activities, 1
  receiver, 0 services/providers, INTERNET + READ_MEDIA_IMAGES only (static
  XML; merged truth needs AGP).
- Repair log: (1) FileSort "stability" claim was wrong — name tiebreaks
  reorder equal keys, so check 47 failed honestly; the contract is now the
  documented TOTAL order with an input-order-independence check. (2) Two
  retention expectations re-derived from window math before first green.
  (3) `thenBy` multi-selector syntax fixed to chained calls. (4) ooxml group
  gained its LiteralWorkbook dependency explicitly. One pre-existing
  duplication trap avoided (FrameBudget/ThermalPolicy already exist); the
  perf slot became :feature:accessibility ContrastRatio.

## Authored Android (NOT executed, one labeled exception)

UsbWatchReceiver wiring edit (allowlist feeds already-allowed; still records
+ notifies) is parse-gated only. UsbAllowlist's STORE LOGIC executes on the
host JVM against test-only `android.content` stubs + an in-memory fake
(17 assertions); the SharedPreferences wiring itself is authored-only and
needs Studio/CI compile + device execution.

## Scope

Host JVM + independent Python recomputation + static scans only. NOT Android,
device, service, anonymity, or acceptance evidence. Census: subset 50/50
(100%), R29-deepened 10/50 (20%), cumulative distinct R26–R29 24/50 (48%),
full 0/50. The :app row gained UsbAllowlist evidence but is NOT counted as
deepened (wiring unaudited).

## Visual inspection (honest substitute, no emulator in sandbox)

No emulator/SDK exists here, so no screenshot was taken and none is claimed.
What ran instead: (1) the static touch-target audit above (223 sites, +0
delta), (2) layout-bounds + page-matrix self-tests (detectors verified; page
coverage still honestly 0.0 — no device exports), (3) the new ContrastRatio
kernel pins the WCAG math (21.0/1.0/#76-#77 boundary) that any future palette
audit will reuse. CI device frames exist as artifacts but blob download is
blocked in this sandbox, so no CI frame was visually inspected either. All
limits stated, not hidden.

## CI ledger (exact candidates, nothing inherited)

BRANCH NOTE: this turn opened with a truncated clone (base commit only), but
the full R26–R28 history was recovered intact from origin (`65bf049`), and
the working tree matched it byte-for-byte outside the 41 R29 files, so no
recovery commit was needed. The R28 ledger stands as branch history; rows
below are the live R29 receipts.

| Candidate | Workflow / run | Outcome | Cause |
|---|---|---|---|
| 432616a | privacy 39648281212, preconditions 39648281447, video 39648281450, WebView 39648281429, R21 39648281515, R23/R22 39648281332 | 6 SUCCESS | — |
| 432616a | native 39648281386: "Traceability, assemble both flavors, JVM tests" | SUCCESS | **Compile receipt**: all R29 code (10 kernels + allowlist + receiver wiring + unescapeXml) assembles both flavors; JVM tests pass (incl. UpnpParsingTest). |
| 432616a | native 39648281386 device job | FAIL (infra) | `pixel6Api34Setup`: API 34 system-image install failed (corrupt zip). Zero cases ran; no code signal. Retriggered via the ledger commit below. |
| 432616a | R24 boundaries 39648281588 | FAIL (infra) | Same system-image corruption; zero cases ran. R29 files on this workflow's paths (receiver wiring, additive LocaleMatch) have no device coverage either way; accepted as infra-blocked, not re-driven. |
| 432616a | PNG 39648281747 | FAIL | Pre-existing: the same 2 R25PngCopyDeviceTest cases as base/R28. |
| 432616a | atlas 39648281629 (1m31s) | FAIL | Pre-existing infra (exit 127), same as base/R28. |
| a2ca07e | (empty retrigger commit) | NO RUNS | Path-filtered workflows ignore empty commits; `gh run rerun` and `workflow_dispatch` both refused (token lacks actions:write). Retrigger happens through this ledger commit instead (native matches `android/**`). |
| 7c4b346 (this tree) | native 39651647529: compile/assemble/JVM | SUCCESS | Second compile receipt on the ledger tree. |
| 7c4b346 | native 39651647529 device job | FAIL | Only the same 2 pre-existing R25PngCopyDeviceTest cases; the full suite otherwise passes. |
| 7c4b346 | atlas 39651647592 (1m34s) | FAIL | Pre-existing infra again (same shape). |

| 8dcb520 | native 39656293954: compile/assemble/JVM | SUCCESS | Third compile receipt. |
| 8dcb520 | native 39656293954 device job | FAIL | `R25PngCopyDeviceTest.recreation...` (pre-existing) + `R24TaskInputDeviceTest.oneNativeImeVisibleNewNoteTouchRequestsDiscardWithoutErasingDraft` (new-vs-base single occurrence). |
| 8dcb520 | atlas 39656294190 (1m15s) | FAIL | Pre-existing infra again. |

Net: all R29 code compiles both flavors with green JVM tests on three
candidates. Device failures: the PNG set varies run to run (2 cases on
432616a/7c4b346, 1 on 8dcb520 — pre-existing, base-confirmed); atlas infra
fails identically every run (base-confirmed); one transient SDK-download
outage cleared on retry. The single R24 IME-discard failure appears only on
8dcb520, whose tree differs from the green-except-PNG 7c4b346 run by ledger
TEXT alone — no R29 code sits on the IME/new-note/discard path (R29 touches
beam/storage/alerts/filesort/alias/a11y/settings/backup/dlna/ooxml/USB
only), so it is classified an emulator flake BY CONSTRUCTION, with the
caveat that a confirmatory rerun was impossible (`gh run rerun` and
`workflow_dispatch` both refused: token lacks actions:write). Zero confirmed
R29 regressions: no failure names an R29 file, and the two R29-edited
production files (UsbWatchReceiver wiring, UpnpParsing addition) ride the
green compile/JVM jobs.

## Amendment (folded into the R29 ledger inside the R30 commit)

Per R28 precedent, the final ledger-close commit's runs are observed, not
chased. Runs on `a62ac00` ("R29: close ledger"):

| Candidate | Workflow / run | Outcome | Cause |
|---|---|---|---|
| a62ac00 | native 39660742343: "Traceability, assemble both flavors, JVM tests" | SUCCESS | Fourth compile receipt on the ledger-close tree. |
| a62ac00 | native 39660742343 device job | FAIL | Only the same pre-existing R25PngCopyDeviceTest cases; the R24 IME-discard case PASSES here. |
| a62ac00 | atlas 39660742441 | FAIL | Pre-existing infra (exit 127), same shape as every run. |

The `a62ac00` tree differs from `8dcb520` by ledger TEXT alone, and the
single R24 IME-discard failure seen on `8dcb520` does not reproduce on
`a62ac00` — so the by-construction flake classification above is now
CONFIRMED by observation. R29 closure stands: 4 compile receipts, device
failures limited to the pre-existing PNG set + atlas infra + one confirmed
flake, zero confirmed R29 regressions.

## Lab receipt

R29 host lab PASS (openjdk version "17.0.9" 2023-10-17 / kotlinc-jvm 2.2.21 (JRE 17.0.9+9)).
Harness: tools/r29_lab.py host --tools /home/user/.cache/fusion-host-r23
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
- python test_record_sdk_platform: PASS 2/2
- python test_release_gate: PASS 5/5
- python test_requirements: PASS 5/5
Hashes (sha256, fixtures+consumers):
  cde46a4970b3684354d8344b8d4f920c0067b23f43f5e65bc71273b1d2592da5  tools/fixtures/r29/didl_audio.xml
  d23a5dc201f38e70b3209e1cab43ef0c8c02eb348ecb0d06f1aeced299581ce4  tools/fixtures/r29/didl_video.xml
  7ce3216a532574c565dfb1a5372e47769b356380841f7da773a304d0da5be0e9  tools/fixtures/r29/pipe.csv
  0ca8779bb3537a8328ccc0aabbda38f7adadf75d7c4d087075cbc90325acc6bf  tools/fixtures/r29/semi.csv
  5e3d5da481016664bfe84b5f90fed802be86c68ecf36a0203e4a467a2e5eecfc  tools/fixtures/r29/shares.txt
  d23e8ccbdc5f6384e2a8feef9880684a9733c1df2936f9bce13cad6eeab4c86e  tools/fixtures/r29/tab.csv
  9653ceda9956f6d55300367bd08e07440f64b1bbc929928a68fcd38857a595f7  testing/src/r29/kotlin/dev/fusion/testing/R29Checks.kt
