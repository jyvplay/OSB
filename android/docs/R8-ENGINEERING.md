# R8 continuation — encrypted index kernel and native verification integrity

**This is not the requested complete system.** Actual production code was added,
compiled and exercised, but substantial features remain unimplemented. There is
no Android APK/build/device/visual acceptance from this pass. No gated capability
was enabled and no whole requirement, module or spike was promoted.

## Runtime honesty

Actually used: repository/file tools, Git, GitHub CLI/API, Python3, Kotlin2.2.21,
Temurin17.0.9+9 runtime, SunJCE, real Linux files/OS locks, competing JVM worker
threads, real child processes, Python cryptography46.0.3/OpenSSL3.5.4 and Pillow12.0.0.
The two pipe-reader threads and two competing index writers are real executions,
not independent agents. Pillow inputs are parser fixtures, not native screenshots.

The previous tool cache was absent in this checkout; the compiler/runtime were
installed again from npm/PyPI. Compiler tarball SHA-512 was checked against npm
registry integrity. This is not upstream-signature/transitive-license clearance.
The runtime lacks javac/jdk.compiler. No full Android SDK, ADB, emulator, native
window viewer, physical receiver/reference device, legal expert or theorem prover
was available/used. GCC is present but unused. No agents, simulated Android APIs,
mock ADB, generated UI screenshots, remote build execution or CI dispatch occurred.
No new APK dependency was introduced; optional host verifiers are not bundled.
No commit or push in this continuation.

### What actually failed in existing CI

Retrieved job metadata for run38513458665: checkout, wrapper validation and JDK
setup passed; `android-actions/setup-android@v3` failed. Compilation, unit tests
and lint were skipped. The full log request failed at the restricted log-download
host; the exact setup root cause is **unknown**, not an invented compiler error.
Job metadata/annotations are retained in `verification/r8-ci-*.json`.

The workflow candidate now uses ubuntu-24.04's documented preinstalled SDK,
checks its executable explicitly, retains installation diagnostics, runs source
checks before SDK installation and moves setup-java to v5. Removing the failing
action is **not a verified CI repair**: the revised workflow has not run. Current
runner-image documentation is not proof of the exact image used by the failed run.

## A. Formal contract

**Admissible objects/access.** A bounded list of immutable `Doc` values; caller-
provided storage scope and AES key; one encrypted snapshot in trusted app-private
storage; cooperating writers using the same lock; authenticated snapshot loads
into an explicitly unlocked, in-process literal search session. Android source
selection, consent, secure-content exclusion, indexing schedule and live Find UI
are not connected to this kernel. A `Doc.source` string is not authorization.

Hostile root/same-UID code, noncooperating writers, directory/hardlink substitution,
provider tampering, memory disclosure and malicious JCA providers are outside the
storage guarantee. Input lists/arrays cannot mutate concurrently during admission.
The caller must supply a trustworthy crypto provider and independently protected
256-bit key. Keystore opacity does not itself prove hardware backing.

**Resources/units.** Encrypted snapshots:≤512 documents;≤1,048,576 total UTF-16
units across all fields;≤4 MiB serialized plaintext; file bound4 MiB+36 bytes.
Per-field limits remain LocalIndex's ID256/title512/body65,536/source1024 units.
Strict UTF-8, unique IDs, schema1; query≤1024 units/results0..100. These are logical
payload limits, not measured heap/frame/thermal budgets or global concurrent-call
limits. Encryption reveals ciphertext length; no padding/access-pattern hiding.

AES-GCM uses provider-generated96-bit IV and128-bit tag; emitted tag length is
checked before publication. Under the **additional hypothesis** of independent
uniform96-bit IVs, the birthday union bound for q encryptions is at most
q(q−1)/2^97. This is not a proof about a provider's entropy or a key-lifetime policy;
rotation/nonce lifetime acceptance remains open. SHA-256 revision equality is
conditional on no relevant digest collision. Revision tokens are not authentication.

