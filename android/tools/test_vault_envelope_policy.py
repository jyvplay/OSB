#!/usr/bin/env python3
"""Differential oracle for VaultEnvelopePolicy (feature/vault)."""
import unittest


def valid_argon2(mem_kib: int, iters: int, par: int, salt_len: int) -> bool:
    return mem_kib >= 19_456 and iters >= 2 and 1 <= par <= 16 and salt_len >= 16


def ct_eq(a: bytes, b: bytes) -> bool:
    if len(a) != len(b):
        return False
    diff = 0
    for x, y in zip(a, b):
        diff |= x ^ y
    return diff == 0


class VaultEnvelopePolicyTest(unittest.TestCase):
    def test_policy(self):
        self.assertTrue(valid_argon2(19_456, 2, 1, 16))
        self.assertFalse(valid_argon2(1024, 2, 1, 16))
        self.assertTrue(ct_eq(b"abc", b"abc"))
        self.assertFalse(ct_eq(b"abc", b"abd"))


if __name__ == "__main__":
    unittest.main()
