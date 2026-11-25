#!/usr/bin/env python3
"""Differential oracle for CapabilityMaskBitset (core/capabilities)."""
import unittest


class CapabilityMaskBitset:
    def __init__(self):
        self.mask = 0

    def grant(self, bit: int):
        self.mask |= 1 << bit

    def revoke(self, bit: int):
        self.mask &= ~(1 << bit)

    def has(self, bit: int) -> bool:
        return (self.mask & (1 << bit)) != 0


class CapabilityMaskBitsetTest(unittest.TestCase):
    def test_bitset(self):
        b = CapabilityMaskBitset()
        b.grant(3)
        self.assertTrue(b.has(3))
        b.revoke(3)
        self.assertFalse(b.has(3))


if __name__ == "__main__":
    unittest.main()
