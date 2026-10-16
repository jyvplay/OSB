# R17 — verified R16 closure, then bounded persistent local tasks

## Runtime honesty and numerical result

**47/48 modules (97.92%) have substantial executable subsets;1/48 (2.08%) remains unestablished: LOK. Fully original-specification-accepted:0/48 (0%). Unfinished:48/48 (100%).** The census counts each included Gradle module once. Tasks extend the already-counted automation module; no new denominator or duplicate credit was introduced.

Actual systems: Python3, separately restored Kotlin2.2.21/JRE17.0.9, Python cryptography46.0.3/OpenSSL and standard-library calendar/struct/CRC, Git/gh/GitHub Actions/API, actual remote Android API34 managed devices, actual AndroidX macrobenchmark/profile generation. Compiler tarball SHA512 and safe archive member paths were checked. Two regression commands ran concurrently. No independent agent, physical device, expert accessibility review, counsel or local Android SDK/ADB execution was available. Later in R17, host-only Z3-solver5.3.0.0 was installed and used for explicitly conditional mathematical models; it did not verify Kotlin or Android code. Real emulator execution is disclosed as such, never substituted for physical acceptance. No TypeScript product, mock peer/player or simulated OS was added.

Order honored: finish R16's six bounded experiments and reporting first, including its explicit negative LOK outcome; then implement Tasks. [R16-ENGINEERING.md](R16-ENGINEERING.md) contains that closure. It does NOT say complete co-watching, Office or performance acceptance.

## A. Formal model

**Objects:** at most128 immutable tasks in one snapshot. Each has a canonical lowercase UUID, nonblank title of1..256 UTF16 units with strict Unicode/no ISO controls, optional proleptic-Gregorian date in years0001..9999, and a reversible done flag. Dates are labels, not instants, timezones or alarms. Revision is a nonnegative signed Long; a meaningful change increments it exactly, overflow refuses, no-op marking does not write. IDs remain stable. Deletion/editing/recurrence/reminders are not implemented.

**Access:** one app process, app-private no-backup storage. No SAF/provider mutation, URI/file grant, CalendarContract, account, cloud, telemetry or scheduled action is added. Multiple repository instances share a process-wide lock. Each mutation compares the caller's expected revision to a fresh load before transforming. This is not an interprocess lock or malicious-rollback defense. File path/caller are trusted; no user title is used as a pathname. Task content is plaintext, disclosed before session enablement and in the panel.

**Resources:**128KiB maximum accepted frame;≤128 tasks; per-title UTF8 length checked before allocation. A valid title of256 UTF16 units needs at most768 UTF8 bytes. Thus a maximal valid canonical snapshot is at most20+128×(36+1+2+768+8)=104340 bytes, below the input cap. This bounds encoded data, not process RSS or storage latency. File reads use fixed buffers and refuse over-limit data. Reads/writes run off the main thread. No hard scheduling, power-loss or hardware-performance guarantee is claimed.

**Storage:** FTS1 canonical big-endian framing, strict UTF8, explicit lengths/counts, revision, date epoch-day/null sentinel, Boolean flag and CRC32. CRC detects accidental changes, not a malicious writer. AtomicFile publication is followed by actual bounded reopen/validation. A save error can occur after publication: outcome is uncertain until reload; no rollback claim or blind retry. Corrupt base data is not replaced. An orphan `.new` with no committed base refuses; AtomicFile may clean its own stale temporary file when a committed base exists.

**Quantifiers:** for a fixed candidate, domain, environment and declared finite corpus, every case must yield its specified state/refusal; one violated invariant rejects the candidate. Accepted subset claims additionally require positive execution of the actual public production path. This is not a universal claim over arbitrary mutable/concurrently altered collections, compromised processes, storage failures, Android versions/OEMs or the full original application. Mathematical bounds assume the stated encoding/model; four conditional integer models were checked by Z3, but no implementation-level proof was produced.

Adjacent problems not substituted: date label/alarm; done flag/notification delivery; private snapshot/general SAF safe-save; process lock/interprocess exclusion; CRC/authentication; successful reopen/power-cut durability; visible widget/complete keyboard/AX; startup TTID/Room restore latency; emulator sample/physical budget; one tested kernel/full module; numeric parser/Office1:1; measured ART rules/proven speedup.

## B. Outcome space and thresholds

