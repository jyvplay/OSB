#!/usr/bin/env python3
"""Differential oracle for DocumentLineIndex (core/documents)."""
import bisect
import unittest


class DocumentLineIndex:
    def __init__(self, text: str):
        self.doc_len = len(text)
        self.starts = [0] + [i + 1 for i, ch in enumerate(text) if ch == "\n"]

    def offset_to_line_col(self, offset: int):
        assert 0 <= offset <= self.doc_len
        idx = bisect.bisect_right(self.starts, offset) - 1
        return idx, offset - self.starts[idx]

    def line_col_to_offset(self, line: int, col: int):
        assert 0 <= line < len(self.starts) and col >= 0
        start = self.starts[line]
        end = self.starts[line + 1] - 1 if line + 1 < len(self.starts) else self.doc_len
        return min(start + col, end)


class DocumentLineIndexTest(unittest.TestCase):
    def test_roundtrip(self):
        idx = DocumentLineIndex("abc\ndefg\nhi")
        self.assertEqual(idx.offset_to_line_col(0), (0, 0))
        self.assertEqual(idx.offset_to_line_col(4), (1, 0))
        self.assertEqual(idx.offset_to_line_col(6), (1, 2))
        self.assertEqual(idx.line_col_to_offset(1, 2), 6)
        self.assertEqual(idx.line_col_to_offset(0, 99), 3)


if __name__ == "__main__":
    unittest.main()
