# R38 plan — tier-5 A–J contract (seventeen executed kernels round)

Round objective, inherited standing orders: finish R37's leftovers (fold the
`2f20920` CI runs into R37-RESULTS as an amendment), then beat R37 in both
quantity and quality: more executed kernels (17 vs 14), more executed
assertions (326 vs 274), more tool tests (24-suite R38 file; lab total rises),
deeper census rows (15 vs 14), all without touching any existing good feature.

## Runtime honesty (what actually exists here)

Available and used this round: pinned host toolchain `~/.cache/fusion-host-r23`
(kotlinc-jvm 2.2.21, JRE 17.0.9 — reinstalled after a sandbox reset, hashes
matched `host-tools.json`), system Python 3.11.2, git, `gh` (annotations API
only; log download and re-run POST are blocked by token permissions), web
search + page fetch for primary sources. NOT available: Android SDK, emulator,
adb, any device, javac, root, lsusb, network listeners, theorem provers.
Therefore: every claim below is host-JVM/host-Python execution, static
analysis, or CI-annotation reading — never device, rendering, or acceptance
evidence. When no adequate verifier exists the claim is downgraded in place.

## A. Formal model

- Admissible objects: pure-logic Kotlin kernels (no `android`/`androidx`
  imports) inside existing Gradle modules; one host consumer
  (`testing/src/r38/.../R38Checks.kt`); binary fixtures built byte-by-byte;
  Python mirrors sharing no code with the kernels.
- Information/access model: the kernel may see only its explicit inputs
  (byte arrays, strings, numbers). Fixtures are read from
  `tools/fixtures/r38/` by the consumer, never by kernels.
- Resource counted: executed assertions = static `ok`/`fails`/`close` call
  sites in R38Checks that all run in one JVM invocation (no loop
  amplification is counted; the 7-step semver chain loop is counted as its
  7 static-equivalent comparisons emitted by one `for` — see D-shape 16 for
  why this is declared rather than hidden).
- Success (quantifier order): FOR ALL 17 kernels, THERE IS a compile of that
  kernel alone (`-include-runtime`, no sibling on the classpath) AND all
  consumer assertions touching it pass AND a Python mirror reproduces its
  pinned vectors. Failure: any single assertion failure, compile error, or
  mirror disagreement fails the whole round gate.
- Parameter regime/units: byte offsets are 0-based; sizes in bytes;
  timestamps in ms; angles in degrees; tolerances stated per `close` call
  (1e-9 or 1e-12).
- Adjacent problems NOT substituted: BitTorrent networking (we parse links,
  no DHT/peer wire); FLAC decoding (header only, no audio); WebP rendering
  (container only, no pixels); HTTP serving (Range math only); crypto
  (PEM transport syntax only, no ASN.1/keys); payment processing (Luhn is a
  redaction gate only); keylogger *detection* (KeyCadence is an evadable
  in-app heuristic and says so in a SCOPE constant).

## B. Outcome space

- H+: 17 single-compiled kernels with >274 executed assertions and
  cross-language mirrors is achievable in this sandbox. **Met**: 326
  assertions, 17 mirrors, evidence = compiler + JVM run + mirror run.
- H−: full-spec acceptance (real swarms, real displays, real mail, real
  devices) is impossible here; stated, not attempted. 0/50 modules
  specification-accepted; no A-grade claim is made.
- H∂: boundary cases found during the round and resolved by evidence:
  `:feature:beam` was NOT first-deepened (ShareManifest R29/ChunkPlan R33
  already execute — the census sentence was corrected before commit);
  three candidate kernels (ZipEocd, VarInt, A1Ref) crossed existing
  ownership (StrictZip's EOCD signature, castv2 Proto varint, DecimalSheet's
  base-26 columns) and were cut, exactly like R37's Haversine/GSM-7 cuts.

## C. Frontier (imported results, with hypotheses)

- BEP 3 (bittorrent.org, fetched): bencode grammar; `i-0e` invalid, leading
  zeros invalid except `i0e`, dict keys sorted as raw strings; validators
  must reject rather than roundtrip invalid data. Hypothesis: final spec,
  2017 revision.
