# R12 — recoverable Notes, real remote surfaces, and bounded testability

**The complete original Android application is not delivered.** This is executable native/Kotlin engineering and recorded experiments, not a web copy, simulated feature set, or production-beta declaration. Missing engines are missing implementation, not merely a test backlog.

## Numerical status and what the percentages mean

| Standard | Absolute result | Percentage |
|---|---:|---:|
| Substantial executable subset with an existing behavioral check | **23/48 modules** | **47.92%** |
| Not established by that conservative testability standard | **25/48** | **52.08%** |
| Entire assigned specification accepted | **0/48** | **0%** |
| Entire assigned specification still outstanding | **48/48** | **100%** |
| Requested Productivity / Forge / browser a–s fully accepted | **0/13;0/13;0/19** | **0%;0%;0%** |

The new [48-row testability census](MODULE-TESTABILITY.md) identifies actual production code, existing check and runnable target for each positive row. A parser/codec/kernel can qualify without its enclosing feature being complete. A negative row may contain substantial untested Android source; it is not automatically empty. Value types, admission/denial-only gates, a compiled folder, simulated peers and unavailable labels do not earn this credit. The executable harness counts as one of the existing48 Gradle modules, not another product feature. Source/check hashes reject stale pairings; they do not prove the human-authored behavioral review.

The separate [whole-module acceptance audit](MODULE-COMPLETION.md) and231-row requirement ledger retain their full denominator,36 cross-cutting obligations and30 unpassed spikes. No original requirement or device/legal gate has been promoted by the testability census. Stale spike reasons claiming that no Android build existed were corrected; each spike still lacks its own accepted evidence.

## Runtime honesty

Actually available and used: repository read/write tools, Bash, Git and `gh`; Python standard library; cached Kotlin2.2.21 compiler and Java17 runtime; GitHub Actions with JDK17, Gradle/AGP8.13.2, Android SDK36 and actual API34 managed-emulator execution on both `aosp-atd` and regular Google images. The local environment has no full Android SDK/ADB or physical device. Host contract and oracle processes ran concurrently in separate build directories; they are not independent agents. The independent oracle means separately implemented Python framing/decoding checks, not an independent human/model reviewer.

Actual systems exercised include Android AtomicFile/ViewModel, a debug service process, SurfaceControlViewHost/SurfaceView/WebView, Linux libc/prctl/seccomp/setrlimit/fork/exec and kernel signals. Emulator execution is not physical-device acceptance. No fake Android API substitutes, generated screenshots or invented receiver/device measurements were used. Pixel checks read the actual emulator framebuffer; results must be read at the exact candidate below.

No theorem prover, independent agent team, counsel, accessibility expert, reference hardware, Office payload or private database was available. Primary sources were retrieved through GitHub, pinned to commits and verified against Git blob SHA-1 plus recorded SHA-256. No assertion is presented as a universal proof or transitive license closure.

## A. Admissible objects, access, quantifiers and resources

### Notes recovery

A draft is an immutable `(name,text,savedText,revision?)` value. A revision binds a saved name and SHA-256 of its exact UTF-8 base. Recovery contains **no pending delete/open/restore command, URI, authorization grant or executable operation**. It is a separate local checkpoint, never an implicit `.md` save or sync.

Admissible fields: unfinished name≤64 UTF-16 units and≤256 encoded bytes; current and saved text each≤256KiB strict UTF-8. A revision-bearing name must additionally satisfy NoteFiles naming rules, and the saved base must match its revision. Unsaved drafts cannot claim a nonempty saved base. One frame≤525,312 bytes, with version magic, explicit lengths and SHA-256 checksum. Unknown versions, invalid flags/UTF-8, excessive lengths, truncation, inconsistent bases, digest mismatch and trailing bytes refuse. SHA-256 is corruption/revision evidence, not authentication against a same-UID attacker who can rewrite and rehash files.

