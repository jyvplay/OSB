#!/usr/bin/env python3
"""Independent differential oracle for VaultEntry (feature/vault).

Records encrypted vault entry metadata (path, nonce, tag, algorithm, key
id). Does not perform encryption. Cross-checks against the Kotlin contract
in feature/vault/src/main/kotlin/dev/fusion/vault/VaultEntry.kt.

No Android SDK, Kotlin compiler or Gradle required; runs on host Python 3.11.
"""
from __future__ import annotations

import sys
import unittest

sys.path.insert(0, str(__file__.rpartition("/tools/")[0] + "/tools"))

MAX_ENTRIES = 4096
MAX_PATH_LEN = 4096
NONCE_LEN = 12
TAG_LEN = 16
MAX_ALGO_LEN = 32
MAX_KEY_ID_LEN = 128
ALGO = {"AES-256-GCM", "ChaCha20-Poly1305"}


class VaultRecord:
    def __init__(self, path, nonce, tag, algorithm, key_id):
        self.path = path
        self.nonce = nonce
        self.tag = tag
        self.algorithm = algorithm
        self.key_id = key_id


class VaultEntry:
    def __init__(self):
        self._entries = {}

    def add_entry(self, path, nonce, tag, algorithm, key_id):
        assert len(path) <= MAX_PATH_LEN, "path too long"
        assert len(nonce) == NONCE_LEN, f"nonce must be {NONCE_LEN} bytes"
        assert len(tag) == TAG_LEN, f"tag must be {TAG_LEN} bytes"
        assert algorithm in ALGO, f"unsupported algorithm: {algorithm}"
        assert len(algorithm) <= MAX_ALGO_LEN, "algorithm too long"
        assert len(key_id) <= MAX_KEY_ID_LEN, "keyId too long"
        assert len(self._entries) < MAX_ENTRIES, "too many entries"
        assert path not in self._entries, f"duplicate path: {path}"
        self._entries[path] = VaultRecord(path, bytes(nonce), bytes(tag), algorithm, key_id)

    def remove_entry(self, path: str) -> bool:
        return self._entries.pop(path, None) is not None

    def entry(self, path: str):
        return self._entries.get(path)

    def contains(self, path: str) -> bool:
        return path in self._entries

    def size(self) -> int:
        return len(self._entries)

    def clear(self):
        self._entries.clear()


class VaultEntryTest(unittest.TestCase):
    def setUp(self):
        self.v = VaultEntry()

    def test_add_and_entry(self):
        nonce = bytes(range(12))
        tag = bytes(range(16))
        self.v.add_entry("/secret", nonce, tag, "AES-256-GCM", "key1")
        e = self.v.entry("/secret")
        self.assertIsNotNone(e)
        self.assertEqual(e.nonce, nonce)
        self.assertEqual(e.tag, tag)
        self.assertEqual(e.algorithm, "AES-256-GCM")
        self.assertEqual(e.key_id, "key1")

    def test_remove_entry(self):
        self.v.add_entry("/secret", bytes(12), bytes(16), "AES-256-GCM", "key1")
        self.assertTrue(self.v.remove_entry("/secret"))
        self.assertFalse(self.v.remove_entry("/secret"))

    def test_invalid_nonce(self):
        with self.assertRaises(AssertionError):
            self.v.add_entry("/a", bytes(10), bytes(16), "AES-256-GCM", "k")
        with self.assertRaises(AssertionError):
            self.v.add_entry("/a", bytes(13), bytes(16), "AES-256-GCM", "k")

    def test_invalid_tag(self):
        with self.assertRaises(AssertionError):
            self.v.add_entry("/a", bytes(12), bytes(15), "AES-256-GCM", "k")

    def test_unsupported_algorithm(self):
        with self.assertRaises(AssertionError):
            self.v.add_entry("/a", bytes(12), bytes(16), "DES-ECB", "k")

    def test_path_too_long(self):
        with self.assertRaises(AssertionError):
            self.v.add_entry("x" * 4097, bytes(12), bytes(16), "AES-256-GCM", "k")

    def test_cha_chacha(self):
        self.v.add_entry("/a", bytes(12), bytes(16), "ChaCha20-Poly1305", "k2")
        e = self.v.entry("/a")
        self.assertEqual(e.algorithm, "ChaCha20-Poly1305")


if __name__ == "__main__":
    unittest.main()
