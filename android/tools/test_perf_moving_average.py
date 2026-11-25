#!/usr/bin/env python3
"""Differential oracle for PerfMovingAverage (core/perf)."""
import unittest


class PerfMovingAverage:
    def __init__(self, alpha: float):
        self.alpha = alpha
        self.val = None

    def update(self, sample: float) -> float:
        self.val = sample if self.val is None else self.alpha * sample + (1.0 - self.alpha) * self.val
        return self.val


class PerfMovingAverageTest(unittest.TestCase):
    def test_ema(self):
        ema = PerfMovingAverage(0.5)
        self.assertAlmostEqual(ema.update(10.0), 10.0)
        self.assertAlmostEqual(ema.update(20.0), 15.0)


if __name__ == "__main__":
    unittest.main()
