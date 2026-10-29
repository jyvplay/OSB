# Round 49 (`R49`) Negative Space, Mechanism Portfolio & Second-Order Adversaries

## D. NEGATIVE SPACE (`18` Candidate Shapes Across `6` Mechanism Families)

### Modal Shortcut Failure Test
- **Tempting modal shortcut**: Flip Writer (`:core:documents`, `:feature:editor`) and Spreadsheet (`:format:ooxml`) to `status = "complete"` in `docs/module-audit.json` or mark `FF-02`/`PR-11` `"verified"` in `docs/feature-register.json` based on large host assertion counts (`> 5,200`), while leaving `168` owner requirements stale at `not_implemented` and skipping higher-order `LAMBDA` closures or 2025–2026 hardware side-channel defenses (`ChoiceJacking`, `Pixnapping`).
- **Why the modal shortcut fails**:
  1. Marking any module `"complete"` or requirement `"verified"` without passing `common_acceptance_gates` and signed device artifacts violates Runtime Honesty and immediately trips `tools/release_gate.py` and `tools/module_audit.py`.
  2. Conversely, leaving `docs/feature-register.json` frozen at `168` `not_implemented` hides genuine clean-room kernel progress from `requirements.csv` and `FULL-SPEC-REVIEW.md`.
  3. Implementing fake regex/macro string substitution instead of lexical `LAMBDA` closures breaks nested `REDUCE(..., LAMBDA(acc, row, VSTACK(acc, ...)))` and omitted `TAKE`/`DROP` counts (`TAKE(A1:C4, , -1)`).

### Enumerated `18` Candidate Shapes (`6` Distinct Mechanism Families)

