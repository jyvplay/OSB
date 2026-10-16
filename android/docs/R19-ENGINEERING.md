# R19 — reversible task edits, confirmed merge/export, and literal multi-sheet reports

Native Kotlin/Compose only. This is an executable increment, **not completion of the original product**. The predeclared mechanisms are in [R19-PLAN.md](R19-PLAN.md). No independent agents, pretend Office/browser, mock provider, or fabricated device/prover result is counted.

| Standard | Absolute count | Percentage |
|---|---:|---:|
| Substantial executable/testable native module subsets | **47/48** | **97.92%** |
| Subset not established: LOK | **1/48** | **2.08%** |
| Fully specification-accepted modules | **0/48** | **0%** |
| Modules unfinished against the full original specification | **48/48** | **100%** |

R19 deepens `:feature:automation` and `:format:ooxml`, integrates real Android UI/provider paths, and adds an actual platform sandbox experiment. It does not invent a 48th qualifying module. A qualifying subset requires substantial real behavior plus a reproducible behavioral check; a folder, build, gate-only helper or test count is insufficient. The ledger remains 231 requirements, 36 cross-cutting obligations and 30 formal physical/legal spikes unpassed. The full 13 productivity and 13 requested Forge feature groups are not accepted either; they are different denominators from 48 included Gradle modules.

## Runtime honesty

Actually available/used: git/gh, shell/filesystem, Python3.11.2, restored Kotlin2.2.21/JRE17.0.9, openpyxl3.1.5, icalendar7.3.0 after calibration, Z3 5.3.0, and remote GitHub Android builds/managed API34 execution. Host SQLite3.40.1 was observed available but was **not used** for app storage. No local Android SDK, physical device, Android Studio GUI session, independent agent, expert AX audit or legal reviewer was available. Concurrent tool jobs are not independent authorship.

The Android emulator runs actual Android APIs, Binder, memory mappings, storage providers and Compose UI; it is not evidence of physical-device budgets. No simulated application/worker/provider substitutes for those paths. Deliberately malformed streams/collections/receipts are adversarial test inputs, explicitly distinct from runtime feature evidence.

The compiler archive SHA512 was checked. Initial extraction failed because this Python tarfile lacks the newer `filter` argument; safe extraction was retried with explicit path/type checks, not unrestricted extraction. Nine previously pinned primary files were re-fetched/hash-checked, plus two supplemental AndroidX source files. This limited review is **not an exhaustive best-known frontier search**, a transitive-license clearance or a compiler trust proof.

## A — formal model and contract

### Admissible objects/access

A `TaskBook` is an immutable snapshot of 0..128 uniquely identified tasks with canonical UUIDs, strict nonblank Unicode titles of 1..256 UTF16 units without ISO controls, optional date-only years 0001..9999, done/archive flags, and revision `r` satisfying `0 <= r <= Long.MAX_VALUE - archivedCount`. Archived records retain capacity. FTS1/FTS2 decoding retains its 128KiB cap, strict canonical/version/CRC checks, and legacy read behavior.

`TaskChange` holds immutable before/after snapshots. It binds **both exact ordered data and revision**, not merely a claimed revision or digest. A changed forward state must have `after.revision = before.revision + 1`. An admitted undoable change also checks that restoring the old data at `after.revision + 1` preserves the archive reserve. No-op preparation returns no new token. Undo never rolls the revision backward.

`TaskMergePreview` admits valid incoming snapshots but does not trust their revision as the local revision. Existing identical IDs are counted and retained; different data for an existing ID refuses the entire plan. New IDs append in incoming order after all existing records. No replacement, silent conflict resolution, automatic renaming, deletion or partial merge is performed. Capacity and immediate-undo headroom are checked before confirmation.

Repository access is serialized by the existing same-process lock. Commit/undo compares the relevant current snapshot before publication and confirms readback. A stale preview/token fails. This is not cross-process locking, authenticated storage, rollback resistance, Room, or a power-loss theorem. If an IO error follows publication, the result is uncertain; the UI blocks and asks for reload instead of assuming rollback/retrying.