- **H+**: bounded local task persistence and interaction are achievable with existing permissible dependencies and authority. Host threshold: actual separately compiled model/codec consumer plus independent wire/date reference. Native threshold: actual file/revision/corruption/abort behavior and shell UI recreation.
- **H−**: specific stronger claims fail. Actual prior-source focus did not always reproduce; actual large Checks annotations were truncated; CRC-valid replacement frames import; a generated profile alone cannot establish a speedup or complete module.
- **H-boundary**: answers depend on trusted clocks/keys, process boundaries, private storage, AtomicFile/platform behavior, API/OEM, concurrency and user consent. Global full-product feasibility/impossibility is not proved.

Commit thresholds were declared in `R17-PLAN.md`; experimental Android source commits followed local/source gates without pretending the device run had passed. Exact native conclusions are recorded separately below.

## C. Retrieved frontier and hypotheses

`research/r17-primary.json` contains8 verified records covering7 distinct source inputs (one inherited LOK header is duplicated, not independent evidence). LO architecture/license/header inputs, pyca AEAD documentation and pinned AndroidX BaselineProfileRule were reverified; AOSP AtomicFile was fetched and Git-blob/SHA256 verified with contextual excerpts. This is a limited primary-source investigation, **not an exhaustive best-known achievability/impossibility survey**.

AtomicFile's contract expressly requires caller locking. The new adapter supplies a single-process lock and revision checks; it does not import an interprocess guarantee. Platform synchronization/rename plus reopen does not establish directory-fsync, hostile-filesystem or power-loss behavior on every device. No complete storage theorem is claimed.

AEAD nonce/key hypotheses and conditional clock interval algebra are restated in R16's report, with real Java/OpenSSL and Android tests. Independently maintained libraries reduce shared implementation dependence but the test corpus is same-author, not independent expert review. The pinned AndroidX upstream source is not misidentified as the exact resolved1.3.3 binary; actual compilation/execution verifies that binary's used interface.

The open interface is end-to-end workflow authority and lifecycle: full browser host isolation/UX, whole Office engines/fidelity, comprehensive broker/parser containment, reminder/provider integration, physical/API37/ABI budgets, AX and licensing. Source headers do not close those interfaces.

## D. Twenty false solutions and executable detectors

| False solution | Detector / exact failure |
|---|---|
|1.97.92% means almost the whole app is complete | Separate0/48 acceptance ledger;231 requirement rows remain unaccepted |
|2. Test count measures feature volume |17131 R16 assertions mostly exercise clock algebra, not17131 sessions |
|3. Source existence makes LOK testable | Actual payload inventory still has only unavailable bridge/build declaration; no positive row |
|4. A successful job guarantees intact evidence | Reassembly/SHA rejected the actually4096-character-truncated bundle |
|5. Repaired receipt inherits old trust | New3800-character export, parser attacks and fresh completed measurement run |
|6. Historical focus failure must always occur | Real NOT_REPRODUCED observation retained; candidate regression remains mandatory |
|7. Handwritten/empty rules count as measured profile |855 imported rules match verified generation and packaging hashes |
|8. Packaged profile proves compilation or speedup | Separate profile-required startup; no speedup inference from3 samples |
|9. Emulator TTID is the500ms restore gate | Units/task distinctions and raw samples reported separately |
|10. A mutable list wrapped in a data object is immutable | Caller-list mutation and attempted exposed-list mutation tests |
|11. UUID parsing implies canonical representation | Short/aliased forms refuse; wire re-encoding must match |
|12. A plausible date string is a valid date | Leap-day/year-zero/year10000/width/zone-suffix tests |
|13. CRC authenticates a snapshot | Actual changed-title frame with recomputed CRC imports successfully |
|14. CRC failure alone tests the parser | Recomputed-CRC malformed flags, counts, revision and UTF8 attacks |
|15. AtomicFile supplies locking | AOSP contract and actual two-repository stale-writer race test |
|16. A remembered UI list is persistent storage | Actual AtomicFile reopen and Activity recreation tests |
|17. Corrupt data can silently reset to empty | Load/add refusal and exact-byte preservation test |
|18. Pending `.new` is a confirmed snapshot | Orphan-unpublished-file refusal test |
|19. Save exception proves rollback | UI blocks until reload and explicitly reports uncertain outcome |
|20. Tasks with due dates deliver reminders | No scheduler/receiver/notification introduced; UI states no reminders |

The modal shortcut is calling a partial component the original workflow. The tests require real files, sockets, UI and compilation where relevant, while the separate full-acceptance ledger prevents those positives from becoming a product-completion claim.

## E–G. Mechanism portfolio, artifacts and candidate-specific adversaries

