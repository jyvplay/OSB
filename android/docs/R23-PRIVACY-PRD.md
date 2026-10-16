# R23 proposed privacy-workspace PRD addendum

**Name:** Offline Privacy Workspace and Device Posture Review. **Not** “Tails mode,” an anonymous mode, a VPN, or an OS hardening service. This is a proposed app-scoped addendum; it does not rewrite the original 231-row requirements ledger or claim an original requirement complete.

## Product decision

For the Android app, ship useful local privacy behavior that the app can actually control: a separate no-INTERNET package, no saved draft, explicit session discard, a secure window, blocked clipboard/content routes, and a read-only device-posture panel that directs the user to Android Settings. Do not attempt to recreate Tails' boot chain, Tor enforcement, Linux MAC randomization, or encrypted live-system persistence inside an Activity.

If the user needs an actual amnesic Tor workstation, the honest route is a supported Tails boot on a trusted laptop and verified media/process, subject to the user's threat model. This Android app is not an alternative with equivalent protections.

## A. Formal model

### Admissible objects

- A single running instance of `dev.fusion.privacy` on Android API 34 or later, installed as the repository's separate `:privacy` application/UID.
- A transient `DevicePrivacySnapshot` containing only: OS-reported API level and security-patch string; a nullable screen-lock-configured observation; nullable USB ADB-debugging setting; nullable current IME package; nullable, bounded list of enabled accessibility-service component IDs; and a nullable, bounded list of currently reported non-virtual keyboard device names. A null list means the API observation was unavailable; an empty list means only that none were listed in that snapshot.
- The app's current volatile scratch text and link projection, both discarded on Activity pause/recreation or explicit discard.

No document URI, imported payload, account, network transaction, persistent user preference, system-wide event stream, or inferred identity is an admissible R23 object.

### Information/access model

The ordinary app process may call public, read-only Android APIs (`KeyguardManager`, `Settings.Global`, `InputMethodManager`, `AccessibilityManager`, and `InputDevice`) and may ask Android to launch Settings. It has no root, device-owner, accessibility-service, IME, VPN, privileged Settings, USB-host-control, kernel, firmware, or other-app memory authority. A Settings launch delegates to the system UI; the user makes any configuration change.

The current app has no `INTERNET` permission. That constrains this package, not the phone, other UIDs, cloud document providers, the host laptop, a connected network, or the user's other apps. Accessibility and IME observations can be filtered or unavailable. An empty list is not an all-clear result.

### Resource counted

- API regime: module `minSdk=34`; root catalog currently `compileSdk=36`, `targetSdk=36` (not a claimed API37 build).
- Each observation is read at Activity render/explicit refresh; no background watcher, network request, database/file write, or system-setting write.
- Output caps: at most 12 accessibility service IDs of 160 UTF-16 code units each; at most 12 keyboard names of 96 UTF-16 code units each; IME package 128 units; security-patch display 160 units. Failed observation is `unknown`, never “disabled.”
- The UI presents no security score or timing guarantee. RAM lifetime/zeroization, storage remanence, and memory-footprint comparison are not measured.

### Success and failure predicates

For every supported device `D`, OS state `S`, and invocation `i` where the public API call returns, success for the **bounded observation feature** means:

1. the output contains only values read from the stated public APIs, within the caps above;
2. an exception, filtered result, or unavailable service is rendered as unknown/unavailable, not as a negative finding;
3. the report always states that it is not a keylogger/malware scan and cannot prove safety or anonymity;
4. tapping a Settings action requests the corresponding system Settings screen without the app changing a setting or acquiring extra authority;
5. the snapshot is not persisted or transmitted by this app and the scratchpad's prior pause-discard contract remains intact.

A failure is any fabricated/fallback “safe” value, over-limit output, exported network/persistence path, direct system-setting write, lost safety disclaimer, or claim that the panel detects a physical/inline/firmware keylogger. A native build/test failure is not waived by an older green run.

For **global anonymity or hardware-keylogger prevention**, the success quantifier would have to range over every app, UID, network route, system service, attached peripheral, boot phase, firmware component, host, and external record. This app has no such access, so it does not satisfy that larger predicate.

### Parameter regime and boundary conditions

- Android API 34+; system Settings activities may be OEM-specific or absent; API fields may be filtered/unavailable.
- USB-protection behavior depends on supported device/model, OS feature, lock state, whether a USB data session was started before locking, and boot/reconnect timing.
- The panel reports a snapshot only; it does not monitor changes after display except when refreshed/on resumed.
- A reported security-patch date is not checked against a vendor's current patch level. A listed keyboard/service is not classified as benign or malicious.

### Adjacent but different problems not substituted

