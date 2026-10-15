# R12 continuation — real testable subsets, not full specification delivery

This supplements `R12-ENGINEERING.md`; it does not replace or relax the original specification, licensing rules, S1–S30 or release gates. No receiver, player, Office engine, remote peer or Android platform was simulated. No new product dependency was vendored. The Media module reuses the Activity Compose dependency already present in the app.

## Current evidence and numerical standard

- **30/48 modules (62.50%) established as substantial testable subsets**, up from23/48. Five new production kernels were compiled separately and executed; two existing Android inventory modules now also have actual passing native observations.18/48 (37.50%) remain not established. The seven additional positive rows are not seven complete features.
- **0/48 (0%) fully specification-accepted;48/48 (100%) still outstanding.** All30 spikes remain unpassed. Productivity0/13, requested Forge0/13, browser a–s0/19 fully accepted. No percentage estimates effort or feature coverage.
- **2,394 new host assertions** across settings, subtitles, pixels, backup content, launcher catalog and UTF8/CSV repairs. `verification/r12b-subsets.txt` binds executed sources/tests to SHA256.
- **1,188 cross-language checks**, seed120014, using Python byte framing, matrix rotation, time intervals, catalog ordering, strict UTF8 and CPython CSV writer/reader against actual compiled Kotlin. `verification/r12b-interop.json` records corpus/script/compiler-receipt hashes. These are different implementations, not independent human authorship.
- **183,179 existing Kotlin assertions rerun**, with13 separately compiled libraries; both negative cross-module compilation attacks still rejected. `verification/r12b-full-jvm.txt`.
- Actual previous/current-source compilation reproduced both old text defects, then demonstrated their repair with identical inputs: FF was previously accepted as UTF8; a quoted multiline CSV cell was previously split/lost. `verification/r12b-text-counterexample.json` and `tools/probe_text_regression.py`.
- **Native first candidate a571531:46/46 PASS**, zero failures/skips, run38679461154. Both flavors, invoked unit/contracts, lint and APK upload succeeded. This is not evidence inherited by later repairs.
- Candidate **67fbd39**, run38680047085: **49/49 PASS,0 FAIL,0 SKIP**, both flavors/unit/contracts/lint/APK upload passed; companion38680052681 succeeded. Includes CSV and both actual inventory cases. However prior25572e6/run38679917430 had46 PASS/1 FAIL: Editor toolbar visibility at200% font;67fbd39 passed without a targeted Editor change, so that pass alone did not establish a fix. The initial toolbar repair2ca27d4 failed48/50: its new long-text visibility attack and a Notes semantics-thread error. Candidate6fd6695 failed49/50 plus its missing source fingerprint. The new candidate0a23219 adds explicit IME resize, bounded toolbar/body shares, post-font idle synchronization and an explicitly enabled/restored software-keyboard fixture. **Latest0a23219/run38682595367:49 PASS/1 FAIL/0 SKIP.** Both debug flavors, invoked unit/contracts, lint and APK upload passed; companion38682599375 succeeded. The remaining failure is the new Editor long-text200% test waiting10 seconds for an actually visible IME, before its toolbar assertion. This is not a full native pass, and it does not prove the IME-visible toolbar behavior.

The census is an assistant-authored lower bound with reproducible source/hash checks. A negative row may contain substantial code without an adequate executed pairing. A narrow parser, inventory or container is not the enclosing complete feature. See `MODULE-TESTABILITY.md` for every module and exact remaining gap.

## Runtime honesty

Actually used: local filesystem/Git, Python3, Kotlin2.2.21 compiler with JRE17.0.9, JVM execution, CPython CSV, SHA256/Git-blob verification, GitHub CLI/API, GitHub Actions JDK17/SDK36/Gradle, and actual Google API34 managed emulators. Host tools run real byte/pixel/file operations, not substituted Android APIs. Tests call production public APIs across separately compiled jars. Gradle also has an explicit `:testing:subsetContracts` target.

