# R24 A–J engineering record

2026-10-17. Work stays on `arena/f77cb2a6-osb`. **No full original-spec module or all-hardware/privacy acceptance.** Latest exact outcomes belong in R24-RESULTS.md once collected; intermediate checkpoints below are not a green current HEAD.

## Runtime honesty

Actually available/used: bash, Python3.11.2, git/gh, filesystem tools, fresh web searches/page retrieval; actual hash-pinned Kotlin2.2.21 compiler and old JRE17.0.9 installed by existing bootstrap, plus openpyxl/XlsxWriter reference readers. Local Java/javac/Kotlin/adb/sdkmanager were initially absent. Old JRE is host-test-only, not native Gradle JAVA_HOME. No local Android SDK/Studio, independent subagents, physical peripheral/DMA/firmware lab, new theorem-prover run or expert legal/AX reviewer. Native compilation/runtime is real remote GitHub Linux/KVM Android, not mocked Android classes or a web replica. Metadata consistency and assistant source review are not cryptographic execution attestation. API37 runtime is a separate actual experiment, never inferred from compile37.

Git checkout metadata initially pointed to7a32437 while persisted R23 files existed. Fetched only the fixed working branch and compared all1439 remote62a0b16 tracked files bytewise: no content difference. Soft-forwarded metadata without discarding any workspace file; no branch switch/root deletion. Existing R23 evidence retained.

## A — admissible objects / authority / units / contract

- Product universe: existing49 included Gradle modules,231 original requirements,36 cross-cutting obligations and30 spikes. Testable-subset census is unweighted; full acceptance still requires ORIGINAL workflows/hardware/AX/legal gates.
- Appearance: exactly2 palettes ×2 layouts ×2 magnification values. Typed IDs; nondestructive legacy-dark/default fallback; corrupt types/unknown patches cannot silently wipe settings. One observable SharedPreferences graph feeds actual Settings and Control Panel. No permission/session-consent/OS-theme/keyboard-preset change. Normal recreation persistence is not a power-loss/DataStore/atomic-import theorem.
- Window state: logical persisted bounds and stable IDs remain owned by existing deterministic reducer. Keyboard fit is a TRANSIENT presentation; no permanent shrink based only on the IME viewport. Geometry actions refused while that different presentation is active. Tiny boxes can fit a rectangle, not prove full form usability.
- Dock: ≤64 own-app entries,64dp hit slot,32dp base glyph, maximum1.25drawing scale, neighbor1.125; no hitbox shrink. All apps reachable via actual horizontal scrolling/Find. Real running indicators only for Fusion windows, not unknown third-party OS tasks. Magnification-off ⇒1.0; not a complete high-contrast/AX certificate.
- Task/Notes: real IME/window/bounds/actions and actual model/file revision. One submit touch, no semantic callback substitute/repeated tap/hidden keyboard. Original R18 saved-revision timeout remains10s. Pinned action toolbar outside changing form/status scroll is structural candidate, not historical causality proof.
- PDF: trusted parent-owned source1..2MiB; isolated worker only receives fresh regular seekable O_RDONLY FD, unlinked before transfer, no URI/network/output path authority. Native PdfRenderer in child worker, ≤1024pages, native dimensions≤14400points; raw opaque preview≤320×320pixels≤409600bytes. Parent never decodes a child PNG/JPEG. Request/page/length/hash/dimensions/cell count bound data, not honest arbitrary worker computation. Per-document serialization + immutable requestedPage prevent stale result relabeling. No full PDF/Office/LOK fidelity/edit/sign/export or anonymous-memory/no-swap/erasure claim.
- Privacy: existing no-INTERNET/no-IME compartment plus protected dynamically registered noninteractive-state receiver; real sleep/wake, no stale key and ordinary-UID spoof refusal. Trusted OS/host/AX/pointer/camera assumptions remain. Timers/broadcasts are not hard real time; no firmware/passive-logger attestation.

Success quantifier: for EACH named declared trace at an EXACT source candidate and actual declared runtime, every required case must execute PASS, noFAIL/SKIP and whole relevant workflow success. Repaired source inherits NO previous success. Mandatory current desktop9/PDF-boundary27/privacy18/full main133 plus builds/JVM/contracts/lint are declared counts, not presumed outcomes. Separate optional API37 privacy18 requires real released-image boot and actual runtime assertion. A focused pass may certify its subset while a full suite fails; never promote that to all-green/full product.

Units: dp/sp versus actual screen px distinguished; font1×/2×, body text opaque sRGB ratio≥4.5 (outline≥3), not rendered alpha/HDR/AX proof. MiB=1048576bytes. Preview pixel dimension rounding≤1px; input presentation floating comparisons tolerance.001dp in bounded host grid. File hash/protocol identity not authentication/computation honesty. APK bytes not RSS/installed/whole-system/Tails footprint. No meaningful speedup claim from old3-sample emulator measurements.

