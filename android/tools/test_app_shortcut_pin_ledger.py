#!/usr/bin/env python3
"""Differential oracle for AppShortcutPinLedger (app)."""
import unittest


class AppShortcutPinLedger:
    def __init__(self, max_pins: int = 32):
        self.max_pins = max_pins
        self.pinned = []

    def pin(self, app_id: str) -> bool:
        if app_id in self.pinned:
            return True
        if len(self.pinned) >= self.max_pins:
            return False
        self.pinned.append(app_id)
        return True


class AppShortcutPinLedgerTest(unittest.TestCase):
    def test_pin(self):
        l = AppShortcutPinLedger(2)
        self.assertTrue(l.pin("a"))
        self.assertTrue(l.pin("b"))
        self.assertFalse(l.pin("c"))


if __name__ == "__main__":
    unittest.main()
