# R36 privacy PRD — Tails-mod companion profile (fresh research, Oct 2026)

Working branch `arena/350b91e4-osb`. Prior: R35-PRIVACY-PRD. Search
synthesis: `research/r36-primary.md` (all five sections). This PRD covers a
COMPANION PROFILE inside Fusion plus OS settings it recommends. It is not an
OS, not Tails, and no footprint/anonymity comparison is claimed without
measurement. R36 executes one privacy kernel (KAnon — k-anonymity auditing)
and two trust-adjacent kernels (AttemptWindow — rate limiting;
ShannonBits — entropy measurement); the rest is baseline reconfirmation +
OS-control documentation.

## 0. Honest positioning (unchanged, restated)

- Tails 7.15 (reconfirmed R36 — no 7.16 by 22 Oct; itemized as Tor Browser
  15.0.25 + Tor client 0.4.9.14 + graphics/Wi-Fi firmware; two-week cadence
  since 7.12, next due late Oct; ISO ~1.8 GB; NEW micro-detail: 7.15 shows
  an empty page instead of the Tor Browser homepage on new Unsafe-Browser
  tabs, #21004): Tor 0.4.9.14, kernel 6.12.111 (7.14 line). Fusion cannot
  reproduce OS-level Tor enforcement, amnesia, or firmware trust from
  inside an app.
- "Lighter than Tails" is not a claim until both sides are measured on the
  same hardware with the same workload. The target below is: smallest honest
  footprint for a bounded task set, with every number receipted.

## 1. PRD features (new or confirmed R36)

| ID | Feature | Status R36 | Evidence |
|---|---|---|---|
| P-ANON-1 | Orbot handoff, 17.9.7 (Tor 0.4.9.14; obfs4 0.9.0, Snowflake 2.15.1) | Authored, unexecuted (reconfirmed current R36; no 17.9.8/17.10 signal; mirrors top at 17.9.3) | Launcher-intent path; parse-gated; device run pending |
| P-ANON-2 | Tor VPN (Arti, F-Droid 1.9.1Beta build 242) as NEXT handoff | Documented (reconfirmed: still beta/experimental, no new information) | Experimental label inherited; no code until stable + review |
| P-CONS-1 | ConsentLedger receipts | EXECUTED (R28, unchanged) | run_r28_contracts + reference |
| P-CONS-2 | ConsentDialog UI | Authored (unchanged) | Parse-gated; strict-clean; device run pending |
| P-CONS-3 | Passkey readiness: Credential Manager transfer API / FIDO CXP-CXF watch | Documented (unchanged) | NO code (needs API 34+ device + review) |
| P-USB-1 | UsbWatchReceiver detection/alerting | Authored (R28) + R29 wiring (unchanged) | Advisory text only; still records + notifies. Parse-gated; app-manifest registered. Device run pending. Detection ONLY. |
| P-USB-1b | UsbAllowlist: private consented-device set (vid:pid grammar, 64-cap) | Store logic EXECUTED (R29 vs in-memory fake, unchanged); wiring authored | Membership means "seen + consented before", NEVER hardware trust (VID:PID spoofable). |
| P-USB-2 | Recommend Android 16+ USB Protection (new connections charging-only while locked; connections established BEFORE locking stay active; all Pixel A16+, select A17) | Documented (reconfirmed R36; USB-on-all-Pixel-A16+ term kept) | Platform feature; Fusion relies on nothing |
| P-USB-3 | Recommend GrapheneOS USB-C port control (five modes, locked default, DUAL-LAYER: hardware data-line disable + kernel second layer) | Documented (unchanged) | OS feature; the concrete BadUSB closure Fusion cannot replicate |
| P-USB-4 | USB-C Authentication note: 2016 standard, weak adoption | Documented (unchanged) | No enforcement claim; OS controls + detection remain the posture |
| P-A17-1 | Recommend Android 17 Advanced Protection SIX (enumerated Oct 1, 2026: Intrusion Logging, USB Protection, Accessibility Protection, Disable WebGPU, Failed Authentication Lock, View Supporting Apps; Expert section in flux) | Documented (reconfirmed R36; six stand) | OS features; Fusion duplicates none; needs Pixel device receipts |
| P-GOS-1 | GrapheneOS controls note (duress PIN, 18h auto-reboot, secure element, secure clipboard; Signature 27 first supported Motorola — November window, SD 8 Elite Extreme Gen 6, MTE + SE, 7+7, Linux 6.18, reset-attack protection, 50MP Lytia + 200MP periscope + B&O; preinstalled builds planned; Titan M2/M3 still the SE bar) | Documented (reconfirmed R36; still November, no exact date) | OS properties only; no in-app replication. Signature 27 is the phone to watch for receipts. |
| P-FOR-1 | Recommend Intrusion Logging for high-risk users (separate opt-in, E2E encrypted, user-only readable, rolling 12-month retention with auto-delete, undeletable-before-expiry, Amnesty/RSF input; must enable BEFORE an incident; unlocked-period + pre-boot gaps documented) | Documented (reconfirmed R36) | Fusion keeps no cloud logs. Honesty boundary stands: "leave no trace" promises must reckon with undeletable logs. |
| P-DIAG-1 | LogScrubber: PII-shape redaction (emails, phone runs, bearer/token secrets, device paths), idempotent | EXECUTED (R31, unchanged) | run_r31_contracts + reference. Wiring to the "Share redacted report" control is future; shapes are not a leak proof. |
| P-AMN-1 | RetentionSweep: keep/purge partition by per-kind max ages over a caller-supplied now | EXECUTED (R32, unchanged) | run_r32_contracts + reference. Deletion execution is caller duty; verdicts are not an erasure proof. |
| P-PIN-1 | PinRules: device-PIN strength policy reporting every violation | EXECUTED (R32, unchanged) | run_r32_contracts + reference. Policy only; storage, rate limiting, and the secure element are outside. |
| P-LOCK-1 | LockoutPolicy: brute-force backoff over caller instants | EXECUTED (R32, unchanged) | run_r32_contracts + reference. Attempt counting only; credential checking is outside. |
| P-AUTH-1 | TotpCode: RFC 6238 TOTP compute + ±window verify over caller instants (all six RFC vectors executed) | EXECUTED (R33, unchanged) | run_r33_contracts + reference. Code math only; secret storage, provisioning, and the authenticator UI are outside. Narrows the replay window; does NOT stop real-time relay phishing. |
| P-PSN-1 | Pseudonym: HMAC-SHA256 keyed pseudonyms (context domain separation, constant-time verify; JVM output equals Python hmac) | EXECUTED (R34, unchanged) | run_r34_contracts + reference. Correlation without raw IDs; key storage, rotation, and analytics wiring are outside. KEYED, not anonymous. |
| P-FMASK-1 | FieldMask: allowlist/denylist projection dropping whole fields before export (flat records, nesting refused) | EXECUTED (R35, unchanged) | run_r35_contracts + reference. Structural only; kept free-text fields can still leak (LogScrubber's job); pipeline wiring future. |
| P-ESC-1 | XorSplit: 2-of-2 XOR secret splitting for escrow-shaped storage (individually useless shares, jointly sufficient) | EXECUTED (R35, unchanged) | run_r35_contracts + reference. Mask randomness is caller duty (uniform, single-use — unverifiable here); share custody + backup transport future. |
| P-KANON-1 | KAnon: k-anonymity auditing over caller rows (equivalence-group counts, insertion-ordered violations) | EXECUTED (R36, host) | run_r36_contracts + reference. Audit only: no suppression/generalization performed; quasi-identifier selection is caller duty; k = 1 is a documented vacuous pass. Export-pipeline gating future. |
| P-RATE-1 | AttemptWindow: sliding-window attempt limiting (half-open window; boundary expired, future ignored) | EXECUTED (R36, host) | run_r36_contracts + reference. Counting only; credential checking outside. Complements P-LOCK-1 backoff (window counting, not doubling delays). Auth-path wiring future. |
| P-ENT-1 | ShannonBits: Shannon entropy in bits per symbol over raw bytes | EXECUTED (R36, host) | run_r36_contracts + reference. Distribution math only; min-entropy outside; NOT a randomness proof. Vault strength-meter wiring future. |
| P-KEY-1 | Shuffled pad + no-IME digit pad | Existing (device-tested) | Screen/pointer/AX observers still trusted |
| P-KEY-2 | BadUSB HID injection response | Partial (policy + inventory + authored detection + allowlist softening + OS dual-layer port control recommended) | Enforcement = OS controls. Passive inline loggers remain undetectable by any software — stated, not solved. |
| P-KEY-3 | Typed-secret reduction via passkeys/hardware keys | Documented (unchanged) | Reduces the software-keylogger yield; does NOT detect keyloggers. |
| P-KEY-4 | Recommend Android 17 Accessibility Protection (verified Accessibility Tools only) as the OS anti-overlay/keylogger control | Documented (reconfirmed R36 inside the standing six) | OS feature; closes the malicious-AX-service keylogger path where enabled. Fusion claims no equivalent. |

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
backoff counting (P-LOCK-1) plus window limiting (P-RATE-1) executed as host
kernels; enforcement needs the credential-check wiring and a device run, both
future. (6) Static-credential replay — narrowed by executed TOTP code math
(P-AUTH-1, 30s windows); real-time relay phishing of typed codes is NOT
stopped by TOTP and is stated, not solved. (7) Raw identifiers in correlated
diagnostics — keyed pseudonyms executed (P-PSN-1); key custody + rotation
future, and pseudonymity is NOT anonymity. (8) Over-collected export fields —
structural projection executed (P-FMASK-1) so pipelines drop whole fields
before export; kept free text can still leak (P-DIAG-1's job). (9)
Single-copy secret storage — 2-of-2 splitting executed (P-ESC-1); mask
randomness and share custody are caller duty. (10) Small-group
re-identification in exports — k-anonymity auditing executed (P-KANON-1);
quasi-identifier selection + suppression/generalization are caller duty, and
an audit is NOT anonymization. (11) Weak-secret overconfidence — entropy
measurement executed (P-ENT-1); distribution math only, never a randomness
proof. No vector is claimed closed by Fusion code alone.

## 3. Footprint discipline (anti-bloat rules)

1. No engine bundled without a size receipt (B2's 37.9 MB upstream AAR is a
   published number, not an in-repo intake — acceptance still needs all four
   stopping-rule gates; R32 caveats stand; 0.7.0 is a monthly, not the LTS
   base; LTS scope is the library only, servoshell out; kayfgit now ships
   the 0.6 LTS embed downstream, which proves the path, not our intake).
2. Every new permission needs a PRD row justifying it (R36 adds NO
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
  pseudonym-as-anonymity, mask-as-redaction, split-as-key-management,
  audit-as-anonymization, window-counting-as-authentication,
  entropy-as-randomness-proof.

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
10. Name the export callers that must project records through FieldMask
    allowlists and device-run an export proving dropped fields never leave
    the device (promotes P-FMASK-1 from executed-kernel to wired feature).
11. Name the export callers that must gate releases on KAnon audits and
    device-run a release that a singleton group blocks (promotes P-KANON-1
    from executed-kernel to wired feature).
12. Wire AttemptWindow behind the vault unlock path and device-run burst
    accept/deny at the window boundary (promotes P-RATE-1 from
    executed-kernel to wired feature).
13. Wire ShannonBits behind the vault secret-strength meter and device-run
    weak/strong secrets through the display (promotes P-ENT-1 from
    executed-kernel to wired feature).