- BEP 9 (fetched): magnet v1 `urn:btih:` 40-hex or 32-base32; v2 `urn:btmh:`
  multihash; `xt` mandatory; `tr`/`x.pe` repeatable; hybrid allowed.
- RFC 9639 (FLAC, Dec 2024): fLaC marker, block header 1+7+24 bits, 34-byte
  streaminfo, u(20) sample rate, u(36) total samples, block sizes 16..65535,
  type 127 forbidden.
- RFC 9649 (WebP, Nov 2024) + libwebp container doc: RIFF little-endian
  envelope; VP8X 10-byte payload, flag bits ICC 0x20 / ALPHA 0x10 / EXIF
  0x08 / XMP 0x04 / ANIM 0x02; canvas dims minus-one u24le; W*H <= 2^32-1.
- semver.org 2.0.0 items 9-11: precedence chain pinned.
- RFC 9562 (UUID, May 2024): variant top bits of octet 8; version nibble of
  octet 6; v7 48-bit big-endian unix-ms; RFC's own v7 test vector
  017F22E2-79B0-7CC3-98C4-DC0C0C07398F = 1645557742000 ms.
- RFC 9110 §14: int-range/suffix-range; last<first invalid; 416 when no
  member satisfiable.
- RFC 2045 §6.7: uppercase escape digits; soft line breaks; 76-char limit.
- RFC 7468: encapsulation boundaries, matching labels, strict base64.
- ISO 8601 week dates via the equivalent first-Thursday rule; verifier =
  Python `datetime.isocalendar` (held-out implementation).
- CSS Color 4 hsl-to-rgb f(n) algorithm; verifier = Python `colorsys`.
- VESA E-EDID A2: 00FF..FF00 header, big-endian 5-bit PnP letters (reserved
  bit 15), LE product/serial, week 0xFF = model-year flag, sum-to-zero
  checksum.
- W3C WebVTT: mm:ss.ttt / hh+:mm:ss.ttt; mm,ss in 00..59; ttt exactly 3.
- NARA Soundex: digit table, H/W transparency, vowel recoding, first-letter
  rule; worked examples T522/A261/P236.
- ISO 32000-1 §7.3.5: /name #XX escapes, #00 forbidden, delimiters must be
  escaped.
- ISO/IEC 7812 Luhn; PCI DSS first6+last4 display rule.
- Keylogger timing (2026 publications): human 6-18 keys/s, jitter SD
  25-70 ms; injection SD < 1.5 ms; >200 keys/s non-human; heuristics
  evadable (QUACK, arXiv 2604.15845). Open interface: no in-app method can
  see a passive inline hardware logger — KeyCadence therefore never emits
  "safe".

## D. Negative space (19 shapes that would NOT satisfy the contract)

1. Compiling all 17 kernels in one kotlinc invocation (symbol borrowing) —
   runner compiles 17 separate jars; detectable by deleting a sibling and
   recompiling.
2. Counting looped assertions as distinct sites — counted statically;
   declared where a loop emits a bounded chain (semver).
3. Claiming device/Android evidence from host JVM runs — every banner says
   host-only.
4. A bencode decoder that tolerates unsorted/duplicate keys (BEP 3 2017
   forbids); test `d1:b0:1:a0:e` / `d1:a0:1:a0:e` refuse.
5. Accepting `i-0e`/`i03e`/`01:a` — three explicit refusal tests.
6. A magnet parser that treats `xt` as optional or drops repeated `tr` —
   tests pin both.
7. Base32 info-hash "support" that just uppercases instead of decoding —
   pinned decoded hex `14ffe51c...` must match.
8. A FLAC reader that trusts the declared length instead of requiring 34 —
   mutation test.
9. A WebP reader that ignores RIFF size disagreement — mutation test.
10. SemVer precedence that compares prerelease identifiers as plain strings
    ("beta.11" would sort before "beta.2") — chain test catches.
11. UUID version read without checking variant first (version nibble is
    meaningless for NCS/Microsoft variants) — nil/Microsoft tests pin null.
