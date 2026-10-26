# R31 alternative designs — browser, Office, sandbox, productivity, interchange

Working branch `arena/350b91e4-osb`. Each alternative names the gate it
attacks, the mechanism, the license/weight cost, and an HONEST verdict.
"Alternative" never means "claimed working": every row ends in either an
executed kernel, an authored surface, or an explicit refusal with the next
highest-information test. Search synthesis: `research/r31-primary.md`
(sections 1-4); unchanged R28 fetched primaries + R29/R30 synthesis stand.

## Browser (gate: no independent engine bundled; WebView-only boundary)

| # | Alternative | Mechanism | Verdict |
|---|---|---|---|
| B1 | GeckoView 158.x pinned AAR | Production engine, Mozilla Maven, minSdk 26 | VIABLE-BUT-UNACCEPTED (unchanged): weight, cadence, SBOM gates open. Next test: engine_intake + size receipt. |
| B2 | Servo 0.6.0 LTS embed (crates.io API + published AAR) | Memory-safe Rust engine, LTS (6-month cadence, 9-month support, security-only), upstream-published `servo-aarch64-android.aar` 37.9 MB | CANDIDATE (updated R31: first size receipt exists upstream). Still not accepted: no in-repo intake run, no license closure, no device run. Next test: engine_intake + Servoshell smoke + LICENSE-REVIEW. |
| B3 | Ladybird | Independent engine, mobile explicitly not a current focus, alpha desktop-only | REFUSED (unchanged). Watch item. |
| B4 | System WebView + policy kernels | SafeNavigation (R26) + ContentBlocker (R27) + CookieJar (R28) around the platform renderer | EXECUTED POLICY RING (extended R31: + RobotsRules crawler-policy rung below). Renderer stays platform code. |
| B5 | Tor VPN (Arti) handoff | Beta Android VPN, F-Droid 1.9.1Beta build 242 (3 Sept), Arti 2.6.0, project-marked experimental | DOCUMENTED NEXT HANDOFF (reconfirmed R31 with exact build). No routing code in-app. |
| B6 | Orbot 17.9.7 baseline | Launcher-intent handoff to maintained C-Tor app; 17.9.6 audit fixes enumerated (ORB-02-011 critical disable, ORB-02-004 consent bypass, traversal, hardcoded creds) | CONFIRMED R31 (still current, fixes itemized). Device run still pending. |
| B7 | RobotsRules (R31) | robots.txt subset: groups, longest-prefix match, tie-Allow | EXECUTED (host). Fetcher/crawler wiring future; product-token UA matching refused (exact + `*` only). |

Executed this turn: one policy rung (B7). The engine path stays baseline
hygiene (B2/B5/B6), not a new claim.

## Office (gate: no engine; values-only XLSX/TSV ceiling)

| # | Alternative | Mechanism | Verdict |
|---|---|---|---|
| O1 | Collabora Online 26.04 MPL-2.0 | Self-hosted COOL server + handoff | VIABLE-BUT-UNACCEPTED (unchanged). Next test: local COOL container + handoff probe. |
| O2 | ONLYOFFICE Docs 10.0 | AGPLv3 core, proprietary Android app | REFUSED for bundling (unchanged), with license warning. |
| O3 | LibreOffice WASM/desktop bridge | Full engine off-device | REFUSED (unchanged): no Android path. The lok module surveyed weakest a third round — still no honest kernel surface. |
| O4 | Grow the in-app ceiling honestly | CsvDialect (R29) + VEventParse (R30) + PdfTrailer/VaultManifest/TextOffsets (R31): delimiters, calendar import, PDF root-locating, vault metadata, editor offsets | EXECUTED (three new rungs): rendering, crypto, and Office engines stay refused. |

## Sandbox (gate: Privacy Sandbox retired; renderer isolation unaudited)

| # | Alternative | Mechanism | Verdict |
|---|---|---|---|
| S1 | Privacy Sandbox / SDK Runtime | Google's in-app sandbox | DEPRECATED PATH (unchanged). Do not adopt. |
| S2 | isolatedProcess + explicit IPC | Renderer/parser in an isolated process, explicit bridge | DOCUMENTED LADDER (unchanged): isolated PDF preview exists; job contracts model the protocol. Next test: hostile-input device run on the next parser. |
| S3 | OS-level controls | Android 16 USB Protection, Intrusion Logging, Android 17 Advanced Protection six-feature set, GrapheneOS 2026101600 (five USB-C port modes, dual-layer enforcement, duress PIN, 18h auto-reboot) | RECOMMENDED DOCS (extended R31: dual-layer detail, Motorola 7-year/Razr plans). Enable where available; Fusion duplicates none. Caveat new R31: USB Protection may move to optional "Expert features" — Fusion relies on nothing. |

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
| P13 | VEventParse (R30) | Single-VEVENT import | EXECUTED (unchanged). Calendar wiring future; recurrence/reminders/sync refused. |
| P14 | LamportClock (R30) | Causal clock for sync ordering | EXECUTED (unchanged). Channel/peer wiring future. |
| P15 | SearchQuery (R31) | Boolean query parse + eval | EXECUTED (host). LocalIndex wiring future; scorer keeps its semantics. |
| P16 | TextOffsets (R31) | Editor offset math | EXECUTED (host). Editor wiring future. |
| P17 | PhaseLedger (R31) | Phase-duration accounting | EXECUTED (host). Instrumentation future; clock reads refused. |

## Media / capture / interchange (executed rungs)

| # | Alternative | Mechanism | Verdict |
|---|---|---|---|
| M1 | RtspMessage (R30) | Strict RTSP/1.0 request framing | EXECUTED (unchanged). Transport/session future, FairPlay untouched. |
| M2 | Timecode (R30) | ms to/from HH:MM:SS:FF | EXECUTED (unchanged). Recorder wiring future; drop-frame refused. |
| M3 | GifInfo (R30) | GIF structure walk | EXECUTED (unchanged). Decode/render future. |
| M4 | TarHeader (R30) | POSIX ustar header parse | EXECUTED (unchanged). Extraction refused. |
| M5 | ZoomLadder (R30) | Half-stop zoom math | EXECUTED (unchanged). Camera wiring future. |
| M6 | SmsSegments (R30) | Segment counts only | EXECUTED (unchanged). Sending refused. |
| M7 | PatchScope (R30) | Patch admission scoping | EXECUTED (unchanged). Apply path future. |
| M8 | DensitySelect (R30) | Tier selection | EXECUTED (unchanged). Layout proof refused. |
| M9 | PdfTrailer (R31) | PDF root-locating reader | EXECUTED (host). Object/render path future; rendering refused. |
| M10 | PsdResources (R31) | Image-resource block walk | EXECUTED (host). Payload decode refused. |
| M11 | VaultManifest (R31) | Vault item manifest + digest | EXECUTED (host). Envelope pairing future; crypto untouched. |
| M12 | CodecPreference (R31) | Encoder ranking | EXECUTED (host). Encode proof refused. |
| M13 | CapabilityGate (R31) | Enablement decisions | EXECUTED (host). Measurement stays with probes. |

## Privacy plumbing (new R31 section)

| # | Alternative | Mechanism | Verdict |
|---|---|---|---|
| D1 | LogScrubber (R31) | PII-shape redaction for diagnostics | EXECUTED (host). Wiring to the "Share redacted report" control future; leak proof refused. |

## Stopping rule for alternatives

An alternative graduates only with: (1) license closure in LICENSE-REVIEW,
(2) exact-candidate intake/size receipt, (3) CI compile, (4) disposable-device
behavior run. Anything short of that stays a table row, never a feature claim.