### Success/failure and quantifier order

For every admitted snapshot `b` and proposed transition `f(b)` meeting the forward and immediate-undo bounds, there exists a token `c` such that **for every observed state `s` exactly equal to `c.after`**, a successful `undo(c,s)` restores the ordered task data of `b` at revision `b.revision+2`. States not equal to that after-snapshot refuse. This is **not** “for every future interleaving there exists an undo that overwrites intervening changes.” IO success additionally requires the actual write/reopen acknowledgement; an exception after publication is not a no-effect theorem.

For every admitted merge preview, application is permitted only against its exact before-snapshot. A confirmed changed merge advances the *local* revision once; a no-op does not write. The immediate undo removes only the newly imported records and restores the prior ordered data at another new revision. A conflicting/stale/over-capacity plan must not be published. No durable multi-level undo history is claimed: UI tokens are session-only, cleared on reload/recreation or replaced by later actions. The no-op edit/import path preserves a still-valid earlier meaningful edit's token.

### Counted resources, boundaries and tolerances

- Task import consumes at most 128KiB+1 bytes before refusal; the extra byte detects overflow. Reads returning zero progress refuse. Blocking provider IO has **no established wall-clock bound**.
- Task FTS export writes a captured **loaded** snapshot to a newly created picker document, closes it, reopens and compares exact bytes. Reload before exporting if another window may have changed the store. A trusted newly-created URI is a caller/provider assumption, not a capability authenticated by the helper.
- Generated task-report readback has a separate 512KiB cap. An actual stress case produced 240,458 bytes, so reusing the 128KiB task-input cap would incorrectly reject this generated report.
- Literal XLSX export accepts 1..16 worksheets, at most 1000 rows/128 columns per sheet, **20,000 cells total**, 2MiB total UTF8 text including sheet names, and at most 16MiB output. Structural budgets precede copying. Caller collections must be stable while captured; arbitrary hostile in-process executable collection implementations are not a complete threat model.
- Cell text is literal, not a formula; XML-invalid scalars, unpaired surrogates and unsupported OOXML escaped-string tokens refuse rather than silently changing values. Numbers retain the existing<=15-digit precision/scale limits and floating-point-consumer warning. Task reports use text dates, not spreadsheet date serials.
- External report comparisons require exact IDs/titles/booleans and exact date strings. Empty date cells tolerate either empty string or blank/None as represented by the external reader. No numeric precision/parity tolerance is silently generalized to all Office consumers.

Adjacent problems that are **not substituted**: XLSX export for Office import/render/edit/recalc or LibreOffice1:1 parity; report export for FTS merge-compatible backup; undo for arbitrary deletion/history; CRC/SHA for authentication; provider readback for durable storage; readonly mapping for a sealed memory object; task dates for reminders; platform WebView experiments for a complete own desktop browser; emulator checks for physical/AX/legal acceptance.

## B — outcome space and declared thresholds

**H+ (bounded):** exact-state undo, append-only merge, bounded new-document transfer and original multi-sheet literal reports can preserve admitted data under the stated ownership/IO assumptions. Threshold: separate production/consumer compilation, adversarial cases, independent references/readers, and relevant named Android outcomes for Android claims.

**H− (overstrong targets):** unconditional revision-consuming undo at every valid boundary, globally bounded workbook size from per-sheet caps alone, and revocation of every writable mapping by `SharedMemory.setProtect(PROT_READ)` are false. Concrete model/code/platform counterexamples are retained. No universal impossibility of building the original software is inferred. Complete acceptance requiring unavailable physical/legal/expert evidence cannot honestly be declared in this session.

**H∂:** success changes at revision/archive headroom, count/byte budgets, conflicting IDs, provider behavior, lifecycle/IME timing, and existing writable aliases. A valid old boundary restore remains possible through the original archive API even when it cannot promise an additional undo; the UI does not route archive/restore through the stricter edit-undo API.

