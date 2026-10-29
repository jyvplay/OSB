# R53 Primary Standards & Security Research Ledger (Verified 2026-10-29)

All R53 mechanisms are designed clean-room under `SPDX-License-Identifier: Apache-2.0` from public IETF RFCs, ISO/W3C/ECMA specifications, AOSP specifications, and peer-reviewed security papers. Zero copyleft (`GPL`/`LGPL`/`AGPL`) source code is copied or translated.

## 1. Browser Media Archiving — 6-Method `Recall` Pipeline (`WARC/1.1` & `RTP/Opus`)
- **ISO 28500:2017 (`WARC/1.1` Web ARChive file format)**:
  - Grammar: `warc-file = 1*warc-record`, `warc-record = header CRLF block CRLF CRLF`, `version = "WARC/1.1" CRLF`.
  - Mandatory headers per record: `WARC-Record-ID` (`<urn:uuid:...>`), `Content-Length` (exact octet length of `block`), `WARC-Date` (UTC ISO-8601 `YYYY-MM-DDThh:mm:ssZ`), `WARC-Type` (`warcinfo`, `response`, `resource`, `metadata`, `request`, `revisit`, `conversion`, `continuation`).
  - Integrity digests: `WARC-Block-Digest` and `WARC-Payload-Digest` (`sha256:<hex/base32>`) enable deterministic verification and deduplicated `revisit` records.
- **IETF RFC 3550 (RTP) & RFC 7587 (RTP Payload Format for Opus Audio)**:
  - Fixed 12-byte RTP header: `V=2` (2b), `P` (1b), `X` (1b), `CC` (4b), `M` (1b), `PT` (7b), `sequence number` (16b big-endian), `timestamp` (32b big-endian), `SSRC` (32b big-endian), followed by `4 * CC` CSRC bytes and optional RFC 5285 extension (`0xBEDE` 1-byte or `0x1000` 2-byte header).
  - RFC 7587 §4.1 mandates a fixed `48,000 Hz` RTP timestamp clock across all Opus bandwidths (`960` ticks per `20 ms` frame) and requires receivers to suppress duplicate RTP packets and conceal sequence gaps.

## 2. Tails-Light Anonymity — Tor Proposal 329 (`Conflux`) & `MAT2`-Class Metadata Scrubbing
- **Tor Proposal 329 (`329-traffic-splitting.txt`, Goulet & Perry / AlSabah et al., C-Tor `0.4.8+`)**:
  - Splits relay stream cells across primary (low-RTT) and secondary circuits merging at an Exit/Rendezvous point while requiring disjoint Guard and Middle relays (`guard_1 != guard_2 && middle_1 != middle_2`).
  - Uses per-leg Proposal 324 `cwnd - inflight` congestion windows (`LOWRTT_TOR` and `BLEST_TOR` schedulers), a 64-bit relative sequence number reassembly queue at the merge endpoint, and `RELAY_CONFLUX_XOFF` backpressure when reorder queue hits high-water mark until drained to `50%` (`RELAY_CONFLUX_XON`).
- **Clean-Room Metadata Sanitization (inspired by Tails `MAT2` feature set; upstream `mat2` is `LGPLv3` and NOT copied)**:
  - Implemented from scratch in Kotlin (`TailsMetadataAnonymizer`) directly against PNG (ISO/IEC 15948 chunk framing: strip `eXIf`, `tEXt`, `zTXt`, `iTXt`, `tIME`; recompute CRC-32), JPEG (ITU T.81 marker framing: strip `APP1` EXIF/XMP, `APP13` IPTC, `COM`), ISOBMFF/MP4 (`udta`/`meta` atom elision), MP3 (`ID3v2` header + `ID3v1` 128-byte trailer stripping), PDF (`/Info` & `/Metadata` XMP stream redaction), and OOXML (`docProps/core.xml` & `docProps/app.xml` creator/company/timestamp scrubbing).

## 3. Local Hardware Origin Attack Defenses (`#26`–`#30`)
- **Vector #26 — `Deep-TEMPEST` (Fernandez et al., LADC 2024, `arXiv:2407.09717`)**:
  - Deep CNNs recover HDMI display text (`< 30%` CER) from unintended TMDS cable RF emanations; paper explicitly notes spatial low-amplitude pixel noise / background gradients degrade SDR CNN reconstruction while preserving human readability.
- **Vector #27 — `CVE-2023-45866` (`BlueDucky`, Marc Newlin 2023) & `BLESA` (`CVE-2020-9770`)**:
  - Unauthenticated Bluetooth Classic / BLE HID-over-GATT (`0x1812`) pairing & reconnection allows proximate attackers to inject keystrokes at DuckyScript speed (`< 8 ms` inter-keystroke interval) without user confirmation.
- **Vector #28 — Android Verified Boot 2.0 (`AVB0` `AvbVBMetaImageHeader`) Evil-Maid Boot Audit**:
  - 256-byte big-endian `AvbVBMetaImageHeader` (`magic = "AVB0"`, `algorithm_type`, `rollback_index` at offset `112`, `flags` at offset `120`) enforces verified boot integrity and monotonic anti-rollback protection.
- **Vector #29 — Ambient Light Sensor (`ALS`) Hand-Shadow PIN Inference**:
  - Unprivileged `Sensor.TYPE_LIGHT` streams leak finger trajectory across virtual keypads; neutralized by zeroing/freezing ALS delivery during IME/credential entry and quantizing ambient lux buckets outside focus.
- **Vector #30 — `Rowhammer` / `Half-Double` DRAM Disturbance (`tREFW` Activation Counter)**:
  - High-frequency LLC-evicting row activations within a `32–64 ms` DRAM refresh window flip adjacent capacitor cells; monitored via sliding-window cache-miss / row-conflict pressure advisories.

## 4. WebAssembly 3.0 Bulk Memory (`0xFC 0x08..0x0B`)
- **W3C WebAssembly Core Specification 3.0 (`2026-10-13`)**:
  - `0xFC 0x08` `memory.init dataidx memidx`, `0xFC 0x09` `data.drop dataidx`, `0xFC 0x0A` `memory.copy dst_mem src_mem` (overlap-safe `memmove` semantics with pre-write bounds trap), and `0xFC 0x0B` `memory.fill memidx`.
