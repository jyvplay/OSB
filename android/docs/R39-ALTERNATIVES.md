# R39 alternatives — browser, Office engine, sandbox, productivity

Standing constraints: no SDK/emulator/device in-sandbox; engine
integrations (Servo AAR, LibreOfficeKit) remain inspected artifacts, not
integrated runtimes; CI device lanes flaky-but-readable.

## The direct question: "writer and spreadsheet to Class A this turn?"

Honest answer: **no — and no amount of host kernels can change that
within one sandbox turn.** A grade here means full-spec acceptance:
rendering fidelity, device interaction, acceptance review. What IS
achievable — and was done — is making the two surfaces' logic layers the
best-evidenced in the repo:

- **Spreadsheet (:format:ooxml)** now holds NINE executed kernels
  (CsvDialect/CsvTable/DecimalSheet/LiteralWorkbook lineage + R39's
  SerialDate, ColLetters, ColWidth): dates (with the Lotus phantom),
  column addressing (full 16384-column bijection) and column geometry
  (the exact ECMA-376 pixel formulas) are no longer approximations. The
  remaining A-gap is mechanism-bounded, not effort-bounded: cell styles,
  shared strings at scale, formula evaluation breadth, and a rendered
  grid — each needs either LOK integration or the in-house SheetFrame
  renderer reaching device tests.
- **Writer (:core:documents + :feature:editor)** gains typographic
  education (SmartQuote), UAX-14 soft-hyphen handling (HyphenSoft) and
  list-numbering math (RomanNum) on top of the diff/markdown/encoding
  lineage. The A-gap: layout, pagination, IME interaction — device-bound.

Path that still closes the gap honestly: keep logic kernels here, drive
the two surfaces' device tests in CI lanes (they exist and are triaged
every round), and record acceptance only when CI shows the rendered
behavior. That is the same ladder every accepted-grade feature must climb.

## Browser

Punycode closes a real gap: the URL bar's IDN handling can now display
and refuse honestly (xn-- round-trips executed; IDNA mapping declared out
of scope rather than faked). Combined with PSL work being owned
elsewhere, cookie/robots/range/content-blocker models, the request-policy
layer is nearly complete without an engine; rendering stays gated on the
Servo AAR lane.

## Office engine

Parse-and-project remains the working alternative to LOK: PDF boxes
(CropBox) join trailer+names; DOCX numbering (RomanNum) and quote/hyphen
education feed export paths. Next concrete step: a PdfPageGeometry kernel
(Rotate × CropBox interaction) and a docx numbering.xml projector.

## Sandbox

JwtSplit gives the permission/consent plumbing a token transport that
constitutionally cannot claim verification (signatureChecked=false is a
constant); UsbDesc extends the input-path audit to descriptor bytes with
its can't-prove-keyboard honesty inline. Refuse-don't-repair continues as
the sandbox stance (escaped JOSE headers, disjoint crops, unknown IBAN
countries, reversed glob ranges — all loud refusals this round).

## Productivity

GlobMatch (find), Rfc3339 (backup ledger timestamps), QuietHours
(alerts), Ulid (sortable ids for storage), IbanCheck (vault redaction
companion to LuhnCheck), MacAddr (privacy log hygiene), MidiNote +
ExposureEv (media/capture math), IcoDir (file-manager thumbnails for .ico).

## Spares surveyed for R40 (ownership re-grep required before writing)

PdfPageGeometry (Rotate×boxes), DocxNumbering projector, `ObjReplace`
(U+FFFC, still free), IcoCur hotspot semantics, Bech32 (if a wallet-free
use exists in vault), CRC-16/CCITT for :cast:castv2 framing (verify
ownership), ColorTemp (a11y). R38's spare bank minus the ones cut above.
