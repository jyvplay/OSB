# R23 — real native input/privacy hardening, not an anonymous OS

## Authoritative final checkpoint (historical chronology follows)

**Native candidate4bc7637, compile/target37 via real SDK37.0:** privacy38871422549 **16/0/0 SUCCESS**,
focused memory38871422568 **24/0/0 SUCCESS**, main38871422566 **121/1/0 FAILURE**.
Both main builds/JVM/contracts/lint and privacy build/lint succeeded; no all-green/native-release acceptance.
Main R18 task Add timed out with actual revision0/empty list, IME visible883px. Cause unproven; no rerun-to-green.
Current complete host run **134.057s/149 Python methods**, R23 assertions898161/160000 four-event words.
Census48/49 testable (97.96%),0/49 complete,4 existing production modules deepened (8.16%).
[Current concise results, artifacts, scopes and next falsifier](R23-RESULTS.md) · [machine record](verification/r23-outcome.json).

Statements of 'pending', restored SDK36, or14/15-case thresholds in historical checkpoints below describe
those earlier stages only. They do not override this outcome or turn a later failed full gate green.


Work is on **`arena/f77cb2a6-osb`**, starting from merged commit7a32437. The prior turn's full free-form input is not available here; the checked-in231-row requirement register,36 cross-cutting obligations,30 spike gates and R22 records are the recovered scope. No original requirement is silently removed. Browser/Office/physical/legal/AX gates remain closed.

## Current evidence (candidate-specific, not inherited)

