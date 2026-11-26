#!/usr/bin/env python3
"""Differential oracle for CaptureKeyframeRingBuffer (feature/capture)."""
import unittest


class Ring:
    def __init__(self, cap):
        self.cap, self.q = cap, []

    def push(self, pts, kf, sz):
        if len(self.q) == self.cap:
            self.q.pop(0)
        self.q.append((pts, kf, sz))

    def from_last_kf(self):
        idx = max((i for i, x in enumerate(self.q) if x[1]), default=-1)
        return [] if idx < 0 else self.q[idx:]


class CaptureKeyframeRingBufferTest(unittest.TestCase):
    def test_gop(self):
        r = Ring(5)
        r.push(1, False, 10)
        r.push(2, True, 50)
        r.push(3, False, 12)
        self.assertEqual(r.from_last_kf(), [(2, True, 50), (3, False, 12)])


if __name__ == "__main__":
    unittest.main()
