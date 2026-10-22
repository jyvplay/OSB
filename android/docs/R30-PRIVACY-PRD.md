# R30 privacy PRD — Tails-mod companion profile (fresh research, Oct 2026)

Working branch `arena/350b91e4-osb`. Prior: R29-PRIVACY-PRD. Search
synthesis: `research/r30-primary.md` (all four sections). This PRD covers a
COMPANION PROFILE inside Fusion plus OS settings it recommends. It is not an
OS, not Tails, and no footprint/anonymity comparison is claimed without
measurement. R30 executes no privacy kernels; the turn's privacy work is
baseline reconfirmation + OS-control documentation.

## 0. Honest positioning (unchanged, restated)

- Tails 7.15 (13 Oct 2026, reconfirmed R30): Tor Browser 15.0.25, Tor
  0.4.9.14, kernel 6.12.111, 2-week cadence. Fusion cannot reproduce OS-level
  Tor enforcement, amnesia, or firmware trust from inside an app.
- "Lighter than Tails" is not a claim until both sides are measured on the
  same hardware with the same workload. The target below is: smallest honest
  footprint for a bounded task set, with every number receipted.

## 1. PRD features (new or confirmed R30)

| ID | Feature | Status R30 | Evidence |
|---|---|---|---|
| P-ANON-1 | Orbot handoff, rebased to 17.9.7 (Tor 0.4.9.14, ORB-02 audit fixes) | Authored, unexecuted (reconfirmed current R30) | Launcher-intent path; parse-gated; device run pending |
| P-ANON-2 | Tor VPN (Arti, Beta 9.1) as NEXT handoff | Documented (reconfirmed: still experimental, not for high-risk users) | Experimental label inherited; no code until stable + review |
| P-CONS-1 | ConsentLedger receipts | EXECUTED (R28, unchanged) | run_r28_contracts + reference |
| P-CONS-2 | ConsentDialog UI | Authored (unchanged) | Parse-gated; strict-clean; device run pending |
| P-CONS-3 | Passkey readiness: Credential Manager transfer API / FIDO CXP-CXF watch | Documented (unchanged) | NO code (needs API 34+ device + review) |
| P-USB-1 | UsbWatchReceiver detection/alerting | Authored (R28) + R29 wiring (unchanged) | Advisory text only; still records + notifies. Parse-gated; app-manifest registered. Device run pending. Detection ONLY. |
| P-USB-1b | UsbAllowlist: private consented-device set (vid:pid grammar, 64-cap) | Store logic EXECUTED (R29 vs in-memory fake, unchanged); wiring authored | Membership means "seen + consented before", NEVER hardware trust (VID:PID spoofable). |
| P-USB-2 | Recommend Android 16+ USB Protection | Documented (unchanged) | Platform feature; Fusion cannot enforce it |
| P-USB-3 | Recommend GrapheneOS USB-C port control, rebased to 2026101600 (five modes, "Charging-only when locked" default) | Documented (extended R30) | OS feature; Fusion cannot replicate it |
| P-USB-4 | USB-C Authentication note: 2016 standard, weak adoption | Documented (unchanged) | No enforcement claim; OS controls + detection remain the posture |
| P-A17-1 | Recommend Android 17 Advanced Protection set | Documented (unchanged) | OS features; Fusion duplicates none; needs Pixel device receipts |
| P-GOS-1 | GrapheneOS 2026101600 controls note: duress PIN wipe, 18h auto-reboot default, secure-element rate limiting, secure clipboard | Documented R30 | OS properties only; no in-app replication; needs device receipts |
| P-FOR-1 | Recommend Intrusion Logging for high-risk users | Documented (unchanged) | Fusion keeps no cloud logs |
| P-KEY-1 | Shuffled pad + no-IME digit pad | Existing (device-tested) | Screen/pointer/AX observers still trusted |
| P-KEY-2 | BadUSB HID injection response | Partial (policy + inventory + authored detection + allowlist softening) | Enforcement = OS controls. Passive inline loggers remain undetectable by any software — stated, not solved. |
| P-KEY-3 | Typed-secret reduction via passkeys/hardware keys | Documented (unchanged) | Reduces the software-keylogger yield; does NOT detect keyloggers, and phishing-resistant != compromise-proof. |

## 2. Footprint discipline (anti-bloat rules)

1. No engine bundled without a size receipt (B1/B2 rules in ALTERNATIVES;
   Servo 0.5.0 stability changes facts, not acceptance).
2. Every new permission needs a PRD row justifying it (R30 adds NO
   permissions and NO components; manifest-scope gate pins the R28 set).
3. Amnesia posture: allowlist/consent records are bounded and prunable; the
   companion stays no-INTERNET; nothing phones home.

## 3. Refusals (explicit non-goals)

- In-app Tor/VPN routing code, root/nftables application, keylogger
  "detection", forensic-erasure promises, Tails-equivalence or footprint
  superiority claims, own cloud forensic logging, server-backed sync,
  Credential Manager code before a device run, USB-C Authentication
  enforcement claims, allowlist-as-trust claims, GrapheneOS-control
  replication claims, SMS sending/telephone permissions.

## 4. Next highest-information tests

1. Disposable-emulator run: USB attach broadcast -> notification/record appears;
   allowlisted re-attach softens text but still records (P-USB-1/1b).
2. Device run: ConsentDialog confirm burns exactly one receipt; cancel/back
   burns none (P-CONS-2).
3. API 34 disposable-device run: passkey create/get via Credential Manager
   (promotes P-CONS-3 from documented to authored).
4. Tor VPN stable-release review: re-evaluate P-ANON-2 when the Tor Project
   lifts the experimental marking.
