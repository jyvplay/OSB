# R7 — full recorded-scope review and native hardening

This is a substantial source/verification pass, **not a completed v2/v2.1 product**.
There is still missing implementation, not merely missing device testing. No
Android build, APK, native screenshot, visual review or pilot clearance occurred.

## Runtime honesty

Actually available/used: repository/file tools, Git, Python, cached Kotlin2.2.21,
cached Temurin17.0.9+9 runtime, JVM execution, real temporary filesystem operations,
real OS file locks and two JVM worker threads in a competing-writer experiment,
GitHub API via `gh`, and supplementary tree-sitter parsing. GCC is available but
was not used. No independent agents, simulator, theorem prover, Android SDK,
Gradle distribution, ADB, emulator, browser UI inspection, physical reference
device, expert review, legal review, or remote CI execution was used. No commit,
push or workflow dispatch. No new external library dependency was introduced.

The Java module inventory includes `java.compiler` (the API), but **not
`jdk.compiler`** (javac); there is no full JDK. Android Studio itself is not
required for command-line Android builds, but the missing SDK/JDK/dependencies
are. Required Google/Maven/Gradle download access is outside this sandbox's
available allowlist. A software emulator is not disproved merely by missing KVM;
no suitable emulator/system image was available or booted here either.

The R6 laptop lab is extended, not falsely described as running here. It now
requests seven actual native views per font setting, adds real Android notes/
SQLite obligations, and retains explicit disposable-emulator consent. Host tests
exercise parsing/admission only, not an ADB substitute. See `NATIVE-LAB.md`.

## A. Formal contract

**Objects and access.** Current native sources; canonical desktop states with at
most48 windows/three workspaces; immutable command descriptors; a bounded local
literal index; named UTF-8 Markdown files in a trusted app-private directory;
explicit note revisions and user-confirmed actions; unavailable SAF mutation
operations. UI, provider, JVM file I/O and device acceptance are separate layers.

Notes writers must cooperate on the same persistent lock file. Symlink endpoints
are refused, but malicious same-UID/root actors, hardlink substitution, directory
replacement and noncooperating writers are outside this guarantee. A revision is
**name+content digest**, not authentication or a monotonic generation. Refusal of different content assumes its SHA-256 digest differs; no collision-impossibility theorem is imported. Equal-byte
delete/recreate ABA is not detected; this boundary was actually executed and
recorded. A new note requires absence; changed content or a cross-note token is
refused. Failures do not claim a successful save. After publication/readback/
directory-force uncertainty the user must reopen to reconcile, not silently retry.

**Resources/units.** Notes:256KiB encoded UTF-8 each,256 visible `.md` files, names
1–64 UTF-16 units and≤128 UTF-8 bytes, one transient file per save. This does not
bound crash-leftover files or prove heap/frame time. Window viewport inputs are
finite and in(0,100000]dp; minimums240×180 Compact/Balanced and320×240 Touch.
A complete48dp reachable region is conditional on a viewport large enough for it;
sub48dp robustness tests are not usable-layout acceptance. Commands≤4096,≤32
keywords each, query≤1024 UTF-16 units, results≤100. Index≤2048 documents,
body≤65536 units, aggregate payload≤8Mi UTF-16 units, not measured heap bytes.

**Quantifier order.** For every *recorded* test input and observed worker run,
the recorded assertions passed. Intended invariant: for every admitted sequential
command history and every cooperating notes-writer schedule satisfying the access
assumptions, each published desktop result owns its list, differing-digest
or cross-note writes are refused, and a confirmed save refers to the exact bytes
read back. Finite tests are evidence for these invariants, not a universal proof.
Input collections must not be concurrently changed during reduction/admission.

Full success is stronger: **all** required functionality implemented and **all**
mandatory build, integration, security, accessibility, license/legal, platform,
physical-device and performance gates passed with reviewed evidence. One JVM
success or one screenshot cannot satisfy that conjunction. Any unmet mandatory
gate means the full success predicate is false here.

**Adjacent problems excluded.** Kotlin compilation≠Android/Compose compilation;
AtomicFile≠Room; Java force/readback≠measured physical durability; a modal palette≠
full indexed Find; Markdown-source editing≠complete Notes; backend refusal≠
journaled fileops; a session mirror≠external interactive H1/BlackTop; an emulator≠
two reference devices; static ownership≠Gradle dependency resolution; screenshots≠
keyboard/TalkBack/Switch/Voice accessibility; source completeness≠acceptance.

## B. H+, H− and boundary

- **H+**: the complete requested product can be delivered after engineering and
  required external verification. This is a candidate outcome, not established.
