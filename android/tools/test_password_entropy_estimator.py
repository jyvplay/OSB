#!/usr/bin/env python3
"""Differential oracle for PasswordEntropyEstimator (core/security)."""
import math
import unittest


def estimate_bits(pw: str) -> float:
    if not pw:
        return 0.0
    has_l = any("a" <= c <= "z" for c in pw)
    has_u = any("A" <= c <= "Z" for c in pw)
    has_d = any("0" <= c <= "9" for c in pw)
    has_s = any(not ("a" <= c <= "z" or "A" <= c <= "Z" or "0" <= c <= "9") for c in pw)
    pool = (26 if has_l else 0) + (26 if has_u else 0) + (10 if has_d else 0) + (32 if has_s else 0)
    return 0.0 if pool == 0 else len(pw) * math.log2(pool)


class PasswordEntropyEstimatorTest(unittest.TestCase):
    def test_entropy(self):
        self.assertEqual(estimate_bits(""), 0.0)
        self.assertAlmostEqual(estimate_bits("abcd"), 4 * math.log2(26), places=6)
        self.assertAlmostEqual(estimate_bits("Ab1!"), 4 * math.log2(94), places=6)


if __name__ == "__main__":
    unittest.main()
