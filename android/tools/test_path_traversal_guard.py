#!/usr/bin/env python3
"""Independent differential oracle for PathTraversalGuard (feature/files).

Validates file paths against directory traversal attacks: rejects `..`
segments, absolute paths, NUL bytes. Cross-checks against the Kotlin
contract in feature/files/src/main/kotlin/dev/fusion/files/PathTraversalGuard.kt.

No Android SDK, Kotlin compiler or Gradle required; runs on host Python 3.11.
"""
from __future__ import annotations

import re
import sys
import unittest

sys.path.insert(0, str(__file__.rpartition("/tools/")[0] + "/tools"))

MAX_PATH_LEN = 4096
MAX_ROOT_LEN = 1024
MAX_SEGMENTS = 512
PATH_RE = re.compile(r"[ -~]*")


class PathTraversalGuard:
    def __init__(self, root):
        assert root and len(root) <= MAX_ROOT_LEN, "invalid root"
        assert ".." not in root, "root must not contain .."
        assert "\u0000" not in root, "root must not contain NUL"
        self._root = root

    def validate(self, path):
        assert path and len(path) <= MAX_PATH_LEN, "invalid path"
        assert PATH_RE.fullmatch(path), "path contains invalid chars"
        assert "\u0000" not in path, "path must not contain NUL"
        segments = [s for s in path.split("/") if s]
        assert len(segments) <= MAX_SEGMENTS, "too many path segments"
        if path.startswith("/"):
            raise ValueError(f"absolute paths refused: {path}")
        for seg in segments:
            if seg == "..":
                raise ValueError(f"traversal segment refused: {path}")
        resolved = (self._root + "/" + path) if not self._root.endswith("/") else (self._root + path)
        assert resolved.startswith(self._root), f"resolved path escapes root: {path}"
        return resolved

    def is_safe(self, path):
        try:
            self.validate(path)
            return True
        except ValueError:
            return False

    def root(self):
        return self._root


class PathTraversalGuardTest(unittest.TestCase):
    def setUp(self):
        self.g = PathTraversalGuard("/data/files")

    def test_safe_path(self):
        self.assertEqual(self.g.validate("doc.txt"), "/data/files/doc.txt")
        self.assertTrue(self.g.is_safe("doc.txt"))

    def test_subdirectory(self):
        self.assertEqual(self.g.validate("a/b/c.txt"), "/data/files/a/b/c.txt")

    def test_traversal_refused(self):
        self.assertFalse(self.g.is_safe("../etc/passwd"))
        self.assertFalse(self.g.is_safe("a/../../etc/passwd"))

    def test_absolute_path_refused(self):
        self.assertFalse(self.g.is_safe("/etc/passwd"))

    def test_null_byte_refused(self):
        with self.assertRaises(AssertionError):
            self.g.validate("doc\x00.txt")

    def test_empty_path_refused(self):
        with self.assertRaises(AssertionError):
            self.g.validate("")

    def test_path_too_long(self):
        with self.assertRaises(AssertionError):
            self.g.validate("x" * (MAX_PATH_LEN + 1))


if __name__ == "__main__":
    unittest.main()
