# R14 — six executable mechanisms and bounded progress

## Runtime honesty and result

**39/48 included modules (81.25%) meet the bounded-testable-subset standard;9/48 (18.75%) remain unestablished. Full specification acceptance remains0/48 (0%);48/48 remain unfinished.** Newly qualifying: Files UI, design system, accessibility, alerts and settings UI. OOXML gains another tested component but is not counted twice. This is not a feature-coverage or Office-parity percentage.

Executed: **1,893 R14 host assertions**, **600 cross-language cases**, **74 Python tests**. R13 regression was rebuilt and rerun: **503 host assertions and404 interoperability cases passed**. Six R14 production libraries were separately compiled, then public consumer tests compiled against them. Receipts bind source, tests, runners and actual JARs. Two independent host-suite commands ran concurrently through the tool runner; no independent agents were used.

Tools actually used: Git/gh/GitHub Actions/API, filesystem, Python3, Kotlin2.2.21, JRE17.0.9, Python decimal/AST, installed openpyxl3.1.5/icalendar7.3.0. No local Android SDK/device, theorem prover, physical lab, expert AX review, counsel or independent held-out reviewer. Remote managed-device execution is Android emulator evidence, not physical-device acceptance. No mock engine, simulated peer or TypeScript product was added.

Receipts: `verification/r14-kernel-contracts.txt`, `r14-interop.json`, `r14-python.txt`, `r14-r13-regression.txt`, counterexamples below. Census: `module-testability.json` / `MODULE-TESTABILITY.md`. Work continues on PR3 without merging.

## A. Formal model

Pure components operate on typed bounded inputs and have no network, filesystem mutation or notification access. Files reads existing user-granted directory metadata; Settings dispatches only an explicitly reviewed density change. The notification listener remains disabled.

| Artifact | Admissible domain, bounds and units | Not established |
|---|---|---|
| `feature/files/.../DirectoryQuery.kt` |≤5000 items; unique ID1..4096/name1..1024 UTF16 units;≤2Mi aggregate ID/name units; query≤128; valid Unicode; nullable nonnegative Long byte size. Literal Locale.ROOT filtering; directories first; overflow-free natural decimal ordering; optional largest files, unknown last; name/ID ties | Provider enumeration timeout, identity/snapshot, recursive search, locale collation, journaled mutation. Provider size0 can mean unknown |
| `core/designsystem/.../Contrast.kt` | Opaque24-bit sRGB; finite minimum1..21; Double result, external absolute tolerance1e−11. Preserve adequate preferred color, else best black/white, else null | Alpha/HDR/other spaces, rendered pixels, all theme tokens, AX acceptance |
| `feature/accessibility/.../SpatialFocus.kt` |≤512 unique IDs; finite nonempty rectangles within±1,000,000 in one caller coordinate system. Visible/enabled current and candidates. Center half-plane, perpendicular overlap priority, squared center distance, ID tie; no wrap | Android focus request, RTL/semantic order, global input, TalkBack/Switch/Voice |
| `feature/alerts/.../NoticeLedger.kt` | Capacity1..256; retention1..604800000ms; ID≤128/title≤256/body≤2048 UTF16; valid Unicode. Posting/advance require nonnegative nondecreasing caller time. Age≥TTL expires. Descending timestamp/ID order. Redact/clear take no clock | Trusted classification/clock, notification ingestion, persistence, concurrent writers, revocation lifecycle |
| `feature/settings/.../SettingsPatch.kt` |≤64 ASCII keys≤64 units; values≤1024 UTF16; aggregate≤65536; valid Unicode. Immutable base→desired deltas; touched current values must equal base or desired; unrelated changes survive; conflicts before returned mutation | Durable transaction, authenticated import, permission grant, arbitrary-setting enablement, same-value ABA detection |
| `format/ooxml/.../DecimalSheet.kt` |≤256 cells, A1..DX1000; expressions≤512 UTF16; syntax depth≤16; dependency height≤16 including cached references. Literals, +−*/, parentheses, references. Decimal34 HALF_EVEN per operation; normalized precision≤34/scale−100..100; all cells or refusal | LibreOffice/Excel grammar, functions/ranges, dates/strings, external names/macros, iterative cycles, Office import/edit/layout/render/full recalc |

Inputs already reside in memory. Count limits are not RSS/wall-time proofs. NoticeLedger requires single-owner use; callers must not concurrently mutate collections. No guarantee covers malicious Java implementations violating collection contracts. File querying now runs off the UI thread; that does not prove device latency or interrupt provider enumeration.

