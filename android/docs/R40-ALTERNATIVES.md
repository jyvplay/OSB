# R40 alternatives — browser, Office engine, sandbox, productivity

## The direct question again: "writer and spreadsheet to Class A this turn?"

Still no — and the reason has not changed shape: A grade = full-spec
acceptance = rendering fidelity + device interaction + acceptance review,
which one sandbox turn cannot produce. What changed THIS round is the
distance to it:

- **Spreadsheet (:format:ooxml, now ELEVEN executed kernels)** gains the
  two layers every style pass needs: format-code STRUCTURE
  (NumFmtSections — splitting and classification, with rendering
  explicitly still refused) and the complete classic error-literal
  algebra (CellErr, with #SPILL!/#CALC! refused BY NAME so the
  dynamic-array decision is a decision). Remaining A-gap, named:
  format RENDERING (placeholder semantics), styles.xml wiring, formula
  breadth, rendered grid on device.
- **Writer (:core:documents + :feature:editor, now TEN executed
  kernels)** gains dash/ellipsis education, U+FFFC object bookkeeping
  and footnote marks. Remaining A-gap: span-aware application (code
  spans must be excluded by the editor before DashEducate/SmartQuote
  run — tracked as the integration task), pagination, IME evidence.

## The structural change this round: CI-executed kernels

The biggest standing gate ("host kotlinc is not the shipping toolchain")
got a bridge: `:testing:r39Contracts` + `:testing:r40Contracts` run all
39 recent kernels under Gradle in the JVM lane. This is the pattern to
extend backwards (r38 and earlier consumers can be wired identically,
one round per lane-budget) until every executed kernel is CI-executed.

## Browser

HtmlEntity completes the text-extraction path's reference handling with
the cp1252 quirk refused-and-documented rather than silently imported.
With Punycode (R39) + entities + the policy kernels, the non-engine
browser layer now lacks mainly: URL canonicalization breadth and the
engine itself (Servo lane unchanged).

## Office engine

Parse-and-project gains PdfRotate (the last Table-30 attribute the page
geometry needed after R39's CropBox) — a PdfPageGeometry composition
(Rotate × boxes × UserUnit refusal) is now a one-kernel step. DOCX-side:
FootnoteMark + RomanNum + ColLetters cover the numbering families
numbering.xml projects onto.

## Sandbox

OtpauthUri is the round's intake-hardening piece (refuse unknown params,
split-before-decode — the repaired shape is exactly a smuggling defense);
SevenZHead + PlistHead extend refuse-before-walk to two more container
families; SafeName closes the filename-injection corner for every
export path.

## Productivity

TaskCheckbox (notes), Percentile (perf dashboards), SnapFraction
(desktop tiling with the exactness property), Monogram (launcher),
Fnv1a (index buckets), Mrz (lens capture validation), Bech32 + IpRedact
(vault/privacy hygiene).

## Spares surveyed for R41 (re-grep before writing)

PdfPageGeometry (compose Rotate+CropBox, both in-house now); editor
span-exclusion wiring for the educators (integration, touches existing
files — needs the TOUCHED census path); DocxNumbering projector;
IcoCur hotspots; `ZipLocal` header walk (check ZipEocd cut scope first);
backporting rNN consumers into Gradle contract tasks (r38 next).
