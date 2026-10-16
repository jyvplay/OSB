# Fusion Shell — native Android development

Open **[`android/`](android/README.md)** in Android Studio Rabbit1. That is the Kotlin / Jetpack Compose application; the root Vite page is only a project landing page and is not the Android product.

**The full v2.0/v2.1 product remains incomplete.** The repository's 231-row requirement ledger is not fully accepted; no release/performance/anonymous-OS claim is made. The root TypeScript page is only a refreshed project landing page, not the Android product. See [build instructions](android/docs/BUILD.md), [verification status](android/STATUS.md), and the [requirements ledger](android/requirements.csv).

## R23 — privacy-workspace increment (source added; Android-unverified here)

Added a read-only local device-posture panel and user-directed Android Settings links to the separate no-INTERNET `:privacy` companion. It reports only bounded system API observations, treats unavailable values as unknown, and explicitly says it cannot scan for malware/keyloggers, authenticate hardware/firmware, or provide anonymity. It does not change device settings. See the [R23 A–J engineering record](android/docs/R23-ENGINEERING.md), [privacy PRD addendum](android/docs/R23-PRIVACY-PRD.md), and [fresh primary research](android/docs/research/r23-primary.md).

R23 compiled, linted and produced an APK in remote API34 CI, but the 10-case suite finished **8 PASS / 2 FAIL**; all four new posture tests and screenshot/socket controls passed, while two workspace UI/lifecycle tests failed. A follow-up caught an Android-test-source compile error (`UiObject.exists`); that assertion has been removed and another CI run is pending. This sandbox has no Android toolchain. **48/49 modules (97.96%) meet the repository's narrow substantial-testable-subset census; 1/49 (2.04%) is not established; 0/49 are fully specification-accepted.** These are module-level source/testability counts, not feature completion or R23 acceptance. [Census](android/docs/MODULE-TESTABILITY.md) · [Rabbit1 try-out](android/docs/BUILD.md).

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
