#!/usr/bin/env python3
"""Independent differential oracle for BomDetector (core/files).

Implements UTF Byte Order Mark (BOM) detection and stripping: UTF-8 (EF BB BF),
UTF-16 LE (FF FE), UTF-16 BE (FE FF), UTF-32 LE (FF FE 00 00), UTF-32 BE
(00 00 FE FF). Longer BOMs take precedence (UTF-32 before UTF-16 before UTF-8).
Cross-checks against the Kotlin contract in
core/files/src/main/kotlin/dev/fusion/files/BomDetector.kt.

No Android SDK, Kotlin compiler or Gradle required; runs on host Python 3.11.
"""
from __future__ import annotations

import sys
import unittest

sys.path.insert(0, str(__file__.rpartition("/tools/")[0] + "/tools"))

MAX_INPUT = 1 << 20  # 1 MiB
UTF8_BOM = b"\xEF\xBB\xBF"
UTF16_LE_BOM = b"\xFF\xFE"
UTF16_BE_BOM = b"\xFE\xFF"
UTF32_LE_BOM = b"\xFF\xFE\x00\x00"
UTF32_BE_BOM = b"\x00\x00\xFE\xFF"


def detect(input_bytes: bytes):
    assert len(input_bytes) <= MAX_INPUT, f"input exceeds {MAX_INPUT} bytes"
    if len(input_bytes) >= 4:
        head = input_bytes[0:4]
        if head == UTF32_LE_BOM:
            return ("UTF-32LE", input_bytes[4:])
        if head == UTF32_BE_BOM:
            return ("UTF-32BE", input_bytes[4:])
    if len(input_bytes) >= 2:
        head2 = input_bytes[0:2]
        if head2 == UTF16_LE_BOM:
            return ("UTF-16LE", input_bytes[2:])
        if head2 == UTF16_BE_BOM:
            return ("UTF-16BE", input_bytes[2:])
    if len(input_bytes) >= 3:
        head3 = input_bytes[0:3]
        if head3 == UTF8_BOM:
            return ("UTF-8", input_bytes[3:])
    return ("UTF-8", input_bytes[:])


def has_bom(input_bytes: bytes):
    assert len(input_bytes) <= MAX_INPUT, f"input exceeds {MAX_INPUT} bytes"
    if len(input_bytes) >= 4 and input_bytes[0:4] in (UTF32_LE_BOM, UTF32_BE_BOM):
        return True
    if len(input_bytes) >= 2 and input_bytes[0:2] in (UTF16_LE_BOM, UTF16_BE_BOM):
        return True
    if len(input_bytes) >= 3 and input_bytes[0:3] == UTF8_BOM:
        return True
    return False


def charset_name(input_bytes: bytes):
    return detect(input_bytes)[0]


class BomDetectorTest(unittest.TestCase):
    def test_no_bom_plain(self):
        cs, rest = detect(b"hello world")
        self.assertEqual(cs, "UTF-8")
        self.assertEqual(rest, b"hello world")

    def test_utf8_bom(self):
        cs, rest = detect(UTF8_BOM + b"hello")
        self.assertEqual(cs, "UTF-8")
        self.assertEqual(rest, b"hello")

    def test_utf16_le_bom(self):
        cs, rest = detect(UTF16_LE_BOM + b"hello")
        self.assertEqual(cs, "UTF-16LE")
        self.assertEqual(rest, b"hello")

    def test_utf16_be_bom(self):
        cs, rest = detect(UTF16_BE_BOM + b"hello")
        self.assertEqual(cs, "UTF-16BE")
        self.assertEqual(rest, b"hello")

    def test_utf32_le_bom(self):
        cs, rest = detect(UTF32_LE_BOM + b"hello")
        self.assertEqual(cs, "UTF-32LE")
        self.assertEqual(rest, b"hello")

    def test_utf32_be_bom(self):
        cs, rest = detect(UTF32_BE_BOM + b"hello")
        self.assertEqual(cs, "UTF-32BE")
        self.assertEqual(rest, b"hello")

    def test_utf32_le_takes_precedence_over_utf16_le(self):
        # UTF-32 LE starts with FF FE which is also UTF-16 LE BOM;
        # UTF-32 must win because it's longer and checked first.
        cs, rest = detect(UTF32_LE_BOM + b"x")
        self.assertEqual(cs, "UTF-32LE")
        self.assertEqual(rest, b"x")

    def test_utf32_be_takes_precedence_over_utf16_be(self):
        cs, rest = detect(UTF32_BE_BOM + b"x")
        self.assertEqual(cs, "UTF-32BE")
        self.assertEqual(rest, b"x")

    def test_has_bom(self):
        self.assertTrue(has_bom(UTF8_BOM + b"x"))
        self.assertTrue(has_bom(UTF16_LE_BOM + b"x"))
        self.assertTrue(has_bom(UTF16_BE_BOM + b"x"))
        self.assertTrue(has_bom(UTF32_LE_BOM + b"x"))
        self.assertTrue(has_bom(UTF32_BE_BOM + b"x"))
        self.assertFalse(has_bom(b"hello"))
        self.assertFalse(has_bom(b""))

    def test_charset_name(self):
        self.assertEqual(charset_name(UTF8_BOM + b"x"), "UTF-8")
        self.assertEqual(charset_name(UTF16_LE_BOM + b"x"), "UTF-16LE")
        self.assertEqual(charset_name(UTF32_BE_BOM + b"x"), "UTF-32BE")

    def test_empty_input(self):
        cs, rest = detect(b"")
        self.assertEqual(cs, "UTF-8")
        self.assertEqual(rest, b"")

    def test_only_bom(self):
        cs, rest = detect(UTF8_BOM)
        self.assertEqual(cs, "UTF-8")
        self.assertEqual(rest, b"")

    def test_input_too_large(self):
        with self.assertRaises(AssertionError):
            detect(b"x" * (MAX_INPUT + 1))
        with self.assertRaises(AssertionError):
            has_bom(b"x" * (MAX_INPUT + 1))


if __name__ == "__main__":
    unittest.main()
