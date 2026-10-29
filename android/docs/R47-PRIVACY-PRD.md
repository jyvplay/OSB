# Round 47 (`R47`) Privacy, Tails-Light Anonymity & Anti-Keylogger PRD

Date: `2026-10-26`  
Status: **Host-Verified Pure-Kotlin Policy & Protocol Kernels (`H+`); Not Full-OS Tails or Physical Hardware Attestation (`H-`)**

---

## 1. Honest Threat-Model Scope

Fusion Shell's `:privacy`, `:core:security`, `:core:hw`, `:feature:accessibility`, and `:app` modules provide **application- and companion-APK-layer compartmentalization** ("Tails-Light" + defense-in-depth anti-keylogger). Because Android apps run in userspace above an untrusted OEM bootloader/baseband/kernel unless paired with verified OS support:
- **Protected Against (Host-Verified Logic)**:
  1. Cross-application and cross-origin Tor circuit correlation over SOCKS5 (`TorSocksIsolation` Proposal 351 `<torS0X>0`).
  2. Motion-sensor keystroke inference (`Glitch in Time` `200+200 Hz` phase-offset reconstruction and `AlphaLogger` `10 Hz` accel+gyro+mag fusion) during text/secret focus (`ImuSideChannelGuard` `0 Hz` blackout).
  3. Fixed-coordinate screen-tap observation and timing side-channels during PIN/passphrase entry (`SecureKeypadScrambler` CSPRNG Fisher-Yates layout + constant-scan hit resolution).
  4. Untrusted cloud/network-capable IME keylogging on secret fields (`ImeTrustGate` -> `FORCE_BUILTIN_SCRAMBLED_PAD`).
  5. Clipboard secret persistence & shoulder-surfing (`ClipboardSensitiveGuard` `EXTRA_IS_SENSITIVE` + `30_000 ms` TTL auto-wipe).
  6. Screen-reader credential vocalization/scraping (`ScreenReaderRedactor`), tapjacking obscured touches (`SecureSurfacePolicy`), lockscreen BadUSB hotplug (`UsbWatchReceiver` + `UsbGuardPolicy`), and author/timestamp metadata leaks in `.docx`, `.xlsx`, `.odt`, and `.pdf`.
- **Explicitly Out of Scope (`H-` Non-Goals)**:
  - Kernel compromise, hardware JTAG/cold-boot DRAM extraction prior to zeroization, compromised Android SurfaceFlinger/InputDispatcher, or Tails Linux `tmpfs`-root live-USB boot amnesia.

---

## 2. Primary Security & Privacy Invariants Enforced in `R47`

| Invariant ID | Kernel (`Module`) | Enforced Specification | Verification Gate |
| --- | --- | --- | --- |
| `PRIV-R47-01` | `TorSocksIsolation` (`:privacy`) | Tor Proposal `351` (`<torS0X>0`) RFC 1929 SOCKS5 auth (`0x01 \|\| 0x09 \|\| "<torS0X>0" \|\| 0x40 \|\| hex_hmac`); rejects `<torS0X>` with any non-`'0'` version byte; warns on plaintext `http://*.onion` | `R47Checks` + `check_r47_reference.py` (`tor_prop351_rfc1929_wire`) |
| `PRIV-R47-02` | `ImuSideChannelGuard` (`:privacy`) | `0 Hz` complete sensor drop across `ACCELEROMETER`, `GYROSCOPE`, `MAGNETOMETER`, `LINEAR_ACCELERATION`, `ROTATION_VECTOR` whenever `textEntryFocused \|\| secretFieldFocused`; `<= 10 Hz` quantized when unfocused | `R47Checks.testImuSideChannelGuardBlackout` |
| `PRIV-R47-03` | `SecureKeypadScrambler` (`:core:security`) | CSPRNG Fisher-Yates permutation per prompt (`12` PIN cells or `18` hex cells), `>= 48dp x 48dp` WCAG/Android AX touch target, constant-scan all-cell hit test | `R47Checks` (`40` random layouts) + `r47-workbench-preview.png` |
| `PRIV-R47-04` | `ClipboardSensitiveGuard` (`:core:security`) | Classifies PEM keys, JWTs, API tokens (`ghp_`/`sk-`/`AKIA`), seed phrases, OTPs, and high-entropy secrets -> `isSensitive=true`, `30s` monotonic TTL wipe, `5s` panic wipe | `R47Checks.testClipboardSensitiveGuardAndTtl` |
| `PRIV-R47-05` | `ImeTrustGate` (`:core:hw`) | Blocks or overrides third-party IMEs holding `android.permission.INTERNET` on `SECRET_PIN_OR_PASSPHRASE` fields (`FORCE_BUILTIN_SCRAMBLED_PAD`) | `R47Checks.testImeTrustGatePolicies` |
| `PRIV-R47-06` | `ScreenReaderRedactor` (`:feature:accessibility`) + `SecureSurfacePolicy` (`:app`) | Masks secret `AccessibilityEvent` text while preserving TalkBack length usability; sets `FLAG_SECURE (0x2000)` + `hideOverlayWindows` and drops `MOTION_FLAG_WINDOW_IS_OBSCURED` touches | `R47Checks` + `MainActivity.kt` (`PARSE OK`) |
| `PRIV-R47-07` | `RamWipeSchedule` (`:privacy`) | Priority-ordered (`VAULT_MASTER_KEYS` -> `SESSION_TOKENS` -> `CLIPBOARD` -> `SCRATCH`) 2-pass `0xFF` then `0x00` readback-verified zeroization on screen-lock / USB panic / OOM trim | `R47Checks.testRamWipeScheduleVerification` |
| `PRIV-R47-08` | `DocxRichDocument`, `XlsxStyledBook`, `OdtRichWriter`, `PdfOutlineBookmarks` | Zero `docProps/core.xml`, zero `meta.xml`, zero `/Author`/`/Producer`, deterministic `1980-01-01T00:00:00Z` ZIP entry timestamps; external OOXML relationships & XXE refused | `check_r47_reference.py` (`40/40`) |
