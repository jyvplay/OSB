#!/usr/bin/env python3
"""Differential oracle for ArchiveEntryPathNormalizer (format/archive)."""
import unittest

MAX_PATH_LEN = 1024
MAX_SEGMENTS = 128


def normalize(raw_path: str) -> str:
    assert 0 < len(raw_path) <= MAX_PATH_LEN, "path length out of bounds"
    assert "\x00" not in raw_path, "path contains NUL byte"
    parts = [p for p in raw_path.split("/") if p and p != "."]
    assert parts, "empty normalized path"
    assert len(parts) <= MAX_SEGMENTS, "too many path segments"
    for p in parts:
        assert p != "..", "path traversal '..' refused"
    joined = "/".join(parts)
    assert len(joined) <= MAX_PATH_LEN, "normalized path too long"
    return joined


class ArchiveEntryPathNormalizerTest(unittest.TestCase):
    def test_normalizes_dots_and_slashes(self):
        self.assertEqual(normalize("./a//b/./c.txt"), "a/b/c.txt")
        self.assertEqual(normalize("/etc/hosts"), "etc/hosts")

    def test_rejects_traversal(self):
        with self.assertRaises(AssertionError):
            normalize("a/../b")

    def test_rejects_nul_and_empty(self):
        with self.assertRaises(AssertionError):
            normalize("a\x00b")
        with self.assertRaises(AssertionError):
            normalize("")
        with self.assertRaises(AssertionError):
            normalize("./.")


if __name__ == "__main__":
    unittest.main()
