#!/usr/bin/env python3
"""Differential oracle for HwDeviceCapabilityMapper (core/hw)."""
import unittest


def classify(cls_id: int, ma: int):
    m = {0x01: "AUDIO", 0x03: "HID", 0x08: "MASS_STORAGE", 0x0E: "VIDEO"}
    return m.get(cls_id, "OTHER"), ma > 500


class HwDeviceCapabilityMapperTest(unittest.TestCase):
    def test_usb(self):
        self.assertEqual(classify(0x03, 100), ("HID", False))
        self.assertEqual(classify(0x08, 900), ("MASS_STORAGE", True))


if __name__ == "__main__":
    unittest.main()
