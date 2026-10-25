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

## Current R44 — 24 executed kernels across 23 modules; bridge spans R37–R44

Branch **`arena/350b91e4-osb`**. Fourth consecutive sandbox-reset recovery, then
twenty-four new host-executed kernels deepening 23/50 modules (46%, tying the
record): hidden-run grid arithmetic, email quote-depth hygiene, honest
go-to-line (clamping reported, never silent), PDF indirect-reference canon
(65535 free-list ceiling refused), SRI strongest-wins intake that never
hashes, the full Debian version total order (tilde laws executed in TWO
independent implementations), ISSN mod-11 with the computed X case, UPC-E
zero-suppression (all four branches), HOME-collapse with the /homework bug
refused by construction, banded Levenshtein with a 125-case agreement sweep,
key-naming lint, readline history semantics incl. the ignorespace privacy
convention, PCM byte math, subject threading normalization, EXIF GPS
presence math (conversion ≠ stripping, stated), display diagonal math that
refuses anisotropic averaging, snapshot naming with the lex==chrono law
executed, fast-scroll buckets (Latin-only declared), EXIF orientation with
its inverse involution, Accept-Language (fingerprint vector named in scope),
inline tags, frame-budget ceil law, dot-hidden vs .nomedia kept distinct,
and human-speakable session codes that explicitly do NOT claim a checksum.

**50/50 testable (100%); 0/50 full-spec accepted; no A-grade claim —
[R44-ALTERNATIVES](android/docs/R44-ALTERNATIVES.md) answers the Class-A
question honestly again.** Executed: **378 kotlinc/JVM assertions (post-repair; the CI bridge caught and we cut a name-colliding kernel — disclosed in R44-RESULTS)** across
24 kernels, 24 mirrors, lab PASS first full run with seven prior rounds'
regressions inside; bridge registers `:testing:r37Contracts` …
`:testing:r44Contracts` (161 kernels / 2777 assertions once green).
[Results](android/docs/R44-RESULTS.md) ·
[Contract](android/docs/R44-PLAN.md) ·
[Privacy PRD](android/docs/R44-PRIVACY-PRD.md) ·
[Try without Studio](android/docs/BUILD.md).

## Prior R43 — 23 executed kernels across 23 modules (widest spread yet)

Branch **`arena/350b91e4-osb`**. Third consecutive sandbox-reset recovery, then
twenty-three new host-executed kernels deepening 23/50 modules (46%): frozen
panes (cut twips formula upheld as cut), Trojan-Source bidi hygiene with
stripping pinned as a user-visible choice, duplicate-line cleanup, PDF
permission bits (policy-not-enforcement), the full 8-token Referrer-Policy
table, Debian control fields, the pinned Android API 21..36 naming table
(API 37 refused until named), golden-angle seat hues, median tap tempo,
BEP-12 tracker tiers (one real regex hole found by first execution, repaired,
attack kept), QR size law, WebAuthn RP-ID matching with pslChecked=false
carried in the result type (the dangerous case executed to justify it),
caret/tilde ranges, snippet windows with brute-force-agreed optimality, age
string recognition (zero cryptography), breadcrumb trimming, Apdex, percent
change (positive bases, declared), RTSP transport, Message-IDs, time
coarsening (NOT anonymity, stated), aspect snapping, alert keys.

**50/50 testable (100%); 0/50 full-spec accepted; no A-grade claim —
[R43-ALTERNATIVES](android/docs/R43-ALTERNATIVES.md) answers the Class-A
question honestly again.** Executed: **372 kotlinc/JVM assertions** across 23
kernels, 23 mirrors, lab PASS with six prior rounds' regressions inside;
bridge now registers `:testing:r37Contracts` … `:testing:r43Contracts`
(137 kernels / 2399 assertions under Gradle once green).
[Results](android/docs/R43-RESULTS.md) ·
[Contract](android/docs/R43-PLAN.md) ·
[Privacy PRD](android/docs/R43-PRIVACY-PRD.md) ·
[Try without Studio](android/docs/BUILD.md).

## Prior R42 — 22 executed kernels across 21 modules; CI bridge spans R37–R42

Branch **`arena/350b91e4-osb`**. Second consecutive sandbox-reset recovery, then
twenty-two new host-executed kernels deepening 21/50 modules (42%): spreadsheet
fill semantics ($-pinned AbsRef) + merge-region law (MergedCells), writer case
cycling + comment toggling, PDF date strings (spec example executed), NMEA
fields, RFC 6797 HSTS (unknown-directive tolerance done spec-right), Damm
pairing-code check digits (both published guarantees executed as mutation
sweeps), ar/deb + BMP container walls, GPC consent intake that refuses to
coerce, leak-proof password-policy checks, dB laws, strict hex, snooze ladders,
1-2-5 axis steps, anchor+period schedules, RFC 2231 continuations, CSI cursor
math, thirds grids, histograms, unit prices. Zero post-execution kernel
repairs (second consecutive round); one pre-execution purity repair disclosed.
Bridge: `r37Contracts` backport + `r42Contracts` — once green, R37–R42's 114
kernels / 2027 assertions run under real Gradle in CI.

