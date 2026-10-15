# R15 — executable module subsets and actual isolated-parser integration

## Runtime honesty and progress

**44/48 modules (91.67%) meet the existing bounded-testable-subset standard;4/48 (8.33%) remain unestablished. Fully specification-accepted modules:0/48 (0%); unfinished:48/48 (100%).** Five newly qualifying modules: core model, Lens, capture, Messages and Vault. Office/sandbox work extends already-counted modules; it does not add another module to the numerator. These are not percentages of feature coverage, Office parity or product readiness.

Executed locally: **5,510 R15 assertions and700 cross-language cases passed**. R14 was separately rebuilt and rerun: **1,893 assertions and600 cross-language cases passed**. **74 Python tests passed.** Most R15 assertions exercise coordinate boundary combinations; assertion volume is not feature volume. Six production library units were compiled separately, then public consumers were compiled and executed. Source, consumer, runner and JAR hashes are retained.

Actual tools: Git/gh/GitHub Actions/API, Python3, restored Kotlin2.2.21/JRE17.0.9, host cryptography46.0.3/OpenSSL, Python fractions/struct/zlib/urllib.parse/decimal. The compiler archive's published SHA512 was checked. Two independent regression commands were run concurrently; no independent agents were used. No local Android SDK/device, prover, physical lab, expert accessibility review or counsel was available. Managed-device results below are actual remote Android emulator results, not physical acceptance. No mock peer, fabricated engine, TypeScript product or simulator was added.

Receipts: `verification/r15-kernel-contracts.txt`, `r15-interop.json`, `r15-python.txt`, `r15-r14-regression.txt`, `r15-r14-interop.json`, native receipts and the counterexample below. All production changes are on `arena/d4c65778-osb`, starting from merged PR3. No additional merge was requested or performed.

## A. Formal model

### Admissible objects, access and resources

| Mechanism and production artifact | Domain, units, budgets and tolerance | Authority / exclusions |
|---|---|---|
| `feature/lens/.../ViewportTransform.kt` | Source/viewport dimensions1..32768; quarter turns0..3; optional horizontal mirror after rotation; FIT/CROP. Continuous image-edge coordinates, not pixel indices. Finite in-domain points. External/roundtrip tolerance1e−6 coordinate units; inverse letterbox boundary allowance1e−9 source units | Pure geometry. No camera capture, sensor/metering mapping, interpolation, segmentation, relighting or virtual camera. Forward crop coordinates may lie outside viewport; inverse only accepts viewport points |
| `feature/capture/.../RecordingTimeline.kt` | Single-owner monotonic nonnegative Long milliseconds; one start/stop;≤1024 completed spans; pause/resume validation; frozen stop summary. Active duration excludes paused intervals; elapsed=active+paused | No encoder/consent authority. Recorder adapter observes elapsedRealtime around successful start and requested stop; observed elapsed time is not media PTS/duration or evidence of a usable recording |
| `feature/messages/.../TranscriptArchive.kt` | Explicit plaintext FMT1 archive;≤256 ordered unique IDs, each1..64 ASCII; author≤256/text≤8192 UTF16; strict UTF8; nondecreasing nonnegative epoch milliseconds;≤2MiB total; big-endian lengths/time; CRC32; canonical readback | No system-message access, transport, attachment support, authentication or encryption. File tests use test-owned files only. CRC is not a malicious-tamper defense |
| `feature/vault/.../VaultEnvelope.kt` |≤1MiB plaintext; exactly32-byte caller key; context≤1024 bytes; FVE1 header contains length and fresh96-bit SecureRandom nonce; AES256-GCM128-bit tag; header+context authenticated; exact frame length; no plaintext returned before doFinal authenticates | Security depends on a secret high-entropy key, suitable platform RNG/JCA and correct context. No KDF, Keystore binding, key persistence, hardware attestation, replay/rollback prevention, secure deletion or Vault UI unlock |
| `core/model/.../LaunchLinks.kt` | Canonical≤128-character `fusion://open/<module>?workspace=<1..3>`; fixed allowlist: files/editor/notes/calculator/browser/settings. Reject aliases, extra queries, authorities, fragments, ports, percent-encoding variants | No Android Intent registration/dispatch, file/URI grant, implicit consent or network access. Workspace is explicitly one-based, not the compositor's internal zero-based index |
| `format/ooxml/.../SheetFrame.kt` plus existing DecimalSheet and PDF sandbox worker | FSC1 canonical sorted request;≤256 cells; addresses A1..DX1000; expressions≤512 UTF16; strict UTF8/Base64;≤256KiB frame. Original Decimal34/HALF_EVEN engine retains syntax/dependency depth16 and scale/precision limits. FSR1 canonical numeric result≤64KiB | Not an Office document, LibreOffice grammar or renderer. Worker receives one fresh read-only regular-file descriptor, not a path/URI/network grant; digest describes consumed bytes, not a stable snapshot |