Capture admission:≤32 MiB PNG;≤16,384px each dimension;≤16,777,216 pixels;≤4096
chunks; non-interlaced8-bit RGB/RGBA; exact row-byte count and filters0..4.
Only small sRGB/gAMA/cHRM/pHYs/sBIT metadata is admitted. Compressed ICC/text,
EXIF, animation and other extensions are refused. This is a declared subset, not
all legal PNG. Binary acquisition separately caps stdout32 MiB/stderr256 KiB and
uses a deadline; it is not an OS sandbox or a descendant-process supervisor.
No pixel-color accuracy or UX tolerance was measured.

**Quantifier order.** For every recorded contract input and observed schedule,
the reported checks passed. The intended storage invariant is: for every admitted
snapshot and cooperating-writer history satisfying the assumptions, a confirmed
save is authenticated/read back as that snapshot; mismatched revisions refuse;
search publication occurs only after full authentication/schema validation.
Finite executions do not prove that universal statement. No claim of exactly-once
IO, anti-rollback, physical durability, secure erasure or universal availability.

Full success requires **all** requested functions implemented, then **all**
mandatory build, native integration, privacy/security, accessibility, legal,
physical-device and performance gates passed with adequate reviewed evidence.
There exist missing functions and unpassed mandatory gates here, so that predicate
is false. One ciphertext roundtrip cannot satisfy it.

**Adjacent problems not substituted.** At-rest encrypted snapshots are not FTS,
encrypted computation or complete encrypted Find. Dropping session references is
not zeroization; locking cannot revoke returned hits. GCM authentication is not
anti-rollback. Atomic rename/readback is not power-loss proof or a SAF move journal.
PNG integrity is not native provenance/UI inspection. JVM compilation is not
Android compilation. Keystore source is not hardware-backed-key evidence. A
changed workflow is not a passing build. A populated module is not a completed one.

## B. Outcome space and thresholds

- **H+**: the product may be achievable with further implementation and the
  prescribed external/device/legal access. Not established by this pass.
- **H−**: acceptance in the current evidence model is impossible while mandatory
  physical/native observations are unavailable and functions are absent. This is
  a logical failure of the acceptance conjunction, **not** a general impossibility
  theorem about building the product or acquiring tools in another environment.
- **H∂**: local crypto/file contracts hold only under their key/provider/directory/
  cooperating-writer assumptions. Accepted PNGs are a subset; a genuine Android
  encoder outside it will be refused until the parser is safely extended using
  real captures. Hardware/Android/toolchain boundaries require new observations.

Evidence threshold: actual execution plus retained source hashes supports a
bounded test result. Android/native claims require actual Android execution;
physical/legal claims require their specified independent evidence. No counters
or status strings relax that threshold.

## C. Retrieved primary frontier

`research/r8-primary-frontier.json` records URLs, pinned commits, Git blob identity,
SHA-256 and excerpts, fetched through GitHub API:

1. **W3C PNG specification source.** Chunk layout/CRC, IHDR, IDAT/zlib and IEND
   inform the strict capture subset. CRC detects corruption, not authenticity.
   Valid pixels alone do not prove they came from this app or a real screen.
2. **OpenJDK17u Cipher API source.** AEAD AAD must be supplied before ciphertext
   processing; GCM requires non-repeating key/IV combinations; tag verification
   can fail on decryption. Imported only under conforming provider/key/nonce
   hypotheses. This is not an independent proof of AES/GCM security or Android's
   implementation. SunJCE/OpenSSL interoperability was separately executed.
3. **OpenJDK Key interface.** `getEncoded()` returns the primary encoding or null;
   it does not transfer mutation ownership. The actual lending-key counterexample
   below demonstrates why zeroing a returned array was invalid here.
4. **GitHub runner-image source.** ubuntu-24.04 documents SDK components and
   ANDROID_HOME. It supports a candidate setup route, not a successful build or a
   reconstruction of the exact failed runner image.

