# R41 privacy PRD — Tails cadence, accessibility-side input attacks

## Fresh currency (searched 2026-10-24)

- **Tails 7.15.1 remains current.** Newly pinned context: the 7.x line
  is the first on Debian 13 (Trixie), and since September the project
  runs a TWO-WEEK scheduled cadence (7.12→7.15 gaps: 15/13/14/13 days),
  putting the next scheduled release in the ~Oct 27–28 window. For the
  Tails-mod PRD this cadence is itself a requirement input:
  **P-41.1 (design):** the mod's update-posture surface should present
  "days since newest upstream release" against the KNOWN cadence, so a
  silent upstream (possible censorship or infrastructure incident)
  becomes visible as an anomaly, not just staleness. Detection-only,
  no auto-update claim.

## The round's input-vector addition: the ACCESSIBILITY cousin

RatHat (Sept 2026, Android) demonstrates the local input attack that
needs no USB hardware at all: abuse of accessibility services to read
the UI tree, synthesize clicks, unlock Developer Options and extract
ADB pairing codes. MITRE T1674 (Input Injection) now ships concrete
detection strategies (DET0568) correlating HID enumeration with
rapid injection-to-execution.

Consequences, stated plainly:

1. This repo's hardware ladder (UsbDesc descriptors, hid_topology
   transcripts, KeyCadence timing) addresses the USB-origin vector; it
   does NOT and cannot address a malicious app holding accessibility
   permission — that is Android's permission model's jurisdiction.
2. What an app CAN honestly do about the a11y vector for ITS OWN
   surfaces: sensitive views (vault entry, TouchCode) already use the
   existing exposure probes; **P-41.2 (design):** extend the exposure
   checklist with an "a11y-service-present" posture line — detection
   and user notice, never blocking (blocking a11y breaks real users;
   the honesty line is visibility).
3. The PRD keeps the standing boundary sentence: software cannot see
   passive inline hardware (WireBadger-class hardware detectors remain
   the market's answer), and timing heuristics remain evadable at human
   cadence (QUACK) — 890 keys/s attack hardware stays the EASY case.

## Shipped privacy-adjacent kernels this round

- **ShlexJoin (:core:commands):** display-quoting that can never
  execute — closes the "copy this command" injection-confusion corner
  (rendering and parsing are now separate verified kernels).
- **KeyMigrate (:core:settings):** schema evolution that REFUSES silent
  settings loss (collision refusal executed) — privacy settings that
  survive upgrades are a privacy property.
- **DataUri (:feature:browser):** data: URLs decode-and-describe with
  strict refusals, so the request-policy layer can budget and block
  BEFORE any renderer sees bytes.
- **ShardPath (:core:storage):** deterministic content-store layout —
  no filename-derived metadata leaks into store paths (digests only).

Standing honesty, restated: an Android app CANNOT be Tails — no
amnesia, no OS-level Tor enforcement, no RAM wipe. TorPosture stays
detection-not-enforcement; the analogue-set boundary list is unchanged
otherwise.

## Keylogger matrix delta vs R40

No new rows; one row's context strengthened (a11y cousin named above so
the matrix's scope statement — "USB-origin vectors" — is explicit about
what it excludes and why that exclusion is a platform boundary, not an
oversight).
