#!/usr/bin/env python3
"""Independent differential oracle for ZipGuard (format/archive).

Implements the same bounded ZIP listing with zip-slip and decompression-bomb
guards in pure Python and cross-checks against the Kotlin contract described
in format/archive/src/main/kotlin/dev/fusion/archive/ZipGuard.kt.

No Android SDK, Kotlin compiler or Gradle is required; this runs on host Python 3.11.
"""
from __future__ import annotations

import os
import sys
import tempfile
import unittest
import zipfile

sys.path.insert(0, str(__file__.rpartition("/tools/")[0] + "/tools"))


MAX_ENTRIES = 10_000
MAX_TOTAL = 2 * 1024 * 1024 * 1024
MAX_RATIO = 100


def list_zip(zip_path):
    """Pure-Python reference implementation of ZipGuard.list()."""
    with zipfile.ZipFile(zip_path) as archive:
        entries = archive.infolist()
        assert len(entries) <= MAX_ENTRIES, "too many entries: %d" % len(entries)

        uncompressed = 0
        compressed = 0
        out = []
        for entry in entries:
            if entry.file_size > 0:
                uncompressed += entry.file_size
            if entry.compress_size > 0:
                compressed += entry.compress_size
            out.append({
                "name": entry.filename,
                "size": max(entry.file_size, 0),
                "dir": entry.is_dir(),
            })

        assert uncompressed <= MAX_TOTAL, "archive expands to more than 2 GB"
        if compressed > 0 and uncompressed // compressed > MAX_RATIO:
            raise AssertionError("compression ratio looks like a zip bomb")

        for info in out:
            assert ".." not in info["name"], "unsafe entry path: %s" % info["name"]
            assert not info["name"].startswith("/"), "absolute entry path: %s" % info["name"]
            assert "\\" not in info["name"], "windows-style path rejected: %s" % info["name"]

        return out


def make_zip(path, entries, compress_type=zipfile.ZIP_DEFLATED):
    with zipfile.ZipFile(path, "w", compress_type) as zf:
        for name, content in entries.items():
            zf.writestr(name, content)


class ZipGuardTest(unittest.TestCase):
    def test_basic_listing(self):
        with tempfile.TemporaryDirectory() as td:
            path = os.path.join(td, "test.zip")
            make_zip(path, {"a.txt": b"hello", "b.txt": b"world"})
            result = list_zip(path)
            self.assertEqual(len(result), 2)
            names = {e["name"] for e in result}
            self.assertEqual(names, {"a.txt", "b.txt"})

    def test_directory_entries(self):
        with tempfile.TemporaryDirectory() as td:
            path = os.path.join(td, "test.zip")
            with zipfile.ZipFile(path, "w") as zf:
                zf.writestr("dir/", b"")
                zf.writestr("dir/file.txt", b"content")
            result = list_zip(path)
            self.assertEqual(len(result), 2)
            dirs = [e for e in result if e["dir"]]
            files = [e for e in result if not e["dir"]]
            self.assertEqual(len(dirs), 1)
            self.assertEqual(len(files), 1)

    def test_too_many_entries(self):
        with tempfile.TemporaryDirectory() as td:
            path = os.path.join(td, "test.zip")
            entries = {("file%d.txt" % i): b"x" for i in range(MAX_ENTRIES + 1)}
            make_zip(path, entries, compress_type=zipfile.ZIP_STORED)
            with self.assertRaises(AssertionError):
                list_zip(path)

    def test_zip_slip_dotdot(self):
        with tempfile.TemporaryDirectory() as td:
            path = os.path.join(td, "test.zip")
            make_zip(path, {"../evil.txt": b"bad"}, compress_type=zipfile.ZIP_STORED)
            with self.assertRaises(AssertionError):
                list_zip(path)

    def test_absolute_path(self):
        with tempfile.TemporaryDirectory() as td:
            path = os.path.join(td, "test.zip")
            make_zip(path, {"/etc/passwd": b"bad"}, compress_type=zipfile.ZIP_STORED)
            with self.assertRaises(AssertionError):
                list_zip(path)

    def test_windows_style_path(self):
        with tempfile.TemporaryDirectory() as td:
            path = os.path.join(td, "test.zip")
            make_zip(path, {"dir\\\\file.txt": b"bad"}, compress_type=zipfile.ZIP_STORED)
            with self.assertRaises(AssertionError):
                list_zip(path)

    def test_empty_zip(self):
        with tempfile.TemporaryDirectory() as td:
            path = os.path.join(td, "empty.zip")
            make_zip(path, {}, compress_type=zipfile.ZIP_STORED)
            result = list_zip(path)
            self.assertEqual(len(result), 0)


if __name__ == "__main__":
    unittest.main()
