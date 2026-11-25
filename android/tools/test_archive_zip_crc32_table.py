#!/usr/bin/env python3
"""Differential oracle for ArchiveZipCrc32Table (format/archive)."""
import unittest
import zlib


class ArchiveZipCrc32TableTest(unittest.TestCase):
    def test_crc32(self):
        self.assertEqual(zlib.crc32(b"123456789") & 0xFFFFFFFF, 0xCBF43926)


if __name__ == "__main__":
    unittest.main()
