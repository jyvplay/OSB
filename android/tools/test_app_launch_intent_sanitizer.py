#!/usr/bin/env python3
"""Differential oracle for AppLaunchIntentSanitizer (app)."""
import unittest

ALLOWED = {"fusion", "https", "content"}


def is_safe(uri: str) -> bool:
    if not uri or len(uri) > 2048:
        return False
    if any(ord(c) < 0x20 or ord(c) == 0x7F for c in uri):
        return False
    colon = uri.find(":")
    if colon <= 0:
        return False
    return uri[:colon].lower() in ALLOWED


class AppLaunchIntentSanitizerTest(unittest.TestCase):
    def test_safe(self):
        self.assertTrue(is_safe("fusion://workspace/main"))
        self.assertFalse(is_safe("javascript:alert(1)"))
        self.assertFalse(is_safe("https://a\nb"))


if __name__ == "__main__":
    unittest.main()
