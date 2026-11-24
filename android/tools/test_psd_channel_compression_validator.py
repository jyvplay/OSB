#!/usr/bin/env python3
"""Differential oracle for PsdChannelCompressionValidator (format/psd)."""
import unittest

MAX_DIM = 30_000
ALLOWED_DEPTHS = {1, 8, 16, 32}
ALLOWED_COMPRESSIONS = {0, 1, 2, 3}


def expected_uncompressed_bytes(width: int, height: int, depth_bits: int, compression: int) -> int:
    assert 1 <= width <= MAX_DIM and 1 <= height <= MAX_DIM
    assert depth_bits in ALLOWED_DEPTHS
    assert compression in ALLOWED_COMPRESSIONS
    bytes_per_row = (width * depth_bits + 7) // 8
    return bytes_per_row * height


class PsdChannelCompressionValidatorTest(unittest.TestCase):
    def test_standard_sizes(self):
        self.assertEqual(expected_uncompressed_bytes(100, 50, 8, 1), 5000)
        self.assertEqual(expected_uncompressed_bytes(9, 2, 1, 0), 4)
        self.assertEqual(expected_uncompressed_bytes(10, 10, 16, 2), 200)

    def test_invalid_inputs(self):
        with self.assertRaises(AssertionError):
            expected_uncompressed_bytes(10, 10, 24, 0)
        with self.assertRaises(AssertionError):
            expected_uncompressed_bytes(10, 10, 8, 4)


if __name__ == "__main__":
    unittest.main()
