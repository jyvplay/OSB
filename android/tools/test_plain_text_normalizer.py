#!/usr/bin/env python3
"""Independent differential oracle for PlainTextNormalizer (core/documents).

Implements bounded plain-text normalizer for a narrow ASCII-compatible subset
of Unicode normalization: strips zero-width code points (U+200B–U+200D,
U+FEFF), collapses runs of ASCII whitespace to a single space, trims leading
and trailing whitespace, and optionally lowercases ASCII letters A–Z.
Non-ASCII code points above U+007F pass through unchanged.
Cross-checks against the Kotlin contract in
core/documents/src/main/kotlin/dev/fusion/documents/PlainTextNormalizer.kt.

No Android SDK, Kotlin compiler or Gradle required; runs on host Python 3.11.
"""
from __future__ import annotations

import sys
import unittest

sys.path.insert(0, str(__file__.rpartition("/tools/")[0] + "/tools"))

MAX_INPUT = 1 << 16  # 64 KiB chars
ZERO_WIDTH = {0x200B, 0x200C, 0x200D, 0xFEFF}
WS = {0x09, 0x0A, 0x0D, 0x20}


def normalize(input_str: str, lowercase: bool = False) -> str:
    assert len(input_str) <= MAX_INPUT, f"input exceeds {MAX_INPUT} chars"
    sb = []
    prev_ws = False
    started = False
    for i in range(len(input_str)):
        cp = ord(input_str[i])
        if cp in ZERO_WIDTH:
            continue
        ch = chr(cp)
        if lowercase and "A" <= ch <= "Z":
            ch = chr(cp + 32)
        if ord(ch) in WS:
            if started and not prev_ws:
                sb.append(" ")
                prev_ws = True
        else:
            sb.append(ch)
            started = True
            prev_ws = False
    result = "".join(sb)
    if result.endswith(" "):
        result = result[:-1]
    return result


def normalize_whitespace_only(input_str: str) -> str:
    return normalize(input_str, lowercase=False)


class PlainTextNormalizerTest(unittest.TestCase):
    def test_plain_no_change(self):
        self.assertEqual(normalize("hello world"), "hello world")

    def test_whitespace_collapse(self):
        self.assertEqual(normalize("hello   world"), "hello world")
        self.assertEqual(normalize("hello\t\tworld"), "hello world")
        self.assertEqual(normalize("hello\n\nworld"), "hello world")
        self.assertEqual(normalize("hello \t \n world"), "hello world")

    def test_leading_trailing_trim(self):
        self.assertEqual(normalize("  hello world  "), "hello world")
        self.assertEqual(normalize("\thello\n"), "hello")

    def test_zero_width_stripped(self):
        self.assertEqual(normalize("he\u200Bllo\u200Cworld\u200D"), "helloworld")
        self.assertEqual(normalize("\uFEFFhello"), "hello")
        self.assertEqual(normalize("a\u200Bb\u200Cc\u200D"), "abc")

    def test_lowercase(self):
        self.assertEqual(normalize("Hello World", lowercase=True), "hello world")
        self.assertEqual(normalize("ABC", lowercase=True), "abc")

    def test_no_lowercase_by_default(self):
        self.assertEqual(normalize("Hello World"), "Hello World")

    def test_non_ascii_passthrough(self):
        self.assertEqual(normalize("héllo wörld"), "héllo wörld")
        self.assertEqual(normalize("日本語テスト"), "日本語テスト")

    def test_mixed_zero_width_and_whitespace(self):
        self.assertEqual(normalize("  he\u200Bllo\u200C   world\uFEFF  "), "hello world")

    def test_empty(self):
        self.assertEqual(normalize(""), "")
        self.assertEqual(normalize("   "), "")
        self.assertEqual(normalize("\t\n"), "")

    def test_only_whitespace(self):
        self.assertEqual(normalize(" \t \n "), "")

    def test_whitespace_only_mode(self):
        self.assertEqual(normalize_whitespace_only("Hello World"), "Hello World")
        self.assertEqual(normalize_whitespace_only("  a   b  "), "a b")

    def test_input_too_large(self):
        with self.assertRaises(AssertionError):
            normalize("x" * (MAX_INPUT + 1))


if __name__ == "__main__":
    unittest.main()
