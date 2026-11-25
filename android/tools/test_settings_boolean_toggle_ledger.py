#!/usr/bin/env python3
"""Differential oracle for SettingsBooleanToggleLedger (feature/settings)."""
import unittest


class SettingsBooleanToggleLedger:
    def __init__(self):
        self.defaults = {}
        self.cur = {}

    def define(self, k: str, d: bool):
        self.defaults[k] = d
        self.cur.setdefault(k, d)

    def toggle(self, k: str) -> bool:
        self.cur[k] = not self.cur[k]
        return self.cur[k]

    def is_dirty(self, k: str) -> bool:
        return self.cur[k] != self.defaults[k]


class SettingsBooleanToggleLedgerTest(unittest.TestCase):
    def test_toggle(self):
        l = SettingsBooleanToggleLedger()
        l.define("dark", False)
        self.assertFalse(l.is_dirty("dark"))
        self.assertTrue(l.toggle("dark"))
        self.assertTrue(l.is_dirty("dark"))


if __name__ == "__main__":
    unittest.main()
