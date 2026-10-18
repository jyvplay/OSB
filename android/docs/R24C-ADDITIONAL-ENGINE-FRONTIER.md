# Additional October 2026 engine frontier — concrete intake, not a bundled browser/Office engine

The new source work here is separate from the eight mechanisms in [the portfolio](R24C-PRIVACY-AND-ENGINES.md). It was researched after the initial native failures, not presented as a substitute for fixing them.

## Servo: a genuinely different browser engine, with an actual Android interface

Fresh primary GitHub API inspection on2026-10-18 found **Servo v0.7.0**, published2026-10-05, and its actual release metadata:

- `servo-aarch64-android.aar`, asset605216176, declared39,860,285 bytes, declared SHA-256 `6b73d23cf63fb3d7c19c908cddafbd475585199583bd16f6aa268b84c6093105`.
- The same release lists an Android demo APK of115,768,080 bytes. Neither size is an RSS/startup/system-footprint measurement.
- Immutable release: https://github.com/servo/servo/releases/tag/v0.7.0 . Raw retained metadata and source hashes: [r24c-servo-source.json](research/r24c-servo-source.json).

This improves on a generic "try another WebView" suggestion: actual tag source `support/android/apk/servoview/src/main/java/org/servo/servoview/ServoView.kt` was retrieved and read, blob`fb71931ce2d839027ce9b24373e0a4412f807c99`. It is a real Android `SurfaceView` and `Choreographer.FrameCallback` wrapper. Its constructor needs `Context`, `Servo.Client`, arguments/log/experimental flags, initial URI, `ServoNavigator` and `CoroutineScope`; source exposes load, stop, resize, surface pause/resume and frame callbacks. This is a Rust-engine embedder, not Android system WebView or GeckoView under a different label.

The tag's actual AAR build file (blob`9993af6ec53f8dc6a3180604e7a9d33b2fc021d7`) uses compileSdk37 and packages a Rust-built `libservoshell.so` plus `libc++_shared.so` through ndk-build. Architecture-specific x64 and ARM variants exist in **source**; that does not mean this observed ARM64 release asset can run on our x86_64 AVD. The tag's retrieved LICENSE is MPL2.0 (blob`d8b475b6e17e390d702c9be4d3a735c7622b9735`); this is not clearance of every dependency/font/resource or trademark.

Primary project material also documents a WebView embedding API, Android support and continuing breaking changes/experimental features; it is not a claim of complete Chromium/Firefox compatibility. [1](https://servo.org/) [2](https://servo.org/blog/2026/05/31/april-in-servo/) [8](https://servo.org/blog/2026/08/31/july-in-servo/)

### Exact executable experiment and current access limits

`engine-intake.py` already performs bounded **nonexecuting** AAR/ELF inspection. The new CI workflow pins this exact release hash/byte size, invokes that real parser, requires a native library rather than merely a Java wrapper, and retains PT_LOAD/ABI/notice/archive-member observations. It expressly keeps `integrated`, `runtime_verified` and `legal_accepted` false. It never installs, dlopens or navigates with the engine. The binary is temporary runner data, not vendored into this repository or redistributed as an app dependency.

The local `gh release download` **failed with EOF at the asset redirect**, outside the sandbox's host allowlist; no local binary intake is inferred. A manual workflow-dispatch attempt returned403 "Resource not accessible by integration"; repository read/branch push still worked. No credentials were requested and there was no repeated unauthorized dispatch. The standalone new workflow did not produce a run. The executable intake is therefore also referenced as a separate job by the already-registered continuation workflow. Its actual outcome, if executed, belongs in `R24C-RESULTS.md` and the retained receipt—not in a guessed success statement here.

### Concrete candidate construction / strongest falsifier

1. A separately owned browser session creates the real engine view only after consent; keep an explicit per-navigation generation, permission/prompt broker and bounded output/URL policy.
2. Surface lifecycle owns renderer creation, resize, pause/resume and teardown. Revoke the entire session/process on a stale callback or failed generation; do not let an old surface publish into a new window.
3. Select exact ABI/build and native packaging policy; test real 16K page-size installation/runtime separately from a header congruence calculation.
4. Pin profile directories to the correct session and prove cookie/storage/file permission separation with actual two-session canaries. A new UID/process changes authority; a library filename alone does not.
5. Exercise pointer/IME/navigation, resize/background/destruction and malicious popup/download/permission flows on actual Android, then a held-out web-compatibility corpus. Measure the frame callback/CPU/RSS lifecycle; Rust is not proof of a leak-free compositor or whole-process memory safety.

**Cheapest decisive falsifier:** install a tiny real wrapper on a supported ARM64 disposable device, load a local origin with two colored/text/input canaries, resize, background and destroy/recreate while checking native pixels, IME commit, stale callback rejection and process/FD/thread counters. AAR inspection does **not** run that experiment. Missing ABI/runtime/callback/storage/license evidence keeps full-browser acceptance open. This is a concrete viable research route, not a claim that OSB now embeds Servo.

## ONLYOFFICE: useful server architecture, not a free native mobile drop-in

Fresh primary README retrieval from `ONLYOFFICE/DocumentServer`, blob`fc18a04513ca801ec09409378331cce111cc5554`, explicitly lists Community as AGPLv3, Enterprise/Developer as proprietary, and mobile web editors as absent from Community. Retained hash: [r24c-onlyoffice-source.json](research/r24c-onlyoffice-source.json). The vendor's May2026 license guide distinguishes free AGPL desktop/server software from free-to-download mobile applications under a commercial license. [11](https://github.com/onlyoffice/documentserver) [4](https://www.onlyoffice.com/blog/2026/05/onlyoffice-license-and-trademark-policy)

AGPL is open-source and permits commercial use subject to its actual obligations; it is **not** automatically impermissible. However "free download", a top-level AGPL badge, and an old fork README do not establish that the current native mobile editor or all desired packaged features are unrestricted/permissively embeddable. Exact source, modifications, distribution/network-source obligations, branding and transitive notices need review. [7](https://helpcenter.onlyoffice.com/docs/faq/docs-community.aspx)

A real user-controlled server plus a consented client can be an alternative **remote** Office architecture. Its request must bind owner/session/document digest/revision, cap input/output/deadline, stage a new output copy, and require local readback before replace. A server's reply is not an authenticity proof. The strongest adversary is a compromised converter or stolen document URL; the cheapest privacy falsifier is an unconsented upload/retention after cancellation. No server was deployed or contacted, and this does not meet an offline/native Office contract. It remains an alternative architecture with a concrete trust/data-flow model, not an implemented replacement for LOK.

## What this does and does not settle

This turn has a newer primary-source frontier and a pinned executable artifact-intake experiment, in addition to GeckoView/LibreOfficeKit/platform/native-subset routes. It does not claim an exhaustive search, independent expert review, compiler verification of these upstream engines, JavaScript/web compatibility, full Office fidelity, or universal software/service license clearance. `:format:lok` remains the one unestablished substantial subset; no engine documentation or intake receipt changes that census by itself.
