#!/usr/bin/env python3
"""Differential oracle for TerminalBellThrottle (feature/terminal)."""
import unittest


class TerminalBellThrottle:
    def __init__(self, min_interval_ms: int):
        self.min_interval_ms = min_interval_ms
        self.last_ms = -1

    def should_ring(self, now_ms: int) -> bool:
        if self.last_ms >= 0 and now_ms - self.last_ms < self.min_interval_ms:
            return False
        self.last_ms = now_ms
        return True


class TerminalBellThrottleTest(unittest.TestCase):
    def test_throttle(self):
        b = TerminalBellThrottle(250)
        self.assertTrue(b.should_ring(100))
        self.assertFalse(b.should_ring(200))
        self.assertTrue(b.should_ring(350))


if __name__ == "__main__":
    unittest.main()
