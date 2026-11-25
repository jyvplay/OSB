#!/usr/bin/env python3
"""Differential oracle for BackupChunkDeduplicator (feature/backup)."""
import unittest


class BackupChunkDeduplicator:
    def __init__(self):
        self.u = {}
        self.total = 0
        self.uniq = 0

    def add(self, h: str, sz: int):
        self.total += sz
        if h not in self.u:
            self.u[h] = sz
            self.uniq += sz

    def ratio(self) -> float:
        return 0.0 if self.total == 0 else (self.total - self.uniq) / self.total


class BackupChunkDeduplicatorTest(unittest.TestCase):
    def test_dedup(self):
        d = BackupChunkDeduplicator()
        d.add("h1", 100)
        d.add("h1", 100)
        self.assertAlmostEqual(d.ratio(), 0.5)


if __name__ == "__main__":
    unittest.main()