No independent agents, theorem prover, external expert, physical device, OEM/API36/37 laboratory, Office payload, receiver hardware or legal reviewer was available or used. No local Android SDK/ADB build is claimed. Native CI is a real external execution environment, but the tests remain authored here. No comprehensive literature search or mathematically best-known global impossibility theorem is claimed.

## A. Formal model and success contract

### Objects, access and adversary

Admissible objects are ordinary bounded JVM strings/maps/lists/owned IntArray/ByteArray samples, complete finite byte streams, actual Android software Bitmaps and caller-observed launcher targets. Inputs are not mutated concurrently during a call; malicious same-UID code can violate ownership and is outside this kernel contract. Android inventory observations assume no hotplug/configuration change between probe and comparison. No invented installed apps, playback clock, notifications or hardware flags are supplied to users.

The adversary chooses encoded lengths, bytes, Unicode, duplicate identities, dimensions, crop coordinates, rotation counts, cue overlap, missing catalog entries and mutable aliases before/after calls. Tests include recomputed checksums, not just random corruption. It does not control the compiler or overwrite tested jars. A hostile content provider can block inside a read: the byte cap does **not** prove wall-time termination or process isolation.

### Quantifier order and acceptance

For each declared kernel K, first fix the grammar, bounds and ownership assumptions. Then for every input x in that domain and every adversarial mutation of supplied data *outside* the call, K(x) must either produce the specified owned output without altering the source, or explicitly refuse invalid/over-budget input. There must exist reproducible concrete checks before a module earns the subset classification. An implementation that succeeds only on a friendly selected input does not satisfy the intended universal contract.

This is a contract, not a universally machine-proved theorem. Executed tests establish the listed finite cases and metamorphic/cross-language corpus; source review supplies the stated bounds. Full product acceptance additionally quantifies over every assigned requirement, required platform/device/provider, AX interaction and legal/safety gate. That conjunction is false today; no component-level test count substitutes for it.

### Parameters, units and tolerances

| Kernel | Explicit domain/budget | Success semantics / boundary |
|---|---|---|
| Settings |1–64 definitions,≤16 aliases each; ID regex and bounded titles;≤4096 UTF8 bytes/value;≤65536-byte canonical frame; query≤256 UTF16 units | Sorted known IDs, canonical padded Base64, strict UTF8, no duplicates/unknown keys. Decode only inspects; does not apply preferences. Header-only is a valid empty partial snapshot, so truncation to a record boundary is not generally detectable. No authentication. |
| SubRip |≤1MiB consumed bytes and canonical export;≤4096 cues;≤32 lines/cue;≤8192 counted UTF16 units/cue including line separators | Strict UTF8 with optional BOM, LF/CRLF, consecutive indices, fixed HH:MM:SS,mmm grammar; hours00–99, minutes/seconds00–59; start<end. Half-open [start,end) milliseconds, exact integer comparisons. Multiple active cues remain in source order. Literal text, not HTML; no playback. |
| ARGB |positive dimensions, product≤4,194,304 pixels; exactly one Int/sample; half-open nonempty in-bounds crop | Exact integer sample permutations. Turns modulo4, including negative/Int.MIN_VALUE. Owned snapshots prevent external aliases.16MiB/sample array, but several arrays coexist; **not** a16MiB process-RSS bound. Android adapters may convert colors/premultiplication. |
| Backup content |≤64 entries,≤256KiB/file,≤8MiB aggregate; bounded FBC1 frame; restricted ASCII .md basenames, no traversal/case collisions/DOS device stems | Canonical uncompressed framing plus SHA256 damage detection. Whole frame is structurally checked before entries return. Returned buffers are caller-owned, not a durable immutable store. No compression, encryption, authentication, filesystem publication or legacy migration. |
| Launcher |≤128 pins;≤4096 supplied targets; bounded key/title/query lengths | Pin/unpin/final-index reorder; missing targets omitted from display but retained as pins; known pins first, other running targets sorted by identity; root-locale search and stable ties. No package discovery, persistence or launch side effect claimed. |
| CSV |≤2Mi UTF16 input units;≤10,000 records;≤256 fields/record;≤65,536 UTF16 units/cell | Comma/double quote, doubled-quote escapes, quoted newlines, LF/CRLF record separators; no trimming/blank loss. Bare CR separators, misplaced/unclosed quotes, post-quote data refused. Blank record is one empty field. No spreadsheet formula evaluation or byte-preserving export. |
| Inventories |actual API34 platform metadata; fixed observation window | Compare RAM within0.000001 GiB and other fields exactly to platform observations. Unknown NPU/session/physical display-out stays unknown. Codec flags are declarations, not initialization/throughput proof. |

