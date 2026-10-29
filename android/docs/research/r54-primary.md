# R54 Primary Research & Clean-Room Specification Ledger (`2026-10-29`)

All R54 production kernels, UI ergonomics engines, Writer & Spreadsheet Class-A-depth codecs, 7-method **Recall** recording stages, post-quantum Tails-Light anonymity primitives, and local hardware side-channel defenses (`#31`–`#35`) are authored 100% from scratch under `Apache-2.0` from open, royalty-free IETF, W3C, ECMA, OASIS, ISO, NIST, USB-IF, and Tor Project specifications.

---

## 1. Writer Class-A-Depth Typography, Pagination & Bidirectional Format Specifications

1. **Knuth–Plass Optimal Paragraph Line-Breaking & Liang TeX Hyphenation (`1981` / `1983`, verified against `AdamantiumStudio/AdamantiumEngine#47`, `liminal-hq/folio#28`, `storytold/craft-libs#19`):**
   - Models paragraphs as typed sequences of `Box(width, text)`, `Glue(width, stretch, shrink)`, and `Penalty(width, penalty, flagged)`.
   - Adjustment ratio $r = (L - W) / S$ when $W < L$ (using total stretch $S > 0$) or $r = (L - W) / K$ when $W > L$ (using total shrink $K > 0$, invalid when $r < -1.0$).
   - Cubic badness $b = \min(10000, \lfloor 100 |r|^3 + 0.5 \rfloor)$; fitness classes `0` ($r < -0.5$ tight), `1` ($-0.5 \le r \le 0.5$ normal), `2` ($0.5 < r \le 1.0$ loose), `3` ($r > 1.0$ very loose), with `fitnessDemerit = 3000..10000` when $|\text{class}_i - \text{class}_{i-1}| > 1$.
   - Total line demerits: $(10 + b + p)^2 + \alpha_{\text{flagged}} + \alpha_{\text{fitness}}$ for $p \ge 0$; $(10 + b)^2 - p^2 + \dots$ for $-10000 < p < 0$.
   - **Adversarial invariants retrieved (`storytold/craft-libs#19`):** (a) never emit an inverted item slice `start..end` (`end < start`) when consecutive negative penalties appear; (b) enforce hard `maxConsecutiveHyphens` (`<= 2`) even in emergency-stretch passes before falling back to greedy live-typing wrap.

2. **OpenType / TrueType `sfnt` Binary Font Table Specification (`ISO/IEC 14496-22`, verified against `SixLabors/Fonts#565`, `genspark-ai/genoffice#1771`, `ChristianPresley/zigguro#322`):**
   - Offset table: 4-byte `sfntVersion` (`0x00010000` TrueType or `0x4F54544F` `'OTTO'`), `numTables` (`u16`), `searchRange`, `entrySelector`, `rangeShift`, followed by `16`-byte `TableRecord(tag, checkSum, offset, length)`.
   - `cmap` Format 4 (`BMP` segmented mapping): `segCount = segCountX2 / 2`; **adversarial bounds invariant (`genoffice#1771`)**: verify `16 + 8 * segCount <= subtableLength` before indexing `endCode`, `startCode`, `idDelta`, or `idRangeOffset`; **adversarial `idDelta` invariant (`SixLabors/Fonts#565`)**: in the `idRangeOffset[i] != 0` branch, if the looked-up glyph index `g != 0`, still compute `(g + idDelta[i]) & 0xFFFF`, while `g == 0` stays `0` (`.notdef`).
   - `hmtx` horizontal metrics: `numberOfHMetrics` pairs `(advanceWidth: u16, lsb: i16)`; for `glyphId >= numberOfHMetrics && glyphId < numGlyphs`, clamp advance width to `hMetrics[numberOfHMetrics - 1].advanceWidth`; for `glyphId >= numGlyphs`, return `0` without throwing.

