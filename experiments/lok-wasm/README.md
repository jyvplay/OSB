# Actual LOK/WASM Writer experiment — not the Android product

This original Apache-2.0 harness loads a pinned third-party LibreOfficeKit WebAssembly build in a real sandboxed Chromium worker. It is an **independent architecture experiment**, not native Android Office integration, a replica of all Writer/Calc/Impress features, or a service for user documents.

## Declared candidate / authority

Publisher `RichardPo/libreoffice-wasm-build`, release `lokjs-writer-libreoffice-26.2.6.3-run37271490985`, asset612017701,70,940,538 compressed bytes; SHA256`591c9b099587bb6bea8d92e2cf73f93395f655e1656800cc8689ab740d229c1d`. Exact C wrapper/link-script source blobs were read and recorded in `android/docs/research/r25-lok-wasm-source.json`. The publisher's newer26.8 artifact is **not selected blindly**: its README documents an unipoll main-loop return/SolarMutex failure. That is a source-described condition, not independently reproduced here until an actual experiment says so.

The wrapper C is MPL2.0; build scripts are MIT; LibreOffice and its dependencies have their own licenses. A top-level JS MIT label does not clear the whole payload. The engine is downloaded only into a disposable runner temporary directory, not vendored/distributed in OSB or uploaded as an APK dependency. Publisher/transport/build provenance remain trusted assumptions; a checksum is not source reproducibility or expert license clearance.

## Concrete experiment / success predicate

1. Hash/size-check the exact archive before bounded extraction; no links, traversal or unexpected members.
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
