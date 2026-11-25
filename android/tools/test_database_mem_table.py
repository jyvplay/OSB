#!/usr/bin/env python3
"""Differential oracle for DatabaseMemTable (core/database)."""
import unittest


class MemTable:
    def __init__(self):
        self.m = {}

    def put(self, k, v):
        self.m[k] = v

    def delete(self, k):
        self.m[k] = None

    def scan_range(self, start, end):
        return sorted((k, v) for k, v in self.m.items() if start <= k < end and v is not None)


class DatabaseMemTableTest(unittest.TestCase):
    def test_scan_and_tombstone(self):
        mt = MemTable()
        mt.put("a", "1")
        mt.put("b", "2")
        mt.put("c", "3")
        mt.delete("b")
        self.assertEqual(mt.scan_range("a", "d"), [("a", "1"), ("c", "3")])


if __name__ == "__main__":
    unittest.main()