**Quantifiers:** choose each candidate's domain, implementation and finite test corpus before judging it; require every case in that corpus to produce the specified result/refusal. This finite predicate is not “every admissible input, Android environment and original workflow satisfies all mandatory gates.” Full acceptance cannot be replaced with “for every module there exists a passing test.” Count each included module once, requiring a nontrivial executable production behavior; folders, value types and denial gates do not qualify.

Adjacent non-solutions: querying versus file operations; color math versus accessibility; focus target selection versus complete keyboard UX; public-notice state versus notification access; map merge versus durable preferences; arithmetic versus Office; UID observations versus containment; source inspection versus legal clearance.

## B. Outcome space

- **H+**: bounded transformations and specified integrations are achievable without new runtime dependencies or authority. Host evidence crossed the subset threshold; native integration requires exact revision/case evidence.
- **H−**: reject a candidate on an invariant counterexample. Clock rollback blocking redaction was one. Guaranteed7:1 contrast for every fixed background is impossible within the opaque sRGB model below.
- **H∂**: outcomes depend on color space/transparency, provider behavior, classification/time, mutation history, formula domain, UID/permissions, API/OEM and legal packaging. Missing conditions remain missing.

No global impossibility theorem or feasibility/completeness result for the entire application or1:1 Office rewrite is claimed. Device/legal/isolation/AX gates remain unpassed.

## C. Retrieved frontier and imported hypotheses

`research/r14-primary.json` records verified Git blob and SHA256 retrievals:

- W3C WCAG at `23bad5904949904275a08f1c6e395b78a02aa6f3`: relative luminance, contrast-ratio definition and Contrast Minimum. sRGB uses threshold0.04045, divisor12.92, exponent2.4 and weights0.2126/0.7152/0.0722. The4.5:1 text criterion has large-text/incidental/logo exceptions. These definitions do not certify Android rendering or classify our text as large.
- LibreOffice at `0f91d58472b9a1b3289e070e4c03335d5935b4eb`: COPYING.MPL, LOK README and header were retrieved again and both digests checked. They establish license/interface/architecture inputs, not engine availability, transitive licensing or parity. No implementation body was copied into the new arithmetic evaluator; no independent clean-room/legal claim is made.
- AOSP WebViewFactory at `299fe6f5d6fc6f1af7c3411dcf4e5efdf7217368`: rejects enumerated privileged UIDs and unsupported/disabled configurations. **It does not contain the assumed explicit isIsolated() rejection at this revision.** Therefore neither universal isolated-process support nor impossibility is imported. Test actual API/provider/UID construction.

One WCAG contents request returned404; pinned tree/blob retrieval and a subsequent contents request succeeded. This is a limited verified primary-source investigation, **not an exhaustive best-known-results survey** or proof that no better approach exists.

Local algebraic bound: for exact luminance L∈[0,1], black/white contrasts are (L+.05)/.05 and1.05/(L+.05). Their product is21, so the larger is at least√21≈4.5826, sufficient for4.5. At intermediate luminance both can be below7; no other opaque foreground luminance exceeds those extreme ratios. The executed middle-gray7:1 case returns null. This model argument is not a prover output, universal floating-point error bound or rendered-AX certificate.

The open interfaces are real rendering/focus, providers/lifecycle, notice access/revocation/storage, settings durability, Office models/import/layout/functions, brokered parser execution, and legal/ABI/physical acceptance.

## D. Negative space —20 false solutions and detectors

