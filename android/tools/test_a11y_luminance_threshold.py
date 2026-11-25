#!/usr/bin/env python3
"""Differential oracle for A11yLuminanceThreshold (feature/accessibility)."""
import unittest


def is_light(lum: float) -> bool:
    return lum >= 0.179


class A11yLuminanceThresholdTest(unittest.TestCase):
    def test_thresh(self):
        self.assertTrue(is_light(0.5))
        self.assertFalse(is_light(0.1))


if __name__ == "__main__":
    unittest.main()
