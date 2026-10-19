# Actual LOK/WASM Writer experiment — not the Android product

This original Apache-2.0 harness loads a pinned third-party LibreOfficeKit WebAssembly build in a real sandboxed Chromium worker. It is an **independent architecture experiment**, not native Android Office integration, a replica of all Writer/Calc/Impress features, or a service for user documents.

## Declared candidate / authority

Publisher `RichardPo/libreoffice-wasm-build`, release `lokjs-writer-libreoffice-26.2.6.3-run37271490985`, asset612017701,70,940,538 compressed bytes; SHA256`591c9b099587bb6bea8d92e2cf73f93395f655e1656800cc8689ab740d229c1d`. Exact C wrapper/link-script source blobs were read and recorded in `android/docs/research/r25-lok-wasm-source.json`. The publisher's newer26.8 artifact is **not selected blindly**: its README documents an unipoll main-loop return/SolarMutex failure. That is a source-described condition, not independently reproduced here until an actual experiment says so.

The wrapper C is MPL2.0; build scripts are MIT; LibreOffice and its dependencies have their own licenses. A top-level JS MIT label does not clear the whole payload. The engine is downloaded only into a disposable runner temporary directory, not vendored/distributed in OSB or uploaded as an APK dependency. Publisher/transport/build provenance remain trusted assumptions; a checksum is not source reproducibility or expert license clearance.

## Concrete experiment / success predicate

1. Hash/size-check the exact archive before bounded extraction; no links, traversal or unexpected members. The exact upstream packaging workflow also includes README.md/LOKJS_COMMIT/LIBREOFFICE_REF; those bounded nonexecuting files are validated, not guessed away.
2. Start a local-only-content HTTP harness with COOP/COEP for the engine's real pthread/shared-memory requirements. Assets use relative/same-origin URLs. Require actual browser `crossOriginIsolated`/SharedArrayBuffer.
3. Use actual `Module.lokReady`/LOK C exports on the owning engine thread. Load an original fixed FODT canary, obtain actual document size, render one256×320 tile, save new ODT and PDF files and destroy the document.
4. Independently parse the actual ODT/XML and PDF with pinned pypdf, require the canary text, and require nonblank actual tile contrast. Only then publish `verified.json`. `tile.png` is an encoding of returned native-engine pixels, not reference artwork.

Resource envelope: pinned archive~71MB,<=512MiB expanded intake, native engine configured with1GiB linear memory (actual heap length must be reported, **not called lightweight RSS**); one document/tile,<=2MiB per result, bounded logs,180s result budget plus outer process timeout. Chrome's sandbox must remain enabled. Only the controlled fixture is admitted; no private/untrusted user input.

Network requests are restricted at the page context/CSP layer to this harness; that is **not a kernel network-isolation proof**. No GitHub token is provided to the engine execution step and checkout credentials are not persisted. Failure/timeout/missing browser leaves no accepted engine result. No automatic retry or guessed output.

## Exact gaps / cheapest next falsifiers

- Actual Android WebView/Gecko embedding and shared-memory support, lifecycle/IME/permission/storage isolation.
- Generic documents, macros/external links, fidelity and editing; malicious input and native browser/engine vulnerabilities.
- Memory/RSS/startup on a laptop/phone;1GiB configured memory is not "lighter than Tails".
- Authentic reproducible build and transitive license review.

If this controlled experiment succeeds, the next smallest test is a supported Android rendering host with the same local canary and explicit resource/permission boundaries. If it fails, preserve the actual error and do not promote `:format:lok` just because the files exist.

## Reproduction

On a disposable Linux host with maintained Node, sandbox-capable Chromium, Python/pypdf and the exact downloaded artifact:

```sh
npm ci --ignore-scripts --no-audit --no-fund
python3 intake.py /path/to/pinned.tar.gz /tmp/owned-lok/engine /tmp/owned-lok/intake.json
CHROME_PATH=/usr/bin/google-chrome timeout --kill-after=10s 210s node probe.mjs /tmp/owned-lok/engine /tmp/owned-lok/results
python3 verify.py /tmp/owned-lok/results
```

This is not executed evidence merely because the script exists. Actual outcomes belong in the R25 results ledger.


Intake repair record: runs39187294825 and39187685049 did not execute the engine. The latter exposed the actual bounded refusal of `lokjs/LOKJS_COMMIT`. The exact tagged upstream packaging workflow, blob33ff59035877d391a3313aa8b7309cf118f945d0, was then read: it adds three metadata files. Intake now admits only those exact extra names and checks wrapper commit3e08b515bca93f82eaaf5be01ee633a960fbe4da and LibreOffice26.2.6.3. The archive size/hash pin, link/path/expansion limits and no-integration flags are unchanged. This is not a runtime pass.


Runtime repair record:8b396c4/b1ee733 reached actual Chromium155.0.8059.39, cross-origin isolation, SharedArrayBuffer and native LOK state2. Loading the462-byte FODT failed with native "Unsupported URL ... type detection failed". Official zetajs convertpdf source was then read: it stages/reads FS files on the **browser main runtime**, while document calls run on the owning LOK worker. The experiment now uses that thread split without changing the FODT fixture or claiming its earlier failure passed. Independent output verification remains mandatory.


Rendering-resolution repair:642bec8 actually loaded the original FODT and produced ODT/PDF that passed independent text readback, but its256×320 whole-page tile had only1 sample below the declared dark threshold (opaque75707/light75310). No complete pass was published. The next experiment preserves that overview AND adds an explicitly declared96dpi top-left tile (15twips/pixel), after the actual saveAs layout operations; the detail must meet the original nonblank-text contrast threshold. Both raw tiles are retained and encoded as diagnostic PNGs even if the visual gate fails. No pixel values or fixture text are synthesized to satisfy the checker.


## Production-adapter verification phase

After the controlled standalone engine succeeded at baf72ab, the experiment is being switched to the original, general bounded adapter in `android/format/lok/src/main/assets/osb-lok/OfficeSession.js`, with a separately served consumer in `android/testing/src/r25js/LokWasmChecks.js`. The main-runtime adapter owns input/output namespaces, byte caps and retire/replay state; the worker adapter owns real load/render/saveAs/destroy calls. Ten protocol attacks and source-buffer mutation must run on the actual engine/virtual filesystem, followed by independent ODT/PDF/tile readback. Their exact source hashes are bound to the receipt. The fixed fixture is no longer the production API.

This phase does NOT enable an Android Office UI, bundle the70MB engine or claim general format/macro/profile/security/footprint/license acceptance. The native `LokBridge.availability()` remains explicit about that boundary. No module census credit is added until this production adapter's positive real-engine execution is collected.