The existing worker's admission, isolated binding, read-only/offset/regular-file checks, one-request lease,5s lifetime watchdog and8s request timeout remain. Parsing now occurs on the worker executor after bounded reading, not on the Binder/main thread. The broker validates bounded result syntax/address/numeric domain. Those checks are not proof of correct arithmetic from a fully compromised worker, complete IPC hardening, no-fork containment or physical resource budgets.

Typed inputs already reside in memory. Input limits do not prove whole-process RSS or provider wall-time bounds. Callers must not mutate arrays/collections concurrently with calls. Recorder accounting is single-owner. Message metadata is caller-supplied, not verified sender identity. Vault context is caller-supplied; using the same context for two resources does not bind either to a filename.

### Quantifiers and success predicate

For each candidate C, fix its admissible domain, implementation and corpus before evaluating it. Executed success means **every case in the declared finite corpus returns its specified value/refusal**. One invariant witness rejects the candidate until repaired and rerun. This does not establish correctness for every admissible input, OEM/API, attacker or original workflow.

The count is included Gradle modules, once each, with a nontrivial executable production behavior and reproducible checks. It is not the number of classes, assertions or available build targets. Full acceptance would require all original mandatory functional/security/legal/device/AX/performance gates—not “for every module there exists one passing example.”

Adjacent problems that must not replace the target: geometry/camera pipeline; observed intervals/encoded video; transcript archive/message transport; AEAD envelope/key-managed Vault; URI codec/launch authorization; arithmetic framing/Office documents; one isolated parser/universal containment; source inspection/legal clearance; emulator pass/physical acceptance; module testability/module completion.

## B. Outcome space and thresholds

- **H+**: the bounded components and isolated arithmetic experiment are achievable under current dependencies and authority. Host thresholds crossed; native claims require exact Android cases.
- **H−**: a concrete invariant is false. The first result validator accepted a cell address outside the evaluator domain; the counterexample is retained and the validator repaired. CRC-only sender authentication and stateless AEAD replay prevention also fail by executable witnesses.
- **H∂**: conclusions depend on coordinate conventions, clock source, caller classification, key/context/nonce assumptions, platform crypto, descriptor semantics, provider behavior, APIs and legal packaging. Do not silently strengthen these assumptions.

No global impossibility theorem for the original app is asserted. No small engine slice proves feasibility/completeness of1:1 LibreOffice parity. Pre-implementation thresholds and available resources were recorded in `R15-PLAN.md`; no original spike is promoted to full acceptance by this round.

## C. Retrieved frontier and imported hypotheses

`research/r15-primary.json` contains re-retrieved, Git-blob/SHA256-verified AOSP SurfaceControlViewHost and three pinned LibreOffice inputs (COPYING.MPL, LOK README/header), plus pyca/cryptography AEAD documentation at version46.0.3, commit `c0af4dd7b75921bbe9f1d41a03dbd4b64a9e3403`.

