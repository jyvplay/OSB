#!/usr/bin/env python3
"""Differential oracle for CommandHistoryRing (core/commands)."""
from collections import deque
import unittest


class CommandHistoryRing:
    def __init__(self, cap: int):
        self.ring = deque(maxlen=cap)

    def record(self, cmd: str):
        assert 0 < len(cmd) <= 1024
        if self.ring and self.ring[-1] == cmd:
            return
        self.ring.append(cmd)

    def search_prefix(self, prefix: str):
        return [c for c in reversed(self.ring) if c.startswith(prefix)]


class CommandHistoryRingTest(unittest.TestCase):
    def test_dedup_and_prefix(self):
        r = CommandHistoryRing(3)
        r.record("ls")
        r.record("ls")
        r.record("cd /tmp")
        r.record("cat a")
        self.assertEqual(r.search_prefix("c"), ["cat a", "cd /tmp"])


if __name__ == "__main__":
    unittest.main()
