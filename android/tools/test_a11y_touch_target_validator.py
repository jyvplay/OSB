#!/usr/bin/env python3
"""Differential oracle for A11yTouchTargetValidator (feature/accessibility)."""
import unittest


def meets_minimum(w: float, h: float) -> bool:
    return w >= 48.0 and h >= 48.0


class A11yTouchTargetValidatorTest(unittest.TestCase):
    def test_min(self):
        self.assertTrue(meets_minimum(48.0, 48.0))
        self.assertFalse(meets_minimum(47.9, 48.0))


if __name__ == "__main__":
    unittest.main()
