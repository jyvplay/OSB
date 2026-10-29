# R51 Primary Research Ledger (Verified 2026-10-27)

All external facts below were retrieved via live `web_search` on `2026-10-27` (`America/New_York`) and govern clean-room Apache-2.0 implementations in `R51` (zero code copied, zero copyleft contamination).

## 1. Permissive Clean-Room Spreadsheet Engine (`IronCalc` MIT / Apache-2.0, 2026)

- **Sources:** `https://www.ironcalc.com/`, `https://github.com/ironcalc/ironcalc`, `https://blog.ironcalc.com/2026/01/25/Changelog-v0.7.1.html`, `https://blog.ironcalc.com/`, `https://crates.io/crates/xls-rs`.
- **Verified Takeaways:**
  - `IronCalc` (`v0.7.1` -> `v0.8.x`, `2026`, dual-licensed `MIT / Apache-2.0`) demonstrates that a portable embeddable spreadsheet engine reaches desktop utility by combining:
    1. A pure functional reactive formula core (`LET`, `LAMBDA`, dynamic spill arrays, `200–300+` functions including statistical `CORREL`, `SLOPE`, `INTERCEPT`, `RSQ`, `STEYX`, `FTEST`, `SUMX2MY2`, `SUMX2PY2`, `SUMXMY2`, `ROMAN`, `ARABIC`, financial depreciation/rates, and text/regex).
    2. **Structured Excel Tables (`xl/tables/table1.xml`)** with auto-expanding ranges, banded rows, column totals, and structured references (`Table1[Revenue]`, `Table1[[#Totals],[Revenue]]`, `[@Qty]*[@Price]`).
    3. **`R1C1` <-> `A1` bidirectional formula reference translation**, autofill series extrapolation, merged-cell & freeze-pane geometry, and `.xlsx` / `.csv` lossless round-trip.
  - **R51 Action:** Expand `FormulaEval.kt` from `168 -> 212` pure functions, `SpillSheetEngine.kt` from `29 -> 36` spilled array/grouping ops (`EXPAND`, `RANDARRAY`, `GROUPBY`, `PIVOTBY`, `PERCENTOF`, `TRIMRANGE`, 2D `TEXTSPLIT`), and author `StructuredTableRefEngine.kt`, `R1C1FormulaTranslator.kt`, and `CalcChainDependencyGraph.kt` in `:format:ooxml`.

## 2. Permissive Clean-Room Writer & Accessible Tagged PDF (`Typst 0.14–0.15.1` Apache-2.0, Oct 2025 – Jul 2026)

- **Sources:** `https://github.com/typst/typst/releases`, `https://typst.app/docs/reference/pdf/`, `https://typst.app/docs/reference/text/text/`, `https://en.wikipedia.org/wiki/Typst`, `https://opensource.posit.co/blog/2026-03-05_pdf-accessibility-and-standards/`.
- **Verified Takeaways:**
  - `Typst` (`0.14.0` Oct 2025 -> `0.15.1` Jul 2026, `Apache-2.0`) re-engineered paged document layout around two decisive capabilities that HTML-to-PDF screenshot tools lack:
    1. **Cost-based galley pagination** penalizing `widow` (single trailing line at top of next page), `orphan` (single leading line at bottom of previous page), `runt` (single-word final line), and orphaned headings (`keepWithNext = true`), while laying out per-page running headers/footers and bottom-of-page footnotes separated by a `0.05em` rule.
    2. **Compile-time `PDF/UA-1` (`ISO 14289-1` over PDF 1.7) Tagged PDF emission:** `/MarkInfo << /Marked true >>`, `/Lang (en-US)`, `/ViewerPreferences << /DisplayDocTitle true >>`, `/StructTreeRoot` (`Document -> H1/H2/P/Table/TR/TH/TD/Figure`), `BDC`/`EMC` `/MCID` marked-content sequences, and compile-time refusal of untitled documents, skipped heading levels (`H1 -> H3`), or `<Figure>` blocks missing `/Alt` text.
  - **R51 Action:** Upgrade `DocxRichDocument.kt` (multi-section `<w:sectPr>`, `<w:cols>`, `<w:gridSpan>`/`<w:shd>` tables, `<w:bookmarkStart>` cross-refs) and `OdtRichWriter.kt` (`<table:table>`, `<text:footnote>`, `<style:master-page>`), and author `WidowOrphanGalleyPaginator.kt` (`:core:documents`) + `PdfUa1TaggedStructureWriter.kt` (`:format:pdf`).

## 3. W3C WebAssembly 2.0 `0xFD` `v128` SIMD, Embeddable CSS Flexbox (`Taffy` MIT) & CHIPS Cookies

