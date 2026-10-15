# R12 current build and try-out entry points

Open **OSB/android** in Android Studio Rabbit1 with a version compatible with
**AGP8.13.2, Kotlin2.2.21, JDK17 and SDK36**. The IDE name itself is not compatibility
proof. Current min/compile/target34/36/36 remains a documented deviation from37.

[Verified native run38676376263](https://github.com/jyvplay/OSB/actions/runs/38676376263)
at2c2b5bc has39/39 API34 cases passing and the `fusion-native-debug` APK artifact.
These are incomplete debug builds, not a cleared production beta.

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
