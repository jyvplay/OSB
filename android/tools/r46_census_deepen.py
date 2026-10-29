#!/usr/bin/env python3
"""Idempotently deepen 34 module rows in docs/module-testability.json with R46 kernels + 3 TOUCHED files.

Re-pins all 3 TOUCHED files (`DesktopChrome.kt`, `TabletopDesktop.kt`, `FormulaEval.kt`) while adding
39 new pure-JVM production kernels across 34 modules (68.00% of 50 modules).
Never modifies docs/module-audit.json (full-spec acceptance remains strictly 0/50 = 0.00%).
"""
from __future__ import annotations

import hashlib
import json
from pathlib import Path
import sys

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "tools"))
import module_testability as census

R46_CHECKS = [
    "testing/src/r46/kotlin/dev/fusion/testing/R46Checks.kt",
    "tools/test_r46_tools.py",
]
CMD = "python3 tools/run_r46_contracts.py"

TOUCHED: dict[str, str] = {
    "app/src/main/java/dev/fusion/shell/DesktopChrome.kt": (
        "R46 patches DesktopTopBar with BoxWithConstraints compact title width cap (<420dp), compact "
        "'WS N' workspace badge, and 1.dp vertical divider before 'Control panel' to fix 360dp horizontal "
        "clipping defects found in visual inspection of r24-native-previews."
    ),
    "app/src/main/java/dev/fusion/shell/TabletopDesktop.kt": (
        "R46 wires TileLabelFit.fitLabel 2-line balanced labels into TabletopDesktop pin tiles so "
        "'Tasks and Automation' and 'Settings and Privacy' render without mid-word ellipsis truncation."
    ),
    "format/ooxml/src/main/kotlin/dev/fusion/ooxml/FormulaEval.kt": (
        "R46 expands FormulaEval from 19 to 35 pure ECMA-376 functions (adding SUMIF, COUNTIF, AVERAGEIF, "
        "IFNA, VLOOKUP, INDEX, MATCH, MEDIAN, STDEV, CONCAT, LEFT, RIGHT, MID, LEN, TRIM, UPPER, LOWER)."
    ),
}

