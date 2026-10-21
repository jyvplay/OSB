# R29 primary-source research (search synthesis, 2026-10-21)

Scope note: this turn's findings come from fresh web-search result synthesis
(2026-10-21), NOT from fetched-page excerpts. Claims below are therefore
weaker than R28's fetched primaries: each section states what R29 takes, what
it refuses, and the fetch/device receipt that would promote the claim.

## 1. Orbot 17.9.7 / Tor 0.4.9.14: audit fixes land, external start deleted

Search synthesis (2026-10-21): Orbot 17.9.7 is current (16 Oct 2026) on Tor
0.4.9.14; 17.9.6 fixed the independent-audit ORB-02 findings and DELETED the
external start path (`StartTorReceiver`). No Tor VPN (Arti) stable-release
signal; it stays experimental.

R29 take: OrbotHandoff is unaffected — it uses only
`getLaunchIntentForPackage` (launcher intent), never the deleted receiver.
PRD records the 17.9.7/audit baseline. R29 refusal: no new routing code, no
Tor VPN code until the Tor Project lifts the experimental marking. Promote
with: F-Droid version receipt + disposable-device handoff run.

## 2. Android 17: Advanced Protection expansion + cloud Intrusion Logging

Search synthesis (2026-10-21): Android 17 (June–Oct 2026 disclosures) expands
Advanced Protection — Accessibility Protection, disabled device-to-device
unlocking, disabled Chrome WebGPU, scam detection, enterprise policy support,
Find Hub biometric lost-lock — plus an Oct 1 six-feature update with
cloud-backed Intrusion Logging.

R29 take: PRD recommends the Android 17 Advanced Protection set where
available; Fusion duplicates none of it. R29 refusal: no OS-feature claims
from inside the app; no Intrusion Logging clone. Promote with: Pixel device
receipts (settings screens), never host tests.

## 3. Passkeys: Credential Manager transfer API + FIDO CXP-CXF (Sept 2026)

Search synthesis (2026-10-21): Android Credential Manager gains a Credential
Transfer API / FIDO CXP-CXF flow moving passwords/passkeys between managers;
Credential Manager is the current API and legacy Fido2ApiClient is
deprecated; Android 16/17 add Restore Credentials, Identity Check, and
credential-store theft hardening. NCSC/FIDO position: passkeys + hardware
keys are the phishing-resistant replacement for typed secrets.

R29 take: PRD adds a passkey-readiness row (typed-secret reduction is the
honest keylogger-vector mitigation: secrets that are never typed cannot be
keylogged). R29 refusal: no Credential Manager code this turn (needs
API 34+ device + review; authored-only code would be untestable here).
Promote with: API 34 disposable-device passkey create/get run.

## 4. USB-C Authentication: real standard, weak adoption; BADUSB-C multi-mode

Search synthesis (2026-10-21): USB Type-C Authentication dates to 2016 but
adoption is very low; BADUSB-C demonstrates Type-C multi-mode attacks
including video capture. Published defenses favor device-layer detection,
default-deny / new-keyboard authorization, allowlists, and physical
controls. Passive inline loggers remain software-undetectable.

R29 take: the OS-controls-plus-detection posture stays (USB Protection,
GrapheneOS port control recommended; receiver detects/alerts). R29 ships the
honest in-app slice: a private consented-device allowlist that softens
ADVISORY verdict text only, still recording and notifying every event.
R29 refusal: no trust claim from allowlist membership (VID:PID is
spoofable); no USB-C Authentication enforcement claim; no logger-detection
claim. Promote with: disposable-emulator attach/detach runs.