1. Tails' live-boot OS semantics vs an Android app session.
2. Tor routing and bridge/captive-portal behavior vs this package's missing Internet permission.
3. Tor Browser fingerprint defenses vs generic browser, VPN, or external browser use.
4. MAC-randomization of network interfaces vs app-local settings.
5. Android device-owner USB policy vs ordinary app permission.
6. IME advisory flags vs a malicious IME receiving input.
7. Accessibility-service review vs disabling assistive technology.
8. Screenshot exclusion (`FLAG_SECURE`) vs physical cameras or input capture.
9. Clearing an editor/view vs secure RAM/flash erasure.
10. A security-patch string vs proven current updates or verified boot.
11. A list of attached input devices vs proof a device/cable/firmware is genuine.
12. A posture dashboard vs antivirus, EDR, forensic inspection, or hardware attestation.
13. Content minimization vs identity anonymization.
14. Encrypted persistence vs volatile/no-persistence behavior.
15. This workspace vs global device network privacy, other apps, modem/SIM identifiers, or laptop-host confidentiality.
16. A bounded browser session kernel/WebView vs the full Major browser.
17. A values-only XLSX projection/CSV table vs an Office editor/renderer.
18. An isolated child and read-only descriptor vs honest computation or a complete hostile-parser sandbox.
19. External app handoff vs first-party browser/Office functionality.
20. A runnable test or source-present module vs full feature acceptance.

## B. Outcome space

- **H+ (bounded target achievable):** app-local volatile workspace, process/package separation, UI-visible observations, and user-directed Settings navigation can be implemented under the access model above.
- **H− (larger target impossible under this ordinary-UID contract):** this app alone cannot claim to make all device traffic anonymous, establish a Tails-equivalent boot session, enforce USB data signaling for the whole device, or inspect an inline hardware keylogger/firmware. This is an authority/interface boundary, not a claim about a custom OS, root, or a managed device.
- **H∂ (conditions change result):** device-owner policy, a supported Advanced Protection implementation, API/OEM behavior, boot state, and physical access change what can be enforced or observed. The actual model/OS must be tested; an app cannot infer those conditions from a generic “privacy” setting.

**Evidence threshold for calling R23 delivered:** successful Kotlin/Android build and lint, all API34 privacy companion cases on the exact source snapshot, targeted lifecycle/Settings/unknown-state tests, and source/manifest audit. A separate physical-device review is still required for supported USB hardware behavior. The second remote run compiled/linted and passed all four new posture tests, but the full suite is **8/10** because two workspace lifecycle/UI cases failed. The third run then failed Android-test source compilation on `UiObject.exists` before tests; that assertion is removed in the current worktree. A fresh CI rerun is pending, so R23 is not accepted.

## Product requirements and implementation status

| ID | Requirement | Current status | Acceptance / limitation |
|---|---|---|---|
| PRIV-R23-01 | Keep companion in separate UID; no `INTERNET`, backup, or cleartext route | Existing manifest | Re-run manifest check and actual IPv4/IPv6 socket-denial test on the current build. Does not constrain other apps. |
| PRIV-R23-02 | Do not restore/persist scratch text; discard whole editor on pause/recreation | Existing app behavior | Preserve prior native lifecycle/IME tests; no forensic-erase claim. |
| PRIV-R23-03 | Secure-window screenshots/non-secure display exclusion; clipboard and rich content refused | Existing app behavior | Re-run existing marker positive/negative control. No defense against camera, hostile OS, or keylogger. |
| PRIV-R23-04 | Show read-only local API observations with per-field unknowns and bounded values | Production Kotlin compile/lint and all four R23 posture tests passed in run 38860941610; 8/10 total cases. Run 38861923458 caught a test-source compile error before cases; corrected source awaits rerun. | `DevicePrivacySnapshot.kt`, `PrivacyPostureTest.kt`; CI built APKs but no safety/trust verdict. |
| PRIV-R23-05 | Link to keyboard, accessibility, developer, and security Settings; never toggle settings itself | R23 source and UI assertions authored | Actual Settings transitions/OEM availability still require managed API34/36/37 and physical review. Returning from Settings discards the scratch session by design. |
| PRIV-R23-06 | Explain supported OS-level USB protection and limitations; advise user not to trust unknown hardware | R23 copy authored | The app does not read/attest USB Protection state or block USB. Confirm current device support in the OS UI/docs. |
| PRIV-R23-07 | No “Tails mode,” global anonymity, secure deletion, or lighter-than-Tails claim | Documentation and UI copy | Keep this boundary in every future network/backup/browser feature and marketing surface. |
| PRIV-R23-08 | Provide actual Tails-on-laptop route for users who require that threat model | Documentation only | The app cannot verify the laptop's boot chain or Tails media; follow current Tails docs and verify official media separately. |

## C. Achievability/impossibility frontier

### Best-known achievable slice

- This package can be isolated from Fusion Shell by Android's app UID and its manifest can omit `INTERNET`; existing `PrivacyPlatformTest` contains actual socket-denial checks for API34 on earlier recorded source.
- The Activity already requests `FLAG_SECURE`, discards editor objects on pause, does not save view state, blocks copy/cut/paste and rich-IME content, and refuses oversized input atomically. Prior device receipts are only evidence for the exact candidate named in those receipts.
- The R23 code adds **read-only observations**, explicit “not scanned” disclosures, and buttons that launch system Settings. A test deliberately supplies a seemingly reassuring snapshot and requires the non-claim to remain visible; this catches the modal shortcut “all checks look good ⇒ safe.”

### Strongest impossibility/boundary results

