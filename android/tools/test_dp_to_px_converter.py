#!/usr/bin/env python3
"""Differential oracle for DpToPxConverter (core/designsystem)."""
import unittest


def dp_to_px(dp: float, density: float) -> int:
    return int(dp * density + 0.5)


class DpToPxConverterTest(unittest.TestCase):
    def test_convert(self):
        self.assertEqual(dp_to_px(16.0, 2.0), 32)
        self.assertEqual(dp_to_px(10.0, 1.5), 15)


if __name__ == "__main__":
    unittest.main()
