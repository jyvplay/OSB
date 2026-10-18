# R23 results — real native subsets, incomplete product

2026-10-16 · branch `arena/f77cb2a6-osb` · native-tested candidate **`4bc763700b63582107e5dbe979f9a185338f66f7`**.

**Overall native regression gate: FAILED, not release-ready.** The final main run is **121PASS/1FAIL/0SKIP**. The separate privacy and memory suites passed. No rerun-to-green, removed assertion, skipped failing case, full-module acceptance or all-hardware/anonymity claim.

## 1. Absolute and percentage accounting

| Measure | Absolute | Percentage |
|---|---:|---:|
| Existing modules with substantial source/test-paired runnable subsets |48/49|97.96%|
| Unestablished substantial subset (`:format:lok`) |1/49|2.04%|
| Fully original-specification accepted modules |0/49|0%|
| Still unfinished at full requested scope |49/49|100%|
| Existing production modules deepened by R23 |4/49|8.16% of inventory, NOT effort/completion|

The four are `:core:security`, `:privacy`, `:format:pdf`, `:feature:automation`. These are **not four new or fully completed modules**. Testability is not97.96% product completion. [Reviewed census and per-module commands](MODULE-TESTABILITY.md) · [full completion audit](MODULE-COMPLETION.md).

## 2. Implemented, not simulated product behavior

### Native offline input/privacy compartment

- Separate no-INTERNET APK; ordinary volatile scratchpad plus **digits-only64-character no-IME pad**. No account, submission, clipboard/export route or persistent draft for the pad.
- Actual input-connection/hardware-key rejection, whole-gesture full/partial obscuration handling, stale detached-button protection, focus/background/recreation discard, default masking,5s reveal-hide UI timer and60s inactivity timer/action checks.
- `FLAG_SECURE`, application-overlay hiding and recents-capture requests; local exposure observations explicitly report UNKNOWN/not a keylogger or malware verdict.
- New native tests include an actual focusable Dialog, real system IME-visible single-tap discard, actual screenshot pixels, visible controls, renderer commits and concurrent nondimming/nonfocusable compositor witnesses. Fresh witness colors, absolute screen geometry and current-view checks prevent treating stale/all-black captures as protection.

This only helps under a trusted Android/host/input environment and the declared logger model. A keyboard-only logger need not receive typed secret keys on the pad; screen/pointer/AX/OS/host/firmware observers remain. Timers are main-looper scheduling, **not hard real-time bounds**. Clear/mask/unlink/anonymous memory are **not forensic erasure**.

### Explicit owned readonly-memory parser input

- Real `evaluateMemorySheet/Csv/Xlsx`, `OwnedFormatMemory` and isolated Binder worker; worksheet UI calls the memory APIs explicitly.
- Fresh helper-owned writer unmaps **before** restricting future maps to READ. Memory is passed via real Parcelable/FD, not an inline2MiB Binder payload. No named parser-input-file fallback in these APIs.
- Strict one-carrier, operation, size/length, empty-padding, permission-widening and write-map checks; actual sender/distinct UID, single admission, child network denial and existing FD/watchdog controls retained.
- Native wire attacks, empty/exact64KiB/malformed/oversized CSV, dense/independently produced literal XLSX, recovery, live FileObserver with before/after positive controls and real picker/new-copy/source-preservation regressions passed the focused24-case suite.

**Existing writers survive restriction:** the actual prerequisite counterexample passed before production implementation. The new guarantee needs trusted fresh ownership; it does not revoke arbitrary malicious-parent aliases. Old `evaluateOwned*` APIs still intentionally use regular readonly/unlinked files. Providers, originals, created exports, OS paging and heap copies remain outside a disk-free/erasure claim. A digest/UID does not prove an untrusted worker computed honestly. This is not full PDF/Office/LOK.

## 3. Real SDK37.0/Rabbit1 build work

Live configuration: **AGP9.1.1 / Gradle9.3.1 / Kotlin2.2.21 / maintained JDK17 / min34 / compile37 / target37**.

