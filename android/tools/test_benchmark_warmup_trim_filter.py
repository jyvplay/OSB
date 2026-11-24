#!/usr/bin/env python3
"""Differential oracle for BenchmarkWarmupTrimFilter (benchmark)."""
import math
import unittest


def summarize(samples, warmup_count: int):
    assert len(samples) <= 100_000
    assert 0 <= warmup_count < len(samples)
    kept = samples[warmup_count:]
    for v in kept:
        assert math.isfinite(v)
    return len(kept), min(kept), max(kept), sum(kept) / len(kept)


class BenchmarkWarmupTrimFilterTest(unittest.TestCase):
    def test_summarize(self):
        cnt, mn, mx, mean = summarize([100.0, 10.0, 20.0, 30.0], 1)
        self.assertEqual((cnt, mn, mx, mean), (3, 10.0, 30.0, 20.0))

    def test_invalid_warmup(self):
        with self.assertRaises(AssertionError):
            summarize([1.0, 2.0], 2)


if __name__ == "__main__":
    unittest.main()