Access model: Activity-scoped owners, trusted app-private ancestors and a shared `no_backup` checkpoint. Inspection found that MainActivity is not singleTask, so a per-ViewModel mutex alone was insufficient. The repaired store serializes cooperating owners with a process monitor and OS file lock, requires a prior read, and compares the entire last-observed checkpoint including absence before replacement. Conflicting owners must reconcile explicitly; no arbitrary hostile same-UID directory replacement, physical-media durability or backup rollback is proved. OS locking is cooperative, and content-based comparison is not a durable monotonic generation counter. AtomicFile temporary/legacy files and external backups are not counted as extra user drafts or claimed securely erased.

Quantifier order: for each admitted cooperating-owner trace, for each **acknowledged** checkpoint C, if the owner subsequently dies, no later checkpoint replaces C and the private file remains readable and unchanged, a fresh reader must recover C; if validation fails, the production ViewModel must block editing/checkpoint overwrite rather than silently start empty. For each recovered stale base whose current saved revision differs, the ordinary saved-note operation must refuse rather than bless that base. Tests establish these predicates only on their executed finite traces; no universal filesystem theorem is asserted.

Owner UI/state transitions remain main-dispatcher operations. Each owner’s automatic mailbox is capacity1/conflated with a500ms coalescing delay. A manual request uses the same mailbox, not one coroutine per click. That is a queue/memory design bound, **not a500ms persistence SLA**. Kernel IO, scheduling and an in-flight commit can take longer. The latest unconfirmed edits can be lost. With n owners the mailbox/storage-state budget is per owner, not a proved global Activity-count or whole-app RSS cap. Content-based comparison does not promise monotonic history or reject a value-identical ABA sequence. This new Notes guard does not retrofit Editor/calculator/desktop recovery with cross-owner coordination. A confirmed discard/new/open can leave an older checkpoint until its replacement is confirmed; recovery is not secure erasure or a two-resource transactional journal with the saved-note store. A failed inventory read after a state-changing operation still schedules the changed recovery state.

### Remote browser embedding

Objects: two actual WebViews in two dedicated nonexported debug services, distinct PIDs, same app UID and distinct pre-initialization profile suffixes. Each receives a real host input token and a SurfaceControlViewHost. A debug Activity embeds its SurfacePackage in a SurfaceView. The fixed, test-owned local HTML is the input target, not a pretend remote website. Network/file/content access is disabled.

Bounded requests: surface width/height64–4096 pixels; correlation IDs36 characters; DOM reply≤256 characters; sequential test requests;10s reply deadline;60s debug-process watchdog. DOM measurements use CSS pixels while relayout and injected screen coordinates use physical pixels; resize tests require a strictly smaller positive DOM height rather than equating the units. Framebuffer RGB tolerance is±8 per channel around the authored button background, away from text.

For each tested pane, actual injected input within its bounds must change only its own counter, with both counters initially zero. READ evaluates state only and cannot increment it. After relayout, input within the smaller surface must still work; input outside it must not reach it. Require actual displayed pixels as well as DOM results. Require distinct non-main PIDs, matching nonempty provider identity and explicitly same UID. The completed39-case run includes this successful trace: a witness for that provider/API, not a proof for all providers, compositor arrangements or origins.

**Imported hypothesis:** AOSP SurfaceView specifies above-host z-order for embedded input. The first runtime candidate omitted it and failed input despite loading the DOM. `setZOrderOnTop(true)` is the tested repair, not proof that browser chrome/overlapping compositor windows can already layer correctly.

### Linux CPU/fork experiment

Domain: a fresh unprivileged LP64 x86_64 Linux child; trusted installation before extra threads or child processes; the supervising Python process is not filtered. The script rejects other architectures rather than guessing syscall numbers. A real preexisting-thread attack must be refused. No assertion is made about arbitrary already-multithreaded Android/ART processes. RLIMIT_CPU accounts for process-lifetime CPU including startup and prior requests, not a fresh per-request stopwatch; the observed exec does not reset it. An unprivileged reused worker cannot simply raise its hard budget for every new request.

