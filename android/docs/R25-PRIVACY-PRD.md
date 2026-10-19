# R25 privacy PRD — concrete boundaries, not an anonymous OS

R24C's no-INTERNET compartment, volatile fields, secure/overlay protections, full/partial obscuration rejection, input-change latch and explicit empty rearm remain. This turn does not remove existing protections or enable private-compartment imports/exports. Actual current pass/fail data is source-scoped in the results ledger.

## New concrete work

| Requirement | Implementation / verifier | Boundary |
|---|---|---|
| P25-1: remove common explicit PNG metadata without rewriting source | Pure bounded PNG transform in existing `:format:image`; separately compiled7150 assertions and53 independent Pillow/file cases | Only noninterlaced RGB/RGBA8 and admitted colour fields,<=2MiB/4,194,304pixels. No arbitrary-profile, visual identity, steganography, orientation/printing or malware-safety claim |
| P25-2: isolate complex image-stream validation and bind outputs to owned source | New isolated service; sealed SharedMemory input; actual Messenger sender UID; INET-denial observation; exact retained-chunk derivative check in parent | Correct worker/trusted OS assumed for full deflate acceptance. No Byzantine-worker authenticity or no-paging/erasure proof |
| P25-3: user-selected source/new-copy output only | Nonexported PNG-copy Activity, actual system pickers, one process-level off-UI provider lane, exact readback, session-only ownership/recreation loss | Main app/providers may network; a hostile provider can block its lane or alias creation. No unconditional provider cancellation, source identity or remote-retention guarantee |
| P25-4: no false UI-readiness evidence | Explicit disposable-emulator power/keyguard precondition plus real asleep-AVD native interaction control | Test-lab preparation only; not a production privacy feature or retrospective diagnosis |
| P25-5: no false video/Office claim | Real native video Surface colour/clock checks; actual pinned Writer/WASM adapter with independent ODT/PDF/tile readback | Local controlled video is not service streaming. Desktop/browser Writer subset is not Android Office, all formats or a lightweight OS |

## Hardware-origin risk treatment

- **Keyboard-only logging:** existing touch-only digits can avoid that channel. It does not protect pointer/screen/camera/AX observation; the normal scratchpad still trusts the IME.
- **New/changed input devices or IME/accessibility/display settings:** existing actual listeners discard/latch. Notifications can be late or forged by a hostile platform; descriptors are not authenticated identity.
- **Overlays/activity substitution:** retained partial/full touch rejection and focus discard narrow app-level exposure; a flag is not a universal UI-origin proof.
- **USB/DMA:** fresh USBGuard sources support default-block authorization with explicit reviewed allow rules. They also warn about initial inventory and daemon shutdown policy. No privileged host configuration or USBGuard enforcement was performed here. Missing sysfs/IOMMU data stays UNKNOWN.
- **Preexisting implants/firmware/host:** an ordinary APK cannot certify absence. A passive logger can leave the app-visible transcript unchanged. Use trusted hardware/custody/firmware and separately validated host controls; this app does not close that original all-hardware problem.

Fresh Tails/Android/USBGuard/service sources and exact hypotheses are in `research/r25-primary.md`. No prevalence dataset was retrieved, so this is a mechanism list, not an invented ranking of the most common attacks. Small APK size does not measure the Android+emulator+host footprint. The real tested Office WASM payload reserves1GiB linear memory and is explicitly not a Tails-footprint reduction.
