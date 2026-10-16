# R23 — privacy posture review and honest product boundaries

**Outcome:** one scoped app-level increment was added to the existing separate offline companion. It reports selected Android system observations, retains “unknown” when a value cannot be observed, points the user to Settings, and keeps a permanent disclaimer that it is not a keylogger/malware scan and cannot prove safety or anonymity. The proposed PRD and external research are recorded separately.

**Not delivered:** Tails OS, Tor routing, a global privacy mode, USB policy enforcement, hardware/firmware inspection, a full browser/Office engine, or completion of the original v2.0/v2.1 product. R23 now compiles and lints remotely, but its full API34 instrumentation suite is not green.

**Evidence:** Local Python source/manifest guard and host suite passed, but they are structural only. The first GitHub privacy CI run failed Kotlin compilation. A second exact-source run compiled, linted and built an APK, then ran 10/10 API34 cases: **8 passed, 2 existing workspace cases failed**. The four new R23 posture tests all passed. UI order and the two failing workflow checks have been repaired in the current worktree; a fresh run is pending. Android Studio Rabbit1 GUI, local Android SDK/emulator, USB device and physical hardware are unavailable in this sandbox.

## Runtime inventory — tools actually available/used

- Used: filesystem/shell, local Git, Python **3.11.2**, and the repository's Python validation; new web search and page fetch; source editing.
- Used this turn: Node **22.22.3** / npm **10.9.8** for `npm ci`, the root landing page's `npm run build`, `npx tsc --noEmit`, and `npm audit`; `gh` was used to inspect/watch GitHub checks. The Vite build and TypeScript check passed. `npm audit` reported **5 advisories (4 high, 1 low)** in the existing root web dependency tree; no dependency upgrades were made because it is outside the Android privacy patch.
- Not installed/available: `java`, Gradle runtime, `kotlinc`, Android SDK, `adb`, `sdkmanager`, Android Studio Rabbit1 GUI, emulator, physical Android device, or USB/hardware test fixture.
- No independent agents, local Android compiler/simulator, theorem prover, database, browser/Office engine, independent human, lawyer, or accessibility reviewer was used. Remote Kotlin CI failed once, then compiled successfully on the next run; see exact receipts in H. Multiple tool calls are not independent researchers.
- Fresh primary-source observations and failed page fetch are listed in [R23 research](research/r23-primary.md). Internet research is not a hardware experiment or expert audit.

## A. Formal model

### Admissible objects

1. One invocation of the repository's `:privacy` application (`dev.fusion.privacy`) on Android API 34 or later.
2. A transient `DevicePrivacySnapshot` with bounded fields read from public Android APIs: API level, OS-reported security-patch string, nullable “secure screen lock configured,” nullable USB ADB-debugging setting, nullable current input-method package, nullable system-reported accessibility component IDs, and currently reported non-virtual keyboard device names.
3. The app's own volatile text and link-projection views, which the existing Activity drops on pause/recreation/explicit discard.
4. User-initiated Android Settings activities opened by intents. The OS/user owns all resulting changes.

Not admissible as R23 input/output: a document/provider URI, account, identity, persistent profile, network result, other app's memory, global input-event stream, hardware attestation, firmware state, host process state, or a stored risk score.

### Information and access model

The Android process has an ordinary app UID and may call public read-only APIs (`KeyguardManager`, `Settings.Global`, `InputMethodManager`, `AccessibilityManager`, `InputDevice`) and request system Settings. It has no root/system privileges, device-owner/profile-owner authority, IME or accessibility-service role, VPN role, bootloader/firmware access, USB policy authority, or permission to inspect other app memory. It does not ask for new permissions or `QUERY_ALL_PACKAGES`.

The separate package's manifest omits `INTERNET`; this is a restriction on **this package only**. It does not disable networking for Fusion Shell, Android, cloud providers, other apps, cellular/modem interfaces, the laptop/hypervisor, or external actors. A failed/filtered read is unknown, not false. The displayed service/device strings are system-reported identifiers, not verified identities.

### Resource being counted

- Supported source baseline: min SDK 34; project catalog compile/target SDK 36. Requested compile/target37 remains a separate unpassed requirement.
- One snapshot on Activity composition/resume and explicit refresh; no polling/background watcher.
- Output bounds in source: at most 12 accessibility component strings ×160 UTF-16 units; 12 keyboard names ×96 units; current IME package ×128 units; security-patch string ×160 units.
- Persistent bytes, transmitted bytes, CPU/RSS ceilings, timing tolerances, memory zeroization and total device footprint are **not** established by this implementation. No “lightweight vs Tails” measurement was run.

