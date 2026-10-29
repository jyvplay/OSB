# Round 49 (`R49`) Primary Research & Permissive Specification Corpus

**Retrieved & verified**: `2026-10-27` (sandbox outbound allowlist + primary RFC/W3C/ECMA/ISO/USENIX/CCS specs).
**License & clean-room policy**: 100% written from scratch under `Apache-2.0`; zero GPL/AGPL/LGPL/SSPL code copied, translated, or renamed; all algorithms derived strictly from open IETF RFCs, ECMA/ISO/OASIS/W3C standards, and peer-reviewed academic publications.

## 1. Local Hardware-Origin Keylogger, Charger & Pixel Side-Channel Defenses (2025–2026)

1. **`ChoiceJacking` (TU Graz & A-SIT, 34th USENIX Security Symposium, Aug 2025)** — *ChoiceJacking: Compromising Mobile Devices through Malicious Chargers like a Decade ago* (`https://www.usenix.org/system/files/usenixsecurity25-draschbacher.pdf`):
   - **Attack mechanism**: Defeats modern Android/iOS Juice-Jacking USB data-consent prompts in **133 ms** without user interaction via three techniques:
     1. *Platform-agnostic HID->BLE bridge*: Charger enumerates briefly as a USB HID keyboard, injects shortcuts to enable Bluetooth and pair an embedded BLE keyboard, then switches USB roles to host and uses BLE input to approve data transfer (`MTP`/`PTP`/`ADB`).
     2. *Android input-buffer flood*: Charger floods fake USB HID keystrokes immediately prior to USB role switch so queued confirmation keystrokes fire while the USB consent dialog renders.
     3. *Android Open Accessory Protocol (`AOAP`, VID `0x18D1`, PIDs `0x2D00..0x2D05`)*: Abuses AOAP to act simultaneously as USB host and HID input device, plus monitors USB power-line load to wait until the user is distracted (e.g., active phone call).
   - **Clean-room R49 defense (`ChoiceJackingUsbGuard.kt` + `UsbDataSignalingController.kt`)**:
     - Enforces `USB_DATA_SIGNALING = CHARGE_ONLY` whenever screen is locked, during active voice/VoIP calls, or within a `2,500 ms` post-VBUS stabilization quarantine window.
     - Quarantines composite/rapidly re-enumerating `VBUS -> HID -> RoleSwitch/BLE` sequences (`< 5,000 ms`) and blocks `AOAP` (`0x18D1:0x2D00..0x2D05`) simultaneous host+HID accessory claims unless explicitly fingerprinted in an unlocked hardware allowlist.
     - Drops all synthetic/external HID `Enter`/`Space`/`Tab` key events while system USB/ADB/MTP consent surfaces are visible.

2. **`Pixnapping` (`CVE-2025-48561`, CMU / UC Berkeley / UW, ACM CCS Oct 2025)** — *Pixnapping: Stealthy Pixel-by-Pixel Screen Reconstruction via Android Window Blur and `GPU.zip` Side Channels* (`https://www.pixnapping.com/`, `https://www.cylab.cmu.edu/news/2025/10/13-pixnapping.html`):
   - **Attack mechanism**: Unprivileged malicious Android app (zero permissions) launches target app/website via `Intent`, stacks semi-transparent Activities with cross-window blur over target pixel coordinates, and measures `GPU.zip` graphical memory compression frame-render timings to recover 2FA codes, crypto seed phrases, and private messages in `< 30 s` on Android 13–16 (Pixel 6–9, Galaxy S25).
   - **Clean-room R49 defense (`PixnappingSurfaceShield.kt`)**:
     - Enforces `FLAG_SECURE` + `Window.setHideOverlayWindows(true)` (`HIDE_OVERLAY_WINDOWS`) + `setCrossWindowBlurEnabled(false)` on all Vault, Passphrase, Seed, 2FA, and Incognito surfaces.
     - Rejects untrusted cross-UID `Intent` task-affinity / translucent-stack launches (`LAUNCH_SINGLE_TASK` isolation) so external apps cannot sandwich sensitive Fusion Activities inside an attacker task stack.
     - Applies deterministic bounded sub-pixel glyph coordinate dither (`±1 px`) + zero-mean high-frequency background noise tile on OTP/seed canvases to decorrelate `GPU.zip` cacheline compression ratios from glyph bitmaps.

3. **Power / CPU DVFS / `/proc` Keystroke Timing Side Channels (`PowerDvfsTimingMitigator.kt`)**:
   - **Attack mechanism**: Co-resident unprivileged processes sample battery current (`BatteryManager`), CPU frequency scaling transitions, or inter-interrupt timing to infer inter-keystroke flight times (`15–180 ms` digraph distributions).
   - **Clean-room R49 defense**: Quantizes sensitive-input event delivery onto fixed `25 ms` isochronous epochs with bounded constant-shape dummy MAC ticks on idle epochs, eliminating inter-keystroke digraph timing correlation.

## 2. Tails-Light Anonymity (`Tails 7.15.1` + `RFC 9849` TLS ECH + `RFC 8828` WebRTC Mode 4)

