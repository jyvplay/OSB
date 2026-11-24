#!/usr/bin/env python3
"""Differential oracle for SettingsOverrideLayer (core/settings)."""
import unittest


class SettingsOverrideLayer:
    def __init__(self):
        self.sys = {}
        self.pol = {}
        self.usr = {}

    def resolve(self, key: str):
        return self.usr.get(key, self.pol.get(key, self.sys.get(key)))


class SettingsOverrideLayerTest(unittest.TestCase):
    def test_precedence(self):
        s = SettingsOverrideLayer()
        s.sys["theme"] = "light"
        s.pol["theme"] = "dark"
        self.assertEqual(s.resolve("theme"), "dark")
        s.usr["theme"] = "amoled"
        self.assertEqual(s.resolve("theme"), "amoled")
        del s.usr["theme"]
        self.assertEqual(s.resolve("theme"), "dark")


if __name__ == "__main__":
    unittest.main()
