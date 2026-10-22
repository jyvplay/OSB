# R34 privacy PRD — Tails-mod companion profile (fresh research, Oct 2026)

Working branch `arena/350b91e4-osb`. Prior: R33-PRIVACY-PRD. Search
synthesis: `research/r34-primary.md` (all five sections). This PRD covers a
COMPANION PROFILE inside Fusion plus OS settings it recommends. It is not an
OS, not Tails, and no footprint/anonymity comparison is claimed without
measurement. R34 executes one privacy kernel (Pseudonym — keyed tokens,
correlation without raw IDs); the rest is baseline reconfirmation +
OS-control documentation.

## 0. Honest positioning (unchanged, restated)

- Tails 7.15 (reconfirmed R34 — no 7.16 by 22 Oct; in-release browser bump
  to Tor Browser 15.0.25 with NoScript 13.6.36.1984 + OpenSSL 3.5.9; 7.14's
  30-Sep date still points at ~27 Oct for 7.16): Tor 0.4.9.14, kernel
  6.12.111. Fusion cannot reproduce OS-level Tor enforcement, amnesia, or
  firmware trust from inside an app.
- "Lighter than Tails" is not a claim until both sides are measured on the
  same hardware with the same workload. The target below is: smallest honest
  footprint for a bounded task set, with every number receipted.

## 1. PRD features (new or confirmed R34)

| ID | Feature | Status R34 | Evidence |
|---|---|---|---|
| P-ANON-1 | Orbot handoff, 17.9.7 (Tor 0.4.9.14); transports now named (obfs4 0.9.0, Snowflake 2.15.1; commit 0ea112a4d) | Authored, unexecuted (reconfirmed current R34) | Launcher-intent path; parse-gated; device run pending |
| P-ANON-2 | Tor VPN (Arti, F-Droid 1.9.1Beta build 242) as NEXT handoff | Documented (reconfirmed: still beta/experimental, no new information) | Experimental label inherited; no code until stable + review |
| P-CONS-1 | ConsentLedger receipts | EXECUTED (R28, unchanged) | run_r28_contracts + reference |
| P-CONS-2 | ConsentDialog UI | Authored (unchanged) | Parse-gated; strict-clean; device run pending |
| P-CONS-3 | Passkey readiness: Credential Manager transfer API / FIDO CXP-CXF watch | Documented (unchanged) | NO code (needs API 34+ device + review) |
| P-USB-1 | UsbWatchReceiver detection/alerting | Authored (R28) + R29 wiring (unchanged) | Advisory text only; still records + notifies. Parse-gated; app-manifest registered. Device run pending. Detection ONLY. |
| P-USB-1b | UsbAllowlist: private consented-device set (vid:pid grammar, 64-cap) | Store logic EXECUTED (R29 vs in-memory fake, unchanged); wiring authored | Membership means "seen + consented before", NEVER hardware trust (VID:PID spoofable). |
| P-USB-2 | Recommend Android 16+ USB Protection (coverage extended R34: all Pixel devices on Android 16+; USB Protection + Failed Authentication Lock only on select Android 17 devices, Pixel 6+) | Documented (extended R34) | Platform feature; Fusion relies on nothing |
| P-USB-3 | Recommend GrapheneOS USB-C port control (five modes, locked default, DUAL-LAYER: hardware data-line disable + kernel second layer) | Documented (unchanged) | OS feature; the concrete BadUSB closure Fusion cannot replicate |
| P-USB-4 | USB-C Authentication note: 2016 standard, weak adoption | Documented (unchanged) | No enforcement claim; OS controls + detection remain the posture |
| P-A17-1 | Recommend Android 17 Advanced Protection six-feature set + Expert-features itemization (framework remains, expert is manual mode inside it; list still in flux; Google-Account access-log toggles) | Documented (unchanged) | OS features; Fusion duplicates none; needs Pixel device receipts |
| P-GOS-1 | GrapheneOS controls note (duress PIN, 18h auto-reboot, secure element, secure clipboard; Signature 27 first supported Motorola — November US window, MTE + SE, 7+7 updates; 2026 Moto models explicitly LACK MTE and adequate SE integration; Pixel 6/6 Pro support ends Oct 2026; Oct 6 build 2026100600 = full 2026-10-05 patch + Android 17 move) | Documented (extended R34) | OS properties only; no in-app replication. The hardware ladder keeps MTE+SE Pixels above current Moto hardware for key-storage claims. |
| P-FOR-1 | Recommend Intrusion Logging for high-risk users (detail new R34: captures Chrome Incognito network events as sites-not-pages; NEITHER user NOR Google can delete early, even on account close; events: installs, process/DNS/IP, USB transfer, cert install, lock) | Documented (extended R34) | Fusion keeps no cloud logs. Honesty boundary: "leave no trace" promises on stock Android 16+ must reckon with undeletable logs. |
| P-DIAG-1 | LogScrubber: PII-shape redaction (emails, phone runs, bearer/token secrets, device paths), idempotent | EXECUTED (R31, unchanged) | run_r31_contracts + reference. Wiring to the "Share redacted report" control is future; shapes are not a leak proof. |
| P-AMN-1 | RetentionSweep: keep/purge partition by per-kind max ages over a caller-supplied now | EXECUTED (R32, unchanged) | run_r32_contracts + reference. Deletion execution is caller duty; verdicts are not an erasure proof. |
| P-PIN-1 | PinRules: device-PIN strength policy reporting every violation | EXECUTED (R32, unchanged) | run_r32_contracts + reference. Policy only; storage, rate limiting, and the secure element are outside. |
| P-LOCK-1 | LockoutPolicy: brute-force backoff over caller instants | EXECUTED (R32, unchanged) | run_r32_contracts + reference. Attempt counting only; credential checking is outside. |
| P-AUTH-1 | TotpCode: RFC 6238 TOTP compute + ±window verify over caller instants (all six RFC vectors executed) | EXECUTED (R33, unchanged) | run_r33_contracts + reference. Code math only; secret storage, provisioning, and the authenticator UI are outside. Narrows the replay window; does NOT stop real-time relay phishing. |
| P-PSN-1 | Pseudonym: HMAC-SHA256 keyed pseudonyms (context domain separation, constant-time verify; JVM output equals Python hmac) | EXECUTED (R34, host) | run_r34_contracts + reference. Correlate-without-raw-IDs only; key storage, rotation, and analytics wiring are outside. KEYED, not anonymous — key holders re-derive the mapping. |
| P-KEY-1 | Shuffled pad + no-IME digit pad | Existing (device-tested) | Screen/pointer/AX observers still trusted |
| P-KEY-2 | BadUSB HID injection response | Partial (policy + inventory + authored detection + allowlist softening + OS dual-layer port control recommended) | Enforcement = OS controls. Passive inline loggers remain undetectable by any software — stated, not solved. |
| P-KEY-3 | Typed-secret reduction via passkeys/hardware keys | Documented (unchanged) | Reduces the software-keylogger yield; does NOT detect keyloggers. |
| P-KEY-4 | Recommend Android 17 Accessibility Protection (verified Accessibility Tools only) as the OS anti-overlay/keylogger control | Documented (unchanged) | OS feature; closes the malicious-AX-service keylogger path where enabled. Fusion claims no equivalent. |

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
observers still trusted. (5) Brute-forced device PIN — policy (P-PIN-1) plus
backoff counting (P-LOCK-1) executed as host kernels; enforcement needs the
credential-check wiring and a device run, both future. (6) Static-credential
replay — narrowed by executed TOTP code math (P-AUTH-1, 30s windows);
real-time relay phishing of typed codes is NOT stopped by TOTP and is
stated, not solved. (7) Raw identifiers in correlated diagnostics — keyed
pseudonyms executed (P-PSN-1) so crash/abuse buckets need never store the
raw ID; key custody + rotation are future, and pseudonymity is NOT
anonymity. No vector is claimed closed by Fusion code alone.