- Tails' own documentation warns that a physically installed keyboard keylogger or compromised firmware can defeat OS-level protection. Therefore an ordinary in-app probe cannot honestly claim it closed that class.
- Google's supported-device USB protection blocks new USB data while locked but documents already-open/unlocked, boot, delay, and OEM boundaries. A button that opens Settings is not proof that the policy is active.
- Android exposes a current IME and selected settings observations; package-visibility and OEM/system behavior prevent turning those into a universal “all observers absent” theorem.

### Exact open interface

The closest available API surfaces are read-only, advisory observations and system-Settings handoff. The missing interface is **privileged device-policy/boot/peripheral authenticity**: ordinary app code cannot reliably establish device-owner status, enable the feature, attest USB state across every connection/boot phase, enumerate inline devices, or authenticate firmware. For global anonymity, the missing interface is a complete OS network enforcement + audited browser identity/fingerprinting policy and physical/host trust model. These remain separate products/gates.

## D. Negative space: 20 look-alikes that fail the contract

1. `INTERNET` absent in this APK ⇒ **all apps are offline**. False: it is package-scoped.
2. `FLAG_SECURE` ⇒ keylogger/camera proof. False: it concerns screenshots/non-secure displays.
3. `IME_FLAG_NO_PERSONALIZED_LEARNING` ⇒ no key capture. False: it is not a hostile-IME containment boundary.
4. Android's default keyboard ⇒ vetted keyboard. False: selected status is not a trust evaluation.
5. Empty accessibility list ⇒ no observer. False: filtering, other channels, privileged code, and APIs outside this list remain.
6. One detected keyboard name ⇒ authenticated keyboard. False: names are self-reported and inline capture may not enumerate.
7. No non-virtual keyboard reported ⇒ no physical input path. False: device inventory is not a hardware audit.
8. ADB setting off ⇒ no USB attack. False: HID/peripherals, OEM paths, unlocked connections and other interfaces are distinct.
9. USB Protection shown in marketing ⇒ active on this device. False: availability/state are device-specific and not read by this app.
10. Locked-screen USB protection ⇒ all USB data blocked at all times. False: documented unlocked/existing-session/boot/reconnect boundaries.
11. Secure screen lock configured ⇒ secrets erased on lock. False: lock is access control, not erasure.
12. `onPause` clears a view ⇒ RAM/flash forensics impossible. False: no zeroization/remanence proof.
13. A “privacy score” of 100% ⇒ anonymity. False: the app cannot observe the global adversary or identity linkages.
14. MAC-randomization guidance ⇒ app changed a modem/Wi-Fi MAC. False: OS/network-interface authority is absent.
15. VPN/Tor status icon ⇒ Tails or Tor Browser behavior. False: routing, leaks, fingerprinting, identity logins and exceptions differ.
16. A Tor route ⇒ logged-in identity is anonymous. False: accounts/content can identify the user.
17. A small APK/no telemetry ⇒ lighter than Tails. False: total host+guest storage, RSS, power, boot, and workload were not measured.
18. Values-only XLSX import ⇒ Office engine or metadata cleaner. False: it is a lossy, bounded projection.
19. WebView renderer ⇒ desktop browser. False: engine/process/origin/extensions/permission/AX/security acceptance is missing.
20. Read-only FD/isolated UID ⇒ parser cannot lie or all resource attacks stop. False: authority reduction does not establish semantic truth or bounded CPU/RSS on every platform.

### Modal shortcut failure and detector

**Shortcut:** “The observed settings are favorable; therefore the device is safe/private.” This incorrectly changes an existentially partial observation into a universal claim over hidden components. The observations quantify only over values returned by a few public APIs, while a hardware keylogger/firmware adversary lies outside the observable set. **Detector:** `evenAnAllReassuringSnapshotDoesNotClaimTheDeviceIsSafeOrAnonymous` constructs a snapshot with a lock configured, ADB off, a familiar IME, no listed accessibility services, no listed keyboard and a future-looking patch string; it requires the report to retain explicit hardware/keylogger and anonymity disclaimers. The test is authored but has not run on Android in this sandbox.

## E. Mechanism portfolio (distinct constructions retained)

The candidates below are not independent reviewers: they are serially authored mechanisms in one session. Their mechanisms are deliberately kept separate instead of treating them as interchangeable. E11–E12 are portfolio alternatives and existing-scope paths, not R23 code delivered by this patch.

### E1. Separate no-INTERNET volatile companion (implemented baseline + R23 panel)

