# Native module completion audit — R12 — bounded draft recovery and concrete testability; incomplete

**Included Gradle modules: 48. Specification-complete: 0 (0.00%). Still requiring completion/acceptance: 48 (100.00%).**

This is not a time/effort estimate. A module is complete only after its entire assigned scope, integration, mandatory safety/accessibility/device tests and license/build gates have reviewed acceptance. A source file, successful compilation or many assertions is insufficient.

## Common gates (build evidence is narrower than acceptance)

- android build lint and both flavors: passed
- whole assigned scope implemented: unpassed
- mandatory spike and device acceptance: unpassed
- security privacy accessibility review: unpassed
- api37 16k and reference performance: unpassed
- license sbom closure: unpassed

Native candidate2c2b5bc, run38676376263: both-flavor assembly, invoked unit/contracts, lint and APK upload passed; regular Google API34 actual39/39 named cases PASS,0 failed/skipped. Includes real remote pixels/input/resize, Notes owner conflicts and writer-process death, and Notes/Editor200%-font cases. Earlier failed candidates retained. Full parser/Office/browser, human AX, physical-device, API37/16K and legal/license acceptance are not established. Testable subsets:23/48 (47.92%), independently defined from full acceptance0/48.

## All included modules

| Module | Present source scope | Current evidence | Accepted | Exact remaining gap |
|---|---|---|---|---|
| `:app` | Native own-window host with restore/workspace actions, Find and Notes integration; prior-source focus failure reproduced and repaired source passed API34 case at1ea1faa; full UI/AX unverified | partial android execution | 0/1 (0%) | complete H1/session/process integration, BlackTop, signed commerce, API37/16K, AX and device gates |
| `:core:documents` | Strict bounded multi-encoding editor, current-source line diff, correlated copy protocol, historical recovery and bound readback receipts; executed host contracts | partial kernel contracts | 0/1 (0%) | Streaming large files, regex, full autosave/recovery, provider/IME/AX/process-death and physical persistence acceptance; complete Forge lifecycle absent |
| `:core:windowing` | Immutable published window lists, deterministic reducer, bounded tile/normal restoration, ordered epoch/sequence command stream and codec v3 with historical v1/v2 migration | partial kernel contracts | 0/1 (0%) | Full WM-01–16 semantics, Compose host/input/display integration, Room, physical a11y/perf |
| `:core:files` | Read-only SAF browsing; all eight Files/trash mutation entry points refuse via tested pure denial policy; Android adapters compiled | partial kernel contracts | 0/1 (0%) | Not the specified journaled fileops; ordered publish/verify/retire, provider crash/undo matrix S6 |
| `:core:capabilities` | Nullable runtime observations and probe source | android compiled | 0/1 (0%) | Android probe execution, OEM/API34/36/37 matrix, hardware initialization evidence |
| `:core:model` | Shared types and fail-closed pilot admission policy | partial kernel contracts | 0/1 (0%) | Complete immutable cross-module models, schema/compatibility and full product integration |
| `:core:commands` | Bounded immutable command snapshots and locale-stable literal ranking; mutation/aliasing regression tests executed | partial kernel contracts | 0/1 (0%) | Complete command routing, conflict/remap/keyboard/a11y contracts and integration |
| `:core:settings` | Canonical bounded UTF8 settings snapshot encode/decode and locale-stable registry search; SharedPreferences adapter uses catalog, native rerun pending | partial kernel contracts | 0/1 (0%) | DataStore/migrations, durable atomic application/undo, actual host-controlled settings and device/AX acceptance missing; snapshot decode does not apply anything |
| `:core:security` | Password/vault crypto source plus Android index-key adapter; decrypt/read refuses missing keys without regeneration; hardware backing not assumed | partial android execution | 0/1 (0%) | Threat model/key lifecycle/review, secure surfaces, destructive/failure-path tests and license closure |
| `:core:perf` | PSS/thermal/frame-statistics source; pure policy tested | partial kernel contracts | 0/1 (0%) | ADPF/sustained/fallback governor, measured E1+ budgets on two devices, no fabricated ratings |
| `:core:hw` | Nullable hardware inventory/probe source | android compiled | 0/1 (0%) | Verified hardware capabilities/initialization, Compat admission, reference device matrix |
| `:core:index` | Bounded literal index plus executed encrypted whole-snapshot store, strict authenticated codec, optimistic revision/file-lock publication and locked-session search; no crawler/FTS or native source integration | partial kernel contracts | 0/1 (0%) | S4 FTS/source/latency/battery decisions; explicit source consent/Vault/private-data exclusions, Android Keystore/filesystem/lifecycle integration, rollback/key lifecycle policy, native Find integration and device security acceptance |
| `:core:storage` | Checked AtomicFile plus legacy SQLite source; unsupported upgrades now refuse without DROP and failed inserts propagate | partial android execution | 0/1 (0%) | Specified Room/migrations, process-death/disk-failure/power-loss acceptance and complete recovery lifecycle |
| `:core:designsystem` | Compose theme source | android compiled | 0/1 (0%) | Complete density/focus/contrast/200%-reflow and TalkBack/Switch/Voice acceptance |
| `:feature:editor` | Native multi-draft editor/tools, non-exported identity broker, checkpoint-before-write adapter, explicit interrupted-copy review and bounded receipts R12 wrapping controls/tabs and normal/200%-font UI cases passed at2c2b5bc; full lifecycle/AX scope still unaccepted. | partial android execution | 0/1 (0%) | Native adapter/broker compiled; actual grant propagation, OEM picker/process-death/SAF/AX/IME matrix and complete editor feature set pending |
| `:feature:launcher` | Immutable pin/reorder/unpin, missing-target preservation, deterministic catalog reconciliation and locale-stable search | partial kernel contracts | 0/1 (0%) | Caller-supplied catalog only, not package discovery/Home/widgets/folders/recents or durable pins; not yet integrated with host shelf/UI or device AX acceptance |
| `:feature:find` | Actual module/window/workspace palette; dialog focus lifetime repaired and native Enter-action case passed at1ee1589 | partial android execution | 0/1 (0%) | Complete app/file/command index integration, cancellation/ranking and keyboard/performance tests |
| `:feature:files` | Read-only SAF browsing pilot and explicit mutation gate | android compiled | 0/1 (0%) | Full FM operations/journaling/undo/Peek, verified providers, accessibility and S6 |
| `:feature:browser` | Executed immutable session metadata/codec. Separate debug :app experiments passed real profiles and remote surface pixels/input/resize on regular API34 at2c2b5bc. No connected product renderer/chrome/profile/navigation integration. | partial kernel contracts | 0/1 (0%) | Complete PR07 a–s browser UI/rendering/profiles/origin/permissions/download/keyboard/reader/filter/own-cast behavior and S29/S30 |
| `:feature:terminal` | App-UID local command execution source; explicit experimental consent | android compiled | 0/1 (0%) | Isolated PTY process, terminal emulation, lifecycle/input/security and P1 acceptance |
| `:feature:vault` | Vault UI/storage source; pilot blocked | android compiled | 0/1 (0%) | Reviewed secure lifecycle, exclusion/lock/key recovery, no data-loss paths, device/security tests |
| `:feature:notes` | Bounded locked Markdown/backlinks/trash; strict local draft codec, coalesced AtomicFile checkpoints with OS-lock/expected-value conflict checks. Real corrupt/stale/recreated-owner/process-death/queue/cross-owner and200%-font native cases passed at2c2b5bc. | partial android execution | 0/1 (0%) | Every-keystroke/power-loss and full lifecycle/IME/AX acceptance; version history, attachments/OCR/import/export and full PR-02 scope |
| `:feature:calc` | Native decimal/scientific/programmer/units/history kernel, Compose and checkpoint integration | partial kernel contracts | 0/1 (0%) | PR-09 complete actual checkpoint/process-death and keyboard/TalkBack/Switch/Voice/200%-reflow acceptance |
| `:feature:settings` | Read-only diagnostics/redacted sharing; geometry density setting | android compiled | 0/1 (0%) | Complete functional setting groups/migrations, runtime verification and accessible full-density behavior |
| `:feature:media` | Strict bounded SubRip decode/canonical export, overlapping half-open cue lookup and consumed-byte-limited stream reader; native picker/inspection UI added, execution pending | partial kernel contracts | 0/1 (0%) | No native media player, synchronization, hardware/PiP/gapless/Beam; sequential-index UTF8 subset only, no styling/positioning; hostile provider wall-time unbounded |
| `:feature:beam` | Discovery/sender surface source; pure fail-closed quality policy tested; pilot blocked | partial kernel contracts | 0/1 (0%) | S17–21 transports/trust/Stop/exclusion/lock/thermal/device evidence; policy alone is not casting |
| `:feature:alerts` | Alerts UI/listener source; listener disabled | android compiled | 0/1 (0%) | Opt-in/permission/privacy/deletion/migration and NC acceptance; disabled source is not completion |
| `:feature:capture` | MediaProjection recorder source with Stop/lock/thermal and partial-file safeguards | android compiled | 0/1 (0%) | Native playback/revocation/exclusion/power-loss and hardware encoder measurement |
| `:feature:backup` | Bounded noncompressed FBC1 content serialization and whole-frame checksum/structural verification before returning entries | partial kernel contracts | 0/1 (0%) | Not encryption/authentication, not legacy ZIP/FSB1 compatibility, not filesystem restore or migration; no UI enablement, S7 remains blocked |
| `:feature:continuity` | Android explicit share/peripheral inventory source | android compiled | 0/1 (0%) | Full CT handoff/desktop/peripheral behaviors, no PC bridge claimed, device acceptance |
| `:feature:accessibility` | System accessibility observation/adaptation source | android compiled | 0/1 (0%) | Whole-product AX release gates; system state inspection does not establish usable UI |
| `:feature:together` | Explicit unavailable screen only; legacy random-peer/clock simulation and its tests removed. No room, transport or synchronized media exists. | android compiled | 0/1 (0%) | Real own-media networking/Syncplay-compatible transport, multi-device clock/media agreement S22 |
| `:feature:lens` | Camera preview only; explicit experimental consent | android compiled | 0/1 (0%) | S25–28 and LN effects/own Meet/web/PC/record paths; third-party native virtual camera is unsupported |
| `:feature:messages` | SMS repository/surface source, role/transport gaps; pilot blocked | android compiled | 0/1 (0%) | Own-Mac client, SMS role/MMS, Matrix/XMPP, delivery states, security/device/legal acceptance |
| `:feature:automation` | App-local routine source; explicit experimental consent | android compiled | 0/1 (0%) | Bounded permission-safe triggers/actions, lifecycle/cancellation/auditability and AU acceptance |
| `:format:native` | Small text/CSV helper source | jvm unit target passed | 0/1 (0%) | Strict safe bounded parsers/CSV fidelity and Forge registration; legacy UTF-8 heuristic not a validator |
| `:format:archive` | ZIP guard source | jvm unit target passed | 0/1 (0%) | Isolated full archive handling, traversal/bomb/encoding/roundtrip corpus and license closure |
| `:format:ooxml` | Executed bounded OOXML lossy text extractor with actual consumed ZIP-member/aggregate byte guards; no POI or Office equivalence | partial kernel contracts | 0/1 (0%) | Full bounded isolated Office/POI route, macros-off, fidelity/loss/safe-save and dependency audit |
| `:format:pdf` | Platform preview source; isolated UID/read-only FD/digest precursor:6/6 API34 cases passed at1ea1faa including native socket denial with reachable controls. Not a document parser boundary or Android CPU/RSS proof | partial android execution | 0/1 (0%) | Complete bounded/isolation/view/edit/annotation/safe-save/fidelity/license acceptance |
| `:format:image` | Owned ARGB crop, quarter-turn rotation and reflection with real pixel permutations; Android Bitmap adapters added, execution pending | partial kernel contracts | 0/1 (0%) | No codec/decoder security, full format/fidelity/HEIC pipeline, UI editor or safe-export acceptance; Android premultiplication/color conversions not bit fidelity |
| `:format:psd` | Bounded PSD parsing source; pilot blocked | jvm unit target passed | 0/1 (0%) | S13 corpus/layer/fidelity/isolation/roundtrip and safe-save acceptance |
| `:format:lok` | LibreOffice route/gate source; not a shipping Office engine | android compiled | 0/1 (0%) | Licensing/isolation/build dependencies, real rendering/editing/fidelity/lifecycle acceptance |
| `:cast:dlna` | UPnP/DLNA client and parser source; pilot blocked | jvm unit target passed | 0/1 (0%) | Real receiver interoperability, transport trust/lifecycle/security and S20 evidence |
| `:cast:airplay` | URL sender/SDP source compiled; no receiver tests | jvm unit target passed | 0/1 (0%) | S18 hardware and S19 counsel/hardware gates; no mirroring or FairPlay bypass claim |
| `:cast:castv2` | CastV2 protocol/client source compiled with unchecked-cast warnings | jvm unit target passed | 0/1 (0%) | S21 receiver/auth/session/Stop/exclusion/latency and protocol/security acceptance |
| `:benchmark` | Startup/baseline generator test source | android uncompiled | 0/1 (0%) | Actual device runs on at least two reference devices, reproducible budgets and reports |
| `:baselineprofile` | Intentionally empty baseline profile; no generated rules | empty profile | 0/1 (0%) | Generate from measured user journeys on devices, verify packaging/effect |
| `:testing` | 183179 host Kotlin assertions/13 separately compiled libraries;68 Python checks;733 cross-language checks;39/39 actual API34 cases at2c2b5bc. Source-bound23/48 (47.92%) testable-subset census is separate from full acceptance0/48. | runner executed | 0/1 (0%) | Complete device/provider/security/AX suites, adversarial engines, physical/reference/OEM/16K matrix and reviewed acceptance records |

