# R5 — native Save-a-Copy hardening and verification frontier

2026-10-13. This is native Kotlin/Compose work, not a TypeScript substitute. It advances PR-01 / FF-15 / FM-18 without enabling gated Files mutations, casting, Office, or other unverified surfaces. **The full application is not complete or cleared for a pilot.**

## Result and runtime honesty

Implemented a correlated copy protocol, native non-exported picker broker, checkpoint-before-output adapter, immutable readback evidence bound to request/destination, conservative recovery, v1→v2 session migration, bounded receipts and explicit abandonment.

Actually used: repository/file tools, Git, GitHub Contents API via `gh` for primary-source retrieval, Python, Kotlin **2.2.21**, Temurin **17.0.9+9** runtime, genuine JVM execution, real temporary-file writes/readbacks/fsync calls, and supplementary syntax parsing. No independent agent, theorem prover, remote database, simulator, Android SDK/emulator, physical device, receiver or counsel was available for this verification. No Android build, APK or instrumentation execution occurred. No commit, push, workflow dispatch or release was performed; the earlier CI approval prompt was skipped.

**Executed:** 153,216 existing + 2,056 calculator + 8,916 editor + **5,388 copy assertions = 169,576 passed**. The copy corpus includes 1,500 deterministic event transitions, all 50 event/phase pairs in that corpus, and three actual file-write/readback completions within those transitions, in addition to targeted filesystem tests. This is production reducer execution, not an emulated Android/provider or a device crash test. Three existing CastV2 unchecked-cast warnings remain. **15 Python tests passed**; structural and fixture checks passed.

Evidence:
- [`verification/r5-jvm-contracts.txt`](verification/r5-jvm-contracts.txt): actual output and compiled-source hashes.
- [`verification/r5-recovery-counterexample.txt`](verification/r5-recovery-counterexample.txt): actual pre-repair recovery failure.
- [`verification/r5-proof-binding-counterexample.txt`](verification/r5-proof-binding-counterexample.txt): actual pre-repair evidence-binding failure.
- [`verification/r5-legacy-fixture.json`](verification/r5-legacy-fixture.json): historical production-writer fixture provenance.
- [`research/r5-copy-frontier.json`](research/r5-copy-frontier.json): retrieved primary-source commits, blobs, hashes and excerpts.

## A. Formal model

**Admissible objects.** One immutable editor session with at most eight valid UTF-8 drafts (≤2 MiB each), at most one frozen pending payload, a copy epoch/monotonic sequence, and at most 16 receipts. Inputs are explicit user requests and attributed picker outcomes. A pending operation contains its source identity, frozen text, operation ID, phase and optional destination. The Android provider is external; the reducer does not assert that a transition performed I/O.

**Access/information model.** The app knows the explicit request ID, current private checkpoint, callback payload, granted URI, source URIs of open documents, recent destination URIs, and observed readback bytes. It does not know uncheckpointed state after process loss, arbitrary provider aliases, remote-cloud completion, physical persistence, or other apps' internal state. The broker receives only an ID and suggested filename, not document contents or source URI. Provider-facing intent carries only the ordinary document-creation request/name, not the correlation token. Receipts are private local metadata, not encrypted/authenticated history.

**Counted resources.** One active attempt; a persisted nonnegative sequence bounded by `Long.MAX_VALUE`; fresh epoch required for a genuinely new session incarnation. Sequence exhaustion refuses new work rather than wrapping. IDs ≤56 ASCII characters; destinations ≤8,192 UTF-8 bytes; 16 receipts; snapshot payload ≤18.5 MiB. Android result handling has one processing slot plus one queued result; overflow is refused, not an unbounded coroutine backlog. The editor output payload is copied before I/O so caller byte-array mutation cannot retarget its contents. These are logical bounds, not measured Android heap/frame-time or provider deadlines. A provider may block; hard liveness is not established.

**Success predicate and quantifiers.** For every admissible event sequence, conditional on unique epoch/sequence identities, serialized host ownership, faithful checkpoints and the adapter honoring the reservation barrier: stale/duplicate/anonymous results must not authorize a different attempt; editor-owned output must require a fresh matching result and a confirmed reservation checkpoint; recovered unfinished attempts must not auto-replay; a saved-state transition requires matching payload **and** request/destination-bound readback evidence. Later edits must remain dirty if they differ from the saved frozen payload. Rejection must retain drafts and must not automatically delete ambiguous provider data.

This is a intended universal invariant, supported by bounded executable tests and the source construction—not a mechanically proved theorem. The established execution statement is only: **for every case in the recorded corpus, its assertions passed**. Full product success requires an integrated implementation satisfying **all** mandatory module/feature/build/platform/device/legal/accessibility gates, not merely existence of a successful local copy example.

