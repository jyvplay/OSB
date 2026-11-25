#!/usr/bin/env python3
"""Differential oracle for FindCaseFoldNormalizer (feature/find)."""
import re
import unittest


def normalize(text: str) -> str:
    return re.sub(r"\s+", " ", text.strip().lower())


class FindCaseFoldNormalizerTest(unittest.TestCase):
    def test_normalize(self):
        self.assertEqual(normalize("  Hello   WORLD \t "), "hello world")


if __name__ == "__main__":
    unittest.main()
