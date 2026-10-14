# R6 — final pre-Studio engineering pass

Native Android sources only; no TypeScript changes, mock UI, generated screenshots
or APK claims. This pass is **not** full-product completion or pilot clearance.

## A. Actual tools and admissible scope

Executed: local Git/file inspection, Python, Kotlin2.2.21 on cached JRE17.0.9+9,
separate production-JAR compilation, actual JVM/file contracts, GitHub API through
`gh`, and supplementary Kotlin tree-sitter parsing. No independent agent, theorem
prover, Android SDK/Gradle build, emulator, browser inspection, physical device,
legal review or CI execution occurred. No push/commit/workflow dispatch.

Objects: the current native source tree, production immutable session types,
compiled module APIs, explicit disposable-AVD identifiers, build output metadata,
and actual future instrumentation receipts/captures. Host helpers validate inputs;
they do not model a fake Android device. Full JDK/SDK/network/AVD prerequisites
are absent here. Source tests are not Android typechecking.

## B. Success model, bounds and alternatives

For the **recorded finite corpus**, all169,576 production JVM assertions pass with
library/consumer boundaries preserved. Two bad consumers must fail for smart-cast
instability; the stable-local consumer must compile. These are compiler/logic
facts, not a universal app-correctness theorem.

The laptop workflow's intended predicate: only the explicitly named, consented,
recognized disposable emulator may be mutated; current-source APKs must build;
exact expected test counts and success markers must be observed; each of five
native views at each of two font settings must have a fresh real capture and
matching Activity configuration. Tests use10-second UI waits,600-second ADB
command bounds and a3600-second build bound. Timeouts fail, not prove absence of
bugs. PNG checks establish framing/dimensions only, not pixel correctness or
accessibility. State restoration cannot be guaranteed after kill/disconnect.

H+: integrated build/visual/full-product success is possible in principle but
**not established**; its threshold is actual execution plus reviewed feature,
platform, accessibility, license/legal and physical-device gates.
H−: this sandbox cannot currently establish an Android build/UI result with the
available toolchain/access. This is a resource boundary, not proof Android or the
product is impossible. Boundary outcome: reproducible native sources, real JVM
compiler/test evidence, and **unrun** laptop-native build/capture infrastructure.
Adjacent problems not substituted: HTML previews, generated pictures, syntax
parsing, code-presence percentages, assertion volume and emulator screenshots do
not replace Android compilation, UI usability, provider safety or device spikes.

## C. Imported primary-source result

Retrieved the Kotlin specification at commit
`0f762a2314a9304e4b3fc386b1aceef1d56c7e4c`, type-inference.md lines246–261.
It names separate module compilation as a cause of smart-cast sink instability
and immutable locals as stable sinks. Full provenance/hash/excerpt:
[research/r6-module-frontier.json](research/r6-module-frontier.json).
The real Kotlin2.2.21 compiler corroborated this narrow rule against the project's
actual documents JAR. Neither the spec nor that compiler experiment imports an
Android/Compose correctness claim. No external reviewer or Studio-version
compatibility result is claimed.

## D. Actual counterexample, repair and patch-specific attack

The former editor UI checked `session.pendingCopy != null` and then read
`session.pendingCopy.name/stage` across a Gradle module boundary. A consumer
against the separately compiled production library failed with the actual
"smart cast … public API property declared in different module" diagnostic.
[verification/r6-boundary-counterexample.txt](verification/r6-boundary-counterexample.txt)
preserves it. The first evidence-saving script had a case-sensitive diagnostic
assertion error; it was repaired and the compiler failure was rerun/saved.

Repair: snapshot `val pendingCopy = session.pendingCopy` in the native screen;
also snapshot nullable request identity in the copy event-corpus consumer.
The old fused compiler runner concealed this class of problem. It now compiles
10 pure Gradle-module libraries and the calculator kernels into11 independent
JARs, then compiles/executes consumers against those APIs. Android adapters and
the two selected Android-module policy files are not full Android builds.

