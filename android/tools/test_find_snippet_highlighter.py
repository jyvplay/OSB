#!/usr/bin/env python3
"""Differential oracle for FindSnippetHighlighter (feature/find)."""
import unittest


def extract_snippet(text: str, start: int, length: int, ctx: int) -> str:
    s = max(0, start - ctx)
    e = min(len(text), start + length + ctx)
    return "" if s > e else text[s:e]


class FindSnippetHighlighterTest(unittest.TestCase):
    def test_snippet(self):
        self.assertEqual(extract_snippet("0123456789", 4, 2, 2), "234567")


if __name__ == "__main__":
    unittest.main()
