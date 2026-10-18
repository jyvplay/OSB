# Sixth candidate: complete real images and companion passes; retained CSV failure investigated

Source **42f1e421c4c2cb3f453d73137a42fb86a8b52e8a**. Full main39093394877 subsequently completed **142/0/0 SUCCESS**, including both build/JVM/contracts/lint and device jobs. The separately failed focused CSV result still stands.

## Actual native visual verification, retrieved and inspected

Continuation39093394837 succeeded9/9, with its engine-intake, four actual per-frame transport jobs and dependent PNG verifier all successful. The final collector was run after completion; all four360×800 previews passed byte/dimension checks, with **22 clipped + unclipped target observations** (7 light desktop,7 silver,4 wall,4 atlas). A later locally strengthened validator also rejects omitted clipped fields and accepted these same actual observations. That parser hardening is not called another Android execution.

All four images below were individually opened and visually inspected, not synthesized:

| Actual frame | SHA-256 | Inspection (finite, not a visual quality score) |
|---|---|---|
| `r24c-tabletop.png` | `85c6812edd5907f7776792baf31939b412fbf0e49a619fb2a9576ef4d9d888c1` | Light surfaces/blue glyphs, slanted floor and separated cards; first dock labels visible, overflow at edge rather than squeezing all apps. |
| `r24c-silver.png` | `8db192308427421659b1494462d7008b2f9fb563699642885e7390b0a0970f82` | Silver/steel surfaces, rounded dock, distinct media/globe glyphs and visible labels. Long Tasks/Settings labels intentionally ellipsize; not universal text legibility. |
| `r24c-wall.png` | `a8e4ead149a21e044e86b4b28c6207abcb19f6a7b873b5f39ce3dcf88932e247` | Focused large wall, an actual two-member pile and saved-view count1; the selected surface chip is outside this horizontally scrolled slice. Not proof of every overview-wall tap. |
| `r24c-atlas.png` | `690a4907f26fc5d33c8f36508294beb7ce7ccd833ee9e6cddbf44077720cda84` | The previously absent map is plainly rendered: genuine land outlines, selected crosshair, zoom2, Places1 and6487.322km spherical distance. Overflow pan controls remain scrollable; no street/GPS/navigation claim. |

Files and complete target/provenance receipt: `r24c-native-previews/sixth-successful-workflow/`. These are explicitly bilinear-downsampled previews of native1080×2400 pixels. The original full PNGs were retained in the run's `r24c-native-frames` artifact, not manufactured from these previews. The floor's83.16% ratio excludes chrome/occlusion and is not83% of each whole image.

## Privacy runtime result and remaining diagnostic gap

Both privacy runtimes completed **19 PASS/0 FAIL/0 SKIP**: API34/39093394844 and actual API37/39093394997. This includes the test-only pre-launch readiness checks and actual asleep-before-workspace wake control, followed by the original revealed-pad sleep/wake assertions. Protection/rearm rules were not disabled and cases were not retried.

However the readiness exporter reported **NOT_OBSERVED,0 records**. Successful test assertions do not give us the missing before-state log, and the fifth run's precise startup-focus cause remains unknown. A separate workflow-only change (66c45be) explicitly enables AGP additional test output on both privacy jobs to try to retain those records. It does not change production/test sources or create a new privacy capability. Its actual result must be collected separately.

## Retained CSV counterexample (do not hide it behind the new9/9 pass)

Focused R21/39093394827 failed **7 PASS/1 FAIL/0 SKIP**. `R21CsvUiDeviceTest.actualSelectionEditInvalidatesPreviewAndSystemSaveReadsBack` reached its expected system-save step but did not observe DocumentsUI within10s. The old assertion carried no geometry/status/foreground detail, so a swallowed tap versus a launch refusal is not proved. R23/27 and appearance/9 separately succeeded.

Source inspection found a real independent UI inconsistency: editing column selection cleared the preview and disabled Save but left the old **"Preview ready" status**. The old driver's `ready()` checked only that stale text. The next patch invalidates the displayed status too and clears editor focus/hides IME when starting preview. The test requires current ready status + enabled Save + actual preview + no observed IME within the unchanged15s readiness budget, verifies complete real Save bounds/focus and sends **one real native touch pair**. It retains actual DocumentsUI selection and file readback, with richer failure details and no retry/longer picker timeout/direct export call.

This is a targeted production consistency/driver repair, not a retroactive causal explanation. It must pass fresh native focused/full gates. The original CSV data-loss/isolation/literal-export semantics remain unchanged.

## Collected workflow-only privacy follow-up

66c45be: API34/39093956416 andAPI37/39093956414 both19/0/0 SUCCESS. Explicit additional-output enabled the API34 reporter to observe19 records,1 not-ready-before (the deliberately asleep control),0 not-ready-after,1 wake and0 keyguard dismissals. API37 still returned **NOT_OBSERVED/0**, so no before-state or retrospective cause is invented. Raw annotation/result receipts are retained as `r24c-seventh-*`.
