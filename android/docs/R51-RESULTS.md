# R51 Exact Verification Results & Runtime Honesty Ledger

- **Branch:** `arena/64550eba-osb`
- **Date:** `2026-10-28`

## 1. Quantitative & Qualitative Leap (`R50 -> R51`)
| Axis | `R50` | `R51` | Delta |
|---|---:|---:|---:|
| New pure-JVM production kernels | `80` | **`85`** | `+5` |
| Touched production files (`pure-JVM` + `Android/Compose`) | `14` (`4+10`) | **`16` (`5+11`)** | `+2` |
| Total created/touched production files in-round | `94` | **`101`** | `+7` |
| Total `src/main/**/*.kt` production files on disk | `816` | **`901`** | `+85` |
| Modules deepened (`settings.gradle.kts`) | `50 / 50 (100.00%)` | **`50 / 50 (100.00%)`** | `100.00%` |
| Full-spec Class A accepted modules (`H-` Runtime Honesty) | `0 / 50 (0.00%)` | **`0 / 50 (0.00%)`** | Zero inflation |
| `FormulaEval` spreadsheet functions | `168` | **`215`** | `+47` |
| `SpillSheetEngine` spilled array operations | `29` | **`36`** | `+7` |
| Android/Compose AST `PARSE OK` files (`check_r26_android_syntax.py`) | `35 / 35` | **`46 / 46`** | `+11` |
| Contiguous CI round contract tasks (`.github/workflows/android.yml`) | `39` (`r13..r50`) | **`40` (`r13..r51`)** | `+1` |
| `feature-register.json` `partial_unverified` / `not_implemented` / `verified` | `213 / 14 / 0` | **`221 / 6 / 0`** | `+8` rows (`91` advanced) |
| Round JVM contract assertions (`run_r51_contracts.py`) | `8,555` (`26` arts) | **`12,251` (`41` arts)** | `+3,696` (`+15` arts) |
| Independent Python 3.11 differential oracle checks (`check_r51_reference.py`) | `146 / 146` | **`208 / 208`** | `+62` |
| Java2D `FontMetrics` / pairwise 2D visual checks / min WCAG AAA | `321` / `36,942` / `9.37:1` | **`864` / `110,376` / `11.93:1`** | `+543` / `+73,434` |
| Live `:8765` warm-JVM workbench (`fusion_workbench_cli.py serve-self-test`) | Cards `1..18` | **Cards `1..22` (`Kotlin-JVM-R51`)** | `+4` live cards |

## 2. Adversarial Repairs Verified From Zero (`I. REPAIR`)
1. `AgeV1StreamCipherEnvelope.kt`: enforced `if (fileKey.size != 16) return null` strictly prior to header `HMAC-SHA256` verification, rejecting malformed wrapped file-key lengths before key derivation.
2. `PdfUa1TaggedStructureWriter.kt`: enforced exact non-blank `/Alt` check on `<Figure>` and strict H1..H6 heading-level continuity (`level <= prevHeadingLevel + 1`).
3. `YjsRgaTextCrdtEngine.kt`: validated causal `origin` existence before mutating document map, preserving unmodified state on out-of-order causal parents.
4. `fusion_workbench_server.py`: repaired multiline BibTeX fixture formatting and `Secure; SameSite=None; Partitioned` CHIPS cookie header in `Kotlin-JVM-R51` daemon self-test (`WORKBENCH SELF-TEST PASS`).

## 3. Explicit `H-` Boundaries
- **Full-Spec Class A remains `0 / 50 = 0.00%`:** No Android SDK 37 (`android.jar`, `aapt2`, `d8`, `R8`), emulator/device ART, `SurfaceFlinger` `FLAG_SECURE`, TalkBack `AccessibilityService`, hardware HALs, or `16 KB` ELF `.apk` install execution occurred in this Linux host container.
- **6 Hardware/Legal Blocked Rows Kept `not_implemented`:** `CA-04` (DRM/HDCP/FairPlay), `CA-06` (proprietary Miracast Wi-Fi P2P WFD), `CA-07` (`AudioPlaybackCapture` hardware capture), `CM-01` (multi-app VirtualDisplay), `FM-12` (proprietary `.rar`/`.7z` write), `TD-05` (root OS namespace sandbox).
