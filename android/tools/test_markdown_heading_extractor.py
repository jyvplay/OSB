#!/usr/bin/env python3
"""Differential oracle for MarkdownHeadingExtractor (feature/notes)."""
import re
import unittest

HEADING_RE = re.compile(r"^(#{1,6})\s+(.+?)\s*#*$")


def extract_headings(md: str):
    out = []
    for i, line in enumerate(md.split("\n")):
        m = HEADING_RE.match(line.rstrip("\r"))
        if m:
            out.append((len(m.group(1)), m.group(2).strip(), i))
    return out


class MarkdownHeadingExtractorTest(unittest.TestCase):
    def test_extract(self):
        self.assertEqual(
            extract_headings("# Title\nbody\n## Sub ##"),
            [(1, "Title", 0), (2, "Sub", 2)],
        )


if __name__ == "__main__":
    unittest.main()
