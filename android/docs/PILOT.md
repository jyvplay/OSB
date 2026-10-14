# R5 pilot preparation — native source evaluation

**Not cleared for public pilot distribution yet.** The first exit gate is a real
Android compile, lint and install. No APK or Android test run was produced in this
sandbox. Pure Kotlin contracts have now run; see the evidence file below.

## Laptop path

Follow `BUILD.md`: JDK17, SDK36, open `android/`, `proDebug` or `playDebug`.
Use a disposable API34+ tablet/resizable AVD first. Do not set Fusion as your only
Home app; S2 reversal/coexistence is unverified. The first-run notice is not a
paid entitlement or a claim that these features are release-ready.

```sh
./gradlew :app:assembleProDebug :app:assemblePlayDebug
./gradlew :app:lintProDebug :app:lintPlayDebug
./gradlew :testing:pilotContracts :testing:calculatorContracts :testing:editorContracts :testing:copyContracts
./gradlew :app:pixel6Api34ProDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.fusionDisposable=true -Pandroid.testInstrumentationRunnerArguments.notClass=dev.fusion.shell.VisualCaptureTest
```

Alternatively, pure production-code contracts run without an Android SDK:

```sh
# Install JDK17+ and the pinned Kotlin compiler 2.2.21 yourself.
# KOTLINC can point to its executable; JAVA_HOME selects your Java runtime.
python3 tools/run_jvm_contracts.py
```

The standalone runner compiles real pure Kotlin modules. It does not replace
Android APIs with mocks and does not run protocol receiver emulation. Outputs
are under `testing/build/contracts/`. It never downloads tools implicitly.

## What is admitted to this pilot UI

`core/model/PilotPolicy.kt` is the runtime admission registry. The gate runs
**before** module composition, including recovered windows.

- **Candidate, not verified:** Editor, read-only Files, bounded calculator,
  Settings/probe, accessibility state, installed-app launching, explicit sharing,
  and Pilot safety.
- **Session consent required:** disposable notes, local shell commands, media
  handoff, capture, camera preview, automation drafts. Recreating the Activity or
  process revokes the acknowledgement. This acknowledgement cannot pass a spike.
- **Blocked:** browser, Vault, Office/PDF/image/PSD parsers, casting, network
  Together, backup, Messages and Alerts. These implementations still exist in
  source; their isolation/trust/privacy/role/fidelity gates have not passed.
- **No background notification ingestion:** notification listener component is
  disabled. Historical private app data from older installs is not automatically
  deleted by this change. Use clean installs for pilot safety; inspect/remove
  old data yourself before enrolling any real account.
- **Files cannot mutate source storage in the pilot:** no trash, copy, new-folder
  or purge actions. The Editor's separately verified Save a Copy path remains
  available for disposable text. S6 moves/deletes are not quietly re-enabled.

## Window and recovery acceptance

1. Open three candidate modules. Verify focus border, buttons and pointer drag.
2. Within the focused Fusion Activity: **Ctrl+Alt+arrows** move; add **Shift** to
   resize; **Ctrl+Alt+1/2/0** tile left/right/maximize; **Ctrl+Alt+Tab** cycles
   windows (Shift reverses); **Ctrl+Alt+M/W** minimizes/closes;
   **Ctrl+Alt+PageUp/PageDown** switches workspace. These are pilot bindings,
   not global hotkeys or a passed shortcut-conflict matrix.
3. Minimize Editor with a disposable draft. Use **Restore: Editor** in the own-
   window shelf. No list claims knowledge of third-party running apps.
4. Change **window geometry density** in Settings. Touch minima are 320×240 dp;
   Compact/Balanced minima are 240×180 dp. Full font/spacing density is deferred.
5. Rotate/resize the emulator; drag windows past every edge; retain a reachable
   title bar. On displays smaller than 48 dp the reachable amount is explicitly
   bounded by the display width/height, not an impossible 48 dp promise.
6. Wait at least 500 ms idle, background, kill/relaunch. Geometry should recover.
   Last in-flight edits are not promised on power loss. Editor content recovery
   is separate from window layout. Minimized/closed non-editor modules do not
   all have full document/session recovery yet.
7. Corrupt a **copy** of `no_backup/desktop-v1.bin` on a disposable AVD and replace
   the test snapshot. Startup must enter recovery-blocked state, never overwrite
   the rejected bytes. Explicit **Reset window layout…** retains old bytes and
   must not delete `editor-session-v1.bin`. Legacy preferences are migrated only
   when no new-format snapshot exists; they are not silently erased.
