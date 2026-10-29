# R54 Results — Writer & Spreadsheet Depth, 7-Method Recall, Post-Quantum Tails-Light, Shields #31–#35

Executed on `2026-10-29` on host OpenJDK `17.0.9+9` with Kotlin `2.2.21` (`-Werror -jvm-target 17`), Python `3.11`, and the real CPython `sqlite3` / `zipfile` / `hashlib` / `hmac` modules. Android SDK `37.0` CI gates are inherited from R53's green run; R54 adds `:testing:r54Contracts` and `check_r54_reference.py` to that workflow.

## RUNTIME HONESTY

- **Executed here:** host `kotlinc` compilation of 22 new pure-JVM kernels + 5 touched kernels; `dev.fusion.testing.R54Checks` on JVM 17 (**`R54_CONTRACTS_OK assertions=1109 kernels=22 modules=50/50`**, `21` artifacts); `check_r54_reference.py` (**`R54_ORACLE_OK checks=139 artifacts=21`**) as an **independent Python re-implementation** (it re-parses the bytes; it does not call Kotlin); `check_r26_android_syntax.py` (**`62/62 PARSE OK`**); `render_r54_visual_preview.py` (**`R54 VISUAL AUDIT PASS sheets=3 boxes=124 pairs=2534 minContrast=7.58`**); `check_project.py`; and the full `tools/test_*.py` suite.
- **Not executed (so not claimed):** physical Android devices, live TalkBack/Switch/Voice review, hardware `MediaProjection`/`FLAG_SECURE` capture, USB-C TCPM kernel enforcement, Wi-Fi firmware CSI suppression, 16 KB physical install, StrongBox attestation, LibreOfficeKit C++ layout parity, HarfBuzz complex-script shaping.
- **Module accounting (both numbers):** host-testable subsets **`50/50 = 100.00%`**; full-spec Class A **`0/50 = 0.00%`**. Writer and Spreadsheet are *deepened to Class-A-depth behaviour on host* but are **not** declared Class A, because the conjunction gate (both-flavour build + lint, whole-scope, mandatory device spikes, security/privacy/AX review, API37/16K, licence/SBOM) is still open.

## What was added (22 kernels, 3 Compose files, 5 touched kernels)