Limits are per invocation, not concurrent-owner/process budgets. No physical power-loss, secure erase, malicious provider wall-time, renderer compromise or APK-wide memory proof is inferred.74 local Python test methods pass; the new receipt-collector tests are publication tooling, not additional Android feature cases.

### Adjacent problems not substituted

Snapshot parsing is not settings application or DataStore migration; SRT inspection is not media playback; ARGB permutation is not decoding/HDR/color fidelity/HEIC encoding; FBC1 is not encrypted transactional backup; pin reconciliation is not installed-app discovery or Home/widgets; CSV parsing is not Office; runtime inventory is not a benchmark or successful codec initialization. Existing remote WebViews are not the complete Fusion Browser; isolated UID is not a complete hostile-parser CPU/RSS/wall/filesystem/network sandbox.

### Inspectable bounds and proof sketches (not theorem-prover output)

For a clockwise quarter-turn, `(x,y) -> (h-1-y,x)` has inverse `(u,v) -> (v,h-1-u)` on the admitted rectangular domains. It is therefore a bijection over sample indices. Product validation uses Long before any Int allocation/index product; the admitted product is≤4,194,304, so array indices remain in Int range. This establishes the coordinate/permutation argument under ordinary-array ownership; tests then check the implemented mapping, not merely an abstract model. Android color conversion remains outside this argument.

Settings encoding is injective on admitted key/value maps: IDs are distinct, known ASCII identifiers; the delimiter cannot occur in IDs; canonical Base64 delimits arbitrary strict UTF8 values; sorted records give one representation. Decode re-encodes and requires exact byte equality. This rules out alternate padding/order/unknown-key representations, but **not** a valid shorter partial map or active attacker producing another canonical map. There is no authenticated transport theorem.

Backup decoding checks outer bounds and digest before interpreting bounded counts/lengths, builds entries locally, and returns only after the final no-trailing-content check. Thus a malformed frame cannot yield a partial successful return through this API, and this API never publishes a file. This is intentionally not a theorem about the legacy restore engine, power failure, encryption, concurrent source mutation or filesystem rollback. Valid-content checksum forgery is executable evidence of the authentication boundary.

CSV scans input once with explicit quoted/closed states; only separators outside quotes finish fields/records. Every append checks cell length and every completed field/record checks its count before insertion. The collection size is finite under the input/cell/record bounds, but object overhead and concurrent calls prevent interpreting the input limit as an APK-wide RSS bound. The old-source counterexample and CPython corpus check actual implementation behavior independently of this argument.

Two platforms can publish the same capability declarations while one fails initialization or throughput. An inventory observer receives the same data in either case, so that information alone cannot distinguish usable hardware. This is an information-model limitation/causal countermodel, not a claimed executed physical-device experiment.

## B. Outcome space and evidence thresholds