### Success and failure with quantifier order

For every supported device `D`, OS/API state `S`, and snapshot invocation `i` where an API returns a value, the **bounded observation** succeeds only if:

1. returned values come from the named APIs and remain within the bounds above;
2. exceptions, filtered data, or missing APIs remain unknown/unavailable, not “disabled” or “safe”;
3. the rendered text always includes the non-claim that this is not a keylogger/malware scan and cannot prove device safety/anonymity;
4. Settings buttons delegate to Android and do not write system settings or silently gain authority;
5. no snapshot persistence/network path is introduced, and the pre-existing pause/discard behavior is unchanged.

A counterexample is any fabricated positive/negative observation, output beyond a bound, hidden setting change, permission expansion, persisted/transmitted snapshot, missing permanent disclaimer, or user-visible assertion that a physical/inline/firmware keylogger was checked.

The broader target (“for every app, UID, network route, USB/HID device, boot phase, firmware component, host and external record, prevent keylogging and provide Tails-level anonymity”) is **not** the quantifier domain of this package. The package lacks access to that domain and does not claim the broader predicate.

### Regime, boundaries, tolerances

- Android API34+ source target; current catalog compile/target36; actual API/OEM support may filter values or omit Settings activities.
- USB Protection depends on supported hardware and OS settings. Google's documentation distinguishes locked from unlocked connections, new from already established data sessions, boot and reconnect behavior, and OEM variation.
- Security-patch value is reported as a string only; it is not compared with vendor currentness. An IME/service/device name is not a trust score.
- The snapshot is not continuous monitoring. A Settings action causes `onPause`, which intentionally discards scratch text; returning recreates the workspace and refreshes observations.
- No tolerances for API latency, battery, RSS, session-start time, erase latency, or anonymity are claimed.

### Adjacent, non-substitutable problems

1. Tails live-boot OS vs an Android Activity.
2. Tor routing/bridges vs absence of `INTERNET` in one package.
3. Tor Browser fingerprint resistance vs generic WebView, system browser or VPN.
4. Linux MAC anonymization vs Android Wi-Fi/modem/SIM identities.
5. Device-owner USB data policy vs ordinary app authority.
6. IME no-learning flags vs a malicious input method.
7. Accessibility-service review vs disabling accessibility tools.
8. `FLAG_SECURE` vs external cameras, malicious input, root or compromised OS.
9. View disposal vs RAM/flash forensic erasure.
10. Screen lock vs encryption/boot-before-first-unlock and verified boot.
11. Security-patch string vs verified/current vendor patch status.
12. InputDevice enumeration vs peripheral/cable/firmware authenticity.
13. Dashboard vs antivirus, EDR, forensic examination or hardware attestation.
14. Content minimization vs anonymity.
15. Encrypted persistence vs volatile app state.
16. Browser session model/WebView vs accepted Major browser.
17. CSV/XLSX values projection vs full Office engine/rendering/editing.
18. Isolated child/read-only descriptor vs honest computation/resource containment.
19. Android external-app handoff vs first-party browser/Office.
20. A module with a testable subset vs complete feature acceptance.

More formal product and acceptance detail is in [R23-PRIVACY-PRD.md](R23-PRIVACY-PRD.md).

## B. Outcome space

- **H+ — achievable under the stated ordinary-app constraints:** no-network-permission companion, volatile local text session, secure-window request, bounded read-only observations, explicit unknowns and user-directed Settings. Source and tests were authored; R23 runtime acceptance is pending.
- **H− — not achievable by this APK alone under this access model:** global network anonymity/Tor enforcement, Tails-equivalent boot/session amnesia, device-wide USB policy changes, physical/inline keylogger detection, firmware authentication, or protection of the laptop/host. This is a scoped authority/interface conclusion, not a theorem about managed/rooted/custom Android.
- **H∂ — result changes with conditions:** device owner/profile owner, OEM USB implementation, Android version, lock/boot state, actual active IME, enabled assistive services, the user's hardware and app/account choices.

The declared delivery threshold is compile + lint + current-source Android instrumentation + real lifecycle/Settings paths, followed by a separate supported-device USB/AX review. It is **not met**. Do not promote the implementation beyond source-authored/unverified.

## C. Achievability / impossibility frontier

### Imported results and their hypotheses