- Officially listed package is **`platforms;android-37.0`**, not the initially requested `platforms;android-37`. Actual source.properties declares **`AndroidVersion.ApiLevel=37.0`**, base SDK, preview0. Both earlier wrong-spelling/strict-schema failures remain retained.
- SDK jar actually inspected remotely: **43,044,472bytes**, SHA256 `bf1b4387cc7ca94fc6ef684f040d9d16fbf16248e181819f020736ea2053f177`.
- App/privacy/benchmark managed devices migrated from removed `devices` to named-container `localDevices`. Documented temporary `android.builtInKotlin=false` / `android.newDsl=false` preserve external Kotlin; not an AGP10 migration.
- Gradle distribution checksum pinned from actual official metadata. Canonical-property tests reject duplicate last-key-wins overrides and aliases. This does not authenticate old caches/all dependencies.
- Real compile anchor `VERSION_CODES.CINNAMON_BUN=37` and actual installed targetSdk37 passed. Both main builds, JVM/contracts/lint and privacy build/lint passed.

**Compilation/target37 is established; API37 runtime and Rabbit1 GUI are NOT executed.** The all-gates upgrade/product threshold is **not met** because the latest full main regression failed. This buildable tuple is retained as experimental work, not promoted to release/fully accepted migration. Earlier SDK36 candidate `b3f2f1d` remains in Git as a rollback/reference; changing compiler versions without causal evidence is not a fix for the historically intermittent task UI.

## 4. Exact latest native outcomes

All three rows use the same **4bc7637** source candidate. Actual runtime: managed Android API34, not a physical laptop/phone.

