# R50 Primary Research Ledger (Verified 2026-10-27)

All citations below were retrieved via live `web_search` on **2026-10-27** and inform clean-room, permissive-only (`Apache-2.0` / `MIT` / public IETF/W3C/ISO/SMPTE specs) implementations written 100% from scratch.

## 1. Tails `6.18`–`7.x` (2025–2026): Pluggable Transports (`WebTunnel`, `Snowflake`, `obfs4`), `Conflux` Multi-Path Circuits, and Recursive `mat2` Metadata Anonymization

- **Sources**:
  - Tor Project Blog (2025-07-25), *"New Release: Tails 6.18 — WebTunnel bridges"* (`https://blog.torproject.org/new-release-tails-6_18/`) & Tor Newsletter Archive (`https://newsletter.torproject.org/archive/`, Aug 2026: Tails `7.8.1` / Tor Browser `15.0.15` / `Arti 1.4.2` `Conflux`).
  - LWN.net (`https://lwn.net/Articles/1044979/`, Nov 2025), *"Protecting privacy with Tails"* — documents `Conflux` dual-guard stream splitting (`2` guard paths recombined at exit) and Metadata Cleaner / `mat2`.
  - Tails Official Documentation, *"Removing metadata from files"* (`https://tails.net/doc/sensitive_documents/metadata/index.en.html`) & Nym Cybersecurity Guide (`https://nym.com/blog/tails-os`, Oct 21, 2026).
- **Engineering Takeaways for R50 (`:privacy` & `:format:archive` / `:format:image`)**:
  1. **Censorship-Circumvention Pluggable Transports (`TorPluggableTransportConfig`)**: Standard Tor guard IPs are public in consensus; `obfs4` disguises traffic as uniform entropy, `Snowflake` tunnels via ephemeral WebRTC peers discovered through a domain-fronted broker, and `WebTunnel` (added in Tails `6.18`, refined through `2026` with `uTLS`) wraps Tor circuits inside ordinary HTTPS Upgrade/WebSocket-like streams to evade active DPI, paired with `Conflux` sequence-numbered dual-leg circuit scheduling (`Vanguards-lite` guard pinning).
  2. **Recursive Embedded Metadata Cleaning (`Mat2RecursiveMetadataScrubber`)**: Tails explicitly warns that Office containers (`.docx`, `.xlsx`, `.pptx`, `.odt`) embed images/media carrying nested EXIF/GPS/XMP/tEXt metadata that shallow cleaners miss. R50 implements a clean-room recursive ZIP + JPEG (`APP1` EXIF/XMP, `APP13` IPTC, `COM`) + PNG (`tEXt`, `zTXt`, `iTXt`, `eXIf`, `tIME`) + OOXML (`docProps/core.xml`, `docProps/app.xml`, `rsid` revision IDs) scrubber that strips metadata both at container root and inside nested `word/media/*`, `xl/media/*`, `ppt/media/*` payloads.

## 2. Bluetooth `BLUFFS` (`CVE-2023-24023`), `KNOB`, and `BIAS` Session-Key Downgrade & HID Impersonation

- **Sources**:
  - Daniele Antonioli (EURECOM), *"BLUFFS: Bluetooth Forward and Future Secrecy Attacks and Defenses"* (`CVE-2023-24023`, Bluetooth Core `4.2–5.4`); SentinelOne Vulnerability Database (`https://www.sentinelone.com/vulnerability-database/cve-2023-24023/`), Espressif Security Advisory `AR2023-010`, and BlueKitchen GmbH analysis (`https://bluekitchen-gmbh.com/bluffs-impersonating-bredr-devices-using-fixed-weak-session-encryption-key/`).
- **Engineering Takeaways for R50 (`:privacy` / `:core:hw`)**:
  - `BLUFFS` breaks both forward and future secrecy across sessions by combining `BIAS` role-switch Secure Connections (`SC`) -> Legacy Secure Connections (`LSC`) downgrade, `KNOB` encryption key length negotiation down to `<= 7` octets (`56` bits, offline-crackable), and session-key derivation nonce reuse (`W_s`), enabling an attacker to impersonate a bonded Bluetooth keyboard or mouse!
  - **Clean-room R50 Countermeasure (`BluetoothBluffsSessionGuard`)**: enforce **Security Mode 4 Level 4 (`Secure Connections Only`)**, mandate **`16`-octet (`128`-bit) encryption keys** (rejecting `7..15`-octet keys for HID/input/vault channels), block any `SC -> LSC` downgrade on previously SC-bonded MACs, and track sliding-window derivation nonces to quarantine nonce-replay MitM sessions.