- **Construction/lemma:** separate Android package/UID; manifest omits network permission; each Activity pause drops the full editor object; posture fields are sampled from public APIs and rendered with unknown-state and non-claim text.
- **Artifact:** `privacy/src/main/AndroidManifest.xml`, `PrivacyActivity.kt`, `DevicePrivacySnapshot.kt`, existing `PrivacyPlatformTest.kt` and `PrivacyWorkspaceTest.kt`, new `PrivacyPostureTest.kt`.
- **Proved portion:** prior source has a recorded API34 socket/screenshot/lifecycle run; R23 adds source and adversarial test but no current-snapshot compile/run evidence. Static manifest/source checks are not runtime proof.
- **Exact unresolved interface:** trust in Android/IME/accessibility/laptop/hardware; settings change is user-controlled.
- **Cheapest falsification:** run `:privacy:pixel6Api34DebugAndroidTest`; test with an alternate IME/accessibility service and return from real Settings while entering dummy text.
- **Gap type:** local feature is achievable; global anonymity/hardware integrity is a different, larger problem.
- **A(C) attacker:** malicious IME receives text; enabled accessibility tool observes UI; external camera records screen; compromised host or device ignores the app's claims. The second-order response is not to label these absent but to keep them out-of-scope in the UI and inspect the native paths.

### E2. Android Enterprise/device-owner policy

- **Construction/lemma:** a device owner or profile owner of an organization-owned managed profile may use `DevicePolicyManager.setUsbDataSignalingEnabled` where supported; an OEM-updated USB HAL is required, and the controller should check `canUsbDataSignalingBeDisabled()`. Separately, a DPC may apply managed network policy. Ordinary activity code cannot stand in for explicit owner provisioning.
- **Concrete artifact:** executable deployment design: `UNMANAGED → explicit user/IT enrollment → eligible owner confirmed → OEM/device support checked → policy applied → OS-reported state tested`; any missing transition stays `UNKNOWN`, never “protected.” The R23 app intentionally performs none of these transitions.
- **Proved portion:** the fetched AOSP guide documents the USB-signaling policy boundary and says disabled policy prohibits USB data connections except charging; it was added in Android 12/API 31 and is not supported on all devices. No actual controller, device enrollment, or policy run was performed.
- **Exact unresolved interface:** enrollment and owner scope, OEM/HAL/API behavior, removal/recovery UX, whether every relevant port/HID/data path is controlled, and independent network leak tests. Consumer Pixel USB Protection is a separate OS feature and must not be conflated with this DPM policy.
- **Cheapest falsification:** provision a disposable supported managed device; apply policy; test reported capability/state, locked/unlocked, pre-existing connections, boot/reconnect, wired/Bluetooth keyboard and owner removal.
- **Gap type:** local managed-device control is a different deployment; global Tails-like anonymity remains equivalent to a larger OS/network problem.
- **A(C) attacker:** user never enrolls, owner is removed, exploit runs before unlock, pre-existing connection persists, or OEM implementation differs. The deployment must expose enrollment/state and fail closed; it cannot be marketed as a universal consumer setting.

### E3. Use actual Tails on a laptop where OS-level amnesia is the requirement

- **Construction/lemma:** choose the OS product that owns boot, root filesystem, Tor firewall policy, session shutdown and optional encrypted persistence, rather than trying to emulate them from one Android UID.
- **Concrete artifact:** supported-use route, not a code module: follow current official Tails installation/verification, boot, network, persistence, and shutdown docs on trusted hardware. The R23 research page records the actual official interfaces and caveats.
- **Proved portion:** official docs describe Tails' Tor connection workflow, default disappearance at shutdown with optional encrypted persistence, and explicit hardware/firmware limitations. We did not download, verify, boot, benchmark or independently audit Tails media.
- **Exact unresolved interface:** hardware/UEFI/firmware trust, correct official media verification, user threat model, persistence choices, Tor limitations, and physical surroundings.
- **Cheapest falsification:** a clean spare laptop boot with capture of actual NIC traffic, inspect persistence configuration and shutdown/reboot behavior using disposable data, and compare against the current official release docs.
- **Gap type:** a different OS/product, not a local implementation gap in `:privacy`.
- **A(C) attacker:** modified laptop/firmware/USB, a host/hypervisor, a user who stores or reuses identifying files/accounts, or an observer who correlates both ends of traffic. The official docs themselves retain those limits.

### E4. First-party browser using a pinned, audited GeckoView-class engine

- **Construction/lemma:** use one version-pinned browser engine with a separate profile/process and a brokered permission/navigation/download interface; bind permission leases to real engine callbacks, origin, tab and renderer lifecycle.
- **Concrete artifact:** executable spike design: on API34, serve two origins locally; verify cookies/storage separation, prompt/revoke on navigate/crash/kill, frame-origin scoping, download ownership, z-order/compositor focus and keyboard/AX semantics. No Gecko dependency is added in R23.
- **Proved portion:** current repository contains bounded browser-session/URL/permission-lease kernels and a WebView surface. Those do not prove a real engine bridge, profiles, lifecycle, or accepted Major browser. Fresh source research confirmed Gecko lifecycle callbacks exist but does not pin an ABI/license.
- **Exact unresolved interface:** exact engine release/ABI/license and full transitive inventory; per-frame origin and real permission UI; renderer process/OOP behavior; downloads, extensions, updates, AX, process death, privacy profile and compositor integration.
- **Cheapest falsification:** real engine proof-of-concept with a navigation/crash between permission approval and callback; assert stale permission cannot be reused and no stale surface receives input.
- **Gap type:** the engine bridge is local; full browser acceptance remains equivalent to the original Major browser scope.
- **A(C) attacker:** a subframe asks for permission under a trusted top-level origin, navigation races a grant, renderer crashes after grant, a service worker persists identity, profile restoration resurrects cookies, or an extension escapes an allowlist. One successful happy-path page is not sufficient.

