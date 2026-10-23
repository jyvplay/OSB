# R37 lab results

Working branch `arena/350b91e4-osb`. Contract: [R37-PLAN](R37-PLAN.md).
Evaluation date 2026-10-23.

Round context: no re-provision happened between R36 and R37. The worktree
continued from the ledger commit `adbb8a1`; before any R37 edit the only
untracked paths were the 14 R37 kernels and `tools/fixtures/r37/`, the toolchain
cache was intact (kotlinc-jvm 2.2.21, JRE 17.0.9), and R36's 176 assertions were
re-run green **on this tree** first. Nothing was recovered from memory.

## Host execution (this sandbox, 2026-10-23)

- `python3 tools/run_r37_contracts.py --tools ~/.cache/fusion-host-r23`:
  **R37 PASS: 274 production-kernel assertions across 14 kernels (zero android
  imports).** Each kernel is compiled **alone** into its own jar (the continuity
  group adds `VTodoParse` because `TaskTally` consumes its `Todo`), then the
  consumer runs against `tools/fixtures/r37/`. Per-kernel counts: SlugHead 18,
  SizeBucket 22, TaskTally 14, XzHead 17, PasteWrap 20, RtspReason 31, UtmStrip
  19, HibpRange 16, PolyLine 20, Mp4Box 16, OscLink 20, RankFuse 18, ExitCode
  25, FovMath 18. **274 static call sites = 274 executed assertions** (none
  inside a loop or conditional).
- `python3 tools/check_r37_reference.py`: PASS — 14 independent mirrors
  (regex slug pipeline, Decimal `ROUND_HALF_UP` sizes, regex+Counter tallies,
  struct+`binascii.crc32` XZ, strip-length re-indent, regex RTSP + 44-code
  cardinality, `urlsplit` URL cleaning, HIBP dict scan, bit-accumulator
  polyline round-trip against the canonical 27-char example, struct MP4 walk,
  OSC regex split, RRF with first-seen tie order, dict exit tables, atan2 FOV).
  Deliberately different algorithms from the kernels.
- `python3 tools/test_r37_tools.py`: **19/19 PASS** (hygiene, package
  collision, fixture shapes, spec-value pins — Check IDs {0,1,4,10}, 44 RTSP
  codes, 18 tracker names — the VTODO spelling pin across kernel/consumer/
  reference, and 5 tests for the HID topology tool).
- **Repair regression**: `python3 tools/run_r34_contracts.py` **166/166 PASS**
  and `python3 tools/check_r34_reference.py` PASS *after* the VTODO token
  repair. The repair inherits no trust: the consumers were changed, then the
  whole R34 surface was re-run.
- `python3 tools/check_r37_touch_targets.py`: detector self-test PASS; 223
  Button-family sites, 175 static findings across 39 files, **delta +0** vs the
  R28 baseline; strict ConsentDialog clean (2 sites). STATIC ONLY.
- `python3 tools/check_r37_manifest_scope.py`: PASS — 2 activities, 1
  receiver, 0 services/providers, INTERNET + READ_MEDIA_IMAGES only.
- `python3 tools/check_r26_android_syntax.py`: 8/8 PARSE OK.
- Layout-bounds, page-matrix and beauty/contrast self-tests: PASS (the beauty
  reference re-checks 3 palettes × 9 pairs ≥ 4.5, floor 0.8316 ≥ 0.80).
- `python3 tools/hid_topology.py` on the labelled format fixtures: REVIEW with
  `DUAL_KEYBOARD` + `HUB_IN_PATH` exactly as designed (2 boot keyboards, max HID
  depth 2). **It is a transcript parser, not a keylogger detector** — its own
  output says so.
- `python3 tools/check_project.py`: PASS (census current, 50 included modules).
- `python3 tools/r37_lab.py host`: **PASS** — 12 non-Python steps + **41 Python
  suites / 326 tests** (R36 was 307; +19 from this round).

