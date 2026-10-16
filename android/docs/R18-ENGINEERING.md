# R18 — task editing/archive/query and owned sandbox requests

**Native Android only. Incomplete product.** This pass deepens existing executable subsets; it does not manufacture a LOK implementation or claim LibreOffice-from-scratch parity. See [the preimplementation contract](R18-PLAN.md), [48-row census](MODULE-TESTABILITY.md), and retained `verification/r18-*` receipts. Failures remain evidence, not results erased by retries.

| Measure | Absolute count | Percentage |
|---|---:|---:|
| Modules with a substantial executable/testable subset | **47/48** | **97.92%** |
| Subset not established: LOK | **1/48** | **2.08%** |
| Fully specification-accepted modules | **0/48** | **0%** |
| Modules still unfinished against the full specification | **48/48** | **100%** |

The denominator is included native modules, not requirements or assertion counts. The separate ledger still has **231 requirements, 36 cross-cutting obligations and 30 formal physical/legal spikes unpassed**. PR03, PR07, PR11 and the complete Forge routes are not delivered. Full productivity acceptance remains0/13 and requested Forge acceptance0/13; these feature denominators are not interchangeable with48 Gradle modules.

## A — actual resources, execution and limits

Used: one coding agent; repository/git/gh, Python3, Kotlin2.2.21, JRE17.0.9, remote Android Gradle/managed API34 runs, host cryptography46.0.3/OpenSSL and **Z3 5.3.0**. No parallel independent agents, local Android SDK/device, physical phones, desktop IDE session, counsel or expert AX review. Tool calls ran concurrently where independent; that is not independent review.

The compiler archive integrity was checked; pinned primary files were re-fetched and their Git blob and SHA256 hashes checked. Remote builds use the repository's Gradle toolchain. Available network access was limited to the provided GitHub/package hosts. This is **not** an exhaustive contemporary research frontier search or a reproducible-toolchain/security audit.

Fresh, actually executed host results:

| Check | Result | Scope |
|---|---:|---|
| R18 separate production libraries and public Kotlin consumers | **906 assertions PASS** | Task transitions/query/versioning and owned reply correlation |
| Independent Python/JVM R18 reference | **1,200 cases PASS** |100 versioned wire cases,800 scope/query/order,100 edits,100 archives,100 owned receipts |
| R17 regression | **397 assertions +200 cases PASS** | Current task semantics; old invalid-flag assumption adapted to FTS2, with a separately captured prechange FTS1 fixture |
| R15 regression | **5,510 assertions +700 cases PASS** | Original sheet framing/arithmetic and other prior kernels; includes actual bidirectional Java/OpenSSL AEAD |
| R14 regression | **1,893 assertions +600 cases PASS** | Six prior production kernels |
| R16 regression | **17,131 assertions +64 cases PASS** | Actual loopback and separate Java/OpenSSL processes; not a measured multi-device player |
| Python tooling | **90 methods PASS** | Includes explicitly labeled parser/policy fixtures, not simulated Android functionality |
| Z3 | **6 conditional UNSAT counterexample checks**, each with SAT premises; **1 SAT old-boundary witness** | Integer/Unicode-width models, not Kotlin or Android verification |
| Linux descriptor witness | **Actually reproduced** | An existing writable alias changes data after unlink despite a read-only reader; not an Android exploit of this implementation |

The census also actually refused a stale R17 UI-test hash after the diagnostic edit; the reviewed binding was refreshed, not the validator disabled (`r18-census-refresh.json`).

Source audit, project/census checks and `git diff --check` are separate structural gates. They do not imply Android execution or feature completion. Host receipts bind source and/or executed producer jars; future source changes require rebuilding rather than reusing old results.

## B — objects, access, quantifiers, resources and adjacent problems

**Task object.** An immutable snapshot has0..128 unique canonical UUID records. A title is nonblank, strict Unicode,1..256 UTF16 units, without ISO controls; optional date is exactly years0001..9999, with no timezone, reminder or recurrence meaning. Done/archive flags are independent. The global revision satisfies:

`0 <= revision <= Long.MAX_VALUE - numberOfArchivedTasks`.

