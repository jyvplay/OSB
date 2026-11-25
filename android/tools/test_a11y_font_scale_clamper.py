#!/usr/bin/env python3
"""Differential oracle for A11yFontScaleClamper (feature/accessibility)."""
import unittest


def clamp_scale(s: float) -> float:
    return max(0.85, min(2.0, s))


class A11yFontScaleClamperTest(unittest.TestCase):
    def test_clamp(self):
        self.assertAlmostEqual(clamp_scale(0.5), 0.85)
        self.assertAlmostEqual(clamp_scale(3.0), 2.0)


if __name__ == "__main__":
    unittest.main()
