#!/usr/bin/env python3
"""Differential oracle for ArchiveGzipHeaderChecker (format/archive)."""
import unittest


def is_valid_gzip(hdr: bytes) -> bool:
    return len(hdr) >= 10 and hdr[0] == 0x1F and hdr[1] == 0x8B and hdr[2] == 0x08 and (hdr[3] & 0xE0) == 0


class ArchiveGzipHeaderCheckerTest(unittest.TestCase):
    def test_gzip(self):
        self.assertTrue(is_valid_gzip(b"\x1f\x8b\x08\x00" + b"\x00" * 6))
        self.assertFalse(is_valid_gzip(b"\x1f\x8b\x08\xe0" + b"\x00" * 6))


if __name__ == "__main__":
    unittest.main()
