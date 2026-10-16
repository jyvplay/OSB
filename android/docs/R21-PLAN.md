# R21 preimplementation contract — 2026-10-15 local

No anonymous OS, complete browser or Office parity claim. No new module denominator.
Actual available: git/gh, Python, cached Kotlin/JDK, host reference libraries, remote Android Actions. No independent agent, physical device, expert review or local Android SDK. Managed Android is actual platform execution, not a mock implementation; it is not a physical acceptance substitute.

Early mechanism branches, before R21 code:
1. Strict bounded UTF8 CSV state machine, with explicit dialect and immutable rectangular table; falsify with quoting, malformed bytes, caps and Python csv disagreement.
2. Existing separate isolated worker/RO-unlinked-FD capability extended to CSV. Prior platform spike evidence: R20 f42193d full run38845693182 contains all FormatIsolationDeviceTest and R18OwnedSheetDeviceTest cases PASS. This is reuse of an executed platform boundary, not S10/S11 full-engine clearance. New operation still needs fresh native tests before UI acceptance.
3. Bounded response frame + owned-input digest/size correlation. Falsifier: malformed lengths/trailing bytes or a wrong digest; matching digest does NOT prove honest computation.
4. Explicit selected-column projection + literal XLSX typing; falsifier: equal selected cells but varying omitted fields changes output, or formulas become executable. No CSV export 'sanitization' by adding hidden apostrophes/tabs.
5. Native worksheet UI with frozen preview invalidated on edits, new-document write/readback, no source overwrite. Falsifier: edit selection after preview, recreation while picker pending, readback mismatch. Parent sees the selected data; it is trusted and network-enabled, not the privacy APK.
6. Browser session/consent generation ownership; reject delayed approval after same-origin/ABA navigation, tab close or profile closure. Real concurrent consume check; no renderer/Android permission grant until real engine integration.
7. Evidence/negative-claim policy, exact candidate native outcomes and independent maintained readers; reject modal promotion to full product or anonymity.

Objects/bounds: CSV input<=65536 bytes, <=256 rows, <=32 columns, <=8192 cells, <=4096 UTF16 units/cell; UTF8 BOM optionally consumed, LF/CRLF record endings, doubled quoted quotes. Reject ragged rows, bare CR outside quotes, malformed UTF8/NUL/unsupported controls. Empty input is zero rows; quoted empty cell is one row. Source payload text and formats are not silently normalized. Worker frame<=131072 bytes; lifetime existing5000ms, parent existing8s reply wait. Counts are bytes, units, rows and cases, NOT completeness.

For every admitted input and ordered unique in-range nonempty column selection, selected output cells equal that projection exactly. For any two admitted inputs with identical selected cells, new XLSX bytes agree under the same runtime/writer environment. CSV parsing is not formula evaluation, arbitrary metadata scrubbing, original-package editing, differential privacy or anonymity. For every consent capability, at most one successful decision/consume; success requires same live document identity, current scope, explicit trusted decision and valid monotonic lease time. These contracts require tests, not existence assertions.

H+: bounded transformations/capability lifecycle are achievable under trusted parent/platform. H-: guest/app cannot enforce secrecy against arbitrary host/IME/privileged observer or retroactively hide external activity. H-boundary: runtime, kernel, caller authenticity, renderer identity, grammar and budgets. Acceptance threshold: host/compiler/readers plus named fresh native workflow; retain failures, add patch-specific attacks. No commit is evidence of acceptance.
