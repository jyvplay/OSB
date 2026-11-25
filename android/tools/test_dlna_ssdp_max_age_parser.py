#!/usr/bin/env python3
"""Differential oracle for DlnaSsdpMaxAgeParser (cast/dlna)."""
import re
import unittest

RE = re.compile(r"max-age\s*=\s*(\d{1,9})", re.IGNORECASE)


def parse_seconds(hdr: str):
    m = RE.search(hdr)
    return int(m.group(1)) if m else None


class DlnaSsdpMaxAgeParserTest(unittest.TestCase):
    def test_parse(self):
        self.assertEqual(parse_seconds("public, max-age=1800"), 1800)


if __name__ == "__main__":
    unittest.main()
