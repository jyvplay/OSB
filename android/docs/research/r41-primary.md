# R41 primary-source record (2026-10-24)

## Fetched primaries

None newly fetched this round. The round's spec families are: prior
fetched primaries (BEP 3 for PiecePlan — fetched R38), EXECUTABLE
verifications that outrank quotation (shlex/unicodedata/timedelta/
base64/urllib agreement), and search-pins below with downgrades
declared.

## Executable verifications (stronger than citation, all ran)

- **ShlexJoin ≡ shlex**: quote/join agree on the full vector set incl.
  the `''` token, `$HOME`, newline and the mixed join.
- **LineWidth ⊂ UAX #11**: every tested code point's class (W/F/H/Na +
  combining) agrees with `unicodedata.east_asian_width` — 漢 カ 한 ｱ Ａ
  `　` a é‐combining, plus CJK Ext-B U+20000.
- **DurationIso exact parts ≡ timedelta** on PT1H30M / P1W / P2DT3H4M5S
  / P365D / PT90M.
- **DataUri ≡ base64+urllib** on the RFC 2397 example and escape paths.
- **TabStops.expand ≡ str.expandtabs** on the shared vectors.

## Search-pinned (downgrades declared)

- ECMA-376 §18.17.2 A1/R1C1 reference styles and grid bounds (ECMA PDF
  not fetched; bounds also executed via ColLetters' pinned XFD).
- GS1 General Specifications §7.9 check digit (GS1 PDF not fetched;
  algebra pinned by three published-family vectors incl. an ISBN-13).
- Flesch (1948) / Kincaid (1975) constants; bands as published.
- pg_trgm padding convention (PostgreSQL docs wording; similarity not
  byte-compared against a running PostgreSQL — stated).
- UAX #11 range table (subset implemented; per-vector verification via
  unicodedata substitutes for fetching the data file).
- git object-store sharding shape (observable in any git repo; no
  citation needed beyond the executed inverse).

## Currency searches (fresh this session)

- **Tails: 7.15.1 remains current** (blog.torproject.org, tails.net,
  release-history roundups). New useful context pinned for the PRD: the
  7.x series is the FIRST on Debian 13 (Trixie), and the project moved
  to a two-week scheduled cadence in September (7.12 onward:
  15/13/14/13-day gaps), which sets the expected window for the next
  scheduled release (~Oct 27–28). Tor Browser 15.0.26 stands.
- **Local-attack currency for the PRD**: MITRE T1674 (Input Injection)
  now carries concrete detection strategies (DET0568: HID enumeration
  under /sys/bus/usb/devices + rapid-injection-to-command-execution
  correlation) — aligning with this repo's descriptor/timing ladder.
  RatHat (Sept 2026) demonstrates the ACCESSIBILITY-side local input
  vector on Android: malware abusing a11y services to unlock developer
  options and extract ADB pairing codes — a software cousin of keystroke
  injection that the PRD now names. O.MG Elite gen-3 numbers unchanged
  (890 keys/s, 1.5M keystrokes, inline FullSpeed keylogger).

## Held-out verifier inventory (check_r41_reference.py)

shlex (quote/join), unicodedata (east_asian_width/combining),
datetime.timedelta, base64 + urllib.parse.unquote_to_bytes,
str.expandtabs. None share a line with the kernels.
