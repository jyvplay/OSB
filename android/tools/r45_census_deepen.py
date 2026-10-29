#!/usr/bin/env python3
"""Idempotently deepen 26 module rows in docs/module-testability.json with R45 kernels + 1 TOUCHED file.

Exercises the R44-designed TOUCHED census pattern on `DashEducate.kt` (`educateOutside` span-exclusion
overload) while adding 33 new pure-JVM production kernels across 26 modules (52.00% of 50 modules).
Never modifies docs/module-audit.json (full-spec acceptance remains 0/50).
"""
from __future__ import annotations

import hashlib
import json
from pathlib import Path
import sys

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "tools"))
import module_testability as census

R45_CHECKS = [
    "testing/src/r45/kotlin/dev/fusion/testing/R45Checks.kt",
    "tools/test_r45_tools.py",
]
CMD = "python3 tools/run_r45_contracts.py"

TOUCHED: dict[str, str] = {
    "core/documents/src/main/kotlin/dev/fusion/documents/DashEducate.kt": (
        "R45 extends DashEducate with educateOutside(text, excludedRanges) so inline code and URL spans "
        "stay byte-identical while surrounding prose receives TeX dash/ellipsis education."
    ),
    "tools/run_jvm_contracts.py": "",
}

DEEPEN: dict[str, list[tuple[str, str]]] = {
    ":format:ooxml": [
        (
            "format/ooxml/src/main/kotlin/dev/fusion/ooxml/FormulaEval.kt",
            "R45 deepens again with FormulaEval: deterministic spreadsheet formula parser, topological "
            "dependency DAG evaluator (#REF!/#DIV/0!/#VALUE!/#NAME?/#NUM!/#N/A/#CYCLE!), and reactive "
            "dirty-closure calculator executed on host JVM.",
        ),
        (
            "format/ooxml/src/main/kotlin/dev/fusion/ooxml/FormulaXlsx.kt",
            "R45 also adds FormulaXlsx: bidirectional ECMA-376 .xlsx workbook reader/writer emitting "
            "both <f> formula expressions and pre-evaluated <v> cached values (cross-checked in Python "
            "openpyxl data_only=False and data_only=True) with zero-epoch reproducible ZIP headers.",
        ),
        (
            "format/ooxml/src/main/kotlin/dev/fusion/ooxml/DocxNumbering.kt",
            "R45 also adds DocxNumbering: ECMA-376 WordprocessingML numbering.xml multi-level counter "
            "resolver (decimal, Roman, alpha, ordinal, bullet, lvlRestart, numOverride) on host JVM.",
        ),
        (
            "format/ooxml/src/main/kotlin/dev/fusion/ooxml/DocxPackage.kt",
            "R45 also adds DocxPackage: clean-room ECMA-376 .docx OPC reader/writer supporting headings, "
            "styled runs, lists, and tables while refusing word/vbaProject.bin macro smuggling.",
        ),
    ],
    ":core:documents": [
        (
            "core/documents/src/main/kotlin/dev/fusion/documents/PieceTable.kt",
            "R45 deepens again with PieceTable: immutable append-buffer piece table with UTF-16 surrogate "
            "boundary protection, line/column indexing, and bounded undo/redo history on host JVM.",
        ),
        (
            "core/documents/src/main/kotlin/dev/fusion/documents/ProseEducate.kt",
            "R45 also adds ProseEducate: span-aware prose educator composing SmartQuote + DashEducate "
            "outside fenced code, inline backtick spans, and RFC 3986 URL spans on host JVM.",
        ),
    ],
    ":feature:editor": [
        (
            "feature/editor/src/main/java/dev/fusion/editor/FindReplace.kt",
            "R45 deepens again with FindReplace: literal and bounded-regex search/replace with $0/$1..$9 "
            "capture templates, zero-length progress guard, and whole-word matching on host JVM.",
        ),
    ],
    ":format:lok": [
        (
            "format/lok/src/main/java/dev/fusion/lok/OdtExtract.kt",
            "R45 deepens again with OdtExtract: OASIS ODF 1.3 .odt/.ods bounded ZIP+XML reader enforcing "
            "entry/byte caps and repeat-expansion bomb refusal on host JVM.",
        ),
    ],
    ":format:native": [
        (
            "format/native/src/main/kotlin/dev/fusion/native/RleBitset.kt",
            "R45 deepens again with RleBitset: sorted interval run-length bitset with unsigned LEB128 "
            "binary framing and exact union/intersection/difference algebra on host JVM.",
        ),
    ],
    ":feature:calc": [
        (
            "feature/calc/src/main/kotlin/dev/fusion/calc/FinMath.kt",
            "R45 deepens again with FinMath: closed-form TVM annuity functions (pmt/pv/fv), NPV, and "
            "bracketed Newton-secant IRR solver verified on host JVM.",
        ),
    ],
    ":feature:notes": [
        (
            "feature/notes/src/main/kotlin/dev/fusion/notes/TocTree.kt",
            "R45 deepens again with TocTree: CommonMark ATX heading table-of-contents extractor with "
            "fenced-code exclusion and deterministic duplicate-slug disambiguation on host JVM.",
        ),
    ],
    ":feature:browser": [
        (
            "feature/browser/src/main/kotlin/dev/fusion/browser/model/CspHeader.kt",
            "R45 deepens again with CspHeader: W3C CSP Level 3 parser/auditor scoring unsafe-inline, "
            "unsafe-eval, wildcard origins, missing object-src, and strict-dynamic nonce overrides.",
        ),
        (
            "feature/browser/src/main/kotlin/dev/fusion/browser/model/PslGate.kt",
            "R45 also adds PslGate: Public Suffix List ICann/wildcard/exception eTLD+1 registrable-domain "
            "resolver blocking supercookie domain attributes and partitioning storage keys on host JVM.",
        ),
    ],
    ":core:security": [
        (
            "core/security/src/main/kotlin/dev/fusion/security/CtSctPolicy.kt",
            "R45 deepens again with CtSctPolicy: RFC 6962 Certificate Transparency multi-operator SCT "
            "quorum evaluator and distrusted-CA root/intermediate gate (CVE-2026-113470 class).",
        ),
        (
            "core/security/src/main/kotlin/dev/fusion/security/DmaGuardAudit.kt",
            "R45 also adds DmaGuardAudit: fail-closed Thunderbolt/USB4/PCIe DMA posture evaluator checking "
            "IOMMU strict translation, UEFI DMAR/IVRS pre-boot protection, and lock-state port policy.",
        ),
    ],
    ":core:hw": [
        (
            "core/hw/src/main/kotlin/dev/fusion/hw/HidReportAudit.kt",
            "R45 deepens again with HidReportAudit: USB HID 1.11 report descriptor bytecode parser and "
            "inter-keystroke timing anomaly detector flagging composite mouse+keyboard keystroke injectors.",
        ),
    ],
    ":privacy": [
        (
            "privacy/src/main/java/dev/fusion/privacy/TailsCadence.kt",
            "R45 deepens again with TailsCadence: Tails 7.15.1 freshness/emergency-advisory cadence "
            "evaluator and Tails 7.14+ 60-second hard-deadline amnesic shutdown planner on host JVM.",
        ),
        (
            "privacy/src/main/java/dev/fusion/privacy/OverlayTouchGuard.kt",
            "R45 also adds OverlayTouchGuard: anti-tapjacking and screen-capture input gate combining "
            "obscured-touch flags, cross-UID overlay presence, HIDE_OVERLAY_WINDOWS, and FLAG_SECURE.",
        ),
    ],
    ":app": [
        (
            "app/src/main/java/dev/fusion/shell/TileLabelFit.kt",
            "R45 deepens again with TileLabelFit: word-boundary 2-line tile label wrapper and 48dp-floor "
            "responsive dock overflow planner repairing r24c mid-word truncation and 390dp 6th-slot clip.",
        ),
    ],
    ":core:designsystem": [
        (
            "core/designsystem/src/main/kotlin/dev/fusion/designsystem/SpringStep.kt",
            "R45 deepens again with SpringStep: exact analytical damped harmonic oscillator solver across "
            "underdamped, critically damped, and overdamped regimes with reduced-motion snap on host JVM.",
        ),
    ],
    ":core:windowing": [
        (
            "core/windowing/src/main/kotlin/dev/fusion/windowing/WorkAreaFit.kt",
            "R45 deepens again with WorkAreaFit: multi-cutout/IME/dock work-area inset resolver and "
            "off-screen floating-window title-bar rescue clamp on host JVM.",
        ),
    ],
    ":core:files": [
        (
            "core/files/src/main/kotlin/dev/fusion/files/MerkleTree.kt",
            "R45 deepens again with MerkleTree: RFC 6962 §2.1 domain-separated (0x00 leaf / 0x01 node) "
            "SHA-256 Merkle audit tree with O(log N) inclusion proof generation and verification.",
        ),
    ],
    ":feature:settings": [
        (
            "feature/settings/src/main/kotlin/dev/fusion/settingsui/EnvOverride.kt",
            "R45 deepens again with EnvOverride: layered settings precedence resolver (DEFAULT < PROFILE "
            "< WORKSPACE < SESSION_EPHEMERAL < POLICY_LOCK) refusing overrides on policy-locked keys.",
        ),
    ],
    ":format:psd": [
        (
            "format/psd/src/main/kotlin/dev/fusion/psd/PsdLayerRecords.kt",
            "R45 deepens again with PsdLayerRecords: Adobe PSD Layer & Mask Information record header "
            "parser extracting bounds, blend modes, opacity, Pascal names, and clipping flags on host JVM.",
        ),
    ],
    ":cast:dlna": [
        (
            "cast/dlna/src/main/kotlin/dev/fusion/cast/dlna/SsdpHeader.kt",
            "R45 deepens again with SsdpHeader: UPnP 2.0 SSDP M-SEARCH/NOTIFY parser enforcing private "
            "LAN-only LOCATION URLs and rejecting WAN SSRF/file/cleartext traps on host JVM.",
        ),
    ],
    ":cast:castv2": [
        (
            "cast/castv2/src/main/kotlin/dev/fusion/castv2/CastTxtProps.kt",
            "R45 deepens again with CastTxtProps: _googlecast._tcp mDNS TXT property decoder extracting "
            "device name, model, capabilities bitmask, receiver status, and UUID on host JVM.",
        ),
    ],
    ":cast:airplay": [
        (
            "cast/airplay/src/main/kotlin/dev/fusion/airplay/TxtRecord.kt",
            "R45 deepens again with TxtRecord: RFC 6763 §6 DNS-SD length-prefixed TXT wire encoder and "
            "case-insensitive first-wins decoder on host JVM.",
        ),
    ],
    ":feature:accessibility": [
        (
            "feature/accessibility/src/main/kotlin/dev/fusion/a11y/KeyChord.kt",
            "R45 deepens again with KeyChord: canonical modifier+key chord parser, normalizer, and "
            "conflict-refusing accessible shortcut registry on host JVM.",
        ),
    ],
    ":feature:maps": [
        (
            "feature/maps/src/main/kotlin/dev/fusion/maps/UtmGrid.kt",
            "R45 deepens again with UtmGrid: WGS84 Transverse Mercator UTM forward/inverse projection "
            "with Norway 32V and Svalbard 31X..37X zone exceptions verified to <1 mm round-trip.",
        ),
    ],
    ":feature:vault": [
        (
            "feature/vault/src/main/kotlin/dev/fusion/vault/HkdfExpand.kt",
            "R45 deepens again with HkdfExpand: RFC 5869 HKDF-SHA256 Extract-and-Expand key derivation "
            "verified against RFC 5869 Appendix A.1-A.3 test vectors on host JVM.",
        ),
    ],
    ":feature:continuity": [
        (
            "feature/continuity/src/main/kotlin/dev/fusion/continuity/ClipMime.kt",
            "R45 deepens again with ClipMime: RFC 2045 MIME offer-vs-accept negotiator enforcing payload "
            "caps and stripping dangerous executable/HTML script clipboard types in strict mode.",
        ),
    ],
    ":feature:automation": [
        (
            "feature/automation/src/main/kotlin/dev/fusion/automation/DagTopo.kt",
            "R45 deepens again with DagTopo: deterministic Kahn lexicographical topological task planner "
            "with parallel execution waves and explicit cycle witness reporting on host JVM.",
        ),
    ],
    ":core:index": [
        (
            "core/index/src/main/kotlin/dev/fusion/index/Bm25Rank.kt",
            "R45 deepens again with Bm25Rank: Okapi BM25 Robertson-Sparck Jones probabilistic full-text "
            "document scorer with deterministic docId tie-breaking on host JVM.",
        ),
    ],
}


def sha(rel: str) -> str:
    return hashlib.sha256((ROOT / rel).read_bytes()).hexdigest()


def main() -> int:
    jp = ROOT / "docs/module-testability.json"
    data = json.loads(jp.read_text())

    owned = {f for items in DEEPEN.values() for f, _ in items} | set(R45_CHECKS) | set(TOUCHED)
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
        for chk in R45_CHECKS:
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
    if "core/documents/src/main/kotlin/dev/fusion/documents/DashEducate.kt" not in touched_applied:
        raise SystemExit("DashEducate.kt TOUCHED entry was not matched in census")

    census.validate(data)
    jp.write_text(json.dumps(data, indent=2) + "\n")
    (ROOT / "docs/MODULE-TESTABILITY.md").write_text(census.render(data))
    reloaded = json.loads(jp.read_text())
    census.validate(reloaded)
    assert census.render(reloaded) == (ROOT / "docs/MODULE-TESTABILITY.md").read_text()
    assert (ROOT / "docs/MODULE-TESTABILITY.md").read_text().endswith("\n")
    print(
        f"R45 census deepen OK ({len(DEEPEN)} modules deepened, {len(touched_applied)} touched):",
        json.dumps(census.counts(reloaded)),
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
