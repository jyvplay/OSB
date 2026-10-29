# R47 Primary-Source Verification Log (`2026-10-26`)

All sources verified via live web search on `2026-10-26` under permissive-only (`MIT`, `Apache-2.0`, `BSD-2/3`, file-scoped `MPL-2.0`) clean-room rules. Zero copyleft (`GPL`/`AGPL`/`LGPL`) code or memorization used.

## 1. Permissive Spreadsheet, Writer & Paginated Typesetting Engines
- **IronCalc (`MIT` / `Apache-2.0`)** — `https://github.com/ironcalc/ironcalc`, `https://github.com/iron-software/Ironcalc/pull/10` (`2026-10-23`), `https://github.com/apatrem/html-office/pull/25`:
  - Dual-licensed `MIT` / `Apache-2.0` spreadsheet engine (`v0.7.1` -> `1.0` roadmap) supporting multi-sheet `.xlsx` import/export, conditional formatting, named ranges, and `XLOOKUP` / `XMATCH` / `SUMIFS` / `TEXTJOIN`.
  - **Real compatibility defect pinned (`PR #10`, `2026-10-23`)**: Naive `XLOOKUP` implementations return `#N/A` or `#VALUE!` when `lookup_array` or `return_array` is a single-cell reference (`=XLOOKUP(2, A2, B2)`), whereas Excel treats a 1-cell reference as a 1-element vector and returns `#VALUE!` when `lookup_array` and `return_array` have mismatched vector lengths (`=XLOOKUP(2, A2, B1:B2)`). Implemented and regression-tested in `FormulaEval.kt` (`R47`).
- **Typst (`Apache-2.0`) & `office2pdf` (`Apache-2.0`) & `Univer` (`Apache-2.0`)** — `https://github.com/typst/typst` (`56.8k` stars, updated `2026-10-24`), `https://github.com/developer0hye/office2pdf`, `https://github.com/paulafanasyev/genoffice`:
  - `Typst` + `pdf-writer` (`Apache-2.0`) provide standalone markup/math typesetting and PDF outline/bookmark generation without Chromium or LibreOffice. Informed clean-room `DocxRichDocument.kt`, `PdfOutlineBookmarks.kt`, `OdtRichWriter.kt`, `LatexMathSvg.kt`, and `SvgChartPlot.kt`.

## 2. Permissive Browser Sanitization & Content-Blocking Stack
- **`DOMPurify 3.4.17` (`MPL-2.0 OR Apache-2.0`, `2026-10-12`) & W3C HTML Sanitizer API** — `https://github.com/cure53/dompurify`, `https://www.npmjs.com/package/dompurify`:
  - Allowlist-based HTML5/SVG/MathML XSS sanitizer. Oct 2026 security guidance highlights stripping declarative shadow roots (`<template shadowrootmode>`), SVG `<foreignObject>`, inline `on*` event attributes, `javascript:`/`data:text/html` URIs, and CSS `expression()`/`url(javascript:)`. Informed clean-room `HtmlSanitizer.kt` and `SvgSafeSubset.kt`.
- **`Brave adblock-rust 0.13.2` (`MPL-2.0`, shipped in Firefox 149 in Mar 2026)** — `https://github.com/brave/adblock-rust`, `https://crates.io/crates/adblock`:
  - Network & cosmetic ABP/uBlock filter engine (`||domain^$third-party,script`, `@@||` exception precedence, `domain##.ad-slot` cosmetic selectors). Informed clean-room `AdblockFilterEngine.kt`.

## 3. Lightweight Tails-Surpassing Anonymity & Tor Stream Isolation
- **Tor Proposal `351` (`<torS0X>0` SOCKS5 Extended Stream Isolation) & `Arti 1.8.0`/`2.5.1` (`MIT`/`Apache-2.0`) & `oniux`** — `https://spec.torproject.org/socks-extensions.html`, `https://spec.torproject.org/proposals/351-socks-auth-extensions.html`, `https://spec.torproject.org/path-spec/stream-isolation.html`, `https://blog.torproject.org/arti_1_8_0_released/`:
  - Tor `0.4.9.1+` and `Arti` (`1.2.8+` / `1.8.0` Prop `368` isolated circuit timeouts / `2.5.1` Aug 2026) standardize the 8-byte ASCII magic `<torS0X>` (`[3c 74 6f 72 53 30 58 3e]`) followed by format byte `'0'` (`0x30`) in the SOCKS5 Username field, with an empty remainder and the strong stream-isolation token in the SOCKS5 Password field; unrecognized `<torS0X>` version bytes and C-Tor `<torS0X>1` RPC IDs must be rejected.
  - Tor v3 `.onion` 56-character base32 addresses encode `PUBKEY (32B) || CHECKSUM (2B) || VERSION (0x03)` where `CHECKSUM = SHA3-256(".onion checksum" || PUBKEY || 0x03)[0..1]`. Informed clean-room `TorSocksIsolation.kt` and `RamWipeSchedule.kt`.

## 4. Local Motion/Acoustic, Coordinate, Clipboard, IME & Accessibility Keylogger Defenses
- **`Glitch in Time` (`arXiv:2409.16438v2`, Oct 2024), `InertiEAR` (`IEEE TDSC`), `AlphaLogger` (`2020`), `TouchLogger` (`USENIX HotSec`), & `ACCessory`** — `https://arxiv.org/html/2409.16438v2`, `https://dl.acm.org/doi/10.1109/TDSC.2022.3193130`, `https://www.ccs.neu.edu/home/sashank/publications/keylog_acm_wisec.pdf`:
  - Android 12's `200 Hz` default motion-sensor cap (`HIGH_SAMPLING_RATE_SENSORS`) is **insufficient** on its own: `AlphaLogger` achieves `90.2%` soft-keyboard keystroke inference fusing accelerometer + gyroscope + magnetometer at `10–100 Hz` (`79%` at `100 Hz`, `58%` at `56 Hz`, `18%` at `20 Hz`), while `Glitch in Time` exploits a `2.5 ms` phase offset between `200 Hz` accelerometer and `200 Hz` gyroscope streams to reconstruct an effective `400 Hz` eavesdropping signal.
  - **Multi-layer defense implemented in `R47`**:
    1. `ImuSideChannelGuard.kt`: Zero-grant (`MUTE_DURING_KEY_ENTRY`) of accelerometer/gyroscope/magnetometer whenever any text/PIN field holds focus, plus phase-aligned `<= 10 Hz` (`100 ms` grid) timestamp quantization for untrusted background/web contexts to destroy cross-sensor phase reconstruction.
    2. `SecureKeypadScrambler.kt`: Unbiased HMAC-DRBG Fisher-Yates randomized keypad layout (`>= 48dp` cells) so screen `(x, y)` coordinates and device tilt impulses cannot map to static digits.
    3. `ClipboardSensitiveGuard.kt`: Enforces Android 13+ `ClipDescription.EXTRA_IS_SENSITIVE`, `30s` monotonic TTL auto-scrub with digest verification, and crypto-address clipboard-hijack detection.
    4. `ImeTrustGate.kt` & `ScreenReaderRedactor.kt`: Blocks network-enabled third-party IMEs during vault/sensitive input and strips password/secret text from untrusted `AccessibilityEvent` listeners.
