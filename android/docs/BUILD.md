# R32 — 10 executed kernels (pure-logic round) + static visual audit

Current work branch **`arena/350b91e4-osb`**. Open **`OSB/android`**, not the root Vite prototype.
[Exact results and limits](R32-RESULTS.md) · [Scope/acceptance contract](R32-PLAN.md) ·
[Deepening census](R32-MODULE-DEEPENING.md) · [Alternatives](R32-ALTERNATIVES.md) ·
[Privacy PRD](R32-PRIVACY-PRD.md) · Prior: [R31 results](R31-RESULTS.md).

**50/50 testable subsets (100%); 10/50 modules deepened with new executed kernels (20%).
0/50 full-spec accepted; all 50 unfinished at full scope. No A-grade claim.**
Host evidence only: 157 kotlinc/JVM assertions, independent Python recomputation,
static touch-target audit (223 sites, 175 pre-existing findings queued, +0 delta),
Android parse gate (0 syntax errors), manifest-scope gate, 258/258 tool tests.
R32 authors no Android files; the 2 authored Android files (USB watch receiver,
consent dialog) remain authored, NOT executed — CI/Studio must compile and run
them before any behavior claim.

## Laptop / Android Studio Rabbit1 (R28)

Use a maintained **full JDK17** and real **SDK `platforms;android-37.0`** with build
tools 36.0.0/35.0.0. Pins AGP9.1.1 / Gradle9.3.1 / Kotlin2.2.21; min34 / compile37 /
target37. Rabbit1 GUI was not run in this sandbox; push this branch for CI receipts.

1. Open `android/`, sync, select **app / proDebug** on a disposable **API34** AVD
   with copied data.
2. Existing Tabletop/Flat desktop, silver Art / light Business presets, local
   MediaPlayer, offline atlas, PNG-copy and privacy scratchpad are unchanged; follow
   the historical sections below for those bounded workflows.
3. New files pending your verification: **UsbWatchReceiver** (USB attach/detach
   detection + advisory verdict + notification-if-permitted; detection ONLY,
   never blocking) and **ConsentDialog** (scope+TTL authorize-once UI).
   Prior authored surfaces (stream player, vector maps, Orbot handoff,
   BeautyDock still UNWIRED) are unchanged.
4. No emulator exists in the sandbox: run `python3 tools/check_r28_touch_targets.py`
   for the static touch-target audit (223 sites; 175 pre-existing findings are
   a review queue, not rendered proof). Export real Compose layout bounds per
   page and validate with `python3 tools/check_r27_pagematrix.py status.json`
   (24-page matrix; honest coverage is 0.0 until exports exist).

## Without Android Studio or a local SDK (R28)

Real kotlinc/JVM execution of the new kernels plus structural gates — no Android:

```sh
cd android
python3 tools/bootstrap_host_tools.py install   # Linux x86_64; disposable JRE17 + kotlinc into ~/.cache
python3 tools/r28_lab.py host --tools "$HOME/.cache/fusion-host-r23"
python3 tools/check_project.py
```

This runs the 112-assertion R28 contracts, the independent reference
recomputation, the touch-target static audit, the page-matrix and
layout-bounds self-tests, the Android-parse gate, and the 10 R28 Python
tests. It does not build an APK or touch a device. Native compile/device
execution delegates to the recorded Gradle suites (`r28_lab.py device
--device-id ...` labels manual runs only; needs real JDK17 + SDK).

---

# R24 continuation — tabletop, map and local media

Current work branch **`arena/a8ac5475-osb`**. Open **`OSB/android`**, not the root Vite prototype.
[Exact results and limits](R24C-RESULTS.md) · [Scope/acceptance contract](R24C-PLAN.md) · [Privacy / engine routes](R24C-PRIVACY-AND-ENGINES.md).

**49/50 substantial runnable subsets (98%); 1/50 unestablished (2%): LOK. 0/50 full-spec accepted; all 50 remain unfinished at full scope.**
This census is not a claim that 49 current native features passed. The recorded specification still has 231 requirements,
36 cross-cutting obligations and 30 formal spikes; none of those spikes is promoted by this continuation.

## Laptop / Android Studio Rabbit1

Use a maintained **full JDK17** and real **SDK `platforms;android-37.0`** with build tools36.0.0/35.0.0.
Pins remain AGP9.1.1 / Gradle9.3.1 / Kotlin2.2.21; min34 / compile37 / target37. Rabbit1 GUI itself has **not** been
run in this sandbox. Remote SDK/Gradle/emulator results are separately recorded, not called a Studio GUI test.

1. Open `android/`, let Gradle sync, select **app / proDebug**, then run on a disposable **API34** AVD using copied data.
   That is the tested main-app runtime; API37 evidence below concerns the separate privacy companion, not the whole app.
   Some existing features still require explicit per-session evaluation consent. Do not use this as a production OS.
2. Default **Tabletop**: single-click/tap a pin to select it, drag to rearrange, or double-click/tap to open its gated app.
   The selected-pin toolbar also provides Open, four directions, Unpin, and To table/back/left/right. Background drag pans.
   Close/minimize existing windows to reveal the underlying tabletop; old saved windows are not erased.
3. Tap a wall or use its top-row surface chip to focus it. Focused walls have full-size move/pile/pin controls like the table.
   The tiny overview marks are decorative, not invisible miniature targets. The floor is83.16% of the **scene excluding chrome**.
4. **Pin app** adds another first-party launch target. **Piles** selects >=2 same-plane pins; select a pile to move or unpile it.
   **Views** saves a uniquely named snapshot of all pins/piles/surface cameras/focus; loading asks before replacing the current
   arrangement. It does not save documents/windows/permissions/media. Caps512 pins /128 piles /16 views, not infinite storage.
5. **Control panel** or **Settings** offers **Art · Silver** and **Business · Light** presets. Palette, layout and **Tabletop / Flat**
   remain independently selectable; Office Blue/Studio Graphite and existing own-window actions remain. Dock overflow scrolls;
   app glyphs do not all get squeezed into narrow slots. Larger fonts use larger slots. This themes Fusion, not the host OS.
6. Find/open **Offline atlas** and enable the bounded workflow: pan/zoom genuine low-resolution Natural Earth land outlines,
   select coordinates, save a pin, revisit it, or measure spherical distance. No location permission, road map, geocoding or navigation.
7. Find/open **Media**: **Player** opens a system document picker for audio/video and uses Android's local player; **Images & subtitles**
   preserves the existing views. No autoplay/background playback; press Play explicitly and Reopen after backgrounding.
   The selected provider/codec is trusted and can itself use a network. Slow-provider blocking remains an explicit limitation.
8. **Online services** accepts supported public YouTube/Spotify/SoundCloud links, validates offline and shows a canonical-URL/privacy/terms
   confirmation. It hands off to an external app/browser; it is **not** embedded or universally free streaming.
9. The separate **privacy / debug** target remains a no-INTERNET volatile scratchpad/touch-only digit pad. Observed input/IME/accessibility/
   display changes discard and latch until an explicit empty restart. This is not Tails, hardware attestation, keylogger detection,
   protection against the laptop host/firmware/cameras, or a promise of forensic erasure.

Keep the existing isolated readonly PDF preview, literal worksheet/CSV tools, Tasks/Notes and editor workflows; the new desktop does
not unblock full browser/Office engines. Use the original bounded-tool instructions in the historical sections below for those routes.

## Without Android Studio or a local SDK

This runs **real production Kotlin kernels**, Python tools and file/process races, not Android or fake platform classes:

```sh
cd android
python3 tools/bootstrap_host_tools.py install
python3 tools/r24c_lab.py doctor --tools "$HOME/.cache/fusion-host-r23"
python3 tools/r24c_lab.py host --tools "$HOME/.cache/fusion-host-r23"
python3 tools/host_privacy_posture.py --report testing/build/r24c/host-posture.json
# Optional inspection of an actual local artifact; no code execution or automatic engine/legal approval:
python3 tools/engine_intake.py /path/to/actual-engine.aar --report testing/build/r24c/intake.json
```

The tested bootstrap route is Linux x86_64. Its pinned JRE17.0.9 is **old and not a full JDK**, for disposable host tests only;
do not set it as Gradle's JDK or use it for production security. Latest fully archived host pipeline at0260d59:179 Python methods,
439839 R24C assertions and65097 retained R24 assertions; exact-source/output in `verification/r24c/host-0260d59/`.
It is not a rerun of every historical host suite and does not build an APK.

## Actual Android without the IDE GUI

Install a maintained full JDK and the Android command-line tools, set `JAVA_HOME` / `ANDROID_HOME`, and review SDK licenses yourself.
No unsupported-compile suppression or locally forged platform is used.

