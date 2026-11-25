#!/usr/bin/env python3
"""Differential oracle for AirPlayTxtVersionParser (cast/airplay)."""
import unittest


def parse_txt(entries):
    out = {}
    for e in entries:
        if "=" in e:
            k, v = e.split("=", 1)
            if k:
                out[k.strip()] = v.strip()
    return out


class AirPlayTxtVersionParserTest(unittest.TestCase):
    def test_parse(self):
        self.assertEqual(parse_txt(["txtvers=1", "protovers=1.1"]), {"txtvers": "1", "protovers": "1.1"})


if __name__ == "__main__":
    unittest.main()