Install `no_new_privs`, then classic BPF seccomp that checks architecture, rejects x32-tagged syscalls and denies clone/fork/vfork/clone3 with EPERM. Set RLIMIT_CPU soft1s/hard2s, ignore SIGXCPU, attempt to raise hard limit, stack an allow-all filter as a bypass attack, and perform a real interpreter `exec` after≥0.75 CPU seconds. Check retained filter, no-new-privileges, CPU usage and hard limit after exec. Accept only actual kernel SIGKILL with measured1.8–3.0 CPU seconds; a15s cleanup timeout never earns a pass. The x32 attack must die with SIGSYS, not return a convenient generic error. Deliberate signal tests disable core dumps.

This constrains selected process-creation/CPU behavior. **All other syscalls are allowed.** It is not RSS, wall-time, network, filesystem or complete sandbox containment. CPU work offloaded to kernel workers, GPUs or other services is not covered. Blocking clone also prevents new threads, making the unchanged policy incompatible with engines that initialize new threads after installation.

### Adjacent problems explicitly excluded

A checkpoint is not explicit note save, backup, encryption or every-keystroke durability. Owner recreation is not process death; death of a dedicated writer is not a main-app force-stop or physical power cut. A checksum is not authentication. A SurfacePackage/DOM reply is not a visible/input-capable browser. Same-UID profiles are not hostile-host isolation. Above-host surfaces are not already integrated compositor chrome. A host seccomp test is not Android enforcement. Per-process limits are not process-tree limits when descendants already exist. A primary Android Office build description is not a shipping audited payload. POI headless extraction is not Office visual editing. External Office/cloud conversion, stock desktop and system casting remain non-solutions for the requested native capabilities.

## B. Outcome space and declared thresholds

- **H+**: bounded local recovery, remote rendering/input and stronger kernel resource boundaries can advance the architecture. A narrow implementation earns evidence only from compiled production behavior plus its actual adversarial runtime checks.
- **H−**: the full acceptance conjunction cannot be discharged with the current absent implementations and unavailable physical/legal/AX observations. This is a bounded-access conclusion, not an impossibility theorem about ever building the product. DOM success alone was experimentally insufficient for embedded input. The earlier R11B fork counterexample remains valid for RLIMIT_CPU alone.
- **H∂**: conclusions change with z-order, viewport/coordinate units, provider/API, recovery acknowledgement, stale revisions, checksum trust, single-owner/single-thread installation, ABI and engine thread/storage requirements.

Thresholds were recorded before the new Android implementation in `R12-SPIKE-PLAN.md`. Host-code acceptance needs executed contracts/attacks; native claims need actual named cases, not successful assembly; engine choice needs payload/license/ABI/storage/fidelity tests; release needs all original gates. Repairs inherit no previous test result. Missing or skipped provider execution is unverified, not a pass.

## C. Retrieved frontier, hypotheses and open interfaces

See `research/r12-primary.json` for exact commits, blob identities, SHA-256 and excerpts. These are primary results verified in a bounded search, **not a claim to have exhausted global literature or proved a best-possible theorem**.

