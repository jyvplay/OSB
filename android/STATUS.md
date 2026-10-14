# Latest: R8 continuation

- **171,082 JVM assertions / 47 Python tests passed**, plus SunJCE↔OpenSSL and
  13 Pillow/parser cross-checks. Actual logs/hashes, not Android evidence.
- New bounded encrypted-index store and locked-session literal search; no source
  discovery, consent/exclusion adapter or live Find UI integration. S4 stays blocked.
- PNG pixel/CRC/deflate validation and bounded binary acquisition now replace the
  earlier framing-only check. Parser fixtures are not native screenshots.
- Native Keystore adapter/test authored; missing keys do not regenerate on read.
  Native code remains uncompiled/unrun; no hardware-backing assertion.
- Existing CI failed during SDK setup, before compilation. Candidate workflow
  repair is authored and **unrun**. No new CI dispatch, commit or push this pass.
- **0/48 specification-complete modules (0%);48 outstanding (100%).** 231 recorded
  requirements and36 explicit cross-cutting obligations;30 unpassed spikes.
- [Full A–J record and remaining feature gaps](docs/R8-ENGINEERING.md).

Earlier sections below record historical checkpoints, not current-source results.

---

# Latest: R8 draft-PR checkpoint

**170,728 JVM assertions and 35 Python tests passed.** Android build/device/visual
checks remain unrun; **0/48 specification-complete modules (0%)**. Latest work adds
ordered window-command epochs/sequences, codec v3 and stronger release-record
checks. The PNG admission weakness found this turn is **not yet repaired**.
See [exact current evidence and gaps](docs/R8-PR-CHECKPOINT.md). Earlier sections
below are historical evidence, not compilation results for all current sources.

---

# Current verification — R7 native scope review

- **0/48 fully accepted modules (0%);48/48 need implementation/acceptance (100%).**
-231 requirements:171 not implemented,56 partial,3 excluded,1 superseded;0 verified.
-30 blocked/unpassed spikes. API37, full process isolation, Room and many major
  features remain absent. This is not just a pending-test backlog.
- Added bounded revision-checked note storage/native UI, immutable window outputs,
  tile/normal restoration and v1 migration, real native Find actions, backend
  Files denial, safer SQLite upgrades and stale-picker gates.
- **169,801 JVM assertions /30 Python tests passed**; actual compiler/file evidence.
  Twelve separate pure libraries; three historical CastV2 unchecked-cast warnings.
- Notes equal-content ABA and post-close window Open replay gaps are recorded,
  not hidden. Android adapters/Compose remain uncompiled.
- No Android build, APK, emulator screenshot, visual review, device, CI or legal pass.
  No commit/push. The laptop lab is authored, not executed here.
- [Full scope](docs/FULL-SPEC-REVIEW.md), [A–J/evidence](docs/R7-ENGINEERING.md),
  [module audit](docs/MODULE-COMPLETION.md), [native lab](docs/NATIVE-LAB.md).

---

## R6 record (historical)

# R6 verification pre-Studio pass

- **0/48 specification-complete modules (0%);48/48 still need completion or
  acceptance (100%).**231 requirement rows;30 blocked spikes; no gate promoted.
- Fixed native editor cross-module nullable-property usage. Runner now compiles
  11 separate JVM libraries (calculator kernels only), then consumers.
- **169,576 JVM assertions and21 Python tests passed.** Two negative compiler
  attacks rejected; stable-local positive consumer compiled. Three CastV2 warnings.
- Native title controls now use48dp minima/tiling menu; header can scroll/reflow.
  No Android geometry, visual or accessibility acceptance inferred.
- Authored guarded real build/instrumentation/capture lab at100%/200% font settings.
  Actual doctor refused missing JDK/SDK/ADB; Android execution remains **unrun**.
- No APK, Android compile/lint/device/human visual/CI/legal pass. No push/commit.
- [R6 engineering record](docs/R6-PRE-STUDIO.md), [native lab](docs/NATIVE-LAB.md),
  [all-module audit](docs/MODULE-COMPLETION.md). Not cleared for pilot distribution.

---

## R5 record (historical)

# R5 correlated copy protocol (2026-10-13)

- Native Save-a-Copy flow now has monotonic attempt identities, a non-exported
  broker, request-specific reservation checkpoints, no automatic unfinished
  replay, bound readback receipts and v1→v2 migration. Android integration uncompiled.
