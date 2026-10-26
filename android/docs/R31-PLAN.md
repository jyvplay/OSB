# R31 scope / acceptance contract — 10 executed kernels (pure-logic round)

Working branch `arena/350b91e4-osb`. Evaluation date 2026-10-22.
Fresh research: `research/r31-primary.md` (search synthesis, weaker than
fetched primaries — see scope note there).
Alternatives: [R31-ALTERNATIVES](R31-ALTERNATIVES.md) ·
Privacy PRD: [R31-PRIVACY-PRD](R31-PRIVACY-PRD.md) ·
Results: [R31-RESULTS](R31-RESULTS.md).

R31 authors NO Android code: 10 host-JVM kernels + 4 fixtures + consumer +
reference + gates + docs. Nine modules are deepened for the first time;
:feature:browser gains a second kernel. Any future wiring is an explicit
unresolved interface, never a silent claim.

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
  except deliberate Unicode fixtures (café); tolerances: exact integer and
  string equality; doubles exact only where bit-exact (0.75), else no
  float comparisons at all.
- Adjacent problems NOT substituted: host pass != Android pass; parse !=
  compile; static touch-target scan != rendered proof; redaction shapes !=
  leak proof; decision table != measurement; query eval != index wiring;
  trailer parse != PDF rendering; UA exact-match != RFC product-token
  matching; clock math != sync.

## B. Outcome space

- H+: all 10 kernels execute green and CI compiles the kernel set.
- H-: a kernel is unimplementable within host-only bounds (would need
  Android/time/network) -> it must be cut, not faked. (Cut pre-writing:
  the lok slot surveyed weakest a third round (gated engine, no honest
  surface); calc is covered (Calculator/Programmer/Units/History); castv2
  framing is inline client code (extracting it would duplicate, rewiring
  it is untestable here) and its namespace grammar was thinner than the
  chosen ten; :app is a shell (kernels belong in feature modules);
  FrameStats would duplicate FrameBudget's p95/jank; MemoryBands would
  invent thresholds — PhaseLedger takes caller durations instead.)
- H∂: verdicts changing at a boundary (scrub digit-count 7/8, gate
  unknown-vs-absent, query depth 8/9 and terms 16/17, offset end vs OOB,
  manifest name/traversal rules, PDF xref exactness, PSD pad parity,
  codec hw-required, phase tie order, robots longest-prefix + ties) ->
  boundary checks mandatory. No H+ commitment until the lab prints PASS.

## C. Frontier (imported results, hypotheses attached)

- PII-shape heuristics (email/phone/token/path grammars): LogScrubber
  implements exactly the documented shapes and refuses nothing else.
- Deny-by-default with explicit unknown states (DP-10 in Models.kt):
  CapabilityGate; null never fabricates.
- Boolean retrieval (terms/phrases/NOT/OR/parens, NOT > AND > OR):
  SearchQuery; uppercase-OR and hyphen-negation are documented subsets.
- UTF-16 offset arithmetic with CRLF-as-content: TextOffsets.
- Canonical-digest manifests (sorted lines, SHA-256): VaultManifest.
- PDF 1.x trailer/xref (header, startxref, Size/Root, 20-byte entries):
  PdfTrailer; LF-only xref is a documented subset.
- PSD image-resource blocks (8BIM, Pascal name, even padding):
  PsdResources; payloads counted, never decoded.
- Encoder ranking over probe lists: CodecPreference (selection only).
- Caller-clock phase accounting: PhaseLedger (no clock reads).
- robots.txt RFC 9309 shape (groups, longest-match, tie-Allow):
  RobotsRules; exact-UA selection is a documented subset (product-token
  matching refused).

## D. Negative space (15 shapes that look like solutions but fail)

1. Kernel importing android.* that "passes" kotlinc but breaks the host/Android split.
2. Consumer asserting on production internals instead of behavior.
3. Fixture-free kernel tested only inline where a format spec exists (PDF/PSD/robots/scrub).
4. Scrubber fixture wording that self-trips the bearer grammar ("bearer header" did).
5. Redaction presented as a leak proof (shapes only; collection policy untouched).
6. Gate treating unknown facts as zeros (null denies with "unknown" reasons).
7. Query tokenizer disagreeing with the parser on Unicode (fixed pre-run; pinned by café).
8. Digest hex formatted without masking negative bytes (fixed pre-run; `%02x` gotcha).
9. U32 size arithmetic overflowing into a false bounds pass (fixed pre-run; subtract form).
10. PDF renderer claims from a trailer reader (locating the root is where it stops).
11. PSD payload decode claims from a header walk (data counted, never decoded).
12. RFC 9309 product-token UA matching claimed from exact-match (refused; pinned by fallback test).
13. Codec selection presented as encode proof (ranking only; no MediaCodec here).
14. Phase ledger reading a clock (caller durations only; deterministic by construction).
15. Static touch-target scan presented as rendered proof.

## E. Mechanism portfolio (10 kernels; each distinct)

