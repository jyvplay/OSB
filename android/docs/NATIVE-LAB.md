# Real native build and visual lab — R8

**Authored, not Android-compiled or device-executed here.** No screenshots or APKs
were produced in this sandbox. The host admission/parser tests ran; that does
not establish that Gradle, instrumentation or screenshot capture works end-to-end.

## What can be established, and where?

A genuine environment can be configured on your laptop or an authorized CI
worker: full JDK17, Android SDK36, platform-tools, Build Tools35.0.0 and36.0.0,
Gradle dependency access, and a booted API34+ AVD with suitable acceleration.
This sandbox has a cached Java runtime/Kotlin compiler, **not** a full JDK/SDK,
ADB, emulator or `/dev/kvm`. The required Google/Maven/Gradle download hosts are
outside its network allowlist. No browser rendering or generated illustration
can replace native Android inspection. Android Studio “Rabbit 1” compatibility
has not been verified; the pinned AGP/Kotlin/toolchain, not the IDE name, is the
build contract. compileSdk remains36, a deviation from the required37.

## Laptop commands

Open `android/` in Android Studio. Install the SDK components above; select a
full JDK17 for both Gradle and your terminal's `JAVA_HOME`. Set `ANDROID_HOME` to
your real SDK directory, or pass `--sdk /absolute/sdk/path` to every command.
Python3 is required. Run from the `android/` directory:

```sh
python3 tools/native_lab.py doctor
python3 tools/native_lab.py build
```

`doctor` checks local prerequisites only. `build` runs both debug APK builds,
both instrumentation-APK builds, both app lint tasks and the kernel Gradle contract
runners (eight on Linux; six elsewhere), retaining the real Gradle log. It does not run every JUnit suite;
additional unit-test tasks are listed in `BUILD.md`/the CI workflow. Downloads
require your network and accepted SDK licenses. Wrapper JAR integrity is checked
by the source checker, but the Gradle distribution SHA-256 remains unpinned:
supply-chain/release acceptance is **not** cleared.

Create a **new, disposable** AVD, preferably a tablet/resizable image with
`ranchu`/`goldfish` hardware, boot it and unlock it. Do not configure personal
accounts or import valuable files. Find its exact serial with your SDK's
`adb devices -l`; use that value, not an automatically selected first device.

```sh
# Replace emulator-5554 with the exact disposable AVD serial you inspected.
python3 tools/native_lab.py visual --serial emulator-5554 --allow-disposable-reset
# Optional separate play-flavor run, with the same safety conditions:
python3 tools/native_lab.py visual --serial emulator-5554 --allow-disposable-reset --flavor play
```

The consent flag authorizes installing debug/test APKs, **erasing this app's
preferences and editor/calculator/desktop recovery drafts** before each smoke
or capture test, and temporarily changing that emulator's system font scale.
It is NOT a factory wipe and does not uninstall other apps. Physical/network
serials, missing consent, unknown emulator hardware, incomplete boot and API<34
are refused. The instrumentation reset rule independently requires the consent
argument and recognized emulator hardware, including when invoked from Studio.
These are accidental-target guards, not authentication against a malicious ADB
server/device spoofing all properties. Never run against a daily-driver device.

The visual command builds current sources rather than silently trusting an old
APK. It validates AGP metadata/package identity and refuses split/escaping APK
paths, installs the selected flavor, and runs the smoke/copy-contract/checkpoint tests and
native SQLite/notes filesystem and encrypted-index/Keystore checks, and one native capture test at **system font scale1.0 and2.0**. Android34+ may apply
nonlinear font scaling: this is the actual 200% system setting, not a claim that
every glyph doubles in pixels. Configuration readback and the Activity's actual
font scale must agree. Each capture uses `UiAutomation.takeScreenshot`, not a
synthetic rendering, and a fresh per-run private directory. Seven views per scale
are requested: desktop, calculator, editor, editor tools, window-action menu, Find
and Notes.
This is limited surface coverage, not every module, smallest window or every
orientation/IME combination. Native assertions are **unrun** here.

## Evidence and failure handling

Real runs write ignored `pilot-reports/lab-*/` directories:
- `build.log`, real instrumentation logs and `result.json`;
- `device-before.json`, recorded **before** font mutation, for recovery;
- on successful capture, fourteen PNGs, dimensions/SHA-256 and Activity configuration;
- `humanVisualReview: NOT PERFORMED` even when automated checks pass;
- best-effort `failure-screen.png` from the actual emulator if testing fails.

