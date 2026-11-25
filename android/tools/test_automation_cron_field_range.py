#!/usr/bin/env python3
"""Differential oracle for AutomationCronFieldRange (feature/automation)."""
import unittest

RANGES = {"MINUTE": (0, 59), "HOUR": (0, 23), "DAY_OF_MONTH": (1, 31), "MONTH": (1, 12), "DAY_OF_WEEK": (0, 6)}


def is_valid(field: str, val: int) -> bool:
    lo, hi = RANGES[field]
    return lo <= val <= hi


class AutomationCronFieldRangeTest(unittest.TestCase):
    def test_ranges(self):
        self.assertTrue(is_valid("MINUTE", 0))
        self.assertFalse(is_valid("MINUTE", 60))


if __name__ == "__main__":
    unittest.main()