The AOSP input describes remote-surface interfaces, not universal browser-host isolation or OEM behavior. LO inputs describe licensing, architecture and interfaces, not a working Android engine, transitive-license closure or parity. No LibreOffice implementation bodies were copied into these functions. The same agent inspected sources and authored code; no independent clean-room/legal claim is made.

The AEAD documentation's nonce/context/authentication conditions apply to the selected AESGCM API, not automatically to every cipher discussed in the document. Independent Java/OpenSSL interoperability checks exercise our actual wire bytes. They are not FIPS validation or a proof of cryptographic security. A32-byte array does not itself prove key entropy or secrecy.

Conditional nonce bound: if nonces are independent uniform96-bit samples under a key, q encryptions have collision probability at most q(q−1)/2^97 by the union bound. No lifetime encryption-count enforcement or entropy measurement is implemented.100 observed distinct test nonces do not prove the RNG assumption or eliminate collision risk.

Local negative results: anyone who can edit a plaintext transcript can recompute its public CRC; the test changes a body and successfully imports the recomputed frame. Similarly, the stateless envelope opens the same authenticated bytes twice and permits substitution of another valid ciphertext using the same key/context. Freshness/resource binding require additional state/context, not a stronger marketing label.

This is a limited verified primary-source investigation, not an exhaustive best-known-results survey. The open interfaces remain real browser authority, complete document engines, key lifecycle, notification/message transport, MediaProjection/OEM behavior, full worker containment, legal/ABI and physical acceptance.

## D. Negative space —20 false solution shapes

| Shortcut | Failure and detector |
|---|---|
|1.44 tested subsets means44 complete modules | Separate0/48 acceptance ledger and48 unfinished modules |
|2. Thousands of geometry assertions mean thousands of features | Corpus composition and module denominator reported separately |
|3. Coordinate transform means camera pipeline/virtual camera works | Native test compares Bitmap geometry only; no camera/input injection claim |
|4. Timeline duration proves MP4 timing or recorder success | Service labels interval as observed, not encoded; finalization/error path remains independent |
|5. Transcript archive is SMS/RCS/iMessage | No message access/send code or transport is introduced |
|6. CRC provides authenticity | Executed changed-body/recomputed-CRC frame is accepted |
|7. AEAD automatically provides identity/freshness | Wrong-context rejection plus successful replay/shared-context substitution tests |
|8. Any32 bytes are a secure production key | Explicit caller entropy/protection assumption; no generated default production key |
|9. Encrypting a file completes Vault | Vault UI and key-management gates stay closed |
|10. A canonical launch URL authorizes dispatch | Codec has no Intent handler or grant; disallowed module/authority/query attacks refuse |
|11. Parent-side arithmetic is isolated execution | Broker only validates numeric results; native case requires different UID/PID and expected worker arithmetic |
|12. A hash-only sandbox proves parser containment | R15 adds real parser work and separate native tests; wider engine containment remains unproved |
|13. Type-correct reply is in the engine domain | ZZ9999 result counterexample; shared frame/result address range checks added |
|14.5s watchdog proves protection after arbitrary compromise | It is an in-process lifetime mechanism, not a no-fork/RCE containment theorem |
|15. Read-only FD means immutable source snapshot | Original source can still change; digest is consumed-byte evidence only |
|16. Parser byte cap is whole-process memory/time proof | Explicit input caps, no RSS/latency acceptance |
|17. Original arithmetic equals LibreOffice functions/parity | SUM/cycles/unsupported grammar reject; no Office engine executed |
|18. Source inspection erases licensing obligations | Pinned license records and no legal/clean-room declaration |
|19. A repaired validator inherits its old pass | Fresh compilation, domain-boundary attacks and complete700-case rerun |
|20. Host or inherited Android green certifies new native code | Exact revision-specific device receipts, pending outcomes not promoted |

The modal shortcut is substitution of a small positive artifact for the original workflow. Concrete detectors include CRC forgery, AEAD replay, disallowed links, unsupported spreadsheet functions and the separate full-acceptance count. An honest refusal is required, but denial-only code is not itself counted as a functioning module.

## E. Six mechanism-distinct constructions