12. Range parsing that returns an empty list instead of 416 refusal when
    nothing satisfies — explicit refusal test.
13. A QP decoder that accepts lowercase `=3d` while claiming strictness —
    refusal test; the robustness option is declined in the KDoc.
14. PEM reading via substring search that accepts a second block or leading
    garbage — both refuse.
15. ISO week via `Calendar`/java.time (would import the thing we claim to
    implement) — kernel is integer math; mirror is Python's independent
    isocalendar.
16. An HSL conversion "verified" against itself — mirror is colorsys, a
    different codebase in a different language.
17. EDID parsing that skips the checksum (most real-world corruption shows
    there) — mutation test.
18. A Luhn scrubber that masks every long digit run (destroys order ids) —
    non-Luhn 16-digit run must survive unchanged.
19. KeyCadence emitting a "safe"/"no keylogger" verdict — the enum has no
    such member; the SCOPE string admits hardware blindness; test pins both.

## E. Mechanism portfolio (17 rows; six fields each, abbreviated per row)

| # | Kernel | Central construction | Artifact | Proved portion | Unresolved interface | Cheapest falsification | Gap |
|---|---|---|---|---|---|---|---|
| 1 | MagnetParse | BEP 9 grammar + RFC 4648 base32 fold | kernel+20 checks | parse/normalize on host | no DHT/peer wire | feed 32-char non-base32 | local |
| 2 | Bencode | strict recursive-descent, raw-key ordering | kernel+23 checks | decode+refusals | no .torrent I/O, Long-bounded ints | `d1:a0:1:a0:e` | local |
| 3 | FlacHead | 64-bit accumulator bit-slice of streaminfo | kernel+fixture+20 | header parse | no frames/audio | flip marker byte | local |
| 4 | WebpHead | RIFF LE walk + VP8X flag bits | kernel+fixture+17 | container parse | no bitstream/pixels | break RIFF size | local |
| 5 | SemVer | item-11 comparator over typed identifiers | kernel+22 | ordering chain | no range syntax (^~) | "beta.11 vs beta.2" | local |
| 6 | UuidInfo | variant-first bit classification | kernel+18 | classify+v7 ms | no generation | Microsoft variant version | local |
| 7 | HttpRange | satisfiability resolution vs length | kernel+19 | resolve/416 | no multipart emit | `bytes=-0` | local |
| 8 | QuotedPrint | strict uppercase escapes + soft breaks | kernel+19 | codec round trip | no charset/MIME tree | `=3d` | local |
| 9 | PemBound | boundary+label grammar, canonical b64 | kernel+18 | single block | no ASN.1 | `QUJDRB==` | local |
| 10 | IsoWeek | days-from-civil + first-Thursday rule | kernel+21 | 7 pinned dates | no timezone | 2021-01-01 | local |
| 11 | HslColor | CSS f(n) closed form | kernel+18 | conversion | no gamut/oklch | colorsys disagreement | local |
| 12 | EdidHead | big/little-endian mixed layout + checksum | kernel+fixture+18 | base block | no DDC bus access | corrupt byte 127 | local |
| 13 | VttTime | strict timestamp grammar + cue line | kernel+19 | timings | no cue text/styling | `00:01,000` | local |
| 14 | LuhnCheck | mod-10 + PCI mask + conservative scrub | kernel+17 | validate/mask | no issuer/BIN truth | 16-digit non-Luhn run | local |
| 15 | Soundex | H/W-transparent run-length coding | kernel+19 | NARA vectors | no fuzzy ranking | Ashcraft→A226 wrong impl | local |
| 16 | PdfName | #XX escape codec with #00 ban | kernel+17 | Table 4 vectors | no PDF structure | `/bad#00name` | local |
| 17 | KeyCadence | mean/SD window classifier, pinned 2026 thresholds | kernel+17 | math+verdicts | cannot see inline HW logger (equivalent to original problem — stated, not solved) | randomized-delay injection passes as INDETERMINATE | **not local; downgraded** |

Early independence: kernels share no code, no helpers, and compile alone;
the only cross-kernel coupling is the consumer file.

## F. Artifact requirement

