#!/usr/bin/env python3
"""Differential oracle for DlnaProtocolInfoMatcher (cast/dlna)."""
import unittest


def extract_mime(info: str):
    parts = info.split(":")
    if len(parts) < 4:
        return None
    mime = parts[2].strip().lower()
    return mime if "/" in mime else None


class DlnaProtocolInfoMatcherTest(unittest.TestCase):
    def test_extract(self):
        self.assertEqual(extract_mime("http-get:*:video/mp4:*"), "video/mp4")
        self.assertIsNone(extract_mime("bad"))


if __name__ == "__main__":
    unittest.main()
