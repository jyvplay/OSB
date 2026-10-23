# R37 scope / acceptance contract — 14 executed kernels + 1 hardware-path tool

Working branch `arena/350b91e4-osb`. Evaluation date 2026-10-23.
Fresh research: [research/r37-primary.md](research/r37-primary.md) — three
**fetched primaries** (RFC 2326, xz 1.2.1, RFC 5545) plus two search batches,
strength-labelled.
Alternatives: [R37-ALTERNATIVES](R37-ALTERNATIVES.md) ·
Privacy PRD: [R37-PRIVACY-PRD](R37-PRIVACY-PRD.md) ·
Results: [R37-RESULTS](R37-RESULTS.md) ·
Deepening: [R37-MODULE-DEEPENING](R37-MODULE-DEEPENING.md).

R37 authors **no Android code**. It ships 14 host-JVM kernels, 4 fixtures
(2 binary, 2 lsusb text), 1 new host tool, 1 consumer, 1 independent Python
reference, 3 static gates, a lab, a census pass and this doc set. Every kernel
is a *second or later* kernel for its module; the survey cut the last
first-deepen candidates on ownership grounds (see B).

Round context: like R34–R36, the sandbox was re-provisioned between R36 and
R37. Recovery was verified, not assumed: origin was re-fetched, the branch
re-pointed with a mixed reset to `adbb8a1` (nothing tracked discarded), and the
worktree confirmed **byte-clean**; the host toolchain cache
(`~/.cache/fusion-host-r23`, outside the persisted snapshot) was re-installed
from the pinned manifest and its hashes matched the R36 manifest
(kotlinc-jvm 2.2.21, JRE 17.0.9). R36's 176 assertions were then re-run and
passed **on this exact tree** before any R37 edit, and the `adbb8a1` CI runs
were triaged from annotations.

## A. Formal model (turn contract)

- **Admissible objects.** Host-JVM Kotlin `object`s with zero `android`/
  `androidx` imports; one consumer under `testing/src/r37`; real fixtures
  where a byte format or transcript format exists (`xz.bin` 12 B, `mp4.bin`
  32 B, `lsusb-verbose.txt`, `lsusb-tree.txt` — the last two are *format*
  fixtures and are labelled as such, never as captures of any machine);
  Python mirrors under `tools/`; static gates; docs. No stubs, fakes, mocks or
  simulated substitutes anywhere.
- **Information/access model.** This sandbox has: bash, Python 3.11,
  kotlinc-jvm 2.2.21 + JRE 17.0.9 (pinned cache), the repo, and network
  egress to github.com / codeload / api.github.com / registry.npmjs.org /
  pypi.org plus the `fetch_page` tool (used for rfc-editor.org and github.com
  this round). It has **no** Android SDK, emulator, device, adb, `lsusb`, root,
  or CI blob download. Anything needing those is refused, never claimed.
- **Resource counted.** New *executed* assertions (274), new kernels (14),
  modules deepened again (14 of 50), and new host tools with their own tests.
  Prior evidence is never inherited by new code; repaired code inherits no
  trust (section I).
- **Success predicate** (quantifier order matters):
  ∃ a green run of *all* 274 assertions on this toolchain ∧ ∀ kernel: zero
  android imports ∧ reference agrees on every shared vector ∧ every static
  gate green ∧ every refusal path exercised ∧ CI compiles the kernel set.
  Failure is the negation of any conjunct; a partial green is reported as a
  partial green.
- **Parameter regime.** Caps live in KDoc and are asserted at their edges
  (`MAX_HEADING` 200, `MAX_TRIES` 10 000, `MAX_TODOS` 100 000, `MAX_LINES`
  10 000, `MAX_DEPTH` 64, `MAX_BODY` 1 000 000, `MAX_LINES` 100 000,
  `MAX_POINTS` 100 000, `MAX_BOXES` 10 000, `MAX_SEQ` 4 096, `MAX_LISTS` 100,
  `MAX_IDS` 10 000, `MAX_URL` 8 000). Units: bytes/KiB…PiB, degrees, mm,
  millimetres→degrees, 1e-5 degree polyline quanta, exit codes 0–255.
  Tolerances: exact for integers/strings/bytes/booleans/transitions; `1e-12`
  for float pins (FOV, RRF) except where the value itself is 1e-6-scale, where
  the pin is `1e-12` absolute on a printed value and `1e-9..1e-5` on a range.