The six branches were identified before implementation in `R15-PLAN.md`. Each has a concrete source artifact and separately compiled public API. Architectural independence is not independent authorship or expert review.

| Mechanism | Established portion / construction | Structure-derived attacker | Unresolved interface, cheapest falsifier, local versus original gap |
|---|---|---|---|
| Affine geometry / ViewportTransform | Quarter-turn and mirror composition with positive scale; inverse and clipping; rational reference and dense boundary corpus | Extreme aspect ratios, zero/oversized dimensions, NaN, letterbox points, orientation/mirror order | Actual Android Bitmap comparison is a separate native test. CameraX sensor/EXIF/metering and virtual-camera output remain separate original requirements |
| Monotonic interval accounting / RecordingTimeline | Ordered disjoint spans give active≤elapsed and paused=elapsed−active; checked transitions and bounded retained history | Clock rollback, repeated stop/start, zero-duration spans,1024-boundary, Long.MAX_VALUE, accounting failure during safety stop | Actual recorder lifecycle/media timestamps, lock/thermal/OEM behavior. Next real consent/start/revoke/finalize test; model does not prove capture success |
| Canonical framed archive / TranscriptArchive | Explicit lengths/counts, strict Unicode, unique IDs/time order, exact readback, CRC; actual file roundtrip | Negative/huge lengths, truncation, duplicate IDs, Unicode poison, reordered times, recomputed CRC forgery | Explicit share/import UI, privacy/persistence, real message transport. Next actual archive consent/export flow; protocol work is not messaging delivery |
| Platform AEAD envelope / VaultEnvelope | AES-GCM under caller key/context, exact header/size, no plaintext before tag authentication; bidirectional Java/OpenSSL tests | Every-byte frame mutation, wrong key/context, oversized frame, replay and shared-context substitution, nonce/key assumptions | Key generation/protection/rotation, KDF, Keystore, crash lifecycle, rollback/freshness and secure deletion. Next real Android Keystore/wrong-key/rotation spike before any Vault unlock; full gap remains substantial |
| Canonical allowlisted URI codec / LaunchLinks | Constructor admits only18 module/workspace targets; all18 roundtrips tested; accepted strings must equal re-encoding | Encoded aliases, duplicate queries, traversal, credentials/port/fragment, forbidden module, excessive length | Actual reviewed dispatcher and workspace-index conversion, no implicit grants. Next explicit Android deep-link consent/denial test; local integration gap, not authorization proof |
| Capability-limited parser worker / SheetFrame + DecimalSheet + FormatLease | Canonical input/output domains, bounded original arithmetic, one-use read-only FD pipeline; actual child execution is separately tested | Out-of-domain result keys, cycles, malformed/noncanonical Base64/results, consumed-byte overflow, worker death, mutable source, forged/compromised replies | Native UID/PID/result evidence, complete Binder containment/no-fork/RSS/API matrix and full Office behavior. Next actual worker tests; bounded-parser gap local, full Office/isolation gap original-scale |

Formal/software-proof distinction: interval and affine arguments assume their mathematical/input models and single-owner execution; floating-point tolerances are tested, not universally proved. Canonical URI validation is enforced by construction and finite target enumeration. AES-GCM relies on platform cryptographic assumptions. No theorem prover was run.

## F. Concrete artifacts and current integration

- Seven new Android cases exercise actual Bitmap transforms, elapsed clock, real transcript/ciphertext files, Android URI parsing, real isolated arithmetic and refusal paths. No synthetic camera, fake notification, simulated transport or substitute Office implementation is used.
- ScreenRecordService records observed elapsed time only after MediaRecorder.start succeeds. Accounting errors cannot prevent the existing stop/finalization path; the displayed interval is explicitly not encoded duration.
- Vault and Messages remain gated at the app level; public bounded components do not enable their full workflows. Lens transforms are not yet wired into the sensor/preview pipeline. LaunchLinks does not register or dispatch Intents.
- FormatWorkerService now has an original arithmetic operation. It retains descriptor provenance/offset/mode checks, enforces the smaller parser input limit while reading, evaluates in the child executor and returns≤64KiB canonical numeric data. The parent checks result domain before returning it. No output path, URI grant or network authority is added.
- `:format:pdf` now depends on the existing first-party `:format:ooxml` module. No new third-party runtime library or MPL/native engine payload is included. Host cryptography/readers are outside the APK.
- `:testing:r15Contracts` and all new device cases are wired to the existing CI workflow. Test existence is not a result.

