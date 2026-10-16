# R18 — current Android build and try-out

Open **OSB/android** in Android Studio Rabbit1 with compatible AGP8.13.2,
Kotlin2.2.21, JDK17 and SDK36 tooling. Gradle/Android execution is remote;
the Rabbit1 desktop IDE itself was not run here. Current min/compile/target34/36/36
still differs from requested compile37. These are evaluation builds, not release clearance.

**47/48 (97.92%) substantial executable subsets;0/48 fully accepted (0%).** Only LOK
lacks an established subset. See [R18-ENGINEERING.md](R18-ENGINEERING.md) for exact
A–J evidence, alternatives, failures and remaining gates.

**Latest tested d6f7393/run38831196589:85 PASS,1 FAIL,0 SKIP**; focused2/2 passed.
[Latest evaluated APK artifacts](https://github.com/jyvplay/OSB/actions/runs/38831196589)
include `fusion-native-debug`; both builds/JVM/contracts/lint passed, but the full native gate FAILED.
The older task-add test timed out with real persisted revision0/no task; all seven new R18 cases passed.
Use disposable data only. Do not treat this as stable or release-ready.

[Earlier9340662 full run](https://github.com/jyvplay/OSB/actions/runs/38830281113) passed86/86,
but its focused run failed0/2. Both results remain; no causal repair/stability claim is made.
Later docs/evidence commits are not the same tested revision. Historical79-case R17 results are in its report.

## Try-out: editable local tasks

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
python3 tools/run_r18_interop.py
python3 tools/run_r17_interop.py
python3 tools/check_project.py
python3 -m unittest discover -s tools -p 'test_*.py'
./gradlew :app:assembleProDebug :testing:r17Contracts :testing:r18Contracts
```

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
