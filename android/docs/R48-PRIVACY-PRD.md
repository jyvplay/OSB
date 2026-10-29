# Round 48 (`R48`) — Tails-Light Anonymity & 5-Channel Anti-Keylogger PRD

## 1. Threat Model & Primary Literature Grounding (`2026-10-26`)
1. **Network Correlation & Resolver Tracking**:
   - Even with TLS 1.3, direct HTTPS reveals client IP to target servers, while shared SOCKS5 circuits correlate cross-origin streams.
   - **Mitigations**:
     - `TorSocksIsolation` (R47) enforces **Tor Proposal 351 (`<torS0X>0`)** per-compartment/per-origin isolation.
     - `OnionV3Address` (R48) validates 56-character **Tor v3 `.onion`** addresses using pure-JVM **NIST FIPS 202 `SHA3-256`** (`Keccak-f[1600]`) and rejects deprecated 16-char SHA-1 v2 addresses fail-closed.
     - `BinaryHttpRfc9292` + `ObliviousHttpPolicy` + `ExitTrafficPadBucket` (R48) implement **IETF RFC 9292 Binary HTTP** (with `128 B` / power-of-two zero padding) and **RFC 9458 Oblivious HTTP** split-trust policy enforcement (distinct Relay vs Gateway operators, HTTPS-only, `0-RTT` forbidden, outer `Cookie`/`Authorization` stripped).
2. **5-Channel Keylogger & Side-Channel Threat Matrix**:
   - **Channel 1 — Acoustic & Ultrasonic (`Improved CoAtNet` Jan 2026 `99.8%`, `ASCA-Net` Jul 2026 `96.97%` VoIP, `SonarSnoop` Feb 2026 `20 kHz` active sonar)**:
     - `AcousticKeystrokeShield` enforces hard `ZERO_FILL_MUTE` whenever any password, PIN, seed-phrase, or `:feature:vault` field holds focus; during general typing with an active foreground VoIP call, collapses stereo `L/R` PCM to monaural `(L+R)/2` (destroying dual-mic TDoA tap triangulation), applies a 33-tap Blackman-windowed sinc `18 kHz` low-pass filter (`20 kHz` attenuated to `0 RMS`), and schedules decoy click masking.
   - **Channel 2 — Malicious USB & BLE HID (`BadUSB`, `BlueDucky`, Composite Vendor `0xFF00` C2)**:
     - `UsbGuardPolicy` + `BleHidPairingGate` (R46) + `HidUsageFirewall` (R48) parse raw USB HID 1.11 Report Descriptors byte-by-byte, rejecting `0xFF00..0xFFFF` Vendor-Defined C2 pages, System Power Down controls, and `GUI+R` / `CTRL+ALT+T` rapid keystroke-injection chords, backed by `KeystrokeJitter` cadence classification.
   - **Channel 3 — Motion Sensor Side-Channel (`Glitch in Time` `400 Hz` IMU)**:
     - `ImuSideChannelGuard` (R47) enforces `0 Hz` sensor blackout during sensitive input and caps background rate to `<= 50 Hz`.
   - **Channel 4 — Screen Capture, Virtual Display, Clipboard, IME & Accessibility**:
     - `ScreenCaptureGuard` (R48) models Android 14/15 `DETECT_SCREEN_CAPTURE` + unsecure `DisplayManager` virtual/presentation displays (`FLAG_SECURE` absent) + untrusted Autofill services, triggering immediate `Vault` auto-lock and sensitive surface redaction alongside `SecureSurfacePolicy`, `ClipboardSensitiveGuard`, `ImeTrustGate`, and `ScreenReaderRedactor`.
   - **Channel 5 — Touch Coordinate Observation & Phishing**:
     - `SecureKeypadScrambler` CSPRNG Fisher-Yates PIN pad + `PasskeyRpValidator` W3C WebAuthn Level 3 RP ID suffix check, monotonic `signCount` clone detection, and `BS <= BE` flag invariant.

## 2. Explicit Runtime Honesty Boundary (`H-`)
All cryptographic framings, FIR DSP filters, HID descriptor parsers, and policy state machines execute and verify deterministically on OpenJDK 17 (`3,861` assertions + `56` Python differential checks). Actual OS-level `AudioRecord` HAL interception, `VpnService` `tun0` packet capture, `WindowManager.LayoutParams.FLAG_SECURE` SurfaceFlinger compositing, and Linux kernel `udev`/USB bus blocking require Android 15–17 physical/emulated hardware (`0/50` Class A accepted in host sandbox).
