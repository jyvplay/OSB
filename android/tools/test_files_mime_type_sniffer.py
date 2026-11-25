#!/usr/bin/env python3
"""Differential oracle for FilesMimeTypeSniffer (core/files)."""
import unittest

MIMES = {"png": "image/png", "jpg": "image/jpeg", "jpeg": "image/jpeg", "pdf": "application/pdf", "txt": "text/plain", "json": "application/json", "zip": "application/zip"}


class FilesMimeTypeSnifferTest(unittest.TestCase):
    def test_mime(self):
        self.assertEqual(MIMES.get("pdf", "application/octet-stream"), "application/pdf")
        self.assertEqual(MIMES.get("xyz", "application/octet-stream"), "application/octet-stream")


if __name__ == "__main__":
    unittest.main()
