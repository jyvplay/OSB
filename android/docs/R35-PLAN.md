# R35 scope / acceptance contract — 10 executed kernels (pure-logic round)

Working branch `arena/350b91e4-osb`. Evaluation date 2026-10-22.
Fresh research: `research/r35-primary.md` (search synthesis, weaker than
fetched primaries — see scope note there).
Alternatives: [R35-ALTERNATIVES](R35-ALTERNATIVES.md) ·
Privacy PRD: [R35-PRIVACY-PRD](R35-PRIVACY-PRD.md) ·
Results: [R35-RESULTS](R35-RESULTS.md).

R35 authors NO Android code: 10 host-JVM kernels + 1 fixture + consumer +
reference + gates + docs. All ten are second (or later) kernels — no
first-deepen surface survived the survey (:app cut a seventh time; three
deferred seconds cut as already-owned, one verbatim). Any future wiring is
an explicit unresolved interface, never a silent claim.

Round context: the sandbox was re-provisioned between R34 and R35 (local
branch reset to base, toolchain cache wiped). Recovery: fetched
origin/arena/350b91e4-osb at `0ab824b`, re-pointed the branch, verified a
byte-identical worktree (empty status), and re-bootstrapped the toolchain
to identical versions (kotlinc 2.2.21, JRE 17.0.9; R34 contracts re-pass
166/166 on the fresh toolchain before R35 work began).

## A. Formal model (turn contract)

- Admissible objects: host-JVM Kotlin kernels (zero android imports);
  real fixtures where a format spec exists (ID3v2 header + padding this
  turn); a JVM consumer; independent Python recomputation; static gates;
  docs. No authored Android files, no stubs, no fakes this turn.
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
  tile equality; two float EV pins via 1e-12 tolerance (documented
  non-exact).
- Adjacent problems NOT substituted: host pass != Android pass; parse !=
  compile; static touch-target scan != rendered proof; EV pair math !=
  ISO-aware metering; XOR split != key management; word diff != semantic
  diff; size+digest grouping != byte comparison; snippet windows != hit
  finding; ID3 header != tag/frame parse; tile coverage != tile fetching;
  vCard subset != contact sync; field masks != value redaction; indent
  votes != reformatting.

## B. Outcome space

- H+: all 10 kernels execute green and CI compiles the kernel set.
- H-: a kernel is unimplementable within host-only bounds or duplicates an
  owned surface -> it must be cut, not faked. (Cut pre-writing: :app a
  seventh round (12/12 files import android/androidx, all honest surfaces
  Android-inline or speculative); SrtCue (SubRip owns SubRip parsing
  verbatim); BackoffPlan (LockoutPolicy owns doubling-backoff math —
  adjacent, not identical, cut on spirit); UnitConvert (calc's
  Units.definition owns unit conversion). Spare held: TaskTally.)
- H∂: verdicts changing at a boundary (EV exact/tolerance edges, XOR
  length edges, word budget/matrix edges, digest-shape edges, snippet
  trim/offset edges, ID3 flag/syncsafe edges, tile cap/wrap edges, vCard
  subset edges, mask cap edges, indent tie/noise edges) -> boundary checks
  mandatory. No H+ commitment until the lab prints PASS.

## C. Frontier (imported results, hypotheses attached)

- Exposure equation EV = log2(N^2/t) with two solvers: EvShift; ISO
  outside.
- 2-of-2 XOR splitting under a caller mask: XorSplit; randomness caller
  duty.
- Bounded word LCS with line-diff walk order: WordDiff; tokenization
  caller duty.
- (Size, digest) grouping, singletons excluded: DupGroup; hashing caller
  duty.
- Ellipsis windows with rebased offsets: SnippetCut; matching caller duty.
- ID3v2.2/2.3/2.4 header with per-version flags: Id3Head; frames outside.
- Bbox tile enumeration over TileMath: TileCover; fetching outside.
- vCard 3.0/4.0 subset (FN/N/TEL/EMAIL, TYPE only): VCardParse; sync
  outside.
- Allowlist/denylist projection over flat records: FieldMask; nesting
  outside.
- Tab/space votes with GCD width, null on ambiguity: IndentDetect;
  reformatting outside.

## D. Negative space (15 shapes that look like solutions but fail)

1. Kernel importing android.* that "passes" kotlinc but breaks the host/Android split.
2. Consumer asserting on production internals instead of behavior.
3. Fixture-free kernel tested only inline where a format spec exists (ID3 has a real fixture).
4. EV math presented as ISO-aware metering (aperture/shutter pair only).
5. XOR split with a derived/counter "mask" (caller must supply uniform randomness).
6. Word diff that tokenizes (guessing word boundaries is caller duty).
7. Digest-only grouping (size is part of the key; digests are caller-hashed).
8. Snippet cutter that re-searches (offsets come from the caller match).
9. Reserved ID3 flag bits silently masked (refused per version).
10. Syncsafe top-bit read as data (refused as corruption).
11. Antimeridian bbox silently half-covered (wrapped longitude refused).
12. Over-cap tile coverage silently truncated (refused, not clipped).
13. vCard unknown props/params silently dropped (refused, mirroring VTodoParse).
14. Field mask presented as value redaction (structural only; LogScrubber owns values).
15. Static touch-target scan presented as rendered proof.

## E. Mechanism portfolio (10 kernels; each distinct)

