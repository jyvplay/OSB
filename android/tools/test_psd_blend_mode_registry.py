#!/usr/bin/env python3
"""Differential oracle for PsdBlendModeRegistry (format/psd)."""
import unittest

MODES = {
    "norm": "NORMAL",
    "mul ": "MULTIPLY",
    "scrn": "SCREEN",
    "over": "OVERLAY",
    "dark": "DARKEN",
    "lite": "LIGHTEN",
}


class PsdBlendModeRegistryTest(unittest.TestCase):
    def test_resolve(self):
        self.assertEqual(MODES.get("mul ", "UNKNOWN"), "MULTIPLY")
        self.assertEqual(MODES.get("xxxx", "UNKNOWN"), "UNKNOWN")


if __name__ == "__main__":
    unittest.main()