A shell exit0 alone is insufficient: the parser also requires the exact expected
JUnit test count, success result code and no failure markers. Missing/old captures
cannot count as success. A failure stops the run; it does not turn on gated modules.
The test app is force-stopped in cleanup (a timed-out instrument command might
still be running). Font restoration is attempted even if that stop fails, and
read back. If the process is
killed, ADB disconnects, the machine loses power, or restoration fails, use the
original value in `device-before.json` to restore it manually:

```sh
adb -s YOUR_EXACT_EMULATOR_SERIAL shell settings put system font_scale ORIGINAL_VALUE
# If the recorded original value was "null", delete the override instead:
adb -s YOUR_EXACT_EMULATOR_SERIAL shell settings delete system font_scale
```

No rollback of erased test drafts is possible. Uninstalling/recreating the AVD is
an optional **manual** cleanup, never done silently by this harness. Generated
logs/screenshots may contain test-device information; inspect before sharing.

## Human UI/UX review still required

Inspect real screenshots, then interact with the running native app. Record
viewport, density, orientation, font setting, build hash and pass/fail per case:
- original native desktop hierarchy/contrast, no generic web replacement;
- all title controls ≥48dp; title drag region remains reachable at240dp Compact
  and320dp Touch; narrow, tiled, maximized and restored windows;
- no clipped controls at200%; horizontal/vertical scrolling and keyboard focus
  must make every action reachable, not merely produce a screenshot;
- keyboard-only open/switch/move/resize/minimize/restore/close, with visible focus;
- TalkBack, Switch Access, Voice Access and non-drag alternatives;
- IME composition, selection and rotation/process-death recovery without silent
  draft loss; actual SAF provider admission/readback/crash behavior;
- external display/hotplug/BlackTop and secure-content exclusions on real devices.

Screenshots alone cannot pass these gates. Two physical reference devices,
performance/thermal/page-size measurements, legal/license review, full feature
implementation and the remaining spike matrix are still required.

## Direct managed-device tests

The configured CI job uses a disposable Gradle-managed AVD, passes consent and
excludes the capture class (which needs per-run arguments):

```sh
./gradlew :app:pixel6Api34ProDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.fusionDisposable=true \
  -Pandroid.testInstrumentationRunnerArguments.notClass=dev.fusion.shell.VisualCaptureTest
```

No CI workflow was dispatched in this pass. A direct Studio run without explicit
consent intentionally fails rather than deleting recovery drafts.

## R7 host-filesystem distinction

The real NoteFiles contract suite exercises POSIX symlinks, file locks, atomic
rename and directory force. The standalone sandbox run used Linux. The laptop
build helper includes `:testing:notesContracts` only on Linux, listing exactly
which tasks actually ran. On other hosts it does not pretend that skipping this
Linux-specific suite proves correctness: `StorageBoundaryDeviceTest` must run on
the Android emulator, and physical durability still requires separate testing.
No Gradle, emulator or screenshot run has occurred here.

## R8 capture integrity and encrypted-index obligations

The lab now bounds each binary subprocess capture to32 MiB stdout/256 KiB stderr
and its existing timeout. It validates PNG chunk framing/CRC, required pixel data,
bounded complete deflate output and row filters. Supported captures are8-bit,
noninterlaced RGB/RGBA,≤16,384px per dimension and≤16,777,216 total pixels,≤4096
chunks. Only small sRGB/gAMA/cHRM/pHYs/sBIT metadata is admitted. ICC/compressed
text/EXIF, animation and other extensions are intentionally refused. A real
Android encoder outside this subset will produce a visible failure; widen the
parser only with actual captures and new bounded tests, never by skipping checks.
These checks do not establish image provenance, pixel correctness, UI quality or
accessibility. Pipe limits are not an OS sandbox or a descendant-process supervisor.

Linux builds additionally run `:testing:encryptedIndexContracts`. The native
`EncryptedIndexDeviceTest` uses fresh test aliases/directories and checks actual
Keystore/filesystem behavior when run. It is **unrun here**. Search-cache encryption
does not encrypt the original Notes files. Encrypted Find source discovery and
native UI integration are not delivered or enabled; S4 remains blocked.

The earlier CI failure occurred during SDK setup, not Kotlin compilation. The
workflow now targets ubuntu-24.04's documented SDK and reports setup failures
explicitly. This candidate CI change has not been dispatched/tested in this pass.
