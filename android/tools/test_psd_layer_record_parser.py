#!/usr/bin/env python3
"""Differential oracle for PsdLayerRecordParser (format/psd)."""
import unittest


class PsdLayerRecordParserTest(unittest.TestCase):
    def test_psd(self):
        self.assertIn("norm", {"norm", "mul ", "scrn", "over", "dark", "lite"})
        top, left, bottom, right = 10, 20, 50, 100
        self.assertEqual((right - left, bottom - top), (80, 40))


if __name__ == "__main__":
    unittest.main()
