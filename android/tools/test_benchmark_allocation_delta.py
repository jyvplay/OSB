#!/usr/bin/env python3
"""Differential oracle for BenchmarkAllocationDelta (benchmark)."""
import unittest


def bytes_per_op(before: int, after: int, iters: int) -> float:
    assert 0 <= before <= after and iters > 0
    return (after - before) / iters


class BenchmarkAllocationDeltaTest(unittest.TestCase):
    def test_delta(self):
        self.assertAlmostEqual(bytes_per_op(1000, 3000, 100), 20.0)


if __name__ == "__main__":
    unittest.main()
