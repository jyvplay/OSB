# R30 primary-source research (search synthesis, 2026-10-22)

Scope note: this turn's findings come from fresh web-search result synthesis
(2026-10-22), NOT from fetched-page excerpts. Claims below are therefore
weaker than R28's fetched primaries: each section states what R30 takes, what
it refuses, and the fetch/device receipt that would promote the claim. R29's
four sections stand unless a section below explicitly supersedes them.

## 1. Tails 7.15 reconfirmed current (13 Oct 2026, two-week cadence)

Search synthesis (2026-10-22): Tails 7.15 is current (13 Oct 2026) on the
two-week cadence running since 7.12: Tor Browser 15.0.25 (Firefox ESR 140,
NoScript, uBlock), Tor 0.4.9.14, kernel 6.12.111, refreshed non-free firmware
for newer Wi-Fi/GPU hardware, automatic upgrade from 7.0+ preserving
Persistent Storage.

R30 take: the PRD Tails baseline is reconfirmed unchanged; no Fusion code
follows from it. R30 refusal: same as R29 — no OS-level Tor enforcement,
amnesia, or firmware-trust claims from inside an app. Promote with: fetched
Tails release notes + same-hardware measured footprint comparison (still
absent).

## 2. GrapheneOS 2026101600 (16 Oct 2026, Android 17, Oct patch)

Search synthesis (2026-10-22): GrapheneOS release 2026101600 (16 Oct, Android
17, Oct 2026 patch) ships five USB-C port modes ("Charging-only when locked"
default), duress PIN wipe, 18-hour auto-reboot default, secure-element rate
limiting, plus a secure-clipboard and default-app overhaul announced Sept
2026. A Motorola Signature 27 partnership targets 2027 Qualcomm-based device
support.

R30 take: PRD extends the OS-control recommend rows (P-USB-3 port modes with
the locked default named; duress PIN / auto-reboot / secure element recorded
as OS features Fusion duplicates none of). R30 refusal: no in-app
replication of port control, duress wipe, or reboot timers — these are OS
properties and stay documented-only. Promote with: GrapheneOS device receipts
(settings screens), never host tests.

## 3. Servo 0.5.0 stable embed API (31 Aug 2026); Ladybird still desktop-only

Search synthesis (2026-10-22): Servo 0.5.0 is a stable embeddable Rust engine
release (31 Aug 2026) with Android support extended to v10 (~91% market),
WPT around 78%, a Servoshell demo, and a crates.io embedding API. Ladybird
remains an alpha 2026 Linux/macOS effort whose mobile port is explicitly not
a current focus; beta-2027/stable-2028 targets are unverified.

R30 take: ALTERNATIVES B2 is updated to Servo 0.5.0 stable + embed API but
stays CANDIDATE (intake/size receipt still required); B3 Ladybird stays
REFUSED with the explicit mobile-not-focus note. R30 refusal: no engine code
or bundling claim — the row update is baseline hygiene, not a new claim.
Promote with: engine_intake + size receipt + Servoshell smoke on a disposable
device.

## 4. Tor VPN Beta 9.1 / Arti 2.6.0 / Orbot 17.9.7: still beta, still current

Search synthesis (2026-10-22): Tor VPN Beta (Arti-based, per-app circuits,
F-Droid/Play/APK, Beta 9.1 on 1 Sept) remains explicitly NOT for high-risk
users, with device identifiers able to leak; Arti 2.6.0 released 1 Sept 2026;
Orbot 17.9.7 (16 Oct, Tor 0.4.9.14) is still current.

R30 take: P-ANON-1 (Orbot handoff baseline) and P-ANON-2 (Tor VPN as NEXT
handoff, experimental) are reconfirmed unchanged; no routing code. R30
refusal: no Tor VPN code until the Tor Project lifts the experimental
marking; no anonymity claim from a launcher handoff. Promote with: stable
release + F-Droid version receipt + disposable-device handoff run.
