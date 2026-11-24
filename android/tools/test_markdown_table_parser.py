#!/usr/bin/env python3
"""Independent differential oracle for MarkdownTableParser (core/documents).

Implements the same bounded Markdown pipe-table parser in pure Python and
cross-checks behaviour against the Kotlin contract described in
core/documents/src/main/kotlin/dev/fusion/documents/MarkdownTableParser.kt.

No Android SDK, Kotlin compiler or Gradle is required; this runs on host Python 3.11.
"""
from __future__ import annotations

import sys
import unittest
from typing import List

sys.path.insert(0, str(__file__.rpartition("/tools/")[0] + "/tools"))


def split_row(line: str) -> List[str]:
    s = line.strip()
    if s.startswith("|"):
        s = s[1:]
    if s.endswith("|"):
        s = s[:-1]
    cells: List[str] = []
    cell = []
    for c in s:
        if c == "|":
            cells.append("".join(cell))
            cell = []
        else:
            cell.append(c)
    cells.append("".join(cell))
    return cells


def is_separator_row(cells: List[str]) -> bool:
    return all(
        (t := c.strip()) != "" and all(ch in "-:" for ch in t) for c in cells
    )


def parse_markdown_table(text: str) -> dict:
    MAX_TEXT = 65_536
    MAX_COLUMNS = 256
    MAX_ROWS = 10_000

    assert len(text) <= MAX_TEXT, "text too long"
    if not text.strip():
        return {"header": [], "rows": []}

    lines = text.splitlines()
    header: List[str] = []
    rows: List[List[str]] = []
    in_table = False

    for line in lines:
        trimmed = line.strip()
        if not trimmed:
            in_table = False
            continue

        if trimmed.startswith("|") and "|" in trimmed:
            cells = split_row(trimmed)
            if is_separator_row(cells):
                in_table = True
                continue
            if in_table:
                assert len(cells) <= MAX_COLUMNS, "too many columns"
                assert len(cells) == len(header), (
                    f"row has {len(cells)} columns, expected {len(header)}"
                )
                assert len(rows) < MAX_ROWS, "too many rows"
                rows.append(cells)
            else:
                assert len(cells) <= MAX_COLUMNS, "too many columns"
                header = cells
                rows = []  # new table starts, reset rows
                in_table = True
        else:
            in_table = False

    return {"header": header, "rows": rows}


class MarkdownTableParserTest(unittest.TestCase):
    def test_basic_table(self):
        # No trimming: cells preserve leading/trailing spaces
        result = parse_markdown_table("| H1 | H2 |\n| --- | --- |\n| A | B |\n| C | D |")
        self.assertEqual(result["header"], [" H1 ", " H2 "])
        self.assertEqual(result["rows"], [[" A ", " B "], [" C ", " D "]])

    def test_empty_text(self):
        result = parse_markdown_table("")
        self.assertEqual(result["header"], [])
        self.assertEqual(result["rows"], [])

    def test_blank_lines_separate_tables(self):
        result = parse_markdown_table("| A | B |\n| - | - |\n| 1 | 2 |\n\n| C | D |\n| - | - |\n| 3 | 4 |")
        self.assertEqual(result["header"], [" C ", " D "])
        self.assertEqual(result["rows"], [[" 3 ", " 4 "]])

    def test_column_mismatch_is_error(self):
        with self.assertRaises(AssertionError):
            parse_markdown_table("| A | B |\n| - | - |\n| 1 | 2 | 3 |")

    def test_preserves_cell_content_no_trim(self):
        result = parse_markdown_table("| A | B |\n| - | - |\n|  hello  |  world  |")
        self.assertEqual(result["rows"], [["  hello  ", "  world  "]])

    def test_empty_records_allowed(self):
        # Cells contain single space, not empty string (no trimming)
        result = parse_markdown_table("| A | B |\n| - | - |\n| | |\n| x | y |")
        self.assertEqual(result["rows"], [[" ", " "], [" x ", " y "]])


if __name__ == "__main__":
    unittest.main()
