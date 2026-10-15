# R13 — executable subsets, original Office boundary, and adversarial verification

## A. Result and actual execution

**34/48 modules (70.83%) now meet the existing bounded behavioral-testability standard; 14/48 (29.17%) do not. Fully specification-accepted modules: 0/48 (0%); unfinished: 48/48 (100%).** The increase from R12B is four modules: Files, terminal, automation and continuity. The existing OOXML module gains another tested transformation; it is not counted twice. A narrow kernel can qualify under this standard while almost all of its parent feature remains unfinished. This is not a percentage of Office parity or product delivery.

Actual work: five production Kotlin libraries compiled separately with Kotlin 2.2.21/JRE 17.0.9, then public consumers compiled and executed; **503 host assertions passed**. Actual Kotlin output passed **404 cross-language cases**: 100 SHA256/byte receipts against Python hashlib, 100 quoted argument vectors against the shared subset of shlex, 104 ICS events through icalendar 7.3.0, and 100 XLSX packages through openpyxl 3.1.5 plus ZIP/XML checks. **74 Python tests passed.** Counts are evidence units, not completed features.

Artifacts: `verification/r13-kernel-contracts.txt`, `verification/r13-interop.json`, `verification/r13-python.txt`, `module-testability.json`, and generated `MODULE-TESTABILITY.md`. The fresh host receipt binds both source and compiled JAR hashes; interop refuses mismatching receipt inputs. The external readers are host verification dependencies, not APK dependencies.

Tools actually used: Git, gh/GitHub Actions and API, Python, Kotlin compiler, Java runtime, independently maintained Python readers, existing source/traceability validators. No parallel agents, theorem prover, physical device, counsel or expert accessibility review was used. This workspace has no local Android SDK/device; remote Android results are recorded separately below. No TypeScript product, mock Office engine or simulated device was added.

## B. Admissible objects, resources and quantified contract

The original target remains Android-only, first-party compositor, local-first/account-free, constrained licenses, bounded isolated parsers and the original productivity/Forge specification. None of S1–S30 is promoted by these kernels. Current compile SDK36 is still a deviation from requested37; native arm64/4K+16K acceptance remains missing.

| Candidate and concrete artifact | Admissible domain and bounds | Positive obligation and tested limit |
|---|---|---|
| `core/files/.../ContentDigest.kt` | Caller-owned InputStream; cap0..64MiB;8KiB buffer; reads at most one excess byte to detect overflow | Receipt describes consumed bytes; real file change is detected. Assumes compliant InputStream behavior. No snapshot, identity, wall-time or mutation authority follows. |
| `feature/terminal/.../ShellWords.kt` | ≤4096 UTF16 code units/line,64 arguments,2048/argument; NUL/unclosed quotes rejected | Empty arguments, adjacent quotes and escaping are preserved under the documented local grammar. No expansions, execution, POSIX equivalence or PTY is implied. |
| `feature/automation/.../ActionPlan.kt` |1..16 strict LF-separated actions; only approved own-app module opens or workspace1..3 | Validate the entire plan before any effect; sequential dispatch stops on false/Exception and reports retained earlier effects. No rollback, persistence, scheduling, permission grant or retry. |
| `feature/continuity/.../CalendarExport.kt` | One UTC whole-second event,1970..9999, end>start; ASCII UID1..128; summary≤1024/description≤8192 UTF16; ISO controls other than LF rejected;≤48KiB output | Literal text escaping, strict Unicode,≤75 UTF8-octet physical lines, exactly one event under the tested reader. No recurrence, CalendarContract, reminder or sharing UI. |
| `format/ooxml/.../LiteralWorkbook.kt` | New single sheet;≤1000 rows,128 columns,20000 cells;≤32767 UTF16/text,2MiB aggregate UTF8; numbers≤15 significant digits/scale−100..100;≤16MiB output | Text/number/bool/empty cells in a five-part STORED ZIP; formula-looking text stays literal. Reject controls, invalid Unicode and ambiguous OOXML escape tokens. No existing document is transformed. |
| `app/src/androidTest/.../RealImeDeviceTest.kt` | Disposable API34 emulator, explicit opt-in, real configured IME,200% font, bounded waits | Observe actual window IME visibility and editor controls via Android APIs; real touch plus accessibility text editing, not Compose synthetic text injection. This is a test harness, not a new input method. |