```sh
cd android
sdkmanager 'platforms;android-37.0' 'build-tools;36.0.0' 'build-tools;35.0.0' 'platform-tools' 'emulator'
python3 tools/r24c_lab.py android
python3 tools/r24c_lab.py device --suite continuation --disposable
python3 tools/r24c_lab.py device --suite full --disposable
python3 tools/r24c_lab.py device --suite privacy --disposable
# Actual released API37.0 privacy runtime, not merely compileSdk37:
sdkmanager 'system-images;android-37.0;google_apis;x86_64'
./gradlew :privacy:pixel6Api37DebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.fusionExpectedRuntime=37
```

Windows uses `gradlew.bat`; the lab selects it automatically for native modes. The device commands reset test-owned state and must
not run on a personal handset. `--disposable` is required for the main app. Managed AVD execution needs hardware virtualization.
The current CI entry points are `android.yml`, `android-r24c.yml`, retained R21/R23/R24 regressions and the two privacy workflows.
The continuation workflow separately inspects a pinned real Servo AAR without loading/installing it; this does not unblock the browser.
APK outputs are `app/build/outputs/apk/pro/debug/`, `app/build/outputs/apk/play/debug/` and `privacy/build/outputs/apk/debug/`.
For remote binaries, choose the **successful exact-candidate** workflow and its artifact; a retained binary from an older failing
workflow is not the current verified app.

## Collect/recheck native evidence

```sh
# Substitute an actual completed GitHub run number from `gh run list`, not a guessed ID.
python3 tools/collect_native_evidence.py RUN_ID RECEIPT_PREFIX --continuation
python3 tools/r24c_capture.py collect --run RUN_ID
python3 tools/r24c_evidence_gate.py docs/verification/RECEIPT_PREFIX-result.json --candidate FULL_COMMIT_SHA --suite continuation
```

The gate requires exact named cases and all-pass/no-skip outcomes plus successful workflow/device conclusions. Capture transport
refuses incomplete/hash-mismatched chunks and does not fabricate images. Actual CI annotation limits require a separate
check-run per frame; each carries<4KiB messages and stays below50 annotations. A dependent job verifies all transported PNG hashes.
The original full PNGs remain in `r24c-native-frames`; preview downsampling is explicitly recorded.
Current visual evidence copies the committed **application Window** with PixelCopy after checking required semantics;
it is not whole-display/system-bar capture or the separate privacy screenshot-security proof. Actual captures/target observations cover only named frames;
all-page/all-state visual, TalkBack, laptop landscape and physical-device acceptance remain open.

---

# Historical R24 build/evaluation instructions (not the current branch/results)

Branch **arena/f77cb2a6-osb**, native-tested23e8868. [Current results/screenshots](R24-RESULTS.md).
**48/49 substantial runnable subsets97.96%;0/49 full original-spec accepted.**

## Laptop / Android Studio Rabbit1

Open **OSB/android**, not root Vite. Use maintained full **JDK17**, actual SDK **platforms;android-37.0**,
build tools36.0.0 and35.0.0. Pins Gradle9.3.1/AGP9.1.1/Kotlin2.2.21, min34/compile37/target37,
documented temporary legacy Kotlin/DSL bridge. No fake SDK/unsupported compile suppression.
Rabbit GUI itself unexecuted; corresponding builds/real managed Android34/37 tests actually ran remotely.

1. Run **app / proDebug** (or playDebug), accept evaluation with copied data in a disposable AVD.
2. Top-right **Control panel** or dock/taskbar **Settings**: select **Business / Art**, **Office Blue / Studio Graphite**.
   They are independent; both entrypoints edit one observable store, no restart or data reset. Dirty drafts preserved in tested traces.
3. **Art**: centered own-app scrollable dock, pointer/keyboard-focus zoom and neighbors/running dots; magnification toggle reduces motion.
   **Business**: full-width bottom own-app taskbar; window actions/focus/restore remain real. Not a host/Android OS theme or keyboard preset.
4. Ctrl+K / Find reaches all app entries. Look for **Isolated PDF preview**; acknowledge scope and choose a copied small PDF.
   Limits2MiB/1024pages/320px; first-party native rendering only in isolated child, no full PDF editing or full Office claimed.
   Old full PDF remains unavailable; source bytes are not overwritten.
5. Tasks/Notes have pinned actions + field-first plaintext forms. Exact native single-tap/real file/draft observations are in results.
6. Separate **privacy / debug** run target is the no-INTERNET volatile scratchpad/no-IME digit pad, not the main app.
   Actual guest sleep/wake clears code and stale input; screen/pointer/AX/host/firmware and paging/erasure remain outside trust.

Use copied data, not real secrets on an untrusted laptop/hypervisor. Neither appearance nor a no-INTERNET APK makes everything anonymous/Tails-like.

## Without Studio / SDK-free sandbox

Actual tested Linux x86_64 route:

```sh
cd android
python3 tools/bootstrap_host_tools.py install
python3 tools/r24_lab.py host --tools "$HOME/.cache/fusion-host-r23"
python3 tools/host_privacy_posture.py --report testing/build/r24/host-posture.json
# Optional bounded local artifact intake, never executes an engine or claims license/runtime approval:
python3 tools/engine_intake.py /path/to/actual-engine.aar --report testing/build/r24/intake.json
```

Latest whole host pipeline exit0/162.067s:158 Python tests,65074 R24 assertions/real separately compiled policies,
R23/R22/R21/reference regressions. Optional tools contain **old JRE17.0.9, not a JDK**; disposable tests only,
NOT Gradle JAVA_HOME/secure production runtime. Compiler/wheels hash pinned; registries still trusted.
No local SDK/adb/javac/Studio, independent agents, physical lab or new SMT theorem claimed.

## Real native JDK/SDK, no IDE GUI

```sh
# Set JAVA_HOME to maintained FULL JDK17 and ANDROID_HOME to actual SDK; review its licenses yourself.
sdkmanager 'platforms;android-37.0' 'build-tools;36.0.0' 'build-tools;35.0.0'
python3 tools/r24_lab.py android
python3 tools/r24_lab.py device --suite ui --disposable       #9 current UI/input/R18 methods
python3 tools/r24_lab.py device --suite pdf --disposable      #3 PDF methods
python3 tools/r24_lab.py device --suite memory --disposable   #24 retained memory/FD/picker methods
python3 tools/r24_lab.py device --suite full --disposable     #133 main methods; includes all above
# The real combined27-case boundary workflow joins retained24 + PDF3, not a sum of two untrusted receipts.
./gradlew :privacy:pixel6Api34DebugAndroidTest
# Exact released-image API37 privacy experiment (NOT every main feature/API37 GUI/hardware acceptance):
sdkmanager 'emulator' 'platform-tools' 'system-images;android-37.0;google_apis;x86_64'
./gradlew :privacy:pixel6Api37DebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.fusionExpectedRuntime=37
```

Windows uses gradlew.bat and maintained manual tool installation; bootstrap is Linux-only. Local native CLI routes were not executed in this no-SDK sandbox;
the same actual Gradle source commands ran in managed Linux/KVM CI. Device suites deliberately reset TEST app state; disposable emulator/data consent is enforced.
JVM policies don't run an APK as a native laptop desktop process.

APK outputs after successful builds:
- privacy/build/outputs/apk/debug/privacy-debug.apk
- app/build/outputs/apk/pro/debug/app-pro-debug.apk
- app/build/outputs/apk/play/debug/app-play-debug.apk

