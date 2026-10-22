# Fusion Shell — native Android development

Open **[`android/`](android/README.md)** in Android Studio. That is the Kotlin /
Jetpack Compose application, not a browser recreation.

The existing root `src/`, npm files and Vite configuration are legacy web work;
they are not the Android product, were not extended in this pass, and are not
part of the Android build.

**This is an incomplete implementation with limited API34 app and API34/API37 privacy emulator testing of the owner's
v2.0 + v2.1 specification.** No release-complete or performance claim is made.
See [build instructions](android/docs/BUILD.md),
[current verification status](android/STATUS.md), and the
[231-row requirement ledger](android/requirements.csv).

Historical: [prior R12 engineering and evidence](android/docs/R12-ENGINEERING.md).
[Current substantial testable subsets:50/50 (100%)](android/docs/MODULE-TESTABILITY.md).
**0/50 specification-accepted modules (0%); all50 unfinished at full scope.**

## Current R30 — 10 executed kernels, pure-logic round (host evidence only)

Branch **`arena/350b91e4-osb`**. Ten new host-executed kernels deepen 10/50 modules
(20%): RTSP request framing, video timecode math, VEVENT calendar import, Lamport
causal clock, GIF structure walk, settings patch scoping, POSIX tar headers,
half-stop zoom ladder, SMS segment math (counts only, no sending), density-tier
selection. No Android code authored this turn — no new components, permissions,
or UI (touch audit +0 delta; manifest scope pinned). Fresh research: Tails 7.15
reconfirmed, GrapheneOS Oct 2026 USB/port controls, Servo 0.5.0 stable embed API,
Tor VPN still beta.

**50/50 testable (100%); 0/50 full-spec accepted; no A-grade claim.** Executed:
98 kotlinc/JVM assertions, independent recomputation, touch-target + parse +
manifest-scope gates, 237/237 tool tests. [Results](android/docs/R30-RESULTS.md) ·
[Contract](android/docs/R30-PLAN.md) · [Privacy PRD](android/docs/R30-PRIVACY-PRD.md) ·
[Try without Studio](android/docs/BUILD.md).

## Prior R29 — executed kernels + USB-allowlist privacy set (host evidence only)

Branch **`arena/350b91e4-osb`**. Ten new host-executed kernels deepen 10/50 modules
(20%): share-manifest offers, storage quota ledger, alert token-bucket throttle,
file-list total ordering, shell alias table, WCAG contrast math, locale
negotiation, backup retention plans, DIDL-Lite items, CSV dialect sniffing with a
real-parser pipeline. One authored Android store (private USB allowlist; logic
executed on JVM against an in-memory fake, wiring unaudited) plus a
detection-only receiver wiring edit await CI/Studio compile plus emulator runs —
no behavior claim is made. A static touch-target audit covers 223 Button sites
(175 pre-existing findings queued; +0 delta; static only, not rendered proof);
a new manifest-scope gate pins the component/permission set. Fresh research:
Orbot 17.9.7 audit fixes, Android 17 Advanced Protection, passkey transfer APIs,
USB-C Authentication posture.

**50/50 testable (100%); 0/50 full-spec accepted; no A-grade claim.** Executed:
128 kotlinc/JVM assertions, independent recomputation, touch-target + parse +
manifest-scope gates, 227/227 tool tests. [Results](android/docs/R29-RESULTS.md) ·
[Contract](android/docs/R29-PLAN.md) · [Privacy PRD](android/docs/R29-PRIVACY-PRD.md) ·
[Try without Studio](android/docs/BUILD.md).

## Prior R28 — executed kernels + USB/consent privacy set (host evidence only)

Branch **`arena/350b91e4-osb`**. Ten new host-executed kernels deepen 10/50 modules
(20%): trash purge ledger, window snap zones, frecency ranking, slippy-map tile
math, podcast chapters, single-use consent receipts, RFC 6265 cookie jar, RRULE
recurrence, note front-matter, terminal scrollback. Two authored Android files
(detection-only USB watch receiver, consent dialog) await CI/Studio compile plus
emulator runs — no behavior claim is made. A static touch-target audit covers 223
Button sites (175 pre-existing findings queued for review; static only, not
rendered proof). Fresh research: Servo 0.6-LTS, Tor VPN/Arti 2.7 beta, Tails 7.15,
Android USB Protection + Intrusion Logging, Privacy Sandbox retired.

