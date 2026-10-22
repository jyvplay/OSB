# R34 alternative designs — browser, Office, sandbox, productivity, interchange

Working branch `arena/350b91e4-osb`. Each alternative names the gate it
attacks, the mechanism, the license/weight cost, and an HONEST verdict.
"Alternative" never means "claimed working": every row ends in either an
executed kernel, an authored surface, or an explicit refusal with the next
highest-information test. Search synthesis: `research/r34-primary.md`
(sections 1-5); unchanged R28 fetched primaries + R29–R33 synthesis stand.

## Browser (gate: no independent engine bundled; WebView-only boundary)

| # | Alternative | Mechanism | Verdict |
|---|---|---|---|
| B1 | GeckoView 158.x pinned AAR | Production engine, Mozilla Maven, minSdk 26 | VIABLE-BUT-UNACCEPTED (unchanged): weight, cadence, SBOM gates open. Next test: engine_intake + size receipt. |
| B2 | Servo 0.6.0 LTS embed (crates.io API + published AAR) | Memory-safe Rust engine, LTS (6-month cadence, 9-month support, security-only), upstream-published `servo-aarch64-android.aar` 37.9 MB | CANDIDATE (context new R34: monthlies carry breaking embed-API changes — toggle_sampling_profiler removed, MouseButton renamed — which hardens the LTS-as-base position; SiteDataManager::clear_site_data offers per-host deletion, softening the no-clearing phrasing). Still not accepted: no in-repo intake run, no license closure, no device run. Next test: engine_intake + embed smoke + LICENSE-REVIEW. |
| B3 | Ladybird | Independent engine, mobile explicitly not a current focus, alpha desktop-only | REFUSED (unchanged). Watch item. |
| B4 | System WebView + policy kernels | SafeNavigation (R26) + ContentBlocker (R27) + CookieJar (R28) + RobotsRules (R31) around the platform renderer | EXECUTED POLICY RING (unchanged). Renderer stays platform code. NOTE: SafeNavigation's strict canonicalize is exactly why the R34 CacheKey candidate was cut as already-owned. |
| B5 | Tor VPN (Arti) handoff | Beta Android VPN, F-Droid 1.9.1Beta build 242 (3 Sept), Arti 2.6.0, project-marked experimental | DOCUMENTED NEXT HANDOFF (reconfirmed R34: still beta/experimental, no new information). No routing code in-app. |
| B6 | Orbot 17.9.7 baseline | Launcher-intent handoff to maintained C-Tor app; component versions now named (obfs4 0.9.0, Snowflake 2.15.1; commit 0ea112a4d) | CONFIRMED R34 (still current, transports itemized). Device run still pending. |
| B7 | RobotsRules (R31) | robots.txt subset: groups, longest-prefix match, tie-Allow | EXECUTED (unchanged). Fetcher/crawler wiring future; product-token UA matching refused (exact + `*` only). |

Executed this turn: none in browser. The engine path stays baseline
hygiene (B2/B5/B6 with new context/confirmations), not a new claim.

## Office (gate: no engine; values-only XLSX/TSV ceiling)

| # | Alternative | Mechanism | Verdict |
|---|---|---|---|
| O1 | Collabora Online 26.04 MPL-2.0 | Self-hosted COOL server + handoff | VIABLE-BUT-UNACCEPTED (unchanged). Next test: local COOL container + handoff probe. |
| O2 | ONLYOFFICE Docs 10.0 | AGPLv3 core, proprietary Android app | REFUSED for bundling (unchanged), with license warning. |
| O3 | LibreOffice WASM/desktop bridge | Full engine off-device | REFUSED (unchanged): no Android path. |
| O4 | Grow the in-app ceiling honestly | CsvDialect (R29) + VEventParse (R30) + PdfTrailer/VaultManifest/TextOffsets (R31) + OfficeCapability (R32) + DiffHunks (R33) + OutlineParse (R34): delimiters, calendar import, PDF root-locating, vault metadata, editor offsets, bounded format-support matrix, unified-hunk rendering, note heading structure | EXECUTED (one new rung: ATX outline with fenced-code suppression; Setext refused): rendering, crypto, and Office engines stay refused. |

## Sandbox (gate: Privacy Sandbox retired; renderer isolation unaudited)

