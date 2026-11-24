#!/usr/bin/env python3
"""Independent differential oracle for AppShortcutResolver (feature/launcher).

Resolves shortcut names to target app IDs through a chain of mappings,
with cycle detection and depth limits. Cross-checks against the Kotlin
contract in feature/launcher/src/main/kotlin/dev/fusion/launcher/AppShortcutResolver.kt.

No Android SDK, Kotlin compiler or Gradle required; runs on host Python 3.11.
"""
from __future__ import annotations

import re
import sys
import unittest

sys.path.insert(0, str(__file__.rpartition("/tools/")[0] + "/tools"))

MAX_SHORTCUTS = 512
MAX_NAME_LEN = 128
MAX_ID_LEN = 128
MAX_DEPTH = 16
NAME_RE = re.compile(r"[a-z][a-z0-9_-]{0,63}")


class AppShortcutResolver:
    def __init__(self):
        self._shortcuts = {}

    def define(self, name, target):
        assert NAME_RE.fullmatch(name), f"invalid shortcut name: {name}"
        assert len(name) <= MAX_NAME_LEN, "name too long"
        assert len(target) <= MAX_ID_LEN, "target too long"
        assert len(self._shortcuts) < MAX_SHORTCUTS or name in self._shortcuts, f"too many shortcuts: {MAX_SHORTCUTS}"
        self._shortcuts[name] = target

    def resolve(self, name):
        assert NAME_RE.fullmatch(name), f"invalid shortcut name: {name}"
        seen = set()
        current = name
        depth = 0
        while True:
            target = self._shortcuts.get(current)
            if target is None:
                return current
            assert current not in seen, f"shortcut cycle at {current}"
            assert depth < MAX_DEPTH, f"resolution depth exceeded: {MAX_DEPTH}"
            seen.add(current)
            depth += 1
            current = target

    def remove(self, name):
        return self._shortcuts.pop(name, None) is not None

    def shortcut_count(self):
        return len(self._shortcuts)

    def is_defined(self, name):
        return name in self._shortcuts

    def clear(self):
        self._shortcuts.clear()


class AppShortcutResolverTest(unittest.TestCase):
    def setUp(self):
        self.r = AppShortcutResolver()

    def test_direct_resolve(self):
        self.r.define("calc", "org.calc.app")
        self.assertEqual(self.r.resolve("calc"), "org.calc.app")

    def test_chain_resolve(self):
        self.r.define("a", "b")
        self.r.define("b", "c")
        self.r.define("c", "org.final.app")
        self.assertEqual(self.r.resolve("a"), "org.final.app")

    def test_unknown_shortcut(self):
        self.assertEqual(self.r.resolve("nonexistent"), "nonexistent")

    def test_cycle_detected(self):
        self.r.define("a", "b")
        self.r.define("b", "a")
        with self.assertRaises(AssertionError):
            self.r.resolve("a")

    def test_depth_exceeded(self):
        for i in range(MAX_DEPTH + 1):
            self.r.define(f"n{i}", f"n{i + 1}")
        self.r.define(f"n{MAX_DEPTH + 1}", "final")
        with self.assertRaises(AssertionError):
            self.r.resolve("n0")

    def test_invalid_name(self):
        with self.assertRaises(AssertionError):
            self.r.define("BadName", "target")
        with self.assertRaises(AssertionError):
            self.r.define("1bad", "target")

    def test_remove(self):
        self.r.define("a", "b")
        self.assertTrue(self.r.remove("a"))
        self.assertFalse(self.r.remove("a"))
        self.assertFalse(self.r.is_defined("a"))


if __name__ == "__main__":
    unittest.main()