- **Tails official documentation** describes a complete OS network/boot/storage workflow: Tor Connection, optional bridges, disappearance by default at shutdown with optional encrypted Persistent Storage, and MAC anonymization of interfaces. Its warnings also say hardware keyloggers and compromised firmware can defeat OS protections, and that metadata/activity reuse can identify users. Hypothesis: the Tails OS/media and its documented threat model are in use. This does not transfer to an ordinary Android app.
- **Google Android Help USB Protection** describes supported Pixel 6+ behavior. Hypotheses include supported device/OS, feature enabled through Advanced Protection, and a locked screen. It documents limits for unlocked/pre-existing connections, boot/reconnect and OEM variation. It is not a universal Android USB-port guarantee and is not enabled by this app.
- **Android Developers input APIs** expose the currently selected IME and some enabled system services. Package visibility/API behavior and OEM policy constrain enumeration. A reported name does not authenticate code or integrity.
- **Android `FLAG_SECURE` documentation** defines screenshot/non-secure-display exclusion, not protection against cameras, hostile IME, hardware capture or compromised firmware.
- **MITRE Mobile ATT&CK T1417** catalogs malicious keyboard and accessibility input-capture techniques. It supports a threat class; it does not prove every service can read every app field or that this simple dashboard detects malware.
- **Previous repository receipts:** R22 docs record `fe8dca2`-candidate API34 focused10/10 and full119/119 for named browser/Office-adjacent workflows; earlier privacy source records `f42193d` 6/6 API34 privacy tests. Those are exact older snapshots, not this R23 source. They cannot be inherited as R23 Android verification.

Sources and direct-fetch/search details: [research/r23-primary.md](research/r23-primary.md). Search scope is primary-source engineering research, not a comprehensive survey of all anonymity/security research.

### Exact open interface

The app can report values offered to an ordinary UID and route the user to Settings. The missing interface is privileged policy + authentic hardware/boot state: the app cannot enforce or attest USB Protection, inspect inline capture, verify an IME binary, globally mediate keyboard events, attest firmware, or inspect the laptop. For network anonymity, the missing interface includes whole-OS route enforcement, leak behavior, audited browser/fingerprinting policy, host trust and identity/content discipline. These do not become solved by a local offline package.

## D. Negative space: 20 apparent solutions that fail

1. No `INTERNET` permission ⇒ **whole phone offline**. Package-local only.
2. `FLAG_SECURE` ⇒ keylogger-proof. It is a screenshot/non-secure-display control.
3. `IME_FLAG_NO_PERSONALIZED_LEARNING` ⇒ no key capture. It is not an IME sandbox.
4. Android default IME ⇒ trusted keyboard. Default is selection, not verification.
5. Empty accessibility list ⇒ no screen observer. APIs can be filtered and other channels remain.
6. One current IME package ⇒ all enabled IMEs known. Current selection is not full installed inventory.
7. No non-virtual keyboard listed ⇒ no physical input device. Enumeration is not hardware inspection.
8. ADB off ⇒ USB safe. HID, peripherals, unlocked sessions and other routes differ.
9. USB Protection on some Pixel ⇒ active on this device. The app cannot read/enable it here.
10. Locked screen ⇒ every USB data path closed at all times. Existing/unlocked/boot/reconnect cases are distinct.
11. Screen lock configured ⇒ data erased. Lock is not erasure.
12. `onPause` clears views ⇒ RAM/flash forensic recovery impossible. No zeroization proof.
13. An “all green” score ⇒ safe/anonymized. The app cannot quantify invisible adversaries.
14. MAC-randomization tip ⇒ this app changed Wi-Fi/modem MAC. No such authority exists here.
15. Tor/VPN badge ⇒ Tails/Tor Browser. Routing, leaks, fingerprinting and identity context differ.
16. Tor route ⇒ login/account/content identity disappears. It does not.
17. Small APK/no telemetry ⇒ lighter than Tails. Total host+guest RSS/storage/power/boot comparison was not measured.
18. Values-only XLSX ⇒ Office engine or metadata scrubber. It is a narrow lossy projection.
19. WebView renderer ⇒ desktop browser. Engine/security/process/extension/accessibility acceptance remains open.
20. Isolated UID/read-only FD ⇒ parser output truthful or CPU/RSS bounded universally. Capability reduction is not semantic proof or a full sandbox.

### Modal shortcut failure and detector

**Shortcut:** “Every visible setting looks favorable, therefore the device is safe/private.” The observation quantifier covers only a few values returned by public APIs; the claim quantifier silently expands to all hidden software, hardware, firmware, host and identities. That inference is invalid.

**Detector authored:** `PrivacyPostureTest.evenAnAllReassuringSnapshotDoesNotClaimTheDeviceIsSafeOrAnonymous` constructs a seemingly favorable posture (secure lock, ADB off, familiar IME, no listed accessibility service or keyboard, future-looking patch string) and requires the report to retain explicit non-scan, hardware-keylogger and anonymity caveats. `failedOrFilteredObservationsRemainUnknownRatherThanBecomingFalse` tests the unknown state. The Kotlin tests have not run here; only the separate Python source guard ran.