Repair log (full detail in [R37-PLAN](R37-PLAN.md) §I): **one shipped-code
repair** (`VTodoParse` accepted `IN-PROGRESS`; RFC 5545 §3.8.1.11 says
`IN-PROCESS`, so every conformant VTODO would have been refused), **two
pre-execution corrections** caught by reading the fetched primaries before the
first green run (`XzHead` had SHA-256 as Check ID 7, which the xz spec reserves
— correct ID is 0x0A; `RtspReason` followed Table 1 and dropped 304), and **nine
kernel/consumer/test fixes** during the turn (one kernel compile fix: PolyLine's
`Int`-as-Boolean `if`; five consumer fixes: `ByteArray.repeat`, a genuinely
ragged tab-unit case, a largesize byte at the wrong offset, an FOV epsilon
assuming 0, an MP4 fixture offset; three Python test fixes: polyline length, RTSP
regex anchor, MP4 type offset), plus two census-tool fixes (idempotent re-run,
declared allowed-stale set). No other kernel semantics changed.

## Authored Android (NONE this turn)

R37 authors no Kotlin-in-Android code and no resources. The only Android-facing
change is inside `:feature:continuity`'s existing `VTodoParse` (pure Kotlin, no
imports). Every R37 surface is host-JVM or host-Python. Any wiring of these
kernels into screens remains an explicit unresolved interface.

## Scope

Host JVM + host Python + static scans only. NOT Android, device, service,
anonymity, or acceptance evidence.

| Measure | Absolute | Percentage |
|---|---|---|
| Modules meeting the testable-subset standard | 50/50 | 100.00% |
| Modules deepened by R37 with new executed kernels | 14/50 | 28.00% |
| Distinct modules deepened R26–R37 (executed kernels) | 45/50 | 90.00% |
| Fully specification-accepted modules | 0/50 | 0.00% |

"Substantial enough code for a test to exist" (the standard the request asks
about) is met by all 50 modules, each with a named runnable command in
`docs/MODULE-TESTABILITY.md`. The number that matters for *this* round is
28.00%: fourteen modules gained new executed kernels, and none of them had a
first-deepen surface left — the survey cut five candidates on ownership grounds
and banked two spares rather than padding the count.

## Visual inspection (honest substitute, no emulator in sandbox)

No emulator or SDK exists here, so no screenshot was taken and none is claimed.
What holds instead: (1) R37 authors no UI, so the static touch-target audit is
unchanged (223 sites, 175 findings, +0 delta) and ConsentDialog stays strict
clean; (2) the beauty/contrast reference runs over all 3 shipped palettes (9
pairs each ≥ 4.5:1, phi-gap floor 0.8316 ≥ 0.80) — that is colour and spacing
math, not a rendered inspection; (3) layout-bounds and page-matrix self-tests
verify the detectors, not the pages (page coverage remains honestly 0.0 without
device exports). CI device frames exist as artifacts, but blob download is
blocked in this sandbox, so no frame was visually inspected either. All limits
are stated, not hidden.

## CI ledger (exact candidates, nothing inherited)

Pending: the R37 commit and its workflow runs are recorded in the ledger commit
per the R35/R36 precedent. Nothing is claimed here before the annotations are
read; the two standing device failures (R25 PNG copy) and the atlas root-install
exit 127 are expected to recur and will be reported as pre-existing only if the
annotations show exactly that.

## Lab receipt

R37 host lab PASS (openjdk version "17.0.9" 2023-10-17 / kotlinc-jvm 2.2.21 (JRE 17.0.9+9)).
Harness: tools/r37_lab.py host
- host contracts: PASS
- reference recomputation: PASS
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
- python test_record_sdk_platform: PASS 2/2
- python test_release_gate: PASS 5/5
- python test_requirements: PASS 5/5
Hashes (sha256, fixtures+consumers):
  9d82b73079cd534ea25d2fd6e11f278dbb75f228386e41056af590148291b572  tools/fixtures/r37/xz.bin
  aa7207beb2649b6efe62aa4bb0fea58ec2a2af6d0d8a2cb0d6497dde6c9ec3a0  tools/fixtures/r37/mp4.bin
  6fba1cbcd2b1e90895ba31e7176343b22d36f62c0992992285550201a8a211c2  testing/src/r37/kotlin/dev/fusion/testing/R37Checks.kt
