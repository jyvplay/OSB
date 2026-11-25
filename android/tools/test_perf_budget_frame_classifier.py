#!/usr/bin/env python3
"""Differential oracle for PerfBudgetFrameClassifier (core/perf)."""
import unittest


def classify(ms: float, budget: float = 16.67) -> str:
    if ms <= budget:
        return "SMOOTH"
    if ms <= 700.0:
        return "JANK"
    return "FROZEN"


class PerfBudgetFrameClassifierTest(unittest.TestCase):
    def test_classify(self):
        self.assertEqual(classify(12.0), "SMOOTH")
        self.assertEqual(classify(25.0), "JANK")
        self.assertEqual(classify(800.0), "FROZEN")


if __name__ == "__main__":
    unittest.main()
