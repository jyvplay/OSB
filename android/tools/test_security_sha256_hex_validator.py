#!/usr/bin/env python3
"""Differential oracle for SecuritySha256HexValidator (core/security)."""
import re
import unittest

HEX_RE = re.compile(r"^[0-9a-fA-F]{64}$")


class SecuritySha256HexValidatorTest(unittest.TestCase):
    def test_valid(self):
        self.assertTrue(bool(HEX_RE.match("a" * 64)))
        self.assertFalse(bool(HEX_RE.match("g" * 64)))


if __name__ == "__main__":
    unittest.main()
