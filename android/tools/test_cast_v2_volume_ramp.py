#!/usr/bin/env python3
"""Differential oracle for CastV2VolumeRamp (cast/castv2)."""
import unittest


def step(cur: float, delta: float) -> float:
    return max(0.0, min(1.0, cur + delta))


class CastV2VolumeRampTest(unittest.TestCase):
    def test_step(self):
        self.assertAlmostEqual(step(0.8, 0.5), 1.0)
        self.assertAlmostEqual(step(0.2, -0.5), 0.0)


if __name__ == "__main__":
    unittest.main()
