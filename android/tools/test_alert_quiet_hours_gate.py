#!/usr/bin/env python3
"""Differential oracle for AlertQuietHoursGate (feature/alerts)."""
import unittest


def is_quiet(minute: int, start: int, end: int) -> bool:
    if start == end:
        return False
    if start < end:
        return start <= minute < end
    return minute >= start or minute < end


class AlertQuietHoursGateTest(unittest.TestCase):
    def test_quiet(self):
        self.assertTrue(is_quiet(23 * 60, 22 * 60, 7 * 60))
        self.assertTrue(is_quiet(6 * 60, 22 * 60, 7 * 60))
        self.assertFalse(is_quiet(12 * 60, 22 * 60, 7 * 60))


if __name__ == "__main__":
    unittest.main()