| Mechanism / central construction | Concrete artifact and established portion | Strong structural attacker / cheapest falsifier | Exact unresolved interface / gap scale |
|---|---|---|---|
| Transported evidence integrity | `export_r16_measurements.py`, `collect_r16_measurements.py`; bounded chunks, reassembly and SHA | Observed4096 truncation; duplicate/missing/reordered/tampered chunks | Trusted CI/source provenance still required; hashes are not independent attestation. Local evidence-interface gap |
| Immutable revisioned task state | `TaskBook.kt`; copied unmodifiable list, canonical IDs/date domain, checked increments | Aliasing, duplicate IDs, year limits, overflow and no-op mutation | Task editing/deletion/recurrence/scheduling absent. Local kernel vs original PR03 workflow |
| Canonical bounded serialization | `TaskFrame`; actual separate producer/consumer and Python reference | Forged valid CRC plus malformed lengths/UTF8/flags/revisions, byte/truncation attacks | Plaintext/unauthenticated; no untrusted-file import UI or cryptographic rollback defense. Local storage-format gap |
| Serialized optimistic storage | `TaskRepository.kt`; shared process lock, expected revision, AtomicFile and verified reopen | Two repository writers racing the same revision; corrupt/oversize/orphan/abort paths | Cross-process actors, power cuts, filesystem/OEM durability and backup not accepted. Local adapter plus original storage gates |
| Lifecycle-aware UI | `TaskPanel.kt`, real shell entry; off-main IO, blocked-on-error state, explicit consent/no reminders | Invalid input, duplicate activation, stale writer, recreation, cancellation after publication | Complete keyboard/TalkBack/Switch/Voice/200% and task workflows remain release gates |
| Measured ART compilation | Actual generator/journeys,855-rule profile and packaging receipt | Missing/empty profile, wrong target, skipped UI, debug build, profile-required compilation | Physical performance and general causal speedup remain unproved. Original budget gap |
| Session-bound AEAD and clock interval | R16 actual TCP producer, independent OpenSSL peer and Android cases freshly verified | Replay/reflection/slow-input/ninth connection/asymmetric delay/dishonest key holder | Pairing, real player, multi-device servo, Syncplay and S22 remain open. Original service gap |
| Engine feasibility boundary | Reverified LO inputs and payload inventory; no fake engine | Treat codec/arithmetic/tile helper as1:1 Office | No audited runnable LOK or original full-parity rewrite. Original-scale gap; negative outcome retained |

The early mechanism separation is in the R16/R17 plans. These are not independently authored agents. Each branch returns executable artifacts, a conditional bound/causal model or a negative witness; status-only branches are not promoted.

## H. Verification receipts

- Tasks: **397 host assertions +200 independent Python/JVM wire/date cases PASS**. Separate production JAR and consumer compilation; source/JAR/runner hashes retained in `r17-kernel-contracts.txt` and `r17-interop.json`.
- R16: **17131 assertions +64 real cross-process Java/OpenSSL socket cases PASS**.
- Fresh R15 regression: **5510 assertions +700 cases PASS**; fresh R14: **1893 assertions +600 cases PASS**.
- **Four Z3 counterexample queries returned UNSAT**, each with satisfiable assumptions; a separate asymmetric-delay query returned SAT against the claim that midpoint always equals true offset. `r17-smt-models.json` retains solver version, source hash, SMT2 and witnesses. These concern mathematical models only, not platform behavior or Kotlin/JCA/AtomicFile implementation.
- **81 Python tests PASS**; structural/source/ownership checks pass. None implies Android or product acceptance.
- R16 native: **74 PASS/0 FAIL/0 SKIP** at7a1eee3/run38695979143, and again atbb338a5/run38697143956 after the evidence/historical-focus repair. Receipts retained.
- Measured-profile run **bb338a5/38697143892**:1 generator +3 measurement cases PASS; exact855-rule hash and ART assets match. See R16 report for raw measurements and limits.
- Five new Task Android cases cover private-file persistence/race, corrupt bytes, oversize/orphan data, explicit AtomicFile abort and real shell add/complete/recreate. Actual outcomes: e5da199/run38698914797 passed79/79;6164211/run38699003492 passed78 with1 Task UI timeout after Add. All four storage tests passed in both. Atddc7fec, the test adds explicit input/enabled-state checks and diagnostic observations without changing production; focused run38700509147 passed the real add/complete/recreate case. This does NOT establish a causal repair or stability. The fresh full-suite result is recorded below after collection.

## I. Repairs and regression discipline

The actual truncated receipt and non-reproduced historical focus experiment are retained, not deleted by the repair. New parser-limit/hash tests and the fresh complete measurement run validate the receipt repair; the current focus assertion remains mandatory. Old-source nonreproduction weakens causality/stability claims.

R16's public clock-constructor defect and unsigned benchmark APK are retained in its report. Fresh expanded clock/concurrency and all64 independent socket cases passed; current module acceptance remains bounded.

