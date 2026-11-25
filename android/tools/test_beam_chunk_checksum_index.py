#!/usr/bin/env python3
"""Differential oracle for BeamChunkChecksumIndex (feature/beam)."""
import unittest


class BeamChunkChecksumIndex:
    def __init__(self, total: int):
        self.total = total
        self.digests = {}

    def record(self, idx: int, hex64: str):
        assert 0 <= idx < self.total and len(hex64) == 64
        self.digests[idx] = hex64.lower()

    def is_complete(self) -> bool:
        return len(self.digests) == self.total


class BeamChunkChecksumIndexTest(unittest.TestCase):
    def test_complete(self):
        idx = BeamChunkChecksumIndex(2)
        idx.record(0, "a" * 64)
        self.assertFalse(idx.is_complete())
        idx.record(1, "b" * 64)
        self.assertTrue(idx.is_complete())


if __name__ == "__main__":
    unittest.main()