The 16 `partial kernel contracts` rows have executed tests of selected production logic, not whole-module acceptance. 0 `compiled only` rows were compiled as sources but not exercised by these runners. `:testing` is the executed harness, not another completed product feature. `android compiled` means source compilation, not working assigned behavior; `partial android execution` means named subset cases executed, including any recorded failures. `jvm unit target passed` records an invoked native-CI JVM unit target, not receiver/engine/device acceptance. See R12-ENGINEERING.md for exact runtime results. No row is a full feature pass.

## Specification architecture reconciliation

The architecture-name inventory below has **47 named main-build slots**, not 48 interchangeable completed features. Of these, **41 match included names**, **2 have partial alternative owners**, and **4 have no separate included module**. 7 current included modules are additional/alternative owners. Name matching is not capability fulfillment. Calculator is required by PR-09 even though its dedicated module is additional to the named architecture list.

| Requested name | Existing candidate(s) | Mapping (not completion) |
|---|---|---|
| `:app` | `:app` | exact name |
| `:core:model` | `:core:model` | exact name |
| `:core:designsystem` | `:core:designsystem` | exact name |
| `:core:windowing` | `:core:windowing` | exact name |
| `:core:commands` | `:core:commands` | exact name |
| `:core:settings` | `:core:settings` | exact name |
| `:core:capabilities` | `:core:capabilities` | exact name |
| `:core:storage` | `:core:storage` | exact name |
| `:core:index` | `:core:index` | exact name |
| `:core:fileops` | `:core:files` | partial owner not equivalent |
| `:core:security` | `:core:security` | exact name |
| `:core:perf` | `:core:perf` | exact name |
| `:core:hw` | `:core:hw` | exact name |
| `:feature:launcher` | `:feature:launcher` | exact name |
| `:feature:find` | `:feature:find` | exact name |
| `:feature:files` | `:feature:files` | exact name |
| `:feature:alerts` | `:feature:alerts` | exact name |
| `:feature:quickpanel` | — | absent |
| `:feature:terminal` | `:feature:terminal` | exact name |
| `:feature:editor` | `:feature:editor` | exact name |
| `:feature:office` | — | absent |
| `:feature:notes` | `:feature:notes` | exact name |
| `:feature:browser` | `:feature:browser` | exact name |
| `:feature:capture` | `:feature:capture` | exact name |
| `:feature:media` | `:feature:media` | exact name |
| `:feature:photos` | — | absent |
| `:feature:vault` | `:feature:vault` | exact name |
| `:feature:backup` | `:feature:backup` | exact name |
| `:feature:beam` | `:feature:beam` | exact name |
| `:feature:together` | `:feature:together` | exact name |
| `:feature:settings` | `:feature:settings` | exact name |
| `:feature:ime` | — | absent |
| `:feature:lens` | `:feature:lens` | exact name |
| `:feature:messages` | `:feature:messages` | exact name |
| `:format:lok` | `:format:lok` | exact name |
| `:format:poi` | `:format:ooxml` | partial owner not equivalent |
| `:format:pdf` | `:format:pdf` | exact name |
| `:format:image` | `:format:image` | exact name |
| `:format:psd` | `:format:psd` | exact name |
| `:format:archive` | `:format:archive` | exact name |
| `:format:native` | `:format:native` | exact name |
| `:cast:airplay` | `:cast:airplay` | exact name |
| `:cast:castv2` | `:cast:castv2` | exact name |
| `:cast:dlna` | `:cast:dlna` | exact name |
| `:benchmark` | `:benchmark` | exact name |
| `:baselineprofile` | `:baselineprofile` | exact name |
| `:testing` | `:testing` | exact name |

Additional/alternative current names: `:core:documents`, `:core:files`, `:feature:accessibility`, `:feature:automation`, `:feature:calc`, `:feature:continuity`, `:format:ooxml`.

**Separate obligations, not falsely added to the 48-module denominator:** own Meet is a required Lens/service-process capability (LN-20); placement in a dedicated `:feature:meet` module remains an architectural decision, and the service is unimplemented. The GPL receiver must be a separate APK and is absent. Play/pro are flavors of one `:app`, not two completed modules. Required process isolation is also not satisfied merely by creating a similarly named folder.

Sources: `settings.gradle.kts`, per-module source review, `docs/feature-register.json`, `docs/verification/r12-jvm-contracts.txt` and `docs/R12-ENGINEERING.md`. Reproduce with `python3 tools/module_audit.py --check`. Review data lives in `docs/module-audit.json`; this report is generated.