### E5. Explicit external-browser delegation (MVP alternative, not a browser)

- **Construction/lemma:** offer a user-confirmed handoff of a validated HTTP(S) URL through Android's resolver to a separately installed browser; clearly leave the Fusion surface and state that cookies/history/network/privacy belong to that external app.
- **Concrete artifact:** user-flow/state-machine design: `URL captured → display canonical host → user confirms → ACTION_VIEW/chooser → external app owns navigation`; cancellation leaves the internal session unchanged. No handoff is newly wired by R23.
- **Proved portion:** existing URL admission is strict and the main app already uses Android intents for some external operations; that is not evidence of this browser handoff or ownership warning.
- **Exact unresolved interface:** external app availability, intent grants/redirects, URL handling, user confirmation UX, referral/context leakage, browser identity and return/recreation behavior.
- **Cheapest falsification:** instrument a resolver test with `https:`, `javascript:`, `file:`, credential-bearing and redirect URLs plus cancellation; confirm only user-approved canonical HTTP(S) opens externally.
- **Gap type:** delegation is a useful product alternative, but it is intentionally not equivalent to the Major browser requirement.
- **A(C) attacker:** malicious handler captures the URL, user is surprised into an account-signed-in browser, intent redirects to a different scheme, or an external app shares identity. Keep handoff explicit; never say “private browser.”

### E6. Own bounded, lossy file projections (CSV / values-only XLSX)

- **Construction/lemma:** accept a narrow, budgeted format grammar; parse in a worker; show every retained value and loss; create a new literal document rather than editing the source. This avoids claiming full Office compatibility.
- **Concrete artifact:** existing `format/ooxml` CSV and R22 values-only XLSX implementation, strict ZIP/XML admission, worker integration, UI import/export, and prior exact-candidate native/host receipts. No new Office code in R23.
- **Proved portion:** the repository records R22 focused10/10 and full119/119 on a named candidate and host reference comparisons. Those receipts predate R23 and do not constitute an Excel/LibreOffice rendering test or a current R23 Android build.
- **Exact unresolved interface:** styles/layout/formulas/dates/macros/ODF and full read/write fidelity; arbitrary package compatibility, OS/version matrix, recovery, licensing and user data-loss review.
- **Cheapest falsification:** generate hostile shared/sparse/large package cases with independent producers, open outputs in real LibreOffice/Excel, and verify loss report + source preservation.
- **Gap type:** a narrow local feature; full Office remains equivalent to the original engine/fidelity scope.
- **A(C) attacker:** a workbook uses a valid-looking ZIP/XML but semantic content lies outside the admitted subset; sparse cells or numeric formats mislead users; provider output aliases source; a user assumes “values-only” strips identifiers. Refuse unsupported features and retain the source.

### E7. Optional isolated LibreOfficeKit-based Office engine

- **Construction/lemma:** bundle a pinned LOK/native library only as a separately licensed and isolated optional engine with bounded parent/child IPC and explicit format/fidelity warnings.
- **Concrete artifact:** release-gate checklist: inventory every binary/source/transitive license; prove reproducible arm64 and requested page-size builds; install on min API; measure RSS/CPU/storage/startup; exercise document open/edit/save/readback and hostile corpus; obtain legal review before packaging.
- **Proved portion:** repository contains an unaccepted `:format:lok` bridge/placeholder and documented license risk. No genuine LOK engine/package, build, runtime, or legal review was verified in this turn.
- **Exact unresolved interface:** transitive licensing/relinking/source obligations, native ABI/16KiB, memory/CPU budgets, crash recovery, UI integration, format fidelity and independent legal sign-off.
- **Cheapest falsification:** first run a complete binary/source dependency-license inventory and a minimal arm64/16KiB build spike; either failure rejects the route before feature UI work.
- **Gap type:** still equivalent to full Office scope, though a verified engine may reduce implementation effort.
- **A(C) attacker:** license file is misread, a transitive GPL component leaks into the main APK, native parser escapes its UID, or “opens” but silently corrupts a high-value format. Keep it out of the main APK until every gate passes.

### E8. External Office application handoff

- **Construction/lemma:** let the user explicitly open a copied/selected document through an Android document intent; keep source URI authority and external app processing visible. This avoids claiming Fusion has Office.
- **Concrete artifact:** integration design with `ACTION_OPEN_DOCUMENT`/`ACTION_VIEW`, content grants limited to the selected URI, pre-handoff copy when user asks, and explicit “external editor owns rendering/save” labels. Existing SAF and system-picker code provides adjacent primitives only.
- **Proved portion:** repository has real document picker workflows and verified source-preservation paths for its bounded worksheet; this does not prove an Office handoff.
- **Exact unresolved interface:** provider and receiving-app URI permission behavior, edits/save-back, cancellation, app absence, cloud provider traffic, document identity leakage and loss recovery.
- **Cheapest falsification:** instrument picker/chooser with a disposable file, cancellation, provider revocation and a receiver that attempts source overwrite; verify actual URI modes and readback.
- **Gap type:** a product compatibility route, not first-party Office engine completion.
- **A(C) attacker:** external editor syncs to cloud or mutates source; provider aliases output; chooser handler is malicious. Use copy-by-default, no persistent grant, and explicit destination confirmation.

