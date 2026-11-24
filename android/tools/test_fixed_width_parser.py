#!/usr/bin/env python3
"""Independent differential oracle for FixedWidthParser (format/native).

Implements bounded fixed-width text parsing in pure Python and cross-checks
against the Kotlin contract described in
format/native/src/main/kotlin/dev/fusion/native/FixedWidthParser.kt.

No Android SDK, Kotlin compiler or Gradle is required; this runs on host Python 3.11.
"""
from __future__ import annotations

import sys
import unittest
from typing import List

sys.path.insert(0, str(__file__.rpartition("/tools/")[0] + "/tools"))


MAX_TEXT = 65_536
MAX_COLUMNS = 256
MAX_ROWS = 10_000
MAX_COLUMN_WIDTH = 4_096
MAX_TOTAL_WIDTH = 65_536


class Column:
    def __init__(self, name: str, width: int):
        assert 1 <= width <= MAX_COLUMN_WIDTH, f"width {width} out of range"
        assert len(name) <= 128, "column name too long"
        self.name = name
        self.width = width


class Schema:
    def __init__(self, columns: List[Column]):
        assert len(columns) <= MAX_COLUMNS, "too many columns"
        assert len(columns) >= 1, "schema must define at least one column"
        self.columns = columns

    @property
    def total_width(self) -> int:
        return sum(c.width for c in self.columns)

    @property
    def column_names(self) -> List[str]:
        return [c.name for c in self.columns]


def parse_fixed_width(text: str, schema: Schema, trim: bool = False) -> dict:
    assert len(text) <= MAX_TEXT, "text too long"
    width = schema.total_width
    assert width <= MAX_TOTAL_WIDTH, "total width too large"

    rows: List[List[str]] = []

    for line in text.splitlines():
        if not line.strip():
            continue
        assert len(line) == width, f"line length {len(line)} != schema width {width}"

        values = []
        pos = 0
        for col in schema.columns:
            cell = line[pos:pos + col.width]
            if trim:
                cell = cell.strip()
            values.append(cell)
            pos += col.width
        rows.append(values)

    assert len(rows) <= MAX_ROWS, "too many rows"

    return {"schema": schema, "rows": rows}


class FixedWidthParserTest(unittest.TestCase):
    def test_basic_parse(self):
        # Schema: first(10) + last(10) + age(3) = 23 total width
        schema = Schema([
            Column("first", 10),
            Column("last", 10),
            Column("age", 3),
        ])
        # Build exact-width lines programmatically
        line1 = "John      " + "Doe       " + "30 "
        line2 = "Jane      " + "Smith     " + "25 "
        assert len(line1) == 23, f"line1 length {len(line1)} != 23"
        assert len(line2) == 23, f"line2 length {len(line2)} != 23"
        text = line1 + "\n" + line2 + "\n"
        result = parse_fixed_width(text, schema)
        self.assertEqual(result["rows"], [
            ["John      ", "Doe       ", "30 "],
            ["Jane      ", "Smith     ", "25 "],
        ])

    def test_trim(self):
        # Schema: first(10) + last(10) = 20 total width
        schema = Schema([
            Column("first", 10),
            Column("last", 10),
        ])
        text = "John      " + "Doe       " + "\n"
        assert len(text.rstrip("\n")) == 20, f"line length {len(text.rstrip(chr(10)))} != 20"
        result = parse_fixed_width(text, schema, trim=True)
        self.assertEqual(result["rows"], [["John", "Doe"]])

    def test_blank_lines_skipped(self):
        schema = Schema([Column("x", 5)])
        text = "hello\n\nworld\n"
        result = parse_fixed_width(text, schema)
        self.assertEqual(result["rows"], [["hello"], ["world"]])

    def test_wrong_line_length_is_error(self):
        schema = Schema([Column("x", 5)])
        with self.assertRaises(AssertionError):
            parse_fixed_width("hello\nworld!\n", schema)

    def test_empty_text(self):
        schema = Schema([Column("x", 5)])
        result = parse_fixed_width("", schema)
        self.assertEqual(result["rows"], [])

    def test_column_width_bounds(self):
        with self.assertRaises(AssertionError):
            Column("x", 0)
        with self.assertRaises(AssertionError):
            Column("x", MAX_COLUMN_WIDTH + 1)

    def test_schema_column_count_bounds(self):
        with self.assertRaises(AssertionError):
            Schema([Column("x", 5)] * (MAX_COLUMNS + 1))

    def test_total_width_bound(self):
        # Total width exceeds MAX_TOTAL_WIDTH: caught in parse_fixed_width
        columns = [Column("c", MAX_COLUMN_WIDTH)] * 17  # 17 * 4096 = 69632 > 65536
        schema = Schema(columns)
        text = "x" * schema.total_width + "\n"
        with self.assertRaises(AssertionError):
            parse_fixed_width(text, schema)


if __name__ == "__main__":
    unittest.main()
