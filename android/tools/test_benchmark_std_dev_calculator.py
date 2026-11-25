#!/usr/bin/env python3
"""Differential oracle for BenchmarkStdDevCalculator (benchmark)."""
import math
import unittest


def stats(samples):
    mean = sum(samples) / len(samples)
    var = sum((x - mean) ** 2 for x in samples) / len(samples)
    return mean, math.sqrt(var)


class BenchmarkStdDevCalculatorTest(unittest.TestCase):
    def test_stats(self):
        m, sd = stats([2.0, 4.0, 4.0, 4.0, 5.0, 5.0, 7.0, 9.0])
        self.assertAlmostEqual(m, 5.0)
        self.assertAlmostEqual(sd, 2.0)


if __name__ == "__main__":
    unittest.main()
