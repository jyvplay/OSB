#!/usr/bin/env python3
"""Differential oracle for DesignSpacingScale (core/designsystem)."""
import unittest

SCALE = [4, 8, 12, 16, 24, 32, 48, 64]


def snap_dp(dp: int) -> int:
    return min(SCALE, key=lambda s: abs(s - dp))


class DesignSpacingScaleTest(unittest.TestCase):
    def test_snap(self):
        self.assertEqual(snap_dp(15), 16)
        self.assertEqual(snap_dp(0), 4)


if __name__ == "__main__":
    unittest.main()
