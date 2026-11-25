#!/usr/bin/env python3
"""Differential oracle for PdfCrossRefOffsetValidator (format/pdf)."""
import unittest


def are_valid(offsets, file_size: int) -> bool:
    prev = -1
    for off in offsets:
        if off < 0 or off >= file_size or off <= prev:
            return False
        prev = off
    return True


class PdfCrossRefOffsetValidatorTest(unittest.TestCase):
    def test_valid(self):
        self.assertTrue(are_valid([10, 50, 120], 200))
        self.assertFalse(are_valid([10, 10, 120], 200))


if __name__ == "__main__":
    unittest.main()