**Boundaries/tolerances.** Payload verification is byte-for-byte with exact length; no numerical tolerance, re-encoding, newline normalization or lossy fallback. SHA-256 is integrity bookkeeping, not authentication. A receipt describes a past readback observation, not perpetual equality. Epoch freshness and non-rollback private storage are assumptions: cloning/rolling back snapshots can invalidate uniqueness assumptions and is not repaired by an unsigned checksum.

**Adjacent problems not substituted.** This is not journaled file moves, in-place safe replacement, transactionality across a SAF provider and local disk, exactly-once document creation, cloud synchronization, physical power-loss durability, encrypted Vault storage, a full editor feature set, or a completed desktop suite. The picker itself may create an empty file before the editor opens output; the reservation barrier governs **editor-owned output**, not every provider side effect.

## B. Outcome space and threshold

- **H+ full target achievable:** not established as delivered; no global impossibility is inferred. Acceptance requires actual integration/build/install and every applicable feature, device, security, licensing and accessibility gate.
- **H− under narrower information constraints:** a local recovery algorithm cannot both automatically resume a waiting operation and always honor an uncheckpointed abandonment when both histories produce identical observed checkpoint/callback data. The indistinguishability argument below supplies the hypotheses; it is not a theorem that the whole app is impossible.
- **H∂ implemented boundary:** after process-level restoration, every unfinished attempt requires review and a fresh picker attempt. Rotation with a retained ViewModel is not automatically the same event as process restoration. Native lifecycle behavior remains untested.

No feature row, spike, module or E0 budget is promoted until its own evidence threshold is met.

## C. Primary-source frontier and exact interface

The fetched sources are commit-pinned in `research/r5-copy-frontier.json`:

1. AndroidX `ActivityResultContracts`: document creation uses `ACTION_CREATE_DOCUMENT`; ordinary `CreateDocument` parsing returns a URI, not an app-defined operation identity. Therefore the previous single callback could not distinguish an old result from a new pending payload merely by reading current mutable state. AndroidX main source is pinned as research evidence, **not asserted to be the exact source of the shipped BOM**.
2. AOSP Android 14 `Intent`, lines 4334–4338: **ACTION_CREATE_DOCUMENT may return an existing document**. Its name is not proof of freshness. This directly changed the implementation: explicit pre-picker admission warning, nonempty/unreadable-target refusal using actual stream reads (not metadata size), and no fresh/atomic guarantee. The native broker forwards only the read/write grant flags actually returned, not a fabricated grant or broad prefix grant. Actual grant forwarding through this Activity and OEM/API34/36/37 behavior remain unverified.
3. AOSP Android 14 `DocumentsContract`: document identities can be represented through tree-relative and document URIs. The Android adapter compares known URI spellings, authority-scoped document IDs and available canonical forms. These comparisons do not establish that arbitrary distinct provider IDs address different physical data. The new emptiness observation also cannot prevent a concurrent change between checking and opening output; that is an unresolved provider/exclusivity gate, not a passed safe-save theorem.
4. R3's separately retrieved `AtomicFile` source remains relevant: returning from `finishWrite` alone is not success evidence. The existing checked adapter explicitly syncs and decodes readback. That still does not prove stable storage under power loss.

No best-known global achievability/impossibility theorem for this entire product was retrieved or claimed. The open interface is the real Android/compiler/registry/provider/storage implementation, not an omitted “standard” mathematical step.

### Indistinguishability counterexample

History W: persist `AWAITING_PICKER(A)`, remain waiting, lose process.
History C: persist the **same bytes**, abandon A only in RAM before the next checkpoint, lose process.

Recovery sees the same checkpoint and can receive the same delayed result A. Under this access model, a deterministic decision based only on those observations is identical for W and C. Automatically writing for W therefore also writes for C. Randomization cannot provide a zero-error distinction from identical information. Additional durable evidence or explicit user confirmation is necessary. R5 chooses explicit review for both. The failing test exercised actual checkpoint-file write/readback plus the production recovery function; it did **not** physically kill Android.

## D. Negative space and modal shortcut

Modal shortcut: “checkpoint exists / readback matches / source compiles, therefore the save flow or module is complete.” The executed module-audit test rejects promotion while mandatory acceptance gates remain unpassed. The new evidence-binding test additionally rejects byte equality used as proof for a different destination.

