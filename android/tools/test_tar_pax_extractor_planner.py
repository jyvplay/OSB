#!/usr/bin/env python3
"""Differential oracle for TarPaxExtractorPlanner (format/archive)."""
import unittest


def parse_pax_records(pax_text: str) -> dict:
    out = {}
    pos = 0
    while pos < len(pax_text):
        sp = pax_text.index(" ", pos)
        rec_len = int(pax_text[pos:sp])
        rec = pax_text[sp + 1 : pos + rec_len]
        assert rec.endswith("\n")
        k, v = rec[:-1].split("=", 1)
        out[k] = v
        pos += rec_len
    return out


class TarPaxExtractorPlannerTest(unittest.TestCase):
    def test_pax_parse(self):
        rec1 = "21 path=dir/file.txt\n"
        rec2 = "11 size=42\n"
        parsed = parse_pax_records(rec1 + rec2)
        self.assertEqual(parsed, {"path": "dir/file.txt", "size": "42"})


if __name__ == "__main__":
    unittest.main()
