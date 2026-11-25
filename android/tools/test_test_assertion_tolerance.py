#!/usr/bin/env python3
"""Differential oracle for TestAssertionTolerance (testing)."""
import math
import unittest


def is_close(a: float, b: float, abs_tol: float = 1e-9, rel_tol: float = 1e-6) -> bool:
    if not (math.isfinite(a) and math.isfinite(b)):
        return a == b
    return abs(a - b) <= abs_tol + rel_tol * max(abs(a), abs(b))


class TestAssertionToleranceTest(unittest.TestCase):
    def test_close(self):
        self.assertTrue(is_close(1.0, 1.0 + 1e-7))
        self.assertFalse(is_close(1.0, 1.1))


if __name__ == "__main__":
    unittest.main()
