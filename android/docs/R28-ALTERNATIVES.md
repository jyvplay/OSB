# R28 alternative designs — browser, Office, sandbox, productivity

Working branch `arena/350b91e4-osb`. Each alternative names the gate it
attacks, the mechanism, the license/weight cost, and an HONEST verdict.
"Alternative" never means "claimed working": every row ends in either an
executed kernel, an authored surface, or an explicit refusal with the next
highest-information test. Primary sources: `research/r28-primary.md`.

## Browser (gate: no independent engine bundled; WebView-only boundary)

| # | Alternative | Mechanism | Verdict |
|---|---|---|---|
| B1 | GeckoView 158.x pinned AAR | Production engine, Mozilla Maven, minSdk 26 | VIABLE-BUT-UNACCEPTED: engine weight (~100MB+), update cadence, SBOM/review gates open. Next test: engine_intake inspection + size receipt. |
| B2 | Servo 0.6-LTS embed (WebView API) | Memory-safe Rust engine, crates.io embedding API, Android v10+ | CANDIDATE (upgraded R28): bounded reader surface only. Next test: Servoshell APK smoke on disposable emulator + LTS API review. |
| B3 | Ladybird | Independent engine, 2028 stable target | REFUSED (this turn): pre-alpha, no Android port, external PRs closed. Watch item. |
| B4 | System WebView + policy kernels | SafeNavigation (R26) + ContentBlocker (R27) + CookieJar (R28, executed) around the platform renderer | EXECUTED POLICY RING: narrows what the boundary renderer can do, but the renderer stays platform code. No engine claim. |
| B5 | Tor VPN (Arti) handoff | Beta Android VPN on Arti 2.7, F-Droid, Cure53-audited, project-marked experimental | DOCUMENTED NEXT HANDOFF after Orbot; experimental label inherited. No routing code in-app. |

Executed this turn: CookieJar (RFC 6265 subset, 512-cap jar, Secure/HttpOnly/
SameSite, expiry eviction) — the policy ring around B4 grows; the engine gap
(B1/B2 intake + device runs) is unchanged and explicitly open.

## Office (gate: no engine; values-only XLSX/TSV ceiling)

| # | Alternative | Mechanism | Verdict |
|---|---|---|---|
| O1 | Collabora Online 26.04 MPL-2.0 | Self-hosted COOL server + Android app handoff / WOPI-style integration | VIABLE-BUT-UNACCEPTED: needs a server the user trusts; collaboration truthfully requires one. Next test: local COOL container + handoff probe. |
| O2 | ONLYOFFICE Docs 10.0 | AGPLv3 core | REFUSED for bundling: Android app is PROPRIETARY (9.4.1); server coupling is AGPL. External-handoff mention only, with license warning. |
| O3 | LibreOffice WASM/desktop bridge | Full engine off-device | REFUSED (unchanged): desktop-WASM only, no Android path. |
| O4 | Grow the in-app ceiling honestly | TSV worksheets (R26), Recurrence/FrontMatter (R28) for tasks/notes | EXECUTED: plaintext-first productivity without pretending to be Office. Formula/style/macro support stays refused. |

## Sandbox (gate: Privacy Sandbox retired; renderer isolation unaudited)

| # | Alternative | Mechanism | Verdict |
|---|---|---|---|
| S1 | Privacy Sandbox / SDK Runtime | Google's in-app sandbox | DEPRECATED PATH (retired Oct 2025 for Chrome+Android). Do not adopt. |
| S2 | isolatedProcess + explicit IPC | Renderer/parser in an isolated UID-less process, AIDL/ContentProvider bridge, no permissions | DOCUMENTED LADDER, partially executed: isolated PDF preview exists; sandbox-job contracts (R26) model the job protocol. Next test: extend the isolated boundary to the subtitle/XLSX parsers with a hostile-input device run. |
| S3 | OS-level controls | Android 16 USB Protection, Intrusion Logging, GrapheneOS port control | RECOMMENDED DOCS: enable where available; Fusion does not duplicate them. |

## Productivity (gate: recurrence/reminders/history refused in prior limits)

| # | Alternative | Mechanism | Verdict |
|---|---|---|---|
| P1 | Recurrence kernel (R28) | RRULE-subset expansion, COUNT xor UNTIL, RFC skip semantics | EXECUTED (host). UI wiring (TaskPanel recurrence display) is future authored work needing a device run. |
| P2 | FrontMatter kernel (R28) | Typed note headers for tasks/notes metadata | EXECUTED (host). Notes read/write integration future. |
| P3 | FrecencyLedger (R28) | Decayed ranking for the Find palette | EXECUTED (host). Palette wiring future. |
| P4 | Server-backed sync/accounts | accounts, push, merge servers | REFUSED: contradicts the offline/consent posture; no server code exists. |

## Stopping rule for alternatives

An alternative graduates only with: (1) license closure in LICENSE-REVIEW,
(2) exact-candidate Intake/size receipt, (3) CI compile, (4) disposable-device
behavior run. Anything short of that stays a table row, never a feature claim.
