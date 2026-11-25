#!/usr/bin/env python3
"""Differential oracle for CapabilitiesGrantEvaluator (core/capabilities)."""
import unittest


def matches(pat: str, cap: str) -> bool:
    if pat in ("*", cap):
        return True
    if pat.endswith(".*"):
        return cap.startswith(pat[:-1])
    return False


def is_allowed(cap: str, allows: set, denies: set) -> bool:
    if any(matches(d, cap) for d in denies):
        return False
    return any(matches(a, cap) for a in allows)


class CapabilitiesGrantEvaluatorTest(unittest.TestCase):
    def test_rbac(self):
        self.assertTrue(is_allowed("fs.read", {"fs.*"}, {"fs.write"}))
        self.assertFalse(is_allowed("fs.write", {"fs.*"}, {"fs.write"}))


if __name__ == "__main__":
    unittest.main()
