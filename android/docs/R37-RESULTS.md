# R37 lab results

Working branch `arena/350b91e4-osb`. Contract: [R37-PLAN](R37-PLAN.md).
Evaluation date 2026-10-23.

Round context: the sandbox was re-provisioned between R36 and R37. Recovery was
verified, not assumed: origin re-fetched, branch re-pointed with a mixed reset to
`adbb8a1` (nothing tracked discarded), worktree **byte-clean**; the host toolchain
cache (`~/.cache/fusion-host-r23`, outside the persisted snapshot) re-installed
from the pinned manifest with hashes matching R36 (kotlinc-jvm 2.2.21, JRE
17.0.9); R36's 176 assertions re-run green **on this tree** before any R37 edit;
and the `adbb8a1` CI runs triaged from annotations (folded into R36-RESULTS).
Nothing was recovered from memory.

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

Six workflows triggered on the R37 commit `d7db0ae` (the path-filtered set this
round: native, atlas, R24 privacy experiment, offline privacy companion, R23
memory/R22 boundaries, R25 local video — the PNG-copy and video workflows
appeared because the commit touches `:privacy`, `:format:archive`, `:media`,
`:maps` and friends). Triage is by the annotations API; logs are not
downloadable here, so only what annotations show is claimed.

| Candidate | Workflow / run | Outcome | Cause |
|---|---|---|---|
| d7db0ae | R24 privacy experiment 39960770888 | SUCCESS | — |
| d7db0ae | R25 local video 39960771300 | SUCCESS | — |
| d7db0ae | offline privacy companion 39960770994 | SUCCESS | The screen-off/wake case that failed once on `2ba9c8e` **passed** here: that watch item is now 1 failure in 2 observations. |
| d7db0ae | R23 memory / R22 boundaries 39960771116 | FAIL | **Single case**: `R22XlsxUiDeviceTest.actualReadOnlyPickerImportProjectionAndNewCopyPreserveSource` fails at line 40 — `wait(Until.gone(documentsui), 10s)` after clicking the seed file, i.e. the SAF picker had not dismissed. 25 of 26 device tests PASS in the same run, including the two sibling picker tests `cancelledRealImportPickerKeepsDraftButInvalidatesExport` and `malformedRealProviderImportRetainsDraftAndCannotReusePriorExport`, and every R22/R23 memory-transport case. R37 touches no OOXML, CSV, SAF, MediaStore or picker code. Workflow was green on the previous eight commits (`7dc6dc3` … `f848e91`). **New watch item (1 in 1)**; re-run blocked by token permissions (403), so the flake-vs-regression call rests on the four facts above, not on a re-run. |
| d7db0ae | native 39960770913: "Traceability, assemble both flavors, JVM tests" | SUCCESS | **Compile receipt**: all 14 R37 kernels assemble both flavours and JVM tests pass, including the repaired `VTodoParse`. |
| d7db0ae | native 39960770913 device job | FAIL | Exactly the 2 pre-existing `R25PngCopyDeviceTest` cases ("Image seed not identified in actual DocumentsUI"; both `Failure detail` notices present). Everything else PASS — notably all three `R24TaskInputDeviceTest` IME cases (IME watch now 2 fails in 8 observations), all three `R24PdfDeviceTest` cases, all five `R24TabletopDeviceTest` cases, `R25LocalVideoDeviceTest`, and `RealImeDeviceTest`. |
| d7db0ae | atlas 39960771335 | FAIL | Pre-existing infra: the "Install real compilation and emulator components" step (step 5) fails; the pinned Servo 0.7.0 AAR inspection succeeds; all four frame jobs and the PNG-bytes verification fail downstream. |

Net: **all 14 R37 kernels compile in both flavours and pass JVM tests.** The
device failures are the two standing R25 PNG cases plus one new single-case
picker-dismissal flake in a workflow that had been green eight times; no failure
names an R37 file, and no R37 code path is exercised by the failing assertions
(they assert that DocumentsUI closed, not that Fusion produced wrong data).
Zero confirmed R37 regressions. The ledger commit that records this table is
docs-only; its own path-filtered runs will be observed and folded into R38 per
the R35/R36 precedent.

### Amendment: runs on the docs commits `b7bdad6`/`2f20920` (folded in R38)

Docs-only delta, so only the path-filtered native and atlas workflows ran on
the round-context correction commit `2f20920`. Triage is by the annotations
API (run ids read for this commit; nothing inherited).

| Candidate | Workflow / run | Outcome | Cause |
|---|---|---|---|
| 2f20920 | native 39963506715: "Traceability, assemble both flavors, JVM tests" (job 120447927965) | SUCCESS | Docs-only; all 14 R37 kernels assemble both flavours, JVM tests pass. Compile receipt holds. |
| 2f20920 | native 39963506715 device job 120447928356 | FAIL | The 2 standing `R25PngCopyDeviceTest` cases ("Image seed not identified in actual DocumentsUI", lines 104/131) **plus one new failure**: `R25LocalVideoDeviceTest.actualMp4DecoderProducesOrderedChangingSurfacePixelsAndReleasesOnHome` (`R25LocalVideoDeviceTest.kt:97`, bare `assertTrue`). That case PASSED on `d7db0ae` in this same workflow — **new watch item (1 fail in 2 recent observations of this job)**. The third PNG sibling `isolatedCopyRejectsCorruptionThenPreservesActualDecodedPixels` passed. All three `R24TaskInputDeviceTest` IME cases and `RealImeDeviceTest` passed → IME watch now 2 fails in 9 observations. |
| 2f20920 | atlas 39963507027 | FAIL | Pre-existing infra: "Install real compilation and emulator components" exits 127; the pinned Servo 0.7.0 AAR inspection job (120447707072) succeeds; all four frame jobs and the PNG-bytes verification fail downstream (`No files were found … native-frames/`). |

**Watch-item update carried into R38:** the R37 picker case
`R22XlsxUiDeviceTest.actualReadOnlyPickerImportProjectionAndNewCopyPreserveSource`
**PASSED** inside the native device job on `2f20920` (listed PASS in the
results annotation) — the watch is now **1 failure in 2 observations**,
consistent with a DocumentsUI-dismissal timing flake, not a regression. The
new `R25LocalVideo` MP4-decoder failure replaces it as the freshest
single-observation watch; no R37/R38 file touches the decoder path. Net: zero
R37 regressions on the docs commits; the R37 closure holds.

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
