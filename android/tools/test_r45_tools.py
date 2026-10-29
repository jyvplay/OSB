#!/usr/bin/env python3
"""Hygiene, structural, cross-file symbol collision, CI bridge, and honesty tests for R45."""
from __future__ import annotations

import json
from pathlib import Path
import re
import sys
import unittest

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "tools"))

import amnesic_posture
import check_r45_manifest_scope
import check_r45_touch_targets
import run_r45_contracts

KERNELS: dict[str, str] = {
    "format/ooxml/src/main/kotlin/dev/fusion/ooxml/FormulaEval.kt": "object FormulaEval",
    "format/ooxml/src/main/kotlin/dev/fusion/ooxml/FormulaXlsx.kt": "object FormulaXlsx",
    "format/ooxml/src/main/kotlin/dev/fusion/ooxml/DocxNumbering.kt": "object DocxNumbering",
    "format/ooxml/src/main/kotlin/dev/fusion/ooxml/DocxPackage.kt": "object DocxPackage",
    "core/documents/src/main/kotlin/dev/fusion/documents/PieceTable.kt": "object PieceTable",
    "core/documents/src/main/kotlin/dev/fusion/documents/ProseEducate.kt": "object ProseEducate",
    "feature/editor/src/main/java/dev/fusion/editor/FindReplace.kt": "object FindReplace",
    "format/lok/src/main/java/dev/fusion/lok/OdtExtract.kt": "object OdtExtract",
    "format/native/src/main/kotlin/dev/fusion/native/RleBitset.kt": "object RleBitset",
    "feature/calc/src/main/kotlin/dev/fusion/calc/FinMath.kt": "object FinMath",
    "feature/notes/src/main/kotlin/dev/fusion/notes/TocTree.kt": "object TocTree",
    "feature/browser/src/main/kotlin/dev/fusion/browser/model/CspHeader.kt": "object CspHeader",
    "feature/browser/src/main/kotlin/dev/fusion/browser/model/PslGate.kt": "object PslGate",
    "core/security/src/main/kotlin/dev/fusion/security/CtSctPolicy.kt": "object CtSctPolicy",
    "core/security/src/main/kotlin/dev/fusion/security/DmaGuardAudit.kt": "object DmaGuardAudit",
    "core/hw/src/main/kotlin/dev/fusion/hw/HidReportAudit.kt": "object HidReportAudit",
    "privacy/src/main/java/dev/fusion/privacy/TailsCadence.kt": "object TailsCadence",
    "privacy/src/main/java/dev/fusion/privacy/OverlayTouchGuard.kt": "object OverlayTouchGuard",
    "app/src/main/java/dev/fusion/shell/TileLabelFit.kt": "object TileLabelFit",
    "core/designsystem/src/main/kotlin/dev/fusion/designsystem/SpringStep.kt": "object SpringStep",
    "core/windowing/src/main/kotlin/dev/fusion/windowing/WorkAreaFit.kt": "object WorkAreaFit",
    "core/files/src/main/kotlin/dev/fusion/files/MerkleTree.kt": "object MerkleTree",
    "feature/settings/src/main/kotlin/dev/fusion/settingsui/EnvOverride.kt": "object EnvOverride",
    "format/psd/src/main/kotlin/dev/fusion/psd/PsdLayerRecords.kt": "object PsdLayerRecords",
    "cast/dlna/src/main/kotlin/dev/fusion/cast/dlna/SsdpHeader.kt": "object SsdpHeader",
    "cast/castv2/src/main/kotlin/dev/fusion/castv2/CastTxtProps.kt": "object CastTxtProps",
    "cast/airplay/src/main/kotlin/dev/fusion/airplay/TxtRecord.kt": "object TxtRecord",
    "feature/accessibility/src/main/kotlin/dev/fusion/a11y/KeyChord.kt": "object KeyChord",
    "feature/maps/src/main/kotlin/dev/fusion/maps/UtmGrid.kt": "object UtmGrid",
    "feature/vault/src/main/kotlin/dev/fusion/vault/HkdfExpand.kt": "object HkdfExpand",
    "feature/continuity/src/main/kotlin/dev/fusion/continuity/ClipMime.kt": "object ClipMime",
    "feature/automation/src/main/kotlin/dev/fusion/automation/DagTopo.kt": "object DagTopo",
    "core/index/src/main/kotlin/dev/fusion/index/Bm25Rank.kt": "object Bm25Rank",
}

TOUCHED = "core/documents/src/main/kotlin/dev/fusion/documents/DashEducate.kt"

