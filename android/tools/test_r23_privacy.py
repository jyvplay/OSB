"""Host-only contract for the R23 privacy source checker, not a device-security test."""
import subprocess
import sys
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


class R23PrivacySourceTests(unittest.TestCase):
    def test_source_guard_passes_and_labels_its_scope(self):
        result = subprocess.run(
            [sys.executable, str(ROOT / "tools/r23_privacy_check.py")],
            cwd=ROOT,
            text=True,
            capture_output=True,
            check=True,
        )
        self.assertIn("STRUCTURAL CHECK ONLY", result.stdout)
        self.assertIn("Kotlin was not compiled", result.stdout)


if __name__ == "__main__":
    unittest.main()
