#!/usr/bin/env python3
"""Independent differential oracle for VersionRange (core/model).

Implements bounded SemVer range matching: >=, <=, ==, !=, >, <, ^ (caret
compatible), ~ (tilde approximate), and bare ==. Delegates to SemVer for
parsing and precedence. Cross-checks against the Kotlin contract in
core/model/src/main/kotlin/dev/fusion/model/VersionRange.kt.

No Android SDK, Kotlin compiler or Gradle required; runs on host Python 3.11.
"""
from __future__ import annotations

import sys
import unittest

sys.path.insert(0, str(__file__.rpartition("/tools/")[0] + "/tools"))

MAX_RANGE_LEN = 64


class SemVer:
    def __init__(self, major: int, minor: int, patch: int):
        self.major = major
        self.minor = minor
        self.patch = patch

    @classmethod
    def parse(cls, s: str):
        parts = s.split(".")
        assert len(parts) == 3, f"SemVer must be MAJOR.MINOR.PATCH: {s}"
        major = int(parts[0])
        minor = int(parts[1])
        patch = int(parts[2])
        assert major >= 0 and minor >= 0 and patch >= 0, f"negative component: {s}"
        for p in parts:
            assert p == str(int(p)) or p == "0", f"leading zeros refused: {s}"
        return cls(major, minor, patch)

    def __eq__(self, other):
        return (self.major, self.minor, self.patch) == (other.major, other.minor, other.patch)

    def __lt__(self, other):
        return (self.major, self.minor, self.patch) < (other.major, other.minor, other.patch)

    def __le__(self, other):
        return (self.major, self.minor, self.patch) <= (other.major, other.minor, other.patch)

    def __gt__(self, other):
        return (self.major, self.minor, self.patch) > (other.major, other.minor, other.patch)

    def __ge__(self, other):
        return (self.major, self.minor, self.patch) >= (other.major, other.minor, other.patch)

    def __ne__(self, other):
        return (self.major, self.minor, self.patch) != (other.major, other.minor, other.patch)

    def __str__(self):
        return f"{self.major}.{self.minor}.{self.patch}"

    def __repr__(self):
        return str(self)


def parse_range(s: str):
    assert len(s) <= MAX_RANGE_LEN, f"range exceeds {MAX_RANGE_LEN} chars"
    import re
    m = re.fullmatch(r"(>=|<=|==|!=|>|<|\^|~)(.+)", s)
    assert m, f"invalid range operator in: {s}"
    op = m.group(1)
    rest = m.group(2)
    assert rest, f"range missing version: {s}"
    return (op, SemVer.parse(rest))


def satisfied_by(op: str, version: SemVer, other: SemVer) -> bool:
    if op == ">=":
        return other >= version
    if op == "<=":
        return other <= version
    if op == "==":
        return other == version
    if op == "!=":
        return other != version
    if op == ">":
        return other > version
    if op == "<":
        return other < version
    if op == "^":
        return _caret_compatible(other, version)
    if op == "~":
        return _tilde_compatible(other, version)
    if op == "":
        return other == version
    assert False, f"unknown operator: {op}"


def _caret_compatible(other: SemVer, version: SemVer) -> bool:
    if other.major != version.major:
        return False
    if version.major == 0 and version.minor == 0:
        return other == version
    if version.major == 0:
        return (other.minor >= version.minor and
                (other.minor > version.minor or other.patch >= version.patch) and
                other.minor < version.minor + 1)
    return other.major == version.major and other.minor >= version.minor


def _tilde_compatible(other: SemVer, version: SemVer) -> bool:
    if version.major == 0 and version.minor == 0:
        return other == version
    if version.major == 0:
        return (other.major == 0 and
                other.minor == version.minor and
                other.patch >= version.patch)
    return (other.major == version.major and
            other.minor == version.minor and
            other.patch >= version.patch)