## E. Mechanism portfolio — alternatives kept distinct

These are distinct technical mechanisms, not six independent reviewers. This session used no independent agents; candidates are kept separate so evidence for one is not incorrectly inherited by another.

### E1. Separate no-INTERNET volatile companion (implemented baseline + R23 panel)

- **Construction/lemma:** separate APK/UID; no `INTERNET`; discard whole editor on pause; read system values only; keep unknown and limitations visible.
- **Concrete artifact:** `privacy/src/main/AndroidManifest.xml`, `PrivacyActivity.kt`, `DevicePrivacySnapshot.kt`, existing workspace/platform tests, new `PrivacyPostureTest.kt`.
- **Proved portion:** previous source receipts test actual socket denial/screenshot/lifecycle. New R23 source and tests are uncompiled/unrun; Python guard checks only source structure.
- **Exact unresolved interface:** Android/IME/AX/host/hardware trust; settings remain user-controlled.
- **Cheapest falsification:** `:privacy:pixel6Api34DebugAndroidTest`; on device, use a third-party test IME/accessibility service, open Settings with dummy text, return and check state.
- **Gap type:** the bounded local feature is achievable; global anonymity/hardware integrity is a different problem.
- **A(C):** malicious IME receives typed text, accessibility observes UI, external camera records it, or hostile host/OS ignores UI. Keep these as visible non-claims.

### E2. Android Enterprise/device-owner policy

- **Construction/lemma:** a device owner or profile owner of an organization-owned managed profile may apply `DevicePolicyManager` USB data-signaling policy where supported; the OEM USB HAL must support it, and the DPC should check `canUsbDataSignalingBeDisabled()`. A DPC may separately apply managed network policy. Ordinary app code cannot impersonate an owner.
- **Concrete artifact:** state machine `UNMANAGED → explicit enrollment → eligible owner verified → device support checked → policy applied → state independently tested`; any missing transition remains UNKNOWN. Not implemented in R23.
- **Proved portion:** the fetched AOSP guide says disabled USB data signaling prohibits USB data connections except charging, was introduced in Android 12/API 31, and is not supported on all devices. No DPC or enrollment was built or executed.
- **Exact unresolved interface:** enrollment/recovery, owner scope, API/OEM/HAL support, pre-existing/boot/reconnect behavior, all-port/HID coverage, profile-vs-device boundaries and real network leak tests. This is distinct from consumer Pixel USB Protection.
- **Cheapest falsification:** provision disposable managed API34+ hardware; test locked/unlocked, boot/reconnect, existing USB session, policy removal, IPv4/IPv6/DNS and VPN loss.
- **Gap type:** a different managed deployment; not consumer APK behavior and not Tails-equivalent anonymity.
- **A(C):** owner never enrolls or is removed, protection starts too late, existing session persists, or OEM behavior differs. Show owner state and fail closed.

### E3. Use actual Tails on a laptop when OS-level amnesia is required

- **Construction/lemma:** use an OS that owns boot, filesystem, Tor policy, session shutdown and optional encrypted persistence rather than emulating them from an app.
- **Concrete artifact:** operational route through current official Tails installation/verification, boot, Tor, persistence and shutdown docs. No media downloaded/verified/booted by us.
- **Proved portion:** official docs describe these workflows and limitations. No independent Tails build/boot/traffic experiment here.
- **Exact unresolved interface:** firmware/UEFI/hardware, correct media, persistence choice, identity-bearing content/accounts, host and physical surroundings.
- **Cheapest falsification:** clean spare machine with verified current media; packet capture, disposable persistence test, shutdown/reboot examination.
- **Gap type:** a different OS product, not a local app implementation gap.
- **A(C):** malicious USB/firmware/host, reused identifying accounts/files, or an observer correlating both ends. Tails documentation itself retains these limits.

### E4. Pinned first-party GeckoView-class browser engine

- **Construction/lemma:** version-pinned engine/process/profile with brokered navigation and permissions bound to actual origin/tab/renderer lifecycle.
- **Concrete artifact:** API34 local-origin spike: cookie/profile separation; two origins and subframes; grant then navigate/crash/kill; downloads, focus, Z-order, keyboard and AX. No engine dependency added.
- **Proved portion:** repository has session/URL/permission-lease kernels and a WebView surface; they are not a Gecko engine bridge or accepted browser. Research found lifecycle API docs but no pin/license/ABI closure.
- **Exact unresolved interface:** exact engine ABI/license/transitives, frame origins, consent UI, process lifecycle, profile clearing, updates, extensions, downloads, AX and compositor integration.
- **Cheapest falsification:** permission grant to top-level then navigate a subframe/kill renderer before callback; assert no stale grant or surface reuse.
- **Gap type:** bridge is local; full browser remains equivalent to original Major browser scope.
- **A(C):** origin confusion, navigation race, renderer crash after grant, service-worker persistence, restored cookies, extension privilege. Happy-path page load is insufficient.

