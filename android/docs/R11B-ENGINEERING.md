> Historical R11B checkpoint. Latest: [R12 native work and exact verification](R12-ENGINEERING.md).

# R11 continuation — reversible Notes, native focus, real browser profiles, and sandbox experiments

**This is not the completed original specification or a production beta.** No
TypeScript UI, fake engine, network mock, synthetic screenshot, or simulated
Android runtime was substituted. New executable production work is concentrated
in Notes and compositor focus. Browser profile code is an explicitly debug-only
platform experiment; Office remains an unimplemented engine.

## Runtime honesty and numerical status

Actual tools used: repository file tools, Bash, Git/`gh`, Python3 standard library,
Kotlin2.2.21 from npm and Temurin17.0.9 runtime from PyPI jdk4py, GitHub Actions with
real JDK/Gradle/Android SDK and an API34 `aosp-atd` managed emulator. Host tool caches
were absent on resumption and were reinstalled; no local Android SDK/ADB/full JDK
was present. The two tool installations ran concurrently. The CPU experiment ran
real Linux child processes, including a real fork. These are not independent agents.
No theorem prover, physical device, counsel, expert reviewer, human AX review,
private database or independent research team participated.

The workspace initially had old Git metadata at7449dfd. All388 tracked files
matched remote428018c byte-for-byte; a mixed reset reconciled metadata only and
all inspected working bytes were checked unchanged. See `r11b-reconciliation.json`.
Both prior workflows at428018c completed successfully; prior evidence is historical.

- **0/48 fully accepted modules (0%);48/48 outstanding (100%).**
- Requested Productivity0/13; Forge0/13; nested browser0/19 accepted, all0%.
-231 requirement rows/36 foundations/30 unpassed spikes; no requirement or S-gate promoted.
- **182,871 Kotlin assertions**,13 separately compiled libraries, including42 new
  real-file trash assertions. **61 Python methods** and **323 additional
  cross-language checks** passed. No assertion count is a feature percentage.
- Linux CPU experiment: three actual executions, including a counterexample to
  whole-job enforcement by per-process limits. Not Android CPU/RSS acceptance.
- **Native candidate1ea1faa:29/29 API34 cases PASS**; both debug flavors and
  invoked unit/contracts/lint passed. This is not a full release/AX/security pass.
- Native evidence is recorded below by exact candidate; repaired candidates do
  not inherit earlier passes.

## A. Formal model, access, quantifiers, resources and adjacent non-solutions

### Notes contract

Objects: live UTF-8 notes and saved-revision trash entries in one trusted
app-private filesystem. Live names have1–64 letters/digits/hyphen/underscore and
≤128 UTF-8 bytes. A revision binds name and SHA-256 of the exact saved bytes.
A trash identity additionally has a UUIDv4-derived32-hex token. Existing-path
collisions refuse. Historical non-reuse is probabilistic, not a durable monotonic
sequence: under independent uniform122-bit randomness, collision probability for
n allocations is bounded by n(n−1)/2^123 (about9.4e−20 at n=10^9). RNG quality and
rollback are not proved by these tests. Metadata and content are
coupled by a single filename inside `.trash-v1`, not separately published sidecars.
The longest admitted filename is230 UTF-8 bytes, below the tested filesystem's
255-byte component limit.256KiB/note,≤256 live notes,≤256 trash entries: at most
128MiB combined retained note content. Catalog validation reads entries lazily;
listing a full trash can read64MiB, so no instant-open/latency claim is made.

Access assumptions: all application writers take the same advisory OS lock;
private directory ancestors and the app UID are trusted. Symlinks/non-regular
entries and malformed identities are refused. This is not protection against an
arbitrary same-UID actor replacing directories while ignoring the lock, nor are
SHA-256 filenames authentication against that actor.