| Kernel | Construction | Proved | Unresolved interface | Cheapest falsifier | Gap |
|---|---|---|---|---|---|
| EvShift | EV equation + solvers | Exact + tolerance pins on JVM | Camera UI | ev(2, 0.5) = 3 | Local |
| XorSplit | Byte XOR split/combine | Round-trips + pins on JVM | Mask supply/escrow | Tampered share | Local |
| WordDiff | Bounded word LCS | Seqs + counts on JVM | Diff display | Tie order | Local |
| DupGroup | (Size,digest) group-by | Groups + order on JVM | Cleanup UI | Uppercase digest | Local |
| SnippetCut | Ellipsis windows | Texts + offsets on JVM | Results list | Radius 0 | Local |
| Id3Head | v2.2/3/4 header walk | Fixture + mutations on JVM | Frame parse | v2.2 flag bit | Local |
| TileCover | Corner tiles + range | Pins + refuses on JVM | Pack download | World z10 | Local |
| VCardParse | Unfold + subset dict | Fields + refuses on JVM | Contact sync | ORG prop | Local |
| FieldMask | keep/drop projection | Maps + caps on JVM | Pipeline wiring | Blank key | Local |
| IndentDetect | Votes + GCD width | Styles + nulls on JVM | Editor config | 40-space run | Local |

## F. Artifacts

Proofs: none (no theorem claims). Executable designs: 10 kernels + consumer +
1 fixture + 6 Python tools. Reproducible experiments: host lab (receipted),
reference recomputation (math.log2 EV, bytes XOR, difflib word agreement,
groupby dups, independent slicing, ID3 walk, slippy recomputation,
dict-based vCard, comprehension masks, math.gcd tallies — deliberately
distinct from the kernels), touch-target scan (223 sites, +0 delta
expected), manifest-scope gate. Quantitative bounds: caps in every KDoc.
Causal model: none claimed.

## G. Second-order adversary (per-candidate attacks, run)

- EvShift: 2 exact EV pins, 2 tolerance pins (f/2.8, f/4), exact
  shutter/aperture solvers, exact shift + round-trip, 6 refusals (zero/NaN
  aperture, zero/negative/infinite shutter, NaN EV).
- XorSplit: mask-share identity, blinded-byte pins, round-trip,
  commutativity, tamper mismatch, single-byte split + round-trip, 64 KiB
  cap OK, 5 refusals (empty secret, over cap, mask mismatch, empty share,
  share mismatch).
- WordDiff: identical seq + counts, replace seq/words/counts, pure
  insert/delete, both-empty, tie-prefers-remove, 2 refusals (word budget,
  matrix budget).
- DupGroup: two-group pins (members, triple, exclusion), digest order,
  empty-in-empty-out, 6 refusals (blank id, negative size, short digest,
  uppercase digest, duplicate id, over cap).
- SnippetCut: middle text + offsets + verify, head/end/whole fits, radius
  zero, 6 refusals (empty text, OOB start, inverted span, end over, big
  radius, negative radius).
- Id3Head: fixture version/flags/size/hdrlen, v2.3 flags, v2.2 + syncsafe
  257, v2.4 footer, 8 refusals (short, magic, major 1/5, v2.2 flags, v2.3
  reserved, v2.4 reserved, bad syncsafe).
- TileCover: z0 world tile, z1 pair size + row-major pins, z10 pair,
  point box, latitude clamp, 5 refusals (lon wrap, lat invert, lon OOB,
  zoom 20, over cap).
- VCardParse: 6 full-card pins, minimal nulls, folding, escapes, 9
  refusals (no BEGIN/END, missing VERSION/FN, bad VERSION, unknown
  prop/param, dup TYPE, empty TEL).
- FieldMask: keep basic/order/missing/empty, drop basic/empty/all, 4
  refusals (blank keep/drop keys, row cap, key cap).
- IndentDetect: tabs, spaces-4, GCD width, mixed null, no-indent null,
  blanks-abstain, single-run, noise-width null, tally counts + width,
  empty null (null-posture kernel: no throws by design).

## H. Verification bindings

- kotlinc-jvm 2.2.21 separate compiles (10 jars; maps group explicitly
  carries TileMath + TileCover) + JRE 17.0.9 consumer run.
- Independent Python recomputation (math.log2, bytes XOR, difflib,
  groupby, independent slicing, ID3 walk, slippy recompute, dict vCard,
  comprehension masks, math.gcd — deliberately distinct from the kernels).
- Static gates: hygiene (imports + SPDX), package-collision guard (sealed
  interface added to the decl pattern for IndentDetect.Style), touch-target
  detector (self-test + strict file + R28-baseline delta), android parse
  gate (unchanged 8 files), manifest-scope gate, layout-bounds +
  page-matrix self-tests.
- CI: exact-candidate compile receipt required; device failures triaged per-run
  (annotations API; logs unavailable — claim only what annotations show).
- Downgrades: kernels are host-only (Android behavior needs CI/device); touch scan
  is static-only; privacy rows without device runs are documented, not delivered
  (field-mask wiring excepted as labeled future work).

## I. Repair policy

Repaired candidates inherit no trust: full lab re-run + at least one new
attack on the patch. Pre-execution review fixes: snippet middle/tail pins
corrected by hand-trace (leading-space window, `ta theta` tail), empty-TEL
test given its missing END:VCARD. Post-failure repairs: none on kernels
(first-run green); one reference-only SyntaxError (unbalanced sorted
paren) fixed in Python.

## J. Stopping

Stop this turn when: lab PASS + census updated + audits regenerated + docs
committed + CI triaged with receipts. Otherwise return the strongest verified
artifact + exact gap + next test (see RESULTS).
