#!/usr/bin/env python3
"""Independent differential oracle for SemVer (core/model).

Implements Semantic Versioning 2.0.0 parser and precedence comparator
pinned to semver.org spec items 2, 9, 10, 11. Cross-checks against the
Kotlin contract in core/model/src/main/kotlin/dev/fusion/model/SemVer.kt.

No Android SDK, Kotlin compiler or Gradle required; runs on host Python 3.11.
"""
from __future__ import annotations

import sys
import unittest

sys.path.insert(0, str(__file__.rpartition("/tools/")[0] + "/tools"))


def _split_idents(s, allow_leading_zero):
    assert s, "empty identifier section"
    parts = s.split(".")
    for p in parts:
        assert p, "empty identifier"
        assert all(c in "0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ-" for c in p), f"bad identifier char in '{p}'"
        if not allow_leading_zero and len(p) > 1 and p.startswith("0") and p.isdigit():
            raise AssertionError(f"leading zero in numeric pre-release identifier: '{p}'")
    return parts


def parse_semver(text):
    assert text, "empty version"
    core = text
    build = []
    pre = []

    plus = core.find("+")
    if plus >= 0:
        build = _split_idents(core[plus + 1:], allow_leading_zero=True)
        core = core[:plus]

    dash = core.find("-")
    if dash >= 0:
        pre = _split_idents(core[dash + 1:], allow_leading_zero=False)
        core = core[:dash]

    parts = core.split(".")
    assert len(parts) == 3, "version core must be MAJOR.MINOR.PATCH"
    major = _version_field(parts[0])
    minor = _version_field(parts[1])
    patch = _version_field(parts[2])
    return (major, minor, patch, pre, build)


def _version_field(s):
    assert s and all(c in "0123456789" for c in s), f"non-numeric version field: '{s}'"
    assert s == "0" or not s.startswith("0"), f"leading zero in version field: '{s}'"
    return int(s)


def _ident_value(ident):
    """Return (is_numeric, value) for precedence comparison."""
    if ident.isdigit():
        return (True, int(ident))
    return (False, ident)


def compare_semver(a, b):
    """Compare two parsed SemVer tuples. Returns -1, 0, or 1."""
    a_major, a_minor, a_patch, a_pre, a_build = a
    b_major, b_minor, b_patch, b_pre, b_build = b

    if a_major != b_major:
        return -1 if a_major < b_major else 1
    if a_minor != b_minor:
        return -1 if a_minor < b_minor else 1
    if a_patch != b_patch:
        return -1 if a_patch < b_patch else 1

    a_has_pre = len(a_pre) > 0
    b_has_pre = len(b_pre) > 0
    if not a_has_pre and not b_has_pre:
        return 0
    if not a_has_pre:
        return 1  # normal > pre-release
    if not b_has_pre:
        return -1

    n = min(len(a_pre), len(b_pre))
    for i in range(n):
        ident_a = a_pre[i]
        ident_b = b_pre[i]
        a_num, a_val = _ident_value(ident_a)
        b_num, b_val = _ident_value(ident_b)
        if a_num and b_num:
            if a_val != b_val:
                return -1 if a_val < b_val else 1
        elif a_num:
            return -1  # numeric < alphanumeric
        elif b_num:
            return 1
        else:
            if ident_a != ident_b:  # ASCII sort
                return -1 if ident_a < ident_b else 1
    return -1 if len(a_pre) < len(b_pre) else (1 if len(a_pre) > len(b_pre) else 0)


def semver_to_string(parsed):
    major, minor, patch, pre, build = parsed
    sb = f"{major}.{minor}.{patch}"
    if pre:
        sb += "-" + ".".join(pre)
    if build:
        sb += "+" + ".".join(build)
    return sb


