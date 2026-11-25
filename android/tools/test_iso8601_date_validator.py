#!/usr/bin/env python3
"""Differential oracle for Iso8601DateValidator (core/model)."""
import re
import unittest

DATE_RE = re.compile(r"^(\d{4})-(\d{2})-(\d{2})$")


def is_valid(date: str) -> bool:
    m = DATE_RE.match(date)
    if not m:
        return False
    y, mo, d = int(m.group(1)), int(m.group(2)), int(m.group(3))
    if not (1 <= mo <= 12):
        return False
    leap = (y % 4 == 0 and y % 100 != 0) or (y % 400 == 0)
    max_d = {1: 31, 2: 29 if leap else 28, 3: 31, 4: 30, 5: 31, 6: 30, 7: 31, 8: 31, 9: 30, 10: 31, 11: 30, 12: 31}[mo]
    return 1 <= d <= max_d


class Iso8601DateValidatorTest(unittest.TestCase):
    def test_dates(self):
        self.assertTrue(is_valid("2024-02-29"))
        self.assertFalse(is_valid("2025-02-29"))
        self.assertTrue(is_valid("2000-02-29"))
        self.assertFalse(is_valid("1900-02-29"))


if __name__ == "__main__":
    unittest.main()