- **169,576 JVM assertions passed**, including **5,388 copy assertions** and
  actual file readbacks. Two new counterexamples failed before repair; all
  affected suites and patch-targeted attacks were rerun.
- **15 Python tests passed**; manifest/routing/fixture structural checks passed.
- [R5 engineering/evidence record](docs/R5-COPY-ENGINEERING.md) documents the
  formal boundary, primary sources, failures, repairs and Android/provider gaps.
- **48 included modules; 0 fully specification-complete (0%); 48 requiring
  completion or acceptance (100%).** No feature/spike promoted. Budgets remain E0.
- No Android compile, APK, device, CI, legal or accessibility pass. Work stays
  local; no commit/push/workflow dispatch. Still not cleared for pilot distribution.

---

## R4 record (historical; superseded where R5 differs)

# R4 native editor (2026-10-13)

- Added native bounded per-tab undo/redo, literal Unicode find/replace, explicit
  revision-bound replacement confirmation and line/code-point navigation.
- Latest real JVM result: **164,188 assertions passed** = 153,216 existing +
  2,056 calculator + **8,916 editor**. A new selected-span race test failed before
  repair; full reruns and two patch-targeted attacks passed afterward.
- **15 Python tests passed**. Evidence and limitations:
  [R4 engineering record](docs/R4-EDITOR-ENGINEERING.md),
  `docs/verification/r4-jvm-contracts.txt`.
- Native ViewModel/Compose/IME changes are source-present but remain uncompiled.
  No local Android APK, instrumentation, accessibility or physical-device pass.
- **48 included modules; 0 specification-complete (0%); 48 requiring completion
  or acceptance (100%).** [Updated audit](docs/MODULE-COMPLETION.md).
  No feature or spike promoted; all physical performance budgets stay E0.

---

## R3 record (historical; superseded where R4 differs)

# R3 calculator and module audit (2026-10-13)

**0/48 included Gradle modules specification-complete (0%); 48/48 still need
completion/acceptance (100%).** This counts full acceptance, not source files or
engineering effort. [Module-by-module audit](docs/MODULE-COMPLETION.md) identifies
present scope, evidence and remaining work, including missing architecture slots.

- Native PR-09 decimal/scientific/programmer/unit/history implementation added.
  Pure kernels compiled; Compose/ViewModel/Android persistence still uncompiled.
- Latest genuine JVM run: **153,216 existing + 2,056 calculator = 155,272
  assertions passed**. Evidence: `docs/verification/r3-jvm-contracts.txt`.
- **15 Python tests passed**, plus structural validation. A held-out Fahrenheit
  precision counterexample failed before repair, followed by full reruns and
  new attacks; see [A–J engineering record](docs/R3-ENGINEERING.md).
- Editor/layout/calculator checkpoints now explicitly sync and verify decoded
  readback; Android implementation/device durability remains unverified.
- **No Android build, APK, lint, instrumentation, CI, physical-device, receiver or
  legal acceptance.** No requirement/spike promoted. Still not cleared for pilot
  distribution. All measured product budgets remain E0.

---

## R2 record (historical; superseded where R3 differs)

# R2 pilot hardening (2026-10-13)

**Pure Kotlin production sources compiled and executed. Android APK build, lint,
JUnit/Compose instrumentation, physical-device and receiver tests remain unrun.**
No feature row or hardware spike is promoted to verified. All performance budgets
remain E0. See [pilot instructions](docs/PILOT.md).

## Executed evidence this turn

- Obtained a development-only Temurin 17.0.9 runtime from `jdk4py==17.0.9.2`
  (PyPI) and Kotlin compiler **2.2.21** (npm registry). Verified downloaded bytes
  against registry SHA-256/SHA-512 metadata; this is transport-integrity evidence,
  not an independent publisher audit. Tools are outside the repository/APK.
- Compiled 10 pure-Kotlin module source trees plus the pure performance policy and
  contract runner, JVM target 17. Existing castv2 unchecked-map-cast warnings remain.
  No network protocol was exercised by this run.
- Executed **153,216 assertions**, including a deterministic 5,000-command reducer
  sequence, title/minimum/z/focus invariants, idempotent keyed opens/setters,
  corrupt/truncated recovery rejection, gate admission, thermal/statistical
  semantics, UTF-8/pending-save codec and actual temporary-file readback.
  These are repeated contract assertions, not 153,216 independent device tests.
