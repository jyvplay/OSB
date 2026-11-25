#!/usr/bin/env python3
"""Differential oracle for OoxmlSheetNameValidator (format/ooxml)."""
import unittest

FORBIDDEN = set(r"\/?*[] :")
FORBIDDEN = set(r"\/?*[]") | {":"}


def is_valid(name: str) -> bool:
    if not name or len(name) > 31 or name.startswith("'") or name.endswith("'"):
        return False
    return not any(c in FORBIDDEN for c in name)


class OoxmlSheetNameValidatorTest(unittest.TestCase):
    def test_sheet_name(self):
        self.assertTrue(is_valid("Sheet1"))
        self.assertFalse(is_valid("Bad:Name"))


if __name__ == "__main__":
    unittest.main()
