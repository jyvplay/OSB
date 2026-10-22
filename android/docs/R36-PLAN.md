# R36 scope / acceptance contract — 12 executed kernels (pure-logic round)

Working branch `arena/350b91e4-osb`. Evaluation date 2026-10-22.
Fresh research: `research/r36-primary.md` (search synthesis, weaker than
fetched primaries — see scope note there).
Alternatives: [R36-ALTERNATIVES](R36-ALTERNATIVES.md) ·
Privacy PRD: [R36-PRIVACY-PRD](R36-PRIVACY-PRD.md) ·
Results: [R36-RESULTS](R36-RESULTS.md).

R36 authors NO Android code: 12 host-JVM kernels + 2 fixtures + consumer +
reference + gates + docs. All twelve are second (or later) kernels — no
first-deepen surface survived the survey (:app cut an eighth time; one
deferred second cut as already-owned, verbatim). Any future wiring is
an explicit unresolved interface, never a silent claim.

Round context: the sandbox was re-provisioned between R35 and R36 (local
branch reset to base, worktree dirty, toolchain cache wiped). Recovery:
fetched origin/arena/350b91e4-osb at `408c39f`, re-pointed the branch
(mixed reset; nothing tracked discarded), verified a byte-identical
worktree (empty status), and re-bootstrapped the toolchain to identical
versions (kotlinc 2.2.21, JRE 17.0.9; R35 contracts re-pass 129/129 on
the fresh toolchain before R36 work began).

## A. Formal model (turn contract)

- Admissible objects: host-JVM Kotlin kernels (zero android imports);
  real fixtures where a format spec exists (WAV + TIFF headers this
  turn); a JVM consumer; independent Python recomputation; static gates;
  docs. No authored Android files, no stubs, no fakes this turn.
- Information/access model: this sandbox has kotlinc/JVM + Python, no SDK,
  no emulator, no log/blob download from CI (annotations API only). Anything
  needing Android is refused, never claimed executed.
- Resource counted: NEW executed assertions + modules deepened with new
  executed kernels (absolute + % of 50). Prior evidence never inherited by
  new code; repaired code inherits no trust (re-run everything).
- Success: 12/12 kernels green on host + reference agreement + 0 android
  imports in kernels + syntax/touch/manifest gates green + CI compile
  receipt + exact-candidate triage of every CI failure. Failure: any gate
  red without a same-turn repair + receipt.
- Parameter regime: bounds in each kernel (caps in KDoc); ASCII grammars
  except documented subsets; tolerances: exact integer, string, byte, and
  transition equality; float pins via documented tolerances (mpp/DoF/entropy
  non-exact; 0.1+0.2 and exact entropies pinned bit-for-bit).
- Adjacent problems NOT substituted: host pass != Android pass; parse !=
  compile; static touch-target scan != rendered proof; RPN eval !=
  expression parsing; clash names != filesystem truth; greedy wrap !=
  typesetting; redirect plan != execution; state machine != protocol
  conformance; largest-fitting bar != exact fit; WAV header != audio
  decode; TIFF header != IFD parse; k-anonymity audit != anonymization;
  window counting != authentication; thin-lens DoF != optical truth;
  Shannon entropy != randomness proof.

## B. Outcome space

- H+: all 12 kernels execute green and CI compiles the kernel set.
- H-: a kernel is unimplementable within host-only bounds or duplicates an
  owned surface -> it must be cut, not faked. (Cut pre-writing: :app an
  eighth round (12/12 files import android/androidx, all honest surfaces
  Android-inline or speculative); DlnaDuration (DidlLite.validDuration
  owns strict H:MM:SS duration grammar verbatim). Spares held unbuilt:
  PasteWrap, TaskTally, RtspReason, SlugHead, XzHead, SizeBucket.)
- H∂: verdicts changing at a boundary (RPN stack-depth edges, clash
  gap-fill edges, wrap width edges, redirect dup-stream edges, Cast
  illegal-cell edges, bar fit edges, WAV geometry-mismatch edges, TIFF
  offset-parity edges, k-anonymity singleton edges, window half-open
  edges, DoF hyperfocal edges, entropy exact/skewed edges) -> boundary
  checks mandatory. No H+ commitment until the lab prints PASS.

## C. Frontier (imported results, hypotheses attached)

- RPN stack evaluation over finite doubles (+ - * / neg sqrt): RpnEval;
  tokenization caller duty.
- Copy-name disambiguation with suffix reuse + lowest-free gap fill:
  NameClash; case folding filesystem duty.
