#!/usr/bin/env python3
"""Differential oracle for CapabilityLeaseTimer (core/capabilities)."""
import unittest


class CapabilityLeaseTimer:
    def __init__(self):
        self.leases = {}

    def grant(self, lease_id: str, cap: str, expires_at_ms: int):
        assert expires_at_ms >= 0
        self.leases[lease_id] = (cap, expires_at_ms)

    def is_active(self, lease_id: str, now_ms: int) -> bool:
        if lease_id not in self.leases:
            return False
        return now_ms < self.leases[lease_id][1]

    def prune_expired(self, now_ms: int) -> int:
        expired = [k for k, (_, exp) in self.leases.items() if now_ms >= exp]
        for k in expired:
            del self.leases[k]
        return len(expired)


class CapabilityLeaseTimerTest(unittest.TestCase):
    def test_lease_lifecycle(self):
        t = CapabilityLeaseTimer()
        t.grant("l1", "read_file", 1000)
        self.assertTrue(t.is_active("l1", 999))
        self.assertFalse(t.is_active("l1", 1000))
        self.assertEqual(t.prune_expired(1000), 1)


if __name__ == "__main__":
    unittest.main()
