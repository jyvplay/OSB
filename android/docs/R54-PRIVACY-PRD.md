# R54 Privacy, Post-Quantum Tails-Light & 35-Vector Hardware Shield PRD

## 1. Threat Model Expansion (`R53` `30` Vectors -> `R54` `35` Vectors + Post-Quantum Harvest-Now-Decrypt-Later)

 Fusion Shell's privacy architecture assumes an adversary capable of both **passive long-term encrypted traffic archival** ("harvest now, decrypt later" against classical X25519 circuits) and **proximate local physical/side-channel access** (malicious USB-C PD docks, cross-origin GPU timing probes, DVFS frequency telemetry, Wi-Fi 802.11bf CSI keystroke sensing, and magnetic lid-sensor spoofing). All defenses are implemented from scratch under `Apache-2.0` with **zero telemetry, zero default external network calls, and zero copyleft contamination**.

---

## 2. Post-Quantum Tails-Light Anonymity Upgrades (`:privacy`)

1. **`MlKem768NttPolynomialEngine.kt` (NIST FIPS 203 `ML-KEM-768`):**
   - Implements the exact $\mathbb{Z}_{3329}[X]/(X^{256}+1)$ Number-Theoretic Transform (`NTT` / `InvNTT` with 128 powers of $\zeta = 17 \bmod 3329$), constant-time bitwise Centered Binomial Distribution (`CBD_2`) without secret-dependent table lookups, and FIPS 203 `ByteEncode_d` / `ByteDecode_d` / `Compress_d` / `Decompress_d` (`ek = 1,184 B`, `dk = 2,400 B`, `ct = 1,088 B`).
2. **`HybridX25519MlKemCircuitHandshake.kt` (Post-Quantum Hybrid Tor Circuit KDF):**
   - Combines classical ephemeral `X25519` shared secret + `ML-KEM-768` encapsulated secret + relay identity binding into a domain-separated `SHA3-256` / `HMAC-SHA256` circuit key schedule so compromising either primitive alone yields zero circuit plaintext.
3. **`TorProp340PackedRelayCellCodec.kt` (Tor Proposal 340 Packed & Fragmented Relay Cells):**
   - Packs multiple logical stream commands (`DATA`, `SENDME`, `END`) into fixed `514`-byte Tor relay frames and safely reassembles bounded multi-cell fragments, eliminating small-packet padding waste while preserving fixed-size wire cells against traffic-size correlation.
4. **`TailsRamWipeColdBootPagePlanner.kt` (Emergency Lock / Shutdown RAM Zeroization):**
   - Plans deterministic multi-pass volatile secret scrubbing across Vault keys, Recall session tokens, clipboard buffers, and Tor circuit states upon screen-lock, USB panic trigger, or lid spoof alert.

---

## 3. Five New Local Hardware & Microarchitectural Attack Shields (`#31`–`#35` in `:core:hw`)

| Vector # | Attack Class & Primary Reference | Kernel (`:core:hw`) | Exact Detection & Mitigation Contract |
|---:|---|---|---|
| **`#31`** | **Rogue USB-C PD Structured VDM / `EnterUSB` Alt-Mode Injection** (`USB PD R3.2 §6.4.12.2 Table 6.33`, `USB PD 3.1 §6.4.9`) | `UsbPdStructuredVdmAltModeGuard.kt` | Rejects unstructured VDMs (`bit 15 == 0`) masquerading as `Discover Identity`, rejects invalid 5-bit command codes (`7..15` or `>= 32`), blocks SVID `0x8087` (Thunderbolt/USB4) and vendor-specific commands `16..31` whenever the shell is locked or untrusted, and allows safe 5V/9V/20V power-only charging. |
| **`#32`** | **`Hertzbleed` DVFS CPU/GPU Frequency-Throttling Side-Channel** (cryptographic secret-dependent power throttling) | `DvfsHertzbleedFrequencyNoiseGuard.kt` | Computes Pearson correlation & step-transition entropy over `cpufreq` kHz samples during cryptographic windows; flags abnormal secret-correlated throttling drops and enforces constant-time jittered execution windows + frequency-telemetry redaction for untrusted processes. |
| **`#33`** | **`Pixnapping` / Cross-Window GPU Compositing Frame-Time Pixel Theft** (`vsync` render-duration side-channel) | `GpuPixelTimingPixnappingShield.kt` | Enforces coarse quantum snapping (`e.g. 16.67ms` bucketed frame callbacks) for untrusted web/sandboxed panes whenever a `FLAG_SECURE`, Vault, or Tails window is visible on the tabletop, forcing opaque layer isolation with zero cross-surface shader blend. |
| **`#34`** | **IEEE 802.11bf Wi-Fi CSI / FTM Keystroke Acoustic-RF Sensing** (subcarrier phase/amplitude finger-motion inference) | `WifiCsiKeystrokeSensingMitigator.kt` | Detects high-rate 802.11mc/az Fine Timing Measurement (`FTM`) bursts or anomalous NDP sounding cadence during sensitive PIN/passphrase focus, suppressing unauthenticated FTM responders and randomizing keystroke UI feedback cadence. |
| **`#35`** | **Hall-Effect Lid Sensor + Magnetometer Cover-Spoof & Induction Attack** | `HallMagnetometerCoverSpoofGuard.kt` | Cross-validates binary Hall-switch state against 3-axis $\mu\text{T}$ magnetic field magnitude & slew rate ($d|\mathbf{B}|/dt$); blocks synthetic magnet unlock spoofing while still honoring lock-on-close fail-safe posture. |

---

## 4. Passkey / WebAuthn Level 3, Redacted Capture & Zero-Telemetry Privacy Gates

- **`WebAuthnLevel3AuthDataVerifier.kt` + `StrongBoxKeyAttestationExtensionParser.kt` + `TotpHotpRfc6238PasskeyVaultCodec.kt` (`SC-01`):** Hardware-backed passkey & TOTP vault verification with strict `BS/BE` flag validation, exact 37-byte assertion length enforcement, and public-suffix phishing defense.
- **`CommitFirstRedactedCapturePipeline.kt` (`MD-01`):** Irreversible pixel-block zeroization + mosaic/box replacement **before** capture buffer persistence so redacted regions never touch disk or undo stacks.
- **`ZeroTelemetryRedactedLogExporter.kt` (`SC-08`):** Proves default telemetry disabled (`0` endpoints) and scrubs IPv4/IPv6, bearer tokens, emails, MAC/BSSIDs, and user home paths from any user-exported diagnostic bundle.
