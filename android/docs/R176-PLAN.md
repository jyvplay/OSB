# R176 — Android-facing diagnostics and real build-surface repair (A–J)

2026-11-27. Incremental baseline: `d3e9bfb` (R146 PR #69). Original branch
baseline: `92bcd3278445af8467da5e3fd5cea7531b9815f4`. Full scope remains v2/v2.1;
this revision is partial delivery. [Executed results](R176-RESULTS.md).

## Runtime honesty / available systems

Actually used: local Git, authenticated `gh`/GitHub REST API, Bash/Python,
Kotlin compiler 2.2.21, disposable host JRE 17.0.9 from jdk4py 17.0.9.2,
JCA, real Java/Python file and ZIP APIs, real threads/latches, CPython Decimal,
Python unittest, Java2D/ImageIO, and a compile-only API-35 `android.jar` mirror.
No independent agents, theorem prover, Android emulator/device, full Android SDK,
Gradle distribution, Android Studio installation or external expert reviewer was
available locally. No simulated Android runtime, fake service or mock provider
was used. Explicit failing streams are real injected I/O experiments, not
observations of an Android provider. Android device tests are authored, unrun.

The previous turn's exact file tree matched the remote R146 commit; the local
branch pointer was reconciled to that already-pushed commit without discarding
or rewriting its work. The old R146 host checks were observed successful on
GitHub. Old Android jobs failed; fetched GitHub annotations identify real duplicate
SemVer/VersionRange compile errors, not executed device cases. CI log archive
retrieval was blocked by network restrictions; annotations, not inaccessible
archives, support this observation.

## A. Formal model

Objects: explicitly selected fixed diagnostic fields (API level, app version,
optional single-line operator note), immutable redacted review R, opaque per-session
ticket T, caller-selected destination streams D, bounded FDE1 ZIP files, bounded
release-version ranges, decimal expressions, rectangles/work areas and byte-search
inputs. Selected diagnostic values <=1024 UTF-16 units; <=256 records. Imported
ZIP <=2 MiB; content <=1 MiB+32 KiB, <=256 sorted unique records. No extraction.
Version range <=64 chars; decimal inputs <=512 chars with finite adjusted exponent
[-1000,1000] and pre-arithmetic scale bounds. Geometry <=16 positive work areas
within [0,65536], arbitrary Int requested positions, Long distance arithmetic.

Access: no ambient log collection, clipboard imports, scratchpad access, network
requests or new permissions. The Activity receives no external intents (not
exported). Only the system-picked URI is opened. File-provider I/O happens on a
bounded worker executor, never Android main. The content provider is untrusted;
it may block, fail, upload data or lie about durability. No filesystem isolation,
provider timeout, offline enforcement or authenticity theorem is assumed.

Resource counted: actual compiler source union, runtime comparisons, deliberately
injected failures, declared unittest cases, named behavior assertions, physical
file bytes and visual artifact dimensions. These are distinct measures. Counts
of source files or smoke assertions are not full feature counts.

Success quantifiers: for every live session S, every review R it prepared, and
every ticket T it issued after explicit digest approval, at most one save may
claim T; every save with a foreign/stale/replayed T must refuse **before** opening
D. After claim, a reported success requires output close followed by a bounded
complete readback whose length and SHA-256 match the staged ZIP. A new session
must refuse a pre-death ticket. Human review itself is outside the code's proof;
a checkbox/digest is not authentication. Failure after opening D may leave a
partial document; the UI must not claim atomic save, silently retry, or delete
an arbitrary provider document.

Experiment threshold is weaker than universal correctness: all named finite
cases must execute against production APIs, agree with the independent references,
and the new regression gate must not conceal skipped/missing execution. Exact
byte equality is used for artifacts; numeric comparisons use exact integer tuples
or decimal34 half-even equality, not generous error tolerances. Tests of active
provider implementations require real Android and remain open.

Adjacent problems NOT substituted: Activity API compile is not full APK/Gradle
resource compilation; host PNG is not native UI; same-process rotation retention
is not durable persistence; content digest is not signature/authorship; readback
is not cloud durability; heuristics are not anonymisation; arithmetic/search
smoke tests are not full module Class A; fixed framing parsed by CastV2Channel
is not official Cast protobuf interoperability.

## B. Outcome space and evidence threshold

* H+: complete original product and mandatory Class-A/device/privacy/AX/provider
  gates. Not established, and never inferred from this increment.
* H−: universal atomic save/durability against an arbitrary content provider is
  impossible in this access model: a provider can acknowledge writes/readback
  and subsequently discard data, or block forever. No local transcript can
  distinguish a provider that discards only after the observation. Do not claim it.
* H∂: bounded review/save/readback protocol and supplied-input subcontracts work
  on the executed host corpus, with compile-only Android API evidence; actual
  native UI/provider matrix remains conditional. This is the supported outcome.

Declared merge evidence: retain prior good behavior, pass affected host gates,
report every remote check result actually observed, and do not use a repository
merge as a Class-A certificate. User explicitly requested merging this PR. The
full original-spec success predicate remains unsatisfied even if a merge succeeds.

## C. Retrieved frontier, hypotheses, open interface

Consulted documentation, not copied implementation:

1. [Android storage samples](https://github.com/android/storage-samples/tree/main/ActionOpenDocument)
   documents user-mediated SAF access without broad storage permission. It is an
   archived example, not a maintained proof of current API37 provider behavior.
   R176 writes its own create/open protocol and separately tests a real-stream
   boundary; Android tests target actual MediaStore but are not run here.
2. [SemVer 2.0.0](https://github.com/semver/semver/blob/master/semver.md): three
   nonnegative core integers, no leading zeros, prerelease identifiers with
   arbitrary numeric length, build metadata ignored for precedence. This does
   **not** specify caret/tilde ranges. R43's existing explicit release-only
   caret/tilde contract supplies those bounds; no npm-complete equivalence is
   imported. Long bounds are an explicit implementation narrowing.
3. Existing R146 OWASP logging/password guidance remains applicable; no new crypto
   primitive is invented. JCA SHA-256 checks content; it is not a signature.

Compile-only platform artifact: public SDK mirror
`Sable/android-platforms`, Git blob
`2d6652edf0fb6046a23638f3a2f2c6246c745b08`, 27092450 bytes, SHA256
`4566663c3876e022b4fa4ced8c8697c4ab1688267f090114fd92d027b32e619b`.
Downloaded via GitHub API, kept outside tracked source under ignored `.cache`.
It supplies API stubs, not an Android runtime. Mirror provenance is trusted only
for this limited type-check, not independently authenticated SDK distribution.
No external code/assets were copied into the application. New implementation is
Apache-2.0 and uses existing platform/stdlib dependencies.

Exact open interface: Android lifecycle/result delivery, Compose Settings→native
Activity navigation, DocumentsUI/provider grants and behavior, native visual/AX
layout at font scales/densities, process-death/system-picker interactions, and the
remaining full-project build defects. Host protocol proofs cannot close it.

## D. Negative space and failure-detecting tests

Modal shortcut: compile each Kotlin-looking kernel alone, then run only its Python
oracle. It fails because duplicate classes and incompatible caller signatures are
properties of the source union, invisible to isolated oracles. The new build gate
compiles the complete model/windowing/index source sets with the 21 repaired files
and invokes their real APIs; before repair it fails on duplicate declarations,
invalid C-style `<<`, invalid BigDecimal access and type errors.

| # | Plausible but invalid result | Discriminator |
|---|---|---|
| 1 | Python agreement described as Kotlin execution | Real compiler + JVM source-union gate |
| 2 | Island compilation hides duplicate classes | Full three-module source union |
| 3 | Drop one colliding feature to pass compile | Keep both settings/units APIs and exercise both |
| 4 | Placeholder SemVer treated as real implementation | Compile real SemVer with existing R43 callers |
| 5 | Range caret admits versions below lower patch | 1039 held-out tuple-reference cases |
| 6 | Numeric prerelease overflows Long | 200-digit identifier comparison |
| 7 | Prefix match counts evalForecastEts as evalForecast | Exact helper identity + added prefix/duplicate mutation tests |
| 8 | Approved digest reusable across sessions | Equal digest/sequence, foreign ticket refusal |
| 9 | Picker result replay opens output twice | Reuse ticket after save; output-open count stays one |
| 10 | Lost process state reopens a returned URI | New workflow instance refuses old ticket before open |
| 11 | Failure to prepare leaves old approval live | Invalid replacement clears old review first |
| 12 | Checkbox silently persists across review recreation | Authored Android recreation test; host does not certify it |
| 13 | Provider accepts output but corrupts bytes | Full archive bounded readback, corruption/extra/truncated tests |
| 14 | Sink write/close failure reported saved | Actual injected I/O errors force FAILED |
| 15 | Read stream returns zero forever | Non-progress refusal test |
| 16 | Duplicate save races both open output | Actual two-thread/latch experiment |
| 17 | ZIP checksum presented as trustworthy author | UI/report explicitly deny authenticity/anonymity |
| 18 | ZIP slip/bomb through diagnostic import | Fixed entry names, 2 MiB raw/expanded limits, no extraction |
| 19 | Import renders untrusted secrets/terminal controls | Only fixed summary shown; malformed/control values refuse |
| 20 | Unicode bidi or isolated surrogate changes reviewed bytes | Selected controls and malformed UTF-16 refused |
| 21 | addWorkArea validates but forgets to register | 262 held-out geometry cases / impossible-minimum attack |
| 22 | Width larger than display crashes coerceIn | Clamp dimensions before positioning; fitting-minimum requirement |
| 23 | Image thumbnail/preprocess allocates huge scaled buffer | Check total pixels before allocation |
| 24 | Very small decimal scale causes expensive alignment | Scale/exponent bounds before BigDecimal arithmetic |
| 25 | Host-rendered visual passed as native screenshot | Artifact labeled HOST; Android screenshot/AX gate stays open |
| 26 | Manifest allowance broadened for arbitrary Activity | Exact new internal Activity allowlist plus exposure mutation controls |

## E/G. Mechanism portfolio, independence and second-order attackers

| Approach / artifact | Construction / established portion | Exact unresolved interface | Cheapest falsification | A(C), structure-derived attacker | Gap kind |
|---|---|---|---|---|---|
| Source-union compiler gate | Actual declarations must coexist; preserves old and later APIs | Full dependency/Gradle/resource closure | Compile sibling source set, not a single file | Hide duplicate in another source root, use incompatible companion signature | Local, not whole product |
| Opaque session ticket state machine | Synchronized claim before opening provider; one session owns identity | Real lifecycle and human approval | Equal digest and sequence in a different instance | Replay late picker URI after discard/process death | Local protocol; lifecycle external |
| Staged ZIP + bounded full readback | Success requires close/read/length/hash agreement | Provider may block or lie later | Inject close failure or extra byte | Return matching readback then discard remotely | Global durability impossible in access model |
| Strict bounded ZIP inspector | Fixed records/entry set, UTF-8/CRC/digest/manifest checks, no extraction | Authorship, secret-content judgment, arbitrary ZIP canonicality | Independently authored hostile ZIP | Recompute digest over secrets; hide bomb or duplicate key | Local integrity; anonymity original problem |
| Independent mathematical references | Tuple intervals, CPython Decimal, native bytes.find | Adversarial input coverage beyond finite corpus | Lower patch/overflow/tiny scale/tie cases | Attack reference and production with same specification misunderstanding | Local correctness; finite evidence |
| Native Activity + capability-limited SAF bridge | Fixed opt-in fields, no ambient probe sharing, existing permission set | Actual provider/AX/UI/process-death behavior | Run authored device tests on disposable AVD | Malicious picker/provider, rotate while choosing, background during write | Integration/device gap |

These mechanisms began as distinct checks; they are not six renamings of one
parser. Independent references are separately implemented by the same assistant,
not independent authors or expert review. Where source-only tests are used, the
claim is structural, never runtime verification.

## F. Proof fragments / artifacts

* Ticket uniqueness: Ticket uses reference identity, not digest/sequence equality.
  `save` takes the monitor, verifies PICKING and the exact ticket, then changes to
  WRITING and clears it before releasing the monitor. Any concurrent/late call
  fails its precondition before destination opening. Tests exercise real threads.
* Stopping geometry infeasibility: a fully contained rectangle with minWidth w
  and minHeight h cannot fit a work area smaller than w or h. The correct result
  there is refusal, not negative clamp ranges or shrinking below the minimum.
* Readback impossibility counterexample: two providers return identical finite
  observations through readback; only one retains data afterward. The application
  cannot prove persistent storage with this interface. SAVED wording is restricted.
* Reproducible executable artifacts: `run_r176_checks.py`, two test suites, three
  Kotlin probes/renderers, source/constant ledger, R146 differential, full suite,
  and authored native/provider tests. See RESULTS for exact outputs.

## H. Verification and imported-result alignment

Actual JVM comparisons: 1039 ranges, 169 decimal calculations, 262 geometry cases,
404 byte searches, 27 independently built ZIPs. Separate protocol exercise has
84 assertions, build-surface exercise 26 behavior assertions +24 constant checks.
Do not sum these into a fabricated number of completed features. R43's original
372 assertions ran too. Local full suite baseline/candidate results in RESULTS.
Native Activity compiled with `-jvm-target 17 -Werror` against API35; SDK37 target
is unchanged, but this does not establish SDK37/Compose/resource compatibility.

The bounded release-only range rule is inherited from the original R43 callers,
not inferred from general SemVer or the later broken placeholder. The old
Python-only tilde/caret oracle's incorrect zero-major/lower-patch cases were
corrected and strengthened, not used to bless a mismatching implementation.

## I. Repairs inherit no trust

Each repaired boundary gets a new attack: foreign identity ticket, concurrent
save claim, invalid replacement, malformed UTF-16/C1/bidi characters, EOF ZIP
junk, oversized decompression, manifest digest mismatch, impossible display minima,
Int extremes, source-prefix helper collision, decimal scale before arithmetic,
and post-repair source-union compilation. R146 tests and failure controls rerun
after moving its engine behind compatibility facades. No tests were disabled to
make the old red baseline green.

## J. Stopping and strongest verified result

Deliver H∂ only. A merged repository is not full product acceptance. No all-feature
A-grade claim: SDK/emulator/provider/AX and much original feature scope remain
unverified or absent. Highest-information next test: build the app with maintained
JDK17 and SDK37 in Android Studio Rabbit 1, run the authored native tests and
manually exercise Create/Open Document across local/cloud providers, rotation and
process death. Verify the actual secure review UI visually on device; do not clear
FLAG_SECURE just to manufacture a screenshot. Do not claim a universal optimality
or exhaustion of all possible engineering work.
