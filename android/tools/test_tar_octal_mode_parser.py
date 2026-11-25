#!/usr/bin/env python3
"""Differential oracle for TarOctalModeParser (format/archive)."""
import unittest


def parse_octal(field: str) -> int:
    trimmed = field.strip(" \x00")
    if not trimmed:
        return 0
    acc = 0
    for ch in trimmed:
        assert "0" <= ch <= "7"
        acc = (acc << 3) + (ord(ch) - ord("0"))
    return acc


class TarOctalModeParserTest(unittest.TestCase):
    def test_parse(self):
        self.assertEqual(parse_octal("0000755 "), 0o755)
        self.assertEqual(parse_octal("  \x00"), 0)


if __name__ == "__main__":
    unittest.main()
