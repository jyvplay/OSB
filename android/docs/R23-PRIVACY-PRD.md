# R23 privacy/input refinement — NOT a Tails fork or anonymous OS

**Current verification:** exact4bc7637 privacy16/0/0 and focused memory24/0/0 on managed API34 compiled/target37.
Main overall121/1 FAILED an existing task Add workflow; no release/full-module acceptance. API37 runtime,
Rabbit1 GUI, malicious external overlay/AX service, physical/host/firmware and whole-system/Tails gates remain open.
Actual APK sizes:privacy2,534,628B; main28,954,907/28,954,979B, remote observations ONLY, not app/OS RAM or Tails comparison.
[Exact results/failures](R23-RESULTS.md). New compositor witnesses and actual IME-visible single-tap case
strengthen native verification without weakening production discard/secure behavior. No general stability proof.


Fresh-source basis and hypotheses: [primary research](research/r23-primary.md). These are additional scoped requirements, not a rewrite or promotion of the original231-row PRD,36 cross-cutting obligations or30 spike gates. No new included module is counted.

## Delivered source and scoped acceptance

| ID | Contract / artifact | Test and boundary |
|---|---|---|
| PRIV23-01 | Normal scratchpad: secure window + recents exclusion + request hiding third-party application overlays. | Native flags, existing positive-control screenshot test and granted HIDE_OVERLAY_WINDOWS permission. Not privileged/system/AX/host capture prevention. |
| PRIV23-02 | Reject both full/partial obscuration for the WHOLE gesture, cancel child gesture before discard, reject its remaining clean UP. `TouchAdmission` + `GuardedPrivacyActivity`. |160,000 exhaustive4-event host words; actual Android dispatch with authored obscuration flags, fresh empty editor/undo. No physical malicious-overlay application test yet. |
| PRIV23-03 | Pause OR window focus loss discards scratchpad/link/code; new whole views rather than reusing an old editor/undo manager. Empty restart control never retains the old gesture. | Native background/recreation and actual focusable Dialog; stale detached keypad callback attack. OS/IME copies and held in-process references remain outside erasure contract. |
| PRIV23-04 | Separate **digits-only** no-IME touch workspace; no EditText/InputConnection, typed-key/shortcut route, provider, clipboard/import/export or authentication submission. | Native actual keypad tap, no text-editor views/connection/IME; numeric/letter/delete/Enter/Tab/Ctrl-V rejection. Host pointer/screens/cameras, trusted AX and OS still see relevant events. This is not a general password keyboard. |
| PRIV23-05 |64 ASCII digits maximum, atomic cap+1 refusal; clear/reshuffle; default masking,5s scheduled reveal-hide; expire at60,000ms checked on timer/actions since last accepted code change. | Host cap, invalid Unicode digits, exact deadline/clock reversal/Long boundary and retained-String erasure counterexample. Native capacity and actual61s wait. Looper scheduling is not a hard real-time guarantee; no forensic/RAM/flash erasure promise. |
| PRIV23-06 | Mark sensitive views with API34 accessibility-data sensitivity, disable content capture/autofill/hierarchy saving, retain legitimate accessibility-tool trust explicitly. | Native property assertions. Full TalkBack/Switch/Voice/200% review not completed. Keyboard navigation intentionally blocked in the optional pad; ordinary scratchpad remains the keyboard-capable route. Do not market pad as universally accessible or AX-proof. |
| PRIV23-07 | No-permission local exposure snapshot: enabled AX service count, external keyboard-capable device count, extra displays, ADB/developer-option settings; UNKNOWN stays unknown. `ExposureProbe` + `LocalExposure` + non-exported Activity. | Native platform snapshot and manifest; host all243 absent/present/unknown combinations. No package/device names/serials/text payload saved. Not continuous enforcement, root detection, comprehensive wireless-debugging check or implant scan. A0 count is NOT an all-clear. |
| PRIV23-08 | No silent device/host settings changes. Read-only Linux USB-default inspector returns bounded observations, not “secure.” | Actual sandbox sysfs read returned UNKNOWN/no exposed buses, NOT zero risk;5 Python parser/file-IO tests. Does not inspect current devices, deny USB or attest a laptop. |
| PRIV23-09 | Investigate no-named-temp-file sandbox transport BEFORE replacing existing format FD protocol. | `R23MemoryBoundaryTest` is an Android prerequisite experiment: retained writable mmap after restriction; unmap-before-Parcel transfer; bounded2MiB readback/new-write refusal. Substrate passed at20c1fd9 BEFORE production implementation. Explicit evaluateMemorySheet/Csv/Xlsx APIs and worksheet UI then passed b3f2f1d distinct-UID/wire/live-observer/FD/picker24-case gate and full122-case run. No named parser-input file fallback in those APIs; legacy evaluateOwned* APIs still intentionally use regular files. Provider sources/created exports, OS paging and memory copies are NOT disk-free/erased. Exact source/candidate limits remain in the engineering ledger. |
| PRIV23-10 | Reproducible local contracts and real CLI Android builds, exact-candidate receipt gate. | `bootstrap_host_tools.py`, `r23_lab.py`, `r23_evidence_gate.py`. Host cache is JRE-only; requires a maintained real JDK17+SDK for Gradle. Green scoped evidence never authorizes anonymity/full completion. |