DEEPEN: dict[str, list[tuple[str, str]]] = {
    ":format:ooxml": [
        (
            "format/ooxml/src/main/kotlin/dev/fusion/ooxml/MultiSheetBook.kt",
            "R46 adds MultiSheetBook: multi-worksheet ECMA-376 .xlsx engine with cross-sheet Sheet1!A1 and "
            "quoted 'Q1 Sales'!B2:B10 references, cross-sheet cycle detection, and zero-epoch OPC read/write.",
        ),
        (
            "format/ooxml/src/main/kotlin/dev/fusion/ooxml/SheetPivot.kt",
            "R46 adds SheetPivot: deterministic row/column group-by pivot aggregation engine (SUM, COUNT, "
            "AVERAGE, MIN, MAX) with row, column, and grand totals.",
        ),
        (
            "format/ooxml/src/main/kotlin/dev/fusion/ooxml/CondFormat.kt",
            "R46 adds CondFormat: spreadsheet conditional formatting rule evaluator (cell comparisons, "
            "Top-N, above-average, 2/3-stop RGB color scales, and normalized [0,1] data bars).",
        ),
    ],
    ":format:pdf": [
        (
            "format/pdf/src/main/java/dev/fusion/format/pdf/PdfPageWriter.kt",
            "R46 deepens :format:pdf with PdfPageWriter: clean-room ISO 32000-1 PDF 1.7 paginated writer "
            "with greedy word-wrap, multi-page automatic page-break pagination, Type1 fonts, running headers "
            "and page numbers, zero-metadata privacy Trailer, and exact byte-offset xref table.",
        ),
    ],
    ":core:documents": [
        (
            "core/documents/src/main/kotlin/dev/fusion/documents/DocOutlineRender.kt",
            "R46 adds DocOutlineRender: clean-room CommonMark subset parser/renderer (headings, lists, "
            "fenced code, pipe tables, inline emphasis/links) to XSS-safe HTML5 and canonical Markdown.",
        ),
        (
            "core/documents/src/main/kotlin/dev/fusion/documents/TrackRevisions.kt",
            "R46 adds TrackRevisions: OOXML w:ins / w:del tracked-changes revision model with accept/reject "
            "invariants and mat2-aligned author/timestamp anonymization.",
        ),
    ],
    ":feature:editor": [
        (
            "feature/editor/src/main/java/dev/fusion/editor/CitationBib.kt",
            "R46 deepens :feature:editor with CitationBib: clean-room BibTeX parser, LaTeX accent normalizer, "
            "and [@key, p. 12] inline citation resolver for IEEE numeric and APA author-year bibliographies.",
        ),
    ],
    ":format:lok": [
        (
            "format/lok/src/main/java/dev/fusion/lok/OdsSheetExtract.kt",
            "R46 deepens :format:lok with OdsSheetExtract: OASIS ODF 1.3 .ods spreadsheet reader/writer "
            "with bounded number-columns-repeated / number-rows-repeated anti-bomb caps.",
        ),
    ],
    ":core:capabilities": [
        (
            "core/capabilities/src/main/java/dev/fusion/capabilities/WasmSandbox.kt",
            "R46 deepens :core:capabilities with WasmSandbox: pure-JVM Chicory-inspired Wasm 1.0 binary "
            "decoder, structured control-flow stack machine, bounds-checked linear memory, and deterministic "
            "instruction fuel trap with zero JNI / 16 KB ELF dependencies.",
        ),
    ],
    ":core:perf": [
        (
            "core/perf/src/main/kotlin/dev/fusion/perf/QuotaBudget.kt",
            "R46 deepens :core:perf with QuotaBudget: hierarchical per-tenant CPU-fuel, heap-bytes, and "
            "IOPS token-bucket enforcer with monotonic refill.",
        ),
    ],
    ":core:storage": [
        (
            "core/storage/src/main/kotlin/dev/fusion/storage/WalFrame.kt",
            "R46 deepens :core:storage with WalFrame: SQLite-style write-ahead log framing with dual-salt "
            "rolling CRC-32C chain and torn-tail crash recovery.",
        ),
    ],
    ":core:commands": [
        (
            "core/commands/src/main/kotlin/dev/fusion/commands/PipeChain.kt",
            "R46 deepens :core:commands with PipeChain: quote-aware command pipeline lexer and bounded "
            "stage executor with zero host shell invocation.",
        ),
    ],
    ":core:model": [
        (
            "core/model/src/main/kotlin/dev/fusion/model/SemVerRange.kt",
            "R46 deepens :core:model with SemVerRange: SemVer 2.0.0 precedence comparator and npm/Cargo "
            "caret/tilde/hyphen/comparator range solver.",
        ),
    ],
    ":core:settings": [
        (
            "core/settings/src/main/kotlin/dev/fusion/settings/SchemaMigrate.kt",
            "R46 deepens :core:settings with SchemaMigrate: shortest-path BFS settings schema migration "
            "planner with atomic rollback on transform failure.",
        ),
    ],
    ":format:native": [
        (
            "format/native/src/main/kotlin/dev/fusion/native/ColumnarPage.kt",
            "R46 deepens :format:native with ColumnarPage: Parquet/Arrow-style columnar page encoder combining "
            "null bitmaps, frame-of-reference delta bit-packing, and UTF-8 dictionary RLE.",
        ),
    ],
    ":format:archive": [
        (
            "format/archive/src/main/kotlin/dev/fusion/archive/TarPax.kt",
            "R46 deepens :format:archive with TarPax: POSIX.1-2001 ustar + PAX extended-header tar reader/writer "
            "with zero-epoch reproducibility and path-traversal/symlink refusal.",
        ),
    ],
    ":privacy": [
        (
            "privacy/src/main/java/dev/fusion/privacy/OnionGraterFilter.kt",
            "R46 deepens :privacy with OnionGraterFilter: Tails onion-grater Tor ControlPort (951) session "
            "command/argument/response filter with 510 Command filtered refusal and UID STREAM scoping.",
        ),
        (
            "privacy/src/main/java/dev/fusion/privacy/MetadataScrub.kt",
            "R46 also adds MetadataScrub: Tails mat2-inspired OOXML/ODF ZIP metadata stripper and in-place "
            "byte-length-preserving PDF Info/XMP metadata redactor.",
        ),
    ],
    ":core:hw": [
        (
            "core/hw/src/main/kotlin/dev/fusion/hw/UsbGuardPolicy.kt",
            "R46 deepens :core:hw with UsbGuardPolicy: USBGuard per-interface policy engine blocking composite "
            "BadUSB (08:* + 03:01:*), lockscreen hotplug HID, and inline hub+HID hardware implants.",
        ),
        (
            "core/hw/src/main/kotlin/dev/fusion/hw/BleHidPairingGate.kt",
            "R46 also adds BleHidPairingGate: Bluetooth Classic (L2CAP 0x0011/0x0013) & BLE HOGP (0x1812) "
            "pairing gate blocking unauthenticated JustWorks BlueDucky CVE-2023-45866 keystroke injection.",
        ),
    ],
    ":core:security": [
        (
            "core/security/src/main/kotlin/dev/fusion/security/MacRandomizer.kt",
            "R46 deepens :core:security with MacRandomizer: RFC 7217 / Tails per-SSID HMAC-SHA256 locally "
            "administered unicast Wi-Fi MAC generator.",
        ),
    ],
    ":app": [
        (
            "app/src/main/java/dev/fusion/shell/KeystrokeJitter.kt",
            "R46 deepens :app with KeystrokeJitter: HIDGuard physiological inter-keystroke cadence classifier "
            "catching synthetic BadUSB DuckyScript bursts plus outbound network chaff scheduler.",
        ),
    ],
    ":feature:browser": [
        (
            "feature/browser/src/main/kotlin/dev/fusion/browser/model/PermissionsPolicyHeader.kt",
            "R46 deepens :feature:browser with PermissionsPolicyHeader: W3C Permissions-Policy structured "
            "header parser and parent-to-iframe intersection inheritance evaluator.",
        ),
    ],
    ":feature:media": [
        (
            "feature/media/src/main/kotlin/dev/fusion/media/HlsPlaylist.kt",
            "R46 deepens :feature:media with HlsPlaylist: RFC 8216 HLS M3U8 master/media parser, HTTPS/AES-128 "
            "validator, and EWMA ABR variant selector.",
        ),
    ],
    ":feature:launcher": [
        (
            "feature/launcher/src/main/kotlin/dev/fusion/launcher/DesktopEntryIni.kt",
            "R46 deepens :feature:launcher with DesktopEntryIni: freedesktop.org Desktop Entry Specification 1.5 "
            "parser, Exec field-code tokenizer, and shell-metacharacter injection blocker.",
        ),
    ],
    ":feature:find": [
        (
            "feature/find/src/main/kotlin/dev/fusion/find/TrigramIndex.kt",
            "R46 deepens :feature:find with TrigramIndex: Russ Cox / CodeSearch positional UTF-8 trigram "
            "inverted index with literal-run extraction and roaring-sorted posting intersection.",
        ),
    ],
    ":feature:files": [
        (
            "feature/files/src/main/kotlin/dev/fusion/filesui/PathConflict.kt",
            "R46 deepens :feature:files with PathConflict: Unicode NFC + case-folding + Windows reserved-device "
            "collision detector and stem-preserving batch auto-renamer.",
        ),
    ],
    ":feature:terminal": [
        (
            "feature/terminal/src/main/kotlin/dev/fusion/terminal/VtScreenBuf.kt",
            "R46 deepens :feature:terminal with VtScreenBuf: ECMA-48 / VT100 terminal screen buffer supporting "
            "CUP/ED/EL/SGR/CRLF/scrollback while dropping OSC 52 clipboard exfiltration.",
        ),
    ],
    ":feature:beam": [
        (
            "feature/beam/src/main/kotlin/dev/fusion/beam/ChunkedTransfer.kt",
            "R46 deepens :feature:beam with ChunkedTransfer: resumable SHA-256 chunked file transfer state "
            "machine with out-of-order assembly and whole-file digest verification.",
        ),
    ],
    ":feature:alerts": [
        (
            "feature/alerts/src/main/kotlin/dev/fusion/alerts/RateWindow.kt",
            "R46 deepens :feature:alerts with RateWindow: Cloudflare-style sliding-window counter rate limiter "
            "and deduplication storm suppressor.",
        ),
    ],
    ":feature:capture": [
        (
            "feature/capture/src/main/kotlin/dev/fusion/capture/EdlCutList.kt",
            "R46 deepens :feature:capture with EdlCutList: SMPTE 258M CMX 3600 timecode math and non-destructive "
            "multi-clip ripple-cut / split timeline engine.",
        ),
    ],
    ":feature:backup": [
        (
            "feature/backup/src/main/kotlin/dev/fusion/backup/RabinChunker.kt",
            "R46 deepens :feature:backup with RabinChunker: Gear rolling-hash content-defined chunking (FastCDC) "
            "engine for shift-resilient deduplicated backups.",
        ),
    ],
    ":feature:together": [
        (
            "feature/together/src/main/kotlin/dev/fusion/together/CrdtLwwMap.kt",
            "R46 deepens :feature:together with CrdtLwwMap: Hybrid Logical Clock (HLC) + state-based "
            "Last-Writer-Wins Element-Map CRDT satisfying commutative/associative/idempotent join laws.",
        ),
    ],
    ":feature:lens": [
        (
            "feature/lens/src/main/kotlin/dev/fusion/lens/BarcodeGs1.kt",
            "R46 deepens :feature:lens with BarcodeGs1: GS1 Mod-10 check-digit engine (EAN-8/UPC-A/EAN-13/GTIN-14/"
            "SSCC-18), GS1-128 AI parser, and 95-module EAN-13 bar-pattern encoder.",
        ),
    ],
    ":feature:messages": [
        (
            "feature/messages/src/main/kotlin/dev/fusion/messages/QuotedPrintable.kt",
            "R46 deepens :feature:messages with QuotedPrintable: RFC 2045 Quoted-Printable + RFC 2047 MIME "
            "encoded-word codec with 76-char soft-wrap and CRLF header-injection refusal.",
        ),
    ],
    ":format:image": [
        (
            "format/image/src/main/kotlin/dev/fusion/format/image/PngMetadataStrip.kt",
            "R46 deepens :format:image with PngMetadataStrip: W3C PNG chunk parser, IEEE 802.3 CRC-32 verifier, "
            "and privacy chunk scrubber removing eXIf/tEXt/zTXt/iTXt/tIME.",
        ),
    ],
    ":feature:automation": [
        (
            "feature/automation/src/main/kotlin/dev/fusion/automation/CronExpr.kt",
            "R46 deepens :feature:automation with CronExpr: POSIX 5-field cron parser and UTC next-fire-epoch "
            "solver implementing Vixie DOM/DOW OR vs wildcard semantics.",
        ),
    ],
    ":feature:vault": [
        (
            "feature/vault/src/main/kotlin/dev/fusion/vault/PassphraseDice.kt",
            "R46 deepens :feature:vault with PassphraseDice: EFF Diceware entropy calculator, NIST SP 800-63B "
            "scorer, and rejection-sampled unbiased index mapper.",
        ),
    ],
    ":feature:notes": [
        (
            "feature/notes/src/main/kotlin/dev/fusion/notes/MarkdownTableFmt.kt",
            "R46 deepens :feature:notes with MarkdownTableFmt: GFM pipe-table parser, column-alignment pretty "
            "printer, escaped-pipe cell splitter, and RFC 4180 CSV converter.",
        ),
    ],
}


