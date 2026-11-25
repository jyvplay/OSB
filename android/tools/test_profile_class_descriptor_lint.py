#!/usr/bin/env python3
"""Differential oracle for ProfileClassDescriptorLint (baselineprofile)."""
import re
import unittest

DESC_RE = re.compile(r"^L[a-zA-Z0-9_/$]+;$")


def is_valid(desc: str) -> bool:
    return bool(desc and len(desc) <= 256 and DESC_RE.match(desc))


class ProfileClassDescriptorLintTest(unittest.TestCase):
    def test_lint(self):
        self.assertTrue(is_valid("Ldev/fusion/shell/MainActivity;"))
        self.assertFalse(is_valid("dev/fusion/MainActivity"))


if __name__ == "__main__":
    unittest.main()