Success predicate: for every **admitted cooperating operation trace**, if delete
returns confirmed success, the selected saved bytes are recoverable under its
trash identity and the live name is absent; if restore returns confirmed success,
the restored bytes/revision match and the corresponding trash object is absent.
For every conflicting live destination, missing/mismatched receipt, over-capacity state
or detected malformed input, refuse without intentionally overwriting/removing
another version. Operations serialize under the lock. Atomic move is required;
there is no copy/unlink or truncating fallback. Following an exception after a
possible rename, report uncertain publication and require inventory reconciliation;
do not delete either possible recovery location. Directory force/readback is not
an empirical physical-power-loss proof.

Tests establish these properties for their finite traces, not universal correctness.
Host Unicode-path tests require a UTF-8 filesystem locale. Actual Linux Java17
under `LC_ALL=C` failed with InvalidPathException before writing the Unicode note;
`LC_ALL=C.UTF-8` passed. Android Unicode filename behavior needs actual device
execution, not inference from the host.

### Native/browser/sandbox contracts

- Focus: activating an inactive window through a child's actual focus action must
  retain that child's keyboard focus. A control build uses prior MainActivity with
  the new test; candidate source is restored before full-suite execution. This is
  an actual Android A/B experiment, not a full rebuild of every prior source file.
- Profiles: two distinct same-UID host processes set distinct data-directory
  suffixes before WebView use. Both load the **same** HTTPS base origin using fixed
  offline HTML with network/file/content access disabled. Require actual cookie and
  JavaScript/localStorage values across write/clear/read operations and distinct
  PIDs. Installed-provider identity must be nonempty and agree. This does not prove
  different UID, pixel rendering, browser UI, every storage API, provider upgrades,
  restart persistence, z-order/IME/AX or full browser security.
- Socket: an app-owned real loopback listener must accept parent connections before
  and after the worker attempt, with matching peer ports. The worker attempts a
  native IPv4 socket/connect. Only EACCES/EPERM or explicit SecurityException is
  accepted as denial; timeouts, refusal/unreachability and unknown Java exceptions
  fail. No DNS or external address is used. It proves this attempted channel, not
  absence of all IPC/covert channels or arbitrary compromised-native behavior.
- Linux CPU: unprivileged LP64 Linux process, native libc setrlimit,1s soft/2s hard
  RLIMIT_CPU. Attempt to raise hard limit must return EPERM; SIGXCPU is deliberately
  ignored. SIGKILL with observed1.8–3.0 CPU seconds is the limited positive result.
  The unconstrained2.5-CPU-second control must exit normally. A fork attack runs
 1.25 CPU seconds in each descendant; normal completion beyond2.5 aggregate CPU
  seconds falsifies whole-job enforcement by the per-process mechanism.15s wall
  timeout is only cleanup; a cleanup kill never earns a pass. No wall deadline or
  Android native limit is established by these host observations.

Adjacent problems explicitly not substituted: preserving a saved note is not
saving an unsaved draft; trash is not backup/version history or the required journaled Files move engine; same-UID profile
partition is not hostile-process isolation; a hidden WebView executing DOM is not
a desktop browser; absence of INTERNET permission is not a socket experiment;
per-process CPU is not process-tree CPU, RSS or wall time; a source license is not
payload license closure; a desktop/headless Office interface is not an Android
renderer/editor; external Office/cloud conversion is not FF01; emulator execution
is not physical-device acceptance; an SDK catalog entry is not an AGP/API build.

## B. H+, H−, H∂ and evidence thresholds

- **H+**: these bounded mechanisms can advance the original architecture. Declare
  each narrow property supported only after its actual production/runtime test.
  The complete product's achievability remains conditional, not proved here.
- **H−**: under this session's access model, the full acceptance conjunction cannot
  be discharged: required physical-device/legal/expert observations are unavailable,
  and many required engines/features are absent. This is not an impossibility
  theorem about building the product with additional resources. Specific stronger
  claims were falsified: same-root focus preservation in the old compositor and
  aggregate job CPU containment by RLIMIT_CPU alone.
- **H∂**: results change with filesystem locale, real provider availability,
  pre-initialization suffix order, supported API/toolchain, cooperating writers,
  process-vs-tree accounting and privilege/fork capabilities. No silent weakening
  of the domain or promotion of a partial result to its adjacent problem.

