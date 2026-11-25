#!/usr/bin/env python3
"""Differential oracle for BackupCompressionRatio (feature/backup)."""
import unittest


def ratio(comp: int, orig: int) -> float:
    return 1.0 if orig == 0 else max(0.0, min(1.0, comp / orig))


class BackupCompressionRatioTest(unittest.TestCase):
    def test_ratio(self):
        self.assertAlmostEqual(ratio(250, 1000), 0.25)
        self.assertEqual(ratio(0, 0), 1.0)


if __name__ == "__main__":
    unittest.main()
