# R146 — executable contract and adversarial review (A–J)

Date: 2026-11-27. Baseline: `92bcd3278445af8467da5e3fd5cea7531b9815f4`.
This is an engineering contract, not full v2.0/v2.1 acceptance. Results and
per-feature testability are in [R146-RESULTS](R146-RESULTS.md).

## A. Formal model

Objects are bounded caller-supplied strings, byte arrays, ordered records,
operation lists and sample sequences. Each production file states its grammar,
units and caps. Access is local computation; the diagnostic CLI additionally
reads one explicitly named file and creates one explicitly approved NEW file.
No ambient collectors, network requests, Android permissions, UI event injection
or scratchpad exports are added. The host probe accesses production public APIs,
not Python substitutes. Work counted: source files actually compiled, corpus
cases actually executed, independently compared verdicts, explicit injected
failures, filesystem/stream artifacts, and named regression tests. Counts are
not conflated with assertion totals or full requirements.

Target property, in quantifier order: for every input x in a declared lane's
admissible domain, production(x) should equal the specification(x); for invalid
x it should refuse or skip exactly as declared. **Evidence is weaker:** for
every x in the fixed 314-case corpus, execute production(x) on JRE 17 and compare
to a separately written Python implementation. This finite experiment does not
prove the universal property, arbitrary Unicode parity, memory bounds for huge
malformed inputs, or device behavior. CLI success requires an immutable scrubbed
snapshot r, approval equal to SHA256(UTF8(r)), then a writable new destination;
any earlier digest mismatch must produce zero output. Digest matching binds
content, not user identity, intent, human review, or secrecy.

Regime: Kotlin/JVM target 17, `-Werror`, pinned host compiler/JRE; no Android SDK
or device run. Resource examples: 512 edit ops/4096 UTF-16 document units;
256 diagnostic records/1024 units each; 12 password-history entries and 600000
PBKDF2-HMAC-SHA256 iterations. Password derivation must run off a future UI thread.

Adjacent problems NOT substituted: annotation geometry is not PDF serialization
or signing; NTP timestamps are not AirPlay streaming; alarm timing is not Android
delivery; URL detection is not safe navigation; metadata subset rewriting is not
universal XML sanitization; a heuristic export is not anonymity; an executable
module subset is not full-spec Class A.

## B. Outcome space and evidence threshold

* **H+**: all original feature, Android/provider/UI/AX/device/security acceptance
  obligations pass with revision-bound evidence. Not established.
* **H−**: a counterexample disproves a claimed bounded contract or a new regression
  appears. Fix, add a discriminating case, rerun; do not average it away.
* **H∂**: finite host subcontracts and an offline export workflow execute correctly
  on tested inputs, while integration and full acceptance remain open. This is
  the strongest supported outcome of this revision.

A green source scan or Python-only test cannot satisfy even the JVM execution
threshold. Full suite baseline failures remain visible instead of being skipped
or relabeled as successes.

## C. Retrieved frontier / permissible mechanisms

Retrieved on 2026-11-27, for design/format review only; no upstream implementation
was copied or renamed:

* [OWASP logging guidance](https://github.com/OWASP/CheatSheetSeries/blob/master/cheatsheets/Logging_Cheat_Sheet.md): exclude sensitive fields, prevent CR/LF injection, and avoid leaks on logging failures. Here: explicit allowlist, selected-value control refusal, generic CLI error, separate review; this is not a general security audit log and intentionally refuses an invalid export atomically.
* [OWASP password storage](https://github.com/OWASP/CheatSheetSeries/blob/master/cheatsheets/Password_Storage_Cheat_Sheet.md): prefer memory-hard password schemes where available; its PBKDF2-HMAC-SHA256 configuration is 600000 iterations. The initial unshipped 1000-round SHA construction was replaced, not blessed by testing. FHC2 uses the JCA primitive, random salt generation/persistence remains the integrating vault's responsibility. This does not claim Argon2-equivalent resistance or FIPS certification.
* [libarchive cpio format manual](https://github.com/libarchive/libarchive/blob/master/libarchive/cpio.5): newc uses 8-hex-digit fields, four-byte name/data alignment and a trailer; CRC/old binary/odc/64-bit variants are distinct. R146 refuses unsupported variants instead of pretending general cpio support.

No global algorithmic best-known/optimality claim is made. New implementation
code is Apache-2.0; the dependency surface remains JDK/Kotlin stdlib/JCA only.
Consulted documentation is linked, not vendored.

## D. Negative-space result shapes (failure-detecting tests)

The modal shortcut is to report Python oracle tests as execution of Kotlin.
The differential runner compiles actual production files and weakens one in a
copy: W2 must fail the comparison. Result-parser tests reject empty/duplicate/
wrong-lane output. Remaining shortcuts and discriminators:

| # | Invalid result shape | Detection or explicit limit |
|---|---|---|
| 1 | Python-only oracle presented as product execution | Separate real kotlinc/JVM runner; W2 mutation |
| 2 | Empty corpus accepted | W1 minimum count/lane checks |
| 3 | Probe silently skips a lane | Missing-result differential |
| 4 | Duplicate case overwrites an earlier wrong answer | `test_duplicate_results_cannot_pass` |
| 5 | Right answer under wrong lane | `test_wrong_lane_cannot_pass` |
| 6 | Static source labels treated as behavior | W2 changed bound must alter runtime verdict |
| 7 | Baseline defect cannot be reproduced | W3 forward-reference/cyclic component vectors |
| 8 | Approval from old review exports changed data | stale-approval API and filesystem tests |
| 9 | Approval refusal still opens destination | no-file test + zero-byte sink assertion |
| 10 | File export clobbers an existing artifact | CREATE_NEW + existing-file test |
| 11 | Symlink destination redirects overwrite | symlink-target preservation test |
| 12 | ZIP contains unreviewed/raw records | Python unzip, fixed entry names, exact content and digest |
| 13 | `,;=%` corrupt literal diagnostic values | typed API punctuation assertion + differential vectors |
| 14 | Redaction regex is over-escaped and matches nothing | explicit email/IP/token expectations |
| 15 | Decoded newline/ESC forges output records | transport-decoded line/control refusal vectors |
| 16 | Export reports success after sink failure | real throwing OutputStream test |
| 17 | Structured edit API bypasses parser caps | 513 ops, zero delete, negative position tests |
| 18 | Integer delete range wraps | Int.MAX_VALUE deletion test |
| 19 | Last edit exceeds doc cap but no next iteration checks it | final-document-bound vector |
| 20 | Coalesced insert cannot be reimported | cap fusion at 256, fixed-point reimport test |
| 21 | Python bankers rounding changes geohash ties | pinned tie and real JVM comparison |
| 22 | Hyphenated XML tag scanner truncates name | editing-cycles/user-defined vectors |
| 23 | A host drawing is presented as Android screen | PNG labels HOST Java2D; no device acceptance |
| 24 | A weak password primitive is promoted by agreeing tests | replace homemade chain with JCA PBKDF2; security review still open |

## E/G. Six mechanism-distinct approaches and second-order adversary A(C)

| Candidate | Established portion | Unresolved interface | Cheapest falsification | Adversary after seeing candidate |
|---|---|---|---|---|
| Two-pass message graph classification + iterative traversal | Forward parent/cycle defects reproduced against adapted baseline; candidate vectors match | Duplicate-ID legacy policy and callers; full messaging UI | Child before parent, detached cycle | Supply a valid root to hide a separate cyclic component |
| Fixed-point positional edit compaction | Each permitted local fuse preserves replay; executed replay equality | Distributed concurrency/CRDT merge not supplied | Replay before/after and reimport output | Use direct list API, overflow a range, grow on last step |
| Allowlist + immutable content-addressed export | Only selected scrubbed text reaches two fixed ZIP entries | Human review, provider/Android UI, secret shapes outside heuristics | Change record after preview, reuse old digest | Encode separators, forge lines, abuse generic error reflection |
| Bounded byte-offset archive/thumbnail scanning | Valid/truncated/unsupported vectors match | Extraction, full PSD decode, broad format compatibility | Truncate each mandatory field; corrupt offsets | Craft declared lengths near signed-integer limits |
| Canonical codecs and interval/geometry arithmetic | Round-trip/refusal/boundary vectors across macro/NTP/geohash/PDF | Full file/transport integration and platform timers | Bad escapes, era edge, exact decimal tie | Attack the independent oracle's numeric/Unicode semantics |
| JCA password-history KDF | PBKDF2 output matches Python hashlib, reuse/prune vectors | Salt generation, secure persistence, UI dispatch, rate limiting | UTF-8 password and existing hash reuse | Offline guesses, missing salt reuse context, CPU exhaustion |

## F. Artifacts, proofs and experiments

Executable design: production files + `tools/diagnostics/DiagnosticExportCli.kt`,
`tools/diagnostic_export.py`. Reproducible experiments: `run_r146_checks.py`,
`test_r146_system.py`, `test_r146_runner.py`, pinned corpus regression tests.
Counterexamples: W3 prints six old-logic mismatches. Visual artifact: actual
Java2D output from the session's reviewed text, not generated artwork.

Local compaction argument: inserting b immediately after a has the same result
as inserting a.text+b.text at a.pos; deleting twice at the same live position
removes the same contiguous original interval as deleting the sum. All other
operations retain order. A fuse strictly decreases operation count, bounded
initially by 512, so at most 511 fuses are possible. Not fusing an insert whose
combined length exceeds 256 preserves the public input grammar. The code also
replays original and fused scripts, checking equality. This argument is only
for sequential positional logs, not concurrent logs or general minimal scripts.

Snapshot argument: Review retains immutable text and private bytes derived once;
writeApproved compares approval to the digest of those bytes before constructing
a ZIP stream. A mismatch therefore writes nothing through this method. SHA-256
collision resistance is an external assumption; approval is not authentication.
I/O failure can leave a partial archive; the CLI removes its newly created file
on ordinary exceptions, without claiming crash-safe atomic publication.

## H. Verification binding

`kotlinc -jvm-target 17 -Werror`; real JRE 17 probe; independent Python verdicts;
separate Python ZIP/manifest reader; injected stream failure; before/after named
host-suite failure comparison; static privacy/traceability checks. The pre-fix
MessageThread does not compile verbatim because it calls missing `getOrEmpty`.
W3's only compile adapter is `children.getOrEmpty(id)` →
`children[id].orEmpty()`; no graph logic is changed. It is evidence about that
adapted old logic, not a claim that the raw baseline compiled.

Source hashes in the module census bind reviewed paths, not compiler provenance
or semantic truth. Golden-vector hashes pin input+verdict, not an independent
proof. Broad-suite failures are reported separately in RESULTS.

## I. Repairs inherit no trust

After agreement was first reached, attack the repair again: direct API caps,
final-state growth, range overflow, unimportable fused output, multiline export
injection, stale approval, symlink/existing destination, sink failure, malformed
UTF-8. Add these cases before rerunning. Audit rejected the weak initial password
hash even though its tests agreed; JCA/Python PBKDF2 now supplies the primitive.
No full security certification is inferred from the replacement.

## J. Stopping frontier

The deliverable is the strongest executed host artifact here, **H∂**, not a claim
that every original feature is complete. Android SDK/device/provider/AX gates
were unavailable and were not simulated. Remaining known baseline failures are
not hidden. R146 adds a working file-export workflow in addition to kernels, but
most new slices are not wired into Android screens/services. Full Class A remains
0/50; further application integration, device testing and security review remain
mandatory. No claim of exhaustive search or exhausted global engineering budget.
