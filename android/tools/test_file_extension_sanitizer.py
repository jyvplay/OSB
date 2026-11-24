#!/usr/bin/env python3
"""Differential oracle for FileExtensionSanitizer (core/files)."""
import re
import unittest

EXT_RE = re.compile(r"^[a-z0-9][a-z0-9._-]{0,31}$")


def sanitize(raw_ext: str) -> str:
    trimmed = raw_ext.strip().lstrip(".").lower()
    assert 0 < len(trimmed) <= 32
    assert "/" not in trimmed and "\\" not in trimmed and "\x00" not in trimmed
    assert EXT_RE.match(trimmed)
    return trimmed


class FileExtensionSanitizerTest(unittest.TestCase):
    def test_sanitize(self):
        self.assertEqual(sanitize("  .PNG "), "png")
        self.assertEqual(sanitize("tar.gz"), "tar.gz")
        with self.assertRaises(AssertionError):
            sanitize("../exe")


if __name__ == "__main__":
    unittest.main()
