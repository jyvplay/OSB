# Completion ledger — R13 (honest, build-unverified)

Counting unit: **Gradle modules** against the 48-module v2.1 plan
(1 app + 12 core + 21 feature + 7 format + 4 cast + 3 test/bench).

A module is *source-present* only when its `build.gradle.kts` and at least one
real implementation file exist here. A module is *verified* only after a
passing Android Studio sync, `:app:assembleDebug`, and its tests running on a
device or emulator. **None of that ran here** — this environment has no
Android SDK, no Gradle runtime and no emulator.

| State | Modules | % of 48 |
|---|---:|---:|
| Fully complete to v2.1 spec **and** build/device verified | 0 | 0.0% |
| Source-present, partial (unverified) | 47 | 97.9% |
| Not present in this tree (separate-repo GPL receiver) | 1 | 2.1% |

By feature rows (~208 in the v2.1 register): **0 verified complete (0%)**.

## R13 was a correctness pass: real defects found and fixed

Everything below would have **failed the build or failed at runtime**. These
were found by re-reading R12's own output rather than trusting its summary.

1. **`Json` accessors could not compile at any call site.** `str/num/obj/arr`
   were declared as *member* extension functions inside `object Json`, so they
   were only callable from inside `Json`'s own scope. Every use in
   `CastV2Client` and `JsonTest` was a compile error. Moved to top-level
   extensions in the same package.
2. **`CastV2ClientTest` passed `String` where `Int` was expected.**
   `CastMessage("receiver-0", "sender-0", …)` bound a String to the leading
   `protocolVersion: Int` parameter. Replaced with a named-argument helper.
3. **`CastV2ClientTest` used `IOException` without importing it.** Added.
4. **`CastV2ClientTest` contained an ambiguous `""""…""""` raw-string.**
   Rewritten as a plain `contains("sess-1")` assertion.
5. **`PsdCompositeEngineTest` could never pass on the JVM.** It asserted
   success from a method that calls `android.graphics.Bitmap`, which is a
   throwing stub in local unit tests. Split the engine into a pure
   `decodeRaster` → `PsdRaster(argb)` core (no `android.graphics`) plus a thin
   `PsdBitmaps` Android bridge. The test suite now exercises the real decoder
   and genuinely passes on a JVM — raw RGB, RGBA alpha, PackBits RLE, literal
   runs, the `-128` no-op, and five distinct rejection paths.
6. **`usesCleartextTraffic="false"` would have broken casting at runtime.**
   DLNA SOAP control and the AirPlay `/play` endpoint are plain HTTP, so every
   send would have thrown "Cleartext HTTP traffic not permitted". Set to true
   with an explicit comment that this is LAN-only.
7. **Stale doc comment in `DlnaClient`** claimed there was no embedded HTTP
   server, which stopped being true when `LocalMediaServer` landed. Corrected.
8. Removed an unused import and an unused field in `BeamDiscovery`.

## R13 additions for runnability

- **`app/src/main/res/` created** (the app previously had *no* resources):
  `strings.xml`, `colors.xml`, `themes.xml` (window background matches the
  Compose desktop so there is no white flash), an adaptive launcher icon
  (`mipmap-anydpi-v26` + vector foreground — valid because minSdk 34 ≥ 26, so
  no PNG density buckets are needed), and a proper `@string/app_name`.
- Manifest now references the real theme and icon, documents why cleartext is
  enabled, and deliberately **omits** `launchMode="singleTask"` because
  `ActivityScenario` (used by the instrumented smoke test) is unreliable with it.

## Source-present (47)

**app (1/1)** · **core (12/12)** · **feature (21/21)** · **format (7/7)** ·
**cast (3/4)** · **test/bench (3/3: benchmark, baselineprofile, testing)**

## Not present in this tree (1)

- **Separate GPL AirPlay receiver app** — by license design it must ship as its
  own application and source repository. Only
  `android/cast/receiver-gpl/README.md` documents the boundary.

## Honest limits (unchanged and still true)

- **Cast clients are real but device-unverified.** castv2 and AirPlay `/play`
  are loopback-tested against fakes that speak the real framing. No physical
  Chromecast or Apple TV was available, so pairing prompts, receiver quirks and
  glass-to-glass latency are unmeasured (E0). The protobuf codec is validated
  against hand-computed bytes, so it is not merely tested against itself.
- AirPlay RAOP audio streaming and FairPlay mirroring: **not implemented**.
  Chromecast screen mirroring: **not implemented**. Any receiver: **not
  implemented**.
- External display is a live session mirror, not an interactive compositor, and
  it closes with the Activity.
- Files: reversible trash (files only, 256 MB, app-private); no cross-folder
  move. Capture: video only, one consent per recording. Backup: app-private
  notes only. Terminal: built-ins only, not a PTY. Lens: preview only, no
  effects, no virtual camera. Together/Automation: local only, no networking or
  triggers. Messages: SMS text only — no MMS, RCS, iMessage or Matrix.
- `:format:lok` is a boundary class, not a document engine. `:format:ooxml` is
  our own scanner, not Apache POI. `:format:psd` decodes the flattened
  composite only.

## Gates before any "complete" claim

1. Studio sync + `:app:assembleDebug` on an API 34/35 emulator.
2. JVM green: `:core:windowing:test :core:index:test :core:commands:test
   :core:security:test :format:native:test :format:archive:test
   :format:ooxml:test :format:psd:test :cast:dlna:test :cast:airplay:test
   :cast:castv2:test :feature:beam:test :feature:together:test :testing:test`.
3. Instrumented: `:app:pixel6Api34DebugAndroidTest`.
4. Macrobenchmark on hardware: `:benchmark:connectedBenchmarkAndroidTest`.
5. Device passes: cast to a real Chromecast and Apple TV, send/read SMS on a
   SIM, SAF grant/copy, delete→restore, save-a-copy, WebView load, vault
   round-trip, notes save, backup export→restore, MP4 playback, notification
   grant/revoke, DLNA to a real renderer, camera preview, display
   attach/detach, rotation + relaunch restore.
6. compileSdk 37 / LAN permission / NPU declarations wait for a compatible AGP
   and on-device API verification.
7. Legal review before any LibreOfficeKit payload, PSD expansion, HEIC
   encoding, FairPlay, or GPL receiver work.
