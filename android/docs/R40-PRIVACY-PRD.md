# R40 privacy PRD — Tails-mod currency, IP hygiene, hardware vectors

## Fresh currency (searched this session, 2026-10-23)

- **Tails: 7.15.1 remains current** (reconfirmed on tails.net + Tor
  blog). No newer release; Tor Browser 15.0.26 (Oct 21) stands,
  16.0a14 alpha only. The eMudhra system-wide CA block remains the
  reference event for P-38.1/P-39.1 (CA distrust as dated data) — both
  still design-only, still stated as such.

### Requirement updates

- **P-40.1 (shipped): network-identifier log hygiene completes.**
  `IpRedact` (:privacy, 22 executed assertions) joins MacAddr: strict
  v4/v6 parsing (leading-zero octal trap refused; `::` rules enforced),
  /24 + /48 prefix-keeping redaction, RFC 1918 classification. Its
  scope string carries the honesty line verbatim: prefix-keeping
  "reduces identifiability, is NOT anonymity" — a /24 still narrows to
  one network. Cross-checked against held-out `ipaddress`.
- **P-40.2 (shipped): provisioning intake hardening.** `OtpauthUri`
  validates TOTP/HOTP provisioning before any secret is stored: base32
  gate, digit/period windows, unknown parameters REFUSED (not dropped),
  and the escaped-colon smuggling shape fixed in-round (split raw label
  before percent-decoding) with attacks pinned on both halves. It never
  computes codes — TotpCode owns that, and the separation is itself the
  security property.
- **P-40.3 (shipped): address-shaped-secret recognition.** `Bech32`
  gives the vault checksum-level validation for bech32/bech32m strings
  with wallet semantics declared out of scope — recognition for
  redaction and storage hygiene, nothing more.

Standing honesty, restated: an Android app CANNOT be Tails — no
amnesia, no OS-level Tor enforcement, no RAM wipe. The in-app analogue
set grows by IpRedact; TorPosture stays detection-not-enforcement.

## Keylogger / local hardware-origin vectors (mandated question)

What the fresh numbers add: the O.MG Cable Elite gen 3 advertises
**890 keys/sec** payloads, **1.5M-keystroke** storage and a FullSpeed
**inline hardware keylogger**. Consequences, stated plainly:

1. At attack speed (890/s ≈ 4.4× the 200/s non-human line), KeyCadence's
   thresholds flag injection trivially — machine-speed payloads remain
   the EASY case.
2. The hard case is unchanged and unchangeable at this layer: an
   attacker who slows to human cadence (QUACK's evasion result) and the
   PASSIVE inline logger, which no software on the host can observe.
   WireBadger — a commercial HARDWARE cable detector earning a TIME 2026
   mention this week — is market confirmation of this repo's standing
   claim: hardware implants get detected by hardware (or physical
   inspection), not by apps.
3. `hid-omg-detect` v2 (lkml, unmerged) now carries a suspicious
   VID/PID table including the classic O.MG id — the same offline
   layer this repo's UsbDesc (R39) + hid_topology (R37) operate at,
   reinforcing descriptor-level auditing as the right in-scope rung.

Honest matrix delta vs R39: one row strengthened —

| Vector | Countermeasure here | Status |
|---|---|---|
| Malformed/implant descriptors | UsbDesc strict refusals (R39) + hid_topology DUAL_KEYBOARD/HUB_IN_PATH (R37) | shipped; offline bytes only |
| Machine-speed injection | KeyCadence thresholds (R38) — now contextualized against published 890 keys/s attack hardware | shipped; heuristic, evadable at human cadence — stated |
| Passive inline logger | NOT software-detectable (now externally corroborated by the hardware-detector market); randomized KeypadLayout + FIDO2 guidance remain the mitigations | unchanged, stated |
| Credential replay | FIDO2 guidance + HibpRange + OtpauthUri intake hardening (new) | shipped |

No row anywhere claims software closes the passive-hardware gap.