## G. Counterexample, repair and stronger attack

The original result-frame checker accepted `FSR1\nZZ9999\t1\n`, even though DecimalSheet's supported address space ends atDX1000. A separately compiled consumer actually returned `{ZZ9999=1}`. `verification/r15-result-domain-counterexample.json` records the consumer, old source/JAR hashes and observed output.

Repair: input and result framing now enforce both column and row bounds. New attacks reject ZZ9999, DY1, DX1001 and A0 on both sides while accepting DX1000. The complete six-library suite was rebuilt after repair, and all cross-language cases rerun. Additional attacks cover maximal records/sheets/plaintext, total archive budget, noncanonical Base64/order, oversized results/context, repeated operations and explicit non-security properties (CRC forgery/replay/shared-context substitution).

Final local results: **5510 assertions PASS;700 cross-language cases PASS**. Repair inherited no earlier pass. Source/JAR hashes identify the actual tested bytes. Native adapters require their own exact Android result, not this host receipt.

## H. External verification and native evidence

The700 cases comprise100 each for geometry, recording accounting, transcript bytes, Java-produced AEAD, launch links and framed arithmetic, plus100 independently generated OpenSSL ciphertexts decrypted by the actual JVM implementation. Geometry uses exact rational affine matrices; transcript references use Python struct/CRC; links use urllib.parse; arithmetic uses decimal34 HALF_EVEN. Cryptography/OpenSSL versions are recorded in `r15-interop.json`.

This is a same-author corpus, not independent held-out review. Independently maintained crypto and standard-library implementations reduce shared implementation dependence; they do not certify keys, RNG entropy, all adversarial inputs, FIPS, physical hardware or entire modules. No LibreOffice/Excel runtime was executed.

Inherited **751c0f4 / run38690692400** completed **64 PASS /0 FAIL /0 SKIP** on the API34 managed device, with build/JVM/contracts/lint success. Retained: `verification/r15-inherited-*`. This includes the previously corrected Settings test, earlier editor cases, six actual format-isolation cases and browser profile/surface experiments. Earlier failures remain in the repository; one green run is not a stability theorem or physical release gate.

