#!/usr/bin/env python3
"""Differential oracle for AirPlayRtspCSeqTracker (cast/airplay)."""
import unittest


class AirPlayRtspCSeqTracker:
    def __init__(self):
        self.last = 0

    def accept(self, cseq: int) -> bool:
        assert cseq >= 1
        if cseq <= self.last:
            return False
        self.last = cseq
        return True


class AirPlayRtspCSeqTrackerTest(unittest.TestCase):
    def test_cseq(self):
        t = AirPlayRtspCSeqTracker()
        self.assertTrue(t.accept(1))
        self.assertFalse(t.accept(1))
        self.assertTrue(t.accept(2))


if __name__ == "__main__":
    unittest.main()