3. **ECMA-376 5th Edition WordprocessingML (`.docx`), OASIS OpenDocument 1.3 (`.odt`), & Rich Text Format `1.9.1` (`.rtf`):**
   - `.docx`: `[Content_Types].xml`, `_rels/.rels`, `word/document.xml`, `word/styles.xml`, `word/numbering.xml`, `word/footnotes.xml`, `word/comments.xml`, `word/header1.xml`, `word/footer1.xml`, `word/_rels/document.xml.rels`.
   - `.odt` (`OASIS ODF 1.3 Part 2 §2.2.1`): `mimetype` (`application/vnd.oasis.opendocument.text`) MUST be the **first** ZIP entry (`index 0`), stored uncompressed (`METHOD_STORED`, `compressedSize == size == 39`), with no extra fields, paired with `META-INF/manifest.xml`, `styles.xml`, and `content.xml`.
   - `.rtf`: Group stack `{ ... }`, control words `\b`, `\i`, `\ul`, `\par`, `\uc1`, and signed 16-bit `\uN?` UTF-16 code units (converting negative values `N < 0` via `N + 65536` and pairing high/low surrogates `0xD800..0xDFFF` into astral Unicode scalar values).

---

## 2. Spreadsheet Class-A-Depth Optimization, Decimal128 & SQLite 3 Format Specifications

