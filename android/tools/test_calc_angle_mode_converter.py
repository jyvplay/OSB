#!/usr/bin/env python3
"""Differential oracle for CalcAngleModeConverter (feature/calc)."""
import math
import unittest


class CalcAngleModeConverterTest(unittest.TestCase):
    def test_conv(self):
        self.assertAlmostEqual(180.0 * (math.pi / 180.0), math.pi)
        self.assertAlmostEqual(math.pi * (180.0 / math.pi), 180.0)


if __name__ == "__main__":
    unittest.main()
