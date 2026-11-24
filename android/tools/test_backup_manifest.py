#!/usr/bin/env python3
"""Independent differential oracle for BackupManifest (feature/backup).

Records backup entries (path, size, SHA-256 checksum) with manifest version
and overall metadata. Cross-checks against the Kotlin contract in
feature/backup/src/main/kotlin/dev/fusion/backup/BackupManifest.kt.

No Android SDK, Kotlin compiler or Gradle required; runs on host Python 3.11.
"""
from __future__ import annotations

import re
import sys
import unittest

sys.path.insert(0, str(__file__.rpartition("/tools/")[0] + "/tools"))

MAX_ENTRIES = 8192
MAX_PATH_LEN = 4096
MAX_SIZE = 1 << 40
MAX_VERSION_LEN = 32
HEX64_RE = re.compile(r"[0-9a-fA-F]{64}")
VERSION_RE = re.compile(r"[0-9]+(\.[0-9]+)*")


class BackupEntry:
    def __init__(self, path: str, size: int, checksum: str):
        self.path = path
        self.size = size
        self.checksum = checksum


class BackupManifest:
    def __init__(self, version: str):
        assert VERSION_RE.fullmatch(version), f"invalid version: {version}"
        assert len(version) <= MAX_VERSION_LEN, "version too long"
        self._version = version
        self._entries = {}

    def add_entry(self, path: str, size: int, checksum: str):
        assert HEX64_RE.fullmatch(checksum), "checksum must be 64 hex chars"
        assert len(path) <= MAX_PATH_LEN, "path too long"
        assert 0 <= size <= MAX_SIZE, "size out of bounds"
        assert len(self._entries) < MAX_ENTRIES, "too many entries"
        assert path not in self._entries, f"duplicate path: {path}"
        self._entries[path] = BackupEntry(path, size, checksum)

    def remove_entry(self, path: str) -> bool:
        return self._entries.pop(path, None) is not None

    def manifest(self):
        total_size = sum(e.size for e in self._entries.values())
        return {
            "version": self._version,
            "entry_count": len(self._entries),
            "total_size": total_size,
            "entries": list(self._entries.values()),
        }

    def entry(self, path: str):
        return self._entries.get(path)

    def contains(self, path: str) -> bool:
        return path in self._entries

    def size(self) -> int:
        return len(self._entries)

    def clear(self):
        self._entries.clear()


class BackupManifestTest(unittest.TestCase):
    def setUp(self):
        self.m = BackupManifest("1.0.0")

    def test_add_and_entry(self):
        self.m.add_entry("/data/file.txt", 1024, "a" * 64)
        e = self.m.entry("/data/file.txt")
        self.assertIsNotNone(e)
        self.assertEqual(e.size, 1024)
        self.assertEqual(e.checksum, "a" * 64)

    def test_manifest(self):
        self.m.add_entry("/a", 100, "b" * 64)
        self.m.add_entry("/b", 200, "c" * 64)
        man = self.m.manifest()
        self.assertEqual(man["version"], "1.0.0")
        self.assertEqual(man["entry_count"], 2)
        self.assertEqual(man["total_size"], 300)

    def test_remove_entry(self):
        self.m.add_entry("/a", 100, "b" * 64)
        self.assertTrue(self.m.remove_entry("/a"))
        self.assertFalse(self.m.remove_entry("/a"))
        self.assertFalse(self.m.contains("/a"))

    def test_invalid_checksum(self):
        with self.assertRaises(AssertionError):
            self.m.add_entry("/a", 100, "not-hex")
        with self.assertRaises(AssertionError):
            self.m.add_entry("/a", 100, "a" * 63)
        with self.assertRaises(AssertionError):
            self.m.add_entry("/a", 100, "a" * 65)

    def test_path_too_long(self):
        with self.assertRaises(AssertionError):
            self.m.add_entry("x" * 4097, 100, "c" * 64)

    def test_size_out_of_bounds(self):
        with self.assertRaises(AssertionError):
            self.m.add_entry("/a", -1, "d" * 64)
        with self.assertRaises(AssertionError):
            self.m.add_entry("/a", MAX_SIZE + 1, "d" * 64)

    def test_invalid_version(self):
        with self.assertRaises(AssertionError):
            BackupManifest("not-a-version")


if __name__ == "__main__":
    unittest.main()