1. **AOSP SurfaceControlViewHost and SurfaceView:** a remotely owned view hierarchy can be transferred as a SurfacePackage; host tokens, lifetime and z-order matter. The package recipient owns its parcelled reference, not the remote host. Hypotheses include a supporting platform/provider and attached display/input hierarchy. Open interface: own-compositor overlap/chrome, input focus, IME, AX, process death, configuration changes and stronger UID isolation.
2. **AOSP AtomicFile:** supports private file replacement/recovery but is not a multi-writer transaction or physical durability theorem. Existing CheckedAtomicFile adds explicit sync and decoded readback because finishWrite can log failures. Open interface: whole-operation crash cuts, correlated saved-note/checkpoint transitions and physical storage behavior.
3. **Linux seccomp source:** unprivileged filtering depends on no-new-privileges or suitable capability; mode/filter installation and retained restrictions have kernel hypotheses. The retrieved upstream source differs from the running6.1.158+ host and Android kernels. Actual host tests provide limited observations; no GPL source is vendored into the application.
4. **LibreOffice Android README and LOK API:** upstream describes a real Android application bundling `liblo-native-code.so`, document tiles and four threads including a native soffice thread; LOK has load/save/tile interfaces. This refutes the unsupported claim that no native Android Office route exists. It does **not** establish this repository's audited build, correct licensing of every payload, ten-format editing fidelity or 16KiB compatibility.
5. **POI SXSSFWorkbook:** upstream streaming workbook handling still uses temporary files and explicit cleanup/compression behavior. Lower heap usage is not no-storage initialization or a read-only-FD drop-in. Open interface: Android path/dependency compatibility, temp budget/cleanup, AWT-dependent paths, macros policy and fidelity.

Global compatibility matters: the unchanged no-clone policy cannot simply be combined with the documented new-thread Office bootstrap. A candidate needs verified whole-thread-group filter installation and a carefully checked thread-only clone policy, or a different parent-enforced architecture. Neither is silently assumed implemented.

## D/E/F/G. Mechanism portfolio, artifacts and structural attackers

These branches were kept distinct: codec framing, persistence, conflict authorization, rendering/input, profile partition, kernel enforcement and engine-placement designs. They are not eight filenames counted as eight solved features.

| Mechanism | Concrete artifact / established portion | Structure-derived attacker and cheapest falsifier | Exact gap; local vs original-scale |
|---|---|---|---|
| Strict value-only recovery frame | `NoteDraft.kt`;308 real Kotlin checks and410 Python/Kotlin oracle checks | Recompute checksum around malformed fields; truncate at every offset; corrupt every byte; forge base relation. Those finite attacks executed | Physical persistence/integration is separate; full Notes remains original-scale |
| Serialized checked AtomicFile | `NotesRecoveryStore`, production ViewModel, native recovery tests | Corrupt committed file, fail readback, stop after acknowledgement, flood manual requests. Exact native results below | Last checkpoint only; cooperating-owner OS-lock/value-CAS candidate; power-cut/backup/arbitrary-writer gap |
| Revision-bound saved-note conflict check, no command replay | Real NoteFiles plus value-only recovery | Change saved bytes after checkpoint; retain a pending Delete before fresh owner. Refuse overwrite and clear command on recovery | Hash trust/cooperating writer assumptions; not universal authenticated history |
| Remote native surface ownership/input | Debug SurfaceControlViewHost services and injected-input/framebuffer test | Correct DOM with wrong z-order; wrong pane receives tap; resized-outside input leaks; over-budget resize damages live host | Native subset only; compositor chrome/IME/AX/security/browser integrations original-scale |
| Process-specific WebView storage | Existing real same-origin cookie/localStorage experiment rerun with suite | Shared suffix, suffix too late, clear profile A erases B | Same UID; all storage/provider/lifecycle/browser requirements remain |
| Kernel no-fork plus hard CPU | `probe_no_fork_cpu.py`, actual fork control/exec/filter/x32/prethread experiments | Fork reset, exec reset, filter replacement/removal, hard-limit raise, preexisting thread and alternate ABI | Host boundary observed; Android/thread compatibility/RSS/wall still original-scale |
| First-party separate-UID Office engine APK | Causal design: main broker grants input FD → audited minimal-permission engine UID with own scratch → bounded tile/edit/save IPC; primary Android LOK interfaces are concrete | Engine asks for a main private path, leaks old-document scratch, crashes after output publication. Cheapest next test: audited Android initialization plus directory/FD access matrix | No payload shipped; legal/transitive/ABI/fidelity/kill gaps are original-scale. This is not ACTION_VIEW delegation |
| Isolated-engine FD/VFS or bounded-memory adapter | Causal design: read-only source FD + budgeted scratch adapter + tile buffers; LOK profile and POI temp-file requirements identify actual write edges | Profile/temp code bypasses adapter, native allocator exceeds budget, threads bypass filter. First falsifier: trace initialization opens/allocations with actual payload | Adapter is not implemented. The conditional working-set bound is input bytes + scratch budget + tile bytes + engine baseline; unknown baseline/native allocations prevent a claimed numerical RSS cap |

