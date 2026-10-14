# R4 — native editor continuation and verification frontier

2026-10-13. This continues R3; it does not assert a complete application. The new work is Kotlin/Compose only. No mock Android runtime, web replacement, receiver simulation or fabricated hardware result was used.

## Runtime honesty and executed result

Available and used: local repository/file tools, Git, `gh`, Python, cached Kotlin **2.2.21**, Temurin **17.0.9+9** runtime, real JVM/codec execution, GitHub primary-source retrieval, and supplementary syntax parsing. `gh run list` returned `[]` at the local-check checkpoint. No independent agent, theorem prover, external database, simulator, physical device or receiver was available. No full JDK/javac, Gradle executable or adb was on PATH; the previous Android SDK/dependency download restriction remains. The author of the tests is also the author of the implementation; compiler execution is not independent expert review.

**Local evidence:** 153,216 existing + 2,056 calculator + **8,916 editor = 164,188 assertions passed**. Existing CastV2 unchecked-cast warnings remain. These are repeated assertions, not 164,188 distinct feature tests. **15 Python tests passed.** Syntax checks are not Android compilation. At this checkpoint no Android build, APK, instrumentation, actual IME, accessibility, device or legal acceptance exists.

- Production: `core/documents/.../TextEditing.kt`, strict size validation in `TextDocument.kt`.
- Native integration: `feature/editor/.../EditorViewModel.kt`, `EditorScreen.kt`, `EditorTools.kt`.
- Executable checks: `core/documents/src/contract/kotlin/dev/fusion/documents/EditorChecks.kt`.
- Current-source evidence/hashes: [`verification/r4-jvm-contracts.txt`](verification/r4-jvm-contracts.txt).
- Actual failed pre-repair attack: [`verification/r4-pre-repair-counterexample.txt`](verification/r4-pre-repair-counterexample.txt).

## A. Formal contract

**Objects:** immutable, well-formed Unicode drafts with stable document IDs, monotonically increasing text revisions, UTF-16 selection offsets, bounded delta history, literal queries and immutable replacement plans. Source URIs, saved digests and frozen pending-copy payloads remain in the existing session model. Pure editing operations never write a provider/source file.

**Access model:** explicit user input and user-granted documents only. No network search, arbitrary code evaluation or regex execution on document contents. Kernels receive strings/values; the Android adapter owns disk/lifecycle/IME effects. No general access to other apps' tasks, files, keyboards or cameras is inferred.

**Counted resources:** at most 2 MiB UTF-8 per draft; eight tabs; at most 100 retained edits per tab and 4,194,304 retained UTF-16 delta units across undo+redo. That is at most 8 MiB of *logical two-byte payload* per tab, 64 MiB across eight tabs, not an actual heap or latency measurement. Oldest history is explicitly clipped; the largest single admissible edit still fits. Query ≤1,024 UTF-16 units; at most 10,000 non-overlapping matches. Excess results cause rejection, not partial replace-all. Output size is checked in both UTF-16 units and UTF-8 bytes before application.

**Quantifier order:** for every admissible draft and operation sequence within these bounds and before revision exhaustion, the intended contract is that each accepted edit returns a valid new draft without mutating the old one; undo/redo reverses retained edits exactly; rejected operations do not change content; a replacement applies only when its document ID, text revision and source text still agree, plus its selected span for selection-only replacement. This is a design invariant supported by the cited executable corpus, **not a machine-proved universal theorem**. The actual established statement is “for every input in the executed corpus, the listed assertions passed.”

Product success is stronger: there must exist an integrated Android implementation such that **all** required module behaviors and mandatory platform/device/legal/accessibility conditions pass their acceptance predicates. Passing one corpus cannot swap those quantifiers for “there exists one successful example.”

**Boundaries/units:** selections are UTF-16 offsets, columns are one-based Unicode code points, not grapheme clusters, pixels or tab-expanded visual columns. CRLF is one break; lone CR and LF are breaks. EOF after a final newline is an empty final line. Invalid coordinates fail rather than silently clamp to another line. Ordinary caret normalization never splits a surrogate pair. Literal matching performs no normalization; optional case insensitivity uses simple one-code-point upper/lower mapping, not locale-dependent or expanding case folding. Runtime Unicode-version differences require Android tests.

**Adjacent non-solutions:** regex editor, multi-encoding editor, unbounded large-file editor, diff/merge tool, process-persistent undo, journaled Files moves, universal Office lifecycle, stock external editor, exact grapheme/visual-column navigation, or a completed native desktop suite. None is substituted for this bounded slice.

## B. Outcome space and threshold

- **H+ full target achievable:** not disproved, but full delivery is not established. Requires actual integrated build/install and all mandatory feature, platform, security, license, accessibility, device and legal gates.
- **H− impossible under all stated constraints:** not established. Lack of a local SDK is an access limitation, not an impossibility theorem about the app.
- **H∂ supported boundary:** bounded editing/search/replacement kernels execute successfully; Android UI/IME/persistence integration is source-present but uncompiled at the local checkpoint. Regex, other encodings, larger files, complete lifecycle and mandatory full-product gates remain open.

