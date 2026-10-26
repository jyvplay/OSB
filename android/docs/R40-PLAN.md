# R40 plan — A–J contract (20 kernels + the CI-execution bridge)

## Runtime honesty (what actually exists here)

Used this round: kotlinc-jvm 2.2.21 + JRE 17.0.9 (persisted toolchain,
hash-pinned), CPython 3.11 stdlib (html, email.header, zlib, plistlib,
ipaddress, urllib — several as HELD-OUT verifiers), git/gh (CI triage by
run id), platform web-search (currency only this round; no page fetch was
needed — all new primaries were either already-fetched spec families or
search-pinned with the downgrade declared). NOT available, never claimed:
Android SDK/emulator/device in-sandbox, theorem provers, fuzzers, human
review. The ONE new verifier this round: CI's own Gradle JVM lane now
executes `:testing:r39Contracts` and `:testing:r40Contracts` — real
AGP-toolchain execution of all 39 R39+R40 kernels, closing the
"host kotlinc ≈ Gradle kotlinc" gap R39 named as its next test. Until that
lane goes green, the Gradle wiring is AUTHORED, not verified; the ledger
below records which it turned out to be.

## A. Formal model

Admissible objects: new pure-logic Kotlin kernels in existing modules;
host tools; one build file + one workflow line (the CI bridge — the only
non-doc, non-kernel edits, both quoted in RESULTS). Access model: bytes
and strings in, no network/clock/device state inside kernels. Resource
counted: executed assertions, mirror agreements, tool tests, census rows,
CI-executed contract tasks. Success (quantifier order explicit): FOR
EVERY kernel K: isolated jar + executed contracts + independent mirror +
census row; AND totals strictly exceed R39 (>19 kernels, >344 assertions,
>30 tool tests, >16 modules); AND the CI bridge is pushed with its result
recorded either way. Failure: any gate red at stamp, any claim without a
log. Units/tolerances in each KDoc (percentile ranks 1-based; snap spans
px; rotations deg mod 360; MRZ weights 7-3-1). Adjacent problems not
substituted: format STRUCTURE ≠ rendering (NumFmtSections); checksum
layer ≠ wallet (Bech32); provisioning intake ≠ code generation
(OtpauthUri); prefix redaction ≠ anonymity (IpRedact); descriptor parse ≠
plist decode (PlistHead envelope only).

## B. Outcome space

H+ (achieved): 20 kernels / 360 assertions / 19 modules deepened; CI
bridge authored and pushed. H− (standing): ALL-features-A-grade remains
impossible in one sandbox turn — 0/50 accepted, restated, with writer +
spreadsheet logic again the round's largest cluster (6 kernels / 116
assertions last round, 5 kernels / 98 this round across
ooxml+documents+editor). H∂: four in-round boundary discoveries moved
answers — they are §I repairs, each with a named attack. Evidence
threshold: compiler exits + mirrors + idempotent census ×2 + lab receipt
+ CI run ids; nothing asserted before it ran.

## C. Frontier

Imported with hypotheses: ECMA-376 §18.8.31 (format-code sections) and
§18.17.3 (error literals); TeX dash conventions (writer-local contract,
stated not imported); Unicode U+FFFC semantics; CMOS footnote-symbol
order; ISO 32000-1 Table 30 (/Rotate shall be multiple of 90,
inheritable); WHATWG character references MINUS the cp1252 C1 quirk
(declared narrowing, and the mirror RECORDS Python doing the quirk);
RFC 2047 §2/§6.2; 7zFormat.txt signature header; published FNV-1a
parameters; ICAO 9303 §4.9 with the published TD3 specimen reproduced
digit-for-digit; Microsoft filename rules; nearest-rank percentile
definition; GFM task-list extension; CF bplist00 trailer; BIP-173/350
polymod constants; Key-Uri otpauth format; prefix-keeping IP redaction
practice. Open interface: everything needing a renderer/device/live bus
stays named in RESULTS §Scope; the CI bridge moves one slice (kernel
execution) across that interface this round.

## D. Negative space — 15+ shapes that do NOT satisfy the contract

