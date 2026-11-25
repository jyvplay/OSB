#!/usr/bin/env python3
"""Differential oracle for HwCpuCoreMaskParser (core/hw)."""
import unittest


def parse_cores(spec: str):
    out = set()
    for part in spec.split(","):
        tok = part.strip()
        if not tok:
            continue
        if "-" in tok:
            s, e = map(int, tok.split("-"))
            out.update(range(s, e + 1))
        else:
            out.add(int(tok))
    return sorted(out)


class HwCpuCoreMaskParserTest(unittest.TestCase):
    def test_parse(self):
        self.assertEqual(parse_cores("0-3,6,8-9"), [0, 1, 2, 3, 6, 8, 9])


if __name__ == "__main__":
    unittest.main()