[Main build/run39075701942](https://github.com/jyvplay/OSB/actions/runs/39075701942), artifact fusion-native-debug;
[privacy API34/run39075701923](https://github.com/jyvplay/OSB/actions/runs/39075701923), artifact offline-privacy-companion.
Main full workflow actually completedSUCCESS with133/0 after initial parent-finalization lag; completed receipt collected.
Other scoped9/27/8/18/18 workflows succeeded; exact scope/candidate IDs, earlier failures and hashes in R24-RESULTS/outcome JSON.
Blob redirects unavailable here, so no locally inspected APK/signature/RSS/Tails comparison. Download via GitHub on your laptop.

```sh
python3 tools/r24_evidence_gate.py docs/verification/r24-final-desktop-result.json --candidate 23e8868bf2d23a26f29f0fcc6a7dc592d429069b --claim r24_desktop_api34
python3 tools/r24_evidence_gate.py docs/verification/r24-final-boundary-result.json --candidate 23e8868bf2d23a26f29f0fcc6a7dc592d429069b --claim r24_pdf_boundary_api34
python3 tools/r24_evidence_gate.py docs/verification/r24-final-api37-result.json --candidate 23e8868bf2d23a26f29f0fcc6a7dc592d429069b --claim r24_screenoff_api37
```

Metadata gates check names/context/counts, not cryptographic execution authenticity. API37 receipts cannot authorize API34 claims.
No root USB/IOMMU policy, all-hardware mitigation, global Tor/Tails routing, full Office/browser, release entitlement or PR merge/close is implied.

---

# Historical R23 — no-IME privacy try-out

Branch **`arena/f77cb2a6-osb`**. Open **`OSB/android`**, not the legacy Vite project.
[Current results and exact failures](R23-RESULTS.md) · [A–J record](R23-ENGINEERING.md).
**48/49 substantial testable subsets (97.96%);0/49 fully accepted,49 unfinished.**

**Exact native4bc7637:** privacy16/0/0 and memory24/0/0 passed; full main **121/1/0 FAILED**
an existing task Add workflow with IME visible and actual store empty. Both builds/JVM/contracts/lint passed.
This is evaluation code, not an all-green release. Do not substitute a successful rerun/different candidate.

## Android Studio Rabbit1 / laptop

Live pins: **AGP9.1.1, Gradle9.3.1, Kotlin2.2.21, maintained JDK17, actual SDK37.0**;
min34/compile37/target37. Use SDK Manager package **`platforms;android-37.0`**, not unqualified37.
Temporary documented `android.builtInKotlin=false`/`android.newDsl=false` retain external Kotlin;
GMD definitions use `localDevices`. No unsupported-API warning suppression or renamed36 SDK.
Rabbit1's [official compatibility table](https://developer.android.com/studio/releases) covers this AGP, but **Rabbit1 GUI itself was NOT executed here**.
Actual runtime checks above used managed API34, NOT API37/OEM/physical laptop testing.
Use a **disposable API34+ AVD and copied data**. Guest privacy is not protection from its host.

1. Open `android/`, sync with real SDK37.0/full JDK17; choose **privacy** application/debug run target.
   This is the separate **Fusion Offline Scratchpad** APK, not `app`/proDebug.
2. Open **Touch-only code pad (no keyboard)**. Opening it discards the ordinary scratchpad.
3. Tap/click digits instead of typing.64-digit ASCII cap; no input connection, clipboard, export,
   submission or saved draft. Keyboard-only loggers need not receive typed secret keys on this route;
   screen/pointer/AX/Android/laptop observers can still observe. Digits reshuffle on new pad/clear.
4. Default masked; **Reveal (5-second timer)**, delete, discard/reshuffle. Accepted changes renew a60s
   inactivity timer/action check. Focus/background/obscured input/recreation discard through actual lifecycle.
   Timers use the main looper, **not hard real-time scheduling or forensic erasure**.
5. **Review local input / exposure** reports limited counts/settings including UNKNOWN, not malware/
   hardware-keylogger detection or a safe verdict. It changes no setting. Turn off unneeded debugging
   through system settings AFTER evaluation; Studio/ADB currently needs it.

The ordinary scratchpad still trusts your chosen IME. Secure-window/recents/overlay controls do not
exclude privileged capture, trusted accessibility tools, host/hypervisor, camera, firmware or hardware.
No global anonymity, no-swap/erasure, Tails equivalence or lighter-whole-system claim.
Read [privacy/hardware PRD](R23-PRIVACY-PRD.md) before sensitive use.

## Main shell / explicit memory worksheet

Choose **app/proDebug** (or playDebug), open **Tasks and action plans (Automation)**, enable with copied
test data, choose **CSV selected-column worksheet**. Existing bounded CSV and **Import values-only XLSX…**
use explicit owned readonly-memory parser-input APIs, no named parser-input-file fallback. Save creates a
NEW literal XLSX copy and reads it back. Legacy `evaluateOwned*` APIs still intentionally use regular files.
Original provider files, created exports, OS paging and heap copies are not disk-free/erased. Main app HAS
network permission; the companion does not make it offline/anonymous. General XLSX/Office/LOK is not delivered.
Detailed historical R22 picker steps below still illustrate the bounded worksheet, but old disk/branch/
SDK/artifact statements describe that older candidate. **The task Add instability above remains unresolved.**

## Without Android Studio — actual host tests

No SDK is needed for the real Kotlin kernels and Office-reference tests. Tested **Linux x86_64**:

```sh
cd android
python3 tools/bootstrap_host_tools.py install  # explicit hash-pinned PyPI/npm downloads; no root
python3 tools/bootstrap_host_tools.py doctor
python3 tools/r23_lab.py host --tools "$HOME/.cache/fusion-host-r23"
python3 tools/inspect_usb_posture.py           # read-only Linux observations; UNKNOWN is not safe
```

Latest whole host pipeline passed149 Python methods,898161 R23 assertions/160000 touch words and bounded
R22/R21/Office references. Source checks are not Android compilation or hardware verification.
The optional cache has Kotlin2.2.21 and **OLD JRE17.0.9, NOT a JDK**: disposable host tests only, NOT
Gradle JAVA_HOME or a secure production runtime. npm scripts disabled; wheel/compiler integrity pinned;
registries/maintainers still trusted. No downloaded tool enters Git or an APK. Native doctor correctly
refused this local no-javac/no-SDK environment. Windows/macOS: manual maintained tool installation;
installer/native CLI is not claimed locally tested there.

## Without the IDE GUI — actual native SDK needed

Install a maintained full JDK17, Android command-line tools and an SDK whose licenses you have reviewed.
Set JAVA_HOME/ANDROID_HOME to real installations; don't use the host-test JRE cache.

```sh
# Real SDK package spelling and both build tools used by current doctor/CI:
sdkmanager 'platforms;android-37.0' 'build-tools;36.0.0' 'build-tools;35.0.0'
python3 tools/probe_android_sdk.py             # non-installing real stable+preview catalog observation
python3 tools/record_sdk_platform.py          # actual installed37.0 metadata/jar; not runtime proof
python3 tools/r23_lab.py android
python3 tools/r23_lab.py device --suite privacy --disposable  #16 cases
python3 tools/r23_lab.py device --suite memory --disposable   #24 cases
python3 tools/r23_lab.py device --suite full --disposable     #latest actual121/1 failure retained
# Direct build equivalent (Windows uses gradlew.bat):
./gradlew :app:assembleProDebug :app:assemblePlayDebug :privacy:assembleDebug :privacy:lintDebug :testing:r23Contracts
```

Use a real AVD/runtime with hardware virtualization; test suites modify test-app state. The local Android
CLI routes were NOT executed here; corresponding source commands actually ran in remote Linux/KVM CI.
JVM code does not turn the APK into a laptop-native desktop program.

APK paths after successful build:
- `privacy/build/outputs/apk/debug/privacy-debug.apk`
- `app/build/outputs/apk/pro/debug/app-pro-debug.apk`
- `app/build/outputs/apk/play/debug/app-play-debug.apk`

[Privacy APK run38871422549](https://github.com/jyvplay/OSB/actions/runs/38871422549), artifact `offline-privacy-companion`.
[Main APK build/run38871422566](https://github.com/jyvplay/OSB/actions/runs/38871422566), artifact `fusion-native-debug`;
**the build succeeded but this whole workflow FAILED its later task UI case**. Download on your laptop via
GitHub. This sandbox's blob/log redirects failed; no local APK/signature/RAM inspection is claimed.
Remote byte sizes/hashes are in [outcome JSON](verification/r23-outcome.json), not total footprint/Tails comparison.

```sh
# Exact scoped gates, not all-product acceptance:
python3 tools/r23_evidence_gate.py docs/verification/r23-final-privacy-result.json --candidate 4bc763700b63582107e5dbe979f9a185338f66f7 --claim r23_privacy_api34
python3 tools/r23_evidence_gate.py docs/verification/r23-final-memory-result.json --candidate 4bc763700b63582107e5dbe979f9a185338f66f7 --claim r23_memory_transport_api34
```

Current privacy policy requires16 names; `r23_prerequisite_api34` retains old14-case substrate scope.
Receipt gates check metadata/cases, not independent cryptographic authenticity or whole-product semantics.
Never overwrite failed receipts. R22 interop defaults to ignored `testing/build/r22/interop.json`;
`--report NEW_PATH` deliberately records a new artifact instead of replacing historical R22 evidence.
Manual GitHub Actions dispatch was403 for this integration; normal push-triggered verification worked.
No credentials requested, alternate token, PR merge/close or release was used.

---

# Historical R22 — native values-only XLSX import and laptop checks

**Evaluated fe8dca210642d42c0553c47ff4bccc16b5e17404: full119/0/0, focused10/0/0,
and CSV regression8/0/0 passed on managed API34; both debug builds/JVM/contracts/lint passed.**
[Debug APKs and full evidence](https://github.com/jyvplay/OSB/actions/runs/38854643235),
artifact `fusion-native-debug`. Later report/tooling commits are not separately native-tested candidates.
Exact candidate/run results and retained failures are in [R22-ENGINEERING.md](R22-ENGINEERING.md).
Do not infer current full acceptance from an older green APK or PR build. PR5 is open/unmerged;
`main` does not contain this work. **48/49 substantial subsets;0/49 fully accepted.**

## R22 laptop / Rabbit1 try-out

On your laptop use branch `arena/31a7965e-osb`. Open **OSB/android** in Android Studio Rabbit1
with compatible pinned **AGP8.13.2/Kotlin2.2.21/JDK17/SDK36**. The IDE GUI was not executed here.
Compile36 still differs from requested compile37. Use a **disposable API34+ AVD and copied test data**.

1. Run `app` / `proDebug`, open **Tasks and action plans (Automation)**, enable the session
   experiment, return to action plans if in Tasks, then **CSV selected-column worksheet**.
2. Use **Import values-only XLSX…** and choose a small plain single-sheet `.xlsx` from Downloads.
   Read the visible **VALUES-ONLY LOSS REPORT**. A successful import replaces the volatile draft.
3. Choose columns, e.g. `2`, then **Preview selected columns**. Review ALL displayed selected cells.
   Formula-looking strings remain text; numeric/boolean source encodings become raw text, not formatted dates.
4. **Save new literal XLSX…** creates a new document. Wait for **XLSX readback verified**.
   The import route never opens source for output; export trusts the provider to honor new-document semantics. Failure can leave a partial new destination.
5. Import the saved copy again through the picker. It now contains only selected columns;
   a previous wider selection should refuse rather than reuse the old source table.

Import limits: **2MiB input,1MiB expanded part,2MiB total expansion,256×32 cells,
4096UTF16 units/cell,131072-byte table reply**. Refuses formulas, macros/extra parts, rich text,
nonzero/custom/date number formats, hidden/custom rows, merges, unsupported ZIP/XML structures
(including comments/CDATA). This is intentionally much narrower than general XLSX compatibility.
Styles/layout/sheet-name/package metadata are discarded in the NEW text projection. Keep your originals.
Closing/recreation loses imported table/draft/pending picker snapshot. No persistent source URI grant.

Privacy: **network-enabled main app**, not offline companion. Selected cells/filename/provider/context
can identify people. Parent/Android/IME/accessibility/laptop are trusted. Input briefly touches private
temporary disk before readonly opening/unlinking; no secure erase. Cloud providers can use their own
network. No global/retroactive anonymity, whole-host protection or Tails footprint claim.

## R22 without Android Studio

From `android/`, configure real **JDK17** (`JAVA_HOME`) and **Kotlin2.2.21** (`KOTLINC`). Install
host verification packages separately, ideally in a local Python environment:

```sh
python3 -m pip install openpyxl==3.1.5 XlsxWriter==3.2.5
python3 tools/r22_lab.py doctor
python3 tools/r22_lab.py host
# Actual production Kotlin compilation + ZIP/XML/XLSX interop + R21 regression + Python tooling.
# Optional disposable sample; refuses an existing destination:
python3 tools/make_r22_sample.py r22-example.xlsx
```

No Android SDK is needed for those host checks; they are NOT Android substitutes. The sample script
was run here and its real XLSX accepted by the actual Kotlin importer. To use it on an AVD, drag the
file into the emulator or use a locally installed ADB:

```sh
adb push r22-example.xlsx /sdcard/Download/
# With an actual Android SDK, without the IDE GUI:
./gradlew :app:assembleProDebug :app:assemblePlayDebug :testing:r22Contracts
./gradlew :app:pixel6Api34ProDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.fusionDisposable=true -Pandroid.testInstrumentationRunnerArguments.class=dev.fusion.shell.R22PlatformDeviceTest,dev.fusion.shell.R22BoundaryDeviceTest,dev.fusion.shell.R22XlsxUiDeviceTest
```

Windows uses `gradlew.bat` and platform-appropriate JAVA_HOME/KOTLINC paths. No local SDK/ADB was
available in this sandbox; actual Android execution used GitHub-managed API34. APK download from a
completed run with `fusion-native-debug` (run links in engineering report):

```sh
gh run download 38854643235 -n fusion-native-debug -D ../r22-apks
# Locate the pro-debug APK in the downloaded artifact tree, then:
adb install -r PATH_TO_PRO_DEBUG_APK
```

Push-triggered native workflows run on the session branch. Manual dispatch previously returned403
for the privacy workflow; no repeat or new privacy pass is inferred. `r22_lab.py dispatch-native`
is provided for appropriately authorized installations, not claimed executed here. Never share credentials.
Collect completed runs only, using a fresh prefix:

```sh
python3 tools/collect_native_evidence.py ACTUAL_RUN_ID NEW_PREFIX --focused
python3 tools/r22_evidence_gate.py docs/verification/NEW_PREFIX-result.json --candidate EXACT_40_CHARACTER_SHA --claim r22_import_api34
# Omit --focused for the full native workflow; claim r22_full_api34 requires full scope.
```

Offline receipt policy is not cryptographic authentication. Named cases, whole workflow outcome and
exact candidate matter. Full/focused suites overlap. No physical/API36/37/AX/legal/full-Office/browser
acceptance follows. Original compositor/browser/Office gates remain closed.

---

# Earlier R21 evaluated build and try-out

## R21: CSV worksheet try-out

**Evaluated candidate b96f060:** full109/109, R21 focused8/8 and Task/Notes7/7 passed
on managed API34; both debug builds/JVM/contracts/lint passed. [Download debug artifacts](https://github.com/jyvplay/OSB/actions/runs/38849172796)
from `fusion-native-debug`. Later report/tooling commits are not separately native-tested
application candidates. This is not physical/AX/full-product release acceptance.

On your laptop, check out `arena/31a7965e-osb` first: PR #5 is not merged, so `main` does not contain these R21 changes.
Open `android/` in Rabbit1 with the pinned AGP8.13.2/Kotlin2.2.21/JDK17/SDK36 setup below.
The IDE GUI itself has not been exercised here. Use a disposable API34+ AVD and test data.

1. Run `app` / `proDebug`; open **Tasks and action plans (Automation)** and enable the
   experiment for this session. Stay in action plans (Back to action plans if in Tasks).
2. Choose **CSV selected-column worksheet**. This opens a real native Activity, not a browser page.
3. Paste, for example:

   ```csv
   Name,Value,Private
   Alice,=1+1,omit-this
   ```

4. Enter `2` under columns, then **Preview selected columns**. The table is parsed in a
   fresh isolated child; every selected cell is shown. Formula-looking text stays text.
   `1,2` deliberately keeps Name too—it is not anonymous. Editing source/selection invalidates
   the previous preview. Display escapes controls without changing exported cell contents.
5. **Save new literal XLSX…**, choose a new DocumentsUI destination, and wait for an explicit
   **XLSX readback verified** result. Open a disposable copy in your actual spreadsheet app
   to test fidelity there. Failure may leave an empty/partial destination; inspect before retry.

Limits:64KiB UTF8,256 rectangular rows,32 columns,4096 UTF16 units/cell. No delimiter/type
sniffing; no original Office package opening, formulas, styles or macros. Unsupported XLSX
text is refused. Draft is session-only: closing/recreation loses it; an outstanding export
snapshot is not restored. Do not use your only copy of important data.

**Privacy boundary:** this is the network-enabled MAIN app, not the separate offline APK.
CSV input briefly touches a private temporary file before readonly opening/unlinking. That is
not secure disk erasure. Parent, Android, IME/accessibility, provider and laptop remain trusted.
Selected content, filenames and sharing context may identify you. The original browser/Office
entry gates are not unlocked by this bounded worksheet feature.

From `android/`, without an IDE GUI:

```sh
python3 tools/r21_lab.py doctor
# Real installed JDK17 + Kotlin2.2.21; make openpyxl3.1.5 and z3-solver importable:
python3 tools/r21_lab.py host
python3 tools/run_r21_stress.py
# With a real Android SDK, not available locally in this sandbox:
./gradlew :app:assembleProDebug :app:assemblePlayDebug :testing:r21Contracts
./gradlew :app:pixel6Api34ProDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.fusionDisposable=true -Pandroid.testInstrumentationRunnerArguments.class=dev.fusion.shell.R21BoundaryDeviceTest,dev.fusion.shell.R21CsvUiDeviceTest
```

Windows uses `gradlew.bat`; set JAVA_HOME/KOTLINC according to your installed tools.
Actual push-triggered native CI is `.github/workflows/android-r21.yml` (focused) and
`android.yml` (full). `r21_lab.py dispatch-native` is provided but not claimed executed.
A separate manual privacy dispatch attempted here returned GitHub403 (integration permission);
no run or pass was inferred. Push CI still works; manual dispatch may require reconnecting
GitHub in Arena with the needed permissions. No credentials should be shared in chat.

Collect only completed actual run IDs with `collect_native_evidence.py RUN_ID NEW_PREFIX`
(`--focused` for the R21 workflow). Do not overwrite historical failed receipts or conflate
focused with full. See [R21-ENGINEERING.md](R21-ENGINEERING.md) for exact candidates/outcomes,
A–J contract, privacy research and residuals. **48/49 substantial subsets;0/49 fully accepted.**

## Earlier R20 build/evidence baseline

Open **OSB/android** in Android Studio Rabbit1 with compatible AGP8.13.2,
Kotlin2.2.21, JDK17 and SDK36 tooling. Gradle/Android execution is remote;
the Rabbit1 desktop IDE itself was not run here. Current min/compile/target34/36/36
still differs from requested compile37. These are evaluation builds, not release clearance.

**48/49 (97.96%) substantial executable subsets; 0/49 fully accepted (0%).**
LOK lacks an established subset. The extra module is a separate no-INTERNET APK,
not a renamed feature. See [R20-ENGINEERING.md](R20-ENGINEERING.md) for exact
A–J evidence and the [R20 instructions below](#r20-laptop-try-out-and-checks-without-android-studio).

**Candidate-specific evidence:** main eb7f2c4 passed 100/100; focused 3db7fdb
passed 7/7; privacy 8b634f4 passed 6/6 but later 62b5c2f failed 5/6 at rejection
feedback. Later full runs 3db7fdb and 62b5c2f each failed 99/100 with Compose cross-thread
semantics errors (Notes input / editor 200%-font). Current f42193d privacy feedback repair passed **6/6**; its main ZIP metadata run
passed **101/101**, with both debug builds/JVM/contracts/lint green.
No earlier pass is inherited.
[Evaluated f42193d privacy APK artifact](https://github.com/jyvplay/OSB/actions/runs/38845693136)
is `offline-privacy-companion` (debug APK inside); 2,486,434 compressed bytes.
[Evaluated f42193d main APK artifacts](https://github.com/jyvplay/OSB/actions/runs/38845693182)
are `fusion-native-debug`. Later report/tooling commits do not inherit an exact-candidate native pass. Download artifacts only from the specifically evaluated
run linked in the R20 ledger. All are debug evaluation artifacts, not release clearance.
The original task-add timeout and Notes visibility root cause remain undiagnosed;
subsequent green runs do not prove general UI stability. Use disposable data only.

## Try-out: reversible tasks, merge and export

1. Use **proDebug** on a disposable API34+ AVD and acknowledge the pilot warning.
2. Open **Automation** from the horizontally scrollable shelf; enable this session with test data,
   then choose **Local tasks**. Action plans remain the separate default view.
3. Add a title and optional date such as2028-02-29. Check/uncheck completion.
4. **Edit task** fills title/date; **Save task changes** preserves ID/completion. **Cancel edit**
   does not change stored data. A stale edit blocks and retains input for review.
5. **Archive task** retains the record; choose **ARCHIVED** and **Restore task** to return it.
   Archived records still count toward128; no permanent deletion is implemented.
6. Use the literal title query, ACTIVE/OPEN/COMPLETED/ARCHIVED controls and date/insertion order.
   View changes do not save a new task revision. Recreate/reopen and reload to inspect persistence.
7. Edit and save, then **Undo last edit/import**: prior task data returns at a new revision, not a
   rolled-back revision. No-op edit/import retains a still-valid earlier undo. Reload/recreation
   clears the session token; this is not durable or multi-level history.
8. **Export all tasks — new document** captures the currently loaded snapshot as plaintext FTS.
   Reload first if another window changed the store. Choose a new destination in the system picker.
   Successful status means exact reopen/readback, not provider honesty or power-loss durability.
9. **Open task file for merge preview** reads FTS1/FTS2 without changing local tasks. Inspect new/
   identical counts; **Confirm append-only merge** appends new UUIDs only. Conflicting existing IDs,
   stale previews or capacity/headroom violations refuse the whole plan. Imported revision is not
   adopted. Discard leaves data unchanged; a changed confirmed merge can be immediately undone.
10. **Export task report — new XLSX** produces Active/Archived sheets with IDs, literal titles,
    date strings and booleans. It is a report, not an FTS backup or full Office engine. Some XML/
    escaped-string text cannot be represented and is refused; use FTS rather than silently losing text.

Cancellation/recreation/provider errors may leave an empty or partial **new** document. Do not
interpret an error as rollback or delete an unrelated destination. Pending export state and undo
are session-only. Task input remains capped at 128KiB; generated report readback is separately
capped at 512KiB. No storage/provider deadlines, encrypted task database or all-provider guarantee.

Limits:128 active+archived tasks,256 UTF16 title/query units, date-only years0001..9999,
plaintext private no-backup storage, same-process lock, no reminders/recurrence/calendar sync.
Loading/no-op preserves FTS1 bytes; explicit mutation writesFTS2, unreadable by older app versions.
No downgrade backup is provided. If saving cannot be confirmed, input is retained and mutations
block until reload; an error may follow publication, so never assume rollback or blindly retry.
The owned-sheet API is an integration API/tested worker path, **not** a new complete Office UI.

## Reproduce checks

From `android/`, set JAVA_HOME to JDK17 and KOTLINC to Kotlin2.2.21:

```sh
LC_ALL=C.UTF-8 python3 tools/run_r16_contracts.py
LC_ALL=C.UTF-8 python3 tools/run_r17_contracts.py
LC_ALL=C.UTF-8 python3 tools/run_r18_contracts.py
LC_ALL=C.UTF-8 python3 tools/run_r19_contracts.py
python3 tools/run_r19_interop.py
python3 tools/run_r19_report_stress.py
python3 tools/run_r18_interop.py
python3 tools/run_r17_interop.py
python3 tools/check_project.py
python3 -m unittest discover -s tools -p 'test_*.py'
./gradlew :app:assembleProDebug :testing:r17Contracts :testing:r18Contracts :testing:r19Contracts
```

The stress script uses Linux resource accounting; run it on a Linux host.
R19 readers require host-only openpyxl 3.1.5; R13 calendar regression uses calibrated icalendar 7.3.0.
`tools/prove_r19_models.py` requires z3-solver 5.3.0.0 and checks conditional models, not Kotlin proofs.

Use `gradlew.bat` on Windows. R16's independent socket verifier additionally needs host-only
cryptography46.0.3 (`PYTHONPATH` as in the historical R15 instructions below).
Native tests require explicit `fusionDisposable=true` and a disposable emulator, as documented
below. Never point reset/instrumentation routines at personal app data.

Optional Linux-host conditional-model verifier (NOT an implementation proof):
`python3 -m pip install --target "$HOME/.cache/fusion-provers" z3-solver==5.3.0.0`, then
`PYTHONPATH="$HOME/.cache/fusion-provers" python3 tools/prove_r18_models.py`.
This also executes a labeled Linux writable-alias experiment; it is not Android verification.

The baseline module now ships855 actual measured first-party startup/Files ART rules.
Generation, packaging and profile-required startup passed atbb338a5/run38697143892.
See `baselineprofile/PROVENANCE.md`; no physical speedup/budget acceptance is claimed.

---

## Historical R15 instructions and receipts (superseded census)

# R15 current build and executable entry points

Open **OSB/android** in Android Studio Rabbit1 with tooling compatible with
AGP8.13.2, Kotlin2.2.21, JDK17 and SDK36. Current min/compile/target34/36/36
still deviates from requested compile37. These are incomplete debug builds, not release clearance.

**44/48 modules (91.67%) have bounded executable subsets;0/48 are fully accepted.**
See [R15-ENGINEERING.md](R15-ENGINEERING.md) for the A–J review, six mechanisms,20 false-solution
shapes, counterexample/repair and exact remaining gaps. Inherited751c0f4/run38690692400 finished
64/64 API34 cases PASS. R15 **eeb93a4/run38692920167** then passed **71/71 API34 cases**,
both debug flavors, invoked JVM/contracts and lint. The seven new cases include actual child-UID
arithmetic, refusal paths, Android Bitmap comparison and platform AEAD. This is not physical or
full-product acceptance. Expanded host checks/docs postdate that native candidate; production and
native-test sources remain unchanged. Download debug APKs from the run's `fusion-native-debug`
artifact, or build locally; no APK was manually installed or visually reviewed in this workspace.

## What can be exercised

- Existing Capture flow now reports its observed recorder-active elapsed interval after finalization.
  This is explicitly not encoded-media duration or proof of playback, hardware encoding or timing.
  Existing consent/notification/lock/thermal protections remain unchanged.
- R15 public Kotlin APIs: Lens geometry, pause/resume accounting, plaintext transcript archives,
  authenticated Vault envelopes, canonical launch targets and bounded arithmetic frames.
  They are not complete new camera/Vault/Messages/launch workflows. No Vault/notification/Office
  gate was unlocked. The Vault API requires a caller-protected random32-byte key, not a password.
- Actual isolated arithmetic is tested through `FormatLease.evaluateSheet` with a test-owned
  FSC1 file. It is not an Office document format; no external Office engine is bundled.

## Reproduce host checks

From `android/`, configure JAVA_HOME for JDK17 and KOTLINC for Kotlin2.2.21:

```sh
LC_ALL=C.UTF-8 python3 tools/run_r15_contracts.py
python3 -m pip install --target "$HOME/.cache/fusion-r15-oracles" cryptography==46.0.3
PYTHONPATH="$HOME/.cache/fusion-r15-oracles" python3 tools/run_r15_interop.py
python3 tools/check_project.py
python3 -m unittest discover -s tools -p 'test_*.py'
./gradlew :testing:r15Contracts
```

Fresh local results:5510 assertions,700 cross-language checks,74 Python tests.
The700 include100 Java→OpenSSL and100 OpenSSL→Java authenticated-envelope cases.
The host dependency is outside the repository/APK. R14 was rebuilt/rerun:1893 assertions and600
cross-language cases passed. None of these commands substitutes for Android/device evidence.

The native commands below remain applicable and now include seven R15 cases: actual Bitmap
transforms, elapsed clock, file/crypto, Android URI and isolated parser success/refusals.
Explicit disposable-emulator consent is mandatory; never reset personal app data for tests.

---

# Historical R14 build and try-out entry points

Open **OSB/android** in Android Studio Rabbit1 with tooling compatible with
AGP8.13.2, Kotlin2.2.21, JDK17 and SDK36. Current min/compile/target34/36/36
remains a deviation from requested compile37. This is an incomplete debug application,
not a production beta or complete Office engine.

**39/48 modules (81.25%) have bounded executable subsets;0/48 are fully accepted.**
Full A–J evidence, six constructions, repairs and limitations: [R14-ENGINEERING.md](R14-ENGINEERING.md).
Inherited native run38687376592:55 PASS/2 FAIL; calendar repair passed, editor redo/recreation
and large-font threading failures remained in that inherited run.
R14 de06095/run38689433735:both debug flavors/JVM/contracts/lint succeeded; native63 PASS/1 FAIL.
All six new kernel cases and both editor cases passed in that run. The Settings test incorrectly
assumed BALANCED instead of the actual COMPACT default. Its corrected fixture and later UI changes
require a fresh native run; no full-product acceptance follows.

## Try the actual changes

- **Files:** choose a folder with the Android picker. Use literal folder filtering, Name or Largest.
  Directories remain first. No file mutation is enabled. Metadata query limit5000 items;
  underlying provider enumeration is not time-bounded. Provider size0 may mean unknown.
- **Settings:** select a different density, review, Cancel or Apply reviewed density.
  The existing desktop geometry changes only on explicit apply. A changed current value
  conflicts instead of being overwritten. The displayed status is not a durability receipt.
- Primary-color foreground uses the new opaque sRGB contrast component. This does not
  certify all controls, text, translucent surfaces, displays or accessibility.
- SpatialFocus, NoticeLedger and DecimalSheet are tested public Kotlin APIs, not newly completed
  global focus, notification or Office workflows. Notification access remains disabled.

## Reproduce R14 checks

From `android/`, configure JAVA_HOME for JDK17 and KOTLINC for Kotlin2.2.21:

```sh
LC_ALL=C.UTF-8 python3 tools/run_r14_contracts.py
python3 tools/run_r14_interop.py
python3 tools/check_project.py
python3 -m unittest discover -s tools -p 'test_*.py'
./gradlew :testing:r14Contracts
```

Fresh host results:1893 assertions,600 cross-language cases,74 Python tests.
The interop runner requires a fresh compiled receipt and verifies source/JAR hashes.
Python references are same-author differential checks using maintained decimal/AST;
no actual Office engine or Android runtime is inferred. R13 was rebuilt and rerun too:
503 host assertions and404 real-reader/differential cases passed.

Native assemble/lint/disposable-emulator commands below remain applicable; they include six
R14 kernel/adapter cases and actual density review/cancel/apply UI. Never run destructive fixture
reset on personal data. No physical/API37/AX/legal/isolation acceptance is inferred from host checks.

---

# Historical R13 build and bounded-subset entry points

Open **OSB/android** in Android Studio Rabbit1 with tooling compatible with
**AGP8.13.2, Kotlin2.2.21, JDK17 and SDK36**. IDE branding is not compatibility proof.
Current min/compile/target34/36/36 remains a deviation from requested compile37.

**34/48 modules (70.83%) have qualifying bounded executable subsets;0/48 are fully
accepted.** This is not a production-beta or full Office implementation. Exact evidence,
six mechanisms, counterexamples and residual gaps: [R13-ENGINEERING.md](R13-ENGINEERING.md).
The inherited API34 run38684747487 has49 PASS/2 FAIL, despite a passing new plain real-IME
test. R13 candidatef0cc82d/run38685789036 has a successful build/JVM/contracts/lint job;
its managed-device suite finished55 PASS/2 FAIL (editor replacement and the calendar-test
regex counterexample). The corrected calendar assertion and later control-character hardening
require their own native run. See retained receipts in the R13 report; this is not a green build.

## Try actual app-local automation

1. Build/run `proDebug` on a disposable API34+ emulator using the commands below.
2. Open Automation; acknowledge the existing experimental/test-data consent gate.
3. Enter `workspace 2` and `open calculator` on separate lines, then explicitly Execute.
4. The host dispatches approved actions to its actual desktop state. No host means Execute
   is unavailable; malformed programs are rejected before dispatch. A later dispatch failure
   stops the plan and retains earlier effects. Plans are session-only, not scheduled jobs.

Terminal now uses a bounded quoted argument lexer for its builtins. `echo "two words" ''`
exercises quoted/empty arguments; this is **not** a PTY or POSIX shell. Calendar export,
content receipts and literal XLSX creation are tested public Kotlin APIs, not newly finished
end-user workflows. No full Office import/edit/render/recalc, journaled move or CalendarContract
acceptance is claimed.

## Reproduce R13 host checks (not Android execution)

From `android/`, configure `JAVA_HOME` to JDK17 and `KOTLINC` to the Kotlin2.2.21 compiler
executable. Host dependencies are installed outside the repository and are not APK dependencies:

```sh
LC_ALL=C.UTF-8 python3 tools/run_r13_contracts.py
python3 -m pip install --target "$HOME/.cache/fusion-r13-oracles" openpyxl==3.1.5 icalendar==7.3.0
PYTHONPATH="$HOME/.cache/fusion-r13-oracles" python3 tools/probe_calendar_reader.py
PYTHONPATH="$HOME/.cache/fusion-r13-oracles" python3 tools/run_r13_interop.py
python3 tools/native_source_audit.py --check
python3 tools/module_testability.py --check
python3 -m unittest discover -s tools -p 'test_*.py'
# Gradle's JVM source set (requires the configured Android build environment):
./gradlew :testing:r13Contracts
```

Fresh results:503 host assertions,404 independent-reader/differential cases,74 Python tests.
The interop runner checks compiled receipt hashes, including actual JARs. Rebuild first; stale
or missing receipts/binaries are not treated as a pass. The older icalendar6.3.2 reader loses
literal backslashes on its own writer's output; its failing calibration is retained, not hidden.
Run the same probe in a separate host environment pinned to6.3.2 to reproduce that witness.

Native assemble, lint and disposable-emulator commands below remain applicable and include
`R13SubsetDeviceTest`, actual automation→Calculator UI dispatch and real IME observations.
No local SDK/device execution or physical acceptance is inferred from the host commands.

---

# Historical R12 continuation build and try-out entry points

Open **OSB/android** in Android Studio Rabbit1 with a version compatible with
**AGP8.13.2, Kotlin2.2.21, JDK17 and SDK36**. The IDE name itself is not compatibility
proof. Current min/compile/target34/36/36 remains a documented deviation from37.

[Latest compiled native run38682595367](https://github.com/jyvplay/OSB/actions/runs/38682595367)
at0a23219 has both debug flavors, invoked unit/contracts and lint passing, with the
`fusion-native-debug` APK artifact. **Native result:49 PASS/1 FAIL/0 SKIP**; the stronger
Editor200%-font long-text test timed out awaiting actual IME visibility. This remains unresolved.
These are incomplete debug builds with a known failing test, not a cleared production beta.

## Try the new read-only subtitle inspector

1. Build/run `proDebug` on a disposable API34+ AVD and open **Media**.
2. Acknowledge its experimental/test-data scope. Choose **Inspect subtitle file…**.
3. Pick a real UTF8 `.srt` file using the Android system document picker. The subtitle path
   does **not** require `READ_MEDIA_IMAGES`; the separate image-inventory action does.
4. Enter an integer time in milliseconds. Active overlapping cues are shown literally,
   with end times excluded; up to8 are previewed with an explicit count.
5. Invalid/unsupported/oversized input is refused without changing the source; prior preview remains.

This is inspection, not playback. It supports the strict consecutive-index/timecode subset in
`R12B-ENGINEERING.md`,≤1MiB. No styles, media-clock sync, durable grant, background player,
provider wall-time guarantee or restoration of the preview after Activity death is claimed.

New settings/image/backup/launcher APIs are tested bounded components, not newly exposed full
end-user workflows. Backup restore remains blocked. Run the actual contracts from `android/`:

```sh
./gradlew :testing:subsetContracts
# With Kotlin2.2.21 and Java17 configured (separate module compilation):
python3 tools/run_subset_contracts.py
python3 tools/run_subset_interop.py
python3 tools/probe_text_regression.py
python3 -m unittest discover -s tools -p 'test_*.py'
```

The same original managed-device command below also runs the new Bitmap, subtitles/UI,
settings, backup-content, CSV and runtime-inventory cases. Do not infer native results from
these host commands. [Current evidence and exact revisions](R12B-ENGINEERING.md).

## Try the actual Notes implementation

1. Choose the `proDebug` app variant and start a disposable API34+ AVD.
2. Run `app`, open Notes, and enable its test-data experiment if prompted.
3. Enter a name and draft text. `Checkpoint draft` requests a local recovery
   checkpoint; wait for the **confirmed by exact readback** status. Unconfirmed
   edits are not promised durable. The checkpoint is separate from saved Markdown.
4. `Save note` explicitly publishes the saved note. Trash/restore acts on that saved
   revision, refuses conflicts, and is not version history or backup.
5. For your own manual lifecycle check, stop/relaunch the app and reopen Notes
   (re-enable test-data consent if requested). This is a suggested user check,
   not a claim that this session manually installed/reviewed the APK.

Recovery is plaintext app-private data in `no_backup`, not encrypted backup. An
unreadable checkpoint blocks edits rather than overwriting it; preserve and inspect
`no_backup/notes-recovery-v1.bin` in Device Explorer before attempting recovery.
Browser remote surfaces are debug instrumentation experiments, not an unlocked
product browser. Office and the original broader missing features remain blocked.

## Reproduce verification

From `android/` with the actual SDK/JDK installed:

```sh
python3 tools/check_project.py
python3 tools/module_testability.py --check
python3 -m unittest discover -s tools -p 'test_*.py' -v
./gradlew :app:assembleProDebug :app:assemblePlayDebug
./gradlew :testing:noteDraftContracts :testing:noteTrashContracts
./gradlew :app:lintProDebug :app:lintPlayDebug
# This requires a running disposable AVD:
./gradlew :app:installProDebug
# Managed device: regular Google API34 image in R12, not aosp-atd.
# Explicit opt-in permits test file resets and temporary system font-scale changes.
./gradlew :app:pixel6Api34ProDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.fusionDisposable=true \
  -Pandroid.testInstrumentationRunnerArguments.notClass=dev.fusion.shell.VisualCaptureTest
python3 tools/report_device_results.py
```

Host-only checks (not Android substitutes), with real Java17/Kotlin2.2.21 paths:

```sh
LC_ALL=C.UTF-8 python3 tools/run_jvm_contracts.py
LC_ALL=C.UTF-8 python3 tools/run_r12_interop.py
python3 tools/probe_no_fork_cpu.py # child-only unprivileged Linux x86_64 experiment
```

The full A–J evidence ledger, exact native candidates and APK links are in
[R12-ENGINEERING.md](R12-ENGINEERING.md). Testability is23/48 (47.92%); full acceptance
is0/48 (0%). A debug APK or green subset suite is not production-beta clearance.

---

# Historical R11B build and test entry points

Open **OSB/android** in Android Studio Rabbit1 with compatible AGP/JDK17 and SDK36.
The IDE name is not a compatibility proof. Current min/compile/target34/36/36 is an
explicit deviation from the requested compile37/target37 migration. No native
arm64/4K+16K engine payload has been validated.

[Run38670396374](https://github.com/jyvplay/OSB/actions/runs/38670396374) at1ea1faa
passed both debug flavors, invoked unit/contracts, lint and APK upload; API34
**29/29 named cases passed**, including six elementary boundary cases and actual
200%-font Notes trash/restore. Download `fusion-native-debug` from that run for
these incomplete debug APKs (not production/beta clearance). No APK was manually
installed/visually reviewed here. See [R11B](R11B-ENGINEERING.md) for exact limits.

```sh
python3 tools/native_lab.py doctor
python3 tools/check_project.py
python3 -m unittest discover -s tools -p 'test_*.py' -v
# Set JAVA_HOME and KOTLINC to actual Java17/Kotlin2.2.21 installations:
python3 tools/run_jvm_contracts.py
python3 tools/run_r11_interop.py
# Full Android prerequisites required:
./gradlew :app:assembleProDebug :app:assemblePlayDebug
./gradlew :testing:browserSessionContracts :testing:noteLinkContracts
./gradlew :app:lintProDebug :app:lintPlayDebug
# Disposable opted-in managed device only; mutates test-owned files and system font scale:
./gradlew :app:pixel6Api34ProDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.fusionDisposable=true \
  -Pandroid.testInstrumentationRunnerArguments.notClass=dev.fusion.shell.VisualCaptureTest
python3 tools/report_device_results.py
```

The reporter reads generated JUnit XML and emits named cases through Checks even
when tests fail; missing XML is **NO_EXECUTION_EVIDENCE**, never a pass. Visual
capture/human AX, physical-device performance and legal/license gates remain open.

---

## Historical R10 verification additions

Current acceptance remains0/48 modules. See [R10 engineering record](R10-ENGINEERING.md).
With a real JDK/Android SDK use the existing both-flavor build/lint/device commands;
the current host JRE/Kotlin setup only compiles the pure libraries.

```sh
python3 tools/check_project.py
python3 -m unittest discover -s tools -p 'test_*.py' -v
# Host Kotlin2.2.21 + Java17 runtime; JAVA_HOME and KOTLINC must point to real tools:
python3 tools/run_jvm_contracts.py
python3 tools/run_text_interop.py --report docs/verification/r10-text-interop.json
# With Gradle prerequisites/dependencies available:
./gradlew :testing:encodingDiffContracts :testing:ooxmlGuardContracts
```

The encoding/diff/ZIP contracts do not execute Android providers, process isolation,
UI or Office engines. `ci_annotations.py` exposes bounded failed-assembly excerpts
through GitHub Checks; full `assemble.log` is also uploaded. Existing run38648345516
failed assembly after successful SDK setup. No compiler cause is inferred from the
Node-action warning. Subsequent run38652913588 passed both-flavor assembly after
two compiler repairs, but failed the unit-test step; contracts/lint/upload skipped.
All Gradle CI phases now use `tools/run_gradle_ci.sh` for bounded API-visible errors.
Final run38656329782 at86b4621 passed both-flavor assembly, all invoked JVM tests,
contracts, both-flavor lint and debug APK upload. Download `fusion-native-debug`
from https://github.com/jyvplay/OSB/actions/runs/38656329782 . These are debug builds
of an incomplete product, not release/beta clearance. Managed-device job skipped;
no artifact was installed or visually inspected here.

---

# Native build and verification

## Toolchain and deliberate deviations

| Setting | Current value |
|---|---|
| Root | `OSB/android` |
| Gradle / wrapper | 8.13, scripts + upstream JAR committed |
| Android Gradle Plugin | 8.13.2 |
| Kotlin + Compose compiler plugin | 2.2.21 |
| Gradle Java runtime | JDK 17 |
| min / compile / target SDK | 34 / **36** / 36 |
| Flavors | `play` (`dev.fusion.shell.play`), `pro` (`dev.fusion.shell.pro`) |
| Initial install variant | `proDebug` |

**compileSdk 37 from the master spec is not yet applied.** API37 SDK/toolchain
availability and the claimed platform APIs have not been verified here. Keeping
an explicit API36 baseline is safer than writing guessed API37 calls or adding
an unsupported compile SDK override. Upgrade as a recorded M8 change, then run
the LAN permission, read-only native libraries and memory-limiter matrix.

No binary NDK payload is added in this pass. ABI/page-size compliance remains a
future native-library gate, not something established by an x86_64 emulator run.

The IDE marketing name is not a build input. If Rabbit 1 reports AGP incompatibility,
use its supported stable upgrade path; do not silently downgrade the project or
install a preview SDK to make the warning disappear. JDK 17 is the pinned CI JVM.

## R7 real laptop build/visual workflow

See [NATIVE-LAB.md](NATIVE-LAB.md) for prerequisite checks, both-flavor build/lint,
guarded actual emulator tests and native screenshots at100%/200%. This workflow
has **not** been Android-executed here. Direct smoke tests require explicit
`fusionDisposable=true` on a disposable AVD; visual capture requires its own run
arguments. Do not execute reset tests on a personal device.

## Setup

- File → Open → `android/`; trust the project only if you trust its sources.
- Settings → Build Tools → Gradle → Gradle JDK: 17.
- SDK Manager: Android SDK Platform 36, Build Tools 36.0.0 and 35.0.0, Emulator,
  Platform Tools and an API34+ 64-bit image. Studio writes `local.properties`.
- Sync requires Google's Maven repository, Maven Central and Gradle distribution
  access from your laptop. These repositories are not accessible to this sandbox.
- Build Variants → `app` → `proDebug` (or `playDebug` without SMS permissions).
- Run on an API34+ tablet/resizable AVD. HOME selection is optional; do not make
  an unverified launcher your only home app. The existing root web app is unrelated.

```sh
./gradlew --version
./gradlew :app:assembleProDebug :app:assemblePlayDebug
./gradlew :app:lintProDebug :app:lintPlayDebug
./gradlew :core:documents:test :core:windowing:test :core:index:test :core:commands:test
./gradlew :core:security:testDebugUnitTest :format:native:test :format:archive:test
./gradlew :format:ooxml:test :format:psd:testDebugUnitTest :cast:dlna:testDebugUnitTest
./gradlew :cast:airplay:test :cast:castv2:test :feature:beam:testDebugUnitTest
./gradlew :feature:together:testDebugUnitTest :testing:test
./gradlew :app:pixel6Api34ProDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.fusionDisposable=true -Pandroid.testInstrumentationRunnerArguments.notClass=dev.fusion.shell.VisualCaptureTest
```

Use `gradlew.bat` in Windows PowerShell. Managed emulators require virtualization
and may be less convenient than Run → selected AVD, followed by
the guarded workflow in `NATIVE-LAB.md`. Do not run tests against a daily
use phone. Debug builds can expose development diagnostics.

`python3 tools/check_project.py` checks CSV freshness, normalized IDs, module
references, XML, catalog conventions, wrapper hash and selected privacy contracts.
It does **not** run Kotlin, Gradle, Android or network receivers.

## Release deliberately blocked

`prePlayReleaseBuild` and `preProReleaseBuild` depend on
`verifyReleaseReadiness`, which calls `tools/requirements.py --release`.
It currently fails. A source file, fake receiver test or a green debug build must
not authorize a production release. Windows release workers can specify
`-PfusionPython=python`; lack of Python also fails closed.

The gate is intentionally conservative for the full suite. A future P1-only
release needs an explicitly scoped release profile, real P1 acceptance evidence,
resolved dependency/license review, security review, and disabling later gated
surfaces—not just changing this command to ignore failures. R8/profile generation,
signed commerce and production signing remain uncompleted release work.

## Editor acceptance on a real provider

Use disposable test documents, not irreplaceable originals. Test on API34 and 36
with local Downloads and at least one non-local SAF provider; extend to 37 later.

1. Open UTF-8 with CRLF, BOM, non-ASCII and emoji. Save a Copy and compare bytes
   externally; original must stay unchanged. Invalid UTF-8 must report an error.
2. Open two files with the same display name and different URIs. Edit each,
   switch tabs, minimize/close/reopen the editor, rotate/recreate Activity.
3. Edit → Save a Copy → cancel. No clean-state transition. Repeat, edit while the
   picker is pending (multi-window), save: only that snapshot is clean.
4. Background while picker is open, then `adb shell am kill dev.fusion.shell.pro`.
   Return through the picker. R5 deliberately refuses automatic write after process
   restoration; inspect any partial destination, then explicitly retry the frozen
   payload through a fresh picker. Rotation without process loss is a separate test.
   Never substitute an empty payload. Android/OEM result delivery must be recorded.
5. Edit, wait >500 ms idle, kill/relaunch. Restore exact draft. Last 500 ms are not
   promised; OS kill/IO errors must never be disguised as an external save.
6. Revoke a provider grant, exhaust destination storage, disconnect a network
   provider mid-write/readback. Draft stays dirty, original stays unchanged,
   potential partial destination is reported. No blind delete/overwrite retry.
7. Dirty Close tab → Keep editing retains text; explicit Discard removes only
   that draft. Close editor window → Keep drafts and close must flush first.
8. Test TalkBack focus/reading, keyboard-only open/edit/save/close and 200% text.
   These tests are still **pending**, not an AX-01 pass.

AtomicFile protects local recovery replacement; a checksum detects accidental
corruption, not malicious tampering. SAF does not expose a universal atomic
replace primitive. Readback demonstrates current provider bytes, not durable
cloud replication or receiver/network integrity.

## Additional R7 contracts

```sh
./gradlew :testing:searchAndGateContracts :testing:windowRestoreContracts
# Linux/POSIX host only; other hosts must use the real Android storage test:
./gradlew :testing:notesContracts
```

These Gradle tasks are authored, unrun here. The production JVM sources were
actually compiled/executed with `tools/run_jvm_contracts.py`; see R7 evidence.
Native shortcuts added: Ctrl+Alt+R restores pre-tile size; Ctrl+Alt+Shift+1/2/3
moves the active window to that workspace. Mouse menu equivalents are provided.
Notes currently support Markdown source, not rendered Markdown, backlinks,
attachments, autosave, encryption or process-death draft recovery. Deletion is
explicitly confirmed/permanent, not a reversible Notes trash implementation.

## R8 encrypted-index and independent host checks

```sh
# Linux/POSIX contract; native Android test remains a separate obligation:
./gradlew :testing:encryptedIndexContracts
# Full genuine standalone JVM suite, when JAVA_HOME/KOTLINC are configured:
python3 tools/run_jvm_contracts.py
# Optional external implementations (install cryptography46.0.3/Pillow12.0.0 yourself):
python3 tools/run_independent_checks.py --report pilot-reports/cross-verifiers.json
```

The optional script downloads nothing. It compares SunJCE and OpenSSL in both
directions and checks parser fixture PNGs with Pillow. Those fixtures are not
native screenshots. No new APK dependency was introduced. See `R8-ENGINEERING.md`
for actual results, bounds and unimplemented product scope. Encrypted Find is not
wired to live sources; no feature/spike is promoted by this kernel work.

## R20: laptop try-out and checks without Android Studio

This section is current for R20; older round-specific limitations above are historical.
Android Studio Rabbit1 GUI has **not** been exercised in this sandbox. Open `android/`
and use JDK17/platform36 as above; IDE branding alone is not a compatibility test.
Compile36 still deviates from requested compile37. Release/full acceptance stays blocked.

Two distinct APKs:
- `app` / `proDebug`: original shell including Tasks → counts-only report. Counts omit
  titles/IDs/dates/revision but can identify through context. This app has Internet permission.
- `privacy` / `debug`: `dev.fusion.privacy`, separate UID, no Internet permission,
  volatile native scratchpad and offline URL projection. **Everything typed is discarded
  on pause/discard/recreation.** No clipboard/import/export or saved draft. Do not put
  work here that you need to keep. IME, accessibility, Android and the laptop remain trusted.

From `android/` (Windows uses `gradlew.bat`):

```sh
./gradlew :privacy:assembleDebug :privacy:lintDebug
# Run privacy on a disposable API34+ AVD using Studio's privacy run target, or:
adb install -r privacy/build/outputs/apk/debug/privacy-debug.apk
adb shell am start -n dev.fusion.privacy/.PrivacyActivity
# Actual managed emulator; not a host model:
./gradlew :privacy:pixel6Api34DebugAndroidTest
```

The debug companion includes a non-exported fixed-marker screenshot control, absent from
release. Neither this APK's compressed size nor an emulator measurement establishes
lower total footprint than Tails. Verify permission/merged manifest/dependencies before
release; no full anonymity, forensic-erasure or all-device screenshot claim is made.

Without Android Studio or a local SDK, host checks require Python3, JDK17, Kotlin2.2.21,
openpyxl3.1.5 and z3-solver (the recorded solver version is 5.3.0). Install host verification
packages separately; they are not APK dependencies. Set `JAVA_HOME` and `KOTLINC` to
real installed tools, and make the Python packages importable.

```sh
python3 tools/r20_lab.py doctor
python3 tools/r20_lab.py host
# Includes production Kotlin compilation, 642 assertions, 760 references/readbacks,
# conditional models, four actual JVM timezone checks and Python tooling tests.
```

Optional legacy regression scripts: `run_r13_contracts.py`, `run_r13_interop.py`
(requires icalendar), `run_r19_contracts.py`, `run_r19_interop.py`, and
`run_r19_report_stress.py`. Interop scripts require their matching preceding compilation.
These write local reports; preserve historical receipts under separate names when auditing.

For actual Android execution without installing Studio, push the current session branch
and use the existing GitHub Actions. Do not confuse PR build-only success with device success.
With already configured GitHub CLI authorization:

```sh
python3 tools/r20_lab.py dispatch-native
# Dispatches android.yml + android-privacy.yml for the current REMOTE branch commit.
# This dispatch mode is provided, not claimed executed here. Normal push runs were used.
gh run list --branch arena/31a7965e-osb
# Then use an actual completed RUN_ID and a NEW receipt prefix:
python3 tools/collect_native_evidence.py RUN_ID NEW_PREFIX --privacy
# Omit --privacy for main full suite. --focused is only the focused diagnostic workflow.
```

Queued/running is not passed. The collector refuses incomplete runs. Read exact candidate,
whole workflow conclusion, named cases, failure/skip counts and artifacts. API34 managed
execution does not accept physical/API36/37/keyboard/AX/legal/LOK requirements. See
[R20-ENGINEERING.md](R20-ENGINEERING.md) for the failure ledger and scope boundaries.
