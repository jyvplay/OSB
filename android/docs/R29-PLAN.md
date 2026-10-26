# R29 scope / acceptance contract — 10 executed kernels + USB-allowlist privacy set

Working branch `arena/350b91e4-osb`. Evaluation date 2026-10-21.
Fresh research: `research/r29-primary.md` (search synthesis, weaker than
fetched primaries — see scope note there).
Alternatives: [R29-ALTERNATIVES](R29-ALTERNATIVES.md) ·
Privacy PRD: [R29-PRIVACY-PRD](R29-PRIVACY-PRD.md) ·
Results: [R29-RESULTS](R29-RESULTS.md).

Branch note: this turn opened with a truncated clone (base commit only), but
the full R26–R28 history was recovered intact from origin (`65bf049`) and the
working tree matched it outside the 41 R29 files, so R29 lands as one commit
on top of the real history (see RESULTS ledger).

## A. Formal model (turn contract)

- Admissible objects: host-JVM Kotlin kernels (zero android imports);
  real fixtures; a JVM consumer; independent Python recomputation; authored
  Android files (compile-gated only); one authored-support store whose LOGIC
  executes on JVM against test-only `android.content` stubs plus an
  in-memory fake (never shipped, never Android evidence); docs.
- Information/access model: this sandbox has kotlinc/JVM + Python, no SDK,
  no emulator, no log/blob download from CI (annotations API only). Anything
  needing Android is authored, never claimed executed.
- Resource counted: NEW executed assertions + modules deepened with new
  executed kernels (absolute + % of 50). Prior evidence never inherited by
  new code; repaired code inherits no trust (re-run everything).
- Success: 10/10 kernels green on host + reference agreement + 0 android
  imports in kernels + parse gate 0 syntax errors + manifest scope unchanged
  + CI compile receipt for authored files + exact-candidate triage of every
  CI failure. Failure: any gate red without a same-turn repair + receipt.
- Parameter regime: bounds in each kernel (caps in KDoc); caller-supplied
  clocks; ASCII grammars except deliberate unicode fixtures (é names);
  tolerances: exact integer equality; doubles exact only where bit-exact
  (21.0, 252.0, 5399.5) else 1e-9 windows.
- Adjacent problems NOT substituted: host pass != Android pass; parse !=
  compile; stub-executed store logic != Android wiring proof; static
  touch-target scan != rendered proof; allowlist membership != hardware
  trust; PRD row != feature.

## B. Outcome space

- H+: all 10 kernels execute green, the allowlist store logic executes
  against the fake, and CI compiles the authored set.
- H-: a kernel is unimplementable within host-only bounds (would need
  Android/time/network) -> it must be cut, not faked. (Cut pre-writing:
  FrameBudget/ThermalPolicy already exist; FTS; crypto; camera.)
- H∂: verdicts changing at a boundary (quota exact-cap, throttle token
  edge, alias depth 8/9, retention window edges, locale fallback order,
  sniff consistency ties) -> boundary checks mandatory. No H+ commitment
  until the lab prints PASS.

## C. Frontier (imported results, hypotheses attached)

- WCAG 2.1 §1.4.3/1.4.6 contrast math (relative luminance, thresholds
  4.5/3.0/7.0/4.5; unchanged in 2.2): ContrastRatio implements exactly this.
- BCP 47 subset (lang[-REGION], no script/extensions): LocaleMatch
  implements this subset and refuses the rest rather than dropping subtags.
- UPnP/DIDL-Lite item shape + `http-get:*:mime:*` protocolInfo: DidlLite
  reads the `<item>` subset the shell renders; envelope/SSDP stay with
  UpnpParsing; the shared XML unescaper is reused, not duplicated.
- CSV delimiter reality (comma/semicolon/tab/pipe in the wild; CsvTable is
  comma-only): CsvDialect sniffs and translates INTO the existing grammar —
  the pipeline ends in the real parser, not a second parser.
- Token bucket (capacity + linear refill, explicit clock): AlertThrottle.
- Grandfather-father-son retention over caller bucket keys: RetentionPlan
  (calendar bucketing stays with the caller — no DST/locale mis-keeps).
- USB HID boot classes + UsbPolicy grammar (R26): receiver wiring reuses
  them; the allowlist feeds already-allowed, advisory-only.
- Open interface: engine/intake/device (see ALTERNATIVES stopping rule).

## D. Negative space (15 shapes that look like solutions but fail)

1. Kernel importing android.* that "passes" kotlinc but breaks the host/Android split.
2. Consumer asserting on production internals instead of behavior.
3. Fixture-free kernel tested only inline where a format spec exists (DIDL/CSV/shares).
4. Time-dependent kernel reading the wall clock (throttle takes caller ms; clock steps clamp).
5. Quota ledger drifting from disk (callers must pair allocate/release; documented, not enforced).
6. Sort "stability" claimed while name tiebreaks reorder (R29 documents the total order instead).
7. Sniff winner presented as certain (scoring + tie-break order documented; SINGLE fallback).
8. Retention doing calendar math (weeks/months are caller keys, never derived here).
9. Locale matching silently dropping script/extension subtags (refused instead).
10. Contrast ratio on alpha-channel hex (refused: unknown backdrop).
11. Share manifest accepting path separators (directory escape).
12. Alias expansion recursing without cycle/depth guards.
13. Allowlist membership presented as hardware trust (VID:PID spoofable; advisory only).
14. Stub-executed store logic presented as Android proof (wiring unaudited; labeled).
15. Static touch-target scan presented as rendered proof.

## E. Mechanism portfolio (10 kernels + 1 authored-support; each distinct)

