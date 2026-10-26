# R30 alternative designs — browser, Office, sandbox, productivity, interchange

Working branch `arena/350b91e4-osb`. Each alternative names the gate it
attacks, the mechanism, the license/weight cost, and an HONEST verdict.
"Alternative" never means "claimed working": every row ends in either an
executed kernel, an authored surface, or an explicit refusal with the next
highest-information test. Search synthesis: `research/r30-primary.md`
(sections 1-4); unchanged R28 fetched primaries + R29 synthesis still stand.

## Browser (gate: no independent engine bundled; WebView-only boundary)

| # | Alternative | Mechanism | Verdict |
|---|---|---|---|
| B1 | GeckoView 158.x pinned AAR | Production engine, Mozilla Maven, minSdk 26 | VIABLE-BUT-UNACCEPTED (unchanged): weight, cadence, SBOM gates open. Next test: engine_intake + size receipt. |
| B2 | Servo 0.5.0 stable embed (crates.io API) | Memory-safe Rust engine, stable release 31 Aug 2026, Android v10+ (~91% market), WPT ~78% | CANDIDATE (updated R30 from 0.6-LTS watch): bounded reader surface only. Stability + embed API are new facts, not new acceptance. Next test: engine_intake + Servoshell smoke + size receipt. |
| B3 | Ladybird | Independent engine, mobile explicitly not a current focus, alpha desktop-only | REFUSED (strengthened R30): pre-alpha, no Android port, 2027/2028 targets unverified. Watch item. |
| B4 | System WebView + policy kernels | SafeNavigation (R26) + ContentBlocker (R27) + CookieJar (R28) around the platform renderer | EXECUTED POLICY RING (unchanged, no R30 addition): narrows the boundary renderer; renderer stays platform code. |
| B5 | Tor VPN (Arti) handoff | Beta Android VPN, Beta 9.1 (1 Sept), Arti 2.6.0, project-marked experimental, identifiers can leak | DOCUMENTED NEXT HANDOFF (reconfirmed R30: still explicitly not for high-risk users). No routing code in-app. |
| B6 | Orbot 17.9.7 baseline | Launcher-intent handoff to maintained C-Tor app | CONFIRMED R30 (still current): handoff unaffected; device run still pending. |

Executed this turn: none on the engine path (deliberately). The R30 browser
work is baseline hygiene (B2/B5/B6), not a new claim.

## Office (gate: no engine; values-only XLSX/TSV ceiling)

| # | Alternative | Mechanism | Verdict |
|---|---|---|---|
| O1 | Collabora Online 26.04 MPL-2.0 | Self-hosted COOL server + handoff | VIABLE-BUT-UNACCEPTED (unchanged). Next test: local COOL container + handoff probe. |
| O2 | ONLYOFFICE Docs 10.0 | AGPLv3 core, proprietary Android app | REFUSED for bundling (unchanged), with license warning. |
| O3 | LibreOffice WASM/desktop bridge | Full engine off-device | REFUSED (unchanged): no Android path. |
| O4 | Grow the in-app ceiling honestly | CsvDialect (R29, unchanged) + VEventParse (R30, P13): real-world delimiter ingestion and single-event calendar import without pretending to be Office | EXECUTED (one new rung): formula/style/macro support and recurrence/sync stay refused. |

## Sandbox (gate: Privacy Sandbox retired; renderer isolation unaudited)

| # | Alternative | Mechanism | Verdict |
|---|---|---|---|
| S1 | Privacy Sandbox / SDK Runtime | Google's in-app sandbox | DEPRECATED PATH (unchanged). Do not adopt. |
| S2 | isolatedProcess + explicit IPC | Renderer/parser in an isolated process, explicit bridge | DOCUMENTED LADDER (unchanged): isolated PDF preview exists; job contracts model the protocol. Next test: hostile-input device run on the next parser. |
| S3 | OS-level controls | Android 16 USB Protection, Intrusion Logging, Android 17 Advanced Protection, GrapheneOS 2026101600 (five USB-C port modes with charging-only-when-locked default, duress PIN, 18h auto-reboot) | RECOMMENDED DOCS (extended R30 per fresh research): enable where available; Fusion duplicates none of them. |

