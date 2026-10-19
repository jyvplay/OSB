# R25 results — operational subsets, not a completed/A-grade product

Working branch `arena/a8ac5475-osb`; native app is `android/`, not the root web prototype. Evaluation date2026-10-18. [Contract/attacks/alternatives](R25-PLAN.md) · [Privacy PRD](R25-PRIVACY-PRD.md) · [PNG contract](R25-PNG-CONTRACT.md) · [Build/laptop instructions](BUILD.md).

## Completion by the requested testable-subset standard

**50/50 included modules now have substantial operational code plus a runnable behavioral check:100%.** No new module was created to inflate that denominator; the PNG work was placed in the existing image-format owner. The previously unestablished LOK module now contains a production bounded Writer/WASM adapter that actually executed with a real engine and separate consumer.

**This is not100% of the specification. Fully original-spec accepted:0/50 (0%); unfinished at full scope:50/50 (100%).** The LOK row is explicitly a **desktop/browser Writer subset**, not a bundled Android Office engine. Generic browser/Office features, all-page visual/AX/device/fidelity/performance/legal gates remain open.231 recorded requirements,36 cross-cutting obligations and30 formal spikes are not promoted by these subset results.

[Per-module source/test pairings and limits](MODULE-TESTABILITY.md) · [Full-scope blockers](MODULE-COMPLETION.md).

## First finished the previous verification work

The previous latest main run had137PASS/5FAIL, despite earlier green results. R25 added eligibility-gated actual power/keyguard observations, one wake/unsecured-keyguard preparation before UI launch, and a real asleep-AVD/native-Find counterexample. No case retry, production reset hook or longer outcome deadline was substituted.