| # | Family | Candidate Shape | Disposition | Reason / Defect if Rejected |
| ---: | --- | --- | --- | --- |
| 1 | F1: Optimal Typesetting | **Knuth–Plass DP paragraph line-breaker + Liang TeX trie hyphenator + widow/orphan multi-column paginator** | **SELECTED** | $O(n \cdot k)$ active-breakpoint minimum-demerits layout with exact stretch/shrink adjustment ratios $r \in [-1, \infty)$. |
| 2 | F1: Optimal Typesetting | Greedy first-fit word wrap with post-hoc inter-word space stretching | **REJECTED** | Produces rivers of whitespace on narrow 360px/multi-column pages and ignores hyphenation lookahead across lines. |
| 3 | F1: Optimal Typesetting | Myers $O(ND)$ word-token redline diff + ODF 1.3 `<text:tracked-changes>` + Mail Merge + BibTeX/CSL citations | **SELECTED** | Full clean-room Writer publishing workflow across both OOXML (`w:ins`/`w:del`) and ODF 1.3. |
| 4 | F2: Functional Array Calc | **Lexical `CellValue.Lambda` closures (depth $\le 32$) + `MAP`/`REDUCE`/`SCAN`/`BYROW`/`BYCOL`/`MAKEARRAY` + `VSTACK`/`HSTACK`/`TAKE`/`DROP`/`CHOOSECOLS`/`CHOOSEROWS`** | **SELECTED** | Matches `IronCalc v0.8.0` and `rustsheet #1608` (including omitted `TAKE`/`DROP` dimensions keeping all rows/cols). |
| 5 | F2: Functional Array Calc | Textual string-replace macro expansion for `LAMBDA` | **REJECTED** | Variable capture corrupts shadowed `LET`/`LAMBDA` bindings and permits exponential term blowup (`LAMBDA(f, f(f))`). |
| 6 | F2: Functional Array Calc | Two-phase Simplex LP + Brent Goal-Seek + `ECMA-376` DrawingML `xl/charts/chart1.xml` + `x14:sparklineGroups` | **SELECTED** | Gives Spreadsheet deterministic optimization + OOXML chart/sparkline XML & vector rendering. |
| 7 | F3: Format & Crypto Containers | **PDF AcroForm + `BT..ET` text highlight quads + `/ByteRange` signature coverage check + WinZip `AE-2` `0x9901` + `formats.yaml` + LOK tile pyramid + HEIC ISOBMFF** | **SELECTED** | Closes `FF-01`, `FF-03`, `FF-04`, `FF-07`, `FF-11`, `FF-12`, `FF-14`, `FF-16`, `FF-17`, `FM-05`, and `PR-08` kernel gaps cleanly. |
| 8 | F3: Format & Crypto Containers | Trusting PDF `/Contents` signature digest without verifying `/ByteRange` covers `[0..sigStart)` and `(sigEnd..EOF]` | **REJECTED** | Vulnerable to incremental-append PDF shadow/byterange gap attacks that alter visible pages after signing. |
| 9 | F4: Capability Wasm & Browser | **Wasm 2.0 `Table`/`call_indirect` + `Global` + `Data` (`memory.init`/`data.drop`) + `f32` + `0xFC trunc_sat` + WASI socket CIDR gate** | **SELECTED** | Executes indirect function tables and `emsdk 6.0.6+` `0xFC` saturating float-to-int conversions deterministically. |
| 10 | F4: Capability Wasm & Browser | Unchecked `call_indirect` jumping to `table[idx]` without comparing `FuncType(paramCount, resultCount)` | **REJECTED** | Violates Wasm 2.0 indirect call type safety and corrupts the operand stack frame. |
| 11 | F4: Capability Wasm & Browser | **`RFC 8828` Mode 1–4 WebRTC ICE candidate scrubber + split-tab/group/sleeping/session workspace + profile-isolated site permissions + Find-in-Page** | **SELECTED** | Closes `PR-07b..r` and prevents STUN `srflx` proxy-bypass IP leaks (`MODE_4_DISABLE_NON_PROXIED_UDP`). |
| 12 | F4: Capability Wasm & Browser | Relying solely on Chrome-style `.local` mDNS ICE host obfuscation | **REJECTED** | Per `RFC 8828` analysis, `.local` mDNS hides LAN IPs (`192.168.x.x`) while leaving real public ISP IPs exposed in `typ srflx` STUN candidates. |
| 13 | F5: Anonymity & Hardware Defense | **`RFC 9849` TLS ECH + Tails `7.15.1` emergency CA SPKI blocklist + Secure Boot 2026 expiry check + local `VpnService` DNS/UID firewall** | **SELECTED** | Closes TLS plaintext SNI leakage and compromised exit-node CA MITM (`CVE-2026-113470` class). |
| 14 | F5: Anonymity & Hardware Defense | **`ChoiceJacking` USB/AOAP/HID->BLE guard + `Pixnapping` `CVE-2025-48561` blur/overlay/dither shield + 25ms power/DVFS epoch quantizer** | **SELECTED** | Defends the three major 2025–2026 local hardware/side-channel attacks (`USENIX Sec '25` & `ACM CCS '25`). |
| 15 | F5: Anonymity & Hardware Defense | Relying solely on standard Android "Allow USB file transfer?" dialog against malicious chargers | **REJECTED** | Proven trivially bypassed in `133 ms` across 8 major OEMs by `ChoiceJacking` (`AOAP` `0x18D1:0x2D00..0x2D05` + HID buffer flood + HID->BLE pairing). |
| 16 | F5: Anonymity & Hardware Defense | Relying solely on `READ_FRAME_BUFFER` permission denial without disabling cross-window blur or `HIDE_OVERLAY_WINDOWS` | **REJECTED** | Bypassed by zero-permission `Pixnapping` (`CVE-2025-48561`) via semi-transparent blur Activity stacking + `GPU.zip`. |
| 17 | F6: Honest Traceability & UI Wiring | **Wire `8` more Compose screens (`25/25` AST `PARSE OK`) within intra-module / existing Gradle edges + advance kernel-backed rows in `feature-register.json` to `partial_unverified` (`0` `verified`, `0/50` Class A)** | **SELECTED** | Synchronizes `requirements.csv` and `FULL-SPEC-REVIEW.md` with reality while keeping `release_gate.py` 100% fail-closed. |
| 18 | F6: Honest Traceability & UI Wiring | Importing `:privacy` or `:format:ooxml` directly into `:feature:browser` or `:feature:calc` without updating `build.gradle.kts` | **REJECTED** | Breaks `native_source_audit.py --check` (`missing_internal_edges`) and violates `:feature:browser`'s zero-project-dependency isolation. |

---

## E. MECHANISM PORTFOLIO (`7` Orthogonal Mechanisms)