Patch-specific attack: stabilizing the containing object alone does **not**
stabilize its public nullable `requestId`. The second negative fixture checks
that nested-property trap. Both negative fixtures are rejected; the local-value
positive fixture compiles; all prior contract assertions pass. A Python source
regression guard checks the native editor continues to use the local value.
See [verification/r6-boundary-regression.txt](verification/r6-boundary-regression.txt)
and [verification/r6-jvm-contracts.txt](verification/r6-jvm-contracts.txt).

## E. Distinct mechanisms and their exact gaps

| Mechanism / artifact | Established portion | Exact gap / falsifier |
|---|---|---|
| Separate libraries — `run_jvm_contracts.py` | Actual compiler boundaries plus full recorded JVM corpus pass | Gradle dependency/plugin/Android compilation; either flavor failing falsifies integration |
| Stable nullable snapshots — editor and copy corpus | Positive compiler case, two negative attacks, source guard | Native Compose screen remains uncompiled; new repeated public nullable dereference falsifies repair |
| Native window controls — `MainActivity.kt` | Source now uses ≥48dp title/action minima; three controls plus tiling menu instead of five cramped buttons; scrollable expanding header | No measured UI hitboxes/reflow;240dp/200% unreachable action falsifies UX claim |
| Destructive-test admission — `DisposableEmulatorRule.kt` and host serial checks | Host rejection tests executed; native independent guard authored | Native guard unrun; direct runner wiping without consent falsifies safety |
| APK identity/path admission — `native_lab.py` | Real temporary-file metadata/path/split parser tests pass | Actual AGP outputs/install unrun; different package or split bypass falsifies selection |
| Instrumentation receipt parsing | Missing/zero/wrong count, crash, bad result and duplicate summaries rejected by executed parser tests | Real runner format/unexpected status unrun; failing suite accepted as success falsifies parser sufficiency |
| Fresh native capture and font observation — `VisualCaptureTest.kt` | Authored real Bitmap/UiAutomation path, unique run dirs, actual configuration requirement | No PNG captured here; stale capture or mismatched200% configuration falsifies evidence |
| Font restoration and durable pre-mutation record | Source records original value first and attempts/readbacks restoration in finally | ADB/SIGKILL/loss prevents guarantee; native failure injection remains necessary |
| Acceptance accounting |48 audited modules; structural and promotion-refusal tests pass | No complete module; any completion claim without reviewed mandatory gates is invalid |

These are complementary mechanisms, not six equivalent parameter changes. Only
the portions explicitly marked executed have runtime evidence.

## F. False solutions and adversaries

E = executed host/compiler/kernel detection; S = source inspection; U = required
Android/device test, **unrun**. None of the U rows is a claimed success.

| False solution / specific adversary | Detecting test or acceptance gate |
|---|---|
|1. Fuse all sources and claim Gradle modules compile | E: separate libraries + original negative consumer |
|2. Stabilize object but repeatedly read its nullable property | E: nested requestId negative attack |
|3. Replace nullable accesses with `!!` everywhere | S: stable locals; U: null/recovered pending native screens |
|4. Parser accepts any zero-exit `am instrument` | E: missing summary/result rejected |
|5. Zero/wrong test count represents success | E: exact-count rejection |
|6. Failure text plus success-looking trailer passes | E: failure/crash markers and duplicate summary rejection |
|7. Automatically choose the first connected phone | E: exact emulator serial and explicit consent required |
|8. Physical/IP serial disguised as normal input | E: physical/network/semicolon/newline serial refusals |
|9. Bypass host guard by running the reset test directly | S: independent native consent/hardware guard; U: direct-run refusal |
|10. Install wrong flavor or silently choose an APK split | E: package and multi-output metadata refusal |
|11. APK metadata path escapes build directory | E: traversal/absolute/backslash/missing output attacks |
|12. Empty/truncated bytes count as a screenshot | E: PNG framing rejection; U: real Bitmap capture/readback |
|13. Reuse a previous screenshot after a failed run | S: fresh run IDs and must-succeed capture; U: partial-run/stale-artifact attack |
|14. Set200% but capture old Activity configuration | S: actual configuration assertion; U: font-change/recreation test |
|15. Pack five32dp buttons into a compact title | E: source regression rejects old dimensions; U: measured240dp/200% hitboxes |
|16. Hide tile actions to make layout fit | S: three menu actions retained; U: keyboard/TalkBack/menu interaction |
|17. Restore font only after a successful test | S: finally + prior durable record; U: disconnect/crash/interrupt test |
|18. Claim parser tests establish real ADB/Gradle execution | E: doctor prerequisite refusal; explicit evidence classifications |
|19. Count many assertions as complete modules | E: module audit refuses unreviewed completion |
|20. Use emulator screenshots to pass BlackTop, casting, codecs or legal gates | E: release audit refuses; U: actual mandatory spike matrix |
|21. Claim screenshots alone prove accessible UI | U: keyboard, TalkBack, Switch/Voice Access and200% interaction review |

