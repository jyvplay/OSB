# R53 Tails-Light Anonymity & Hardware Origin Defense PRD

## 1. Tails-Light Anonymity Upgrades (Smaller Footprint, Zero Copyleft)
1. **Clean-Room `MAT2`-Equivalent Outbound File Sanitizer (`TailsMetadataAnonymizer`)**:
   - Automatically strips identifying metadata (`EXIF` GPS/camera serials, `XMP` creator history, `IPTC`, PNG `tEXt`/`iTXt`/`eXIf`/`tIME`, MP4 `udta`/`meta`, MP3 `ID3v1`/`ID3v2`, PDF `/Info` & `/Metadata`, OOXML `docProps/core.xml` & `app.xml`) into `.cleaned.<ext>` copies before sharing or exporting.
2. **Tor Proposal 329 `Conflux` Multipath Circuit Splicer (`TorConfluxMultipathScheduler`)**:
   - Enforces disjoint Guard and Middle relays across linked legs (`guardA != guardB && middleA != middleB`), schedules 514-byte relay cells via `LOWRTT_TOR` or `BLEST_TOR` ($\lambda$-biased head-of-line blocking avoidance), reorders out-of-order arrivals before stream delivery, and emits `RELAY_CONFLUX_XOFF`/`XON` backpressure.
3. **Recall Vault Metadata & Incognito Isolation**:
   - `Recall` continues to enforce **zero disk writes** in Incognito/Private tabs, **hard refusal** on DRM (`Widevine`/`PlayReady`/`FairPlay`/`SAMPLE-AES`), `FLAG_SECURE`, and `ALLOW_CAPTURE_BY_NONE`, and strips tracking parameters + HTTP `Cookie`/`Authorization` headers in both `.har` and `WARC/1.1` exports.

## 2. Local Hardware Origin Attack Vectors `#26`–`#30` (Cumulative `30` Vectors)
- **`#26` `TempestSubpixelEmsecDitherer`**: Mitigates `Deep-TEMPEST` (LADC 2024) CNN HDMI RF emanation text recovery via deterministic spatial Bayer/blue-noise subpixel luminance dithering while preserving `>= 7.0:1` WCAG text contrast.
- **`#27` `BleHogpKeystrokeFirewall`**: Mitigates `CVE-2023-45866` (`BlueDucky`) & `BLESA` unauthenticated Bluetooth/BLE HID keystroke injection by requiring authenticated MITM + Secure Connections bond state and quarantining `< 8 ms` keystroke bursts.
- **`#28` `VerifiedBootVbmetaDigestAuditor`**: Audits 256-byte `AVB0` `AvbVBMetaImageHeader` images (`magic`, `flags` verity/verification disable bits, `rollback_index >= storedFloor`, partition SHA-256 hash descriptors) against physical Evil-Maid boot tampering.
- **`#29` `AmbientLightKeystrokeShadowGuard`**: Freezes `Sensor.TYPE_LIGHT` delivery during credential/IME entry and quantizes lux outside focus to defeat hand-shadow PIN inference.
- **`#30` `DramRowhammerRefreshMonitor`**: Tracks LLC-evicting aggressor activation rates within `tREFW` (`32/64 ms`) and triggers proactive buffer reallocation / page retirement advisories before bit-flip thresholds.
