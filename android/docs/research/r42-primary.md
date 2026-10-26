# R42 primary-source record (2026-10-24, second session)

## Executable verifications (ran; stronger than quotation)

- ISO 32000-1 §7.9.4's OWN example `D:199812231952-08'00` parses to the
  documented fields and round-trips.
- Damm 2004: the published order-10 table re-entered INDEPENDENTLY in
  Kotlin and Python; the algorithm's two guarantees (all single-digit
  errors, all adjacent transpositions) executed as exhaustive mutation
  sweeps on both sides — 60 single mutations + all adjacent swaps.
- NMEA classic GGA fields (4807.038,N / 01131.000,E) convert to the
  textbook decimal degrees and format back.
- GS1-family lineage reused (R41's EAN pins stand).

## Search-pinned (downgrades declared)

- RFC 6797 §6.1 HSTS grammar incl. the unknown-directive tolerance and
  the once-only directive rule (RFC text not re-fetched; both clauses
  are executed as tests, which is the stronger anchor).
- GPC specification: `Sec-GPC: 1` is the only defined value; the
  .well-known/gpc.json shape. (Spec page not fetched this round.)
- Unix ar / Debian .deb outer layout (60-byte headers, even padding,
  `debian-binary` first member).
- BITMAPFILE/INFOHEADER layout and the 4-byte row-stride law.
- ECMA-48 cursor-final defaults (missing/0 parameter → 1).
- ECMA-376 $-references and mergeCells no-overlap rule.

## Currency searches (fresh this session)

- **Tails: 7.15.1 remains current** (blog.torproject.org category
  feed, tails.net/install, third-party roundups re-checked today).
  The two-week-cadence expectation from R41 stands: next scheduled
  release window ~Oct 27–28; nothing landed yet. NOTE FOR HONESTY: the
  keylogger/HID search was NOT re-run this round (budget); the PRD
  reuses R41's 2026-10-24 pins and labels them as one day old.

## Mirror inventory (check_r42_reference.py)

Handwritten recomputations + struct/math/urllib/str.title/bytes.hex.
No NEW held-out stdlib family applied to these surfaces; the mirror
header states this downgrade explicitly instead of overclaiming.
