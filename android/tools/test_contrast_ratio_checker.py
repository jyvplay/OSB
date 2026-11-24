#!/usr/bin/env python3
"""Differential oracle for ContrastRatioChecker (core/designsystem)."""
import unittest


def channel_lum(c8: int) -> float:
    s = c8 / 255.0
    return s / 12.92 if s <= 0.03928 else ((s + 0.055) / 1.055) ** 2.4


def contrast_ratio(c1, c2) -> float:
    l1 = 0.2126 * channel_lum(c1[0]) + 0.7152 * channel_lum(c1[1]) + 0.0722 * channel_lum(c1[2])
    l2 = 0.2126 * channel_lum(c2[0]) + 0.7152 * channel_lum(c2[1]) + 0.0722 * channel_lum(c2[2])
    lighter, darker = max(l1, l2), min(l1, l2)
    return (lighter + 0.05) / (darker + 0.05)


class ContrastRatioCheckerTest(unittest.TestCase):
    def test_black_white(self):
        self.assertAlmostEqual(contrast_ratio((0, 0, 0), (255, 255, 255)), 21.0, places=4)


if __name__ == "__main__":
    unittest.main()
