> Historical merged checkpoint. See [R8 continuation](R8-ENGINEERING.md) for current changes and evidence; the PNG defect recorded below has now been repaired within a declared subset.

# R8 work-in-progress pull-request checkpoint

This checkpoint follows R7 and was prepared when the owner requested a pull
request. It is not a completed R8/full-system engineering pass or pilot clearance.
**0/48 modules fully specification-complete/accepted (0%); 48 outstanding (100%).**
231 recorded requirements: 171 not implemented, 56 partial/unverified, 3 excluded,
1 superseded. No status was promoted on the strength of these test counts.

## New actual observations and repairs

- Before repair, a keyed Open replay after Close reopened the window with ID2.
  A stale-viewport Move also changed geometry. Actual output is retained in
  `verification/r8-window-before.txt`; the original consumer source and genuine
  v2-writer fixture are in `core/windowing/src/contract/resources/`.
- Added an ordered `DesktopCommandStream`: caller-supplied epoch, strictly next
  positive sequence, no wrap, explicit rejection/replay dispositions and immutable
  publication. Codec v3 stores epoch/watermark with geometry and reads historical
  v1 and v2. The native session producer is wired to it but remains Android-uncompiled.
- A retry must retain its original envelope. Re-enveloping an old action is a new
  action. Raw `WindowEngine.reduce` still does only live-window Open-key deduplication;
  it is not the stream API. Consumed payloads are not retained for equality checking.
  Checkpoint rollback loses an uncheckpointed suffix. No durable acknowledgement,
  multi-writer log, exactly-once external IO or full WM acceptance is claimed.
- Relative Move/Resize now refuse viewport dimensions differing from current state.
- Before repair, the release checker returned success with all P1 rows marked
  verified, 166 non-P1 rows unfinished and only truthy nonexistent evidence fields.
  The adversarial inputs were temporary host-parser test data, not real approvals.
- Added explicit release checks for full recorded scope, module/common/architecture
  gates, source-bound records, bounded hashed artifact files, attributed reviews,
  spike/requirement coverage and two distinct physical-device report aliases.
  Traceability validation now also runs under `python -O`. Artifact hashes are not
  reviewer authentication or proof that a physical measurement/review really occurred.
  This is a necessary structural barrier, not an independent acceptance authority.
  The complete valid-release workflow remains unexercised; there are no approvals.
- **Unrepaired:** the native lab's PNG framing parser accepted a header/IEND-only
  byte string with no IDAT/pixels. The input hex and actual parser output are in
  `verification/r8-admission-before.txt`. No fake image was saved/presented as a
  screenshot. PNG decoding/CRC/content validation still needs repair and regressions;
  even correct decoding cannot establish native provenance or human visual quality.
- Notes equal-content delete/recreate ABA remains. No changes to that contract here.

## Checks actually run on this checkpoint

- **170,728 JVM assertions:** 153,216 baseline + 2,056 calculator + 8,916 editor +
  5,388 copy + 61 notes + 45 search/gates + 1,046 window/stream assertions.
- Twelve pure production libraries separately compiled with Kotlin2.2.21/JRE17;
  two expected negative cross-module compiler attacks rejected and the positive
  consumer compiled. Three pre-existing CastV2 unchecked-cast warnings remain.
- **35 Python tests passed.** Project/source ownership checks and `git diff --check`
  passed. Normal and optimized release commands refused, as required.
- Actual logs and compiled-source hashes: `verification/r8-jvm-contracts.txt`,
  `r8-boundary-regression.txt`, `r8-pr-checks.txt`, `r8-environment.json`.
  The current generated source inventory retains its existing
  `r7-source-inventory.json` filename; it is not old-source compilation evidence.

## Unmet mandatory gates and next tests

No full JDK, SDK, Gradle distribution, ADB or emulator is available in this sandbox;
no Android build, APK, native screenshots, visual review or physical-device/legal
acceptance occurred. No independent agents or theorem prover were used. CI results
must be read from the PR checks; local evidence does not predict their result.

Substantial implementation remains: Room/process architecture, BlackTop, commerce,
journaled file operations, full Office/browser/media/vault/casting/Together/Lens/
Messages and other recorded scope. compileSdk36 remains a deviation from required37.
The original v2/v2.1 review in `FULL-SPEC-REVIEW.md` and per-module gap ledger remain
necessary; this checkpoint does not independently prove the register's transcription.

Next highest-information native test: provision the documented full toolchain,
build both flavors, repair actual compiler errors, then run the explicitly
consented disposable-emulator lab and inspect genuine captures/interactions.
Before trusting capture receipts, repair the PNG parser and test corrupt/empty,
valid, oversized and decompression-bomb inputs. Do not use a daily-driver device.