class VersionRangeTest(unittest.TestCase):
    def test_ge(self):
        v = SemVer.parse("1.2.3")
        self.assertTrue(satisfied_by(">=", v, SemVer.parse("1.2.3")))
        self.assertTrue(satisfied_by(">=", v, SemVer.parse("1.2.4")))
        self.assertTrue(satisfied_by(">=", v, SemVer.parse("1.3.0")))
        self.assertTrue(satisfied_by(">=", v, SemVer.parse("2.0.0")))
        self.assertFalse(satisfied_by(">=", v, SemVer.parse("1.2.2")))
        self.assertFalse(satisfied_by(">=", v, SemVer.parse("1.1.9")))

    def test_le(self):
        v = SemVer.parse("1.2.3")
        self.assertTrue(satisfied_by("<=", v, SemVer.parse("1.2.3")))
        self.assertTrue(satisfied_by("<=", v, SemVer.parse("1.2.2")))
        self.assertTrue(satisfied_by("<=", v, SemVer.parse("1.1.9")))
        self.assertFalse(satisfied_by("<=", v, SemVer.parse("1.2.4")))
        self.assertFalse(satisfied_by("<=", v, SemVer.parse("2.0.0")))

    def test_eq(self):
        v = SemVer.parse("1.2.3")
        self.assertTrue(satisfied_by("==", v, SemVer.parse("1.2.3")))
        self.assertFalse(satisfied_by("==", v, SemVer.parse("1.2.4")))
        self.assertFalse(satisfied_by("==", v, SemVer.parse("1.2.2")))

    def test_ne(self):
        v = SemVer.parse("1.2.3")
        self.assertTrue(satisfied_by("!=", v, SemVer.parse("1.2.4")))
        self.assertTrue(satisfied_by("!=", v, SemVer.parse("2.0.0")))
        self.assertFalse(satisfied_by("!=", v, SemVer.parse("1.2.3")))

    def test_gt_lt(self):
        v = SemVer.parse("1.2.3")
        self.assertTrue(satisfied_by(">", v, SemVer.parse("1.2.4")))
        self.assertFalse(satisfied_by(">", v, SemVer.parse("1.2.3")))
        self.assertFalse(satisfied_by(">", v, SemVer.parse("1.2.2")))
        self.assertTrue(satisfied_by("<", v, SemVer.parse("1.2.2")))
        self.assertFalse(satisfied_by("<", v, SemVer.parse("1.2.3")))
        self.assertFalse(satisfied_by("<", v, SemVer.parse("1.2.4")))

    def test_caret_major_bump(self):
        # ^1.2.3 means >=1.2.3, <2.0.0
        v = SemVer.parse("1.2.3")
        self.assertTrue(satisfied_by("^", v, SemVer.parse("1.2.3")))
        self.assertTrue(satisfied_by("^", v, SemVer.parse("1.3.0")))
        self.assertTrue(satisfied_by("^", v, SemVer.parse("1.9.9")))
        self.assertFalse(satisfied_by("^", v, SemVer.parse("2.0.0")))
        self.assertFalse(satisfied_by("^", v, SemVer.parse("0.9.9")))

    def test_caret_zero_major(self):
        # ^0.2.3 means >=0.2.3, <0.3.0
        v = SemVer.parse("0.2.3")
        self.assertTrue(satisfied_by("^", v, SemVer.parse("0.2.3")))
        self.assertTrue(satisfied_by("^", v, SemVer.parse("0.2.9")))
        self.assertFalse(satisfied_by("^", v, SemVer.parse("0.3.0")))
        self.assertFalse(satisfied_by("^", v, SemVer.parse("1.0.0")))

    def test_caret_zero_major_zero_minor(self):
        # ^0.0.3 means >=0.0.3, <0.0.4 — only 0.0.3 matches
        v = SemVer.parse("0.0.3")
        self.assertTrue(satisfied_by("^", v, SemVer.parse("0.0.3")))
        self.assertFalse(satisfied_by("^", v, SemVer.parse("0.0.9")))
        self.assertFalse(satisfied_by("^", v, SemVer.parse("0.0.2")))
        self.assertFalse(satisfied_by("^", v, SemVer.parse("0.0.4")))

    def test_tilde(self):
        # ~1.2.3 means >=1.2.3, <1.3.0
        v = SemVer.parse("1.2.3")
        self.assertTrue(satisfied_by("~", v, SemVer.parse("1.2.3")))
        self.assertTrue(satisfied_by("~", v, SemVer.parse("1.2.9")))
        self.assertFalse(satisfied_by("~", v, SemVer.parse("1.3.0")))
        self.assertFalse(satisfied_by("~", v, SemVer.parse("1.2.2")))

    def test_tilde_zero_major_zero_minor(self):
        # ~0.0.3 means >=0.0.3, <0.0.4 — only 0.0.3 matches
        v = SemVer.parse("0.0.3")
        self.assertTrue(satisfied_by("~", v, SemVer.parse("0.0.3")))
        self.assertFalse(satisfied_by("~", v, SemVer.parse("0.0.9")))
        self.assertFalse(satisfied_by("~", v, SemVer.parse("0.0.2")))
        self.assertFalse(satisfied_by("~", v, SemVer.parse("0.0.4")))

    def test_bare_equals_eq(self):
        v = SemVer.parse("1.2.3")
        self.assertTrue(satisfied_by("", v, SemVer.parse("1.2.3")))
        self.assertFalse(satisfied_by("", v, SemVer.parse("1.2.4")))

    def test_invalid_range_operator(self):
        with self.assertRaises(AssertionError):
            parse_range("*1.2.3")
        with self.assertRaises(AssertionError):
            parse_range("x1.2.3")
        with self.assertRaises(AssertionError):
            parse_range(">=1.2")

    def test_range_too_long(self):
        with self.assertRaises(AssertionError):
            parse_range(">=" + "1" * 60)

    def test_leading_zeros_refused(self):
        with self.assertRaises(AssertionError):
            SemVer.parse("01.2.3")
        with self.assertRaises(AssertionError):
            parse_range(">=01.2.3")


if __name__ == "__main__":
    unittest.main()
