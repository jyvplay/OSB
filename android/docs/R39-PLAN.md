# R39 plan — A–J contract (writer + spreadsheet push, 19 kernels)

## Runtime honesty (what actually exists here)

Actually available and actually used this round: kotlinc-jvm 2.2.21 +
JRE 17.0.9 (re-bootstrapped after a sandbox reset, hashes matched
`host-tools.json`), CPython 3.11 with stdlib (datetime, fnmatch, base64,
struct, the punycode codec — several used as HELD-OUT verifiers), git, gh
(GitHub API for CI run/annotation triage), the platform's web-search and
page-fetch tools (used for the ULID canonical spec and currency searches),
and GitHub Actions CI consuming pushes. NOT available, therefore never
claimed: Android SDK/emulator/device in-sandbox, theorem provers, fuzzers,
LibreOfficeKit/Servo runtimes, independent human review. No execution,
parallel search, or verification is claimed beyond what the logs show.
The user's "Class A this turn?" question is answered under H− below —
honestly, no; the full argument is in R39-ALTERNATIVES.

## A. Formal model

Admissible objects: new pure-logic Kotlin kernels inside existing Gradle
modules; host Python tools; docs. No new Android components, permissions,
or UI (touch-target delta must stay +0; manifest scope pinned).
Information/access model: file bytes and strings in; no network, no device
state, no clocks inside kernels (timestamps are inputs). Resource counted:
executed production-kernel assertions (ok/refusal), executed mirror
agreements, tool tests, census rows deepened. Success (quantifiers
explicit): FOR EVERY new kernel K THERE IS a compiled-in-isolation jar, ≥1
executed contract group over K, ≥1 independent Python mirror agreeing on
K's pinned vectors, and a census row naming K — AND the round totals
strictly exceed R38 (>17 kernels, >326 assertions, >24 tool tests, >15
modules deepened). Failure: any gate red at stamp time, or any claim
without a log. Regime/boundaries/units: each kernel's KDoc declares its
domain (serials 1..2958465; columns 1..16384; MIDI 0..127; minutes
0..1439; EV in stops; points for PDF boxes); tolerances explicit (EV
±0.05; frequencies ±1e-9 where exact math allows). Adjacent problems NOT
substituted: rendering documents ≠ serial-date math; IDNA ≠ Punycode
transform; JWT *verification* ≠ structural split (the kernel hard-codes
that distinction); live USB enumeration ≠ descriptor parsing; A-grade
acceptance ≠ any amount of host evidence.

## B. Outcome space