Every row above lands as: Kotlin source + executed consumer assertions +
Python mirror + (for binary kernels) a byte-pinned fixture with sha256 in
the lab receipt. No status-report rows.

## G. Second-order adversary (per candidate, strongest attack)

- A(MagnetParse): hybrid link with *conflicting* duplicate btih — refused as
  duplicate; mixed-case base32 — folded before decode; `%2` tail — refused.
- A(Bencode): 40-deep nesting bomb — depth cap refuses; 20-digit integer —
  Long-scope refusal (declared narrowing, not silent wrap).
- A(FlacHead): type-127 block first — explicit forbid; length 35 — refused
  even though the buffer is big enough.
- A(WebpHead): canvas 2^24 x 2^24 (each dim legal, product overflows) —
  refused via the 2^32-1 budget.
- A(SemVer): `1.0.0-01` (leading-zero numeric identifier) — refused;
  `01.0.0` — refused; build `+001` — allowed (spec says so).
- A(UuidInfo): version nibble 7 with Microsoft variant — version suppressed,
  unixMs null (quantifier order: variant BEFORE version).
- A(HttpRange): `bytes=5000-,0-0` — must not 416 (one member satisfies);
  `bytes=-5` on an empty representation — must 416.
- A(QuotedPrint): `=` as last char of final line (no following line) —
  refused; 77-char line — refused.
- A(PemBound): base64 non-canonical final quantum `QUJDRB==` — stdlib
  accepts, strict kernel refuses (mirror checks the bit math explicitly).
- A(IsoWeek): 2024-12-30 (a Monday that already belongs to next year's W01)
  — pinned; year-0 — refused.
- A(HslColor): hue -150 vs 570 vs 210 — all identical output.
- A(EdidHead): checksum fixed up after a mutation (fixSum) so only the
  attacked field differs — tests mutate THEN re-checksum so the refusal is
  attributable to the field, not the checksum.
- A(VttTime): `00:01.000--> 00:02.000` (missing left space) — refused.
- A(LuhnCheck): `79927398713` inside text (11-digit Luhn-valid) — NOT
  masked; PAN-length floor enforced.
- A(Soundex): Burroughs (same-code letters across H) — B620 not B622;
  O'Brien — refused, caller must strip punctuation explicitly.
- A(PdfName): `/bad#4` truncated escape — refused; round trip over
  delimiter-heavy string.
- A(KeyCadence): human-speed input with SD 10 ms (randomized machine) —
  INDETERMINATE, not HUMAN_PLAUSIBLE; this is the honest failure mode the
  QUACK paper forces.

## H. Verification bindings

- kotlinc exit codes for 17 solo compiles + 1 consumer compile.
- One JVM execution: `R38 PASS: 326 production-kernel assertions across 17
  kernels.`
- `check_r38_reference.py`: 17 mirrors, five of them held-out stdlib
  implementations (uuid, quopri, colorsys, datetime, statistics/base64).
- `test_r38_tools.py`: 24 unittest cases (hygiene, collisions, fixture
  bytes, spec-pin strings, mirror strictness, sha256 pins).
- Lab: `r38_lab.py host` re-runs R38 + R37 (regression) + R34 (repair
  regression) + static gates + full Python suite set, stamps the receipt.
- CI (after push): the native workflow's JVM job is the both-flavor compile
  receipt; device jobs are read by annotations only.
- No adequate verifier exists for: real swarm behavior, real display EDID
  over DDC, real mail transport, hardware keyloggers. Claims there are
  downgraded to parsing/scoping statements.

## I. Repair policy

Applied once already this round: the beam census sentence falsely claimed a
first kernel; repaired (script + JSON) and the deepen re-run twice for
idempotency. Any kernel repair after CI invalidates trust: re-run
run_r38_contracts + mirrors + lab, and add a new attack at the patch site.

## J. Stopping

Stop only when: contracts 326/326, mirrors 17/17, tool tests 24/24, lab
PASS, census validated twice, docs written, commit pushed, CI triaged by
exact run ids, ledger committed. Known-open items are listed in
R38-RESULTS with the next highest-information test for each.