No module, feature row, spike or E0 performance budget is promoted by this work.

## C. Retrieved frontier and hypotheses

Pinned commits, blobs, SHA-256 and excerpts are in [`research/r4-editor-frontier.json`](research/r4-editor-frontier.json).

1. OpenJDK 17 `Character`: UTF-16 supplementary characters occupy surrogate pairs; int/code-point APIs address characters beyond the BMP. This supports the offset model and rejects a char-only search/navigation assumption. It does not prove all Android versions have the same Unicode tables or make simple mapping equivalent to full case folding.
2. AndroidX `TextFieldValue`: selection and IME composition are separate state; supplying null composition can accept an in-progress composition. The adapter therefore retains incoming `TextFieldValue.copy(...)` composition during ordinary updates and deliberately creates a new value for explicit editing/navigation commands. The retrieved AndroidX main revision is commit-pinned, **not asserted to be the source of the pinned Compose BOM**. Exact dependency/API alignment and real IME behavior are unresolved Android verification interfaces.

No best-known global achievability/impossibility theorem for this entire Android product was established. These are primary API/source contracts, not imported proofs of app completeness. No upstream code or new third-party library was copied into the application in this slice.

## D. Negative space and modal-shortcut detector

The modal shortcut remains “source exists / many tests pass, therefore the module is complete.” `test_module_audit.test_source_presence_cannot_bypass_acceptance` changes an incomplete row to complete and requires the audit to reject it while common gates are unpassed. This test actually ran.

Additional false solution shapes and detectors:

1. Full string snapshots are automatically cheap — conditional bound below exposes the cost.
2. Maximum 100 undo steps means all 100 survive any edit sizes — payload cap and explicit clipping test.
3. An undo may reuse an old revision — ABA attack requires monotonic revisions.
4. Same text means same document — cross-document replacement attack.
5. Same text/revision means same selected target — actual failing selection-movement attack, now repaired.
6. Replace-all may commit only the first 10,000 matches — cap test rejects the whole plan.
7. Regex metacharacters can be interpreted in literal replacement — `$1` and `\n` stay literal.
8. Empty search means insertion everywhere — empty-query rejection.
9. Character count equals UTF-8 byte size — multibyte expansion rejection before application.
10. UTF-16 char equals Unicode character — emoji/Deseret tests and boundary normalization.
11. Case-insensitive means normalization or full case folding — composed/decomposed accent and Straße tests distinguish them.
12. Global locale may change search meaning — Turkish default-locale test.
13. Every newline is LF — mixed CR/LF/CRLF and trailing-empty-line cases.
14. Canceling an async operation may let a late replacement apply — opaque cancellation ticket plus immutable plan; Android scheduling remains untested.
15. Reconstructing a text field is neutral to IME composition — upstream source contradicts that assumption.
16. A no-op replacement deserves a fake undo entry — no-op revision/depth test.
17. Editing/undo may alter the pending save snapshot — existing immutable session/payload codec contracts remain in the full run; Android picker delivery is still unverified.
18. ViewModel survival through rotation equals process-death undo recovery — UI explicitly states history is not restored after process death.
19. Syntax parsing equals Android compilation — separate evidence labels and no APK claim.
20. An instrumentation test file equals real IME/TalkBack acceptance — authored test remains unrun, and semantic text input would not establish actual IME acceptance anyway.

## E/F/G. Mechanism portfolio, artifacts and candidate-specific attackers

These are distinct mechanisms and alternatives evaluated sequentially, not invented independent agents or independently completed products. Early alternatives retain their own evidence/gaps rather than inheriting a selected implementation's pass.

