# R26 fresh primary-source ledger — 2026-10-21

Scope: source research for concrete bounded kernels, not runtime or legal acceptance.
Search excerpts are marked; full pages are distinguished. R25 sources retain their own
retrieval/evidence boundaries. No physical lab, Studio GUI run, or independent audit is
implied by this research step.

## Tails amnesia / persistence model (PRD input for the Tails-mod policy kernel)

**Search excerpts:** Tails ("The Amnesic Incognito Live System") is a Debian-based live OS
that runs from RAM, never mounts host storage, forces all traffic through Tor at firewall
level, randomizes MAC per boot, and wipes RAM at shutdown against cold-boot recovery.
Persistence is an opt-in LUKS-encrypted partition on the same USB stick, unlocked at boot,
compartmentalized per category (documents, bookmarks, network credentials, keys);
everything else stays amnesic. Current-line releases tracked: Tails 7.8.1 (June 2026),
Debian 13 base since 7.0. [1](https://en.wikipedia.org/wiki/Tails_(operating_system))
[3](https://secure-os.org/articles/tails-os/)
[10](https://privacygear.nl/en/reviews/tails-os-review/)

**Official docs (fetched excerpts):** everything in Persistent Storage is encrypted
automatically with LUKS/dm-crypt defaults; the volume is NOT hidden (seizure reveals its
existence); per-category opt-in (Personal Data, Dotfiles, bookmarks, network, keys).
[4](https://tails.net/doc/persistent_storage/index.en.html)
[8](https://tails.net/about/index.en.html)

Consequence: the honest portable subset is a **category-granular persistence policy plus
explicit wipe receipts** (R26 `AmnesiaPolicy`), never "Tails in an APK". RAM/swap/host
erasure and Tor enforcement cannot be proven by an app-level model.

## Tor on Android (Orbot handoff design)

**Search excerpts:** Orbot (Guardian Project, GPL) routes per-app traffic through Tor via
the Android VpnService API without root, exposes SOCKS5 :9050 / HTTP :8118, bundles
Snowflake/obfs4 transports, and indicates bootstrap with its own Connected state
(Tor 0.4.9.x line, 2026 releases). [2](https://github.com/guardianproject/orbot-android)
[6](https://deepwiki.com/guardianproject/orbot-android)
[8](https://grokipedia.com/page/Orbot)

Consequence: no in-APK Tor reimplementation; explicit handoff to the installed Orbot app
plus tri-state posture observations (`TorPosture`, `OrbotHandoff`). Installed-icon
presence is not a circuit.

## Browser engines (major-browser alternatives)

**Search excerpts:** GeckoView (MPL-2.0) is Mozilla's reusable Android engine (Gecko 125+,
Firefox Android 158, Oct 2026), shipped via Mozilla Maven with Stable/Beta/Nightly
channels, multi-process content isolation, and an official WebExtension API surface;
requires minSdk 26. [2](https://wiki.mozilla.org/Mobile/GeckoView)
[14](https://en.wikipedia.org/wiki/Firefox_for_Android)
[9](https://mvnrepository.com/artifact/org.mozilla.geckoview/geckoview)
Servo (MPL-2.0, Linux Foundation Europe) reached 0.5.0 (Aug 2026): Rust engine, Android
support down to Android 10, crates.io embedding API with monthly breaking releases, a
published Android AAR (~37.9 MB arm64), and an explicit "evaluate production use
carefully" warning. [8](https://en.wikipedia.org/wiki/Servo_(software))
[10](https://www.newsbreak.com/news/4859560410067-servo-0-5-advances-rust-browser-engine-with-faster-rendering-and-broader-web-compatibility)
[2](https://newsanalysis.net/servo-02-android-browser-engine/)

Consequence: engine choice stays a gated decision (size, ABI, process model, license
closure); the engine-independent policy layer (`SafeNavigation`: canonicalization,
host allow/block, private-session flags) is the executable R26 subset.

## Office engine (productivity alternative)

**Search excerpts:** Collabora Office (MPL-2.0+, LibreOffice core, Android development
since 2012, 26.04 line Oct 2026) is the maintained open-source Android document/
spreadsheet/presentation editor with Play + F-Droid + direct-APK distribution; TDF
renewed separate LibreOffice Online work in 2026. [1](https://en.wikipedia.org/wiki/Collabora_Online)
[2](https://play.google.com/store/apps/details?id=com.collabora.libreoffice&hl=en)
[8](https://www.collaboraonline.com/collabora-office-for-android-ios-credits/)

Consequence: no Office reimplementation and no vendored engine binary; bounded local
formats (TSV worksheet import in R26) plus documented external-editor handoff remain
the honest route. The R25 LOK/WASM desktop subset stays desktop-only.

## Media playback (video/audio without service extraction)

**Search excerpts:** AndroidX Media3 (Apache-2.0) is the maintained ExoPlayer home
(1.11.0 Aug 2026; old exoplayer2 frozen at 2.19.1), with modular HLS/DASH/RTSP/smooth-
streaming artifacts and documented no-DRM-by-default playback. [2](https://github.com/androidx/media)
[9](https://liveapi.com/blog/exoplayer/) [10](https://mvnrepository.com/artifact/androidx.media3/media3-exoplayer-hls)
Prior R25 Spotify findings stand: full streaming needs eligible Premium + written
approval for commercial use; no free-for-all-use API exists.

Consequence: Media3 ExoPlayer for local files + user-supplied DIRECT http(s) media URLs
only (R26 `StreamPlayerActivity`, authored for CI); bounded playlist parsing
(`PlaylistParser` M3U/M3U8/PLS, host-executed); service links keep disclosed external
handoff. No page-to-stream extraction anywhere.

## Offline maps (mapping tool without Google services)

**Search excerpts:** MapsForge 0.30.0 (Aug 2026, LGPLv3 with §4(d)/(e) waived for app
embedding) renders OpenStreetMap vector data fully on-device (~400 KB library);
osmdroid is archived/unmaintained. [1](https://wiki.openstreetmap.org/wiki/Mapsforge)
[2](https://github.com/mapsforge/mapsforge) [6](https://github.com/osmdroid/osmdroid)

Consequence: presence-gated optional MapsForge binding (`VectorAtlasBridge`, zero new
deps, compiles without the library); retained Natural Earth atlas stays the default;
bounded GPX track import (`GpxTrack`) is the executed R26 subset. No routing/traffic/
geocoding claim.

## Sandbox (isolated-process model)

**Official docs:** `android:isolatedProcess` services run under a special UID with no
permissions of their own and SELinux `isolated_app` confinement; IPC-only
communication; Chromium layers seccomp-BPF on top; Gecko documents the same OS-level
primitive. [2](https://developer.android.com/guide/topics/manifest/service-element)
[1](https://chromium.googlesource.com/chromium/src/+/refs/tags/128.0.6613.5/docs/security/android-sandbox.md)
[7](https://firefox-source-docs.mozilla.org/mobile/android/geckoview/project/isolated-process/overview.html)

Consequence: the executable subset is the job contract (`SandboxJob`: fixed namespaces,
caps, input-bound outputs, retire/replay); UID/SELinux/FD enforcement stays a device
gate, never a model claim.

## USB/hardware-origin vectors (keylogger + rogue-device countermeasures)

**Official docs:** USBGuard authorizes by ordered rules (allow/block/reject, id match,
interface-class match, stateful conditions), implicit default block; `generate-policy`
blesses currently-connected devices (must run on a trusted inventory); daemon config
distinguishes present/inserted policy. The ArchWiki warns an unconfigured daemon blocks
all input immediately. [1](https://usbguard.github.io/documentation/rule-language)
[2](https://wiki.archlinux.org/title/USBGuard)
[9](https://usbguard.github.io/documentation/configuration)
Prior R25 Tails findings stand: altered hardware/firmware and inline passive loggers are
outside software protection; a screen/touch keyboard avoids the keyboard channel only.

Consequence: read-only USBGuard-subset policy helper (`UsbPolicy`, refuses untrusted
inventory, no enforcement from an APK) + channel-avoidance table and shuffled touch pad
(`KeypadLayout`/`InputChannel`). Detection of a passive logger is explicitly disclaimed
as impossible from identical app-visible traces.

## Visual standards (beauty budgets)

**Official/standards excerpts:** WCAG 2.2 Level AA requires 4.5:1 normal text, 3:1 large
text, 3:1 non-text components (SC 1.4.3/1.4.11); AAA raises text to 7:1/4.5:1.
[7](https://www.w3.org/TR/WCAG22/) [2](https://www.w3.org/WAI/WCAG22/quickref/)
Phi (1.618…) is used here ONLY as an optional composition proportion with tolerance, a
long-standing layout heuristic — never as an accessibility rule or a beauty proof.

Consequence: finite named budgets (`BeautyStandard`: AA math on shipped constants,
phi-proximity check, scroll-overflow dock plan, ≥80% floor dominance) plus an
independent Python recomputation and a layout-bounds gate. No whole-page beauty or
preference claim.

## Research limits

No Tor deployment, engine load, receiver test, license opinion, human-preference study,
or physical measurement occurred in this research step. Findings are inputs to bounded
tests, not completed modules.