| False solution | Why it fails / detector |
|---|---|
|1. A tested helper completes its module | Census says subset; acceptance remains0/48 |
|2. Assertions count as features |1893 assertions and39 modules are different resources |
|3. Directory filtering supplies journaled moves | Mutation backend stays denied; source gate checks remain |
|4. Parse filename digits as Long |200-digit filename attack tests overflow-free ordering |
|5. Return partial results after a duplicate/budget error | Exceptions and UI refusal, not partial query results |
|6. Always return a supposedly compliant color | Middle-gray7:1 case must return null |
|7. Math proves AX | No pixel/assistive-technology acceptance inferred |
|8. Returning a focus ID moves Android focus | API has no input/focus authority; integration remains missing |
|9. Private update leaves old public content | Executed rollback/redaction witness; repaired removal has no clock input |
|10. Wall time is always monotonic | Posting/advance reject rollback; privacy removal cannot be blocked by it |
|11. Replace the entire settings map | Cross-language unrelated-field preservation and conflict/no-partial-effect checks |
|12. A patch is durable/authenticated | It returns a map; UI says requested, not a durability receipt |
|13. BigDecimal.equals tests numeric equality |60530 versus6.053E+4 witness; explicit normalized-value regression |
|14. Memoization hides an excessive dependency graph |18-cell chains in different insertion orders must refuse |
|15. Arithmetic evaluation is full spreadsheet recalculation | Unsupported SUM, unknown cells, cycles, grammar and budgets refuse |
|16. Decimal34 can silently enter a15-digit export | Native test requires the literal writer to reject unsupported1/3 precision |
|17. Same UID, different process means isolation | Existing actual isolated UID tests are separate from browser process profiles |
|18. Source inspection removes license obligations | Pinned license/interface records; no legal/clean-room assertion |
|19. Green PR means native device suite passed | pull_request run38688199586 skipped the device job; push run38687376592 failed |
|20. Latest passing case erases historical failures | Retain failed receipts and rerun affected cases on new bytes |

The modal shortcut—one positive slice stands in for the original workflow—is detected by explicit scope separation and negative cases: e.g. DecimalSheet refuses `SUM(A1)`, cycles and external names rather than pretending Office support; all original completion counts remain zero.

## E. Six mechanism-distinct constructions

Candidates and thresholds were recorded in `R14-PLAN.md` before implementation. Libraries compile separately, with no shared substitute engine. Same-agent authorship remains a limitation; architectural separation is not independent review.

| Mechanism / artifact | Established portion | Structure-derived attacker | Exact interface / cheapest next falsifier / gap |
|---|---|---|---|
| Overflow-free lexical metadata ordering / DirectoryQuery | Production filter/order, positive/negative boundaries, permutation invariance and Python arbitrary-integer comparisons | Huge digit runs, duplicate IDs, malformed Unicode, locale differences, aggregate overflow | Real provider enumeration, metadata ambiguity, UI cancellation; next controlled SAF large-directory test. Query integration gap local; journaled operations remain separate original scope |
| Radiometric transformation / Contrast | Opaque sRGB calculation, algebraic black/white bound,60-digit Python decimal comparison | NaN minimum, invalid RGB, unattainable7:1, rounding near thresholds | Actual theme/rendered surfaces, alpha/HDR; next pixel/color-space check. Full AX gap is not solved by color math |
| Geometric selection / SpatialFocus | Directional candidate filtering, overlap/distance/ID ranking, ties/visibility/finite bounds | Coincident centers, disabled nearer node, NaN/empty rectangles, reordered ties | Android focus/RTL/reading order; next real directional-key focus test. Local integration plus broader original AX requirements |
| Temporal bounded state / NoticeLedger | Real state transitions, TTL equality, deterministic eviction, stale/future/rollback refusal, unconditional removal | Public→private transition during rollback, expired arrivals, capacity ties, Long.MAX_VALUE | Classification, lifecycle/revocation and storage; next real listener permission-denial/revocation spike before enabling it. Full notification feature remains missing |
| Three-way conflict detection / SettingsPatch | Immutable patch, idempotence, deletion, conflict-before-result and unrelated-edit preservation | Input aliasing, two-key conflict, imported deletion, aggregate overflow, same-value ABA | Actual host persistence and reviewed supported fields; next native density review/cancel/apply test. Persistence is not supplied by map merge |
| Decimal parsing and dependency graph / DecimalSheet | Original bounded arithmetic, precedence, references, cycles and graph height; Python AST/decimal comparisons | Deep/cached chains, syntax bombs, divide-by-zero, unknown/external references, precision/scale overflow | Workbook model/import/functions/layout/edit/render; next controlled real Office comparison in the declared numeric domain. The remaining full-parity gap is essentially the original engine problem |

No unproved implementation step is labeled “standard.” Concrete code, corpus, counterexamples and source/artifact-bound receipts exist for each candidate. No formal universal proof is claimed for the software.

## F. Artifact and integration ledger

