# R51 Permissive Clean-Room Architecture Alternatives & Exact Class-A Path

**Date:** `2026-10-27` · **Branch:** `arena/64550eba-osb`

## 1. Answering the Core Question: Writer, Spreadsheet & Core Modules -> Class A

| Pillar | Why Full Class A Remains `0/50` in Host Sandbox (`H-`) | Permissive Clean-Room Bypass Executed in `R51` (`H+`) | Permissive Specs / Reference Designs Studied (Zero Code Copied) |
|---|---|---|---|
| **Writer (`:core:documents`, `:feature:editor`, `:format:ooxml`, `:format:lok`, `:format:pdf`)** | Needs Android API 37 `EditText`/`BasicTextField` IME composition, TalkBack traversal, print spooler, and SAF picker on device. | Multi-section `.docx` (`<w:sectPr>`, `<w:cols>`, `<w:gridSpan>`, `<w:bookmarkStart>`), rich `.odt` (`<table:table>`, `<text:footnote>`, `<style:master-page>`), `WidowOrphanGalleyPaginator` (Typst-grade cost model), and `PdfUa1TaggedStructureWriter` (`ISO 14289-1` `/StructTreeRoot` + `/MCID` + `/Alt` gate), live in `:8765` Card 19. | `ECMA-376` 5th Ed., `OASIS ODF 1.3`, `ISO 14289-1 PDF/UA-1`, `Typst 0.15` (`Apache-2.0`). |
| **Spreadsheet (`:format:ooxml`, `:format:lok`, `:feature:calc`)** | Needs Compose 2D touch/stylus viewport & TalkBack cell announcements on Android hardware. | `212`-function `FormulaEval` (+44 stats/finance/regex/lookup fns), `36`-op `SpillSheetEngine` (`GROUPBY`, `PIVOTBY`, `EXPAND`, `RANDARRAY`, `PERCENTOF`, `TRIMRANGE`), `StructuredTableRefEngine` (`xl/tables/table1.xml` + `Table1[Col]`), `R1C1FormulaTranslator`, `CalcChainDependencyGraph`, live in `:8765` Card 20. | `IronCalc` (`MIT/Apache-2.0`), `ECMA-376` SpreadsheetML, `OASIS OpenFormula 1.3`. |
| **Browser & Wasm Sandbox (`:feature:browser`, `:core:capabilities`)** | Needs Android `WebView` / isolated-process Binder service on device. | `WasmSandbox` `0x7B` `v128` + `0xFD` LEB128 SIMD (`i32x4`, `i16x8`, `v128.load/store`), `CssFlexBlockBoxLayouter` (standalone reader/app layout), `ChipsPartitionedCookieJar` (`RFC 6265bis` `Partitioned`), `ServiceWorkerOfflineCacheManifest`, live in `:8765` Card 21. | `W3C WebAssembly 2.0` (`CC-BY-4.0`), `Taffy` (`MIT`), `RFC 6265bis` / W3C CHIPS. |
| **Tails-Light & 20-Vector Hardware Shield (`:privacy`, `:core:security`, `:core:hw`, `:feature:vault`)** | Needs Android `VpnService` TUN, `UsbManager`, `SensorManager`, and `SurfaceFlinger` `FLAG_SECURE` on hardware. | `TorVanguardsLiteMesh` (`Prop 333` 4-relay RAM-only L2 guards), `Luks2Argon2idHeaderAuditor` (`Tails 7.15.1`), `AgeV1StreamCipherEnvelope` (`RFC 8439` ChaCha20-Poly1305 + strict 16B key check), plus `V16..V20` (`GhostTouch`, `Thunderclap IOMMU`, Hall tamper, Optical ALS/cam mute, DRAM Rowhammer canary arena), live in `:8765` Card 22. | `Tor Prop 333` (BSD), `LUKS2` spec, `age v1` (`BSD-3-Clause`), `RFC 8439`, `USENIX GhostTouch`, `NDSS Thunderclap`. |

## 2. Exact Partition of the Final 14 `not_implemented` Spec Rows (`14 -> 6`)

- **8 Rows Advanced to `partial_unverified` in `R51` via Clean-Room Permissive Kernels:**
  1. `AP-05` -> `OwnStartupInventoryLedger.kt` (`:app`, own startup/worker inventory; Shizuku cross-app remains external).
  2. `TD-04` -> `LinuxRootfs16KbManifestVerifier.kt` (`:feature:terminal`, 16 KB ELF64 `p_align >= 0x4000` + read-only proot bind-mount verifier).
  3. `TD-06` -> `AdbWirelessPairingHelper.kt` (`:feature:terminal`, AOSP `_adb-tls-pairing._tcp` QR/six-digit pairing payload & port validator).
  4. `CA-12` -> `MiracastSessionAdaptationEngine.kt` (`:feature:beam`, WFD RTSP `M3/M4` `wfd_video_formats` bitrate/fps adaptation ladder).
  5. `RG-05` -> `OemGameOverlayCoexistencePolicy.kt` (`:core:perf`, Android `GameManager` mode + OEM floating-overlay inset collision resolver).
  6. `LN-10` -> `HandLandmarkGestureClassifier.kt` (`:feature:lens`, Apache-2.0 21-landmark 3D finger-curl & pinch hysteresis gesture classifier).
  7. `LN-12` -> `GenerativeSceneRamLicenseGate.kt` (`:feature:lens`, `>= 8 GiB` RAM + permissive SPDX model manifest gate + deterministic procedural studio backdrop).
  8. `MS-01` -> `BlueBubblesBridgeClientProtocol.kt` (`:feature:messages`, clean-room REST/Socket.IO client envelope & user-owned Mac prerequisite validator).
- **6 Rows Strictly Preserved as `not_implemented` (`H-` Legal / Copyleft / Privileged Boundaries):**
  - `CA-04` (Apple proprietary FairPlay DRM), `CA-06` & `CA-07` (separate GPL copyleft receiver APKs), `CM-01` (separate LGPL Wine/Box64 APK), `FM-12` (external UID-2000 Shizuku Binder), `TD-05` (`/dev/kvm` AVF hypervisor).
