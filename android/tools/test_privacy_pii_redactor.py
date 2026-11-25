#!/usr/bin/env python3
"""Differential oracle for PrivacyPiiRedactor (core/privacy)."""
import re
import unittest

EMAIL_RE = re.compile(r"[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}")
IPV4_RE = re.compile(r"\b(?:\d{1,3}\.){3}\d{1,3}\b")


def redact(s: str) -> str:
    s = EMAIL_RE.sub("[REDACTED_EMAIL]", s)
    return IPV4_RE.sub("[REDACTED_IP]", s)


class PrivacyPiiRedactorTest(unittest.TestCase):
    def test_redact(self):
        self.assertEqual(redact("user alice@example.com at 192.168.1.5"), "user [REDACTED_EMAIL] at [REDACTED_IP]")


if __name__ == "__main__":
    unittest.main()