Successful edits preserve ID/done/archive and change only title/date. Archived records must be restored before title/date/completion edits. Archive/restore preserves every other field. A no-op preserves the object/revision; the repository avoids writing it. Unknown IDs, invalid data, exhausted revision capacity and stale expected revisions refuse. Archive is not deletion: all archived records still occupy the128-record cap.

**Quantified local target.** For every *admitted* snapshot and existing archived ID, a successful abstract restore increments revision and reduces archive count by one while preserving the reserve. Every prefix of restoring all archived IDs is representable, provided no external rollback/writer or IO failure changes the state. The mathematical model is universally checked within its stated integer assumptions; the Kotlin implementation is only exercised by the recorded finite cases. Neither means every Android execution is proved.

**Wire/storage.** FTS2 uses magic0x46545332, bit0 done and bit1 archive. FTS1 remains read-compatible with flags0..1. Canonical decode checks strict encoding, count, CRC, version, trailing data and exact same-version re-encoding. Valid maximum frame size is104,340bytes, below128KiB. The historical private filename remains `noBackupFilesDir/tasks-v1.bin`; its name is not the wire version. Loading/no-op does not rewrite FTS1; explicit successful mutation writes FTS2. Older apps cannot read it, and the UI discloses this before saving. No downgrade writer/migration backup is claimed.

Storage uses the existing same-process serialization and expected-revision comparison, AtomicFile publication and verified reopen. Plaintext/CRC is neither authenticated nor rollback-protected; no cross-process writer exclusion or power-loss theorem follows. A post-publication error can leave new data present: the UI blocks and asks for reload, never blindly retries or promises rollback. Room remains an unmet full-spec integration obligation.

**Projection.** Query is bounded to256 UTF16 units and uses strict Unicode, Locale.ROOT lowercase literal substring matching, ACTIVE/OPEN/COMPLETED/ARCHIVED scope, and insertion or ascending date/null-last/stable-tie ordering. Querying cannot mutate the snapshot. This is not regex, stemming, locale-aware linguistic search or a date-range query UI.

**Owned arithmetic request.** The caller supplies at most256 cells, encoded to bounded FSC1 (<=256KiB); FSR1 is bounded to64KiB. The caller must not mutate its map concurrently during the defensive copy. Later map/returned-array changes cannot alter the owned frame. The parent retains expected byte count, SHA256 and exact cell-key set, closes the private file writer, opens a read-only descriptor and unlinks before handoff to the existing real isolated worker. The new API is additive; the arbitrary-file API retains its weaker schema-only claim.

The parent rejects structural, metadata and key-set discrepancies. **A compromised worker can still return wrong numbers with matching keys/digest.** Its report is not cryptographic proof of execution. The file path is private to a trusted app owner; this is not a sealed memfd and does not defend against a preexisting writable alias or compromised same-UID owner. Adjacent unsolved problems include complete Office parsing/render/edit/save, full parser compromise containment, authenticated rollback-resistant storage, crash consistency, reminders, keyboard/AX completeness and physical budgets.

## C — H+, H−, boundary and evidence thresholds

- **H+ (bounded):** valid task edits/archive/restore/query and owned-model correlation can be added without silently rewriting legacy data, discarding task fields, or putting expression execution in the parent. Threshold: fresh external-consumer compilation, adversarial host/reference checks, and actual relevant Android cases; a complete native gate is reported only from its executed named outcomes.
- **H− (strong overclaim):** schema-valid replies imply request correlation; a read-only FD plus unlink implies universal immutability; successful archive always implies a possible restore without a revision reserve; passing emulator tests implies complete Tasks/Office. Three concrete boundary witnesses refute these shortcuts. They do not prove the whole product impossible.
- **Boundary:** `revision=MAX-1` with one active task used to allow archive atMAX and then throw on restore. The actual separately compiled Kotlin consumer, old source/jar hashes and stdout are retained in `r18-revision-counterexample.json`.
- **Repair:** all admitted snapshots reserve one revision per archived record. New archive requires room for its increment *and* its future restore; unrelated edits cannot spend existing reserves. This intentionally refuses an archive one step earlier nearMAX rather than accepting an irreversible operation.
- **Patch attacks:** refusal atMAX-1, successful archive atMAX-2 then restore toMAX, attempted unrelated-edit theft, an inadmissible constructed snapshot, and restoration of128 archived records at the exact boundary. These are executed host attacks, plus a new native storage case—not an assertion that native execution passed before it was observed.
- **Evidence rules:** source/build success is not device execution; UI assertions are not a power-cut test; repeated success is not a causal repair; an SMT model is not a compiler/implementation proof. A repaired candidate inherits no native pass from its predecessor.

