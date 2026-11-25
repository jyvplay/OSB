#!/usr/bin/env python3
"""Differential oracle for DesignElevationShadow (core/designsystem)."""
import unittest


def shadow_alpha(dp: int) -> float:
    c = max(0, min(24, dp))
    return 0.0 if c == 0 else 0.04 + (c / 24.0) * 0.20


class DesignElevationShadowTest(unittest.TestCase):
    def test_alpha(self):
        self.assertEqual(shadow_alpha(0), 0.0)
        self.assertAlmostEqual(shadow_alpha(24), 0.24)


if __name__ == "__main__":
    unittest.main()
