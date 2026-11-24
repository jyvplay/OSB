#!/usr/bin/env python3
"""Independent differential oracle for Soundex (core/index).

Implements American Soundex per NARA coding rules: first letter + 3 digits,
side-by-side rule, H/W transparency rule, vowel separation rule.
Cross-checks against the Kotlin contract in
core/index/src/main/kotlin/dev/fusion/index/Soundex.kt.

No Android SDK, Kotlin compiler or Gradle required; runs on host Python 3.11.
"""
from __future__ import annotations

import sys
import unittest

sys.path.insert(0, str(__file__.rpartition("/tools/")[0] + "/tools"))


def _code(c):
    if c in "BFPV":
        return 1
    if c in "CGJKQSXZ":
        return 2
    if c in "DT":
        return 3
    if c == "L":
        return 4
    if c in "MN":
        return 5
    if c == "R":
        return 6
    if c in "AEIOUY":
        return 0  # vowel separator
    if c in "HW":
        return -1  # transparent
    raise AssertionError(f"non-letter '{c}'")


def encode(name):
    assert name, "empty name"
    upper = name.upper()
    for c in upper:
        _code(c)  # validate every char up front
    first = upper[0]
    sb = [first]
    last_code = _code(first)  # first letter participates in side-by-side rule
    for i in range(1, len(upper)):
        c = _code(upper[i])
        if c == -1:
            continue  # H/W: transparent, lastCode survives
        if c == 0:
            last_code = 0  # vowel: separates
        else:
            if c != last_code:
                sb.append(str(c))
            last_code = c
        if len(sb) == 4:
            break
    while len(sb) < 4:
        sb.append("0")
    return "".join(sb)


def matches(a, b):
    return encode(a) == encode(b)


class SoundexTest(unittest.TestCase):
    def test_basic_encode(self):
        self.assertEqual(encode("ROBERT"), "R163")
        self.assertEqual(encode("RUBERT"), "R163")

    def test_nara_examples(self):
        self.assertEqual(encode("Washington"), "W252")
        self.assertEqual(encode("Lee"), "L000")
        self.assertEqual(encode("Gutierrez"), "G362")

    def test_side_by_side_rule(self):
        self.assertEqual(encode("Pfister"), "P236")

    def test_hw_transparency_rule(self):
        self.assertEqual(encode("Ashcraft"), "A261")

    def test_vowel_separation_rule(self):
        self.assertEqual(encode("Tymczak"), "T522")

    def test_padding(self):
        self.assertEqual(encode("A"), "A000")
        self.assertEqual(encode("Li"), "L000")

    def test_truncation(self):
        self.assertEqual(len(encode("Zzzzzz")), 4)

    def test_empty_name_rejected(self):
        with self.assertRaises(AssertionError):
            encode("")

    def test_non_letter_rejected(self):
        with self.assertRaises(AssertionError):
            encode("O'Brien")
        with self.assertRaises(AssertionError):
            encode("123")

    def test_case_insensitive(self):
        self.assertEqual(encode("Robert"), encode("robert"))
        self.assertEqual(encode("Robert"), encode("ROBERT"))

    def test_matches(self):
        self.assertTrue(matches("Robert", "Rupert"))
        self.assertFalse(matches("Robert", "Smith"))

    def test_same_name_matches(self):
        self.assertTrue(matches("Hello", "Hello"))

    def test_different_codes(self):
        self.assertNotEqual(encode("B"), encode("C"))  # 1 vs 2
        self.assertNotEqual(encode("B"), encode("D"))  # 1 vs 3


if __name__ == "__main__":
    unittest.main()