### E9. OS-isolated parser worker with owned read-only descriptor

- **Construction/lemma:** parent captures bounded bytes, closes writer, unlinks its private name, opens a fresh read-only FD, and passes only that capability to one Android isolated service; parent validates bounded reply framing and owns timeouts.
- **Concrete artifact:** existing `format/pdf` worker/lease and R22 XLSX/CSV operations plus device boundary tests. This is not a new process architecture delivered by R23.
- **Proved portion:** existing recorded API34 operations test real Binder/UID/FD/socket boundaries for named cases. They do not prove all parsers, semantic truth, every provider, CPU/RSS caps, or physical-device containment.
- **Exact unresolved interface:** worker input stability, hard process CPU/memory limits on ART/OEM kernels, parent timeout/kill correctness, alias/revocation, output authenticity, parser completeness and crash cleanup.
- **Cheapest falsification:** attack writable alias/seek offset, late replies, unbind/rebind, dead parent/child, oversized output, socket attempt, blocked FD, and concurrent source mutation on an actual managed device.
- **Gap type:** local authority reduction; full parser sandbox remains equivalent to the original safety/coverage gate.
- **A(C) attacker:** compromised child returns a digest-matching lie; parent accepts stale reply; file descriptor is reopened writable; worker is starved or blocks; package reaches a parser path not using the worker. Treat output as untrusted data and enumerate all call sites.

### E10. WASM/parser plug-in sandbox

- **Construction/lemma:** run format adapters in a bounded WASM runtime with explicit host calls and no implicit file/network syscalls; broker all bytes through a capability API.
- **Concrete artifact:** design-only acceptance table: pinned runtime/toolchain; deny-by-default imports; memory/fuel cap; one request per instance; signed module supply chain; audited licensing; architecture/API and 16KiB tests.
- **Proved portion:** no runtime or plug-in was installed or executed in this repository/session. This is a candidate, not an established security boundary.
- **Exact unresolved interface:** Android runtime/ABI/legal bundle, CPU/fuel behavior under hostile imports, host-call correctness, memory ceiling, exploit response, UI/process lifecycle and whether the runtime adds more attack surface than Android's isolated UID.
- **Cheapest falsification:** compile one parser harness and an adversarial infinite-loop/memory-growth/host-call corpus under the exact Android runtime; measure kill latency and residual host authority.
- **Gap type:** local sandbox mechanism experiment, but parser coverage and original user-facing features remain the same acceptance problem.
- **A(C) attacker:** interpreter/JIT bug, host-call confused deputy, fuel accounting bypass, malicious module update, unsupported import with broad authority. Do not call WASM “safe” without runtime-specific tests and dependency/legal audit.

### E11. Vertical-slice local-first productivity

- **Construction/lemma:** deliver one workflow at a time with a named data owner, revision semantics, lifecycle, save/readback and export contract; do not block useful local work on full browser/Office parity.
- **Concrete artifact:** existing Tasks, Notes, CSV worksheet and bounded XLSX flows, with exact-candidate R17–R22 reports. No productivity code was added in R23.
- **Proved portion:** narrow local workflows have prior scoped tests on their recorded candidates. This does not imply reminders, recurring calendars, collaboration, synchronization, Meet, complete desktop Office, or acceptance of the current full product.
- **Exact unresolved interface:** shared identity/transactions, timezones and notifications, background work, backup/restore, synchronization, accessibility, window integration and broad file-format fidelity.
- **Cheapest falsification:** run a current-source API34 process-death matrix for Task create/edit/archive/restore/search; kill between every commit/ack and verify durable revision and readback.
- **Gap type:** bounded local workflows are closable; the full suite remains an aggregate integration and compatibility problem.
- **A(C) attacker:** stale revision, duplicate import, process death, locale/timezone shift, notification denial or false “saved” toast. Require durable readback and recoverable UX.

### E12. Android-managed Work Profile or secondary user

- **Construction/lemma:** use an OS-created managed Work Profile or secondary Android user to separate an app instance and its data under Android's user/profile model; a DPC may provision/manage a work profile. The platform—not Fusion—owns the cross-user boundary and profile removal behavior.
- **Concrete artifact:** deployment test design: check feature/support, explicitly provision a disposable profile/user, install the same test app into personal and managed contexts, attack cross-profile URI/intent/clipboard/notification paths, then disable/remove the profile and inspect documented data lifecycle. No DPC, profile or secondary user was created in R23.
- **Proved portion:** Android Enterprise and multiple-user documentation describe DPC-managed work profiles and separate users/data; no profile was provisioned or tested here.
- **Exact unresolved interface:** OEM/policy support, cross-profile sharing and admin configuration, account/notification linkability, managed provider/network behavior, retention/deletion semantics and recovery.
- **Cheapest falsification:** install the app in personal and work contexts, share a disposable file both ways, inspect actual URI grants and clipboard/notification behavior, then disable/remove the work profile and verify which test data remains.
- **Gap type:** managed compartmentalization option, not Tails, global anonymity, physical-keylogger defense or completion of the productivity suite.
- **A(C) attacker:** an allowed cross-profile share, linked account/notification, administrator policy or unexpected profile deletion crosses the intended boundary. Label context and test every handoff; do not promise unlinkability.

