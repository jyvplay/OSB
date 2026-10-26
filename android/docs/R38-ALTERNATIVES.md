# R38 alternatives — browser, Office engine, sandbox, productivity

Standing constraint set: no SDK/emulator/device here; CI device lanes are
flaky-but-readable; engine integrations (Servo AAR, LibreOfficeKit) are
inspected artifacts, not integrated runtimes. Alternatives shipped or
advanced this round, all open-source-compatible:

## Browser
- **Range-request core (shipped)**: `HttpRange` gives the download/media
  layer exact RFC 9110 semantics (resume, suffix fetch, 416) without any
  engine. Next adapter: wire into the local HTTP beam subset for resumable
  share pulls — pure logic already proven.
- Content-blocker/cookie/robots/safe-navigation models (R30-R36) plus
  HttpRange now cover request policy end to end; the rendering gate is
  still the Servo AAR (CI inspects it; integration stays blocked by the
  emulator lane, documented in the ledger).

## Office engine
- **PdfName (shipped)** extends the pdf module below trailer level: name
  objects decode exactly per §7.3.5, which xref/dictionary walking needs
  next. The alternative path around LOK remains: parse-and-project (CSV,
  XLSX literal import, DecimalSheet arithmetic, PDF trailer+names) rather
  than full-fidelity rendering.
- **QuotedPrint (shipped)** is transport plumbing for document mail-out —
  the messages module can now round-trip QP bodies byte-exactly.

## Sandbox
- **Bencode + MagnetParse (shipped)** give the beam module a validated
  intake boundary: a hostile .torrent/magnet never reaches a parser that
  tolerates duplicate keys or unsorted dicts (the BEP 3 2017 clarification
  is enforced as refusal). Depth/size/Long caps are declared narrowings —
  the sandbox stance is refuse-don't-repair.
- **PemBound (shipped)**: strict transport syntax for future key import;
  single-block, canonical-bits-only, no ASN.1 claims.

## Productivity
- **IsoWeek (shipped)**: schedule math for automation rules, mirrored by
  Python's isocalendar. **SemVer (shipped)**: update/compat gates for the
  module catalog. **UuidInfo (shipped)**: v7 ids give storage sortable
  timestamps with zero clock code. **Soundex (shipped)**: phonetic recall
  in search without any index rebuild. **VttTime (shipped)**: caption
  timing for capture review. **FlacHead/WebpHead/EdidHead (shipped)**:
  media/display inventory honesty from bytes alone. **HslColor (shipped)**:
  palette math feeding the existing contrast gates. **LuhnCheck (shipped)**:
  PAN redaction in logs/exports with a no-overreach guarantee.

## Spares banked for R39 (surveyed, not written)
- `GifLoop` is NOT available (GifInfo owns the surface). Candidates that
  passed a quick ownership scan: `ObjReplace` (U+FFFC handling, :core:documents),
  `CropBox` vs `MediaBox` precedence (:format:pdf), `IcoDir` (:format:image),
  `Crc16Modbus` (:cast:dlna transport checks). Each needs a fresh ownership
  grep before writing — R37 and R38 both lost 3 candidates to late cuts.