- **Adjacent problems NOT substituted** (each is a different problem and must
  not be reported as this one): host pass ≠ Android pass; a slug ≠ link
  resolution; a formatted size ≠ filesystem truth; a status tally ≠ task
  scheduling; an XZ *header* ≠ decompression and ≠ integrity of the payload;
  re-indenting ≠ formatting or parsing; an RTSP *status line* ≠ RTSP session or
  transport conformance; stripping known trackers ≠ untracked; a breach-count
  lookup ≠ password strength and ≠ "unbreached" when 0; a decoded polyline ≠ a
  route, a distance or a map; an MP4 top-level walk ≠ demuxing; an OSC 8 parse
  ≠ link navigation or safety; reciprocal-rank fusion ≠ relevance; an exit-code
  name ≠ process handling; field-of-view math ≠ optical truth; an HID
  transcript audit ≠ keylogger detection.

## B. Outcome space

- **H+**: all 14 kernels execute green (274 assertions), the Python reference
  agrees, gates stay clean, and CI compiles both flavours.
- **H−** (impossibility under stated constraints): any candidate whose
  behaviour cannot be established on a host JVM without Android, a device, a
  network capture or a live bus must be **cut**, not faked. Cuts made this
  round, with the owning surface quoted:
  1. **Haversine distance** (:feature:maps) — already owned verbatim by
     `GpxTrack` track length.
  2. **GSM-7 encoding** (:feature:messages) — owned by `SmsSegments`.
  3. **M3U parsing** (:feature:media) — owned by `PlaylistParser`.
  4. **PNG chunk walk** (:format:image) — owned by `PngMetadata` + the R25
     contracts.
  5. **Public-suffix check** (:feature:browser) — owned by SafeNavigation's
     registrable-domain logic.
  6. **:app, ninth look** — 12/12 files still import android/androidx; every
     honest surface is Android-inline or speculative.
  Two spares were held unbuilt rather than padded in: `MagnetParse` (:feature:beam)
  and `FlacHead` (:feature:media).
- **H∂** (answer changes at a boundary): unit-boundary rounding
  (`1 048 575 B` renders "1024 KiB"), polyline quantization at 1e-5, the
  128-vs-129 exit-code split, the 44th RTSP code (304 present in ABNF, absent
  from Table 1), Check ID 0x0A vs the reserved 0x07, `IN-PROCESS` vs
  `IN-PROGRESS`, blank-line collapse in pasted blocks, tie order in RRF.
  Every one has a boundary assertion. **No H+ is committed until the lab
  prints PASS.**

## C. Frontier (imported results, hypotheses attached)

| Imported result | Source read this round | Hypotheses carried into code |
|---|---|---|
| XZ Stream Header layout, Check IDs, flags CRC | xz-file-format 1.2.1 §2.1.1 | 12-byte header; flags[0]=0; IDs 0/1/4/0x0A only; reserved → error |
| RTSP status grammar | RFC 2326 §7.1.1 | 44 codes; grammar spellings; `RTSP/1.0` only; reason ≤128 chars |
| VTODO STATUS values | RFC 5545 §3.8.1.11 | `IN-PROCESS` is the only in-progress token |
| Google encoded polyline (1e-5, zigzag, 5-bit chunks, +63) | canonical example (27 chars) verified by two independent decoders | round-trip exact at 1e-5; refuse out-of-range |
| HIBP k-anonymity range response | API contract (35-char suffix + `:count`) | caller hashes; 0 ≠ unbreached |
| ISO BMFF box header (size/type, `size==0` to end, `size==1` → 64-bit) | ISO/IEC 14496-12 common knowledge, pinned by fixture | refuse overrun/underflow |
| OSC 8 (`ESC ] 8 ; params ; URI ST`) | widely implemented terminal spec | params `k=v`, empty URI = close |
| Reciprocal rank fusion | Cormack et al. RRF (k = 60) | stable first-seen ties |
| BSD sysexits 64–78, 128+N Linux signal table | sysexits.h / Linux numbering | names only, never handling |
| FOV = 2·atan(d/2f) | thin-lens geometry | rectilinear only |
| IEC 80000-13 binary prefixes | 1024 powers | one decimal, half-up |