Thresholds: host mechanism → real production-code assertions plus attacks;
native property → named executed Android case, not just an APK; regression cause
→ controlled prior-source failure and candidate pass; browser engine choice →
provider/profile/UI/security/license matrix; Office → actual payload/ABI, storage,
license and fidelity corpus; product → every required gate. No final module passes.

## C. Verified frontier and exact open interfaces

Pinned repositories/commits/Git blob identities/SHA-256/excerpts are in
`research/r11b-primary-frontier.json`; downloaded bytes were checked against Git
blob hashes. These are the strongest primary sources verified in this bounded
pass, not a claim of exhaustive best-known literature coverage.

- AOSP Android14 WebView documents data-directory suffixes and ordering before
  initialization; CookieManager has actual asynchronous clear/write operations;
  WebSettings exposes DOM storage and blocked network loads. Hypothesis: installed
  provider implements these contracts. The debug experiment tests one actual API34
  environment. It does not resolve cross-UID remote-surface input/AX or the full
  provider matrix.
- LibreOffice `userinstall.cxx` explicitly calls directory creation and contains
  an Android/iOS branch creating a profile `/user` directory; initialization source
  exposes profile URLs with platform-specific paths. Hypothesis: a chosen Android
  build follows this initialization path. This is concrete evidence against treating
  our read-only-FD worker as a drop-in LOK host, not a theorem that every possible
  port must use that filesystem layout.
- Prior R11 primary records verify MPL2/LOK APIs, POI Apache2 and AWT references,
  platform PDF seekable-FD and isolation guidance, SurfaceControlViewHost, and
  feature-detected AndroidX profiles. Their hypotheses/limits remain in R11.
- Real SDK-manager stable catalog observation returned **NOT_LISTED** for exact
  `platforms;android-37` on the queried runner. This is not a universal absence
  proof or an excuse to invent API37 calls. SDK36 remains the explicit build
  baseline; SDK37/AGP compatibility needs a fresh successful availability/build
  experiment before migration.
- Retrieved upstream Linux `do_prlimit` checks hard-limit increases against
  CAP_SYS_RESOURCE in the initial user namespace, applies security hooks and arms
  CPU timers subject to CONFIG_POSIX_TIMERS. Those are required hypotheses, not
  evidence that a different Android kernel permits the same calls. Research excerpts
  are not vendored/linked GPL product code.
- The Linux CPU observations are direct libc/kernel experiments, not an imported
  Android guarantee. New native JNI/seccomp/ABI work has not been performed.

Exact open interface: platform WebView host profiles have concrete supporting
runtime evidence but not the compositor/security integration; LOK needs an audited
Android payload plus a viable writable/virtualized profile/temporary-file interface;
POI needs transitive/license and selected-path Android compatibility; sandbox CPU
needs aggregate/fork/Android enforcement beyond a cooperative timer or per-process
host limit. Unknown licenses remain blocked, including transitive components.

## D/E/F/G. Mechanism-distinct portfolio, artifacts and candidate-specific attacks

