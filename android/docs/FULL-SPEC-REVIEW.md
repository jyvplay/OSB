# Full recorded v2/v2.1 scope review — R11 continuation

**231 requirement records and36 cross-cutting obligations reviewed against the recorded scope; no whole feature or module accepted.**
This is a traceability/ownership/gap audit, not a claim that every line of Kotlin was semantically verified. The recorded owner register is the source of scope; this does not independently prove that its transcription includes every sentence of the original specifications.

50 included modules; 178 main Kotlin files inventoried with hashes. Internal source-package references checked against direct dependencies and transitive `api` exports. Reflection, generated code, external dependencies, public-signature type closure and Android/Gradle DSL compilation remain outside this scanner.

## Cross-cutting contract (v2.1 supersedes conflicting v2.0 clauses)

| Obligation | Actual frontier / remaining gap |
|---|---|
| Native H1 desktop, not stock desktop replacement | Partial own Compose host; external display is still a noninteractive session mirror. |
| v2.1 below-floor Compat, not hardware hard block | No floor hard block; measured Compat/Standard/Studio policy incomplete. |
| API34 minimum / compile37 / target36→37 | min34/compile37/target37 declared via actual37.0 package/AGP9.1.1/Gradle9.3.1; source inventory is not execution evidence; candidate-specific results in R23-RESULTS.md. |
| arm64 and 4K/16K native pages | No linked native/transitive APK audit or physical page-size test. |
| Immutable deterministic WindowEngine / effects / versioned restore | Published list snapshots protected, deterministic kernel tests; full effect/session integration incomplete. |
| Open idempotency / command replay | Ordered epoch/sequence stream and codec v3 tested across Close/recovery; raw Open keys still deduplicate only live windows. No durable acknowledgement or multi-writer log. |
| 48dp title / Compact240×180 / Touch320×240 | Kernel geometry and authored Compose minima; rendered/AX acceptance unrun. |
| Room restore / debounce≤500ms | AtomicFile geometry still used, not Room. No500ms device persistence bound proved. |
| Hilt / Flow / DataStore / WorkManager / KSP | Flow/coroutines partially used; requested architecture stack incomplete. |
| Isolated web/terminal/preview/format/vault/cast/IME/lens/meet processes | Required complete process/service boundaries absent; dangerous surfaces remain blocked. |
| Version catalog / R8 / benchmarks / SBOM / licenses | Catalog present; release R8, measured profiles and pinned transitive license/SBOM closure absent. |
| Mandatory S1–S30 spikes before gated capability delivery | All30 lack passes; no gate promoted by source or JVM results. |
| M1 / M2 / M3 / M4 | Only portions present; H1/commerce, Files/index, alerts/governor and terminal/input milestones unaccepted. |
| M5 / M6 / M7 / M8 | Office/browser/vault/media, own casting, multi-device/display/backup and API37 hardening unaccepted. |
| BlackTop60min / own FGS / hotplug / OEM throttling | SH-10/12 implementation and S23/reference-device evidence absent. |
| Mouse learn / controller / external-keyboard IME / confinement | Native keyboard subset only; IN/WM-16 scope incomplete. |
| Five-state thermal / ADPF→sustained→own fallback | Pure policy tested; complete measurement/governor integration absent. |
| Forge registry / macros-off / lifecycle / explicit fidelity loss | Editor bounded encoding/copy/recovery/diff kernels; OOXML container guards only. Complete registry/isolated engines/loss reports absent. |
| SAF safe-save and file-move journal | Editor Save a Copy limited protocol; Files mutations now refuse in backend. No full journal/provider acceptance. |
| Real browser z-order/extensions/security S29/S30 | Blocked; no generic WebView substituted as completed browser. |
| Own Beam / trust / secure exclusion / stop / codec/latency indicator | Blocked pending S17–21 and security/hardware review; no delegated system-cast equivalence. |
| AirPlay legal boundary / GPL receiver separate APK | No mirroring/FairPlay claim; counsel/hardware and separate receiver APK absent. |
| Together own-media Syncplay compatibility /≤40ms | Bounded real authenticated TCP exchange and conditional clock intervals passed host/OpenSSL/API34 tests. Pairing, rooms, player/Syncplay integration and multi-device S22/S24 acceptance absent. |
| Lens LN-01–15 / Meet / per-site / PC webcam / recording | Physical camera preview only; S25–28 and processed-track routes absent. |
| No native third-party Android virtual camera/mic claim | Boundary maintained; does not implement own Meet/browser/PC alternatives. |
| Own-Mac Messages / SMS role / Matrix/XMPP / RCS handoff | Transport/role scope incomplete; no no-Mac iMessage, hosted Apple relay or fake RCS API. |
| Encrypted search / source consent / secure-content exclusion | Executed bounded AES-GCM snapshot and locked-session kernel; Android keys/tests authored. No FTS/crawler/source consent integration, Vault/private-content exclusion adapter, key lifecycle or S4 acceptance. |
| Local-first / no default telemetry / opt-in Home / permission-light | Source defaults partly enforced; full privacy/security review unpassed. |
| Keyboard complete / visible focus / TalkBack/Switch/Voice /200% | Selected native interaction regressions pass on API34; no whole-product keyboard/TalkBack/Switch/Voice review. |
| Density changes dimensions, not capability set | Tile geometric impossibility explicitly disabled; full density/AX matrix unrun. |
| $19.99 + $14.99 Pro /14day / signed offline entitlement | Commerce and signed entitlement absent; no fabricated trial/ownership state. |
| Offline no feature loss/no wipe / expiry read-only Files/Find | No remote wipe path introduced; complete entitlement lifecycle absent. |
| Permissive main APK / isolated MPL / no forbidden copyleft assets | No new APK dependency added this pass; optional external host verifiers are not bundled. Full transitive/legal audit still required. |
| Two reference devices / E0 budgets / no invented measurements | API34 emulator startup/Files measurements and855 measured first-party ART rules retained. No two physical reference-device acceptance; budgets remain E0. |
| Requested47 slots vs included48 / dedicated modules | 41 exact,2 partial owners,4 absent (quickpanel/office/photos/ime); Meet/GPL ownership also unresolved. |
| Actual Android build and native visual inspection | R16 both-flavor build/tests/lint and74 API34 named cases passed, including actual bounded arithmetic in isolated UID and real authenticated sockets. R17 Tasks native results are revision-specific; full parser containment, visual/AX/physical acceptance absent. |

