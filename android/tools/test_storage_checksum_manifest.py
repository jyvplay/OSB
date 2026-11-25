#!/usr/bin/env python3
"""Differential oracle for StorageChecksumManifest (core/storage)."""
import unittest


class StorageChecksumManifest:
    def __init__(self):
        self.exp = {}

    def put(self, path: str, sha: str):
        self.exp[path] = sha.lower()

    def verify(self, path: str, sha: str) -> bool:
        return self.exp.get(path) == sha.lower()


class StorageChecksumManifestTest(unittest.TestCase):
    def test_verify(self):
        m = StorageChecksumManifest()
        m.put("a.bin", "A" * 64)
        self.assertTrue(m.verify("a.bin", "a" * 64))


if __name__ == "__main__":
    unittest.main()
