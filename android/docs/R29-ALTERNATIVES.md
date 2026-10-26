# R29 alternative designs — browser, Office, sandbox, productivity

Working branch `arena/350b91e4-osb`. Each alternative names the gate it
attacks, the mechanism, the license/weight cost, and an HONEST verdict.
"Alternative" never means "claimed working": every row ends in either an
executed kernel, an authored surface, or an explicit refusal with the next
highest-information test. Search synthesis: `research/r29-primary.md`
(sections 1-4); unchanged R28 fetched primaries still stand.

## Browser (gate: no independent engine bundled; WebView-only boundary)

| # | Alternative | Mechanism | Verdict |
|---|---|---|---|
| B1 | GeckoView 158.x pinned AAR | Production engine, Mozilla Maven, minSdk 26 | VIABLE-BUT-UNACCEPTED (unchanged): weight, cadence, SBOM gates open. Next test: engine_intake + size receipt. |
| B2 | Servo 0.6-LTS embed (WebView API) | Memory-safe Rust engine, LTS embedding API, Android v10+ | CANDIDATE (unchanged): bounded reader surface only. Next test: Servoshell smoke + LTS API review. |
| B3 | Ladybird | Independent engine, 2028 stable target | REFUSED (unchanged): pre-alpha, no Android port. Watch item. |
| B4 | System WebView + policy kernels | SafeNavigation (R26) + ContentBlocker (R27) + CookieJar (R28) around the platform renderer | EXECUTED POLICY RING (unchanged, no R29 addition): narrows the boundary renderer; renderer stays platform code. |
| B5 | Tor VPN (Arti) handoff | Beta Android VPN, project-marked experimental | DOCUMENTED NEXT HANDOFF (reconfirmed R29: no stable-release signal in fresh research). No routing code in-app. |
| B6 | Orbot 17.9.7 baseline | Launcher-intent handoff to maintained C-Tor app | CONFIRMED R29: handoff uses `getLaunchIntentForPackage`, unaffected by the 17.9.6 `StartTorReceiver` deletion; 17.9.6 audit fixes (ORB-02) noted. Device run still pending. |

Executed this turn: none on the engine path (deliberately). The R29 browser
work is baseline hygiene (B6), not a new claim.

## Office (gate: no engine; values-only XLSX/TSV ceiling)

| # | Alternative | Mechanism | Verdict |
|---|---|---|---|
| O1 | Collabora Online 26.04 MPL-2.0 | Self-hosted COOL server + handoff | VIABLE-BUT-UNACCEPTED (unchanged). Next test: local COOL container + handoff probe. |
| O2 | ONLYOFFICE Docs 10.0 | AGPLv3 core, proprietary Android app | REFUSED for bundling (unchanged), with license warning. |
| O3 | LibreOffice WASM/desktop bridge | Full engine off-device | REFUSED (unchanged): no Android path. |
| O4 | Grow the in-app ceiling honestly | CsvDialect (R29): sniff `;`/tab/`|` + translate into the real CsvTable grammar | EXECUTED: real-world delimiter ingestion without pretending to be Office. Formula/style/macro support stays refused. |

## Sandbox (gate: Privacy Sandbox retired; renderer isolation unaudited)

| # | Alternative | Mechanism | Verdict |
|---|---|---|---|
| S1 | Privacy Sandbox / SDK Runtime | Google's in-app sandbox | DEPRECATED PATH (unchanged). Do not adopt. |
| S2 | isolatedProcess + explicit IPC | Renderer/parser in an isolated process, explicit bridge | DOCUMENTED LADDER (unchanged): isolated PDF preview exists; job contracts model the protocol. Next test: hostile-input device run on the next parser. |
| S3 | OS-level controls | Android 16 USB Protection, Intrusion Logging, GrapheneOS port control, Android 17 Advanced Protection | RECOMMENDED DOCS (extended R29 per fresh research): enable where available; Fusion duplicates none of them. |

## Productivity (gate: recurrence/reminders/history refused in prior limits)

| # | Alternative | Mechanism | Verdict |
|---|---|---|---|
| P1 | Recurrence kernel (R28) | RRULE-subset expansion | EXECUTED (unchanged). Scheduler/UI wiring future. |
| P2 | FrontMatter kernel (R28) | Typed note headers | EXECUTED (unchanged). Notes integration future. |
| P3 | FrecencyLedger (R28) | Decayed ranking for Find | EXECUTED (unchanged). Palette wiring future. |
| P4 | Server-backed sync/accounts | accounts, push, merge servers | REFUSED (unchanged): contradicts offline/consent posture. |
| P5 | AlertThrottle (R29) | Per-rule token bucket | EXECUTED (host). Alert-engine wiring future, needs device run. |
| P6 | AliasTable (R29) | Bounded shell aliases, cycle-safe | EXECUTED (host). Shell registry wiring future. |
| P7 | FileSort (R29) | Total file-list ordering | EXECUTED (host). Files list wiring future. |
| P8 | RetentionPlan (R29) | GFS snapshot selection | EXECUTED (host). Backup-engine wiring future. |
| P9 | ShareManifest (R29) | Validated Beam send offer | EXECUTED (host). Beam transport/consent future. |
| P10 | DidlLite (R29) | DIDL-Lite item render subset | EXECUTED (host). Browse/transport fetch future. |
| P11 | LocaleMatch (R29) | Locale negotiation | EXECUTED (host). Settings/UX wiring future. |
| P12 | QuotaLedger (R29) | Per-scope byte quotas | EXECUTED (host). Storage pairing is caller duty. |

## Stopping rule for alternatives

An alternative graduates only with: (1) license closure in LICENSE-REVIEW,
(2) exact-candidate intake/size receipt, (3) CI compile, (4) disposable-device
behavior run. Anything short of that stays a table row, never a feature claim.
