# R28 scope / acceptance contract — 10 executed kernels + USB/consent privacy set

Working branch `arena/350b91e4-osb`. Evaluation date 2026-10-21.
Fresh primary-source findings: `research/r28-primary.md`.
Alternatives: [R28-ALTERNATIVES](R28-ALTERNATIVES.md) ·
Privacy PRD: [R28-PRIVACY-PRD](R28-PRIVACY-PRD.md) ·
Results: [R28-RESULTS](R28-RESULTS.md).

## A. Formal model (turn contract)

- Admissible objects: host-JVM Kotlin kernels (zero android imports);
  real fixtures; a JVM consumer; independent Python recomputation; authored
  Android files (compile-gated only); docs with license closure pointers.
- Information/access model: this sandbox has kotlinc/JVM + Python, no SDK,
  no emulator, no log/blob download from CI (annotations API only). Anything
  needing Android is authored, never claimed executed.
- Resource counted: NEW executed-kernel assertions + modules deepened with
  new executed kernels (absolute + % of 50). Prior evidence never inherited
  by new code; repaired code inherits no trust (re-run everything).
- Success: 10/10 kernels green on host + reference agreement + 0 android
  imports + parse gate 0 syntax errors + CI compile receipt for authored
  files + exact-candidate triage of every CI failure. Failure: any gate red
  without a same-turn repair + receipt.
- Parameter regime: bounds in each kernel (caps listed in KDoc); UTC dates;
  ASCII grammars; tolerances: exact integer/date equality, exact 0.5 decay,
  double equality only where bit-exact (pow(0.5,1)).
- Adjacent problems NOT substituted: host pass != Android pass; parse !=
  compile; static touch-target scan != rendered proof; policy text !=
  enforcement; notification != blocking; PRD row != feature.

## B. Outcome space

- H+: all 10 kernels execute green and CI compiles the authored set.
- H-: a kernel is unimplementable within host-only bounds (would need
  Android/time/network) -> it must be cut, not faked.
- H∂: kernels whose verdict changes at a boundary (expiry instants, zoom
  19/20, COUNT 366/367, TTL 30/3600) -> boundary checks are mandatory, not
  optional. No H+ commitment until the lab prints PASS.

## C. Frontier (imported results, hypotheses attached)

- Slippy-map convention (OSM wiki formula, zoom 0..19, lat clamp
  ±85.05112878): TileMath implements exactly this, nothing more.
- RFC 6265 §5.2/5.3 (cookie parse/match, domain-tail + path-boundary rules):
  CookieJar implements the stated subset; cookie-date covers 3 fixed formats.
- RFC 5545 RRULE (COUNT/XOR/UNTIL, invalid-date skip): Recurrence implements
  FREQ DAILY/WEEKLY/MONTHLY + INTERVAL/COUNT/UNTIL/BYDAY-weekly only.
- Podlove Simple Chapters (namespace + start/title/href): ChapterMarks reads
  exactly this; no other podcast namespace.
- USB HID boot interface classes (03:01:01/03:01:02) + USBGuard grammar
  subset (R26): UsbWatchReceiver verdicts reuse them, advisory-only.
- Open interface: engine/intake/device (see ALTERNATIVES stopping rule).

## D. Negative space (15 shapes that look like solutions but fail)

1. Kernel importing android.* that "passes" kotlinc but breaks the host/Android split.
2. Consumer asserting on production internals instead of behavior.
3. Fixture-free kernel tested only on inline strings where a format spec exists (chapters/cookies).
4. Time-dependent kernel reading the wall clock (non-reproducible).
5. Random ids without a determinism story (ConsentLedger: randomness OK, checks use returned ids).
6. Double equality on non-bit-exact math (only 0.5^1 asserted exactly).
7. Eviction scored on stale state (R28 check 33 caught exactly this in FrecencyLedger).
8. Boundary-off-by-one: expiry instant, zoom cap, COUNT cap, TTL edges (all pinned).
9. Quadkey/tile math "verified" by round-trip only (adds the (0,0,1)->(1,1) absolute vector + Python cross-check).
10. Cookie matcher tested without the /app vs /application boundary.
11. Recurrence tested without the Jan-31 gap skip.
12. Front-matter accepting nesting/duplicates silently.
13. Scrollback stripping controls silently instead of refusing.
14. Authored receiver implying enforcement ("blocked") in user-visible text.
15. Static touch-target scan presented as rendered proof.
- Modal shortcut (1): a kernel with `import android.*` compiles under
  kotlinc alone and could fake "host execution". Detector: the hygiene test
  in test_r28_tools.py fails the suite on any android/androidx import; CI
  Gradle would additionally compile it as Android. Test name:
  KernelHygiene.test_kernels_are_host_only.