4. **Tails `7.15` (`2026-10-13`) & Emergency Release `7.15.1` (`2026-10-23`)** (`https://safeguard.sh/resources/blog/tails-release-history-amnesic-live-system-tor-persistent-storage-and-the-update-model`, `https://linuxiac.com/tails-7-15-1-privacy-focused-linux-distro-released-as-emergency-security-update/`, `https://9to5linux.com/tails-7-7-anonymous-distro-adds-detection-of-outdated-secure-boot-certificates`):
   - **Key 2026 Tails capabilities confirmed**:
     - Emergency system-wide distrust of compromised Certificate Authorities (`CVE-2026-113470` / eMudhra intermediate revocation across both Tor Browser `15.0.26` and system `ca-certificates`) — crucial because Tor exit relays can otherwise mount active TLS MITM using rogue CA intermediates.
     - Outdated UEFI Secure Boot certificate expiry detection (2011 Microsoft/UEFI CA certificates expiring starting June 2026, added in Tails `7.7+`).
   - **Clean-room R49 implementation (`TailsCaTrustPinBlocklist.kt`)**: System-wide SPKI SHA-256 + issuer-DN emergency CA revocation gate + Secure Boot certificate expiry/rollover auditor.

5. **IETF `RFC 9849` — TLS Encrypted Client Hello (`ECH`)** (`https://www.rfc-editor.org/rfc/rfc9849.html`):
   - Encrypts the sensitive TLS 1.3 `ClientHelloInner` (including target `SNI` and `ALPN` list) under HPKE (`RFC 9180`) keys fetched via DNS `HTTPS`/`SVCB` (`RFC 9460`, `SvcParamKey ech = 5`), presenting only the anonymity-set `public_name` in `ClientHelloOuter`, with `ECH GREASE` dummy extensions when ECH config is absent and strict downgrade detection.
   - **Clean-room R49 implementation (`TlsEchConfigCodec.kt`)**: Full binary `ECHConfigList` (`version = 0xFE0D`) encoder/decoder, `HTTPS` RR `ech` param parser, `GREASE` decoy payload generator, and `public_name` / cipher-suite / outer-SNI leak validator.

6. **IETF `RFC 8828` — WebRTC IP Address Handling Requirements + mDNS `.local` Candidates** (`https://www.rfc-editor.org/rfc/rfc8828.html`, `https://blog.crawlex.net/blog/webrtc-ip-leakage/`):
   - Default Browser Mode 2 exposes real ISP public IPs via STUN `srflx` UDP candidates even when HTTP traffic uses a SOCKS/Tor/OHTTP proxy, while `.local` mDNS only masks LAN `host` candidates.
   - **Clean-room R49 implementation (`WebRtcIceCandidateScrubber.kt`)**: Implements all four `RFC 8828` modes (`MODE_1_ENUMERATE_ALL`, `MODE_2_DEFAULT_ROUTE_AND_LOCAL`, `MODE_3_DEFAULT_ROUTE_ONLY`, `MODE_4_DISABLE_NON_PROXIED_UDP`) with default `MODE_4` in Tails-Light/Incognito profiles (stripping `host`/`srflx`/`prflx` UDP candidates and allowing only proxied TCP/TURN `relay` or mDNS-masked `.local` candidates when LAN mode is explicitly authorized).

## 3. Spreadsheet `LAMBDA` + Dynamic Array Reshaping, Writer Optimal Line-Breaking & Wasm 2.0

7. **`IronCalc v0.8.0` (`MIT`/`Apache-2.0`, PRs `#909`, `#912`, `#914`, `#915`, Jul–Aug 2026) & `rustsheet` PR `#1608`** (`https://newreleases.io/project/github/ironcalc/IronCalc/release/v0.8.0`, `https://github.com/steveash/rustsheet/pull/1608`):
   - Confirms modern clean-room Excel parity requires first-class `LAMBDA` closures + higher-order helpers (`MAP`, `REDUCE`, `SCAN`, `BYROW`, `BYCOL`, `MAKEARRAY`) + table reshaping (`VSTACK`, `HSTACK`, `TAKE`, `DROP`, `TOCOL`, `TOROW`, `CHOOSECOLS`, `CHOOSEROWS`, `WRAPROWS`, `WRAPCOLS`, `TRANSPOSE`), including preserving all rows/columns when `TAKE`/`DROP` row or column count arguments are omitted (`TAKE(arr, , -1)`, `DROP(arr, 1)`).
   - **Clean-room R49 implementation (`FormulaEval.kt` `95 -> 130` functions + `SpillSheetEngine.kt` `6 -> 22` array/LAMBDA engines + `DrawingMlChartBuilder.kt` + `SheetSparklineRenderer.kt` + `SheetSolverSimplex.kt`)**.

8. **Knuth–Plass Optimal Paragraph Line-Breaking (`Software—Practice and Experience`, 1981) & Franklin M. Liang TeX Hyphenation (Stanford PhD Thesis, 1983)**:
   - Replaces greedy ragged-right line wrapping with dynamic-programming minimum-demerits justification over boxes, stretch/shrink glues, and hyphenation penalties (`KnuthPlassLineBreaker.kt` + `LiangHyphenationEngine.kt` + `MultiColumnPagePaginator.kt` + `OdfTrackedChangesCodec.kt`).

9. **W3C WebAssembly 2.0 (`0xFC` Non-Trapping Float-to-Int `trunc_sat` `0x00..0x01`, Bulk Memory `0x08..0x0B`, `Table` / `call_indirect`, `Global`)**:
   - Upgrades `WasmSandbox.kt` with Section 4 (`Table`), Section 6 (`Global`), Section 9 (`Element`), Section 11 (`Data` active + passive `memory.init`/`data.drop`), `call_indirect` runtime signature verification, `f32` IEEE-754 arithmetic, and `i32.trunc_sat_f32_s/u` saturation.