| Mechanism | Construction / concrete artifact and established portion | Strongest structure-derived attack / cheapest next falsifier | Exact gap; local or original-scale |
|---|---|---|---|
| Atomic rename with self-describing trash object | NoteFiles.delete/restore/trash;42 real-file assertions, no separate metadata publication | Crash after move before force; non-cooperating rename; changed trash bytes; same-name restore. Tamper/collision/lock/capacity attacks executed; physical crash not | Local filesystem/durability/UX gaps; full Notes attachments/OCR/export still original-scale |
| Focus-ownership feedback | MainActivity windowHasFocus guard; real old-source failure and patched child-focus pass | Focus child in inactive window, then compositor activation effect steals it. Actual counterexample reproduced | Additional pointer/capture/IME/external-display/AX cases local to integration; not whole H1 |
| Kernel FD/UID and native socket capability boundary | FormatLease/Worker plus real listener positive controls | Unreachable listener mislabeled denial, recycled Message op, writable/offset FD, wrong UID. Explicit operation copied before Handler recycle; native socket test distinguishes errno | Active hostile parser, parent death/RSS and all channels remain original sandbox work |
| OS WebView process profiles | Debug BrowserProfileProbe and BrowserProfileDeviceTest; actual same-origin cookie/DOM behavior | Different origins or in-memory maps fake partition; suffix set too late; same datadir; B clear leaks into A. Same-origin cross-clear test executed | UI/renderer lifetime, broker permissions, host UID policy, storage matrix; substantial original browser work |
| Irreversible per-process kernel CPU ceiling | probe_kernel_cpu_limit.py + measured JSON | Ignore SIGXCPU; raise hard limit; fork descendants resetting CPU accounting. All three experiment modes executed | Fork/aggregate escape observed; Android seccomp/JNI/ABI and RSS/wall control absent, not a complete sandbox |
| LOK profile/storage adaptation | Pinned Android directory-creation source + executable FD boundary precursor; causal mismatch established | Initialize actual audited payload without writable profile/temp paths; record failure instead of assuming file-URL FD support is sufficient | FD/VFS port or minimal-permission engine APK still needs build/legal/fidelity; original-scale engine integration |
| Bounded streaming Office extraction / headless subset | Existing OOXML production contracts rerun; prior POI API/AWT evidence | Ignored ZIP parts evade bytes; extraction passed off as fidelity; JVM/AWT success passed off as Android support | Extractor is useful but cannot fulfill FF01/FF02; selected POI Android/license spike remains |
| Authenticated local snapshot and correlated publication | Existing encrypted-index/copy/recovery kernels rerun against actual files and strict schemas | Wrong-scope key, stale picker identity, cross-destination receipt, rollback and unverified publication | Proven finite host subset; provider, Keystore lifecycle, live Find and full move journaling remain |

Early independence: Notes does not depend on a browser engine; browser experiment
is debug-only and does not mutate the main renderer gate; Linux kernel experiment
is external tooling and not linked into the APK; socket probe does not import a
parser; Office source research is not represented as an imported binary. The
portfolio artifacts are different mechanisms, not six copies of a UI/reducer.

### At least15 false-solution shapes and detectors

1. Delete with an unlink and label it reversible → reopen trash after a fresh store instance.
2. Separate metadata and data writes called atomic → current one-object filename avoids that split; physical crash test still required.
3. Restore overwrites an identical live note → explicit identical-content collision refusal test.
4. Receipt from a previous deletion restores the current generation → token/revision replay tests.
5. Tampered trash bytes become a newly blessed revision during enumeration → filename-bound digest comparison refuses catalog/restore.
6. Trash silently purges at capacity →256-entry capacity test requires original live note remain.
7. A symlink escape is counted as restore → actual symlink/directory-link tests retain outside bytes.
8. Correct host ASCII tests imply Unicode filenames everywhere → actual C-locale failure and UTF-8-locale success retained.
9. A revision/hash is called authenticated storage → same-UID rewriting remains excluded; use authenticated store for a different threat model.
10. Window activation refocuses the container and still counts as child focus → actual old-source Android failure.
11. The new focus patch inherits old green CI → full candidate suite rerun plus inactive-child attack.
12. Semantic node existence is usable UI → require displayed/clickable actions and a displayed confirmation; waiting alone failed.
13. Horizontal scrolling nested inside vertical scrolling is assumed keyboard/reflow complete → wrapping toolbar and200%-font native test.
14. Permission flag equals socket denial → actual native socket attempt with live before/after positive controls.
15. Java exception, timeout or ECONNREFUSED equals denial → only direct security refusal accepted; first Java-wrapper candidate failed.
16. A recycled Handler Message is captured across async execution → copy operation/id/arguments before dispatch; mixed digest/socket cases exercise replies.
17. Distinct origins fake browser profiles → identical origin and opposing cookie/localStorage operations.
18. Distinct PIDs imply different security UID → browser experiment explicitly asserts same app UID and claims only partitioning.
19. A hidden WebView/DOM test is a completed desktop browser → required UI, split, permission, profile-lifetime and AX tests remain absent.
20. Root MPL/Apache license proves all dependencies legal → payload-level closure required; no Office dependency imported.
21. LOK documentLoad ABI means it can run with a read-only FD alone → concrete Android profile-directory code contradicts that shortcut.
22. Ignorable soft CPU signal is a hard guard → actual ignored-soft/hard-kill experiment.
23. Per-process CPU limit bounds the entire request → actual fork-reset counterexample exceeds aggregate budget.
24. Successful emulator tests equal physical/16K/AX release → full acceptance still0/48; release validator refuses.

