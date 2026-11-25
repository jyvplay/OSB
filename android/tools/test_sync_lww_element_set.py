#!/usr/bin/env python3
"""Differential oracle for SyncLwwElementSet (core/sync)."""
import unittest


class LwwElementSet:
    def __init__(self):
        self.add_set, self.rem_set = {}, {}

    def add(self, elem, ts):
        if elem not in self.add_set or ts > self.add_set[elem]:
            self.add_set[elem] = ts

    def remove(self, elem, ts):
        if elem not in self.rem_set or ts > self.rem_set[elem]:
            self.rem_set[elem] = ts

    def contains(self, elem):
        if elem not in self.add_set:
            return False
        if elem not in self.rem_set:
            return True
        return self.add_set[elem] > self.rem_set[elem]


class SyncLwwElementSetTest(unittest.TestCase):
    def test_lww_remove_wins_on_tie(self):
        s = LwwElementSet()
        s.add("x", 100)
        s.remove("x", 100)
        self.assertFalse(s.contains("x"))
        s.add("x", 101)
        self.assertTrue(s.contains("x"))


if __name__ == "__main__":
    unittest.main()
