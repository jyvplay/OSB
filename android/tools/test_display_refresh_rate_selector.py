#!/usr/bin/env python3
"""Differential oracle for DisplayRefreshRateSelector (core/hw)."""
import unittest


def select_hz(supported, target: int, min_hz: int = 24, max_hz: int = 240):
    cands = [h for h in supported if min_hz <= h <= max_hz]
    if not cands:
        return None
    return min(cands, key=lambda h: (abs(h - target), -h))


class DisplayRefreshRateSelectorTest(unittest.TestCase):
    def test_select(self):
        self.assertEqual(select_hz([60, 90, 120], 100), 90)
        self.assertEqual(select_hz([60, 120], 90), 120)
        self.assertIsNone(select_hz([15], 60))


if __name__ == "__main__":
    unittest.main()
