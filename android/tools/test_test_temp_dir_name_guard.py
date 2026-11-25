#!/usr/bin/env python3
"""Differential oracle for TestTempDirNameGuard (testing)."""
import re
import unittest

NAME_RE = re.compile(r"^[a-zA-Z0-9._-]{1,64}$")


def is_safe(name: str) -> bool:
    return name not in (".", "..") and bool(NAME_RE.match(name))


class TestTempDirNameGuardTest(unittest.TestCase):
    def test_safe(self):
        self.assertTrue(is_safe("tmp_run_1"))
        self.assertFalse(is_safe(".."))
        self.assertFalse(is_safe("a/b"))


if __name__ == "__main__":
    unittest.main()
