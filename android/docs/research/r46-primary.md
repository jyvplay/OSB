# R46 Primary-Source Verification Log (`2026-10-26`)

All sources below were retrieved via live `web_search` on `2026-10-26` or verified against normative IETF/W3C/ISO/OASIS/POSIX standards to ground R46's Tails-Mod privacy features, hardware anti-keylogger defenses, zero-JNI WebAssembly sandboxing, and paginated PDF/Office architecture.

---

## 1. Tails OS Architecture (`onion-grater`, `udev-watchdog`, `mat2` Metadata Cleaner)

- **Tails Official Design Specification (`https://tails.net/contribute/design/`):**
  - **`onion-grater` Tor Control Port Filter Proxy:** Runs on port `951` in front of Tor's Control Port (`9051`), enforcing per-application YAML command/event allowlists (`/etc/onion-grater.d/*.yml`), e.g. allowing `SIGNAL NEWNYM`, `GETINFO circuit-status`, rewriting `GETINFO net/listeners/socks`, restricting `STREAM` events to streams owned by the requesting client (`restrict-stream-events`), and rejecting unauthorized commands (`SETCONF`, `ADD_ONION`, `GETCONF`) with `510 Command filtered`. Confirmed ongoing 2026 audit in Tails GitLab `#20821` (`https://gitlab.tails.boum.org/tails/tails/-/work_items/20821`).
  - **`udev-watchdog` Emergency Media-Removal Wipe:** Monitors physical boot/security token medium and triggers immediate emergency memory wipe bypassing normal shutdown scripts upon physical removal — with explicit **suspend-to-RAM guard (`#11729`)** disabling false-positive removal triggers during suspend/resume races.
  - **`mat2` / Metadata Cleaner (`https://www1.tails.net/doc/about/features/index.en.html`):** Standard Tails file-sanitization layer stripping identifying metadata from Office OOXML (`docProps/core.xml`, `docProps/app.xml`), ODF (`meta.xml`), PNG (`eXIf`, `tEXt`, `zTXt`, `iTXt`, `tIME`, `pHYs`), JPEG, and PDF files prior to sharing.

---

## 2. Local Hardware-Origin & Wireless Keystroke-Injection Defenses (2025–2026)

- **USBGuard Per-Interface Authorization (`https://rapidriverskunk.works/OffSecHWDefense.html`, `https://www.kicksecure.com/wiki/USBGuard`, `https://www.zerosuniverse.com/tools/usb-hid-badusb-duckyscript-analyzer/`):**
  - Blocks BadUSB devices at the USB interface set level before kernel HID driver bind:
    - `reject with-interface all-of { 08:*:* 03:01:* }` and `{ 08:*:* 03:00:* }` (Mass Storage + HID composite implants).
    - Blocks secondary `03:01:01` boot keyboards hotplugged when an authorized primary keyboard is already attached or while the workstation/device is screen-locked.
    - Kicksecure's architectural boundary: USBGuard is a pre-bind device authorization gate, NOT a post-authorization keystroke filter — requiring post-bind physiological timing analysis as a second independent layer.
- **Physiological Keystroke Jitter Profiling (`HIDGuard`, `https://github.com/Bor-Code/HIDGuard`):**
  - Human finger mechanics exhibit a `>= 25–30 ms` inter-keystroke physiological speed floor and natural interval standard deviation of `25–70 ms`, whereas microcontroller BadUSB loops (`Rubber Ducky`, `Digispark`, `Teensy`, `Flipper Zero`, `Pico`) inject with synthetic uniformity (`stddev < 1.5–2.0 ms`) or sub-20ms bursts.
- **Bluetooth / BLE Unauthenticated HID Injection (`BlueDucky` / `CVE-2023-45866`, `https://bugzilla.redhat.com/show_bug.cgi?id=CVE-2023-45866`):**
  - Exploits `NoInputNoOutput` (`JustWorks`) unauthenticated Bluetooth HID interrupt/control channel pairing to inject DuckyScript keystrokes over the air without user confirmation. Countered by rejecting any Bluetooth/BLE HID (`0x1812` / `0x1124`) keyboard lacking authenticated MITM pairing + encrypted bond.

---

## 3. Permissive Zero-JNI WebAssembly Sandbox & Document/PDF Engines

- **`Chicory 1.7.5` / Bytecode Alliance `Endive` (`Apache-2.0`, `https://github.com/dylibso/chicory`, `https://central.sonatype.com/artifact/com.dylibso.chicory/runtime`):**
  - 100% pure-JVM WebAssembly runtime with **zero JNI and zero native `.so` libraries**, running `.wasm` modules anywhere Java 11+ / Android ART runs without 16 KB ELF native page-alignment issues. Inspires R46's clean-room `WasmSandbox.kt` (W3C WebAssembly Core 2.0 binary magic/section validator, explicit host-import capability gate, linear-memory bounds checker, and deterministic fuel-metered `i32`/`i64` stack machine).
- **Permissive Document & PDF Engines (`Apache PDFBox 3.0.8` `Apache-2.0`, `WeasyPrint` `BSD-3-Clause`, `office2pdf` / `Typst` `Apache-2.0`, `rdocx` `MIT/Apache-2.0`):**
  - Demonstrates direct structured Document/Spreadsheet AST -> paginated PDF 1.7 rendering without LibreOffice or Chromium (`MuPDF`/`PyMuPDF` `AGPL-3.0` and `OpenHTMLtoPDF` `LGPL` explicitly excluded). R46 implements clean-room `PdfPageWriter.kt` directly from ISO 32000-1:2008.

---

## 4. Normative Standards Used for Clean-Room R46 Kernels

- **ISO 32000-1:2008 (PDF 1.7):** Catalog/Pages/Page/Font/Contents object graph, Standard 14 Helvetica/Courier AFM character metrics, text/graphics operators (`BT`/`ET`/`Tf`/`Td`/`Tj`, `re`/`S`/`f`), exact byte-offset `xref` table & `startxref`.
- **W3C WebAssembly Core Specification 2.0:** `\0asm` (`0x00 0x61 0x73 0x6D`) version `1`, LEB128 section framing (Type `1`, Import `2`, Function `3`, Memory `5`, Export `7`, Code `10`), structured `block`/`loop`/`br_if` & `i32`/`i64` arithmetic.
- **ECMA-376 5th Ed. (OOXML) & OASIS ODF 1.3:** Multi-sheet `workbook.xml` + `sheet1..sheetN.xml`, cross-sheet `Sheet2!A1:B4` references, conditional formatting rules, `.ods` typed cells, and `docProps` metadata parts.
- **POSIX.1-2001 (`pax` `ustar` extended headers), RFC 8216 (HLS `.m3u8`), RFC 2045 §6.7 (Quoted-Printable), W3C `Permissions-Policy`, IEEE 802 / RFC 7217 (locally administered unicast MAC), GS1 General Specifications (AI `01`/`17`/`10`/`21` + mod-10 GTIN check digit).**