A repaired candidate inherits no native result. Source/build-only or queued jobs cannot satisfy native thresholds. A retry pass does not prove a causal UI repair. Four conditional Z3 checks are not proofs of Kotlin/Android execution.

## C — frontier, primary imports and open interfaces

Pinned URLs, hashes and scope are in `research/r19-primary.json`, `r19-picker-primary.json`, and `r19-tools.json`. No authoritative theorem covering this entire application was retrieved or claimed. Relevant imported interfaces have the following limited meanings:

| Primary source | Verified import and hypotheses | Exact open interface |
|---|---|---|
| [AOSP AtomicFile](https://github.com/aosp-mirror/platform_frameworks_base/blob/299fe6f5d6fc6f1af7c3411dcf4e5efdf7217368/core/java/android/util/AtomicFile.java) | Caller supplies serialization; the adapter uses one process-global lock | External writers, power loss, directory durability and authenticated rollback protection |
| [AOSP SharedMemory](https://github.com/aosp-mirror/platform_frameworks_base/blob/299fe6f5d6fc6f1af7c3411dcf4e5efdf7217368/core/java/android/os/SharedMemory.java) | Restricting future mappings does not revoke old mapping protections | Elimination of all writable aliases; actual cross-process replacement transport; hostile-worker containment |
| [AOSP WebView](https://github.com/aosp-mirror/platform_frameworks_base/blob/299fe6f5d6fc6f1af7c3411dcf4e5efdf7217368/core/java/android/webkit/WebView.java) | Data-directory suffix is per process and must precede initialization | Dynamic profile lifecycle, renderer death, session/IME/compositor integration, not per-tab suffix switching |
| [AndroidX ActivityResultContracts](https://github.com/androidx/androidx/blob/f8f5e9c42b562e58bfca57d793d57e9471bc0d50/activity/activity/src/main/java/androidx/activity/result/contract/ActivityResultContracts.kt) | CreateDocument requests creation with explicit MIME/name and returns the provider's result URI | Provider honesty, cancellation/recreation, partial publication and full picker-save workflow; not an atomicity theorem |
| [LOK interface](https://github.com/LibreOffice/core/blob/0f91d58472b9a1b3289e070e4c03335d5935b4eb/include/LibreOfficeKit/LibreOfficeKit.h), Android/LOK READMEs and MPL text at the same pinned commit | Upstream integration interface and license text exist | No audited payload, transitive approval, major-format corpus, ABI/16KiB or safe-save/fidelity acceptance in this project |
| Pinned pyca AEAD docs / AndroidX BaselineProfileRule | AEAD has nonce/key/AAD hypotheses; profile generation is not a causal performance theorem | Neither encrypts these tasks, proves lifecycle security nor establishes physical speed/latency budgets |
| Pinned UiDevice source | Apache license header retrieved; requested exact signature search did not match | No API availability inferred from that failed search. Actual resolved2.3.0 test compilation/device execution is the relevant gate |

No Office/browser binary or unknown-license engine was added. UIAutomator is a **test-only** dependency; activity-compose is an existing catalog dependency and the workbook implementation is original project code. Source license headers do not constitute a complete transitive or legal audit. Existing licensing exclusions and all formal release gates remain intact.

## D — negative space and executable modal-shortcut detector

`tools/r19_evidence_gate.py` uses a closed vocabulary and actual named run receipts. It checks the inherited strict candidate/conclusion/count requirements and the required R19 cases. Its tests use retained actual receipts plus explicitly corrupted copies. It cannot understand arbitrary prose, authenticate a forged offline receipt, judge test sufficiency, or replace expert review.

| # | Looks like a solution, but is not | Executable detector / counterexample |
|---:|---|---|
|1|Count a source folder/build as completed module|Hash/path census plus separate0/48 full-acceptance ledger |
|2|Create a token helper and claim LOK48/48|LOK remains unestablished; no engine payload or claimed qualifying pairing |
|3|Use source revision alone for undo ownership|Same-revision/different-data native attack and exact snapshot comparison |
|4|Undo by decrementing revision|Host/Python checks require a fresh revision; replay/stale token refuses |
|5|Promise unconditional undo atMAX|Actual boundary tests and SAT counterexample; forward-valid need not be undoable |
|6|Spend archived-record restore reserve|Existing R18 attacks plus native old-restore-boundary test |
|7|Erase prior undo on a no-op save|New actual UI no-op/edit/undo sequence; readiness observes enabled control, not unchanged revision alone |
|8|Import by silently replacing matching IDs|Host conflict refusal and native no-publication checks |
|9|Adopt an imported MAX revision locally|Host/Python/native foreign-revision checks; local revision advances once |
|10|Apply an outdated preview|Native interleaving and same-revision altered-file attacks |
|11|Free capacity by hiding archives|128 archived record import/capacity and restore tests |
|12|Treat a preview as a completed save|Native absent-file check before confirmed merge |
|13|Bound consumed bytes but spin on zero reads|Adversarial zero-progress stream refuses; overflow consumes at most limit+1 |
|14|Treat byte bounds as IO deadlines|Policy rejects `all_IO_cancellable`; provider latency remains unbounded |
|15|Check limits after copying oversized collections|Actual external-consumer witness; row and aggregate-cell patch attacks |
|16|Apply20k cap independently to each worksheet|Aggregate20,001-cell attack and SAT two-sheet counterexample |
|17|Let formula-like titles become workbook formulas|Actual openpyxl type/value assertions and native generated-XML check |
|18|Call XLSX report a task backup/import|Actual content-provider report is refused by FTS reader; UI distinguishes formats |
|19|Silently normalize unsupported XML/OOXML text|Host refusals preserve task data; FTS remains available |
|20|Call reopen/hash physical durability or authentication|Exact-byte readback claim is scoped; policy rejects power-loss/authentication claims |
|21|Call a new readonly mapping an immutable object|Actual SharedMemory old-writer counterexample; strengthened permission-errno attack |
|22|Count an unrelated mapping failure as permission denial|New test requires EACCES/EPERM, not just any exception |
|23|Count green build-only/zero-execution jobs as device passes|Collector distinguishes full/focused jobs and refuses the wrong job shape; named outcomes required |
|24|Reuse a pre-repair candidate's pass|Exact commit gate and source/jar hashes; repaired candidates need fresh runs |
|25|Blame new product code for an uncalibrated reader|Actual icalendar6.3.2 failure retained; restore known7.3.0, own-writer calibration, original corpus rerun |
|26|Turn a missed tap into a pass with semantic onClick/sleep|Actual touch remains; no timeout inflation or semantic click bypass |
|27|Promote finite tests to all providers, AX, Office or UI stability|Unsupported-claim policy tests and explicit residual ledger |
|28|Infer picker cancellation from background app semantics alone|Stronger native check requires DocumentsUI disappearance and app foreground; fresh result required |

The modal shortcut is the inference from **one available/possible local behavior** to the **universal original acceptance predicate**. E.g. a real readonly mapping exists does not mean all writers are revoked; a parsed report exists does not mean Office fidelity; a single successful UI run does not mean every supported interaction is stable. The policy deliberately rejects those claim labels even when an exact narrow receipt is green.

## E — independent mechanism portfolio

The early branches were recorded before feature implementation, then composed only at explicit interfaces. Independence here is mechanism independence, **not multiple independent agents**.

| Mechanism / central construction | Concrete artifact and established portion | Unresolved interface; cheapest falsifier | Gap type |
|---|---|---|---|
|1. Exact snapshot undo capability; two forward revisions | `TaskChanges.kt`; separate consumer/reference checks and conditional arithmetic | Alter same-revision data, replay, or useMAX boundary; storage/lifecycle still needed | Local undo contract; durable full history remains larger |
|2. Append-only merge planner with conflict/no-op partition | `TaskMergePreview`; independent Python expected frames | Conflicting ID after earlier additions, full128 cap, stale preview, imported MAX | Local admitted merge; not arbitrary conflict reconciliation/sync |
|3. Bounded stream consumption | `TaskStreams.kt`; overflow/no-progress/truncation attacks | Provider blocks forever or violates stream assumptions | Byte-consumption bound local; deadline requires different architecture |
|4. Serialized exact-CAS publication and readback | `TaskRepository.kt`, native file tests | External writer, postpublish failure/power cut; exact-content stale token | Same-process local; Room/crash-proof journal still open |
|5. New-document capability and explicit preview/confirmation UI | `TaskTransferIO.kt`, actual picker/provider tests | Recreate during picker, lying provider, cancelled/partial export; arbitrary old URI violates helper precondition | Local routes; all-provider/end-to-end UX remains broader |
|6. Original literal OOXML package graph | `LiteralWorkbook.exportSheets`, external openpyxl | Duplicate names, global text/cell limits, escape amplification, formula-like text | Local export; full Office engine is original-scale gap |
|7. Task-to-report semantic projection | `TaskWorkbook.kt`; two sheets, text dates, booleans, strict refusal | Max128 records, text escaping beyond128KiB, unsupported XML tokens | Local reporting; not lossless FTS roundtrip or editable Office model |
|8. Actual memory-permission counterexample | `R19SharedMemoryDeviceTest`; real mappings/old writer | Require permission errno, retain a writable alias, test actual cross-process transport next | Local OS primitive; universal sandbox remains original-scale gap |
|9. Conditional mathematics and claim admission | `prove_r19_models.py`, `r19_evidence_gate.py` | SAT under positive premises, model/code mismatch, wrong candidate/zero cases | Local model/policy; cannot prove implementation or release acceptance |

Each branch returns actual code, a finite experiment/counterexample, or conditional machine-checked model—not a status label. The UI/provider branch is not promoted merely because its host primitives compile.

## F — artifact and external-verification ledger

| Executed check | Observed result | Exact scope |
|---|---:|---|
| R19 separate production libraries/public consumer | **539 assertions PASS** | Undo/merge/streams, task reports and multi-sheet writer |
| Independent Python/JVM plus external openpyxl | **430 cases PASS** |150 merge references,150 edit/undo references,80 multi-sheet and50 task-report readbacks |
| Postimplementation stress corpus | **30 report cases PASS** | Empty and128-record cases, quotes, CJK/emoji, extreme dates, literal formula-like strings |
| Stress input/output bytes | Input max104,340; output3,306..240,458 | Actual observed corpus, not all possible cases |
| Host producer child peak RSS |103,704KiB in this Linux run | One actual JVM process observation, not a bound/Android/physical acceptance; excludes Python memory |
| R13 regression | **503 assertions +404 cross-reader cases PASS** | Including100 actual XLSX packages; reader-version failure retained separately |
| R17 regression | **397 assertions +200 reference cases PASS** | Existing task model/codec |
| R18 regression | **906 assertions +1,200 reference cases PASS** | Existing task/query and owned-sheet correlation |
| Python tooling | **95 methods PASS** | Includes offline metadata-policy fixtures, not native app outcomes |
| Z3 5.3.0 | **4 UNSAT counterexample checks with SAT premises;2 SAT boundary models** | Conditional integer models, not implementation verification |

`verification/r19-kernel-contracts.txt`, `r19-interop.json`, `r19-report-stress.json`, `r19-models.json` and regression receipts bind actual sources/jars/scripts. The stress corpus was created after implementation; it is **not claimed as a pre-registered blind or independently authored held-out set**. openpyxl/icalendar are independently maintained readers; test orchestration/corpus authorship is ours.

The first R13 cross-reader rerun used the wrong older icalendar6.3.2 and actually failed on backslash handling. The prior repository receipt identified 7.3.0 and its calibrated reader boundary. The production calendar exporter was not changed. 7.3.0's own-writer/readback calibration and the original 404-case corpus were rerun successfully. This is verifier repair, not permission to discard inconvenient product failures.

## G — candidate-specific second-order attacks

**Undo:** attack a token with equal revision but different data/order, replay after undo, attempt a no-op that discards prior UI capability, exhaust revision headroom, and restore an archived record at the old reserve boundary. The stricter undoable path refuses before write; the original restore path remains available.

**Merge:** put a conflict after valid additions, import an enormous foreign revision, fill capacity entirely with archives, alter the actual base file at the same revision, or mutate another repository instance between preview and confirmation. There is no partial application/automatic conflict policy. Undo removes newly added records only when the current snapshot still matches exactly.

**Streams/providers:** truncate at every prefix, append a byte, provide128KiB+1, return zero progress, hand over a non-content URI, or pass a generated XLSX to the FTS decoder. Real platform MediaStore tests are distinct from adversarial stream objects. Blocking/lying providers and all cancellation/power-loss cases are not established.

**Workbook:** attack duplicate sheet names, invalid Unicode/XML/OOXML escape-like text,16/17-sheet boundary,20,000/20,001 aggregate cells, aggregate text across sheets, and XML escape amplification. The first candidate copied invalid rows before rejecting them; its actual public-consumer witness is retained in `r19-copy-before-bound.json`. The patch checks structure before copying and adds an aggregate-cell lazy-collection attack. A maximum task report exceeds128KiB, so its512KiB readback route is independently tested rather than weakening the task-import limit.

**SharedMemory:** keep the original writable mapping after reducing protection; it still changes data seen by a readonly reader. Tighten the negative check to actual permission errno so ENOMEM/other failure cannot masquerade as access denial. This is not a malicious-worker simulation and does not certify a replacement production transport.

**UI/verification:** unchanged revision is not completion evidence for no-op work. The patch test waits for the actual enabled Undo control and disappearance of edit mode under the same 10s threshold, then uses touch. Actual first native compilation failed because UIAutomator was not on the app test classpath; no cases ran. The test-only dependency was added and fresh native verification requested. Old Task-add instability remains a separate issue; successful diagnostic runs are not a proven causal repair, and observing geometry itself can change timing.

## H — native verification, not inferred passes

| Candidate / run | Actual outcome | Meaning |
|---|---|---|
|1b80ba7 / [38832395859](https://github.com/jyvplay/OSB/actions/runs/38832395859)|86 PASS,0 FAIL,0 SKIP|Collected inherited baseline; does not erase R18's85/86 failure |
|d0dc4f0 / [38837062900](https://github.com/jyvplay/OSB/actions/runs/38837062900)|87 PASS,0 FAIL,0 SKIP|Actual initial SharedMemory alias spike and existing suite; before feature implementation |
|2ee4e9e focused / [38838202649](https://github.com/jyvplay/OSB/actions/runs/38838202649)|0 executed; FAILURE|Missing UIAutomator test dependency, not a passed device suite |
|2ee4e9e full / [38838202743](https://github.com/jyvplay/OSB/actions/runs/38838202743)|0 executed; FAILURE|Same instrumentation compilation gap; build-only success cannot substitute |
|d80d971 focused / [38838538835](https://github.com/jyvplay/OSB/actions/runs/38838538835)|0 executed; FAILURE|Dependency not yet repaired in that source candidate |
|d80d971 full / [38838538834](https://github.com/jyvplay/OSB/actions/runs/38838538834)|0 executed; FAILURE|Same missing test dependency |
|cce38cb full / [38838667124](https://github.com/jyvplay/OSB/actions/runs/38838667124)|95 PASS, 0 FAIL, 0 SKIP|Actual new feature cases and stronger SharedMemory permission assertion; both builds/JVM/contracts/lint also passed |
|cce38cb focused / [38838667176](https://github.com/jyvplay/OSB/actions/runs/38838667176)|4 PASS, 0 FAIL, 0 SKIP|R17/R18 plus two R19 UI cases; not a full-suite replacement |
|58e6566 full / [38839938215](https://github.com/jyvplay/OSB/actions/runs/38839938215)|96 PASS, 0 FAIL, 0 SKIP|Includes all ten R19 cases and actual picker import/confirm/save/reopen/undo; both builds/JVM/contracts/lint PASS |
|58e6566 focused / [38839938303](https://github.com/jyvplay/OSB/actions/runs/38839938303)|5 PASS, 0 FAIL, 0 SKIP|R17/R18 and all three strengthened R19 UI cases PASS |

These are actual managed API34 pro-debug results, not physical hardware, API36/37 or play-flavor device execution. `r19-native-*`, `r19-focused-*` and `r19-picker-{native,focused}-*` retain the named cases and fetched run/job/annotation metadata. No native success is inferred from 87 earlier cases.

**Subsequent test-strengthening candidate 58e6566:** the old picker-cancel assertion observed app state but did not explicitly require DocumentsUI to leave the foreground. This is a source-reviewed test gap, not a separately demonstrated false pass. The new assertion checks actual picker disappearance and app package foreground. A third R19 UI test drives actual system import → confirm → create/save → reopen generated copy → discard no-op preview → undo, checking untouched source bytes. No fake provider, URI injection into the app callback, semantic onClick bypass, sleep or increased timeout is used. The strengthened full and focused runs actually passed 96/96 and 5/5 cases at 58e6566. The complete select/confirm/save/reopen/discard/undo sequence passed in both runs. The test uses a uniquely named real MediaStore input and a uniquely named picker-created copy on the explicitly disposable AVD; it does not inject a URI into the app callback. It does not claim every provider, process-death/recreation-during-picker behavior, or every failure mode. The transfer policy now additionally requires this case; therefore the earlier 95-case receipt does **not** pass that strengthened transfer claim. Duplicate PR-triggered jobs build/test without the full device job; their green status is not a new native count. The collector now supports an explicit `--focused` mode and records suite identity rather than treating focused output as full-suite acceptance.

## I — repair and trust reset

- Workbook copy-before-bound witness → preflight structural/aggregate checks → fresh separate compiler run, both targeted lazy-collection attacks,430 references/readbacks and30 stress reports; R13 legacy writer regression rerun.
- Potential UI no-op loss found by source/data-flow review → preserve only a token matching the unchanged current snapshot → new native no-op/edit/undo attack. This prepatch UI loss was **not** separately reproduced on a device; do not relabel source review as execution.
- Test readiness flaw → observe enabled Undo/edit-mode exit rather than an already-present revision label. No longer timeout, retry loop or semantic click bypass.
- Generic SharedMemory mapping failure → require EACCES/EPERM and retain successful reader/writer controls. Stronger permission assertion passed in the collected cce38cb full run.
- Native compiler failure → add existing-catalog UIAutomator2.3.0 only to androidTest → fresh compile/device gates; no runtime APK engine dependency added.
- Picker foreground assertion gap → explicit disappearance/foreground predicates and actual select/confirm/save/reopen/undo workflow → fresh full/focused gates, with no inherited acceptance.
- Old reader failure → calibrated 7.3.0 → unchanged corpus rerun; original failure retained.

The evidence policy is attacked with wrong candidates, missing R19 cases, corrupted counts/conclusions and18 unsupported broader claim labels. An authored source-hash census still needs semantic review; it is not an autonomous completeness prover.

## J — alternatives, residual gap and next highest-information experiment

### Browser

Keep the first-party compositor and own session model, with early-initialized profile processes and remote WebView surfaces as one route. The existing real storage-profile and remote-pixel/input/resize experiments remain concrete artifacts. Per-process suffix is not per-tab isolation. The next discriminating experiment is real renderer/profile-process death and rebind with two live views, IME/focus/resize and complete session reconstruction. An audited isolated alternate engine such as GeckoView is a distinct route, not an executed integration here; native/transitive licenses and ABI/16KiB budgets precede adoption. A tab bar or external browser delegation is not substituted for the requested own desktop browser/Beam stack.

### Office

R19 advances the original restricted-model route with actual multi-sheet packages and a user-facing task report. It is useful without claiming import, styles, layout, formulas, macros, fidelity or LibreOffice-from-scratch parity. The alternate audited LOK route still requires a permitted payload/transitive audit plus real malicious-document, rendering and round-trip corpus tests. An original wider OOXML/ODF engine would avoid importing an engine but must actually implement the missing semantics. Exporting literal data does not close that original-scale gap. Cloud/delegation is not silently substituted.

### Sandbox

The actual SharedMemory experiment rules out the proposed shortcut “setProtect readonly means no writer remains.” A safer shared-memory design needs all writable mappings eliminated before transfer, with explicit ownership tests. A reviewed NDK sealed-memfd route is distinct: test actual seals/EBUSY behavior, descriptor mode compatibility with the existing worker reader, Binder handoff, ABI/16KiB and SELinux on real Android before production integration. Copied bounded Binder payloads are another route, trading shared aliases for transaction-buffer pressure. None proves a compromised worker's arithmetic truth. The existing unlinked-file path remains unchanged and retains its trusted-owner limitation; no unsafe transport switch was made after a partial spike.

### Productivity/storage

Exact snapshot undo/append-only merge already provides a bounded, account-free workflow without pretending to solve global synchronization. A durable operation journal could retain multi-level undo but needs tail/replay/compaction/authenticated-rollback policies. Room/SQLite transactions would address the requested Room direction, but migration, revision-CAS, process death and power-cut tests remain mandatory. Dates alone do not implement inexact reminders or notification permissions. Manual export is explicit plaintext, not automatic backup or cloud relay.

### Stop condition and remaining gap

Strongest collected artifacts are the compiled/externally consumed kernels, actual platform alias counterexample, **96/96 full and 5/5 focused API34 cases at 58e65660881e7e5b4663dd9e3c31dd86ab651a05**, and both debug builds/JVM/contracts/lint. The exact-candidate policy admitted all three narrow claims after fresh collection (`verification/r19-promotion.json`), while refusing the old 95-case receipt for the strengthened transfer claim. This is a completed bounded increment, not an original-scale solution. Later documentation/evidence/host-tool-test commits are not themselves new native results; no Android production or instrumentation source changed after 58e6566 in this evidence closure.

Still missing: full Office/LOK, full own browser/Beam, complete parser containment, durable undo/Room, all-provider picker behavior and lifecycle/failure coverage, task reminders, complete keyboard/AX/200%-reflow release verification, API37/arm64/16KiB/physical budgets and the remaining original workflows/legal gates. Compile36 remains a requested-compile37 deviation. Full module acceptance stays0/48.

Next highest-information checks: collect the repaired native run; on failure retain actual case/phase/data and repair that mechanism, not a guessed cause. After a pass, exercise picker save/import-confirmation/recreation across real providers and200% font, then inject real post-publication failure/process death before considering durable history. For the older Task-add failure, capture input-event timing, status and button/window/IME bounds together; pre/post geometry or another retry alone cannot establish cause.

Try only disposable data; see [BUILD.md](BUILD.md). The working branch remains `arena/31a7965e-osb`; PR5 remains open and no merge was authorized/performed.

**Next highest-information experiments:** (1) kill/recreate the app while a real picker is outstanding and while provider write/readback is interrupted, requiring no invented payload, no original-file overwrite and explicit uncertainty; (2) exercise no-op *confirmation* after an existing undo token, beyond the now-passed preview-discard path; (3) capture the old intermittent task-submit failure with actual input-delivery/IME geometry timing, without substituting sleeps or semantic clicks. These are specific unresolved Android paths, not a claim that a finite retry establishes stability. Browser renderer-death/rebind and sandbox ownership/sealing experiments remain separate from these storage tests.

PR #5 remains open and unmerged. No merge authorization was given. The original full product, physical/legal gates and full module acceptance remain unfinished; test counts are not percentages of specification completion.
