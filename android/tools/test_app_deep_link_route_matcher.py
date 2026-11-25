#!/usr/bin/env python3
"""Differential oracle for AppDeepLinkRouteMatcher (app)."""
import unittest


def match_host(uri: str, allowed):
    if not uri.startswith("fusion://"):
        return None
    host = uri[len("fusion://") :].split("/")[0].split("?")[0].lower()
    return host if host in allowed else None


class AppDeepLinkRouteMatcherTest(unittest.TestCase):
    def test_match(self):
        self.assertEqual(match_host("fusion://Editor/open?id=1", {"editor", "files"}), "editor")
        self.assertIsNone(match_host("https://editor/open", {"editor"}))


if __name__ == "__main__":
    unittest.main()
