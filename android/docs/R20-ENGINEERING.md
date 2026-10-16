# R20 — bounded offline privacy, reduced-disclosure reports and browser permission leases

**Native Android, not a Tails clone or universal anonymizer.** An ordinary app cannot retroactively anonymize this conversation, GitHub activity, the laptop, other apps, existing files or a compromised OS. The strongest new construction is a separate small no-INTERNET APK with a deliberately volatile workspace. It does not replace the original first-party shell/compositor, Office engine or browser requirements.

## Evaluated artifacts

- [Main debug APKs and full 101-case run](https://github.com/jyvplay/OSB/actions/runs/38845693182), artifact `fusion-native-debug`.
- [Offline companion debug APK and six-case run](https://github.com/jyvplay/OSB/actions/runs/38845693136), artifact `offline-privacy-companion`.
- Tested application candidate: **f42193d5b97aae4d03399286029c101c9f73088a**. Later evidence/docs/tooling commits are not separately claimed as native-tested candidates. Downloadable artifacts may expire under repository retention settings.
- [Build/laptop/no-Studio instructions](BUILD.md#r20-laptop-try-out-and-checks-without-android-studio).

## Completion accounting

| Denominator / standard | Count | Percentage |
|---|---:|---:|
| Original 48 included modules with substantial testable subsets | 47/48 | 97.92% |
| New separate `:privacy` companion with substantial testable code/checks | 1/1 | 100% of this additional subset only |
| Combined included modules with substantial testable subsets | **48/49** | **97.96%** |
| Unestablished subset: LOK | **1/49** | **2.04%** |
| Fully specification-accepted combined modules | **0/49** | **0%** |
| Unfinished against full assigned scope/mandatory gates | **49/49** | **100%** |

The denominator increased because the no-INTERNET boundary requires a different application/UID, not another Activity in the network-enabled shell. No old module was split/renamed to manufacture progress. Four existing areas were deepened: automation/reporting, security/link projection, the browser session kernel, and literal OOXML package metadata. The new permission ledger is not browser permission UI integration. The original ledger still has 231 requirements and 36 cross-cutting obligations; all 30 formal spikes remain unpassed. The R20 contract is an additional request, not a claim those original obligations were completed.

## Runtime honesty

Actually used: git/gh, shell/filesystem, Python 3.11.2, cached Kotlin 2.2.21 and JRE 17.0.9, openpyxl 3.1.5, icalendar 7.3.0, Z3 5.3.0, fresh web search/page retrieval, and remote Gradle/Android managed API34 execution. Host SQLite 3.40.1 was observed, not used for persistence. `r20_lab.py doctor` observed **no local SDK/ADB**. No Android Studio GUI, physical device, independent agent, privacy/legal expert or AX reviewer was available. Concurrent tool/CI jobs are not independent agents.

All application execution reported below is real Kotlin/JVM or actual Android APIs, views, files/providers, sockets and screenshots. Generated task records and deliberately malformed inputs are adversarial test data, not mock applications/providers or simulated runtime evidence. API34 managed Android is not physical-device assurance. No VPN/Tor client, remote relay or unknown-license engine was added.

## A — formal model

### Objects and access

- Main app tasks retain the admitted immutable `TaskBook` domain: 0..128 active+archived records; canonical UUID; valid title/date/flags; bounded revision; strict FTS1/2 parser. Existing plaintext persistence and session undo semantics are unchanged.
- `TaskCounts` is a four-integer vector `(activeOpen, activeDone, archivedOpen, archivedDone)`, each 0..128 and sum ≤128. Its writer accepts this vector, **not a source book**, and emits one literal `Counts` worksheet.
- Link projection accepts explicit HTTP(S) with a valid host, no credentials, control/backslash/format characters or IPv6 zone identifiers, a valid port, and strict UTF8 ≤4096 bytes. It canonicalizes scheme/host/default port, preserves raw path (empty becomes `/`), and removes query and fragment. Presence flags are explicitly returned; the host/path can identify a person.
- Permission leases are in-process object tickets bound to profile, positive tab ID, exact normalized origin and kind. Capacity 64; lifetime 1..60,000 **milliseconds**; `issued <= now < expiry`. Trusted callers must supply real consent, monotonic elapsed time, authentic scope, and navigation/close invalidations. These calls do not grant Android permissions or implement a browser prompt.
- Offline companion `dev.fusion.privacy` is a different APK/UID from `dev.fusion.shell.pro/play`, has no Internet/storage/runtime permissions, no shared UID, no data-provider/import/export route, no saved payload/hierarchy, and no main-app persistence dependency. It uses original core security code, native framework views and Kotlin runtime, not Compose/WebView/Tor.

### Quantifier order and success

For **every pair** of admitted task books with the same four counts, generated workbook bytes must agree **under the same writer/runtime environment**, regardless of titles, UUIDs, dates, order or revision. This is payload noninterference for omitted fields, not anonymity, timing noninterference, differential privacy or removal of provider/filename/sharing metadata. Independent tests compare exact bytes and decoded cells.

For **every identified ticket**, at most one successful consume is permitted, and **every successful consume** must match scope/kind and the half-open lifetime. Wrong-context attempts burn the identified ticket. Clock rollback clears leases. This is conditional on trusted invocation and actual synchronization; it is not “there exists a later permission that overwrites any navigation.” Two real competing JVM threads are also exercised.

For a companion workspace in a trusted Android process, after its pause/discard/recreation path completes, the next visible editor must be new and empty; its undo must not restore the prior editor's text. No universal claim is made about old Java strings, framework/IME copies, swap, crash dumps or forensic erasure. The input bound counts retained **UTF-16 units**, not total heap. The link parser separately counts UTF8 bytes. The latest input patch refuses an oversized entire change rather than inserting a truncated prefix, including whole-value and range replacement paths.

Secure-window success requires a visible unprotected screenshot marker control and absence of that marker in two protected captures. No Internet success requires specific permission-denial errno for IPv4/IPv6 socket creation, not arbitrary IO failure. AF_UNIX transfer is a positive control and a counterexample to “all IPC blocked.” These are finite API34 experiments, not universally quantified OS guarantees.

Adjacent problems NOT substituted: offline for anonymous online browsing; APK size for OS/RAM/installed footprint; no Internet permission for all information-flow isolation; secure window for protection from host/IME/AX/cameras; clearing views for secure erasure; counts-only reports for a task backup or full Office; origin leases for browser consent/renderer integration; a VPN for fingerprint resistance; same-process library checks for hostile-kernel safety.

## B — outcome space and thresholds

**H+ bounded:** these least-privilege, volatile-UI, projection and lease mechanisms can execute under stated caller/platform assumptions. Thresholds: production/public-consumer compilation; exact reference/readback checks; actual named native outcomes for OS/UI claims; whole relevant workflow green for policy promotion.

**H− overstrong:** a normal Android application cannot guarantee that a privileged compromised host, identity-bearing user behavior, external IME/providers and all other apps are anonymous. This is a trust-boundary counterexample, not a claim that privacy engineering or the original software is generally impossible. No smaller/better-than-Tails comparison is accepted without comparable system/function/threat-model measurements.

**H∂:** answers change at UID/permission, lifecycle, first window publication, input and timestamp bounds, origin/consent provenance, output aggregation and verifier scope. A mathematical model or queued workflow cannot cross the missing implementation/native interface.

## C — fresh frontier and imports

Nine new searches and three primary-page fetches are recorded in [research/r20-primary.md](research/r20-primary.md), with exact hypotheses and limitations. This is a limited primary-source review, not exhaustive best-known-results verification.

Tails itself documents identity, metadata, endpoint correlation and hostile-hardware boundaries. [1](https://tails.net/doc/about/warnings/index.en.html) The Tor Project distinguishes network routing from tracker/fingerprint defenses. [2](https://download.torproject.org/compare-apps/) Android documents secure-window screenshot scope, not universal observer exclusion. [3](https://developer.android.com/security/fraud-prevention/activities) The IME no-learning flag is explicitly advisory. [1](https://developer.android.com/reference/android/view/inputmethod/EditorInfo)

Imported source documentation did not replace experiments: the first late-FLAG_SECURE experiment actually failed. Secure-before-first-publication was then tested, not assumed. LOK/Collabora descriptions did not clear any binary or transitive license gate. No authoritative theorem covering the full application was found or claimed.

## D — negative space and modal-shortcut detectors

| # | False solution shape | Concrete detector / bound |
|---:|---|---|
|1|Call a new Activity in the main UID offline|Separate APK manifest plus actual permission/socket check |
|2|Call a missing Internet permission “all IPC blocked”|Actual AF_UNIX socketpair transfers bytes |
|3|Accept timeout/unreachable network as permission denial|Require EACCES/EPERM, not any exception |
|4|Read FLAG_SECURE and declare pixels safe|Actual screenshot positive/negative controls |
|5|Enable protection after showing private content|Retained late-toggle capture failure; initial secure construction |
|6|Call secure window secrecy from IME/AX/host|Explicit trust model and unsupported-claim gate |
|7|Treat IME no-learning request as enforcement|Native flag check plus primary documented limitation |
|8|Clear EditText but keep its undo stack|Replace whole widget; old-widget identity and undo attack |
|9|Restore volatile text from saved hierarchy|Saving disabled; actual recreation starts empty |
|10|Accept an intent extra that disables security|Old spike flag/incoming-text injection attack |
|11|Let default copy/cut/paste/share escape policy|Native context-menu refusals; shortcut/action-mode code |
|12|Let rich IME content become implicit provider import|Actual InputConnection commitContent refusal |
|13|Keep executable URLSpan metadata in plain text|Native stock-EditText positive control; private widget strips spans |
|14|Silently truncate oversized edits|Native LengthFilter counterexample and whole-change refusal patch attacks |
|15|Lose old text when accessibility/setText filters against empty dest|Separate whole-value and Editable-range boundary checks |
|16|Confuse 4096 characters with 4096 UTF8 bytes|CJK amplification and malformed-surrogate host attacks |
|17|Strip query and call the link anonymous|Actual `/Alice` path remains; SAT countermodel |
|18|Decode raw percent escapes into another path|Python reference and Android Uri path comparison |
|19|Strip a secret query without validating malformed input|Malformed Unicode in removed query must still refuse |
|20|Call aggregates anonymous|Exact counts differ; SAT countermodel and explicit UI warning |
|21|Leave source IDs/revision in report properties/hash|Independent ZIP/cell inspection and equal-count byte comparisons |
|22|Call XLSX counts a backup/full Office engine|Projection type omits data intentionally; UI says not a backup |
|23|Reuse a permission on another profile/tab/origin/kind|Wrong-scope attack burns the ticket |
|24|Accept at expiry, overflow time or roll clock backward|Host boundaries, native elapsed clock and conditional models |
|25|Consume the same ticket concurrently twice|Two actual JVM threads, one winner; one-shot model |
|26|Infer actual browser prompt integration from a lease kernel|Policy refuses `browser_permission_UI_wired` |
|27|Cherry-pick passing R20 cases from a failing full run|Actual 99/100 receipt is rejected by the gate |
|28|Promote initial spike to later feature code|Two-case spike cannot satisfy six-case workspace requirement |
|29|Infer a displayed dialog from touch return|New bounded visibility predicate and repeated prompt/cancel attack |
|30|Call source presence, 642 assertions or 6 native cases full acceptance|Separate 0/49 full-acceptance ledger |
|31|Use fixed 1980 UTC timestamps and assume timezone-independent ZIPs|Four actual JVMs exposed different DOS fields/extra-field presence; fixed epoch repair checked separately |
|32|Use API35 setTimeLocal merely because desktop Java supports it|Primary Android API-level check; retain min34-compatible interface |
|33|Set EditText.error within filter and assume visible rejection survives|Actual 62b5c2f failure; separate status label with IME/deletion follow-up |

The modal shortcut swaps **some scoped behavior exists** for **every adversary/interaction satisfies the global predicate**. Native “INET denied AND Unix IPC works,” retained path identity, distinguishable aggregates, and the failed whole native gate are executable counterexamples. `test_r20_evidence.py` rejects 20 broader labels even with a genuine green privacy receipt. This is closed-vocabulary policy, not semantic understanding of arbitrary prose or receipt authentication.

## E — mechanism portfolio

Early branches were recorded in [R20-PLAN.md](R20-PLAN.md) before companion feature implementation. They are mechanism-distinct, not independently authored agents.

| Mechanism | Construction / concrete artifact / established portion | Exact interface and cheapest falsifier | Gap |
|---|---|---|---|
|1. Kernel capability removal|`:privacy` manifest/application; real INET permission denial, Unix positive control|Try a fresh actual socket; inspect merged package permissions. Other UIDs/IPC remain outside boundary.|Local app boundary; whole OS anonymity is original-scale |
|2. Display publication ordering|Secure flag before first window, Recents exclusion; `PrivacyPlatformTest` actual marker controls|Capture first/reopened protected surface against visible control; test physical MediaProjection/external display next.|Local screenshot path, not all observers |
|3. Volatile lifecycle ownership|Native `PrivacyActivity` rebuilds complete editors on pause/discard; state saving/clipboard/rich-content paths constrained|Pause/recreate, old undo, incoming intent, span, rich-content and oversized replacement attacks|Local UI retention; heap/IME/OS erasure is larger |
|4. Aggregate projection|`TaskCounts` accepts four counts, writes original literal workbook; actual provider/UI route plus 320 external readbacks|Change all omitted fields while keeping counts, compare bytes; inspect package metadata; reopen actual new URI|Local disclosure reduction, not anonymity/Office |
|5. Link component projection|`LinkDisclosure`, visible offline companion preview; 140 Python references|Credentials, malformed Unicode, percent escapes, byte amplification, path identity|Local offline transformation; tracking/correlation remain |
|6. One-shot origin leases|`PermissionLeases`, bounded synchronized map, deadline and invalidation; public-consumer/native clock checks|Race same ticket, wrong scope, rollback, expiry, 65th lease; wire actual navigation/consent next|Kernel local; full browser integration remains |
|7. Trust-reset tooling|`r20_evidence_gate.py`, `r20_lab.py`, actual receipts, 4 conditional UNSAT +2 SAT models|Wrong candidate/suite/counts; real failing main receipt; missing feature cases|Local reproducibility/policy, not truth oracle or expert acceptance |

An eighth mechanism is fixed package-metadata construction in `LiteralWorkbook`: use Unix epoch zero, with DOS pre-1980 clamping and fixed UTC extended timestamp where emitted. `check_r20_zip_timezone.py` binds current source hashes, runs four separate JVMs and inspects the produced packages through Python ZIP. The counterexample was recorded before repair. Cheapest falsifier: change timezone/runtime and compare bytes/metadata, then read cells independently. This is local ZIP disclosure reduction, not arbitrary-file metadata removal or universal reproducibility across ZIP implementations.

Every branch returns code, an executed finite experiment, a counterexample or a conditional model. No branch closes its unresolved interface by using a status label.

## F — external verification and counted resources

| Executed artifact | Actual result | Scope |
|---|---:|---|
| Separate Kotlin production jars and public consumer | **642 assertions PASS** | Includes real two-thread one-shot competition; not Android UI |
| Python references + actual Java + openpyxl | **760 cases PASS** | 320 counts reports (160 equal-count byte pairs), 140 links, 300 leases |
| Counts report payload | **2,563..2,567 bytes observed after metadata repair** | Corpus range, not all-format footprint |
| Fresh R19 regression | **539 assertions +430 references/readbacks PASS** | Legacy task changes/streams/multi-sheet writer unaffected |
| Python tooling | **103 methods PASS** | Includes corrupted offline receipt checks, not 103 app features |
| Z3 5.3.0 | **4 UNSAT with SAT premises; 2 SAT countermodels** | Conditional integers/Booleans, not Kotlin or OS refinement proofs |
| Four separate JVM timezone runs | Before: four different hashes; after: one identical hash | UTC, New York, Tokyo, Chatham; one empty-count report; not all environments |
| Fresh R13 regression | **503 assertions +404 cross-language cases PASS** | Includes 100 openpyxl workbooks and 104 icalendar events |
| Fresh R19 report stress | **30 real-Java/openpyxl cases PASS** | 3,414..240,566 output bytes; host child peak RSS 103,288 KiB, not Android budget |
| No-Studio orchestration | `r20_lab.py doctor` and `host` actually executed | Local SDK/ADB absent; host compiler/oracles available; native work is remote |

Sources, scripts and output identities are retained under `verification/r20-*`. The corpus was authored after implementation; it is **not** an independently authored blind holdout. openpyxl is an independently maintained reader. No Excel/LibreOffice fidelity test or independent expert audit is invented.

The 8b634f4 debug companion was actually **2,484,638 compressed APK bytes (~2.37 MiB)**. This includes a debug-only, non-exported, fixed-color screenshot control. It excludes Android, installed size, RAM, OS caches and development infrastructure. No comparison with Tails is inferred. At 62b5c2f the compressed debug APK was 2,485,686 bytes, SHA256 `7a0f6a7583732e472e8e71f6aa668dc187e064c6292cc8a777af546e481c147e`, but its native suite failed 5/6. The f42193d repair then passed 6/6, build and lint. Its compressed debug APK was **2,486,434 bytes (~2.37 MiB)**, SHA256 `80fcd7a90105f3cb1514412252f382515a7872acc832082fe147d2928ee0b2df`. Size remains distinct from functional acceptance.

## G — candidate-specific adversary and global compatibility

- **Separate UID:** remove Internet but deliberately attempt Unix IPC; a global information-flow claim is falsified. Main Shell still has Internet and application-wide cleartext permission for existing gated transports. The companion does not modify them or their data.
- **Secure display:** attack the exact timing of adding a flag after content publication, then test first publication and a secure relaunch after an unprotected control. The late failure is retained; no eventual-enforcement root cause is asserted from it.
- **Volatile editor:** attack old undo, reconstruction, incoming extras, text classifier, stock span behavior, default rich-content/drag routes, exact cap/cap+1, replacing existing text, and URI replacement. Managed heap copies and arbitrary trusted IME behavior remain residuals.
- **ZIP metadata:** the old fixed 1980 UTC instant still encoded local timezone in package headers. Four distinct old hashes falsified cross-timezone equality. Fixed zero-epoch output produced one hash in those four JVMs; reran R13/R19/R20 readers. A new Android timezone method is required by the evidence gate. Existing saved files are not rewritten.
- **Counts:** use XML-illegal/escape-like source titles that the full writer refuses; counts can still be emitted because those fields are explicitly omitted. Vary UUID/date/order/revision and compare payloads. Do not remove source data or label aggregates anonymous.
- **Leases:** attempt wrong-context consumption followed by correct replay; overflow/rollback; revoke another scope versus the intended scope; capacity and concurrent use. Untrusted consent provenance is not solved by a ticket.
- **UI/policy:** the actual main suite failed on an older Notes discard dialog while all new R20 cases passed. The gate refuses cherry-picking. A new displayed-dialog predicate and second prompt/cancel attack are fresh tests, not a demonstrated causal repair of all UI instability.

## H — actual native ledger

| Candidate / run | Result | Meaning |
|---|---|---|
|12b6360 / privacy 38842015884|0 executed; failure|Unsupported wrapper phase, before Gradle. Fixed phase; failure retained. |
|80ae8d9 / privacy 38842126545|1 PASS, 1 FAIL|INET/Unix check passed; late secure toggle screenshot still had 37,318 sampled marker pixels. |
|8871a48 / privacy 38842513660|2 PASS, 0 FAIL|Secure from first publication + separate positive control passed before workspace implementation. |
|8871a48 / PR build 38842518025|failure, not device evidence|Stale source inventory; regenerated review in subsequent feature candidate. |
|24f933d / full 38842588235|100 PASS, 0 FAIL, 0 SKIP|Counts/provider/UI, link and lease cases; both main builds/JVM/contracts/lint passed. |
|24f933d / focused 38842588195|6 PASS, 0 FAIL|Task and counts UI, including real no-op merge confirmation followed by undo. |
|abb7075 / privacy 38843065408|0 executed; compile failure|Kotlin synthetic classifier property was val; direct setter repair required. |
|eff309a / privacy 38843178753|0 executed; compile failure|Same classifier failure; rich-content patch did not inherit any pass. |
|8b634f4 / privacy 38843240703|6 PASS, 0 FAIL, 0 SKIP|Actual implemented workspace, socket/pixel/view/lifecycle/input checks; build/lint passed. |
|8b634f4 / full 38843240724|99 PASS, 1 FAIL, 0 SKIP|Older Notes backlink discard-dialog visibility failed; all R20 cases passed, **whole gate failed**. Both main builds/JVM/contracts/lint passed separately. |
|3db7fdb / focused 38844546774|7 PASS, 0 FAIL, 0 SKIP|Expanded Tasks/Notes displayed-dialog and repeat-cancel checks; not a production Notes causal diagnosis. |
|eb7f2c4 / full 38844681734|100 PASS, 0 FAIL, 0 SKIP|Later full main regression passed; no privacy companion or subsequent ZIP patch inference. |
|3db7fdb / full 38844546891|99 PASS, 1 FAIL, 0 SKIP|Notes save/recreation test failed with Compose SnapshotStateObserver cross-thread semantics diagnostic during performTextInput line192; no causal repair established. |
|62b5c2f / full 38844736662|99 PASS, 1 FAIL, 0 SKIP|Editor 200%-font action test failed with Compose SnapshotStateObserver cross-thread access at line308; no full-main promotion. |
|62b5c2f / privacy 38844736671|5 PASS, 1 FAIL, 0 SKIP|Oversized setter preserved empty content but error feedback was null at line75; build/lint succeeded, whole gate failed. |
|f42193d / privacy 38845693136|6 PASS, 0 FAIL, 0 SKIP|Build/lint and fresh separate-status feedback + IME overflow/deletion attack passed. |
|f42193d / full 38845693182|101 PASS, 0 FAIL, 0 SKIP|New ZIP timezone case passed; both debug builds/JVM/contracts/lint passed. Earlier 100-case run is insufficient for the strengthened counts gate. |

The fresh feedback/ZIP receipts are `r20-feedback-privacy-*` and `r20-final-native-*`, both exact candidate `f42193d5b97aae4d03399286029c101c9f73088a`. The companion, counts and lease evidence-policy checks admitted only their scoped claims. No pass is inherited across candidates. PR build 38845696577 also succeeded; it is not another device run. The focused workflow now includes the affected Notes method; focused counts are not a full-suite replacement. Main and companion are different APKs/suites and are never conflated into one device acceptance count.

## I — repairs and trust reset

1. Initial census still expected 48 after adding a separate APK → explicitly check 49 total and 48 originals; regenerate both ledgers. Retained actual failed Python test.
2. Wrong CI wrapper phase → use supported `device` phase; no pass inferred from zero tests.
3. Actual late secure-window failure → secure before first publication; protected/unprotected/protected captures. Recorded 2/2 spike before implementing the workspace.
4. Actual host malformed-Unicode exception contract failure → normalize specific charset/URI syntax exceptions; attack malformed text even in removed query and UTF8 amplification; 642/760 checks rerun.
5. Actual classifier compilation failure → call `setTextClassifier` explicitly; native regression asserts `NO_OP`, not merely successful compilation.
6. Source-reviewed default rich content/spans → reject routes/strip spans; actual stock EditText control and private widget/InputConnection checks passed at 8b634f4. No prepatch application exploit is claimed.
7. Source-reviewed input truncation and empty-destination setter semantics → refuse the whole edit and preserve previous content; add stock LengthFilter control, cap+1, existing-value setter, range replacement and viewport attacks. At 62b5c2f the native test failed because widget-owned error feedback was null. Repaired feedback through a separate status label; added actual IME commit at capacity and accepted deletion/feedback-clearing attacks. The repaired f42193d then passed all six companion cases on managed API34; no physical/AX/other-API acceptance inferred.
8. Actual Notes dialog visibility failure → observe visible dialog/decision control rather than assuming touch return means painted dialog; add second prompt/cancel and readonly model/window diagnostics. No sleeps, semantic click substitution or increased existing wait. The new predicate uses the established 10-second UI readiness budget. Causal UI-stability diagnosis remains open even if this candidate passes.

9. Actual cross-timezone ZIP witness → fixed Unix epoch metadata (not API35-only setTimeLocal); four fresh JVM equality checks plus R13 503/404, R19 539/430 and 30-report stress, and R20 642/760 rerun. Added a new Android case and made the counts evidence gate require it; historical green 100-case main receipts no longer satisfy that strengthened counts claim.
10. Census compilation evidence was stale → label privacy partial Android execution, retaining both 8b 6/6 and 62b 5/6 rather than treating either as current repair acceptance. An initially invalid evidence enum was rejected by structural/Python gates, then corrected to the existing enum and reports regenerated.

## J — alternatives, exact residuals and next experiment

**Browser:** retain own compositor/session architecture and real remote-engine surface work. Integrate the new lease ledger only after authentic tab/profile/origin/consent and navigation invalidation are wired through actual engine callbacks; test renderer death/rebind and two live views. A separately isolated audited Gecko/Tor-capable engine is an alternative, not a substitute stock WebView UI or an installed engine here. Exact origins are not PSL/eTLD+1 site isolation, and network routing is not fingerprint resistance.

**Office:** original bounded workbook projection is useful now; larger original OOXML/ODF import/render/edit engines remain substantial missing work. An isolated audited LOK provider could reuse upstream semantics, but payload/transitive licenses, JNI/ABI/16KiB, malicious-document containment, fidelity and safe-save corpus gates remain mandatory. A self-hosted remote engine would change offline/privacy assumptions and is not silently substituted.

**Sandbox/privacy:** the separate no-INTERNET UID is an executable alternative to trying to make a network-enabled main process globally offline with an in-app switch. It sacrifices online functionality and deliberately has no saved-data/transfer route. A user-authorized VPN lockdown route would need actual IPv4/IPv6/DNS/failure/restart/bypass verification and still would not anonymize application identity. Shared-memory sealing/ownership alternatives from R19 still need actual cross-process tests; the production unlinked-file transport was not replaced on partial evidence.

**Productivity:** explicit aggregate export reduces disclosure without changing the original task records. Durable journals/Room migration, multi-level undo and authenticated rollback policies remain alternatives requiring crash/replay tests. Encrypted-at-rest storage can reduce another threat surface, but it is not anonymous networking or secure managed-memory deletion. The ephemeral companion intentionally discards work on pause rather than pretending to recover it privately.

**Next highest-information tests:** reproduce the newer SnapshotStateObserver failures at Notes text input/editor 200%-font access, retaining actual focus/layout and main-thread state before failure. Do not blindly move Compose actions into runOnUiThread (which may block their synchronization) or treat diagnostic stacks as proven production root causes. Then reproduce the Notes touch/presentation failure with the added actual model/window observations; inspect IME/checkpoint reflow without guessed padding or timing fixes. Kill/recreate the main app while an actual task picker is outstanding and during interrupted provider write/readback. Exercise companion protections on physical API34/36/37 devices with real IMEs, accessibility, MediaProjection and external displays. Audit full merged/release dependency/permission surfaces before any release claim.

The original full browser, Office/LOK, Beam, parser containment, task reminders/Room, complete keyboard/AX/200%-reflow, API37, arm64/16KiB, physical budgets, commerce and legal gates remain unfinished. Compile36 is still a deviation from requested compile37; a runner catalog observation is not API37 acceptance. PR #5 remains open/unmerged; no merge authorization was supplied.