def sha(rel: str) -> str:
    return hashlib.sha256((ROOT / rel).read_bytes()).hexdigest()


def main() -> int:
    jp = ROOT / "docs/module-testability.json"
    data = json.loads(jp.read_text())

    owned = {f for items in DEEPEN.values() for f, _ in items} | set(R46_CHECKS) | set(TOUCHED)
    stale: list[tuple[str, str]] = []
    for r in data["rows"]:
        for p, pinned in r["sha256"].items():
            if p not in owned and sha(p) != pinned:
                stale.append((r["module"], p))
    if stale:
        raise SystemExit(
            f"refusing to silently re-pin modified files outside DEEPEN/TOUCHED: {stale}"
        )

    seen: set[str] = set()
    touched_applied: set[str] = set()
    for r in data["rows"]:
        for t_path, t_note in TOUCHED.items():
            if t_path in r["production"] or t_path in r["checks"] or t_path in r["sha256"]:
                touched_applied.add(t_path)
                if t_note and t_note not in r["behavior"]:
                    r["behavior"] = r["behavior"].rstrip() + " " + t_note
                r["sha256"][t_path] = sha(t_path)

        items = DEEPEN.get(r["module"])
        if not items:
            continue
        for prod, note in items:
            if prod not in r["production"]:
                r["production"].append(prod)
            if note not in r["behavior"]:
                r["behavior"] = r["behavior"].rstrip() + " " + note
        for chk in R46_CHECKS:
            if chk not in r["checks"]:
                r["checks"].append(chk)
        if CMD not in r["command"]:
            r["command"] = (r["command"] + "; " + CMD) if r["command"] else CMD
        for p in r["production"] + r["checks"]:
            r["sha256"][p] = sha(p)
        seen.add(r["module"])

    missing_mods = set(DEEPEN) - seen
    if missing_mods:
        raise SystemExit(f"modules not found in census: {sorted(missing_mods)}")
    missing_touched = set(TOUCHED) - touched_applied
    if missing_touched:
        raise SystemExit(f"TOUCHED entries not matched in census: {sorted(missing_touched)}")

    census.validate(data)
    jp.write_text(json.dumps(data, indent=2) + "\n")
    (ROOT / "docs/MODULE-TESTABILITY.md").write_text(census.render(data))
    reloaded = json.loads(jp.read_text())
    census.validate(reloaded)
    assert census.render(reloaded) == (ROOT / "docs/MODULE-TESTABILITY.md").read_text()
    assert (ROOT / "docs/MODULE-TESTABILITY.md").read_text().endswith("\n")
    print(
        f"R46 census deepen OK ({len(DEEPEN)} modules deepened, {len(touched_applied)} touched):",
        json.dumps(census.counts(reloaded)),
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