- Greedy word wrap with space collapsing: WrapCol; hyphenation/width
  caller duty.
- Shell redirect planning (< > >> 2>, one per stream): RedirPlan;
  execution outside.
- CastV2 player-state machine (4 states, 11 legal moves): CastState;
  message validation outside.
- Web-Mercator ground resolution + largest-fitting 1/2/5 bar: ScaleBar;
  rounding caller policy.
- Canonical WAV header with geometry cross-checks: WavHead; samples
  outside.
- TIFF header, either byte order, magic 42: TiffHead; IFD tags outside.
- k-anonymity auditing over caller rows: KAnon; quasi-identifier choice
  caller duty.
- Sliding-window attempt limiting, half-open window: AttemptWindow;
  credential checking outside.
- Thin-lens DoF limits via the hyperfocal (+f form): DofNear;
  diffraction/breathing outside.
- Shannon entropy in bits per symbol over raw bytes: ShannonBits;
  min-entropy outside.

## D. Negative space (16 shapes that look like solutions but fail)

1. Kernel importing android.* that "passes" kotlinc but breaks the host/Android split.
2. Fixture-free kernel tested only inline where a format spec exists (WAV and TIFF have real fixtures).
3. RPN evaluation presented as expression parsing (infix/tokenizing is caller duty).
4. Clash names presented as filesystem truth (case folding + normalization are fs duty).
5. Greedy wrap presented as typesetting (hyphenation, Unicode width, justification outside).
6. Redirect plan presented as execution (no fork/exec/redirection is performed).
7. State machine presented as Cast protocol conformance (message parsing/validation outside).
8. Largest-fitting bar presented as exact fit (the bar under-fills by design).
9. WAV header presented as audio decode (samples are never read).
10. TIFF header presented as IFD parsing (tags/entries outside).
11. k-anonymity audit presented as anonymization (audit only; no suppression/generalization performed).
12. Window counting presented as authentication (credential checking outside).
13. Thin-lens DoF presented as optical truth (diffraction, breathing, real-lens corrections outside).
14. Entropy presented as a randomness proof (distribution math only; min-entropy outside).
15. k = 1 audit presented as a privacy finding (vacuous pass, documented as such).
16. Static touch-target scan presented as rendered proof.

## E. Mechanism portfolio (12 kernels; each distinct)

| Kernel | Construction | Proved | Unresolved interface | Cheapest falsifier | Gap |
|---|---|---|---|---|---|
| RpnEval | Stack machine, finite-only | Pins + classic + refuses on JVM | Calc UI | Leftover operand | Local |
| NameClash | Suffix reuse + gap fill | Names + order on JVM | Files copy flow | Gap reuse | Local |
| WrapCol | Greedy fill, collapse | Lines + refuses on JVM | Editor display | Overlong word | Local |
| RedirPlan | Token walk, one/stream | Plans + order on JVM | Shell runner | Dup stdout | Local |
| CastState | 4x7 transition table | 11 moves + refuses on JVM | Cast transport | Idle pause | Local |
| ScaleBar | mpp + fit loop | Pins + fits on JVM | Map overlay | 2e7 non-fit | Local |
| WavHead | Tag + geometry checks | Fixture + mutations on JVM | Sample decode | Bad blockAlign | Local |
| TiffHead | Order + magic + offset | Fixture + orders on JVM | IFD walk | Odd offset | Local |
| KAnon | Group counts, ordered viol | Groups + pins on JVM | Export pipeline | Singleton | Local |
| AttemptWindow | Half-open count | Admits + denies on JVM | Auth wiring | Boundary t | Local |
| DofNear | Hyperfocal +f, null far | Pins + infinity on JVM | Lens UI | s == H | Local |
| ShannonBits | -sum p log2 p | Exacts + skew on JVM | Vault meter | Empty input | Local |

## F. Artifacts

Proofs: none (no theorem claims). Executable designs: 12 kernels + consumer +
2 fixtures + 7 Python tools. Reproducible experiments: host lab (receipted),
reference recomputation (float RPN, loop gap-fill, greedy wrap walk, token
redir walk, dict-table Cast, slippy recompute, struct WAV/TIFF re-parse,
Counter k-anonymity, comprehension windows, formula DoF, math.log2 entropy —
deliberately distinct from the kernels), touch-target scan (223 sites, +0
delta expected), manifest-scope gate. Quantitative bounds: caps in every
KDoc. Causal model: none claimed.

## G. Second-order adversary (per-candidate attacks, run)