| Kernel | Construction | Proved | Unresolved interface | Cheapest falsifier | Gap |
|---|---|---|---|---|---|
| ShareManifest | Validated offer: bare names, mime/sha/total caps, canonical digest, frame codec | Offer + codec on JVM | Beam transport/consent | `../x` name, dup name | Local (UI/transport future) |
| QuotaLedger | Per-scope quotas, allocate/release, snapshot frames | Accounting + codec on JVM | Filesystem pairing (caller duty) | Over-cap allocate | Local |
| AlertThrottle | Per-key token bucket, clamped clock, refuse-when-full | Burst/refill/isolation on JVM | Alert-engine wiring | Backwards timestamp | Local |
| FileSort | Total order: key, folded name, raw name | 5-key vectors + descriptors on JVM | Files list wiring | Case-pair order | Local |
| AliasTable | Bounded table, argv[0] resolve, cycle+depth guards | Chains/cycles/depth on JVM | Shell builtin registry | a->b->a | Local |
| ContrastRatio | sRGB linearization + ratio + AA/AAA thresholds | 21.0/1.0/boundaries on JVM | Palette audit wiring | #767676 vs #777777 | Local |
| LocaleMatch | Exact, same-lang, list-default lookup | Fallback table on JVM | Settings/UX wiring | en-GB over [en-US, fr-FR] | Local |
| RetentionPlan | Newest-N days + newest-M weeks + newest-K months | GFS union on JVM | Bucket-key producer | Week-latest identity | Local |
| DidlLite | Item parse, first res, strict duration, shared unescape | Fixtures + refusals on JVM | Browse/transport fetch | Non-http-get proto | Local |
| CsvDialect | Consistency-scored sniff + quote-aware translate | Sniff + CsvTable pipeline on JVM | Import UX wiring | Quoted `;` cell | Local |
| UsbAllowlist | vid:pid grammar + capped SharedPreferences set | Store logic on JVM vs fake | Android wiring + device run | Spoofed vid:pid text | Equivalent to device-run gap |
| Receiver wiring | Allowlist feeds already-allowed; still records+notifies | Parse only | Device behavior run | Emulator USB attach | Equivalent to device-run gap |

## F. Artifacts

Proofs: none (no theorem claims). Executable designs: 10 kernels + consumer +
6 fixtures + USB allowlist + host stubs + fake + 5 Python tools. Reproducible
experiments: host lab (receipted), reference recomputation, touch-target scan
(223 sites, +0 delta), manifest-scope gate. Quantitative bounds: caps in every
KDoc. Causal model: none claimed.

## G. Second-order adversary (per-candidate attacks, run)

- ShareManifest: separator/`..`/blank names, upper MIME, short sha, dup names, over-total, truncated frame, reversed-order digest.
- QuotaLedger: over-quota allocate (state unchanged), unknown scope, quota-below-usage, remove-in-use, release floor, snapshot round-trip.
- AlertThrottle: burst+1 denial, half-token denial, refill edges, per-key isolation, backwards clock (clamped, no bonus), bad key shape, zero capacity.
- FileSort: all 4 keys × directions, dirs-first on/off, descriptor parse errors, empty input, tie input-order independence.
- AliasTable: chains, passthrough, 2-cycle, self-cycle, 9-deep chain (refused), undefine, bad names, empty expansion.
- ContrastRatio: 21.0 window, 1.0 exact, short hex, #76/#77 AA boundary, large-text leniency, AAA strictness, order freedom, alpha/named/non-hex refusals.
- LocaleMatch: exact, case folding, same-language fallback, list default, first-wins, empty supported, empty subtag, digit lang, third subtag, underscore.
- RetentionPlan: whole-day keeps, week-latest identity, GFS union + drops, zero plan, empty input, order independence, dup id, negative keep/over-cap/negative day.
- DidlLite: container counting, entity unescape, missing optionals, quote entities, fractional duration, URL preservation, missing class/res, non-http-get proto.
- CsvDialect: 3 dialect sniffs, single-column, quoted separators, multiline field, quoted tab, pipe rows, unterminated quote, comma passthrough.
- UsbAllowlist: upper/short/malformed vidpid, dup add, shared backing, remove-missing, stored-junk filtering, clear, fill-to-cap, past-cap add vs dup, negative/over-wide ids.
- Receiver wiring: wording review (no "blocked"), permission-degraded path unchanged, exported=true unchanged.

## H. Verification bindings

- kotlinc-jvm 2.2.21 separate compiles (11 jars: 10 kernels + usb group) + JRE 17.0.9 consumer run.
- Independent Python recomputation (all 10 kernel logics + allowlist-adjacent math where host-meaningful).
- Static gates: hygiene (imports), touch-target detector (self-test + strict file + R28-baseline delta),
  android parse gate, manifest-scope gate, layout-bounds + page-matrix self-tests.
- CI: exact-candidate compile receipt required; device failures triaged per-run
  (annotations API; logs unavailable — claim only what annotations show).
- Downgrades: authored files are parse-only (allowlist store logic excepted as labeled);
  touch scan is static-only; privacy rows without device runs are documented, not delivered.

## I. Repair policy

Repaired candidates inherit no trust: full lab re-run + at least one new
attack on the patch. R29 repairs: (1) FileSort "stability" claim replaced by
the documented total order + input-order-independence check after check 47
failed; (2) retention fixture expectations re-derived from window math; (3)
thenBy multi-selector syntax fixed to chained calls; (4) ooxml group gained
its LiteralWorkbook dependency explicitly.

## J. Stopping

Stop this turn when: lab PASS + census updated + audits regenerated + docs
committed + CI triaged with receipts. Otherwise return the strongest verified
artifact + exact gap + next test (see RESULTS).