| # | Alternative | Mechanism | Verdict |
|---|---|---|---|
| S1 | Privacy Sandbox / SDK Runtime | Google's in-app sandbox | DEPRECATED PATH (unchanged). Do not adopt. |
| S2 | isolatedProcess + explicit IPC | Renderer/parser in an isolated process, explicit bridge | DOCUMENTED LADDER (unchanged; R33's expat DSA-6404-1 motivation stands, including for Fusion's own format readers): isolated PDF preview exists; job contracts model the protocol. Next test: hostile-input device run on the next parser. |
| S3 | OS-level controls | Android 16 USB Protection (now all Pixel A16+), Intrusion Logging (Chrome Incognito network events captured as sites-not-pages; NEITHER user NOR Google can delete early, even on account close), Android 17 Advanced Protection + Expert-features itemization (in flux), GrapheneOS A17 builds + Signature 27 (November US window, MTE + secure element) | RECOMMENDED DOCS (extended R34: Intrusion-Logging undeletability + USB coverage details — Fusion relies on nothing). Enable where available; Fusion duplicates none. |

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
| P15 | SearchQuery (R31) | Boolean query parse + eval | EXECUTED (unchanged). LocalIndex wiring future; scorer keeps its semantics. |
| P16 | TextOffsets (R31) | Editor offset math | EXECUTED (unchanged). Editor wiring future. |
| P17 | PhaseLedger (R31) | Phase-duration accounting | EXECUTED (unchanged). Instrumentation future; clock reads refused. |
| P18 | ICalDuration (R32) | RFC 5545 durations to seconds | EXECUTED (unchanged). Scheduler wiring future; calendrical months/years refused. |
| P19 | ArgSplit (R32) | Shell-ish argv splitter | EXECUTED (unchanged). Shell registry wiring future; expansion/globbing refused. |
| P20 | FractionApprox (R32) | Bounded-denominator rational approximation | EXECUTED (unchanged). Calc display wiring future; Double conversion is caller duty. |
| P21 | VectorClocks (R33) | Vector increment/merge/compare | EXECUTED (unchanged). Conflict-resolution wiring future; overflow refused. |
| P22 | DiffHunks (R33) | Unified-hunk rendering | EXECUTED (unchanged). Diff display wiring future; LineDiff computes. |
| P23 | GlobMatch (R33) | Glob subset over names | EXECUTED (unchanged). Search wiring future; filesystem recursion refused. |
| P24 | FuzzyScore (R33) | Greedy fuzzy scoring | EXECUTED (unchanged). Palette ranking future; alignment documented non-optimal. |
| P25 | AnsiSgr (R33) | SGR styling subset + strip | EXECUTED (unchanged). Terminal render future; cursor control refused. |
| P26 | StatSummary (R34) | Welford descriptive stats | EXECUTED (host). Stats display future; inference refused. |
| P27 | MimeGuess (R34) | Extension-to-MIME table | EXECUTED (host). Share-sheet wiring future; content sniffing refused. |
| P28 | OutlineParse (R34) | ATX heading outline | EXECUTED (host). Notes jump-list future; Setext refused. |
| P29 | BracketMatch (R34) | Lexical bracket mates | EXECUTED (host). Editor highlight future; syntax-awareness refused. |
| P30 | PipePlan (R34) | Quote-aware pipe split | EXECUTED (host). Shell runner future; execution refused. |
| P31 | VTodoParse (R34) | Single-VTODO import | EXECUTED (host). Task-sync wiring future; recurrence/reminders/sync refused. |
| P32 | SoapFault (R34) | SOAP fault + UPnPError parse | EXECUTED (host). Renderer error surfacing future; SOAP stack refused. |

## Media / capture / interchange (executed rungs)

