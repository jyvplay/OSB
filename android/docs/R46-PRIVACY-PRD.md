# R46 Privacy, Tails-Like Lightweight Amnesia & Wired/Wireless Anti-Keylogger PRD

Date: `2026-10-26`  
Primary Research Log: `docs/research/r46-primary.md`  
Air-Gap Invariant: `:privacy` AndroidManifest holds **zero** `INTERNET` permission (`android.permission.HIDE_OVERLAY_WINDOWS` only, enforced by `tools/check_r45_manifest_scope.py`).

---

## 1. Confirmed Tails (`7.15.1` / 2026 Design) Lightweight Features Implemented in Fusion

| Tails Feature (Primary Source Verified `2026-10-26`) | Threat Closed | Fusion Clean-Room Production Kernel | Host Verification |
| :--- | :--- | :--- | :--- |
| **`onion-grater` Tor ControlPort (`951`) Filter Proxy** (`tails.net/contribute/design/`, `#20821`) | Compromised client app sending `GETINFO address`, `GETCONF`, or `ADD_ONION` to deanonymize host IP or spy on foreign circuits | `privacy/.../OnionGraterFilter.kt`: per-app command/argument allowlists, `510 Command filtered` default-deny, `GETINFO net/listeners/socks` rewrite to isolated SOCKS port, UID-scoped `650 STREAM` filter | `R46Checks` + live `:8765` Security tab |
| **`mat2` / Metadata Cleaner** (`tails.net/doc/about/features/`) | Author names, edit timestamps, GPS EXIF, XMP, and printer/software fingerprints leaked in shared `.docx`/`.xlsx`/`.ods`/`.pdf`/`.png` files | `privacy/.../MetadataScrub.kt` (OOXML/ODF `docProps/*` & `meta.xml` removal + byte-length-preserving PDF `/Author`/`/CreationDate` in-place space overwrite) + `format/image/.../PngMetadataStrip.kt` (`eXIf`/`tEXt`/`zTXt`/`iTXt`/`tIME` strip + CRC-32 validation) + `TrackRevisions.anonymizeAuthors` | `R46Checks` + `check_r46_reference.py` (`zlib.crc32` & PDF `xref` integrity) |
| **Per-Network Wi-Fi MAC Anonymization** (Tails MAC spoofing / RFC 7217) | Cross-network physical tracking via factory OUI MAC or predictable sequential randomization | `core/security/.../MacRandomizer.kt`: HMAC-SHA256 `(bootEpochSecret, SSID, interfaceIndex)` emitting 6-octet unicast locally-administered (`b1=1, b0=0`) MACs | `R46Checks` (`40`-vector property sweep + Python `hmac` differential check) |
| **`udev-watchdog` Emergency Wipe & 60s Shutdown Cadence** (`#11729`, Tails `7.15.1`) | Cold-boot RAM extraction after physical device seizure; false-positive wipe during suspend-to-RAM race | `privacy/.../TailsCadence.kt` (R45) + `tools/amnesic_posture.py` live `/proc/swaps` & `RLIMIT_CORE` audit | `R45Checks` + `amnesic_posture.py` |
| **Tapjacking / Overlay Occlusion Guard** | Malicious floating window tricking user into approving sensitive dialogs | `privacy/.../OverlayTouchGuard.kt` (`FLAG_WINDOW_IS_OBSCURED = 0x1`) + `HIDE_OVERLAY_WINDOWS` manifest check | `R45Checks` + `check_r45_manifest_scope.py` |

---

## 2. Multi-Layer Hardware & Local Keylogger Defense Matrix (Wired + Wireless + Acoustic/Timing)

As documented in Kicksecure's `USBGuard` analysis and `HIDGuard` research (`docs/research/r46-primary.md`), **device-authorization rules alone cannot detect keystrokes injected after a keyboard is authorized**, and **USB rules alone miss unauthenticated Bluetooth/BLE HID injection (`BlueDucky` `CVE-2023-45866`)**. Fusion therefore enforces **four independent layers**:

1. **Layer 1 — Pre-Bind USB Interface Topology Gate (`UsbGuardPolicy.kt` + `HidReportAudit.kt`)**:
   - Rejects composite Mass Storage + HID (`08:*:*` + `03:01:*`) Rubber Ducky / Bash Bunny devices (`COMPOSITE_BADUSB_STORAGE_HID`).
   - Rejects composite USB Network (`02:*`/`E0:*`/`FF:*`) + HID implants.
   - Rejects composite USB Hub + Boot Keyboard (`09:*:*` + `03:01:01`) inline hardware keylogger interposers (`COMPOSITE_INLINE_HUB_HID_IMPLANT`, e.g., O.MG Cable / KeyGrabber topology).
   - Rejects any newly hotplugged HID keyboard while the lockscreen is active (`LOCKSCREEN_HOTPLUG_DENIED`) and requires explicit interactive confirmation for secondary keyboards when a primary keyboard is already attached.
   - `HidReportAudit.kt` (R45) parses raw USB HID Report Descriptors to catch mice/headsets smuggling undeclared `Usage Page 0x01 / Usage 0x06` keyboard collections.
2. **Layer 2 — Bluetooth Classic & BLE HOGP Pairing Gate (`BleHidPairingGate.kt`)**:
   - Closes **`CVE-2023-45866` (`BlueDucky`)** by rejecting all unauthenticated `NoInputNoOutput` / `JustWorks` Bluetooth Classic HID (`L2CAP PSM 0x0011` Control / `0x0013` Interrupt) and BLE HOGP (`UUID 0x1812`) connections (`DENY_CVE_2023_45866_BLUEDUCKY`).
   - Requires MITM-protected `NUMERIC_COMPARISON` or `PASSKEY_ENTRY`, FIPS/LE Secure Connections (`sc=true`), `>= 16`-byte encryption key size, and unlocked screen state during bonding.
3. **Layer 3 — Post-Authorization Physiological Keystroke Cadence Filter (`KeystrokeJitter.kt`)**:
   - Continuously evaluates rolling inter-keystroke intervals ($\text{IKI}_i = t_i - t_{i-1}$):
     - Flags `SYNTHETIC_SUPERHUMAN_SPEED` (`shouldBlockStream = true`) when mean $\text{IKI} < 22\text{ ms}$ (>540 WPM microcontroller burst).
     - Flags `SYNTHETIC_CLOCKWORK_REGULARITY` (`shouldBlockStream = true`) when sample $\sigma(\text{IKI}) < 1.5\text{ ms}$ and mean $< 120\text{ ms}$ (crystal-clocked DuckyScript `DEFAULT_DELAY` loop vs `25–70 ms` natural human variance).
4. **Layer 4 — Outbound Network Keystroke Timing Chaff (`KeystrokeJitter.scheduleQuantizedCoverStream`)**:
   - Defeats SSH/collaborative-editor packet-timing keystroke inference (Song–Wagner–Tian) by quantizing outbound key packets onto fixed `50 ms` ticks and filling empty ticks with indistinguishable `isChaffCover = true` dummy frames.
5. **Layer 5 — PCIe / Thunderbolt DMA Posture (`DmaGuardAudit.kt` + `amnesic_posture.py`)**:
   - Audits `/sys/kernel/iommu_groups` and requires `IommuMode.STRICT` + pre-boot IOMMU protection before reporting `HARDENED` DMA posture.
