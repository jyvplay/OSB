#!/usr/bin/env python3
"""Differential oracle for BackupRetentionWindow (feature/backup)."""
import unittest


def select_retained(ages_days, max_count: int, max_age_days: int):
    kept = []
    for age in ages_days:
        assert age >= 0
        if age <= max_age_days and len(kept) < max_count:
            kept.append(age)
    return kept


class BackupRetentionWindowTest(unittest.TestCase):
    def test_select(self):
        self.assertEqual(select_retained([1, 5, 10, 40], 2, 30), [1, 5])


if __name__ == "__main__":
    unittest.main()