### E5. Explicit external-browser delegation

- **Construction/lemma:** validate an HTTP(S) URL, show canonical host, require user confirmation, then hand off with Android `ACTION_VIEW`/resolver. External browser owns cookies, history, engine and network.
- **Concrete artifact:** design flow only; no R23 handoff is wired. Android docs show URL intents can be launched without broad package visibility.
- **Proved portion:** strict URL kernel and existing unrelated intents exist; no actual browser handoff test.
- **Exact unresolved interface:** resolver handlers, redirect/scheme confusion, user consent, external account/provider behavior, referral/context leakage and return lifecycle.
- **Cheapest falsification:** malicious resolver + `javascript:`, `file:`, credential URL, redirect and cancellation test; only confirmed canonical HTTP(S) may leave.
- **Gap type:** useful MVP route, explicitly not a first-party browser.
- **A(C):** handler captures URL, logged-in browser joins identities, or redirect changes scheme. Label the app boundary and require confirmation.

### E6. Own bounded CSV / values-only XLSX projections

- **Construction/lemma:** narrow accepted grammar, bounded parser in worker, all retained values visible, explicit loss report, create a new literal document and never save back.
- **Concrete artifact:** existing CSV/XLSX code, strict ZIP/XML admission, isolated worker, UI picker and earlier R22 evidence.
- **Proved portion:** R22 docs record named-candidate API34 and independent host reference cases. No Excel/LibreOffice parity; those receipts predate R23 and are not a fresh build.
- **Exact unresolved interface:** formulas, styles/layout, date/types, macros, ODF, full rendering/editing, general package dialects, user loss review and broader provider/device behavior.
- **Cheapest falsification:** hostile sparse/shared-string/malformed corpus from independent producer; open exported copy in real Excel/LibreOffice; compare exact visible values and source bytes.
- **Gap type:** useful local subset; full Office remains the original engine/fidelity problem.
- **A(C):** unusual semantics/identifiers survive, formatting is misread, user treats raw numbers as dates, provider aliases source. Refuse unsupported inputs, show loss, keep original.

### E7. Optional isolated LibreOfficeKit engine

- **Construction/lemma:** a pinned optional native engine, separate package/process, full transitive-license audit and bounded parent IPC.
- **Concrete artifact:** gate plan: source/binary/license inventory, arm64 + requested 16KiB builds, API34/36/37 install, resource measurements, real edit/save/readback corpus, legal review. No engine added.
- **Proved portion:** only an existing LOK placeholder/module and documented unresolved license/build status. No LOK runtime or legal review.
- **Exact unresolved interface:** licenses/transitives, native ABI/page size, memory/CPU, parser escape, recovery, actual fidelity and legal approval.
- **Cheapest falsification:** complete binary/source license inventory and minimal reproducible arm64/16KiB build before any UI integration.
- **Gap type:** still equivalent to full Office acceptance; may reduce implementation effort after gates.
- **A(C):** transitive copyleft contamination, native escape, silent corruption, resource exhaustion, mistaken “opens” = fidelity. Block main APK until all gates pass.

### E8. External Office-application handoff

- **Construction/lemma:** user chooses an Android document handler; source authority, destination and external-app ownership remain explicit; copy first if edits must not touch source.
- **Concrete artifact:** SAF/`ACTION_VIEW` design with limited URI grant and readback; existing picker primitives are adjacent, not this complete flow.
- **Proved portion:** system pickers and bounded import/export already have exact candidate tests; no external Office app route is proven here.
- **Exact unresolved interface:** URI grant modes, output aliasing, cloud provider/network, cancellation, source preservation, app absence, save-back and recovery.
- **Cheapest falsification:** receiver app attempts source overwrite; provider revokes access; cancel chooser; verify destination digest and source unchanged.
- **Gap type:** compatibility alternative, not first-party Office completion.
- **A(C):** receiver/cloud syncs private data or provider aliases source. Copy-by-default, explicit external warning, no persistent grant.

### E9. Android isolated parser worker + owned read-only FD

