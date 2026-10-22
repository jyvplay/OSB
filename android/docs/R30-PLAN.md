# R30 scope / acceptance contract — 10 executed kernels (pure-logic round)

Working branch `arena/350b91e4-osb`. Evaluation date 2026-10-22.
Fresh research: `research/r30-primary.md` (search synthesis, weaker than
fetched primaries — see scope note there).
Alternatives: [R30-ALTERNATIVES](R30-ALTERNATIVES.md) ·
Privacy PRD: [R30-PRIVACY-PRD](R30-PRIVACY-PRD.md) ·
Results: [R30-RESULTS](R30-RESULTS.md).

R30 authors NO Android code and NO authored-support store: 10 host-JVM
kernels + 4 fixtures + consumer + reference + gates + docs. Any future
wiring (settings patch path, camera zoom, backup tar writer) is an
explicit unresolved interface, never a silent claim.

## A. Formal model (turn contract)

- Admissible objects: host-JVM Kotlin kernels (zero android imports);
  real fixtures; a JVM consumer; independent Python recomputation; static
  gates; docs. No authored Android files, no stubs, no fakes this turn.
- Information/access model: this sandbox has kotlinc/JVM + Python, no SDK,
  no emulator, no log/blob download from CI (annotations API only). Anything
  needing Android is refused, never claimed executed.
- Resource counted: NEW executed assertions + modules deepened with new
  executed kernels (absolute + % of 50). Prior evidence never inherited by
  new code; repaired code inherits no trust (re-run everything).
- Success: 10/10 kernels green on host + reference agreement + 0 android
  imports in kernels + syntax/touch/manifest gates green + CI compile
  receipt + exact-candidate triage of every CI failure. Failure: any gate
  red without a same-turn repair + receipt.
- Parameter regime: bounds in each kernel (caps in KDoc); ASCII grammars
  except deliberate GSM-7/é fixtures; tolerances: exact integer equality;
  doubles exact only where bit-exact, else 1e-6/1e-9 windows.
- Adjacent problems NOT substituted: host pass != Android pass; parse !=
  compile; static touch-target scan != rendered proof; counts-only SMS math
  != sending; header parse != archive extraction; clock math != sync.

## B. Outcome space

- H+: all 10 kernels execute green and CI compiles the kernel set.
- H-: a kernel is unimplementable within host-only bounds (would need
  Android/time/network) -> it must be cut, not faked. (Cut pre-writing:
  the lok slot surveyed weakest and was dropped for :core:model; perf
  stays cut since FrameBudget/ThermalPolicy exist; FTS; crypto; camera
  sensor access.)
- H∂: verdicts changing at a boundary (timecode day edges, Lamport
  overflow, GIF truncated trailer, tar checksum, zoom clamp, SMS
  160/153/161 edges, density 179/180 + 279/280 + touch override) ->
  boundary checks mandatory. No H+ commitment until the lab prints PASS.

## C. Frontier (imported results, hypotheses attached)

- RTSP RFC 2326 framing subset (request line, CSeq, Content-Length,
  SDP body): RtspMessage implements this subset and refuses the rest.
- Timecode HH:MM:SS:FF convention with fps-gated frame field: Timecode.
- RFC 5545 VEVENT subset (CRLF framing, folding, text escapes, UTC
  stamp instants): VEventParse; recurrence/timezones refused.
- Lamport 1978 causal clock (tick, receive max-plus-one): LamportClock.
- GIF89a logical-screen/descriptor/extension/trailer walk: GifInfo
  (structure only; LZW decode refused).
- POSIX ustar header (octal fields, checksum, magic/version 00,
  name+prefix join): TarHeader (headers only; extraction refused).
- GSM 03.38 7-bit alphabet + extension escapes + UCS-2 fallback with
  160/153/70/67 budgets: SmsSegments (counts only; sending refused).
- Android density ladder reviewed against the in-tree DensityProfile
  tiers: DensitySelect (selection only; layout/assertion proof refused).

## D. Negative space (15 shapes that look like solutions but fail)

1. Kernel importing android.* that "passes" kotlinc but breaks the host/Android split.
2. Consumer asserting on production internals instead of behavior.
3. Fixture-free kernel tested only inline where a format spec exists (RTSP/ICS/GIF/tar).
4. Lamport clock drifting on wall time (pure counters; overflow refused, never wrapped).
5. Timecode claiming drop-frame or frame-rate conversion (neither implemented).
6. VEVENT parser accepting bare-LF or missing DTEND-after-DTSTART (both refused).
7. RTSP parser splitting headers on bare LF or ignoring Content-Length (both refused).
8. GIF walker decoding pixels or guessing past a bad trailer (refused: structure only).
9. Tar reader extracting members or skipping checksum (refused: headers only, verified).
10. SMS math sending, hitting the radio, or needing READ_SMS (counts only; no permission).
11. Zoom ratio presented as optical/camera proof (UI math only; sensor pipeline untouched).
12. Density selection presented as layout/render proof (tier choice only).
13. Patch-scope permits() presented as a settings write (admission only; no apply path).
14. GSM alphabet with duplicated or overlapping codepoints (verified 127+9, overlap none).
15. Static touch-target scan presented as rendered proof.

## E. Mechanism portfolio (10 kernels; each distinct)

