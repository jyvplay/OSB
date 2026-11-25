#!/usr/bin/env python3
"""Differential oracle for NativeElfMagicChecker (format/native)."""
import unittest


def is_valid_elf(hdr: bytes) -> bool:
    if len(hdr) < 6 or hdr[:4] != b"\x7fELF":
        return False
    return hdr[4] in (1, 2) and hdr[5] in (1, 2)


class NativeElfMagicCheckerTest(unittest.TestCase):
    def test_elf(self):
        self.assertTrue(is_valid_elf(b"\x7fELF\x02\x01"))
        self.assertFalse(is_valid_elf(b"\x7fELF\x03\x01"))


if __name__ == "__main__":
    unittest.main()
