#!/usr/bin/env python3
"""Differential oracle for AppIntentRouteDispatcher (app)."""
import unittest


class Dispatcher:
    def __init__(self):
        self.r = {}

    def reg(self, hp, mod):
        self.r[hp.lower()] = mod

    def resolve(self, uri):
        if not uri.startswith("osb://"):
            return None
        return self.r.get(uri[6:].split("?", 1)[0].lower())


class AppIntentRouteDispatcherTest(unittest.TestCase):
    def test_dispatch(self):
        d = Dispatcher()
        d.reg("editor/open", "feature:editor")
        self.assertEqual(d.resolve("osb://editor/open?file=a.txt"), "feature:editor")
        self.assertIsNone(d.resolve("https://evil.com"))


if __name__ == "__main__":
    unittest.main()
