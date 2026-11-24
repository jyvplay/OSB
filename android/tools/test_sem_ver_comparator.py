#!/usr/bin/env python3
"""Differential oracle for SemVerComparator (core/model)."""
import re
import unittest

SEMVER_RE = re.compile(r"^(0|[1-9][0-9]{0,8})\.(0|[1-9][0-9]{0,8})\.(0|[1-9][0-9]{0,8})$")


def parse_semver(v: str):
    m = SEMVER_RE.match(v)
    if not m:
        raise ValueError(v)
    return int(m.group(1)), int(m.group(2)), int(m.group(3))


def compare_semver(a: str, b: str) -> int:
    pa, pb = parse_semver(a), parse_semver(b)
    return (pa > pb) - (pa < pb)


class SemVerComparatorTest(unittest.TestCase):
    def test_compare(self):
        self.assertEqual(compare_semver("1.2.3", "1.2.3"), 0)
        self.assertEqual(compare_semver("1.2.10", "1.2.3"), 1)
        self.assertEqual(compare_semver("0.9.9", "1.0.0"), -1)


if __name__ == "__main__":
    unittest.main()
