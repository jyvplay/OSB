#!/usr/bin/env python3
"""Differential oracle for SettingsJsonKeyEscaper (core/settings)."""
import unittest


def escape_json(s: str) -> str:
    return s.replace("\\", "\\\\").replace('"', '\\"').replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t")


class SettingsJsonKeyEscaperTest(unittest.TestCase):
    def test_escape(self):
        self.assertEqual(escape_json('a"b\nc'), 'a\\"b\\nc')


if __name__ == "__main__":
    unittest.main()