| Area | Files | Executed proof |
|---|---|---|
| Writer typography | `OptimalParagraphComposer.kt`, `LiangHyphenationTrieEngine.kt`, `OpenTypeSfntMetricsParser.kt`, `PeritextRichSpanCrdtEngine.kt` | 15 measures × (never worse than greedy, 0 overfull lines, ≤2 consecutive hyphens, no inverted slice); Liang breaks inside `[2, len-3]` with no `ff/fi/fl` split; real `sfnt` built+parsed (96 glyphs, `A→34`, `hmtx` 640, `kern A/V = -80`, truncated font refused); Peritext replicas converge on text + runs after concurrent format/insert/delete |
| Writer containers | `DocxBidirectionalPackageCodec.kt` (+`Rtf`), `OdtBidirectionalPackageCodec.kt` | `.docx` 11 parts incl. `tblGrid`/9 cells/2 footnotes/1 comment/numbering, deterministic bytes; `.odt` `mimetype` first **and** STORED (39 B); `.rtf` astral `U+1F680` via signed `-10179?`/`-8576?` and round-trips |
| Spreadsheet optimisation | `SpreadsheetSimplexMilpSolver.kt`, `Decimal128FinancialEngine.kt` | Big-M simplex + Bland; LP `16.5` ≥ MILP `16.5` ≥ exhaustive integer grid; MILP integral; infeasible/unbounded/empty-model classified; knapsack `11`; decimal `0.1+0.2+0.3 = 0.6`, 100k×`0.07` `= 7000.00` while binary64 drifts (`6999.9999999921…`); amortisation ends at exact `0.00` |
| Spreadsheet file format | `Sqlite3BtreeTableFileReader.kt` | 9-byte SQLite varint round-trip; 2-page DB; **CPython `sqlite3`: `integrity_check = ok`, rows `North/1200/19.99, South/-40/0.5, West/0/NULL`**, `sqlite_master` + declared types match; corrupted page type detected |
| Repertoire (touched) | `FormulaEval.kt` **350** fns, `SpillSheetEngine.kt` **56** ops | 50 new functions exercised (trig/inverse, complex, XIRR/PRICEMAT, `NORM.INV` vs Acklam, `VAR.P/S`, `MTRACE`, `DAYS360`, matrix `CIRCULANT/HILBERT/CHOLESKY/GRAM/ROW_NORM/EWMA/SAVGOL/HADAMARD`); spill `SEQUENCE`, `HILBERT(4)`, and a **deterministic `BLOCKED_COLLISION`** |
| Recall 7 methods | `RecallTier6MseSourceBufferTap.kt`, `RecallFlacRfc9639LosslessArchiver.kt`, `RecallMethodEscalationRouter.kt` (touched) | FLAC verified at 6 block sizes (16…4096): frame-sync landing for every seekpoint, **PCM MD5 == STREAMINFO MD5**, shifted (file-relative) offsets and zero sample counts rejected; MSE tap keeps 4/5 fragments, drops a fully covered duplicate, trims an overlap, reports a gap, refuses missing-init and degenerate-only input; 6 R53 tiers preserved, 7 extended methods, tier 7 receipts counted and undoable |
| Post-quantum / passkey | `MlKem768NttPolynomialEngine.kt`, `TorProp340PackedRelayCellCodec.kt`, `WebAuthnLevel3AuthDataVerifier.kt` | SHA3-256/SHA3-512/SHAKE128 match Python `hashlib`; 24× NTT round-trip **and** NTT·basemul == schoolbook; compress error bounds for `d∈{1,4,10,12}` over all 3329 coefficients; CBD₂ ∈ [-2,2]; X25519 DH agreement; Prop 340 lossless at 1/200/509/512/1100/2500 B with 509-byte cells; WebAuthn rejects wrong rpId, `BS=1&BE=0`, trailing bytes, clone signCount, missing UP/UV, short/malformed CBOR; RFC 6238 vectors `287082` / `081804` |
| Shields `#31`–`#35` | `UsbPdStructuredVdmAltModeGuard.kt`, `DvfsHertzbleedFrequencyNoiseGuard.kt`, `GpuPixelTimingPixnappingShield.kt`, `WifiCsiKeystrokeSensingMitigator.kt`, `HallMagnetometerCoverSpoofGuard.kt` | unstructured VDM & commands `7..15` refused, `16..31` blocked when locked, TB `EnterUSB` needs a trusted unlocked dock, charging preserved; DVFS `r<0` leaking trace alerts while benign does not; render duration quantised to the vsync grid and 10 ms distinguishable vs 1 µs not; CSI cadence lock + rogue FTM responder flagged and suppressed; instant magnet slew and closed-with-no-field flagged, fail-safe stays locked |
| Desktop ergonomics | `ContextMenuKeyboardTypeaheadSubmenuEngine.kt`, `BespokeViewModeAnimatedMorphSpec.kt`, `UndoToastBatchCoalescerController.kt` | typeahead prefix accumulation/reset, mnemonic lookup, separator/disabled skipping, 150 ms hover-intent remainder, safe-triangle bridge, left-flip at the right edge with `180..280dp`/`168..240dp` enforcement; 3×3×11 morph frames stay in the 24×24 box on the half-pixel grid; **50-receipt burst → 1 visible card, `8.44%`→`7.11%` right-edge fraction, LIFO undo replay** |
| Compose UI | `feature/editor/WriterTypographyInspectorCard.kt`, `feature/calc/.../calcui/SpreadsheetOptimizerPanel.kt`, `feature/browser/RecallSevenMethodStudioSheet.kt` | parse-gated (`62/62 PARSE OK`); each renders live kernel output (break quality, MILP/decimal objective, method routing) rather than static text |

