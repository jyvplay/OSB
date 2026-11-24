#!/usr/bin/env python3
"""Independent differential oracle for CookieSameSiteValidator (feature/browser).

Validates cookie SameSite attributes (Strict/Lax/None) and enforces
SameSite=None requires Secure. Cross-checks against the Kotlin contract in
feature/browser/src/main/kotlin/dev/fusion/browser/model/CookieSameSiteValidator.kt.

No Android SDK, Kotlin compiler or Gradle required; runs on host Python 3.11.
"""
from __future__ import annotations

import re
import sys
import unittest

sys.path.insert(0, str(__file__.rpartition("/tools/")[0] + "/tools"))

MAX_COOKIES = 1024
MAX_NAME_LEN = 256
MAX_VALUE_LEN = 4096
MAX_DOMAIN_LEN = 256
SAMESITE = {"Strict", "Lax", "None"}
NAME_RE = re.compile(r"[^\x00-\x20\\\";,\\\\]+")


class CookieInfo:
    def __init__(self, name, value, domain, same_site, secure):
        self.name = name
        self.value = value
        self.domain = domain
        self.same_site = same_site
        self.secure = secure


class CookieSameSiteValidator:
    def __init__(self):
        self._cookies = {}

    def validate(self, name, value, domain, same_site, secure):
        assert name and len(name) <= MAX_NAME_LEN, "invalid cookie name"
        assert NAME_RE.fullmatch(name), "cookie name contains invalid chars"
        assert len(value) <= MAX_VALUE_LEN, "cookie value too long"
        assert domain is None or len(domain) <= MAX_DOMAIN_LEN, "domain too long"
        assert same_site is None or same_site in SAMESITE, f"invalid SameSite: {same_site}"

        if same_site == "None" and not secure:
            return (False, "SameSite=None requires Secure")
        return (True, None)

    def record_cookie(self, name, value, domain, same_site, secure):
        assert len(self._cookies) < MAX_COOKIES or name in self._cookies, f"too many cookies: {MAX_COOKIES}"
        self._cookies[name] = CookieInfo(name, value, domain, same_site, secure)

    def cookie_count(self):
        return len(self._cookies)

    def remove_cookie(self, name):
        return self._cookies.pop(name, None) is not None

    def clear(self):
        self._cookies.clear()


class CookieSameSiteValidatorTest(unittest.TestCase):
    def setUp(self):
        self.v = CookieSameSiteValidator()

    def test_strict_secure(self):
        valid, reason = self.v.validate("sess", "abc", None, "Strict", True)
        self.assertTrue(valid)

    def test_none_requires_secure(self):
        valid, reason = self.v.validate("sess", "abc", None, "None", False)
        self.assertFalse(valid)
        self.assertEqual(reason, "SameSite=None requires Secure")

    def test_none_secure_ok(self):
        valid, _ = self.v.validate("sess", "abc", None, "None", True)
        self.assertTrue(valid)

    def test_lax_ok(self):
        valid, _ = self.v.validate("sess", "abc", None, "Lax", False)
        self.assertTrue(valid)

    def test_no_samesite_ok(self):
        valid, _ = self.v.validate("sess", "abc", None, None, False)
        self.assertTrue(valid)

    def test_invalid_samesite(self):
        with self.assertRaises(AssertionError):
            self.v.validate("sess", "abc", None, "Invalid", False)

    def test_empty_name(self):
        with self.assertRaises(AssertionError):
            self.v.validate("", "abc", None, None, False)

    def test_name_too_long(self):
        with self.assertRaises(AssertionError):
            self.v.validate("x" * 257, "abc", None, None, False)

    def test_value_too_long(self):
        with self.assertRaises(AssertionError):
            self.v.validate("sess", "x" * 4097, None, None, False)

    def test_domain_too_long(self):
        with self.assertRaises(AssertionError):
            self.v.validate("sess", "abc", "x" * 257, None, False)


if __name__ == "__main__":
    unittest.main()
