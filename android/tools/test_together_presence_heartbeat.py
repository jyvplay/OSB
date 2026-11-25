#!/usr/bin/env python3
"""Differential oracle for TogetherPresenceHeartbeat (feature/together)."""
import unittest


class TogetherPresenceHeartbeat:
    def __init__(self, thresh: int):
        self.thresh = thresh
        self.seen = {}

    def beat(self, peer: str, now: int):
        self.seen[peer] = now

    def active(self, now: int):
        return sorted(p for p, ts in self.seen.items() if now - ts <= self.thresh)


class TogetherPresenceHeartbeatTest(unittest.TestCase):
    def test_heartbeat(self):
        h = TogetherPresenceHeartbeat(1000)
        h.beat("p1", 100)
        h.beat("p2", 900)
        self.assertEqual(h.active(1500), ["p2"])


if __name__ == "__main__":
    unittest.main()
