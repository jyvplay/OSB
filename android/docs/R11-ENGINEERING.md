# R11 — bounded implementation, actual Android failures, and viable next routes

**The full original Android specification / production beta is NOT delivered.**
There is substantial missing implementation, not simply a test backlog. This pass
adds executable Android/Kotlin work, not a TypeScript duplicate or a simulated UI.

## Numerical status

- Included modules: **0/48 accepted (0%); 48/48 outstanding (100%)**.
- Requested Productivity: **0/13 accepted (0%)**; Forge: **0/13 (0%)**.
- Browser a–s: **0/19 accepted (0%)**, nested within PR-07, not extra modules.
- Recorded scope:231 requirement rows,36 cross-cutting obligations;171
  `not_implemented`,56 `partial_unverified`,3 excluded,1 superseded;30 unpassed spikes.
- Host execution: **182,829 assertions**,13 separately compiled Kotlin libraries;
  **61 Python test methods**; **323 additional cross-language checks**. Counts are
  case coverage, not percentages of feature completeness or universal proofs.
- **Native run38662585401 @1ee1589:24/24 API34 cases PASS (100% of these
  cases;0 failures/0 skipped), including all five FD/isolation cases.** Both debug
  flavors assemble; invoked unit tests, contract suites, lint and APK upload pass.
  [Executed run/artifacts](https://github.com/jyvplay/OSB/actions/runs/38662585401).
  This test percentage is explicitly not a feature-completion percentage.
- New browser metadata contracts:5,202 assertions. New Notes link contracts:429.
  A codec, a graph, and a service boundary do not complete their enclosing features.

## A. Actual tools, objects, access and success model

Used this pass: repository tools, Bash, Git/`gh`, Python standard library,
Kotlin2.2.21 compiler, Temurin17 runtime, real temporary files/OS locks, GitHub APIs,
and GitHub Actions' actual Gradle/Android toolchain and API34 managed emulator.
No local full JDK/SDK/ADB was established. No physical phone, Apple receiver,
independent agent, theorem prover, counsel, or human AX reviewer participated.
The Python oracle is separately implemented but authored in the same session;
it is not an independent expert audit. No new production third-party dependency
or native engine payload was introduced. Existing older TypeScript was untouched.

Whole-product success remains the conjunction of complete requested behavior,
own native compositor integration, build/runtime correctness, legal/dependency
closure, required process boundaries/spikes, device/performance and keyboard/AX
acceptance. A finite test only establishes the executed cases on its recorded
runtime. Compile success does not quantify over every input or platform.

### Objects and budgets actually implemented

**BrowserSessions / BrowserSessionCodec** operate on immutable metadata, not live
pages:≤64 tabs,8 windows,16 groups; positive session-monotonic IDs; pinned prefix,
contiguous groups, active/split membership, bounded collapse/color/name metadata,
close/reorder/group/detach/merge operations and literal search. Active, pinned and
split-visible tabs cannot request sleep. Split divider is200–800 permille. Only
explicit HTTP(S)/about:blank navigation is admitted; credentials, invalid hosts,
malformed Unicode and unsupported schemes are refused. Raw escapes are preserved.
URL4096 UTF-8 bytes/title512/group80/search256/session-name128. HTTP admission is
not TLS/origin permission authorization, SSRF prevention or a network policy.

Schema1 codec:≤512KiB **payload**, plus36 framing/checksum bytes; bounded counts,
strict boolean admission, full-state invariants, checksum and trailing-data refusal.
The SHA-256 checksum is not encryption/authentication. Actual temporary-file
roundtrip is tested; no product disk repository, privacy policy, runtime restore,
renderer driver, browser UI, cookie jar, permission store or profile exists here.
Live callbacks will need a runtime epoch in addition to session IDs before restore
can be connected safely. `sleepRequested` does not reclaim renderer memory.

**NoteLinks / NoteFiles.links()** scan saved revisions while holding the existing
cooperative OS writer lock. Reads are lazy, one bounded note at a time. Limits:
256 notes,256KiB strict UTF-8 per note,8MiB accepted aggregate graph input,
4096 recognized reference occurrences; excess refuses the graph, not partial
publication. A refusal can read the next bounded note before detecting aggregate
excess; this is not an8MiB process-RSS ceiling. SHA-256 binds supplied text to its
revision. Published graph/maps/sets are immutable. Self references are excluded
from incoming backlinks; missing targets are reported. Native UI explicitly scans
on IO, clears prior graph on scan/successful save, and reuses dirty-draft confirmation
for navigation. External changes need a new scan. Unresolved display is explicitly
limited to first32, without truncating saved text or the underlying graph.

Grammar is exact case-sensitive `[[note-name]]` with valid NoteFiles names;
backslash-escaped opens, single-line backtick spans and standalone0–3-space fenced
blocks are excluded. Not CommonMark: aliases, ordinary Markdown destination links,
HTML, indented code and multiline inline-code interpretation are unsupported.
No attachments, OCR, encryption, recovery journal or complete Markdown preview was
added. The app-private/cooperating-writer assumption does not defend against an
arbitrary same-UID attacker rewriting files outside the lock.

**FormatLease / FormatWorkerService** pass one regular/read-only/offset-zero FD
into a uniquely named isolated-service instance; opaque SHA-256 only.16MiB actual
read limit,12s bind wait,8s result wait, requested self-kill5s after service creation.
Death tests allow15s observation (up to20s construction-to-death budget). No hard
real-time bound is claimed. The same-process watchdog is not robust to arbitrary
native compromise. A read-only FD restricts the worker, not another writer of the
same inode: immutable broker-owned input snapshots remain an engine-adapter obligation. No parser, document output, renderer, Office engine or parent
OS-enforced CPU/RSS limit is implemented by this service.

## B. Hypotheses, thresholds and boundaries

- **H+ (bounded usefulness):** immutable metadata, saved-revision graphs and
  least-authority FD workers can be useful building blocks. Their actual tests
  support only their declared finite domains and runtime observations.
- **H− (current acceptance):** the complete beta fails acceptance: key engines,
  platform37, physical budgets and whole-feature behavior are absent. This is
  evidence of present incompleteness, not a theorem that the product is impossible.
- **Boundary:** reject unsafe/over-budget input and preserve prior state; do not
  return fake success, silently downgrade formats, load restored URLs automatically,
  enable gated modules or equate a copied/extracted representation with fidelity.
- Promote a host mechanism only after real production-code tests. Promote a native
  observation only after named device-case results. Promote an engine only after
  build/transitive license, malformed corpus, isolation/death and fidelity spikes.
  Full product acceptance requires the stronger conjunction above.

## C. Retrieved primary results and what remains open

`research/r11-frontier.json` and `research/r11-alternatives.json` retain repository,
pinned commit, Git blob identity (verified against downloaded bytes), SHA-256,
paths and selected excerpts. These are source retrievals, not package certification.

1. AOSP Android14 `Context.bindIsolatedService`: instance naming/shared-process
   contract. Main SDK method and OS runtime observations are separate evidence.
2. AOSP `Handler.MessengerImpl.send`: Android stamps `sendingUid` from Binder;
   this supports broker reply-origin checks, not trust in arbitrary reply contents.
3. LibreOffice `COPYING.MPL` and `LibreOfficeKit.h`: MPL2 text and concrete
   documentLoad/saveAs/paintTile ABI. Root `COPYING` alone was insufficient and
   includes GPL text; the extra retrieval corrects that research limitation, not
   the entire payload's license uncertainty. Every shipped component still needs
   SPDX/SBOM/notice/source-offer and linking review.
4. Apache POI LICENSE/XSSFWorkbook source: Apache2 and actual workbook APIs.
   Retrieved `SheetUtil` imports `java.awt`/FontRenderContext: a concrete Android
   porting surface, not proof every read/write path requires AWT or is compatible.
5. AndroidX `ProfileStore`: `MULTI_PROFILE` is feature-detected; API level alone
   is insufficient. AndroidX WebKit is not currently a browser dependency here.
6. AOSP `SurfaceControlViewHost`: public embedding/setView/SurfacePackage API
   and accessibility plumbing exist. It does not establish our remote WebView's
   input/IME/AX/security/lifecycle correctness. Hidden constructors are not proposed.
7. GeckoRuntime/GeckoRuntimeSettings carry MPL2 and an embedding/runtime interface.
   This pinned GitHub mirror is source research, not a current-release or supported
   profile-isolation/Android16K compatibility certification.
8. AOSP `PdfRenderer`: seekable descriptor ownership and explicit recommendation
   to render untrusted files in a separate minimally privileged isolated process.
9. PdfBox-Android `LICENSE.txt`: Apache2 root license retrieved after correcting
   an initial `LICENSE`404. No dependency closure or integration was performed.

10. AOSP Zygote native app-data isolation mounts tmpfs over data roots and bind-mounts
    selected packages. This is a plausible mechanism for observed ENOENT; it is not
    a captured mount-namespace trace proving that exact cause in our emulator.

No current-literature exhaustiveness, vendor endorsement, private result or legal
opinion is claimed. Imported API existence does not close any original spike.

## D. Mechanism-distinct portfolio and early independence

These are independent mechanisms, not six labels on one reducer. Portfolio rows
state the artifact actually available and a candidate-specific falsifier. The
unimplemented engines below are not counted as implemented portfolio artifacts.

| Mechanism / artifact | Established part | Residual local obligation / falsifier | Gap to original product |
|---|---|---|---|
| OS capability boundary: FormatLease/Worker + five instrumented cases | Real Binder/FD/service code, not a mock parser | Wrong UID, private-file read, writable/pipe admission, missing death or cross-job reply defeats candidate; CPU/RSS/native compromise still open | Complete isolated Forge/parser pipeline and FF12 watchdog |
| Immutable browser reducer + binary admission codec |5,202 checks: bounded membership/order, alias resistance, corruption/forged valid-checksum schema refusal, actual file roundtrip | Stale live callback after restored IDs; renderer/disc repository absent | Full desktop browser a–s, not generic WebView |
| Revision-bound sparse note graph + locked file scan |429 checks, actual saved files/lock refusal/immutable old snapshot | Non-cooperating writer, unsupported Markdown grammar, stale graph after external change | Full PR02 attachments/OCR/export/recovery |
| Strict transcoding + exact bounded line DP | Existing R10 editor production contracts rerun | Unmappable output, line-boundary changes or stale source can invalidate a save; budgets explicit | Streaming large files, regex, full native lifecycle |
| Correlated publication/recovery state machine | Actual checkpoint/readback and historical-schema contracts | Old picker response or receipt for another destination must never publish current success | Full SAF/provider crash matrix and journaled file moves |
| Authenticated encrypted snapshot store | Real JCE/file/lock contracts rerun | Wrong key/scope/tamper must refuse; rollback, key lifecycle and live discovery remain | Encrypted Find/FTS, exclusions and S4 |
| Counted streaming archive/text extraction | OOXML consumed-byte guards and malformed/bomb cases rerun | Unknown/ignored ZIP members evade accounting; lossy text confused with a renderer | Complete Office/POI/fidelity route |
| Non-consuming compositor press observation | Replaces failing parent click-merge; preserves explicit AX focus action and keyboard focus | Native distinct-pane controls and input regression; human TalkBack/200% still required | Complete H1 external display/input/AX release gate |

Mechanism independence was kept early: browser model has no renderer dependency;
Notes uses the existing store rather than creating a browser dependency; the FD
worker does not require Office and cannot claim Office; codec/graph tests are
compiled against separate production libraries. No sixfold completion credit.

## E. Alternative engineering routes — viable candidates, not shipped solutions

| Constraint / route | Why worth testing; concrete interface/artifact | Next decisive test and tradeoff |
|---|---|---|
| Browser A: OS WebView renderer, own desktop chrome, feature-detected profiles | Existing OS renderer plus ProfileStore/MULTI_PROFILE; metadata kernel can drive tabs/groups/splits without becoming the renderer | S29/30 feature/provider matrix, cookies/cache/storage separation, permissions/redirects/downloads, renderer death; unsupported profile capability must remain unavailable |
| Browser B: dedicated host/profile process with SurfaceControlViewHost embedding in own compositor | Public SurfacePackage/SurfaceView path offers interactive remote surfaces without stock desktop delegation | Actual two-window remote surface, keyboard/IME/AX, resize, crashes and focus; same-app process name is **not** UID isolation. Separate permission-scoped APK/UID or a proven broker boundary needed for stronger host isolation |
| Browser C: GeckoView MPL island | Real embedding/runtime source; alternative engine and extension surface | Pinned supported artifact, full transitive license/16K/ABI audit, runtime/profile topology, input/AX and memory spike. Larger footprint; MPL files stay outside permissive main code. Not currently vendored |
| Office A: isolated LibreOfficeKit native worker | Verified MPL header and documentLoad/paintTile/saveAs ABI; current generic FD worker is a precursor, not adapter | S10 Android arm64/4K+16K build with audited payload; ten-format corpus, macros disabled, fonts/roundtrip fidelity, kill/OOM/output validation; expensive but targets real editing/rendering |
| Office B: POI headless subset in bounded worker | Apache2 APIs and actual AWT compatibility surface retrieved | S11 Android dependency/class-loading spike for selected read/write/extract paths, XML/ZIP limits and output fidelity; no visual-editing or universal conversion substitution |
| Office C: own strict extractor + explicit retained original | Existing bounded OOXML extractor can expose low-fidelity text safely when gated appropriately | Isolation/grammar/bomb tests and clear fidelity label; cannot satisfy FF01/FF02 or overwrite original as equivalent Office document |
| PDF A: platform PdfRenderer in isolated service + own annotation model | AOSP recommends isolated process; FD worker can precede seekable-PDF integration | Decoder crash/bomb/page/pixel/memory bounds and clean death; annotation appearance/export/signature semantics still need code. No<100ms claim without measurement |
| PDF B: audited PdfBox-Android export backend | Apache2 root license and Android-specific project; possible separate PDF manipulation route | Transitive closure, hostile PDF corpus, font/annotation/signature fidelity and safe publication. No imported engine or legal clearance yet |
| Productivity: application-owned stores, system-provider adapters only where mandated | Existing Notes/Editor/Calc stores; Contacts/Calendar APIs should remain permission-scoped adapters, not wholesale private database copies | API34/36/37 permission/revocation/read-only picker matrix; task/reminder/calendar/contact features remain absent |
| Sandbox fallback: separate minimal-permission APK UID, capability FD interface | Stronger separation than same-UID :process; Android Binder/FD building blocks exist | Caller identity/auth, one-use capabilities, broker outputs, process teardown and install/update/package policy. Networked browser host and no-network document worker require different policies; not one magic manifest flag |

A critical Office constraint is still unresolved: LibreOfficeKit may need writable
profile/temp/font-cache paths, while this isolated-UID worker currently has only
an explicit read-only input FD and no usable app-private pathname. It is **not** a
drop-in LOK host. S10 must measure an FD/virtual-filesystem or bounded-memory port,
or a separate minimal-permission engine APK with controlled scratch storage. The
latter improves main-app separation but does not automatically isolate documents
from each other or provide a parent-enforced kill deadline. Neither is approved
without a real build, storage/teardown experiment and hostile corpus.

Choose only after the decisive spikes. No external Office app, cloud converter,
stock desktop, system cast button, app-UID pseudo-PTY or GPL/AGPL library is counted
as fulfilling a prohibited substitution. Unknown licenses stay blocked. A verified
license *file* is not a verified transitive payload. MPL separation is an engineering
policy, not a claim that IPC alone settles legal obligations.

## F. Candidate-specific attackers / false-solution detection

| # | False solution or attacker | Detecting test / required observation |
|---|---|---|
|1|A same-UID `:process` called isolation|Observed UID≠app UID, unavailable exact private pathname plus positive FD controls and denied INTERNET; named device cases|
|2|Self-reported UID in a Bundle trusted|Compare OS-stamped Messenger sender UID and process observations|
|3|A writable FD treated as read-only because API says read|F_GETFL/type/offset guard and actual writable/pipe/nonzero tests|
|4|Two jobs share one mutable worker|Concurrent fresh instances with distinct PID/UID and digests|
|5|Watchdog source constant called a hard deadline|Actual Binder death; subsequent native-saturation/parent-enforcement attack still required|
|6|Opaque hash service called an isolated Office engine|Supply real docx render/edit/save corpus; currently unsupported, fails acceptance|
|7|Eight UI failures hidden by unmerged-tree selectors|Keep production pane semantics; regression queries normal merged tree; do not suppress AX|
|8|Parent touch focus consumes child gestures|Press observer never consumes; native text/edit/control and multi-window regressions; drag/IME/human AX still required|
|9|Tab metadata or sleep flag called browser functionality|Live independent two-page navigation/memory/callback tests; currently no renderer integration|
|10|Restored IDs authorize a stale renderer callback|Runtime epoch/navigation-token attack; not closed by current metadata monotonicity|
|11|URL parser treated as TLS/SSRF/permission policy|Redirect/DNS/origin/late-permission tests at real renderer boundary; currently absent|
|12|Checksum treated as authenticated private session storage|Attacker recomputes valid checksum with invalid schema (tested); confidentiality/authenticity require separate policy|
|13|Group ordering silently aliases mutable caller lists|Mutation attempts and invariant checks against published collections|
|14|Only aggregate device test total reported as isolation pass|Publish exact JUnit class.method outcomes; missing XML explicitly means no execution evidence|
|15|Saved note graph presented as live draft graph|Saved-file tests, revision binding, successful-save invalidation; explicit saved-only UI label|
|16|Wiki regex called full CommonMark/backlinks|Code/escape/fence/invalid-name fixtures; unsupported aliases/HTML/indented code remain disclosed|
|17|Graph includes partial results after size/link budget breach|Exact8MiB boundary and4097-reference refusal; build publishes only after complete success|
|18|Backlink navigation silently discards dirty draft|Reuse existing NoteAction confirmation; native draft recreation/discard tests; backlink-specific dirty navigation remains a next test|
|19|A GPL root COPYING or MPL header treated as whole LO license closure|Payload-level SBOM and dependency/link audit; gate remains blocked|
|20|POI Java compilation implies Android/AWT support|Selected-path real Android class-loading and corpus spike; source import is counterevidence to assumption|
|21|PDF preview/direct output confused with safe annotated/signature export|Reopen/validate retained-original export, signature/appearance/fidelity tests; absent|
|22|Green compilation or benchmark module presence means accepted beta|Module gate conjunction, named runtime results and two-device measurements;0/48 remains|

## G. Repairs inherit no trust

First instrumented attempt failed compilation: ShellSmokeTest needed scoped
ExperimentalTestApi opt-in and two stale Open-text selectors were repaired in
d6d6d20. The next run38659138584 **executed22 tests with8 failures**. Captured
runtime failure: `IllegalStateException: merge function called on unmergeable
property PaneTitle`. Same run's build/unit/contracts/lint job succeeded; PR run
38659146516 also succeeded, demonstrating why PR-green alone was insufficient.

Compositor parent `.clickable` merged descendant feature pane semantics. ea9766d
replaces it with Initial-pass press observation without consuming child events;
keyboard focus and explicit custom Focus action remain. Added a distinct-two-pane
regression and actual Notes-store-to-backlink-UI test. No test was deleted or AX
semantics hidden to avoid the exception. Case-level CI reporter now runs even on
test failure; missing/conflicting results are not interpreted as success. Its parser
fixtures exercise reporting logic only, never fabricate device observations.

**Native run38660636656 at ea9766d:** both flavors assemble; invoked unit tests,
contracts, lint and APK upload pass. API34 **24 executed:22 PASS,2 FAIL,0 skipped**.
All editor smoke cases, two-pane semantics regression, both Notes UI cases,
checkpoint/copy correlation, Keystore-loss and storage-boundary cases passed.
Four isolation cases passed; `isolatedUidCannotReadParentPrivateFileOrAcquireInternetPermission`
failed. Do not infer its individual internal assertions passed. Palette Enter failed
with `FocusRequester is not initialized`.

Repair5a49eb0 moves palette focus into the dialog's content composition and uses
native Os.open errno rather than Java IOException-cause matching for the probe.
The EACCES assertion was not weakened. The reporter now includes bounded actual
JUnit failure details. Run38661669902 at5a49eb0 then passed build/unit/contracts/lint and **23/24** native
cases. Palette now passed. The sole failure was exact: expected EACCES13, actual
ENOENT2. This refutes the original errno-specific hypothesis; it does not alone
establish either a sandbox escape or successful isolation.

Candidate1ee1589 therefore revises the *observable property*, not the product gate:
private pathname cannot be opened, while an explicitly delegated FD to that same
existing file can be read. The test now requires exact path equality, parent/worker
stat device+inode identity, correct binary digest, unchanged parent inode/content,
different UID/PID and denied INTERNET. Only EACCES denial or ENOENT name invisibility
is admitted; open success/other errno fails. These controls defeat the adjacent
false solution “probe an arbitrary nonexistent path.” The failed strict-EACCES
history is retained, the case is accurately renamed, and fresh execution is required.
**Fresh run38662585401 at1ee1589 PASSED:**24/24 named cases,0 failures/0 skipped,
including all five boundary cases; both-flavor build, invoked unit/contracts/lint
and debug APK upload also passed. The controlled exact-path/FD property passed,
not the refuted universal-EACCES property. This is still not full parser/security
or physical-device acceptance. `r11-device-final-annotations.json`, `r11-ci-jobs.json`
and `r11-native-results.json` preserve exact named/runtime evidence. Artifact
metadata is retained in `r11-ci-artifacts.json`; uploaded debug APKs were not
locally downloaded, hashed or manually installed in this sandbox.
See `verification/r11-device-first-annotations.json`, `r11-device-second-annotations.json`
and `r11-device-fourth-annotations.json`; structured named history is in
`verification/r11-native-results.json`.

## H. External/runtime verification and reproducibility

`verification/r11-jvm-contracts.txt`:182,829 assertions,13 production library
boundaries and two intentionally rejected cross-module smart-cast consumers.
`r11-python-checks.txt`:61 methods. `r11-independent-checks.json`:323 checks
including300 independent regex/hashlib Notes cases and an independent Python
struct/modified-UTF/SHA-256 decoder of a real Kotlin session file (including NUL
and supplementary Unicode). This oracle covers its narrow flat wiki grammar,
not the whole scanner or browser behavior. Historical R10 independent results
remain historical, not relabeled as newly executed R11 results.

From `android/`:

```sh
python3 tools/check_project.py
python3 -m unittest discover -s tools -p 'test_*.py' -v
JAVA_HOME=/actual/java17 KOTLINC=/actual/kotlinc python3 tools/run_jvm_contracts.py
JAVA_HOME=/actual/java17 KOTLINC=/actual/kotlinc python3 tools/run_r11_interop.py
# Real JDK/SDK and dependencies required below:
./gradlew :app:assembleProDebug :app:assemblePlayDebug
./gradlew :testing:browserSessionContracts :testing:noteLinkContracts
./gradlew :app:lintProDebug :app:lintPlayDebug
# Only on a disposable, explicitly opted-in test device:
./gradlew :app:pixel6Api34ProDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.fusionDisposable=true \
  -Pandroid.testInstrumentationRunnerArguments.notClass=dev.fusion.shell.VisualCaptureTest
python3 tools/report_device_results.py
```

GitHub runner/emulator is an external execution environment, not independent
source review. No physical-device, OEM, API36/37 runtime, arm64/16K, latency,
TalkBack/Switch/Voice or200%-reflow acceptance follows from it. Screenshots were
not inspected here; VisualCaptureTest is intentionally excluded from this CI run.
Do not run test-owned file mutation/consent fixtures on personal user data.

## I. Feature/module testability — executable subset versus absence

All48 modules are individually enumerated in [MODULE-COMPLETION](MODULE-COMPLETION.md).
Every module remains0/1 accepted (0%). This is not an estimate of developer effort.
A module can have useful executable production logic and still lack most assigned
features; conversely a compilable screen or wrapper can contain no requested engine.

| Productivity parent | Substantial executable implementation / present evidence | Absent or materially incomplete; next decisive test |
|---|---|---|
|PR01 Editor|Bounded8-draft encoding-preserving editor, undo/literal tools, copy/recovery and line diff; host contracts/native smoke candidates|Streaming large files, regex, autosave/recovery semantics, full picker/process-death/IME/AX/performance matrix|
|PR02 Notes|Real revision-checked local Markdown file store + saved-note backlink graph and native scan UI|Attachments/OCR/import-export/preview/versioning/reversible delete; dirty-backlink navigation/native lifecycle/AX|
|PR03 Tasks|No substantial task/reminder engine|Actual local schema, migrations, recurrence and inexact reminder lifecycle|
|PR04 Calendar|No complete CalendarContract feature|Permission-scoped provider adapter and revocation/query/edit tests|
|PR05 Contacts|No complete ContactsContract/API37 picker feature|Verified API37 picker contract and API34/36/37 permission matrix|
|PR06 Mail|No implemented triage/native IMAP system|Explicit handoff/triage first, IMAP only P4; no fake inbox|
|PR07 Browser|New immutable session kernel/codec only; old BrowserScreen remains blocked, not connected|Desktop tab/sidebar/group/split UI, renderer/profile/permissions/download/reader/filter/shortcut/drag/cast integrations|
|PR08 PDF/image|Existing preview/edit source, gated; generic isolated FD spike is not a renderer|Safe isolated rendering, measured warm<100ms, annotate/sign/export/fidelity|
|PR09 Calculator|Decimal/scientific/programmer/units/history production kernels and Compose source;2,056 host checks|Full keyboard/AX/200% and native persistence/performance acceptance|
|PR10 Clock|No complete clock/timer/inexact-alarm product|Lifecycle/notification/Doze/inexact-delivery behavior; no exact-alarm substitution|
|PR11 Office|Gate/routing source and lossy OOXML extraction, not Office|Real native engine S10/S11 and requested ten formats/open/render/edit/save|
|PR12 Clipboard/IME|No complete optional IME/global clipboard|Own-module history + sensitivity/expiry/remaps; opt-in IME scope/permission tests|
|PR13 Sticky notes|No substantive sticky-note feature|Native window/store/lifecycle/keyboard/AX implementation|

For PR07, existing metadata covers only a subset of domain-level concerns
(tabs/sidebar ordering and search, groups, split metadata, pinned/sleep requests,
own-window associations, some batch close/group operations and named snapshots).
It implements **none of the19 full subfeatures**. No bookmark, live profiles,
actual split WebViews, reader, permission broker, downloader, desktop UA automation,
filter or Beam URL-send pipeline is smuggled into that count.

| Forge parent | Present executable subset | Absent / next decisive test |
|---|---|---|
|FF01 LibreOfficeKit|MPL/ABI research + non-engine isolated FD precursor|Actual audited native payload and ten-format fidelity/edit/save S10|
|FF02 POI|Own bounded OOXML text extractor, not POI|POI dependency/Android S11 and headless read/write/convert/extract|
|FF03 PDF|Platform preview source|Isolated parser, annotate/sign/export/safe-save/fidelity|
|FF04 AI|No complete route|PDF-compatible AI identification via validated PDF route; non-PDF AI clearly limited|
|FF05 Images|Platform image operations source|Complete format corpus/edit/export/loss reports and bounded isolated pipeline|
|FF06 HEIC/HEIF/AVIF decode|No accepted S12 capability matrix|Actual platform codec/format/16K/OEM initialization and bounds; no x265|
|FF07 HEIC encode|No complete encoder|Only HeifWriter/HWHEVC capability path and pixel/color/fidelity tests|
|FF08 PSD/PSB|Existing bounded parser source/unit tests; no accepted layer editor|S13 permissible library/corpus/layers/composite/export/isolation|
|FF09 SVG/text|Bounded native text/CSV helpers/editor; not complete SVG/RTF|Safe SVG rendering/export and text-format fidelity/SaveAs|
|FF10 EPUB|No EPUB implementation|P4 parser, resource sandbox, reflow/navigation/export scope|
|FF11 FormatRouter|Partial routing/gates/copy protocol; not universal SaveAs|Engine registry, format-dependent fidelity/loss UI and verified publication|
|FF12 Sandbox|Real isolated-service FD/digest candidate with five device cases|Hostile real parser, parent kill/CPU/RSS/output guards, crash corpus and S-gate review|
|FF13 Metadata|No Tika+engine metadata/thumbnail pipeline|Audited Tika subset, bounded worker outputs, cache invalidation and consent/exclusions|

Other major absences: specified Room-based≤500ms restore; complete BlackTop/H1;
isolated PTY; own Beam interoperability/trust/Stop/exclusions; receiver APK;
Lens/own Meet/PC routes; own-Mac Messages; journaled Files moves; timeline backup;
commerce/entitlements; platform37 and physical reference budgets. Some legacy
source exists behind gates but is not a working substitute for these systems.

## J. Exact residual gap and next executable decision

No full solution is claimed. The24 native cases now pass and named results were
retrieved. Keep S10/S11/S29/S30 and full FF12 blocked while
running one licensed engine/capability spike at a time. Browser renderer and Office
payload are absent, not merely awaiting a final toggle. Stronger watchdog control,
ABI/page-size/license closure, provider/recovery/native AX, hardware casting and
performance acceptance remain necessary even after green smoke tests. A dedicated
inactive-window child-focus handoff regression, backlink navigation with a dirty
draft, saturated-worker death, real socket denial and API36/37 runs are next tests;
the present suite does not establish those behaviors.

Open **`OSB/android`** in Studio Rabbit1, with the documented compatible AGP/JDK17
and SDK36 baseline; the IDE name does not change toolchain compatibility. Current
min/compile/target is34/36/36, explicitly short of compile37/target37 migration.
Run `python3 tools/native_lab.py doctor`, then the real build/test workflow in
[BUILD](BUILD.md). Draft PR#2 remains the continuing work item, not a production
release or accepted beta.