- **H−**: full acceptance cannot be declared from this turn's evidence. No global
  impossibility theorem for the product is claimed. Under a finite-memory,
  arbitrary-key-history model, exact membership of all previously closed Open
  keys needs distinguishing their different subsets; the current live-window
  key set plainly does not do that. This narrow limitation is not impossibility
  of an ordered epoch/sequence design or of the whole shell.
- **H∂**: exact half tiling is admissible only when viewport width≥twice the
  profile minimum and height≥its minimum. At smaller viewports it is refused,
  rather than pretending two oversized overlapping panes are halves. Notes
  guarantees depend on cooperating private writers and supported local filesystem
  operations. Android and physical results require additional tools/evidence.

Threshold: only actual executions promote their explicitly bounded subclaims.
No whole feature, module, spike or E0 budget is promoted in this pass.

## C. Primary-source frontier

Retrieved pinned OpenJDK17u `Files.java` and `FileChannel.java` at
`9c16e89d275654cee98f5374434bea2097dda91e`. Hashes, blob IDs, URLs and excerpts are
in `research/r7-files-frontier.json` and `r7-channel-frontier.json`.

Imported contract: `ATOMIC_MOVE` requests atomic filesystem movement; other
options are ignored, existing-target replacement is implementation-specific, and
unsupported atomic movement must fail. Therefore the store uses **no truncating/
non-atomic fallback**. This is not an exclusive-create primitive against arbitrary
external writers. File-channel force has a local-storage/channel-write contract;
remote storage lacks the same guarantee and OS metadata behavior varies. It does
not supply a portable proof of directory/rename or physical-power-loss durability
on Android. Successful Linux JVM experiments establish only the operations they ran.

A pinned AndroidX Key source declares `NumPadEnter`; provenance is in
`r7-key-frontier.json`. It is not an exact BOM resolution/compile result. R6's
pinned Kotlin smart-cast rule and negative compiler checks remain applicable.

Pinned AndroidX activity source declares `enableEdgeToEdge` and
`SystemBarStyle.dark`; `r7-edge-frontier.json` records provenance and excerpts.
This is not the resolved activity1.9.3 artifact, Android compilation or native
inset acceptance. The Files excerpt also documents the fresh temporary-file
creation contract; the scratch-retention regression is an actual Linux test,
not a reproduced random-name collision or a hostile path-substitution proof.

Gradle's official8.13 release metadata was rechecked: ZIP asset digests were null.
A guessed public website-source repository returned404; public organization
metadata did not provide the hoped-for website checksum source. No checksum was
invented or copied from an unverified third party. Distribution pinning remains
open; wrapper-JAR integrity is separately checked. This is not an exhaustive
worldwide literature/search claim or independent expert review.

## D. Negative space — solution-shaped failures

E=executed production/compiler/host test; S=source audit only; U=unrun native/device
obligation. These classifications must not be merged.

| False result / adversary | Detector and actual state |
|---|---|
|1. “All modules exist, therefore complete” | E: module audit refuses status promotion without common gates;0/48 complete |
|2. “169k assertions mean Android works” | E: doctor refuses missing prerequisites; no APK/UI execution |
|3. Combined compilation represents module boundaries | E:12 independent libraries plus retained negative/positive consumers |
|4. Kotlin `List` means immutable | E: actual mutable-cast counterexample, then mutation rejection |
|5. Freeze only changed states, leak no-op input lists | E: no-op publication detaches caller list; patch-specific test |
|6. Shallow command copy isolates keywords | E: original caller keyword mutation changed search; nested list now copied/frozen |
|7. Negative/unbounded queries and inventories are harmless | E: query/result/body/entry/aggregate-limit rejection |
|8. Title ranking alone is complete Find | S/U: only real module/window/workspace actions wired; other sources explicitly excluded |
|9. Tile overwrites the only normal geometry | E: old geometry-loss observation; tile chains retain original rectangle |
|10. Repeated maximize replaces restore bounds | E: repeated commands preserve original normal bounds and are equal no-ops |
|11. Geometry from an old viewport remains valid | E: stale tile command refusal and resize/reflow tests |
|12. Two below-minimum panes count as valid halves | E: exact width boundary tests for each profile |
|13. A new codec can just reject old layouts | E: genuine prior-writer v1 fixture read by v2; hash pinned |
|14. Validating note names only on save is enough | E: actual legacy path expression escapes its directory; all new operations validate |
|15. Save means direct overwrite is safe | E: existing-target, stale-content and cross-note rejection; actual temp/atomic/readback paths |
|16. Equal bytes authorize another note | E: identity-binding patch attack refuses cross-note equal-content token |
|17. One writer instance means no races | E: two real store instances/worker threads compete; one confirmation, no stale overwrite |
|18. UTF-8 replacement decoding is acceptable | E: invalid surrogate/malformed bytes rejected without silently repairing text |
|19. Note hashes detect generation/ABA | E: actual equal-content delete/recreate demonstrates the limitation; no such claim |
|20. “Readonly UI” protects unsafe backend code | E+S: all8 backend mutation policies deny; destructive legacy bodies removed |
|21. Corrupt legacy receipts mean empty trash | S/U: parse failure now propagates and UI reports retained bytes; Android unrun |
|22. Schema upgrade can drop owner data | S: destructive SQL removed; U: actual Android SQLite preservation test authored |
|23. A stale picker result bypasses a blocked module | S: image/docs/PDF/PSD/backup callbacks recheck admission; native delivery unrun |
|24. IO ran, so a UI lifecycle race is solved | S/U: ViewModel ownership/busy guards and confirmation, but rotation/IME/device tests unrun |
|25. Source ownership scanner is a compiler | E: package-string false positive was repaired; limitations remain explicit |
|26. Fake previews or generated PNGs verify native UI | None produced; U: UiAutomation captures required |
|27. Test counters are correct regardless of failures | E: native-lab receipt parser rejects wrong counts/crash/duplicate summaries |
|28. Current open-key logic prevents all future replays | E: actual post-close replay counterexample; gap remains explicit |