The last two are explicit causal/interface models, not shipping code, approval to add unknown-license dependencies, or assertions that an adapter is a “standard solved step.” The cheapest next experiment is specified because the engine/payload remains absent.

## Negative space:24 false-solution shapes and detectors

1. Persist only on Activity rotation → fresh-owner and separate writer-process tests.
2. Call saved Markdown “draft recovery” → recover an unsaved draft and verify saved bytes unchanged.
3. Replay pending delete on startup → persist only values; assert pending action is absent after recovery.
4. Accept corrupt checkpoint as empty and overwrite it → corrupt-file test demands blocked edits and exact retained bytes.
5. Recompute revision from recovered draft and overwrite newer save → stale-base real-file/native tests.
6. Trust checksum alone → resealed malformed UTF-8/length/flag/base attacks.
7. Allocate from attacker-supplied length → Int.MAX_VALUE length refusal before allocation.
8. Accept trailing payload/version ambiguity → unknown magic and trailing-byte tests.
9. Claim all typing is durable → require explicit checkpoint status; stopping before acknowledgement remains a documented loss window.
10. Launch one IO coroutine per checkpoint click or blindly overwrite another Activity owner →1000-request native child-job-count regression; one mailbox per owner, plus stale-owner/missing-versus-empty checkpoint tests.
11. Treat directory/file sync as physical power-loss proof → no such claim without real power-cut matrix.
12. Count hidden DOM execution as a browser → require actual compositor pixels and injected-input counter changes.
13. Guess z-order does not matter → actual failed first input trace; AOSP hypothesis and repaired rerun.
14. Route both panes to one renderer/host state → distinct PIDs and independent counters.
15. Let out-of-bounds resize input reach a remote child → outside-resized-surface input attack.
16. Accept oversized resize then lose live surface → refusal followed by a successful real tap.
17. Count same-UID data partition as security isolation → assert same UID explicitly and retain stronger isolation gap.
18. Count per-process CPU as aggregate containment → retain actual R11B fork-reset counterexample.
19. Let exec shed restrictions/accounting → actual new-interpreter exec and rechecked syscalls/usage.
20. Apply a per-thread filter to existing arbitrary threads → preexisting-thread preflight must refuse; no TSYNC claim.
21. Bypass syscall comparisons with x32 tagging → real tagged syscall must receive SIGSYS.
22. Silence a timer or raise hard CPU → ignore SIGXCPU, attempt hard raise/filter removal and allow-all stacking; require kernel kill/errno.
23. Claim a no-clone sandbox is a compatible Office host → upstream new-thread bootstrap is a concrete compatibility countercondition.
24. Count testable code or random peer displays as completed modules → separate23/48 testability from0/48 full acceptance; legacy random Together UI/model/tests removed, not relabeled as real transport.

**Modal shortcut:** running JavaScript in a hidden WebView and calling that a desktop browser omits pixels, real input, chrome layering, navigation, permissions, persistence and security. The new remote-surface test detects the pixels/input subset of that shortcut; the first native attempt actually failed it. Passing the repaired subset still cannot promote PR07.

## H/I. Verification ledger and repairs

