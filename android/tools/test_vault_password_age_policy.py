#!/usr/bin/env python3
"""Differential oracle for VaultPasswordAgePolicy (feature/vault)."""
import unittest


def evaluate(age_days: int, max_age_days: int, warn_before_days: int):
    assert age_days >= 0
    assert 1 <= max_age_days <= 3650
    assert 0 <= warn_before_days <= max_age_days
    remaining = max_age_days - age_days
    if remaining <= 0:
        return "EXPIRED", 0
    if remaining <= warn_before_days:
        return "WARN", remaining
    return "OK", remaining


class VaultPasswordAgePolicyTest(unittest.TestCase):
    def test_thresholds(self):
        self.assertEqual(evaluate(10, 90, 14), ("OK", 80))
        self.assertEqual(evaluate(80, 90, 14), ("WARN", 10))
        self.assertEqual(evaluate(90, 90, 14), ("EXPIRED", 0))
        self.assertEqual(evaluate(120, 90, 14), ("EXPIRED", 0))


if __name__ == "__main__":
    unittest.main()