1. **Dynamic-Programming Optimal Typography & Tracked Publishing** (`KnuthPlassLineBreaker`, `LiangHyphenationEngine`, `MultiColumnPagePaginator`, `OdfTrackedChangesCodec`, `DocumentCompareDiff`, `MailMergeTemplateEngine`, `BibliographyCitationEngine`).
2. **Bounded Lexical `LAMBDA` Calculus, Dynamic Array Algebra & Simplex Optimization** (`FormulaEval` `130` fns, `SpillSheetEngine` `22` array/HOF ops, `DrawingMlChartBuilder`, `SheetSparklineRenderer`, `SheetSolverSimplex`, `OoxmlMacroVbaInspector`).
3. **Zero-Trust Binary Container Inspection & Signature ByteRange Coverage** (`PdfPkcs7SignatureVerifier`, `PdfFormAcroFieldInspector`, `PdfPageTextSearchHighlighter`, `ZipAesExtraFieldCodec`, `FormatSandboxWatchdog`, `HeicIsomBoxInspector`, `AiPdfCompatibilityBridge`, `FormatsRegistryCatalog`, `ExportFidelityAdvisor`, `LokTileRenderCache`).
4. **Capability-Typed Wasm 2.0 Indirect Dispatch & Saturating Numerics** (`WasmSandbox` `Table`/`call_indirect`/`Global`/`Data`/`trunc_sat`, `WasiSocketCapabilityGate`, `AppFunctionsSchemaRegistry`).
5. **Network Metadata Encryption & Proxy-Pinned ICE Scrubbing** (`TlsEchConfigCodec` `RFC 9849`, `TailsCaTrustPinBlocklist` `Tails 7.15.1`, `WebRtcIceCandidateScrubber` `RFC 8828` Mode 4, `VpnAppFirewallPolicy`).
6. **Multi-Vector Physical & Microarchitectural Side-Channel Isolation** (`ChoiceJackingUsbGuard`, `UsbDataSignalingController`, `PixnappingSurfaceShield`, `PowerDvfsTimingMitigator`, `ModuleAppLockGate`, `TinkAeadKeysetEnvelope`).
7. **Cross-Language Differential Verification + Headless `FontMetrics` Layout Proof + Fail-Closed Traceability** (`check_r49_reference.py`, `render_r49_visual_preview.py`, `requirements.py`, `native_source_audit.py`, `release_gate.py`).

---

## G. SECOND-ORDER ADVERSARY CRITIQUE

1. **Self-Application Recursion Bomb in `LAMBDA(f, f(f))`**:
   - *Attack*: Spreadsheet user writes `=LET(omega, LAMBDA(f, f(f)), omega(omega))` to hang the UI/evaluator thread via `StackOverflowError`.
   - *Defense & Test*: `FormulaEval` tracks active `lambdaCallDepth` on every closure application and fails deterministically at `MAX_LAMBDA_CALL_DEPTH = 32` (`CellErr.Err.NUM` / bounded exception), tested directly in `R49Checks.kt` and `check_r49_reference.py`.
2. **Omitted Argument Misinterpretation in `TAKE` / `DROP` (`TAKE(A1:C4, , -1)`)**:
   - *Attack*: Naive comma-splitting drops empty arguments between commas (`,,`), turning `TAKE(A1:C4, , -1)` (keep all rows, last column) into `TAKE(A1:C4, -1)` (keep last row, all columns!).
   - *Defense & Test*: `FormulaEval` (`Expr.OmittedArg`) and `SpillSheetEngine` (`splitTopLevelArgsPreservingEmpty`) preserve empty positional slots explicitly (`ISOMITTED`), matching Excel 365 and `rustsheet #1608`.
3. **PDF Signature Shadow / ByteRange Hole Attack**:
   - *Attack*: Attacker signs bytes `[0..1000)` and `[1200..2000)` while appending an unsigned incremental xref update `[2000..2450)` that replaces Page 1's `/Contents`.
   - *Defense & Test*: `PdfPkcs7SignatureVerifier` enforces `offset1 == 0`, `offset2 == offset1 + len1 + hexContentsEnvelopeBytes`, AND `offset2 + len2 == pdfBytes.size` (full-EOF coverage; any trailing unsigned bytes trigger `UNSIGNED_INCREMENTAL_BYTES_AFTER_SIGNATURE`).
4. **ChoiceJacking `AOAP` Simultaneous Host+HID & Delayed Buffer Flood**:
   - *Attack*: Malicious charger attaches `VBUS`, injects 64 `KEYCODE_ENTER` events in `90 ms`, then flips to `AOAP` `0x18D1:0x2D01` at `130 ms`.
   - *Defense & Test*: `ChoiceJackingUsbGuard` rejects both `AOAP` PIDs `0x2D00..0x2D05` with concurrent HID interfaces AND any USB role/mode transition preceded by external HID keystrokes within `5,000 ms`.
5. **Traceability Inflation via `feature-register.json`**:
   - *Attack*: Bulk-updating `docs/feature-register.json` accidentally sets `verified` or promotes legally excluded rows (`CA-06`/`CA-07` GPL receivers, `MS-02` iMessage without Mac, `PR-01` RichText superseded).
   - *Defense & Test*: Excluded (`CA-06`, `CA-07`, `MS-02`) and superseded (`PR-01`) rows remain untouched; `verified` count is asserted `== 0` in `test_r49_tools.py`; `requirements.py --release` is asserted to exit non-zero (`RELEASE BLOCKED`).
