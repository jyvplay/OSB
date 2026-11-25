#!/usr/bin/env python3
"""Differential oracle for PdfPageRangeParser (format/pdf)."""
import unittest


def parse_ranges(spec: str, max_page: int):
    pages = set()
    for part in spec.split(","):
        tok = part.strip()
        assert tok
        if "-" in tok:
            a, b = tok.split("-")
            s, e = int(a.strip()), int(b.strip())
            assert 1 <= s <= e <= max_page
            pages.update(range(s, e + 1))
        else:
            p = int(tok)
            assert 1 <= p <= max_page
            pages.add(p)
    return sorted(pages)


class PdfPageRangeParserTest(unittest.TestCase):
    def test_parse(self):
        self.assertEqual(parse_ranges("1-3, 5, 2-4", 10), [1, 2, 3, 4, 5])


if __name__ == "__main__":
    unittest.main()
