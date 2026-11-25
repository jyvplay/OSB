#!/usr/bin/env python3
"""Differential oracle for IndexStopwordFilter (core/index)."""
import unittest

STOPWORDS = {"a", "an", "and", "are", "as", "at", "be", "by", "for", "from", "in", "is", "it", "of", "on", "or", "that", "the", "to", "with"}


def filter_tokens(tokens):
    return [t for t in tokens if t.lower() not in STOPWORDS]


class IndexStopwordFilterTest(unittest.TestCase):
    def test_filter(self):
        self.assertEqual(filter_tokens(["The", "quick", "fox", "in", "box"]), ["quick", "fox", "box"])


if __name__ == "__main__":
    unittest.main()
