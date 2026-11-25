#!/usr/bin/env python3
"""Differential oracle for UrlSchemeAllowlist (feature/browser)."""
import unittest

DEFAULT_ALLOWED = {"https", "http", "about", "blob"}


def is_allowed(url: str, allowed=DEFAULT_ALLOWED) -> bool:
    if not url or len(url) > 4096:
        return False
    colon = url.find(":")
    if colon <= 0:
        return False
    return url[:colon].lower() in allowed


class UrlSchemeAllowlistTest(unittest.TestCase):
    def test_allowlist(self):
        self.assertTrue(is_allowed("https://example.com"))
        self.assertFalse(is_allowed("javascript:alert(1)"))
        self.assertFalse(is_allowed("file:///etc/passwd"))


if __name__ == "__main__":
    unittest.main()
