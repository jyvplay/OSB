# R39 privacy PRD — Tails-mod currency, CA-trust fallout, hardware vectors

## Fresh currency (searched 2026-10-23, same-day sources)

The eMudhra story MOVED since the R38 PRD half a day earlier, and the
movement matters for requirements:

- Tails 7.15.1 (today): Tor Browser 15.0.26, CVE-2026-113470 fixed,
  eMudhra certificates blocked **system-wide via Debian ca-certificates**,
  not just in the browser. (Unchanged from R38's pin, reconfirmed.)
- NEW: the root cause is quantified — eight intermediate keys with 3–8
  bits of entropy (one effectively 4 bits), crackable in under a second;
  Mozilla/Chrome blocked Oct 17 within hours of the signed proof;
  Sectigo cross-signs revoked by Oct 19; Chrome removes the emSign ECC
  Root G3 on **Oct 27**; and eMudhra announced Oct 22 it is **exiting
  the global TLS certificate business**.

### Requirement updates (numbered continuation)

- **P-39.1 (hardens P-38.1):** CA distrust must be *data with a date*,
  not code. The Oct 17→Oct 27 window shows even vendors stagger
  distrust; an in-app CA policy store needs an effective-date field so a
  distrust entry can be shipped ahead of its enforcement date. Status:
  design only, stated as such.
- **P-39.2:** vendor-exit handling — a CA leaving the ecosystem (eMudhra
  → Sectigo replacements) means *replacement* certificates arrive in
  bulk; pinning strategies keyed to CA identity must treat issuer
  migration as a first-class, user-visible event, never a silent
  re-pin. Status: design only.
- **P-39.3 (shipped):** log hygiene for network identifiers — `MacAddr`
  (:privacy, 15 executed assertions) classifies U/L and I/G bits across
  three input shapes, suppresses OUI claims for locally-administered
  addresses, and redacts to the administration half
  (`da:a1:19:xx:xx:xx`). Its scope string pins the honesty: local-unicast
  is a *hint* that an address may be Android-randomized — never proof
  (docks and VMs set the same bit).
- **P-39.4 (shipped):** vault-side PAN/IBAN hygiene — `IbanCheck` joins
  LuhnCheck so exports can validate-then-mask account identifiers with a
  no-overreach rule (unknown countries refuse rather than guess).

Standing honesty, restated: an Android app CANNOT be Tails. No amnesia,
no OS-level Tor enforcement, no RAM wipe. The in-app analogue set
(AmnesiaPolicy, TorPosture detection, FirewallPolicy, UtmStrip,
LogScrubber, RetentionSweep, LuhnCheck, now MacAddr) is enumerated with
boundaries each time, and TorPosture remains detection-not-enforcement.

## Keylogger / local hardware-origin vectors (mandated question)

2026 state of the art moved too (sources in research/r39-primary.md):
an IEEE-published Rubber-Ducky defense reports >90% detection with <5%
false positives at <15 ms latency; HID-APP demonstrates a fully-local
two-phase pipeline (timing screen → on-device intent classification) —
important because it matches this repo's privacy constraint (no cloud);
NVISO published a forensic teardown of a real Digispark implant (Oct 16);
HIDGuard's threat-model doc now openly lists its own evasion windows —
the honesty bar this repo set is becoming industry practice.

Shipped this round: **UsbDesc** (:core:hw, 17 executed assertions) — the
descriptor-level rung of the input-path audit ladder. What it closes: a
stored descriptor claiming USB 3.x with a full-speed EP0, zero
configurations, or a malformed length now refuses instead of parsing
"best-effort" — exactly the sloppy-firmware shapes cheap implants show.
What it cannot do, said in its SCOPE string: identify a keyboard (class
is deferred to interfaces) or see passive inline hardware at all.

Updated honest matrix:

| Vector | Countermeasure in this repo | Status |
|---|---|---|
| BadUSB keystroke injection | KeyCadence timing verdicts (R38) + UsbWatchReceiver attach policy | shipped; heuristic, evadable — stated |
| Dual-HID enumeration | hid_topology.py DUAL_KEYBOARD over stored transcripts | shipped R37; offline only |
| Malformed/implant descriptors | **UsbDesc strict refusals (new)** | shipped; offline bytes only |
| Inline passive logger | NOT software-detectable (reconfirmed); randomized KeypadLayout + FIDO2 guidance remain the mitigations | partially mitigated, stated |
| Captured-credential replay | FIDO2/WebAuthn guidance + HibpRange | guidance + range check shipped |
| Hub interposition | HUB_IN_PATH flag (R37) | shipped; transcript-only |

No claim anywhere that software alone closes the passive-hardware gap;
the matrix says so in the table, not a footnote.