| # | Alternative | Mechanism | Verdict |
|---|---|---|---|
| M1 | RtspMessage (R30) | Strict RTSP/1.0 request framing | EXECUTED (unchanged). Transport/session future, FairPlay untouched. |
| M2 | Timecode (R30) | ms to/from HH:MM:SS:FF | EXECUTED (unchanged). Recorder wiring future; drop-frame refused. |
| M3 | GifInfo (R30) | GIF structure walk | EXECUTED (unchanged). Decode/render future. |
| M4 | TarHeader (R30) | POSIX ustar header parse | EXECUTED (unchanged). Extraction refused. |
| M5 | ZoomLadder (R30) | Half-stop zoom math | EXECUTED (unchanged). Camera wiring future. |
| M6 | SmsSegments (R30) | Segment counts only | EXECUTED (unchanged). Sending refused. NOTE: its [BASIC]/[EXT] tables are exactly why the R34 GsmAlphabet candidate was cut as already-owned. |
| M7 | PatchScope (R30) | Patch admission scoping | EXECUTED (unchanged). Apply path future. |
| M8 | DensitySelect (R30) | Tier selection | EXECUTED (unchanged). Layout proof refused. |
| M9 | PdfTrailer (R31) | PDF root-locating reader | EXECUTED (unchanged). Object/render path future; rendering refused. |
| M10 | PsdResources (R31) | Image-resource block walk | EXECUTED (unchanged). Payload decode refused. |
| M11 | VaultManifest (R31) | Vault item manifest + digest | EXECUTED (unchanged). Envelope pairing future; crypto untouched. |
| M12 | CodecPreference (R31) | Encoder ranking | EXECUTED (unchanged). Encode proof refused. |
| M13 | CapabilityGate (R31) | Enablement decisions | EXECUTED (unchanged). Measurement stays with probes. |
| M14 | GeoUri (R32) | Strict RFC 5870 geo-URI subset + round-trip format | EXECUTED (unchanged). Maps/positioning wiring future; non-WGS84 refused. |
| M15 | CueSheetParser (R32) | CUE subset to absolute CD-frame cues | EXECUTED (unchanged). Chapter/decode path future; audio decode refused. |
| M16 | CastMediaMeta (R32) | Cast LOAD record validation | EXECUTED (unchanged). Transport future; escaping is the open interface. |
| M17 | BmpInspect (R33) | BMP header reader | EXECUTED (unchanged). Pixel decode refused. |
| M18 | GzipInspect (R33) | Gzip member-header reader | EXECUTED (unchanged). Decompression refused. |
| M19 | E164Format (R33) | E.164 grammar normalization | EXECUTED (unchanged). Dialer/SMS wiring future; numbering-plan guessing refused. |
| M20 | ChunkPlan (R33) | Transfer range planning | EXECUTED (unchanged). Transport/hash/reassembly future. |
| M21 | JpegSeg (R34) | JPEG marker walk + SOF dims | EXECUTED (host). Pixel decode refused; entropy never read. |
| M22 | ZlibHead (R34) | RFC 1950 header reader | EXECUTED (host). Decompression refused; FDICT refused. |

## Privacy plumbing

| # | Alternative | Mechanism | Verdict |
|---|---|---|---|
| D1 | LogScrubber (R31) | PII-shape redaction for diagnostics | EXECUTED (unchanged). Wiring to the "Share redacted report" control future; leak proof refused. |
| D2 | RetentionSweep (R32) | Keep/purge partition by per-kind max ages (caller clock) | EXECUTED (unchanged). Deletion execution is caller duty; unknown kinds refused. Erasure proof refused. |
| D3 | Pseudonym (R34) | HMAC-SHA256 keyed pseudonyms + context separation | EXECUTED (host). Correlation without raw IDs; key storage, rotation, and analytics wiring future. KEYED, not anonymous — anyone with the key re-derives the mapping. |

## Trust boundaries

| # | Alternative | Mechanism | Verdict |
|---|---|---|---|
| T1 | PinRules (R32) | Device-PIN strength policy with all violations reported | EXECUTED (unchanged). Policy only; storage, rate limiting, and the secure element are outside. |
| T2 | LockoutPolicy (R32) | Brute-force backoff over caller instants (3 free, doubling 30s, 10-saturation) | EXECUTED (unchanged). Attempt counting only; credential checking is outside. No decay window by design. |
| T3 | TotpCode (R33) | RFC 6238 TOTP compute + ±window verify over caller instants | EXECUTED (unchanged). Code math only; secret storage, provisioning, and the authenticator UI are outside. Timing side-channels outside. |

## Stopping rule for alternatives

An alternative graduates only with: (1) license closure in LICENSE-REVIEW,
(2) exact-candidate intake/size receipt, (3) CI compile, (4) disposable-device
behavior run. Anything short of that stays a table row, never a feature claim.
