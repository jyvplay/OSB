# R42 privacy PRD — consent signals, policy honesty, Tails watch

## Fresh currency (searched 2026-10-24, second session)

- **Tails 7.15.1 remains current** (re-checked today). The two-week
  cadence window for the next scheduled release (~Oct 27–28) has not
  produced one yet — consistent with the cadence, no anomaly. P-41.1
  (cadence-anomaly visibility) stands as the design requirement this
  watch would feed.
- **Honesty note:** the keylogger/HID currency search was NOT re-run
  this round (budget allocation); the matrix below reuses R41's pins,
  which are one day old. No claim of fresh hardware-vector currency is
  made.

## Shipped privacy work this round

- **P-42.1 (shipped): GPC intake that cannot manufacture consent.**
  `GpcHeader` (:privacy) parses `Sec-GPC` and `/.well-known/gpc.json`
  with the refusal posture the legal surface demands: the ONLY defined
  header value is `1`; anything else REFUSES rather than coercing to a
  consent state in either direction. Executed both ways (0 and "true"
  refuse; false in gpc.json is false, not an error).
- **P-42.2 (shipped): password-policy honesty.** `PwPolicy`
  (:feature:vault) checks compliance with its scope string carrying
  the orthodoxy — policy is NOT strength — and an API shape that
  cannot leak: CharArray in, booleans/counts out, never substrings.
  ShannonBits (entropy) and HibpRange (breach) remain the other two
  legs; the three are deliberately separate kernels.
- **P-42.3 (shipped): typed-code error detection, bounded claim.**
  `PairPin` (:feature:continuity) gives pairing codes Damm check
  digits — ALL single-digit errors and adjacent transpositions caught,
  executed as sweeps — with "NEVER a security property" in its scope
  string. Entropy/rate-limiting live in the session layer.
- **P-42.4 (shipped): transport-security header intake.** `HstsParse`
  (:feature:browser) — spec-faithful tolerance for unknown directives,
  duplicate refusal, and max-age=0 surfaced as the delete-policy
  signal it is.

## Hardware/local vectors (standing, matrix unchanged)

The R41 matrix stands verbatim: USB-origin ladder (UsbDesc +
hid_topology + KeyCadence) shipped with stated evasion bounds; passive
inline hardware NOT software-detectable (hardware detectors remain the
market answer); accessibility-abuse named as a platform-boundary
exclusion; FIDO2 + randomized KeypadLayout remain the mitigations for
capture-and-replay. No new rows claimed without new evidence.

Standing honesty, restated: an Android app CANNOT be Tails — no
amnesia, no OS-level Tor enforcement, no RAM wipe. TorPosture stays
detection-not-enforcement.
