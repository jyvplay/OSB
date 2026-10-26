# R32 scope / acceptance contract — 10 executed kernels (pure-logic round)

Working branch `arena/350b91e4-osb`. Evaluation date 2026-10-22.
Fresh research: `research/r32-primary.md` (search synthesis, weaker than
fetched primaries — see scope note there).
Alternatives: [R32-ALTERNATIVES](R32-ALTERNATIVES.md) ·
Privacy PRD: [R32-PRIVACY-PRD](R32-PRIVACY-PRD.md) ·
Results: [R32-RESULTS](R32-RESULTS.md).

R32 authors NO Android code: 10 host-JVM kernels + 1 fixture + consumer +
reference + gates + docs. Three modules are deepened for the first time
(:feature:calc, :format:lok, :cast:castv2); seven gain a second kernel. Any
future wiring is an explicit unresolved interface, never a silent claim.

## A. Formal model (turn contract)

- Admissible objects: host-JVM Kotlin kernels (zero android imports);
  real fixtures where a format spec exists (CUE only this turn — every
  other kernel takes scalar/vector input, so inline vectors are honest);
  a JVM consumer; independent Python recomputation; static gates; docs.
  No authored Android files, no stubs, no fakes this turn.
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
  except documented subsets; tolerances: exact integer, string, and
  fraction equality; doubles exact only where round-trip guaranteed
  (geo parse/format).
- Adjacent problems NOT substituted: host pass != Android pass; parse !=
  compile; static touch-target scan != rendered proof; capability matrix !=
  Office rendering; LOAD validation != cast transport; argv split != shell
  execution; PIN policy != credential security; sweep partition != erasure
  proof; duration math != scheduling; geo parse != positioning; backoff
  counting != authentication; cue parse != audio decode.

## B. Outcome space

- H+: all 10 kernels execute green and CI compiles the kernel set.
- H-: a kernel is unimplementable within host-only bounds (would need
  Android/time/network) -> it must be cut, not faked. (Cut pre-writing:
  :app a fourth round (all honest surfaces Android-inline or speculative;
  windowing already owns codecs/streams); archive-2nd (ZipGuard already
  owns zip-slip + bomb guards); calc radix/format (ProgrammerParser,
  OutputRadix, decimal-bounded already cover it); FrameStats would
  duplicate FrameBudget's p95/jank. Deferred seconds kept for R33, not
  cut: E164Format, CellRef, SoapEnvelope, NoticeWindow, AnsiSgr, AspectFit,
  GlobMatch, WikiLink, ChunkPlan.)
- H∂: verdicts changing at a boundary (fraction 22/7 vs 311/99 at maxDen
  10/100, sweep keep/purge at exactly max age, lockout 3rd-free/4th-locked
  + free-at-expiry, duration week-exclusivity + H-M-S order, geo ±90/±180,
  cue INDEX 01 strict increase + track contiguity, argsplit empty-arg and
  comment edges, PIN mod-10 wrap runs, castmeta grammar edges, office
  unknown-probe) -> boundary checks mandatory. No H+ commitment until the
  lab prints PASS.

## C. Frontier (imported results, hypotheses attached)

- Best rational approximation (continued fractions, convergent +
  semiconvergent finish): FractionApprox; Double conversion is caller duty.
- Honest capability matrices (flags name existing surfaces, gates quoted):
  OfficeCapability; every edit flag false.
- Cast LOAD record validation (URL/MIME/title grammars): CastMediaMeta;
  escaping stays the documented open interface.
- Shell-ish argv splitting (quotes/escapes/comments, no expansion):
  ArgSplit; refuses unterminated shapes.
- PIN strength policy (length/runs/repeats/blocklist, all reasons):
  PinRules; storage and rate limiting outside.
- Retention partitioning (per-kind max ages, caller clock):
  RetentionSweep; unknown kinds refused, never silently kept or purged.
- RFC 5545 durations to seconds (weeks-alone, no calendrical units):
  ICalDuration; months/years refused.
