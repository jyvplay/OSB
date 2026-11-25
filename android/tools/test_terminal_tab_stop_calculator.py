#!/usr/bin/env python3
"""Differential oracle for TerminalTabStopCalculator (feature/terminal)."""
import unittest


def next_tab_stop(col: int, w: int = 8) -> int:
    return ((col // w) + 1) * w


class TerminalTabStopCalculatorTest(unittest.TestCase):
    def test_tab(self):
        self.assertEqual(next_tab_stop(0), 8)
        self.assertEqual(next_tab_stop(7), 8)
        self.assertEqual(next_tab_stop(8), 16)


if __name__ == "__main__":
    unittest.main()
