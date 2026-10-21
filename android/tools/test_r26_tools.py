import copy
import json
import tempfile
import unittest
from pathlib import Path

import amnesic_posture
import beauty_audit
import module_audit
import module_testability
import prove_r26_models
from r26_evidence_gate import expected, verify

ROOT = Path(__file__).resolve().parents[1]


class R26ToolAndSecurityTests(unittest.TestCase):
    def test_all_56_modules_have_substantial_runnable_tests_while_full_spec_remains_zero(self):
        included = module_audit.included_modules()
        self.assertEqual(len(included), 56)
        for mod in (":core:fileops", ":format:poi", ":feature:office", ":feature:quickpanel", ":feature:photos", ":feature:ime"):
            self.assertIn(mod, included)
        census = json.loads((ROOT / "docs/module-testability.json").read_text())
        summary = module_testability.counts(census)
        self.assertEqual(summary["total"], 56)
        self.assertEqual(summary["substantial_testable_subsets"], 56)
        self.assertEqual(summary["testable_percent"], 100.0)

    def test_modal_shortcut_detector_blocks_false_promotion_of_unverified_gates(self):
        # Negative-space modal shortcut detector: any attempt to change BLOCKED full-spec gates to CANDIDATE fails immediately.
        policy_src = (ROOT / "core/model/src/main/kotlin/dev/fusion/model/PilotPolicy.kt").read_text()
        for gated in ("browser", "docs", "pdf", "vault", "beam", "backup", "alerts", "together", "messages", "dlna"):
            self.assertRegex(
                policy_src,
                rf'PilotModule\("{gated}",\s*"[^"]+",\s*PilotAccess\.BLOCKED,',
                msg=f"Modal shortcut detected: {gated} must stay PilotAccess.BLOCKED",
            )

    def test_r26_evidence_gate_enforces_exact_153_full_and_5_focused_cases(self):
        self.assertEqual(len(expected("r26")), 5)
        self.assertEqual(len(expected("full")), 153)
        self.assertEqual(len(expected("privacy")), 19)
        sha = "c" * 40
        good = {
            "candidate": sha,
            "suite": "focused",
            "platform_api": 34,
            "conclusion": "success",
            "device_conclusion": "success",
            "cases": [{"case": c, "result": "PASS"} for c in sorted(expected("r26"))],
            "counts": {"PASS": 5, "FAIL": 0, "SKIP": 0},
        }
        self.assertEqual(verify(good, sha, "r26")["passed_cases"], 5)
        for bad_mut in ("drop", "skip", "wrong_sha"):
            mutated = copy.deepcopy(good)
            if bad_mut == "drop":
                mutated["cases"].pop()
            elif bad_mut == "skip":
                mutated["cases"][0]["result"] = "SKIP"
            else:
                mutated["candidate"] = "d" * 40
            with self.assertRaises(ValueError):
                verify(mutated, sha, "r26")

    def test_z3_formal_proofs_and_beauty_audit_and_badusb_composite_detection(self):
        z3_rep = prove_r26_models.run_all()
        self.assertEqual(z3_rep["theorem_count"], 6)
        self.assertTrue(z3_rep["all_unsat_proved"])

        beauty = beauty_audit.audit_all()
        self.assertGreater(beauty["tabletop_floor_scene_share"], 0.80)
        self.assertGreaterEqual(beauty["palettes"]["STUDIO_SILVER"]["min_component_border_wcag"], 3.0)
        self.assertGreaterEqual(beauty["palettes"]["STUDIO_GRAPHITE"]["muted_on_raised_apca_lc"], 60.0)

        # Synthetic sysfs composite BadUSB tree (03 HID + 08 Mass Storage on single USB physical device):
        with tempfile.TemporaryDirectory() as tmp:
            usb_root = Path(tmp) / "sys/bus/usb/devices"
            dev = usb_root / "1-2"
            (dev / "1-2:1.0").mkdir(parents=True)
            (dev / "1-2:1.1").mkdir(parents=True)
            (dev / "idVendor").write_text("dead\n")
            (dev / "idProduct").write_text("beef\n")
            (dev / "1-2:1.0/bInterfaceClass").write_text("03\n")
            (dev / "1-2:1.1/bInterfaceClass").write_text("08\n")
            posture = amnesic_posture.inspect_host(Path(tmp))
            self.assertEqual(len(posture["composite_badusb_suspects"]), 1)

    def test_all_gradle_version_catalog_accessors_resolve_cleanly(self):
        import re
        import tomllib

        cat = tomllib.loads((ROOT / "gradle/libs.versions.toml").read_text())
        valid = (
            {"libs." + k.replace("-", ".") for k in cat["libraries"]}
            | {"libs.plugins." + k.replace("-", ".") for k in cat["plugins"]}
            | {"libs.versions." + k.replace("-", ".") for k in cat["versions"]}
        )
        for gradle_file in ROOT.rglob("*.gradle.kts"):
            for m in re.findall(r"\blibs(?:\.[A-Za-z0-9_]+)+", gradle_file.read_text()):
                cleaned = re.sub(r"\.get$", "", m)
                self.assertIn(cleaned, valid, f"Invalid catalog accessor {m} in {gradle_file}")