1. A NumFmt "renderer" that formats numbers — enormous surface, wrong
   layer; STRUCTURE only is the contract. Test: no render API exists.
2. Error literals with case-insensitive parse ("#n/a") — storage is
   canonical; leniency here corrupts round-trips. Test: case refusal.
3. Dash education inside code spans — the modal shortcut this round: a
   regex over the whole text LOOKS correct on prose demos and silently
   corrupts `a--b` in code. Why it fails: the kernel cannot see span
   boundaries, so correctness is impossible at this layer; the honest
   contract pushes exclusion to the caller AND SAYS SO. Detector: the
   SCOPE string names the limitation; the tool test greps it; the
   editor-integration task cannot claim done without span-exclusion
   wiring (tracked in ALTERNATIVES).
4. U+FFFC "attachment manager" — placeholder bookkeeping only; payloads
   are another module's evidence. Test: partition invariant only.
5. Footnote letters style — belongs to list numbering (ColLetters);
   including it here would duplicate logic across modules. Test: absent.
6. /Rotate accepting 45 — spec says shall; viewers that tolerate are
   not a license to guess. Test: refusal.
7. HTML5-complete entity table pasted in as 2200 lines of data — data,
   not logic; would also import the cp1252 quirk silently. Test: subset
   size pinned at 30 + quirk refusal pinned BOTH sides (kernel refuses;
   mirror documents Python remapping).
8. RFC 2047 with charset passthrough — mojibake by construction. Test:
   KOI8-R refusal.
9. A 7z "parser" that walks the next header without the CRC gate —
   exactly how truncated downloads corrupt. Test: bit-flip refusal.
10. FNV marketed as integrity hashing. Test: non-cryptographic grep.
11. MRZ validation presented as identity verification. Test: scope grep
    (no-identity-claims) + the composite-tamper refusal.
12. Filename sanitizer that invents names for all-garbage input. Test:
    `???` refusal.
13. Interpolating percentiles by accident (statistics.quantiles
    semantics) — p99 must be an observation. Test: every result is a
    member of the input.
14. Task checkboxes toggled by substring replace ("[x]" anywhere in
    prose). Test: `-[ ]` and `- [y]` non-matches.
15. Snap layout via rounding each column independently (sums drift off
    total). Test: the 9×6 tiling sweep.
16. bplist object-graph decode "while we're here" — unbounded recursion
    surface; envelope-only is the declared step. Test: no decode API.
17. Bech32 with segwit address semantics bolted on — wallet adjacency
    the vault must not have. Test: scope grep.
18. otpauth intake that silently drops unknown params (the common
    library behavior) — intake must refuse what it cannot vouch for.
    Test: unknown-parameter refusal.

## E. Mechanism portfolio (six mechanisms, early independence kept)

| # | Mechanism | Construction | Artifacts | Proved | Open interface | Cheapest falsification | Gap |
|---|---|---|---|---|---|---|---|
| 1 | Context-scanning text transforms | longest-match runs; quote/bracket state | DashEducate, NumFmtSections(split) | 45 executed assertions | code-span exclusion (caller) | one mis-split quoted format | local |
| 2 | Checksum/residue algebra | CRC-32 span gate; 7-3-1 weights; GF(2^5) polymod | SevenZHead, Mrz, Bech32 | fixture + specimen + both constants | corpus breadth | one accepted tamper | local |
| 3 | Grammar intake with refusal | regex-anchored items; param whitelists | TaskCheckbox, OtpauthUri, Rfc2047 | 56 assertions incl. repairs | full-document contexts | one silently-dropped field | local |
| 4 | Integer lattice arithmetic | floor boundaries; nearest-rank; mod-360 group | SnapFraction, Percentile, PdfRotate | sweeps + full compose table | none in scope | a non-tiling width | local |
| 5 | Envelope/trailer byte parsing | fixed offsets + cross-bound checks | PlistHead, (UsbDesc/IcoDir R39) | plistlib fixture + 7 refusals | object-graph decode (future) | mutated trailer accepted | local |
| 6 | Classification kernels | literal tables + bit rules | CellErr, HtmlEntity, Fnv1a, SafeName, Monogram, ObjReplace, FootnoteMark, IpRedact | vectors + held-out mirrors | locale/grapheme layers (declared) | mirror disagreement | local |

