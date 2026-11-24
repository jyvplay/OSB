#!/usr/bin/env python3
"""Independent differential oracle for ConstantTimeCompare (core/security).

Implements constant-time comparison of byte arrays: compares every byte
regardless of early mismatch so execution time does not depend on how many
leading bytes match. Length differences are XORed into the accumulator so
they also contribute uniformly. Cross-checks against the Kotlin contract in
core/security/src/main/kotlin/dev/fusion/security/ConstantTimeCompare.kt.

No Android SDK, Kotlin compiler or Gradle required; runs on host Python 3.11.
"""
from __future__ import annotations

import sys
import unittest

sys.path.insert(0, str(__file__.rpartition("/tools/")[0] + "/tools"))

MAX_LEN = 1 << 16  # 64 KiB


def constant_time_equals(a: bytes, b: bytes) -> bool:
    assert len(a) <= MAX_LEN, f"array a exceeds {MAX_LEN} bytes"
    assert len(b) <= MAX_LEN, f"array b exceeds {MAX_LEN} bytes"
    result = len(a) ^ len(b)
    n = min(len(a), len(b))
    for i in range(n):
        result |= (a[i] ^ b[i]) & 0xFF
    return result == 0


class ConstantTimeCompareTest(unittest.TestCase):
    def test_equal_arrays(self):
        self.assertTrue(constant_time_equals(b"hello", b"hello"))
        self.assertTrue(constant_time_equals(b"", b""))
        self.assertTrue(constant_time_equals(b"\x00\x01\x02", b"\x00\x01\x02"))

    def test_different_lengths(self):
        self.assertFalse(constant_time_equals(b"hello", b"hell"))
        self.assertFalse(constant_time_equals(b"", b"a"))
        self.assertFalse(constant_time_equals(b"a", b""))

    def test_different_content_same_length(self):
        self.assertFalse(constant_time_equals(b"hello", b"jello"))
        self.assertFalse(constant_time_equals(b"\x00\x01", b"\x00\x02"))
        self.assertFalse(constant_time_equals(b"abc", b"abd"))

    def test_prefix_match(self):
        # Long common prefix but different at end — must still return False
        self.assertFalse(constant_time_equals(b"aaaaab", b"aaaaac"))
        self.assertFalse(constant_time_equals(b"secret1234", b"secret5678"))

    def test_single_byte_diff(self):
        self.assertFalse(constant_time_equals(b"\x00", b"\x01"))
        self.assertTrue(constant_time_equals(b"\xFF", b"\xFF"))

    def test_all_zero_vs_all_zero(self):
        self.assertTrue(constant_time_equals(b"\x00" * 100, b"\x00" * 100))

    def test_all_zero_vs_all_ff(self):
        self.assertFalse(constant_time_equals(b"\x00" * 100, b"\xFF" * 100))

    def test_array_too_large(self):
        with self.assertRaises(AssertionError):
            constant_time_equals(b"x" * (MAX_LEN + 1), b"y")
        with self.assertRaises(AssertionError):
            constant_time_equals(b"x", b"y" * (MAX_LEN + 1))


if __name__ == "__main__":
    unittest.main()
