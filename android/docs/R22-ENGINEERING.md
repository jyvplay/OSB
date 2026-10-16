# R22 — bounded, isolated values-only XLSX import

**Native Android only. Not a full Office/browser engine, anonymous OS, or release acceptance.** Five existing modules deepened; PR5 remains open/unmerged. No new module was invented to improve the percentage.

## Candidate and current evidence

Platform prerequisite: **e9be6a7e50ad9fca93b481a2e0e587e3c4f3c644**, run [38851496451](https://github.com/jyvplay/OSB/actions/runs/38851496451), **2PASS/0FAIL/0SKIP**. Recorded in commit ee7c587 **before** importer/worker/UI implementation. Its 20,000-node budget was subsequently revised and separately exercised on Android; it is not evidence for every later parser change.

Current application candidate **fe8dca210642d42c0553c47ff4bccc16b5e17404** caches admission regexes under the unchanged worker deadline and repeats the maximum dense test in fresh children. Earlier2339c96 repaired picker instrumentation;83e8e5b repaired the observed Notes test-side read-observation race. **Focused run38854643183:10PASS/0FAIL/0SKIP SUCCESS.** **Full run38854643235:119PASS/0FAIL/0SKIP SUCCESS**, both debug builds, all invoked JVM/contract targets and lint passed. [APKs/full evidence](https://github.com/jyvplay/OSB/actions/runs/38854643235), artifact `fusion-native-debug`. Later report/tooling commits are not separately claimed native-tested application candidates. [Focused receipt](verification/r22-final-focused-result.json), [native run](https://github.com/jyvplay/OSB/actions/runs/38854643183). [Laptop/Rabbit1/no-Studio instructions](BUILD.md#r22-laptop--rabbit1-try-out).

| Census standard | Absolute | Percentage |
|---|---:|---:|
| Included modules with substantial executable subsets |48/49|97.96%|
| Not established: LOK |1/49|2.04%|
| Fully specification-accepted modules |0/49|0%|
| Still unfinished against full assigned scope |49/49|100%|
| Existing modules deepened this round |5/49|10.20% of inventory, NOT acceptance|

Affected: `:format:archive`, `:format:ooxml`, `:format:pdf` (existing isolated-worker host, not a new PDF engine), `:feature:automation`, `:feature:browser`. Original48-module comparison remains47/48 substantial subsets. Original231 requirement rows,36 cross-cutting obligations and30 formal spike gates are not promoted by these local experiments.

## Runtime honesty

Actually used: shell/filesystem/git/gh, Python3.11.2, Kotlin2.2.21, JDK17.0.9, Python zipfile/ElementTree, openpyxl3.1.5, newly installed **host-only XlsxWriter3.2.5**, fresh web search/page retrieval, and GitHub Gradle/actual managed API34 production/test APK execution. No local SDK/ADB, Studio GUI, physical Android device, Excel/LibreOffice runtime, independent human/agent, legal/AX auditor or new theorem prover run. Host XlsxWriter installed LICENSE.txt/METADATA reviewed:BSD-2-Clause, no Requires-Dist entries; hashes in r22-host-tool-license.json. No new APK third-party engine/parser dependency. Existing archive module is linked to OOXML; original Kotlin implements the bounded substrate. Parallel CI jobs are not independent reviewers. Authored byte streams/corpora exercise real implementations; no app functionality is mocked. Android instrumentation uses real Binder/UIDs/files/Inflater/SAX/Activities/DocumentsUI/providers. Managed emulator is not physical evidence.

## A — formal contract and resources

**Objects/access.** Stable caller-owned package bytes; parent captures a private copy, closes writer, opens a fresh readonly regular FD at offset0, unlinks its temporary pathname, passes FD to one isolated service instance. Source URI/path is not given to child. Parent can see all source bytes and has networking permission. Child returns bounded data, not file-writing authority. Concurrent malicious mutation of caller arrays, compromised parent/platform and malicious semantic computation by child are excluded from correctness assumptions, not claimed solved.

**Package.** Input≤2MiB. Generic strict reader≤16 allowlisted ASCII file members; importer allows only10 specific possible parts, including the five required package/workbook/worksheet parts. Local and central names/flags/methods/CRC/sizes/offset coverage must agree. Contiguous local records start at0 and end exactly at central directory; EOCD ends exactly at input length. No encryption, descriptor bit, ZIP64 extras, comments, symlinks/directories, gaps, leading/trailing bytes or unknown Office parts. STORE/raw DEFLATE only. Actually produced bytes≤declared size≤1MiB/member, sum≤2MiB; raw deflate must finish with zero remaining compressed bytes; actual CRC checked. Uninterpreted timestamp/ordinary extra metadata is not a fidelity guarantee.

**XML.** Strict UTF8, optional single BOM, ≤1MiB/member. XML1.0/UTF-8 declaration subset. Reject raw `<!` before SAX, deliberately excluding DTD, comments and CDATA. Throwing entity resolver; no processing-instruction, schema-validation, XInclude or transform processing pass. Namespace-aware tree uses expanded names, not string matching. Depth≤32; final nodes≤32,768; ≤16 attributes and≤16 new namespace mappings per element; local names≤128, element qualified names≤256, namespace URI≤2048, attribute value≤4096; aggregate direct character content≤1MiB. These are admission limits, not a JVM/Android RSS or hard real-time theorem. Android-specific behavior was tested, not inferred from desktop JDK.

**Workbook semantics.** Single visible sheet, explicit package/content-type/relationship targets; no external links or traversal normalization. Inline or shared plain strings; raw numeric/boolean encodings become **literal text**. No numeric/date formatting inference. Refuse formulas/cached formula-string types/errors/date types, nonzero cell styles/custom numeric formats, rich text, merges, row-hidden/custom-row structures and unknown worksheet structures/parts. Empty default and explicitly allowed display metadata is omitted, not rendered. Workbook style/layout/name/package-author metadata is deliberately not copied. It is not a complete OOXML schema validator, XLSX compatibility layer or original-document safe-save engine.

**Coordinates/output.** Strict increasing row indices1..256, strict increasing cell columnsA..AF within matching rows, no duplicates/coordinate mismatch. Fill missing cells through last actual admitted cell, starting atA1; dimension metadata does not allocate the table, trailing empty row records do not extend it. Table≤256×32=8192 cells,≤4096 UTF16 units/cell; existing data frame≤131072 bytes and no trailing bytes. Selected columns ordered/unique/in-range. Existing writer rejects unsupported XML/OOXML escaped-string tokens rather than rewriting. All newly exported cells are literal strings; maximum output2MiB, exact reopen/readback required.

**Lifecycle.** UI GET_CONTENT uses read/import-copy, no persistent grant. Success replaces volatile draft with imported table; cancel/refusal retains previous input but invalidates export. Any edit invalidates prepared preview; typing CSV replaces imported table. Fresh generation identity prevents stale publication. CREATE_DOCUMENT uses a frozen new-file payload; recreation loses pending import/export and refuses stale callbacks. The import route never requests source output; export uses only a new CREATE_DOCUMENT result. Source equality was verified on the real tested provider. A malicious/incorrect provider can alias or mutate underlying objects: source preservation and new-target identity still require provider honesty, not merely different URI strings. IO can block on provider; executor interruption is not a proved provider cancellation deadline. Existing child watchdog5s, broker connect12s/reply8s are configured bounds, not hard real-time or hostile-kernel guarantees.

**Browser kernel.** `suspendDocument(tab)` revokes pending decisions, grants and document identity without inventing a destination URL. Further requests fail until a successful navigation creates a fresh identity. Last URL in snapshot is not permission liveness. Trusted caller must provide authentic renderer/navigation events, consent and clock; actual engine bridge still absent.

**Quantifiers.** For every admitted package, every returned cell corresponds to its admitted coordinate under the above XML-decoded/raw-text semantics, not Excel-rendered semantics. For every selected column, every exported value equals that projection. For pairs with the same projected values under the same writer/runtime, fresh export bytes agree even when omitted values or source author/sheet metadata differ. For every suspended document, old prompt/grant use fails; for every stale UI job, publication fails. These are intended source contracts tested over declared corpora, not universal formal verification of Kotlin/JVM/native parser correctness.

Adjacent problems not substituted: header metadata vs actual inflate; XML syntax vs Office fidelity; sparse coordinates vs sequential scanning; text projection vs workbook preservation; metadata omission vs anonymity; unlink vs erasure; IPC correlation vs computation authentication; suspended model vs wired engine; provider readback vs durability; managedAPI34 vs physical/API36/37/AX/legal acceptance.

## B — hypotheses and declared threshold

**H+** bounded read-only import/projection and permission revocation can run under trusted platform/parent/caller assumptions. Threshold: record platform spike before gated feature code, compile actual production consumers, compare independent maintained producers/readers, test malicious boundary inputs, then require exact-candidate named native operation/UI cases plus whole relevant green workflow for that scoped claim.

**H−** this app cannot make the privileged host, this conversation, GitHub, other applications or previously recorded external activity globally/retroactively anonymous. No lighter-than-Tails predicate is established.

**H∂** format/dialect limits, caller ownership, correct lifecycle delivery, platform/runtime, source/provider permissions and legal/fidelity interfaces determine scope. A refusal is sometimes correct safety behavior and sometimes a documented compatibility gap; it is never silently counted as Office parity.

## C — verified frontier and imported results

[Nine fresh searches/six page fetches](research/r22-primary.md), including Tails host/metadata warnings, Android GET_CONTENT/isolated services, OWASP parser hardening, Gecko crash/kill callbacks, LibreOffice mixed license terms, Tor Browser fingerprint defenses and Android VPN controls. Partial pages and rolling API versions are labelled. No document proves our implementation. No [W]/[K] historical assertion is called fresh research.

Independent maintained implementations actually executed: Python zipfile/ElementTree, openpyxl3.1.5 and XlsxWriter3.2.5. Positive packages include STORE and DEFLATE, inline and shared strings, sparse coordinates, non-ASCII and formula-looking literal text. Actual exported workbooks are reopened by openpyxl. Self-authored corpus, not a blind holdout, Excel rendering test or independent human audit.

## D — negative space: false solution shapes and detectors

|#|False shortcut|Concrete detector / remaining boundary|
|---:|---|---|
|1|Old lossy scanner is a validating import engine|New parser does not call `Ooxml.kt`; malformed XML refused|
|2|ZIP central sizes alone prevent bombs|Lying declared expansion-size attack, consumed output cap|
|3|ZIP prefix is enough|Trailing/truncated/local-CEN disagreement mutations|
|4|A finished deflater means exact member consumption|Python accepts appended compressed junk; strict reader refuses|
|5|Descriptor/ZIP64/encryption are implicitly supported|Explicit refusal; not broad compatibility|
|6|Ignore unknown Office parts|Macro/unknown member refusal|
|7|String-match XML tag names|Namespace-aware SAX, undeclared-prefix/duplicate-attribute tests|
|8|Desktop XML defaults prove Android safety|Recorded actual Android prerequisite spike|
|9|Entity resolver alone covers every XML feature|Pre-SAX `<!` refusal; XInclude is data-only, no schema/transform pass|
|10|Raising a resource cap removes the boundary|32,768/32,769 regression attack plus dense native roundtrip|
|11|Sequentially collected cells preserve sparse sheets|Independent shared-string C3 case pads A1..C3 correctly|
|12|Cached formula values are safe literal input|Formula/type refusal, even cached numeric value present|
|13|Raw serial numbers are interpreted dates|Explicit raw-text semantics; date/custom-style refusal; no date rendering promise|
|14|OOXML escaped-string tokens are ordinary literal text|`_x0041_` refusal rather than wrong displayed content|
|15|A new workbook preserves original format/metadata|Visible static loss report; no original save-back route|
|16|Omitted metadata means anonymous|Retained values/filenames/provider/context can identify|
|17|Input length read then truncated is safe|Capture exact cap/cap+1/no-progress checks|
|18|Read-only UI may write the original URI|Source readback equality after import/export/reimport|
|19|Digest and matching length prove honest child semantics|Real `OwnedXlsxRequest.accept` admits bounded forged table with matching claims|
|20|Unlinked file never touched disk|UI warning; no erase claim|
|21|Main app is no-INTERNET because worker is isolated|Separate parent/child/companion scope; actual UID/permission observations|
|22|URL equality establishes live renderer|Suspend/same-URL fresh-document tests; bridge remains absent|
|23|A test works because previous tests initialized its device singleton|Focused first-attempt failure retained; explicit initialization repair|
|24|Picker-created document is necessarily visible in app MediaStore query|Actual empty-query failure; fresh real picker grant replaces shortcut|
|25|Reopening output can reuse old wider source table|After reimport, old column2 must refuse; column1 shows exact new copy|
|26|Passing seven new kernels accepts failed UI suite|Exact scope/count/candidate policy rejects failed whole run|
|27|Passing docs/PR build implies full managed-device success|Inherited108/1 and spike110/1 failures retained|
|28|VPN/Tor badge gives Tails-level anonymity|No implementation; threat-model and fingerprint/route tests still required|
|29|Top-level MPL clears LibreOffice bundle|Transitive/license/ABI/16KiB gate remains closed|
|30|48 testable subsets are48 finished modules|0/49 specification acceptance maintained|

## E — mechanism-distinct portfolio

Early branches were recorded in R22-PLAN.md; all are work by one agent, not an invented team.

|Mechanism|Artifact/result|Strongest falsifier|Exact residual|
|---|---|---|---|
|Binary envelope and consumed-output accounting|`StrictZip.kt`; Python/native comparisons|Local/CEN split, false expanded size, deflate suffix|Narrow ZIP dialect; JVM/native bugs/RSS not proved absent|
|Lexical exclusion plus namespace-aware bounded SAX|`BoundedXml.kt`; actual Android prefeature spike|DTD, wrong encoding, depth/node/attribute cap, XInclude|Refuses comments/CDATA; no generic XML schema or API36/37 approval|
|Relationship/type/coordinate admission|`LiteralXlsxImport.kt`; two external workbook producers|External/traversal relationship, sparse C3, duplicate/out-of-range/formula|Single-sheet plain values only; no Office fidelity|
|Capability-limited isolated computation|`FormatLease`/worker operation5/`OwnedXlsxRequest`|Corrupt/oversize then fresh lease; distinctUID; forged correlated table|Parent sees bytes; digest authenticates neither computation nor erase|
|Provider snapshot and explicit lossy new copy|Native Activity/`WorksheetInput`/`TaskTransferIO`|Cancel/refusal/recreation/stale column after real reimport|IO/provider/IME/host trusted; no original edit/durability|
|Document suspension/revocation|`BrowserConsentSession.suspendDocument`|Old pending/grant after suspend, no request until fresh navigate|No renderer/frame/consent bridge|
|Exact evidence admission|`r22_evidence_gate.py`, receipt-mutation tests|Wrong candidate/suite/count/missing/failed/duplicate/skip/global claim|Offline metadata checks, not receipt authentication or expert acceptance|

## F — candidate-specific attack strategy

Attack byte provenance before parser, not only happy-path rendering: mutate matching signatures/headers/flags, insert trailing deflate bytes, lie about expanded size, use an independently generated ZIP; then attack XML encodings/entities/namespaces/limits; then relationships/content types and sparse coordinates; then cross-process lifetime, forged reply semantics, maximum dense frame; finally source/copy capability and lifecycle transitions. Host export pairs change source metadata and unselected private values while keeping selected cells fixed. The new picker reimport attack detects accidental retention of the old wider table, not merely a success label.

## G — actual external verification ledger

Host final: **37 public-consumer assertions PASS**; **327 actual interop requests:271accepted/56refused**, **15 equal-byte exported-workbook pairs**, seed220022. Limits exercised include exact1MiB XML text,32/33depth,32,768/32,769nodes,16/17attributes,4096/4097ASCII or2048/2049emoji UTF16 cells,2MiB capture/cap+1, dense256×32. `r22-host.txt` binds actual source/compiler/JAR hashes. `r22-interop.json` binds transcript digests; timing is a single host observation, not Android performance proof. Fresh R21 host regression:376 assertions PASS. Python tooling:**121 methods PASS** after adding exact final-focused/full scope tests. Producer modified timestamps can vary: seed fixes corpus values, not every ZIP input byte. Initial289-case run/native fixture used producer packages directly; later host packages normalize ZIP-envelope timestamps using Python while retaining producer XML. Additional native regression runs are recorded separately below.

|Candidate / run|Observed native result|Interpretation|
|---|---|---|
|96ea655 /38850608205|108PASS/1FAIL|Inherited old local-plan/calculator UI timeout; build-only38850611869 success does not override it|
|e9be6a7 /38851496451|2PASS/0FAIL/0SKIP|Preimplementation Android XML/ZIP spike only|
|e9be6a7 /38851496283|110PASS/1FAIL|Old Notes backlink UI not displayed; source-build check also stale before generated inventory repair|
|4e0b9b2 /38852835011|7PASS/2FAIL|All platform/worker/kernel cases passed; UI helper failed before interaction because UiDevice singleton uninitialized|
|4e0b9b2 /38852835003|8PASS/0FAIL/0SKIP|Prior CSV workflow regression|
|4e0b9b2 /38852835031|7PASS/0FAIL/0SKIP|Prior Tasks/Notes focused regression|
|f6ab831 /38853356837|9PASS/1FAIL|UI import/save readback reached; test's MediaStore query could not find created copy; cancel/malformed-provider cases passed|
|4e0b9b2 /38852835007|116PASS/2FAIL|Same copy-query failure plus old Notes read assertion raced ongoing store inventory|
|f6ab831 /38853356911|118PASS/1FAIL|Full regression failed the same MediaStore-query test|
|2339c96 /38853911009|9PASS/1FAIL|All three repaired UI cases passed, but dense worker died; cause not authenticated by death callback alone|
|83e8e5b /38854330516|7PASS/0FAIL/0SKIP|Actual Notes/Tasks focused regression after operation-readiness test repair|
|2339c96 /38853911115|119PASS/0FAIL/0SKIP SUCCESS|Whole native run green, despite separate focused dense failure; not a stability proof|
|83e8e5b /38854330458|119PASS/0FAIL/0SKIP SUCCESS|Whole native regression after Notes observation repair; before regex optimization|
|fe8dca2 /38854643187|8PASS/0FAIL/0SKIP SUCCESS|Fresh CSV workflow regression|
|fe8dca2 /38854643183|10PASS/0FAIL/0SKIP SUCCESS|All platform/isolated/kernel/UI cases, including two fresh dense imports|
|fe8dca2 /38854643235|119PASS/0FAIL/0SKIP SUCCESS|Whole managed API34 workflow green; builds/JVM/contracts/lint also passed|

Overlapping focused/full counts are not summed as unique tests. Receipts retain failures. Earlier R21b96 full109/0 remains a historical scoped pass, not current stability proof. Actual Android UI failures in older shell/Notes have not received a causal product repair here; later green runs cannot establish one.

## H — repairs, regressions and new attacks

1. Initial dense import failed at20,000 XML nodes: maximum own-writer worksheet uses more elements. Retained `r22-dense-first-host.txt`. Raised finite node cap to32,768, not unlimited; same dense256×32 case now passes host and actual isolated Android. New boundary attack32,769 rejects on host and Android. Source memory/time bounds still not proved.
2. First openpyxl workbook refused because ordinary sheetPr was not in allowed display metadata; retained `r22-first-xlsx-interop.txt`. Added only named metadata, later fileVersion for XlsxWriter; unknown worksheet parts/formulas/hidden rows/extra package parts still refuse. External readers/producers and negative corpus rerun. These were subset-availability defects, not a justification to accept arbitrary XML.
3. First focused UI run failed7/2 before app interactions. Initialize instrumentation-backed UiDevice before legacy UiScrollable. Regression rerun9/1; new actual malformed-provider import retains draft, invalidates export, leaves source bytes intact, and can then parse retained CSV.
4. Second UI failure was empty MediaStore query after actual picker save/readback. Do not add broad permissions or fabricate a file path: reopen through actual picker grant. Add old-column2 refusal/new-column1 exact preview after reimport. Native2339c96 confirmed all three UI cases, but whole focused run failed a separate dense-worker death; no whole gate promoted.
5. Full4e0b9b2 exposed `NotesBusy` in a direct test-side read after status appeared. Source publishes trash/restore status with `busy=true` before final storage scans. Candidate83e8e5b waits for the actual matching ViewModel status **and `busy=false`**, under the existing10s budget, before acquiring another store lock. Reverse restore and existing200% font case exercise the same readiness boundary. No mutation retries or exception suppression, and no claim to fix all old Notes/Compose instability. Focused7/0 regression passed at83e8e5b; broader stability not established.
6. Focused2339c96 returned `Worker died` for the maximum dense case, which passed earlier candidates. Binder death alone does not establish watchdog versus another kill cause. Code inspection found per-cell regex compilation repeated thousands of times; fe8dca2 hoists five patterns to per-process constants without changing grammar,5s watchdog or8s reply timeout. Host corpus plus decimal/exponent lexical cases rerun; new native attack repeats dense import in two fresh children, no warm-process reuse or swallowed failures. Fresh fe8dca2 focused10/0 passed, including two dense children. This supports the scoped implementation; it does not retrospectively authenticate the earlier kill cause or prove timing/RSS on all devices.
7. Initial source contracts caught stale generated inventory/census after adding/editing code. Regenerated reviewed artifacts and source hashes; check_project/Python tests rerun. A successful shell's last command was not used to conceal the earlier failure; ledgers state it explicitly.

## I — original design, alternatives and privacy refinement

**Major browser:** bounded session/consent kernels plus future audited isolated GeckoView/native engine remains one route; original compositor must host real surfaces, frame origins, permissions, downloads, accessibility, keyboard and own-cast behavior. A fresh renderer bridge spike is next, not a generic WebView substitute. External browser intent is honest delegation, not our browser delivery. Rolling Gecko docs do not clear version/license/ABI requirements.

**Office:** current route is useful original bounded value projection, sacrificing layout/types/formulas/styles and original edit fidelity explicitly. Full original OOXML/ODF engine is much larger work. Isolated LOK can be investigated only after audited exact transitive licensing/build/16KiB/resources/fidelity spike. Remote conversion changes offline/privacy scope and is not silently enabled. Old scanner remains separate and explicitly lossy.

**Sandbox:** readonly owned FD and separate UID reduce authority but do not make child computation trustworthy. Alternative sealed-memory capability needs a real API/availability/ownership spike; generic language sandbox/WASM/parser libraries need dependency/ABI/runtime budgets and legal audit. No speculative replacement added.

**Productivity:** real Tasks/Notes/CSV and now narrow XLSX projection are native increments. Full appointments/calendar/recurrence/Office editing/sync/Meet integration remain unimplemented/unaccepted. No stock desktop/DeX or cast delegation substitutes for first-party compositor/Beam.

**Lightweight privacy:** unchanged separate offline scratchpad is useful compartmentalization, not an anonymous OS. New projection omits source metadata and unselected cells in a new file, but retained content, filename, provider and host can identify. Future VPN/lockdown routing would still require DNS/IPv6/bypass/disconnect/OEM tests; Tor Browser fingerprint defenses do not come from a generic Tor route. No global/retroactive privacy claim, remote relay, telemetry pipeline or account requirement added. [Privacy requirement refinement](R22-PRIVACY-BOUNDARIES.md) lists implemented versus planned interfaces. No comparison of total guest+host RSS/storage/power with Tails has been performed.

Main permissive-license/MPL-isolation policy, GPL receiver separation, original compositor, min34/target36→37 and requested compile37, arm64/4K/16K, restore≤500ms, S17–S22 hardware/counsel, AX/TalkBack/Switch/Voice/200%reflow, legal/fidelity and physical-device gates remain. Actual pinned compile36 still deviates; current CI catalog reported SDK37 NOT_LISTED, not universal future unavailability.

## J — strongest artifact, exact gap and next decisive test

Strongest currently recorded: actual isolated Android values-only import (including independently produced shared-string workbook and maximum dense original export), existing CSV/Task regression passes, and host independent import/export comparisons. All repaired UI cases passed at2339c96, but the whole focused gate was9/1 because of a dense-worker death. Latest fe8dca2 focused10/0 now admits only the exact named API34 import experiment; full119/0 also passed on the same exact candidate. Receipts, named cases and whole workflow satisfy only those scoped gates. Full product acceptance remains0/49. Completed-run artifacts were present/unexpired when queried; retention can expire them.

Next decisive tests: real API36/37/OEM/physical and adversarial slow/revoked provider lifecycles, keyboard/AX review, truly blinded malformed package corpus, resource measurements, and a separately gated real browser event bridge. Real Excel/LibreOffice opening/rendering and a legal-cleared full Office engine are still absent. No merge is authorized or performed.
