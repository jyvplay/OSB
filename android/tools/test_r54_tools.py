#!/usr/bin/env python3
"""R54 tooling & ledger invariants (host-only; no Android device claim)."""
from __future__ import annotations

import json
import re
import sys
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "tools"))


class R54ToolTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls) -> None:
        cls.rows = json.loads((ROOT / "docs/feature-register.json").read_text(encoding="utf-8"))
        cls.audit = json.loads((ROOT / "docs/module-audit.json").read_text(encoding="utf-8"))
        cls.testability = json.loads((ROOT / "docs/module-testability.json").read_text(encoding="utf-8"))
        cls.ca = json.loads((ROOT / "docs/class-a-readiness-r54.json").read_text(encoding="utf-8"))
        cls.build = (ROOT / "testing/build.gradle.kts").read_text(encoding="utf-8")
        cls.ci = (ROOT.parent / ".github/workflows/android.yml").read_text(encoding="utf-8")

    def test_feature_register_keeps_231_rows_and_zero_verified(self) -> None:
        self.assertEqual(231, len(self.rows))
        self.assertEqual(0, sum(1 for r in self.rows if r["status"] == "verified"))

    def test_r54_tags_twelve_rows_without_status_inflation(self) -> None:
        tagged = [r for r in self.rows if "jvm:R54Checks" in r["tests"]]
        self.assertGreaterEqual(len(tagged), 12)
        for r in tagged:
            self.assertEqual("partial_unverified", r["status"])
        # most rows now carry an executed JVM suite tag
        with_jvm = sum(1 for r in self.rows if any(t.startswith("jvm:") for t in r["tests"]))
        self.assertGreaterEqual(with_jvm, 204)

    def test_module_audit_keeps_zero_complete_modules(self) -> None:
        self.assertEqual(50, len(self.audit["modules"]))
        self.assertEqual(0, sum(1 for m in self.audit["modules"] if m["status"] == "complete"))
        self.assertFalse(any(m.get("accepted") is True for m in self.audit["modules"]))
        self.assertIn("r54_note", self.audit)

    def test_testability_census_wires_all_fifty_modules_to_r54(self) -> None:
        self.assertEqual(50, len(self.testability["rows"]))
        wired = [r for r in self.testability["rows"] if "run_r54_contracts.py" in r.get("command", "")]
        self.assertEqual(50, len(wired))
        for r in self.testability["rows"]:
            self.assertIn("run_r53_contracts.py", r["command"])

    def test_class_a_readiness_r54_is_self_consistent(self) -> None:
        verdict = self.ca["runtime_honesty_verdict"]
        self.assertEqual("R54", self.ca["round"])
        self.assertEqual(0, verdict["full_spec_modules_accepted"])
        self.assertEqual(50, verdict["testable_subset_modules_verified"])
        self.assertFalse(verdict["can_claim_full_class_a_in_host_sandbox"])
        self.assertEqual(35, verdict["r54_hardware_vectors_total"])
        self.assertEqual(7, verdict["r54_recall_methods"])

    def test_not_implemented_rows_were_not_promoted(self) -> None:
        not_impl = {r["id"] for r in self.rows if r["status"] == "not_implemented"}
        self.assertTrue({"FM-12", "TD-05", "CM-01"} <= not_impl)
        for r in self.rows:
            if "jvm:R54Checks" in r["tests"]:
                self.assertNotIn(r["id"], not_impl)

    def test_r54_new_kernels_are_pure_jvm(self) -> None:
        kernels = [
            "core/documents/src/main/kotlin/dev/fusion/documents/OptimalParagraphComposer.kt",
            "core/documents/src/main/kotlin/dev/fusion/documents/LiangHyphenationTrieEngine.kt",
            "core/documents/src/main/kotlin/dev/fusion/documents/OpenTypeSfntMetricsParser.kt",
            "core/documents/src/main/kotlin/dev/fusion/documents/PeritextRichSpanCrdtEngine.kt",
            "core/windowing/src/main/kotlin/dev/fusion/windowing/ContextMenuKeyboardTypeaheadSubmenuEngine.kt",
            "core/designsystem/src/main/kotlin/dev/fusion/designsystem/BespokeViewModeAnimatedMorphSpec.kt",
            "core/commands/src/main/kotlin/dev/fusion/commands/UndoToastBatchCoalescerController.kt",
            "format/ooxml/src/main/kotlin/dev/fusion/ooxml/DocxBidirectionalPackageCodec.kt",
            "format/ooxml/src/main/kotlin/dev/fusion/ooxml/SpreadsheetSimplexMilpSolver.kt",
            "format/ooxml/src/main/kotlin/dev/fusion/ooxml/Decimal128FinancialEngine.kt",
            "format/ooxml/src/main/kotlin/dev/fusion/ooxml/Sqlite3BtreeTableFileReader.kt",
            "format/lok/src/main/kotlin/dev/fusion/lok/OdtBidirectionalPackageCodec.kt",
            "feature/media/src/main/kotlin/dev/fusion/media/RecallFlacRfc9639LosslessArchiver.kt",
            "feature/browser/src/main/kotlin/dev/fusion/browser/model/RecallTier6MseSourceBufferTap.kt",
            "privacy/src/main/kotlin/dev/fusion/privacy/MlKem768NttPolynomialEngine.kt",
            "privacy/src/main/kotlin/dev/fusion/privacy/TorProp340PackedRelayCellCodec.kt",
            "core/security/src/main/kotlin/dev/fusion/security/WebAuthnLevel3AuthDataVerifier.kt",
            "core/hw/src/main/kotlin/dev/fusion/hw/UsbPdStructuredVdmAltModeGuard.kt",
            "core/hw/src/main/kotlin/dev/fusion/hw/DvfsHertzbleedFrequencyNoiseGuard.kt",
            "core/hw/src/main/kotlin/dev/fusion/hw/GpuPixelTimingPixnappingShield.kt",
            "core/hw/src/main/kotlin/dev/fusion/hw/WifiCsiKeystrokeSensingMitigator.kt",
            "core/hw/src/main/kotlin/dev/fusion/hw/HallMagnetometerCoverSpoofGuard.kt",
        ]
        self.assertEqual(22, len(kernels))
        for rel in kernels:
            p = ROOT / rel
            self.assertTrue(p.is_file(), rel)
            text = p.read_text(encoding="utf-8")
            self.assertNotIn("import android.", text, rel)
            self.assertNotIn("import androidx.", text, rel)
            self.assertIn("SPDX-License-Identifier: Apache-2.0", text, rel)
            self.assertGreater(len(text.splitlines()), 40, rel)

    def test_r54_compose_files_registered_in_syntax_gate(self) -> None:
        import check_r26_android_syntax as gate

        ui = [
            "feature/editor/src/main/kotlin/dev/fusion/editor/WriterTypographyInspectorCard.kt",
            "feature/calc/src/main/kotlin/dev/fusion/calc/SpreadsheetOptimizerPanel.kt",
            "feature/browser/src/main/kotlin/dev/fusion/browser/RecallSevenMethodStudioSheet.kt",
        ]
        for rel in ui:
            self.assertIn(rel, gate.FILES)
            self.assertTrue((ROOT / rel).is_file())
            self.assertIn("@Composable", (ROOT / rel).read_text(encoding="utf-8"))
        self.assertGreaterEqual(len(gate.FILES), 62)

    def test_existing_r49_breaker_preserved(self) -> None:
        legacy = (ROOT / "core/documents/src/main/kotlin/dev/fusion/documents/KnuthPlassLineBreaker.kt").read_text(encoding="utf-8")
        self.assertIn("object KnuthPlassLineBreaker", legacy)
        self.assertIn("usedFallbackGreedy", legacy)
        self.assertIn("data class Config", legacy)

    def test_touched_files_keep_r53_upgrades(self) -> None:
        formula = (ROOT / "format/ooxml/src/main/kotlin/dev/fusion/ooxml/FormulaEval.kt").read_text(encoding="utf-8")
        self.assertIn("350 pure functions", formula)
        self.assertIn("300 pure functions R53", formula)
        self.assertIn('"HILBERT"', formula)
        self.assertIn('"NORM.INV"', formula)
        spill = (ROOT / "format/ooxml/src/main/kotlin/dev/fusion/ooxml/SpillSheetEngine.kt").read_text(encoding="utf-8")
        self.assertIn("56 spilled array", spill)
        self.assertIn("48 R53", spill)
        wasm = (ROOT / "core/capabilities/src/main/java/dev/fusion/capabilities/WasmSandbox.kt").read_text(encoding="utf-8")
        self.assertIn("MAX_MEMORIES_PER_MODULE = 8", wasm)
        self.assertIn("0x0E -> {", wasm)  # br_table added in R54
        self.assertIn("0x42 -> {", wasm)  # i64.const added in R54
        router = (ROOT / "feature/browser/src/main/kotlin/dev/fusion/browser/model/RecallMethodEscalationRouter.kt").read_text(encoding="utf-8")
        self.assertIn("TIER_5_WARC_ISO28500_BUNDLE", router)
        self.assertIn("METHOD_7_MSE_SOURCEBUFFER_FLAC_TAP", router)
        coord = (ROOT / "app/src/main/java/dev/fusion/shell/RecallSessionCoordinator.kt").read_text(encoding="utf-8")
        self.assertIn("(1..7)", coord)

    def test_gradle_and_ci_register_r54_contract_task(self) -> None:
        self.assertIn('tasks.register<JavaExec>("r54Contracts")', self.build)
        self.assertIn('val r54 by sourceSets.creating', self.build)
        self.assertIn(":testing:r54Contracts", self.ci)
        self.assertIn("python3 tools/check_r54_reference.py", self.ci)
        rounds = (
            [f"r{n}Contracts" for n in range(13, 25)]
            + ["r24cContracts", "r25PngContracts"]
            + [f"r{n}Contracts" for n in range(26, 55)]
        )
        self.assertEqual(43, len(rounds))
        for task in rounds:
            self.assertIn(f'"{task}"', self.build)

    def test_artifacts_and_metrics_when_present(self) -> None:
        art = ROOT / "testing/build/r54/artifacts"
        if not art.is_dir():
            self.skipTest("host artifacts not built in this checkout")
        for name in (
            "r54-writer-roundtrip.docx", "r54-writer-roundtrip.odt", "r54-writer-roundtrip.rtf",
            "r54-calc-ledger.sqlite", "r54-recall-lossless.flac", "r54-recall-mse-tap.mp4",
            "r54-webauthn-authdata.bin", "r54-prop340-cell.bin", "r54-synthetic-font.ttf",
            "r54-summary.json",
        ):
            p = art / name
            self.assertTrue(p.is_file() and p.stat().st_size > 0, name)
        summary = json.loads((art / "r54-summary.json").read_text(encoding="utf-8"))
        self.assertGreaterEqual(summary["assertions"], 1000)
        self.assertEqual("0/50", summary["full_class_a_modules"])
        self.assertEqual("50/50", summary["modules_deepened"])

    def test_visual_metrics_when_present(self) -> None:
        metrics_path = ROOT / "docs/verification/r54-host-workbench/metrics.json"
        if not metrics_path.is_file():
            self.skipTest("visual sheets not rendered in this checkout")
        metrics = json.loads(metrics_path.read_text(encoding="utf-8"))
        self.assertEqual(3, len(metrics["sheets"]))
        for sheet in metrics["sheets"]:
            self.assertGreaterEqual(sheet["boxes"], 20)
        self.assertGreaterEqual(metrics["min_wcag_contrast"], 7.0)
        for sheet in metrics["sheets"]:
            self.assertTrue((ROOT / "docs/verification/r54-host-workbench" / sheet["file"]).is_file())
        self.assertIn("0/50", metrics["honesty"])

    def test_docs_and_sources_present(self) -> None:
        for rel in (
            "docs/research/r54-primary.md", "docs/R54-PLAN.md", "docs/R54-ALTERNATIVES.md",
            "docs/R54-PRIVACY-PRD.md", "docs/R54-RESULTS.md", "docs/class-a-readiness-r54.json",
        ):
            self.assertTrue((ROOT / rel).is_file(), rel)
        primary = (ROOT / "docs/research/r54-primary.md").read_text(encoding="utf-8")
        for token in ("9639", "FIPS 203", "Proposal 340", "WebAuthn", "6.4.12.2", "cmap", "SQLite 3"):
            self.assertIn(token, primary)

    def test_workbench_exposes_r54_routes(self) -> None:
        src = (ROOT / "tools/fusion_workbench_server.py").read_text(encoding="utf-8")
        for route in ("/api/r54/writer-typography", "/api/r54/calc-optimisation", "/api/r54/recall-shield-ergonomics"):
            self.assertIn(route, src)
        self.assertIn('default="0.0.0.0"', src)
        self.assertIn('fetch("api/status")', src)
        self.assertNotIn("http://localhost", src)
        self.assertNotIn("http://127.0.0.1", src)

    def test_no_deleted_tracked_files(self) -> None:
        import subprocess

        out = subprocess.run(
            ["git", "status", "--porcelain"], cwd=ROOT.parent, capture_output=True, text=True, check=True,
        ).stdout
        deleted = [line for line in out.splitlines() if re.match(r"^\s?D", line)]
        self.assertEqual([], deleted)


if __name__ == "__main__":
    unittest.main()
