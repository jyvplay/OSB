#!/usr/bin/env python3
"""Differential oracle for DnsQueryPaddingSizer (privacy)."""
import unittest

BLOCK_SIZE = 128
MAX_DNS_BYTES = 4096


def compute(original_bytes: int):
    assert 1 <= original_bytes <= MAX_DNS_BYTES, "DNS message size out of bounds"
    padded = ((original_bytes + BLOCK_SIZE - 1) // BLOCK_SIZE) * BLOCK_SIZE
    clamped = min(padded, MAX_DNS_BYTES)
    return original_bytes, clamped, clamped - original_bytes


class DnsQueryPaddingSizerTest(unittest.TestCase):
    def test_exact_block(self):
        self.assertEqual(compute(128), (128, 128, 0))
        self.assertEqual(compute(256), (256, 256, 0))

    def test_pads_up(self):
        self.assertEqual(compute(1), (1, 128, 127))
        self.assertEqual(compute(129), (129, 256, 127))

    def test_max_clamped(self):
        self.assertEqual(compute(4096), (4096, 4096, 0))

    def test_out_of_bounds(self):
        with self.assertRaises(AssertionError):
            compute(0)
        with self.assertRaises(AssertionError):
            compute(4097)


if __name__ == "__main__":
    unittest.main()