- Files UI now exposes literal filter and natural/largest ordering. Query work is off the UI thread; canceled work cannot publish after its coroutine cancellation boundary. This does not cancel underlying SAF enumeration. Over-budget/invalid metadata does not become a truncated “complete” query.
- Main shell and design-system theme consume the foreground selector for primary-color content. The custom dark shelf container also gets its own explicitly computed foreground; reusing primary black there would fail the4.5 numerical threshold. Other theme pairs and all rendered accessibility requirements remain unverified.
- Settings exposes review, cancel and apply for the existing actual density action. A changed value conflicts instead of silently overwriting it. Search input is bounded to256 UTF16 units rather than crashing the registry on a long query.
- NoticeLedger and SpatialFocus are public tested components, not enabled notification access or completed native focus integration.
- DecimalSheet is an original public arithmetic API, not a newly unlocked Office screen. A native test connects a supported numeric result to the literal XLSX writer and separately requires unsupported precision to be refused.
- Six new Android kernel/adapter cases and one actual Settings UI case are wired to the managed-device suite. Their existence is not a pass. `:testing:r14Contracts` is wired to CI.

## G. Adversarial counterexamples and repairs

1. **Verifier mismatch:** the initial host run failed at `60530 != 6.053E+4`, because BigDecimal.equals includes representation/scale while the evaluator explicitly normalizes values. Retained: `verification/r14-first-host-failure.txt`. The expectation now uses the documented normalized numeric representation, with explicit60530 and signed-zero regressions. No product arithmetic was changed just to match a faulty representation expectation.
2. **Real privacy defect:** a separately compiled public-API consumer posted an item at100ms, attempted redaction at99ms, observed `redaction_refused=Clock rollback`, then observed one retained item. `verification/r14-privacy-counterexample.json` binds the old source/JAR hashes and includes the consumer and actual output. Repair: remove time from redaction and clear entirely. Posting/expiry still enforce their time contract. New attacks refuse clock rollback, then require redaction/clear to remove the data anyway.
3. **Boundary hardening:** dependency height is tracked independently of evaluation cache hits, not merely active recursion depth. Tests reject overlong graphs in forward and reverse insertion order. Other attacks cover exact5000/512/256/64 limits, malformed Unicode, atomic two-field conflicts, maximum clock and exact expression/depth limits.

4. **Native fixture error:** de06095's new Settings test expected BALANCED before apply, while WindowEngine's actual default is COMPACT. The failed result is retained. The fixture now asserts COMPACT before its first action, verifies that review/cancel do not change it, then requires TOUCH after explicit apply. No product default was changed to satisfy the test. Native re-verification is required.
5. **Global color compatibility:** primary black is sufficient on mint but not on the custom dark shelf container. Explicit shelf foreground selection and host/device attacks for that pair were added. This establishes color-pair arithmetic, not a pixel/AX pass.

After repair and additional attacks, all six libraries and consumers were freshly compiled: **1893 PASS**, followed by **600/600 cross-language PASS**. R13 regression was also rebuilt and rerun. No repaired component inherited an earlier pass. Native changes still require native evidence.

## H. Verification and native status

External reference mechanisms: maintained Python decimal at precision60 for contrast and precision34 HALF_EVEN for formula arithmetic; maintained Python AST for expression syntax/precedence; arbitrary-size Python integers for filename digits; separately written Python references for focus, settings and notice behavior. These600 cases are same-author differential tests, not an independent held-out audit. R13 uses independently maintained real format readers and passed again. No LibreOffice or Excel runtime was executed.

Inherited **f5a18b8 / push run38687376592**: **55 PASS /2 FAIL /0 SKIP**, retained in `verification/r14-inherited-*`. The corrected calendar case passed. Editor replacement passed the initial replacement/undo assertions but failed **after redo and Activity recreation**, at ShellSmokeTest line124. The200%-font Compose case failed with SnapshotStateObserver cross-thread access. Read-only diagnostics and a new pre-recreation success assertion now distinguish redo dispatch from recreation loss; neither failure has been declared repaired.

That same inherited run passed all six FormatIsolationDeviceTest cases: distinct isolated UIDs, controlled private-path denial with explicit FD success, watchdog death, invalid descriptors, single-use leases and actual loopback denial with reachable parent controls. Browser process-profile storage separation and real remote WebView pixels/input/resize also passed. These are substantial existing experimental evidence, **not isolation of every parser, full desktop browser delivery, or universal containment**.