- **Construction/lemma:** copy bounded input; close writer; unlink temporary name; reopen read-only descriptor; pass only capability to isolated service; validate bounded reply framing and lifetime.
- **Concrete artifact:** existing `format/pdf` worker/lease, R22 CSV/XLSX operations and actual API34 boundary tests.
- **Proved portion:** prior device tests prove only named UID/FD/Binder/socket cases. No proof of semantic honesty or universal CPU/RSS limits.
- **Exact unresolved interface:** every parser call-site, timeout/kill/reply ownership, concurrent mutation, provider block, crash cleanup, resource limits and child result trust.
- **Cheapest falsification:** writable-alias/seek/rebind/late-reply/death/socket/oversize/blocked-input/parent-crash attacks on actual device.
- **Gap type:** local authority reduction; full parser sandbox remains the original coverage/resource problem.
- **A(C):** child returns digest-matching lie, stale Binder reply wins, unbounded parser path bypasses worker. Treat outputs as data and enumerate call sites.

### E10. WASM parser/runtime sandbox

- **Construction/lemma:** pinned WASM runtime, deny-by-default imports, one request per instance, explicit host capabilities, memory/fuel limits.
- **Concrete artifact:** executable spike matrix for runtime/toolchain/ABI, host-call map, adversarial loop/memory corpus, supply-chain/license review. No runtime installed or tested.
- **Proved portion:** none for this repository; design only.
- **Exact unresolved interface:** Android runtime/ABI/legal dependencies, fuel correctness, memory ceiling, host-call deputy behavior and whether runtime expands attack surface.
- **Cheapest falsification:** hostile loop, memory growth, import escape and crash harness on exact Android runtime; measure kill latency and host authority.
- **Gap type:** alternate local sandbox mechanism; product/parser coverage still needs full acceptance.
- **A(C):** interpreter bug, unmetered host call, runtime update or malicious module. “WASM” is not itself a security proof.

### E11. Vertical-slice local-first productivity

- **Construction/lemma:** ship one workflow with explicit data owner, revision semantics, lifecycle, save/readback and export contract at a time; do not wait for full browser/Office parity.
- **Concrete artifact:** existing Tasks/Notes/CSV worksheet/XLSX flows; R17–R22 exact-candidate reports. No productivity code added in R23.
- **Proved portion:** narrow local workflows exist and have prior scoped tests. No implication of reminders, calendar recurrence, collaboration, sync, Meet, or complete desktop suite.
- **Exact unresolved interface:** shared identity/transactions, timezones/notifications, background work, backup/restore, sync, accessibility, window integration and broad formats.
- **Cheapest falsification:** one API34 process-death matrix for Task create/edit/archive/restore/search; kill between every write/ack and verify durable revision/readback.
- **Gap type:** local workflow gaps are closable; the full suite is an aggregate integration problem.
- **A(C):** stale revision, duplicate import, process death, locale/timezone shift, notification denial or false “saved” toast. Require durable readback and recovery UX.

### E12. OS-managed Work Profile / secondary user

- **Construction/lemma:** managed Android DPC creates separate work profile/user and installs apps/data there; the platform owns separation and deletion, not Fusion.
- **Concrete artifact:** deployment test design: check managed-user feature; explicit provisioning; verify separate profile data; attack cross-profile URI/clipboard/notification/intent flows; disable/remove profile and check documented cleanup. No DPC or profile is created in R23.
- **Proved portion:** Android Enterprise docs describe DPC-controlled work profiles and separation; no profile was provisioned/tested here.
- **Exact unresolved interface:** OEM support, DPC authority, cross-profile flows, account/notification linkability, managed provider/network behavior and data recovery.
- **Cheapest falsification:** install same app personal/work, cross-share selected test file, verify no hidden source grant, turn profile off/remove and inspect test data lifecycle.
- **Gap type:** compartmentalization alternative for managed devices; not Tails, global anonymity, physical-keylogger defense or full productivity.
- **A(C):** allowed cross-profile share, admin configuration leak, linked account, or user deletes work profile unexpectedly. Badge the context and test handoffs.

## F. Concrete artifacts returned

1. **Production source:** `privacy/src/main/java/dev/fusion/privacy/DevicePrivacySnapshot.kt` (bounded nullable observations, unknown-preserving formatting, permanent hardware/anonymity caveat).
2. **UI:** `privacy/src/main/java/dev/fusion/privacy/PrivacyActivity.kt` (refresh; keyboard, accessibility, developer, security Settings intents; no system-setting writes; screen/session handling retained).
3. **Android tests authored:** `PrivacyPostureTest.kt` (untrusted control/bidi text sanitization, all-reassuring counterexample, unknown values, real API bounds and visible controls). Not run.
4. **Host checker + unittest:** `tools/r23_privacy_check.py`, `tools/test_r23_privacy.py`. Structural only.
5. **Research/PRD:** `docs/research/r23-primary.md`, `docs/R23-PRIVACY-PRD.md`, this A–J record.
6. **Traceability:** `module-testability.json` / `MODULE-TESTABILITY.md` updated; `FULL-SPEC-REVIEW.md` and hashed source inventory regenerated.