| Candidate | Construction and concrete artifact | Demonstrated/proved portion | Specific attacker / cheapest falsifier | Remaining interface and gap type |
|---|---|---|---|---|
| C1 full snapshots (alternative, not shipped) | Quantitative bound: 100 independent full snapshots × 2,097,152 UTF-16 units = 209,715,200 logical units per tab | Under an unshared-full-copy representation this exceeds the chosen 4,194,304-unit budget by 50×; clipping/deduplication changes the conditions | Repeated distinct maximum-size replacements; falsify a claimed 100-full-snapshot budget | A clipped snapshot design could work; selection of retention policy is a **local** gap, not proof of product impossibility |
| C2 minimal-span reversible deltas (selected) | `EditBuffer`, exact removed/inserted spans and before/after selections | Executed reversible Unicode/string corpus; clipped step/payload budgets; maximum single edit fits | Emoji sharing a high surrogate, full-document changes, branching after undo | IME grouping, Android heap/frame time and persisted undo remain **local integration** gaps |
| C3 runtime literal search oracle (alternative) | Independent `String.indexOf` non-overlap loop in exhaustive checks | Exact case-sensitive short-text expected ranges, independent of KMP code | Exhaust all binary strings length 0–7 / needles length 1–4 | Does not establish full Unicode-insensitive semantics or bounded Android latency; **local** scope gap |
| C4 code-point KMP (selected) | Prefix/failure table and bounded match-offset ring in `LiteralSearch` | Forward scan plus failure links gives O(N+M+K) construction: each fallback decreases matched length and increments are bounded by scanned points; executable oracle agreement | Long repeated-prefix text, supplementary case mappings, excessive results | Manual complexity argument, not proof-assistant certificate; runtime Unicode/Android integration **local** |
| C5 optimistic revision-bound plans (selected) | `ReplacementPlan` with ID, revision, exact source and optional selected-span guard | Executed stale, cross-tab, ABA and moved-selection rejections; one-step replace-all undo | A→B→A text cycle, identical content in another tab, caret/selection races | Native callback ordering and UI confirmation integration **local**; passing this does not solve all storage transactions |
| C6 strict byte/offset accounting (selected) | Allocation-free `encodedSize`, surrogate-boundary selections, output projection before building | Actual JDK encoder comparisons, malformed-surrogate rejection and byte-expansion tests | Multibyte content fits character cap but exceeds byte cap; replacement multiplication exceeds Int range | No full Unicode-normalization/grapheme editor or Android font/IME proof; **local** |
| C7 mixed-newline position scanner (selected) | `TextNavigation.offset` consumes CRLF once and code points within a line | Explicit expected offsets, EOF, empty text and out-of-range cases pass | Trailing CRLF, lone CR, emoji before column, enormous requested line | Visual columns/tab widths and actual cursor reveal **local** |
| C8 effect separation/native IME state (source integration) | ViewModel-owned per-tab buffers/fields; Default-dispatcher search; opaque cancellation ticket; native scrollable tools/confirmation | Source construction only; no Android compile or execution proof | IME preedit followed by find/undo, dismissal during work, rotation, 200% text and Switch/Voice | Android compiler/lifecycle/AX/device gates; solving the full product from this alone is **equivalent to the original unmet integration problem** |

C1 returns a quantitative counterexample, C3 an executable independent oracle, and the other branches executable production designs plus explicit falsifiers. None returns a status-only “standard step.”

## H/I. External execution, actual repair and renewed attacks

The initial **8,913-assertion** editor corpus passed. A new test then prepared selection-only replacement for the first `one`, moved the selection to another `one` without editing text, and demanded rejection. It **failed in the actual JVM**: text revision checks alone did not bind selection-only intent.

Repair: the plan captures and checks the selected span for selection-only replacements. New patch-targeted attacks ensure reversing the direction of the *same* selected span remains valid and moving the caret does not incorrectly invalidate a replace-*all* plan. The entire existing/window/document/calculator/editor corpus was then rebuilt and rerun: **164,188 assertions passed**, editor count **8,916**. The historical failing binary was separately rerun solely to preserve its actual error output; it is not current-source evidence.

Added `ShellSmokeTest.editorLiteralReplaceIsOneUndoableDraftEdit`: real native tool UI → confirmation → undo/redo → Activity recreation. It is authored but **uncompiled/unrun**. Syntax and source review cannot close that gap. No repaired Android candidate inherits a JVM pass as its own acceptance.

## J. Strongest artifact, completion and next experiment

**Strongest verified artifact:** compiled production bounded editing kernels, executable adversarial corpus, source-bound evidence, and a fail-closed module ledger. **Exact gap:** no integrated Android compilation/installation yet, plus incomplete full module scope and all mandatory platform/device/legal/accessibility gates.

**Included modules: 48. Fully specification-complete: 0 (0%). Still requiring completion/acceptance: 48 (100%).** This measures acceptance, not elapsed effort or source-file coverage. Architecture discrepancies remain in [MODULE-COMPLETION.md](MODULE-COMPLETION.md); no module was added just to inflate progress.

Highest-information next test is a real JDK17/SDK-enabled Android build worker, then actual native UI tests:

```sh
# From android/
./gradlew :app:assembleProDebug :app:assemblePlayDebug
./gradlew :app:lintProDebug :app:lintPlayDebug
./gradlew :testing:pilotContracts :testing:calculatorContracts :testing:editorContracts
./gradlew :app:pixel6Api34ProDebugAndroidTest
```

On a disposable laptop AVD/API34+ device: use native Editor; type Chinese/Japanese composing text and emoji with an actual IME; find/replace/undo; reverse selection; cancel during search; resize/rotate; inspect actual cursor reveal; run keyboard/TalkBack/Switch/Voice at 200% text; kill/relaunch after checkpoint and verify contents survive while undo history does not falsely reappear. Test actual provider failure/callback paths separately. No APK, paid-release readiness, physical budget or full-module completion follows from the current local results.
