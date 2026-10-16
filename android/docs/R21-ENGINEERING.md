# R21 — isolated CSV worksheet and document-bound consent

**Native Android work, not an anonymous OS or full Office/browser delivery.** Four existing modules are deepened; no module was split or renamed to increase the denominator. PR #5 remains unmerged.

## Evaluated build and laptop use

- Application candidate **b96f060808d6a7b73df9047f31209d4d589bcae7**.
- [Main debug APKs/full109-case evidence](https://github.com/jyvplay/OSB/actions/runs/38849172796), artifact `fusion-native-debug`.
- [Focused8-case R21 evidence](https://github.com/jyvplay/OSB/actions/runs/38849172743) and [Task/Notes7-case compatibility](https://github.com/jyvplay/OSB/actions/runs/38849172798).
- [Laptop/build/no-Studio instructions](BUILD.md#r21-csv-worksheet-try-out). Native Activity/picker were tested directly; the Automation button is source-wired, not a separate passed shelf-entry test.
- Later evidence/docs/tooling commits are not separately claimed as native-tested application candidates. Artifacts may expire under repository retention settings. PR build38849178263 also passed; that is not another device suite.

## Completion / substantial testability

| Standard | Absolute | Percentage |
|---|---:|---:|
| Included modules with substantial executable subsets | **48/49** | **97.96%** |
| Not established: LOK | **1/49** | **2.04%** |
| Fully specification-accepted modules | **0/49** | **0%** |
| Unfinished against complete assigned specification | **49/49** | **100%** |
| Existing modules deepened in R21 | **4/49** | **8.16% of inventory**, not acceptance |

Concrete pairings are in MODULE-TESTABILITY.md/json with source hashes. R21 deepens `:format:ooxml`, `:format:pdf` (existing worker host, not a PDF engine), `:feature:browser`, and `:feature:automation`. The separate privacy APK remains a bounded offline workspace, not a fifth new R21 module. Original 48-module comparison remains47/48 (97.92%); LOK is not upgraded merely because its placeholder compiles. All30 original formal spike gates remain unpassed; a passed boundary experiment is not a whole S10/S11 acceptance.

## Runtime honesty

Actually available/used: filesystem/shell, git/gh, Python3.11.2, Kotlin2.2.21, JDK17.0.9, Python csv/struct/zipfile, openpyxl3.1.5, Z3 5.3.0, fresh web search/page retrieval and remote GitHub Gradle/Android managed API34. No local SDK/ADB, Android Studio GUI, physical device, independent agent, human legal/privacy/AX expert, Excel or LibreOffice runtime. No database was introduced. The remote test system is an Android managed emulator running real production/test APKs, not a physical device; no fabricated runtime is substituted. Parallel CI processes are not independent reviewers. Authored numerical boundary inputs exercise real functions; they are not simulated Android. Instrumentation uses actual UIDs, Binder, files, views, IME API and DocumentsUI/provider operations.

## A — formal model

### Objects, access and resources

- CSV input is a caller-owned byte sequence of at most **65,536 bytes**. Strict UTF8; optionally consume one initial UTF8 BOM. Dialect admits LF/CRLF record endings, doubled quotes, quoted commas and embedded CR/LF; rejects bare CR outside quotes, dangling/misplaced quotes, ragged rows, malformed UTF8 and controls other than TAB/CR/LF. No header/type/separator sniffing. Empty input means zero rows; `""` means one empty cell.
- Table: **0..256 rows**, **1..32 columns for nonempty tables**, **≤8192 cells**, **≤4096 UTF16 units/cell**. Rows and outer list are immutable snapshots. Caller must not mutate input arrays/collections concurrently during capture or comparison; arbitrary malicious collection implementations are not admitted. Selection is an ordered, unique, nonempty list of valid zero-based indices (UI is one-based).
- Data-only FCT1 reply: magic, row/column counts and length-prefixed strict UTF8; **≤131,072 bytes**, no trailing data. Parent parses this bounded result protocol, not original CSV syntax. All data remains untrusted; correlated size/digest is not proof of correct computation.
- Worker: existing non-exported isolated service, one operation per bound instance, fresh readonly regular FD at offset0. Parent closes writer, opens readonly then unlinks the private temporary path before sending. Worker has no source pathname/URI grant. Existing watchdog **5000ms** from child creation; broker connect wait **12s**, reply wait **8s**. These are configured observation/timeouts, not a hard realtime guarantee against arbitrary compromised code/scheduler. SAF write/readback has no proved latency bound.
- UI retains a bounded text draft and frozen workbook in memory; generation is a fresh object identity rather than a wrapping numeric counter. Editing invalidates preview. Callback from an obsolete generation cannot enable export. Picker receives a frozen payload; recreation loses it explicitly. Workbook output capped **2MiB** in this route. New-document readback must match exact bytes. No original overwrite/retirement or provider durability claim.
- Consent controller is single-window, ≤64 tabs using existing session kernel, ≤64 pending prompts, existing ≤64 permission leases with **1..60,000ms** lifetime. Fresh object identity per document; same-URL, cross-origin and unsupported navigation invalidate pending/granted state. Close/shutdown invalidate. Trusted caller supplies actual consent and monotonic clock; no renderer or Android permission is granted by this class.

### Quantifiers and success

For **every admitted table T and ordered selection S**, projection cells equal exactly `T[row][S[column]]`; order, spaces, leading zeroes and formula-like strings are preserved. For **every pair T,U with identical selected cells**, fresh XLSX bytes must agree **under the same writer/runtime environment**. This excludes omitted fields, not identity remaining in selected values, filenames, provider metadata or timing. CSV admission does not imply every string is admitted by the literal OOXML writer: unsupported XML/OOXML escaped-string tokens are refused, never silently dropped. No existing workbook is modified.

For **every operation trace through the trusted controller**, a successful decision/consume requires a current live document identity and valid scope/time; a ticket succeeds at most once. Invalid navigation revokes before URL validation, even when no new URL can enter the session model. Wrong-context grant consumption conservatively revokes all outstanding grants. This can reduce availability but cannot grant another origin permission. Interface success still requires all real engine events to arrive; that bridge is absent, not assumed.

For **every completed preview job**, publication requires the captured identity still equal the current UI identity and Activity not destroyed. For **every export**, only explicit Save authorizes the frozen payload; mismatch/cancel/lost snapshot cannot be reported as verified export. Current exact provider bytes are distinct from durability and provider honesty.

Adjacent problems not substituted: CSV versus arbitrary Office import; literal strings versus formula engine; projection versus anonymization; temporary unlink versus disk erasure; isolated UID versus hostile-host protection; kernel grant versus renderer consent; typed text versus a safe CSV dialect for every consumer; managed API34 versus physical/AX release; compiled placeholder versus testable LOK; compressed APK versus total system footprint.

## B — outcome space / thresholds

**H+**: bounded parsing, data-only transport, projection, verified new-file publication and document-owned consent can run under trusted platform/parent/caller assumptions. Evidence threshold: actual compilation/public-consumer checks, independent maintained readers, fresh named Android cases, and green whole relevant workflow before its scoped promotion.

**H−**: an Android guest/app cannot guarantee secrecy against arbitrary host/IME/privileged monitoring, user identity disclosure or previously recorded external activity. No universal lighter-than-Tails/system-anonymity predicate is met.

**H∂**: grammar/size boundaries, caller authenticity, document lifecycle callbacks, provider behavior, kernel/runtime, OS/hypervisor and license closure change the answer. Any missing interface downgrades the claim. A successful subset is not the full product predicate.

## C — frontier and imports

Eight searches and four fetch calls are recorded in [research/r21-primary.md](research/r21-primary.md), including exact primary URLs, hypotheses and limitations. This is not an exhaustive best-known theorem survey. Android isolated-service docs support a capability-based construction; Tails documents host/metadata/correlation boundaries. Tor distinguishes routing and browser defenses. RFC4180 is informational and has dialect/errata caveats; OWASP documents consumer formula interpretation. LibreOffice's mixed licensing page prevents promoting a top-level MPL label to dependency clearance. GeckoView exposes separate app/content permission callbacks; rolling examples are not a pinned ABI or an installed engine.

No imported result establishes our entire system. The remaining interface is concrete: real engine/frame callbacks and process lifecycle, auditable permissible binary/transitive payload, bounded IPC/provider semantics, physical/AX/fidelity and privacy threat-model verification.

## D — negative space / modal shortcut tests

| # | Attractive but false result | Detector / counterexample |
|---:|---|---|
|1|CSV rows are just split on newline/comma|Quoted commas/newlines/doubled quotes compared with Python csv |
|2|Accept malformed UTF8 with replacement characters|Malformed-byte refusals, no silent normalization |
|3|Keep valid prefix of oversized input|Byte cap/cap+1 and UI whole-edit refusal |
|4|Pad or drop ragged cells|Ragged-row native/host refusal |
|5|Infer types and lose leading zeroes|Actual openpyxl text-cell equality including000123 |
|6|Quoted CSV makes formulas harmless|OWASP boundary; use literal XLSX typing, no CSV export promise |
|7|Silently prefix apostrophes/tabs to 'sanitize'|Exact source/selected-cell preservation tests |
|8|Skip omitted columns only in the preview|Equal selected-cell output bytes across changed omitted columns |
|9|Call column projection anonymous|Selected names/identifiers remain; conditional countermodel |
|10|Call a digest proof of honest worker computation|Actual accept of a bounded forged-cell frame with matching metadata |
|11|Call a package process name isolation|Actual different UID/PID and permission checks |
|12|Give child arbitrary URI/path authority|Owned readonly unlinked FD, no source URI sent |
|13|Call unlinked input never-written/securely erased|UI explicitly discloses private temporary disk input; no erasure gate |
|14|Trust arbitrary Binder result lengths|Frame caps, huge-length/truncation/trailing attacks |
|15|Reuse a worker after failure|Single-use lease, fresh real child recovery test |
|16|Publish a worker result after input changed|Real in-flight main-thread edit rejects obsolete callback |
|17|Keep old export enabled after column edit|Actual UI selection invalidation test |
|18|Show only a prefix but imply complete review|Full bounded preview; row256 assertion; display escaping leaves export intact |
|19|Use Back as unconditional hide-keyboard|Initial missing-root failures; later explicit IME hide request |
|20|Treat recreated Activity as retaining picker snapshot|No saved hierarchy/payload; explicit lost-snapshot refusal |
|21|Call successful write verified/durable|Exact provider readback; separate unproved durability |
|22|Same URL means same document|Same-URL and A→B→A delayed-prompt tests |
|23|Validate navigation before revoking old grants|Actual first-host failure; invalidation-first repair |
|24|Approve delayed consent after close/shutdown|Prompt identity removal, shutdown and tab-ID tests |
|25|Consume ticket twice concurrently|Two actual JVM threads, exactly one success |
|26|Lease ledger means browser permission UI integration|Unsupported-claim policy; no delegate/renderer installed |
|27|MPL top-level means all LOK dependencies allowed|Primary mixed-license evidence; LOK still excluded |
|28|Green worker spike accepts later UI|Named-case evidence gate rejects missing UI cases |
|29|Cherry-pick passing boundary cases from failed UI run|Retained4/2 receipt is refused as a whole |
|30|APK size proves lighter anonymous OS|No comparable system/RAM/installed/functionality benchmark |
|31|API34 run proves physical/AX/API37 acceptance|Separate unpassed release gates |
|32|Conditional Z3 bound proves Kotlin parser/ZIP|Explicit unproved refinement/decoded-byte lemma |
|33|Sources/assertion counts mean full delivery|Separate48/49 subsets and0/49 acceptance ledger |
|34|Pass a new2MiB policy into a legacy512KiB reader|Actual UI failure; streaming exact-readback repair and real >512KiB provider attack |
|35|Accept matching prefix with trailing bytes|Exact comparator reads one extra byte and refuses; real provider append attack |

Modal error: `there exists a bounded passing implementation/trace` is exchanged for `for all adversaries, environments and interactions the full product predicate holds`. `test_r21_evidence.py` checks actual green spike, failed UI and earlier full receipts; rejects20 broader labels and mismatched candidate/suite/counts. This closed vocabulary is not semantic prose understanding or authenticated remote attestation.

## E — mechanism portfolio

The seven branches were recorded before implementation in R21-PLAN.md. Same author, not independent agents.

| Mechanism | Construction/artifact and established portion | Unresolved interface / cheapest falsifier | Gap scale |
|---|---|---|---|
|1. Grammar state machine|`CsvTable.parse`, strict UTF8, bounds, immutable table; real host and child execution|Other dialects refused; fuzz quote/CR/UTF8/cap boundaries against Python reader|Local grammar, not original full Office |
|2. Kernel capability transport|`FormatLease.evaluateOwnedCsv` + worker operation4; existing UID/FD spike reused, new3-case pre-UI spike passed|Compromised parent/OS, inherited writable capabilities, hostile parser code not proved; attempt mutable descriptor/private path/network|Local process boundary; hostile-host anonymity original-scale |
|3. Bounded reply protocol|FCT1 parser + `OwnedCsvRequest` defensive capture and metadata correlation|Correct metadata can accompany forged values; truncation/length/trailing fuzz and forged-cell counterexample|Local structural integrity, computation authenticity unresolved |
|4. Explicit projection/typing|Ordered column projection + original literal XLSX;480 external reader documents,80 equal-byte pairs|Selected values identify; OOXML text subset rejects some CSV; inspect all ZIP fields and actual target Office app next|Local disclosure reduction, not full fidelity/anonymity |
|5. UI transaction ownership|`CsvWorkbenchActivity`, fresh generation identity, full escaped preview, explicit new-document/readback|Picker death/provider stall/host secrecy/AX still require experiments; edit in flight/recreate/picker cancel|Local workflow with external provider boundary |
|6. Document-owned consent|`BrowserConsentSession`, private document identities, synchronized prompt/lease ownership|Real engine/frame/renderer callbacks and authentic consent missing; same-URL/ABA/invalid navigation and race|Local lifecycle kernel; full browser remains substantial |
|7. Evidence boundary|Lab, actual receipts, validator,3 conditional UNSAT +2 SAT witnesses|Offline metadata not authentication; mutate candidate/case/whole conclusion and compare new native runs|Local reproducibility, not universal verifier |

An eighth, repair-driven mechanism is bounded exact streaming equality (`ExactReadback`): it removes the incompatible legacy-limit interface and avoids allocating a second whole document. The exact unresolved interface is provider correctness/latency, not byte equality on the observed stream. Cheapest falsifier is a matching prefix followed by one additional byte. Its gap is local, not full Office or anonymous storage.

Each branch returns executable code/experiments, conditional proof or a counterexample—not just a status label. Existing source warnings/gates stay closed for full browser, Office/LOK and privacy OS claims.

## F/H — executed verification and resources

| Artifact | Observed result | Exact limit |
|---|---|---|
| Fresh production/public-consumer Kotlin jars | **376 assertions PASS** | Actual JVM code; not rendering or Android permission grant |
| Python csv/struct/zipfile + openpyxl3.1.5 | **493 requests:480 reader successes,13 refusals** | Authored corpus, not independent authorship or blind holdout |
| Omitted-column paired outputs | **80 pairs byte-identical** | Same runtime/environment; selected identities remain |
| Near-cap independent-reader stress | **6 cases PASS**; input up to65536 bytes; workbook **68629..586521 bytes**, child peak RSS **101012KiB**, producer **0.465s observed** | One host observation, excludes Python reader; not Android budgets or a performance bound |
| New XLSX corpus payload | **2141..12641 bytes** | Corpus observations, not worst-case Android/RAM budget |
| Z3 5.3.0 | **3 nonvacuous UNSAT;2 SAT witnesses** | No source/refinement proof; frame lemma assumes decoded bytes≤source bytes |
| Python tooling | **112 methods PASS** in `verification/r21-python.txt` | Metadata policy tests, not app-feature count |
| Fresh legacy regression | R19 **539 assertions/430 references**; R20 **642 assertions/760 references** PASS | Pure kernels/readers; Android shared-readback regression remains separate |
| No-Studio tools | `r21_lab.py doctor` and `host` actually executed | Local SDK/ADB absent; native run is remote |

All are our authored experiments; only the maintained external implementations are independent. No Excel/LibreOffice/physical/AX/counsel verification is invented. Conditional frame calculation: `65536 +4×8192 +12 =98316 ≤131072` bytes, assuming decoded field bytes do not exceed admitted CSV bytes. Manual grammar argument: separators/outer quotes/BOM are removed; doubled quotes collapse; strict UTF8 preserves remaining code points. This is a review argument, not an automatically checked compiler refinement theorem.

### Native ledger (exact candidates)

| Candidate / run | Outcomes | Meaning |
|---|---|---|
|4af1efe / full38846791100|101 PASS,0 FAIL,0 SKIP|Completed R20 baseline; no new R21 functionality inferred. |
|4af1efe / privacy38846791129|6 PASS,0 FAIL,0 SKIP|R20 offline companion baseline, not an R21 UI test |
|6cb8bbc / focused38847410562|3 PASS,0 FAIL,0 SKIP|New CSV/consent operation spike passed **before worksheet UI code** |
|6cb8bbc / full38847410503|104 PASS,0 FAIL,0 SKIP|Main builds/JVM/contracts/lint and pre-UI full regression green |
|883a8d9 / focused38847993916|4 PASS,2 FAIL,0 SKIP|Two touch-driven UI cases lost root before Preview; whole gate failed |
|7e82cc3 / focused38848179543|6 PASS,1 FAIL,0 SKIP|IME/recreation and Binder cap passed; actual Save readback failed. |
|883a8d9 / full38847993849|105 PASS,2 FAIL,0 SKIP|Same two UI failures; whole gate rejected. |
|7e82cc3 / full38848179561|107 PASS,1 FAIL,0 SKIP|Save/readback case failed; whole gate rejected. |
|ca32548 / full38848664160|107 PASS,1 FAIL,0 SKIP|Same Save/readback failure; partial-preview changes did not grant a pass. |
|ca32548 / focused38848664101|6 PASS,1 FAIL,0 SKIP|Full preview did not repair the independent readback budget mismatch. |
|ca32548 / Tasks38848664113|7 PASS,0 FAIL,0 SKIP|Older Task/Notes compatibility before readback repair; not worksheet acceptance. |
|b96f060 / focused38849172743|8 PASS,0 FAIL,0 SKIP|All new boundary/UI cases, real large-provider/trailing-byte attack passed. |
|b96f060 / Tasks38849172798|7 PASS,0 FAIL,0 SKIP|Existing Task provider/UI/undo and Notes compatibility after shared-readback repair. |
|b96f060 / full38849172796|109 PASS,0 FAIL,0 SKIP|Whole main workflow passed, including both debug builds/JVM/contracts/lint. |

A manual `gh workflow run android-privacy.yml` attempt returned HTTP403 Resource not accessible by integration; automatic push CI remained available. No new privacy run or pass was inferred.

Exact-candidate R21 focused and full evidence-policy invocations admitted only the named b96f060 experiments.

Main and privacy are distinct APKs. Full and focused suites are not substitutes. Retained earlier R20 intermittent Notes/Compose failures remain unresolved; subsequent green runs do not prove causal stability repair.

## G — second-order attacks / global compatibility

- Parser-specific: closing quote followed by ordinary data, bare CR, BOM-only/embedded BOM, empty file versus empty field, huge advertised frame length, every truncated prefix, exact65536 bytes,8192 empty cells, forged metadata-correlated reply.
- Projection-specific: source formulas, leading zeroes, quoted control characters, identity retained in selected cells, unsupported OOXML escaped token in omitted versus selected column, pairs varying hidden fields. Preview escapes formatting without silently changing output.
- UI-specific: completion arrives after real editor mutation; old export after selection edit; source survives save; recreation loses old payload; full row256 must remain reviewable; keyboard Back must not navigate away. Pending provider writes/remote stall and process death while picker open remain higher-information residuals.
- Consent-specific: same URL new document, ABA origin return, invalid destination, deny then replay, wrong kind, capacity64/65, half-open expiry, close/shutdown, real two-thread consume. Untrusted true/false 'consent' is still a missing interface, not magically authenticated.
- Global: main APK retains INTERNET; CSV source temporarily touches private disk. Offline companion is separate and unchanged. No new network route, cloud relay, telemetry, third-party engine or dependency license was added. Old saved files/tasks are not migrated or rewritten.

## I — repair ledger / trust reset

1. Initial navigation candidate validated destination before revocation. Actual host assertion failed when an old grant survived unsupported navigation (`r21-first-host.txt`). Repair invalidates first; delayed-prompt, absent-document and valid-navigation recovery attacks added. First log lacks a complete archived prepatch source manifest; current repaired source/jars are hash-bound. No falsely reproducible original commit claimed.
2. First UI test used Back to hide keyboard. Actual4/2 run lost worksheet root after that step. This supports the navigation hypothesis, not an instrumented causal proof. Later test uses the actual IME hide API, preserving physical touches for Preview/Save. At7e82cc3 the recreation/preview path passed but Save exposed the separate readback-limit failure; all UI cases passed only after the later readback repair.
3. Review caught stale job completion leaving busy UI and numeric generation wrap risk. Fresh identity and unconditional busy reset for obsolete callbacks; actual in-flight mutation test added. No prepatch native exploit claimed.
4. Review rejected partial preview as adequate complete review and 'draft not saved' as sufficient disk disclosure. Now all admitted selected cells are displayed (escaped only for display), row256 asserted, private temporary input stated explicitly. New host formatter attacks; all affected native gates reset.
5. Actual6/1 worksheet run reached Save but could not confirm readback. Source review found new2MiB route passed that configured limit into legacy TaskStreams, whose admitted maximum is512KiB. Repaired with `ExactReadback`: compare at most expected length+1 using an8192-byte buffer, reject truncated/altered/trailing bytes, retain existing Task input caps. Real host attacks include0/8192/512KiB/2MiB boundaries and overflow-before-consumption. New native attack writes a generated workbook >512KiB to real Downloads, appends a byte and requires rejection. The common readback path changed, so fresh Task/Notes7/7, R21 focused8/8 and full109/109 were required and passed at b96f060. This is finite provider evidence, not all-provider durability.
6. Initial solver script called nonexistent `z3.Arrays`; it failed before producing a result. Corrected to real `z3.Array`, reran all models, retained failure. Solver output is conditional, not a code correctness theorem.

## J — alternatives, residuals and next experiment

**Browser:** keep native first-party compositor/session goals. An isolated, pinned, allowed-license Gecko engine with real navigation/frame/media callbacks is an alternative to building a complete engine from scratch, but is not delivered by our controller. Reject old rolling examples as ABI proof. Same-origin top-level tabs do not model iframe principals, process reuse, renderer death, fingerprinting or Tor circuit isolation. The blocked generic WebView path is not promoted or used as fulfillment.

**Office:** bounded original text/table projections work without carrying a giant engine, but deliberately sacrifice rendering/styles/formulas/macros/import preservation. Full original OOXML/ODF engine or isolated audited LOK are distinct routes. LOK requires transitive license/build/ABI/16KiB, resource containment, safe-save/fidelity and hardware measurements. Remote office conversion would change offline/privacy guarantees and is not silently substituted.

**Sandbox:** current unlinked readonly capability uses known platform behavior with explicit trusted-parent assumption. Sealed shared-memory/memfd alternatives need actual mapping/seal/ownership tests on all target APIs; read-only FD alone does not revoke pre-existing writable mappings. Hard resource containment against arbitrary exploited worker code still needs platform-process controls, not only an in-process watchdog.

**Productivity/privacy:** column projection removes unselected data without mutating originals; typed XLSX avoids CSV consumer type inference in tested readers. It is not differential privacy or general metadata scrubbing. The small offline companion stays the strongest currently executed no-INTERNET boundary. A future Android-wide route needs user-authorized VPN lockdown plus an actual audited anonymity transport and browser defenses. Even that cannot hide the laptop/host or retroactively anonymize chat/GitHub. Compare full installed/RAM/startup/energy/function/threat models before any 'lighter than Tails' claim.

**Next highest-information experiment:** actual process death while DocumentsUI holds a fresh output URI, then verify no stale/new draft substitution and inspect any empty/partial document. Add hostile readback/provider-stall tests without fake providers, physical API34/36/37 with real IME/TalkBack/Switch/Voice/200% reflow, and real renderer callbacks before consent integration. Retain earlier intermittent full-suite failures and instrument main-thread focus/layout rather than guess at production causes.

Original full native compositor/browser/Office/Beam, Room/reminders, physical performance/16KiB, API37, complete keyboard/AX and legal/license gates remain unfinished. Compile36 is still a requested-compile37 deviation. No release or merge authorization is inferred.
