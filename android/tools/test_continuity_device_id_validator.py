#!/usr/bin/env python3
"""Differential oracle for ContinuityDeviceIdValidator (feature/continuity)."""
import re
import unittest

ID_RE = re.compile(r"^[a-zA-Z0-9_-]{4,64}$")


class ContinuityDeviceIdValidatorTest(unittest.TestCase):
    def test_valid(self):
        self.assertTrue(bool(ID_RE.match("pixel-9-pro")))
        self.assertFalse(bool(ID_RE.match("ab")))


if __name__ == "__main__":
    unittest.main()