8. Open/restore Beam, Vault and PDF: blocker panel only, no discovery, provider
   permission, parser, password entry or transport. A blocked module never shows
   an override button.
9. Run TalkBack/Switch/Voice, keyboard-only input and 200% text. Pane titles,
   named title controls and custom move/resize actions are source-present; only
   real assistive-tech testing can pass AX-01/S9.

## Correlated Save-a-Copy acceptance (R5 — native/provider checks unrun)

The new broker/adapter has not passed an Android compiler. Use only disposable
files after a successful local build. `R5-COPY-ENGINEERING.md` defines the exact
logical guarantees and unresolved framework/provider interfaces.

1. Save a Copy first requires explicit admission confirmation, then opens an
   app-owned broker and the system Create Document picker. Android permits that
   action to return an existing item: select a newly created file. Nonempty or
   unreadable targets must be refused before output opens. Emptiness alone is
   not atomic freshness/exclusivity; concurrent/provider risks are unverified. The broker receives only a request ID and suggested name, never document
   contents or source URI. Verify read/write grants actually reach the editor.
2. Cancel through system Back and the broker button. Matching cancellation must
   retire only its own attempt. An anonymous result must not cancel a newer save.
3. Retry retains the original frozen bytes and creates a new request identity.
   Old result/cancel callbacks must not authorize/cancel the new attempt. To save
   newer edits instead, explicitly abandon the old payload and start a new copy.
4. Change text while the picker or provider operation is pending. Only the frozen
   bytes may be saved; later edits must remain dirty. Try another open document's
   URI, tree/document aliases and prior partial destinations: reject known aliases.
5. Kill/relaunch while waiting, after reservation and after output. **Every
   restored unfinished attempt requires review and a fresh picker.** No automatic
   replay. Rotation with retained ViewModel is a separate test, not process death.
6. Force checkpoint/open/readback/receipt-write failures. An editor output stream
   must not open without a confirmed request-specific reservation. The picker may
   already have created an empty file. Do not automatically delete uncertain data.
7. Verified receipts mean the frozen bytes matched readback at that moment, not
   cloud sync, provider identity, perpetual equality or power-loss durability.
   Only the last 16 receipts are retained; history is not authenticated/encrypted.
8. Test old v1 recovery data. Contents and frozen payload remain exact, but old
   uncorrelated results cannot write. The filename remains `editor-session-v1.bin`;
   the envelope written by this revision is v2. Older app readers may refuse it.
9. Use local Downloads and a non-local SAF provider, then extend through the
   API34/36/37/OEM/physical-device matrix. Native Intent/manifest tests alone do not
   validate provider behavior. No Android/provider test is claimed here.

## Editor tools acceptance (R4 — Android/IME tests not executed)

1. Open disposable UTF-8 text with emoji, combining accents, CRLF and lone CR/LF.
   Edit, Undo (Ctrl+Z), Redo (Ctrl+Shift+Z or Ctrl+Y); verify exact contents and
   independent tab histories. Return to a saved digest should remove dirty state.
2. Ctrl+F/H/G opens native Find/Replace/Go to. Search is literal, non-overlapping,
   capped at 10,000 hits, and wraps. `$1` and backslash-n are literal replacement
   text, not regex expansions. Case-insensitive is simple Unicode mapping, not
   normalization or an expanding case fold. Close tools returns focus to text.
3. Review replace-all, then Apply to draft. It must be one undo step and never
   write the source file or modify a frozen pending Save a Copy payload.
   A changed revision/tab/selected-match span must refuse stale confirmation.
4. Navigate across mixed line endings and emoji. Coordinates are one-based lines
   and Unicode code-point columns, not visual/tab/grapheme columns. Invalid
   coordinates must fail, not jump to an unrelated position.
5. Use actual Chinese/Japanese IME preedit, emoji, dead keys, mouse selection,
   keyboard, TalkBack/Switch/Voice and 200% text in a narrow window. Syntax scans
   and Compose semantic text-input tests cannot establish real IME acceptance.
6. Undo retains at most 100 steps and 4,194,304 UTF-16 payload units per tab;
   oldest steps can be clipped. Check the warning and that the newest edit is
   still undoable. This is a logical storage cap, not a measured heap/frame budget.
7. Activity recreation should preserve history; process kill/relaunch recovers
   checkpointed contents but deliberately starts with empty undo/redo history.
   No process-persistent undo or all-modules session guarantee is claimed.

## Calculator acceptance (R3 — not executed here)