- **Sources:** `https://webassembly.github.io/spec/versions/core/WebAssembly-2.0.pdf`, `https://github.com/WebAssembly/simd/blob/master/proposals/simd/BinarySIMD.md`, `https://github.com/WebAssembly/simd/blob/master/proposals/simd/NewOpcodes.md`, `https://openapps.pro/packages/taffy`.
- **Verified Takeaways:**
  - W3C WebAssembly 2.0 encodes packed 128-bit SIMD via value type `0x7B` (`v128`) and prefix `0xFD` followed by an unsigned LEB128 SIMD opcode (`v128.load = 0x00`, `v128.store = 0x0B`, `v128.const = 0x0C` + 16 imm bytes, `i8x16.shuffle = 0x0D` + 16 lane bytes, `i32x4.splat = 0x11`, `i32x4.extract_lane = 0x1B`, `i32x4.replace_lane = 0x1C`, `v128.not = 0x4D`, `v128.and = 0x4E`, `v128.or = 0x50`, `v128.xor = 0x51`, `i32x4.add = 0xAE`, `i32x4.sub = 0xB1`, `i32x4.mul = 0xB5`, `i32x4.dot_i16x8_s = 0xBA`).
  - `Taffy` (`MIT` / `Apache-2.0`) demonstrates standalone embeddable CSS Block + Flexbox box-tree layout independent of browser WebView engines.
  - **R51 Action:** Upgrade `WasmSandbox.kt` with `0x7B` `v128` + `0xFD` SIMD execution & `invokeV128`, and author `CssFlexBlockBoxLayouter.kt` + `ChipsPartitionedCookieJar.kt` (`RFC 6265bis` `__Host-` + `(top_frame_site, host_key)` CHIPS isolation) in `:feature:browser`.

## 4. Tails OS `7.15.1` (`Oct 23, 2026`), Tor `Arti 2.7.0` `Vanguards-Lite`, `LUKS2 Argon2id` & `age v1`

- **Sources:** `https://safeguard.sh/resources/blog/tails-release-history-amnesic-live-system-tor-persistent-storage-and-the-update-model`, `https://tech-insider.org/tails-vs-qubes-os-vs-whonix-privacy-os-2026/`, `https://spec.torproject.org/proposals/333-vanguards-lite.html`, `https://blog.torproject.org/announcing-vanguards-for-arti/`, `https://ethz.ch/content/dam/ethz/special-interest/infk/inst-infsec/appliedcrypto/education/theses/semester-project_mirco-stauble.pdf`.
- **Verified Takeaways:**
  - As of `2026-10-27`, Tails `7.15.1` (`2026-10-23`, Debian 13 Trixie, Linux `6.12.111`, Tor `0.4.9.14`, Tor Browser `15.0.26`) and Tor `Arti 2.7.0` (`2026-10-01`) enforce:
    1. **`Vanguards-Lite` (`Proposal 333`)**: `NUM_LAYER2_GUARDS = 4` Layer-2 vanguard relays with `max(U_1, U_2)` `[1, 12]`-day lifetimes (`mean ~ 8 days`), never persisted to disk in amnesic mode, preventing onion/client guard-discovery sybil attacks.
    2. **`LUKS2` + `Argon2id` persistent-storage KDF hygiene**: `memory >= 1,048,576 KiB (1 GiB)`, `time >= 4`, `aes-xts-plain64` 512-bit key, `>= 5`-word Diceware passphrase floor, rejecting legacy `LUKS1`/`PBKDF2`.
    3. **`age-encryption.org/v1` (`X25519` + `HKDF-SHA256` + `RFC 8439 ChaCha20-Poly1305`)**: enforces exact `16-byte` unwrapped file-key length check before `HMAC-SHA256` header authentication (preventing key-length HMAC collision edge cases).

## 5. Local Hardware-Origin Attack Countermeasures (`V16..V20`)

- **Sources:**
  - `GhostTouch` (`Wang et al., USENIX Security 2022`, `https://www.usenix.org/system/files/sec22-wang-kai.pdf`) & `WIGHT` (`IEEE S&P 2022`, `https://www.computer.org/csdl/proceedings-article/sp/2022/131600b537/1FlQCm4Upqg`): contactless `40 mm` dipole EMI and wired charging-cable GND common-mode `100–500 kHz` interference inject phantom taps (`sigma ~ 14.6 x 19.2 px`).
  - `Thunderclap` (`NDSS / LWN 782381`, `https://www.ndss-symposium.org/ndss-paper/thunderclap-exploring-vulnerabilities-in-operating-system-iommu-protection-via-dma-from-untrustworthy-peripherals/`): malicious USB-C / Thunderbolt / USB4 PCIe peripherals exploit disabled/sub-page IOMMU mappings for DMA memory extraction.
- **R51 Action:** Author `V16` `CapacitiveGhostTouchFilter.kt`, `V17` `Usb4PcieIommuDmaGuard.kt`, `V18` `HallMagneticCoverTamperGuard.kt`, `V19` `OpticalReflectionSideChannelGuard.kt`, and `V20` `DramRowhammerZeroizeGuard.kt`.