- **H+ (bounded subsets):** the declared transformations/inventories are implementable with available platform/JVM APIs. Threshold: public API compilation, behavioral fixtures, negative/boundary tests, finite cross-language corpus, then actual Android execution for Android claims. The five new kernels and both inventory paths have executed evidence; native evidence is revision-specific above.
- **H− (specific false contracts):** the unchanged replacement-decoding helper cannot validate UTF8 (actual FF counterexample); the unchanged line-by-line CSV splitter cannot preserve multiline fields (actual prior-source execution); an unkeyed recomputable checksum cannot authenticate content (valid re-forgery deliberately accepted and recorded). These are narrow negative results, not impossibility of the whole product.
- **H∂:** full coverage changes with format variants, larger inputs, concurrent hostile owners, caller-held capabilities, provider blocking, platform color conversions, engine threads and hardware/legal approval. LOK's upstream Android route prevents declaring native Office inherently impossible, but does not resolve package licensing, storage, sandbox or fidelity integration.

The configured native suite excludes the manual VisualCaptureTest; executed-case0 SKIP does not mean every possible manual test ran. No H+ is promoted to whole-feature acceptance. No blocked gate is relabeled passed because source now exists.

## C. Retrieved frontier and exact open interfaces

New primary retrievals, Git-blob and SHA256 verified via GitHub API, in `research/r12b-primary.json`:

1. **AOSP Bitmap**, commit299fe6f5d6fc6f1af7c3411dcf4e5efdf7217368. `getPixels` returns non-premultiplied packed ARGB in sRGB; creation APIs describe sRGB outputs. Hypothesis: these are platform API semantics, not original image metadata/fidelity. Therefore exact pure-array permutations do not prove HDR/wide-gamut/translucent Bitmap roundtrip fidelity. Open interface: explicit conversion/loss reports and format-specific decode/edit/export tests.
2. **AndroidX Media SubripParser**, commit8c6678b657ede1e7883fc164ef73ed483c7796c3. Its timing grammar accepts optional hours/milliseconds and its implementation uses HTML interpretation. Hypothesis: this is a broader library implementation, not a normative requirement that all SRT be interpreted as HTML. Our grammar deliberately refuses some variants and displays literal text. Open interface: reviewed dialect/styling admission, actual player clock/renderer and license closure before adding dependencies. No Media3 implementation was copied into product code.

Prior pinned AOSP SurfaceControlViewHost/AtomicFile, Linux seccomp, LibreOffice Android/LOK and POI records remain in `research/r12-primary.json`. Those source results and prior actual experiments are reused **as historical evidence**, not presented as re-executed this turn. They establish routes and compatibility constraints, not full hardware/legal/security acceptance. There is no claim that these finite retrievals exhaust the literature or establish a global best-known theorem.

## D. Negative space — tempting but false solutions and detectors

