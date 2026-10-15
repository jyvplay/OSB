# R10 — productivity / Forge engineering and acceptance record

**The requested production-ready beta is not delivered.** Real Android-source and
host-kernel changes were made; major features remain absent, not merely untested.
No mock browser, delegated Office replacement, synthetic screenshot or fabricated
spike pass was substituted.

- **0/48 included modules accepted (0%); 48/48 outstanding (100%).**
- Latest requested parent scope: **0/13 Productivity and 0/13 Forge accepted
  (0% each)**. Browser **0/19 a–s subfeatures accepted (0%)**. Browser subfeatures
  are nested under PR-07, not nineteen additional included modules.
- **177,198 JVM assertions passed** across twelve separately compiled pure
  libraries; **57 Python test methods passed**; **707 additional independent-
  implementation/oracle checks passed**. Counts are not feature-completion scores.
- **CI run38656329782 at86b4621 passed both debug-flavor assembly, all invoked
  JVM unit tests, production contracts and both-flavor lint; debug APK upload
  succeeded.** [Run / downloadable artifacts](https://github.com/jyvplay/OSB/actions/runs/38656329782).
  The managed-device job was **skipped**. No installed APK, runtime/AX/latency,
  physical-device, isolation or beta acceptance is inferred.


## A. Actual tools, admissible objects and success model

Used: real repository file tools, Bash, Git/`gh`, Python3.11 standard library,
Temurin17.0.9 runtime from jdk4py and Kotlin2.2.21 from npm. Executed production
Kotlin, actual temporary-file writes/readbacks, historical serialization,
compressed archives, Python codec/ZIP oracles, recursive edit-distance oracle,
source/traceability checks and GitHub APIs. Tool caches are not product assets.
No new APK dependency was introduced. Upstream source excerpts are research,
not vendored runtime code or a completed transitive-license audit.

`verification/r10-environment.json` records absent javac, SDK platforms/build tools
and ADB. No local Gradle/Android build, emulator, physical device, Apple receiver,
legal opinion, independent agent or theorem prover was used. Real remote Android
CI was executed after pushing the existing draft PR, using the workflow's
Ubuntu24.04 runner, Temurin17 JDK and SDK36/build-tools35+36 setup. The older run38648345516
failed assembly after SDK installation, but its full logs were unavailable.
New run38652102783 identified an actual compiler error: `IntArray.mapNotNull` in
Peripherals. Conversion through `asIterable()` was applied. Run38652511085 then
identified missing public `DocumentFile` classpath exposure; `core:files` now
exports the existing dependency because its public signatures use that type.
Run38652913588 **passed both-flavor assembly** and then failed the JVM unit-test
step. This is not a green workflow or device test. Diagnostic capture now covers
assembly, unit tests, contracts and lint without changing each Gradle exit status.
Final run38656329782 at86b4621 passed every build/test/lint/upload step; its
managed-device job was skipped. This final result supersedes the intermediate
failures above, without erasing them or promoting any feature acceptance gate.


The local snapshot incorrectly had HEAD at7449dfd while R8 was already pushed as
3f3bb79. A fetched-tree byte comparison found310 identical remote files and only
six intentional R10 edits among differing remote-tracked paths. HEAD/index were
reconciled to3f3bb79 with a **mixed**, not hard, reset; every existing working file
was hash-checked unchanged. See `r10-git-reconciliation.json`. No branch switch,
duplicate R8 commit or working-file discard occurred.

Admissible editor input is a bounded byte string, not arbitrary Office/binary
content. Supported representations are strict UTF-8, UTF-8+BOM, explicit BOM-less
UTF-16 LE/BE, BOM-bearing UTF-16 LE/BE, and explicitly selected Windows-1252.
Automatic selection recognizes BOMs, otherwise requires strict UTF-8; it does not
guess legacy encodings. UTF-32/ambiguous UTF-16-BOM+initial-NUL requires an explicit
supported choice. Malformed/unmappable input or output is refused without replacement.

Bounds:2MiB encoded input/output **and** canonical UTF-8 draft representation;
8 tabs; existing bounded undo/recovery budget. Unrepresentable newly edited legacy
text remains recoverable but cannot be exported in that encoding. This is not
large-file streaming or measured instant-open. Saved-content dirty hashes remain
canonical; verified-copy receipts describe the **actual encoded bytes**.

Diff uses immutable line scripts, preserving CR/LF/CRLF and final-newline
semantics:≤2048 lines/side,≤1,000,000 DP cells,≤2MiB canonical bytes/side. The native
adapter compares the bytes read from the **current source**, not a historical
baseline/three-way merge. Preview truncation is disclosed (200entries/1024chars
per entry); it never truncates the draft or writes the source. Concurrent source
mutation is not a provider snapshot guarantee. IO cancellation/watchdogs remain
an open native requirement.

OOXML limits actual consumed compressed bytes to64MiB, aggregate expanded bytes
to64MiB, each ignored/directory part to32MiB, each selected XML part to2MiB, entries
to5000, names to1024characters, cells/shared strings to20000 and rows to10000.
Selected XML uses strict UTF-8; unsafe/duplicate names and DTD/entity declarations
are refused. The own scanner remains a lossy **text extractor**, not an XML/OPC
validator, Office renderer/editor, POI or LibreOfficeKit. ZIP metadata is not
trusted for these byte limits. Local-header streaming is not central-directory
validation or authenticity. No macro execution engine was added.

Whole-product success is a conjunction: implemented requested behavior AND native
compilation/execution AND isolation/licensing/spikes AND device/latency/AX/security
acceptance. Quantifiers are not weakened to “a file exists” or “one example passed.”
Finite host tests establish their listed cases, not universal correctness or the
original product acceptance conjunction.

## B. Outcomes and evidence thresholds

- **H+**: these bounded host mechanisms are useful candidate building blocks.
  Listed executed cases support that claim; native integration still needs tests.
- **H−**: present production/beta acceptance fails because code and required
  observations are missing. This is not a proof the product is impossible to build.
- **Boundary**: unsupported encoding, over-budget files/diffs/archives and uncertain
  save outcomes must be refused or retained for review, not silently converted,
  truncated, overwritten or labeled successful. Refusal is not implementation of
  the omitted format/large-file/regex feature.

Evidence threshold: current source hashes plus executed compiler/tests for host
claims; actual both-flavor Android builds and device observations for native claims;
physical/legal evidence for corresponding spikes. Repaired candidates inherit no
pass from the failed predecessor. Source audits are not type checks.

## C. Primary results and the open interface

`research/r10-primary-frontier.json` contains two pinned OpenJDK17u sources,
Git-blob-verified with SHA256 and excerpts:

1. `ZipInputStream.closeEntry()` consumes unread entry data by calling `read`.
   Imported under that implementation/API behavior: checking only wanted entries
   leaves an unchecked drain. The actual baseline counterexample below independently
   exercises the repository implementation. Android runtime/resource isolation is
   still an open interface.
2. `Charset-X-Coder.java.template` documents malformed/unmappable error policies,
   including REPORT. An initial request for generated `CharsetDecoder.java`
   returned404; the repository directory was listed and the actual template fetched.
   Strict policy does not itself prove BOM selection, exact roundtrip, provider
   behavior, byte-budget safety or Android charset availability. Those remain
   separately testable obligations; CPython comparisons cover host examples.

No exhaustive latest literature survey or product-wide achievability theorem was
obtained. Earlier pinned research remains historical. No license/protocol/native
spike is marked passed from these two imports.

## D. False-solution shapes and detecting tests

E=executed; S=authored/source-only; U=unrun acceptance requirement.

| False solution | Detector / outcome |
|---|---|
|1. Decoder changed, exporter still always UTF-8 | E: each representation's actual output bytes compared; wrong-encoding readback evidence rejected |
|2. BOM silently removed or doubled | E: exact byte roundtrips; CPython inputs include a leading content U+FEFF as well as framing BOM |
|3. Latin decoding guessed from arbitrary bytes | E: default malformed UTF-8 refusal; Windows-1252 requires explicit choice |
|4. Encoder silently replaces an emoji with `?` | E: Windows-1252 emoji export refused |
|5. Failed export loses the user's new Unicode draft | E: unrepresentable draft still checkpoints/restores; no copy identity reserved |
|6. UTF-16 encoding permits lone surrogates | E: malformed UTF-16 and unpaired surrogate cases refused |
|7. Input fits2MiB so encoded output must fit | E: UTF-16 expansion and BOM-over-boundary refused |
|8. New recovery writer tests itself, therefore old recovery migrates | E: genuine pre-edit schema2 fixture plus retained schema1 fixture; schema3 reader migration |
|9. Migration forgets pending-copy encoding | E: frozen payload and document metadata roundtrip for all seven representations |
|10. UTF-8 content digest proves UTF-16 destination bytes | E: encoding-specific receipts/readback bindings; canonical digest is not reused as byte evidence |
|11. Save completion discards later edits | E: verified frozen copy preserves a later dirty draft |
|12. Diff normalizes line endings | E: CRLF versus LF and missing final newline are edits; exact reconstruction checked |
|13. A plausible diff is a minimum edit script | E:225 exhaustive short pairs checked against a separate recursive edit-distance oracle |
|14. Diff allocates unbounded quadratic memory | E:1,000,000-cell boundary accepted; over-boundary and line caps refused |
|15. Diff against current disk is a saved-baseline comparison | S: UI/adapter explicitly names current source; U: live provider race/lifecycle testing |
|16. Only selected ZIP parts need byte limits | E: old production accepts a32,905byte archive draining33,554,433 ignored bytes; repaired production refuses |
|17. Directory entries contain no payload | E: oversized directory-entry data refused |
|18. Per-entry bound also bounds the entire archive | E: individually bounded parts crossing aggregate cap refused |
|19. ZIP size fields or filenames establish safety | E: counted reads, duplicate/path/control-name attacks and CRC-corrupted stored data refused |
|20. Compact empty cells are harmless to cell budgets | E: first candidate failed `<c/>` counter test; repaired regression plus `<c/>`, `<c />`, attributed empty-cell attack passed |
|21. Missing XML closing tags merely mean an empty preview | E: repeated unclosed text/cell tags fail rather than repeated end-of-input rescans |
|22. A zero-progress stream will eventually finish | E: zero-read stream refused and closed; U: actually blocking provider stream still needs isolation/watchdog |
|23. Byte caps are a format-process sandbox | S/U: no such claim; FF-12 isolation remains absent |
|24. SDK setup passed, therefore APK exists | E: current CI first failed assembly; repaired assembly passed, then unit tests failed and upload was skipped |
|25. Build log text may be emitted raw as workflow commands | E: percent/newline injection escaping and output bounds tested |
|26. A stale prior report proves the new tree passed | E: runner removes old success record before running; source audit caught stale96-vs98 inventory, refreshed and rerun |
|27. Assertion count completes Office/browser/AX | E: gates remain0/48; U: actual acceptance still absent |
|28. Primitive arrays expose every Iterable extension | E: real Android compiler rejected IntArray.mapNotNull; asIterable conversion reran through successful assembly |
|29. An implementation dependency exposes public signature types | E: actual DocumentFile classpath failure; existing dependency changed to api and assembly rerun passed |
|30. Annotation strings can exceed the API message budget | E: first remote excerpt was truncated by GitHub; repaired3000byte limit/task filtering and Unicode-tail attacks passed |
|31. A supported schema number can stand for an unsupported future schema | E: CI exposed stale version2 fixture; changed to Int.MAX_VALUE and separately tested truncated versions1–3, without weakening rejection |
|32. A scripted peer may always return request ID3 | E: old loopback test failed; fixture now echoes actual IDs and checks complete1–6 sequence; not device evidence |
|33. A response-only fixture proves outbound JSON is well formed | E: fixture parsing exposed an unterminated sessionId in production LOAD; actual client quote repaired and quoted/Unicode/newline title regression added |
|34. Suppress lint instead of preserving hardware compatibility | E: Pro lint reported implied required telephony; manifest now declares optional hardware, no baseline/suppression added |

## E–G. Distinct mechanisms, adversaries and exact gaps

These are complementary engineering approaches, not six completed alternative
products or independent agents. An encoding probe preceded integration.
Encoding/diff and archive containment
have separate contract entry points; CI diagnostics have separate Python tests.
They share this author and integration runner: no stronger author/process
independence or six complete alternative-system searches are claimed.
CPython serialization and recursive distance were separate oracles rather than
calls back into the Kotlin algorithm. Same-author oracle code is not an
independent expert review; ZIP implementations may share zlib ancestry.

| Mechanism / artifact | Established portion | Structural attack / cheapest falsifier | Exact interface gap and scope |
|---|---|---|---|
| Strict explicit transcoding / `TextEncodingCodec` | Seven representations and cross-language bytes | Malformed sequences, ambiguous BOM, unmappable edit, expansion boundary | Native charset/provider/UI and bounded response time; local gap plus original large-file/regex requirements |
| Versioned recovery / `SessionCodec` schema3 | Genuine v1/v2 migration, v3 metadata and frozen payload | Unsupported enum/version, legacy reservation replay, Unicode draft not representable on export | Android AtomicFile crash/recreation tests; local native gap |
| Request/destination-bound actual byte evidence / `CopyProtocol` + `VerifiedCopy` | Encoded file readback; wrong representation rejected; late edit retained | UTF-8 evidence for UTF-16 payload, stale URI/request, provider alteration | SAF aliases/concurrency/durability and full move journal still original obligations |
| Bounded line LCS / `LineDiff` | Reconstruction, immutable output, finite resource thresholds;225 minimum-cost oracle pairs | Repetitive lines, CRLF/final newline, long lines, max matrix | Native rendering/AX/latency, current-source consistency; historical merge not implemented |
| Counted ZIP consumption / `Ooxml` | Ignored/directory/aggregate, selected XML and object-count limits | Payload after wanted part; duplicate names; compact `<c/>`; missing close tags | No isolated process/watchdog/central-directory/XML validator or Office fidelity; original FF-01/02/12 gaps |
| Revision/ticket publication guard / `EditorViewModel` | Adapter authored using existing revision identity; stale comparison discarded | Switch/close/edit tab while source IO completes; cancel dialog mid-read | **Android-compiled, runtime unrun** new adapter; cheapest native falsifier is a delayed real provider read plus tab change |
| Phase-preserving evidence transport / `ci_annotations.py`, workflow | Bounded excerpts and command escaping executed; original assembly exit retained in shell source | Nonzero build with misleading Node warning, huge log, newline command injection | Real assembly now passed after two compiler fixes; unit tests/lint/runtime acceptance remain separate |

## H. Execution record and repairs

Durable files under `docs/verification/`:

- `r10-jvm-contracts.txt`:177,198 assertions =171,082 prior suite +6089 encoding/diff
  +27 OOXML guards. Twelve separate production JVM libraries, actual public-API
  consumers; two negative module-boundary attacks rejected and positive consumer
  compiled. Three pre-existing CastV2 unchecked-cast warnings remain.
- `r10-boundary-regression.txt`: actual negative diagnostics and fixture hashes.
- `r10-text-interop.json`:707 checks; seven CPython encoding directions,225 diff
  pairs with reconstruction/minimum-cost oracle, Python-generated valid ZIP and
  ignored-entry bomb refusal. Production hashes checked against compiled evidence.
- `r10-ooxml-first-candidate-failure.txt`: real first-candidate failure at compact
  empty-cell count. Repair admitted `<c/>` into the same counting path; the full
  suite and new varied empty-cell attack reran successfully afterward.
- `r10-ignored-zip-counterexample.txt`: identical real archive exercised against
  the baseline source and repaired production library. Baseline success is a
  counterexample to the proposed ignored-part limit, not proof of a crash/exploit.
- `r10-python-before-refresh.txt`:51/52 methods passed; generated inventory was
  stale after adding two main Kotlin sources. Not hidden as an all-pass run.
- `r10-python-checks.txt`:after inventory refresh and subsequent diagnostic/public-API/manifest regressions,57/57 passed.
- `r10-project-checks.txt`: structural module/source/XML/wrapper/privacy checks
  passed. Current inventory98 main Kotlin files; original R8 inventory retained.
- `r10-ci-job-metadata.json`: older baseline CI phase result.
- `r10-ci-compiler-diagnostics.json`, `r10-ci-after-continuity.json`: actual
  compiler failures, respectively primitive-array extension and DocumentFile export.
- `r10-ci-assembly-success-jobs.json`: both-flavor assembly SUCCESS at04f8083,
  followed by unit-test FAILURE; contracts/lint/upload skipped.
- `r10-ci-annotations.json`: first diagnostic excerpt truncated by GitHub; tool
  repair excludes progress noise and caps decoded UTF-8 bytes to3000. A fresh
  Unicode-tail test initially exposed truncation from the wrong end; repaired
  suffix truncation and all57 final Python methods passed.
- `r10-environment.json`: doctor correctly refuses unavailable Android prerequisites.

Subsequent CI exposed two test-fixture defects and a production serialization bug:
`DocumentSafetyTest` mislabeled supported schema2 as future; its replacement uses
an actually unknown version and separately attacks truncated known versions1–3.
The existing **scripted plain-loopback** Cast peer reused response ID3. Echoing
actual request IDs exposed malformed production LOAD JSON: sessionId lacked its
closing quote. That quote was repaired; title escaping and the full1–6 request
sequence are now asserted. Run38655390192 passed both-flavor assembly, all invoked
JVM unit tests and all contract tasks; the final86b4621 rerun passed them again. This loopback test is **not a physical Cast
receiver, TLS test, protocol certification, S21 pass or accepted Beam feature**.
The existing unauthenticated Cast TLS/first-connect trust gap remains a release
blocker; production casting gates were not relaxed.

That run then stopped at Pro lint (2 errors/35 warnings), first error: SMS
permissions implied required telephony hardware. The Pro manifest now declares
telephony optional, consistent with Compat/no hardware-floor exclusion. No lint
baseline or suppression was added; the repair subsequently passed both-flavor lint in run38656329782.
`r10-ci-tests-success-jobs.json` and `r10-ci-lint-diagnostics.json` retain those
phase results. Intermediate failures remain recorded instead of overwritten.

**Final remote evidence:** `r10-ci-success-jobs.json`,
`r10-ci-success-annotations.json`, and `r10-ci-artifacts.json` record successful
run38656329782 at86b4621. The `fusion-native-debug` archive is20,365,258bytes and
GitHub reports SHA256 `6512c2f5c6ea77d6c15b4c9a255b24a391873cbdba55d0d7adb6c65bf0c45032`.
That is a **server-reported archive digest**, not a locally rehashed APK or an
installed-device observation. Artifacts were not downloaded/inspected here. The
managed-device job was skipped; successful lint does not assert zero warnings.
Final host reruns:177,198 assertions,57 Python methods,707 text/ZIP/diff oracle
checks, project/source validation and whitespace checks. No completed module,
spike, device visual review or production-beta designation was awarded.

The first combined JVM runner hid a failing child's stderr in CalledProcessError.
It now retains/prints child failure output and removes stale success output at
startup; compilation and subsequent full execution passed. No failure artifact
was relabeled a pass. CPython oracles were run after the repair, not inherited.

## I. Original requested scope is not reduced to these kernels

| Requested item | Actual frontier / unmet production-beta work |
|---|---|
| PR-01 A/P1 editor | Added bounded encodings/copy/recovery/current-source diff; existing undo/literal tools. Large-file streaming, regex, instant-open budgets and full native acceptance absent |
| PR-02 A/P2 Notes | Existing bounded local note files/native source. Attachments, backlinks, OCR import/export workflow and native acceptance incomplete |
| PR-03 A/P3 tasks/reminders | Complete task store/UI/inexact scheduling not implemented; no exact-alarm substitution |
| PR-04 B/P3 calendar | CalendarContract permission/CRUD/conflict/UX integration incomplete |
| PR-05 A/B/P3 contacts | ContactsContract/API37 picker lifecycle and accepted UI absent |
| PR-06 A/P2 mail; IMAP P4 | Handoff/triage and native IMAP are not complete |
| PR-07 a–s browser | Blocked; no generic WebView substituted. Every listed subfeature remains unaccepted (details below) |
| PR-08 A/P2 PDF/image | No<100ms warm-open/annotate/sign acceptance; current decoders are in-process and exports lack full verified protocol |
| PR-09 A/P1 calculator | Existing tested decimal/scientific/programmer/units/history kernels. Native/keyboard/AX/device acceptance still unpassed |
| PR-10 A/P3 clocks/timers | Complete inexact alarms/timezone/process-death UX absent |
| PR-11 A/B/P2 Office | FF-01/02 engines absent; external open is not counted as a replacement |
| PR-12 A/P2 clipboard/IME | Sensitive/expiring in-module history, optional global IME and key-remap integration incomplete |
| PR-13 A/P3 sticky notes | Complete desktop persistence/window/lifecycle behavior absent |
| FF-01 A/B-tier/P2/S10 LOK | Bridge unimplemented. No isolated MPL engine, Office family edit/save, fidelity corpus or S10 pass |
| FF-02 D/A-tier/P2/S11 POI | Not integrated/audited. Own text scanner is not POI headless read/write/convert |
| FF-03 B/A/P2 PDF | PdfRenderer source only; complete isolated annotations/sign/export absent |
| FF-04 B/C/A/B/P3 AI | PDF-compatible AI admission/fidelity route absent; non-PDF AI remains unsupported |
| FF-05 A/B/A/P1 raster | Platform source handles a subset; complete TIFF/BMP/GIF editing/export, isolation/readback and fidelity absent |
| FF-06 B/A/B/P1/S12 HEIC/HEIF/AVIF | Platform compatibility matrix/NDK/API fallback/device decode-only acceptance absent; no x265 |
| FF-07 B/A/P2 HEIC export | HeifWriter/HW HEVC export and real capability tests absent |
| FF-08 C/B/P3/S13 PSD/PSB | Reviewed library/layer/composite/export integration and S13 absent |
| FF-09 A/A/P2 SVG/text | Bounded text improvements are not complete SVG/render/export/CSV/RTF format-aware editing |
| FF-10 B/A/P4 EPUB | Complete permissive native reader/navigation/accessibility absent |
| FF-11 A/P2 FormatRouter | Not implemented as an accepted engine registry + Save-As/export + explicit fidelity-loss workflow |
| FF-12 A/P1 sandbox | ZIP counters are real but no isolated format process, FD/IPC lifecycle and effective watchdog acceptance |
| FF-13 A/P2 metadata | Tika/engine thumbnail service, bounded lifecycle/cache/privacy and license closure absent |

Browser a–s residuals, all0/19 accepted: **a** desktop horizontal tabs; **b** vertical
sidebar/reorder/group/pin/search; **c** named/colored/collapsible groups; **d/e**
horizontal/vertical two-WebView splits with divider/independent navigation; **f**
pinned tabs; **g** sleeping renderers/restoration; **h** desktop-UA heuristics and
site override; **i** complete requested keyboard shortcuts; **j** compositor drag
out/in and merge; **k** reader mode; **l** per-site camera/mic/location/notifications/
popups/JS decisions; **m** Files-integrated downloads; **n** find in page; **o**
isolated profile data directories; **p** license-audited content filtering/site
exemption; **q** batch/multiselect actions; **r** named sessions including windows,
groups and splits; **s** page-media URL handoff to own Beam protocols. Existing
source scaffolding does not accept any of them; S29/S30 and Beam gates remain.

Original wider scope also remains: own H1 compositor integration/Room≤500ms,
API37 and page-size/ABI validation, native process boundaries, BlackTop, encrypted
Find source integration, journaled Files moves, PTY, own Beam/receiver, Lens/Meet,
own-Mac Messages, timeline backup, offline commerce/entitlements and physical
performance/AX budgets. All30 spikes still lack acceptance. Latest grades/priorities
above are requirements, not measured achieved fidelity.

## J. Strongest artifact, exact remaining gap and next falsifier

Strongest new verified artifact: **both native debug variants assembled, all invoked
JVM tests/contracts passed, both-flavor lint passed, and debug APKs uploaded in real
CI**. Alongside that: bounded text representation/recovery/encoded-copy kernels,
reconstructible line scripts with separate host oracles and a repaired ignored-ZIP
drain counterexample. This is executable build progress, **not production-readiness**.

Immediate next test: execute the existing consented disposable managed-device
workflow and actual native runtime checks, rather than infer them from compilation.
Run the
native encoding picker, schema1/2 recovery, source-diff tab/cancellation races,
SAF readback and rotation/process-death scenarios on API34/36/37. Measure, do not
assume, IO/UI responsiveness and200% keyboard/TalkBack/Switch/Voice behavior.

Separate missing implementation work: process-isolated format host/watchdog first;
S10/S11/S12/S13 engine/license/fidelity spikes; real FormatRouter and verified exports;
S29/S30 browser security/compositor architecture; remaining Productivity stores,
permissions, scheduling and UX. No host test removes those original obligations.