Adjacent non-substitutable problems: app chrome vs Windows/macOS/HOME/third-party compositor; encoded preference vs applied state; pointer label vs actual hover pixels; draw size vs hit target; Activity recreation vs process/power durability; accessibility action ACK vs actual scrolled visibility; renderer commit vs compositor presentation; offline APK vs global routing/amnesia; compile37 vs actual OS37 vs Rabbit GUI; bounded PDF/literal XLSX vs full Office; readonly FD vs no retained writer; ELF headers/notices vs integrated/legal/16K-runtime engine; hardware policy counts vs physical authenticity.

## B — outcome space / stopping threshold

H+: stated bounded native app controls, independent professional palettes/layouts, real IME submission and isolated PDF preview can work under the declared trust/caps. H−: an unprivileged APK cannot universally anonymize a hostile host/firmware, detect a passive logger with identical observable transcript, erase past external logs or infer honest worker results from a digest. H∂: outcome changes with OS/runtime, actual viewport/input, artifact/ABI/license/fidelity, live screenshot verifier and trusted hardware conditions.

Do not choose H+ for the whole original system. Only named scope is admitted when its actual gates pass. If any mandatory current gate fails, report it, retained evidence, strongest bounded artifact and next falsifier; no rerun-to-green or skip/threshold relaxation.

## C — frontier / imported hypotheses

[Fresh primary ledger](research/r24-primary.md): Tails warnings/virtualization, Android window/tapjacking/insets/PdfRenderer/16K support, kernel USB/Thunderbolt, Windows DMA, Apple Dock/motion, Microsoft accessibility, Gecko lifecycle, LibreOfficeKit architecture/licenses and Tor Browser distinctions.

Important imported hypotheses: seekable FD/ownership and worker-thread recommendation for PdfRenderer; no API35-only constructor assumed on min34; safeDrawing may already include IME, so no blind double padding/newer-ruler import; protected SCREEN_OFF is overall noninteractive-state event/dynamic receiver, not physical screen/host-lid attestation; USB defaults affect new admission, descriptor identity is not crypto; VBS not required for Windows Kernel DMA; strict16K ELF/header/ZIP/runtime/compat mode are different. Gecko crash/kill closes session; rolling docs are not a pinned binary/bridge. Top-level MPL/process split does not clear exact transitive assets. Generic browser+Tor lacks Tor Browser privacy/fingerprint protections.

No exhaustive frontier claim or invented prevalence dataset. Exact open interfaces: native browser compositor/permissions/IME/crash + pinned ABI/16K artifact; Office conversion/tile/edit callbacks/fidelity/fonts/macros/rights; host OS/firmware/IOMMU trust beyond app authority. [Mechanism portfolio and quantitative bounds](R24-ENGINE-PORTFOLIO.md).

## D — negative space / modal shortcut detectors

1Static style mock≠native UI;2enum saved≠layout;3mirrored settings caches≠one truth;4draft loss≠appearance-only;5hover label≠drawn zoom;6visual zoom≠48dp hitbox;7light/dark≠AX/high contrast;8color-only state≠accessible names;9font arithmetic≠actual2× configuration;10snapshot inspect≠safe apply;11OS-inspired≠OS implementation;12keyboard hide/direct callback/repeated tap≠single Add repair;13larger timeout/rerun≠cause fixed;14one R18 pass≠all Task workflows;15missing/offscreen accessibility node≠absent feature;16true SHOW_ON_SCREEN ACK≠actual scroll;17'normal' target≠completed raster animation;18orphan EXIT event≠admissible mouse stream;19blank screenshot≠secure;20renderer commit≠presentation;21hash/base64 prefix≠complete native PNG;22root-relative rects≠screen coords;23one green component≠whole regression;24screen-off clearing≠RAM/host-swap erasure;25USBcount0≠safe laptop;26generic proxy≠Tor Browser/Tails;27small APK≠lighter OS;28PDF header≠renderer;29child PNG decode in parent≠parser isolation;30new page label≠new page pixels;31FD RO≠hostile-parent immutability;32UID/hash≠honest child;33ELF64/notices≠Android/16K/legal integration;34compile37≠runtime37;35API37 receipt≠API34 claim;36partial48/49≠full completion;37generated profile≠integrated speedup;38schema/source checks≠Android compile.

Concrete detectors: R24Checks actual production/consumer compilation, R24AppearanceDeviceTest raster growth/stable hitbox/motion/focus, one real native submit + file/model observations, independent colored PDF pages and fast requests, existing real FileObserver/FD/socket controls, actual screen off/spoof test, bounded PNG part count/hash/CRC/deflate before viewing, r24_evidence_gate mutations/actual failed receipts. Stale result marker is removed BEFORE compiler invocation; actual missing-compiler test confirms old success cannot survive. No such metadata parser authenticates a compromised CI runner.

## E / F — independent branches and artifacts

