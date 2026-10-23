# R38 primary-source record (fetched vs searched, 2026-10-23)

Honesty note: "fetched" = the page body was retrieved and read this round;
"searched" = result snippets from multiple independent hits were used and are
corroborated by an executable stdlib mirror where one exists. Specs behind
paywalls (ISO 32000-1, ISO/IEC 7812, VESA E-EDID release docs) are pinned via
public mirrors/secondary documentation plus executable cross-checks, and that
downgrade is stated here rather than hidden.

## Fetched

- BEP 3, bittorrent.org/beps/bep_0003.html (version 0e08ddf8, final):
  bencoding grammar incl. `i-0e` invalid, leading zeros invalid except
  `i0e`, dict keys "sorted as raw strings, not alphanumerics"; 2017
  clarification that validators must reject invalid metainfo rather than
  decode-encode roundtrip.
- BEP 9, bittorrent.org/beps/bep_0009.html (version cfd7cd53): magnet v1/v2
  URI grammar; 40-hex and 32-base32 btih forms; btmh multihash; `xt`
  mandatory, `dn`/`tr`/`x.pe` optional and repeatable; hybrid rule.

## Searched with executable or multi-source corroboration

- RFC 9639 (FLAC): streaminfo table (u16/u16/u24/u24/u20/u3/u5/u36/u128),
  block-header layout, type 127 forbidden, block sizes 16..65535 —
  rfc-editor.org results + datatracker; fixture built to this layout and
  parsed by independent Python bit math.
- RFC 9649 (WebP): VP8X 10-byte payload, Rsv(2) I L E X A R flag bits,
  reserved 24 bits, canvas minus-one u24le pair, W*H <= 2^32-1 —
  rfc-editor + chromium libwebp container-spec + libwebp flag constants
  (0x20/0x10/0x08/0x04/0x02) agreeing across three sources.
- RFC 9562 (UUID): variant/version bit homes and the v7 test vector
  017F22E2-79B0-7CC3-98C4-DC0C0C07398F; executable verifier = Python
  `uuid` (version 7, RFC_4122 variant, int>>80 == 1645557742000).
- RFC 9110 §14 Range: int-range/suffix-range ABNF and satisfiability; the
  mirror re-implements resolution independently.
- RFC 2045 §6.7: uppercase hex requirement, soft breaks, 76-char cap;
  executable verifier = Python `quopri` on the accepted vectors.
- RFC 7468 PEM: boundary lines, matching labels, 64-char body lines;
  executable verifier = `base64.b64decode(validate=True)` plus explicit
  trailing-bit math for the non-canonical case stdlib tolerates.
- semver.org 2.0.0 items 9-11 (well-known public text): the item-11
  example chain is the pinned vector set.
- W3C WebVTT timestamp grammar: w3.org presentation + MDN (hours two OR
  more digits; mm/ss 00..59; ttt exactly 3).
- VESA EDID: Wikipedia field table + VESA E-EDID A2 PDF (glenwing mirror) +
  UEFI-Utilities struct: header 00FF...FF00, big-endian 15-bit PnP triple
  (reserved bit 15), LE product/serial, week 0xFF model-year flag, year =
  value+1990, sum-to-zero checksum. Real-world anchor: 0x10AC = DEL,
  0x244D = IBM (both decode in tests).
- NARA Soundex (archives.gov coding guide + Rosetta Code note): digit
  table, H/W rule ("the consonant to the right is not coded"), vowel
  separation codes twice, first-letter participation; worked examples
  Tymczak T522, Ashcraft A261; community-standard extras (Pfister P236,
  Burroughs B620) cross-checked by the independent Python mirror.
- ISO 32000-1 §7.3.5 name objects: Table 4 examples and the #00
  prohibition (public spec text widely mirrored; paywalled original —
  downgrade stated).
- Luhn / PCI DSS display rule: algorithm is public domain; classic vectors
  79927398713 and 4111111111111111; first6+last4 masking rule.

## Currency searches (mandated per round)

- Tails: **7.15.1 released 2026-10-23 (today)** — emergency update: Tor
  Browser 15.0.26 fixing CVE-2026-113470 and blocking all eMudhra-issued
  certificates; Debian's ca-certificates updated so the block applies
  system-wide, not just in Tor Browser (linuxiac, tails.net/news). Prior:
  7.15 (2026-10-13, Tor Browser 15.0.25, tor 0.4.9.14, firmware refresh);
  7.14 (2026-09-30, kernel 6.12.111). endoflife.date confirms 7.15.x as
  the only supported line.
- Hardware keyloggers / HID injection (2026): velocity IOC >200 keys/s
  (humans 6-18); HIDGuard pins the biomechanical floor (sustained <25-30 ms
  per stroke implausible; human SD 25-70 ms vs machine SD <1.5 ms); a
  Linux kernel HID monitoring driver was proposed Apr 2026 (itsfoss
  report); a Raspberry-Pi USB passthrough gate was published Sep 2026
  (IJRISS) — host-independent, the same topology-interposition idea as our
  stored-transcript audit; QUACK (arXiv 2604.15845) demonstrates that
  speed/regularity heuristics are evadable by randomized delays — which is
  why KeyCadence's "human window" verdict is only HUMAN_PLAUSIBLE and low
  jitter at human speed is INDETERMINATE, never "safe"; MITRE codified
  input injection as T1674. Unchanged ground truth: a passive inline
  hardware logger adds no software-visible signature; physical inspection
  and FIDO2 (captured password is useless) remain the honest mitigations.

## Not established / explicitly not claimed

- No live USB bus, no DDC/EDID read from a real display, no device
  execution of any kernel, no network fetch of any magnet/torrent, no
  claim that KeyCadence detects hardware keyloggers (it cannot).