| False solution shape | Detector / present status |
|---|---|
|1. Count a new folder as completed | Census rejects positive rows without production/check/command; whole acceptance stays0. |
|2. Equate a build with passing native cases (modal shortcut) | `test_cases_not_inferred_from_success`; collector requires actual case annotations and unique statuses. A successful assemble with no cases gives zero cases, not a pass. |
|3. Decode arbitrary bytes using replacement characters | FF/overlong/surrogate/out-of-range/truncated UTF8 attacks, actual old/new compilation and300 Python comparisons. |
|4. Split CSV by lines and discard blanks | Actual old-source multiline counterexample; blank record/trailing cell/embedded CRLF fixtures and CPython corpus. |
|5. Silently repair malformed CSV quotes | Unterminated/misplaced/post-close data attacks must refuse. |
|6. Claim any header-truncated settings snapshot is corrupt | Explicit valid header-only empty-snapshot test; claim withdrawn, not a decoder failure. |
|7. Treat an alias as an importable setting ID | Unknown/alias IDs, duplicate keys and noncanonical Base64 rejected. |
|8. Assume default locale is stable | Turkish default-locale settings/launcher tests. |
|9. Treat snapshot decode as settings apply | Native inspection test changes actual preference, inspects old snapshot, confirms current preference unchanged. |
|10. Use a single subtitle at a time or inclusive end | Overlapping cues, exact start/end, Long.MAX_VALUE position. |
|11. Treat subtitle markup as executable rendering | Literal `<b>` Compose text-input test; no HTML API used. |
|12. Use declared file size as read budget | `SubRip.read` counts actual bytes and refuses max+1 input; no provider-size metadata trusted. |
|13. Call a manually selected subtitle position a media clock | UI explicitly says inspection, not playing; no transport/playback controls invented. |
|14. Rotate only square examples | Hand3×2 expected matrices, seeded non-square permutations, four-turn/reflection identities. |
|15. Reuse mutable caller arrays/lists as internal state | Mutate input/output aliases; later results must remain intact. |
|16. Multiply dimensions as Int before checking | Int.MAX_VALUE dimensions refused before allocation; Long product guard. |
|17. Claim Android packed colors preserve every original image bit | Primary API evidence contradicts that scope; native opaque-sample test explicitly narrower. |
|18. Use checksum as authentication | Recompute SHA256 over deliberately changed valid content; decoder accepts, proving authentication is absent. |
|19. Stream archive entries directly over user files | New decoder has no filesystem API and returns only after full validation. Legacy restore remains blocked. No transactional claim. |
|20. Accept traversal/case aliases/reserved device names | Resealed frames, ../, slash/backslash, case-collision and CON/AUX/COM/LPT attacks. |
|21. Allocate from encoded counts/lengths before limits | Resealed Int.MAX_VALUE count/name fields refused; per-file and aggregate maxima exercised. |
|22. Drop pins whenever an app disappears | Missing-target/reappearance test preserves saved ordering. |
|23. Invent NPU/display-out/session capability from SoC/model | Both actual runtime inventory cases passed at67fbd39; unknown fields stayed null/unknown. This does not validate physical capabilities. |
|24. Advertise codec declaration as usable casting | Inventory tests compare declarations only; no initialization or receiver acceptance inferred. |
|25. Inherit a previous candidate's native pass after repair | Every native-changing revision invokes fresh CI; results retain exact commit/run/cases. |
|26. Treat an unbounded provider read as cancellable wall-time containment | Explicit excluded boundary; next test needs real stalled provider/broker watchdog. Coroutine cancellation alone is not accepted proof. |

## E–G. Mechanism portfolio and candidate-specific adversaries

The five new kernels began as separate module sources and compile into separate libraries; no shared permissive parser or fake Android layer was used. Existing inventory paths remain separate. This is implementation independence, not independent agent authorship.