**50/50 testable (100%); 0/50 full-spec accepted; no A-grade claim.** Executed:
112 kotlinc/JVM assertions, independent recomputation, touch-target + parse gates,
217/217 tool tests. [Results](android/docs/R28-RESULTS.md) ·
[Contract](android/docs/R28-PLAN.md) · [Privacy PRD](android/docs/R28-PRIVACY-PRD.md) ·
[Try without Studio](android/docs/BUILD.md).

## Prior R27 — executed kernels + authored hub/dock wiring (host evidence only)

Branch **`arena/350b91e4-osb`**. Ten new host-executed kernels deepen 8/50 modules
(16%): tabletop arrange (snap/tidy/first-free), podcast RSS/Atom enclosures,
explicit HLS variant choice, 48-city offline gazetteer, hosts+ABP-subset content
blocker, CommonMark+GFM-table markdown, fail-closed firewall proposal, USB
inventory diff, 15-style M3 type scale, Vixie cron next-fire. Three Android edits
(BeautyDock tag alignment still unwired, player launch contract, MediaHub
stream-player button) are authored with 0 syntax errors and await CI/Studio
compile plus emulator runs — no behavior claim is made.

**50/50 testable (100%); 0/50 full-spec accepted; no A-grade claim.** Executed:
106 kotlinc/JVM assertions, independent recomputation, page-matrix + parse gates,
207/207 tool tests. [Results](android/docs/R27-RESULTS.md) ·
[Contract](android/docs/R27-PLAN.md) ·
[Try without Studio](android/docs/BUILD.md).

## Prior R26 — executed kernels + authored surfaces (host evidence only)

Branch **`arena/350b91e4-osb`**. Ten host-executed kernels deepened 6/50 modules
(12%): finite beauty budgets (27/27 AA pairs recomputed), amnesiac session policy,
tri-state Tor posture, USBGuard-subset policy evaluation, shuffled-pad channel
avoidance, bounded playlists, GPX import, safe-navigation policy, sandbox-job
contracts, TSV worksheets. Four Android surfaces (Media3 direct-URL player, optional
vector-map probe, Orbot handoff, phi-gap dock) are authored with 0 syntax errors and
await CI/Studio compile plus emulator runs — no playback/map/Tor/dock claim is made.

**50/50 testable (100%); 0/50 full-spec accepted; no A-grade claim.** Executed:
157 kotlinc/JVM assertions, independent recomputation, layout + parse gates.
[Results](android/docs/R26-RESULTS.md) ·
[Contract](android/docs/R26-PLAN.md) · [Privacy PRD](android/docs/R26-PRIVACY-PRD.md).

## Current R24 continuation — native tabletop, local media and offline atlas

Branch **`arena/a8ac5475-osb`**. Default slanted tabletop with draggable pins, directly focused walls,
piles and saved scene views; silver Art / light Business presets and font-aware overflow dock.
Actual local Android audio/video player and a real Natural Earth offline atlas are bounded additions;
YouTube/Spotify/SoundCloud links use disclosed **external handoff**, not embedded streaming.
Input/IME changes discard the separate privacy workspace until explicit empty rearm.

**49/50 substantial code+runnable-test subsets (98%);0/50 full-spec accepted.**
The new local host pipeline passes179 Python tests,439839 continuation assertions and65097 retained
R24 assertions. These are not native/physical acceptance. Native failures, targeted repairs, exact-source
results and actual screenshots are retained rather than presented as an all-green product.
A real pinned Servo Android AAR was inspected remotely; it was **not loaded or integrated**.

[Current results, counts and gaps](android/docs/R24C-RESULTS.md)
· [Laptop / no-Studio instructions](android/docs/BUILD.md)
· [A–J contract and alternatives](android/docs/R24C-PLAN.md)
· [Privacy / engine boundaries](android/docs/R24C-PRIVACY-AND-ENGINES.md)
· [Additional October engine frontier](android/docs/R24C-ADDITIONAL-ENGINE-FRONTIER.md).

## Historical R24 — native professional desktop and safer input/documents

