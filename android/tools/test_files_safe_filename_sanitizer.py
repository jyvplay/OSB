#!/usr/bin/env python3
"""Differential oracle for FilesSafeFilenameSanitizer (core/files)."""
import unittest

FORBIDDEN = set('/\\:*?"<>|')


def sanitize(name: str) -> str:
    out = "".join("_" if (ord(c) < 0x20 or c in FORBIDDEN) else c for c in name.strip())
    assert out and out not in (".", "..")
    return out


class FilesSafeFilenameSanitizerTest(unittest.TestCase):
    def test_sanitize(self):
        self.assertEqual(sanitize("a/b:c?.txt"), "a_b_c_.txt")


if __name__ == "__main__":
    unittest.main()