Pure kernels passed 2,056 assertions; the native UI/adapter are still uncompiled.
See `R3-ENGINEERING.md` for all bounds, approximation and verification gaps.

1. Decimal: `0.1+0.2` → `0.3`, `-2^2` → `-4`, `2^3^2` → `512`.
   Divide by zero, fractional decimal powers and oversized input must show errors.
2. Scientific: degrees `sin(90)` → approximately `1`; `sqrt(-1)` must fail.
   Switch angle context and recall an older entry: its original context must return
   without automatically calculating anything. Scientific is not exact-real math.
3. Programmer: `0xffffffffffffffff` → signed `-1`; `-1 >>> 1` → `9223372036854775807`.
   Hex output is `0xFFFFFFFFFFFFFFFF` for `-1`. `1<<64` must fail, not wrap the count.
4. Conversion: `0 C` → `32 F`; `0 F` → `-17.77777777777777777777777777777778 C`.
   `1 KiB` → `1024 B`, `1 kB` → `1000 B`; below absolute zero must fail.
5. Enter in the expression submits; Enter on focused Recall/Clear/menus must keep
   the native control action, not submit the expression. Test all modes with keyboard,
   TalkBack/Switch/Voice, 200% text and narrow own windows. No AX pass exists yet.
6. Rotate/minimize/reopen; then wait for verified checkpoint and kill/relaunch.
   Context, incomplete input and recorded results must survive without re-execution.
   Last in-flight changes are not guaranteed through a kill. History limit is 100.
7. On a disposable AVD only, corrupt a copied `no_backup/calculator-v1.bin`.
   Recovery must block and retain bytes until explicit retain-and-reset. Editor/layout
   must stay untouched. Clear history must require confirmation; a failed checkpoint
   must not claim durable deletion. Retained snapshots are not securely erased.
8. Run `CheckpointDeviceTest` against actual Android `AtomicFile`; then a separate
   kill/disk-full/rename failure matrix. Explicit sync/readback is not proof of
   physical power-loss durability. The specified Room backend is still not implemented.

## Capture acceptance (disposable screen content only)

Open Capture → acknowledge experimental scope → grant visible notifications →
Android MediaProjection consent. No audio is recorded. The encoder is **requested
AVC at 30 fps**, not verified hardware acceleration or measured latency.

- Persistent red shell indicator and immutable foreground-notification **Stop**
  action must work even after minimizing the Capture window.
- Notification permission/channel denial, screen lock/off or thermal status
  SEVERE+ must refuse/stop recording. The pilot stops rather than claiming an
  adaptive quality ladder. OEM background callback delivery still needs testing.
- Capture is written to an app-specific `.mp4.partial`. Stop/release succeeds,
  MP4 metadata is checked, the file is synced and renamed before a completed
  path is reported. This is not a playback/fidelity or power-loss proof.
- Stop immediately after Start, revoke capture from Android, trigger recorder
  failure/full storage and rotate/change source bounds. A failed stop must not
  advertise a partial file as saved. Unverified partials can remain for inspection;
  they are not auto-shared or called healthy recordings.
- Vault is blocked, not “redacted by magic.” Do not share secrets from any app.
  Android FLAG_SECURE behavior and SC-09 exclusions still need device acceptance.

## Real device observations

Connect a test device with USB debugging authorized. Obtain its actual serial
using `adb devices`. This script makes **read-only** queries and never installs,
resets, roots, records or changes device settings:

```sh
python3 tools/pilot_device_report.py --serial YOUR_ACTUAL_ADB_SERIAL --output device-observations.json
```

No serial is written to the report. For the owner's private firmware ledger,
explicitly add `--include-fingerprint`. Do not commit device reports to Git.
Review the report before sharing; SoC/model/version fields are still device
metadata. Empty/failed/timeout observations are unknown, never zero or a pass.
Android Settings also offers a reviewed, redacted share-sheet export of current
SoC/RAM/codec/thermal/PSS observations. No telemetry or automatic upload exists.

## Admission decision record

Record APK hash/version, API/firmware, device/RAM/page size, test operator/date,
exact steps, expected/observed outcome, errors, thermal conditions and evidence
paths. Use ≥2 physical reference devices before promoting any E0 budget.

A candidate can advance only after its Android and device-specific checks pass.
A gate may also fail and choose the specified fallback. Do not edit statuses to
`verified` merely because a debug build or these JVM contracts pass. License
closure, isolation, security/accessibility reviews and signed distribution remain
release blockers. This document does not authorize a production rollout.