R7's pinned Files/FileChannel contracts still delimit atomic-move/force behavior;
no non-atomic fallback was added. No best-known whole-product achievability or
impossibility theorem, exhaustive literature survey, or expert review was obtained.
The exact open interface is provider/key lifecycle + source/privacy integration +
Android execution + the still-missing product functions and external gates.

## D. Negative space: solution-shaped failures

E=executed host/production test; S=source only; U=unrun mandatory integration.

| False solution shape | Detector / actual result |
|---|---|
|1. “All source folders exist, so the system is done” | E: module accounting stays0/48 accepted |
|2. “A JVM compile is an Android build” | E: doctor refuses missing SDK/full JDK; U: native compilation |
|3. “The CI badge failed, so Kotlin failed” | E: API metadata identifies setup failure and skipped compile |
|4. “Removing setup-android fixes CI” | U: revised workflow must run; no success asserted |
|5. “PNG signature/dimensions/IEND prove a screenshot” | E: prior header-only input now rejected; valid-header/no-IDAT patch attack also rejected |
|6. “IDAT exists, therefore pixels exist” | E: empty, malformed, truncated and extra deflate streams rejected |
|7. “Valid CRC means valid pixel size” | E: authenticated framing with oversized/short/long pixel output rejected |
|8. “Bounded pixels bound all PNG metadata” | E: compressed ICC/text/EXIF and animation refused; patch-specific profile attack |
|9. “Checking size after capture bounds subprocess memory” | E: real output/stderr overflow children terminated by bounded pipe reader |
|10. “Ciphertext search is implemented” | E: actual authenticated plaintext session search; no encrypted-computation claim |
|11. “AES-GCM alone validates document schema” | E: validly encrypted oversized lengths, unsupported schema, duplicate IDs and malformed UTF-8 refused |
|12. “Encrypt data but leave titles/sources as plaintext sidecars” | E+S: all fields serialized inside GCM; only ciphertext/empty lock file written |
|13. “A plaintext substring sample proves confidentiality” | E sample exists; claim explicitly downgraded to source design/conditional crypto assumptions |
|14. “Reuse the same scope everywhere; scope proves provenance” | E: wrong scope refused; U: caller source authorization/domain allocation remains separate |
|15. “Wipe every array returned by a key” | E: actual pre-repair caller key was zeroed; repaired lending-key regression passed |
|16. “Decrypt failure can leave old search results active” | E: failed unlock locks session before load; old results no longer searchable through it |
|17. “Session lock revokes plaintext already returned” | E: retained hit remains readable; limitation intentionally tested |
|18. “Content token prevents competing overwrite” without a lock | E: two real instances/writers; exactly one confirmation from shared revision |
|19. “Atomic rename is full durability/transactional journal” | E Linux operations only; U power loss/Android/SAF remain unproved |
|20. “Authentic old ciphertext is current” | E: actual rollback loads old authenticated snapshot; no anti-rollback claim |
|21. “Missing Keystore key means generate a replacement during read” | S: read/decrypt paths refuse; U: native test authored, unrun |
|22. “Keystore means StrongBox or biometric authentication” | S: corrected claims; key policy is not biometric; hardware/device gates remain U |
|23. “Plaintext and encrypted caches can share silent fallback” | S: no plaintext fallback; E: unsupported/corrupt containers refuse |
|24. “Encrypted index means Notes files themselves are encrypted” | S: NoteFiles remains plaintext; no contrary claim |
|25. “354 new assertions complete S4 or P1” | E: release/gate audit still refuses; S4 physical/source decisions remain blocked |
|26. “No physical tools proves permanent product impossibility” | Not inferred; H− is restricted to present acceptance/access |

The modal shortcut is substituting a lower-layer positive result for the full
success conjunction. The existing release gate, doctor, scope audit, real negative
compiler tests and new malformed-PNG/encrypted-schema tests detect concrete forms
of that substitution; none is itself a product-completion proof.

## E–G. Mechanism portfolio and second-order attackers