## H/I. Executed verification, failures, repairs and no inherited trust

Host logs: `r11b-jvm-contracts.txt`, `r11b-python-checks.txt`,
`r11b-independent-checks.json`. Linux experiment: `r11b-kernel-cpu.json` with exact
source SHA-256, architecture, native errno, exit status and measured CPU/wall time.
Reproduce from `android/` with actual Kotlin/Java paths:

```sh
LC_ALL=C.UTF-8 JAVA_HOME=/actual/java17 KOTLINC=/actual/kotlinc python3 tools/run_jvm_contracts.py
LC_ALL=C.UTF-8 JAVA_HOME=/actual/java17 KOTLINC=/actual/kotlinc python3 tools/run_r11_interop.py --report docs/verification/r11b-independent-checks.json
python3 -m unittest discover -s tools -p 'test_*.py' -v
python3 tools/probe_kernel_cpu_limit.py # Linux host only, runs bounded child processes
python3 tools/check_project.py
./gradlew :testing:noteTrashContracts
# Disposable AVD only, never personal data:
./gradlew :app:pixel6Api34ProDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.fusionDisposable=true \
  -Pandroid.testInstrumentationRunnerArguments.notClass=dev.fusion.shell.VisualCaptureTest
```

- c9f7dc0/run38668309848: old-source focus assertion failed exactly as predicted;
  candidate focus passed.27 device cases:25 PASS/2 FAIL. Socket Java wrapper gave
  OTHER_FAILURE/errno−1, not accepted as denial. Trash confirmation was not displayed.
-04ff55e/run38668756004: browser actual profile/DOM test PASS;28 cases26 PASS/2 FAIL.
- ac433fe/run38669282249: native syscall probe PASS, browser/focus/dirty-backlink
  tests PASS.28 cases27 PASS/1 FAIL. Waiting10s did not fix trash UI; it remained
  a failed candidate. This is why the dialog issue was not waived as timing.
