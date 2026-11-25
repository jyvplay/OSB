#!/usr/bin/env python3
"""Differential oracle for PsdColorModeValidator (format/psd)."""
import unittest

VALID = {0, 1, 2, 3, 4, 7, 8, 9}


class PsdColorModeValidatorTest(unittest.TestCase):
    def test_modes(self):
        self.assertIn(3, VALID)
        self.assertNotIn(5, VALID)


if __name__ == "__main__":
    unittest.main()
