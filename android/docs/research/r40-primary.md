# R40 primary-source record (2026-10-23, second session of the day)

## Fetched primaries (this round)

None newly fetched — the round's spec families are either already-fetched
lineage (BEP/RFC families from prior rounds), reimplementations from
specification text whose constants are independently executable
(BIP-173/350 polymod — verified by a HANDWRITTEN Python polymod agreeing
with the Kotlin one on the BIP-173 `A12UEL5L` vector and on tampered
variants), or search-pinned with the downgrade declared below. The ULID
spec fetch from the morning session remains the round family's newest
fetched primary.

## Search-pinned (downgrades declared)

- ECMA-376 §18.8.31 format-code sections, §18.17.3 error literals (the
  ECMA PDF itself not fetched; structure corroborated across SheetJS and
  xlsxwriter lineages).
- ISO 32000-1 Table 30 `/Rotate` ("shall be a multiple of 90",
  inheritable, default 0).
- 7-Zip `7zFormat.txt` signature-header layout (magic, 0.4 version,
  StartHeaderCRC over the 20 geometry bytes) — verified EXECUTABLY via
  zlib.crc32 agreement on the fixture, which is stronger than quoting.
- FNV-1a parameters and vectors (0x811C9DC5 / 0xE40C292C / 0xBF9CF968;
  64-bit 0xCBF29CE484222325 / foobar 0x85944171F73967E8) — recomputed
  independently in the mirror, not trusted from memory.
- ICAO 9303 Part 3 §4.9 — the TD3 specimen line
  `L898902C36UTO7408122F1204159ZE184226B<<<<<10` is REPRODUCED by the
  kernel's own arithmetic (all five check digits), the strongest
  anchoring available without fetching the ICAO PDF.
- Microsoft file-naming rules (reserved device stems incl. with
  extensions, trailing dot/space).
- WHATWG character references + the documented HTML5 C1/cp1252 quirk —
  the quirk is EXECUTED on the Python side (`html.unescape('&#128;') ==
  '€'`) while the kernel refuses it as a declared narrowing; both
  behaviors are pinned, neither is hidden.
- GFM task-list extension (marker-space requirement, 9-digit ordinal cap).
- Apple CFBinaryPList trailer layout — verified executably via plistlib
  round-trip on the fixture.
- Google Key-Uri (otpauth) format conventions.

## Currency searches (fresh this session)

- **Tails: no release newer than 7.15.1** (reconfirmed on tails.net and
  the Tor blog); Tor Browser 15.0.26 (Oct 21) stands; 16.0a14 alpha
  Oct 15. The 7.15.1 eMudhra block remains the latest privacy-stack
  event.
- **Hardware keystroke injection, new numbers for the PRD**: the O.MG
  Cable Elite (gen 3) ships 890 keys/sec max payload speed, 1.5M-keystroke
  payloads, and a FullSpeed inline hardware keylogger — vendor-published
  figures that put the R37/R38 timing thresholds in context (890/s is
  ~4.4× the 200/s definitively-non-human line; detection stays easy at
  machine speed and hard exactly when attackers slow to human cadence,
  which is QUACK's point). `hid-omg-detect` v2 patch is on lkml with
  per-device drvdata, a 64-sample timing window and a suspicious VID/PID
  table (O.MG classic 0x16c0:0x27db) — still unmerged. WireBadger, a
  commercial handheld malicious-cable detector (BVS, Oct 20), earned a
  TIME Best Inventions 2026 mention — notable because it is HARDWARE
  detecting hardware, consistent with this repo's standing claim that
  software alone cannot see passive inline implants.

## Held-out verifier inventory (check_r40_reference.py)

html.unescape (HtmlEntity), email.header.decode_header/make_header
(Rfc2047), zlib.crc32 (SevenZHead), plistlib.loads (PlistHead),
ipaddress (IpRedact), urllib.parse (OtpauthUri label/query behavior).
None share a line of code with the kernels.
