#!/usr/bin/env python3
"""Differential oracle for BeamChunkBitfieldTracker (feature/beam)."""
import unittest


class Tracker:
    def __init__(self, n):
        self.rec = [False] * n

    def mark(self, i):
        self.rec[i] = True

    def missing(self, lim=64):
        return [i for i, v in enumerate(self.rec) if not v][:lim]


class BeamChunkBitfieldTrackerTest(unittest.TestCase):
    def test_tracker(self):
        t = Tracker(4)
        t.mark(0)
        t.mark(2)
        self.assertEqual(t.missing(), [1, 3])


if __name__ == "__main__":
    unittest.main()
