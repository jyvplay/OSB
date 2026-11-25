#!/usr/bin/env python3
"""Differential oracle for ContinuityClockSkewGuard (feature/continuity)."""
import unittest


def within_skew(local_ms: int, remote_ms: int, max_skew: int) -> bool:
    return abs(remote_ms - local_ms) <= max_skew


class ContinuityClockSkewGuardTest(unittest.TestCase):
    def test_skew(self):
        self.assertTrue(within_skew(1000, 1050, 100))
        self.assertFalse(within_skew(1000, 1200, 100))


if __name__ == "__main__":
    unittest.main()