## E. Mechanism portfolio (10 kernels + 2 authored; each distinct)

| Kernel | Construction | Proved | Unresolved interface | Cheapest falsifier | Gap |
|---|---|---|---|---|---|
| TrashLedger | Expiry-then-size purge order, saturating sums | Ordering + validation on JVM | Filesystem application (refused: FileMutationPolicy denies) | Feed 4097 entries / colliding restore | Local (policy done; app wiring future) |
| SnapZones | Corner-priority zone pick + exact tile rects | Geometry on JVM | WM adoption of targets | Pointer at threshold±1 | Local |
| FrecencyLedger | Half-life decay sums, post-insert eviction | Ranking + eviction on JVM | Palette wiring | 65th target identity | Local |
| TileMath | Slippy forward/inverse + quadkey bijection | Vectors + round-trips on JVM | Tile fetch/render (none) | (550,335,10) bounds center | Local |
| ChapterMarks | Strict NS parse + monotonic starts | Fixture + refusals on JVM | Playback integration | Non-monotonic XML | Local |
| ConsentLedger | Random single-use TTL receipts, caller clock | Issue/consume/prune on JVM | Dialog wiring + device run | Replay/expired consume | Local |
| CookieJar | RFC 6265-subset store/match/evict | Fixture + boundaries on JVM | Engine cookie sync (none) | /app vs /application | Local |
| Recurrence | Bounded date-scan expansion, RFC skips | 3 vectors + refusals on JVM | Reminder scheduler (none) | Jan-31 COUNT=3 | Local |
| FrontMatter | Flat typed header, no nesting | Fixture + refusals on JVM | Notes read/write path | Duplicate/nested input | Local |
| Scrollback | Ring + exact greedy reflow | Wrap/ring/refusal on JVM | PTY (refused, not a shell) | Tab push, width-8 reflow | Local |
| UsbWatchReceiver | Attach broadcast + verdict text + record | Parse only | Device behavior run | Emulator USB attach | Equivalent to device-run gap |
| ConsentDialog | Scope+TTL dialog, callback burn | Parse + strict touch-target | Device behavior run | Cancel/back burns nothing | Equivalent to device-run gap |

## F. Artifacts

Proofs: none (no theorem claims). Executable designs: 10 kernels + consumer +
fixtures + 4 Python tools. Reproducible experiments: host lab (receipted),
reference recomputation, touch-target scan (223 sites). Quantitative bounds:
caps in every KDoc. Causal model: none claimed.

## G. Second-order adversary (per-candidate attacks, run)

- TrashLedger: duplicate ids, colliding restore, path escape, saturating sums.
- SnapZones: corners vs edges, threshold±1, outside pointer, min-size trap.
- FrecencyLedger: time travel, negative age (clamped), eviction identity (bug found + fixed).
- TileMath: lon seam (180 clamps, 181 refuses), zoom 20, bad quadkey, lat clamp.
- ChapterMarks: XXE/doctype (disallowed), non-monotonic, missing title, malformed.
- ConsentLedger: replay, scope swap, consume-at-expiry, TTL edges, unknown id, randomness non-emptiness.
- CookieJar: foreign Domain, no-pair, Max-Age=0 delete, per-host flood (65th evicts oldest), insecure None demotion.
- Recurrence: BYDAY-on-DAILY, COUNT+UNTIL, COUNT 367, YEARLY, missing prefix, UNTIL window.
- FrontMatter: unterminated, duplicate, nesting, bad key, bad list, no colon, int range, 1MiB doc cap.
- Scrollback: tab/C0 controls, width 7, 2005-line ring, 300-col truncation count, empty-line survival.
- Receiver/Dialog: no-enforcement wording review, permission-degraded path, exported=true requirement, 48dp+tags.

## H. Verification bindings

- kotlinc-jvm 2.2.21 separate compiles (10 jars) + JRE 17.0.9 consumer run.
- Independent Python recomputation (chapters/cookies/recurrence/tiles/trash/front-matter).
- Static gates: hygiene (imports), touch-target detector (self-test + strict file),
  android parse gate, layout-bounds + page-matrix self-tests.
- CI: exact-candidate compile receipt required; device failures triaged per-run
  (annotations API; logs unavailable — claim only what annotations show).
- Downgrades: authored files are parse-only; touch scan is static-only;
  privacy rows without device runs are documented, not delivered.

## I. Repair policy

Repaired candidates inherit no trust: full lab re-run + at least one new
attack on the patch (R28: FrecencyLedger post-insert eviction re-attacked
with the 65-target identity check; negative-age clamp added and checked).

## J. Stopping

Stop this turn when: lab PASS + census updated + audits regenerated + docs
committed + CI triaged with receipts. Otherwise return the strongest verified
artifact + exact gap + next test (see RESULTS).