| Candidate / construction | Concrete artifact and established portion | Structure-derived strongest attack | Unresolved interface / cheapest next test / gap scale |
|---|---|---|---|
|1. Canonical keyed snapshot | `core/settings/.../SettingsSnapshot.kt`; sorted exact-key serialization, strict UTF8, locale-stable search; host fixtures/interoperability | Duplicate/alias IDs, valid empty-prefix truncation, Unicode and over-budget aliases before copying | Atomic application/schema migrations/undo; real interrupted preferences/DataStore migration test. Local kernel gap; full settings still wider. |
|2. Timed text interval sequence | `feature/media/.../SubRip.kt`, real picker and `SubtitleInspector`; parser/export/overlap lookup and consumed-byte limit | Nonsequential indices, simultaneous cues, end boundaries, malformed bytes, slow provider | Actual decode/playback clock/lifecycle and broader dialect; real media file/seek test plus stalled provider. Local parser gap; player remains larger. |
|3. Integer coordinate permutation | `format/image/.../ArgbImage.kt`, `ImageOps` Bitmap adapters | Non-square/overflow/negative-turn/alias/alpha samples; packed-color conversion boundary | Color/fidelity-aware editor/export and isolated bounded decoder; real translucent/wide-gamut fixture with explicit loss report. Local permutation complete only within declared domain. |
|4. Whole-content staged framing | `feature/backup/.../BackupContent.kt`; canonical uncompressed bounded frame, validates all before returning | Valid-digest malicious names/counts, case collisions, aggregate exhaustion, checksum forgery | Authenticated encryption/version migration/private staging/transactional restore and rollback; real fault after each publish with source preservation. Full backup transaction gap not solved by codec. |
|5. Persistent identity order versus observed membership | `feature/launcher/.../PinnedCatalog.kt`; immutable pin/reorder/reconcile/search | Disappearing/reappearing target, duplicate identity, mutable catalog/pins, locale ties | Durable pins, actual package/window inventory and host shelf controls; uninstall/reinstall + reorder/restart test. Local ordering gap; full Home/widgets/AX larger. |
|6. Grammar state machine + strict decoder | `format/native/.../TextFormats.kt`; real CSV preservation/refusal and UTF8 correction | Embedded separators, empty last record, post-quote bytes, boundary counts, invalid scalar encodings | Streaming file lifecycle/format-router/fidelity reports; actual CSV file opened/edited/Save-a-Copy revalidated. No Office substitute. |
|7. Direct platform observations with unknown propagation | `CapabilityProbe.kt` and new `RuntimeInventoryDeviceTest`; actual API34 case passed at67fbd39 | All-null report, inferred NPU/desktop, stale hotplug, RAM units wrong | OEM/API36/37/physical matrix, actual usable features; hardware connect/disconnect and real initialization. Observation subset, not compatibility proof. |
|8. Codec inventory partition | `HwProbe.kt` and same device test; actual API34 case passed at67fbd39 | Always-empty list, encoder/decoder inversion, hardware declaration misrepresented as throughput | Codec initialization, encode/decode/hardware load and receiver matrix; cheapest real encoder start then sustained budget. No S17–21 pass. |
|9. Browser process-hosted native surfaces | Prior actual remote-pixel/input/resize/profile artifacts plus fresh regression | Above-host surface covering chrome, IME/AX, hostile origin/profile, privileged same-UID worker | Own compositor integration and separate-UID/permission broker; exact next test below. Full browser still original-scale. |
|10. Engine packaging/storage route | Pinned actual LOK Android and POI source plus causal dependency map below | Thread creation rejected by inherited filter; engine opens writable sidecars/temp paths outside grants | Build/license/storage adapter/fidelity/lifecycle matrix. Route exists, but full native Office remains original-scale. |

## Alternative designs for the four major gates

### Browser — separate composition and trust problems

Keep own native window/chrome ownership and attach remote document surfaces only within explicit content rectangles. Existing above-host embedding passed pixels/input, not arbitrary overlapping compositor chrome. The cheapest discriminator is a real multiwindow test with one browser surface overlapped by another window's title bar, injected hits on both sides of the boundary, IME resize and TalkBack navigation. Do not change assertions to DOM-only clicks.

Same-UID process/profile separation cannot protect data against a compromised same-UID engine. A first-party separately installed engine APK with a distinct UID and a narrow signature-protected Binder surface/download/permission protocol is an alternative to falsely calling same-UID profiles a security boundary. This is a concrete architecture condition, not a built browser. It needs install/version negotiation, explicit capability grants, origin/prompt binding, lifecycle and hostile-peer tests. An isolated-service WebView cannot simply be assumed to initialize with arbitrary app storage; probe that separately before selecting it.

### Office — real engine plus writable state, not extraction-as-editing

The pinned upstream LOK Android route supplies an actual native engine path, while the prior source record also identifies threads and writable profile/temp needs. Two conditional constructions remain viable to investigate: (a) a separately licensed first-party engine APK/private UID with per-job bounded writable workspace and capability-brokered inputs/outputs; (b) a real engine FD/VFS/storage adapter, including sidecar/profile/font/temp requirements, inside an isolated worker. Merely renaming an FD as a file URL does not implement this adapter.

