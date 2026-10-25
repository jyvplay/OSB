# R44 privacy PRD — GPS presence audit, header hygiene, Tails cadence

## Fresh currency (searched 2026-10-24, fourth session)
- **Tails 7.15.1 remains current.** Upgrade to P-41.1's basis: the
  OFFICIAL release-schedule page documents the policy — "a new
  version of Tails on each new release of Firefox, usually every 2
  weeks" — so the cadence-anomaly feature now rests on documented
  policy, not observation. Window for 7.16 (~Oct 27-28) stands open;
  no anomaly.
- **Honesty:** the keylogger/HID search was not re-run; R41's
  hardware pins are three days old and the matrix inherits unchanged.

## Shipped privacy work this round
- **P-44.1 (shipped): GPS presence made legible before export.**
  `GpsRational` (:privacy) converts EXIF GPS rationals exactly so
  consent screens can SHOW what would leak; the KDoc pins that
  conversion is not removal — stripping is the caller's explicit act.
- **P-44.2 (shipped): Accept-Language named as a fingerprint
  vector IN the parser's scope** (:feature:browser) — the policy
  layer that consumes it is explicitly licensed to send less.
- **P-44.3 (shipped): integrity intake without crypto creep.**
  `SriHash` parses and selects strongest-wins but never hashes;
  digests are computed where bytes are, keeping key-shaped code out
  of the parsing layer.
- **P-44.4 (shipped): history privacy semantics.** `HistDedup`
  implements ignorespace — the shell convention that a leading space
  keeps a command out of history — exactly and testably.

## Hardware/local vectors (standing)
The R41 matrix stands verbatim; R43's zero-hardware bidi row and the
USB ladder are unchanged. No new rows without new evidence.

Standing honesty: an Android app CANNOT be Tails — no amnesia, no
OS-level Tor enforcement, no RAM wipe. TorPosture stays
detection-not-enforcement.
