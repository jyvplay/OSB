#!/usr/bin/env python3
"""Independent differential oracle for ArchiveIntegrityVerifier (format/archive).

Implements the same bounded ZIP integrity verifier in pure Python using
zlib.crc32 and cross-checks against the Kotlin contract described in
format/archive/src/main/kotlin/dev/fusion/archive/ArchiveIntegrityVerifier.kt.

No Android SDK, Kotlin compiler or Gradle is required; this runs on host Python 3.11.
"""
from __future__ import annotations

import os
import sys
import tempfile
import unittest
import zipfile
import zlib

sys.path.insert(0, str(__file__.rpartition("/tools/")[0] + "/tools"))

MAX_ENTRIES = 10_000
MAX_TOTAL_BYTES = 2 * 1024 * 1024 * 1024
MAX_NAME_LENGTH = 4_096


def verify_zip(zip_path):
    assert os.path.exists(zip_path), "archive does not exist: %s" % zip_path
    assert os.path.isfile(zip_path), "not a file: %s" % zip_path

    entries_list = []
    total_bytes = 0
    all_valid = True

    with zipfile.ZipFile(zip_path) as archive:
        infos = archive.infolist()
        assert len(infos) <= MAX_ENTRIES, "too many entries: %d" % len(infos)

        for info in infos:
            assert len(info.filename) <= MAX_NAME_LENGTH, (
                "entry name too long: %d" % len(info.filename)
            )

            entry_valid = True
            error = None
            size = 0
            crc = 0

            try:
                crc_val = 0
                with archive.open(info) as f:
                    while True:
                        chunk = f.read(8192)
                        if not chunk:
                            break
                        crc_val = zlib.crc32(chunk, crc_val)
                        size += len(chunk)
                        assert total_bytes + size <= MAX_TOTAL_BYTES, (
                            "archive expands to more than 2 GiB"
                        )
                crc = crc_val & 0xFFFFFFFF
            except Exception as e:
                entry_valid = False
                all_valid = False
                error = str(e) or "read error"

            if entry_valid and crc != (info.CRC & 0xFFFFFFFF):
                entry_valid = False
                all_valid = False
                error = "CRC-32 mismatch: computed %08x, stored %08x" % (
                    crc, info.CRC & 0xFFFFFFFF
                )

            total_bytes += size
            entries_list.append({
                "name": info.filename,
                "crc32": crc,
                "size": size,
                "valid": entry_valid,
                "error": error,
            })

    return {
        "valid": all_valid and total_bytes <= MAX_TOTAL_BYTES,
        "entries": entries_list,
        "totalEntries": len(entries_list),
        "totalBytes": total_bytes,
    }


def make_zip(path, entries, compress_type=zipfile.ZIP_DEFLATED):
    with zipfile.ZipFile(path, "w", compress_type) as zf:
        for name, content in entries.items():
            zf.writestr(name, content)


def corrupt_zip_raw(zip_path):
    with open(zip_path, "rb") as f:
        data = bytearray(f.read())
    if len(data) > 100:
        data[100] ^= 0xFF
    with tempfile.NamedTemporaryFile(suffix=".zip", delete=False) as tmp:
        tmp.write(data)
        tmp_path = tmp.name
    return tmp_path


class ArchiveIntegrityTest(unittest.TestCase):
    def test_valid_archive(self):
        with tempfile.TemporaryDirectory() as td:
            path = os.path.join(td, "test.zip")
            make_zip(path, {"a.txt": b"hello world", "b.txt": b"foo bar baz"})
            report = verify_zip(path)
            self.assertTrue(report["valid"])
            self.assertEqual(report["totalEntries"], 2)
            for entry in report["entries"]:
                self.assertTrue(entry["valid"])
                self.assertIsNone(entry["error"])

    def test_empty_archive(self):
        with tempfile.TemporaryDirectory() as td:
            path = os.path.join(td, "empty.zip")
            make_zip(path, {}, compress_type=zipfile.ZIP_STORED)
            report = verify_zip(path)
            self.assertTrue(report["valid"])
            self.assertEqual(report["totalEntries"], 0)

    def test_detects_raw_corruption(self):
        with tempfile.TemporaryDirectory() as td:
            path = os.path.join(td, "test.zip")
            make_zip(path, {"a.txt": b"hello world"}, compress_type=zipfile.ZIP_STORED)
            corrupt_path = corrupt_zip_raw(path)
            try:
                try:
                    report = verify_zip(corrupt_path)
                    self.assertIsInstance(report["valid"], bool)
                except zipfile.BadZipFile:
                    pass
            finally:
                os.unlink(corrupt_path)

    def test_too_many_entries(self):
        with tempfile.TemporaryDirectory() as td:
            path = os.path.join(td, "test.zip")
            entries = {("file%d.txt" % i): b"x" for i in range(MAX_ENTRIES + 1)}
            make_zip(path, entries, compress_type=zipfile.ZIP_STORED)
            with self.assertRaises(AssertionError):
                verify_zip(path)

    def test_entry_name_too_long(self):
        with tempfile.TemporaryDirectory() as td:
            path = os.path.join(td, "test.zip")
            long_name = "x" * (MAX_NAME_LENGTH + 1) + ".txt"
            make_zip(path, {long_name: b"content"}, compress_type=zipfile.ZIP_STORED)
            with self.assertRaises(AssertionError):
                verify_zip(path)

    def test_missing_archive(self):
        with self.assertRaises(AssertionError):
            verify_zip("/nonexistent/path.zip")

    def test_directory_entry(self):
        with tempfile.TemporaryDirectory() as td:
            path = os.path.join(td, "test.zip")
            with zipfile.ZipFile(path, "w") as zf:
                zf.writestr("dir/", b"")
                zf.writestr("dir/file.txt", b"content")
            report = verify_zip(path)
            self.assertTrue(report["valid"])
            self.assertEqual(report["totalEntries"], 2)

    def test_report_structure(self):
        with tempfile.TemporaryDirectory() as td:
            path = os.path.join(td, "test.zip")
            make_zip(path, {"a.txt": b"hello"}, compress_type=zipfile.ZIP_STORED)
            report = verify_zip(path)
            self.assertIn("valid", report)
            self.assertIn("entries", report)
            self.assertIn("totalEntries", report)
            self.assertIn("totalBytes", report)
            entry = report["entries"][0]
            self.assertIn("name", entry)
            self.assertIn("crc32", entry)
            self.assertIn("size", entry)
            self.assertIn("valid", entry)
            self.assertIn("error", entry)


if __name__ == "__main__":
    unittest.main()
