# R37 privacy PRD — Tails-mod companion profile (fresh research, Oct 2026)

Working branch `arena/350b91e4-osb`. Prior: R36-PRIVACY-PRD. Research:
[research/r37-primary.md](research/r37-primary.md) (two search batches + three
fetched standards). This PRD covers a COMPANION PROFILE inside Fusion plus OS
settings it recommends. It is not an OS, not Tails, and no
footprint/anonymity comparison is claimed without measurement.

R37 executes **two privacy kernels** (UtmStrip, HibpRange) and **one host tool
for input-path auditing** (`hid_topology.py`). The rest is reconfirmation +
OS-control documentation.

## 0. Honest positioning (restated with this round's sources)

- **Tails 7.15 (2026-10-13) is still current.** Reconfirmed this round from
  endoflife.date/tails (page dated 2026-10-14) and es.wikipedia (7.15, 13 Oct
  2026); no 7.16 signal, and the 7.12-onward two-week cadence puts the next
  release in late October. Tails remains: Debian 13 base, GNOME, x86-64 only,
  RAM wipe on shutdown, optional Persistent Storage that is *encrypted but
  detectable* — forensic analysis can see it exists, just not read it.
- Fusion cannot reproduce OS-level Tor enforcement, amnesia, cold-boot
  resistance or firmware trust from inside an app. What it can do: keep its own
  data ephemeral, refuse to leak identifiers it does not need, and hand the
  network layer to tools that do have those properties.
- "Lighter than Tails" stays unclaimed. The measurable target: smallest honest
  footprint for a bounded task set, every number receipted.

## 1. PRD features (new or reconfirmed R37)

| ID | Feature | Status R37 | Evidence |
|---|---|---|---|
| P-ANON-1 | Orbot handoff, 17.9.7 (Tor 0.4.9.14; obfs4 0.9.0, Snowflake 2.15.1) | Authored, unexecuted (reconfirmed current R37: no 17.9.8/17.10) | Launcher-intent path; parse-gated; device run pending |
| P-ANON-2 | Tor VPN (Arti, F-Droid 1.9.1Beta build 242) as NEXT handoff | Documented (unchanged; still beta/experimental) | No code until stable + review |
| P-CONS-1 | ConsentLedger receipts | EXECUTED (R28, unchanged) | run_r28_contracts + reference |
| P-CONS-2 | ConsentDialog UI | Authored (unchanged) | Parse-gated; strict-clean |
| P-CONS-3 | Passkey readiness: Credential Manager transfer API / FIDO CXP-CXF watch | Documented (unchanged) | NO code (needs API 34+ device + review) |
| P-USB-1 | UsbWatchReceiver detection/alerting | Authored (R28) + R29 wiring (unchanged) | Advisory text only. Detection ONLY. |
| P-USB-1b | UsbAllowlist: private consented-device set | Store logic EXECUTED (R29, unchanged) | Membership means "seen + consented", NEVER hardware trust |
| P-USB-2 | Recommend Android 16+ USB Protection (charging-only while locked for NEW connections) | Documented (reconfirmed R36) | Platform feature; Fusion relies on nothing |
| P-USB-3 | Recommend GrapheneOS USB-C port control (five modes, hardware data-line disable + kernel layer) | Documented (unchanged) | The concrete BadUSB closure Fusion cannot replicate |
| P-USB-4 | USB-C Authentication note (2016 standard, weak adoption) | Documented (unchanged) | No enforcement claim |
| P-A17-1 | Recommend Android 17 Advanced Protection six (Intrusion Logging, USB Protection, Accessibility Protection, Disable WebGPU, Failed Authentication Lock, View Supporting Apps) | Documented (reconfirmed R36) | OS features; Fusion duplicates none |
| P-GOS-1 | GrapheneOS controls note (duress PIN, auto-reboot, secure element; Signature 27 = first Motorola support, November window) | Documented (reconfirmed R37: still November, no exact date) | OS properties only |
| P-FOR-1 | Recommend Intrusion Logging for high-risk users (opt-in, E2E, user-only, 12-month, undeletable before expiry) | Documented (reconfirmed R36) | Fusion keeps no cloud logs; "no trace" promises must reckon with undeletable logs |
| P-DIAG-1 | LogScrubber: PII-shape redaction, idempotent | EXECUTED (R31, unchanged) | Shapes are not a leak proof |
| P-AMN-1 | RetentionSweep: keep/purge by per-kind max age | EXECUTED (R32, unchanged) | Verdicts are not an erasure proof |
| P-PIN-1 | PinRules: PIN strength policy | EXECUTED (R32, unchanged) | Policy only |
| P-LOCK-1 | LockoutPolicy: brute-force backoff | EXECUTED (R32, unchanged) | Counting only |
| P-AUTH-1 | TotpCode: RFC 6238 compute + ±window verify | EXECUTED (R33, unchanged) | Narrows replay; does NOT stop real-time relay phishing |
| P-PSN-1 | Pseudonym: HMAC-SHA256 keyed pseudonyms | EXECUTED (R34, unchanged) | KEYED, not anonymous |
| P-FMASK-1 | FieldMask: allowlist/denylist projection | EXECUTED (R35, unchanged) | Structural only |
| P-ESC-1 | XorSplit: 2-of-2 XOR secret splitting | EXECUTED (R35, unchanged) | Mask randomness is caller duty |
| P-KANON-1 | KAnon: k-anonymity auditing | EXECUTED (R36, unchanged) | Audit only; k = 1 is a vacuous pass |
| P-RATE-1 | AttemptWindow: sliding-window attempt limiting | EXECUTED (R36, unchanged) | Counting only |
| P-ENT-1 | ShannonBits: Shannon entropy over raw bytes | EXECUTED (R36, unchanged) | NOT a randomness proof |
| **P-UTM-1** | **UtmStrip: known tracking parameters removed from share URLs, order and fragment preserved, unknown parameters kept** | **EXECUTED (R37, host)** | run_r37_contracts + reference. Denylist of 18 names matched case-insensitively. A cleaned URL is "less tracked", never "untracked"; server-side/timing/cookie tracking is untouched. |
| **P-HIBP-1** | **HibpRange: local scan of a HIBP k-anonymity range response (5-char prefix sent, 35-char suffix matched locally)** | **EXECUTED (R37, host)** | run_r37_contracts + reference. Hashing and network fetch are caller duty; the kernel never sees a password. `0` means "not in this corpus", never "unbreached". |
| **P-HW-1** | **HID input-path audit: parse a stored `lsusb -v`/`-t` transcript; count boot keyboards, measure HID depth, flag dual-keyboard / hub-in-path / unlisted-keyboard** | **EXECUTED (R37, host tool on a labelled format fixture)** | `tools/hid_topology.py` + 5 python tests + 1 lab run. Reads a transcript, never a live bus (no lsusb/adb/root here). Cannot see a non-enumerating implant — its own output says so. |
| P-KEY-1 | Shuffled pad + no-IME digit pad (secrets entered on-screen, never through the physical keyboard) | Existing (device-tested) | New R37 sourcing: on-screen input is the documented defeat for inline hardware loggers (Wikipedia Keystroke_logging; AirDrive FAQ). Screen/pointer/AX observers still trusted. |
| P-KEY-2 | BadUSB HID injection response | Partial (policy + inventory + authored detection + allowlist softening + OS dual-layer port control recommended) | New R37 sourcing sharpens the boundary: dual-HID enumeration is the best cheap heuristic and survives identifier spoofing (Bastille O.M.G. research), but injection speed is configurable, so *timing* heuristics are evadable by design. We use enumeration, not timing. |
| P-KEY-3 | Typed-secret reduction via passkeys/hardware keys | Documented (unchanged) | Reduces the yield of a captured password; does NOT detect keyloggers |
| P-KEY-4 | Recommend Android 17 Accessibility Protection (verified AX tools only) | Documented (reconfirmed R36) | Closes the malicious-AX-service keylogger path where enabled |