## Every recorded requirement

Owner candidates below identify where work belongs, not implementation completeness. An absent owner is explicit. `partial_unverified` retains the entire remaining acceptance obligation; executed kernel subcontracts do not promote the row.

| ID | Full recorded target | Status | Owner candidates | Mandatory spikes |
|---|---|---|---|---|
| SH-01 | Launcher pinned/all-apps/folders/recents/search | partial_unverified | `:app`, `:feature:launcher` | S2 |
| SH-02 | Shelf pinned/running/badges/previews/edges | partial_unverified | `:app`, `:feature:launcher` | No spike named here; integration/security/AX gates still apply |
| SH-03 | Top bar menus/status/clock/Beam indicator | partial_unverified | `:app`, `:feature:launcher` | No spike named here; integration/security/AX gates still apply |
| SH-04 | Desktop wallpaper/icons/folders/widgets/context menu/layouts | partial_unverified | `:app`, `:feature:launcher` | No spike named here; integration/security/AX gates still apply |
| SH-05 | Widget host with suspend and crash isolation | not_implemented | `:app`, `:feature:launcher` | No spike named here; integration/security/AX gates still apply |
| SH-06 | Hot corners/edge triggers/mouse gestures | not_implemented | `:app`, `:feature:launcher` | No spike named here; integration/security/AX gates still apply |
| SH-07 | Local-first onboarding/permission explainers/config/license | partial_unverified | `:app`, `:feature:launcher` | No spike named here; integration/security/AX gates still apply |
| SH-08 | Session restore after death/reboot | partial_unverified | `:app`, `:feature:launcher` | No spike named here; integration/security/AX gates still apply |
| SH-09 | Configurable mouse navigation buttons (alias IN-01 contract) | not_implemented | `:app`, `:feature:launcher` | S15 |
| SH-10 | BlackTop external compositor/phone blackout/foreground session | not_implemented | `:app`, `:feature:launcher` | S23 |
| SH-11 | Phone-as-touchpad/keyboard for own compositor | not_implemented | `:app`, `:feature:launcher` | S23 |
| SH-12 | OEM throttle detector and guidance | not_implemented | `:app`, `:feature:launcher` | S23 |
| WM-01 | Move/resize/minimize/maximize/close/z/focus/title/menu compositor | partial_unverified | `:app`, `:core:windowing` | S3 |
| WM-02 | Half/third/quarter/custom tiling/edge drag/named groups | partial_unverified | `:app`, `:core:windowing` | S3 |
| WM-03 | Named/reorderable/per-window workspaces | partial_unverified | `:app`, `:core:windowing` | No spike named here; integration/security/AX gates still apply |
| WM-04 | Overview grid/search/live own-window thumbnails | not_implemented | `:app`, `:core:windowing` | No spike named here; integration/security/AX gates still apply |
| WM-05 | App/window/workspace switcher modes | partial_unverified | `:app`, `:core:windowing` | No spike named here; integration/security/AX gates still apply |
| WM-06 | Probe-chosen S1-S4 external-app broker and display pinning | not_implemented | `:app`, `:core:windowing` | S1 |
| WM-07 | Multi-display layouts/hotplug/open-where-acted | not_implemented | `:app`, `:core:windowing` | S1 |
| WM-08 | Window rules and remembered geometry | not_implemented | `:app`, `:core:windowing` | No spike named here; integration/security/AX gates still apply |
| WM-09 | Keyboard-only move/resize/tile | partial_unverified | `:app`, `:core:windowing` | S9 |
| WM-10 | Distraction-free focus mode | not_implemented | `:app`, `:core:windowing` | No spike named here; integration/security/AX gates still apply |
| WM-11 | Always-on-top mini windows | not_implemented | `:app`, `:core:windowing` | No spike named here; integration/security/AX gates still apply |
| WM-12 | Per-app density override with limits label | not_implemented | `:app`, `:core:windowing` | S1 |
| WM-13 | Move windows across workspaces with drag/menu/keyboard | partial_unverified | `:app`, `:core:windowing` | No spike named here; integration/security/AX gates still apply |
| WM-14 | Key-to-touch mapper overlay | not_implemented | `:app`, `:core:windowing` | No spike named here; integration/security/AX gates still apply |
| WM-15 | Fusion window-to-cast render-to-Surface | not_implemented | `:app`, `:core:windowing` | S17 |
| WM-16 | Cursor confinement on external display | not_implemented | `:app`, `:core:windowing` | S1 |
| SR-01 | Palette apps/commands/windows/settings/files/contacts/events/notes/targets | partial_unverified | `:feature:find`, `:core:commands`, `:core:index` | S4 |
| SR-02 | Index health/exclusions/rebuild/churn quarantine | not_implemented | `:feature:find`, `:core:commands`, `:core:index` | S4 |
| SR-03 | Exact/fuzzy search then opt-in semantic embeddings | partial_unverified | `:feature:find`, `:core:commands`, `:core:index` | S4 |
| SR-04 | Opt-in web results below local results | not_implemented | `:feature:find`, `:core:commands`, `:core:index` | No spike named here; integration/security/AX gates still apply |
| SR-05 | In-folder scoped search | not_implemented | `:feature:find`, `:core:commands`, `:core:index` | S4 |
| SR-06 | Palette calculator/conversion/notes/focus/settings/routines/casting | not_implemented | `:feature:find`, `:core:commands`, `:core:index` | No spike named here; integration/security/AX gates still apply |
| SR-07 | Opt-in external app content via AppSearch/AppFunctions | not_implemented | `:feature:find`, `:core:commands`, `:core:index` | No spike named here; integration/security/AX gates still apply |
| SR-08 | Search coverage and missing-result diagnostics | not_implemented | `:feature:find`, `:core:commands`, `:core:index` | No spike named here; integration/security/AX gates still apply |
| FM-01 | Tabbed file browser/list/grid/column/gallery/tree/tags/favorites/F2 | partial_unverified | `:core:files`, `:feature:files`, `:core:documents` | No spike named here; integration/security/AX gates still apply |
| FM-02 | Dual pane/split and optional terminal pane | not_implemented | `:core:files`, `:feature:files`, `:core:documents` | No spike named here; integration/security/AX gates still apply |
| FM-03 | Journaled copy/move/delete/rename/batch/conflicts/undo/pause/checksums | not_implemented | `:core:files`, `:feature:files`, `:core:documents` | S6 |
| FM-04 | Isolated Forge Peek for image/PDF/text/media/archive/Office/PSD/HEIC | not_implemented | `:core:files`, `:feature:files`, `:core:documents` | No spike named here; integration/security/AX gates still apply |
| FM-05 | Archive browse/read/write/encrypted ZIP/slip-bomb guards | not_implemented | `:core:files`, `:feature:files`, `:core:documents` | No spike named here; integration/security/AX gates still apply |
| FM-06 | Concise/full/custom context menus/terminal/path/cast | not_implemented | `:core:files`, `:feature:files`, `:core:documents` | No spike named here; integration/security/AX gates still apply |
| FM-07 | Storage overview/cleanup/duplicates/large files | not_implemented | `:core:files`, `:feature:files`, `:core:documents` | No spike named here; integration/security/AX gates still apply |
| FM-08 | USB/SD/SMB/SFTP/WebDAV/FTP and reconnect diagnostics | not_implemented | `:core:files`, `:feature:files`, `:core:documents` | No spike named here; integration/security/AX gates still apply |
| FM-09 | Cloud/placeholder state badges | not_implemented | `:core:files`, `:feature:files`, `:core:documents` | No spike named here; integration/security/AX gates still apply |
| FM-10 | DocumentsProvider for network/cloud/archive | not_implemented | `:core:files`, `:feature:files`, `:core:documents` | No spike named here; integration/security/AX gates still apply |
| FM-11 | Trash with origin/date/size/retention | partial_unverified | `:core:files`, `:feature:files`, `:core:documents` | S6 |
| FM-12 | Android data/obb via Shizuku/ADB | not_implemented | `:core:files`, `:feature:files`, `:core:documents` | No spike named here; integration/security/AX gates still apply |
| FM-13 | New-file templates and context-menu New | not_implemented | `:core:files`, `:feature:files`, `:core:documents` | No spike named here; integration/security/AX gates still apply |
| FM-14 | Cross-window/app ClipData drag-drop with URI grants | not_implemented | `:core:files`, `:feature:files`, `:core:documents` | No spike named here; integration/security/AX gates still apply |
| FM-15 | Selection count/size/batch toolbar | partial_unverified | `:core:files`, `:feature:files`, `:core:documents` | No spike named here; integration/security/AX gates still apply |
| FM-16 | Quick filter bar | partial_unverified | `:core:files`, `:feature:files`, `:core:documents` | No spike named here; integration/security/AX gates still apply |
| FM-17 | Cached background folder sizes | not_implemented | `:core:files`, `:feature:files`, `:core:documents` | No spike named here; integration/security/AX gates still apply |
| FM-18 | SAF session-copy safe-save/reopen verification/Save a Copy fallback | partial_unverified | `:core:files`, `:feature:files`, `:core:documents` | No spike named here; integration/security/AX gates still apply |
| NC-01 | Grouped alerts/history/snooze/reply/rules/pin/search/batch dismiss | partial_unverified | `:feature:alerts`, `:feature:quickpanel` (absent), `:app` | No spike named here; integration/security/AX gates still apply |
| NC-02 | Focus modes by workspace/time/app/display with reason | not_implemented | `:feature:alerts`, `:feature:quickpanel` (absent), `:app` | No spike named here; integration/security/AX gates still apply |
| NC-03 | Quick panel audio/brightness/media/DND/light/battery/Beam/radio panels | not_implemented | `:feature:alerts`, `:feature:quickpanel` (absent), `:app` | No spike named here; integration/security/AX gates still apply |
| NC-04 | Notification diagnostics | not_implemented | `:feature:alerts`, `:feature:quickpanel` (absent), `:app` | No spike named here; integration/security/AX gates still apply |
| NC-05 | Observable own-operation progress/live updates | partial_unverified | `:feature:alerts`, `:feature:quickpanel` (absent), `:app` | No spike named here; integration/security/AX gates still apply |
| NC-06 | Volume/brightness/layout OSD | not_implemented | `:feature:alerts`, `:feature:quickpanel` (absent), `:app` | No spike named here; integration/security/AX gates still apply |
| ST-01 | Settings graph IDs/aliases/lock reasons/history/undo/export/import | partial_unverified | `:core:settings`, `:feature:settings`, `:core:capabilities` | No spike named here; integration/security/AX gates still apply |
| ST-02 | Curated system-settings deep links per OEM | not_implemented | `:core:settings`, `:feature:settings`, `:core:capabilities` | No spike named here; integration/security/AX gates still apply |
| ST-03 | Default-apps guide | not_implemented | `:core:settings`, `:feature:settings`, `:core:capabilities` | No spike named here; integration/security/AX gates still apply |
| ST-04 | Capabilities/fingerprint/thermal/memory/codec/redacted diagnostics | partial_unverified | `:core:settings`, `:feature:settings`, `:core:capabilities` | No spike named here; integration/security/AX gates still apply |
| ST-05 | Theme/dynamic color/accent/density/motion/font scale | partial_unverified | `:core:settings`, `:feature:settings`, `:core:capabilities` | No spike named here; integration/security/AX gates still apply |
| ST-06 | Shortcut presets/conflict detection/per-device profiles | not_implemented | `:core:settings`, `:feature:settings`, `:core:capabilities` | No spike named here; integration/security/AX gates still apply |
| ST-07 | External-display UI scale independent of system | not_implemented | `:core:settings`, `:feature:settings`, `:core:capabilities` | No spike named here; integration/security/AX gates still apply |
| ST-08 | RestrictionsManager managed configuration | not_implemented | `:core:settings`, `:feature:settings`, `:core:capabilities` | No spike named here; integration/security/AX gates still apply |
| ST-09 | Phone/desktop per-context profiles | not_implemented | `:core:settings`, `:feature:settings`, `:core:capabilities` | No spike named here; integration/security/AX gates still apply |
| AP-01 | App info hub | partial_unverified | `:feature:launcher`, `:app` | No spike named here; integration/security/AX gates still apply |
| AP-02 | Own-data uninstall assistant | not_implemented | `:feature:launcher`, `:app` | No spike named here; integration/security/AX gates still apply |
| AP-03 | Pro APK install manager | not_implemented | `:feature:launcher`, `:app` | No spike named here; integration/security/AX gates still apply |
| AP-04 | App update links/inventory | not_implemented | `:feature:launcher`, `:app` | No spike named here; integration/security/AX gates still apply |
| AP-05 | Own background/startup inventory; Shizuku for others | not_implemented | `:feature:launcher`, `:app` | No spike named here; integration/security/AX gates still apply |
| TD-01 | Terminal emulator tabs/profiles/true color/mouse/search/safe paste | not_implemented | `:feature:terminal` | S5 |
| TD-02 | Local shell plus PTY | partial_unverified | `:feature:terminal` | S5 |
| TD-03 | SSH/SFTP/USB serial | not_implemented | `:feature:terminal` | No spike named here; integration/security/AX gates still apply |
| TD-04 | Read-only 16KB-compatible Linux proot payload | not_implemented | `:feature:terminal` | S5 |
| TD-05 | Non-protected AVF VM where available | not_implemented | `:feature:terminal` | No spike named here; integration/security/AX gates still apply |
| TD-06 | Pro wireless-debugging helper | not_implemented | `:feature:terminal` | No spike named here; integration/security/AX gates still apply |
| PR-01 | Text/Markdown editor encodings/large files/autosave/diff/regex | partial_unverified | `:feature:editor`, `:feature:notes`, `:feature:calc`, `:feature:browser`, `:feature:office` (absent), `:feature:ime` (absent) | No spike named here; integration/security/AX gates still apply |
| PR-02 | Local Markdown notes/attachments/backlinks/OCR/export | partial_unverified | `:feature:editor`, `:feature:notes`, `:feature:calc`, `:feature:browser`, `:feature:office` (absent), `:feature:ime` (absent) | No spike named here; integration/security/AX gates still apply |
| PR-03 | Tasks/reminders without exact alarms | not_implemented | `:feature:editor`, `:feature:notes`, `:feature:calc`, `:feature:browser`, `:feature:office` (absent), `:feature:ime` (absent) | No spike named here; integration/security/AX gates still apply |
| PR-04 | CalendarContract calendar | not_implemented | `:feature:editor`, `:feature:notes`, `:feature:calc`, `:feature:browser`, `:feature:office` (absent), `:feature:ime` (absent) | No spike named here; integration/security/AX gates still apply |
| PR-05 | ContactsContract/API37 picker | not_implemented | `:feature:editor`, `:feature:notes`, `:feature:calc`, `:feature:browser`, `:feature:office` (absent), `:feature:ime` (absent) | No spike named here; integration/security/AX gates still apply |
| PR-06 | Mail handoff/triage then native IMAP | not_implemented | `:feature:editor`, `:feature:notes`, `:feature:calc`, `:feature:browser`, `:feature:office` (absent), `:feature:ime` (absent) | No spike named here; integration/security/AX gates still apply |
| PR-07 | Desktop-class browser (parent requirement) | partial_unverified | `:feature:editor`, `:feature:notes`, `:feature:calc`, `:feature:browser`, `:feature:office` (absent), `:feature:ime` (absent) | S29, S30 |
| PR-07a | Visible horizontal browser tab strip | partial_unverified | `:feature:editor`, `:feature:notes`, `:feature:calc`, `:feature:browser`, `:feature:office` (absent), `:feature:ime` (absent) | No spike named here; integration/security/AX gates still apply |
| PR-07b | Vertical tab sidebar/reorder/groups/pins/search | not_implemented | `:feature:editor`, `:feature:notes`, `:feature:calc`, `:feature:browser`, `:feature:office` (absent), `:feature:ime` (absent) | No spike named here; integration/security/AX gates still apply |
| PR-07c | Named/colored/collapsible tab groups | not_implemented | `:feature:editor`, `:feature:notes`, `:feature:calc`, `:feature:browser`, `:feature:office` (absent), `:feature:ime` (absent) | No spike named here; integration/security/AX gates still apply |
| PR-07d | Horizontal split tab view with independent navigation | not_implemented | `:feature:editor`, `:feature:notes`, `:feature:calc`, `:feature:browser`, `:feature:office` (absent), `:feature:ime` (absent) | S14 |
| PR-07e | Vertical split tab view | not_implemented | `:feature:editor`, `:feature:notes`, `:feature:calc`, `:feature:browser`, `:feature:office` (absent), `:feature:ime` (absent) | S14 |
| PR-07f | Pinned browser tabs | not_implemented | `:feature:editor`, `:feature:notes`, `:feature:calc`, `:feature:browser`, `:feature:office` (absent), `:feature:ime` (absent) | No spike named here; integration/security/AX gates still apply |
| PR-07g | Sleeping tabs and restore by URL | not_implemented | `:feature:editor`, `:feature:notes`, `:feature:calc`, `:feature:browser`, `:feature:office` (absent), `:feature:ime` (absent) | S14 |
| PR-07h | Desktop UA on display/keyboard with per-site override | partial_unverified | `:feature:editor`, `:feature:notes`, `:feature:calc`, `:feature:browser`, `:feature:office` (absent), `:feature:ime` (absent) | No spike named here; integration/security/AX gates still apply |
| PR-07i | Full browser keyboard shortcuts | not_implemented | `:feature:editor`, `:feature:notes`, `:feature:calc`, `:feature:browser`, `:feature:office` (absent), `:feature:ime` (absent) | No spike named here; integration/security/AX gates still apply |
| PR-07j | Tab drag-out/drag-in between compositor windows | not_implemented | `:feature:editor`, `:feature:notes`, `:feature:calc`, `:feature:browser`, `:feature:office` (absent), `:feature:ime` (absent) | No spike named here; integration/security/AX gates still apply |
| PR-07k | Reader mode | not_implemented | `:feature:editor`, `:feature:notes`, `:feature:calc`, `:feature:browser`, `:feature:office` (absent), `:feature:ime` (absent) | No spike named here; integration/security/AX gates still apply |
| PR-07l | Per-site camera/mic/location/notification/popup/JS permissions | not_implemented | `:feature:editor`, `:feature:notes`, `:feature:calc`, `:feature:browser`, `:feature:office` (absent), `:feature:ime` (absent) | No spike named here; integration/security/AX gates still apply |
| PR-07m | Downloads integrated with Files | not_implemented | `:feature:editor`, `:feature:notes`, `:feature:calc`, `:feature:browser`, `:feature:office` (absent), `:feature:ime` (absent) | No spike named here; integration/security/AX gates still apply |
| PR-07n | Find in page | not_implemented | `:feature:editor`, `:feature:notes`, `:feature:calc`, `:feature:browser`, `:feature:office` (absent), `:feature:ime` (absent) | No spike named here; integration/security/AX gates still apply |
| PR-07o | Separate-data-directory browser profiles | not_implemented | `:feature:editor`, `:feature:notes`, `:feature:calc`, `:feature:browser`, `:feature:office` (absent), `:feature:ime` (absent) | No spike named here; integration/security/AX gates still apply |
| PR-07p | License-verified content/tracker filtering with per-site disable | not_implemented | `:feature:editor`, `:feature:notes`, `:feature:calc`, `:feature:browser`, `:feature:office` (absent), `:feature:ime` (absent) | S30 |
| PR-07q | Batch tab close/bookmark/select/group/window/send actions | not_implemented | `:feature:editor`, `:feature:notes`, `:feature:calc`, `:feature:browser`, `:feature:office` (absent), `:feature:ime` (absent) | No spike named here; integration/security/AX gates still apply |
| PR-07r | Named browser sessions restoring windows/tabs/groups/splits | not_implemented | `:feature:editor`, `:feature:notes`, `:feature:calc`, `:feature:browser`, `:feature:office` (absent), `:feature:ime` (absent) | No spike named here; integration/security/AX gates still apply |
| PR-07s | Page-media cast to Beam | not_implemented | `:feature:editor`, `:feature:notes`, `:feature:calc`, `:feature:browser`, `:feature:office` (absent), `:feature:ime` (absent) | No spike named here; integration/security/AX gates still apply |
| PR-08 | PDF/image viewer annotations/signing | not_implemented | `:feature:editor`, `:feature:notes`, `:feature:calc`, `:feature:browser`, `:feature:office` (absent), `:feature:ime` (absent) | No spike named here; integration/security/AX gates still apply |
| PR-09 | Basic/scientific/programmer/unit calculator with history | partial_unverified | `:feature:editor`, `:feature:notes`, `:feature:calc`, `:feature:browser`, `:feature:office` (absent), `:feature:ime` (absent) | No spike named here; integration/security/AX gates still apply |
| PR-10 | Clock/timers/inexact alarms | not_implemented | `:feature:editor`, `:feature:notes`, `:feature:calc`, `:feature:browser`, `:feature:office` (absent), `:feature:ime` (absent) | No spike named here; integration/security/AX gates still apply |
| PR-11 | Native Office via Forge with optional external open | not_implemented | `:feature:editor`, `:feature:notes`, `:feature:calc`, `:feature:browser`, `:feature:office` (absent), `:feature:ime` (absent) | S10 |
| PR-12 | Optional IME global clipboard; in-module history/sensitive/expiry/remap | not_implemented | `:feature:editor`, `:feature:notes`, `:feature:calc`, `:feature:browser`, `:feature:office` (absent), `:feature:ime` (absent) | S16 |
| PR-13 | Desktop sticky notes | not_implemented | `:feature:editor`, `:feature:notes`, `:feature:calc`, `:feature:browser`, `:feature:office` (absent), `:feature:ime` (absent) | No spike named here; integration/security/AX gates still apply |
| FF-01 | LOKit Office open/render/edit/save | not_implemented | `:format:native`, `:format:lok`, `:format:ooxml`, `:format:pdf`, `:format:image`, `:format:psd`, `:format:archive`, `:core:documents` | S10 |
| FF-02 | Shaded POI headless Office read/write/convert/extract | not_implemented | `:format:native`, `:format:lok`, `:format:ooxml`, `:format:pdf`, `:format:image`, `:format:psd`, `:format:archive`, `:core:documents` | S11 |
| FF-03 | PDF render/annotate/sign/export | partial_unverified | `:format:native`, `:format:lok`, `:format:ooxml`, `:format:pdf`, `:format:image`, `:format:psd`, `:format:archive`, `:core:documents` | No spike named here; integration/security/AX gates still apply |
| FF-04 | PDF-compatible AI; non-PDF AI boundary | not_implemented | `:format:native`, `:format:lok`, `:format:ooxml`, `:format:pdf`, `:format:image`, `:format:psd`, `:format:archive`, `:core:documents` | No spike named here; integration/security/AX gates still apply |
| FF-05 | Raster open/edit/export | partial_unverified | `:format:native`, `:format:lok`, `:format:ooxml`, `:format:pdf`, `:format:image`, `:format:psd`, `:format:archive`, `:core:documents` | No spike named here; integration/security/AX gates still apply |
| FF-06 | HEIC/HEIF/AVIF decode via platform/audited fallback | partial_unverified | `:format:native`, `:format:lok`, `:format:ooxml`, `:format:pdf`, `:format:image`, `:format:psd`, `:format:archive`, `:core:documents` | S12 |
| FF-07 | Platform hardware HEIC encode/export | not_implemented | `:format:native`, `:format:lok`, `:format:ooxml`, `:format:pdf`, `:format:image`, `:format:psd`, `:format:archive`, `:core:documents` | S12 |
| FF-08 | PSD/PSB layers/composite/export | partial_unverified | `:format:native`, `:format:lok`, `:format:ooxml`, `:format:pdf`, `:format:image`, `:format:psd`, `:format:archive`, `:core:documents` | S13 |
| FF-09 | SVG render/export and text/Markdown/CSV/RTF edit; vector editor later | partial_unverified | `:format:native`, `:format:lok`, `:format:ooxml`, `:format:pdf`, `:format:image`, `:format:psd`, `:format:archive`, `:core:documents` | No spike named here; integration/security/AX gates still apply |
| FF-10 | EPUB reader | not_implemented | `:format:native`, `:format:lok`, `:format:ooxml`, `:format:pdf`, `:format:image`, `:format:psd`, `:format:archive`, `:core:documents` | No spike named here; integration/security/AX gates still apply |
| FF-11 | Save-As/export fidelity warning | not_implemented | `:format:native`, `:format:lok`, `:format:ooxml`, `:format:pdf`, `:format:image`, `:format:psd`, `:format:archive`, `:core:documents` | No spike named here; integration/security/AX gates still apply |
| FF-12 | Isolated format sandbox/watchdogs/bomb guards | not_implemented | `:format:native`, `:format:lok`, `:format:ooxml`, `:format:pdf`, `:format:image`, `:format:psd`, `:format:archive`, `:core:documents` | No spike named here; integration/security/AX gates still apply |
| FF-13 | Thumbnail/metadata service | not_implemented | `:format:native`, `:format:lok`, `:format:ooxml`, `:format:pdf`, `:format:image`, `:format:psd`, `:format:archive`, `:core:documents` | No spike named here; integration/security/AX gates still apply |
| FF-14 | formats.yaml engine/grade/boundary registry | not_implemented | `:format:native`, `:format:lok`, `:format:ooxml`, `:format:pdf`, `:format:image`, `:format:psd`, `:format:archive`, `:core:documents` | No spike named here; integration/security/AX gates still apply |
| FF-15 | Open/dirty/save/recovery/close-confirm document lifecycle | partial_unverified | `:format:native`, `:format:lok`, `:format:ooxml`, `:format:pdf`, `:format:image`, `:format:psd`, `:format:archive`, `:core:documents` | No spike named here; integration/security/AX gates still apply |
| FF-16 | Conversion/loss report before lossy save | not_implemented | `:format:native`, `:format:lok`, `:format:ooxml`, `:format:pdf`, `:format:image`, `:format:psd`, `:format:archive`, `:core:documents` | No spike named here; integration/security/AX gates still apply |
| FF-17 | Macros disabled by default | not_implemented | `:format:native`, `:format:lok`, `:format:ooxml`, `:format:pdf`, `:format:image`, `:format:psd`, `:format:archive`, `:core:documents` | No spike named here; integration/security/AX gates still apply |
| MD-01 | Capture recording/audio/annotation/blur/redact/OCR/commit-first | partial_unverified | `:feature:media`, `:feature:photos` (absent), `:feature:capture`, `:feature:lens` | S17 |
| MD-02 | Hardware media player/subtitles/PiP/gapless/Beam | partial_unverified | `:feature:media`, `:feature:photos` (absent), `:feature:capture`, `:feature:lens` | No spike named here; integration/security/AX gates still apply |
| MD-03 | Photos with non-destructive edits/HEIC | partial_unverified | `:feature:media`, `:feature:photos` (absent), `:feature:capture`, `:feature:lens` | No spike named here; integration/security/AX gates still apply |
| MD-04 | Voice recorder | not_implemented | `:feature:media`, `:feature:photos` (absent), `:feature:capture`, `:feature:lens` | No spike named here; integration/security/AX gates still apply |
| MD-05 | Camera/scan/QR hooks | not_implemented | `:feature:media`, `:feature:photos` (absent), `:feature:capture`, `:feature:lens` | No spike named here; integration/security/AX gates still apply |
| MD-06 | Audio devices panel | not_implemented | `:feature:media`, `:feature:photos` (absent), `:feature:capture`, `:feature:lens` | No spike named here; integration/security/AX gates still apply |
| MD-07 | API37 color picker | not_implemented | `:feature:media`, `:feature:photos` (absent), `:feature:capture`, `:feature:lens` | No spike named here; integration/security/AX gates still apply |
| MD-08 | Hardware video trim/export | not_implemented | `:feature:media`, `:feature:photos` (absent), `:feature:capture`, `:feature:lens` | No spike named here; integration/security/AX gates still apply |
| MD-09 | Own-module audio focus/volume dashboard | not_implemented | `:feature:media`, `:feature:photos` (absent), `:feature:capture`, `:feature:lens` | No spike named here; integration/security/AX gates still apply |
| SC-01 | Passwords/passkeys/TOTP/notes/provider/autofill/StrongBox/import/export | partial_unverified | `:core:security`, `:feature:vault` | No spike named here; integration/security/AX gates still apply |
| SC-02 | Module app lock | not_implemented | `:core:security`, `:feature:vault` | No spike named here; integration/security/AX gates still apply |
| SC-03 | Permission/special-access audit | not_implemented | `:core:security`, `:feature:vault` | No spike named here; integration/security/AX gates still apply |
| SC-04 | Pro VpnService per-app firewall/DNS filter | not_implemented | `:core:security`, `:feature:vault` | No spike named here; integration/security/AX gates still apply |
| SC-05 | Privacy dashboard | not_implemented | `:core:security`, `:feature:vault` | No spike named here; integration/security/AX gates still apply |
| SC-06 | FLAG_SECURE per window | not_implemented | `:core:security`, `:feature:vault` | No spike named here; integration/security/AX gates still apply |
| SC-07 | Tink encrypted containers | not_implemented | `:core:security`, `:feature:vault` | No spike named here; integration/security/AX gates still apply |
| SC-08 | Telemetry off/local logs/redacted export | partial_unverified | `:core:security`, `:feature:vault` | No spike named here; integration/security/AX gates still apply |
| SC-09 | Cast excludes Vault/secure surfaces; red indicator; stop on lock | not_implemented | `:core:security`, `:feature:vault` | No spike named here; integration/security/AX gates still apply |
| BK-01 | Timeline encrypted incremental backup/remote targets/restore browser | partial_unverified | `:feature:backup` | S7 |
| BK-02 | Folder sync with conflict inbox | not_implemented | `:feature:backup` | No spike named here; integration/security/AX gates still apply |
| BK-03 | Configuration export/import | not_implemented | `:feature:backup` | No spike named here; integration/security/AX gates still apply |
| BK-04 | New-device checklist | not_implemented | `:feature:backup` | No spike named here; integration/security/AX gates still apply |
| BK-05 | Backup health and monthly sampled restore drill | not_implemented | `:feature:backup` | S7 |
| BK-06 | User-held recovery keys/no default cloud escrow | not_implemented | `:feature:backup` | S7 |
| CT-01 | Share-sheet send/receive and Quick Share | partial_unverified | `:feature:continuity`, `:feature:together` | No spike named here; integration/security/AX gates still apply |
| CT-02 | Clean-room KDE-Connect-protocol PC bridge | not_implemented | `:feature:continuity`, `:feature:together` | No spike named here; integration/security/AX gates still apply |
| CT-03 | API37 Handoff/Continue-On | not_implemented | `:feature:continuity`, `:feature:together` | No spike named here; integration/security/AX gates still apply |
| CT-04 | Cast/present superseded by CA family | superseded | `:feature:continuity`, `:feature:together` | No spike named here; integration/security/AX gates still apply |
| CT-05 | RDP/VNC/SSH remote desktop clients | not_implemented | `:feature:continuity`, `:feature:together` | No spike named here; integration/security/AX gates still apply |
| CT-06 | Peripheral manager | partial_unverified | `:feature:continuity`, `:feature:together` | No spike named here; integration/security/AX gates still apply |
| CA-01 | RAOP/AirPlay2 audio sender/pairing/sync/volume/metadata | not_implemented | `:feature:beam`, `:cast:airplay`, `:cast:castv2`, `:cast:dlna`, `:feature:together` | S18 |
| CA-02 | AirPlay URL video/pairing/Range server/HW transcode/scrub | partial_unverified | `:feature:beam`, `:cast:airplay`, `:cast:castv2`, `:cast:dlna`, `:feature:together` | S18 |
| CA-03 | AirPlay photo/slideshow | not_implemented | `:feature:beam`, `:cast:airplay`, `:cast:castv2`, `:cast:dlna`, `:feature:together` | No spike named here; integration/security/AX gates still apply |
| CA-04 | Pro legal-gated AirPlay screen mirror/FairPlay client | not_implemented | `:feature:beam`, `:cast:airplay`, `:cast:castv2`, `:cast:dlna`, `:feature:together` | S19 |
| CA-05 | Beam discovery/source/route chooser/indicator/quality/latency/audio routes | partial_unverified | `:feature:beam`, `:cast:airplay`, `:cast:castv2`, `:cast:dlna`, `:feature:together` | No spike named here; integration/security/AX gates still apply |
| CA-06 | Separate GPL APK AirPlay audio receiver | not_implemented | `:feature:beam`, `:cast:airplay`, `:cast:castv2`, `:cast:dlna`, `:feature:together` | S20 |
| CA-07 | Separate GPL APK AirPlay mirroring/video receiver | not_implemented | `:feature:beam`, `:cast:airplay`, `:cast:castv2`, `:cast:dlna`, `:feature:together` | S20 |
| CA-08 | Cast SDK play/own castv2 pro; files/URLs/queue/scrub/volume | partial_unverified | `:feature:beam`, `:cast:airplay`, `:cast:castv2`, `:cast:dlna`, `:feature:together` | S21 |
| CA-09 | Custom Cast web receiver plus WebRTC screen/window mirror | not_implemented | `:feature:beam`, `:cast:airplay`, `:cast:castv2`, `:cast:dlna`, `:feature:together` | S17 |
| CA-10 | Own DLNA SSDP/AVTransport/RenderingControl sender and renderer | partial_unverified | `:feature:beam`, `:cast:airplay`, `:cast:castv2`, `:cast:dlna`, `:feature:together` | No spike named here; integration/security/AX gates still apply |
| CA-11 | Together sync/controls/chat/presence/QR; Syncplay/own-copy mode | not_implemented | `:feature:beam`, `:cast:airplay`, `:cast:castv2`, `:cast:dlna`, `:feature:together` | S22 |
| CA-12 | Miracast settings panel and active-session adaptation | not_implemented | `:feature:beam`, `:cast:airplay`, `:cast:castv2`, `:cast:dlna`, `:feature:together` | No spike named here; integration/security/AX gates still apply |
| CA-13 | Measured-link/thermal/receiver-aware cast quality ladder | partial_unverified | `:feature:beam`, `:cast:airplay`, `:cast:castv2`, `:cast:dlna`, `:feature:together` | S17 |
| CA-14 | Cast exclusions/lock-critical stop/trust/no default relay | not_implemented | `:feature:beam`, `:cast:airplay`, `:cast:castv2`, `:cast:dlna`, `:feature:together` | No spike named here; integration/security/AX gates still apply |
| CA-15 | Shared-clock multi-receiver synchronized casting | not_implemented | `:feature:beam`, `:cast:airplay`, `:cast:castv2`, `:cast:dlna`, `:feature:together` | S24 |
| AX-01 | Semantics/panes and TalkBack/Switch/Voice release gate | partial_unverified | `:feature:accessibility`, `:core:designsystem`, `:app` | S9 |
| AX-02 | Keyboard-complete shell | partial_unverified | `:feature:accessibility`, `:core:designsystem`, `:app` | S9 |
| AX-03 | Font scale/contrast/reduced motion adaptation | partial_unverified | `:feature:accessibility`, `:core:designsystem`, `:app` | No spike named here; integration/security/AX gates still apply |
| AX-04 | Pointer/touch/touchpad/focus rings | not_implemented | `:feature:accessibility`, `:core:designsystem`, `:app` | S15 |
| AX-05 | Fusion IME input remap | not_implemented | `:feature:accessibility`, `:core:designsystem`, `:app` | S16 |
| AX-06 | Voice palette input | not_implemented | `:feature:accessibility`, `:core:designsystem`, `:app` | No spike named here; integration/security/AX gates still apply |
| AX-07 | Player captions | not_implemented | `:feature:accessibility`, `:core:designsystem`, `:app` | No spike named here; integration/security/AX gates still apply |
| AX-08 | Accessibility test automation | not_implemented | `:feature:accessibility`, `:core:designsystem`, `:app` | S9 |
| AU-01 | Routines triggers/actions/dry-run/audit/per-action permissions | partial_unverified | `:feature:automation` | No spike named here; integration/security/AX gates still apply |
| AU-02 | Workspace presets | not_implemented | `:feature:automation` | No spike named here; integration/security/AX gates still apply |
| AU-03 | AppFunctions exposure | not_implemented | `:feature:automation` | No spike named here; integration/security/AX gates still apply |
| AU-04 | Opt-in local assistant/visible tool calls/memory/no idle process | not_implemented | `:feature:automation` | No spike named here; integration/security/AX gates still apply |
| AU-05 | Third-party automation Intent API | not_implemented | `:feature:automation` | No spike named here; integration/security/AX gates still apply |
| RG-01 | Five-state thermal governor for index/effects/backup/cast | partial_unverified | `:core:perf`, `:core:hw`, `:benchmark`, `:baselineprofile` | No spike named here; integration/security/AX gates still apply |
| RG-02 | ADPF then sustained-performance then own-governor fallback | not_implemented | `:core:perf`, `:core:hw`, `:benchmark`, `:baselineprofile` | No spike named here; integration/security/AX gates still apply |
| RG-03 | Interaction/idle refresh policy | not_implemented | `:core:perf`, `:core:hw`, `:benchmark`, `:baselineprofile` | No spike named here; integration/security/AX gates still apply |
| RG-04 | Own-process PSS/CPU/wakeups/IO/net/exit/memory-limiter monitor | partial_unverified | `:core:perf`, `:core:hw`, `:benchmark`, `:baselineprofile` | No spike named here; integration/security/AX gates still apply |
| RG-05 | OEM game overlay/cooler coexistence spike | not_implemented | `:core:perf`, `:core:hw`, `:benchmark`, `:baselineprofile` | No spike named here; integration/security/AX gates still apply |
| RG-06 | Charging/battery/metered-aware work | not_implemented | `:core:perf`, `:core:hw`, `:benchmark`, `:baselineprofile` | No spike named here; integration/security/AX gates still apply |
| RG-07 | Low-RAM mode | not_implemented | `:core:perf`, `:core:hw`, `:benchmark`, `:baselineprofile` | S8 |
| RG-08 | Hardware codec/NPU/Vulkan report and routing | partial_unverified | `:core:perf`, `:core:hw`, `:benchmark`, `:baselineprofile` | No spike named here; integration/security/AX gates still apply |
| CM-01 | Separate Wine/Box64 APK with user installers | not_implemented | `:app` | No spike named here; integration/security/AX gates still apply |
| CM-02 | Web-app windows | not_implemented | `:app` | No spike named here; integration/security/AX gates still apply |
| CM-03 | No macOS application execution | excluded | `:app` | No spike named here; integration/security/AX gates still apply |
| BL-01 | One-time purchase $19.99 and optional $14.99 Pro pack | not_implemented | `:app` | No spike named here; integration/security/AX gates still apply |
| BL-02 | Signed Keystore-bound offline/grace entitlement cache | not_implemented | `:app` | No spike named here; integration/security/AX gates still apply |
| BL-03 | Pro Ed25519 license keys/optional device binding/transfers | not_implemented | `:app` | No spike named here; integration/security/AX gates still apply |
| BL-04 | 14-day full trial then read-only Files/Find and nag-free state | not_implemented | `:app` | No spike named here; integration/security/AX gates still apply |
| BL-05 | Refund-safe revocation/no user-data wipe | not_implemented | `:app` | No spike named here; integration/security/AX gates still apply |
| IN-01 | Mouse press-release/scroll mapping/hover/Learn my mouse | not_implemented | `:app`, `:feature:ime` (absent), `:feature:continuity` | S15 |
| IN-07 | Game-controller pointer mode | not_implemented | `:app`, `:feature:ime` (absent), `:feature:continuity` | No spike named here; integration/security/AX gates still apply |
| IN-08 | External-keyboard IME switcher | not_implemented | `:app`, `:feature:ime` (absent), `:feature:continuity` | No spike named here; integration/security/AX gates still apply |
| LN-01 | Segmented background blur/replace/remove | not_implemented | `:feature:lens`, `:feature:meet` (absent) | S26 |
| LN-02 | Smoothed auto-frame/multi-person crop | not_implemented | `:feature:lens`, `:feature:meet` (absent) | S26 |
| LN-03 | Gaze-aware framing; eye-contact redirect research only | not_implemented | `:feature:lens`, `:feature:meet` (absent) | No spike named here; integration/security/AX gates still apply |
| LN-04 | Video denoise/low-light shaders | not_implemented | `:feature:lens`, `:feature:meet` (absent) | S26 |
| LN-05 | License-verified FSR1 upscale | not_implemented | `:feature:lens`, `:feature:meet` (absent) | No spike named here; integration/security/AX gates still apply |
| LN-06 | Studio face-normal virtual relight | not_implemented | `:feature:lens`, `:feature:meet` (absent) | S26 |
| LN-07 | WebRTC APM/RNNoise microphone noise removal | not_implemented | `:feature:lens`, `:feature:meet` (absent) | S26 |
| LN-08 | AEC3 room echo removal | not_implemented | `:feature:lens`, `:feature:meet` (absent) | S26 |
| LN-09 | Vignette/LUT/color/.cube import | not_implemented | `:feature:lens`, `:feature:meet` (absent) | No spike named here; integration/security/AX gates still apply |
| LN-10 | Hand-landmark gesture actions | not_implemented | `:feature:lens`, `:feature:meet` (absent) | No spike named here; integration/security/AX gates still apply |
| LN-11 | Procedural/parallax/user-image scenes | not_implemented | `:feature:lens`, `:feature:meet` (absent) | No spike named here; integration/security/AX gates still apply |
| LN-12 | License/RAM-gated on-device generative scenes | not_implemented | `:feature:lens`, `:feature:meet` (absent) | S28 |
| LN-13 | Presenter person/slides/screen/pointer/ink composition | not_implemented | `:feature:lens`, `:feature:meet` (absent) | S26 |
| LN-14 | Lens presets/hotkeys/per-app memory | not_implemented | `:feature:lens`, `:feature:meet` (absent) | No spike named here; integration/security/AX gates still apply |
| LN-15 | Thermal/quality ladder | not_implemented | `:feature:lens`, `:feature:meet` (absent) | S26 |
| LN-20 | Fusion Meet owned WebRTC processed tracks/screen share | not_implemented | `:feature:lens`, `:feature:meet` (absent) | S26 |
| LN-21 | Browser per-site processed camera/mic/display route | not_implemented | `:feature:lens`, `:feature:meet` (absent) | S25, S30 |
| LN-22 | PC companion webcam route | not_implemented | `:feature:lens`, `:feature:meet` (absent) | S27 |
| LN-23 | Lens recording/RTMP/SRT route | not_implemented | `:feature:lens`, `:feature:meet` (absent) | S26 |
| MS-01 | BlueBubbles-compatible client requires user-owned Mac and Apple ID | not_implemented | `:feature:messages` | No spike named here; integration/security/AX gates still apply |
| MS-02 | No on-phone no-Mac iMessage/rustpush/private FairPlay certificates | excluded | `:feature:messages` | No spike named here; integration/security/AX gates still apply |
| MS-03 | No hosted Apple-account relay sold by Fusion | excluded | `:feature:messages` | No spike named here; integration/security/AX gates still apply |
| MS-04 | RCS system-app handoff only | not_implemented | `:feature:messages` | No spike named here; integration/security/AX gates still apply |
| MS-05 | SMS/MMS with SMS role | partial_unverified | `:feature:messages` | No spike named here; integration/security/AX gates still apply |
| MS-06 | Permissive Matrix/XMPP clients; no AGPL Signal client | not_implemented | `:feature:messages` | No spike named here; integration/security/AX gates still apply |

Status counts: {"excluded": 3, "not_implemented": 168, "partial_unverified": 59, "superseded": 1}.

Reproduce: `python3 tools/native_source_audit.py --check`. Fingerprints in `docs/verification/r11-source-inventory.json` identify reviewed inputs, not compiled artifacts. See `R11-ENGINEERING.md` for mechanisms, adversaries, evidence and the next falsifying tests.
