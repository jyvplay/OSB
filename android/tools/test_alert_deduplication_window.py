#!/usr/bin/env python3
"""Differential oracle for AlertDeduplicationWindow (feature/alerts)."""
import unittest


class AlertDeduplicationWindow:
    def __init__(self, w: int):
        self.w = w
        self.seen = {}

    def should_emit(self, key: str, now: int) -> bool:
        prev = self.seen.get(key)
        if prev is not None and now - prev < self.w:
            return False
        self.seen[key] = now
        return True


class AlertDeduplicationWindowTest(unittest.TestCase):
    def test_dedup(self):
        d = AlertDeduplicationWindow(500)
        self.assertTrue(d.should_emit("a", 100))
        self.assertFalse(d.should_emit("a", 400))
        self.assertTrue(d.should_emit("a", 600))


if __name__ == "__main__":
    unittest.main()