Open interface between "imported" and "proved": the RFC/spec text fixes the
*table*, not this repo's *use* of it. Nothing here shows the tables are
complete for future protocol extensions — both parsers refuse unknown values by
construction, which is the honest posture.

## D. Negative space (18 shapes that look like solutions but fail)

1. A kernel that imports `android.*` and "passes kotlinc" — fails the split;
   detected by the hygiene test and the runner's assertion.
2. Re-implementing a surface another kernel owns (haversine GSM-7, M3U, PNG
   chunks, PSL) — detected by the ownership survey before writing.
3. Presenting a slug as link resolution — detected by scope tests (no DOM, no
   anchors).
4. Presenting a size string as filesystem truth — refused: `format` never
   touches a file.
5. Counting VTODOs by "non-completed" — detected by the `unspecified` bucket
   assertion.
6. Treating an XZ header as decompression — detected by the absence of any
   payload API and by the "trailing bytes accepted" pin (header only).
7. Getting the Check ID from a guess instead of the spec (the 0x07 bug) —
   detected by `SHA256(10)` + reserved-id refusals.
8. Following Table 1's spellings and silently dropping 304 — detected by the
   44-code cardinality assertion and the `304` phrase pin.
9. Accepting both `IN-PROCESS` and `IN-PROGRESS` "for compatibility" — detected
   by the explicit refusal test for the legacy token.
10. Re-indenting by string search/replace — detected by the ragged-indent
    refusals and the tab-vs-space separation.
11. Reporting an echoed RTSP reason as canonical — detected by separating
    `parse().reason` from `phrase(code)`.
12. Stripping *all* query parameters and calling the URL clean — detected by
    the "unknown parameters kept" pins.
13. Treating HIBP count 0 as "unbreached" — refused in doc and by the
    absent-suffix pin returning 0 with the corpus caveat.
14. Decoding a polyline without zigzag or with 6-bit chunks — detected by the
    canonical 3-point vector and the exact re-encode.
15. Walking MP4 boxes with a fixed 8-byte stride — detected by the largesize
    and to-end fixtures.
16. Parsing OSC 8 with a greedy split that swallows `;` inside the URI —
    detected by `mailto:a@b` and by treating the *first* `;` as separator.
17. Sorting RRF ties by id — detected by the stable first-seen tie pin.
18. Claiming the HID audit detects keyloggers — refused in the tool's own
    output (`limits`) and in the PRD; a static scan is not a physical search.

## E. Mechanism portfolio (14 kernels + 1 tool; each distinct)

| Kernel | Construction | Proved on host JVM | Unresolved interface | Cheapest falsifier | Gap |
|---|---|---|---|---|---|
| SlugHead | char filter + suffix loop | 18 assertions | note links | `A  B` double hyphen | local |
| SizeBucket | power-of-1024 loop | 22 | file list UI | 1 048 575 → "1024 KiB" | local |
| TaskTally | enum→counter | 14 | task UI | null status bucket | local |
| XzHead | magic/flags/CRC check | 17 | payload decode | id 0x07 refused | local |
| PasteWrap | unit-stack walk | 20 | editor paste flow | ragged indent | local |
| RtspReason | ABNF table + strict parse | 31 | session state | 304 phrase | local |
| UtmStrip | pair filter, order kept | 19 | share sheet | unknown param kept | local |
| HibpRange | suffix scan | 16 | network fetch | 35-char check | local |
| PolyLine | varint/zigzag codec | 20 | map overlay | canonical string | local |
| Mp4Box | size/type walk | 16 | demux | largesize | local |
| OscLink | param split + URI check | 20 | terminal render | `mailto:` URI | local |
| RankFuse | Σ 1/(k+rank) | 18 | search UI | tie order | local |
| ExitCode | three tables | 25 | shell runner | code 128 | local |
| FovMath | atan formula | 18 | lens UI | 1e9 mm | local |
| `hid_topology.py` | lsusb -v/-t parse + policy | 5 python tests + 1 lab run | live bus (no lsusb/root here) | dual-keyboard fixture | local |

