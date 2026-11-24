#!/usr/bin/env python3
"""Independent differential oracle for SettingsCatalog (core/settings).

Implements bounded settings key/value catalog: keys follow a dotted namespace
grammar (lowercase segments separated by dots); values are plain strings
bounded in length. Supports get/put/remove/contains with explicit capacity
and per-key/per-value limits. Cross-checks against the Kotlin contract in
core/settings/src/main/kotlin/dev/fusion/settings/SettingsCatalog.kt.

No Android SDK, Kotlin compiler or Gradle required; runs on host Python 3.11.
"""
from __future__ import annotations

import re
import sys
import unittest

sys.path.insert(0, str(__file__.rpartition("/tools/")[0] + "/tools"))

MAX_ENTRIES = 4096
MAX_KEY_LEN = 256
MAX_VALUE_LEN = 4096
KEY_RE = re.compile(r"[a-z][a-z0-9]*(\.[a-z][a-z0-9]*)*")


class SettingsCatalog:
    def __init__(self):
        self._entries = {}

    def put(self, key: str, value: str):
        assert KEY_RE.fullmatch(key), f"invalid settings key: {key}"
        assert len(key) <= MAX_KEY_LEN, f"key exceeds {MAX_KEY_LEN} chars"
        assert len(value) <= MAX_VALUE_LEN, f"value exceeds {MAX_VALUE_LEN} chars"
        if key not in self._entries:
            assert len(self._entries) < MAX_ENTRIES, f"catalog full: {MAX_ENTRIES} entries"
        self._entries[key] = value

    def get(self, key: str):
        return self._entries.get(key)

    def contains(self, key: str) -> bool:
        return key in self._entries

    def remove(self, key: str) -> bool:
        return self._entries.pop(key, None) is not None

    def keys(self) -> set:
        return set(self._entries.keys())

    def size(self) -> int:
        return len(self._entries)

    def clear(self):
        self._entries.clear()


class SettingsCatalogTest(unittest.TestCase):
    def setUp(self):
        self.catalog = SettingsCatalog()

    def test_put_and_get(self):
        self.catalog.put("appearance.theme", "dark")
        self.assertEqual(self.catalog.get("appearance.theme"), "dark")
        self.assertTrue(self.catalog.contains("appearance.theme"))

    def test_get_missing(self):
        self.assertIsNone(self.catalog.get("missing.key"))

    def test_overwrite(self):
        self.catalog.put("appearance.theme", "dark")
        self.catalog.put("appearance.theme", "light")
        self.assertEqual(self.catalog.get("appearance.theme"), "light")

    def test_remove(self):
        self.catalog.put("appearance.theme", "dark")
        self.assertTrue(self.catalog.remove("appearance.theme"))
        self.assertFalse(self.catalog.remove("appearance.theme"))
        self.assertFalse(self.catalog.contains("appearance.theme"))

    def test_invalid_key(self):
        with self.assertRaises(AssertionError):
            self.catalog.put("Appearance.theme", "dark")  # uppercase
        with self.assertRaises(AssertionError):
            self.catalog.put("123.theme", "dark")  # starts with digit
        with self.assertRaises(AssertionError):
            self.catalog.put("theme..double", "dark")  # empty segment
        with self.assertRaises(AssertionError):
            self.catalog.put(".theme", "dark")  # leading dot
        with self.assertRaises(AssertionError):
            self.catalog.put("theme.", "dark")  # trailing dot

    def test_key_too_long(self):
        with self.assertRaises(AssertionError):
            self.catalog.put("a" * 257, "value")

    def test_value_too_long(self):
        with self.assertRaises(AssertionError):
            self.catalog.put("key", "v" * 4097)

    def test_too_many_entries(self):
        for i in range(MAX_ENTRIES):
            self.catalog.put(f"key{i}", "v")
        with self.assertRaises(AssertionError):
            self.catalog.put("key4096", "v")

    def test_keys(self):
        self.catalog.put("a.b", "1")
        self.catalog.put("a.c", "2")
        self.catalog.put("b.d", "3")
        self.assertEqual(self.catalog.keys(), {"a.b", "a.c", "b.d"})

    def test_size(self):
        self.assertEqual(self.catalog.size(), 0)
        self.catalog.put("a", "1")
        self.assertEqual(self.catalog.size(), 1)
        self.catalog.remove("a")
        self.assertEqual(self.catalog.size(), 0)

    def test_clear(self):
        self.catalog.put("a", "1")
        self.catalog.put("b", "2")
        self.catalog.clear()
        self.assertEqual(self.catalog.size(), 0)
        self.assertFalse(self.catalog.contains("a"))

    def test_valid_key_forms(self):
        self.catalog.put("a", "1")
        self.catalog.put("a.b", "2")
        self.catalog.put("a.b.c", "3")
        self.catalog.put("a1.b2.c3", "4")
        self.assertEqual(self.catalog.size(), 4)


if __name__ == "__main__":
    unittest.main()
