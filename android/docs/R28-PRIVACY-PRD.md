# R28 privacy PRD — Tails-mod companion profile (fresh research, Oct 2026)

Working branch `arena/350b91e4-osb`. Prior: R26-PRIVACY-PRD. Primary sources:
`research/r28-primary.md` sections 4-5. This PRD covers a COMPANION PROFILE
inside Fusion plus OS settings it recommends. It is not an OS, not Tails, and
no footprint/anonymity comparison is claimed without measurement.

## 0. Honest positioning (unchanged, restated)

- Tails 7.15 (current, 13 Oct 2026): Tor Browser 15.0.25, Tor 0.4.9.14,
  kernel 6.12.111, 2-week cadence. Fusion cannot reproduce OS-level Tor
  enforcement, amnesia, or firmware trust from inside an app.
- "Lighter than Tails" is not a claim until both sides are measured on the
  same hardware with the same workload. The target below is: smallest honest
  footprint for a bounded task set, with every number receipted.

## 1. PRD features (new or confirmed R28)

| ID | Feature | Status R28 | Evidence |
|---|---|---|---|
| P-ANON-1 | Orbot handoff (C Tor, maintenance) | Authored R26, unexecuted | Parse-gated; device run pending |
| P-ANON-2 | Tor VPN (Arti 2.7) as NEXT handoff | Documented R28 | Beta+experimental labels inherited; F-Droid/reproducible-builds noted; no code until stable + review |
| P-CONS-1 | ConsentLedger: scoped single-use receipts (30-3600s TTL, replay/scope/expiry refusal) | EXECUTED (host, 112-suite) | run_r28_contracts + reference |
| P-CONS-2 | ConsentDialog: scope + TTL confirmation UI | Authored R28 | Parse-gated 7/7; touch-target strict-clean; device run pending |
| P-USB-1 | UsbWatchReceiver (app module): attach/detach detection, UsbPolicy verdict text, bounded record, notification-if-permitted | Authored R28 | Parse-gated; app-manifest registered; :privacy untouched (moved out of :core:security after a [NotificationPermission] lint failure — see RESULTS). Device run pending. Detection ONLY. |
| P-USB-2 | Recommend Android 16+ USB Protection (charging-only while locked) | Documented R28 | Platform feature, Pixel 6+; Fusion cannot enforce it |
| P-USB-3 | Recommend GrapheneOS USB-C port control (5 modes, hardware data-line disable) | Documented R28 | OS feature; Fusion cannot replicate it |
| P-FOR-1 | Recommend Intrusion Logging (opt-in E2EE forensic logs, 12-month retention) for high-risk users | Documented R28 | Platform feature with Amnesty/RSF design input; Fusion keeps no cloud logs |
| P-KEY-1 | Shuffled pad + no-IME digit pad (software-keylogger resistance on companion routes) | Existing (R23/R26) | Device-tested; screen/pointer/AX observers still trusted |
| P-KEY-2 | Close the most common HARDWARE shape: BadUSB HID keystroke injection | Partial R28 | Policy (UsbPolicy R26) + inventory diff (UsbInventory R27) + runtime detection/alert (UsbWatchReceiver R28, authored) + ConsentLedger so injected keystrokes alone cannot mint consent. Enforcement = OS controls above. Passive inline loggers remain undetectable by any software — stated, not solved. |

## 2. Footprint discipline (anti-bloat rules)

1. No engine bundled without a size receipt (B1/B2 rules in ALTERNATIVES).
2. Every new permission needs a PRD row justifying it (POST_NOTIFICATIONS: USB
   alerts only, runtime-checked, degrades to private record).
3. Amnesia posture: consent receipts and USB records are bounded and prunable;
   the companion stays no-INTERNET; nothing phones home.

## 3. Refusals (explicit non-goals)

- In-app Tor/VPN routing code, root/nftables application, keylogger
  "detection", forensic-erasure promises, Tails-equivalence or footprint
  superiority claims, own cloud forensic logging (duplicates Intrusion
  Logging badly), server-backed sync.

## 4. Next highest-information tests

1. Disposable-emulator run: USB attach broadcast -> notification/record appears
   (P-USB-1 behavior proof).
2. Device run: ConsentDialog confirm burns exactly one receipt; cancel/back
   burns none (P-CONS-2).
3. Tor VPN stable-release review: re-evaluate P-ANON-2 handoff when the Tor
   Project lifts the experimental marking.
