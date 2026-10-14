# Native module completion audit — R8 continuation — encrypted index kernel and native-lab integrity; incomplete

**Included Gradle modules: 48. Specification-complete: 0 (0.00%). Still requiring completion/acceptance: 48 (100.00%).**

This is not a time/effort estimate. A module is complete only after its entire assigned scope, integration, mandatory safety/accessibility/device tests and license/build gates have reviewed acceptance. A source file, successful compilation or many assertions is insufficient.

## Common gates (all currently unpassed)

- android build lint and both flavors: unpassed
- whole assigned scope implemented: unpassed
- mandatory spike and device acceptance: unpassed
- security privacy accessibility review: unpassed
- api37 16k and reference performance: unpassed
- license sbom closure: unpassed

## All included modules

| Module | Present source scope | Current evidence | Exact remaining gap |
|---|---|---|---|
| `:app` | Native own-window host with restore/workspace actions, Find and notes integration; API/Compose/UI execution unverified | android uncompiled | Android build/install, complete H1/session/process integration, BlackTop, signed commerce, API37/16K, AX and device gates |
| `:core:documents` | Strict text/editor kernels plus correlated copy protocol, v1→v2 recovery migration, monotonic attempts and request/destination-bound byte readback | partial kernel contracts | Android/provider/IME integration and physical persistence acceptance; regex, encodings, large-file/diff and universal Forge lifecycle remain incomplete |
| `:core:windowing` | Immutable published window lists, deterministic reducer, bounded tile/normal restoration, ordered epoch/sequence command stream and codec v3 with historical v1/v2 migration | partial kernel contracts | Full WM-01–16 semantics, Compose host/input/display integration, Room, physical a11y/perf |
| `:core:files` | Read-only SAF browsing; all eight Files/trash mutation entry points refuse via tested pure denial policy; Android adapters uncompiled | partial kernel contracts | Not the specified journaled fileops; ordered publish/verify/retire, provider crash/undo matrix S6 |
| `:core:capabilities` | Nullable runtime observations and probe source | android uncompiled | Android probe execution, OEM/API34/36/37 matrix, hardware initialization evidence |
| `:core:model` | Shared types and fail-closed pilot admission policy | partial kernel contracts | Complete immutable cross-module models, schema/compatibility and full product integration |
| `:core:commands` | Bounded immutable command snapshots and locale-stable literal ranking; mutation/aliasing regression tests executed | partial kernel contracts | Complete command routing, conflict/remap/keyboard/a11y contracts and integration |
| `:core:settings` | Settings graph source | android uncompiled | DataStore, migrations, full settings/apply semantics and persistence acceptance |
| `:core:security` | Password/vault crypto source plus Android index-key adapter; decrypt/read refuses missing keys without regeneration; hardware backing not assumed | android uncompiled | Threat model/key lifecycle/review, secure surfaces, destructive/failure-path tests and license closure |
| `:core:perf` | PSS/thermal/frame-statistics source; pure policy tested | partial kernel contracts | ADPF/sustained/fallback governor, measured E1+ budgets on two devices, no fabricated ratings |
| `:core:hw` | Nullable hardware inventory/probe source | android uncompiled | Verified hardware capabilities/initialization, Compat admission, reference device matrix |
| `:core:index` | Bounded literal index plus executed encrypted whole-snapshot store, strict authenticated codec, optimistic revision/file-lock publication and locked-session search; no crawler/FTS or native source integration | partial kernel contracts | S4 FTS/source/latency/battery decisions; explicit source consent/Vault/private-data exclusions, Android Keystore/filesystem/lifecycle integration, rollback/key lifecycle policy, native Find integration and device security acceptance |
| `:core:storage` | Checked AtomicFile plus legacy SQLite source; unsupported upgrades now refuse without DROP and failed inserts propagate | android uncompiled | Specified Room/migrations, no destructive upgrades, process-death/disk-failure acceptance; adapter uncompiled |
| `:core:designsystem` | Compose theme source | android uncompiled | Complete density/focus/contrast/200%-reflow and TalkBack/Switch/Voice acceptance |
| `:feature:editor` | Native multi-draft editor/tools, non-exported identity broker, checkpoint-before-write adapter, explicit interrupted-copy review and bounded receipts | android uncompiled | Native adapter/broker uncompiled; actual grant propagation, OEM picker/process-death/SAF/AX/IME matrix and complete editor feature set pending |
| `:feature:launcher` | Shelf/launcher UI source | android uncompiled | Full pinned/folders/recents/search/Home/widget behavior, keyboard/device acceptance |
| `:feature:find` | Native keyboard-navigable module/window/workspace command palette wired to actual actions; Android uncompiled | android uncompiled | Complete app/file/command index integration, cancellation/ranking and keyboard/performance tests |
| `:feature:files` | Read-only SAF browsing pilot and explicit mutation gate | android uncompiled | Full FM operations/journaling/undo/Peek, verified providers, accessibility and S6 |
| `:feature:browser` | Browser surface source; pilot blocked | android uncompiled | S29/S30 before engine work; process/origin/permissions/download/security/extension acceptance |
| `:feature:terminal` | App-UID local command execution source; explicit experimental consent | android uncompiled | Isolated PTY process, terminal emulation, lifecycle/input/security and P1 acceptance |
| `:feature:vault` | Vault UI/storage source; pilot blocked | android uncompiled | Reviewed secure lifecycle, exclusion/lock/key recovery, no data-loss paths, device/security tests |
| `:feature:notes` | Executed bounded UTF-8 file store, OS lock/revision checks, atomic publication/readback; native ViewModel/confirmation UI authored and unrun | partial kernel contracts | Android filesystem/IME/AX/lifecycle acceptance; process-death draft recovery, versioning/reversible delete, attachments/backlinks/OCR/export and full PR-02 scope |
| `:feature:calc` | Native decimal/scientific/programmer/units/history kernel, Compose and checkpoint integration | partial kernel contracts | PR-09 Android build, actual checkpoint/process-death and keyboard/TalkBack/Switch/Voice/200%-reflow acceptance |
| `:feature:settings` | Read-only diagnostics/redacted sharing; geometry density setting | android uncompiled | Complete functional setting groups/migrations, runtime verification and accessible full-density behavior |
| `:feature:media` | Local media library/playback source; explicit experimental consent | android uncompiled | Full codec/license/fidelity/metadata/library/device requirements and measured playback acceptance |
| `:feature:beam` | Discovery/sender surface source; pure fail-closed quality policy tested; pilot blocked | partial kernel contracts | S17–21 transports/trust/Stop/exclusion/lock/thermal/device evidence; policy alone is not casting |
| `:feature:alerts` | Alerts UI/listener source; listener disabled | android uncompiled | Opt-in/permission/privacy/deletion/migration and NC acceptance; disabled source is not completion |
| `:feature:capture` | MediaProjection recorder source with Stop/lock/thermal and partial-file safeguards | android uncompiled | Android build/playback/revocation/exclusion/power-loss and hardware encoder measurement |
| `:feature:backup` | Archive/export/restore source; pilot blocked | android uncompiled | Reviewed encryption, transactional restore/rollback, versions/large-data/crash tests and BK acceptance |
| `:feature:continuity` | Android explicit share/peripheral inventory source | android uncompiled | Full CT handoff/desktop/peripheral behaviors, no PC bridge claimed, device acceptance |
| `:feature:accessibility` | System accessibility observation/adaptation source | android uncompiled | Whole-product AX release gates; system state inspection does not establish usable UI |
| `:feature:together` | Pre-existing local room/drift model only; pilot blocked | android uncompiled | Real own-media networking/Syncplay-compatible transport, multi-device clock/media agreement S22 |
| `:feature:lens` | Camera preview only; explicit experimental consent | android uncompiled | S25–28 and LN effects/own Meet/web/PC/record paths; third-party native virtual camera is unsupported |
| `:feature:messages` | SMS repository/surface source, role/transport gaps; pilot blocked | android uncompiled | Own-Mac client, SMS role/MMS, Matrix/XMPP, delivery states, security/device/legal acceptance |
| `:feature:automation` | App-local routine source; explicit experimental consent | android uncompiled | Bounded permission-safe triggers/actions, lifecycle/cancellation/auditability and AU acceptance |
| `:format:native` | Small text/CSV helper source | compiled only | Strict safe bounded parsers/CSV fidelity and Forge registration; legacy UTF-8 heuristic not a validator |
| `:format:archive` | ZIP guard source | compiled only | Isolated full archive handling, traversal/bomb/encoding/roundtrip corpus and license closure |
| `:format:ooxml` | OOXML text extraction kernel source; no POI equivalence claim | compiled only | Full bounded isolated Office/POI route, macros-off, fidelity/loss/safe-save and dependency audit |
| `:format:pdf` | Platform PDF preview source; pilot blocked | android uncompiled | Complete bounded/isolation/view/edit/annotation/safe-save/fidelity/license acceptance |
| `:format:image` | Image operations source; pilot blocked | android uncompiled | FM/FF format registry, fidelity/loss reporting, HEIC platform gate and codec/device tests |
| `:format:psd` | Bounded PSD parsing source; pilot blocked | android uncompiled | S13 corpus/layer/fidelity/isolation/roundtrip and safe-save acceptance |
| `:format:lok` | LibreOffice route/gate source; not a shipping Office engine | android uncompiled | Licensing/isolation/build dependencies, real rendering/editing/fidelity/lifecycle acceptance |
| `:cast:dlna` | UPnP/DLNA client and parser source; pilot blocked | android uncompiled | Real receiver interoperability, transport trust/lifecycle/security and S20 evidence |
| `:cast:airplay` | URL sender/SDP source compiled; no receiver tests | compiled only | S18 hardware and S19 counsel/hardware gates; no mirroring or FairPlay bypass claim |
| `:cast:castv2` | CastV2 protocol/client source compiled with unchecked-cast warnings | compiled only | S21 receiver/auth/session/Stop/exclusion/latency and protocol/security acceptance |
| `:benchmark` | Startup/baseline generator test source | android uncompiled | Actual device runs on at least two reference devices, reproducible budgets and reports |
| `:baselineprofile` | Intentionally empty baseline profile; no generated rules | empty profile | Generate from measured user journeys on devices, verify packaging/effect |
| `:testing` | Executed twelve-library JVM contracts including notes, window restore and search/backend denials; host/source audits; Android tests authored | runner executed | Full module/device/provider/security/AX suites, CI build/lint/instrumentation and reviewed acceptance records |

The 10 `partial kernel contracts` rows have executed tests of selected production logic, not whole-module acceptance. 5 `compiled only` rows were compiled as sources but not exercised by these runners. `:testing` is the executed harness, not another completed product feature. Android adapters and UI remain uncompiled.

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

Sources: `settings.gradle.kts`, per-module source review, `docs/feature-register.json`, `docs/verification/r7-jvm-contracts.txt`. Reproduce with `python3 tools/module_audit.py --check`. Review data lives in `docs/module-audit.json`; this report is generated.
