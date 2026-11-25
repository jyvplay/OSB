#!/usr/bin/env python3
"""Differential oracle for PdfHeaderVersionParser (format/pdf)."""
import re
import unittest

HDR_RE = re.compile(r"^%PDF-([12])\.([0-7])$")


def parse_version(line: str):
    m = HDR_RE.match(line.rstrip("\r\n"))
    return (int(m.group(1)), int(m.group(2))) if m else None


class PdfHeaderVersionParserTest(unittest.TestCase):
    def test_parse(self):
        self.assertEqual(parse_version("%PDF-1.7\n"), (1, 7))
        self.assertIsNone(parse_version("%PDF-3.0"))


if __name__ == "__main__":
    unittest.main()