## F. Artifact requirement — met

Every branch: executed code + counted assertions + independent mirror.
The CI bridge is an executable design whose verification event (the lane
run) is recorded in the ledger, not presumed.

## G. Second-order adversary

A(NumFmtSections): quote-inside-bracket `["a"];b` — pinned. A(CellErr):
`#SPILL!` must refuse BY NAME, not fall through to "unknown". A(Dash):
5-hyphen rule and `..`/`....` non-matches. A(FootnoteMark): the ¹²³
block irregularity and leading-superscript-zero. A(PdfRotate): full 4×4
compose table, not spot checks. A(HtmlEntity): `&#128;` — kernel refuses
while the MIRROR proves Python remaps it to €; both sides executed.
A(Rfc2047): the dead-refusal regex found in-round (see §I). A(SevenZHead):
flip a byte INSIDE the CRC span vs the minor-version byte OUTSIDE it —
both behaviors pinned. A(Mrz): composite-digit tamper with all field
digits intact. A(Percentile): the FP self-map sweep that found the bug.
A(SnapFraction): spans(_,0) empty-range bypass found in-round.
A(OtpauthUri): %3A on BOTH halves of the label. A(IpRedact):
leading-zero octets, `1:2:3:4:5:6:7::8` pointless compression.

## H. Verification binding

Bound: kotlinc exits + R40Checks (360/20); 20 mirrors (six held-out
stdlib families); census ×2 idempotent + --check; manifest/touch gates;
lab receipt; CI run ids post-push — including the first CI execution of
r39Contracts/r40Contracts. Downgraded without claim: Gradle task
correctness until the lane runs; dash education in rendered documents;
ICO/7z behavior on wild corpora; MRZ on real documents (specimen only).

## I. Repair log (no inherited trust; each patch got a new attack)

1. Rfc2047 KERNEL: the language-tag refusal was DEAD CODE — the word
   regex excluded '*' so tagged words never matched and passed through
   silently. Regex widened, refusal now fires; attack kept.
2. Percentile KERNEL: `ceil(p/100*n)` FP drift gave rank 56 for p=55,
   n=100 — caught by the self-map sweep on first execution. Fixed by
   multiply-before-divide; p=55 and p=29 pinned as regression attacks.
3. SnapFraction KERNEL: `spans(total, 0)` returned an empty list without
   any validation firing (empty range skipped `span`). Explicit
   `require(n > 0)` added; refusal test kept.
4. OtpauthUri KERNEL: label was percent-decoded BEFORE the `:` split, so
   an escaped `%3A` became a split point. Now split-raw-then-decode;
   attacks pinned on both halves.
5. Two consumer-side wrong vectors (NumFmt `-General`; thirds-of-1000
   expectation) — kernels were right, tests fixed; both now carry the
   corrected reasoning in their messages. Also two mirror tautologies
   (`or True`) deleted and a tool test (`no tautological assertions`)
   added so that shape cannot return.
6. LAB-HARNESS honesty hole (also retroactive to R39): plain-script
   tool tests collected as 0 unittest tests, so lab receipts recorded
   hollow "PASS 0/0" suite lines while the real checks ran only
   manually. Both files converted to per-check unittest cases
   (Ran 30/Ran 38); disclosed in RESULTS for both rounds.
All six areas re-ran from zero afterwards; final 360/20 is post-repair.

## J. Stopping

Stopped at: all gates green, lab stamped, CI triaged by exact id, ledger
committed. Open and restated: 0/50 accepted; the A-grade question is
answered honestly again (ALTERNATIVES §1). Next highest-information test
(R41): confirm the CI contract tasks stay green across a flake cycle,
then promote one device-adjacent kernel chain (SheetFrame or editor
span-exclusion for DashEducate) into an instrumented test in an existing
device lane.