H+ (achieved, evidence below): a 19-kernel round with every kernel
executed and mirrored, writer+spreadsheet surfaces visibly deepened.
H− (held for the user's explicit ask): "writer and spreadsheet to Class A
this turn" is NOT achievable under stated constraints — A grade =
full-spec acceptance, which requires rendering fidelity, device
interaction evidence and acceptance review that this sandbox cannot
produce; claiming it would violate the honesty clause. Status stays 0/50
accepted.
H∂: per-kernel boundaries that changed the answer during design are
recorded in each KDoc (phantom serial 60; leap-second refusal; local-MAC
hint-not-proof; disjoint-crop refusal; ULID 2^48 ceiling).
Evidence threshold declared up front: compiler exit codes + mirror
agreement + idempotent census ×2 + lab receipt; nothing committed as fact
before those ran.

## C. Frontier

Imported results, restated with hypotheses: Excel's 1900 leap-year bug is
contractual (ECMA-376 keeps serial 60 = phantom 1900-02-29; verified
against Microsoft's own KB wording via current articles); ECMA-376
§18.3.1.13 column-width formulas assume integer MDW and the 5px padding
(Calibri-11/96dpi default); RFC 3492's parameters and overflow-rejection
clause; the ULID canonical spec (fetched primary, 2026-10-23) including
the monotonic-overflow failure requirement; POSIX fnmatch semantics as
realized by CPython; ISO 13616 mod-97 (ISO text paywalled — SWIFT
registry lengths used, downgrade declared); USB 2.0 §9.6.1 field law
(spec zip not fetched; field layout search-pinned, hence the fixture is
synthetic-but-exact). Open interface between achievability and
impossibility: everything executable-at-home is executed; everything
needing a renderer/device/bus stays in CI-or-Studio space and is named in
RESULTS §Scope.

## D. Negative space — 15+ shapes that would NOT satisfy the contract

1. A SerialDate that "fixes" the 1900 bug (breaks every real file; the
   phantom is the spec). Test: serial 60 must round-trip as 1900-02-29.
2. Column letters via plain base-26 with a zero digit (off-by-one at Z/AA).
   Test: the full 1..16384 bijection property.
3. Column width ignoring the 128/MDW rounding term (drifts at MDW≠7).
   Test: MDW 8 pin 67px.
4. A smart-quote pass claiming elision correctness it doesn't have — the
   modal shortcut this round. It LOOKS right on every demo sentence and
   silently curls 'Tis the wrong way. Why it fails the contract: an
   undeclared wrong case is a hidden lie; declared, it is a narrowing.
   Detector: the explicit `'Tis → ‘Tis (declared)` assertion pins the
   wrong-but-documented behavior so any silent "fix" breaks a test and
   forces a documented decision.
5. Roman numerals accepting IIII/VX "to be lenient" (clock-face trivia
   destroying parse-format bijection). Test: bijection property + refusals.
6. A CropBox that silently substitutes MediaBox on disjoint crops (viewer
   behavior, but a library guessing is dishonest). Test: disjoint refusal.
7. An ICO parser trusting PNG payload dims from the directory (lets a
   256-claimed 64px icon through). Test: lying-IHDR refusal.
8. Punycode advertised as IDNA (missing nameprep/UTS46 — spoofing risk).
   Test: SCOPE string + tool-test grep.
9. A JWT kernel with a `verify(key)` stub — worst possible shape. Test:
   signatureChecked is a constant false; tool test greps the never-verifies
   declaration.
10. USB descriptor parse marketed as keylogger detection. Test: SCOPE
    says a descriptor cannot identify keyboards; tool test greps it.
11. An IBAN validator passing unknown countries through length-unchecked.
    Test: XX refusal assertion.
12. Glob matching implemented via regex translation but diverging from
    fnmatch on `[]]`/unterminated `[`. Test: vector-for-vector fnmatch
    agreement in the mirror.
13. MAC "randomization detector" (local bit is a hint; VMs/docks also set
    it). Test: hint-not-proof wording pinned by tool test.
14. RFC 3339 parser accepting naive timestamps (destroys ledger
    comparability). Test: naive refusal.
15. Quiet hours with closed intervals (double-fires at boundaries). Test:
    half-open assertions at both ends.
16. EV math claiming luminance metering (needs the K calibration constant
    we declared out). Test: SCOPE + no luminance API exists.
17. ULID decode mapping I/L/O→1/1/0 "Crockford-style" (spec silence →
    refusal, not leniency). Test: I/O/U refusals.
18. Counting the 1..16384 and 1..3999 property loops as thousands of
    assertions to inflate totals — refused; each loop counts once.

## E. Mechanism portfolio (six distinct mechanisms shipped)

| # | Mechanism | Central construction | Artifact | Proved portion | Unresolved interface | Cheapest falsification | Gap |
|---|---|---|---|---|---|---|---|
| 1 | Calendar arithmetic | Hinnant civil-day algebra + Lotus phantom split | SerialDate, Rfc3339, (IsoWeek R38) | 48 executed assertions, datetime held-out | fractional-day serials; zones | one disagreeing datetime vector | local |
| 2 | Positional codecs | bijective-26, Crockford-32/48-bit, base64url, roman glyph table | ColLetters, Ulid, JwtSplit(b64url), RomanNum | bijection properties executed whole-range | none inside scope | a non-round-tripping index | local |
| 3 | Byte-structure parsing | offset/endian field law + cross-field agreement | IcoDir, UsbDesc (+EDID/FLAC/WebP R38) | fixture parses + 14 refusal shapes | live-bus/true-file corpus | one mutated fixture byte accepted | local |
| 4 | Variable-length integer transform | RFC 3492 generalized-variable-length integers w/ bias adaptation | Punycode | encoder+decoder against held-out codec | IDNA layer above | codec disagreement on any label | equivalent to full IDNA only if mis-scoped — scoped, so local |
| 5 | Checksum/residue algebra | mod-97 over base-36 digit stream; Luhn (R38) | IbanCheck | registry vectors + generation inverse | full 80-country registry (data) | bigint mod disagreement | local |
| 6 | Interval/state machines | half-open wrap windows; backtracking star matcher; quote context FSM | QuietHours, GlobMatch, SmartQuote, HyphenSoft | brute-force minute loops; fnmatch vectors | locale typography | brute-force vs closed-form mismatch | local |

Early independence preserved: mechanisms share no helper code; each kernel
compiles alone (19 separate jars, enforced by the runner).

## F. Artifact requirement — met

Every branch above returns executed code + counted assertions + a mirror.
No status-report-only branch exists this round. The two property loops are
quantitative bounds (full-domain bijections), not samples.

## G. Second-order adversary (strongest attack per candidate)

A(SerialDate): feed 1900-02-29 into toSerial (must give 60, not throw) and
1899-12-31 (must throw) — both pinned. A(ColWidth): MDW=8 (the 128/MDW
term differs from 128/7) — pinned. A(Punycode): uppercase extended digits
+ literal-case preservation (my own first test vector was WRONG here —
the kernel was right, the test fixed; recorded in §I). A(Ulid): first
char '8' (130-bit trap) — refused. A(JwtSplit): alg:none with empty
signature accepted structurally but flagged; escaped-header smuggling
refused. A(GlobMatch): `[]]`, `a[`, reversed ranges — all pinned against
fnmatch. A(IcoDir): directory/IHDR disagreement — refused. A(QuietHours):
boundary minutes at both ends of a wrapping window — pinned half-open.
A(IbanCheck): check digits 00/01/99 (mod-97 fixed points) — refused.

## H. Verification binding

Accepted→bound to: kotlinc exit codes + R39Checks run (344/19); Python
mirror run (19 mirrors, 6 held-out stdlib verifiers); census validate ×2
idempotent; manifest/touch gates; lab receipt in R39-RESULTS; CI run ids
in the ledger after push. Downgraded (no adequate verifier here): visual
typography of SmartQuote output (needs rendered review), ICO behavior on
real-world icon corpora, USB descriptors from live hardware, IBAN
registry breadth. Each downgrade is stated where the claim appears.

## I. Repair log (this round)

One in-round repair: the R39Checks vector `decode("BCHER-KVA") == bücher`
was FALSE — RFC 3492 preserves literal-portion case; only extended digits
are case-insensitive (the held-out Python codec agrees: `BCHERü`). The
TEST was wrong, the kernel right. Repaired to `bcher-KVA`, full contracts
re-run from zero (no inherited trust), and a new attack added: the mirror
now pins `b'bcher-KVA'.decode('punycode') == 'bücher'` so both sides of
the case rule are externally anchored. Also: one consumer-visibility fix
(JwtSplit.b64urlDecode internal→public, a legitimate API surface) —
re-run from zero afterwards.

## J. Stopping

Stopped when: all 19 kernels green, mirrors green, census idempotent,
gates green, lab receipt stamped, CI triaged by exact run id, ledger
committed. Not stopped by belief: the A-grade goal remains open (0/50)
and is restated, not resolved. Next highest-information test (R40):
compile-and-run one R39 kernel inside the real AGP unit-test task in CI
(closing the "host kotlinc ≈ AGP kotlinc" gap with CI evidence), and an
ICO corpus harvested from open-licensed icon sets for IcoDir.