| Kernel | Construction | Proved | Unresolved interface | Cheapest falsifier | Gap |
|---|---|---|---|---|---|
| RtspMessage | Strict request parse + canonical serialize | Framing + round-trip on JVM | AirPlay transport/session | Bare-LF body, missing CSeq | Local (transport future) |
| Timecode | ms to/from HH:MM:SS:FF, fps gate | Vectors + day edges on JVM | Recorder/encoder wiring | fps-0, 24:00:00:00 | Local |
| VEventParse | Unfold + unescape + stamp instants | Fixture + refusals on JVM | Calendar/import wiring | Bare LF, DTEND<DTSTART | Local |
| LamportClock | Tick/receive/max+1, overflow refusal | Causal sequence on JVM | Channel/peer wiring | Receive-at-MAX | Local |
| GifInfo | Screen walk + frame count, trailer exact | Fixture + truncation on JVM | Decode/render wiring | Missing 0x3B | Local |
| PatchScope | Exact/prefix permits + partition | Scope table on JVM | Settings apply path | appearancex leak | Local |
| TarHeader | Octal/checksum/magic parse + join | Fixture + corruption on JVM | Extractor/creator | Flipped checksum byte | Local |
| ZoomLadder | Half-stop ladder 1x-16x | Ratio/index/clamp on JVM | Camera/UI wiring | indexOf(0.5x) | Local |
| SmsSegments | Alphabet classify + septet + segments | Edges + classes on JVM | Telephony (never in-app) | 161-char GSM text | Local (counts only) |
| DensitySelect | dpi+touch tier select vs ladder | Cuts + override on JVM | Layout/render proof | 179/180, touch@120dpi | Local |

## F. Artifacts

Proofs: none (no theorem claims). Executable designs: 10 kernels + consumer +
4 fixtures + 6 Python tools. Reproducible experiments: host lab (receipted),
reference recomputation, touch-target scan (223 sites, +0 delta expected),
manifest-scope gate. Quantitative bounds: caps in every KDoc. Causal model:
none claimed.

## G. Second-order adversary (per-candidate attacks, run)

- RtspMessage: ANNOUNCE fixture framing, lowercase method, RTSP/2.0, missing CSeq,
  bad Content-Length, body over/under-length, bare-LF header, bare-LF body, non-ASCII
  line, serialize refusal of bad method/non-ASCII, exact-bytes round-trip.
- Timecode: 0@25, 1040@25, 3661000@24, 86399999@30, fps 0/61, negative ms, day+1ms,
  malformed strings (7 shapes), frame>=fps, hour 24, format/parse round-trip.
- VEventParse: 6-prop fixture (unfold+unescape, 125-char summary), missing BEGIN/END,
  missing UID/DTSTAMP/VEVENT, bad stamp shape, leap-2026 honesty, DTEND==DTSTART,
  bad escape, double backslash, missing colon, comma/semicolon/newline unescape.
- LamportClock: tick sequence, receive-max+1, receive-below (no bonus), zero/negative
  remote, read-only time(), tick-at-MAX and receive-at-MAX overflow refusals.
- GifInfo: 2-frame fixture walk, bad magic, short header, truncated global table,
  truncated descriptor/image/ext-trailer, image-before-screen, unknown block,
  multi-sub-block skip, trailer exactness, too-short record, single-frame no-table.
- PatchScope: empty scope grammar, bad prefix/exact shapes, exact hit, descendant hit,
  prefix boundary (appearancex), disjoint miss, deep nesting, case sensitivity,
  empty diffs, full allow/refuse partition incl. unchanged-key exclusion.
- TarHeader: 2-entry fixture (names/sizes/mode/mtime/type), short record, bad checksum,
  GNU magic (ustar-space), bad version digit, dir typeflag, non-octal size, size cap,
  prefix join, prefix overflow, hard size-cap boundary.
- ZoomLadder: ratio 0/1/2/8/odd, negative/over-16 indices, nearest-index rounding,
  half-step tie, below-min/above-max clamps, min/max/step accessors, float windows.
- SmsSegments: GSM basic/extended/empty classes, emoji/dash/CJK UCS-2, ext-as-2-septets,
  160/161 and 153x2/306/307 edges, UCS-2 70/71 and 67x2/134/135 edges, max-length cap,
  empty-text refusal, GSM alphabet 127+9 disjoint audit.
- DensitySelect: 179/180 and 279/280 cuts, touch override at 120dpi, negative/zero dpi,
  huge dpi, all-tier coverage, threshold-order audit vs DensityProfile ladder.

## H. Verification bindings

- kotlinc-jvm 2.2.21 separate compiles (10 jars, dependencies explicit) + JRE 17.0.9 consumer run.
- Independent Python recomputation (all 10 kernel logics).
- Static gates: hygiene (imports), touch-target detector (self-test + strict file + R28-baseline delta),
  android parse gate (unchanged 8 files), manifest-scope gate, layout-bounds + page-matrix self-tests.
- CI: exact-candidate compile receipt required; device failures triaged per-run
  (annotations API; logs unavailable — claim only what annotations show).
- Downgrades: kernels are host-only (Android behavior needs CI/device); touch scan
  is static-only; privacy rows without device runs are documented, not delivered.

## I. Repair policy

Repaired candidates inherit no trust: full lab re-run + at least one new
attack on the patch. Pre-execution review fixes (before first green):
RtspMessage bare-LF body walk + serialize printable-ASCII check, VEventParse
dead-require removal. Post-failure repairs: (1) TarHeader ustar version
offset 262/263 -> 263/264 after the fixture refused (off-by-one, new
bad-version-digit attack added); (2) runner group gained the Models.kt
dependency DensitySelect needs; (3) fixture Content-Length corrected
129 -> 135 real body bytes; (4) event.ics backslash hygiene re-verified at
byte level (valid `\\` pair; decoded summary pinned at 125 chars); (5) checks
tightened to grammar-valid inputs (patch-scope boundary key) and exact
golden vectors (SMS/timecode/tar).

## J. Stopping

Stop this turn when: lab PASS + census updated + audits regenerated + docs
committed + CI triaged with receipts. Otherwise return the strongest verified
artifact + exact gap + next test (see RESULTS).