## F. Artifacts

- **Proofs**: none claimed (no theorem is asserted).
- **Executable design**: 14 kernels, 274 assertions, 4 fixtures, 1 consumer,
  7 Python tools (`run_r37_contracts`, `check_r37_reference`,
  `check_r37_touch_targets`, `check_r37_manifest_scope`, `test_r37_tools`,
  `r37_lab`, `r37_census_deepen`) + `hid_topology.py`.
- **Reproducible experiments**: the lab (receipted), the reference mirror
  (14 independent re-implementations), the census, the R34 regression re-run.
- **Quantitative bounds**: every cap above, plus "274 call sites = 274
  executed assertions" — no assertion sits in a loop or a conditional branch.
- **Causal model**: none claimed.

## G. Second-order adversary (per-candidate attacks, all executed)

- **SlugHead** — 13 pins (basic, punctuation, double space, trim, kept chars,
  tab, case, accent, unique free/clash/gap/suffix-base), 5 refusals (blank,
  empty, over-long, CJK, emoji). Attack survived: the *attacker* here is the
  heading text; empty-survivor headings are refused rather than slugged to "".
- **SizeBucket** — 20 pins (nine scale boundaries, seven formats, rounding at
  the unit edge, PiB saturation), 2 refusals (negative scale/format).
- **TaskTally** — 12 pins (six buckets, empty, all-null, single, RFC token),
  2 refusals (over cap, legacy token).
- **XzHead** — 8 pins (header length, fixture, None/CRC64/SHA-256 variants, ids
  4 and 0x0A, trailing bytes), 9 refusals (short, magic, flags, ids 2/3/7/0x0B,
  high nibble, CRC).
- **PasteWrap** — 10 pins (one level, stacked, tabs, tab at depth, 3-space
  unit, blank collapse, empty, identity, max depth, nine-space reject), 10
  refusals (unit shapes, depth, ragged, mixed, depth-via-indent, newline, cap).
- **RtspReason** — 19 pins (three parses, cardinality 44, 304/306 presence,
  nine grammar phrases), 12 refusals (unknown 199/306/600, HTTP version,
  RTSP/2.0, two-digit, missing/empty reason, non-digit, out-of-range, newline,
  over-cap).
- **UtmStrip** — 16 pins (drop/keep, empty query, no tracker, no query, case,
  click ids, fragment, order, empty name, valueless, kept valueless, mailchimp,
  openstat, fragment-internal query, set size, unknown name), 3 refusals.
- **HibpRange** — 8 pins (hit, CRLF, empty body, absent, lowercase sha, blank
  lines, zero count, large count), 8 refusals (sha shape, suffix length,
  missing colon, non-numeric, negative, lowercase suffix, over cap).
- **PolyLine** — 13 pins (canonical points ×3, origin, empty decode/encode,
  re-encode, string round trip, point round trip, both extreme corners,
  quantization), 7 refusals (char, truncation, lat/lng range, non-finite, caps).
- **Mp4Box** — 10 pins (fixture count/types/offsets, empty input, empty
  payload, payload box, two boxes, second offset, largesize, to-end), 6
  refusals (short header, size<header, overrun, non-ASCII, short largesize,
  cap).
- **OscLink** — 10 pins (plain, params, id, close marker, ST terminator,
  single param, key charset, mailto), 10 refusals (prefix, OSC 7, terminator,
  separator, key, key char, duplicate, space, control, cap).
- **RankFuse** — 7 pins + 6 float pins (ordering, three scores, default k,
  tie, k=1 ×2, empty), 5 refusals (k, empty set, blank, duplicate, cap).
