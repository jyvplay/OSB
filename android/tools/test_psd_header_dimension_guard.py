#!/usr/bin/env python3
"""Differential oracle for PsdHeaderDimensionGuard (format/psd)."""
import unittest


def is_valid(sig: str, ver: int, ch: int, w: int, h: int) -> bool:
    if sig != "8BPS" or ver not in (1, 2) or not (1 <= ch <= 56):
        return False
    mx = 30_000 if ver == 1 else 300_000
    return 1 <= w <= mx and 1 <= h <= mx


class PsdHeaderDimensionGuardTest(unittest.TestCase):
    def test_psd(self):
        self.assertTrue(is_valid("8BPS", 1, 4, 1920, 1080))
        self.assertFalse(is_valid("8BPS", 1, 4, 40000, 1080))


if __name__ == "__main__":
    unittest.main()