The intended universal claims are restricted to those domains and assumptions. **Finite tests do not prove every admissible input or environment.** Timeouts, OOM, malicious stream implementations, concurrent input mutation, all OEMs/readers and physical performance are outside demonstrated coverage. No formal proof is claimed. Hashing bytes cannot make a blocked SAF provider cancellable. Output-size checks do not establish end-to-end Android memory or latency budgets.

## C. H+, H−, boundary and thresholds

- **H+**: the five pure components implement their bounded transformations, and the actual Android host can dispatch approved automation actions without bypassing existing gates. Host threshold: separate production compilation, concrete positive effects, malformed/budget attacks and source-bound receipts. Android integration requires actual Android execution, not host inference.
- **H−**: a compatible-looking format drops data; the plan executes a valid prefix of an invalid program; a callback failure silently continues; a hash is treated as identity; terminal quotes become command execution; an apparent IME test uses only synthetic input. Reject such a candidate on the first witness.
- **Boundary**: an external reader itself loses data, a consumer uses binary floating point, Android tests fluctuate, a stream blocks, or device/API behavior differs. Keep the counterexample; narrow the claim rather than weaken the input-preservation condition.
- **Original acceptance threshold**: full required workflows, license/transitive inventory, correct isolation, failure/recovery matrices, API34/36/37 and physical/AX/performance gates. Not crossed here.

## D. Retrieved primary interfaces and imported results

`research/r13-office-primary.json` records three re-retrieved files at **LibreOffice/core@0f91d58472b9a1b3289e070e4c03335d5935b4eb**, with SHA256 and Git blob verification:

1. [COPYING.MPL](https://github.com/LibreOffice/core/blob/0f91d58472b9a1b3289e070e4c03335d5935b4eb/COPYING.MPL): license terms, not a license audit of the entire engine or its dependencies. Source inspection does not automatically make a rewrite independently authored or free of obligations.
2. [LibreOfficeKit README](https://github.com/LibreOffice/core/blob/0f91d58472b9a1b3289e070e4c03335d5935b4eb/libreofficekit/README.md): architecture context, not evidence that an Android payload builds or runs.
3. [LibreOfficeKit header](https://github.com/LibreOffice/core/blob/0f91d58472b9a1b3289e070e4c03335d5935b4eb/include/LibreOfficeKit/LibreOfficeKit.h): documentLoad/saveAs/paintTile/postKeyEvent/getPart interface surfaces. The open obligations are real engine behavior, ABI/lifecycle, licensing, dependency closure, isolation, fidelity and hardware budgets.

No LibreOffice implementation bodies were incorporated. The same agent inspected these sources and authored the new writer; **no independent clean-room or legal-clearance claim** is made. The writer uses a different bounded API and is not a LibreOffice implementation.

The published Kotlin2.2.21 compiler tarball was integrity-checked before extraction. Independent reader results are imported only under the exact recorded reader versions and corpus. No Excel/LibreOffice runtime was executed. Prior browser/sandbox primary-source investigations remain in `research/r12-primary.json`; this round does not pretend to repeat or certify all their gates.

## E. Six mechanism-distinct candidates and independent boundaries

The candidates were separated in `R13-PLAN.md` before implementation. They are not six wrappers around one missing engine. Libraries1–5 compile separately and communicate with their consumers through public APIs. This is architectural independence, **not independent authorship or expert review**.

| Mechanism | Artifact / observed portion | Candidate-specific attacker | Gap and next falsifier |
|---|---|---|---|
| Streaming cryptographic accumulation | ContentDigest; actual file/readback, short reads, excess-byte consumption and provider exception tests; hashlib differential | Zero-progress reader, changed bytes, early EOF/over-cap bytes, ownership confusion | Blocking/concurrently changing provider; next: actual SAF provider cancellation/version test before any mutation authority |
| Finite-state lexical parsing | ShellWords; quotes/empty args/bounds;100 shlex-quoted vectors | Unterminated quote, trailing escape, NUL,65th argument, oversized argument | Grammar intentionally differs from POSIX inside double quotes; next: native LocalShell adapter and hardware keyboard paths, not shell expansion |
| Typed bounded action interpreter | ActionPlan plus actual `Routines.kt`/MainActivity callback; real host filesystem effects, abort and no-effects-on-invalid-plan tests | Valid prefix followed by forbidden command, callback false/throw, implicit consent | Actual host dispatch has a separate Android test; next: recreate/cancel/focus/permission-denial matrix. No scheduler contract claimed |
| Unicode wire serialization | CalendarExport; octet folding, date bounds, injection and104 independently read events | CRLF property injection, lone surrogate, backslashes next to escapes, long multibyte lines | Old reader failed its own roundtrip; next: Android calendar/importer and other calendar clients without changing expected literal data |
| OPC/OOXML package construction | LiteralWorkbook; CRC/XML/relationship structure and100 independent workbook reads; formula-text and budget attacks | XML metacharacters, formula-like text, `_xHHHH_`, oversized dimensions/text/numbers | No importer/layout/edit/recalc. Next: controlled actual Office reader corpus and original import/edit design, not a parity label |
| Real OS observation | RealImeDeviceTest; retained API34 PASS in inherited run | Missing/disabled IME, clipped controls at200%, activity recreation, synthetic-input false positive | The stronger Compose IME case passed at f0cc82d after earlier failures; the whole suite still fails. Next: isolated and whole-suite repetitions with recorded IME/focus/insets and thread access. No physical AX acceptance |

## F. False-solution ledger (20 explicit detections)

| False solution | Detection / disposition |
|---|---|
|1. Count directories as modules delivered | Require real behavior/source/check pairing; separate0/48 acceptance ledger |
|2. Count503 assertions as503 features | Assertions are reported separately from34 qualifying modules |
|3. Call literal XLSX export full Office | API/limitations explicitly exclude import, layout, edit and recalculation |
|4. Call text extraction native document rendering | Existing extraction remains explicitly lossy; no renderer result exists |
|5. Treat an API header as engine availability | Require payload build/load/tile/edit/save and ABI tests; gate stays closed |
|6. Rewrite inspected source and declare it license-free | No clean-room/legal assertion; dependency/license review remains required |
|7. Read through one buggy oracle and alter valid escaping | Reader self-roundtrip calibration; retain both failing6.3.2 and passing7.3.0 receipts |
|8. Delete difficult backslash fixtures to get green | Original randomized corpus retained; four concrete counterexamples added |
|9. Supply a default successful no-op automation callback | Null callback disables Execute; real MainActivity dispatch installed |
|10. Execute valid actions before discovering invalid later syntax | Entire plan parses first; malformed-prefix side-effect count must remain0 |
|11. Pretend aborted actions rolled back | Result reports completed actions and retained prior effects |
|12. Call builtin argument lexing a Unix shell/PTY | Help/API explicitly reject this; double-quote non-POSIX boundary is tested |
|13. Use SHA256 equality as stable file identity | Receipt documentation denies identity/snapshot/mutation authority |
|14. Use a byte cap as a provider timeout | No wall-time guarantee; required next provider test remains open |
|15. Call ICS export CalendarContract/reminders | No CalendarContract/share integration claimed or counted as complete |
|16. Use Compose text injection as proof of real IME visibility | Separate actual WindowInsets/UiAutomation harness and named result |
|17. Discard old editor failures after a newer isolated pass | Inherited49 PASS/2 FAIL receipt retained; neither test removed |
|18. Promote a process name into security isolation | Require UID/authority/escape/watchdog evidence; no isolation acceptance |
|19. Treat source checks/green host suite as an Android build | Separate exact revision, build job and emulator outcomes |
|20. Treat reader success as decimal/rendering/full-format fidelity | Corpus and versions named; binary numeric and format/layout limitations remain explicit |

## G. External verification, failure, repair and new attack

The first interop run **failed**, at calendar text equality. Minimal calibration showed icalendar6.3.2 loses literal backslashes even on **its own writer's output**: `a\\;b`, `a\\\\b` and literal `a\\nb` changed. This establishes a reader-side counterexample independently of our serializer; it is not permission to ignore product failures.

Artifacts: `tools/probe_calendar_reader.py`, `verification/r13-calendar-reader-6.3.2.json` (three mismatches), and `verification/r13-calendar-reader-7.3.0.json` (four exact roundtrips). The product serializer was **not** modified to appease the old reader. The verifier moved to7.3.0, the original randomized corpus stayed intact, and four targeted backslash attacks were added through the actual Kotlin exporter. Full rerun: **404/404 PASS**. The repaired verifier inherited no pass from the old run.

Additional final host attacks cover short reads, excess-byte consumption, IO propagation, exact lexer lengths, deliberate non-POSIX behavior, invalid-plan prefix effects, maximum event dates/field lengths, exact20000-cell acceptance and malformed escaped-string tokens. A subsequent boundary review found that the initial calendar control filter missed DEL/C1 characters. The supported domain now explicitly rejects ISO controls except LF, with DEL and C1 attacks added to host and device tests. This product hardening was not trusted from the prior pass: all five libraries and consumers were freshly rebuilt, **503 host assertions passed**, followed by **404/404 interop cases**. The same agent authored the corpus; maintained external implementations provide a different implementation, not an independent reviewer or held-out corpus.

The new native calendar test exposed a second verifier bug: `(?m)^BEGIN:VEVENT\\r$` returned0 on a correct CRLF-delimited event. A separately compiled minimal Kotlin probe reproduced0 for that regex and1 for exact CRLF-line counting (`verification/r13-calendar-regex-counterexample.json`). The assertion now counts exact physical BEGIN/END lines, with host regression checks added. This is not a serializer repair; the native rerun of the corrected assertion is still pending.

## H. Android evidence and unresolved regressions

- Inherited candidate **a88cb21**, [run38684747487](https://github.com/jyvplay/OSB/actions/runs/38684747487): retained **49 PASS/2 FAIL/0 SKIP**. The new `RealImeDeviceTest.realImeAndLongEditorTextAtTwoHundredPercentWithoutComposeInputInjection` passed. The editor literal-replace test still saw `one one` instead of `two two`; the200%-font Compose test failed with multithreaded SnapshotStateObserver access. These are not explained away as harmless harness issues. Full details: `verification/r13-inherited-ime-*`.
- R13 production candidate **f0cc82d**, [run38685789036](https://github.com/jyvplay/OSB/actions/runs/38685789036): build job completed successfully (both debug flavors, invoked JVM/contracts and lint). Managed-device result: **55 PASS/2 FAIL/0 SKIP**. The five new kernel/adapter cases yielded four PASS and one FAIL (the calendar regex counterexample above). Actual automation→Calculator UI dispatch passed, as did the plain real-IME and stronger Compose IME cases in this run. The other failure remains editor literal replacement (`one one` instead of `two two`). Exact receipts: `verification/r13-native-f0-*`. The workflow is **failed**, not green.
- Five `R13SubsetDeviceTest` cases and an actual automation→Calculator UI case were added. Their exact observed outcomes are separated above; the calendar test repair is not yet an Android pass. The nullable Automation host API preserves historical regression compilation while disabling execution without a real host.

No physical API36/37, complete keyboard/TalkBack/Switch/Voice validation, production APK clearance or legal gate is established. Stronger earlier tests remain present. The production source inventory was regenerated after the calendar control hardening. That hardening and the expanded tests postdate the f0cc82d build; they require their own native run and inherit no Android pass.

## I. Viable alternatives and the full Office request

**The requested honest, complete1:1 LibreOffice rewrite is not delivered.** No defensible percentage of that parity can be calculated from this corpus. A single-sheet literal writer is useful original functionality but is not a substitute for the contract.

| Direction | Concrete decision artifact / status | Hard boundary and next experiment |
|---|---|---|
| Original document engines, one behavioral slice at a time | LiteralWorkbook and R13 corpus are an implemented first slice | Need original document models, import/export, font/layout/render, editing, recalculation, charts, pagination, undo, scripting policy and every required format. Pin upstream behaviors/tolerances/corpus before differential parity claims |
| Isolated LibreOfficeKit | Pinned license/README/header inspection; existing Office gate remains closed | Candidate for broad capability, **not a from-scratch rewrite**. Must audit full payload/transitives and legal packaging, build arm64/16KiB, run real edit/render/save/crash corpus. A boundary does not automatically cure incompatible dependencies |
| Headless POI route | Existing `research/r12-primary.json` and FF02 contract; not newly shipped here | Candidate for allowed read/write/convert/extract after audit. Does not provide Writer/Calc/Impress rendering or1:1 engine behavior |
| First-party desktop browser over platform web surfaces | Existing BrowserSessions/BrowserSessionCodec and first-party BrowserScreen; previous remote-surface spike | Real tabs/groups/splits/permissions/download/session/compositor work still required. Same-UID WebView process is not the specified isolation; surface lifecycle and authority must be tested |
| Own browser engine payload | Research/decision alternative, no new payload | Separate license/dependency/ABI/security/update spike before integration; engine replacement alone does not supply first-party desktop UX |
| Sandboxed worker with explicit brokers | Prior native process/FD/remote-surface experiments and `research/r12-primary.json` | Need minimal authorities, real isolated UID proof, watchdog, bomb/cancellation/crash tests and no-fork/resource support on Android; host Linux probes are not Android proofs |
| Platform-native productivity contracts | CalendarExport plus existing editor/notes/calculator kernels | Real CalendarContract, ContactsContract/API37 picker, IMAP, PDF/image edit, reminders and lifecycle paths remain separate work. Picker/mail handoff is not native mail or complete calendar |

External Office apps, cloud Office, generic browser wrappers and system cast buttons are **not** accepted replacements. Legal device gates for Beam/AirPlay/Cast and the original receiver separation remain unchanged. No new runtime library or engine dependency was added to bypass the license policy.

For1:1 parity, the minimum comparison definition still needs a named upstream behavioral revision/platform, required formats (docx/doc/xlsx/xls/pptx/ppt/odt/ods/odp/rtf), render/font and numeric tolerances, error/repair semantics, macro policy and edit/save sequences. The inspected revision names a source baseline, not a completed behavioral oracle. Full parity conflicts with an unrestricted interpretation that includes features explicitly prohibited by this product's macro/license/security constraints; the admissible parity domain must respect those exclusions, not silently omit them.

## J. Exact residual gap and next tests

1. Re-run the corrected calendar assertion, control-character attacks and expanded tests on the final branch revision; do not transfer f0cc82d results to changed bytes.
2. Diagnose editor replacement and intermittent off-main snapshot failures with isolated and whole-suite runs, retaining all failures. Read-only before-apply/after-apply/after-close observations of the actual Activity-owned EditorViewModel were added to the replacement test, without changing its success assertions or dispatching edits through the model. Their native outcome is pending; no production editor repair is claimed. A passing plain-IME harness is useful discrimination, not proof of the cause of the other failures.
3. Integrate calendar export only through an explicit user action and safe-save/share boundary; then test actual Android importers and permission-denial/lifecycle paths. Files digest must not unlock source deletion.
4. Keep original Office work bounded and explicit. Next useful falsifier: real reader interoperability beyond the current Python reader, followed by an original importer/model/edit/save slice with fidelity reports. No engine-parity promotion from this writer.
5. Continue the14 modules without established qualifying subsets, but do not manufacture positive counts from denial gates or source presence. Audit all new dependencies and execute the applicable pre-feature spikes.
6. Physical/API37/ABI/isolation/AX/legal/performance budgets remain unpassed. No new acceptance is granted by this report.

Reproduction and exact build entry points: **[BUILD.md](BUILD.md)**. Census regeneration: `python3 tools/module_testability.py --check`. All results are bounded engineering evidence, not full-product completion.