- **ExitCode** — 22 pins (conventions, five sysexits, four signals, unnamed
  low/mid, 128, unnamed signal, 255, three `isSuccess`), 3 refusals.
- **FovMath** — 11 pins (four sensor pins, 1e9 mm narrow, monotonicity ×2,
  three equivalents), 7 refusals (focal/sensor/crop zero-negative, NaN, inf).
- **`hid_topology.py`** — 5 Python tests (fixture verdict, single-keyboard OK,
  mouse-is-not-keyboard, hub class ignored, device cap) + 1 lab run; its own
  output states the limit.

## H. Verification bindings

- kotlinc-jvm 2.2.21: **14 separate compiles** (so no kernel can borrow a
  sibling's symbols) + JRE 17.0.9 consumer run → `R37 PASS: 274`.
- Independent Python recomputation (`check_r37_reference.py`): deliberately
  different algorithms (regex pipeline, Decimal ROUND_HALF_UP, struct,
  urlsplit, atan2, defaultdict) — 14 mirrors agree.
- Pure-Python suite (`test_r37_tools.py`): 19 tests incl. hygiene, package
  collision, fixture shapes, spec-value pins (Check IDs, 44 RTSP codes, 18
  tracker names) and the VTODO spelling pin.
- Static gates: touch targets (detector self-test + strict ConsentDialog + R28
  baseline delta 0), manifest scope, android syntax (8/8), layout bounds, page
  matrix, beauty/contrast reference, project structure.
- Repair regression: R34 contracts 166/166 + R34 reference re-run green.
- CI: exact-candidate compile receipt required; device failures triaged by
  annotations only (no log download) and never inherited from earlier rounds.

## I. Repair policy

A repaired candidate inherits no trust: full lab re-run + at least one new
attack aimed at the patch.

- **Shipped-code repair (1)**: `VTodoParse` wire token `IN-PROGRESS` →
  `IN-PROCESS` (RFC 5545 §3.8.1.11). Attacks aimed at the patch: the R37
  consumer asserts the RFC token parses **and** that the legacy token is
  refused; `test_r37_tools` pins the string in kernel, consumer and reference.
  R34's 166 assertions and reference were re-run afterwards (green) — no
  evidence was carried over.
- **Pre-execution corrections (2, new uncommitted files)**: `XzHead`
  `SHA256(7)`→`SHA256(10)` (xz 1.2.1 §2.1.1.2; 0x07 is reserved) and
  `RtspReason`'s table rebuilt from the §7.1.1 ABNF (adds 304, fixes four
  spellings). Both were caught by reading the primary text, before the first
  green run; both now have regression assertions.
- **Consumer/reference updates (2)**: `R34Checks.kt` and
  `check_r34_reference.py` follow the RFC token.
- **Kernel/consumer/test fixes during the turn (9)**: one kernel compile fix
  (`PolyLine`: `if (result and 1)` is `Int`, not `Boolean` → `!= 0`); five
  consumer-side fixes (`ByteArray.repeat` does not exist → manual loop; a
  tab-unit case that was actually ragged; the largesize byte written at the
  wrong offset; an FOV epsilon that assumed 0 instead of 2.06e-6; an MP4
  fixture offset); three pure-Python test fixes (polyline length, RTSP regex
  anchor, MP4 type offset). Only the first touched a kernel, and it was a
  compile error, not a behaviour change.
- **Census-tool fixes (2)**: the deepen script is now idempotent (re-running it
  cannot duplicate a sentence or move a hash silently) and takes a declared
  allowed-stale set instead of failing on its own re-hash.
- **Census**: a declared allowed-stale set (3 paths) is the only way the census
  accepts a changed hash; anything else fails loudly.

## J. Stopping

Stop this turn when: lab PASS + census updated + audits regenerated + docs
committed + CI triaged with exact run ids. Otherwise return the strongest
verified artifact, the exact remaining gap, and the next highest-information
test. See [R37-RESULTS](R37-RESULTS.md).
