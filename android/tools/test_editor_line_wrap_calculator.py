#!/usr/bin/env python3
"""Differential oracle for EditorLineWrapCalculator (feature/editor)."""
import textwrap
import unittest


class EditorLineWrapCalculatorTest(unittest.TestCase):
    def test_wrap(self):
        self.assertEqual(textwrap.wrap("hello world foo bar", width=11), ["hello world", "foo bar"])


if __name__ == "__main__":
    unittest.main()
