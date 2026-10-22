# R34 scope / acceptance contract — 10 executed kernels (pure-logic round)

Working branch `arena/350b91e4-osb`. Evaluation date 2026-10-22.
Fresh research: `research/r34-primary.md` (search synthesis, weaker than
fetched primaries — see scope note there).
Alternatives: [R34-ALTERNATIVES](R34-ALTERNATIVES.md) ·
Privacy PRD: [R34-PRIVACY-PRD](R34-PRIVACY-PRD.md) ·
Results: [R34-RESULTS](R34-RESULTS.md).

R34 authors NO Android code: 10 host-JVM kernels + 2 fixtures + consumer +
reference + gates + docs. All ten are second (or later) kernels — no
first-deepen surface survived the survey (:app cut a sixth time; two
deferred seconds cut as already-owned, both verbatim). Any future wiring is
an explicit unresolved interface, never a silent claim.

## A. Formal model (turn contract)

- Admissible objects: host-JVM Kotlin kernels (zero android imports);
  real fixtures where a format spec exists (structural JPEG + a real zlib
  stream this turn; pseudonym pins to a JVM=hmac interop check); a JVM
  consumer; independent Python recomputation; static gates; docs. No
  authored Android files, no stubs, no fakes this turn.
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
  except documented subsets; tolerances: exact integer, string, byte, and
  hex equality; one float variance pin via 1e-9 tolerance (documented
  non-exact); pseudonym pins to Python-hmac agreement exactly.
- Adjacent problems NOT substituted: host pass != Android pass; parse !=
  compile; static touch-target scan != rendered proof; stats summary !=
  statistical inference; extension table != content sniffing; ATX outline
  != full Markdown parse; lexical bracket match != syntax-aware matching;
  fault parse != SOAP stack; pipe split != shell execution; JPEG markers
  != image decode; zlib header != decompression; VTODO parse != task sync;
  pseudonym != anonymization.

## B. Outcome space

- H+: all 10 kernels execute green and CI compiles the kernel set.
- H-: a kernel is unimplementable within host-only bounds or duplicates an
  owned surface -> it must be cut, not faked. (Cut pre-writing: :app a
  sixth round (12/12 files import android/androidx, all honest surfaces
  Android-inline or speculative); GsmAlphabet (SmsSegments owns GSM-7
  classification via its [BASIC]/[EXT] tables verbatim); CacheKey
  (SafeNavigation.canonicalize owns strict canonicalization with query
  handling verbatim).)
- H∂: verdicts changing at a boundary (stats empty/single/mixed-sign sets,
  mime last-dot/case/unknown rules, outline fence open/close edges,
  bracket interleave/unbalanced edges, fault prefix/absence edges, pipe
  quote/escape/empty-stage edges, JPEG SOS-stop/truncation edges, zlib
  FCHECK/FDICT edges, VTODO range/fold/unknown-prop edges, pseudonym
  key/context/value edges) -> boundary checks mandatory. No H+
  commitment until the lab prints PASS.

## C. Frontier (imported results, hypotheses attached)

- Welford online mean/variance (cancellation-safe summaries): StatSummary;
  inference outside.
- Extension-to-MIME table (last-dot rule, null for unknown): MimeGuess;
  sniffing refused.
- ATX outline with fenced-code suppression: OutlineParse; Setext outside.
- Lexical bracket-mate lookup (documented string/comment blindness):
  BracketMatch; parsing outside.
- SOAP 1.1 fault + UPnPError detail extraction: SoapFault; no SOAP stack.
- Quote-aware pipeline splitting (empty stages refused): PipePlan.
- JPEG marker walk with SOF0/1/2 geometry, SOS stop: JpegSeg; RFC 10918
  structure only, entropy untouched.
- RFC 1950 header validation (FCHECK, FDICT refused): ZlibHead.
- Single-VTODO parse (folding, escapes, ranges): VTodoParse; RFC 5545
  task subset.
- HMAC-SHA256 pseudonyms with context separation: Pseudonym; keyed, not
  anonymous.

## D. Negative space (15 shapes that look like solutions but fail)

1. Kernel importing android.* that "passes" kotlinc but breaks the host/Android split.
2. Consumer asserting on production internals instead of behavior.
3. Fixture-free kernel tested only inline where a format spec exists (JPEG/zlib have real fixtures).
4. Welford presented as exact arithmetic (one variance pin honestly uses tolerance).
5. Extension table presented as content sniffing (names only; unknown returns null).
6. Fenced code headings leaking into the outline (suppressed; unclosed fence suppresses to EOF).
7. Bracket matcher assumed syntax-aware (purely lexical; interleave pin documents it).
8. Prefixed Fault close-tag found by first `</` (stops at `</faultcode>` — caught pre-run; regex open/close pair).
9. Bare-SOI input returning an empty walk (refused via done-flag; caught at runtime).
10. Pipe splitter ignoring quotes (quote-tracked; escapes preserved for the word-splitter).
11. JPEG walker interpreting post-SOS bytes (stops; entropy never read).
12. Zlib header accepted without FCHECK (enforced) or with FDICT silently skipped (refused).
13. VTODO unknown properties silently dropped (refused, mirroring VEventParse).
14. Pseudonym without a context label (domain separation mandatory; keyed, not anonymous).
15. Static touch-target scan presented as rendered proof.

## E. Mechanism portfolio (10 kernels; each distinct)