## D — verified primary imports, hypotheses and open interfaces

Nine distinct pinned primary files were checked across `research/r18-primary.json` and `research/r18-alternatives-primary.json`. These are limited source reviews, not blanket approval of dependencies or APIs.

| Primary source | Imported fact and required hypotheses | Interface left open |
|---|---|---|
| [AOSP AtomicFile](https://github.com/aosp-mirror/platform_frameworks_base/blob/299fe6f5d6fc6f1af7c3411dcf4e5efdf7217368/core/java/android/util/AtomicFile.java) | Caller supplies locking; our repository uses a process-global lock | External writers, filesystem/power-loss behavior and platform-version validation |
| [AOSP WebView](https://github.com/aosp-mirror/platform_frameworks_base/blob/299fe6f5d6fc6f1af7c3411dcf4e5efdf7217368/core/java/android/webkit/WebView.java) | Data-directory suffix belongs to the current process and must be set before WebView/android.webkit initialization | Not a per-tab profile switch or whole browser; actual installed provider/version, render/input/lifecycle still require tests |
| [AOSP SharedMemory](https://github.com/aosp-mirror/platform_frameworks_base/blob/299fe6f5d6fc6f1af7c3411dcf4e5efdf7217368/core/java/android/os/SharedMemory.java) | Protection can restrict future mappings; existing mappings keep their earlier protection | Must remove writable aliases; substituting this API alone would not prove immutable input |
| [LOK C interface](https://github.com/LibreOffice/core/blob/0f91d58472b9a1b3289e070e4c03335d5935b4eb/include/LibreOfficeKit/LibreOfficeKit.h), [LOK README](https://github.com/LibreOffice/core/blob/0f91d58472b9a1b3289e070e4c03335d5935b4eb/libreofficekit/README.md), [Android README](https://github.com/LibreOffice/core/blob/0f91d58472b9a1b3289e070e4c03335d5935b4eb/android/README.md), [MPL text](https://github.com/LibreOffice/core/blob/0f91d58472b9a1b3289e070e4c03335d5935b4eb/COPYING.MPL) | There is an upstream integration interface/license text, not an audited Android payload in this project | Every included/transitive license, ABI/16K, fonts, bounded parsing, safe save and document fidelity; no legal clearance or 1:1 parity |
| [pyca AEAD docs](https://github.com/pyca/cryptography/blob/c0af4dd7b75921bbe9f1d41a03dbd4b64a9e3403/docs/hazmat/primitives/aead.rst) | Authenticated encryption needs correct key/nonce/AAD use; used by actual R15/R16 host regression | Does not establish lifecycle key security or task-file encryption; task data is plaintext |
| [AndroidX BaselineProfileRule](https://github.com/androidx/androidx/blob/f8f5e9c42b562e58bfca57d793d57e9471bc0d50/benchmark/benchmark-macro-junit4/src/main/java/androidx/benchmark/macro/junit4/BaselineProfileRule.kt) | Profile generation interface; pinned source is not misidentified as the resolved1.3.3 binary | Prior855 measured rules are real, but repeated R17 measurements reversed median ordering; no causal speedup/physical budget claim |

No new Office/browser engine or unknown-license runtime dependency was vendored. Host compiler/prover/oracle packages are not shipped in the APK. Main/isolated-module licensing and the user's prohibitions remain unchanged.

## E — false solutions and executable shortcut detectors

`tools/r18_evidence_gate.py` is an executable **closed-vocabulary promotion policy**, with tests in `test_r18_evidence.py`. It accepts only exact-candidate, wholly green, named API34 subsets and refuses unsupported wider claims, missing execution, wrong revisions, duplicate cases, mismatched counts, failures and skips. It does **not** understand arbitrary prose, authenticate a forged receipt, prove a test's correctness or certify product behavior. Real receipts are obtained through the existing gh-backed collector; policy fixtures are labeled fixtures, not device outcomes.

| # | False solution | Detector / concrete attack |
|---:|---|---|
|1|Count folders/compilation as complete modules|`module_testability.py` requires real production/check pairings and exact48-module coverage; full acceptance remains separately0 |
|2|Promote LOK by adding an unrelated helper|LOK row remains unestablished; no audited engine artifact |
|3|Reuse an old candidate's green result after repair|Gate exact candidate comparison; source/jar hashes in host receipts |
|4|Promote a queued/build-only run as native success|Gate requires both conclusions plus named required cases |
|5|Hide a failed/skipped case in aggregate counts|Recompute outcomes; reject duplicates/mismatches/FAIL/SKIP |
|6|Claim exhaustive correctness from finite tests|Gate rejects `all_inputs_exhaustive`; model claims are explicitly conditional |
|7|Claim Z3 proved Kotlin/AtomicFile|Gate rejects `Kotlin_formal_proof`; model receipts retain SMT2, SAT premises and source hashes |
|8|Archive by deleting data or freeing capacity|Field-preservation and128-record/full-archive tests |
|9|Accept an archive that cannot be restored|Actual MAX-1 consumer witness; reserve repair and128-restore attack |
|10|Let another edit steal restore reserve|Executed protected-reserve attack and native persisted-reserve case |
|11|Rewrite FTS1 silently on load/no-op|Captured prechange fixture and actual Android byte-preservation test |
|12|Accept new flag bits in the old format|All256 flags checked for both versions; CRC repaired before malformed-input tests |
|13|Use lossy Unicode/locale-dependent/regex search accidentally|Unpaired-surrogate/control/bound tests, Turkish-default-locale and literal-metacharacter checks |
|14|Mutate the task list while filtering/sorting|Immutable snapshot/revision checks and independent Python projections |
|15|Use a stale edit to overwrite another mutation|Expected-revision native conflict checks; retained input/blocked UI policy |
|16|Treat CRC or reopen as authentication/power-loss durability|Explicit policy rejection; no encryption/authentication/durability claim |
|17|Accept unrelated schema-valid worker keys|Actual old `{B1=999}` witness for requestA1; owned missing/extra/wrong-key attacks |
|18|Call matching metadata arithmetic truth|Executed matching-key/digest wrong-value acceptance witness; gate rejects `arithmetic_truth` |
|19|Call unlink/read-only a seal|Actual Linux writable-alias witness plus SharedMemory primary contract |
|20|Allow caller-map/frame/result mutation to alter ownership|Defensive-copy and unmodifiable-result attacks; concurrent mutation during copy excluded |
|21|Pass UI by replacing touch with semantic onClick or sleeps|Actual `performClick`, visibility/selected-state assertions; no semantic click bypass or timeout increase |
|22|Explain a failure away with a successful rerun|Retain every collected failed native run; diagnosis remains conditional |
|23|Promote a bounded sheet to full Office/browser/AX/legal acceptance|Gate unsupported-claim refusal; separate full requirement ledger |
|24|Lose the failing callsite in truncated logs|Reporting-tool long-stack fixture preserves own frames; test-only actual disk/window diagnostics |

## F — mechanism-distinct branches and genuine alternatives

These branches use different mechanisms and artifacts, not renamed trials. “Established” below is local and conditional; no branch solves the original full product.

| Branch / artifact | Established portion | Unresolved interface and falsifier | Local-to-original gap |
|---|---|---|---|
|1. Immutable state machine — `TaskBook.kt` | Checked revisions, edits, reversible archive state and reserve;906-suite attacks and conditional algebra | Any admitted archive lacking a representable restore, field loss or reserve theft refutes the contract | No scheduling, history, cross-device concurrency or full Tasks |
|2. Versioned canonical binary codec — `TaskFrame` | FTS1 read/FTS2 write, both256-flag spaces, exact104340-byte maximum, independent wire references | Legacy load rewriting bytes, noncanonical acceptance or changed IDs is a falsifier | No authenticated/downgrade/backup migration |
|3. Serialized optimistic snapshot storage — `TaskRepository.kt` | Actual private file reopen, stale revision refusal and no-op preservation | Real concurrent external writer or crash/power-cut inconsistency; same-process scope is explicit | Not Room, multi-process transaction isolation or universal durability |
|4. Pure projection — `TaskQuery.kt` | Independent Python reference, bounded literal search, stable/null-last date order | Locale dependence, query mutation, unstable ties or escaped cap falsifies | Not indexed full-text/date-range/task-provider integration |
|5. Actual Compose interaction — `TaskPanel.kt`, R18/R17 UI tests | Real edit/save/cancel/archive/recreate/query actions exist; native failures are retained | Failed visible tap, selected scope, saved revision or recreation is decisive; gate remains open until observed | Not keyboard/AX/200%-reflow or complete compositor acceptance |
|6. Owned capability/correlation — `OwnedSheetRequest.kt`, `FormatLease.kt` | Immutable bounded request metadata/key set; actual isolated worker tests; no parent expression evaluation | Wrong/missing keys accepted, named input retained, mutable owned frame or writer capability leak | Not arithmetic authenticity, sealed memory, PDF/Office engine containment |
|7. Mathematical reserve model — `prove_r18_models.py` | Six nonvacuous conditional checks; actual old Kotlin boundary witness kept separate | SAT under the stated positive premises, or implementation/model mismatch | Model is not source verification, persistence proof or full product theorem |
|8. Existing remote browser surfaces/profiles — retained real Android tests | Existing separate-profile storage and remote pixels/input/resize cases rerun in R18 | Same-origin storage bleed, wrong surface input or stale lifecycle state | Still debug experiments, not own complete desktop browser/Beam/session stack |

### Alternative designs, tradeoffs and decisive next experiments

**Browser:** retain own compositor and a first-party tab/session model, but put platform WebViews in early-initialized profile processes and embed their remote surfaces. Existing `BrowserProfileDeviceTest` and `BrowserSurfaceDeviceTest` are executable integration artifacts, not a completed browser. The pinned suffix contract rules out pretending that assigning a different suffix per tab in one initialized process is a profile system. Next discriminating experiment: real profile-process death/rebind with two live remote views, input/IME/resize, session restoration and storage-isolation checks. A different engine such as GeckoView is a genuinely different route, but no audited package/transitive set or integration was executed here; MPL isolation/licensing and physical budgets would precede shipping it. A from-scratch standards engine is not replaced by claiming a tab bar is a browser. Neither route supplies own Beam by delegation.

**Office:** (a) audited isolated LOK for broad imported rendering/edit/save, versus (b) original explicitly restricted document/cell models with literal export and truthful fidelity labels. This pass advances (b)'s request boundary, not LOK parity. Its actual arithmetic/schema/correlation artifacts are executable but do not implement Writer/Calc/Impress file compatibility. The LOK branch needs a permitted reproducible Android payload and transitive audit, then malicious-document/ABI/16K and round-trip corpus testing. Pure Kotlin OOXML/ODF work can avoid importing an engine but must implement every missing style/layout/formula/object/save semantic; limited XML extraction is not that engine. Delegate-to-another-app/remote cloud rendering would change the original own/local-first target and is not substituted silently.

**Sandbox transport:** the implemented private unlinked regular-file FD avoids large inline Binder payloads and retains the existing bounded worker reader. It assumes trusted ownership/no surviving writer. Alternative1: Parcelable SharedMemory, carefully unmapping writer aliases and checking protection; the primary contract explicitly warns that old mappings retain their permissions. Alternative2: an actually sealed memfd via a reviewed NDK boundary, with write/grow/shrink seal verification and Android ABI/SELinux/16K experiments; not implemented or claimed available here. Alternative3: bounded copied Binder byte arrays, which avoid shared writable storage but consume transaction buffers and require concurrent-pressure/failure tests. None makes a compromised worker's numeric reply true. Request correlation, process isolation and verifiable computation are separate problems.

**Productivity persistence:** whole canonical snapshots are simple and bounded at128 tasks, but rewrite the snapshot and provide no full undo history. An append-only operation journal would preserve history and separate publication from projection, while creating tail-truncation/replay/compaction/rollback problems. Room/SQLite transactions would match the requested Room direction and improve relational/query evolution, but still need explicit migration, revision-CAS and actual crash/failure testing. Neither alternative is implemented merely by naming it. Inexact reminders would add scheduling, permission and date/time policies; a stored date label is intentionally not represented as reminder delivery.

## G — candidate-specific adversaries and actual failures

1. **First public-consumer compile failed:** nullable external-property smart casting was invalid across module boundaries. Retained diagnostic/source hash; consumer snapshots nullable values locally; fresh separate compilation passed. This was a test compilation repair, not an Android feature success.
2. **Task revision candidate failed a real public-consumer attack:** archive atMAX-1 succeeded but restore overflowed. Fixed the admitted-state invariant, not just the one transition. New attacks include unrelated edits and128 restorations; the old witness remains.
3. **Schema-only worker boundary:** a request containingA1 coexisted with an accepted unrelatedB1 result. This demonstrates missing correlation, not a forged Binder exploit. The additive owned API addresses that interface; a matching-metadata wrong-number test still passes intentionally to delimit the remaining trust assumption.
4. **Actual UI:** old focused runs reached archived selection but did not find the expected task. A full run also timed out at initial save observation. Wrapped scope controls replace nested horizontal scrolling and add actual visibility/selected-state attacks. That is a repair candidate, **not** a causally established diagnosis. Its own focused run failed, including a Compose cross-thread observer error while constructing diagnostics.
5. **Infrastructure:** one reserve candidate never executed cases because Google system-image installation reported a non-ZIP archive. This is0 executed cases, not0 failures meaning success.
6. **Evidence path:** initial diagnostic truncation hid own callsites, and catching `Exception` missed assertion-type failures. Test-only diagnostic capture now records phase, real disk state and actual window/IME geometry without Compose's failing print path; reporter retains relevant frames. These changes do not make the UI pass.

## H — external verification and native ledger

“External” means independent algorithms/runtime boundaries and remote Android execution, **not independent authorship or expert review**. Producer and consumer Kotlin compile separately. Python uses struct/zlib/date/sort/hashlib reference operations; cryptography cases use actual OpenSSL. Android cases exercise real private files, real Binder services/isolated UIDs and actual Compose touch workflows. No mock worker or fake browser is counted.

| Candidate / run | Observed result | Interpretation |
|---|---|---|
|da49491 / [38701998103](https://github.com/jyvplay/OSB/actions/runs/38701998103)|79 PASS,0 FAIL,0 SKIP|Inherited R17 baseline, not R18 acceptance |
|ad5cf80 / [38829684603](https://github.com/jyvplay/OSB/actions/runs/38829684603)|81 PASS,2 FAIL,0 SKIP|R18 initial UI save timeout; Editor diagnostic observer failure; task disk cases passed |
|ad5cf80 focused / [38829684692](https://github.com/jyvplay/OSB/actions/runs/38829684692)|1 PASS,1 FAIL|R17 UI passed; R18 task absent after archived selection |
|3361afe / [38829869927](https://github.com/jyvplay/OSB/actions/runs/38829869927)|84 PASS,1 FAIL,0 SKIP|Both new owned-worker cases passed; R18 UI absent-task failure remained |
|9590ff1 / [38830193992](https://github.com/jyvplay/OSB/actions/runs/38830193992)|0 executed cases; workflow/device FAILURE|System-image installation failure; no reserve-native pass inferred |
|9590ff1 focused / [38830193844](https://github.com/jyvplay/OSB/actions/runs/38830193844)|1 PASS,1 FAIL|Archived-view observation failure remained |
|9340662 focused / [38830281117](https://github.com/jyvplay/OSB/actions/runs/38830281117)|0 PASS,2 FAIL|R17 add timeout; R18 Compose observer error; wrapped controls not a verified repair |
|9340662 full / [38830281113](https://github.com/jyvplay/OSB/actions/runs/38830281113)|**86 PASS,0 FAIL,0 SKIP**|Both builds/JVM/contracts/lint also passed; reserve and owned-worker cases executed. Does not erase the focused failure |
|d6f7393 focused / [38831196487](https://github.com/jyvplay/OSB/actions/runs/38831196487)|**2 PASS,0 FAIL,0 SKIP**|Same production as9340662, diagnostic-only changes; not a causal repair |
|d6f7393 full / [38831196589](https://github.com/jyvplay/OSB/actions/runs/38831196589)|**85 PASS,1 FAIL,0 SKIP**|All seven R18 cases passed; older R17 task-add timed out. Both builds/JVM/contracts/lint passed; whole native gate FAILED |

The latest collected full candidate is **d6f7393:85/86**, not a green native gate. Its actual failure diagnostic reports **disk revision0, tasks=[]**, decor1080x2400, visible frame(0,128)-(1080,1517), IME visible and bottom inset883px. Thus no task was present in the persisted snapshot when inspected. This does **not** distinguish missed/refused submit from delayed work or establish IME occlusion as the cause; target bounds/event timing and UI status were not captured. The earlier86-case full pass establishes one successful run, not stability. Duplicate pull-request-triggered build-only successes do not imply device cases: the workflows intentionally run native jobs on the appropriate event. Reports keep exact run/candidate identity. The earlier R17 timeout remains an independent unresolved stability observation.

## I — repair regression and trust reset

The revision patch rebuilt both production libraries and external consumers; reran906 assertions,1,200 Python/JVM cases, the adapted397+200 R17 checks, and fresh R14/R15/R16 regressions. It added the theft-of-reserve and128-restoration attacks rather than relying on the original one-item case. Six Z3 checks describe only the intended arithmetic invariant.

UI changes add visible-control and selected-label assertions and cycle all four scopes while checking revision4 remains unchanged. No semantic `OnClick` bypass, longer sleep or timeout inflation is used to turn a miss into a pass. Diagnostic-only changes preserve the actions and thresholds. The old failures remain retained; any eventual successful retry is reported separately from a proven causal repair.

`r18-promotion-review.json` records actual policy execution: the9340662 green receipt permits only its named bounded subsets; failed/missing earlier runs and broader claims are refused, as is reuse for d6f7393. The actual d6f7393 focused receipt is insufficient for the whole named subset; its failed full receipt is refused by the green-gate policy despite85 passing cases. `r18-production-lineage.json` confirms no main-source changes between those two commits; tests/diagnostics differ and still require their own result.

The fail-closed evidence policy itself is attacked with wrong candidates, missing/duplicate cases, mismatched/Boolean counts, queued/failed/skipped outcomes, inconsistent run locators and18 inadmissible wider claim labels. Those are tool fixtures, not new app capabilities or native passes. A hash-bound authored census cannot independently decide whether its descriptions are true; review and executed evidence remain necessary.

## J — exact residual gap and next experiment

**Delivered as bounded code:** editable date-only tasks, durable archive/restore state, legacy read compatibility/current encoding, query/scope/date ordering, protected restore revisions, and caller-owned arithmetic request correlation through the real isolated worker. Actual owned-worker tests first passed within a failed native suite; the full9340662 run subsequently passed86/86, including Tasks and the persisted reserve. Host kernels pass; **UI stability/causal diagnosis remains open**. Latest d6f7393 has2/2 focused but85/86 full, with the older add test failing and all seven R18 cases passing.

**Not delivered:** stable/full Tasks or reminders, Room integration, complete own desktop browser and session/Beam stack, full Office/LOK or from-scratch1:1 parity, complete sandbox compromise containment, AX/keyboard/200%-reflow release acceptance, API37/arm64/16K/physical budgets, remaining original providers/protocols/legal gates. Compile36 remains a deviation from requested compile37. No hardware/legal gate is marked passed.

**Next decisive experiment:** retain the now-observed revision0/empty-store failure, capture the actual status text, Add-button bounds and IME/layout/event timing around a real submit, and distinguish missing input delivery/validation from queued or failed persistence. The existing post-timeout geometry is not enough for a causal claim. Repair only the implicated mechanism, then rerun first-add, all scopes, real keyboard/IME and lifecycle regressions at normal and200% font scale. The new R18 workflow and persisted-reserve cases passed in both9340662 and d6f7393, separately from the conditional SMT models; the older UI failure still blocks a claim of a wholly green latest candidate. No blind retry or speculative production change was made after that observation.

Try only disposable test data: Automation → enable for this session → Local tasks. Archive retains data and counts against capacity; FTS2 mutation prevents downgrade reading. If save confirmation fails, reload and inspect rather than assuming rollback. [BUILD.md](BUILD.md) gives the reproducible commands and exact evaluation APK guidance. PR5 exists; no merge is authorized or performed by this pass.
