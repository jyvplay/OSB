#!/usr/bin/env python3
"""Differential oracle for HwUsbVendorProductPair (core/hw)."""
import re
import unittest

USB_RE = re.compile(r"^([0-9a-fA-F]{4}):([0-9a-fA-F]{4})$")


def parse_usb(s: str):
    m = USB_RE.match(s)
    if not m:
        raise ValueError(s)
    return int(m.group(1), 16), int(m.group(2), 16)


class HwUsbVendorProductPairTest(unittest.TestCase):
    def test_parse(self):
        self.assertEqual(parse_usb("18d1:4ee7"), (0x18D1, 0x4EE7))


if __name__ == "__main__":
    unittest.main()