## F. Concrete artifacts returned

- New runtime source: read-only Android posture probe, bounded output, explicit unknowns and permanent “not a scan”/hardware-boundary statements.
- New Android instrumentation source: untrusted control/bidi text sanitization, one all-reassuring-state counterexample, one unknown-state check, and one actual Android API/UI surface test.
- Updated Android privacy UI: refresh + user-directed keyboard, accessibility, developer, and security Settings buttons; no settings write or extra permission.
- New executable host-only source check: validates privacy manifest/source/test linkage and reports that it is structural rather than Kotlin/Android execution.
- New research and PRD docs with current official Tails/Android/MITRE links, plus the mechanism portfolio above.

These artifacts are **source-level deliverables**. Since no JDK/Gradle/Android SDK/ADB is installed in this sandbox, no changed Kotlin code or instrumentation test has been compiled/executed here. No APK is produced.

## G. Second-order adversary summary

For each candidate `C`, an attacker adapts to its particular boundary:

| Candidate | Strongest structure-specific attack `A(C)` | Test that would expose it |
|---|---|---|
| E1 offline companion | Evil IME captures scratch input; physical camera observes; app is paused then a resumed Activity restores stale text through framework state | Existing no-restore/lifecycle + socket tests; R23 all-green/unknown tests; real IME/Settings pause-return test |
| E2 device owner | Policy never enrolled/removed, applies only after boot, or already-open USB connection survives lock | Managed-device state transition matrix including boot, existing connection and owner removal |
| E3 Tails laptop | Modified USB/firmware/host, identity-bearing files/accounts, adversary sees both traffic endpoints | Verified-media/boot and packet-capture experiment on known hardware; explicit identity-separation fixtures |
| E4 GeckoView | Origin confusion, subframe permission, renderer crash/navigation race, profile survives “private” close | Real origin server + crash/kill/navigation tests and persistent-storage inspection |
| E5 external browser | Resolver interception, external account cookies, redirect/scheme confusion, user surprises | Exact chooser intent assertions, malicious handler and redirect test, visible external handoff confirmation |
| E6 projection | Format semantics not represented, hidden values/identifiers survive, resource-heavy ZIP/XML bypass, source mutation | Independent producer corpus, adversarial ZIP/XML budgets, original-source digest/readback and human loss review |
| E7 LOK | Transitive license failure, native escape, corrupting save, memory exhaustion | SBOM/license/legal gate first; then isolated crash/fidelity/resource corpus |
| E8 Office delegation | Receiver app/cloud provider reads or overwrites original, grant persists, user cannot recover | Provider/receiver instrumentation, source-change detection, revoked-grant and copy-only workflow |
| E9 isolated process | Child forges valid-looking result, stale Binder response wins, parent times out incorrectly, path outside worker bypasses isolation | Untrusted reply corpus, request IDs, mutation/binder death/socket/timeout tests on current OS |
| E10 WASM | Host-call privilege leak or resource meter/runtime exploit, module update changes behavior | Runtime-specific adversarial WASM corpus with denied-import, memory/fuel, host-call and signed-update checks |
| E11 productivity | Stale revisions, process death between commit and acknowledgement, duplicate import, timezone/locale shift | API34 process-death/revision/readback matrix for each local workflow |
| E12 Work Profile / secondary user | Cross-profile intent/share, linked account, admin policy or unexpected profile deletion | Personal/work profile cross-share and removal lifecycle test with disposable data |

No row gains trust because another mechanism exists. A repair must rerun its gates and add a patch-specific attack; for this turn the all-reassuring snapshot test is the new attack specifically aimed at the dashboard's most dangerous failure mode.

## H. Verification and runtime honesty

### Actually available and used

- Filesystem/shell and local Git in `/home/user/OSB`.
- Python 3.11.2: `tools/r23_privacy_check.py`, its focused host test, the full `python3 -m unittest discover -s tools -p 'test_*.py' -v` suite (**122 PASS**), and repository source/traceability checks passed. These do not compile/run Android.
- Node.js 22.22.3/npm 10.9.8: `npm ci`, `npm run build`, and `npx tsc --noEmit` passed for the root Vite landing page only. `npm audit` reported **5 advisories (4 high, 1 low)** in the existing web dependency tree; no upgrades were made in this Android patch.
- Web search and page fetch for the primary links in `research/r23-primary.md`, including the AOSP USB data-signaling guide.

### Not available or not used

