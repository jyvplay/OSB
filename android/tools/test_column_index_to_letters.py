#!/usr/bin/env python3
"""Differential oracle for ColumnIndexToLetters (format/ooxml)."""
import unittest


def to_letters(col_index: int) -> str:
    assert 0 <= col_index <= 16383
    out = []
    n = col_index + 1
    while n > 0:
        rem = (n - 1) % 26
        out.append(chr(ord("A") + rem))
        n = (n - 1) // 26
    return "".join(reversed(out))


class ColumnIndexToLettersTest(unittest.TestCase):
    def test_letters(self):
        self.assertEqual(to_letters(0), "A")
        self.assertEqual(to_letters(25), "Z")
        self.assertEqual(to_letters(26), "AA")
        self.assertEqual(to_letters(16383), "XFD")


if __name__ == "__main__":
    unittest.main()