- Evidence with compiled-source SHA-256 hashes:
  `docs/verification/r2-jvm-contracts.txt`.
- **9 Python tests passed** for traceability and diagnostic-report semantics.
  Structural checks pass (231 feature rows, 30 spike IDs, 48 modules).
- The Gradle wrapper now launches under Java but fails fetching Gradle 8.13 from
  `services.gradle.org` with TLS/network restriction. Google Maven/Android SDK
  remain unavailable. No Android compile or APK is claimed.

## New native hardening

- Window state: dense bounded z-order, correct cross-workspace focus, no focus
  theft when closing/minimizing background windows, finite geometry, density
  minima even under tiling, stable switcher ordering and bounded window count.
- Android host: stable keyed Compose window content, named accessibility actions,
  keyboard move/resize/tile/switch/close, minimized-window restoration.
- Layout: bounded versioned/checksummed AtomicFile snapshots, a single IO writer,
  throttled writes, legacy migration, retained corrupt snapshots and explicit
  reset independent of editor drafts. **AtomicFile, not the specified Room
  backend**; Room migration remains a recorded architectural deviation.
- Pilot registry gates module creation and restored windows. Experimental consent
  lasts only for the Activity session. Unsafe/incomplete surfaces fail closed;
  there is no override for blocked modules. Files is read-only. Notification
  listener is disabled, including at component level.
- Capture: foreground notification Stop, shell indicator, screen-off/thermal
  stop, visible-notification precondition, aspect-preserving requested size,
  partial-file lifecycle, stop-success and metadata checks before publish,
  protection against stale MediaProjection callback cleanup. Android execution
  and hardware/latency/exclusion validation are still pending.
- Beam quality policy now requires known encoder capabilities and stops on critical/unknown thermal conditions; this is still not a transport implementation.
- Probes: errors remain unknown, no fallback invention of hardware AVC, RAM
  reported fractionally instead of classifying a reserved 8GB device as 7GB,
  correct total PSS (previous code returned private-dirty memory), correct
  severe/critical mapping, no zero-frame “0ms” performance claim. Settings probes
  run off main while the Activity is STARTED; report sharing is explicit/redacted.
- Settings no longer offers nonfunctional shortcut preset toggles. Geometry
  density is wired to the reducer in one location; full design density remains
  incomplete. Backup password arrays are wiped on cancel/finally even though
  that module is currently pilot-blocked.
- Added read-only, explicit-device ADB observation collection; no ADB device was
  available here. Reporter unit tests do not stand in for a collection run.

## Remaining admission blockers

A successful full Android build/install and the checklist in `docs/PILOT.md` are
required **before enrolling pilot users**. Casting trust/pairing, Forge isolation,
BlackTop, full browser, secure Vault, signed billing, journaled moves, backup,
Lens effects/Meet, messaging roles/transports, API37, SBOM/license closure and
physical accessibility/performance gates remain incomplete. Blocking a dangerous
module is a safety fallback, not completion of that module.

---

## Previous R1 record (historical; superseded where R2 differs)

# R1 native continuation (historical, 2026-10-13)

**No Android APK compiled, installed or run in this sandbox. No JVM test executed.
No physical device, receiver or legal sign-off. All latency/memory budgets E0.**
This supersedes earlier R12/R13 language implying that loopback or PSD JVM tests
“passed”; those files were test source, not execution evidence available here.

## What actually ran

| Action | Result |
|---|---|
| Repository/source/build/manifest review | Performed |
| Fetch official Gradle wrapper through GitHub contents API | Retrieved v8.13.0 scripts/JAR; pinned local hash |
| Fetch upstream Gradle/AndroidX/coroutines license text | Retrieved; see `docs/LICENSE-REVIEW.md`; not a transitive audit |
| `python3 tools/check_project.py` | Passed: 231 rows, 30 spike IDs, 48 module references, XML/catalog/privacy checks |
| Python traceability-validator tests | **5 passed**; these are not Kotlin/Android tests |
| `python3 tools/requirements.py --release` | Failed as intended: 61 P1-containing rows unverified, all spikes unpassed |
| `./android/gradlew --version` | Blocked: `JAVA_HOME` unset, no Java executable |
| JDK/Kotlin compiler download attempt | Redirect to inaccessible `release-assets.githubusercontent.com`; failed |
| Kotlin syntax-only parser on new/changed source | Supplemental review only; not compiler/type-check evidence |
| Gradle sync/assemble/lint/JVM tests/emulator/CI | **Not run** |