## Productivity (gate: recurrence/reminders/history refused in prior limits)

| # | Alternative | Mechanism | Verdict |
|---|---|---|---|
| P1 | Recurrence kernel (R28) | RRULE-subset expansion | EXECUTED (unchanged). Scheduler/UI wiring future. |
| P2 | FrontMatter kernel (R28) | Typed note headers | EXECUTED (unchanged). Notes integration future. |
| P3 | FrecencyLedger (R28) | Decayed ranking for Find | EXECUTED (unchanged). Palette wiring future. |
| P4 | Server-backed sync/accounts | accounts, push, merge servers | REFUSED (unchanged): contradicts offline/consent posture. |
| P5 | AlertThrottle (R29) | Per-rule token bucket | EXECUTED (unchanged). Alert-engine wiring future, needs device run. |
| P6 | AliasTable (R29) | Bounded shell aliases, cycle-safe | EXECUTED (unchanged). Shell registry wiring future. |
| P7 | FileSort (R29) | Total file-list ordering | EXECUTED (unchanged). Files list wiring future. |
| P8 | RetentionPlan (R29) | GFS snapshot selection | EXECUTED (unchanged). Backup-engine wiring future. |
| P9 | ShareManifest (R29) | Validated Beam send offer | EXECUTED (unchanged). Beam transport/consent future. |
| P10 | DidlLite (R29) | DIDL-Lite item render subset | EXECUTED (unchanged). Browse/transport fetch future. |
| P11 | LocaleMatch (R29) | Locale negotiation | EXECUTED (unchanged). Settings/UX wiring future. |
| P12 | QuotaLedger (R29) | Per-scope byte quotas | EXECUTED (unchanged). Storage pairing is caller duty. |
| P13 | VEventParse (R30) | Single-VEVENT import: unfold, unescape, UTC instants, DTEND guard | EXECUTED (host). Calendar/import wiring future; recurrence, reminders, timezones, sync stay refused. |
| P14 | LamportClock (R30) | Causal clock for future sync ordering | EXECUTED (host). Channel/peer wiring future; wall-clock sync refused. |

## Media / capture / interchange (R30 executed rungs)

| # | Alternative | Mechanism | Verdict |
|---|---|---|---|
| M1 | RtspMessage (R30) | Strict RTSP/1.0 request framing for the AirPlay SETUP/ANNOUNCE path | EXECUTED (host). Narrows the cast boundary; transport/session/render future, FairPlay untouched. |
| M2 | Timecode (R30) | ms to/from HH:MM:SS:FF display math | EXECUTED (host). Recorder/encoder wiring future; drop-frame refused. |
| M3 | GifInfo (R30) | GIF structure walk: screen size + frame count | EXECUTED (host). Decode/render future; pixel decoding refused. |
| M4 | TarHeader (R30) | POSIX ustar header parse + verify | EXECUTED (host). Extractor/creator future; extraction refused. |
| M5 | ZoomLadder (R30) | Half-stop digital-zoom ladder math | EXECUTED (host). Camera/UI wiring future; sensor proof refused. |
| M6 | SmsSegments (R30) | GSM-7/UCS-2 classification + segment counts | EXECUTED (host, counts only). Sending refused; no telephony permission. |
| M7 | PatchScope (R30) | Settings-patch admission scoping | EXECUTED (host). Apply-path wiring future; writes refused. |
| M8 | DensitySelect (R30) | Density-tier selection vs the reviewed ladder | EXECUTED (host). Layout/render proof refused. |

## Stopping rule for alternatives

An alternative graduates only with: (1) license closure in LICENSE-REVIEW,
(2) exact-candidate intake/size receipt, (3) CI compile, (4) disposable-device
behavior run. Anything short of that stays a table row, never a feature claim.