## Repairs (#1–#5, zero-trust, each with a new adversarial test)

1. **RTF `\u` escape mis-parse** — `\uc1`/`\u233?` were misread as escapes, silently dropping text (and an astral emoji). Fixed with an explicit digit guard; test asserts exact round-trip incl. `U+1F680`.
2. **DOCX footnote/comment counting** — the counter missed `w:type="separator"` (attribute order). Fixed to attribute-order-insensitive scan; test asserts `2` footnotes and `1` comment.
3. **SQLite varint 9-byte form** — encode/decode disagreed for values ≥ 2⁵⁶. Fixed to the spec's 7×8 + 8-bit tail layout; test round-trips `2⁶³-1` and `2³⁵`.
4. **Simplex sign convention & Phase-I objective** — the first implementation reported `UNBOUNDED`/wrong optima. Rewritten as Big-M with the correct `x_B = rhs − Σtab·x_N` sign convention, Bland's rule, and an explicit empty-model case; validated against exhaustive integer enumeration, infeasible and unbounded models.
5. **ML-KEM `ByteDecode` bit order and FLAC 9-bit block-size code** — decode read LSB-first and the encoder wrote an 8-bit block-size code for 512 frames, breaking lossless MD5 for every block size > 16. Both fixed; the oracle now decodes the PCM itself and compares the MD5.

Also repaired: **a name collision I introduced** — an early draft overwrote the R49 `KnuthPlassLineBreaker`; it was restored byte-for-byte from `HEAD` and the new work moved to `OptimalParagraphComposer` (verified by `test_existing_r49_breaker_preserved` and by re-running `R49_CONTRACTS_OK assertions=6301` and `R53_CONTRACTS_OK assertions=25425`).

## Ledger truth

- `feature-register.json`: `231` rows, **`0` verified**, `221` `partial_unverified`, `12` rows newly tagged `jvm:R54Checks`; `204/231` rows now carry at least one executed `jvm:*` tag (`FM-12`, `TD-05`, `CM-01`, FairPlay/GPL receivers, and hardware/AX-only rows still have none).
- `module-audit.json`: **`0/50` complete**; each touched module gained an `r54_host_evidence` note and the file carries an explicit `r54_note` that host kernels do not satisfy the conjunction gate.
- `class-a-readiness-r54.json` (`fusion.class_a_readiness.v6`): `11` pillars, all `H+` for the host portion, `0/50` Class A, `50/50` testable subsets.

## Visual inspection

`docs/verification/r54-host-workbench/` — `r54-writer-typography.png`, `r54-spreadsheet-class-a.png`, `r54-recall-shield-ergonomics.png` (all `1880×1400`), inspected by reading each PNG back: no overlapping text boxes, every panel inside its border, next to a mock context menu with the selected row and submenu, and the three original `List`/`Grid`/`Columns` glyphs. `metrics.json` records `boxes=124`, `pairs=2534`, `min_wcag_contrast=7.58`.

## Next highest-information tests

1. `:app:assembleProDebug`/`assemblePlayDebug` + lint with the R54 UI files wired into a real screen (needs SDK 37 CI — the workflow now runs `:testing:r54Contracts`).
2. Open the emitted `r54-writer-roundtrip.docx`/`.odt` in LibreOffice and diff the rendered PDF against the composer's line breaks (LOKit parity is still `H∂`).
3. Run `r54-calc-ledger.sqlite` through SQLite's own `sqlite3` CLI with `PRAGMA quick_check` on a second platform to confirm the varint/serial-type assumptions.
4. Feed a real X25519 + ML-KEM-768 ACVP vector set into `MlKem768NttPolynomialEngine` to move the ring layer from "self-consistent" to "KAT-verified".