| False solution shape | Concrete detector or boundary |
|---|---|
| 1. Use whichever payload is pending when a URI returns | Late A result after B retry is ignored |
| 2. Null callback means cancel the current attempt | Unattributed results do not mutate pending state |
| 3. Retry may reuse the old request identity | Persisted sequence advances; stale retry test |
| 4. Old UI cancel may cancel a new attempt | Rendered expected ID must still match |
| 5. Matching result alone permits output | Separate destination-ready and write-reserved phases |
| 6. Reservation only in RAM is sufficient | Adapter requires request/stage-specific checkpoint readback before output |
| 7. Recovered waiting state is necessarily still authorized | Actual indistinguishability counterexample; all unfinished recovery goes to review |
| 8. Replay a reserved write after process loss | Recovered reservation cannot accept another result |
| 9. Equal bytes prove the particular destination | Actual failed binding test; bind request and URI |
| 10. Correct destination bytes prove the particular attempt | Same-destination/different-ID patch attack |
| 11. Caller-owned byte arrays stay immutable during I/O | Real-file caller-mutation test |
| 12. Existing source/recent partial URI is a fresh copy | Core refusals plus unrun Android alias checks |
| 13. Distinct URI strings prove distinct provider objects | Explicitly rejected assumption; document-ID/canonical comparisons are partial |
| 14. Provider failure means safe to delete destination | Failure tests retain the real file; adapter never auto-deletes it |
| 15. Saved frozen content makes later edits clean | Late-edit digest/dirty assertions |
| 16. Legacy v1 payload can receive a new uncorrelated result | Legacy migration produces review-only state |
| 17. A new writer generating its own old-version fixture proves migration | Fixture came from the previously compiled R4 production writer |
| 18. Schema checksum authenticates receipt claims | Checksum is not authentication; private-state trust remains an assumption |
| 19. Bounded receipts allow resetting the sequence | 100 retries retain monotonic sequence while clipping receipts |
| 20. Sequence overflow may wrap | Explicit failure at exhaustion |
| 21. Kotlin/Intent test source equals real grant/IME/AX acceptance | Android tests remain uncompiled/unrun |
| 22. These assertions establish a complete module or app | 0/48 accepted; all mandatory product gates remain separate |
| 23. ACTION_CREATE_DOCUMENT guarantees a fresh item | Retrieved AOSP contract permits existing documents; explicit admission warning and real-file nonempty refusal tests |
| 24. Empty read establishes exclusive ownership | It is only a point-in-time observation; TOCTOU/provider risks remain and block full acceptance |

## E/F/G. Mechanism-distinct portfolio and targeted adversaries

These mechanisms/alternatives were examined sequentially; no independent-agent or parallel-verification claim is made. Every row supplies an executable artifact, counterexample, causal model or bound, rather than a status-only step.

| Candidate | Construction/artifact and demonstrated portion | Strongest structure-specific attack / cheapest falsifier | Exact remaining interface; gap class |
|---|---|---|---|
| C1 current-payload callback (rejected) | Causal trace: freeze A → abandon/retry B → old URI A → read current payload B. Previous adapter source had no result identity | Deliver old selection/cancel after rotating payload; new protocol tests require no-op | Old Android adapter was not executed; trace establishes missing information, not a claimed device exploit. **Local design gap** |
| C2 epoch + persisted sequence (selected) | `CopyProtocol.start/retry/abandon`; bounded IDs, monotonic sequence, no wrap; actual tests | Identical payload in two attempts; stale control; clipped receipts; sequence exhaustion | Fresh epoch and non-rollback session assumptions; native transport must retain ID. **Local identity interface** |
| C3 native identity broker (source implementation) | Explicit non-exported Activity returns its own ID; provider receives no frozen contents; only returned grant flags forwarded | System Back vs late callback; recreation; anonymous result; cross-UID launch; actual write/read grant use | Intent/manifest test authored; framework/SDK/OEM behavior uncompiled/unrun. **Local Android interface**, not a completed feature |
| C4 checkpointed reservation (selected kernel + unrun adapter) | `DESTINATION_READY → WRITE_RESERVED`, exact request/stage sync/readback before editor output | Crash or disk failure on either side of barrier; duplicate callback while busy | Real codec/fsync calls ran on temporary files, not Android failure injection or power-loss proof. **Local storage/effect interface** |
| C5 conservative recovery (selected after repair) | Two-history indistinguishability argument; production `recover` refuses all unfinished replay | Uncheckpointed abandon + restored waiting bytes; late cancel after recovery; fresh retry afterward | Trades automatic liveness for explicit confirmation; real Activity/process behavior still required. **Local information boundary** |
| C6 content/request/destination-bound readback (selected after repair) | `VerifiedCopy.Readback` + `Binding`; actual writes/reopens and immutable payload copy | Correct bytes for wrong URI/ID, unbound proof, caller mutation, readback tamper | Host must bind factories to the declared target; provider identity/physical durability not established. **Local effect/evidence interface** |
| C7 versioned migration/receipt ledger (selected) | v1 fixture from previous production JAR, v2 codec, bounded receipts and exact pending payload preservation | Unknown version with valid checksum, malformed phase, duplicate/future IDs, metadata bounds | Checksum is not tamper protection; no general backup/rollback protocol. **Local schema interface** |
| C8 known-alias exclusion (partial) | Core original/open-document/recent-target refusals; Android authority/document-ID/canonical checks | Two different provider IDs alias the same data; alias changes between check and open | Arbitrary provider equivalence is not observable through URI spelling alone. Requires provider hypotheses and tests. **External trust boundary**; universal malicious-provider safety cannot be claimed |

