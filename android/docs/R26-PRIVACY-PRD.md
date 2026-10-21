# R26 privacy PRD — Tails-mod policy layer, not an anonymous OS

R25's no-INTERNET compartment, volatile fields, secure/overlay protections, obscuration
rejection, input-change latch/explicit empty rearm, and bounded PNG-copy workflow remain
unchanged. This turn adds no imports/exports to the private compartment and removes no
protection. Fresh sources: `research/r26-primary.md` (Tails, Orbot, USBGuard).

## What "Tails-mod" honestly means here

Tails is a live OS: RAM-only execution, firewall-enforced Tor, per-boot MAC
randomization, shutdown RAM wipe, optional LUKS per-category persistence. An Android app
cannot replicate the OS, network-enforcement, or erasure properties. The portable subset
is the **policy layer**: category-granular persistence, explicit wipe receipts, honest
Tor posture, read-only USB policy help, and keyboard-channel avoidance. "Lightweight"
refers to this policy code (pure Kotlin, zero new APK dependencies for the kernels);
it is not a whole-system footprint claim — the Android + emulator + host stack still
dominates, and any future engine payload (GeckoView/Servo/Media3/MapsForge) must be
measured separately on device.

## New concrete work

| Requirement | Implementation / verifier | Boundary |
|---|---|---|
| P26-1: category-granular amnesia with receipts | `AmnesiaPolicy` (core/security), executed in R26Checks | Policy transitions only. No RAM/swap/backup/host/forensic-erasure proof; the receipt text disclaims it |
| P26-2: Tor posture without false anonymity | `TorPosture` tri-state + merge (executed); `OrbotHandoff` panel (authored) | Installed icon is not a circuit. Routing needs Orbot + user VPN consent + observed bootstrap; fingerprint/timing/exit risks remain |
| P26-3: read-only USB authorization help | `UsbPolicy` USBGuard-subset parser/evaluator (executed); refuses untrusted inventory | No enforcement from an APK; descriptors are spoofable match keys, not identity; passive inline loggers invisible |
| P26-4: keyboard-channel avoidance | `KeypadLayout` shuffled pad + `InputChannel` threat table (executed) | Avoids KEY_EVENTS only. Pointer/screen/AX/camera/host still exposed. Detection of a passive logger is impossible from identical traces |
| P26-5: no false media/service privacy | `PlaylistParser` refuses encrypted/variant/remote-nesting; service links keep disclosed external handoff | Local parsing only. External apps apply their own terms/identity; no anonymous streaming claim |
| P26-6: no false visual-privacy evidence | Beauty budgets are sRGB math + bounds gates, never content-privacy claims | PixelCopy/window evidence stays in its own gated pipeline with its own limits |

## Hardware-origin risk treatment (common vectors, closed-form answers)

- **Passive inline keylogger / pre-OS keyboard tap:** CANNOT be detected from the app
  (identical trace). Closed by avoidance: touch-only shuffled pad emits no key events.
  Residual: pointer/screen/camera/AX/host observation — stated in the threat table.
- **Second/rogue USB keyboard:** narrowed by the one-keyboard stateful rule + default
  block + combo rejection, evaluated as a reviewable proposal. Residual: spoofed
  descriptors, preexisting implants, no APK-side enforcement.
- **USB storage with hidden interfaces:** rejected by suspicious-combo rules in the
  proposal. Residual: same as above; enforcement needs host USBGuard authority.
- **Malicious IME / AX / overlay / activity substitution:** retained R24/R25 discard,
  latch, obscuration rejection. Residual: privileged/late/forged platform signals.
- **Firmware/host/hypervisor observation:** outside the app trust boundary. Tails itself
  excludes altered hardware. Use trusted custody; no app badge overrides this.
- **Forensic recovery (RAM/swap/flash):** no erasure guarantee. Volatile handling +
  explicit wipe receipts reduce retention; they do not prove destruction.

## Privacy feature-set improvements (this turn)

1. Persistence is now deny-by-default per category with an explicit enable path.
2. Every wipe produces an inspectable receipt that states its own limits.
3. Tor status can no longer display "safe" from partial evidence (unknown propagates).
4. USB posture moves from prose guidance to an executable rule evaluator.
5. Keyboard-channel defense moves from a single pad to pad + explicit threat table.
6. No new network, location, or identifier permission was added for any R26 kernel.

## Footprint note

R26 kernels add ~1,500 lines of dependency-free Kotlin plus fixtures/tools; the only
new APK dependencies are the four Apache-2.0 Media3 artifacts (playback module only,
size unmeasured here — needs device APK analysis). This is not a Tails comparison and
not a lightweight-OS claim.
