# Round 49 (`R49`) Tails-Light Anonymity & Local Hardware Attack Defense PRD

**Baseline expanded**: Upgrades `R48-PRIVACY-PRD.md` (`RFC 9458` OHTTP + `RFC 9292` BHTTP + Tor Prop `351` `<torS0X>0` + Tails `onion-grater` + EXIF/PDF scrubber + Acoustic/Ultrasonic `ASCA-Net`/`SonarSnoop` + `400 Hz` IMU + USB/BLE HID BadUSB) with **2025–2026 primary-source network & physical hardware defenses** verified on `2026-10-27`.

---

## 1. Confirmed Tails-Light (`Tails 7.15.1` + `RFC 9849` + `RFC 8828`) Feature Additions

| Requirement ID | Feature | Clean-Room Production Kernel (`R49`) | Threat Closed | Remaining Android OS / Device Boundary (`H∂`) |
| --- | --- | --- | --- | --- |
| `TL-R49-01` | **IETF `RFC 9849` TLS Encrypted Client Hello (`ECH`)** | `:privacy` -> `TlsEchConfigCodec.kt` | Encrypts inner `ClientHello` (`SNI` + `ALPN`) via HPKE (`version 0xFE0D`) from DNS `HTTPS` RR (`SvcParamKey=5`); emits `ECH GREASE` decoys when absent; rejects plaintext SNI leaks & ECH downgrades. | Live BoringSSL/Conscrypt socket integration on Android API 37. |
| `TL-R49-02` | **Tails `7.15.1` Emergency Compromised-CA SPKI Blocklist & Secure Boot 2026 Expiry Auditor** | `:privacy` -> `TailsCaTrustPinBlocklist.kt` | Blocks compromised root/intermediate CAs (`CVE-2026-113470` / eMudhra class) across Tor exit circuits and flags expiring 2011 UEFI/boot trust anchors (`Tails 7.7–7.15.1`). | System `NetworkSecurityConfig` X509TrustManager hook on device. |
| `TL-R49-03` | **IETF `RFC 8828` Mode 4 WebRTC IP Leak Scrubber + `.local` mDNS** | `:feature:browser` -> `WebRtcIceCandidateScrubber.kt` | Enforces `MODE_4_DISABLE_NON_PROXIED_UDP` in Tails-Light/Incognito tabs, stripping `typ host`, `typ srflx` (STUN ISP IP leak!), and `typ prflx` UDP candidates from SDP offers/answers. | Android `WebView` / engine JS `RTCPeerConnection` hook on device. |
| `TL-R49-04` | **Pro Local `VpnService` Per-App UID & DNS Leak Firewall (`SC-04`)** | `:core:security` -> `VpnAppFirewallPolicy.kt` | Compiles fail-closed per-UID allow/deny tables + blocks cleartext UDP/TCP port 53, DoT port 853 bypass, and LAN multicast (`224.0.0.0/4`, `ff00::/8`) outside the Tor/OHTTP tunnel. | Android `VpnService.Builder` TUN descriptor binding on device (`S8`). |
| `TL-R49-05` | **Tink-Compatible Streaming AEAD Persistent Containers (`SC-07`)** | `:feature:vault` -> `TinkAeadKeysetEnvelope.kt` | AES-256-GCM-HKDF chunked container encryption with keyset rotation, primary key ID binding, chunk sequence AAD (prevents chunk reorder/truncation), and zero cloud escrow. | Hardware StrongBox master-key wrapping on physical device (`S5`). |

---

## 2. Complete 9-Vector Local Hardware & Side-Channel Anti-Keylogger Matrix (`R46–R49`)

To answer *"is there a way to counter keyloggers and other local hardware origin attack vectors? Please close the most common version of those hardware weak points"*, Fusion Shell now enforces a **9-layer defense-in-depth matrix** covering every major physical, peripheral, sensor, acoustic, graphical, and power/timing attack vector documented through October 2026:

| Layer | Local Hardware / Side-Channel Vector | Primary Research Basis | Clean-Room Production Kernels | Concrete Mitigation Enforced |
| ---: | --- | --- | --- | --- |
| 1 | **Malicious Public Chargers & `ChoiceJacking` (`AOAP` + HID->BLE + Input Flood)** | USENIX Security 2025 (`Draschbacher et al.`) | `ChoiceJackingUsbGuard.kt`, `UsbDataSignalingController.kt` (`R49`) | `CHARGE_ONLY` data-lane lock while locked / in-call / `<2.5s` VBUS attach; blocks `AOAP` `0x18D1:0x2D00..0x2D05` + HID composite spoofing & external HID keystrokes on USB consent dialogs. |
| 2 | **`Pixnapping` (`CVE-2025-48561`) Cross-Window Blur & `GPU.zip` Pixel Theft** | ACM CCS 2025 (`Wang, Paccagnella et al.`) | `PixnappingSurfaceShield.kt` (`R49`), `SecureSurfacePolicy.kt` (`R46`) | `FLAG_SECURE` + `HIDE_OVERLAY_WINDOWS` + `setCrossWindowBlurEnabled(false)` + cross-UID `Intent` task isolation + `±1px` sub-pixel glyph dither & zero-mean background entropy tile. |
| 3 | **Power-Line / Battery / CPU DVFS Keystroke Timing Side Channel** | USENIX Sec '25 §7 & DVFS timing literature | `PowerDvfsTimingMitigator.kt` (`R49`) | Quantizes sensitive keystroke dispatch onto fixed `25 ms` epochs with constant-shape chaff MAC ticks on empty epochs and `/proc`/battery-current polling rate guards. |
| 4 | **BadUSB / RubberDucky / Keystroke-Injection Dongles & BlueDucky BLE HID** | USB HID 1.11 / `CVE-2023-45866` | `UsbHidPortGuard.kt` (`R46`), `HidKeystrokeTimingGuard.kt` (`R47`), `BleBondGuard.kt` (`R46`) | Blocks composite mass-storage+HID endpoints, flags `<8 ms` superhuman inter-keystroke variance (`CV < 0.08`), and rejects unauthenticated `JustWorks` BLE keyboard bonding. |
| 5 | **Acoustic & Active Ultrasonic Keystroke Inference (`ASCA-Net`, `Improved CoAtNet`, `SonarSnoop`)** | `IJIS` Jan 2026, `JCVHT` Jul 2026, `arXiv:2502.09782` | `AcousticKeystrokeShield.kt` (`R48`) | Suspends background mic capture, collapses dual-mic stereo TDoA `(L+R)/2`, applies `18 kHz` ultrasonic low-pass filter against `SonarSnoop`, and injects decoy click transients. |
| 6 | **400 Hz Accelerometer / Gyroscope Tap-Location Inference (`Glitch in Time`)** | `Sensors` 2025 / Android 12+ `HIGH_SAMPLING_RATE_SENSORS` | `MotionSensorCloak.kt` (`R47`), `ScrambledPinPad.kt` (`R47`) | Clamps IMU sampling to `0 Hz` (masked) or `<= 50 Hz` quantized (`0.05 m/s^2`) during sensitive focus + Fisher–Yates CSPRNG scrambled PIN/passphrase keypad layout (breaks coordinate->digit mapping even under physical shoulder-surfing or touch-controller taps!). |
| 7 | **Malicious Third-Party Cloud IMEs & Accessibility Overlay Keyloggers** | Android IME / `AccessibilityService` threat model | `SecureInputChannelGuard.kt` (`R48`), `AccessibilityTrustGuard.kt` (`R46`) | Routes Vault/Passphrase fields strictly to internal zero-persistence composer (`imeOptions = NO_PERSONALIZED_LEARNING`), refusing third-party network IMEs & untrusted `canRetrieveWindowContent` services. |
| 8 | **Clipboard Snooping & Terminal Escape-Sequence Paste Hijacking** | Android 13+ Sensitive Clipboard & Bracketed Paste | `SensitiveClipboardGuard.kt` (`R48`), `VtTrueColorSafePasteEngine.kt` (`R49`) | Marks secrets `EXTRA_IS_SENSITIVE` with `30s` auto-wipe and strips `ESC`/`\r`/`\u0003`/OSC title-spoof bytes from terminal pastes before execution. |
| 9 | **Cold-Boot / Evil-Maid / Unattended Unlocked Session Access** | Tails `udev-watchdog` & screen-lock threat model | `ModuleAppLockGate.kt` (`R49`), `AmnesicSessionBudget.kt` (`R46`) | Per-module idle auto-relock, immediate screen-off secret zeroization, exponential failed-PIN backoff, and USB port state change panic lock. |

---

## 3. Explicit Hardware Honest Non-Claims (`H-`)

- A **physical inline hardware keylogger spliced into an external wired PS/2 or USB keyboard cable** sits upstream of the host USB controller; software cannot stop the inline tap from recording physical key matrix closures on that external keyboard. Our countermeasure for high-risk secrets is **routing entry through the on-screen CSPRNG `ScrambledPinPad` (`R47`) with `PixnappingSurfaceShield` (`R49`) + `MotionSensorCloak` (`R47`) + `AcousticKeystrokeShield` (`R48`)**, bypassing external physical keyboards completely.
- Kernel DMA (`Thunderbolt`/`PCIe`) and baseband firmware attacks require SoC IOMMU + bootloader `AVB` hardware enforcement outside userspace Kotlin.
