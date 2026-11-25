#!/usr/bin/env python3
"""Differential oracle for CaptureBitrateClamper (feature/capture)."""
import unittest


def clamp_bps(bps: int, lo: int = 64_000, hi: int = 50_000_000) -> int:
    return max(lo, min(hi, bps))


class CaptureBitrateClamperTest(unittest.TestCase):
    def test_clamp(self):
        self.assertEqual(clamp_bps(1000), 64_000)
        self.assertEqual(clamp_bps(99_000_000), 50_000_000)


if __name__ == "__main__":
    unittest.main()
