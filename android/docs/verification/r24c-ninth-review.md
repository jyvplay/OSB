# Ninth actual candidate — correct window frames, full pass, one first-publication driver failure

Exact **3f7bcc409957b33e640d4b3218192e7679478192**:

- Full39096175193 **142/0/0 SUCCESS**, both jobs; continuation39096175448 **9/0/0 SUCCESS**, including frame matrix/verifier and real engine intake.
- R21/39096175487 **8/0/0**, R23/39096175232 **27/0/0**, appearance39096175227 **9/0/0**, privacy34/39096175326 **19/0/0** and actual privacy37/39096175265 **19/0/0**, all SUCCESS.
- Separate Tasks diagnostic39096175189 **6/1/0 FAILURE**. R18's new actual accessibility text driver queried `tasks-title` once before its native editable node was published. The error is now an absent native target, not the prior Compose SnapshotStateObserver cross-thread exception. A full-suite pass does not erase this focused failure.
- Fresh host179 Python tests,439839 R24C assertions,65097 R24 assertions, exit0/**91.906s**; complete archive `r24c/host-3f7bcc4/`.

## Actual pixels inspected

All four360×800 app-window images were retrieved with complete hashes and actually opened. Light tabletop now visibly has its pins (not Loading arrangement); silver Art, wall/pile and the real atlas also show the intended states. Native22 target-instance records passed clipped/unclipped bounds checks. `capture_api` explicitly records **PixelCopy(Window) after actual renderer frame commit**; full1080×2400 original app-window buffers remain in the artifact. This is not whole-display or screenshot-security evidence, which uses a different unchanged privacy test API.

Hashes:

- Light: `bc9ed84f978d69fd11b2e333da8f1036a6e1ebc3ab0c12e5b80f920375fc309b`
- Silver: `326b3389ccf8259604de9db705935962a64b3a097f8bd1a14064ab644695e4b2`
- Wall: `04ffbd257f419b3f59344b1c054f2ae9ed0ba8b5ac8f066a21d846ac3fc75277`
- Atlas: `87dc737347df81807216f168d647b1f6f254031fbef6071a43a106e2f00864bd`

Files/provenance/manual qualifications: `r24c-native-previews/ninth-committed-window/`. No whole-app/all-state/beauty acceptance follows from four frames.

## Privacy failure is not retroactively explained

The strengthened post-Clear test passed with its exact waits/assertions, immediate replacement-view check and retired-view invariant intact. Actual API34 trace contained four phase markers at elapsed259898,259909,265913,265943ms (observer attached, first reveal, before Clear, fresh pad confirmed), **no intervening observed external event**, and did not reach its64-event cap. That passing trace does not identify the eighth failure's cause. API37 before-state/reveal records still were not exported, despite actual19-case success. No security guard was relaxed and no missing-pad branch is treated as a pass.

## Next narrow driver correction

The focused R18 failure exposes a missing **native accessibility-publication** precondition. Next driver waits within one10s budget for the actual tagged/visible/enabled/editable node and resulting native text, with bounded root/package/editable-tag diagnostics. It sends ACTION_SET_TEXT **once**, still requires exact prior and resulting values, and keeps all original edit/cancel/archive/recreate/restore/query/order/revision assertions. No workflow/test retry or model setter is introduced; no outcome timeout is extended. New focused/full execution is required.