| Kernel | Construction | Proved | Unresolved interface | Cheapest falsifier | Gap |
|---|---|---|---|---|---|
| StatSummary | Welford + sorted median | Exact + tolerance pins on JVM | Stats UI | var(1,2,3,4) = 1.25 | Local |
| MimeGuess | Case-folded last-dot table | 26 entries + rules on JVM | Share-sheet wiring | archive.tar.gz | Local |
| OutlineParse | Fence-tracked ATX scan | Entries + suppressions on JVM | Outline UI | ``` fence | Local |
| BracketMatch | Depth scan from cursor | Mates + nulls on JVM | Editor highlight | `([)]` | Local |
| SoapFault | Substring fault walk | Fields + refuses on JVM | Renderer errors | s:Fault prefix | Local |
| PipePlan | Quote/escape splitter | Stages + refuses on JVM | Shell runner | `a \|\| b` | Local |
| JpegSeg | Marker walk, SOS stop | Fixture + mutations on JVM | Image decode | Bare SOI | Local |
| ZlibHead | CMF/FLG validation | Fixture + corruptions on JVM | Decompression | Flipped FCHECK | Local |
| VTodoParse | Unfold + dict parse | Fields + refuses on JVM | Task sync | LOCATION prop | Local |
| Pseudonym | HMAC-SHA256 truncate | JVM=hmac on JVM | Analytics wiring | Context swap | Local |

## F. Artifacts

Proofs: none (no theorem claims). Executable designs: 10 kernels + consumer +
2 fixtures + 6 Python tools. Reproducible experiments: host lab (receipted),
reference recomputation (statistics-module stats, mimetypes agreement,
regex outline, stack-scan brackets, ElementTree faults, split-repair pipes,
stdlib zlib roundtrip, JVM=hmac pseudonyms — deliberately distinct from the
kernels), touch-target scan (223 sites, +0 delta expected), manifest-scope
gate. Quantitative bounds: caps in every KDoc. Causal model: none claimed.

## G. Second-order adversary (per-candidate attacks, run)

- StatSummary: base-set 6 pins (count/sum/mean/var/minmax/median), single
  4 pins, 2 median variants, 5 Welford large-magnitude pins (mean exact,
  variance 1e-9 tolerance), 4 refusals (empty, NaN, ±inf).
- MimeGuess: 26 table pins, case fold, last-extension rule, 2 unknown
  nulls (xyz, bin), 4 refusals (blank, no ext, trailing dot, bad chars).
- OutlineParse: 4 entry pins (levels/titles/lines), empty-doc/blank-doc
  empties, unclosed-fence suppression, indented heading, tab separator.
- BracketMatch: paren fwd/bwd, 3 nesting pins, 2 interleave pins, adjacent,
  3 nulls (unbalanced open/close, mismatch), 3 refusals (OOB ×2,
  non-bracket).
- SoapFault: 4 field pins on prefixed envelope, unprefixed parse, 8
  refusals (no fault/code/string/detail/UPnPError/errcode/errdesc, bad
  errcode).
- PipePlan: single, 3-stage trim, quoted-pipe ×2 (literal), escaped pipe
  (kept for word-splitter), 7 refusals (leading/trailing/double pipe,
  blank, unterminated ×2, trailing backslash).
- JpegSeg: 5-marker sequence + offsets + lengths + 1×1 geometry pins on
  the 141-byte fixture, 5 refusals (no SOI, truncated, bad length,
  overrun, bad frame).
- ZlibHead: cinfo 7 / flevel 2 / hdrlen 2 pins on the real stream, 6
  refusals (short, FCHECK, non-deflate, window, FDICT, zero header).
- VTodoParse: 6 full-task pins, minimal nulls, folding, escapes, date-only
  DUE, 9 refusals (no BEGIN/END, missing UID/SUMMARY, bad STATUS/DUE,
  PRIORITY/PERCENT range, unknown prop).
- Pseudonym: determinism, length + hex shape, 3 separations (context,
  value, key), matches true/wrong/badlen, 5 refusals (short/long key,
  blank context, empty/oversized value).

## H. Verification bindings

- kotlinc-jvm 2.2.21 separate compiles (10 jars, one kernel each) + JRE
  17.0.9 consumer run.
- Independent Python recomputation (statistics module, mimetypes, regex
  outline, stack-scan brackets, ElementTree faults, split-repair pipes,
  stdlib zlib roundtrip, JVM=hmac pseudonym interop — deliberately
  distinct from the kernels).
- Static gates: hygiene (imports + SPDX), package-collision guard (R32
  lesson, kept), touch-target detector (self-test + strict file +
  R28-baseline delta), android parse gate (unchanged 8 files),
  manifest-scope gate, layout-bounds + page-matrix self-tests.
- CI: exact-candidate compile receipt required; device failures triaged per-run
  (annotations API; logs unavailable — claim only what annotations show).
- Downgrades: kernels are host-only (Android behavior needs CI/device); touch scan
  is static-only; privacy rows without device runs are documented, not delivered
  (pseudonym storage/wiring excepted as labeled future work).

## I. Repair policy

Repaired candidates inherit no trust: full lab re-run + at least one new
attack on the patch. Pre-execution review fixes: SoapFault prefixed-close
regex pair (first-`</` search would stop at `</faultcode>`). Post-failure
repairs: (1) JpegSeg bare-SOI returned an empty walk -> done-flag refusal
plus a dedicated truncation attack; (2) parallel same-file edits proved
lossy a fourth time (only last-per-file survived) -> re-applied atomically
in a single call with grep verification; (3) consumer-only slips (outline
tab test, bracket interleave index) fixed in tests, kernels correct; (4)
reference-only slips (mimetypes wav alias + tar.gz encoding layer,
ElementTree namespace-XPath quirk) fixed in Python.

## J. Stopping

Stop this turn when: lab PASS + census updated + audits regenerated + docs
committed + CI triaged with receipts. Otherwise return the strongest verified
artifact + exact gap + next test (see RESULTS).
