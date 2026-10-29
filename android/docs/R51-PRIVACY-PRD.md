# R51 Tails-Light (`7.15.1` / `Arti 2.7.0`) & 20-Vector Local Hardware Attack Countermeasure PRD

**Date:** `2026-10-27` · **Branch:** `arena/64550eba-osb`

## 1. Tails-Light Lightweight Amnesic Subsystem Upgrades (Confirmed via 2026-10-27 Research)

Building on `R47` (`Tor Prop 351` SOCKS isolation), `R48` (`RFC 9458` OHTTP + `RFC 9292` BHTTP + v3 `.onion`), `R49` (`RFC 9849` ECH + `Tails 7.15.1` CA pin blocklist + `RFC 8828` Mode 4 WebRTC), and `R50` (`WebTunnel`/`Snowflake`/`Conflux` + `mat2` recursive OOXML/JPEG scrub):

1. **`TorVanguardsLiteMesh.kt` (`:privacy`, `Tor Proposal 333` / `Arti 2.7.0`):**
   - Maintains `NUM_LAYER2_GUARDS = 4` ephemeral Layer-2 vanguard relays with `max(U_1, U_2)` rotation lifetimes over `[1, 12]` days (`mean ~ 8.33 days`), **zero disk serialization** (`persistToDisk = false` invariant), and automatic quarantine on circuit-construction burst anomalies (guard-discovery sybil defense).
2. **`Luks2Argon2idHeaderAuditor.kt` (`:privacy`, `Tails 5.14..7.15.1` Persistent Storage Policy):**
   - Audits opt-in encrypted container JSON headers: rejects `LUKS1` and `pbkdf2`, requires `type = "luks2"`, `kdf.type = "argon2id"`, `memory >= 1048576` KiB (`1 GiB`) on desktop or explicit mobile-RAM-bounded `>= 262144` KiB (`256 MiB`) with `time >= 4`, `aes-xts-plain64` `key_size = 64` (`512-bit` XTS), `4096`-byte sector alignment, and `>= 5`-word Diceware passphrase entropy (`>= 64.6 bits`).
3. **`AgeV1StreamCipherEnvelope.kt` (`:feature:vault`, `age-encryption.org/v1` + `RFC 8439`):**
   - Clean-room pure-JVM `RFC 8439` `ChaCha20` + `Poly1305` AEAD + `HKDF-SHA256` (`"age-encryption.org/v1/X25519"` / `"payload"`) 64 KiB STREAM chunked encryption (`last-chunk` nonce flag `0x01` preventing truncation/reorder), enforcing **exact `16-byte` unwrapped file-key length** prior to header `HMAC-SHA256` verification.
4. **`ChipsPartitionedCookieJar.kt` (`:feature:browser`, `RFC 6265bis` + W3C CHIPS):**
   - Double-keys `Partitioned` cookies by `(top_level_site, host_key)` so an embedded third-party tracker iframe on `news.example` cannot read the cookie it set on `shop.example`; enforces `__Host-` (`Secure`, `Path=/`, no `Domain`) and ephemeral RAM wipe on session close.

## 2. 20-Vector Anti-Keylogger & Local Hardware Attack Matrix (`V1..V20`)

`R51` preserves `V1..V15` (`ImuSideChannelGuard`, `AcousticKeystrokeShield`, `SecureKeypadScrambler`, `OverlayTouchGuard`, `ImeTrustGate`, `ClipboardSensitiveGuard`, `ChoiceJackingUsbGuard`, `PixnappingSurfaceShield`, `PowerDvfsTimingMitigator`, `UsbDataSignalingController`, `BluetoothBluffsSessionGuard`, `WifiProbeSequenceScrambler`, `NfcRelayDistanceBoundingGate`, `TempestDisplayJitterFilter`, `UsbBadHidKeystrokeRateGuard`) and closes **5 additional physical/hardware attack vectors (`V16..V20`)**:

| Vector | Physical Attack Mechanism | Clean-Room `R51` Kernel | Enforced Quantitative Invariant |
|---|---|---|---|
| **`V16` Capacitive `GhostTouch` / `WIGHT` EMI** | `100–500 kHz` radiated dipole EMI (`<= 40 mm`) or USB charger GND common-mode noise injects phantom taps/swipes (`sigma ~ 14.6x19.2 px`). | `CapacitiveGhostTouchFilter.kt` (`:core:hw`) | Rejects `touchMajor <= 0.5f` pin-point EMI spikes, blocks rapid narrow-locus tap bursts correlated with AC/USB power-rail ripple, and requires out-of-band confirmation for high-risk actions while charging on untrusted bricks. |
| **`V17` `Thunderclap` USB4 / PCIe DMA** | Malicious USB-C / Thunderbolt / USB4 dock or charger exposes a PCIe endpoint to DMA-read credential memory when IOMMU is off or shares 4 KiB pages. | `Usb4PcieIommuDmaGuard.kt` (`:core:hw`) | Enforces `authorized = 0` for external PCIe/USB4 tunnels while locked, requires `iommu.strict = 1` + dedicated bounce pages, and quarantines hot-plugged PCIe bridges advertising network/NVMe class codes during secret sessions. |
| **`V18` Magnetic Hall-Sensor & Cover Spoof** | External magnet spoofs `LID_OPEN` wake to trigger lockscreen notifications/assistant or samples uncalibrated magnetometer (`uT`) near keyboard switches. | `HallMagneticCoverTamperGuard.kt` (`:core:security`) | Quarantines magnetic field excursions `|B| > 250.0 uT` or slew `> 80.0 uT / 10 ms`, suppresses lid-wake lockscreen previews during anomaly, and zeroes uncalibrated magnetometer delivery during secret entry. |
| **`V19` Optical / ALS & Front-Camera Reflection** | High-rate `TYPE_LIGHT` lux modulation or front-camera eyeglass/cornea reflection leaks on-screen PIN pad / document contrast transitions. | `OpticalReflectionSideChannelGuard.kt` (`:core:security`) | Quantizes ambient-light readings to coarse `0 Hz` bucketed bands during sensitive focus and forces front-camera preview stream pause whenever `Vault` / `Privacy` / `PIN` surfaces hold focus. |
| **`V20` DRAM Rowhammer & Cold-Boot Remanence** | High-frequency aggressor-row activation flips adjacent PTE/key bits; post-lock heap remanence exposes plaintext secrets. | `DramRowhammerZeroizeGuard.kt` (`:core:security`) | Stores sensitive byte slices interleaved with `0xA55A` + bitwise-complement integrity canaries; detects any `>= 1`-bit flip before use, triggers immediate zero-fill wipe (`0x00` + `0xFF` + `0x00`), and refuses corrupted secret material. |