Current exact-candidate native outcomes, failures and evidence links live in [R23-ENGINEERING.md](R23-ENGINEERING.md), not inferred from this source inventory.

## The hardware paths we can and cannot close

No prevalence dataset was retrieved; “all the most common vulnerabilities are now closed” would be ungrounded. These documented classes are addressed separately:

| Attack class | Mitigation in this change / action for the laptop owner | What is NOT closed |
|---|---|---|
| Keyboard-cable/keyboard-only recorder | Enter digits with first-party touch buttons instead of sending keyboard scan codes. Inspect cable/USB adapters and use physically trusted peripherals. | Logger with pointer/video access, firmware implant or a compromised host. No hardware-logger detection/removal. Normal scratchpad remains keyboard-observable. |
| Malicious ordinary IME | Optional pad has no IME text-editor connection. Normal input visibly retains its IME trust warning. | OS/AX/screen observation, IME side channels outside declared input path; other apps still use their own keyboards. |
| Ordinary overlay/tapjacking | HIDE_OVERLAY_WINDOWS request plus full/partial gesture rejection/discard. | System/privileged/legitimate-tool overlays, fake whole apps, hostile Android event flags. Needs real overlay-provider/OEM testing. |
| USB HID injection / BadUSB | Pad ignores textual hardware keys/shortcuts; read-only inspector and recovery-first allowlist guidance. | OS-level shortcuts, shell/admin actions, other apps, descriptor forgery or non-keyboard payloads. An Android app cannot implement host-wide USBGuard. |
| Debug bridge / unwanted accessibility / screen sharing | Visible observations, explicit trust boundary; owner reviews and turns off unneeded debugging/remote sessions after evaluation. | Snapshot cannot disable these authorities, detect all capture, inspect host services or comprehensively audit wireless ADB. |
| Thunderbolt/USB4 DMA | Owner checks actual host DMA/IOMMU/UEFI support and OEM policies; Windows primary guidance is in research record. | No Android app control or test of the laptop DMA boundary. A normal USB data blocker is not a DMA/firmware solution. |
| Stolen unlocked/paused workspace | Whole-view discard, bounded code lifetime, default masking, no saved drafts for companion. | Cold-boot/forensics, swap, powered RAM, physical cameras or privileged memory. Never use sole important data in the scratchpad. |
| Compromised BIOS/UEFI/host/VM | Use a trusted, updated, physically controlled device; high-risk work must not rely on a guest hiding from its host. | Not software-repairable from this app. Known-good boot alone does not establish safe firmware/hardware. |

## Future privacy-system acceptance, still NOT implemented

- **Network compartment:** explicit opt-in networking outside the no-INTERNET companion; authentic route status, all-IP/DNS/IPv6/WebRTC/direct-socket/bypass/teardown/reconnect/captive-portal tests; no hidden cloud relay. A VPN badge is not route verification or browser fingerprint resistance.
- **Browser anonymity:** a maintained, audited privacy-browser threat model and fingerprint tests. A generic engine over Tor is NOT Tor Browser and is not relabelled as it. Full Major browser and original compositor obligations remain.
- **Boot/system layer:** if “Tails-like” means whole-machine behavior, design a separately audited live-boot product with host independence, verified updates/boot chain, device ownership and documented firmware limits. An ordinary APK cannot supply that layer. Do not ship a renamed APK as a Tails mod.
- **Lightweight comparison:** compare total guest+host installed bytes, peak/steady PSS/RSS, cold startup, power and network on the SAME real hardware/workload, with Tor/browser/Office functionality held constant. A2.5MB APK vs a bootable OS is not a valid comparison. No such comparison occurred.
- **No retroactive promises:** cannot delete prior chat/GitHub/ISP/provider logs or anonymize already-published identifiers. Filenames, retained document values, host paths and user behavior can identify people.
- **No “more private than Tails” rank:** threat models differ; no adequate verifier or measured equivalence exists here.

## Additional PRD verification obligations learned from actual failures

- **PRIV23-11 — real SDK identity:** treat `platforms;android-37.0` and ApiLevel37.0 as base37,
  not a nonexistent unqualified package or every higher minor/beta. Inspect actual metadata/jar and compile
  an official37 constant + actual installed target37. This does not test API37 runtime or the IDE GUI.
- **PRIV23-12 — non-vacuous screen verification:** require actual focus/current marker/renderer commit,
  real co-captured nonfocusable witness, correct cross-window screen coordinates, unoccluded area and
  nonempty positive controls; first witness-presented secure marker pixel fails. A blank or stale frame
  is not protection. Retain13/1,14/1,15/1,14/2 counterexamples and fresh16 result. Not an all-frame/OEM theorem.
- Latest full native121/1 is a blocking acceptance failure; independent passed privacy/memory experiments
  cannot certify all Task/productivity behavior, and visible IME alone cannot diagnose its cause.
