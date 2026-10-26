# R38 privacy PRD — Tails-mod currency and hardware attack vectors

## Fresh currency (searched 2026-10-23)

- **Tails 7.15.1 shipped today (2026-10-23)** as an emergency release: Tor
  Browser 15.0.26 fixes CVE-2026-113470 and blocks every certificate issued
  by the eMudhra CA; crucially the block is applied system-wide through
  Debian's `ca-certificates`, not just inside Tor Browser. 7.15
  (2026-10-13) had brought Tor Browser 15.0.25 / tor 0.4.9.14 / firmware
  refresh; 7.14 (2026-09-30) kernel 6.12.111.
- PRD consequence (new requirement, numbered to follow R37's set):
  **P-38.1 CA-revocation parity** — the in-app network layer must be able
  to distrust a CA list supplied as data (like ca-certificates does),
  without a code release. Today the app has no CA pinning store at all;
  honest status: unimplemented, design only. The eMudhra incident is the
  concrete threat model: a trusted-but-compromised CA, revoked faster by
  OS vendors than by app updates.
- Standing honesty: an Android app CANNOT be Tails. No amnesia guarantee,
  no Tor enforcement at the OS level, no RAM wipe. The in-app analogue
  remains: AmnesiaPolicy, TorPosture (detection, not enforcement),
  FirewallPolicy, UtmStrip, LogScrubber, RetentionSweep, and now
  **LuhnCheck** (PCI first6+last4 PAN redaction with a no-overreach rule:
  only Luhn-valid 13-19 digit runs are masked, so timestamps and order ids
  survive).

## Keylogger / local hardware-origin vectors (mandated question)

What changed in the evidence this round:
- A Linux kernel HID monitoring driver was proposed (Apr 2026) — upstream
  momentum for OS-level injection detection, still not something an app
  can rely on.
- Published timing numbers are now pinned in code: humans 6-18 keys/s with
  inter-key SD 25-70 ms; microcontroller injection SD < 1.5 ms; sustained
  sub-25 ms strokes biomechanically implausible; >200 keys/s definitively
  non-human. QUACK (arXiv 2604.15845) proves the simple heuristics are
  evadable via randomized delays.

Shipped this round: **KeyCadence** (:core:security, 17 executed
assertions). In-app inter-keystroke classifier for the PIN keypad path:
MACHINE_LIKE / HUMAN_PLAUSIBLE / INDETERMINATE. Three honesty properties,
enforced by tests: (1) there is no "safe" verdict; (2) human-speed input
with machine-low jitter is INDETERMINATE, not human; (3) the SCOPE constant
states it cannot observe passive inline hardware keyloggers. It receives
durations only — never key contents.

Closing the most common hardware weak points — the honest matrix:

| Vector | Countermeasure here | Status |
|---|---|---|
| BadUSB keystroke injection (most common) | KeyCadence timing verdicts on sensitive fields + UsbWatchReceiver/UsbAllowlist attach policy | shipped (heuristic, evadable — stated) |
| Dual-HID enumeration (second "keyboard" appears) | hid_topology.py DUAL_KEYBOARD flag over stored lsusb transcripts | shipped R37 (offline audit only, no live bus) |
| Inline passive logger in the cable/plug | NOT detectable by any software here; mitigations: on-screen randomized KeypadLayout (shipped earlier) so touch coordinates not keystrokes carry the secret, physical inspection, FIDO2 so captured text is useless | partially mitigated, detection honestly impossible in-app |
| Captured-password replay | FIDO2/WebAuthn guidance in vault docs; HibpRange k-anonymity checks | guidance + range check shipped |
| USB topology interposition (extra hub hop) | HUB_IN_PATH flag in hid_topology.py | shipped R37, transcript-only |

Still true, still stated: no Tails/keylogger-detection claims are made that
the code cannot demonstrate; OS-level and in-app claims are kept separate.
