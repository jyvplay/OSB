#!/usr/bin/env python3
"""Differential oracle for TerminalScrollbackRing (feature/terminal)."""
from collections import deque
import unittest


class TerminalScrollbackRing:
    def __init__(self, capacity: int):
        assert 1 <= capacity <= 10_000
        self.capacity = capacity
        self.buf = deque(maxlen=capacity)

    def push(self, line: str):
        assert len(line) <= 4096
        self.buf.append(line)

    def tail(self, n: int):
        assert n >= 0
        if n == 0:
            return []
        return list(self.buf)[-n:]


class TerminalScrollbackRingTest(unittest.TestCase):
    def test_ring_eviction_and_tail(self):
        r = TerminalScrollbackRing(3)
        for i in range(5):
            r.push(f"line{i}")
        self.assertEqual(list(r.buf), ["line2", "line3", "line4"])
        self.assertEqual(r.tail(2), ["line3", "line4"])
        self.assertEqual(r.tail(0), [])


if __name__ == "__main__":
    unittest.main()
