#!/usr/bin/env python3
"""Independent differential oracle for BoyerMooreSearch (core/index).

Implements bounded Boyer-Moore-Horspool substring search: builds a bad-character
shift table from the needle and scans the haystack left to right, skipping
forward by the precomputed shift on mismatch. Returns the first index of the
needle in the haystack, or -1 if absent. The empty needle matches at index 0.
Cross-checks against the Kotlin contract in
core/index/src/main/kotlin/dev/fusion/index/BoyerMooreSearch.kt.

No Android SDK, Kotlin compiler or Gradle required; runs on host Python 3.11.
"""
from __future__ import annotations

import sys
import unittest

sys.path.insert(0, str(__file__.rpartition("/tools/")[0] + "/tools"))

MAX_HAYSTACK = 1 << 20  # 1 MiB chars
MAX_NEEDLE = 4096
ALPHABET = 256


def build_shift_table(needle: str) -> list:
    assert len(needle) <= MAX_NEEDLE, f"needle exceeds {MAX_NEEDLE} chars"
    table = [len(needle)] * ALPHABET
    for i in range(len(needle) - 1):
        table[ord(needle[i]) & 0xFF] = len(needle) - 1 - i
    return table


def index_of(haystack: str, needle: str) -> int:
    assert len(haystack) <= MAX_HAYSTACK, f"haystack exceeds {MAX_HAYSTACK} chars"
    n = len(needle)
    if n == 0:
        return 0
    if len(haystack) < n:
        return -1
    table = build_shift_table(needle)
    i = n - 1
    while i < len(haystack):
        j = n - 1
        while j >= 0 and (ord(haystack[i - (n - 1 - j)]) & 0xFF) == (ord(needle[j]) & 0xFF):
            j -= 1
        if j < 0:
            return i - n + 1
        i += table[ord(haystack[i]) & 0xFF]
    return -1


def contains(haystack: str, needle: str) -> bool:
    return index_of(haystack, needle) >= 0


class BoyerMooreSearchTest(unittest.TestCase):
    def test_empty_needle(self):
        self.assertEqual(index_of("anything", ""), 0)
        self.assertEqual(index_of("", ""), 0)

    def test_needle_longer_than_haystack(self):
        self.assertEqual(index_of("abc", "abcdef"), -1)

    def test_exact_match(self):
        self.assertEqual(index_of("hello world", "world"), 6)
        self.assertEqual(index_of("hello world", "hello"), 0)

    def test_no_match(self):
        self.assertEqual(index_of("hello world", "xyz"), -1)
        self.assertEqual(index_of("abc", "d"), -1)

    def test_single_char_match(self):
        self.assertEqual(index_of("abcdef", "d"), 3)
        self.assertEqual(index_of("abcdef", "a"), 0)
        self.assertEqual(index_of("abcdef", "f"), 5)

    def test_single_char_no_match(self):
        self.assertEqual(index_of("abcdef", "z"), -1)

    def test_overlapping_match(self):
        # "aaa" in "aaaaa" matches at 0
        self.assertEqual(index_of("aaaaa", "aaa"), 0)

    def test_repeated_pattern(self):
        self.assertEqual(index_of("abababab", "abab"), 0)
        self.assertEqual(index_of("abababab", "baba"), 1)

    def test_needle_at_end(self):
        self.assertEqual(index_of("hello world", "rld"), 8)

    def test_case_sensitive(self):
        self.assertEqual(index_of("Hello World", "world"), -1)
        self.assertEqual(index_of("Hello World", "World"), 6)

    def test_unicode_low_byte(self):
        # Characters above U+00FF are compared by low byte only
        self.assertEqual(index_of("héllo", "llo"), 2)

    def test_contains(self):
        self.assertTrue(contains("hello world", "world"))
        self.assertFalse(contains("hello world", "xyz"))
        self.assertTrue(contains("anything", ""))

    def test_needle_too_long(self):
        with self.assertRaises(AssertionError):
            build_shift_table("x" * (MAX_NEEDLE + 1))

    def test_haystack_too_large(self):
        with self.assertRaises(AssertionError):
            index_of("x" * (MAX_HAYSTACK + 1), "a")


if __name__ == "__main__":
    unittest.main()
