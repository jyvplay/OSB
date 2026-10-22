# R33 scope / acceptance contract — 10 executed kernels (pure-logic round)

Working branch `arena/350b91e4-osb`. Evaluation date 2026-10-22.
Fresh research: `research/r33-primary.md` (search synthesis, weaker than
fetched primaries — see scope note there).
Alternatives: [R33-ALTERNATIVES](R33-ALTERNATIVES.md) ·
Privacy PRD: [R33-PRIVACY-PRD](R33-PRIVACY-PRD.md) ·
Results: [R33-RESULTS](R33-RESULTS.md).

R33 authors NO Android code: 10 host-JVM kernels + 3 fixtures + consumer +
reference + gates + docs. All ten are second (or later) kernels — no
first-deepen surface survived the survey (:app cut a fifth time; six
deferred seconds cut as already-owned, two verbatim). Any future wiring is
an explicit unresolved interface, never a silent claim.

## A. Formal model (turn contract)

- Admissible objects: host-JVM Kotlin kernels (zero android imports);
  real fixtures where a format spec exists (BMP + 2 gzip members this
  turn; TOTP pins to published RFC vectors); a JVM consumer; independent
  Python recomputation; static gates; docs. No authored Android files, no
  stubs, no fakes this turn.
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
  code equality; TOTP pins to RFC 6238 vectors exactly.
- Adjacent problems NOT substituted: host pass != Android pass; parse !=
  compile; static touch-target scan != rendered proof; E.164 grammar !=
  numbering plan; SGR styling != terminal emulation; chunk ranges !=
  transfer; glob match != filesystem search; fuzzy score != ranking
  quality; vector compare != conflict resolution; hunk render != diff
  algorithm; BMP/gzip headers != decode; TOTP compute != authenticator
  security.

## B. Outcome space

- H+: all 10 kernels execute green and CI compiles the kernel set.
- H-: a kernel is unimplementable within host-only bounds or duplicates an
  owned surface -> it must be cut, not faked. (Cut pre-writing: :app a
  fifth round (all honest surfaces Android-inline or speculative);
  WikiLink (NoteLinks owns [[..]] refs); NoticeWindow (NoticeLedger owns
  retention/eviction); AspectFit (ViewportTransform owns FIT/CROP);
  CellRef (DecimalSheet owns A1 validation + letters→column,
  LiteralWorkbook owns column→letters); SoapEnvelope (UpnpParsing owns it
  verbatim); CronSpec (automation owns it verbatim).)
- H∂: verdicts changing at a boundary (E.164 digit counts 7/15 and
  00/trunk/CC paths, SGR extended-color truncation, chunk uneven tails,
  glob class edges incl. literal `]`/`-`, fuzzy bonus/gap arithmetic,
  clock BEFORE/CONCURRENT edges, hunk context-merge + empty-side starts,
  BMP top-down/RLE edges, gzip flag-walk ends, TOTP window edges) ->
  boundary checks mandatory. No H+ commitment until the lab prints PASS.

## C. Frontier (imported results, hypotheses attached)

- E.164 grammar without a numbering plan (separators, 00/+, trunk
  convention, 7..15 digits): E164Format; guessing refused.
- SGR styling subset (attributes, 8/16/256/truecolor, strict refuses):
  AnsiSgr; cursor control outside.
- Fixed-size range planning with resume subtraction: ChunkPlan.
- Linear-time glob matching (token DP, classes, escapes): GlobMatch;
  names only, no recursion.
- Greedy subsequence scoring (word/camel/adjacency/gap): FuzzyScore;
  alignment documented non-optimal.
- Vector-clock causality (increment/merge/compare, canonical):
  VectorClocks; overflow refused.
- Unified-hunk rendering (context merge, empty-side convention):
  DiffHunks; LineDiff order expected.
- BMP BITMAPINFOHEADER facts without pixel decode: BmpInspect.
- Gzip member-header walk (FNAME, verified FHCRC): GzipInspect; RFC 1952.
- RFC 6238 TOTP (base32, HMAC-SHA1, 30s, ±window): TotpCode; caller clock.

## D. Negative space (15 shapes that look like solutions but fail)

1. Kernel importing android.* that "passes" kotlinc but breaks the host/Android split.
2. Consumer asserting on production internals instead of behavior.
3. Fixture-free kernel tested only inline where a format spec exists (BMP/gzip have real fixtures).
4. Int literals compared against Long maps (caught at runtime; L-suffixes added).
5. Glob `]`-first treated as close instead of literal (POSIX violation — fixed pre-run).
6. Dead refusal checks that no input can reach (empty-class — removed, replaced by a reachable cap test).
7. Hunk renderer assuming line endings are stripped (LineDiff keeps them — stripped in render).
8. Misordered change runs rendered as-is without a documented order contract (KDoc now states the LineDiff-order expectation).
9. TOTP 7-digit pin derived by string-slicing instead of mod 10^7 (caught by hand-trace; fixed).
10. E.164 guessing a country for local numbers (refused; explicit default code only).
11. SGR unknown codes silently passed (refused; documented subset).
12. Fuzzy greedy alignment presented as optimal (documented non-optimal; pins match greedy).
13. Hunk render presented as the diff algorithm (LineDiff computes; hunks render).
14. Header parse presented as media decode (BMP/gzip headers only; payloads untouched).
15. Static touch-target scan presented as rendered proof.

## E. Mechanism portfolio (10 kernels; each distinct)