| Kernel | Construction | Proved | Unresolved interface | Cheapest falsifier | Gap |
|---|---|---|---|---|---|
| LogScrubber | 5-shape regex redaction + idempotence | Fixture + edges on JVM | Report-share wiring | 7-digit run, no-TLD host | Local (wiring future) |
| CapabilityGate | Deny-default decision table | Vectors + unknowns on JVM | Probe mapping (caller) | Null ram + need | Local |
| SearchQuery | Recursive descent + self-eval | Grammar + semantics on JVM | LocalIndex wiring | a OR b c shape | Local |
| TextOffsets | Starts/line-col/word runs | Vectors + CRLF on JVM | Editor wiring | Offset==length | Local |
| VaultManifest | Validated items + canon digest | Caps + order-free digest on JVM | Envelope pairing | a/b name, upper sha | Local |
| PdfTrailer | Header/trailer/xref subset | Fixture + corruption on JVM | Object/render path | CRLF entries | Local |
| PsdResources | 8BIM block walk + pads | Fixture + truncation on JVM | Engine consumption | Odd-size pad cut | Local |
| CodecPreference | HW-first ranking + reasons | Selection table on JVM | Probe + MediaCodec | HW-required miss | Local |
| PhaseLedger | Caller-ms ledger + shares | Totals/ties on JVM | Instrumentation | Dup phase | Local |
| RobotsRules | Group parse + longest-match | Fixture + ties on JVM | Fetcher wiring | /p allow+disallow | Local |

## F. Artifacts

Proofs: none (no theorem claims). Executable designs: 10 kernels + consumer +
4 fixtures + 6 Python tools. Reproducible experiments: host lab (receipted),
reference recomputation, touch-target scan (223 sites, +0 delta expected),
manifest-scope gate. Quantitative bounds: caps in every KDoc. Causal model:
none claimed.

## G. Second-order adversary (per-candidate attacks, run)

- LogScrubber: exact fixture scrub, sensitivity flags, idempotence, no-TLD host,
  7-digit run, digit-run inside email, token `&` stop, empty text, over-cap input.
- CapabilityGate: full allow, sdk/sdk-25 edges, encoder unknown/missing/case, ram
  unknown/short, freeform unknown/absent, bad minSdk, bad ram need.
- SearchQuery: lowercase normalization, AND/OR/NOT/phrase/paren hit+miss,
  precedence SHAPE equality, lowercase-or-as-term, hyphen-negation shape, unicode
  café, 12 refusal shapes (empty/blank/group/unbalanced/phrase/NOT/trailing/caps/depth).
- TextOffsets: starts incl. empty/trailing-newline, line/col corners, clamp to
  newline and to end, word mid/space/end/empty/underscore-digit, CRLF starts and
  CR-as-content, 5 refusals (OOB ×3, negative, over-cap).
- VaultManifest: totals, order-independent digest, digest shape + mutation,
  remove, 10 refusals (dup/bad id ×3, slash/empty/long name, negative bytes,
  short/upper sha, unknown/bad remove).
- PdfTrailer: version/size/root/startxref exactness, 4 entries incl. free head
  and computed offsets, 7 refusals (header/EOF/startxref/size/root/offset/CRLF).
- PsdResources: exact 2-block fixture, empty section, 6 refusals (signature,
  truncated id/name, non-ASCII name, size overrun, cut data pad).
- CodecPreference: hw-first, hw-required null, software fallback, case folding,
  empty probe null, empty candidates refusal.
- PhaseLedger: totals, exact 0.75 share, missing/empty shares, slowest + tie +
  empty, 5 refusals (dup/bad name/negative/over-ms/65th phase).
- RobotsRules: 2-group fixture shape, star/named allow matrices, longest-match,
  versioned-UA-star-fallback, empty-Disallow, unknown-line skipping, group
  split/join, case folding, tie-Allow, 3 cap refusals.

## H. Verification bindings

- kotlinc-jvm 2.2.21 separate compiles (10 jars, dependencies explicit) + JRE 17.0.9 consumer run.
- Independent Python recomputation (all 10 kernel logics, shunting-yard query
  evaluator deliberately distinct from the kernel's recursive descent).
- Static gates: hygiene (imports), touch-target detector (self-test + strict file + R28-baseline delta),
  android parse gate (unchanged 8 files), manifest-scope gate, layout-bounds + page-matrix self-tests.
- CI: exact-candidate compile receipt required; device failures triaged per-run
  (annotations API; logs unavailable — claim only what annotations show).
- Downgrades: kernels are host-only (Android behavior needs CI/device); touch scan
  is static-only; privacy rows without device runs are documented, not delivered
  (LogScrubber's wiring excepted as labeled future work).

## I. Repair policy

Repaired candidates inherit no trust: full lab re-run + at least one new
attack on the patch. Pre-execution review fixes: SearchQuery Unicode
tokenizer, VaultManifest hex masking, PsdResources overflow-safe bound,
TextOffsets dead-code removal, exact-UA + hyphen-negation KDoc honesty.
Post-failure repairs: (1) PdfTrailer object/data-class name clash ->
PdfTrailerInfo (parallel same-file edits proved lossy again; applied
serially); (2) runner SPDX assert tripped on pre-existing Models.kt ->
SPDX stays in tool tests per R30 precedent; (3) scrub fixture "bearer
header" self-tripped the bearer grammar -> reworded to "auth header"
(kernel behavior correct per its grammar); (4) checks arithmetic: toOffset
clamp target 10 not 9, OOB probes at 11 not 10 (end positions are valid).

## J. Stopping

Stop this turn when: lab PASS + census updated + audits regenerated + docs
committed + CI triaged with receipts. Otherwise return the strongest verified
artifact + exact gap + next test (see RESULTS).
