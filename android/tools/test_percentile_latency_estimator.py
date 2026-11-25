#!/usr/bin/env python3
"""Differential oracle for PercentileLatencyEstimator (core/perf)."""
import math
import unittest


def percentile(samples, p: float) -> float:
    s = sorted(samples)
    rank = max(1, min(len(s), math.ceil((p / 100.0) * len(s))))
    return s[rank - 1]


class PercentileLatencyEstimatorTest(unittest.TestCase):
    def test_percentile(self):
        self.assertEqual(percentile([10.0, 20.0, 30.0, 40.0, 50.0], 50.0), 30.0)
        self.assertEqual(percentile([10.0, 20.0, 30.0, 40.0, 50.0], 99.0), 50.0)


if __name__ == "__main__":
    unittest.main()
