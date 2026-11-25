#!/usr/bin/env python3
"""Differential oracle for CalcPercentChangeSolver (feature/calc)."""
import unittest


def percent_change(old_val: float, new_val: float) -> float:
    assert old_val != 0.0
    return ((new_val - old_val) / abs(old_val)) * 100.0


class CalcPercentChangeSolverTest(unittest.TestCase):
    def test_percent_change(self):
        self.assertAlmostEqual(percent_change(50.0, 75.0), 50.0)
        self.assertAlmostEqual(percent_change(100.0, 80.0), -20.0)


if __name__ == "__main__":
    unittest.main()
