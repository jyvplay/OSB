#!/usr/bin/env python3
"""Differential oracle for LokZoomScaleQuantizer (format/lok)."""
import unittest

LEVELS = [0.25, 0.5, 0.75, 1.0, 1.25, 1.5, 2.0, 3.0, 4.0]


def quantize(zoom: float) -> float:
    return min(LEVELS, key=lambda l: abs(zoom - l))


class LokZoomScaleQuantizerTest(unittest.TestCase):
    def test_quantize(self):
        self.assertEqual(quantize(1.1), 1.0)
        self.assertEqual(quantize(1.8), 2.0)


if __name__ == "__main__":
    unittest.main()