Host evidence (pure production sources covered by these host runners are unchanged by the subsequent Android UI/startup repairs):
- `verification/r12-jvm-contracts.txt`: **183,179 Kotlin assertions** across13 independently compiled production libraries, including308 new draft checks. “Independent libraries” describes compilation boundaries, not agents.
- `verification/r12-interop.json`: **410** new exact-frame/decoder/malformed-frame Python–Kotlin checks.
- `verification/r12-existing-interop.json`: **323** rerun browser-session/backlink checks. Combined oracle checks733; not feature-completion points.
- Python tool suite: **68 methods** after adding seven census checks; source/project/generated audits remain separate from Android compilation.
- `verification/r12-no-fork-cpu.json`: current source-bound real Linux results. Unconstrained control completed2.535085 CPU seconds and a real fork. Filtered/exec'd child died by SIGKILL at2.003869 CPU seconds. Preexisting-thread install refused; tagged x32 syscall died by SIGSYS. No cleanup kill counted.

Native progression:
- `2f28dd7`, run38672293602: debug Activity failed Android compilation because `left/right` inside a layout receiver resolved to coordinate properties. Not runtime evidence. Failure output retained.
- `dbc75da`, run38672607445: both-flavor build/unit/contracts/lint passed; **33 cases:32 PASS/1 FAIL**. All three new recovery cases passed; remote browser failed its first actual tap with counter0 instead of1. Framebuffer/resize assertions after that failure were not reached and are not claimed executed for this candidate.
- Repair: explicitly qualify Activity SurfaceViews, then separately apply documented above-host z-order. Keep real input/pixel requirements, add over-budget/outside-resized input attacks; do not replace them with JavaScript `.click()`.
- Later candidates add actual dedicated writer death/restart and a1000-request checkpoint mailbox regression. The queue issue was identified by source review; no prior-source runtime counterfactual for it is claimed.
- `42afbcb`, run38673555260:34 cases,33 PASS/1 FAIL. Above-host z-order enabled independent real taps, but the screenshot pixel was0; framebuffer/resize acceptance failed. Actual dedicated Notes writer death/restart passed.
- `28f3be6`, run38673620389:35 cases,34 PASS/1 FAIL. The1000-request mailbox test passed; the same pixel assertion failed. Both debug flavors and invoked unit/contracts/lint passed. No framebuffer success was inferred from input success.
- `0f2dae3`, run38674521630: regular Google API34 image,35 cases:32 PASS/3 FAIL. **The unchanged full browser pixel/input/resize/bounds test passed**, as did actual writer death and the queue flood. One Notes startup/action test timed out and two Editor UI cases failed. This comparison changes runtime/provider; it is not a proof that a particular ATD component caused transparent capture.
- Repair candidate`0eceaef`: startup now publishes readiness only after recovery and inventory reads; Editor controls/tabs wrap rather than nest horizontal scrolling, dialog visibility is still required, and two distinct tabs must exist before typing the second document. Added first-ready inventory and actual200%-font Editor tests; font restoration now waits for resources to settle. The startup race and layout hypotheses are identified from source plus failure evidence, not independent universal causal proofs.
- `0eceaef`, run38675838172: **37/37 PASS,0 FAIL,0 SKIP** on regular Google API34; both-flavor build, invoked unit/contracts/lint and APK upload passed. Both first-ready and200%-font Editor attacks passed, as did the complete browser pixel/input/resize case and Notes writer death.
- Source review then found MainActivity permits multiple instances. Candidate`2c2b5bc` adds an actual OS lock plus expected-value comparison across checkpoint owners, with read-before-write, stale-owner, explicit reconciliation and missing-versus-empty attacks. This is a new repair, not covered by the preceding37 passes.
- **Latest native candidate`2c2b5bc`, run38676376263:39/39 PASS,0 FAIL,0 SKIP** on the regular Google API34 managed image. The two new ownership attacks and all earlier cases passed afresh. Both debug flavors, invoked JVM unit/contracts, lint and APK upload passed at the same revision. Companion run38676381199 also completed success; the named case records come from38676376263.
- [Verified run and `fusion-native-debug` APKs](https://github.com/jyvplay/OSB/actions/runs/38676376263). Exact case progression, job results and artifact digests are retained in `verification/r12-native-results.json`, `r12-ci-jobs.json` and `r12-ci-artifacts.json`. These are evidence metadata, not bundled APKs. The exact provider name/version was asserted and printed by the test but is not exposed in the retained Checks case-summary metadata; no unobserved provider/version matrix is claimed.

A rejected browser experiment remains debug-only and cannot unlock the blocked product browser. The removed Together random-peer model is not replaced with a fake service; its UI now says unavailable. Historical R11/R11B results remain historical rather than being overwritten with current counts.

## Alternative routes that preserve the original constraints

### Browser

The real profile and surface experiments make process-hosted platform WebView a more concrete candidate. Next is a two-window **own-compositor** integration test with overlapping chrome, focus, IME and AX, plus crash/lifecycle/provider tests. Above-host SurfaceViews can complicate overlapping controls; a root-surface hierarchy or individually composed chrome surfaces needs its own actual test. AndroidX provider-supported profiles offer a different feature-detected storage route; they do not by themselves solve hostile-host isolation. An isolated MPL GeckoView engine remains an alternative only after payload/transitive/ABI review. No generic WebView page, external browser or desktop mode is counted as Fusion Browser.

### Office

Use the upstream Android LOK path as engineering evidence, not as a universal prohibition. Two conditional designs are an audited first-party separate-UID engine APK with bounded scratch/IPC, or an actual LOK storage adapter inside an isolated worker. Both preserve native rendering/editing ownership but need a working payload and corpus. POI can serve selected headless extraction/conversion paths; its streaming APIs still need controlled temporary files and are not visual Office equivalence. External open remains optional only. Main APK permissive-license rules, isolated MPL modules, no unknown transitive license and no disallowed codec remain binding.

### Sandbox

The no-fork experiment repairs the specific host fork-reset mechanism, but its threading restriction is not an Office-ready solution. A thread-aware clone policy plus verified whole-thread-group installation is a possible next mechanism, not currently implemented. Parent-enforced wall deadline/lease death, bounded output/shared memory, real RSS pressure and existing mappings remain independent obligations. A separate APK provides another UID boundary but not automatically per-document cleanup or unkillable-engine resistance.

### Productivity

Notes now has a bounded recovery path rather than memory-only drafts. Import/export, attachments/OCR, richer Markdown and version history are still missing. Permission-scoped CalendarContract/ContactsContract adapters, inexact reminders and platform email handoff remain viable permission-light routes, but they require their own working adapters/provider tests; no inbox/task/calendar mock is introduced. Existing native editor/calculator kernels remain useful testable subsets, not complete PR01/PR09 acceptance. Full Files move journaling is not replaced by Notes AtomicFile or trash operations.

## J. Stopping predicate, runnable entry and next decisive tests

The full success predicate remains false. The strongest deliverables are real Notes code, Editor reflow, reproducible kernel/oracle experiments, the verified remote-surface subset and exact module census—not a claim to have finished every absent feature. The repaired full native suite passed its39 named cases. The next highest-information tests are own-compositor browser chrome/IME/AX, an audited Android LOK initialization/storage/thread experiment, and Android-native thread-group resource enforcement. Physical reference devices, API37/16KiB, hardware transports, counsel and human AX remain necessary. The release checker was executed and correctly refused acceptance; see `verification/r12-release-check.txt`.

Open `OSB/android` in Studio Rabbit1 **with an IDE/toolchain compatible with AGP8.13.2, Kotlin2.2.21, JDK17 and SDK36**. The IDE name alone is not a verified compatibility result. Current min/compile/target34/36/36 still deviates from the requested compile37 migration; the queried CI SDK catalog reports NOT_LISTED for37, not universal unavailability. See [BUILD](BUILD.md), use disposable text, and do not apply destructive instrumentation reset to a personal device.
