# R39 primary-source record (fetched vs search-pinned, 2026-10-23)

## Fetched primaries (full text retrieved this round)

- **ULID canonical spec** — github.com/ulid/spec (master, last substantive
  merge d0c7170). Pinned: Crockford alphabet
  `0123456789ABCDEFGHJKMNPQRSTVWXYZ` (I/L/O/U excluded); 26 chars =
  10 timestamp + 16 randomness; 48-bit Unix-ms timestamp ("until 10889
  AD"); lexicographic sort guarantee; monotonic same-millisecond rule =
  +1 on the random component with carry, generation MUST fail on
  overflow; largest valid ULID `7ZZZZZZZZZZZZZZZZZZZZZZZZZ` (= 2^48-1 ms),
  larger MUST be rejected by all implementations; binary layout 16 octets
  network byte order. All of these are executed assertions in Ulid.kt.

## Search-pinned (secondary; exact fetch not performed — downgrade declared)

- **Excel 1900 system** — multiple current sources incl. Microsoft KB
  lineage ("Excel incorrectly assumes that the year 1900 is a leap
  year"), Wikipedia Leap-year-problem (bug promoted into ECMA-376 as a
  requirement), readxl/duckdb-excel implementation notes: serial 1 =
  1900-01-01, serial 60 = phantom 1900-02-29, serials 61+ offset by one
  vs naive day count; 1904 system has no phantom; delta 1462 days.
- **ECMA-376 §18.3.1.13 column widths** — formula set reproduced across
  SpreadsheetML tooling (SheetJS docs, xlsxwriter-derived code):
  px = Truncate(((256*chars + Truncate(128/MDW))/256)*MDW); file width =
  Truncate(((chars*MDW+5)/MDW)*256)/256; MDW 7 px for Calibri 11 @96dpi;
  default 8.43 chars ⇒ 64 total px. ECMA-376 PDF itself not fetched here.
- **RFC 3492 Punycode** — parameters (base 36, tmin 1, tmax 26, skew 38,
  damp 700, bias 72, n0 0x80), §6.2 overflow MUST-reject. Held-out
  verification via CPython's punycode codec substitutes for a fresh RFC
  fetch; the codec agreement is the stronger evidence anyway.
- **PDF 32000-1 §7.9.5 / Table 30** — rectangle normalization; CropBox
  default = MediaBox; effective region = intersection.
- **USB 2.0 §9.6.1** — 18-byte device descriptor layout, EP0 sizes
  {8,16,32,64} below 3.0, exponent 9 at 3.x; usb.org zip not fetched.
- **ISO 13616 IBAN** — ISO text paywalled (declared); SWIFT registry
  lengths for the 12-country subset; mod-97 == 1; check digits 02..98.
- **IEEE 802 / RFC 7042** — first-octet bit0 I/G, bit1 U/L; Android
  randomized MACs are locally-administered unicast.
- **ISO 16 / MIDI 1.0** — A4=440 Hz = note 69; C4=60 convention declared
  against Yamaha C3=60.
- **RFC 3339 §5.6/§4.3** — grammar, -00:00 unknown-offset semantics; the
  two canonical examples (1985-04-12, 1996-12-19) used as vectors.

## Currency searches (fresh, 2026-10-23)

- **Tails 7.15.1 confirmed again today** (tails.net, torproject blog,
  linuxiac): emergency release; Tor Browser 15.0.26 fixes CVE-2026-113470;
  blocks ALL eMudhra certificates; Debian ca-certificates updated so the
  block is system-wide, not browser-only.
- **eMudhra saga (new since the R38 PRD was written)**: 8 intermediate CA
  private keys had 3–8 bits of entropy (one effectively 4) — cracked in
  under a second (Scott Helme); Mozilla/Chrome blocked Oct 17; eMudhra
  revoked ~14h after report; Sectigo cross-signs revoked by Oct 19;
  Chrome removes the emSign ECC Root G3 trust on Oct 27; **eMudhra is
  exiting the global TLS business entirely** (board decision Oct 22,
  csoonline/thehackernews/ars).
- **Keystroke-injection defense (2026 state)**: IEEE paper on Rubber-
  Ducky defense reporting >90% detection / <5% FP / <15 ms latency on 50
  attack runs; HID-APP (Springer) two-phase local pipeline — timing
  screen then on-device transformer intent classification, fully local;
  NVISO forensic teardown of a Digispark ATtiny85 HID implant (Oct 16,
  2026); HIDGuard (Windows) added WH_KEYBOARD_LL interception +
  CM_Disable_DevNode countermeasures and an honest threat-model doc;
  `hid-omg-detect` Linux kernel driver proposal still in review. QUACK
  (arXiv 2604.15845) remains the systematic study: naive speed/regularity
  heuristics are evadable; timing-feature models work without content
  access. Consequences for this repo are in R39-PRIVACY-PRD.

## Held-out verifier inventory (used in check_r39_reference.py)

datetime/date arithmetic (SerialDate), datetime.fromisoformat (Rfc3339),
punycode codec (Punycode), base64.urlsafe_b64decode (JwtSplit),
fnmatch.fnmatchcase (GlobMatch), int base-36 bigint mod 97 (IbanCheck).
None of these share a line of code with the Kotlin kernels.
