# R31 privacy PRD — Tails-mod companion profile (fresh research, Oct 2026)

Working branch `arena/350b91e4-osb`. Prior: R30-PRIVACY-PRD. Search
synthesis: `research/r31-primary.md` (all four sections). This PRD covers a
COMPANION PROFILE inside Fusion plus OS settings it recommends. It is not an
OS, not Tails, and no footprint/anonymity comparison is claimed without
measurement. R31 executes one privacy kernel (LogScrubber, diagnostics
hygiene); the rest is baseline reconfirmation + OS-control documentation.

## 0. Honest positioning (unchanged, restated)

- Tails 7.15 (13 Oct 2026, reconfirmed R31; 7.16 due ~27 Oct on cadence):
  Tor Browser 15.0.25, Tor 0.4.9.14, kernel 6.12.111. Fusion cannot
  reproduce OS-level Tor enforcement, amnesia, or firmware trust from
  inside an app.
- "Lighter than Tails" is not a claim until both sides are measured on the
  same hardware with the same workload. The target below is: smallest honest
  footprint for a bounded task set, with every number receipted.

## 1. PRD features (new or confirmed R31)

| ID | Feature | Status R31 | Evidence |
|---|---|---|---|
| P-ANON-1 | Orbot handoff, 17.9.7 (Tor 0.4.9.14); audit fixes itemized (ORB-02-011 critical disable, ORB-02-004 consent bypass, traversal, hardcoded creds) | Authored, unexecuted (reconfirmed current R31) | Launcher-intent path; parse-gated; device run pending |
| P-ANON-2 | Tor VPN (Arti, F-Droid 1.9.1Beta build 242) as NEXT handoff | Documented (reconfirmed: still beta/experimental) | Experimental label inherited; no code until stable + review |
| P-CONS-1 | ConsentLedger receipts | EXECUTED (R28, unchanged) | run_r28_contracts + reference |
| P-CONS-2 | ConsentDialog UI | Authored (unchanged) | Parse-gated; strict-clean; device run pending |
| P-CONS-3 | Passkey readiness: Credential Manager transfer API / FIDO CXP-CXF watch | Documented (unchanged) | NO code (needs API 34+ device + review) |
| P-USB-1 | UsbWatchReceiver detection/alerting | Authored (R28) + R29 wiring (unchanged) | Advisory text only; still records + notifies. Parse-gated; app-manifest registered. Device run pending. Detection ONLY. |
| P-USB-1b | UsbAllowlist: private consented-device set (vid:pid grammar, 64-cap) | Store logic EXECUTED (R29 vs in-memory fake, unchanged); wiring authored | Membership means "seen + consented before", NEVER hardware trust (VID:PID spoofable). |
| P-USB-2 | Recommend Android 16+ USB Protection | Documented (caveat new R31: may move to optional "Expert features") | Platform feature; Fusion relies on nothing |
| P-USB-3 | Recommend GrapheneOS USB-C port control (five modes, locked default, DUAL-LAYER: hardware data-line disable + kernel second layer) | Documented (extended R31) | OS feature; the concrete BadUSB closure Fusion cannot replicate |
| P-USB-4 | USB-C Authentication note: 2016 standard, weak adoption | Documented (unchanged) | No enforcement claim; OS controls + detection remain the posture |
| P-A17-1 | Recommend Android 17 Advanced Protection six-feature set (Intrusion Logging with 12-month rolling retention + separate opt-in, USB Protection, Accessibility Protection, Disable WebGPU, Failed Authentication Lock, View Supporting Apps) | Documented (extended R31) | OS features; Fusion duplicates none; needs Pixel device receipts |
| P-GOS-1 | GrapheneOS controls note (duress PIN, 18h auto-reboot, secure element, secure clipboard; Motorola 7-year/Razr plans) | Documented (extended R31) | OS properties only; no in-app replication |
| P-FOR-1 | Recommend Intrusion Logging for high-risk users | Documented (unchanged) | Fusion keeps no cloud logs |
| P-DIAG-1 | LogScrubber: PII-shape redaction (emails, phone runs, bearer/token secrets, device paths), idempotent | EXECUTED (R31, host) | run_r31_contracts + reference. Wiring to the "Share redacted report" control is future; shapes are not a leak proof. |
| P-KEY-1 | Shuffled pad + no-IME digit pad | Existing (device-tested) | Screen/pointer/AX observers still trusted |
| P-KEY-2 | BadUSB HID injection response | Partial (policy + inventory + authored detection + allowlist softening + OS dual-layer port control recommended) | Enforcement = OS controls. Passive inline loggers remain undetectable by any software — stated, not solved. |
| P-KEY-3 | Typed-secret reduction via passkeys/hardware keys | Documented (unchanged) | Reduces the software-keylogger yield; does NOT detect keyloggers. |
| P-KEY-4 | Recommend Android 17 Accessibility Protection (verified Accessibility Tools only) as the OS anti-overlay/keylogger control | Documented R31 | OS feature; closes the malicious-AX-service keylogger path where enabled. Fusion claims no equivalent. |

## 2. Hardware-vector closure statement (the most common versions)

Most common local hardware-origin vectors, each with its honest status: (1)
BadUSB HID injection via malicious USB — closed by OS dual-layer port
control (P-USB-3/P-USB-2) plus in-app detection/advisory (P-USB-1/1b);
in-app enforcement is impossible and unclaimed. (2) Software keyloggers via
malicious IME/accessibility services — yield reduced by typed-secret
reduction (P-KEY-3) and closed where the OS restricts AX to verified tools
(P-KEY-4); detection of an installed logger is unclaimed. (3) Passive inline
USB loggers — undetectable by any software, stated not solved. (4)
Shoulder-surfing/screen observation — mitigated by existing pads (P-KEY-1),
observers still trusted. No vector is claimed closed by Fusion code alone.

## 3. Footprint discipline (anti-bloat rules)

1. No engine bundled without a size receipt (B2's 37.9 MB upstream AAR is a
   published number, not an in-repo intake — acceptance still needs all four
   stopping-rule gates).
2. Every new permission needs a PRD row justifying it (R31 adds NO
   permissions and NO components; manifest-scope gate pins the R28 set).
3. Amnesia posture: allowlist/consent records are bounded and prunable; the
   companion stays no-INTERNET; nothing phones home.

## 4. Refusals (explicit non-goals)

- In-app Tor/VPN routing code, root/nftables application, keylogger
  "detection", forensic-erasure promises, Tails-equivalence or footprint
  superiority claims, own cloud forensic logging, server-backed sync,
  Credential Manager code before a device run, USB-C Authentication
  enforcement claims, allowlist-as-trust claims, GrapheneOS-control
  replication claims, SMS sending/telephone permissions, redaction-as-leak-proof.

## 5. Next highest-information tests

1. Disposable-emulator run: USB attach broadcast -> notification/record appears;
   allowlisted re-attach softens text but still records (P-USB-1/1b).
2. Device run: ConsentDialog confirm burns exactly one receipt; cancel/back
   burns none (P-CONS-2).
3. API 34 disposable-device run: passkey create/get via Credential Manager
   (promotes P-CONS-3 from documented to authored).
4. Tor VPN stable-release review: re-evaluate P-ANON-2 when the Tor Project
   lifts the experimental marking.
5. Wire LogScrubber behind the "Share redacted report" control and device-run
   a share (promotes P-DIAG-1 from executed-kernel to wired feature).
