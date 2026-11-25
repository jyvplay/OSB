#!/usr/bin/env python3
"""Differential oracle for BatteryThermalThrottleLevel (core/hw)."""
import unittest


def classify(temp_tenths_c: int) -> str:
    if temp_tenths_c < 380:
        return "NONE"
    if temp_tenths_c < 420:
        return "LIGHT"
    if temp_tenths_c < 450:
        return "MODERATE"
    if temp_tenths_c < 480:
        return "SEVERE"
    return "CRITICAL"


class BatteryThermalThrottleLevelTest(unittest.TestCase):
    def test_classify(self):
        self.assertEqual(classify(350), "NONE")
        self.assertEqual(classify(400), "LIGHT")
        self.assertEqual(classify(490), "CRITICAL")


if __name__ == "__main__":
    unittest.main()
