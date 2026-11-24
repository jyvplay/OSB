#!/usr/bin/env python3
"""Differential oracle for SlidingWindowRpsCounter (core/perf)."""
from collections import deque
import unittest


class SlidingWindowRpsCounter:
    def __init__(self, window_ms: int):
        self.window_ms = window_ms
        self.ts = deque()

    def _prune(self, now_ms: int):
        cutoff = now_ms - self.window_ms
        while self.ts and self.ts[0] <= cutoff:
            self.ts.popleft()

    def record(self, now_ms: int):
        self._prune(now_ms)
        if len(self.ts) < 10_000:
            self.ts.append(now_ms)

    def count(self, now_ms: int) -> int:
        self._prune(now_ms)
        return len(self.ts)


class SlidingWindowRpsCounterTest(unittest.TestCase):
    def test_window(self):
        c = SlidingWindowRpsCounter(1000)
        c.record(100)
        c.record(500)
        self.assertEqual(c.count(1099), 2)
        self.assertEqual(c.count(1100), 1)
        self.assertEqual(c.count(1500), 0)


if __name__ == "__main__":
    unittest.main()