For either route: audited source/transitive license closure before payload admission; macros disabled; real DOCX/XLSX/PPTX/ODF corpus open/render/edit/Save-a-Copy/reopen; fidelity/loss reports; denied ambient paths/network; fresh job lifetime/watchdog. POI streaming/headless extraction is useful separately but not native Office UI, and its temp files must also be included in the policy. No external viewer or cloud engine is counted as implementation.

### Sandbox — thread-aware lifecycle, not weakening fork denial silently

The prior fresh single-thread LP64 host seccomp/RLIMIT experiment is evidence only for its declared host regime. Denying clone also denies needed new engine threads. A candidate thread-group policy could permit only audited same-process thread creation while denying child-process creation, then use a fresh worker per job plus parent-enforced wall/RSS supervision. Its security depends on actual clone/clone3 flags, ABI, runtime startup and Android kernel behavior; it is **not** accepted based on this causal model.

Highest-information test: initialize the real chosen engine in an Android worker, attempt forbidden process creation and required threads, then test fork/exec/reset, CPU across threads, stalled I/O, memory growth, watchdog death and cleanup. CPU offloaded to services/GPU remains a separate resource. Existing isolated UID/FD/socket/watchdog tests help, but do not establish this complete policy.

### Productivity — finish usable operations without inventing privileges

New SRT inspection, CSV preservation and settings snapshot inspection are real operations with stated boundaries. Notes already supplies actual local Markdown/draft recovery. For remaining tasks/clocks use app-owned storage and inexact scheduling rather than exact-alarm privilege; Calendar/Contacts use explicit scoped platform adapters/pickers with API37 verification; clipboard remains explicit in-module or an opt-in permissible IME, not fabricated global access. Each needs real process-death, revoked-permission and keyboard/AX cases before delivery claims.

Backup's new content layer must not be connected to the legacy unsafe restore merely to make a button active. Next implement a reviewed versioned authenticated envelope, stage/validate every item, then journal non-overwriting publication/rollback under cross-owner locking, with fault injection at every transition. S7 remains blocked until the required acceptance evidence exists.

## H–I. Verification, failures and repairs

1. Initial settings rejection test incorrectly required a valid six-byte empty snapshot to fail. The format intentionally permits partial/empty exports and never auto-applies them. Kept the behavior, corrected the overclaim, added explicit empty/extra-line/unknown-key cases. No data-integrity guarantee is inferred from canonical text alone.
2. Source review found registry/pin input lists copied before count checks. Moved count checks ahead of defensive copies and added over-count definitions/aliases/pins attacks. Also refused reserved DOS device stems instead of calling every ASCII basename portable. Full affected suite rerun; cross-language resealed-path attacks added. No prior pass inherited.
3. Replaced actual broken UTF8 helper and CSV splitter. Executed both baseline and repaired source with the identical attack inputs, then reran the entire existing host suite and expanded held-out corpus. CSV rejects ambiguous syntax instead of silently normalizing it.
4. Generated source inventory initially became stale after adding files: source check and one Python test failed. Regenerated the real source inventory; did not weaken the checker. Fresh checks passed afterward.
5. Native CI is explicitly revision-bound: a57153146/46;308272246/46;25572e646/47 (Editor visibility failure);67fbd3949/49. Raw cases and failure detail are retained. Full log download for25572e6 failed at the Actions results host (EOF); the Checks annotation did expose the exact assertion at ShellSmokeTest.kt:124. No missing log content is invented.
6. The intermittent Editor failure occurred at the New toolbar button after typing at200% font. Source inspection identified a shared toolbar/document scrolling region; focus/IME bring-into-view is a causal hypothesis, not an isolated proven root cause. Moved actions outside that scroll region, bounded their separate scroll viewport, retained existing assertions and added immediate toolbar visibility plus a60-line200%-font tab-creation attack. The new attack rejected this first repair:2ca27d4 had48 PASS/2 FAIL and6fd6695 had49 PASS/1 FAIL. The long-text action was still not displayed; another failure surfaced a Compose SnapshotStateObserver thread error while reporting semantics. The later49/49 run did not eliminate this gap.
7. Source review then found unspecified window IME adjustment. Candidatebd85d02 requests `adjustResize`, reserves40%/60% toolbar/body viewport shares, waits for UI idle after font changes, and requires an actually visible IME in the new test. Candidate0a23219 also explicitly enables and restores software-keyboard display when a hardware keyboard is attached. All assertions retained. bd85d02/run38682543803 had48 PASS/2 FAIL (Editor replace/recreate content assertion and Editor200% semantics-thread error).0a23219/run38682595367 had49 PASS/1 FAIL: actual IME visibility timed out. The previous failures did not recur in that run, but their root causes are not independently established. Root panning remains a hypothesis, not a proven isolated cause; the stronger IME-visible interaction contract is still unverified.
8. The first Editor repair commit6fd6695 accidentally omitted its generated source-inventory fingerprint; the following2ca27d4 included it. Source validation is intentionally strict. This publication mistake is not hidden or attributed to Kotlin/platform behavior.

