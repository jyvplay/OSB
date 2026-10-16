# R23 — real native input/privacy hardening, not an anonymous OS

Work is on **`arena/f77cb2a6-osb`**, starting from merged commit7a32437. The prior turn's full free-form input is not available here; the checked-in231-row requirement register,36 cross-cutting obligations,30 spike gates and R22 records are the recovered scope. No original requirement is silently removed. Browser/Office/physical/legal/AX gates remain closed.

## Current evidence (candidate-specific, not inherited)

- **910b739307924f7bedfd52c7d04827cd08badf56**: [privacy run38860744364](https://github.com/jyvplay/OSB/actions/runs/38860744364) **12PASS/0FAIL/0SKIP**, successful compile/lint/R23 Gradle contracts. Includes actual keypad tap, no IME connection, key/shortcut rejection, full/partial dispatch rejection, real focusable Dialog, background/recreation/cap/stale-key attacks and an actual61s idle wait; all6 earlier companion cases also passed. [Receipt](verification/r23-first-privacy-result.json).
- **Same910b739**, [main run38860744226](https://github.com/jyvplay/OSB/actions/runs/38860744226): **119PASS/0FAIL/0SKIP**, both debug builds/JVM/all invoked contracts/lint succeeded. [Receipt](verification/r23-first-native-result.json). This does not include companion cases or the subsequently added memory/reveal/AX assertions.
- **20c1fd92559158bf8780b6c02907e3d95bfd4fa1**: [privacy38862379063](https://github.com/jyvplay/OSB/actions/runs/38862379063) **14PASS/0FAIL/0SKIP** (new reveal/stale timer, trusted AX action and SharedMemory counterexample/2MiB Parcel); [main38862379057](https://github.com/jyvplay/OSB/actions/runs/38862379057) **119PASS/0FAIL/0SKIP**, builds/JVM/contracts/lint success. [Pre-implementation decision](verification/r23-memory-platform-decision.md) was committed before production memory code.
- Following candidate adds explicit `evaluateMemorySheet/Csv/Xlsx`, fresh owned/unmapped/restricted SharedMemory, bounded readonly child, transport correlation, no disk fallback and worksheet UI integration. **Authored, pending fresh compilation/native gate24 and full regression.** Legacy `evaluateOwned*` regular-file APIs remain explicit and separately enforced, not silently relabelled or fallback.
- Actual host compilation: **898,161 new assertions PASS**, **160,000 exhaustive4-event touch words**. R22 regression37 assertions and327 interop requests (271accepted/56refused),15 equal-byte projected-workbook pairs; R21 regression376 assertions PASS. Initial Python126 then130/131 and135/136 methods passed after additions. A generated-hash failure and its rerun are retained below. Final count/outcomes must use the latest exact record, not add overlapping runs together.

### Completion/testability, by the requested substantial-subset standard

| Standard | Absolute | Percent |
|---|---:|---:|
| Included Gradle modules with a real behavioral source/test pairing |48/49|97.96%|
| Not established under that standard (`:format:lok`) |1/49|2.04%|
| Fully specification-accepted modules |0/49|0%|
| Still incomplete against assigned full scope/acceptance |49/49|100%|
| Existing modules deepened in initial verified R23 application change |2/49|4.08% of inventory, NOT acceptance |
| Additional existing modules with authored memory integration, pending native |2/49|4.08%; no new module/completion credit |

Affected initially: `:core:security`, `:privacy`; memory route also changes `:format:pdf` and `:feature:automation` (4/49=8.16% of inventory total touched, not acceptance). Original48-module comparison remains47/48 (97.92%). These are unweighted counts, not percent engineering effort or product completeness. The49 include test/profile modules, not49 interchangeable finished features. No new Gradle module was invented to improve a percentage. [Per-module commands, behavior, limits and hashes](MODULE-TESTABILITY.md).

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
|24|131 tooling tests equal native/product acceptance|Separate JVM/Python/native ledgers,0/49 full acceptance|
|25|JRE path is an installed JDK/Android SDK|Doctor reports javac absent; lab explicitly refuses host cache for Android|
|26|A rerun may overwrite archived evidence unnoticed|R22 interop default moved to ignored build output; explicit new --report; old receipt restored/retained|
|27|Proxy/Tor badge is complete Major browser anonymity|Tor primary warning; no engine/fingerprint/route acceptance|
|28|Top-level MPL clears a full Office bundle|Exact-version transitive/asset/license/ABI gates remain|

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
|Anonymous-memory alternative|Actual platform prerequisite20c1fd9 passed; new `OwnedFormatMemory`/explicit `FormatLease.evaluateMemory*` and isolated worker/UI code +3 native methods authored pending new gate|Retained writer after read restriction; unprotected memory/size/ambiguous carriers/unsupported op/empty padding; live FileObserver positive controls|Trusted parent ownership, cross-UID/FileObserver/UI/FD regression gate24 pending; local integration, not host secrecy|
|Exact evidence/tooling|Pinned hash-checked cache, real lab, `r23_evidence_gate.py`|Old green candidate, wrong suite, missing/failed/skipped/new case, malformed JSON field|Policy is offline metadata checking, not upstream authentication/independent expert acceptance|

All are one author's approaches recorded before their respective implementation, not an invented team. Each returns executable code, actual tests/data, a bounded experiment or explicit model counterexample. No branch is called finished merely because code exists.

## H/I — verification, repairs and reattacks

1. Direct GitHub release binary/checksum downloads failed at disallowed redirect hosts. Switched to explicit pinned npm/PyPI host tools; no fake compiler or platform stubs. Integrity/hashes and actual compiler/runtime versions recorded. JRE≠JDK is enforced and tested; old runtime is not recommended for production.
2. First standalone kernels passed898,161 assertions; initial entire Python suite126passed. New policy tests increased to130/131. Adding a native test caused the intended `Census review stale` error (1error in131 methods), retained as `r23-python-third.txt`; reviewed hashes regenerated and131 methods then passed. No failed check is masked by a subsequent shell command.
3. Running the historical R22 interoperability script wrote its default archived report. Copied the new run under R23, restored the actual old R22 record, changed default to ignored build output and added explicit `--report`. Reran actual327 requests after repair; result/source hash in `r23-r22-interop.json`. Host tests do not infer Android/Excel rendering.
4. Initial native privacy candidate910b739 passed12/12. Additional reveal deadline/stale timer, AX activation and shared-memory tests form a fresh candidate; the old receipt is insufficient for them. Gate unit test expressly rejects first receipt for full R23 claim. No source repair is inferred from unrelated old UI flakiness.
5. Actual20c1fd9 privacy14/14 and main119/119 succeeded; then recorded/committed the prerequisite before production memory implementation. Authored3 new native integration/wire/observer methods and froze24 required names, retaining original FD APIs/gates and no-fallback explicit memory methods. New main source inventory initially refused stale generated review; regenerated after new helper was reviewed. Python136 methods passed; actual Android integration remains pending.
6. Bootstrap was strengthened from version-only PyPI pins to binary-only hash-checked wheels, npm lifecycle scripts disabled and fixed compiler integrity. Actual install rerun succeeded, java/compiler versions rerun. New command-refusal tests attack promotion of JRE-only cache to Android/JDK and absent-SDK fake builds.

## Alternative routes for browser / Office / sandbox / productivity

|Target|Mechanism-distinct practical route, not a substituted product|What it overcomes / what remains|
|---|---|---|
|Major browser|Embed an audited pinned Gecko engine in the ORIGINAL native compositor, separate process, authentic navigation/frame/permission/download/crash/kill bridge; gate with real overlapping-surface/IME/focus/AX and extension tests|Avoids writing a whole HTML engine; does NOT bypass S29/S30, engine licensing/native ABI/16KiB, fingerprint or API37/physical review. No generic WebView/external intent called complete.|
|Browser privacy alternative|Separate maintained privacy-browser compartment for explicitly delegated anonymous browsing; keep its identity/routing/retention threat model distinct|Can provide a useful user-owned workflow without pretending our browser was delivered. Cannot silently replace required first-party browser, claim host secrecy or label arbitrary Tor proxy as Tor Browser.|
|Office bounded original engine|Existing original arithmetic/CSV/literal XLSX/value-only import, explicit loss report, isolated worker, selected-column new-copy readback|Working small interoperable data subset now, not layout/styles/formulas/multisheet full import or original safe-save. Continue explicit format grammar expansion only with maintained producers/readers and hostile packages.|
|Office full-engine alternative|Audited exact-version LOK build or separately signed local companion with versioned, bounded, data-only protocol and no silent cloud conversion|Avoids reinventing full layout; process/APK separation does NOT waive license obligations. Needs exact transitive/fonts/assets/SBOM, ABI/16KiB, memory/lifecycle/fidelity and legal review before enabling. LOK still has no established executable subset.|
|Sandbox less disk exposure|Owned SharedMemory after unmapping writers, future permissions reduced BEFORE handoff, bounded map/copy in isolated child; platform prerequisite first|Could remove named temporary-file input capture; does not imply no swap/RAM erasure/honest computation. Must not bypass existing O_RDONLY FD checks under a new label. Actual substrate prerequisite passed; explicit production memory APIs/UI and3 attacks are now authored, awaiting new native result. Legacy regular-file APIs retained.|
|Sandbox semantics alternative|Treat every child result as untrusted literal bounded data with no ambient write/network authority; parent independently validates representability and user intent|Reduces impact without requiring a digest to prove honest computation. Does not authenticate correctness; current worker digest/UID correlation remains insufficient for that claim.|
|Productivity|Incrementally integrate existing native Tasks/Notes/editor with explicit snapshots, stale-revision checks, reversible changes and permission-light import/new-copy workflows|Already useful bounded execution paths; not reminders/calendar recurrence/sync/full Office/Meet. Offline local-first increments do not bypass encryption/key/lifecycle/provider/AX gates.|
|Whole-system privacy|Separate live-boot/minimal-OS product or a physically trusted managed device, rather than rebranding an APK as Tails|Moves enforcement to the layer controlling devices/routing/retention. Still trusts firmware/hardware and requires boot/update/measurement review; no total footprint or anonymity superiority claim.|

## J — exact gap and next highest-information tests

Strongest admitted artifact is the14-case actual Android companion path at20c1fd9, its119-case main regression and real host regressions, not an anonymous system. Prerequisite now passed and recorded BEFORE production code; next candidate must pass the24 required native distinct-UID memory/observer/wire/FD/CSV-XLSX UI names plus full native regression before the new explicit no-fallback memory APIs/worksheet UI path can be accepted even narrowly. Independently, run a real malicious-overlay test app, TalkBack/Switch/Voice/200%/API36/37 and physical laptop/peripheral/DMA evaluation. A blinded malformed Office corpus and real engine/compositor spike have more information than adding another broad “complete” label.

No claim all original pieces or all hardware weaknesses are closed. No release acceptance, branch switch, merge or closing operation is authorized/performed.
