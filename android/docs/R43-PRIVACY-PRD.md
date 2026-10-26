# R43 privacy PRD — paste hygiene, time coarsening, consent-adjacent intake

## Fresh currency (searched 2026-10-24, third session)
- **Tails 7.15.1 remains current**; the two-week-cadence window for
  7.16 (~Oct 27–28) has not produced a release yet — consistent with
  cadence, no anomaly; P-41.1 (cadence-anomaly visibility) unchanged.
- **Honesty:** the keylogger/HID search was again not re-run; R41's
  hardware pins are two days old and the matrix below says so.

## Shipped privacy work this round
- **P-43.1 (shipped): Trojan-Source paste hygiene.** `BidiStrip`
  (:core:documents) detects/strips/audits explicit bidi controls with
  balance checking; stripping is pinned as a USER-VISIBLE choice in
  the KDoc because silently rewriting legitimate RTL text would be its
  own harm. This is the local-input-attack class that needs no
  hardware at all — closing a real, common weak point.
- **P-43.2 (shipped): log-time coarsening.** `TimeCoarse` (:privacy)
  floors epochs to minute/hour/day with the IpRedact-style honesty
  line: reduces precision, NOT anonymity.
- **P-43.3 (shipped): secret-shaped string refusal.** `AgeId`
  (:feature:vault) recognizes age recipients (strict bech32 + 5→8
  regroup) and flags `AGE-SECRET-KEY-1…` as SECRET-SHAPED so logging
  layers can refuse persistence — zero cryptography by design.
- **P-43.4 (shipped): authentication-adjacent intake honesty.**
  `RpIdMatch` (:core:security) does the WebAuthn dot-boundary rule
  with `pslChecked=false` carried IN THE RESULT TYPE — and the test
  suite executes the dangerous "example.org matches org" case to
  prove why the flag exists. No PSL claim is smuggled.

## Hardware/local vectors (standing)
The R41 matrix stands verbatim (USB ladder shipped with stated evasion
bounds; passive inline hardware not software-detectable; accessibility
abuse named as platform-boundary). This round's addition is the
SOFTWARE-side local vector above (bidi controls), which the matrix now
references as the zero-hardware row.

Standing honesty: an Android app CANNOT be Tails — no amnesia, no
OS-level Tor enforcement, no RAM wipe. TorPosture stays
detection-not-enforcement.
