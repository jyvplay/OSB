# R54 Formal Engineering Plan — A–J Protocol (`Writer` + `Spreadsheet` Class-A-Depth, 7-Method `Recall`, Post-Quantum `Tails-Light`, & `224/231` JVM-Testable Register Closure)

## RUNTIME HONESTY

- **Available & Executed Locally:** OpenJDK `17.0.9+9` (`java`, `jar`, headless `Java2D` `FontMetrics` + `BufferedImage` rasterizer), Kotlin JVM compiler `2.2.21` (`kotlinc -Werror -jvm-target 17`), Python `3.11` (`zlib`, `gzip`, `zipfile`, `struct`, `hashlib`, `xml.etree.ElementTree`, `openpyxl 3.1.5`, `unittest`), live `Fusion Shell Workbench` (`0.0.0.0:8765`), `web_search` (`github.com` primary specification repos), and `git`/`gh` connected to `jyvplay/OSB` GitHub Actions (`AGP 9.1.1`, `Gradle 9.4.1`, `platforms;android-37.0`).
- **Unavailable Locally (Strict `H-` / `H∂` Boundary):** Physical Android reference hardware (`S1..S30` hardware spikes), live human-operated `TalkBack` / Switch Access / Voice Access review, physical `SurfaceFlinger` `FLAG_SECURE` / `MediaProjection` hardware loopback, 16 KB page-size physical ELF installation, and hardware KeyMint StrongBox chips.

---

## A. FORMAL MODEL

1. **Admissible Objects:**
   - Repository state $\mathcal{R}_{54}$ extending `main` (`92ca4e1`, containing `R1`–`R53`) monotonically with **zero deleted tracked files**, **exact `50` declared Gradle modules** in `settings.gradle.kts`, **`104` new pure-JVM production kernels** (`0` `android.*` imports), **`8` new Jetpack Compose UI components**, **`21` upgraded (`TOUCHED`) production files**, **`43` contiguous Gradle CI contract suites (`r13..r54`)**, **`7` `1880×1400` PNG visual inspection sheets**, and an **open Pull Request** on `arena/63e6364e-osb` -> `main` (kept open per explicit user instruction).
2. **Information / Access Model:**
   - Every pure-JVM kernel operates deterministically over immutable byte/value inputs with zero ambient network, filesystem, or JNI authority; external Python 3.11 differential oracles inspect emitted binary/XML/container bytes independently of Kotlin readers.
3. **Resources Counted:**
   - Pure-JVM kernels (`104 > 96`), Compose UI files (`8 > 7`), AST syntax checks (`67 > 59`), `TOUCHED` files (`21 > 19`), round assertions (`> 34,000 > 25,425`), binary/structured artifacts (`94 > 77`), differential oracle checks (`> 530 > 411`), visual PNGs (`7 > 6`), `FontMetrics` boxes (`> 1,620 > 1,376`), pairwise 2D non-overlap checks (`> 230,000 > 180,495`), `FormulaEval` functions (`350 > 300`), `SpillSheetEngine` ops (`56 > 48`), `Recall` methods (`7 > 6`), hardware defense vectors (`35 > 30`), and `feature-register.json` rows carrying executed `jvm:*` tests (`224/231 = 96.97%` vs `199/231 = 86.15%`, closing **all `22` previously un-JVM-tested `partial_unverified` rows plus `3` clean-room boundary rows `FM-12`, `TD-05`, `CM-01`**).
4. **Quantifier Order for Success Predicate:**
   $$\forall m \in \text{Modules}_{50},\ \exists K_m \subseteq \mathcal{K}_{104} \text{ s.t. } |K_m| \ge 1 \ \land\ \forall k \in K_m,\ \forall x \in \mathcal{T}_k:\ \text{JVM}_{17}(k, x) = \text{Oracle}_{\text{Py311}}(x) \ \land\ \text{AGP}_{37}(\mathcal{R}_{54}) = \text{PASS}.$$
5. **Parameter Regimes, Units & Tolerances:**
   - Floating-point numerical tolerances: $|\Delta| \le 10^{-9}$ (`FormulaEval`, `SimplexMilp`, `KnuthPlass`), exact `0` ULP for `Decimal128FinancialEngine`, exact byte/CRC-8/CRC-16/CRC-32/SHA-256 equality for `FLAC`, `DOCX`, `ODT`, `XLSX`, `ODS`, `SQLite3`, `PDF`, `WebAuthn`, and `ML-KEM-768`; UI touch targets $\ge 48\,\text{dp}$; context menus `180..280dp` (primary) and `168..240dp` (submenu); compact `Undo` stack height $\le 9\%$ of `900dp` desktop right edge; WCAG AAA text contrast $\ge 7.0:1$ (target $> 12.4:1$).
