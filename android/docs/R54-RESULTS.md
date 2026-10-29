# R54 — bounded Office authoring, evidence and remaining gates

**Date:** 2026-10-29. **Current scope:** experimental writer-to-DOCX and single-sheet-to-XLSX copy paths, not a Class A Office suite. Original implementation; no third-party engine was added to the APK. Use disposable data. [Build/evaluation instructions](BUILD.md); [module testability census](MODULE-TESTABILITY.md); [full-spec audit](MODULE-COMPLETION.md).

## Runtime honesty and census

- This sandbox has no Android SDK, `adb`, emulator, Android Studio GUI or physical device. The native code, instrumented tests and screenshot capture were **authored, not locally compiled or executed**. The first remote Android CI attempt ([run 40973921050](https://github.com/jyvplay/OSB/actions/runs/40973921050)) actually failed on nullable URI type errors in the export coordinator before device tests; the fix must be checked by a later run. The SDK-free Kotlin syntax gate is not a type check. Do not interpret a host preview, ZIP/XML read, test source or CI configuration as Android execution or visual approval.
- The reviewed, source-hash-bound census records **50/50 included Gradle modules (100%)** with at least one *bounded substantial testable subset*. This is a review of evidence pairings, including historical runnable checks; it is not a fresh run of every behavior or a claim that an entire module is functional. **0/50 modules (0%) are full-spec accepted; 50/50 remain unfinished** by the separate audit. Presence or quantity of kernels, tests or files cannot change that acceptance count.
- Original Office requirements PR-11, FF-01, FF-02, FF-11 and FF-17 and the S10/S11 security/fidelity gates remain incomplete. The existing `docs` importer remains blocked; this work does not route arbitrary Office inputs through it.

## A. Formal scope and transition contract

1. Writer accepts existing bounded plain-text editor state and explicitly exports a **new copy**: at most 128 KiB UTF-8 / 2,048 paragraphs; CRLF and CR become paragraph separators. Tested tabs, blank paragraphs, supplementary characters and surrounding spaces survive the emitted DOCX. This is neither DOCX import nor rich-text/layout conversion.
2. Spreadsheet drafts contain A1–Z100, at most 256 populated cells and 64 formulas. Explicit text/number/boolean/formula types avoid implicit formula conversion. Cells have byte and XML-character limits; formulas have a narrow allowed vocabulary and bounded references. A candidate must validate and recalculate before replacing the prior immutable draft. Edits, undo and redo are not external saves.
3. Local recovery uses a versioned, bounded, checksummed frame and Android `AtomicFile` with readback; recovery failure retains the original bytes and blocks overwrites. A checksum is not authentication, a backup, a confidentiality guarantee, or proof against power loss. Unapplied spreadsheet input is retained in the ViewModel across window hiding/configuration changes, but is not checkpointed and is lost on process death. Undo/redo history is also process-local.
4. An export is a frozen in-memory payload with a unique correlated request. It is refused if editor recovery or another editor operation leaves source identities unavailable. The selection must be a content URI, not a known/canonical source or recent destination, and readable as empty before output is opened. After writing, byte readback is compared. A stale callback cannot consume a newer request; process death does not replay writes. These checks cannot prove provider identity/freshness, prevent a race after the emptiness check, establish atomic creation or guarantee durability.

## B. Outcome space

- **H+ — host-verified:** the real JVM implementations passed the tests in section H; independent readers opened sample DOCX/XLSX and inspected expected content and formula caches. The Markdown bridge no longer hangs on the tested malformed-heading inputs.
- **H− — not accepted:** Class A writer/spreadsheet, arbitrary Office import, faithful layout/formatting, complete Excel formulas, release safety, OEM/provider behavior, native accessibility, broad visual usability and physical-device gates are not established.
- **H∂ — boundary:** Kotlin syntax parsing, source auditing, host file operations and a configured AVD workflow are useful but cannot replace Android build, real picker/grant delivery, device interaction, actual native screen inspection or power-loss/provider tests. Test callbacks directly supplied to the exporter do not exercise the picker.

## C. Research frontier and tradeoffs

- Android's [Storage Access Framework document-provider guide](https://developer.android.com/guide/topics/providers/document-provider) and [shared-document guide](https://developer.android.google.cn/training/data-storage/shared/documents-files?hl=EN) informed the explicit `ACTION_CREATE_DOCUMENT` copy flow and grant handling. Platform documentation is not a guarantee about every custom provider's aliases, races or persistence.
- Microsoft OfficeDev's [formula documentation](https://github.com/OfficeDev/open-xml-docs/blob/main/docs/spreadsheet/working-with-formulas.md) distinguishes formula expressions from stored cached values. `openpyxl` reads the tested caches; it did **not** recalculate them, and neither that library nor `python-docx` proves Word/Excel/LibreOffice fidelity.
- A LibreOfficeKit path could offer substantially greater fidelity than this small original implementation, but needs a maintained Android build, JNI/native assets, security isolation, runtime/performance evaluation and license/distribution review; [LibreOffice licensing context](https://wiki.documentfoundation.org/Development/Re-Basing/es) and a [public example Android integration](https://github.com/Shrawan-13/Wiyu) were reviewed as leads, not adopted or verified as a drop-in. No proprietary component or new runtime dependency was added.
- Pareto choice: a deliberately bounded, local-only authoring slice gives more useful application wiring and testable data ownership without bypassing the existing unsafe-import block; it trades away Office compatibility, rich editing and full-feature acceptance. The host-only independent readers are test tools, not APK dependencies.

## D. Misleading-result / negative-space catalogue

| # | Misleading shape | Guard or exact residual limitation |
|---|---|---|
| 1 | 50/50 testable subsets means 50 complete apps | Separate 0/50 full-spec audit; narrow positive rows may cover one kernel. |
| 2 | Kotlin parsed means Android builds | Syntax-only gate says so; real Gradle compilation is a separate CI/device gate. |
| 3 | Test file exists means a test ran | Authored Android tests are labelled unrun locally and require recorded CI results. |
| 4 | Host diagram is a screenshot | No native image is claimed until a real captured frame is obtained and inspected. |
| 5 | ZIP opens means Office compatibility | ZIP/XML and two independent readers check selected generated examples, not all suites, fidelity or hostile input. |
| 6 | Formula cache proves calculation in Excel | Cache checked against explicit expected values; readers do not calculate. Differential recalculation in real spreadsheet applications remains open. |
| 7 | Text beginning `=` is harmless in every representation | Explicit text kind is emitted as an inline string; verified with openpyxl on a sample, not universal cross-application behavior. |
| 8 | Cycles, malformed formulas or dynamic links silently succeed | Host tests refuse selected cycles, invalid syntax, unsupported functions, outside-sheet and external references; exhaustive engine semantics remain open. |
| 9 | Rejected edit leaves a partially changed sheet | Candidate recalculation precedes immutable transition; host verifies a rejected cycle leaves the old draft intact. |
| 10 | CRC/checksum establishes authenticity or backup | Recovery SHA-256 detects sampled corruption/truncation but is unauthenticated, local and not power-loss proof. |
| 11 | Latest UI field automatically survives process death | Only applied cells are checkpointed; unapplied input and undo history are explicitly process-local. |
| 12 | Different URI strings must be different files | Known and canonical identities are checked; undiscoverable aliases remain possible. |
| 13 | Empty destination cannot be overwritten by someone else | A check/act race and provider-specific semantics remain; empty read is not an atomic lease. |
| 14 | Correlated callback proves real picker grants | In-memory ID race is host-tested; MediaStore tests directly inject callbacks. A separate end-to-end DocumentsUI case is authored but not yet executed locally. |
| 15 | Byte readback proves durable save | Readback means observed bytes then; provider sync, atomicity and power-loss durability remain unknown. |
| 16 | Cancellation or stale result can consume the next export | Frozen payload has one ID and one claim/cancel; concurrent/late-ID host checks exercise this, not every Android lifecycle. |
| 17 | XML text round-trip automatically preserves CR/tab/Unicode | Explicit tab/break and CR escapes plus sample independent-reader checks; writer intentionally normalizes line endings. |
| 18 | Non-heading `#tag` is a normal heading | Former Markdown bridge loop now consumes non-headings; host checks include `#`, `#tag`, invalid depth and mixed input. The bridge remains a lossy subset, not CommonMark. |
| 19 | A prior success marker proves the current run | Commands are rerun after repairs; build artifacts are generated files, not universal evidence. The first concurrent Python run saw incomplete fixtures and was rerun after generation. |
| 20 | Emulator acceptance means real hardware/accessibility | Even a future AVD pass cannot establish physical OEM, TalkBack, Switch Access or all viewport/provider behavior. |
| 21 | `ROUND` silently clamping precision is a valid computed answer | The existing shared evaluator clamps digits to ±12; this new authoring path refuses `ROUND` until correct semantics are implemented and independently checked. |
| 22 | Small input implies small derived string | Repeated concatenation can grow exponentially. The authoring path refuses `CONCAT` and `&` until a derived-value resource budget is implemented. |

## E. Mechanism-distinct portfolio (not six versions of the same test)

1. **Explicit typed source model:** text/number/boolean/formula admission prevents accidental re-interpretation; typed inputs impose constraints and are not a general spreadsheet file format.
2. **Transactional validation and dependency recalculation:** immutable candidate plus cycle/refusal handling preserves the old draft, but the limited evaluator is not Excel's calculation engine.
3. **Interchange serialization and independent parsing:** bounded original DOCX/XLSX ZIP/XML writers create real files, inspected by independent readers; sample acceptance is not a fidelity corpus.
4. **Private recovery:** a versioned bounded checksum frame plus checked AtomicFile readback catches selected damage; not backup, encryption or an authenticated log.
5. **One-attempt correlation:** copied payload and atomic claim/cancel avoid one class of stale-result races; no durable exactly-once delivery.
6. **Cautious provider I/O:** known-alias and emptiness refusal, then verified byte readback; no provider-wide alias or TOCTOU proof.
7. **Capability boundary:** the route requires consent for the new sheet and leaves the arbitrary Office importer blocked; this sacrifices import to avoid silently bypassing security review.

## F. Reproducible artifacts

From `android/`, first configure maintained Java 17/Kotlin 2.2.21, or use the documented SDK-free `tools/bootstrap_host_tools.py` for disposable host tests:

```sh
python3 tools/run_r54_contracts.py
python3 tools/check_r54_reference.py --require-office-libraries
python3 tools/check_r26_android_syntax.py
python3 tools/check_project.py
python3 -m unittest discover -s tools -p 'test_*.py'
```

The R54 host runner produces `testing/build/r54/artifacts/writer.docx`, `sheet.xlsx` and `sheet.fsh` (ignored build products). `check_r54_reference.py --require-office-libraries` requires host-only `openpyxl==3.1.5` and `python-docx==1.2.0`; without the flag its stdlib ZIP/XML checks still run and report which readers were available. The Android Gradle task is `:testing:r54Contracts`. The authored disposable-AVD tests and screenshot capture are in `R54OfficeDeviceTest.kt`; the dedicated `.github/workflows/android-r54-office.yml` is configured to publish its actual reports/captures. A configured job or empty artifact is not a pass.

## G. Second-order adversary

The host suite exercises invalid UTF-8/XML inputs, malformed formulas, oversized data, checksum mutation, truncation, stale IDs, mutable caller buffers, 24 concurrent claims and parser progress. These are finite tests, not a fuzzer, hostile-provider proof or a time-bound performance certification. Particular residual hazards are malicious providers returning aliases or changing data between check/write, a crash between system and app state transitions, external app recalculation differences, and oversized/adversarial behavior outside the narrowly admitted authoring path. No Office importer was exposed to address them by assertion alone.

## H. Executed verification for this working tree (host only)

| Actual command | Result / scope |
|---|---|
| `python3 tools/run_r54_contracts.py` | **662 JVM assertions passed**; sample DOCX, XLSX and recovery bytes generated. |
| `PYTHONPATH=<host-reader-paths> python3 tools/check_r54_reference.py --require-office-libraries` | **21 checks passed**, including openpyxl 3.1.5 and python-docx 1.2.0; no formula recalculation by readers. |
| `python3 tools/run_jvm_contracts.py` | Core pilot, OOXML guards, editor, copy, calculator, notes, gate and other real pure-JVM contracts passed; Android excluded. |
| `python3 tools/run_r24_contracts.py` | **65,097** host assertions passed, including pilot policies. |
| `python3 tools/run_r45_contracts.py` and `check_r45_reference.py` | **508** host assertions plus differential checks passed, including existing DOCX/XLSX paths. |
| `python3 tools/run_r48_contracts.py` and `check_r48_reference.py` | **3,861** host assertions and **56** reference checks passed, including existing Markdown bridge cases. |
| `python3 tools/run_r50_contracts.py` and `check_r50_reference.py` | **8,555** assertions and **146** reference checks passed. |
| `python3 tools/run_r53_contracts.py` and `check_r53_reference.py` | **25,425** assertions and **411** reference checks passed. |
| `python3 -m unittest discover -s tools -p 'test_*.py'` | **1,520** tests passed after generated regression fixtures existed, the census was updated and historical headings were preserved. |
| `python3 tools/check_project.py`, `module_testability.py --check`, `module_audit.py --check` | Structural/source-hash/census checks passed; not Gradle or device execution. |
| `python3 tools/check_r26_android_syntax.py` | Selected Kotlin sources parsed; unresolved Android symbols are expected and not type-checked. |

The host Java 17.0.9 runtime and Kotlin 2.2.21 were installed in a disposable tool cache. Java 17.0.9 is old and is **not** the recommended maintained Studio JDK. The first R24 invocation lacked `KOTLINC` on `PATH`; it passed after explicitly setting the documented host-tool environment. These are actual outcomes, not simulations.

## I. Repair and re-verification

- Word tab/break handling and spreadsheet carriage-return escaping were changed after checking actual serialization; R54, existing R45, and independent readers were rerun. New samples do not prove complete OOXML behavior.
- Review found the shared evaluator silently clamps `ROUND` precision and permits concatenation growth without a derived-text budget. The new authoring route now refuses both until repaired and verified; host adversarial refusals were added. Existing legacy evaluator behavior was not relabelled as fixed.
- Remote Android compilation exposed a cross-module nullable-property error missed by syntax parsing; the selected URI is now held in a stable checked local. This repair requires a real Android compile rerun, not inference from host checks.
- A non-advancing Markdown parser loop on `#`-prefixed non-headings was repaired; the host runner has a timeout and regression inputs, and the existing R48 bridge suite passed afterward. No claim of lossless Markdown conversion is made.
- Changed source hashes were reviewed and updated in the census; ownership inventory and project checks were regenerated. An initial concurrent Python run observed missing/partial R50/R53 fixtures; after both generators completed the full suite was rerun. A later test flagged historical headings changed in documentation; the headings were restored and all 1,520 tests passed. An earlier incorrect test discovery path was corrected. No failed run is relabelled as a pass.

## J. Stopping rule and remaining acceptance work

The pre-commit threshold for this slice is actual host execution of changed behavior, independent Office inspection where feasible, affected regressions, and explicit native/device disclosure—not Class A acceptance. Remaining work includes: Android SDK compilation and AVD execution on this exact revision; inspection of actual captured Writer and sheet frames at multiple sizes/font scales; end-to-end real system picker, grant/lifecycle, OEM and malicious-provider testing; process-death/recovery and storage fault matrices; keyboard/IME, accessibility and performance measurement; Office-suite differential recalculation and fidelity tests; safe isolated import and broader writer/spreadsheet functionality; and physical hardware/safety/release gates. The PR should remain open while these are unresolved.
