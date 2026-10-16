# Fusion Shell — native Android development

Open **[`android/`](android/README.md)** in Android Studio. That is the Kotlin /
Jetpack Compose application, not a browser recreation.

The existing root `src/`, npm files and Vite configuration are legacy web work;
they are not the Android product, were not extended in this pass, and are not
part of the Android build.

**This is an incomplete implementation with limited API34 emulator testing of the owner's
v2.0 + v2.1 specification.** No release-complete or performance claim is made.
See [build instructions](android/docs/BUILD.md),
[current verification status](android/STATUS.md), and the
[231-row requirement ledger](android/requirements.csv).

Historical: [prior R12 engineering and evidence](android/docs/R12-ENGINEERING.md).
[Current substantial testable subsets:48/49 (97.96%)](android/docs/MODULE-TESTABILITY.md).
**0/49 specification-accepted modules (0%); not a production-ready beta.**

## Latest R23 — real native work, overall regression still failed

Native-tested **`4bc7637`** on `arena/f77cb2a6-osb`: **privacy16/0/0**, **memory24/0/0**,
**full main121/1/0 (FAILED)**. An existing R18 task Add workflow timed out with the IME visible
and actual store still empty. No rerun-to-green or blanket stability/acceptance claim.

- Native no-IME digit pad, whole-gesture/focus/lifecycle discard and observation-only exposure review.
- Explicit owned readonly-memory worksheet APIs and real distinct-UID/wire/FileObserver/picker tests;
  no named parser-input fallback in these APIs. Legacy regular-file APIs remain intentional.
- **Real SDK37.0 / AGP9.1.1 / Gradle9.3.1 / target37** builds, JVM/contracts/lint passed.
  Runtime testing is API34; Rabbit1 GUI/API37 runtime are unexecuted. All-gates migration acceptance is not met.
- **4/49 existing production modules deepened (8.16%),0 fully completed**. Cumulative testability48/49
  (97.96%) is NOT product completion. No Tails replacement, all-hardware logger defense or full browser/Office.

[Current results, failures, APK sizes and exact next test](android/docs/R23-RESULTS.md)
· [Laptop/no-Studio instructions](android/docs/BUILD.md#r23--current-session--no-ime-privacy-try-out)
· [A–J evidence and alternatives](android/docs/R23-ENGINEERING.md)
· [Privacy/hardware PRD](android/docs/R23-PRIVACY-PRD.md).

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
