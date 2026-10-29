# R53 Architectural Alternatives & Blocker Bypass Analysis

## 1. Bypassing External Binary Dependencies (`FFmpeg`, `ExifTool`, `Poppler`, `warcio`, `Tor C Equi-X`)
| Capability | Conventional Heavy/Copyleft Blocker | Clean-Room Permissive (`Apache-2.0`) R53 Bypass |
|---|---|---|
| Ephemeral/Multi-asset Web Capture | Headless Chromium / Python `warcio` daemon unavailable inside lightweight Android sandbox | Pure-JVM `RecallTier5WarcIso28500Archiver` emitting byte-exact ISO 28500:2017 `WARC/1.1` (`warcinfo`, `response`, `resource`, `metadata`) + `.cdxj` replay index |
| Live WebRTC Media Recording | Browser `MediaRecorder` often blocked or re-encodes lossily | Pure-JVM `RecallTier25WebRtcRtpDepacketizer` (RFC 3550 + RFC 7587 48 kHz Opus RTP jitter buffer + duplicate suppression + gap concealment) |
| Tails `MAT2` Metadata Anonymizer | Upstream `mat2` is `LGPLv3` and shells out to `exiftool`, `poppler`, `ffmpeg`, `gdk-pixbuf` | Pure-JVM `TailsMetadataAnonymizer` surgically stripping metadata chunks/atoms/streams across PNG, JPEG, MP4, MP3, PDF, and OOXML with zero external binaries |
| Tor Multipath Throughput | Single-circuit Tor latency bottlenecks media/office sync | Clean-room Tor Proposal 329 `TorConfluxMultipathScheduler` (`LOWRTT_TOR` + `BLEST_TOR` + disjoint Guard/Middle check + 64-bit sequence reassembly) |
| Submenu Right-Click & Z-Stack Fan | Full-width modal sheets or full-height notification drawers obscuring desktop canvas | `ContextMenuSubmenuCascadeGeometry` (`180..280dp` parent + `168..240dp` submenu with collision flip) + `UndoDeckInteractiveFanController` (`8.4%` compact bottom-right Z-stack, on-demand fan) |
