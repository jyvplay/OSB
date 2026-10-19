# First real compiler counterexample / repair

Candidate bac8225 / continuation run39087194833 failed at `:app:compileProDebugAndroidTestKotlin`.
Production main sources had compiled; **zero native test cases executed**, no screenshot produced.
`NativeVisualEvidence` assumed automatically imported DpRect width/height extensions. Pinned Compose
reports those references unresolved. This is a test-harness compilation defect, not proof the UI works.

Repair uses explicit right−left and bottom−top Dp values. Added a patch-specific **actual 200% font**
scenario: independently measure real targets and save a named view through the UI. Host receipt mutation
tests now reject this actual failed run and missing/duplicate/skipped/wrong-source/wrong-runtime cases.
Every affected main/continuation/appearance/boundary gate must rerun; no native pass is inherited.

The earlier Kotlin host warning exposed a private data-class constructor via generated `copy()`.
`ServiceLink` is now an ordinary class with a private constructor and no copy method; the full malicious-URL
corpus reran. Host source review also caught potentially invalid MediaPlayer duration reads while
PREPARING; observation now queries the real player only in queryable states. These are source-review
findings, not falsely attributed runtime crashes. The repaired host pipeline passed433681 new and65097
retained R24 assertions /165 Python tests; still not native evidence.

Before the repair push, separate first-candidate gates completed: main build/lint/JVM/contracts job PASS;
privacy API34 and actual released API37.0 runtime each19 PASS /0 FAIL /0 SKIP. The app full/focused device
jobs did not execute cases because the shared instrumentation helper did not compile. These are retained
as `r24c-first-*` receipts, not reassigned to the repaired candidate. Also found during test review:
`performScrollTo` requires a scroll ancestor, so fixed scene pins/dialog buttons now use explicit ancestor
matching, never a swallowed assertion. The IME test now verifies rearm **before** restoring its setting,
then separately awaits and asserts the second real discard, removing an idle-vs-notification race. All
changed tests must execute again. The census remains49/50 bounded testable subsets,0/50 full acceptance.
