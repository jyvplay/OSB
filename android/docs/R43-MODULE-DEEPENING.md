# R43 module deepening census

Standard restated: a module holds a *substantial testable subset* when
it contains enough real logic that a meaningful executed test exists.
After R43:

- **50/50 modules (100%)** hold substantial testable subsets.
- **23/50 modules (46%)** received NEW executed kernels this round
  (R42: 21, R41: 20) — the widest round yet; all verified deepens-again
  BEFORE writing.
- **45/50 modules (90%)** carry at least one distinct executed kernel
  (the five without remain `:app`, `:format:native`, `:benchmark`,
  `:baselineprofile`, `:testing`).
- **0/50 specification-accepted. No A-grade claim.**

## R43 kernels (sum 372 assertions, runtime-verified)

| Module | Kernel | | Module | Kernel |
|---|---|---|---|---|
| :format:ooxml | SheetFreeze | | :feature:vault | AgeId |
| :core:documents | BidiStrip | | :feature:files | BreadcrumbTrim |
| :feature:editor | DupLines | | :core:perf | Apdex |
| :format:pdf | PdfPerm | | :feature:calc | PercentChange |
| :feature:browser | RefererPolicy | | :cast:airplay | RtspTransport |
| :format:archive | DebControl | | :feature:messages | MsgIdParse |
| :core:capabilities | ApiName | | :privacy | TimeCoarse |
| :feature:together | SeatColor | | :core:windowing | AspectSnap |
| :feature:media | Bpm | | :feature:alerts | AlertKey |
| :feature:beam | TrackerTier | | :core:model | VersionRange |
| :feature:lens | QrVersion | | :feature:find | SnippetWindow |
| :core:security | RpIdMatch | | | |

Totals: 23 kernels / 23 modules / 372 assertions (R42: 22/21/362).
Writer+spreadsheet this round: SheetFreeze + BidiStrip + DupLines =
3 kernels / 46 assertions (panes, paste hygiene, cleanup commands).

## CI bridge status
`r43Contracts` joins the lane; once green, R37–R43 = **137 kernels /
2399 assertions** under Gradle per push.

## Ownership cuts this round (grep-verified; do not retry)
- Ucs2Parts — `feature/messages/SmsSegments.kt` owns segmentation.
- WordWrap — `feature/editor/WrapCol.kt` owns greedy wrapping.
- BackoffEnvelope — `core/security/AttemptWindow.kt` +
  `feature/vault/LockoutPolicy.kt` own retry timing.

## Census mechanics
Deepen ×3 byte-identical; `--check` green; TOUCHED empty (one kernel
repair touched only an R43 file); sibling allowance extended to r42's
tool-test file; inventory regenerated BEFORE the lab.
