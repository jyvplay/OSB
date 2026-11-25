#!/usr/bin/env python3
"""Differential oracle for DesignWcagContrast (core/design)."""
import unittest


def lin(c8: int) -> float:
    c = c8 / 255.0
    return c / 12.92 if c <= 0.03928 else ((c + 0.055) / 1.055) ** 2.4


def lum(rgb: int) -> float:
    return 0.2126 * lin((rgb >> 16) & 0xFF) + 0.7152 * lin((rgb >> 8) & 0xFF) + 0.0722 * lin(rgb & 0xFF)


def ratio(a: int, b: int) -> float:
    l1, l2 = lum(a), lum(b)
    return (max(l1, l2) + 0.05) / (min(l1, l2) + 0.05)


class DesignWcagContrastTest(unittest.TestCase):
    def test_bw(self):
        self.assertAlmostEqual(ratio(0xFFFFFF, 0x000000), 21.0, places=2)


if __name__ == "__main__":
    unittest.main()
