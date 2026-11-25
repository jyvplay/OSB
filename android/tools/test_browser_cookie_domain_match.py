#!/usr/bin/env python3
"""Differential oracle for BrowserCookieDomainMatch (feature/browser)."""
import unittest


def matches(host: str, dom: str, host_only: bool) -> bool:
    h = host.lower()
    d = dom.lower().lstrip(".")
    if h == d:
        return True
    if host_only:
        return False
    return h.endswith("." + d)


class BrowserCookieDomainMatchTest(unittest.TestCase):
    def test_match(self):
        self.assertTrue(matches("sub.example.com", ".example.com", False))
        self.assertFalse(matches("sub.example.com", "example.com", True))


if __name__ == "__main__":
    unittest.main()
