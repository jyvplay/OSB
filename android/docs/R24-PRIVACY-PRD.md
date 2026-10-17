# R24 privacy / lightweight-system PRD supplement

As of2026-10-17. Supplement to original v2/v2.1 and R23, NOT a substitute OS specification or a declaration of whole-module acceptance. See fresh primary-source ledger and exact R24 candidate receipts. Requirements below are separate from verified outcomes.

## Trust, impossible claims and units

Objects: volatile scratchpad/digit buffer, fixed copied untrusted source bytes, bounded raw preview pixels, appearance preferences and a host's exposed policy observations. Ordinary app/isolated UID capability, genuine power/input/window APIs and trusted host/kernel are the allowed access model. The APK cannot attest firmware/physical implants or control a laptop/hypervisor it runs inside. A passive keylogger preserving the same observable events yields indistinguishable app transcripts; no observation-only detector can always identify it. This is a conditional causal argument, not a theorem-prover run.

Success is scoped: for every declared named input/lifecycle/wire trace at an EXACT candidate, all required cases execute and pass with whole relevant workflow success. Partial observations/absent sysfs/failed screenshots must NOT yield a safety verdict. Main full regression, physical/AX/API37/OS/legal acceptance remain separate mandatory claims. UI timers are main-loop scheduling, not hard real time. MiB=1,048,576bytes; raw preview≤320×320×4=409,600bytes; source≤2MiB. None implies peak RSS, no paging or secure erasure.

## Added requirements / actual construction

| ID | Requirement / artifact | Acceptance and exact exclusions |
|---|---|---|
|PRIV24-01|Trusted dynamically registered SCREEN_OFF/SCREEN_ON lifecycle in GuardedPrivacyActivity; clear/reset touch admission and refuse interaction while noninteractive; unregister on destroy|Actual sleep/wake and ordinary-UID spoof refusal in R24ScreenOffDeviceTest. ACTION_SCREEN_OFF is overall noninteractive state, not every physical display/power change. Host laptop lid/sleep is NOT guaranteed to send guest event.|
|PRIV24-02|No stale key/reveal publication after off/wake/background/focus/recreation|Real stale-button and reveal/idle cases retained. Filling one buffer/drop/mask≠erasing every heap/String/IME/OS/host copy.|
|PRIV24-03|No native PDF parser in trusted main UI route; bounded seekable readonly FD to isolated child, no URI/network/output path authority; validate raw ARGB|Actual renderer golden pixels, real picker/byte preservation, writable/memory/wrong-page/malformed/oversize refusal and existing FD/socket/private-path regressions. Child/native platform remains trusted for correctness; hash/UID not honest computation. PDF source may briefly use unlinked private file; no memory-only/no-swap claim.|
|PRIV24-04|Never decode child-controlled PNG/JPEG in parent; one serialized render per document and immutable page binding|Bounded primitive pixels only, width×height checked without overflow, opaque preview. Rapid Prev/Next/Prev requires current page's actual colored pixels, not a changed label or late response. Not full Office/PDF fidelity, signing or editing.|
|PRIV24-05|Appearance changes only own chrome/settings, not permission/experiment consent/Android or host theme; preserve drafts|One observable store, actual4 layout/palette combinations, real mouse/focus magnification/hitbox/reduce motion, corruption/unknown patch refusal. Theme≠privacy or all-AX/high-contrast certification. Document/image colors stay document content.|
|PRIV24-06|Read-only host USB/Thunderbolt/IOMMU policy counts, never a keylogger detector or automatic hardener|host_privacy_posture.py actually returned USBUNKNOWN/IOMMUgroupcount0 in this sandbox. No privileged writes or hardware closure. Missing data/empty directories aren't all-clear.|
|PRIV24-07|Actual artifact intake before third-party full engine load|engine_intake.py inspects bounded real ELF/AAR/header/notice inventory only, never dlopen; flags integration/runtime/legal acceptance FALSE. Strict16K-header condition is not physical/compat-mode execution, APK ZIP alignment or expert transitive license approval.|
|PRIV24-08|Quantify lightweight claims only with correct denominator/workload|Actual compressed APK bytes/hashes may be reported. Emulator/test harness/OS/host/SDK overhead separate. Profile/latency/frames do not equal RAM or Tails ISO/installed cost. No lighter-than-Tails claim without equivalent system/workload/security comparison.|
|PRIV24-09|No generic proxy/skin rebranding as Tor/Tails/Windows/macOS|Tor Browser includes modified browser/fingerprint/origin protections; arbitrary engine+Tor proxy is different. Business/Art are own-app layout conventions, not OS emulation/entitlement or host network anonymization.|

## Common local/hardware-origin weaknesses: what is actually addressed

1. **Keyboard-only recording:** existing no-IME pointer digit route suppresses hardware text and has no input connection; actual framework keys/touches tested. It does not cover ordinary scratchpad/Office text, screen/pointer/AX/camera or hostile OS. No hardware dongle was tested.
2. **Forgotten visible/volatile code on guest sleep:** trusted noninteractive-state event clears and prevents stale input; actual sleep/wake tested. This is not cold-boot/RAM/host-swap erasure.
3. **Ordinary Android capture/overlay/partial-obscuration:** secure/overlay and whole-gesture controls retained with positive controls. Actual compositor witness is not a malicious third-party overlay/AX service/privileged-host test.
4. **Untrusted PDF exploiting parent's authority:** native parser moved behind real isolated UID/readonly input; raw output prevents parent image-decoder re-entry. OS/kernel escape/side channels/child forgery and other uncontained format routes remain.
5. **Unexpected USB/DMA attachment:** only observations and a recovery-safe host-admin workflow are supplied. New-device default0 doesn't revoke old devices; internal default2 and IDs/classes are not authenticated identity. IOMMU/UEFI enforcement belongs to host/firmware. Windows Kernel DMA does not require VBS; not every HID logger is DMA-capable.
6. **Host/hypervisor/firmware compromise and prior external logs:** NOT closed. Prefer physically controlled updated trusted hardware/boot/update chain and independently evaluated system-wide routing/amnesia; an APK cannot rewrite past chat/GitHub/ISP observations or hide a guest from its host.

## Host-admin safety protocol (not automatically executed)

Establish trusted hardware/custody and tested recovery input/console before changing peripheral policy. Review OS-specific USB authorization/USBGuard/Thunderbolt/IOMMU/Kernel DMA controls with the host administrator, including already-admitted devices and boot time. Do not blindly disable all USB: laptop internal keyboard/touchpad/storage may be USB. Test deliberate allowed/denied peripherals on sacrificial hardware. Consider hardware authenticators for suitable origin-bound authentication, but validate enrollment/PIN/user-presence/session/host assumptions; this app has not integrated or attested one.

No prevalence dataset establishes that 'the most common' hardware weaknesses are all closed. The honest output is scoped controls, explicit impossible trust boundary and a next real physical test—not a green anonymity badge.
