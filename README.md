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
[Current substantial testable subsets:30/48 (62.50%)](android/docs/MODULE-TESTABILITY.md).
**0/48 specification-accepted modules (0%); not a production-ready beta.**

## Latest R12 continuation

**30/48 modules (62.50%) have substantial testable subsets;0/48 are fully specification-accepted.**
Five new bounded kernels and two newly executed Android inventory paths, plus strict UTF8/CSV
repairs and actual subtitle inspection. See the [R12 continuation engineering record](android/docs/R12B-ENGINEERING.md)
and [Android Studio instructions](android/docs/BUILD.md). Native results are revision-specific;
no whole browser, Office engine, full sandbox or complete productivity suite is claimed.
