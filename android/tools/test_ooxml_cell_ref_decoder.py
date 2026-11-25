#!/usr/bin/env python3
"""Differential oracle for OoxmlCellRefDecoder (format/ooxml)."""
import re
import unittest


def decode(ref: str):
    m = re.fullmatch(r"([A-Za-z]{1,3})(\d{1,7})", ref.strip())
    assert m
    col = 0
    for ch in m.group(1).upper():
        col = col * 26 + (ord(ch) - ord("A") + 1)
    return int(m.group(2)) - 1, col - 1


class OoxmlCellRefDecoderTest(unittest.TestCase):
    def test_decode(self):
        self.assertEqual(decode("A1"), (0, 0))
        self.assertEqual(decode("AA10"), (9, 26))
        self.assertEqual(decode("XFD1048576"), (1_048_575, 16_383))


if __name__ == "__main__":
    unittest.main()