## 3. Footprint discipline (anti-bloat rules)

1. No engine bundled without a size receipt (B2's 37.9 MB upstream AAR is a
   published number, not an in-repo intake — acceptance still needs all four
   stopping-rule gates; R32 caveats stand; 0.7.0 is a monthly, not the LTS
   base; R34 monthly API churn hardens the LTS position).
2. Every new permission needs a PRD row justifying it (R34 adds NO
   permissions and NO components; manifest-scope gate pins the R28 set).
3. Amnesia posture: allowlist/consent records are bounded and prunable; the
   companion stays no-INTERNET; nothing phones home.

## 4. Refusals (explicit non-goals)

- In-app Tor/VPN routing code, root/nftables application, keylogger
  "detection", forensic-erasure promises, Tails-equivalence or footprint
  superiority claims, own cloud forensic logging, server-backed sync,
  Credential Manager code before a device run, USB-C Authentication
  enforcement claims, allowlist-as-trust claims, GrapheneOS-control
  replication claims, SMS sending/telephone permissions, redaction-as-leak-proof,
  sweep-verdicts-as-erasure-proof, PIN-policy-as-credential-security,
  backoff-counting-as-authentication, TOTP-math-as-relay-defense,
  pseudonym-as-anonymity.

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
6. Name the deletion callers that must honor RetentionSweep verdicts and
   device-run a purge (promotes P-AMN-1 from executed-kernel to wired feature).
7. Wire PinRules + LockoutPolicy behind the vault unlock path and device-run
   accept/reject/lockout (promotes P-PIN-1/P-LOCK-1 from executed-kernels to
   a wired feature).
8. Provision a test TOTP secret through the vault and device-run code
   display + ±window acceptance (promotes P-AUTH-1 from executed-kernel to
   a wired feature).
9. Name the analytics/crash callers that must mint Pseudonym tokens instead
   of storing raw IDs, provision a test key, and device-run a correlated
   report (promotes P-PSN-1 from executed-kernel to wired feature).
