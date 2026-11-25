#!/usr/bin/env python3
"""Differential oracle for CommandsShortcutChordParser (core/commands)."""
import unittest

MODS = {"ctrl", "alt", "shift", "meta"}


def canonicalize(chord: str) -> str:
    parts = [p.strip().lower() for p in chord.split("+") if p.strip()]
    mods = sorted({p for p in parts if p in MODS})
    keys = [p for p in parts if p not in MODS]
    assert len(keys) == 1
    return "+".join(mods + keys)


class CommandsShortcutChordParserTest(unittest.TestCase):
    def test_canon(self):
        self.assertEqual(canonicalize("Shift+Ctrl+P"), "ctrl+shift+p")


if __name__ == "__main__":
    unittest.main()