**50/50 testable (100%); 0/50 full-spec accepted; no A-grade claim —
[R42-ALTERNATIVES](android/docs/R42-ALTERNATIVES.md) answers the Class-A
question honestly again.** Executed: **362 kotlinc/JVM assertions** across 22
kernels, 22 Python mirrors, lab 22 gates + 46 suites / 487 tests.
[Results](android/docs/R42-RESULTS.md) ·
[Contract](android/docs/R42-PLAN.md) ·
[Privacy PRD](android/docs/R42-PRIVACY-PRD.md) ·
[Try without Studio](android/docs/BUILD.md).

## Prior R41 — 21 executed kernels across 20 modules; CI bridge spans R38–R41

Branch **`arena/350b91e4-osb`**. Round opened with a full sandbox-reset recovery
(byte-clean re-sync, toolchain reinstall, prior baselines green before any
edit). Twenty-one new host-executed kernels deepen 20/50 modules (40%): the
spreadsheet ADDRESSING layer completes (SheetRange + R1c1Ref over the full
16384×1048576 grid, no wrapping), writer gains UAX-11 column widths +
provably-geometry-preserving tab expansion, plus PDF page-geometry
composition, shlex-identical display quoting, calendar-honest ISO durations,
carry-safe DMS, Android-order hex colors, Flesch arithmetic, EAN-13/UPC/ISBN
algebra, RFC 2397 data: URLs, pg_trgm trigrams, turn rotation, bias-corrected
EWMA, loss-refusing settings migration, cast namespaces, git-style sharding,
span merging, BEP-3 piece math, and RTL-aware launcher grids. **Zero kernel
repairs — first round since R38 where every kernel passed its full contract on
first execution.** The CI bridge extends backwards (`r38Contracts`) and
forwards (`r41Contracts`): once green, R38–R41's 77 kernels / 1391 assertions
all execute under real Gradle in CI.

**50/50 testable (100%); 0/50 full-spec accepted; no A-grade claim — the
Class-A question is answered honestly again in
[R41-ALTERNATIVES](android/docs/R41-ALTERNATIVES.md).** Executed: **361
kotlinc/JVM assertions** across 21 kernels, 21 Python mirrors (five held-out
stdlib families), lab 20 gates + 45 suites / 451 tests.
[Results](android/docs/R41-RESULTS.md) ·
[Contract](android/docs/R41-PLAN.md) ·
[Privacy PRD](android/docs/R41-PRIVACY-PRD.md) ·
[Try without Studio](android/docs/BUILD.md).

## Prior R40 — 20 executed kernels + first CI-executed contract tasks

Branch **`arena/350b91e4-osb`**. Twenty new host-executed kernels deepen 19/50
modules (38%): spreadsheet format-code structure + the seven classic error
literals; writer dash/ellipsis education, U+FFFC bookkeeping and CMOS footnote
marks; PDF /Rotate with the full composition table; strict HTML entities (the
cp1252 quirk refused AND documented); RFC 2047 encoded words; 7z signature CRC
gate; FNV-1a; ICAO 9303 MRZ check digits reproducing the published specimen;
filename safety; nearest-rank percentiles; GFM task checkboxes; exact-tiling
snap fractions; bplist envelopes; launcher monograms; Bech32/Bech32m; otpauth
intake hardening; IP /24-/48 redaction. **NEW: `:testing:r39Contracts` +
`:testing:r40Contracts` run all 39 recent kernels under real Gradle in CI's
JVM lane.** Six in-round repairs are documented in R40-PLAN §I — four kernel
bugs caught by first execution, plus a lab-harness honesty hole (hollow
"PASS 0/0" tool-suite lines) fixed and disclosed retroactively for R39.

**50/50 testable (100%); 0/50 full-spec accepted; no A-grade claim — the
Class-A question is answered honestly again in
[R40-ALTERNATIVES](android/docs/R40-ALTERNATIVES.md).** Executed: **360
kotlinc/JVM assertions** across 20 separately compiled kernels, 20 Python
mirrors (six held-out stdlib families), lab 18 gates + 44 suites / 418 tests.
[Results](android/docs/R40-RESULTS.md) ·
[Contract](android/docs/R40-PLAN.md) ·
[Privacy PRD](android/docs/R40-PRIVACY-PRD.md) ·
[Try without Studio](android/docs/BUILD.md).

## Prior R39 — 19 executed kernels, writer + spreadsheet push (host evidence only)

Branch **`arena/350b91e4-osb`**. Nineteen new host-executed kernels deepen 16/50
modules (32%): the spreadsheet trio (Excel serial dates with the Lotus phantom
1900-02-29, the full A..XFD column bijection, exact ECMA-376 column-width pixel
math), the writer trio (smart quotes with declared narrowings, UAX-14 soft
hyphens, canonical Roman numbering with the full 1..3999 bijection), PDF
CropBox rules, ICO directories with PNG-IHDR agreement, full RFC 3492 Punycode,
USB device descriptors (input-path audit rung), MIDI notes, the canonical ULID
codec, a JWS splitter that constitutionally cannot claim verification, IBAN
mod-97, fnmatch-exact globs, MAC U/L-bit privacy analysis, strict RFC 3339,
quiet-hours windows, and APEX exposure math. No Android code authored (touch
audit +0; manifest pinned). Fresh research: ULID canonical spec fetched;
same-day Tails 7.15.1 + eMudhra TLS-exit currency; 2026 HID-defense papers.