The modal shortcut is still conflating narrow host execution with full Android
acceptance. The actual negative prerequisite checks, isolated compiler tests,
module audit and fail-closed release audit detect that substitution.

## E–G. Independent mechanisms, artifacts and candidate-specific attackers

These mechanisms were developed as distinct components and then integrated, not
claimed as independent agents or six alternative completed products. “Established”
below means the stated finite evidence/structural argument, not a theorem prover.

| Mechanism / construction | Artifact and established portion | Strongest specific attacker / cheapest falsifier | Exact unresolved interface; gap type |
|---|---|---|---|
| Copy/freeze published state including no-ops | WindowEngine plus119 focused checks; real pre-fix mutation recorded | Mutable cast, mutate caller input after a no-op, mutate restored list: rejected | Concurrent mutation during admission excluded; UI/thread/lifecycle verification local; full WM original scope remains |
| Preserve normal rectangle separately from tile state | DesktopCodec2 + actual v1 fixture; tile chains/reflow/recovery checked | Repeated max, density change, stale viewport, impossible half, corrupt pair | Native pixel geometry/drag/display tests local; quarter/custom groups and global replay still original-scope gaps |
| File lock + content/name revision check | NoteFiles and61 real-filesystem assertions | Competing instances, same-content different note, symlink, byte growth, invalid UTF-8 | Trusted cooperating directory and local FS; Android syscall acceptance local; complete Notes/lifecycle original scope remains |
| Temporary-file publication with no non-atomic fallback | Production NoteFiles force/move/readback executed on Linux | Unsupported atomic move, storage exhaustion, interruption after publication | Those fault/device paths unrun; Java contract does not prove Android directory/power durability; local integration gap |
| Defensive command snapshot + bounded serialized index | Registry/LocalIndex;45 combined search/gate checks | Mutate caller keyword list, cast returned list, capacity failure, Turkish-locale invariant | No fuzzy/FTS/crawler/Android latency proof; full Find original scope remains |
| Native command identity dispatcher | CommandPalette/MainActivity; source wired to actual module/focus/workspace actions | Missing owner, stale window ID, Enter/keyboard/200% behavior | Ownership checks passed; actual Compose/UI compilation and interaction unrun; local integration gap |
| Denial at mutation entry points | FileMutationPolicy;8 policies executed, old destructive bodies removed | Bypass the UI Boolean or experimental consent | Refusal is established, functionality is NOT; implementing accepted journaled fileops remains the original S6 problem |
| Refuse unknown schema transitions and stale module results | SQLite migration/insertion guards; picker callback guards | Bump schema, failed insert, restored picker callback while module blocked | Structural checks only; native tests required; local verification gap, not completed storage architecture |
| Full-scope/source-ownership ledger | FULL-SPEC-REVIEW plus main-source hashes;231 rows/35 foundations | Omit a family, remove a real module edge, confuse implementation with api exports | Register-transcription fidelity, generated/external symbols and semantic correctness not proved; full acceptance still original problem |
| Native lab with explicit consent and fresh evidence | Native smoke/SQLite/notes tests;7 views×2 font settings requested | Wrong serial/package, stale PNGs, test crash, missed font restoration | Host parser/admission checks executed; actual build/emulator/human visual review unrun; environment interface unresolved |

## H. Executed verification and preserved counterexamples