These are distinct, complementary mechanisms, not six finished alternative apps
or claimed independent agents. Crypto/storage and capture/transport were developed
and exercised separately before integration. “Established” means the finite
executed portion unless explicitly labeled source-only.

| Mechanism | Concrete artifact / established portion | Structure-specific attacker / cheapest falsifier | Exact remaining interface; gap type |
|---|---|---|---|
| AEAD domain-bound container | EncryptedIndexCodec; SunJCE/OpenSSL both directions | Wrong key/scope, altered nonce/tag/header, reused nonce/provider policy | Crypto/provider/nonce lifetime/Android key acceptance; local, not full Find |
| Bounded strict schema + immutable ownership | Codec admission/readers; count/field/unit/UTF-8/ID checks | Authenticate malicious lengths with a real key; mutate caller list; borrowed encoding array | Concurrent input mutation excluded; source discovery/privacy original scope |
| OS lock + optimistic revision + validated atomic publication | EncryptedIndexFiles; real filesystem/readback and competing writers | Stale/cross-scope revision, symlink target, corrupt current file, storage/force fault | Android/power-fault matrix unrun; SAF journal is a separate original problem |
| Explicit plaintext-session lifecycle | EncryptedSearchSession; locked/failed-unlock refusal | Retain a hit before lock; fail reopening with another key | Memory copies/lifecycle/secure UI/source revocation not solved; native integration/original privacy scope |
| Layered PNG integrity and subset admission | png_capture.py; CRC/order/IDAT/deflate/filters; Pillow checks | Valid CRC but invalid rows; overexpansion; metadata bomb; real wide-gamut encoder | Real encoder compatibility and human visual review unrun; local capture gap, AX remains original scope |
| Bounded real subprocess pipes | bounded_process.py; actual stdout/stderr/timeout experiments | Fill both pipes, emit beyond budget, exit nonzero, keep child alive | Not a descendant supervisor or OS resource-isolation proof; local lab robustness |
| Existing-key-only native adapter | IndexKeys + corrected VaultCrypto read path + native test | Delete/invalidate key; cross-instance creation; unsupported OEM KeyInfo | Source-only, trusted single owner process; native/device evidence missing |
| Evidence/phase separation | CI job metadata, revised workflow, release/ownership audits | Setup failure mislabeled compiler failure; stale artifact/status promotion | Next real CI build still needed; all original feature/device/legal gaps remain |

The key mutation was found in this pass's **new code**, not retroactively attributed
to an older implementation. Executing a custom SecretKey that lent its backing
array printed `unchanged:false; all-zero:true`. The repair stops mutating the
encoding; the regression additionally decrypts using an independent SecretKeySpec
constructed from the original bytes. Cipher tag size and candidate roundtrip are
also checked before touching existing ciphertext. Native key creation serializes
instances in one process only; it is not a cross-process key-rotation coordinator.

## H–I. Verification, repair and reruns

**171,082 JVM assertions passed** =153,216 baseline +2,056 calculator +8,916 editor
+5,388 copy +61 notes +354 encrypted-index +45 search/gates +1,046 window/stream.
Twelve production pure libraries compiled separately; two intended module-boundary
negative compilations rejected; positive consumer compiled. Three historical
CastV2 unchecked-cast warnings remain. No receiver protocol test was performed.
The full runner was rerun after the key-array repair and final prepublication
checks, not inherited from the initial candidate.

**47 Python tests passed**, including7 PNG test methods,3 real-subprocess methods
and source guards (which do not count as Android execution). PNG tests exhaust
single-byte changes and truncation offsets for their small fixture, not all PNGs.
Repaired capture handling includes valid-header/no-pixels and compressed-profile
attacks aimed at the new validator, not only the historical malformed header.

**Independent implementation checks:** actual SunJCE→OpenSSL and
OpenSSL→production Kotlin codec with independently serialized UTF-8;13 PNG parser
cross-checks using Pillow. These are external software verifiers, not independent
human review or a theorem prover. No parser fixture was delivered as a native image.