See portfolio for full construction/proved/exact-gap/falsifier/local-versus-original table. Independence was real: a333887 privacy18 passed while Main/UI compiler failed; pure palette/input policy compiler ran separately; real host ELF/sysfs observations did not depend on Android success; PDF27 passed while standalone UI navigation failed. These are not votes from fictional independent agents.

Artifact families: Appearance.kt/ProfessionalPalettes.kt/Theme.kt, observable SettingsGraph/AppearanceControls, DesktopChrome real vectors/hover/focus, InputPresentation + pinned Task/Notes toolbars, protected screen-state base, OwnedPdfRequest/native child/raw-pixel parent/UI, engine_intake actual ELF/JAR, host_privacy_posture actual readonly observations, causal indistinguishability/raw4K bandwidth bounds, executable no-Studio lab and candidate-specific evidence gates. Full engines remain unintegrated.

## G — structure-specific adversaries

- Unknown/corrupt preference type, mixed invalid patch, legacy aliases and stale second control; real2× density/search reachability; dirty editor preserved and original blocked Beam/full PDF cannot be authorized by appearance/preview consent.
- Residual pointer/focus history, normal target before120ms animation complete, edge clipping and incorrect physical-coordinate ROI; actual mouse-source movement/focus-only enlargement and disabled zoom, not an artificial state setter.
- Form target excluded from external AA tree, ACK without movement, IME reflow/status-text moving buttons; actual visible-body swipes and pinned save/New, no extra submit tap. Historical causality still separate.
- Hostile PDF invalid bytes/size/page/dimensions/raw array length/source digest, memory carrier/writable FD; fresh recovery, isolated UID and real kernel boundaries. Parent raw pixel output avoids re-entering a compressed image parser. Rapid Prev/Next/Prev catches stale current-page relabeling.
- Spoofed protected broadcast, old button/reveal after real sleep/wake; no OS power/receiver callback fabrication.
- ZIP central directory/header bounds, actual non-Android ELF strict16K failure, wrapper with no native engine, legal/integration/runtime flags remain FALSE. Firmware/host can preserve app-visible transcript, outside attestation authority.

## H / I — actual verification / repair history

- Baseline R23 latest main121/1 remained failure, not inherited; fetched/verified existing metadata only.
- Firsta333887: host policy compilation passed but actual Main/UI compiler rejected4 uses of Stroke.strokeWidth (actual API property width).0 UI/native Main cases. Actual privacy39066971115:18/0/0, whole success.
- cf898df: UI39068286325:5/2 (four appearance cases + original R18 pass; standalone2 fail at inherited dialog tag). PDF/boundary39068286331:27/0 and privacy39068286334:18/0. Full39068286301:126/5, with additional Notes dialog, settings scroll and raster baseline failures. No full acceptance.
- e30: UI5/2 progressed through dialog but title control could not be shown. CSV7/1 dense lease 'Worker died'; no parser/compiler cause inferred or longer watchdog. Parent timing diagnostics added while original5s/8s limits retained.
- c0839e1: UI5/4: four other appearance/2× cases + original R18 pass, orphan EXIT fixture +3 absent-node navigators fail before submit. PDF27/0/privacy18/0/CSV8/0 independently pass. Offscreen AA descendants require actual-body scroll; mouse fixture now uses admissible movement, non-dock focus and actual base raster.
- Actual PNGs first truncated4096 characters; collector refused bad base64 instead of guessing padding. Then observed50 annotations/job cap: two complete Business PNGs independently checksum/CRC validated; Art partial never displayed. Next mirror uses two smaller previews; all4 actual files remain artifact. Native image examination is assistant visual review, not expert AX certification.
- Metadata hash/source inventory intentionally rejected stale new pairings; reviewed regeneration, failure logs preserved. Source/Python checks alone never override compiler/device data. Final receipts/results are authoritative when collected.

All repaired candidates rerun affected/whole gates and add targeted attacks; no automatic test retries, test deletion, fake worker, generic external app handoff as full engine, or unsupported-SDK suppression introduced.

## J — remaining gap / next highest-information test

Do not stop merely at difficult interfaces: new code, actual compiler/data, renderer and pointer/lifecycle experiments were produced. Finite budget still cannot establish all49 original modules. Latest exact final outcome/remaining failure must be copied from collected receipts, never assumed from expected9/27/18/133.

If standalone input still fails, capture actual visible scroller/node actions/rect/IME and pre-tap text BEFORE changing production submission logic; old R18 already passed at focused snapshots. If worker dies, use new parent elapsed ages/op to distinguish preparation/lifetime from crash/OOM without relaxing limits. If API37 image or verifier absent, mark no runtime37 acceptance. Browser/Office need actual pinned binaries/legal/fidelity/compositor interfaces, hardware privacy needs host/physical authority. No all-A-grade/release, Tails equivalence, all-hardware closure or original-spec completion is asserted.