`tools/collect_native_evidence.py` refuses incomplete runs, malformed/duplicate case annotations and unknown status syntax. It stores raw run/jobs/annotations/artifact metadata; test counts are not inferred from a successful build. Six Python tests attack this collector. APK artifact digests identify outputs, not product acceptance.

## J. Stop boundary and next experiment

This turn returns executable Kotlin, real native integration/fixtures, reproducible host/cross-language experiments, actual prior-source counterexamples and recorded native outcomes—not a declaration that all remaining work is done. No real budget is claimed infinite. The latest complete native run remains red at49/50; the new IME requirement is retained rather than skipped, weakened or called passed. Further blind full-suite reruns without additional observability would provide less information than the targeted experiment below.

Immediate highest-information test: query installed/enabled/default emulator IME, capture actual root insets/window bounds, and drive the Editor using real injected touch/input through a plain ActivityScenario/UiAutomation path. Separate an unavailable/suppressed keyboard or test-input-path effect from application resize/panning behavior, then repeat the same200%-font long-text toolbar attack and the full suite. This is a proposed discriminator, not an executed result.

Other highest-information next tests: real browser overlap/chrome/IME/AX; selected Office engine build plus denied ambient I/O and real edit/reopen corpus; Android thread-compatible sandbox enforcement; transactional backup fault matrix; subtitle stalled-provider/lifecycle and actual player integration. API37/16K/physical devices/human AX/legal/hardware acceptance remain outside the available verifier set. Exact release acceptance remains false.

## Try it in Android Studio Rabbit1

Open `OSB/android` with the compatible pinned toolchain in `BUILD.md`, select `proDebug`, and use a disposable emulator. Open **Media**, acknowledge its experimental status, then **Inspect subtitle file…** and choose a UTF8 .srt using the system picker. No image permission is required for this subtitle operation. Enter milliseconds to inspect active literal cues; this does not play media or modify the source. Unsupported, invalid or >1MiB input is refused and the previous preview retained.

Settings/image/backup-content/launcher kernels have reproducible targets and selected Android adapter tests; this does not mean new end-user settings import, image editor, backup restore or shelf pin controls were exposed. Backup remains blocked. Existing Notes/Editor/Calculator remain available under their documented scope.

## Exact native progression

See `verification/r12b-native-results.json` and per-run raw jobs/Checks/artifact metadata. APKs at [latest compiled candidate0a23219](https://github.com/jyvplay/OSB/actions/runs/38682595367) are debug-only and have a known failing IME test. The earlier [49/49 candidate67fbd39](https://github.com/jyvplay/OSB/actions/runs/38680047085) predates the stronger IME test and is not evidence that this gate passes.