6. **Adjacent Problems Explicitly Refused Substitution:**
   - Do **not** substitute source count or JVM assertion totals for physical-device `Class A` sign-off (`module-audit.json` remains `0/50 = 0.00%` `complete`); do **not** bundle `LGPLv3`/`GPL` code (`mat2`, `shairplay`, `LibreOffice` C++ blobs) into permissive modules; do **not** bypass DRM/EME/`FLAG_SECURE`; do **not** merge the R54 PR (keep it open).

---

## B. OUTCOME SPACE (`H+` / `H-` / `H∂`)

- **`H+` (Achievable & Externally Verified):** `50/50 = 100.00%` host-testable module subsets deepened; Writer & Spreadsheet elevated to full bidirectional `.docx`/`.odt`/`.rtf`/`.pdf` + `.xlsx`/`.ods`/`.sqlite` + Knuth-Plass + OpenType + Simplex/MILP + Decimal128 pure-JVM + Compose UI completeness; `224/231` (`100%` of non-excluded, non-FairPlay/GPL rows) covered by executed `jvm:*` checks; full Android SDK `37.0` dual-flavor APK assembly, JVM unit tests, `43` Gradle contract tasks, cross-language Python differential oracles, and Android Lint passing on CI.
- **`H-` (Impossible Under Honest Constraints):** Accepting `status = "complete"` in `module-audit.json` (`0/50 = 0.00%`) without physical 16 KB hardware, `S1..S30` physical spikes, and human TalkBack/security review; implementing proprietary Apple FairPlay (`CA-04`) or embedding GPL AirPlay receivers (`CA-06`, `CA-07`) inside the permissive main repo.
- **`H∂` (Boundary):** `FM-12` (Shizuku/ADB restricted dir), `TD-05` (AVF `vm_config.json`), and `CM-01` (PE64/ELF64 external compatibility boundary) cross from `not_implemented` to `partial_unverified` at the exact pure-protocol / binary-verifier boundary while preserving their physical runtime/external-APK boundaries explicitly.

---

## D. NEGATIVE SPACE (`18` Counterfeit Shapes Refused + Modal-Shortcut Detector Test)

1. Claiming Writer/Spreadsheet `status: "complete"` in `module-audit.json` without `release_gate.py` physical-device & owner-signed `docs/release-artifacts` digests.
2. Emitting `.odt`/`.ods` archives where `mimetype` is DEFLATE-compressed or placed after `META-INF/manifest.xml` (violates OASIS ODF 1.3 Part 2 §2.2.1).
3. **Modal Shortcut #3 (Tested Explicitly):** Writing FLAC `SEEKTABLE` `SEEKPOINT` offsets relative to file byte `0` instead of the **first audio frame header**, or writing frame byte length instead of **frame sample count** (`RFC 9639 §8.5.1` / `musetric#907`) — detected by `check_r54_reference.py` computing `first_frame_offset + seekpoint.stream_offset` and verifying `0xFFF8`/`0xFFF9` frame-sync magic at that exact byte!
4. Accepting WebAuthn `authenticatorData` with `BS=1, BE=0` (`flags = 0x11`) or trailing bytes when `AT=0, ED=0` (`len > 37`).
5. Parsing USB-PD unstructured VDMs (`bit 15 == 0`) as Structured `Discover Identity` or accepting 5-bit `Command >= 32`.
6. Omitting `idDelta mod 65536` in OpenType `cmap` Format 4 when `idRangeOffset != 0` or trusting `segCountX2` on truncated subtables.
7. Decoding SQLite varints as little-endian LEB128 instead of big-endian 1–9 byte varints with 8-bit 9th byte.
8. Allowing Knuth-Plass line breaking to return inverted `start..end` slices on consecutive penalties or `> 2` consecutive hyphenated lines.
9. Using IEEE-754 `Double` for multi-year bond/ledger penny summation where `0.1 + 0.2 != 0.3` accumulates rounding drift (`Decimal128FinancialEngine` required).
10. Infinite cycling in degenerate Simplex LP tableaux when Bland's anti-cycling pivot rule is omitted.
11. Mutating source HEIC/JPEG pixels during photo edits instead of non-destructive `.fusion-edit.json` sidecar recipes (`MD-03`).
12. Retaining pre-redacted bitmap layers in undo history after `MD-01` PII capture redaction (`CommitFirstRedactedCapturePipeline` zeroes pre-redacted blocks prior to commit).
13. Accepting LAN DLNA `controlURL` pointing to `127.0.0.1` or a different host IP than the SSDP responder (SSRF vector).
14. Secret-dependent lookup tables in `ML-KEM-768` `CBD_2` binomial sampling (`RustCrypto/KEMs#398` timing leak).
15. Expanding right-click context menus past `280dp` or letting burst `Undo` notifications exceed `9%` compact right-edge height.
16. Rendering fractional-pixel blurry `List`/`Grid`/`Columns` vector strokes on `1.25x`/`1.5x`/`2.0x` DPI displays.
17. Adding new Gradle modules to `settings.gradle.kts` that break historical `50/50` census invariants (`test_module_testability.py`).
18. Merging the R54 PR instead of keeping it open as explicitly instructed by the user.
