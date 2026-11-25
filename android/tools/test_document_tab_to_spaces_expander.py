#!/usr/bin/env python3
"""Differential oracle for DocumentTabToSpacesExpander (core/documents)."""
import unittest


def expand_line(line: str, tab_w: int = 4) -> str:
    return line.expandtabs(tab_w)


class DocumentTabToSpacesExpanderTest(unittest.TestCase):
    def test_expand(self):
        self.assertEqual(expand_line("a\tb", 4), "a   b")


if __name__ == "__main__":
    unittest.main()