- No local `java`, `gradle`, `kotlinc`, `adb`, `sdkmanager`, Android SDK, Android Studio Rabbit1 GUI, emulator, physical device, receiver, or test phone was available.
- No theorem prover, database, independent agent/reviewer, expert counsel/AX reviewer, or local Android experiment was used.
- GitHub privacy run [38859445946](https://github.com/jyvplay/OSB/actions/runs/38859445946) at `a0b9de7e41ed33773a127ced95d299e712d85e71` **failed before tests**: `:privacy:compileDebugKotlin` could not resolve `InputDevice.hasSource`; no APK/device cases. The source was repaired to use the public `sources` mask.
- The next run [38860941610](https://github.com/jyvplay/OSB/actions/runs/38860941610), commit `b9451f1c79f7b054b226aba28c375a628f232f22`, passed Kotlin compile, assemble and lint, built a 2,506,950-byte APK (SHA-256 `4de396dbec81cf4d41e15f93c5ff58ccc4762bcff5132d96d01d2a85e956a10c`), and executed **10/10** API34 cases (**8 PASS, 2 FAIL**). All four new R23 posture tests, the screenshot control and the socket-denial control passed. Two workspace tests failed: the input was not located by UIAutomator before scrolling, and `ActivityScenario` did not leave RESUMED when asked to move to CREATED. The current worktree moves the main tasks before posture guidance, adds explicit scrolling and tests discard via an actual keyboard-Settings handoff; another run is pending. R22 receipts concern an older candidate and do not verify R23.
- The third run [38861923458](https://github.com/jyvplay/OSB/actions/runs/38861923458) built the production APK (2,506,946 bytes; SHA-256 `c4dba5ae9affb4bd52b844e3d296a774c54134c0895e1b8b51db9c8a605280db`) but failed `:privacy:compileDebugAndroidTestKotlin` because `UiObject.exists` was unresolved. **No device cases ran in that attempt.** That assertion is removed in the worktree; a rerun against the corrected Android-test source is pending.
- The next run [38860941610](https://github.com/jyvplay/OSB/actions/runs/38860941610), commit `b9451f1c79f7b054b226aba28c375a628f232f22`, passed Kotlin compile, assemble and lint, built a 2,506,950-byte APK (SHA-256 `4de396dbec81cf4d41e15f93c5ff58ccc4762bcff5132d96d01d2a85e956a10c`), and executed **10/10** API34 cases (**8 PASS, 2 FAIL**). All four new R23 posture tests, the screenshot control and the socket-denial control passed. Two workspace tests failed: the input was not located by UIAutomator before scrolling, and `ActivityScenario` did not leave RESUMED when asked to move to CREATED. The current worktree moves the main tasks before posture guidance, adds explicit scrolling and tests discard via an actual keyboard-Settings handoff; another run is pending. R22 receipts concern an older candidate and do not verify R23.

Local scripts validate XML/manifest structure, ledger hashes, Python tooling, and authored source contracts; they do not type-check Kotlin or verify hardware. Managed API34 runs give partial real Android evidence, not full acceptance: R23 remains **runtime-partially-verified**, with two workspace cases and the corrected Android-test compilation awaiting confirmation; USB behavior still requires physical-device review.

## I. Repair and re-verification gate

The R23 patch has no inherited trust from R22. Before any R23 delivery claim, run:

1. `python3 tools/check_project.py` and all `python3 -m unittest discover -s tools -p 'test_*.py' -v` on the exact source tree.
2. `:privacy:assembleDebug`, `:privacy:lintDebug`, and `:privacy:pixel6Api34DebugAndroidTest` on a disposable API34+ managed device.
3. Re-run the actual IPv4/IPv6 denied-socket/Unix-pair positive control, FLAG_SECURE screenshot positive/negative control, pause/recreation/discard tests, and all four new R23 posture tests.
4. Attack the patch: set unknown API responses, substitute a third-party IME, enable an accessibility service needed for assistive use, open Settings while dummy text exists, return, and verify scratch data is gone while the panel refreshes; verify all Settings buttons are user-delegated and there is no false “protected” result.
5. Then test supported USB Protection on actual supported hardware across locked/unlocked, pre-existing session, boot and reconnect. Do not infer this from an emulator or source code.

A failure requires repair, hash/regeneration, all affected checks rerun, and at least one new attack targeting the repair. A clean Python structural check is not a substitute for items 2–5.

## J. Strongest current artifact, exact remaining gap, next test

**Strongest new artifact:** an app-local posture panel backed by read-only public Android API calls, with unknown-preserving fields and a test candidate that forces a non-claim even under an all-reassuring snapshot. Existing privacy protections stay separate. This is not yet a compiled APK or executed feature.

**Exact remaining gap:** Kotlin/API compilation, runtime Settings/IME/accessibility behavior on current Android versions, actual USB Protection state/device/OEM behavior, independent security review, and all OS-wide Tails/browser/Office/sandbox/productivity requirements. The new panel cannot verify hardware/firmware trust or global anonymity by design.

**Next highest-information test:** first run the exact current source through the existing Android privacy CI job (`:privacy:assembleDebug :privacy:lintDebug :privacy:pixel6Api34DebugAndroidTest`) on API34. It will cheaply falsify imports, API availability, layout IDs, socket/window/lifecycle regressions, and the new modal-shortcut tests. The next test after that is a supported physical Pixel 6+ USB Protection matrix, not another source-only review.
