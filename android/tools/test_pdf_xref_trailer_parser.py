#!/usr/bin/env python3
"""Differential oracle for PdfXrefTrailerParser (format/pdf)."""
import unittest


def parse_startxref(txt: str):
    idx = txt.rfind("startxref")
    if idx < 0:
        return None
    toks = txt[idx + len("startxref") :].strip().split()
    return int(toks[0]) if toks else None


class PdfXrefTrailerParserTest(unittest.TestCase):
    def test_startxref(self):
        self.assertEqual(parse_startxref("trailer\nstartxref\n12345\n%%EOF"), 12345)


if __name__ == "__main__":
    unittest.main()