| Workflow | PASS / FAIL / SKIP | Whole result |
|---|---:|---|
| [Offline privacy38871422549](https://github.com/jyvplay/OSB/actions/runs/38871422549) |16 /0 /0|SUCCESS|
| [Memory + retained FD/CSV/XLSX38871422568](https://github.com/jyvplay/OSB/actions/runs/38871422568) |24 /0 /0|SUCCESS|
| [Full main38871422566](https://github.com/jyvplay/OSB/actions/runs/38871422566) |121 /1 /0|**FAILURE**|

The focused24 cases overlap the main122; **do not add them as162 unique passes**. Main plus privacy =138 named cases,137 passed/1 failed. Thirteen new R23 native methods passed; this does not erase an older regression failure.

**Exact failing case:** `dev.fusion.shell.R18TaskUiDeviceTest.editCancelArchiveRecreateRestoreAndQueryThroughRealShell`, phase `open/add`, waiting10s for saved revision1. Diagnostic: screen1080×2400; visible rect `(0,128)-(1080,1517)`; IME visible with bottom inset883; actual disk revision0/tasks[]. Status/before-click text were not captured. Cause is **not diagnosed**. IME/geometry/focus/action-delivery are hypotheses, not a proven compiler regression or a fixed flake.

Earlier SDK37 candidate `aceea3a` main38869721151 passed122/0/0 and memory38869720986 passed24/0/0, but its privacy38869720982 was15/1. That is a **different candidate**, not inherited acceptance. Earlier13/1,14/1,15/1 and coordinate14/2 failures and pre-edit repair plans are preserved.

Named receipt gates allow only latest exact-candidate privacy16 and focused memory24; the failed whole main receipt cannot authorize a green full regression. [Machine-readable outcome](verification/r23-outcome.json) · [A–J engineering, hypotheses, negative shapes and alternatives](R23-ENGINEERING.md).

## 5. Executed without Studio / performance honesty

Latest complete SDK-free host pipeline: exit0, **134.057s**, **149 Python methods passed**; real Kotlin2.2.21 production/consumer compilation, **898,161 R23 assertions /160,000 four-event words**, R22 checks37, R21 assertions376, Office reference327 checks (271accepted/56refused/15equal-byte pairs). These are bounded corpora, not full Office or hardware proofs. [Raw host output](verification/r23-host-current.txt).

A separate automatic [API34 profile experiment38869279741](https://github.com/jyvplay/OSB/actions/runs/38869279741), candidate79bb1c3, actually passed generation plus3 measurement methods:837 first-party ART rules, packaged nonempty ART assets,3 samples per startup mode. Cold median1619.21ms; profile-compiled median1616.69ms; Files frame CPU P50≈45.98ms/P95≈137.55ms. **No meaningful speedup/smoothness/physical-budget claim** from these tiny emulated samples. The generated837-rule artifact is retained in the receipt, **not installed into current source assets**. [Raw receipt](verification/r16-measured-38869279741-receipt.json).

Remote CI compressed APK byte observations at4bc7637:

| APK | Bytes |
|---|---:|
| Separate privacy debug |2,534,628|
| Main play debug |28,954,907|
| Main pro debug |28,954,979|

Hashes are in the outcome JSON. This is NOT installed size, app RSS/PSS, whole Android/emulator/host cost or a comparison against Tails. APK/log blob redirects were unavailable locally; no local APK inspection is claimed.

## 6. Try / reproduce

Open **`android/`** in Android Studio Rabbit1, maintained JDK17 and real SDK37.0; choose `privacy`/debug or `app`/proDebug. Use copied data and a disposable API34+ AVD. GUI itself was not run here. [Detailed current laptop/no-Studio instructions](BUILD.md#r23--current-session--no-ime-privacy-try-out).

```sh
cd android
# Linux x86_64: explicit optional host downloads, no SDK/Studio needed
python3 tools/bootstrap_host_tools.py install
python3 tools/r23_lab.py host --tools "$HOME/.cache/fusion-host-r23"

# Real maintained JDK17 + installed Android SDK required, no GUI:
python3 tools/r23_lab.py android
python3 tools/r23_lab.py device --suite privacy --disposable
python3 tools/r23_lab.py device --suite memory --disposable
python3 tools/r23_lab.py device --suite full --disposable  # known full-suite failure above
```

Optional host cache includes an **old JRE17.0.9, not a JDK**; disposable host tests only, NOT a secure production runtime/Gradle JAVA_HOME. Doctor correctly refused local native compilation without javac/SDK. Native executions above are remote real Android, not mocked APIs. No independent subagents, physical lab or new R23 SMT theorem were used.

## 7. Remaining exact gaps / next falsifiers

1. **Immediate highest-information test:** before ONE real task Add tap, capture title/date text, actual add-button bounds and focus/active-window generation against the IME-visible viewport; then record callback receipt, validation/status and disk revision. Add a real IME-visible task-add counterexample before any fix. Do not hide keyboard, switch to direct callback, delete assertion or rerun until green to erase the failure.
2. Actual API37 runtime, Rabbit1 GUI, real third-party hostile overlay/AX service, OEM/accessibility/font/display matrix and physical peripheral/DMA/firmware/host validation remain open.
3. Major browser engine/compositor integration, full Office/LOK fidelity/ABI/transitive licenses and original S29/S30/full workflow/physical/legal gates remain unaccepted. Mechanism-distinct alternatives and their artifacts/gaps are in A–J; no generic WebView+Tor or literal-XLSX subset is relabeled as the original target.
4. Tails-like global routing/amnesia/whole-host trust and a lighter-than-Tails system are not delivered by an APK. New primary research informs the [privacy/hardware PRD](R23-PRIVACY-PRD.md) and [research ledger](research/r23-primary.md); ordinary apps cannot attest hostile hardware/firmware or erase prior external logs.

No PR merge/close or release was performed. Later report/receipt-only changes are not a separate native-tested build and must not be called an all-green HEAD.

Reporting provenance: [470 native/build/test/workflow inputs compared](verification/r23-native-inputs-at-report.json)
against4bc7637. The sole nonidentical executable-tool file has an exactly checked report-note string change
in native_source_audit.py; no validator control flow, Kotlin/native source, Gradle/config/test body or
workflow changed after those native results. Reporting source checks/149 Python methods were rerun,
not a new APK build. This provenance check is not a way to promote the FAILED main gate.
