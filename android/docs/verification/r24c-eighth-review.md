# Eighth candidate: CSV repair passed; full/companion failures and an early frame remain

Source **7a2b4a8e19e883c6c3edb01ac740dfe37622d9b1**:

- Continuation39094764474: **9/0/0 SUCCESS**, with four per-frame jobs and dependent transport verification.
- R21/39094764520: **8/0/0 SUCCESS**, including truthful preview invalidation, measured one native Save touch, actual DocumentsUI selection and readback.
- R23/39094764448: **27/0/0 SUCCESS**; appearance39094764512: **9/0/0 SUCCESS**; additional Tasks diagnostic39094764475: **7/0/0 SUCCESS**. Overlap is not additional unique coverage.
- Full39094764437: **141/1/0 FAILURE**, separate assemble/JVM/contracts/lint job SUCCESS. The retained R18 edit/archive/recreate/query case hit a Compose `SnapshotStateObserver` multithreaded-access exception in `performTextInput` at line25. It did not fail a task persistence assertion; that does not make the workflow green.
- Privacy34/39094764472: **18/1/0 FAILURE**. Retained `actualRevealDeadlineRehidesAndNextInputMasksImmediately` found no `touch_code_output` after its clear + second6s wait (line207). Its first reveal-deadline/masking assertions had run. No discard/focus/input reason was recorded, so the cause is **unknown**.
- Actual privacy37/39094764473: **19/0/0 SUCCESS**. API34 readiness exported19 records/one intentional asleep control/one wake/all ready afterward; API37 readiness records remain unexported. Earlier green results do not erase the new API34 failure.
- Fresh full host: **177 Python tests,439839 R24C assertions,65097 retained R24 assertions**, exit0/**95.639s**. Archive `r24c/host-7a2b4a8/`.

## Visual inspection caught something the transport/semantic checks did not

All four real360×800 files passed transport hashes and were individually opened. Silver, wall and atlas display the intended states. But **`r24c-tabletop.png` shows "Loading arrangement…"**, even though later semantic checks measured the ready pin controls. This is NOT accepted as a ready-tabletop image. The real file, hash and manual failed state-alignment review are retained in `r24c-native-previews/eighth-successful-workflow/`. The directory name describes workflow conclusion, not visual acceptance.

The capture previously happened before required geometry assertions and `UiAutomation.takeScreenshot` could read an earlier compositor frame. This illustrates why valid PNG hashes, source metadata and passing later semantics do not prove the pictured state.

Next capture patch: measure required nodes first; request a genuine hardware renderer frame-commit callback; perform one native **PixelCopy(Window)**; require its success result. An older audit file is removed before a new capture, and a failing geometry assertion can still retain a diagnostic image without a passing audit. Capture API/dimensions/filter are explicit. The result is **actual application-window pixels**, not whole-display/system-bar capture. Privacy's separate screenshot-security/visible-marker proof continues using its original compositor API and is not weakened.

This follows the primary Android API contract, which explicitly distinguishes submitted frames from displayed frames and recommends frame-commit + PixelCopy for current UI content. [1](https://developer.android.com/reference/kotlin/android/view/ViewTreeObserver) [3](https://developer.android.com/reference/android/view/PixelCopy)

## R18 test-driver repair, not deletion of a regression

The retained R18 scenario keeps all edit/cancel/archive/recreate/restore/query/order/revision assertions. Only its text-entry driver changes: scroll the real field, require the exact prior native editable text, send a real Android accessibility ACTION_SET_TEXT, and require the exact resulting native text. It does not call the task model or replace persistence assertions. This avoids the specific Compose test input/measure path seen on the instrumentation thread while the real IME layout was changing. No retry or longer wait is added. Both focused and full native reruns are required.

## Privacy: collect the missing event, do not invent a fix

The next patch **does not relax** the empty-pad assertion, automatically restart, retry the test, change production guards, or extend either6s wait. It confirms the replacement view immediately after Clear, retains the retired view to detect stale writes into it, and records independent actual input-device/display/configuration/window-focus/screen callbacks around the test. If the pad disappears again, failure text includes phase, real guard UI/restart state, focus/interactive state and event history instead of a bare null pointer.

A green follow-up without a reproduced event will NOT establish the cause of this failure. A genuine observed input/focus disruption and an erroneous timer mutation are different hypotheses; they must not be conflated. Current-source full/privacy acceptance remains failed until new execution, and general stability/full specification acceptance remains unestablished regardless.