| Kernel | Construction | Proved | Unresolved interface | Cheapest falsifier | Gap |
|---|---|---|---|---|---|
| E164Format | Separator/00/trunk/CC grammar | Paths + refusals on JVM | Dialer/SMS wiring | Bare local number | Local (wiring future) |
| AnsiSgr | SGR state machine + strip | Styles + refuses on JVM | Terminal render | Code 2 (faint) | Local |
| ChunkPlan | Ceil-division ranges + resume | Tails + resume on JVM | Transport/hash | 10 bytes / 3 | Local |
| GlobMatch | Token DP over names | Classes/escapes on JVM | Search wiring | `[]]x` literal | Local |
| FuzzyScore | Greedy scored alignment | Exact pins on JVM | Palette ranking | fb/FooBar = 41 | Local |
| VectorClocks | Map increment/max/compare | Orders + overflow on JVM | Conflict resolution | {a:1} vs {b:1} | Local |
| DiffHunks | Context-merge + headers | Exact strings on JVM | Diff display | ctx0 replace | Local |
| BmpInspect | LE header walk | Fixture + mutations on JVM | Pixel decode | RLE compression | Local |
| GzipInspect | Flag-walked header + CRC | Fixtures + corruptions on JVM | Decompression | Flipped FHCRC | Local |
| TotpCode | HMAC-SHA1 + truncation | 6 RFC vectors on JVM | Storage/UI | t=59 → 94287082 | Local |

## F. Artifacts

Proofs: none (no theorem claims). Executable designs: 10 kernels + consumer +
3 fixtures + 6 Python tools. Reproducible experiments: host lab (receipted),
reference recomputation (recursive glob matcher + hmac TOTP + difflib
agreement, deliberately distinct from the kernels), touch-target scan (223
sites, +0 delta expected), manifest-scope gate. Quantitative bounds: caps in
every KDoc. Causal model: none claimed.

## G. Second-order adversary (per-candidate attacks, run)

- E164Format: pretty/dotted/00/trunk/no-trunk paths, digit floors/ceilings,
  9 refusals (letters, bare local, short/long, double/mid plus, bad CC,
  blank, leading-zero intl).
- AnsiSgr: 8 sequence-shape probes (incl. overflow/alpha/empty-param nulls),
  7 style applications (256, truecolor, bright, default-fg, unbold, reset),
  5 refusals (faint, truncated ×2, bad mode, empty), strip ×3 (SGR gone,
  CSI stays, trailing ESC).
- ChunkPlan: uneven/even/empty/single-short plans, resume partial/full,
  5 refusals (zero chunk, negative total, over 1M, done out of range, huge
  chunk).
- GlobMatch: 16 match/miss vectors (stars, ?, classes, ranges, negation,
  literal `]`/`-`, escapes, empties, `**`), 5 refusals (unterminated,
  reversed, trailing escape, long pattern/name).
- FuzzyScore: 4 exact pins (55/41/34/25), folded 33, empty 0, 2 nulls,
  1 cap refusal.
- VectorClocks: first/new-node ticks, merge max + zero-strip, 4 orders +
  empty-equal, 4 refusals (overflow, bad node, negative, 65th node).
- DiffHunks: LineDiff-order pin, exact replace/del/add strings at ctx3,
  ctx0 string, identical-empty, 2 context refusals. Uses REAL LineDiff
  output (documents group carries TextEncoding/TextDocument/LineDiff).
- BmpInspect: exact 2×2 fixture record, top-down synthetic variant, 7
  byte-mutations (magic/size/DIB/planes/bpp/RLE/width), short input.
- GzipInspect: exact FNAME + FHCRC fixture records, 8 corruptions (short,
  magic, CM, reserved bits, truncated FEXTRA, unterminated FNAME, FHCRC
  mismatch, no payload).
- TotpCode: all six RFC 6238 SHA-1 vectors at 8 digits, three 6-digit +
  one 7-digit derivations, window in/out + wrong-code, 7 refusals
  (lowercase, bad length, short key, negative instant, bad digits,
  window over, code shape).

## H. Verification bindings

- kotlinc-jvm 2.2.21 separate compiles (10 jars; documents group explicitly
  carries TextEncoding + TextDocument + LineDiff) + JRE 17.0.9 consumer run.
- Independent Python recomputation (recursive glob matcher, hmac TOTP over
  raw key bytes, difflib change-shape agreement — deliberately distinct
  from the kernels).
- Static gates: hygiene (imports), package-collision guard (R32 lesson,
  kept), touch-target detector (self-test + strict file + R28-baseline
  delta), android parse gate (unchanged 8 files), manifest-scope gate,
  layout-bounds + page-matrix self-tests.
- CI: exact-candidate compile receipt required; device failures triaged per-run
  (annotations API; logs unavailable — claim only what annotations show).
- Downgrades: kernels are host-only (Android behavior needs CI/device); touch scan
  is static-only; privacy rows without device runs are documented, not delivered
  (TOTP storage/UI excepted as labeled future work).

## I. Repair policy

Repaired candidates inherit no trust: full lab re-run + at least one new
attack on the patch. Pre-execution review fixes: SGR overflow-param null,
glob `^`-negation removal, glob `]`-literal add + dead-check removal, hunk
line-ending strip, TOTP/fuzzy pin corrections. Post-failure repairs: (1)
documents group missing TextBytes/TextEncoding closure -> group carries
TextEncoding + TextDocument + LineDiff (verified no further Text* refs);
(2) clock pins compared Int maps to Long maps -> L-suffixes; (3) parallel
same-file edits proved lossy a third time (only last-per-file survived) ->
re-applied serially with grep verification; (4) reference/test-only slips
(glob mirror parity, TOTP slice precedence) fixed in Python.

## J. Stopping

Stop this turn when: lab PASS + census updated + audits regenerated + docs
committed + CI triaged with receipts. Otherwise return the strongest verified
artifact + exact gap + next test (see RESULTS).