The full suite's missing build/platform/feature/licensing/accessibility evidence remains equivalent to the original product-delivery obligation; none of these local mechanisms replaces it.

## H/I. Verification, failures and renewed attacks

1. Real kernel compilation succeeded. Tests then **failed** on recovered waiting state authorizing a result after an uncheckpointed abandonment.
2. Recovery was changed to require review for every unfinished attempt. A new run then **failed** on readback evidence from a different request/destination with matching bytes.
3. Evidence was bound to both identifiers. New attacks separately changed only the destination, only the request ID, and removed binding. Further recovery attacks replayed old cancellation/selection after explicit retry. All affected suites were rebuilt and rerun.
4. The current copy corpus passes **5,388 assertions**, including 1,500 transitions checked against an independently written phase table, covering all 50 event/phase combinations encountered in this fixed corpus. It is finite testing, not exhaustive proof over Android interleavings.
5. The historical v1 fixture is 356 bytes, SHA-256 `225712d0e470b2ff9706b49fb8c1f6c344608cebd3a39ef983c601f50a6088a0`. It was generated by R4's already-compiled `SessionCodec.write`, then read by v2. BOM, emoji, CRLF, later edits and the distinct frozen payload survive. The private filename `editor-session-v1.bin` is retained for discovery, but its new envelope version is 2; older readers are not claimed compatible.
6. Further primary-source review found that the Create Document action permits existing items. Added an explicit user warning/confirmation before initial and retry pickers, a fail-closed stream-emptiness preflight, and real-file tests for nonempty retention, empty admission and unreadable rejection. Reran all JVM suites afterward. This is **not** an atomic freshness/exclusivity solution; the provider/concurrency gap remains.
7. Native `CopyContractDeviceTest` checks were authored for explicit/non-exported routing and attributed parsing. They have **not compiled or run**. Even passing them would not prove actual picker grants, provider fidelity, process-death behavior, or accessibility.

The same author wrote tests and implementation. Compiler output, actual file observations and an independently structured phase table improve evidence but do not create an independent review team or theorem certificate.

## J. Stop boundary, completion and next high-information test

**Strongest verified artifact:** executable production protocol/codec/readback kernels, real-file adversarial tests, historical migration fixture, preserved failing counterexamples and source-bound output. **Exact gap:** native broker/adapter/manifest integration has not passed an Android compiler or runtime, and the full module feature sets and mandatory acceptance gates remain incomplete.

**48 included Gradle modules: 0 specification-complete (0%); 48 still need implementation or mandatory acceptance (100%).** No new module was created to inflate counts. See [MODULE-COMPLETION.md](MODULE-COMPLETION.md) for individual gaps and architecture reconciliation.

Next high-information experiment: full JDK17/SDK36 build worker, both debug flavors and lint, then real provider/picker tests on a disposable AVD/device. No remote CI was launched without approval.

```sh
# From android/, with the real Android toolchain/dependency access installed:
./gradlew :app:assembleProDebug :app:assemblePlayDebug
./gradlew :app:lintProDebug :app:lintPlayDebug
./gradlew :testing:pilotContracts :testing:calculatorContracts :testing:editorContracts :testing:copyContracts
./gradlew :app:pixel6Api34ProDebugAndroidTest
```

After installation: test normal create/readback; cancel; retry while an old picker exists; rotate broker and shell; kill/relaunch while waiting, after reservation and after provider write; deny read/write grants; use local and non-local providers; verify known alias rejection, late edits and receipt uncertainty. A physical/OEM matrix remains necessary for power loss, grants, API37, IME/AX, performance and other full-product gates. Do not treat an uncertain destination as disposable or automatically delete it.
