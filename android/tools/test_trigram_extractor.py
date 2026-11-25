#!/usr/bin/env python3
"""Differential oracle for TrigramExtractor (core/index)."""
import unittest


def extract(text: str):
    norm = text.lower()
    if len(norm) < 3:
        return set()
    return {norm[i : i + 3] for i in range(len(norm) - 2)}


class TrigramExtractorTest(unittest.TestCase):
    def test_extract(self):
        self.assertEqual(extract("ab"), set())
        self.assertEqual(extract("Hello"), {"hel", "ell", "llo"})


if __name__ == "__main__":
    unittest.main()