Reproduce with `tools/run_jvm_contracts.py`, Python unittest discovery, and optional
`tools/run_independent_checks.py`. Actual source hashes/output are in:
- `verification/r8-continuation-jvm-contracts.txt` and boundary regression;
- `r8-independent-checks.json`, `r8-encryption-probe.txt`;
- `r8-key-ownership-before.txt` (actual counterexample plus consumer source);
- `r8-continuation-host-checks.txt` and `r8-source-inventory.json`;
- `r8-ci-job-metadata.json` / `r8-ci-annotations.json` (past CI, not this patch).

Android `EncryptedIndexDeviceTest` is authored only. It exercises actual Keystore
creation/read/key-loss and encrypted file/search behavior when run; no substitute
host Keystore was introduced. S4 is still blocked. Original Notes encryption,
Android session lock callbacks, source consent/exclusions and live Find indexing
are **not** enabled by these kernels.

## J. Full scope accounting and stopping boundary

**48 included modules;0 fully specification-complete/accepted (0%);48 outstanding
(100%).** Evidence categories remain31 Android-uncompiled,10 selected kernel
contracts,5 compiled-only,1 harness,1 empty profile—not completion percentages.
231 requirement records:171 not implemented,56 partial/unverified,3 excluded,
1 superseded. All30 spikes unpassed. The cross-cutting audit now has36 obligations:
encrypted search/privacy integration was made explicit rather than hidden in a
broad privacy row. Original-spec transcription itself is not independently proved.

| Specifically requested product area | Exact current gap |
|---|---|
| Complete P1 |61 P1-containing rows unverified; native build, integration and functionality missing |
| Actual BlackTop | Interactive external H1/blackout/FGS/hotplug/OEM60-minute acceptance absent |
| Keyboard/accessibility release pass | Only source and authored tests; no TalkBack/Switch/Voice/200%/keyboard acceptance |
| Multi-process parser isolation | Complete service/IPC/resource/permission boundaries absent |
| Encrypted search | New tested persistence/session kernel; FTS, source permissions/exclusions, Android lifecycle/keys and live Find integration absent |
| Journaled fault-safe moves | Backend refusal retained; requested SAF publish/retire/undo/fault journal absent |
| Terminal PTY | Packaged PTY/process/16K/input/lifecycle acceptance absent; S5 blocked |
| Office editing | Isolated licensed engines/registry/fidelity/loss/safe-save absent; S10/S11 blocked |
| RAOP and mirroring/receiver | Hardware/legal/protocol/trust/stop/exclusion gates remain open; no new protocol claim |
| Lens processing/Meet/browser injection | Processed track routes/own Meet/per-site proof/model/device gates absent |
| Mac-backed iMessage | Own-Mac transport/auth/delivery integration and legal/device evidence absent; no hosted/no-Mac substitute |
| Full browser | S29/S30 engines, process/privacy/permissions/download/extension requirements unimplemented/unaccepted |
| Timeline backup | No accepted encrypted incremental timeline/transactional restore/health drills; S7 blocked |

Also missing: requested architecture slots, Room/Hilt/process coordination,
commerce/offline entitlement, compileSdk37/target37 migration, native16K audit,
SBOM/license closure and measured reference-device budgets. Budgets stay E0.

**Strongest verified artifact:** the actual bounded encrypted-index/search kernel,
repaired capture admission/transport code, independently checked formats and
reproducible source-bound test evidence. Not an APK or full product.

**Next highest-information test:** with authorization, push this branch and run
the revised real CI setup to reach actual Android compiler/lint output. On a
provisioned laptop run `native_lab.py doctor`, both-flavor build, then the consented
disposable-emulator suite—including Keystore test and real captures. Repair real
failures before trusting any native integration. Separately, the listed substantial
feature implementations and physical/legal/accessibility spikes must still be
completed. Testing alone cannot close this backlog.