The inherited pull_request run38688199586 was green but **skipped managed-device checks**. Do not use it to erase the failing push run. R14 candidate **de06095 / push run38689433735** completed: **63 PASS /1 FAIL /0 SKIP**. Both debug flavors, invoked JVM/contracts and lint succeeded. All six new R14 kernel/adapter cases passed, including real temporary-file metadata and arithmetic→literal XLSX integration; both previously failing editor cases also passed in this run. The only failure was the Settings test's incorrect default-density assumption described above. Receipts: `verification/r14-native-first-*`. This is a failed overall workflow, not a green device suite. PR run38689437702 is separate and does not replace the push-run evidence. Later UI-query/fixture/test updates do not inherit Android acceptance from an earlier revision.

## I. Browser, Office, sandbox and productivity alternatives

| Route | Concrete foundation / status | How it addresses a constraint; exact unpassed experiment |
|---|---|---|
| First-party browser shell over separate platform WebView profiles/surfaces | Existing BrowserSessions/BrowserSessionCodec, debug BrowserSurfaceProbe and passing inherited profile/surface cases | Keeps own compositor/UI, not a generic external browser. Same-UID profiles are not a security boundary. Next: actual provider/UID construction and storage/network-authority tests, then tab/split/session/permission UX matrix |
| Separate first-party companion APK/UID for a browser host | Architecture alternative grounded in UID distinction, not an implemented new payload | Could separate host authority without claiming `android:process` isolates it. Must test signature-permission broker, installation/consent, remote surfaces/input/resize/death and supported providers. No invisible automatic installation or API support assumed |
| Audited original numeric/document engine slices | DecimalSheet, prior literal XLSX writer and real tests | No foreign engine payload or implementation copying. Useful bounded functions, but import/render/layout/edit/functions/fidelity remain. Next: named real-engine numeric corpus and an original import/model/edit/save slice |
| Isolated LOK payload | Reverified pinned license/header/README; existing gate closed | Potential broad engine capability, not a from-scratch rewrite. Must audit payload/transitives, MPL packaging and counsel needs; build arm64/16KiB and execute render/edit/save/crash corpus. IPC separation alone cannot cure incompatible dependencies |
| Headless POI path | Prior `research/r12-primary.json` and FF02 contract, not newly shipped | Potential allowed read/write/extract after full audit. Does not supply native layout, UI, Calc recalc or full Office parity |
| Minimal-authority parser workers | Existing FormatLease/FormatWorkerService and six inherited actual Android isolation checks | Explicit read-only regular-file capabilities and watchdog are a real foundation. Next integrate an actual bounded parser in that worker and run bomb/cancel/death/descriptor attacks. Digest success is not proof every parser is contained |
| Native productivity APIs plus bounded own-app state | Files query, reviewed settings, notice/focus kernels, prior CalendarExport/editor/notes/calculator | Avoids unnecessary broad permission/engine dependencies. Still requires real CalendarContract/ContactsContract/API37 picker, reminders, PDF editing, IMAP, lifecycle and AX tests; export/handoff is not the entire feature |

No stock desktop, external Office, cloud relay, system casting button or fake virtual camera is substituted. Native Office remains incomplete. Source inspection does not confer independent clean-room status or remove licensing obligations. The original macro/license/isolation exclusions must remain explicit when defining any parity domain. A1:1 rewrite is not delivered, and no honest parity percentage follows from these tests.

## J. Stopping boundary and next highest-information tests

1. Collect the exact R14 push-run device cases and compiler output; fix concrete failures rather than infer native success from host results. Re-run changed UI/fixtures on the final branch revision.
2. Use the strengthened editor assertion to determine whether redo fails before recreation or state changes during recreation. Preserve the large-font threading failure and original strong test; do not substitute the separate passing real-IME test.
3. Run actual large-directory SAF/filter/cancellation tests and density review/cancel/apply UI checks. No journaled mutation unlock follows.
4. Integrate actual parser work into the already tested isolated worker before making broader sandbox claims. For browsers, resolve actual UID/provider capability experimentally instead of assuming a source-level impossibility or support theorem.
5. Continue the nine unestablished modules without counting gate-only code: core model, Vault, capture, Together, Lens, Messages, LOK, benchmark and baseline profile. Existing source in these modules is not declared absent.
6. Physical API34/36/37, compile37 migration, arm64/4K+16K payloads, performance budgets, full keyboard/assistive-technology review and legal acceptance remain unpassed. No testability percentage overrides these gates.

Reproduction and laptop/Android Studio entry points: [BUILD.md](BUILD.md). This increment returns bounded implemented artifacts, counterexamples and next experiments, not a claim that the original complete specification has been delivered.