**Office Blue / Studio Graphite**, independent **Business / Art** layouts in both **Settings and Control Panel**.
Art has a centered own-app dock with real mouse/keyboard-focus magnification, running dots and left window controls;
Business has a full-width bottom taskbar. No stock OS/third-party task authority is implied.

Exact native-tested **23e8868**: desktop/input**9/0**, PDF/boundary**27/0**, CSV**8/0**, privacy**18/0 on API34 AND API37**.
Main full workflow completed**133/0 SUCCESS**, including both build/JVM/contracts/lint jobs;
earlier aggregate bookkeeping lag was retained, then the actual completed receipt collected. All earlier failures retained.

Genuine isolated readonly PDF preview (<=2MiB/1024pages/320px) and real IME pinned Task/Notes actions are implemented.
**48/49 substantial testable subsets (97.96%),0/49 fully original-spec accepted.** Eleven existing production modules
deepened (22.45% inventory), not eleven completed modules. No full browser/Office/host-hardware anonymity/Tails claim.

[Current results and actual native screenshots](android/docs/R24-RESULTS.md)
· [Try on laptop / no-Studio scripts](android/docs/BUILD.md#r24-current-native-evaluation)
· [A–J engineering](android/docs/R24-ENGINEERING.md)
· [Privacy PRD](android/docs/R24-PRIVACY-PRD.md)
· [Engine alternatives](android/docs/R24-ENGINE-PORTFOLIO.md).

## Historical R19

[R19 engineering/evidence report](android/docs/R19-ENGINEERING.md): exact-snapshot edit/import undo,
confirmed append-only task merge, real system-picker FTS interchange, and Active/Archived literal
XLSX reports with aggregate-bounded multi-sheet writing. Task data is plaintext; reports are not backups.
**cce38cb: 95/95 full API34 cases and 4/4 focused cases passed**, plus both builds/JVM/contracts/lint.
**Latest 58e6566: 96/96 full API34 cases and 5/5 focused cases passed**, including the stronger
actual picker import/save/reopen/undo workflow; both builds/JVM/contracts/lint also passed. Earlier compiler failures and intermittent task-add failures remain recorded.
**539 host assertions + 430 reference/readback cases + 30 stress reports passed.**
No full Office, browser, physical/AX/legal or release-completion claim; subset count remains 47/48.
Try **Automation → enable with test data → Local tasks**; see [instructions and limits](android/docs/BUILD.md).

## Historical R18

[R18 engineering/evidence report](android/docs/R18-ENGINEERING.md): editable date-only tasks,
reversible archive/restore with protected revision capacity, versioned persistence and query/order;
plus caller-owned arithmetic requests through the real isolated worker.
**Latest d6f7393/run38831196589:85/86 API34 cases passed; one older task-add timeout remains.**
Its focused run passed2/2; all seven new R18 cases and both builds/JVM/contracts/lint passed.
Earlier9340662 passed86/86 full but failed0/2 focused. UI instability remains unresolved.
**906 new host assertions +1,200 reference cases;47/48 executable subsets,0/48 fully accepted.**
No complete desktop browser, Office/LOK, physical/AX/legal or production-release claim.
Try **Automation → enable with test data → Local tasks**. Archive retains data/capacity;
explicit edits upgrade legacy data to FTS2; no reminders are implemented.

## Historical R17

[R17 report](android/docs/R17-ENGINEERING.md): R16 experiments closed,855 actual measured ART rules,
and persistent local Tasks added. Latest verified native candidate **ddc7fec passed79/79 API34 cases**,
both builds/JVM/contracts/lint. An earlier Task UI timeout remains unexplained; successful reruns
are not a causal repair/stability proof. No full Office, browser, physical or release acceptance.
Try **Automation → enable with test data → Local tasks**; no reminders are implemented.

## Historical R12 continuation

**30/48 modules (62.50%) have substantial testable subsets;0/48 are fully specification-accepted.**
Five new bounded kernels and two newly executed Android inventory paths, plus strict UTF8/CSV
repairs and actual subtitle inspection. See the [R12 continuation engineering record](android/docs/R12B-ENGINEERING.md)
and [Android Studio instructions](android/docs/BUILD.md). Native results are revision-specific;
no whole browser, Office engine, full sandbox or complete productivity suite is claimed.