**169,801 JVM assertions passed** =153,216 baseline +2,056 calculator +8,916 editor
+5,388 copy +61 notes +45 search/gate +119 window restore. Twelve real pure
libraries were compiled separately. The performance, Beam and Files-denial policy
files are selected pure sources from Android modules, not complete Android builds.
Three pre-existing CastV2 unchecked-cast warnings remain. No actual receiver test.

**30 Python tests passed.** Source ownership, project/XML/fixture/traceability
checks passed; the release check correctly refused. Positive/negative module
boundary compiler gates were rerun. All compiled-source hashes are recorded.
Supplementary grammar parsing flags three files that the real Kotlin compiler
accepted; it is not used to overrule compilation or imply Android correctness.

Evidence:
- `verification/r7-before.txt`, `r7-counterexamples.kt.txt`: actual old command/
  window kernels plus exact legacy note path-expression observation. **The old
  Android NotesStore itself was not executed.**
- `verification/r7-window-alias-before.txt`: real published-list mutation.
- `verification/r7-policy-alias-before.txt`: an actual in-process cast changed
  Beam admission before the policy catalogue was frozen. Not a remote exploit.
- `verification/r7-jvm-contracts.txt`, `r7-boundary-regression.txt`: final actual
  compiler/contracts and source hashes, not a source-only claim.
- `verification/r7-remaining-boundaries.txt`: actual final-library post-close Open
  replay and note content-ABA boundaries, retained rather than concealed.
- `verification/r7-source-checks.txt`, `r7-source-inventory.json`: executed host
  checks and source fingerprints, explicitly not Android compilation.

No independent expert or theorem-prover output exists. External checks are the
real compiler/JVM/filesystem and retrieved primary-source contracts, not another
model's agreement. Source-only interfaces retain the lower evidence level.

## I. Repairs and integration attacks

The final full JVM runner was rerun after both window and policy-catalogue immutability repairs; no earlier
result was inherited. Additional attacks target no-op aliasing, cross-note
revision binding, multiple writers, stale tile viewports and schema pairs.
Temporary-file creation was changed to the JDK fresh-file primitive so cleanup cannot treat a failed name reservation as ownership of a pre-existing file. A real pre-existing scratch-file retention check was added; the full JVM suite was rerun afterward.
The second-order list attack also applied to PilotPolicy: replacing a blocked entry through a MutableList cast changed admission. The catalogue is now frozen; replacement, clear and consent-bypass regressions passed.
The source scanner initially mistook benchmark package strings for imports;
string/comment removal repaired that false positive, and real graph-edge/API
export regressions now guard the scanner. It still does not parse interpolated
Kotlin expressions or prove full type closure. A public notes refresh flag that
could bypass the busy barrier was removed during review; source guard added,
actual Android ViewModel concurrency remains unrun.

Picker callbacks now recheck the same unavailable-module policy as the UI.
Failed new-folder listing no longer retains old-folder rows under a new breadcrumb.
Legacy trash corruption no longer fabricates an empty index; receipt text labels
old recorded metadata as unverified. None of this promotes SAF crash/provider gates. The native root now explicitly configures dark system bars and safe-drawing inset padding rather than assuming legacy decor fitting under target36. Real cutout, IME,200% reflow and temporary-viewport geometry behavior remain unrun; no screenshot proves these changes yet.

## J. Accounting, strongest artifact and stopping boundary

**48 included modules;0 fully specification-complete/accepted (0%);48 still need
implementation or acceptance (100%).** Ten have selected executed kernel/policy
checks, five have only pure-source compilation,31 have Android source-only rows,
one is the test harness, one lacks generated profile acceptance. Every Android
adapter is still uncompiled here, including adapters in kernel-tested modules.
These categories are not effort/completion percentages.

231 recorded requirements:171 not implemented,56 partial/unverified,3 excluded,
one superseded, **zero whole verified features**. All30 spikes remain unpassed.
Four requested dedicated modules remain absent, plus unresolved Meet/GPL receiver
placement. The complete per-row and cross-cutting gaps are in FULL-SPEC-REVIEW.md.

The strongest artifact is a native source tree with actual new production logic,
recorded counterexamples, repaired kernels and real compiler/filesystem evidence.
It is not “everything codeable is fully coded.” Room/Hilt/process isolation,
BlackTop/full external H1, commerce, fileops, Office/browser/media/vault/casting,
Together/Lens/Messages and broad UI/input/AX scope still need substantial work.

**Next highest-information test:** provision the real command-line toolchain on
the laptop, run `native_lab.py doctor`, then both-flavor `build`. Treat the first
actual compiler errors as falsifiers and repair them. Only after that run the
consented disposable-AVD suite; inspect real native screenshots and interactions
at100%/200%. The sandbox currently cannot supply those missing observations.