class SemVerTest(unittest.TestCase):
    def test_basic_parse(self):
        v = parse_semver("1.2.3")
        self.assertEqual(v, (1, 2, 3, [], []))

    def test_parse_with_prerelease(self):
        v = parse_semver("1.0.0-alpha")
        self.assertEqual(v, (1, 0, 0, ["alpha"], []))

    def test_parse_with_build(self):
        v = parse_semver("1.0.0+build.123")
        self.assertEqual(v, (1, 0, 0, [], ["build", "123"]))

    def test_parse_with_pre_and_build(self):
        v = parse_semver("1.0.0-alpha.1+exp.sha.5114f85")
        self.assertEqual(v, (1, 0, 0, ["alpha", "1"], ["exp", "sha", "5114f85"]))

    def test_leading_zero_rejected(self):
        with self.assertRaises(AssertionError):
            parse_semver("01.2.3")
        with self.assertRaises(AssertionError):
            parse_semver("1.02.3")
        with self.assertRaises(AssertionError):
            parse_semver("1.2.03")

    def test_numeric_prerelease_leading_zero_rejected(self):
        with self.assertRaises(AssertionError):
            parse_semver("1.0.0-alpha.01")

    def test_empty_version_rejected(self):
        with self.assertRaises(AssertionError):
            parse_semver("")

    def test_wrong_core_format(self):
        with self.assertRaises(AssertionError):
            parse_semver("1.2")
        with self.assertRaises(AssertionError):
            parse_semver("1.2.3.4")

    def test_compare_major(self):
        self.assertEqual(compare_semver((1, 0, 0, [], []), (2, 0, 0, [], [])), -1)
        self.assertEqual(compare_semver((2, 0, 0, [], []), (1, 0, 0, [], [])), 1)
        self.assertEqual(compare_semver((1, 0, 0, [], []), (1, 0, 0, [], [])), 0)

    def test_compare_minor(self):
        self.assertEqual(compare_semver((1, 0, 0, [], []), (1, 1, 0, [], [])), -1)

    def test_compare_patch(self):
        self.assertEqual(compare_semver((1, 0, 0, [], []), (1, 0, 1, [], [])), -1)

    def test_normal_greater_than_prerelease(self):
        self.assertEqual(compare_semver((1, 0, 0, [], []), (1, 0, 0, ["alpha"], [])), 1)

    def test_prerelease_identifier_order(self):
        # Canonical chain: 1.0.0-alpha < 1.0.0-alpha.1 < 1.0.0-alpha.beta < 1.0.0-beta
        self.assertLess(compare_semver(parse_semver("1.0.0-alpha"), parse_semver("1.0.0-alpha.1")), 0)
        self.assertLess(compare_semver(parse_semver("1.0.0-alpha.1"), parse_semver("1.0.0-alpha.beta")), 0)
        self.assertLess(compare_semver(parse_semver("1.0.0-alpha.beta"), parse_semver("1.0.0-beta")), 0)

    def test_beta_versions(self):
        self.assertLess(compare_semver(parse_semver("1.0.0-beta"), parse_semver("1.0.0-beta.2")), 0)
        self.assertLess(compare_semver(parse_semver("1.0.0-beta.2"), parse_semver("1.0.0-beta.11")), 0)
        self.assertLess(compare_semver(parse_semver("1.0.0-beta.11"), parse_semver("1.0.0-rc.1")), 0)
        self.assertLess(compare_semver(parse_semver("1.0.0-rc.1"), parse_semver("1.0.0")), 0)

    def test_numeric_vs_alphanumeric(self):
        self.assertLess(compare_semver(parse_semver("1.0.0-1"), parse_semver("1.0.0-alpha")), 0)

    def test_numeric_identifier_comparison(self):
        self.assertLess(compare_semver(parse_semver("1.0.0-alpha.1"), parse_semver("1.0.0-alpha.2")), 0)

    def test_alphanumeric_ascii_sort(self):
        self.assertLess(compare_semver(parse_semver("1.0.0-alpha"), parse_semver("1.0.0-beta")), 0)

    def test_larger_prerelease_set_wins(self):
        self.assertLess(compare_semver(parse_semver("1.0.0-alpha"), parse_semver("1.0.0-alpha.1")), 0)

    def test_build_ignored_in_precedence(self):
        v1 = parse_semver("1.0.0+build1")
        v2 = parse_semver("1.0.0+build2")
        self.assertEqual(compare_semver(v1, v2), 0)

    def test_to_string(self):
        v = parse_semver("1.2.3-alpha.1+build.456")
        self.assertEqual(semver_to_string(v), "1.2.3-alpha.1+build.456")

    def test_to_string_plain(self):
        v = parse_semver("2.0.0")
        self.assertEqual(semver_to_string(v), "2.0.0")


if __name__ == "__main__":
    unittest.main()
