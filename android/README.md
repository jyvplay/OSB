# Latest: R18 — editable/archiveable tasks and owned sandbox requests

- **47/48 modules (97.92%) have substantial executable subsets;1/48 (2.08%) remains unestablished: LOK.**
- **0/48 fully accepted (0%);48/48 unfinished (100%).** All30 formal spikes remain unpassed.
- Tasks now have title/date editing, durable archive/restore, literal query/scope/date ordering, FTS1 read/FTS2 write compatibility, and a checked revision reserve so archived tasks remain restorable.
- Additive owned-sheet API uses an unlinked read-only input and request metadata/key-set correlation. **Not arithmetic authenticity or full Office/LOK.**
- **Latest d6f7393/run38831196589:85 PASS,1 FAIL,0 SKIP**; focused2/2 passed. Older task-add timed out with actual store still revision0/empty. All seven R18 cases and both builds/JVM/contracts/lint passed; the full native gate FAILED.
- Earlier9340662/run38830281113 passed86/86 but failed its focused run0/2. Both outcomes are retained; UI instability is not marked fixed.
- **906 R18 assertions +1,200 Python/JVM cases;90 tooling tests;6 conditional Z3 checks.** Fresh R14/R15/R16/R17 regressions also passed. None proves full product acceptance.
- [R18 report, failures, alternatives and A–J](docs/R18-ENGINEERING.md), [census](docs/MODULE-TESTABILITY.md), [evaluation APK/try-out](docs/BUILD.md).

# Historical: R17 — bounded R16 closure and persistent local tasks

- **47/48 modules (97.92%) have executable subsets;1/48 (2.08%) remains unestablished: LOK.**
- **0/48 fully accepted;48/48 unfinished.** All original full workflow/physical/legal/AX gates remain separate.
- R16: real authenticated TCP/clock tests, actual benchmark journeys and855 measured first-party ART rules.
- R17: local Tasks with date-only labels, reversible completion, revision-checked private persistence and corrupt-data refusal; no reminders.
- **ddc7fec/run38700509084:79/79 API34 cases PASS**, both debug builds, JVM/contracts and lint.
- Earlier6164211:78/79 with a Task UI timeout. Focused/full reruns passed without a production change;
  causal diagnosis/stability remains open, not silently marked fixed.
- **397 Tasks assertions +200 independent wire/date cases;17131 R16 assertions +64 actual socket cases;
  81 Python tests; four conditional Z3 integer-model checks.** None proves the complete application.
- [R17 engineering/evidence/alternatives](docs/R17-ENGINEERING.md), [R16 closure](docs/R16-ENGINEERING.md),
  [per-module census](docs/MODULE-TESTABILITY.md), [Studio try-out](docs/BUILD.md).

> **Historical: [R12 continuation — seven more testable modules](docs/R12B-ENGINEERING.md).**
> **30/48 modules (62.50%) meet the substantial-testable-subset standard;0/48 are fully accepted.**
> Five new kernels: settings snapshots, subtitle inspection, ARGB operations, backup-content framing,
> and launcher catalog/pins. Two existing runtime-inventory modules now have actual Android checks.
> Strict UTF8 and multiline CSV defects repaired. **2,394 new +183,179 rerun Kotlin assertions;
> 1,188 new cross-language checks;74 local Python tests passed.**
> Latest0a23219:49/50 API34 cases passed; the stronger Editor test timed out awaiting an observable IME. Both flavors/unit/contracts/lint passed. The native suite is NOT fully green.
> The preceding200%-font Editor failure is retained, not silently erased by a later pass.
> See [Studio instructions](docs/BUILD.md) and the [48-row census](docs/MODULE-TESTABILITY.md).

