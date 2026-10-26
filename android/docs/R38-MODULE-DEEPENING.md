# R38 module deepening

Standard unchanged: a module counts as having a substantial testable subset
when its census row names production files, executable checks, a command,
and pinned sha256 pairs — validated by `tools/module_testability.py --check`.

## Census counts (absolute and percent)

| Metric | Absolute | Percent |
|---|---|---|
| Modules with substantial testable subsets | 50/50 | 100.00% |
| Modules not established by the standard | 0/50 | 0.00% |
| Modules deepened by R38 with NEW executed kernels | 15/50 | 30.00% |
| Modules deepened R26–R38 combined (distinct, executed kernels) | 45/50 | 90.00% |
| Fully specification-accepted modules | 0/50 | 0.00% |
| Unfinished at full scope | 50/50 | 100.00% |

Counting notes: the distinct metric still counts modules whose census row
names a kernel **executed on the host JVM**; the excluded five are `:app`
(R29's UsbAllowlist line runs against an in-memory fake of two Android APIs,
not a pure kernel), `:format:native` (lifecycle/TSV contracts, not an
executed kernel), `:benchmark`, `:baselineprofile`, `:testing` (device
measurement / harness rows). All fifteen R38 modules were already distinct,
so the distinct count holds at 45/50 while per-module executed depth rises —
same shape as R37. A census-honesty repair happened in-round: the beam
sentence first claimed a "first executed beam kernel", which is false
(ShareManifest R29, ChunkPlan R33); corrected before commit.

R38 deepens: :feature:beam (MagnetParse + Bencode — the round's only
double), :feature:media (FlacHead), :format:image (WebpHead), :core:model
(SemVer), :core:storage (UuidInfo), :feature:browser (HttpRange),
:feature:messages (QuotedPrint), :core:security (PemBound + KeyCadence —
the second double), :feature:automation (IsoWeek), :feature:accessibility
(HslColor), :core:hw (EdidHead), :feature:capture (VttTime), :privacy
(LuhnCheck), :core:index (Soundex), :format:pdf (PdfName).

## Absolute execution counts

| Kernel | New assertions | Fixture |
|---|---|---|
| MagnetParse | 20 | — |
| Bencode | 24 | — |
| FlacHead | 20 | flac.bin (42 B) |
| WebpHead | 17 | webp.bin (30 B) |
| SemVer | 22 | — |
| UuidInfo | 18 | — |
| HttpRange | 19 | — |
| QuotedPrint | 19 | — |
| PemBound | 18 | — |
| IsoWeek | 21 | — |
| HslColor | 18 | — |
| EdidHead | 18 | edid.bin (128 B) |
| VttTime | 19 | — |
| LuhnCheck | 17 | — |
| Soundex | 19 | — |
| PdfName | 17 | — |
| KeyCadence | 17 | — |
| **Total** | **326** (reported by the runner; R37 was 274) | 3 fixtures |

(Note: the 7-comparison semver precedence chain is emitted by one loop over
a pinned list; every other count is a flat static call site. Nothing is
branch- or loop-amplified beyond that declared chain.)

## Ownership cuts this round (do not retry)

- ZipEocd — `format/archive/.../StrictZip.kt` already owns 0x06054b50.
- VarInt — `cast/castv2/.../Proto.kt` already owns protobuf varint.
- A1Ref — `format/ooxml/.../DecimalSheet.kt` already owns base-26 columns.

## Is there substantial enough code for a test, per feature/module?

Yes for 50/50 modules by the testable-subset standard (the census lists the
exact file pairs and hashes). No module is specification-complete; the gap
between "testable subset" and "feature done" remains the honest headline:
0/50 done, 50/50 testable, and R38 widened executed coverage in 15 of them.
