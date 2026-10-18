# Third actual native run: improvements, counterexamples and revised diagnosis

Candidate **0a7b22e612d25959c32f4f7626594f916e664198**, continuation39088691659: **7 PASS /2 FAIL /0 SKIP**. All five tabletop cases passed, including drag/persistence, actual wall movement/pile/saved-view load, corruption refusal, 200% font interaction, and silver/light measured visible targets. This supports the targeted top-bar and single-tap sequencing repairs; it is not general gesture/AX/beauty acceptance.

Full run39088691650: **139 PASS /3 FAIL /0 SKIP**. Besides map and keyboard seek, the retained `RealImeDeviceTest` timed out at "long text entered". Build/both APKs/JVM/contracts/lint succeeded, but the whole native workflow failed. Other focused suites8/8,27/27,9/9 and privacy19/19 on both API34 andAPI37 passed. Earlier failures are not erased.

## Revised map hypothesis — do not overstate the previous diagnosis

The second-run suggestion that the45px tap hit a centered saved pin was **not established**. The stronger third test instead found **no visible `map-pin-1` node** after saved coordinates/camera center had been checked. Map pin rendering explicitly culls when canvas height is<48dp; an IME-constrained window plus fixed controls can collapse that weighted region. Thus neither a swallowed background tap nor a hit on a real pin was proved by the earlier trace.

The next patch explicitly clears focus/hides the **actual Dialog's** keyboard on successful Save (not the underlying window's controller), waits for observed IME closure, captures the actual atlas before further interactions, and requires a measured>=96dp canvas plus the real visible saved pin before the pin/background positive-control experiment. These are falsifiable runtime checks, not direct coordinate/camera setters or an inferred visual pass. If the viewport is still absent, its image/bounds must be retained instead of guessing again.

## Keyboard seek staging

The third player test genuinely played and paused the PCM file, then timed out waiting for keyboard seek: actual observation `PAUSED`, position373ms, duration4000ms, real player present. The former `requestFocus()` return was ignored while still in touch mode. The next test sends a real Tab to enter keyboard navigation, asserts `isInTouchMode == false`, asserts successful SeekBar focus, and then sends a real DPAD key. Same real-position predicate/deadline, no MediaPlayer setter or fake timer. Physical audible sound and video frames remain untested.

## Retained real-IME regression

The older test searched for any visible editable node, including after a separate IME window was shown, and did not refresh AX nodes or independently inspect the selected document. The next test binds the real `editor-content` resource tag, refreshes actual AX nodes, requires **both** exact model content and visible native text, and adds model/AX/bounds diagnostics on failure. It still uses real touch, accessibility text entry and real IME; no Compose input interceptor, longer timeout, ignored assertion or retry. This is a stronger driver/diagnostic repair, **not yet a causal proof** of why the third run failed. No editor production behavior was silently changed.

## Actual image inspected / transport failure preserved

One complete actual silver screenshot was recovered and visually read: `r24c-native-previews/third-failed-workflow/r24c-silver.png`, SHA-256 `98d47cea102bf7a388435f0812941eadd8664b8f1395ff349fbd9872fd86e490`,280×622. It shows native light-silver surfaces, slanted floor/grid, readable separated pin cards, visible Find/control actions and a horizontally overflowing dock rather than compressed icons. Small preview text/ellipsized labels are not a whole-app readability pass. Media/map used the same generic document glyph; the next production patch gives those routes distinct original play/globe glyphs.

Tabletop PNG transport was incomplete (6/12 chunks), wall/geometry observations were not recovered. They were **not** displayed or fabricated. The old exporter emitted up to8 notices **per image per step**, exceeding GitHub's effective per-step notice retention. The next exporter globally pages at most8 chunks/step and separates a capture index/actual measured targets; limits32 chunks/image,4 named images,16 chunk steps. A test round-trips the actual recovered silver PNG. Failed-frame images remain evidence of a failed workflow, not a promotion to pass.