> **Historical: [R12 implementation, alternatives and exact evidence](docs/R12-ENGINEERING.md).**
> **39/39 API34 cases passed at2c2b5bc** on a regular Google managed image;
> both debug flavors, invoked unit/contracts and lint passed.
> Added bounded Notes recovery with cross-owner conflict refusal and actual writer-death
> tests; repaired Editor reflow; verified real remote WebView pixels/input/resize in
> debug experiments. These do not deliver a complete desktop browser or Office engine.
> **183,179 Kotlin assertions;68 Python tests;733 cross-language checks passed.**
> **23/48 modules (47.92%) have substantial testable subsets;0/48 (0%) are fully accepted.**
> See the [48-row testability census](docs/MODULE-TESTABILITY.md) and [Studio try-out instructions](docs/BUILD.md).
> [Verified run / debug APKs](https://github.com/jyvplay/OSB/actions/runs/38676376263).

> **Historical: [R11B implementation/testability/alternatives](docs/R11B-ENGINEERING.md).**
> **182,871 JVM assertions /13 libraries;61 Python methods;323 cross-language
> checks passed.** Real Notes trash/restore, reflow and compositor focus repair.
> **29/29 API34 named cases passed at1ea1faa**, including200%-font Notes,
> six elementary boundary cases and debug WebView profile/DOM experiment.
> Both debug flavors, invoked unit/contracts/lint and APK upload passed.
> No complete desktop browser, Office engine or full parser sandbox is delivered.
> **0/48 accepted modules (0%); not a complete product or production beta.**
>
> Older sections below are historical; use R12B for current evidence and gaps.

> **Historical: [R10 productivity / Forge engineering](docs/R10-ENGINEERING.md).**
> **177,198 JVM assertions,57 Python methods and707 cross-implementation/oracle
> checks passed.** Added encoding-preserving editor recovery/copy, bounded
> current-source line diff and repaired ignored-ZIP expansion guards. **Both Android
> flavors now assemble; unit tests, contracts, lint and APK upload pass in CI.** Browser,
> Office engines and format isolation are still missing/blocked.
> **0/48 accepted modules (0%); not a production-ready beta.**

> **Historical: [R8 continuation](docs/R8-ENGINEERING.md).** 171,082 JVM assertions,
> 47 Python tests and cross-implementation crypto/PNG checks passed. Added a bounded
> encrypted-index persistence/search kernel and repaired capture integrity checks.
> Android/Keystore integration and visual inspection remain unrun; encrypted Find
> is not wired to live sources. **0/48 complete modules. Substantial coding remains.**

> **Previous merged checkpoint:** [R8 draft-PR notes](docs/R8-PR-CHECKPOINT.md): 170,728 JVM
> assertions / 35 Python tests passed; Android build and visual inspection unrun.
> Full system remains incomplete (0/48 accepted modules). R7 evidence below is historical.

# Fusion Shell — native Kotlin / Compose

Open this directory in Android Studio. Do **not** open the repository root as a
web project. This source tree is a partial implementation, not the complete
v2.1 product. CI debug APKs are incomplete test builds, not certified releases.

## Historical R7 — full recorded-scope review and native hardening

**0/48 fully specification-complete modules (0%);48/48 still require implementation
or acceptance (100%).** There is substantial missing code, not just missing tests.
The [full scope review](docs/FULL-SPEC-REVIEW.md) covers231 recorded requirements
and35 cross-cutting obligations.171 rows remain unimplemented and56 partial.

New production code adds revision-checked bounded note files and native note
lifecycle UI, immutable published window lists, saved tile/normal geometry with
v1→v2 restore migration, workspace movement controls, and an executable native
module/window/workspace Find dialog. Unsafe legacy Files mutations now refuse
in the backend; unsupported SQLite upgrades no longer drop owner data.

**169,801 JVM assertions and30 Python tests passed.** Twelve pure libraries were
compiled independently. Actual counterexamples and remaining limits are retained
in the [R7 engineering record](docs/R7-ENGINEERING.md). **No Android compile, APK,
lint/device execution or visual review occurred.** No TypeScript or mock UI added.

Open this directory in Studio with JDK17/SDK36; first run
`python3 tools/native_lab.py doctor`. The [native lab guide](docs/NATIVE-LAB.md)
covers real both-flavor builds and guarded disposable-emulator captures of seven
views at100%/200%. That Android workflow remains authored, unrun here.

## R6 record — pre-Studio native pass

**0/48 modules fully accepted (0%); 48/48 still require completion/acceptance (100%).**
Native module-boundary smart casts were repaired and the real JVM runner now
compiles libraries separately. All **169,576 assertions** and **21 Python tests**
passed. Two bad module-boundary consumers were rejected; the repaired pattern
compiled. These are not Android build results.

Native title controls now have48dp minima and retain tiling in an actions menu.
The [real native lab guide](docs/NATIVE-LAB.md) explains both-flavor builds,
explicitly consented disposable-emulator tests, and actual screenshot collection
at100%/200% font settings. **That Android workflow is authored, unrun.** No native
screenshots, APK, visual review or release clearance were produced here.

Start with `python3 tools/native_lab.py doctor` from this directory after installing
the prerequisites. See the [R6 evidence/gap record](docs/R6-PRE-STUDIO.md) and
[per-module completion audit](docs/MODULE-COMPLETION.md).

## R5 record — correlated native save/recovery

**48 included modules; 0 specification-complete (0%); 48 still need completion or
mandatory acceptance (100%).** [Module audit](docs/MODULE-COMPLETION.md).

Save a Copy now has per-attempt identities, a native non-exported picker broker,
checkpoint-before-output reservation, request/destination-bound byte readback and
bounded receipts. Restored unfinished attempts require explicit review and a new
picker; they are never automatically replayed. Historical v1 drafts migrate to v2.

**169,576 JVM assertions and 15 Python tests passed.** The
[R5 engineering record](docs/R5-COPY-ENGINEERING.md) preserves two real failing
counterexamples and the repaired verification results. Android broker/adapters
remain uncompiled; no APK or pilot clearance is claimed.

## R4 record (historical)

**48 included modules; 0 specification-complete (0%); 48 still need completion or
mandatory acceptance (100%).** See the [module audit](docs/MODULE-COMPLETION.md).

The editor now has bounded per-tab undo/redo, Unicode-aware literal find/replace
with stale-confirmation protection, and mixed-newline line/column navigation.
Native integration preserves ordinary IME composition values; actual Android/IME
acceptance remains unrun. Undo history is in-memory only, not restored after a kill.

**164,188 JVM assertions and 15 Python tests passed.** Native UI/adapters have not
been Android-compiled; no APK or cleared pilot is claimed. The
[R4 engineering record](docs/R4-EDITOR-ENGINEERING.md) contains the actual failed
counterexample, repair, executable evidence and remaining gates.

## R3 record (historical)

**48 included Gradle modules; 0 specification-complete (0%); 48 unfinished or
awaiting mandatory acceptance (100%).** Source existence is not completion.
Read the [full module audit](docs/MODULE-COMPLETION.md) and
[A–J engineering/evidence record](docs/R3-ENGINEERING.md).

The native calculator now has bounded decimal expressions, approximate scientific
functions, 64-bit programmer arithmetic, dimension-checked units and local history.
Its pure kernels passed **2,056 assertions**; existing contracts passed **153,216**.
**15 Python tests** passed. No Android compilation/APK/device acceptance is claimed.
Calculator/editor/layout checkpoints add explicit sync and decoded readback; their
Android lifecycle/durability gates remain open. No module is promoted to complete.

## Pilot hardening status (R2)

Read [PILOT.md](docs/PILOT.md) before evaluation. Pure Kotlin production contracts
now compile and pass (153,216 assertions; no Android/receiver evidence). The
native host adds geometry recovery, keyboard window controls and minimized-window
restore. A runtime policy blocks unsafe/incomplete modules before composition,
including on restore. Files is read-only; experimental modules need session
consent. Capture stop/privacy/finalization and capability reporting were hardened
but still require Android/device testing. No public pilot is cleared yet.

## Run on your laptop

1. Open `OSB/android` in Android Studio (Rabbit 1 or a compatible stable release).
2. Select a **JDK 17** Gradle JVM; install **SDK Platform 36** via SDK Manager.
   Gradle 8.13, AGP 8.13.2 and Kotlin 2.2.21 are pinned. The wrapper is included.
3. Sync. In **Build Variants**, select **`proDebug`** for `app`.
4. Create/start an Android 14+ tablet or resizable emulator and Run `app`.
   A laptop x86_64 emulator can exercise the Kotlin/Android UI; it is not a
   substitute for Snapdragon/ARM64 codec, NPU, 16KB or display testing.
5. Open Editor from the shelf. New/Open UTF-8 → edit → Save a Copy using Android's
   document picker. Reopen Editor after closing or relaunching to recover drafts.

Terminal equivalent (Windows: replace `./gradlew` with `gradlew.bat`):

```sh
./gradlew :app:assembleProDebug :app:assemblePlayDebug
./gradlew :core:documents:test :testing:pilotContracts :testing:calculatorContracts :testing:editorContracts :testing:copyContracts
./gradlew :app:pixel6Api34ProDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.fusionDisposable=true -Pandroid.testInstrumentationRunnerArguments.notClass=dev.fusion.shell.VisualCaptureTest
```

APKs, **after a successful local build**:
`app/build/outputs/apk/pro/debug/app-pro-debug.apk` and
`app/build/outputs/apk/play/debug/app-play-debug.apk`.

[Detailed build/troubleshooting](docs/BUILD.md) · [Verification report](STATUS.md)

## Implemented source in this pass

- Native editor: multiple document tabs (8 max), independent drafts, strict
  UTF-8 (2 MiB per document), no silent replacement of malformed input, Ctrl+O/N/S.
- Versioned, bounded, checksummed local recovery stored with Android `AtomicFile`
  under `no_backup`. The source URI is not overwritten. Recovery is **not** a
  backup or encryption feature. Recent edits inside the debounce interval may
  not survive sudden process termination.
- Immutable pending-save payload persisted **before** launching the system
  picker; Activity/process recreation cannot substitute an empty string for it.
- Save a Copy closes the output, reopens it, compares exact bytes, and only then
  updates the saved state. Edits made after the picker snapshot remain dirty.
  Unknown/failed provider outcomes retain the draft and warn about partial copies.
- Tab discard confirmation; closing the editor window offers to retain drafts
  locally instead of silently losing them.
- `play` and `pro` application flavors with distinct IDs; SMS permissions are
  absent from `play`. Billing/entitlements remain **unimplemented**.
- Removed automatic LAN publication of the Files tree. Local HTTP sharing is
  disabled until a per-file capability/expiry/stop-indicator design is verified.
- Beam discovery uses distinct mDNS listeners and drops stale callbacks; a
  fabricated 100 Mbps link/HEVC quality display was removed.
- Gradle wrapper, one version catalog, structural checks, JVM/device test sources,
  CI for both debug flavors, and a release gate that currently fails closed.

## Existing source — not newly verified

The shell has first-party windows, basic tiling, workspaces, a module shelf,
module-name palette and geometry persistence. A secondary display is only a
session mirror, **not BlackTop or an interactive compositor**. Files uses SAF;
trash/copy, notes, calculator, shell command execution (not PTY), PDF/image/PSD
preview, notification logging and capture have partial implementations.

Cast URL clients exist, but no Apple TV, HomePod, Chromecast or DLNA receiver
has been tested here. A transport accepting a request is not proof of playback.
Lens is camera preview only; Together is a local model, not a network watch party.
Neither is claimed as the requested finished subsystem.

## Not delivered yet

Complete P1; actual BlackTop; keyboard/a11y release pass; multi-process parser
isolation; encrypted search; journaled fault-safe moves; terminal PTY; Office
editing; RAOP; any mirroring/receiver; Lens processing/Meet/browser injection;
Mac-backed iMessage; full browser requirements; timeline backup; paid licensing;
API37 migration; resolved transitive-license SBOM; performance measurements.

See the [normalized register](docs/feature-register.json) and
[spike ledger](docs/spikes.json). No spike is marked passed without evidence.