A malicious device/ADB server could spoof emulator properties and reports; this
lab assumes a trusted local SDK/AVD and is not a remote attestation system.
Similarly, source hashes bind bookkeeping, not authenticated package provenance.
Untrusted document providers, callback races and actual copy durability retain
the R5 attack/gap record; this pass does not remove those gates.

## G. Actual evidence versus authored infrastructure

-169,576 JVM assertions passed after isolated compilation; three existing
 CastV2 unchecked-cast warnings remain. Two rejected compiler attacks and one
 accepted stable-local consumer are additional compile checks, not more assertions.
-21 Python tests passed, including six new host/source tests; manifest/project,
 module counts and generated traceability checks passed. Expected release refusal.
-Doctor actually refused: no full JDK/SDK/ADB prerequisites. An explicit-consent
 CLI refusal also occurred before any device access. Both are evidence of refusal,
 not a successful build lab.
-Supplementary syntax scan initially flagged the capture helper's `open` name;
 it was renamed `openModule`. The existing CastV2 parser warning is a grammar
 limitation: that exact source passed the real Kotlin compiler. No Android
 compilation is inferred from parsing. Final scan evidence records this exception.
-Native screenshot, smoke and reset guards, Gradle/lint, real font restoration,
 all new UI geometry checks and actual human visual review remain **unrun**.

Current-source hashes/output are in `verification/r6-source-checks.txt`.

## H. Accounting

**48 included modules;0 fully specification-complete/accepted (0%);48 requiring
completion or mandatory acceptance (100%).** Six modules have selected executed
kernel logic (12.5% of module names, NOT12.5% product completion), seven have only
pure-source compilation,33 Android modules are uncompiled, one test harness ran,
and one profile module lacks generated acceptance.231 requirement rows,30 blocked
spikes, no whole verified feature. Requested47-name architecture still has41 exact
names, two partial alternative owners and four absent dedicated modules:
quickpanel, office, photos and ime. Meet/GPL APK ownership remains unresolved.
See the generated [module audit](MODULE-COMPLETION.md) for every module's gap.

## I. Environment and visual answer

**Yes on a suitably provisioned laptop/authorized worker; not established in this
sandbox.** The real native workflow is now authored in
[NATIVE-LAB.md](NATIVE-LAB.md). It neither runs a web substitute nor manufactures
screenshots. No Android environment was secretly provisioned or tested. Real
captures can subsequently be inspected, but human review is a separate recorded
step and cannot be claimed in advance.

## J. Strongest artifact, exact residual gap, next test

Strongest verified artifact: current production JVM libraries compiled separately,
actual prior corpus passed, and a genuine integration-pattern compiler defect
was found, repaired and attacked again. Native window improvements and a cautious
real-device-tool workflow are authored, not executed acceptance.

Exact residual: no integrated Android build or UI inspection; compile37, full
feature/process/compositor/external-display implementation, Room/commerce,
SAF/device/AX/IME, isolation/security, performance16K, license/SBOM and30 spike
gates remain. No gated capability was enabled to inflate progress.

Next falsifying test: on the laptop run `native_lab.py doctor`, then `build`.
Repair real Gradle errors before installing. On a fresh disposable AVD run the
consented visual command, inspect actual screenshots/interactions at both font
settings, and record failures rather than treating captures as a release pass.