4. **SQLite 3 Binary File Format (`sqlite.org/fileformat2.html`, verified against `sjqtentacles/sml-sqlite` & `Dicklesworthstone/frankensqlite`):**
   - `100`-byte database header on page `1`: magic `"SQLite format 3\u0000"` (`0..15`), big-endian `u16` page size at `16..17` (`512..32768` power of two, or `1` meaning `65536`), page count at `28..31`, text encoding at `56..59` (`1 = UTF-8`, `2 = UTF-16le`, `3 = UTF-16be`).
   - **SQLite Big-Endian Varint (1–9 bytes, NOT LEB128):** bytes `1..8` contribute 7 bits each with MSB `0x80` signaling continuation; byte `9` (if reached) contributes **all 8 bits**.
   - Table B-tree pages: interior table page `0x05` (`12`-byte header ending with 4-byte `rightChild` page number) and leaf table page `0x0D` (`8`-byte header; `u16` cell count at `+3`, cell pointer array following header; page 1's B-tree header starts at byte offset `100`).
   - Record serial types: `0` (`NULL`), `1..6` (`8/16/24/32/48/64`-bit big-endian two's-complement signed int), `7` (IEEE-754 big-endian `float64`), `8` (integer `0`), `9` (integer `1`), `N >= 12` even (`(N-12)/2` byte `BLOB`), `N >= 13` odd (`(N-13)/2` byte `TEXT`).

5. **Two-Phase Revised Simplex & Branch-and-Bound MILP + IEEE 754 `decimal128`:**
   - Phase I introduces non-negative artificial variables for `>=` and `=` rows to establish a basic feasible solution (`BFS`) or prove infeasibility (`INFEASIBLE`), using **Bland's smallest-subscript rule** to guarantee finite termination without degenerate cycling; Phase II optimizes the original objective (`OPTIMAL` or `UNBOUNDED`); Branch-and-Bound splits fractional integer variables ($x_j \le \lfloor v \rfloor$ vs $x_j \ge \lceil v \rceil$) with LP-relaxation pruning.

---

## 3. Recall 7-Method Media Studio: W3C MSE `SourceBuffer` & IETF RFC 9639 `FLAC`

6. **IETF RFC 9639 (`FLAC` Lossless Audio Codec, verified against `ietf-wg-cellar/flac-specification`, `musetric/musetric#907`, `OxideAV/oxideav-flac#11`):**
   - Magic `"fLaC"` (`0x66 0x4C 0x61 0x43`), mandatory first metadata block type `0` (`STREAMINFO`, `34` bytes): `minBlockSize(16)`, `maxBlockSize(16)`, `minFrameSize(24)`, `maxFrameSize(24)`, `sampleRate(20)`, `channelsMinus1(3)`, `bitsPerSampleMinus1(5)`, `totalSamples(36)`, `md5Signature(128)` (raw interleaved little-endian signed PCM bytes!).
   - **Adversarial `SEEKTABLE` invariant (`musetric#907`, RFC 9639 §8.5.1):** Each `18`-byte `SEEKPOINT` stores `(firstSampleOfTargetFrame: u64, byteOffsetFromFirstAudioFrameHeader: u64, targetFrameSampleCount: u16)` — never offset from byte `0` of the file and never compressed byte size in field 3!
   - Audio frames: 15-bit sync `0x3FFE` (`0xFFF8` / `0xFFF9`), UTF-8-coded frame/sample number, CRC-8 (`x^8 + x^2 + x + 1`, `0x07`) frame header checksum, verbatim/fixed-LPC subframes, byte alignment zero-pad, and CRC-16 (`x^16 + x^15 + x^2 + 1`, `0x8005`) full-frame footer.

7. **W3C Media Source Extensions (`MSE`) `SourceBuffer` Tap + UPnP/DLNA `AVTransport:1` (`karan-ksrk/dlna-cast`, `chenzhenhong/mini-mdr`):**
   - Method 7 (`TIER_6_MSE_SOURCEBUFFER_TAP`) captures MSE initialization segments (`ftyp+moov` or WebM `EBML+Segment+Tracks`) paired with out-of-order `appendBuffer` media fragments (`moof+mdat` or `Cluster`), deduplicating overlapping presentation timestamp ranges and sealing a standalone container.
   - DLNA `SSDP M-SEARCH` + SOAP `AVTransport:1` enforces same-host RFC 1918 LAN control-URL origin validation so a rogue LAN SSDP packet cannot trigger SSRF against loopback or external hosts.

---

## 4. Post-Quantum Tails-Light, W3C WebAuthn Level 3 & Hardware Shield `#31`–`#35`

8. **NIST FIPS 203 (`ML-KEM-768`) & Tor Proposal 340 (`Packed and Fragmented Relay Cells`, `torproject/torspec`, `RustCrypto/KEMs#398`, `itzmeanjan/ml-kem`):**
   - `ML-KEM-768`: $q = 3329$, $n = 256$, $k = 3$, $\eta_1 = \eta_2 = 2$, $d_u = 10$, $d_v = 4$, `ek = 1,184 B`, `dk = 2,400 B`, `ct = 1,088 B`, `ss = 32 B`; constant-time bitwise `CBD_2` sampling without secret-dependent table lookups.
   - Tor Proposal 340: packs multiple stream relay messages into a single `514`-byte cell payload and fragments oversized messages across consecutive cells with explicit header framing.

9. **W3C WebAuthn Level 3 §6.1 `authenticatorData` (`Neziahtech/streampay#322`, `golang/go#71095`):**
   - Layout: `rpIdHash[32] || flags[1] || signCount[4] || optional attestedCredentialData || optional extensions`.
   - **Adversarial invariants (`streampay#322`):** (a) reject `(flags & 0x10) != 0 && (flags & 0x08) == 0` (`BS=1` while `BE=0` is forbidden by §6.1); (b) when `(flags & 0xC0) == 0` (`AT=0` and `ED=0`), require `authenticatorData.size == 37` **exactly**, rejecting trailing unauthenticated bytes.

10. **Hardware Side-Channel & Physical Bus Defenses `#31`–`#35` (`OpenDevicePartnership/embedded-usb-pd#92`, `#101`, `hodgesds/narf#1194`):**
    - **`#31` USB-PD R3.2 V1.2 §6.4.12.2 Table 6.33 Structured VDM & `EnterUSB` Guard:** Enforces `bit 15 == 1` (`Structured VDM` type bit) and 5-bit `Command in {1..6} U {16..31}` while blocking SVID `0x8087` Thunderbolt/USB4 alt-mode & vendor DFU VDMs under lock.
    - **`#32` `Hertzbleed` DVFS Frequency-Throttling Noise Guard**, **`#33` `Pixnapping` Cross-Window GPU Frame-Timing Shield**, **`#34` IEEE 802.11bf Wi-Fi CSI Keystroke-Sensing Mitigator**, and **`#35` Hall-Effect + 3-Axis Magnetometer Cover-Spoof & Induction Guard**.
