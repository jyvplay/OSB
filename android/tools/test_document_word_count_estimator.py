#!/usr/bin/env python3
"""Differential oracle for DocumentWordCountEstimator (core/documents)."""
import unittest


def count_stats(text: str):
    if not text:
        return 0, 0, 0
    words = 0
    in_word = False
    lines = 1
    for ch in text:
        if ch == "\n":
            lines += 1
        if ch.isspace():
            in_word = False
        elif not in_word:
            in_word = True
            words += 1
    return len(text), words, lines


class DocumentWordCountEstimatorTest(unittest.TestCase):
    def test_count(self):
        self.assertEqual(count_stats(""), (0, 0, 0))
        self.assertEqual(count_stats("hello world\nfoo"), (15, 3, 2))


if __name__ == "__main__":
    unittest.main()
