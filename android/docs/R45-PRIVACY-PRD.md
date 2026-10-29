# R45 Privacy Mode PRD — Tails-Inspired Lightweight Amnesia & Anti-Keylogger Architecture

**Date:** `2026-10-26`  
**Primary Sources Verified (`docs/research/r45-primary.md`):**
- **Tails `7.15.1`** (`2026-10-23`, emergency release on Debian 13 *Trixie* with Tor Browser `15.0.26` & `ca-certificates` distrusting 8 eMudhra intermediates for **CVE-2026-113470**; 60-second hard graceful-shutdown ceiling introduced in Tails `7.14`).
- **2025–2026 Hardware Implant & DMA Literature:** Linux `hid-omg-detect` USB HID keystroke-injection profiling, Thunderbolt/USB4/PCIe IOMMU strict mode + UEFI early-boot `DMAR`/`IVRS` enforcement, and Android `HIDE_OVERLAY_WINDOWS` / `MotionEvent.FLAG_WINDOW_IS_OBSCURED` anti-tapjacking gates.

---

## 1. Honesty Boundary: What Android / Host App Mode CAN and CANNOT Claim vs Bare-Metal Tails OS

| Property | Bare-Metal Tails 7.15.1 USB | Fusion Shell Privacy / Amnesic Mode (`:privacy` + `:core:security` + `:core:hw`) |
|---|---|---|
| **Kernel & Bootloader Ownership** | Owns Linux kernel, initramfs `tmpfs`, and emergency memory-wipe-on-pull | **Does NOT own host OS kernel/bootloader**; runs as an unprivileged app (`:privacy` air-gapped companion APK has **ZERO `INTERNET` permission**). Cannot wipe kernel page cache or firmware RAM on its own |
| **Disk / Flash Forensics** | Zero writes to internal SSD/NVMe unless Persistent Storage unlocked | **App-level RAM-only session state**: zero SQLite/Room/Preferences writes in Amnesic Session, `allowBackup="false"`, explicit `ByteArray.fill(0)` key zeroization, `amnesic_posture.py` audits host swap (`0 B` required) and persistent RW mounts |
| **Network Anonymity** | Mandatory Tor circuit isolation + packet-drop kill-switch (`nftables`) | `:privacy` companion APK removes `android.permission.INTERNET` at the manifest level (enforced by `check_r45_manifest_scope.py`), making network exfiltration impossible from `:privacy` |
| **CA & Transport Trust** | Emergency 72-hour CA distrust (`CVE-2026-113470`) | `CtSctPolicy` enforces multi-operator RFC 6962 SCT quorum (`>=2` distinct operators) AND immediate `REJECT_CA_DISTRUSTED` blocklist before SCT evaluation; `TailsCadence` enforces 14-day freshness + emergency advisory lockouts |
| **Bounded Shutdown Wipe** | Tails 7.14+ 60-second systemd shutdown timeout + RAM wipe | `TailsCadence.planShutdown` enforces immediate `IMMEDIATE_WIPE_AND_TERMINATE` on USB-yank/panic-chord and `DEADLINE_FORCED_WIPE_POWEROFF` at `60_000 ms` if any background worker hangs |

---

## 2. Lightweight / Low-Footprint Budget Requirements

1. **Zero Background Daemon Tax:** Idle `:privacy` footprint targets `< 24 MiB` heap, `0` background `Service`s, `0` `ContentProvider`s, and `0` network sockets (`amnesic_posture.py` self-measures RSS in KiB on every run).
2. **Pure In-Memory Session Store:** Ephemeral notes, vault unlock subkeys (`HkdfExpand`), and clipboard buffers (`ClipMime` in `strictAmnesic = true` mode) reside only in heap arrays zeroized on screen-off, panic chord, USB attach alert, or 60-second shutdown deadline.
3. **Deterministic Zero-Epoch Exports:** When the user explicitly exports a `.docx` (`DocxPackage`) or `.xlsx` (`FormulaXlsx`) through the unexported `CopyDestinationActivity` broker, PKZIP timestamps are pinned to `1980-01-01 00:00:00` so files never leak creation timezone or wall-clock metadata.

---

## 3. Threat Matrix: Countering Common Hardware-Origin & Keylogger Attack Vectors