**50/50 testable (100%); 0/50 full-spec accepted; no A-grade claim — the
"writer/spreadsheet to Class A this turn?" question is answered honestly in
[R39-ALTERNATIVES](android/docs/R39-ALTERNATIVES.md).** Executed: **344
kotlinc/JVM assertions** across 19 separately compiled kernels, 19 Python
mirrors (six held-out stdlib verifiers), 350/350 tool tests (43 suites).
[Results](android/docs/R39-RESULTS.md) ·
[Contract](android/docs/R39-PLAN.md) ·
[Privacy PRD](android/docs/R39-PRIVACY-PRD.md) ·
[Try without Studio](android/docs/BUILD.md).

## Prior R38 — 17 executed kernels, pure-logic round (host evidence only)

Branch **`arena/350b91e4-osb`**. Seventeen new host-executed kernels deepen 15/50
modules (30%), all seconds (cumulative distinct holds at 45/50, 90%): magnet
links + strict bencode (first beam formats), FLAC/WebP/EDID headers with real
byte fixtures, SemVer precedence, UUID v7 timestamps, HTTP ranges,
quoted-printable, strict PEM, ISO week dates, CSS HSL, WebVTT timings, Luhn PAN
redaction, NARA Soundex, PDF name escapes, and an explicitly evadable
keystroke-cadence classifier (no "safe" verdict exists). No Android code
authored — touch audit +0 delta; manifest scope pinned. Fresh research: two
fetched primaries (BEP 3, BEP 9), stdlib-held-out mirrors (uuid, quopri,
colorsys, isocalendar), **Tails 7.15.1 same-day currency** (eMudhra CA block,
CVE-2026-113470), 2026 keystroke-timing numbers pinned in code.

**50/50 testable (100%); 0/50 full-spec accepted; no A-grade claim.** Executed:
**326 kotlinc/JVM assertions** across 17 separately compiled kernels, 17
independent Python mirrors, touch-target + parse + manifest-scope gates,
337/337 tool tests (42 suites). [Results](android/docs/R38-RESULTS.md) ·
[Contract](android/docs/R38-PLAN.md) · [Privacy PRD](android/docs/R38-PRIVACY-PRD.md) ·
[Try without Studio](android/docs/BUILD.md).

## Prior R37 — 14 executed kernels, pure-logic round (host evidence only)

Fourteen host-executed kernels deepen 14/50
modules (28%), all seconds (cumulative distinct holds at 45/50, 90%): heading slugs,
IEC size scaling, VTODO tallies, XZ stream headers, paste re-indentation, RTSP
reason phrases, tracking-parameter stripping, HIBP range scans, encoded polylines,
MP4 box walks, OSC 8 hyperlinks, reciprocal-rank fusion, exit-status names,
field-of-view math. Plus one host tool for HID input-path auditing (a transcript
parser, explicitly NOT a keylogger detector).
No Android code authored this turn — no new components, permissions, or UI (touch
audit +0 delta; manifest scope pinned). Fresh research: three **fetched primaries**
(RFC 2326, xz 1.2.1, RFC 5545) drove one shipped repair (VTODO `IN-PROCESS`) and
two pre-run corrections; Tails 7.15 stands, Signature 27 still November, Servo
0.7.0 stands, Orbot 17.9.7 stands, the A17 six stand.

**50/50 testable (100%); 0/50 full-spec accepted; no A-grade claim.** Executed:
274 kotlinc/JVM assertions across 14 separately compiled kernels, independent
Python recomputation (14 mirrors), touch-target + parse + manifest-scope gates,
326/326 tool tests. [Results](android/docs/R37-RESULTS.md) ·
[Contract](android/docs/R37-PLAN.md) · [Privacy PRD](android/docs/R37-PRIVACY-PRD.md) ·
[Try without Studio](android/docs/BUILD.md).

## Prior R36 — 12 executed kernels, pure-logic round (host evidence only)

Branch **`arena/350b91e4-osb`**. Twelve host-executed kernels deepened 12/50 modules
(24%), all seconds. No Android code authored. Fresh research: Tails 7.15, Signature
27 November still dateless, Servo 0.7.0 + downstream LTS embed, A17 six, Orbot 17.9.7.

**50/50 testable (100%); 0/50 full-spec accepted; no A-grade claim.** Executed:
176 kotlinc/JVM assertions, independent recomputation, touch-target + parse +
manifest-scope gates, 307/307 tool tests. [Results](android/docs/R36-RESULTS.md) ·
[Contract](android/docs/R36-PLAN.md) · [Privacy PRD](android/docs/R36-PRIVACY-PRD.md).

## Prior R30 — 10 executed kernels, pure-logic round (host evidence only)

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