No new Android module was created or marked complete. No APK is present.

## G. Second-order adversary `A(C)`

Each candidate gets an attacker adapted to its own structure; attacks and minimum falsifiers are summarized below. An early green test cannot be inherited by a repaired candidate.

| Candidate | `A(C)` | Falsifying test |
|---|---|---|
| E1 volatile companion | malicious IME, accessibility observer, camera, stale framework state | actual alternate IME/AX + pause/Settings return and existing screenshot/socket/lifecycle controls |
| E2 device owner | no enrollment, owner removed, boot gap, prior USB session, OEM divergence | managed-device lock/unlock/boot/reconnect/removal matrix |
| E3 Tails | modified USB/firmware/host, identity-bearing account/content, traffic correlation | verified media + clean hardware + packet-capture/persistence experiment |
| E4 engine browser | subframe origin confusion, navigation/grant race, crash, service worker/profile persistence | real-origin grant/navigate/kill/restart/browser-storage test |
| E5 external browser | malicious resolver, account cookies, redirect/scheme confusion | intent chooser/cancel/malicious handler/redirect test |
| E6 projections | unseen semantics, hidden identifiers, resource bomb, source alias | independently produced hostile corpus + exact source digest/readback + real Office opening |
| E7 LOK | transitive license, native escape, silent data corruption | full license inventory first; 16KiB/arm64 build, isolation and fidelity corpus |
| E8 external Office | cloud sync, source overwrite, long-lived grant | malicious receiver/provider-revocation/source-unchanged test |
| E9 isolated parser | child forges truthful-looking reply, stale Binder, worker bypass, deadlock | untrusted reply/FD mutation/timeout/death/socket/oversize cases on device |
| E10 WASM | runtime exploit, host-call deputy, fuel/memory bypass, malicious module update | exact runtime hostile-loop/import/memory/update harness |
| E11 productivity | stale revisions, kill between commit/ack, duplicate import, bad timezone | process-death/revision/readback matrix per workflow |
| E12 Work Profile | cross-profile intent/share, linked account, unexpected profile deletion | personal/work profile cross-share and removal lifecycle test |

The patch-specific attack for R23 is E1's most dangerous false inference: a deliberately favorable snapshot must still say it cannot inspect hardware or prove safety. The Android version of that test remains pending.

## H. Verification: observed evidence only

### Actually executed this turn

