# Second real native counterexamples and targeted repairs

Candidate **307f2d9746bc6bc32057f6da07aaa68c1fdb98d3**, continuation run39087747864: **5 PASS /4 FAIL /0 SKIP**. This time instrumentation compiled and actually executed. Failed receipts and exact stack traces are retained as `r24c-second-*`. These outcomes do not pass `r24c_evidence_gate.py`.

| Observed failure | Diagnosis / targeted change (must rerun) | Gate is not weakened |
|---|---|---|
| Native Find layout height was40.000004dp | Production top-bar Find, Control panel and safety actions now have explicit >=48dp layout minima. Parent height can grow, and Find precedes the overflowing title. | Retain >=47.99dp tolerance and additionally require >=48dp **visible clipped** bounds for each declared target. Larger invisible/unclipped bounds alone are insufficient. |
| After a single pin click, `desk-to-back` did not yet exist | Native pin uses `combinedClickable` with double-click open. Hypothesis: single-click action awaits the double-click window while the test immediately queried the next action. Test now waits <=3s for the actual resulting action; the real drag/store assertions are unchanged. No production action is injected. | A nonfunctional selection still fails. Pile selection receives the same explicit postcondition before unpile. A later successful run would support a test sequencing explanation, not a universal gesture-latency proof. |
| Player was genuinely READY with~4-second duration, but exact-text `By.text("Play")` found nothing | Platform Button text transformation/global AX-idle are inappropriate control selectors for an updating native clock. Test now verifies visible enabled button bounds and injects one native touch pair at that actual screen target. | No direct MediaPlayer.start/pause/seek, fabricated timer or View.performClick replaces the test. Real position, pause stability, native SeekBar input and background release must still pass. Exact root cause of the old selector failure was not independently traced. |
| Map distance stayedzero after `center + (45px,15px)` tap | At the emulator density this point lies **inside the centered 48dp saved pin**, which correctly reselects the original coordinate. Use the real pin as a positive control, then tap at80%/35% of the actual canvas, outside its hitbox. | Both pin selection and background selection are now checked; distance still must change by>100m. No direct cursor setter is used. |

Separate second-candidate results: retained CSV8/8, boundary/PDF27/27, appearance/input9/9 and API37 privacy19/19 passed. API34 privacy **17/19** failed two **older R23 methods**, not the new IME case: immediately after their local focus poll, required views were null (reveal-button test and exposure-report test). The old helper read a decor focus flag without the shared callback/idle barrier. It now uses the existing real `PrivacyDeviceReady.focus` callback barrier and adds actual visible-text diagnostics for an absent workspace. **No automatic restart, retrying rule, skipped case or longer timeout was added.** Whether this explains those failures is a testable hypothesis until the rerun; keep an unresolved stability warning if the causal evidence remains insufficient.

The strengthened IME case itself passed on both APIs and explicitly awaits two actual setting-change discards. Its passes do not erase the two older failures. Full source/owner inventory was refreshed after the production top-bar edit; the expected stale-inventory rejection was not a runtime failure and no changed source is accepted merely by rehashing it.

Capture code now writes the **actual PNG before target assertions**, so a future failing layout can be visually diagnosed. Failed-workflow images remain failed-workflow evidence. Wall/atlas captures and named measured target values are transported too, subject to strict chunk/hash/budget checks. No new image is drawn, synthesized or reconstructed from source.

The completed full native run39087747908 executed **137 PASS /5 FAIL /0 SKIP**. Four failures match the
continuation cases; the fifth is the **unchanged** `ShellSmokeTest.shellRendersWithShelf`: the old visible
“Fusion Shell” identity disappeared when the wallpaper was replaced. The production Business top bar now
restores that identity rather than weakening/deleting the smoke assertion. All other132 retained cases
passed, including the candidate focus regression (the separate prior-source focus counterexample was
reproduced as expected). The main build/lint/JVM/contracts job also passed; the aggregate workflow FAILED.
These facts are specific to307f2d9, not passes inherited by the next repaired source.