At **0d3c5a2**, [main39185124900](https://github.com/jyvplay/OSB/actions/runs/39185124900) passed **143/0/0**, both build/JVM/contracts/lint and device jobs. [Readiness39185124848](https://github.com/jyvplay/OSB/actions/runs/39185124848) passed **6/0/0**, including all five previously failing input scenarios. Retained task7, R21/8, R23/27, appearance9, desktop/media/atlas9 and privacy34/19 cohorts also passed. Suites overlap; do not sum them as distinct product coverage.

Full UI preparation retained125 candidate observations with one intentionally asleep control/one wake/no not-ready-after. This validates the prepared-AVD experiment, **not** a retrospective cause for every prior intermittent failure. Original failures and the privacy post-Clear unknown remain recorded.

## New PNG metadata-copy workflow

- Actual bounded Kotlin parser/transform in `:format:image`: CRC/framing/order, RGB/RGBA8 dimensions, exact single zlib/scanline/filter validation, metadata removal and exact retained-image-data proof.
- Separate real isolated Android service using owned read-only SharedMemory, observed Messenger sender UID, actual INET-denial check and parent structural output verification.
- Nonexported UI with explicit system source/new-file pickers, process-bounded off-UI provider lane, session invalidation and exact readback. It does not add import/export to the no-INTERNET companion.
- Captured native-page/target transport is authored and separately gated.

**Executed host:**7150 production-kernel assertions plus53 real-file/independent Pillow13 decoder cases. Raw RGB/RGBA samples and source bytes are preserved. These results are not Android UI or anonymity evidence.

**Native trail:**9eab703 focused0/3 and full143/3 failed because of an invalid isolated instance name and image-picker selection. The instance-name repair was exercised at **befb112**: isolated corruption rejection followed by a real valid copy/Android decoded-pixel comparison **passed**. The two actual image-picker cases still failed because no matching filename was found in grid text. That focused run is **1/2/0 FAILURE**; full **145/2/0 FAILURE**, with all other cases passed. The next driver uses exact native text or accessibility description, not an arbitrary item or injected callback, and is awaiting new execution.

Limits:2MiB,4096/side,4,194,304 pixels, noninterlaced RGB/RGBA8, admitted colour/transparency only. EXIF orientation/printing can change; visual identity, steganography and encoding fingerprints remain. Not an anonymous or malware-safe image. Unresponsive providers can occupy the bounded lane; new-file identity/remote retention assumes a trustworthy provider.

## Actual local video output, not service streaming

At **befb112**, [media39191792509](https://github.com/jyvplay/OSB/actions/runs/39191792509) passed **3/0/0**: retained audio playback/pause/seek/background release, URL refusal, and new actual AVC decoder/Surface progression.

The original2800-byte six-second fixture was genuinely encoded by pinned FFmpeg7.0.2 (test tool only). Actual Android PixelCopy(SurfaceView) samples were independently decoded by Pillow:

| Native frame | Real player position | Observed mean RGB |
|---|---:|---|
| Red |288ms |255,0,0 |
| Green |2082ms |2,128,2 |
| Blue |4045ms |1,0,255 |

[Actual receipt and three decoded frames](verification/r25/video-first/verified.json). Readiness/elapsed time alone did not satisfy the test. This is controlled silent local video—not audible quality, arbitrary codec/DRM/hardware performance, the full media picker path, or YouTube/Spotify/SoundCloud integration. Main-thread provider/data-source blocking remains a named media gap.

## Additional Office architecture actually executed

The original production adapter `format/lok/src/main/assets/osb-lok/OfficeSession.js` has a main-filesystem owner and a LOK-worker owner, fixed job namespaces, caps, retire/replay checks, and real document load/tile/saveAs/destroy calls. It is no longer a presence-only string, but the Android native loader remains disabled.

At **befb112**, [engine39191792340](https://github.com/jyvplay/OSB/actions/runs/39191792340) succeeded with a pinned70,940,538-byte LibreOfficeKit26.2.6.3 WASM archive. The production adapter and separately served consumer ran **10 protocol attacks/guards** plus actual source-buffer mutation, then converted an original FODT to ODT/PDF. Independent XML/pypdf readback recovered the marker; actual page/detail tiles were returned and visually inspected.

- Actual test browser: Chrome155.0.8059.39 (verification tool, not bundled app code).
- Actual WASM linear memory: **1,073,741,824 bytes**; this is not lightweight RSS or a Tails comparison.
- One PDF page; actual document12808×16408twips; overview and96dpi detail tiles256×320.
- Real engine images and source hashes: [verified receipt](verification/r25/lok-production-first/verified.json), [overview](verification/r25/lok-production-first/tile.png), [detail](verification/r25/lok-production-first/detail.png).

No Android engine payload, generic Writer/Calc/Impress/edit fidelity, malicious-document safety, profile isolation, reproducible upstream build or full transitive license acceptance follows. The engine binary is not vendored into OSB. An additional actual Android WebView shared-memory/COOP/COEP capability probe is authored to test the next hosting boundary; it is not an assumed engine load.

The experiment retained meaningful failures: exact archive metadata refusal; native main loop initialized but worker-local FS file invisible to native calls; original full-page thumbnail too small for the declared dark-text sample threshold. It then used the verified main/worker filesystem split and retained both overview and a declared96dpi detail tile. No fake PDF, canned image or assumed engine success replaced these failures.

## Research, tools and remaining gates

Fresh Tails, Android, USBGuard, PNG and provider/engine sources: [ledger](research/r25-primary.md). Tails itself excludes hostile hardware/firmware; the app cannot certify passive keylogger absence from an identical OS transcript. USB allowlisting needs actual host authority and trusted inventory; no USB policy/firmware change was performed. Spotify's current streaming/commercial conditions are not an unrestricted free-for-all-use API; handoff remains honestly labeled.

Actually used: Python3.11.2, Node22, git/gh, Kotlin2.2.21 with an old disposable JRE17 host cache, real files/processes, Pillow13, FFmpeg7.0.2 for the original fixture, pypdf6.21.1, Playwright-core1.64.1/actual desktop Chrome on CI, and GitHub Actions maintained JDK17/SDK37/KVM Android. No independent agent, theorem prover, physical lab, Android Studio Rabbit1 GUI or deployed anonymous OS was used.

The workspace was restored with prior code/evidence but a baseline Git index and no excluded tool cache. Fetched branch metadata was reconciled without changing any of2229 recorded file hashes; the pinned host tool cache was actually reinstalled. This bookkeeping is not new runtime evidence.

Current new native contracts: full148 methods (including the newly authored WebView capability observation), readiness6, PNG3, media3, web1, continuation9, privacy19/runtime. The latest additions/repairs require their own complete collected outcomes. No all-green/A-grade/full-spec claim is made while those gates remain pending or failed.
