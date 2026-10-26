# R39 lab results

Round shape: sandbox reset recovery (toolchain re-bootstrap, hashes
matched `host-tools.json`; R38 baseline re-run green — 326/326 + 17
mirrors — BEFORE any edit), then nineteen new executed kernels with the
writer+spreadsheet surfaces front-loaded per the user's explicit ask.

## Host execution (this sandbox, 2026-10-23)

- `python3 tools/run_r39_contracts.py` → **R39 PASS: 344 production-kernel
  assertions across 19 kernels.** Each kernel compiled ALONE with
  `-include-runtime` (19 jars), consumer compiled against all 19, one JVM
  run. 344 > R38's 326; 19 kernels > 17; 16 modules deepened > 15.
- `python3 tools/check_r39_reference.py` → PASS, 19 independent mirrors;
  SIX are held-out stdlib verifiers (datetime ×2, punycode codec, base64,
  fnmatch, bigint mod-97) sharing zero code with the kernels.
- `python3 tools/test_r39_tools.py` → 30/30 (R38: 24).
- Static gates: touch targets 223 sites / 175 findings / **delta +0**
  (R39 authors no UI); manifest scope unchanged (2 activities, 1
  receiver, 0 services/providers).
- Census: deepen script run twice, byte-identical; `--check` green.
  50/50 testable (100%), 16/50 deepened this round (32%), 45/50 distinct
  (90%), 0/50 accepted (0%).
- Source inventory regenerated BEFORE the lab run (R38 lesson applied).

## Pinned facts produced this round (spot list)

- Excel serials: 60 = phantom 1900-02-29 (round-trips); 25569 =
  1970-01-01; 46318 = 2026-10-23; 1904-system delta 1462.
- Column grid: full 1..16384 ⇄ A..XFD bijection executed; default 8.43
  chars = 64 px; file width 9.140625; MDW 8 gives 67 content px.
- Roman numerals: full 1..3999 bijection executed; 3888 =
  MMMDCCCLXXXVIII is the longest canonical numeral.
- Punycode: bücher⇄bcher-kva, ü⇄tda, RFC sample L round-trips; literal
  portion preserves case while extended digits are case-insensitive —
  the round's one repaired test vector (kernel was right, test was wrong).
- ULID: README sample timestamp 1469922850259 ms; spec successor
  …MVRZ→…MVS0 executed; 80-bit overflow refusal executed.
- ICO: PNG-IHDR/directory dimension agreement enforced (lying fixture
  refused); BMP dims are directory-trusted and declared so.
- JWT: alg:none surfaced with empty-signature-only-for-none rule;
  signatureChecked is a constant false.
- Sunny-16 pinned at EV15 ±0.05; IST 17:30+05:30 equals noon UTC epoch
  1792756800.

## Authored Android (NONE this turn)

All 19 kernels are new pure-logic files in existing modules; zero
manifest/UI/service changes. A laptop Android Studio Rabbit 1 build of
this branch is behaviorally identical to the prior round except the new
kernels compile into their modules. The Studio quickstart in BUILD.md is
unchanged and still accurate.

## Scope

Host JVM + host Python + static XML/text analysis + CI annotations only.
No device, emulator, rendering, live-USB, mail, or payment evidence.
0/50 modules specification-accepted; nothing here is A-grade and no such
claim is made (the user's Class-A question is answered directly in
R39-ALTERNATIVES).

## Visual inspection (honest substitute, no renderer in sandbox)

Beauty/contrast reference re-run green in the lab battery (3 palettes ×
9 pairs ≥ 4.5:1; phi-gap floor 0.8316 ≥ 0.80); layout-bounds and
page-matrix self-tests green; touch-target delta +0. SmartQuote output
is verified character-exact, not typographically rendered — rendered
review remains open and is listed as a downgrade, not claimed.

## CI ledger (exact candidates, nothing inherited)

Round commit `4d6007e0ca7b65f9b5b461062112720375836ecc`; all 8 triggered
workflows triaged by exact run id via the annotations API. **Zero R39
regressions.** Two further workflows (Tasks focused diagnostic, R24
appearance/input/PDF) are path-filtered and correctly did NOT trigger —
R39 touches none of their watched paths (R38's automation kernel did);
recorded as non-trigger, not as missing evidence.

| Run id | Workflow | Conclusion | Triage |
|---|---|---|---|
| 40046709153 | Android native | failure | JVM job (traceability + both flavors + JVM tests, **compiles all 19 R39 kernels under real AGP/Gradle**) SUCCESS; managed-device job failed with exactly the 2 standing `R25PngCopyDeviceTest` signatures ("Image seed not identified in actual DocumentsUI", pick:80) |
| 40046709079 | R25 actual PNG metadata copy | failure | same 2 standing PNG DocumentsUI signatures |
| 40046709016 | R24 continuation desktop media atlas | failure | standing install exit 127, no device test executed |
| 40046709102 | R25 actual local video decoding | success | MP4-decoder watch improves to 1 fail / 4 observations |
| 40046709111 | R21 boundary checks | success | — |
| 40046709024 | R24 actual API37 privacy experiment | success | IME watch → 2 fails / 11 observations |
| 40046709109 | R23 memory and R22 boundary checks | success | Xlsx picker-dismiss watch → 1/4 |
| 40046709034 | Android offline privacy companion | success | screen-off watch → 1/4 |

Standing watch table after this round: 2 PNG picker signatures (every
observation — now the only recurring device failure besides atlas),
atlas exit 127 (every observation), R17 focus repro (diagnostic lane,
not triggered this round), IME 2/11, Xlsx 1/4, screen-off 1/4, R25 MP4
1/4, audio-focus 1/10 (did not recur). `rerun-failed-jobs` remains 403
on this token; flake rates accumulate across observations instead.

## Lab receipt

R39 host lab PASS (openjdk version "17.0.9" 2023-10-17 / kotlinc-jvm 2.2.21 (JRE 17.0.9+9)).
Harness: tools/r39_lab.py host
- host contracts: PASS
- reference recomputation: PASS
- R38 contracts (regression): PASS
- R38 reference (regression): PASS
- R37 contracts (regression): PASS
- R37 reference (regression): PASS
- R34 contracts (repair regression): PASS
- R34 reference (repair regression): PASS
- android syntax gate: PASS
- layout-bounds self-test: PASS
- page-matrix self-test: PASS
- beauty/contrast reference: PASS
- touch-target static audit: PASS
- manifest-scope gate: PASS
- HID topology parser (fixture): PASS
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
- python test_r37_tools: PASS 19/19
- python test_r38_tools: PASS 24/24
- python test_r39_tools: PASS 0/0
- python test_record_sdk_platform: PASS 2/2
- python test_release_gate: PASS 5/5
- python test_requirements: PASS 5/5
Hashes (sha256, fixtures+consumers):
  cb67e4ec08d393a6812137948b8b5fa376fff0a0d7f1664f493e071f941c7d09  tools/fixtures/r39/ico.bin
  9798dc19d3b90d043f2d68178b43e86b925a192dad04735568a34df4c0f5060e  tools/fixtures/r39/usbdev.bin
  fdcd34941916dee9133ca7d06bb23d30e2fb8e686b1654f0713102614bfb7c41  testing/src/r39/kotlin/dev/fusion/testing/R39Checks.kt