| Vector | Physical / Local Attack Mechanism | Why Naive Software Defenses Fail | R45 Implemented Countermeasure & Kernel |
|---|---|---|---|
| **V1. Active USB HID Keystroke Injectors** (O.MG Cable, USB Rubber Ducky, Bash Bunny, spoofed receivers) | Implant inside a charging cable or mouse dongle spoofs benign VID/PID (`0x05AC` Apple) and exposes a hidden HID Boot Keyboard (`0x01/0x06`) typing payloads at `200+ keys/s` | Vendor/Product ID allowlists are trivially cloned in implant firmware | **`HidReportAudit.kt` (`:core:hw`)** parses raw USB HID 1.11 report descriptor bytecode (`Usage Page 0x01`, `Usage 0x06`/`0x07`, `Usage Page 0x07`, vendor `0xFF00..0xFFFF`), flags `UNDECLARED_KEYBOARD_CAPABILITY` + `COMPOSITE_MOUSE_AND_KEYBOARD`, and detects `<5 ms` inter-keystroke bursts (`SUPERHUMAN_TYPING_CADENCE`) + `<150 ms` post-enumeration auto-typing (`IMMEDIATE_POST_ENUM_INJECTION`) -> `quarantineDevice = true` |
| **V2. Passive Inline USB Hardware Keyloggers** | Transparent PHY/interposer sits between wired USB keyboard and host port, recording reports silently | Emits zero extra descriptors; invisible if transparent, though cheap taps insert an extra USB 2.0 hub tier | **`hid_topology.py` + Randomized On-Screen Scrambler Entry**: vault/passphrase entry defaults to non-USB randomized touch keypad protected by `OverlayTouchGuard` whenever an external USB keyboard or unexpected hub depth is present |
| **V3. Thunderbolt / USB4 / PCIe Direct Memory Access (DMA)** (PCILeech, UEFI early-boot DMA) | Malicious dock/charger reads/writes physical RAM directly over PCIe lanes before OS login or under passthrough IOMMU | Standard OS lockscreens do not stop PCIe bus-mastering memory reads if IOMMU is disabled or `passthrough=1` | **`DmaGuardAudit.kt` (`:core:security`) + `amnesic_posture.py`** require `IommuMode.STRICT` (`iommu.passthrough=0`, `iommu.strict=1`) + `preBootDmaProtection = true` (UEFI `DMAR`/`IVRS` DMA protection) + `UsbLockPolicy.DISABLED_WHILE_LOCKED`, downgrading to `EXPOSED_CRITICAL` otherwise |
| **V4. Screen-Overlay Tapjacking & Accessibility/Capture Spyware** | Malicious app draws a 1%-alpha window over PIN pad or records screen frames | User thinks they are tapping the system prompt while touches pass through or are observed | **`OverlayTouchGuard.kt` (`:privacy`) + `HIDE_OVERLAY_WINDOWS` manifest permission** inspect `MotionEvent.FLAG_WINDOW_IS_OBSCURED` (`0x1`), `FLAG_WINDOW_IS_PARTIALLY_OBSCURED` (`0x2`), cross-UID overlays, `FLAG_SECURE`, and active screen capture — refusing secret input (`allowSecretInput = false`) on any violation |
| **V5. Clipboard & Acoustic/Timing Side Channels** | Rich HTML/URI clipboard payloads trigger remote beacon fetches on paste; swap/core dumps persist plaintext keys to SSD | Clearing clipboard late or pasting `text/html` executes `<img src="https://...">` | **`ClipMime.kt` (`:feature:continuity`)** strips HTML/RTF/URI executable types in strict mode (`plain/text` only, payload size capped); **`amnesic_posture.py`** verifies `swap = 0 B`, `RLIMIT_CORE = 0`, `fs.suid_dumpable = 0` |
| **V6. Compromised / Forged TLS CA Intermediates** (`CVE-2026-113470` class) | Weak-entropy CA intermediate issues rogue leaf cert for sensitive domain | OS root store updates lag by weeks on mobile devices | **`CtSctPolicy.kt` (`:core:security`) + `TailsCadence.kt` (`:privacy`)** enforce immediate CA SPKI/cert hash revocation independent of OS store + multi-operator RFC 6962 SCT quorum + 14-day staleness warning / 45-day stale lockout |

---

## 4. Measurable Acceptance Gates for Privacy Mode

- **Gate P1 (Host Contract Execution — PASS in R45):** `TailsCadence`, `HidReportAudit`, `DmaGuardAudit`, `OverlayTouchGuard`, `CtSctPolicy`, `PslGate`, `CspHeader`, `ClipMime`, and `HkdfExpand` pass all positive + adversarial vectors in `R45Checks.kt`.
- **Gate P2 (Static Manifest Air-Gap — PASS in R45):** `check_r45_manifest_scope.py` verifies `:privacy` requests **only** `android.permission.HIDE_OVERLAY_WINDOWS` (zero `INTERNET`) and bans `SYSTEM_ALERT_WINDOW`, `BIND_ACCESSIBILITY_SERVICE`, and `QUERY_ALL_PACKAGES` repo-wide.
- **Gate P3 (Live Host Posture Audit — PASS in R45):** `python3 tools/amnesic_posture.py --json` inspects real `/proc/swaps`, `RLIMIT_CORE`, `/proc/sys/fs/suid_dumpable`, `/sys/kernel/iommu_groups`, `/sys/firmware/acpi/tables/DMAR`, `/proc/mounts`, and `/sys/bus/usb/devices`.
- **Gate P4 (Device Hardware Gate — OPEN for Class A):** Physical Android API 34–37 test verifying `Window.setHideOverlayWindows(true)`, `FLAG_SECURE` surface rejection under `MediaProjection`, and `UsbManager` attach quarantine on real O.MG/composite HID test hardware.