## 2. Hardware-vector closure statement (the most common versions)

Most common local hardware-origin vectors, each with its honest status:

1. **Inline USB/PS-2 hardware keylogger between keyboard and host.** Invisible
   to every software layer; no driver, no process, no kernel module. What Fusion
   does: secrets that matter are entered on the on-screen pad (P-KEY-1), and the
   USB tree can be reviewed with P-HW-1 for a second keyboard or an unexpected
   hub. What Fusion does not do: detect the implant. Physical inspection remains
   the only detection, and P-HW-1 says so in its output.
2. **Malicious HID device injecting keystrokes (BadUSB/Rubber-Ducky class).**
   Addressed at the OS layer (Android 16+ USB Protection, GrapheneOS port
   control) and by the consented-device allowlist; the app cannot block a
   kernel-level HID event it never sees before the OS does.
3. **Firmware/BIOS-level logger.** Out of reach for an app; only measured boot
   and vendor firmware trust help. Not claimed.
4. **Wireless capture of a wireless keyboard.** Out of reach; recommend wired
   or Bluetooth-protected input where the threat model includes it.
5. **Accessibility-service logger.** Addressed by Android 17 Accessibility
   Protection where available; Fusion's own exposure probe latches on AX
   changes but cannot police another app.
6. **Screen/overlay capture.** Out of reach in-app; `FLAG_SECURE` and OS
   overlay protections are the real controls.

Quantifier order matters and is stated deliberately: **for every** software-only
defense available to an app, **there exists** a hardware logger that defeats it;
the app therefore claims *reduction of the most common input-path weaknesses*,
never detection.

## 3. What "lighter than Tails" would require (unmet, listed)

Same-hardware measurement of: boot RAM, resident set, disk writes per session,
updatable attack surface, Tor enforcement provenance, and cold-boot resistance.
Fusion has receipts for none of these, so no comparison is made. The honest
target remains a smaller *task set* with a smaller *stored footprint*, not a
smaller operating system.

## 4. Privacy work explicitly refused this round

- No "anonymous mode" claim from UtmStrip (parameter stripping only).
- No breach checking performed by the app (HibpRange parses a response; the
  network call, the hash and the consent are caller duties).
- No keylogger detection claim from `hid_topology.py`.
- No Tails-equivalence, RAM-wipe, or firmware-trust claim of any kind.
