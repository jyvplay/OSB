#!/usr/bin/env python3
"""Differential oracle for ProfileRuleDeduplicator (baselineprofile)."""
import unittest


def dedup_sort(rules):
    return sorted({r.strip() for r in rules if r.strip()})


class ProfileRuleDeduplicatorTest(unittest.TestCase):
    def test_dedup(self):
        self.assertEqual(dedup_sort(["b", "a", "b", " "]), ["a", "b"])


if __name__ == "__main__":
    unittest.main()