- RpnEval: 11 pins (add, subdiv, muladd, sqrt, neg, div, 0.1+0.2
  bit-exact, neg literal, mixed, Wikipedia classic = 5, chain), 10
  refusals (div zero, underflow, unknown op, bad/NaN/Inf literal,
  leftover, sqrt neg, overflow, empty).
- NameClash: 10 pins (free, first/second clash, gap fill, no-ext,
  numbered-input base reuse, dotfile, multi-dot, case-sensitive,
  single-char), 1 refusal (blank).
- WrapCol: 9 pins (greedy, exact fit, single, width one, empty, blank,
  collapse, cascade, pairs), 4 refusals (overlong word, newline, width
  zero, width over cap).
- RedirPlan: 10 pins (plain, out argv/redir, append, stdin, stderr,
  multi argv/order, source-order kept, glued stays arg), 6 refusals
  (trailing op, op-as-target, dup stdout/stdin/stderr, empty target).
- CastState: 4 parses + all 11 legal moves pinned, 12 refusals (10
  illegal cells incl. every state's pause/play/load mirror, 2 bad
  parses).
- ScaleBar: 9 pins (equator z0/z1 exact, mpp 45/10, bar exact/two/
  meters+pixels/small/mid), 6 refusals (lat over/under, zoom high/neg,
  bad mpp, bad target).
- WavHead: 7 pins (fixture format/rate/geometry/riff, float-stereo
  format/rate, riff echo), 11 refusals (short, RIFF/WAVE/fmt tags,
  fmt length, ADPCM, channels, rate, bits, blockAlign, byteRate).
- TiffHead: 4 pins (fixture LE, BE 16, LE 10, BE minimal), 7 refusals
  (short, bad order, swapped magics x2, odd offset, low offset,
  BigTIFF 43).
- KAnon: 7 pins (groups/min/not-ok/violation-pin, k=1 vacuous, pairs
  ok, single group, violation order), 5 refusals (k zero, empty,
  ragged, no columns, over cap).
- AttemptWindow: 9 pins (empty admits, three denies, two admits,
  boundary expires, future ignored, unsorted counts, old expired,
  now counts, many expired), 5 refusals (neg now, bad max, bad
  window, neg prior, over cap).
- DofNear: 6 pins (hyperfocal +f, near, far, bracket, far-null at H,
  near finite), 4 refusals (zero focal, neg aperture, NaN coc, zero
  focus).
- ShannonBits: 6 pins (0/1/2-bit exacts, total bits, skewed pair),
  2 refusals (empty, over cap).

## H. Verification bindings

- kotlinc-jvm 2.2.21 separate compiles (12 jars) + JRE 17.0.9
  consumer run.
- Independent Python recomputation (float RPN, gap-fill loop, wrap
  walk, redir walk, dict-table Cast, slippy recompute, struct
  re-parse, Counter audit, comprehension windows, formula DoF,
  math.log2 — deliberately distinct from the kernels).
- Static gates: hygiene (imports + SPDX), package-collision guard,
  touch-target detector (self-test + strict file + R28-baseline
  delta), android parse gate (unchanged 8 files), manifest-scope
  gate, layout-bounds + page-matrix self-tests.
- CI: exact-candidate compile receipt required; device failures triaged per-run
  (annotations API; logs unavailable — claim only what annotations show).
- Downgrades: kernels are host-only (Android behavior needs CI/device); touch scan
  is static-only; privacy rows without device runs are documented, not delivered
  (k-anonymity wiring excepted as labeled future work).

## I. Repair policy

Repaired candidates inherit no trust: full lab re-run + at least one new
attack on the patch. Pre-execution review fixes: stereo WAV bytes rebuilt
with explicit LE helpers after spotting bad hand arithmetic; scale-bar b3
pin re-derived for the kernel's largest-fitting (not closest-pick)
semantics before first green. Post-failure repairs (all test-side; kernels
correct as written): numbered-input expectation fixed to base reuse (the
kernel strips ` (N)`), b4/b5 bar pins fixed (raw-meter arithmetic slip +
fit semantics), the hyperfocal pin restored to the +f form after a
mid-stream misread moved it off, the reference mirror aligned (fit loop,
+f hyperfocal, suffix strip), and one lost update re-applied after
parallel same-file edits raced. No kernel repairs.

## J. Stopping

Stop this turn when: lab PASS + census updated + audits regenerated + docs
committed + CI triaged with receipts. Otherwise return the strongest verified
artifact + exact gap + next test (see RESULTS).