- `python3 tools/r23_privacy_check.py` — **PASS**, source/manifest/UI/test-presence contract only. Output explicitly says Kotlin/Android, Settings, IME, accessibility, USB and hardware were not executed/verified.
- `python3 -m unittest tools.test_r23_privacy -v` — **1 Python test PASS**, exercising the host source guard only. `python3 -m unittest discover -s tools -p 'test_*.py' -v` — **122 Python host tests PASS**, including the structural R23 guard; none compiles or runs the Android app.
- `python3 tools/module_testability.py` then `--check` — **PASS**, 48/49 narrow subset count; source hashes checked against reviewed inventory.
- `python3 tools/native_source_audit.py` then `--check` — **PASS**, requirement/ownership inventory regenerated for the added Kotlin source. This is lexical/source ownership, not compiler validation.
- `python3 tools/check_project.py` — **PASS**, 231 requirement rows, 30 spike IDs, 49 modules, catalog/XML/privacy/traceability/source checks.
- GitHub privacy workflow [38859445946](https://github.com/jyvplay/OSB/actions/runs/38859445946), commit `a0b9de7e41ed33773a127ced95d299e712d85e71` — **FAIL before tests**: `:privacy:compileDebugKotlin` reported unresolved `InputDevice.hasSource` in `DevicePrivacySnapshot.kt:87`; no APK or device case. Repaired with the public `sources` bitmask.
- Privacy workflow [38860941610](https://github.com/jyvplay/OSB/actions/runs/38860941610), commit `b9451f1c79f7b054b226aba28c375a628f232f22` — Kotlin compile, `:privacy:assembleDebug` and `:privacy:lintDebug` **PASS**. It built `privacy-debug.apk` (2,506,950 bytes; SHA-256 `4de396dbec81cf4d41e15f93c5ff58ccc4762bcff5132d96d01d2a85e956a10c`) and executed **10/10** API34 cases: **8 PASS, 2 FAIL**. All four new R23 posture tests passed, as did both platform tests. Failures: `actualTypingDiscardCannotRestoreOldEditorUndoAndRecreationStartsEmpty` (UIAutomator could not find the editor before scrolling) and `pauseDropsBothFieldsAndDisablesSavedHierarchyAndClipboardActions` (`ActivityScenario` stayed RESUMED instead of reaching CREATED). Worktree repair puts the primary tasks before the lengthy posture guidance, explicitly scrolls to the editor, and tests discard via an actual keyboard-Settings handoff; rerun pending. The APK artifact was uploaded by CI but not downloaded locally.
- Separate Android native workflow [38859445862](https://github.com/jyvplay/OSB/actions/runs/38859445862) completed at the first commit: traceability, app assemble, JVM tests, contracts and lint job **PASS**; the `:app` API34 managed-device test job **FAIL**. That job did not run `:privacy` posture tests. Exact device-case counts were not recovered because the artifact-host download returned EOF; no counts are inferred.
- `npm ci`, `npm run build`, and `npx tsc --noEmit` — **PASS** for the root Vite landing page only; no Android source is included. `npm audit` reported **5 advisories (4 high, 1 low)** in the existing root web dependency tree; no dependency upgrades were made in this Android-focused patch.
- Runtime checks confirmed Python 3.11.2, Node 22.22.3 and npm 10.9.8; local `java`, `gradle`, `kotlinc`, `adb`, and `sdkmanager` are absent. `gh` was used to inspect/watch GitHub checks.
- Fresh web queries/page fetches are recorded in `research/r23-primary.md`; the direct English Tails warnings fetch failed HTTP500 and its current official search result was used for that snippet. The AOSP USB signaling guide was fetched. No independent expert/reviewer or hardware test.

### Not executed

No fully passing R23 API34 instrumentation suite exists: one remote run compiled/linted, built an APK and executed all 10 named cases, with 8 passing and 2 failing. Its secure-window screenshot and socket-denial controls passed, as did all four new posture tests. The two remaining lifecycle/UI cases have been repaired in the worktree but not rerun yet. No physical USB device, keyboard, hardware/firmware inspection, external browser, Office engine, network leak test, thermal/RSS comparison, formal proof, or Rabbit1 GUI run occurred. Historical R22 receipts concern a different candidate and do not verify R23.

## I. Repair and fresh gates

R23 inherits no Android runtime trust from earlier R20/R22 receipts. The next exact-source gate is:

```sh
cd android
python3 tools/check_project.py
python3 -m unittest discover -s tools -p 'test_*.py' -v
./gradlew :privacy:assembleDebug :privacy:lintDebug :privacy:pixel6Api34DebugAndroidTest
```

Then add the current-source Android results for: IPv4/IPv6 socket denial + Unix-socket positive control; FLAG_SECURE screenshot marker positive/negative control; whole-editor discard; actual Settings navigation/return; actual active-IME and accessibility behavior; and all four R23 posture tests. After any repair, regenerate hashes/ledgers, rerun every affected gate, and add at least one new attack targeting the repair. The lowest-cost patch attack is to make one API unavailable/filtered and ensure it remains UNKNOWN without dropping the permanent disclaimer.

A physical supported Pixel matrix must separately check the actual OS USB Protection toggle/state in Settings, locked/unlocked new connection, previously established connection, boot, reconnect delay and OEM behavior. An emulator result cannot close that hardware gate.

## J. Strongest artifact, exact remaining gap, next highest-information test

**Strongest new artifact:** app-local, no-network, bounded read-only observations + a settings handoff and a counterexample test designed to prevent “all visible checks passed ⇒ safe.” It improves user guidance without claiming to defeat input capture. Host structural tests passed.

**Exact gap:** no Kotlin/API compile or runtime evidence; actual Settings/IME/accessibility and lifecycle transitions remain untested on this source; USB state is neither controlled nor attested; hardware/firmware/host security remains outside app access. Original browser, Office, sandbox, productivity, release, API37, 16KiB, physical, legal and accessibility requirements remain incomplete.

**Next highest-information test:** run the existing privacy workflow against this exact source on a disposable API34 managed device (`:privacy:assembleDebug :privacy:lintDebug :privacy:pixel6Api34DebugAndroidTest`). It can cheaply falsify imports, Android API availability, resource IDs, lifecycle regressions and the modal-shortcut test. The next test after that is a supported physical Pixel 6+ USB Protection matrix—not another source-only audit.
