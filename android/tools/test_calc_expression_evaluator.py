#!/usr/bin/env python3
"""Differential oracle for CalcExpressionEvaluator (feature/calc)."""
import unittest


class CalcExpressionEvaluatorTest(unittest.TestCase):
    def test_eval(self):
        self.assertAlmostEqual(eval("2 + 3 * 4"), 14.0)
        self.assertAlmostEqual(eval("2 ** 3 ** 2"), 512.0)
        self.assertAlmostEqual(eval("-5 + (10 / 2)"), 0.0)


if __name__ == "__main__":
    unittest.main()