Tasks add an attack beyond checksum-only testing: recompute CRC after corrupting semantic fields, then require semantic refusal. Constructor aliasing, date boundaries and revision overflow receive explicit tests. Save failure is not renamed rollback. The Task UI timeout at6164211 is retained. It did not reproduce in the focused diagnostic; no causal production repair is claimed. Fresh full-suite results must be examined, and repeat/timing/IME/density investigation remains a residual gate even after a green rerun.

## J. Major-system alternatives and exact remaining gaps

1. **Browser:** use the existing first-party shell/session model and actual remote-surface/profile experiments as foundations. Causal design: explicit user intent → signature/UID-checked host broker → separate first-party browser host → remote surface/input → own compositor; grants and per-site permissions stay explicit. Next falsifier: wrong-UID binder request, profile crossover and host death on API34/36/37. Same-UID processes and a generic WebView UI do not satisfy the security/full desktop-browser contract. A separate first-party companion UID is a design route, not a shipped or universally supported solution.
2. **Office:** preserve original bounded formats/arithmetic and actual read-only-FD child execution, while specifying a real document model/import/layout/edit/save/fidelity corpus. Audited isolated LOK and headless POI are alternative engine routes, not evidence of a from-scratch1:1 rewrite; IPC does not cure incompatible licenses. Next highest-information test needs an actual approved engine payload and real format corpus. Full LibreOffice parity remains undelivered; no honest parity percentage is available.
3. **Sandbox:** extend the already executed isolated UID/FD/arithmetic boundary rather than label every parser safe. Causal model: trusted owner grants one bounded read capability → worker computes → broker validates bounded result → lease ends. Next attacks: compromised worker/Binder/Parcelable, child spawning, resource exhaustion, cancellation and provider mutation. Consumed-byte digest is not an immutable-source guarantee. The existing lifetime watchdog is not universal post-compromise containment.
4. **Productivity:** original native components and owned private storage provide useful functionality without pretending provider/transport gates passed. This turn adds Tasks; prior editor/notes/calculator/calendar export remain bounded. Next tasks: actual UI/AX review, editing/deletion/undo, due-date/reminder permission/lifecycle spikes, then provider-backed calendar/contacts. Inexact reminder submission is not delivery; no scheduler is fabricated.
5. **Performance:** measured first-party ART rules and actual profile-required startup are now real artifacts. Next: two physical reference devices, arm64/4KiB+16KiB, complete journeys, thermal/long-run and budget measurements. No hard hardware-floor block or emulator-grade substitution.
6. **Release:** all48 modules still have original unmet requirements. Compile37 migration (current34/36/36 min/compile/target), full browser/Office, physical/API37, legal, complete keyboard/AX, safe-save/provider/fault matrices and S1–S30 remain open. See FULL-SPEC-REVIEW.md and MODULE-TESTABILITY.md rather than interpreting97.92% as finished features.

No architecture can honestly erase legal, physical or authority gates. These alternatives provide testable boundaries and next experiments rather than bypasses or delegated stock-desktop/system-cast/external-Office replacements.


## Repeated measurement observation

The measured-profile pipeline also passed ate352ad7/run38698420603: generator and all3 measurements, with the identical855-rule profile hash. In that run TTID medians were1393.754506ms (none) and1415.739593ms (profile-required), reversing the first run's ordering. Neither pair of3-sample observations supports a causal speedup or physical budget claim. Full metric JSON, including frame-duration/overrun samples, is retained rather than selectively reporting a favorable run.


## Final exact native outcome and stopping boundary

**ddc7fec / [run38700509084](https://github.com/jyvplay/OSB/actions/runs/38700509084):79 PASS,0 FAIL,0 SKIP**, both debug builds, invoked JVM/contracts and lint success. All five Task storage/UI cases passed. Production and native-test sources are unchanged by the final evidence/documentation/model-proof additions; see `r17-native-source-match.json`. This is not a new native execution at the final documentation commit or reproducible-binary attestation.

The earlier6164211 Task UI timeout remains an unresolved intermittent observation. The diagnostic and full rerun passed without a production change; no causal repair/stability theorem is claimed. Highest-information next test: repeat actual fast input/Add interactions across IME visibility, density/font scale, activity lifecycle and full-suite ordering while retaining UI/disk diagnostics. Do not dismiss a reproduced failure as merely a flaky test.

R16's bounded plan is closed. R17 adds a working observed local Tasks path and verified storage/kernel components, not complete PR03 or all original features. Original full product success is NOT established.47/48 bounded subsets,0/48 fully accepted remains the honest result.
