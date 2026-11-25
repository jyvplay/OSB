#!/usr/bin/env python3
"""Differential oracle for CapabilitiesRateQuotaLedger (core/capabilities)."""
import unittest


class CapabilitiesRateQuotaLedger:
    def __init__(self, mx: int):
        self.mx = mx
        self.counts = {}

    def try_acquire(self, cap: str) -> bool:
        c = self.counts.get(cap, 0)
        if c >= self.mx:
            return False
        self.counts[cap] = c + 1
        return True


class CapabilitiesRateQuotaLedgerTest(unittest.TestCase):
    def test_quota(self):
        l = CapabilitiesRateQuotaLedger(2)
        self.assertTrue(l.try_acquire("net"))
        self.assertTrue(l.try_acquire("net"))
        self.assertFalse(l.try_acquire("net"))


if __name__ == "__main__":
    unittest.main()