- RFC 5870 geo URIs, strict subset (wgs84-only, unknown params refused):
  GeoUri; round-trip formatting.
- Caller-clock brute-force backoff (free failures, doubling, saturation):
  LockoutPolicy; no decay window, conservative by design.
- CUE sheet subset to absolute CD-frame cues: CueSheetParser; globals
  tolerated, INDEX 01 strictly increasing.

## D. Negative space (15 shapes that look like solutions but fail)

1. Kernel importing android.* that "passes" kotlinc but breaks the host/Android split.
2. Consumer asserting on production internals instead of behavior.
3. Fixture-free kernel tested only inline where a format spec exists (CUE has one).
4. Semiconvergent finish compared against the CF remainder instead of the target (caught by the pi pin; fixed).
5. k+1 semiconvergent presented as bounded (it always exceeds maxDen; compare k vs previous convergent instead — fixed pre-run).
6. Overflow-safe ordering violated (numerator before denominator check — fixed pre-run; q first).
7. Parser/data-class name clash (`object CueSheet` vs `data class CueSheet` — renamed to CueSheetParser).
8. Reference mirror disagreeing with the kernel on case (urlsplit lowercases schemes — fixed in the mirror).
9. Capability matrix claiming Office rendering (flags name other modules' surfaces; engines refused).
10. LOAD validation presented as cast transport (record only; transport future).
11. Sweep partition presented as erasure proof (verdicts only; deletion is caller duty).
12. Geo parse presented as positioning (string math only; no location access).
13. Backoff counting presented as authentication (attempt ledger only; credential checking outside).
14. Cue parse presented as audio decode (times only; decoding outside).
15. Static touch-target scan presented as rendered proof.

## E. Mechanism portfolio (10 kernels; each distinct)

| Kernel | Construction | Proved | Unresolved interface | Cheapest falsifier | Gap |
|---|---|---|---|---|---|
| FractionApprox | CF convergents + semiconvergent finish | Exact + best-with-bound on JVM | Calc display wiring | pi at maxDen 100 | Local (wiring future) |
| OfficeCapability | Closed matrix + gate lists | Rows + probe negatives on JVM | Every engine | Unknown format probe | Local (engines refused) |
| CastMediaMeta | URL/MIME/title grammars | Accept + refusal shapes on JVM | Transport + escaping | Params MIME, quote title | Local |
| ArgSplit | Quote/escape/comment splitter | Vectors + unterminated on JVM | Shell registry wiring | `""` mid-line | Local |
| PinRules | Length/run/repeat/blocklist rules | Policy matrix on JVM | Storage + element | 7890 wrap | Local |
| RetentionSweep | Age partition, caller clock | Boundary + refusals on JVM | Deletion execution | Age == max age | Local |
| ICalDuration | Manual dur-value scan | Values + 13 refusals on JVM | Scheduler wiring | P1W2D | Local |
| GeoUri | Strict geo subset + format | Parse + round-trip on JVM | Maps/positioning | crs=luna | Local |
| LockoutPolicy | Doubling backoff, saturation | Schedule + reset on JVM | Credential checking | 4th failure at 30s | Local |
| CueSheetParser | CUE subset to CD frames | Fixture + 14 refusals on JVM | Chapter/decode path | Equal INDEX 01s | Local |

## F. Artifacts

Proofs: none (no theorem claims). Executable designs: 10 kernels + consumer +
1 fixture + 6 Python tools. Reproducible experiments: host lab (receipted),
reference recomputation (stdlib limit_denominator + regex-grammar readers
deliberately distinct from the kernels), touch-target scan (223 sites, +0
delta expected), manifest-scope gate. Quantitative bounds: caps in every
KDoc. Causal model: none claimed.

## G. Second-order adversary (per-candidate attacks, run)

- FractionApprox: exact halves/eighths/integers/zero, pi at maxDen 10 and
  100 (22/7 vs 311/99), third, negative, sign normalization ×2, 4 refusals
  (maxDen 0/over, value over, zero den). Pins cross-checked against
  CPython limit_denominator before the run.
- OfficeCapability: pdf view-only with 6 gates, case/dot normalization,
  docx gated, txt view-only, sorted viewable, unknown-probe negative,
  license gate named, 3 id refusals.
- CastMediaMeta: valid record, query kept, 12 refusals (ftp, bare host,
  space, upper scheme, MIME params/upper/bare, title empty/long/quote/
  backslash/control).
- ArgSplit: basic/whitespace, both quote kinds + cross-literals, escaped
  space/dquote, empty args ×2, comments ×3, empty line, quote-join,
  6 refusals (unterminated ×2, trailing backslash ×2, caps ×2).
- PinRules: 2 accepts, 14 policy vectors (runs incl. mod-10 wrap ×3,
  all-same, halves, blocklist, lengths, empty, non-digit single reason).
- RetentionSweep: boundary-inclusive keep, strictly-older purge, empty
  sweep, 7 refusals (unknown kind, future stamp, negative now, empty/zero
  table, bad id, over records).
- ICalDuration: week/full/negative/zero/plus/hm/zero-day values, 13
  refusals (month/year, bare P/PT/PDT, order, dup part, week-plus, no P,
  space, 10 digits, over cap, empty).
- GeoUri: basic/upper+alt/crs/unc/both-params parses, format basic,
  full round-trip, 12 refusals (no scheme, lat/lon over, 1/4 coords, bad
  crs, unknown param, bare param, neg unc, NaN, space, format range).
- LockoutPolicy: 3 free failures, 4th locks 30s, locked-before/free-at-
  expiry, 5th 60s, 10-failure 32min saturation + stability, success
  reset, fresh unlocked, 2 refusals (negative now, bad state).
- CueSheetParser: 3-track fixture incl. global tags, INDEX 00 skip, bare
  track, 14 refusals (missing FILE, TRACK-before-FILE, first-not-1,
  noncontiguous, missing/dup INDEX 01, nonincreasing, bad ff/ss, unknown
  + lowercase keywords, unquoted title, no tracks, dup FILE).

## H. Verification bindings

- kotlinc-jvm 2.2.21 separate compiles (10 jars, dependencies explicit) + JRE 17.0.9 consumer run.
- Independent Python recomputation (stdlib limit_denominator, urllib URL
  verdicts, regex duration + CUE grammars — deliberately distinct from
  the kernels' manual scans).
- Static gates: hygiene (imports), touch-target detector (self-test + strict file + R28-baseline delta),
  android parse gate (unchanged 8 files), manifest-scope gate, layout-bounds + page-matrix self-tests.
- CI: exact-candidate compile receipt required; device failures triaged per-run
  (annotations API; logs unavailable — claim only what annotations show).
- Downgrades: kernels are host-only (Android behavior needs CI/device); touch scan
  is static-only; privacy rows without device runs are documented, not delivered
  (RetentionSweep's deletion execution excepted as labeled future work).

## I. Repair policy

Repaired candidates inherit no trust: full lab re-run + at least one new
attack on the patch. Pre-execution review fixes: FractionApprox overflow
ordering (q before p) + huge-quotient shortcut, KDoc cap honesty (32min,
not 1h), semiconvergent-vs-previous-convergent finish, CueSheet global
TITLE/PERFORMER tolerance. Post-failure repairs: (1) CueSheet
object/data-class name clash -> CueSheetParser (applied serially with grep
verification); (2) semi() compared against the CF remainder, not the target
-> captured target (caught by the pi-semiconvergent pin, which passed the
coarser pin by accident); (3) reference urlsplit scheme lowering -> raw-
string case check in the mirror (kernel correct); (4) reference CUE tuple
index slip (mirror-only); (5) tool-test TRACK regex missed indented fixture
lines (test-only; kernel trims).

## J. Stopping

Stop this turn when: lab PASS + census updated + audits regenerated + docs
committed + CI triaged with receipts. Otherwise return the strongest verified
artifact + exact gap + next test (see RESULTS).
