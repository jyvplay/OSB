# Fourth actual candidate: map failure captured, audio/retained IME now pass

Exact source **12fbf4f04228965c181c12f40763afc3392075fc**:

- Continuation39090097807: device job **8 PASS/1 FAIL/0 SKIP**; whole workflow **CANCELLED**, not green. Independent intake call was cancelled by the standalone intake's identical concurrency group.
- Full39090097758: **141 PASS/1 FAIL/0 SKIP**, whole workflow FAILURE. Separate assemble/JVM/contracts/lint job SUCCESS.
- The one device failure in both is the atlas. Audio's actual READY/no-autoplay/play/pause/keyboard-seek/background-release case now passed; the strengthened retained real-IME AX+model case also passed. No audio fidelity or universal flake-resolution proof follows.
- Retained R21/R23/appearance8/27/9 cases and both privacy runtimes19/19 succeeded. All raw receipts are retained.

## Actual zero-sized map, not a guessed tap collision

The new diagnostic was decisive: `offline-map-canvas` had `Rect.fromLTRB(0.0,0.0,0.0,0.0)` despite the save/IME-hidden steps. The actual native image was recovered and visually inspected: `r24c-native-previews/fourth-failed-workflow/r24c-atlas.png` (SHA256`961aa4e2cccef3dde347ac90f74c68316f41b64dda9a8b503e46108e6c2c05e7`). It plainly shows controls/coordinates and no map; the window stays shortened even with no keyboard visible. This is a production layout defect, not a missing test timeout.

The old viewport effect persisted `SetViewport` whenever THIS window's Compose IME bottom was zero. A separate Dialog can own IME/focus while the underlying window's viewport is reduced. The kernel then correctly constrains normal bounds to the incorrectly admitted small persistent viewport; hiding the keyboard does not grow those normal bounds back. This is the causal mechanism being attacked next, not an assertion that every platform callback order is proved.

The next source patch:

1. Adds a pure physical-pixel `ViewportCommit` rule (focus, IME, full safe/visible native frames,1px rounding tolerance, bounded valid coordinates), exercised by6158 new positive/adversarial host checks.
2. Observes actual native visible-frame layout changes and window focus in MainActivity; only a focused/unobscured window may publish persistent viewport changes. Other geometry remains a transient `InputPresentation` fit; no delay/retry or reinitialization of saved windows.
3. Adds native before/after normal-bound equality across the actual map Dialog/IME. The map window must not silently shrink; the canvas must still be>=96dp in this portrait test regime.
4. Replaces the map's two fixed toolbar rows plus up-to130dp footer with one overflowing real toolbar. Coordinate/measurement overlays consume no canvas layout height; status/full limitations move to an explicit Details dialog. The reduced-window boundary is no longer disguised as an empty map.

Repairs have **not** inherited the141 successful cases as current-source acceptance; all affected native gates must run again.

## Screenshot inspection and transport quota

Three complete actual frames were recovered and visually read (light tabletop, silver tabletop, broken atlas), plus measured targets for four frames. Wall's metadata is real, but its PNG remained incomplete at2/8 and was not displayed. The check API returned exactly50 annotations and page2 was empty. Per-step pagination alone did not handle the job-wide quota.

Next transport uses<=32768 Base64 characters/annotation (below64KiB), at most6 chunks for each<=128KiB PNG,4 names,8 chunks/step and3 steps:<=24 image annotations, leaving19 known continuation diagnostic slots within50. The receiver remains compatible with earlier smaller-chunk receipts. Python tests round-trip the actual native silver bytes and verify resource arithmetic; those tests are not new Android captures.

Small280px nearest-neighbor previews were insufficient to judge dock-label clipping. The next capture keeps the **original full PNG in the CI artifact** and emits a360px bilinear-downsampled preview, with dimensions/filter recorded. It is still a transformation of real native pixels, never reference artwork. Dock text now explicitly uses12sp/16sp line height matching its production budget, and native tests check an actual label's full visible height/text-layout result at normal and200% font.

## Independent real engine experiment and wiring repair

Standalone39090098092 succeeded: actual39,860,285-byte Servo0.7.0 AAR matched the pin; actual ARM64 `libc++_shared.so` and `libservoshell.so` satisfy the strict16K **header** condition. No native loading/runtime/legal approval. Raw receipt is separate from the failed/cancelled app workflow.

The next workflow removes duplicate push fanout and includes the calling workflow name in the separately prefixed intake concurrency group. A configuration regression check is added. This corrects a real cancellation defect; it is not a reason to accept the failed native run.

## Host/source gate observations

An initial new-patch host invocation was correctly refused by the stale source-inventory gate, before compilation. After regenerating hashes/census (bookkeeping only), the full local host pipeline passed **172 Python methods,439839 R24C assertions and65097 retained R24 assertions**. The new6158 viewport checks are included, not a separate fabricated coverage percentage. Full native results and the captured callback/layout behavior remain the decisive next gate.