-1ea1faa: replaced nested horizontal actions with wrapping FlowRow, made dialog
  explanation scrollable, kept visible-control assertions, added an actual200%
  system-font-scale test with explicit disposable-emulator opt-in and restoration.
  **Run38670396374:29/29 PASS,0 FAIL,0 SKIP**, including both normal-font and
  actual200%-font trash/restore, dirty-backlink, focus, browser profile and six
  boundary cases. Both debug flavors, invoked unit/contracts/lint and APK upload
  passed at this same revision. Companion run38670400049 also completed success;
  the named results here come from38670396374, not inferred from its companion.
  The reflow repair is supported for these cases; the exact original layout
  failure mechanism is still a hypothesis, not established by changing one factor.
  [Executed run and APK artifacts](https://github.com/jyvplay/OSB/actions/runs/38670396374).
  Case/count progression, jobs and artifact SHA-256 digests are retained in
  `verification/r11b-native-results.json`, `r11b-ci-jobs.json`, and
  `r11b-ci-artifacts.json`. These are evidence metadata, not vendored APKs.
  The font test restores its previous setting in finally; it does not establish
  all configuration-change orderings or replace human AX/keyboard review.

`check_focus_regression.py` keeps counterfactual XML separate, restores current
source in `finally`, removes stale results, and exposes unexpected compile failures.
The ordinary reporter reads only the candidate run. Historical failures remain in
`r11b-device-*-annotations.json`. This is external runtime execution, not an
independent source review or formal theorem proof.

## Feature/module testability: substantial executable implementation vs absence

The full48-row acceptance table remains [MODULE-COMPLETION](MODULE-COMPLETION.md).
All rows are0/1 accepted (0%). The full13 Productivity/13 requested Forge and19
nested browser gap tables remain in [R11](R11-ENGINEERING.md); this continuation
does not silently shrink them.

| Area | Enough real code for what test? | Missing behavior / acceptance |
|---|---|---|
| Notes | Yes: bounded file store, saved backlinks, real trash/restore, native UI, dirty-draft confirmation; host and Android tests exist | Attachments, OCR, full Markdown, import/export, autosave/process-death drafts, backup and full AX/durability |
| Compositor | Yes: native own windows and actual inactive-child focus experiment | Complete H1/BlackTop/interactive external display/Room/performance/keyboard/AX |
| Browser | Yes: metadata/codec host tests and debug real provider/profile/DOM experiment | No connected desktop browser UI, native profile manager, permission/download/reader/filter/splits/drag/cast integration; no S29/S30 pass |
| Format sandbox | Yes: actual UID/FD/digest/death/socket experiments; Linux CPU research is separately runnable | No hostile document engine, parent-enforced wall/RSS/tree containment or full FF12 |
| Office | No real Android LOK/POI engine to render/edit/save the ten formats; only own lossy extractor, gate code and primary research | Payload, legal/transitive audit, profile/temp storage, macros-off/fidelity/corpus/ABI integration |
| Editor / calculator / copy / encrypted index | Yes: substantial bounded kernels and selected native tests, rerun rather than replaced | Full respective scope, real providers/source indexing, process-death/AX/physical gates |
| Tasks/calendar/contacts/mail/clocks/IME/sticky notes | No new substantial implementations in this increment | Original PR03–06/10/12/13 remain absent or materially incomplete |
| Other platform/Forge/Beam/Lens/Meet/Messages/backup/commerce | Existing source subsets only as detailed in the full audit | No new claimed transport, engine, hardware, legal, entitlement or complete feature pass |

## Alternative routes and next decisive tests

1. **Browser:** platform WebView with process-specific profiles now has a real
   narrow experiment, not only a proposal. Next: two interactive remote surfaces
   in the own compositor, independent navigation/IME/AX, provider matrix and an
   explicit trusted-host vs isolated-renderer security review. Same UID does not
   fulfill a stronger separate-UID host requirement. AndroidX multi-profile support
   can be another feature-detected route, not inferred from API level alone.
2. **Office:** isolated LOK requires a tested FD/VFS or bounded-memory profile/temp
   adapter, or a separate minimal-permission engine APK with its own scratch area.
   Separate APK protects main-app storage but not automatically cross-document
   caches or guaranteed kill. First test is actual audited Android initialization
   and storage access, before claiming rendering. POI is a separate headless
   subset route, not a replacement for Office visual editing.
3. **Sandbox:** a hard per-process native CPU ceiling plus verified irreversible
   fork denial could improve on a disableable in-process watchdog. The observed
   fork attack means the combination, Android applicability and retained mappings/
   RSS/wall behavior must be tested, not assumed. No new JNI payload was shipped.
4. **Productivity:** keep bounded app-owned stores and reversible operations;
   add permission-scoped Calendar/Contacts adapters and inexact scheduling only
   after their actual platform/provider tests. Do not substitute delegated apps,
   fake inboxes, exact alarms or a plaintext index for the requested features.

## J. Stopping predicate and exact residual gap

The complete success predicate remains false. The release checker was executed
and correctly exited1 with an unverified-requirements refusal; its output is in
`verification/r11b-release-check.txt`. New working subsets and real
counterexamples do not authorize all-feature or beta claims. The native reflow
candidate passed the named29 cases; next highest-information steps are interactive
browser surface/host-boundary tests, an audited Android Office initialization/
storage experiment, and Android-native aggregate CPU/fork/RSS enforcement. Physical
reference devices, API37/toolchain,16K payloads, human AX and legal closure remain
outside the observations obtained here. Open `OSB/android` in Studio Rabbit1 with
the documented compatible JDK17/SDK36 baseline; do not mistake IDE naming for
verified SDK37 support.
