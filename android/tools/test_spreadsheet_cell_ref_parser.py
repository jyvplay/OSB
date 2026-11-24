#!/usr/bin/env python3
"""Differential oracle for SpreadsheetCellRefParser (format/ooxml)."""
import re
import unittest

MAX_COL_INDEX = 16383
MAX_ROW_INDEX = 1048575
REF_RE = re.compile(r"^(\$?)([A-Z]{1,3})(\$?)([1-9][0-9]{0,6})$")


def parse_ref(ref: str):
    m = REF_RE.match(ref)
    if not m:
        raise ValueError(f"invalid cell ref: {ref}")
    col_abs = m.group(1) == "$"
    col_letters = m.group(2)
    row_abs = m.group(3) == "$"
    row_num = int(m.group(4))
    col = 0
    for ch in col_letters:
        col = col * 26 + (ord(ch) - ord("A") + 1)
    col -= 1
    row = row_num - 1
    assert 0 <= col <= MAX_COL_INDEX, "column out of bounds"
    assert 0 <= row <= MAX_ROW_INDEX, "row out of bounds"
    return col, row, col_abs, row_abs


class SpreadsheetCellRefParserTest(unittest.TestCase):
    def test_basic_refs(self):
        self.assertEqual(parse_ref("A1"), (0, 0, False, False))
        self.assertEqual(parse_ref("$B$12"), (1, 11, True, True))
        self.assertEqual(parse_ref("XFD1048576"), (16383, 1048575, False, False))

    def test_out_of_bounds(self):
        with self.assertRaises(AssertionError):
            parse_ref("XFE1")
        with self.assertRaises(AssertionError):
            parse_ref("A1048577")

    def test_invalid_syntax(self):
        with self.assertRaises(ValueError):
            parse_ref("A0")
        with self.assertRaises(ValueError):
            parse_ref("1A")


if __name__ == "__main__":
    unittest.main()