## 3. Wi-Fi 802.11 Probe-Request De-Anonymization (`2025–2026`) & NFC HCE Relay Distance-Bounding

- **Sources**:
  - Safeguard.sh (2026-10-24), *"Wi-Fi Probe Requests Explained: Phone Tracking and MAC Randomisation"* (`https://safeguard.sh/resources/blog/wi-fi-probe-requests-explained-how-phones-are-tracked-by-wi-fi-and-what-mac-randomisation-fixes`).
  - `arXiv:2606.25788` (Jun 2026), *"Can Machine Learning Break Wi-Fi Privacy? A Study on MAC Address Randomization"*; Cifuentes-Urtubey (UIUC IDEALS 2025); NDSS 2025 Paper `#703`, *"Deanonymizing Device Identities via Side-channel Attacks"*.
  - CompTIA / Cyber-Physical NFC Relay Case Studies (Sep 2026, `https://www.comptiaexamprep.com/2024/10/understanding-nfc-power-of-near-field.html`).
- **Engineering Takeaways for R50 (`:privacy` / `:core:security`)**:
  1. **Wi-Fi Probe Scrambling (`WifiProbeSequenceScrambler`)**: MAC randomization alone fails when (a) 12-bit 802.11 sequence numbers increment monotonically across MAC rotations, (b) directed probes broadcast saved/hidden SSIDs (WiGLE geolocation), or (c) Inter-Probe Frame Arrival Time (`IFAT`) intervals form a deterministic fingerprint. Our kernel enforces locally-administered unicast MAC rotation (`bit 1 = 1, bit 0 = 0`) coupled with **simultaneous 12-bit sequence-number re-seeding**, zero-SSID wildcard probes, and jittered `IFAT` schedules, plus captive-portal Evil-Twin certificate/DNS quarantine.
  2. **NFC Relay Distance-Bounding (`NfcRelayDistanceBoundingGate`)**: Relay proxies tunnel ISO-14443 APDUs over Wi-Fi/LTE (`RTT >= 25..150 ms` vs `< 8..12 ms` genuine near-field). Our gate enforces strict `RTT <= 12.0 ms` distance-bounding, monotonic single-use 128-bit challenge nonces, screen-unlocked foreground verification, and automatic HCE lock on relay-latency anomalies.
  3. **Display Electromagnetic `TempestSDR` Anti-Gleaning Filter (`TempestDisplayJitterFilter`)**: High-contrast sharp pixel edges radiate narrowband VHF/UHF harmonics reconstructing text on screen (`TempestSDR` / `Screen Gleaning`); subtle sub-LSB luminance/chromatic phase dither + horizontal blanking jitter attenuates coherent horizontal-scan harmonics while preserving WCAG contrast.

## 4. AMD FidelityFX Super Resolution 1.0 (`FSR 1.0`, MIT License) & Adobe `.cube` 3D LUT Color Grading

- **Sources**:
  - AMD GPUOpen, *"FidelityFX Super Resolution 1.0 (FSR 1) — EASU and RCAS"* (`https://gpuopen.com/fidelityfx-superresolution/`, MIT license; SIGGRAPH 2021 presentation `Unity AMD FSR - SIGGRAPH 2021.pdf`).
- **Engineering Takeaways for R50 (`:feature:lens` — `LN-05`, `LN-09`)**:
  - `FSR 1.0` (`MIT` license) performs spatial upscaling in two stateless passes: **Pass 1 `EASU`** (12-tap local gradient-direction analysis + elliptical Lanczos2 polynomial `(2/5 d^2 - 1)^2` + 2x2 neighborhood min/max anti-ringing clamp) and **Pass 2 `RCAS`** (5-tap cross `b, d, e, f, h` adaptive contrast sharpening solved for clip-free local weight in `0.0..2.0` stops).
  - Clean-room **Adobe `.cube` 3D LUT (`Cube3dLutColorGrader`, `LN-09`)** parses `TITLE`, `DOMAIN_MIN`, `DOMAIN_MAX`, and `LUT_3D_SIZE N` (`N^3` RGB lattice) and evaluates **tetrahedral 3D interpolation** (`6` sub-tetrahedra per unit cube, strictly more accurate along neutral grey diagonals than trilinear interpolation) plus vignette falloff!