- **910b739307924f7bedfd52c7d04827cd08badf56**: [privacy run38860744364](https://github.com/jyvplay/OSB/actions/runs/38860744364) **12PASS/0FAIL/0SKIP**, successful compile/lint/R23 Gradle contracts. Includes actual keypad tap, no IME connection, key/shortcut rejection, full/partial dispatch rejection, real focusable Dialog, background/recreation/cap/stale-key attacks and an actual61s idle wait; all6 earlier companion cases also passed. [Receipt](verification/r23-first-privacy-result.json).
- **Same910b739**, [main run38860744226](https://github.com/jyvplay/OSB/actions/runs/38860744226): **119PASS/0FAIL/0SKIP**, both debug builds/JVM/all invoked contracts/lint succeeded. [Receipt](verification/r23-first-native-result.json). This does not include companion cases or the subsequently added memory/reveal/AX assertions.
- **20c1fd92559158bf8780b6c02907e3d95bfd4fa1**: [privacy38862379063](https://github.com/jyvplay/OSB/actions/runs/38862379063) **14PASS/0FAIL/0SKIP** (new reveal/stale timer, trusted AX action and SharedMemory counterexample/2MiB Parcel); [main38862379057](https://github.com/jyvplay/OSB/actions/runs/38862379057) **119PASS/0FAIL/0SKIP**, builds/JVM/contracts/lint success. [Pre-implementation decision](verification/r23-memory-platform-decision.md) was committed before production memory code.
- **b3f2f1d879c59714e2918169c66bac550916540f**: [focused memory38864044897](https://github.com/jyvplay/OSB/actions/runs/38864044897) **24PASS/0FAIL/0SKIP** and [privacy38864044657](https://github.com/jyvplay/OSB/actions/runs/38864044657) **14PASS/0FAIL/0SKIP**. Production distinct-UID memory/wire/observer/FD/CSV-XLSX UI experiment passed; [main38864044743](https://github.com/jyvplay/OSB/actions/runs/38864044743) **122PASS/0FAIL/0SKIP**, both builds/JVM/contracts/lint succeeded. CSV regression8/8 and Tasks7/7 also passed; these overlap full cases and are NOT additional unique-case counts.
- Memory candidate adds explicit `evaluateMemorySheet/Csv/Xlsx`, fresh owned/unmapped/restricted SharedMemory, bounded readonly child, transport correlation, no disk fallback and worksheet UI integration. **Compilation and named gate24 passed at b3f2f1d; full122-case regression also passed at that exact candidate.** Legacy `evaluateOwned*` regular-file APIs remain explicit and separately enforced, not silently relabelled or fallback.
- Actual host compilation: **898,161 new assertions PASS**, **160,000 exhaustive4-event touch words**. R22 regression37 assertions and327 interop requests (271accepted/56refused),15 equal-byte projected-workbook pairs; R21 regression376 assertions PASS. Initial Python126 then130/131 and135/136/137/144 methods passed after additions. A generated-hash failure and its rerun are retained below. Final count/outcomes must use the latest exact record, not add overlapping runs together.

### Completion/testability, by the requested substantial-subset standard

| Standard | Absolute | Percent |
|---|---:|---:|
| Included Gradle modules with a real behavioral source/test pairing |48/49|97.96%|
| Not established under that standard (`:format:lok`) |1/49|2.04%|
| Fully specification-accepted modules |0/49|0%|
| Still incomplete against assigned full scope/acceptance |49/49|100%|
| Existing production modules with new R23 subsets now exercised on Android |4/49|8.16% touched; NOT full-module acceptance |

Affected initially: `:core:security`, `:privacy`; verified memory route also changes `:format:pdf` and `:feature:automation` (4/49=8.16% of inventory total touched, not acceptance). Original48-module comparison remains47/48 (97.92%). These are unweighted counts, not percent engineering effort or product completeness. The49 include test/profile modules, not49 interchangeable finished features. No new Gradle module was invented to improve a percentage. [Per-module commands, behavior, limits and hashes](MODULE-TESTABILITY.md).

## Runtime honesty

Actually available/used: shell/filesystem, git/gh, Python3.11.2, node/npm, search/page retrieval, optional host-only Kotlin2.2.21 on Temurin **JRE17.0.9**, Python zipfile/ElementTree/openpyxl3.1.5/XlsxWriter3.2.5, and remote GitHub Gradle/JDK17/actual managed API34 Android runtime. The initial sandbox had **no Java, javac, Kotlin compiler, Gradle install, SDK, ADB or Android runtime**. Public npm/PyPI installations supplied a real Kotlin compiler and JRE, **not a full JDK**. A maintained full JDK17 is required on the laptop/remote runner for Android. Bootstrap hashes/versions are retained in `r23-host-tools.json`. The old host-only JRE is for disposable tests, not a production security recommendation.

No independent agent/team/human reviewer, physical Android/keyboard/keylogger/DMA lab, Studio GUI, Excel/LibreOffice runtime, external database or theorem prover was used for R23. Z3/cryptography/Pillow were installed into an initial optional cache for possible older checks, but no new Z3 theorem proof or those verifier runs is claimed. Parallel tool calls/CI jobs are not independent reviewers. Exhaustive kernel input enumeration is a real code test, not an Android/host simulator. Managed Android is an emulator, not physical hardware. No app feature is mocked or replaced with a web preview.

Outbound policy blocks release-assets and Actions Azure-blob redirects: official GitHub compiler ZIP download, checksum-asset redirect and an actual `gh run download` attempt failed. No local APK/archive inspection is inferred. GitHub API run/job/annotation/artifact metadata was actually retrieved. Named native outcomes are bound to exact remote candidate metadata, not cryptographically authenticated by our local JSON checker. Debug APKs remain downloadable through GitHub on a permitted laptop/network while artifacts remain retained. No credential request/workaround occurred.

## A — formal model and resource accounting

**Objects/access.** Trusted Android API34+ platform and first-party companion UI process; caller-visible Android input/window/lifecycle APIs, limited settings, accessibility counts and input/display enumeration. The process does not control kernel, IME, accessibility tools, host/hypervisor, firmware, physical peripherals/cameras, external logs or other apps. An ordinary keyboard-only logger observes keyboard events but not in-app digit-button actions. This last restriction is essential, not a universal attacker description.

**Admissible input.** Touch pad is0..64 ASCII decimal digits, not arbitrary text/authentication. Normal scratchpad keeps32,768 UTF16 units; link input keeps4096 units plus existing strict UTF8/URI projection rules. Existing non-exported new screens take no inbound payload. No touch-pad save/import/provider/export/clipboard/authentication route exists. Legitimate accessibility activation is intentionally a trusted input route, not proof of a physical touch.

**Touch contract.** Events are phase DOWN/CONTINUE/UP/CANCEL/OTHER and two obscuration booleans delivered by Android. Every flagged event kills admission until a new clean DOWN; prior pressed children receive CANCEL, not rejected UP. Focus/background resets admission. No promise when a compromised platform lies about flags. Pointer/keyboard identity is not cryptographic authentication.

**Lifetime.** Fixed64-char buffer; cap+1 leaves it unchanged. Only accepted append/delete updates its monotonic timestamp. Exact60,000ms since last change, clock reversal, pause, focus loss, obscuration, manual discard or recreation discards from new UI. Reveal uses5,000ms scheduled hide and immediate hide on code edits. Main-looper scheduling is NOT a hard real-time upper bound under UI starvation/suspend/host compromise. Interactions also check elapsed realtime. Clear fills this one char array and drops views/references; returned/display Strings, framework/IME/native copies and RAM/flash/swap are NOT proven erased.

**Resources/units.** Digit payload capacity128bytes as64 UTF16 chars, excluding object/UI/framework overhead; O(1) admission and append/delete, O(64) clear/reveal. No total RSS or power theorem. USB inspector caps256 root hubs,4096 directory entries and33 consumed bytes per attribute (32 admissible plus overflow detector); at most512 attribute reads. It reads defaults only, collects no device names/serials, performs no sysfs writes/root/network/subprocess hardening. Unknown/unreadable/empty inventory is not zero risk.

**Quantifier order / success.** Fix a trusted platform/caller model first. For every admitted event/action sequence, returned pad text equals its accepted digit changes under the above cap/expiry semantics; for every discard event, subsequent newly attached UI/connection/stale keypad callback cannot restore old content. Then test declared corpora/platform cases; finite tests do NOT prove universal implementation correctness. Success for the scoped API34 experiment requires exact-candidate compile/lint/contracts and every required named native case plus whole successful workflow, not existence of one passing case. Full-product success additionally requires all original per-module/integration/device/AX/legal gates; none is claimed met.

**Owned-memory protocol / resources.** New explicit memory APIs freeze caller bytes and allocate `max(1,n)` with n≤64KiB for arithmetic-frame/CSV and n≤2MiB for XLSX. Empty CSV is0 payload bytes plus one zero padding byte. Parent's sole writer never escapes, is unmapped in finally BEFORE restricting future permissions to READ and publishing the Parcelable. Child checks exactly one carrier, operation/declared allocation bounds, failed permission widening and only permission-denied write-map errors; maps readonly, copies exactly n, unmaps/closes before parsing. No silent file fallback. Legacy file APIs retain regular/O_RDONLY/fresh-offset/consumed-output limits and real device/inode evidence; anonymous route uses explicit transport and sentinel inode/device−1, not invented filesystem measurements. Existing single admission, actual senderUID, distinct isolated UID,5s watchdog and no-INTERNET child remain. Reply hash/length/keys and bounded data validation correlate an honest worker response; a compromised child can still forge plausible wrong values. Provider input sources and NEW exported copies can touch disk. Anonymous pages, byte arrays, parser/UI Strings, heap copies, scheduler/OS/cache behavior and peak RSS are not erased/attested/bounded by the raw input-cap number.

**Ownership argument, not hostile-parent sealing.** Before handoff, trusted helper creates fresh memory, retains the only writer locally, exposes no aliases, explicitly unmaps it, then reduces protection. Under truthful platform semantics, no mapping created by that helper can still write after publication; every later write-map is refused. Existing-writer platform counterexample shows why restriction alone cannot establish that statement for an arbitrary/untrusted sender. Real wire tests reject unprotected regions but cannot detect every old alias in a compromised parent. FileObserver before/after controls establish observer liveness during the declared traces, not a universal no-file theorem for future source changes or every filesystem.

**Impossibility boundary.** Consider two physical keyboards that forward identical events and descriptors, one additionally stores typed keys privately. For any app algorithm limited to these interfaces and any fixed internal randomness, its observations are identical in the two worlds. Therefore it cannot both detect every such logger and clear every honest keyboard. This is a model-level indistinguishability argument, not a physical implant experiment or machine-checked theorem. A host observing guest display/pointer state likewise lies outside guest exclusion authority. No global/retroactive anonymity predicate follows.

**Adjacent problems not substituted:** no keyboard connection vs no observation; screenshot flag vs hostile-host secrecy; overlay hiding vs all accessibility blocking; masking vs erasure; count zero vs attestation; ADB setting vs all debugging transports; USB defaults vs currently admitted peripherals; SharedMemory mapping restriction vs revocation of existing writers; same-process Parcel vs isolated-UID transfer; host contracts vs Android execution; testability vs original-spec completion.

## B — outcome space / evidence threshold

- **H+** useful narrow path removal/ordinary-window/lifecycle controls under trusted OS/host assumptions. Admit only after production compile, boundary tests and exact named real API34 run.910b739 supports its12 named cases, not future cases.
- **H−** app-only detection/removal of every transparent hardware logger, firmware/host secrecy, making all activity anonymous or deleting earlier external records under this access model. Do not weaken the target and call it solved.
- **H∂** physical custody, observer capabilities, accessibility needs, real event semantics, scheduling and platform/engine/provider/legal boundaries change what is achievable.

Committing a candidate only requests verification; it is not acceptance. Repairs inherit no pass. If there is no adequate hardware/AX/footprint verifier, claim stays downgraded, not “verified by self-review.”

## C — retrieved frontier / exact open interfaces

[Fresh primary research](research/r23-primary.md) covers Tails physical/VM limits, Android full/partial tapjacking, secure-window/overlay controls, API34 accessibility trust, keyboard advisories, input-device enumeration, kernel USB authorization, shared memory, host DMA, real Gecko lifecycle, version-dependent Office licenses and Tor Browser vs arbitrary proxy. Needed assumptions and partially fetched/contradictory text are explicitly recorded. No search result proves our code and no exhaustive best-known literature claim is made.

The exact open interfaces: real overlay/AX/OEM/physical security review; actual laptop controls; trusted no-old-writer ownership PLUS new production distinct-UID shared-memory transfer verification; engine/compositor/frame/permission wiring; exact Office licenses/fidelity/ABI; total guest+host privacy/footprint measurements. A local green kernel does not bridge them.

## D — negative space (false solution shapes and detectors)

|#|Looks like a solution, but is not|Detector / exact reason|
|---:|---|---|
|1|No IME means no keylogging anywhere|Native keyboard rejection plus explicit trusted AX action; host pointer/screen is outside input-connection boundary|
|2|A touch-only pad authenticates genuine physical touch|Native accessibility-action activation counterexample; tools/in-process trusted code can activate buttons|
|3|FLAG_SECURE blocks overlays|Separate HIDE_OVERLAY_WINDOWS request and both obscuration flags; actual screenshot positive control is not an overlay test|
|4|Only filter the obscured DOWN|Clean DOWN then flagged UP attack; remaining clean UP must not start new UI|
|5|Only full obscuration matters|Partial-obscuration native dispatch case|
|6|Drop UP without cancelling a pressed child|Actual dispatch cancels before replacement; stale gesture/kernel history cases|
|7|Clear text but reuse editor/undo|Whole-editor replacement + new-view undo/recreation case|
|8|Detached keypad listeners retain authority|Old Button callback after clear/background must not mutate new pad|
|9|Masking equals erasure|Retained String counterexample in real Kotlin runner; memory/OS copies explicitly excluded|
|10|Truncate oversized code silently|64 then65th digit atomic refusal; unchanged revealed content|
|11|Reveal is private forever|Actual5s hide + immediate-edit hide + stale timer against new pad; looper timing caveat|
|12|Polling a length renews secrecy|Exact60s kernel boundary and actual61s idle wait; read/reveal does not renew timestamp|
|13|Clock reversal gives a new lease|Long/negative/reversed monotonic-input attacks clear|
|14|No INTERNET permission means no IPC/exfiltration route|Existing real AF_UNIX socketpair positive control; no universal IPC claim|
|15|Zero input/AX/display count certifies safety|All243 unknown/absent/present report combinations; report always denies all-clear|
|16|Unknown setting silently means disabled|UNKNOWN formatting and independent per-signal refusal; no wireless-ADB completeness claim|
|17|USB descriptors prove trusted hardware|Kernel guidance explicitly says forgeable; inspector does not classify trusted devices|
|18|Deny NEW USB equals revoke existing devices|Read-only report spells out difference; real sandbox inventory UNKNOWN|
|19|setProtect revokes every existing writable map|Explicit Android SharedMemory counterexample prerequisite, not a source-only assertion|
|20|A readonly ByteBuffer proves a sealed cross-UID protocol|Parcel/mapping test is scoped; existing FormatLease wire contract still needs independent integration|
|21|A2.5MB APK beats Tails footprint|Reject lighter-than-Tails claim; total guest+host/workload measurements absent|
|22|Green old12-case run accepts new reveal/memory tests|Exact required-name gate refuses `r23_privacy_api34` for first receipt|
|23|A malformed receipt or truthy Boolean count is success|Mutation tests: wrong candidate/suite, duplicate/missing/fail/skip, structured result, Boolean count/run|
|24|Hundreds of tooling tests equal native/product acceptance|Separate JVM/Python/native ledgers,0/49 full acceptance|
|25|JRE path is an installed JDK/Android SDK|Doctor reports javac absent; lab explicitly refuses host cache for Android|
|26|A rerun may overwrite archived evidence unnoticed|R22 interop default moved to ignored build output; explicit new --report; old receipt restored/retained|
|27|Proxy/Tor badge is complete Major browser anonymity|Tor primary warning; no engine/fingerprint/route acceptance|
|28|Top-level MPL clears a full Office bundle|Exact-version transitive/asset/license/ABI gates remain|
|29|Readonly future mapping excludes a retained malicious-parent writer|Actual old-writer counterexample; only trusted fresh ownership supports the new protocol|
|30|Final directory listing proves no intermediate private input|Live FileObserver sees create/move/close-write with before/after positive controls, plus source path review; tested traces only|
|31|Any failed write-map proves readonly protection|Require failed permission widening AND only EACCES/EPERM; ENOMEM/EINVAL are not a permission proof|
|32|Anonymous parser input means source/export/OS never touches disk|Legacy APIs, provider source, created exports, paging and RAM copies remain explicitly outside that claim|
|33|Official SDK37/Rabbit table implies installed SDK/build success|Actual4a7aa4d pre-test failures; stable NOT_LISTED and old GMD DSL compile errors outweigh a compatibility narrative|
|34|A checksum appearing anywhere is the configured checksum|Canonical wrapper parser rejects duplicate last-key-wins overrides/Unicode-key aliases/extra fields|
|35|Checksum config authenticates every existing cache/tool/dependency|Scope to future distribution download integrity; old caches, mutable actions/transitives and upstream trust remain|
|36|Missing unqualified android-37 implies all SDK37 absent|Actual stable/preview catalogs list37.0; actual metadata says37.0; exact base alias policy plus real installation/compile anchor|
|37|Renderer frame commit/AX node implies screenshot presentation|Actual post-control15/1 failure; concurrent real displayed witness, fresh second RGB and nonempty controls, never poll secure pixels until zero|
|38|Two Views' root-global rectangles share screen coordinates|Actual d5 coordinate14/2 failure; clipped local rect + getLocationOnScreen and bottom-placement/occlusion checks; new4bc16 passes|
|39|All new R23 cases passing erases an old whole-suite failure|Current main121/1 remains FAILED; independent privacy16/memory24 do not promote product acceptance|
|40|Unfixable hostile hardware means useful scoped controls must be rejected|Accept actual no-IME/readonly-memory subsets under stated trust model, without pretending to solve the stronger original target|
|41|A task failure after SDK upgrade proves compiler cause or a rollback fix|Same class of task Add failure existed earlier; capture pre-tap text/focus/bounds/IME/callback/revision, do not infer causality from version order|

**Modal shortcut:** “no IME + secure window ⇒ safe on a hostile laptop.” The host can observe the guest's displayed keypad and pointer coordinates; the app cannot exclude an observer above its authority. The actual AX activation case also demonstrates that the pad is not genuine-touch authentication. `test_actual_first_r23_pass_has_only_its_named_scope` feeds a real green receipt into the evidence gate and rejects host/hardware/global/erasure claims. This test detects an invalid *claim bridge*; it is not a hostile-host penetration test.

## E/F/G — distinct mechanisms, artifacts and strongest structural attackers

|Mechanism / central construction|Concrete artifact and established part|Strongest attacker / cheapest falsifier|Exact unresolved interface / gap type|
|---|---|---|---|
|Whole-stream touch admission|`TouchAdmission.kt`,898,161 aggregate host assertions incl160,000 words; native full/partial discard|Clean DOWN, flagged UP, clean UP into reset view|Platform flag/event fidelity and real overlay app/OEM; local review, not solved by more automaton words|
|Remove text-editor route|`TouchCodeActivity.kt`; actual tap/no InputConnection/hardware-key tests|Ctrl-V/Enter/Delete, AX activation, pointer/screen observer|Digits-only scope, AX/host trust; global anti-keylogging gap is equivalent to original impossible stronger target|
|Bound lifetime/authority|`VolatileDigits.kt`, per-view generation, handler cleanup|cap+1, stale old button, reveal timer after reset, clock reversal, actual61s wait|Framework copies and scheduling/forensics; local behavior testable, erase claim unestablished|
|Window/view capabilities|`GuardedPrivacyActivity`, manifest, sensitive views, old real screenshot marker control|Ordinary screenshot vs positive control; system/AX/privileged overlay|API36/37/OEM/physical/AX matrix; local deployment gate, not universal host security|
|Observe without certifying|`LocalExposure.kt`, `ExposureProbe`, non-exported report Activity|Unavailable signal, enumeration removal, false all-clear on0|No root/implant attestation or continuous enforcement; excluded stronger problem remains|
|Read-only host triage|`inspect_usb_posture.py`,5 parser/IO tests, actual UNKNOWN sysfs observation|Oversized/invalid attribute, >256 buses, no hubs, forged descriptors|Real admin allowlist and recovery, host/DMA authority; local deployment not app capability|
|Anonymous-memory alternative|Actual prerequisite20c1fd9 BEFORE implementation; production `OwnedFormatMemory`/explicit `FormatLease.evaluateMemory*`, isolated worker/UI and3 new methods passed b3f2f1d scoped24 and full122|Retained writer; unprotected memory/size/ambiguous carriers/unsupported op/empty padding; live FileObserver positive controls|Trusted-parent ownership remains essential; observed API34 only, not host/swap secrecy or authenticated child computation|
|Exact evidence/tooling|Pinned hash-checked cache, real lab, `r23_evidence_gate.py`|Old green candidate, wrong suite, missing/failed/skipped/new case, malformed JSON field|Policy is offline metadata checking, not upstream authentication/independent expert acceptance|

All are one author's approaches recorded before their respective implementation, not an invented team. Each returns executable code, actual tests/data, a bounded experiment or explicit model counterexample. No branch is called finished merely because code exists.

## H/I — verification, repairs and reattacks

1. Direct GitHub release binary/checksum downloads failed at disallowed redirect hosts. Switched to explicit pinned npm/PyPI host tools; no fake compiler or platform stubs. Integrity/hashes and actual compiler/runtime versions recorded. JRE≠JDK is enforced and tested; old runtime is not recommended for production.
2. First standalone kernels passed898,161 assertions; initial entire Python suite126passed. New policy tests increased to130/131. Adding a native test caused the intended `Census review stale` error (1error in131 methods), retained as `r23-python-third.txt`; reviewed hashes regenerated and131 methods then passed. No failed check is masked by a subsequent shell command.
3. Running the historical R22 interoperability script wrote its default archived report. Copied the new run under R23, restored the actual old R22 record, changed default to ignored build output and added explicit `--report`. Reran actual327 requests after repair; result/source hash in `r23-r22-interop.json`. Host tests do not infer Android/Excel rendering.
4. Initial native privacy candidate910b739 passed12/12. Additional reveal deadline/stale timer, AX activation and shared-memory tests form a fresh candidate; the old receipt is insufficient for them. Gate unit test expressly rejects first receipt for full R23 claim. No source repair is inferred from unrelated old UI flakiness.
5. Actual20c1fd9 privacy14/14 and main119/119 succeeded; then recorded/committed the prerequisite before production memory implementation. Authored3 new native integration/wire/observer methods and froze24 required names, retaining original FD APIs/gates and no-fallback explicit memory methods. New main source inventory initially refused stale generated review; regenerated after new helper was reviewed. Python136 methods passed at that authored checkpoint; b3f2f1d then passed actual integration24/full122/companion14.
6. Bootstrap was strengthened from version-only PyPI pins to binary-only hash-checked wheels, npm lifecycle scripts disabled and fixed compiler integrity. Actual install rerun succeeded, java/compiler versions rerun. New command-refusal tests attack promotion of JRE-only cache to Android/JDK and absent-SDK fake builds.

## Alternative routes for browser / Office / sandbox / productivity

|Target|Mechanism-distinct practical route, not a substituted product|What it overcomes / what remains|
|---|---|---|
|Major browser|Embed an audited pinned Gecko engine in the ORIGINAL native compositor, separate process, authentic navigation/frame/permission/download/crash/kill bridge; gate with real overlapping-surface/IME/focus/AX and extension tests|Avoids writing a whole HTML engine; does NOT bypass S29/S30, engine licensing/native ABI/16KiB, fingerprint or API37/physical review. No generic WebView/external intent called complete.|
|Browser privacy alternative|Separate maintained privacy-browser compartment for explicitly delegated anonymous browsing; keep its identity/routing/retention threat model distinct|Can provide a useful user-owned workflow without pretending our browser was delivered. Cannot silently replace required first-party browser, claim host secrecy or label arbitrary Tor proxy as Tor Browser.|
|Office bounded original engine|Existing original arithmetic/CSV/literal XLSX/value-only import, explicit loss report, isolated worker, selected-column new-copy readback|Working small interoperable data subset now, not layout/styles/formulas/multisheet full import or original safe-save. Continue explicit format grammar expansion only with maintained producers/readers and hostile packages.|
|Office full-engine alternative|Audited exact-version LOK build or separately signed local companion with versioned, bounded, data-only protocol and no silent cloud conversion|Avoids reinventing full layout; process/APK separation does NOT waive license obligations. Needs exact transitive/fonts/assets/SBOM, ABI/16KiB, memory/lifecycle/fidelity and legal review before enabling. LOK still has no established executable subset.|
|Sandbox less disk exposure|Owned SharedMemory after unmapping writers, future permissions reduced BEFORE handoff, bounded map/copy in isolated child; platform prerequisite first|Could remove named temporary-file input capture; does not imply no swap/RAM erasure/honest computation. Must not bypass existing O_RDONLY FD checks under a new label. Actual substrate prerequisite passed BEFORE implementation; explicit production memory APIs/UI and3 attacks passed b3f2f1d focused24/full122. Legacy regular-file APIs retained; no broad all-parser/OS disk-free claim.|
|Sandbox semantics alternative|Treat every child result as untrusted literal bounded data with no ambient write/network authority; parent independently validates representability and user intent|Reduces impact without requiring a digest to prove honest computation. Does not authenticate correctness; current worker digest/UID correlation remains insufficient for that claim.|
|Productivity|Incrementally integrate existing native Tasks/Notes/editor with explicit snapshots, stale-revision checks, reversible changes and permission-light import/new-copy workflows|Already useful bounded execution paths; not reminders/calendar recurrence/sync/full Office/Meet. Offline local-first increments do not bypass encryption/key/lifecycle/provider/AX gates.|
|Whole-system privacy|Separate live-boot/minimal-OS product or a physically trusted managed device, rather than rebranding an APK as Tails|Moves enforcement to the layer controlling devices/routing/retention. Still trusts firmware/hardware and requires boot/update/measurement review; no total footprint or anonymity superiority claim.|

## J — exact gap and next highest-information tests

Strongest current scoped artifact is4bc7637: actual privacy16 and focused memory24 pass with real SDK37.0/target37;
both builds/JVM/contracts/lint pass. **Whole main121/1 FAILED** the old R18 first task Add. Current all-gates
threshold is not met; no all-module/release/fully validated upgrade claim. No rerun-to-green or skip introduced.
Immediate cheapest falsifier is a one-tap IME-visible task Add trace with BEFORE text/geometry/focus/active-window
generation and AFTER callback/status/disk revision. Root cause is not diagnosed from IME visibility alone.
The buildable SDK37 tuple stays experimental; b3f2f1d SDK36 remains a rollback/reference, not a compiler-causality proof.

Next independent gates: actual API37/Rabbit GUI, malicious external overlay/AX service, physical/OEM/hardware,
real browser-engine/compositor spike and blinded Office fidelity/ABI/transitive-license evaluation. No APK can
certify hostile host/firmware secrecy, erasure of prior external logs or a lighter-than-Tails whole system.
No PR merge/close/release or full specification acceptance was performed.

## Conditional SDK37 experiment, separate from b3f2f1d acceptance

Fresh official Rabbit1/API37/AGP9.1.1 compatibility and temporary legacy Kotlin/DSL support retrieved;
[pre-edit plan](R23-SDK37-PLAN.md) and actual upstream Gradle9.3.1 binary digest retained.
Configured compile/target37, Gradle9.3.1 with distribution SHA, AGP9.1.1, existing Kotlin2.2.21/legacy DSL opt-outs.
**Actual4a7aa4d FAILED:** main38865132612, privacy38865132640, focused38865132614, all before native execution (0PASS/0FAIL/0SKIP, NOT a pass). Runner stable catalog reported SDK37 NOT_LISTED; install failed; actual AGP9 compiler also rejected deprecated managedDevices.devices DSL. Documentation is not installed tools. No unsupported-API suppression, fake SDK or license bypass used. Source test137 passed but did NOT predict Gradle compilation. Failure receipts and build annotations are retained. Restored AGP8.13.2/Gradle8.13/compile-target36 and added a bounded real stable+preview SDK catalog probe; original37 deviation remains. New Gradle8.13 distribution checksum was actually retrieved from the official checksum endpoint, not guessed or copied from a secondary issue. Canonical-property tests reject duplicate last-key-wins overrides. A configured checksum does NOT revalidate an old local cache or authenticate all dependencies. Fresh restored candidate must rerun all its gates.

### Repair checkpoint after failed SDK37 candidate

Working SDK36/AGP8.13.2 restored; no production memory/input source or native test body was weakened.
Real bound catalog tool added for stable+preview channels (non-installing; SDK tool may refresh its cache).
Checksum pin configured from actual official8.13 response;3 duplicate/alias/hash policy tests added.
144 Python methods now pass; fresh restored native workflows remain mandatory. APK byte/hash collection
for BOTH main variants added to the real build, not inferred from compressed artifact-container size.
No code from the rejected SDK37 toolchain is silently accepted; exact receipts remain separate.

## Screenshot readiness failure and stronger verifier (pending fresh candidate)

Restored99c4405 companion38866590115 actually FAILED **13PASS/1FAIL/0SKIP**: old screenshot marker
wait timed out on reopened secure activity, not a pixel-leak assertion. [Pre-edit repair hypothesis](verification/r23-screenshot-repair-plan.md)
was committed before the harness change. Source shows focus callbacks replace injected content, but99 lacks
focus telemetry, so historical causality/general UI stability is NOT claimed solved.
New helper waits actual focus, installs a per-capture nonce, requires real renderer frame commit, and
asserts identical shown/attached/root marker, geometry and secure flag BEFORE AND AFTER actual screenshot.
Secure-first/nonsecure-positive-control/reopened-secure pixel assertions retained. New independent REAL
focusable Dialog invalidates old marker and requires a fresh committed secure frame, with visible controls.
No production discard/secure behavior weakened. **Authored, not yet compiled/run; full companion now15.**
Gate alias `r23_prerequisite_api34` preserves the old14-case substrate scope; current `r23_privacy_api34`
requires15. Actual13/1 receipt rejected even for passed input/memory subsets because whole workflow failed.
All fresh15/24/122 gates remain mandatory. Earlier14-case success is not a repair pass.

Manual exact-branch `gh workflow run android-r22.yml --ref arena/f77cb2a6-osb` was attempted after
b335172 push, but GitHub returned403 Resource not accessible by integration. Git push/read/API
access still worked; no credentials requested or alternative token used. R23 policy/privacy-harness
changes now legitimately trigger the existing memory regression through its normal push path filters,
so verification does not depend on manual-dispatch permission. This is CI coverage configuration,
not a claimed manually dispatched run or a workaround to retrieve restricted artifacts.

## Second SDK37 and workspace-verifier checkpoint

Actual99 main122/focused24 passed; its broad stable+preview SDK probe lists **platforms;android-37.0**.
The missing old `android-37` spelling did NOT establish all API37 SDKs unavailable. Earlier broad diagnosis
was too strong; raw failed install remains a failure. [Second pre-edit plan](R23-SDK37-SECOND-PLAN.md)
precedes new tuple edits: correct37.0 package, AGP9.1.1/Gradle9.3.1, temporary documented legacy bridge,
GMD localDevices named container, actual installed source.properties/jar hash and SDK37 compile anchor.
**Current new SDK37 candidate is not yet compiled/executed.** No renamed36/fake SDK/suppression.

Actual b335172 privacy **14PASS/1FAIL/0SKIP**: stronger screenshot and actual Dialog/frame attack passed,
old workspace assertNotSame after one native click failed. Whole gate FAILED; no historical causality or
fixed stability inferred. [Pre-edit plan](verification/r23-workspace-repair-plan.md) retained before changes.
Shared real-focus readiness and post-click main-idle readback now authored, no click retry, removed
assertion or direct performClick substitute. New real IME-visible single-tap discard test expands current
privacy scope to16. All16/24/122 fresh native gates mandatory. Python147 passed at authored checkpoint;
source validation alone proved insufficient on prior SDK37 and UI candidates.

Actual79bb1c3 second SDK tuple failed at SDK install/inspection on all3 workflows,0 native cases.
No successful inspection notice; source properties/path/size diagnosis not yet visible because raw log
redirect EOF. [Pre-repair inspector diagnostic checkpoint](verification/r23-sdk37-inspector-repair-plan.md)
retained; exception/actual platform metadata now annotated and SDK stderr failure tail preserved.
Only exact base37/37.0 metadata normalization accepted, not higher minors/betas or an inferred build pass.

## Actual SDK37 compile checkpoint and concurrent-display reattack

Actualaceea3a installed official SDK37.0; raw source.properties really says ApiLevel=37.0 (not integer37),
baseSdk=true/PreviewSdkInt0. Jar43,044,472bytes/SHA bf1b4387cc7ca94fc6ef684f040d9d16fbf16248e181819f020736ea2053f177.
Both main builds/JVM/contracts/lint now actually succeeded with9.1.1/9.3.1/target37; focused workflow
succeeded, counts to collect. Native companion15/1 FAILED: compile37 constant/target37, original discard,
new real IME one-tap and all input/memory tests passed; post-focus unprotected screenshot control failed.
No all-black/failed-control pass admitted. [Pre-edit concurrent-witness plan](verification/r23-compositor-witness-plan.md)
adds a real test-only nondimming/nonfocusable small Dialog, unique actual displayed RGB and geometry
leaving>3/4 marker area. Secure pixels fail immediately on first witness-presented frame; positive
controls must actually show marker. Second actual parent commit/fresh witness color and real retired-marker
rejection strengthen the verifier, no fabricated images or production FLAG_SECURE/discard weakening.
Fresh16/24/122 still mandatory; general compositor/OEM/host/all-frame stability unaccepted.

## Final empirical profile note

Automatic79bb1c3 run38869279741 actually succeeded generation+3 macrobenchmark methods,837 owned
rules and nonempty packaged ART assets. Cold/profile-compiled startup medians1619.21/1616.69ms, n=3 each;
Files frameCPU P50≈45.98/P95≈137.55ms. No meaningful speedup or physical-budget acceptance, no profile
asset update into current source; measured837 receipt is separate from older855 data. Full current main
failure remains121/1 even though benchmark workflow on another candidate succeeded.
