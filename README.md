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
[Current substantial testable subsets:49/50 (98%)](android/docs/MODULE-TESTABILITY.md).
**0/50 specification-accepted modules (0%); all50 unfinished at full scope.**

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