R15 native candidate **eeb93a4 / [run38692920167](https://github.com/jyvplay/OSB/actions/runs/38692920167)** completed **71 PASS /0 FAIL /0 SKIP**. Both debug flavors, invoked JVM/contracts and lint also passed. All seven R15 cases passed, including actual Bitmap rotation/mirroring, file-based platform AEAD, Android URI parsing, real elapsed clock, real transcript I/O, actual different-UID/PID spreadsheet execution, cycle refusal and consumed-input overflow refusal. Retained: `verification/r15-native-*`.

The tested production and Android-instrumentation sources are unchanged by the later expanded host corpus/docs; `verification/r15-native-source-match.json` records this check. The native run used the earlier5496-assertion host suite; the expanded5510-assertion suite passed locally. This is not a new native run or reproducible-binary attestation. No local Android execution or physical acceptance is claimed.

## I. Alternatives for the major unresolved systems

| Direction | Actual foundation | Remaining experiment / constraint |
|---|---|---|
| First-party browser UX on platform profiles/remote surfaces | Existing first-party browser/session sources; inherited passing profile storage and real pixels/input/resize cases; reverified SurfaceControlViewHost interface | Complete tabs/groups/splits/permissions/downloads/session/keyboard UX; actual browser-host UID/network/storage authority. Same-UID process separation is not the required security boundary |
| Separate first-party companion UID for browser hosting | A design alternative, not a newly shipped payload; existing FD/UID and remote-surface experiments provide partial pieces | Signature-permission broker, explicit installation/consent, provider/API support, lifecycle/input/death and storage-authority tests. Prior pinned WebViewFactory source did not establish a universal isolated-process support/impossibility theorem |
| Original bounded Office engine slices inside a real worker | R14 arithmetic, R13 literal XLSX, R15 framing and actual isolated-operation code/tests | The API34 child-result gate passed for the bounded arithmetic operation; next original document model/import/edit/save, functions/layout/font/fidelity corpus. This is not full Office or LibreOffice1:1 parity |
| Isolated LOK payload | Reverified license/header/architecture inputs; existing engine gate remains closed | Complete payload/transitive-license review, ABI/16KiB build, render/edit/save/crash corpus and required legal review. IPC alone does not cure incompatible dependencies. LOK would not be a from-scratch rewrite |
| Headless audited POI route | Prior FF02 contract/research, not newly shipped here | Dependency audit and actual conversion/read/write corpus. Does not replace rendering, complete recalculation or native Office UX |
| Explicit capabilities and original bounded parser workers | Existing actual isolated-UID/FD/watchdog/socket tests; now real arithmetic operation instead of only hashing | Re-run all native isolation cases; add complete compromised-worker/Binder/no-fork/RSS/lifecycle/API tests. Input limits alone do not prove containment |
| Platform-native productivity plus explicit local formats | Recorder accounting, transcript archive, launch target, prior CalendarExport/editor/notes/calculator/Files | Real CalendarContract/ContactsContract/API37 picker, IMAP, reminders, PDF/image editing, Messages bridge and lifecycle/AX acceptance. Export/codec/accounting is not the original workflow |

No external Office app, stock desktop, generic browser wrapper, system cast button, cloud relay or unsupported virtual camera is substituted. New test dependencies are not runtime engine dependencies. No S1–S30 gate is promoted to full acceptance by these alternatives.

**Full LibreOffice1:1 parity remains undelivered.** The inspected interface/license sources do not establish implementation-function coverage. Original arithmetic is not a LibreOffice rewrite, and inspection does not automatically remove licensing obligations. A parity domain must explicitly respect the product's macro/license/isolation exclusions and define reference revision, formats, fonts/layout, edit/save/error semantics and tolerances. No honest parity percentage follows from a small corpus.

## J. Exact residual gap and highest-information next tests

1. Preserve the completed71-case native receipt and extend parser attacks to actual malformed result/IPC, cancellation/death, API36/37 and physical-resource tests. The positive child-UID arithmetic/source-preservation and refusal gates passed for eeb93a4; this does not certify the complete broker against arbitrary compromised-worker behavior.
2. Treat any compile/device failure as a rejected candidate, retain it and rerun affected gates after repair. The shader/crypto/provider/IPC behavior needs API36/37 and physical validation too.
3. Before exposing Vault, verify actual Android key lifecycle/KDF/Keystore/rotation/rollback behavior and legal/security release requirements. The envelope alone cannot satisfy these.
4. Before exposing transcript import/export or launch links, implement explicit consent, sensitive-data handling, safe-save/reopen and actual dispatcher checks. Do not enable Messages transport or grants from codec success.
5. Test real MediaProjection start/revoke/lock/thermal/finalize and CameraX sensor/preview behavior. Observed interval and coordinate calculations are only components.
6. The four modules still without a qualifying subset are Together, LOK, benchmark and baselineprofile. Do not replace missing real transport/engine/profile generation with gate-only helpers or fabricated peers/performance numbers.
7. All48 modules remain unfinished under the original specification. Compile37 migration, physical/API37, arm64/4K+16K, budgets, complete keyboard/AX, legal and full workflow gates remain open.

Build/reproduction: [BUILD.md](BUILD.md). This increment returns implemented bounded artifacts, actual counterexamples and reproducible experiments—not a declaration that the full original app has been completed.
