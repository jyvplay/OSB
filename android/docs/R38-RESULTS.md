# R38 lab results

Round shape: recovery from a sandbox reset (re-fetch, mixed reset to
`2f20920`, byte-clean, toolchain reinstall with hashes matching
`host-tools.json`), R37 baseline re-run green (274/274 + 14 mirrors) BEFORE
any edit, the `2f20920` CI runs folded into R37-RESULTS as an amendment,
then seventeen new executed kernels.

## Host execution (this sandbox, 2026-10-23)

- `python3 tools/run_r38_contracts.py` → **R38 PASS: 326 production-kernel
  assertions across 17 kernels.** Each kernel compiled ALONE with
  `-include-runtime` (17 jars), consumer compiled against all 17, one JVM
  run. 326 > R37's 274; 17 kernels > R37's 14.
- `python3 tools/check_r38_reference.py` → PASS, 17 independent Python
  mirrors; five use held-out stdlib implementations (uuid, quopri,
  colorsys, datetime.isocalendar, statistics/base64) that share zero code
  with the kernels.
- `python3 tools/test_r38_tools.py` → 24/24.
- Static gates: touch targets 223 sites / 175 findings / **delta +0** vs
  the R28 baseline (R38 authors no UI); ConsentDialog strict clean
  (2 sites); manifest scope unchanged (2 activities, 1 receiver,
  0 services/providers, INTERNET + READ_MEDIA_IMAGES).
- Census: `r38_census_deepen.py` run twice (idempotent, byte-identical),
  `module_testability.py --check` green. 50/50 testable (100%), 15/50
  deepened this round (30%), 45/50 distinct executed-kernel modules (90%),
  0/50 specification-accepted (0%).

## Pinned facts produced this round (spot list)

- Magnet base32 vector `CT76KHBPIIZZ7AMINCYQAAAAAAAAAAAA` ⇄
  `14ffe51c2f42339f818868b10000000000000000`.
- FLAC fixture: 42 bytes; streaminfo acc = sr(20)|ch-1(3)|bps-1(5)|
  total(36); 44100 Hz / 2ch / 16bps / 88200 samples / 2000 ms.
- WebP fixture: riffSize 22, VP8X flags 0x30 (ICC|ALPHA), canvas stored
  1023x767 → 1024x768.
- UUIDv7 RFC vector ts = 1645557742000 ms (Python uuid agrees).
- EDID: 0x10AC → "DEL", 0x244D → "IBM"; sum-to-zero checksum enforced.
- Luhn: `79927398713` valid but NOT masked in text (11 < PAN floor 13) —
  the scrubber's no-overreach property.
- Soundex H/W rule: Burroughs → B620 (not B622); Ashcraft → A261 (not A226).
- SemVer item-11 chain holds including `beta.2 < beta.11`.
- KeyCadence: human window = mean 55.6..166.7 ms AND SD 25..70 ms;
  SD < 1.5 ms or mean < 25 ms or rate > 200/s ⇒ MACHINE_LIKE; no "safe"
  verdict exists.

## Authored Android (NONE this turn)

All 17 kernels are new pure-logic files in existing modules; no manifest,
UI, or service changes. The app a laptop Android Studio build produces is
behaviorally identical to `2f20920` except that the new kernels exist and
are compiled into their modules.

## Scope

Host JVM + host Python + static XML/text analysis + CI annotations only.
No device, emulator, rendering, swarm, mail, display-DDC, or payment
evidence. 0/50 modules specification-accepted; nothing here is A-grade and
no such claim is made.

## Visual inspection (honest substitute, no emulator in sandbox)

No renderer exists here, so visual claims stay downgraded to measurements:
beauty/contrast reference re-run green (3 palettes × 9 pairs ≥ 4.5:1,
phi-gap floor 0.8316 ≥ 0.80), layout-bounds and page-matrix self-tests
green, touch-target delta +0. The only new visually-relevant kernel,
HslColor, is verified numerically against colorsys — not against rendered
pixels; page render coverage remains honestly 0.0.

## CI ledger (exact candidates, nothing inherited)

Filled in after the push of the R38 round commit; triage is by the
annotations API with exact run ids, per the R35-R37 precedent. Standing
watches carried in: IME 2 fails / 9 observations; audio-focus 1/10;
screen-off companion 1/2; R22 Xlsx picker-dismiss 1/2 (downgraded from 1/1
after passing on `2f20920`); NEW R25LocalVideo MP4-decoder case 1 fail / 2
recent observations of that job (first failed on `2f20920`, a docs-only
commit, so no code suspect exists).


## Lab receipt

R38 host lab PASS (openjdk version "17.0.9" 2023-10-17 / kotlinc-jvm 2.2.21 (JRE 17.0.9+9)).
Harness: tools/r38_lab.py host
- host contracts: PASS
- reference recomputation: PASS
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
- python test_record_sdk_platform: PASS 2/2
- python test_release_gate: PASS 5/5
- python test_requirements: PASS 5/5
Hashes (sha256, fixtures+consumers):
  e5a397ce78f7e95e0eba692b95e990003de53b371817b166d68a827b554709a9  tools/fixtures/r38/flac.bin
  e291d05f5c1a32f670b1e660ba22907d877725cd6b963e2f7cc5f4966bbacbdf  tools/fixtures/r38/webp.bin
  9d57aa0a77fab6133ef867a4bcc67159cfe7398965d79a1737467533cdfe49a0  tools/fixtures/r38/edid.bin
  6a710d7b7e6fdc9b9c396b40035c194c472fbe10b049c3550660fbb2e48554f0  testing/src/r38/kotlin/dev/fusion/testing/R38Checks.kt
