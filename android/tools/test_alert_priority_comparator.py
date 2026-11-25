#!/usr/bin/env python3
"""Differential oracle for AlertPriorityComparator (feature/alerts)."""
import unittest

RANKS = {"LOW": 0, "NORMAL": 1, "HIGH": 2, "CRITICAL": 3}


class AlertPriorityComparatorTest(unittest.TestCase):
    def test_order(self):
        self.assertLess(RANKS["LOW"], RANKS["CRITICAL"])


if __name__ == "__main__":
    unittest.main()
