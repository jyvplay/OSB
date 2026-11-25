#!/usr/bin/env python3
"""Differential oracle for AlertQuietHoursSchedule (feature/alerts)."""
import unittest


def is_suppressed(m: int, start: int, end: int, crit: bool) -> bool:
    if crit or start == end:
        return False
    if start < end:
        return start <= m < end
    return m >= start or m < end


class AlertQuietHoursScheduleTest(unittest.TestCase):
    def test_wrap(self):
        self.assertTrue(is_suppressed(23 * 60, 22 * 60, 7 * 60, False))
        self.assertFalse(is_suppressed(23 * 60, 22 * 60, 7 * 60, True))
        self.assertFalse(is_suppressed(12 * 60, 22 * 60, 7 * 60, False))


if __name__ == "__main__":
    unittest.main()
