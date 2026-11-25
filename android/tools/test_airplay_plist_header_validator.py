#!/usr/bin/env python3
"""Differential oracle for AirPlayPlistHeaderValidator (cast/airplay)."""
import unittest


def detect_fmt(b: bytes) -> str:
    if b.startswith(b"bplist00"):
        return "BINARY_BPLIST00"
    s = b[:64].decode("utf-8", errors="ignore").lstrip()
    if s.startswith("<?xml") or s.startswith("<plist"):
        return "XML_PLIST"
    return "UNKNOWN"


class AirPlayPlistHeaderValidatorTest(unittest.TestCase):
    def test_plist(self):
        self.assertEqual(detect_fmt(b"bplist00\x01\x02"), "BINARY_BPLIST00")
        self.assertEqual(detect_fmt(b"  <plist version='1.0'>"), "XML_PLIST")


if __name__ == "__main__":
    unittest.main()
