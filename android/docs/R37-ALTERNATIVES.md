# R37 alternative designs — browser, Office, sandbox, productivity, input path

Working branch `arena/350b91e4-osb`. Evaluation date 2026-10-23. Contract:
[R37-PLAN](R37-PLAN.md) · Results: [R37-RESULTS](R37-RESULTS.md).

Each alternative names a **mechanism**, not a wish. "EXECUTED" means a kernel or
tool with a green host run this round or a named earlier round; "DOCUMENTED"
means prose only. Nothing here is accepted as full scope.

## Browser (gate: no independent engine bundled; WebView-only boundary)

| # | Alternative | Mechanism | Verdict R37 |
|---|---|---|---|
| B1 | GeckoView 158.x pinned AAR | Production engine via Mozilla Maven | VIABLE-BUT-UNACCEPTED (unchanged). Weight, cadence, SBOM gates open. Next test: engine_intake + size receipt. |
| B2 | Servo 0.7.0 LTS embed | Memory-safe Rust engine, upstream `servo-aarch64-android.aar` | CANDIDATE (R37 research: still no 0.8.0; no new downstream embed signal beyond one experimental Linux web-view PR). Not accepted: no in-repo intake, no license closure, no device run. |
| B3 | Ladybird | Independent engine, desktop alpha | REFUSED (unchanged). |
| B4 | System WebView + policy ring | SafeNavigation + ContentBlocker + CookieJar + RobotsRules around the platform renderer | EXECUTED POLICY RING (unchanged). Renderer stays platform code. |
| **B8** | **Local-first reading pipeline** (NEW R37) | Invert the dependency: fetch → bounded readability extraction → offline text, and only hand an interactive page to the platform WebView when interaction is unavoidable. Policy ring still gates URLs, robots and cookies. | DESIGN (no new code this round). The engine gate does not block a *reading* product; it blocks a *rendering* product. Next test: extraction fixture set + handoff probe. |
| **B9** | **Multi-source result fusion** (NEW R37) | `RankFuse` merges independently ranked lists (history, index hits, bookmarks) with reciprocal-rank fusion and stable ties, so search quality does not depend on an engine's ranking. | EXECUTED (R37, `run_r37_contracts`, 18 assertions). Wiring to the search UI is future. |
| B5 | Tor VPN (Arti) handoff | F-Droid beta, Arti 2.6.0 | DOCUMENTED (unchanged: still beta/experimental). |
| B6 | Orbot 17.9.7 handoff | Launcher intent to maintained C-Tor app | CONFIRMED (R37 research: 17.9.7 still latest; no 17.9.8/17.10). Device run pending. |
| **B10** | **Tracking-parameter scrub on share** (NEW R37) | `UtmStrip` removes known `utm_*`/click-id parameters before a link leaves the app, reducing cross-site correlation of shares. | EXECUTED (R37, 19 assertions). Denylist only: a cleaned URL is "less tracked", never "untracked". |

## Office (gate: no engine; values-only OOXML/CSV ceiling)

| # | Alternative | Mechanism | Verdict R37 |
|---|---|---|---|
| O1 | Collabora Online 26.04 (MPL-2.0) | Self-hosted server + handoff | VIABLE-BUT-UNACCEPTED (unchanged). Next test: local container + handoff probe. |
| O2 | ONLYOFFICE Docs 10.0 | AGPLv3 core, proprietary Android app | REFUSED for bundling (unchanged). |
| O3 | LibreOffice WASM/desktop bridge | Engine off-device | REFUSED (unchanged): no Android path. |
| O4 | Grow the in-app ceiling honestly | CsvDialect, VEventParse, PdfTrailer, VaultManifest, TextOffsets, OfficeCapability, DiffHunks, OutlineParse, WordDiff | EXECUTED (unchanged). |
| **O5** | **Container-first archive inspection** (NEW R37) | Treat `.xz`/`.mp4` as containers to be *identified and described*, never decoded: `XzHead` reads the 12-byte stream header and its integrity Check; `Mp4Box` walks top-level boxes. Users get "what is this and is the header coherent" without an engine. | EXECUTED (R37, 17 + 16 assertions, real fixtures). Decompression/demux refused. |
| **O6** | **Human-unit presentation** (NEW R37) | `SizeBucket` renders IEC sizes with exact unit boundaries so lists never lie about magnitude; `SlugHead` gives stable heading fragments so exported notes keep addressable structure. | EXECUTED (R37, 22 + 18 assertions). |

## Sandbox (gate: parser isolation unaudited; Privacy Sandbox retired)

| # | Alternative | Mechanism | Verdict R37 |
|---|---|---|---|
| S1 | Privacy Sandbox / SDK Runtime | Google's in-app sandbox | DEPRECATED PATH (unchanged). |
| S2 | isolatedProcess + explicit IPC | Parser in an isolated process behind a job contract | DOCUMENTED LADDER (unchanged). Next test: hostile-input device run on the next parser. |
| S3 | OS-level controls | Android 17 Advanced Protection six; GrapheneOS A17 + Signature 27 (November window) | RECOMMENDED DOCS (reconfirmed R37: six stand; Signature 27 still November, no exact date). Fusion duplicates none. |
| **S4** | **Input-path audit instead of input trust** (NEW R37) | `tools/hid_topology.py` parses a stored `lsusb -v`/`-t` transcript, counts boot keyboards, measures HID depth and flags dual-keyboard / hub-in-path / unlisted-keyboard shapes. Pairs with the existing read-only USB authorization posture tool and the on-screen secret pad. | EXECUTED (R37, 5 python tests + 1 lab run on a labelled *format* fixture). It audits what the bus reports; it cannot see a non-enumerating implant and says so in its own output. |

## Productivity / continuity (gate: recurrence, reminders, history)

| # | Alternative | Mechanism | Verdict R37 |
|---|---|---|---|
| P1 | Recurrence kernel | RRULE-subset expansion | EXECUTED (unchanged). Scheduler wiring future. |
| P2 | FrontMatter kernel | Typed note headers | EXECUTED (unchanged). |
| P3 | FrecencyLedger | Decayed ranking | EXECUTED (unchanged). |
| **P4** | **Task roll-up over parsed VTODOs** (NEW R37) | `TaskTally` counts RFC 5545 statuses into five buckets, keeping `unspecified` separate so un-statused tasks are never silently filed as "needs action". | EXECUTED (R37, 14 assertions). |
| **P5** | **Paste that respects scope** (NEW R37) | `PasteWrap` re-indents a pasted block onto the current depth, collapsing blank lines and refusing ragged indents instead of guessing. | EXECUTED (R37, 20 assertions). |
| **P6** | **Terminal honesty** (NEW R37) | `ExitCode` names shell/sysexits/`128+N` statuses; `OscLink` parses OSC 8 links so a hyperlink is *displayed as text with an address*, never auto-fetched. | EXECUTED (R37, 25 + 20 assertions). |
| **P7** | **Media and map legibility** (NEW R37) | `FovMath` converts focal length + sensor dimension to degrees; `PolyLine` decodes/encodes 1e-5 paths so tracks can be drawn without a routing service. | EXECUTED (R37, 18 + 20 assertions). |

## What these alternatives deliberately do NOT do

They do not bundle an engine, do not decode Office or video payloads, do not
claim sandbox isolation without a device run, and do not claim that any input
audit detects a keylogger. Those four are the gates; the alternatives route
around them by changing the product's promise rather than by faking the engine.