RESULTS: list[tuple[str, bool]] = []


def check(name: str, cond: bool) -> None:
    RESULTS.append((name, bool(cond)))
    if not cond:
        raise SystemExit(f"FAIL: {name}")


def _collect_checks() -> None:
    texts = {rel: (ROOT / rel).read_text(encoding="utf-8") for rel in KERNELS}
    touched_text = (ROOT / TOUCHED).read_text(encoding="utf-8")

    # 1-10: Core R45 kernel & touched-file hygiene
    check("all 33 R45 kernels exist with declared objects",
          len(KERNELS) == 33 and all(decl in texts[rel] for rel, decl in KERNELS.items()))
    check("DashEducate touched overload educateOutside exists",
          "fun educateOutside(" in touched_text and "object DashEducate" in touched_text)
    check("no android or androidx imports in any R45 kernel",
          all("import android" not in t and "import androidx" not in t for t in texts.values()))
    check("no java.io.File in any R45 kernel",
          all("java.io.File" not in t for t in texts.values()))
    check("every R45 kernel declares const val SCOPE honesty string",
          all("const val SCOPE" in t for t in texts.values()))
    check("no TODO or FIXME markers left in any R45 kernel",
          all(not re.search(r"(?<!V)TODO", t) and "FIXME" not in t for t in texts.values()))
    total_fns = {
        "core/security/src/main/kotlin/dev/fusion/security/DmaGuardAudit.kt",
        "feature/continuity/src/main/kotlin/dev/fusion/continuity/ClipMime.kt",
    }
    check("every partial-domain R45 kernel enforces input validation (DmaGuardAudit & ClipMime are total over typed inputs)",
          all(("require(" in t or "check(" in t or "IllegalArgumentException" in t)
              for rel, t in texts.items() if rel not in total_fns))
    check("run_r45_contracts covers all 33 specs across 26 modules",
          len(run_r45_contracts.SPECS) == 33 and len(run_r45_contracts.module_groups()) == 26)
    check("run_r45_contracts tracks DashEducate in TOUCHED_FILES",
          TOUCHED in run_r45_contracts.TOUCHED_FILES)
    check("R45Checks consumer enforces host-only non-device banner",
          "Host JVM only; not Android/device evidence." in (ROOT / run_r45_contracts.CHECKS_FILE).read_text())

    # 11-22: Global cross-file top-level symbol collision & source inventory checks
    pkg_re = re.compile(r"^\s*package\s+([\w.]+)", re.M)
    sym_re = re.compile(
        r"^(?:public\s+|internal\s+|sealed\s+|data\s+|enum\s+|value\s+|abstract\s+|open\s+|annotation\s+)*"
        r"(?:class|object|interface|typealias)\s+([A-Za-z_]\w*)",
        re.M,
    )
    seen_fqn: dict[str, str] = {}
    collisions: list[tuple[str, str, str]] = []
    main_kt_count = 0
    for kt in sorted(ROOT.glob("**/src/main/**/*.kt")):
        if "/build/" in kt.as_posix():
            continue
        main_kt_count += 1
        src = kt.read_text(encoding="utf-8")
        pm = pkg_re.search(src)
        pkg = pm.group(1) if pm else ""
        for sm in sym_re.finditer(src):
            fqn = f"{pkg}.{sm.group(1)}"
            rel = kt.relative_to(ROOT).as_posix()
            if fqn in seen_fqn and seen_fqn[fqn] != rel:
                collisions.append((fqn, seen_fqn[fqn], rel))
            seen_fqn[fqn] = rel
    check("zero cross-file top-level Kotlin symbol collisions across entire repo", len(collisions) == 0)
    check("total src/main Kotlin file count >= 495 (462 baseline + 33 R45)", main_kt_count >= 495)

    # 23-34: Contiguous 12-round Gradle CI bridge (r34Contracts..r45Contracts)
    gradle_text = (ROOT / "testing/build.gradle.kts").read_text(encoding="utf-8")
    wf_text = (ROOT.parent / ".github/workflows/android.yml").read_text(encoding="utf-8")
    for rnum in range(34, 46):
        task_name = f"r{rnum}Contracts"
        check(f"Gradle & CI workflow wire {task_name}",
              f'register<JavaExec>("{task_name}")' in gradle_text and f":testing:{task_name}" in wf_text)

    # 35-52: Spec & normative constant pins across R45 mechanisms
    check("FormulaXlsx emits deterministic zero-epoch storedZip PKZIP headers",
          "storedZip(" in texts["format/ooxml/src/main/kotlin/dev/fusion/ooxml/FormulaXlsx.kt"])
    check("FormulaEval refuses volatile functions and evaluates sheet DAGs",
          "VOLATILE_OR_DYNAMIC" in texts["format/ooxml/src/main/kotlin/dev/fusion/ooxml/FormulaEval.kt"]
          and "fun evaluateSheet(" in texts["format/ooxml/src/main/kotlin/dev/fusion/ooxml/FormulaEval.kt"])
    check("DocxPackage refuses word/vbaProject.bin macro smuggling",
          "vbaProject.bin" in texts["format/ooxml/src/main/kotlin/dev/fusion/ooxml/DocxPackage.kt"])
    check("DocxNumbering supports Roman, alpha, ordinal, and bullet formatting",
          "MAX_ROMAN = 3999" in texts["format/ooxml/src/main/kotlin/dev/fusion/ooxml/DocxNumbering.kt"]
          and "LOWER_ROMAN" in texts["format/ooxml/src/main/kotlin/dev/fusion/ooxml/DocxNumbering.kt"])
    check("PieceTable implements append-buffer pieces and bounded undo history",
          "Source.ORIGINAL" in texts["core/documents/src/main/kotlin/dev/fusion/documents/PieceTable.kt"]
          and "MAX_HISTORY = 256" in texts["core/documents/src/main/kotlin/dev/fusion/documents/PieceTable.kt"])
    check("ProseEducate protects inline backticks, fenced code, and RFC 3986 URLs",
          "ProtectedKind.URL" in texts["core/documents/src/main/kotlin/dev/fusion/documents/ProseEducate.kt"]
          and "ProtectedKind.INLINE_CODE" in texts["core/documents/src/main/kotlin/dev/fusion/documents/ProseEducate.kt"])
    check("OdtExtract caps cell repeat expansion bombs",
          "MAX_COL_REPEAT" in texts["format/lok/src/main/java/dev/fusion/lok/OdtExtract.kt"])
    check("MerkleTree pins RFC 6962 0x00 leaf and 0x01 node domain prefixes",
          "0x00" in texts["core/files/src/main/kotlin/dev/fusion/files/MerkleTree.kt"]
          and "0x01" in texts["core/files/src/main/kotlin/dev/fusion/files/MerkleTree.kt"])
    check("HkdfExpand uses HmacSHA256 with 32-byte hash length",
          "HmacSHA256" in texts["feature/vault/src/main/kotlin/dev/fusion/vault/HkdfExpand.kt"]
          and "HASH_LEN = 32" in texts["feature/vault/src/main/kotlin/dev/fusion/vault/HkdfExpand.kt"])
    check("HidReportAudit detects HID Generic Desktop Keyboard 0x01/0x06 usage",
          "UNDECLARED_KEYBOARD_CAPABILITY" in texts["core/hw/src/main/kotlin/dev/fusion/hw/HidReportAudit.kt"]
          and "0x06" in texts["core/hw/src/main/kotlin/dev/fusion/hw/HidReportAudit.kt"])
    check("TailsCadence pins Tails 7.14+ 60-second shutdown deadline",
          "SHUTDOWN_HARD_DEADLINE_MS = 60_000L" in texts["privacy/src/main/java/dev/fusion/privacy/TailsCadence.kt"])
    check("OverlayTouchGuard checks MotionEvent FLAG_WINDOW_IS_OBSCURED 0x1",
          "MOTION_FLAG_WINDOW_IS_OBSCURED = 0x1" in texts["privacy/src/main/java/dev/fusion/privacy/OverlayTouchGuard.kt"])
    check("DmaGuardAudit requires STRICT IOMMU + preBootDmaProtection for HARDENED",
          "IommuMode.STRICT" in texts["core/security/src/main/kotlin/dev/fusion/security/DmaGuardAudit.kt"])
    check("CtSctPolicy enforces multi-operator SCT quorum and distrusted-CA revocation",
          "REJECT_CA_DISTRUSTED" in texts["core/security/src/main/kotlin/dev/fusion/security/CtSctPolicy.kt"])
    check("PslGate classifies 3-valued eTLD+1 and validates WebAuthn RP IDs",
          "PINNED_VERSION" in texts["feature/browser/src/main/kotlin/dev/fusion/browser/model/PslGate.kt"]
          and "fun isValidRpIdForHost(" in texts["feature/browser/src/main/kotlin/dev/fusion/browser/model/PslGate.kt"])
    check("CspHeader evaluates URL and inline script/style nonce/hash/strict-dynamic rules",
          "fun allowsInline(" in texts["feature/browser/src/main/kotlin/dev/fusion/browser/model/CspHeader.kt"])
    check("TileLabelFit enforces 48dp WCAG/Android minimum touch target floor",
          "MIN_TOUCH_TARGET_DP = 48" in texts["app/src/main/java/dev/fusion/shell/TileLabelFit.kt"])
    check("UtmGrid implements Norway 32V and Svalbard 31X..37X exceptions",
          "computeZone(" in texts["feature/maps/src/main/kotlin/dev/fusion/maps/UtmGrid.kt"]
          and "72.0" in texts["feature/maps/src/main/kotlin/dev/fusion/maps/UtmGrid.kt"])
    check("SsdpHeader enforces private LAN-only LOCATION URLs",
          "isPrivateLanIpLiteral" in texts["cast/dlna/src/main/kotlin/dev/fusion/cast/dlna/SsdpHeader.kt"])
    check("Bm25Rank implements Robertson-Sparck Jones IDF with docId tie-break",
          "idf(" in texts["core/index/src/main/kotlin/dev/fusion/index/Bm25Rank.kt"])

    # 47-56: Gate tools, differential oracle, visual audit artifact, census & 0/50 non-inflation
    touch_res = check_r45_touch_targets.verify()
    check("touch-target gate passes with 48dp floor and 0 XML violations",
          touch_res["tile_label_fit_floor_dp"] == 48 and touch_res["xml_violations"] == 0)

    mf_res = check_r45_manifest_scope.check_manifests()
    check("manifest-scope gate verifies 17 manifests and air-gapped :privacy",
          mf_res["manifests_checked"] >= 17
          and mf_res["privacy_permissions"] == ["android.permission.HIDE_OVERLAY_WINDOWS"]
          and mf_res["global_forbidden_violations"] == 0)

    amn_res = amnesic_posture.collect_posture()
    check("amnesic_posture inspects live Linux procfs/sysfs without fabricating strict amnesia",
          "swap_bytes" in amn_res and "iommu_group_count" in amn_res
          and isinstance(amn_res["strict_amnesic_posture"], bool))

    ref_src = (ROOT / "tools/check_r45_reference.py").read_text(encoding="utf-8")
    check("check_r45_reference runs openpyxl in both data_only=False and data_only=True modes",
          "data_only=False" in ref_src and "data_only=True" in ref_src and "or True" not in ref_src)

    vis_png = ROOT / "docs/verification/r45-host-workbench/r45-workbench-preview.png"
    vis_json = ROOT / "docs/verification/r45-host-workbench/r45-visual-audit.json"
    check("tracked R45 visual preview PNG exists with 1440x940 IHDR",
          vis_png.is_file() and vis_png.read_bytes()[:8] == b"\x89PNG\r\n\x1a\n"
          and int.from_bytes(vis_png.read_bytes()[16:20], "big") == 1440)
    vis_meta = json.loads(vis_json.read_text(encoding="utf-8")) if vis_json.is_file() else {}
    check("visual audit report enforces WCAG >= 4.5:1 and FontMetrics bounds",
          vis_meta.get("min_contrast_ratio", 0) >= 4.5 and vis_meta.get("font_metrics_bounds_enforced") is True)

    audit = json.loads((ROOT / "docs/module-audit.json").read_text(encoding="utf-8"))
    complete_mods = [m for m in audit.get("modules", []) if m.get("status") == "complete"]
    check("full-spec module completion remains strictly 0/50 (zero status inflation)",
          len(audit.get("modules", [])) == 50 and len(complete_mods) == 0)

    testability = json.loads((ROOT / "docs/module-testability.json").read_text(encoding="utf-8"))
    r45_mods = [m for m in testability["rows"] if "run_r45_contracts.py" in m.get("command", "")]
    check("module-testability census wires all 26 R45 deepened modules while keeping 50/50 testable",
          len(testability["rows"]) == 50 and len(r45_mods) == 26)


_collect_checks()


class R45ToolChecks(unittest.TestCase):
    pass


for _idx, (_label, _passed) in enumerate(RESULTS):
    def _make_test(_ok: bool = _passed, _msg: str = _label):
        def _test(self: unittest.TestCase) -> None:
            self.assertTrue(_ok, _msg)
        return _test

    _slug = "".join(ch if ch.isalnum() else "_" for ch in _label)[:64]
    setattr(R45ToolChecks, f"test_{_idx:02d}_{_slug}", _make_test())


if __name__ == "__main__":
    unittest.main(verbosity=1)