No CI run was triggered, no branch was pushed, and no APK or benchmark report is
claimed. GitHub Actions is configured for future runs once changes are pushed.

## Code completed in this slice (not product-complete)

- Added pure Kotlin `:core:documents`: strict bounded UTF-8, immutable document /
  pending-save models, versioned checksummed bounded recovery codec, exact-byte
  post-close verification. Ten JVM safety test methods authored.
- Replaced transient single-buffer Editor with Activity-scoped native sessions,
  eight independent tabs, AtomicFile recovery, persistent picker payload,
  separate saved snapshot, provider alias guard, verified Save a Copy, and
  explicit discard/retain-draft decisions. PR-01, FM-18 and FF-15 stay **partial**:
  no large-file/diff/regex/multi-encoding editor or universal Forge lifecycle.
- Removed the automatic HTTP server for the granted Files tree (data exposure).
  Sharing a SAF grant is not consent to share over LAN. Server class remains in
  source, disconnected from the app until a per-file trusted session is built.
- Removed invented Beam link/codec values; mDNS listeners are separate per
  protocol, callback writes go through main, resolution is time-bounded and
  stale results are rejected. Physical receiver interoperability is unverified.
- Added pinned wrapper and version catalog; API36 target; play/pro IDs; play
  manifest no longer inherits SMS permissions. No Play Billing/Cast SDK was added.
  SMS UI now says “submitted,” not “sent” without delivery evidence.
- Added both-flavor CI, strengthened instrumented test initialization and editor
  tests, structural checks and a failing-closed release-build dependency.

## Requirements and spike reconciliation

- v2.0 §7 actually lists **195 rows** when counting PR-07 plus PR-07a–s and the
  superseded CT-04. v2.1 adds 36 rows: **231**, not the prose's 172/208.
- SH-10′ and CA-11′ are updates, not extra IDs. IN-01 is separately listed but
  overlaps SH-09. BL tiers absent in the commerce table are normalized as A for
  ordinary application implementation, not asserted Play-policy approval.
- The opening contract's “§8 tables” resolves to the actual §7 feature register.
- The patch reuses S12/S13 for browser spikes, colliding with HEIC/PSD. Preserve
  canonical S12/S13; assign aliases **S29 (Gecko Z-order), S30 (WebExtensions)**.
  This mapping is explicit, not a fabricated pair of passed tests.
- `tests` fields prefixed `plan:` are acceptance obligations, **not executable
  coverage**. No whole feature row has verified status. Recount via
  `python3 tools/requirements.py`; do not hand-edit the CSV.

## Pre-implementation gate record for this slice

The repository contained feature code before any completed spike evidence. That
is a pre-existing deviation, not retroactive permission to mark spikes passed.
R1 extends the conservative, copy-only text path and repairs data-loss/privacy
problems. S6 remains blocked: no moves, atomic provider replacement or file-ops
losslessness is enabled. No new casting/Lens/browser/BlackTop engine is added.
All protocol, hardware, accessibility and legal gates remain open.

## Highest-risk remaining work

1. **Run a real native build** and lint/test results on a JDK/SDK-enabled worker.
   Source-only review cannot exclude API, dependency or Android runtime errors.
2. Existing castv2 accepts arbitrary TLS certificates; AirPlay pairing is absent;
   cleartext permission is application-wide. Add receiver trust, per-transport
   network restrictions and visible session ownership before production casting.
3. Existing parsers still execute in the host rather than isolated services.
   Some preview/file paths lack full streaming size/time cancellation controls.
4. Existing recovery elsewhere, trash/copy, notifications, vault and backups need
   security/fault testing. Plaintext editor recovery is not a vault.
5. Original compositor lacks full keyboard/a11y/density behavior and verified
   session persistence. External display remains a mirror, not BlackTop.
6. Complete the missing P1 requirements before calling this an MVP. Paid licensing,
   full index/files/terminal and hardware routing are not implemented.
7. License/SBOM closure, target37 platform verification, real receiver tests,
   device measurements and legal review cannot be inferred from code presence.
